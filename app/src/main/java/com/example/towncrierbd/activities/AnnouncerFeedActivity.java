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
import com.example.towncrierbd.utils.*;
import android.widget.*;
import com.example.towncrierbd.adapters.FeedAdapter;
import com.example.towncrierbd.models.Announcement;
import com.example.towncrierbd.models.UserModel;
import com.example.towncrierbd.utils.*;
import com.google.android.gms.location.*;
import com.google.android.material.badge.BadgeDrawable;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;

import java.util.*;

public class AnnouncerFeedActivity extends AppCompatActivity {

    private TextView tvWelcome, tvLocationName, tvRadius;
    private TextView tvNearbyPosts, tvYourLocation;
    private RecyclerView rvFeed;
    private FeedAdapter adapter;
    private PostInteractions interactions;
    private boolean messagePostsOnly=false;
    private int visibleCount=5;
    private boolean myRequests=false;
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

    private final Map<String, String> roleCache   = new HashMap<>();
    private final Set<String>         roleFetching = new HashSet<>();

    private String searchQuery   = "";
    private double selectedRadius;

    private List<String> myHawkerCategories    = new ArrayList<>();
    private List<String> myHawkerSubcategories = new ArrayList<>();

    private NetworkMonitor networkMonitor;
    private UserModel currentUser;
    private AppStrings strings;
    private Button btnTrackAcceptedPaths;
    private final android.os.Handler bidTicker = new android.os.Handler(Looper.getMainLooper());
    private final Runnable bidTickRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isFinishing() && !isDestroyed()) {
                applyAndShow();
                bidTicker.postDelayed(this, 15000);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        PostWorkflow.setViewerRole(Constants.ROLE_ANNOUNCER);
        setContentView(R.layout.activity_announcer_feed);
        findViewById(R.id.btnHeaderInbox).setOnClickListener(v->startActivity(new Intent(this,InboxActivity.class)));

        strings = AppStrings.get(this);

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

        // ✅ Bilingual views
        tvNearbyPosts  = findViewById(R.id.tvNearbyPosts);
        tvYourLocation = findViewById(R.id.tvYourLocation);

        // ✅ Apply bilingual strings
        applyStrings();

        adapter = new FeedAdapter(this);
        rvFeed.setLayoutManager(new LinearLayoutManager(this));
        rvFeed.setAdapter(adapter);

        auth     = FirebaseAuth.getInstance();
        annRef   = FirebaseDatabase.getInstance().getReference(Constants.DB_ANNOUNCEMENTS);
        userRef  = FirebaseDatabase.getInstance().getReference(Constants.DB_USERS);
        chatsRef = FirebaseDatabase.getInstance().getReference(Constants.DB_CHATS);

        ExpiredPostCleaner.cleanExpired(auth.getUid());

        updateRadiusText();

        if (tvRadius != null) {
            tvRadius.setVisibility(View.VISIBLE);
            tvRadius.setOnClickListener(v -> showRadiusDialog());
        }

        if (etSearch != null) {
            etSearch.setHint(strings.feedSearchHintAnn());
            etSearch.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
                @Override public void afterTextChanged(Editable s) {}
                @Override public void onTextChanged(CharSequence s, int st, int b, int c) {
                    visibleCount=5;
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

        if (fabAdd != null) {
            fabAdd.show();
            fabAdd.setOnClickListener(v ->
                    startActivity(new Intent(AnnouncerFeedActivity.this, AddAnnouncementActivity.class)));
        }

        setupLanguageToggle();

        setupBottomNav();
        selectedRadius = RadiusManager.getRadius(this);
        if (selectedRadius < 0.5 || selectedRadius > 3.0) {
            selectedRadius = 1.0;
            RadiusManager.setRadius(this, 1.0);
        }
        updateRadiusText();
        locationClient = LocationServices.getFusedLocationProviderClient(this);
        requestNotificationPermission();
        startNetworkMonitoring();
        loadUserThenStart();
        listenForUnreadMessages();
        com.example.towncrierbd.utils.AppUpdateManager.listenForUpdates(this);
        interactions=new PostInteractions(()->{if(interactions!=null){adapter.setHighlighted(interactions.ids);applyAndShow();}});
        btnTrackAcceptedPaths=findViewById(R.id.btnTrackAcceptedPaths);
        if(btnTrackAcceptedPaths!=null){
            btnTrackAcceptedPaths.setText(PostCatalog.text(this,"🗺️ Track Paths","🗺️ ট্র্যাক পাথ"));
            btnTrackAcceptedPaths.setOnClickListener(v->startActivity(new Intent(this, AnnouncerAcceptedMapActivity.class)));
        }
    }

    // ✅ Apply all bilingual strings
    private void applyStrings() {
        AppStrings s = AppStrings.get(this);

        if (tvNearbyPosts  != null) tvNearbyPosts.setText(s.feedNearbyPosts());
        if (tvYourLocation != null) tvYourLocation.setText(s.feedYourLocation());
        if (etSearch       != null) etSearch.setHint(s.feedSearchHintAnn());

        // No internet banner
        if (bannerNoInternet instanceof TextView)
            ((TextView) bannerNoInternet).setText(s.feedNoInternet());

        // Empty state
        if (layoutEmpty != null) {
            TextView tvEmptyTitle = layoutEmpty.findViewById(R.id.tvEmptyTitle);
            TextView tvEmptySub   = layoutEmpty.findViewById(R.id.tvEmptySub);
            if (tvEmptyTitle != null) tvEmptyTitle.setText(s.feedEmptyAnnouncerTitle());
            if (tvEmptySub   != null) tvEmptySub.setText(s.feedEmptyAnnouncerSub());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        com.example.towncrierbd.services.DeliveryLocationService.resumeAssigned(this);
        if (fabAdd != null) fabAdd.show();
        if (bottomNav != null) bottomNav.setSelectedItemId(R.id.menu_feed);
        bidTicker.removeCallbacks(bidTickRunnable);
        bidTicker.postDelayed(bidTickRunnable, 15000);

        // Sync radius from Map/Global preference
        double savedRadius = RadiusManager.getRadius(this);
        if (savedRadius != selectedRadius) {
            selectedRadius = savedRadius;
            updateRadiusText();
            applyAndShow();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        bidTicker.removeCallbacks(bidTickRunnable);
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
                    try {
                        View dotUnread = findViewById(R.id.dotUnreadInbox);
                        if (dotUnread != null) {
                            dotUnread.setVisibility(unread > 0 ? View.VISIBLE : View.GONE);
                        }
                    } catch (Exception ignored) {}
                });
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        };
        chatsRef.addValueEventListener(unreadListener);
    }

    private void setupLanguageToggle() {
        View layoutLangToggle = findViewById(R.id.layoutLangToggle);
        TextView tvLangBn = findViewById(R.id.tvLangBn);
        TextView tvLangEn = findViewById(R.id.tvLangEn);

        boolean isBn = LanguageManager.isBangla(this);

        if (tvLangBn != null && tvLangEn != null) {
            if (isBn) {
                tvLangBn.setBackgroundResource(R.drawable.bg_lang_active_pill);
                tvLangBn.setTextColor(android.graphics.Color.parseColor("#1565C0"));
                tvLangEn.setBackground(null);
                tvLangEn.setTextColor(android.graphics.Color.parseColor("#B0D2FF"));
            } else {
                tvLangEn.setBackgroundResource(R.drawable.bg_lang_active_pill);
                tvLangEn.setTextColor(android.graphics.Color.parseColor("#1565C0"));
                tvLangBn.setBackground(null);
                tvLangBn.setTextColor(android.graphics.Color.parseColor("#B0D2FF"));
            }

            View.OnClickListener onToggle = v -> {
                LanguageManager.toggle(this);
                String uid = auth.getUid();
                if (uid != null) LanguageManager.saveToFirebase(this, uid);
                recreate();
            };

            if (layoutLangToggle != null) layoutLangToggle.setOnClickListener(onToggle);
            tvLangBn.setOnClickListener(v -> {
                if (!LanguageManager.isBangla(this)) {
                    LanguageManager.setLanguage(this, LanguageManager.LANG_BN);
                    String uid = auth.getUid();
                    if (uid != null) LanguageManager.saveToFirebase(this, uid);
                    recreate();
                }
            });
            tvLangEn.setOnClickListener(v -> {
                if (!LanguageManager.isEnglish(this)) {
                    LanguageManager.setLanguage(this, LanguageManager.LANG_EN);
                    String uid = auth.getUid();
                    if (uid != null) LanguageManager.saveToFirebase(this, uid);
                    recreate();
                }
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
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                        Manifest.permission.POST_NOTIFICATIONS)) {
                    new AlertDialog.Builder(this)
                            .setTitle(strings.notifEnableTitle())
                            .setMessage(strings.notifEnableMsgReq())
                            .setPositiveButton(strings.notifAllow(), (d, w) ->
                                    ActivityCompat.requestPermissions(this,
                                            new String[]{Manifest.permission.POST_NOTIFICATIONS},
                                            NOTIFICATION_REQ))
                            .setNegativeButton(strings.notifNotNow(), null)
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
        if(interactions!=null)interactions.close();
        if (feedListener != null)   annRef.removeEventListener(feedListener);
        if (unreadListener != null) chatsRef.removeEventListener(unreadListener);
        if (locationClient != null && locationCallback != null)
            locationClient.removeLocationUpdates(locationCallback);
        if (adapter != null) adapter.release();
        if (networkMonitor != null) networkMonitor.stopMonitoring();
    }

    private void showRadiusDialog() {
        AppStrings s = AppStrings.get(this);
        double[] values = {0.5, 1.0, 1.5, 2.0, 2.5, 3.0};
        new AlertDialog.Builder(this)
                .setTitle(s.feedSelectRadius())
                .setItems(s.feedRadiusOptions(), (d, which) -> {
                    visibleCount=5;
                    selectedRadius = values[which];
                    RadiusManager.setRadius(this, selectedRadius);
                    updateRadiusText();
                    applyAndShow();
                }).show();
    }

    private void updateRadiusText() {
        if (tvRadius != null)
            tvRadius.setText(AppStrings.get(this).feedWithinKmBanner(selectedRadius));
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
                    if (u.getName() != null) {
                        String name = u.getName().trim();
                        String firstName = name.contains(" ") ? name.split(" ")[0] : name;
                        if (tvWelcome != null) {
                            AppStrings s = AppStrings.get(AnnouncerFeedActivity.this);
                            tvWelcome.setText(s.feedHello() + firstName + "! 👋");
                        }
                    }
                    myHawkerCategories    = u.getHawkerCategories();
                    myHawkerSubcategories = u.getHawkerSubcategories();
                    if (u.getLanguage() != null && !u.getLanguage().isEmpty()) {
                        LanguageManager.setLanguage(AnnouncerFeedActivity.this, u.getLanguage());
                    }
                    if (!locationReady && u.getLat() != 0 && u.getLng() != 0) {
                        myLat = u.getLat();
                        myLng = u.getLng();
                        locationReady = true;
                        if (tvLocationName != null && u.getLocationName() != null && !u.getLocationName().isEmpty()) {
                            tvLocationName.setText(u.getLocationName());
                        }
                    }
                }
                attachFeedListenerOnce();
                startLiveLocation();
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(AnnouncerFeedActivity.this,
                        strings.feedProfileFailed(), Toast.LENGTH_SHORT).show();
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
                        strings.feedLoadFailed() + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        };
        annRef.addValueEventListener(feedListener);
    }

    private void applyAndShow() {
        String myUid = auth.getUid();
        long now = System.currentTimeMillis();
        List<Announcement> out = new ArrayList<>();

        // Collect this announcer's currently LIVE active announcement posts
        List<Announcement> myActivePosts = new ArrayList<>();
        if (myUid != null) {
            for (Announcement p : all) {
                if (p != null && !PostWorkflow.request(p) && myUid.equals(p.getUserId())
                        && RequestLifecycle.live(p, now)) {
                    myActivePosts.add(p);
                }
            }
        }

        int myAcceptedCount = 0;
        for (Announcement a : all) {
            if (a == null) continue;
            if (PostWorkflow.request(a)) {
                RequestBidManager.syncExpiredBidsIfNeeded(a, now);
                if (RequestLifecycle.hasActiveJob(a, myUid, now)) {
                    myAcceptedCount++;
                }
            }
            boolean related=interactions!=null&&interactions.ids.contains(a.getId());
            if (PostWorkflow.request(a)) {
                if (!com.example.towncrierbd.utils.RequestLifecycle.visibleToSeller(a, myUid, now)) continue;
                boolean isAssignedToMe = RequestLifecycle.hasActiveJob(a, myUid, now);
                boolean isSpecificToMe = myUid != null && myUid.equals(a.getSourceAnnouncerUid());
                // Self / general request: ONLY show if announcer has an active post containing ALL requested items
                if (!isAssignedToMe && !isSpecificToMe) {
                    if (!RequestMatcher.matchesAnnouncerActivePosts(a, myActivePosts, now)) continue;
                }
            } else if (!messagePostsOnly || !related || !PostWorkflow.owns(a) || !PostWorkflow.live(a)) continue;
            if(messagePostsOnly&&!related)continue;
            if (!searchQuery.isEmpty()) {
                String title = safe(a.getTitle()).toLowerCase();
                String desc  = safe(a.getDescription()).toLowerCase();
                String cat   = safe(a.getCategory()).toLowerCase();
                if (!title.contains(searchQuery) &&
                        !desc.contains(searchQuery) &&
                        !cat.contains(searchQuery)) continue;
            }

            boolean assigned=RequestLifecycle.hasActiveJob(a, myUid, now)||(myUid!=null&&myUid.equals(a.getSourceAnnouncerUid()));
            if(assigned||messagePostsOnly){out.add(a);continue;}
            if (locationReady && a.getLat() != 0 && a.getLng() != 0) {
                double dist = DistanceUtil.distanceKm(myLat, myLng, a.getLat(), a.getLng());
                if (dist <= Math.min(selectedRadius, a.getRequestRadiusKm())) {
                    out.add(a);
                }
            } else {
                out.add(a);
            }
        }

        if (btnTrackAcceptedPaths != null) {
            String base = PostCatalog.text(this, "🗺️ Track Paths", "🗺️ ট্র্যাক পাথ");
            btnTrackAcceptedPaths.setText(myAcceptedCount > 0 ? (base + " (" + myAcceptedCount + ")") : base);
        }

        // Sort posts within radius by distance in ascending order (closest to announcer first)
        if (locationReady) {
            Collections.sort(out, (a1, a2) -> {
                boolean has1 = a1.getLat() != 0 && a1.getLng() != 0;
                boolean has2 = a2.getLat() != 0 && a2.getLng() != 0;
                if (has1 && has2) {
                    double d1 = DistanceUtil.distanceKm(myLat, myLng, a1.getLat(), a1.getLng());
                    double d2 = DistanceUtil.distanceKm(myLat, myLng, a2.getLat(), a2.getLng());
                    int cmp = Double.compare(d1, d2);
                    if (cmp != 0) return cmp;
                } else if (has1) {
                    return -1;
                } else if (has2) {
                    return 1;
                }
                return Long.compare(a2.getTime(), a1.getTime());
            });
        } else {
            // Fallback before location is resolved: sort by newest
            Collections.sort(out, (a1, a2) -> Long.compare(a2.getTime(), a1.getTime()));
        }

        adapter.setData(new ArrayList<>(out.subList(0,Math.min(visibleCount,out.size()))));
        adapter.setShowMore(out.size()>visibleCount,()->{visibleCount+=5;applyAndShow();});
        if (locationReady) adapter.setMyLocation(myLat, myLng);

        if (layoutEmpty != null) {
            layoutEmpty.setVisibility(out.isEmpty() ? View.VISIBLE : View.GONE);
        }
        if (fabAdd != null) fabAdd.show();
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
        if (tvLocationName != null) {
            tvLocationName.setOnClickListener(v -> {
                if (!isLocationEnabled()) {
                    startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                }
            });
        }
        if (!isLocationEnabled()) {
            if (tvLocationName != null && !locationReady) tvLocationName.setText(strings.feedTurnOnGps());
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_REQ);
            return;
        }
        try {
            locationClient.getLastLocation().addOnSuccessListener(loc -> {
                if (loc != null) {
                    myLat = loc.getLatitude();
                    myLng = loc.getLongitude();
                    locationReady = true;
                    String nice = getNiceLocationName(myLat, myLng);
                    if (tvLocationName != null) tvLocationName.setText(nice);
                    updateUserLocationInFirebase(myLat, myLng, nice);
                    updateAnnouncementsLocationInFirebase(myLat, myLng);
                    adapter.setMyLocation(myLat, myLng);
                    applyAndShow();
                }
            });
        } catch (SecurityException ignored) {}
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
                updateAnnouncementsLocationInFirebase(myLat, myLng);
                adapter.setMyLocation(myLat, myLng);
                applyAndShow();
            }
        };
        locationClient.requestLocationUpdates(req, locationCallback, Looper.getMainLooper());
    }

    private void updateAnnouncementsLocationInFirebase(double lat, double lng) {
        String uid = auth.getUid();
        if (uid == null) return;
        for (Announcement a : all) {
            if (a != null && uid.equals(a.getUserId()) && a.isActive() && !PostWorkflow.request(a)) {
                // If location changed by more than 20 meters, update in Firebase Realtime Database
                if (DistanceUtil.distanceKm(a.getLat(), a.getLng(), lat, lng) > 0.02) {
                    a.setLat(lat);
                    a.setLng(lng);
                    Map<String, Object> locMap = new HashMap<>();
                    locMap.put("lat", lat);
                    locMap.put("lng", lng);
                    locMap.put("locationUpdatedAt", ServerValue.TIMESTAMP);
                    annRef.child(a.getId()).updateChildren(locMap);
                }
            }
        }
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
        if(com.example.towncrierbd.services.DeliveryLocationService.isRunning())return;
        String uid = auth.getUid();
        if (uid == null) return;
        DatabaseReference ref = userRef.child(uid);
        java.util.Map<String,Object> update=new java.util.HashMap<>();
        update.put("lat",lat);update.put("lng",lng);update.put("locationName",name);update.put("locationUpdatedAt",com.google.firebase.database.ServerValue.TIMESTAMP);
        ref.updateChildren(update);
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