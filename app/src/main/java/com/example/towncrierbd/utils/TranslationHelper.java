package com.example.towncrierbd.utils;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * TranslationHelper — MyMemory Free Translation API
 *
 * ✅ 100% Free — no API key required
 * ✅ Supports Bangla ↔ English (bn|en)
 * ✅ Auto-detects language direction
 * ✅ In-memory cache to avoid duplicate API calls
 * ✅ Rate limit: ~5000 words/day for anonymous use
 *
 * Usage:
 *   TranslationHelper.translate("আমার সবজি আছে", result -> {
 *       tvTitle.setText(result);
 *   });
 */
public class TranslationHelper {

    private static final String TAG = "TranslationHelper";

    // MyMemory free API endpoint
    private static final String API_URL =
            "https://api.mymemory.translated.net/get?q=%s&langpair=%s";

    // Simple in-memory cache: "text|langpair" → translated text
    private static final Map<String, String> cache = new HashMap<>();

    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface TranslateCallback {
        void onResult(String translatedText);
        void onError(String originalText); // fallback to original on error
    }

    /**
     * Detect language and translate:
     * - If text contains Bangla characters → translate to English
     * - If text is English/Latin → translate to Bangla
     */
    public static void translate(String text, TranslateCallback callback) {
        if (text == null || text.trim().isEmpty()) {
            if (callback != null) callback.onResult(text);
            return;
        }

        String langPair = detectLangPair(text);
        translateWithLangPair(text, langPair, callback);
    }

    /**
     * Force translate Bangla → English
     */
    public static void toBengali(String text, TranslateCallback callback) {
        translateWithLangPair(text, "en|bn", callback);
    }

    /**
     * Force translate English → Bangla
     */
    public static void toEnglish(String text, TranslateCallback callback) {
        translateWithLangPair(text, "bn|en", callback);
    }

    /**
     * Core translation method with caching
     */
    public static void translateWithLangPair(String text, String langPair,
                                             TranslateCallback callback) {
        if (text == null || text.trim().isEmpty()) {
            if (callback != null) callback.onResult(text);
            return;
        }

        String trimmed = text.trim();
        String cacheKey = trimmed + "|" + langPair;

        // Return cached result immediately
        if (cache.containsKey(cacheKey)) {
            if (callback != null) callback.onResult(cache.get(cacheKey));
            return;
        }

        // Network call on background thread
        new Thread(() -> {
            String result = callMyMemory(trimmed, langPair);
            if (result != null && !result.isEmpty()) {
                cache.put(cacheKey, result);
                mainHandler.post(() -> {
                    if (callback != null) callback.onResult(result);
                });
            } else {
                mainHandler.post(() -> {
                    if (callback != null) callback.onError(trimmed); // fallback
                });
            }
        }).start();
    }

    /**
     * Translate multiple fields together to reduce API calls.
     * Joins fields with " | " separator, translates once, then splits back.
     *
     * @param fields    Array of strings to translate
     * @param langPair  e.g. "bn|en"
     * @param callback  Returns array of translated strings (same length as input)
     */
    public static void translateFields(String[] fields, String langPair,
                                       MultiFieldCallback callback) {
        if (fields == null || fields.length == 0) {
            if (callback != null) callback.onResult(fields);
            return;
        }

        // Build combined text
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) sb.append(" || ");
            sb.append(fields[i] == null ? "" : fields[i].trim());
        }

        String combined = sb.toString();
        String cacheKey = combined + "|" + langPair;

        if (cache.containsKey(cacheKey)) {
            String cached = cache.get(cacheKey);
            if (callback != null) callback.onResult(splitResult(cached, fields.length));
            return;
        }

        new Thread(() -> {
            String result = callMyMemory(combined, langPair);
            if (result != null && !result.isEmpty()) {
                cache.put(cacheKey, result);
                String[] parts = splitResult(result, fields.length);
                mainHandler.post(() -> {
                    if (callback != null) callback.onResult(parts);
                });
            } else {
                mainHandler.post(() -> {
                    if (callback != null) callback.onResult(fields); // fallback to originals
                });
            }
        }).start();
    }

    public interface MultiFieldCallback {
        void onResult(String[] translatedFields);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Detect whether text is primarily Bangla or English
     * Bangla Unicode range: U+0980–U+09FF
     */
    public static boolean isBangla(String text) {
        if (text == null) return false;
        int banglaCount = 0;
        for (char c : text.toCharArray()) {
            if (c >= 0x0980 && c <= 0x09FF) banglaCount++;
        }
        return banglaCount > text.length() * 0.2; // >20% Bangla chars
    }

    public static String detectLangPair(String text) {
        return isBangla(text) ? "bn|en" : "en|bn";
    }

    /**
     * Split combined translated text back into fields.
     * MyMemory may not preserve " || " exactly, so we try both " || " and "||"
     */
    private static String[] splitResult(String result, int expectedCount) {
        String[] parts = result.split("\\s*\\|\\|\\s*");
        if (parts.length >= expectedCount) return parts;

        // Fallback: return result in first slot
        String[] fallback = new String[expectedCount];
        for (int i = 0; i < expectedCount; i++) {
            fallback[i] = i < parts.length ? parts[i].trim() : "";
        }
        return fallback;
    }

    /**
     * Actual HTTP call to MyMemory API
     * Returns translated string or null on error
     */
    private static String callMyMemory(String text, String langPair) {
        try {
            // Limit to 500 chars per request (MyMemory limit per segment)
            String truncated = text.length() > 500 ? text.substring(0, 500) : text;
            String encoded = URLEncoder.encode(truncated, "UTF-8");
            String urlStr = String.format(API_URL, encoded, langPair);

            HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestProperty("User-Agent", "TownCrierBD/1.0");

            int code = conn.getResponseCode();
            if (code != 200) {
                Log.w(TAG, "MyMemory HTTP " + code);
                return null;
            }

            BufferedReader br = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();

            JSONObject json = new JSONObject(sb.toString());
            JSONObject responseData = json.getJSONObject("responseData");
            String translated = responseData.getString("translatedText");

            // MyMemory returns "PLEASE SELECT TWO DISTINCT LANGUAGES" on bad input
            if (translated.contains("PLEASE SELECT") || translated.contains("QUERY LENGTH")) {
                return null;
            }

            Log.d(TAG, "Translated: " + text.substring(0, Math.min(30, text.length()))
                    + " → " + translated.substring(0, Math.min(30, translated.length())));
            return translated;

        } catch (Exception e) {
            Log.e(TAG, "Translation error: " + e.getMessage());
            return null;
        }
    }

    /**
     * Clear the translation cache (call on language toggle to force re-fetch)
     */
    public static void clearCache() {
        cache.clear();
    }

    /**
     * Pre-warm cache for a list of announcements (call when feed loads)
     * Translates in background so UI is instant when user taps translate
     */
    public static void prewarmCache(String[] texts, String langPair) {
        new Thread(() -> {
            for (String text : texts) {
                if (text == null || text.trim().isEmpty()) continue;
                String key = text.trim() + "|" + langPair;
                if (!cache.containsKey(key)) {
                    String result = callMyMemory(text.trim(), langPair);
                    if (result != null) cache.put(key, result);
                    // Small delay to avoid rate limiting
                    try { Thread.sleep(200); } catch (InterruptedException ignored) {}
                }
            }
        }).start();
    }
}