package com.example.towncrierbd.adapters;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.speech.tts.TextToSpeech;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
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
import com.example.towncrierbd.activities.ChatActivity;
import com.example.towncrierbd.models.Announcement;
import com.example.towncrierbd.utils.Constants;
import com.example.towncrierbd.utils.DistanceUtil;
import com.example.towncrierbd.utils.TranslationHelper;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.FirebaseDatabase;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class FeedAdapter extends RecyclerView.Adapter<FeedAdapter.VH> {

    private final Context context;
    private final List<Announcement> items = new ArrayList<>();

    private double myLat = 0, myLng = 0;
    private boolean hasMyLoc        = false;
    private boolean showEditDelete   = false;
    private boolean disableCardClick = false;

    private TextToSpeech tts;
    private MediaPlayer  mediaPlayer;

    // ✅ Translation state: track which cards are showing translated text
    // Key = announcement ID, Value = true if translated view is shown
    private final Map<String, Boolean> translatedState = new HashMap<>();

    // ✅ Translation cache per item (title, desc, category)
    private final Map<String, String[]> translationCache = new HashMap<>();

    public FeedAdapter(Context context) {
        this.context = context;
        tts = new TextToSpeech(context, status -> {
            if (status == TextToSpeech.SUCCESS && tts != null) {
                tts.setLanguage(Locale.ENGLISH);
            }
        });
    }

    public void setShowEditDelete(boolean show) {
        this.showEditDelete = show;
        notifyDataSetChanged();
    }

    public void setDisableCardClick(boolean disable) {
        this.disableCardClick = disable;
        notifyDataSetChanged();
    }

    public void release() {
        try {
            if (tts != null) { tts.stop(); tts.shutdown(); tts = null; }
        } catch (Exception ignored) {}
        try {
            if (mediaPlayer != null) { mediaPlayer.stop(); mediaPlayer.release(); mediaPlayer = null; }
        } catch (Exception ignored) {}
    }

    public void setMyLocation(double lat, double lng) {
        myLat = lat;
        myLng = lng;
        hasMyLoc = true;
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
        String annId = safe(a.getId());

        // Badge
        String b = safe(a.getDisplayCategoryLabel());
        if (h.tvBadge != null)
            h.tvBadge.setText(b.isEmpty() ? safe(a.getCategory()) : b);

        if (h.tvTitle != null) h.tvTitle.setText(safe(a.getTitle()));
        if (h.tvDesc  != null) h.tvDesc.setText(safe(a.getDescription()));

        String nm = safe(a.getUserName());
        if (h.tvName   != null) h.tvName.setText(nm);
        if (h.tvAvatar != null)
            h.tvAvatar.setText(nm.isEmpty() ? "U" :
                    String.valueOf(Character.toUpperCase(nm.charAt(0))));

        if (h.tvTime != null) h.tvTime.setText(getRelativeTime(a.getTime()));

        // Expiry countdown
        if (h.tvExpiry != null) {
            String expiry = getExpiryText(a.getExpireAt());
            if (expiry.isEmpty()) {
                h.tvExpiry.setVisibility(View.GONE);
            } else {
                h.tvExpiry.setVisibility(View.VISIBLE);
                h.tvExpiry.setText(expiry);
                long remaining = a.getExpireAt() - System.currentTimeMillis();
                long hours = remaining / 3600000L;
                if (hours < 1)      h.tvExpiry.setTextColor(0xFFD32F2F);
                else if (hours < 6) h.tvExpiry.setTextColor(0xFFF57C00);
                else                h.tvExpiry.setTextColor(0xFF388E3C);
            }
        }

        // Audio badge
        if (h.tvAudioBadge != null) {
            boolean hasAudio = a.getAudioUrl() != null && !a.getAudioUrl().isEmpty();
            h.tvAudioBadge.setVisibility(hasAudio ? View.VISIBLE : View.GONE);
        }

        // Image
        String imgUrl = safe(a.getImageUrl());
        if (h.ivPhoto != null) {
            if (!imgUrl.isEmpty()) {
                h.ivPhoto.setVisibility(View.VISIBLE);
                Glide.with(context).load(imgUrl).centerCrop()
                        .placeholder(android.R.drawable.ic_menu_gallery).into(h.ivPhoto);
            } else {
                h.ivPhoto.setVisibility(View.GONE);
                h.ivPhoto.setImageDrawable(null);
            }
        }

        // Distance
        if (h.tvDistance != null) {
            if (hasMyLoc) {
                double d = DistanceUtil.distanceKm(myLat, myLng, a.getLat(), a.getLng());
                h.tvDistance.setText(String.format(Locale.getDefault(), "%.1f km away", d));
            } else {
                h.tvDistance.setText("Nearby");
            }
        }

        // ── Translate button ──────────────────────────────────────────────
        if (h.btnTranslate != null) {
            boolean isTranslated = Boolean.TRUE.equals(translatedState.get(annId));

            if (TranslationHelper.isBangla(safe(a.getTitle()) + safe(a.getDescription()))) {
                // Bangla post → show "EN" button
                h.btnTranslate.setVisibility(View.VISIBLE);
                h.btnTranslate.setText(isTranslated ? "🌐 বাংলা" : "🌐 English");
            } else {
                // English or mixed post → show "BN" button
                h.btnTranslate.setVisibility(View.VISIBLE);
                h.btnTranslate.setText(isTranslated ? "🌐 English" : "🌐 বাংলা");
            }

            // If already translated, show translated text
            if (isTranslated && translationCache.containsKey(annId)) {
                String[] cached = translationCache.get(annId);
                if (cached != null && cached.length >= 3) {
                    if (h.tvBadge != null && !cached[0].isEmpty()) h.tvBadge.setText(cached[0]);
                    if (h.tvTitle != null && !cached[1].isEmpty()) h.tvTitle.setText(cached[1]);
                    if (h.tvDesc  != null && !cached[2].isEmpty()) h.tvDesc.setText(cached[2]);
                }
            }

            h.btnTranslate.setOnClickListener(v -> {
                boolean currentlyTranslated = Boolean.TRUE.equals(translatedState.get(annId));

                if (currentlyTranslated) {
                    // ── Toggle back to original ──────────────────────────
                    translatedState.put(annId, false);
                    if (h.tvBadge != null) h.tvBadge.setText(b.isEmpty() ? safe(a.getCategory()) : b);
                    if (h.tvTitle != null) h.tvTitle.setText(safe(a.getTitle()));
                    if (h.tvDesc  != null) h.tvDesc.setText(safe(a.getDescription()));
                    if (TranslationHelper.isBangla(safe(a.getTitle()) + safe(a.getDescription()))) {
                        h.btnTranslate.setText("🌐 English");
                    } else {
                        h.btnTranslate.setText("🌐 বাংলা");
                    }

                } else {
                    // ── Translate ────────────────────────────────────────
                    if (translationCache.containsKey(annId)) {
                        // Already cached — show immediately
                        String[] cached = translationCache.get(annId);
                        translatedState.put(annId, true);
                        if (cached != null && cached.length >= 3) {
                            if (h.tvBadge != null && !cached[0].isEmpty()) h.tvBadge.setText(cached[0]);
                            if (h.tvTitle != null && !cached[1].isEmpty()) h.tvTitle.setText(cached[1]);
                            if (h.tvDesc  != null && !cached[2].isEmpty()) h.tvDesc.setText(cached[2]);
                        }
                        if (TranslationHelper.isBangla(safe(a.getTitle()) + safe(a.getDescription()))) {
                            h.btnTranslate.setText("🌐 বাংলা");
                        } else {
                            h.btnTranslate.setText("🌐 English");
                        }
                        return;
                    }

                    // Show loading state
                    h.btnTranslate.setEnabled(false);
                    h.btnTranslate.setText("⏳ Translating...");

                    // Determine direction
                    boolean isBanglaPost = TranslationHelper.isBangla(
                            safe(a.getTitle()) + safe(a.getDescription()));
                    String langPair = isBanglaPost ? "bn|en" : "en|bn";

                    String[] fields = {
                            b.isEmpty() ? safe(a.getCategory()) : b,  // category label
                            safe(a.getTitle()),                         // title
                            safe(a.getDescription())                    // description
                    };

                    TranslationHelper.translateFields(fields, langPair, results -> {
                        if (results != null) {
                            translationCache.put(annId, results);
                            translatedState.put(annId, true);
                            if (h.tvBadge != null && results.length > 0 && !results[0].isEmpty())
                                h.tvBadge.setText(results[0]);
                            if (h.tvTitle != null && results.length > 1 && !results[1].isEmpty())
                                h.tvTitle.setText(results[1]);
                            if (h.tvDesc  != null && results.length > 2 && !results[2].isEmpty())
                                h.tvDesc.setText(results[2]);
                        }
                        h.btnTranslate.setEnabled(true);
                        h.btnTranslate.setText(isBanglaPost ? "🌐 বাংলা" : "🌐 English");
                    });
                }
            });
        }

        if (showEditDelete) {
            // ── Profile mode ──────────────────────────────────────────────
            if (h.btnListen        != null) h.btnListen.setVisibility(View.GONE);
            if (h.btnChat          != null) h.btnChat.setVisibility(View.GONE);
            if (h.btnCall          != null) h.btnCall.setVisibility(View.GONE);
            if (h.btnDirection     != null) h.btnDirection.setVisibility(View.GONE);
            if (h.layoutEditDelete != null) h.layoutEditDelete.setVisibility(View.VISIBLE);

            if (h.btnEdit != null)
                h.btnEdit.setOnClickListener(v -> showEditDialog(a, h.getAdapterPosition()));

            if (h.btnDelete != null) {
                h.btnDelete.setOnClickListener(v ->
                        new AlertDialog.Builder(context)
                                .setTitle("Delete Post")
                                .setMessage("Are you sure you want to delete this post?")
                                .setPositiveButton("Delete", (d, w) -> deletePost(a, h.getAdapterPosition()))
                                .setNegativeButton("Cancel", null)
                                .show());
            }

            if (h.btnDetails != null) h.btnDetails.setOnClickListener(v -> openDetail(a));

        } else {
            // ── Feed mode ─────────────────────────────────────────────────
            if (h.btnListen        != null) h.btnListen.setVisibility(View.VISIBLE);
            if (h.btnChat          != null) h.btnChat.setVisibility(View.VISIBLE);
            if (h.btnCall          != null) h.btnCall.setVisibility(View.VISIBLE);
            if (h.btnDirection     != null) h.btnDirection.setVisibility(View.VISIBLE);
            if (h.layoutEditDelete != null) h.layoutEditDelete.setVisibility(View.GONE);

            if (h.btnCall != null) {
                h.btnCall.setOnClickListener(v -> {
                    String phone = safe(a.getPhone());
                    if (phone.isEmpty()) {
                        Toast.makeText(context, "No phone number", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    context.startActivity(new Intent(Intent.ACTION_DIAL,
                            Uri.parse("tel:" + phone)));
                });
            }

            if (h.btnChat != null) {
                h.btnChat.setOnClickListener(v -> {
                    String postOwnerUid = safe(a.getUserId());
                    String myUid = safe(FirebaseAuth.getInstance().getUid());
                    if (postOwnerUid.isEmpty()) {
                        Toast.makeText(context, "Cannot start chat", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (myUid.equals(postOwnerUid)) {
                        Toast.makeText(context, "Cannot chat with yourself", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Intent i = new Intent(context, ChatActivity.class);
                    i.putExtra(ChatActivity.EXTRA_OTHER_UID,  postOwnerUid);
                    i.putExtra(ChatActivity.EXTRA_OTHER_NAME, safe(a.getUserName()));
                    context.startActivity(i);
                });
            }

            if (h.btnDirection != null) {
                h.btnDirection.setOnClickListener(v -> openDirections(a.getLat(), a.getLng()));
            }

            if (h.btnListen != null) {
                h.btnListen.setOnClickListener(v -> playAudioOrTts(a));
            }
        }

        // Card click → Detail
        if (!disableCardClick) {
            h.itemView.setOnClickListener(v -> openDetail(a));
        } else {
            h.itemView.setOnClickListener(null);
        }
    }

    private void openDirections(double destLat, double destLng) {
        Uri gmmIntentUri = Uri.parse(
                "google.navigation:q=" + destLat + "," + destLng + "&mode=d");
        Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
        mapIntent.setPackage("com.google.android.apps.maps");

        if (mapIntent.resolveActivity(context.getPackageManager()) != null) {
            context.startActivity(mapIntent);
        } else {
            Uri web = Uri.parse("https://www.google.com/maps/dir/?api=1&destination="
                    + destLat + "," + destLng + "&travelmode=driving");
            context.startActivity(new Intent(Intent.ACTION_VIEW, web));
        }
    }

    private void playAudioOrTts(Announcement a) {
        String audioUrl = safe(a.getAudioUrl());
        if (!audioUrl.isEmpty()) {
            playRemoteAudio(audioUrl);
        } else {
            String speak = safe(a.getTitle()) + ". " + safe(a.getDescription());
            if (tts != null) tts.speak(speak, TextToSpeech.QUEUE_FLUSH, null, "tc_announce");
        }
    }

    private void playRemoteAudio(String url) {
        if (mediaPlayer != null) {
            try { mediaPlayer.stop(); mediaPlayer.release(); } catch (Exception ignored) {}
            mediaPlayer = null;
        }

        Toast.makeText(context, "Loading audio...", Toast.LENGTH_SHORT).show();

        mediaPlayer = new MediaPlayer();
        try {
            mediaPlayer.setDataSource(url);
            mediaPlayer.setOnPreparedListener(mp -> {
                mp.start();
                Toast.makeText(context, "▶ Playing audio", Toast.LENGTH_SHORT).show();
            });
            mediaPlayer.setOnCompletionListener(mp -> {
                mp.release();
                mediaPlayer = null;
            });
            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                Toast.makeText(context, "Playback error", Toast.LENGTH_SHORT).show();
                mp.release();
                mediaPlayer = null;
                return true;
            });
            mediaPlayer.prepareAsync();
        } catch (Exception e) {
            Toast.makeText(context, "Cannot play audio", Toast.LENGTH_SHORT).show();
            mediaPlayer = null;
        }
    }

    private String getExpiryText(long expireAt) {
        if (expireAt <= 0) return "";
        long remaining = expireAt - System.currentTimeMillis();
        if (remaining <= 0) return "Expired";
        long hours   = remaining / 3600000L;
        long minutes = (remaining % 3600000L) / 60000L;
        if (hours >= 24) { long days = hours / 24; return "⏳ Expires in " + days + " day" + (days > 1 ? "s" : ""); }
        if (hours >= 1)  return "⏳ Expires in " + hours + " hr" + (hours > 1 ? "s" : "");
        if (minutes >= 1) return "⏳ Expires in " + minutes + " min";
        return "⏳ Expiring soon";
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
        intent.putExtra(AnnouncementDetailActivity.EXTRA_AUDIO_URL, safe(a.getAudioUrl()));
        intent.putExtra(AnnouncementDetailActivity.EXTRA_USER_NAME, safe(a.getUserName()));
        intent.putExtra(AnnouncementDetailActivity.EXTRA_DISTANCE,  dist);
        intent.putExtra(AnnouncementDetailActivity.EXTRA_TIME,      getRelativeTime(a.getTime()));
        intent.putExtra(AnnouncementDetailActivity.EXTRA_OTHER_UID, safe(a.getUserId()));
        intent.putExtra(AnnouncementDetailActivity.EXTRA_LAT,       a.getLat());
        intent.putExtra(AnnouncementDetailActivity.EXTRA_LNG,       a.getLng());
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
                            .child(a.getId()).child("title").setValue(newTitle);
                    FirebaseDatabase.getInstance()
                            .getReference(Constants.DB_ANNOUNCEMENTS)
                            .child(a.getId()).child("description").setValue(newDesc);
                    a.setTitle(newTitle);
                    a.setDescription(newDesc);
                    // Clear translation cache for this post since content changed
                    translationCache.remove(a.getId());
                    translatedState.remove(a.getId());
                    int currentPos = items.indexOf(a);
                    if (currentPos >= 0) notifyItemChanged(currentPos);
                    Toast.makeText(context, "Updated ✅", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deletePost(Announcement a, int pos) {
        if (a.getId() == null) return;
        if (pos < 0 || pos >= items.size()) return;

        FirebaseDatabase.getInstance()
                .getReference(Constants.DB_ANNOUNCEMENTS)
                .child(a.getId())
                .removeValue()
                .addOnSuccessListener(v -> {
                    int currentPos = items.indexOf(a);
                    if (currentPos >= 0) {
                        items.remove(currentPos);
                        translationCache.remove(a.getId());
                        translatedState.remove(a.getId());
                        notifyItemRemoved(currentPos);
                        notifyItemRangeChanged(currentPos, items.size());
                    }
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

    // ─── ViewHolder ──────────────────────────────────────────────────────────
    public static class VH extends RecyclerView.ViewHolder {
        ImageView ivPhoto;
        TextView  tvBadge, tvTitle, tvDesc, tvAvatar, tvName, tvDistance, tvTime;
        TextView  tvExpiry, tvAudioBadge;
        Button    btnListen, btnChat, btnCall, btnDirection;
        // ✅ NEW: Translate toggle button
        Button    btnTranslate;
        View      layoutEditDelete;
        Button    btnEdit, btnDelete, btnDetails;

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
            tvExpiry         = itemView.findViewById(R.id.tvExpiry);
            tvAudioBadge     = itemView.findViewById(R.id.tvAudioBadge);
            btnListen        = itemView.findViewById(R.id.btnListen);
            btnChat          = itemView.findViewById(R.id.btnChat);
            btnCall          = itemView.findViewById(R.id.btnCall);
            btnDirection     = itemView.findViewById(R.id.btnDirection);
            btnTranslate     = itemView.findViewById(R.id.btnTranslate); // ✅ NEW
            layoutEditDelete = itemView.findViewById(R.id.layoutEditDelete);
            btnEdit          = itemView.findViewById(R.id.btnEdit);
            btnDelete        = itemView.findViewById(R.id.btnDelete);
            btnDetails       = itemView.findViewById(R.id.btnDetails);
        }
    }

    private String safe(String s) { return s == null ? "" : s.trim(); }
}