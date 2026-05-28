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

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.example.towncrierbd.R;
import com.example.towncrierbd.models.Announcement;
import com.example.towncrierbd.models.UserModel;
import com.example.towncrierbd.utils.AppStrings;
import com.example.towncrierbd.utils.Constants;
import com.example.towncrierbd.utils.DistanceUtil;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
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

    private BottomSheetBehavior<View> sheetBehavior;
    private View bottomSheet;
    private ImageView ivCover;
    private ImageView btnCloseSheet;
    private View coverPlaceholder;
    private TextView tvBadge, tvTitle, tvDesc, tvDistance;
    private Button btnDetails;
    private ImageButton btnChat, btnCall;
    private ImageButton btnDirectionSheet;

    private FloatingActionButton fabMyLoc, fabDirections;

    private final Map<String, Announcement> markerMap = new HashMap<>();
    private Announcement lastSelected = null;

    private ValueEventListener annListener = null;

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
        btnDetails        = findViewById(R.id.btnDetails);
        btnChat           = findViewById(R.id.btnChat);
        btnCall           = findViewById(R.id.btnCall);
        btnDirectionSheet = findViewById(R.id.btnDirectionSheet);
        fabMyLoc          = findViewById(R.id.fabMyLoc);
        fabDirections     = findViewById(R.id.fabDirections);

        if (btnCloseSheet != null) {
            btnCloseSheet.setOnClickListener(v -> {
                if (sheetBehavior != null)
                    sheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
            });
        }

        if (fabMyLoc != null)      fabMyLoc.setOnClickListener(v -> requestLocation());
        if (fabDirections != null) fabDirections.setOnClickListener(v -> openDirectionsToSelected());

        SupportMapFragment mapFragment = (SupportMapFragment)
                getSupportFragmentManager().findFragmentById(R.id.map);
        if (mapFragment != null) mapFragment.getMapAsync(this);

        loadMyRoleThenStart();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
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
        fusedLoc.getLastLocation().addOnSuccessListener(loc -> {
            if (loc != null) setMyLocation(loc);
            else             attachAnnouncementsListener();
        });
    }

    private void setMyLocation(Location loc) {
        myLat    = loc.getLatitude();
        myLng    = loc.getLongitude();
        hasMyLoc = true;
        if (mMap != null) {
            LatLng me = new LatLng(myLat, myLng);
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(me, 15f));
            attachAnnouncementsListener();
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
        if (annRef == null) return;
        if (annListener != null) annRef.removeEventListener(annListener);

        annListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (mMap == null) return;
                AppStrings s = AppStrings.get(MapsActivity.this);

                mMap.clear();
                markerMap.clear();

                if (hasMyLoc) {
                    LatLng me = new LatLng(myLat, myLng);
                    mMap.addMarker(new MarkerOptions()
                            .position(me)
                            .title(s.mapYou())
                            .icon(BitmapDescriptorFactory.defaultMarker(
                                    BitmapDescriptorFactory.HUE_AZURE)));
                }

                String myUid = auth.getUid();
                long now = System.currentTimeMillis();

                for (DataSnapshot ds : snapshot.getChildren()) {
                    Announcement a = ds.getValue(Announcement.class);
                    if (a == null) continue;
                    if (a.getId() == null || a.getId().trim().isEmpty()) a.setId(ds.getKey());
                    if (a.getId() == null) continue;
                    if (myUid != null && myUid.equals(a.getUserId())) continue;
                    if (a.getExpireAt() > 0 && now > a.getExpireAt()) continue;
                    String postRole = safe(a.getUserRole());
                    if (!wantRole.equals(postRole)) continue;
                    if (hasMyLoc) {
                        double d = DistanceUtil.distanceKm(myLat, myLng, a.getLat(), a.getLng());
                        if (d > Constants.FEED_RADIUS_KM) continue;
                    }
                    LatLng pos = new LatLng(a.getLat(), a.getLng());
                    Marker m = mMap.addMarker(new MarkerOptions()
                            .position(pos)
                            .title(safe(a.getTitle()).isEmpty() ? "Announcement" : a.getTitle())
                            .icon(BitmapDescriptorFactory.defaultMarker(
                                    BitmapDescriptorFactory.HUE_VIOLET)));
                    if (m != null) {
                        m.setTag(a.getId());
                        markerMap.put(a.getId(), a);
                    }
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };
        annRef.addValueEventListener(annListener);
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

        if (btnChat != null) {
            btnChat.setOnClickListener(v -> {
                AppStrings as = AppStrings.get(this);
                String postOwnerUid = safe(a.getUserId());
                String myUidNow     = safe(auth.getUid());
                if (postOwnerUid.isEmpty()) {
                    Toast.makeText(this, as.mapCannotChat(), Toast.LENGTH_SHORT).show();
                    return;
                }
                if (myUidNow.equals(postOwnerUid)) {
                    Toast.makeText(this, as.mapChatSelf(), Toast.LENGTH_SHORT).show();
                    return;
                }
                Intent i = new Intent(this, ChatActivity.class);
                i.putExtra(ChatActivity.EXTRA_OTHER_UID,  postOwnerUid);
                i.putExtra(ChatActivity.EXTRA_OTHER_NAME, safe(a.getUserName()));
                startActivity(i);
            });
        }

        if (btnDirectionSheet != null) {
            btnDirectionSheet.setOnClickListener(v ->
                    openDirectionsTo(a.getLat(), a.getLng()));
        }

        if (btnDetails != null) {
            btnDetails.setOnClickListener(v -> {
                AppStrings as = AppStrings.get(this);
                String dist = hasMyLoc
                        ? as.mapKmAway(DistanceUtil.distanceKm(myLat, myLng, a.getLat(), a.getLng()))
                        : "";
                Intent intent = new Intent(MapsActivity.this, AnnouncementDetailActivity.class);
                intent.putExtra(AnnouncementDetailActivity.EXTRA_TITLE,     safe(a.getTitle()));
                intent.putExtra(AnnouncementDetailActivity.EXTRA_DESC,      safe(a.getDescription()));
                intent.putExtra(AnnouncementDetailActivity.EXTRA_CATEGORY,  safe(a.getDisplayCategoryLabel()));
                intent.putExtra(AnnouncementDetailActivity.EXTRA_PHONE,     safe(a.getPhone()));
                intent.putExtra(AnnouncementDetailActivity.EXTRA_IMAGE_URL, safe(a.getImageUrl()));
                intent.putExtra(AnnouncementDetailActivity.EXTRA_USER_NAME, safe(a.getUserName()));
                intent.putExtra(AnnouncementDetailActivity.EXTRA_DISTANCE,  dist);
                intent.putExtra(AnnouncementDetailActivity.EXTRA_TIME,      getRelativeTime(a.getTime()));
                intent.putExtra(AnnouncementDetailActivity.EXTRA_OTHER_UID, safe(a.getUserId()));
                intent.putExtra(AnnouncementDetailActivity.EXTRA_LAT,       a.getLat());
                intent.putExtra(AnnouncementDetailActivity.EXTRA_LNG,       a.getLng());
                startActivity(intent);
            });
        }

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