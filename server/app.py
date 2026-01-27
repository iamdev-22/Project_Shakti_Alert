# server/app.py
import os
import json
import sqlite3
from datetime import datetime, timedelta
from flask import Flask, request, jsonify, send_file
from werkzeug.utils import secure_filename
import smtplib
from email.message import EmailMessage
import requests

BASE_DIR = os.path.dirname(__file__)
UPLOAD_DIR = os.path.join(BASE_DIR, "uploads")
os.makedirs(UPLOAD_DIR, exist_ok=True)

CFG_PATH = os.path.join(BASE_DIR, "config.json")
with open(CFG_PATH, "r", encoding="utf-8") as f:
    CFG = json.load(f)

DB_PATH = os.path.join(BASE_DIR, "alerts.db")

# ---------- DB helpers ----------
def init_db():
    conn = sqlite3.connect(DB_PATH)
    c = conn.cursor()
    c.execute("""
    CREATE TABLE IF NOT EXISTS alerts (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      phone TEXT,
      lat REAL,
      lon REAL,
      timestamp TEXT,
      audio_path TEXT,
      video_path TEXT
    )
    """)
    conn.commit()
    conn.close()
init_db()

# ---------- Email helper ----------
def send_email_with_attachments(subject, body, to_emails, attachments=None):
    user = CFG["gmail_user"]
    password = CFG["gmail_app_password"]
    msg = EmailMessage()
    msg["From"] = user
    msg["To"] = ", ".join(to_emails)
    msg["Subject"] = subject
    msg.set_content(body)
    attachments = attachments or []
    for p in attachments:
        try:
            with open(p, "rb") as f:
                data = f.read()
            msg.add_attachment(data, maintype="application", subtype="octet-stream",
                               filename=os.path.basename(p))
        except Exception as e:
            print("attach error", e)
    with smtplib.SMTP_SSL("smtp.gmail.com", 465) as smtp:
        smtp.login(user, password)
        smtp.send_message(msg)
    print("[email] sent")

# ---------- Telegram helper (free & reliable) ----------
def send_telegram_message(chat_id, text):
    token = CFG.get("telegram_bot_token")
    if not token or not chat_id:
        return
    url = f"https://api.telegram.org/bot{token}/sendMessage"
    requests.post(url, json={"chat_id": chat_id, "text": text})

def send_telegram_file(chat_id, file_path, caption=None):
    token = CFG.get("telegram_bot_token")
    if not token or not chat_id or not os.path.exists(file_path):
        return
    url = f"https://api.telegram.org/bot{token}/sendDocument"
    with open(file_path, "rb") as f:
        requests.post(url, files={"document": f}, data={"chat_id": chat_id, "caption": caption or ""})

# ---------- Optional WhatsApp automation (info & helper) ----------
# NOTE: reliable automatic WhatsApp send requires Business API or automation via browser (selenium/pywhatkit).
# We'll recommend Telegram + Email for auto-delivery. If you want browser automation, see note at end.

# ---------- Routes ----------
app = Flask(__name__)

@app.route("/trigger_alert", methods=["POST"])
def trigger_alert():
    """
    Accepts multipart form-data:
      - phone (string)
      - lat (float)
      - lon (float)
      - audio (file) optional
      - video (file) optional
      - live (bool) optional -> if true, indicates continuous stream
    """
    phone = request.form.get("phone") or CFG["emergency_contacts"][0]["phone"]
    lat = request.form.get("lat")
    lon = request.form.get("lon")
    ts = request.form.get("timestamp") or datetime.utcnow().isoformat()

    audio_path = None
    video_path = None

    if "audio" in request.files:
        f = request.files["audio"]
        fname = secure_filename(f.filename or f"audio_{int(datetime.utcnow().timestamp())}.wav")
        audio_path = os.path.join(UPLOAD_DIR, fname)
        f.save(audio_path)

    if "video" in request.files:
        f = request.files["video"]
        fname = secure_filename(f.filename or f"video_{int(datetime.utcnow().timestamp())}.mp4")
        video_path = os.path.join(UPLOAD_DIR, fname)
        f.save(video_path)

    # store in DB
    conn = sqlite3.connect(DB_PATH)
    c = conn.cursor()
    c.execute("INSERT INTO alerts (phone, lat, lon, timestamp, audio_path, video_path) VALUES (?, ?, ?, ?, ?, ?)",
              (phone, float(lat) if lat else None, float(lon) if lon else None, ts, audio_path, video_path))
    conn.commit()
    conn.close()

    # Build maps link
    maps_link = f"https://maps.google.com/?q={lat},{lon}" if lat and lon else "Location unavailable"

    # Email the guardian with attachments (background thread advisable)
    subject = f"🚨 Emergency alert from {phone}"
    body = f"Emergency detected.\nPhone: {phone}\nTime: {ts}\nLocation: {maps_link}\n\nSent by Shakti System."
    try:
        send_email_with_attachments(subject, body, [CFG["guardian_email"]], [p for p in (audio_path, video_path) if p])
    except Exception as e:
        print("email error", e)

    # Telegram (recommended) to guardian chat
    try:
        tchat = CFG.get("telegram_chat_id")
        send_telegram_message(tchat, f"{subject}\n{maps_link}")
        if audio_path:
            send_telegram_file(tchat, audio_path, caption="Audio evidence")
        if video_path:
            send_telegram_file(tchat, video_path, caption="Video evidence")
    except Exception as e:
        print("telegram error", e)

    # For WhatsApp: we will also send a short message via wa.me link in email/telegram
    wa_text = f"🚨 EMERGENCY! {maps_link}"
    wa_link = f"https://wa.me/{phone.lstrip('+')}?text={requests.utils.requote_uri(wa_text)}"

    # Response contains helpful links for guardians
    return jsonify({
        "status": "ok",
        "maps_link": maps_link,
        "whatsapp_quick_link": wa_link
    })

@app.route("/latest", methods=["GET"])
def latest():
    # return most recent alert JSON
    conn = sqlite3.connect(DB_PATH)
    c = conn.cursor()
    c.execute("SELECT id, phone, lat, lon, timestamp FROM alerts ORDER BY id DESC LIMIT 1")
    row = c.fetchone()
    conn.close()
    if not row:
        return jsonify({"lat": None, "lon": None, "timestamp": None})
    return jsonify({"lat": row[2], "lon": row[3], "timestamp": row[4]})

# Danger-zone check helper (simple geohash-like rounding)
@app.route("/danger_zones", methods=["GET"])
def danger_zones():
    # group by rounded lat/lon to 3 decimals (~100m) and count alerts
    conn = sqlite3.connect(DB_PATH)
    c = conn.cursor()
    c.execute("""
      SELECT ROUND(lat,3) as rlat, ROUND(lon,3) as rlon, COUNT(*) as cnt
      FROM alerts
      WHERE lat IS NOT NULL AND lon IS NOT NULL
      GROUP BY rlat, rlon
      HAVING cnt >= 10
    """)
    rows = c.fetchall()
    conn.close()
    zones = [{"lat": r[0], "lon": r[1], "count": r[2]} for r in rows]
    return jsonify(zones)

if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5000, debug=True)
