package com.shinyanemoto.talktrace

import android.Manifest
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shinyanemoto.talktrace.data.RecordingItem
import com.shinyanemoto.talktrace.data.RecordingRepository
import com.shinyanemoto.talktrace.media.PlaybackController
import com.shinyanemoto.talktrace.media.RecorderManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MainUiState(
    val hasAudioPermission: Boolean = false,
    val isRecording: Boolean = false,
    val recordingElapsedMillis: Long = 0L,
    val recordings: List<RecordingItem> = emptyList(),
    val currentlyPlayingPath: String? = null,
    val statusMessage: String? = null,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = RecordingRepository(application.applicationContext)
    private val recorderManager = RecorderManager()
    private val playbackController = PlaybackController()

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null
    private var recordingStartedAtMillis: Long? = null

    init {
        refreshPermissionState()
        refreshRecordings()
    }

    fun refreshPermissionState() {
        val granted = ContextCompat.checkSelfPermission(
            getApplication(),
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED

        _uiState.update { it.copy(hasAudioPermission = granted) }
    }

    fun onPermissionResult(granted: Boolean) {
        _uiState.update {
            it.copy(
                hasAudioPermission = granted,
                statusMessage = if (granted) null else "マイク権限がないため録音を開始できません。",
            )
        }
    }

    fun startRecording() {
        if (!_uiState.value.hasAudioPermission || _uiState.value.isRecording) {
            return
        }

        stopPlayback()

        val outputFile = repository.createNewRecordingFile()
        runCatching {
            recorderManager.startRecording(outputFile)
            recordingStartedAtMillis = System.currentTimeMillis()
            _uiState.update {
                it.copy(
                    isRecording = true,
                    recordingElapsedMillis = 0L,
                    statusMessage = "録音中です。自分の声を記録しています。",
                )
            }
            startTimer()
        }.onFailure {
            recorderManager.release()
            outputFile.delete()
            _uiState.update {
                it.copy(statusMessage = "録音を開始できませんでした。マイクの状態を確認してください。")
            }
        }
    }

    fun stopRecording() {
        if (!_uiState.value.isRecording) {
            return
        }

        timerJob?.cancel()
        timerJob = null

        val savedFile = recorderManager.stopRecording()
        recordingStartedAtMillis = null

        _uiState.update {
            it.copy(
                isRecording = false,
                recordingElapsedMillis = 0L,
                statusMessage = if (savedFile != null) {
                    "録音ファイルを保存しました。"
                } else {
                    "録音を保存できませんでした。短すぎる録音の可能性があります。"
                },
            )
        }

        refreshRecordings()
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

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                val startedAt = recordingStartedAtMillis ?: break
                val elapsed = System.currentTimeMillis() - startedAt
                _uiState.update { it.copy(recordingElapsedMillis = elapsed) }
                delay(1_000)
            }
        }
    }

    override fun onCleared() {
        recorderManager.release()
        playbackController.release()
        super.onCleared()
    }
}
