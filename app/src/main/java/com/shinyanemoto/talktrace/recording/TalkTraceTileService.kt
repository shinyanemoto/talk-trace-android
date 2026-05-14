package com.shinyanemoto.talktrace.recording

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.shinyanemoto.talktrace.MainActivity

class TalkTraceTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        updateTile(isRecording = RecordingSessionStore.state.value.isRecording)
    }

    override fun onTileAdded() {
        super.onTileAdded()
        updateTile(isRecording = RecordingSessionStore.state.value.isRecording)
    }

    override fun onClick() {
        super.onClick()

        val isRecording = RecordingSessionStore.state.value.isRecording
        if (isRecording) {
            RecordingServiceController.stop(applicationContext)
        } else {
            startRecordingFromTile()
        }
        updateTile(isRecording = !isRecording)
    }

    private fun startRecordingFromTile() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(
                    MainActivity.EXTRA_AUTO_START_RECORDING_SOURCE,
                    MainActivity.AUTO_START_SOURCE_TILE,
                )
            }
            val pendingIntent = PendingIntent.getActivity(
                this,
                REQUEST_CODE_TILE_START,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            RecordingServiceController.start(applicationContext)
        }
    }

    private fun updateTile(isRecording: Boolean) {
        val tile = qsTile ?: return
        tile.label = "TalkTrace"
        tile.state = if (isRecording) {
            Tile.STATE_ACTIVE
        } else {
            Tile.STATE_INACTIVE
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (isRecording) "録音中" else "録音開始"
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            tile.stateDescription = if (isRecording) {
                "自分の声を録音中"
            } else {
                "録音停止中"
            }
        }

        tile.updateTile()
    }

    companion object {
        private const val REQUEST_CODE_TILE_START = 3101
    }
}
