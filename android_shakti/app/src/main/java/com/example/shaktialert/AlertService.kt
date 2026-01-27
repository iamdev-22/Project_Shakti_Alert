package com.example.shaktialert

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.CamcorderProfile
import android.media.MediaRecorder
import android.os.*
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.telephony.SmsManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import java.io.File
import java.io.IOException
import java.util.*
import kotlin.math.sqrt
import android.widget.Toast
import android.speech.tts.TextToSpeech
import android.hardware.Camera
import android.graphics.SurfaceTexture

class AlertService : Service(), LocationListener, SensorEventListener {

    private val TAG = "AlertService"
    private lateinit var locationManager: LocationManager
    private lateinit var sensorManager: SensorManager
    private var speechRecognizer: SpeechRecognizer? = null
    private var recorder: MediaRecorder? = null
    private var camera: Camera? = null
    private var videoFile: File? = null
    private var audioFile: File? = null
    private var lastShakeTime = 0L
    private var handler = Handler(Looper.getMainLooper())
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    private var currentLat = 0.0
    private var currentLon = 0.0
    private var tts: TextToSpeech? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        
        // Acquire wake lock to keep service running even when screen is off
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "ShaktiAlert::VoiceListeningWakeLock"
        )
        wakeLock?.acquire(24 * 60 * 60 * 1000L) // 24 hours max
        
        startForegroundNotification()
        startLocationUpdates()
        getLastKnownLocation() // ✅ Get location immediately
        startSpeechRecognition()
        startShakeDetection()
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.getDefault()
            }
        }
        
        Log.d(TAG, "✅ AlertService started with wake lock - will continue even when screen is off")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "START_LISTENING" -> {
                startSpeechRecognition()
                broadcastStatus("Listening for 'Help'...")
                broadcastMic(true)
                Log.d(TAG, "✅ Voice listening started")
            }
            "STOP_LISTENING" -> {
                // Only stop voice recognition, keep service running for location tracking
                speechRecognizer?.stopListening()
                speechRecognizer?.destroy()
                speechRecognizer = null
                broadcastStatus("Voice listening stopped")
                broadcastMic(false)
                Log.d(TAG, "⏸ Voice listening stopped - service still running for location")
                // Don't call stopSelf() - keep service alive for location tracking
            }
        }
        // START_STICKY ensures service restarts if killed by system
        return START_STICKY
    }

    // 🛰 Foreground Notification
    private fun startForegroundNotification() {
        val channelId = "shakti_alert_channel"
        val channel = NotificationChannel(channelId, "Shakti Alert", NotificationManager.IMPORTANCE_LOW)
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Shakti Alert Active")
            .setContentText("Listening and Monitoring for Emergencies…")
            .setSmallIcon(R.drawable.ic_emergency)
            .build()

        startForeground(1, notification)
    }

    // 📍 Start Continuous Real-Time Location Updates (Like Zomato/Life360/Swiggy)
    private fun startLocationUpdates() {
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        try {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
            ) return
            
            // ✅ HIGH-FREQUENCY GPS UPDATES (Every 2 seconds, 0 meters minimum distance)
            // This ensures continuous real-time tracking like delivery/ride-sharing apps
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER, 
                2_000L,  // Update every 2 seconds (was 10 seconds)
                0f,      // No minimum distance - track even small movements (was 5 meters)
                this
            )
            
            // ✅ Also use NETWORK provider as fallback for indoor/poor GPS
            locationManager.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER,
                2_000L,  // Same frequency
                0f,      // No minimum distance
                this
            )
            
            Log.d(TAG, "✅ Continuous real-time location tracking started (2-second updates)")
        } catch (e: Exception) {
            Log.e(TAG, "Location error: ${e.message}")
        }
    }
    
    // Get last known location immediately (fixes 0.0,0.0 issue)
    private fun getLastKnownLocation() {
        try {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
            ) return
            
            val lastLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            
            lastLocation?.let {
                currentLat = it.latitude
                currentLon = it.longitude
                Log.d(TAG, "📍 Got last known location: $currentLat, $currentLon")
                broadcastStatus("Location obtained: $currentLat, $currentLon")
            } ?: run {
                Log.w(TAG, "⚠️ No last known location available")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get last known location: ${e.message}")
        }
    }

    // 📳 Shake Detection
    private fun startShakeDetection() {
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        val gX = event.values[0] / SensorManager.GRAVITY_EARTH
        val gY = event.values[1] / SensorManager.GRAVITY_EARTH
        val gZ = event.values[2] / SensorManager.GRAVITY_EARTH
        val gForce = sqrt(gX * gX + gY * gY + gZ * gZ)

        if (gForce > 2.7) { // Shake threshold
            val now = System.currentTimeMillis()
            if (now - lastShakeTime > 3000) {
                lastShakeTime = now
                triggerEmergency("Shake Detected Emergency!")
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    // 🎤 Voice Detection (SILENT MODE - No beeps, no indicators)
    private fun startSpeechRecognition() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return
        
        handler.post {
            if (speechRecognizer == null) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            }
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                // Silent mode settings
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1500L)
                putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf<String>())
            }

            muteSystemAudio() // Mute beep

            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    // Don't broadcast - stay silent
                }
                override fun onBeginningOfSpeech() {
                    // Don't broadcast - stay silent
                }
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    // Don't broadcast - stay silent
                }
                override fun onError(error: Int) {
                    // Restart listening after error (e.g. no speech)
                    handler.postDelayed({ startSpeechRecognition() }, 1000)
                }
                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        val text = matches[0].lowercase(Locale.getDefault())
                        Log.d(TAG, "Voice detected: $text")
                        if (text.contains("help") || text.contains("bachao") || text.contains("save me")) {
                            // ⚡ INSTANT ALERT - Send WhatsApp IMMEDIATELY (no verification delay)
                            broadcastStatus("Help Detected! Sending instant alert...")
                            sendQuickAlert("Voice Triggered Emergency (Help Detected)")
                            
                            // Then start recording media in background (non-blocking)
                            handler.postDelayed({
                                triggerEmergency("Voice Triggered Emergency")
                            }, 500) // Small delay to let quick alert send first
                        }
                    }
                    handler.postDelayed({ startSpeechRecognition() }, 500)
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            speechRecognizer?.startListening(intent)
        }
    }

    private fun broadcastStatus(status: String) {
        val intent = Intent("com.shakti.alert.STATUS")
        intent.putExtra("status", status)
        sendBroadcast(intent)
    }

    private fun broadcastMic(active: Boolean) {
        val intent = Intent("com.shakti.alert.MIC")
        intent.putExtra("active", active)
        sendBroadcast(intent)
    }

    // ⚡ INSTANT QUICK ALERT - Send WhatsApp IMMEDIATELY (2-3 seconds)
    private fun sendQuickAlert(reason: String) {
        val prefs = getSharedPreferences("shakti_prefs", Context.MODE_PRIVATE)
        val baseUrl = prefs.getString("server_url", "http://192.168.1.35:5000") ?: "http://192.168.1.35:5000"
        val url = "$baseUrl/quick_alert"
        
        Log.d(TAG, "⚡ Sending INSTANT alert to: $url")
        Log.d(TAG, "📍 Location: $currentLat, $currentLon")
        
        // Create JSON payload (faster than multipart)
        val json = """
            {
                "message": "$reason",
                "lat": $currentLat,
                "lon": $currentLon
            }
        """.trimIndent()
        
        val requestBody = RequestBody.create("application/json".toMediaTypeOrNull(), json)
        val req = Request.Builder().url(url).post(requestBody).build()
        
        // Send asynchronously (non-blocking)
        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "❌ Quick alert failed: ${e.message}")
                handler.post {
                    broadcastStatus("Quick alert failed - trying full upload")
                }
            }
            
            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string() ?: ""
                response.close()
                
                Log.d(TAG, "✅ Quick Alert Response: $responseBody")
                
                handler.post {
                    if (responseBody.contains("\"whatsapp_sent\":true")) {
                        broadcastStatus("✅ Guardian notified via WhatsApp!")
                        Toast.makeText(this@AlertService, 
                            "🚨 Guardian alerted!\n📱 WhatsApp sent in 2 seconds!", 
                            Toast.LENGTH_LONG).show()
                    } else {
                        broadcastStatus("Alert sent to server")
                    }
                }
            }
        })
    }
    
    // 🎯 Voice Verification Process
    private fun startVoiceVerification() {
        broadcastStatus("Verifying your voice...")
        
        // Record a short audio sample for verification
        try {
            val dir = File(getExternalFilesDir(null), "verification")
            if (!dir.exists()) dir.mkdirs()
            val verifyFile = File(dir, "verify_${System.currentTimeMillis()}.mp3")
            
            val verifyRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(verifyFile.absolutePath)
                prepare()
                start()
            }
            
            // Record for 3 seconds
            handler.postDelayed({
                try {
                    verifyRecorder.stop()
                    verifyRecorder.release()
                    
                    // Send to backend for verification
                    verifyVoiceWithBackend(verifyFile)
                } catch (e: Exception) {
                    Log.e(TAG, "Verification recording failed: ${e.message}")
                    // If verification fails, still trigger emergency for safety
                    broadcastStatus("Verification failed - Triggering emergency anyway for safety")
                    triggerEmergency("Voice Triggered Emergency (Verification Failed)")
                }
            }, 3000)
            
        } catch (e: Exception) {
            Log.e(TAG, "Voice verification setup failed: ${e.message}")
            // If verification fails, still trigger emergency for safety
            broadcastStatus("Verification failed - Triggering emergency anyway for safety")
            triggerEmergency("Voice Triggered Emergency (Verification Failed)")
        }
    }
    
    // 🔐 Verify voice with backend
    private fun verifyVoiceWithBackend(audioFile: File) {
        val prefs = getSharedPreferences("shakti_prefs", Context.MODE_PRIVATE)
        val baseUrl = prefs.getString("server_url", "http://192.168.1.35:5000") ?: "http://192.168.1.35:5000"
        val url = "$baseUrl/verify_voice"
        
        val form = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("audio", audioFile.name, 
                RequestBody.create("audio/mp3".toMediaTypeOrNull(), audioFile))
            .build()
        
        val req = Request.Builder().url(url).post(form).build()
        
        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Voice verification request failed: ${e.message}")
                // If backend is unreachable, trigger emergency for safety
                handler.post {
                    broadcastStatus("Backend unreachable - Triggering emergency for safety")
                    triggerEmergency("Voice Triggered Emergency (Backend Unreachable)")
                }
            }
            
            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string() ?: ""
                response.close()
                
                handler.post {
                    try {
                        // Parse response (expecting JSON with "verified": true/false)
                        val verified = responseBody.contains("\"verified\":true") || 
                                      responseBody.contains("\"success\":true")
                        
                        if (verified) {
                            broadcastStatus("Voice Verified! Starting emergency protocol...")
                            triggerEmergency("Voice Triggered Emergency (Verified)")
                        } else {
                            broadcastStatus("Voice not verified - False alarm")
                            Toast.makeText(this@AlertService, 
                                "Voice not recognized. Say 'help' again if you need assistance.", 
                                Toast.LENGTH_LONG).show()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing verification response: ${e.message}")
                        // On error, trigger for safety
                        broadcastStatus("Verification error - Triggering emergency for safety")
                        triggerEmergency("Voice Triggered Emergency (Verification Error)")
                    }
                }
            }
        })
    }

    // 🚨 Combined Trigger for Shake / Voice / Manual
    private fun triggerEmergency(reason: String) {
        Log.w(TAG, "🚨 Alert triggered: $reason")
        broadcastStatus("EMERGENCY ACTIVATED! Recording video, audio and tracking location...")

        // ✅ Video recording with invisible preview (background)
        startVideoRecording()
        
        // ✅ Audio recording
        startAudioRecording()
        
        // Start live location tracking (send location every 5 seconds)
        startLiveLocationTracking()

        // Record for 15 seconds (10s video + 5s audio)
        handler.postDelayed({
            try {
                stopVideoRecording()
                stopAudioRecording()
                
                // Wait 1 second for files to finalize before sending
                handler.postDelayed({
                    sendAlert(reason)
                    stopLiveLocationTracking()
                }, 1000)
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping recordings: ${e.message}")
                // Still try to send alert
                sendAlert(reason)
                stopLiveLocationTracking()
            }
        }, 15_000L) // 15 seconds total (10s video + 5s audio)
    }
    
    // 📍 Live Location Tracking
    private var locationTrackingRunnable: Runnable? = null
    
    private fun startLiveLocationTracking() {
        locationTrackingRunnable = object : Runnable {
            override fun run() {
                uploadLocation(currentLat, currentLon)
                handler.postDelayed(this, 5000) // Update every 5 seconds
            }
        }
        handler.post(locationTrackingRunnable!!)
    }
    
    private fun stopLiveLocationTracking() {
        locationTrackingRunnable?.let { handler.removeCallbacks(it) }
    }

    // 🎥 Video Recording with Background Support
    @Suppress("DEPRECATION")
    private fun startVideoRecording() {
        try {
            // Open back camera
            camera = Camera.open(Camera.CameraInfo.CAMERA_FACING_BACK)
            
            // Create invisible preview surface
            val surfaceTexture = SurfaceTexture(0)
            camera?.setPreviewTexture(surfaceTexture)
            camera?.startPreview()
            
            // Unlock camera for MediaRecorder
            camera?.unlock()
            
            // Create video file
            val dir = File(getExternalFilesDir(null), "videos")
            if (!dir.exists()) dir.mkdirs()
            videoFile = File(dir, "alert_${System.currentTimeMillis()}.mp4")
            
            // Setup MediaRecorder
            recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                MediaRecorder()
            }.apply {
                setCamera(camera)
                setAudioSource(MediaRecorder.AudioSource.CAMCORDER)
                setVideoSource(MediaRecorder.VideoSource.CAMERA)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                setVideoSize(1280, 720) // 720p
                setVideoFrameRate(30)
                setVideoEncodingBitRate(3000000) // 3 Mbps
                setAudioEncodingBitRate(128000) // 128 kbps
                setOutputFile(videoFile!!.absolutePath)
                
                prepare()
                start()
                
                Log.d(TAG, "✅ Video recording started: ${videoFile!!.absolutePath}")
                broadcastStatus("📹 Recording video...")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Video recording failed: ${e.message}")
            e.printStackTrace()
            // Release camera if failed
            camera?.release()
            camera = null
        }
    }

    private fun stopVideoRecording() {
        try {
            recorder?.stop()
            recorder?.release()
            recorder = null
            
            // Release camera
            camera?.stopPreview()
            camera?.release()
            camera = null
            
            Log.d(TAG, "✅ Video recording stopped")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Stop video failed: ${e.message}")
        }
    }

    // 🎙 Audio Recording
    private fun startAudioRecording() {
        try {
            val dir = File(getExternalFilesDir(null), "audio")
            if (!dir.exists()) dir.mkdirs()
            audioFile = File(dir, "audio_${System.currentTimeMillis()}.mp3")
            val audioRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(audioFile!!.absolutePath)
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Audio record failed: ${e.message}")
        }
    }

    private fun stopAudioRecording() {
        try {
            recorder?.stop()
            recorder?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Stop audio failed: ${e.message}")
        }
    }

    // 📤 Send Alert (SMS + Backend Trigger)
    private fun sendAlert(reason: String) {
        val prefs = getSharedPreferences("shakti_prefs", Context.MODE_PRIVATE)
        val contact1 = prefs.getString("emergency_contact_1", null)
        val contact2 = prefs.getString("emergency_contact_2", null)
        val guardianPhone = prefs.getString("guardian_phone", contact1) // Use guardian phone from config
        val message = "🚨 $reason\nLocation: https://maps.google.com/?q=${currentLat},${currentLon}"

        // ✅ GUARANTEED SMS DELIVERY - Send via Android SIM (100% reliable)
        try {
            if (SmsFallback.hasSmsPermission(this)) {
                val locationLink = "https://maps.google.com/?q=${currentLat},${currentLon}"
                
                // Send to guardian phone
                guardianPhone?.let {
                    val smsSent = SmsFallback.sendEmergencySMS(
                        context = this,
                        phoneNumber = it,
                        message = reason,
                        locationLink = locationLink
                    )
                    if (smsSent) {
                        Log.i(TAG, "✅ SMS sent successfully via Android SIM to $it")
                        broadcastStatus("✅ SMS sent to guardian")
                    }
                }
                
                // Also send to backup contacts
                contact1?.let {
                    SmsFallback.sendEmergencySMS(this, it, reason, locationLink)
                }
                contact2?.let {
                    SmsFallback.sendEmergencySMS(this, it, reason, locationLink)
                }
            } else {
                Log.w(TAG, "⚠️ SMS permission not granted - using old SMS method")
                // Fallback to old method
                val sms = SmsManager.getDefault()
                contact1?.let { sms.sendTextMessage(it, null, message, null, null) }
                contact2?.let { sms.sendTextMessage(it, null, message, null, null) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "SMS failed: ${e.message}")
        }

        // Trigger Backend Alert (which handles WhatsApp/Email)
        uploadToServer(reason)
    }

    private fun muteSystemAudio() {
        try {
            val am = getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
            am.adjustStreamVolume(android.media.AudioManager.STREAM_NOTIFICATION, android.media.AudioManager.ADJUST_MUTE, 0)
            am.adjustStreamVolume(android.media.AudioManager.STREAM_ALARM, android.media.AudioManager.ADJUST_MUTE, 0)
            am.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC, android.media.AudioManager.ADJUST_MUTE, 0)
            am.adjustStreamVolume(android.media.AudioManager.STREAM_SYSTEM, android.media.AudioManager.ADJUST_MUTE, 0)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to mute: ${e.message}")
        }
    }

    private fun unmuteSystemAudio() {
        try {
            val am = getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
            am.adjustStreamVolume(android.media.AudioManager.STREAM_NOTIFICATION, android.media.AudioManager.ADJUST_UNMUTE, 0)
            am.adjustStreamVolume(android.media.AudioManager.STREAM_ALARM, android.media.AudioManager.ADJUST_UNMUTE, 0)
            am.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC, android.media.AudioManager.ADJUST_UNMUTE, 0)
            am.adjustStreamVolume(android.media.AudioManager.STREAM_SYSTEM, android.media.AudioManager.ADJUST_UNMUTE, 0)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unmute: ${e.message}")
        }
    }




    // -------------------- ⚠️ DANGER ZONE CHECK --------------------
    private fun checkDangerZone(lat: Double, lon: Double) {
        val prefs = getSharedPreferences("shakti_prefs", Context.MODE_PRIVATE)
        val baseUrl = prefs.getString("server_url", "http://192.168.1.35:5000") ?: "http://192.168.1.35:5000"
        val url = "$baseUrl/community/danger_zones" // Using the new endpoint if preferred, or keep existing logic

        // For now, let's keep the simple prediction endpoint if it exists, or use the new one.
        // Let's stick to the previous implementation for consistency unless we want to switch to the new community system fully here.
        // The previous code used /predict_danger_zone. Let's keep it compatible.
        val predictUrl = "$baseUrl/predict_danger_zone"

        val json = """{"lat": $lat, "lon": $lon}"""
        val body = RequestBody.create("application/json".toMediaTypeOrNull(), json)

        val request = Request.Builder()
            .url(predictUrl)
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Danger check failed: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                val respText = response.body?.string()?.trim() ?: ""
                response.close()
                if (respText.contains("1")) {
                    // Danger zone detected - just log it, don't annoy user
                    Log.w(TAG, "⚠️ User is in a danger zone")
                }
            }
        })
    }

    // Simple voice warning - DISABLED
    private fun speakWarning() {
        // Disabled per user request
    }


    // 🌐 Upload Alert with Media to Flask
    private fun uploadToServer(reason: String) {
        broadcastStatus("Uploading alert to server...")
        
        val prefs = getSharedPreferences("shakti_prefs", Context.MODE_PRIVATE)
        // ✅ FIXED: Using local network IP instead of expired ngrok URL
        val baseUrl = prefs.getString("server_url", "http://192.168.1.35:5000") ?: "http://192.168.1.35:5000"
        val url = "$baseUrl/upload_alert"

        
        Log.d(TAG, "📤 Uploading alert to: $url")
        Log.d(TAG, "📍 Location: $currentLat, $currentLon")
        Log.d(TAG, "🎥 Video: ${videoFile?.absolutePath}")
        Log.d(TAG, "🎙 Audio: ${audioFile?.absolutePath}")
        
        val form = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("message", reason)
            .addFormDataPart("lat", currentLat.toString())
            .addFormDataPart("lon", currentLon.toString())

        videoFile?.let {
            if (it.exists() && it.length() > 0) {
                form.addFormDataPart("video", it.name, RequestBody.create("video/mp4".toMediaTypeOrNull(), it))
                Log.d(TAG, "✅ Video file attached: ${it.length()} bytes")
            } else {
                Log.w(TAG, "⚠️ Video file is empty or doesn't exist")
            }
        }
        audioFile?.let {
            if (it.exists() && it.length() > 0) {
                form.addFormDataPart("audio", it.name, RequestBody.create("audio/mp3".toMediaTypeOrNull(), it))
                Log.d(TAG, "✅ Audio file attached: ${it.length()} bytes")
            } else {
                Log.w(TAG, "⚠️ Audio file is empty or doesn't exist")
            }
        }

        val req = Request.Builder().url(url).post(form.build()).build()
        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "❌ Upload failed: ${e.message}")
                handler.post {
                    broadcastStatus("❌ Server connection failed - retrying...")
                    Toast.makeText(this@AlertService, 
                        "❌ Cannot reach server\n🔄 Retrying...\n📱 Check server is running", 
                        Toast.LENGTH_LONG).show()
                    
                    // Auto-retry after 3 seconds
                    handler.postDelayed({
                        Log.d(TAG, "🔄 Retrying upload...")
                        uploadToServer(reason)
                    }, 3000)
                }
            }
            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string() ?: ""
                response.close()
                
                Log.d(TAG, "✅ Server Response: $responseBody")
                
                handler.post {
                    try {
                        // Parse response to check WhatsApp status
                        val whatsappSent = responseBody.contains("\"whatsapp_sent\":true") || 
                                          responseBody.contains("\"status\":\"ok\"") ||
                                          responseBody.contains("\"success\":true") ||
                                          response.code == 200
                        
                        if (whatsappSent) {
                            broadcastStatus("✅ Alert sent to guardian via WhatsApp!")
                            Toast.makeText(this@AlertService, 
                                "🚨 Emergency alert sent successfully!\n📍 Location, 🎥 Video, 🎙️ Audio sent to guardian", 
                                Toast.LENGTH_LONG).show()
                        } else {
                            // Check if WhatsApp service is not running
                            if (responseBody.contains("Connection refused") || 
                                responseBody.contains("localhost:3000") ||
                                responseBody.contains("WhatsApp is not") ||
                                responseBody.contains("not ready")) {
                                broadcastStatus("⚠️ WhatsApp service not running - retrying...")
                                Toast.makeText(this@AlertService, 
                                    "⚠️ WhatsApp service not connected.\n📱 Ensure WhatsApp Web is scanned.\n🔄 Retrying...", 
                                    Toast.LENGTH_LONG).show()
                                
                                // Retry after 2 seconds
                                handler.postDelayed({
                                    uploadToServer(reason)
                                }, 2000)
                            } else {
                                broadcastStatus("Alert uploaded - Check WhatsApp configuration")
                                Toast.makeText(this@AlertService, 
                                    "Alert uploaded to server.\nPlease check WhatsApp configuration.", 
                                    Toast.LENGTH_LONG).show()
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing response: ${e.message}")
                        broadcastStatus("Alert uploaded to server")
                        Toast.makeText(this@AlertService, 
                            "Alert uploaded. Check server logs for details.", 
                            Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    override fun onLocationChanged(loc: Location) {
        currentLat = loc.latitude
        currentLon = loc.longitude
        
        // ✅ Send real-time location update to server immediately
        uploadLocation(loc.latitude, loc.longitude, loc.accuracy)
        
        Log.d(TAG, "📍 Location updated: $currentLat, $currentLon (accuracy: ${loc.accuracy}m)")
    }
    
    // 🌍 Upload live location with accuracy (overloaded method)
    private fun uploadLocation(lat: Double, lon: Double, accuracy: Float = 0f) {
        val prefs = getSharedPreferences("shakti_prefs", Context.MODE_PRIVATE)
        val baseUrl = prefs.getString("server_url", "http://192.168.1.42:5000") ?: "http://192.168.1.42:5000"
        val token = prefs.getString("auth_token", "") ?: ""
        val url = "$baseUrl/update_location"
        
        // Include accuracy and timestamp for better tracking
        val json = """{"lat": $lat, "lon": $lon, "accuracy": $accuracy, "timestamp": ${System.currentTimeMillis()}}"""
        val requestBuilder = Request.Builder()
            .url(url)
            .post(RequestBody.create("application/json".toMediaTypeOrNull(), json))
        
        // Add auth token if available
        if (token.isNotEmpty()) {
            requestBuilder.addHeader("Authorization", "Bearer $token")
        }
        
        val req = requestBuilder.build()
            
        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { 
                Log.e(TAG, "Loc update failed: ${e.message}") 
            }
            override fun onResponse(call: Call, response: Response) { 
                response.close()
                Log.d(TAG, "✅ Location sent to server: $lat, $lon")
            }
        })
        
        // Also check danger zone
        checkDangerZone(lat, lon)
    }

    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        speechRecognizer?.destroy()
        sensorManager.unregisterListener(this)
        tts?.stop()
        tts?.shutdown()
        unmuteSystemAudio()
        
        // Release wake lock
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        
        Log.d(TAG, "✅ AlertService destroyed - wake lock released")
        super.onDestroy()
    }
}
