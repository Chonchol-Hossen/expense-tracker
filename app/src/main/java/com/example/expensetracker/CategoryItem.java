package com.example.expensetracker;

public class CategoryItem {
    private String name;
    private String amount;
    private String percentage;

    public CategoryItem(String name, String amount, String percentage) {
        this.name = name;
        this.amount = amount;
        this.percentage = percentage;
    }

    public String getName() { return name; }
    public String getAmount() { return amount; }
    public String getPercentage() { return percentage; }
}
