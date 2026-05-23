package com.example.towncrierbd.activities;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.*;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.towncrierbd.R;
import com.example.towncrierbd.models.Announcement;
import com.example.towncrierbd.models.UserModel;
import com.example.towncrierbd.utils.AudioRecorderHelper;
import com.example.towncrierbd.utils.CategoryConfig;
import com.example.towncrierbd.utils.CloudinaryUploader;
import com.example.towncrierbd.utils.Constants;
import com.example.towncrierbd.utils.NotificationSender;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class AddAnnouncementActivity extends AppCompatActivity {

    // ── Unit constants ─────────────────────────────────────────────────────
    private static final int UNIT_MINUTES = 0;
    private static final int UNIT_HOURS   = 1;
    private static final int UNIT_DAYS    = 2;

    // Limits (in minutes)
    private static final long MIN_MINUTES = 1;
    private static final long MAX_MINUTES = 7 * 24 * 60; // 7 days

    // ── Views ──────────────────────────────────────────────────────────────
    private Spinner  spCategory, spSubcategory, spExpiryUnit;
    private EditText etCustomSub, etTitle, etDesc, etExpiryValue;
    private TextView tvExpiryPreview;
    private Button   btnCancel, btnPublish;
    private ImageView btnClose, ivPreview;
    private View     btnAddImage, btnRecordAudio;
    private TextView tvImageStatus, tvAudioStatus;

    // ── Firebase ───────────────────────────────────────────────────────────
    private FirebaseAuth      auth;
    private DatabaseReference annRef, userRef;

    // ── Media ──────────────────────────────────────────────────────────────
    private Bitmap selectedBitmap    = null;
    private AudioRecorderHelper audioRecorder;
    private String  recordedAudioPath = null;
    private boolean isRecording       = false;

    // ── Launchers ──────────────────────────────────────────────────────────
    private ActivityResultLauncher<Intent>   galleryLauncher;
    private ActivityResultLauncher<Intent>   audioFileLauncher;
    private ActivityResultLauncher<String[]> permissionLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_announcement);

        // Bind views
        spCategory      = findViewById(R.id.spCategory);
        spSubcategory   = findViewById(R.id.spSubcategory);
        spExpiryUnit    = findViewById(R.id.spExpiryUnit);
        etCustomSub     = findViewById(R.id.etCustomSub);
        etTitle         = findViewById(R.id.etTitle);
        etDesc          = findViewById(R.id.etDesc);
        etExpiryValue   = findViewById(R.id.etExpiryValue);
        tvExpiryPreview = findViewById(R.id.tvExpiryPreview);
        btnCancel       = findViewById(R.id.btnCancel);
        btnPublish      = findViewById(R.id.btnPublish);
        btnClose        = findViewById(R.id.btnClose);
        btnAddImage     = findViewById(R.id.btnAddImage);
        btnRecordAudio  = findViewById(R.id.btnRecordAudio);
        tvImageStatus   = findViewById(R.id.tvImageStatus);
        tvAudioStatus   = findViewById(R.id.tvAudioStatus);
        ivPreview       = findViewById(R.id.ivPreview);

        audioRecorder = new AudioRecorderHelper(this);

        auth    = FirebaseAuth.getInstance();
        annRef  = FirebaseDatabase.getInstance().getReference(Constants.DB_ANNOUNCEMENTS);
        userRef = FirebaseDatabase.getInstance().getReference(Constants.DB_USERS);

        setupPermissions();
        setupLaunchers();
        setupCategoryUI();
        setupExpiryUI();

        btnCancel.setOnClickListener(v -> finish());
        if (btnClose != null) btnClose.setOnClickListener(v -> finish());
        btnPublish.setOnClickListener(v -> publish());
        if (btnAddImage    != null) btnAddImage.setOnClickListener(v -> showImageChooser());
        if (btnRecordAudio != null) btnRecordAudio.setOnClickListener(v -> showAudioOptions());
    }

    // ══════════════════════════════════════════════════════════════════════
    // Expiry UI
    // ══════════════════════════════════════════════════════════════════════

    private void setupExpiryUI() {
        // Unit spinner: Minutes / Hours / Days
        ArrayAdapter<String> unitAdapter = new ArrayAdapter<>(
                this,
                R.layout.spinner_selected_white,
                new String[]{"Minutes", "Hours", "Days"}
        );
        unitAdapter.setDropDownViewResource(R.layout.spinner_dropdown_dark);
        spExpiryUnit.setAdapter(unitAdapter);
        spExpiryUnit.setSelection(UNIT_HOURS); // default → Hours

        // Live preview whenever value or unit changes
        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {
                updateExpiryPreview();
            }
        };
        etExpiryValue.addTextChangedListener(watcher);

        spExpiryUnit.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                updateExpiryPreview();
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });
    }

    /** Returns expiry duration in milliseconds, or -1 if invalid / empty. */
    private long getExpiryMillis() {
        String raw = etExpiryValue.getText().toString().trim();
        if (raw.isEmpty()) return -1;

        double value;
        try {
            value = Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            return -1;
        }

        if (value <= 0) return -1;

        int unit = spExpiryUnit.getSelectedItemPosition();
        long minutes;
        switch (unit) {
            case UNIT_MINUTES: minutes = (long) value;               break;
            case UNIT_DAYS:    minutes = (long)(value * 24 * 60);    break;
            default:           minutes = (long)(value * 60);         break; // UNIT_HOURS
        }

        if (minutes < MIN_MINUTES) return -1;
        if (minutes > MAX_MINUTES) return -2; // over limit

        return minutes * 60_000L;
    }

    private void updateExpiryPreview() {
        if (tvExpiryPreview == null) return;
        long millis = getExpiryMillis();
        if (millis == -1) {
            tvExpiryPreview.setText("");
            return;
        }
        if (millis == -2) {
            tvExpiryPreview.setText("⚠️ Maximum 7 days allowed");
            tvExpiryPreview.setTextColor(0xFFEF4444);
            return;
        }
        tvExpiryPreview.setTextColor(0xFF6B7280);
        tvExpiryPreview.setText("⏳ Post will expire in " + humanReadable(millis));
    }

    /** Converts millis to a human-friendly string like "2 hours 30 minutes" */
    private String humanReadable(long millis) {
        long totalMinutes = millis / 60_000L;
        long days    = totalMinutes / (24 * 60);
        long hours   = (totalMinutes % (24 * 60)) / 60;
        long minutes = totalMinutes % 60;

        StringBuilder sb = new StringBuilder();
        if (days > 0)    sb.append(days).append(days == 1 ? " day"    : " days");
        if (hours > 0)   { if (sb.length() > 0) sb.append(" "); sb.append(hours).append(hours == 1 ? " hour"   : " hours"); }
        if (minutes > 0) { if (sb.length() > 0) sb.append(" "); sb.append(minutes).append(minutes == 1 ? " minute" : " minutes"); }
        return sb.length() > 0 ? sb.toString() : "less than a minute";
    }

    // ══════════════════════════════════════════════════════════════════════
    // Audio
    // ══════════════════════════════════════════════════════════════════════

    private void showAudioOptions() {
        if (isRecording) { stopAudioRecording(); return; }

        new AlertDialog.Builder(this)
                .setTitle("Add Audio")
                .setItems(new String[]{"🎙️ Record Audio", "📁 Select Audio File"}, (dialog, which) -> {
                    if (which == 0) startAudioRecording();
                    else            openAudioFilePicker();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void openAudioFilePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("audio/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        audioFileLauncher.launch(intent);
    }

    private void handleSelectedAudioFile(Uri uri) {
        if (uri == null) return;
        try {
            String fileName = "tc_audio_selected_" + System.currentTimeMillis() + ".m4a";
            File outFile = new File(getCacheDir(), fileName);
            InputStream in  = getContentResolver().openInputStream(uri);
            if (in == null) { toast("Could not read audio file"); return; }
            OutputStream out = new FileOutputStream(outFile);
            byte[] buf = new byte[8192]; int read;
            while ((read = in.read(buf)) != -1) out.write(buf, 0, read);
            in.close(); out.close();
            recordedAudioPath = outFile.getAbsolutePath();
            if (tvAudioStatus != null)
                tvAudioStatus.setText("✅ Audio file selected — will upload on Publish");
            updateAudioButton(false);
        } catch (IOException e) {
            toast("Failed to load audio file: " + e.getMessage());
        }
    }

    private void startAudioRecording() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(new String[]{Manifest.permission.RECORD_AUDIO});
            return;
        }
        recordedAudioPath = null;
        audioRecorder.startRecording(new AudioRecorderHelper.RecordListener() {
            @Override public void onRecordStarted() {
                isRecording = true;
                runOnUiThread(() -> {
                    updateAudioButton(true);
                    if (tvAudioStatus != null)
                        tvAudioStatus.setText("🔴 Recording... (tap again to stop)");
                });
            }
            @Override public void onRecordStopped(String localPath) {}
            @Override public void onError(String message) {
                isRecording = false;
                runOnUiThread(() -> {
                    updateAudioButton(false);
                    if (tvAudioStatus != null) tvAudioStatus.setText("❌ Error: " + message);
                    toast("Recording error: " + message);
                });
            }
        });
    }

    private void stopAudioRecording() {
        audioRecorder.stopRecording(new AudioRecorderHelper.RecordListener() {
            @Override public void onRecordStarted() {}
            @Override public void onRecordStopped(String localPath) {
                isRecording = false;
                recordedAudioPath = localPath;
                runOnUiThread(() -> {
                    updateAudioButton(false);
                    if (tvAudioStatus != null)
                        tvAudioStatus.setText("✅ Audio recorded — will upload on Publish");
                });
            }
            @Override public void onError(String message) {
                isRecording = false;
                runOnUiThread(() -> {
                    updateAudioButton(false);
                    if (tvAudioStatus != null) tvAudioStatus.setText("❌ Stop failed: " + message);
                });
            }
        });
    }

    private void updateAudioButton(boolean recording) {
        if (btnRecordAudio == null) return;
        if (btnRecordAudio instanceof LinearLayout) {
            LinearLayout ll = (LinearLayout) btnRecordAudio;
            for (int i = 0; i < ll.getChildCount(); i++) {
                View child = ll.getChildAt(i);
                if (child instanceof TextView)
                    ((TextView) child).setText(recording ? "⏹ Stop Recording" : "Add Audio");
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // Permissions
    // ══════════════════════════════════════════════════════════════════════

    private void setupPermissions() {
        permissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                    Boolean audioGranted = result.get(Manifest.permission.RECORD_AUDIO);
                    if (Boolean.TRUE.equals(audioGranted)) startAudioRecording();
                });
    }

    private void requestImagePermissionsIfNeeded() {
        List<String> need = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                    != PackageManager.PERMISSION_GRANTED)
                need.add(Manifest.permission.READ_MEDIA_IMAGES);
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED)
                need.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        }
        if (!need.isEmpty()) permissionLauncher.launch(need.toArray(new String[0]));
    }

    // ══════════════════════════════════════════════════════════════════════
    // Launchers
    // ══════════════════════════════════════════════════════════════════════

    private void setupLaunchers() {
        galleryLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), res -> {
                    if (res.getResultCode() == RESULT_OK && res.getData() != null) {
                        Uri uri = res.getData().getData();
                        if (uri == null) return;
                        try {
                            selectedBitmap = MediaStore.Images.Media
                                    .getBitmap(getContentResolver(), uri);
                            if (ivPreview != null) {
                                ivPreview.setVisibility(View.VISIBLE);
                                ivPreview.setImageBitmap(selectedBitmap);
                            }
                            if (tvImageStatus != null)
                                tvImageStatus.setText("Image selected ✅");
                        } catch (IOException e) { toast("Image read failed"); }
                    }
                });

        audioFileLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), res -> {
                    if (res.getResultCode() == RESULT_OK && res.getData() != null)
                        handleSelectedAudioFile(res.getData().getData());
                });
    }

    private void showImageChooser() {
        requestImagePermissionsIfNeeded();
        new AlertDialog.Builder(this)
                .setTitle("Add Image")
                .setItems(new String[]{"Gallery"}, (d, which) -> openGallery())
                .show();
    }

    private void openGallery() {
        Intent i = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        i.setType("image/*");
        galleryLauncher.launch(i);
    }

    // ══════════════════════════════════════════════════════════════════════
    // Category UI
    // ══════════════════════════════════════════════════════════════════════

    private void setupCategoryUI() {
        List<String> cats = new ArrayList<>(CategoryConfig.MAIN);
        ArrayAdapter<String> catAdapter = new ArrayAdapter<>(
                this, R.layout.spinner_selected_white, cats);
        catAdapter.setDropDownViewResource(R.layout.spinner_dropdown_dark);
        spCategory.setAdapter(catAdapter);

        spCategory.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                updateSubcategoryUI(getSelected(spCategory));
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });

        spSubcategory.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                String cat = getSelected(spCategory);
                String sub = getSelected(spSubcategory);
                if ("Others".equals(cat)) showCustom(true, "Optional: Write details");
                else if ("Other".equalsIgnoreCase(sub)) showCustom(true, "Specify other (optional)");
                else showCustom(false, "");
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });
    }

    private void updateSubcategoryUI(String cat) {
        List<String> subs = new ArrayList<>();
        subs.add("Select subcategory");
        if (!"Others".equals(cat)) subs.addAll(CategoryConfig.getSubcategories(cat));
        ArrayAdapter<String> subAdapter = new ArrayAdapter<>(
                this, R.layout.spinner_dropdown_dark, subs);
        subAdapter.setDropDownViewResource(R.layout.spinner_dropdown_dark);
        spSubcategory.setAdapter(subAdapter);
        showCustom("Others".equals(cat), "Optional: Write details");
    }

    private void showCustom(boolean show, String hint) {
        if (etCustomSub == null) return;
        etCustomSub.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) etCustomSub.setHint(hint);
        if (!show) etCustomSub.setText("");
    }

    private String getSelected(Spinner sp) {
        Object o = sp.getSelectedItem();
        return o == null ? "" : o.toString().trim();
    }

    // ══════════════════════════════════════════════════════════════════════
    // Publish
    // ══════════════════════════════════════════════════════════════════════

    private void publish() {
        if (isRecording) { toast("Please stop recording first"); return; }

        String title  = etTitle.getText().toString().trim();
        String desc   = etDesc.getText().toString().trim();
        String cat    = getSelected(spCategory);
        String subSel = getSelected(spSubcategory);
        if ("Select subcategory".equalsIgnoreCase(subSel)) subSel = "";
        String custom = etCustomSub.getText().toString().trim();

        // ── Validate required fields ──────────────────────────────────────
        if (title.isEmpty()) { toast("Title is required"); etTitle.requestFocus(); return; }
        if (desc.isEmpty())  { toast("Description is required"); etDesc.requestFocus(); return; }

        // ── Validate expiry (required) ────────────────────────────────────
        long expiryMillis = getExpiryMillis();
        if (expiryMillis == -1) {
            toast("Please set an expiry time");
            etExpiryValue.requestFocus();
            return;
        }
        if (expiryMillis == -2) {
            toast("Maximum expiry time is 7 days");
            etExpiryValue.requestFocus();
            return;
        }

        String uid = auth.getUid();
        if (uid == null) return;

        btnPublish.setEnabled(false);
        if (tvImageStatus != null) tvImageStatus.setText("Please wait...");

        final String fCat    = cat, fSub = subSel, fCustom = custom;
        final String fTitle  = title, fDesc = desc;
        final long   fExpiry = expiryMillis;

        userRef.child(uid).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                UserModel u = snapshot.getValue(UserModel.class);
                if (u == null) {
                    reset(); toast("User not found"); return;
                }
                if (u.getLat() == 0.0 && u.getLng() == 0.0) {
                    reset(); toast("Location not detected. Open Feed once and try again."); return;
                }

                String id = annRef.push().getKey();
                if (id == null) { reset(); return; }

                long now      = System.currentTimeMillis();
                long expireAt = now + fExpiry;

                Announcement a = new Announcement();
                a.setId(id);
                a.setUserId(uid);
                a.setUserName(u.getName());
                String role = (u.getRole() == null || u.getRole().trim().isEmpty())
                        ? Constants.ROLE_USER : u.getRole().trim();
                a.setUserRole(role);
                a.setCategory(fCat);
                a.setSubcategory(fSub);
                a.setCustomSubcategory(fCustom);
                a.setTitle(fTitle);
                a.setDescription(fDesc);
                a.setPhone(u.getPhone());
                a.setLat(u.getLat());
                a.setLng(u.getLng());
                a.setTime(now);
                a.setExpireAt(expireAt);

                // Step 1: Image
                if (selectedBitmap != null) {
                    if (tvImageStatus != null) tvImageStatus.setText("Uploading image...");
                    CloudinaryUploader.uploadBitmap(
                            AddAnnouncementActivity.this, selectedBitmap,
                            new CloudinaryUploader.UploadListener() {
                                @Override public void onSuccess(String url) {
                                    a.setImageUrl(url);
                                    uploadAudioThenSave(a, u, id);
                                }
                                @Override public void onError(String msg) {
                                    a.setImageUrl("");
                                    toast("Image upload failed, posting without image");
                                    uploadAudioThenSave(a, u, id);
                                }
                            });
                } else {
                    a.setImageUrl("");
                    uploadAudioThenSave(a, u, id);
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {
                reset(); toast("Failed: " + error.getMessage());
            }
        });
    }

    private void uploadAudioThenSave(Announcement a, UserModel u, String annId) {
        if (recordedAudioPath != null && !recordedAudioPath.isEmpty()) {
            if (tvImageStatus != null) tvImageStatus.setText("Uploading audio...");
            AudioRecorderHelper.uploadAudio(recordedAudioPath, annId,
                    new AudioRecorderHelper.UploadListener() {
                        @Override public void onProgress(int pct) {
                            runOnUiThread(() -> {
                                if (tvImageStatus != null)
                                    tvImageStatus.setText("Uploading audio... " + pct + "%");
                            });
                        }
                        @Override public void onSuccess(String url) {
                            a.setAudioUrl(url);
                            runOnUiThread(() -> saveAnnouncement(a, u));
                        }
                        @Override public void onError(String msg) {
                            a.setAudioUrl("");
                            runOnUiThread(() -> {
                                toast("Audio upload failed, posting without audio");
                                saveAnnouncement(a, u);
                            });
                        }
                    });
        } else {
            a.setAudioUrl("");
            saveAnnouncement(a, u);
        }
    }

    private void saveAnnouncement(Announcement a, UserModel u) {
        if (tvImageStatus != null) tvImageStatus.setText("Publishing...");
        annRef.child(a.getId()).setValue(a)
                .addOnSuccessListener(v -> {
                    NotificationSender.sendAnnouncementNotification(
                            a.getId(), a.getTitle(), a.getDescription(),
                            a.getLat(), a.getLng(), a.getUserId());
                    toast("Published ✅");
                    finish();
                })
                .addOnFailureListener(e -> {
                    reset(); toast("Save failed: " + e.getMessage());
                });
    }

    private void reset() {
        btnPublish.setEnabled(true);
        if (tvImageStatus != null) tvImageStatus.setText("");
    }

    // ══════════════════════════════════════════════════════════════════════
    // Lifecycle
    // ══════════════════════════════════════════════════════════════════════

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (audioRecorder != null && isRecording) audioRecorder.cancelRecording();
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}