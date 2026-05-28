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
import com.example.towncrierbd.utils.AppStrings;
import com.example.towncrierbd.utils.Constants;
import com.example.towncrierbd.utils.LanguageManager;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.*;

public class LoginActivity extends AppCompatActivity {

    private EditText etEmail, etPassword;
    private Button btnLogin;
    private TextView tvGotoSignup, tvForgotPassword, tvLangToggle;
    private TextView tvAppName, tvSubtitle;

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
        tvLangToggle     = findViewById(R.id.tvLangToggle);
        tvAppName        = findViewById(R.id.tvAppName);
        tvSubtitle       = findViewById(R.id.tvSubtitle);

        applyStrings();

        btnLogin.setOnClickListener(v -> login());

        tvGotoSignup.setOnClickListener(v ->
                startActivity(new Intent(this, SignupActivity.class)));

        if (tvForgotPassword != null) {
            tvForgotPassword.setOnClickListener(v ->
                    startActivity(new Intent(this, ForgotPasswordActivity.class)));
        }

        // Language toggle — recreate so all static views refresh.
        // Input fields are EMPTY on login page so recreate is safe here.
        if (tvLangToggle != null) {
            tvLangToggle.setOnClickListener(v -> {
                LanguageManager.toggle(this);
                recreate();
            });
        }
    }

    /**
     * Apply all UI strings from AppStrings based on current language.
     *
     * KEY RULE: setHint() on EditText is safe — it never overwrites typed text.
     * setText() on EditText would overwrite — we never call it on input fields.
     * Static labels (TextViews) always use setText().
     */
    private void applyStrings() {
        AppStrings s = AppStrings.get(this);

        // Static label TextViews — always safe to setText()
        if (tvAppName  != null) tvAppName.setText(s.loginTitle());
        if (tvSubtitle != null) tvSubtitle.setText(s.loginSubtitle());

        // Buttons & links
        if (btnLogin         != null) btnLogin.setText(s.loginBtn());
        if (tvGotoSignup     != null) tvGotoSignup.setText(s.loginGoSignup());
        if (tvForgotPassword != null) tvForgotPassword.setText(s.loginForgot());
        if (tvLangToggle     != null) tvLangToggle.setText(LanguageManager.getToggleLabel(this));

        // Hints only on input fields — NEVER setText()
        if (etEmail    != null) etEmail.setHint(s.loginHintEmailPhone());

        // For TextInputEditText inside TextInputLayout:
        // Set hint on the EditText directly (NOT on the TextInputLayout)
        // to avoid MaterialComponents re-applying it over typed text.
        if (etPassword != null) etPassword.setHint(s.loginHintPassword());
    }

    private void login() {
        AppStrings s = AppStrings.get(this);
        String input = etEmail.getText().toString().trim();
        String pass  = etPassword.getText().toString().trim();

        if (input.isEmpty() || pass.isEmpty()) {
            toast(s.loginRequiredFields());
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
                    toast(AppStrings.get(this).loginWrongCredential());
                });
    }

    private void loginWithPhone(String phone, String pass) {
        String normalized = phone.trim().replaceAll("[\\s\\-]", "");
        String key1 = normalized.replace("+", "").replace(".", "_");

        String key2 = null;
        if (normalized.startsWith("0") && normalized.length() >= 11) {
            key2 = ("880" + normalized.substring(1)).replace(".", "_");
        }
        String key3 = null;
        if (normalized.startsWith("+880")) {
            key3 = ("0" + normalized.substring(4)).replace(".", "_");
        } else if (normalized.startsWith("880") && !normalized.startsWith("0")) {
            key3 = ("0" + normalized.substring(3)).replace(".", "_");
        }

        final String finalKey2 = key2;
        final String finalKey3 = key3;

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
                        toast(AppStrings.get(LoginActivity.this).loginFailed());
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
                        toast(AppStrings.get(LoginActivity.this).loginFailed());
                    }
                });
    }

    private void loginWithPhoneFallback(String phone, String pass) {
        DatabaseReference usersRef = FirebaseDatabase.getInstance()
                .getReference(Constants.DB_USERS);
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
                        tryUserPhoneVariants(phone, pass);
                    }
                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        btnLogin.setEnabled(true);
                        toast(AppStrings.get(LoginActivity.this).loginFailed());
                    }
                });
    }

    private void tryUserPhoneVariants(String phone, String pass) {
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
            toast(AppStrings.get(this).loginPhoneNotFound());
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
                            toast(AppStrings.get(LoginActivity.this).loginPhoneNotFound());
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
                        toast(AppStrings.get(LoginActivity.this).loginPhoneNotFound());
                    }
                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        btnLogin.setEnabled(true);
                        toast(AppStrings.get(LoginActivity.this).loginFailed());
                    }
                });
    }

    private void signInWithEmail(String email, String pass) {
        auth.signInWithEmailAndPassword(email, pass)
                .addOnSuccessListener(res -> routeUser())
                .addOnFailureListener(e -> {
                    btnLogin.setEnabled(true);
                    toast(AppStrings.get(this).loginWrongCredential());
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

                        LanguageManager.syncFromFirebase(LoginActivity.this, uid, () -> {
                            if (Constants.ROLE_ANNOUNCER.equals(user.getRole())) {
                                goTo(AnnouncerFeedActivity.class);
                            } else {
                                goTo(GeneralFeedActivity.class);
                            }
                        });
                    }
                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        if (btnLogin != null) btnLogin.setEnabled(true);
                        toast(AppStrings.get(LoginActivity.this).loginFailed());
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