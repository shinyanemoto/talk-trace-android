package com.shinyanemoto.talktrace.data

import java.io.File

data class RecordingItem(
    val file: File,
    val fileName: String,
    val recordedAtMillis: Long,
    val durationMillis: Long,
)

