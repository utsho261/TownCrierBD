package com.example.towncrierbd.activities;

import android.content.Context;
import android.content.Intent;

import androidx.appcompat.app.AppCompatActivity;

import com.example.towncrierbd.utils.LanguageManager;

/**
 * BaseActivity — extend this instead of AppCompatActivity in every activity.
 *
 * This ensures:
 * 1. Language is applied on every activity launch (attachBaseContext)
 * 2. All activities get the switchLanguage() helper method
 *
 * MIGRATION: Replace "extends AppCompatActivity" with "extends BaseActivity"
 * in ALL Activity classes.
 */
public abstract class BaseActivity extends AppCompatActivity {

    /**
     * CRITICAL: This method applies the saved language to every activity.
     * Without this, language changes won't take effect.
     */
    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LanguageManager.wrap(newBase));
    }

    /**
     * Toggle language and restart this activity to apply changes.
     * Call this from your language toggle button's onClick.
     */
    protected void switchLanguage() {
        LanguageManager.toggleLanguage(this);
        restartActivity();
    }

    /**
     * Restart the current activity smoothly to apply language change.
     */
    protected void restartActivity() {
        Intent intent = getIntent();
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        finish();
        startActivity(intent);
        // Optional: disable animation for seamless restart
        overridePendingTransition(0, 0);
    }
}