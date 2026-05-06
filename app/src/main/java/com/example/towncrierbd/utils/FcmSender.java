package com.example.towncrierbd.utils;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

public class FcmSender {

    private static final String TAG = "FcmSender";

    public interface SendCallback {
        void onDone(int successCount);
    }

    /**
     * Send FCM notification to a list of device tokens.
     * Runs on a background thread automatically.
     */
    public static void sendToTokens(List<String> tokens,
                                    String title,
                                    String body,
                                    SendCallback callback) {
        if (tokens == null || tokens.isEmpty()) {
            if (callback != null) callback.onDone(0);
            return;
        }

        new Thread(() -> {
            int success = 0;
            // FCM allows max 1000 tokens per request
            int batchSize = 1000;
            for (int i = 0; i < tokens.size(); i += batchSize) {
                List<String> batch = tokens.subList(i,
                        Math.min(i + batchSize, tokens.size()));
                if (sendBatch(batch, title, body)) success += batch.size();
            }
            int finalSuccess = success;
            android.os.Handler handler = new android.os.Handler(
                    android.os.Looper.getMainLooper());
            handler.post(() -> {
                if (callback != null) callback.onDone(finalSuccess);
            });
        }).start();
    }

    private static boolean sendBatch(List<String> tokens, String title, String body) {
        try {
            JSONArray regIds = new JSONArray();
            for (String t : tokens) regIds.put(t);

            JSONObject notification = new JSONObject();
            notification.put("title", title);
            notification.put("body", body);

            JSONObject payload = new JSONObject();
            payload.put("registration_ids", regIds);
            payload.put("notification", notification);
            payload.put("data", new JSONObject()
                    .put("title", title)
                    .put("body", body));

            URL url = new URL(Constants.FCM_API_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", "key=" + Constants.FCM_SERVER_KEY);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);

            OutputStream os = conn.getOutputStream();
            os.write(payload.toString().getBytes("UTF-8"));
            os.close();

            int responseCode = conn.getResponseCode();
            conn.disconnect();
            return responseCode == 200;

        } catch (Exception e) {
            Log.e(TAG, "FCM send error: " + e.getMessage());
            return false;
        }
    }
}