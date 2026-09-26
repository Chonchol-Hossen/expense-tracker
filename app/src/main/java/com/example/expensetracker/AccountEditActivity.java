package com.example.expensetracker;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.auth.UserInfo;

import java.util.regex.Pattern; // Import Pattern

public class AccountEditActivity extends AppCompatActivity {

    private MaterialButton sectionName, sectionEmail, sectionPassword, buttonDelete;
    private ImageView iconBack;

    private FirebaseAuth mAuth;
    private FirebaseFirestore firestore;

    // DEFINING THE PASSWORD PATTERN (Same as SignUp)
    private static final Pattern PASSWORD_PATTERN =
            Pattern.compile("^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=!])(?=\\S+$).{8,15}$");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_account_edit);

        mAuth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();

        sectionName = findViewById(R.id.sectionName);
        sectionEmail = findViewById(R.id.sectionEmail);
        sectionPassword = findViewById(R.id.sectionPassword);
        buttonDelete = findViewById(R.id.buttonDelete);
        iconBack = findViewById(R.id.iconBack);

        iconBack.setOnClickListener(v -> finish());

        sectionName.setOnClickListener(v -> editNameDialog());
        sectionEmail.setOnClickListener(v -> editEmailDialog());
        sectionPassword.setOnClickListener(v -> editPasswordDialog());
        buttonDelete.setOnClickListener(v -> confirmDelete());
    }

    // ------------------------ CHANGE NAME ------------------------
    private void editNameDialog() {
        EditText input = new EditText(this);
        input.setHint("Enter new name");

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Change Name")
                .setView(input)
                .setPositiveButton("Save", null)
                .setNegativeButton("Cancel", (d, w) -> d.dismiss())
                .create();

        dialog.setOnShowListener(dlg ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                    String newName = input.getText().toString().trim();
                    if (newName.isEmpty()) {
                        input.setError("Name cannot be empty");
                        return;
                    }

                    FirebaseUser user = mAuth.getCurrentUser();
                    if (user == null) return;

                    firestore.collection("Users")
                            .document(user.getUid())
                            .update("name", newName)
                            .addOnSuccessListener(aVoid ->
                                    Toast.makeText(this, "Name updated", Toast.LENGTH_SHORT).show())
                            .addOnFailureListener(e -> {
                                e.printStackTrace();
                                Log.e("NAME_UPDATE", "Name update failed", e);
                                Toast.makeText(this,
                                        "Failed: " + e.getMessage(),
                                        Toast.LENGTH_LONG).show();
                            });

                    dialog.dismiss();
                })
        );

        dialog.show();
    }

    // ------------------------ CHANGE EMAIL ------------------------
    private void editEmailDialog() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) return;

        boolean isPasswordProvider = false;
        for (UserInfo info : user.getProviderData()) {
            if (info.getProviderId().equals("password")) {
                isPasswordProvider = true;
                break;
            }
        }

        if (!isPasswordProvider) {
            Toast.makeText(this,
                    "Only Email/Password accounts can change email.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        EditText input = new EditText(this);
        input.setHint("Enter new email");
        input.setInputType(InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Change Email")
                .setView(input)
                .setPositiveButton("Next", null)
                .setNegativeButton("Cancel", (d, w) -> d.dismiss())
                .create();

        dialog.setOnShowListener(dlg ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                    String newEmail = input.getText().toString().trim();
                    if (newEmail.isEmpty()) {
                        input.setError("Enter a valid email");
                        return;
                    }

                    dialog.dismiss();
                    confirmPasswordForEmail(newEmail);
                })
        );

        dialog.show();
    }

    private void confirmPasswordForEmail(String newEmail) {
        EditText input = new EditText(this);
        input.setHint("Enter current password");
        input.setInputType(InputType.TYPE_TEXT_VARIATION_PASSWORD);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Confirm Password")
                .setView(input)
                .setPositiveButton("Update", null)
                .setNegativeButton("Cancel", (d, w) -> d.dismiss())
                .create();

        dialog.setOnShowListener(dlg ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                    String password = input.getText().toString().trim();
                    if (password.isEmpty()) {
                        input.setError("Password required");
                        return;
                    }

                    dialog.dismiss();
                    updateEmail(newEmail, password);
                })
        );

        dialog.show();
    }

    private void updateEmail(String newEmail, String password) {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) return;

        AuthCredential credential = EmailAuthProvider.getCredential(user.getEmail(), password);

        user.reauthenticate(credential)
                .addOnSuccessListener(aVoid ->
                        user.updateEmail(newEmail)
                                .addOnSuccessListener(aVoid2 -> {
                                    firestore.collection("Users")
                                            .document(user.getUid())
                                            .update("email", newEmail);

                                    Toast.makeText(this,
                                            "Email updated successfully",
                                            Toast.LENGTH_LONG).show();
                                })
                                .addOnFailureListener(e -> {
                                    e.printStackTrace();
                                    Log.e("EMAIL_UPDATE", "Email update failed", e);
                                    Toast.makeText(this,
                                            "Failed: " + e.getMessage(),
                                            Toast.LENGTH_LONG).show();
                                })
                )
                .addOnFailureListener(e -> {
                    e.printStackTrace();
                    Log.e("REAUTH", "Reauthentication failed", e);
                    Toast.makeText(this,
                            "Password incorrect",
                            Toast.LENGTH_LONG).show();
                });
    }

    // ------------------------ CHANGE PASSWORD ------------------------
    private void editPasswordDialog() {
        EditText input = new EditText(this);
        input.setHint("Enter new password");
        input.setInputType(InputType.TYPE_TEXT_VARIATION_PASSWORD);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Change Password")
                .setView(input)
                .setPositiveButton("Save", null)
                .setNegativeButton("Cancel", (d, w) -> d.dismiss())
                .create();

        dialog.setOnShowListener(dlg ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                    String newPassword = input.getText().toString().trim();

                    // --- UPDATED VALIDATION LOGIC START ---
                    if (!PASSWORD_PATTERN.matcher(newPassword).matches()) {
                        input.setError("Password must be 8-15 chars, contain at least 1 uppercase, 1 lowercase, 1 number, and 1 special symbol.");
                        return;
                    }
                    // --- UPDATED VALIDATION LOGIC END ---

                    FirebaseUser user = mAuth.getCurrentUser();
                    if (user == null) return;

                    user.updatePassword(newPassword)
                            .addOnSuccessListener(aVoid ->
                                    Toast.makeText(this, "Password changed", Toast.LENGTH_SHORT).show())
                            .addOnFailureListener(e -> {
                                e.printStackTrace();
                                Log.e("PASS_UPDATE", "Password update failed", e);
                                Toast.makeText(this,
                                        "Failed: " + e.getMessage(),
                                        Toast.LENGTH_LONG).show();
                            });

                    dialog.dismiss();
                })
        );

        dialog.show();
    }

    // ------------------------ DELETE ACCOUNT ------------------------
    private void confirmDelete() {
        new AlertDialog.Builder(this)
                .setTitle("Delete Account?")
                .setMessage("This cannot be undone.")
                .setPositiveButton("Delete", (d, w) -> deleteAccount())
                .setNegativeButton("Cancel", (d, w) -> d.dismiss())
                .show();
    }

    private void deleteAccount() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) return;

        firestore.collection("Users")
                .document(user.getUid())
                .delete()
                .addOnSuccessListener(aVoid ->
                        user.delete()
                                .addOnSuccessListener(aVoid2 -> {
                                    Toast.makeText(this,
                                            "Account deleted",
                                            Toast.LENGTH_SHORT).show();

                                    mAuth.signOut();

                                    Intent i = new Intent(AccountEditActivity.this, OnboardingActivity.class);
                                    i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP |
                                            Intent.FLAG_ACTIVITY_NEW_TASK |
                                            Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                    startActivity(i);
                                    finish();
                                })

                                .addOnFailureListener(e -> {
                                    e.printStackTrace();
                                    Log.e("DELETE", "Auth delete failed", e);
                                    Toast.makeText(this,
                                            "Failed: " + e.getMessage(),
                                            Toast.LENGTH_LONG).show();
                                })
                )
                .addOnFailureListener(e -> {
                    e.printStackTrace();
                    Log.e("DELETE", "Firestore delete failed", e);
                    Toast.makeText(this,
                            "Failed to delete user data",
                            Toast.LENGTH_LONG).show();
                });
    }
}