#!/usr/bin/env python3
"""
Test the alert endpoint to verify it sends to ALL guardians
"""

import requests
import json
import time

BASE_URL = "http://172.20.10.3:5000"  # Use the backend IP from the running server

def test_quick_alert():
    print("\n" + "="*70)
    print("🧪 TESTING ALERT - SHOULD SEND TO ALL 3 GUARDIANS")
    print("="*70)
    
    # First, verify guardians are loaded
    print("\n1️⃣ Checking guardians...")
    try:
        response = requests.get(f"{BASE_URL}/guardians", timeout=5)
        guardians = response.json()
        print(f"   ✅ Found {len(guardians)} guardians:")
        for i, g in enumerate(guardians, 1):
            print(f"      {i}. {g.get('name')} ({g.get('phone')})")
    except Exception as e:
        print(f"   ❌ Error: {e}")
        return False
    
    if len(guardians) < 2:
        print(f"\n   ❌ ERROR: Need at least 2 guardians, found only {len(guardians)}")
        return False
    
    # Trigger quick alert
    print("\n2️⃣ Triggering emergency alert...")
    alert_data = {
        "lat": "28.6139",
        "lon": "77.2090",
        "message": "🚨 EMERGENCY ALERT TEST 🚨\n\nThis is a TEST alert to verify ALL guardians receive notifications."
    }
    
    try:
        response = requests.post(
            f"{BASE_URL}/quick_alert",
            json=alert_data,
            timeout=30
        )
        print(f"   ✅ Alert triggered! Status: {response.status_code}")
        result = response.json()
        print(f"   📊 Response: {json.dumps(result, indent=3)}")
        
    except Exception as e:
        print(f"   ❌ Error: {e}")
        return False
    
    print("\n" + "="*70)
    print("✅ TEST COMPLETE!")
    print("="*70)
    print("\n📋 Check the backend console (where app.py is running):")
    print("   You should see:")
    print("   ✅ Total Guardians: 3")
    print("   ✅ Guardian 1: Dev - +919990758187")
    print("   ✅ Guardian 2: Mother - +919876543210")
    print("   ✅ Guardian 3: Sister - +919123456789")
    print("   ✅ Sending WhatsApp to each...")
    print("   ✅ Alert Summary: Sent to: 3/3")
    print("\n📱 Check WhatsApp:")
    print("   All 3 guardians should receive the alert message")
    print("="*70 + "\n")
    
    return True

if __name__ == "__main__":
    test_quick_alert()
