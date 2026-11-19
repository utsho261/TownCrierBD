package com.example.towncrierbd.utils;

/** Compare two seller positions against the same current viewer location. */
public final class SellerMovement {
    public static int direction(double viewerLat,double viewerLng,double previousLat,double previousLng,
                                double currentLat,double currentLng,long previousTime,long currentTime,long now) {
        if(previousTime<=0 || currentTime<=previousTime || currentTime-previousTime>120000 || now-currentTime>120000)return 2;
        double before=DistanceUtil.distanceKm(viewerLat,viewerLng,previousLat,previousLng);
        double after=DistanceUtil.distanceKm(viewerLat,viewerLng,currentLat,currentLng);
        double change=after-before;
        if(Math.abs(change)<0.01)return 0;
        return change<0?-1:1;
    }
}
