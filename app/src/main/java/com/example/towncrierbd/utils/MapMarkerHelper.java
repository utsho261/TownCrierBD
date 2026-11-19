package com.example.towncrierbd.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.Marker;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Generates circular profile photo or initial-letter pin markers for Google Maps.
 */
public final class MapMarkerHelper {

    private static final int[] AVATAR_PALETTE = {
            0xFF2563EB, // Blue
            0xFF7C3AED, // Violet
            0xFF059669, // Emerald
            0xFFD97706, // Amber
            0xFFDB2777, // Pink
            0xFF0891B2, // Cyan
            0xFF4F46E5, // Indigo
            0xFFDC2626  // Red
    };

    private static final Map<String, BitmapDescriptor> iconCache = new ConcurrentHashMap<>();

    private MapMarkerHelper() {}

    public static String extractInitial(@Nullable String name) {
        if (name == null) return "?";
        String trimmed = name.trim();
        if (trimmed.isEmpty()) return "?";
        int cp = trimmed.codePointAt(0);
        return new String(Character.toChars(Character.toUpperCase(cp)));
    }

    public static int colorForUser(@Nullable String uid, @Nullable String name, int fallbackColor) {
        String key = (uid != null && !uid.trim().isEmpty()) ? uid : (name != null ? name : "");
        if (key.isEmpty()) return fallbackColor;
        int idx = Math.abs(key.hashCode()) % AVATAR_PALETTE.length;
        return AVATAR_PALETTE[idx];
    }

    /**
     * Immediately returns a cached photo marker (if available) or a crisp initial-letter marker.
     */
    public static BitmapDescriptor getInitialOrCachedDescriptor(
            @NonNull Context context,
            @Nullable String uid,
            @Nullable String name,
            @Nullable String photoUrl,
            int borderColor) {

        String cleanUrl = photoUrl == null ? "" : photoUrl.trim();
        if (!cleanUrl.isEmpty()) {
            String photoKey = "photo|" + cleanUrl + "|" + borderColor;
            BitmapDescriptor cached = iconCache.get(photoKey);
            if (cached != null) return cached;
        }

        String letter = extractInitial(name);
        int fillColor = colorForUser(uid, name, borderColor);
        String letterKey = "letter|" + letter + "|" + fillColor + "|" + borderColor;
        BitmapDescriptor cachedLetter = iconCache.get(letterKey);
        if (cachedLetter != null) return cachedLetter;

        Bitmap bmp = createPinBitmap(context, null, letter, fillColor, borderColor);
        BitmapDescriptor desc = BitmapDescriptorFactory.fromBitmap(bmp);
        iconCache.put(letterKey, desc);
        return desc;
    }

    /**
     * Applies a profile photo marker (or initial-letter fallback) onto an existing Google Maps Marker.
     * If photoUrl is present and not yet cached, loads it via Glide and updates the marker icon seamlessly.
     */
    public static void bindProfileMarker(
            @NonNull Context context,
            @Nullable Marker marker,
            @Nullable String uid,
            @Nullable String name,
            @Nullable String photoUrl,
            int borderColor) {

        if (marker == null) return;

        // 1. Set instant icon (cached photo or crisp letter avatar)
        BitmapDescriptor immediate = getInitialOrCachedDescriptor(context, uid, name, photoUrl, borderColor);
        try {
            marker.setIcon(immediate);
            marker.setAnchor(0.5f, 1.0f);
        } catch (Exception ignored) {
            return;
        }

        // 2. If user has a profile photo URL and it's not cached yet, fetch asynchronously
        String cleanUrl = photoUrl == null ? "" : photoUrl.trim();
        if (cleanUrl.isEmpty()) return;

        String photoKey = "photo|" + cleanUrl + "|" + borderColor;
        if (iconCache.containsKey(photoKey)) return;

        final Context appCtx = context.getApplicationContext();
        final WeakReference<Marker> markerRef = new WeakReference<>(marker);
        final float density = appCtx.getResources().getDisplayMetrics().density;
        final int photoPx = Math.max(64, Math.round(40 * density));

        try {
            Glide.with(appCtx)
                    .asBitmap()
                    .load(cleanUrl)
                    .circleCrop()
                    .into(new CustomTarget<Bitmap>(photoPx, photoPx) {
                        @Override
                        public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
                            try {
                                Bitmap pinBmp = createPinBitmap(appCtx, resource, extractInitial(name),
                                        colorForUser(uid, name, borderColor), borderColor);
                                BitmapDescriptor desc = BitmapDescriptorFactory.fromBitmap(pinBmp);
                                iconCache.put(photoKey, desc);

                                Marker m = markerRef.get();
                                if (m != null) {
                                    m.setIcon(desc);
                                    m.setAnchor(0.5f, 1.0f);
                                }
                            } catch (Exception ignored) {
                                // Marker might have been cleared/removed by map refresh
                            }
                        }

                        @Override
                        public void onLoadCleared(@Nullable Drawable placeholder) {}
                    });
        } catch (Exception ignored) {}
    }

    /**
     * Draws a crisp circular pin marker with colored outer rim, white inner ring, bottom pointer tip,
     * and either the user's circular profile photo or bold initial letter.
     */
    private static Bitmap createPinBitmap(
            @NonNull Context context,
            @Nullable Bitmap photo,
            @NonNull String letter,
            int fillColor,
            int borderColor) {

        float d = context.getResources().getDisplayMetrics().density;
        int circleDiameter = Math.round(44 * d);
        int pointerHeight  = Math.round(10 * d);
        int shadowPad      = Math.round(4 * d);

        int width  = circleDiameter + shadowPad * 2;
        int height = circleDiameter + pointerHeight + shadowPad * 2;

        Bitmap out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);

        float cx = width / 2f;
        float cy = shadowPad + circleDiameter / 2f;
        float outerRadius = circleDiameter / 2f;

        // 1. Soft drop shadow underneath tip
        Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        shadowPaint.setColor(0x33000000);
        canvas.drawOval(cx - 8 * d, height - shadowPad - 2 * d, cx + 8 * d, height - shadowPad + 2 * d, shadowPaint);

        // 2. Bottom pointer triangle (colored with borderColor)
        Paint pinPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        pinPaint.setStyle(Paint.Style.FILL);
        pinPaint.setColor(borderColor);

        Path pointer = new Path();
        float tipY = height - shadowPad;
        pointer.moveTo(cx - 9 * d, cy + outerRadius * 0.65f);
        pointer.lineTo(cx + 9 * d, cy + outerRadius * 0.65f);
        pointer.lineTo(cx, tipY);
        pointer.close();
        canvas.drawPath(pointer, pinPaint);

        // 3. Outer colored circle ring
        canvas.drawCircle(cx, cy, outerRadius, pinPaint);

        // 4. Crisp white inner border ring
        float whiteRadius = outerRadius - 3.0f * d;
        Paint whitePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        whitePaint.setColor(Color.WHITE);
        canvas.drawCircle(cx, cy, whiteRadius, whitePaint);

        // 5. Inner avatar content (Profile Photo OR Colored Circle + Initial Letter)
        float contentRadius = whiteRadius - 2.0f * d;
        if (photo != null && !photo.isRecycled()) {
            int targetSize = Math.max(1, Math.round(contentRadius * 2));
            Bitmap scaled = Bitmap.createScaledBitmap(photo, targetSize, targetSize, true);
            Paint photoPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            BitmapShader shader = new BitmapShader(scaled, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            android.graphics.Matrix matrix = new android.graphics.Matrix();
            matrix.setTranslate(cx - contentRadius, cy - contentRadius);
            shader.setLocalMatrix(matrix);
            photoPaint.setShader(shader);
            canvas.drawCircle(cx, cy, contentRadius, photoPaint);
        } else {
            Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            fillPaint.setColor(fillColor);
            canvas.drawCircle(cx, cy, contentRadius, fillPaint);

            Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            textPaint.setColor(Color.WHITE);
            textPaint.setTextSize(18 * d);
            textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            textPaint.setTextAlign(Paint.Align.CENTER);

            Rect bounds = new Rect();
            textPaint.getTextBounds(letter, 0, letter.length(), bounds);
            float textY = cy - bounds.exactCenterY();
            canvas.drawText(letter, cx, textY, textPaint);
        }

        return out;
    }
}
