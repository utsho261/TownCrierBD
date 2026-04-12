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
    private TextView tvGotoSignup;

    private FirebaseAuth auth;
    private DatabaseReference userRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        super.onCreate(savedInstanceState);

        auth = FirebaseAuth.getInstance();

        // ✅ Already logged in থাকলে সরাসরি Feed এ যাও
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser != null) {
            routeUser();
            return; // onCreate বাকি execute করবে না
        }

        setContentView(R.layout.activity_login);

        etEmail      = findViewById(R.id.etEmail);
        etPassword   = findViewById(R.id.etPassword);
        btnLogin     = findViewById(R.id.btnLogin);
        tvGotoSignup = findViewById(R.id.tvGotoSignup);

        userRef = FirebaseDatabase.getInstance().getReference(Constants.DB_USERS);

        btnLogin.setOnClickListener(v -> login());
        tvGotoSignup.setOnClickListener(v ->
                startActivity(new Intent(this, SignupActivity.class)));
    }

    // ================= LOGIN =================

    private void login() {
        String input = etEmail.getText().toString().trim();
        String pass  = etPassword.getText().toString().trim();

        if (input.isEmpty() || pass.isEmpty()) {
            toast("Email or Phone and Password required");
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

    private void loginWithPhone(String phone, String pass) {
        userRef.orderByChild("phone").equalTo(phone)
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
                            if (user == null) continue;
                            auth.signInWithEmailAndPassword(user.getEmail(), pass)
                                    .addOnSuccessListener(res -> routeUser())
                                    .addOnFailureListener(e -> {
                                        btnLogin.setEnabled(true);
                                        toast("Wrong password");
                                    });
                            break;
                        }
                    }
                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        btnLogin.setEnabled(true);
                        toast("Login failed");
                    }
                });
    }

    private void routeUser() {
        String uid = auth.getUid();
        if (uid == null) {
            if (btnLogin != null) btnLogin.setEnabled(true);
            return;
        }

        DatabaseReference userRef = FirebaseDatabase.getInstance()
                .getReference(Constants.DB_USERS);

        userRef.child(uid).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (btnLogin != null) btnLogin.setEnabled(true);

                UserModel user = snapshot.getValue(UserModel.class);
                if (user == null) return;

                Intent intent;
                if (Constants.ROLE_ANNOUNCER.equals(user.getRole())) {
                    intent = new Intent(LoginActivity.this, AnnouncerFeedActivity.class);
                } else {
                    intent = new Intent(LoginActivity.this, GeneralFeedActivity.class);
                }

                // ✅ Back press করলে Login এ ফিরে যাবে না
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                if (btnLogin != null) btnLogin.setEnabled(true);
                toast("Login failed");
            }
        });
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}