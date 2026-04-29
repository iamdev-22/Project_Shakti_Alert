package com.example.shaktialert

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.material.bottomsheet.BottomSheetBehavior
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/**
 * Enhanced LocationFragment - Life360 style
 * Shows map with circle members, profile photos, battery levels
 */
class EnhancedLocationFragment : Fragment(), OnMapReadyCallback, LocationListener {

    private val TAG = "EnhancedLocationFragment"
    private val client = OkHttpClient()
    private var googleMap: GoogleMap? = null
    private lateinit var locationManager: LocationManager
    
    private var currentLat = 28.6139
    private var currentLon = 77.2090
    
    private lateinit var bottomSheet: View
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<View>
    private lateinit var circleNameText: TextView
    private lateinit var memberCountText: TextView
    private lateinit var recyclerMembers: RecyclerView
    private lateinit var btnCreateCircle: Button
    private lateinit var btnJoinCircle: Button
    
    private val membersList = mutableListOf<CircleMember>()
    private lateinit var membersAdapter: CircleMembersAdapter
    
    private var currentCircleId: Int = 0
    private var currentCircleName: String = "Shakti Alert Group"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_location_enhanced, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        try {
            // Initialize map
            val mapFragment = childFragmentManager.findFragmentById(R.id.map) as? SupportMapFragment
            mapFragment?.getMapAsync(this)
            
            // Initialize bottom sheet
            bottomSheet = view.findViewById(R.id.bottomSheet)
            bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet)
            
            // Initialize views
            circleNameText = view.findViewById(R.id.tvCircleName)
            memberCountText = view.findViewById(R.id.tvMemberCount)
            recyclerMembers = view.findViewById(R.id.recyclerMembers)
            btnCreateCircle = view.findViewById(R.id.btnCreateCircle)
            btnJoinCircle = view.findViewById(R.id.btnJoinCircle)
            
            // Setup RecyclerView
            recyclerMembers.layoutManager = LinearLayoutManager(requireContext())
            membersAdapter = CircleMembersAdapter(membersList)
            recyclerMembers.adapter = membersAdapter
            
            // Circle name click - show slide-up panel
            circleNameText.setOnClickListener {
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
            
            // Refresh Button
            view.findViewById<View>(R.id.btnRefreshLocation).setOnClickListener {
                Toast.makeText(context, "Refreshing location...", Toast.LENGTH_SHORT).show()
                startLocationUpdates()
                loadCircleMembers()
            }
            
            // Start location updates
            startLocationUpdates()
            
            // Load user's circles
            loadUserCircles()
            
            android.util.Log.d(TAG, "EnhancedLocationFragment initialized")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error initializing: ${e.message}")
            e.printStackTrace()
        }
    }

    override fun onMapReady(map: GoogleMap) {
        try {
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
                    isMyLocationEnabled = true
                }
                
                val initialPos = LatLng(currentLat, currentLon)
                moveCamera(CameraUpdateFactory.newLatLngZoom(initialPos, 15f)) // Closer zoom
            }
            
            // Load circle members and show on map
            loadCircleMembers()
            
            android.util.Log.d(TAG, "Map ready")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error in onMapReady: ${e.message}")
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
                ActivityCompat.requestPermissions(
                    requireActivity(),
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    1001
                )
                return
            }
            
            // Request high accuracy location updates
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                2000L,  // 2 seconds
                5f,     // 5 meters
                this
            )
            
            // Also listen to network provider for faster lock
            locationManager.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER,
                2000L,
                5f,
                this
            )
            
            val lastLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            
            lastLocation?.let {
                currentLat = it.latitude
                currentLon = it.longitude
                updateMapLocation(it.latitude, it.longitude)
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error starting location updates: ${e.message}")
        }
    }

    override fun onLocationChanged(location: Location) {
        currentLat = location.latitude
        currentLon = location.longitude
        updateMapLocation(location.latitude, location.longitude)
    }

    private fun updateMapLocation(lat: Double, lon: Double) {
        try {
            googleMap?.let { map ->
                val position = LatLng(lat, lon)
                
                // Add/update user marker
                map.clear()
                map.addMarker(
                    MarkerOptions()
                        .position(position)
                        .title("You")
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
                )
                
                map.animateCamera(CameraUpdateFactory.newLatLngZoom(position, 14f))
                
                // Reload circle members to show their pins
                loadCircleMembers()
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error updating map: ${e.message}")
        }
    }

    private fun loadUserCircles() {
        val prefs = requireContext().getSharedPreferences("shakti_prefs", Context.MODE_PRIVATE)
        val baseUrl = prefs.getString("server_url", "http://192.168.29.91:5000") ?: return
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
                    android.util.Log.e(TAG, "Error parsing circles: ${e.message}")
                }
            }
        })
    }

    private fun loadCircleMembers() {
        if (currentCircleId == 0) return
        
        val prefs = requireContext().getSharedPreferences("shakti_prefs", Context.MODE_PRIVATE)
        val baseUrl = prefs.getString("server_url", "http://192.168.29.91:5000") ?: return
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
                        membersList.clear()
                        
                        activity?.runOnUiThread {
                            googleMap?.clear()
                        }
                        
                        for (i in 0 until membersArray.length()) {
                            val memberObj = membersArray.getJSONObject(i)
                            val lat = memberObj.optDouble("lat", 0.0)
                            val lon = memberObj.optDouble("lon", 0.0)
                            val name = memberObj.getString("name")
                            val battery = 85 // TODO: Get real battery level
                            
                            membersList.add(CircleMember(
                                id = memberObj.getInt("user_id"),
                                name = name,
                                lat = lat,
                                lon = lon,
                                battery = battery,
                                photo = memberObj.optString("profile_photo", ""),
                                lastActive = memberObj.optString("last_update", ""),
                                address = memberObj.optString("address", "Unknown")
                            ))
                            
                            // Add marker on map
                            if (lat != 0.0 && lon != 0.0) {
                                activity?.runOnUiThread {
                                    googleMap?.addMarker(
                                        MarkerOptions()
                                            .position(LatLng(lat, lon))
                                            .title(name)
                                            .snippet("Battery: $battery%")
                                            .icon(BitmapDescriptorFactory.defaultMarker(
                                                BitmapDescriptorFactory.HUE_VIOLET
                                            ))
                                    )
                                }
                            }
                        }
                        
                        activity?.runOnUiThread {
                            membersAdapter.notifyDataSetChanged()
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "Error loading members: ${e.message}")
                }
            }
        })
    }

    private fun showCreateCircleDialog() {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_create_circle, null)
        val nameInput = dialogView.findViewById<EditText>(R.id.etCircleName)
        
        AlertDialog.Builder(requireContext())
            .setTitle("Create a Circle")
            .setView(dialogView)
            .setPositiveButton("Create") { _, _ ->
                val circleName = nameInput.text.toString().trim()
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
        val baseUrl = prefs.getString("server_url", "http://192.168.29.91:5000") ?: return
        val token = prefs.getString("auth_token", "") ?: return
        
        val json = """{"name": "$name"}"""
        val requestBody = RequestBody.create("application/json".toMediaTypeOrNull(), json)
        
        val request = Request.Builder()
            .url("$baseUrl/circles/create")
            .addHeader("Authorization", "Bearer $token")
            .post(requestBody)
            .build()
        
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                activity?.runOnUiThread {
                    Toast.makeText(context, "Failed to create circle", Toast.LENGTH_SHORT).show()
                }
            }
            
            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string() ?: ""
                response.close()
                
                try {
                    val json = JSONObject(responseBody)
                    if (json.getBoolean("success")) {
                        val inviteCode = json.getString("invite_code")
                        activity?.runOnUiThread {
                            showInviteCodeDialog(inviteCode)
                            loadUserCircles()
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "Error creating circle: ${e.message}")
                }
            }
        })
    }

    private fun showInviteCodeDialog(inviteCode: String) {
        AlertDialog.Builder(requireContext())
            .setTitle("Circle Created!")
            .setMessage("Share this invite code:\n\n$inviteCode")
            .setPositiveButton("Copy") { _, _ ->
                val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("Invite Code", inviteCode)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, "Code copied!", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("OK", null)
            .show()
    }

    private fun showJoinCircleDialog() {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_join_circle, null)
        val codeInput = dialogView.findViewById<EditText>(R.id.etInviteCode)
        
        AlertDialog.Builder(requireContext())
            .setTitle("Join a Circle")
            .setView(dialogView)
            .setPositiveButton("Join") { _, _ ->
                val inviteCode = codeInput.text.toString().trim().uppercase()
                if (inviteCode.isNotEmpty()) {
                    joinCircle(inviteCode)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun joinCircle(inviteCode: String) {
        val prefs = requireContext().getSharedPreferences("shakti_prefs", Context.MODE_PRIVATE)
        val baseUrl = prefs.getString("server_url", "http://192.168.29.91:5000") ?: return
        val token = prefs.getString("auth_token", "") ?: return
        
        val json = """{"invite_code": "$inviteCode"}"""
        val requestBody = RequestBody.create("application/json".toMediaTypeOrNull(), json)
        
        val request = Request.Builder()
            .url("$baseUrl/circles/join")
            .addHeader("Authorization", "Bearer $token")
            .post(requestBody)
            .build()
        
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                activity?.runOnUiThread {
                    Toast.makeText(context, "Failed to join circle", Toast.LENGTH_SHORT).show()
                }
            }
            
            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string() ?: ""
                response.close()
                
                try {
                    val json = JSONObject(responseBody)
                    activity?.runOnUiThread {
                        if (json.getBoolean("success")) {
                            Toast.makeText(context, json.getString("message"), Toast.LENGTH_SHORT).show()
                            loadUserCircles()
                        } else {
                            Toast.makeText(context, json.getString("error"), Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "Error joining circle: ${e.message}")
                }
            }
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        try {
            locationManager.removeUpdates(this)
        } catch (e: Exception) {}
    }

    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}
}

