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
                startSpeechRecognition()
                Log.d(TAG, "✅ Voice listening started")
            }
            "STOP_LISTENING" -> {
                speechRecognizer?.stopListening()
                speechRecognizer?.destroy()
                speechRecognizer = null
                Log.d(TAG, "⏸ Voice listening stopped")
            }
        }
        return START_STICKY
    }

    private fun startForegroundNotification() {
        val channelId = "shakti_alert_channel"
        val channel = NotificationChannel(channelId, "Shakti Alert", NotificationManager.IMPORTANCE_LOW)
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Shakti Alert Active")
            .setContentText("Listening for emergencies...")
            .setSmallIcon(R.drawable.ic_emergency)
            .build()

        startForeground(1, notification)
    }

    private fun startLocationUpdates() {
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        try {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
            ) return

            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                2_000L,
                0f,
                this
            )
            
            locationManager.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER,
                2_000L,
                0f,
                this
            )
            
            Log.d(TAG, "✅ Location tracking started")
        } catch (e: Exception) {
            Log.e(TAG, "Location error: ${e.message}")
        }
    }

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
                Log.d(TAG, "📍 Location: $currentLat, $currentLon")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get location: ${e.message}")
        }
    }

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
            }

            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) {
                    handler.postDelayed({ startSpeechRecognition() }, 1000)
                }
                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        val text = matches[0].lowercase(Locale.getDefault())
                        Log.d(TAG, "Voice: $text")
                        if (text.contains("help") || text.contains("bachao") || text.contains("save me")) {
                            sendQuickAlert()
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

    // ⚡ SIMPLE QUICK ALERT - Just send WhatsApp, no complexity!
    private fun sendQuickAlert() {
        val prefs = getSharedPreferences("shakti_prefs", Context.MODE_PRIVATE)
        val baseUrl = prefs.getString("server_url", "http://172.20.10.2:5000") ?: "http://172.20.10.2:5000"
        val url = "$baseUrl/quick_alert"

        Log.d(TAG, "⚡ Sending alert to: $url")

        val json = """
            {
                "message": "🚨 EMERGENCY ALERT - Help Needed!",
                "lat": $currentLat,
                "lon": $currentLon
            }
        """.trimIndent()

        val requestBody = RequestBody.create("application/json".toMediaTypeOrNull(), json)
        val req = Request.Builder().url(url).post(requestBody).build()

        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "❌ Alert failed: ${e.message}")
                handler.post {
                    Toast.makeText(this@SimpleAlertService,
                        "❌ Server offline - Check connection",
                        Toast.LENGTH_LONG).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string() ?: ""
                response.close()

                Log.d(TAG, "✅ Response: $responseBody")

                handler.post {
                    if (responseBody.contains("\"whatsapp_sent\":true")) {
                        Toast.makeText(this@SimpleAlertService,
                            "✅ Guardian alerted via WhatsApp!",
                            Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this@SimpleAlertService,
                            "⚠️ Alert sent but WhatsApp may be offline",
                            Toast.LENGTH_LONG).show()
                    }
                }
            }
        })
    }

    override fun onLocationChanged(loc: Location) {
        currentLat = loc.latitude
        currentLon = loc.longitude
        Log.d(TAG, "📍 Location updated: $currentLat, $currentLon")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        speechRecognizer?.destroy()
        Log.d(TAG, "✅ Service destroyed")
        super.onDestroy()
    }
}
