package com.shinyanemoto.talktrace.data

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RecordingRepository(private val context: Context) {
    private val recordingsDirectory: File
        get() = File(context.filesDir, RECORDINGS_DIRECTORY_NAME).apply { mkdirs() }

    fun createNewRecordingFile(): File {
        val timestamp = fileNameDateFormat.format(Date())
        return File(recordingsDirectory, "talktrace_$timestamp.m4a")
    }

    fun listRecordings(): List<RecordingItem> {
        return recordingsDirectory
            .listFiles()
            .orEmpty()
            .filter { it.isFile && it.extension.lowercase(Locale.US) == "m4a" }
            .sortedByDescending { it.lastModified() }
            .map { file ->
                RecordingItem(
                    file = file,
                    fileName = file.name,
                    recordedAtMillis = file.lastModified(),
                    durationMillis = extractDuration(file),
                )
            }
    }

    fun deleteRecording(recording: RecordingItem): Boolean {
        return recording.file.delete()
    }

    fun createShareIntent(recording: RecordingItem): Intent {
        val contentUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            recording.file,
        )

        return Intent(Intent.ACTION_SEND).apply {
            type = "audio/mp4"
            putExtra(Intent.EXTRA_STREAM, contentUri)
            putExtra(Intent.EXTRA_SUBJECT, recording.fileName)
            clipData = ClipData.newRawUri(recording.fileName, contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun extractDuration(file: File): Long {
        val retriever = MediaMetadataRetriever()
        return runCatching {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?: 0L
        }.getOrDefault(0L)
            .also { retriever.release() }
    }

    private companion object {
        private const val RECORDINGS_DIRECTORY_NAME = "recordings"
        private val fileNameDateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
    }
}

