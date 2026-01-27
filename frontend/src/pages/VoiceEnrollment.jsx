import React, { useState, useRef } from 'react';

export default function VoiceEnrollment() {
    const [recording, setRecording] = useState(false);
    const [status, setStatus] = useState('');
    const mediaRecorderRef = useRef(null);
    const chunksRef = useRef([]);
    const BACKEND_URL = "http://127.0.0.1:5000";

    const startRecording = async () => {
        try {
            const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
            mediaRecorderRef.current = new MediaRecorder(stream);
            chunksRef.current = [];

            mediaRecorderRef.current.ondataavailable = (e) => {
                if (e.data.size > 0) chunksRef.current.push(e.data);
            };

            mediaRecorderRef.current.onstop = async () => {
                const blob = new Blob(chunksRef.current, { type: 'audio/wav' });
                uploadAudio(blob);
            };

            mediaRecorderRef.current.start();
            setRecording(true);
            setStatus('Recording... Say "Help" clearly multiple times.');
        } catch (err) {
            console.error("Error accessing mic:", err);
            setStatus('Microphone access denied.');
        }
    };

    const stopRecording = () => {
        if (mediaRecorderRef.current && recording) {
            mediaRecorderRef.current.stop();
            setRecording(false);
            setStatus('Processing...');
        }
    };

    const uploadAudio = async (blob) => {
        const formData = new FormData();
        formData.append('audio', blob, 'enroll.wav');
        formData.append('username', 'dev'); // Default user for now

        try {
            const res = await fetch(`${BACKEND_URL}/enroll_voice`, {
                method: 'POST',
                body: formData,
            });
            const data = await res.json();

            if (res.ok) {
                setStatus('✅ Voice enrolled successfully!');
            } else {
                setStatus(`❌ Enrollment failed: ${data.message}`);
            }
        } catch (err) {
            setStatus('❌ Error uploading audio.');
        }
    };

    return (
        <div className="min-h-screen bg-gray-900 text-white p-10 flex flex-col items-center">
            <h1 className="text-4xl font-bold mb-8 text-blue-500">Voice Enrollment</h1>

            <div className="w-full max-w-lg bg-gray-800 p-8 rounded-lg shadow-lg text-center">
                <p className="mb-6 text-gray-300">
                    To verify your identity during an emergency, please record your voice.
                    Click "Start Recording" and say <strong>"Help! Help! Help!"</strong> clearly.
                </p>

                {!recording ? (
                    <button
                        onClick={startRecording}
                        className="bg-blue-600 hover:bg-blue-700 text-white font-bold py-3 px-8 rounded-full transition text-xl"
                    >
                        🎙 Start Recording
                    </button>
                ) : (
                    <button
                        onClick={stopRecording}
                        className="bg-red-600 hover:bg-red-700 text-white font-bold py-3 px-8 rounded-full transition text-xl animate-pulse"
                    >
                        ⏹ Stop Recording
                    </button>
                )}

                <p className="mt-6 text-lg font-semibold">{status}</p>
            </div>
        </div>
    );
}
