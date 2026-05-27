package com.example.towncrierbd.activities;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.widget.*;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.towncrierbd.R;
import com.example.towncrierbd.adapters.FeedAdapter;
import com.example.towncrierbd.models.Announcement;
import com.example.towncrierbd.models.UserModel;
import com.example.towncrierbd.utils.AppStrings;
import com.example.towncrierbd.utils.Constants;
import com.example.towncrierbd.utils.LanguageManager;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;

import java.util.ArrayList;
import java.util.List;

public class ProfileActivity extends AppCompatActivity {

    private TextView tvName, tvRole, tvEmail, tvPhone, tvAvatarLarge;
    private Button btnLogout, btnEditProfile, btnEditCategories;
    private TextView tvLangToggle;
    private RecyclerView rvMyPosts;
    private FeedAdapter myPostsAdapter;

    private FirebaseAuth auth;
    private DatabaseReference userRef, annRef;
    private ValueEventListener postsListener;
    private Query postsQuery;
    private UserModel currentUser = null;

    private ActivityResultLauncher<Intent> categoryLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        tvName            = findViewById(R.id.tvName);
        tvRole            = findViewById(R.id.tvRole);
        tvEmail           = findViewById(R.id.tvEmail);
        tvPhone           = findViewById(R.id.tvPhone);
        tvAvatarLarge     = findViewById(R.id.tvAvatarLarge);
        btnLogout         = findViewById(R.id.btnLogout);
        btnEditProfile    = findViewById(R.id.btnEditProfile);
        btnEditCategories = findViewById(R.id.btnEditCategories);
        tvLangToggle      = findViewById(R.id.tvLangToggle);
        rvMyPosts         = findViewById(R.id.rvMyPosts);

        myPostsAdapter = new FeedAdapter(this);
        myPostsAdapter.setShowEditDelete(true);
        myPostsAdapter.setDisableCardClick(true);
        rvMyPosts.setLayoutManager(new LinearLayoutManager(this));
        rvMyPosts.setAdapter(myPostsAdapter);

        auth    = FirebaseAuth.getInstance();
        userRef = FirebaseDatabase.getInstance().getReference(Constants.DB_USERS);
        annRef  = FirebaseDatabase.getInstance().getReference(Constants.DB_ANNOUNCEMENTS);

        categoryLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        ArrayList<String> cats = result.getData()
                                .getStringArrayListExtra(HawkerCategoryActivity.EXTRA_CATEGORIES);
                        ArrayList<String> subs = result.getData()
                                .getStringArrayListExtra(HawkerCategoryActivity.EXTRA_SUBCATEGORIES);
                        String othersName = result.getData()
                                .getStringExtra(HawkerCategoryActivity.EXTRA_OTHERS_NAME);
                        saveUpdatedCategories(cats, subs, othersName);
                    }
                });

        applyStrings();

        // Language toggle
        if (tvLangToggle != null) {
            tvLangToggle.setOnClickListener(v -> {
                LanguageManager.toggle(this);
                applyStrings();
                // Refresh adapter
                if (myPostsAdapter != null) myPostsAdapter.notifyDataSetChanged();
            });
        }

        btnLogout.setOnClickListener(v -> {
            auth.signOut();
            Intent intent = new Intent(this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });

        if (btnEditProfile != null)
            btnEditProfile.setOnClickListener(v -> showEditProfileDialog());

        if (btnEditCategories != null)
            btnEditCategories.setOnClickListener(v -> openCategoryEditor());

        loadProfile();
        loadMyPosts();
    }

    private void applyStrings() {
        AppStrings s = AppStrings.get(this);
        if (btnEditProfile    != null) btnEditProfile.setText(s.profileEditBtn());
        if (btnLogout         != null) btnLogout.setText(s.profileLogout());
        if (btnEditCategories != null) btnEditCategories.setText(s.profileEditCategories());
        if (tvLangToggle      != null) tvLangToggle.setText(LanguageManager.getToggleLabel(this));

        // Refresh contact info if user already loaded
        if (currentUser != null) {
            tvEmail.setText(s.profileEmailPrefix() + safe(currentUser.getEmail()));
            tvPhone.setText(s.profilePhonePrefix() + safe(currentUser.getPhone()));

            // Role display
            String role = safe(currentUser.getRole());
            if (Constants.ROLE_ANNOUNCER.equals(role)) {
                tvRole.setText(LanguageManager.isEnglish(this) ? "ANNOUNCER" : "বিক্রেতা/ঘোষক");
            } else {
                tvRole.setText(LanguageManager.isEnglish(this) ? "General User" : "সাধারণ ব্যবহারকারী");
            }
        }

        TextView tvMyPostsTitle = findViewById(R.id.tvMyPostsTitle);
        if (tvMyPostsTitle != null) tvMyPostsTitle.setText(s.profileMyPosts());
    }

    private void openCategoryEditor() {
        Intent intent = new Intent(this, HawkerCategoryActivity.class);
        if (currentUser != null) {
            intent.putStringArrayListExtra(
                    HawkerCategoryActivity.EXTRA_CATEGORIES,
                    new ArrayList<>(currentUser.getHawkerCategories()));
            intent.putStringArrayListExtra(
                    HawkerCategoryActivity.EXTRA_SUBCATEGORIES,
                    new ArrayList<>(currentUser.getHawkerSubcategories()));
            intent.putExtra(
                    HawkerCategoryActivity.EXTRA_OTHERS_NAME,
                    currentUser.getHawkerOthersName());
        }
        categoryLauncher.launch(intent);
    }

    private void saveUpdatedCategories(ArrayList<String> cats,
                                       ArrayList<String> subs,
                                       String othersName) {
        String uid = auth.getUid();
        if (uid == null) return;

        if (cats != null) {
            userRef.child(uid).child("hawkerCategories").setValue(cats);
            if (currentUser != null) currentUser.setHawkerCategories(cats);
        }
        if (subs != null) {
            userRef.child(uid).child("hawkerSubcategories").setValue(subs);
            if (currentUser != null) currentUser.setHawkerSubcategories(subs);
        }
        if (othersName != null) {
            userRef.child(uid).child("hawkerOthersName").setValue(othersName);
            if (currentUser != null) currentUser.setHawkerOthersName(othersName);
        }

        Toast.makeText(this, AppStrings.get(this).profileCatsUpdated(), Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (postsListener != null && postsQuery != null)
            postsQuery.removeEventListener(postsListener);
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
                currentUser = u;

                AppStrings s = AppStrings.get(ProfileActivity.this);
                String name = safe(u.getName());
                tvName.setText(name);

                String role = safe(u.getRole());
                if (Constants.ROLE_ANNOUNCER.equals(role)) {
                    tvRole.setText(LanguageManager.isEnglish(ProfileActivity.this) ? "ANNOUNCER" : "বিক্রেতা/ঘোষক");
                } else {
                    tvRole.setText(LanguageManager.isEnglish(ProfileActivity.this) ? "General User" : "সাধারণ ব্যবহারকারী");
                }

                tvEmail.setText(s.profileEmailPrefix() + safe(u.getEmail()));
                tvPhone.setText(s.profilePhonePrefix() + safe(u.getPhone()));

                if (tvAvatarLarge != null && !name.isEmpty())
                    tvAvatarLarge.setText(String.valueOf(Character.toUpperCase(name.charAt(0))));

                myPostsAdapter.setMyLocation(u.getLat(), u.getLng());

                if (btnEditCategories != null) {
                    boolean isAnnouncer = Constants.ROLE_ANNOUNCER.equals(u.getRole());
                    btnEditCategories.setVisibility(
                            isAnnouncer ? android.view.View.VISIBLE : android.view.View.GONE);
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void loadMyPosts() {
        String uid = auth.getUid();
        if (uid == null) return;

        postsListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<Announcement> list = new ArrayList<>();
                long now = System.currentTimeMillis();
                for (DataSnapshot s : snapshot.getChildren()) {
                    Announcement a = s.getValue(Announcement.class);
                    if (a == null) continue;
                    if (a.getId() == null || a.getId().trim().isEmpty())
                        a.setId(s.getKey());
                    if (a.getExpireAt() > 0 && now > a.getExpireAt()) continue;
                    list.add(a);
                }
                myPostsAdapter.setData(list);
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };

        postsQuery = annRef.orderByChild("userId").equalTo(uid);
        postsQuery.addValueEventListener(postsListener);
    }

    private void showEditProfileDialog() {
        AppStrings s = AppStrings.get(this);

        if (currentUser == null) {
            Toast.makeText(this, s.profileNotLoaded(), Toast.LENGTH_SHORT).show();
            return;
        }

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 16, 48, 0);

        TextView labelName = new TextView(this);
        labelName.setText(s.profileLabelName());
        labelName.setTextSize(14);
        EditText etName = new EditText(this);
        etName.setText(currentUser.getName());

        TextView labelPhone = new TextView(this);
        labelPhone.setText(s.profileLabelPhone());
        labelPhone.setTextSize(14);
        labelPhone.setPadding(0, 16, 0, 0);
        EditText etPhone = new EditText(this);
        etPhone.setText(currentUser.getPhone());
        etPhone.setInputType(android.text.InputType.TYPE_CLASS_PHONE);

        layout.addView(labelName);
        layout.addView(etName);
        layout.addView(labelPhone);
        layout.addView(etPhone);

        new AlertDialog.Builder(this)
                .setTitle(s.profileEditDialogTitle())
                .setView(layout)
                .setPositiveButton(s.save(), (d, w) -> {
                    String newName  = etName.getText().toString().trim();
                    String newPhone = etPhone.getText().toString().trim();
                    if (newName.isEmpty()) {
                        Toast.makeText(this, s.profileNameEmpty(), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    String uid = auth.getUid();
                    if (uid == null) return;
                    userRef.child(uid).child("name").setValue(newName);
                    userRef.child(uid).child("phone").setValue(newPhone);
                    currentUser.setName(newName);
                    currentUser.setPhone(newPhone);
                    tvName.setText(newName);
                    tvPhone.setText(s.profilePhonePrefix() + newPhone);
                    if (tvAvatarLarge != null && !newName.isEmpty())
                        tvAvatarLarge.setText(String.valueOf(Character.toUpperCase(newName.charAt(0))));
                    Toast.makeText(this, s.profileUpdated(), Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(s.cancel(), null)
                .show();
    }

    private String safe(String s) { return s == null ? "" : s.trim(); }
}