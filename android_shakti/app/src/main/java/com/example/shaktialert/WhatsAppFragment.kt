package com.example.shaktialert

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.RelativeLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.tabs.TabLayout
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.json.JSONObject
import java.io.IOException

class WhatsAppFragment : Fragment() {

    private lateinit var tabLayout: TabLayout
    private lateinit var qrContainer: RelativeLayout
    private lateinit var phoneContainer: ScrollView
    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    
    private lateinit var etGuardianPhone: EditText
    private lateinit var btnConnectPhone: Button
    private lateinit var tvStatus: TextView
    
    private val client = OkHttpClient()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_whatsapp, container, false)

        // Initialize Views
        tabLayout = view.findViewById(R.id.tabLayout)
        qrContainer = view.findViewById(R.id.qrContainer)
        phoneContainer = view.findViewById(R.id.phoneContainer)
        webView = view.findViewById(R.id.whatsappWebView)
        progressBar = view.findViewById(R.id.progressBar)
        
        etGuardianPhone = view.findViewById(R.id.etGuardianPhone)
        btnConnectPhone = view.findViewById(R.id.btnConnectPhone)
        tvStatus = view.findViewById(R.id.tvStatus)

        // Setup Tabs
        setupTabs()
        
        // Setup WebView
        setupWebView()
        loadWhatsAppPage()
        
        // Setup Phone Connect
        setupPhoneConnect()

        return view
    }

    private fun setupTabs() {
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> {
                        qrContainer.visibility = View.VISIBLE
                        phoneContainer.visibility = View.GONE
                    }
                    1 -> {
                        qrContainer.visibility = View.GONE
                        phoneContainer.visibility = View.VISIBLE
                    }
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
            allowContentAccess = true
            allowFileAccess = true
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                progressBar.visibility = View.GONE
            }
            
            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                progressBar.visibility = View.GONE
                
                // Only show toast, don't replace webview content to keep retry button available
                if (qrContainer.visibility == View.VISIBLE) {
                     Toast.makeText(context, "Connection Error. Retrying...", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun loadWhatsAppPage() {
        progressBar.visibility = View.VISIBLE

        // ✅ FIX: Use the PC's IP from server_url settings, but on port 3001 (WhatsApp server)
        // 127.0.0.1 is the PHONE's own localhost — it can never reach the PC's server!
        val prefs = requireContext().getSharedPreferences("shakti_prefs", Context.MODE_PRIVATE)
        val serverUrl = prefs.getString("server_url", "http://192.168.29.91:5000") ?: "http://192.168.29.91:5000"

        // Extract just the host (IP address) from server_url and use port 3001
        val waUrl = try {
            val uri = android.net.Uri.parse(serverUrl)
            val host = uri.host ?: "192.168.29.91"
            "http://$host:3001"
        } catch (e: Exception) {
            "http://192.168.29.91:3001"
        }

        android.util.Log.d("WhatsApp", "Loading WhatsApp server at: $waUrl")
        webView.loadUrl(waUrl)
    }
    
    private fun setupPhoneConnect() {
        btnConnectPhone.setOnClickListener {
            val phone = etGuardianPhone.text.toString().trim()
            
            if (phone.isEmpty() || phone.length < 10) {
                Toast.makeText(context, "Please enter a valid number", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            updateGuardianPhone(phone)
        }
    }
    
    private fun updateGuardianPhone(phone: String) {
        val prefs = requireContext().getSharedPreferences("shakti_prefs", Context.MODE_PRIVATE)
        val serverUrl = prefs.getString("server_url", "http://192.168.29.91:5000") ?: "http://192.168.29.91:5000"
        
        tvStatus.text = "Connecting..."
        tvStatus.setTextColor(android.graphics.Color.GRAY)
        btnConnectPhone.isEnabled = false
        
        val json = JSONObject().put("phone", phone)
        val body = RequestBody.create("application/json".toMediaTypeOrNull(), json.toString())
        
        val request = Request.Builder()
            .url("$serverUrl/api/update_guardian_phone")
            .post(body)
            .build()
            
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                activity?.runOnUiThread {
                    tvStatus.text = "❌ Connection Failed: ${e.message}"
                    tvStatus.setTextColor(android.graphics.Color.RED)
                    btnConnectPhone.isEnabled = true
                }
            }
            
            override fun onResponse(call: Call, response: Response) {
                activity?.runOnUiThread {
                    if (response.isSuccessful) {
                        tvStatus.text = "✅ Number Linked Successfully!"
                        tvStatus.setTextColor(android.graphics.Color.GREEN)
                        Toast.makeText(context, "Guardian number updated!", Toast.LENGTH_LONG).show()
                    } else {
                        tvStatus.text = "❌ Error: ${response.code}"
                        tvStatus.setTextColor(android.graphics.Color.RED)
                    }
                    btnConnectPhone.isEnabled = true
                }
            }
        })
    }

    fun canGoBack(): Boolean = webView.canGoBack()
    
    fun goBack() {
        if (webView.canGoBack()) {
            webView.goBack()
        }
    }
}
