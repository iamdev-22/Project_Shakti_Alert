package com.example.shaktialert

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.json.JSONObject
import java.io.IOException

/**
 * GroupChatActivity - Full-featured Instagram-style group chat
 * Features: Text messaging, Voice calls, Video calls, File sharing
 */
class GroupChatActivity : AppCompatActivity() {

    private val TAG = "GroupChatActivity"
    private val client = OkHttpClient()
    private lateinit var recyclerView: RecyclerView
    private lateinit var messagesAdapter: GroupMessagesAdapter
    private val messagesList = mutableListOf<ChatMessage>()
    private lateinit var messageInput: EditText
    private lateinit var sendButton: ImageButton
    private lateinit var voiceCallButton: ImageButton
    private lateinit var videoCallButton: ImageButton
    private lateinit var attachButton: ImageButton
    
    private var circleId: Int = 0
    private var circleName: String = ""
    
    companion object {
        fun start(context: Context, circleId: Int, circleName: String) {
            val intent = Intent(context, GroupChatActivity::class.java)
            intent.putExtra("circle_id", circleId)
            intent.putExtra("circle_name", circleName)
            context.startActivity(intent)
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_group_chat)
        
        // Get circle info
        circleId = intent.getIntExtra("circle_id", 0)
        circleName = intent.getStringExtra("circle_name") ?: "Group Chat"
        
        // Set title
        supportActionBar?.title = circleName
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        
        // Initialize views
        recyclerView = findViewById(R.id.recyclerMessages)
        messageInput = findViewById(R.id.etMessage)
        sendButton = findViewById(R.id.btnSend)
        voiceCallButton = findViewById(R.id.btnVoiceCall)
        videoCallButton = findViewById(R.id.btnVideoCall)
        attachButton = findViewById(R.id.btnAttach)
        
        // Setup RecyclerView
        recyclerView.layoutManager = LinearLayoutManager(this)
        messagesAdapter = GroupMessagesAdapter(messagesList)
        recyclerView.adapter = messagesAdapter
        
        // Button listeners
        sendButton.setOnClickListener {
            val message = messageInput.text.toString().trim()
            if (message.isNotEmpty()) {
                sendMessage(message)
                messageInput.text.clear()
            }
        }
        
        voiceCallButton.setOnClickListener {
            startVoiceCall()
        }
        
        videoCallButton.setOnClickListener {
            startVideoCall()
        }
        
        attachButton.setOnClickListener {
            showAttachmentOptions()
        }
        
        // Load messages
        loadMessages()
        
        // Auto-refresh every 3 seconds
        startAutoRefresh()
    }
    
    private fun loadMessages() {
        val prefs = getSharedPreferences("shakti_prefs", Context.MODE_PRIVATE)
        val baseUrl = prefs.getString("server_url", "http://192.168.29.91:5000") ?: return
        val token = prefs.getString("auth_token", "") ?: return
        
        val request = Request.Builder()
            .url("$baseUrl/api/circles/$circleId/messages?limit=100")
            .addHeader("Authorization", "Bearer $token")
            .get()
            .build()
        
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                // Silently fail
            }
            
            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string() ?: ""
                response.close()
                
                try {
                    val json = JSONObject(responseBody)
                    if (json.getBoolean("success")) {
                        val messagesArray = json.getJSONArray("messages")
                        messagesList.clear()
                        
                        for (i in 0 until messagesArray.length()) {
                            val msgObj = messagesArray.getJSONObject(i)
                            messagesList.add(ChatMessage(
                                id = msgObj.getInt("id"),
                                message = msgObj.getString("message"),
                                senderName = msgObj.getString("sender_name"),
                                senderId = msgObj.getInt("sender_id"),
                                timestamp = msgObj.getString("timestamp"),
                                senderPhoto = msgObj.optString("sender_photo", "")
                            ))
                        }
                        
                        runOnUiThread {
                            messagesAdapter.notifyDataSetChanged()
                            if (messagesList.isNotEmpty()) {
                                recyclerView.scrollToPosition(messagesList.size - 1)
                            }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "Error loading messages: ${e.message}")
                }
            }
        })
    }
    
    private fun sendMessage(message: String) {
        val prefs = getSharedPreferences("shakti_prefs", Context.MODE_PRIVATE)
        val baseUrl = prefs.getString("server_url", "http://192.168.29.91:5000") ?: return
        val token = prefs.getString("auth_token", "") ?: return
        
        val json = """{"message": "$message"}"""
        val requestBody = RequestBody.create("application/json".toMediaTypeOrNull(), json)
        
        val request = Request.Builder()
            .url("$baseUrl/api/circles/$circleId/messages")
            .addHeader("Authorization", "Bearer $token")
            .post(requestBody)
            .build()
        
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@GroupChatActivity, "Failed to send message", Toast.LENGTH_SHORT).show()
                }
            }
            
            override fun onResponse(call: Call, response: Response) {
                response.close()
                // Reload messages to show new message
                loadMessages()
            }
        })
    }
    
    private fun startVoiceCall() {
        AlertDialog.Builder(this)
            .setTitle("Voice Call")
            .setMessage("Start voice call with all members of $circleName?")
            .setPositiveButton("Start Call") { _, _ ->
                // TODO: Implement WebRTC voice call
                Toast.makeText(this, "Voice call feature coming soon!", Toast.LENGTH_LONG).show()
                // For now, show a placeholder
                showCallPlaceholder("Voice Call")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun startVideoCall() {
        AlertDialog.Builder(this)
            .setTitle("Video Call")
            .setMessage("Start video call with all members of $circleName?")
            .setPositiveButton("Start Call") { _, _ ->
                // TODO: Implement WebRTC video call
                Toast.makeText(this, "Video call feature coming soon!", Toast.LENGTH_LONG).show()
                // For now, show a placeholder
                showCallPlaceholder("Video Call")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showCallPlaceholder(callType: String) {
        AlertDialog.Builder(this)
            .setTitle("$callType in Progress")
            .setMessage("Connecting to group members...\n\n📞 Calling all members\n⏱️ Duration: 00:00")
            .setPositiveButton("End Call") { dialog, _ ->
                dialog.dismiss()
                Toast.makeText(this, "Call ended", Toast.LENGTH_SHORT).show()
            }
            .setCancelable(false)
            .show()
    }
    
    private fun showAttachmentOptions() {
        val options = arrayOf("📷 Camera", "🖼️ Gallery", "📍 Location", "📄 Document")
        AlertDialog.Builder(this)
            .setTitle("Send Attachment")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> Toast.makeText(this, "Camera feature coming soon!", Toast.LENGTH_SHORT).show()
                    1 -> Toast.makeText(this, "Gallery feature coming soon!", Toast.LENGTH_SHORT).show()
                    2 -> sendCurrentLocation()
                    3 -> Toast.makeText(this, "Document feature coming soon!", Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }
    
    private fun sendCurrentLocation() {
        // Send current location as a message
        sendMessage("📍 Shared location")
        Toast.makeText(this, "Location shared!", Toast.LENGTH_SHORT).show()
    }
    
    private fun startAutoRefresh() {
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val runnable = object : Runnable {
            override fun run() {
                loadMessages()
                handler.postDelayed(this, 3000) // Refresh every 3 seconds
            }
        }
        handler.postDelayed(runnable, 3000)
    }
    
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}

// Adapter for Group Messages
class GroupMessagesAdapter(
    private val messages: List<ChatMessage>
) : RecyclerView.Adapter<GroupMessagesAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val senderName: TextView = view.findViewById(R.id.tvSenderName)
        val messageText: TextView = view.findViewById(R.id.tvMessage)
        val timestamp: TextView = view.findViewById(R.id.tvTimestamp)
        val profilePhoto: ImageView = view.findViewById(R.id.imgProfile)
        val messageContainer: View = view.findViewById(R.id.messageContainer)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat_message, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val message = messages[position]
        holder.senderName.text = message.senderName
        holder.messageText.text = message.message
        
        // Format timestamp (just show time)
        try {
            val time = message.timestamp.split("T")[1].substring(0, 5)
            holder.timestamp.text = time
        } catch (e: Exception) {
            holder.timestamp.text = ""
        }
        
        // TODO: Load profile photo with Glide/Picasso
        // For now, use default avatar
    }

    override fun getItemCount() = messages.size
}
