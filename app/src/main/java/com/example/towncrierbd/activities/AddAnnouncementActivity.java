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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class AddAnnouncementActivity extends AppCompatActivity {

    private Spinner spCategory, spSubcategory;
    private EditText etCustomSub, etTitle, etDesc, etHours;
    private Button btnCancel, btnPublish;
    private ImageView btnClose, ivPreview;
    private View btnAddImage, btnRecordAudio;
    private TextView tvImageStatus, tvAudioStatus;

    private FirebaseAuth auth;
    private DatabaseReference annRef, userRef;

    private Bitmap selectedBitmap = null;

    private AudioRecorderHelper audioRecorder;
    private String recordedAudioPath = null;
    private boolean isRecording = false;

    private ActivityResultLauncher<Intent>   galleryLauncher;
    private ActivityResultLauncher<String[]> permissionLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_announcement);

        spCategory    = findViewById(R.id.spCategory);
        spSubcategory = findViewById(R.id.spSubcategory);
        etCustomSub   = findViewById(R.id.etCustomSub);
        etTitle       = findViewById(R.id.etTitle);
        etDesc        = findViewById(R.id.etDesc);
        etHours       = findViewById(R.id.etHours);
        btnCancel     = findViewById(R.id.btnCancel);
        btnPublish    = findViewById(R.id.btnPublish);
        btnClose      = findViewById(R.id.btnClose);
        btnAddImage   = findViewById(R.id.btnAddImage);
        btnRecordAudio = findViewById(R.id.btnRecordAudio);
        tvImageStatus = findViewById(R.id.tvImageStatus);
        tvAudioStatus = findViewById(R.id.tvAudioStatus);
        ivPreview     = findViewById(R.id.ivPreview);

        audioRecorder = new AudioRecorderHelper(this);

        if (btnRecordAudio != null) btnRecordAudio.setVisibility(View.VISIBLE);
        if (tvAudioStatus  != null) tvAudioStatus.setVisibility(View.VISIBLE);

        auth    = FirebaseAuth.getInstance();
        annRef  = FirebaseDatabase.getInstance().getReference(Constants.DB_ANNOUNCEMENTS);
        userRef = FirebaseDatabase.getInstance().getReference(Constants.DB_USERS);

        setupPermissions();
        setupLaunchers();
        setupCategoryUI();

        btnCancel.setOnClickListener(v -> finish());
        if (btnClose != null) btnClose.setOnClickListener(v -> finish());
        btnPublish.setOnClickListener(v -> publish());
        if (btnAddImage    != null) btnAddImage.setOnClickListener(v -> showImageChooser());
        if (btnRecordAudio != null) btnRecordAudio.setOnClickListener(v -> handleAudioToggle());
    }

    // ── Audio recording toggle ─────────────────────────────────────────────

    private void handleAudioToggle() {
        if (!isRecording) {
            startAudioRecording();
        } else {
            stopAudioRecording();
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
                if (child instanceof TextView) {
                    ((TextView) child).setText(recording ? "Stop Recording" : "Record Audio");
                }
            }
        }
    }

    // ── Permission setup ───────────────────────────────────────────────────

    private void setupPermissions() {
        permissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                    Boolean audioGranted = result.get(Manifest.permission.RECORD_AUDIO);
                    if (Boolean.TRUE.equals(audioGranted)) {
                        startAudioRecording();
                    }
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

    // ── Gallery launcher ───────────────────────────────────────────────────

    private void setupLaunchers() {
        galleryLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                res -> {
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
                        } catch (IOException e) {
                            toast("Image read failed");
                        }
                    }
                }
        );
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

    // ── Category UI ────────────────────────────────────────────────────────

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

    // ── Publish ────────────────────────────────────────────────────────────

    private void publish() {
        if (isRecording) {
            toast("Please stop recording first");
            return;
        }

        String title    = etTitle.getText().toString().trim();
        String desc     = etDesc.getText().toString().trim();
        String cat      = getSelected(spCategory);
        String subSel   = getSelected(spSubcategory);
        if ("Select subcategory".equalsIgnoreCase(subSel)) subSel = "";
        String custom   = etCustomSub.getText().toString().trim();
        String hoursStr = etHours.getText().toString().trim();
        long hoursValue = 24;
        try { if (!hoursStr.isEmpty()) hoursValue = Long.parseLong(hoursStr); } catch (Exception ignored) {}

        if (hoursValue < 1)   hoursValue = 1;
        if (hoursValue > 168) hoursValue = 168;

        if (title.isEmpty() || desc.isEmpty()) {
            toast("Title & Description required");
            return;
        }

        String uid = auth.getUid();
        if (uid == null) return;

        btnPublish.setEnabled(false);
        if (tvImageStatus != null) tvImageStatus.setText("Please wait...");

        final String fCat    = cat,  fSub   = subSel, fCustom = custom;
        final String fTitle  = title, fDesc  = desc;
        final long   fHours  = hoursValue;

        userRef.child(uid).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                UserModel u = snapshot.getValue(UserModel.class);
                if (u == null) {
                    btnPublish.setEnabled(true);
                    if (tvImageStatus != null) tvImageStatus.setText("");
                    toast("User not found");
                    return;
                }

                if (u.getLat() == 0.0 && u.getLng() == 0.0) {
                    btnPublish.setEnabled(true);
                    if (tvImageStatus != null) tvImageStatus.setText("");
                    toast("Location not detected. Open Feed once and try again.");
                    return;
                }

                String id = annRef.push().getKey();
                if (id == null) {
                    btnPublish.setEnabled(true);
                    if (tvImageStatus != null) tvImageStatus.setText("");
                    return;
                }

                long now      = System.currentTimeMillis();
                long expireAt = now + (fHours * 60L * 60L * 1000L);

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

                // ── Step 1: Image upload (optional) ──────────────────────
                if (selectedBitmap != null) {
                    if (tvImageStatus != null) tvImageStatus.setText("Uploading image...");
                    CloudinaryUploader.uploadBitmap(
                            AddAnnouncementActivity.this,
                            selectedBitmap,
                            new CloudinaryUploader.UploadListener() {
                                @Override public void onSuccess(String imageUrl) {
                                    a.setImageUrl(imageUrl);
                                    uploadAudioThenSave(a, u, id);
                                }
                                @Override public void onError(String message) {
                                    a.setImageUrl("");
                                    toast("Image upload failed, posting without image");
                                    uploadAudioThenSave(a, u, id);
                                }
                            }
                    );
                } else {
                    a.setImageUrl("");
                    uploadAudioThenSave(a, u, id);
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {
                btnPublish.setEnabled(true);
                if (tvImageStatus != null) tvImageStatus.setText("");
                toast("Failed: " + error.getMessage());
            }
        });
    }

    /**
     * Step 2: Audio upload to Cloudinary (optional) → তারপর Firebase এ save করো
     */
    private void uploadAudioThenSave(Announcement a, UserModel u, String annId) {
        if (recordedAudioPath != null && !recordedAudioPath.isEmpty()) {
            if (tvImageStatus != null) tvImageStatus.setText("Uploading audio...");

            AudioRecorderHelper.uploadAudio(
                    recordedAudioPath,
                    annId,
                    new AudioRecorderHelper.UploadListener() {
                        @Override public void onProgress(int percent) {
                            runOnUiThread(() -> {
                                if (tvImageStatus != null)
                                    tvImageStatus.setText("Uploading audio... " + percent + "%");
                            });
                        }
                        @Override public void onSuccess(String downloadUrl) {
                            a.setAudioUrl(downloadUrl);
                            runOnUiThread(() -> saveAnnouncement(a, u));
                        }
                        @Override public void onError(String message) {
                            a.setAudioUrl("");
                            runOnUiThread(() -> {
                                toast("Audio upload failed, posting without audio");
                                saveAnnouncement(a, u);
                            });
                        }
                    }
            );
        } else {
            a.setAudioUrl("");
            saveAnnouncement(a, u);
        }
    }

    /**
     * Step 3: Firebase Realtime DB তে announcement save করো
     * ✅ FIXED: NotificationSender এখন call হচ্ছে
     */
    private void saveAnnouncement(Announcement a, UserModel u) {
        if (tvImageStatus != null) tvImageStatus.setText("Publishing...");
        annRef.child(a.getId()).setValue(a)
                .addOnSuccessListener(v -> {
                    // ✅ FIXED: notification পাঠাও (আগে এটা missing ছিল)
                    NotificationSender.sendAnnouncementNotification(
                            a.getId(),
                            a.getTitle(),
                            a.getDescription(),
                            a.getLat(),
                            a.getLng(),
                            a.getUserId()
                    );
                    toast("Published ✅");
                    finish();
                })
                .addOnFailureListener(e -> {
                    btnPublish.setEnabled(true);
                    if (tvImageStatus != null) tvImageStatus.setText("");
                    toast("Save failed: " + e.getMessage());
                });
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (audioRecorder != null && isRecording) {
            audioRecorder.cancelRecording();
        }
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}