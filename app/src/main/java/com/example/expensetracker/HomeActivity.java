package com.example.expensetracker;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.TextView;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class HomeActivity extends AppCompatActivity
        implements TransactionAdapter.OnTransactionActionListener {

    private ImageView notificationIcon, profileImage, fabCreate;
    private View notificationBadge;
    private TextView greetingText, subText, balanceAmount, incomeAmount, expenseAmount, monthYearText;
    private LinearLayout datePickerContainer;
    private RecyclerView transactionsRecyclerView;
    private TransactionAdapter transactionAdapter;
    private List<Transaction> transactionList;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private Calendar selectedDate;
    private BottomNavigationView bottomNavigationView;
    private ListenerRegistration profileListener;
    private ActivityResultLauncher<Intent> createTransactionLauncher;
    private String userId;

    // ---------- MASTER CATEGORY ICON MAP ----------
    private static class CategoryIcon {
        int resId;
        String uri;
    }

    private final Map<String, CategoryIcon> categoryIconMap = new HashMap<>();
    // ------------------------------------------------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            startActivity(new Intent(this, SignInActivity.class));
            finish();
            return;
        }
        userId = currentUser.getUid();

        notificationIcon = findViewById(R.id.notificationIcon);
        notificationBadge = findViewById(R.id.notificationBadge);
        profileImage = findViewById(R.id.profileImage);
        fabCreate = findViewById(R.id.fabCreate);
        greetingText = findViewById(R.id.greetingText);
        subText = findViewById(R.id.subText);
        balanceAmount = findViewById(R.id.balanceAmount);
        incomeAmount = findViewById(R.id.incomeAmount);
        expenseAmount = findViewById(R.id.expenseAmount);
        datePickerContainer = findViewById(R.id.datePickerContainer);
        monthYearText = findViewById(R.id.monthYearText);
        transactionsRecyclerView = findViewById(R.id.transactionsRecyclerView);
        bottomNavigationView = findViewById(R.id.bottomNavigationView);

        transactionList = new ArrayList<>();

        // Pass 'this' as listener
        transactionAdapter = new TransactionAdapter(this, this);
        transactionsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        transactionsRecyclerView.setAdapter(transactionAdapter);

        selectedDate = Calendar.getInstance();
        updateMonthYearText();

        initializeUserCategoryMappings(); // optional legacy
        loadDataFromFirebase();
        loadTransactionsFromFirestore();
        setupBottomNavigationView();

        // Listeners
        notificationIcon.setOnClickListener(v -> openNotifications());
        // 🚫 Make profile image non-clickable (do NOT go to UserActivity)
        profileImage.setOnClickListener(null);

        datePickerContainer.setOnClickListener(v -> showMonthYearPickerDialog());

        createTransactionLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        selectedDate = Calendar.getInstance();
                        updateMonthYearText();
                        loadTransactionsFromFirestore();
                        loadDataFromFirebase();
                    }
                }
        );

        fabCreate.setOnClickListener(v -> {
            Intent intent = new Intent(HomeActivity.this, CreateTransactionActivity.class);
            createTransactionLauncher.launch(intent);
        });
    }

    // ------------------ Transaction actions ------------------

    @Override
    public void onEdit(Transaction transaction) {
        // If you later add Edit from popup, this will work.
        if (transaction.getDocumentId() == null) {
            Toast.makeText(this, "Error: Transaction ID is missing.", Toast.LENGTH_LONG).show();
            return;
        }

        Intent intent = new Intent(this, CreateTransactionActivity.class);
        intent.putExtra("EDIT_TRANSACTION_ID", transaction.getDocumentId());
        createTransactionLauncher.launch(intent);
    }

    @Override
    public void onDelete(Transaction transaction) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Transaction")
                .setMessage("Are you sure you want to delete the transaction for " + transaction.getCategory() + "? This action cannot be undone.")
                .setPositiveButton("Delete", (dialog, which) -> deleteTransaction(transaction))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteTransaction(Transaction transaction) {
        if (transaction.getDocumentId() == null) {
            Toast.makeText(this, "Error: Transaction ID is missing.", Toast.LENGTH_SHORT).show();
            return;
        }

        DocumentReference transactionRef = db.collection("Users").document(userId)
                .collection("transactions").document(transaction.getDocumentId());

        transactionRef.delete()
                .addOnSuccessListener(aVoid -> updateUserBalanceAfterDeletion(transaction))
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Delete failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    Log.e("Firestore", "Error deleting transaction", e);
                });
    }

    private void updateUserBalanceAfterDeletion(Transaction transaction) {
        DocumentReference userRef = db.collection("Users").document(userId);
        final double amount = transaction.getAmount();
        final boolean isExpense = transaction.isExpense();

        db.runTransaction(t -> {
            DocumentSnapshot snapshot = t.get(userRef);
            Double currentBalance = snapshot.getDouble("balance");
            if (currentBalance == null) currentBalance = 0.0;
            Double currentIncome = snapshot.getDouble("totalIncome");
            if (currentIncome == null) currentIncome = 0.0;
            Double currentExpense = snapshot.getDouble("totalExpense");
            if (currentExpense == null) currentExpense = 0.0;

            double newBalance, newIncome = currentIncome, newExpense = currentExpense;

            if (isExpense) {
                newBalance = currentBalance + amount;
                newExpense = currentExpense - amount;
            } else {
                newBalance = currentBalance - amount;
                newIncome = currentIncome - amount;
            }

            t.update(userRef, "balance", newBalance);
            t.update(userRef, "totalIncome", newIncome);
            t.update(userRef, "totalExpense", newExpense);

            return null;
        }).addOnSuccessListener(aVoid -> {
            Toast.makeText(this, "Transaction deleted successfully. Balance updated.", Toast.LENGTH_SHORT).show();
            loadDataFromFirebase();
            loadTransactionsFromFirestore();
        }).addOnFailureListener(e -> {
            Toast.makeText(this, "Failed to update balance.", Toast.LENGTH_LONG).show();
            Log.e("Firestore", "Balance update error", e);
        });
    }

    // ------------------ MASTER ICON MAP ------------------

    private void addIconToMap(String name, int resId) {
        CategoryIcon icon = new CategoryIcon();
        icon.resId = resId;
        icon.uri = null;
        categoryIconMap.put(name.toLowerCase(Locale.ROOT).trim(), icon);
    }

    private void loadCategoryIconMap(Runnable callback) {
        categoryIconMap.clear();

        // 1. Default EXPENSE categories
        addIconToMap("Food", R.drawable.ic_food);
        addIconToMap("Transport", R.drawable.ic_transport);
        addIconToMap("Shopping", R.drawable.ic_shopping);
        addIconToMap("Health", R.drawable.ic_health);
        addIconToMap("Entertainment", R.drawable.ic_entertainment);
        addIconToMap("Utilities", R.drawable.ic_utilities);
        addIconToMap("Rent", R.drawable.ic_rent);
        addIconToMap("Education", R.drawable.ic_education);
        addIconToMap("Other", R.drawable.ic_other);

        // 2. Default INCOME categories
        addIconToMap("Salary", R.drawable.ic_salary);
        addIconToMap("Business", R.drawable.ic_business);
        addIconToMap("Investment", R.drawable.ic_investment);
        addIconToMap("Freelance", R.drawable.ic_freelance);
        addIconToMap("Gift", R.drawable.ic_gift);
        addIconToMap("Bonus", R.drawable.ic_bonus);
        addIconToMap("Rental", R.drawable.ic_rental);
        addIconToMap("Refund", R.drawable.ic_refund);
        addIconToMap("Other", R.drawable.ic_other);

        // 3. Custom categories from /Users/{uid}/categories
        db.collection("Users").document(userId)
                .collection("categories")
                .get()
                .addOnSuccessListener(snap -> {
                    for (QueryDocumentSnapshot doc : snap) {
                        String name = doc.getString("name");
                        if (name == null) continue;
                        String normalized = name.toLowerCase(Locale.ROOT).trim();

                        String uri = doc.getString("iconUri");
                        Long resLong = doc.getLong("iconResId");

                        CategoryIcon icon = new CategoryIcon();
                        if (uri != null && !uri.isEmpty()) {
                            icon.uri = uri;
                            icon.resId = 0;
                        } else if (resLong != null) {
                            icon.resId = resLong.intValue();
                            icon.uri = null;
                        } else {
                            icon.resId = R.drawable.ic_other;
                        }

                        categoryIconMap.put(normalized, icon);
                    }
                    callback.run();
                })
                .addOnFailureListener(e -> {
                    Log.e("HomeActivity", "Failed to load categories for icons: " + e.getMessage());
                    callback.run();
                });
    }

    // ------------------ LOAD TRANSACTIONS WITH ICON MAP ------------------

    private void loadTransactionsFromFirestore() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) return;

        loadCategoryIconMap(() -> {

            db.collection("Users").document(user.getUid())
                    .collection("transactions")
                    .orderBy("date", Query.Direction.DESCENDING)
                    .get()
                    .addOnSuccessListener(queryDocumentSnapshots -> {
                        transactionList.clear();

                        for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                            String category = doc.getString("category");
                            String paymentMethod = doc.getString("paymentMethod");
                            Double amount = doc.getDouble("amount");
                            Date date = doc.getDate("date");
                            Boolean isExpense = doc.getBoolean("isExpense");

                            if (category == null || amount == null || date == null || isExpense == null) {
                                continue;
                            }

                            String normalized = category.toLowerCase(Locale.ROOT).trim();

                            CategoryIcon icon = categoryIconMap.get(normalized);
                            int resId = icon != null ? icon.resId : (isExpense ? R.drawable.ic_other : R.drawable.ic_salary);
                            String uri = icon != null ? icon.uri : null;

                            Transaction tx = new Transaction(
                                    category,
                                    paymentMethod,
                                    amount,
                                    date,
                                    isExpense,
                                    resId,
                                    uri,
                                    doc.getId()
                            );
                            tx.setDocumentId(doc.getId());
                            transactionList.add(tx);
                        }
                        applyDateFilter();
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(this, "Failed to load transactions", Toast.LENGTH_SHORT).show();
                        Log.e("Firestore", "Error loading transactions", e);
                    });
        });
    }

    // ------------------ Rest of HomeActivity ------------------

    private void initializeUserCategoryMappings() {
        // Legacy, safe to keep (not required for icons)
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) return;

        String userId = user.getUid();
        DocumentReference categoryRef = db.collection("UserCategoryMappings").document(userId);

        categoryRef.get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && !task.getResult().exists()) {
                Map<String, String> defaultCategories = new HashMap<>();
                defaultCategories.put("Food", "ic_food");
                defaultCategories.put("Groceries", "ic_food");
                defaultCategories.put("Salary", "ic_salary");
                defaultCategories.put("Rent", "ic_home");
                defaultCategories.put("Transport", "ic_transport");
                defaultCategories.put("Shopping", "ic_shopping");
                defaultCategories.put("Utilities", "ic_utilities");
                defaultCategories.put("Entertainment", "ic_entertainment");

                categoryRef.set(defaultCategories)
                        .addOnSuccessListener(aVoid -> Log.d("Firestore", "Default categories initialized for user"))
                        .addOnFailureListener(e -> Log.e("Firestore", "Failed to set default categories", e));
            }
        });
    }

    private void setupBottomNavigationView() {
        bottomNavigationView.setLabelVisibilityMode(BottomNavigationView.LABEL_VISIBILITY_UNLABELED);
        bottomNavigationView.setSelectedItemId(R.id.nav_home);

        bottomNavigationView.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_home) return true;
            if (itemId == R.id.nav_charts) {
                startActivity(new Intent(HomeActivity.this, ChartsActivity.class));
                return true;
            }
            if (itemId == R.id.nav_chatbot) {
                startActivity(new Intent(HomeActivity.this, ChatbotActivity.class));
                return true;
            }
            if (itemId == R.id.nav_user) {
                startActivity(new Intent(HomeActivity.this, UserActivity.class));
                return true;
            }
            return false;
        });
    }

    private void updateMonthYearText() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("MMMM yyyy", Locale.getDefault());
        monthYearText.setText(dateFormat.format(selectedDate.getTime()));
    }

    private void showMonthYearPickerDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select Month and Year");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setPadding(50, 20, 50, 20);

        final NumberPicker monthPicker = new NumberPicker(this);
        monthPicker.setMinValue(0);
        monthPicker.setMaxValue(11);
        monthPicker.setDisplayedValues(new String[]{
                "January", "February", "March", "April", "May", "June",
                "July", "August", "September", "October", "November", "December"
        });
        monthPicker.setValue(selectedDate.get(Calendar.MONTH));
        monthPicker.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);

        final NumberPicker yearPicker = new NumberPicker(this);
        yearPicker.setMinValue(2000);
        yearPicker.setMaxValue(2100);
        yearPicker.setValue(selectedDate.get(Calendar.YEAR));
        yearPicker.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);

        layout.addView(monthPicker, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        layout.addView(yearPicker, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        builder.setView(layout);

        builder.setPositiveButton("OK", (dialog, which) -> {
            int selectedMonth = monthPicker.getValue();
            int selectedYear = yearPicker.getValue();
            selectedDate.set(selectedYear, selectedMonth, 1);
            updateMonthYearText();
            applyDateFilter();
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());
        builder.show();
    }

    private void applyDateFilter() {
        int selectedMonth = selectedDate.get(Calendar.MONTH);
        int selectedYear = selectedDate.get(Calendar.YEAR);

        List<Transaction> filteredList = new ArrayList<>();
        for (Transaction transaction : transactionList) {
            Calendar transCal = Calendar.getInstance();
            transCal.setTime(transaction.getDate());
            if (transCal.get(Calendar.MONTH) == selectedMonth &&
                    transCal.get(Calendar.YEAR) == selectedYear) {
                filteredList.add(transaction);
            }
        }
        transactionAdapter.setTransactions(filteredList);
    }

    @SuppressLint("SetTextI18n")
    private void loadDataFromFirebase() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) return;

        DocumentReference userRef = db.collection("Users").document(user.getUid());
        if (profileListener != null) profileListener.remove();

        profileListener = userRef.addSnapshotListener((snapshot, e) -> {
            if (e != null || snapshot == null || !snapshot.exists()) {
                balanceAmount.setText("৳ 0.00");
                incomeAmount.setText("৳ 0.00");
                expenseAmount.setText("৳ 0.00");
                greetingText.setText("Hi User!");
                notificationBadge.setVisibility(View.GONE);
                profileImage.setImageResource(R.drawable.ic_profile);
                setTimeBasedGreeting();
                return;
            }

            balanceAmount.setText("৳ " + String.format("%.2f",
                    snapshot.getDouble("balance") != null ? snapshot.getDouble("balance") : 0.0));
            incomeAmount.setText("৳ " + String.format("%.2f",
                    snapshot.getDouble("totalIncome") != null ? snapshot.getDouble("totalIncome") : 0.0));
            expenseAmount.setText("৳ " + String.format("%.2f",
                    snapshot.getDouble("totalExpense") != null ? snapshot.getDouble("totalExpense") : 0.0));

            String fullName = snapshot.getString("name");
            greetingText.setText("Hi " + (fullName != null && !fullName.isEmpty()
                    ? fullName.split(" ")[0]
                    : "User") + "!");

            Boolean hasNewNotification = snapshot.getBoolean("hasNewNotification");
            notificationBadge.setVisibility((hasNewNotification != null && hasNewNotification)
                    ? View.VISIBLE : View.GONE);

            // 🔥 Load profile photo from Firestore (same as UserActivity)
            String profileImageUrl = snapshot.getString("profileImageUrl");
            if (profileImageUrl != null && !profileImageUrl.isEmpty()) {
                Glide.with(this)
                        .load(profileImageUrl)
                        .placeholder(R.drawable.ic_profile)
                        .circleCrop()
                        .into(profileImage);
            } else {
                profileImage.setImageResource(R.drawable.ic_profile);
            }

            setTimeBasedGreeting();
        });
    }

    private void openNotifications() {
        notificationBadge.setVisibility(View.GONE);
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            db.collection("Users").document(user.getUid())
                    .update("hasNewNotification", false)
                    .addOnFailureListener(e -> Log.e("Firebase", "Failed to clear notification", e));
        }
        startActivity(new Intent(this, NotificationActivity.class));
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadDataFromFirebase();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (profileListener != null) profileListener.remove();
    }

    private void setTimeBasedGreeting() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hour >= 5 && hour < 12) subText.setText("Good Morning.");
        else if (hour >= 12 && hour < 17) subText.setText("Good Afternoon.");
        else subText.setText("Good Evening.");
    }
}
