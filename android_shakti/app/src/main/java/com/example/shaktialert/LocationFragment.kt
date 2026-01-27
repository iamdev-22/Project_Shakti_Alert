package com.example.shaktialert

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.BitmapDescriptorFactory

/**
 * Simple LocationFragment - Life360 style
 * Shows map with user location and friends
 */
class LocationFragment : Fragment(), OnMapReadyCallback, LocationListener {

    private val TAG = "LocationFragment"
    private var googleMap: GoogleMap? = null
    private lateinit var locationManager: LocationManager
    
    private var currentLat = 28.6139
    private var currentLon = 77.2090

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Use simple layout with just a map
        return inflater.inflate(R.layout.fragment_location_simple, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        try {
            // Initialize map
            val mapFragment = childFragmentManager.findFragmentById(R.id.map) as? SupportMapFragment
            mapFragment?.getMapAsync(this)
            
            // Start location updates
            startLocationUpdates()
            
            Log.d(TAG, "LocationFragment initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing LocationFragment: ${e.message}")
            e.printStackTrace()
            Toast.makeText(context, "Error loading map: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onMapReady(map: GoogleMap) {
        try {
            googleMap = map
            
            // Configure map
            googleMap?.apply {
                mapType = GoogleMap.MAP_TYPE_NORMAL
                uiSettings.isZoomControlsEnabled = true
                uiSettings.isCompassEnabled = true
                uiSettings.isMyLocationButtonEnabled = true
                
                // Enable my location if permission granted
                if (ActivityCompat.checkSelfPermission(
                        requireContext(),
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    isMyLocationEnabled = true
                }
                
                // Set initial position
                val initialPos = LatLng(currentLat, currentLon)
                moveCamera(CameraUpdateFactory.newLatLngZoom(initialPos, 12f))
                
                // Add marker for current location
                addMarker(
                    MarkerOptions()
                        .position(initialPos)
                        .title("You")
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
                )
            }
            
            Log.d(TAG, "Map ready and configured")
        } catch (e: Exception) {
            Log.e(TAG, "Error in onMapReady: ${e.message}")
            e.printStackTrace()
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
                Log.w(TAG, "Location permission not granted")
                return
            }
            
            // Request location updates
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                5000L,  // 5 seconds
                10f,    // 10 meters
                this
            )
            
            // Get last known location
            val lastLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            
            lastLocation?.let {
                currentLat = it.latitude
                currentLon = it.longitude
                updateMapLocation(it.latitude, it.longitude)
            }
            
            Log.d(TAG, "Location updates started")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting location updates: ${e.message}")
            e.printStackTrace()
        }
    }

    override fun onLocationChanged(location: Location) {
        currentLat = location.latitude
        currentLon = location.longitude
        updateMapLocation(location.latitude, location.longitude)
        Log.d(TAG, "Location updated: $currentLat, $currentLon")
    }

    private fun updateMapLocation(lat: Double, lon: Double) {
        try {
            googleMap?.let { map ->
                val position = LatLng(lat, lon)
                map.animateCamera(CameraUpdateFactory.newLatLngZoom(position, 15f))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating map location: ${e.message}")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        try {
            locationManager.removeUpdates(this)
        } catch (e: Exception) {
            Log.e(TAG, "Error removing location updates: ${e.message}")
        }
    }

    // Required LocationListener methods
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}
}
