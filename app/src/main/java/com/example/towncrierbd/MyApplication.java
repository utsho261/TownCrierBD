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
        CloudinaryUploader.init(this);

        // ✅ FCM token refresh on app start
        FirebaseMessaging.getInstance().getToken()
                .addOnSuccessListener(token -> {
                    String uid = FirebaseAuth.getInstance().getUid();
                    if (uid != null && token != null) {
                        FirebaseDatabase.getInstance()
                                .getReference("users")
                                .child(uid)
                                .child("fcmToken")
                                .setValue(token);
                    }
                });
    }
}