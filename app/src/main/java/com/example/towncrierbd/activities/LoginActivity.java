package com.example.towncrierbd.activities;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDelegate;

import com.example.towncrierbd.R;
import com.example.towncrierbd.models.UserModel;
import com.example.towncrierbd.utils.Constants;
import com.example.towncrierbd.utils.LanguageManager;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.*;

public class LoginActivity extends BaseActivity {

    private EditText etEmail, etPassword;
    private Button   btnLogin;
    private TextView tvGotoSignup, tvForgotPassword, tvLanguageToggle;

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
        tvLanguageToggle = findViewById(R.id.tvLanguageToggle);

        // ── Language Toggle ────────────────────────────────────────────────
        if (tvLanguageToggle != null) {
            tvLanguageToggle.setText(LanguageManager.getToggleLabel(this));
            tvLanguageToggle.setOnClickListener(v -> switchLanguage());
        }

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
            toast(getString(R.string.fields_required));
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
                    toast(getString(R.string.wrong_credentials));
                });
    }

    private void loginWithPhone(String phone, String pass) {
        String normalized = phone.trim().replaceAll("[\\s\\-]", "");
        String key1 = normalized.replace("+", "").replace(".", "_");
        String key2 = null;
        if (normalized.startsWith("0") && normalized.length() >= 11)
            key2 = ("880" + normalized.substring(1)).replace(".", "_");
        String key3 = null;
        if (normalized.startsWith("+880"))
            key3 = ("0" + normalized.substring(4)).replace(".", "_");
        else if (normalized.startsWith("880") && !normalized.startsWith("0"))
            key3 = ("0" + normalized.substring(3)).replace(".", "_");

        final String finalKey2 = key2;
        final String finalKey3 = key3;

        FirebaseDatabase.getInstance()
                .getReference(Constants.DB_PHONE_MAP)
                .child(key1)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        String email = snapshot.getValue(String.class);
                        if (email != null && !email.isEmpty()) { signInWithEmail(email, pass); return; }
                        if (finalKey2 != null) tryPhoneKey(finalKey2, finalKey3, normalized, pass);
                        else if (finalKey3 != null) tryPhoneKey(finalKey3, null, normalized, pass);
                        else loginWithPhoneFallback(normalized, pass);
                    }
                    @Override public void onCancelled(@NonNull DatabaseError error) {
                        btnLogin.setEnabled(true); toast(getString(R.string.login_failed));
                    }
                });
    }

    private void tryPhoneKey(String key, String nextKey, String normalizedPhone, String pass) {
        FirebaseDatabase.getInstance().getReference(Constants.DB_PHONE_MAP).child(key)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                        String email = snapshot.getValue(String.class);
                        if (email != null && !email.isEmpty()) { signInWithEmail(email, pass); return; }
                        if (nextKey != null) tryPhoneKey(nextKey, null, normalizedPhone, pass);
                        else loginWithPhoneFallback(normalizedPhone, pass);
                    }
                    @Override public void onCancelled(@NonNull DatabaseError error) {
                        btnLogin.setEnabled(true); toast(getString(R.string.login_failed));
                    }
                });
    }

    private void loginWithPhoneFallback(String phone, String pass) {
        FirebaseDatabase.getInstance().getReference(Constants.DB_USERS)
                .orderByChild("phone").equalTo(phone)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            for (DataSnapshot s : snapshot.getChildren()) {
                                UserModel user = s.getValue(UserModel.class);
                                if (user != null && user.getEmail() != null) { signInWithEmail(user.getEmail(), pass); return; }
                            }
                        }
                        tryUserPhoneVariants(phone, pass);
                    }
                    @Override public void onCancelled(@NonNull DatabaseError error) {
                        btnLogin.setEnabled(true); toast(getString(R.string.login_failed));
                    }
                });
    }

    private void tryUserPhoneVariants(String phone, String pass) {
        String altPhone = null;
        if (phone.startsWith("0")) altPhone = "+880" + phone.substring(1);
        else if (phone.startsWith("880")) altPhone = "0" + phone.substring(3);
        else if (phone.startsWith("+880")) altPhone = "0" + phone.substring(4);

        if (altPhone == null) { btnLogin.setEnabled(true); toast(getString(R.string.phone_not_found)); return; }

        final String searchPhone = altPhone;
        FirebaseDatabase.getInstance().getReference(Constants.DB_USERS)
                .orderByChild("phone").equalTo(searchPhone)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (!snapshot.exists()) { btnLogin.setEnabled(true); toast(getString(R.string.phone_not_found)); return; }
                        for (DataSnapshot s : snapshot.getChildren()) {
                            UserModel user = s.getValue(UserModel.class);
                            if (user != null && user.getEmail() != null) { signInWithEmail(user.getEmail(), pass); return; }
                        }
                        btnLogin.setEnabled(true); toast(getString(R.string.phone_not_found));
                    }
                    @Override public void onCancelled(@NonNull DatabaseError error) {
                        btnLogin.setEnabled(true); toast(getString(R.string.login_failed));
                    }
                });
    }

    private void signInWithEmail(String email, String pass) {
        auth.signInWithEmailAndPassword(email, pass)
                .addOnSuccessListener(res -> routeUser())
                .addOnFailureListener(e -> { btnLogin.setEnabled(true); toast(getString(R.string.wrong_password)); });
    }

    private void routeUser() {
        String uid = auth.getUid();
        if (uid == null) { if (btnLogin != null) btnLogin.setEnabled(true); return; }
        FirebaseDatabase.getInstance().getReference(Constants.DB_USERS).child(uid)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (btnLogin != null) btnLogin.setEnabled(true);
                        UserModel user = snapshot.getValue(UserModel.class);
                        if (user == null) { goTo(GeneralFeedActivity.class); return; }
                        goTo(Constants.ROLE_ANNOUNCER.equals(user.getRole())
                                ? AnnouncerFeedActivity.class : GeneralFeedActivity.class);
                    }
                    @Override public void onCancelled(@NonNull DatabaseError error) {
                        if (btnLogin != null) btnLogin.setEnabled(true);
                        toast(getString(R.string.login_failed));
                    }
                });
    }

    private void goTo(Class<?> cls) {
        Intent intent = new Intent(LoginActivity.this, cls);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent); finish();
    }

    private void toast(String msg) { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show(); }
}