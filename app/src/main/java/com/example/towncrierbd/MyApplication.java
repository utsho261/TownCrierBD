package com.example.towncrierbd;

import android.app.Application;
import com.example.towncrierbd.utils.CloudinaryUploader;

public class MyApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        CloudinaryUploader.init(this);
    }
}