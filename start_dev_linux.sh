#!/bin/bash
# Start Shakti Alert - Development Mode with Auto-Start Frontend
# This script sets up the environment and starts both backend and frontend (macOS/Linux)

echo ""
echo "======================================================================"
echo "             SHAKTI ALERT - DEVELOPMENT MODE"
echo "======================================================================"
echo ""
echo "Starting Backend with Frontend Auto-Start..."
echo ""

# Set the environment variable to enable frontend auto-start
export SHAKTI_AUTO_START_FRONTEND=true

# Get the directory where this script is located
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

# Change to the shakti_alert directory
cd "$SCRIPT_DIR/shakti_alert"

# Check if Python is available
if ! command -v python3 &> /dev/null; then
    echo "ERROR: Python 3 is not installed"
    exit 1
fi

# Check if npm is available (required for frontend)
if ! command -v npm &> /dev/null; then
    echo "WARNING: npm is not installed or not in PATH"
    echo "Frontend will not auto-start. Install Node.js to enable it."
    echo ""
fi

# Run the backend (which will auto-start frontend if npm is available)
echo "Starting Flask Backend..."
echo "Backend: http://localhost:5000"
echo "Frontend: http://localhost:5173"
echo ""
echo "Press Ctrl+C to stop all servers"
echo ""

python3 app.py
