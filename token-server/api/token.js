import { AccessToken } from "livekit-server-sdk";

export default async function handler(req, res) {
  if (req.method !== "POST") {
    res.setHeader("Allow", "POST");
    return res.status(405).json({ error: "Use POST" });
  }

  const LIVEKIT_API_KEY = process.env.LIVEKIT_API_KEY;
  const LIVEKIT_API_SECRET = process.env.LIVEKIT_API_SECRET;

  if (!LIVEKIT_API_KEY || !LIVEKIT_API_SECRET) {
    return res.status(500).json({
      error: "Missing LIVEKIT_API_KEY / LIVEKIT_API_SECRET in Vercel env vars",
    });
  }

  const { room, identity } = req.body ?? {};
  if (!room || !identity) {
    return res.status(400).json({ error: "room and identity are required" });
  }

  try {
    const at = new AccessToken(LIVEKIT_API_KEY, LIVEKIT_API_SECRET, { identity });
    at.addGrant({
      room,
      roomJoin: true,
      canPublishData: true,
      canSubscribe: true,
    });

    const token = await at.toJwt();
    return res.status(200).json({ token });
  } catch (e) {
    return res
      .status(500)
      .json({ error: `Failed to generate token: ${e?.message ?? e}` });
  }
}

