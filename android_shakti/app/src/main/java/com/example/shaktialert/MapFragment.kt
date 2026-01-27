package com.example.shaktialert

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.fragment.app.Fragment
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.json.JSONObject
import java.io.IOException

class MapFragment : Fragment() {

    private val client = OkHttpClient()
    private lateinit var webView: WebView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_map, container, false)
        
        webView = view.findViewById(R.id.webViewMap)
        
        // Enhanced WebView settings
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            setSupportZoom(true)
            builtInZoomControls = false
            loadWithOverviewMode = true
            useWideViewPort = true
            javaScriptCanOpenWindowsAutomatically = true
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = true
            allowContentAccess = true
        }
        
        // Add JavaScript interface for HTML to call Android functions
        webView.addJavascriptInterface(AndroidInterface(), "Android")
        
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                Log.d("MapFragment", "Map page loaded: $url")
                
                // Load saved circle and update UI
                view?.postDelayed({
                    // Load saved circle
                    loadSavedCircle(webView)
                    
                    // Start location tracking
                    startLocationTracking(webView)
                    Log.d("MapFragment", "Location tracking started")
                    
                    // Start periodic member reload
                    startPeriodicMemberReload(webView)
                }, 1000)
            }
            
            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                Log.e("MapFragment", "WebView error: ${error?.description}")
            }
        }
        
        webView.webChromeClient = android.webkit.WebChromeClient()
        
        // Clear WebView cache to ensure fresh HTML is loaded
        webView.clearCache(true)
        webView.clearHistory()
        
        // Load the HTML map
        webView.loadUrl("file:///android_asset/live_map.html")

        return view
    }
    
    // JavaScript Interface for HTML to call Android functions
    inner class AndroidInterface {
        @JavascriptInterface
        fun getBatteryLevel(): Int {
            val batteryManager = requireContext().getSystemService(Context.BATTERY_SERVICE) as? android.os.BatteryManager
            return batteryManager?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 100
        }
        
        @JavascriptInterface
        fun getUserData(): String {
            val prefs = requireContext().getSharedPreferences("shakti_prefs", Context.MODE_PRIVATE)
            val name = prefs.getString("user_name", "User") ?: "User"
            val email = prefs.getString("user_email", "user@example.com") ?: "user@example.com"
            val photoUri = prefs.getString("user_photo_uri", "") ?: ""
            
            val json = JSONObject()
            json.put("name", name)
            json.put("email", email)
            json.put("photoUri", photoUri)
            
            return json.toString()
        }
        
        
        @JavascriptInterface
        fun onChatClicked() {
            activity?.runOnUiThread {
                try {
                    GroupChatActivity.start(requireContext(), 1, "Shakti Alert Group")
                } catch (e: Exception) {
                    Toast.makeText(context, "Chat feature coming soon!", Toast.LENGTH_SHORT).show()
                }
            }
        }
        
        @JavascriptInterface
        fun onProfileClicked() {
            activity?.runOnUiThread {
                try {
                    // Navigate to ProfileFragment
                    val bottomNav = activity?.findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottom_navigation)
                    bottomNav?.selectedItemId = R.id.nav_profile
                } catch (e: Exception) {
                    Toast.makeText(context, "Profile", Toast.LENGTH_SHORT).show()
                }
            }
        }
        
        @JavascriptInterface
        fun onSettingsClicked() {
            activity?.runOnUiThread {
                try {
                    // Navigate to SettingsFragment
                    val bottomNav = activity?.findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottom_navigation)
                    bottomNav?.selectedItemId = R.id.nav_settings
                } catch (e: Exception) {
                    Toast.makeText(context, "Settings", Toast.LENGTH_SHORT).show()
                }
            }
        }
        
        @JavascriptInterface
        fun onNotificationsClicked() {
            activity?.runOnUiThread {
                Toast.makeText(context, "Notifications", Toast.LENGTH_SHORT).show()
            }
        }
        
        @JavascriptInterface
        fun onHelpClicked() {
            activity?.runOnUiThread {
                try {
                    val intent = android.content.Intent(requireContext(), AiHelpActivity::class.java)
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(context, "AI Help coming soon!", Toast.LENGTH_SHORT).show()
                }
            }
        }
        
        @JavascriptInterface
        fun onWhatsAppClicked() {
            activity?.runOnUiThread {
                try {
                    // Navigate to WhatsApp fragment
                    val fragmentManager = activity?.supportFragmentManager
                    fragmentManager?.beginTransaction()
                        ?.replace(R.id.fragment_container, WhatsAppFragmentNative())
                        ?.addToBackStack(null)
                        ?.commit()
                } catch (e: Exception) {
                    Toast.makeText(context, "WhatsApp integration coming soon!", Toast.LENGTH_SHORT).show()
                }
            }
        }
        
        @JavascriptInterface
        fun onLogoutClicked() {
            activity?.runOnUiThread {
                val prefs = requireContext().getSharedPreferences("shakti_prefs", Context.MODE_PRIVATE)
                val serverUrl = prefs.getString("server_url", "http://192.168.1.35:5000") ?: "http://192.168.1.35:5000"
                val token = prefs.getString("auth_token", "") ?: ""
                
                // Call logout API
                val request = Request.Builder()
                    .url("$serverUrl/auth/logout")
                    .addHeader("Authorization", "Bearer $token")
                    .post(RequestBody.create(null, ByteArray(0)))
                    .build()
                
                client.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        // Even if API fails, logout locally
                        activity?.runOnUiThread {
                            performLogout()
                        }
                    }
                    
                    override fun onResponse(call: Call, response: Response) {
                        response.close()
                        activity?.runOnUiThread {
                            performLogout()
                        }
                    }
                })
            }
        }
        
        private fun performLogout() {
            // Clear all stored data
            val prefs = requireContext().getSharedPreferences("shakti_prefs", Context.MODE_PRIVATE)
            prefs.edit().clear().apply()
            
            // Navigate to login
            val intent = android.content.Intent(requireContext(), LoginActivity::class.java)
            intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            activity?.finish()
        }
        
        @JavascriptInterface
        fun onCreateCircle(circleName: String) {
            activity?.runOnUiThread {
                val prefs = requireContext().getSharedPreferences("shakti_prefs", Context.MODE_PRIVATE)
                val serverUrl = prefs.getString("server_url", "http://192.168.1.35:5000") ?: "http://192.168.1.35:5000"
                val token = prefs.getString("auth_token", "") ?: ""
                
                if (token.isEmpty()) {
                    Toast.makeText(context, "Please login first", Toast.LENGTH_LONG).show()
                    return@runOnUiThread
                }
                
                Log.d("MapFragment", "Creating circle: $circleName")
                createCircleOnServer(circleName, serverUrl, token)
            }
        }
        
        @JavascriptInterface
        fun onJoinCircle(inviteCode: String) {
            activity?.runOnUiThread {
                val prefs = requireContext().getSharedPreferences("shakti_prefs", Context.MODE_PRIVATE)
                val serverUrl = prefs.getString("server_url", "http://192.168.1.35:5000") ?: "http://192.168.1.35:5000"
                val token = prefs.getString("auth_token", "") ?: ""
                
                if (token.isEmpty()) {
                    Toast.makeText(context, "Please login first", Toast.LENGTH_LONG).show()
                    return@runOnUiThread
                }
                
                Log.d("MapFragment", "Joining circle with code: $inviteCode")
                joinCircleOnServer(inviteCode, serverUrl, token)
            }
        }
    }
    
    private fun createCircleOnServer(name: String, serverUrl: String, token: String) {
        Log.d("MapFragment", "createCircleOnServer called with name: $name")
        
        val json = JSONObject()
        json.put("name", name)

        val body = RequestBody.create("application/json".toMediaTypeOrNull(), json.toString())
        val request = Request.Builder()
            .url("$serverUrl/api/circles/create")
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()

        Log.d("MapFragment", "Sending request to: $serverUrl/circles/create")

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("MapFragment", "Network error: ${e.message}")
                activity?.runOnUiThread {
                    val safeMessage = (e.message ?: "Network error").replace("'", "\\'").replace("\n", "\\n")
                    webView.evaluateJavascript(
                        "closeCreateCircleDialog(); " +
                        "alert('Network error: $safeMessage\\n\\nPlease check your internet connection.');" ,
                        null
                    )
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val respStr = response.body?.string() ?: ""
                Log.d("MapFragment", "Response code: ${response.code}")
                Log.d("MapFragment", "Response body: $respStr")
                
                activity?.runOnUiThread {
                    try {
                        if (respStr.trim().startsWith("<!") || respStr.trim().startsWith("<html")) {
                            Log.e("MapFragment", "Received HTML instead of JSON")
                            // Show error in HTML dialog
                            webView.evaluateJavascript(
                                "closeCreateCircleDialog(); " +
                                "alert('Server error. Please check if backend is running.');",
                                null
                            )
                            return@runOnUiThread
                        }

                        val jsonResp = JSONObject(respStr)
                        if (jsonResp.optBoolean("success")) {
                            val circleId = jsonResp.optInt("circle_id", 0)
                            val inviteCode = jsonResp.optString("invite_code", "")
                            Log.d("MapFragment", "Circle created successfully. ID: $circleId, Code: $inviteCode")
                            
                            // SAVE CIRCLE LOCALLY SO IT PERSISTS
                            if (circleId > 0 && inviteCode.isNotEmpty()) {
                                try {
                                    val circleManager = CircleManager(requireContext())
                                    circleManager.saveCircle(circleId, name, inviteCode)
                                    circleManager.setCurrentCircle(circleId)
                                    Log.d("MapFragment", "Circle saved locally: $name")
                                    
                                    // Update group selector to show actual group name
                                    webView.evaluateJavascript(
                                        "document.querySelector('.group-selector').textContent = '👥 $name';",
                                        null
                                    )
                                } catch (e: Exception) {
                                    Log.e("MapFragment", "Error saving circle: ${e.message}")
                                }
                            }
                            
                            if (inviteCode.isNotEmpty()) {
                                // Show invite code in HTML dialog
                                webView.evaluateJavascript(
                                    "document.getElementById('generatedCode').value = '$inviteCode'; " +
                                    "document.getElementById('createCircleDialog').classList.remove('active'); " +
                                    "document.getElementById('inviteCodeDialog').classList.add('active');",
                                    null
                                )
                            } else {
                                webView.evaluateJavascript(
                                    "closeCreateCircleDialog(); " +
                                    "alert('Circle created but no invite code received');",
                                    null
                                )
                            }
                        } else {
                            val error = jsonResp.optString("error", "Failed to create circle")
                            Log.e("MapFragment", "Server error: $error")
                            
                            // Check if token expired
                            if (error.contains("Token expired", ignoreCase = true) || 
                                error.contains("token is invalid", ignoreCase = true) ||
                                error.contains("authentication", ignoreCase = true)) {
                                
                                Log.e("MapFragment", "Token expired! Clearing token...")
                                
                                // Clear expired token
                                val prefs = requireContext().getSharedPreferences("shakti_prefs", Context.MODE_PRIVATE)
                                prefs.edit().remove("auth_token").apply()
                                
                                // Show login prompt
                                webView.evaluateJavascript(
                                    "closeCreateCircleDialog(); " +
                                    "alert('Your session has expired.\\n\\nPlease login again to continue.');",
                                    null
                                )
                                
                                // Redirect to login after 2 seconds
                                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                    try {
                                        val intent = android.content.Intent(requireContext(), LoginActivity::class.java)
                                        intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
                                        startActivity(intent)
                                        activity?.finish()
                                    } catch (e: Exception) {
                                        Log.e("MapFragment", "Could not redirect to login: ${e.message}")
                                    }
                                }, 2000)
                            } else {
                                // Show other errors
                                val safeError = error.replace("'", "\\'").replace("\n", "\\n")
                                webView.evaluateJavascript(
                                    "closeCreateCircleDialog(); " +
                                    "alert('Error: $safeError');",
                                    null
                                )
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("MapFragment", "Parse error: ${e.message}")
                        Log.e("MapFragment", "Response was: $respStr")
                        Log.e("MapFragment", "Stack trace:", e)
                        // Show error in HTML dialog
                        val safeMessage = (e.message ?: "Unknown error").replace("'", "\\'").replace("\n", "\\n")
                        webView.evaluateJavascript(
                            "closeCreateCircleDialog(); " +
                            "alert('Error: $safeMessage\\n\\nPlease check Logcat for details.');",
                            null
                        )
                    }
                }
            }
        })
    }
    
    private fun joinCircleOnServer(inviteCode: String, serverUrl: String, token: String) {
        val json = JSONObject()
        json.put("invite_code", inviteCode)

        val body = RequestBody.create("application/json".toMediaTypeOrNull(), json.toString())
        val request = Request.Builder()
            .url("$serverUrl/api/circles/join")
            .addHeader("Authorization", "Bearer $token")
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                activity?.runOnUiThread {
                    Toast.makeText(context, "Failed to join circle: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val respStr = response.body?.string()
                response.close()
                
                activity?.runOnUiThread {
                    try {
                        val jsonResp = JSONObject(respStr ?: "{}")
                        if (jsonResp.optBoolean("success")) {
                            val circleId = jsonResp.optInt("circle_id", 0)
                            val circleName = jsonResp.optString("circle_name", "Circle")
                            
                            Toast.makeText(context, "Joined $circleName successfully!", Toast.LENGTH_LONG).show()
                            
                            // SAVE CIRCLE LOCALLY SO IT PERSISTS
                            if (circleId > 0) {
                                try {
                                    val circleManager = CircleManager(requireContext())
                                    circleManager.saveCircle(circleId, circleName, inviteCode)
                                    circleManager.setCurrentCircle(circleId)
                                    Log.d("MapFragment", "Joined circle saved locally: $circleName")
                                    
                                    // Update group selector to show actual circle name
                                    webView.evaluateJavascript(
                                        "document.querySelector('.group-selector').textContent = '👥 $circleName';",
                                        null
                                    )
                                } catch (e: Exception) {
                                    Log.e("MapFragment", "Error saving joined circle: ${e.message}")
                                }
                            }
                            
                            // Close dialog
                            webView.evaluateJavascript(
                                "document.getElementById('joinCircleDialog').classList.remove('active');",
                                null
                            )
                            
                            // Reload circle members to show the new member
                            if (circleId > 0) {
                                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                    loadCircleMembersForMap(webView)
                                }, 500)
                            }
                        } else {
                            Toast.makeText(context, jsonResp.optString("error", "Failed to join circle"), Toast.LENGTH_LONG).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }
    
    private fun startLocationTracking(webView: WebView) {
        try {
            val locationManager = requireContext().getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager
            if (locationManager == null) {
                Log.e("MapFragment", "LocationManager is null")
                Toast.makeText(context, "Location service unavailable", Toast.LENGTH_SHORT).show()
                return
            }
            
            if (androidx.core.app.ActivityCompat.checkSelfPermission(
                    requireContext(),
                    android.Manifest.permission.ACCESS_FINE_LOCATION
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                Log.w("MapFragment", "Location permission not granted")
                Toast.makeText(context, "Please grant location permission", Toast.LENGTH_LONG).show()
                return
            }
            
            val locationListener = object : android.location.LocationListener {
                override fun onLocationChanged(location: android.location.Location) {
                    try {
                        val lat = location.latitude
                        val lon = location.longitude
                        val accuracy = location.accuracy
                        
                        if (lat == 0.0 && lon == 0.0) {
                            Log.w("MapFragment", "Invalid location: 0,0")
                            return
                        }
                        
                        Log.d("MapFragment", "Location updated: $lat, $lon, accuracy: $accuracy meters")
                        
                        // Get profile photo
                        val prefs = requireContext().getSharedPreferences("shakti_prefs", Context.MODE_PRIVATE)
                        val photoUriStr = prefs?.getString("user_photo_uri", "") ?: ""
                        
                        // Update map
                        try {
                            val sanitizedPhotoUrl = photoUriStr.replace("'", "\\'")
                            val jsCommand = "updateUserLocation($lat, $lon, $accuracy, '$sanitizedPhotoUrl');"
                            Log.d("MapFragment", "📍 Updating location: Lat=$lat, Lon=$lon, Accuracy=$accuracy")
                            
                            webView.post {
                                webView.evaluateJavascript(jsCommand) { result ->
                                    Log.d("MapFragment", "JS result: $result")
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("MapFragment", "Error updating location: ${e.message}")
                        }
                        
                        // Send to server
                        sendLocationToServer(lat, lon)
                        
                        // Load circle members
                        loadCircleMembersForMap(webView)
                    } catch (e: Exception) {
                        Log.e("MapFragment", "Error in onLocationChanged: ${e.message}")
                    }
                }
                
                override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
                override fun onProviderEnabled(provider: String) {
                    Log.d("MapFragment", "Location provider enabled: $provider")
                }
                override fun onProviderDisabled(provider: String) {
                    Log.w("MapFragment", "Location provider disabled: $provider")
                    Toast.makeText(context, "Please enable GPS", Toast.LENGTH_SHORT).show()
                }
            }
            
            try {
                locationManager.requestLocationUpdates(
                    android.location.LocationManager.GPS_PROVIDER,
                    2000L,
                    0f,
                    locationListener
                )
                
                Log.d("MapFragment", "Location updates requested")
                
                // Get last known location
                val lastLocation = locationManager.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)
                    ?: locationManager.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER)
                
                lastLocation?.let {
                    val accuracy = it.accuracy
                    Log.d("MapFragment", "Last known location: ${it.latitude}, ${it.longitude}, accuracy: $accuracy meters")
                    
                    val prefs = requireContext().getSharedPreferences("shakti_prefs", Context.MODE_PRIVATE)
                    val photoUriStr = prefs.getString("user_photo_uri", "")
                    
                    webView.postDelayed({
                        val sanitizedPhotoUrl = photoUriStr?.replace("'", "\\'") ?: ""
                        val jsCommand = "updateUserLocation(${it.latitude}, ${it.longitude}, $accuracy, '$sanitizedPhotoUrl');"
                        Log.d("MapFragment", "📍 Initial location: Lat=${it.latitude}, Lon=${it.longitude}")
                        
                        try {
                            webView.evaluateJavascript(jsCommand) { result ->
                                Log.d("MapFragment", "Initial JS result: $result")
                            }
                            Toast.makeText(context, "Location marker added (±${accuracy.toInt()}m)", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Log.e("MapFragment", "Error setting initial location: ${e.message}")
                        }
                    }, 500)
                } ?: run {
                    Log.w("MapFragment", "No last known location available")
                    Toast.makeText(context, "Waiting for GPS signal...", Toast.LENGTH_SHORT).show()
                }
            } catch (e: SecurityException) {
                Log.e("MapFragment", "Security exception: ${e.message}")
                Toast.makeText(context, "Location permission error", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Log.e("MapFragment", "Error starting location tracking: ${e.message}")
            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun sendLocationToServer(lat: Double, lon: Double) {
        val prefs = requireContext().getSharedPreferences("shakti_prefs", Context.MODE_PRIVATE)
        val baseUrl = prefs.getString("server_url", "http://192.168.1.35:5000") ?: return
        val token = prefs.getString("auth_token", "") ?: return
        
        // Get battery level
        val batteryManager = requireContext().getSystemService(Context.BATTERY_SERVICE) as? android.os.BatteryManager
        val batteryLevel = batteryManager?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 0
        
        val json = JSONObject()
        json.put("lat", lat)
        json.put("lon", lon)
        json.put("accuracy", 10.0)
        json.put("timestamp", System.currentTimeMillis())
        json.put("battery", batteryLevel)  // Add battery level
        
        Log.d("MapFragment", "Sending location: ($lat, $lon) Battery: $batteryLevel%")
        
        val body = RequestBody.create("application/json".toMediaTypeOrNull(), json.toString())
        val request = Request.Builder()
            .url("$baseUrl/update_location")
            .addHeader("Authorization", "Bearer $token")
            .post(body)
            .build()
        
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) {
                response.close()
            }
        })
    }
    
    private fun loadCircleMembersForMap(webView: WebView) {
        val prefs = requireContext().getSharedPreferences("shakti_prefs", Context.MODE_PRIVATE)
        val baseUrl = prefs.getString("server_url", "http://192.168.1.35:5000") ?: return
        val token = prefs.getString("auth_token", "") ?: return
        
        val request = Request.Builder()
            .url("$baseUrl/api/circles/list")
            .addHeader("Authorization", "Bearer $token")
            .get()
            .build()
        
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            
            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string() ?: ""
                response.close()
                
                try {
                    val json = JSONObject(responseBody)
                    if (json.getBoolean("success")) {
                        val circlesArray = json.getJSONArray("circles")
                        if (circlesArray.length() > 0) {
                            val firstCircle = circlesArray.getJSONObject(0)
                            val circleId = firstCircle.getInt("id")
                            loadMembersLocations(webView, circleId)
                        }
                    }
                } catch (e: Exception) {}
            }
        })
    }
    
    private fun loadMembersLocations(webView: WebView, circleId: Int) {
        val prefs = requireContext().getSharedPreferences("shakti_prefs", Context.MODE_PRIVATE)
        val baseUrl = prefs.getString("server_url", "http://192.168.1.35:5000") ?: return
        val token = prefs.getString("auth_token", "") ?: return
        
        Log.d("MapFragment", "Loading members for circle: $circleId")
        
        val request = Request.Builder()
            .url("$baseUrl/api/circles/$circleId/members")
            .addHeader("Authorization", "Bearer $token")
            .get()
            .build()
        
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("MapFragment", "Failed to load members: ${e.message}")
            }
            
            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string() ?: ""
                response.close()
                
                Log.d("MapFragment", "Members response: $responseBody")
                
                try {
                    val json = JSONObject(responseBody)
                    if (json.getBoolean("success")) {
                        val membersArray = json.getJSONArray("members")
                        
                        Log.d("MapFragment", "Found ${membersArray.length()} members")
                        
                        activity?.runOnUiThread {
                            for (i in 0 until membersArray.length()) {
                                val member = membersArray.getJSONObject(i)
                                val userId = member.getInt("user_id")
                                val name = member.getString("name")
                                val lastName = member.optString("last_name", "")
                                val fullName = if (lastName.isNotEmpty()) "$name $lastName" else name
                                val lat = member.optDouble("lat", 0.0)
                                val lon = member.optDouble("lon", 0.0)
                                val photoUrl = member.optString("profile_photo", "")
                                
                                Log.d("MapFragment", "Member: $fullName (ID: $userId) at ($lat, $lon)")
                                
                                // Show member on map if they have location
                                if (lat != 0.0 && lon != 0.0) {
                                    webView.evaluateJavascript(
                                        "updateMemberLocation($userId, '$fullName', $lat, $lon, '$photoUrl', 'Active');",
                                        null
                                    )
                                } else {
                                    // Show member in list even without location
                                    webView.evaluateJavascript(
                                        "updateMemberInList($userId, '$fullName', 'Location not available', 'Offline', 0, '$photoUrl');",
                                        null
                                    )
                                }
                            }
                        }
                    } else {
                        Log.e("MapFragment", "Failed to get members: ${json.optString("error")}")
                    }
                } catch (e: Exception) {
                    Log.e("MapFragment", "Error parsing members: ${e.message}")
                }
            }
        })
    }
    
    private fun startPeriodicMemberReload(webView: WebView) {
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val runnable = object : Runnable {
            override fun run() {
                loadCircleMembersForMap(webView)
                handler.postDelayed(this, 10000) // Reload every 10 seconds
            }
        }
        handler.postDelayed(runnable, 10000) // Start after 10 seconds
    }
    
    private fun loadSavedCircle(webView: WebView) {
        try {
            val circleManager = CircleManager(requireContext())
            val currentCircle = circleManager.getCurrentCircle()
            
            if (currentCircle != null) {
                Log.d("MapFragment", "Loading saved circle: ${currentCircle.name} (ID: ${currentCircle.id})")
                
                // Update group selector to show actual circle name
                webView.evaluateJavascript(
                    "document.querySelector('.group-selector').textContent = '👥 ${currentCircle.name}';",
                    null
                )
                
                // Load members for this circle
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    loadCircleMembersForMap(webView)
                }, 500)
            } else {
                Log.d("MapFragment", "No saved circle found")
                // Show default text when no circle
                webView.evaluateJavascript(
                    "document.querySelector('.group-selector').textContent = '👥 No Circle';",
                    null
                )
            }
        } catch (e: Exception) {
            Log.e("MapFragment", "Error loading saved circle: ${e.message}")
        }
    }
}
