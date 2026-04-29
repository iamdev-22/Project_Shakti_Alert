#!/usr/bin/env python3
"""
Test script to add multiple guardians and verify they're saved
"""

import requests
import json
import time

BASE_URL = "http://localhost:5000"

def test_guardians():
    print("🧪 Testing Guardian Endpoints\n")
    print("=" * 60)
    
    # Test 1: Get current guardians
    print("\n1️⃣ GET Current Guardians:")
    try:
        response = requests.get(f"{BASE_URL}/guardians", timeout=5)
        guardians = response.json()
        print(f"✅ Response Status: {response.status_code}")
        print(f"📍 Current Guardians: {json.dumps(guardians, indent=2)}")
    except Exception as e:
        print(f"❌ Error: {e}")
        return False
    
    # Test 2: Add first guardian
    print("\n2️⃣ ADD Guardian 1 (+919990758187):")
    guardian1 = {
        "name": "Dev Tomar",
        "phone": "+919990758187"
    }
    try:
        response = requests.post(
            f"{BASE_URL}/set-guardian-phone",
            json=guardian1,
            timeout=5
        )
        print(f"✅ Response Status: {response.status_code}")
        result = response.json()
        print(f"📍 Response: {json.dumps(result, indent=2)}")
    except Exception as e:
        print(f"❌ Error: {e}")
    
    time.sleep(1)
    
    # Test 3: Add second guardian
    print("\n3️⃣ ADD Guardian 2 (+919876543210):")
    guardian2 = {
        "name": "Mother",
        "phone": "+919876543210"
    }
    try:
        response = requests.post(
            f"{BASE_URL}/set-guardian-phone",
            json=guardian2,
            timeout=5
        )
        print(f"✅ Response Status: {response.status_code}")
        result = response.json()
        print(f"📍 Response: {json.dumps(result, indent=2)}")
    except Exception as e:
        print(f"❌ Error: {e}")
    
    time.sleep(1)
    
    # Test 4: Get guardians again to verify both are saved
    print("\n4️⃣ GET Updated Guardians:")
    try:
        response = requests.get(f"{BASE_URL}/guardians", timeout=5)
        guardians = response.json()
        print(f"✅ Response Status: {response.status_code}")
        print(f"📍 Guardians Count: {len(guardians)}")
        for i, g in enumerate(guardians, 1):
            print(f"   Guardian {i}: {g.get('name')} -> {g.get('phone')}")
        
        if len(guardians) >= 2:
            print("\n✅ SUCCESS! Both guardians are saved!")
        else:
            print("\n❌ FAILED! Only 1 guardian saved. Check config.json")
        
    except Exception as e:
        print(f"❌ Error: {e}")
    
    # Test 5: Check config.json directly
    print("\n5️⃣ Checking config.json directly:")
    try:
        with open("d:/project sakti/shakti_alert/config.json", "r") as f:
            cfg = json.load(f)
            guardians = cfg.get("guardians", [])
            print(f"✅ Guardians in config: {len(guardians)}")
            for i, g in enumerate(guardians, 1):
                print(f"   {i}. {g.get('name')}: {g.get('phone')}")
    except Exception as e:
        print(f"❌ Error reading config: {e}")
    
    print("\n" + "=" * 60)

if __name__ == "__main__":
    test_guardians()
