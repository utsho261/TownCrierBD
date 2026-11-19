package com.example.towncrierbd.utils;

import android.app.Activity;
import android.content.Context;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.towncrierbd.models.Announcement;
import com.example.towncrierbd.models.RequestBid;
import com.example.towncrierbd.services.DeliveryLocationService;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.Transaction;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Competitive Multi-Announcer ETA Bidding & Auto-Fallback Manager.
 *
 * Rules:
 * 1. Multiple announcers can accept the same user request by offering an arrival time (in minutes).
 * 2. Offered time MUST be <= the currently effective remaining time (and <= user's original time).
 * 3. The post's displayed deadline (expireAt) and leading seller automatically update to the
 *    announcer who offered the SHORTEST remaining time (e.g. 30m -> 25m -> 15m).
 * 4. If the leading announcer fails to arrive/deliver before their offered deadline, their bid
 *    automatically expires and is removed from them, and the post falls back to the next fastest
 *    active announcer's deadline (or back to user's original remaining time if none remain).
 * 5. If no one arrives before the user's original deadline (originalExpireAt), the post becomes
 *    inactive automatically.
 */
public final class RequestBidManager {

    private static final Map<String, Long> lastSyncAttempt = new ConcurrentHashMap<>();

    private RequestBidManager() {}

    public static long getOriginalDeadline(Announcement a) {
        if (a == null) return 0L;
        return a.getOriginalExpireAt() > 0 ? a.getOriginalExpireAt() : a.getExpireAt();
    }

    public static long getEffectiveDeadline(Announcement a, long now) {
        if (a == null) return 0L;
        long orig = getOriginalDeadline(a);
        long minDeadline = Long.MAX_VALUE;

        Map<String, RequestBid> bids = a.getAcceptedBids();
        if (bids != null) {
            for (RequestBid bid : bids.values()) {
                if (bid != null && "active".equals(bid.getStatus()) && bid.getDeadlineAt() > now) {
                    if (bid.getDeadlineAt() < minDeadline) {
                        minDeadline = bid.getDeadlineAt();
                    }
                }
            }
        }
        if (minDeadline != Long.MAX_VALUE) {
            return orig > 0 ? Math.min(minDeadline, orig) : minDeadline;
        }
        return orig;
    }

    public static int getMaxAllowedOfferMinutes(Announcement a, long now) {
        long eff = getEffectiveDeadline(a, now);
        if (eff <= 0) return 30;
        long remMs = eff - now;
        if (remMs <= 0) return 0;
        return (int) Math.max(1, (long) Math.ceil(remMs / 60000.0));
    }

    public static RequestBid getActiveBid(Announcement a, String uid, long now) {
        if (a == null || uid == null || uid.isEmpty()) return null;
        if (!a.isActive() || RequestLifecycle.terminal(a)) return null;

        Map<String, RequestBid> bids = a.getAcceptedBids();
        if (bids != null && !bids.isEmpty()) {
            RequestBid bid = bids.get(uid);
            if (bid != null && "active".equals(bid.getStatus())
                    && (bid.getDeadlineAt() <= 0 || bid.getDeadlineAt() > now)) {
                return bid;
            }
            return null;
        }

        // Legacy compatibility for older accepted posts created before acceptedBids existed
        if (uid.equals(a.getAcceptedByUid())
                && ("accepted".equals(a.getStatus()) || "delivered".equals(a.getStatus()))) {
            long orig = getOriginalDeadline(a);
            if ("delivered".equals(a.getStatus()) || orig <= 0 || orig > now) {
                int remMin = (int) Math.max(1, (long) Math.ceil(Math.max(0, orig - now) / 60000.0));
                return new RequestBid(uid, a.getAcceptedByName(), remMin, a.getAcceptedAt(), orig, "active");
            }
        }
        return null;
    }

    public static boolean hasActiveBid(Announcement a, String uid, long now) {
        return getActiveBid(a, uid, now) != null;
    }

    public static int getActiveBidCount(Announcement a, long now) {
        if (a == null || !a.isActive() || RequestLifecycle.terminal(a)) return 0;
        Map<String, RequestBid> bids = a.getAcceptedBids();
        if (bids != null && !bids.isEmpty()) {
            int count = 0;
            for (RequestBid bid : bids.values()) {
                if (bid != null && "active".equals(bid.getStatus()) && bid.getDeadlineAt() > now) {
                    count++;
                }
            }
            return count;
        }
        if ("accepted".equals(a.getStatus()) && a.getAcceptedByUid() != null && !a.getAcceptedByUid().isEmpty()) {
            long orig = getOriginalDeadline(a);
            if (orig <= 0 || orig > now) return 1;
        }
        return 0;
    }

    public static boolean needsExpirySync(Announcement a, long now) {
        if (a == null || !RequestLifecycle.request(a) || !a.isActive()) return false;
        if (RequestLifecycle.terminal(a) || "delivered".equals(a.getStatus())) return false;

        long orig = getOriginalDeadline(a);
        if (orig > 0 && now >= orig) return true;

        Map<String, RequestBid> bids = a.getAcceptedBids();
        if (bids != null) {
            for (RequestBid bid : bids.values()) {
                if (bid != null && "active".equals(bid.getStatus())
                        && bid.getDeadlineAt() > 0 && now >= bid.getDeadlineAt()) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Checks if any announcer's offered deadline or the user's original deadline has passed,
     * and atomically transitions the post in Firebase (dropping expired announcer -> promoting
     * next fastest announcer -> or auto-deactivating once user's original time expires).
     */
    public static void syncExpiredBidsIfNeeded(Announcement a, long now) {
        if (!needsExpirySync(a, now) || a.getId() == null || a.getId().isEmpty()) return;
        if (FirebaseAuth.getInstance().getUid() == null) return;

        Long last = lastSyncAttempt.get(a.getId());
        if (last != null && now - last < 5000L) return;
        lastSyncAttempt.put(a.getId(), now);

        DatabaseReference ref = FirebaseDatabase.getInstance()
                .getReference(Constants.DB_ANNOUNCEMENTS)
                .child(a.getId());

        final List<String> expiredUids = new ArrayList<>();
        final String[] promotedUid = {""};
        final String[] promotedName = {""};
        final long[] promotedDeadline = {0L};
        final boolean[] autoDeactivated = {false};

        ref.runTransaction(new Transaction.Handler() {
            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData current) {
                Announcement latest = current.getValue(Announcement.class);
                if (latest == null || !RequestLifecycle.request(latest) || !latest.isActive()) {
                    return Transaction.abort();
                }
                if (RequestLifecycle.terminal(latest) || "delivered".equals(latest.getStatus())) {
                    return Transaction.abort();
                }

                long ts = System.currentTimeMillis();
                long orig = getOriginalDeadline(latest);
                if (latest.getOriginalExpireAt() <= 0 && orig > 0) {
                    latest.setOriginalExpireAt(orig);
                }

                expiredUids.clear();
                promotedUid[0] = "";
                promotedName[0] = "";
                promotedDeadline[0] = 0L;
                autoDeactivated[0] = false;

                boolean changed = false;
                Map<String, RequestBid> bids = latest.getAcceptedBids();

                // Case 1: User's 1st original deadline expired -> auto-deactivate entire post
                if (orig > 0 && ts >= orig) {
                    if (bids != null) {
                        for (RequestBid bid : bids.values()) {
                            if (bid != null && "active".equals(bid.getStatus())) {
                                bid.setStatus("expired");
                                expiredUids.add(bid.getAnnouncerUid());
                            }
                        }
                    }
                    latest.setActive(false);
                    latest.setExpireAt(orig);
                    latest.setStatusUpdatedAt(ts);
                    autoDeactivated[0] = true;
                    current.setValue(latest);
                    return Transaction.success(current);
                }

                // Case 2: Check individual announcer bid deadlines
                RequestBid best = null;
                if (bids != null) {
                    for (RequestBid bid : bids.values()) {
                        if (bid == null) continue;
                        if ("active".equals(bid.getStatus())) {
                            if (bid.getDeadlineAt() > 0 && ts >= bid.getDeadlineAt()) {
                                bid.setStatus("expired");
                                expiredUids.add(bid.getAnnouncerUid());
                                changed = true;
                            } else if (bid.getDeadlineAt() > ts) {
                                if (best == null || bid.getDeadlineAt() < best.getDeadlineAt()) {
                                    best = bid;
                                }
                            }
                        }
                    }
                }

                if (!changed) return Transaction.abort();

                String prevLeader = latest.getAcceptedByUid() != null ? latest.getAcceptedByUid() : "";
                if (best != null) {
                    latest.setStatus("accepted");
                    latest.setAcceptedByUid(best.getAnnouncerUid());
                    latest.setAcceptedByName(best.getAnnouncerName());
                    latest.setExpireAt(orig > 0 ? Math.min(best.getDeadlineAt(), orig) : best.getDeadlineAt());
                    latest.setStatusUpdatedAt(ts);
                    if (!best.getAnnouncerUid().equals(prevLeader)) {
                        promotedUid[0] = best.getAnnouncerUid();
                        promotedName[0] = best.getAnnouncerName();
                        promotedDeadline[0] = latest.getExpireAt();
                    }
                } else {
                    latest.setStatus("open");
                    latest.setAcceptedByUid("");
                    latest.setAcceptedByName("");
                    latest.setExpireAt(orig);
                    latest.setStatusUpdatedAt(ts);
                }

                current.setValue(latest);
                return Transaction.success(current);
            }

            @Override
            public void onComplete(@Nullable DatabaseError error, boolean committed, @Nullable DataSnapshot snapshot) {
                if (error != null || !committed || snapshot == null) return;
                String postTitle = a.getTitle() != null ? a.getTitle() : "Request";

                for (String expUid : expiredUids) {
                    if (expUid != null && !expUid.isEmpty()) {
                        NotificationSender.sendRequestEventNotification(
                                expUid,
                                a.getId(),
                                "expired",
                                "⏰ সময় শেষ (অ্যাক্সেপ্ট বাতিল)",
                                postTitle + " — নির্ধারিত সময়ে না পৌঁছানোয় রিকোয়েস্টটি আপনার তালিকা থেকে সরে গেছে।"
                        );
                    }
                }

                if (!promotedUid[0].isEmpty()) {
                    long remMin = Math.max(1, (long) Math.ceil((promotedDeadline[0] - System.currentTimeMillis()) / 60000.0));
                    NotificationSender.sendRequestEventNotification(
                            promotedUid[0],
                            a.getId(),
                            "promoted",
                            "🏆 আপনি এখন লিডিং বিক্রেতা!",
                            postTitle + " — আপনার সময় বাকি: " + remMin + " মিনিট।"
                    );
                    if (a.getUserId() != null && !a.getUserId().isEmpty()) {
                        NotificationSender.sendRequestEventNotification(
                                a.getUserId(),
                                a.getId(),
                                "accepted",
                                "🔄 নতুন বিক্রেতা আসছেন",
                                promotedName[0] + " (" + remMin + " মিনিট) — " + postTitle
                        );
                    }
                    // Sync requestTracking to the newly promoted announcer
                    FirebaseDatabase.getInstance().getReference(Constants.DB_USERS)
                            .child(promotedUid[0]).get().addOnSuccessListener(userSnap -> {
                                Double lat = userSnap.child("lat").getValue(Double.class);
                                Double lng = userSnap.child("lng").getValue(Double.class);
                                Map<String, Object> track = new HashMap<>();
                                track.put("ownerUid", a.getUserId());
                                track.put("sellerUid", promotedUid[0]);
                                track.put("sellerName", promotedName[0]);
                                track.put("lat", lat != null ? lat : 0.0);
                                track.put("lng", lng != null ? lng : 0.0);
                                track.put("updatedAt", System.currentTimeMillis());
                                FirebaseDatabase.getInstance().getReference("requestTracking")
                                        .child(a.getId()).setValue(track);
                            });
                } else if (autoDeactivated[0] && a.getUserId() != null && !a.getUserId().isEmpty()) {
                    NotificationSender.sendRequestEventNotification(
                            a.getUserId(),
                            a.getId(),
                            "expired",
                            "⏰ রিকোয়েস্টের সময় শেষ",
                            postTitle + " — নির্ধারিত সময়ের মধ্যে কেউ না আসায় পোস্টটি নিষ্ক্রিয় হয়েছে।"
                    );
                }
            }
        });
    }

    /**
     * Announcer accepts a request with an offered arrival time (in minutes).
     * Atomically adds/updates their bid and recalculates the global minimum deadline across all announcers.
     */
    public static void acceptWithOfferMinutes(
            Context c,
            Announcement a,
            int offerMinutes,
            @Nullable Runnable done,
            @Nullable Runnable failed) {

        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null || a == null || a.getId() == null) {
            if (failed != null) failed.run();
            return;
        }
        if (offerMinutes < 1) {
            Toast.makeText(c, PostCatalog.text(c, "Enter at least 1 minute", "কমপক্ষে ১ মিনিট দিন"), Toast.LENGTH_SHORT).show();
            if (failed != null) failed.run();
            return;
        }

        FirebaseDatabase.getInstance().getReference(Constants.DB_USERS).child(uid)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snap) {
                        String rawName = snap.child("name").getValue(String.class);
                        final String actorName = (rawName == null || rawName.trim().isEmpty()) ? "Announcer" : rawName.trim();
                        final Double curLat = snap.child("lat").getValue(Double.class);
                        final Double curLng = snap.child("lng").getValue(Double.class);

                        DatabaseReference postRef = FirebaseDatabase.getInstance()
                                .getReference(Constants.DB_ANNOUNCEMENTS)
                                .child(a.getId());

                        final String[] errorMsg = {""};
                        final boolean[] isNowLeader = {false};

                        postRef.runTransaction(new Transaction.Handler() {
                            @NonNull
                            @Override
                            public Transaction.Result doTransaction(@NonNull MutableData current) {
                                Announcement latest = current.getValue(Announcement.class);
                                if (latest == null) {
                                    errorMsg[0] = PostCatalog.text(c, "Post not found", "পোস্ট পাওয়া যায়নি");
                                    return Transaction.abort();
                                }
                                if (!latest.isActive() || RequestLifecycle.terminal(latest) || "delivered".equals(latest.getStatus())) {
                                    errorMsg[0] = PostCatalog.text(c, "Request is no longer open", "রিকোয়েস্টটি আর সক্রিয় নেই");
                                    return Transaction.abort();
                                }

                                long now = System.currentTimeMillis();
                                long orig = getOriginalDeadline(latest);
                                if (latest.getOriginalExpireAt() <= 0 && orig > 0) {
                                    latest.setOriginalExpireAt(orig);
                                }
                                if (orig > 0 && now >= orig) {
                                    latest.setActive(false);
                                    current.setValue(latest);
                                    errorMsg[0] = PostCatalog.text(c, "Request time has expired", "রিকোয়েস্টের সময় শেষ হয়ে গেছে");
                                    return Transaction.success(current);
                                }

                                Map<String, RequestBid> bids = latest.getAcceptedBids();
                                if (bids == null) bids = new HashMap<>();

                                // Expire stale bids first
                                for (RequestBid b : bids.values()) {
                                    if (b != null && "active".equals(b.getStatus())
                                            && b.getDeadlineAt() > 0 && now >= b.getDeadlineAt()) {
                                        b.setStatus("expired");
                                    }
                                }

                                // Compute current effective deadline (minimum of remaining active bids, or user's original deadline)
                                long curEff = orig;
                                for (RequestBid b : bids.values()) {
                                    if (b != null && "active".equals(b.getStatus()) && b.getDeadlineAt() > now) {
                                        if (curEff <= 0 || b.getDeadlineAt() < curEff) {
                                            curEff = b.getDeadlineAt();
                                        }
                                    }
                                }

                                long proposed = now + offerMinutes * 60_000L;
                                if (curEff > 0 && proposed > curEff + 35_000L) {
                                    int maxMin = (int) Math.max(1, (long) Math.ceil((curEff - now) / 60000.0));
                                    errorMsg[0] = PostCatalog.text(c,
                                            "Must be " + maxMin + " min or less",
                                            "সময় অবশ্যই " + maxMin + " মিনিট বা তার কম হতে হবে");
                                    return Transaction.abort();
                                }

                                long finalDeadline = proposed;
                                if (curEff > 0 && finalDeadline > curEff) finalDeadline = curEff;
                                if (orig > 0 && finalDeadline > orig) finalDeadline = orig;

                                RequestBid myBid = new RequestBid(uid, actorName, offerMinutes, now, finalDeadline, "active");
                                bids.put(uid, myBid);
                                latest.setAcceptedBids(bids);

                                // Recompute leader with smallest deadlineAt
                                RequestBid best = null;
                                for (RequestBid b : bids.values()) {
                                    if (b != null && "active".equals(b.getStatus()) && b.getDeadlineAt() > now) {
                                        if (best == null || b.getDeadlineAt() < best.getDeadlineAt()) {
                                            best = b;
                                        }
                                    }
                                }

                                if (best != null) {
                                    latest.setStatus("accepted");
                                    latest.setAcceptedByUid(best.getAnnouncerUid());
                                    latest.setAcceptedByName(best.getAnnouncerName());
                                    latest.setAcceptedAt(now);
                                    latest.setExpireAt(best.getDeadlineAt());
                                    latest.setStatusUpdatedAt(now);
                                    isNowLeader[0] = uid.equals(best.getAnnouncerUid());
                                }

                                current.setValue(latest);
                                return Transaction.success(current);
                            }

                            @Override
                            public void onComplete(@Nullable DatabaseError error, boolean committed, @Nullable DataSnapshot snapshot) {
                                if (error != null) {
                                    Toast.makeText(c, error.getMessage(), Toast.LENGTH_LONG).show();
                                    if (failed != null) failed.run();
                                    return;
                                }
                                if (!committed || !errorMsg[0].isEmpty()) {
                                    Toast.makeText(c, errorMsg[0].isEmpty()
                                            ? PostCatalog.text(c, "Could not accept request", "রিকোয়েস্ট গ্রহণ করা যায়নি")
                                            : errorMsg[0], Toast.LENGTH_LONG).show();
                                    if (failed != null) failed.run();
                                    return;
                                }

                                Announcement updated = snapshot != null ? snapshot.getValue(Announcement.class) : null;
                                if (updated != null) {
                                    a.setStatus(updated.getStatus());
                                    a.setExpireAt(updated.getExpireAt());
                                    a.setOriginalExpireAt(updated.getOriginalExpireAt());
                                    a.setAcceptedByUid(updated.getAcceptedByUid());
                                    a.setAcceptedByName(updated.getAcceptedByName());
                                    a.setAcceptedBids(updated.getAcceptedBids());
                                }

                                if (c instanceof Activity) {
                                    DeliveryLocationService.startFrom((Activity) c);
                                }

                                if (isNowLeader[0]) {
                                    Map<String, Object> track = new HashMap<>();
                                    track.put("ownerUid", a.getUserId());
                                    track.put("sellerUid", uid);
                                    track.put("sellerName", actorName);
                                    track.put("lat", curLat != null ? curLat : 0.0);
                                    track.put("lng", curLng != null ? curLng : 0.0);
                                    track.put("updatedAt", System.currentTimeMillis());
                                    FirebaseDatabase.getInstance().getReference("requestTracking")
                                            .child(a.getId()).setValue(track);
                                }

                                if (a.getUserId() != null && !a.getUserId().isEmpty()) {
                                    NotificationSender.sendRequestEventNotification(
                                            a.getUserId(),
                                            a.getId(),
                                            "accepted",
                                            PostCatalog.text(c, "Request accepted (" + offerMinutes + " min)", "রিকোয়েস্ট গৃহীত (" + offerMinutes + " মিনিট)"),
                                            actorName + " — " + (a.getTitle() != null ? a.getTitle() : "")
                                    );
                                }

                                Toast.makeText(c, PostCatalog.text(c,
                                        "✅ Accepted! Offer: " + offerMinutes + " min. Open 'Track Paths' to view route.",
                                        "✅ রিকোয়েস্ট গৃহীত হয়েছে (" + offerMinutes + " মিনিট)! 'ট্র্যাক পাথ' থেকে সব রুট দেখুন।"),
                                        Toast.LENGTH_LONG).show();

                                if (done != null) done.run();
                            }
                        });
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Toast.makeText(c, error.getMessage(), Toast.LENGTH_SHORT).show();
                        if (failed != null) failed.run();
                    }
                });
    }

    /**
     * Seller cancels ONLY their own bid. If another announcer has an active bid, that announcer
     * automatically becomes the leader and the time updates to their offered time.
     */
    public static void cancelSellerBid(
            Context c,
            Announcement a,
            String sellerUid,
            String reason,
            @Nullable Runnable done,
            @Nullable Runnable failed) {

        if (a == null || a.getId() == null || sellerUid == null) {
            if (failed != null) failed.run();
            return;
        }

        DatabaseReference postRef = FirebaseDatabase.getInstance()
                .getReference(Constants.DB_ANNOUNCEMENTS)
                .child(a.getId());

        final String[] promotedUid = {""};
        final String[] promotedName = {""};

        postRef.runTransaction(new Transaction.Handler() {
            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData current) {
                Announcement latest = current.getValue(Announcement.class);
                if (latest == null) return Transaction.abort();
                if (RequestLifecycle.terminal(latest)) return Transaction.abort();

                long now = System.currentTimeMillis();
                long orig = getOriginalDeadline(latest);
                if (latest.getOriginalExpireAt() <= 0 && orig > 0) {
                    latest.setOriginalExpireAt(orig);
                }

                Map<String, RequestBid> bids = latest.getAcceptedBids();
                if (bids != null && bids.containsKey(sellerUid)) {
                    RequestBid mine = bids.get(sellerUid);
                    if (mine != null) mine.setStatus("cancelled");
                }

                // Find next fastest active bidder
                RequestBid best = null;
                if (bids != null) {
                    for (RequestBid b : bids.values()) {
                        if (b == null) continue;
                        if ("active".equals(b.getStatus())) {
                            if (b.getDeadlineAt() > 0 && now >= b.getDeadlineAt()) {
                                b.setStatus("expired");
                            } else if (b.getDeadlineAt() > now) {
                                if (best == null || b.getDeadlineAt() < best.getDeadlineAt()) {
                                    best = b;
                                }
                            }
                        }
                    }
                }

                promotedUid[0] = "";
                promotedName[0] = "";

                if (best != null) {
                    latest.setStatus("accepted");
                    latest.setAcceptedByUid(best.getAnnouncerUid());
                    latest.setAcceptedByName(best.getAnnouncerName());
                    latest.setExpireAt(orig > 0 ? Math.min(best.getDeadlineAt(), orig) : best.getDeadlineAt());
                    latest.setStatusUpdatedAt(now);
                    promotedUid[0] = best.getAnnouncerUid();
                    promotedName[0] = best.getAnnouncerName();
                } else if (orig <= 0 || now < orig) {
                    latest.setStatus("open");
                    latest.setAcceptedByUid("");
                    latest.setAcceptedByName("");
                    latest.setExpireAt(orig);
                    latest.setStatusUpdatedAt(now);
                } else {
                    latest.setStatus("cancelled");
                    latest.setActive(false);
                    latest.setCancellationReason(reason != null ? reason : "");
                    latest.setCancelledByUid(sellerUid);
                    latest.setCancelledAt(now);
                    latest.setStatusUpdatedAt(now);
                }

                current.setValue(latest);
                return Transaction.success(current);
            }

            @Override
            public void onComplete(@Nullable DatabaseError error, boolean committed, @Nullable DataSnapshot snapshot) {
                if (error != null || !committed) {
                    Toast.makeText(c, PostCatalog.text(c, "Could not cancel", "বাতিল করা যায়নি"), Toast.LENGTH_SHORT).show();
                    if (failed != null) failed.run();
                    return;
                }
                if (!promotedUid[0].isEmpty()) {
                    NotificationSender.sendRequestEventNotification(
                            promotedUid[0],
                            a.getId(),
                            "promoted",
                            "🏆 আপনি এখন লিডিং বিক্রেতা!",
                            a.getTitle() != null ? a.getTitle() : "Request"
                    );
                }
                if (a.getUserId() != null && !a.getUserId().isEmpty()) {
                    NotificationSender.sendRequestEventNotification(
                            a.getUserId(),
                            a.getId(),
                            "cancelled",
                            PostCatalog.text(c, "Seller cancelled offer", "বিক্রেতা অফার বাতিল করেছেন"),
                            reason != null ? reason : ""
                    );
                }
                Toast.makeText(c, PostCatalog.text(c, "Your acceptance was cancelled", "আপনার অ্যাক্সেপ্ট বাতিল হয়েছে"), Toast.LENGTH_SHORT).show();
                if (done != null) done.run();
            }
        });
    }
}
