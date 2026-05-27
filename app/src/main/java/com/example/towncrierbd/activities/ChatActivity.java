package com.example.towncrierbd.activities;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.towncrierbd.R;
import com.example.towncrierbd.adapters.ChatAdapter;
import com.example.towncrierbd.models.ChatMessage;
import com.example.towncrierbd.models.UserModel;
import com.example.towncrierbd.utils.Constants;
import com.example.towncrierbd.utils.NotificationSender;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class ChatActivity extends BaseActivity {

    public static final String EXTRA_OTHER_UID  = "otherUid";
    public static final String EXTRA_OTHER_NAME = "otherName";

    // ✅ FIX: Use "|" as separator — Firebase push keys never contain "|"
    // Previously "_" was used which exists in Firebase UIDs, breaking inbox room parsing
    private static final String ROOM_SEP = "|";

    private RecyclerView rvMessages;
    private EditText etMessage;
    private View btnSend;
    private TextView tvOtherName;
    private TextView tvHeaderAvatar;
    private ImageView btnBack;

    private ChatAdapter adapter;
    private DatabaseReference chatRef;
    private ValueEventListener chatListener;

    private String myUid;
    private String myName = "";
    private String otherUid;
    private String otherName;
    private String chatRoomId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

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

        // ✅ FIX: Use "|" separator — safe because Firebase push keys only contain
        // [-0-9A-Za-z_] and "|" is not in that set, so splitting on "|" is always correct.
        // Lexicographic order kept so both sides generate the same roomId.
        chatRoomId = buildRoomId(myUid, otherUid);

        rvMessages     = findViewById(R.id.rvMessages);
        etMessage      = findViewById(R.id.etMessage);
        btnSend        = findViewById(R.id.btnSend);
        tvOtherName    = findViewById(R.id.tvOtherName);
        tvHeaderAvatar = findViewById(R.id.tvHeaderAvatar);
        btnBack        = findViewById(R.id.btnBack);

        tvOtherName.setText(otherName != null ? otherName : "Chat");

        String nm = (otherName != null && !otherName.trim().isEmpty()) ? otherName.trim() : "?";
        if (tvHeaderAvatar != null) {
            tvHeaderAvatar.setText(String.valueOf(Character.toUpperCase(nm.charAt(0))));
        }

        adapter = new ChatAdapter(myUid);
        LinearLayoutManager lm = new LinearLayoutManager(this);
        lm.setStackFromEnd(true);
        rvMessages.setLayoutManager(lm);
        rvMessages.setAdapter(adapter);

        btnBack.setOnClickListener(v -> finish());
        btnSend.setOnClickListener(v -> sendMessage());

        chatRef = FirebaseDatabase.getInstance()
                .getReference(Constants.DB_CHATS)
                .child(chatRoomId);

        loadMyName();
        listenMessages();
    }

    /**
     * ✅ FIX: Central room ID builder — always call this, never construct inline.
     * Uses "|" separator which cannot appear in Firebase UIDs or push keys.
     * Lexicographic sort ensures roomId is identical regardless of who opens the chat first.
     */
    public static String buildRoomId(String uid1, String uid2) {
        if (uid1.compareTo(uid2) <= 0) {
            return uid1 + "|" + uid2;
        } else {
            return uid2 + "|" + uid1;
        }
    }

    /**
     * ✅ FIX: Splits a roomId built with "|" separator and returns the other participant's UID.
     * Returns null if this room does not involve myUid.
     */
    public static String getOtherUidFromRoom(String roomId, String myUid) {
        int sep = roomId.indexOf('|');
        if (sep < 0) return null;
        String part1 = roomId.substring(0, sep);
        String part2 = roomId.substring(sep + 1);
        if (myUid.equals(part1)) return part2;
        if (myUid.equals(part2)) return part1;
        return null; // not my room
    }

    /**
     * ✅ FIX: Check if a roomId involves myUid.
     */
    public static boolean isMyRoom(String roomId, String myUid) {
        return getOtherUidFromRoom(roomId, myUid) != null;
    }

    private void loadMyName() {
        FirebaseDatabase.getInstance()
                .getReference(Constants.DB_USERS)
                .child(myUid)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        UserModel u = snapshot.getValue(UserModel.class);
                        if (u != null && u.getName() != null) myName = u.getName();
                    }
                    @Override public void onCancelled(@NonNull DatabaseError error) {}
                });
    }

    private void listenMessages() {
        chatListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<ChatMessage> list = new ArrayList<>();
                for (DataSnapshot s : snapshot.getChildren()) {
                    ChatMessage msg = s.getValue(ChatMessage.class);
                    if (msg == null) continue;
                    if (msg.getId() == null) msg.setId(s.getKey());
                    list.add(msg);

                    // Mark other person's messages as read
                    if (otherUid.equals(msg.getSenderId()) && !msg.isRead()) {
                        s.getRef().child("read").setValue(true);
                    }
                }
                adapter.setMessages(list);
                if (!list.isEmpty()) {
                    rvMessages.scrollToPosition(list.size() - 1);
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(ChatActivity.this,
                        "Failed to load messages: " + error.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        };
        chatRef.orderByChild("timestamp").addValueEventListener(chatListener);
    }

    private void sendMessage() {
        String text = etMessage.getText().toString().trim();
        if (TextUtils.isEmpty(text)) return;

        String msgId = chatRef.push().getKey();
        if (msgId == null) return;

        ChatMessage msg = new ChatMessage(
                myUid,
                myName,
                otherUid,
                text,
                System.currentTimeMillis()
        );
        msg.setId(msgId);

        chatRef.child(msgId).setValue(msg)
                .addOnSuccessListener(v -> {
                    etMessage.setText("");
                    NotificationSender.sendChatNotification(
                            otherUid,
                            myName,
                            myUid,
                            text,
                            chatRoomId
                    );
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Send failed: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (chatListener != null && chatRef != null) {
            chatRef.removeEventListener(chatListener);
        }
    }
}