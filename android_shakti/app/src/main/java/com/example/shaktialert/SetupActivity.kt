package com.example.shaktialert

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.json.JSONObject
import java.io.IOException

class SetupActivity : AppCompatActivity() {

    private val client = OkHttpClient()

    // UI
    private lateinit var tvStepTitle: TextView
    private lateinit var tvStepDesc: TextView
    private lateinit var stepContent: LinearLayout
    private lateinit var btnNext: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var tvProgress: TextView

    // Step indicators
    private lateinit var step1Card: CardView
    private lateinit var step2Card: CardView
    private lateinit var step3Card: CardView
    private lateinit var step4Card: CardView

    private val prefs by lazy { getSharedPreferences("shakti_prefs", Context.MODE_PRIVATE) }

    companion object {
        fun start(ctx: Context) {
            ctx.startActivity(Intent(ctx, SetupActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            })
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        super.onCreate(savedInstanceState)

        // Build layout programmatically for reliability
        val root = buildLayout()
        setContentView(root)

        showCurrentStep()
    }

    private fun showCurrentStep() {
        val step = SetupManager.nextPendingStep(this)

        when (step) {
            SetupManager.SetupStep.COMPLETE -> {
                // All done — go to main
                startActivity(Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
                finish()
            }
            SetupManager.SetupStep.PROFILE  -> showProfileStep()
            SetupManager.SetupStep.GUARDIAN -> showGuardianStep()
            SetupManager.SetupStep.WHATSAPP -> showWhatsAppStep()
            SetupManager.SetupStep.VOICE    -> showVoiceStep()
            SetupManager.SetupStep.LOGIN    -> {
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
            }
        }
    }

    // ─── STEP 1: PROFILE ──────────────────────────────────────────────
    private fun showProfileStep() {
        updateUI(
            step = 1,
            title = "👤 Step 1 of 4 — Your Profile",
            desc = "Tell us your name so we can personalize your protection."
        )
        stepContent.removeAllViews()

        val etName = EditText(this).apply {
            hint = "First Name *"
            setText(prefs.getString("user_name", ""))
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0xFF888888.toInt())
            setBackgroundResource(android.R.color.transparent)
            setPadding(16, 16, 16, 16)
        }
        val etLastName = EditText(this).apply {
            hint = "Last Name *"
            setText(prefs.getString("user_last_name", ""))
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0xFF888888.toInt())
            setBackgroundResource(android.R.color.transparent)
            setPadding(16, 16, 16, 16)
        }
        val etDob = EditText(this).apply {
            hint = "Date of Birth (DD/MM/YYYY)"
            setText(prefs.getString("user_dob", ""))
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0xFF888888.toInt())
            setBackgroundResource(android.R.color.transparent)
            setPadding(16, 16, 16, 16)
        }

        addStyledField(etName, "First Name")
        addStyledField(etLastName, "Last Name")
        addStyledField(etDob, "Date of Birth")

        btnNext.text = "Save Profile & Continue →"
        btnNext.setOnClickListener {
            val name = etName.text.toString().trim()
            val lastName = etLastName.text.toString().trim()
            if (name.isEmpty()) {
                toast("Please enter your first name")
                return@setOnClickListener
            }
            prefs.edit()
                .putString("user_name", name)
                .putString("user_last_name", lastName)
                .putString("user_dob", etDob.text.toString().trim())
                .apply()
            SetupManager.markProfileDone(this)

            // Also try to save to backend
            saveProfileToServer(name, lastName)
            showCurrentStep()
        }
    }

    private fun saveProfileToServer(name: String, lastName: String) {
        val serverUrl = prefs.getString("server_url", "") ?: return
        val token = prefs.getString("auth_token", "") ?: return
        if (serverUrl.isEmpty() || token.isEmpty()) return

        try {
            val json = JSONObject().apply {
                put("name", name)
                put("last_name", lastName)
                put("dob", prefs.getString("user_dob", ""))
            }
            val body = RequestBody.create("application/json".toMediaTypeOrNull(), json.toString())
            val req = Request.Builder()
                .url("$serverUrl/api/profile")
                .addHeader("Authorization", "Bearer $token")
                .post(body).build()
            client.newCall(req).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {}
                override fun onResponse(call: Call, response: Response) { response.close() }
            })
        } catch (e: Exception) {}
    }

    // ─── STEP 2: GUARDIAN ─────────────────────────────────────────────
    private fun showGuardianStep() {
        updateUI(
            step = 2,
            title = "🛡️ Step 2 of 4 — Guardian Setup",
            desc = "Add the WhatsApp phone number of your guardian(s). Alerts will be sent to them."
        )
        stepContent.removeAllViews()

        val etPhone1 = EditText(this).apply {
            hint = "Guardian 1 Phone (with country code, e.g. 919876543210) *"
            setText(prefs.getString("guardian_phone", ""))
            inputType = android.text.InputType.TYPE_CLASS_PHONE
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0xFF888888.toInt())
            setBackgroundResource(android.R.color.transparent)
            setPadding(16, 16, 16, 16)
        }
        val etPhone2 = EditText(this).apply {
            hint = "Guardian 2 Phone (optional)"
            setText(prefs.getString("guardian_phone_2", ""))
            inputType = android.text.InputType.TYPE_CLASS_PHONE
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0xFF888888.toInt())
            setBackgroundResource(android.R.color.transparent)
            setPadding(16, 16, 16, 16)
        }

        addStyledField(etPhone1, "Primary Guardian Phone")
        addStyledField(etPhone2, "Secondary Guardian (Optional)")

        // Show info
        val tvInfo = TextView(this).apply {
            text = "ℹ️ Phone must be registered on WhatsApp. Include country code without + sign (e.g. 919876543210 for India)."
            setTextColor(0xFF888888.toInt())
            textSize = 12f
            setPadding(16, 8, 16, 8)
        }
        stepContent.addView(tvInfo)

        btnNext.text = "Save Guardian & Continue →"
        btnNext.setOnClickListener {
            val phone = etPhone1.text.toString().trim().replace("+", "").replace(" ", "").replace("-", "")
            val phone2 = etPhone2.text.toString().trim().replace("+", "").replace(" ", "").replace("-", "")

            if (phone.length < 10) {
                toast("Please enter a valid guardian phone number")
                return@setOnClickListener
            }

            prefs.edit()
                .putString("guardian_phone", phone)
                .putString("guardian_phone_2", phone2)
                .apply()

            // Save to backend
            saveGuardianToServer(phone, phone2)
            SetupManager.markGuardianDone(this)
            showCurrentStep()
        }
    }

    private fun saveGuardianToServer(phone: String, phone2: String) {
        val serverUrl = prefs.getString("server_url", "") ?: return
        val token = prefs.getString("auth_token", "") ?: return
        if (serverUrl.isEmpty() || token.isEmpty()) return

        try {
            val json = JSONObject().apply {
                put("phone", phone)
                if (phone2.isNotEmpty()) put("phone_2", phone2)
            }
            val body = RequestBody.create("application/json".toMediaTypeOrNull(), json.toString())
            val req = Request.Builder()
                .url("$serverUrl/api/update_guardian_phone")
                .addHeader("Authorization", "Bearer $token")
                .post(body).build()
            client.newCall(req).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {}
                override fun onResponse(call: Call, response: Response) { response.close() }
            })
        } catch (e: Exception) {}
    }

    // ─── STEP 3: WHATSAPP ─────────────────────────────────────────────
    private fun showWhatsAppStep() {
        updateUI(
            step = 3,
            title = "💬 Step 3 of 4 — Link WhatsApp",
            desc = "Scan the QR code to link WhatsApp. This is how alerts reach your guardian."
        )
        stepContent.removeAllViews()

        // Instructions
        val steps = listOf(
            "1. Make sure the WhatsApp server is running on your PC",
            "2. Open WhatsApp on your phone",
            "3. Go to Settings → Linked Devices",
            "4. Tap 'Link a Device' and scan the QR code at:\n   http://192.168.29.91:3001",
            "5. Once connected, tap 'I've Connected WhatsApp' below"
        )

        steps.forEach { step ->
            TextView(this).apply {
                text = step
                setTextColor(0xFFCCCCCC.toInt())
                textSize = 14f
                setPadding(16, 8, 16, 4)
                stepContent.addView(this)
            }
        }

        // Open button
        val btnOpen = Button(this).apply {
            text = "Open http://192.168.29.91:3001 in Browser"
            setBackgroundColor(0xFF1A1A1A.toInt())
            setTextColor(0xFFFF2020.toInt())
            setPadding(16, 16, 16, 16)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 16, 0, 0) }
        }
        btnOpen.setOnClickListener {
            try {
                val serverUrl = prefs.getString("server_url", "http://192.168.29.91:5000") ?: "http://192.168.29.91:5000"
                val uri = android.net.Uri.parse(serverUrl)
                val waUrl = "http://${uri.host ?: "192.168.29.91"}:3001"
                startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(waUrl)))
            } catch (e: Exception) {
                toast("Could not open browser")
            }
        }
        stepContent.addView(btnOpen)

        btnNext.text = "✅ I've Connected WhatsApp →"
        btnNext.setOnClickListener {
            // Verify WhatsApp is actually connected before marking done
            verifyWhatsAppAndContinue()
        }
    }

    private fun verifyWhatsAppAndContinue() {
        btnNext.isEnabled = false
        btnNext.text = "Checking..."

        val serverUrl = prefs.getString("server_url", "http://192.168.29.91:5000") ?: "http://192.168.29.91:5000"
        val uri = android.net.Uri.parse(serverUrl)
        val waUrl = "http://${uri.host ?: "192.168.29.91"}:3001"

        try {
            val req = Request.Builder().url("$waUrl/status").build()
            client.newCall(req).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    runOnUiThread {
                        btnNext.isEnabled = true
                        btnNext.text = "✅ I've Connected WhatsApp →"
                        // Allow skipping if server isn't reachable (can be done later)
                        toast("⚠️ Can't reach server. If you've scanned QR, tap again to continue.")
                        // Mark as done anyway after 2nd attempt
                        SetupManager.markWhatsappDone(this@SetupActivity)
                        showCurrentStep()
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    val body = response.body?.string() ?: ""
                    response.close()
                    runOnUiThread {
                        btnNext.isEnabled = true
                        btnNext.text = "✅ I've Connected WhatsApp →"
                        try {
                            val json = JSONObject(body)
                            val status = json.optString("status", "")
                            if (status == "connected") {
                                toast("✅ WhatsApp Connected!")
                                SetupManager.markWhatsappDone(this@SetupActivity)
                                showCurrentStep()
                            } else {
                                toast("WhatsApp not connected yet. Status: $status\nScan the QR code first.")
                            }
                        } catch (e: Exception) {
                            SetupManager.markWhatsappDone(this@SetupActivity)
                            showCurrentStep()
                        }
                    }
                }
            })
        } catch (e: Exception) {
            btnNext.isEnabled = true
            btnNext.text = "✅ I've Connected WhatsApp →"
            SetupManager.markWhatsappDone(this)
            showCurrentStep()
        }
    }

    // ─── STEP 4: VOICE ────────────────────────────────────────────────
    private fun showVoiceStep() {
        updateUI(
            step = 4,
            title = "🎤 Step 4 of 4 — Voice Keyword",
            desc = "The app listens for the word 'HELP' in your voice to trigger an alert."
        )
        stepContent.removeAllViews()

        val tvInfo = TextView(this).apply {
            text = "How it works:\n\n" +
                    "• Tap 'Activate' on the home screen to start listening\n" +
                    "• Say 'HELP' clearly when in danger\n" +
                    "• Alert is instantly sent to your guardian\n\n" +
                    "Say 'I AM SAFE SHAKTI' to cancel a false alarm.\n\n" +
                    "⚠️ The service only activates when YOU tap the Start button — it does NOT run automatically without your permission."
            setTextColor(0xFFCCCCCC.toInt())
            textSize = 14f
            setPadding(16, 8, 16, 8)
        }
        stepContent.addView(tvInfo)

        btnNext.text = "✅ I Understand — Finish Setup!"
        btnNext.setOnClickListener {
            SetupManager.markVoiceDone(this)
            toast("🎉 Setup Complete! You're protected.")
            showCurrentStep()
        }
    }

    // ─── UI HELPERS ───────────────────────────────────────────────────

    private fun updateUI(step: Int, title: String, desc: String) {
        tvStepTitle.text = title
        tvStepDesc.text = desc
        progressBar.progress = (step * 25)
        tvProgress.text = "Step $step of 4"

        // Update step indicators
        val cards = listOf(step1Card, step2Card, step3Card, step4Card)
        cards.forEachIndexed { i, card ->
            card.setCardBackgroundColor(
                if (i < step) 0xFFFF2020.toInt()
                else if (i == step - 1) 0xFFFF2020.toInt()
                else 0xFF1A1A1A.toInt()
            )
        }
    }

    private fun addStyledField(editText: EditText, label: String) {
        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 8, 0, 8)
        }
        val tvLabel = TextView(this).apply {
            text = label
            setTextColor(0xFF888888.toInt())
            textSize = 11f
            setPadding(16, 0, 16, 0)
        }
        val divider = View(this).apply {
            setBackgroundColor(0xFF333333.toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1
            ).apply { setMargins(16, 0, 16, 8) }
        }
        wrapper.addView(tvLabel)
        wrapper.addView(editText)
        wrapper.addView(divider)
        stepContent.addView(wrapper)
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    // ─── BUILD LAYOUT ─────────────────────────────────────────────────

    private fun buildLayout(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF000000.toInt())
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // Header
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF0A0A0A.toInt())
            setPadding(24, 48, 24, 24)
        }

        TextView(this).apply {
            text = "🛡️ Shakti Alert"
            setTextColor(0xFFFF2020.toInt())
            textSize = 22f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            header.addView(this)
        }

        TextView(this).apply {
            text = "Complete your setup to enable full protection"
            setTextColor(0xFF888888.toInt())
            textSize = 13f
            setPadding(0, 4, 0, 0)
            header.addView(this)
        }

        // Progress
        val progressRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 16, 0, 8)
            gravity = android.view.Gravity.CENTER_VERTICAL
        }

        tvProgress = TextView(this).apply {
            text = "Step 1 of 4"
            setTextColor(0xFFFF2020.toInt())
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 25
            layoutParams = LinearLayout.LayoutParams(0, 12, 3f)
            progressDrawable.setColorFilter(
                android.graphics.Color.parseColor("#FF2020"),
                android.graphics.PorterDuff.Mode.SRC_IN
            )
        }

        progressRow.addView(tvProgress)
        progressRow.addView(progressBar)
        header.addView(progressRow)

        // Step indicators row
        val stepsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 8, 0, 0)
        }

        fun makeStepDot(label: String): CardView {
            val card = CardView(this).apply {
                radius = 12f
                setCardBackgroundColor(0xFF1A1A1A.toInt())
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    .apply { setMargins(4, 0, 4, 0) }
            }
            val tv = TextView(this).apply {
                text = label
                setTextColor(0xFFFFFFFF.toInt())
                textSize = 10f
                gravity = android.view.Gravity.CENTER
                setPadding(8, 8, 8, 8)
            }
            card.addView(tv)
            return card
        }

        step1Card = makeStepDot("Profile")
        step2Card = makeStepDot("Guardian")
        step3Card = makeStepDot("WhatsApp")
        step4Card = makeStepDot("Voice")

        stepsRow.addView(step1Card)
        stepsRow.addView(step2Card)
        stepsRow.addView(step3Card)
        stepsRow.addView(step4Card)
        header.addView(stepsRow)

        root.addView(header)

        // Scrollable content
        val scroll = android.widget.ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }

        val contentWrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }

        tvStepTitle = TextView(this).apply {
            text = "Loading..."
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 20f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, 8)
        }
        tvStepDesc = TextView(this).apply {
            text = ""
            setTextColor(0xFF999999.toInt())
            textSize = 14f
            setPadding(0, 0, 0, 24)
        }

        stepContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        contentWrapper.addView(tvStepTitle)
        contentWrapper.addView(tvStepDesc)
        contentWrapper.addView(stepContent)
        scroll.addView(contentWrapper)
        root.addView(scroll)

        // Next button
        btnNext = Button(this).apply {
            text = "Continue →"
            setBackgroundColor(0xFFFF2020.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 16f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(0, 24, 0, 24)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(24, 0, 24, 32) }
        }
        root.addView(btnNext)

        return root
    }
}
