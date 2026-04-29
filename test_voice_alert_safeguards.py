#!/usr/bin/env python3
"""
Test script to verify voice alert safeguards
Run this to test the no-duplicate-alert system
"""

import requests
import json
import time

BASE_URL = "http://127.0.0.1:5000"

def test_voice_alert_idempotency():
    """Test that duplicate alerts are rejected"""
    print("\n" + "="*70)
    print("TEST 1: Idempotency - Same token rejected")
    print("="*70)
    
    token = f"test-token-{int(time.time())}"
    payload = {
        "alert_token": token,
        "trigger_type": "voice_help"
    }
    
    # Send first alert
    print(f"\n✓ Sending first alert with token: {token}")
    resp1 = requests.post(f"{BASE_URL}/trigger_voice_alert", json=payload)
    print(f"  Response 1: {resp1.status_code} - {resp1.json().get('status')}")
    
    # Try to send same token again immediately
    print(f"\n✓ Sending DUPLICATE alert with same token...")
    resp2 = requests.post(f"{BASE_URL}/trigger_voice_alert", json=payload)
    print(f"  Response 2: {resp2.status_code} - {resp2.json().get('status')}")
    
    if resp2.status_code == 400 and resp2.json().get('status') == 'duplicate_rejected':
        print("  ✅ PASS: Duplicate token correctly rejected!")
        return True
    else:
        print("  ❌ FAIL: Duplicate was not rejected")
        return False


def test_rate_limiting():
    """Test that rapid alerts are rate limited"""
    print("\n" + "="*70)
    print("TEST 2: Rate Limiting - Max 1 alert per 15 seconds")
    print("="*70)
    
    # Send first alert with unique token
    token1 = f"test-token-{int(time.time())}-1"
    print(f"\n✓ Sending first alert...")
    resp1 = requests.post(f"{BASE_URL}/trigger_voice_alert", json={
        "alert_token": token1,
        "trigger_type": "voice_help"
    })
    print(f"  Response 1: {resp1.status_code} - {resp1.json().get('status')}")
    
    # Try to send another alert immediately (different token)
    token2 = f"test-token-{int(time.time())}-2"
    print(f"\n✓ Sending second alert IMMEDIATELY with different token...")
    resp2 = requests.post(f"{BASE_URL}/trigger_voice_alert", json={
        "alert_token": token2,
        "trigger_type": "voice_help"
    })
    print(f"  Response 2: {resp2.status_code} - {resp2.json().get('status')}")
    
    if resp2.status_code == 429 and resp2.json().get('status') == 'rate_limited':
        print("  ✅ PASS: Rate limiting working!")
        print(f"  💬 Message: {resp2.json().get('message')}")
        return True
    else:
        print("  ❌ FAIL: Rate limiting not applied")
        return False


def test_successful_alert():
    """Test that a valid alert goes through"""
    print("\n" + "="*70)
    print("TEST 3: Valid Alert - Should process successfully")
    print("="*70)
    
    # Wait 16 seconds to bypass rate limit from previous test
    print("\n⏳ Waiting 16 seconds for rate limit window to pass...")
    for i in range(16, 0, -1):
        print(f"  {i}s remaining...", end='\r')
        time.sleep(1)
    print("  Ready!      ")
    
    token = f"test-token-{int(time.time())}"
    print(f"\n✓ Sending alert with fresh token: {token}")
    resp = requests.post(f"{BASE_URL}/trigger_voice_alert", json={
        "alert_token": token,
        "trigger_type": "voice_help"
    })
    print(f"  Response: {resp.status_code} - {resp.json().get('status')}")
    
    if resp.status_code == 200 and resp.json().get('status') == 'ok':
        print("  ✅ PASS: Valid alert processed successfully!")
        print(f"  WhatsApp sent: {resp.json().get('whatsapp_sent')}")
        return True
    else:
        print("  ❌ FAIL: Valid alert was not processed")
        return False


if __name__ == "__main__":
    print("\n" + "╔" + "="*68 + "╗")
    print("║" + " "*15 + "🎤 VOICE ALERT SAFEGUARD TESTS" + " "*21 + "║")
    print("╚" + "="*68 + "╝")
    
    results = []
    
    try:
        results.append(("Idempotency Guard", test_voice_alert_idempotency()))
        results.append(("Rate Limiting", test_rate_limiting()))
        results.append(("Valid Alert Processing", test_successful_alert()))
    except Exception as e:
        print(f"\n❌ ERROR: {e}")
        print("Make sure Flask backend is running: python app.py")
    
    # Summary
    print("\n" + "="*70)
    print("TEST SUMMARY")
    print("="*70)
    
    passed = sum(1 for _, result in results if result)
    total = len(results)
    
    for test_name, result in results:
        status = "✅ PASS" if result else "❌ FAIL"
        print(f"{status:8} {test_name}")
    
    print(f"\n{passed}/{total} tests passed")
    
    if passed == total:
        print("🎉 All safeguards working correctly!")
    else:
        print("⚠️  Some tests failed - check backend logs")
