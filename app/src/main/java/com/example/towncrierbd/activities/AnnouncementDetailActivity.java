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

    // ✅ Translation state
    private boolean isTranslated = false;
    private String originalTitle, originalDesc, originalCategory;
    private String translatedTitle, translatedDesc, translatedCategory;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_announcement_detail);

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
        Button         btnTranslate   = findViewById(R.id.btnTranslate); // ✅ NEW

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

        // ── ✅ Translate Button ─────────────────────────────────────────────
        if (btnTranslate != null) {
            AppStrings strings = AppStrings.get(this);
            boolean isBanglaPost = TranslationHelper.isBangla(originalTitle + originalDesc);
            boolean isViewerBn = !LanguageManager.isEnglish(this);

            if ((isBanglaPost && !isViewerBn) || (!isBanglaPost && isViewerBn)) {
                btnTranslate.setVisibility(View.VISIBLE);
                btnTranslate.setText(isBanglaPost ? strings.detailTranslateToEn() : strings.detailTranslateToBn());
            } else {
                btnTranslate.setVisibility(View.GONE);
            }
            btnTranslate.setVisibility(View.VISIBLE);

            String langPair = isBanglaPost ? "bn|en" : "en|bn";

            btnTranslate.setOnClickListener(v -> {
                if (isTranslated) {
                    // ── Show original ──
                    isTranslated = false;
                    if (tvCategory != null) tvCategory.setText(originalCategory);
                    if (tvTitle    != null) tvTitle.setText(originalTitle);
                    if (tvDesc     != null) tvDesc.setText(originalDesc);
                    btnTranslate.setText(isBanglaPost ? "🌐 English" : "🌐 বাংলা");

                } else {
                    // ── Translate ──
                    if (translatedTitle != null) {
                        // Already translated — show cached
                        isTranslated = true;
                        if (tvCategory != null && translatedCategory != null)
                            tvCategory.setText(translatedCategory);
                        if (tvTitle    != null) tvTitle.setText(translatedTitle);
                        if (tvDesc     != null) tvDesc.setText(translatedDesc);
                        btnTranslate.setText(isBanglaPost ? "🌐 বাংলা" : "🌐 English");
                        return;
                    }

                    btnTranslate.setEnabled(false);
                    btnTranslate.setText(strings.detailTranslating());

                    String[] fields = {originalCategory, originalTitle, originalDesc};

                    TranslationHelper.translateFields(fields, langPair, results -> {
                        if (results != null && results.length >= 3) {
                            translatedCategory = results[0];
                            translatedTitle    = results[1];
                            translatedDesc     = results[2];

                            isTranslated = true;
                            if (tvCategory != null && !translatedCategory.isEmpty())
                                tvCategory.setText(translatedCategory);
                            if (tvTitle    != null && !translatedTitle.isEmpty())
                                tvTitle.setText(translatedTitle);
                            if (tvDesc     != null && !translatedDesc.isEmpty())
                                tvDesc.setText(translatedDesc);
                        } else {
                            Toast.makeText(this, strings.detailTranslateFail(), Toast.LENGTH_SHORT).show();
                        }
                        btnTranslate.setEnabled(true);
                        btnTranslate.setText(isBanglaPost ? strings.detailTranslateToBn() : strings.detailTranslateToEn());
                    });
                }
            });
        }

        // Audio button
        if (btnPlayAudio != null) {
            if (audioUrl != null && !audioUrl.isEmpty()) {
                btnPlayAudio.setVisibility(View.VISIBLE);
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
                if (phone == null || phone.isEmpty()) {
                    Toast.makeText(this, "No phone number", Toast.LENGTH_SHORT).show();
                    return;
                }
                startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone)));
            });
        }

        // Chat button
        if (btnChat != null) {
            btnChat.setOnClickListener(v -> {
                if (otherUid == null || otherUid.isEmpty()) {
                    Toast.makeText(this, "Cannot start chat", Toast.LENGTH_SHORT).show();
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
        if (isPlaying) {
            stopAudio();
            btn.setText("🔊 Play Audio");
            isPlaying = false;
        } else {
            btn.setText("⏹ Stop Audio");
            btn.setEnabled(false);
            Toast.makeText(this, "Loading audio...", Toast.LENGTH_SHORT).show();

            releasePlayer();
            mediaPlayer = new MediaPlayer();
            try {
                mediaPlayer.setDataSource(url);
                mediaPlayer.setOnPreparedListener(mp -> {
                    mp.start();
                    isPlaying = true;
                    runOnUiThread(() -> {
                        btn.setEnabled(true);
                        Toast.makeText(this, "▶ Playing", Toast.LENGTH_SHORT).show();
                    });
                });
                mediaPlayer.setOnCompletionListener(mp -> {
                    isPlaying = false;
                    runOnUiThread(() -> btn.setText("🔊 Play Audio"));
                    releasePlayer();
                });
                mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                    isPlaying = false;
                    runOnUiThread(() -> {
                        btn.setText("🔊 Play Audio");
                        btn.setEnabled(true);
                        Toast.makeText(this, "Playback error", Toast.LENGTH_SHORT).show();
                    });
                    releasePlayer();
                    return true;
                });
                mediaPlayer.prepareAsync();
            } catch (Exception e) {
                btn.setText("🔊 Play Audio");
                btn.setEnabled(true);
                Toast.makeText(this, "Cannot play audio", Toast.LENGTH_SHORT).show();
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