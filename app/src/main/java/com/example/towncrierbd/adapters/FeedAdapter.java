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
import com.example.towncrierbd.utils.AppStrings;
import com.example.towncrierbd.utils.Constants;
import com.example.towncrierbd.utils.DistanceUtil;
import com.example.towncrierbd.utils.LanguageManager;
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

    private final Map<String, Boolean>   translatedState   = new HashMap<>();
    private final Map<String, String[]>  translationCache  = new HashMap<>();

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
        try { if (tts != null) { tts.stop(); tts.shutdown(); tts = null; } } catch (Exception ignored) {}
        try { if (mediaPlayer != null) { mediaPlayer.stop(); mediaPlayer.release(); mediaPlayer = null; } } catch (Exception ignored) {}
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
        AppStrings s = AppStrings.get(context);
        String annId = safe(a.getId());


        // Badge — show in app's current language if we have a translation cached
        String badgeEn = safe(a.getDisplayCategoryLabel());
        if (badgeEn.isEmpty()) badgeEn = safe(a.getCategory());
        String badgeDisplay = LanguageManager.isEnglish(context)
                ? badgeEn
                : getCategoryBn(badgeEn);
        if (h.tvBadge != null) h.tvBadge.setText(badgeDisplay);

        if (h.tvTitle != null) h.tvTitle.setText(safe(a.getTitle()));
        if (h.tvDesc  != null) h.tvDesc.setText(safe(a.getDescription()));

        String nm = safe(a.getUserName());
        if (h.tvName   != null) h.tvName.setText(nm);
        if (h.tvAvatar != null)
            h.tvAvatar.setText(nm.isEmpty() ? "U" : String.valueOf(Character.toUpperCase(nm.charAt(0))));

        if (h.tvTime != null) h.tvTime.setText(getRelativeTime(a.getTime(), s));

        // Expiry
        if (h.tvExpiry != null) {
            String expiry = getExpiryText(a.getExpireAt(), s);
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
            if (hasAudio) h.tvAudioBadge.setText(s.cardAudioBadge());
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
                String fmt = String.format(Locale.getDefault(), "%.1f", d);
                h.tvDistance.setText(s.cardKmAway(fmt));
            } else {
                h.tvDistance.setText(s.nearby());
            }
        }

        // ── Translate button ──────────────────────────────────────────────
        if (h.btnTranslate != null) {
            boolean isTranslated = Boolean.TRUE.equals(translatedState.get(annId));
            boolean isBanglaPost = TranslationHelper.isBangla(safe(a.getTitle()) + safe(a.getDescription()));

            // Button label: show what it WILL translate TO
            if (isBanglaPost) {
                h.btnTranslate.setText(isTranslated ? s.cardTranslateToBn() : s.cardTranslateToEn());
            } else {
                h.btnTranslate.setText(isTranslated ? s.cardTranslateToEn() : s.cardTranslateToBn());
            }
            h.btnTranslate.setVisibility(View.VISIBLE);

            // Show cached translation if active
            if (isTranslated && translationCache.containsKey(annId)) {
                String[] cached = translationCache.get(annId);
                if (cached != null && cached.length >= 3) {
                    if (h.tvBadge != null && !cached[0].isEmpty()) h.tvBadge.setText(cached[0]);
                    if (h.tvTitle != null && !cached[1].isEmpty()) h.tvTitle.setText(cached[1]);
                    if (h.tvDesc  != null && !cached[2].isEmpty()) h.tvDesc.setText(cached[2]);
                }
            }

            final String finalBadgeEn = badgeEn;
            h.btnTranslate.setOnClickListener(v -> {
                boolean currentlyTranslated = Boolean.TRUE.equals(translatedState.get(annId));

                if (currentlyTranslated) {
                    // Toggle back to original
                    translatedState.put(annId, false);
                    String badgeOrig = LanguageManager.isEnglish(context)
                            ? finalBadgeEn : getCategoryBn(finalBadgeEn);
                    if (h.tvBadge != null) h.tvBadge.setText(badgeOrig);
                    if (h.tvTitle != null) h.tvTitle.setText(safe(a.getTitle()));
                    if (h.tvDesc  != null) h.tvDesc.setText(safe(a.getDescription()));
                    h.btnTranslate.setText(isBanglaPost ? s.cardTranslateToEn() : s.cardTranslateToBn());

                } else {
                    if (translationCache.containsKey(annId)) {
                        String[] cached = translationCache.get(annId);
                        translatedState.put(annId, true);
                        if (cached != null && cached.length >= 3) {
                            if (h.tvBadge != null && !cached[0].isEmpty()) h.tvBadge.setText(cached[0]);
                            if (h.tvTitle != null && !cached[1].isEmpty()) h.tvTitle.setText(cached[1]);
                            if (h.tvDesc  != null && !cached[2].isEmpty()) h.tvDesc.setText(cached[2]);
                        }
                        h.btnTranslate.setText(isBanglaPost ? s.cardTranslateToBn() : s.cardTranslateToEn());
                        return;
                    }

                    h.btnTranslate.setEnabled(false);
                    h.btnTranslate.setText(s.cardTranslating());

                    String langPair = isBanglaPost ? "bn|en" : "en|bn";
                    String[] fields = {
                            finalBadgeEn,
                            safe(a.getTitle()),
                            safe(a.getDescription())
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
                        h.btnTranslate.setText(isBanglaPost ? s.cardTranslateToBn() : s.cardTranslateToEn());
                    });
                }
            });
        }

        if (showEditDelete) {
            // Profile mode
            if (h.btnListen        != null) h.btnListen.setVisibility(View.GONE);
            if (h.btnChat          != null) h.btnChat.setVisibility(View.GONE);
            if (h.btnCall          != null) h.btnCall.setVisibility(View.GONE);
            if (h.btnDirection     != null) h.btnDirection.setVisibility(View.GONE);
            if (h.layoutEditDelete != null) h.layoutEditDelete.setVisibility(View.VISIBLE);

            if (h.btnEdit   != null) h.btnEdit.setText(s.cardEdit());
            if (h.btnDelete != null) h.btnDelete.setText(s.cardDelete());
            if (h.btnDetails!= null) h.btnDetails.setText(s.cardDetails());

            if (h.btnEdit   != null) h.btnEdit.setOnClickListener(v -> showEditDialog(a, h.getAdapterPosition()));
            if (h.btnDelete != null) {
                h.btnDelete.setOnClickListener(v ->
                        new AlertDialog.Builder(context)
                                .setTitle(s.cardDeleteConfirmTitle())
                                .setMessage(s.cardDeleteConfirmMsg())
                                .setPositiveButton(s.delete(), (d, w) -> deletePost(a, h.getAdapterPosition()))
                                .setNegativeButton(s.cancel(), null)
                                .show());
            }
            if (h.btnDetails != null) h.btnDetails.setOnClickListener(v -> openDetail(a));

        } else {
            // Feed mode
            if (h.btnListen        != null) { h.btnListen.setVisibility(View.VISIBLE); h.btnListen.setText(s.cardListen()); }
            if (h.btnChat          != null) { h.btnChat.setVisibility(View.VISIBLE);   h.btnChat.setText(s.cardChat()); }
            if (h.btnCall          != null) { h.btnCall.setVisibility(View.VISIBLE);   h.btnCall.setText(s.cardCall()); }
            if (h.btnDirection     != null) { h.btnDirection.setVisibility(View.VISIBLE); h.btnDirection.setText(s.cardDirection()); }
            if (h.layoutEditDelete != null) h.layoutEditDelete.setVisibility(View.GONE);

            if (h.btnCall != null) {
                h.btnCall.setOnClickListener(v -> {
                    String phone = safe(a.getPhone());
                    if (phone.isEmpty()) { Toast.makeText(context, s.cardNoPhone(), Toast.LENGTH_SHORT).show(); return; }
                    context.startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone)));
                });
            }

            if (h.btnChat != null) {
                h.btnChat.setOnClickListener(v -> {
                    String postOwnerUid = safe(a.getUserId());
                    String myUid = safe(FirebaseAuth.getInstance().getUid());
                    if (postOwnerUid.isEmpty()) { Toast.makeText(context, s.cardNoChatEmpty(), Toast.LENGTH_SHORT).show(); return; }
                    if (myUid.equals(postOwnerUid)) { Toast.makeText(context, s.cardNoChatSelf(), Toast.LENGTH_SHORT).show(); return; }
                    Intent i = new Intent(context, ChatActivity.class);
                    i.putExtra(ChatActivity.EXTRA_OTHER_UID,  postOwnerUid);
                    i.putExtra(ChatActivity.EXTRA_OTHER_NAME, safe(a.getUserName()));
                    context.startActivity(i);
                });
            }

            if (h.btnDirection != null) h.btnDirection.setOnClickListener(v -> openDirections(a.getLat(), a.getLng()));
            if (h.btnListen    != null) h.btnListen.setOnClickListener(v -> playAudioOrTts(a));
        }

        if (!disableCardClick) {
            h.itemView.setOnClickListener(v -> openDetail(a));
        } else {
            h.itemView.setOnClickListener(null);
        }
    }

    /** Try to get Bangla category name; fall back to English if not mapped */
    private String getCategoryBn(String englishKey) {
        String bn = com.example.towncrierbd.utils.CategoryConfig.MAIN_BN.get(englishKey);
        return (bn != null) ? bn : englishKey;
    }

    private void openDirections(double destLat, double destLng) {
        Uri gmmIntentUri = Uri.parse("google.navigation:q=" + destLat + "," + destLng + "&mode=d");
        Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
        mapIntent.setPackage("com.google.android.apps.maps");
        if (mapIntent.resolveActivity(context.getPackageManager()) != null) {
            context.startActivity(mapIntent);
        } else {
            Uri web = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=" + destLat + "," + destLng + "&travelmode=driving");
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
        AppStrings s = AppStrings.get(context);
        if (mediaPlayer != null) {
            try { mediaPlayer.stop(); mediaPlayer.release(); } catch (Exception ignored) {}
            mediaPlayer = null;
        }
        Toast.makeText(context, s.detailLoadingAudio(), Toast.LENGTH_SHORT).show();
        mediaPlayer = new MediaPlayer();
        try {
            mediaPlayer.setDataSource(url);
            mediaPlayer.setOnPreparedListener(mp -> {
                mp.start();
                Toast.makeText(context, s.detailPlayingAudio(), Toast.LENGTH_SHORT).show();
            });
            mediaPlayer.setOnCompletionListener(mp -> { mp.release(); mediaPlayer = null; });
            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                Toast.makeText(context, s.detailPlaybackError(), Toast.LENGTH_SHORT).show();
                mp.release(); mediaPlayer = null; return true;
            });
            mediaPlayer.prepareAsync();
        } catch (Exception e) {
            Toast.makeText(context, s.detailCannotPlay(), Toast.LENGTH_SHORT).show();
            mediaPlayer = null;
        }
    }

    private String getExpiryText(long expireAt, AppStrings s) {
        if (expireAt <= 0) return "";
        long remaining = expireAt - System.currentTimeMillis();
        if (remaining <= 0) return s.expired();
        long hours   = remaining / 3600000L;
        long minutes = (remaining % 3600000L) / 60000L;
        if (hours >= 24) { long days = hours / 24; return s.expiryDays(days); }
        if (hours >= 1)  return s.expiryHours(hours);
        if (minutes >= 1) return s.expiryMinutes(minutes);
        return s.expirySoon();
    }

    private void openDetail(Announcement a) {
        AppStrings s = AppStrings.get(context);
        String dist = "";
        if (hasMyLoc) {
            double d = DistanceUtil.distanceKm(myLat, myLng, a.getLat(), a.getLng());
            String fmt = String.format(Locale.getDefault(), "%.1f", d);
            dist = s.cardKmAway(fmt);
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
        intent.putExtra(AnnouncementDetailActivity.EXTRA_TIME,      getRelativeTime(a.getTime(), s));
        intent.putExtra(AnnouncementDetailActivity.EXTRA_OTHER_UID, safe(a.getUserId()));
        intent.putExtra(AnnouncementDetailActivity.EXTRA_LAT,       a.getLat());
        intent.putExtra(AnnouncementDetailActivity.EXTRA_LNG,       a.getLng());
        context.startActivity(intent);
    }

    private void showEditDialog(Announcement a, int pos) {
        AppStrings s = AppStrings.get(context);

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 16, 48, 0);

        TextView labelTitle = new TextView(context);
        labelTitle.setText(s.cardEditLabelTitle());
        labelTitle.setTextSize(14);
        EditText etNewTitle = new EditText(context);
        etNewTitle.setText(a.getTitle());

        TextView labelDesc = new TextView(context);
        labelDesc.setText(s.cardEditLabelDesc());
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
                .setTitle(s.cardEditTitle())
                .setView(layout)
                .setPositiveButton(s.save(), (d, w) -> {
                    String newTitle = etNewTitle.getText().toString().trim();
                    String newDesc  = etNewDesc.getText().toString().trim();
                    if (newTitle.isEmpty()) {
                        Toast.makeText(context, s.cardTitleEmpty(), Toast.LENGTH_SHORT).show();
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
                    translationCache.remove(a.getId());
                    translatedState.remove(a.getId());
                    int currentPos = items.indexOf(a);
                    if (currentPos >= 0) notifyItemChanged(currentPos);
                    Toast.makeText(context, s.cardUpdated(), Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(s.cancel(), null)
                .show();
    }

    private void deletePost(Announcement a, int pos) {
        AppStrings s = AppStrings.get(context);
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
                    Toast.makeText(context, s.cardDeleted(), Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(context, s.cardDeleteFailed(), Toast.LENGTH_SHORT).show());
    }

    @Override public int getItemCount() { return items.size(); }

    private String getRelativeTime(long timeMillis, AppStrings s) {
        if (timeMillis == 0) return "";
        long diff    = System.currentTimeMillis() - timeMillis;
        long minutes = diff / 60000;
        long hours   = minutes / 60;
        long days    = hours / 24;
        if (minutes < 1)  return s.justNow();
        if (minutes < 60) return s.timeMinAgo(minutes);
        if (hours < 24)   return s.timeHrAgo(hours);
        return s.timeDayAgo(days);
    }

    public static class VH extends RecyclerView.ViewHolder {
        ImageView ivPhoto;
        TextView  tvBadge, tvTitle, tvDesc, tvAvatar, tvName, tvDistance, tvTime;
        TextView  tvExpiry, tvAudioBadge;
        Button    btnListen, btnChat, btnCall, btnDirection;
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
            btnTranslate     = itemView.findViewById(R.id.btnTranslate);
            layoutEditDelete = itemView.findViewById(R.id.layoutEditDelete);
            btnEdit          = itemView.findViewById(R.id.btnEdit);
            btnDelete        = itemView.findViewById(R.id.btnDelete);
            btnDetails       = itemView.findViewById(R.id.btnDetails);
        }
    }

    private String safe(String s) { return s == null ? "" : s.trim(); }
}