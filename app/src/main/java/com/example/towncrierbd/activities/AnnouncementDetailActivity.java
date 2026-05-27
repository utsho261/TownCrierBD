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

import com.bumptech.glide.Glide;
import com.example.towncrierbd.R;
import com.google.android.material.button.MaterialButton;

public class AnnouncementDetailActivity extends BaseActivity {

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_announcement_detail);

        ImageView    ivPhoto       = findViewById(R.id.ivPhoto);
        TextView     tvCategory    = findViewById(R.id.tvCategory);
        TextView     tvTitle       = findViewById(R.id.tvTitle);
        TextView     tvDesc        = findViewById(R.id.tvDesc);
        TextView     tvAvatar      = findViewById(R.id.tvAvatar);
        TextView     tvUserName    = findViewById(R.id.tvUserName);
        TextView     tvDistance    = findViewById(R.id.tvDistance);
        TextView     tvTime        = findViewById(R.id.tvTime);
        Button       btnCall       = findViewById(R.id.btnCall);
        Button       btnChat       = findViewById(R.id.btnChat);
        Button       btnDirection  = findViewById(R.id.btnDirection);
        ImageView    btnBack       = findViewById(R.id.btnBack);
        MaterialButton btnPlayAudio = findViewById(R.id.btnPlayAudio);

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

        if (tvCategory != null) tvCategory.setText(safe(category));
        if (tvTitle    != null) tvTitle.setText(safe(title));
        if (tvDesc     != null) tvDesc.setText(safe(desc));
        if (tvUserName != null) tvUserName.setText(safe(userName));
        if (tvDistance != null) tvDistance.setText(safe(distance));
        if (tvTime     != null) tvTime.setText(safe(time));

        if (tvAvatar != null) {
            String nm = safe(userName);
            tvAvatar.setText(nm.isEmpty() ? "U" :
                    String.valueOf(Character.toUpperCase(nm.charAt(0))));
        }

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

        // ✅ UPDATED: Use getString() for audio button text
        if (btnPlayAudio != null) {
            if (audioUrl != null && !audioUrl.isEmpty()) {
                btnPlayAudio.setVisibility(View.VISIBLE);
                btnPlayAudio.setText(getString(R.string.play_audio));
                final String finalAudioUrl = audioUrl;
                btnPlayAudio.setOnClickListener(v -> toggleAudio(finalAudioUrl, btnPlayAudio));
            } else {
                btnPlayAudio.setVisibility(View.GONE);
            }
        }

        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        // ✅ UPDATED: Use getString() for call button
        if (btnCall != null) {
            btnCall.setOnClickListener(v -> {
                if (phone == null || phone.isEmpty()) {
                    // ✅ UPDATED
                    Toast.makeText(this, getString(R.string.no_phone_number), Toast.LENGTH_SHORT).show();
                    return;
                }
                startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone)));
            });
        }

        // ✅ UPDATED: Use getString() for chat button
        if (btnChat != null) {
            btnChat.setOnClickListener(v -> {
                if (otherUid == null || otherUid.isEmpty()) {
                    // ✅ UPDATED
                    Toast.makeText(this, getString(R.string.cannot_start_chat), Toast.LENGTH_SHORT).show();
                    return;
                }
                Intent i = new Intent(this, ChatActivity.class);
                i.putExtra(ChatActivity.EXTRA_OTHER_UID,  otherUid);
                i.putExtra(ChatActivity.EXTRA_OTHER_NAME, safe(userName));
                startActivity(i);
            });
        }

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
            // ✅ UPDATED: Use getString()
            btn.setText(getString(R.string.play_audio));
            isPlaying = false;
        } else {
            // ✅ UPDATED: Use getString()
            btn.setText(getString(R.string.stop_audio));
            btn.setEnabled(false);
            // ✅ UPDATED: Use getString()
            Toast.makeText(this, getString(R.string.loading_audio), Toast.LENGTH_SHORT).show();

            releasePlayer();
            mediaPlayer = new MediaPlayer();
            try {
                mediaPlayer.setDataSource(url);
                mediaPlayer.setOnPreparedListener(mp -> {
                    mp.start();
                    isPlaying = true;
                    runOnUiThread(() -> {
                        btn.setEnabled(true);
                        // ✅ UPDATED: Use getString()
                        Toast.makeText(this, getString(R.string.playing), Toast.LENGTH_SHORT).show();
                    });
                });
                mediaPlayer.setOnCompletionListener(mp -> {
                    isPlaying = false;
                    // ✅ UPDATED: Use getString()
                    runOnUiThread(() -> btn.setText(getString(R.string.play_audio)));
                    releasePlayer();
                });
                mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                    isPlaying = false;
                    runOnUiThread(() -> {
                        // ✅ UPDATED: Use getString()
                        btn.setText(getString(R.string.play_audio));
                        btn.setEnabled(true);
                        Toast.makeText(this, getString(R.string.playback_error), Toast.LENGTH_SHORT).show();
                    });
                    releasePlayer();
                    return true;
                });
                mediaPlayer.prepareAsync();
            } catch (Exception e) {
                // ✅ UPDATED: Use getString()
                btn.setText(getString(R.string.play_audio));
                btn.setEnabled(true);
                Toast.makeText(this, getString(R.string.cannot_play_audio), Toast.LENGTH_SHORT).show();
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