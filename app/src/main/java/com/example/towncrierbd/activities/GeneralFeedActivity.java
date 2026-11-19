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
import com.example.towncrierbd.models.Announcement;
import com.example.towncrierbd.models.UserModel;
import com.example.towncrierbd.utils.*;
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
    private TextView tvGoodDay, tvSeeNearby, tvFilterLabel, tvYourLocation;
    private View scrollChips;
    private ChipGroup chipGroup;
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
    private double selectedRadius;

    private List<String> myPreferredCategories    = new ArrayList<>();
    private List<String> myPreferredSubcategories = new ArrayList<>();

    private NetworkMonitor networkMonitor;
    private AppStrings strings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        PostWorkflow.setViewerRole(Constants.ROLE_USER);
        setContentView(R.layout.activity_general_feed);
        findViewById(R.id.btnHeaderInbox).setOnClickListener(v->startActivity(new Intent(this,InboxActivity.class)));

        strings = AppStrings.get(this);

        tvWelcome        = findViewById(R.id.tvWelcome);
        tvLocationName   = findViewById(R.id.tvLocationName);
        tvRadius         = findViewById(R.id.tvRadius);
        rvFeed           = findViewById(R.id.rvFeed);
        fabAdd           = findViewById(R.id.fabAdd);
        tvToggleFilter   = findViewById(R.id.tvToggleFilter);
        scrollChips      = findViewById(R.id.scrollChips);

        layoutEmpty      = findViewById(R.id.layoutEmpty);
        swipeRefresh     = findViewById(R.id.swipeRefresh);
        etSearch         = findViewById(R.id.etSearch);
        bannerNoInternet = findViewById(R.id.bannerNoInternet);
        bottomNav        = findViewById(R.id.bottomNav);

        // ✅ Bilingual label views
        tvGoodDay      = findViewById(R.id.tvGoodDay);
        tvSeeNearby    = findViewById(R.id.tvSeeNearby);
        tvFilterLabel  = findViewById(R.id.tvFilterLabel);
        tvYourLocation = findViewById(R.id.tvYourLocation);

        // ✅ Apply all bilingual strings immediately
        applyStrings();

        adapter = new FeedAdapter(this);
        rvFeed.setLayoutManager(new LinearLayoutManager(this));
        rvFeed.setAdapter(adapter);

        auth     = FirebaseAuth.getInstance();
        annRef   = FirebaseDatabase.getInstance().getReference(Constants.DB_ANNOUNCEMENTS);
        userRef  = FirebaseDatabase.getInstance().getReference(Constants.DB_USERS);
        chatsRef = FirebaseDatabase.getInstance().getReference(Constants.DB_CHATS);

        ExpiredPostCleaner.cleanExpired(auth.getUid());

        fabAdd.setOnClickListener(v -> new android.app.AlertDialog.Builder(this)
                .setTitle(PostCatalog.text(this, "Create request", "রিকোয়েস্ট তৈরি করুন"))
                .setItems(new String[]{
                        PostCatalog.text(this, "Request from active posts", "সক্রিয় পোস্ট থেকে রিকোয়েস্ট"),
                        PostCatalog.text(this, "Create my own request", "নিজের রিকোয়েস্ট পোস্ট তৈরি")
                }, (dialog, which) -> {
                    Intent intent = new Intent(this, AddAnnouncementActivity.class);
                    intent.putExtra(AddAnnouncementActivity.EXTRA_REQUEST_MODE, which == 1
                            ? com.example.towncrierbd.utils.RequestMode.INDEPENDENT
                            : com.example.towncrierbd.utils.RequestMode.ACTIVE_POSTS);
                    startActivity(intent);
                }).setNegativeButton(AppStrings.get(this).cancel(), null).show());

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

        PostCatalog.load(this,this::setupFeedSelectors);

        if (etSearch != null) {
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
        loadMyRoleThenStart();
        listenForUnreadMessages();
        listenForAcceptedRequests();
        com.example.towncrierbd.utils.AppUpdateManager.listenForUpdates(this);
        interactions=new PostInteractions(()->{if(interactions!=null){adapter.setHighlighted(interactions.ids);applyAndShow();}});
    }

    // ✅ Apply ALL bilingual strings — called on create and after language toggle
    private void applyStrings() {
        AppStrings s = AppStrings.get(this);

        if (tvGoodDay      != null) tvGoodDay.setText(s.feedGoodDay());
        if (tvSeeNearby    != null) tvSeeNearby.setText(s.feedSeeNearby());
        if (tvFilterLabel  != null) tvFilterLabel.setText(s.feedFilterCategory());
        if (tvToggleFilter != null) tvToggleFilter.setText(s.feedShowFilter());
        if (tvYourLocation != null) tvYourLocation.setText(s.feedYourLocation());
        if (etSearch       != null) etSearch.setHint(s.feedSearchHint());

        // No internet banner
        if (bannerNoInternet instanceof TextView)
            ((TextView) bannerNoInternet).setText(s.feedNoInternet());

        // Empty state — ids now exist in XML
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
    protected void onResume() {
        super.onResume();
        if (bottomNav != null) bottomNav.setSelectedItemId(R.id.menu_feed);

        // Sync radius from Map/Global preference
        double savedRadius = RadiusManager.getRadius(this);
        if (savedRadius != selectedRadius) {
            selectedRadius = savedRadius;
            updateRadiusText();
            applyAndShow();
        }
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
        if (myRequestsListener != null) annRef.removeEventListener(myRequestsListener);
        if (locationClient != null && locationCallback != null)
            locationClient.removeLocationUpdates(locationCallback);
        if (adapter != null) adapter.release();
        if (networkMonitor != null) networkMonitor.stopMonitoring();
    }

    private ValueEventListener myRequestsListener;
    private final java.util.Set<String> notifiedAcceptedIds = new java.util.HashSet<>();

    private void listenForAcceptedRequests() {
        String myUid = auth.getUid();
        if (myUid == null) return;
        myRequestsListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (isFinishing() || isDestroyed()) return;
                for (DataSnapshot s : snapshot.getChildren()) {
                    Announcement a = s.getValue(Announcement.class);
                    if (a == null) continue;
                    if (a.getId() == null || a.getId().isEmpty()) a.setId(s.getKey());
                    if (myUid.equals(a.getUserId()) && "accepted".equals(a.getStatus()) && !notifiedAcceptedIds.contains(a.getId())) {
                        notifiedAcceptedIds.add(a.getId());
                        String sellerName = a.getAcceptedByName() != null && !a.getAcceptedByName().isEmpty() ? a.getAcceptedByName() : "Announcer";
                        new AlertDialog.Builder(GeneralFeedActivity.this)
                                .setTitle(PostCatalog.text(GeneralFeedActivity.this, "🚴 Request Accepted!", "🚴 রিকোয়েস্ট গ্রহণ করা হয়েছে!"))
                                .setMessage(PostCatalog.text(GeneralFeedActivity.this,
                                        sellerName + " accepted your request for \"" + a.getTitle() + "\". Track live delivery on map?",
                                        sellerName + " আপনার \"" + a.getTitle() + "\" এর রিকোয়েস্ট গ্রহণ করেছেন। লাইভ ম্যাপে ডেলিভারি ট্র্যাক করবেন?"))
                                .setPositiveButton(PostCatalog.text(GeneralFeedActivity.this, "🗺️ Open Map", "🗺️ ম্যাপ খুলুন"), (d, w) -> {
                                    Intent intent = new Intent(GeneralFeedActivity.this, RequestTrackingActivity.class);
                                    intent.putExtra("announcementId", a.getId());
                                    startActivity(intent);
                                })
                                .setNegativeButton(PostCatalog.text(GeneralFeedActivity.this, "Later", "পরে"), null)
                                .show();
                        break;
                    }
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        };
        annRef.orderByChild("userId").equalTo(myUid).addValueEventListener(myRequestsListener);
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
                if (u.getName() != null) {
                    AppStrings s = AppStrings.get(GeneralFeedActivity.this);
                    tvWelcome.setText(s.feedWelcomeBack() + u.getName() + "!");
                }
                if (u.getLanguage() != null && !u.getLanguage().isEmpty()) {
                    LanguageManager.setLanguage(GeneralFeedActivity.this, u.getLanguage());
                }
                if (!locationReady && u.getLat() != 0 && u.getLng() != 0) {
                    myLat = u.getLat();
                    myLng = u.getLng();
                    locationReady = true;
                    if (tvLocationName != null && u.getLocationName() != null && !u.getLocationName().isEmpty()) {
                        tvLocationName.setText(u.getLocationName());
                    }
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
            boolean own = myUid != null && myUid.equals(a.getUserId());
            boolean related = interactions != null && interactions.ids.contains(a.getId());
            if (messagePostsOnly) {
                if (!related) continue;
            } else {
                if (myRequests) {
                    // Show only requests created by this user
                    if (!own || !PostWorkflow.request(a)) continue;
                } else {
                    // Show active announcer posts / announcements
                    boolean isAnnouncerPost = !PostWorkflow.request(a)
                            || "announcement".equalsIgnoreCase(a.getPostType())
                            || (a.getUserRole() != null && a.getUserRole().equalsIgnoreCase(Constants.ROLE_ANNOUNCER));
                    if (!isAnnouncerPost) continue;
                }
                if (!PostWorkflow.live(a)) continue;
                if (!PostWorkflow.matchesCategory(a, selectedCategory)) continue;
            }
            if (!searchQuery.isEmpty()) {
                String title = safe(a.getTitle()).toLowerCase();
                String desc  = safe(a.getDescription()).toLowerCase();
                String cat   = safe(a.getCategory()).toLowerCase();
                if (!title.contains(searchQuery) &&
                        !desc.contains(searchQuery) &&
                        !cat.contains(searchQuery)) continue;
            }
            if (messagePostsOnly || (myRequests && own)) {
                out.add(a);
                continue;
            }
            if (locationReady && a.getLat() != 0 && a.getLng() != 0) {
                double dist = DistanceUtil.distanceKm(myLat, myLng, a.getLat(), a.getLng());
                if (dist <= selectedRadius) {
                    out.add(a);
                }
            } else {
                // Keep post visible before location is resolved or if post coordinates are unset
                out.add(a);
            }
        }

        if (locationReady) {
            out.sort((p1, p2) -> {
                boolean has1 = p1.getLat() != 0 && p1.getLng() != 0;
                boolean has2 = p2.getLat() != 0 && p2.getLng() != 0;
                if (has1 && has2) {
                    double d1 = DistanceUtil.distanceKm(myLat, myLng, p1.getLat(), p1.getLng());
                    double d2 = DistanceUtil.distanceKm(myLat, myLng, p2.getLat(), p2.getLng());
                    int cmp = Double.compare(d1, d2);
                    if (cmp != 0) return cmp;
                } else if (has1) {
                    return -1;
                } else if (has2) {
                    return 1;
                }
                return Long.compare(p2.getTime(), p1.getTime());
            });
        } else {
            out.sort((p1, p2) -> Long.compare(p2.getTime(), p1.getTime()));
        }

        adapter.setData(new ArrayList<>(out.subList(0, Math.min(visibleCount, out.size()))));
        adapter.setShowMore(out.size() > visibleCount, () -> { visibleCount += 5; applyAndShow(); });
        if (locationReady) adapter.setMyLocation(myLat, myLng);
        if (layoutEmpty != null)
            layoutEmpty.setVisibility(out.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void setupFeedSelectors() {
        Spinner category=findViewById(R.id.spFeedCategory);
        java.util.List<String> keys=new java.util.ArrayList<>(),labels=new java.util.ArrayList<>();
        keys.add(CategoryConfig.CAT_ALL);labels.add(PostCatalog.text(this,"All categories","সব ক্যাটাগরি"));
        for(CategoryConfig.HawkerCategory cat:PostCatalog.sorted(this)){keys.add(cat.name);labels.add(PostCatalog.label(this,cat.name));}
        category.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,labels));
        category.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            @Override public void onItemSelected(AdapterView<?> a,View v,int position,long id){selectedCategory=keys.get(position);applyAndShow();}
            @Override public void onNothingSelected(AdapterView<?> a){}
        });
        Spinner mode=findViewById(R.id.spFeedMode);
        mode.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{PostCatalog.text(this,"All","সব"),PostCatalog.text(this,"My requests","আমার রিকোয়েস্ট")}));
        mode.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            @Override public void onItemSelected(AdapterView<?> a,View v,int position,long id){myRequests=position==1;applyAndShow();}
            @Override public void onNothingSelected(AdapterView<?> a){}
        });
        if(getIntent().getBooleanExtra("myRequests",false))mode.setSelection(1);
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
                adapter.setMyLocation(myLat, myLng);
                applyAndShow();
            }
        };
        try {
            locationClient.requestLocationUpdates(req, locationCallback, Looper.getMainLooper());
        } catch (SecurityException ignored) {}
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