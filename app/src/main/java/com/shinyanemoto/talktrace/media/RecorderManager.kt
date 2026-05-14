package com.shinyanemoto.talktrace.media

import android.media.MediaRecorder
import java.io.File

class RecorderManager {
    private var mediaRecorder: MediaRecorder? = null
    private var outputFile: File? = null
    val isRecording: Boolean
        get() = mediaRecorder != null

    fun startRecording(destination: File) {
        check(mediaRecorder == null) { "Recording already in progress." }

        outputFile = destination
        mediaRecorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(44_100)
            setAudioEncodingBitRate(128_000)
            setAudioChannels(1)
            setOutputFile(destination.absolutePath)
            prepare()
            start()
        }
    }

    fun stopRecording(): File? {
        val currentRecorder = mediaRecorder ?: return null
        val currentOutput = outputFile

        return runCatching {
            currentRecorder.stop()
            currentOutput
        }.getOrNull()
            ?.takeIf { it.exists() }
            .also { savedFile ->
                if (savedFile == null) {
                    currentOutput?.delete()
                }
                currentRecorder.release()
                mediaRecorder = null
                outputFile = null
            }
    }

    fun discardRecording() {
        val currentOutput = outputFile
        release()
        currentOutput?.delete()
    }

    fun release() {
        runCatching {
            mediaRecorder?.release()
        }
        mediaRecorder = null
        outputFile = null
    }
}
