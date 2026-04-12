package com.example.towncrierbd.utils;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;

import java.io.ByteArrayOutputStream;

public class ImageBase64Util {

    // reduce size so Firebase DB not heavy
    public static String bitmapToBase64(Bitmap bmp) {
        if (bmp == null) return "";
        try {
            Bitmap scaled = scaleDown(bmp, 900); // max width/height 900px
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            scaled.compress(Bitmap.CompressFormat.JPEG, 70, baos); // quality 70
            byte[] bytes = baos.toByteArray();
            return Base64.encodeToString(bytes, Base64.NO_WRAP);
        } catch (Exception e) {
            return "";
        }
    }

    public static Bitmap base64ToBitmap(String b64) {
        try {
            if (b64 == null) return null;
            b64 = b64.trim();
            if (b64.isEmpty()) return null;
            byte[] bytes = Base64.decode(b64, Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        } catch (Exception e) {
            return null;
        }
    }

    private static Bitmap scaleDown(Bitmap bmp, int maxSize) {
        int w = bmp.getWidth();
        int h = bmp.getHeight();
        if (w <= maxSize && h <= maxSize) return bmp;

        float ratio = Math.min((float) maxSize / w, (float) maxSize / h);
        int nw = Math.round(w * ratio);
        int nh = Math.round(h * ratio);
        return Bitmap.createScaledBitmap(bmp, nw, nh, true);
    }
}
