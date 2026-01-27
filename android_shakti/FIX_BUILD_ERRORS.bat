@echo off
echo ========================================
echo COMPLETE BUILD ERROR FIX SCRIPT
echo ========================================
echo.
echo IMPORTANT: Close Android Studio before running this!
echo.
pause

echo Step 1: Cleaning Gradle caches...
rmdir /s /q "%USERPROFILE%\.gradle\caches" 2>nul
echo Gradle caches cleaned!

echo.
echo Step 2: Cleaning Android Studio caches...
rmdir /s /q "%USERPROFILE%\.android\build-cache" 2>nul
rmdir /s /q "%LOCALAPPDATA%\Google\AndroidStudio2024.2\caches" 2>nul
rmdir /s /q "%LOCALAPPDATA%\Google\AndroidStudio2024.1\caches" 2>nul
rmdir /s /q "%LOCALAPPDATA%\Google\AndroidStudio2023.3\caches" 2>nul
echo Android Studio caches cleaned!

echo.
echo Step 3: Cleaning project build directories...
cd /d "%~dp0"
rmdir /s /q "build" 2>nul
rmdir /s /q "app\build" 2>nul
rmdir /s /q ".gradle" 2>nul
echo Project build directories cleaned!

echo.
echo Step 4: Running Gradle clean...
call gradlew.bat clean --no-daemon
echo Gradle clean completed!

echo.
echo ========================================
echo CLEANUP COMPLETE!
echo ========================================
echo.
echo Now you can:
echo 1. Open Android Studio
echo 2. Open this project
echo 3. Click "Sync Project with Gradle Files"
echo 4. Wait for sync to complete
echo 5. Build the project
echo.
pause
