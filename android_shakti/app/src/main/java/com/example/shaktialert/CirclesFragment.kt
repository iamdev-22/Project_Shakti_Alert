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
 * CirclesFragment - Life360 style circles/groups management
 * Features: Create circle, Join circle, View circles, Manage members
 */
class CirclesFragment : Fragment() {

    private val TAG = "CirclesFragment"
    private val client = OkHttpClient()
    private lateinit var recyclerView: RecyclerView
    private lateinit var circlesAdapter: CirclesAdapter
    private val circlesList = mutableListOf<Circle>()
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_circles, container, false)

        // ✅ FIX: Check login before doing anything
        val prefs = requireContext().getSharedPreferences("shakti_prefs", Context.MODE_PRIVATE)
        val token = prefs.getString("auth_token", "") ?: ""
        if (token.isEmpty()) {
            Toast.makeText(requireContext(),
                "⚠️ Please login first to use Circles",
                Toast.LENGTH_LONG).show()
            // Redirect to login
            startActivity(android.content.Intent(requireContext(), LoginActivity::class.java))
            return view
        }

        // Initialize RecyclerView
        recyclerView = view.findViewById(R.id.recyclerCircles)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        circlesAdapter = CirclesAdapter(circlesList,
            onCircleClick = { circle ->
                // Handle circle click - show members
                showCircleMembers(circle)
            },
            onDeleteClick = { circle ->
                // Show confirmation dialog
                AlertDialog.Builder(requireContext())
                    .setTitle("Delete Circle")
                    .setMessage("Are you sure you want to delete '${circle.name}'? This cannot be undone.")
                    .setPositiveButton("Delete") { _, _ ->
                        deleteCircle(circle.id)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        )
        recyclerView.adapter = circlesAdapter

        // Create Circle button
        view.findViewById<Button>(R.id.btnCreateCircle).setOnClickListener {
            showCreateCircleDialog()
        }

        // Join Circle button
        view.findViewById<Button>(R.id.btnJoinCircle).setOnClickListener {
            showJoinCircleDialog()
        }

        // Load circles
        loadCircles()

        return view
    }
    
    private fun loadCircles() {
        val prefs = requireContext().getSharedPreferences("shakti_prefs", Context.MODE_PRIVATE)
        val baseUrl = prefs.getString("server_url", "http://192.168.29.91:5000") ?: return
        val token = prefs.getString("auth_token", "") ?: return
        
        val request = Request.Builder()
            .url("$baseUrl/api/circles/list")
            .addHeader("Authorization", "Bearer $token")
            .get()
            .build()
        
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                activity?.runOnUiThread {
                    Toast.makeText(context, "Failed to load circles", Toast.LENGTH_SHORT).show()
                }
            }
            
            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string() ?: ""
                response.close()
                
                try {
                    val json = JSONObject(responseBody)
                    if (json.getBoolean("success")) {
                        val circlesArray = json.getJSONArray("circles")
                        circlesList.clear()
                        
                        for (i in 0 until circlesArray.length()) {
                            val circleObj = circlesArray.getJSONObject(i)
                            circlesList.add(Circle(
                                id = circleObj.getInt("id"),
                                name = circleObj.getString("name"),
                                inviteCode = circleObj.getString("invite_code"),
                                memberCount = circleObj.getInt("member_count"),
                                role = circleObj.getString("role")
                            ))
                        }
                        
                        activity?.runOnUiThread {
                            circlesAdapter.notifyDataSetChanged()
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "Error parsing circles: ${e.message}")
                }
            }
        })
    }
    
    private fun showCreateCircleDialog() {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_create_circle, null)
        val nameInput = dialogView.findViewById<EditText>(R.id.etCircleName)
        
        AlertDialog.Builder(requireContext())
            .setTitle("Create a Circle")
            .setView(dialogView)
            .setPositiveButton("Create") { _, _ ->
                val circleName = nameInput.text.toString().trim()
                if (circleName.isNotEmpty()) {
                    createCircle(circleName)
                } else {
                    Toast.makeText(context, "Please enter a circle name", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun createCircle(name: String) {
        val prefs = requireContext().getSharedPreferences("shakti_prefs", Context.MODE_PRIVATE)
        val baseUrl = prefs.getString("server_url", "http://192.168.29.91:5000") ?: return
        val token = prefs.getString("auth_token", "") ?: return
        
        val json = """{"name": "$name"}"""
        val requestBody = RequestBody.create("application/json".toMediaTypeOrNull(), json)
        
        val request = Request.Builder()
            .url("$baseUrl/api/circles/create")
            .addHeader("Authorization", "Bearer $token")
            .post(requestBody)
            .build()
        
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                activity?.runOnUiThread {
                    Toast.makeText(context, "Failed to create circle", Toast.LENGTH_SHORT).show()
                }
            }
            
            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string() ?: ""
                response.close()
                
                try {
                    val json = JSONObject(responseBody)
                    if (json.getBoolean("success")) {
                        val inviteCode = json.getString("invite_code")
                        activity?.runOnUiThread {
                            showInviteCodeDialog(inviteCode)
                            loadCircles() // Refresh list
                        }
                    } else {
                        activity?.runOnUiThread {
                            Toast.makeText(context, json.getString("error"), Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "Error creating circle: ${e.message}")
                }
            }
        })
    }
    
    private fun showInviteCodeDialog(inviteCode: String) {
        AlertDialog.Builder(requireContext())
            .setTitle("Circle Created!")
            .setMessage("Share this invite code with others:\n\n$inviteCode")
            .setPositiveButton("Copy Code") { _, _ ->
                val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("Invite Code", inviteCode)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, "Invite code copied!", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("OK", null)
            .show()
    }
    
    private fun showJoinCircleDialog() {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_join_circle, null)
        val codeInput = dialogView.findViewById<EditText>(R.id.etInviteCode)
        
        AlertDialog.Builder(requireContext())
            .setTitle("Join a Circle")
            .setView(dialogView)
            .setPositiveButton("Join") { _, _ ->
                val inviteCode = codeInput.text.toString().trim().uppercase()
                if (inviteCode.isNotEmpty()) {
                    joinCircle(inviteCode)
                } else {
                    Toast.makeText(context, "Please enter an invite code", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun joinCircle(inviteCode: String) {
        val prefs = requireContext().getSharedPreferences("shakti_prefs", Context.MODE_PRIVATE)
        val baseUrl = prefs.getString("server_url", "http://192.168.29.91:5000") ?: return
        val token = prefs.getString("auth_token", "") ?: return
        
        val json = """{"invite_code": "$inviteCode"}"""
        val requestBody = RequestBody.create("application/json".toMediaTypeOrNull(), json)
        
        val request = Request.Builder()
            .url("$baseUrl/api/circles/join")
            .addHeader("Authorization", "Bearer $token")
            .post(requestBody)
            .build()
        
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                activity?.runOnUiThread {
                    Toast.makeText(context, "Failed to join circle", Toast.LENGTH_SHORT).show()
                }
            }
            
            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string() ?: ""
                response.close()
                
                try {
                    val json = JSONObject(responseBody)
                    activity?.runOnUiThread {
                        if (json.getBoolean("success")) {
                            Toast.makeText(context, json.getString("message"), Toast.LENGTH_SHORT).show()
                            loadCircles() // Refresh list
                        } else {
                            Toast.makeText(context, json.getString("error"), Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "Error joining circle: ${e.message}")
                }
            }
        })
    }
    
    private fun deleteCircle(circleId: Int) {
        val prefs = requireContext().getSharedPreferences("shakti_prefs", Context.MODE_PRIVATE)
        val baseUrl = prefs.getString("server_url", "http://192.168.29.91:5000") ?: return
        val token = prefs.getString("auth_token", "") ?: return
        
        val request = Request.Builder()
            .url("$baseUrl/api/circles/$circleId/delete")
            .addHeader("Authorization", "Bearer $token")
            .delete()
            .build()
        
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                activity?.runOnUiThread {
                    Toast.makeText(context, "Failed to delete circle", Toast.LENGTH_SHORT).show()
                }
            }
            
            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string() ?: ""
                response.close()
                
                try {
                    val json = JSONObject(responseBody)
                    activity?.runOnUiThread {
                        if (json.getBoolean("success")) {
                            Toast.makeText(context, "Circle deleted successfully", Toast.LENGTH_SHORT).show()
                            loadCircles() // Refresh list
                        } else {
                            Toast.makeText(context, json.getString("error"), Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "Error deleting circle: ${e.message}")
                }
            }
        })
    }

    private fun showCircleMembers(circle: Circle) {
        // Navigate to CircleChatFragment
        val chatFragment = CircleChatFragment.newInstance(circle.id, circle.name)
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, chatFragment)
            .addToBackStack(null)
            .commit()
    }
}

// Data class for Circle
data class Circle(
    val id: Int,
    val name: String,
    val inviteCode: String,
    val memberCount: Int,
    val role: String
)

// Adapter for Circles RecyclerView
class CirclesAdapter(
    private val circles: List<Circle>,
    private val onCircleClick: (Circle) -> Unit,
    private val onDeleteClick: (Circle) -> Unit
) : RecyclerView.Adapter<CirclesAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nameText: TextView = view.findViewById(R.id.tvCircleName)
        val membersText: TextView = view.findViewById(R.id.tvMemberCount)
        val codeText: TextView = view.findViewById(R.id.tvInviteCode)
        val deleteButton: ImageButton = view.findViewById(R.id.btnDeleteCircle)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_circle, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val circle = circles[position]
        holder.nameText.text = circle.name
        holder.membersText.text = "${circle.memberCount} members"
        holder.codeText.text = "${circle.inviteCode}"
        
        // Show delete button only if user is admin
        if (circle.role == "admin" || circle.role == "creator") {
            holder.deleteButton.visibility = View.VISIBLE
            holder.deleteButton.setOnClickListener {
                onDeleteClick(circle)
            }
        } else {
            holder.deleteButton.visibility = View.GONE
        }
        
        holder.itemView.setOnClickListener {
            onCircleClick(circle)
        }
    }

    override fun getItemCount() = circles.size
}
