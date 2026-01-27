# Railway.app Deployment Configuration Guide

## Overview
This guide helps you deploy Shakti Alert on Railway as TWO separate services:
1. **Backend Service** (Flask/Python)
2. **Frontend Service** (React/Vite)

---

## Step 1: Deploy Backend Service

### 1.1 Create Backend Project on Railway

1. Go to [Railway.app](https://railway.app)
2. Click **"New Project"** → **"Deploy from GitHub"** (or create blank)
3. Select your repository

### 1.2 Configure Backend Service

**Build Command:**
```bash
cd shakti_alert && pip install -r requirements.txt
```

**Start Command:**
```bash
cd shakti_alert && python app.py
```

**Port:** Leave default (Railway will auto-set via PORT env variable)

**Environment Variables:**
```
SHAKTI_AUTO_START_FRONTEND=false
PORT=5000
PYTHONUNBUFFERED=1

# Add your configuration from shakti_alert/config.json:
# (Copy these values from your config.json)
gmail_user=your-email@gmail.com
gmail_app_password=your-app-password
gemini_api_key=your-api-key
```

**Volume/Database:**
- If using SQLite, mount a volume for `shakti_alert/database/`
- Path: `/app/shakti_alert/database`

### 1.3 Get Backend URL

After deployment, Railway will show you a URL like:
```
https://shakti-alert-backend-prod.up.railway.app
```

**Copy this URL** - you'll need it for frontend configuration.

---

## Step 2: Deploy Frontend Service

### 2.1 Create Frontend Project on Railway

1. Click **"New Project"** → **"Deploy from GitHub"**
2. Select your repository (same as backend)

### 2.2 Configure Frontend Service

**Build Command:**
```bash
cd frontend && npm install && npm run build
```

**Start Command:**
```bash
cd frontend && npm run preview
```

**Port:** Leave default

**Environment Variables:**
```
VITE_API_BASE=https://shakti-alert-backend-prod.up.railway.app
NODE_ENV=production
```

*(Replace the URL with your actual backend URL from Step 1.3)*

---

## Step 3: Connect Frontend to Backend

### 3.1 Update Frontend Configuration

Your frontend code should use the `VITE_API_BASE` environment variable. Make sure your API calls use it:

**Example (JavaScript):**
```javascript
const API_BASE = import.meta.env.VITE_API_BASE || 'http://localhost:5000';

// API calls:
fetch(`${API_BASE}/api/endpoint`)
```

### 3.2 Configure CORS (Backend)

The backend's CORS is already configured to accept requests from anywhere:
```python
CORS(app, resources={r"/*": {
    "origins": "*",
    "methods": ["GET", "POST", "PUT", "DELETE", "OPTIONS"],
}})
```

✅ **This is already done in your app.py**

---

## Step 4: Testing

### Test Backend
```
https://your-backend-url.railway.app/api/health
(Should return JSON response)
```

### Test Frontend
```
https://your-frontend-url.railway.app
(Should load React app and connect to backend)
```

### Monitor Logs
1. Go to Railway dashboard
2. Select service (Backend or Frontend)
3. Click **"Logs"** to view real-time output

---

## Step 5: Environment Variables from config.json

Copy these from `shakti_alert/config.json` and add to Railway Backend:

```
gmail_user=devtomar452@gmail.com
gmail_app_password=wzjvesqoyymiqrww
gemini_api_key=AIzaSyCi9Ojixe8wpA9Vhy8UJCCFBrtj-7WZWPg
tracking_url=
waba_id=865491459160781
phone_number_id=867161659810934
whatsapp_permanent_token=EAAVEh1FW0tYBPx0kdVc1sdAVaaUTDoEpl94rY5SPooPypUB9J3UGv9ZCE30yGGYNV08fzlsFqdZCVWUubcH4RHhoPP8CyQgwyClxA4yhSQGBQXaTlYzZC6UAr37XCzOj4VfguDupjAxNPt3q0AjpU1xdkkP2oGtHU2RD4fhcLZB4ZAjZA7mnAVOmbIp2drBYBZBIceoPcx2KwuDNuIJEZCHHfUul8J7ZAF8cWLTgyZA3GdpDypnRaJ907PumBBBwecmuzWjrszvVFVZCp8ZBMVlez2NWrjuG
```

*(Be careful with secrets - use Railway's secret management, not in code)*

---

## Step 6: Troubleshooting

### Frontend can't connect to backend
- Check `VITE_API_BASE` environment variable is correct
- Verify backend service is running
- Check browser console for CORS errors
- Wait 2-3 minutes for Railway deployment to complete

### Backend crashes on startup
- Check database directory exists
- Verify all dependencies installed: `pip install -r requirements.txt`
- Check error logs in Railway dashboard

### Port conflicts
- Railway auto-assigns ports, no manual configuration needed
- If PORT env var is set, Flask will use it

### WhatsApp/Email features not working
- Verify all API keys and credentials in environment variables
- Check that credentials are correct from config.json
- Test API connectivity from backend logs

---

## Step 7: Local Testing (Before Railway Deployment)

Test your deployment configuration locally:

**Terminal 1 - Backend (Simulate Railway)**
```bash
set PORT=5000
set SHAKTI_AUTO_START_FRONTEND=false
cd shakti_alert
python app.py
```

**Terminal 2 - Frontend (Simulate Railway)**
```bash
set VITE_API_BASE=http://localhost:5000
cd frontend
npm run build
npm run preview  # This runs on http://localhost:4173
```

Visit `http://localhost:4173` and verify frontend connects to backend.

---

## Step 8: Production Checklist

- [ ] Backend environment variables configured
- [ ] Frontend VITE_API_BASE set correctly
- [ ] Database backup enabled
- [ ] API keys stored securely (use Railway's secret manager)
- [ ] Logs being monitored
- [ ] Both services running and connected
- [ ] HTTPS/SSL working automatically (Railway provides this)
- [ ] Rate limiting/security headers configured if needed

---

## Important Notes

1. **Frontend Auto-Start is DISABLED**: Set `SHAKTI_AUTO_START_FRONTEND=false` in backend environment variables
2. **No Breaking Changes**: Your current local development still works
3. **Database**: If using SQLite, you must mount a persistent volume
4. **Secrets**: Never commit `.env` files or API keys to Git
5. **Database Migrations**: Run any needed migrations in Railway console if needed

---

## Useful Railway Commands

Get backend service logs:
```bash
railway logs --service backend
```

View environment variables:
```bash
railway env
```

Deploy manually:
```bash
railway up
```

---

## Support

If deployment fails:
1. Check Railway dashboard logs
2. Verify environment variables
3. Test backend: `GET /api/health` (or similar endpoint)
4. Test CORS: Check browser network tab
5. Check Python version matches requirements

Good luck with your deployment! 🚀

