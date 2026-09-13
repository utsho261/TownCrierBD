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
import androidx.viewpager2.widget.ViewPager2;

import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import com.bumptech.glide.Glide;
import com.example.towncrierbd.R;
import com.example.towncrierbd.activities.AddAnnouncementActivity;
import com.example.towncrierbd.activities.AnnouncementDetailActivity;
import com.example.towncrierbd.activities.ChatActivity;
import com.example.towncrierbd.models.Announcement;
import com.example.towncrierbd.utils.AppStrings;
import com.example.towncrierbd.utils.CategoryConfig;
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
import java.util.concurrent.atomic.AtomicInteger;

public class FeedAdapter extends RecyclerView.Adapter<FeedAdapter.VH> {

    private final Context context;
    private final List<Announcement> items = new ArrayList<>();

    private double myLat = 0, myLng = 0;
    private boolean hasMyLoc        = false;
    private boolean showEditDelete   = false;
    private boolean disableCardClick = false;

    private TextToSpeech tts;
    private MediaPlayer  mediaPlayer;

    /**
     * Per-item translation state.
     * Key  = announcement id
     * ONLY title + description are ever translated.
     * Category badge, expiry, distance, time — NEVER translated here.
     */
    private final Map<String, TranslateState> translateStateMap = new HashMap<>();

    private static class TranslateState {
        boolean isTranslated    = false;
        boolean isLoading       = false;
        String  translatedTitle = null;
        String  translatedDesc  = null;
        // Store the detected lang-pair so button label is always accurate
        String  detectedLangPair = null; // e.g. "bn|en" or "en|bn"
    }

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
        if (data == null || data.isEmpty()) translateStateMap.clear();
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

        // ── Category badge ─────────────────────────────────────────────────
        // Always follows APP language — never changes with translate button.
        String badgeEn = safe(a.getDisplayCategoryLabel());
        if (badgeEn.isEmpty()) badgeEn = safe(a.getCategory());
        String badgeDisplay = LanguageManager.isEnglish(context)
                ? badgeEn
                : getCategoryBn(badgeEn);
        if (h.tvBadge != null) h.tvBadge.setText(badgeDisplay);

        // ── Translate state for this item ──────────────────────────────────
        String itemId = safe(a.getId());
        if (itemId.isEmpty()) itemId = String.valueOf(pos);
        TranslateState state = translateStateMap.get(itemId);
        if (state == null) {
            state = new TranslateState();
            // Pre-detect language pair from title so button label is correct immediately
            state.detectedLangPair = TranslationHelper.detectLangPair(safe(a.getTitle()));
            translateStateMap.put(itemId, state);
        }

        // ── Title & Description (show translated or original) ──────────────
        String displayTitle = (state.isTranslated && state.translatedTitle != null)
                ? state.translatedTitle : safe(a.getTitle());
        String displayDesc  = (state.isTranslated && state.translatedDesc  != null)
                ? state.translatedDesc  : safe(a.getDescription());

        if (h.tvTitle != null) h.tvTitle.setText(displayTitle);
        if (h.tvDesc  != null) {
            if (displayDesc.isEmpty()) {
                h.tvDesc.setVisibility(View.GONE);
            } else {
                h.tvDesc.setVisibility(View.VISIBLE);
                h.tvDesc.setText(displayDesc);
            }
        }

        // ── User info ──────────────────────────────────────────────────────
        String nm = safe(a.getUserName());
        if (h.tvName   != null) h.tvName.setText(nm);
        if (h.tvAvatar != null)
            h.tvAvatar.setText(nm.isEmpty() ? "U" : String.valueOf(Character.toUpperCase(nm.charAt(0))));

        // ── Time ───────────────────────────────────────────────────────────
        if (h.tvTime != null) h.tvTime.setText(getRelativeTime(a.getTime(), s));

        // ── Expiry ─────────────────────────────────────────────────────────
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

        // ── Audio badge ────────────────────────────────────────────────────
        if (h.tvAudioBadge != null) {
            boolean hasAudio = a.getAudioUrl() != null && !a.getAudioUrl().isEmpty();
            h.tvAudioBadge.setVisibility(hasAudio ? View.VISIBLE : View.GONE);
            if (hasAudio) h.tvAudioBadge.setText(s.cardAudioBadge());
        }

        // ── Image Slider ──────────────────────────────────────────────────
        List<String> imageUrls = a.getImageUrls();
        if (imageUrls == null) {
            imageUrls = new ArrayList<>();
            if (a.getImageUrl() != null && !a.getImageUrl().isEmpty()) {
                imageUrls.add(a.getImageUrl());
            }
        }

        if (h.flImageSlider != null) {
            if (!imageUrls.isEmpty()) {
                h.flImageSlider.setVisibility(View.VISIBLE);
                ImageSliderAdapter sliderAdapter = new ImageSliderAdapter(imageUrls, v -> {
                    if (!disableCardClick) openDetail(a);
                });
                h.vpImageSlider.setAdapter(sliderAdapter);

                if (imageUrls.size() > 1) {
                    h.tvImageIndex.setVisibility(View.VISIBLE);
                    h.tvImageIndex.setText("1/" + imageUrls.size());
                    final List<String> finalUrls = imageUrls;
                    h.vpImageSlider.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
                        @Override
                        public void onPageSelected(int position) {
                            h.tvImageIndex.setText((position + 1) + "/" + finalUrls.size());
                        }
                    });
                } else {
                    h.tvImageIndex.setVisibility(View.GONE);
                }
            } else {
                h.flImageSlider.setVisibility(View.GONE);
            }
        }

        // ── Distance ───────────────────────────────────────────────────────
        if (h.tvDistance != null) {
            if (hasMyLoc) {
                double d = DistanceUtil.distanceKm(myLat, myLng, a.getLat(), a.getLng());
                String fmt = String.format(Locale.getDefault(), "%.1f", d);
                h.tvDistance.setText(s.cardKmAway(fmt));
            } else {
                h.tvDistance.setText(s.nearby());
            }
        }

        // ── Translate button ───────────────────────────────────────────────
        // ONLY title + description are translated. Everything else is untouched.
        if (h.btnTranslate != null) {
            updateTranslateBtnLabel(h.btnTranslate, state, s);

            final String finalItemId      = itemId;
            final TranslateState finalState = state;

            h.btnTranslate.setOnClickListener(v -> {
                if (finalState.isLoading) return; // prevent double-tap

                AppStrings as = AppStrings.get(context);

                if (finalState.isTranslated) {
                    // ── Revert to original ─────────────────────────────────
                    finalState.isTranslated = false;
                    if (h.tvTitle != null) h.tvTitle.setText(safe(a.getTitle()));
                    if (h.tvDesc  != null) h.tvDesc.setText(safe(a.getDescription()));
                    updateTranslateBtnLabel(h.btnTranslate, finalState, as);

                } else if (finalState.translatedTitle != null) {
                    // ── Use cached translation ─────────────────────────────
                    finalState.isTranslated = true;
                    if (h.tvTitle != null) h.tvTitle.setText(finalState.translatedTitle);
                    if (h.tvDesc  != null) h.tvDesc.setText(
                            finalState.translatedDesc != null ? finalState.translatedDesc : "");
                    updateTranslateBtnLabel(h.btnTranslate, finalState, as);

                } else {
                    // ── Fetch translation — title and desc SEPARATELY ──────
                    finalState.isLoading = true;
                    h.btnTranslate.setText(as.cardTranslating());
                    h.btnTranslate.setEnabled(false);

                    final String rawTitle = safe(a.getTitle());
                    final String rawDesc  = safe(a.getDescription());

                    final String langPair = finalState.detectedLangPair != null
                            ? finalState.detectedLangPair
                            : TranslationHelper.detectLangPair(rawTitle);

                    // ✅ FIX: translate title and description independently
                    // so each result lands in its own TextView — no separator splitting needed.
                    final String[] results = new String[2]; // [0]=title, [1]=desc
                    final AtomicInteger doneCount = new AtomicInteger(0);

                    Runnable onBothDone = () -> {
                        finalState.isLoading = false;
                        finalState.translatedTitle = results[0] != null ? results[0] : rawTitle;
                        finalState.translatedDesc  = results[1] != null ? results[1] : rawDesc;
                        finalState.isTranslated = true;

                        if (h.tvTitle != null) h.tvTitle.setText(finalState.translatedTitle);
                        if (h.tvDesc  != null) h.tvDesc.setText(finalState.translatedDesc);
                        h.btnTranslate.setEnabled(true);
                        AppStrings as2 = AppStrings.get(context);
                        updateTranslateBtnLabel(h.btnTranslate, finalState, as2);
                    };

                    // Translate title
                    TranslationHelper.translateWithLangPair(
                            rawTitle, langPair,
                            new TranslationHelper.TranslateCallback() {
                                @Override
                                public void onResult(String result) {
                                    results[0] = result;
                                    if (doneCount.incrementAndGet() == 2) onBothDone.run();
                                }
                                @Override
                                public void onError(String originalText) {
                                    results[0] = rawTitle; // fallback
                                    if (doneCount.incrementAndGet() == 2) onBothDone.run();
                                    AppStrings as2 = AppStrings.get(context);
                                    Toast.makeText(context, as2.cardTranslateFail(), Toast.LENGTH_SHORT).show();
                                }
                            }
                    );

                    // Translate description
                    TranslationHelper.translateWithLangPair(
                            rawDesc, langPair,
                            new TranslationHelper.TranslateCallback() {
                                @Override
                                public void onResult(String result) {
                                    results[1] = result;
                                    if (doneCount.incrementAndGet() == 2) onBothDone.run();
                                }
                                @Override
                                public void onError(String originalText) {
                                    results[1] = rawDesc; // fallback
                                    if (doneCount.incrementAndGet() == 2) onBothDone.run();
                                }
                            }
                    );
                }
            });
        }

        // ── Action buttons (feed vs profile mode) ─────────────────────────
        if (showEditDelete) {
            if (h.btnListen        != null) h.btnListen.setVisibility(View.GONE);
            if (h.btnChat          != null) h.btnChat.setVisibility(View.GONE);
            if (h.btnCall          != null) h.btnCall.setVisibility(View.GONE);
            if (h.btnDirection     != null) h.btnDirection.setVisibility(View.GONE);
            if (h.layoutEditDelete != null) h.layoutEditDelete.setVisibility(View.VISIBLE);
            // Hide translate in profile/edit mode — not needed there
            if (h.btnTranslate     != null) h.btnTranslate.setVisibility(View.GONE);

            // Active status badge
            if (h.tvActiveStatus != null) {
                h.tvActiveStatus.setVisibility(View.VISIBLE);
                long now = System.currentTimeMillis();
                boolean isExpired = a.getExpireAt() > 0 && now > a.getExpireAt();
                if (!a.isActive()) {
                    h.tvActiveStatus.setText(s.cardStatusInactive());
                    h.tvActiveStatus.setTextColor(0xFFD32F2F);
                    h.tvActiveStatus.setBackground(roundedStatusBg(0xFFFFEBEE, 6));
                } else if (isExpired) {
                    h.tvActiveStatus.setText(s.cardStatusExpired());
                    h.tvActiveStatus.setTextColor(0xFFC62828);
                    h.tvActiveStatus.setBackground(roundedStatusBg(0xFFFFEBEE, 6));
                } else {
                    h.tvActiveStatus.setText(s.cardStatusActive());
                    h.tvActiveStatus.setTextColor(0xFF2E7D32);
                    h.tvActiveStatus.setBackground(roundedStatusBg(0xFFE8F5E9, 6));
                }
            }

            // Toggle active / deactive button
            if (h.btnToggleActive != null) {
                long now = System.currentTimeMillis();
                boolean isExpired = a.getExpireAt() > 0 && now > a.getExpireAt();
                boolean isCurrentlyLive = a.isActive() && !isExpired;

                if (isCurrentlyLive) {
                    h.btnToggleActive.setText(s.cardDeactivate());
                    h.btnToggleActive.setBackgroundTintList(ColorStateList.valueOf(0xFFF57C00));
                    h.btnToggleActive.setOnClickListener(v -> {
                        if (a.getId() == null) return;
                        FirebaseDatabase.getInstance().getReference(Constants.DB_ANNOUNCEMENTS)
                                .child(a.getId()).child("active").setValue(false);
                        a.setActive(false);
                        int adapterPos = h.getAdapterPosition();
                        if (adapterPos >= 0 && adapterPos < items.size()) notifyItemChanged(adapterPos);
                        Toast.makeText(context, s.cardPostDeactivated(), Toast.LENGTH_SHORT).show();
                    });
                } else {
                    h.btnToggleActive.setText(s.cardActivate());
                    h.btnToggleActive.setBackgroundTintList(ColorStateList.valueOf(0xFF2E7D32));
                    h.btnToggleActive.setOnClickListener(v -> showReactivateDialog(a, h.getAdapterPosition()));
                }
            }

            if (h.btnEdit   != null) h.btnEdit.setText(s.cardEdit());
            if (h.btnDelete != null) h.btnDelete.setText(s.cardDelete());
            if (h.btnDetails!= null) h.btnDetails.setText(s.cardDetails());

            if (h.btnEdit != null) {
                h.btnEdit.setOnClickListener(v -> {
                    Intent intent = new Intent(context, AddAnnouncementActivity.class);
                    intent.putExtra(AddAnnouncementActivity.EXTRA_EDIT_ID, a.getId());
                    context.startActivity(intent);
                });
            }
            if (h.btnDelete != null) {
                h.btnDelete.setOnClickListener(v ->
                        new AlertDialog.Builder(context)
                                .setTitle(s.cardDeleteConfirmTitle())
                                .setMessage(s.cardDeleteConfirmMsg())
                                .setPositiveButton(s.delete(),
                                        (d, w) -> deletePost(a, h.getAdapterPosition()))
                                .setNegativeButton(s.cancel(), null)
                                .show());
            }
            if (h.btnDetails != null) h.btnDetails.setOnClickListener(v -> openDetail(a));

        } else {
            if (h.tvActiveStatus != null) h.tvActiveStatus.setVisibility(View.GONE);
            if (h.btnListen    != null) { h.btnListen.setVisibility(View.VISIBLE);    h.btnListen.setText(s.cardListen()); }
            if (h.btnChat      != null) { h.btnChat.setVisibility(View.VISIBLE);      h.btnChat.setText(s.cardChat()); }
            if (h.btnCall      != null) { h.btnCall.setVisibility(View.VISIBLE);      h.btnCall.setText(s.cardCall()); }
            if (h.btnDirection != null) { h.btnDirection.setVisibility(View.VISIBLE); h.btnDirection.setText(s.cardDirection()); }
            if (h.layoutEditDelete != null) h.layoutEditDelete.setVisibility(View.GONE);
            if (h.btnTranslate != null) h.btnTranslate.setVisibility(View.VISIBLE);

            if (h.btnCall != null) {
                h.btnCall.setOnClickListener(v -> {
                    String phone = safe(a.getPhone());
                    if (phone.isEmpty()) {
                        Toast.makeText(context, s.cardNoPhone(), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    context.startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone)));
                });
            }

            if (h.btnChat != null) {
                h.btnChat.setOnClickListener(v -> {
                    String postOwnerUid = safe(a.getUserId());
                    String myUid = safe(FirebaseAuth.getInstance().getUid());
                    if (postOwnerUid.isEmpty()) {
                        Toast.makeText(context, s.cardNoChatEmpty(), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (myUid.equals(postOwnerUid)) {
                        Toast.makeText(context, s.cardNoChatSelf(), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Intent i = new Intent(context, ChatActivity.class);
                    i.putExtra(ChatActivity.EXTRA_OTHER_UID,  postOwnerUid);
                    i.putExtra(ChatActivity.EXTRA_OTHER_NAME, safe(a.getUserName()));
                    context.startActivity(i);
                });
            }

            if (h.btnDirection != null)
                h.btnDirection.setOnClickListener(v -> openDirections(a.getLat(), a.getLng()));
            if (h.btnListen != null)
                h.btnListen.setOnClickListener(v -> playAudioOrTts(a));
        }

        // ── Card click → detail ────────────────────────────────────────────
        if (!disableCardClick) {
            h.itemView.setOnClickListener(v -> openDetail(a));
        } else {
            h.itemView.setOnClickListener(null);
        }
    }

    // ── Translate button label ─────────────────────────────────────────────
    // Uses the POST'S own language (detectedLangPair) — not the app language —
    // so the label is always accurate regardless of app language setting.
    private void updateTranslateBtnLabel(Button btn, TranslateState state, AppStrings s) {
        if (state.isLoading) {
            btn.setText(s.cardTranslating());
            return;
        }
        if (state.isTranslated) {
            // Currently showing translation → offer to revert
            // If we translated bn→en, translation is now English → offer বাংলা
            // If we translated en→bn, translation is now Bangla → offer English
            boolean translatedIsBangla = "en|bn".equals(state.detectedLangPair);
            btn.setText(translatedIsBangla ? s.cardTranslateToEn() : s.cardTranslateToBn());
        } else {
            // Currently showing original → offer the translation
            // bn|en means original is Bangla → offer English
            // en|bn means original is English → offer Bangla
            if ("bn|en".equals(state.detectedLangPair)) {
                btn.setText(s.cardTranslateToEn());   // "🌐 English"
            } else {
                btn.setText(s.cardTranslateToBn());   // "🌐 বাংলা"
            }
        }
    }

    /** Get Bangla category name; fall back to English if not mapped */
    private String getCategoryBn(String englishKey) {
        String bn = CategoryConfig.MAIN_BN.get(englishKey);
        return (bn != null) ? bn : englishKey;
    }

    private void openDirections(double destLat, double destLng) {
        Uri gmmIntentUri = Uri.parse("google.navigation:q=" + destLat + "," + destLng + "&mode=d");
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
        // Always pass ORIGINAL text — detail screen has its own translate button
        intent.putExtra(AnnouncementDetailActivity.EXTRA_TITLE,     safe(a.getTitle()));
        intent.putExtra(AnnouncementDetailActivity.EXTRA_DESC,      safe(a.getDescription()));
        // ✅ Pass English category key — detail screen converts to app language itself
        intent.putExtra(AnnouncementDetailActivity.EXTRA_CATEGORY,  safe(a.getCategory()).isEmpty()
                ? safe(a.getDisplayCategoryLabel()) : safe(a.getCategory()));
        intent.putExtra(AnnouncementDetailActivity.EXTRA_PHONE,     safe(a.getPhone()));
        intent.putExtra(AnnouncementDetailActivity.EXTRA_IMAGE_URL, safe(a.getImageUrl()));

        ArrayList<String> urls = new ArrayList<>();
        if (a.getImageUrls() != null) urls.addAll(a.getImageUrls());
        else if (!safe(a.getImageUrl()).isEmpty()) urls.add(a.getImageUrl());
        intent.putStringArrayListExtra(AnnouncementDetailActivity.EXTRA_IMAGE_URLS, urls);

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
                    // Clear translate cache since original changed
                    translateStateMap.remove(safe(a.getId()));
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
                        translateStateMap.remove(safe(a.getId()));
                        items.remove(currentPos);
                        notifyItemRemoved(currentPos);
                        notifyItemRangeChanged(currentPos, items.size());
                    }
                    Toast.makeText(context, s.cardDeleted(), Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(context, s.cardDeleteFailed(), Toast.LENGTH_SHORT).show());
    }

    private void showReactivateDialog(Announcement a, int pos) {
        AppStrings s = AppStrings.get(context);
        boolean isBn = !LanguageManager.isEnglish(context);
        String[] options = isBn ? new String[]{
                "১ ঘণ্টা", "৬ ঘণ্টা", "১২ ঘণ্টা", "২৪ ঘণ্টা (১ দিন)", "৩ দিন", "৭ দিন"
        } : new String[]{
                "1 hour", "6 hours", "12 hours", "24 hours (1 day)", "3 days", "7 days"
        };
        long[] millis = new long[]{
                3600_000L,
                6 * 3600_000L,
                12 * 3600_000L,
                24 * 3600_000L,
                3 * 24 * 3600_000L,
                7 * 24 * 3600_000L
        };

        new AlertDialog.Builder(context)
                .setTitle(s.cardReactivateDurationTitle())
                .setItems(options, (dialog, which) -> {
                    if (a.getId() == null) return;
                    long now = System.currentTimeMillis();
                    long newExpireAt = now + millis[which];
                    Map<String, Object> updates = new HashMap<>();
                    updates.put("active", true);
                    updates.put("time", now);
                    updates.put("expireAt", newExpireAt);

                    FirebaseDatabase.getInstance().getReference(Constants.DB_ANNOUNCEMENTS)
                            .child(a.getId())
                            .updateChildren(updates)
                            .addOnSuccessListener(v -> {
                                a.setActive(true);
                                a.setTime(now);
                                a.setExpireAt(newExpireAt);
                                int currentPos = items.indexOf(a);
                                if (currentPos >= 0) notifyItemChanged(currentPos);
                                Toast.makeText(context, s.cardPostActivated(), Toast.LENGTH_SHORT).show();
                            })
                            .addOnFailureListener(e ->
                                    Toast.makeText(context, "Failed to activate post", Toast.LENGTH_SHORT).show());
                })
                .setNegativeButton(s.cancel(), null)
                .show();
    }

    private GradientDrawable roundedStatusBg(int color, int radiusDp) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(color);
        float density = context.getResources().getDisplayMetrics().density;
        gd.setCornerRadius(radiusDp * density);
        return gd;
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
        View      flImageSlider;
        ViewPager2 vpImageSlider;
        TextView  tvImageIndex;
        TextView  tvBadge, tvTitle, tvDesc, tvAvatar, tvName, tvDistance, tvTime;
        TextView  tvExpiry, tvAudioBadge, tvActiveStatus;
        Button    btnListen, btnChat, btnCall, btnDirection;
        Button    btnTranslate; // ✅ Only translates title + desc — nothing else
        View      layoutEditDelete;
        Button    btnToggleActive, btnEdit, btnDelete, btnDetails;

        public VH(@NonNull View itemView) {
            super(itemView);
            flImageSlider    = itemView.findViewById(R.id.flImageSlider);
            vpImageSlider    = itemView.findViewById(R.id.vpImageSlider);
            tvImageIndex     = itemView.findViewById(R.id.tvImageIndex);
            tvBadge          = itemView.findViewById(R.id.tvBadge);
            tvTitle          = itemView.findViewById(R.id.tvTitle);
            tvDesc           = itemView.findViewById(R.id.tvDesc);
            tvAvatar         = itemView.findViewById(R.id.tvAvatar);
            tvName           = itemView.findViewById(R.id.tvName);
            tvDistance       = itemView.findViewById(R.id.tvDistance);
            tvTime           = itemView.findViewById(R.id.tvTime);
            tvExpiry         = itemView.findViewById(R.id.tvExpiry);
            tvAudioBadge     = itemView.findViewById(R.id.tvAudioBadge);
            tvActiveStatus   = itemView.findViewById(R.id.tvActiveStatus);
            btnListen        = itemView.findViewById(R.id.btnListen);
            btnChat          = itemView.findViewById(R.id.btnChat);
            btnCall          = itemView.findViewById(R.id.btnCall);
            btnDirection     = itemView.findViewById(R.id.btnDirection);
            btnTranslate     = itemView.findViewById(R.id.btnTranslate);
            layoutEditDelete = itemView.findViewById(R.id.layoutEditDelete);
            btnToggleActive  = itemView.findViewById(R.id.btnToggleActive);
            btnEdit          = itemView.findViewById(R.id.btnEdit);
            btnDelete        = itemView.findViewById(R.id.btnDelete);
            btnDetails       = itemView.findViewById(R.id.btnDetails);
        }
    }

    private String safe(String s) { return s == null ? "" : s.trim(); }
}