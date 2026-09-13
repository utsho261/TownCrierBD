package com.example.towncrierbd;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.example.towncrierbd.activities.ChatActivity;
import com.example.towncrierbd.activities.GeneralFeedActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

public class MyFirebaseMessagingService extends FirebaseMessagingService {

    private static final String CHANNEL_ANNOUNCE = "tc_announce_channel";
    private static final String CHANNEL_CHAT     = "tc_chat_channel";

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);

        // 1. User MUST be logged in. If user has logged out, suppress all notifications!
        String currentUid = FirebaseAuth.getInstance().getUid();
        if (currentUid == null) {
            android.util.Log.d("MyFirebaseMsgService", "User is logged out; suppressing notification.");
            return;
        }

        String title = "Town Crier BD";
        String body  = "New activity!";

        // notification payload থেকে নাও
        if (remoteMessage.getNotification() != null) {
            String t = remoteMessage.getNotification().getTitle();
            String b = remoteMessage.getNotification().getBody();
            if (t != null) title = t;
            if (b != null) body  = b;
        }

        // data payload থেকেও নাও (override করবে)
        Map<String, String> data = remoteMessage.getData();
        if (data.containsKey("title")) title = data.get("title");
        if (data.containsKey("body"))  body  = data.get("body");

        String type = data.getOrDefault("type", "announcement");

        if ("chat".equals(type)) {
            String receiverId  = data.getOrDefault("receiverId", "");
            // If receiverId is provided, ensure message is meant for current logged-in user
            if (!receiverId.isEmpty() && !currentUid.equals(receiverId)) {
                android.util.Log.d("MyFirebaseMsgService", "Chat notification intended for different user; suppressing.");
                return;
            }
            String chatRoomId  = data.getOrDefault("chatRoomId", "");
            String senderName  = data.getOrDefault("senderName", "");
            String senderId    = data.getOrDefault("senderId", "");
            showChatNotification(title, body, chatRoomId, senderName, senderId);
        } else {
            showAnnouncementNotification(title, body);
        }
    }

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        com.example.towncrierbd.utils.AuthUtils.saveTokenToFirebase(token);
    }

    // ── Announcement Notification ──────────────────────────────
    private void showAnnouncementNotification(String title, String body) {
        NotificationManager manager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ANNOUNCE,
                    "Announcements",
                    NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("Nearby announcements");
            manager.createNotificationChannel(ch);
        }

        Intent intent = new Intent(this, GeneralFeedActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(this, CHANNEL_ANNOUNCE)
                        .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                        .setContentTitle(title)
                        .setContentText(body)
                        .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                        .setAutoCancel(true)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setContentIntent(pi);

        manager.notify((int) System.currentTimeMillis(), builder.build());
    }

    // ── Chat Notification ──────────────────────────────────────
    private void showChatNotification(String title, String body,
                                      String chatRoomId, String senderName, String senderId) {
        NotificationManager manager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_CHAT,
                    "Chat Messages",
                    NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("In-app chat messages");
            manager.createNotificationChannel(ch);
        }

        // Chat notification tap করলে সরাসরি ChatActivity তে যাবে
        Intent intent = new Intent(this, ChatActivity.class);
        intent.putExtra(ChatActivity.EXTRA_OTHER_UID,  senderId);
        intent.putExtra(ChatActivity.EXTRA_OTHER_NAME, senderName);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pi = PendingIntent.getActivity(
                this, (int) System.currentTimeMillis(), intent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(this, CHANNEL_CHAT)
                        .setSmallIcon(android.R.drawable.ic_dialog_email)
                        .setContentTitle(title)
                        .setContentText(body)
                        .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                        .setAutoCancel(true)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setContentIntent(pi);

        manager.notify((int) System.currentTimeMillis(), builder.build());
    }
}