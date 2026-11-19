package com.example.towncrierbd.services;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.*;
import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import com.example.towncrierbd.activities.AnnouncerFeedActivity;
import com.example.towncrierbd.models.Announcement;
import com.example.towncrierbd.utils.*;
import com.google.android.gms.location.*;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;
import java.util.*;

/** Foreground location sharing lasts only while this seller has accepted/delivered requests. */
public class DeliveryLocationService extends Service {
    private static final String CHANNEL="delivery_location";
    private static boolean running;
    private String uid;
    private Query jobs;
    private ValueEventListener jobsListener;
    private FirebaseAuth.AuthStateListener authListener;
    private FusedLocationProviderClient locations;
    private LocationCallback callback;
    private boolean requesting;
    public static boolean isRunning(){return running;}
    public static boolean hasPermission(Context c){return ContextCompat.checkSelfPermission(c,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(c,Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED;}
    public static void startFrom(Activity a) {
        if(a.isFinishing()||a.isDestroyed()||!hasPermission(a))return;
        try{ContextCompat.startForegroundService(a,new Intent(a,DeliveryLocationService.class));}
        catch(RuntimeException e){android.widget.Toast.makeText(a,PostCatalog.text(a,"Open the feed to resume delivery location sharing.","ডেলিভারির লোকেশন চালু করতে Feed খুলুন।"),android.widget.Toast.LENGTH_LONG).show();}
    }
    public static void resumeAssigned(Activity a){
        String uid=FirebaseAuth.getInstance().getUid();if(uid==null||running||!hasPermission(a))return;
        FirebaseDatabase.getInstance().getReference(Constants.DB_ANNOUNCEMENTS).get().addOnSuccessListener(rows->{
            if(a.isFinishing()||a.isDestroyed()||!a.hasWindowFocus()||!uid.equals(FirebaseAuth.getInstance().getUid()))return;
            long now=System.currentTimeMillis();
            for(DataSnapshot row:rows.getChildren()){Announcement p=row.getValue(Announcement.class);if(p!=null&&RequestLifecycle.hasActiveJob(p,uid,now)){startFrom(a);break;}}
        });
    }
    @Override public void onCreate(){
        super.onCreate();running=true;
        uid=FirebaseAuth.getInstance().getUid();if(uid==null||!hasPermission(this)){stopSelf();return;}
        if(Build.VERSION.SDK_INT>=26)getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel(CHANNEL,"Delivery location",NotificationManager.IMPORTANCE_LOW));
        PendingIntent open=PendingIntent.getActivity(this,728,new Intent(this,AnnouncerFeedActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification notification=new NotificationCompat.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle(PostCatalog.text(this,"Sharing delivery location","ডেলিভারির লোকেশন শেয়ার হচ্ছে"))
            .setContentText(PostCatalog.text(this,"Stops when the request is received or cancelled","পণ্য গ্রহণ বা বাতিল হলে বন্ধ হবে")).setOngoing(true).setContentIntent(open).build();
        try {if(Build.VERSION.SDK_INT>=29)startForeground(728,notification,android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);else startForeground(728,notification);}catch(RuntimeException e){stopSelf();return;}
        uid=FirebaseAuth.getInstance().getUid();if(uid==null||!hasPermission(this)){stopSelf();return;}
        locations=LocationServices.getFusedLocationProviderClient(this);
        authListener=auth->{if(!uid.equals(auth.getUid()))stopSelf();};FirebaseAuth.getInstance().addAuthStateListener(authListener);
        jobs=FirebaseDatabase.getInstance().getReference(Constants.DB_ANNOUNCEMENTS);
        jobsListener=new ValueEventListener(){
            @Override public void onDataChange(@NonNull DataSnapshot rows){
                long now=System.currentTimeMillis();
                boolean active=false;for(DataSnapshot row:rows.getChildren()){Announcement p=row.getValue(Announcement.class);if(p!=null&&RequestLifecycle.hasActiveJob(p,uid,now)){active=true;break;}}
                if(active)requestLocation();else stopSelf();
            }
            @Override public void onCancelled(@NonNull DatabaseError error){stopSelf();}
        };jobs.addValueEventListener(jobsListener);
    }
    private void requestLocation(){
        if(requesting)return;if(!hasPermission(this)){stopSelf();return;}requesting=true;
        callback=new LocationCallback(){@Override public void onLocationResult(@NonNull LocationResult result){
            Location l=result.getLastLocation();if(l==null||!uid.equals(FirebaseAuth.getInstance().getUid()))return;
            if(SystemClock.elapsedRealtimeNanos()-l.getElapsedRealtimeNanos()>120000000000L)return;
            Map<String,Object> update=new HashMap<>();update.put("lat",l.getLatitude());update.put("lng",l.getLongitude());update.put("locationUpdatedAt",ServerValue.TIMESTAMP);update.put("locationAccuracy",l.hasAccuracy()?l.getAccuracy():0);
            FirebaseDatabase.getInstance().getReference(Constants.DB_USERS).child(uid).updateChildren(update);
        }};
        try{locations.requestLocationUpdates(new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY,10000).setMinUpdateIntervalMillis(5000).build(),callback,Looper.getMainLooper());}
        catch(SecurityException e){stopSelf();}
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){return START_NOT_STICKY;}
    @Override public IBinder onBind(Intent intent){return null;}
    @Override public void onDestroy(){
        running=false;if(jobs!=null&&jobsListener!=null)jobs.removeEventListener(jobsListener);
        if(locations!=null&&callback!=null)locations.removeLocationUpdates(callback);
        if(authListener!=null)FirebaseAuth.getInstance().removeAuthStateListener(authListener);
        stopForeground(true);super.onDestroy();
    }
}
