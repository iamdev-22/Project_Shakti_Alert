package com.example.shaktialert

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton

class ProfileFragment : Fragment() {

    private val PICK_IMAGE_REQUEST = 101
    private lateinit var imgProfile: ImageView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_profile, container, false)
        
        imgProfile = view.findViewById(R.id.imgProfile)
        val etName = view.findViewById<EditText>(R.id.etName)
        val etLastName = view.findViewById<EditText>(R.id.etLastName)
        val etEmail = view.findViewById<EditText>(R.id.etEmail)
        val etPhone = view.findViewById<EditText>(R.id.etPhone)
        val etAddress = view.findViewById<EditText>(R.id.etAddress)
        val btnSave = view.findViewById<MaterialButton>(R.id.btnSave)
        val btnLogout = view.findViewById<TextView>(R.id.btnLogout)
        val btnEditPhoto = view.findViewById<ImageButton>(R.id.btnEditPhoto)

        val prefs = requireContext().getSharedPreferences("shakti_prefs", Context.MODE_PRIVATE)
        
        // Load Data
        etName.setText(prefs.getString("user_name", ""))
        etLastName.setText(prefs.getString("user_last_name", ""))
        etEmail.setText(prefs.getString("user_email", ""))
        etPhone.setText(prefs.getString("user_phone", ""))
        etAddress.setText(prefs.getString("user_address", ""))
        
        // Load Photo
        val photoUriStr = prefs.getString("user_photo_uri", null)
        if (photoUriStr != null) {
            try {
                imgProfile.setImageURI(Uri.parse(photoUriStr))
            } catch (e: Exception) {
                // Fail silently
            }
        }
        
        btnSave.setOnClickListener {
            prefs.edit()
                .putString("user_name", etName.text.toString())
                .putString("user_last_name", etLastName.text.toString())
                .putString("user_email", etEmail.text.toString())
                .putString("user_phone", etPhone.text.toString())
                .putString("user_address", etAddress.text.toString())
                .apply()
            
            Toast.makeText(context, "Profile Saved Successfully!", Toast.LENGTH_SHORT).show()
        }

        btnEditPhoto.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "image/*"
                addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivityForResult(intent, PICK_IMAGE_REQUEST)
        }
        
        btnLogout.setOnClickListener {
            // Call logout API
            val serverUrl = prefs.getString("server_url", "http://192.168.29.91:5000") ?: "http://192.168.29.91:5000"
            val token = prefs.getString("auth_token", "") ?: ""
            
            val client = okhttp3.OkHttpClient()
            val request = okhttp3.Request.Builder()
                .url("$serverUrl/auth/logout")
                .addHeader("Authorization", "Bearer $token")
                .post(okhttp3.RequestBody.create(null, ByteArray(0)))
                .build()
            
            client.newCall(request).enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                    // Even if API fails, logout locally
                    activity?.runOnUiThread {
                        performLogout(prefs)
                    }
                }
                
                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    response.close()
                    activity?.runOnUiThread {
                        performLogout(prefs)
                    }
                }
            })
        }
        
        return view
    }
    
    private fun performLogout(prefs: android.content.SharedPreferences) {
        prefs.edit().clear().apply()
        val intent = Intent(requireContext(), LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        activity?.finish()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK) {
            data?.data?.let { uri ->
                requireContext().contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                
                imgProfile.setImageURI(uri)
                
                // Save URI
                val prefs = requireContext().getSharedPreferences("shakti_prefs", Context.MODE_PRIVATE)
                prefs.edit().putString("user_photo_uri", uri.toString()).apply()
            }
        }
    }
}
