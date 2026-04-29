import { useEffect, useState, useRef } from 'react'

export default function VoiceSystem(){
  const [status, setStatus] = useState('Idle')
  const [listening, setListening] = useState(false)
  const [pendingAlert, setPendingAlert] = useState(false)
  const [detectedText, setDetectedText] = useState('')
  const recogRef = { current: null }
  const alertSentRef = useRef(false)
  const lastAlertTimeRef = useRef(0)

  const speak = (msg)=>{
    try{
      const u = new SpeechSynthesisUtterance(msg)
      u.rate=1; u.pitch=1
      speechSynthesis.speak(u)
    }catch(e){console.warn('TTS failed', e)}
  }

  // Initialize only when user explicitly enables listening
  useEffect(()=>{
    if(!listening) return

    const Recognition = window.SpeechRecognition || window.webkitSpeechRecognition
    if(!Recognition) {
      setStatus('SpeechRecognition unsupported in this browser')
      setListening(false)
      return
    }

    const r = new Recognition()
    recogRef.current = r
    r.continuous = true
    r.lang = 'en-IN'
    let lastDetectionTime = 0

    r.onresult = (ev)=>{
      const transcript = ev.results[ev.results.length-1][0].transcript.toLowerCase()
      console.log('heard:', transcript)
      
      // Debounce: ignore detections within 2 seconds of last one
      const now = Date.now()
      if(now - lastDetectionTime < 2000) {
        console.log('Ignored duplicate detection within 2s')
        return
      }
      
      // Only flag detection if no pending alert and not already sent
      if(transcript.includes('help') && !pendingAlert && !alertSentRef.current){
        lastDetectionTime = now
        setDetectedText(transcript)
        setStatus('Help detected — please confirm to send alert')
        setPendingAlert(true)
        // stop listening to avoid repeated detections
        try{ r.stop() }catch(e){}
        setListening(false)
        speak('Help detected. Confirm to send alert or cancel to dismiss.')
      }
    }

    r.onerror = (e)=>{
      console.log('rec error',e)
      setStatus('Recognition error')
      setListening(false)
    }

    try{
      r.start()
      setStatus('Listening for wake word (say "help")')
      speak('Voice alerts enabled. Say help to start emergency flow.')
    }catch(e){
      console.warn('Could not start recognition', e)
      setStatus('Could not start recognition')
      setListening(false)
    }

    return ()=>{
      try{ r.stop() }catch(e){}
      recogRef.current = null
    }
  },[listening, pendingAlert])

  // When user confirms, call backend endpoints responsibly
  const confirmAlert = async ()=>{
    // GUARD: prevent sending alert twice
    if(alertSentRef.current) {
      console.warn('Alert already sent, ignoring duplicate confirm')
      return
    }

    // GUARD: Global minimum 20 seconds between alerts (matches backend MIN_ALERT_GAP)
    const now = Date.now()
    const timeSinceLastAlert = now - lastAlertTimeRef.current
    const MIN_ALERT_GAP_MS = 20000  // 20 seconds = matches backend MIN_ALERT_GAP
    
    if(timeSinceLastAlert < MIN_ALERT_GAP_MS) {
      const secondsToWait = Math.ceil((MIN_ALERT_GAP_MS - timeSinceLastAlert) / 1000)
      console.warn(`Too soon to send another alert (need ${secondsToWait}s more)`)
      setStatus(`⏳ System cooldown. Wait ${secondsToWait} seconds before next alert.`)
      speak(`Please wait ${secondsToWait} seconds before sending another alert.`)
      return
    }

    // Mark as sent to prevent duplicates
    alertSentRef.current = true
    lastAlertTimeRef.current = now

    setPendingAlert(false)
    setStatus('🔒 Sending alert to guardians (DO NOT INTERRUPT)...')
    speak('Sending alert to guardians now')
    const base = (import.meta.env.VITE_API_BASE||'http://127.0.0.1:5000')
    try{
      // Send to backend with idempotency token
      const alertToken = `${Date.now()}-${Math.random()}`
      console.log('📤 Sending alert with token:', alertToken)
      
      const response = await fetch(base + '/trigger_voice_alert', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ 
          alert_token: alertToken,
          trigger_type: 'voice_help'
        })
      }).catch((e)=>{
        console.error('Fetch failed', e)
        return null
      })

      if(response && response.ok) {
        const data = await response.json()
        console.log('✅ Server response:', data)
        speak('Alert sent to guardians.')
        setStatus('✅ Alert sent successfully! System on 20-second cooldown.')
      } else if(response && response.status === 429) {
        // Rate limited on server
        const data = await response.json()
        console.warn('⚠️ Server rate limited:', data)
        speak('Alert system is on cooldown. Try again in a moment.')
        setStatus(`⏳ Alert system cooling down: ${data.message}`)
        alertSentRef.current = false  // Allow retry after cooldown
      } else {
        console.error('Server error:', response?.status)
        speak('Failed to send alert')
        setStatus('❌ Failed to send alert. Try again.')
        alertSentRef.current = false  // Allow retry on error
      }
    }catch(e){
      console.error('Alert failed', e)
      setStatus('❌ Failed to send alert. Try again.')
      speak('Failed to send alert')
      alertSentRef.current = false  // Allow retry on error
    } finally {
      // Reset flag after MIN_ALERT_GAP to allow another alert
      setTimeout(()=>{
        alertSentRef.current = false
        setStatus('Ready for next alert after cooldown')
      }, MIN_ALERT_GAP_MS)
    }
  }

  const cancelAlert = ()=>{
    alertSentRef.current = false
    setPendingAlert(false)
    setDetectedText('')
    setStatus('Alert cancelled')
    speak('Alert cancelled')
  }

  return (
    <div className="text-center text-green-300 font-semibold mt-4">
      <div>{status}</div>
      <div className="mt-2">
        <button className="px-3 py-1 bg-green-600 rounded mr-2" onClick={()=> setListening(s=>!s)}>
          {listening ? 'Disable Voice Alerts' : 'Enable Voice Alerts'}
        </button>
      </div>

      {pendingAlert && (
        <div className="mt-3">
          <div className="mb-2">Detected: "{detectedText}"</div>
          <button className="px-3 py-1 bg-red-600 rounded mr-2" onClick={confirmAlert}>Confirm & Send Alert</button>
          <button className="px-3 py-1 bg-gray-600 rounded" onClick={cancelAlert}>Cancel</button>
        </div>
      )}
    </div>
  )
}
