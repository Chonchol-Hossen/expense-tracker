package com.example.expensetracker;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;

public class ForgotPasswordActivity extends AppCompatActivity {

    private EditText emailEditText;
    private Button resetPasswordButton;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgot_password);

        // Initialize Firebase Auth
        mAuth = FirebaseAuth.getInstance();

        // Get UI elements
        emailEditText = findViewById(R.id.emailEditText);
        resetPasswordButton = findViewById(R.id.resetPasswordButton);

        // Set click listener
        resetPasswordButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String email = emailEditText.getText().toString().trim();

                if (TextUtils.isEmpty(email)) {
                    Toast.makeText(ForgotPasswordActivity.this, "Enter your email", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    Toast.makeText(ForgotPasswordActivity.this, "Invalid email format", Toast.LENGTH_SHORT).show();
                    return;
                }

                // Send password reset email
                mAuth.sendPasswordResetEmail(email)
                        .addOnCompleteListener(task -> {
                            if (task.isSuccessful()) {
                                // Inform user that if the email is registered, they will receive a reset link
                                Toast.makeText(ForgotPasswordActivity.this, "If this email is registered, a reset link has been sent!", Toast.LENGTH_LONG).show();
                            } else {
                                // If the email is not registered, show a generic message
                                if (task.getException() instanceof FirebaseAuthInvalidUserException) {
                                    // This exception is thrown if the email is not registered
                                    Toast.makeText(ForgotPasswordActivity.this, "This email is not registered. Please check your email and try again.", Toast.LENGTH_SHORT).show();
                                } else {
                                    // Handle any other errors
                                    Toast.makeText(ForgotPasswordActivity.this, "Failed to send reset email! Please try again later.", Toast.LENGTH_SHORT).show();
                                }
                            }
                        });
            }
        });
    }
}
