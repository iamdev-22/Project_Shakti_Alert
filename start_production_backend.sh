#!/bin/bash
# Start Shakti Alert Backend Only (Production/Railway Mode)
# Frontend is NOT auto-started - it must be deployed separately (macOS/Linux)

echo ""
echo "======================================================================"
echo "          SHAKTI ALERT - BACKEND ONLY (Production Mode)"
echo "======================================================================"
echo ""
echo "Backend is starting WITHOUT frontend auto-start"
echo "This is the recommended setup for Railway/Production deployment"
echo ""

# Ensure frontend auto-start is disabled
export SHAKTI_AUTO_START_FRONTEND=false

# Get the directory where this script is located
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

# Change to the shakti_alert directory
cd "$SCRIPT_DIR/shakti_alert"

# Check if Python is available
if ! command -v python3 &> /dev/null; then
    echo "ERROR: Python 3 is not installed"
    exit 1
fi

# Run the backend
echo "Starting Flask Backend..."
echo "Backend: http://localhost:5000"
echo ""
echo "Frontend must be deployed separately to:"
echo "- Railway (recommended)"
echo "- Vercel"
echo "- Netlify"
echo "- Any other static hosting"
echo ""
echo "Configure VITE_API_BASE environment variable to point to this backend"
echo ""
echo "Press Ctrl+C to stop"
echo ""

python3 app.py
