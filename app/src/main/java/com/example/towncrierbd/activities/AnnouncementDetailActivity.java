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

    // ✅ FIXED: MediaPlayer এখন সত্যিই ব্যবহার হচ্ছে
    private MediaPlayer mediaPlayer;
    private boolean isPlaying = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_announcement_detail);

        ImageView ivPhoto        = findViewById(R.id.ivPhoto);
        TextView tvCategory      = findViewById(R.id.tvCategory);
        TextView tvTitle         = findViewById(R.id.tvTitle);
        TextView tvDesc          = findViewById(R.id.tvDesc);
        TextView tvAvatar        = findViewById(R.id.tvAvatar);
        TextView tvUserName      = findViewById(R.id.tvUserName);
        TextView tvDistance      = findViewById(R.id.tvDistance);
        TextView tvTime          = findViewById(R.id.tvTime);
        Button btnCall           = findViewById(R.id.btnCall);
        Button btnChat           = findViewById(R.id.btnChat);
        ImageView btnBack        = findViewById(R.id.btnBack);
        // ✅ FIXED: btnPlayAudio এখন layout এ আছে এবং কাজ করছে
        MaterialButton btnPlayAudio = findViewById(R.id.btnPlayAudio);

        String title    = getIntent().getStringExtra(EXTRA_TITLE);
        String desc     = getIntent().getStringExtra(EXTRA_DESC);
        String category = getIntent().getStringExtra(EXTRA_CATEGORY);
        String phone    = getIntent().getStringExtra(EXTRA_PHONE);
        String imageUrl = getIntent().getStringExtra(EXTRA_IMAGE_URL);
        String audioUrl = getIntent().getStringExtra(EXTRA_AUDIO_URL); // ✅ FIXED: এখন ব্যবহার হচ্ছে
        String userName = getIntent().getStringExtra(EXTRA_USER_NAME);
        String distance = getIntent().getStringExtra(EXTRA_DISTANCE);
        String time     = getIntent().getStringExtra(EXTRA_TIME);
        String otherUid = getIntent().getStringExtra(EXTRA_OTHER_UID);

        tvCategory.setText(safe(category));
        tvTitle.setText(safe(title));
        tvDesc.setText(safe(desc));
        tvUserName.setText(safe(userName));
        tvDistance.setText(safe(distance));
        tvTime.setText(safe(time));

        String nm = safe(userName);
        tvAvatar.setText(nm.isEmpty() ? "U" :
                String.valueOf(Character.toUpperCase(nm.charAt(0))));

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

        // ✅ FIXED: Audio play button — audioUrl থাকলে button দেখাও
        if (audioUrl != null && !audioUrl.isEmpty()) {
            if (btnPlayAudio != null) {
                btnPlayAudio.setVisibility(View.VISIBLE);
                btnPlayAudio.setOnClickListener(v -> toggleAudio(audioUrl, btnPlayAudio));
            }
        }

        btnBack.setOnClickListener(v -> finish());

        btnCall.setOnClickListener(v -> {
            if (phone == null || phone.isEmpty()) {
                Toast.makeText(this, "No phone number", Toast.LENGTH_SHORT).show();
                return;
            }
            startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone)));
        });

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

    // ✅ FIXED: Audio toggle — play/pause/stop logic
    private void toggleAudio(String url, MaterialButton btn) {
        if (isPlaying) {
            // চলছে — stop করো
            stopAudio();
            btn.setText("🔊 Play Audio");
            isPlaying = false;
        } else {
            // শুরু করো
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

    private String safe(String s) { return s == null ? "" : s.trim(); }
}