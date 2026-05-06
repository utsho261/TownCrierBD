package com.example.towncrierbd.utils;

import android.util.Log;

import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class NotificationSender {

    private static final String TAG = "NotificationSender";

    // ─────────────────────────────────────────────
    // Announcement notification পাঠাও
    // ─────────────────────────────────────────────
    public static void sendAnnouncementNotification(
            String annId,
            String title,
            String description,
            double lat,
            double lng,
            String userId) {

        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("annId",       annId != null ? annId : "");
                body.put("title",       title != null ? title : "");
                body.put("description", description != null ? description : "");
                body.put("lat",         lat);
                body.put("lng",         lng);
                body.put("userId",      userId != null ? userId : "");

                postToServer(Constants.SERVER_URL + "/notify-announcement", body);

            } catch (Exception e) {
                Log.e(TAG, "Announcement notification error: " + e.getMessage());
            }
        }).start();
    }

    // ─────────────────────────────────────────────
    // Chat notification পাঠাও
    // ─────────────────────────────────────────────
    public static void sendChatNotification(
            String receiverId,
            String senderName,
            String senderId,
            String text,
            String chatRoomId) {

        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("receiverId",  receiverId != null ? receiverId : "");
                body.put("senderName",  senderName != null ? senderName : "");
                body.put("senderId",    senderId != null ? senderId : "");
                body.put("text",        text != null ? text : "");
                body.put("chatRoomId",  chatRoomId != null ? chatRoomId : "");

                postToServer(Constants.SERVER_URL + "/notify-chat", body);

            } catch (Exception e) {
                Log.e(TAG, "Chat notification error: " + e.getMessage());
            }
        }).start();
    }

    // ─────────────────────────────────────────────
    // HTTP POST helper
    // ─────────────────────────────────────────────
    private static void postToServer(String urlStr, JSONObject body) {
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);

            byte[] input = body.toString().getBytes(StandardCharsets.UTF_8);
            OutputStream os = conn.getOutputStream();
            os.write(input, 0, input.length);
            os.close();

            int responseCode = conn.getResponseCode();
            Log.d(TAG, "Server response: " + responseCode + " for " + urlStr);
            conn.disconnect();

        } catch (Exception e) {
            Log.e(TAG, "HTTP error: " + e.getMessage());
        }
    }
}