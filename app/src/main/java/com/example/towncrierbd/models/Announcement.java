package com.example.towncrierbd.models;

public class Announcement {

    private String id;
    private String userId;
    private String userName;
    private String userRole;

    private String category;
    private String subcategory;
    private String customSubcategory;

    private String title;
    private String description;
    private String phone;

    private String imageBase64;

    private double lat;
    private double lng;

    private long time;
    private long expireAt;

    public Announcement() {}

    public String getId() { return id; }
    public String getUserId() { return userId; }
    public String getUserName() { return userName; }
    public String getUserRole() { return userRole; }

    public String getCategory() { return category; }
    public String getSubcategory() { return subcategory; }
    public String getCustomSubcategory() { return customSubcategory; }

    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getPhone() { return phone; }

    public String getImageBase64() { return imageBase64; }

    public double getLat() { return lat; }
    public double getLng() { return lng; }

    public long getTime() { return time; }
    public long getExpireAt() { return expireAt; }

    public void setId(String id) { this.id = id; }
    public void setUserId(String userId) { this.userId = userId; }
    public void setUserName(String userName) { this.userName = userName; }
    public void setUserRole(String userRole) { this.userRole = userRole; }

    public void setCategory(String category) { this.category = category; }
    public void setSubcategory(String subcategory) { this.subcategory = subcategory; }
    public void setCustomSubcategory(String customSubcategory) { this.customSubcategory = customSubcategory; }

    public void setTitle(String title) { this.title = title; }
    public void setDescription(String description) { this.description = description; }
    public void setPhone(String phone) { this.phone = phone; }

    public void setImageBase64(String imageBase64) { this.imageBase64 = imageBase64; }

    public void setLat(double lat) { this.lat = lat; }
    public void setLng(double lng) { this.lng = lng; }

    public void setTime(long time) { this.time = time; }
    public void setExpireAt(long expireAt) { this.expireAt = expireAt; }

    public String getDisplayCategoryLabel() {
        String cs = (customSubcategory == null) ? "" : customSubcategory.trim();
        String sc = (subcategory == null) ? "" : subcategory.trim();
        if (!cs.isEmpty()) return cs;
        if (!sc.isEmpty() && !"Other".equalsIgnoreCase(sc)) return sc;
        return (category == null) ? "" : category.trim();
    }
}
