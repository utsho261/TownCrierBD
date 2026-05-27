package com.example.towncrierbd.utils;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.firebase.database.FirebaseDatabase;

public class LanguageManager {

    public static final String LANG_EN = "en";
    public static final String LANG_BN = "bn";

    private static final String PREFS_NAME = "tc_prefs";
    private static final String KEY_LANG   = "app_language";

    // ── Get / Set ──────────────────────────────────────────────────────────

    public static String getLanguage(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_LANG, LANG_EN); // default: English
    }

    public static void setLanguage(Context context, String lang) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_LANG, lang)
                .apply();
    }

    public static boolean isEnglish(Context context) {
        return LANG_EN.equals(getLanguage(context));
    }

    public static boolean isBangla(Context context) {
        return LANG_BN.equals(getLanguage(context));
    }

    // ── Toggle helper ──────────────────────────────────────────────────────

    public static void toggle(Context context) {
        if (isEnglish(context)) {
            setLanguage(context, LANG_BN);
        } else {
            setLanguage(context, LANG_EN);
        }
    }

    public static String pick(Context context, String en, String bn) {
        return isEnglish(context) ? en : bn;
    }

    // ── Language label for UI ──────────────────────────────────────────────

    public static String getCurrentLabel(Context context) {
        return isEnglish(context) ? "English" : "বাংলা";
    }

    public static String getToggleLabel(Context context) {
        return isEnglish(context) ? "Switch to বাংলা" : "Switch to English";
    }

    public static void syncFromFirebase(Context ctx, String uid, Runnable onDone) {
        FirebaseDatabase.getInstance()
                .getReference("users")
                .child(uid)
                .child("language")
                .addListenerForSingleValueEvent(new com.google.firebase.database.ValueEventListener() {
                    @Override
                    public void onDataChange(@androidx.annotation.NonNull com.google.firebase.database.DataSnapshot snapshot) {
                        String lang = snapshot.getValue(String.class);
                        if (lang != null && (lang.equals(LANG_EN) || lang.equals(LANG_BN))) {
                            setLanguage(ctx, lang);
                        }
                        if (onDone != null) onDone.run();
                    }
                    @Override
                    public void onCancelled(@androidx.annotation.NonNull com.google.firebase.database.DatabaseError error) {
                        if (onDone != null) onDone.run();
                    }
                });
    }

    public static void saveToFirebase(String uid) {
        if (uid == null || uid.isEmpty()) return;
    }

    public static void saveToFirebase(Context ctx, String uid) {
        if (uid == null || uid.isEmpty()) return;
        String lang = getLanguage(ctx);
        FirebaseDatabase.getInstance()
                .getReference("users")
                .child(uid)
                .child("language")
                .setValue(lang);
    }

}