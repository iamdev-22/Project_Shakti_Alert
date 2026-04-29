#!/usr/bin/env python
"""
DEBUG ALERT SYSTEM
Helps diagnose why alerts aren't being sent to guardians
Run this script to check all components
"""

import os
import sys
import json
import requests
from pathlib import Path

print("=" * 80)
print("SHAKTI ALERT SYSTEM - DEBUG CHECKER")
print("=" * 80)

# Add shakti_alert to path
sys.path.insert(0, str(Path(__file__).parent / "shakti_alert"))

# 1. Check config file
print("\n[1] CHECKING CONFIG FILE")
print("-" * 80)

config_file = Path("shakti_alert") / "config.json"
if config_file.exists():
    print(f"✅ Config file found: {config_file}")
    with open(config_file, 'r') as f:
        config = json.load(f)
    
    guardians = config.get("guardians", []) or config.get("emergency_contacts", [])
    print(f"   Guardians configured: {len(guardians)}")
    
    if guardians:
        for i, g in enumerate(guardians, 1):
            name = g.get("name", "Unknown")
            phone = g.get("phone", "NO PHONE")
            print(f"   {i}. {name}: {phone}")
    else:
        print("   ⚠️  NO GUARDIANS FOUND IN CONFIG!")
        print("   FIX: Add guardians in the app's WhatsApp Setup")
else:
    print(f"❌ Config file not found: {config_file}")
    print("   Create config.json with guardians list")

# 2. Check alert_system module
print("\n[2] CHECKING ALERT SYSTEM MODULE")
print("-" * 80)

try:
    from alert_system import send_whatsapp_text
    print("✅ alert_system module found")
    print("✅ send_whatsapp_text function available")
except ImportError as e:
    print(f"❌ Cannot import alert_system: {e}")

# 3. Check WhatsApp server
print("\n[3] CHECKING WHATSAPP SERVER")
print("-" * 80)

try:
    print("Checking if WhatsApp server is running on localhost:3001...")
    response = requests.get("http://localhost:3001/status", timeout=5)
    status_data = response.json()
    
    is_ready = status_data.get("isReady", False)
    status = status_data.get("status", "unknown")
    
    print(f"✅ WhatsApp server is running!")
    print(f"   Status: {status}")
    print(f"   Ready: {'YES' if is_ready else 'NO'}")
    
    if not is_ready:
        print("   ⚠️  Server is NOT ready yet (still initializing)")
        print("   Please wait a moment and try again")
    
except requests.exceptions.ConnectionError:
    print("❌ WhatsApp server is NOT running on localhost:3001")
    print("   FIX: Start WhatsApp server first:")
    print("   cd shakti_alert/wa_server && npm start")
except Exception as e:
    print(f"❌ Error checking server: {e}")

# 4. Test alert sending
print("\n[4] TESTING ALERT SENDING")
print("-" * 80)

try:
    from alert_system import send_whatsapp_text
    config_file = Path("shakti_alert") / "config.json"
    
    if config_file.exists():
        with open(config_file, 'r') as f:
            config = json.load(f)
        
        guardians = config.get("guardians", []) or config.get("emergency_contacts", [])
        
        if guardians:
            print(f"Attempting to send test message to {len(guardians)} guardian(s)...")
            
            test_message = "🚨 TEST ALERT - Shakti Debug Check (You can ignore this)"
            
            for guardian in guardians:
                phone = guardian.get("phone")
                name = guardian.get("name", "Unknown")
                
                if not phone:
                    print(f"   ⚠️  {name}: No phone number - SKIPPING")
                    continue
                
                try:
                    print(f"\n   Sending to {name} ({phone})...")
                    result = send_whatsapp_text(to=phone, body=test_message)
                    
                    if result:
                        print(f"   Status Code: {result.status_code}")
                        if result.status_code in [200, 201]:
                            print(f"   ✅ SUCCESS!")
                        else:
                            print(f"   ⚠️  Status: {result.status_code}")
                            if hasattr(result, 'text'):
                                print(f"   Response: {result.text}")
                    else:
                        print(f"   ❌ No response from server")
                except Exception as e:
                    print(f"   ❌ Error: {e}")
        else:
            print("❌ No guardians to test with")
            print("   Add guardians in config.json first")
    else:
        print("❌ Config file not found")
        
except Exception as e:
    print(f"❌ Cannot test alert: {e}")

# 5. Summary and recommendations
print("\n[5] SUMMARY & RECOMMENDATIONS")
print("-" * 80)

print("""
Common issues and fixes:

1. Guardians not sending:
   - Check if guardians are added in config.json
   - Verify phone numbers are correct (with country code)
   - Make sure WhatsApp server is running

2. WhatsApp server not running:
   - Start it with: cd shakti_alert/wa_server && npm start
   - Check port 3001 is not used by another app
   - Check for errors in server logs

3. Phone number format:
   - Should include country code: +919876543210
   - Not: 9876543210

4. Alert not sending (even with guardians):
   - Check app.py logs for error messages
   - Look for "ALERT SUMMARY" section in terminal
   - Verify guardians list is being loaded (should print when sending alert)

5. Duplicate alerts:
   - System prevents alerts within 20 seconds (by design)
   - Wait 20+ seconds between help commands

For more details, check:
- logs/app.log (application logs)
- logs/errors.log (error logs)
- Terminal output when sending alert
""")

print("=" * 80)
print("Debug check complete!")
print("=" * 80)
