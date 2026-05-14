package com.shinyanemoto.talktrace.media

import android.media.MediaPlayer
import com.shinyanemoto.talktrace.data.RecordingItem

class PlaybackController {
    private var mediaPlayer: MediaPlayer? = null

    fun play(recording: RecordingItem, onCompletion: () -> Unit) {
        stop()
        mediaPlayer = MediaPlayer().apply {
            setDataSource(recording.file.absolutePath)
            setOnCompletionListener {
                stop()
                onCompletion()
            }
            prepare()
            start()
        }
    }

    fun stop() {
        mediaPlayer?.runCatching {
            if (isPlaying) {
                stop()
            }
        }
        mediaPlayer?.release()
        mediaPlayer = null
    }

    fun release() {
        stop()
    }
}

