@echo off
echo ================================================
echo    Shakti Alert - Android Build Script
echo ================================================
echo.

echo [1/5] Setting JAVA_HOME...
set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr
set PATH=%JAVA_HOME%\bin;%PATH%
echo JAVA_HOME set to: %JAVA_HOME%
echo.

echo [2/5] Stopping Gradle Daemon...
call gradlew --stop
echo.

echo [3/5] Cleaning project...
call gradlew clean
if %errorlevel% neq 0 (
    echo ERROR: Clean failed!
    pause
    exit /b %errorlevel%
)
echo.

echo [4/5] Building Debug APK...
call gradlew assembleDebug
if %errorlevel% neq 0 (
    echo ERROR: Build failed!
    pause
    exit /b %errorlevel%
)
echo.

echo [5/5] Build Complete!
echo ================================================
echo.
echo APK Location:
echo %CD%\app\build\outputs\apk\debug\app-debug.apk
echo.
echo ================================================
echo Next Steps:
echo 1. Install APK on your device
echo 2. Or run: gradlew installDebug
echo ================================================
echo.
pause
