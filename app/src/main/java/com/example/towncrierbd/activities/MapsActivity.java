package com.example.towncrierbd.activities;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
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
import com.example.towncrierbd.utils.Constants;
import com.example.towncrierbd.utils.DistanceUtil;
import com.example.towncrierbd.utils.ImageBase64Util;
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
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class MapsActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final int LOCATION_REQ = 909;

    private GoogleMap mMap;
    private FusedLocationProviderClient fusedLoc;

    private double myLat = 0, myLng = 0;
    private boolean hasMyLoc = false;

    // Firebase
    private FirebaseAuth auth;
    private DatabaseReference annRef, userRef;

    private String myRole = "";
    private String wantRole = ""; // opposite role

    // BottomSheet
    private BottomSheetBehavior<android.view.View> sheetBehavior;
    private android.view.View bottomSheet;
    private ImageView ivCover, btnCloseSheet;
    private TextView tvBadge, tvTitle, tvDesc, tvDistance;
    private Button btnDetails;
    private ImageButton btnChat, btnCall;

    // Buttons
    private FloatingActionButton fabMyLoc, fabDirections;

    // marker -> announcement
    private final Map<String, Announcement> markerMap = new HashMap<>();

    // optional: drawn polyline (only if you later implement in-app)
    private Polyline routeLine;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);

        auth = FirebaseAuth.getInstance();
        fusedLoc = LocationServices.getFusedLocationProviderClient(this);

        annRef = FirebaseDatabase.getInstance().getReference(Constants.DB_ANNOUNCEMENTS);
        userRef = FirebaseDatabase.getInstance().getReference(Constants.DB_USERS);

        // bottom sheet views
        bottomSheet = findViewById(R.id.bottomSheet);
        sheetBehavior = BottomSheetBehavior.from(bottomSheet);
        sheetBehavior.setHideable(true);
        sheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);

        ivCover = findViewById(R.id.ivCover);
        btnCloseSheet = findViewById(R.id.btnCloseSheet);
        tvBadge = findViewById(R.id.tvBadge);
        tvTitle = findViewById(R.id.tvTitle);
        tvDesc = findViewById(R.id.tvDesc);
        tvDistance = findViewById(R.id.tvDistance);
        btnDetails = findViewById(R.id.btnDetails);
        btnChat = findViewById(R.id.btnChat);
        btnCall = findViewById(R.id.btnCall);

        btnCloseSheet.setOnClickListener(v -> sheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN));

        fabMyLoc = findViewById(R.id.fabMyLoc);
        fabDirections = findViewById(R.id.fabDirections);

        fabMyLoc.setOnClickListener(v -> requestLocation());

        // ✅ directions: open Google Maps app with navigation line (FREE)
        fabDirections.setOnClickListener(v -> openDirectionsToSelected());

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        if (mapFragment != null) mapFragment.getMapAsync(this);

        // 먼저 role load -> then location + markers
        loadMyRoleThenStart();
    }

    private void loadMyRoleThenStart() {
        String uid = auth.getUid();
        if (uid == null) {
            myRole = Constants.ROLE_USER;
            wantRole = Constants.ROLE_ANNOUNCER;
            requestLocation();
            return;
        }

        userRef.child(uid).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                UserModel u = snapshot.getValue(UserModel.class);
                myRole = safe(u == null ? "" : u.getRole());
                if (myRole.isEmpty()) myRole = Constants.ROLE_USER;

                wantRole = Constants.ROLE_ANNOUNCER.equals(myRole)
                        ? Constants.ROLE_USER
                        : Constants.ROLE_ANNOUNCER;

                requestLocation(); // get location (or try)
            }

            @Override public void onCancelled(@NonNull DatabaseError error) {
                myRole = Constants.ROLE_USER;
                wantRole = Constants.ROLE_ANNOUNCER;
                requestLocation();
            }
        });
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;

        // ✅ Remove compass + unwanted UI
        mMap.getUiSettings().setCompassEnabled(false);      // remove that small icon you showed
        mMap.getUiSettings().setMapToolbarEnabled(false);   // removes "Directions" toolbar sometimes
        mMap.getUiSettings().setMyLocationButtonEnabled(false); // we use our own button

        LatLng dhaka = new LatLng(23.8103, 90.4125);
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(dhaka, 12f));

        mMap.setOnMarkerClickListener(marker -> {
            Object tag = marker.getTag();
            if (tag instanceof String) {
                Announcement a = markerMap.get((String) tag);
                if (a != null) {
                    showBottomSheet(a);
                    return true;
                }
            }
            return false;
        });

        attachAnnouncementsListener();
    }

    // -------------------- LOCATION --------------------

    private void requestLocation() {
        if (!isLocationEnabled()) {
            Toast.makeText(this, "Turn ON GPS", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
            return;
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_REQ);
            return;
        }

        fusedLoc.getLastLocation()
                .addOnSuccessListener(loc -> {
                    if (loc != null) {
                        setMyLocation(loc);
                    } else {
                        Toast.makeText(this, "Location not ready yet", Toast.LENGTH_SHORT).show();
                        // still show markers if map ready
                        if (mMap != null) attachAnnouncementsListener();
                    }
                });
    }

    private void setMyLocation(Location loc) {
        myLat = loc.getLatitude();
        myLng = loc.getLongitude();
        hasMyLoc = true;

        if (mMap != null) {
            mMap.clear();
            markerMap.clear();

            LatLng me = new LatLng(myLat, myLng);
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(me, 15f));
            mMap.addMarker(new MarkerOptions()
                    .position(me)
                    .title("You")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)));

            attachAnnouncementsListener(); // reload markers around me
        }
    }

    private boolean isLocationEnabled() {
        LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        return lm != null && (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
                || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_REQ && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            requestLocation();
        } else if (requestCode == LOCATION_REQ) {
            Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show();
        }
    }

    // -------------------- MARKERS (ROLE FILTER + RADIUS) --------------------

    private void attachAnnouncementsListener() {
        if (annRef == null) return;

        annRef.addValueEventListener(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (mMap == null) return;

                // redraw
                mMap.clear();
                markerMap.clear();

                // re-add my marker
                if (hasMyLoc) {
                    LatLng me = new LatLng(myLat, myLng);
                    mMap.addMarker(new MarkerOptions()
                            .position(me)
                            .title("You")
                            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)));
                }

                String myUid = auth.getUid();

                for (DataSnapshot s : snapshot.getChildren()) {
                    Announcement a = s.getValue(Announcement.class);
                    if (a == null) continue;

                    if (a.getId() == null || a.getId().trim().isEmpty()) a.setId(s.getKey());
                    if (a.getId() == null) continue;

                    // ✅ own post map এ দেখাবে না (facebook style: own posts profile)
                    if (myUid != null && myUid.equals(a.getUserId())) continue;

                    // ✅ show only opposite role
                    String postRole = safe(a.getUserRole());
                    if (!wantRole.equals(postRole)) continue;

                    // ✅ radius filter
                    if (hasMyLoc) {
                        double d = DistanceUtil.distanceKm(myLat, myLng, a.getLat(), a.getLng());
                        if (d > Constants.FEED_RADIUS_KM) continue;
                    }

                    LatLng pos = new LatLng(a.getLat(), a.getLng());
                    Marker m = mMap.addMarker(new MarkerOptions()
                            .position(pos)
                            .title(safe(a.getTitle()).isEmpty() ? "Announcement" : a.getTitle())
                            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_VIOLET)));

                    if (m != null) {
                        m.setTag(a.getId());
                        markerMap.put(a.getId(), a);
                    }
                }
            }

            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    // -------------------- BOTTOM SHEET (NO GARBAGE IMAGE) --------------------

    private Announcement lastSelected = null;

    private void showBottomSheet(Announcement a) {
        lastSelected = a;

        String badge = safe(a.getDisplayCategoryLabel());
        if (badge.isEmpty()) badge = safe(a.getCategory());
        if (badge.isEmpty()) badge = "Category";
        tvBadge.setText(badge);

        tvTitle.setText(safe(a.getTitle()));
        tvDesc.setText(safe(a.getDescription()));

        if (hasMyLoc) {
            double d = DistanceUtil.distanceKm(myLat, myLng, a.getLat(), a.getLng());
            tvDistance.setText(String.format(Locale.getDefault(), "%.1f km away", d));
        } else {
            tvDistance.setText("Nearby");
        }

        // ✅ Glide দিয়ে image load
        String imgUrl = safe(a.getImageUrl());
        if (!imgUrl.isEmpty()) {
            ivCover.setVisibility(android.view.View.VISIBLE);
            Glide.with(this)
                    .load(imgUrl)
                    .centerCrop()
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .into(ivCover);
        } else {
            ivCover.setVisibility(android.view.View.GONE);
            ivCover.setImageDrawable(null);
        }

        btnCall.setOnClickListener(v -> {
            String phone = safe(a.getPhone());
            if (phone.isEmpty()) {
                Toast.makeText(this, "No phone number", Toast.LENGTH_SHORT).show();
                return;
            }
            startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone)));
        });

        btnChat.setOnClickListener(v -> {
            String phone = safe(a.getPhone());
            if (phone.isEmpty()) {
                Toast.makeText(this, "No phone number", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent i = new Intent(Intent.ACTION_SENDTO);
            i.setData(Uri.parse("smsto:" + phone));
            i.putExtra("sms_body", "Hello! I'm interested in: " + safe(a.getTitle()));
            startActivity(i);
        });

        btnDetails.setOnClickListener(v ->
                Toast.makeText(this, "Details screen later", Toast.LENGTH_SHORT).show());

        sheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
    }

    // -------------------- DIRECTIONS (FREE: OPEN GOOGLE MAPS) --------------------

    private void openDirectionsToSelected() {
        if (!hasMyLoc) {
            Toast.makeText(this, "Location not ready", Toast.LENGTH_SHORT).show();
            return;
        }
        if (lastSelected == null) {
            Toast.makeText(this, "Select a marker first", Toast.LENGTH_SHORT).show();
            return;
        }

        double dLat = lastSelected.getLat();
        double dLng = lastSelected.getLng();

        // Google Maps navigation: shows live route line (Pathao/Uber style)
        Uri gmmIntentUri = Uri.parse("google.navigation:q=" + dLat + "," + dLng + "&mode=d");
        Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
        mapIntent.setPackage("com.google.android.apps.maps");

        if (mapIntent.resolveActivity(getPackageManager()) != null) {
            startActivity(mapIntent);
        } else {
            // fallback (no package)
            Uri web = Uri.parse("https://www.google.com/maps/dir/?api=1&destination="
                    + dLat + "," + dLng + "&travelmode=driving");
            startActivity(new Intent(Intent.ACTION_VIEW, web));
        }
    }

    private String safe(String s) { return s == null ? "" : s.trim(); }
}
