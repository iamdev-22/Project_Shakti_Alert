package com.example.shaktialert

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton

class MainActivity : AppCompatActivity() {

    private val PERMISSION_REQUEST_CODE = 123
    private var currentFragmentTag = "HOME"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ensurePermissions()
        
        // Load user profile data from backend
        loadUserProfile()

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        val fabHome = findViewById<FloatingActionButton>(R.id.fabHome)

        bottomNav.background = null // Clear background for BottomAppBar transparency

        // Handle back button properly
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (currentFragmentTag == "HOME") {
                    // If on home, exit app
                    finish()
                } else {
                    // Otherwise go to home
                    currentFragmentTag = "HOME"
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.fragment_container, HomeFragment(), "HOME")
                        .commit()
                    
                    // Deselect bottom nav
                    bottomNav.menu.setGroupCheckable(0, true, false)
                    for (i in 0 until bottomNav.menu.size()) {
                        bottomNav.menu.getItem(i).isChecked = false
                    }
                    bottomNav.menu.setGroupCheckable(0, true, true)
                }
            }
        })

        bottomNav.setOnItemSelectedListener { item ->
            // Prevent re-selecting the same fragment
            if (currentFragmentTag == item.itemId.toString()) {
                return@setOnItemSelectedListener true
            }
            
            val fragment = when (item.itemId) {
                R.id.nav_map -> {
                    currentFragmentTag = "MAP"
                    MapFragment()
                }
                R.id.nav_profile -> {
                    currentFragmentTag = "PROFILE"
                    ProfileFragment()
                }
                R.id.nav_contacts -> {
                    currentFragmentTag = "CONTACTS"
                    ContactsFragment()
                }
                R.id.nav_settings -> {
                    currentFragmentTag = "SETTINGS"
                    SettingsFragment()
                }
                else -> {
                    currentFragmentTag = "MAP"
                    MapFragment()
                }
            }
            
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, fragment, currentFragmentTag)
                .commit()
            true
        }

        fabHome.setOnClickListener {
            currentFragmentTag = "HOME"
            
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, HomeFragment(), "HOME")
                .commit()
                
            // Deselect all bottom nav items
            bottomNav.menu.setGroupCheckable(0, true, false)
            for (i in 0 until bottomNav.menu.size()) {
                bottomNav.menu.getItem(i).isChecked = false
            }
            bottomNav.menu.setGroupCheckable(0, true, true)
        }

        // Default Load Home Page
        if (savedInstanceState == null) {
            currentFragmentTag = "HOME"
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, HomeFragment(), "HOME")
                .commit()
        }
    }
    
    private fun loadUserProfile() {
        val prefs = getSharedPreferences("shakti_prefs", MODE_PRIVATE)
        val token = prefs.getString("auth_token", "") ?: return
        val baseUrl = prefs.getString("server_url", "http://192.168.1.35:5000") ?: return
        
        if (token.isEmpty()) return
        
        // Load profile data from backend
        val client = okhttp3.OkHttpClient()
        val request = okhttp3.Request.Builder()
            .url("$baseUrl/api/profile")
            .addHeader("Authorization", "Bearer $token")
            .get()
            .build()
        
        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                // Silently fail - user can still use app
            }
            
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                val responseBody = response.body?.string() ?: ""
                response.close()
                
                try {
                    val json = org.json.JSONObject(responseBody)
                    if (json.getBoolean("success")) {
                        val profile = json.getJSONObject("profile")
                        
                        // Save profile data
                        prefs.edit().apply {
                            putInt("user_id", profile.optInt("id"))
                            putString("user_name", profile.optString("name", ""))
                            putString("user_last_name", profile.optString("last_name", ""))
                            putString("user_email", profile.optString("email", ""))
                            putString("user_address", profile.optString("address", ""))
                            putString("user_dob", profile.optString("dob", ""))
                            putInt("user_age", profile.optInt("age", 0))
                            putString("user_photo", profile.optString("profile_photo", ""))
                            apply()
                        }
                    }
                } catch (e: Exception) {
                    // Silently fail
                }
            }
        })
    }

    private fun ensurePermissions() {
        val permissions = arrayOf(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION,
            android.Manifest.permission.ACCESS_BACKGROUND_LOCATION,
            android.Manifest.permission.RECORD_AUDIO,
            android.Manifest.permission.CAMERA,
            android.Manifest.permission.SEND_SMS,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        )
        
        if (!hasPermissions(*permissions)) {
            ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE)
        }
    }

    private fun hasPermissions(vararg permissions: String): Boolean {
        return permissions.all {
            ActivityCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }
}
