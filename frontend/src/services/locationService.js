/**
 * Real-time Location Tracking Service
 * Improves GPS accuracy and provides continuous updates
 */

class LocationService {
    constructor() {
        this.watchId = null;
        this.lastLocation = null;
        this.updateCallbacks = [];
        this.errorCallbacks = [];
        this.minAccuracy = 100; // meters
        this.updateInterval = 5000; // 5 seconds
    }

    /**
     * Start continuous GPS tracking
     */
    startTracking(onUpdate, onError) {
        if (!navigator.geolocation) {
            console.error("Geolocation not supported");
            if (onError) onError("Geolocation not supported");
            return;
        }

        this.updateCallbacks.push(onUpdate);
        if (onError) this.errorCallbacks.push(onError);

        // High accuracy tracking
        const options = {
            enableHighAccuracy: true,
            timeout: 10000,
            maximumAge: 0
        };

        // Get initial position immediately
        navigator.geolocation.getCurrentPosition(
            (pos) => this.handlePosition(pos),
            (err) => this.handleError(err),
            options
        );

        // Watch position for continuous updates
        this.watchId = navigator.geolocation.watchPosition(
            (pos) => this.handlePosition(pos),
            (err) => this.handleError(err),
            options
        );
    }

    /**
     * Stop tracking
     */
    stopTracking() {
        if (this.watchId !== null) {
            navigator.geolocation.clearWatch(this.watchId);
            this.watchId = null;
        }
        this.updateCallbacks = [];
        this.errorCallbacks = [];
    }

    /**
     * Handle successful position update
     */
    handlePosition(position) {
        const { latitude, longitude, accuracy, timestamp } = position.coords;

        // Only update if accuracy is good enough or location changed significantly
        if (!this.lastLocation || 
            accuracy <= this.minAccuracy || 
            this.getDistance(this.lastLocation, { latitude, longitude }) > 10) {
            
            this.lastLocation = {
                latitude,
                longitude,
                accuracy,
                timestamp
            };

            // Notify all subscribers
            this.updateCallbacks.forEach(cb => {
                try {
                    cb({
                        lat: latitude,
                        lon: longitude,
                        accuracy: Math.round(accuracy),
                        timestamp: new Date(timestamp).toISOString()
                    });
                } catch (e) {
                    console.error("Update callback error:", e);
                }
            });
        }
    }

    /**
     * Handle geolocation errors
     */
    handleError(error) {
        console.error("Geolocation error:", error);
        
        let message = "Unknown location error";
        switch (error.code) {
            case error.PERMISSION_DENIED:
                message = "Location permission denied";
                break;
            case error.POSITION_UNAVAILABLE:
                message = "Location unavailable";
                break;
            case error.TIMEOUT:
                message = "Location request timeout";
                break;
        }

        this.errorCallbacks.forEach(cb => {
            try {
                cb(message);
            } catch (e) {
                console.error("Error callback error:", e);
            }
        });
    }

    /**
     * Calculate distance between two points (haversine)
     */
    getDistance(point1, point2) {
        const R = 6371000; // Earth radius in meters
        const lat1 = (point1.latitude * Math.PI) / 180;
        const lat2 = (point2.latitude * Math.PI) / 180;
        const deltaLat = ((point2.latitude - point1.latitude) * Math.PI) / 180;
        const deltaLon = ((point2.longitude - point1.longitude) * Math.PI) / 180;

        const a =
            Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2) +
            Math.cos(lat1) * Math.cos(lat2) *
            Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);
        const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return R * c; // distance in meters
    }

    /**
     * Get last known location
     */
    getLastLocation() {
        return this.lastLocation;
    }

    /**
     * Reverse geocode coordinates to address
     */
    async getAddressFromCoords(lat, lon) {
        try {
            const response = await fetch(
                `https://nominatim.openstreetmap.org/reverse?format=json&lat=${lat}&lon=${lon}`
            );
            const data = await response.json();
            return data.address?.city || data.address?.state || "Unknown Location";
        } catch (error) {
            console.error("Geocoding error:", error);
            return "Location";
        }
    }
}

export default new LocationService();
