package com.example.towncrierbd.activities;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.towncrierbd.R;
import com.example.towncrierbd.adapters.FeedAdapter;
import com.example.towncrierbd.models.Announcement;
import com.example.towncrierbd.models.UserModel;
import com.example.towncrierbd.utils.Constants;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;

import java.util.ArrayList;
import java.util.List;

public class ProfileActivity extends AppCompatActivity {

    private TextView tvName, tvRole, tvEmail, tvPhone;
    private Button btnLogout;

    private RecyclerView rvMyPosts;
    private FeedAdapter myPostsAdapter;

    private FirebaseAuth auth;
    private DatabaseReference userRef, annRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        tvName    = findViewById(R.id.tvName);
        tvRole    = findViewById(R.id.tvRole);
        tvEmail   = findViewById(R.id.tvEmail);
        tvPhone   = findViewById(R.id.tvPhone);
        btnLogout = findViewById(R.id.btnLogout);
        rvMyPosts = findViewById(R.id.rvMyPosts);

        // ✅ Profile mode: edit/delete দেখাবে, card click disable
        myPostsAdapter = new FeedAdapter(this);
        myPostsAdapter.setShowEditDelete(true);
        myPostsAdapter.setDisableCardClick(true);
        rvMyPosts.setLayoutManager(new LinearLayoutManager(this));
        rvMyPosts.setAdapter(myPostsAdapter);

        auth    = FirebaseAuth.getInstance();
        userRef = FirebaseDatabase.getInstance().getReference(Constants.DB_USERS);
        annRef  = FirebaseDatabase.getInstance().getReference(Constants.DB_ANNOUNCEMENTS);

        btnLogout.setOnClickListener(v -> {
            auth.signOut();
            Intent intent = new Intent(this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });

        loadProfile();
        loadMyPosts();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (myPostsAdapter != null) myPostsAdapter.release();
    }

    private void loadProfile() {
        String uid = auth.getUid();
        if (uid == null) return;

        userRef.child(uid).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                UserModel u = snapshot.getValue(UserModel.class);
                if (u == null) return;

                tvName.setText(u.getName());
                tvRole.setText(u.getRole());
                tvEmail.setText("Email: " + safe(u.getEmail()));
                tvPhone.setText("Phone: " + safe(u.getPhone()));

                myPostsAdapter.setMyLocation(u.getLat(), u.getLng());
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void loadMyPosts() {
        String uid = auth.getUid();
        if (uid == null) return;

        annRef.orderByChild("userId").equalTo(uid)
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        List<Announcement> list = new ArrayList<>();
                        for (DataSnapshot s : snapshot.getChildren()) {
                            Announcement a = s.getValue(Announcement.class);
                            if (a == null) continue;
                            if (a.getId() == null || a.getId().trim().isEmpty())
                                a.setId(s.getKey());
                            list.add(a);
                        }
                        myPostsAdapter.setData(list);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {}
                });
    }

    private String safe(String s) { return s == null ? "" : s.trim(); }
}