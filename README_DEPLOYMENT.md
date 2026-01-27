# 🚀 QUICK START GUIDE

## What's New?

Your app now runs frontend separately for Railway deployment, but still works perfectly locally!

---

## 🎯 Local Development (Choose One)

### Option 1: Automatic (Easiest)
**Windows:**
```bash
start_dev_windows.bat
```

**macOS/Linux:**
```bash
bash start_dev_linux.sh
```

**Result:** Backend + Frontend both start, all on default ports.

---

### Option 2: Manual (More Control - Recommended)

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

**Result:** Backend at `http://localhost:5000`, Frontend at `http://localhost:5173`

---

## 🌐 Railway Deployment

### Backend (One Railway Service)
- **Directory:** `shakti_alert/`
- **Build:** `cd shakti_alert && pip install -r requirements.txt`
- **Run:** `cd shakti_alert && python app.py`
- **Env Var:** `SHAKTI_AUTO_START_FRONTEND=false`

### Frontend (Another Railway Service)
- **Directory:** `frontend/`
- **Build:** `cd frontend && npm install && npm run build`
- **Run:** `cd frontend && npm run preview`
- **Env Var:** `VITE_API_BASE=https://your-backend-url.railway.app`

*(Replace the URL with your actual backend URL)*

---

## 📝 What Changed?

✅ **Good News:** Almost nothing!

- Frontend no longer auto-starts (can still enable it)
- Environment variable `SHAKTI_AUTO_START_FRONTEND` controls this
- All your code works exactly the same
- Database, APIs, features all unchanged

---

## 📚 Read More

For detailed information:
- **[FIX_SUMMARY.md](./FIX_SUMMARY.md)** ← Start here!
- **[DEPLOYMENT_SETUP.md](./DEPLOYMENT_SETUP.md)** - Complete setup guide
- **[RAILWAY_DEPLOYMENT.md](./RAILWAY_DEPLOYMENT.md)** - Railway specific steps

---

## ⚡ TL;DR

1. **Local Dev:** Run `start_dev_windows.bat` or start manually in two terminals
2. **Production:** Deploy backend and frontend as separate Railway services
3. **No Breaking Changes:** Everything still works!
4. **Set Backend URL:** In frontend, set `VITE_API_BASE=your-backend-url`

---

## 🎉 You're Ready!

Your project is now perfectly set up for Railway deployment while keeping local development smooth. 

**Next:** Read [RAILWAY_DEPLOYMENT.md](./RAILWAY_DEPLOYMENT.md) when you're ready to deploy.

