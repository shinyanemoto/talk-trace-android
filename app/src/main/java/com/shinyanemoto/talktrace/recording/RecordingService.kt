package com.shinyanemoto.talktrace.recording

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.shinyanemoto.talktrace.MainActivity
import com.shinyanemoto.talktrace.R
import com.shinyanemoto.talktrace.data.RecordingRepository
import com.shinyanemoto.talktrace.media.RecorderManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RecordingService : Service() {
    private val repository by lazy { RecordingRepository(applicationContext) }
    private val recorderManager = RecorderManager()
    private val notificationManager by lazy { NotificationManagerCompat.from(this) }

    private var hasCompletedStopFlow = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_RECORDING -> startRecording()
            ACTION_STOP_RECORDING -> stopRecording()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        if (!hasCompletedStopFlow && recorderManager.isRecording) {
            recorderManager.discardRecording()
            RecordingSessionStore.markRecordingFailed("録音サービスが終了したため、録音を停止しました。")
            RecordingTileSync.requestTileRefresh(this)
        }
        super.onDestroy()
    }

    private fun startRecording() {
        if (RecordingSessionStore.state.value.isRecording) {
            return
        }

        val startedAtMillis = System.currentTimeMillis()
        val outputFile = repository.createNewRecordingFile()
        hasCompletedStopFlow = false
        startServiceInForeground(buildNotification(startedAtMillis))

        runCatching {
            recorderManager.startRecording(outputFile)
        }.onSuccess {
            RecordingSessionStore.markRecordingStarted(
                startedAtMillis = startedAtMillis,
                filePath = outputFile.absolutePath,
            )
            RecordingTileSync.requestTileRefresh(this)
            notificationManager.notify(
                NOTIFICATION_ID,
                buildNotification(startedAtMillis),
            )
        }.onFailure {
            outputFile.delete()
            RecordingSessionStore.markRecordingFailed(
                "録音を開始できませんでした。マイクの状態を確認してください。",
            )
            RecordingTileSync.requestTileRefresh(this)
            stopForegroundAndService()
        }
    }

    private fun stopRecording() {
        if (!recorderManager.isRecording) {
            stopForegroundAndService()
            return
        }

        hasCompletedStopFlow = true
        val savedFile = recorderManager.stopRecording()
        if (savedFile != null) {
            RecordingSessionStore.markRecordingStopped(savedFile.absolutePath)
        } else {
            RecordingSessionStore.markRecordingFailed(
                "録音を保存できませんでした。短すぎる録音の可能性があります。",
            )
        }
        RecordingTileSync.requestTileRefresh(this)
        stopForegroundAndService()
    }

    private fun startServiceInForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun stopForegroundAndService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    private fun buildNotification(startedAtMillis: Long): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            REQUEST_CODE_OPEN_APP,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val stopIntent = Intent(this, RecordingService::class.java).apply {
            action = ACTION_STOP_RECORDING
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            REQUEST_CODE_STOP_RECORDING,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val startTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(startedAtMillis))

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("自分の声をバックグラウンドで録音中です")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "録音中です。開始時刻 $startTime。通知の停止ボタンから録音を終了できます。",
                ),
            )
            .setContentIntent(openAppPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(true)
            .setWhen(startedAtMillis)
            .setUsesChronometer(true)
            .addAction(
                android.R.drawable.ic_media_pause,
                "録音停止",
                stopPendingIntent,
            )
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "TalkTrace Recording",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "TalkTrace の録音中通知"
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val ACTION_START_RECORDING = "com.shinyanemoto.talktrace.action.START_RECORDING"
        const val ACTION_STOP_RECORDING = "com.shinyanemoto.talktrace.action.STOP_RECORDING"

        private const val CHANNEL_ID = "talktrace_recording"
        private const val NOTIFICATION_ID = 1001
        private const val REQUEST_CODE_OPEN_APP = 2001
        private const val REQUEST_CODE_STOP_RECORDING = 2002
    }
}
