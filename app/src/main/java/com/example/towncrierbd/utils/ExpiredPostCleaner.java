package com.example.towncrierbd.utils;

import androidx.annotation.NonNull;

import com.example.towncrierbd.models.Announcement;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class ExpiredPostCleaner {

    /**
     * Feed open হলে call করো।
     * Expired announcements Firebase DB থেকে delete করো।
     *
     * NOTE: Audio এখন Cloudinary-তে আছে।
     * Cloudinary-তে client-side delete safe না (API secret লাগে)।
     * তাই audio file Cloudinary-তে থাকবে, শুধু DB entry মুছবে।
     * Server-side cleanup করতে চাইলে Firebase Functions ব্যবহার করো।
     */
    public static void cleanExpired() {
        DatabaseReference annRef = FirebaseDatabase.getInstance()
                .getReference(Constants.DB_ANNOUNCEMENTS);

        long now = System.currentTimeMillis();

        annRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                for (DataSnapshot s : snapshot.getChildren()) {
                    Announcement a = s.getValue(Announcement.class);
                    if (a == null) continue;

                    if (a.getExpireAt() > 0 && now > a.getExpireAt()) {
                        // ✅ শুধু Firebase DB থেকে delete করো
                        // Cloudinary audio server-side cleanup করতে হবে
                        s.getRef().removeValue();
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // ignore
            }
        });
    }
}