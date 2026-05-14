package com.shinyanemoto.talktrace.recording

import android.content.ComponentName
import android.content.Context
import android.service.quicksettings.TileService

object RecordingTileSync {
    fun requestTileRefresh(context: Context) {
        TileService.requestListeningState(
            context,
            ComponentName(context, TalkTraceTileService::class.java),
        )
    }
}

