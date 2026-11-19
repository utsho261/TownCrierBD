package com.example.towncrierbd.utils;

import android.util.Log;

import androidx.annotation.NonNull;

import com.example.towncrierbd.models.Announcement;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NotificationSender {

    private static final String TAG = "NotificationSender";

    // ─────────────────────────────────────────────
    // Announcement notification:
    // Sends notification to general users whose profile notification settings match!
    // If a user has NOT set notifications, they will NOT receive the notification!
    // ─────────────────────────────────────────────
    public static void sendAnnouncementNotification(
            String annId,
            String title,
            String description,
            double lat,
            double lng,
            String userId) {
        Announcement a = new Announcement();
        a.setId(annId);
        a.setTitle(title);
        a.setDescription(description);
        a.setLat(lat);
        a.setLng(lng);
        a.setUserId(userId);
        sendAnnouncementNotification(a);
    }

    public static void sendAnnouncementNotification(Announcement a) {
        if (a == null || a.getId() == null || a.getId().isEmpty()) return;

        final String annId = a.getId();
        final String title = a.getTitle() != null ? a.getTitle() : "";
        final String description = a.getDescription() != null ? a.getDescription() : "";
        final double postLat = a.getLat();
        final double postLng = a.getLng();
        final String authorUid = a.getUserId() != null ? a.getUserId() : "";

        // Query users in Realtime Database to find matching general users
        FirebaseDatabase.getInstance().getReference(Constants.DB_USERS)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        long now = System.currentTimeMillis();
                        for (DataSnapshot userSnap : snapshot.getChildren()) {
                            String uid = userSnap.getKey();
                            if (uid == null || uid.equals(authorUid)) continue;

                            String role = userSnap.child("role").getValue(String.class);
                            if (Constants.ROLE_ANNOUNCER.equals(role)) continue; // only notify General Users

                            Boolean notifEnabled = userSnap.child("notificationsEnabled").getValue(Boolean.class);
                            if (Boolean.FALSE.equals(notifEnabled)) continue;

                            // Requirement 1:
                            // "jodi notification set na kore tahole notification asbe na"
                            // Check if user has configured notification categories
                            List<String> notifCats = new ArrayList<>();
                            for (DataSnapshot catSnap : userSnap.child("notificationCategories").getChildren()) {
                                String c = catSnap.getValue(String.class);
                                if (c != null && !c.trim().isEmpty()) {
                                    notifCats.add(c.trim());
                                }
                            }
                            if (notifCats.isEmpty()) {
                                // User has NOT configured notification settings! Do NOT send notification!
                                continue;
                            }

                            Double uLat = userSnap.child("lat").getValue(Double.class);
                            Double uLng = userSnap.child("lng").getValue(Double.class);
                            if (uLat == null || uLng == null || (uLat == 0.0 && uLng == 0.0)) continue;

                            Double radiusKm = userSnap.child("notificationRadiusKm").getValue(Double.class);
                            if (radiusKm == null || radiusKm <= 0) radiusKm = 1.0;

                            double dist = DistanceUtil.distanceKm(postLat, postLng, uLat, uLng);
                            if (dist > radiusKm) continue; // Beyond user's chosen notification distance

                            // Check category match
                            List<String> postCats = new ArrayList<>();
                            if (a.getCategory() != null && !a.getCategory().isEmpty()) postCats.add(a.getCategory());
                            if (a.getSelectedCategories() != null) postCats.addAll(a.getSelectedCategories());
                            if (a.getSelectedProducts() != null) postCats.addAll(a.getSelectedProducts());

                            boolean matches = false;
                            for (String cat : notifCats) {
                                if (CategoryConfig.CAT_ALL.equals(cat)) {
                                    matches = true;
                                    break;
                                }
                                for (String pCat : postCats) {
                                    if (cat.equalsIgnoreCase(pCat)
                                            || pCat.toLowerCase().contains(cat.toLowerCase())
                                            || cat.toLowerCase().contains(pCat.toLowerCase())) {
                                        matches = true;
                                        break;
                                    }
                                }
                                if (matches) break;
                            }

                            if (!matches) continue; // Does not match user's selected categories

                            // Dispatch notification record to this user's queue in Firebase
                            String eventId = "ann_" + annId + "_" + now;
                            Map<String, Object> notif = new HashMap<>();
                            notif.put("annId", annId);
                            notif.put("eventId", eventId);
                            notif.put("type", "announcement");
                            notif.put("action", "announcement");
                            notif.put("title", "📢 " + (title.isEmpty() ? "Announcement" : title));
                            notif.put("body", description.isEmpty() ? title : description);
                            notif.put("timestamp", now);
                            notif.put("read", false);

                            FirebaseDatabase.getInstance().getReference("requestNotifications")
                                    .child(uid)
                                    .child(eventId)
                                    .setValue(notif);
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "Failed to fetch users for announcement notification: " + error.getMessage());
                    }
                });

        // Also post to HTTP server in background thread as backup
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("annId",       annId);
                body.put("title",       title);
                body.put("description", description);
                body.put("lat",         postLat);
                body.put("lng",         postLng);
                body.put("userId",      authorUid);
                postToServer(Constants.SERVER_URL + "/notify-announcement", body);
            } catch (Exception ignored) {}
        }).start();
    }

    // ─────────────────────────────────────────────
    // Chat notification
    // ─────────────────────────────────────────────
    public static void sendChatNotification(
            String receiverId,
            String senderName,
            String senderId,
            String text,
            String chatRoomId) {
        sendChatNotification(receiverId, senderName, senderId, text, chatRoomId, null);
    }

    public static void sendChatNotification(
            String receiverId,
            String senderName,
            String senderId,
            String text,
            String chatRoomId,
            String announcementId) {

        if (receiverId == null || receiverId.trim().isEmpty() ||
            senderId == null || senderId.trim().isEmpty() ||
            receiverId.equals(senderId)) {
            return;
        }

        final long now = System.currentTimeMillis();
        final String cleanSender = (senderName != null && !senderName.trim().isEmpty())
                ? senderName.trim() : "User";
        final String cleanText = (text != null && !text.trim().isEmpty())
                ? text.trim() : "New message";
        final String roomClean = (chatRoomId != null ? chatRoomId.replace("|", "_") : "");
        final String eventId = "chat_" + roomClean + "_" + now;

        // 1. Immediately write notification record to requestNotifications/{receiverId}
        // This ensures the receiver's device receives the push notification even outside the app!
        Map<String, Object> notif = new HashMap<>();
        notif.put("annId", announcementId != null ? announcementId : "");
        notif.put("chatRoomId", chatRoomId != null ? chatRoomId : "");
        notif.put("senderId", senderId);
        notif.put("senderName", cleanSender);
        notif.put("receiverId", receiverId);
        notif.put("title", "💬 " + cleanSender);
        notif.put("body", cleanText);
        notif.put("action", "chat");
        notif.put("type", "chat");
        notif.put("timestamp", now);
        notif.put("read", false);

        FirebaseDatabase.getInstance().getReference("requestNotifications")
                .child(receiverId)
                .child(eventId)
                .setValue(notif);

        // 2. Also dispatch HTTP to server in background thread as backup
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("receiverId",   receiverId);
                body.put("recipientUid", receiverId);
                body.put("senderName",   cleanSender);
                body.put("senderId",     senderId);
                body.put("text",         cleanText);
                body.put("title",        "💬 " + cleanSender);
                body.put("body",         cleanText);
                body.put("chatRoomId",   chatRoomId != null ? chatRoomId : "");
                body.put("type",         "chat");
                body.put("annId",        announcementId != null ? announcementId : "");
                postToServer(Constants.SERVER_URL + "/notify-chat", body);
            } catch (Exception ignored) {}
        }).start();
    }

    // ─────────────────────────────────────────────
    // Request event notification:
    // (new request, accept request, delivery done, cancelled)
    // ─────────────────────────────────────────────
    public static void sendRequestEventNotification(
            String recipientUid,
            String annId,
            String action,
            String title,
            String body) {

        if (recipientUid == null || recipientUid.trim().isEmpty()) return;

        final long now = System.currentTimeMillis();
        final String eventId = "req_" + (annId != null ? annId : "") + "_" + action + "_" + now;

        Map<String, Object> notif = new HashMap<>();
        notif.put("annId", annId != null ? annId : "");
        notif.put("eventId", eventId);
        notif.put("type", "request");
        notif.put("action", action);
        notif.put("title", title != null ? title : "Request update");
        notif.put("body", body != null ? body : "");
        notif.put("timestamp", now);
        notif.put("read", false);

        FirebaseDatabase.getInstance().getReference("requestNotifications")
                .child(recipientUid)
                .child(eventId)
                .setValue(notif);
    }

    // ─────────────────────────────────────────────
    // Self-request notification:
    // Sends ONLY to announcers who currently have a LIVE active post
    // containing ALL requested items within the request radius.
    // Announcers with no active post or missing any requested item receive NOTHING.
    // ─────────────────────────────────────────────
    public static void sendSelfRequestNotification(Announcement req) {
        if (req == null || req.getId() == null || req.getId().trim().isEmpty()) return;

        final String reqId = req.getId();
        final String buyerUid = req.getUserId() != null ? req.getUserId() : "";
        final double reqLat = req.getLat();
        final double reqLng = req.getLng();
        final double reqRadius = req.getRequestRadiusKm() > 0 ? req.getRequestRadiusKm() : 1.0;
        final String reqTitle = (req.getTitle() != null && !req.getTitle().trim().isEmpty())
                ? req.getTitle().trim() : "New request";

        FirebaseDatabase.getInstance().getReference(Constants.DB_ANNOUNCEMENTS)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        long now = System.currentTimeMillis();
                        Map<String, List<Announcement>> activePostsByAnnouncer = new HashMap<>();

                        for (DataSnapshot row : snapshot.getChildren()) {
                            Announcement post = row.getValue(Announcement.class);
                            if (post == null || RequestLifecycle.request(post)) continue;
                            if (!RequestLifecycle.live(post, now)) continue;
                            String ownerUid = post.getUserId();
                            if (ownerUid == null || ownerUid.trim().isEmpty() || ownerUid.equals(buyerUid)) continue;
                            post.setId(row.getKey());
                            List<Announcement> list = activePostsByAnnouncer.get(ownerUid);
                            if (list == null) {
                                list = new ArrayList<>();
                                activePostsByAnnouncer.put(ownerUid, list);
                            }
                            list.add(post);
                        }

                        for (Map.Entry<String, List<Announcement>> entry : activePostsByAnnouncer.entrySet()) {
                            String announcerUid = entry.getKey();
                            Announcement matched = RequestMatcher.findMatchingActivePost(req, entry.getValue(), now);
                            if (matched == null) continue;

                            if (reqLat != 0.0 && reqLng != 0.0 && matched.getLat() != 0.0 && matched.getLng() != 0.0) {
                                double distKm = DistanceUtil.distanceKm(reqLat, reqLng, matched.getLat(), matched.getLng());
                                if (distKm > reqRadius) continue;
                            }

                            sendRequestEventNotification(
                                    announcerUid,
                                    reqId,
                                    "open",
                                    "🔔 নতুন রিকোয়েস্ট",
                                    reqTitle
                            );
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "Failed to match self-request announcers: " + error.getMessage());
                    }
                });
    }

    // ─────────────────────────────────────────────
    // HTTP POST helper
    // ─────────────────────────────────────────────
    private static void postToServer(String urlStr, JSONObject body) {
        if (urlStr == null || urlStr.trim().isEmpty()) return;
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setRequestProperty("Accept", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            byte[] input = body.toString().getBytes(StandardCharsets.UTF_8);
            OutputStream os = conn.getOutputStream();
            os.write(input, 0, input.length);
            os.flush();
            os.close();

            int responseCode = conn.getResponseCode();
            Log.d(TAG, "Server response: " + responseCode + " for " + urlStr);
            conn.disconnect();
        } catch (Exception ignored) {}
    }
}
