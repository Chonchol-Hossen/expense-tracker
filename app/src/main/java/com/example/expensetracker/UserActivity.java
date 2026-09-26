package com.example.expensetracker;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.HashMap;

import de.hdodenhof.circleimageview.CircleImageView;

public class UserActivity extends AppCompatActivity {

    private BottomNavigationView bottomNavigationView;

    private TextView sectionAccount, sectionCategories, sectionWallets, sectionSupport;
    private TextView textName, textEmail;
    private CircleImageView profileImage;
    private Button buttonSignOut;

    private FirebaseAuth mAuth;
    private FirebaseFirestore firestore;
    private FirebaseStorage storage;

    private boolean isUploading = false;

    private final ActivityResultLauncher<String> pickImageLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null && !isUploading) {
                    isUploading = true;
                    uploadProfileImageToFirebase(uri);
                }
            });

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user);

        mAuth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance();

        initViews();
        setupBottomNavigationView();
        setupClicks();
        loadUserInfo();
        clearOldCacheOnLogin();
        loadProfileImage();
    }

    private void initViews() {
        bottomNavigationView = findViewById(R.id.bottomNavigationView);
        profileImage = findViewById(R.id.profileImage);
        textName = findViewById(R.id.textName);
        textEmail = findViewById(R.id.textEmail);

        sectionAccount = findViewById(R.id.sectionAccount);
        sectionCategories = findViewById(R.id.sectionCategories);
        sectionWallets = findViewById(R.id.sectionWallets);
        sectionSupport = findViewById(R.id.sectionSupport);
        buttonSignOut = findViewById(R.id.buttonSignOut);
    }

    // -------------------------------------
    // CLEAR OLD IMAGE WHEN NEW USER LOGS IN
    // -------------------------------------
    private void clearOldCacheOnLogin() {
        File file = new File(getFilesDir(), "profile.jpg");
        if (file.exists()) file.delete();  // prevent showing old user’s photo
    }

    // -------------------------------------
    // BOTTOM NAVIGATION
    // -------------------------------------
    private void setupBottomNavigationView() {

        bottomNavigationView.setSelectedItemId(R.id.nav_user);

        bottomNavigationView.setOnItemSelectedListener(item -> {

            int id = item.getItemId();

            if (id == R.id.nav_home) {
                startActivity(new Intent(this, HomeActivity.class));
                overridePendingTransition(0,0);
                finish();
                return true;
            }
            if (id == R.id.nav_charts) {
                startActivity(new Intent(this, ChartsActivity.class));
                overridePendingTransition(0,0);
                finish();
                return true;
            }
            if (id == R.id.nav_chatbot) {
                startActivity(new Intent(this, ChatbotActivity.class));
                overridePendingTransition(0,0);
                finish();
                return true;
            }

            return id == R.id.nav_user;
        });
    }

    // -------------------------------------
    // CLICK HANDLERS
    // -------------------------------------
    private void setupClicks() {

        sectionAccount.setOnClickListener(v ->
                startActivity(new Intent(UserActivity.this, AccountEditActivity.class)));

        sectionCategories.setOnClickListener(v ->
                startActivity(new Intent(UserActivity.this, CategoriesActivity.class)));

        sectionWallets.setOnClickListener(v ->
                startActivity(new Intent(UserActivity.this, WalletsActivity.class)));

        sectionSupport.setOnClickListener(v ->
                startActivity(new Intent(UserActivity.this, SupportActivity.class)));

        profileImage.setOnClickListener(v -> pickImageLauncher.launch("image/*"));

        buttonSignOut.setOnClickListener(v -> {
            deleteLocalProfileImage();
            mAuth.signOut();

            Intent i = new Intent(UserActivity.this, SignInActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            finish();
        });
    }

    // -------------------------------------
    // LOAD USER INFO
    // -------------------------------------
    private void loadUserInfo() {
        if (mAuth.getCurrentUser() == null) return;

        String uid = mAuth.getCurrentUser().getUid();

        firestore.collection("Users").document(uid)
                .addSnapshotListener((snapshot, e) -> {
                    if (snapshot != null && snapshot.exists()) {
                        textName.setText(snapshot.getString("name"));
                        textEmail.setText(snapshot.getString("email"));
                    }
                });
    }

    // -------------------------------------
    // UPLOAD PROFILE IMAGE
    // -------------------------------------
    private void uploadProfileImageToFirebase(Uri uri) {

        Toast.makeText(this, "Uploading...", Toast.LENGTH_SHORT).show();

        String uid = mAuth.getCurrentUser().getUid();
        StorageReference ref = storage.getReference()
                .child("profileImages/" + uid + "/profile.jpg");

        ref.putFile(uri)
                .addOnSuccessListener(task -> ref.getDownloadUrl().addOnSuccessListener(downloadUri -> {
                    saveImageUrlToFirestore(downloadUri.toString());
                    saveImageToInternal(uri);
                    loadImage(downloadUri.toString());
                    isUploading = false;
                }))
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Upload failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    isUploading = false;
                });
    }

    private void saveImageUrlToFirestore(String url) {
        String uid = mAuth.getCurrentUser().getUid();

        firestore.collection("Users")
                .document(uid)
                .set(new HashMap<String, Object>() {{
                    put("profileImageUrl", url);
                }}, SetOptions.merge())
                .addOnSuccessListener(unused ->
                        Toast.makeText(UserActivity.this, "Profile photo updated", Toast.LENGTH_SHORT).show()
                );
    }

    // -------------------------------------
    // LOAD PROFILE IMAGE (SAFE)
    // -------------------------------------
    private void loadProfileImage() {

        String uid = mAuth.getCurrentUser().getUid();

        firestore.collection("Users")
                .document(uid)
                .get()
                .addOnSuccessListener(doc -> {

                    String url = doc.getString("profileImageUrl");

                    if (url != null && !url.isEmpty()) {
                        loadImage(url);
                    } else {
                        profileImage.setImageResource(R.drawable.ic_camera);
                    }
                })
                .addOnFailureListener(e ->
                        profileImage.setImageResource(R.drawable.ic_camera));
    }

    private void loadImage(String url) {

        Glide.with(this)
                .load(url)
                .placeholder(R.drawable.ic_camera)
                .diskCacheStrategy(DiskCacheStrategy.NONE)   // prevent old image showing
                .skipMemoryCache(true)                        // avoid cached images
                .into(profileImage);
    }

    // -------------------------------------
    // INTERNAL STORAGE FALLBACK
    // -------------------------------------
    private void saveImageToInternal(Uri uri) {
        try {
            InputStream is = getContentResolver().openInputStream(uri);
            Bitmap bmp = BitmapFactory.decodeStream(is);
            is.close();

            if (bmp == null) return;

            Bitmap scaled = scaleBitmap(bmp, 800);

            File file = new File(getFilesDir(), "profile.jpg");
            FileOutputStream fos = new FileOutputStream(file);
            scaled.compress(Bitmap.CompressFormat.JPEG, 85, fos);
            fos.close();

        } catch (Exception ignored) {}
    }

    private void deleteLocalProfileImage() {
        try {
            File file = new File(getFilesDir(), "profile.jpg");
            if (file.exists()) file.delete();
        } catch (Exception ignored) {}
    }

    private Bitmap scaleBitmap(Bitmap src, int maxSize) {
        float ratio = Math.min((float) maxSize / src.getWidth(), (float) maxSize / src.getHeight());
        int w = Math.round(src.getWidth() * ratio);
        int h = Math.round(src.getHeight() * ratio);
        return Bitmap.createScaledBitmap(src, w, h, true);
    }
}
