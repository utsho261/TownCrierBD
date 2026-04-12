package com.example.towncrierbd.activities;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.towncrierbd.R;
import com.example.towncrierbd.adapters.FeedAdapter;
import com.example.towncrierbd.models.Announcement;
import com.example.towncrierbd.models.UserModel;
import com.example.towncrierbd.utils.CategoryConfig;
import com.example.towncrierbd.utils.Constants;
import com.example.towncrierbd.utils.DistanceUtil;
import com.google.android.gms.location.*;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class GeneralFeedActivity extends AppCompatActivity {

    private TextView tvWelcome, tvLocationName, tvRadius;

    private TextView tvToggleFilter;
    private View scrollChips;
    private ChipGroup chipGroup;

    private RecyclerView rvFeed;
    private FeedAdapter adapter;
    private FloatingActionButton fabAdd;

    private DatabaseReference annRef, userRef;
    private FirebaseAuth auth;

    private static final int LOCATION_REQ = 900;
    private FusedLocationProviderClient locationClient;
    private LocationCallback locationCallback;

    private boolean locationReady = false;
    private double myLat = 0.0, myLng = 0.0;

    private final List<Announcement> all = new ArrayList<>();
    private ValueEventListener feedListener;

    private String selectedCategory = CategoryConfig.CAT_ALL;

    private String myRole = "";
    private String wantRole = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_general_feed);

        tvWelcome = findViewById(R.id.tvWelcome);
        tvLocationName = findViewById(R.id.tvLocationName);
        tvRadius = findViewById(R.id.tvRadius);

        rvFeed = findViewById(R.id.rvFeed);
        fabAdd = findViewById(R.id.fabAdd);

        tvToggleFilter = findViewById(R.id.tvToggleFilter);
        scrollChips = findViewById(R.id.scrollChips);
        chipGroup = findViewById(R.id.chipGroup);

        adapter = new FeedAdapter(this);
        rvFeed.setLayoutManager(new LinearLayoutManager(this));
        rvFeed.setAdapter(adapter);

        auth = FirebaseAuth.getInstance();
        annRef = FirebaseDatabase.getInstance().getReference(Constants.DB_ANNOUNCEMENTS);
        userRef = FirebaseDatabase.getInstance().getReference(Constants.DB_USERS);

        fabAdd.setOnClickListener(v -> startActivity(new Intent(this, AddAnnouncementActivity.class)));

        if (tvRadius != null) tvRadius.setText("within " + Constants.FEED_RADIUS_KM + " km");

        if (tvToggleFilter != null && scrollChips != null) {
            tvToggleFilter.setOnClickListener(v -> {
                if (scrollChips.getVisibility() == View.GONE) {
                    scrollChips.setVisibility(View.VISIBLE);
                    tvToggleFilter.setText("Hide");
                } else {
                    scrollChips.setVisibility(View.GONE);
                    tvToggleFilter.setText("Show");
                }
            });
        }

        if (chipGroup != null) buildCategoryChips();

        BottomNavigationView nav = findViewById(R.id.bottomNav);
        if (nav != null) {
            nav.setSelectedItemId(R.id.menu_feed);
            nav.setOnItemSelectedListener(item -> {
                if (item.getItemId() == R.id.menu_map) {
                    startActivity(new Intent(this, MapsActivity.class));
                    return true;
                } else if (item.getItemId() == R.id.menu_profile) {
                    startActivity(new Intent(this, ProfileActivity.class));
                    return true;
                }
                return true;
            });
        }

        locationClient = LocationServices.getFusedLocationProviderClient(this);

        loadMyRoleThenStart();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (feedListener != null) annRef.removeEventListener(feedListener);
        if (locationClient != null && locationCallback != null) locationClient.removeLocationUpdates(locationCallback);
        if (adapter != null) adapter.release();
    }

    // ================= Step-5: Load my role =================

    private void loadMyRoleThenStart() {
        String uid = auth.getUid();
        if (uid == null) return;

        userRef.child(uid).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                UserModel u = snapshot.getValue(UserModel.class);
                if (u == null) return;

                myRole = safe(u.getRole());
                if (myRole.isEmpty()) myRole = Constants.ROLE_USER;

                // opposite
                wantRole = Constants.ROLE_ANNOUNCER.equals(myRole)
                        ? Constants.ROLE_USER
                        : Constants.ROLE_ANNOUNCER;

                // welcome
                if (u.getName() != null) tvWelcome.setText("Welcome Back, " + u.getName() + "!");

                attachFeedListenerOnce();
                startLiveLocation();
            }

            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void attachFeedListenerOnce() {
        if (feedListener != null) return;

        feedListener = new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                all.clear();
                for (DataSnapshot s : snapshot.getChildren()) {
                    Announcement a = s.getValue(Announcement.class);
                    if (a == null) continue;
                    if (a.getId() == null || a.getId().trim().isEmpty()) a.setId(s.getKey());
                    all.add(a);
                }
                applyAndShow();
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        };

        annRef.addValueEventListener(feedListener);
    }

    // ================= Step-5: FILTER CORE =================

    private void applyAndShow() {
        String myUid = auth.getUid();
        long now = System.currentTimeMillis();
        List<Announcement> out = new ArrayList<>();

        for (Announcement a : all) {
            if (a == null) continue;
            if (myUid != null && myUid.equals(a.getUserId())) continue;

            // ✅ Expired বাদ
            if (a.getExpireAt() > 0 && now > a.getExpireAt()) continue;

            String postRole = safe(a.getUserRole());
            if (!wantRole.equals(postRole)) continue;

            if (!CategoryConfig.CAT_ALL.equals(selectedCategory)) {
                String c = a.getCategory() == null ? "" : a.getCategory().trim();
                if (!selectedCategory.equals(c)) continue;
            }

            if (!locationReady) continue;
            double dist = DistanceUtil.distanceKm(myLat, myLng, a.getLat(), a.getLng());
            if (dist <= Constants.FEED_RADIUS_KM) out.add(a);
        }

        adapter.setData(out);
        if (locationReady) adapter.setMyLocation(myLat, myLng);
    }

    private void buildCategoryChips() {
        chipGroup.removeAllViews();

        addChip(CategoryConfig.CAT_ALL, true);
        for (String c : CategoryConfig.MAIN) addChip(c, false);

        chipGroup.setOnCheckedChangeListener((group, checkedId) -> {
            Chip ch = group.findViewById(checkedId);
            if (ch != null) {
                selectedCategory = ch.getText().toString();
                applyAndShow();
            }
        });
    }

    private void addChip(String text, boolean checked) {
        Chip chip = new Chip(this);
        chip.setText(text);
        chip.setCheckable(true);
        chip.setChecked(checked);
        chipGroup.addView(chip);
    }

    // ================= LIVE LOCATION =================

    private void startLiveLocation() {
        if (!isLocationEnabled()) {
            tvLocationName.setText("Turn ON GPS");
            startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
            return;
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_REQ);
            return;
        }

        LocationRequest req = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000)
                .setMinUpdateIntervalMillis(1500)
                .build();

        locationCallback = new LocationCallback() {
            @Override public void onLocationResult(@NonNull LocationResult result) {
                Location loc = result.getLastLocation();
                if (loc == null) return;

                myLat = loc.getLatitude();
                myLng = loc.getLongitude();
                locationReady = true;

                String nice = getNiceLocationName(myLat, myLng);
                tvLocationName.setText(nice);

                updateUserLocationInFirebase(myLat, myLng, nice);

                adapter.setMyLocation(myLat, myLng);
                applyAndShow();
            }
        };

        locationClient.requestLocationUpdates(req, locationCallback, Looper.getMainLooper());
    }

    private String getNiceLocationName(double lat, double lng) {
        try {
            Geocoder g = new Geocoder(this, Locale.getDefault());
            List<Address> list = g.getFromLocation(lat, lng, 1);
            if (list != null && !list.isEmpty()) {
                Address a = list.get(0);

                String subLocal = safe(a.getSubLocality());
                String local = safe(a.getLocality());
                if (local.isEmpty()) local = safe(a.getSubAdminArea());
                if (local.isEmpty()) local = safe(a.getAdminArea());

                if (!subLocal.isEmpty() && !local.isEmpty()) return subLocal + ", " + local;
                if (!local.isEmpty()) return local;
            }
        } catch (Exception ignored) {}
        return "Bangladesh";
    }

    private void updateUserLocationInFirebase(double lat, double lng, String name) {
        String uid = auth.getUid();
        if (uid == null) return;
        DatabaseReference ref = userRef.child(uid);
        ref.child("lat").setValue(lat);
        ref.child("lng").setValue(lng);
        ref.child("locationName").setValue(name);
    }

    private String safe(String s) { return s == null ? "" : s.trim(); }

    private boolean isLocationEnabled() {
        LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        return lm != null && (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
                || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_REQ && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startLiveLocation();
        } else if (requestCode == LOCATION_REQ) {
            tvLocationName.setText("Permission denied");
            Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show();
        }
    }
}
