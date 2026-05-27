package com.example.towncrierbd.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;

import java.util.Locale;

/**
 * LanguageManager — 100% FREE, offline language switching.
 *
 * Supports:  English ("en")  and  Bangla ("bn")
 * Storage:   SharedPreferences (no cost, no library needed)
 * Method:    Android resource locale override (built-in, free)
 *
 * Usage:
 *   // In every Activity's attachBaseContext:
 *   @Override
 *   protected void attachBaseContext(Context base) {
 *       super.attachBaseContext(LanguageManager.wrap(base));
 *   }
 *
 *   // To toggle language (call from any button click):
 *   LanguageManager.toggleLanguage(this);
 *   // Then restart the activity to apply
 */
public class LanguageManager {

    private static final String PREF_NAME     = "tc_language_pref";
    private static final String KEY_LANGUAGE  = "selected_language";
    public  static final String LANG_ENGLISH  = "en";
    public  static final String LANG_BANGLA   = "bn";

    // ── Get current saved language (default: English) ─────────────────────

    public static String getLanguage(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_LANGUAGE, LANG_ENGLISH);
    }

    // ── Save selected language ─────────────────────────────────────────────

    public static void setLanguage(Context context, String language) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_LANGUAGE, language).apply();
    }

    // ── Toggle between English and Bangla ─────────────────────────────────

    public static String toggleLanguage(Context context) {
        String current = getLanguage(context);
        String next = LANG_ENGLISH.equals(current) ? LANG_BANGLA : LANG_ENGLISH;
        setLanguage(context, next);
        return next;
    }

    public static boolean isBangla(Context context) {
        return LANG_BANGLA.equals(getLanguage(context));
    }

    // ── Wrap context with selected locale — call in attachBaseContext ───────

    public static Context wrap(Context context) {
        String language = getLanguage(context);
        return updateResources(context, language);
    }

    // ── Apply locale to context ────────────────────────────────────────────

    public static Context updateResources(Context context, String language) {
        Locale locale = new Locale(language);
        Locale.setDefault(locale);

        Resources res = context.getResources();
        Configuration config = new Configuration(res.getConfiguration());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            config.setLocale(locale);
            context = context.createConfigurationContext(config);
        } else {
            config.locale = locale;
            res.updateConfiguration(config, res.getDisplayMetrics());
        }

        return context;
    }

    // ── Get display name for current language ──────────────────────────────

    public static String getLanguageDisplayName(Context context) {
        return isBangla(context) ? "বাংলা" : "English";
    }

    // ── Get the toggle button label (shows what you'll switch TO) ──────────

    public static String getToggleLabel(Context context) {
        return isBangla(context) ? "English" : "বাংলা";
    }
}