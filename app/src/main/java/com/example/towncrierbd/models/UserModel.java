package com.example.towncrierbd.models;

import java.util.ArrayList;
import java.util.List;

public class UserModel {
    private String uid;
    private String name;
    private String email;
    private String phone;
    private String role;

    private String locationName;
    private double lat;
    private double lng;

    // ── Hawker profile ─────────────────────────────────────────────────────
    // e.g. ["Vegetable Seller", "Fish Seller", "Others"]
    private List<String> hawkerCategories;

    // e.g. ["Spinach (Palong)", "Rui", "Katla", "My custom item"]
    private List<String> hawkerSubcategories;

    // "Others" category এ user-defined custom category name
    // e.g. "Flower pot seller"
    private String hawkerOthersName;

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
}