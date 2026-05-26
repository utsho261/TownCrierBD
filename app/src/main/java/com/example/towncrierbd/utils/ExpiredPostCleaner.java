package com.example.towncrierbd.utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.towncrierbd.models.Announcement;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class ExpiredPostCleaner {

    /**
     * ✅ FIX: Only delete posts owned by the current user (myUid).
     *
     * OLD behaviour: every logged-in client deleted ALL expired posts on load.
     * With N users online, the same post got N simultaneous delete calls — redundant
     * writes, Firebase quota waste, and potential race conditions.
     *
     * NEW behaviour: each client only cleans up their OWN expired posts.
     * This is safe, idempotent, and scales correctly.
     *
     * Server-side cleanup (Firebase Functions) can still handle posts from users
     * who are offline, but that is optional — posts are already filtered client-side
     * by expireAt so they never appear in any feed even without deletion.
     *
     * @param myUid The UID of the currently logged-in user. Pass null to skip cleanup.
     */
    public static void cleanExpired(@Nullable String myUid) {
        if (myUid == null || myUid.isEmpty()) return;

        DatabaseReference annRef = FirebaseDatabase.getInstance()
                .getReference(Constants.DB_ANNOUNCEMENTS);

        long now = System.currentTimeMillis();

        // ✅ Query only this user's posts — avoids downloading the entire announcements node
        annRef.orderByChild("userId").equalTo(myUid)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        for (DataSnapshot s : snapshot.getChildren()) {
                            Announcement a = s.getValue(Announcement.class);
                            if (a == null) continue;

                            // Only delete if expired and belongs to this user (double-check)
                            if (a.getExpireAt() > 0
                                    && now > a.getExpireAt()
                                    && myUid.equals(a.getUserId())) {
                                s.getRef().removeValue();
                            }
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        // Ignore — cleanup is best-effort
                    }
                });
    }
}