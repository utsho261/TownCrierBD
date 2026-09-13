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
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.example.towncrierbd.R;
import com.example.towncrierbd.models.Announcement;
import com.example.towncrierbd.models.UserModel;
import com.example.towncrierbd.utils.*;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;

import java.io.*;
import java.util.*;

public class AddAnnouncementActivity extends AppCompatActivity {

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
    private TextView tvImageStatus, tvAudioStatus;
    private View     btnAddImage, btnRecordAudio;

    // Audio Player UI
    private LinearLayout llAudioPlayer;
    private ImageView    btnPlayAudio, btnDeleteAudio;
    private TextView     tvAudioDuration;
    private MediaPlayer  mediaPlayer;
    private Handler      playbackHandler = new Handler(Looper.getMainLooper());

    // Step 3 bilingual label views
    private TextView tvLabelTitle, tvLabelDesc, tvLabelExpiry, tvExpiryMax, tvExpiryInfo;
    private TextView tvBtnAddImage, tvBtnAddAudio;

    // Step 2 labels
    private TextView tvStep2Title, tvStep2Subtitle;

    private String postType = "announcement";

    private FirebaseAuth      auth;
    private DatabaseReference annRef, userRef;

    private List<String> userHawkerCategories    = new ArrayList<>();
    private List<String> userHawkerSubcategories = new ArrayList<>();
    private String       userHawkerOthersName    = "";

    public static final String EXTRA_EDIT_ID = "editAnnouncementId";

    private String            editAnnouncementId = null;
    private Announcement      existingAnnouncement = null;
    private final List<String> existingImageUrls = new ArrayList<>();
    private String            existingAudioUrl = null;

    private List<Bitmap>      selectedBitmaps = new ArrayList<>();
    private LinearLayout      llImagePreviews;
    private HorizontalScrollView hsvImagePreview;
    private AudioRecorderHelper audioRecorder;
    private String            recordedAudioPath;
    private boolean           isRecording;

    private ActivityResultLauncher<Intent>   galleryLauncher, audioFileLauncher;
    private ActivityResultLauncher<String[]> permissionLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_announcement_wizard);

        if (getIntent() != null && getIntent().hasExtra(EXTRA_EDIT_ID)) {
            editAnnouncementId = getIntent().getStringExtra(EXTRA_EDIT_ID);
        }

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
        tvImageStatus   = findViewById(R.id.tvImageStatus);
        tvAudioStatus   = findViewById(R.id.tvAudioStatus);
        btnAddImage     = findViewById(R.id.btnAddImage);
        btnRecordAudio  = findViewById(R.id.btnRecordAudio);

        llImagePreviews = findViewById(R.id.llImagePreviews);
        hsvImagePreview = findViewById(R.id.hsvImagePreview);

        llAudioPlayer   = findViewById(R.id.llAudioPlayer);
        btnPlayAudio    = findViewById(R.id.btnPlayAudio);
        btnDeleteAudio  = findViewById(R.id.btnDeleteAudio);
        tvAudioDuration = findViewById(R.id.tvAudioDuration);

        if (btnPlayAudio != null) btnPlayAudio.setOnClickListener(v -> toggleAudioPlayback());
        if (btnDeleteAudio != null) btnDeleteAudio.setOnClickListener(v -> deleteSelectedAudio());

        // Step 3 bilingual label views
        tvLabelTitle   = findViewById(R.id.tvLabelTitle);
        tvLabelDesc    = findViewById(R.id.tvLabelDesc);
        tvLabelExpiry  = findViewById(R.id.tvLabelExpiry);
        tvExpiryMax    = findViewById(R.id.tvExpiryMax);
        tvExpiryInfo   = findViewById(R.id.tvExpiryInfo);
        tvBtnAddImage  = findViewById(R.id.tvBtnAddImage);
        tvBtnAddAudio  = findViewById(R.id.tvBtnAddAudio);

        // Step 2 label views
        tvStep2Title    = findViewById(R.id.tvStep2Title);
        tvStep2Subtitle = findViewById(R.id.tvStep2Subtitle);

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
        applyWizardStrings();
        setupExpiryUI();

        loadUserProfileThenInit();
        showStep(1);
    }

    // ════════════════════════════════════════════════════════════════════════
    // ✅ Apply bilingual strings to ALL wizard views
    // ════════════════════════════════════════════════════════════════════════
    private void applyWizardStrings() {
        AppStrings s = AppStrings.get(this);

        // ── Step 1 ──
        TextView tvStep1Title = stepCategory != null ? stepCategory.findViewById(R.id.tvStep1Title) : null;
        TextView tvStep1Sub   = stepCategory != null ? stepCategory.findViewById(R.id.tvStep1Subtitle) : null;
        Button   btnNext1     = stepCategory != null ? stepCategory.findViewById(R.id.btnNextStep1) : null;
        if (tvStep1Title != null) tvStep1Title.setText(s.addStep1Title());
        if (tvStep1Sub   != null) tvStep1Sub.setText(s.addStep1Subtitle());
        if (btnNext1     != null) btnNext1.setText(s.addStep1Next());

        // ── Step 2 ──
        if (tvStep2Title    != null) tvStep2Title.setText(s.addStep2Title());
        if (tvStep2Subtitle != null) tvStep2Subtitle.setText(s.addStep2Subtitle());
        Button btnBack2 = stepProducts != null ? stepProducts.findViewById(R.id.btnBackStep2) : null;
        Button btnNext2 = stepProducts != null ? stepProducts.findViewById(R.id.btnNextStep2) : null;
        if (btnBack2 != null) btnBack2.setText(s.addStep2Back());
        if (btnNext2 != null) btnNext2.setText(s.addStep2Next());

        // ── Step 3 labels ── ✅ These are the ones previously not updating
        if (tvLabelTitle  != null) tvLabelTitle.setText(s.addStep3LabelTitle());
        if (tvLabelDesc   != null) tvLabelDesc.setText(s.addStep3LabelDesc());
        if (tvLabelExpiry != null) tvLabelExpiry.setText(s.addStep3ExpiryLabel());
        // ✅ tvExpiryMax: show "max 7 days" text bilingual
        if (tvExpiryMax   != null) tvExpiryMax.setText(s.addStep3ExpiryMax());
        // ✅ tvExpiryInfo: "Post disappears automatically..."
        if (tvExpiryInfo  != null) tvExpiryInfo.setText(s.addStep3ExpiryInfo());

        // ── Step 3 hint texts — setHint() only, never setText() ──
        if (etTitle       != null) etTitle.setHint(s.addStep3HintTitle());
        if (etDesc        != null) etDesc.setHint(s.addStep3HintDesc());
        if (etExpiryValue != null) etExpiryValue.setHint(s.addStep3ExpiryHint());

        // ── Image / Audio button labels ──
        if (tvBtnAddImage != null) tvBtnAddImage.setText(s.addStep3AddImage());
        if (tvBtnAddAudio != null) tvBtnAddAudio.setText(s.addStep3AddAudio());

        // ── Publish button ──
        if (btnPublish != null) {
            btnPublish.setText(editAnnouncementId != null ? s.addEditWizardUpdateBtn() : s.addStep3Publish());
        }

        // ── Back button on step 3 ──
        Button btnBack3 = stepDetails != null ? stepDetails.findViewById(R.id.btnBackStep3) : null;
        if (btnBack3 != null) btnBack3.setText(s.addStep3Back());

        // ── Custom category hint ──
        if (etCustomCategoryText != null)
            etCustomCategoryText.setHint(s.addStep2CustomHint());
    }

    // ════════════════════════════════════════════════════════════════════════
    // Expiry UI
    // ════════════════════════════════════════════════════════════════════════
    private void setupExpiryUI() {
        if (spExpiryUnit == null) return;
        AppStrings s = AppStrings.get(this);
        // ✅ Units array from AppStrings — auto-bilingual
        String[] units = s.addStep3ExpiryUnits();

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
                @Override public void beforeTextChanged(CharSequence s2, int st, int c, int af) {}
                @Override public void afterTextChanged(Editable s2) {}
                @Override public void onTextChanged(CharSequence s2, int st, int b, int c) {
                    updateExpiryPreview();
                }
            });
        }

        spExpiryUnit.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) { updateExpiryPreview(); }
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
        AppStrings s = AppStrings.get(this);
        long millis = getExpiryMillis();

        if (millis == -1) {
            tvExpiryPreview.setVisibility(View.GONE);
            tvExpiryPreview.setText("");
            return;
        }

        tvExpiryPreview.setVisibility(View.VISIBLE);

        if (millis == -2) {
            tvExpiryPreview.setText(s.addStep3ExpiryOver());
            tvExpiryPreview.setTextColor(0xFFEF4444);
            tvExpiryPreview.setBackgroundColor(0xFFFEE2E2);
            return;
        }

        tvExpiryPreview.setTextColor(0xFF1565C0);
        tvExpiryPreview.setBackgroundColor(0xFFE3F2FD);
        tvExpiryPreview.setText(s.addStep3ExpiryPreview(humanReadable(millis)));
    }

    private String humanReadable(long millis) {
        AppStrings s = AppStrings.get(this);
        long tot = millis / 60_000L;
        long d = tot / (24 * 60), h = (tot % (24 * 60)) / 60, m = tot % 60;
        StringBuilder sb = new StringBuilder();
        if (d > 0) sb.append(s.expiryHumanDay(d));
        if (h > 0) { if (sb.length() > 0) sb.append(" "); sb.append(s.expiryHumanHour(h)); }
        if (m > 0) { if (sb.length() > 0) sb.append(" "); sb.append(s.expiryHumanMin(m)); }
        return sb.length() > 0 ? sb.toString() : s.expirySoon();
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

                if (editAnnouncementId != null) {
                    loadAnnouncementForEdit(editAnnouncementId);
                } else {
                    buildCategoryStep();
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError e) {}
        });
    }

    private void updatePostTypeHeader() {
        if (tvPostTypeHeader == null) return;
        AppStrings s = AppStrings.get(this);
        if (editAnnouncementId != null) {
            tvPostTypeHeader.setText(s.addEditWizardTitle());
            return;
        }
        if ("request".equals(postType)) {
            tvPostTypeHeader.setText(s.addHeaderRequest());
        } else {
            tvPostTypeHeader.setText(s.addHeaderAnnouncement());
        }
    }

    private void loadAnnouncementForEdit(String id) {
        annRef.child(id).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot s) {
                Announcement a = s.getValue(Announcement.class);
                if (a == null) {
                    toast(LanguageManager.isEnglish(AddAnnouncementActivity.this) ? "Post not found" : "পোস্টটি পাওয়া যায়নি");
                    finish();
                    return;
                }
                if (a.getId() == null || a.getId().isEmpty()) a.setId(s.getKey());
                existingAnnouncement = a;

                if (a.getPostType() != null && !a.getPostType().isEmpty()) {
                    postType = a.getPostType();
                }
                updatePostTypeHeader();

                AppStrings str = AppStrings.get(AddAnnouncementActivity.this);
                if (btnPublish != null) {
                    btnPublish.setText(str.addEditWizardUpdateBtn());
                }

                selectedCategories.clear();
                if (a.getSelectedCategories() != null && !a.getSelectedCategories().isEmpty()) {
                    selectedCategories.addAll(a.getSelectedCategories());
                } else if (a.getCategory() != null && !a.getCategory().isEmpty()) {
                    selectedCategories.add(a.getCategory());
                }

                selectedSubcategories.clear();
                if (a.getSelectedSubcategories() != null) {
                    selectedSubcategories.addAll(a.getSelectedSubcategories());
                }

                selectedProducts.clear();
                if (a.getSelectedProducts() != null) {
                    selectedProducts.addAll(a.getSelectedProducts());
                }

                if (etTitle != null) etTitle.setText(a.getTitle());
                if (etDesc != null) etDesc.setText(a.getDescription());
                if (etCustomCategoryText != null && a.getCustomSubcategory() != null) {
                    etCustomCategoryText.setText(a.getCustomSubcategory());
                }

                // Expiry setup
                long now = System.currentTimeMillis();
                long diff = a.getExpireAt() - now;
                if (diff > 0) {
                    long hours = diff / 3600_000L;
                    if (hours >= 1) {
                        if (spExpiryUnit != null) spExpiryUnit.setSelection(UNIT_HOURS);
                        if (etExpiryValue != null) etExpiryValue.setText(String.valueOf(hours));
                    } else {
                        long mins = Math.max(1, diff / 60_000L);
                        if (spExpiryUnit != null) spExpiryUnit.setSelection(UNIT_MINUTES);
                        if (etExpiryValue != null) etExpiryValue.setText(String.valueOf(mins));
                    }
                } else {
                    if (spExpiryUnit != null) spExpiryUnit.setSelection(UNIT_HOURS);
                    if (etExpiryValue != null) etExpiryValue.setText("24");
                }
                updateExpiryPreview();

                // Existing images
                existingImageUrls.clear();
                if (a.getImageUrls() != null && !a.getImageUrls().isEmpty()) {
                    existingImageUrls.addAll(a.getImageUrls());
                } else if (a.getImageUrl() != null && !a.getImageUrl().isEmpty()) {
                    existingImageUrls.add(a.getImageUrl());
                }
                updateImagePreviews();

                // Existing audio
                existingAudioUrl = a.getAudioUrl();
                if (existingAudioUrl != null && !existingAudioUrl.isEmpty()) {
                    showAudioPlayer();
                    if (tvAudioStatus != null)
                        tvAudioStatus.setText(LanguageManager.isEnglish(AddAnnouncementActivity.this)
                                ? "Existing audio attached" : "বিদ্যমান অডিও সংযুক্ত আছে");
                }

                buildCategoryStep();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                toast(LanguageManager.isEnglish(AddAnnouncementActivity.this) ? "Failed to load post" : "পোস্ট লোড করতে ব্যর্থ হয়েছে");
            }
        });
    }

    // ════════════════════════════════════════════════════════════════════════
    // Step navigation
    // ════════════════════════════════════════════════════════════════════════
    private void showStep(int step) {
        if (stepCategory != null) stepCategory.setVisibility(step == 1 ? View.VISIBLE : View.GONE);
        if (stepProducts  != null) stepProducts.setVisibility(step == 2 ? View.VISIBLE : View.GONE);
        if (stepDetails   != null) stepDetails.setVisibility(step == 3 ? View.VISIBLE : View.GONE);
        if (tvStepIndicator != null) tvStepIndicator.setText(AppStrings.get(this).addStepOf(step, 3));
    }

    private void goToStep2() {
        if (selectedCategories.isEmpty()) {
            toast(AppStrings.get(this).addStep1SelectAtLeast());
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
        AppStrings s = AppStrings.get(this);
        boolean isBn = !LanguageManager.isEnglish(this);

        List<CategoryConfig.HawkerCategory> categoriesToShow = new ArrayList<>();

        if (Constants.ROLE_ANNOUNCER.equals("announcement".equals(postType) ? Constants.ROLE_ANNOUNCER : "")
                && !userHawkerCategories.isEmpty()) {
            for (CategoryConfig.HawkerCategory cat : CategoryConfig.HAWKER_CATEGORIES) {
                if (userHawkerCategories.contains(cat.name)) categoriesToShow.add(cat);
            }
        } else {
            categoriesToShow = CategoryConfig.HAWKER_CATEGORIES;
        }
        if (categoriesToShow.isEmpty()) categoriesToShow = CategoryConfig.HAWKER_CATEGORIES;

        if (!selectedCategories.isEmpty()) {
            for (CategoryConfig.HawkerCategory cat : CategoryConfig.HAWKER_CATEGORIES) {
                if (selectedCategories.contains(cat.name) && !categoriesToShow.contains(cat)) {
                    categoriesToShow.add(cat);
                }
            }
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
            if (selectedCategories.contains(cat.name)) {
                cb.setChecked(true);
                row.setBackground(roundedBg(0xFFEFF6FF, dp(12)));
            }

            TextView tvLabel = new TextView(this);
            tvLabel.setText(cat.emoji + "  " + (isBn ? cat.nameBn : cat.name));
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
                tvHint.setText(s.addStep1Items(selectedCount));
                tvHint.setTextSize(11);
                tvHint.setTextColor(0xFF9CA3AF);
            } else if (!cat.subGroups.isEmpty()) {
                int total = 0;
                for (CategoryConfig.SubGroup g : cat.subGroups) total += g.items.size();
                tvHint.setText(s.addStep1Items(total));
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
            TextView tvHint2 = new TextView(this);
            tvHint2.setText(s.addStep1EditCatHint());
            tvHint2.setTextSize(12);
            tvHint2.setTextColor(0xFF6B7280);
            tvHint2.setPadding(dp(4), dp(12), dp(4), 0);
            llCategoryList.addView(tvHint2);
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // STEP 2 — Product selection
    // ════════════════════════════════════════════════════════════════════════
    private void buildProductStep() {
        if (llProductList == null) return;
        llProductList.removeAllViews();
        productCheckBoxes.clear();
        AppStrings s = AppStrings.get(this);
        boolean isBn = !LanguageManager.isEnglish(this);
        boolean isAnnouncer = "announcement".equals(postType);
        boolean hasOthers = selectedCategories.contains("Others");

        if (etCustomCategoryText != null) {
            etCustomCategoryText.setVisibility(hasOthers ? View.VISIBLE : View.GONE);
            if (hasOthers) {
                String hint = userHawkerOthersName.isEmpty()
                        ? s.addStep2CustomHint()
                        : (LanguageManager.isEnglish(this) ? "e.g. " : "যেমন: ") + userHawkerOthersName;
                etCustomCategoryText.setHint(hint);
            }
        }

        boolean anyAdded = false;

        for (CategoryConfig.HawkerCategory cat : CategoryConfig.HAWKER_CATEGORIES) {
            if (!selectedCategories.contains(cat.name)) continue;
            if ("Others".equals(cat.name)) continue;

            TextView tvCatHeader = new TextView(this);
            tvCatHeader.setText(cat.emoji + "  " + (isBn ? cat.nameBn : cat.name));
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
                tvSelectAll.setText(s.addStep2SelectAll(isBn ? cat.nameBn : cat.name));
                tvSelectAll.setTextSize(12);
                tvSelectAll.setTextColor(0xFF1976F3);
                tvSelectAll.setPadding(0, 0, 0, dp(4));
                final CategoryConfig.HawkerCategory finalCat = cat;
                tvSelectAll.setOnClickListener(v -> selectAllForCategory(finalCat));
                llProductList.addView(tvSelectAll);
            }

            for (CategoryConfig.SubGroup group : cat.subGroups) {
                List<String> engItemsToShow;
                List<String> dispItemsToShow;

                if (isAnnouncer && !userHawkerSubcategories.isEmpty()) {
                    engItemsToShow  = new ArrayList<>();
                    dispItemsToShow = new ArrayList<>();
                    for (int idx = 0; idx < group.items.size(); idx++) {
                        if (userHawkerSubcategories.contains(group.items.get(idx))) {
                            engItemsToShow.add(group.items.get(idx));
                            dispItemsToShow.add(isBn ? group.itemsBn.get(idx) : group.items.get(idx));
                        }
                    }
                    if (engItemsToShow.isEmpty()) continue;
                } else {
                    engItemsToShow  = group.items;
                    dispItemsToShow = isBn ? group.itemsBn : group.items;
                }

                TextView tvSub = new TextView(this);
                tvSub.setText("▸ " + (isBn ? group.groupNameBn : group.groupName));
                tvSub.setTextSize(12);
                tvSub.setTypeface(null, Typeface.BOLD);
                tvSub.setTextColor(0xFF6B7280);
                tvSub.setPadding(dp(4), dp(8), 0, dp(4));
                llProductList.addView(tvSub);

                for (int i = 0; i < dispItemsToShow.size(); i += 2) {
                    LinearLayout rowLayout = new LinearLayout(this);
                    rowLayout.setOrientation(LinearLayout.HORIZONTAL);
                    LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    rlp.setMargins(0, dp(2), 0, dp(2));
                    rowLayout.setLayoutParams(rlp);

                    addProductCheckBox(rowLayout, engItemsToShow.get(i), dispItemsToShow.get(i), group.groupName);
                    if (i + 1 < dispItemsToShow.size())
                        addProductCheckBox(rowLayout, engItemsToShow.get(i + 1), dispItemsToShow.get(i + 1), group.groupName);
                    llProductList.addView(rowLayout);
                }
            }
        }

        if (hasOthers && !anyAdded) {
            TextView tvInfo = new TextView(this);
            tvInfo.setText(AppStrings.get(this).addStep2NeedDescribe(postType));
            tvInfo.setTextSize(13);
            tvInfo.setTextColor(0xFF6B7280);
            tvInfo.setPadding(0, dp(8), 0, 0);
            llProductList.addView(tvInfo);
        }

        if (llProductList.getChildCount() == 0) {
            TextView tvInfo = new TextView(this);
            tvInfo.setText(s.addStep2NoProducts());
            tvInfo.setTextSize(13);
            tvInfo.setTextColor(0xFF6B7280);
            tvInfo.setPadding(dp(4), dp(8), dp(4), 0);
            llProductList.addView(tvInfo);
        }
    }

    private void addProductCheckBox(LinearLayout parent, String engKey, String displayText, String subcategory) {
        CheckBox cb = new CheckBox(this);
        cb.setText(displayText);
        cb.setTextSize(13);
        cb.setTextColor(0xFF374151);
        cb.setButtonTintList(ColorStateList.valueOf(0xFF1976F3));
        cb.setChecked(selectedProducts.contains(engKey));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        clp.setMargins(dp(2), dp(1), dp(2), dp(1));
        cb.setLayoutParams(clp);
        cb.setTag(engKey);

        cb.setOnCheckedChangeListener((btn, checked) -> {
            if (checked) {
                selectedProducts.add(engKey);
                selectedSubcategories.add(subcategory);
            } else {
                selectedProducts.remove(engKey);
            }
        });

        productCheckBoxes.add(cb);
        parent.addView(cb);
    }

    private void selectAllForCategory(CategoryConfig.HawkerCategory cat) {
        for (CategoryConfig.SubGroup g : cat.subGroups) {
            selectedProducts.addAll(g.items);
            selectedSubcategories.add(g.groupName);
        }
        for (CheckBox cb : productCheckBoxes) {
            String tag = (String) cb.getTag();
            if (tag != null && selectedProducts.contains(tag)) cb.setChecked(true);
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // Publish
    // ════════════════════════════════════════════════════════════════════════
    private void publish() {
        AppStrings s = AppStrings.get(this);
        if (isRecording) { toast(s.addStopRecordFirst()); return; }

        String title = etTitle.getText().toString().trim();
        String desc  = etDesc.getText().toString().trim();

        if (title.isEmpty()) { toast(s.addPublishNoTitle()); etTitle.requestFocus(); return; }

        long expiryMillis = getExpiryMillis();
        if (expiryMillis == -1) {
            toast(s.addPublishNoExpiry());
            if (etExpiryValue != null) etExpiryValue.requestFocus();
            return;
        }
        if (expiryMillis == -2) {
            toast(s.addPublishExpiryOver());
            if (etExpiryValue != null) etExpiryValue.requestFocus();
            return;
        }

        String uid = auth.getUid();
        if (uid == null) return;

        btnPublish.setEnabled(false);
        if (tvImageStatus != null) tvImageStatus.setText(s.pleaseWait());

        userRef.child(uid).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                UserModel u = snapshot.getValue(UserModel.class);
                if (u == null) { reset(); toast(s.addPublishUserNotFound()); return; }
                if (u.getLat() == 0.0 && u.getLng() == 0.0) {
                    reset(); toast(s.addPublishNoLoc()); return;
                }

                String id = (editAnnouncementId != null) ? editAnnouncementId : annRef.push().getKey();
                if (id == null) { reset(); return; }

                long now = System.currentTimeMillis();

                Announcement a = (existingAnnouncement != null) ? existingAnnouncement : new Announcement();
                a.setId(id);
                a.setActive(true);
                if (existingAnnouncement == null) {
                    a.setUserId(uid);
                    a.setUserName(u.getName());
                    String role = (u.getRole() == null || u.getRole().trim().isEmpty())
                            ? Constants.ROLE_USER : u.getRole().trim();
                    a.setUserRole(role);
                    a.setPhone(u.getPhone());
                    a.setLat(u.getLat());
                    a.setLng(u.getLng());
                }
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
                    else a.setCustomSubcategory("");
                } else {
                    a.setCustomSubcategory("");
                }

                a.setTitle(title);
                a.setDescription(desc);
                a.setTime(now);
                a.setExpireAt(now + expiryMillis);

                if (!selectedBitmaps.isEmpty()) {
                    uploadImagesThenAudio(a, id, 0, new ArrayList<>());
                } else {
                    a.setImageUrls(new ArrayList<>(existingImageUrls));
                    a.setImageUrl(existingImageUrls.isEmpty() ? "" : existingImageUrls.get(0));
                    uploadAudioThenSave(a, id);
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError e) {
                reset(); toast(s.addPublishFailed(e.getMessage()));
            }
        });
    }

    private void uploadImagesThenAudio(Announcement a, String annId, int index, List<String> uploadedUrls) {
        AppStrings s = AppStrings.get(this);
        if (index >= selectedBitmaps.size()) {
            List<String> finalUrls = new ArrayList<>(existingImageUrls);
            finalUrls.addAll(uploadedUrls);
            a.setImageUrls(finalUrls);
            a.setImageUrl(finalUrls.isEmpty() ? "" : finalUrls.get(0));
            uploadAudioThenSave(a, annId);
            return;
        }

        if (tvImageStatus != null) {
            tvImageStatus.setText(p("Uploading image " + (index + 1) + "/" + selectedBitmaps.size() + "...",
                    "ছবি আপলোড হচ্ছে " + (index + 1) + "/" + selectedBitmaps.size() + "..."));
        }

        CloudinaryUploader.uploadBitmap(this, selectedBitmaps.get(index), new CloudinaryUploader.UploadListener() {
            @Override
            public void onSuccess(String url) {
                uploadedUrls.add(url);
                uploadImagesThenAudio(a, annId, index + 1, uploadedUrls);
            }

            @Override
            public void onError(String message) {
                // If one fails, continue with what we have
                uploadImagesThenAudio(a, annId, index + 1, uploadedUrls);
            }
        });
    }

    private String p(String en, String bn) {
        return LanguageManager.isEnglish(this) ? en : bn;
    }

    private void uploadAudioThenSave(Announcement a, String annId) {
        AppStrings s = AppStrings.get(this);
        if (recordedAudioPath != null && !recordedAudioPath.isEmpty()) {
            if (tvImageStatus != null) tvImageStatus.setText(s.addPublishAudioUploading());
            AudioRecorderHelper.uploadAudio(recordedAudioPath, annId,
                    new AudioRecorderHelper.UploadListener() {
                        @Override public void onProgress(int pct) {
                            runOnUiThread(() -> {
                                if (tvImageStatus != null)
                                    tvImageStatus.setText(s.addPublishAudioPct(pct));
                            });
                        }
                        @Override public void onSuccess(String url) {
                            a.setAudioUrl(url);
                            runOnUiThread(() -> saveAnnouncement(a));
                        }
                        @Override public void onError(String msg) {
                            a.setAudioUrl("");
                            runOnUiThread(() -> {
                                toast(s.addPublishAudioFailed());
                                saveAnnouncement(a);
                            });
                        }
                    });
        } else if (existingAudioUrl != null && !existingAudioUrl.isEmpty()) {
            a.setAudioUrl(existingAudioUrl);
            saveAnnouncement(a);
        } else {
            a.setAudioUrl("");
            saveAnnouncement(a);
        }
    }

    private void saveAnnouncement(Announcement a) {
        AppStrings s = AppStrings.get(this);
        if (tvImageStatus != null) tvImageStatus.setText(s.addPublishing());
        annRef.child(a.getId()).setValue(a)
                .addOnSuccessListener(v -> {
                    if (editAnnouncementId == null) {
                        NotificationSender.sendAnnouncementNotification(
                                a.getId(), a.getTitle(), a.getDescription(),
                                a.getLat(), a.getLng(), a.getUserId());
                        toast(s.addPublishSuccess(postType));
                    } else {
                        toast(s.addEditWizardSuccess());
                    }
                    finish();
                })
                .addOnFailureListener(e -> {
                    reset(); toast(s.addPublishFailed(e.getMessage()));
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
        AppStrings s = AppStrings.get(this);
        if (isRecording) { stopAudioRecording(); return; }
        new AlertDialog.Builder(this)
                .setTitle(s.audioAddTitle())
                .setItems(new String[]{s.audioOptionRecord(), s.audioOptionFile()}, (d, which) -> {
                    if (which == 0) startAudioRecording();
                    else            openAudioFilePicker();
                })
                .setNegativeButton(s.cancel(), null).show();
    }

    private void openAudioFilePicker() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("audio/*"); i.addCategory(Intent.CATEGORY_OPENABLE);
        audioFileLauncher.launch(i);
    }

    private void handleSelectedAudioFile(Uri uri) {
        AppStrings s = AppStrings.get(this);
        if (uri == null) return;
        try {
            File out = new File(getCacheDir(), "tc_audio_sel_" + System.currentTimeMillis() + ".m4a");
            InputStream in = getContentResolver().openInputStream(uri);
            if (in == null) { toast(s.audioCouldNotRead()); return; }
            OutputStream os = new FileOutputStream(out);
            byte[] buf = new byte[8192]; int r;
            while ((r = in.read(buf)) != -1) os.write(buf, 0, r);
            in.close(); os.close();
            recordedAudioPath = out.getAbsolutePath();
            if (tvAudioStatus != null) tvAudioStatus.setText(s.audioFileSelected());
            showAudioPlayer();
        } catch (IOException e) { toast(s.audioLoadFailed()); }
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
                    AppStrings s = AppStrings.get(AddAnnouncementActivity.this);
                    if (tvAudioStatus != null) tvAudioStatus.setText(s.audioRecording());
                    updateAudioButton(true);
                });
            }
            @Override public void onRecordStopped(String path) {}
            @Override public void onError(String msg) {
                isRecording = false;
                runOnUiThread(() -> {
                    AppStrings s = AppStrings.get(AddAnnouncementActivity.this);
                    if (tvAudioStatus != null) tvAudioStatus.setText(s.audioRecordError(msg));
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
                    AppStrings s = AppStrings.get(AddAnnouncementActivity.this);
                    if (tvAudioStatus != null) tvAudioStatus.setText(s.audioRecorded());
                    updateAudioButton(false);
                    showAudioPlayer();
                });
            }
            @Override public void onError(String msg) {
                isRecording = false;
                runOnUiThread(() -> {
                    AppStrings s = AppStrings.get(AddAnnouncementActivity.this);
                    if (tvAudioStatus != null) tvAudioStatus.setText(s.audioStopFailed());
                    updateAudioButton(false);
                });
            }
        });
    }

    private void updateAudioButton(boolean recording) {
        AppStrings s = AppStrings.get(this);
        if (tvBtnAddAudio != null)
            tvBtnAddAudio.setText(recording ? s.audioStopBtn() : s.addStep3AddAudio());
    }

    private void showAudioPlayer() {
        boolean hasAudio = (recordedAudioPath != null && !recordedAudioPath.isEmpty())
                || (existingAudioUrl != null && !existingAudioUrl.isEmpty());
        if (!hasAudio) {
            if (llAudioPlayer != null) llAudioPlayer.setVisibility(View.GONE);
            return;
        }
        if (llAudioPlayer != null) {
            llAudioPlayer.setVisibility(View.VISIBLE);
            tvAudioDuration.setText("0:00");
            btnPlayAudio.setImageResource(android.R.drawable.ic_media_play);
        }
    }

    private void toggleAudioPlayback() {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            stopPlayback();
        } else {
            startPlayback();
        }
    }

    private void startPlayback() {
        String audioSource = (recordedAudioPath != null && !recordedAudioPath.isEmpty())
                ? recordedAudioPath : existingAudioUrl;
        if (audioSource == null || audioSource.isEmpty()) return;
        try {
            if (mediaPlayer == null) {
                mediaPlayer = new MediaPlayer();
            } else {
                mediaPlayer.reset();
            }
            mediaPlayer.setDataSource(audioSource);
            mediaPlayer.setOnPreparedListener(mp -> {
                mp.start();
                btnPlayAudio.setImageResource(android.R.drawable.ic_media_pause);
                updatePlaybackProgress();
            });
            mediaPlayer.setOnCompletionListener(mp -> {
                btnPlayAudio.setImageResource(android.R.drawable.ic_media_play);
                playbackHandler.removeCallbacksAndMessages(null);
                if (mediaPlayer != null) {
                    tvAudioDuration.setText(formatTime(mediaPlayer.getDuration()));
                }
            });
            mediaPlayer.prepareAsync();
        } catch (Exception e) {
            toast(LanguageManager.isEnglish(this) ? "Playback error" : "অডিও চালানো যায়নি");
        }
    }

    private void stopPlayback() {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            btnPlayAudio.setImageResource(android.R.drawable.ic_media_play);
            playbackHandler.removeCallbacksAndMessages(null);
        }
    }

    private void updatePlaybackProgress() {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            tvAudioDuration.setText(formatTime(mediaPlayer.getCurrentPosition()));
            playbackHandler.postDelayed(this::updatePlaybackProgress, 500);
        }
    }

    private String formatTime(int ms) {
        int sec = ms / 1000;
        int m = sec / 60;
        int s = sec % 60;
        return String.format(Locale.getDefault(), "%d:%02d", m, s);
    }

    private void deleteSelectedAudio() {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer = null;
        }
        playbackHandler.removeCallbacksAndMessages(null);
        if (recordedAudioPath != null) {
            File f = new File(recordedAudioPath);
            if (f.exists()) f.delete();
            recordedAudioPath = null;
        }
        existingAudioUrl = null;
        if (llAudioPlayer != null) llAudioPlayer.setVisibility(View.GONE);
        if (tvAudioStatus != null) tvAudioStatus.setText("");
        toast(LanguageManager.isEnglish(this) ? "Audio removed" : "অডিও সরানো হয়েছে");
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
                        Intent data = res.getData();
                        if (data.getClipData() != null) {
                            int count = data.getClipData().getItemCount();
                            for (int i = 0; i < count; i++) {
                                Uri uri = data.getClipData().getItemAt(i).getUri();
                                addImageFromUri(uri);
                            }
                        } else if (data.getData() != null) {
                            addImageFromUri(data.getData());
                        }
                    }
                });
        audioFileLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), res -> {
                    if (res.getResultCode() == RESULT_OK && res.getData() != null)
                        handleSelectedAudioFile(res.getData().getData());
                });
    }

    private void addImageFromUri(Uri uri) {
        try {
            Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
            selectedBitmaps.add(bitmap);
            updateImagePreviews();
        } catch (IOException e) {
            toast(AppStrings.get(this).imageReadFailed());
        }
    }

    private void updateImagePreviews() {
        if (llImagePreviews == null) return;
        llImagePreviews.removeAllViews();

        int total = existingImageUrls.size() + selectedBitmaps.size();
        if (total == 0) {
            if (hsvImagePreview != null) hsvImagePreview.setVisibility(View.GONE);
            if (tvImageStatus != null) tvImageStatus.setText("");
            return;
        }

        if (hsvImagePreview != null) hsvImagePreview.setVisibility(View.VISIBLE);
        if (tvImageStatus != null)
            tvImageStatus.setText(AppStrings.get(this).imageSelected() + " (" + total + ")");

        // Existing image URLs
        for (int i = 0; i < existingImageUrls.size(); i++) {
            final int index = i;
            String url = existingImageUrls.get(i);

            View v = getLayoutInflater().inflate(R.layout.item_image_preview, llImagePreviews, false);
            ImageView iv = v.findViewById(R.id.ivPreview);
            ImageView btnDel = v.findViewById(R.id.btnDelete);

            com.bumptech.glide.Glide.with(this)
                    .load(url)
                    .centerCrop()
                    .into(iv);

            btnDel.setOnClickListener(view -> {
                existingImageUrls.remove(index);
                updateImagePreviews();
            });

            llImagePreviews.addView(v);
        }

        // New bitmaps
        for (int i = 0; i < selectedBitmaps.size(); i++) {
            final int index = i;
            Bitmap bmp = selectedBitmaps.get(i);

            View v = getLayoutInflater().inflate(R.layout.item_image_preview, llImagePreviews, false);
            ImageView iv = v.findViewById(R.id.ivPreview);
            ImageView btnDel = v.findViewById(R.id.btnDelete);

            iv.setImageBitmap(bmp);
            btnDel.setOnClickListener(view -> {
                selectedBitmaps.remove(index);
                updateImagePreviews();
            });

            llImagePreviews.addView(v);
        }
    }

    private void showImageChooser() {
        AppStrings s = AppStrings.get(this);
        new AlertDialog.Builder(this)
                .setTitle(s.imageAddTitle())
                .setItems(new String[]{s.imageGallery()}, (d, w) -> {
                    Intent i = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                    i.setType("image/*");
                    i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                    galleryLauncher.launch(i);
                }).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (audioRecorder != null && isRecording) audioRecorder.cancelRecording();
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        playbackHandler.removeCallbacksAndMessages(null);
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