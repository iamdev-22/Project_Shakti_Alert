@echo off
echo ========================================
echo Building ShaktiAlert Android App
echo ========================================
echo.

cd /d "d:\project sakti\android_shakti"

echo Cleaning previous builds...
call gradlew clean

echo.
echo Building debug APK...
call gradlew assembleDebug

echo.
echo ========================================
echo Build Complete!
echo ========================================
echo.
echo APK Location:
echo app\build\outputs\apk\debug\app-debug.apk
echo.
echo To install on device, run:
echo gradlew installDebug
echo.
pause
