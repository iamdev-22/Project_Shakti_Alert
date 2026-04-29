#!/usr/bin/env python3
"""
Test the GLOBAL ALERT LOCK to prevent multiple alerts being sent.
This test verifies:
1. Only ONE alert processes at a time (atomic)
2. 20-second cooldown between alerts is enforced globally
3. Duplicate tokens are rejected
"""

import requests
import json
import time
import threading
from datetime import datetime

BASE_URL = "http://localhost:5000"

def log(msg, status=""):
    """Pretty print with timestamp"""
    timestamp = datetime.now().strftime("%H:%M:%S.%f")[:-3]
    emoji = {
        "PASS": "✅",
        "FAIL": "❌",
        "INFO": "ℹ️",
        "WARN": "⚠️",
        "LOCK": "🔒",
        "WAIT": "⏳",
        "CHECK": "🔍",
    }.get(status, "→")
    print(f"[{timestamp}] {emoji} {msg}")

def send_alert(token, delay=0):
    """Send a single alert and return response"""
    if delay > 0:
        log(f"Waiting {delay}s before sending alert", "WAIT")
        time.sleep(delay)
    
    try:
        log(f"Sending alert with token: {token}", "CHECK")
        response = requests.post(
            f"{BASE_URL}/trigger_voice_alert",
            json={
                "alert_token": token,
                "trigger_type": "voice_help"
            },
            timeout=5
        )
        
        data = response.json()
        status_code = response.status_code
        
        if status_code == 200:
            log(f"✓ Alert sent (token: {token[:20]}...)", "PASS")
            return {"token": token, "status": "sent", "code": 200, "data": data}
        elif status_code == 429:
            log(f"↻ Rate limited (token: {token[:20]}...): {data.get('message')}", "WARN")
            return {"token": token, "status": "rate_limited", "code": 429, "data": data}
        else:
            log(f"✗ Error {status_code} (token: {token[:20]}...)", "FAIL")
            return {"token": token, "status": "error", "code": status_code, "data": data}
    except Exception as e:
        log(f"✗ Exception: {e}", "FAIL")
        return {"token": token, "status": "exception", "code": 0, "error": str(e)}

def test_1_single_alert():
    """Test 1: Single alert should succeed"""
    log("=" * 70, "INFO")
    log("TEST 1: Single Alert (Should Succeed)", "INFO")
    log("=" * 70, "INFO")
    
    token = f"test1-{int(time.time()*1000)}"
    result = send_alert(token)
    
    if result["code"] == 200:
        log("✅ TEST 1 PASSED: Alert sent successfully", "PASS")
        return True, result
    else:
        log("❌ TEST 1 FAILED: Alert should have succeeded", "FAIL")
        return False, result

def test_2_rapid_duplicates():
    """Test 2: Duplicate tokens sent rapidly should be rejected"""
    log("\n" + "=" * 70, "INFO")
    log("TEST 2: Rapid Duplicate Tokens (Should Be Rejected)", "INFO")
    log("=" * 70, "INFO")
    
    token = f"test2-{int(time.time()*1000)}"
    
    log("Sending first alert...", "CHECK")
    result1 = send_alert(token)
    
    log("Immediately sending same token again...", "CHECK")
    result2 = send_alert(token)
    
    if result1["code"] == 200 and result2["code"] == 400:
        log("✅ TEST 2 PASSED: First succeeded, duplicate rejected", "PASS")
        return True, (result1, result2)
    else:
        log(f"❌ TEST 2 FAILED: Expected (200, 400) got ({result1['code']}, {result2['code']})", "FAIL")
        return False, (result1, result2)

def test_3_concurrent_alerts():
    """Test 3: Multiple concurrent requests should be serialized by lock"""
    log("\n" + "=" * 70, "INFO")
    log("TEST 3: Concurrent Alerts (Should Be Serialized by Lock)", "INFO")
    log("=" * 70, "INFO")
    
    tokens = [f"concurrent-{int(time.time()*1000)}-{i}" for i in range(3)]
    results = []
    
    def send_concurrent(token, results_list):
        result = send_alert(token, delay=0)
        results_list.append(result)
    
    log(f"Sending 3 alerts concurrently...", "CHECK")
    threads = []
    for token in tokens:
        t = threading.Thread(target=send_concurrent, args=(token, results))
        threads.append(t)
        t.start()
    
    for t in threads:
        t.join()
    
    # Count successes and rate limits
    successes = sum(1 for r in results if r["code"] == 200)
    rate_limited = sum(1 for r in results if r["code"] == 429)
    
    log(f"Results: {successes} succeeded, {rate_limited} rate-limited", "CHECK")
    
    # Global lock should allow only 1 to succeed, others rate-limited or locked
    if successes == 1 and (rate_limited > 0 or len(results) - successes > 0):
        log("✅ TEST 3 PASSED: Global lock serialized requests", "PASS")
        return True, results
    else:
        log(f"❌ TEST 3 FAILED: Expected 1 success + rate limits, got {successes} success(es)", "FAIL")
        return False, results

def test_4_cooldown_enforcement():
    """Test 4: Second alert within 20 seconds should be rejected"""
    log("\n" + "=" * 70, "INFO")
    log("TEST 4: 20-Second Cooldown Enforcement", "INFO")
    log("=" * 70, "INFO")
    
    token1 = f"cooldown1-{int(time.time()*1000)}"
    
    log("Sending first alert...", "CHECK")
    result1 = send_alert(token1)
    
    if result1["code"] != 200:
        log("❌ TEST 4 FAILED: First alert should succeed", "FAIL")
        return False, (result1, None)
    
    log("Waiting 2 seconds before sending second alert...", "WAIT")
    time.sleep(2)
    
    token2 = f"cooldown2-{int(time.time()*1000)}"
    log("Sending second alert (within 20s window)...", "CHECK")
    result2 = send_alert(token2)
    
    if result2["code"] == 429:
        retry_after = result2["data"].get("next_alert_available_in", 20)
        log(f"✅ TEST 4 PASSED: Second alert rejected with 20s cooldown", "PASS")
        return True, (result1, result2)
    else:
        log(f"❌ TEST 4 FAILED: Expected 429, got {result2['code']}", "FAIL")
        return False, (result1, result2)

def run_all_tests():
    """Run all tests and summarize"""
    log("\n" + "🧪" * 35, "INFO")
    log("GLOBAL ALERT LOCK TEST SUITE", "INFO")
    log("🧪" * 35 + "\n", "INFO")
    
    tests = [
        ("Single Alert", test_1_single_alert),
        ("Duplicate Tokens", test_2_rapid_duplicates),
        ("Concurrent Alerts", test_3_concurrent_alerts),
        ("Cooldown Enforcement", test_4_cooldown_enforcement),
    ]
    
    results = []
    
    for test_name, test_func in tests:
        try:
            passed, data = test_func()
            results.append((test_name, passed))
        except Exception as e:
            log(f"❌ Test crashed: {e}", "FAIL")
            results.append((test_name, False))
        
        time.sleep(1)  # Space between tests
    
    # Print summary
    log("\n" + "=" * 70, "INFO")
    log("TEST SUMMARY", "INFO")
    log("=" * 70, "INFO")
    
    passed_count = sum(1 for _, p in results if p)
    total_count = len(results)
    
    for test_name, passed in results:
        status = "✅ PASS" if passed else "❌ FAIL"
        log(f"{status}: {test_name}", "CHECK")
    
    log(f"\nTotal: {passed_count}/{total_count} tests passed", "INFO")
    
    if passed_count == total_count:
        log("🎉 ALL TESTS PASSED! Global alert lock is working correctly.", "PASS")
    else:
        log(f"⚠️ {total_count - passed_count} test(s) failed. Review above for details.", "WARN")
    
    return passed_count == total_count

if __name__ == "__main__":
    try:
        # Check server is running
        log("Checking server connection...", "CHECK")
        response = requests.get(f"{BASE_URL}/health", timeout=2)
        if response.status_code == 200:
            log("✅ Server is running", "PASS")
        else:
            log(f"⚠️ Server responded with {response.status_code}", "WARN")
    except:
        log("❌ Cannot connect to server at {BASE_URL}", "FAIL")
        log("Make sure to run: python -m shakti_alert.app", "INFO")
        exit(1)
    
    # Run tests
    all_passed = run_all_tests()
    
    log("\n" + "=" * 70, "INFO")
    if all_passed:
        log("✅ Voice Alert System is secure against multiple alerts!", "PASS")
    else:
        log("❌ Voice Alert System needs review", "FAIL")
    log("=" * 70, "INFO")
