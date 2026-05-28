package com.example.towncrierbd.activities;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.towncrierbd.R;
import com.example.towncrierbd.utils.AppStrings;
import com.google.firebase.auth.FirebaseAuth;

public class ForgotPasswordActivity extends AppCompatActivity {

    private EditText etEmail;
    private Button btnSendOTP;
    private FirebaseAuth auth;

    // ✅ FIX: Do NOT initialize here — context is null at field init time
    private AppStrings strings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgot_password);

        // ✅ FIX: Initialize AFTER setContentView
        strings = AppStrings.get(this);

        auth       = FirebaseAuth.getInstance();
        etEmail    = findViewById(R.id.etEmail);
        btnSendOTP = findViewById(R.id.btnSendOTP);

        // ✅ Apply translated strings
        if (etEmail    != null) etEmail.setHint(strings.forgotHint());
        if (btnSendOTP != null) btnSendOTP.setText(strings.forgotSendBtn());

        TextView tvBackToLogin = findViewById(R.id.tvBackToLogin);
        if (tvBackToLogin != null) {
            tvBackToLogin.setText(strings.forgotBackToLogin());
            tvBackToLogin.setOnClickListener(v -> finish());
        }

        btnSendOTP.setOnClickListener(v -> sendResetEmail());
    }

    private void sendResetEmail() {
        String email = etEmail.getText().toString().trim();

        if (email.isEmpty()) {
            toast(strings.forgotEnterEmail());
            return;
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            toast(strings.forgotInvalidEmail());
            return;
        }

        btnSendOTP.setEnabled(false);
        btnSendOTP.setText(strings.forgotSending());

        auth.sendPasswordResetEmail(email)
                .addOnSuccessListener(v -> {
                    toast(strings.forgotSent());
                    finish();
                })
                .addOnFailureListener(e -> {
                    btnSendOTP.setEnabled(true);
                    btnSendOTP.setText(strings.forgotSendBtn());
                    toast(strings.forgotFailed(e.getMessage() != null ? e.getMessage() : ""));
                });
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }
}