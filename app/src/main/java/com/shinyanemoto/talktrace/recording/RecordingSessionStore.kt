package com.shinyanemoto.talktrace.recording

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object RecordingSessionStore {
    private val _state = MutableStateFlow(RecordingSessionState())
    val state: StateFlow<RecordingSessionState> = _state.asStateFlow()

    fun markRecordingStarted(startedAtMillis: Long, filePath: String) {
        _state.value = RecordingSessionState(
            isRecording = true,
            startedAtMillis = startedAtMillis,
            activeFilePath = filePath,
            lastEvent = RecordingSessionEvent.Started(startedAtMillis),
        )
    }

    fun markRecordingStopped(savedFilePath: String) {
        _state.value = RecordingSessionState(
            isRecording = false,
            lastEvent = RecordingSessionEvent.Saved(savedFilePath),
        )
    }

    fun markRecordingFailed(message: String) {
        _state.update { current ->
            current.copy(
                isRecording = false,
                startedAtMillis = null,
                activeFilePath = null,
                lastEvent = RecordingSessionEvent.Failed(message),
            )
        }
    }

    fun clearEvent() {
        _state.update { it.copy(lastEvent = null) }
    }
}

