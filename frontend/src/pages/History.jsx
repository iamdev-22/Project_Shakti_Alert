import React, { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";

export default function History() {
  const [alerts, setAlerts] = useState([]);
  const [loading, setLoading] = useState(true);
  const navigate = useNavigate();

  useEffect(() => {
    fetchHistory();
  }, []);

  const fetchHistory = async () => {
    try {
      const res = await fetch("http://127.0.0.1:5000/alert_history");
      const data = await res.json();
      setAlerts(data);
      setLoading(false);
    } catch (err) {
      console.error("Failed to fetch history:", err);
      setLoading(false);
    }
  };

  const formatDate = (timestamp) => {
    if (!timestamp) return "Unknown";
    const date = new Date(timestamp);
    return date.toLocaleString();
  };

  return (
    <div className="min-h-screen bg-gradient-to-br from-gray-900 via-red-900 to-black text-white p-8">
      <div className="max-w-6xl mx-auto">
        {/* Header */}
        <div className="flex justify-between items-center mb-8">
          <h1 className="text-4xl font-bold">📜 Alert History</h1>
          <button
            onClick={() => navigate("/")}
            className="px-4 py-2 bg-gray-700 hover:bg-gray-600 rounded-lg transition"
          >
            ← Back to Home
          </button>
        </div>

        {/* Loading State */}
        {loading && (
          <div className="text-center py-12">
            <div className="animate-spin rounded-full h-16 w-16 border-t-2 border-b-2 border-red-500 mx-auto"></div>
            <p className="mt-4 text-gray-400">Loading history...</p>
          </div>
        )}

        {/* Empty State */}
        {!loading && alerts.length === 0 && (
          <div className="text-center py-12 bg-gray-800/50 rounded-xl">
            <p className="text-2xl text-gray-400">No alerts yet</p>
            <p className="text-gray-500 mt-2">Your alert history will appear here</p>
          </div>
        )}

        {/* Alert List */}
        {!loading && alerts.length > 0 && (
          <div className="space-y-4">
            {alerts.map((alert) => (
              <div
                key={alert.id}
                className="bg-gray-800/70 backdrop-blur-sm rounded-xl p-6 border border-gray-700 hover:border-red-500 transition-all"
              >
                <div className="flex justify-between items-start">
                  <div className="flex-1">
                    <div className="flex items-center gap-3 mb-2">
                      <span className="text-2xl">🚨</span>
                      <h3 className="text-xl font-semibold">
                        {alert.message || "Emergency Alert"}
                      </h3>
                      {alert.danger === "danger" && (
                        <span className="px-3 py-1 bg-red-600 text-xs rounded-full">
                          DANGER ZONE
                        </span>
                      )}
                    </div>

                    <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mt-4 text-sm">
                      <div>
                        <p className="text-gray-400">📅 Date & Time</p>
                        <p className="font-semibold">{formatDate(alert.timestamp)}</p>
                      </div>
                      <div>
                        <p className="text-gray-400">📍 Location</p>
                        <p className="font-semibold">
                          {alert.lat?.toFixed(4)}, {alert.lon?.toFixed(4)}
                        </p>
                      </div>
                      <div>
                        <p className="text-gray-400">🆔 Alert ID</p>
                        <p className="font-semibold">#{alert.id}</p>
                      </div>
                    </div>
                  </div>

                  <a
                    href={`https://www.google.com/maps?q=${alert.lat},${alert.lon}`}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="px-4 py-2 bg-blue-600 hover:bg-blue-700 rounded-lg transition text-sm"
                  >
                    View on Map
                  </a>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
