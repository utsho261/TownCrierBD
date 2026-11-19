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

        // 2. Check if notifications are enabled on this device
        if (!androidx.core.app.NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            android.util.Log.d("MyFirebaseMsgService", "Notifications are disabled on device; suppressing.");
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
        String recipient = data.get("recipientUid");
        if (recipient == null || recipient.isEmpty()) {
            recipient = data.get("receiverId");
        }
        if (recipient != null && !recipient.isEmpty() && !currentUid.equals(recipient)) {
            android.util.Log.d("MyFirebaseMsgService", "Notification intended for " + recipient + ", but current user is " + currentUid + "; suppressing.");
            return;
        }

        if (data.containsKey("title") && data.get("title") != null && !data.get("title").isEmpty()) {
            title = data.get("title");
        }
        if (data.containsKey("body") && data.get("body") != null && !data.get("body").isEmpty()) {
            body  = data.get("body");
        } else if (data.containsKey("text") && data.get("text") != null && !data.get("text").isEmpty()) {
            body = data.get("text");
        }

        String type = data.getOrDefault("type", "announcement");

        if ("chat".equals(type)) {
            String receiverId  = data.getOrDefault("receiverId", data.getOrDefault("recipientUid", ""));
            // If receiverId is provided, ensure message is meant for current logged-in user
            if (!receiverId.isEmpty() && !currentUid.equals(receiverId)) {
                android.util.Log.d("MyFirebaseMsgService", "Chat notification intended for different user; suppressing.");
                return;
            }
            String chatRoomId  = data.getOrDefault("chatRoomId", "");
            // If user is currently looking at this active chat room, don't show status bar notification banner
            if (chatRoomId != null && !chatRoomId.isEmpty() && chatRoomId.equals(ChatActivity.activeChatRoomId)) {
                android.util.Log.d("MyFirebaseMsgService", "Chat room currently active on screen; suppressing banner.");
                return;
            }
            String senderName  = data.getOrDefault("senderName", "");
            String senderId    = data.getOrDefault("senderId", "");
            showChatNotification(title, body, chatRoomId, senderName, senderId, data.getOrDefault("annId",""));
        } else {
            showAnnouncementNotification(title, body, data);
        }
    }

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        com.example.towncrierbd.utils.AuthUtils.saveTokenToFirebase(token);
    }

    // ── Announcement Notification ──────────────────────────────
    private void showAnnouncementNotification(String title, String body, Map<String,String> data) {
        boolean urgent="high".equals(data.get("priority"));
        String channel=urgent?CHANNEL_ANNOUNCE+"_high":CHANNEL_ANNOUNCE+"_normal";
        NotificationManager manager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    channel,
                    urgent?"Priority announcements":"Announcements",
                    urgent?NotificationManager.IMPORTANCE_HIGH:NotificationManager.IMPORTANCE_DEFAULT);
            ch.setDescription("Nearby announcements");
            manager.createNotificationChannel(ch);
        }

        String event=data.getOrDefault("eventId",data.getOrDefault("annId","announcement"));
        int notificationId=event.hashCode();
        Intent intent=new Intent(this,com.example.towncrierbd.activities.RequestNotificationActivity.class);
        intent.putExtra("announcementId",data.getOrDefault("annId",""));intent.putExtra("recipientUid",data.getOrDefault("recipientUid",""));intent.putExtra("eventId",data.getOrDefault("eventId",""));
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi=PendingIntent.getActivity(this,notificationId,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(this, channel)
                        .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                        .setContentTitle(title)
                        .setContentText(body)
                        .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                        .setAutoCancel(true)
                        .setPriority(urgent?NotificationCompat.PRIORITY_HIGH:NotificationCompat.PRIORITY_DEFAULT)
                        .setContentIntent(pi);

        if(androidx.core.app.NotificationManagerCompat.from(this).areNotificationsEnabled())manager.notify(notificationId,builder.build());
    }

    // ── Chat Notification ──────────────────────────────────────
    private void showChatNotification(String title, String body,
                                      String chatRoomId, String senderName, String senderId, String annId) {
        NotificationManager manager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_CHAT,
                    "Chat Messages",
                    NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("In-app chat messages");
            ch.enableVibration(true);
            manager.createNotificationChannel(ch);
        }

        // Chat notification tap করলে সরাসরি ChatActivity তে যাবে
        Intent intent = new Intent(this, ChatActivity.class);
        if (annId != null && !annId.isEmpty()) {
            intent.putExtra("announcementId", annId);
        }
        intent.putExtra(ChatActivity.EXTRA_OTHER_UID,  senderId);
        intent.putExtra(ChatActivity.EXTRA_OTHER_NAME, senderName != null && !senderName.isEmpty() ? senderName : "User");
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        int notifId = (chatRoomId != null && !chatRoomId.isEmpty())
                ? Math.abs(chatRoomId.hashCode())
                : (int) (System.currentTimeMillis() & 0xfffffff);

        PendingIntent pi = PendingIntent.getActivity(
                this, notifId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(this, CHANNEL_CHAT)
                        .setSmallIcon(android.R.drawable.ic_dialog_email)
                        .setContentTitle(title)
                        .setContentText(body)
                        .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                        .setAutoCancel(true)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setDefaults(NotificationCompat.DEFAULT_ALL)
                        .setContentIntent(pi);

        manager.notify(notifId, builder.build());
    }
}