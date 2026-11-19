package com.example.towncrierbd;

import android.app.Application;

import com.example.towncrierbd.utils.CloudinaryUploader;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.messaging.FirebaseMessaging;

public class MyApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        // Cloudinary init
        CloudinaryUploader.init(this);
        com.example.towncrierbd.utils.AppNotificationManager.init(this);
        registerActivityLifecycleCallbacks(new com.example.towncrierbd.utils.RequestEventObserver());

        // FCM token refresh — app start এ token save করো (if user logged in)
        com.example.towncrierbd.utils.AuthUtils.syncFcmToken();
        com.example.towncrierbd.utils.PresenceManager.trackCurrentPresence();
    }
}