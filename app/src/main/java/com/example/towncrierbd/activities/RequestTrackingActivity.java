package com.example.towncrierbd.activities;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.*;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.widget.NestedScrollView;

import com.example.towncrierbd.models.Announcement;
import com.example.towncrierbd.utils.*;
import com.google.android.gms.maps.*;
import com.google.android.gms.maps.model.*;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;

import java.util.Locale;

/**
 * Uber/Pathao style live delivery tracking map for both Buyer (General User) and Seller (Announcer).
 */
public class RequestTrackingActivity extends AppCompatActivity {
    private TextView tvStatusHeader, tvDistanceEta, tvPersonName, tvItemsList;
    private MaterialButton btnCall, btnChat, btnPrimaryAction;
    private GoogleMap map;
    private DatabaseReference postRef, trackingRef, sellerUserRef;
    private ValueEventListener postListener, trackingListener, sellerUserListener;
    private Announcement request;
    private DataSnapshot trackingSnap;
    private double currentSellerLat = 0.0, currentSellerLng = 0.0;
    private long lastSellerUpdate = 0;
    private String buyerName = "", buyerPhotoUrl = "";
    private String sellerName = "", sellerPhotoUrl = "";
    private String watchedSellerUid = "";
    private boolean buyerProfileLoaded = false;
    private Marker destinationMarker, sellerMarker;
    private Polyline routeLine;
    private boolean fittedCamera = false;
    private final Handler timer = new Handler(Looper.getMainLooper());
    private final Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            if (request != null) RequestBidManager.syncExpiredBidsIfNeeded(request, System.currentTimeMillis());
            render();
            timer.postDelayed(this, 5000);
        }
    };

    private String text(String en, String bn) {
        return PostCatalog.text(this, en, bn);
    }

    private boolean isBuyer() {
        String myUid = FirebaseAuth.getInstance().getUid();
        return request != null && myUid != null && myUid.equals(request.getUserId());
    }

    private boolean isSeller() {
        String myUid = FirebaseAuth.getInstance().getUid();
        return request != null && myUid != null && (myUid.equals(request.getAcceptedByUid())
                || myUid.equals(RequestLifecycle.seller(request))
                || RequestBidManager.hasActiveBid(request, myUid, System.currentTimeMillis()));
    }

    private boolean allowed() {
        return (isBuyer() || isSeller()) && PostWorkflow.live(request) && RequestLifecycle.inProgress(request);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String id = getIntent().getStringExtra("announcementId");
        if (id == null || FirebaseAuth.getInstance().getUid() == null) {
            Toast.makeText(this, "Request not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        buildUi();

        postRef = FirebaseDatabase.getInstance().getReference(Constants.DB_ANNOUNCEMENTS).child(id);
        trackingRef = FirebaseDatabase.getInstance().getReference("requestTracking").child(id);

        postListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                request = snapshot.getValue(Announcement.class);
                if (request != null && (request.getId() == null || request.getId().isEmpty())) {
                    request.setId(snapshot.getKey());
                }
                if (request == null || !allowed()) {
                    if (request != null && !PostWorkflow.live(request)) {
                        Toast.makeText(RequestTrackingActivity.this, text("Delivery closed.", "ডেলিভারি সম্পন্ন বা বন্ধ হয়েছে।"), Toast.LENGTH_SHORT).show();
                        finish();
                        return;
                    }
                }
                attachSellerLocationListener();
                render();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                request = null;
                render();
            }
        };
        postRef.addValueEventListener(postListener);
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#F1F5F9"));

        // Top Header Card
        CardView headerCard = new CardView(this);
        headerCard.setCardElevation(8f);
        headerCard.setRadius(0f);
        headerCard.setCardBackgroundColor(Color.WHITE);

        LinearLayout headerLayout = new LinearLayout(this);
        headerLayout.setOrientation(LinearLayout.VERTICAL);
        headerLayout.setPadding(32, 24, 32, 24);

        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);

        ImageView ivBack = new ImageView(this);
        ivBack.setImageResource(android.R.drawable.ic_menu_revert);
        ivBack.setColorFilter(Color.parseColor("#1E293B"));
        ivBack.setPadding(0, 0, 24, 0);
        ivBack.setOnClickListener(v -> finish());
        topBar.addView(ivBack);

        tvStatusHeader = new TextView(this);
        tvStatusHeader.setTextSize(18f);
        tvStatusHeader.setTypeface(null, Typeface.BOLD);
        tvStatusHeader.setTextColor(Color.parseColor("#0F172A"));
        tvStatusHeader.setText(text("Live Delivery Tracking", "লাইভ ডেলিভারি ট্র্যাকিং"));
        topBar.addView(tvStatusHeader, new LinearLayout.LayoutParams(0, -2, 1f));

        headerLayout.addView(topBar);

        tvDistanceEta = new TextView(this);
        tvDistanceEta.setTextSize(14f);
        tvDistanceEta.setTextColor(Color.parseColor("#2563EB"));
        tvDistanceEta.setTypeface(null, Typeface.BOLD);
        tvDistanceEta.setPadding(0, 12, 0, 0);
        tvDistanceEta.setText(text("Connecting live location…", "লাইভ লোকেশন যুক্ত হচ্ছে…"));
        headerLayout.addView(tvDistanceEta);

        headerCard.addView(headerLayout);
        root.addView(headerCard);

        // Center Map Holder
        FrameLayout mapHolder = new FrameLayout(this);
        int mapId = com.example.towncrierbd.R.id.deliveryMap;
        mapHolder.setId(mapId);
        root.addView(mapHolder, new LinearLayout.LayoutParams(-1, 0, 1f));

        SupportMapFragment mapFragment = SupportMapFragment.newInstance();
        getSupportFragmentManager().beginTransaction().replace(mapId, mapFragment).commit();
        mapFragment.getMapAsync(googleMap -> {
            map = googleMap;
            map.getUiSettings().setZoomControlsEnabled(true);
            map.getUiSettings().setMyLocationButtonEnabled(true);
            render();
        });

        // Bottom Info & Action Card (Uber / Pathao style)
        CardView bottomCard = new CardView(this);
        bottomCard.setCardElevation(16f);
        bottomCard.setRadius(32f);
        bottomCard.setCardBackgroundColor(Color.WHITE);

        LinearLayout bottomLayout = new LinearLayout(this);
        bottomLayout.setOrientation(LinearLayout.VERTICAL);
        bottomLayout.setPadding(36, 28, 36, 32);

        tvPersonName = new TextView(this);
        tvPersonName.setTextSize(16f);
        tvPersonName.setTypeface(null, Typeface.BOLD);
        tvPersonName.setTextColor(Color.parseColor("#1E293B"));
        bottomLayout.addView(tvPersonName);

        tvItemsList = new TextView(this);
        tvItemsList.setTextSize(13f);
        tvItemsList.setTextColor(Color.parseColor("#64748B"));
        tvItemsList.setPadding(0, 4, 0, 16);
        bottomLayout.addView(tvItemsList);

        // Action Buttons Row: Call & Chat
        LinearLayout buttonsRow = new LinearLayout(this);
        buttonsRow.setOrientation(LinearLayout.HORIZONTAL);

        btnCall = new MaterialButton(this);
        btnCall.setText(text("📞 Call", "📞 কল"));
        btnCall.setTextSize(14f);
        btnCall.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#10B981")));
        btnCall.setTextColor(Color.WHITE);
        btnCall.setCornerRadius(20);
        LinearLayout.LayoutParams callLp = new LinearLayout.LayoutParams(0, -2, 1f);
        callLp.rightMargin = 12;
        buttonsRow.addView(btnCall, callLp);

        btnChat = new MaterialButton(this);
        btnChat.setText(text("💬 Chat", "💬 চ্যাট"));
        btnChat.setTextSize(14f);
        btnChat.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#3B82F6")));
        btnChat.setTextColor(Color.WHITE);
        btnChat.setCornerRadius(20);
        LinearLayout.LayoutParams chatLp = new LinearLayout.LayoutParams(0, -2, 1f);
        chatLp.leftMargin = 12;
        buttonsRow.addView(btnChat, chatLp);

        bottomLayout.addView(buttonsRow);

        // Primary Workflow Button (e.g. Mark Delivered / Confirm Received)
        btnPrimaryAction = new MaterialButton(this);
        btnPrimaryAction.setTextSize(15f);
        btnPrimaryAction.setTypeface(null, Typeface.BOLD);
        btnPrimaryAction.setCornerRadius(24);
        LinearLayout.LayoutParams primaryLp = new LinearLayout.LayoutParams(-1, -2);
        primaryLp.topMargin = 16;
        bottomLayout.addView(btnPrimaryAction, primaryLp);

        bottomCard.addView(bottomLayout);
        root.addView(bottomCard);

        setContentView(root);
    }

    private void attachSellerLocationListener() {
        if (request == null) return;
        String sellerUid = RequestLifecycle.seller(request);
        if (sellerUid == null || sellerUid.isEmpty()) sellerUid = request.getAcceptedByUid();
        if (sellerUid.isEmpty()) return;

        // 1. Listen to requestTracking/{announcementId}
        if (trackingListener == null) {
            trackingListener = new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    trackingSnap = snapshot;
                    if (snapshot.exists()) {
                        Double lat = snapshot.child("lat").getValue(Double.class);
                        Double lng = snapshot.child("lng").getValue(Double.class);
                        Long updated = snapshot.child("updatedAt").getValue(Long.class);
                        if (lat != null && lng != null && lat != 0.0 && lng != 0.0) {
                            currentSellerLat = lat;
                            currentSellerLng = lng;
                            if (updated != null) lastSellerUpdate = updated;
                            render();
                        }
                    }
                }
                @Override public void onCancelled(@NonNull DatabaseError error) {}
            };
            trackingRef.addValueEventListener(trackingListener);
        }

        if (!buyerProfileLoaded && request.getUserId() != null && !request.getUserId().isEmpty()) {
            buyerProfileLoaded = true;
            buyerName = request.getUserName() != null ? request.getUserName() : "";
            FirebaseDatabase.getInstance().getReference(Constants.DB_USERS)
                    .child(request.getUserId())
                    .addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snap) {
                            String n = snap.child("name").getValue(String.class);
                            String p = snap.child("photoUrl").getValue(String.class);
                            if (n != null && !n.trim().isEmpty()) buyerName = n.trim();
                            buyerPhotoUrl = p != null ? p.trim() : "";
                            if (destinationMarker != null) {
                                MapMarkerHelper.bindProfileMarker(RequestTrackingActivity.this, destinationMarker,
                                        request.getUserId(), buyerName, buyerPhotoUrl, Color.parseColor("#E11D48"));
                            }
                        }
                        @Override public void onCancelled(@NonNull DatabaseError error) {}
                    });
        }

        // 2. Also listen directly to users/{sellerUid} to catch live GPS published by DeliveryLocationService
        if (sellerUserListener == null || !sellerUid.equals(watchedSellerUid)) {
            if (sellerUserRef != null && sellerUserListener != null) {
                sellerUserRef.removeEventListener(sellerUserListener);
            }
            watchedSellerUid = sellerUid;
            sellerUserRef = FirebaseDatabase.getInstance().getReference(Constants.DB_USERS).child(sellerUid);
            sellerUserListener = new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    String n = snapshot.child("name").getValue(String.class);
                    String p = snapshot.child("photoUrl").getValue(String.class);
                    if (n != null && !n.trim().isEmpty()) sellerName = n.trim();
                    sellerPhotoUrl = p != null ? p.trim() : "";
                    Double lat = snapshot.child("lat").getValue(Double.class);
                    Double lng = snapshot.child("lng").getValue(Double.class);
                    Long updated = snapshot.child("locationUpdatedAt").getValue(Long.class);
                    if (lat != null && lng != null && lat != 0.0 && lng != 0.0) {
                        currentSellerLat = lat;
                        currentSellerLng = lng;
                        if (updated != null) lastSellerUpdate = updated;
                    }
                    render();
                }
                @Override public void onCancelled(@NonNull DatabaseError error) {}
            };
            sellerUserRef.addValueEventListener(sellerUserListener);
        }
    }

    private void render() {
        if (request == null) return;
        boolean buyer = isBuyer();
        boolean seller = isSeller();

        String status = request.getStatus();
        if ("delivered".equals(status)) {
            tvStatusHeader.setText(text("Order Delivered", "অর্ডার ডেলিভারি হয়েছে"));
            tvStatusHeader.setTextColor(Color.parseColor("#16A34A"));
        } else {
            tvStatusHeader.setText(text("En Route — Live Tracking", "পথে আছেন — লাইভ ট্র্যাকিং"));
            tvStatusHeader.setTextColor(Color.parseColor("#2563EB"));
        }

        // Person Info
        if (seller) {
            tvPersonName.setText(text("Buyer: ", "ক্রেতা: ") + (request.getUserName() != null ? request.getUserName() : "User"));
            String quantities = PostWorkflow.quantities(this, request);
            tvItemsList.setText(quantities.isEmpty() ? request.getTitle() : quantities);
            String phone = request.getPhone();
            btnCall.setVisibility(phone != null && !phone.trim().isEmpty() ? View.VISIBLE : View.GONE);
            btnCall.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone))));
            btnChat.setOnClickListener(v -> PostWorkflow.chat(this, request));

            btnPrimaryAction.setVisibility(View.VISIBLE);
            btnPrimaryAction.setText(text("✅ Mark Delivered", "✅ ডেলিভারি দেওয়া হয়েছে"));
            btnPrimaryAction.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#10B981")));
            btnPrimaryAction.setOnClickListener(v -> {
                new AlertDialog.Builder(this)
                        .setTitle(text("Confirm Delivery", "ডেলিভারি নিশ্চিত করুন"))
                        .setMessage(text("Have you arrived and delivered the products to the buyer?", "আপনি কি ক্রেতার ঠিকানায় পণ্য পৌঁছে দিয়েছেন?"))
                        .setPositiveButton(text("Yes, Delivered", "হ্যাঁ, দিয়েছি"), (d, w) -> {
                            PostWorkflow.updateDelivery(this, request, "delivered", () -> {
                                Toast.makeText(this, text("Marked as delivered!", "ডেলিভারি সম্পন্ন হিসেবে চিহ্নিত হয়েছে!"), Toast.LENGTH_SHORT).show();
                            });
                        })
                        .setNegativeButton(text("Cancel", "বাতিল"), null)
                        .show();
            });
        } else if (buyer) {
            String sellerName = request.getAcceptedByName();
            if (sellerName == null || sellerName.isEmpty()) sellerName = "Announcer";
            tvPersonName.setText(text("Seller: ", "বিক্রেতা: ") + sellerName);
            String quantities = PostWorkflow.quantities(this, request);
            tvItemsList.setText(quantities.isEmpty() ? request.getTitle() : quantities);

            String peerUid = PostWorkflow.chatPeer(request);
            btnCall.setVisibility(View.GONE);
            // Fetch seller phone if available
            if (!peerUid.isEmpty()) {
                FirebaseDatabase.getInstance().getReference(Constants.DB_USERS).child(peerUid).child("phone").get()
                        .addOnSuccessListener(snap -> {
                            String phone = snap.getValue(String.class);
                            if (phone != null && !phone.isEmpty()) {
                                btnCall.setVisibility(View.VISIBLE);
                                btnCall.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone))));
                            }
                        });
            }
            btnChat.setOnClickListener(v -> PostWorkflow.chat(this, request));

            if ("delivered".equals(status)) {
                btnPrimaryAction.setVisibility(View.VISIBLE);
                btnPrimaryAction.setText(text("🎉 Confirm Received", "🎉 পণ্য পেয়েছি"));
                btnPrimaryAction.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#2563EB")));
                btnPrimaryAction.setOnClickListener(v -> {
                    new AlertDialog.Builder(this)
                            .setTitle(text("Confirm Receipt", "পণ্য গ্রহণ নিশ্চিত করুন"))
                            .setMessage(text("Did you receive all requested items?", "আপনি কি সব পণ্য সঠিকভাবে পেয়েছেন?"))
                            .setPositiveButton(text("Yes, Received", "হ্যাঁ, পেয়েছি"), (d, w) -> {
                                PostWorkflow.updateDelivery(this, request, "received", () -> {
                                    Toast.makeText(this, text("Delivery completed. Thank you!", "পণ্য গ্রহণ সম্পন্ন হয়েছে। ধন্যবাদ!"), Toast.LENGTH_SHORT).show();
                                    finish();
                                });
                            })
                            .setNegativeButton(text("Not Received", "পাইনি"), (d, w) -> {
                                PostWorkflow.updateDelivery(this, request, "notReceived", () -> {
                                    Toast.makeText(this, text("Reported not received.", "অবহিত করা হয়েছে।"), Toast.LENGTH_SHORT).show();
                                });
                            })
                            .show();
                });
            } else {
                btnPrimaryAction.setVisibility(View.GONE);
            }
        }

        // Map Render
        if (map == null) return;
        LatLng destination = new LatLng(request.getLat(), request.getLng());

        String dName = !buyerName.isEmpty() ? buyerName : (request.getUserName() != null ? request.getUserName() : "Buyer");
        if (destinationMarker == null) {
            destinationMarker = map.addMarker(new MarkerOptions()
                    .position(destination)
                    .title(text("Delivery Location", "ডেলিভারির স্থান"))
                    .icon(MapMarkerHelper.getInitialOrCachedDescriptor(this, request.getUserId(), dName, buyerPhotoUrl, Color.parseColor("#E11D48")))
                    .anchor(0.5f, 1.0f));
            MapMarkerHelper.bindProfileMarker(this, destinationMarker, request.getUserId(), dName, buyerPhotoUrl, Color.parseColor("#E11D48"));
        } else {
            destinationMarker.setPosition(destination);
            MapMarkerHelper.bindProfileMarker(this, destinationMarker, request.getUserId(), dName, buyerPhotoUrl, Color.parseColor("#E11D48"));
        }

        if (currentSellerLat != 0.0 && currentSellerLng != 0.0) {
            LatLng sellerPos = new LatLng(currentSellerLat, currentSellerLng);
            String sUid = RequestLifecycle.seller(request);
            String sName = !sellerName.isEmpty() ? sellerName : (request.getAcceptedByName() != null ? request.getAcceptedByName() : "Seller");
            if (sellerMarker == null) {
                sellerMarker = map.addMarker(new MarkerOptions()
                        .position(sellerPos)
                        .title(text("Seller Location", "বিক্রেতার অবস্থান"))
                        .icon(MapMarkerHelper.getInitialOrCachedDescriptor(this, sUid, sName, sellerPhotoUrl, Color.parseColor("#2563EB")))
                        .anchor(0.5f, 1.0f));
                MapMarkerHelper.bindProfileMarker(this, sellerMarker, sUid, sName, sellerPhotoUrl, Color.parseColor("#2563EB"));
            } else {
                sellerMarker.setPosition(sellerPos);
                MapMarkerHelper.bindProfileMarker(this, sellerMarker, sUid, sName, sellerPhotoUrl, Color.parseColor("#2563EB"));
            }

            // Draw route line
            if (routeLine == null) {
                routeLine = map.addPolyline(new PolylineOptions()
                        .add(sellerPos, destination)
                        .color(Color.parseColor("#2563EB"))
                        .width(10f));
            } else {
                java.util.List<LatLng> points = new java.util.ArrayList<>();
                points.add(sellerPos);
                points.add(destination);
                routeLine.setPoints(points);
            }

            double dist = DistanceUtil.distanceKm(destination.latitude, destination.longitude, currentSellerLat, currentSellerLng);
            long agoSec = Math.max(0, (System.currentTimeMillis() - lastSellerUpdate) / 1000);
            String agoText = agoSec < 60 ? (agoSec + text("s ago", " সেকেন্ড আগে")) : ((agoSec / 60) + text("m ago", " মিনিট আগে"));
            tvDistanceEta.setText(String.format(Locale.getDefault(), "📍 %.2f km ", dist) +
                    text("away · Live update: ", "দূরে · আপডেট: ") + agoText);

            if (!fittedCamera) {
                LatLngBounds bounds = new LatLngBounds.Builder()
                        .include(destination)
                        .include(sellerPos)
                        .build();
                try {
                    map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 180));
                    fittedCamera = true;
                } catch (Exception ignored) {
                    map.moveCamera(CameraUpdateFactory.newLatLngZoom(destination, 14f));
                }
            }
        } else {
            tvDistanceEta.setText(text("Waiting for seller location signal…", "বিক্রেতার লোকেশন সিগন্যালের অপেক্ষায়…"));
            if (!fittedCamera) {
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(destination, 15f));
                fittedCamera = true;
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        timer.post(refreshRunnable);
    }

    @Override
    protected void onPause() {
        timer.removeCallbacks(refreshRunnable);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        timer.removeCallbacks(refreshRunnable);
        if (postRef != null && postListener != null) postRef.removeEventListener(postListener);
        if (trackingRef != null && trackingListener != null) trackingRef.removeEventListener(trackingListener);
        if (sellerUserRef != null && sellerUserListener != null) sellerUserRef.removeEventListener(sellerUserListener);
        super.onDestroy();
    }
}
