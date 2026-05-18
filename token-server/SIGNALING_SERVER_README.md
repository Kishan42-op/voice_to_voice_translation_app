## Phase 5: Realtime WebSocket Signaling Server

This server provides **dual functionality**:
1. **Socket.IO Signaling** - realtime call signaling (incoming calls, accept/reject, room allocation)
2. **REST API** - LiveKit token generation

## Architecture

```
Socket.IO (Signaling)          REST API (Tokens)
├─ register                    ├─ GET /health
├─ call-user                   ├─ POST /token
├─ call-accepted               └─ POST /token-debug
├─ call-rejected
└─ call-ended
```

### Signaling Flow

```
Phone A (Caller)                    Backend Server                  Phone B (Callee)
    │                                    │                               │
    ├──── register(uid-a) ────────────>  │                               │
    │    uid-a → socketId-a              │                               │
    │                                    │                               │
    │                                    │  <──── register(uid-b) ────── │
    │                                    │         uid-b → socketId-b    │
    │                                    │                               │
    ├─ call-user(to:uid-b, from:uid-a) >│                               │
    │                              [allocate room: room-xxx]             │
    │                                    ├───── incoming-call ────────> │
    │  <───── call-initiated ──────────  │                               │
    │                                    │                               │
    │                                    │                    [User taps Accept]
    │                                    │                               │
    │  <───── call-accepted ──────────  │  <─── call-accepted(callId) ── │
    │         (room: room-xxx)           │                               │
    │                                    │                               │
    │ [Fetch LiveKit token]              │   [Fetch LiveKit token]       │
    │ [Join LiveKit room]                │   [Join LiveKit room]         │
    │                                    │                               │
    ├═══════════ Audio Stream (LiveKit) ═════════════════════════════════>
    │         <═══════════════════════════════════════════════════════   │
    │                                    │                               │
```

## Setup & Run

### 1) Install Dependencies

From the `token-server/` folder:

```bash
npm install
```

This installs:
- `express` - REST API framework
- `cors` - Cross-origin handling
- `dotenv` - Environment variables
- `socket.io` - WebSocket signaling
- `livekit-server-sdk` - Token generation

### 2) Set LiveKit Credentials

Edit `token-server/.env`:

```env
LIVEKIT_API_KEY=your-key
LIVEKIT_API_SECRET=your-secret
PORT=3000
```

**Where to find these:**
- Log in to [LiveKit Cloud](https://cloud.livekit.io)
- Navigate to your project
- Settings → API Key / Secret
- Copy both values

### 3) Run the Server

```bash
npm run start
```

Output:
```
✓ Token + Signaling server running on http://0.0.0.0:3000
  - Socket.IO (WebSocket signaling) on ws://0.0.0.0:3000
  - REST API for tokens on http://0.0.0.0:3000/token
```

### 4) Configure in Android App

Edit `App.java`:

```java
private static final String SOCKET_SERVER_URL = "http://<YOUR_PC_IP>:3000";
```

**Find your PC IP:**
- Windows:
  ```powershell
  ipconfig
  # Look for "IPv4 Address" on your WiFi adapter (e.g., 192.168.1.2)
  ```

### 5) Test Signaling

#### Option A: Two Real Android Phones

1. Connect both phones to the same WiFi network
2. Install the app on both
3. Log in with different Firebase accounts (e.g., user-a@test.com, user-b@test.com)
4. Open Friends list on Phone A
5. Press Call on Phone B's contact
6. Phone B should receive incoming call screen
7. Tap Accept → both should transition to call activity

#### Option B: Android Emulator + Physical Phone

1. Physical phone: connect to WiFi with IP e.g. 192.168.1.2
2. Emulator: connect to same WiFi or use Android Emulator's special IP `10.0.2.2` to reach host machine
3. Run server on host machine

### 6) Debug Connection Issues

**Check server is running:**
```bash
curl http://localhost:3000/health
# Should return: {"ok":true}
```

**Check Socket.IO connection:**
- Look in Android Logcat for:
  - `✓ Socket connected | socketId: ...`
  - `✓ Emitted register event for uid: ...`
  - `✗ Connect error: ...` (if connection fails)

**Common Issues:**

| Issue | Solution |
|-------|----------|
| `✗ Connect error: ...` | Check IP address in `App.java` matches your PC |
| No incoming-call event | Check both users are registered (see logcat) |
| Token fetch fails | Verify LiveKit API key/secret in `.env` |
| "target-offline" error | Ensure both phones have app open and registered |

## Signaling Events Reference

### Client → Server

#### `register`
Register authenticated user with backend.
```javascript
socket.emit("register", { uid: "user-id" });
```

#### `call-user`
Initiate an outgoing call.
```javascript
socket.emit("call-user", {
  to: "target-uid",
  from: "caller-uid",
  fromName: "Caller Name"
});
```

#### `call-accepted`
Accept an incoming call.
```javascript
socket.emit("call-accepted", {
  callId: "call-xxx",
  from: "receiver-uid"
});
```

#### `call-rejected`
Reject an incoming call.
```javascript
socket.emit("call-rejected", {
  callId: "call-xxx",
  from: "receiver-uid"
});
```

#### `call-ended`
End an active call.
```javascript
socket.emit("call-ended", {
  callId: "call-xxx",
  from: "ending-user-uid"
});
```

### Server → Client

#### `incoming-call`
Notifies callee of incoming call.
```javascript
socket.on("incoming-call", (data) => {
  console.log(data); // { callId, from, fromName, to, room }
});
```

#### `call-accepted`
Notifies caller that call was accepted.
```javascript
socket.on("call-accepted", (data) => {
  console.log(data); // { callId, from, to, room }
});
```

#### `call-rejected`
Notifies caller that call was rejected.
```javascript
socket.on("call-rejected", (data) => {
  console.log(data); // { callId, from }
});
```

#### `call-initiated`
Acknowledges successful call initiation.
```javascript
socket.on("call-initiated", (data) => {
  console.log(data); // { callId, room }
});
```

#### `register-ack`
Acknowledges successful registration.
```javascript
socket.on("register-ack", (data) => {
  console.log(data); // { uid, status: "ok" }
});
```

## Token API Reference

### POST /token

Generate a LiveKit token for a user to join a room.

**Request:**
```bash
curl -X POST http://localhost:3000/token \
  -H "Content-Type: application/json" \
  -d "{\"room\":\"call-room-1\",\"identity\":\"user-a\"}"
```

**Response:**
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

**Usage in Android:**
```java
String token = liveKitManager.fetchTokenSync(
  "http://192.168.1.2:3000",
  "room-id",
  "user-uid"
);
```

### POST /token-debug

Debug endpoint: returns token + decoded payload (helpful for troubleshooting).

**Request:**
```bash
curl -X POST http://localhost:3000/token-debug \
  -H "Content-Type: application/json" \
  -d "{\"room\":\"call-room-1\",\"identity\":\"user-a\"}"
```

**Response:**
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "decoded": {
    "header": { "alg": "HS256", "typ": "JWT" },
    "payload": {
      "iss": "API_KEY",
      "sub": "user-a",
      "video": { "room": "call-room-1", "roomJoin": true, ... },
      ...
    }
  }
}
```

## Production Deployment

### Deploy to Vercel (Recommended)

1. Push `token-server/` to GitHub
2. Create new project on [Vercel Dashboard](https://vercel.com)
3. Import GitHub repo
4. Add environment variables (Settings → Environment Variables):
   - `LIVEKIT_API_KEY`
   - `LIVEKIT_API_SECRET`
5. Deploy
6. Update Android app `App.java`:
   ```java
   private static final String SOCKET_SERVER_URL = "https://your-project.vercel.app";
   ```

### Deploy to Your Own Server

1. Clone repo on your server
2. Set environment variables:
   ```bash
   export LIVEKIT_API_KEY=your-key
   export LIVEKIT_API_SECRET=your-secret
   export PORT=3000
   ```
3. Run with process manager (e.g., pm2):
   ```bash
   npm install -g pm2
   pm2 start local/server.js --name "call-signaling"
   pm2 save
   pm2 startup
   ```

## Architecture Notes

- **Stateless Design**: Each server instance independently handles user registrations
- **Room Allocation**: Backend allocates a unique room per call (format: `room-{callId}`)
- **Socket ID Mapping**: Backend maps `uid → socketId` to route messages to correct client
- **Token Generation**: Tokens are generated on-demand and include room + identity grants

## Scaling Considerations (Future)

- **Redis Adapter**: For multi-server setups, use `@socket.io/redis-adapter`
- **Database**: Store call history in Firestore or PostgreSQL
- **Metrics**: Add Prometheus/StatsD for monitoring
- **FCM**: Integrate Firebase Cloud Messaging for push notifications to offline users

