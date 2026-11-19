package com.example.towncrierbd.utils;
import androidx.annotation.NonNull;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;
import java.util.*;

public final class PostInteractions implements AutoCloseable {
    public final Set<String> ids=new HashSet<>();
    private DatabaseReference ref;
    private ValueEventListener listener;
    public PostInteractions(Runnable changed) {
        String uid=FirebaseAuth.getInstance().getUid(); if(uid==null)return;
        ref=FirebaseDatabase.getInstance().getReference("postInteractions").child(uid);
        listener=new ValueEventListener(){
            @Override public void onDataChange(@NonNull DataSnapshot data){ids.clear();for(DataSnapshot child:data.getChildren())ids.add(child.getKey());changed.run();}
            @Override public void onCancelled(@NonNull DatabaseError e){}
        };
        ref.addValueEventListener(listener);
    }
    public void close(){if(ref!=null && listener!=null)ref.removeEventListener(listener);}
}
