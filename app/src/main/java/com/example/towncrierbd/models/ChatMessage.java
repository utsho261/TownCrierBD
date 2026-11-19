package com.example.towncrierbd.models;

public class ChatMessage {
    private String messageType = "";
    public String getMessageType() { return messageType == null ? "" : messageType; }
    public void setMessageType(String value) { messageType = value; }
    private String offerPostId = "";
    public String getOfferPostId() { return offerPostId == null ? "" : offerPostId; }
    public void setOfferPostId(String value) { offerPostId = value; }
    private String requestedByUid = "";
    public String getRequestedByUid() { return requestedByUid == null ? "" : requestedByUid; }
    public void setRequestedByUid(String value) { requestedByUid = value; }


    private String id;
    private String senderId;
    private String senderName;
    private String receiverId;
    private String text;
    private long timestamp;
    private boolean read;

    private String announcementId;
    private String audioUrl;
    private String imageUrl;
    private String postTitle;
    private String postImageUrl;
    private boolean edited;

    public String getAnnouncementId() { return announcementId; }
    public void setAnnouncementId(String value) { announcementId = value; }
    public String getAudioUrl() { return audioUrl; }
    public void setAudioUrl(String value) { audioUrl = value; }
    public String getImageUrl() { return imageUrl == null ? "" : imageUrl; }
    public void setImageUrl(String value) { imageUrl = value; }
    public String getPostTitle() { return postTitle == null ? "" : postTitle; }
    public void setPostTitle(String value) { postTitle = value; }
    public String getPostImageUrl() { return postImageUrl == null ? "" : postImageUrl; }
    public void setPostImageUrl(String value) { postImageUrl = value; }
    public boolean isEdited() { return edited; }
    public void setEdited(boolean edited) { this.edited = edited; }
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