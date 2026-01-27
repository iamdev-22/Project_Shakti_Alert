package com.example.shaktialert

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.json.JSONObject
import java.io.IOException

/**
 * CircleChatFragment - Life360 style group chat
 * Real-time messaging for circle members
 */
class CircleChatFragment : Fragment() {

    private val TAG = "CircleChatFragment"
    private val client = OkHttpClient()
    private lateinit var recyclerView: RecyclerView
    private lateinit var messagesAdapter: MessagesAdapter
    private val messagesList = mutableListOf<ChatMessage>()
    private lateinit var messageInput: EditText
    private lateinit var sendButton: Button
    
    private var circleId: Int = 0
    private var circleName: String = ""
    
    companion object {
        fun newInstance(circleId: Int, circleName: String): CircleChatFragment {
            val fragment = CircleChatFragment()
            val args = Bundle()
            args.putInt("circle_id", circleId)
            args.putString("circle_name", circleName)
            fragment.arguments = args
            return fragment
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            circleId = it.getInt("circle_id")
            circleName = it.getString("circle_name", "Circle Chat")
        }
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_circle_chat, container, false)
        
        // Set title
        view.findViewById<TextView>(R.id.tvChatTitle).text = circleName
        
        // Back Button
        view.findViewById<View>(R.id.btnBack).setOnClickListener {
            parentFragmentManager.popBackStack()
        }
        
        // Members Button
        view.findViewById<View>(R.id.btnMembers).setOnClickListener {
            showMembersDialog()
        }
        
        // Initialize RecyclerView
        recyclerView = view.findViewById(R.id.recyclerMessages)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        messagesAdapter = MessagesAdapter(messagesList)
        recyclerView.adapter = messagesAdapter
        
        // Initialize input
        messageInput = view.findViewById(R.id.etMessage)
        sendButton = view.findViewById(R.id.btnSend)
        
        sendButton.setOnClickListener {
            val message = messageInput.text.toString().trim()
            if (message.isNotEmpty()) {
                sendMessage(message)
                messageInput.text.clear()
            }
        }
        
        // Load messages
        loadMessages()
        
        // Auto-refresh every 3 seconds
        startAutoRefresh()
        
        return view
    }
    
    
    private fun showMembersDialog() {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_members, null)
        val title = dialogView.findViewById<TextView>(R.id.tvDialogTitle)
        val recyclerMembers = dialogView.findViewById<RecyclerView>(R.id.recyclerContacts)
        val progressBar = dialogView.findViewById<ProgressBar>(R.id.progressBar)
        
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setPositiveButton("Close", null)
            .show()
            
        // Fetch members
        fetchMembers(recyclerMembers, progressBar)
    }
    
    private fun fetchMembers(recyclerView: RecyclerView, progressBar: ProgressBar) {
        val prefs = requireContext().getSharedPreferences("shakti_prefs", Context.MODE_PRIVATE)
        val baseUrl = prefs.getString("server_url", "http://192.168.1.35:5000") ?: return
        val token = prefs.getString("auth_token", "") ?: return
        
        val request = Request.Builder()
            .url("$baseUrl/api/circles/$circleId/members")
            .addHeader("Authorization", "Bearer $token")
            .get()
            .build()
            
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                activity?.runOnUiThread {
                    progressBar.visibility = View.GONE
                    Toast.makeText(context, "Failed to load members", Toast.LENGTH_SHORT).show()
                }
            }
            
            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string() ?: ""
                response.close()
                
                try {
                    val json = JSONObject(responseBody)
                    if (json.getBoolean("success")) {
                        val membersArray = json.getJSONArray("members")
                        val membersList = mutableListOf<CircleMember>()
                        
                        for (i in 0 until membersArray.length()) {
                            val obj = membersArray.getJSONObject(i)
                            membersList.add(CircleMember(
                                id = obj.getInt("id"),
                                name = obj.getString("name"),
                                photo = obj.optString("profile_photo", ""),
                                battery = obj.optInt("battery_level", 0),
                                address = obj.optString("address", "Unknown Location"),
                                lastActive = obj.optString("last_updated", "Just now")
                            ))
                        }
                        
                        
                        activity?.runOnUiThread {
                            progressBar.visibility = View.GONE
                            recyclerView.visibility = View.VISIBLE
                            recyclerView.layoutManager = LinearLayoutManager(context)
                            recyclerView.adapter = CircleMembersAdapter(membersList)
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "Error parsing members: ${e.message}")
                }
            }
        })
    }
    
    private fun loadMessages() {
        val prefs = requireContext().getSharedPreferences("shakti_prefs", Context.MODE_PRIVATE)
        val baseUrl = prefs.getString("server_url", "http://192.168.1.35:5000") ?: return
        val token = prefs.getString("auth_token", "") ?: return
        
        val request = Request.Builder()
            .url("$baseUrl/api/circles/$circleId/messages?limit=50")
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
                        
                        activity?.runOnUiThread {
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
        val prefs = requireContext().getSharedPreferences("shakti_prefs", Context.MODE_PRIVATE)
        val baseUrl = prefs.getString("server_url", "http://192.168.1.35:5000") ?: return
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
                activity?.runOnUiThread {
                    Toast.makeText(context, "Failed to send message", Toast.LENGTH_SHORT).show()
                }
            }
            
            override fun onResponse(call: Call, response: Response) {
                response.close()
                // Reload messages to show new message
                loadMessages()
            }
        })
    }
    
    private fun startAutoRefresh() {
        view?.postDelayed(object : Runnable {
            override fun run() {
                loadMessages()
                view?.postDelayed(this, 3000) // Refresh every 3 seconds
            }
        }, 3000)
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        // Stop auto-refresh
        view?.removeCallbacks(null)
    }
}

// Adapters
class MessagesAdapter(
    private val messages: List<ChatMessage>
) : RecyclerView.Adapter<MessagesAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val senderName: TextView = view.findViewById(R.id.tvSenderName)
        val messageText: TextView = view.findViewById(R.id.tvMessage)
        val timestamp: TextView = view.findViewById(R.id.tvTimestamp)
        val profilePhoto: ImageView = view.findViewById(R.id.imgProfile)
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
        
        try {
            val time = message.timestamp.split("T")[1].substring(0, 5)
            holder.timestamp.text = time
        } catch (e: Exception) {
            holder.timestamp.text = ""
        }
    }

    override fun getItemCount() = messages.size
}

