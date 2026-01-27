package com.example.shaktialert

/**
 * Shared data models for Circle functionality
 * Used across CircleChatFragment and EnhancedLocationFragment
 */

// Circle Member data class - matches backend API response
data class CircleMember(
    val id: Int,              // user_id from backend
    val name: String,
    val photo: String,        // profile_photo from backend
    val battery: Int,         // battery_level from backend
    val address: String,
    val lastActive: String,   // last_update from backend
    val lat: Double = 0.0,
    val lon: Double = 0.0
)

// Chat Message data class
data class ChatMessage(
    val id: Int,
    val message: String,
    val senderName: String,
    val senderId: Int,
    val timestamp: String,
    val senderPhoto: String
)
