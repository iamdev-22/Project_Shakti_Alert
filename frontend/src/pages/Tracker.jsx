import React, { useEffect, useRef, useState } from "react";
import L from "leaflet";
import "leaflet/dist/leaflet.css";

// Fix for default marker icon missing in React Leaflet
import icon from 'leaflet/dist/images/marker-icon.png';
import iconShadow from 'leaflet/dist/images/marker-shadow.png';

let DefaultIcon = L.icon({
  iconUrl: icon,
  shadowUrl: iconShadow,
  iconSize: [25, 41],
  iconAnchor: [12, 41]
});
L.Marker.prototype.options.icon = DefaultIcon;

export default function Tracker() {
  const mapRef = useRef(null);
  const myMarkerRef = useRef(null);
  const [friends, setFriends] = useState([]);

  useEffect(() => {
    // ✅ Initialize map only once
    if (!mapRef.current) {
      mapRef.current = L.map("map").setView([28.6, 77.2], 13); // Default location
      L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", {
        attribution: "© OpenStreetMap contributors",
      }).addTo(mapRef.current);
    }

    const map = mapRef.current;
    const zoneLayer = L.layerGroup().addTo(map);
    const friendLayer = L.layerGroup().addTo(map);

    // ✅ Fetch & draw danger zones
    async function fetchZones() {
      try {
        const res = await fetch("http://127.0.0.1:5000/danger_zones");
        const zones = await res.json();

        zoneLayer.clearLayers();

        zones.forEach((z) => {
          L.circle([z.lat, z.lon], {
            radius: z.radius_m || 150,
            color: "red",
            fillColor: "red",
            fillOpacity: 0.3,
            weight: 2,
          })
            .addTo(zoneLayer)
            .bindPopup(`🚨 Danger Zone<br>${z.count} recent alerts`);
        });
      } catch (err) {
        console.error("Failed to load danger zones:", err);
      }
    }

    fetchZones();
    const interval = setInterval(fetchZones, 60000); // refresh every 60s

    // ✅ Live Location Tracking
    if (navigator.geolocation) {
      navigator.geolocation.watchPosition(
        (pos) => {
          const { latitude, longitude } = pos.coords;

          if (!myMarkerRef.current) {
            // Create blue marker for self
            const blueIcon = new L.Icon({
              iconUrl: 'https://raw.githubusercontent.com/pointhi/leaflet-color-markers/master/img/markers/marker-icon-2x-blue.png',
              shadowUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/0.7.7/images/marker-shadow.png',
              iconSize: [25, 41],
              iconAnchor: [12, 41],
              popupAnchor: [1, -34],
              shadowSize: [41, 41]
            });

            myMarkerRef.current = L.marker([latitude, longitude], { icon: blueIcon })
              .addTo(map)
              .bindPopup("📍 My Live Location")
              .openPopup();

            map.setView([latitude, longitude], 15);
          } else {
            myMarkerRef.current.setLatLng([latitude, longitude]);
          }
        },
        (err) => console.error("Location error:", err),
        { enableHighAccuracy: true }
      );
    }

    return () => {
      clearInterval(interval);
      map.removeLayer(zoneLayer);
      map.removeLayer(friendLayer);
    };
  }, []);

  // ✅ Update friends on map
  useEffect(() => {
    if (!mapRef.current) return;

    // Simple way: clear and redraw friends (for demo)
    // In production, use a layer group reference
    friends.forEach(f => {
      const greenIcon = new L.Icon({
        iconUrl: 'https://raw.githubusercontent.com/pointhi/leaflet-color-markers/master/img/markers/marker-icon-2x-green.png',
        shadowUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/0.7.7/images/marker-shadow.png',
        iconSize: [25, 41],
        iconAnchor: [12, 41],
        popupAnchor: [1, -34],
        shadowSize: [41, 41]
      });
      L.marker([f.lat, f.lon], { icon: greenIcon })
        .addTo(mapRef.current)
        .bindPopup(`👤 ${f.name}<br>Last seen: Just now`);
    });
  }, [friends]);

  const addFriend = () => {
    if (!mapRef.current) return;
    const center = mapRef.current.getCenter();
    // Add friend near center
    const newFriend = {
      id: Date.now(),
      name: `Friend ${friends.length + 1}`,
      lat: center.lat + (Math.random() - 0.5) * 0.01,
      lon: center.lng + (Math.random() - 0.5) * 0.01
    };
    setFriends([...friends, newFriend]);
  };

  return (
    <div className="relative h-[90vh] w-full">
      <div
        id="map"
        style={{
          width: "100%",
          height: "100%",
          borderRadius: "15px",
          boxShadow: "0 0 20px rgba(255,0,0,0.3)",
          zIndex: 0
        }}
      ></div>

      {/* Community Map Controls */}
      <div className="absolute bottom-8 right-8 z-[1000] flex flex-col gap-3">
        <button
          onClick={addFriend}
          className="bg-green-600 hover:bg-green-700 text-white p-4 rounded-full shadow-lg transition-all transform hover:scale-105 flex items-center gap-2"
        >
          <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M16 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"></path><circle cx="8.5" cy="7" r="4"></circle><line x1="20" y1="8" x2="20" y2="14"></line><line x1="23" y1="11" x2="17" y2="11"></line></svg>
          Add Friend
        </button>
        <div className="bg-gray-900/90 text-white p-4 rounded-xl shadow-lg backdrop-blur-sm">
          <h3 className="font-bold mb-2">Community Map</h3>
          <div className="flex items-center gap-2 text-sm mb-1">
            <span className="w-3 h-3 rounded-full bg-blue-500"></span> You
          </div>
          <div className="flex items-center gap-2 text-sm mb-1">
            <span className="w-3 h-3 rounded-full bg-green-500"></span> Friends ({friends.length})
          </div>
          <div className="flex items-center gap-2 text-sm">
            <span className="w-3 h-3 rounded-full bg-red-500 opacity-50"></span> Danger Zones
          </div>
        </div>
      </div>
    </div>
  );
}
