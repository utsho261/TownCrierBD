package com.example.towncrierbd.utils;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.NonNull;

import com.example.towncrierbd.activities.LoginActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.messaging.FirebaseMessaging;

/**
 * Handles authentication lifecycle utilities including FCM token sync and clean logout.
 * When logged out, removes FCM tokens and cancels all active notifications to prevent
 * any post-logout notifications from reaching the device.
 */
public class AuthUtils {

    private static final String TAG = "AuthUtils";

    /**
     * Uploads the given token to the current user's profile in Firebase Realtime Database.
     */
    public static void saveTokenToFirebase(String token) {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null || token == null || token.isEmpty()) return;

        FirebaseDatabase.getInstance()
                .getReference(Constants.DB_USERS)
                .child(uid)
                .child("fcmToken")
                .setValue(token)
                .addOnFailureListener(e -> Log.e(TAG, "Failed to save FCM token: " + e.getMessage()));
    }

    /**
     * Fetches current device's FCM token and saves it to Firebase Realtime Database
     * for the currently authenticated user.
     */
    public static void syncFcmToken() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        try {
            FirebaseMessaging.getInstance().getToken()
                    .addOnSuccessListener(token -> {
                        if (token != null && !token.isEmpty()) {
                            saveTokenToFirebase(token);
                        }
                    })
                    .addOnFailureListener(e -> Log.e(TAG, "Error fetching FCM token: " + e.getMessage()));
        } catch (Exception e) {
            Log.e(TAG, "Error invoking getToken: " + e.getMessage());
        }
    }

    /**
     * Completely logs out the current user:
     * 1. Removes the fcmToken from the user's record in Firebase Realtime Database.
     * 2. Deletes the device's local FCM registration token so Google FCM servers no longer deliver to it.
     * 3. Cancels all notifications currently posted in the Android status bar.
     * 4. Signs out of FirebaseAuth.
     * 5. Redirects to LoginActivity clearing all back-stack activities.
     */
    public static void logout(@NonNull Activity activity) {
        String uid = FirebaseAuth.getInstance().getUid();

        // 1. Remove fcmToken from Firebase Database so server will not send notifications
        if (uid != null) {
            try {
                FirebaseDatabase.getInstance()
                        .getReference(Constants.DB_USERS)
                        .child(uid)
                        .child("fcmToken")
                        .removeValue();
            } catch (Exception e) {
                Log.e(TAG, "Error removing fcmToken from database: " + e.getMessage());
            }
        }

        // 2. Unregister and delete FCM token from this device
        try {
            FirebaseMessaging.getInstance().deleteToken()
                    .addOnCompleteListener(task -> Log.d(TAG, "FCM token deleted on logout: " + task.isSuccessful()));
        } catch (Exception e) {
            Log.e(TAG, "Error deleting FCM token: " + e.getMessage());
        }

        // 3. Clear any existing active notifications from the notification bar
        try {
            NotificationManager manager =
                    (NotificationManager) activity.getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.cancelAll();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error cancelling notifications: " + e.getMessage());
        }

        // 4. Sign out from Firebase Auth
        try {
            FirebaseAuth.getInstance().signOut();
        } catch (Exception e) {
            Log.e(TAG, "Error signing out: " + e.getMessage());
        }

        // 5. Navigate to LoginActivity
        Intent intent = new Intent(activity, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        activity.startActivity(intent);
        activity.finish();
    }
}
