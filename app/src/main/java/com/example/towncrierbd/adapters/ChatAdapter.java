package com.example.towncrierbd.adapters;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.media.MediaPlayer;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.towncrierbd.R;
import com.example.towncrierbd.models.Announcement;
import com.example.towncrierbd.models.ChatMessage;
import com.example.towncrierbd.utils.Constants;
import com.example.towncrierbd.utils.PostCatalog;
import com.example.towncrierbd.utils.PostWorkflow;
import com.example.towncrierbd.views.WaveformView;
import com.google.firebase.database.FirebaseDatabase;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.VH> {

    private static final int TYPE_SENT     = 1;
    private static final int TYPE_RECEIVED = 2;

    public interface MessageActionListener {
        void onEditMessage(ChatMessage msg);
        void onDeleteMessage(ChatMessage msg);
    }

    private final List<ChatMessage> messages = new ArrayList<>();
    private final Map<String, Announcement> postCache = new HashMap<>();
    private final String myUid;
    private MessageActionListener actionListener;

    private MediaPlayer player;
    private String playingUrl;
    private int playingPosition = -1;

    public ChatAdapter(String myUid) {
        this.myUid = myUid;
    }

    public void setActionListener(MessageActionListener listener) {
        this.actionListener = listener;
    }

    public void release() {
        playingUrl = null;
        playingPosition = -1;
        if (player != null) {
            try { player.stop(); player.release(); } catch (Exception ignored) {}
            player = null;
        }
    }

    public void setMessages(List<ChatMessage> list) {
        messages.clear();
        if (list != null) messages.addAll(list);
        notifyDataSetChanged();
    }

    public void addMessage(ChatMessage msg) {
        messages.add(msg);
        notifyItemInserted(messages.size() - 1);
    }

    @Override
    public int getItemViewType(int position) {
        ChatMessage msg = messages.get(position);
        return myUid.equals(msg.getSenderId()) ? TYPE_SENT : TYPE_RECEIVED;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layout = (viewType == TYPE_SENT)
                ? R.layout.item_chat_sent
                : R.layout.item_chat_received;
        View v = LayoutInflater.from(parent.getContext()).inflate(layout, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        ChatMessage msg = messages.get(pos);
        Context context = h.itemView.getContext();
        boolean isSentByMe = myUid.equals(msg.getSenderId());

        // ── 0. Long press for Edit / Delete on sent messages ───────────────
        if (isSentByMe) {
            View.OnLongClickListener longClick = v -> {
                showMessageActions(context, msg);
                return true;
            };
            if (h.bubbleContainer != null) h.bubbleContainer.setOnLongClickListener(longClick);
            h.itemView.setOnLongClickListener(longClick);
        } else {
            if (h.bubbleContainer != null) h.bubbleContainer.setOnLongClickListener(null);
            h.itemView.setOnLongClickListener(null);
        }

        // ── 1. FB Messenger Style Post / Offer Card ────────────────────────
        // Shown ONLY when this message is a post offer or shared post
        boolean isPostOffer = "post_offer".equals(msg.getMessageType())
                || "post_share".equals(msg.getMessageType())
                || (msg.getOfferPostId() != null && !msg.getOfferPostId().isEmpty());

        if (h.cardPostOffer != null) {
            if (isPostOffer) {
                h.cardPostOffer.setVisibility(View.VISIBLE);
                final String annId = (msg.getOfferPostId() != null && !msg.getOfferPostId().isEmpty())
                        ? msg.getOfferPostId() : msg.getAnnouncementId();

                if (postCache.containsKey(annId)) {
                    bindPostCardData(context, h, postCache.get(annId), msg);
                } else if (annId != null && !annId.isEmpty()) {
                    // Pre-fill with available message data
                    h.tvPostCardTitle.setText(msg.getText() != null && !msg.getText().isEmpty()
                            ? msg.getText() : PostCatalog.text(context, "Voice Offer", "ভয়েস অফার"));
                    h.tvPostCardCategory.setText(PostCatalog.text(context, "TownCrier Post", "ঘোষণা"));

                    FirebaseDatabase.getInstance().getReference(Constants.DB_ANNOUNCEMENTS)
                            .child(annId)
                            .get()
                            .addOnSuccessListener(data -> {
                                Announcement a = data.getValue(Announcement.class);
                                if (a != null) {
                                    a.setId(data.getKey());
                                    postCache.put(annId, a);
                                    bindPostCardData(context, h, a, msg);
                                }
                            });
                }

                h.cardPostOffer.setOnClickListener(v -> openPostDetails(context, annId));
            } else {
                h.cardPostOffer.setVisibility(View.GONE);
            }
        }

        // ── 2. Image message ──────────────────────────────────────────────
        String imageUrl = msg.getImageUrl();
        boolean hasImage = imageUrl != null && !imageUrl.trim().isEmpty();
        if (h.cardChatImage != null && h.ivChatImage != null) {
            if (hasImage) {
                h.cardChatImage.setVisibility(View.VISIBLE);
                Glide.with(context)
                        .load(imageUrl)
                        .centerCrop()
                        .placeholder(R.drawable.bg_card)
                        .into(h.ivChatImage);

                h.ivChatImage.setOnClickListener(v -> showFullImageDialog(context, imageUrl));
            } else {
                h.cardChatImage.setVisibility(View.GONE);
            }
        }

        // ── 3. Voice / Audio message capsule ──────────────────────────────
        String audioUrl = msg.getAudioUrl();
        boolean hasVoice = audioUrl != null && !audioUrl.trim().isEmpty();
        if (h.layoutVoice != null) {
            if (hasVoice) {
                h.layoutVoice.setVisibility(View.VISIBLE);
                boolean isCurrentPlaying = audioUrl.equals(playingUrl);

                if (h.btnPlayVoice != null) {
                    h.btnPlayVoice.setImageResource(isCurrentPlaying ? R.drawable.ic_pause : R.drawable.ic_play_arrow);
                }

                if (h.waveformVoice != null) {
                    h.waveformVoice.setPlaying(isCurrentPlaying);
                }

                if (h.tvVoiceDuration != null) {
                    h.tvVoiceDuration.setText(isCurrentPlaying
                            ? PostCatalog.text(context, "Playing…", "চলছে…")
                            : PostCatalog.text(context, "Play", "শুনুন"));
                }

                View.OnClickListener playListener = v -> toggleVoicePlayback(context, audioUrl, h.getAdapterPosition());
                h.layoutVoice.setOnClickListener(playListener);
                if (h.btnPlayVoice != null) h.btnPlayVoice.setOnClickListener(playListener);
            } else {
                h.layoutVoice.setVisibility(View.GONE);
            }
        }

        // ── 4. Text message ───────────────────────────────────────────────
        String text = msg.getText();
        boolean isMediaOnly = ("image".equals(msg.getMessageType()) && hasImage)
                || ("voice".equals(msg.getMessageType()) && hasVoice);
        boolean hasText = text != null && !text.trim().isEmpty() && !isPostOffer && !isMediaOnly;

        if (h.tvMessage != null) {
            if (hasText) {
                h.tvMessage.setVisibility(View.VISIBLE);
                h.tvMessage.setText(text);
            } else if (!hasImage && !hasVoice && !isPostOffer) {
                h.tvMessage.setVisibility(View.VISIBLE);
                h.tvMessage.setText(text != null ? text : "");
            } else {
                h.tvMessage.setVisibility(View.GONE);
            }
        }

        // ── 5. Status indicators (Edited, Time, Seen) ─────────────────────
        if (h.tvEdited != null) {
            h.tvEdited.setVisibility(msg.isEdited() ? View.VISIBLE : View.GONE);
        }

        if (h.tvTime != null) {
            h.tvTime.setText(formatTime(msg.getTimestamp()));
        }

        if (h.tvSeenStatus != null) {
            if (msg.isRead()) {
                h.tvSeenStatus.setText("✓✓ " + PostCatalog.text(context, "Seen", "দেখা হয়েছে"));
                h.tvSeenStatus.setTextColor(0xFF90CAF9);
            } else {
                h.tvSeenStatus.setText("✓ " + PostCatalog.text(context, "Sent", "পাঠানো হয়েছে"));
                h.tvSeenStatus.setTextColor(0xFFCBD5E1);
            }
        }

        if (h.tvSenderName != null) {
            h.tvSenderName.setText(msg.getSenderName() != null ? msg.getSenderName() : "User");
        }
    }

    private void bindPostCardData(Context context, VH h, Announcement a, ChatMessage msg) {
        if (a == null) return;
        if (h.tvPostCardTitle != null) {
            h.tvPostCardTitle.setText(a.getTitle() != null ? a.getTitle() : "Announcement");
        }
        if (h.tvPostCardCategory != null) {
            String cat = a.getCategory() != null ? a.getCategory() : "";
            if (a.getSubcategory() != null && !a.getSubcategory().isEmpty()) {
                cat = cat + " • " + a.getSubcategory();
            }
            h.tvPostCardCategory.setText(cat);
        }
        if (h.ivPostCardImage != null) {
            String img = a.getImageUrl();
            if (img != null && !img.trim().isEmpty()) {
                Glide.with(context).load(img).centerCrop().placeholder(R.drawable.bg_card).into(h.ivPostCardImage);
            } else {
                h.ivPostCardImage.setImageResource(R.drawable.bg_card);
            }
        }
    }

    private void openPostDetails(Context context, String annId) {
        if (annId == null || annId.isEmpty()) return;
        Announcement cached = postCache.get(annId);
        if (cached != null) {
            PostWorkflow.detail(context, cached);
            return;
        }
        FirebaseDatabase.getInstance().getReference(Constants.DB_ANNOUNCEMENTS).child(annId).get()
                .addOnSuccessListener(data -> {
                    Announcement a = data.getValue(Announcement.class);
                    if (a != null) {
                        a.setId(data.getKey());
                        postCache.put(annId, a);
                        PostWorkflow.detail(context, a);
                    } else {
                        Toast.makeText(context, PostCatalog.text(context, "Post unavailable", "পোস্ট পাওয়া যায়নি"), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void showMessageActions(Context context, ChatMessage msg) {
        if (actionListener == null) return;
        boolean canEdit = "text".equals(msg.getMessageType())
                || (msg.getText() != null && !msg.getText().isEmpty()
                && (msg.getAudioUrl() == null || msg.getAudioUrl().isEmpty())
                && (msg.getImageUrl() == null || msg.getImageUrl().isEmpty())
                && !"post_offer".equals(msg.getMessageType()));

        List<String> options = new ArrayList<>();
        if (canEdit) {
            options.add(PostCatalog.text(context, "✏️ Edit Message", "✏️ এডিট করুন"));
        }
        options.add(PostCatalog.text(context, "🗑️ Delete Message", "🗑️ ডিলিট করুন"));

        new AlertDialog.Builder(context)
                .setTitle(PostCatalog.text(context, "Message Options", "মেসেজ অপশন"))
                .setItems(options.toArray(new String[0]), (dialog, which) -> {
                    String selected = options.get(which);
                    if (selected.contains("Edit") || selected.contains("এডিট")) {
                        actionListener.onEditMessage(msg);
                    } else if (selected.contains("Delete") || selected.contains("ডিলিট")) {
                        actionListener.onDeleteMessage(msg);
                    }
                })
                .setNegativeButton(PostCatalog.text(context, "Cancel", "বাতিল"), null)
                .show();
    }

    private void toggleVoicePlayback(Context context, String audioUrl, int position) {
        if (audioUrl.equals(playingUrl)) {
            release();
            notifyItemChanged(position);
            return;
        }

        int prevPos = playingPosition;
        release();
        if (prevPos >= 0 && prevPos < messages.size()) {
            notifyItemChanged(prevPos);
        }

        playingUrl = audioUrl;
        playingPosition = position;
        notifyItemChanged(position);

        player = new MediaPlayer();
        try {
            player.setDataSource(audioUrl);
            player.setOnPreparedListener(mp -> {
                if (mp == player) mp.start();
            });
            player.setOnCompletionListener(mp -> {
                release();
                if (position >= 0 && position < messages.size()) {
                    notifyItemChanged(position);
                }
            });
            player.setOnErrorListener((mp, what, extra) -> {
                release();
                if (position >= 0 && position < messages.size()) {
                    notifyItemChanged(position);
                }
                Toast.makeText(context, PostCatalog.text(context, "Could not play audio. Tap to retry.", "অডিও চলেনি। আবার চেষ্টা করুন।"), Toast.LENGTH_SHORT).show();
                return true;
            });
            player.prepareAsync();
        } catch (Exception e) {
            release();
            if (position >= 0 && position < messages.size()) {
                notifyItemChanged(position);
            }
            Toast.makeText(context, PostCatalog.text(context, "Could not play audio", "অডিও চালানো যায়নি"), Toast.LENGTH_SHORT).show();
        }
    }

    private void showFullImageDialog(Context context, String imageUrl) {
        Dialog dialog = new Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        ImageView fullImage = new ImageView(context);
        fullImage.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        fullImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
        fullImage.setBackgroundColor(0xFF000000);

        Glide.with(context)
                .load(imageUrl)
                .fitCenter()
                .into(fullImage);

        fullImage.setOnClickListener(v -> dialog.dismiss());
        dialog.setContentView(fullImage);
        dialog.show();
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    private String formatTime(long millis) {
        if (millis == 0) return "";
        return new SimpleDateFormat("hh:mm a", Locale.getDefault()).format(new Date(millis));
    }

    public static class VH extends RecyclerView.ViewHolder {
        View bubbleContainer;
        TextView tvMessage, tvTime, tvSenderName, tvEdited, tvSeenStatus;
        TextView tvVoiceDuration;
        ImageView ivChatImage, btnPlayVoice;
        CardView cardChatImage, cardPostOffer;
        ImageView ivPostCardImage;
        TextView tvPostCardBadge, tvPostCardTitle, tvPostCardCategory, tvPostCardAction;
        LinearLayout layoutVoice;
        WaveformView waveformVoice;

        public VH(@NonNull View itemView) {
            super(itemView);
            bubbleContainer    = itemView.findViewById(R.id.bubbleContainer);
            tvMessage          = itemView.findViewById(R.id.tvMessage);
            tvTime             = itemView.findViewById(R.id.tvTime);
            tvSenderName       = itemView.findViewById(R.id.tvSenderName);
            tvEdited           = itemView.findViewById(R.id.tvEdited);
            tvSeenStatus       = itemView.findViewById(R.id.tvSeenStatus);

            cardChatImage      = itemView.findViewById(R.id.cardChatImage);
            ivChatImage        = itemView.findViewById(R.id.ivChatImage);

            layoutVoice        = itemView.findViewById(R.id.layoutVoice);
            btnPlayVoice       = itemView.findViewById(R.id.btnPlayVoice);
            waveformVoice      = itemView.findViewById(R.id.waveformVoice);
            tvVoiceDuration    = itemView.findViewById(R.id.tvVoiceDuration);

            cardPostOffer      = itemView.findViewById(R.id.cardPostOffer);
            ivPostCardImage    = itemView.findViewById(R.id.ivPostCardImage);
            tvPostCardBadge    = itemView.findViewById(R.id.tvPostCardBadge);
            tvPostCardTitle    = itemView.findViewById(R.id.tvPostCardTitle);
            tvPostCardCategory = itemView.findViewById(R.id.tvPostCardCategory);
            tvPostCardAction   = itemView.findViewById(R.id.tvPostCardAction);
        }
    }
}
