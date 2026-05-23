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

public class ChatActivity extends AppCompatActivity {

    public static final String EXTRA_OTHER_UID  = "otherUid";
    public static final String EXTRA_OTHER_NAME = "otherName";

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

        // ✅ FIX: নিজের সাথে চ্যাট করা যাবে না
        if (myUid.equals(otherUid)) {
            Toast.makeText(this, "Cannot chat with yourself", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // ✅ FIX: chatRoomId — UID compare করে consistent room ID তৈরি করো
        // UID তে "_" থাকতে পারে তাই separator হিসেবে "_" ব্যবহার করা ঠিক না
        // বরং lexicographic order maintain করো যাতে দুইদিক থেকে একই room পাওয়া যায়
        chatRoomId = myUid.compareTo(otherUid) < 0
                ? myUid + "_" + otherUid
                : otherUid + "_" + myUid;

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

        // ✅ FIX: myUid pass করো adapter এ — এটাই sent/received ঠিক করে
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

                    // ✅ FIX: অপরজনের message read হিসেবে mark করো
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
        // ✅ timestamp অনুযায়ী sort করো
        chatRef.orderByChild("timestamp").addValueEventListener(chatListener);
    }

    private void sendMessage() {
        String text = etMessage.getText().toString().trim();
        if (TextUtils.isEmpty(text)) return;

        String msgId = chatRef.push().getKey();
        if (msgId == null) return;

        // ✅ FIX: senderId = myUid, receiverId = otherUid সঠিকভাবে set করো
        ChatMessage msg = new ChatMessage(
                myUid,      // senderId
                myName,     // senderName
                otherUid,   // receiverId
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