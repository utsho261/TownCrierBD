package com.example.towncrierbd.adapters;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
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

import com.example.towncrierbd.R;
import com.example.towncrierbd.models.Announcement;
import com.example.towncrierbd.utils.ImageBase64Util;

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
            if (tts != null) {
                tts.stop();
                tts.shutdown();
                tts = null;
            }
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

        String badge = safe(a.getDisplayCategoryLabel());
        h.tvBadge.setText(badge.isEmpty() ? safe(a.getCategory()) : badge);

        h.tvTitle.setText(safe(a.getTitle()));
        h.tvDesc.setText(safe(a.getDescription()));
        h.tvName.setText(safe(a.getUserName()));

        String nm = safe(a.getUserName());
        h.tvAvatar.setText(nm.isEmpty() ? "U" : String.valueOf(Character.toUpperCase(nm.charAt(0))));

        // ✅ Image from Base64: না থাকলে পুরোটা hide (garbage icon না)
        Bitmap bmp = ImageBase64Util.base64ToBitmap(a.getImageBase64());
        if (bmp != null) {
            h.ivPhoto.setVisibility(View.VISIBLE);
            h.ivPhoto.setImageBitmap(bmp);
        } else {
            h.ivPhoto.setImageDrawable(null);
            h.ivPhoto.setVisibility(View.GONE);
        }

        if (hasMyLoc) {
            double d = distanceKm(myLat, myLng, a.getLat(), a.getLng());
            h.tvDistance.setText(String.format(Locale.getDefault(), "%.1f km away", d));
        } else {
            h.tvDistance.setText("Nearby");
        }

        h.btnCall.setOnClickListener(v -> {
            String phone = safe(a.getPhone());
            if (phone.isEmpty()) { Toast.makeText(context, "No phone number", Toast.LENGTH_SHORT).show(); return; }
            context.startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone)));
        });

        h.btnChat.setOnClickListener(v -> {
            String phone = safe(a.getPhone());
            if (phone.isEmpty()) { Toast.makeText(context, "No phone number", Toast.LENGTH_SHORT).show(); return; }
            Intent i = new Intent(Intent.ACTION_SENDTO);
            i.setData(Uri.parse("smsto:" + phone));
            i.putExtra("sms_body", "Hello! I'm interested in: " + safe(a.getTitle()));
            context.startActivity(i);
        });

        h.btnListen.setOnClickListener(v -> {
            String speak = safe(a.getTitle()) + ". " + safe(a.getDescription());
            if (tts != null) tts.speak(speak, TextToSpeech.QUEUE_FLUSH, null, "tc_announce");
        });
    }

    @Override public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        ImageView ivPhoto;
        TextView tvBadge, tvTitle, tvDesc, tvAvatar, tvName, tvDistance;
        View btnListen, btnChat, btnCall;

        VH(@NonNull View itemView) {
            super(itemView);
            ivPhoto = itemView.findViewById(R.id.ivPhoto);
            tvBadge = itemView.findViewById(R.id.tvBadge);
            tvTitle = itemView.findViewById(R.id.tvTitle);
            tvDesc = itemView.findViewById(R.id.tvDesc);
            tvAvatar = itemView.findViewById(R.id.tvAvatar);
            tvName = itemView.findViewById(R.id.tvName);
            tvDistance = itemView.findViewById(R.id.tvDistance);

            btnListen = itemView.findViewById(R.id.btnListen);
            btnChat = itemView.findViewById(R.id.btnChat);
            btnCall = itemView.findViewById(R.id.btnCall);
        }
    }

    private String safe(String s) { return s == null ? "" : s.trim(); }

    private double distanceKm(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double aa = Math.sin(dLat/2)*Math.sin(dLat/2)
                + Math.cos(Math.toRadians(lat1))*Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon/2)*Math.sin(dLon/2);
        double c = 2 * Math.atan2(Math.sqrt(aa), Math.sqrt(1-aa));
        return R * c;
    }
}
