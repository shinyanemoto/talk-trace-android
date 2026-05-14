package com.shinyanemoto.talktrace.recording

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

object RecordingServiceController {
    fun start(context: Context) {
        val intent = Intent(context, RecordingService::class.java).apply {
            action = RecordingService.ACTION_START_RECORDING
        }
        ContextCompat.startForegroundService(context, intent)
    }

    fun stop(context: Context) {
        val intent = Intent(context, RecordingService::class.java).apply {
            action = RecordingService.ACTION_STOP_RECORDING
        }
        context.startService(intent)
    }
}

