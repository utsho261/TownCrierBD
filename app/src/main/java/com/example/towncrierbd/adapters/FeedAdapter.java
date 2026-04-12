package com.example.towncrierbd.adapters;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.speech.tts.TextToSpeech;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.towncrierbd.R;
import com.example.towncrierbd.models.Announcement;
import com.example.towncrierbd.utils.DistanceUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class FeedAdapter extends RecyclerView.Adapter<FeedAdapter.VH> {

    private final Context context;
    private final List<Announcement> items = new ArrayList<>();

    private double myLat = 0, myLng = 0;
    private boolean hasMyLoc = false;

    private TextToSpeech tts;

    public FeedAdapter(Context context) {
        this.context = context;
        tts = new TextToSpeech(context, status -> {
            if (status == TextToSpeech.SUCCESS) tts.setLanguage(Locale.ENGLISH);
        });
    }

    public void release() {
        try {
            if (tts != null) { tts.stop(); tts.shutdown(); tts = null; }
        } catch (Exception ignored) {}
    }

    public void setMyLocation(double lat, double lng) {
        myLat = lat; myLng = lng; hasMyLoc = true;
        notifyDataSetChanged();
    }

    public void setData(List<Announcement> data) {
        items.clear();
        if (data != null) items.addAll(data);
        notifyDataSetChanged();
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_announcement, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        Announcement a = items.get(pos);

        // Badge
        String badge = safe(a.getDisplayCategoryLabel());
        h.tvBadge.setText(badge.isEmpty() ? safe(a.getCategory()) : badge);

        // Title & Desc
        h.tvTitle.setText(safe(a.getTitle()));
        h.tvDesc.setText(safe(a.getDescription()));

        // Avatar & Name
        String nm = safe(a.getUserName());
        h.tvName.setText(nm);
        h.tvAvatar.setText(nm.isEmpty() ? "U" :
                String.valueOf(Character.toUpperCase(nm.charAt(0))));

        // ✅ Relative Time
        h.tvTime.setText(getRelativeTime(a.getTime()));

        // ✅ Glide দিয়ে image load
        String imgUrl = safe(a.getImageUrl());
        if (!imgUrl.isEmpty()) {
            h.ivPhoto.setVisibility(View.VISIBLE);
            Glide.with(context)
                    .load(imgUrl)
                    .centerCrop()
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .into(h.ivPhoto);
        } else {
            h.ivPhoto.setVisibility(View.GONE);
            h.ivPhoto.setImageDrawable(null);
        }

        // ✅ Distance
        if (hasMyLoc) {
            double d = DistanceUtil.distanceKm(myLat, myLng, a.getLat(), a.getLng());
            h.tvDistance.setText(String.format(Locale.getDefault(), "%.1f km away", d));
        } else {
            h.tvDistance.setText("Nearby");
        }

        // Call
        h.btnCall.setOnClickListener(v -> {
            String phone = safe(a.getPhone());
            if (phone.isEmpty()) {
                Toast.makeText(context, "No phone number", Toast.LENGTH_SHORT).show();
                return;
            }
            context.startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone)));
        });

        // Chat
        h.btnChat.setOnClickListener(v -> {
            String phone = safe(a.getPhone());
            if (phone.isEmpty()) {
                Toast.makeText(context, "No phone number", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent i = new Intent(Intent.ACTION_SENDTO);
            i.setData(Uri.parse("smsto:" + phone));
            i.putExtra("sms_body", "Hello! I'm interested in: " + safe(a.getTitle()));
            context.startActivity(i);
        });

        // Listen TTS
        h.btnListen.setOnClickListener(v -> {
            String speak = safe(a.getTitle()) + ". " + safe(a.getDescription());
            if (tts != null) tts.speak(speak, TextToSpeech.QUEUE_FLUSH, null, "tc_announce");
        });
    }

    @Override public int getItemCount() { return items.size(); }

    private String getRelativeTime(long timeMillis) {
        if (timeMillis == 0) return "";
        long diff    = System.currentTimeMillis() - timeMillis;
        long minutes = diff / 60000;
        long hours   = minutes / 60;
        long days    = hours / 24;
        if (minutes < 1)  return "Just now";
        if (minutes < 60) return minutes + " min ago";
        if (hours < 24)   return hours + " hr ago";
        return days + " day" + (days > 1 ? "s" : "") + " ago";
    }

    static class VH extends RecyclerView.ViewHolder {
        ImageView ivPhoto;
        TextView tvBadge, tvTitle, tvDesc, tvAvatar, tvName, tvDistance, tvTime;
        View btnListen, btnChat, btnCall;

        VH(@NonNull View itemView) {
            super(itemView);
            ivPhoto    = itemView.findViewById(R.id.ivPhoto);
            tvBadge    = itemView.findViewById(R.id.tvBadge);
            tvTitle    = itemView.findViewById(R.id.tvTitle);
            tvDesc     = itemView.findViewById(R.id.tvDesc);
            tvAvatar   = itemView.findViewById(R.id.tvAvatar);
            tvName     = itemView.findViewById(R.id.tvName);
            tvDistance = itemView.findViewById(R.id.tvDistance);
            tvTime     = itemView.findViewById(R.id.tvTime);
            btnListen  = itemView.findViewById(R.id.btnListen);
            btnChat    = itemView.findViewById(R.id.btnChat);
            btnCall    = itemView.findViewById(R.id.btnCall);
        }
    }

    private String safe(String s) { return s == null ? "" : s.trim(); }
}