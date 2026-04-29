package com.shakti.alert.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.*
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.example.shaktialert.R
import io.socket.client.IO
import io.socket.client.Socket
import org.webrtc.*
import java.net.URISyntaxException

/**
 * Foreground Service for Live Streaming
 * This service runs even when the screen is OFF
 * Streams camera + audio to guardians via WebRTC
 */
class LiveStreamService : Service() {

    companion object {
        private const val TAG = "LiveStreamService"
        const val CHANNEL_ID = "LiveStreamChannel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START_STREAM = "START_STREAM"
        const val ACTION_STOP_STREAM = "STOP_STREAM"
        const val EXTRA_SERVER_URL = "SERVER_URL"
        const val EXTRA_USER_ID = "USER_ID"
    }

    private var socket: Socket? = null
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var localVideoTrack: VideoTrack? = null
    private var localAudioTrack: AudioTrack? = null
    private var videoCapturer: CameraVideoCapturer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    
    private var serverUrl: String = "http://localhost:5000"
    private var userId: String = "dev"

    override fun onCreate() {
        super.onCreate()
        
        // Create notification channel
        createNotificationChannel()
        
        // Acquire wake lock to keep CPU running when screen is OFF
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "ShaktiAlert::LiveStreamWakeLock"
        )
        wakeLock?.acquire(10*60*1000L /*10 minutes*/)
        
        // Initialize WebRTC
        initializeWebRTC()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_STREAM -> {
                serverUrl = intent.getStringExtra(EXTRA_SERVER_URL) ?: serverUrl
                userId = intent.getStringExtra(EXTRA_USER_ID) ?: userId
                startForegroundService()
                if (hasStreamPermissions()) {
                    startStreaming()
                } else {
                    updateNotification("❌ Missing camera/mic permission")
                    Log.e(TAG, "Missing permissions for camera/microphone")
                    stopSelf()
                }
            }
            ACTION_STOP_STREAM -> {
                stopStreaming()
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun startForegroundService() {
        val notification = createNotification("🔴 Live Emergency Stream Active")
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun createNotification(contentText: String): Notification {
        val stopIntent = Intent(this, LiveStreamService::class.java).apply {
            action = ACTION_STOP_STREAM
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Shakti Alert - Emergency Stream")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_emergency)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .addAction(R.drawable.ic_stop, "Stop Stream", stopPendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Live Stream Service",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Keeps emergency live stream active"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun initializeWebRTC() {
        // Initialize PeerConnectionFactory
        val options = PeerConnectionFactory.InitializationOptions.builder(this)
            .setEnableInternalTracer(true)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)

        val encoderFactory = DefaultVideoEncoderFactory(
            EglBase.create().eglBaseContext,
            true,
            true
        )
        val decoderFactory = DefaultVideoDecoderFactory(EglBase.create().eglBaseContext)

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .createPeerConnectionFactory()
    }

    private fun startStreaming() {
        try {
            // Connect to signaling server
            connectToSignalingServer()
            
            // Create peer connection
            createPeerConnection()
            
            // Start camera capture (works even with screen OFF)
            startCameraCapture()
            
            // Start audio capture
            startAudioCapture()
            
            // Notify server we're starting stream
            socket?.emit("start_stream", org.json.JSONObject().apply {
                put("user_id", userId)
            })
            
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e(TAG, "Stream failed: ${e.message}")
            updateNotification("❌ Stream Failed: ${e.message}")
        }
    }

    private fun connectToSignalingServer() {
        try {
            socket = IO.socket(serverUrl)
            
            socket?.on(Socket.EVENT_CONNECT) {
                updateNotification("✅ Connected to server")
            }
            
            socket?.on("viewer_joined") { args ->
                val data = args[0] as org.json.JSONObject
                val viewerSid = data.getString("viewer_sid")
                createAndSendOffer(viewerSid)
            }
            
            socket?.on("answer") { args ->
                val data = args[0] as org.json.JSONObject
                val answer = SessionDescription(
                    SessionDescription.Type.ANSWER,
                    data.getJSONObject("answer").getString("sdp")
                )
                peerConnection?.setRemoteDescription(SimpleSdpObserver(), answer)
            }
            
            socket?.on("ice_candidate") { args ->
                val data = args[0] as org.json.JSONObject
                val candidate = IceCandidate(
                    data.getJSONObject("candidate").getString("sdpMid"),
                    data.getJSONObject("candidate").getInt("sdpMLineIndex"),
                    data.getJSONObject("candidate").getString("candidate")
                )
                peerConnection?.addIceCandidate(candidate)
            }
            
            socket?.connect()
            
        } catch (e: URISyntaxException) {
            e.printStackTrace()
        }
    }

    private fun createPeerConnection() {
        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer()
        )

        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
            tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED
        }

        peerConnection = peerConnectionFactory?.createPeerConnection(
            rtcConfig,
            object : PeerConnection.Observer {
                override fun onIceCandidate(candidate: IceCandidate?) {
                    candidate?.let {
                        socket?.emit("ice_candidate", org.json.JSONObject().apply {
                            put("candidate", org.json.JSONObject().apply {
                                put("sdpMid", it.sdpMid)
                                put("sdpMLineIndex", it.sdpMLineIndex)
                                put("candidate", it.sdp)
                            })
                        })
                    }
                }

                override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {
                    when (newState) {
                        PeerConnection.PeerConnectionState.CONNECTED -> 
                            updateNotification("🔴 LIVE - Guardian watching")
                        PeerConnection.PeerConnectionState.DISCONNECTED -> 
                            updateNotification("⚠️ Connection lost")
                        PeerConnection.PeerConnectionState.FAILED -> 
                            updateNotification("❌ Connection failed")
                        else -> {}
                    }
                }

                // Other required overrides...
                override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}
                override fun onIceConnectionChange(p0: PeerConnection.IceConnectionState?) {}
                override fun onIceConnectionReceivingChange(p0: Boolean) {}
                override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}
                override fun onAddStream(p0: MediaStream?) {}
                override fun onRemoveStream(p0: MediaStream?) {}
                override fun onDataChannel(p0: DataChannel?) {}
                override fun onRenegotiationNeeded() {}
                override fun onAddTrack(p0: RtpReceiver?, p1: Array<out MediaStream>?) {}
                override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
            }
        )
    }

    private fun startCameraCapture() {
        val enumerator: CameraEnumerator = if (Camera2Enumerator.isSupported(this)) {
            Camera2Enumerator(this)
        } else {
            Camera1Enumerator(false)
        }

        val deviceNames = enumerator.deviceNames
        val backCameraName = deviceNames.firstOrNull { enumerator.isBackFacing(it) }
            ?: deviceNames.firstOrNull()

        if (backCameraName == null) {
            Log.e(TAG, "No camera device found")
            updateNotification("❌ No camera found")
            return
        }

        try {
            videoCapturer = enumerator.createCapturer(backCameraName, null)

            val surfaceTextureHelper = SurfaceTextureHelper.create(
                "CaptureThread",
                EglBase.create().eglBaseContext
            )

            val videoSource = peerConnectionFactory?.createVideoSource(false)
            videoCapturer?.initialize(surfaceTextureHelper, this, videoSource?.capturerObserver)
            videoCapturer?.startCapture(640, 480, 30)

            localVideoTrack = peerConnectionFactory?.createVideoTrack("video", videoSource)
            peerConnection?.addTrack(localVideoTrack)
        } catch (e: Exception) {
            Log.e(TAG, "Camera start failed: ${e.message}")
            updateNotification("❌ Camera start failed")
        }
    }

    private fun startAudioCapture() {
        if (!hasStreamPermissions()) {
            Log.e(TAG, "Missing mic permission for audio capture")
            return
        }
        val audioConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
        }
        
        val audioSource = peerConnectionFactory?.createAudioSource(audioConstraints)
        localAudioTrack = peerConnectionFactory?.createAudioTrack("audio", audioSource)
        peerConnection?.addTrack(localAudioTrack)
    }

    private fun createAndSendOffer(targetSid: String) {
        peerConnection?.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(sessionDescription: SessionDescription?) {
                peerConnection?.setLocalDescription(SimpleSdpObserver(), sessionDescription)
                
                socket?.emit("offer", org.json.JSONObject().apply {
                    put("target", targetSid)
                    put("offer", org.json.JSONObject().apply {
                        put("type", sessionDescription?.type?.canonicalForm())
                        put("sdp", sessionDescription?.description)
                    })
                })
            }
        }, MediaConstraints())
    }

    private fun stopStreaming() {
        try {
            socket?.emit("stop_stream", org.json.JSONObject().apply {
                put("user_id", userId)
            })
        } catch (e: Exception) {
            Log.e(TAG, "Stop stream emit failed: ${e.message}")
        }
        
        videoCapturer?.stopCapture()
        videoCapturer?.dispose()
        
        localVideoTrack?.dispose()
        localAudioTrack?.dispose()
        
        peerConnection?.close()
        peerConnection?.dispose()
        
        socket?.disconnect()
        socket?.close()
        
        wakeLock?.release()
    }

    private fun updateNotification(text: String) {
        val notification = createNotification(text)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        stopStreaming()
    }

    private fun hasStreamPermissions(): Boolean {
        val cameraOk = ActivityCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        val micOk = ActivityCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        return cameraOk && micOk
    }

    // Simple SDP Observer
    private open class SimpleSdpObserver : SdpObserver {
        override fun onCreateSuccess(sessionDescription: SessionDescription?) {}
        override fun onSetSuccess() {}
        override fun onCreateFailure(error: String?) {}
        override fun onSetFailure(error: String?) {}
    }
}
