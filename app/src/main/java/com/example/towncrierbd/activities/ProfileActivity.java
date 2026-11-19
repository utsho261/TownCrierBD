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
import com.example.towncrierbd.utils.AuthUtils;
import com.example.towncrierbd.utils.NotificationSettings;
import com.example.towncrierbd.utils.PostCatalog;
import com.example.towncrierbd.utils.Constants;
import com.example.towncrierbd.utils.LanguageManager;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;

import java.util.ArrayList;
import java.util.List;

public class ProfileActivity extends AppCompatActivity {

    private TextView tvName, tvRole, tvEmail, tvPhone, tvAvatarLarge;
    private Button btnLogout, btnEditProfile, btnEditCategories, btnNotificationSettings;
    private TextView tvLangToggle;
    private RecyclerView rvMyPosts;
    private FeedAdapter myPostsAdapter;

    private FirebaseAuth auth;
    private DatabaseReference userRef, annRef;
    private ValueEventListener postsListener;
    private Query postsQuery;
    private UserModel currentUser = null;
    private ImageView profilePhoto;
    private ActivityResultLauncher<String> photoPicker;
    private boolean uploadingPhoto;
    private String label(String en,String bn){return PostCatalog.text(this,en,bn);}

    private ActivityResultLauncher<Intent> categoryLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        ImageView btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }
        TextView tvProfileTitle = findViewById(R.id.tvProfileTitle);
        if (tvProfileTitle != null) {
            tvProfileTitle.setText(label("Profile", "প্রোফাইল"));
        }

        tvName            = findViewById(R.id.tvName);
        tvRole            = findViewById(R.id.tvRole);
        tvEmail           = findViewById(R.id.tvEmail);
        tvPhone           = findViewById(R.id.tvPhone);
        tvAvatarLarge     = findViewById(R.id.tvAvatarLarge);
        profilePhoto=findViewById(R.id.ivProfilePhoto);
        photoPicker=registerForActivityResult(new ActivityResultContracts.GetContent(),uri->{if(uri!=null)uploadPhoto(uri);});
        tvAvatarLarge.setContentDescription(label("Edit profile photo","প্রোফাইলের ছবি বদলান"));
        tvAvatarLarge.setOnClickListener(v->choosePhoto());profilePhoto.setOnClickListener(v->choosePhoto());
        btnLogout         = findViewById(R.id.btnLogout);
        btnNotificationSettings = findViewById(R.id.btnNotificationSettings);
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
                String uid = auth.getUid();
                if (uid != null) LanguageManager.saveToFirebase(this, uid);
                applyStrings();
                if (myPostsAdapter != null) myPostsAdapter.notifyDataSetChanged();
            });
        }

        btnLogout.setOnClickListener(v -> {
            AppStrings s = AppStrings.get(this);
            new AlertDialog.Builder(this)
                    .setTitle(s.profileLogoutConfirmTitle())
                    .setMessage(s.profileLogoutConfirmMsg())
                    .setPositiveButton(s.profileLogout(), (dialog, which) -> AuthUtils.logout(this))
                    .setNegativeButton(s.cancel(), null)
                    .show();
        });

        if (btnEditProfile != null)
            btnEditProfile.setOnClickListener(v -> showEditProfileDialog());

        if (btnEditCategories != null)
            btnEditCategories.setOnClickListener(v -> openCategoryEditor());

        if (btnNotificationSettings != null) {
            btnNotificationSettings.setVisibility(android.view.View.GONE);
            btnNotificationSettings.setOnClickListener(v -> {
                if (currentUser != null) NotificationSettings.show(this, currentUser);
                else Toast.makeText(this, AppStrings.get(this).pleaseWait(), Toast.LENGTH_SHORT).show();
            });
        }
        loadProfile();
        loadMyPosts();
    }

    private void applyStrings() {
        AppStrings s = AppStrings.get(this);
        if (btnEditProfile    != null) btnEditProfile.setText("EDIT");
        if (btnLogout         != null) btnLogout.setText("LogOut");
        if (btnEditCategories != null) btnEditCategories.setText(s.profileEditCategories());
        if (tvLangToggle      != null) tvLangToggle.setText(LanguageManager.getToggleLabel(this));

        btnNotificationSettings.setText(PostCatalog.text(this, "Notification settings", "নোটিফিকেশন সেটিংস"));

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
                com.example.towncrierbd.utils.PostWorkflow.setViewerRole(u.getRole());
                displayPhoto();

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

                boolean isAnnouncer = Constants.ROLE_ANNOUNCER.equals(u.getRole());
                if (btnEditCategories != null) {
                    btnEditCategories.setVisibility(
                            isAnnouncer ? android.view.View.VISIBLE : android.view.View.GONE);
                }
                if (btnNotificationSettings != null) {
                    btnNotificationSettings.setVisibility(
                            isAnnouncer ? android.view.View.GONE : android.view.View.VISIBLE);
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
                    if (com.example.towncrierbd.utils.RequestLifecycle.request(a)) {
                        com.example.towncrierbd.utils.RequestBidManager.syncExpiredBidsIfNeeded(a, now);
                    }
                    if (a.isActive() && !com.example.towncrierbd.utils.RequestLifecycle.live(a, now)) {
                        a.setActive(false);
                        s.getRef().child("active").setValue(false);
                    }
                    list.add(a);
                }
                list.sort((a,b)->com.example.towncrierbd.utils.RequestLifecycle.compareProfile(a,b,now));
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

        Button changePhoto=new Button(this);changePhoto.setText(label("Change profile photo","প্রোফাইলের ছবি বদলান"));changePhoto.setOnClickListener(v->choosePhoto());layout.addView(changePhoto);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(s.profileEditDialogTitle()).setView(layout)
            .setPositiveButton(s.save(),null).setNegativeButton(s.cancel(),null).create();
        dialog.setOnShowListener(v->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button->{
            String name=com.example.towncrierbd.utils.ProfileValidation.name(etName.getText().toString()),phone=com.example.towncrierbd.utils.ProfileValidation.phone(etPhone.getText().toString());
            if(!com.example.towncrierbd.utils.ProfileValidation.validName(name)){etName.setError(label("Use letters and valid name punctuation (2–80 characters)","সঠিক নাম লিখুন: অক্ষর, স্পেস, ডট, অ্যাপস্ট্রফি বা হাইফেন (২–৮০ অক্ষর)"));return;}
            if(!com.example.towncrierbd.utils.ProfileValidation.validPhone(phone)){etPhone.setError(label("Enter a valid 11-digit mobile number","সঠিক ১১ সংখ্যার মোবাইল নম্বর লিখুন"));return;}
            String uid=auth.getUid();if(uid==null)return;
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            java.util.Map<String,Object> updates=new java.util.HashMap<>();updates.put("name",name);updates.put("phone",phone);
            userRef.child(uid).updateChildren(updates).addOnSuccessListener(result->{
                currentUser.setName(name);currentUser.setPhone(phone);tvName.setText(name);tvPhone.setText(s.profilePhonePrefix()+phone);tvAvatarLarge.setText(name.substring(0,1).toUpperCase());dialog.dismiss();Toast.makeText(this,s.profileUpdated(),Toast.LENGTH_SHORT).show();
            }).addOnFailureListener(e->{dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);Toast.makeText(this,label("Profile was not saved. Retry.","প্রোফাইল সেভ হয়নি। আবার চেষ্টা করুন।"),Toast.LENGTH_LONG).show();});
        }));dialog.show();
    }
    private void choosePhoto() {
        if(currentUser==null||uploadingPhoto)return;
        String[] options=currentUser.getPhotoUrl().isEmpty()?new String[]{label("Choose photo","ছবি বাছুন")}:new String[]{label("Choose photo","ছবি বাছুন"),label("Remove photo","ছবি সরান")};
        new AlertDialog.Builder(this).setTitle(label("Profile photo","প্রোফাইলের ছবি")).setItems(options,(d,which)->{if(which==0)photoPicker.launch("image/*");else savePhoto("");}).show();
    }
    private void displayPhoto() {
        if (currentUser == null || isFinishing() || isDestroyed()) return;
        String photoUrl = currentUser.getPhotoUrl();
        boolean present = photoUrl != null && !photoUrl.trim().isEmpty();
        if(present) {
            tvAvatarLarge.setVisibility(android.view.View.GONE);
            profilePhoto.setVisibility(android.view.View.VISIBLE);
            com.bumptech.glide.Glide.with(this)
                    .load(photoUrl)
                    .circleCrop()
                    .error(android.R.drawable.ic_menu_camera)
                    .into(profilePhoto);
        } else {
            profilePhoto.setVisibility(android.view.View.GONE);
            tvAvatarLarge.setVisibility(android.view.View.VISIBLE);
            com.bumptech.glide.Glide.with(this).clear(profilePhoto);
        }
    }
    private void uploadPhoto(android.net.Uri uri) {
        if(uploadingPhoto)return;uploadingPhoto=true;String uploadUid=auth.getUid();
        Toast.makeText(this,label("Uploading photo…","ছবি আপলোড হচ্ছে…"),Toast.LENGTH_SHORT).show();
        new Thread(()->{
            try{
                android.graphics.Bitmap bitmap=com.example.towncrierbd.utils.ImageImporter.decode(this,uri);
                com.example.towncrierbd.utils.CloudinaryUploader.uploadBitmap(this,bitmap,new com.example.towncrierbd.utils.CloudinaryUploader.UploadListener(){
                    @Override public void onSuccess(String url){runOnUiThread(()->{if(uploadUid!=null&&uploadUid.equals(auth.getUid()))savePhoto(url);else uploadingPhoto=false;});}
                    @Override public void onError(String message){runOnUiThread(()->photoError());}
                });
            }catch(Exception e){runOnUiThread(()->photoError());}
        },"profile-photo").start();
    }
    private void savePhoto(String url) {
        String uid=auth.getUid();if(uid==null){uploadingPhoto=false;return;}
        uploadingPhoto=true;
        userRef.child(uid).child("photoUrl").setValue(url).addOnSuccessListener(v->{uploadingPhoto=false;if(isDestroyed()||isFinishing())return;currentUser.setPhotoUrl(url);displayPhoto();})
            .addOnFailureListener(e->photoError());
    }
    private void photoError(){uploadingPhoto=false;if(!isDestroyed()&&!isFinishing())Toast.makeText(this,label("Photo was not saved. Try again.","ছবি সেভ হয়নি। আবার চেষ্টা করুন।"),Toast.LENGTH_LONG).show();}

    private String safe(String s) { return s == null ? "" : s.trim(); }
}