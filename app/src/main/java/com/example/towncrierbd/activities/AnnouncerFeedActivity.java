package com.example.towncrierbd.activities;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.towncrierbd.R;
import com.example.towncrierbd.adapters.FeedAdapter;
import com.example.towncrierbd.models.Announcement;
import com.example.towncrierbd.models.UserModel;
import com.example.towncrierbd.utils.Constants;
import com.example.towncrierbd.utils.DistanceUtil;
import com.example.towncrierbd.utils.ExpiredPostCleaner;
import com.example.towncrierbd.utils.NetworkMonitor;
import com.google.android.gms.location.*;
import com.google.android.material.badge.BadgeDrawable;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;

import java.util.*;

public class AnnouncerFeedActivity extends AppCompatActivity {

    private TextView tvWelcome, tvLocationName, tvRadius;
    private RecyclerView rvFeed;
    private FeedAdapter adapter;
    private FloatingActionButton fabAdd;
    private View layoutEmpty;
    private SwipeRefreshLayout swipeRefresh;
    private EditText etSearch;
    private View bannerNoInternet;
    private BottomNavigationView bottomNav;

    private FirebaseAuth auth;
    private DatabaseReference annRef, userRef, chatsRef;

    private static final int LOCATION_REQ     = 900;
    private static final int NOTIFICATION_REQ = 901;

    private FusedLocationProviderClient locationClient;
    private LocationCallback locationCallback;

    private boolean locationReady = false;
    private double myLat = 0.0, myLng = 0.0;

    private final List<Announcement> all = new ArrayList<>();
    private ValueEventListener feedListener;
    private ValueEventListener unreadListener;

    private final Map<String, String> roleCache = new HashMap<>();
    private final Set<String> roleFetching = new HashSet<>();

    private String searchQuery = "";
    private double selectedRadius = Constants.FEED_RADIUS_KM;

    // Announcer's own categories (set during signup/profile)
    private List<String> myHawkerCategories    = new ArrayList<>();
    private List<String> myHawkerSubcategories = new ArrayList<>();

    private NetworkMonitor networkMonitor;
    private UserModel currentUser;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_announcer_feed);

        tvWelcome        = findViewById(R.id.tvWelcome);
        tvLocationName   = findViewById(R.id.tvLocationName);
        tvRadius         = findViewById(R.id.tvRadius);
        rvFeed           = findViewById(R.id.rvFeed);
        fabAdd           = findViewById(R.id.fabAdd);
        layoutEmpty      = findViewById(R.id.layoutEmpty);
        swipeRefresh     = findViewById(R.id.swipeRefresh);
        etSearch         = findViewById(R.id.etSearch);
        bannerNoInternet = findViewById(R.id.bannerNoInternet);
        bottomNav        = findViewById(R.id.bottomNav);

        adapter = new FeedAdapter(this);
        rvFeed.setLayoutManager(new LinearLayoutManager(this));
        rvFeed.setAdapter(adapter);

        auth     = FirebaseAuth.getInstance();
        annRef   = FirebaseDatabase.getInstance().getReference(Constants.DB_ANNOUNCEMENTS);
        userRef  = FirebaseDatabase.getInstance().getReference(Constants.DB_USERS);
        chatsRef = FirebaseDatabase.getInstance().getReference(Constants.DB_CHATS);

        ExpiredPostCleaner.cleanExpired();

        updateRadiusText();

        if (tvRadius != null) {
            tvRadius.setOnClickListener(v -> showRadiusDialog());
        }

        if (etSearch != null) {
            etSearch.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
                @Override public void afterTextChanged(Editable s) {}
                @Override public void onTextChanged(CharSequence s, int st, int b, int c) {
                    searchQuery = s.toString().trim().toLowerCase();
                    applyAndShow();
                }
            });
        }

        if (swipeRefresh != null) {
            swipeRefresh.setOnRefreshListener(() -> {
                ExpiredPostCleaner.cleanExpired();
                applyAndShow();
                swipeRefresh.setRefreshing(false);
            });
        }

        // FAB - fixed visibility and click
        if (fabAdd != null) {
            fabAdd.setVisibility(View.VISIBLE);
            fabAdd.bringToFront();
            fabAdd.setOnClickListener(v ->
                    startActivity(new Intent(this, AddAnnouncementActivity.class)));
        }

        setupBottomNav();

        locationClient = LocationServices.getFusedLocationProviderClient(this);

        requestNotificationPermission();
        startNetworkMonitoring();
        loadUserThenStart();
        listenForUnreadMessages();
    }

    private void setupBottomNav() {
        if (bottomNav == null) return;
        bottomNav.setSelectedItemId(R.id.menu_feed);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.menu_map) {
                startActivity(new Intent(this, MapsActivity.class));
                return true;
            } else if (id == R.id.menu_inbox) {
                startActivity(new Intent(this, InboxActivity.class));
                return true;
            } else if (id == R.id.menu_profile) {
                startActivity(new Intent(this, ProfileActivity.class));
                return true;
            }
            return true;
        });
    }

    private void listenForUnreadMessages() {
        String myUid = auth.getUid();
        if (myUid == null || bottomNav == null) return;

        unreadListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                int totalUnread = 0;

                for (DataSnapshot roomSnap : snapshot.getChildren()) {
                    String roomId = roomSnap.getKey();
                    if (roomId == null) continue;

                    int sepIdx = roomId.indexOf('_');
                    if (sepIdx < 0) continue;
                    String p1 = roomId.substring(0, sepIdx);
                    String p2 = roomId.substring(sepIdx + 1);
                    if (!myUid.equals(p1) && !myUid.equals(p2)) continue;

                    String otherUid = myUid.equals(p1) ? p2 : p1;

                    for (DataSnapshot msgSnap : roomSnap.getChildren()) {
                        String senderId = msgSnap.child("senderId").getValue(String.class);
                        Boolean read = msgSnap.child("read").getValue(Boolean.class);
                        // Only count messages FROM the other person, not our own
                        if (otherUid.equals(senderId) && (read == null || !read)) {
                            totalUnread++;
                        }
                    }
                }

                final int unread = totalUnread;
                runOnUiThread(() -> {
                    try {
                        BadgeDrawable badge = bottomNav.getOrCreateBadge(R.id.menu_inbox);
                        if (unread > 0) {
                            badge.setVisible(true);
                            badge.setNumber(unread);
                        } else {
                            badge.setVisible(false);
                            badge.clearNumber();
                        }
                    } catch (Exception ignored) {}
                });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };

        chatsRef.addValueEventListener(unreadListener);
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {

                if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                        Manifest.permission.POST_NOTIFICATIONS)) {
                    new AlertDialog.Builder(this)
                            .setTitle("Enable Notifications")
                            .setMessage("Town Crier BD sends notifications when new announcements are nearby.")
                            .setPositiveButton("Allow", (d, w) ->
                                    ActivityCompat.requestPermissions(this,
                                            new String[]{Manifest.permission.POST_NOTIFICATIONS},
                                            NOTIFICATION_REQ))
                            .setNegativeButton("Not now", null)
                            .show();
                } else {
                    ActivityCompat.requestPermissions(this,
                            new String[]{Manifest.permission.POST_NOTIFICATIONS},
                            NOTIFICATION_REQ);
                }
            }
        }
    }

    private void startNetworkMonitoring() {
        networkMonitor = new NetworkMonitor(this);
        if (!networkMonitor.isConnected()) showNoBanner(true);

        networkMonitor.startMonitoring(new NetworkMonitor.NetworkCallback() {
            @Override public void onAvailable() { showNoBanner(false); }
            @Override public void onLost()      { showNoBanner(true);  }
        });
    }

    private void showNoBanner(boolean show) {
        if (bannerNoInternet != null)
            bannerNoInternet.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        moveTaskToBack(true);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (feedListener != null)   annRef.removeEventListener(feedListener);
        if (unreadListener != null) chatsRef.removeEventListener(unreadListener);
        if (locationClient != null && locationCallback != null)
            locationClient.removeLocationUpdates(locationCallback);
        if (adapter != null) adapter.release();
        if (networkMonitor != null) networkMonitor.stopMonitoring();
    }

    private void showRadiusDialog() {
        String[] options = {"1 km", "3 km", "5 km", "10 km"};
        double[] values  = {1.0, 3.0, 5.0, 10.0};
        new AlertDialog.Builder(this)
                .setTitle("Select Radius")
                .setItems(options, (d, which) -> {
                    selectedRadius = values[which];
                    updateRadiusText();
                    applyAndShow();
                })
                .show();
    }

    private void updateRadiusText() {
        if (tvRadius != null)
            tvRadius.setText("within " + (int) selectedRadius + " km  ▾");
    }

    private void loadUserThenStart() {
        String uid = auth.getUid();
        if (uid == null) return;
        userRef.child(uid).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                UserModel u = snapshot.getValue(UserModel.class);
                if (u != null) {
                    currentUser = u;
                    if (u.getName() != null)
                        tvWelcome.setText("Welcome Back, " + u.getName() + "! 👋");

                    // Load announcer's own selling categories
                    myHawkerCategories    = u.getHawkerCategories();
                    myHawkerSubcategories = u.getHawkerSubcategories();
                }
                attachFeedListenerOnce();
                startLiveLocation();
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(AnnouncerFeedActivity.this,
                        "Failed to load profile.", Toast.LENGTH_SHORT).show();
                attachFeedListenerOnce();
                startLiveLocation();
            }
        });
    }

    private void attachFeedListenerOnce() {
        if (feedListener != null) return;

        feedListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                all.clear();
                for (DataSnapshot s : snapshot.getChildren()) {
                    Announcement a = s.getValue(Announcement.class);
                    if (a == null) continue;
                    if (a.getId() == null || a.getId().trim().isEmpty()) a.setId(s.getKey());
                    all.add(a);
                }
                applyAndShow();
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(AnnouncerFeedActivity.this,
                        "Failed to load feed: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        };

        annRef.addValueEventListener(feedListener);
    }

    private void applyAndShow() {
        String myUid = auth.getUid();
        long now = System.currentTimeMillis();
        List<Announcement> out = new ArrayList<>();

        for (Announcement a : all) {
            if (a == null) continue;
            // Don't show own posts
            if (myUid != null && myUid.equals(a.getUserId())) continue;
            // Don't show expired
            if (a.getExpireAt() > 0 && now > a.getExpireAt()) continue;

            // Announcer sees posts from General Users (requests)
            String postRole = resolvePostRole(a);
            if (!Constants.ROLE_USER.equals(postRole)) continue;

            // ── FEATURE #5: If announcer has set categories, only show
            // posts that match those categories (buyers looking for what announcer sells)
            if (!myHawkerCategories.isEmpty()) {
                boolean matches = false;
                // Check primary category
                String primaryCat = safe(a.getCategory());
                if (myHawkerCategories.contains(primaryCat)) matches = true;
                // Check selectedCategories list
                if (!matches && a.getSelectedCategories() != null) {
                    for (String pc : a.getSelectedCategories()) {
                        if (myHawkerCategories.contains(pc)) { matches = true; break; }
                    }
                }
                if (!matches) continue;
            }

            // Search filter
            if (!searchQuery.isEmpty()) {
                String title = safe(a.getTitle()).toLowerCase();
                String desc  = safe(a.getDescription()).toLowerCase();
                String cat   = safe(a.getCategory()).toLowerCase();
                if (!title.contains(searchQuery) &&
                        !desc.contains(searchQuery) &&
                        !cat.contains(searchQuery)) continue;
            }

            if (!locationReady) continue;
            double dist = DistanceUtil.distanceKm(myLat, myLng, a.getLat(), a.getLng());
            if (dist <= selectedRadius) out.add(a);
        }

        adapter.setData(out);
        if (locationReady) adapter.setMyLocation(myLat, myLng);

        if (layoutEmpty != null) {
            layoutEmpty.setVisibility(out.isEmpty() && locationReady ? View.VISIBLE : View.GONE);
        }
    }

    private String resolvePostRole(Announcement a) {
        String r = safe(a.getUserRole());
        if (!r.isEmpty()) return r;

        String uid = safe(a.getUserId());
        if (uid.isEmpty()) return "";

        if (roleCache.containsKey(uid)) return roleCache.get(uid);

        if (!roleFetching.contains(uid)) {
            roleFetching.add(uid);
            userRef.child(uid).child("role").addListenerForSingleValueEvent(
                    new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                            String rr = safe(snapshot.getValue(String.class));
                            if (rr.isEmpty()) rr = Constants.ROLE_USER;
                            roleCache.put(uid, rr);
                            roleFetching.remove(uid);
                            applyAndShow();
                        }
                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {
                            roleFetching.remove(uid);
                        }
                    });
        }
        return "";
    }

    private void startLiveLocation() {
        if (!isLocationEnabled()) {
            if (tvLocationName != null) tvLocationName.setText("Turn ON GPS");
            startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
            return;
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_REQ);
            return;
        }

        LocationRequest req = new LocationRequest.Builder(
                Priority.PRIORITY_BALANCED_POWER_ACCURACY, 10000)
                .setMinUpdateIntervalMillis(5000)
                .build();

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult result) {
                Location loc = result.getLastLocation();
                if (loc == null) return;

                myLat = loc.getLatitude();
                myLng = loc.getLongitude();
                locationReady = true;

                String nice = getNiceLocationName(myLat, myLng);
                if (tvLocationName != null) tvLocationName.setText(nice);

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
                String sub  = safe(a.getSubLocality());
                String city = safe(a.getLocality());
                if (city.isEmpty()) city = safe(a.getSubAdminArea());
                if (city.isEmpty()) city = safe(a.getAdminArea());
                if (!sub.isEmpty() && !city.isEmpty()) return sub + ", " + city;
                if (!city.isEmpty()) return city;
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

    private boolean isLocationEnabled() {
        LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        return lm != null && (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
                || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER));
    }

    private String safe(String s) { return s == null ? "" : s.trim(); }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_REQ && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startLiveLocation();
        } else if (requestCode == LOCATION_REQ) {
            if (tvLocationName != null) tvLocationName.setText("Permission denied");
            Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show();
        }
    }
}