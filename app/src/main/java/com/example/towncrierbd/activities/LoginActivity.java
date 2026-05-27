package com.example.towncrierbd.activities;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.example.towncrierbd.R;
import com.example.towncrierbd.models.UserModel;
import com.example.towncrierbd.utils.Constants;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.*;

public class LoginActivity extends AppCompatActivity {

    private EditText etEmail, etPassword;
    private Button btnLogin;
    private TextView tvGotoSignup, tvForgotPassword;

    private FirebaseAuth auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        super.onCreate(savedInstanceState);

        auth = FirebaseAuth.getInstance();

        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser != null) {
            routeUser();
            return;
        }

        setContentView(R.layout.activity_login);

        etEmail          = findViewById(R.id.etEmail);
        etPassword       = findViewById(R.id.etPassword);
        btnLogin         = findViewById(R.id.btnLogin);
        tvGotoSignup     = findViewById(R.id.tvGotoSignup);
        tvForgotPassword = findViewById(R.id.tvForgotPassword);

        btnLogin.setOnClickListener(v -> login());

        tvGotoSignup.setOnClickListener(v ->
                startActivity(new Intent(this, SignupActivity.class)));

        if (tvForgotPassword != null) {
            tvForgotPassword.setOnClickListener(v ->
                    startActivity(new Intent(this, ForgotPasswordActivity.class)));
        }
    }

    private void login() {
        String input = etEmail.getText().toString().trim();
        String pass  = etPassword.getText().toString().trim();

        if (input.isEmpty() || pass.isEmpty()) {
            toast("Email/Phone and Password required");
            return;
        }

        btnLogin.setEnabled(false);

        if (android.util.Patterns.EMAIL_ADDRESS.matcher(input).matches()) {
            loginWithEmail(input, pass);
        } else {
            loginWithPhone(input, pass);
        }
    }

    private void loginWithEmail(String email, String pass) {
        auth.signInWithEmailAndPassword(email, pass)
                .addOnSuccessListener(res -> routeUser())
                .addOnFailureListener(e -> {
                    btnLogin.setEnabled(true);
                    toast("Wrong email or password");
                });
    }

    // ✅ FIX: Phone login — normalize করে multiple key formats try করো
    private void loginWithPhone(String phone, String pass) {
        // Normalize: remove spaces and dashes
        String normalized = phone.trim().replaceAll("[\\s\\-]", "");

        // Build all possible DB key variations
        // Signup এ phone key store হয় format: phone.replace("+","").replace(".","_")
        String key1 = normalized.replace("+", "").replace(".", "_");

        // Also try: if user typed 01712..., try 8801712...
        String key2 = null;
        if (normalized.startsWith("0") && normalized.length() >= 11) {
            key2 = ("880" + normalized.substring(1)).replace(".", "_");
        }

        // Also try: if user typed +8801712..., try 01712...
        String key3 = null;
        if (normalized.startsWith("+880")) {
            key3 = ("0" + normalized.substring(4)).replace(".", "_");
        } else if (normalized.startsWith("880") && !normalized.startsWith("0")) {
            key3 = ("0" + normalized.substring(3)).replace(".", "_");
        }

        final String finalKey2 = key2;
        final String finalKey3 = key3;

        // Try key1 first
        FirebaseDatabase.getInstance()
                .getReference(Constants.DB_PHONE_MAP)
                .child(key1)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        String email = snapshot.getValue(String.class);
                        if (email != null && !email.isEmpty()) {
                            signInWithEmail(email, pass);
                            return;
                        }
                        // Try key2
                        if (finalKey2 != null) {
                            tryPhoneKey(finalKey2, finalKey3, normalized, pass);
                        } else if (finalKey3 != null) {
                            tryPhoneKey(finalKey3, null, normalized, pass);
                        } else {
                            loginWithPhoneFallback(normalized, pass);
                        }
                    }
                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        btnLogin.setEnabled(true);
                        toast("Login failed");
                    }
                });
    }

    private void tryPhoneKey(String key, String nextKey, String normalizedPhone, String pass) {
        FirebaseDatabase.getInstance()
                .getReference(Constants.DB_PHONE_MAP)
                .child(key)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        String email = snapshot.getValue(String.class);
                        if (email != null && !email.isEmpty()) {
                            signInWithEmail(email, pass);
                            return;
                        }
                        if (nextKey != null) {
                            tryPhoneKey(nextKey, null, normalizedPhone, pass);
                        } else {
                            loginWithPhoneFallback(normalizedPhone, pass);
                        }
                    }
                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        btnLogin.setEnabled(true);
                        toast("Login failed");
                    }
                });
    }

    // Final fallback: scan users by phone field directly
    private void loginWithPhoneFallback(String phone, String pass) {
        DatabaseReference usersRef = FirebaseDatabase.getInstance()
                .getReference(Constants.DB_USERS);

        // Try exact match first
        usersRef.orderByChild("phone").equalTo(phone)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            for (DataSnapshot s : snapshot.getChildren()) {
                                UserModel user = s.getValue(UserModel.class);
                                if (user != null && user.getEmail() != null) {
                                    signInWithEmail(user.getEmail(), pass);
                                    return;
                                }
                            }
                        }
                        // Try alternate formats in users collection
                        tryUserPhoneVariants(phone, pass);
                    }
                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        btnLogin.setEnabled(true);
                        toast("Login failed");
                    }
                });
    }

    private void tryUserPhoneVariants(String phone, String pass) {
        // Build alternate phone format to search
        String altPhone = null;
        if (phone.startsWith("0")) {
            altPhone = "+880" + phone.substring(1);
        } else if (phone.startsWith("880")) {
            altPhone = "0" + phone.substring(3);
        } else if (phone.startsWith("+880")) {
            altPhone = "0" + phone.substring(4);
        }

        if (altPhone == null) {
            btnLogin.setEnabled(true);
            toast("Phone number not found");
            return;
        }

        final String searchPhone = altPhone;
        FirebaseDatabase.getInstance()
                .getReference(Constants.DB_USERS)
                .orderByChild("phone").equalTo(searchPhone)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (!snapshot.exists()) {
                            btnLogin.setEnabled(true);
                            toast("Phone number not found");
                            return;
                        }
                        for (DataSnapshot s : snapshot.getChildren()) {
                            UserModel user = s.getValue(UserModel.class);
                            if (user != null && user.getEmail() != null) {
                                signInWithEmail(user.getEmail(), pass);
                                return;
                            }
                        }
                        btnLogin.setEnabled(true);
                        toast("Phone number not found");
                    }
                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        btnLogin.setEnabled(true);
                        toast("Login failed");
                    }
                });
    }

    // ✅ Helper: sign in with email (shared by all phone login paths)
    private void signInWithEmail(String email, String pass) {
        auth.signInWithEmailAndPassword(email, pass)
                .addOnSuccessListener(res -> routeUser())
                .addOnFailureListener(e -> {
                    btnLogin.setEnabled(true);
                    toast("Wrong password");
                });
    }

    private void routeUser() {
        String uid = auth.getUid();
        if (uid == null) {
            if (btnLogin != null) btnLogin.setEnabled(true);
            return;
        }

        FirebaseDatabase.getInstance()
                .getReference(Constants.DB_USERS)
                .child(uid)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (btnLogin != null) btnLogin.setEnabled(true);

                        UserModel user = snapshot.getValue(UserModel.class);
                        if (user == null) {
                            goTo(GeneralFeedActivity.class);
                            return;
                        }

                        if (Constants.ROLE_ANNOUNCER.equals(user.getRole())) {
                            goTo(AnnouncerFeedActivity.class);
                        } else {
                            goTo(GeneralFeedActivity.class);
                        }
                    }
                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        if (btnLogin != null) btnLogin.setEnabled(true);
                        toast("Login failed");
                    }
                });
    }

    private void goTo(Class<?> cls) {
        Intent intent = new Intent(LoginActivity.this, cls);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}