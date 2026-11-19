package com.example.towncrierbd.utils;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.example.towncrierbd.R;
import com.example.towncrierbd.activities.AnnouncementDetailActivity;
import com.example.towncrierbd.activities.ChatActivity;
import com.example.towncrierbd.activities.GeneralFeedActivity;
import com.example.towncrierbd.activities.RequestNotificationActivity;
import com.example.towncrierbd.activities.RequestTrackingActivity;
import com.example.towncrierbd.models.Announcement;
import com.example.towncrierbd.models.ChatMessage;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class AppNotificationManager {

    private static final String TAG = "AppNotificationManager";

    public static final String CHANNEL_CHAT = "tc_chat_channel";
    public static final String CHANNEL_REQUEST = "tc_request_channel";
    public static final String CHANNEL_ANNOUNCE = "tc_announce_channel_high";

    private static Context appContext;
    public static Activity currentForegroundActivity = null;

    private static final Set<String> handledEvents = Collections.synchronizedSet(new HashSet<>());
    private static ChildEventListener notifListener = null;
    private static DatabaseReference notifRef = null;

    private static ChildEventListener chatsListener = null;
    private static DatabaseReference chatsRef = null;

    private static long sessionStartTime = System.currentTimeMillis();

    public static void init(Application app) {
        appContext = app.getApplicationContext();
        sessionStartTime = System.currentTimeMillis();
        createNotificationChannels();

        app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override
            public void onActivityResumed(@NonNull Activity activity) {
                currentForegroundActivity = activity;
            }

            @Override
            public void onActivityPaused(@NonNull Activity activity) {
                if (currentForegroundActivity == activity) {
                    currentForegroundActivity = null;
                }
            }

            @Override public void onActivityCreated(@NonNull Activity a, @Nullable Bundle b) {}
            @Override public void onActivityStarted(@NonNull Activity a) {}
            @Override public void onActivityStopped(@NonNull Activity a) {}
            @Override public void onActivitySaveInstanceState(@NonNull Activity a, @NonNull Bundle b) {}
            @Override public void onActivityDestroyed(@NonNull Activity a) {}
        });

        FirebaseAuth.getInstance().addAuthStateListener(auth -> {
            String uid = auth.getUid();
            if (uid != null) {
                startListening(uid);
            } else {
                stopListening();
            }
        });

        String initialUid = FirebaseAuth.getInstance().getUid();
        if (initialUid != null) {
            startListening(initialUid);
        }
    }

    private static void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && appContext != null) {
            NotificationManager manager = appContext.getSystemService(NotificationManager.class);
            if (manager == null) return;

            // Chat Channel (Heads-up pop up)
            NotificationChannel chatCh = new NotificationChannel(
                    CHANNEL_CHAT,
                    "Chat Messages",
                    NotificationManager.IMPORTANCE_HIGH
            );
            chatCh.setDescription("Incoming chat and voice messages");
            chatCh.enableVibration(true);
            chatCh.enableLights(true);
            chatCh.setLightColor(Color.BLUE);
            chatCh.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            manager.createNotificationChannel(chatCh);

            // Request Channel (Heads-up pop up)
            NotificationChannel reqCh = new NotificationChannel(
                    CHANNEL_REQUEST,
                    "Requests & Deliveries",
                    NotificationManager.IMPORTANCE_HIGH
            );
            reqCh.setDescription("Updates on requests and deliveries");
            reqCh.enableVibration(true);
            reqCh.enableLights(true);
            reqCh.setLightColor(Color.GREEN);
            reqCh.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            manager.createNotificationChannel(reqCh);

            // Announcement Channel (Heads-up pop up)
            NotificationChannel annCh = new NotificationChannel(
                    CHANNEL_ANNOUNCE,
                    "Announcements",
                    NotificationManager.IMPORTANCE_HIGH
            );
            annCh.setDescription("Nearby priority announcements matching your profile");
            annCh.enableVibration(true);
            annCh.enableLights(true);
            annCh.setLightColor(Color.YELLOW);
            annCh.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            manager.createNotificationChannel(annCh);
        }
    }

    public static synchronized void startListening(String uid) {
        if (uid == null || uid.isEmpty()) return;
        stopListening();

        // 1. Listen to user's personal requestNotifications queue
        notifRef = FirebaseDatabase.getInstance().getReference("requestNotifications").child(uid);
        notifListener = new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                processNotificationRecord(snapshot, uid);
            }

            @Override
            public void onChildChanged(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                processNotificationRecord(snapshot, uid);
            }

            @Override public void onChildRemoved(@NonNull DataSnapshot snapshot) {}
            @Override public void onChildMoved(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {}
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        };
        notifRef.orderByChild("read").equalTo(false).addChildEventListener(notifListener);

        // 2. Also listen to chats for direct real-time delivery
        chatsRef = FirebaseDatabase.getInstance().getReference(Constants.DB_CHATS);
        chatsListener = new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot roomSnap, @Nullable String prevRoom) {
                attachRoomListener(roomSnap, uid);
            }

            @Override
            public void onChildChanged(@NonNull DataSnapshot roomSnap, @Nullable String prevRoom) {
                attachRoomListener(roomSnap, uid);
            }

            @Override public void onChildRemoved(@NonNull DataSnapshot snapshot) {}
            @Override public void onChildMoved(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {}
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        };
        chatsRef.addChildEventListener(chatsListener);
    }

    private static void attachRoomListener(DataSnapshot roomSnap, String myUid) {
        String roomId = roomSnap.getKey();
        if (roomId == null || !ChatActivity.isMyRoom(roomId, myUid)) return;

        for (DataSnapshot msgSnap : roomSnap.getChildren()) {
            ChatMessage msg = msgSnap.getValue(ChatMessage.class);
            if (msg == null) continue;
            if (msg.getTimestamp() < sessionStartTime - 30000) continue; // Skip old historical messages

            if (myUid.equals(msg.getReceiverId()) && !msg.isRead()) {
                String eventKey = "chat_direct_" + msg.getId();
                if (handledEvents.add(eventKey)) {
                    if (roomId.equals(ChatActivity.activeChatRoomId)) {
                        // User is currently looking at this active chat room, skip pop up
                        continue;
                    }
                    String senderName = msg.getSenderName() != null && !msg.getSenderName().isEmpty()
                            ? msg.getSenderName() : "User";
                    String text = msg.getText() != null && !msg.getText().isEmpty()
                            ? msg.getText() : (msg.getAudioUrl() != null ? "🎙 Voice offer" : "New message");

                    showChatNotification("💬 " + senderName, text, roomId, senderName, msg.getSenderId(), msg.getAnnouncementId());
                }
            }
        }
    }

    private static void processNotificationRecord(DataSnapshot snap, String myUid) {
        Boolean read = snap.child("read").getValue(Boolean.class);
        if (Boolean.TRUE.equals(read)) return;

        String key = snap.getKey();
        if (key == null) return;
        String eventId = myUid + ":" + key;
        if (!handledEvents.add(eventId)) return;

        String action = snap.child("action").getValue(String.class);
        String title = snap.child("title").getValue(String.class);
        String body = snap.child("body").getValue(String.class);
        String annId = snap.child("annId").getValue(String.class);

        final String finalTitle = (title == null || title.isEmpty()) ? "Town Crier BD" : title;
        final String finalBody = (body == null) ? "" : body;

        // ── CHAT EVENT ──────────────────────────────────────────────
        if ("chat".equals(action)) {
            String chatRoomId = snap.child("chatRoomId").getValue(String.class);
            String senderId = snap.child("senderId").getValue(String.class);
            String senderName = snap.child("senderName").getValue(String.class);

            if (chatRoomId != null && chatRoomId.equals(ChatActivity.activeChatRoomId)) {
                // Currently looking at screen
                snap.getRef().child("read").setValue(true);
                return;
            }

            showChatNotification(finalTitle, finalBody, chatRoomId, senderName, senderId, annId);
            snap.getRef().child("read").setValue(true);
            return;
        }

        // ── ANNOUNCEMENT EVENT ──────────────────────────────────────
        if ("announcement".equals(action)) {
            showAnnouncementNotification(finalTitle, finalBody, annId);
            snap.getRef().child("read").setValue(true);
            return;
        }

        // ── REQUEST / DELIVERY EVENTS ──────────────────────────────
        showRequestNotification(finalTitle, finalBody, annId, key, action);

        // If inside the app, also trigger immediate in-app AlertDialog popup!
        // If outside the app (fg == null), keep read=false for "open" and "delivered"
        // so that RequestEventObserver shows the Accept/Confirm dialog as soon as user opens the app!
        final Activity fg = currentForegroundActivity;
        if (fg != null && !fg.isFinishing() && !fg.isDestroyed()) {
            snap.getRef().child("read").setValue(true);
            fg.runOnUiThread(() -> {
                if (fg.isFinishing() || fg.isDestroyed()) return;

                if ("delivered".equals(action)) {
                    if (annId != null && !annId.isEmpty()) {
                        FirebaseDatabase.getInstance().getReference(Constants.DB_ANNOUNCEMENTS).child(annId).get()
                                .addOnSuccessListener(postRow -> {
                                    if (fg.isFinishing() || fg.isDestroyed()) return;
                                    Announcement post = postRow.getValue(Announcement.class);
                                    if (post != null && myUid.equals(post.getUserId()) && "delivered".equals(post.getStatus())) {
                                        post.setId(annId);
                                        new AlertDialog.Builder(fg)
                                                .setTitle(PostCatalog.text(fg, "Delivery confirmation", "পণ্য গ্রহণ নিশ্চিত করুন"))
                                                .setMessage(post.getTitle())
                                                .setPositiveButton(PostCatalog.text(fg, "Received", "পেয়েছি"), (d, w) -> PostWorkflow.delivery(fg, post, "received", null))
                                                .setNegativeButton(PostCatalog.text(fg, "Not received", "পাইনি"), (d, w) -> PostWorkflow.updateDelivery(fg, post, "notReceived", null))
                                                .setNeutralButton(PostCatalog.text(fg, "Later", "পরে"), null)
                                                .show();
                                    }
                                });
                    }
                } else if ("open".equals(action)) {
                    // New request for announcer (either self request or active post request) -> Ask Accept or Not!
                    if (annId != null && !annId.isEmpty()) {
                        FirebaseDatabase.getInstance().getReference(Constants.DB_ANNOUNCEMENTS).child(annId).get()
                                .addOnSuccessListener(postRow -> {
                                    if (fg.isFinishing() || fg.isDestroyed()) return;
                                    Announcement post = postRow.getValue(Announcement.class);
                                    long now = System.currentTimeMillis();
                                    if (post == null || !RequestLifecycle.live(post, now) || "delivered".equals(post.getStatus())) return;
                                    post.setId(annId);
                                    showIncomingRequestPrompt(fg, post, myUid);
                                });
                    }
                } else {
                    new AlertDialog.Builder(fg)
                            .setTitle(finalTitle)
                            .setMessage(finalBody)
                            .setPositiveButton(PostCatalog.text(fg, "View request", "রিকোয়েস্ট দেখুন"), (d, w) -> {
                                Intent i = new Intent(fg, RequestNotificationActivity.class);
                                i.putExtra("announcementId", annId);
                                i.putExtra("recipientUid", myUid);
                                fg.startActivity(i);
                            })
                            .setNegativeButton(PostCatalog.text(fg, "Close", "বন্ধ করুন"), null)
                            .show();
                }
            });
        } else if (!"open".equals(action) && !"delivered".equals(action)) {
            snap.getRef().child("read").setValue(true);
        }
    }

    public static AlertDialog showIncomingRequestPrompt(Activity activity, Announcement post, String myUid) {
        long now = System.currentTimeMillis();
        int maxMin = RequestBidManager.getMaxAllowedOfferMinutes(post, now);
        String qty = PostWorkflow.quantities(activity, post);
        StringBuilder msg = new StringBuilder();
        if (post.getTitle() != null && !post.getTitle().trim().isEmpty()) {
            msg.append(post.getTitle().trim());
        }
        if (!qty.isEmpty()) {
            msg.append("\n\n").append(qty);
        }
        if (maxMin > 0) {
            msg.append("\n\n").append(PostCatalog.text(activity,
                    "⏱️ Time remaining: " + maxMin + " min",
                    "⏱️ সময় বাকি: " + maxMin + " মিনিট"));
        }
        msg.append("\n\n").append(PostCatalog.text(activity,
                "Do you want to accept this request?",
                "আপনি কি এই রিকোয়েস্টটি গ্রহণ করতে চান?"));

        AlertDialog dlg = new AlertDialog.Builder(activity)
                .setTitle(PostCatalog.text(activity, "🔔 New Request!", "🔔 নতুন রিকোয়েস্ট এসেছে!"))
                .setMessage(msg.toString())
                .setPositiveButton(PostCatalog.text(activity, "✅ Accept", "✅ গ্রহণ করুন"), (d, w) ->
                        PostWorkflow.delivery(activity, post, "accept", null))
                .setNeutralButton(PostCatalog.text(activity, "View", "বিস্তারিত"), (d, w) -> {
                    Intent i = new Intent(activity, RequestNotificationActivity.class);
                    i.putExtra("announcementId", post.getId());
                    i.putExtra("recipientUid", myUid);
                    activity.startActivity(i);
                })
                .setNegativeButton(PostCatalog.text(activity, "❌ No", "❌ না"), null)
                .create();
        dlg.show();
        return dlg;
    }

    public static synchronized void stopListening() {
        if (notifRef != null && notifListener != null) {
            notifRef.removeEventListener(notifListener);
        }
        notifRef = null;
        notifListener = null;

        if (chatsRef != null && chatsListener != null) {
            chatsRef.removeEventListener(chatsListener);
        }
        chatsRef = null;
        chatsListener = null;
    }

    // ─────────────────────────────────────────────
    // Heads-up Notifications (Pop up banners)
    // ─────────────────────────────────────────────

    public static void showChatNotification(
            String title,
            String body,
            String chatRoomId,
            String senderName,
            String senderId,
            String annId) {

        if (appContext == null) return;
        NotificationManager manager = (NotificationManager) appContext.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;

        createNotificationChannels();

        Intent intent = new Intent(appContext, ChatActivity.class);
        if (annId != null && !annId.isEmpty()) intent.putExtra("announcementId", annId);
        intent.putExtra(ChatActivity.EXTRA_OTHER_UID, senderId != null ? senderId : "");
        intent.putExtra(ChatActivity.EXTRA_OTHER_NAME, senderName != null && !senderName.isEmpty() ? senderName : "User");
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);

        int notifId = (chatRoomId != null && !chatRoomId.isEmpty())
                ? Math.abs(chatRoomId.hashCode())
                : (int) (System.currentTimeMillis() & 0xfffffff);

        PendingIntent pi = PendingIntent.getActivity(
                appContext, notifId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(appContext, CHANNEL_CHAT)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setAutoCancel(true)
                .setContentIntent(pi);

        if (NotificationManagerCompat.from(appContext).areNotificationsEnabled()) {
            manager.notify(notifId, builder.build());
        }
    }

    public static void showRequestNotification(
            String title,
            String body,
            String annId,
            String eventId,
            String action) {

        if (appContext == null) return;
        NotificationManager manager = (NotificationManager) appContext.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;

        createNotificationChannels();

        Intent intent;
        if ("accepted".equals(action)) {
            intent = new Intent(appContext, RequestTrackingActivity.class);
        } else {
            intent = new Intent(appContext, RequestNotificationActivity.class);
        }
        intent.putExtra("announcementId", annId != null ? annId : "");
        intent.putExtra("eventId", eventId != null ? eventId : "");
        intent.putExtra("recipientUid", FirebaseAuth.getInstance().getUid());
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);

        int notifId = (eventId != null && !eventId.isEmpty())
                ? Math.abs(eventId.hashCode())
                : (int) (System.currentTimeMillis() & 0xfffffff);

        PendingIntent pi = PendingIntent.getActivity(
                appContext, notifId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(appContext, CHANNEL_REQUEST)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setAutoCancel(true)
                .setContentIntent(pi);

        if (NotificationManagerCompat.from(appContext).areNotificationsEnabled()) {
            manager.notify(notifId, builder.build());
        }
    }

    public static void showAnnouncementNotification(
            String title,
            String body,
            String annId) {

        if (appContext == null) return;
        NotificationManager manager = (NotificationManager) appContext.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;

        createNotificationChannels();

        Intent intent = new Intent(appContext, AnnouncementDetailActivity.class);
        intent.putExtra("announcementId", annId != null ? annId : "");
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);

        int notifId = (annId != null && !annId.isEmpty())
                ? Math.abs(annId.hashCode())
                : (int) (System.currentTimeMillis() & 0xfffffff);

        PendingIntent pi = PendingIntent.getActivity(
                appContext, notifId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(appContext, CHANNEL_ANNOUNCE)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_EVENT)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setAutoCancel(true)
                .setContentIntent(pi);

        if (NotificationManagerCompat.from(appContext).areNotificationsEnabled()) {
            manager.notify(notifId, builder.build());
        }
    }
}
