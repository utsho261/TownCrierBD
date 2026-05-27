package com.example.towncrierbd.activities;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.example.towncrierbd.R;
import com.example.towncrierbd.models.Announcement;
import com.example.towncrierbd.models.UserModel;
import com.example.towncrierbd.utils.*;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;

import java.io.*;
import java.util.*;

public class AddAnnouncementActivity extends BaseActivity {

    private static final int UNIT_MINUTES = 0, UNIT_HOURS = 1, UNIT_DAYS = 2;
    private static final long MIN_MINUTES = 1, MAX_MINUTES = 7 * 24 * 60;

    private View   stepCategory, stepProducts, stepDetails;
    private TextView tvStepIndicator, tvPostTypeHeader;

    private LinearLayout llCategoryList;
    private final Set<String> selectedCategories = new LinkedHashSet<>();

    private LinearLayout  llProductList;
    private EditText      etCustomCategoryText;
    private final Set<String> selectedProducts      = new LinkedHashSet<>();
    private final Set<String> selectedSubcategories = new LinkedHashSet<>();
    private final List<CheckBox> productCheckBoxes  = new ArrayList<>();

    private Spinner  spExpiryUnit;
    private EditText etTitle, etDesc, etExpiryValue;
    private TextView tvExpiryPreview;
    private Button   btnPublish;
    private ImageView ivPreview;
    private TextView tvImageStatus, tvAudioStatus;
    private View     btnAddImage, btnRecordAudio;

    private String postType = "announcement";

    private FirebaseAuth      auth;
    private DatabaseReference annRef, userRef;

    private List<String> userHawkerCategories    = new ArrayList<>();
    private List<String> userHawkerSubcategories = new ArrayList<>();
    private String       userHawkerOthersName    = "";

    private Bitmap            selectedBitmap;
    private AudioRecorderHelper audioRecorder;
    private String            recordedAudioPath;
    private boolean           isRecording;

    private ActivityResultLauncher<Intent>   galleryLauncher, audioFileLauncher;
    private ActivityResultLauncher<String[]> permissionLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_announcement_wizard);

        auth    = FirebaseAuth.getInstance();
        annRef  = FirebaseDatabase.getInstance().getReference(Constants.DB_ANNOUNCEMENTS);
        userRef = FirebaseDatabase.getInstance().getReference(Constants.DB_USERS);
        audioRecorder = new AudioRecorderHelper(this);

        tvStepIndicator  = findViewById(R.id.tvStepIndicator);
        tvPostTypeHeader = findViewById(R.id.tvPostTypeHeader);
        stepCategory     = findViewById(R.id.stepCategory);
        stepProducts     = findViewById(R.id.stepProducts);
        stepDetails      = findViewById(R.id.stepDetails);
        llCategoryList   = findViewById(R.id.llCategoryList);
        llProductList    = findViewById(R.id.llProductList);
        etCustomCategoryText = findViewById(R.id.etCustomCategory);

        spExpiryUnit    = findViewById(R.id.spExpiryUnit);
        etTitle         = findViewById(R.id.etTitle);
        etDesc          = findViewById(R.id.etDesc);
        etExpiryValue   = findViewById(R.id.etExpiryValue);
        tvExpiryPreview = findViewById(R.id.tvExpiryPreview);
        btnPublish      = findViewById(R.id.btnPublish);
        ivPreview       = findViewById(R.id.ivPreview);
        tvImageStatus   = findViewById(R.id.tvImageStatus);
        tvAudioStatus   = findViewById(R.id.tvAudioStatus);
        btnAddImage     = findViewById(R.id.btnAddImage);
        btnRecordAudio  = findViewById(R.id.btnRecordAudio);

        View btnClose = findViewById(R.id.btnClose);
        if (btnClose != null) btnClose.setOnClickListener(v -> finish());
        if (btnAddImage != null)    btnAddImage.setOnClickListener(v -> showImageChooser());
        if (btnRecordAudio != null) btnRecordAudio.setOnClickListener(v -> showAudioOptions());
        if (btnPublish != null)     btnPublish.setOnClickListener(v -> publish());

        Button btnNext1 = findViewById(R.id.btnNextStep1);
        if (btnNext1 != null) btnNext1.setOnClickListener(v -> goToStep2());

        Button btnBack2 = findViewById(R.id.btnBackStep2);
        if (btnBack2 != null) btnBack2.setOnClickListener(v -> showStep(1));

        Button btnNext2 = findViewById(R.id.btnNextStep2);
        if (btnNext2 != null) btnNext2.setOnClickListener(v -> showStep(3));

        Button btnBack3 = findViewById(R.id.btnBackStep3);
        if (btnBack3 != null) btnBack3.setOnClickListener(v -> showStep(2));

        setupPermissions();
        setupLaunchers();
        setupExpiryUI();

        loadUserProfileThenInit();
        showStep(1);
    }

    // ════════════════════════════════════════════════════════════════════════
    // Expiry UI
    // ════════════════════════════════════════════════════════════════════════

    private void setupExpiryUI() {
        if (spExpiryUnit == null) return;

        // ✅ UPDATED: Use getString() for localized spinner labels
        String[] units = new String[]{
                getString(R.string.minutes),
                getString(R.string.hours),
                getString(R.string.days)
        };

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, units) {

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View v = super.getView(position, convertView, parent);
                TextView tv = v.findViewById(android.R.id.text1);
                tv.setTextColor(0xFF111111);
                tv.setTextSize(16f);
                tv.setTypeface(null, Typeface.BOLD);
                tv.setPadding(dp(14), 0, dp(14), 0);
                tv.setGravity(Gravity.CENTER_VERTICAL);
                return v;
            }

            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                View v = super.getDropDownView(position, convertView, parent);
                TextView tv = v.findViewById(android.R.id.text1);
                tv.setTextColor(0xFF111111);
                tv.setTextSize(15f);
                tv.setPadding(dp(16), dp(14), dp(16), dp(14));
                if (position == spExpiryUnit.getSelectedItemPosition()) {
                    v.setBackgroundColor(0xFFE3F2FD);
                } else {
                    v.setBackgroundColor(0xFFFFFFFF);
                }
                return v;
            }
        };

        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spExpiryUnit.setAdapter(adapter);
        spExpiryUnit.setSelection(UNIT_HOURS);

        if (tvExpiryPreview != null) {
            tvExpiryPreview.setVisibility(View.GONE);
            tvExpiryPreview.setText("");
        }

        if (etExpiryValue != null) {
            etExpiryValue.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int st, int c, int af) {}
                @Override public void afterTextChanged(Editable s) {}
                @Override public void onTextChanged(CharSequence s, int st, int b, int c) {
                    updateExpiryPreview();
                }
            });
        }

        spExpiryUnit.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                updateExpiryPreview();
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });
    }

    private long getExpiryMillis() {
        if (etExpiryValue == null) return -1;
        String raw = etExpiryValue.getText().toString().trim();
        if (raw.isEmpty()) return -1;
        double value;
        try { value = Double.parseDouble(raw); } catch (NumberFormatException e) { return -1; }
        if (value <= 0) return -1;
        int unit = (spExpiryUnit != null) ? spExpiryUnit.getSelectedItemPosition() : UNIT_HOURS;
        long minutes;
        switch (unit) {
            case UNIT_MINUTES: minutes = (long) value;            break;
            case UNIT_DAYS:    minutes = (long)(value * 24 * 60); break;
            default:           minutes = (long)(value * 60);      break;
        }
        if (minutes < MIN_MINUTES) return -1;
        if (minutes > MAX_MINUTES) return -2;
        return minutes * 60_000L;
    }

    private void updateExpiryPreview() {
        if (tvExpiryPreview == null) return;
        long millis = getExpiryMillis();

        if (millis == -1) {
            tvExpiryPreview.setVisibility(View.GONE);
            tvExpiryPreview.setText("");
            return;
        }

        tvExpiryPreview.setVisibility(View.VISIBLE);

        if (millis == -2) {
            // ✅ UPDATED: Use getString()
            tvExpiryPreview.setText(getString(R.string.max_expiry_warning));
            tvExpiryPreview.setTextColor(0xFFEF4444);
            tvExpiryPreview.setBackgroundColor(0xFFFEE2E2);
            return;
        }

        tvExpiryPreview.setTextColor(0xFF1565C0);
        tvExpiryPreview.setBackgroundColor(0xFFE3F2FD);
        // ✅ UPDATED: Use getString()
        tvExpiryPreview.setText(getString(R.string.expiry_preview, humanReadable(millis)));
    }

    private String humanReadable(long millis) {
        long tot = millis / 60_000L;
        long d = tot / (24 * 60), h = (tot % (24 * 60)) / 60, m = tot % 60;
        StringBuilder sb = new StringBuilder();
        if (d > 0) sb.append(d).append(d == 1 ? " day" : " days");
        if (h > 0) { if (sb.length() > 0) sb.append(" "); sb.append(h).append(h == 1 ? " hour" : " hours"); }
        if (m > 0) { if (sb.length() > 0) sb.append(" "); sb.append(m).append(m == 1 ? " minute" : " minutes"); }
        return sb.length() > 0 ? sb.toString() : "less than a minute";
    }

    // ════════════════════════════════════════════════════════════════════════
    // Load user profile
    // ════════════════════════════════════════════════════════════════════════

    private void loadUserProfileThenInit() {
        String uid = auth.getUid();
        if (uid == null) return;
        userRef.child(uid).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot s) {
                UserModel u = s.getValue(UserModel.class);
                if (u == null) return;

                String role = u.getRole();
                postType = Constants.ROLE_ANNOUNCER.equals(role) ? "announcement" : "request";
                updatePostTypeHeader();

                userHawkerCategories    = u.getHawkerCategories();
                userHawkerSubcategories = u.getHawkerSubcategories();
                userHawkerOthersName    = u.getHawkerOthersName();

                buildCategoryStep();
            }
            @Override public void onCancelled(@NonNull DatabaseError e) {}
        });
    }

    private void updatePostTypeHeader() {
        if (tvPostTypeHeader == null) return;
        if ("request".equals(postType)) {
            // ✅ UPDATED: Use getString()
            tvPostTypeHeader.setText(getString(R.string.create_request));
        } else {
            tvPostTypeHeader.setText(getString(R.string.create_announcement));
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // Step navigation
    // ════════════════════════════════════════════════════════════════════════

    private void showStep(int step) {
        if (stepCategory != null) stepCategory.setVisibility(step == 1 ? View.VISIBLE : View.GONE);
        if (stepProducts  != null) stepProducts.setVisibility(step == 2 ? View.VISIBLE : View.GONE);
        if (stepDetails   != null) stepDetails.setVisibility(step == 3 ? View.VISIBLE : View.GONE);
        // ✅ UPDATED: Use getString()
        if (tvStepIndicator != null) tvStepIndicator.setText(getString(R.string.step_indicator, step));
    }

    private void goToStep2() {
        if (selectedCategories.isEmpty()) {
            // ✅ UPDATED: Use getString()
            toast(getString(R.string.please_select_category));
            return;
        }
        buildProductStep();
        showStep(2);
    }

    // ════════════════════════════════════════════════════════════════════════
    // STEP 1 — Category selection
    // ════════════════════════════════════════════════════════════════════════

    private void buildCategoryStep() {
        if (llCategoryList == null) return;
        llCategoryList.removeAllViews();

        List<CategoryConfig.HawkerCategory> categoriesToShow = new ArrayList<>();

        if (Constants.ROLE_ANNOUNCER.equals(postType.equals("announcement") ? Constants.ROLE_ANNOUNCER : "")
                && !userHawkerCategories.isEmpty()) {
            for (CategoryConfig.HawkerCategory cat : CategoryConfig.HAWKER_CATEGORIES) {
                if (userHawkerCategories.contains(cat.name)) {
                    categoriesToShow.add(cat);
                }
            }
        } else {
            categoriesToShow = CategoryConfig.HAWKER_CATEGORIES;
        }

        if (categoriesToShow.isEmpty()) {
            categoriesToShow = CategoryConfig.HAWKER_CATEGORIES;
        }

        for (CategoryConfig.HawkerCategory cat : categoriesToShow) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(14), dp(14), dp(14), dp(14));
            row.setBackground(roundedBg(0xFFFFFFFF, dp(12)));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, dp(6), 0, 0);
            row.setLayoutParams(lp);

            CheckBox cb = new CheckBox(this);
            cb.setButtonTintList(ColorStateList.valueOf(0xFF1976F3));

            TextView tvLabel = new TextView(this);
            tvLabel.setText(cat.emoji + "  " + cat.name);
            tvLabel.setTextSize(15);
            tvLabel.setTypeface(null, Typeface.BOLD);
            tvLabel.setTextColor(0xFF1F2937);
            tvLabel.setPadding(dp(12), 0, 0, 0);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            tvLabel.setLayoutParams(tlp);

            int selectedCount = 0;
            for (String sub : userHawkerSubcategories) {
                for (CategoryConfig.SubGroup g : cat.subGroups) {
                    if (g.items.contains(sub)) { selectedCount++; break; }
                }
            }

            TextView tvHint = new TextView(this);
            if (selectedCount > 0) {
                tvHint.setText(selectedCount + " items");
                tvHint.setTextSize(11);
                tvHint.setTextColor(0xFF9CA3AF);
            } else if (!cat.subGroups.isEmpty()) {
                int total = 0;
                for (CategoryConfig.SubGroup g : cat.subGroups) total += g.items.size();
                tvHint.setText(total + " items");
                tvHint.setTextSize(11);
                tvHint.setTextColor(0xFF9CA3AF);
            }

            row.addView(cb);
            row.addView(tvLabel);
            row.addView(tvHint);
            llCategoryList.addView(row);

            row.setOnClickListener(v -> cb.toggle());
            cb.setOnCheckedChangeListener((btn, checked) -> {
                if (checked) {
                    selectedCategories.add(cat.name);
                    row.setBackground(roundedBg(0xFFEFF6FF, dp(12)));
                } else {
                    selectedCategories.remove(cat.name);
                    row.setBackground(roundedBg(0xFFFFFFFF, dp(12)));
                    for (CategoryConfig.SubGroup g : cat.subGroups)
                        selectedProducts.removeAll(g.items);
                }
            });
        }

        if ("announcement".equals(postType) && !userHawkerCategories.isEmpty()) {
            TextView tvHint = new TextView(this);
            // ✅ UPDATED: Use getString()
            tvHint.setText(getString(R.string.edit_categories_hint));
            tvHint.setTextSize(12);
            tvHint.setTextColor(0xFF6B7280);
            tvHint.setPadding(dp(4), dp(12), dp(4), 0);
            llCategoryList.addView(tvHint);
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // STEP 2 — Product selection
    // ════════════════════════════════════════════════════════════════════════

    private void buildProductStep() {
        if (llProductList == null) return;
        llProductList.removeAllViews();
        productCheckBoxes.clear();

        boolean isAnnouncer = "announcement".equals(postType);
        boolean hasOthers = selectedCategories.contains("Others");

        if (etCustomCategoryText != null) {
            etCustomCategoryText.setVisibility(hasOthers ? View.VISIBLE : View.GONE);
            if (hasOthers && !userHawkerOthersName.isEmpty()) {
                etCustomCategoryText.setHint("e.g. " + userHawkerOthersName);
            }
        }

        boolean anyAdded = false;

        for (CategoryConfig.HawkerCategory cat : CategoryConfig.HAWKER_CATEGORIES) {
            if (!selectedCategories.contains(cat.name)) continue;
            if ("Others".equals(cat.name)) continue;

            TextView tvCatHeader = new TextView(this);
            tvCatHeader.setText(cat.emoji + "  " + cat.name);
            tvCatHeader.setTextSize(15);
            tvCatHeader.setTypeface(null, Typeface.BOLD);
            tvCatHeader.setTextColor(0xFF1976F3);
            LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            hlp.setMargins(0, dp(14), 0, dp(4));
            tvCatHeader.setLayoutParams(hlp);
            llProductList.addView(tvCatHeader);
            anyAdded = true;

            boolean hasPreSelected = isAnnouncer && !userHawkerSubcategories.isEmpty();
            if (!hasPreSelected) {
                TextView tvSelectAll = new TextView(this);
                // ✅ UPDATED: Use getString()
                tvSelectAll.setText(getString(R.string.select_all_from, cat.name));
                tvSelectAll.setTextSize(12);
                tvSelectAll.setTextColor(0xFF1976F3);
                tvSelectAll.setPadding(0, 0, 0, dp(4));
                final CategoryConfig.HawkerCategory finalCat = cat;
                tvSelectAll.setOnClickListener(v -> selectAllForCategory(finalCat));
                llProductList.addView(tvSelectAll);
            }

            for (CategoryConfig.SubGroup group : cat.subGroups) {
                List<String> itemsToShow;
                if (isAnnouncer && !userHawkerSubcategories.isEmpty()) {
                    itemsToShow = new ArrayList<>();
                    for (String item : group.items) {
                        if (userHawkerSubcategories.contains(item)) {
                            itemsToShow.add(item);
                        }
                    }
                    if (itemsToShow.isEmpty()) continue;
                } else {
                    itemsToShow = group.items;
                }

                TextView tvSub = new TextView(this);
                tvSub.setText("▸ " + group.groupName);
                tvSub.setTextSize(12);
                tvSub.setTypeface(null, Typeface.BOLD);
                tvSub.setTextColor(0xFF6B7280);
                tvSub.setPadding(dp(4), dp(8), 0, dp(4));
                llProductList.addView(tvSub);

                for (int i = 0; i < itemsToShow.size(); i += 2) {
                    LinearLayout rowLayout = new LinearLayout(this);
                    rowLayout.setOrientation(LinearLayout.HORIZONTAL);
                    LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    rlp.setMargins(0, dp(2), 0, dp(2));
                    rowLayout.setLayoutParams(rlp);

                    addProductCheckBox(rowLayout, itemsToShow.get(i), group.groupName);
                    if (i + 1 < itemsToShow.size())
                        addProductCheckBox(rowLayout, itemsToShow.get(i + 1), group.groupName);
                    llProductList.addView(rowLayout);
                }
            }
        }

        if (hasOthers && !anyAdded) {
            TextView tvInfo = new TextView(this);
            tvInfo.setText("Describe what you " + ("request".equals(postType) ? "need" : "sell") + " in the field above.");
            tvInfo.setTextSize(13);
            tvInfo.setTextColor(0xFF6B7280);
            tvInfo.setPadding(0, dp(8), 0, 0);
            llProductList.addView(tvInfo);
        }

        if (llProductList.getChildCount() == 0) {
            TextView tvInfo = new TextView(this);
            tvInfo.setText("No specific products for selected categories. Proceed to next step.");
            tvInfo.setTextSize(13);
            tvInfo.setTextColor(0xFF6B7280);
            tvInfo.setPadding(dp(4), dp(8), dp(4), 0);
            llProductList.addView(tvInfo);
        }
    }

    private void addProductCheckBox(LinearLayout parent, String product, String subcategory) {
        CheckBox cb = new CheckBox(this);
        cb.setText(product);
        cb.setTextSize(13);
        cb.setTextColor(0xFF374151);
        cb.setButtonTintList(ColorStateList.valueOf(0xFF1976F3));
        cb.setChecked(selectedProducts.contains(product));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        clp.setMargins(dp(2), dp(1), dp(2), dp(1));
        cb.setLayoutParams(clp);

        cb.setOnCheckedChangeListener((btn, checked) -> {
            if (checked) {
                selectedProducts.add(product);
                selectedSubcategories.add(subcategory);
            } else {
                selectedProducts.remove(product);
            }
        });

        productCheckBoxes.add(cb);
        parent.addView(cb);
    }

    private void selectAllForCategory(CategoryConfig.HawkerCategory cat) {
        for (CategoryConfig.SubGroup g : cat.subGroups) {
            for (String item : g.items) {
                selectedProducts.add(item);
                selectedSubcategories.add(g.groupName);
            }
        }
        for (CheckBox cb : productCheckBoxes) {
            String label = cb.getText().toString();
            if (selectedProducts.contains(label)) cb.setChecked(true);
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // Publish
    // ════════════════════════════════════════════════════════════════════════

    private void publish() {
        // ✅ UPDATED: Use getString()
        if (isRecording) { toast(getString(R.string.stop_recording_first)); return; }

        String title = etTitle.getText().toString().trim();
        String desc  = etDesc.getText().toString().trim();

        // ✅ UPDATED: Use getString()
        if (title.isEmpty()) { toast(getString(R.string.title_required_msg)); etTitle.requestFocus(); return; }
        if (desc.isEmpty())  { toast(getString(R.string.description_required_msg)); etDesc.requestFocus(); return; }

        long expiryMillis = getExpiryMillis();
        if (expiryMillis == -1) {
            // ✅ UPDATED: Use getString()
            toast(getString(R.string.enter_expiry));
            if (etExpiryValue != null) etExpiryValue.requestFocus();
            return;
        }
        if (expiryMillis == -2) {
            // ✅ UPDATED: Use getString()
            toast(getString(R.string.max_expiry_7_days));
            if (etExpiryValue != null) etExpiryValue.requestFocus();
            return;
        }

        String uid = auth.getUid();
        if (uid == null) return;

        btnPublish.setEnabled(false);
        // ✅ UPDATED: Use getString()
        if (tvImageStatus != null) tvImageStatus.setText(getString(R.string.please_wait));

        userRef.child(uid).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                UserModel u = snapshot.getValue(UserModel.class);
                if (u == null) { reset(); toast("User not found"); return; }
                if (u.getLat() == 0.0 && u.getLng() == 0.0) {
                    // ✅ UPDATED: Use getString()
                    reset(); toast(getString(R.string.location_not_detected)); return;
                }

                String id = annRef.push().getKey();
                if (id == null) { reset(); return; }

                long now = System.currentTimeMillis();

                Announcement a = new Announcement();
                a.setId(id);
                a.setUserId(uid);
                a.setUserName(u.getName());
                String role = (u.getRole() == null || u.getRole().trim().isEmpty())
                        ? Constants.ROLE_USER : u.getRole().trim();
                a.setUserRole(role);
                a.setPostType(postType);

                String primaryCat = selectedCategories.isEmpty()
                        ? "" : selectedCategories.iterator().next();
                a.setCategory(primaryCat);
                a.setSelectedCategories(new ArrayList<>(selectedCategories));
                a.setSelectedSubcategories(new ArrayList<>(selectedSubcategories));
                a.setSelectedProducts(new ArrayList<>(selectedProducts));

                if (etCustomCategoryText != null
                        && etCustomCategoryText.getVisibility() == View.VISIBLE) {
                    String custom = etCustomCategoryText.getText().toString().trim();
                    if (!custom.isEmpty()) a.setCustomSubcategory(custom);
                }

                a.setTitle(title);
                a.setDescription(desc);
                a.setPhone(u.getPhone());
                a.setLat(u.getLat());
                a.setLng(u.getLng());
                a.setTime(now);
                a.setExpireAt(now + expiryMillis);

                if (selectedBitmap != null) {
                    // ✅ UPDATED: Use getString()
                    if (tvImageStatus != null) tvImageStatus.setText(getString(R.string.uploading_image));
                    CloudinaryUploader.uploadBitmap(
                            AddAnnouncementActivity.this, selectedBitmap,
                            new CloudinaryUploader.UploadListener() {
                                @Override public void onSuccess(String url) {
                                    a.setImageUrl(url);
                                    uploadAudioThenSave(a, id);
                                }
                                @Override public void onError(String msg) {
                                    a.setImageUrl("");
                                    toast("Image upload failed, posting without image");
                                    uploadAudioThenSave(a, id);
                                }
                            });
                } else {
                    a.setImageUrl("");
                    uploadAudioThenSave(a, id);
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError e) {
                reset(); toast("Failed: " + e.getMessage());
            }
        });
    }

    private void uploadAudioThenSave(Announcement a, String annId) {
        if (recordedAudioPath != null && !recordedAudioPath.isEmpty()) {
            // ✅ UPDATED: Use getString()
            if (tvImageStatus != null) tvImageStatus.setText(getString(R.string.uploading_audio));
            AudioRecorderHelper.uploadAudio(recordedAudioPath, annId,
                    new AudioRecorderHelper.UploadListener() {
                        @Override public void onProgress(int pct) {
                            runOnUiThread(() -> {
                                if (tvImageStatus != null)
                                    tvImageStatus.setText(getString(R.string.uploading_audio) + " " + pct + "%");
                            });
                        }
                        @Override public void onSuccess(String url) {
                            a.setAudioUrl(url);
                            runOnUiThread(() -> saveAnnouncement(a));
                        }
                        @Override public void onError(String msg) {
                            a.setAudioUrl("");
                            runOnUiThread(() -> {
                                toast("Audio upload failed, posting without audio");
                                saveAnnouncement(a);
                            });
                        }
                    });
        } else {
            a.setAudioUrl("");
            saveAnnouncement(a);
        }
    }

    private void saveAnnouncement(Announcement a) {
        // ✅ UPDATED: Use getString()
        if (tvImageStatus != null) tvImageStatus.setText(getString(R.string.publishing));
        annRef.child(a.getId()).setValue(a)
                .addOnSuccessListener(v -> {
                    NotificationSender.sendAnnouncementNotification(
                            a.getId(), a.getTitle(), a.getDescription(),
                            a.getLat(), a.getLng(), a.getUserId());
                    // ✅ UPDATED: Use getString()
                    toast("request".equals(postType)
                            ? getString(R.string.request_posted)
                            : getString(R.string.published_success));
                    finish();
                })
                .addOnFailureListener(e -> {
                    reset(); toast("Save failed: " + e.getMessage());
                });
    }

    private void reset() {
        if (btnPublish != null) btnPublish.setEnabled(true);
        if (tvImageStatus != null) tvImageStatus.setText("");
    }

    // ════════════════════════════════════════════════════════════════════════
    // Audio / Image helpers
    // ════════════════════════════════════════════════════════════════════════

    private void showAudioOptions() {
        if (isRecording) { stopAudioRecording(); return; }
        // ✅ UPDATED: Use getString()
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.add_audio_title))
                .setItems(new String[]{
                        getString(R.string.record_audio),
                        getString(R.string.select_audio_file)
                }, (d, which) -> {
                    if (which == 0) startAudioRecording();
                    else            openAudioFilePicker();
                })
                .setNegativeButton(getString(R.string.cancel), null).show();
    }

    private void openAudioFilePicker() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("audio/*"); i.addCategory(Intent.CATEGORY_OPENABLE);
        audioFileLauncher.launch(i);
    }

    private void handleSelectedAudioFile(Uri uri) {
        if (uri == null) return;
        try {
            File out = new File(getCacheDir(), "tc_audio_sel_" + System.currentTimeMillis() + ".m4a");
            InputStream in = getContentResolver().openInputStream(uri);
            if (in == null) { toast("Could not read audio"); return; }
            OutputStream os = new FileOutputStream(out);
            byte[] buf = new byte[8192]; int r;
            while ((r = in.read(buf)) != -1) os.write(buf, 0, r);
            in.close(); os.close();
            recordedAudioPath = out.getAbsolutePath();
            // ✅ UPDATED: Use getString()
            if (tvAudioStatus != null) tvAudioStatus.setText(getString(R.string.audio_selected));
        } catch (IOException e) { toast("Audio load failed"); }
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
                    // ✅ UPDATED: Use getString()
                    if (tvAudioStatus != null) tvAudioStatus.setText(getString(R.string.recording_start));
                    updateAudioButton(true);
                });
            }
            @Override public void onRecordStopped(String path) {}
            @Override public void onError(String msg) {
                isRecording = false;
                runOnUiThread(() -> {
                    if (tvAudioStatus != null) tvAudioStatus.setText("❌ Error: " + msg);
                    updateAudioButton(false);
                });
            }
        });
    }

    private void stopAudioRecording() {
        audioRecorder.stopRecording(new AudioRecorderHelper.RecordListener() {
            @Override public void onRecordStarted() {}
            @Override public void onRecordStopped(String path) {
                isRecording = false;
                recordedAudioPath = path;
                runOnUiThread(() -> {
                    // ✅ UPDATED: Use getString()
                    if (tvAudioStatus != null) tvAudioStatus.setText(getString(R.string.audio_recorded));
                    updateAudioButton(false);
                });
            }
            @Override public void onError(String msg) {
                isRecording = false;
                runOnUiThread(() -> {
                    if (tvAudioStatus != null) tvAudioStatus.setText("❌ Stop failed");
                    updateAudioButton(false);
                });
            }
        });
    }

    private void updateAudioButton(boolean recording) {
        if (btnRecordAudio instanceof LinearLayout) {
            LinearLayout ll = (LinearLayout) btnRecordAudio;
            for (int i = 0; i < ll.getChildCount(); i++) {
                View child = ll.getChildAt(i);
                if (child instanceof TextView)
                    // ✅ UPDATED: Use getString()
                    ((TextView) child).setText(recording
                            ? getString(R.string.stop)
                            : getString(R.string.add_audio));
            }
        }
    }

    private void setupPermissions() {
        permissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                    if (Boolean.TRUE.equals(result.get(Manifest.permission.RECORD_AUDIO)))
                        startAudioRecording();
                });
    }

    private void setupLaunchers() {
        galleryLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), res -> {
                    if (res.getResultCode() == RESULT_OK && res.getData() != null) {
                        Uri uri = res.getData().getData();
                        if (uri == null) return;
                        try {
                            selectedBitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
                            if (ivPreview != null) {
                                ivPreview.setVisibility(View.VISIBLE);
                                ivPreview.setImageBitmap(selectedBitmap);
                            }
                            // ✅ UPDATED: Use getString()
                            if (tvImageStatus != null) tvImageStatus.setText(getString(R.string.image_selected));
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
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.add_image))
                .setItems(new String[]{"📷 Gallery"}, (d, w) -> {
                    Intent i = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                    i.setType("image/*");
                    galleryLauncher.launch(i);
                }).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (audioRecorder != null && isRecording) audioRecorder.cancelRecording();
    }

    private GradientDrawable roundedBg(int color, int radius) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(color);
        gd.setCornerRadius(radius);
        return gd;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}