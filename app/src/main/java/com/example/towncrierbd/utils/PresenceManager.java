package com.example.towncrierbd.utils;

import android.content.Context;

import androidx.annotation.NonNull;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;

public class PresenceManager {

    private static ValueEventListener connectionListener;
    private static DatabaseReference connectedRef;
    private static String currentUid = null;

    public static synchronized void trackCurrentPresence() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null || uid.isEmpty()) return;

        if (uid.equals(currentUid) && connectionListener != null) return;
        stopTracking();

        currentUid = uid;
        connectedRef = FirebaseDatabase.getInstance().getReference(".info/connected");
        DatabaseReference userStatusRef = FirebaseDatabase.getInstance()
                .getReference(Constants.DB_USERS)
                .child(currentUid);

        connectionListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Boolean isConnected = snapshot.getValue(Boolean.class);
                if (Boolean.TRUE.equals(isConnected)) {
                    Map<String, Object> onlineMap = new HashMap<>();
                    onlineMap.put("online", true);
                    onlineMap.put("lastSeen", ServerValue.TIMESTAMP);
                    userStatusRef.updateChildren(onlineMap);

                    Map<String, Object> offlineMap = new HashMap<>();
                    offlineMap.put("online", false);
                    offlineMap.put("lastSeen", ServerValue.TIMESTAMP);
                    userStatusRef.onDisconnect().updateChildren(offlineMap);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };

        connectedRef.addValueEventListener(connectionListener);
    }

    public static synchronized void setOfflineNow() {
        if (currentUid == null) return;
        DatabaseReference userStatusRef = FirebaseDatabase.getInstance()
                .getReference(Constants.DB_USERS)
                .child(currentUid);

        Map<String, Object> offlineMap = new HashMap<>();
        offlineMap.put("online", false);
        offlineMap.put("lastSeen", ServerValue.TIMESTAMP);
        userStatusRef.updateChildren(offlineMap);
    }

    public static synchronized void setOnlineNow() {
        if (currentUid == null) {
            trackCurrentPresence();
            return;
        }
        DatabaseReference userStatusRef = FirebaseDatabase.getInstance()
                .getReference(Constants.DB_USERS)
                .child(currentUid);

        Map<String, Object> onlineMap = new HashMap<>();
        onlineMap.put("online", true);
        onlineMap.put("lastSeen", ServerValue.TIMESTAMP);
        userStatusRef.updateChildren(onlineMap);
    }

    public static synchronized void stopTracking() {
        if (connectedRef != null && connectionListener != null) {
            connectedRef.removeEventListener(connectionListener);
            connectionListener = null;
        }
        currentUid = null;
    }

    public static String formatActiveStatus(Context context, boolean isOnline, long lastSeen) {
        if (isOnline) {
            return PostCatalog.text(context, "● Active now", "● অনলাইনে আছেন");
        }

        if (lastSeen <= 0) {
            return PostCatalog.text(context, "Offline", "অফলাইন");
        }

        long now = System.currentTimeMillis();
        long diff = Math.max(0, now - lastSeen);
        long mins = diff / (60 * 1000);

        if (mins < 1) {
            return PostCatalog.text(context, "Active just now", "এইমাত্র সক্রিয় ছিলেন");
        } else if (mins < 60) {
            return PostCatalog.text(context, "Active " + mins + "m ago", mins + " মিনিট আগে সক্রিয় ছিলেন");
        } else {
            long hours = mins / 60;
            if (hours < 24) {
                return PostCatalog.text(context, "Active " + hours + "h ago", hours + " ঘণ্টা আগে সক্রিয় ছিলেন");
            } else {
                long days = hours / 24;
                return PostCatalog.text(context, "Active " + days + "d ago", days + " দিন আগে সক্রিয় ছিলেন");
            }
        }
    }
}
