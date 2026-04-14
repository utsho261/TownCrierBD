package com.example.towncrierbd.adapters;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.speech.tts.TextToSpeech;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.towncrierbd.R;
import com.example.towncrierbd.activities.AnnouncementDetailActivity;
import com.example.towncrierbd.models.Announcement;
import com.example.towncrierbd.utils.Constants;
import com.example.towncrierbd.utils.DistanceUtil;
import com.google.firebase.database.FirebaseDatabase;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class FeedAdapter extends RecyclerView.Adapter<FeedAdapter.VH> {

    private final Context context;
    private final List<Announcement> items = new ArrayList<>();

    private double myLat = 0, myLng = 0;
    private boolean hasMyLoc = false;
    private boolean showEditDelete = false;
    private boolean disableCardClick = false;

    private TextToSpeech tts;

    public FeedAdapter(Context context) {
        this.context = context;
        tts = new TextToSpeech(context, status -> {
            if (status == TextToSpeech.SUCCESS) tts.setLanguage(Locale.ENGLISH);
        });
    }

    public void setShowEditDelete(boolean show) {
        this.showEditDelete = show;
    }

    public void setDisableCardClick(boolean disable) {
        this.disableCardClick = disable;
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

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_announcement, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        Announcement a = items.get(pos);

        // Badge
        String b = safe(a.getDisplayCategoryLabel());
        h.tvBadge.setText(b.isEmpty() ? safe(a.getCategory()) : b);

        // Title & Desc
        h.tvTitle.setText(safe(a.getTitle()));
        h.tvDesc.setText(safe(a.getDescription()));

        // Avatar & Name
        String nm = safe(a.getUserName());
        h.tvName.setText(nm);
        h.tvAvatar.setText(nm.isEmpty() ? "U" :
                String.valueOf(Character.toUpperCase(nm.charAt(0))));

        // Time
        h.tvTime.setText(getRelativeTime(a.getTime()));

        // Image
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

        // Distance
        if (hasMyLoc) {
            double d = DistanceUtil.distanceKm(myLat, myLng, a.getLat(), a.getLng());
            h.tvDistance.setText(String.format(Locale.getDefault(), "%.1f km away", d));
        } else {
            h.tvDistance.setText("Nearby");
        }

        // ✅ Profile এ Listen/Chat/Call hide করো
        if (showEditDelete) {
            h.btnListen.setVisibility(View.GONE);
            h.btnChat.setVisibility(View.GONE);
            h.btnCall.setVisibility(View.GONE);
        } else {
            h.btnListen.setVisibility(View.VISIBLE);
            h.btnChat.setVisibility(View.VISIBLE);
            h.btnCall.setVisibility(View.VISIBLE);

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

            // Listen
            h.btnListen.setOnClickListener(v -> {
                String speak = safe(a.getTitle()) + ". " + safe(a.getDescription());
                if (tts != null) tts.speak(speak, TextToSpeech.QUEUE_FLUSH, null, "tc_announce");
            });
        }

        // ✅ Card click → Detail (Profile এ disable)
        if (!disableCardClick) {
            h.itemView.setOnClickListener(v -> openDetail(a));
        } else {
            h.itemView.setOnClickListener(null);
        }

        // ✅ Edit/Delete — শুধু Profile এ
        if (showEditDelete && h.layoutEditDelete != null) {
            h.layoutEditDelete.setVisibility(View.VISIBLE);
            h.btnEdit.setOnClickListener(v -> showEditDialog(a, pos));
            h.btnDelete.setOnClickListener(v ->
                    new AlertDialog.Builder(context)
                            .setTitle("Delete Post")
                            .setMessage("Are you sure you want to delete this post?")
                            .setPositiveButton("Delete", (d, w) -> deletePost(a, pos))
                            .setNegativeButton("Cancel", null)
                            .show()
            );
        } else if (h.layoutEditDelete != null) {
            h.layoutEditDelete.setVisibility(View.GONE);
        }
    }

    private void openDetail(Announcement a) {
        String dist = "";
        if (hasMyLoc) {
            double d = DistanceUtil.distanceKm(myLat, myLng, a.getLat(), a.getLng());
            dist = String.format(Locale.getDefault(), "%.1f km away", d);
        }
        Intent intent = new Intent(context, AnnouncementDetailActivity.class);
        intent.putExtra(AnnouncementDetailActivity.EXTRA_TITLE,     safe(a.getTitle()));
        intent.putExtra(AnnouncementDetailActivity.EXTRA_DESC,      safe(a.getDescription()));
        intent.putExtra(AnnouncementDetailActivity.EXTRA_CATEGORY,  safe(a.getDisplayCategoryLabel()));
        intent.putExtra(AnnouncementDetailActivity.EXTRA_PHONE,     safe(a.getPhone()));
        intent.putExtra(AnnouncementDetailActivity.EXTRA_IMAGE_URL, safe(a.getImageUrl()));
        intent.putExtra(AnnouncementDetailActivity.EXTRA_USER_NAME, safe(a.getUserName()));
        intent.putExtra(AnnouncementDetailActivity.EXTRA_DISTANCE,  dist);
        intent.putExtra(AnnouncementDetailActivity.EXTRA_TIME,      getRelativeTime(a.getTime()));
        context.startActivity(intent);
    }

    private void showEditDialog(Announcement a, int pos) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 16, 48, 0);

        TextView labelTitle = new TextView(context);
        labelTitle.setText("Title");
        labelTitle.setTextSize(14);
        EditText etNewTitle = new EditText(context);
        etNewTitle.setText(a.getTitle());

        TextView labelDesc = new TextView(context);
        labelDesc.setText("Description");
        labelDesc.setTextSize(14);
        labelDesc.setPadding(0, 16, 0, 0);
        EditText etNewDesc = new EditText(context);
        etNewDesc.setText(a.getDescription());
        etNewDesc.setMinLines(3);

        layout.addView(labelTitle);
        layout.addView(etNewTitle);
        layout.addView(labelDesc);
        layout.addView(etNewDesc);

        new AlertDialog.Builder(context)
                .setTitle("Edit Post")
                .setView(layout)
                .setPositiveButton("Save", (d, w) -> {
                    String newTitle = etNewTitle.getText().toString().trim();
                    String newDesc  = etNewDesc.getText().toString().trim();
                    if (newTitle.isEmpty()) {
                        Toast.makeText(context, "Title cannot be empty", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    FirebaseDatabase.getInstance()
                            .getReference(Constants.DB_ANNOUNCEMENTS)
                            .child(a.getId())
                            .child("title").setValue(newTitle);
                    FirebaseDatabase.getInstance()
                            .getReference(Constants.DB_ANNOUNCEMENTS)
                            .child(a.getId())
                            .child("description").setValue(newDesc);
                    a.setTitle(newTitle);
                    a.setDescription(newDesc);
                    notifyItemChanged(pos);
                    Toast.makeText(context, "Updated ✅", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deletePost(Announcement a, int pos) {
        if (a.getId() == null) return;
        FirebaseDatabase.getInstance()
                .getReference(Constants.DB_ANNOUNCEMENTS)
                .child(a.getId())
                .removeValue()
                .addOnSuccessListener(v -> {
                    items.remove(pos);
                    notifyItemRemoved(pos);
                    notifyItemRangeChanged(pos, items.size());
                    Toast.makeText(context, "Deleted ✅", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(context, "Delete failed", Toast.LENGTH_SHORT).show());
    }

    @Override
    public int getItemCount() { return items.size(); }

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

    public static class VH extends RecyclerView.ViewHolder {
        ImageView ivPhoto;
        TextView tvBadge, tvTitle, tvDesc, tvAvatar, tvName, tvDistance, tvTime;
        View btnListen, btnChat, btnCall;
        View layoutEditDelete;
        android.widget.Button btnEdit, btnDelete;

        public VH(@NonNull View itemView) {
            super(itemView);
            ivPhoto          = itemView.findViewById(R.id.ivPhoto);
            tvBadge          = itemView.findViewById(R.id.tvBadge);
            tvTitle          = itemView.findViewById(R.id.tvTitle);
            tvDesc           = itemView.findViewById(R.id.tvDesc);
            tvAvatar         = itemView.findViewById(R.id.tvAvatar);
            tvName           = itemView.findViewById(R.id.tvName);
            tvDistance       = itemView.findViewById(R.id.tvDistance);
            tvTime           = itemView.findViewById(R.id.tvTime);
            btnListen        = itemView.findViewById(R.id.btnListen);
            btnChat          = itemView.findViewById(R.id.btnChat);
            btnCall          = itemView.findViewById(R.id.btnCall);
            layoutEditDelete = itemView.findViewById(R.id.layoutEditDelete);
            btnEdit          = itemView.findViewById(R.id.btnEdit);
            btnDelete        = itemView.findViewById(R.id.btnDelete);
        }
    }

    private String safe(String s) { return s == null ? "" : s.trim(); }
}