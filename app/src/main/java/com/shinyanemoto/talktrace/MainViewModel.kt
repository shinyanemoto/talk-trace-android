package com.shinyanemoto.talktrace

import android.Manifest
import android.app.Application
import android.os.Build
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shinyanemoto.talktrace.data.RecordingItem
import com.shinyanemoto.talktrace.data.RecordingRepository
import com.shinyanemoto.talktrace.media.PlaybackController
import com.shinyanemoto.talktrace.recording.RecordingServiceController
import com.shinyanemoto.talktrace.recording.RecordingSessionEvent
import com.shinyanemoto.talktrace.recording.RecordingSessionStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MainUiState(
    val hasAudioPermission: Boolean = false,
    val hasNotificationPermission: Boolean = true,
    val isRecording: Boolean = false,
    val recordingElapsedMillis: Long = 0L,
    val recordings: List<RecordingItem> = emptyList(),
    val currentlyPlayingPath: String? = null,
    val statusMessage: String? = null,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = RecordingRepository(application.applicationContext)
    private val playbackController = PlaybackController()

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null

    init {
        observeRecordingSession()
        refreshPermissionState()
        refreshRecordings()
    }

    fun refreshPermissionState() {
        val hasAudioPermission = ContextCompat.checkSelfPermission(
            getApplication(),
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
        val hasNotificationPermission = hasNotificationPermission()

        _uiState.update {
            it.copy(
                hasAudioPermission = hasAudioPermission,
                hasNotificationPermission = hasNotificationPermission,
            )
        }
    }

    fun onPermissionsResult(grants: Map<String, Boolean>) {
        val audioGranted = grants[Manifest.permission.RECORD_AUDIO] ?: _uiState.value.hasAudioPermission
        val notificationGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            grants[Manifest.permission.POST_NOTIFICATIONS] ?: _uiState.value.hasNotificationPermission
        } else {
            true
        }

        _uiState.update {
            it.copy(
                hasAudioPermission = audioGranted,
                hasNotificationPermission = notificationGranted,
                statusMessage = if (audioGranted && notificationGranted) {
                    null
                } else {
                    permissionMessage(audioGranted, notificationGranted)
                },
            )
        }
    }

    fun startRecording() {
        val state = _uiState.value
        if (!state.hasAudioPermission || !state.hasNotificationPermission || state.isRecording) {
            _uiState.update {
                it.copy(
                    statusMessage = permissionMessage(
                        audioGranted = state.hasAudioPermission,
                        notificationGranted = state.hasNotificationPermission,
                    ),
                )
            }
            return
        }

        stopPlayback()
        runCatching {
            RecordingServiceController.start(getApplication())
        }.onFailure {
            _uiState.update {
                it.copy(statusMessage = "録音サービスを開始できませんでした。権限と端末設定を確認してください。")
            }
        }
    }

    fun stopRecording() {
        if (!RecordingSessionStore.state.value.isRecording) {
            return
        }
        RecordingServiceController.stop(getApplication())
    }

    fun refreshRecordings() {
        _uiState.update { it.copy(recordings = repository.listRecordings()) }
    }

    fun togglePlayback(recording: RecordingItem) {
        if (_uiState.value.currentlyPlayingPath == recording.file.absolutePath) {
            stopPlayback()
            return
        }

        if (_uiState.value.isRecording) {
            _uiState.update {
                it.copy(statusMessage = "録音中は保存済みファイルを再生できません。")
            }
            return
        }

        runCatching {
            playbackController.play(recording) {
                _uiState.update { state -> state.copy(currentlyPlayingPath = null) }
            }
            _uiState.update {
                it.copy(
                    currentlyPlayingPath = recording.file.absolutePath,
                    statusMessage = "再生中: ${recording.fileName}",
                )
            }
        }.onFailure {
            stopPlayback()
            _uiState.update { it.copy(statusMessage = "音声ファイルを再生できませんでした。") }
        }
    }

    fun stopPlayback() {
        playbackController.stop()
        _uiState.update { it.copy(currentlyPlayingPath = null) }
    }

    fun deleteRecording(recording: RecordingItem) {
        if (_uiState.value.currentlyPlayingPath == recording.file.absolutePath) {
            stopPlayback()
        }

        val deleted = repository.deleteRecording(recording)
        _uiState.update {
            it.copy(
                statusMessage = if (deleted) {
                    "録音ファイルを削除しました。"
                } else {
                    "録音ファイルを削除できませんでした。"
                },
            )
        }
        refreshRecordings()
    }

    fun buildShareIntent(recording: RecordingItem): Intent {
        return repository.createShareIntent(recording)
    }

    fun clearStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    private fun observeRecordingSession() {
        viewModelScope.launch {
            var wasRecording = false
            RecordingSessionStore.state.collectLatest { session ->
                _uiState.update {
                    it.copy(
                        isRecording = session.isRecording,
                        recordingElapsedMillis = if (session.isRecording) {
                            session.startedAtMillis?.let(::elapsedSince) ?: 0L
                        } else {
                            0L
                        },
                    )
                }

                val startedAt = session.startedAtMillis
                if (session.isRecording && startedAt != null) {
                    startTimer(startedAt)
                } else {
                    stopTimer()
                }

                if (wasRecording && !session.isRecording) {
                    refreshRecordings()
                }
                wasRecording = session.isRecording

                when (val event = session.lastEvent) {
                    is RecordingSessionEvent.Started -> {
                        _uiState.update {
                            it.copy(statusMessage = "録音中です。バックグラウンドでも継続します。")
                        }
                        RecordingSessionStore.clearEvent()
                    }

                    is RecordingSessionEvent.Saved -> {
                        _uiState.update {
                            it.copy(statusMessage = "録音ファイルを保存しました。")
                        }
                        RecordingSessionStore.clearEvent()
                    }

                    is RecordingSessionEvent.Failed -> {
                        _uiState.update { it.copy(statusMessage = event.message) }
                        RecordingSessionStore.clearEvent()
                    }

                    null -> Unit
                }
            }
        }
    }

    private fun startTimer(startedAtMillis: Long) {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                val elapsed = elapsedSince(startedAtMillis)
                _uiState.update { it.copy(recordingElapsedMillis = elapsed) }
                delay(1_000)
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    private fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                getApplication(),
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun permissionMessage(audioGranted: Boolean, notificationGranted: Boolean): String {
        return when {
            !audioGranted && !notificationGranted ->
                "録音開始にはマイク権限と通知権限が必要です。通知はバックグラウンド録音の停止操作に使います。"

            !audioGranted ->
                "マイク権限がないため録音を開始できません。自分の発話を記録するために必要です。"

            !notificationGranted ->
                "通知権限がないためバックグラウンド録音を開始できません。録音中通知と通知からの停止操作に使います。"

            else -> ""
        }
    }

    private fun elapsedSince(startedAtMillis: Long): Long {
        return System.currentTimeMillis() - startedAtMillis
    }

    override fun onCleared() {
        playbackController.release()
        super.onCleared()
    }
}
