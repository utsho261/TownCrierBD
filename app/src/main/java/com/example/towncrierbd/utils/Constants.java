package com.example.towncrierbd.utils;

public class Constants {
    public static final String DB_USERS         = "users";
    public static final String DB_ANNOUNCEMENTS = "announcements";
    public static final String DB_PHONE_MAP     = "phone_to_email";
    public static final String DB_CHATS         = "chats";

    public static final String ROLE_USER      = "General User";
    public static final String ROLE_ANNOUNCER = "ANNOUNCER";

    public static final double FEED_RADIUS_KM = 3.0;

    public static final String CLOUDINARY_CLOUD_NAME    = "dilf73u5q";
    public static final String CLOUDINARY_UPLOAD_PRESET = "town_crier_preset";

    // FCM Legacy HTTP API (client-side trigger)
    // Replace with your actual FCM Server Key from Firebase Console → Project Settings → Cloud Messaging
    public static final String FCM_SERVER_KEY = "YOUR_FCM_SERVER_KEY_HERE";
    public static final String FCM_API_URL    = "https://fcm.googleapis.com/fcm/send";

    // Firebase Storage audio folder
    public static final String STORAGE_AUDIO_FOLDER = "announcement_audio";
}