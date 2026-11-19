package com.example.towncrierbd.activities;

import android.Manifest;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.towncrierbd.R;
import com.example.towncrierbd.adapters.ChatAdapter;
import com.example.towncrierbd.models.Announcement;
import com.example.towncrierbd.models.ChatMessage;
import com.example.towncrierbd.models.UserModel;
import com.example.towncrierbd.utils.AppStrings;
import com.example.towncrierbd.utils.AudioRecorderHelper;
import com.example.towncrierbd.utils.CloudinaryUploader;
import com.example.towncrierbd.utils.Constants;
import com.example.towncrierbd.utils.ImageImporter;
import com.example.towncrierbd.utils.NotificationSender;
import com.example.towncrierbd.utils.PostCatalog;
import com.example.towncrierbd.utils.PostWorkflow;
import com.example.towncrierbd.utils.PresenceManager;
import com.example.towncrierbd.views.WaveformView;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ChatActivity extends AppCompatActivity {

    public static final String EXTRA_OTHER_UID  = "otherUid";
    public static final String EXTRA_OTHER_NAME = "otherName";

    // Track active chat room to suppress redundant status-bar notification banner when user is actively in this room
    public static volatile String activeChatRoomId = null;

    private RecyclerView rvMessages;
    private EditText etMessage;
    private View btnSend;
    private TextView tvOtherName, tvActiveStatus;
    private TextView tvHeaderAvatar;
    private ImageView ivHeaderAvatar, btnBack;

    // Real FB Messenger Action Buttons
    private ImageView btnCamera, btnGallery, btnVoiceRecord;

    // Bot Suggestion Bar
    private View layoutBotSuggestion;
    private TextView tvBotPrompt;
    private MaterialButton btnBotRequestOffer;
    private ImageView btnDismissBot;
    private boolean botDismissed = false;

    // Post Context
    private LinearLayout layoutPostContext;
    private MaterialButton btnRelatedPost, btnSwitchPost;

    private ChatAdapter adapter;
    private DatabaseReference chatRef;
    private ValueEventListener chatListener;
    private Query messagesQuery;

    // Presence listener for the other user
    private DatabaseReference otherUserPresenceRef;
    private ValueEventListener otherPresenceListener;
    private final Handler presenceRefreshHandler = new Handler(Looper.getMainLooper());
    private Runnable presenceRefreshRunnable;
    private boolean otherUserOnline = false;
    private long otherUserLastSeen = 0;

    private String myUid;
    private String myName = "";
    private String myRole = "";
    private String otherUid;
    private String otherName;
    private String otherRole = "";
    private String chatRoomId;
    private String announcementId;

    private AudioRecorderHelper voiceRecorder;
    private ActivityResultLauncher<String> voicePermissionLauncher;
    private ActivityResultLauncher<String> cameraPermissionLauncher;
    private ActivityResultLauncher<Uri> takePictureLauncher;
    private ActivityResultLauncher<String> galleryLauncher;

    private Uri cameraTempUri;
    private File cameraTempFile;
    private MediaPlayer voicePreview;
    private boolean sendingVoice = false;
    private boolean requestingOffer = false;
    private boolean contextPinned = false;

    // Voice recording custom dialog state
    private Dialog recordDialog;
    private final Handler recordHandler = new Handler(Looper.getMainLooper());
    private long recordStartTime = 0;
    private Runnable recordTickRunnable;

    private final LinkedHashSet<String> contextIds = new LinkedHashSet<>();
    private final Map<String, String> contextTitles = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        announcementId = getIntent().getStringExtra("announcementId");
        if (announcementId != null && announcementId.isEmpty()) announcementId = null;
        contextPinned = announcementId != null;

        otherUid  = getIntent().getStringExtra(EXTRA_OTHER_UID);
        otherName = getIntent().getStringExtra(EXTRA_OTHER_NAME);

        if (otherUid == null || otherUid.isEmpty()) {
            Toast.makeText(this, "Invalid chat", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        myUid = FirebaseAuth.getInstance().getUid();
        if (myUid == null) { finish(); return; }

        if (myUid.equals(otherUid)) {
            Toast.makeText(this, "Cannot chat with yourself", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        chatRoomId = buildRoomId(myUid, otherUid);
        chatRef = FirebaseDatabase.getInstance().getReference(Constants.DB_CHATS).child(chatRoomId);

        bindViews();
        setupActivityLaunchers();

        adapter = new ChatAdapter(myUid);
        adapter.setActionListener(new ChatAdapter.MessageActionListener() {
            @Override
            public void onEditMessage(ChatMessage msg) {
                showEditMessageDialog(msg);
            }

            @Override
            public void onDeleteMessage(ChatMessage msg) {
                confirmDeleteMessage(msg);
            }
        });

        LinearLayoutManager lm = new LinearLayoutManager(this);
        lm.setStackFromEnd(true);
        rvMessages.setLayoutManager(lm);
        rvMessages.setAdapter(adapter);

        btnBack.setOnClickListener(v -> finish());
        btnSend.setOnClickListener(v -> sendMessage());

        btnCamera.setOnClickListener(v -> checkAndLaunchCamera());
        btnGallery.setOnClickListener(v -> galleryLauncher.launch("image/*"));
        btnVoiceRecord.setOnClickListener(v -> checkAndRecordVoice());

        btnRelatedPost.setOnClickListener(v -> openRelatedPost(announcementId));
        btnSwitchPost.setOnClickListener(v -> chooseContext());

        btnDismissBot.setOnClickListener(v -> {
            botDismissed = true;
            layoutBotSuggestion.setVisibility(View.GONE);
        });
        btnBotRequestOffer.setOnClickListener(v -> onRequestVoiceOfferClicked());

        loadMyProfile();
        loadOtherUserProfile();
        listenOtherPresence();
        updateContextUi();
        listenMessages();
    }

    private void bindViews() {
        rvMessages         = findViewById(R.id.rvMessages);
        etMessage          = findViewById(R.id.etMessage);
        btnSend            = findViewById(R.id.btnSend);
        tvOtherName        = findViewById(R.id.tvOtherName);
        tvActiveStatus     = findViewById(R.id.tvActiveStatus);
        tvHeaderAvatar     = findViewById(R.id.tvHeaderAvatar);
        ivHeaderAvatar     = findViewById(R.id.ivHeaderAvatar);
        btnBack            = findViewById(R.id.btnBack);

        btnCamera          = findViewById(R.id.btnCamera);
        btnGallery         = findViewById(R.id.btnGallery);
        btnVoiceRecord     = findViewById(R.id.btnVoiceRecord);

        layoutBotSuggestion= findViewById(R.id.layoutBotSuggestion);
        tvBotPrompt        = findViewById(R.id.tvBotPrompt);
        btnBotRequestOffer = findViewById(R.id.btnBotRequestOffer);
        btnDismissBot      = findViewById(R.id.btnDismissBot);

        layoutPostContext  = findViewById(R.id.layoutPostContext);
        btnRelatedPost     = findViewById(R.id.btnRelatedPost);
        btnSwitchPost      = findViewById(R.id.btnSwitchPost);

        tvOtherName.setText(otherName != null ? otherName : "Chat");
        String nm = (otherName != null && !otherName.trim().isEmpty()) ? otherName.trim() : "?";
        tvHeaderAvatar.setText(String.valueOf(Character.toUpperCase(nm.charAt(0))));
    }

    private void setupActivityLaunchers() {
        voiceRecorder = new AudioRecorderHelper(this);

        voicePermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    if (granted) recordVoiceMessage();
                    else Toast.makeText(this, PostCatalog.text(this, "Microphone permission is required", "মাইক্রোফোনের অনুমতি প্রয়োজন"), Toast.LENGTH_SHORT).show();
                });

        cameraPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    if (granted) launchCameraCapture();
                    else Toast.makeText(this, PostCatalog.text(this, "Camera permission is required", "ক্যামেরার অনুমতি প্রয়োজন"), Toast.LENGTH_SHORT).show();
                });

        takePictureLauncher = registerForActivityResult(
                new ActivityResultContracts.TakePicture(),
                success -> {
                    if (success && cameraTempUri != null) {
                        uploadAndSendCapturedImage(cameraTempUri);
                    }
                });

        galleryLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        uploadAndSendGalleryImage(uri);
                    }
                });
    }

    public static String buildRoomId(String uid1, String uid2) {
        if (uid1.compareTo(uid2) <= 0) {
            return uid1 + "|" + uid2;
        } else {
            return uid2 + "|" + uid1;
        }
    }

    public static boolean isMyRoom(String roomId, String myUid) {
        if (roomId == null || myUid == null) return false;
        int sep = roomId.indexOf('|');
        if (sep < 0) return false;
        String part1 = roomId.substring(0, sep);
        String part2 = roomId.substring(sep + 1);
        return myUid.equals(part1) || myUid.equals(part2);
    }

    public static String getOtherUidFromRoom(String roomId, String myUid) {
        int sep = roomId.indexOf('|');
        if (sep < 0) return null;
        String part1 = roomId.substring(0, sep);
        String part2 = roomId.substring(sep + 1);
        if (myUid.equals(part1)) return part2;
        if (myUid.equals(part2)) return part1;
        return null;
    }

    private void loadMyProfile() {
        FirebaseDatabase.getInstance()
                .getReference(Constants.DB_USERS)
                .child(myUid)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        UserModel u = snapshot.getValue(UserModel.class);
                        if (u != null) {
                            myName = u.getName() != null ? u.getName() : "";
                            myRole = u.getRole() != null ? u.getRole() : "";
                            checkAndShowBotSuggestion();
                        }
                    }
                    @Override public void onCancelled(@NonNull DatabaseError error) {}
                });
    }

    private void loadOtherUserProfile() {
        FirebaseDatabase.getInstance()
                .getReference(Constants.DB_USERS)
                .child(otherUid)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        UserModel u = snapshot.getValue(UserModel.class);
                        if (u != null) {
                            otherRole = u.getRole() != null ? u.getRole() : "";
                            if (u.getName() != null && !u.getName().isEmpty()) {
                                otherName = u.getName();
                                tvOtherName.setText(otherName);
                            }
                            String photo = u.getPhotoUrl();
                            if (photo != null && !photo.trim().isEmpty() && ivHeaderAvatar != null) {
                                tvHeaderAvatar.setVisibility(View.GONE);
                                ivHeaderAvatar.setVisibility(View.VISIBLE);
                                Glide.with(ChatActivity.this).load(photo).circleCrop().into(ivHeaderAvatar);
                            }
                            checkAndShowBotSuggestion();
                        }
                    }
                    @Override public void onCancelled(@NonNull DatabaseError error) {}
                });
    }

    // ── Real-time Active / Presence Listener ────────────────────────────────
    private void listenOtherPresence() {
        otherUserPresenceRef = FirebaseDatabase.getInstance().getReference(Constants.DB_USERS).child(otherUid);
        otherPresenceListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (isFinishing() || isDestroyed()) return;
                Boolean onl = snapshot.child("online").getValue(Boolean.class);
                Long ls = snapshot.child("lastSeen").getValue(Long.class);
                otherUserOnline = Boolean.TRUE.equals(onl);
                otherUserLastSeen = ls != null ? ls : 0;
                updatePresenceUi();
            }

            @Override public void onCancelled(@NonNull DatabaseError error) {}
        };
        otherUserPresenceRef.addValueEventListener(otherPresenceListener);

        // Periodic ticker to refresh relative elapsed time ("Active Xm ago") every 30 seconds
        presenceRefreshRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isFinishing() && !isDestroyed()) {
                    updatePresenceUi();
                    presenceRefreshHandler.postDelayed(this, 30000);
                }
            }
        };
        presenceRefreshHandler.postDelayed(presenceRefreshRunnable, 30000);
    }

    private void updatePresenceUi() {
        if (tvActiveStatus == null) return;
        String statusText = PresenceManager.formatActiveStatus(this, otherUserOnline, otherUserLastSeen);
        tvActiveStatus.setText(statusText);
        if (otherUserOnline) {
            tvActiveStatus.setTextColor(0xFF4ADE80); // Bright green
        } else {
            tvActiveStatus.setTextColor(0xFF90CAF9); // Light soft blue
        }
    }

    // ── Edit & Delete Messages ──────────────────────────────────────────────
    private void showEditMessageDialog(ChatMessage msg) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(PostCatalog.text(this, "Edit Message", "মেসেজ এডিট করুন"));

        final EditText input = new EditText(this);
        input.setText(msg.getText());
        if (msg.getText() != null) {
            input.setSelection(msg.getText().length());
        }
        input.setPadding(40, 30, 40, 30);
        builder.setView(input);

        builder.setPositiveButton(PostCatalog.text(this, "Save", "সংরক্ষণ"), (dialog, which) -> {
            String newText = input.getText().toString().trim();
            if (newText.isEmpty()) return;
            Map<String, Object> updates = new HashMap<>();
            updates.put("text", newText);
            updates.put("edited", true);
            chatRef.child(msg.getId()).updateChildren(updates)
                    .addOnSuccessListener(v -> Toast.makeText(this, PostCatalog.text(this, "Message edited", "মেসেজ এডিট হয়েছে"), Toast.LENGTH_SHORT).show())
                    .addOnFailureListener(e -> Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
        });
        builder.setNegativeButton(PostCatalog.text(this, "Cancel", "বাতিল"), null);
        builder.show();
    }

    private void confirmDeleteMessage(ChatMessage msg) {
        new AlertDialog.Builder(this)
                .setTitle(PostCatalog.text(this, "Delete Message", "মেসেজ মুছুন"))
                .setMessage(PostCatalog.text(this, "Are you sure you want to delete this message?", "আপনি কি নিশ্চিতভাবে এই মেসেজটি মুছে ফেলতে চান?"))
                .setPositiveButton(PostCatalog.text(this, "Delete", "মুছুন"), (dialog, which) -> {
                    chatRef.child(msg.getId()).removeValue()
                            .addOnSuccessListener(v -> Toast.makeText(this, PostCatalog.text(this, "Message deleted", "মেসেজ মুছে ফেলা হয়েছে"), Toast.LENGTH_SHORT).show())
                            .addOnFailureListener(e -> Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                })
                .setNegativeButton(PostCatalog.text(this, "Cancel", "বাতিল"), null)
                .show();
    }

    // ── Bot Suggestion: Request Voice Offer ─────────────────────────────────
    private void checkAndShowBotSuggestion() {
        if (botDismissed) return;
        boolean amGeneralUser = !Constants.ROLE_ANNOUNCER.equals(myRole);
        boolean isAnnouncer = Constants.ROLE_ANNOUNCER.equals(otherRole);

        // If I'm general user or other user is announcer, query active voice offers
        if (amGeneralUser || isAnnouncer) {
            FirebaseDatabase.getInstance().getReference(Constants.DB_ANNOUNCEMENTS)
                    .orderByChild("userId").equalTo(otherUid)
                    .addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot rows) {
                            if (isFinishing() || isDestroyed() || botDismissed) return;
                            int voicePostCount = 0;
                            for (DataSnapshot row : rows.getChildren()) {
                                Announcement a = row.getValue(Announcement.class);
                                if (a != null && a.isActive() && !PostWorkflow.request(a)
                                        && PostWorkflow.live(a)
                                        && a.getAudioUrl() != null && !a.getAudioUrl().trim().isEmpty()) {
                                    voicePostCount++;
                                }
                            }
                            if (voicePostCount > 0 && !botDismissed) {
                                layoutBotSuggestion.setVisibility(View.VISIBLE);
                                tvBotPrompt.setText(PostCatalog.text(ChatActivity.this,
                                        "🤖 Bot: Request active voice offer from this announcer?",
                                        "🤖 রোবট পরামর্শ: বিক্রেতার ভয়েস অফার শুনতে চান?"));
                                btnBotRequestOffer.setText(PostCatalog.text(ChatActivity.this,
                                        "🎙️ Request Voice Offer",
                                        "🎙️ ভয়েস অফার অনুরোধ করুন"));
                            } else {
                                layoutBotSuggestion.setVisibility(View.GONE);
                            }
                        }
                        @Override public void onCancelled(@NonNull DatabaseError error) {}
                    });
        } else {
            layoutBotSuggestion.setVisibility(View.GONE);
        }
    }

    private void onRequestVoiceOfferClicked() {
        if (requestingOffer) return;
        requestingOffer = true;
        btnBotRequestOffer.setEnabled(false);
        Toast.makeText(this, PostCatalog.text(this, "Checking active voice offers…", "সক্রিয় ভয়েস অফার খোঁজা হচ্ছে…"), Toast.LENGTH_SHORT).show();

        FirebaseDatabase.getInstance().getReference(Constants.DB_ANNOUNCEMENTS)
                .orderByChild("userId").equalTo(otherUid)
                .get()
                .addOnSuccessListener(rows -> {
                    requestingOffer = false;
                    btnBotRequestOffer.setEnabled(true);
                    if (isFinishing() || isDestroyed()) return;

                    List<Announcement> activeOffers = new ArrayList<>();
                    for (DataSnapshot row : rows.getChildren()) {
                        Announcement a = row.getValue(Announcement.class);
                        if (a != null && a.isActive() && !PostWorkflow.request(a)
                                && PostWorkflow.live(a)
                                && a.getAudioUrl() != null && !a.getAudioUrl().trim().isEmpty()) {
                            a.setId(row.getKey());
                            activeOffers.add(a);
                        }
                    }

                    activeOffers.sort((a, b) -> Long.compare(b.getTime(), a.getTime()));

                    if (activeOffers.isEmpty()) {
                        Toast.makeText(ChatActivity.this,
                                PostCatalog.text(ChatActivity.this,
                                        "This announcer currently has no active voice post.",
                                        "এই ঘোষণাকারীর বর্তমানে সক্রিয় কোনো ভয়েস অফার পোস্ট নেই।"),
                                Toast.LENGTH_LONG).show();
                        layoutBotSuggestion.setVisibility(View.GONE);
                        return;
                    }

                    if (activeOffers.size() == 1) {
                        deliverVoiceOfferToChat(activeOffers.get(0));
                        return;
                    }

                    String[] labels = new String[activeOffers.size()];
                    for (int i = 0; i < activeOffers.size(); i++) {
                        labels[i] = activeOffers.get(i).getTitle();
                    }

                    new AlertDialog.Builder(ChatActivity.this)
                            .setTitle(PostCatalog.text(ChatActivity.this, "Choose Voice Offer", "ভয়েস অফার নির্বাচন করুন"))
                            .setItems(labels, (dialog, which) -> deliverVoiceOfferToChat(activeOffers.get(which)))
                            .setNegativeButton(PostCatalog.text(ChatActivity.this, "Cancel", "বাতিল"), null)
                            .show();
                })
                .addOnFailureListener(e -> {
                    requestingOffer = false;
                    btnBotRequestOffer.setEnabled(true);
                    Toast.makeText(ChatActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void deliverVoiceOfferToChat(Announcement offer) {
        String msgId = chatRef.push().getKey();
        if (msgId == null) return;

        ChatMessage msg = new ChatMessage(
                otherUid,       // Announcer is the sender of the voice offer
                otherName,
                myUid,          // General user is recipient
                offer.getTitle() != null && !offer.getTitle().isEmpty() ? offer.getTitle() : "Voice Offer",
                System.currentTimeMillis()
        );
        msg.setId(msgId);
        msg.setAudioUrl(offer.getAudioUrl());
        msg.setAnnouncementId(offer.getId());
        msg.setOfferPostId(offer.getId());
        msg.setPostTitle(offer.getTitle());
        msg.setPostImageUrl(offer.getImageUrl());
        msg.setMessageType("post_offer");
        msg.setRequestedByUid(myUid);

        chatRef.child(msgId).setValue(msg)
                .addOnSuccessListener(v -> {
                    Toast.makeText(this,
                            PostCatalog.text(this, "Voice offer received! Tap to play.", "ভয়েস অফার এসেছে! শুনতে ট্যাপ করুন।"),
                            Toast.LENGTH_SHORT).show();
                    if (layoutBotSuggestion != null) {
                        layoutBotSuggestion.setVisibility(View.GONE);
                    }
                    NotificationSender.sendChatNotification(
                            myUid,
                            otherName != null && !otherName.isEmpty() ? otherName : "Announcer",
                            otherUid,
                            "🎙️ " + (offer.getTitle() != null ? offer.getTitle() : "Voice Offer"),
                            chatRoomId,
                            offer.getId()
                    );
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to deliver offer: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    // ── Messages Listener ───────────────────────────────────────────────────
    private void listenMessages() {
        chatListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<ChatMessage> list = new ArrayList<>();
                String latestContext = null;
                contextIds.clear();
                if (announcementId != null) contextIds.add(announcementId);

                for (DataSnapshot s : snapshot.getChildren()) {
                    ChatMessage msg = s.getValue(ChatMessage.class);
                    if (msg == null) continue;
                    if (msg.getId() == null) msg.setId(s.getKey());
                    list.add(msg);
                    if (msg.getAnnouncementId() != null && !msg.getAnnouncementId().isEmpty()) {
                        latestContext = msg.getAnnouncementId();
                        contextIds.add(latestContext);
                    }

                    if (otherUid.equals(msg.getSenderId()) && !msg.isRead()) {
                        s.getRef().child("read").setValue(true);
                    }
                }
                if (!contextPinned && latestContext != null) announcementId = latestContext;
                updateContextUi();
                adapter.setMessages(list);
                if (!list.isEmpty()) {
                    rvMessages.scrollToPosition(list.size() - 1);
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(ChatActivity.this, "Failed to load messages: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        };
        messagesQuery = chatRef.orderByChild("timestamp");
        messagesQuery.addValueEventListener(chatListener);
    }

    // ── Text Message ────────────────────────────────────────────────────────
    private void sendMessage() {
        String text = etMessage.getText().toString().trim();
        if (TextUtils.isEmpty(text)) return;

        String msgId = chatRef.push().getKey();
        if (msgId == null) return;

        String senderDisplayName = (myName != null && !myName.trim().isEmpty())
                ? myName.trim()
                : (FirebaseAuth.getInstance().getCurrentUser() != null && FirebaseAuth.getInstance().getCurrentUser().getDisplayName() != null
                    ? FirebaseAuth.getInstance().getCurrentUser().getDisplayName() : "User");

        ChatMessage msg = new ChatMessage(
                myUid,
                senderDisplayName,
                otherUid,
                text,
                System.currentTimeMillis()
        );
        msg.setId(msgId);
        msg.setAnnouncementId(announcementId);
        msg.setMessageType("text");

        chatRef.child(msgId).setValue(msg)
                .addOnSuccessListener(v -> {
                    etMessage.setText("");
                    NotificationSender.sendChatNotification(
                            otherUid,
                            senderDisplayName,
                            myUid,
                            text,
                            chatRoomId,
                            announcementId
                    );
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Send failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    // ── Real FB Messenger: Camera Photo Capture ─────────────────────────────
    private void checkAndLaunchCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        } else {
            launchCameraCapture();
        }
    }

    private void launchCameraCapture() {
        try {
            cameraTempFile = File.createTempFile("chat_cam_", ".jpg", getCacheDir());
            cameraTempUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", cameraTempFile);
            takePictureLauncher.launch(cameraTempUri);
        } catch (Exception e) {
            Toast.makeText(this, "Camera error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void uploadAndSendCapturedImage(Uri uri) {
        Toast.makeText(this, PostCatalog.text(this, "Uploading photo…", "ছবি আপলোড হচ্ছে…"), Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                Bitmap bitmap = ImageImporter.decode(this, uri);
                CloudinaryUploader.uploadBitmap(this, bitmap, new CloudinaryUploader.UploadListener() {
                    @Override
                    public void onSuccess(String imageUrl) {
                        runOnUiThread(() -> sendImageMessage(imageUrl));
                        if (cameraTempFile != null) cameraTempFile.delete();
                    }

                    @Override
                    public void onError(String message) {
                        runOnUiThread(() -> Toast.makeText(ChatActivity.this, "Photo upload failed: " + message, Toast.LENGTH_SHORT).show());
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(ChatActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    // ── Real FB Messenger: Gallery Image Selection ──────────────────────────
    private void uploadAndSendGalleryImage(Uri uri) {
        Toast.makeText(this, PostCatalog.text(this, "Uploading image…", "ছবি আপলোড হচ্ছে…"), Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                Bitmap bitmap = ImageImporter.decode(this, uri);
                CloudinaryUploader.uploadBitmap(this, bitmap, new CloudinaryUploader.UploadListener() {
                    @Override
                    public void onSuccess(String imageUrl) {
                        runOnUiThread(() -> sendImageMessage(imageUrl));
                    }

                    @Override
                    public void onError(String message) {
                        runOnUiThread(() -> Toast.makeText(ChatActivity.this, "Image upload failed: " + message, Toast.LENGTH_SHORT).show());
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(ChatActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void sendImageMessage(String imageUrl) {
        String msgId = chatRef.push().getKey();
        if (msgId == null) return;

        String senderDisplayName = (myName != null && !myName.trim().isEmpty())
                ? myName.trim()
                : (FirebaseAuth.getInstance().getCurrentUser() != null && FirebaseAuth.getInstance().getCurrentUser().getDisplayName() != null
                    ? FirebaseAuth.getInstance().getCurrentUser().getDisplayName() : "User");
        String displayText = PostCatalog.text(this, "📷 Photo", "📷 ছবি");

        ChatMessage msg = new ChatMessage(myUid, senderDisplayName, otherUid, displayText, System.currentTimeMillis());
        msg.setId(msgId);
        msg.setImageUrl(imageUrl);
        msg.setAnnouncementId(announcementId);
        msg.setMessageType("image");

        chatRef.child(msgId).setValue(msg)
                .addOnSuccessListener(v -> {
                    NotificationSender.sendChatNotification(
                            otherUid,
                            senderDisplayName,
                            myUid,
                            displayText,
                            chatRoomId,
                            announcementId
                    );
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to send image: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    // ── Waveform Styled Voice Message Recording & Sending ───────────────────
    private void checkAndRecordVoice() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            voicePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
        } else {
            recordVoiceMessage();
        }
    }

    private void recordVoiceMessage() {
        if (sendingVoice) return;

        recordDialog = new Dialog(this);
        recordDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        recordDialog.setContentView(R.layout.dialog_voice_record);
        if (recordDialog.getWindow() != null) {
            recordDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            recordDialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        TextView tvRecordTimer   = recordDialog.findViewById(R.id.tvRecordTimer);
        TextView tvRecordPrompt  = recordDialog.findViewById(R.id.tvRecordPrompt);
        WaveformView waveformLive= recordDialog.findViewById(R.id.waveformLive);
        View btnStopRecord       = recordDialog.findViewById(R.id.btnStopRecord);
        View btnCancelRecord     = recordDialog.findViewById(R.id.btnCancelRecord);

        waveformLive.setRecordingMode(true);
        tvRecordPrompt.setText(PostCatalog.text(this, "Speak into microphone…", "মাইক্রোফোনে কথা বলুন…"));

        btnCancelRecord.setOnClickListener(v -> {
            stopRecordTimer();
            if (voiceRecorder != null) voiceRecorder.cancelRecording();
            recordDialog.dismiss();
        });

        btnStopRecord.setOnClickListener(v -> {
            stopRecordTimer();
            if (voiceRecorder != null) {
                voiceRecorder.stopRecording(new AudioRecorderHelper.RecordListener() {
                    @Override public void onRecordStarted() {}
                    @Override
                    public void onRecordStopped(String path) {
                        recordDialog.dismiss();
                        previewVoice(path);
                    }
                    @Override
                    public void onError(String message) {
                        recordDialog.dismiss();
                        Toast.makeText(ChatActivity.this, message, Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });

        recordDialog.setOnCancelListener(d -> {
            stopRecordTimer();
            if (voiceRecorder != null) voiceRecorder.cancelRecording();
        });

        voiceRecorder.startRecording(new AudioRecorderHelper.RecordListener() {
            @Override
            public void onRecordStarted() {
                recordStartTime = System.currentTimeMillis();
                recordDialog.show();
                startRecordTimer(tvRecordTimer, waveformLive);
            }

            @Override public void onRecordStopped(String path) {}

            @Override
            public void onError(String message) {
                Toast.makeText(ChatActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void startRecordTimer(TextView tvTimer, WaveformView waveformView) {
        stopRecordTimer();
        recordTickRunnable = new Runnable() {
            @Override
            public void run() {
                if (voiceRecorder != null && voiceRecorder.isRecording()) {
                    long elapsed = (System.currentTimeMillis() - recordStartTime) / 1000;
                    long m = elapsed / 60;
                    long s = elapsed % 60;
                    if (tvTimer != null) {
                        tvTimer.setText(String.format(Locale.getDefault(), "%02d:%02d", m, s));
                    }
                    if (waveformView != null) {
                        int amp = voiceRecorder.getMaxAmplitude();
                        waveformView.addLiveAmplitude(amp);
                    }
                    recordHandler.postDelayed(this, 100);
                }
            }
        };
        recordHandler.post(recordTickRunnable);
    }

    private void stopRecordTimer() {
        if (recordTickRunnable != null) {
            recordHandler.removeCallbacks(recordTickRunnable);
            recordTickRunnable = null;
        }
    }

    private void previewVoice(String path) {
        Dialog preview = new Dialog(this);
        preview.requestWindowFeature(Window.FEATURE_NO_TITLE);
        preview.setContentView(R.layout.dialog_voice_preview);
        if (preview.getWindow() != null) {
            preview.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            preview.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        TextView tvTitle = preview.findViewById(R.id.tvPreviewTitle);
        ImageView btnPlay = preview.findViewById(R.id.btnPreviewPlay);
        WaveformView waveform = preview.findViewById(R.id.waveformPreview);
        TextView tvDuration = preview.findViewById(R.id.tvPreviewDuration);
        MaterialButton btnDelete = preview.findViewById(R.id.btnPreviewDelete);
        MaterialButton btnSend = preview.findViewById(R.id.btnPreviewSend);

        tvTitle.setText(PostCatalog.text(this, "🎙️ Voice Message Preview", "🎙️ ভয়েস মেসেজ প্রিভিউ"));
        btnDelete.setText(PostCatalog.text(this, "🗑 Delete", "🗑 মুছুন"));
        btnSend.setText(PostCatalog.text(this, "Send 🚀", "পাঠান 🚀"));

        final boolean[] isPlaying = {false};

        try {
            MediaPlayer mp = new MediaPlayer();
            mp.setDataSource(path);
            mp.prepare();
            int totalSec = mp.getDuration() / 1000;
            tvDuration.setText(String.format(Locale.getDefault(), "%02d:%02d", totalSec / 60, totalSec % 60));
            mp.release();
        } catch (Exception ignored) {}

        btnPlay.setOnClickListener(v -> {
            if (isPlaying[0]) {
                if (voicePreview != null) {
                    try { voicePreview.stop(); voicePreview.release(); } catch (Exception ignored) {}
                    voicePreview = null;
                }
                isPlaying[0] = false;
                btnPlay.setImageResource(R.drawable.ic_play_arrow);
                waveform.setPlaying(false);
            } else {
                try {
                    if (voicePreview != null) voicePreview.release();
                    voicePreview = new MediaPlayer();
                    voicePreview.setDataSource(path);
                    voicePreview.setOnPreparedListener(mp -> {
                        mp.start();
                        isPlaying[0] = true;
                        btnPlay.setImageResource(R.drawable.ic_pause);
                        waveform.setPlaying(true);
                    });
                    voicePreview.setOnCompletionListener(mp -> {
                        isPlaying[0] = false;
                        btnPlay.setImageResource(R.drawable.ic_play_arrow);
                        waveform.setPlaying(false);
                    });
                    voicePreview.prepareAsync();
                } catch (Exception e) {
                    Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        });

        btnDelete.setOnClickListener(v -> {
            if (voicePreview != null) {
                try { voicePreview.stop(); voicePreview.release(); } catch (Exception ignored) {}
                voicePreview = null;
            }
            new File(path).delete();
            preview.dismiss();
        });

        btnSend.setOnClickListener(v -> {
            if (sendingVoice) return;
            String id = chatRef.push().getKey();
            if (id == null) return;
            sendingVoice = true;
            btnSend.setEnabled(false);

            if (voicePreview != null) {
                try { voicePreview.stop(); voicePreview.release(); } catch (Exception ignored) {}
                voicePreview = null;
            }

            Toast.makeText(this, PostCatalog.text(this, "Sending voice message…", "ভয়েস মেসেজ পাঠানো হচ্ছে…"), Toast.LENGTH_SHORT).show();
            AudioRecorderHelper.uploadAudio(path, "chat_" + id, new AudioRecorderHelper.UploadListener() {
                @Override public void onProgress(int percent) {}
                @Override
                public void onSuccess(String url) {
                    runOnUiThread(() -> {
                        sendingVoice = false;
                        preview.dismiss();
                        saveVoiceMessage(id, url);
                    });
                }
                @Override
                public void onError(String message) {
                    runOnUiThread(() -> {
                        sendingVoice = false;
                        btnSend.setEnabled(true);
                        Toast.makeText(ChatActivity.this, message, Toast.LENGTH_LONG).show();
                    });
                }
            });
        });

        preview.setOnDismissListener(d -> {
            if (voicePreview != null) {
                try { voicePreview.stop(); voicePreview.release(); } catch (Exception ignored) {}
                voicePreview = null;
            }
        });

        preview.show();
    }

    private void saveVoiceMessage(String id, String url) {
        String senderDisplayName = (myName != null && !myName.trim().isEmpty())
                ? myName.trim()
                : (FirebaseAuth.getInstance().getCurrentUser() != null && FirebaseAuth.getInstance().getCurrentUser().getDisplayName() != null
                    ? FirebaseAuth.getInstance().getCurrentUser().getDisplayName() : "User");
        String displayText = PostCatalog.text(this, "🎙️ Voice message", "🎙️ ভয়েস মেসেজ");

        ChatMessage msg = new ChatMessage(myUid, senderDisplayName, otherUid, displayText, System.currentTimeMillis());
        msg.setId(id);
        msg.setAnnouncementId(announcementId);
        msg.setAudioUrl(url);
        msg.setMessageType("voice");

        chatRef.child(id).setValue(msg)
                .addOnSuccessListener(v -> {
                    NotificationSender.sendChatNotification(
                            otherUid,
                            senderDisplayName,
                            myUid,
                            displayText,
                            chatRoomId,
                            announcementId
                    );
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to send: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    // ── Context / Post Linking ──────────────────────────────────────────────
    private void openRelatedPost(String id) {
        if (id == null) return;
        FirebaseDatabase.getInstance().getReference(Constants.DB_ANNOUNCEMENTS).child(id).get()
                .addOnSuccessListener(data -> {
                    Announcement a = data.getValue(Announcement.class);
                    if (a != null) {
                        a.setId(data.getKey());
                        PostWorkflow.detail(this, a);
                    } else {
                        Toast.makeText(this, PostCatalog.text(this, "Post unavailable", "পোস্ট পাওয়া যায়নি"), Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void updateContextUi() {
        if (layoutPostContext == null) return;
        if (announcementId == null) {
            layoutPostContext.setVisibility(View.GONE);
            return;
        }

        layoutPostContext.setVisibility(View.VISIBLE);
        btnRelatedPost.setText(PostCatalog.text(this, "★ Related post", "★ সংশ্লিষ্ট পোস্ট"));
        btnSwitchPost.setVisibility(contextIds.size() > 1 ? View.VISIBLE : View.GONE);

        final String current = announcementId;
        FirebaseDatabase.getInstance().getReference(Constants.DB_ANNOUNCEMENTS).child(current).get()
                .addOnSuccessListener(row -> {
                    Announcement post = row.getValue(Announcement.class);
                    if (post == null) return;
                    contextTitles.put(current, post.getTitle());
                    if (current.equals(announcementId)) {
                        btnRelatedPost.setText("★ " + post.getTitle());
                    }
                });
    }

    private void chooseContext() {
        final List<String> ids = new ArrayList<>(contextIds);
        if (ids.isEmpty()) return;
        List<com.google.android.gms.tasks.Task<DataSnapshot>> tasks = new ArrayList<>();
        for (String id : ids) {
            tasks.add(FirebaseDatabase.getInstance().getReference(Constants.DB_ANNOUNCEMENTS).child(id).get());
        }
        com.google.android.gms.tasks.Tasks.whenAllComplete(tasks).addOnSuccessListener(done -> {
            if (isFinishing() || isDestroyed()) return;
            String[] labels = new String[ids.size()];
            for (int i = 0; i < ids.size(); i++) {
                com.google.android.gms.tasks.Task<DataSnapshot> task = tasks.get(i);
                Announcement post = task.isSuccessful() ? task.getResult().getValue(Announcement.class) : null;
                labels[i] = post == null ? PostCatalog.text(this, "Unavailable post", "পোস্ট পাওয়া যায়নি") : post.getTitle();
            }
            new AlertDialog.Builder(this)
                    .setTitle(PostCatalog.text(this, "Messages will refer to this post", "মেসেজ এই পোস্টের সঙ্গে যুক্ত থাকবে"))
                    .setItems(labels, (dialog, which) -> {
                        announcementId = ids.get(which);
                        contextPinned = true;
                        updateContextUi();
                    })
                    .show();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        activeChatRoomId = chatRoomId;
        PresenceManager.setOnlineNow();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (chatRoomId != null && chatRoomId.equals(activeChatRoomId)) {
            activeChatRoomId = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (chatRoomId != null && chatRoomId.equals(activeChatRoomId)) {
            activeChatRoomId = null;
        }
        stopRecordTimer();
        if (presenceRefreshRunnable != null) {
            presenceRefreshHandler.removeCallbacks(presenceRefreshRunnable);
            presenceRefreshRunnable = null;
        }
        if (otherUserPresenceRef != null && otherPresenceListener != null) {
            otherUserPresenceRef.removeEventListener(otherPresenceListener);
            otherPresenceListener = null;
        }
        if (voiceRecorder != null) voiceRecorder.cancelRecording();
        if (voicePreview != null) {
            try { voicePreview.stop(); voicePreview.release(); } catch (Exception ignored) {}
            voicePreview = null;
        }
        if (adapter != null) adapter.release();
        if (chatListener != null && messagesQuery != null) {
            messagesQuery.removeEventListener(chatListener);
        }
    }
}
