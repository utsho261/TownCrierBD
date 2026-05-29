package com.example.towncrierbd.activities;

import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.example.towncrierbd.R;
import com.example.towncrierbd.utils.AppStrings;
import com.example.towncrierbd.utils.LanguageManager;
import com.example.towncrierbd.utils.TranslationHelper;
import com.google.android.material.button.MaterialButton;

import java.util.concurrent.atomic.AtomicInteger;

public class AnnouncementDetailActivity extends AppCompatActivity {

    public static final String EXTRA_TITLE      = "title";
    public static final String EXTRA_DESC       = "desc";
    public static final String EXTRA_CATEGORY   = "category";
    public static final String EXTRA_PHONE      = "phone";
    public static final String EXTRA_IMAGE_URL  = "imageUrl";
    public static final String EXTRA_AUDIO_URL  = "audioUrl";
    public static final String EXTRA_USER_NAME  = "userName";
    public static final String EXTRA_DISTANCE   = "distance";
    public static final String EXTRA_TIME       = "time";
    public static final String EXTRA_OTHER_UID  = "otherUid";
    public static final String EXTRA_LAT        = "lat";
    public static final String EXTRA_LNG        = "lng";

    private MediaPlayer mediaPlayer;
    private boolean isPlaying = false;

    // Translation state
    private boolean isTranslated = false;
    private String originalTitle, originalDesc, originalCategory;
    private String translatedTitle, translatedDesc, translatedCategory;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_announcement_detail);

        AppStrings s = AppStrings.get(this);

        // Bind views
        ImageView      ivPhoto        = findViewById(R.id.ivPhoto);
        TextView       tvCategory     = findViewById(R.id.tvCategory);
        TextView       tvTitle        = findViewById(R.id.tvTitle);
        TextView       tvDesc         = findViewById(R.id.tvDesc);
        TextView       tvAvatar       = findViewById(R.id.tvAvatar);
        TextView       tvUserName     = findViewById(R.id.tvUserName);
        TextView       tvDistance     = findViewById(R.id.tvDistance);
        TextView       tvTime         = findViewById(R.id.tvTime);
        Button         btnCall        = findViewById(R.id.btnCall);
        Button         btnChat        = findViewById(R.id.btnChat);
        Button         btnDirection   = findViewById(R.id.btnDirection);
        ImageView      btnBack        = findViewById(R.id.btnBack);
        MaterialButton btnPlayAudio   = findViewById(R.id.btnPlayAudio);
        Button         btnTranslate   = findViewById(R.id.btnTranslate);

        // Read intent extras
        String title    = getIntent().getStringExtra(EXTRA_TITLE);
        String desc     = getIntent().getStringExtra(EXTRA_DESC);
        String category = getIntent().getStringExtra(EXTRA_CATEGORY);
        String phone    = getIntent().getStringExtra(EXTRA_PHONE);
        String imageUrl = getIntent().getStringExtra(EXTRA_IMAGE_URL);
        String audioUrl = getIntent().getStringExtra(EXTRA_AUDIO_URL);
        String userName = getIntent().getStringExtra(EXTRA_USER_NAME);
        String distance = getIntent().getStringExtra(EXTRA_DISTANCE);
        String time     = getIntent().getStringExtra(EXTRA_TIME);
        String otherUid = getIntent().getStringExtra(EXTRA_OTHER_UID);
        double destLat  = getIntent().getDoubleExtra(EXTRA_LAT, 0.0);
        double destLng  = getIntent().getDoubleExtra(EXTRA_LNG, 0.0);

        // Store originals for toggle
        originalTitle    = safe(title);
        originalDesc     = safe(desc);
        originalCategory = safe(category);

        // Populate views
        if (tvCategory != null) tvCategory.setText(originalCategory);
        if (tvTitle    != null) tvTitle.setText(originalTitle);
        if (tvDesc     != null) tvDesc.setText(originalDesc);
        if (tvUserName != null) tvUserName.setText(safe(userName));
        if (tvDistance != null) tvDistance.setText(safe(distance));
        if (tvTime     != null) tvTime.setText(safe(time));

        if (tvAvatar != null) {
            String nm = safe(userName);
            tvAvatar.setText(nm.isEmpty() ? "U" :
                    String.valueOf(Character.toUpperCase(nm.charAt(0))));
        }

        // Image
        if (ivPhoto != null) {
            if (imageUrl != null && !imageUrl.isEmpty()) {
                ivPhoto.setVisibility(View.VISIBLE);
                Glide.with(this)
                        .load(imageUrl)
                        .centerCrop()
                        .placeholder(android.R.drawable.ic_menu_gallery)
                        .into(ivPhoto);
            } else {
                ivPhoto.setVisibility(View.GONE);
            }
        }

        // ── Translate Button ────────────────────────────────────────────
        if (btnTranslate != null) {
            boolean isBanglaPost = TranslationHelper.isBangla(originalTitle + originalDesc);

            btnTranslate.setVisibility(View.VISIBLE);
            btnTranslate.setText(isBanglaPost ? s.detailTranslateToEn() : s.detailTranslateToBn());

            String langPair = isBanglaPost ? "bn|en" : "en|bn";

            btnTranslate.setOnClickListener(v -> {
                AppStrings as = AppStrings.get(this);

                if (isTranslated) {
                    // ── Revert to original — each field goes back to its own view ──
                    isTranslated = false;
                    if (tvCategory != null) tvCategory.setText(originalCategory);
                    if (tvTitle    != null) tvTitle.setText(originalTitle);
                    if (tvDesc     != null) tvDesc.setText(originalDesc);
                    btnTranslate.setText(isBanglaPost ? as.detailTranslateToEn() : as.detailTranslateToBn());

                } else {
                    // ── Use cache if all three are already translated ──
                    if (translatedTitle != null && translatedDesc != null && translatedCategory != null) {
                        isTranslated = true;
                        if (tvCategory != null) tvCategory.setText(translatedCategory);
                        if (tvTitle    != null) tvTitle.setText(translatedTitle);
                        if (tvDesc     != null) tvDesc.setText(translatedDesc);
                        btnTranslate.setText(isBanglaPost ? as.detailTranslateToBn() : as.detailTranslateToEn());
                        return;
                    }

                    // ── Translate each field INDEPENDENTLY ──
                    // This avoids the "all text in one box" bug caused by
                    // translateFields() joining fields with " || " — MyMemory
                    // sometimes drops the separator, merging all results into
                    // the first field (tvCategory) and leaving tvTitle/tvDesc empty.
                    btnTranslate.setEnabled(false);
                    btnTranslate.setText(as.detailTranslating());

                    final String[] results = new String[3]; // [0]=category, [1]=title, [2]=desc
                    final AtomicInteger doneCount = new AtomicInteger(0);

                    Runnable onAllDone = () -> {
                        // Each result is guaranteed to belong to its own field
                        translatedCategory = results[0] != null && !results[0].isEmpty()
                                ? results[0] : originalCategory;
                        translatedTitle    = results[1] != null && !results[1].isEmpty()
                                ? results[1] : originalTitle;
                        translatedDesc     = results[2] != null && !results[2].isEmpty()
                                ? results[2] : originalDesc;

                        isTranslated = true;

                        // Each translated string goes into its correct, dedicated TextView
                        if (tvCategory != null) tvCategory.setText(translatedCategory);
                        if (tvTitle    != null) tvTitle.setText(translatedTitle);
                        if (tvDesc     != null) tvDesc.setText(translatedDesc);

                        btnTranslate.setEnabled(true);
                        AppStrings as2 = AppStrings.get(this);
                        btnTranslate.setText(isBanglaPost ? as2.detailTranslateToBn() : as2.detailTranslateToEn());
                    };

                    // Translate category (index 0)
                    TranslationHelper.translateWithLangPair(
                            originalCategory, langPair,
                            new TranslationHelper.TranslateCallback() {
                                @Override public void onResult(String result) {
                                    results[0] = result;
                                    if (doneCount.incrementAndGet() == 3) onAllDone.run();
                                }
                                @Override public void onError(String originalText) {
                                    results[0] = originalCategory; // fallback
                                    if (doneCount.incrementAndGet() == 3) onAllDone.run();
                                }
                            });

                    // Translate title (index 1)
                    TranslationHelper.translateWithLangPair(
                            originalTitle, langPair,
                            new TranslationHelper.TranslateCallback() {
                                @Override public void onResult(String result) {
                                    results[1] = result;
                                    if (doneCount.incrementAndGet() == 3) onAllDone.run();
                                }
                                @Override public void onError(String originalText) {
                                    results[1] = originalTitle; // fallback
                                    if (doneCount.incrementAndGet() == 3) onAllDone.run();
                                    AppStrings as2 = AppStrings.get(AnnouncementDetailActivity.this);
                                    Toast.makeText(AnnouncementDetailActivity.this,
                                            as2.detailTranslateFail(), Toast.LENGTH_SHORT).show();
                                }
                            });

                    // Translate description (index 2)
                    TranslationHelper.translateWithLangPair(
                            originalDesc, langPair,
                            new TranslationHelper.TranslateCallback() {
                                @Override public void onResult(String result) {
                                    results[2] = result;
                                    if (doneCount.incrementAndGet() == 3) onAllDone.run();
                                }
                                @Override public void onError(String originalText) {
                                    results[2] = originalDesc; // fallback
                                    if (doneCount.incrementAndGet() == 3) onAllDone.run();
                                }
                            });
                }
            });
        }

        // Audio button
        if (btnPlayAudio != null) {
            if (audioUrl != null && !audioUrl.isEmpty()) {
                btnPlayAudio.setVisibility(View.VISIBLE);
                btnPlayAudio.setText(s.detailPlayAudio());
                final String finalAudioUrl = audioUrl;
                btnPlayAudio.setOnClickListener(v -> toggleAudio(finalAudioUrl, btnPlayAudio));
            } else {
                btnPlayAudio.setVisibility(View.GONE);
            }
        }

        // Back button
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());

        // Call button
        if (btnCall != null) {
            btnCall.setOnClickListener(v -> {
                AppStrings as = AppStrings.get(this);
                if (phone == null || phone.isEmpty()) {
                    Toast.makeText(this, as.detailNoPhone(), Toast.LENGTH_SHORT).show();
                    return;
                }
                startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone)));
            });
        }

        // Chat button
        if (btnChat != null) {
            btnChat.setOnClickListener(v -> {
                AppStrings as = AppStrings.get(this);
                if (otherUid == null || otherUid.isEmpty()) {
                    Toast.makeText(this, as.detailCannotChat(), Toast.LENGTH_SHORT).show();
                    return;
                }
                Intent i = new Intent(this, ChatActivity.class);
                i.putExtra(ChatActivity.EXTRA_OTHER_UID,  otherUid);
                i.putExtra(ChatActivity.EXTRA_OTHER_NAME, safe(userName));
                startActivity(i);
            });
        }

        // Direction button
        if (btnDirection != null) {
            if (destLat != 0.0 || destLng != 0.0) {
                btnDirection.setVisibility(View.VISIBLE);
                btnDirection.setOnClickListener(v -> openDirections(destLat, destLng));
            } else {
                btnDirection.setVisibility(View.GONE);
            }
        }
    }

    private void openDirections(double destLat, double destLng) {
        Uri gmmIntentUri = Uri.parse(
                "google.navigation:q=" + destLat + "," + destLng + "&mode=d");
        Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
        mapIntent.setPackage("com.google.android.apps.maps");

        if (mapIntent.resolveActivity(getPackageManager()) != null) {
            startActivity(mapIntent);
        } else {
            Uri web = Uri.parse("https://www.google.com/maps/dir/?api=1&destination="
                    + destLat + "," + destLng + "&travelmode=driving");
            startActivity(new Intent(Intent.ACTION_VIEW, web));
        }
    }

    private void toggleAudio(String url, MaterialButton btn) {
        AppStrings s = AppStrings.get(this);
        if (isPlaying) {
            stopAudio();
            btn.setText(s.detailPlayAudio());
            isPlaying = false;
        } else {
            btn.setText(s.detailStopAudio());
            btn.setEnabled(false);
            Toast.makeText(this, s.detailLoadingAudio(), Toast.LENGTH_SHORT).show();

            releasePlayer();
            mediaPlayer = new MediaPlayer();
            try {
                mediaPlayer.setDataSource(url);
                mediaPlayer.setOnPreparedListener(mp -> {
                    mp.start();
                    isPlaying = true;
                    runOnUiThread(() -> {
                        btn.setEnabled(true);
                        Toast.makeText(this, s.detailPlayingAudio(), Toast.LENGTH_SHORT).show();
                    });
                });
                mediaPlayer.setOnCompletionListener(mp -> {
                    isPlaying = false;
                    runOnUiThread(() -> btn.setText(s.detailPlayAudio()));
                    releasePlayer();
                });
                mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                    isPlaying = false;
                    runOnUiThread(() -> {
                        btn.setText(s.detailPlayAudio());
                        btn.setEnabled(true);
                        Toast.makeText(this, s.detailPlaybackError(), Toast.LENGTH_SHORT).show();
                    });
                    releasePlayer();
                    return true;
                });
                mediaPlayer.prepareAsync();
            } catch (Exception e) {
                btn.setText(s.detailPlayAudio());
                btn.setEnabled(true);
                Toast.makeText(this, s.detailCannotPlay(), Toast.LENGTH_SHORT).show();
                releasePlayer();
            }
        }
    }

    private void stopAudio() {
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) mediaPlayer.stop();
            } catch (Exception ignored) {}
            releasePlayer();
        }
    }

    private void releasePlayer() {
        if (mediaPlayer != null) {
            try { mediaPlayer.release(); } catch (Exception ignored) {}
            mediaPlayer = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopAudio();
    }

    private String safe(String s) {
        return s == null ? "" : s.trim();
    }
}