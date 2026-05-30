# Quick Reference - Version 1 Architecture

## TL;DR: The Flow

```
SENDER:
User speaks → ASR → Translate → **Send TEXT** → Network

RECEIVER:
**Receive TEXT** → TTS Synthesis → AudioTrack → User hears voice
```

---

## Key Files

| Component | File | Responsibility |
|-----------|------|-----------------|
| **Speech Processing** | `CallPipelineManager.java` | ASR → Translate → Notify with TEXT |
| **Network** | `CallSessionManager.kt` | Send/Receive TEXT via LiveKit data channel |
| **Receiver Handler** | `CallActivity.java` | Listen for incoming TEXT → Call TTS |
| **TTS Engine** | `TtsEngine.java` | Synthesize TEXT to speech locally |
| **Language Config** | `LanguageCatalog.java` | Maps language names to codes/folders |

---

## Sender-Side (Speech Processing)

### **Pipeline Listener - Callba ck when translation is ready:**
```java
pipelineManager.setListener(new CallPipelineManager.PipelineListener() {
    @Override
    public void onLocalTranslation(String text) {
        // TEXT ready to send to remote peer
        sessionManager.sendTranslation(text);
    }
});
```

### **What happens in CallPipelineManager:**
```
ASR: "Namaste, kaise ho?"  (Hindi spoken)
     ↓
Translate: "नमस्ते, कैसे हो?"  (Hindi → Hindi... wait, example)
Actually: "नमस्ते, कैसे हो?" → (Hindi to English) → "Hello, how are you?"
     ↓
listener.onLocalTranslation("Hello, how are you?")
     ↓
NO TTS on sender side - just notify
```

---

## Network Transmission

### **Sending TEXT:**
```java
sessionManager.sendTranslation("Hello, how are you?");
```

**What it does internally:**
```java
fun sendTranslation(text: String) {
    publishData(JSONObject()
        .put("type", "translation")
        .put("text", text))
}
```

### **Receiving TEXT:**
```java
sessionManager.getIncomingTranslation().observe(this, text -> {
    handleRemoteTranslation(text);
});
```

---

## Receiver-Side (Local TTS)

### **Receive and synthesize:**
```java
private void handleRemoteTranslation(String translatedText) {
    // Get the language the remote peer is using
    if (remoteLang == null) return;
    
    // Create TTS engine for that language
    new Thread(() -> {
        try {
            TtsEngine tts = new TtsEngine(this, null, remoteLang.ttsFolder);
            tts.speak(translatedText);  // ← This synthesizes AND plays
        } catch (Exception e) {
            Log.e(TAG, "TTS Error: " + e.getMessage());
        }
    }).start();
}
```

### **What TtsEngine.speak() does:**
```
1. Load vocab: assets/tts/<lang>/tokens.txt
2. Load model: assets/tts/<lang>/model.onnx
3. Convert text to token IDs
4. Run ONNX inference
5. Get PCM float array output
6. Create AudioTrack with VOICE_COMMUNICATION usage
7. Write audio and play
```

---

## Language Configuration

### **How languages are defined:**
```java
new LangConfig(
    "Hindi",        // Display name
    "hi",           // ASR code (for speech recognition)
    "hin_Deva",     // Translation code
    "hin"           // TTS folder (in assets/tts/)
)
```

### **Finding a language:**
```java
LangConfig lang = LanguageCatalog.findByCode("hi");
// lang.name = "Hindi"
// lang.asrCode = "hi"
// lang.transCode = "hin_Deva"
// lang.ttsFolder = "hin"
```

---

## Important NOT-TO-DOs

❌ **DO NOT synthesize audio on sender side**
```java
// WRONG:
public void onLocalTranslation(String text) {
    TtsEngine tts = new TtsEngine(...);  // ← WRONG!
    tts.speak(text);                      // ← WRONG!
    sessionManager.sendTranslation(text);
}

// RIGHT:
public void onLocalTranslation(String text) {
    sessionManager.sendTranslation(text);  // ← Just send TEXT
}
```

❌ **DO NOT stream audio through LiveKit**
```java
// WRONG - Do NOT create audio tracks for translation
audioTrack.publish(...);  // ← WRONG!

// RIGHT - Use data channel only
sessionManager.sendTranslation(text);  // ← Text only
```

❌ **DO NOT load TTS on sender**
```java
// In CallPipelineManager.initialize():
// ✅ Correct: Only load ASR and Translator
asrEngine = modelManager.getAsrEngine(...);
translator = modelManager.getTranslator(...);
// NO TTS here - receiver will load it

// WRONG:
ttsEngine = modelManager.getTtsEngine(...);  // ← WRONG for sender!
```

---

## Deployment Checklist

- ✅ ASR models in `assets/asr/<lang>/`
- ✅ Translator model in `assets/trans/`
- ✅ TTS models in `assets/tts/<lang>/`
- ✅ LanguageCatalog updated with all language mappings
- ✅ Build successful: `./gradlew assembleDebug`
- ✅ No audio streaming through LiveKit
- ✅ Data channel used for TEXT only

---

## Testing the Flow

### **Step 1: User Speaks (Sender)**
```
Voice → Recorder → Pipeline
Check logs for: "[ASR] recognized: ..."
                "[TRANSLATE] Output: ..."
```

### **Step 2: Text Sent (Network)**
```
Check logs for: "[DATA] Published: type=translation size=..."
```

### **Step 3: Text Received (Receiver)**
```
Check logs for: "[DATA_RX] Received: type=translation"
                "[TEXT_RX] Translation: ..."
```

### **Step 4: TTS Synthesizes (Receiver)**
```
Check logs for: "[LOCAL_TTS] Synthesizing remote translation in: Hindi"
                "[LOCAL_TTS] Synthesis complete in XXXms"
Audio should play from speaker
```

---

## Common Issues & Fixes

| Issue | Cause | Fix |
|-------|-------|-----|
| No audio on receiver | TTS engine not loading | Check `remoteLang.ttsFolder` matches asset path |
| Text not transmitted | Data channel error | Check LiveKit connection, logs for "[DATA] Publish failed" |
| High latency | Waiting for user silence | Adjust VAD thresholds in CallPipelineManager |
| Wrong language audio | Language mismatch | Verify language exchange happened, check logs |
| Synthesis slow | Model loading | First call to TtsEngine is slower; subsequent calls faster |

---

## Performance Targets

- **Network bandwidth:** < 10KB/min (vs. 250KB+ for audio)
- **TTS latency:** 1-3 seconds
- **Total round-trip:** 2-4 seconds (ASR + Network + TTS)
- **Memory on receiver:** ~50MB (just TTS model)
- **Memory on sender:** ~500MB (ASR + Translator)

---

## Version 1 Vs Future Versions

| Aspect | V1 (Current) | V2 (Future) | V3 (Future) |
|--------|--------------|-----------|-----------|
| **Network Mode** | TEXT only | TEXT + optional audio stream | Audio + ML features |
| **Synthesis** | Local (receiver) | Selectable (local/remote) | Hybrid |
| **Languages** | 11 Indic | All supported | 100+ |
| **Hardware** | Works on any | GPU optional | GPU preferred |

---

**Status:** ✅ Ready for Production
**Build:** ✅ Successful
**Testing:** ✅ Complete

