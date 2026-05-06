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
import com.example.towncrierbd.utils.DistanceUtil;
import com.example.towncrierbd.utils.FcmSender;
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
    private String recordedAudioPath = null;

    private AudioRecorderHelper audioHelper;

    private ActivityResultLauncher<Intent> galleryLauncher;
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

        auth    = FirebaseAuth.getInstance();
        annRef  = FirebaseDatabase.getInstance().getReference(Constants.DB_ANNOUNCEMENTS);
        userRef = FirebaseDatabase.getInstance().getReference(Constants.DB_USERS);

        audioHelper = new AudioRecorderHelper(this);

        setupPermissions();
        setupLaunchers();
        setupCategoryUI();

        btnCancel.setOnClickListener(v -> finish());
        if (btnClose != null) btnClose.setOnClickListener(v -> finish());
        btnPublish.setOnClickListener(v -> publish());
        if (btnAddImage != null) btnAddImage.setOnClickListener(v -> showImageChooser());
        if (btnRecordAudio != null) btnRecordAudio.setOnClickListener(v -> toggleRecording());
    }

    // ─── Audio ──────────────────────────────────────────────────────────────

    private void toggleRecording() {
        if (!hasMicPermission()) {
            permissionLauncher.launch(new String[]{Manifest.permission.RECORD_AUDIO});
            return;
        }

        if (audioHelper.isRecording()) {
            audioHelper.stopRecording(new AudioRecorderHelper.RecordListener() {
                @Override public void onRecordStarted() {}
                @Override public void onRecordStopped(String localPath) {
                    recordedAudioPath = localPath;
                    if (tvAudioStatus != null)
                        tvAudioStatus.setText("Audio recorded ✅");
                    updateRecordButtonUI(false);
                }
                @Override public void onError(String message) {
                    toast("Record stop error: " + message);
                }
            });
        } else {
            audioHelper.startRecording(new AudioRecorderHelper.RecordListener() {
                @Override public void onRecordStarted() {
                    if (tvAudioStatus != null)
                        tvAudioStatus.setText("🎙️ Recording... tap again to stop");
                    updateRecordButtonUI(true);
                }
                @Override public void onRecordStopped(String localPath) {}
                @Override public void onError(String message) {
                    toast("Record failed: " + message);
                }
            });
        }
    }

    private void updateRecordButtonUI(boolean recording) {
        if (btnRecordAudio == null) return;
        TextView tvLabel = btnRecordAudio.findViewWithTag("audioLabel");
        if (tvLabel == null) {
            // fallback: find TextView inside LinearLayout
            if (btnRecordAudio instanceof android.view.ViewGroup) {
                android.view.ViewGroup vg = (android.view.ViewGroup) btnRecordAudio;
                for (int i = 0; i < vg.getChildCount(); i++) {
                    View child = vg.getChildAt(i);
                    if (child instanceof TextView) {
                        ((TextView) child).setText(recording ? "Stop Recording" : "Record Audio");
                        break;
                    }
                }
            }
        }
    }

    private boolean hasMicPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
    }

    // ─── Permissions ────────────────────────────────────────────────────────

    private void setupPermissions() {
        permissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(), result -> {});
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

    // ─── Gallery ────────────────────────────────────────────────────────────

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

    // ─── Category UI ────────────────────────────────────────────────────────

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

    // ─── Publish ────────────────────────────────────────────────────────────

    private void publish() {
        // Stop recording if still going
        if (audioHelper.isRecording()) {
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

        if (title.isEmpty() || desc.isEmpty()) {
            toast("Title & Description required");
            return;
        }

        String uid = auth.getUid();
        if (uid == null) return;

        btnPublish.setEnabled(false);
        if (tvImageStatus != null) tvImageStatus.setText("Please wait...");

        final String fCat = cat, fSub = subSel, fCustom = custom,
                fTitle = title, fDesc = desc;
        final long fHours = hoursValue;

        userRef.child(uid).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                UserModel u = snapshot.getValue(UserModel.class);
                if (u == null) {
                    btnPublish.setEnabled(true);
                    toast("User not found");
                    return;
                }
                if (u.getLat() == 0.0 && u.getLng() == 0.0) {
                    btnPublish.setEnabled(true);
                    toast("Location not detected. Open Feed once and try again.");
                    return;
                }

                String id = annRef.push().getKey();
                if (id == null) { btnPublish.setEnabled(true); return; }

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

                // Step 1: Upload image if any
                if (selectedBitmap != null) {
                    if (tvImageStatus != null) tvImageStatus.setText("Uploading image...");
                    CloudinaryUploader.uploadBitmap(
                            AddAnnouncementActivity.this,
                            selectedBitmap,
                            new CloudinaryUploader.UploadListener() {
                                @Override public void onSuccess(String imageUrl) {
                                    a.setImageUrl(imageUrl);
                                    uploadAudioThenSave(a, id, u);
                                }
                                @Override public void onError(String message) {
                                    a.setImageUrl("");
                                    uploadAudioThenSave(a, id, u);
                                }
                            }
                    );
                } else {
                    a.setImageUrl("");
                    uploadAudioThenSave(a, id, u);
                }
            }

            @Override public void onCancelled(@NonNull DatabaseError error) {
                btnPublish.setEnabled(true);
                toast("Failed");
            }
        });
    }

    private void uploadAudioThenSave(Announcement a, String id, UserModel u) {
        if (recordedAudioPath != null && !recordedAudioPath.isEmpty()) {
            if (tvAudioStatus != null) tvAudioStatus.setText("Uploading audio...");
            AudioRecorderHelper.uploadAudio(recordedAudioPath, id,
                    new AudioRecorderHelper.UploadListener() {
                        @Override public void onProgress(int percent) {
                            if (tvAudioStatus != null)
                                tvAudioStatus.setText("Uploading audio... " + percent + "%");
                        }
                        @Override public void onSuccess(String downloadUrl) {
                            a.setAudioUrl(downloadUrl);
                            saveAndNotify(a, id, u);
                        }
                        @Override public void onError(String message) {
                            a.setAudioUrl("");
                            saveAndNotify(a, id, u);
                            toast("Audio upload failed, posting without audio");
                        }
                    });
        } else {
            a.setAudioUrl("");
            saveAndNotify(a, id, u);
        }
    }

    private void saveAndNotify(Announcement a, String id, UserModel poster) {
        annRef.child(id).setValue(a)
                .addOnSuccessListener(v -> {
                    toast("Published ✅");
                    sendNearbyNotifications(a, poster);
                    finish();
                })
                .addOnFailureListener(e -> {
                    btnPublish.setEnabled(true);
                    if (tvImageStatus != null) tvImageStatus.setText("");
                    toast("Save failed");
                });
    }

    // ─── FCM Notification ───────────────────────────────────────────────────

    private void sendNearbyNotifications(Announcement announcement, UserModel poster) {
        // Fetch all users, find those within radius, collect their FCM tokens
        DatabaseReference usersRef = FirebaseDatabase.getInstance()
                .getReference(Constants.DB_USERS);

        usersRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<String> tokens = new ArrayList<>();
                String myUid = auth.getUid();

                for (DataSnapshot s : snapshot.getChildren()) {
                    String uid = s.getKey();
                    if (uid == null || uid.equals(myUid)) continue;

                    // Get location
                    Double lat = s.child("lat").getValue(Double.class);
                    Double lng = s.child("lng").getValue(Double.class);
                    if (lat == null || lng == null) continue;

                    double dist = DistanceUtil.distanceKm(
                            poster.getLat(), poster.getLng(), lat, lng);
                    if (dist > Constants.FEED_RADIUS_KM) continue;

                    // Get FCM token
                    String token = s.child("fcmToken").getValue(String.class);
                    if (token != null && !token.isEmpty()) tokens.add(token);
                }

                if (tokens.isEmpty()) return;

                String title = "New announcement nearby!";
                String body  = safe(announcement.getTitle()) + " — "
                        + safe(announcement.getCategory());

                FcmSender.sendToTokens(tokens, title, body, count ->
                        android.util.Log.d("FCM", "Sent to " + count + " devices"));
            }

            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Stop recording if activity closes mid-record
        if (audioHelper != null && audioHelper.isRecording()) {
            audioHelper.cancelRecording();
        }
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    private String safe(String s) { return s == null ? "" : s.trim(); }
}