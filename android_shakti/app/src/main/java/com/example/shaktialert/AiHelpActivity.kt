package com.example.shaktialert

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.json.JSONObject
import java.io.IOException

class AiHelpActivity : AppCompatActivity() {

    private lateinit var chatContainer: androidx.appcompat.widget.LinearLayoutCompat
    private lateinit var scrollView: ScrollView
    private lateinit var inputMessage: EditText
    private lateinit var btnSend: ImageButton
    private lateinit var progressBar: ProgressBar
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ai_help)

        // Setup toolbar
        supportActionBar?.apply {
            title = "AI Help Assistant"
            setDisplayHomeAsUpEnabled(true)
        }

        chatContainer = findViewById(R.id.chatContainer)
        scrollView = findViewById(R.id.scrollView)
        inputMessage = findViewById(R.id.inputMessage)
        btnSend = findViewById(R.id.btnSend)
        progressBar = findViewById(R.id.progressBar)

        btnSend.setOnClickListener {
            sendMessage()
        }

        // Add welcome message
        addAiMessage("👋 Hi! I'm your Shakti Alert AI assistant. Ask me anything about how the app works, emergency features, or troubleshooting!")
    }

    private fun sendMessage() {
        val message = inputMessage.text.toString().trim()
        if (message.isEmpty()) {
            Toast.makeText(this, "Please enter a message", Toast.LENGTH_SHORT).show()
            return
        }

        // Add user message to chat
        addUserMessage(message)
        inputMessage.text.clear()

        // Show loading
        progressBar.visibility = View.VISIBLE
        btnSend.isEnabled = false

        // Send to AI
        scope.launch {
            try {
                val response = getAiResponse(message)
                addAiMessage(response)
            } catch (e: Exception) {
                addAiMessage("❌ Sorry, I'm having trouble connecting. Please try again.\n\nError: ${e.message}")
            } finally {
                progressBar.visibility = View.GONE
                btnSend.isEnabled = true
            }
        }
    }

    private suspend fun getAiResponse(message: String): String = withContext(Dispatchers.IO) {
        val prefs = getSharedPreferences("shakti_prefs", MODE_PRIVATE)
        val serverUrl = prefs.getString("server_url", "http://192.168.1.35:5000") ?: "http://192.168.1.35:5000"
        
        val json = JSONObject().apply {
            put("message", message)
        }

        val requestBody = RequestBody.create(
            "application/json".toMediaTypeOrNull(),
            json.toString()
        )

        val request = Request.Builder()
            .url("$serverUrl/api/ai/chat")
            .post(requestBody)
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string() ?: throw IOException("Empty response")
        
        if (!response.isSuccessful) {
            throw IOException("Server error: ${response.code}")
        }

        val jsonResponse = JSONObject(responseBody)
        jsonResponse.getString("response")
    }

    private fun addUserMessage(message: String) {
        val messageView = layoutInflater.inflate(R.layout.item_chat_user, chatContainer, false)
        messageView.findViewById<TextView>(R.id.messageText).text = message
        chatContainer.addView(messageView)
        scrollToBottom()
    }

    private fun addAiMessage(message: String) {
        val messageView = layoutInflater.inflate(R.layout.item_chat_ai, chatContainer, false)
        messageView.findViewById<TextView>(R.id.messageText).text = message
        chatContainer.addView(messageView)
        scrollToBottom()
    }

    private fun scrollToBottom() {
        scrollView.post {
            scrollView.fullScroll(View.FOCUS_DOWN)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
