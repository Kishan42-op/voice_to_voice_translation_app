# GEMINI.md

# Realtime Voice Translation App

Production style Android realtime voice calling and translation application.

---

# Tech Stack

## Android
- Java
- Android SDK
- ViewModel
- LiveData / StateFlow
- Notifications
- Foreground services planned later

## Backend
- Node.js
- Socket.IO
- Railway deployment

## Authentication & Database
- Firebase Authentication
- Firestore

## Realtime Media
- LiveKit Cloud

## Future AI Pipeline
- ASR (Speech Recognition)
- Translation
- TTS
- Multilingual voice pipeline

---

# Current Infrastructure

## Signaling Server

Railway deployment:

```txt
https://voicetovoicetranslationapp-production.up.railway.app
```

Responsibilities:
- socket connection
- user registration
- uid → socketId mapping
- outgoing call routing
- incoming call signaling
- call accept/reject flow

---

## LiveKit

Using LiveKit Cloud.

Current URL:

```kotlin
public static final String LIVEKIT_URL =
    "wss://indicpipelineapp-0vui3jrn.livekit.cloud";
```

Responsibilities:
- realtime audio
- room connection
- participants
- media streaming

---

# Existing Features

Already implemented:

- Firebase login/signup
- user profiles
- Firestore integration
- Socket.IO signaling
- outgoing call flow
- incoming call flow
- LiveKit room joining
- incoming call notifications
- Android 13 notification permission handling
- profile persistence

---

# Planned Features

Future roadmap:

- realtime speech recognition
- realtime translation
- multilingual conversations
- translated captions
- translated audio
- adaptive language model loading

IMPORTANT:
Do NOT optimize for translation pipeline yet.

Current focus:
stabilize realtime calling.

---

# Translation Pipeline Integration Update

---

# Current Development Phase

We are now entering:

```txt
feat/integrate-translation-pipeline
```

IMPORTANT:
Core calling/signaling stabilization remains priority.

Translation integration should be incremental and MUST preserve existing stable call behavior.

DO NOT massively rewrite architecture.

---

# Translation Pipeline Goal

We already have:
- language selection implemented
- user preferred language stored
- old translation pipeline implemented inside previous MainActivity version

Goal:
Integrate the EXISTING translation pipeline into the NEW realtime call architecture.

DO NOT rebuild translation pipeline from scratch unless necessary.

---

# User Language Flow

Each user already selects their language during signup/profile setup.

Examples:

User A:
- Hindi

User B:
- Gujarati

During call:

```txt
User A speaks Hindi
→ ASR runs on User A device
→ text translated Hindi → Gujarati
→ translated output/audio sent to User B
→ User B hears Gujarati
```

Reverse direction:

```txt
User B speaks Gujarati
→ translated Gujarati → Hindi
→ translated output/audio sent to User A
→ User A hears Hindi
```

IMPORTANT:
Translation should primarily happen on sender side.

Receiver should receive translated media/output.

---

# Existing Translation Pipeline

Old implementation exists in:
- previous MainActivity version

Need to inspect and reuse:
- ASR flow
- translation flow
- TTS flow
- model loading
- audio processing
- pipeline threading/coroutines

DO NOT unnecessarily rewrite working pipeline logic.

---

# Translation Integration Requirements

Need integration with:
- current signaling flow
- current LiveKit flow
- current call lifecycle

Pipeline must coexist with:
- outgoing call flow
- incoming call flow
- LiveKit room
- mute/speaker controls
- realtime connection states

---

# Current UI Requirements

Existing call UI already exists.

Need improvements.

---

# Updated Call UI Layout

## Top Section
Show:
- participant name
- participant language
- connection state
- call timer after connected

---

## Middle Section

Realtime translation/transcription area.

Need clean modern message-style layout.

Show:

### Local User
```txt
You said:
<recognized text>

Translated:
<translated outgoing text>
```

### Remote User
```txt
Received:
<translated incoming text>
```

Need:
- realtime updates
- readable typography
- good spacing
- modern design
- smooth scrolling if needed

IMPORTANT:
This section is core to translation experience.

---

## Bottom Controls

Improve UI/UX for:

- mute
- speaker
- end/decline call

Requirements:
- better spacing
- cleaner icons
- polished appearance
- modern call interface feel

Avoid over-design.

---

# Translation Processing Expectations

Need to identify:
- where microphone audio is captured
- where ASR runs
- where translation runs
- where TTS/audio synthesis runs
- how translated output is transmitted

Need clear architecture understanding BEFORE modifying heavily.

---

# Performance Requirements

IMPORTANT:
Avoid:
- blocking UI thread
- heavy processing on main thread
- unnecessary allocations

Use:
- coroutines
- background threads
- proper lifecycle-safe processing

---

# Logging Requirements

Add detailed logs for translation pipeline.

Prefixes:

```txt
[ASR]
[TRANSLATE]
[TTS]
[PIPELINE]
[LIVEKIT]
```

Need logs for:
- speech recognition started
- speech recognized
- translation started
- translation completed
- TTS started
- TTS completed
- translated media sent
- translated media received
- pipeline latency
- errors/failures

---

# Important Constraints

1. Preserve stable signaling flow

2. Preserve stable LiveKit connection flow

3. Do NOT massively rewrite architecture

4. Reuse old MainActivity pipeline logic wherever possible

5. Keep implementation incremental

6. Translation integration should NOT break call lifecycle

7. Avoid introducing unstable state management

---

# Development Order

## Step 1
Inspect old MainActivity translation implementation.

Understand:
- ASR
- translation
- TTS
- threading
- media flow

---

## Step 2
Explain existing pipeline architecture BEFORE coding.

---

## Step 3
Identify integration points with:
- current call flow
- LiveKit
- audio lifecycle

---

## Step 4
Implement minimal working translation pipeline inside active calls.

Goal:
stable working prototype first.

---

## Step 5
Improve call UI incrementally.

---

# Important Architecture Direction

Current priority:
- stable calling
- stable signaling
- stable translation flow

NOT:
- over-abstraction
- over-engineering
- unnecessary rewrites

---

# Final Expected Experience

User A:
- speaks in their own language
- sees recognized + translated text
- translated output sent automatically

User B:
- receives translated audio/text in their own language

Both users:
- stay in stable realtime call
- see clean modern call UI
- see realtime translated conversation updates

Core expectation:
Realtime multilingual conversation experience similar to futuristic live translation calling apps.

# IMPORTANT DIRECTION

DO NOT:
- over-engineer
- massively rewrite architecture
- aggressively refactor lifecycle
- introduce huge abstractions right now

Goal:
restore a stable core calling flow FIRST.

Stabilize before optimizing.

---



# Backend Expectations

Backend MUST log:

- user registered
- uid mapped to socketId
- outgoing call routed
- receiver socket found
- accept routed back

Need verification:
- mappings not stale
- socketIds valid
- offline emitted correctly
- Try to write easy code , not much complex
---

# Debugging Philosophy

Prefer:
- incremental fixes
- debugging-first approach
- stabilization first

Avoid:
- rewriting entire architecture
- premature optimization
- huge abstractions

---

# Current Goal

Restore reliable realtime calling similar to:

- WhatsApp
- Telegram
- Google Meet

Core expectations:

- caller stays on call screen
- receiver reliably receives calls
- accept flow works
- connected state works
- no fake offline
- no fake declined
- no random exits
- no stale listeners
- no duplicate callbacks

Stability first.
Optimization later.

---

# Important Files To Inspect

Likely relevant areas:

- SocketManager
- SignalingRepository
- CallActivity
- IncomingCallActivity
- NotificationManager
- LiveKit room listeners
- socket listener registration
- cleanup/endCall methods
- backend socket mapping logic
- asr files,tts directory (with the main activity directory onl)

---

# Required Debugging Process

## Step 1
Add detailed logs everywhere.

## Step 2
Trace exact event order.

## Step 3
Identify stale listener duplication.

## Step 4
Verify registration completes before calling.

## Step 5
Verify backend routes events correctly.

## Step 6
Fix premature cleanup.

## Step 7
Stabilize connected state.

DO NOT skip steps.

