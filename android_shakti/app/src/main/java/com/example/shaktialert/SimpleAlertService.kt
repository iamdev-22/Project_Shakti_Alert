package com.example.shaktialert

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.*
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import android.content.pm.PackageManager
import android.widget.Toast
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import java.io.IOException
import java.util.*

/**
 * SIMPLE AlertService - No bugs, no complexity, just works!
 * - Listens for "help"
 * - Sends WhatsApp alert immediately
 * - No video/audio recording (causes bugs)
 * - No complex uploads (causes hanging)
 */
class SimpleAlertService : Service(), LocationListener {

    private val TAG = "SimpleAlertService"
    private lateinit var locationManager: LocationManager
    private var speechRecognizer: SpeechRecognizer? = null
    private var handler = Handler(Looper.getMainLooper())
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    private var currentLat = 0.0
    private var currentLon = 0.0
    
    // ✅ AUTO-RESTART LOCK
    // After alert is sent, listening pauses for AUTO_RESTART_DELAY_MS then restarts AUTOMATICALLY.
    // The server-side cooldown (5 min) prevents a second alert from actually being SENT.
    private var alertAlreadySent = false
    private val AUTO_RESTART_DELAY_MS = 30_000L  // 30 seconds pause after alert, then auto-restart

    // Debounce: prevent same utterance from triggering twice within 1.5 seconds
    private var isRecognitionActive = false
    private var lastHelpDetectionTime = 0L
    private val HELP_DETECTION_DEBOUNCE = 1500L

    override fun onCreate() {
        super.onCreate()
        startForegroundNotification()
        startLocationUpdates()
        getLastKnownLocation()
        Log.d(TAG, "✅ Simple AlertService started")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "START_LISTENING" -> {
                alertAlreadySent = false  // Fresh start
                handler.removeCallbacksAndMessages(null)  // Cancel any pending auto-restart
                startSpeechRecognition()
                startForegroundNotification(state = "listening")
                Log.d(TAG, "✅ Voice listening started")
            }
            "STOP_LISTENING" -> {
                stopListening()
                startForegroundNotification(state = "listening")
                Log.d(TAG, "⏸ Voice listening stopped")
            }
            "RESET_ALERT" -> {
                // Manual reset (from notification tap or app) — same as restart
                alertAlreadySent = false
                handler.removeCallbacksAndMessages(null)
                startSpeechRecognition()
                startForegroundNotification(state = "listening")
                Log.d(TAG, "🔄 Alert manually reset — listening resumed")
            }
        }
        return START_STICKY
    }

    private fun stopListening() {
        speechRecognizer?.stopListening()
        speechRecognizer?.destroy()
        speechRecognizer = null
        isRecognitionActive = false
        handler.removeCallbacksAndMessages(null)  // Cancel any pending restarts
    }

    private fun startForegroundNotification(state: String = "listening") {
        val channelId = "shakti_alert_channel"
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(channelId, "Shakti Alert", NotificationManager.IMPORTANCE_HIGH)
        )

        val notification = when (state) {
            "alert_sent" -> {
                // 🚨 ALERT SENT — auto-restarts in 30s, show countdown message
                NotificationCompat.Builder(this, channelId)
                    .setContentTitle("🚨 ALERT SENT — Guardian Notified!")
                    .setContentText("Listening will auto-resume in 30 seconds...")
                    .setSmallIcon(R.drawable.ic_emergency)
                    .setColor(android.graphics.Color.RED)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setOngoing(true)
                    .build()
            }
            "restarting" -> {
                NotificationCompat.Builder(this, channelId)
                    .setContentTitle("🔄 Restarting Listener...")
                    .setContentText("Alert sent. Restarting voice detection now.")
                    .setSmallIcon(R.drawable.ic_emergency)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setOngoing(true)
                    .build()
            }
            else -> {
                // Normal listening state
                NotificationCompat.Builder(this, channelId)
                    .setContentTitle("🛡️ Shakti Alert — Listening")
                    .setContentText("Say 'Help' to send emergency alert to guardian.")
                    .setSmallIcon(R.drawable.ic_emergency)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setOngoing(true)
                    .build()
            }
        }

        startForeground(1, notification)
    }

    private fun startLocationUpdates() {
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        try {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
            ) {
                Log.w(TAG, "⚠️ Location permission not granted")
                return
            }

            // ✅ FIX: Check which providers are available and use them
            val gpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
            val networkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

            Log.d(TAG, "GPS enabled: $gpsEnabled, Network enabled: $networkEnabled")

            // Use GPS if available
            if (gpsEnabled) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER, 5_000L, 5f, this
                )
                Log.d(TAG, "✅ GPS location tracking started")
            }

            // Always also use network provider as fallback (works indoors, no GPS signal needed)
            if (networkEnabled) {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER, 5_000L, 5f, this
                )
                Log.d(TAG, "✅ Network location tracking started")
            }

            if (!gpsEnabled && !networkEnabled) {
                Log.w(TAG, "⚠️ No location provider available. Check device settings.")
            }

            // Get last known location immediately
            getLastKnownLocation()

        } catch (e: Exception) {
            Log.e(TAG, "Location error: ${e.message}")
        }
    }

    private fun getLastKnownLocation() {
        try {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
            ) return

            // ✅ Try all available providers, pick the most accurate/recent
            val providers = listOf(
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER
            )

            var bestLocation: android.location.Location? = null
            for (provider in providers) {
                try {
                    if (!locationManager.isProviderEnabled(provider)) continue
                    val loc = locationManager.getLastKnownLocation(provider) ?: continue
                    if (bestLocation == null || loc.accuracy < bestLocation.accuracy) {
                        bestLocation = loc
                    }
                } catch (e: Exception) { /* provider not available */ }
            }

            bestLocation?.let {
                currentLat = it.latitude
                currentLon = it.longitude
                Log.d(TAG, "📍 Last known location: $currentLat, $currentLon (accuracy: ${it.accuracy}m)")
            } ?: Log.w(TAG, "No cached location available yet")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to get location: ${e.message}")
        }
    }

    private fun startSpeechRecognition() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return

        // Prevent overlapping recognition sessions
        if (isRecognitionActive) {
            Log.d(TAG, "⚠️ Speech recognition already active, skipping restart")
            return
        }

        handler.post {
            if (speechRecognizer == null) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            }
            
            isRecognitionActive = true
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            }

            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    isRecognitionActive = false
                }
                override fun onError(error: Int) {
                    isRecognitionActive = false
                    val errorName = when(error) {
                        SpeechRecognizer.ERROR_NO_MATCH -> "NO_MATCH"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "TIMEOUT"
                        SpeechRecognizer.ERROR_NETWORK -> "NETWORK"
                        else -> "ERROR_$error"
                    }
                    Log.d(TAG, "Speech recognition error: $errorName - will restart in 2s")
                    handler.postDelayed({ startSpeechRecognition() }, 2000) // Increased from 1s to 2s
                }
                override fun onResults(results: Bundle?) {
                    isRecognitionActive = false
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        val text = matches[0].lowercase(Locale.getDefault())
                        Log.d(TAG, "🎤 Voice detected: '$text'")

                        val isHelpWord = text.contains("help") ||
                                text.contains("bachao") ||
                                text.contains("save me") ||
                                text.contains("madad")

                        if (isHelpWord) {
                            val now = System.currentTimeMillis()

                            // ✅ ONE-SHOT LOCK: If alert already sent, ignore
                            if (alertAlreadySent) {
                                Log.d(TAG, "   ⛔ Alert already sent this session — BLOCKED")
                                // Do NOT restart listening after alert is sent
                                return
                            }

                            // Debounce: same utterance within 1 second
                            if (now - lastHelpDetectionTime < HELP_DETECTION_DEBOUNCE) {
                                Log.d(TAG, "   ⚠️ Debounced duplicate detection")
                                handler.postDelayed({ startSpeechRecognition() }, 1000)
                                return
                            }
                            lastHelpDetectionTime = now

                            // 🚨 SEND ALERT — ONE TIME only per session
                            Log.d(TAG, "   ✅ SENDING ALERT — will auto-restart listener in ${AUTO_RESTART_DELAY_MS/1000}s")
                            alertAlreadySent = true  // Prevent any second trigger during restart delay

                            // 🛑 Stop speech recognition immediately (prevents echo/re-detection)
                            stopListening()

                            // Show 'Alert Sent' notification
                            startForegroundNotification(state = "alert_sent")

                            // Send the alert
                            sendQuickAlert()

                            handler.post {
                                Toast.makeText(this@SimpleAlertService,
                                    "🚨 ALERT SENT! Guardian notified. Listening resumes in 30s.",
                                    Toast.LENGTH_LONG).show()
                            }

                            // Broadcast to HomeFragment
                            sendBroadcast(Intent("com.shakti.alert.STATUS").apply {
                                putExtra("status", "🚨 ALERT SENT! Re-listening in 30 seconds...")
                            })

                            // 🔄 AUTO-RESTART after delay (user-requested behavior)
                            // The server-side cooldown (5 min) prevents a second alert from being sent.
                            handler.postDelayed({
                                alertAlreadySent = false   // Re-arm for new genuine emergency
                                startForegroundNotification(state = "restarting")
                                handler.postDelayed({
                                    startSpeechRecognition()
                                    startForegroundNotification(state = "listening")
                                    sendBroadcast(Intent("com.shakti.alert.STATUS").apply {
                                        putExtra("status", "Listening... Say 'Help' for emergency")
                                    })
                                    Log.d(TAG, "🔄 AUTO-RESTARTED listening after alert")
                                }, 2000L)  // 2 more seconds to show 'restarting' state
                            }, AUTO_RESTART_DELAY_MS)

                            return  // Don't fall through to normal restart logic

                        } else {
                            Log.d(TAG, "   No help word in: '$text'")
                        }
                    } else {
                        Log.d(TAG, "   No speech detected")
                    }

                    // Only restart if alert has NOT been sent
                    if (!alertAlreadySent) {
                        handler.postDelayed({ startSpeechRecognition() }, 1000)
                    }
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            speechRecognizer?.startListening(intent)
        }
    }

    // ⚡ SIMPLE QUICK ALERT - Sends to Flask server + direct WhatsApp as backup!
    private fun sendQuickAlert() {
        val prefs = getSharedPreferences("shakti_prefs", Context.MODE_PRIVATE)
        val baseUrl = prefs.getString("server_url", "http://192.168.29.91:5000") ?: "http://192.168.29.91:5000"
        val guardianPhone = prefs.getString("guardian_phone", "") ?: ""
        val guardianPhone2 = prefs.getString("guardian_phone_2", "") ?: ""
        val serverUrl = prefs.getString("server_url", "http://192.168.29.91:5000") ?: "http://192.168.29.91:5000"
        val token = prefs.getString("auth_token", "") ?: ""

        val mapsLink = if (currentLat != 0.0 && currentLon != 0.0)
            "https://maps.google.com/?q=$currentLat,$currentLon"
        else "Location not available"

        val alertMessage = "🚨 *SHAKTI ALERT - EMERGENCY!*\n\n" +
                "Someone needs help!\n" +
                "📍 Location: $mapsLink\n" +
                "Coordinates: $currentLat, $currentLon\n\n" +
                "Please respond immediately!"

        // Method 1: Via Flask backend (includes location, logs, etc.)
        val url = "$baseUrl/quick_alert"
        Log.d(TAG, "⚡ Sending alert to Flask: $url")
        Log.d(TAG, "   Guardian: $guardianPhone")
        Log.d(TAG, "   Location: $currentLat, $currentLon")

        val json = org.json.JSONObject().apply {
            put("message", alertMessage)
            put("lat", currentLat)
            put("lon", currentLon)
            if (guardianPhone.isNotEmpty()) put("phone", guardianPhone)
            if (guardianPhone2.isNotEmpty()) put("phone_2", guardianPhone2)
        }.toString()

        val requestBody = RequestBody.create("application/json".toMediaTypeOrNull(), json)
        val reqBuilder = Request.Builder().url(url).post(requestBody)
        if (token.isNotEmpty()) reqBuilder.addHeader("Authorization", "Bearer $token")
        val req = reqBuilder.build()

        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "❌ Flask alert failed: ${e.message}")
                // Method 2 (fallback): Send directly to WhatsApp server
                if (guardianPhone.isNotEmpty()) {
                    sendDirectWhatsApp(serverUrl, guardianPhone, alertMessage)
                } else {
                    handler.post {
                        Toast.makeText(this@SimpleAlertService,
                            "❌ Alert failed — No guardian phone set! Go to Setup.",
                            Toast.LENGTH_LONG).show()
                    }
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string() ?: ""
                val statusCode = response.code
                response.close()
                Log.d(TAG, "Flask Response: $statusCode — $responseBody")

                handler.post {
                    when {
                        statusCode == 429 || responseBody.contains("\"status\":\"cooldown\"") -> {
                            Toast.makeText(this@SimpleAlertService,
                                "⏱️ Alert cooldown active. Already sent recently.",
                                Toast.LENGTH_LONG).show()
                        }
                        responseBody.contains("\"whatsapp_sent\":true") || statusCode in 200..299 -> {
                            Toast.makeText(this@SimpleAlertService,
                                "✅ ALERT SENT to Guardian via WhatsApp!",
                                Toast.LENGTH_LONG).show()
                            // Also send direct as confirmation
                            if (guardianPhone.isNotEmpty()) {
                                sendDirectWhatsApp(serverUrl, guardianPhone, alertMessage)
                            }
                        }
                        else -> {
                            // Flask failed — try direct WhatsApp
                            Log.w(TAG, "Flask returned $statusCode — trying direct WhatsApp")
                            if (guardianPhone.isNotEmpty()) {
                                sendDirectWhatsApp(serverUrl, guardianPhone, alertMessage)
                            } else {
                                Toast.makeText(this@SimpleAlertService,
                                    "❌ Server error $statusCode. Set guardian phone in Setup.",
                                    Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            }
        })
    }

    // Direct WhatsApp send — bypasses Flask, hits Node.js server directly
    private fun sendDirectWhatsApp(serverUrl: String, phone: String, message: String) {
        try {
            val uri = android.net.Uri.parse(serverUrl)
            val waUrl = "http://${uri.host ?: "192.168.29.91"}:3001/send-text"
            Log.d(TAG, "📱 Sending direct WhatsApp to $phone via $waUrl")

            val json = org.json.JSONObject().apply {
                put("phone", phone)
                put("message", message)
            }.toString()

            val body = RequestBody.create("application/json".toMediaTypeOrNull(), json)
            val req = Request.Builder().url(waUrl).post(body).build()

            client.newCall(req).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e(TAG, "❌ Direct WhatsApp failed: ${e.message}")
                    handler.post {
                        Toast.makeText(this@SimpleAlertService,
                            "❌ WhatsApp server unreachable. Ensure PC server is running.",
                            Toast.LENGTH_LONG).show()
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    val body2 = response.body?.string() ?: ""
                    response.close()
                    Log.d(TAG, "Direct WA Response: ${response.code} — $body2")
                    handler.post {
                        if (response.isSuccessful) {
                            Toast.makeText(this@SimpleAlertService,
                                "✅ WhatsApp alert sent to guardian!",
                                Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(this@SimpleAlertService,
                                "⚠️ WhatsApp send failed (${response.code}). Scan QR first.",
                                Toast.LENGTH_LONG).show()
                        }
                    }
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "sendDirectWhatsApp error: ${e.message}")
        }
    }

    override fun onLocationChanged(loc: Location) {
        currentLat = loc.latitude
        currentLon = loc.longitude
        Log.d(TAG, "📍 Location updated: $currentLat, $currentLon")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        speechRecognizer?.destroy()
        try { locationManager.removeUpdates(this) } catch (e: Exception) {}
        Log.d(TAG, "✅ Service destroyed")
        super.onDestroy()
    }
}
