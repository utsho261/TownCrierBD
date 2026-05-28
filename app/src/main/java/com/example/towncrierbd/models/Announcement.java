package com.example.towncrierbd.models;

import java.util.List;

public class Announcement {

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

    private String title;
    private String description;
    private String phone;

    private String imageUrl;
    private String audioUrl;

    private double lat;
    private double lng;

    private long time;
    private long expireAt;

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
    public String getAudioUrl()          { return audioUrl; }
    public double getLat()               { return lat; }
    public double getLng()               { return lng; }
    public long   getTime()              { return time; }
    public long   getExpireAt()          { return expireAt; }

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
    public void setAudioUrl(String audioUrl)       { this.audioUrl = audioUrl; }
    public void setLat(double lat)                 { this.lat = lat; }
    public void setLng(double lng)                 { this.lng = lng; }
    public void setTime(long time)                 { this.time = time; }
    public void setExpireAt(long expireAt)         { this.expireAt = expireAt; }

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