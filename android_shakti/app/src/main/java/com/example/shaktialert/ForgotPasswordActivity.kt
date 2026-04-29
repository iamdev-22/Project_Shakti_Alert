package com.example.shaktialert

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.json.JSONObject
import java.io.IOException

class ForgotPasswordActivity : AppCompatActivity() {

    private lateinit var emailInput: EditText
    private lateinit var resetCodeInput: EditText
    private lateinit var newPasswordInput: EditText
    private lateinit var confirmPasswordInput: EditText
    
    private lateinit var step1Layout: LinearLayout
    private lateinit var step2Layout: LinearLayout
    private lateinit var step3Layout: LinearLayout
    
    private lateinit var sendCodeButton: Button
    private lateinit var verifyCodeButton: Button
    private lateinit var resetPasswordButton: Button
    
    private val client = OkHttpClient()
    private var userEmail = ""
    private var resetCode = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forgot_password)
        
        // Initialize views
        emailInput = findViewById(R.id.emailInput)
        resetCodeInput = findViewById(R.id.resetCodeInput)
        newPasswordInput = findViewById(R.id.newPasswordInput)
        confirmPasswordInput = findViewById(R.id.confirmPasswordInput)
        
        step1Layout = findViewById(R.id.step1Layout)
        step2Layout = findViewById(R.id.step2Layout)
        step3Layout = findViewById(R.id.step3Layout)
        
        sendCodeButton = findViewById(R.id.sendCodeButton)
        verifyCodeButton = findViewById(R.id.verifyCodeButton)
        resetPasswordButton = findViewById(R.id.resetPasswordButton)
        
        // Show step 1 initially
        showStep(1)
        
        // Step 1: Send reset code
        sendCodeButton.setOnClickListener {
            val email = emailInput.text.toString().trim()
            if (email.isEmpty()) {
                Toast.makeText(this, "Please enter your email", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            userEmail = email
            requestResetCode(email)
        }
        
        // Step 2: Verify code (removed - not needed, backend verifies when resetting)
        verifyCodeButton.setOnClickListener {
            val code = resetCodeInput.text.toString().trim()
            if (code.isEmpty()) {
                Toast.makeText(this, "Please enter the reset code", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (code.length != 6) {
                Toast.makeText(this, "Reset code must be 6 digits", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            // CRITICAL FIX: Verify code with backend before proceeding
            resetCode = code
            verifyCodeWithBackend(userEmail, code)
        }
        
        // Step 3: Reset password
        resetPasswordButton.setOnClickListener {
            val newPassword = newPasswordInput.text.toString().trim()
            val confirmPassword = confirmPasswordInput.text.toString().trim()
            
            if (newPassword.isEmpty()) {
                Toast.makeText(this, "Please enter new password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            if (newPassword != confirmPassword) {
                Toast.makeText(this, "Passwords don't match", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            if (newPassword.length < 6) {
                Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            // Backend will verify the code when resetting password
            resetPassword(userEmail, resetCode, newPassword)
        }
    }
    
    private fun showStep(step: Int) {
        step1Layout.visibility = if (step == 1) LinearLayout.VISIBLE else LinearLayout.GONE
        step2Layout.visibility = if (step == 2) LinearLayout.VISIBLE else LinearLayout.GONE
        step3Layout.visibility = if (step == 3) LinearLayout.VISIBLE else LinearLayout.GONE
    }
    
    private fun requestResetCode(email: String) {
        val prefs = getSharedPreferences("shakti_prefs", MODE_PRIVATE)
        val serverUrl = prefs.getString("server_url", "http://192.168.29.91:5000") ?: "http://192.168.29.91:5000"
        
        val json = JSONObject()
        json.put("email", email)
        
        val body = RequestBody.create("application/json".toMediaTypeOrNull(), json.toString())
        val request = Request.Builder()
            .url("$serverUrl/auth/forgot-password")
            .post(body)
            .build()
        
        sendCodeButton.isEnabled = false
        sendCodeButton.text = "Sending..."
        
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    sendCodeButton.isEnabled = true
                    sendCodeButton.text = "Send Code"
                    Toast.makeText(this@ForgotPasswordActivity, "Network error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
            
            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string() ?: ""
                runOnUiThread {
                    sendCodeButton.isEnabled = true
                    sendCodeButton.text = "Send Code"
                    
                    try {
                        // Check if response is HTML (server error)
                        if (responseBody.trim().startsWith("<!") || responseBody.trim().startsWith("<html")) {
                            Toast.makeText(
                                this@ForgotPasswordActivity,
                                "Server error. Please check:\n1. Backend is running\n2. Server URL is correct\n3. Network connection",
                                Toast.LENGTH_LONG
                            ).show()
                            return@runOnUiThread
                        }
                        
                        val jsonResponse = JSONObject(responseBody)
                        if (jsonResponse.getBoolean("success")) {
                            // For testing: show the code (remove in production)
                            val code = jsonResponse.optString("code", "")
                            if (code.isNotEmpty()) {
                                // Show code in a clear dialog
                                android.app.AlertDialog.Builder(this@ForgotPasswordActivity)
                                    .setTitle("Reset Code")
                                    .setMessage("Your reset code is:\n\n$code\n\nEnter this code in the next step.")
                                    .setPositiveButton("OK") { dialog, _ ->
                                        dialog.dismiss()
                                        showStep(2)
                                    }
                                    .setCancelable(false)
                                    .show()
                            } else {
                                Toast.makeText(this@ForgotPasswordActivity, "Reset code sent to your email!", Toast.LENGTH_LONG).show()
                                showStep(2)
                            }
                        } else {
                            val error = jsonResponse.optString("error", "Failed to send reset code")
                            Toast.makeText(this@ForgotPasswordActivity, error, Toast.LENGTH_LONG).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(
                            this@ForgotPasswordActivity,
                            "Error: ${e.message}\n\nPlease check if backend server is running at the configured URL.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        })
    }
    
    private fun verifyCodeWithBackend(email: String, code: String) {
        val prefs = getSharedPreferences("shakti_prefs", MODE_PRIVATE)
        val serverUrl = prefs.getString("server_url", "http://192.168.29.91:5000") ?: "http://192.168.29.91:5000"
        
        val json = JSONObject()
        json.put("email", email)
        json.put("code", code)
        
        val body = RequestBody.create("application/json".toMediaTypeOrNull(), json.toString())
        val request = Request.Builder()
            .url("$serverUrl/auth/verify-reset-code")
            .post(body)
            .build()
        
        verifyCodeButton.isEnabled = false
        verifyCodeButton.text = "Verifying..."
        
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    verifyCodeButton.isEnabled = true
                    verifyCodeButton.text = "Verify Code"
                    Toast.makeText(this@ForgotPasswordActivity, "Network error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
            
            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string() ?: ""
                runOnUiThread {
                    verifyCodeButton.isEnabled = true
                    verifyCodeButton.text = "Verify Code"
                    
                    try {
                        val jsonResponse = JSONObject(responseBody)
                        if (jsonResponse.getBoolean("success")) {
                            Toast.makeText(this@ForgotPasswordActivity, "✅ Code verified!", Toast.LENGTH_SHORT).show()
                            showStep(3) // Now proceed to password reset
                        } else {
                            val error = jsonResponse.optString("error", "Invalid code")
                            Toast.makeText(this@ForgotPasswordActivity, "❌ $error", Toast.LENGTH_LONG).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(this@ForgotPasswordActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        })
    }
    
    private fun resetPassword(email: String, code: String, newPassword: String) {
        val prefs = getSharedPreferences("shakti_prefs", MODE_PRIVATE)
        val serverUrl = prefs.getString("server_url", "http://192.168.29.91:5000") ?: "http://192.168.29.91:5000"
        
        val json = JSONObject()
        json.put("email", email)
        json.put("code", code)
        json.put("new_password", newPassword)
        
        val body = RequestBody.create("application/json".toMediaTypeOrNull(), json.toString())
        val request = Request.Builder()
            .url("$serverUrl/auth/reset-password")
            .post(body)
            .build()
        
        resetPasswordButton.isEnabled = false
        resetPasswordButton.text = "Resetting..."
        
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    resetPasswordButton.isEnabled = true
                    resetPasswordButton.text = "Reset Password"
                    Toast.makeText(this@ForgotPasswordActivity, "Network error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
            
            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string() ?: ""
                runOnUiThread {
                    resetPasswordButton.isEnabled = true
                    resetPasswordButton.text = "Reset Password"
                    
                    try {
                        // Check if response is HTML (server error)
                        if (responseBody.trim().startsWith("<!") || responseBody.trim().startsWith("<html")) {
                            Toast.makeText(
                                this@ForgotPasswordActivity,
                                "Server error. Please check if backend is running.",
                                Toast.LENGTH_LONG
                            ).show()
                            return@runOnUiThread
                        }
                        
                        val jsonResponse = JSONObject(responseBody)
                        if (jsonResponse.getBoolean("success")) {
                            Toast.makeText(this@ForgotPasswordActivity, "Password reset successful! Please login.", Toast.LENGTH_LONG).show()
                            finish() // Go back to login
                        } else {
                            val error = jsonResponse.optString("error", "Failed to reset password")
                            Toast.makeText(this@ForgotPasswordActivity, error, Toast.LENGTH_LONG).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(
                            this@ForgotPasswordActivity,
                            "Error: ${e.message}\n\nPlease check if backend server is running.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        })
    }
}
