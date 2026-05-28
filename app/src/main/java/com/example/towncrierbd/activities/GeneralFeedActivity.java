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
import com.example.towncrierbd.utils.AppStrings;
import com.example.towncrierbd.utils.CategoryConfig;
import com.example.towncrierbd.utils.Constants;
import com.example.towncrierbd.utils.DistanceUtil;
import com.example.towncrierbd.utils.ExpiredPostCleaner;
import com.example.towncrierbd.utils.LanguageManager;
import com.example.towncrierbd.utils.NetworkMonitor;
import com.google.android.gms.location.*;
import com.google.android.material.badge.BadgeDrawable;
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

    private TextView tvWelcome, tvLocationName, tvRadius, tvToggleFilter;
    private TextView tvGoodDay, tvSeeNearby, tvNearbyPosts, tvFilterLabel;
    private View scrollChips;
    private ChipGroup chipGroup;
    private RecyclerView rvFeed;
    private FeedAdapter adapter;
    private FloatingActionButton fabAdd;
    private View layoutEmpty;
    private SwipeRefreshLayout swipeRefresh;
    private EditText etSearch;
    private View bannerNoInternet;
    private BottomNavigationView bottomNav;

    private DatabaseReference annRef, userRef, chatsRef;
    private FirebaseAuth auth;

    private static final int LOCATION_REQ     = 900;
    private static final int NOTIFICATION_REQ = 901;

    private FusedLocationProviderClient locationClient;
    private LocationCallback locationCallback;

    private boolean locationReady = false;
    private double myLat = 0.0, myLng = 0.0;

    private final List<Announcement> all = new ArrayList<>();
    private ValueEventListener feedListener;
    private ValueEventListener unreadListener;

    private String selectedCategory = CategoryConfig.CAT_ALL;
    private String myRole  = "";
    private String wantRole = "";
    private String searchQuery = "";
    private double selectedRadius = Constants.FEED_RADIUS_KM;

    private List<String> myPreferredCategories    = new ArrayList<>();
    private List<String> myPreferredSubcategories = new ArrayList<>();

    private NetworkMonitor networkMonitor;

    // ✅ FIX: Do NOT initialize here — context is null at field init time
    private AppStrings strings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_general_feed);

        // ✅ FIX: Initialize AFTER setContentView so context is ready
        strings = AppStrings.get(this);

        tvWelcome        = findViewById(R.id.tvWelcome);
        tvLocationName   = findViewById(R.id.tvLocationName);
        tvRadius         = findViewById(R.id.tvRadius);
        rvFeed           = findViewById(R.id.rvFeed);
        fabAdd           = findViewById(R.id.fabAdd);
        tvToggleFilter   = findViewById(R.id.tvToggleFilter);
        scrollChips      = findViewById(R.id.scrollChips);
        chipGroup        = findViewById(R.id.chipGroup);
        layoutEmpty      = findViewById(R.id.layoutEmpty);
        swipeRefresh     = findViewById(R.id.swipeRefresh);
        etSearch         = findViewById(R.id.etSearch);
        bannerNoInternet = findViewById(R.id.bannerNoInternet);
        bottomNav        = findViewById(R.id.bottomNav);

        // ✅ IDs for previously hardcoded strings
        tvGoodDay    = findViewById(R.id.tvGoodDay);
        tvSeeNearby  = findViewById(R.id.tvSeeNearby);
        tvNearbyPosts = findViewById(R.id.tvNearbyPosts);
        tvFilterLabel = findViewById(R.id.tvFilterLabel);

        // ✅ Apply all translatable strings
        applyStrings();

        adapter = new FeedAdapter(this);
        rvFeed.setLayoutManager(new LinearLayoutManager(this));
        rvFeed.setAdapter(adapter);

        auth     = FirebaseAuth.getInstance();
        annRef   = FirebaseDatabase.getInstance().getReference(Constants.DB_ANNOUNCEMENTS);
        userRef  = FirebaseDatabase.getInstance().getReference(Constants.DB_USERS);
        chatsRef = FirebaseDatabase.getInstance().getReference(Constants.DB_CHATS);

        ExpiredPostCleaner.cleanExpired(auth.getUid());

        fabAdd.setOnClickListener(v ->
                startActivity(new Intent(this, AddAnnouncementActivity.class)));

        updateRadiusText();

        if (tvRadius != null)
            tvRadius.setOnClickListener(v -> showRadiusDialog());

        if (tvToggleFilter != null && scrollChips != null) {
            tvToggleFilter.setOnClickListener(v -> {
                if (scrollChips.getVisibility() == View.GONE) {
                    scrollChips.setVisibility(View.VISIBLE);
                    tvToggleFilter.setText(strings.feedHideFilter());
                } else {
                    scrollChips.setVisibility(View.GONE);
                    tvToggleFilter.setText(strings.feedShowFilter());
                }
            });
        }

        if (chipGroup != null) buildCategoryChips();

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
                ExpiredPostCleaner.cleanExpired(auth.getUid());
                applyAndShow();
                swipeRefresh.setRefreshing(false);
            });
        }

        TextView tvLangToggle = findViewById(R.id.tvLangToggle);
        if (tvLangToggle != null) {
            tvLangToggle.setText(LanguageManager.getToggleLabel(this));
            tvLangToggle.setOnClickListener(v -> {
                LanguageManager.toggle(this);
                String uid = auth.getUid();
                if (uid != null) LanguageManager.saveToFirebase(this, uid);
                recreate();
            });
        }

        setupBottomNav();
        locationClient = LocationServices.getFusedLocationProviderClient(this);
        requestNotificationPermission();
        startNetworkMonitoring();
        loadMyRoleThenStart();
        listenForUnreadMessages();
    }

    // ✅ Apply all translatable strings to views
    private void applyStrings() {
        AppStrings s = AppStrings.get(this);

        if (tvGoodDay    != null) tvGoodDay.setText(s.feedGoodDay());
        if (tvSeeNearby  != null) tvSeeNearby.setText(s.feedSeeNearby());
        if (tvNearbyPosts != null) tvNearbyPosts.setText(s.feedNearbyPosts());
        if (tvFilterLabel != null) tvFilterLabel.setText(s.feedFilterCategory());
        if (tvToggleFilter != null) tvToggleFilter.setText(s.feedShowFilter());
        if (etSearch != null) etSearch.setHint(s.feedSearchHint());

        // No internet banner
        if (bannerNoInternet instanceof TextView)
            ((TextView) bannerNoInternet).setText(s.feedNoInternet());

        // Empty state
        if (layoutEmpty != null) {
            TextView tvEmptyTitle = layoutEmpty.findViewById(R.id.tvEmptyTitle);
            TextView tvEmptySub   = layoutEmpty.findViewById(R.id.tvEmptySub);
            if (tvEmptyTitle != null) tvEmptyTitle.setText(s.feedEmptyTitle());
            if (tvEmptySub   != null) tvEmptySub.setText(s.feedEmptySubtitle());
        }
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
                    if (!ChatActivity.isMyRoom(roomId, myUid)) continue;
                    String otherUid = ChatActivity.getOtherUidFromRoom(roomId, myUid);
                    if (otherUid == null) continue;
                    for (DataSnapshot msgSnap : roomSnap.getChildren()) {
                        String senderId = msgSnap.child("senderId").getValue(String.class);
                        Boolean read    = msgSnap.child("read").getValue(Boolean.class);
                        if (otherUid.equals(senderId) && (read == null || !read)) totalUnread++;
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
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        };
        chatsRef.addValueEventListener(unreadListener);
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                        Manifest.permission.POST_NOTIFICATIONS)) {
                    new AlertDialog.Builder(this)
                            .setTitle(strings.notifEnableTitle())
                            .setMessage(strings.notifEnableMsgAnn())
                            .setPositiveButton(strings.notifAllow(), (d, w) ->
                                    ActivityCompat.requestPermissions(this,
                                            new String[]{Manifest.permission.POST_NOTIFICATIONS},
                                            NOTIFICATION_REQ))
                            .setNegativeButton(strings.notifNotNow(), null).show();
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
        AppStrings s = AppStrings.get(this);
        double[] values = {1.0, 3.0, 5.0, 10.0};
        new AlertDialog.Builder(this)
                .setTitle(s.feedSelectRadius())
                .setItems(s.feedRadiusOptions(), (d, which) -> {
                    selectedRadius = values[which];
                    updateRadiusText();
                    applyAndShow();
                }).show();
    }

    private void updateRadiusText() {
        if (tvRadius != null)
            tvRadius.setText(AppStrings.get(this).feedWithinKmBanner(selectedRadius));
    }

    private void loadMyRoleThenStart() {
        String uid = auth.getUid();
        if (uid == null) return;
        userRef.child(uid).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                UserModel u = snapshot.getValue(UserModel.class);
                if (u == null) return;
                myRole = safe(u.getRole());
                if (myRole.isEmpty()) myRole = Constants.ROLE_USER;
                wantRole = Constants.ROLE_ANNOUNCER.equals(myRole)
                        ? Constants.ROLE_USER : Constants.ROLE_ANNOUNCER;
                myPreferredCategories    = u.getHawkerCategories();
                myPreferredSubcategories = u.getHawkerSubcategories();
                if (u.getName() != null)
                    tvWelcome.setText(strings.feedWelcomeBack() + u.getName() + "!");
                if (u.getLanguage() != null && !u.getLanguage().isEmpty()) {
                    LanguageManager.setLanguage(GeneralFeedActivity.this, u.getLanguage());
                }
                attachFeedListenerOnce();
                startLiveLocation();
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(GeneralFeedActivity.this,
                        strings.feedProfileFailed(), Toast.LENGTH_SHORT).show();
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
                Toast.makeText(GeneralFeedActivity.this,
                        strings.feedLoadFailed() + error.getMessage(), Toast.LENGTH_SHORT).show();
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
            if (myUid != null && myUid.equals(a.getUserId())) continue;
            if (a.getExpireAt() > 0 && now > a.getExpireAt()) continue;
            String postRole = safe(a.getUserRole());
            if (!wantRole.equals(postRole)) continue;
            if (!myPreferredCategories.isEmpty()) {
                boolean matchesPreference = false;
                List<String> postCategories = a.getSelectedCategories();
                if (postCategories != null) {
                    for (String pc : postCategories) {
                        if (myPreferredCategories.contains(pc)) { matchesPreference = true; break; }
                    }
                }
                if (!matchesPreference) {
                    String primaryCat = safe(a.getCategory());
                    if (myPreferredCategories.contains(primaryCat)) matchesPreference = true;
                }
                if (!matchesPreference) continue;
            }
            if (!CategoryConfig.CAT_ALL.equals(selectedCategory)) {
                String c = a.getCategory() == null ? "" : a.getCategory().trim();
                if (!selectedCategory.equals(c)) continue;
            }
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
        if (layoutEmpty != null)
            layoutEmpty.setVisibility(out.isEmpty() && locationReady ? View.VISIBLE : View.GONE);
    }

    private void buildCategoryChips() {
        chipGroup.removeAllViews();
        boolean isBn = !LanguageManager.isEnglish(this);
        // "All" chip
        addChip(CategoryConfig.CAT_ALL, isBn ? CategoryConfig.CAT_ALL_BN : CategoryConfig.CAT_ALL, true);
        for (com.example.towncrierbd.utils.CategoryConfig.HawkerCategory cat : CategoryConfig.HAWKER_CATEGORIES) {
            addChip(cat.name, isBn ? cat.nameBn : cat.name, false);
        }
        chipGroup.setOnCheckedChangeListener((group, checkedId) -> {
            Chip ch = group.findViewById(checkedId);
            if (ch != null) {
                // Tag stores English key
                Object tag = ch.getTag();
                selectedCategory = (tag != null) ? tag.toString() : ch.getText().toString();
                applyAndShow();
            }
        });
    }

    private void addChip(String englishKey, String displayText, boolean checked) {
        Chip chip = new Chip(this);
        chip.setText(displayText);
        chip.setTag(englishKey); // always English for filtering
        chip.setCheckable(true);
        chip.setChecked(checked);
        chipGroup.addView(chip);
    }

    private void startLiveLocation() {
        if (!isLocationEnabled()) {
            if (tvLocationName != null) tvLocationName.setText(strings.feedTurnOnGps());
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
                Address a    = list.get(0);
                String sub   = safe(a.getSubLocality());
                String local = safe(a.getLocality());
                if (local.isEmpty()) local = safe(a.getSubAdminArea());
                if (local.isEmpty()) local = safe(a.getAdminArea());
                if (!sub.isEmpty() && !local.isEmpty()) return sub + ", " + local;
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
            if (tvLocationName != null) tvLocationName.setText(strings.feedPermissionDenied());
            Toast.makeText(this, strings.feedPermissionDenied(), Toast.LENGTH_SHORT).show();
        }
    }
}