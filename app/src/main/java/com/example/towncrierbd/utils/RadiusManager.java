package com.example.towncrierbd.utils;

import android.content.Context;
import android.content.SharedPreferences;

public class RadiusManager {
    private static final String PREFS_NAME = "tc_prefs";
    private static final String KEY_RADIUS = "feed_radius";

    public static double getRadius(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return (double) prefs.getFloat(KEY_RADIUS, (float) Constants.FEED_RADIUS_KM);
    }

    public static void setRadius(Context context, double radius) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putFloat(KEY_RADIUS, (float) radius)
                .apply();
    }
}