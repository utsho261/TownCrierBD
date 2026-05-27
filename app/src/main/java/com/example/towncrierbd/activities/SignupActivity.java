package com.example.towncrierbd.activities;

import android.Manifest;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Bundle;
import android.os.Looper;
import android.widget.*;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.towncrierbd.R;
import com.example.towncrierbd.models.UserModel;
import com.example.towncrierbd.utils.AppStrings;
import com.example.towncrierbd.utils.Constants;
import com.example.towncrierbd.utils.LanguageManager;
import com.google.android.gms.location.*;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class SignupActivity extends AppCompatActivity {

    private EditText etName, etPhone, etEmail, etPassword, etDob;
    private RadioButton rbGeneral, rbAnnouncer;
    private Button btnSignup;
    private TextView tvGotoLogin, tvLangToggle, tvIAm, tvAppName, tvSubtitle;

    private FirebaseAuth auth;

    private static final int LOCATION_REQ = 101;
    private FusedLocationProviderClient locationClient;
    private LocationCallback locationCallback;

    private double userLat = 0.0;
    private double userLng = 0.0;
    private String locationName = "Unknown";

    private ActivityResultLauncher<Intent> categoryLauncher;
    private String    pendingUid;
    private UserModel pendingUser;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        auth = FirebaseAuth.getInstance();

        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser != null) {
            routeLoggedInUser(currentUser.getUid());
            return;
        }

        setContentView(R.layout.activity_signup);

        etName      = findViewById(R.id.etName);
        etPhone     = findViewById(R.id.etPhone);
        etEmail     = findViewById(R.id.etEmail);
        etPassword  = findViewById(R.id.etPassword);
        etDob       = findViewById(R.id.etDob);
        rbGeneral   = findViewById(R.id.rbGeneral);
        rbAnnouncer = findViewById(R.id.rbAnnouncer);
        btnSignup   = findViewById(R.id.btnSignup);
        tvGotoLogin = findViewById(R.id.tvGotoLogin);
        tvLangToggle = findViewById(R.id.tvLangToggle);
        tvIAm       = findViewById(R.id.tvIAm);
        tvAppName   = findViewById(R.id.tvAppName);
        tvSubtitle  = findViewById(R.id.tvSubtitle);

        locationClient = LocationServices.getFusedLocationProviderClient(this);
        requestLocation();

        applyStrings();

        etDob.setOnClickListener(v -> openDatePicker());
        btnSignup.setOnClickListener(v -> doSignup());
        tvGotoLogin.setOnClickListener(v -> finish());

        if (tvLangToggle != null) {
            tvLangToggle.setOnClickListener(v -> {
                LanguageManager.toggle(this);
                applyStrings();
            });
        }

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

                        if (cats != null) pendingUser.setHawkerCategories(cats);
                        if (subs != null) pendingUser.setHawkerSubcategories(subs);
                        if (othersName != null && !othersName.isEmpty())
                            pendingUser.setHawkerOthersName(othersName);

                        saveUserToFirebase(pendingUid, pendingUser);

                    } else {
                        FirebaseUser u = auth.getCurrentUser();
                        if (u != null) {
                            u.delete().addOnCompleteListener(task -> {});
                        }
                        pendingUid  = null;
                        pendingUser = null;
                        btnSignup.setEnabled(true);
                        AppStrings s = AppStrings.get(this);
                        Toast.makeText(this, s.signupSelectCategories(), Toast.LENGTH_LONG).show();
                    }
                }
        );
    }

    private void applyStrings() {
        AppStrings s = AppStrings.get(this);
        if (tvLangToggle  != null) tvLangToggle.setText(LanguageManager.getToggleLabel(this));
        if (tvAppName     != null) tvAppName.setText(s.appName());
        if (tvSubtitle    != null) tvSubtitle.setText(s.signupSubtitle());
        if (etName        != null) etName.setHint(s.signupHintName());
        if (etPhone       != null) etPhone.setHint(s.signupHintPhone());
        if (etEmail       != null) etEmail.setHint(s.signupHintEmail());
        if (etDob         != null) etDob.setHint(s.signupHintDob());
        if (etPassword    != null) etPassword.setHint(s.signupHintPassword());
        if (tvIAm         != null) tvIAm.setText(s.signupIAm());
        if (rbGeneral     != null) rbGeneral.setText(s.signupRoleGeneral());
        if (rbAnnouncer   != null) rbAnnouncer.setText(s.signupRoleAnnouncer());
        if (btnSignup     != null) btnSignup.setText(s.signupBtn());
        if (tvGotoLogin   != null) tvGotoLogin.setText(s.signupGoLogin());
    }

    private void routeLoggedInUser(String uid) {
        FirebaseDatabase.getInstance()
                .getReference(Constants.DB_USERS)
                .child(uid)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        UserModel u = snapshot.getValue(UserModel.class);
                        Class<?> dest = GeneralFeedActivity.class;
                        if (u != null && Constants.ROLE_ANNOUNCER.equals(u.getRole()))
                            dest = AnnouncerFeedActivity.class;
                        go(dest);
                    }
                    @Override public void onCancelled(@NonNull DatabaseError error) {
                        go(GeneralFeedActivity.class);
                    }
                });
    }

    private void requestLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_REQ);
        } else {
            fetchLocation();
        }
    }

    private void fetchLocation() {
        LocationRequest request = new LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY, 3000)
                .setMinUpdateIntervalMillis(2000)
                .build();

        locationCallback = new LocationCallback() {
            @Override public void onLocationResult(@NonNull LocationResult result) {
                Location loc = result.getLastLocation();
                if (loc == null) return;
                userLat = loc.getLatitude();
                userLng = loc.getLongitude();
                resolveAddress(loc);
                locationClient.removeLocationUpdates(locationCallback);
            }
        };

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) return;
        locationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper());
    }

    private void resolveAddress(Location location) {
        try {
            Geocoder g = new Geocoder(this, Locale.getDefault());
            List<Address> list = g.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
            if (list != null && !list.isEmpty()) {
                Address a = list.get(0);
                String locality = a.getLocality();
                String country  = a.getCountryName();
                if (locality != null && country != null) {
                    locationName = locality + ", " + country;
                }
            }
        } catch (Exception ignored) {}
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_REQ && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            fetchLocation();
        }
    }

    private void doSignup() {
        AppStrings s = AppStrings.get(this);
        String name  = etName.getText().toString().trim();
        String phone = etPhone.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String pass  = etPassword.getText().toString().trim();

        if (name.isEmpty() || email.isEmpty() || pass.isEmpty()) {
            toast(s.signupRequiredFields());
            return;
        }
        if (pass.length() < 6) {
            toast(s.signupPasswordShort());
            return;
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            toast(s.signupInvalidEmail());
            return;
        }

        String role = rbAnnouncer.isChecked()
                ? Constants.ROLE_ANNOUNCER : Constants.ROLE_USER;

        btnSignup.setEnabled(false);
        btnSignup.setText(s.signupCreating());

        auth.createUserWithEmailAndPassword(email, pass)
                .addOnSuccessListener(res -> {
                    btnSignup.setText(s.signupBtn());
                    String uid = auth.getUid();
                    if (uid == null) {
                        btnSignup.setEnabled(true);
                        return;
                    }

                    UserModel user = new UserModel(
                            uid, name, email, phone, role, locationName, userLat, userLng);

                    if (Constants.ROLE_ANNOUNCER.equals(role)) {
                        pendingUid  = uid;
                        pendingUser = user;
                        Intent catIntent = new Intent(this, HawkerCategoryActivity.class);
                        categoryLauncher.launch(catIntent);
                    } else {
                        saveUserToFirebase(uid, user);
                    }
                })
                .addOnFailureListener(e -> {
                    btnSignup.setEnabled(true);
                    btnSignup.setText(s.signupBtn());
                    String msg = e.getMessage();
                    if (msg != null && msg.contains("email address is already in use")) {
                        toast(s.signupEmailInUse());
                    } else {
                        toast(msg != null ? msg : s.signupBtn());
                    }
                });
    }

    private void saveUserToFirebase(String uid, UserModel user) {
        AppStrings s = AppStrings.get(this);
        FirebaseDatabase db = FirebaseDatabase.getInstance();

        db.getReference(Constants.DB_USERS)
                .child(uid)
                .setValue(user)
                .addOnSuccessListener(v -> {
                    String phone = user.getPhone();
                    if (phone != null && !phone.isEmpty()) {
                        String normalized = phone.trim().replaceAll("[\\s\\-]", "");
                        String key1 = normalized.replace("+", "").replace(".", "_");
                        db.getReference(Constants.DB_PHONE_MAP).child(key1).setValue(user.getEmail());

                        if (normalized.startsWith("0") && normalized.length() >= 11) {
                            String key2 = ("880" + normalized.substring(1)).replace(".", "_");
                            db.getReference(Constants.DB_PHONE_MAP).child(key2).setValue(user.getEmail());
                        }
                        if (normalized.startsWith("+880")) {
                            String key3 = ("0" + normalized.substring(4)).replace(".", "_");
                            db.getReference(Constants.DB_PHONE_MAP).child(key3).setValue(user.getEmail());
                        } else if (normalized.startsWith("880") && !normalized.startsWith("0")) {
                            String key3 = ("0" + normalized.substring(3)).replace(".", "_");
                            db.getReference(Constants.DB_PHONE_MAP).child(key3).setValue(user.getEmail());
                        }
                    }

                    LanguageManager.saveToFirebase(SignupActivity.this, uid);

                    btnSignup.setEnabled(true);
                    Class<?> dest = Constants.ROLE_ANNOUNCER.equals(user.getRole())
                            ? AnnouncerFeedActivity.class
                            : GeneralFeedActivity.class;
                    toast(s.signupWelcome(user.getName()));
                    go(dest);
                })
                .addOnFailureListener(e -> {
                    btnSignup.setEnabled(true);
                    toast(s.signupSaveProfileFailed(e.getMessage() != null ? e.getMessage() : ""));
                });
    }

    private void openDatePicker() {
        Calendar c = Calendar.getInstance();
        DatePickerDialog dp = new DatePickerDialog(
                this,
                (view, y, m, d) -> etDob.setText(d + "/" + (m + 1) + "/" + y),
                c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));
        dp.getDatePicker().setMaxDate(System.currentTimeMillis());
        dp.show();
    }

    private void go(Class<?> cls) {
        Intent intent = new Intent(SignupActivity.this, cls);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}