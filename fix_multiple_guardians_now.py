#!/usr/bin/env python3
"""
QUICK FIX: Add multiple guardians and verify alerts send to all
Run this in the project directory: python fix_multiple_guardians_now.py
"""

import json
import os
import sys
from datetime import datetime

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
CONFIG_PATH = os.path.join(BASE_DIR, "shakti_alert", "config.json")

def read_config():
    """Read current config"""
    if not os.path.exists(CONFIG_PATH):
        print(f"❌ Config not found at {CONFIG_PATH}")
        return None
    
    try:
        with open(CONFIG_PATH, 'r', encoding='utf-8') as f:
            return json.load(f)
    except Exception as e:
        print(f"❌ Error reading config: {e}")
        return None

def save_config(config):
    """Save config"""
    try:
        with open(CONFIG_PATH, 'w', encoding='utf-8') as f:
            json.dump(config, f, indent=4, ensure_ascii=False)
        print("✅ Config saved successfully")
        return True
    except Exception as e:
        print(f"❌ Error saving config: {e}")
        return False

def print_guardians(config):
    """Print current guardians"""
    guardians = config.get("guardians", []) or []
    print(f"\n📱 Current Guardians ({len(guardians)} total):")
    if not guardians:
        print("   ❌ NO GUARDIANS CONFIGURED")
    else:
        for i, g in enumerate(guardians, 1):
            print(f"   {i}. {g.get('name', 'Unknown'):20} | {g.get('phone')}")

def main():
    print("\n" + "="*70)
    print("🔧 SHAKTI ALERT - FIX MULTIPLE GUARDIANS")
    print("="*70)
    
    # Read current config
    print("\n1️⃣ Reading current configuration...")
    config = read_config()
    if not config:
        return False
    
    print(f"✅ Config loaded from: {CONFIG_PATH}")
    print_guardians(config)
    
    # Initialize guardians array if doesn't exist
    if "guardians" not in config or not isinstance(config.get("guardians"), list):
        print("\n⚠️ No 'guardians' array found. Creating new one...")
        config["guardians"] = []
    
    # Add guardians
    guardians_to_add = [
        {"name": "Dev Tomar", "phone": "+919990758187"},
        {"name": "Mother", "phone": "+919876543210"},
        {"name": "Sister", "phone": "+919123456789"}
    ]
    
    print("\n2️⃣ Adding test guardians...")
    
    for new_guardian in guardians_to_add:
        # Check if already exists
        exists = any(g.get("phone") == new_guardian["phone"] for g in config["guardians"])
        
        if exists:
            print(f"   ⏭️  Skipping {new_guardian['name']} (already exists)")
        else:
            config["guardians"].append({
                "name": new_guardian["name"],
                "phone": new_guardian["phone"],
                "added_at": datetime.now().isoformat()
            })
            print(f"   ✅ Added: {new_guardian['name']} ({new_guardian['phone']})")
    
    # Save config
    print("\n3️⃣ Saving configuration...")
    if not save_config(config):
        return False
    
    # Verify saved
    print("\n4️⃣ Verifying guardians were saved...")
    config_check = read_config()
    print_guardians(config_check)
    
    saved_count = len(config_check.get("guardians", []))
    if saved_count >= 2:
        print(f"\n✅ SUCCESS! {saved_count} guardians configured and saved!")
        print("\n📋 Next Steps:")
        print("   1. Restart your Flask backend: Ctrl+C and re-run app.py")
        print("   2. Trigger an emergency alert")
        print("   3. Check that ALL guardians receive WhatsApp alerts")
        print("   4. In backend console should show: '📊 Alert Summary: Sent to: X/X'")
        return True
    else:
        print(f"\n❌ ERROR! Only {saved_count} guardians saved (expected at least 2)")
        return False

if __name__ == "__main__":
    success = main()
    print("\n" + "="*70 + "\n")
    sys.exit(0 if success else 1)
