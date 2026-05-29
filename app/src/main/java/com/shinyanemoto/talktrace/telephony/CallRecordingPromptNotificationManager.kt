package com.shinyanemoto.talktrace.telephony

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.shinyanemoto.talktrace.MainActivity
import com.shinyanemoto.talktrace.R

class CallRecordingPromptNotificationManager(context: Context) {
    private val appContext = context.applicationContext
    private val notificationManager = NotificationManagerCompat.from(appContext)

    init {
        createNotificationChannel()
    }

    fun show(callState: TalkTraceCallState) {
        if (CallRecordingPromptStore.isVisible.value) {
            return
        }

        notificationManager.notify(NOTIFICATION_ID, buildNotification(callState))
        CallRecordingPromptStore.setVisible(true)
    }

    fun dismiss() {
        if (!CallRecordingPromptStore.isVisible.value) {
            return
        }

        notificationManager.cancel(NOTIFICATION_ID)
        CallRecordingPromptStore.setVisible(false)
    }

    private fun buildNotification(callState: TalkTraceCallState): Notification {
        val openAppIntent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            appContext,
            REQUEST_CODE_OPEN_APP,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val startRecordingIntent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_AUTO_START_RECORDING_SOURCE, MainActivity.AUTO_START_SOURCE_CALL_PROMPT)
        }
        val startRecordingPendingIntent = PendingIntent.getActivity(
            appContext,
            REQUEST_CODE_START_RECORDING,
            startRecordingIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val contentText = when (callState) {
            TalkTraceCallState.Ringing -> "着信中です。自分の声を記録できます。"
            TalkTraceCallState.Offhook -> "通話中です。自分の声を記録できます。"
            else -> "自分の声を記録できます。"
        }

        return NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(appContext.getString(R.string.app_name))
            .setContentText(contentText)
            .setContentIntent(openAppPendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .addAction(
                android.R.drawable.ic_media_play,
                "録音開始",
                startRecordingPendingIntent,
            )
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "TalkTrace Call Prompt",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "TalkTrace の録音開始提案通知"
        }
        manager.createNotificationChannel(channel)
    }

    private companion object {
        private const val CHANNEL_ID = "talktrace_call_prompt"
        private const val NOTIFICATION_ID = 2001
        private const val REQUEST_CODE_OPEN_APP = 2101
        private const val REQUEST_CODE_START_RECORDING = 2102
    }
}
