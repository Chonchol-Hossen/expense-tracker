package com.example.expensetracker;

import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

public class NotificationActivity extends AppCompatActivity {

    private FirebaseFirestore db;
    private String userId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notification);

        TextView notificationText = findViewById(R.id.notificationText);

        db = FirebaseFirestore.getInstance();
        userId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        db.collection("Users")
                .document(userId)
                .collection("notifications")
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(10)
                .get()
                .addOnSuccessListener(query -> {

                    boolean found = false;
                    StringBuilder builder = new StringBuilder();

                    for (QueryDocumentSnapshot doc : query) {
                        String message = doc.getString("message");
                        if (message != null && !message.trim().isEmpty()) {
                            builder.append("• ")
                                    .append(message)
                                    .append("\n\n");
                            found = true;
                        }
                    }

                    if (!found) {
                        // ✅ Your preferred empty state
                        notificationText.setText("No new notifications 🎉");
                        notificationText.setTextColor(
                                ContextCompat.getColor(this, R.color.button_green)
                        );
                    } else {
                        // 🔴 Show alerts
                        notificationText.setText(builder.toString().trim());
                        notificationText.setTextColor(
                                ContextCompat.getColor(this, R.color.button_red)
                        );
                    }
                })
                .addOnFailureListener(e -> {
                    // Safe fallback
                    notificationText.setText("No new notifications 🎉");
                    notificationText.setTextColor(
                            ContextCompat.getColor(this, R.color.button_green)
                    );
                });
    }
}
