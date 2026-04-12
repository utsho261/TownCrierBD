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
import com.example.towncrierbd.utils.CategoryConfig;
import com.example.towncrierbd.utils.Constants;
import com.example.towncrierbd.utils.ImageBase64Util;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class AddAnnouncementActivity extends AppCompatActivity {

    private Spinner spCategory, spSubcategory;
    private EditText etCustomSub, etTitle, etDesc, etHours;
    private Button btnCancel, btnPublish;
    private ImageView btnClose;

    private View btnAddImage;
    private TextView tvImageStatus;

    private FirebaseAuth auth;
    private DatabaseReference annRef, userRef;

    private String imageBase64 = ""; // ✅ Base64 image (optional)

    private ActivityResultLauncher<Intent> galleryLauncher;
    private ActivityResultLauncher<String[]> permissionLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_announcement);

        spCategory = findViewById(R.id.spCategory);
        spSubcategory = findViewById(R.id.spSubcategory);

        etCustomSub = findViewById(R.id.etCustomSub);
        etTitle = findViewById(R.id.etTitle);
        etDesc = findViewById(R.id.etDesc);
        etHours = findViewById(R.id.etHours);

        btnCancel = findViewById(R.id.btnCancel);
        btnPublish = findViewById(R.id.btnPublish);
        btnClose = findViewById(R.id.btnClose);

        btnAddImage = findViewById(R.id.btnAddImage);
        tvImageStatus = findViewById(R.id.tvImageStatus);

        auth = FirebaseAuth.getInstance();
        annRef = FirebaseDatabase.getInstance().getReference(Constants.DB_ANNOUNCEMENTS);
        userRef = FirebaseDatabase.getInstance().getReference(Constants.DB_USERS);

        setupPermissions();
        setupLaunchers();
        setupCategoryUI();

        btnCancel.setOnClickListener(v -> finish());
        if (btnClose != null) btnClose.setOnClickListener(v -> finish());
        btnPublish.setOnClickListener(v -> publish());

        if (btnAddImage != null) btnAddImage.setOnClickListener(v -> showImageChooser());
    }

    private void setupPermissions() {
        permissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {}
        );
    }

    private void requestImagePermissionsIfNeeded() {
        List<String> need = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                    != PackageManager.PERMISSION_GRANTED) {
                need.add(Manifest.permission.READ_MEDIA_IMAGES);
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                need.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            }
        }
        if (!need.isEmpty()) permissionLauncher.launch(need.toArray(new String[0]));
    }

    private void setupLaunchers() {
        galleryLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                res -> {
                    if (res.getResultCode() == RESULT_OK && res.getData() != null) {
                        Uri uri = res.getData().getData();
                        if (uri == null) return;

                        try {
                            Bitmap bmp = MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
                            imageBase64 = ImageBase64Util.bitmapToBase64(bmp);
                            if (tvImageStatus != null) tvImageStatus.setText("Image selected ✅");
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

    // ---------------- Category UI ----------------

    private void setupCategoryUI() {
        List<String> cats = new ArrayList<>(CategoryConfig.MAIN);
        ArrayAdapter<String> catAdapter = new ArrayAdapter<>(this, R.layout.spinner_selected_white, cats);
        catAdapter.setDropDownViewResource(R.layout.spinner_dropdown_dark);
        spCategory.setAdapter(catAdapter);

        spCategory.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updateSubcategoryUI(getSelected(spCategory));
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        spSubcategory.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String cat = getSelected(spCategory);
                String sub = getSelected(spSubcategory);

                if ("Others".equals(cat)) {
                    showCustom(true, "Optional: Write details if needed");
                } else if ("Other".equalsIgnoreCase(sub)) {
                    showCustom(true, "Specify other (optional)");
                } else {
                    showCustom(false, "");
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void updateSubcategoryUI(String cat) {
        List<String> subs = new ArrayList<>();
        subs.add("Select subcategory");
        if (!"Others".equals(cat)) subs.addAll(CategoryConfig.getSubcategories(cat));

        ArrayAdapter<String> subAdapter = new ArrayAdapter<>(this, R.layout.spinner_dropdown_dark, subs);
        subAdapter.setDropDownViewResource(R.layout.spinner_dropdown_dark);
        spSubcategory.setAdapter(subAdapter);

        showCustom("Others".equals(cat), "Optional: Write details if needed");
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

    // ---------------- Publish ----------------

    private void publish() {
        String title = etTitle.getText().toString().trim();
        String desc  = etDesc.getText().toString().trim();

        String cat = getSelected(spCategory);
        String subSel = getSelected(spSubcategory);
        if ("Select subcategory".equalsIgnoreCase(subSel)) subSel = "";
        String custom = etCustomSub.getText().toString().trim();

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

        final String fCat = cat, fSub = subSel, fCustom = custom, fTitle = title, fDesc = desc;
        final long fHours = hoursValue;

        userRef.child(uid).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                UserModel u = snapshot.getValue(UserModel.class);
                if (u == null) { btnPublish.setEnabled(true); toast("User not found"); return; }

                if (u.getLat() == 0.0 && u.getLng() == 0.0) {
                    btnPublish.setEnabled(true);
                    toast("Location not detected yet. Open Feed once and try again.");
                    return;
                }

                String id = annRef.push().getKey();
                if (id == null) { btnPublish.setEnabled(true); return; }

                long now = System.currentTimeMillis();
                long expireAt = now + (fHours * 60L * 60L * 1000L);

                Announcement a = new Announcement();
                a.setId(id);
                a.setUserId(uid);
                a.setUserName(u.getName());

                // ✅ IMPORTANT
                String role = (u.getRole() == null || u.getRole().trim().isEmpty())
                        ? Constants.ROLE_USER
                        : u.getRole().trim();
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

                a.setImageBase64(imageBase64 == null ? "" : imageBase64);

                annRef.child(id).setValue(a)
                        .addOnSuccessListener(v -> { toast("Published"); finish(); })
                        .addOnFailureListener(e -> { btnPublish.setEnabled(true); toast("Save failed"); });
            }

            @Override public void onCancelled(@NonNull DatabaseError error) {
                btnPublish.setEnabled(true);
                toast("Failed");
            }
        });
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}
