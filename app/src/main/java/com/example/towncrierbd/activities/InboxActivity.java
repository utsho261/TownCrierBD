package com.example.towncrierbd.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.towncrierbd.R;
import com.example.towncrierbd.models.ChatMessage;
import com.example.towncrierbd.utils.Constants;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * InboxActivity — WhatsApp-style inbox showing only MY conversations.
 *
 * Chat room ID format: sorted(uid1, uid2) joined by "_"
 * Since Firebase UIDs contain only alphanumeric + "-", splitting by "_" is safe
 * because each room has exactly: uid1 + "_" + uid2 where both UIDs have no underscores.
 *
 * Each user only sees rooms where THEIR uid appears in the room key.
 * Privacy: Firebase Security Rules should restrict /chats/{roomId} to only
 * the two users whose UIDs form the roomId.
 */
public class InboxActivity extends AppCompatActivity {

    // ── Conversation model ───────────────────────────────────────────────────
    public static class Conversation {
        public String roomId;
        public String otherUid;
        public String otherName;
        public String lastMessage;
        public long   lastTimestamp;
        public int    unreadCount;

        public Conversation(String roomId, String otherUid) {
            this.roomId = roomId;
            this.otherUid = otherUid;
            this.otherName = "";
            this.lastMessage = "";
            this.lastTimestamp = 0;
            this.unreadCount = 0;
        }
    }

    // ── Adapter ─────────────────────────────────────────────────────────────
    private static class InboxAdapter extends RecyclerView.Adapter<InboxAdapter.VH> {

        private final List<Conversation> items = new ArrayList<>();
        private final String myUid;

        InboxAdapter(String myUid) { this.myUid = myUid; }

        void setData(List<Conversation> data) {
            items.clear();
            if (data != null) items.addAll(data);
            notifyDataSetChanged();
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_inbox_conversation, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            Conversation c = items.get(pos);
            String name = c.otherName.isEmpty() ? "User" : c.otherName;

            // Avatar initial
            h.tvAvatar.setText(String.valueOf(Character.toUpperCase(name.charAt(0))));

            h.tvName.setText(name);
            h.tvPreview.setText(c.lastMessage.isEmpty() ? "Tap to chat" : c.lastMessage);

            if (c.lastTimestamp > 0) {
                h.tvTime.setText(formatTime(c.lastTimestamp));
                h.tvTime.setVisibility(View.VISIBLE);
            } else {
                h.tvTime.setVisibility(View.GONE);
            }

            // Unread badge
            if (c.unreadCount > 0) {
                h.tvUnreadBadge.setVisibility(View.VISIBLE);
                h.tvUnreadBadge.setText(c.unreadCount > 99 ? "99+" : String.valueOf(c.unreadCount));
                h.tvName.setAlpha(1.0f);
                h.tvPreview.setAlpha(1.0f);
            } else {
                h.tvUnreadBadge.setVisibility(View.GONE);
                h.tvName.setAlpha(0.85f);
                h.tvPreview.setAlpha(0.65f);
            }

            h.itemView.setOnClickListener(v -> {
                Intent i = new Intent(v.getContext(), ChatActivity.class);
                i.putExtra(ChatActivity.EXTRA_OTHER_UID,  c.otherUid);
                i.putExtra(ChatActivity.EXTRA_OTHER_NAME, name);
                v.getContext().startActivity(i);
            });
        }

        @Override public int getItemCount() { return items.size(); }

        private String formatTime(long millis) {
            long now = System.currentTimeMillis();
            long diff = now - millis;
            if (diff < 60_000) return "Now";
            if (diff < 3_600_000) return (diff / 60_000) + "m";
            if (diff < 86_400_000) {
                return new SimpleDateFormat("h:mm a", Locale.getDefault()).format(new Date(millis));
            }
            return new SimpleDateFormat("MMM d", Locale.getDefault()).format(new Date(millis));
        }

        static class VH extends RecyclerView.ViewHolder {
            TextView tvAvatar, tvName, tvPreview, tvTime, tvUnreadBadge;
            VH(@NonNull View v) {
                super(v);
                tvAvatar      = v.findViewById(R.id.tvAvatar);
                tvName        = v.findViewById(R.id.tvName);
                tvPreview     = v.findViewById(R.id.tvPreview);
                tvTime        = v.findViewById(R.id.tvTime);
                tvUnreadBadge = v.findViewById(R.id.tvUnreadBadge);
            }
        }
    }

    // ── Activity ─────────────────────────────────────────────────────────────
    private RecyclerView rv;
    private InboxAdapter adapter;
    private TextView tvEmpty;
    private String myUid;
    private final List<Conversation> conversations = new ArrayList<>();
    private ValueEventListener inboxListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_inbox);

        myUid = FirebaseAuth.getInstance().getUid();
        if (myUid == null) { finish(); return; }

        rv = findViewById(R.id.rvInbox);
        tvEmpty = findViewById(R.id.tvEmpty);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new InboxAdapter(myUid);
        rv.setAdapter(adapter);

        View btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());

        listenToInbox();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (inboxListener != null) {
            FirebaseDatabase.getInstance()
                    .getReference(Constants.DB_CHATS)
                    .removeEventListener(inboxListener);
        }
    }

    /**
     * Real-time listener — updates immediately when messages arrive.
     * Only loads rooms where my UID is part of the room key.
     */
    private void listenToInbox() {
        inboxListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                conversations.clear();

                for (DataSnapshot roomSnap : snapshot.getChildren()) {
                    String roomId = roomSnap.getKey();
                    if (roomId == null) continue;

                    // Room key = uid1 + "_" + uid2 (both UIDs are alphanumeric + "-" only)
                    // Check if myUid is part of this room
                    if (!isMyRoom(roomId)) continue;

                    // Derive the other user's UID
                    String otherUid = getOtherUid(roomId);
                    if (otherUid == null || otherUid.isEmpty()) continue;

                    Conversation conv = new Conversation(roomId, otherUid);

                    long lastTs = 0;
                    String lastText = "";
                    int unreadCount = 0;

                    for (DataSnapshot msgSnap : roomSnap.getChildren()) {
                        ChatMessage msg = msgSnap.getValue(ChatMessage.class);
                        if (msg == null) continue;

                        long ts = msg.getTimestamp();
                        if (ts > lastTs) {
                            lastTs   = ts;
                            lastText = msg.getText() != null ? msg.getText() : "";
                        }

                        // Count unread messages sent TO me that I haven't read
                        if (otherUid.equals(msg.getSenderId()) && !msg.isRead()) {
                            unreadCount++;
                        }
                    }

                    conv.lastMessage   = lastText;
                    conv.lastTimestamp = lastTs;
                    conv.unreadCount   = unreadCount;
                    conversations.add(conv);
                }

                // Sort: most recent first
                Collections.sort(conversations,
                        (a, b) -> Long.compare(b.lastTimestamp, a.lastTimestamp));

                resolveNamesAndShow();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };

        FirebaseDatabase.getInstance()
                .getReference(Constants.DB_CHATS)
                .addValueEventListener(inboxListener);
    }

    /**
     * Check if myUid is part of the room.
     * Room format: uid1_uid2 where uid1 < uid2 (lexicographically)
     */
    private boolean isMyRoom(String roomId) {
        // Room = uid1 + "_" + uid2
        // Since Firebase UIDs contain only [a-zA-Z0-9] and "-",
        // the single "_" separator is unambiguous.
        int sepIdx = roomId.indexOf('_');
        if (sepIdx < 0) return false;
        String part1 = roomId.substring(0, sepIdx);
        String part2 = roomId.substring(sepIdx + 1);
        return myUid.equals(part1) || myUid.equals(part2);
    }

    /**
     * Get the other user's UID from the room ID.
     */
    private String getOtherUid(String roomId) {
        int sepIdx = roomId.indexOf('_');
        if (sepIdx < 0) return null;
        String part1 = roomId.substring(0, sepIdx);
        String part2 = roomId.substring(sepIdx + 1);
        return myUid.equals(part1) ? part2 : part1;
    }

    private void resolveNamesAndShow() {
        if (conversations.isEmpty()) {
            adapter.setData(conversations);
            if (tvEmpty != null) tvEmpty.setVisibility(View.VISIBLE);
            rv.setVisibility(View.GONE);
            return;
        }

        if (tvEmpty != null) tvEmpty.setVisibility(View.GONE);
        rv.setVisibility(View.VISIBLE);

        final int[] remaining = {conversations.size()};

        for (Conversation conv : conversations) {
            FirebaseDatabase.getInstance()
                    .getReference(Constants.DB_USERS)
                    .child(conv.otherUid)
                    .child("name")
                    .addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                            String name = snapshot.getValue(String.class);
                            if (name != null && !name.isEmpty()) conv.otherName = name;
                            remaining[0]--;
                            if (remaining[0] == 0) adapter.setData(conversations);
                        }
                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {
                            remaining[0]--;
                            if (remaining[0] == 0) adapter.setData(conversations);
                        }
                    });
        }
    }
}