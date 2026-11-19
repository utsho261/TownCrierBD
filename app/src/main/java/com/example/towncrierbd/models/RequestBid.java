package com.example.towncrierbd.models;

public class RequestBid {
    private String announcerUid = "";
    private String announcerName = "";
    private int offerMinutes = 0;
    private long acceptedAt = 0;
    private long deadlineAt = 0;
    private String status = "active"; // "active", "expired", "cancelled", "delivered"

    public RequestBid() {}

    public RequestBid(String announcerUid, String announcerName, int offerMinutes, long acceptedAt, long deadlineAt, String status) {
        this.announcerUid = announcerUid == null ? "" : announcerUid;
        this.announcerName = announcerName == null ? "" : announcerName;
        this.offerMinutes = offerMinutes;
        this.acceptedAt = acceptedAt;
        this.deadlineAt = deadlineAt;
        this.status = status == null ? "active" : status;
    }

    public String getAnnouncerUid() { return announcerUid == null ? "" : announcerUid; }
    public void setAnnouncerUid(String value) { announcerUid = value == null ? "" : value; }

    public String getAnnouncerName() { return announcerName == null ? "" : announcerName; }
    public void setAnnouncerName(String value) { announcerName = value == null ? "" : value; }

    public int getOfferMinutes() { return offerMinutes; }
    public void setOfferMinutes(int value) { offerMinutes = value; }

    public long getAcceptedAt() { return acceptedAt; }
    public void setAcceptedAt(long value) { acceptedAt = value; }

    public long getDeadlineAt() { return deadlineAt; }
    public void setDeadlineAt(long value) { deadlineAt = value; }

    public String getStatus() { return status == null ? "" : status; }
    public void setStatus(String value) { status = value == null ? "" : value; }
}
