package com.example.expensetracker;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.expensetracker.databinding.ActivityCategoriesBinding;
import com.google.android.material.tabs.TabLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CategoriesActivity extends AppCompatActivity {

    private ActivityCategoriesBinding binding;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private CategoryListAdapter categoryAdapter;

    private static final int PICK_IMAGE_REQUEST = 1;
    private static final int PERMISSION_REQUEST_CODE = 100;

    private boolean isUploadingIcon = false;

    public static class CustomCategory {
        public String documentId;
        public String name;
        public int iconResId;
        public String iconUri;
        public boolean isExpense;
        public boolean isDefault;

        public CustomCategory(String name, int iconResId, boolean isExpense, boolean isDefault) {
            this.documentId = null;
            this.name = name;
            this.iconResId = iconResId;
            this.iconUri = null;
            this.isExpense = isExpense;
            this.isDefault = isDefault;
        }

        public CustomCategory(String documentId, String name, String iconUri, int iconResId, boolean isExpense) {
            this.documentId = documentId;
            this.name = name;
            this.iconResId = iconResId;
            this.iconUri = iconUri;
            this.isExpense = isExpense;
            this.isDefault = false;
        }
    }

    private final List<CustomCategory> categoryList = new ArrayList<>();
    private final List<CustomCategory> filteredCategoryList = new ArrayList<>();

    private final List<CustomCategory> expenseDefaults = Arrays.asList(
            new CustomCategory("Food", R.drawable.ic_food, true, true),
            new CustomCategory("Transport", R.drawable.ic_transport, true, true),
            new CustomCategory("Shopping", R.drawable.ic_shopping, true, true),
            new CustomCategory("Health", R.drawable.ic_health, true, true),
            new CustomCategory("Entertainment", R.drawable.ic_entertainment, true, true),
            new CustomCategory("Utilities", R.drawable.ic_utilities, true, true),
            new CustomCategory("Rent", R.drawable.ic_rent, true, true),
            new CustomCategory("Education", R.drawable.ic_education, true, true),
            new CustomCategory("Other", R.drawable.ic_other, true, true)
    );

    private final List<CustomCategory> incomeDefaults = Arrays.asList(
            new CustomCategory("Salary", R.drawable.ic_salary, false, true),
            new CustomCategory("Business", R.drawable.ic_business, false, true),
            new CustomCategory("Investment", R.drawable.ic_investment, false, true),
            new CustomCategory("Freelance", R.drawable.ic_freelance, false, true),
            new CustomCategory("Gift", R.drawable.ic_gift, false, true),
            new CustomCategory("Bonus", R.drawable.ic_bonus, false, true),
            new CustomCategory("Rental", R.drawable.ic_rental, false, true),
            new CustomCategory("Refund", R.drawable.ic_refund, false, true),
            new CustomCategory("Other", R.drawable.ic_other, false, true)
    );

    private boolean isExpenseSelected = true;
    private int selectedIconResId = R.drawable.ic_other;
    private String selectedIconUri = null;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCategoriesBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        setupRecyclerView();
        setupTabLayout();
        setupAddCategoryButton();
        setupIconSelector();
        setupBackButton();

        loadCategoriesFromFirestore();
    }

    private void setupBackButton() {
        binding.iconBack.setOnClickListener(v -> finish());
    }

    private void setupRecyclerView() {
        categoryAdapter = new CategoryListAdapter(filteredCategoryList, this);
        binding.recyclerCategories.setLayoutManager(new GridLayoutManager(this, 3));
        binding.recyclerCategories.setAdapter(categoryAdapter);
    }

    private void setupTabLayout() {
        binding.tabCategoryType.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                isExpenseSelected = tab.getPosition() == 0;
                filterCategories(isExpenseSelected);
            }

            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });

        binding.tabCategoryType.selectTab(binding.tabCategoryType.getTabAt(0));
    }

    private void setupIconSelector() {
        binding.iconSelectedCategory.setOnClickListener(v -> checkAndRequestPermission());
    }

    private void checkAndRequestPermission() {
        String permissionToRequest;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionToRequest = Manifest.permission.READ_MEDIA_IMAGES;
        } else {
            permissionToRequest = Manifest.permission.READ_EXTERNAL_STORAGE;
        }

        if (ContextCompat.checkSelfPermission(this, permissionToRequest)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{permissionToRequest}, PERMISSION_REQUEST_CODE);
        } else {
            openFilePicker();
        }
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(Intent.createChooser(intent, "Select Icon"), PICK_IMAGE_REQUEST);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSION_REQUEST_CODE &&
                grantResults.length > 0 &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            openFilePicker();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_IMAGE_REQUEST &&
                resultCode == RESULT_OK &&
                data != null &&
                data.getData() != null) {

            Uri localUri = data.getData();

            Glide.with(this)
                    .load(localUri)
                    .placeholder(R.drawable.ic_other)
                    .into(binding.iconSelectedCategory);

            uploadIconToFirebase(localUri);
        }
    }

    private void uploadIconToFirebase(Uri localUri) {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) return;

        isUploadingIcon = true;

        StorageReference iconRef = FirebaseStorage.getInstance()
                .getReference()
                .child("category_icons/" + user.getUid() + "/" + System.currentTimeMillis() + ".jpg");

        try {
            InputStream input = getContentResolver().openInputStream(localUri);
            Bitmap bmp = BitmapFactory.decodeStream(input);
            input.close();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bmp.compress(Bitmap.CompressFormat.JPEG, 80, baos);
            byte[] data = baos.toByteArray();

            iconRef.putBytes(data)
                    .addOnSuccessListener(t -> iconRef.getDownloadUrl().addOnSuccessListener(url -> {
                        selectedIconUri = url.toString();
                        selectedIconResId = 0;
                        isUploadingIcon = false;
                        Toast.makeText(this, "Icon uploaded!", Toast.LENGTH_SHORT).show();
                    }))
                    .addOnFailureListener(e -> {
                        isUploadingIcon = false;
                        Toast.makeText(this, "Upload failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    });

        } catch (Exception e) {
            isUploadingIcon = false;
            Toast.makeText(this, "Image error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private CollectionReference getUserCategoriesRef() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) return null;

        return db.collection("Users")
                .document(user.getUid())
                .collection("categories");
    }

    private void loadCategoriesFromFirestore() {

        if (mAuth.getCurrentUser() == null) return;

        CollectionReference categoriesRef = getUserCategoriesRef();
        if (categoriesRef == null) return;

        categoriesRef.addSnapshotListener((value, error) -> {

            if (mAuth.getCurrentUser() == null) return;   // <-- FIX 1

            if (error != null) {
                Log.e("Categories", "Error listening categories", error);
                return; // <-- FIX 2 (NO TOAST)
            }

            categoryList.clear();
            categoryList.addAll(expenseDefaults);
            categoryList.addAll(incomeDefaults);

            if (value != null) {
                for (QueryDocumentSnapshot doc : value) {
                    try {
                        String id = doc.getId();
                        String name = doc.getString("name");
                        boolean isExpense = Boolean.TRUE.equals(doc.getBoolean("isExpense"));
                        String uri = doc.getString("iconUri");
                        int resId = doc.getLong("iconResId") != null ? doc.getLong("iconResId").intValue() : 0;

                        if (name == null) continue;

                        boolean duplicate = false;
                        for (CustomCategory c : categoryList) {
                            if (c.name.equals(name) && c.isExpense == isExpense) {
                                duplicate = true;
                                break;
                            }
                        }

                        if (!duplicate) {
                            if (uri != null && !uri.isEmpty()) {
                                categoryList.add(new CustomCategory(id, name, uri, 0, isExpense));
                            } else if (resId != 0) {
                                categoryList.add(new CustomCategory(id, name, null, resId, isExpense));
                            }
                        }

                    } catch (Exception e) {
                        Log.e("Categories", "Parse error", e);
                    }
                }
            }

            filterCategories(isExpenseSelected);
        });
    }

    private void filterCategories(boolean showExpense) {
        filteredCategoryList.clear();
        for (CustomCategory c : categoryList) {
            if (c.isExpense == showExpense)
                filteredCategoryList.add(c);
        }

        if (!isFinishing()) {
            categoryAdapter.notifyDataSetChanged();
        }
    }

    private void setupAddCategoryButton() {
        binding.btnAddCategory.setOnClickListener(v -> {
            String input = binding.inputCategoryName.getText() != null
                    ? binding.inputCategoryName.getText().toString().trim()
                    : "";

            if (TextUtils.isEmpty(input)) {
                Toast.makeText(this, "Enter category name", Toast.LENGTH_SHORT).show();
                return;
            }

            if (isUploadingIcon) {
                Toast.makeText(this, "Wait… icon is uploading", Toast.LENGTH_SHORT).show();
                return;
            }

            if (selectedIconResId == R.drawable.ic_other && selectedIconUri == null) {
                Toast.makeText(this, "Select an icon", Toast.LENGTH_SHORT).show();
                return;
            }

            String finalName = input.substring(0, 1).toUpperCase() + input.substring(1);

            for (CustomCategory c : categoryList) {
                if (c.name.equals(finalName) && c.isExpense == isExpenseSelected) {
                    Toast.makeText(this, "Category already exists", Toast.LENGTH_SHORT).show();
                    return;
                }
            }

            saveCategoryToFirestore(finalName, selectedIconResId, selectedIconUri, isExpenseSelected);
        });
    }

    private void saveCategoryToFirestore(String name, int resId, String uri, boolean isExpense) {

        CollectionReference ref = getUserCategoriesRef();
        if (ref == null) return;

        Map<String, Object> map = new HashMap<>();
        map.put("name", name);
        map.put("isExpense", isExpense);
        map.put("createdAt", new Date());
        map.put("iconUri", uri != null ? uri : null);
        map.put("iconResId", uri == null ? resId : 0);

        ref.add(map)
                .addOnSuccessListener(doc -> {
                    Toast.makeText(this, "Added!", Toast.LENGTH_SHORT).show();
                    binding.inputCategoryName.setText("");
                    selectedIconUri = null;
                    selectedIconResId = R.drawable.ic_other;
                    binding.iconSelectedCategory.setImageResource(selectedIconResId);
                });
    }

    public void showDeleteConfirmationDialog(CustomCategory category) {
        if (category.isDefault) {
            Toast.makeText(this, "Cannot delete default category", Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Delete?")
                .setMessage("Delete " + category.name + "?")
                .setPositiveButton("Delete", (d, w) -> deleteCustomCategory(category))
                .setNegativeButton("Cancel", (d, w) -> d.dismiss())
                .show();
    }

    public void deleteCustomCategory(CustomCategory category) {
        if (category.documentId == null) return;

        CollectionReference ref = getUserCategoriesRef();
        if (ref == null) return;

        ref.document(category.documentId).delete();
    }

    private static class CategoryListAdapter extends RecyclerView.Adapter<CategoryListAdapter.CategoryViewHolder> {

        private final List<CustomCategory> categories;
        private final CategoriesActivity activity;

        public CategoryListAdapter(List<CustomCategory> categories, CategoriesActivity activity) {
            this.categories = categories;
            this.activity = activity;
        }

        @NonNull
        @Override
        public CategoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_category, parent, false);
            return new CategoryViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull CategoryViewHolder holder, int pos) {
            CustomCategory c = categories.get(pos);
            holder.categoryName.setText(c.name);

            if (c.iconUri != null) {
                Glide.with(activity).load(c.iconUri).into(holder.categoryIcon);
            } else {
                holder.categoryIcon.setImageResource(c.iconResId);
            }

            holder.itemView.setOnLongClickListener(v -> {
                if (!c.isDefault) activity.showDeleteConfirmationDialog(c);
                else Toast.makeText(activity, "Default category cannot be deleted", Toast.LENGTH_SHORT).show();
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return categories.size();
        }

        static class CategoryViewHolder extends RecyclerView.ViewHolder {
            TextView categoryName;
            ImageView categoryIcon;

            public CategoryViewHolder(@NonNull View itemView) {
                super(itemView);
                categoryName = itemView.findViewById(R.id.category_label);
                categoryIcon = itemView.findViewById(R.id.category_icon);
            }
        }
    }
}
