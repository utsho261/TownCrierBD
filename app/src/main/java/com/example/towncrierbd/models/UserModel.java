package com.example.towncrierbd.models;

import java.util.ArrayList;
import java.util.List;

public class UserModel {
    private String photoUrl = "";
    public String getPhotoUrl() { return photoUrl == null ? "" : photoUrl; }
    public void setPhotoUrl(String value) { photoUrl = value; }

    private String uid;
    private String name;
    private String email;
    private String phone;
    private String role;

    private String locationName;
    private double lat;
    private double lng;
    private String language = "en";

    // ── Hawker profile ─────────────────────────────────────────────────────
    // e.g. ["Vegetable Seller", "Fish Seller", "Others"]
    private List<String> hawkerCategories;

    // e.g. ["Spinach (Palong)", "Rui", "Katla", "My custom item"]
    private List<String> hawkerSubcategories;

    // "Others" category এ user-defined custom category name
    // e.g. "Flower pot seller"
    private String hawkerOthersName;

    private double notificationRadiusKm = 1;
    private List<String> notificationCategories;
    private boolean notificationConfigured = false;
    private long locationUpdatedAt;
    private boolean online = false;
    private long lastSeen = 0;
    public boolean isOnline() { return online; }
    public void setOnline(boolean online) { this.online = online; }
    public long getLastSeen() { return lastSeen; }
    public void setLastSeen(long lastSeen) { this.lastSeen = lastSeen; }
    public double getNotificationRadiusKm() { return notificationRadiusKm; }
    public void setNotificationRadiusKm(double value) { notificationRadiusKm = value; }
    public List<String> getNotificationCategories() { return notificationCategories; }
    public void setNotificationCategories(List<String> value) { notificationCategories = value; }
    public boolean isNotificationConfigured() { return notificationConfigured || (notificationCategories != null && !notificationCategories.isEmpty()); }
    public void setNotificationConfigured(boolean value) { notificationConfigured = value; }
    public long getLocationUpdatedAt() { return locationUpdatedAt; }
    public void setLocationUpdatedAt(long value) { locationUpdatedAt = value; }
    public UserModel() {}

    public UserModel(String uid, String name, String email, String phone,
                     String role, String locationName, double lat, double lng) {
        this.uid          = uid;
        this.name         = name;
        this.email        = email;
        this.phone        = phone;
        this.role         = role;
        this.locationName = locationName;
        this.lat          = lat;
        this.lng          = lng;
    }

    // ── Standard getters ──────────────────────────────────────────────────
    public String getUid()          { return uid; }
    public String getName()         { return name; }
    public String getEmail()        { return email; }
    public String getPhone()        { return phone; }
    public String getRole()         { return role; }
    public String getLocationName() { return locationName; }
    public double getLat()          { return lat; }
    public double getLng()          { return lng; }

    // ── Hawker getters ────────────────────────────────────────────────────
    public List<String> getHawkerCategories() {
        return hawkerCategories != null ? hawkerCategories : new ArrayList<>();
    }
    public List<String> getHawkerSubcategories() {
        return hawkerSubcategories != null ? hawkerSubcategories : new ArrayList<>();
    }
    public String getHawkerOthersName() {
        return hawkerOthersName != null ? hawkerOthersName : "";
    }

    // ── Standard setters ──────────────────────────────────────────────────
    public void setUid(String uid)                   { this.uid = uid; }
    public void setName(String name)                 { this.name = name; }
    public void setEmail(String email)               { this.email = email; }
    public void setPhone(String phone)               { this.phone = phone; }
    public void setRole(String role)                 { this.role = role; }
    public void setLocationName(String locationName) { this.locationName = locationName; }
    public void setLat(double lat)                   { this.lat = lat; }
    public void setLng(double lng)                   { this.lng = lng; }

    // ── Hawker setters ────────────────────────────────────────────────────
    public void setHawkerCategories(List<String> hawkerCategories) {
        this.hawkerCategories = hawkerCategories;
    }
    public void setHawkerSubcategories(List<String> hawkerSubcategories) {
        this.hawkerSubcategories = hawkerSubcategories;
    }
    public void setHawkerOthersName(String hawkerOthersName) {
        this.hawkerOthersName = hawkerOthersName;
    }

    public String getLanguage() { return language == null ? "en" : language; }
    public void setLanguage(String language) { this.language = language; }
}