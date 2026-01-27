package com.example.shaktialert

import android.content.Context
import android.hardware.Camera
import android.media.CamcorderProfile
import android.media.MediaRecorder
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class VideoRecorder(private val context: Context) {

    private var camera: Camera? = null
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    fun startRecording(): String? {
        try {
            camera = Camera.open(Camera.CameraInfo.CAMERA_FACING_BACK)
            camera?.unlock()

            recorder = MediaRecorder().apply {
                setCamera(camera)
                setAudioSource(MediaRecorder.AudioSource.CAMCORDER)
                setVideoSource(MediaRecorder.VideoSource.CAMERA)
                setProfile(CamcorderProfile.get(CamcorderProfile.QUALITY_480P))

                val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "alerts")
                if (!dir.exists()) dir.mkdirs()

                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                outputFile = File(dir, "video_$timestamp.mp4")
                setOutputFile(outputFile!!.absolutePath)

                setMaxDuration(20000) // 20 sec
                setOrientationHint(90)

                prepare()
                start()
            }

            Log.d("VideoRecorder", "🎥 Recording started at: ${outputFile!!.absolutePath}")
            return outputFile!!.absolutePath

        } catch (e: Exception) {
            Log.e("VideoRecorder", "Video start error: ${e.message}")
            releaseResources()
            return null
        }
    }

    fun stopRecording(): String? {
        try {
            recorder?.apply {
                stop()
                release()
            }
            camera?.release()
            Log.d("VideoRecorder", "✅ Recording stopped, saved at: ${outputFile?.absolutePath}")
            return outputFile?.absolutePath
        } catch (e: Exception) {
            Log.e("VideoRecorder", "Stop error: ${e.message}")
            releaseResources()
            return null
        }
    }

    private fun releaseResources() {
        try {
            recorder?.release()
            recorder = null
        } catch (_: Exception) { }

        try {
            camera?.release()
            camera = null
        } catch (_: Exception) { }
    }
}
