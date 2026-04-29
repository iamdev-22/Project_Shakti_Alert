package com.example.shaktialert

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.core.app.NotificationCompat

/**
 * SetupManager — enforces onboarding flow per user.
 *
 * REQUIRED STEPS (in order):
 *   1. login          — user must be logged in
 *   2. profile        — name/DOB filled
 *   3. guardian       — at least one guardian phone saved
 *   4. whatsapp       — WhatsApp QR scanned & connected
 *   5. voice          — voice keyword recorded/confirmed
 *
 * Each step must be completed before the next is accessible.
 * Features are BLOCKED with a notification if steps are skipped.
 */
object SetupManager {

    private const val PREFS_NAME        = "shakti_prefs"
    private const val KEY_SETUP_USER    = "setup_completed_for_user"  // which user completed setup
    private const val KEY_STEP_PROFILE  = "setup_step_profile"
    private const val KEY_STEP_GUARDIAN = "setup_step_guardian"
    private const val KEY_STEP_WHATSAPP = "setup_step_whatsapp"
    private const val KEY_STEP_VOICE    = "setup_step_voice"
    private const val NOTIF_CHANNEL     = "shakti_setup"
    private const val NOTIF_ID          = 9001

    // ─── READ HELPERS ────────────────────────────────────────────────

    fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun currentUserId(ctx: Context): Int =
        prefs(ctx).getInt("user_id", -1)

    fun isLoggedIn(ctx: Context): Boolean =
        prefs(ctx).getString("auth_token", "").orEmpty().isNotEmpty()

    // Setup is scoped per user_id — different users get fresh setup state
    private fun setupUserId(ctx: Context): Int =
        prefs(ctx).getInt(KEY_SETUP_USER, -1)

    fun isProfileDone(ctx: Context)  = prefs(ctx).getBoolean(KEY_STEP_PROFILE, false)
    fun isGuardianDone(ctx: Context) = prefs(ctx).getBoolean(KEY_STEP_GUARDIAN, false)
    fun isWhatsappDone(ctx: Context) = prefs(ctx).getBoolean(KEY_STEP_WHATSAPP, false)
    fun isVoiceDone(ctx: Context)    = prefs(ctx).getBoolean(KEY_STEP_VOICE, false)

    fun isFullySetup(ctx: Context): Boolean =
        isLoggedIn(ctx) &&
        isProfileDone(ctx) &&
        isGuardianDone(ctx) &&
        isWhatsappDone(ctx) &&
        isVoiceDone(ctx)

    // ─── MARK STEPS ─────────────────────────────────────────────────

    fun markProfileDone(ctx: Context) {
        prefs(ctx).edit().putBoolean(KEY_STEP_PROFILE, true).apply()
    }

    fun markGuardianDone(ctx: Context) {
        prefs(ctx).edit().putBoolean(KEY_STEP_GUARDIAN, true).apply()
    }

    fun markWhatsappDone(ctx: Context) {
        prefs(ctx).edit().putBoolean(KEY_STEP_WHATSAPP, true).apply()
    }

    fun markVoiceDone(ctx: Context) {
        val uid = currentUserId(ctx)
        prefs(ctx).edit()
            .putBoolean(KEY_STEP_VOICE, true)
            .putInt(KEY_SETUP_USER, uid)
            .apply()
    }

    // ─── NEW USER LOGIN — clear previous user's data ─────────────────
    /**
     * Call this IMMEDIATELY after a successful login.
     * If the logged-in userId differs from the last setup user,
     * clear ALL user-specific data so no cross-contamination occurs.
     */
    fun onUserLogin(ctx: Context, newUserId: Int, newEmail: String) {
        val p = prefs(ctx)
        val lastUserId = p.getInt(KEY_SETUP_USER, -1)

        if (lastUserId != newUserId && lastUserId != -1) {
            // Different user — CLEAR all personal data from previous session
            android.util.Log.i("SetupManager", "New user $newUserId, clearing data from user $lastUserId")
            p.edit()
                // Clear setup steps
                .putBoolean(KEY_STEP_PROFILE, false)
                .putBoolean(KEY_STEP_GUARDIAN, false)
                .putBoolean(KEY_STEP_WHATSAPP, false)
                .putBoolean(KEY_STEP_VOICE, false)
                // Clear personal data
                .remove("user_name")
                .remove("user_last_name")
                .remove("user_dob")
                .remove("user_age")
                .remove("user_address")
                .remove("user_photo")
                .remove("guardian_phone")
                .remove("guardian_phone_2")
                .remove("guardian_name")
                // Keep server_url and auth_token (set by new login)
                .putInt(KEY_SETUP_USER, newUserId)
                .putInt("user_id", newUserId)
                .putString("user_email", newEmail)
                .apply()
        } else if (lastUserId == -1) {
            // First ever login — mark the user
            p.edit().putInt(KEY_SETUP_USER, newUserId).apply()
        }
    }

    // ─── NEXT STEP TO COMPLETE ───────────────────────────────────────

    fun nextPendingStep(ctx: Context): SetupStep {
        if (!isLoggedIn(ctx))    return SetupStep.LOGIN
        if (!isProfileDone(ctx)) return SetupStep.PROFILE
        if (!isGuardianDone(ctx))return SetupStep.GUARDIAN
        if (!isWhatsappDone(ctx))return SetupStep.WHATSAPP
        if (!isVoiceDone(ctx))   return SetupStep.VOICE
        return SetupStep.COMPLETE
    }

    // ─── GATE CHECK — call this before allowing any feature ──────────
    /**
     * Returns true if the user can access the feature.
     * If not, shows a notification + toast telling them what to complete.
     */
    fun checkSetup(ctx: Context, requiredStep: SetupStep? = null): Boolean {
        val next = nextPendingStep(ctx)
        if (next == SetupStep.COMPLETE) return true

        // If requiredStep is specified, check if we at least reached it
        if (requiredStep != null) {
            val stepsInOrder = listOf(
                SetupStep.LOGIN, SetupStep.PROFILE, SetupStep.GUARDIAN,
                SetupStep.WHATSAPP, SetupStep.VOICE, SetupStep.COMPLETE
            )
            val nextIdx = stepsInOrder.indexOf(next)
            val reqIdx  = stepsInOrder.indexOf(requiredStep)
            if (nextIdx > reqIdx) return true // already past this step
        }

        // Show setup reminder notification
        showSetupNotification(ctx, next)
        return false
    }

    private fun showSetupNotification(ctx: Context, pendingStep: SetupStep) {
        val (title, msg) = when (pendingStep) {
            SetupStep.LOGIN    -> "Login Required" to "Please login to use Shakti Alert."
            SetupStep.PROFILE  -> "Complete Your Profile" to "Step 1/4: Fill your name & date of birth in Profile."
            SetupStep.GUARDIAN -> "Add Guardian" to "Step 2/4: Add at least one guardian phone number."
            SetupStep.WHATSAPP -> "Connect WhatsApp" to "Step 3/4: Scan WhatsApp QR code to enable alerts."
            SetupStep.VOICE    -> "Voice Setup" to "Step 4/4: Record your voice keyword to activate protection."
            SetupStep.COMPLETE -> return
        }

        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create channel
        val channel = NotificationChannel(
            NOTIF_CHANNEL, "Setup Reminders", NotificationManager.IMPORTANCE_HIGH
        ).apply { description = "Shakti Alert setup progress" }
        nm.createNotificationChannel(channel)

        // Intent to open app
        val intent = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)
        val pi = PendingIntent.getActivity(
            ctx, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(ctx, NOTIF_CHANNEL)
            .setSmallIcon(R.drawable.ic_emergency)
            .setContentTitle("⚠️ Setup Required: $title")
            .setContentText(msg)
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                "$msg\n\nComplete all setup steps to enable full protection."
            ))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setColor(android.graphics.Color.RED)
            .build()

        nm.notify(NOTIF_ID, notif)
    }

    enum class SetupStep {
        LOGIN, PROFILE, GUARDIAN, WHATSAPP, VOICE, COMPLETE
    }
}
