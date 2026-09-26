package com.example.expensetracker;

import java.util.Date;
import java.util.Objects;

public class Transaction {

    private String category;
    private String paymentMethod;
    private double amount;
    private Date date;
    private boolean isExpense;
    private int iconResId;
    private String iconUri;
    private String documentId; // Firestore Document ID

    // REQUIRED EMPTY CONSTRUCTOR FOR FIRESTORE
    public Transaction() {}

    // MAIN CONSTRUCTOR (all fields)
    public Transaction(String category,
                       String paymentMethod,
                       double amount,
                       Date date,
                       boolean isExpense,
                       int iconResId,
                       String iconUri,
                       String documentId) {

        this.category = category;
        this.paymentMethod = paymentMethod;
        this.amount = amount;
        this.date = date;
        this.isExpense = isExpense;
        this.iconResId = iconResId;
        this.iconUri = iconUri;
        this.documentId = documentId;
    }

    // SHORT CONSTRUCTOR (no URI, no documentId)
    public Transaction(String category,
                       String paymentMethod,
                       double amount,
                       Date date,
                       boolean isExpense,
                       int iconResId) {
        this(category, paymentMethod, amount, date, isExpense, iconResId, null, null);
    }

    // OLD COMPATIBLE CONSTRUCTOR (no icon info, no id)
    public Transaction(String category,
                       String paymentMethod,
                       double amount,
                       Date date,
                       boolean isExpense) {
        this(category, paymentMethod, amount, date, isExpense, 0, null, null);
    }

    // GETTERS
    public String getCategory() {
        return category;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public double getAmount() {
        return amount;
    }

    public Date getDate() {
        return date;
    }

    public boolean isExpense() {
        return isExpense;
    }

    public int getIconResId() {
        return iconResId;
    }

    public String getIconUri() {
        return iconUri;
    }

    public String getDocumentId() {
        return documentId;
    }

    // Used by DiffUtil
    public String getId() {
        return documentId;
    }

    // SETTERS
    public void setCategory(String category) {
        this.category = category;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    public void setDate(Date date) {
        this.date = date;
    }

    public void setExpense(boolean expense) {
        isExpense = expense;
    }

    public void setIconResId(int iconResId) {
        this.iconResId = iconResId;
    }

    public void setIconUri(String iconUri) {
        this.iconUri = iconUri;
    }

    public void setDocumentId(String documentId) {
        this.documentId = documentId;
    }

    // HELPER
    public String getType() {
        return isExpense ? "Expense" : "Income";
    }

    public int getNote() {
        return 0;
    }

    // equals WITHOUT icons (important for DiffUtil)
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Transaction)) return false;

        Transaction that = (Transaction) o;

        return Double.compare(that.amount, amount) == 0 &&
                isExpense == that.isExpense &&
                Objects.equals(category, that.category) &&
                Objects.equals(paymentMethod, that.paymentMethod) &&
                Objects.equals(date, that.date) &&
                Objects.equals(documentId, that.documentId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(category, paymentMethod, amount, date, isExpense, documentId);
    }
}
