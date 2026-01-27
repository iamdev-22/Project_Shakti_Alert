import React, { useState, useEffect } from 'react';

export default function WhatsAppLogin() {
    const [qrCode, setQrCode] = useState(null);
    const [status, setStatus] = useState('Disconnected');
    const [phoneNumber, setPhoneNumber] = useState('');
    const [guardianPhone, setGuardianPhone] = useState('');
    const [pairingCode, setPairingCode] = useState(null);
    const [loading, setLoading] = useState(false);
    const [activeTab, setActiveTab] = useState('QR'); // 'qr' or 'phone' or 'guardian'
    const [savedGuardians, setSavedGuardians] = useState([]);
    const [error, setError] = useState('');
    const [success, setSuccess] = useState('');
    const BACKEND_URL = "http://127.0.0.1:5000";
    const WA_SERVER_URL = "http://localhost:3001";

    useEffect(() => {
        fetchStatus();
        fetchGuardians();
        const interval = setInterval(fetchStatus, 2000);
        return () => clearInterval(interval);
    }, []);

    const fetchStatus = async () => {
        try {
            // Try direct WhatsApp server first (fastest)
            const res = await fetch(`${WA_SERVER_URL}/status`);
            if (!res.ok) throw new Error(`Server returned ${res.status}`);
            
            const data = await res.json();
            console.log("✅ QR Status from direct server:", {
                status: data.status,
                hasQR: !!(data.qrCode || data.qr),
                serverTime: new Date().toLocaleTimeString()
            });
            
            setStatus(data.status || 'loading');
            
            // Handle QR code - support both response formats
            let qrImage = data.qrCode || data.qr;
            
            if (qrImage) {
                // Ensure it's a valid data URI for display
                if (typeof qrImage === 'string') {
                    if (qrImage.startsWith('data:image')) {
                        // Already formatted correctly
                        setQrCode(qrImage);
                    } else {
                        // Add data URI prefix if missing
                        setQrCode(`data:image/png;base64,${qrImage}`);
                    }
                }
            } else {
                setQrCode(null);
            }
            setError('');
        } catch (err) {
            console.warn("⚠️ Direct server failed:", err.message);
            try {
                // Fallback to Flask backend proxy
                const res = await fetch(`${BACKEND_URL}/api/whatsapp/qr`);
                if (!res.ok) throw new Error(`Backend returned ${res.status}`);
                
                const data = await res.json();
                console.log("✅ QR Status from backend proxy:", {
                    status: data.status,
                    hasQR: !!(data.qrCode || data.qr),
                    serverTime: new Date().toLocaleTimeString()
                });
                
                setStatus(data.status || 'loading');
                
                let qrImage = data.qrCode || data.qr;
                if (qrImage) {
                    if (typeof qrImage === 'string') {
                        if (qrImage.startsWith('data:image')) {
                            setQrCode(qrImage);
                        } else {
                            setQrCode(`data:image/png;base64,${qrImage}`);
                        }
                    }
                } else {
                    setQrCode(null);
                }
                setError('');
            } catch (err2) {
                console.error("❌ Both servers unreachable:", {
                    directError: err.message,
                    backendError: err2.message
                });
                setStatus('server_down');
                setQrCode(null);
                setError('❌ Cannot connect to WhatsApp server. Make sure WhatsApp server is running on port 3001!');
            }
        }
    };

    const fetchGuardians = async () => {
        try {
            const res = await fetch(`${BACKEND_URL}/guardians`);
            if (res.ok) {
                const data = await res.json();
                setSavedGuardians(data || []);
            }
        } catch (err) {
            console.error("Failed to fetch guardians:", err);
        }
    };

    const handleSaveGuardian = async () => {
        if (!guardianPhone.trim()) {
            setError('Please enter guardian phone number');
            return;
        }

        const cleanedPhone = guardianPhone.replace(/[^0-9+]/g, '');
        if (cleanedPhone.length < 10) {
            setError('Phone number must be at least 10 digits');
            return;
        }

        try {
            const res = await fetch(`${BACKEND_URL}/set-guardian-phone`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ phone: cleanedPhone })
            });

            if (res.ok) {
                setSuccess('Guardian phone saved successfully!');
                setGuardianPhone('');
                setTimeout(() => setSuccess(''), 3000);
                fetchGuardians();
            } else {
                setError('Failed to save guardian phone');
            }
        } catch (err) {
            setError('Error saving: ' + err.message);
        }
    };

    const handleRemoveGuardian = async (index) => {
        try {
            const res = await fetch(`${BACKEND_URL}/remove-guardian/${index}`, {
                method: 'DELETE'
            });

            if (res.ok) {
                setSuccess('Guardian removed');
                setTimeout(() => setSuccess(''), 3000);
                fetchGuardians();
            }
        } catch (err) {
            setError('Failed to remove: ' + err.message);
        }
    };

    const handleReset = async () => {
        if (!confirm("Are you sure? This will log you out and reset the connection.")) return;
        try {
            await fetch(`${WA_SERVER_URL}/reset`, { method: 'POST' });
            setStatus('disconnected');
            setQrCode(null);
            setPairingCode(null);
            setSuccess('Connection reset');
            setTimeout(() => setSuccess(''), 3000);
        } catch (err) {
            setError("Failed to reset: " + err);
        }
    };

    const handleGetPairingCode = async () => {
        if (!phoneNumber) {
            setError("Please enter your phone number (e.g., 919876543210)");
            return;
        }
        setLoading(true);
        setError('');
        try {
            const cleanedPhone = phoneNumber.replace(/[^0-9]/g, '');

            const res = await fetch(`${WA_SERVER_URL}/pair-code`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ phoneNumber: cleanedPhone })
            });
            const data = await res.json();
            if (data.error) throw new Error(data.error);
            setPairingCode(data.code);
            setSuccess('Pairing code generated! Enter it in WhatsApp.');
        } catch (err) {
            setError("Failed to get code: " + err.message);
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="min-h-screen bg-color-transparent text-white p-6">
            <div className="max-w-2xl mx-auto">
                <h1 className="text-4xl font-bold mb-2 text-center bg-gradient-to-r from-green-600 to-blue-300 bg-clip-text text-transparent">
                    WhatsApp Setup
                </h1>
                <p className="text-center text-gray-400 mb-8">Connect WhatsApp and add emergency contacts</p>

                {/* Status Badge */}
                <div className="mb-6 p-4 rounded-lg bg-color-trasprant">
                    <p className="text-sm text-gray-400">
                        WhatsApp Status: 
                        <span className={`ml-2 font-bold ${
                            status === 'connected' ? 'text-green-400' : 
                            status === 'server_down' ? 'text-red-400' : 
                            'text-yellow-400'
                        }`}>
                            {status === 'connected' && '✅ Connected'}
                            {status === 'disconnected' && '⏳ Disconnected'}
                            {status === 'server_down' && '❌ Server Down'}
                            {!['connected', 'disconnected', 'server_down'].includes(status) && '🔄 ' + status}
                        </span>
                    </p>
                </div>

                {/* Error/Success Messages */}
                {error && <div className="mb-4 p-4 bg-red-900/30 border border-red-600 rounded-lg text-red-300 text-sm">{error}</div>}
                {success && <div className="mb-4 p-4 bg-green-900/30 border border-green-600 rounded-lg text-green-300 text-sm">{success}</div>}

                {status === 'server_down' && (
                    <div className="bg-red-900/20 border border-red-600 p-6 rounded-lg text-center mb-6">
                        <p className="text-red-300 font-semibold">❌ WhatsApp Server is not running</p>
                        <p className="text-gray-400 text-sm mt-2">Run: node index.js in shakti_alert/wa_server/</p>
                    </div>
                )}

                {/* WhatsApp Connection */}
                {status !== 'server_down' && (
                    <div className="bg-gray-800 p-8 rounded-lg shadow-lg mb-8">
                        <h2 className="text-2xl font-bold mb-6 text-center text-green-400">
                            📱 Connect WhatsApp
                        </h2>

                        {status !== 'connected' ? (
                            <>
                                {/* Tabs */}
                                <div className="flex gap-4 mb-6 border-b border-gray-700">
                                    <button
                                        onClick={() => setActiveTab('qr')}
                                        className={`pb-3 px-4 font-semibold transition-all ${
                                            activeTab === 'qr'
                                                ? 'border-b-2 border-green-500 text-green-400'
                                                : 'text-gray-400 hover:text-gray-300'
                                        }`}
                                    >
                                        📱 QR Code
                                    </button>
                                    <button
                                        onClick={() => setActiveTab('phone')}
                                        className={`pb-3 px-4 font-semibold transition-all ${
                                            activeTab === 'phone'
                                                ? 'border-b-2 border-blue-500 text-blue-400'
                                                : 'text-gray-400 hover:text-gray-300'
                                        }`}
                                    >
                                        ☎️ Phone Number
                                    </button>
                                </div>

                                {/* QR Code Tab */}
                                {activeTab === 'qr' && (
                                    <div className="flex flex-col items-center">
                                        <p className="text-gray-300 mb-6 text-center text-sm">
                                            1. Open WhatsApp on your phone<br/>
                                            2. Go to Settings → Linked Devices<br/>
                                            3. Tap "Link a Device"<br/>
                                            4. Scan the QR code below
                                        </p>
                                        {qrCode ? (
                                            <div className="bg-white p-4 rounded-lg">
                                                <img src={qrCode} alt="WhatsApp QR" className="w-64 h-64 rounded" />
                                            </div>
                                        ) : (
                                            <div className="w-64 h-64 bg-gray-700 rounded-lg flex flex-col items-center justify-center">
                                                <div className="animate-spin text-3xl mb-2">📱</div>
                                                <p className="text-gray-400 text-sm">Loading QR Code...</p>
                                            </div>
                                        )}
                                        <p className="text-xs text-gray-500 mt-4">QR code refreshes every 5 minutes</p>
                                    </div>
                                )}

                                {/* Phone Number Tab */}
                                {activeTab === 'phone' && (
                                    <div className="flex flex-col gap-4">
                                        <p className="text-gray-300 text-center text-sm">
                                            Link your WhatsApp using phone number
                                        </p>
                                        <div>
                                            <label className="block text-sm font-semibold mb-2 text-gray-300">
                                                Your Phone (with country code)
                                            </label>
                                            <input
                                                type="text"
                                                placeholder="919876543210"
                                                className="w-full p-3 rounded bg-gray-700 border border-gray-600 focus:outline-none focus:border-blue-500 text-white"
                                                value={phoneNumber}
                                                onChange={(e) => setPhoneNumber(e.target.value)}
                                            />
                                            <p className="text-xs text-gray-500 mt-1">
                                                Format: Country Code + Number (e.g., 91 for India, 1 for USA)
                                            </p>
                                        </div>
                                        <button
                                            onClick={handleGetPairingCode}
                                            disabled={loading}
                                            className="w-full bg-blue-600 hover:bg-blue-700 disabled:bg-gray-600 text-white font-bold py-3 px-4 rounded transition"
                                        >
                                            {loading ? "⏳ Getting Code..." : "🔐 Get Pairing Code"}
                                        </button>

                                        {pairingCode && (
                                            <div className="bg-blue-900/20 border border-blue-600 p-4 rounded-lg">
                                                <p className="text-sm text-gray-300 mb-3">
                                                    Enter this code in WhatsApp:
                                                </p>
                                                <p className="text-2xl font-mono font-bold text-blue-400 text-center tracking-widest">
                                                    {pairingCode?.match(/.{1,4}/g)?.join('-') || pairingCode}
                                                </p>
                                                <p className="text-xs text-gray-500 mt-3 text-center">
                                                    Settings → Linked Devices → Link with phone number
                                                </p>
                                            </div>
                                        )}
                                    </div>
                                )}

                                <button
                                    onClick={handleReset}
                                    className="w-full mt-6 bg-red-600/20 hover:bg-red-600/30 border border-red-600 text-red-300 font-semibold py-2 px-4 rounded transition text-sm"
                                >
                                    🔄 Reset Connection
                                </button>
                            </>
                        ) : (
                            <div className="text-center">
                                <div className="text-6xl mb-4">✅</div>
                                <h3 className="text-2xl font-bold text-green-400 mb-2">Connected!</h3>
                                <p className="text-gray-300 mb-6">
                                    Shakti Alert can send emergency messages via WhatsApp
                                </p>
                                <button
                                    onClick={handleReset}
                                    className="bg-red-600 hover:bg-red-700 text-white font-semibold py-2 px-4 rounded transition"
                                >
                                    Disconnect
                                </button>
                            </div>
                        )}
                    </div>
                )}

                {/* Guardian Phone Setup */}
                <div className="bg-gray-800 p-8 rounded-lg shadow-lg">
                    <h2 className="text-2xl font-bold mb-6 text-center text-blue-400">
                        👨‍👩‍👧 Guardian Phone Setup
                    </h2>
                    <p className="text-gray-400 text-center text-sm mb-6">
                        Add guardian phone numbers for emergency alerts via WhatsApp, SMS, or Email
                    </p>

                    {/* Add Guardian Form */}
                    <div className="space-y-4 mb-8">
                        <div>
                            <label className="block text-sm font-semibold mb-2 text-gray-300">
                                Guardian Phone (with country code)
                            </label>
                            <input
                                type="text"
                                placeholder="+919990758187 or 919990758187"
                                className="w-full p-3 rounded bg-gray-700 border border-gray-600 focus:outline-none focus:border-green-500 text-white"
                                value={guardianPhone}
                                onChange={(e) => setGuardianPhone(e.target.value)}
                            />
                            <p className="text-xs text-gray-500 mt-1">
                                Emergency alerts will be sent to this number via WhatsApp, SMS, or Email
                            </p>
                        </div>
                        <button
                            onClick={handleSaveGuardian}
                            className="w-full bg-green-600 hover:bg-green-700 text-white font-bold py-3 px-4 rounded transition"
                        >
                            💾 Save Guardian Phone
                        </button>
                    </div>

                    {/* Guardians List */}
                    <div className="border-t border-gray-700 pt-6">
                        <h3 className="text-lg font-semibold mb-4 text-gray-300">
                            📋 Saved Guardians ({savedGuardians.length})
                        </h3>
                        {savedGuardians.length === 0 ? (
                            <p className="text-gray-500 text-center py-6">
                                No guardians added yet. Add one above.
                            </p>
                        ) : (
                            <div className="space-y-3">
                                {savedGuardians.map((guardian, idx) => (
                                    <div
                                        key={idx}
                                        className="bg-gray-700/50 border border-gray-600 p-4 rounded-lg flex justify-between items-center"
                                    >
                                        <div>
                                            <p className="font-semibold text-white">
                                                {guardian.name || `Guardian ${idx + 1}`}
                                            </p>
                                            <p className="text-sm text-gray-400">{guardian.phone}</p>
                                        </div>
                                        <button
                                            onClick={() => handleRemoveGuardian(idx)}
                                            className="bg-red-600 hover:bg-red-700 text-white px-3 py-1 rounded text-sm transition"
                                        >
                                            ❌ Remove
                                        </button>
                                    </div>
                                ))}
                            </div>
                        )}
                    </div>
                </div>

                <div className="mt-8 text-center text-xs text-gray-500">
                    <p>Need help? Make sure to:</p>
                    <p className="mt-1">1. Run WhatsApp server (node index.js in wa_server/)</p>
                    <p>2. Add at least one guardian phone</p>
                    <p>3. Start the Shakti Alert system from Home page</p>
                </div>
            </div>
        </div>
    );
}
