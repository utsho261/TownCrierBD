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
 * InboxActivity — shows all chat conversations for the current user.
 *
 * Data model:
 *   DB_CHATS / {roomId} / {msgId}
 *   roomId = sorted(uid1, uid2) joined by "_"
 *
 * We scan every room whose key contains our UID, grab the last message,
 * then look up the other user's name from DB_USERS.
 */
public class InboxActivity extends AppCompatActivity {

    // ── Simple conversation data class ──────────────────────────────────────
    public static class Conversation {
        public String roomId;
        public String otherUid;
        public String otherName;
        public String lastMessage;
        public long   lastTimestamp;
        public boolean unread;

        public Conversation(String roomId, String otherUid) {
            this.roomId = roomId;
            this.otherUid = otherUid;
            this.otherName = "";
            this.lastMessage = "";
            this.lastTimestamp = 0;
            this.unread = false;
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
                    .inflate(android.R.layout.simple_list_item_2, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            Conversation c = items.get(pos);
            String name = c.otherName.isEmpty() ? c.otherUid : c.otherName;
            h.tvName.setText(name);
            h.tvPreview.setText(c.lastMessage.isEmpty() ? "" : c.lastMessage);
            if (c.unread) {
                h.tvName.setAlpha(1.0f);
            } else {
                h.tvName.setAlpha(0.7f);
            }
            h.itemView.setOnClickListener(v -> {
                Intent i = new Intent(v.getContext(), ChatActivity.class);
                i.putExtra(ChatActivity.EXTRA_OTHER_UID,  c.otherUid);
                i.putExtra(ChatActivity.EXTRA_OTHER_NAME, name);
                v.getContext().startActivity(i);
            });
        }

        @Override public int getItemCount() { return items.size(); }

        static class VH extends RecyclerView.ViewHolder {
            TextView tvName, tvPreview;
            VH(@NonNull View v) {
                super(v);
                tvName    = v.findViewById(android.R.id.text1);
                tvPreview = v.findViewById(android.R.id.text2);
            }
        }
    }

    // ── Activity ─────────────────────────────────────────────────────────────
    private RecyclerView rv;
    private InboxAdapter adapter;
    private String myUid;
    private final List<Conversation> conversations = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Use a minimal layout — just a RecyclerView + toolbar back button
        // In your project, replace this with a proper layout XML if you want
        // custom styling (header card, "Messages" title, etc.)
        setContentView(R.layout.activity_inbox);

        myUid = FirebaseAuth.getInstance().getUid();
        if (myUid == null) { finish(); return; }

        rv = findViewById(R.id.rvInbox);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new InboxAdapter(myUid);
        rv.setAdapter(adapter);

        View btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());

        loadConversations();
    }

    private void loadConversations() {
        FirebaseDatabase.getInstance()
                .getReference(Constants.DB_CHATS)
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        conversations.clear();

                        for (DataSnapshot roomSnap : snapshot.getChildren()) {
                            String roomId = roomSnap.getKey();
                            if (roomId == null) continue;

                            // Only rooms that involve the current user
                            if (!roomId.contains(myUid)) continue;

                            // Derive the other UID from the roomId (format: uid1_uid2)
                            String[] parts = roomId.split("_");
                            if (parts.length != 2) continue;
                            String otherUid = parts[0].equals(myUid) ? parts[1] : parts[0];

                            Conversation conv = new Conversation(roomId, otherUid);

                            // Find the last message (ordered by timestamp)
                            long lastTs = 0;
                            String lastText = "";
                            boolean hasUnread = false;

                            for (DataSnapshot msgSnap : roomSnap.getChildren()) {
                                long ts = msgSnap.child("timestamp").getValue(Long.class) != null
                                        ? msgSnap.child("timestamp").getValue(Long.class)
                                        : 0L;
                                String senderId = msgSnap.child("senderId").getValue(String.class);
                                Boolean read = msgSnap.child("read").getValue(Boolean.class);

                                if (ts > lastTs) {
                                    lastTs = ts;
                                    lastText = msgSnap.child("text").getValue(String.class) != null
                                            ? msgSnap.child("text").getValue(String.class) : "";
                                }
                                // Unread = message sent to ME that I haven't read
                                if (otherUid.equals(senderId) &&
                                        (read == null || !read)) {
                                    hasUnread = true;
                                }
                            }

                            conv.lastMessage   = lastText;
                            conv.lastTimestamp = lastTs;
                            conv.unread        = hasUnread;
                            conversations.add(conv);
                        }

                        // Sort: most recent first
                        Collections.sort(conversations,
                                (a, b) -> Long.compare(b.lastTimestamp, a.lastTimestamp));

                        // Resolve names, then show
                        resolveNames();
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {}
                });
    }

    private void resolveNames() {
        if (conversations.isEmpty()) {
            adapter.setData(conversations);
            return;
        }

        // Counter to know when all name lookups are done
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

    private String formatTime(long millis) {
        if (millis == 0) return "";
        return new SimpleDateFormat("MMM d", Locale.getDefault()).format(new Date(millis));
    }
}