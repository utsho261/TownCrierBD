package com.example.towncrierbd.activities;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;

import com.example.towncrierbd.models.Announcement;
import com.example.towncrierbd.models.RequestBid;
import com.example.towncrierbd.models.UserModel;
import com.example.towncrierbd.utils.Constants;
import com.example.towncrierbd.utils.DistanceUtil;
import com.example.towncrierbd.utils.MapMarkerHelper;
import com.example.towncrierbd.utils.PostCatalog;
import com.example.towncrierbd.utils.PostWorkflow;
import com.example.towncrierbd.utils.RequestBidManager;
import com.example.towncrierbd.utils.RequestLifecycle;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Dedicated in-app multi-stop path tracker for Announcers:
 * Displays ALL currently accepted user requests on one map with profile photo/letter markers,
 * a combined multi-stop route polyline from the announcer's live GPS, live ETA countdowns,
 * and one-tap multi-stop navigation & delivery controls.
 */
public class AnnouncerAcceptedMapActivity extends AppCompatActivity {

    private GoogleMap map;
    private TextView tvHeaderTitle, tvHeaderSub, tvEmptyStops;
    private MaterialButton btnSortToggle, btnNavigateAll;
    private LinearLayout stopsContainer;

    private String myUid;
    private String myName = "";
    private String myPhotoUrl = "";
    private double myLat = 0.0, myLng = 0.0;
    private boolean hasMyLoc = false;
    private boolean sortByDeadline = true;
    private boolean initialCameraFitted = false;

    private DatabaseReference annRef, userRef;
    private ValueEventListener annListener;
    private FusedLocationProviderClient fusedLoc;
    private LocationCallback locCallback;

    private final List<Announcement> acceptedStops = new ArrayList<>();
    private final Map<String, UserModel> userCache = new HashMap<>();

    private final Handler ticker = new Handler(Looper.getMainLooper());
    private final Runnable tickRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isFinishing() && !isDestroyed()) {
                long now = System.currentTimeMillis();
                for (Announcement a : new ArrayList<>(acceptedStops)) {
                    RequestBidManager.syncExpiredBidsIfNeeded(a, now);
                }
                renderAll();
                ticker.postDelayed(this, 10000);
            }
        }
    };

    private String txt(String en, String bn) {
        return PostCatalog.text(this, en, bn);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        myUid = FirebaseAuth.getInstance().getUid();
        if (myUid == null) {
            finish();
            return;
        }

        annRef = FirebaseDatabase.getInstance().getReference(Constants.DB_ANNOUNCEMENTS);
        userRef = FirebaseDatabase.getInstance().getReference(Constants.DB_USERS);
        fusedLoc = LocationServices.getFusedLocationProviderClient(this);

        buildUi();
        loadMyProfile();
        startLocationUpdates();
        listenAcceptedRequests();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#EEF3FB"));

        // ── Top Header Bar ────────────────────────────────────────────────
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setBackgroundColor(Color.parseColor("#1565C0"));
        header.setPadding(dp(14), dp(12), dp(14), dp(12));

        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);

        ImageView btnBack = new ImageView(this);
        btnBack.setImageResource(android.R.drawable.ic_menu_revert);
        btnBack.setColorFilter(Color.WHITE);
        btnBack.setPadding(dp(4), dp(4), dp(12), dp(4));
        btnBack.setOnClickListener(v -> finish());
        topRow.addView(btnBack);

        LinearLayout titleCol = new LinearLayout(this);
        titleCol.setOrientation(LinearLayout.VERTICAL);
        tvHeaderTitle = new TextView(this);
        tvHeaderTitle.setText(txt("🗺️ Track Accepted Paths", "🗺️ অ্যাক্সেপ্ট করা পাথসমূহ"));
        tvHeaderTitle.setTextColor(Color.WHITE);
        tvHeaderTitle.setTextSize(17f);
        tvHeaderTitle.setTypeface(null, Typeface.BOLD);
        titleCol.addView(tvHeaderTitle);

        tvHeaderSub = new TextView(this);
        tvHeaderSub.setText(txt("Loading accepted requests…", "অ্যাক্সেপ্ট করা রিকোয়েস্ট লোড হচ্ছে…"));
        tvHeaderSub.setTextColor(Color.parseColor("#BBDEFB"));
        tvHeaderSub.setTextSize(12f);
        titleCol.addView(tvHeaderSub);
        topRow.addView(titleCol, new LinearLayout.LayoutParams(0, -2, 1f));
        header.addView(topRow);

        // Action pills row: Sort Mode + Navigate All
        LinearLayout pillRow = new LinearLayout(this);
        pillRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams pillRowLp = new LinearLayout.LayoutParams(-1, -2);
        pillRowLp.topMargin = dp(8);
        header.addView(pillRow, pillRowLp);

        btnSortToggle = new MaterialButton(this);
        btnSortToggle.setAllCaps(false);
        btnSortToggle.setTextSize(12f);
        btnSortToggle.setCornerRadius(dp(10));
        btnSortToggle.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#1E40AF")));
        btnSortToggle.setTextColor(Color.WHITE);
        btnSortToggle.setText(txt("⏱️ Sort: Fastest Time", "⏱️ ক্রম: কম সময় আগে"));
        btnSortToggle.setOnClickListener(v -> {
            sortByDeadline = !sortByDeadline;
            btnSortToggle.setText(sortByDeadline
                    ? txt("⏱️ Sort: Fastest Time", "⏱️ ক্রম: কম সময় আগে")
                    : txt("📍 Sort: Nearest First", "📍 ক্রম: কাছেরটি আগে"));
            initialCameraFitted = false;
            renderAll();
        });
        LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(0, dp(40), 1f);
        sLp.setMarginEnd(dp(6));
        pillRow.addView(btnSortToggle, sLp);

        btnNavigateAll = new MaterialButton(this);
        btnNavigateAll.setAllCaps(false);
        btnNavigateAll.setTextSize(12f);
        btnNavigateAll.setCornerRadius(dp(10));
        btnNavigateAll.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#059669")));
        btnNavigateAll.setTextColor(Color.WHITE);
        btnNavigateAll.setText(txt("🧭 Navigate All", "🧭 সব রুট ম্যাপে"));
        btnNavigateAll.setOnClickListener(v -> openMultiStopNavigation());
        LinearLayout.LayoutParams nLp = new LinearLayout.LayoutParams(0, dp(40), 1f);
        nLp.setMarginStart(dp(6));
        pillRow.addView(btnNavigateAll, nLp);

        root.addView(header);

        // ── Google Map Container (Top ~54%) ──────────────────────────────
        android.widget.FrameLayout mapHolder = new android.widget.FrameLayout(this);
        mapHolder.setId(View.generateViewId());
        root.addView(mapHolder, new LinearLayout.LayoutParams(-1, 0, 1.25f));

        SupportMapFragment mapFragment = SupportMapFragment.newInstance();
        getSupportFragmentManager().beginTransaction().replace(mapHolder.getId(), mapFragment).commit();
        mapFragment.getMapAsync(googleMap -> {
            map = googleMap;
            map.getUiSettings().setZoomControlsEnabled(true);
            map.getUiSettings().setMapToolbarEnabled(false);
            renderAll();
        });

        // ── Bottom Scrollable Stops List (~46%) ──────────────────────────
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.parseColor("#EEF3FB"));
        scroll.setFillViewport(true);

        stopsContainer = new LinearLayout(this);
        stopsContainer.setOrientation(LinearLayout.VERTICAL);
        stopsContainer.setPadding(dp(12), dp(10), dp(12), dp(20));

        tvEmptyStops = new TextView(this);
        tvEmptyStops.setText(txt("No accepted requests right now.\nAccept requests from your feed to see all stops & paths here.",
                "বর্তমানে আপনার কোনো অ্যাক্সেপ্ট করা রিকোয়েস্ট নেই।\nফিড থেকে রিকোয়েস্ট গ্রহণ করলে এখানে সব মার্কার ও পাথ একসাথে দেখা যাবে।"));
        tvEmptyStops.setTextColor(Color.parseColor("#64748B"));
        tvEmptyStops.setTextSize(14f);
        tvEmptyStops.setGravity(Gravity.CENTER);
        tvEmptyStops.setPadding(dp(20), dp(36), dp(20), dp(36));
        stopsContainer.addView(tvEmptyStops);

        scroll.addView(stopsContainer);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        setContentView(root);
    }

    private void loadMyProfile() {
        userRef.child(myUid).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                UserModel u = snapshot.getValue(UserModel.class);
                if (u != null) {
                    myName = u.getName() != null ? u.getName().trim() : "";
                    myPhotoUrl = u.getPhotoUrl();
                    if (!hasMyLoc && u.getLat() != 0.0 && u.getLng() != 0.0) {
                        myLat = u.getLat();
                        myLng = u.getLng();
                        hasMyLoc = true;
                    }
                    renderAll();
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void startLocationUpdates() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        try {
            fusedLoc.getLastLocation().addOnSuccessListener(loc -> {
                if (loc != null) {
                    myLat = loc.getLatitude();
                    myLng = loc.getLongitude();
                    hasMyLoc = true;
                    renderAll();
                }
            });
            LocationRequest req = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 8000)
                    .setMinUpdateIntervalMillis(4000)
                    .build();
            locCallback = new LocationCallback() {
                @Override
                public void onLocationResult(@NonNull LocationResult result) {
                    Location loc = result.getLastLocation();
                    if (loc == null) return;
                    myLat = loc.getLatitude();
                    myLng = loc.getLongitude();
                    hasMyLoc = true;
                    renderAll();
                }
            };
            fusedLoc.requestLocationUpdates(req, locCallback, Looper.getMainLooper());
        } catch (SecurityException ignored) {}
    }

    private void listenAcceptedRequests() {
        annListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                long now = System.currentTimeMillis();
                acceptedStops.clear();
                for (DataSnapshot row : snapshot.getChildren()) {
                    Announcement a = row.getValue(Announcement.class);
                    if (a == null) continue;
                    if (a.getId() == null || a.getId().trim().isEmpty()) a.setId(row.getKey());
                    if (!RequestLifecycle.request(a)) continue;

                    RequestBidManager.syncExpiredBidsIfNeeded(a, now);

                    if (RequestLifecycle.hasActiveJob(a, myUid, now)) {
                        acceptedStops.add(a);
                        fetchBuyerProfileIfNeeded(a.getUserId());
                    }
                }
                renderAll();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };
        annRef.addValueEventListener(annListener);
    }

    private void fetchBuyerProfileIfNeeded(String buyerUid) {
        if (buyerUid == null || buyerUid.isEmpty() || userCache.containsKey(buyerUid)) return;
        userCache.put(buyerUid, new UserModel()); // placeholder to avoid duplicate fetches
        userRef.child(buyerUid).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                UserModel u = snapshot.getValue(UserModel.class);
                if (u != null) {
                    userCache.put(buyerUid, u);
                    renderAll();
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private List<Announcement> getOrderedStops(long now) {
        List<Announcement> ordered = new ArrayList<>();
        for (Announcement a : acceptedStops) {
            if (RequestLifecycle.hasActiveJob(a, myUid, now)) {
                ordered.add(a);
            }
        }
        if (ordered.isEmpty()) return ordered;

        if (sortByDeadline) {
            ordered.sort((a, b) -> {
                RequestBid ba = RequestBidManager.getActiveBid(a, myUid, now);
                RequestBid bb = RequestBidManager.getActiveBid(b, myUid, now);
                long da = ba != null ? ba.getDeadlineAt() : RequestBidManager.getEffectiveDeadline(a, now);
                long db = bb != null ? bb.getDeadlineAt() : RequestBidManager.getEffectiveDeadline(b, now);
                return Long.compare(da, db);
            });
            return ordered;
        }

        // Greedy nearest-neighbor path ordering starting from announcer's live GPS
        if (!hasMyLoc) return ordered;
        List<Announcement> remaining = new ArrayList<>(ordered);
        List<Announcement> route = new ArrayList<>();
        double curLat = myLat, curLng = myLng;
        while (!remaining.isEmpty()) {
            int bestIdx = 0;
            double bestDist = Double.MAX_VALUE;
            for (int i = 0; i < remaining.size(); i++) {
                Announcement cand = remaining.get(i);
                double d = DistanceUtil.distanceKm(curLat, curLng, cand.getLat(), cand.getLng());
                if (d < bestDist) {
                    bestDist = d;
                    bestIdx = i;
                }
            }
            Announcement next = remaining.remove(bestIdx);
            route.add(next);
            curLat = next.getLat();
            curLng = next.getLng();
        }
        return route;
    }

    private void renderAll() {
        if (isFinishing() || isDestroyed()) return;
        long now = System.currentTimeMillis();
        List<Announcement> ordered = getOrderedStops(now);

        // Compute total path distance
        double totalKm = 0.0;
        if (hasMyLoc && !ordered.isEmpty()) {
            double pLat = myLat, pLng = myLng;
            for (Announcement stop : ordered) {
                if (stop.getLat() != 0.0 || stop.getLng() != 0.0) {
                    totalKm += DistanceUtil.distanceKm(pLat, pLng, stop.getLat(), stop.getLng());
                    pLat = stop.getLat();
                    pLng = stop.getLng();
                }
            }
        }

        if (ordered.isEmpty()) {
            tvHeaderSub.setText(txt("0 accepted requests", "কোনো অ্যাক্সেপ্ট রিকোয়েস্ট নেই"));
        } else {
            String distLabel = totalKm > 0
                    ? String.format(Locale.getDefault(), " · %.2f km", totalKm)
                    : "";
            tvHeaderSub.setText(ordered.size()
                    + txt(ordered.size() == 1 ? " accepted stop" : " accepted stops", "টি অ্যাক্সেপ্ট স্টপ")
                    + distLabel);
        }

        btnNavigateAll.setEnabled(!ordered.isEmpty());
        btnNavigateAll.setAlpha(ordered.isEmpty() ? 0.5f : 1f);

        renderMap(ordered, now);
        renderStopCards(ordered, now);
    }

    private void renderMap(List<Announcement> ordered, long now) {
        if (map == null) return;
        map.clear();

        List<LatLng> pathPoints = new ArrayList<>();
        LatLngBounds.Builder boundsBuilder = new LatLngBounds.Builder();
        int pointsCount = 0;

        // 1. Announcer's own live location marker
        if (hasMyLoc) {
            LatLng me = new LatLng(myLat, myLng);
            pathPoints.add(me);
            boundsBuilder.include(me);
            pointsCount++;

            int blue = Color.parseColor("#2563EB");
            Marker meMarker = map.addMarker(new MarkerOptions()
                    .position(me)
                    .title(txt("You (Announcer)", "আপনি (বিক্রেতা)"))
                    .icon(MapMarkerHelper.getInitialOrCachedDescriptor(this, myUid, myName, myPhotoUrl, blue))
                    .anchor(0.5f, 1.0f));
            MapMarkerHelper.bindProfileMarker(this, meMarker, myUid, myName, myPhotoUrl, blue);
        }

        // 2. All accepted request markers
        for (int i = 0; i < ordered.size(); i++) {
            Announcement stop = ordered.get(i);
            if (stop.getLat() == 0.0 && stop.getLng() == 0.0) continue;

            LatLng pos = new LatLng(stop.getLat(), stop.getLng());
            pathPoints.add(pos);
            boundsBuilder.include(pos);
            pointsCount++;

            UserModel buyer = userCache.get(stop.getUserId());
            String bName = (buyer != null && buyer.getName() != null && !buyer.getName().trim().isEmpty())
                    ? buyer.getName().trim() : (stop.getUserName() != null ? stop.getUserName() : "Buyer");
            String bPhoto = buyer != null ? buyer.getPhotoUrl() : "";

            boolean isLeader = myUid.equals(stop.getAcceptedByUid());
            boolean isDelivered = "delivered".equals(stop.getStatus());
            int pinColor = isDelivered ? Color.parseColor("#059669")
                    : (isLeader ? Color.parseColor("#E11D48") : Color.parseColor("#7C3AED"));

            RequestBid myBid = RequestBidManager.getActiveBid(stop, myUid, now);
            long remMin = myBid != null
                    ? Math.max(1, (long) Math.ceil((myBid.getDeadlineAt() - now) / 60000.0))
                    : RequestBidManager.getMaxAllowedOfferMinutes(stop, now);

            Marker m = map.addMarker(new MarkerOptions()
                    .position(pos)
                    .title("#" + (i + 1) + " " + bName + " (" + remMin + "m)")
                    .snippet(stop.getTitle())
                    .icon(MapMarkerHelper.getInitialOrCachedDescriptor(this, stop.getUserId(), bName, bPhoto, pinColor))
                    .anchor(0.5f, 1.0f));
            MapMarkerHelper.bindProfileMarker(this, m, stop.getUserId(), bName, bPhoto, pinColor);
        }

        // 3. Draw combined route Polyline connecting all stops
        if (pathPoints.size() >= 2) {
            map.addPolyline(new PolylineOptions()
                    .addAll(pathPoints)
                    .color(Color.parseColor("#2563EB"))
                    .width(10f));
        }

        // 4. Fit camera once when points are ready
        if (!initialCameraFitted && pointsCount > 0) {
            initialCameraFitted = true;
            try {
                if (pointsCount == 1) {
                    map.animateCamera(CameraUpdateFactory.newLatLngZoom(pathPoints.get(0), 15f));
                } else {
                    map.animateCamera(CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), dp(64)));
                }
            } catch (Exception ignored) {}
        }
    }

    private void renderStopCards(List<Announcement> ordered, long now) {
        stopsContainer.removeAllViews();
        if (ordered.isEmpty()) {
            stopsContainer.addView(tvEmptyStops);
            return;
        }

        for (int i = 0; i < ordered.size(); i++) {
            final int stopIndex = i + 1;
            final Announcement stop = ordered.get(i);

            CardView card = new CardView(this);
            card.setRadius(dp(14));
            card.setCardElevation(dp(3));
            card.setCardBackgroundColor(Color.WHITE);
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-1, -2);
            clp.bottomMargin = dp(10);
            card.setLayoutParams(clp);

            LinearLayout box = new LinearLayout(this);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setPadding(dp(14), dp(12), dp(14), dp(12));

            // Row 1: Stop # + Buyer Name + Status Badge
            LinearLayout top = new LinearLayout(this);
            top.setOrientation(LinearLayout.HORIZONTAL);
            top.setGravity(Gravity.CENTER_VERTICAL);

            boolean isLeader = myUid.equals(stop.getAcceptedByUid());
            boolean isDelivered = "delivered".equals(stop.getStatus());

            TextView tvTitle = new TextView(this);
            String buyerName = stop.getUserName() != null && !stop.getUserName().isEmpty() ? stop.getUserName() : "Buyer";
            tvTitle.setText("#" + stopIndex + " · " + buyerName);
            tvTitle.setTextSize(15f);
            tvTitle.setTypeface(null, Typeface.BOLD);
            tvTitle.setTextColor(Color.parseColor("#0F172A"));
            top.addView(tvTitle, new LinearLayout.LayoutParams(0, -2, 1f));

            TextView badge = new TextView(this);
            badge.setTextSize(11f);
            badge.setTypeface(null, Typeface.BOLD);
            badge.setPadding(dp(8), dp(3), dp(8), dp(3));
            if (isDelivered) {
                badge.setText(txt("✅ Delivered", "✅ ডেলিভারি হয়েছে"));
                badge.setTextColor(Color.parseColor("#065F46"));
                badge.setBackgroundColor(Color.parseColor("#D1FAE5"));
            } else if (isLeader) {
                badge.setText(txt("🏆 Leading", "🏆 লিডিং"));
                badge.setTextColor(Color.parseColor("#991B1B"));
                badge.setBackgroundColor(Color.parseColor("#FEE2E2"));
            } else {
                badge.setText(txt("⏳ Backup", "⏳ ব্যাকআপ"));
                badge.setTextColor(Color.parseColor("#5B21B6"));
                badge.setBackgroundColor(Color.parseColor("#EDE9FE"));
            }
            top.addView(badge);
            box.addView(top);

            // Row 2: Items & Title
            TextView tvPost = new TextView(this);
            String qty = PostWorkflow.quantities(this, stop);
            String body = (stop.getTitle() != null ? stop.getTitle() : "")
                    + (!qty.isEmpty() ? "\n" + qty : "");
            tvPost.setText(body);
            tvPost.setTextSize(13f);
            tvPost.setTextColor(Color.parseColor("#334155"));
            tvPost.setPadding(0, dp(4), 0, dp(4));
            box.addView(tvPost);

            // Row 3: Distance + Remaining Offered Time
            RequestBid myBid = RequestBidManager.getActiveBid(stop, myUid, now);
            long myRemMin = myBid != null
                    ? Math.max(1, (long) Math.ceil((myBid.getDeadlineAt() - now) / 60000.0))
                    : RequestBidManager.getMaxAllowedOfferMinutes(stop, now);
            int leadMin = RequestBidManager.getMaxAllowedOfferMinutes(stop, now);

            String distStr = "";
            if (hasMyLoc && (stop.getLat() != 0.0 || stop.getLng() != 0.0)) {
                double dKm = DistanceUtil.distanceKm(myLat, myLng, stop.getLat(), stop.getLng());
                distStr = dKm < 1.0
                        ? String.format(Locale.getDefault(), "📍 %.0fm · ", dKm * 1000.0)
                        : String.format(Locale.getDefault(), "📍 %.2fkm · ", dKm);
            }

            String timeInfo = distStr + txt("My time left: " + myRemMin + " min", "আমার সময় বাকি: " + myRemMin + " মিনিট");
            if (!isLeader && !isDelivered) {
                timeInfo += txt(" (Current fastest: " + leadMin + "m)", " (বর্তমান দ্রুততম: " + leadMin + " মি.)");
            }
            TextView tvMeta = new TextView(this);
            tvMeta.setText(timeInfo);
            tvMeta.setTextSize(12f);
            tvMeta.setTypeface(null, Typeface.BOLD);
            tvMeta.setTextColor(Color.parseColor("#1D4ED8"));
            box.addView(tvMeta);

            // Row 4: Quick Actions (Route, Delivered, Chat, Cancel)
            LinearLayout btnRow = new LinearLayout(this);
            btnRow.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams brLp = new LinearLayout.LayoutParams(-1, -2);
            brLp.topMargin = dp(8);
            box.addView(btnRow, brLp);

            MaterialButton btnDir = makeSmallBtn(txt("🧭 Route", "🧭 রুট"), "#2563EB");
            btnDir.setOnClickListener(v -> openSingleNavigation(stop));
            btnRow.addView(btnDir, new LinearLayout.LayoutParams(0, dp(38), 1f));

            if (!isDelivered && RequestLifecycle.canDeliver(stop, myUid)) {
                MaterialButton btnDel = makeSmallBtn(txt("✅ Done", "✅ ডেলিভারি"), "#059669");
                btnDel.setOnClickListener(v -> PostWorkflow.delivery(this, stop, "delivered", this::renderAll));
                LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(0, dp(38), 1.15f);
                dlp.setMarginStart(dp(6));
                btnRow.addView(btnDel, dlp);
            }

            MaterialButton btnChat = makeSmallBtn(txt("💬 Chat", "💬 চ্যাট"), "#0284C7");
            btnChat.setOnClickListener(v -> PostWorkflow.chat(this, stop));
            LinearLayout.LayoutParams clp2 = new LinearLayout.LayoutParams(0, dp(38), 0.95f);
            clp2.setMarginStart(dp(6));
            btnRow.addView(btnChat, clp2);

            if (!isDelivered) {
                MaterialButton btnCancel = makeSmallBtn(txt("✖", "✖"), "#DC2626");
                btnCancel.setOnClickListener(v -> PostWorkflow.delivery(this, stop, "cancel", this::renderAll));
                LinearLayout.LayoutParams xlp = new LinearLayout.LayoutParams(dp(44), dp(38));
                xlp.setMarginStart(dp(6));
                btnRow.addView(btnCancel, xlp);
            }

            card.addView(box);
            card.setOnClickListener(v -> {
                if (map != null && (stop.getLat() != 0.0 || stop.getLng() != 0.0)) {
                    map.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(stop.getLat(), stop.getLng()), 16f));
                }
            });
            stopsContainer.addView(card);
        }
    }

    private MaterialButton makeSmallBtn(String text, String bgHex) {
        MaterialButton b = new MaterialButton(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(11.5f);
        b.setTextColor(Color.WHITE);
        b.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor(bgHex)));
        b.setCornerRadius(dp(10));
        b.setInsetTop(0);
        b.setInsetBottom(0);
        b.setPadding(dp(4), 0, dp(4), 0);
        return b;
    }

    private void openSingleNavigation(Announcement stop) {
        if (stop.getLat() == 0.0 && stop.getLng() == 0.0) {
            Toast.makeText(this, txt("Location unavailable", "লোকেশন পাওয়া যায়নি"), Toast.LENGTH_SHORT).show();
            return;
        }
        String uri = String.format(Locale.US, "google.navigation:q=%f,%f", stop.getLat(), stop.getLng());
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
        try {
            startActivity(intent);
        } catch (Exception e) {
            String web = String.format(Locale.US, "https://www.google.com/maps/dir/?api=1&destination=%f,%f", stop.getLat(), stop.getLng());
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(web)));
        }
    }

    private void openMultiStopNavigation() {
        List<Announcement> ordered = getOrderedStops(System.currentTimeMillis());
        List<Announcement> valid = new ArrayList<>();
        for (Announcement a : ordered) {
            if (a.getLat() != 0.0 || a.getLng() != 0.0) valid.add(a);
        }
        if (valid.isEmpty()) {
            Toast.makeText(this, txt("No accepted locations to navigate", "নেভিগেট করার মতো কোনো লোকেশন নেই"), Toast.LENGTH_SHORT).show();
            return;
        }
        if (valid.size() == 1) {
            openSingleNavigation(valid.get(0));
            return;
        }

        // Build Google Maps multi-stop directions URL: origin -> waypoints -> destination
        Announcement last = valid.get(valid.size() - 1);
        StringBuilder url = new StringBuilder("https://www.google.com/maps/dir/?api=1");
        if (hasMyLoc) {
            url.append(String.format(Locale.US, "&origin=%f,%f", myLat, myLng));
        }
        url.append(String.format(Locale.US, "&destination=%f,%f", last.getLat(), last.getLng()));
        if (valid.size() > 1) {
            url.append("&waypoints=");
            for (int i = 0; i < valid.size() - 1; i++) {
                if (i > 0) url.append("|");
                url.append(String.format(Locale.US, "%f,%f", valid.get(i).getLat(), valid.get(i).getLng()));
            }
        }
        url.append("&travelmode=driving");

        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url.toString())));
        } catch (Exception e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        ticker.removeCallbacks(tickRunnable);
        ticker.postDelayed(tickRunnable, 5000);
    }

    @Override
    protected void onPause() {
        super.onPause();
        ticker.removeCallbacks(tickRunnable);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ticker.removeCallbacks(tickRunnable);
        if (annRef != null && annListener != null) {
            annRef.removeEventListener(annListener);
        }
        if (fusedLoc != null && locCallback != null) {
            fusedLoc.removeLocationUpdates(locCallback);
        }
    }
}
