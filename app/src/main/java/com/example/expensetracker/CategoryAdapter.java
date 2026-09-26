package com.example.expensetracker;

import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide; // <-- NEW IMPORT
import com.google.android.material.card.MaterialCardView;
import java.util.List;

public class CategoryAdapter extends RecyclerView.Adapter<CategoryAdapter.CategoryViewHolder> {

    // --- MODIFIED Category class to support both Resource ID (int) and URI (String) ---
    public static class Category {
        public final int iconResId;
        public final String label;
        public final String iconUri; // <-- NEW FIELD for custom file paths

        // Constructor for DEFAULT (Resource ID) categories
        public Category(int iconResId, String label) {
            this.iconResId = iconResId;
            this.label = label;
            this.iconUri = null; // URI is null for built-in icons
        }

        // Constructor for CUSTOM (URI) categories
        public Category(String iconUri, String label) {
            this.iconResId = 0; // Use 0 or another indicator for URI-based icons
            this.label = label;
            this.iconUri = iconUri; // Set the custom file path/URI
        }
    }
    // -------------------------------------------------------------

    private List<Category> categories;
    private int selectedPosition = RecyclerView.NO_POSITION;
    private final OnCategorySelectedListener listener;

    public interface OnCategorySelectedListener {
        void onCategorySelected(Category category);
    }

    public CategoryAdapter(List<Category> categories, OnCategorySelectedListener listener) {
        this.categories = categories;
        this.listener = listener;
    }

    public static class CategoryViewHolder extends RecyclerView.ViewHolder {
        MaterialCardView card;
        ImageView icon;
        TextView label;

        public CategoryViewHolder(@NonNull View itemView) {
            super(itemView);
            card = (MaterialCardView) itemView;
            icon = itemView.findViewById(R.id.category_icon);
            label = itemView.findViewById(R.id.category_label);
        }
    }

    @NonNull
    @Override
    public CategoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_category, parent, false);
        return new CategoryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull final CategoryViewHolder holder, final int position) {
        final Category category = categories.get(position);

        // --- 🔑 FIX: Dynamic Icon Loading Logic ---
        if (category.iconUri != null && !category.iconUri.isEmpty()) {
            // Load custom icon from URI using Glide
            Glide.with(holder.icon.getContext())
                    .load(Uri.parse(category.iconUri))
                    .placeholder(R.drawable.ic_other) // Use a placeholder while loading
                    .into(holder.icon);
        } else {
            // Load default/built-in icon using resource ID
            holder.icon.setImageResource(category.iconResId);
        }
        // ------------------------------------------

        holder.label.setText(category.label);

        // Focus highlight logic
        boolean isSelected = position == selectedPosition;

        int primaryColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.primary);
        int blackColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.black);
        int lightGreyColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.light_grey);

        holder.card.setCardBackgroundColor(isSelected ?
                ContextCompat.getColor(holder.itemView.getContext(), R.color.primary_light) :
                ContextCompat.getColor(holder.itemView.getContext(), android.R.color.white));

        holder.label.setTextColor(isSelected ? primaryColor : blackColor);

        holder.card.setStrokeWidth(isSelected ? 3 : 1);
        holder.card.setStrokeColor(isSelected ? primaryColor : lightGreyColor);


        holder.card.setOnClickListener(v -> {
            int previousPosition = selectedPosition;
            selectedPosition = holder.getAdapterPosition();
            notifyItemChanged(previousPosition);
            notifyItemChanged(selectedPosition);
            if (listener != null) listener.onCategorySelected(category);
        });
    }

    @Override
    public int getItemCount() {
        return categories.size();
    }

    public void updateCategories(List<Category> newCategories) {
        // Reset selection when new category type is loaded (Expense/Income toggle)
        selectedPosition = RecyclerView.NO_POSITION;
        categories = newCategories;
        notifyDataSetChanged();
    }
    public void setSelectedPosition(int position) {
        this.selectedPosition = position;
    }

    public Category getSelectedCategory() {
        if (selectedPosition != RecyclerView.NO_POSITION) {
            return categories.get(selectedPosition);
        }
        return null;
    }

}