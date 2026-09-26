package com.example.expensetracker;

import android.content.DialogInterface; // NEW IMPORT
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log; // NEW IMPORT
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog; // NEW IMPORT
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.expensetracker.databinding.ActivityWalletsBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

public class WalletsActivity extends AppCompatActivity {

    private ActivityWalletsBinding binding;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private WalletAdapter walletAdapter;

    // List now holds CustomWallet objects
    private List<CustomWallet> walletList;

    // Firestore Collection name for wallets
    private static final String WALLETS_COLLECTION = "wallets";

    // --- NEW: Custom Data Structure ---
    public static class CustomWallet {
        public String documentId; // Firestore ID (null for defaults)
        public String name;
        public boolean isDefault; // Flag to prevent deletion of built-in wallets

        // Constructor for DEFAULT wallets
        public CustomWallet(String name, boolean isDefault) {
            this.documentId = null;
            this.name = name;
            this.isDefault = isDefault;
        }

        // Constructor for CUSTOM wallets (from Firestore)
        public CustomWallet(String documentId, String name) {
            this.documentId = documentId;
            this.name = name;
            this.isDefault = false;
        }
    }

    // Default wallets names (used for checking against custom wallets)
    private final List<String> defaultWalletNames = Arrays.asList(
            "Cash",
            "Bank Transfer",
            "Debit Card",
            "Credit Card"
    );

    // Default wallets objects
    private final List<CustomWallet> defaultWallets = new ArrayList<>();
    {
        for (String name : defaultWalletNames) {
            defaultWallets.add(new CustomWallet(name, true));
        }
    }


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityWalletsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Initialize the list to hold CustomWallet objects
        walletList = new ArrayList<>();

        setupRecyclerView();
        loadWallets();
        setupAddWalletButton();
        setupBackButton();
    }

    private void setupBackButton() {
        binding.iconBack.setOnClickListener(v -> finish());
    }

    private void setupRecyclerView() {
        // Pass 'this' (the Activity) to the adapter
        walletAdapter = new WalletAdapter(walletList, this);
        binding.recyclerWallets.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerWallets.setAdapter(walletAdapter);
    }

    private CollectionReference getUserWalletsRef() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "User not logged in.", Toast.LENGTH_SHORT).show();
            return null;
        }
        // Wallets will be stored under Users/{userId}/wallets
        return db.collection("Users")
                .document(user.getUid())
                .collection(WALLETS_COLLECTION);
    }

    private void loadWallets() {

        if (mAuth.getCurrentUser() == null) return; // <--- FIX

        CollectionReference walletsRef = getUserWalletsRef();
        if (walletsRef == null) return;

        walletsRef.addSnapshotListener((value, error) -> {

            if (mAuth.getCurrentUser() == null) return; // user signed out → ignore everything

            if (error != null) {
                Log.e("WalletsActivity", "Error loading wallets", error);
                return; // Do not show toast after logout
            }

            walletList.clear();
            walletList.addAll(defaultWallets);

            if (value != null) {
                for (QueryDocumentSnapshot document : value) {
                    String documentId = document.getId();
                    String walletName = document.getString("name");

                    if (walletName != null) {
                        if (!defaultWalletNames.contains(walletName)) {
                            walletList.add(new CustomWallet(documentId, walletName));
                        }
                    }
                }
            }

            if (!isFinishing()) {
                walletAdapter.notifyDataSetChanged();
            }
        });
    }

    private void setupAddWalletButton() {
        binding.btnAddWallet.setOnClickListener(v -> {
            String walletNameInput = binding.inputWalletName.getText() != null ?
                    binding.inputWalletName.getText().toString().trim() : "";

            if (TextUtils.isEmpty(walletNameInput)) {
                Toast.makeText(this, "Please enter a wallet name.", Toast.LENGTH_SHORT).show();
                return;
            }

            // Capitalize first letter
            String finalWalletName = walletNameInput.substring(0, 1).toUpperCase() + walletNameInput.substring(1);

            // Check if the wallet name already exists in the displayed list (checking CustomWallet objects)
            if (walletList.stream().anyMatch(w -> w.name.equals(finalWalletName))) {
                Toast.makeText(this, "This wallet already exists.", Toast.LENGTH_SHORT).show();
                return;
            }

            saveWalletToFirestore(finalWalletName);
        });
    }

    private void saveWalletToFirestore(String walletName) {
        CollectionReference walletsRef = getUserWalletsRef();
        if (walletsRef == null) return;

        Map<String, Object> walletData = new HashMap<>();
        walletData.put("name", walletName);
        walletData.put("createdAt", new Date());

        walletsRef.add(walletData)
                .addOnSuccessListener(documentReference -> {
                    Toast.makeText(this, walletName + " added successfully!", Toast.LENGTH_SHORT).show();
                    binding.inputWalletName.setText(""); // Clear input
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error adding wallet: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    Log.e("WalletsActivity", "Error adding wallet", e);
                });
    }

    // --- NEW METHOD: SHOW CONFIRMATION DIALOG ---
    public void showDeleteConfirmationDialog(CustomWallet walletToDelete) {
        // This check is a safeguard, adapter should handle isDefault check
        if (walletToDelete.isDefault) {
            Toast.makeText(this, "Cannot delete default wallets.", Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Delete Wallet")
                .setMessage("Are you sure you want to permanently delete the wallet: " + walletToDelete.name + "?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    // User confirmed deletion
                    deleteCustomWallet(walletToDelete);
                })
                .setNegativeButton("Cancel", (dialog, which) -> {
                    dialog.dismiss();
                })
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

    // --- DELETE CUSTOM WALLET METHOD ---
    public void deleteCustomWallet(CustomWallet walletToDelete) {
        if (walletToDelete.documentId == null) {
            Toast.makeText(this, "Error: Wallet ID missing.", Toast.LENGTH_SHORT).show();
            return;
        }

        CollectionReference walletsRef = getUserWalletsRef();
        if (walletsRef == null) return;

        walletsRef.document(walletToDelete.documentId).delete()
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, walletToDelete.name + " deleted successfully!", Toast.LENGTH_SHORT).show();
                    // SnapshotListener handles list refresh
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Delete failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    Log.e("Firestore", "Error deleting wallet: " + walletToDelete.name, e);
                });
    }


    // --- MODIFIED RecyclerView Adapter for displaying wallets ---
    private static class WalletAdapter extends RecyclerView.Adapter<WalletAdapter.WalletViewHolder> {
        private final List<CustomWallet> wallets; // List of CustomWallet objects
        private final WalletsActivity activity; // Reference to the parent Activity

        // Constructor modified to accept the Activity
        public WalletAdapter(List<CustomWallet> wallets, WalletsActivity activity) {
            this.wallets = wallets;
            this.activity = activity;
        }

        @NonNull
        @Override
        public WalletViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            // Using a simple built-in layout for demonstration
            View view = LayoutInflater.from(parent.getContext()).inflate(android.R.layout.simple_list_item_1, parent, false);
            return new WalletViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull WalletViewHolder holder, int position) {
            CustomWallet wallet = wallets.get(position);
            holder.walletName.setText(wallet.name);

            // --- LONG CLICK LISTENER NOW CALLS THE CONFIRMATION DIALOG ---
            holder.itemView.setOnLongClickListener(v -> {
                if (wallet.isDefault) {
                    Toast.makeText(activity, "Cannot delete default wallets.", Toast.LENGTH_SHORT).show();
                    return true;
                }

                // Show the confirmation popup
                activity.showDeleteConfirmationDialog(wallet);
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return wallets.size();
        }

        static class WalletViewHolder extends RecyclerView.ViewHolder {
            TextView walletName;

            public WalletViewHolder(@NonNull View itemView) {
                super(itemView);
                // Use the built-in Android TextView ID
                walletName = itemView.findViewById(android.R.id.text1);
            }
        }
    }
}