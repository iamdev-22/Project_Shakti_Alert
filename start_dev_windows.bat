@echo off
REM Start Shakti Alert - Development Mode with Auto-Start Frontend
REM This script sets up the environment and starts both backend and frontend

echo.
echo ======================================================================
echo             SHAKTI ALERT - DEVELOPMENT MODE
echo ======================================================================
echo.
echo Starting Backend with Frontend Auto-Start...
echo.

REM Set the environment variable to enable frontend auto-start
set SHAKTI_AUTO_START_FRONTEND=true

REM Change to the shakti_alert directory
cd /d "%~dp0shakti_alert"

REM Check if Python is available
python --version >nul 2>&1
if errorlevel 1 (
    echo ERROR: Python is not installed or not in PATH
    pause
    exit /b 1
)

REM Check if npm is available (required for frontend)
npm --version >nul 2>&1
if errorlevel 1 (
    echo WARNING: npm is not installed or not in PATH
    echo Frontend will not auto-start. Install Node.js to enable it.
    echo.
)

REM Run the backend (which will auto-start frontend if npm is available)
echo Starting Flask Backend...
echo Backend: http://localhost:5000
echo Frontend: http://localhost:5173
echo.
echo Press Ctrl+C to stop all servers
echo.
python app.py

pause
