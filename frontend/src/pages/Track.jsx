import React, { useState, useEffect, useRef } from "react";
import { useNavigate } from "react-router-dom";
import LocationService from "../services/locationService";

export default function Track() {
    const [friends, setFriends] = useState([]);
    const [showAddFriend, setShowAddFriend] = useState(false);
    const [showCodeOptions, setShowCodeOptions] = useState(false);
    const [friendCode, setFriendCode] = useState("");
    const [myCode, setMyCode] = useState(null);
    const [error, setError] = useState("");
    const [success, setSuccess] = useState("");
    const [myLocation, setMyLocation] = useState(null);
    const [locationAccuracy, setLocationAccuracy] = useState(null);
    const [currentAddress, setCurrentAddress] = useState("Fetching location...");
    const mapRef = useRef(null);
    const googleMapRef = useRef(null);
    const markersRef = useRef({});
    const circlesRef = useRef({});
    const dangerCirclesRef = useRef([]);
    const navigate = useNavigate();

    const COLORS = [
        '#00ffea', '#ff00ea', '#00ff00', '#ffea00', '#ff6b00',
        '#00b4d8', '#ff006e', '#7209b7', '#3a86ff', '#fb5607'
    ];

    useEffect(() => {
        loadGoogleMaps();
        fetchFriendsLocations();
        fetchMyCode();
        startLocationTracking();

        const locationInterval = setInterval(fetchFriendsLocations, 10000);
        const dangerInterval = setInterval(fetchDangerZones, 30000);

        return () => {
            clearInterval(locationInterval);
            clearInterval(dangerInterval);
            LocationService.stopTracking();
        };
    }, []);

    /**
     * Start real-time location tracking
     */
    const startLocationTracking = () => {
        LocationService.startTracking(
            async (location) => {
                console.log("📍 Location updated:", location);
                setMyLocation(location);
                setLocationAccuracy(location.accuracy);
                
                // Send to server for backup
                try {
                    await fetch("http://127.0.0.1:5000/update_location", {
                        method: "POST",
                        headers: { "Content-Type": "application/json" },
                        body: JSON.stringify({
                            lat: location.lat,
                            lon: location.lon,
                            accuracy: location.accuracy,
                            battery: getBatteryLevel(),
                            timestamp: Date.now()
                        })
                    });
                } catch (err) {
                    console.error("Location sync failed:", err);
                }

                // Get address from coordinates
                const address = await LocationService.getAddressFromCoords(location.lat, location.lon);
                setCurrentAddress(address);

                // Update map center
                if (googleMapRef.current) {
                    googleMapRef.current.panTo({ lat: location.lat, lng: location.lon });
                }
            },
            (error) => {
                console.error("Location error:", error);
                setError(error);
            }
        );
    };

    /**
     * Get device battery level
     */
    const getBatteryLevel = async () => {
        try {
            if (navigator.getBattery) {
                const battery = await navigator.getBattery();
                return Math.round(battery.level * 100);
            }
        } catch (e) {
            console.log("Battery API not available");
        }
        return 100;
    };

    const loadGoogleMaps = () => {
        if (window.google) {
            initMap();
            return;
        }

        const script = document.createElement('script');
        script.src = `https://maps.googleapis.com/maps/api/js?key=AIzaSyCT9Txjd4gsh-3uXl63uA_Medci0pp3yNI`;
        script.async = true;
        script.defer = true;
        script.onload = initMap;
        document.head.appendChild(script);
    };

    const initMap = () => {
        if (!mapRef.current) return;

        const map = new window.google.maps.Map(mapRef.current, {
            zoom: 12,
            center: { lat: 28.6139, lng: 77.2090 },
            mapTypeId: "roadmap",
            styles: [
                { elementType: "geometry", stylers: [{ color: "#0b132b" }] },
                { elementType: "labels.text.stroke", stylers: [{ color: "#0b132b" }] },
                { elementType: "labels.text.fill", stylers: [{ color: "#f0f0f0" }] },
                { featureType: "water", elementType: "geometry", stylers: [{ color: "#172a45" }] },
                { featureType: "road", elementType: "geometry", stylers: [{ color: "#1f4068" }] }
            ]
        });

        googleMapRef.current = map;
        fetchDangerZones();
    };

    const getAuthHeaders = () => {
        const token = localStorage.getItem("shakti_token");
        return token ? { "Authorization": `Bearer ${token}` } : null;
    };

    const fetchFriendsLocations = async () => {
        const headers = getAuthHeaders();

        if (!headers) {
            fetchSingleUserLocation();
            return;
        }

        try {
            const res = await fetch("http://127.0.0.1:5000/friends/locations", { headers });
            const data = await res.json();

            if (data.success && data.locations) {
                updateMarkers(data.locations);
            }
        } catch (err) {
            console.error("Failed to fetch friends locations:", err);
            fetchSingleUserLocation();
        }
    };

    const fetchSingleUserLocation = async () => {
        try {
            const res = await fetch("http://127.0.0.1:5000/latest_location");
            const data = await res.json();

            if (data.lat && data.lon) {
                updateMarkers([{
                    user_id: 0,
                    name: "You",
                    last_name: "",
                    lat: parseFloat(data.lat),
                    lon: parseFloat(data.lon),
                    is_stale: false
                }]);
            }
        } catch (err) {
            console.error("Failed to fetch location:", err);
        }
    };

    const fetchDangerZones = async () => {
        if (!googleMapRef.current) return;

        try {
            const res = await fetch("http://127.0.0.1:5000/danger_zones");
            const zones = await res.json();

            dangerCirclesRef.current.forEach(c => c.setMap(null));
            dangerCirclesRef.current = [];

            zones.forEach(zone => {
                const circle = new window.google.maps.Circle({
                    map: googleMapRef.current,
                    center: { lat: zone.lat, lng: zone.lon },
                    radius: zone.radius_m || 1000,
                    fillColor: "#ff0000",
                    fillOpacity: 0.35,
                    strokeColor: "#ff0000",
                    strokeWeight: 2,
                    clickable: true
                });

                const infoWindow = new window.google.maps.InfoWindow({
                    content: `<div style="color:black"><strong>🚨 Danger Zone</strong><br>High Alert Activity<br>Reports: ${zone.count}</div>`
                });

                circle.addListener("click", () => {
                    infoWindow.setPosition(circle.getCenter());
                    infoWindow.open(googleMapRef.current);
                });

                dangerCirclesRef.current.push(circle);
            });
        } catch (err) {
            console.error("Failed to fetch danger zones:", err);
        }
    };

    const updateMarkers = (locations) => {
        if (!googleMapRef.current) return;

        const validLocations = locations.filter(loc => loc.lat && loc.lon);
        setFriends(validLocations);

        // Remove old markers
        Object.keys(markersRef.current).forEach(userId => {
            if (!validLocations.find(loc => loc.user_id == userId)) {
                markersRef.current[userId].setMap(null);
                if (circlesRef.current[userId]) circlesRef.current[userId].setMap(null);
                delete markersRef.current[userId];
                delete circlesRef.current[userId];
            }
        });

        // Update or create markers
        validLocations.forEach((location, index) => {
            const userId = location.user_id;
            const position = { lat: location.lat, lng: location.lon };
            const color = COLORS[index % COLORS.length];
            const fullName = `${location.name} ${location.last_name || ''}`.trim();
            const isStale = location.is_stale;

            if (markersRef.current[userId]) {
                markersRef.current[userId].setPosition(position);
                circlesRef.current[userId].setCenter(position);
            } else {
                const marker = new window.google.maps.Marker({
                    position: position,
                    map: googleMapRef.current,
                    title: fullName,
                    icon: {
                        path: window.google.maps.SymbolPath.CIRCLE,
                        scale: 10,
                        fillColor: color,
                        fillOpacity: isStale ? 0.5 : 1,
                        strokeColor: '#ffffff',
                        strokeWeight: 2
                    },
                    label: {
                        text: fullName.charAt(0).toUpperCase(),
                        color: '#ffffff',
                        fontSize: '12px',
                        fontWeight: 'bold'
                    }
                });

                const circle = new window.google.maps.Circle({
                    map: googleMapRef.current,
                    radius: 50,
                    fillColor: color,
                    fillOpacity: 0.2,
                    strokeColor: color,
                    strokeWeight: 1
                });
                circle.bindTo('center', marker, 'position');

                const infoContent = `
          <div style="color: black; padding: 10px;">
            <h3 style="margin: 0 0 10px 0;">${fullName}</h3>
            <p style="margin: 5px 0;"><strong>Email:</strong> ${location.email || 'N/A'}</p>
            ${location.timestamp ? `<p style="margin: 5px 0;"><strong>Last seen:</strong> ${location.age_minutes} min ago</p>` : ''}
            ${isStale ? '<p style="color: orange; margin: 5px 0;">⚠️ Location may be outdated</p>' : ''}
          </div>
        `;

                const infoWindow = new window.google.maps.InfoWindow({ content: infoContent });
                marker.addListener('click', () => infoWindow.open(googleMapRef.current, marker));

                markersRef.current[userId] = marker;
                circlesRef.current[userId] = circle;
            }
        });

        // Center map
        if (validLocations.length > 0) {
            const bounds = new window.google.maps.LatLngBounds();
            validLocations.forEach(loc => bounds.extend({ lat: loc.lat, lng: loc.lon }));
            googleMapRef.current.fitBounds(bounds);
            if (validLocations.length === 1) googleMapRef.current.setZoom(14);
        }
    };

    const fetchMyCode = async () => {
        const headers = getAuthHeaders();
        if (!headers) return;

        try {
            const res = await fetch("http://127.0.0.1:5000/friends/my_code", { headers });
            const data = await res.json();
            if (data.success) setMyCode(data);
        } catch (err) {
            console.error("Failed to fetch code:", err);
        }
    };

    const generateNewCode = async () => {
        const headers = getAuthHeaders();
        if (!headers) {
            setError("Please login first");
            return;
        }

        try {
            const res = await fetch("http://127.0.0.1:5000/friends/generate_code", {
                method: "POST",
                headers
            });
            const data = await res.json();

            if (data.success) {
                setMyCode(data);
                setSuccess("New friend code generated!");
                setTimeout(() => setSuccess(""), 3000);
            }
        } catch (err) {
            setError("Failed to generate code");
        }
    };

    const addFriend = async () => {
        if (!friendCode.trim()) {
            setError("Please enter a friend code");
            return;
        }

        const headers = getAuthHeaders();
        if (!headers) {
            setError("Please login first");
            return;
        }

        try {
            const res = await fetch("http://127.0.0.1:5000/friends/add", {
                method: "POST",
                headers: {
                    ...headers,
                    "Content-Type": "application/json"
                },
                body: JSON.stringify({ code: friendCode.toUpperCase() })
            });

            const data = await res.json();

            if (data.success) {
                setSuccess(`Added ${data.friend.name} as a friend!`);
                setFriendCode("");
                setShowAddFriend(false);
                setShowCodeOptions(false);
                fetchFriendsLocations();
                setTimeout(() => setSuccess(""), 3000);
            } else {
                setError(data.error || "Failed to add friend");
            }
        } catch (err) {
            setError("Failed to add friend");
        }
    };

    const copyCode = () => {
        if (myCode?.code) {
            navigator.clipboard.writeText(myCode.code);
            setSuccess("Code copied!");
            setTimeout(() => setSuccess(""), 2000);
        }
    };

    return (
        <div className="relative h-screen w-screen bg-gray-900">
            {/* Map */}
            <div ref={mapRef} className="absolute inset-0"></div>

            {/* Professional Location Status Header */}
            <div className="absolute top-4 left-4 right-4 z-10">
                <div className="bg-gradient-to-r from-blue-900/95 to-purple-900/95 backdrop-blur-lg px-6 py-4 rounded-2xl border border-blue-500/30 text-white shadow-lg">
                    <div className="flex items-center justify-between">
                        <div>
                            <div className="flex items-center gap-3 mb-2">
                                <div className="w-3 h-3 bg-green-400 rounded-full animate-pulse"></div>
                                <span className="font-bold text-lg">📍 Live Location</span>
                                {locationAccuracy && (
                                    <span className="text-xs bg-green-500/20 px-2 py-1 rounded text-green-300">
                                        Accuracy: ±{locationAccuracy}m
                                    </span>
                                )}
                            </div>
                            <p className="text-sm text-gray-300">{currentAddress}</p>
                            {myLocation && (
                                <p className="text-xs text-gray-400 mt-1">
                                    {myLocation.lat.toFixed(6)}, {myLocation.lon.toFixed(6)}
                                </p>
                            )}
                        </div>
                        <div className="text-right">
                            <div className="text-2xl">👥</div>
                            <p className="text-sm text-gray-300">{friends.length} friends</p>
                        </div>
                    </div>
                </div>
            </div>

            {/* Back Button */}
            <button
                onClick={() => navigate("/")}
                className="absolute bottom-4 left-4 bg-gray-900/90 backdrop-blur-sm px-4 py-2 rounded-lg border border-gray-700 text-white hover:bg-gray-800 transition z-10"
            >
                ← Back
            </button>

            {/* Add Friend Button */}
            <button
                onClick={() => setShowAddFriend(!showAddFriend)}
                className="absolute bottom-4 right-4 bg-purple-600 hover:bg-purple-700 px-6 py-3 rounded-lg text-white font-semibold transition z-10 shadow-lg"
            >
                👥 Add Friend
            </button>

            {/* Add Friend Modal */}
            {showAddFriend && (
                <div className="absolute inset-0 bg-black/50 backdrop-blur-sm flex items-center justify-center z-20">
                    <div className="bg-gray-900 rounded-2xl p-8 max-w-md w-full mx-4 border border-gray-700">
                        <div className="flex justify-between items-center mb-6">
                            <h2 className="text-2xl font-bold text-white">Add Friend</h2>
                            <button
                                onClick={() => {
                                    setShowAddFriend(false);
                                    setShowCodeOptions(false);
                                    setError("");
                                }}
                                className="text-gray-400 hover:text-white text-2xl"
                            >
                                ×
                            </button>
                        </div>

                        {error && (
                            <div className="mb-4 p-3 bg-red-500/20 border border-red-500 rounded-lg text-red-300 text-sm">
                                {error}
                                <button onClick={() => setError("")} className="ml-2 underline">Dismiss</button>
                            </div>
                        )}

                        {success && (
                            <div className="mb-4 p-3 bg-green-500/20 border border-green-500 rounded-lg text-green-300 text-sm">
                                {success}
                            </div>
                        )}

                        {!showCodeOptions ? (
                            <div className="space-y-4">
                                <button
                                    onClick={() => setShowCodeOptions('enter')}
                                    className="w-full py-4 bg-blue-600 hover:bg-blue-700 rounded-lg text-white font-semibold transition"
                                >
                                    🔑 Enter Friend Code
                                </button>
                                <button
                                    onClick={() => {
                                        setShowCodeOptions('generate');
                                        if (!myCode) generateNewCode();
                                    }}
                                    className="w-full py-4 bg-purple-600 hover:bg-purple-700 rounded-lg text-white font-semibold transition"
                                >
                                    ✨ Generate My Code
                                </button>
                            </div>
                        ) : showCodeOptions === 'enter' ? (
                            <div>
                                <label className="block text-gray-300 mb-2">Enter Friend Code:</label>
                                <input
                                    type="text"
                                    value={friendCode}
                                    onChange={(e) => setFriendCode(e.target.value.toUpperCase())}
                                    placeholder="ABC123"
                                    maxLength="6"
                                    className="w-full px-4 py-3 bg-gray-800 border border-gray-600 rounded-lg text-white text-center text-2xl font-mono tracking-widest focus:outline-none focus:border-purple-500 transition mb-4"
                                />
                                <div className="flex gap-3">
                                    <button
                                        onClick={() => setShowCodeOptions(false)}
                                        className="flex-1 py-3 bg-gray-700 hover:bg-gray-600 rounded-lg text-white transition"
                                    >
                                        Back
                                    </button>
                                    <button
                                        onClick={addFriend}
                                        className="flex-1 py-3 bg-green-600 hover:bg-green-700 rounded-lg text-white font-semibold transition"
                                    >
                                        Add Friend
                                    </button>
                                </div>
                            </div>
                        ) : (
                            <div>
                                <label className="block text-gray-300 mb-2">Your Friend Code:</label>
                                {myCode ? (
                                    <div>
                                        <div className="bg-gradient-to-r from-purple-600 to-pink-600 rounded-lg p-6 text-center mb-4">
                                            <div className="text-5xl font-mono font-bold tracking-widest text-white">
                                                {myCode.code}
                                            </div>
                                            <p className="text-sm mt-2 text-white/80">
                                                Expires in {Math.floor((new Date(myCode.expires_at) - new Date()) / (1000 * 60 * 60))}h
                                            </p>
                                        </div>
                                        <div className="flex gap-3">
                                            <button
                                                onClick={() => setShowCodeOptions(false)}
                                                className="flex-1 py-3 bg-gray-700 hover:bg-gray-600 rounded-lg text-white transition"
                                            >
                                                Back
                                            </button>
                                            <button
                                                onClick={copyCode}
                                                className="flex-1 py-3 bg-blue-600 hover:bg-blue-700 rounded-lg text-white font-semibold transition"
                                            >
                                                📋 Copy Code
                                            </button>
                                            <button
                                                onClick={generateNewCode}
                                                className="flex-1 py-3 bg-purple-600 hover:bg-purple-700 rounded-lg text-white font-semibold transition"
                                            >
                                                🔄 New
                                            </button>
                                        </div>
                                    </div>
                                ) : (
                                    <div className="text-center py-8">
                                        <p className="text-gray-400 mb-4">Generating code...</p>
                                    </div>
                                )}
                            </div>
                        )}
                    </div>
                </div>
            )}

            {/* Legend */}
            <div className="absolute bottom-4 left-4 bg-gray-900/90 backdrop-blur-sm rounded-xl p-4 border border-gray-700 text-white z-10 max-w-xs">
                <div className="font-bold mb-3">👥 Active Users</div>
                {friends.length === 0 ? (
                    <div className="text-gray-400 text-sm">No active users</div>
                ) : (
                    <div className="space-y-2">
                        {friends.map((friend, index) => {
                            const color = COLORS[index % COLORS.length];
                            const fullName = `${friend.name} ${friend.last_name || ''}`.trim();
                            const status = friend.is_stale ? '⏱️' : '✅';

                            return (
                                <div key={friend.user_id} className="flex items-center gap-2">
                                    <div
                                        className="w-4 h-4 rounded-full"
                                        style={{ backgroundColor: color }}
                                    ></div>
                                    <div className="flex-1">
                                        <div className="text-sm">{status} {fullName}</div>
                                        {friend.age_minutes && (
                                            <div className="text-xs text-gray-400">{friend.age_minutes}m ago</div>
                                        )}
                                    </div>
                                </div>
                            );
                        })}
                    </div>
                )}
            </div>
        </div>
    );
}
