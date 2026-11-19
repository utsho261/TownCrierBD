package com.example.towncrierbd.utils;

public final class RequestMode {
    public static final String ACTIVE_POSTS = "active_posts";
    public static final String INDEPENDENT = "independent";

    private RequestMode() {}

    public static String normalize(String value) {
        return INDEPENDENT.equals(value) ? INDEPENDENT : ACTIVE_POSTS;
    }

    public static boolean usesAvailableItems(String postType, String mode) {
        return "request".equals(postType) && ACTIVE_POSTS.equals(normalize(mode));
    }
}
