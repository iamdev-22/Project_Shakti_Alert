package com.example.shaktialert

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.slider.Slider
import androidx.appcompat.app.AppCompatDelegate
import android.util.Log

class SettingsFragment : Fragment() {
    private val TAG = "SettingsFragment"
    
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_settings, container, false)
        
        try {
            // Get UI elements
            val etServerUrl = view.findViewById<EditText>(R.id.etServerUrl)
            val btnSave = view.findViewById<Button>(R.id.btnSaveSettings)
            val switchDarkMode = view.findViewById<SwitchMaterial>(R.id.switchDarkMode)
            val switchNotifs = view.findViewById<SwitchMaterial>(R.id.switchNotifs)
            val switchLocation = view.findViewById<SwitchMaterial>(R.id.switchLocation)
            val sliderSOS = view.findViewById<Slider>(R.id.sliderSOS)
            
            if (etServerUrl == null || btnSave == null || switchDarkMode == null) {
                Log.e(TAG, "UI elements not found!")
                return view
            }
            
            val prefs = requireContext().getSharedPreferences("shakti_prefs", Context.MODE_PRIVATE)
            
            // ===== LOAD SAVED SETTINGS =====
            
            // Load Server URL
            val savedUrl = prefs.getString("server_url", "http://192.168.1.35:5000") ?: "http://192.168.1.35:5000"
            etServerUrl.setText(savedUrl)
            Log.i(TAG, "Loaded server URL: $savedUrl")
            
            // Load Dark Mode
            val isDarkMode = prefs.getBoolean("dark_mode", false)
            switchDarkMode.isChecked = isDarkMode
            Log.i(TAG, "Loaded dark mode: $isDarkMode")
            
            // Load Notifications
            val notificationsEnabled = prefs.getBoolean("notifications_enabled", true)
            switchNotifs?.isChecked = notificationsEnabled
            
            // Load Location Sharing
            val locationSharing = prefs.getBoolean("location_sharing_enabled", true)
            switchLocation?.isChecked = locationSharing
            
            // Load SOS Countdown
            val sosDuration = prefs.getInt("sos_countdown_duration", 3)
            sliderSOS?.value = sosDuration.toFloat()
            
            // ===== SAVE BUTTON LISTENER =====
            
            btnSave.setOnClickListener {
                val url = etServerUrl.text.toString().trim()
                
                if (url.isEmpty()) {
                    Toast.makeText(context, "Server URL cannot be empty", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                
                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    Toast.makeText(context, "URL must start with http:// or https://", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                
                // Save to SharedPreferences
                prefs.edit().putString("server_url", url).apply()
                Log.i(TAG, "Saved server URL: $url")
                
                Toast.makeText(context, "✅ Settings Saved Successfully!", Toast.LENGTH_LONG).show()
            }
            
            // ===== DARK MODE LISTENER =====
            
            switchDarkMode.setOnCheckedChangeListener { _, isChecked ->
                Log.i(TAG, "Dark mode toggled: $isChecked")
                
                prefs.edit().putBoolean("dark_mode", isChecked).apply()
                
                if (isChecked) {
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                    Toast.makeText(context, "🌙 Dark Mode Enabled", Toast.LENGTH_SHORT).show()
                } else {
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                    Toast.makeText(context, "☀️ Light Mode Enabled", Toast.LENGTH_SHORT).show()
                }
                
                // Restart activity to apply theme
                activity?.recreate()
            }
            
            // ===== NOTIFICATIONS LISTENER =====
            
            switchNotifs?.setOnCheckedChangeListener { _, isChecked ->
                Log.i(TAG, "Notifications toggled: $isChecked")
                prefs.edit().putBoolean("notifications_enabled", isChecked).apply()
                
                val message = if (isChecked) "✅ Notifications Enabled" else "🔕 Notifications Disabled"
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
            
            // ===== LOCATION SHARING LISTENER =====
            
            switchLocation?.setOnCheckedChangeListener { _, isChecked ->
                Log.i(TAG, "Location sharing toggled: $isChecked")
                prefs.edit().putBoolean("location_sharing_enabled", isChecked).apply()
                
                val message = if (isChecked) "📍 Location Sharing Enabled" else "📍 Location Sharing Disabled"
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
            
            // ===== SOS COUNTDOWN LISTENER =====
            
            sliderSOS?.addOnChangeListener { _, value, _ ->
                Log.i(TAG, "SOS countdown changed: ${value.toInt()} seconds")
                prefs.edit().putInt("sos_countdown_duration", value.toInt()).apply()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing settings: ${e.message}", e)
            Toast.makeText(context, "Error loading settings: ${e.message}", Toast.LENGTH_LONG).show()
        }
        
        return view
    }
    
    override fun onResume() {
        super.onResume()
        Log.i(TAG, "Fragment resumed")
    }
}
