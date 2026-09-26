package com.example.expensetracker;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class ChartCategoryAdapter extends RecyclerView.Adapter<ChartCategoryAdapter.ChartCategoryViewHolder> {

    private List<CategoryItem> categoryList;

    public ChartCategoryAdapter(List<CategoryItem> categoryList) {
        this.categoryList = categoryList;
    }

    public void updateData(List<CategoryItem> newList) {
        this.categoryList = newList;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ChartCategoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.chart_item_category, parent, false);
        return new ChartCategoryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ChartCategoryViewHolder holder, int position) {
        CategoryItem item = categoryList.get(position);
        holder.categoryName.setText(item.getName());
        holder.categoryAmount.setText(item.getAmount());
        holder.categoryPercentage.setText(item.getPercentage());
        holder.categoryIcon.setImageResource(R.drawable.ic_other);
    }

    @Override
    public int getItemCount() {
        return categoryList.size();
    }

    static class ChartCategoryViewHolder extends RecyclerView.ViewHolder {
        ImageView categoryIcon;
        TextView categoryName, categoryPercentage, categoryAmount;

        ChartCategoryViewHolder(@NonNull View itemView) {
            super(itemView);
            categoryIcon = itemView.findViewById(R.id.categoryIcon);
            categoryName = itemView.findViewById(R.id.categoryName);
            categoryPercentage = itemView.findViewById(R.id.categoryPercent);
            categoryAmount = itemView.findViewById(R.id.categoryAmount);
        }
    }
}