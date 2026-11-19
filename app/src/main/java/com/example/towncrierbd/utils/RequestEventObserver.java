package com.example.towncrierbd.utils;
import android.app.*;
import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.NonNull;
import com.example.towncrierbd.activities.RequestNotificationActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;
import java.util.*;
/** Durable in-app lifecycle notifications; shows Accept/Reject prompt when announcer enters the app. */
public final class RequestEventObserver implements Application.ActivityLifecycleCallbacks {
    private Query query;
    private ValueEventListener listener;
    private Activity foreground;
    private AlertDialog dialog;
    private boolean resolving;
    private int generation;
    private final Set<String> shown=new HashSet<>();
    @Override public void onActivityResumed(Activity activity){
        detach();resolving=false;final int token=++generation;foreground=activity;String uid=FirebaseAuth.getInstance().getUid();if(uid==null){shown.clear();return;}
        query=FirebaseDatabase.getInstance().getReference("requestNotifications").child(uid).orderByChild("read").equalTo(false).limitToFirst(30);
        listener=new ValueEventListener(){@Override public void onDataChange(@NonNull DataSnapshot rows){
            if(foreground!=activity||activity.isFinishing()||activity.isDestroyed()||resolving||(dialog!=null&&dialog.isShowing()))return;
            for(DataSnapshot row:rows.getChildren()){
                String event=uid+":"+row.getKey(),action=row.child("action").getValue(String.class);
                if("chat".equals(action)||"announcement".equals(action))continue;
                if(shown.contains(event))continue;
                String id=row.child("annId").getValue(String.class);if(id==null||id.isEmpty())continue;
                if("delivered".equals(action)) {
                    resolving=true;
                    FirebaseDatabase.getInstance().getReference(Constants.DB_ANNOUNCEMENTS).child(id).get().addOnSuccessListener(postRow->{
                        if(token!=generation)return;resolving=false;
                        if(foreground!=activity||activity.isFinishing()||activity.isDestroyed()){shown.remove(event);return;}
                        com.example.towncrierbd.models.Announcement post=postRow.getValue(com.example.towncrierbd.models.Announcement.class);
                        if(post==null||!uid.equals(post.getUserId())||!"delivered".equals(post.getStatus())||!PostWorkflow.live(post)){row.getRef().child("read").setValue(true);return;}
                        post.setId(id);shown.add(event);
                        dialog=new AlertDialog.Builder(activity).setTitle(PostCatalog.text(activity,"Delivery confirmation","পণ্য গ্রহণ নিশ্চিত করুন")).setMessage(post.getTitle())
                            .setPositiveButton(PostCatalog.text(activity,"Received","পেয়েছি"),(d,w)->PostWorkflow.delivery(activity,post,"received",null))
                            .setNegativeButton(PostCatalog.text(activity,"Not received","পাইনি"),(d,w)->PostWorkflow.updateDelivery(activity,post,"notReceived",null))
                            .setNeutralButton(PostCatalog.text(activity,"Later","পরে"),null).create();
                        dialog.setOnDismissListener(d->{dialog=null;row.getRef().child("read").setValue(true);});dialog.show();
                    }).addOnFailureListener(e->{if(token==generation)resolving=false;});
                    break;
                }
                if("open".equals(action)) {
                    resolving=true;
                    FirebaseDatabase.getInstance().getReference(Constants.DB_ANNOUNCEMENTS).child(id).get().addOnSuccessListener(postRow->{
                        if(token!=generation)return;resolving=false;
                        if(foreground!=activity||activity.isFinishing()||activity.isDestroyed()){shown.remove(event);return;}
                        com.example.towncrierbd.models.Announcement post=postRow.getValue(com.example.towncrierbd.models.Announcement.class);
                        long now=System.currentTimeMillis();
                        if(post==null||!RequestLifecycle.live(post,now)||"delivered".equals(post.getStatus())){row.getRef().child("read").setValue(true);return;}
                        post.setId(id);shown.add(event);
                        dialog=AppNotificationManager.showIncomingRequestPrompt(activity,post,uid);
                        if(dialog!=null){
                            dialog.setOnDismissListener(d->{dialog=null;row.getRef().child("read").setValue(true);});
                        } else {
                            row.getRef().child("read").setValue(true);
                        }
                    }).addOnFailureListener(e->{if(token==generation)resolving=false;});
                    break;
                }
                shown.add(event);
                dialog=new AlertDialog.Builder(activity).setTitle(row.child("title").getValue(String.class)).setMessage(row.child("body").getValue(String.class))
                    .setPositiveButton(PostCatalog.text(activity,"View request","রিকোয়েস্ট দেখুন"),(d,w)->{Intent i=new Intent(activity,RequestNotificationActivity.class);i.putExtra("announcementId",id);i.putExtra("recipientUid",uid);activity.startActivity(i);})
                    .setNegativeButton(PostCatalog.text(activity,"Close","বন্ধ করুন"),null).create();
                dialog.setOnDismissListener(d->{dialog=null;row.getRef().child("read").setValue(true);});dialog.show();break;
            }
        }@Override public void onCancelled(@NonNull DatabaseError error){}};
        query.addValueEventListener(listener);
    }
    private void detach(){if(query!=null&&listener!=null)query.removeEventListener(listener);query=null;listener=null;}
    @Override public void onActivityPaused(Activity a){if(foreground==a){foreground=null;++generation;resolving=false;detach();if(dialog!=null){dialog.dismiss();dialog=null;}}}
    public void onActivityCreated(Activity a,Bundle b){}public void onActivityStarted(Activity a){}public void onActivityStopped(Activity a){}public void onActivitySaveInstanceState(Activity a,Bundle b){}public void onActivityDestroyed(Activity a){}
}
