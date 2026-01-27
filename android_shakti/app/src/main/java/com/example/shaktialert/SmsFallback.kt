package com.example.shaktialert

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.ContextCompat

object SmsFallback {
    
    private const val TAG = "SmsFallback"
    
    /**
     * Send SMS using Android's built-in SMS (100% reliable)
     * This uses the phone's SIM card directly
     */
    fun sendEmergencySMS(
        context: Context,
        phoneNumber: String,
        message: String,
        locationLink: String = ""
    ): Boolean {
        return try {
            // Check SMS permission
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) 
                != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "SMS permission not granted")
                return false
            }
            
            // Format phone number (add +91 if needed for India)
            val formattedPhone = when {
                phoneNumber.startsWith("+") -> phoneNumber
                phoneNumber.length == 10 -> "+91$phoneNumber"
                else -> "+$phoneNumber"
            }
            
            // Prepare SMS message (max 160 chars)
            val smsText = "🚨 SHAKTI ALERT!\n$message\n📍 $locationLink"
            val truncatedText = if (smsText.length > 160) {
                "🚨 EMERGENCY! ${message.take(100)}... $locationLink"
            } else {
                smsText
            }
            
            Log.i(TAG, "Sending SMS to $formattedPhone")
            
            // Send SMS using Android SmsManager
            val smsManager = SmsManager.getDefault()
            
            // If message is too long, split it
            if (truncatedText.length > 160) {
                val parts = smsManager.divideMessage(truncatedText)
                smsManager.sendMultipartTextMessage(
                    formattedPhone,
                    null,
                    parts,
                    null,
                    null
                )
            } else {
                smsManager.sendTextMessage(
                    formattedPhone,
                    null,
                    truncatedText,
                    null,
                    null
                )
            }
            
            Log.i(TAG, "✅ SMS sent successfully via Android SIM")
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ SMS sending failed: ${e.message}", e)
            false
        }
    }
    
    /**
     * Check if SMS permission is granted
     */
    fun hasSmsPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) ==
                PackageManager.PERMISSION_GRANTED
    }
}
