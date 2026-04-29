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
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Request as OkRequest
import okhttp3.Callback
import okhttp3.Call
import okhttp3.Response as OkResponse
import org.json.JSONObject
import org.json.JSONArray
import java.util.concurrent.TimeUnit
import android.util.Log

class ContactsFragment : Fragment() {

    private val TAG = "ContactsFragment"
    private lateinit var backendUrl: String
    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(7, TimeUnit.SECONDS)
            .build()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_contacts, container, false)
        
        val et1 = view.findViewById<EditText>(R.id.etContact1)
        val et2 = view.findViewById<EditText>(R.id.etContact2)
        val etEmail = view.findViewById<EditText>(R.id.etGuardianEmail)
        val btnSave = view.findViewById<Button>(R.id.btnSave)
        val btnTestAlert = view.findViewById<Button>(R.id.btnTestAlert)

        val prefs = requireContext().getSharedPreferences("shakti_prefs", Context.MODE_PRIVATE)
        
        // Get backend URL from shared preferences (set during app setup)
        backendUrl = prefs.getString("backend_url", "http://172.20.10.3:5000") ?: "http://172.20.10.3:5000"
        
        // Load from local storage first (for UI)
        et1.setText(prefs.getString("emergency_contact_1", ""))
        et2.setText(prefs.getString("emergency_contact_2", ""))
        etEmail.setText(prefs.getString("guardian_email", ""))

        btnSave.setOnClickListener {
            val contact1 = et1.text.toString().trim()
            val contact2 = et2.text.toString().trim()
            val email = etEmail.text.toString().trim()
            
            // Validate inputs
            if (contact1.isEmpty() && contact2.isEmpty()) {
                Toast.makeText(context, "Please enter at least one contact", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            // Save locally first
            prefs.edit()
                .putString("emergency_contact_1", contact1)
                .putString("emergency_contact_2", contact2)
                .putString("guardian_email", email)
                .apply()
            
            // Send to backend
            if (contact1.isNotEmpty()) {
                sendGuardianToBackend("Guardian 1", contact1)
            }
            if (contact2.isNotEmpty()) {
                sendGuardianToBackend("Guardian 2", contact2)
            }
            
            Toast.makeText(context, "Saving guardians...", Toast.LENGTH_SHORT).show()
        }

        btnTestAlert.setOnClickListener {
            // Attempt to get last known location from prefs if present
            val lastLat = prefs.getString("last_lat", "0.0") ?: "0.0"
            val lastLon = prefs.getString("last_lon", "0.0") ?: "0.0"
            checkBackendReachable(httpClient) { reachable ->
                activity?.runOnUiThread {
                    if (reachable) {
                        sendTestAlert("Test Alert from ShaktiApp", lastLat, lastLon)
                    } else {
                        Toast.makeText(requireContext(), "⚠️ Backend unreachable. Check backend_url setting and network.", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        // After view created, try flushing any pending guardians
        flushPendingGuardians()

        return view
    }
    
    private fun sendGuardianToBackend(name: String, phone: String) {
        val url = "$backendUrl/set-guardian-phone"

        val json = JSONObject()
        json.put("name", name)
        json.put("phone", phone)

        val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
        val body = json.toString().toRequestBody(mediaType)

        // Use shared HTTP client with timeouts
        val client = httpClient
        val okRequest = OkRequest.Builder()
            .url(url)
            .post(body)
            .build()

        // First quickly check backend reachability
        checkBackendReachable(client) { reachable ->
            if (!reachable) {
                // Save to pending queue and inform user
                savePendingGuardian(name, phone)
                activity?.runOnUiThread {
                    Toast.makeText(requireContext(), "⚠️ Backend unreachable. Saved locally and will retry when online.", Toast.LENGTH_LONG).show()
                }
                return@checkBackendReachable
            }

            client.newCall(okRequest).enqueue(object : Callback {
                override fun onFailure(call: Call, e: java.io.IOException) {
                    // On failure, queue for retry
                    Log.w(TAG, "sendGuardianToBackend failed: ${e.message}")
                    savePendingGuardian(name, phone)
                    activity?.runOnUiThread {
                        Toast.makeText(requireContext(), "❌ Network error. Guardian saved locally and will retry.", Toast.LENGTH_LONG).show()
                    }
                }

                override fun onResponse(call: Call, response: OkResponse) {
                    try {
                        val respStr = response.body?.string() ?: ""
                        val respJson = if (respStr.isNotEmpty()) JSONObject(respStr) else JSONObject()
                        val success = respJson.optBoolean("success", false)
                        activity?.runOnUiThread {
                            if (success) {
                                Toast.makeText(requireContext(), "✅ $name saved successfully!", Toast.LENGTH_SHORT).show()
                                // If success, attempt to flush any pending items (maybe duplicates)
                                flushPendingGuardians()
                            } else {
                                val error = respJson.optString("error", "Unknown error")
                                Toast.makeText(requireContext(), "❌ Error: $error", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        activity?.runOnUiThread {
                            Toast.makeText(requireContext(), "❌ Response parse error", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            })
        }
    }

    // ------------------ Pending queue helpers ------------------
    private fun savePendingGuardian(name: String, phone: String) {
        try {
            val prefs = requireContext().getSharedPreferences("shakti_prefs", Context.MODE_PRIVATE)
            val pendingStr = prefs.getString("pending_guardians", "[]") ?: "[]"
            val arr = JSONArray(pendingStr)
            val obj = JSONObject()
            obj.put("name", name)
            obj.put("phone", phone)
            arr.put(obj)
            prefs.edit().putString("pending_guardians", arr.toString()).apply()
            Log.i(TAG, "Saved pending guardian: $name - $phone")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save pending guardian: ${e.message}")
        }
    }

    private fun flushPendingGuardians() {
        try {
            val prefs = requireContext().getSharedPreferences("shakti_prefs", Context.MODE_PRIVATE)
            val pendingStr = prefs.getString("pending_guardians", "[]") ?: "[]"
            val arr = JSONArray(pendingStr)
            if (arr.length() == 0) return

            // Attempt to send each. We'll rebuild a new array for failures
            val remaining = JSONArray()
            val client = httpClient

            checkBackendReachable(client) { reachable ->
                if (!reachable) {
                    Log.i(TAG, "Backend unreachable - will keep ${arr.length()} pending guardians")
                    return@checkBackendReachable
                }

                for (i in 0 until arr.length()) {
                    val item = arr.getJSONObject(i)
                    val name = item.optString("name")
                    val phone = item.optString("phone")

                    val url = "$backendUrl/set-guardian-phone"
                    val json = JSONObject()
                    json.put("name", name)
                    json.put("phone", phone)
                    val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
                    val okRequest = OkRequest.Builder().url(url).post(body).build()

                    try {
                        val resp = client.newCall(okRequest).execute()
                        val respStr = resp.body?.string() ?: ""
                        val respJson = if (respStr.isNotEmpty()) JSONObject(respStr) else JSONObject()
                        val success = respJson.optBoolean("success", false)
                        if (!success) {
                            // keep it in remaining
                            remaining.put(item)
                        }
                    } catch (e: Exception) {
                        // on exception, keep item
                        remaining.put(item)
                    }
                }

                prefs.edit().putString("pending_guardians", remaining.toString()).apply()
                Log.i(TAG, "Flush completed. Remaining=${remaining.length()}")
            }

        } catch (e: Exception) {
            Log.w(TAG, "Failed to flush pending guardians: ${e.message}")
        }
    }

    // Quick backend reachability check
    private fun checkBackendReachable(client: OkHttpClient, callback: (Boolean) -> Unit) {
        try {
            // Ensure backendUrl starts with http:// or https://
            var testUrl = backendUrl
            if (!testUrl.startsWith("http://") && !testUrl.startsWith("https://")) {
                testUrl = "http://$testUrl"
            }
            if (testUrl.endsWith("/")) testUrl = testUrl.dropLast(1)
            val url = "$testUrl/guardians"

            val okRequest = OkRequest.Builder().url(url).get().build()
            client.newCall(okRequest).enqueue(object : Callback {
                override fun onFailure(call: Call, e: java.io.IOException) {
                    Log.w(TAG, "Backend reachability failed: ${e.message}")
                    callback(false)
                }

                override fun onResponse(call: Call, response: OkResponse) {
                    callback(response.isSuccessful)
                }
            })
        } catch (e: Exception) {
            Log.w(TAG, "checkBackendReachable exception: ${e.message}")
            callback(false)
        }
    }

    private fun sendTestAlert(message: String, lat: String, lon: String) {
        val url = "$backendUrl/quick_alert"

        val json = JSONObject()
        json.put("message", message)
        json.put("lat", lat)
        json.put("lon", lon)

        val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
        val body = json.toString().toRequestBody(mediaType)

        val client = httpClient
        val okRequest = OkRequest.Builder()
            .url(url)
            .post(body)
            .build()

        activity?.runOnUiThread {
            Toast.makeText(requireContext(), "🔔 Sending test alert...", Toast.LENGTH_SHORT).show()
        }

        // Check backend first
        checkBackendReachable(client) { reachable ->
            if (!reachable) {
                activity?.runOnUiThread {
                    Toast.makeText(requireContext(), "⚠️ Backend unreachable. Unable to send test alert.", Toast.LENGTH_LONG).show()
                }
                return@checkBackendReachable
            }

            client.newCall(okRequest).enqueue(object : Callback {
                override fun onFailure(call: Call, e: java.io.IOException) {
                    val msg = e.message ?: "Network error"
                    activity?.runOnUiThread {
                        Toast.makeText(requireContext(), "❌ Test alert failed: $msg", Toast.LENGTH_LONG).show()
                    }
                }

                override fun onResponse(call: Call, response: OkResponse) {
                    try {
                        val respStr = response.body?.string() ?: ""
                        val respJson = if (respStr.isNotEmpty()) JSONObject(respStr) else JSONObject()
                        val status = respJson.optString("status", "unknown")
                        val whatsappSent = respJson.optBoolean("whatsapp_sent", false)
                        activity?.runOnUiThread {
                            if (whatsappSent) {
                                Toast.makeText(requireContext(), "✅ Test alert sent (WhatsApp)", Toast.LENGTH_LONG).show()
                            } else {
                                val err = respJson.optString("whatsapp_error", respJson.optString("message", "No response"))
                                Toast.makeText(requireContext(), "⚠️ Test alert response: $status - $err", Toast.LENGTH_LONG).show()
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        activity?.runOnUiThread {
                            Toast.makeText(requireContext(), "❌ Test alert parse error", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            })
        }
    }
}
