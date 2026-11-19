package com.example.towncrierbd.utils;

import com.example.towncrierbd.models.Announcement;

public final class RequestLifecycle {
    private RequestLifecycle() {}

    public static boolean request(Announcement a) {
        return a != null && ("request".equals(a.getPostType()) || "General User".equals(a.getUserRole()));
    }

    public static boolean terminal(Announcement a) {
        return a != null && ("received".equals(a.getStatus()) || "cancelled".equals(a.getStatus()));
    }

    public static boolean inProgress(Announcement a) {
        return request(a) && ("accepted".equals(a.getStatus()) || "delivered".equals(a.getStatus()));
    }

    public static boolean live(Announcement a, long now) {
        if (a == null || !a.isActive() || terminal(a)) return false;
        if (request(a)) {
            // Delivered requests stay alive until buyer confirms Received / Not Received
            if ("delivered".equals(a.getStatus())) return true;
            // Open and accepted requests expire automatically once user's 1st original deadline passes
            long masterExpire = RequestBidManager.getOriginalDeadline(a);
            return masterExpire <= 0 || masterExpire > now;
        }
        return a.getExpireAt() <= 0 || a.getExpireAt() > now;
    }

    public static String seller(Announcement a) {
        if (a == null) return "";
        if (a.getDeliveredByUid() != null && !a.getDeliveredByUid().isEmpty() && "delivered".equals(a.getStatus())) {
            return a.getDeliveredByUid();
        }
        return !a.getAcceptedByUid().isEmpty()
                ? a.getAcceptedByUid()
                : (a.getDeliveredByUid() == null ? "" : a.getDeliveredByUid());
    }

    public static boolean hasActiveJob(Announcement a, String uid, long now) {
        if (!request(a) || !live(a, now) || uid == null || uid.isEmpty()) return false;
        if ("delivered".equals(a.getStatus())) {
            return uid.equals(seller(a));
        }
        return RequestBidManager.hasActiveBid(a, uid, now);
    }

    /**
     * Multiple announcers can accept the same user request while it is open or accepted
     * (by offering a time <= current minimum remaining time).
     */
    public static boolean canAccept(Announcement a, String uid, String role, long now) {
        if (!request(a) || !live(a, now)) return false;
        if (!"open".equals(a.getStatus()) && !"accepted".equals(a.getStatus())) return false;
        if (!"ANNOUNCER".equals(role) || uid == null || uid.equals(a.getUserId())) return false;
        if (RequestBidManager.getMaxAllowedOfferMinutes(a, now) <= 0) return false;
        return a.getSourceAnnouncerUid().isEmpty() || uid.equals(a.getSourceAnnouncerUid());
    }

    public static boolean canDeliver(Announcement a, String uid) {
        long now = System.currentTimeMillis();
        return request(a) && live(a, now) && "accepted".equals(a.getStatus()) && uid != null
                && (uid.equals(seller(a)) || RequestBidManager.hasActiveBid(a, uid, now));
    }

    public static boolean canCancel(Announcement a, String uid) {
        if (!request(a) || !a.isActive() || terminal(a) || uid == null) return false;
        if (uid.equals(a.getUserId())) {
            return !"delivered".equals(a.getStatus());
        }
        long now = System.currentTimeMillis();
        if ("delivered".equals(a.getStatus())) return uid.equals(seller(a));
        return uid.equals(seller(a)) || RequestBidManager.hasActiveBid(a, uid, now);
    }

    /**
     * All matching announcers continue to see open & accepted requests so they see
     * the live minimum time updates (e.g. 30m -> 25m -> 15m) and can bid faster times.
     */
    public static boolean visibleToSeller(Announcement a, String uid, long now) {
        if (!request(a) || !live(a, now) || uid == null || uid.equals(a.getUserId())) return false;
        if ("delivered".equals(a.getStatus())) {
            return uid.equals(seller(a));
        }
        if (RequestBidManager.hasActiveBid(a, uid, now)) return true;
        return a.getSourceAnnouncerUid().isEmpty() || uid.equals(a.getSourceAnnouncerUid());
    }

    public static int compareProfile(Announcement a, Announcement b, long now) {
        boolean x = live(a, now), y = live(b, now);
        return x != y ? (x ? -1 : 1) : Long.compare(b.getTime(), a.getTime());
    }
}
