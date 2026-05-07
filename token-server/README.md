## LiveKit token server (run on your PC)

This server generates LiveKit tokens securely using your **LiveKit Cloud API Key** and **API Secret**.

## Deploy to Vercel (recommended for public access)

Vercel uses **serverless functions**, so this repo includes Vercel endpoints:

- `GET /api/health`
- `POST /api/token`

### 1) Push this `token-server/` folder to GitHub

Make sure you do **not** commit `.env`.

### 2) Import into Vercel

- Vercel Dashboard → **Add New Project**
- Select your GitHub repo
- Framework preset: **Other**

### 3) Set environment variables in Vercel

Vercel Project → **Settings → Environment Variables**

- `LIVEKIT_API_KEY`
- `LIVEKIT_API_SECRET`

Deploy again after setting them.

### 4) Test after deploy

- `https://<your-project>.vercel.app/api/health` → `{"ok":true}`

### 5) Use from Android

In the Android app, set **Token Server URL** to:

- `https://<your-project>.vercel.app/api`

(The app appends `/token` automatically.)

### 1) Install Node.js

Install Node.js LTS from the official site. Then restart your terminal.

### 2) Install dependencies

From this folder (`token-server/`):

```bash
npm install
```

### 3) Set your LiveKit Cloud API keys

#### Option A (recommended): Use a `.env` file (works every time)

1. Copy `.env.example` → `.env`
2. Edit `token-server/.env` and paste your values:

```env
LIVEKIT_API_KEY=PASTE_KEY_HERE
LIVEKIT_API_SECRET=PASTE_SECRET_HERE
PORT=3000
```

#### Option B: Set environment variables in PowerShell (only for that terminal)

In PowerShell (replace values) **in the same terminal where you will run `npm run start`**:

```powershell
$env:LIVEKIT_API_KEY="PASTE_YOUR_KEY"
$env:LIVEKIT_API_SECRET="PASTE_YOUR_SECRET"
```

### 4) Start server

```bash
npm run start
```

It will print something like:

`Token server running on http://0.0.0.0:3000`

### 4.1) Confirm your API key/secret are correct (important)

If you see LiveKit error **"could not fetch region settings: 401"**, it almost always means:

- **Your LiveKit URL and your API key/secret are from different LiveKit Cloud projects**, or
- The URL is typed wrong.

Run this debug request from your laptop:

```bash
curl -X POST http://127.0.0.1:3000/token-debug -H "Content-Type: application/json" -d "{\"room\":\"call1\",\"identity\":\"A\"}"
```

In the JSON output, check:

- `decoded.payload.iss` must equal your **LIVEKIT_API_KEY**
- `decoded.payload.video.room` must equal `"call1"`
- `decoded.payload.sub` (or identity field) should be `"A"`

If `iss` does not match the API key shown in your LiveKit Cloud project (for your LiveKit URL), copy the correct key/secret from that project and restart the server.

### 5) Use from Android

In the Android app, set:

- **LiveKit URL**: `wss://indicpipelineapp-0vui3jrn.livekit.cloud`
- **Token Server URL**: `http://<YOUR_PC_LAN_IP>:3000`
- **Room ID**: e.g. `call1` (same on both phones)
- **Your User ID**: `A` on phone-1, `B` on phone-2

The app will request tokens automatically.

