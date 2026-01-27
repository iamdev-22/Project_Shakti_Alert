@echo off
REM Start Shakti Alert Backend Only (Production/Railway Mode)
REM Frontend is NOT auto-started - it must be deployed separately

echo.
echo ======================================================================
echo          SHAKTI ALERT - BACKEND ONLY (Production Mode)
echo ======================================================================
echo.
echo Backend is starting WITHOUT frontend auto-start
echo This is the recommended setup for Railway/Production deployment
echo.

REM Ensure frontend auto-start is disabled
set SHAKTI_AUTO_START_FRONTEND=false

REM Change to the shakti_alert directory
cd /d "%~dp0shakti_alert"

REM Check if Python is available
python --version >nul 2>&1
if errorlevel 1 (
    echo ERROR: Python is not installed or not in PATH
    pause
    exit /b 1
)

REM Run the backend
echo Starting Flask Backend...
echo Backend: http://localhost:5000
echo.
echo Frontend must be deployed separately to:
echo - Railway (recommended)
echo - Vercel
echo - Netlify
echo - Any other static hosting
echo.
echo Configure VITE_API_BASE environment variable to point to this backend
echo.
echo Press Ctrl+C to stop
echo.
python app.py

pause
