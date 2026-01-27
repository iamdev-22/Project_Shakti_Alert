# =========================================
# 🚨 Danger Zone Model Training Script
# =========================================

import pandas as pd
import numpy as np
from sklearn.ensemble import RandomForestClassifier
from sklearn.model_selection import train_test_split
from sklearn.metrics import classification_report
import joblib
import os

# -----------------------------
# 1️⃣ Load your alerts dataset
# -----------------------------
# Example CSV format:
# user_id, lat, lon, timestamp
# 101, 28.6139, 77.2090, 2025-10-30T09:41:00Z
# 102, 28.6140, 77.2091, 2025-10-30T09:43:00Z

DATA_PATH = os.path.join(os.path.dirname(__file__), "../shakti_alert/database/alerts.csv")

if not os.path.exists(DATA_PATH):
    print("⚠️ No alerts.csv found — creating sample dummy data")
    df = pd.DataFrame({
        "user_id": np.random.randint(100, 200, 100),
        "lat": np.random.uniform(28.60, 28.70, 100),
        "lon": np.random.uniform(77.10, 77.30, 100),
        "timestamp": pd.date_range("2025-10-01", periods=100, freq="H")
    })
else:
    df = pd.read_csv(DATA_PATH)

print(f"Loaded {len(df)} alert records")

# -----------------------------
# 2️⃣ Group nearby coordinates
# -----------------------------
# Round off lat/lon to group nearby alerts (≈100m radius)
df["lat_group"] = df["lat"].round(3)
df["lon_group"] = df["lon"].round(3)

# -----------------------------
# 3️⃣ Count unique users & alert frequency
# -----------------------------
stats = df.groupby(["lat_group", "lon_group"]).agg({
    "user_id": "nunique",    # unique users
    "lat": "size"            # total alerts (frequency)
}).rename(columns={"user_id": "unique_users", "lat": "total_alerts"}).reset_index()

# -----------------------------
# 4️⃣ Label Danger Zones
# -----------------------------
# ✅ Danger = if ≥10 unique users OR ≥20 total alerts
stats["danger"] = ((stats["unique_users"] >= 10) | (stats["total_alerts"] >= 20)).astype(int)

print("Sample danger zone data:")
print(stats.head())

# -----------------------------
# 5️⃣ Train Model
# -----------------------------
X = stats[["lat_group", "lon_group"]]
y = stats["danger"]

X_train, X_test, y_train, y_test = train_test_split(X, y, test_size=0.2, random_state=42)

model = RandomForestClassifier(n_estimators=100, random_state=42)
model.fit(X_train, y_train)

y_pred = model.predict(X_test)
print("\n📊 Model Performance:")
print(classification_report(y_test, y_pred))

# -----------------------------
# 6️⃣ Save Trained Model
# -----------------------------
MODEL_PATH = os.path.join(os.path.dirname(__file__), "danger_zone_model.pkl")
joblib.dump(model, MODEL_PATH)
print(f"\n✅ Model saved to {MODEL_PATH}")
