package com.example.towncrierbd.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.towncrierbd.R;
import com.example.towncrierbd.models.ChatMessage;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.VH> {

    private static final int TYPE_SENT     = 1;
    private static final int TYPE_RECEIVED = 2;

    private final List<ChatMessage> messages = new ArrayList<>();
    private final String myUid;

    public ChatAdapter(String myUid) {
        this.myUid = myUid;
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
        h.tvMessage.setText(msg.getText());
        h.tvTime.setText(formatTime(msg.getTimestamp()));
        if (h.tvSenderName != null) {
            h.tvSenderName.setText(msg.getSenderName());
        }
    }

    @Override
    public int getItemCount() { return messages.size(); }

    private String formatTime(long millis) {
        if (millis == 0) return "";
        return new SimpleDateFormat("hh:mm a", Locale.getDefault()).format(new Date(millis));
    }

    public static class VH extends RecyclerView.ViewHolder {
        TextView tvMessage, tvTime, tvSenderName;

        public VH(@NonNull View itemView) {
            super(itemView);
            tvMessage    = itemView.findViewById(R.id.tvMessage);
            tvTime       = itemView.findViewById(R.id.tvTime);
            tvSenderName = itemView.findViewById(R.id.tvSenderName);
        }
    }
}