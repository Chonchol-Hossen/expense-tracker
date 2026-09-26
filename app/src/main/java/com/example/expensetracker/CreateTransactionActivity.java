package com.example.expensetracker;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;

import com.example.expensetracker.databinding.ActivityCreateTransactionBinding;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

public class CreateTransactionActivity extends AppCompatActivity {

    private ActivityCreateTransactionBinding binding;
    private CategoryAdapter categoryAdapter;

    private List<CategoryAdapter.Category> expenseCategories;
    private List<CategoryAdapter.Category> incomeCategories;

    // Default categories — MASTER LIST
    private final List<CategoryAdapter.Category> defaultExpenseCategories = Arrays.asList(
            new CategoryAdapter.Category(R.drawable.ic_food, "Food"),
            new CategoryAdapter.Category(R.drawable.ic_transport, "Transport"),
            new CategoryAdapter.Category(R.drawable.ic_shopping, "Shopping"),
            new CategoryAdapter.Category(R.drawable.ic_health, "Health"),
            new CategoryAdapter.Category(R.drawable.ic_entertainment, "Entertainment"),
            new CategoryAdapter.Category(R.drawable.ic_utilities, "Utilities"),
            new CategoryAdapter.Category(R.drawable.ic_rent, "Rent"),
            new CategoryAdapter.Category(R.drawable.ic_education, "Education"),
            new CategoryAdapter.Category(R.drawable.ic_other, "Other")
    );
    private final List<CategoryAdapter.Category> defaultIncomeCategories = Arrays.asList(
            new CategoryAdapter.Category(R.drawable.ic_salary, "Salary"),
            new CategoryAdapter.Category(R.drawable.ic_business, "Business"),
            new CategoryAdapter.Category(R.drawable.ic_investment, "Investment"),
            new CategoryAdapter.Category(R.drawable.ic_freelance, "Freelance"),
            new CategoryAdapter.Category(R.drawable.ic_gift, "Gift"),
            new CategoryAdapter.Category(R.drawable.ic_bonus, "Bonus"),
            new CategoryAdapter.Category(R.drawable.ic_rental, "Rental"),
            new CategoryAdapter.Category(R.drawable.ic_refund, "Refund"),
            new CategoryAdapter.Category(R.drawable.ic_other, "Other")
    );

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private String selectedPaymentMethod = "Cash";

    // EDIT MODE FIELDS
    private String editingTransactionId = null;
    private double originalAmount = 0;
    private boolean originalIsExpense = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCreateTransactionBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Check for edit mode
        Intent intent = getIntent();
        if (intent != null && intent.hasExtra("EDIT_TRANSACTION_ID")) {
            editingTransactionId = intent.getStringExtra("EDIT_TRANSACTION_ID");
            binding.btnSubmit.setText("Update Transaction");
        }

        expenseCategories = new ArrayList<>(defaultExpenseCategories);
        incomeCategories = new ArrayList<>(defaultIncomeCategories);

        setupAmountInput();
        categoryAdapter = new CategoryAdapter(expenseCategories, category -> { });
        setupCategoryRecycler();
        setupToggleButtons();

        loadCategories(); // custom categories
        setupPaymentMethodSpinner();
        setupSubmitButton();
        setupBackButton();

        if (editingTransactionId != null) {
            loadTransactionForEdit(editingTransactionId);
        }
    }

    private CollectionReference getUserCategoriesRef() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) return null;
        return db.collection("Users").document(user.getUid()).collection("categories");
    }

    private void loadCategories() {
        CollectionReference categoriesRef = getUserCategoriesRef();
        if (categoriesRef == null) return;

        categoriesRef.get().addOnSuccessListener(queryDocumentSnapshots -> {
            expenseCategories.clear();
            incomeCategories.clear();
            expenseCategories.addAll(defaultExpenseCategories);
            incomeCategories.addAll(defaultIncomeCategories);

            for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                try {
                    String name = document.getString("name");
                    boolean isExpense = Boolean.TRUE.equals(document.getBoolean("isExpense"));
                    String customUri = document.getString("iconUri");
                    Long resIdLong = document.getLong("iconResId");

                    CategoryAdapter.Category customCategory;

                    if (customUri != null && !customUri.isEmpty()) {
                        customCategory = new CategoryAdapter.Category(customUri, name);
                    } else {
                        int iconResId = resIdLong != null ? resIdLong.intValue() : R.drawable.ic_other;
                        customCategory = new CategoryAdapter.Category(iconResId, name);
                    }

                    if (name != null) {
                        List<CategoryAdapter.Category> targetList = isExpense ? expenseCategories : incomeCategories;
                        boolean isDuplicate = false;
                        for (CategoryAdapter.Category c : targetList) {
                            if (c.label.equals(name)) {
                                isDuplicate = true;
                                break;
                            }
                        }
                        if (!isDuplicate) {
                            targetList.add(customCategory);
                        }
                    }
                } catch (Exception e) {
                    Log.e("CreateTransaction", "Error loading custom category", e);
                }
            }

            if (editingTransactionId == null) {
                if (binding.toggleGroup.getCheckedButtonId() == R.id.btn_expense) {
                    categoryAdapter.updateCategories(expenseCategories);
                } else {
                    categoryAdapter.updateCategories(incomeCategories);
                }
            }

        }).addOnFailureListener(e ->
                Toast.makeText(this, "Failed to load custom categories.", Toast.LENGTH_SHORT).show());
    }

    private void loadTransactionForEdit(String transactionId) {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) return;

        db.collection("Users").document(user.getUid())
                .collection("transactions").document(transactionId)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (!snapshot.exists()) {
                        Toast.makeText(this, "Transaction not found.", Toast.LENGTH_LONG).show();
                        finish();
                        return;
                    }

                    Double amountDouble = snapshot.getDouble("amount");
                    Boolean isExpenseBoolean = snapshot.getBoolean("isExpense");

                    originalAmount = amountDouble != null ? amountDouble : 0;
                    originalIsExpense = isExpenseBoolean != null ? isExpenseBoolean : true;

                    String categoryLabel = snapshot.getString("category");
                    String paymentMethod = snapshot.getString("paymentMethod");

                    binding.inputAmount.setText(String.valueOf(originalAmount));

                    if (originalIsExpense) {
                        binding.toggleGroup.check(R.id.btn_expense);
                        categoryAdapter.updateCategories(expenseCategories);
                    } else {
                        binding.toggleGroup.check(R.id.btn_income);
                        categoryAdapter.updateCategories(incomeCategories);
                    }

                    selectCategoryInAdapter(categoryLabel);
                    selectPaymentMethodInSpinner(paymentMethod);

                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error loading transaction: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    finish();
                });
    }

    private void selectCategoryInAdapter(String categoryLabel) {
        if (categoryLabel == null) return;
        List<CategoryAdapter.Category> list = originalIsExpense ? expenseCategories : incomeCategories;
        int position = -1;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).label.equalsIgnoreCase(categoryLabel)) {
                position = i;
                break;
            }
        }
        if (position != -1) {
            categoryAdapter.setSelectedPosition(position);
            categoryAdapter.notifyDataSetChanged();
        }
    }

    private void selectPaymentMethodInSpinner(String paymentMethod) {
        ArrayAdapter<String> adapter = (ArrayAdapter<String>) binding.spinnerPaymentMethod.getAdapter();
        if (adapter != null && paymentMethod != null) {
            int position = adapter.getPosition(paymentMethod);
            if (position >= 0) {
                binding.spinnerPaymentMethod.setSelection(position);
            }
        }
    }

    private void setupBackButton() {
        binding.iconBack.setOnClickListener(v -> finish());
    }

    private void setupAmountInput() {
        binding.inputAmount.setHint("0.00");
    }

    private void setupCategoryRecycler() {
        binding.recyclerCategories.setLayoutManager(new GridLayoutManager(this, 3));
        binding.recyclerCategories.setAdapter(categoryAdapter);
    }

    private void setupToggleButtons() {
        binding.toggleGroup.check(R.id.btn_expense);
        setToggleState(true);

        binding.toggleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                if (checkedId == R.id.btn_expense) {
                    categoryAdapter.updateCategories(expenseCategories);
                    setToggleState(true);
                } else if (checkedId == R.id.btn_income) {
                    categoryAdapter.updateCategories(incomeCategories);
                    setToggleState(false);
                }
            }
        });
    }

    private void setToggleState(boolean isExpenseSelected) {
        if (isExpenseSelected) {
            binding.btnExpense.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.button_red)));
            binding.btnExpense.setTextColor(ContextCompat.getColor(this, android.R.color.white));

            binding.btnIncome.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.white)));
            binding.btnIncome.setTextColor(ContextCompat.getColor(this, R.color.black));
        } else {
            binding.btnIncome.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.button_green)));
            binding.btnIncome.setTextColor(ContextCompat.getColor(this, android.R.color.white));

            binding.btnExpense.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.white)));
            binding.btnExpense.setTextColor(ContextCompat.getColor(this, R.color.black));
        }
    }

    private CollectionReference getUserWalletsRef() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) return null;
        return db.collection("Users").document(user.getUid()).collection("wallets");
    }

    private void setupPaymentMethodSpinner() {
        List<String> paymentMethods = new ArrayList<>();
        paymentMethods.add("Cash");
        paymentMethods.add("Bank Transfer");
        paymentMethods.add("Debit Card");
        paymentMethods.add("Credit Card");

        CollectionReference walletsRef = getUserWalletsRef();
        if (walletsRef != null) {
            walletsRef.get().addOnSuccessListener(queryDocumentSnapshots -> {
                for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                    String walletName = document.getString("name");
                    if (walletName != null && !paymentMethods.contains(walletName)) {
                        paymentMethods.add(walletName);
                    }
                }
                setupPaymentMethodSpinnerAdapter(paymentMethods);
            }).addOnFailureListener(e -> {
                Toast.makeText(this, "Failed to load custom wallets. Using defaults.", Toast.LENGTH_SHORT).show();
                setupPaymentMethodSpinnerAdapter(paymentMethods);
            });
        } else {
            setupPaymentMethodSpinnerAdapter(paymentMethods);
        }
    }

    private void setupPaymentMethodSpinnerAdapter(List<String> paymentMethods) {
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, paymentMethods);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spinnerPaymentMethod.setAdapter(spinnerAdapter);

        binding.spinnerPaymentMethod.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedPaymentMethod = paymentMethods.get(position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                selectedPaymentMethod = "Cash";
            }
        });

        if (!paymentMethods.isEmpty()) {
            selectedPaymentMethod = paymentMethods.get(0);
        }
    }

    // --- SUBMIT (Add / Edit) ---
    private void setupSubmitButton() {
        binding.btnSubmit.setOnClickListener(v -> {
            CategoryAdapter.Category selectedCategory = categoryAdapter.getSelectedCategory();
            String amountText = binding.inputAmount.getText() != null ? binding.inputAmount.getText().toString().trim() : "";

            if (selectedCategory == null) {
                Toast.makeText(this, "Please select a category", Toast.LENGTH_SHORT).show();
                return;
            }
            if (TextUtils.isEmpty(amountText)) {
                Toast.makeText(this, "Please enter an amount", Toast.LENGTH_SHORT).show();
                return;
            }

            double amount;
            try {
                amount = Double.parseDouble(amountText);
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Invalid amount", Toast.LENGTH_SHORT).show();
                return;
            }
            if (amount <= 0) {
                Toast.makeText(this, "Amount must be greater than zero", Toast.LENGTH_SHORT).show();
                return;
            }

            FirebaseUser user = mAuth.getCurrentUser();
            if (user == null) {
                Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show();
                return;
            }

            DocumentReference userRef = db.collection("Users").document(user.getUid());
            boolean isExpense = binding.toggleGroup.getCheckedButtonId() == R.id.btn_expense;

            Map<String, Object> transactionData = new HashMap<>();
            transactionData.put("category", selectedCategory.label);
            transactionData.put("paymentMethod", selectedPaymentMethod);
            transactionData.put("amount", amount);
            transactionData.put("isExpense", isExpense);
            transactionData.put("iconResId", selectedCategory.iconResId);
            if (selectedCategory.iconUri != null) {
                transactionData.put("iconUri", selectedCategory.iconUri);
            }

            if (editingTransactionId == null) {
                transactionData.put("date", new Date());
                double balanceImpact = isExpense ? -amount : amount;
                runTransactionUpdate(userRef, transactionData, isExpense, balanceImpact, null);
            } else {
                DocumentReference transactionRef = userRef.collection("transactions").document(editingTransactionId);
                double netChange = calculateNetChange(amount, isExpense);
                runTransactionUpdate(userRef, transactionData, isExpense, netChange, transactionRef);
            }
        });
    }

    private double calculateNetChange(double newAmount, boolean newIsExpense) {
        double reverseAmount = originalIsExpense ? originalAmount : -originalAmount;
        double newEffect = newIsExpense ? -newAmount : newAmount;
        return reverseAmount + newEffect;
    }

    private void runTransactionUpdate(DocumentReference userRef, Map<String, Object> transactionData,
                                      boolean newIsExpense, double amountChange, DocumentReference transactionRef) {

        final double newAmount = (Double) transactionData.get("amount");

        db.runTransaction(transaction -> {
            DocumentSnapshot snapshot = transaction.get(userRef);
            Double currentBalance = snapshot.getDouble("balance");
            if (currentBalance == null) currentBalance = 0.0;
            Double currentIncome = snapshot.getDouble("totalIncome");
            if (currentIncome == null) currentIncome = 0.0;
            Double currentExpense = snapshot.getDouble("totalExpense");
            if (currentExpense == null) currentExpense = 0.0;

            double newBalance = currentBalance + amountChange;

            if (editingTransactionId != null) {
                if (originalIsExpense) {
                    currentExpense -= originalAmount;
                } else {
                    currentIncome -= originalAmount;
                }
            }

            if (newIsExpense) {
                currentExpense += newAmount;
            } else {
                currentIncome += newAmount;
            }

            transaction.update(userRef, "balance", newBalance);
            transaction.update(userRef, "totalIncome", currentIncome);
            transaction.update(userRef, "totalExpense", currentExpense);

            if (editingTransactionId == null) {
                userRef.collection("transactions").add(transactionData);
            } else {
                transaction.set(transactionRef, transactionData);
            }

            return null;
        }).addOnSuccessListener(aVoid -> {
            Toast.makeText(this, editingTransactionId == null ? "Transaction saved" : "Transaction updated", Toast.LENGTH_SHORT).show();
            setResult(RESULT_OK);
            finish();
        }).addOnFailureListener(e ->
                Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }
}
