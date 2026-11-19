package com.example.towncrierbd.activities;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.view.ViewGroup;
import com.google.android.gms.location.*;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.example.towncrierbd.R;
import com.example.towncrierbd.models.Announcement;
import com.example.towncrierbd.models.UserModel;
import com.example.towncrierbd.utils.*;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import android.graphics.Color;
import android.widget.HorizontalScrollView;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.*;
import java.util.Locale;
import java.util.Map;

public class MapsActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final int LOCATION_REQ = 909;

    private GoogleMap mMap;
    private FusedLocationProviderClient fusedLoc;

    private double myLat = 0, myLng = 0;
    private boolean hasMyLoc = false;

    private FirebaseAuth auth;
    private DatabaseReference annRef, userRef;

    private String myRole   = "";
    private String wantRole = "";
    private String myName   = "";
    private String myPhotoUrl = "";

    private BottomSheetBehavior<View> sheetBehavior;
    private View bottomSheet;
    private ImageView ivCover;
    private ImageView btnCloseSheet;
    private View coverPlaceholder;
    private TextView tvBadge, tvTitle, tvDesc, tvDistance, tvRadius;
    private TextView tvHeaderTitle, tvRadiusPill;
    private ImageView btnBack;
    private HorizontalScrollView scrollNearest;
    private LinearLayout nearestChipsLayout;
    private Button btnDetails;
    private ImageButton btnChat, btnCall;
    private ImageButton btnDirectionSheet;

    private FloatingActionButton fabMyLoc, fabDirections;
    private View fabRow;

    private final Map<String, Announcement> markerMap = new HashMap<>();
    private Announcement lastSelected = null;
    private double selectedRadius;
    private Circle radiusCircle = null;
    private boolean initialZoomDone = false;

    private ValueEventListener annListener = null;
    private final List<Announcement> mapPosts=new ArrayList<>();
    private final List<Announcement> myOwnActivePosts=new ArrayList<>();
    private final Map<String,ValueEventListener> sellerListeners=new HashMap<>();
    private final Map<String,UserModel> sellerLocations=new HashMap<>(), previousSellerLocations=new HashMap<>();
    private TextView movementLabel;
    private boolean focusedNearest;
    private LocationCallback liveLocation;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);

        auth     = FirebaseAuth.getInstance();
        fusedLoc = LocationServices.getFusedLocationProviderClient(this);
        annRef   = FirebaseDatabase.getInstance().getReference(Constants.DB_ANNOUNCEMENTS);
        userRef  = FirebaseDatabase.getInstance().getReference(Constants.DB_USERS);

        bottomSheet = findViewById(R.id.bottomSheet);
        if (bottomSheet != null) {
            sheetBehavior = BottomSheetBehavior.from(bottomSheet);
            sheetBehavior.setHideable(true);
            sheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
        }

        ivCover           = findViewById(R.id.ivCover);
        btnCloseSheet     = findViewById(R.id.btnCloseSheet);
        coverPlaceholder  = findViewById(R.id.coverPlaceholder);
        tvBadge           = findViewById(R.id.tvBadge);
        tvTitle           = findViewById(R.id.tvTitle);
        tvDesc            = findViewById(R.id.tvDesc);
        tvDistance        = findViewById(R.id.tvDistance);
        tvRadius          = findViewById(R.id.tvRadius);
        tvHeaderTitle     = findViewById(R.id.tvHeaderTitle);
        tvRadiusPill      = findViewById(R.id.tvRadiusPill);
        btnBack           = findViewById(R.id.btnBack);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());

        scrollNearest      = findViewById(R.id.scrollNearest);
        nearestChipsLayout = findViewById(R.id.nearestChipsLayout);

        btnDetails        = findViewById(R.id.btnDetails);
        btnChat           = findViewById(R.id.btnChat);
        btnCall           = findViewById(R.id.btnCall);
        btnDirectionSheet = findViewById(R.id.btnDirectionSheet);
        View btnFilter    = findViewById(R.id.btnFilter);

        movementLabel=new TextView(this);movementLabel.setTextSize(14);
        if (tvDistance != null && tvDistance.getParent() instanceof LinearLayout) {
            ((LinearLayout)tvDistance.getParent()).addView(movementLabel,((LinearLayout)tvDistance.getParent()).indexOfChild(tvDistance)+1);
        }

        fabRow            = findViewById(R.id.fabRow);
        fabMyLoc          = findViewById(R.id.fabMyLoc);
        fabDirections     = findViewById(R.id.fabDirections);

        // Slide FABs up smoothly when bottom sheet expands
        if (bottomSheet != null && sheetBehavior != null) {
            sheetBehavior.addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
                @Override
                public void onStateChanged(@NonNull View sheet, int newState) {
                    if (newState == BottomSheetBehavior.STATE_HIDDEN && fabRow != null) {
                        fabRow.animate().translationY(0).setDuration(150).start();
                    }
                }

                @Override
                public void onSlide(@NonNull View sheet, float slideOffset) {
                    if (fabRow != null) {
                        float h = sheet.getHeight();
                        fabRow.setTranslationY(-h * Math.max(0f, slideOffset));
                    }
                }
            });
        }

        if (btnCloseSheet != null) {
            btnCloseSheet.setOnClickListener(v -> {
                if (sheetBehavior != null)
                    sheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
            });
        }

        if (btnFilter != null) {
            btnFilter.setOnClickListener(v -> showRadiusDialog());
        }

        selectedRadius = RadiusManager.getRadius(this);
        updateRadiusBanner();

        if (fabMyLoc != null) {
            fabMyLoc.setOnClickListener(v -> {
                if (hasMyLoc && mMap != null) {
                    zoomToRadius(true);
                }
                requestLocation();
            });
        }
        if (fabDirections != null) fabDirections.setOnClickListener(v -> openDirectionsToSelected());

        SupportMapFragment mapFragment = (SupportMapFragment)
                getSupportFragmentManager().findFragmentById(R.id.map);
        if (mapFragment != null) mapFragment.getMapAsync(this);

        loadMyRoleThenStart();
    }

    @Override protected void onResume(){super.onResume();if(fusedLoc!=null){selectedRadius=RadiusManager.getRadius(this);updateRadiusBanner();requestLocation();}}
    @Override protected void onPause(){super.onPause();if(liveLocation!=null){fusedLoc.removeLocationUpdates(liveLocation);liveLocation=null;}}

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(liveLocation!=null)fusedLoc.removeLocationUpdates(liveLocation);
        for(Map.Entry<String,ValueEventListener> entry:sellerListeners.entrySet())userRef.child(entry.getKey()).removeEventListener(entry.getValue());
        sellerListeners.clear();
        if (annListener != null && annRef != null) {
            annRef.removeEventListener(annListener);
            annListener = null;
        }
    }

    private void loadMyRoleThenStart() {
        String uid = auth.getUid();
        if (uid == null) {
            myRole   = Constants.ROLE_USER;
            wantRole = Constants.ROLE_ANNOUNCER;
            requestLocation();
            return;
        }

        userRef.child(uid).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                UserModel u = snapshot.getValue(UserModel.class);
                myRole = safe(u == null ? "" : u.getRole());
                myName = safe(u == null ? "" : u.getName());
                myPhotoUrl = safe(u == null ? "" : u.getPhotoUrl());
                if (myRole.isEmpty()) myRole = Constants.ROLE_USER;
                wantRole = Constants.ROLE_ANNOUNCER.equals(myRole)
                        ? Constants.ROLE_USER : Constants.ROLE_ANNOUNCER;
                requestLocation();
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                myRole   = Constants.ROLE_USER;
                wantRole = Constants.ROLE_ANNOUNCER;
                requestLocation();
            }
        });
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;
        mMap.getUiSettings().setCompassEnabled(false);
        mMap.getUiSettings().setMapToolbarEnabled(false);
        mMap.getUiSettings().setMyLocationButtonEnabled(false);

        LatLng dhaka = new LatLng(23.8103, 90.4125);
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(dhaka, 12f));

        mMap.setOnMarkerClickListener(marker -> {
            Object tag = marker.getTag();
            if (tag instanceof String) {
                Announcement a = markerMap.get((String) tag);
                if (a != null) { showBottomSheet(a); return true; }
            }
            return false;
        });

        mMap.setOnMapClickListener(latLng -> {
            if (sheetBehavior != null)
                sheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
        });

        if (!wantRole.isEmpty()) attachAnnouncementsListener();
    }

    private void requestLocation() {
        AppStrings s = AppStrings.get(this);
        if (!isLocationEnabled()) {
            Toast.makeText(this, s.mapTurnOnGps(), Toast.LENGTH_SHORT).show();
            startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_REQ);
            return;
        }
        if(liveLocation==null) {
            liveLocation=new LocationCallback(){@Override public void onLocationResult(@NonNull LocationResult result){if(result.getLastLocation()!=null)setMyLocation(result.getLastLocation());}};
            LocationRequest request=new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY,10000).setMinUpdateIntervalMillis(5000).build();
            fusedLoc.requestLocationUpdates(request,liveLocation,Looper.getMainLooper());
        }
        fusedLoc.getLastLocation().addOnSuccessListener(loc -> {
            if (loc != null && android.os.SystemClock.elapsedRealtimeNanos()-loc.getElapsedRealtimeNanos()<=120_000_000_000L) setMyLocation(loc);
            else             attachAnnouncementsListener();
        });
    }

    private void setMyLocation(Location loc) {
        myLat    = loc.getLatitude();
        myLng    = loc.getLongitude();
        hasMyLoc = true;
        String uid=auth.getUid();
        if(uid!=null){Map<String,Object> update=new HashMap<>();update.put("lat",myLat);update.put("lng",myLng);update.put("locationUpdatedAt",com.google.firebase.database.ServerValue.TIMESTAMP);userRef.child(uid).updateChildren(update);}
        if(mMap!=null){
            if (!initialZoomDone) {
                initialZoomDone = true;
                zoomToRadius(true);
            }
            attachAnnouncementsListener();
            renderMapPosts();
        }
    }

    private void drawRadiusCircle() {
        if (mMap == null || !hasMyLoc) return;
        if (radiusCircle != null) {
            try { radiusCircle.remove(); } catch (Exception ignored) {}
            radiusCircle = null;
        }

        double radiusMeters = selectedRadius * 1000.0;
        CircleOptions circleOptions = new CircleOptions()
                .center(new LatLng(myLat, myLng))
                .radius(radiusMeters)
                .strokeWidth(3.5f)
                .strokeColor(Color.parseColor("#801565C0"))
                .fillColor(Color.parseColor("#181565C0"))
                .zIndex(1.0f);

        radiusCircle = mMap.addCircle(circleOptions);
    }

    private float getZoomForRadius(double radiusKm) {
        if (radiusKm <= 0.12) return 17.6f; // 100m -> tight street-level zoom
        if (radiusKm <= 0.55) return 15.8f; // 500m -> neighborhood zoom
        if (radiusKm <= 1.15) return 14.8f; // 1 km -> area zoom
        if (radiusKm <= 2.15) return 13.8f; // 2 km -> city-sector zoom
        return 13.1f;                       // 3 km or more -> wider overview zoom
    }

    private void zoomToRadius(boolean animate) {
        if (mMap == null || !hasMyLoc) return;
        LatLng userPos = new LatLng(myLat, myLng);
        float zoom = getZoomForRadius(selectedRadius);
        if (animate) {
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(userPos, zoom), 600, null);
        } else {
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(userPos, zoom));
        }
    }

    private boolean isLocationEnabled() {
        LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        return lm != null && (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
                || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        AppStrings s = AppStrings.get(this);
        if (requestCode == LOCATION_REQ && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            requestLocation();
        } else if (requestCode == LOCATION_REQ) {
            Toast.makeText(this, s.mapPermDenied(), Toast.LENGTH_SHORT).show();
        }
    }

    private void attachAnnouncementsListener() {
        if(annRef==null || annListener!=null || wantRole.isEmpty())return;
        annListener=new ValueEventListener(){
            @Override public void onDataChange(@NonNull DataSnapshot data){
                mapPosts.clear();myOwnActivePosts.clear();Set<String> owners=new HashSet<>();
                for(DataSnapshot row:data.getChildren()){
                    Announcement a=row.getValue(Announcement.class);
                    if(a==null || !PostWorkflow.live(a))continue;
                    a.setId(row.getKey());
                    if(PostWorkflow.owns(a)){
                        if(!PostWorkflow.request(a))myOwnActivePosts.add(a);
                        continue;
                    }
                    if(!wantRole.equals(a.getUserRole()))continue;
                    mapPosts.add(a);
                    if(a.getUserId()!=null && !a.getUserId().trim().isEmpty())owners.add(a.getUserId());
                }
                for(String uid:new ArrayList<>(sellerListeners.keySet()))if(!owners.contains(uid)){
                    userRef.child(uid).removeEventListener(sellerListeners.remove(uid));sellerLocations.remove(uid);previousSellerLocations.remove(uid);
                }
                for(String uid:owners)watchSeller(uid);
                renderMapPosts();
            }
            @Override public void onCancelled(@NonNull DatabaseError error){Toast.makeText(MapsActivity.this,error.getMessage(),Toast.LENGTH_SHORT).show();}
        };annRef.addValueEventListener(annListener);
    }
    private void watchSeller(String uid){
        if(sellerListeners.containsKey(uid))return;
        ValueEventListener listener=new ValueEventListener(){
            @Override public void onDataChange(@NonNull DataSnapshot data){
                UserModel next=data.getValue(UserModel.class);if(next==null)return;
                UserModel previous=sellerLocations.get(uid);
                if(previous!=null && next.getLocationUpdatedAt()>previous.getLocationUpdatedAt())previousSellerLocations.put(uid,previous);
                sellerLocations.put(uid,next);renderMapPosts();
            }
            @Override public void onCancelled(@NonNull DatabaseError error){}
        };sellerListeners.put(uid,listener);userRef.child(uid).addValueEventListener(listener);
    }
    private void renderMapPosts(){
        if(mMap==null || !hasMyLoc || wantRole.isEmpty())return;
        mMap.clear();markerMap.clear();
        if (nearestChipsLayout != null) nearestChipsLayout.removeAllViews();

        String myUid = auth.getUid();
        int myColor = Color.parseColor("#2563EB");
        Marker myMarker = mMap.addMarker(new MarkerOptions()
                .position(new LatLng(myLat, myLng))
                .title(AppStrings.get(this).mapYou())
                .icon(MapMarkerHelper.getInitialOrCachedDescriptor(this, myUid, myName, myPhotoUrl, myColor))
                .anchor(0.5f, 1.0f));
        MapMarkerHelper.bindProfileMarker(this, myMarker, myUid, myName, myPhotoUrl, myColor);
        drawRadiusCircle();

        List<Announcement> visible=new ArrayList<>();long now=System.currentTimeMillis();
        for(Announcement a:mapPosts){
            if(!PostWorkflow.live(a))continue;
            if(PostWorkflow.request(a)){
                RequestBidManager.syncExpiredBidsIfNeeded(a, now);
                if(!RequestLifecycle.visibleToSeller(a, myUid, now))continue;
                boolean isAssigned = RequestLifecycle.hasActiveJob(a, myUid, now);
                boolean isSpecific = myUid != null && myUid.equals(a.getSourceAnnouncerUid());
                if(!isAssigned && !isSpecific && !RequestMatcher.matchesAnnouncerActivePosts(a, myOwnActivePosts, now))continue;
            }
            UserModel live=sellerLocations.get(a.getUserId());
            if(Constants.ROLE_ANNOUNCER.equals(a.getUserRole()) && live!=null && live.getLocationUpdatedAt()>0 && now-live.getLocationUpdatedAt()<=120000){a.setLat(live.getLat());a.setLng(live.getLng());}
            double radius=PostWorkflow.request(a)?Math.min(selectedRadius,a.getRequestRadiusKm()):selectedRadius;
            double distKm=DistanceUtil.distanceKm(myLat,myLng,a.getLat(),a.getLng());
            if(distKm<=radius)visible.add(a);
        }
        visible.sort((a,b)->{int distance=Double.compare(DistanceUtil.distanceKm(myLat,myLng,a.getLat(),a.getLng()),DistanceUtil.distanceKm(myLat,myLng,b.getLat(),b.getLng()));return distance!=0?distance:Long.compare(b.getTime(),a.getTime());});

        boolean isBn = LanguageManager.isBangla(this);
        if (tvRadius != null) {
            String countText;
            if (visible.isEmpty()) {
                countText = isBn ? "কাছাকাছি কোনো সক্রিয় পোস্ট নেই" : "No active posts in range";
            } else {
                countText = visible.size() + (isBn ? "টি পোস্ট সক্রিয়" : (visible.size() == 1 ? " post active" : " posts active"));
            }
            tvRadius.setText(AppStrings.get(this).feedWithinKmBanner(selectedRadius) + " • " + countText);
        }

        if (scrollNearest != null) {
            scrollNearest.setVisibility(visible.isEmpty() ? View.GONE : View.VISIBLE);
        }

        for(Announcement a:visible){
            UserModel owner = sellerLocations.get(a.getUserId());
            String ownerName = (owner != null && owner.getName() != null && !owner.getName().trim().isEmpty())
                    ? owner.getName() : a.getUserName();
            String ownerPhoto = owner != null ? owner.getPhotoUrl() : "";
            int pinColor = PostWorkflow.request(a) ? Color.parseColor("#E11D48") : Color.parseColor("#7C3AED");

            Marker marker=mMap.addMarker(new MarkerOptions()
                    .position(new LatLng(a.getLat(),a.getLng()))
                    .title(a.getTitle())
                    .icon(MapMarkerHelper.getInitialOrCachedDescriptor(this, a.getUserId(), ownerName, ownerPhoto, pinColor))
                    .anchor(0.5f, 1.0f));
            MapMarkerHelper.bindProfileMarker(this, marker, a.getUserId(), ownerName, ownerPhoto, pinColor);
            if(marker!=null)marker.setTag(a.getId());markerMap.put(a.getId(),a);

            if (nearestChipsLayout != null) {
                TextView chip = new TextView(this);
                chip.setBackgroundResource(R.drawable.bg_chip_nearest);
                chip.setTextColor(Color.parseColor("#1565C0"));
                chip.setTextSize(12);
                chip.setTypeface(null, android.graphics.Typeface.BOLD);
                int padH = (int)(12 * getResources().getDisplayMetrics().density);
                int padV = (int)(6 * getResources().getDisplayMetrics().density);
                chip.setPadding(padH, padV, padH, padV);
                double distM = DistanceUtil.distanceKm(myLat, myLng, a.getLat(), a.getLng()) * 1000.0;
                String distStr = distM < 1000 ? String.format(Locale.getDefault(), "%.0fm", distM)
                                              : String.format(Locale.getDefault(), "%.1fkm", distM / 1000.0);
                chip.setText("📍 " + distStr + " • " + safe(a.getTitle()));

                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
                lp.setMarginEnd((int)(8 * getResources().getDisplayMetrics().density));
                chip.setLayoutParams(lp);

                chip.setOnClickListener(v -> {
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(
                            new LatLng(a.getLat(), a.getLng()),
                            getZoomForRadius(selectedRadius)), 500, null);
                    showBottomSheet(a);
                });
                nearestChipsLayout.addView(chip);
            }
        }
        if(!focusedNearest && !visible.isEmpty()){
            focusedNearest=true;
        }
        if(lastSelected!=null){
            Announcement current=markerMap.get(lastSelected.getId());
            if(current==null){lastSelected=null;if(sheetBehavior!=null)sheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);}
            else if(sheetBehavior!=null && sheetBehavior.getState()!=BottomSheetBehavior.STATE_HIDDEN)showBottomSheet(current);
        }
    }
    private String movement(Announcement a){
        if(PostWorkflow.request(a))return "";
        UserModel current=sellerLocations.get(a.getUserId()),previous=previousSellerLocations.get(a.getUserId());
        int direction=current==null || previous==null?2:SellerMovement.direction(myLat,myLng,previous.getLat(),previous.getLng(),current.getLat(),current.getLng(),previous.getLocationUpdatedAt(),current.getLocationUpdatedAt(),System.currentTimeMillis());
        if(direction==-1)return PostCatalog.text(this,"Seller moving towards you (approx.)","বিক্রেতা আপনার দিকে আসছেন (আনুমানিক)");
        if(direction==1)return PostCatalog.text(this,"Seller moving away (approx.)","বিক্রেতা দূরে যাচ্ছেন (আনুমানিক)");
        if(direction==0)return PostCatalog.text(this,"No significant movement","উল্লেখযোগ্য চলাচল নেই");
        return PostCatalog.text(this,"Waiting for recent seller locations","বিক্রেতার সাম্প্রতিক অবস্থানের অপেক্ষায়");
    }

    private void showBottomSheet(Announcement a) {
        if (a == null) return;
        lastSelected = a;
        AppStrings s = AppStrings.get(this);

        if (tvBadge != null) {
            String badge = safe(a.getDisplayCategoryLabel());
            if (badge.isEmpty()) badge = safe(a.getCategory());
            if (badge.isEmpty()) badge = s.mapCategory();
            tvBadge.setText(badge);
        }

        if (tvTitle != null) tvTitle.setText(safe(a.getTitle()));
        if (tvDesc  != null) tvDesc.setText(safe(a.getDescription()));
        movementLabel.setText(movement(a));
        btnCall.setVisibility(!safe(a.getPhone()).isEmpty() && (!PostWorkflow.request(a)||a.isShowPhone())?View.VISIBLE:View.GONE);

        // ✅ FIX: mapKmAway already returns a formatted string — no double-format
        if (tvDistance != null) {
            if (hasMyLoc) {
                double d = DistanceUtil.distanceKm(myLat, myLng, a.getLat(), a.getLng());
                tvDistance.setText(s.mapKmAway(d));
            } else {
                tvDistance.setText(s.nearby());
            }
        }

        String imgUrl = safe(a.getImageUrl());
        if (ivCover != null) {
            if (!imgUrl.isEmpty()) {
                ivCover.setVisibility(View.VISIBLE);
                if (coverPlaceholder != null) coverPlaceholder.setVisibility(View.GONE);
                Glide.with(this).load(imgUrl).centerCrop()
                        .placeholder(android.R.drawable.ic_menu_gallery).into(ivCover);
            } else {
                ivCover.setVisibility(View.GONE);
                ivCover.setImageDrawable(null);
                if (coverPlaceholder != null) coverPlaceholder.setVisibility(View.VISIBLE);
            }
        }

        if (btnCall != null) {
            btnCall.setOnClickListener(v -> {
                AppStrings as = AppStrings.get(this);
                String phone = safe(a.getPhone());
                if (phone.isEmpty()) {
                    Toast.makeText(this, as.mapNoPhone(), Toast.LENGTH_SHORT).show();
                    return;
                }
                startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone)));
            });
        }

        if(btnChat!=null)btnChat.setOnClickListener(v->PostWorkflow.chat(this,a));

        if (btnDirectionSheet != null) {
            btnDirectionSheet.setOnClickListener(v ->
                    openDirectionsTo(a.getLat(), a.getLng()));
        }

        if(btnDetails!=null)btnDetails.setOnClickListener(v->PostWorkflow.detail(this,a));

        if (sheetBehavior != null)
            sheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
    }

    private void openDirectionsToSelected() {
        AppStrings s = AppStrings.get(this);
        if (!hasMyLoc) {
            Toast.makeText(this, s.mapNoLocation(), Toast.LENGTH_SHORT).show();
            return;
        }
        if (lastSelected == null) {
            Toast.makeText(this, s.mapSelectMarker(), Toast.LENGTH_SHORT).show();
            return;
        }
        openDirectionsTo(lastSelected.getLat(), lastSelected.getLng());
    }

    private void openDirectionsTo(double destLat, double destLng) {
        Uri gmmIntentUri = Uri.parse(
                "google.navigation:q=" + destLat + "," + destLng + "&mode=d");
        Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
        mapIntent.setPackage("com.google.android.apps.maps");
        if (mapIntent.resolveActivity(getPackageManager()) != null) {
            startActivity(mapIntent);
        } else {
            Uri web = Uri.parse("https://www.google.com/maps/dir/?api=1&destination="
                    + destLat + "," + destLng + "&travelmode=driving");
            startActivity(new Intent(Intent.ACTION_VIEW, web));
        }
    }

    private void updateRadiusBanner() {
        AppStrings s = AppStrings.get(this);
        boolean isBn = LanguageManager.isBangla(this);

        if (tvHeaderTitle != null) {
            tvHeaderTitle.setText(s.mapTitle());
        }

        String radiusLabel = selectedRadius < 1.0
                ? ((int) (selectedRadius * 1000)) + (isBn ? " মি" : " m")
                : ((int) selectedRadius) + (isBn ? " কিমি" : " km");

        if (tvRadiusPill != null) {
            tvRadiusPill.setText("📍 " + radiusLabel + " ▾");
        }

        if (tvRadius != null) {
            if (!hasMyLoc) {
                tvRadius.setText(s.feedWithinKmBanner(selectedRadius) + " • " + s.feedDetecting());
            } else {
                tvRadius.setText(s.feedWithinKmBanner(selectedRadius));
            }
        }
    }

    private void showRadiusDialog() {
        AppStrings s = AppStrings.get(this);
        double[] values = {0.5, 1.0, 1.5, 2.0, 2.5, 3.0};
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(s.feedSelectRadius())
                .setItems(s.feedRadiusOptions(), (d, which) -> {
                    selectedRadius = values[which];
                    RadiusManager.setRadius(this, selectedRadius);
                    updateRadiusBanner();

                    // Smoothly animate camera zoom when range changes (zooms in if range decreases)
                    if (hasMyLoc && mMap != null) {
                        zoomToRadius(true);
                    }

                    focusedNearest = false;
                    renderMapPosts();
                }).show();
    }

    private String getRelativeTime(long timeMillis) {
        if (timeMillis == 0) return "";
        AppStrings s = AppStrings.get(this);
        long diff    = System.currentTimeMillis() - timeMillis;
        long minutes = diff / 60000;
        long hours   = minutes / 60;
        long days    = hours / 24;
        if (minutes < 1)  return s.justNow();
        if (minutes < 60) return s.timeMinAgo(minutes);
        if (hours < 24)   return s.timeHrAgo(hours);
        return s.timeDayAgo(days);
    }

    private String safe(String s) {
        return s == null ? "" : s.trim();
    }
}