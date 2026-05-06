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

import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class SignupActivity extends AppCompatActivity {

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        auth = FirebaseAuth.getInstance();

        // ✅ FIX: already logged-in → check role and route correctly (not always GeneralFeed)
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser != null) {
            routeLoggedInUser(currentUser.getUid());
            return;
        }

        setContentView(R.layout.activity_signup);

        etName     = findViewById(R.id.etName);
        etPhone    = findViewById(R.id.etPhone);
        etEmail    = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        etDob      = findViewById(R.id.etDob);

        rbGeneral   = findViewById(R.id.rbGeneral);
        rbAnnouncer = findViewById(R.id.rbAnnouncer);

        btnSignup   = findViewById(R.id.btnSignup);
        tvGotoLogin = findViewById(R.id.tvGotoLogin);

        locationClient = LocationServices.getFusedLocationProviderClient(this);
        requestLocation();

        etDob.setOnClickListener(v -> openDatePicker());
        btnSignup.setOnClickListener(v -> doSignup());
        tvGotoLogin.setOnClickListener(v -> finish());
    }

    // ✅ FIX: fetch role then route
    private void routeLoggedInUser(String uid) {
        FirebaseDatabase.getInstance()
                .getReference(Constants.DB_USERS)
                .child(uid)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        UserModel u = snapshot.getValue(UserModel.class);
                        Class<?> dest = GeneralFeedActivity.class;
                        if (u != null && Constants.ROLE_ANNOUNCER.equals(u.getRole())) {
                            dest = AnnouncerFeedActivity.class;
                        }
                        Intent intent = new Intent(SignupActivity.this, dest);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    }
                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        // Fallback to GeneralFeed
                        Intent intent = new Intent(SignupActivity.this, GeneralFeedActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
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
            List<Address> list = g.getFromLocation(
                    location.getLatitude(), location.getLongitude(), 1);
            if (list != null && !list.isEmpty()) {
                Address a = list.get(0);
                locationName = a.getLocality() + ", " + a.getCountryName();
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

        String role = rbAnnouncer.isChecked()
                ? Constants.ROLE_ANNOUNCER : Constants.ROLE_USER;

        btnSignup.setEnabled(false);

        auth.createUserWithEmailAndPassword(email, pass)
                .addOnSuccessListener(res -> {
                    String uid = auth.getUid();
                    if (uid == null) return;

                    UserModel user = new UserModel(
                            uid, name, email, phone, role, locationName, userLat, userLng);

                    FirebaseDatabase db = FirebaseDatabase.getInstance();

                    db.getReference(Constants.DB_USERS)
                            .child(uid)
                            .setValue(user)
                            .addOnSuccessListener(v -> {
                                if (!phone.isEmpty()) {
                                    String phoneKey = phone.replace("+", "").replace(".", "_");
                                    db.getReference(Constants.DB_PHONE_MAP)
                                            .child(phoneKey)
                                            .setValue(email);
                                }

                                btnSignup.setEnabled(true);

                                // ✅ role-aware routing after signup
                                Class<?> dest = Constants.ROLE_ANNOUNCER.equals(role)
                                        ? AnnouncerFeedActivity.class
                                        : GeneralFeedActivity.class;
                                Intent intent = new Intent(this, dest);
                                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                startActivity(intent);
                                finish();
                            });
                })
                .addOnFailureListener(e -> {
                    btnSignup.setEnabled(true);
                    toast(e.getMessage());
                });
    }

    private void openDatePicker() {
        Calendar c = Calendar.getInstance();
        DatePickerDialog dp = new DatePickerDialog(
                this,
                (view, y, m, d) -> etDob.setText(d + "/" + (m + 1) + "/" + y),
                c.get(Calendar.YEAR),
                c.get(Calendar.MONTH),
                c.get(Calendar.DAY_OF_MONTH)
        );
        dp.getDatePicker().setMaxDate(System.currentTimeMillis());
        dp.show();
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}