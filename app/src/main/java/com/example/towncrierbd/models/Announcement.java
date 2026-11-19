package com.example.towncrierbd.models;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Announcement {
    private String sourceAnnouncementId = "";
    public String getSourceAnnouncementId() { return sourceAnnouncementId == null ? "" : sourceAnnouncementId; }
    public void setSourceAnnouncementId(String value) { sourceAnnouncementId = value; }
    private String sourceAnnouncerUid = "";
    public String getSourceAnnouncerUid() { return sourceAnnouncerUid == null ? "" : sourceAnnouncerUid; }
    public void setSourceAnnouncerUid(String value) { sourceAnnouncerUid = value; }
    private String sourcePostTitle = "";
    public String getSourcePostTitle() { return sourcePostTitle == null ? "" : sourcePostTitle; }
    public void setSourcePostTitle(String value) { sourcePostTitle = value; }
    private String acceptedByUid = "";
    public String getAcceptedByUid() { return acceptedByUid == null ? "" : acceptedByUid; }
    public void setAcceptedByUid(String value) { acceptedByUid = value; }
    private String acceptedByName = "";
    public String getAcceptedByName() { return acceptedByName == null ? "" : acceptedByName; }
    public void setAcceptedByName(String value) { acceptedByName = value; }
    private String cancelledByUid = "";
    public String getCancelledByUid() { return cancelledByUid == null ? "" : cancelledByUid; }
    public void setCancelledByUid(String value) { cancelledByUid = value; }
    private String cancelledByName = "";
    public String getCancelledByName() { return cancelledByName == null ? "" : cancelledByName; }
    public void setCancelledByName(String value) { cancelledByName = value; }
    private String cancellationReason = "";
    public String getCancellationReason() { return cancellationReason == null ? "" : cancellationReason; }
    public void setCancellationReason(String value) { cancellationReason = value; }
    private long acceptedAt;
    public long getAcceptedAt() { return acceptedAt; }
    public void setAcceptedAt(long value) { acceptedAt = value; }
    private long cancelledAt;
    public long getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(long value) { cancelledAt = value; }
    private long statusUpdatedAt;
    public long getStatusUpdatedAt() { return statusUpdatedAt; }
    public void setStatusUpdatedAt(long value) { statusUpdatedAt = value; }
    private long workflowRevision;
    public long getWorkflowRevision() { return workflowRevision; }
    public void setWorkflowRevision(long value) { workflowRevision = value; }


    private String id;
    private String userId;
    private String userName;
    private String userRole;

    private String category;
    private String subcategory;
    private String customSubcategory;

    // ✅ Multi-category & product selection
    private List<String> selectedCategories;
    private List<String> selectedSubcategories;
    private List<String> selectedProducts;

    // ✅ "announcement" (Announcer post) or "request" (General User post)
    private String postType;
    private String requestMode = "active_posts";

    public String getRequestMode() { return com.example.towncrierbd.utils.RequestMode.normalize(requestMode); }
    public void setRequestMode(String value) { requestMode = com.example.towncrierbd.utils.RequestMode.normalize(value); }

    private String title;
    private String description;
    private String phone;

    private String imageUrl;
    private List<String> imageUrls;
    private String audioUrl;

    private double lat;
    private double lng;

    private long time;
    private long expireAt;
    private boolean active = true;

    private String status = "open";
    private String deliveredByUid;
    private String deliveredByName;
    private long deliveredAt;
    private long receivedAt;
    private String contactMethod = "chat";
    private boolean showPhone;
    private String priority = "normal";
    private int frequencyMinutes;
    private double requestRadiusKm = 1;
    private List<RequestedItem> requestedItems;

    public String getStatus() { return status == null ? "open" : status; }
    public void setStatus(String value) { status = value; }
    public String getDeliveredByUid() { return deliveredByUid; }
    public void setDeliveredByUid(String value) { deliveredByUid = value; }
    public String getDeliveredByName() { return deliveredByName; }
    public void setDeliveredByName(String value) { deliveredByName = value; }
    public long getDeliveredAt() { return deliveredAt; }
    public void setDeliveredAt(long value) { deliveredAt = value; }
    public long getReceivedAt() { return receivedAt; }
    public void setReceivedAt(long value) { receivedAt = value; }
    public String getContactMethod() { return contactMethod == null ? "chat" : contactMethod; }
    public void setContactMethod(String value) { contactMethod = value; }
    public boolean isShowPhone() { return showPhone; }
    public void setShowPhone(boolean value) { showPhone = value; }
    public String getPriority() { return priority == null ? "normal" : priority; }
    public void setPriority(String value) { priority = value; }
    public int getFrequencyMinutes() { return frequencyMinutes; }
    public void setFrequencyMinutes(int value) { frequencyMinutes = value; }
    public double getRequestRadiusKm() { return requestRadiusKm; }
    public void setRequestRadiusKm(double value) { requestRadiusKm = value; }
    public List<RequestedItem> getRequestedItems() { return requestedItems; }
    public void setRequestedItems(List<RequestedItem> value) { requestedItems = value; }

    private long originalExpireAt;
    private Map<String, RequestBid> acceptedBids;
    public long getOriginalExpireAt() { return originalExpireAt; }
    public void setOriginalExpireAt(long value) { originalExpireAt = value; }
    public Map<String, RequestBid> getAcceptedBids() { return acceptedBids; }
    public void setAcceptedBids(Map<String, RequestBid> value) { acceptedBids = value; }

    public Announcement() {}

    // ── Standard getters ──────────────────────────────────────────────────
    public String getId()                { return id; }
    public String getUserId()            { return userId; }
    public String getUserName()          { return userName; }
    public String getUserRole()          { return userRole; }
    public String getCategory()          { return category; }
    public String getSubcategory()       { return subcategory; }
    public String getCustomSubcategory() { return customSubcategory; }
    public String getTitle()             { return title; }
    public String getDescription()       { return description; }
    public String getPhone()             { return phone; }
    public String getImageUrl()          { return imageUrl; }
    public List<String> getImageUrls()   { return imageUrls; }
    public String getAudioUrl()          { return audioUrl; }
    public double getLat()               { return lat; }
    public double getLng()               { return lng; }
    public long   getTime()              { return time; }
    public long   getExpireAt()          { return expireAt; }
    public boolean isActive()            { return active; }

    // ✅ NEW getters
    public String       getPostType()              { return postType; }
    public List<String> getSelectedCategories()    { return selectedCategories; }
    public List<String> getSelectedSubcategories() { return selectedSubcategories; }
    public List<String> getSelectedProducts()      { return selectedProducts; }

    // ── Standard setters ──────────────────────────────────────────────────
    public void setId(String id)                   { this.id = id; }
    public void setUserId(String userId)           { this.userId = userId; }
    public void setUserName(String userName)       { this.userName = userName; }
    public void setUserRole(String userRole)       { this.userRole = userRole; }
    public void setCategory(String category)       { this.category = category; }
    public void setSubcategory(String subcategory) { this.subcategory = subcategory; }
    public void setCustomSubcategory(String s)     { this.customSubcategory = s; }
    public void setTitle(String title)             { this.title = title; }
    public void setDescription(String d)           { this.description = d; }
    public void setPhone(String phone)             { this.phone = phone; }
    public void setImageUrl(String imageUrl)       { this.imageUrl = imageUrl; }
    public void setImageUrls(List<String> urls)    { this.imageUrls = urls; }
    public void setAudioUrl(String audioUrl)       { this.audioUrl = audioUrl; }
    public void setLat(double lat)                 { this.lat = lat; }
    public void setLng(double lng)                 { this.lng = lng; }
    public void setTime(long time)                 { this.time = time; }
    public void setExpireAt(long expireAt)         { this.expireAt = expireAt; }
    public void setActive(boolean active)          { this.active = active; }

    // ✅ NEW setters
    public void setPostType(String postType)              { this.postType = postType; }
    public void setSelectedCategories(List<String> l)     { this.selectedCategories = l; }
    public void setSelectedSubcategories(List<String> l)  { this.selectedSubcategories = l; }
    public void setSelectedProducts(List<String> l)       { this.selectedProducts = l; }

    // ── Display label ──────────────────────────────────────────────────────
    public String getDisplayCategoryLabel() {
        String cs = (customSubcategory == null) ? "" : customSubcategory.trim();
        String sc = (subcategory == null)       ? "" : subcategory.trim();
        if (!cs.isEmpty()) return cs;
        if (!sc.isEmpty() && !"Other".equalsIgnoreCase(sc)) return sc;
        return (category == null) ? "" : category.trim();
    }

    // ── Product summary ────────────────────────────────────────────────────

    @Deprecated
    public String getProductSummary() {
        if (selectedProducts == null || selectedProducts.isEmpty()) return "";
        int count = Math.min(3, selectedProducts.size());
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i > 0) sb.append(" • ");
            sb.append(selectedProducts.get(i));
        }
        if (selectedProducts.size() > 3)
            sb.append(" +").append(selectedProducts.size() - 3).append(" more");
        return sb.toString();
    }


    public String getProductSummary(String moreSuffix) {
        if (selectedProducts == null || selectedProducts.isEmpty()) return "";
        int count = Math.min(3, selectedProducts.size());
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i > 0) sb.append(" • ");
            sb.append(selectedProducts.get(i));
        }
        if (selectedProducts.size() > 3) sb.append(moreSuffix);
        return sb.toString();
    }
}