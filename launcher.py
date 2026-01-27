import os
import time
import json
import threading
import tkinter as tk
from tkinter import ttk, messagebox
import requests
import subprocess
import sys
import warnings

IS_SILENT = "--silent" in sys.argv


# ✅ Suppress noisy warnings
warnings.filterwarnings("ignore", category=UserWarning)
warnings.filterwarnings("ignore", message="Some weights of")
warnings.filterwarnings("ignore", message="You should probably TRAIN")

# ✅ Fix Unicode encoding for logs
sys.stdout.reconfigure(encoding='utf-8')

# ---------------- CONFIG ----------------
BASE_DIR = os.path.dirname(__file__)  # D:\project sakti
APP_PATH = os.path.join(BASE_DIR, "shakti_alert", "app.py")
MAIN_PATH = os.path.join(BASE_DIR, "shakti_alert", "main.py")
NGROK_PATH = os.path.join(BASE_DIR, "ngrok-v3-stable-windows-amd64", "ngrok.exe")
NGROK_PORT = "5000"

# ---------------- UI SETUP ----------------
if not IS_SILENT:
    root = tk.Tk()
    root.title("🚨 Shakti Alert Launcher")
    ...
else:
    # Silent mode me tkinter window mat banao
    root = None

if root:
    root.geometry("850x600")
    root.configure(bg="#101820")

style = ttk.Style()
style.configure("TButton", font=("Segoe UI", 11), padding=6)
style.configure("TLabel", background="#101820", foreground="#FEE715")

header = tk.Label(
    root,
    text="⚡ Shakti Alert System Launcher ⚡",
    bg="#101820",
    fg="#FEE715",
    font=("Segoe UI", 18, "bold")
)
header.pack(pady=10)

frame = tk.Frame(root, bg="#101820")
frame.pack(pady=10)

log_box = tk.Text(
    root,
    bg="#1A1A1A",
    fg="#00FF9C",
    insertbackground="white",
    wrap="word"
)
log_box.pack(fill="both", expand=True, padx=15, pady=10)

url_label = tk.Label(
    root,
    text="🌐 Live URL: Not connected",
    fg="#ff4444",
    bg="#101820",
    font=("Segoe UI", 11, "bold")
)
url_label.pack(pady=5)


# ---------------- LOGGING ----------------
def log(msg):
    """Write log to console or GUI box safely."""
    print(msg)  # always print in console (for silent mode)

    if root:  # GUI mode only
        log_box.insert(tk.END, msg + "\n")
        log_box.see(tk.END)
        root.update()



# ---------------- UTILITY FUNCTIONS ----------------
def check_flask_running():
    """Check if Flask is reachable on localhost:5000"""
    try:
        res = requests.get("http://127.0.0.1:5000", timeout=3)
        return res.status_code == 200
    except Exception:
        return False


def get_ngrok_url():
    """Try to fetch the ngrok public URL via its local API"""
    try:
        res = requests.get("http://127.0.0.1:4040/api/tunnels", timeout=5)
        tunnels = res.json().get("tunnels", [])
        if tunnels:
            url = tunnels[0].get("public_url")
            # ✅ Save latest URL for alert_system
            with open(os.path.join(BASE_DIR, "current_ngrok_url.json"), "w", encoding="utf-8") as f:
                json.dump({"url": url}, f, indent=2)
            return url
    except Exception as e:
        log(f"[Error] get_ngrok_url failed: {e}")
    return None


def run_process(cmd, prefix):
    """Run background process and live-stream logs"""
    try:
        proc = subprocess.Popen(
            cmd,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            shell=True,
            encoding="utf-8",
            errors="replace"
        )
        for line in proc.stdout:
            if "Debugger PIN" in line:
                continue
            log(f"{prefix} {line.strip()}")
        proc.wait()
    except Exception as e:
        log(f"[ERROR] {prefix} {e}")


# ---------------- MAIN LAUNCH FUNCTION ----------------
def start_all():
    """Launch Flask, ngrok, and main system"""
    log_box.delete(1.0, tk.END)
    log("🚀 Starting Shakti Alert System...\n")

    if not os.path.exists(APP_PATH):
        messagebox.showerror("Error", "app.py not found!")
        return

    # Kill any previous instances (Flask/ngrok)
    # Stop only older Shakti background instances, not Flask
    subprocess.run('taskkill /f /fi "WINDOWTITLE eq Shakti Alert Launcher"', shell=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    subprocess.run('taskkill /f /im ngrok.exe', shell=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

    time.sleep(2)

    # ✅ Start Flask backend
    log("🟢 Starting Flask server...")
    threading.Thread(
        target=lambda: run_process(f'"{sys.executable}" "{APP_PATH}"', "[Flask]"),
        daemon=True
    ).start()

    # Wait for Flask to come up
    for _ in range(15):
        if check_flask_running():
            log("✅ Flask running on http://127.0.0.1:5000")
            break
        time.sleep(1)
    else:
        log("❌ Flask server did not start. Check app.py.")
        return

    # ✅ Start ngrok tunnel
    log("🟣 Starting ngrok tunnel...")
    threading.Thread(
        target=lambda: run_process(f'"{NGROK_PATH}" http {NGROK_PORT}', "[Ngrok]"),
        daemon=True
    ).start()
    time.sleep(6)

    url = get_ngrok_url()
    if url:
        url_label.config(text=f"🌐 Live URL: {url}", fg="#00ff55")
        log(f"✅ Ngrok tunnel active → {url}")
    else:
        url_label.config(text="🌐 Live URL: Not connected", fg="#ff4444")
        log("⚠️ Could not detect ngrok tunnel, continuing anyway...")

    # ✅ Start AI emergency system
    if os.path.exists(MAIN_PATH):
        log("🧠 Booting main alert system...")
        threading.Thread(
            target=lambda: run_process(f'"{sys.executable}" "{MAIN_PATH}"', "[Main]"),
            daemon=True
        ).start()
        log("🎤 Waiting for wake word: HELP\n")
    else:
        log("⚠️ main.py not found!")


# ---------------- STOP FUNCTION ----------------
def stop_all():
    log("🛑 Stopping all Shakti processes...\n")
    for proc in ["python.exe", "ngrok.exe"]:
        try:
            subprocess.run(f'taskkill /f /im {proc}', shell=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        except Exception as e:
            log(f"[ERROR] Could not stop {proc}: {e}")
    url_label.config(text="🌐 Live URL: Not connected", fg="#ff4444")
    log("✅ All processes stopped successfully.\n")


# ---------------- BUTTONS ----------------
start_btn = ttk.Button(frame, text="▶ Start System", command=start_all)
start_btn.grid(row=0, column=0, padx=10)

stop_btn = ttk.Button(frame, text="⏹ Stop System", command=stop_all)
stop_btn.grid(row=0, column=1, padx=10)

clear_btn = ttk.Button(frame, text="🧹 Clear Logs", command=lambda: log_box.delete(1.0, tk.END))
clear_btn.grid(row=0, column=2, padx=10)

if IS_SILENT:
    log("🚀 Silent mode active — auto-starting Shakti Alert System...")
    start_all()
else:
    log("💡 Click 'Start System' to launch Flask, Ngrok & Main alert system.")
    root.mainloop()
