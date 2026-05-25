package com.example.indicpipeline.models;

import android.content.Context;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.indicpipeline.AsrEngine;
import com.example.indicpipeline.LangConfig;
import com.example.indicpipeline.OfflineTranslator;
import com.example.indicpipeline.TtsEngine;
import com.example.indicpipeline.language.LanguageCatalog;

import java.util.HashMap;
import java.util.Map;

import ai.onnxruntime.OrtEnvironment;

/**
 * Singleton manager to preload and cache AI models.
 * Ensures models are "warm" before a call starts.
 */
public class GlobalModelManager {
    private static final String TAG = "MODEL_MANAGER";
    private static GlobalModelManager instance;

    private final Map<String, AsrEngine> asrCache = new HashMap<>();
    private final Map<String, TtsEngine> ttsCache = new HashMap<>();
    private final Map<String, OfflineTranslator> transCache = new HashMap<>();
    
    private OrtEnvironment sharedEnv;
    private final MutableLiveData<String> status = new MutableLiveData<>("Idle");
    private boolean isPreloadingText = false;

    private GlobalModelManager() {
        try {
            sharedEnv = OrtEnvironment.getEnvironment();
        } catch (Exception e) {
            Log.e(TAG, "Failed to get OrtEnvironment", e);
        }
    }

    public static synchronized GlobalModelManager getInstance() {
        if (instance == null) {
            instance = new GlobalModelManager();
        }
        return instance;
    }

    public LiveData<String> getStatus() {
        return status;
    }

    /**
     * Preloads models for the user's preferred language.
     */
    public void preloadLocalModels(Context context, LangConfig lang) {
        if (lang == null) return;
        new Thread(() -> {
            try {
                Log.i(TAG, "Preloading local models for: " + lang.name);
                
                status.postValue("Loading " + lang.name + " ASR...");
                getAsrEngine(context, lang.asrCode);

                status.postValue("Loading " + lang.name + " TTS...");
                getTtsEngine(context, lang.ttsFolder);

                status.postValue(lang.name + " models ready");
                Log.i(TAG, "Local models preloaded.");
            } catch (Exception e) {
                Log.e(TAG, "Preload failed", e);
                status.postValue("Preload Error");
            }
        }).start();
    }

    public synchronized AsrEngine getAsrEngine(Context context, String asrCode) throws Exception {
        if (!asrCache.containsKey(asrCode)) {
            asrCache.put(asrCode, new AsrEngine(context, sharedEnv));
        }
        return asrCache.get(asrCode);
    }

    public synchronized TtsEngine getTtsEngine(Context context, String ttsFolder) throws Exception {
        if (!ttsCache.containsKey(ttsFolder)) {
            ttsCache.put(ttsFolder, new TtsEngine(context, sharedEnv, ttsFolder));
        }
        return ttsCache.get(ttsFolder);
    }

    public synchronized OfflineTranslator getTranslator(Context context) throws Exception {
        // Current OfflineTranslator seems to be generic/multilingual in its constructor
        // and language-specific in its translate() call.
        if (transCache.isEmpty()) {
            transCache.put("default", new OfflineTranslator(context, sharedEnv));
        }
        return transCache.get("default");
    }

    public void cleanup() {
        for (AsrEngine e : asrCache.values()) e.close();
        for (TtsEngine e : ttsCache.values()) {
            try { e.close(); } catch (Exception ignored) {}
        }
        for (OfflineTranslator e : transCache.values()) e.close();
        asrCache.clear();
        ttsCache.clear();
        transCache.clear();
    }
}
