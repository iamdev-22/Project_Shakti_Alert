@echo off
REM This script updates the server URL in your Android app without rebuilding

echo ========================================
echo SHAKTI ALERT - SERVER URL FIX
echo ========================================
echo.
echo This will update your app to use the correct server URL
echo without needing to rebuild the app.
echo.

REM Check if ADB is available
adb version >nul 2>&1
if %errorlevel% neq 0 (
    echo ERROR: ADB not found!
    echo.
    echo Please do this manually instead:
    echo 1. Open Shakti Alert app
    echo 2. Go to Settings or Login screen
    echo 3. Change Server URL to: http://192.168.1.42:5000
    echo 4. Save and restart app
    pause
    exit /b 1
)

echo Checking for connected devices...
adb devices

echo.
echo Updating server URL in app preferences...
adb shell "run-as com.example.shaktialert sh -c 'echo \"<?xml version='1.0' encoding='utf-8' standalone='yes' ?><map><string name=\\\"server_url\\\">http://192.168.1.42:5000</string></map>\" > /data/data/com.example.shaktialert/shared_prefs/shakti_prefs.xml'"

if %errorlevel% equ 0 (
    echo.
    echo ========================================
    echo SUCCESS! Server URL Updated
    echo ========================================
    echo.
    echo Now:
    echo 1. Close Shakti Alert app completely
    echo 2. Reopen the app
    echo 3. Test the alert
    echo.
) else (
    echo.
    echo ========================================
    echo MANUAL UPDATE REQUIRED
    echo ========================================
    echo.
    echo Please do this on your phone:
    echo 1. Open Shakti Alert app
    echo 2. Find Server URL setting
    echo 3. Change to: http://192.168.1.42:5000
    echo 4. Save and restart app
    echo.
)

pause
