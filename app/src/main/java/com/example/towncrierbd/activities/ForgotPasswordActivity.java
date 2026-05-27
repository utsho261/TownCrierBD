package com.example.towncrierbd.activities;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.towncrierbd.R;
import com.google.firebase.auth.FirebaseAuth;

public class ForgotPasswordActivity extends BaseActivity {

    private EditText etEmail;
    private Button btnSendOTP;
    private FirebaseAuth auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgot_password);

        auth       = FirebaseAuth.getInstance();
        etEmail    = findViewById(R.id.etEmail);
        btnSendOTP = findViewById(R.id.btnSendOTP);

        // ✅ FIXED: tvBackToLogin click listener was missing
        TextView tvBackToLogin = findViewById(R.id.tvBackToLogin);
        if (tvBackToLogin != null) {
            tvBackToLogin.setOnClickListener(v -> finish());
        }

        btnSendOTP.setOnClickListener(v -> sendResetEmail());
    }

    private void sendResetEmail() {
        String email = etEmail.getText().toString().trim();

        if (email.isEmpty()) {
            toast("Please enter your email");
            return;
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            toast("Enter a valid email");
            return;
        }

        btnSendOTP.setEnabled(false);
        btnSendOTP.setText("Sending...");

        auth.sendPasswordResetEmail(email)
                .addOnSuccessListener(v -> {
                    toast("Reset link sent! Check your email ✅");
                    finish();
                })
                .addOnFailureListener(e -> {
                    btnSendOTP.setEnabled(true);
                    btnSendOTP.setText("Send Reset Link");
                    toast("Failed: " + e.getMessage());
                });
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }
}