package com.example.towncrierbd.models;

public class ChatMessage {

    private String id;
    private String senderId;
    private String senderName;
    private String receiverId;
    private String text;
    private long timestamp;
    private boolean read;

    public ChatMessage() {}

    public ChatMessage(String senderId, String senderName, String receiverId,
                       String text, long timestamp) {
        this.senderId   = senderId;
        this.senderName = senderName;
        this.receiverId = receiverId;
        this.text       = text;
        this.timestamp  = timestamp;
        this.read       = false;
    }

    public String getId()           { return id; }
    public String getSenderId()     { return senderId; }
    public String getSenderName()   { return senderName; }
    public String getReceiverId()   { return receiverId; }
    public String getText()         { return text; }
    public long   getTimestamp()    { return timestamp; }
    public boolean isRead()         { return read; }

    public void setId(String id)                 { this.id = id; }
    public void setSenderId(String senderId)     { this.senderId = senderId; }
    public void setSenderName(String senderName) { this.senderName = senderName; }
    public void setReceiverId(String receiverId) { this.receiverId = receiverId; }
    public void setText(String text)             { this.text = text; }
    public void setTimestamp(long timestamp)     { this.timestamp = timestamp; }
    public void setRead(boolean read)            { this.read = read; }
}