import React, { useEffect, useState } from "react";
import axios from "axios";

export default function Dashboard(){
  const [alerts, setAlerts] = useState([]);
  const API = import.meta.env.VITE_API_BASE || "http://127.0.0.1:5000";

  useEffect(()=>{
    async function load(){
      try {
        // If backend exposes /alerts or /dashboard adjust path.
        const r = await axios.get(`${API}/alerts`); 
        setAlerts(r.data || []);
      } catch(e){
        console.error(e);
        // fallback: read latest_location as single event
        try {
          const s = await axios.get(`${API}/latest_location`);
          setAlerts([{lat: s.data.lat, lon: s.data.lon, timestamp: s.data.timestamp, message: "Live location snapshot"}]);
        } catch(_) {}
      }
    }
    load();
  },[]);

  return (
    <div className="p-6 container mx-auto">
      <h2 className="text-2xl font-bold mb-4">Alert Dashboard</h2>
      <div className="grid gap-3">
        {alerts.map((a, idx) => (
          <div key={idx} className="glass p-4 rounded flex justify-between items-center">
            <div>
              <div className="text-sm text-slate-400">{a.message || "Alert"}</div>
              <div className="font-medium">{a.lat}, {a.lon}</div>
              <div className="text-xs text-slate-500">{a.timestamp || "—"}</div>
            </div>
            <div className="text-sm text-accent font-semibold">{a.danger || "unknown"}</div>
          </div>
        ))}
      </div>
    </div>
  );
}
