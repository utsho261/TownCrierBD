package com.example.towncrierbd.utils;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * LanguageManager — App-wide language preference manager.
 *
 * Supports two languages:
 *   LANG_EN = "en"  → English (default)
 *   LANG_BN = "bn"  → Bangla (বাংলা)
 *
 * Language is stored in SharedPreferences so it persists across sessions.
 * Call setLanguage() from Settings/Profile, and isEnglish() / isBangla()
 * everywhere else to check the current language.
 *
 * Usage:
 *   LanguageManager.setLanguage(context, LanguageManager.LANG_BN);
 *   String title = LanguageManager.isEnglish(context) ? "Feed" : "ফিড";
 */
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

    // ── Pick string based on current language ──────────────────────────────

    /**
     * Returns en if language is English, bn if Bangla.
     * Convenience wrapper for all UI text.
     */
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
}