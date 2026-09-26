package com.example.expensetracker;

import android.os.Bundle;
import android.text.InputType;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Map;

public class VerifyEmailOtpActivity extends AppCompatActivity {

    private EditText inputOtp, inputPassword;
    private MaterialButton buttonVerify;
    private ImageView iconBack;

    private FirebaseAuth mAuth;
    private FirebaseFirestore firestore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_verify_email_otp);

        mAuth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();

        inputOtp = findViewById(R.id.inputOtp);
        inputPassword = findViewById(R.id.inputPassword);
        buttonVerify = findViewById(R.id.buttonVerify);
        iconBack = findViewById(R.id.iconBack);

        inputPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        iconBack.setOnClickListener(v -> finish());

        buttonVerify.setOnClickListener(v -> verifyOtpAndChangeEmail());
    }

    private void verifyOtpAndChangeEmail() {
        String otp = inputOtp.getText().toString().trim();
        String currentPassword = inputPassword.getText().toString().trim();

        if (otp.isEmpty()) {
            Toast.makeText(this, "Enter OTP", Toast.LENGTH_SHORT).show();
            return;
        }
        if (currentPassword.isEmpty()) {
            Toast.makeText(this, "Enter your current password", Toast.LENGTH_SHORT).show();
            return;
        }

        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "No user signed in", Toast.LENGTH_SHORT).show();
            return;
        }

        String uid = user.getUid();

        firestore.collection("pendingEmailChanges")
                .document(uid)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (!snapshot.exists()) {
                        Toast.makeText(this, "No pending email change found", Toast.LENGTH_LONG).show();
                        return;
                    }

                    Map<String, Object> data = snapshot.getData();
                    if (data == null) {
                        Toast.makeText(this, "Invalid pending data", Toast.LENGTH_LONG).show();
                        return;
                    }

                    String storedOtp = (String) data.get("otp");
                    String newEmail = (String) data.get("newEmail");

                    if (storedOtp == null || newEmail == null) {
                        Toast.makeText(this, "Incomplete pending data", Toast.LENGTH_LONG).show();
                        return;
                    }

                    if (!storedOtp.equals(otp)) {
                        Toast.makeText(this, "Invalid OTP", Toast.LENGTH_LONG).show();
                        return;
                    }

                    // OTP correct -> re-authenticate with password
                    AuthCredential credential = EmailAuthProvider.getCredential(user.getEmail(), currentPassword);

                    user.reauthenticate(credential)
                            .addOnSuccessListener(aVoid -> {
                                // Now safe to update email
                                user.updateEmail(newEmail)
                                        .addOnSuccessListener(aVoid2 -> {
                                            // Update email in Users collection
                                            firestore.collection("Users")
                                                    .document(uid)
                                                    .update("email", newEmail)
                                                    .addOnSuccessListener(aVoid3 -> {
                                                        // Remove pending record
                                                        firestore.collection("pendingEmailChanges")
                                                                .document(uid)
                                                                .delete();

                                                        showSuccessDialog();
                                                    })
                                                    .addOnFailureListener(e ->
                                                            Toast.makeText(this,
                                                                    "Email updated in auth but failed in Firestore: " + e.getMessage(),
                                                                    Toast.LENGTH_LONG).show());
                                        })
                                        .addOnFailureListener(e -> {
                                            if (e instanceof FirebaseAuthRecentLoginRequiredException) {
                                                Toast.makeText(this,
                                                        "Please log in again and retry.",
                                                        Toast.LENGTH_LONG).show();
                                            } else {
                                                Toast.makeText(this,
                                                        "Failed to update email: " + e.getMessage(),
                                                        Toast.LENGTH_LONG).show();
                                            }
                                        });
                            })
                            .addOnFailureListener(e ->
                                    Toast.makeText(this,
                                            "Re-authentication failed: " + e.getMessage(),
                                            Toast.LENGTH_LONG).show());
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this,
                                "Failed to load OTP: " + e.getMessage(),
                                Toast.LENGTH_LONG).show());
    }

    private void showSuccessDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Email Updated")
                .setMessage("Your email has been updated successfully.")
                .setPositiveButton("OK", (dialog, which) -> {
                    dialog.dismiss();
                    finish();
                })
                .setCancelable(false)
                .show();
    }
}
