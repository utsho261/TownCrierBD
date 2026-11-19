package com.example.towncrierbd.utils;

import com.example.towncrierbd.models.Announcement;
import com.example.towncrierbd.models.RequestedItem;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Strict active-post matcher for General User self-requests:
 * - An announcer with NO active post never receives requests in feed/map/notifications.
 * - When a user requests 1 or multiple items under a category, ONLY an announcer who has
 *   a live active post containing ALL requested items matches.
 */
public final class RequestMatcher {

    private RequestMatcher() {}

    private static String norm(String raw) {
        if (raw == null) return "";
        return raw.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    public static String extractPrimaryCategory(Announcement a) {
        if (a == null) return "";
        if (a.getCategory() != null && !a.getCategory().trim().isEmpty()) {
            return a.getCategory().trim();
        }
        List<String> cats = a.getSelectedCategories();
        if (cats != null) {
            for (String c : cats) {
                if (c != null && !c.trim().isEmpty()) return c.trim();
            }
        }
        return "";
    }

    public static Set<String> extractRequestItems(Announcement req) {
        Set<String> items = new LinkedHashSet<>();
        if (req == null) return items;

        List<String> products = req.getSelectedProducts();
        if (products != null) {
            for (String p : products) {
                String n = norm(p);
                if (!n.isEmpty()) items.add(n);
            }
        }

        List<RequestedItem> reqItems = req.getRequestedItems();
        if (reqItems != null) {
            for (RequestedItem ri : reqItems) {
                if (ri == null) continue;
                String n = norm(ri.getProduct());
                if (!n.isEmpty()) items.add(n);
            }
        }

        if (items.isEmpty() && req.getCustomSubcategory() != null && !req.getCustomSubcategory().trim().isEmpty()) {
            for (String part : req.getCustomSubcategory().split(",")) {
                String n = norm(part);
                if (!n.isEmpty()) items.add(n);
            }
        }
        return items;
    }

    public static Set<String> extractPostItems(Announcement post) {
        Set<String> items = new LinkedHashSet<>();
        if (post == null) return items;

        List<String> products = post.getSelectedProducts();
        if (products != null) {
            for (String p : products) {
                String n = norm(p);
                if (!n.isEmpty()) items.add(n);
            }
        }

        List<String> subs = post.getSelectedSubcategories();
        if (subs != null) {
            for (String s : subs) {
                String n = norm(s);
                if (!n.isEmpty()) items.add(n);
            }
        }

        if (post.getSubcategory() != null && !post.getSubcategory().trim().isEmpty()) {
            String n = norm(post.getSubcategory());
            if (!n.isEmpty()) items.add(n);
        }

        if (post.getCustomSubcategory() != null && !post.getCustomSubcategory().trim().isEmpty()) {
            for (String part : post.getCustomSubcategory().split(",")) {
                String n = norm(part);
                if (!n.isEmpty()) items.add(n);
            }
        }
        return items;
    }

    public static boolean postMatchesCategory(Announcement post, String reqCategory) {
        if (post == null) return false;
        String target = norm(reqCategory);
        if (target.isEmpty() || norm(CategoryConfig.CAT_ALL).equals(target)) return true;

        if (target.equals(norm(post.getCategory()))) return true;
        List<String> postCats = post.getSelectedCategories();
        if (postCats != null) {
            for (String c : postCats) {
                if (target.equals(norm(c))) return true;
            }
        }
        return false;
    }

    public static boolean singlePostMatchesRequest(Announcement post, Announcement req, long now) {
        if (post == null || req == null) return false;
        if (RequestLifecycle.request(post)) return false;
        if (!RequestLifecycle.live(post, now)) return false;

        String reqCat = extractPrimaryCategory(req);
        if (!reqCat.isEmpty() && !postMatchesCategory(post, reqCat)) {
            return false;
        }

        Set<String> reqItems = extractRequestItems(req);
        if (reqItems.isEmpty()) {
            return true;
        }

        Set<String> postItems = extractPostItems(post);
        if (postItems.isEmpty()) {
            return false;
        }

        // Strict ALL-item check: every requested item must exist in this active post
        for (String needed : reqItems) {
            if (!postItems.contains(needed)) {
                return false;
            }
        }
        return true;
    }

    public static Announcement findMatchingActivePost(
            Announcement req,
            List<Announcement> announcerActivePosts,
            long now) {
        if (req == null || announcerActivePosts == null || announcerActivePosts.isEmpty()) {
            return null;
        }
        for (Announcement post : announcerActivePosts) {
            if (singlePostMatchesRequest(post, req, now)) {
                return post;
            }
        }
        return null;
    }

    public static boolean matchesAnnouncerActivePosts(
            Announcement req,
            List<Announcement> announcerActivePosts,
            long now) {
        return findMatchingActivePost(req, announcerActivePosts, now) != null;
    }
}
