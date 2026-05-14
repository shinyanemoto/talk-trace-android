package com.shinyanemoto.talktrace.recording

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

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
            RecordingServiceController.start(applicationContext)
        }
        updateTile(isRecording = !isRecording)
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
}

