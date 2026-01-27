package com.example.shaktialert

import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import kotlin.math.cos
import kotlin.math.sin

class HomeFragment : Fragment() {

    private lateinit var tvStatus: TextView
    private lateinit var dotWaveContainer: FrameLayout
    private val dots = mutableListOf<View>()
    private var waveAnimator: ValueAnimator? = null
    private var isAnimating = false
    
    private val statusReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "com.shakti.alert.STATUS" -> {
                    val status = intent.getStringExtra("status") ?: ""
                    tvStatus.text = "Status: $status"
                    updateVoiceAnimation(status)
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        tvStatus = view.findViewById(R.id.tvStatus)
        dotWaveContainer = view.findViewById(R.id.dotWaveContainer)
        
        val btnStart = view.findViewById<View>(R.id.btnStartService)
        val btnStop = view.findViewById<View>(R.id.btnStopService)
        val btnTrigger = view.findViewById<View>(R.id.btnTrigger)
        val btnEnroll = view.findViewById<View>(R.id.btnEnroll)
        
        // Create dots after layout is complete
        dotWaveContainer.post {
            createDotCircle()
            startWaveAnimation()
        }

        btnStart.setOnClickListener {
            // Start main alert service
            val svc = Intent(requireContext(), AlertService::class.java)
            svc.action = "START_LISTENING"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                requireContext().startForegroundService(svc)
            } else {
                requireContext().startService(svc)
            }
            
            // ✅ NEW: Start background service to keep app alive
            ShaktiBackgroundService.start(requireContext())
            
            tvStatus.text = "Status: Active & Listening"
            Toast.makeText(context, "Shakti Alert Activated", Toast.LENGTH_SHORT).show()
            updateVoiceAnimation("active")
        }

        btnStop.setOnClickListener {
            // Stop main alert service
            val svc = Intent(requireContext(), AlertService::class.java)
            requireContext().stopService(svc)
            
            // ✅ Stop background service
            ShaktiBackgroundService.stop(requireContext())
            
            tvStatus.text = "Status: System Stopped"
            Toast.makeText(context, "Shakti Alert Stopped", Toast.LENGTH_SHORT).show()
            updateVoiceAnimation("stopped")
        }

        btnTrigger.setOnClickListener {
            val svc = Intent(requireContext(), AlertService::class.java)
            svc.putExtra("manual_trigger", true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                requireContext().startForegroundService(svc)
            } else {
                requireContext().startService(svc)
            }
            Toast.makeText(context, "SOS TRIGGERED!", Toast.LENGTH_LONG).show()
            updateVoiceAnimation("emergency")
        }
        
        btnEnroll.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Voice Setup")
                .setMessage("To stop the alarm with your voice, please record your safe phrase:\n\n'I AM SAFE SHAKTI'")
                .setPositiveButton("Record") { _, _ ->
                    Toast.makeText(context, "Recording started... (Simulated)", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
        
        // AI Help Button
        val btnAiHelp = view.findViewById<View>(R.id.btnAiHelp)
        btnAiHelp.setOnClickListener {
            val intent = Intent(requireContext(), AiHelpActivity::class.java)
            startActivity(intent)
        }

        return view
    }
    
    private fun createDotCircle() {
        val numDots = 40 // Number of dots in the circle
        val radius = 90f // Radius of the circle in dp
        val dotSize = 8 // Size of each dot in dp
        
        val centerX = dotWaveContainer.width / 2f
        val centerY = dotWaveContainer.height / 2f
        
        dots.clear()
        dotWaveContainer.removeAllViews()
        
        for (i in 0 until numDots) {
            val angle = (i * 360f / numDots) * Math.PI / 180f
            
            val dot = View(requireContext()).apply {
                layoutParams = FrameLayout.LayoutParams(
                    (dotSize * resources.displayMetrics.density).toInt(),
                    (dotSize * resources.displayMetrics.density).toInt()
                )
                setBackgroundResource(R.drawable.dot_white)
                alpha = 0.6f
                scaleX = 0.8f
                scaleY = 0.8f
                
                // Position dot on circle
                val x = centerX + (radius * resources.displayMetrics.density * cos(angle)).toFloat()
                val y = centerY + (radius * resources.displayMetrics.density * sin(angle)).toFloat()
                
                translationX = x - (dotSize * resources.displayMetrics.density / 2)
                translationY = y - (dotSize * resources.displayMetrics.density / 2)
            }
            
            dotWaveContainer.addView(dot)
            dots.add(dot)
        }
    }
    
    private fun startWaveAnimation() {
        if (isAnimating) return
        
        waveAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 2000 // 2 seconds for one complete wave
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            
            addUpdateListener { animator ->
                val progress = animator.animatedValue as Float
                
                dots.forEachIndexed { index, dot ->
                    // Calculate wave offset for this dot
                    val dotAngle = (index * 360f / dots.size)
                    val wavePhase = (progress + dotAngle) % 360f
                    
                    // Calculate scale based on sine wave
                    val waveValue = sin(Math.toRadians(wavePhase.toDouble())).toFloat()
                    val scale = 0.6f + (waveValue + 1f) / 2f * 0.8f // Scale between 0.6 and 1.4
                    
                    // Calculate alpha based on wave
                    val alpha = 0.3f + (waveValue + 1f) / 2f * 0.7f // Alpha between 0.3 and 1.0
                    
                    dot.scaleX = scale
                    dot.scaleY = scale
                    dot.alpha = alpha
                }
            }
            
            start()
        }
        
        isAnimating = true
    }
    
    private fun stopWaveAnimation() {
        waveAnimator?.cancel()
        waveAnimator = null
        
        // Reset all dots to default state
        dots.forEach { dot ->
            dot.scaleX = 0.8f
            dot.scaleY = 0.8f
            dot.alpha = 0.6f
        }
        
        isAnimating = false
    }
    
    private fun startFastWave() {
        waveAnimator?.cancel()
        
        waveAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 1000 // Faster wave for active state
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            
            addUpdateListener { animator ->
                val progress = animator.animatedValue as Float
                
                dots.forEachIndexed { index, dot ->
                    val dotAngle = (index * 360f / dots.size)
                    val wavePhase = (progress + dotAngle) % 360f
                    val waveValue = sin(Math.toRadians(wavePhase.toDouble())).toFloat()
                    
                    val scale = 0.5f + (waveValue + 1f) / 2f * 1.0f // More dramatic scale
                    val alpha = 0.4f + (waveValue + 1f) / 2f * 0.6f
                    
                    dot.scaleX = scale
                    dot.scaleY = scale
                    dot.alpha = alpha
                }
            }
            
            start()
        }
    }
    
    private fun updateVoiceAnimation(status: String) {
        try {
            val s = status.toLowerCase()
            when {
                s.contains("help") || s.contains("detected") || s.contains("emergency") -> {
                    if (!isAnimating) startWaveAnimation()
                    else startFastWave() // Faster wave for emergency
                }
                s.contains("listening") || s.contains("active") -> {
                    if (!isAnimating) startWaveAnimation()
                    else startFastWave() // Active wave
                }
                s.contains("stopped") -> {
                    stopWaveAnimation()
                }
                else -> {
                    if (!isAnimating) startWaveAnimation()
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("HomeFragment", "Failed to update animation: ${e.message}")
        }
    }

    override fun onResume() {
        super.onResume()
        val filter = android.content.IntentFilter().apply {
            addAction("com.shakti.alert.STATUS")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requireContext().registerReceiver(statusReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            requireContext().registerReceiver(statusReceiver, filter)
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            requireContext().unregisterReceiver(statusReceiver)
        } catch (e: Exception) {}
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        // Cleanup animator to prevent memory leaks
        waveAnimator?.cancel()
        waveAnimator = null
        dots.clear()
    }
}
