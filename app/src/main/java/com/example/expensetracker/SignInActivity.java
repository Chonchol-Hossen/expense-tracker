package com.example.expensetracker;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils; // Added for empty checks
import android.util.Log;
import android.util.Patterns; // Added for Email validation
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.regex.Pattern; // Added for Regex

public class SignInActivity extends AppCompatActivity {

    private EditText emailEditText, passwordEditText;
    private Button signInButton;
    private TextView forgotPasswordText, signUpText;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    // SAME PASSWORD PATTERN AS SIGN UP
    private static final Pattern PASSWORD_PATTERN =
            Pattern.compile("^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=!])(?=\\S+$).{8,15}$");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign_in);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        emailEditText = findViewById(R.id.emailEditText);
        passwordEditText = findViewById(R.id.passwordEditText);
        signInButton = findViewById(R.id.signInButton);
        forgotPasswordText = findViewById(R.id.forgotPasswordText);
        signUpText = findViewById(R.id.signUpText);

        signInButton.setOnClickListener(v -> signInUser());

        signUpText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(SignInActivity.this, SignUpActivity.class);
                startActivity(intent);
            }
        });

        forgotPasswordText.setOnClickListener(v -> {
            startActivity(new Intent(SignInActivity.this, ForgotPasswordActivity.class));
        });
    }

    protected void onStart() {
        super.onStart();
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            startActivity(new Intent(SignInActivity.this, HomeActivity.class));
            finish();
        }
    }

    private void signInUser() {
        String email = emailEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString().trim();

        // --- VALIDATION START ---

        // 1. Check Email Empty
        if (TextUtils.isEmpty(email)) {
            emailEditText.setError("Email is required");
            emailEditText.requestFocus();
            return;
        }

        // 2. Check Email Format
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailEditText.setError("Please enter a valid email");
            emailEditText.requestFocus();
            return;
        }

        // 3. Check Password Empty
        if (TextUtils.isEmpty(password)) {
            passwordEditText.setError("Password is required");
            passwordEditText.requestFocus();
            return;
        }

        // 4. Check Password Strict Pattern
        if (!PASSWORD_PATTERN.matcher(password).matches()) {
            passwordEditText.setError("Password invalid (Must be 8-15 chars, Uppercase, Lowercase, Number, Special)");
            passwordEditText.requestFocus();
            return;
        }

        // --- VALIDATION END ---

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            String userId = user.getUid();
                            // Set hasNewNotification to true in Firestore
                            db.collection("Users").document(userId)
                                    .update("hasNewNotification", true)
                                    .addOnSuccessListener(aVoid -> {
                                        Log.d("Firebase", "Notification set to true for user: " + userId);
                                        Toast.makeText(SignInActivity.this, "Welcome back!", Toast.LENGTH_SHORT).show();
                                        Intent intent = new Intent(SignInActivity.this, HomeActivity.class);
                                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                                        startActivity(intent);
                                        finish();
                                    })
                                    .addOnFailureListener(e -> {
                                        Log.e("Firebase", "Error setting notification", e);
                                        // Proceed even if Firestore update fails
                                        Toast.makeText(SignInActivity.this, "Welcome back!", Toast.LENGTH_SHORT).show();
                                        Intent intent = new Intent(SignInActivity.this, HomeActivity.class);
                                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                                        startActivity(intent);
                                        finish();
                                    });
                        }
                    } else {
                        Toast.makeText(SignInActivity.this, "Authentication failed!", Toast.LENGTH_SHORT).show();
                    }
                });
    }
}