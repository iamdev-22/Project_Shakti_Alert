package com.example.shaktialert

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.json.JSONObject
import java.io.IOException

class LoginActivity : AppCompatActivity() {

    private var isLogin = true
    private val client = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // Check if already logged in
        val prefs = getSharedPreferences("shakti_prefs", Context.MODE_PRIVATE)
        if (prefs.contains("auth_token")) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        val btnToggleLogin = findViewById<Button>(R.id.btnToggleLogin)
        val btnToggleSignup = findViewById<Button>(R.id.btnToggleSignup)
        val layoutNames = findViewById<LinearLayout>(R.id.layoutNames)
        val btnAction = findViewById<Button>(R.id.btnAction)
        val btnSkip = findViewById<TextView>(R.id.btnSkip)

        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val etFirstName = findViewById<EditText>(R.id.etFirstName)
        val etLastName = findViewById<EditText>(R.id.etLastName)
        val etServerUrl = findViewById<EditText>(R.id.etServerUrl)

        // Pre-fill server URL if saved, otherwise default to local network IP
        etServerUrl.setText(prefs.getString("server_url", "http://192.168.1.35:5000"))


        // Toggle Logic
        btnToggleLogin.setOnClickListener {
            isLogin = true
            layoutNames.visibility = View.GONE
            btnAction.text = "Login"
            btnToggleLogin.backgroundTintList = ContextCompat.getColorStateList(this, R.color.shakti_red)
            btnToggleLogin.setTextColor(ContextCompat.getColor(this, R.color.white))
            btnToggleSignup.backgroundTintList = ContextCompat.getColorStateList(this, android.R.color.transparent)
            btnToggleSignup.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        }

        btnToggleSignup.setOnClickListener {
            isLogin = false
            layoutNames.visibility = View.VISIBLE
            btnAction.text = "Create Account"
            btnToggleSignup.backgroundTintList = ContextCompat.getColorStateList(this, R.color.shakti_red)
            btnToggleSignup.setTextColor(ContextCompat.getColor(this, R.color.white))
            btnToggleLogin.backgroundTintList = ContextCompat.getColorStateList(this, android.R.color.transparent)
            btnToggleLogin.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        }

        // Action Logic
        btnAction.setOnClickListener {
            val email = etEmail.text.toString()
            val password = etPassword.text.toString()
            val serverUrl = etServerUrl.text.toString().trim()
            
            // Save server URL
            prefs.edit().putString("server_url", serverUrl).apply()
            
            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val json = JSONObject()
            json.put("email", email)
            json.put("password", password)
            
            val endpoint = if (isLogin) "/auth/login" else "/auth/signup"
            
            if (!isLogin) {
                json.put("name", etFirstName.text.toString())
                json.put("last_name", etLastName.text.toString())
            }

            // Use saved URL
            val url = "$serverUrl$endpoint"

            val body = RequestBody.create("application/json".toMediaTypeOrNull(), json.toString())
            val request = Request.Builder().url(url).post(body).build()

            btnAction.isEnabled = false
            btnAction.text = "Please wait..."

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    runOnUiThread {
                        btnAction.isEnabled = true
                        btnAction.text = if (isLogin) "Login" else "Create Account"
                        Toast.makeText(this@LoginActivity, 
                            "Connection Error: ${e.message}\n\nPlease check:\n1. Server is running\n2. Server URL is correct\n3. Network connection", 
                            Toast.LENGTH_LONG).show()
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    val respStr = response.body?.string() ?: ""
                    runOnUiThread {
                        btnAction.isEnabled = true
                        btnAction.text = if (isLogin) "Login" else "Create Account"
                        
                        try {
                            // Check for non-JSON response (e.g. HTML 404/500 from proxy/bad URL)
                            val trimmedResp = respStr.trim()
                            if (trimmedResp.startsWith("<!") || trimmedResp.startsWith("<html") || !trimmedResp.startsWith("{")) {
                                Toast.makeText(
                                    this@LoginActivity,
                                    "Error: Invalid Server URL or Server Offline.\nPlease check the URL and try again.",
                                    Toast.LENGTH_LONG
                                ).show()
                                return@runOnUiThread
                            }

                            val jsonResp = JSONObject(trimmedResp)
                            if (jsonResp.optBoolean("success")) {
                                val token = jsonResp.optString("token")
                                val userObj = jsonResp.optJSONObject("user")
                                
                                // Save user data from login response
                                val editor = prefs.edit()
                                editor.putString("auth_token", token)
                                editor.putString("user_email", email)
                                
                                if (userObj != null) {
                                    editor.putInt("user_id", userObj.optInt("id"))
                                    editor.putString("user_name", userObj.optString("name", ""))
                                    editor.putString("user_last_name", userObj.optString("last_name", ""))
                                }
                                editor.apply()
                                
                                Toast.makeText(this@LoginActivity, "Welcome!", Toast.LENGTH_SHORT).show()
                                startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                                finish()
                            } else {
                                val err = jsonResp.optString("error", "Authentication failed")
                                Toast.makeText(this@LoginActivity, err, Toast.LENGTH_LONG).show()
                            }
                        } catch (e: Exception) {
                           // If we get here, it's likely a JSON parse error due to bad response
                           Toast.makeText(
                               this@LoginActivity,
                               "Invalid response from server. Check URL.",
                               Toast.LENGTH_LONG
                           ).show()
                        }
                    }
                }
            })
        }

        // Forgot Password
        findViewById<TextView>(R.id.tvForgotPassword).setOnClickListener {
            startActivity(Intent(this, ForgotPasswordActivity::class.java))
        }

        btnSkip.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }
}
