package com.example.shaktialert

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.material.bottomsheet.BottomSheetBehavior
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.json.JSONObject
import java.io.IOException

/**
 * Live Map Fragment - Native Google Maps with real-time location updates
 * Replaces WebView map with continuous location tracking
 */
class LiveMapFragment : Fragment(), OnMapReadyCallback, LocationListener {

    private val TAG = "LiveMapFragment"
    private val client = OkHttpClient()
    private var googleMap: GoogleMap? = null
    private lateinit var locationManager: LocationManager
    
    private var currentLat = 0.0
    private var currentLon = 0.0
    private var isLocationSet = false
    
    private lateinit var bottomSheet: View
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<View>
    private lateinit var circleNameText: TextView
    private lateinit var memberCountText: TextView
    private lateinit var imgProfileBottomSheet: ImageView
    
    private val userMarkers = mutableMapOf<Int, Marker>()
    private var myMarker: Marker? = null
    
    private val handler = Handler(Looper.getMainLooper())
    private val updateInterval = 5000L // Update every 5 seconds
    
    private var currentCircleId: Int = 0
    private var currentCircleName: String = "Shakti Alert Group"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_live_map, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        try {
            // Initialize views first
            circleNameText = view.findViewById(R.id.tvCircleName)
            memberCountText = view.findViewById(R.id.tvMemberCount)
            imgProfileBottomSheet = view.findViewById(R.id.imgProfileBottomSheet)
            
            val btnCreateCircle = view.findViewById<Button>(R.id.btnCreateCircle)
            val btnJoinCircle = view.findViewById<Button>(R.id.btnJoinCircle)
            val cardFamilySelector = view.findViewById<View>(R.id.cardFamilySelector)
            
            // Initialize bottom sheet
            bottomSheet = view.findViewById(R.id.bottomSheet)
            bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet)
            bottomSheetBehavior.peekHeight = 200
            
            // Load profile photo
            val prefs = requireContext().getSharedPreferences("shakti_prefs", Context.MODE_PRIVATE)
            val photoUriStr = prefs.getString("user_photo_uri", null)
            if (photoUriStr != null) {
                try {
                    imgProfileBottomSheet.setImageURI(android.net.Uri.parse(photoUriStr))
                } catch (e: Exception) {
                    Log.e(TAG, "Error loading profile photo: ${e.message}")
                }
            }
            
            // Circle name click - show slide-up panel
            cardFamilySelector.setOnClickListener {
                if (bottomSheetBehavior.state == BottomSheetBehavior.STATE_COLLAPSED) {
                    bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
                } else {
                    bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
                }
            }
            
            // Create Circle button
            btnCreateCircle.setOnClickListener {
                showCreateCircleDialog()
            }
            
            // Join Circle button
            btnJoinCircle.setOnClickListener {
                showJoinCircleDialog()
            }
            
            // WhatsApp button
            val fabWhatsApp = view.findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.fabWhatsApp)
            fabWhatsApp.setOnClickListener {
                try {
                    parentFragmentManager.beginTransaction()
                        .replace(R.id.fragment_container, WhatsAppFragmentNative())
                        .addToBackStack(null)
                        .commit()
                } catch (e: Exception) {
                    Toast.makeText(context, "Error opening WhatsApp: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
            
            // Initialize map - MUST be done after view is created
            val mapFragment = childFragmentManager.findFragmentById(R.id.map) as? SupportMapFragment
            if (mapFragment == null) {
                Log.e(TAG, "Map fragment is null!")
                Toast.makeText(context, "Error loading map", Toast.LENGTH_SHORT).show()
            } else {
                mapFragment.getMapAsync(this)
                Log.d(TAG, "Map fragment found, requesting map async")
            }
            
            // Start location updates
            startLocationUpdates()
            
            // Load user's circles
            loadUserCircles()
            
            // Start periodic updates
            startPeriodicUpdates()
            
            Log.d(TAG, "LiveMapFragment initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing: ${e.message}")
            e.printStackTrace()
            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onMapReady(map: GoogleMap) {
        try {
            Log.d(TAG, "onMapReady called!")
            Toast.makeText(context, "Map is loading...", Toast.LENGTH_SHORT).show()
            
            googleMap = map
            
            googleMap?.apply {
                mapType = GoogleMap.MAP_TYPE_NORMAL
                uiSettings.isZoomControlsEnabled = true
                uiSettings.isMyLocationButtonEnabled = true
                
                if (ActivityCompat.checkSelfPermission(
                        requireContext(),
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    isMyLocationEnabled = false // We'll use custom marker
                }
                
                if (currentLat != 0.0 && currentLon != 0.0) {
                    val initialPos = LatLng(currentLat, currentLon)
                    moveCamera(CameraUpdateFactory.newLatLngZoom(initialPos, 15f))
                    // Add initial marker only if location valid
                    updateMyMarker(currentLat, currentLon)
                } else {
                    Toast.makeText(context, "Waiting for GPS...", Toast.LENGTH_LONG).show()
                }
            } // End googleMap.apply
            
            // Load circle members
            loadCircleMembers()
            
            Toast.makeText(context, "Map loaded successfully!", Toast.LENGTH_SHORT).show()
            Log.d(TAG, "Map ready and initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Error in onMapReady: ${e.message}")
            e.printStackTrace()
            Toast.makeText(context, "Map error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun startLocationUpdates() {
        try {
            locationManager = requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager
            
            if (ActivityCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                2000L,  // 2 seconds
                0f,
                this
            )
            
            val lastLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            
            lastLocation?.let {
                currentLat = it.latitude
                currentLon = it.longitude
                updateMyMarker(it.latitude, it.longitude)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting location updates: ${e.message}")
        }
    }

    override fun onLocationChanged(location: Location) {
        currentLat = location.latitude
        currentLon = location.longitude
        updateMyMarker(location.latitude, location.longitude)
        
        // Send location to server
        sendLocationToServer(location.latitude, location.longitude)
    }

    private fun updateMyMarker(lat: Double, lon: Double) {
        try {
            val position = LatLng(lat, lon)
            
            if (myMarker == null) {
                // Create new marker
                myMarker = googleMap?.addMarker(
                    MarkerOptions()
                        .position(position)
                        .title("You")
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
                )
                
                // Center camera on first update
                googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(position, 14f))
            } else {
                // Update existing marker position
                myMarker?.position = position
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating marker: ${e.message}")
        }
    }

    private fun sendLocationToServer(lat: Double, lon: Double) {
        val prefs = requireContext().getSharedPreferences("shakti_prefs", Context.MODE_PRIVATE)
        val baseUrl = prefs.getString("server_url", "http://192.168.1.35:5000") ?: return
        val token = prefs.getString("auth_token", "") ?: return
        
        val json = JSONObject()
        json.put("latitude", lat)
        json.put("longitude", lon)
        json.put("accuracy", 10.0)
        
        val body = RequestBody.create("application/json".toMediaTypeOrNull(), json.toString())
        val request = Request.Builder()
            .url("$baseUrl/location/update")
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

    private fun startPeriodicUpdates() {
        handler.postDelayed(object : Runnable {
            override fun run() {
                loadCircleMembers()
                handler.postDelayed(this, updateInterval)
            }
        }, updateInterval)
    }

    private fun loadUserCircles() {
        val prefs = requireContext().getSharedPreferences("shakti_prefs", Context.MODE_PRIVATE)
        val baseUrl = prefs.getString("server_url", "http://192.168.1.35:5000") ?: return
        val token = prefs.getString("auth_token", "") ?: return
        
        val request = Request.Builder()
            .url("$baseUrl/circles/list")
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
                            currentCircleId = firstCircle.getInt("id")
                            currentCircleName = firstCircle.getString("name")
                            val memberCount = firstCircle.getInt("member_count")
                            
                            activity?.runOnUiThread {
                                circleNameText.text = currentCircleName
                                memberCountText.text = "$memberCount members"
                                loadCircleMembers()
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing circles: ${e.message}")
                }
            }
        })
    }

    private fun loadCircleMembers() {
        if (currentCircleId == 0) return
        
        val prefs = requireContext().getSharedPreferences("shakti_prefs", Context.MODE_PRIVATE)
        val baseUrl = prefs.getString("server_url", "http://192.168.1.35:5000") ?: return
        val token = prefs.getString("auth_token", "") ?: return
        
        val request = Request.Builder()
            .url("$baseUrl/circles/$currentCircleId/members")
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
                        val membersArray = json.getJSONArray("members")
                        
                        activity?.runOnUiThread {
                            updateMemberMarkers(membersArray)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error loading members: ${e.message}")
                }
            }
        })
    }

    private fun updateMemberMarkers(membersArray: org.json.JSONArray) {
        try {
            val currentMemberIds = mutableSetOf<Int>()
            
            for (i in 0 until membersArray.length()) {
                val memberObj = membersArray.getJSONObject(i)
                val userId = memberObj.getInt("user_id")
                val lat = memberObj.optDouble("lat", 0.0)
                val lon = memberObj.optDouble("lon", 0.0)
                val name = memberObj.getString("name")
                
                currentMemberIds.add(userId)
                
                if (lat != 0.0 && lon != 0.0) {
                    val position = LatLng(lat, lon)
                    
                    if (userMarkers.containsKey(userId)) {
                        // Update existing marker
                        userMarkers[userId]?.position = position
                    } else {
                        // Create new marker
                        val marker = googleMap?.addMarker(
                            MarkerOptions()
                                .position(position)
                                .title(name)
                                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_VIOLET))
                        )
                        marker?.let { userMarkers[userId] = it }
                    }
                }
            }
            
            // Remove markers for members no longer in circle
            val toRemove = userMarkers.keys.filter { it !in currentMemberIds }
            toRemove.forEach {
                userMarkers[it]?.remove()
                userMarkers.remove(it)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating markers: ${e.message}")
        }
    }

    // Circle management functions (same as MapFragment)
    private fun showCreateCircleDialog() {
        val input = EditText(requireContext())
        input.hint = "Enter Circle Name"
        input.setPadding(50, 50, 50, 50)

        AlertDialog.Builder(requireContext())
            .setTitle("Create a Circle")
            .setMessage("Give your circle a name")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val circleName = input.text.toString().trim()
                if (circleName.isNotEmpty()) {
                    createCircle(circleName)
                } else {
                    Toast.makeText(context, "Please enter a circle name", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun createCircle(name: String) {
        val prefs = requireContext().getSharedPreferences("shakti_prefs", Context.MODE_PRIVATE)
        val serverUrl = prefs.getString("server_url", "http://192.168.1.35:5000") ?: return
        val token = prefs.getString("auth_token", "") ?: return

        if (token.isEmpty()) {
            Toast.makeText(context, "Please login first", Toast.LENGTH_LONG).show()
            return
        }

        val json = JSONObject()
        json.put("name", name)

        val body = RequestBody.create("application/json".toMediaTypeOrNull(), json.toString())
        val request = Request.Builder()
            .url("$serverUrl/circles/create")
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                activity?.runOnUiThread {
                    Toast.makeText(context, "Network error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val respStr = response.body?.string() ?: ""
                activity?.runOnUiThread {
                    try {
                        if (respStr.trim().startsWith("<!") || respStr.trim().startsWith("<html")) {
                            Toast.makeText(context, "Server error. Please check if backend is running.", Toast.LENGTH_LONG).show()
                            Log.e(TAG, "Received HTML instead of JSON: $respStr")
                            return@runOnUiThread
                        }

                        val jsonResp = JSONObject(respStr)
                        if (jsonResp.optBoolean("success")) {
                            val inviteCode = jsonResp.optString("invite_code", "")
                            if (inviteCode.isNotEmpty()) {
                                showInviteCodeDialog(inviteCode)
                                loadUserCircles()
                            } else {
                                Toast.makeText(context, "Circle created but no invite code received", Toast.LENGTH_LONG).show()
                            }
                        } else {
                            val error = jsonResp.optString("error", "Failed to create circle")
                            Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, "Error parsing response. Check if backend is running.", Toast.LENGTH_LONG).show()
                        Log.e(TAG, "Parse error: ${e.message}\nResponse: $respStr")
                    }
                }
            }
        })
    }

    private fun showInviteCodeDialog(inviteCode: String) {
        AlertDialog.Builder(requireContext())
            .setTitle("Circle Created!")
            .setMessage("Share this invite code with others:\n\n$inviteCode")
            .setPositiveButton("Copy Code") { _, _ ->
                val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("Invite Code", inviteCode)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, "Invite code copied!", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("OK", null)
            .show()
    }

    private fun showJoinCircleDialog() {
        val input = EditText(requireContext())
        input.hint = "Enter 6-character Invite Code"
        input.setPadding(50, 50, 50, 50)

        AlertDialog.Builder(requireContext())
            .setTitle("Join a Circle")
            .setMessage("Enter the invite code")
            .setView(input)
            .setPositiveButton("Join") { _, _ ->
                val inviteCode = input.text.toString().trim().uppercase()
                if (inviteCode.isNotEmpty()) {
                    joinCircle(inviteCode)
                } else {
                    Toast.makeText(context, "Please enter an invite code", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun joinCircle(inviteCode: String) {
        val prefs = requireContext().getSharedPreferences("shakti_prefs", Context.MODE_PRIVATE)
        val serverUrl = prefs.getString("server_url", "http://192.168.1.35:5000") ?: return
        val token = prefs.getString("auth_token", "") ?: return

        val json = JSONObject()
        json.put("invite_code", inviteCode)

        val body = RequestBody.create("application/json".toMediaTypeOrNull(), json.toString())
        val request = Request.Builder()
            .url("$serverUrl/circles/join")
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
                activity?.runOnUiThread {
                    try {
                        val jsonResp = JSONObject(respStr ?: "{}")
                        if (jsonResp.optBoolean("success")) {
                            Toast.makeText(context, jsonResp.optString("message", "Joined circle successfully!"), Toast.LENGTH_LONG).show()
                            loadUserCircles()
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

    override fun onDestroyView() {
        super.onDestroyView()
        try {
            locationManager.removeUpdates(this)
            handler.removeCallbacksAndMessages(null)
        } catch (e: Exception) {}
    }

    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}
}
