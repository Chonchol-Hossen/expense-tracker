package com.example.expensetracker;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns; // Import for Email validation
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

public class SignUpActivity extends AppCompatActivity {

    private EditText fullNameEditText, emailEditText, passwordEditText, confirmPasswordEditText;
    private Button signUpButton;
    private TextView forgotPasswordText, signInText;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    // PASSWORD PATTERN: 8-15 chars, 1 Upper, 1 Lower, 1 Digit, 1 Special Char
    private static final Pattern PASSWORD_PATTERN =
            Pattern.compile("^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=!])(?=\\S+$).{8,15}$");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign_up);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        fullNameEditText = findViewById(R.id.fullNameEditText);
        emailEditText = findViewById(R.id.emailEditText);
        passwordEditText = findViewById(R.id.passwordEditText);
        confirmPasswordEditText = findViewById(R.id.confirmPasswordEditText);
        signUpButton = findViewById(R.id.signUpButton);
        forgotPasswordText = findViewById(R.id.forgotPasswordText);
        signInText = findViewById(R.id.signInText);

        signUpButton.setOnClickListener(v -> registerUser());

        signInText.setOnClickListener(v -> {
            startActivity(new Intent(SignUpActivity.this, SignInActivity.class));
            finish();
        });

        forgotPasswordText.setOnClickListener(v -> {
            startActivity(new Intent(SignUpActivity.this, ForgotPasswordActivity.class));
        });
    }

    private void registerUser() {
        String name = fullNameEditText.getText().toString().trim();
        String email = emailEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString().trim();
        String confirmPassword = confirmPasswordEditText.getText().toString().trim();

        // 1. Name Check
        if (TextUtils.isEmpty(name)) {
            fullNameEditText.setError("Name is required");
            fullNameEditText.requestFocus();
            return;
        }

        // 2. Email Check (Empty?)
        if (TextUtils.isEmpty(email)) {
            emailEditText.setError("Email is required");
            emailEditText.requestFocus();
            return;
        }

        // 3. Email Check (Format? e.g. "text@domain.com")
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailEditText.setError("Please enter a valid email address");
            emailEditText.requestFocus();
            return;
        }

        // 4. Password Check (Empty?)
        if (TextUtils.isEmpty(password)) {
            passwordEditText.setError("Password is required");
            passwordEditText.requestFocus();
            return;
        }

        // 5. Password Check (Strict Rules)
        if (!PASSWORD_PATTERN.matcher(password).matches()) {
            passwordEditText.setError("Password must be 8-15 chars, contain at least 1 uppercase, 1 lowercase, 1 number, and 1 special symbol.");
            passwordEditText.requestFocus();
            return;
        }

        // 6. Confirm Password Check
        if (!password.equals(confirmPassword)) {
            confirmPasswordEditText.setError("Passwords do not match");
            confirmPasswordEditText.requestFocus();
            return;
        }

        // Proceed with Registration
        signUpButton.setEnabled(false);
        signUpButton.setText("Creating account...");

        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    signUpButton.setEnabled(true);
                    signUpButton.setText("Sign Up");

                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            saveUserData(user.getUid(), name, email);
                        }
                    } else {
                        Toast.makeText(this,
                                "Error: " + task.getException().getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void saveUserData(String userId, String name, String email) {
        Map<String, Object> userMap = new HashMap<>();
        userMap.put("name", name);
        userMap.put("email", email);
        userMap.put("balance", 0.0);
        userMap.put("profileImageUrl", "");

        db.collection("Users").document(userId).set(userMap)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Registration Successful!", Toast.LENGTH_SHORT).show();

                    // Go to Home Activity immediately
                    Intent intent = new Intent(this, HomeActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    finish();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this,
                            "Failed to save user data: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
    }
}