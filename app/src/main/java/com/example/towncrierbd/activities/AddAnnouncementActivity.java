package com.example.towncrierbd.activities;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
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
import com.example.towncrierbd.models.RequestedItem;
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

    // Step Details bilingual label views (in Step 2)
    private TextView tvLabelTitle, tvLabelDesc, tvLabelExpiry, tvExpiryMax, tvExpiryInfo;
    private TextView tvBtnAddImage, tvBtnAddAudio;
    private ImageView ivBtnAddAudio;
    private AlertDialog recordingDialog;
    private final Handler recordTimerHandler = new Handler(Looper.getMainLooper());
    private Runnable recordTimerRunnable;
    private int recordDurationSec = 0;

    // Step 2 & 3 titles
    private TextView tvStep2Title, tvStep2Subtitle;
    private TextView tvStep3Title, tvStep3Subtitle;

    // Voice Speech Recognition & Auto-Selection views
    private TextView     tvVoiceStatus;
    private LinearLayout llAutoSelectedBanner;
    private TextView     tvAutoSelectedBanner;
    private String       recognizedSpeechText = "";
    private final AudioItemSelection audioItemSelection = new AudioItemSelection();
    private AudioTranscriber.Job transcriptionJob;
    private Spinner audioLanguage;
    private boolean importingAudio, publishing, playbackPrepared;
    private static final long MAX_AUDIO_BYTES = 50L * 1024 * 1024;

    private String postType = "announcement";
    public static final String EXTRA_REQUEST_MODE = "requestMode";
    public static final String EXTRA_SOURCE_ID = "sourceAnnouncementId";
    private String sourceAnnouncementId="", sourceAnnouncerUid="", sourcePostTitle="", pendingNewId;
    private Button sourcePostButton;
    private boolean sourceDialogShowing;
    private String requestMode = RequestMode.ACTIVE_POSTS;
    private RadioGroup requestModeChoices;
    private RadioButton activePostChoice, independentChoice;
    private boolean bindingRequestMode;
    private int requestCatalogGeneration;
    private final Set<String> customRequestItems = new LinkedHashSet<>();
    private Button addCustomRequestItem;
    private final Map<String,Set<String>> availableRequestItems = new LinkedHashMap<>();
    private final Map<String,String> quantities = new LinkedHashMap<>();
    private final Map<String,EditText> quantityInputs = new LinkedHashMap<>();
    private CheckBox showPhone;
    private Spinner contactMethod;
    private LinearLayout requestOptions;
    private Button radiusButton;
    private double requestLat, requestLng, requestRadiusKm;
    private boolean catalogReady, transcribing;
    private volatile int audioGeneration;


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

        requestMode = RequestMode.normalize(getIntent().getStringExtra(EXTRA_REQUEST_MODE));
        sourceAnnouncementId = getIntent().getStringExtra(EXTRA_SOURCE_ID);
        if(sourceAnnouncementId == null)sourceAnnouncementId="";
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

        // Step 2 & 3 titles
        tvStep2Title    = findViewById(R.id.tvStep2Title);
        tvStep2Subtitle = findViewById(R.id.tvStep2Subtitle);
        tvStep3Title    = findViewById(R.id.tvStep3Title);
        tvStep3Subtitle = findViewById(R.id.tvStep3Subtitle);

        // Step 2 (Details & Voice Broadcast) views
        tvLabelTitle   = findViewById(R.id.tvLabelTitle);
        tvLabelDesc    = findViewById(R.id.tvLabelDesc);
        tvLabelExpiry  = findViewById(R.id.tvLabelExpiry);
        tvExpiryMax    = findViewById(R.id.tvExpiryMax);
        tvExpiryInfo   = findViewById(R.id.tvExpiryInfo);
        tvBtnAddImage  = findViewById(R.id.tvBtnAddImage);
        tvBtnAddAudio  = findViewById(R.id.tvBtnAddAudio);
        ivBtnAddAudio  = findViewById(R.id.ivBtnAddAudio);

        // Voice status in Step 2 Audio section
        tvVoiceStatus = findViewById(R.id.tvVoiceStatus);
        setupAudioLanguage();

        // Step 3 Auto-selection banner
        llAutoSelectedBanner = findViewById(R.id.llAutoSelectedBanner);
        tvAutoSelectedBanner = findViewById(R.id.tvAutoSelectedBanner);

        View btnClose = findViewById(R.id.btnClose);
        if (btnClose != null) btnClose.setOnClickListener(v -> { if (!publishing) finish(); });
        if (btnAddImage != null)    btnAddImage.setOnClickListener(v -> showImageChooser());
        if (btnRecordAudio != null) btnRecordAudio.setOnClickListener(v -> showAudioOptions());
        if (btnPublish != null)     btnPublish.setOnClickListener(v -> publish());

        Button btnNext1 = findViewById(R.id.btnNextStep1);
        if (btnNext1 != null) btnNext1.setOnClickListener(v -> goToStep2());
        Button btnBack2 = findViewById(R.id.btnBackStep2);
        if (btnBack2 != null) btnBack2.setOnClickListener(v -> showStep(1));
        Button btnNext2 = findViewById(R.id.btnNextStep2);
        if (btnNext2 != null) btnNext2.setOnClickListener(v -> goToStep3());
        Button btnBack3 = findViewById(R.id.btnBackStep3);
        if (btnBack3 != null) btnBack3.setOnClickListener(v -> showStep(2));

        setupPermissions();
        setupLaunchers();
        applyWizardStrings();
        setupExpiryUI();
        etTitle.setVisibility(View.GONE);
        tvLabelTitle.setVisibility(View.GONE);
        setupRequestOptions();

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

        // ── Step 2 (Details) ──
        if (tvStep2Title    != null) tvStep2Title.setText(s.addStep2Title());
        if (tvStep2Subtitle != null) tvStep2Subtitle.setText(s.addStep2Subtitle());

        Button btnBack2 = stepDetails != null ? stepDetails.findViewById(R.id.btnBackStep2) : null;
        Button btnNext2 = stepDetails != null ? stepDetails.findViewById(R.id.btnNextStep2) : null;
        if (btnBack2 != null) btnBack2.setText(s.addStep2Back());
        if (btnNext2 != null) btnNext2.setText(s.addStep2Next());

        if (tvLabelTitle  != null) tvLabelTitle.setText(s.addStep3LabelTitle());
        if (tvLabelDesc   != null) tvLabelDesc.setText(s.addStep3LabelDesc());
        if (tvLabelExpiry != null) tvLabelExpiry.setText(s.addStep3ExpiryLabel());
        if (tvExpiryMax   != null) tvExpiryMax.setText(s.addStep3ExpiryMax());
        if (tvExpiryInfo  != null) tvExpiryInfo.setText(s.addStep3ExpiryInfo());

        if (etTitle       != null) etTitle.setHint(s.addStep3HintTitle());
        if (etDesc        != null) etDesc.setHint(s.addStep3HintDesc());
        if (etExpiryValue != null) etExpiryValue.setHint(s.addStep3ExpiryHint());

        if (tvBtnAddImage != null) tvBtnAddImage.setText(s.addStep3AddImage());
        if (tvBtnAddAudio != null) tvBtnAddAudio.setText(s.addStep3AddAudio());

        // ── Step 3 (Products & Auto-Selection) ──
        if (tvStep3Title    != null) tvStep3Title.setText(s.addStep3Title());
        if (tvStep3Subtitle != null) tvStep3Subtitle.setText(s.addStep3Subtitle());

        Button btnBack3 = stepProducts != null ? stepProducts.findViewById(R.id.btnBackStep3) : null;
        if (btnBack3 != null) btnBack3.setText(s.addStep3Back());

        if (btnPublish != null) {
            btnPublish.setText(editAnnouncementId != null ? s.addEditWizardUpdateBtn() : s.addStep3Publish());
        }

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
                requestLat=u.getLat(); requestLng=u.getLng();
                requestOptions.setVisibility("request".equals(postType) ? View.VISIBLE : View.GONE);
                updateRequestModeUi();

                userHawkerCategories    = u.getHawkerCategories();
                userHawkerSubcategories = u.getHawkerSubcategories();
                userHawkerOthersName    = u.getHawkerOthersName();

                PostCatalog.load(AddAnnouncementActivity.this, () -> {
                    if (editAnnouncementId != null) loadAnnouncementForEdit(editAnnouncementId);
                    else refreshRequestCatalog();
                });
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
                if (!uidOwns(a)) { toast(p("You can only edit your own post","শুধু নিজের পোস্ট এডিট করতে পারবেন")); finish(); return; }
                if (PostWorkflow.request(a) && !"open".equals(a.getStatus())) { toast(p("This request is being completed","এই রিকোয়েস্টের ডেলিভারি প্রক্রিয়া চলছে")); finish(); return; }
                existingAnnouncement = a;
                showPhone.setChecked(a.isShowPhone());
                contactMethod.setSelection("phone".equals(a.getContactMethod()) ? 1 : "voice".equals(a.getContactMethod()) ? 2 : 0);
                if (a.getRequestedItems()!=null) for(RequestedItem item:a.getRequestedItems()) quantities.put(item.getProduct(),item.getQuantity());

                if (a.getPostType() != null && !a.getPostType().isEmpty()) {
                    postType = a.getPostType();
                }
                requestMode = RequestMode.normalize(a.getRequestMode());
                sourceAnnouncementId=a.getSourceAnnouncementId();
                requestRadiusKm = a.getRequestRadiusKm() > 0 ? a.getRequestRadiusKm() : requestRadiusKm;
                updateRequestModeUi();
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

                if (RequestMode.INDEPENDENT.equals(requestMode) && selectedCategories.contains("Others")) {
                    Set<String> known = new HashSet<>();
                    for (CategoryConfig.HawkerCategory cat : PostCatalog.sorted(AddAnnouncementActivity.this))
                        known.addAll(PostCatalog.items(cat.name));
                    for (String item : selectedProducts) if (!known.contains(item)) customRequestItems.add(item);
                    etCustomCategoryText.setText("");
                }
                refreshRequestCatalog();
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
        if (stepDetails  != null) stepDetails.setVisibility(step == 2 ? View.VISIBLE : View.GONE);
        if (stepProducts != null) stepProducts.setVisibility(step == 3 ? View.VISIBLE : View.GONE);
        if (tvStepIndicator != null) tvStepIndicator.setText(AppStrings.get(this).addStepOf(step, 3));

        View dot1 = findViewById(R.id.indicatorDot1);
        View dot2 = findViewById(R.id.indicatorDot2);
        View dot3 = findViewById(R.id.indicatorDot3);
        if (dot1 != null) dot1.setAlpha(step >= 1 ? 1.0f : 0.35f);
        if (dot2 != null) dot2.setAlpha(step >= 2 ? 1.0f : 0.35f);
        if (dot3 != null) dot3.setAlpha(step >= 3 ? 1.0f : 0.35f);

        if (step != 2) releasePlayback();
        if (step == 2 && tvVoiceStatus != null && tvVoiceStatus.getText().toString().trim().isEmpty())
            showAudioHint();
    }

    private void goToStep2() {
        if(usesAvailableItems() && sourceAnnouncementId.isEmpty()){loadAvailableRequestItems();return;}
        if(!catalogReady){toast(p("Loading available items…","পণ্যের তালিকা লোড হচ্ছে…"));return;}
        enforceSingleCategoryForSelfRequest();
        if (selectedCategories.isEmpty()) {
            toast(AppStrings.get(this).addStep1SelectAtLeast());
            return;
        }
        showStep(2);
    }

    private boolean mediaValid() {
        if("request".equals(postType))return true;
        boolean image=!selectedBitmaps.isEmpty()||!existingImageUrls.isEmpty();
        boolean audio=(recordedAudioPath!=null&&!recordedAudioPath.isEmpty())||(existingAudioUrl!=null&&!existingAudioUrl.isEmpty());
        if(image&&audio)return true;
        toast(p("Announcer posts require an image and a voice recording or audio file.","ঘোষকের পোস্টে ছবি এবং রেকর্ড করা ভয়েস বা অডিও ফাইল দুটিই লাগবে।"));return false;
    }
    private void goToStep3() {
        if(!mediaValid())return;
        AppStrings s = AppStrings.get(this);
        if (isRecording || importingAudio || transcribing) {
            toast(p("Finish recording/loading and wait for audio recognition", "রেকর্ড/লোড শেষ করে অডিও শনাক্ত হওয়া পর্যন্ত অপেক্ষা করুন"));
            return;
        }
        if (!catalogReady) { toast(p("Loading items…", "পণ্য লোড হচ্ছে…")); return; }

        long expiryMillis = getExpiryMillis();
        if (expiryMillis == -1) {
            toast(s.addStep3InvalidExpiry());
            if (etExpiryValue != null) etExpiryValue.requestFocus();
            return;
        }
        if (expiryMillis == -2) {
            toast(s.addStep3ExpiryOver());
            if (etExpiryValue != null) etExpiryValue.requestFocus();
            return;
        }

        String desc = etDesc.getText().toString().trim();
        VoiceKeywordMatcher.MatchResult descriptionMatches = detectItems(desc);
        if (isSingleCategorySelfRequest()) {
            if (selectedCategories.isEmpty() && !descriptionMatches.matchedCategories.isEmpty()) {
                selectedCategories.add(descriptionMatches.matchedCategories.iterator().next());
            }
            for (String item : descriptionMatches.matchedProducts) {
                selectedProducts.add(item);
                audioItemSelection.keepProduct(item);
            }
            enforceSingleCategoryForSelfRequest();
        } else {
            selectedCategories.addAll(descriptionMatches.matchedCategories);
            selectedProducts.addAll(descriptionMatches.matchedProducts);
            for (String category : descriptionMatches.matchedCategories) audioItemSelection.keepCategory(category);
            for (String item : descriptionMatches.matchedProducts) audioItemSelection.keepProduct(item);
        }
        retainAvailableItems();
        updateAutoSelectedBanner();

        buildProductStep();
        showStep(3);
    }

    private boolean isSingleCategorySelfRequest() {
        return "request".equals(postType) && RequestMode.INDEPENDENT.equals(requestMode);
    }

    private void enforceSingleCategoryForSelfRequest() {
        if (!isSingleCategorySelfRequest() || selectedCategories.size() <= 1) return;
        String first = selectedCategories.iterator().next();
        selectedCategories.clear();
        selectedCategories.add(first);
        Set<String> allowed = new HashSet<>(PostCatalog.items(first));
        if ("Others".equals(first)) allowed.addAll(customRequestItems);
        selectedProducts.retainAll(allowed);
    }

    private void setupAudioLanguage() {
        LinearLayout container = (LinearLayout) tvVoiceStatus.getParent();
        TextView label = new TextView(this);
        label.setText(p("Audio language", "অডিওর ভাষা"));
        label.setTextColor(0xFF1F2937);
        label.setPadding(0, dp(12), 0, dp(4));
        audioLanguage = new Spinner(this);
        audioLanguage.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"বাংলা", "English"}) {
            @Override public View getView(int position, View convertView, ViewGroup parent) {
                TextView text = (TextView) super.getView(position, convertView, parent);
                text.setTextColor(0xFF1F2937);
                return text;
            }
            @Override public View getDropDownView(int position, View convertView, ViewGroup parent) {
                TextView text = (TextView) super.getDropDownView(position, convertView, parent);
                text.setTextColor(0xFF1F2937);
                text.setBackgroundColor(0xFFFFFFFF);
                return text;
            }
        });
        audioLanguage.setSelection(0); // Bangladesh: voice language is independent of interface language.
        int index = container.indexOfChild(tvVoiceStatus);
        container.addView(label, index);
        container.addView(audioLanguage, index + 1, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
        audioLanguage.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (recordedAudioPath != null && !isRecording && !importingAudio && !publishing)
                    transcribeFile(new File(recordedAudioPath), selectedAudioLanguage());
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        tvVoiceStatus.setOnClickListener(v -> {
            if (recordedAudioPath != null && !isRecording && !importingAudio && !transcribing && !publishing)
                transcribeFile(new File(recordedAudioPath), selectedAudioLanguage());
        });
        showAudioHint();
    }

    private String selectedAudioLanguage() {
        return audioLanguage.getSelectedItemPosition() == 1 ? "en-US" : "bn-BD";
    }

    private void showAudioHint() {
        tvVoiceStatus.setVisibility(View.VISIBLE);
        tvVoiceStatus.setText(p("Record or choose audio. Items are recognized on this phone, offline, then selected in the next step.",
                "অডিও রেকর্ড করুন বা ফাইল বাছুন। ফোনেই অফলাইনে কথা শনাক্ত করে পরের ধাপে পণ্য নির্বাচন হবে।"));
    }

    private VoiceKeywordMatcher.MatchResult detectItems(String text) {
        VoiceKeywordMatcher.MatchResult result = VoiceKeywordMatcher.match(text);
        for (CategoryConfig.HawkerCategory category : PostCatalog.sorted(this)) {
            for (CategoryConfig.SubGroup group : category.subGroups) {
                for (int i = 0; i < group.items.size(); i++) {
                    String item = group.items.get(i);
                    String bangla = i < group.itemsBn.size() ? group.itemsBn.get(i) : item;
                    if (VoiceKeywordMatcher.containsKeyword(text, item) || VoiceKeywordMatcher.containsKeyword(text, bangla)) {
                        result.matchedCategories.add(category.name);
                        result.matchedProducts.add(item);
                    }
                }
            }
        }
        for (Map.Entry<String, Set<String>> entry : availableRequestItems.entrySet()) {
            if (!usesAvailableItems()) break;
            for (String item : entry.getValue()) if (VoiceKeywordMatcher.containsKeyword(text, item)) {
                result.matchedCategories.add(entry.getKey());
                result.matchedProducts.add(item);
            }
        }
        return result;
    }

    private void onSpeechRecognized(String heard) {
        recognizedSpeechText = heard == null ? "" : heard.trim();
        VoiceKeywordMatcher.MatchResult result = detectItems(recognizedSpeechText);
        audioItemSelection.apply(selectedCategories, selectedProducts, result.matchedCategories, result.matchedProducts);
        enforceSingleCategoryForSelfRequest();
        retainAvailableItems();
        List<String> detected = new ArrayList<>();
        for (String item : result.matchedProducts) if (selectedProducts.contains(item))
            detected.add(PostCatalog.itemLabel(this, item));
        tvVoiceStatus.setText(p("Heard: ", "শনাক্তকৃত কথা: ") + recognizedSpeechText + "\n"
                + (detected.isEmpty() ? p("No matching items. Select items manually or change the audio language.",
                "মিলে যায় এমন পণ্য পাওয়া যায়নি। নিজে পণ্য নির্বাচন করুন বা অডিওর ভাষা বদলান।")
                : p("Selected: ", "নির্বাচিত: ") + TextUtils.join(", ", detected)));
        updateAutoSelectedBanner();
        buildCategoryStep();
    }

    private void updateAutoSelectedBanner() {
        List<String> labels = new ArrayList<>();
        for (String item : detectItems(recognizedSpeechText).matchedProducts)
            if (selectedProducts.contains(item)) labels.add(PostCatalog.itemLabel(this, item));
        llAutoSelectedBanner.setVisibility(labels.isEmpty() ? View.GONE : View.VISIBLE);
        tvAutoSelectedBanner.setText(AppStrings.get(this).addStep3AutoSelectedBanner(labels.size(), TextUtils.join(", ", labels)));
    }

    private void cancelAudioAnalysis() {
        ++audioGeneration;
        if (transcriptionJob != null) transcriptionJob.cancel();
        transcriptionJob = null;
        transcribing = false;
    }

    private void clearAudioChoices() {
        cancelAudioAnalysis();
        recognizedSpeechText = "";
        audioItemSelection.clear(selectedCategories, selectedProducts);
        llAutoSelectedBanner.setVisibility(View.GONE);
    }

    // ════════════════════════════════════════════════════════════════════════
    // STEP 1 — Category selection
    // ════════════════════════════════════════════════════════════════════════
    private void buildCategoryStep() {
        if (llCategoryList == null) return;
        llCategoryList.removeAllViews();
        enforceSingleCategoryForSelfRequest();
        AppStrings s = AppStrings.get(this);
        boolean isBn = !LanguageManager.isEnglish(this);

        if (isSingleCategorySelfRequest()) {
            TextView singleHint = new TextView(this);
            singleHint.setText(p(
                    "ℹ️ Select 1 category at a time (you can choose multiple items inside)",
                    "ℹ️ একবারে একটি ক্যাটাগরি নির্বাচন করুন (ভেতরে একাধিক পণ্য নিতে পারবেন)"));
            singleHint.setTextSize(12);
            singleHint.setTextColor(0xFF1565C0);
            singleHint.setPadding(dp(4), dp(4), dp(4), dp(8));
            llCategoryList.addView(singleHint);
        }

        List<CategoryConfig.HawkerCategory> categoriesToShow = new ArrayList<>();
        for(CategoryConfig.HawkerCategory cat : PostCatalog.sorted(this)) {
            if(usesAvailableItems() && !availableRequestItems.containsKey(cat.name)) continue;
            if("announcement".equals(postType) && !userHawkerCategories.isEmpty()
                    && !userHawkerCategories.contains(cat.name) && !selectedCategories.contains(cat.name)) continue;
            categoriesToShow.add(cat);
        }
        if(usesAvailableItems() && categoriesToShow.isEmpty()) {
            TextView empty=new TextView(this);
            empty.setText(p("No active seller items here. Increase the radius or choose Create my own request.","এই দূরত্বে সক্রিয় পণ্য নেই। দূরত্ব বাড়ান অথবা নিজের রিকোয়েস্ট তৈরি করুন।"));
            llCategoryList.addView(empty);
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
                audioItemSelection.keepCategory(cat.name);
                if (checked) {
                    if (isSingleCategorySelfRequest() && !selectedCategories.isEmpty() && !selectedCategories.contains(cat.name)) {
                        selectedCategories.clear();
                        selectedProducts.clear();
                        selectedCategories.add(cat.name);
                        buildCategoryStep();
                        return;
                    }
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

        if("announcement".equals(postType)) {
            Button add=new Button(this);add.setText(p("+ Add category","+ ক্যাটাগরি যোগ করুন"));
            add.setOnClickListener(v->PostCatalog.addDialog(this,name->{
                selectedCategories.add(name);
                if(!userHawkerCategories.contains(name))userHawkerCategories.add(name);
                buildCategoryStep();
                String uid=auth.getUid();if(uid!=null)userRef.child(uid).child("hawkerCategories").setValue(new ArrayList<>(userHawkerCategories))
                    .addOnFailureListener(e->toast(e.getMessage()));
            }));llCategoryList.addView(add);
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
        for(Map.Entry<String,EditText> entry:quantityInputs.entrySet()) quantities.put(entry.getKey(),entry.getValue().getText().toString());
        quantityInputs.clear(); llProductList.removeAllViews(); productCheckBoxes.clear();
        boolean request="request".equals(postType);
        boolean customRequest = request && !usesAvailableItems() && selectedCategories.contains("Others");
        etCustomCategoryText.setVisibility((!request || customRequest) && selectedCategories.contains("Others") ? View.VISIBLE : View.GONE);
        etCustomCategoryText.setHint(customRequest ? p("Item name", "পণ্যের নাম") : AppStrings.get(this).addStep2CustomHint());
        addCustomRequestItem.setVisibility(customRequest ? View.VISIBLE : View.GONE);
        for(CategoryConfig.HawkerCategory cat:PostCatalog.sorted(this)) {
            if(!selectedCategories.contains(cat.name)) continue;
            List<String> items = usesAvailableItems() ? new ArrayList<>(availableRequestItems.getOrDefault(cat.name,Collections.emptySet())) : PostCatalog.items(cat.name);
            if (customRequest && "Others".equals(cat.name)) items.addAll(customRequestItems);
            if(items.isEmpty()) continue;
            items.sort(String.CASE_INSENSITIVE_ORDER);
            TextView heading=new TextView(this); heading.setText(cat.emoji+" "+PostCatalog.label(this,cat.name)); heading.setTextSize(15); heading.setTypeface(null,Typeface.BOLD); llProductList.addView(heading);
            TextView all=new TextView(this); all.setText(p("Select / clear all","সব নির্বাচন / বাদ দিন")); all.setTextColor(0xFF1976F3); all.setPadding(0,dp(8),0,dp(8)); llProductList.addView(all);
            all.setOnClickListener(v -> {
                audioItemSelection.keepCategory(cat.name);
                for (String item : items) audioItemSelection.keepProduct(item);
                if (selectedProducts.containsAll(items)) selectedProducts.removeAll(items); else selectedProducts.addAll(items);
                buildProductStep();
            });
            for(String item:items) {
                LinearLayout row=new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL);
                CheckBox cb=new CheckBox(this); cb.setText(PostCatalog.itemLabel(this,item)); cb.setTag(item); cb.setChecked(selectedProducts.contains(item));
                row.addView(cb,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1)); productCheckBoxes.add(cb);
                EditText quantity=new EditText(this);
                if(request) {
                    quantity.setText(quantities.getOrDefault(item,"")); quantity.setHint(p("e.g. 2 kg","যেমন: ২ কেজি")); quantity.setSingleLine(true); quantity.setTextSize(13); quantity.setEnabled(cb.isChecked());
                    row.addView(quantity,new LinearLayout.LayoutParams(dp(130),ViewGroup.LayoutParams.WRAP_CONTENT)); quantityInputs.put(item,quantity);
                }
                cb.setOnCheckedChangeListener((v, checked) -> {
                    audioItemSelection.keepProduct(item);
                    if (checked) { selectedProducts.add(item); audioItemSelection.keepCategory(cat.name); }
                    else selectedProducts.remove(item);
                    quantity.setEnabled(checked);
                });
                llProductList.addView(row);
            }
        }
        if(llProductList.getChildCount()==0) { TextView empty=new TextView(this);empty.setText(AppStrings.get(this).addStep2NoProducts());llProductList.addView(empty); }
    }

    // ════════════════════════════════════════════════════════════════════════
    // Publish
    // ════════════════════════════════════════════════════════════════════════
    private void publish() {
        AppStrings s = AppStrings.get(this);
        if (publishing) return;
        if (isRecording) { toast(s.addStopRecordFirst()); return; }
        if (importingAudio || !catalogReady) { toast(s.pleaseWait()); return; }

        enforceSingleCategoryForSelfRequest();
        if (selectedCategories.isEmpty()) {
            toast(s.addStep1SelectAtLeast());
            showStep(1);
            return;
        }

        String desc  = etDesc.getText().toString().trim();

        if (!mediaValid()) { showStep(2); return; }
        if (usesAvailableItems() && sourceAnnouncementId.isEmpty()) { showStep(1); loadAvailableRequestItems(); return; }
        if (transcribing) { toast(p("Please wait for audio recognition","অডিও শনাক্ত হওয়া পর্যন্ত অপেক্ষা করুন")); return; }
        if ("request".equals(postType)) {
            retainAvailableItems();
            for(String item:selectedProducts) {
                EditText input=quantityInputs.get(item);
                if(input!=null) quantities.put(item,input.getText().toString().trim());
                if(quantities.getOrDefault(item,"").isEmpty()) {
                    if(input!=null) input.setError(p("Enter quantity and unit","পরিমাণ ও একক লিখুন"));
                    toast(p("Enter quantity for every item","প্রতিটি পণ্যের পরিমাণ লিখুন")); return;
                }
            }
        }

        final String title=generatedTitle();
        long expiryMillis = getExpiryMillis();
        if (expiryMillis == -1) {
            toast(s.addPublishNoExpiry());
            showStep(2);
            if (etExpiryValue != null) etExpiryValue.requestFocus();
            return;
        }
        if (expiryMillis == -2) {
            toast(s.addPublishExpiryOver());
            showStep(2);
            if (etExpiryValue != null) etExpiryValue.requestFocus();
            return;
        }

        if (selectedProducts.isEmpty() && ("request".equals(postType) || !selectedCategories.contains("Others"))) {
            toast(s.addStep2SelectAtLeast());
            showStep(3);
            return;
        }

        String uid = auth.getUid();
        if (uid == null) return;

        publishing = true;
        btnPublish.setEnabled(false);
        audioLanguage.setEnabled(false);
        if (tvImageStatus != null) tvImageStatus.setText(s.pleaseWait());

        userRef.child(uid).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                UserModel u = snapshot.getValue(UserModel.class);
                if (u == null) { reset(); toast(s.addPublishUserNotFound()); return; }
                if("request".equals(postType) && showPhone.isChecked() && (u.getPhone()==null || u.getPhone().trim().isEmpty())){
                    reset();toast(p("Add a phone number to your profile first","আগে প্রোফাইলে ফোন নম্বর যোগ করুন"));return;
                }
                if (u.getLat() == 0.0 && u.getLng() == 0.0) {
                    reset(); toast(s.addPublishNoLoc()); return;
                }

                if(pendingNewId==null)pendingNewId=annRef.push().getKey();
                String id = (editAnnouncementId != null) ? editAnnouncementId : pendingNewId;
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
                a.setRequestMode(requestMode);
                a.setSourceAnnouncementId(usesAvailableItems()?sourceAnnouncementId:"");
                a.setSourceAnnouncerUid(usesAvailableItems()?sourceAnnouncerUid:"");
                a.setSourcePostTitle(usesAvailableItems()?sourcePostTitle:"");
                a.setPhone(!"request".equals(postType) || showPhone.isChecked() ? u.getPhone() : "");
                a.setShowPhone(!"request".equals(postType) || showPhone.isChecked());
                a.setContactMethod(new String[]{"chat","phone","voice"}[contactMethod.getSelectedItemPosition()]);
                a.setRequestRadiusKm(requestRadiusKm);
                List<RequestedItem> requested=new ArrayList<>();
                if("request".equals(postType)) for(String item:selectedProducts) requested.add(new RequestedItem(item,quantities.get(item)));
                a.setRequestedItems(requested);

                String primaryCat = selectedCategories.isEmpty()
                        ? "" : selectedCategories.iterator().next();
                a.setCategory(primaryCat);
                a.setSelectedCategories(new ArrayList<>(selectedCategories));
                a.setSelectedSubcategories(new ArrayList<>());
                a.setSubcategory("");
                a.setSelectedProducts(new ArrayList<>(selectedProducts));

                if ("request".equals(postType) && !usesAvailableItems() && selectedCategories.contains("Others")) {
                    List<String> chosenCustom = new ArrayList<>(customRequestItems);
                    chosenCustom.retainAll(selectedProducts);
                    a.setCustomSubcategory(TextUtils.join(", ", chosenCustom));
                } else if (etCustomCategoryText != null
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
                a.setOriginalExpireAt(now + expiryMillis);

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
                runOnUiThread(()->{reset();toast(p("Image upload failed. Your images are kept; retry Publish.","ছবি আপলোড হয়নি। ছবি রাখা আছে; আবার প্রকাশ করুন।"));});
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
                            runOnUiThread(() -> {
                                reset();
                                toast(p("Audio upload failed. Your audio is kept; tap Publish to retry.",
                                        "অডিও আপলোড হয়নি। অডিও রাখা আছে; আবার প্রকাশ করুন।"));
                            });
                        }
                    }, true);
        } else if (existingAudioUrl != null && !existingAudioUrl.isEmpty()) {
            a.setAudioUrl(existingAudioUrl);
            saveAnnouncement(a);
        } else {
            a.setAudioUrl("");
            saveAnnouncement(a);
        }
    }

    private void saveAnnouncement(Announcement a) {
        AppStrings strings=AppStrings.get(this);
        if(tvImageStatus!=null)tvImageStatus.setText(strings.addPublishing());
        if("request".equals(postType)) {
            a.setPostType("request");
            a.setRequestMode(requestMode);
            if (a.getStatus() == null || a.getStatus().isEmpty()) a.setStatus("open");
            a.setActive(true);
            if (usesAvailableItems() && sourceAnnouncementId != null) {
                a.setSourceAnnouncementId(sourceAnnouncementId);
            }
            if(editAnnouncementId!=null){
                annRef.child(a.getId()).runTransaction(new Transaction.Handler(){
                    @NonNull @Override public Transaction.Result doTransaction(@NonNull MutableData current){
                        Announcement latest=current.getValue(Announcement.class);
                        if(latest==null)return Transaction.success(current);
                        if(!uidOwns(latest) || (PostWorkflow.request(latest) && !"open".equals(latest.getStatus())))return Transaction.abort();
                        current.setValue(a);return Transaction.success(current);
                    }
                    @Override public void onComplete(DatabaseError error,boolean committed,DataSnapshot result){
                        if(error==null && committed && result!=null && result.exists()){
                            publishing = false; toast(strings.addEditWizardSuccess());finish();
                        }else{reset();toast(error!=null?error.getMessage():p("Request changed. Reopen it before editing.","রিকোয়েস্টের অবস্থা বদলেছে। আবার খুলুন।"));}
                    }
                });
            } else {
                annRef.child(a.getId()).setValue(a).addOnSuccessListener(v->{
                    publishing = false;
                    toast(strings.addPublishSuccess(postType));
                    if (a.getSourceAnnouncerUid() != null && !a.getSourceAnnouncerUid().trim().isEmpty()) {
                        com.example.towncrierbd.utils.NotificationSender.sendRequestEventNotification(
                                a.getSourceAnnouncerUid().trim(),
                                a.getId(),
                                "open",
                                p("New request", "নতুন রিকোয়েস্ট"),
                                a.getTitle() != null ? a.getTitle() : "New request on your post"
                        );
                    } else {
                        com.example.towncrierbd.utils.NotificationSender.sendSelfRequestNotification(a);
                    }
                    finish();
                }).addOnFailureListener(e->{
                    reset();
                    toast(strings.addPublishFailed(e.getMessage()));
                });
            }
            return;
        }
        if(editAnnouncementId!=null){
            annRef.child(a.getId()).runTransaction(new Transaction.Handler(){
                @NonNull @Override public Transaction.Result doTransaction(@NonNull MutableData current){
                    Announcement latest=current.getValue(Announcement.class);
                    if(latest==null)return Transaction.success(current);
                    if(!uidOwns(latest) || (PostWorkflow.request(latest) && !"open".equals(latest.getStatus())))return Transaction.abort();
                    current.setValue(a);return Transaction.success(current);
                }
                @Override public void onComplete(DatabaseError error,boolean committed,DataSnapshot result){
                    if(error==null && committed && result!=null && result.exists()){
                        publishing = false; toast(strings.addEditWizardSuccess());finish();
                    }else{reset();toast(error!=null?error.getMessage():p("Request changed. Reopen it before editing.","রিকোয়েস্টের অবস্থা বদলেছে। আবার খুলুন।"));}
                }
            });
        }else{
            annRef.child(a.getId()).setValue(a).addOnSuccessListener(v->{
                publishing = false; 
                toast(strings.addPublishSuccess(postType));
                if ("announcement".equals(postType) && a.isActive()) {
                    com.example.towncrierbd.utils.NotificationSender.sendAnnouncementNotification(a);
                }
                finish();
            })
                .addOnFailureListener(e->{reset();toast(strings.addPublishFailed(e.getMessage()));});
        }
    }

    private void reset() {
        publishing = false;
        audioLanguage.setEnabled(true);
        if (btnPublish != null) btnPublish.setEnabled(true);
        if (tvImageStatus != null) tvImageStatus.setText("");
    }

    // ════════════════════════════════════════════════════════════════════════
    // Audio / Image helpers
    // ════════════════════════════════════════════════════════════════════════
    private void showAudioOptions() {
        if (publishing || importingAudio) { toast(AppStrings.get(this).pleaseWait()); return; }
        if (isRecording) { stopAudioRecording(); return; }
        AppStrings s = AppStrings.get(this);
        new AlertDialog.Builder(this)
                .setTitle(s.audioAddTitle())
                .setItems(new String[]{s.audioOptionRecord(), s.audioOptionFile()}, (dialog, which) -> {
                    releasePlayback();
                    if (which == 0) startAudioRecording(); else openAudioFilePicker();
                })
                .setNegativeButton(s.cancel(), null).show();
    }

    private void openAudioFilePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("audio/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        try { audioFileLauncher.launch(intent); }
        catch (android.content.ActivityNotFoundException e) {
            toast(p("No audio file picker is installed", "অডিও ফাইল বেছে নেওয়ার অ্যাপ পাওয়া যায়নি"));
        }
    }

    private void handleSelectedAudioFile(Uri uri) {
        if (uri == null || publishing) return;
        cancelAudioAnalysis();
        final int generation = audioGeneration;
        importingAudio = true;
        tvVoiceStatus.setText(p("Loading audio…", "অডিও লোড হচ্ছে…"));
        new Thread(() -> {
            File output = null;
            try {
                String displayName = "";
                try (Cursor cursor = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                    if (cursor != null && cursor.moveToFirst()) displayName = cursor.getString(0);
                } catch (Exception ignored) {}
                String extension = ".m4a";
                if (displayName != null && displayName.matches("(?i).*\\.(mp3|m4a|mp4|wav|ogg|aac|3gp|flac|opus|amr)"))
                    extension = displayName.substring(displayName.lastIndexOf('.')).toLowerCase(Locale.ROOT);
                output = File.createTempFile("tc_audio_selected_", extension, getCacheDir());
                long count = 0;
                try (InputStream input = getContentResolver().openInputStream(uri);
                     OutputStream out = new FileOutputStream(output)) {
                    if (input == null) throw new IOException("Audio cannot be read");
                    byte[] buffer = new byte[8192];
                    int size;
                    while ((size = input.read(buffer)) != -1) {
                        if (generation != audioGeneration) throw new IOException("Cancelled");
                        count += size;
                        if (count > MAX_AUDIO_BYTES) throw new IOException("Choose audio smaller than 50 MB");
                        out.write(buffer, 0, size);
                    }
                }
                MediaMetadataRetriever metadata = new MediaMetadataRetriever();
                try {
                    metadata.setDataSource(output.getAbsolutePath());
                    String duration = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                    if (count == 0 || duration == null || Long.parseLong(duration) <= 0)
                        throw new IOException("Choose a playable audio file");
                } finally { metadata.release(); }
                final File selected = output;
                final String name = displayName == null ? "" : displayName;
                runOnUiThread(() -> {
                    if (generation != audioGeneration || isFinishing() || isDestroyed()) { selected.delete(); return; }
                    importingAudio = false;
                    attachAudio(selected.getAbsolutePath(), AppStrings.get(this).audioFileSelected() + (name.isEmpty() ? "" : " (" + name + ")"));
                });
            } catch (Exception error) {
                if (output != null) output.delete();
                runOnUiThread(() -> {
                    if (generation != audioGeneration || isFinishing() || isDestroyed()) return;
                    importingAudio = false;
                    showAudioHint();
                    toast(AppStrings.get(this).audioLoadFailed() + ": " + error.getMessage());
                    if (recordedAudioPath != null && recognizedSpeechText.isEmpty())
                        transcribeFile(new File(recordedAudioPath), selectedAudioLanguage());
                });
            }
        }, "audio-file-import").start();
    }

    private void attachAudio(String path, String label) {
        releasePlayback();
        clearAudioChoices();
        if (recordedAudioPath != null && !recordedAudioPath.equals(path)) new File(recordedAudioPath).delete();
        recordedAudioPath = path;
        existingAudioUrl = null;
        tvAudioStatus.setText(label);
        showAudioPlayer();
        transcribeFile(new File(path), selectedAudioLanguage());
    }

    private boolean uidOwns(Announcement a) { return auth.getUid()!=null && auth.getUid().equals(a.getUserId()); }
    private String generatedTitle() {
        String title=TextUtils.join(", ",selectedProducts.isEmpty()?selectedCategories:selectedProducts);
        return title.length()>100?title.substring(0,100):title;
    }
    private void setupRequestOptions() {
        requestRadiusKm=RadiusManager.getRadius(this);
        radiusButton=new Button(this); radiusButton.setText(p("Request radius: ","রিকোয়েস্টের দূরত্ব: ")+(int)(requestRadiusKm*1000)+" m");
        LinearLayout categoryContainer = (LinearLayout) llCategoryList.getParent();
        requestModeChoices = new RadioGroup(this);
        requestModeChoices.setOrientation(LinearLayout.VERTICAL);
        requestModeChoices.setPadding(dp(8), dp(8), dp(8), dp(8));
        requestModeChoices.setBackground(roundedBg(0xFFFFFFFF, dp(12)));
        activePostChoice = new RadioButton(this);
        activePostChoice.setId(View.generateViewId());
        activePostChoice.setText(p("Request from active posts", "সক্রিয় পোস্ট থেকে রিকোয়েস্ট"));
        independentChoice = new RadioButton(this);
        independentChoice.setId(View.generateViewId());
        independentChoice.setText(p("Create my own request", "নিজের রিকোয়েস্ট পোস্ট তৈরি"));
        activePostChoice.setTextColor(0xFF1F2937);
        independentChoice.setTextColor(0xFF1F2937);
        activePostChoice.setButtonTintList(ColorStateList.valueOf(0xFF1976F3));
        independentChoice.setButtonTintList(ColorStateList.valueOf(0xFF1976F3));
        requestModeChoices.addView(activePostChoice);
        requestModeChoices.addView(independentChoice);
        categoryContainer.addView(requestModeChoices, 2);
        categoryContainer.addView(radiusButton, 3);
        sourcePostButton=new Button(this);sourcePostButton.setAllCaps(false);sourcePostButton.setSingleLine(false);
        sourcePostButton.setText(p("Choose active seller post","বিক্রেতার সক্রিয় পোস্ট বাছুন"));
        categoryContainer.addView(sourcePostButton,4);
        sourcePostButton.setOnClickListener(v->{sourceAnnouncementId="";loadAvailableRequestItems();});
        requestModeChoices.setVisibility(View.GONE);
        requestModeChoices.setOnCheckedChangeListener((group, checkedId) -> {
            if (bindingRequestMode) return;
            requestMode = checkedId == independentChoice.getId() ? RequestMode.INDEPENDENT : RequestMode.ACTIVE_POSTS;
            refreshRequestCatalog();
        });
        addCustomRequestItem = new Button(this);
        addCustomRequestItem.setText(p("+ Add item", "+ পণ্য যোগ করুন"));
        LinearLayout productContainer = (LinearLayout) etCustomCategoryText.getParent();
        productContainer.addView(addCustomRequestItem, productContainer.indexOfChild(etCustomCategoryText) + 1);
        addCustomRequestItem.setVisibility(View.GONE);
        addCustomRequestItem.setOnClickListener(v -> {
            String item = etCustomCategoryText.getText().toString().trim();
            if (item.isEmpty() || item.length() > 80) {
                etCustomCategoryText.setError(p("Enter an item name (1–80 characters)", "১–৮০ অক্ষরের পণ্যের নাম লিখুন"));
                return;
            }
            customRequestItems.add(item);
            selectedProducts.add(item);
            selectedCategories.add("Others");
            audioItemSelection.keepProduct(item);
            audioItemSelection.keepCategory("Others");
            etCustomCategoryText.setText("");
            buildProductStep();
        });
        radiusButton.setOnClickListener(v->new AlertDialog.Builder(this).setTitle(p("Select radius","দূরত্ব নির্বাচন করুন"))
            .setItems(new String[]{"100 m","500 m","1 km","2 km","3 km"},(d,w)->{
                requestRadiusKm=new double[]{0.1,0.5,1,2,3}[w];RadiusManager.setRadius(this,requestRadiusKm);
                radiusButton.setText(p("Request radius: ","রিকোয়েস্টের দূরত্ব: ")+(int)(requestRadiusKm*1000)+" m");refreshRequestCatalog();
            }).show());
        requestOptions=new LinearLayout(this); requestOptions.setOrientation(LinearLayout.VERTICAL);
        showPhone=new CheckBox(this);showPhone.setText(p("Show my phone number","আমার ফোন নম্বর দেখান"));requestOptions.addView(showPhone);
        TextView label=new TextView(this);label.setText(p("Receive offers through","অফার গ্রহণের মাধ্যম"));requestOptions.addView(label);
        contactMethod=new Spinner(this);contactMethod.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,
            new String[]{p("Chat (default)","চ্যাট (ডিফল্ট)"),p("Phone","ফোন"),p("Voice offer","ভয়েস অফার")}));requestOptions.addView(contactMethod);
        contactMethod.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            @Override public void onItemSelected(AdapterView<?> a,View v,int position,long id){if(position==1)showPhone.setChecked(true);}
            @Override public void onNothingSelected(AdapterView<?> a){}
        });
        showPhone.setOnCheckedChangeListener((v,checked)->{if(!checked && contactMethod.getSelectedItemPosition()==1)contactMethod.setSelection(0);});
        LinearLayout details=(LinearLayout)etDesc.getParent();details.addView(requestOptions,details.indexOfChild(etDesc)+1);
        requestOptions.setVisibility(View.GONE);radiusButton.setVisibility(View.GONE);
    }
    private boolean usesAvailableItems() {
        return RequestMode.usesAvailableItems(postType, requestMode);
    }

    private void updateRequestModeUi() {
        boolean request = "request".equals(postType);
        requestModeChoices.setVisibility(request ? View.VISIBLE : View.GONE);
        sourcePostButton.setVisibility(usesAvailableItems() ? View.VISIBLE : View.GONE);
        if(tvStep2Subtitle!=null)tvStep2Subtitle.setText(request?p("Image and voice are optional","ছবি ও ভয়েস ঐচ্ছিক"):p("Add an image and voice (both required)","ছবি ও ভয়েস যোগ করুন (দুটিই বাধ্যতামূলক)"));
        requestOptions.setVisibility(request ? View.VISIBLE : View.GONE);
        radiusButton.setVisibility(request ? View.VISIBLE : View.GONE);
        radiusButton.setText(p("Request radius: ", "রিকোয়েস্টের দূরত্ব: ") + (int)(requestRadiusKm * 1000) + " m");
        bindingRequestMode = true;
        requestModeChoices.check(RequestMode.INDEPENDENT.equals(requestMode) ? independentChoice.getId() : activePostChoice.getId());
        bindingRequestMode = false;
    }

    private void refreshRequestCatalog() {
        ++requestCatalogGeneration;
        updateRequestModeUi();
        if (usesAvailableItems()) loadAvailableRequestItems();
        else {
            catalogReady = true;
            buildCategoryStep();
        }
    }

    private void loadAvailableRequestItems() {
        final int generation=++requestCatalogGeneration;catalogReady=false;
        if(sourceAnnouncementId != null && !sourceAnnouncementId.isEmpty()) {
            annRef.child(sourceAnnouncementId).get().addOnSuccessListener(snapshot -> {
                if(generation != requestCatalogGeneration || !usesAvailableItems() || isFinishing() || isDestroyed()) return;
                Announcement a = snapshot.getValue(Announcement.class);
                if(a != null && !PostWorkflow.request(a)) {
                    a.setId(snapshot.getKey());
                    applySourcePost(a);
                    return;
                }
                loadAvailableRequestItemsFromList(generation);
            }).addOnFailureListener(e -> loadAvailableRequestItemsFromList(generation));
            return;
        }
        loadAvailableRequestItemsFromList(generation);
    }

    private void loadAvailableRequestItemsFromList(final int generation) {
        annRef.addListenerForSingleValueEvent(new ValueEventListener(){
            @Override public void onDataChange(@NonNull DataSnapshot data) {
                if(generation!=requestCatalogGeneration||!usesAvailableItems()||isFinishing()||isDestroyed())return;
                List<Announcement> candidates=new ArrayList<>();Announcement chosen=null;
                for(DataSnapshot child:data.getChildren()) {
                    Announcement a=child.getValue(Announcement.class);
                    if(a==null||PostWorkflow.request(a)||!PostWorkflow.live(a))continue;
                    a.setId(child.getKey());
                    if(sourceAnnouncementId!=null && sourceAnnouncementId.equals(a.getId()))chosen=a;
                    boolean locValid = requestLat != 0.0 || requestLng != 0.0;
                    if(!locValid || DistanceUtil.distanceKm(requestLat,requestLng,a.getLat(),a.getLng())<=requestRadiusKm)candidates.add(a);
                }
                if(chosen!=null){applySourcePost(chosen);return;}
                availableRequestItems.clear();catalogReady=true;retainAvailableItems();buildCategoryStep();
                sourceAnnouncementId="";sourcePostButton.setText(p("Choose active seller post","বিক্রেতার সক্রিয় পোস্ট বাছুন"));
                if(candidates.isEmpty()){toast(p("No active post in this radius. Increase the radius or create your own request.","এই দূরত্বে সক্রিয় পোস্ট নেই। দূরত্ব বাড়ান অথবা নিজের রিকোয়েস্ট তৈরি করুন।"));return;}
                if(sourceDialogShowing)return;
                candidates.sort((x,y)->Long.compare(y.getTime(),x.getTime()));
                String[] labels=new String[candidates.size()];for(int i=0;i<labels.length;i++)labels[i]=candidates.get(i).getTitle()+" — "+candidates.get(i).getUserName();
                sourceDialogShowing=true;
                AlertDialog dialog=new AlertDialog.Builder(AddAnnouncementActivity.this).setTitle(p("Choose seller post","বিক্রেতার পোস্ট বাছুন"))
                    .setItems(labels,(d,which)->{if(generation==requestCatalogGeneration&&usesAvailableItems())applySourcePost(candidates.get(which));})
                    .setNegativeButton(AppStrings.get(AddAnnouncementActivity.this).cancel(),null).create();
                dialog.setOnDismissListener(d->sourceDialogShowing=false);dialog.show();
            }
            @Override public void onCancelled(@NonNull DatabaseError e){if(generation==requestCatalogGeneration){catalogReady=false;toast(p("Could not load posts. Tap Choose active seller post to retry.","পোস্ট লোড হয়নি। আবার বাছুন বোতাম চাপুন।"));}}
        });
    }
    private void applySourcePost(Announcement a) {
        sourceAnnouncementId=a.getId();
        sourceAnnouncerUid = a.getUserId();
        sourcePostTitle = a.getTitle();
        sourcePostButton.setText(p("Seller post: ","বিক্রেতার পোস্ট: ")+a.getTitle()+" — "+a.getUserName());
        availableRequestItems.clear();
        List<String> cats=a.getSelectedCategories();if(cats==null||cats.isEmpty())cats=Collections.singletonList(a.getCategory());
        List<String> products=a.getSelectedProducts()==null?Collections.emptyList():a.getSelectedProducts();
        for(String cat:cats) {
            if(cat==null)continue;
            Set<String> offered=new LinkedHashSet<>();
            for(String item:products)if(cats.size()==1||PostCatalog.items(cat).contains(item))offered.add(item);
            if(offered.isEmpty() && !products.isEmpty()) offered.addAll(products);
            if("Others".equals(cat)&&a.getCustomSubcategory()!=null&&!a.getCustomSubcategory().trim().isEmpty())offered.add(a.getCustomSubcategory().trim());
            if(!offered.isEmpty())availableRequestItems.put(cat,offered);
        }
        if(availableRequestItems.isEmpty() && !products.isEmpty()) {
            String defaultCat = (cats != null && !cats.isEmpty()) ? cats.get(0) : "General";
            availableRequestItems.put(defaultCat, new LinkedHashSet<>(products));
        }
        catalogReady=true;retainAvailableItems();buildCategoryStep();
        if(!recognizedSpeechText.isEmpty())onSpeechRecognized(recognizedSpeechText);
    }
    private void retainAvailableItems() {
        if(!usesAvailableItems() || !catalogReady)return;
        selectedCategories.retainAll(availableRequestItems.keySet());
        Set<String> allowed=new HashSet<>();for(String cat:selectedCategories)allowed.addAll(availableRequestItems.getOrDefault(cat,Collections.emptySet()));
        selectedProducts.retainAll(allowed);
    }
    private void transcribeFile(File file, String language) {
        if (publishing || isRecording || importingAudio || !file.exists()) return;
        clearAudioChoices();
        final int generation = audioGeneration;
        transcribing = true;
        tvVoiceStatus.setVisibility(View.VISIBLE);
        tvVoiceStatus.setText(p("Recognizing audio… You can listen or delete while waiting.", "অডিও শনাক্ত হচ্ছে… অপেক্ষার সময় শুনতে বা মুছতে পারবেন।"));
        transcriptionJob = AudioTranscriber.transcribe(this, file.getAbsolutePath(), language, new AudioTranscriber.Callback() {
            @Override public void progress(int percent) {
                if (generation != audioGeneration || isFinishing() || isDestroyed()) return;
                tvVoiceStatus.setText(percent < 0
                        ? p("Preparing offline voice…", "অফলাইন ভয়েস প্রস্তুত হচ্ছে…")
                        : p("Recognizing on this phone: ", "ফোনেই অডিও শনাক্ত হচ্ছে: ") + percent + "%"
                        + p(" — you can listen or delete.", " — শুনতে বা মুছতে পারবেন।"));
            }
            @Override public void complete(String text) {
                if (generation != audioGeneration || isFinishing() || isDestroyed()) return;
                transcribing = false;
                transcriptionJob = null;
                if (text == null || text.trim().isEmpty()) tvVoiceStatus.setText(p(
                        "No speech recognized. Check the language, tap to retry, or select items manually.",
                        "কথা শনাক্ত হয়নি। ভাষা দেখুন, আবার চেষ্টা করতে চাপুন অথবা নিজে পণ্য নির্বাচন করুন।"));
                else onSpeechRecognized(text);
            }
            @Override public void failed(Throwable error) {
                if (generation != audioGeneration || isFinishing() || isDestroyed()) return;
                transcribing = false;
                transcriptionJob = null;
                tvVoiceStatus.setText(AudioTranscriber.errorMessage(error, LanguageManager.isEnglish(AddAnnouncementActivity.this)));
            }
        });
    }

    private void startAudioRecording() {
        if (publishing || importingAudio || isRecording) return;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(new String[]{Manifest.permission.RECORD_AUDIO});
            return;
        }
        releasePlayback();
        cancelAudioAnalysis();
        audioRecorder.startRecording(new AudioRecorderHelper.RecordListener() {
            @Override public void onRecordStarted() {
                isRecording = true;
                audioLanguage.setEnabled(false);
                tvAudioStatus.setText(AppStrings.get(AddAnnouncementActivity.this).audioRecording());
                updateAudioButton(true);
                showRecordingDialog();
            }
            @Override public void onRecordStopped(String path) {}
            @Override public void onError(String message) {
                isRecording = false;
                audioLanguage.setEnabled(true);
                updateAudioButton(false);
                dismissRecordingDialog();
                restoreAudioAfterCancelledRecording();
                toast(AppStrings.get(AddAnnouncementActivity.this).audioRecordError(message));
            }
        });
    }

    private void stopAudioRecording() {
        dismissRecordingDialog();
        audioRecorder.stopRecording(new AudioRecorderHelper.RecordListener() {
            @Override public void onRecordStarted() {}
            @Override public void onRecordStopped(String path) {
                isRecording = false;
                audioLanguage.setEnabled(true);
                updateAudioButton(false);
                attachAudio(path, AppStrings.get(AddAnnouncementActivity.this).audioRecorded());
            }
            @Override public void onError(String message) {
                isRecording = false;
                audioLanguage.setEnabled(true);
                updateAudioButton(false);
                restoreAudioAfterCancelledRecording();
                toast(AppStrings.get(AddAnnouncementActivity.this).audioStopFailed());
            }
        });
    }

    private void cancelAudioRecording() {
        dismissRecordingDialog();
        isRecording = false;
        audioRecorder.cancelRecording();
        audioLanguage.setEnabled(true);
        updateAudioButton(false);
        restoreAudioAfterCancelledRecording();
    }

    private void restoreAudioAfterCancelledRecording() {
        boolean attached = recordedAudioPath != null || (existingAudioUrl != null && !existingAudioUrl.isEmpty());
        tvAudioStatus.setText(attached ? p("Previous audio kept", "আগের অডিও রাখা হয়েছে") : "");
        showAudioPlayer();
        if (recordedAudioPath != null && recognizedSpeechText.isEmpty() && !isFinishing())
            transcribeFile(new File(recordedAudioPath), selectedAudioLanguage());
        else if (!attached) showAudioHint();
    }

    private void showRecordingDialog() {
        dismissRecordingDialog();
        AppStrings s = AppStrings.get(this);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_audio_recording, null);
        builder.setView(dialogView);
        builder.setCancelable(false);

        View dotRecording     = dialogView.findViewById(R.id.dotRecording);
        TextView tvTitle      = dialogView.findViewById(R.id.tvRecordingTitle);
        TextView tvTimer      = dialogView.findViewById(R.id.tvRecordingTimer);
        TextView tvHint       = dialogView.findViewById(R.id.tvRecordingHint);
        Button btnStop        = dialogView.findViewById(R.id.btnStopRecording);
        Button btnCancel      = dialogView.findViewById(R.id.btnCancelRecording);

        tvTitle.setText(s.audioRecordingTitle());
        tvTimer.setText("00:00");
        tvHint.setText(s.audioRecordingHint());
        btnStop.setText(s.audioRecordingStop());
        btnCancel.setText(s.audioRecordingCancel());

        if (dotRecording != null) {
            AlphaAnimation pulse = new AlphaAnimation(1.0f, 0.2f);
            pulse.setDuration(600);
            pulse.setRepeatMode(Animation.REVERSE);
            pulse.setRepeatCount(Animation.INFINITE);
            dotRecording.startAnimation(pulse);
        }

        recordDurationSec = 0;
        recordTimerRunnable = new Runnable() {
            @Override
            public void run() {
                if (isRecording) {
                    recordDurationSec++;
                    int min = recordDurationSec / 60;
                    int sec = recordDurationSec % 60;
                    tvTimer.setText(String.format(Locale.getDefault(), "%02d:%02d", min, sec));
                    recordTimerHandler.postDelayed(this, 1000);
                }
            }
        };
        recordTimerHandler.postDelayed(recordTimerRunnable, 1000);

        btnStop.setOnClickListener(v -> stopAudioRecording());
        btnCancel.setOnClickListener(v -> cancelAudioRecording());

        recordingDialog = builder.create();
        if (recordingDialog.getWindow() != null) {
            recordingDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        recordingDialog.show();
    }

    private void dismissRecordingDialog() {
        if (recordTimerHandler != null) {
            recordTimerHandler.removeCallbacksAndMessages(null);
        }
        if (recordingDialog != null && recordingDialog.isShowing()) {
            try {
                recordingDialog.dismiss();
            } catch (Exception ignored) {}
        }
        recordingDialog = null;
    }

    private void updateAudioButton(boolean recording) {
        AppStrings s = AppStrings.get(this);
        if (tvBtnAddAudio != null) {
            tvBtnAddAudio.setText(recording ? s.audioStopBtn() : s.addStep3AddAudio());
            tvBtnAddAudio.setTextColor(recording ? 0xFFDC2626 : 0xFFA21CAF);
        }
        if (ivBtnAddAudio != null) {
            ivBtnAddAudio.setImageResource(recording
                    ? android.R.drawable.ic_media_pause
                    : android.R.drawable.ic_btn_speak_now);
            ivBtnAddAudio.setImageTintList(ColorStateList.valueOf(recording ? 0xFFDC2626 : 0xFFA21CAF));
        }
        if (btnRecordAudio != null) {
            btnRecordAudio.setBackgroundResource(recording
                    ? R.drawable.bg_recording_badge
                    : R.drawable.bg_btn_soft);
        }
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

            if (recordedAudioPath != null && !recordedAudioPath.isEmpty()) {
                MediaMetadataRetriever mmr = new MediaMetadataRetriever();
                try {
                    mmr.setDataSource(recordedAudioPath);
                    String durStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                    if (durStr != null) {
                        long durMs = Long.parseLong(durStr);
                        tvAudioDuration.setText(formatTime((int) durMs));
                    }
                } catch (Exception ignored) {
                    tvAudioDuration.setText("0:00");
                } finally {
                    try { mmr.release(); } catch (Exception ignored) {}
                }
            }
        }
    }

    private void toggleAudioPlayback() {
        if (isRecording) return;
        if (mediaPlayer != null && !playbackPrepared) { releasePlayback(); return; }
        if (mediaPlayer != null) {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.pause();
                playbackHandler.removeCallbacksAndMessages(null);
                btnPlayAudio.setImageResource(android.R.drawable.ic_media_play);
            } else {
                mediaPlayer.start();
                btnPlayAudio.setImageResource(android.R.drawable.ic_media_pause);
                updatePlaybackProgress();
            }
            return;
        }
        String source = recordedAudioPath != null ? recordedAudioPath : existingAudioUrl;
        if (source == null || source.isEmpty()) return;
        final MediaPlayer player = new MediaPlayer();
        mediaPlayer = player;
        playbackPrepared = false;
        try {
            player.setDataSource(source);
            player.setOnPreparedListener(prepared -> {
                if (mediaPlayer != prepared || isFinishing() || isDestroyed()) return;
                playbackPrepared = true;
                prepared.start();
                btnPlayAudio.setImageResource(android.R.drawable.ic_media_pause);
                updatePlaybackProgress();
            });
            player.setOnCompletionListener(completed -> {
                if (mediaPlayer != completed) return;
                releasePlayback();
                showAudioPlayer();
            });
            player.setOnErrorListener((failed, what, extra) -> {
                if (mediaPlayer == failed) {
                    releasePlayback();
                    toast(AppStrings.get(this).detailCannotPlay());
                }
                return true;
            });
            player.prepareAsync();
        } catch (Exception error) {
            releasePlayback();
            toast(AppStrings.get(this).detailCannotPlay());
        }
    }

    private void releasePlayback() {
        playbackHandler.removeCallbacksAndMessages(null);
        MediaPlayer old = mediaPlayer;
        mediaPlayer = null;
        playbackPrepared = false;
        if (old != null) old.release();
        if (btnPlayAudio != null) btnPlayAudio.setImageResource(android.R.drawable.ic_media_play);
    }

    private void updatePlaybackProgress() {
        if (mediaPlayer != null && playbackPrepared && mediaPlayer.isPlaying()) {
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
        if (publishing) { toast(AppStrings.get(this).pleaseWait()); return; }
        releasePlayback();
        clearAudioChoices();
        importingAudio = false;
        if (recordedAudioPath != null) new File(recordedAudioPath).delete();
        recordedAudioPath = null;
        existingAudioUrl = null;
        showAudioPlayer();
        tvAudioStatus.setText("");
        showAudioHint();
        buildCategoryStep();
        toast(p("Audio removed", "অডিও সরানো হয়েছে"));
    }

    private void setupPermissions() {
        permissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                    if (Boolean.TRUE.equals(result.get(Manifest.permission.RECORD_AUDIO))) startAudioRecording();
                    else toast(p("Microphone permission is needed to record. You can still choose an audio file.",
                            "রেকর্ড করতে মাইক্রোফোনের অনুমতি প্রয়োজন। চাইলে অডিও ফাইল বেছে নিতে পারবেন।"));
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
            Bitmap bitmap = com.example.towncrierbd.utils.ImageImporter.decode(this, uri);
            if(selectedBitmaps.size()+existingImageUrls.size()>=10){bitmap.recycle();toast(p("Maximum 10 images","সর্বোচ্চ ১০টি ছবি"));return;}
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
    protected void onStop() {
        releasePlayback();
        if (isRecording) cancelAudioRecording();
        super.onStop();
    }

    @Override
    public void onBackPressed() {
        if (publishing) { toast(AppStrings.get(this).pleaseWait()); return; }
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        cancelAudioAnalysis();
        ++requestCatalogGeneration;
        dismissRecordingDialog();
        if (audioRecorder != null && isRecording) audioRecorder.cancelRecording();
        releasePlayback();
        if (isFinishing() && !publishing && recordedAudioPath != null) new File(recordedAudioPath).delete();
        super.onDestroy();
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