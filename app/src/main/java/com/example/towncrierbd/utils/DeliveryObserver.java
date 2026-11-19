package com.example.towncrierbd.utils;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import androidx.annotation.NonNull;
import com.example.towncrierbd.models.Announcement;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;
import java.util.HashSet;
import java.util.Set;

/** Show delivery receipts only in the foreground, once per delivery event per session. */
public final class DeliveryObserver implements Application.ActivityLifecycleCallbacks {
    private Query query;
    private ValueEventListener listener;
    private final Set<String> shown = new HashSet<>();
    private Activity foreground;
    public void onActivityResumed(Activity activity) {
        detach(); foreground=activity;
        String uid=FirebaseAuth.getInstance().getUid();if(uid==null){shown.clear();return;}
        query=FirebaseDatabase.getInstance().getReference(Constants.DB_ANNOUNCEMENTS).orderByChild("userId").equalTo(uid);
        listener=new ValueEventListener(){
            @Override public void onDataChange(@NonNull DataSnapshot data){
                for(DataSnapshot row:data.getChildren()){
                    Announcement a=row.getValue(Announcement.class);if(a==null || !"delivered".equals(a.getStatus()))continue;
                    a.setId(row.getKey());String event=uid+":"+a.getId()+":"+a.getDeliveredAt();
                    if(foreground==activity && !activity.isFinishing() && !activity.isDestroyed() && shown.add(event)){
                        PostWorkflow.receivedPopup(activity,a);break;
                    }
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error){}
        };
        query.addValueEventListener(listener);
    }
    private void detach(){if(query!=null && listener!=null)query.removeEventListener(listener);query=null;listener=null;}
    public void onActivityPaused(Activity activity){if(foreground==activity){foreground=null;detach();}}
    public void onActivityCreated(Activity a,Bundle b){}
    public void onActivityStarted(Activity a){}
    public void onActivityStopped(Activity a){}
    public void onActivitySaveInstanceState(Activity a,Bundle b){}
    public void onActivityDestroyed(Activity a){}
}
