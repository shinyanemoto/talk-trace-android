package com.shinyanemoto.talktrace.recording

sealed interface RecordingSessionEvent {
    data class Started(val startedAtMillis: Long) : RecordingSessionEvent

    data class Saved(val filePath: String) : RecordingSessionEvent

    data class Failed(val message: String) : RecordingSessionEvent
}

data class RecordingSessionState(
    val isRecording: Boolean = false,
    val startedAtMillis: Long? = null,
    val activeFilePath: String? = null,
    val lastEvent: RecordingSessionEvent? = null,
)

