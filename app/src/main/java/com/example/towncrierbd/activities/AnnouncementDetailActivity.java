package com.example.towncrierbd.activities;

import android.content.Intent;
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

public class AnnouncementDetailActivity extends AppCompatActivity {

    public static final String EXTRA_TITLE     = "title";
    public static final String EXTRA_DESC      = "desc";
    public static final String EXTRA_CATEGORY  = "category";
    public static final String EXTRA_PHONE     = "phone";
    public static final String EXTRA_IMAGE_URL = "imageUrl";
    public static final String EXTRA_USER_NAME = "userName";
    public static final String EXTRA_DISTANCE  = "distance";
    public static final String EXTRA_TIME      = "time";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_announcement_detail);

        ImageView ivPhoto   = findViewById(R.id.ivPhoto);
        TextView tvCategory = findViewById(R.id.tvCategory);
        TextView tvTitle    = findViewById(R.id.tvTitle);
        TextView tvDesc     = findViewById(R.id.tvDesc);
        TextView tvAvatar   = findViewById(R.id.tvAvatar);
        TextView tvUserName = findViewById(R.id.tvUserName);
        TextView tvDistance = findViewById(R.id.tvDistance);
        TextView tvTime     = findViewById(R.id.tvTime);
        Button btnCall      = findViewById(R.id.btnCall);
        Button btnChat      = findViewById(R.id.btnChat);
        ImageView btnBack   = findViewById(R.id.btnBack);

        String title    = getIntent().getStringExtra(EXTRA_TITLE);
        String desc     = getIntent().getStringExtra(EXTRA_DESC);
        String category = getIntent().getStringExtra(EXTRA_CATEGORY);
        String phone    = getIntent().getStringExtra(EXTRA_PHONE);
        String imageUrl = getIntent().getStringExtra(EXTRA_IMAGE_URL);
        String userName = getIntent().getStringExtra(EXTRA_USER_NAME);
        String distance = getIntent().getStringExtra(EXTRA_DISTANCE);
        String time     = getIntent().getStringExtra(EXTRA_TIME);

        tvCategory.setText(safe(category));
        tvTitle.setText(safe(title));
        tvDesc.setText(safe(desc));
        tvUserName.setText(safe(userName));
        tvDistance.setText(safe(distance));
        tvTime.setText(safe(time));

        // Avatar first letter
        String nm = safe(userName);
        tvAvatar.setText(nm.isEmpty() ? "U" :
                String.valueOf(Character.toUpperCase(nm.charAt(0))));

        // Image
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

        btnBack.setOnClickListener(v -> finish());

        btnCall.setOnClickListener(v -> {
            if (phone == null || phone.isEmpty()) {
                Toast.makeText(this, "No phone number", Toast.LENGTH_SHORT).show();
                return;
            }
            startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone)));
        });

        btnChat.setOnClickListener(v -> {
            if (phone == null || phone.isEmpty()) {
                Toast.makeText(this, "No phone number", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent i = new Intent(Intent.ACTION_SENDTO);
            i.setData(Uri.parse("smsto:" + phone));
            i.putExtra("sms_body", "Hello! I'm interested in: " + safe(title));
            startActivity(i);
        });
    }

    private String safe(String s) { return s == null ? "" : s.trim(); }
}