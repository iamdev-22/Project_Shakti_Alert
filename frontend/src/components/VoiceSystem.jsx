import { useEffect, useState } from 'react'

export default function VoiceSystem(){
  const [status, setStatus] = useState('Listening...')

  useEffect(()=>{
    const speak = (msg)=>{
      const u = new SpeechSynthesisUtterance(msg)
      u.rate=1; u.pitch=1
      speechSynthesis.speak(u)
    }
    speak('Shakti system activated. Say help to start the emergency flow.')

    const Recognition = window.SpeechRecognition || window.webkitSpeechRecognition
    if(!Recognition) {
      setStatus('SpeechRecognition unsupported in this browser')
      return
    }
    const r = new Recognition()
    r.continuous = true
    r.lang = 'en-IN'
    r.onresult = (ev)=>{
      const transcript = ev.results[ev.results.length-1][0].transcript.toLowerCase()
      console.log('heard:', transcript)
      if(transcript.includes('help')){
        setStatus('Help detected — recording...')
        speak('Audio recording started. Switching to video in five seconds.')
        // trigger backend API endpoints (user should run flask backend)
        fetch((import.meta.env.VITE_API_BASE||'http://127.0.0.1:5000') + '/start_audio').catch(()=>{})
        setTimeout(()=>{
          fetch((import.meta.env.VITE_API_BASE||'http://127.0.0.1:5000') + '/start_video').catch(()=>{})
          speak('Alert sent to guardian.')
        },5000)
      }
    }
    r.onerror = (e)=> console.log('rec error',e)
    r.start()
    return ()=> r.stop()
  },[])

  return <div className="text-center text-green-300 font-semibold mt-4">{status}</div>
}
