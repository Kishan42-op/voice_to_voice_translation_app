package com.example.indicpipeline;

import android.content.Context;
import android.util.Log;
import ai.onnxruntime.*;
import org.json.JSONObject;
import java.io.*;
import java.nio.LongBuffer;
import java.util.*;

public class OfflineTranslator {
    private OrtEnvironment env; // Shared
    private OrtSession encoderSession, decoderSession, lmHeadSession;
    private SentencePieceTokenizer tokenizer;
    private Map<String, Long> srcVocab;
    private Map<Long, String> tgtVocab;

    public OfflineTranslator(Context context, OrtEnvironment sharedEnv) throws Exception {
        env = sharedEnv; // USING SHARED ENV HERE
        tokenizer = new SentencePieceTokenizer();

        try {
            // Models moved under assets/trans/
            Log.d("OfflineTranslator", "Loading translation models...");
            String tokenizerPath = assetToCache(context, "trans/tokenizer.model");
            String encoderPath = assetToCache(context, "trans/encoder_quant.onnx");
            String decoderPath = assetToCache(context, "trans/decoder_quant.onnx");
            String lmPath = assetToCache(context, "trans/lm_head_quant.onnx");

            Log.d("OfflineTranslator", "Tokenizer: " + tokenizerPath);
            Log.d("OfflineTranslator", "Encoder: " + encoderPath + " (size: " + new File(encoderPath).length() + ")");
            Log.d("OfflineTranslator", "Decoder: " + decoderPath + " (size: " + new File(decoderPath).length() + ")");
            Log.d("OfflineTranslator", "LM Head: " + lmPath + " (size: " + new File(lmPath).length() + ")");

            srcVocab = loadVocab(context, "trans/dict.SRC.json");
            tgtVocab = loadReverseVocab(context, "trans/dict.TGT.json");

            tokenizer.loadModel(tokenizerPath);
            OrtSession.SessionOptions options = new OrtSession.SessionOptions();
            options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.BASIC_OPT);

            Log.d("OfflineTranslator", "Creating encoder session...");
            encoderSession = env.createSession(encoderPath, options);
            Log.d("OfflineTranslator", "Encoder session created successfully");

            Log.d("OfflineTranslator", "Creating decoder session...");
            decoderSession = env.createSession(decoderPath, options);
            Log.d("OfflineTranslator", "Decoder session created successfully");

            Log.d("OfflineTranslator", "Creating LM head session...");
            lmHeadSession = env.createSession(lmPath, options);
            Log.d("OfflineTranslator", "LM head session created successfully");
        } catch (Exception e) {
            Log.e("OfflineTranslator", "Error initializing translation models", e);
            throw new Exception("Failed to initialize OfflineTranslator: " + e.getMessage(), e);
        }
    }

    private Map<String, Long> loadVocab(Context context, String fileName) throws Exception {
        Map<String, Long> vocab = new HashMap<>();
        try (InputStream is = context.getAssets().open(fileName);
             Scanner scanner = new Scanner(is, "UTF-8").useDelimiter("\\A")) {
            JSONObject json = new JSONObject(scanner.hasNext() ? scanner.next() : "");
            Iterator<String> keys = json.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                vocab.put(key, json.getLong(key));
            }
        }
        return vocab;
    }

    private Map<Long, String> loadReverseVocab(Context context, String fileName) throws Exception {
        Map<Long, String> vocab = new HashMap<>();
        try (InputStream is = context.getAssets().open(fileName);
             Scanner scanner = new Scanner(is, "UTF-8").useDelimiter("\\A")) {
            JSONObject json = new JSONObject(scanner.hasNext() ? scanner.next() : "");
            Iterator<String> keys = json.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                vocab.put(json.getLong(key), key);
            }
        }
        return vocab;
    }

    private String assetToCache(Context context, String assetPath) throws IOException {
        // Keep cache filename stable even when assets are under subfolders.
        String cacheName = assetPath.replace("/", "_");
        // Use internal files directory instead of cache to avoid system clearing
        File filesDir = context.getFilesDir();
        File parentDir = new File(filesDir, "models");
        if (!parentDir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            parentDir.mkdirs();
        }
        File file = new File(parentDir, cacheName);

        // Check if file exists and has valid size
        if (file.exists() && file.length() > 0) {
            return file.getAbsolutePath();
        }

        // Copy asset to file with proper error handling
        try (InputStream is = context.getAssets().open(assetPath)) {
            long assetSize = is.available();
            if (assetSize == 0) {
                throw new IOException("Asset file is empty: " + assetPath);
            }

            File tempFile = new File(parentDir, cacheName + ".tmp");
            try (FileOutputStream os = new FileOutputStream(tempFile)) {
                byte[] buffer = new byte[65536]; // Use larger buffer for better performance
                int read;
                long totalWritten = 0;
                while ((read = is.read(buffer)) != -1) {
                    os.write(buffer, 0, read);
                    totalWritten += read;
                }
                os.flush(); // Ensure all data is written
                os.getFD().sync(); // Force sync to disk
            }

            // Validate file size matches expected asset size
            if (tempFile.length() != assetSize) {
                //noinspection ResultOfMethodCallIgnored
                tempFile.delete();
                throw new IOException("File copy incomplete. Expected: " + assetSize + ", Got: " + tempFile.length());
            }

            // Atomic rename to final location
            if (file.exists()) {
                //noinspection ResultOfMethodCallIgnored
                file.delete();
            }
            if (!tempFile.renameTo(file)) {
                //noinspection ResultOfMethodCallIgnored
                tempFile.delete();
                throw new IOException("Failed to rename temp file to: " + file.getAbsolutePath());
            }
        } catch (IOException e) {
            throw new IOException("Failed to copy asset '" + assetPath + "' to cache: " + e.getMessage(), e);
        }

        return file.getAbsolutePath();
    }

    public String translate(String text, String srcLang, String tgtLang) throws Exception {
        String unifiedText = ScriptConverter.convertToDevanagari(text, srcLang);
        String[] pieces = tokenizer.tokenizeAsPieces(unifiedText);

        long[] inputIds = new long[pieces.length + 3];
        long unkId = srcVocab.getOrDefault("<unk>", 3L);

        inputIds[0] = srcVocab.getOrDefault(srcLang, unkId);
        inputIds[1] = srcVocab.getOrDefault(tgtLang, unkId);
        for (int i = 0; i < pieces.length; i++) inputIds[i + 2] = srcVocab.getOrDefault(pieces[i], unkId);
        inputIds[inputIds.length - 1] = 2L;

        long[] attentionMask = new long[inputIds.length];
        Arrays.fill(attentionMask, 1L);

        long[] shape = {1, inputIds.length};
        OnnxTensor tInput = OnnxTensor.createTensor(env, LongBuffer.wrap(inputIds), shape);
        OnnxTensor tMask = OnnxTensor.createTensor(env, LongBuffer.wrap(attentionMask), shape);

        Map<String, OnnxTensor> encInputs = new HashMap<>();
        encInputs.put("input_ids", tInput);
        encInputs.put("attention_mask", tMask);

        float[][][] encoderHidden;
        try (OrtSession.Result encOut = encoderSession.run(encInputs)) {
            encoderHidden = (float[][][]) encOut.get(0).getValue();
        } finally { tInput.close(); tMask.close(); }

        List<Long> generated = new ArrayList<>();
        generated.add(2L);

        for (int i = 0; i < 256; i++) {
            long[] curInput = new long[generated.size()];
            for (int j = 0; j < generated.size(); j++) curInput[j] = generated.get(j);

            OnnxTensor decInput = OnnxTensor.createTensor(env, new long[][]{curInput});
            OnnxTensor encHiddenTensor = OnnxTensor.createTensor(env, encoderHidden);
            OnnxTensor encMaskTensor = OnnxTensor.createTensor(env, new long[][]{attentionMask});

            Map<String, OnnxTensor> decInputs = new HashMap<>();
            decInputs.put("decoder_input_ids", decInput);
            decInputs.put("encoder_hidden_states", encHiddenTensor);
            decInputs.put("encoder_attention_mask", encMaskTensor);

            try (OrtSession.Result decOut = decoderSession.run(decInputs)) {
                float[][][] dHidden = (float[][][]) decOut.get(0).getValue();
                float[][] lastToken = { dHidden[0][dHidden[0].length - 1] };

                OnnxTensor lmInput = OnnxTensor.createTensor(env, new float[][][]{lastToken});
                try (OrtSession.Result lmOut = lmHeadSession.run(Collections.singletonMap("decoder_hidden_states", lmInput))) {
                    float[][][] logits = (float[][][]) lmOut.get(0).getValue();
                    int nextToken = argmax(logits[0][0]);
                    if (nextToken == 2 && generated.size() > 1) break;
                    generated.add((long) nextToken);
                } finally { lmInput.close(); }
            } finally {
                decInput.close(); encHiddenTensor.close(); encMaskTensor.close();
            }
        }

        String[] outPieces = new String[generated.size() - 1];
        for (int i = 1; i < generated.size(); i++) {
            outPieces[i - 1] = tgtVocab.getOrDefault(generated.get(i), "<unk>");
        }

        String decodedDevanagari = tokenizer.decodePieces(outPieces);
        return ScriptConverter.convertFromDevanagari(decodedDevanagari, tgtLang);
    }

    private int argmax(float[] array) {
        int bestIdx = 0;
        float maxVal = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < array.length; i++) {
            if (array[i] > maxVal) { maxVal = array[i]; bestIdx = i; }
        }
        return bestIdx;
    }
}