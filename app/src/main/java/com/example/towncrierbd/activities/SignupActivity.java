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
import com.example.towncrierbd.utils.Constants;
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

public class SignupActivity extends BaseActivity {

    private EditText etName, etPhone, etEmail, etPassword, etDob;
    private RadioButton rbGeneral, rbAnnouncer;
    private Button btnSignup;
    private TextView tvGotoLogin;

    private FirebaseAuth auth;

    private static final int LOCATION_REQ = 101;
    private FusedLocationProviderClient locationClient;
    private LocationCallback locationCallback;

    private double userLat = 0.0;
    private double userLng = 0.0;
    private String locationName = "Unknown";

    // ✅ FIX: Category launcher for Announcer signup
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

        locationClient = LocationServices.getFusedLocationProviderClient(this);
        requestLocation();

        etDob.setOnClickListener(v -> openDatePicker());
        btnSignup.setOnClickListener(v -> doSignup());
        tvGotoLogin.setOnClickListener(v -> finish());

        // ✅ FIX: Category picker result — launched after account creation for Announcers
        categoryLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        // ✅ User completed category selection — save their choices
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

                        // Now save user to Firebase
                        saveUserToFirebase(pendingUid, pendingUser);

                    } else {
                        // ✅ FIX: User pressed back from category screen
                        // Delete the Firebase Auth account since signup is incomplete
                        FirebaseUser u = auth.getCurrentUser();
                        if (u != null) {
                            u.delete().addOnCompleteListener(task -> {
                                // Account deleted — reset UI
                            });
                        }
                        pendingUid  = null;
                        pendingUser = null;
                        btnSignup.setEnabled(true);
                        Toast.makeText(this,
                                "Please select your selling categories to complete signup",
                                Toast.LENGTH_LONG).show();
                    }
                }
        );
    }

    // ── Route already-logged-in user ───────────────────────────────────────

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

    // ── Location ───────────────────────────────────────────────────────────

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

    // ── Signup ─────────────────────────────────────────────────────────────

    private void doSignup() {
        String name  = etName.getText().toString().trim();
        String phone = etPhone.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String pass  = etPassword.getText().toString().trim();

        if (name.isEmpty() || email.isEmpty() || pass.isEmpty()) {
            toast("Name, Email, Password required");
            return;
        }
        if (pass.length() < 6) {
            toast("Password must be at least 6 characters");
            return;
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            toast("Please enter a valid email address");
            return;
        }

        String role = rbAnnouncer.isChecked()
                ? Constants.ROLE_ANNOUNCER : Constants.ROLE_USER;

        btnSignup.setEnabled(false);
        btnSignup.setText("Creating account...");

        auth.createUserWithEmailAndPassword(email, pass)
                .addOnSuccessListener(res -> {
                    btnSignup.setText("Sign Up");
                    String uid = auth.getUid();
                    if (uid == null) {
                        btnSignup.setEnabled(true);
                        return;
                    }

                    UserModel user = new UserModel(
                            uid, name, email, phone, role, locationName, userLat, userLng);

                    if (Constants.ROLE_ANNOUNCER.equals(role)) {
                        // ✅ FIX: Announcer must select categories before account is saved
                        pendingUid  = uid;
                        pendingUser = user;
                        // Launch category selection screen
                        Intent catIntent = new Intent(this, HawkerCategoryActivity.class);
                        categoryLauncher.launch(catIntent);
                    } else {
                        // General User — save directly
                        saveUserToFirebase(uid, user);
                    }
                })
                .addOnFailureListener(e -> {
                    btnSignup.setEnabled(true);
                    btnSignup.setText("Sign Up");
                    String msg = e.getMessage();
                    if (msg != null && msg.contains("email address is already in use")) {
                        toast("This email is already registered. Please login.");
                    } else {
                        toast(msg != null ? msg : "Signup failed");
                    }
                });
    }

    // ── Save to Firebase ───────────────────────────────────────────────────

    private void saveUserToFirebase(String uid, UserModel user) {
        FirebaseDatabase db = FirebaseDatabase.getInstance();

        db.getReference(Constants.DB_USERS)
                .child(uid)
                .setValue(user)
                .addOnSuccessListener(v -> {
                    String phone = user.getPhone();
                    if (phone != null && !phone.isEmpty()) {
                        // Store ALL phone formats for flexible login
                        String normalized = phone.trim().replaceAll("[\\s\\-]", "");

                        // Format 1: as-is (remove + and replace . with _)
                        String key1 = normalized.replace("+", "").replace(".", "_");
                        db.getReference(Constants.DB_PHONE_MAP).child(key1).setValue(user.getEmail());

                        // Format 2: if starts with 0, also store 880... version
                        if (normalized.startsWith("0") && normalized.length() >= 11) {
                            String key2 = ("880" + normalized.substring(1)).replace(".", "_");
                            db.getReference(Constants.DB_PHONE_MAP).child(key2).setValue(user.getEmail());
                        }

                        // Format 3: +880 or 880 → store 0... version too
                        if (normalized.startsWith("+880")) {
                            String key3 = ("0" + normalized.substring(4)).replace(".", "_");
                            db.getReference(Constants.DB_PHONE_MAP).child(key3).setValue(user.getEmail());
                        } else if (normalized.startsWith("880") && !normalized.startsWith("0")) {
                            String key3 = ("0" + normalized.substring(3)).replace(".", "_");
                            db.getReference(Constants.DB_PHONE_MAP).child(key3).setValue(user.getEmail());
                        }
                    }

                    btnSignup.setEnabled(true);

                    Class<?> dest = Constants.ROLE_ANNOUNCER.equals(user.getRole())
                            ? AnnouncerFeedActivity.class
                            : GeneralFeedActivity.class;
                    toast("Welcome, " + user.getName() + "! 🎉");
                    go(dest);
                })
                .addOnFailureListener(e -> {
                    btnSignup.setEnabled(true);
                    toast("Failed to save profile: " + e.getMessage());
                });
    }

    // ── Helpers ────────────────────────────────────────────────────────────

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