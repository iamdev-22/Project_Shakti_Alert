@echo off
echo ========================================
echo FIXING BUILD ERROR - AGGRESSIVE CLEAN
echo ========================================
echo.

REM Set JAVA_HOME to Android Studio's bundled JDK
set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
set "PATH=%JAVA_HOME%\bin;%PATH%"

echo Closing any running Gradle daemons...
taskkill /F /IM java.exe 2>nul
timeout /t 3 /nobreak >nul

echo ========================================
echo Step 1: Deep Cleaning Gradle Cache
echo ========================================
echo Deleting corrupted transforms...
rmdir /s /q "%USERPROFILE%\.gradle\caches\8.13\transforms" 2>nul
rmdir /s /q "%USERPROFILE%\.gradle\caches\transforms-3" 2>nul
echo Deleting project .gradle...
cd /d "d:\project sakti\android_shakti"
rmdir /s /q .gradle 2>nul
rmdir /s /q build 2>nul
rmdir /s /q app\build 2>nul
echo Done!
echo.

echo ========================================
echo Step 2: Running Gradle Clean with Refresh
echo ========================================
call gradlew.bat clean --no-build-cache --refresh-dependencies --no-daemon
echo.

echo ========================================
echo Step 3: Building Debug APK
echo ========================================
call gradlew.bat assembleDebug --no-build-cache --no-configuration-cache --no-daemon --stacktrace
echo.

echo ========================================
echo BUILD COMPLETION CHECK
echo ========================================
if exist "app\build\outputs\apk\debug\app-debug.apk" (
    echo SUCCESS: APK created at app\build\outputs\apk\debug\app-debug.apk
) else (
    echo FAILURE: APK not found.
)
echo.
pause
