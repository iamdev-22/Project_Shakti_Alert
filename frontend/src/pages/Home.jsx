import React, { useState, useEffect, useRef } from "react";
import Spline from "@splinetool/react-spline";
import LiquidEther from "./LiquidEther";

export default function Home() {
    const [status, setStatus] = useState("Idle");
    const [step, setStep] = useState("");
    const [helpDetected, setHelpDetected] = useState(false);
    const [connected, setConnected] = useState(false);
    const BACKEND_URL = "http://127.0.0.1:5000";
    const voiceLock = useRef(false);
    const alertLock = useRef(false);

    const speak = (text) => {
        if (voiceLock.current) return;
        voiceLock.current = true;
        window.speechSynthesis.cancel();
        const msg = new SpeechSynthesisUtterance(text);
        msg.pitch = 1;
        msg.rate = 1;
        msg.onend = () => (voiceLock.current = false);
        window.speechSynthesis.speak(msg);
    };

    const startShakti = async () => {
        setStatus("Starting...");
        setStep("⚙ Activating Shakti Alert system...");
        speak("Activating Shakti Alert system. Please wait.");

        try {
            const res = await fetch(`${BACKEND_URL}/start_shakti`, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
            });
            const data = await res.json();

            if (data.status === "success" || data.status === "starting" || data.status === "already_running") {
                setStatus("Running");
                setStep("🎤 Initializing mic and model...");
                speak("Shakti system initializing, please wait a few seconds.");
            } else {
                setStatus("Error");
                setStep("❌ Unable to start Shakti Alert system.");
                speak("Error while starting Shakti Alert system.");
            }
        } catch {
            setStatus("Backend not reachable");
            setStep("⚠️ Flask backend not running.");
            speak("Backend not reachable. Please start Flask server.");
        }
    };

    const stopShakti = async () => {
        setStatus("Stopping...");
        setStep("🛑 Stopping all services...");
        speak("Stopping Shakti Alert system.");

        try {
            await fetch(`${BACKEND_URL}/stop_shakti`, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
            });
            setStatus("Stopped");
            setStep("🟡 System stopped successfully.");
            speak("System stopped successfully.");
        } catch {
            setStatus("Error");
            setStep("❌ Error stopping Shakti Alert system.");
            speak("Unable to stop system.");
        }
    };

    // Check for active session on mount
    useEffect(() => {
        const checkActive = async () => {
            try {
                const res = await fetch(`${BACKEND_URL}/check_active`);
                const data = await res.json();
                if (data.running) {
                    setStatus("Running");
                    setConnected(true);
                    setStep("✅ System Active (Reconnected)");
                }
            } catch (err) {
                console.log("Backend check failed", err);
            }
        };
        checkActive();
    }, []);

    // Geolocation Sync & Danger Zone Monitor
    useEffect(() => {
        if (status === "Running") {
            const checkLocationAndZones = async () => {
                if (navigator.geolocation) {
                    navigator.geolocation.getCurrentPosition(
                        async (position) => {
                            const { latitude, longitude, accuracy } = position.coords;

                            // Get auth token if available
                            const token = localStorage.getItem("shakti_token");
                            const headers = {
                                "Content-Type": "application/json"
                            };

                            if (token) {
                                headers["Authorization"] = `Bearer ${token}`;
                            }

                            // 1. Sync Location
                            fetch(`${BACKEND_URL}/update_location`, {
                                method: "POST",
                                headers: headers,
                                body: JSON.stringify({ lat: latitude, lon: longitude, accuracy: accuracy }),
                            }).catch((err) => console.error("Location sync failed", err));

                            // 2. Check Danger Zones
                            try {
                                const res = await fetch(`${BACKEND_URL}/danger_zones`);
                                const zones = await res.json();

                                let dangerFound = false;
                                for (const zone of zones) {
                                    const dist = getDistanceFromLatLonInKm(latitude, longitude, zone.lat, zone.lon);
                                    if (dist <= 1.0) {
                                        dangerFound = true;
                                        break;
                                    }
                                }

                                if (dangerFound) {
                                    if (!window.dangerZoneWarned) {
                                        speak("Warning: You are entering a known danger zone. Please be careful.");
                                        setStep("⚠️ WARNING: You are in a DANGER ZONE!");
                                        window.dangerZoneWarned = true;
                                    }
                                } else {
                                    window.dangerZoneWarned = false;
                                }

                            } catch (err) {
                                console.error("Danger zone check failed", err);
                            }
                        },
                        (err) => console.error("Geolocation error:", err),
                        { enableHighAccuracy: true, timeout: 10000, maximumAge: 0 }
                    );
                }
            };

            checkLocationAndZones();
            const interval = setInterval(checkLocationAndZones, 10000);
            return () => clearInterval(interval);
        }
    }, [status]);

    // Haversine Formula for Distance
    function getDistanceFromLatLonInKm(lat1, lon1, lat2, lon2) {
        var R = 6371;
        var dLat = deg2rad(lat2 - lat1);
        var dLon = deg2rad(lon2 - lon1);
        var a =
            Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(deg2rad(lat1)) * Math.cos(deg2rad(lat2)) *
            Math.sin(dLon / 2) * Math.sin(dLon / 2);
        var c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        var d = R * c;
        return d;
    }

    function deg2rad(deg) {
        return deg * (Math.PI / 180);
    }

    // SSE Status Stream
    useEffect(() => {
        if (status === "Running") {
            const evtSource = new EventSource(`${BACKEND_URL}/status_stream`);
            setConnected(true);

            evtSource.onmessage = (event) => {
                const msg = event.data || "";

                if (msg.includes("VERIFYING_VOICE")) {
                    setStep("🔍 Verifying Voice...");
                } else if (msg.includes("VOICE_VERIFIED")) {
                    setStep("✅ Verified User");
                    speak("User verified.");
                } else if (msg.includes("ANALYZING_EMOTION")) {
                    setStep("🧠 Detecting Emotion...");
                } else if (msg.includes("PANIC_DETECTED")) {
                    setStep("🚨 Panic Tone Detected!");
                    speak("Panic tone detected.");
                } else if (msg.includes("NO_PANIC_DETECTED")) {
                    setStep("😌 Calm Tone Detected");
                } else if (msg.includes("RECORDING_AUDIO")) {
                    setStep("🎤 Recording Audio...");
                    speak("Recording audio.");
                } else if (msg.includes("RECORDING_VIDEO")) {
                    setStep("🎥 Recording Video...");
                    speak("Recording video.");
                } else if (msg.includes("SENDING_ALERT")) {
                    setStep("🚀 Sending Alert...");
                } else if (msg.includes("ALERT_SENT_SUCCESSFULLY")) {
                    alertLock.current = false;
                    setStep("✅ Alert Successfully Sent!");
                    speak("Alert sent to guardian via WhatsApp, SMS, or email.");
                    setHelpDetected(false);
                    setTimeout(() => setStep("👂 Listening for 'Help'..."), 4000);
                } else if (msg.includes("ALERT_SENT")) {
                    alertLock.current = false;
                    setStep("✅ Alert Delivered! (via SMS/Email if WhatsApp unavailable)");
                    speak("Alert delivered to guardian.");
                    setHelpDetected(false);
                    setTimeout(() => setStep("👂 Listening for 'Help'..."), 4000);
                } else if (msg.includes("LISTENING")) {
                    if (!alertLock.current) {
                        setStep("👂 Listening for 'Help'...");
                    }
                } else if (msg.includes("SYSTEM_STOPPED")) {
                    alertLock.current = false;
                    setStep("🟡 System stopped.");
                    setStatus("Stopped");
                }
            };

            evtSource.onerror = (err) => {
                console.error("[SSE] Connection lost:", err);
                setConnected(false);
                evtSource.close();
            };

            return () => evtSource.close();
        }
    }, [status]);

    return (
        <main className="relative flex flex-col items-center justify-center min-h-screen text-white">
            <div className="absolute inset-0 -z-10">
                <LiquidEther
                    className="absolute inset-0 -z-10"
                    colors={["#1a0000", "#660000", "#ff0000"]}
                    mouseForce={15}
                    cursorSize={70}
                    resolution={0.35}
                    autoDemo={true}
                    autoSpeed={0.5}
                    autoIntensity={2.0}
                    autoResumeDelay={1000}
                    autoRampDuration={0.6}
                />
            </div>

            <div className="w-11/12 max-w-6xl p-10 rounded-xl bg-transparent flex flex-col lg:flex-row items-center justify-between gap-10">
                <div className="space-y-6 lg:w-1/2">
                    <h1 className="text-6xl font-extrabold">Shakti Alert</h1>

                    <p className="text-gray-300 text-lg leading-relaxed">
                        AI-powered emergency system with live tracking, instant alerts, and automatic emotion-aware activation.
                    </p>

                    <div className="flex flex-wrap gap-4">
                        <button
                            onClick={startShakti}
                            className="px-6 py-3 bg-red-600 hover:bg-red-700 transition-all rounded-lg shadow-md font-semibold"
                        >
                            🔴 Start Shakti Alert
                        </button>

                        <button
                            onClick={stopShakti}
                            className="px-6 py-3 bg-gray-800 hover:bg-gray-900 transition-all rounded-lg shadow-md font-semibold"
                        >
                            ⏹ Stop Shakti Alert
                        </button>
                    </div>

                    <div className="mt-5 text-gray-300">
                        <p>System Status: <span className="font-bold">{status}</span></p>

                        {connected ? (
                            <p className="text-green-400 text-sm mt-1">🛰 Connected</p>
                        ) : (
                            <p className="text-red-400 text-sm mt-1">⚠ Disconnected</p>
                        )}

                        {step && (
                            <p className="text-blue-300 mt-3 font-semibold animate-pulse">{step}</p>
                        )}

                        {helpDetected && (
                            <p className="text-red-400 mt-2 font-bold animate-bounce">
                                🧠 HELP DETECTED - Alert Sequence Active
                            </p>
                        )}
                    </div>
                </div>

                <div className="lg:w-1/2 w-full h-[420px] rounded-xl overflow-hidden bg-transparent relative spline-container">
                    <Spline scene="https://prod.spline.design/NErcUzyPUmhMPONg/scene.splinecode" />

                    {/* Overlay to cover watermark */}
                    <div style={{
                        position: 'absolute',
                        bottom: 0,
                        right: 0,
                        width: '150px',
                        height: '50px',
                        background: 'transparent',
                        zIndex: 9999,
                        pointerEvents: 'none'
                    }}></div>

                    <style>{`
                        /* Hide Spline watermark logo - all variations */
                        .spline-container iframe {
                            pointer-events: auto;
                        }
                        
                        /* Hide the watermark badge */
                        .spline-container canvas + div,
                        .spline-container > div > div:last-child,
                        .spline-container > div > a,
                        .spline-watermark,
                        [class*="watermark"],
                        [class*="logo"],
                        [class*="badge"],
                        a[href*="spline"],
                        div[style*="position: absolute"][style*="bottom"],
                        div[style*="position: fixed"][style*="bottom"] {
                            display: none !important;
                            opacity: 0 !important;
                            visibility: hidden !important;
                            pointer-events: none !important;
                        }
                        
                        /* Additional targeting for the badge */
                        .spline-container * {
                            position: relative;
                        }
                        
                        .spline-container *:has(> a[href*="spline"]) {
                            display: none !important;
                        }
                        
                        /* Force hide bottom right elements */
                        .spline-container > div > div:nth-last-child(1),
                        .spline-container > div > div:nth-last-child(2) {
                            display: none !important;
                        }
                    `}</style>
                </div>
            </div>
        </main>
    );
}
