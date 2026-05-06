package com.example.towncrierbd.utils;

import android.content.Context;
import android.graphics.Bitmap;

import com.cloudinary.android.MediaManager;
import com.cloudinary.android.callback.ErrorInfo;
import com.cloudinary.android.callback.UploadCallback;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.HashMap;
import java.util.Map;

public class CloudinaryUploader {

    private static boolean initialized = false;

    public static void init(Context context) {
        if (initialized) return;
        Map<String, String> config = new HashMap<>();
        config.put("cloud_name", Constants.CLOUDINARY_CLOUD_NAME);
        MediaManager.init(context, config);
        initialized = true;
    }

    // ── Common listener ───────────────────────────────────────────────────

    public interface UploadListener {
        void onSuccess(String url);
        void onError(String message);
    }

    // ── Image upload (Bitmap) ─────────────────────────────────────────────

    public static void uploadBitmap(Context context, Bitmap bitmap, UploadListener listener) {
        try {
            Bitmap scaled = scaleBitmap(bitmap, 900);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            scaled.compress(Bitmap.CompressFormat.JPEG, 75, baos);
            byte[] bytes = baos.toByteArray();

            File tempFile = File.createTempFile("tc_img_", ".jpg", context.getCacheDir());
            FileOutputStream fos = new FileOutputStream(tempFile);
            fos.write(bytes);
            fos.close();

            MediaManager.get()
                    .upload(tempFile.getAbsolutePath())
                    .unsigned(Constants.CLOUDINARY_UPLOAD_PRESET)
                    .callback(new UploadCallback() {
                        @Override public void onStart(String requestId) {}
                        @Override public void onProgress(String requestId, long bytes, long totalBytes) {}

                        @Override
                        public void onSuccess(String requestId, Map resultData) {
                            String url = (String) resultData.get("secure_url");
                            tempFile.delete();
                            listener.onSuccess(url != null ? url : "");
                        }

                        @Override
                        public void onError(String requestId, ErrorInfo error) {
                            tempFile.delete();
                            listener.onError(error.getDescription());
                        }

                        @Override public void onReschedule(String requestId, ErrorInfo error) {}
                    })
                    .dispatch();

        } catch (Exception e) {
            listener.onError(e.getMessage() != null ? e.getMessage() : "Image upload failed");
        }
    }

    // ── Audio upload (local .m4a file path) ──────────────────────────────

    public static void uploadAudio(String localFilePath, String announcementId,
                                   AudioUploadListener listener) {
        if (localFilePath == null || localFilePath.isEmpty()) {
            if (listener != null) listener.onError("No audio file path");
            return;
        }

        File file = new File(localFilePath);
        if (!file.exists()) {
            if (listener != null) listener.onError("Audio file not found");
            return;
        }

        // Cloudinary resource_type "video" দিলে audio ও accept করে
        MediaManager.get()
                .upload(localFilePath)
                .unsigned(Constants.CLOUDINARY_UPLOAD_PRESET)
                .option("resource_type", "video")          // audio র জন্য "video" লাগে Cloudinary তে
                .option("public_id", "announcement_audio/" + announcementId)
                .callback(new UploadCallback() {
                    @Override public void onStart(String requestId) {}

                    @Override
                    public void onProgress(String requestId, long bytes, long totalBytes) {
                        if (listener == null) return;
                        int pct = totalBytes > 0 ? (int)(100 * bytes / totalBytes) : 0;
                        listener.onProgress(pct);
                    }

                    @Override
                    public void onSuccess(String requestId, Map resultData) {
                        String url = (String) resultData.get("secure_url");
                        file.delete(); // local temp file clean up
                        if (listener != null)
                            listener.onSuccess(url != null ? url : "");
                    }

                    @Override
                    public void onError(String requestId, ErrorInfo error) {
                        if (listener != null) listener.onError(error.getDescription());
                    }

                    @Override public void onReschedule(String requestId, ErrorInfo error) {}
                })
                .dispatch();
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private static Bitmap scaleBitmap(Bitmap bmp, int maxSize) {
        int w = bmp.getWidth(), h = bmp.getHeight();
        if (w <= maxSize && h <= maxSize) return bmp;
        float ratio = Math.min((float) maxSize / w, (float) maxSize / h);
        return Bitmap.createScaledBitmap(bmp,
                Math.round(w * ratio), Math.round(h * ratio), true);
    }

    // ── Audio upload listener (progress সহ) ──────────────────────────────

    public interface AudioUploadListener {
        void onProgress(int percent);
        void onSuccess(String audioUrl);
        void onError(String message);
    }
}