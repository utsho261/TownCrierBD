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
     * Call this when Feed opens.
     * Scans all announcements, deletes expired ones + their audio from Storage.
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
                        String id = a.getId() != null ? a.getId() : s.getKey();

                        // 1. Delete audio from Firebase Storage (if exists)
                        if (a.getAudioUrl() != null && !a.getAudioUrl().isEmpty()) {
                            AudioRecorderHelper.deleteAudio(id);
                        }

                        // 2. Delete post from Realtime DB
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