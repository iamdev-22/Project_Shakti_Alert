package com.example.shaktialert

import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONObject
import java.io.IOException

class WhatsAppFragmentNative : Fragment() {

    private lateinit var tabLayout: TabLayout
    private lateinit var viewPager: ViewPager2
    
    // QR Tab views
    private lateinit var qrCodeImage: ImageView
    private lateinit var qrProgressBar: ProgressBar
    private lateinit var qrStatusText: TextView
    private lateinit var statusCard: androidx.cardview.widget.CardView
    private lateinit var statusText: TextView
    private lateinit var refreshButton: Button
    private lateinit var disconnectButton: Button
    
    // Phone Tab views
    private lateinit var phoneInput: TextInputEditText
    private lateinit var savePhoneButton: Button
    
    private val client = OkHttpClient()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var qrCheckJob: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_whatsapp_native, container, false)
        
        tabLayout = view.findViewById(R.id.tabLayout)
        viewPager = view.findViewById(R.id.viewPager)
        
        setupTabs()
        
        return view
    }
    
    private fun setupTabs() {
        val adapter = WhatsAppPagerAdapter(this)
        viewPager.adapter = adapter
        
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "QR Code"
                1 -> "Phone Number"
                else -> ""
            }
        }.attach()
        
        // Start QR check when QR tab is selected
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                if (position == 0) {
                    startQRCheck()
                } else {
                    stopQRCheck()
                }
            }
        })
    }
    
    fun setupQRTab(view: View) {
        qrCodeImage = view.findViewById(R.id.qrCodeImage)
        qrProgressBar = view.findViewById(R.id.qrProgressBar)
        qrStatusText = view.findViewById(R.id.qrStatusText)
        statusCard = view.findViewById(R.id.statusCard)
        statusText = view.findViewById(R.id.statusText)
        refreshButton = view.findViewById(R.id.refreshButton)
        disconnectButton = view.findViewById(R.id.disconnectButton)
        
        refreshButton.setOnClickListener { refreshQR() }
        disconnectButton.setOnClickListener { disconnect() }
        
        startQRCheck()
    }
    
    fun setupPhoneTab(view: View) {
        phoneInput = view.findViewById(R.id.phoneInput)
        savePhoneButton = view.findViewById(R.id.savePhoneButton)
        
        savePhoneButton.setOnClickListener { savePhoneNumber() }
    }
    
    private fun startQRCheck() {
        qrCheckJob?.cancel()
        qrCheckJob = scope.launch {
            while (isActive) {
                checkQRCode()
                delay(3000) // Check every 3 seconds
            }
        }
    }
    
    private fun stopQRCheck() {
        qrCheckJob?.cancel()
    }
    
    private suspend fun checkQRCode() = withContext(Dispatchers.IO) {
        try {
            val prefs = requireContext().getSharedPreferences("shakti_prefs", android.content.Context.MODE_PRIVATE)
            val serverUrl = prefs.getString("server_url", "http://192.168.1.35:5000") ?: "http://192.168.1.35:5000"
            
            // Connect through Flask backend API (better than direct server connection)
            val request = Request.Builder()
                .url("$serverUrl/api/whatsapp/qr")
                .build()
            
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            
            withContext(Dispatchers.Main) {
                if (response.isSuccessful && responseBody.isNotEmpty()) {
                    try {
                        val json = JSONObject(responseBody)
                        val status = json.optString("status", "")
                        val qrData = json.optString("qr", "") + json.optString("qrCode", "") // Check both keys
                        
                        when (status) {
                            "connected" -> {
                                qrProgressBar.visibility = View.GONE
                                qrCodeImage.visibility = View.VISIBLE
                                qrCodeImage.setImageResource(android.R.drawable.checkbox_on_background)
                                qrStatusText.text = "✅ WhatsApp Connected!"
                                statusText.text = "✅ WhatsApp Connected Successfully!"
                                statusText.setTextColor(resources.getColor(android.R.color.holo_green_dark))
                            }
                            "qr_code" -> {
                                if (qrData.isNotEmpty()) {
                                    displayQRCode(qrData)
                                    qrStatusText.text = "📱 Scan QR code with WhatsApp"
                                    statusText.text = "⏳ Waiting for scan..."
                                    statusText.setTextColor(resources.getColor(android.R.color.holo_orange_dark))
                                } else {
                                    qrProgressBar.visibility = View.VISIBLE
                                    qrCodeImage.visibility = View.GONE
                                    qrStatusText.text = "⏳ Generating QR code..."
                                    statusText.text = "⏳ Please wait..."
                                }
                            }
                            "loading" -> {
                                qrProgressBar.visibility = View.VISIBLE
                                qrCodeImage.visibility = View.GONE
                                qrStatusText.text = "Generating QR code..."
                                statusText.text = "⏳ Initializing WhatsApp connection..."
                            }
                            else -> {
                                val message = json.optString("message", "Unknown error")
                                qrStatusText.text = "⏳ $message"
                                statusText.text = "⏳ $message"
                                statusText.setTextColor(resources.getColor(android.R.color.holo_orange_dark))
                            }
                        }
                    } catch (e: Exception) {
                        qrStatusText.text = "❌ Parse error: ${e.message}"
                        statusText.text = "❌ Error parsing response"
                        statusText.setTextColor(resources.getColor(android.R.color.holo_red_dark))
                    }
                } else {
                    qrStatusText.text = "❌ Server error: ${response.code}"
                    statusText.text = "❌ Cannot connect to server"
                    statusText.setTextColor(resources.getColor(android.R.color.holo_red_dark))
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                qrStatusText.text = "❌ Error: ${e.message}"
                statusText.text = "❌ Connection error"
                statusText.setTextColor(resources.getColor(android.R.color.holo_red_dark))
            }
        }
    }
    
    private fun displayQRCode(qrData: String) {
        try {
            // QR data is base64 encoded image
            if (qrData.startsWith("data:image")) {
                val base64Data = qrData.substring(qrData.indexOf(",") + 1)
                val decodedBytes = Base64.decode(base64Data, Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                
                qrProgressBar.visibility = View.GONE
                qrCodeImage.visibility = View.VISIBLE
                qrCodeImage.setImageBitmap(bitmap)
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Error displaying QR code: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun refreshQR() {
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val request = Request.Builder()
                        .url("http://127.0.0.1:3001/reset")
                        .post(RequestBody.create(null, ""))
                        .build()
                    
                    client.newCall(request).execute()
                }
                
                qrProgressBar.visibility = View.VISIBLE
                qrCodeImage.visibility = View.GONE
                qrStatusText.text = "Refreshing QR code..."
                
                delay(2000)
                checkQRCode()
            } catch (e: Exception) {
                Toast.makeText(context, "Error refreshing QR: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun disconnect() {
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val request = Request.Builder()
                        .url("http://127.0.0.1:3001/reset")
                        .post(RequestBody.create(null, ""))
                        .build()
                    
                    client.newCall(request).execute()
                }
                
                Toast.makeText(context, "WhatsApp disconnected", Toast.LENGTH_SHORT).show()
                qrStatusText.text = "Disconnected. Refresh to reconnect."
            } catch (e: Exception) {
                Toast.makeText(context, "Error disconnecting: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun savePhoneNumber() {
        val phone = phoneInput.text.toString().trim()
        
        if (!phone.matches(Regex("^\\+[0-9]{10,15}$"))) {
            Toast.makeText(context, "Please enter a valid phone number with country code", Toast.LENGTH_LONG).show()
            return
        }
        
        scope.launch {
            try {
                val prefs = requireContext().getSharedPreferences("shakti_prefs", android.content.Context.MODE_PRIVATE)
                val serverUrl = prefs.getString("server_url", "http://127.0.0.1:5000") ?: "http://127.0.0.1:5000"
                
                val json = JSONObject().apply {
                    put("phone", phone)
                }
                
                withContext(Dispatchers.IO) {
                    val requestBody = RequestBody.create(
                        "application/json".toMediaType(),
                        json.toString()
                    )
                    
                    val request = Request.Builder()
                        .url("$serverUrl/api/update_guardian_phone")
                        .post(requestBody)
                        .build()
                    
                    client.newCall(request).execute()
                }
                
                Toast.makeText(context, "✅ Guardian phone number saved!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "❌ Error saving phone number: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        scope.cancel()
    }
    
    // ViewPager Adapter
    inner class WhatsAppPagerAdapter(fragment: Fragment) : androidx.viewpager2.adapter.FragmentStateAdapter(fragment) {
        override fun getItemCount(): Int = 2
        
        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> QRTabFragment()
                1 -> PhoneTabFragment()
                else -> QRTabFragment()
            }
        }
    }
    
    // QR Tab Fragment
    class QRTabFragment : Fragment() {
        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
            val view = inflater.inflate(R.layout.tab_whatsapp_qr, container, false)
            (parentFragment as? WhatsAppFragmentNative)?.setupQRTab(view)
            return view
        }
    }
    
    // Phone Tab Fragment
    class PhoneTabFragment : Fragment() {
        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
            val view = inflater.inflate(R.layout.tab_whatsapp_phone, container, false)
            (parentFragment as? WhatsAppFragmentNative)?.setupPhoneTab(view)
            return view
        }
    }
}
