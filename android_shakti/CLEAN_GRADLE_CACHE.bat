@echo off
setlocal
echo ========================================
echo FIXING GRADLE CACHE CORRUPTION (v2)
echo ========================================
echo.

echo 1. Stopping Gradle Daemons...
taskkill /F /IM java.exe 2>nul
taskkill /F /IM studio64.exe 2>nul
echo (Note: Android Studio might close, this is normal/good)
timeout /t 2 /nobreak >nul

echo.
echo 2. Deleting corrupted Kotlin DSL accessors...
rd /s /q "%USERPROFILE%\.gradle\caches\8.13\kotlin-dsl" 2>nul
rd /s /q "%USERPROFILE%\.gradle\caches\transforms-3" 2>nul
rd /s /q "%USERPROFILE%\.gradle\caches\jars-9" 2>nul
rd /s /q "%USERPROFILE%\.gradle\caches\modules-2" 2>nul
echo Done.

echo.
echo 3. Deleting project-local build cache...
cd /d "d:\project sakti\android_shakti"
rd /s /q .gradle 2>nul
rd /s /q build 2>nul
rd /s /q app\build 2>nul
echo Done.

echo.
echo ========================================
echo CLEANUP COMPLETE
echo ========================================
echo.
echo INSTRUCTIONS:
echo 1. Open Android Studio
echo 2. Wait for "Gradle Sync" to finish (it will redownload files).
echo 3. Click Build -> Rebuild Project.
echo.
pause
