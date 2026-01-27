# Shakti Alert - Deployment & Development Setup Guide

## Overview
Your project now supports:
- **Local Development**: Backend + Frontend running together
- **Production/Railway**: Backend and Frontend completely separated (deployed independently)

---

## 🚀 LOCAL DEVELOPMENT (Windows)

### Option 1: Start Everything (Backend + Frontend Auto-Start)

```bash
# Set environment variable to auto-start frontend
set SHAKTI_AUTO_START_FRONTEND=true

# Then run the backend (which will auto-start frontend)
python app.py
```

Or use the convenient script:
```bash
# Windows batch file (automatic)
start_dev_windows.bat
```

### Option 2: Manual Start (Recommended - More Control)

**Terminal 1 - Backend:**
```bash
cd shakti_alert
python app.py
```

**Terminal 2 - Frontend:**
```bash
cd frontend
npm run dev
```

Both will be available at:
- Backend: `http://localhost:5000`
- Frontend: `http://localhost:5173`

---

## 🌐 RAILWAY DEPLOYMENT (Production)

### Backend Setup

1. **Create Railway Project** - Backend
2. **Configure Environment Variables:**
   ```
   SHAKTI_AUTO_START_FRONTEND=false  (or just leave it unset)
   PORT=5000
   ```

3. **Build Command:**
   ```bash
   pip install -r requirements.txt
   ```

4. **Start Command:**
   ```bash
   python -m gunicorn -w 2 -b 0.0.0.0:$PORT "app:app"
   ```
   Or simply:
   ```bash
   python app.py
   ```

5. **Deploy Directory:** `shakti_alert/`

### Frontend Setup

1. **Create Railway Project** - Frontend
2. **Configure Environment Variables:**
   ```
   VITE_API_BASE=https://your-backend-url.railway.app
   ```

3. **Build Command:**
   ```bash
   npm run build
   ```

4. **Start Command:**
   ```bash
   npm run preview
   ```
   Or use static hosting (Vercel/Netlify) with:
   ```
   VITE_API_BASE=https://your-backend-url.railway.app npm run build
   ```

5. **Deploy Directory:** `frontend/`

---

## 📝 Environment Variables Reference

### Backend (.env in `shakti_alert/`)
```env
# Frontend Auto-Start Control (Development Only)
SHAKTI_AUTO_START_FRONTEND=false

# Railway Port Configuration
PORT=5000

# Gmail Configuration
gmail_user=your-email@gmail.com
gmail_app_password=your-app-password

# API Keys
gemini_api_key=your-gemini-key
```

### Frontend (.env in `frontend/`)
```env
# API Base URL
VITE_API_BASE=http://127.0.0.1:5000  (Development)
VITE_API_BASE=https://your-backend-url.railway.app  (Production)
```

---

## ✅ Testing Deployment Locally

### Simulate Railway Deployment (Local):

**Terminal 1 - Backend (Production Mode):**
```bash
cd shakti_alert
set PORT=5000
python app.py
```

**Terminal 2 - Frontend (Production Build):**
```bash
cd frontend
set VITE_API_BASE=http://localhost:5000
npm run build
npm run preview
```

Then visit: `http://localhost:4173`

---

## 🐛 Troubleshooting

### Frontend doesn't connect to backend
- Check `VITE_API_BASE` environment variable
- Verify backend is running and accessible
- Check browser console for CORS errors
- Ensure backend is serving on the correct port

### Auto-start not working in development
- Set: `SHAKTI_AUTO_START_FRONTEND=true`
- Ensure npm is installed: `npm --version`
- Frontend folder exists at: `../frontend/`

### Backend crashes on startup
- Check all required packages: `pip install -r requirements.txt`
- Verify database directory exists
- Check file permissions on uploads folder

---

## 🎯 Quick Reference

| Scenario | Command |
|----------|---------|
| Local Dev (Auto-start) | `start_dev_windows.bat` |
| Local Dev (Manual) | Backend: `python app.py` / Frontend: `cd frontend && npm run dev` |
| Production Build (Local) | `npm run build` in frontend, then deploy both |
| Railway Backend | Deploy `shakti_alert/` with `python app.py` |
| Railway Frontend | Deploy `frontend/` with API endpoint configured |

---

## 🚀 Deployment Checklist

- [ ] Backend environment variables configured (PORT, API keys)
- [ ] Frontend environment variables configured (VITE_API_BASE)
- [ ] Database migrations completed
- [ ] Static files served correctly
- [ ] API CORS headers configured
- [ ] SSL/HTTPS working
- [ ] Database backups enabled
- [ ] Monitoring/Logging set up

