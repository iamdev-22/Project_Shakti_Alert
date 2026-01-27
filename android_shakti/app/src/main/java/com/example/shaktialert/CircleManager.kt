package com.example.shaktialert

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * Manages circle data persistence
 * Saves circles locally so they don't disappear when app closes
 */
class CircleManager(context: Context) {
    private val prefs: SharedPreferences = 
        context.getSharedPreferences("circles_data", Context.MODE_PRIVATE)
    
    companion object {
        private const val TAG = "CircleManager"
        private const val KEY_CIRCLES = "circles"
        private const val KEY_CURRENT_CIRCLE = "current_circle_id"
    }
    
    /**
     * Save circle locally
     */
    fun saveCircle(circleId: Int, circleName: String, inviteCode: String, memberCount: Int = 0, role: String = "member") {
        try {
            val circles = getCircles().toMutableList()
            
            // Check if circle already exists
            val existingIndex = circles.indexOfFirst { it.id == circleId }
            
            val circle = Circle(circleId, circleName, inviteCode, memberCount, role)
            
            if (existingIndex >= 0) {
                circles[existingIndex] = circle
                Log.d(TAG, "Updated existing circle: $circleName")
            } else {
                circles.add(circle)
                Log.d(TAG, "Added new circle: $circleName")
            }
            
            // Save to SharedPreferences
            val jsonArray = JSONArray()
            circles.forEach { c ->
                val json = JSONObject()
                json.put("id", c.id)
                json.put("name", c.name)
                json.put("invite_code", c.inviteCode)
                json.put("member_count", c.memberCount)
                json.put("role", c.role)
                jsonArray.put(json)
            }
            
            prefs.edit().putString(KEY_CIRCLES, jsonArray.toString()).apply()
            Log.d(TAG, "Saved ${circles.size} circles to storage")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving circle: ${e.message}")
        }
    }
    
    /**
     * Get all saved circles
     */
    fun getCircles(): List<Circle> {
        return try {
            val circlesJson = prefs.getString(KEY_CIRCLES, "[]") ?: "[]"
            val jsonArray = JSONArray(circlesJson)
            val circles = mutableListOf<Circle>()
            
            for (i in 0 until jsonArray.length()) {
                val json = jsonArray.getJSONObject(i)
                circles.add(Circle(
                    json.getInt("id"),
                    json.getString("name"),
                    json.getString("invite_code"),
                    json.optInt("member_count", 0),
                    json.optString("role", "member")
                ))
            }
            
            Log.d(TAG, "Loaded ${circles.size} circles from storage")
            circles
        } catch (e: Exception) {
            Log.e(TAG, "Error loading circles: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Get current active circle
     */
    fun getCurrentCircle(): Circle? {
        val circleId = prefs.getInt(KEY_CURRENT_CIRCLE, -1)
        if (circleId == -1) return null
        
        return getCircles().find { it.id == circleId }
    }
    
    /**
     * Set current active circle
     */
    fun setCurrentCircle(circleId: Int) {
        prefs.edit().putInt(KEY_CURRENT_CIRCLE, circleId).apply()
        Log.d(TAG, "Set current circle to: $circleId")
    }
    
    /**
     * Remove a circle
     */
    fun removeCircle(circleId: Int) {
        try {
            val circles = getCircles().toMutableList()
            circles.removeAll { it.id == circleId }
            
            val jsonArray = JSONArray()
            circles.forEach { c ->
                val json = JSONObject()
                json.put("id", c.id)
                json.put("name", c.name)
                json.put("invite_code", c.inviteCode)
                json.put("member_count", c.memberCount)
                json.put("role", c.role)
                jsonArray.put(json)
            }
            
            prefs.edit().putString(KEY_CIRCLES, jsonArray.toString()).apply()
            
            // If removed circle was current, clear current
            if (getCurrentCircle()?.id == circleId) {
                prefs.edit().remove(KEY_CURRENT_CIRCLE).apply()
            }
            
            Log.d(TAG, "Removed circle: $circleId")
        } catch (e: Exception) {
            Log.e(TAG, "Error removing circle: ${e.message}")
        }
    }
    
    /**
     * Clear all circles
     */
    fun clearCircles() {
        prefs.edit()
            .remove(KEY_CIRCLES)
            .remove(KEY_CURRENT_CIRCLE)
            .apply()
        Log.d(TAG, "Cleared all circles")
    }
    
    /**
     * Check if user has any circles
     */
    fun hasCircles(): Boolean {
        return getCircles().isNotEmpty()
    }
}
