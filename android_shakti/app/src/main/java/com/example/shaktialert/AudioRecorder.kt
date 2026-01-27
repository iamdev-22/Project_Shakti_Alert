package com.example.shaktialert

import android.media.MediaRecorder
import android.os.Environment
import java.io.File

class AudioRecorder {
    private var recorder: MediaRecorder? = null
    private var outputPath: String? = null

    fun startRecord(dir: File): String? {
        dir.mkdirs()
        val file = File(dir, "audio_${System.currentTimeMillis()}.mp4")
        outputPath = file.absolutePath
        recorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(outputPath)
            prepare()
            start()
        }
        return outputPath
    }

    fun stopRecord() {
        try {
            recorder?.stop()
        } catch (e: Exception) { }
        recorder?.release()
        recorder = null
    }

    fun getPath(): String? = outputPath
}
