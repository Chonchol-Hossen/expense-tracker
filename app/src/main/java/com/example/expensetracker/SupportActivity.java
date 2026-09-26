package com.example.expensetracker;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

public class SupportActivity extends AppCompatActivity {

    private MaterialButton sectionPhone, sectionEmail;
    private ImageView iconBack;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_support);

        initViews();
        setListeners();
    }

    private void initViews() {
        iconBack = findViewById(R.id.iconBack);
        sectionPhone = findViewById(R.id.sectionPhone);
        sectionEmail = findViewById(R.id.sectionEmail);
    }

    private void setListeners() {

        // Back button
        iconBack.setOnClickListener(v -> finish());

        // Phone click
        sectionPhone.setOnClickListener(v -> {
            String phone = "+880 1580 359364";  // Your support number
            Intent intent = new Intent(Intent.ACTION_DIAL);
            intent.setData(Uri.parse("tel:" + phone));

            try {
                startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(this, "Call app not found", Toast.LENGTH_SHORT).show();
            }
        });

        // Email click
        sectionEmail.setOnClickListener(v -> {
            String email = "asikur.thedashstudio@gmail.com";  // Your support email
            Intent intent = new Intent(Intent.ACTION_SENDTO);
            intent.setData(Uri.parse("mailto:" + email));
            intent.putExtra(Intent.EXTRA_SUBJECT, "Support Request");
            intent.putExtra(Intent.EXTRA_TEXT, "Hello, I need help regarding...");

            try {
                startActivity(Intent.createChooser(intent, "Send Email"));
            } catch (Exception e) {
                Toast.makeText(this, "Email app not found", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
