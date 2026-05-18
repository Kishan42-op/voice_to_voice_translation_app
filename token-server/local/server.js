import "dotenv/config";
import express from "express";
import cors from "cors";
import { AccessToken } from "livekit-server-sdk";

const app = express();
app.use(cors());
app.use(express.json());

const LIVEKIT_API_KEY = process.env.LIVEKIT_API_KEY;
const LIVEKIT_API_SECRET = process.env.LIVEKIT_API_SECRET;

app.get("/health", (_req, res) => {
  res.json({ ok: true });
});

app.post("/token", async (req, res) => {
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
    res.json({ token });
  } catch (e) {
    res.status(500).json({ error: `Failed to generate token: ${e?.message ?? e}` });
  }
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
app.listen(port, "0.0.0.0", () => {
  console.log(`Token server running on http://0.0.0.0:${port}`);
});

