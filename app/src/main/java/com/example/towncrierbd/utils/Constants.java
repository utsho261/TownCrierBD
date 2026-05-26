package com.example.towncrierbd.utils;

import com.example.towncrierbd.BuildConfig;

public class Constants {
    public static final String DB_USERS         = "users";
    public static final String DB_ANNOUNCEMENTS = "announcements";
    public static final String DB_PHONE_MAP     = "phone_to_email";
    public static final String DB_CHATS         = "chats";

    public static final String ROLE_USER      = "General User";
    public static final String ROLE_ANNOUNCER = "ANNOUNCER";

    public static final double FEED_RADIUS_KM = 3.0;

    // ✅ FIX: Read from BuildConfig (injected from local.properties at build time)
    // Never hardcode API keys in source — they get committed to git
    public static final String CLOUDINARY_CLOUD_NAME    = BuildConfig.CLOUDINARY_CLOUD_NAME;
    public static final String CLOUDINARY_UPLOAD_PRESET = BuildConfig.CLOUDINARY_UPLOAD_PRESET;
    public static final String SERVER_URL               = BuildConfig.SERVER_URL;

    // Firebase Storage audio folder
    public static final String STORAGE_AUDIO_FOLDER = "announcement_audio";
}