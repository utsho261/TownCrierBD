package com.example.towncrierbd.utils;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;

import com.example.towncrierbd.BuildConfig;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Real-time In-App Force Update Manager (OTA Self-Update).
 * Monitors Firebase "app_update" node. If a newer versionCode is released,
 * blocks the app UI with a non-dismissible dialog until updated.
 */
public final class AppUpdateManager {
    private static final String TAG = "AppUpdateManager";
    private static AlertDialog activeUpdateDialog = null;

    private AppUpdateManager() {}

    /**
     * Start listening for real-time app updates.
     * Call this in onCreate() of main activities (LoginActivity, GeneralFeedActivity, AnnouncerFeedActivity).
     */
    public static ValueEventListener listenForUpdates(Activity activity) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return null;

        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("app_update");
        ValueEventListener listener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (activity.isFinishing() || activity.isDestroyed()) return;

                try {
                    if (!snapshot.exists()) {
                        // Seed initial app_update node if missing so developer can edit it easily in Firebase Console
                        ref.child("latest_version_code").setValue(BuildConfig.VERSION_CODE);
                        ref.child("latest_version_name").setValue(BuildConfig.VERSION_NAME);
                        ref.child("download_url").setValue("");
                        ref.child("release_notes").setValue("New features and bug fixes / নতুন ফিচার ও বাগ ফিক্স যুক্ত করা হয়েছে।");
                        ref.child("force_update").setValue(true);
                        return;
                    }

                    Object rawCode = snapshot.child("latest_version_code").getValue();
                    long latestCode = 0;
                    if (rawCode instanceof Number) {
                        latestCode = ((Number) rawCode).longValue();
                    } else if (rawCode instanceof String) {
                        try {
                            latestCode = (long) Double.parseDouble((String) rawCode);
                        } catch (Exception ignored) {}
                    }
                    if (latestCode <= 0) return;

                    int currentCode = BuildConfig.VERSION_CODE;
                    if (latestCode > currentCode) {
                        Object rawVersionName = snapshot.child("latest_version_name").getValue();
                        String versionName = rawVersionName != null ? String.valueOf(rawVersionName) : "";

                        Object rawDownloadUrl = snapshot.child("download_url").getValue();
                        String downloadUrl = rawDownloadUrl != null ? String.valueOf(rawDownloadUrl) : "";

                        Object rawReleaseNotes = snapshot.child("release_notes").getValue();
                        String releaseNotes = rawReleaseNotes != null ? String.valueOf(rawReleaseNotes) : "";

                        Object rawForce = snapshot.child("force_update").getValue();
                        boolean forceUpdate = false;
                        if (rawForce instanceof Boolean) {
                            forceUpdate = (Boolean) rawForce;
                        } else if (rawForce instanceof String) {
                            forceUpdate = Boolean.parseBoolean((String) rawForce);
                        }

                        showUpdateDialog(activity, versionName, downloadUrl, releaseNotes, forceUpdate);
                    } else {
                        dismissDialog();
                    }
                } catch (Throwable t) {
                    Log.e(TAG, "Error checking update: " + t.getMessage(), t);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.w(TAG, "Update check cancelled: " + error.getMessage());
            }
        };

        ref.addValueEventListener(listener);
        return listener;
    }

    private static void showUpdateDialog(Activity activity, String versionName, String downloadUrl, String releaseNotes, boolean force) {
        if (activeUpdateDialog != null && activeUpdateDialog.isShowing()) return;

        boolean isBn = !LanguageManager.isEnglish(activity);
        String title = isBn ? "📢 নতুন আপডেট এসেছে!" : "📢 New Update Available!";
        String versionLabel = (versionName != null && !versionName.isEmpty()) ? (" v" + versionName) : "";

        StringBuilder message = new StringBuilder();
        message.append(isBn ? "আপনার Town Crier BD অ্যাপটি" : "Your Town Crier BD app");
        message.append(versionLabel);
        message.append(isBn ? " নতুন ভার্সনে আপডেট করতে হবে।\n\n" : " needs to be updated.\n\n");

        if (releaseNotes != null && !releaseNotes.trim().isEmpty()) {
            message.append(isBn ? "নতুন কী আছে:\n" : "What's new:\n");
            message.append(releaseNotes.trim()).append("\n\n");
        }

        message.append(isBn ? "অ্যাপটি ব্যবহার করতে এখনই আপডেট করুন।" : "Please update now to continue using the app.");

        AlertDialog.Builder builder = new AlertDialog.Builder(activity)
                .setTitle(title)
                .setMessage(message.toString())
                .setCancelable(!force)
                .setPositiveButton(isBn ? "⬇️ এখনই আপডেট করুন" : "⬇️ Update Now", (dialog, which) -> {
                    startUpdate(activity, downloadUrl);
                });

        if (!force) {
            builder.setNegativeButton(isBn ? "পরে" : "Later", null);
        }

        activeUpdateDialog = builder.create();
        activeUpdateDialog.setCanceledOnTouchOutside(!force);
        activeUpdateDialog.show();
    }

    private static void dismissDialog() {
        if (activeUpdateDialog != null && activeUpdateDialog.isShowing()) {
            try {
                activeUpdateDialog.dismiss();
            } catch (Exception ignored) {}
            activeUpdateDialog = null;
        }
    }

    private static void startUpdate(Activity activity, String downloadUrl) {
        if (downloadUrl == null || downloadUrl.trim().isEmpty()) {
            Toast.makeText(activity,
                    PostCatalog.text(activity, "Download link not set. Please contact admin.", "ডাউনলোড লিঙ্ক পাওয়া যায়নি। অনুগ্রহ করে অপেক্ষা করুন।"),
                    Toast.LENGTH_LONG).show();
            return;
        }

        String url = downloadUrl.trim();

        // If URL points directly to an APK, download in-app and launch installer
        if (url.toLowerCase().endsWith(".apk") || url.contains(".apk?")) {
            downloadAndInstallApk(activity, url);
        } else {
            // Otherwise, open external link (e.g. Google Drive, MediaFire, web page) in browser
            try {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                activity.startActivity(browserIntent);
            } catch (Exception e) {
                Toast.makeText(activity, "Could not open link: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private static void downloadAndInstallApk(Activity activity, String apkUrl) {
        boolean isBn = !LanguageManager.isEnglish(activity);
        ProgressDialog progress = new ProgressDialog(activity);
        progress.setTitle(isBn ? "আপডেট ডাউনলোড হচ্ছে..." : "Downloading update...");
        progress.setMessage(isBn ? "অনুগ্রহ করে অপেক্ষা করুন" : "Please wait");
        progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progress.setCancelable(false);
        progress.setMax(100);
        progress.show();

        new Thread(() -> {
            try {
                URL url = new URL(apkUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.connect();

                int fileLength = conn.getContentLength();
                File downloadDir = activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                if (downloadDir == null) downloadDir = activity.getCacheDir();
                File apkFile = new File(downloadDir, "towncrierbd_update.apk");

                if (apkFile.exists()) apkFile.delete();

                InputStream input = conn.getInputStream();
                FileOutputStream output = new FileOutputStream(apkFile);

                byte[] data = new byte[4096];
                long total = 0;
                int count;
                while ((count = input.read(data)) != -1) {
                    total += count;
                    if (fileLength > 0) {
                        int currentProgress = (int) (total * 100 / fileLength);
                        activity.runOnUiThread(() -> progress.setProgress(currentProgress));
                    }
                    output.write(data, 0, count);
                }

                output.flush();
                output.close();
                input.close();

                activity.runOnUiThread(() -> {
                    progress.dismiss();
                    installApk(activity, apkFile);
                });

            } catch (Exception e) {
                Log.e(TAG, "Download failed", e);
                activity.runOnUiThread(() -> {
                    progress.dismiss();
                    Toast.makeText(activity, (isBn ? "ডাউনলোড ব্যর্থ হয়েছে। লিঙ্কে যাচ্ছি..." : "Download failed. Opening link..."), Toast.LENGTH_SHORT).show();
                    try {
                        activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(apkUrl)));
                    } catch (Exception ignored) {}
                });
            }
        }).start();
    }

    public static void installApk(Activity activity, File apkFile) {
        if (!apkFile.exists()) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!activity.getPackageManager().canRequestPackageInstalls()) {
                new AlertDialog.Builder(activity)
                        .setTitle(PostCatalog.text(activity, "Permission required", "অনুমতি আবশ্যক"))
                        .setMessage(PostCatalog.text(activity,
                                "Allow Town Crier BD to install app updates from this source.",
                                "অ্যাপ আপডেট ইনস্টল করতে Town Crier BD-কে অনুমতি দিন।"))
                        .setPositiveButton(PostCatalog.text(activity, "Settings", "সেটিংস"), (d, w) -> {
                            Intent settingsIntent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
                            settingsIntent.setData(Uri.parse("package:" + activity.getPackageName()));
                            activity.startActivity(settingsIntent);
                        })
                        .setNegativeButton(PostCatalog.text(activity, "Cancel", "বাতিল"), null)
                        .show();
                return;
            }
        }

        try {
            Uri apkUri = FileProvider.getUriForFile(activity, activity.getPackageName() + ".fileprovider", apkFile);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(activity, "Install error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
}
