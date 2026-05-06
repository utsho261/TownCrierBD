package com.example.towncrierbd.utils;

import android.content.Context;
import android.media.MediaRecorder;
import android.os.Build;
import android.util.Log;

import org.json.JSONObject;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class AudioRecorderHelper {

    private static final String TAG = "AudioRecorderHelper";

    public interface RecordListener {
        void onRecordStarted();
        void onRecordStopped(String localPath);
        void onError(String message);
    }

    public interface UploadListener {
        void onProgress(int percent);
        void onSuccess(String downloadUrl);
        void onError(String message);
    }

    private MediaRecorder recorder;
    private String outputPath;
    private boolean isRecording = false;

    private final Context context;

    public AudioRecorderHelper(Context context) {
        this.context = context;
    }

    public boolean isRecording() {
        return isRecording;
    }

    public void startRecording(RecordListener listener) {
        try {
            File dir = context.getCacheDir();
            File outFile = new File(dir, "tc_audio_" + System.currentTimeMillis() + ".m4a");
            outputPath = outFile.getAbsolutePath();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                recorder = new MediaRecorder(context);
            } else {
                //noinspection deprecation
                recorder = new MediaRecorder();
            }

            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setAudioSamplingRate(44100);
            recorder.setAudioEncodingBitRate(96000);
            recorder.setOutputFile(outputPath);
            recorder.prepare();
            recorder.start();

            isRecording = true;
            if (listener != null) listener.onRecordStarted();

        } catch (IOException | IllegalStateException e) {
            isRecording = false;
            if (listener != null) listener.onError("Recording failed: " + e.getMessage());
        }
    }

    public void stopRecording(RecordListener listener) {
        if (!isRecording || recorder == null) return;
        try {
            recorder.stop();
            recorder.release();
            recorder = null;
            isRecording = false;
            if (listener != null) listener.onRecordStopped(outputPath);
        } catch (Exception e) {
            recorder = null;
            isRecording = false;
            if (listener != null) listener.onError("Stop failed: " + e.getMessage());
        }
    }

    public void cancelRecording() {
        if (recorder != null) {
            try { recorder.stop(); } catch (Exception ignored) {}
            try { recorder.release(); } catch (Exception ignored) {}
            recorder = null;
        }
        isRecording = false;
        if (outputPath != null) {
            new File(outputPath).delete();
            outputPath = null;
        }
    }

    /**
     * Upload audio to Cloudinary using unsigned upload preset.
     * resource_type=video handles audio files (.m4a, .mp3, etc.)
     * Runs on background thread — wrap callbacks in runOnUiThread if needed.
     */
    public static void uploadAudio(String localPath,
                                   String announcementId,
                                   UploadListener listener) {
        if (localPath == null || localPath.isEmpty()) {
            if (listener != null) listener.onError("No audio file");
            return;
        }
        File file = new File(localPath);
        if (!file.exists()) {
            if (listener != null) listener.onError("Audio file not found");
            return;
        }

        new Thread(() -> {
            String boundary = "------CloudinaryBoundary" + System.currentTimeMillis();
            HttpURLConnection conn = null;
            try {
                // Cloudinary video endpoint handles audio files
                String uploadUrl = "https://api.cloudinary.com/v1_1/"
                        + Constants.CLOUDINARY_CLOUD_NAME
                        + "/video/upload";

                URL url = new URL(uploadUrl);
                conn = (HttpURLConnection) url.openConnection();
                conn.setDoOutput(true);
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type",
                        "multipart/form-data; boundary=" + boundary);
                conn.setConnectTimeout(30_000);
                conn.setReadTimeout(120_000);

                DataOutputStream out = new DataOutputStream(conn.getOutputStream());

                // upload_preset — must be configured in Cloudinary dashboard
                // to allow unsigned uploads for video/audio resource type
                writeField(out, boundary, "upload_preset", Constants.CLOUDINARY_UPLOAD_PRESET);

                // Use a predictable public_id so we can delete later
                String publicId = Constants.STORAGE_AUDIO_FOLDER + "/" + announcementId;
                writeField(out, boundary, "public_id", publicId);

                // Overwrite existing asset with same public_id
                writeField(out, boundary, "overwrite", "true");

                // File part
                out.writeBytes("--" + boundary + "\r\n");
                out.writeBytes("Content-Disposition: form-data; name=\"file\"; "
                        + "filename=\"" + file.getName() + "\"\r\n");
                out.writeBytes("Content-Type: audio/mp4\r\n\r\n");

                long fileSize = file.length();
                long bytesWritten = 0;
                byte[] buf = new byte[8192];
                int read;
                try (FileInputStream fis = new FileInputStream(file)) {
                    while ((read = fis.read(buf)) != -1) {
                        out.write(buf, 0, read);
                        bytesWritten += read;
                        if (listener != null && fileSize > 0) {
                            int pct = (int) (100 * bytesWritten / fileSize);
                            listener.onProgress(pct);
                        }
                    }
                }
                out.writeBytes("\r\n--" + boundary + "--\r\n");
                out.flush();
                out.close();

                int responseCode = conn.getResponseCode();
                InputStream is = responseCode == 200
                        ? conn.getInputStream()
                        : conn.getErrorStream();

                StringBuilder sb = new StringBuilder();
                byte[] tmp = new byte[4096];
                int n;
                while ((n = is.read(tmp)) != -1) sb.append(new String(tmp, 0, n));
                is.close();

                if (responseCode == 200) {
                    JSONObject json = new JSONObject(sb.toString());
                    String secureUrl = json.optString("secure_url", "");
                    file.delete(); // clean up local cache
                    if (listener != null) listener.onSuccess(secureUrl);
                } else {
                    Log.e(TAG, "Cloudinary error " + responseCode + ": " + sb);
                    if (listener != null)
                        listener.onError("Upload failed (" + responseCode + ")");
                }

            } catch (Exception e) {
                Log.e(TAG, "Audio upload exception: " + e.getMessage());
                if (listener != null)
                    listener.onError(e.getMessage() != null ? e.getMessage() : "Upload failed");
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }

    /**
     * Delete audio from Cloudinary via your Render.com server.
     * The server uses Cloudinary Admin API (api_key + api_secret) to delete.
     * POST /delete-audio  { "public_id": "announcement_audio/<announcementId>" }
     */
    public static void deleteAudio(String announcementId) {
        if (announcementId == null || announcementId.isEmpty()) return;

        new Thread(() -> {
            try {
                String publicId = Constants.STORAGE_AUDIO_FOLDER + "/" + announcementId;
                JSONObject body = new JSONObject();
                body.put("public_id", publicId);
                body.put("resource_type", "video"); // audio is resource_type=video in Cloudinary

                URL url = new URL(Constants.SERVER_URL + "/delete-audio");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);

                byte[] input = body.toString().getBytes("UTF-8");
                conn.getOutputStream().write(input, 0, input.length);

                int code = conn.getResponseCode();
                Log.d(TAG, "Delete audio response: " + code + " for " + publicId);
                conn.disconnect();
            } catch (Exception e) {
                Log.e(TAG, "deleteAudio error: " + e.getMessage());
            }
        }).start();
    }

    // ── Multipart helper ─────────────────────────────────────────────────────
    private static void writeField(DataOutputStream out,
                                   String boundary,
                                   String name,
                                   String value) throws IOException {
        out.writeBytes("--" + boundary + "\r\n");
        out.writeBytes("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n");
        out.writeBytes(value + "\r\n");
    }
}