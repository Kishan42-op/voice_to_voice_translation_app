package com.example.indicpipeline;

import android.content.Context;
import android.os.Environment;
import android.util.Log;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.util.List;

public class BatchEvaluator {

    private static final double POWER_KW = 0.005; // 5 Watts
    private static final double CARBON_INTENSITY_G_KWH = 714.0;

    // INTERFACE TO TALK TO THE UI
    public interface BenchmarkCallback {
        void onProgress(int currentFile, int totalFiles, String statusText);
        void onComplete(String finalMessage);
        void onError(String errorMsg);
    }

    private static double calculateCarbon(long timeMs) {
        double timeHours = timeMs / 3600000.0;
        return timeHours * POWER_KW * CARBON_INTENSITY_G_KWH;
    }

    public static void runBatchBenchmark(Context context, AsrEngine asrEngine, OfflineTranslator translator, List<LangConfig> allLangs, BenchmarkCallback callback) {
        // Benchmark functionality has been removed per project requirement.
        // Keep the API so callers won't fail; notify caller that the feature is unavailable.
        new Thread(() -> {
            if (callback != null) {
                callback.onError("Benchmark functionality has been removed.");
            }
        }).start();
    }

    private static LangConfig getLangConfigByName(String folderName, List<LangConfig> langs) {
        for (LangConfig lc : langs) {
            if (lc.name.equalsIgnoreCase(folderName)) return lc;
        }
        return null;
    }
}