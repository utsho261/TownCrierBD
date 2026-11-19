package com.example.towncrierbd.utils;

import android.content.Context;
import android.media.MediaRecorder;
import android.os.Build;
import android.util.Log;

import org.json.JSONObject;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class AudioRecorderHelper {

    private static final String TAG = "AudioRecorderHelper";

    // ── Interfaces ─────────────────────────────────────────────────────────

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

    // ── Fields ─────────────────────────────────────────────────────────────

    private MediaRecorder recorder;
    private String        outputPath;
    private boolean       isRecording = false;
    private final Context context;

    // ── Constructor ────────────────────────────────────────────────────────

    public AudioRecorderHelper(Context context) {
        this.context = context.getApplicationContext();
    }

    public boolean isRecording() { return isRecording; }

    public int getMaxAmplitude() {
        if (recorder != null && isRecording) {
            try {
                return recorder.getMaxAmplitude();
            } catch (Exception ignored) {}
        }
        return 0;
    }

    // ── Recording ──────────────────────────────────────────────────────────

    public void startRecording(RecordListener listener) {
        if (isRecording) { if (listener != null) listener.onError("Already recording"); return; }
        try {
            File dir = context.getCacheDir();
            if (!dir.exists()) dir.mkdirs();

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

        } catch (Exception e) {
            cancelRecording();
            if (listener != null) listener.onError("Recording failed: " + e.getMessage());
        }
    }

    public void stopRecording(RecordListener listener) {
        if (!isRecording || recorder == null) {
            if (listener != null) listener.onError("Not recording");
            return;
        }
        try {
            recorder.stop();
            recorder.release();
            recorder = null;
            isRecording = false;
            if (listener != null) listener.onRecordStopped(outputPath);
        } catch (Exception e) {
            cancelRecording();
            if (listener != null) listener.onError("Stop failed: " + e.getMessage());
        }
    }

    public void cancelRecording() {
        if (recorder != null) {
            try { recorder.stop();    } catch (Exception ignored) {}
            try { recorder.release(); } catch (Exception ignored) {}
            recorder = null;
        }
        isRecording = false;
        if (outputPath != null) {
            new File(outputPath).delete();
            outputPath = null;
        }
    }

    // ── Cloudinary Upload ──────────────────────────────────────────────────

    /** Audio assets use Cloudinary's video upload endpoint and retain their sound. */
    public static void uploadAudio(String localPath,
                                   String announcementId,
                                   UploadListener listener) {
        uploadAudio(localPath, announcementId, listener, false);
    }

    public static void uploadAudio(String localPath, String announcementId,
                                   UploadListener listener, boolean retainLocalCopy) {
        if (localPath == null || localPath.isEmpty()) {
            if (listener != null) listener.onError("No audio file path");
            return;
        }

        File file = new File(localPath);
        if (!file.exists() || file.length() == 0) {
            if (listener != null) listener.onError("Audio file missing or empty");
            return;
        }

        new Thread(() -> {
            try {
                String url = doUpload(file, announcementId + "_" + java.util.UUID.randomUUID(), listener);
                if (!retainLocalCopy) file.delete();
                if (url != null && listener != null) {
                    listener.onSuccess(url);
                }
            } catch (Exception e) {
                Log.e(TAG, "Cloudinary upload error: " + e.getMessage());
                if (listener != null)
                    listener.onError("Upload failed: " + e.getMessage());
            }
        }).start();
    }

    // ── Core multipart upload ──────────────────────────────────────────────

    private static String doUpload(File file,
                                   String publicId,
                                   UploadListener listener) throws Exception {

        String cloudName    = Constants.CLOUDINARY_CLOUD_NAME;
        String uploadPreset = Constants.CLOUDINARY_UPLOAD_PRESET;

        // Cloudinary treats audio as a video asset without a visual track.
        String apiUrl    = "https://api.cloudinary.com/v1_1/" + cloudName + "/video/upload";
        String boundary  = "TCBoundary" + System.currentTimeMillis();
        String CRLF      = "\r\n";
        String DASHDASH  = "--";

        HttpURLConnection conn = (HttpURLConnection) new URL(apiUrl).openConnection();
        conn.setDoInput(true);
        conn.setDoOutput(true);
        conn.setUseCaches(false);
        conn.setConnectTimeout(30_000);
        conn.setReadTimeout(120_000);   // audio বড় হলে বেশি সময় লাগতে পারে
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type",
                "multipart/form-data; boundary=" + boundary);

        DataOutputStream out = new DataOutputStream(conn.getOutputStream());

        // ── upload_preset field ────────────────────────────────────────────
        out.writeBytes(DASHDASH + boundary + CRLF);
        out.writeBytes("Content-Disposition: form-data; name=\"upload_preset\"" + CRLF + CRLF);
        out.writeBytes(uploadPreset + CRLF);

        // ── public_id field (Cloudinary-তে folder/name) ───────────────────
        out.writeBytes(DASHDASH + boundary + CRLF);
        out.writeBytes("Content-Disposition: form-data; name=\"public_id\"" + CRLF + CRLF);
        out.writeBytes("announcement_audio/" + publicId + CRLF);

        // Determine dynamic extension & MIME type from actual file
        String ext = "m4a";
        String contentType = "audio/mp4";
        String lowerName = file.getName().toLowerCase(java.util.Locale.ROOT);
        if (lowerName.endsWith(".mp3")) {
            ext = "mp3";
            contentType = "audio/mpeg";
        } else if (lowerName.endsWith(".wav")) {
            ext = "wav";
            contentType = "audio/wav";
        } else if (lowerName.endsWith(".ogg")) {
            ext = "ogg";
            contentType = "audio/ogg";
        } else if (lowerName.endsWith(".aac")) {
            ext = "aac";
            contentType = "audio/aac";
        } else if (lowerName.endsWith(".flac")) {
            ext = "flac";
            contentType = "audio/flac";
        } else if (lowerName.endsWith(".opus")) {
            ext = "opus";
            contentType = "audio/ogg";
        } else if (lowerName.endsWith(".amr")) {
            ext = "amr";
            contentType = "audio/amr";
        } else if (lowerName.endsWith(".3gp")) {
            ext = "3gp";
            contentType = "audio/3gpp";
        }

        // ── file field ────────────────────────────────────────────────────
        out.writeBytes(DASHDASH + boundary + CRLF);
        out.writeBytes("Content-Disposition: form-data; name=\"file\"; "
                + "filename=\"audio_" + publicId + "." + ext + "\"" + CRLF);
        out.writeBytes("Content-Type: " + contentType + CRLF + CRLF);

        // File bytes + progress
        long   total    = file.length();
        long   written  = 0;
        int    lastPct  = -1;
        byte[] buf      = new byte[8192];

        try (FileInputStream fis = new FileInputStream(file)) {
            int read;
            while ((read = fis.read(buf)) != -1) {
                out.write(buf, 0, read);
                written += read;
                int pct = (int) (100L * written / total);
                if (pct != lastPct) {
                    lastPct = pct;
                    if (listener != null) listener.onProgress(pct);
                }
            }
        }

        out.writeBytes(CRLF + DASHDASH + boundary + DASHDASH + CRLF);
        out.flush();
        out.close();

        // ── Response ──────────────────────────────────────────────────────
        int code = conn.getResponseCode();
        Log.d(TAG, "Cloudinary HTTP response: " + code);

        InputStream responseStream = (code == 200)
                ? conn.getInputStream()
                : conn.getErrorStream();

        java.io.BufferedReader br = new java.io.BufferedReader(
                new java.io.InputStreamReader(responseStream));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        br.close();

        String body = sb.toString();
        Log.d(TAG, "Cloudinary response body: " + body);

        if (code != 200) {
            throw new Exception("HTTP " + code + " — " + body);
        }

        JSONObject json = new JSONObject(body);

        if (json.has("secure_url")) {
            String secureUrl = json.getString("secure_url");
            Log.d(TAG, "Audio uploaded successfully: " + secureUrl);
            return secureUrl;
        } else {
            throw new Exception("No secure_url in response: " + body);
        }
    }

    // ── Delete ────────────────────────────────────────────────────────────

    /**
     * Cloudinary unsigned delete support করে না।
     * তাই এখানে শুধু Firebase DB থেকে audioUrl field null করা হয়।
     * Actual Cloudinary file delete করতে হলে server-side করতে হবে।
     */
    public static void deleteAudio(String announcementId) {
        // Server-side (Firebase Functions বা Render.com server) থেকে করো।
        // Client-side Cloudinary delete করতে API secret লাগে যা app-এ রাখা unsafe।
        Log.d(TAG, "deleteAudio: " + announcementId
                + " — handle server-side for security");
    }
}