import "dotenv/config";
import express from "express";
import cors from "cors";
import { AccessToken } from "livekit-server-sdk";
import { createServer } from "http";
import { Server } from "socket.io";

const app = express();
app.use(cors());
app.use(express.json());

const LIVEKIT_API_KEY = process.env.LIVEKIT_API_KEY;
const LIVEKIT_API_SECRET = process.env.LIVEKIT_API_SECRET;

async function issueToken(req, res, room, identity) {
  if (!LIVEKIT_API_KEY || !LIVEKIT_API_SECRET) {
    return res.status(500).json({
      error:
        "Missing LIVEKIT_API_KEY / LIVEKIT_API_SECRET environment variables. Set them in PowerShell first.",
    });
  }
  if (!room || !identity) {
    return res.status(400).json({ error: "room and identity are required" });
  }

  console.log(`[token] room=${room} identity=${identity}`);

  const at = new AccessToken(LIVEKIT_API_KEY, LIVEKIT_API_SECRET, { identity });
  at.addGrant({
    room,
    roomJoin: true,
    canPublishData: true,
    canSubscribe: true,
  });

  try {
    const token = await at.toJwt();
    return res.json({ token });
  } catch (e) {
    return res.status(500).json({ error: `Failed to generate token: ${e?.message ?? e}` });
  }
}

// Create HTTP server for Socket.IO
const httpServer = createServer(app);
const io = new Server(httpServer, {
  cors: { origin: "*", methods: ["GET", "POST"] },
  transports: ["websocket", "polling"],
});

// ============================================
// Socket.IO Signaling Server
// ============================================

// Map uid -> socketId for user discovery
const userSockets = new Map(); // uid -> socketId
// Map callId -> { from, to, room, startedAt }
const activeCalls = new Map();

io.on("connection", (socket) => {
  console.log(`[socket] client connected: ${socket.id}`);

  // Handle user registration
  socket.on("register", (payload) => {
    try {
      const uid = payload?.uid;
      if (!uid) {
        console.error("[socket] register: no uid provided");
        return;
      }
      userSockets.set(uid, socket.id);
      console.log(`[socket] registered uid=${uid} -> socketId=${socket.id}`);
      socket.emit("register-ack", { uid, status: "ok" });
    } catch (e) {
      console.error("[socket] register error:", e);
    }
  });

  // Handle incoming call request
  socket.on("call-user", (payload) => {
    try {
      const { to, from, fromName } = payload;
      if (!to || !from) {
        console.error("[socket] call-user: missing to/from");
        return;
      }

      // Look up the target user's socket
      const toSocketId = userSockets.get(to);
      if (!toSocketId) {
        console.log(`[socket] call-user: target ${to} not online`);
        socket.emit("call-error", { reason: "target-offline" });
        return;
      }

      // Create a unique call ID
      const callId = `call-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;

      // Allocate a room for LiveKit
      const room = `room-${callId}`;

      // Store call metadata
      activeCalls.set(callId, {
        from,
        to,
        room,
        startedAt: Date.now(),
        fromSocketId: socket.id,
        toSocketId,
      });

      console.log(
        `[socket] call-user: from=${from} to=${to} callId=${callId} room=${room}`
      );

      // Send incoming-call to target
      io.to(toSocketId).emit("incoming-call", {
        callId,
        from,
        fromName,
        to,
        room,
      });

      // Acknowledge to caller
      socket.emit("call-initiated", { callId, room });
    } catch (e) {
      console.error("[socket] call-user error:", e);
    }
  });

  // Handle call acceptance
  socket.on("call-accepted", (payload) => {
    try {
      const { callId, from } = payload;
      if (!callId) {
        console.error("[socket] call-accepted: no callId");
        return;
      }

      const callData = activeCalls.get(callId);
      if (!callData) {
        console.error(`[socket] call-accepted: callId ${callId} not found`);
        return;
      }

      console.log(`[socket] call-accepted: callId=${callId} by ${from}`);

      // Send call-accepted back to the original caller
      io.to(callData.fromSocketId).emit("call-accepted", {
        callId,
        from: callData.to, // The receiver is now responding
        to: callData.from, // Original caller
        room: callData.room,
      });

      // Mark call as accepted
      callData.acceptedAt = Date.now();
      callData.accepted = true;
    } catch (e) {
      console.error("[socket] call-accepted error:", e);
    }
  });

  // Handle call rejection
  socket.on("call-rejected", (payload) => {
    try {
      const { callId, from } = payload;
      if (!callId) {
        console.error("[socket] call-rejected: no callId");
        return;
      }

      const callData = activeCalls.get(callId);
      if (!callData) {
        console.error(`[socket] call-rejected: callId ${callId} not found`);
        return;
      }

      console.log(`[socket] call-rejected: callId=${callId} by ${from}`);

      // Notify original caller
      io.to(callData.fromSocketId).emit("call-rejected", {
        callId,
        from: callData.to,
      });

      // Clean up
      activeCalls.delete(callId);
    } catch (e) {
      console.error("[socket] call-rejected error:", e);
    }
  });

  // Handle call termination
  socket.on("call-ended", (payload) => {
    try {
      const { callId, from } = payload;
      if (!callId) {
        console.error("[socket] call-ended: no callId");
        return;
      }

      const callData = activeCalls.get(callId);
      if (!callData) {
        console.error(`[socket] call-ended: callId ${callId} not found`);
        return;
      }

      console.log(`[socket] call-ended: callId=${callId} by ${from}`);

      // Notify other peer
      const otherPeer = from === callData.from ? callData.toSocketId : callData.fromSocketId;
      io.to(otherPeer).emit("call-ended", { callId });

      // Clean up
      activeCalls.delete(callId);
    } catch (e) {
      console.error("[socket] call-ended error:", e);
    }
  });

  // Handle disconnection
  socket.on("disconnect", () => {
    console.log(`[socket] client disconnected: ${socket.id}`);
    // Remove user from user map
    for (const [uid, sid] of userSockets.entries()) {
      if (sid === socket.id) {
        userSockets.delete(uid);
        console.log(`[socket] unregistered uid=${uid}`);
        break;
      }
    }
  });
});

// ============================================
// REST API for Token Generation
// ============================================

app.get("/health", (_req, res) => {
  res.json({ ok: true });
});

app.get("/token", async (req, res) => {
  const room = req.query?.room;
  const identity = req.query?.identity;
  return issueToken(req, res, room, identity);
});

app.post("/token", async (req, res) => {
  const { room, identity } = req.body ?? {};
  return issueToken(req, res, room, identity);
});

// Debug endpoint: returns token + decoded JWT header/payload (no signature verification)
app.post("/token-debug", async (req, res) => {
  const { room, identity } = req.body ?? {};

  if (!LIVEKIT_API_KEY || !LIVEKIT_API_SECRET) {
    return res.status(500).json({
      error:
        "Missing LIVEKIT_API_KEY / LIVEKIT_API_SECRET environment variables. Set them in PowerShell first.",
    });
  }
  if (!room || !identity) {
    return res.status(400).json({ error: "room and identity are required" });
  }

  const at = new AccessToken(LIVEKIT_API_KEY, LIVEKIT_API_SECRET, { identity });
  at.addGrant({
    room,
    roomJoin: true,
    canPublishData: true,
    canSubscribe: true,
  });

  let token;
  try {
    token = await at.toJwt();
  } catch (e) {
    return res.status(500).json({ error: `Failed to generate token: ${e?.message ?? e}` });
  }

  if (typeof token !== "string") {
    return res.status(500).json({ error: "Token is not a string (unexpected SDK behavior)." });
  }

  const [h, p] = token.split(".");
  if (!h || !p) {
    return res.status(500).json({ error: "Token does not look like a JWT." });
  }
  const decode = (part) => JSON.parse(Buffer.from(part, "base64url").toString("utf8"));

  res.json({
    token,
    decoded: {
      header: decode(h),
      payload: decode(p),
    },
    note: "decoded payload should show iss=LIVEKIT_API_KEY, sub=identity, video.room=room",
  });
});

const port = process.env.PORT ? Number(process.env.PORT) : 3000;
httpServer.listen(port, "0.0.0.0", () => {
  console.log(`\n✓ Token + Signaling server running on http://0.0.0.0:${port}`);
  console.log(`  - Socket.IO (WebSocket signaling) on ws://0.0.0.0:${port}`);
  console.log(`  - REST API for tokens on http://0.0.0.0:${port}/token`);
});

