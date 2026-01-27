@echo off
echo ========================================
echo Android Shakti Alert - Build Fix Script
echo ========================================
echo.

cd /d "d:\project sakti\android_shakti"

echo [1/4] Stopping Gradle Daemon...
call gradlew --stop
timeout /t 2 /nobreak >nul

echo.
echo [2/4] Cleaning project...
call gradlew clean
timeout /t 2 /nobreak >nul

echo.
echo [3/4] Building project...
call gradlew assembleDebug

echo.
echo [4/4] Build complete!
echo.
echo ========================================
echo Next steps:
echo 1. Open Android Studio
echo 2. Click File -^> Sync Project with Gradle Files
echo 3. Run the app on your device
echo ========================================
echo.
pause
