package com.example.towncrierbd.utils;

import android.content.Context;
import android.media.MediaRecorder;
import android.os.Build;

import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.File;
import java.io.IOException;

public class AudioRecorderHelper {

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
     * Upload recorded audio to Firebase Storage.
     * Path: announcement_audio/{announcementId}.m4a
     */
    public static void uploadAudio(String localPath, String announcementId,
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

        StorageReference ref = FirebaseStorage.getInstance()
                .getReference()
                .child(Constants.STORAGE_AUDIO_FOLDER)
                .child(announcementId + ".m4a");

        UploadTask task = ref.putFile(android.net.Uri.fromFile(file));

        task.addOnProgressListener(snapshot -> {
            if (listener == null) return;
            long total = snapshot.getTotalByteCount();
            long transferred = snapshot.getBytesTransferred();
            int pct = total > 0 ? (int) (100 * transferred / total) : 0;
            listener.onProgress(pct);
        });

        task.continueWithTask(t -> {
            if (!t.isSuccessful() && t.getException() != null)
                throw t.getException();
            return ref.getDownloadUrl();
        }).addOnSuccessListener(uri -> {
            file.delete(); // clean up local temp file
            if (listener != null) listener.onSuccess(uri.toString());
        }).addOnFailureListener(e -> {
            if (listener != null) listener.onError(e.getMessage() != null
                    ? e.getMessage() : "Upload failed");
        });
    }

    /**
     * Delete audio from Firebase Storage when post expires or is deleted.
     */
    public static void deleteAudio(String announcementId) {
        if (announcementId == null || announcementId.isEmpty()) return;
        FirebaseStorage.getInstance()
                .getReference()
                .child(Constants.STORAGE_AUDIO_FOLDER)
                .child(announcementId + ".m4a")
                .delete()
                .addOnFailureListener(e -> { /* ignore — file may not exist */ });
    }
}