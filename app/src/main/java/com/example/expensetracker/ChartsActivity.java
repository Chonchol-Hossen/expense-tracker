package com.example.expensetracker;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ChartsActivity extends AppCompatActivity {

    private PieChart incomePieChart, expensePieChart;
    private Spinner chartTypeSpinner;
    private MaterialButtonToggleGroup typeToggleGroup;
    private MaterialButton btnToggleExpense, btnToggleIncome;
    private LinearLayout categoryCardsContainer;
    private BottomNavigationView bottomNavigationView;

    // ✅ Budget TextView (ADDED)
    private TextView tvSetBudget;

    private FirebaseFirestore db;
    private String userId;

    // ✅ Budget Prefs (ADDED)
    private static final String PREF_BUDGET = "budget_prefs";

    // CATEGORY SUMMARY (Updated with displayName)
    private static class CategorySummary {
        float totalAmount = 0f;
        int iconResId = R.drawable.ic_other;
        String iconUri = null;
        String displayName;   // original category name used for UI
    }

    // Store transaction totals
    private Map<String, CategorySummary> incomeCategoryData = new HashMap<>();
    private Map<String, CategorySummary> expenseCategoryData = new HashMap<>();

    // Icon storage
    private Map<String, CategoryData> customCategoryDataMap = new HashMap<>();

    // Icon data holder
    private static class CategoryData {
        int iconResId = R.drawable.ic_other;
        String iconUri = null;
        boolean isExpense;
    }

    private final int[] CHART_COLORS = new int[]{
            Color.rgb(105, 182, 255),
            Color.rgb(255, 138, 101),
            Color.rgb(141, 221, 149),
            Color.rgb(255, 238, 88),
            Color.rgb(179, 157, 219),
            Color.rgb(129, 199, 132),
            Color.rgb(255, 87, 34),
            Color.rgb(3, 169, 244)
    };

    private int colorBlack;
    private int colorGrey;
    private int colorActive;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_charts);

        colorBlack = ContextCompat.getColor(this, R.color.black);
        colorGrey = ContextCompat.getColor(this, R.color.light_grey);
        colorActive = ContextCompat.getColor(this, R.color.button_green);

        incomePieChart = findViewById(R.id.incomePieChart);
        expensePieChart = findViewById(R.id.expensePieChart);
        chartTypeSpinner = findViewById(R.id.chartTypeSpinner);
        typeToggleGroup = findViewById(R.id.typeToggleGroup);
        btnToggleExpense = findViewById(R.id.btnToggleExpense);
        btnToggleIncome = findViewById(R.id.btnToggleIncome);
        categoryCardsContainer = findViewById(R.id.categoryCardsContainer);
        bottomNavigationView = findViewById(R.id.bottomNavigationView);

        // ✅ ADDED
        tvSetBudget = findViewById(R.id.tvSetBudget);
        tvSetBudget.setOnClickListener(v -> showBudgetDialog());

        db = FirebaseFirestore.getInstance();
        userId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        setupSpinners();
        setupToggleGroupAppearance();
        setupBottomNavigationView();

        // Default selection → Expense
        typeToggleGroup.check(R.id.btnToggleExpense);
        updateButtonAppearance(R.id.btnToggleExpense);

        fetchDataFromFirebase("Monthly");
    }

    // -------------------------------------------------------------------
    // ✅ BUDGET FEATURE (ADDED ONLY - DOES NOT CHANGE YOUR EXISTING LOGIC)
    // -------------------------------------------------------------------

    private void showBudgetDialog() {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setHint("Enter budget amount");

        new MaterialAlertDialogBuilder(this)
                .setTitle("Set Expense Budget")
                .setView(input)
                .setPositiveButton("Save", (dialog, which) -> {
                    String value = input.getText() != null ? input.getText().toString().trim() : "";
                    if (!value.isEmpty()) {
                        try {
                            float amount = Float.parseFloat(value);
                            if (amount > 0) {
                                saveBudget(amount);
                            } else {
                                removeBudget();
                            }
                        } catch (Exception e) {
                            // ignore invalid input safely
                        }
                        updateBudgetUI();
                    }
                })
                .setNeutralButton("Remove", (dialog, which) -> {
                    removeBudget();
                    updateBudgetUI();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void saveBudget(float amount) {
        SharedPreferences sp = getSharedPreferences(PREF_BUDGET, MODE_PRIVATE);
        sp.edit().putFloat(getBudgetKey(), amount).apply();
    }

    private void removeBudget() {
        SharedPreferences sp = getSharedPreferences(PREF_BUDGET, MODE_PRIVATE);
        sp.edit().remove(getBudgetKey()).apply();
    }

    private String getBudgetKey() {
        // Budget is tied to current selected filter (Monthly/Weekly/Yearly/Daily)
        String filter = "monthly";
        if (chartTypeSpinner != null && chartTypeSpinner.getSelectedItem() != null) {
            filter = chartTypeSpinner.getSelectedItem().toString().toLowerCase(Locale.ROOT);
        }
        return "budget_" + filter;
    }

    private float getTotalExpense() {
        float total = 0f;
        for (CategorySummary s : expenseCategoryData.values()) {
            total += s.totalAmount;
        }
        return total;
    }

    private void updateBudgetUI() {
        if (tvSetBudget == null) return;

        SharedPreferences sp = getSharedPreferences(PREF_BUDGET, MODE_PRIVATE);
        float budget = sp.getFloat(getBudgetKey(), -1f);

        if (budget <= 0f) {
            tvSetBudget.setText("Set Budget");
            tvSetBudget.setTextColor(ContextCompat.getColor(this, R.color.primary));
            return;
        }

        float spent = getTotalExpense();
        tvSetBudget.setText(String.format(Locale.getDefault(), "%.0f / %.0f", spent, budget));

        if (spent > budget) {
            // exceeded
            tvSetBudget.setTextColor(ContextCompat.getColor(this, R.color.button_red));
        } else {
            // within
            tvSetBudget.setTextColor(ContextCompat.getColor(this, R.color.button_green));
        }
    }

    // -------------------------------------------------------------------
    // MASTER CATEGORY DATA LOADING + MAPPING
    // -------------------------------------------------------------------

    private void loadCategoryIconMappings(Runnable onComplete) {
        customCategoryDataMap.clear();

        addDefaultCategoriesToMap(true);  // Default expenses
        addDefaultCategoriesToMap(false); // Default incomes

        db.collection("Users").document(userId).collection("categories")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {

                        String name = doc.getString("name");
                        if (name == null) continue;

                        String key = name.toLowerCase(Locale.ROOT).trim();

                        CategoryData data = new CategoryData();
                        data.isExpense = Boolean.TRUE.equals(doc.getBoolean("isExpense"));

                        String uri = doc.getString("iconUri");
                        Long res = doc.getLong("iconResId");

                        if (uri != null && !uri.isEmpty()) {
                            data.iconUri = uri;
                            data.iconResId = 0;
                        } else if (res != null) {
                            data.iconResId = res.intValue();
                        }

                        customCategoryDataMap.put(key, data);
                    }

                    onComplete.run();
                })
                .addOnFailureListener(e -> onComplete.run());
    }

    private void addDefaultCategoriesToMap(boolean isExpense) {

        List<Transaction> defaults = isExpense
                ? Arrays.asList(
                new Transaction("Food", null, 0, null, true, R.drawable.ic_food),
                new Transaction("Transport", null, 0, null, true, R.drawable.ic_transport),
                new Transaction("Shopping", null, 0, null, true, R.drawable.ic_shopping),
                new Transaction("Health", null, 0, null, true, R.drawable.ic_health),
                new Transaction("Entertainment", null, 0, null, true, R.drawable.ic_entertainment),
                new Transaction("Utilities", null, 0, null, true, R.drawable.ic_utilities),
                new Transaction("Rent", null, 0, null, true, R.drawable.ic_rent),
                new Transaction("Education", null, 0, null, true, R.drawable.ic_education),
                new Transaction("Other", null, 0, null, true, R.drawable.ic_other)
        )
                : Arrays.asList(
                new Transaction("Salary", null, 0, null, false, R.drawable.ic_salary),
                new Transaction("Business", null, 0, null, false, R.drawable.ic_business),
                new Transaction("Investment", null, 0, null, false, R.drawable.ic_investment),
                new Transaction("Freelance", null, 0, null, false, R.drawable.ic_freelance),
                new Transaction("Gift", null, 0, null, false, R.drawable.ic_gift),
                new Transaction("Bonus", null, 0, null, false, R.drawable.ic_bonus),
                new Transaction("Rental", null, 0, null, false, R.drawable.ic_rental),
                new Transaction("Refund", null, 0, null, false, R.drawable.ic_refund),
                new Transaction("Other", null, 0, null, false, R.drawable.ic_other)
        );

        for (Transaction tx : defaults) {
            String key = tx.getCategory().toLowerCase(Locale.ROOT).trim();

            if (!customCategoryDataMap.containsKey(key)) {
                CategoryData data = new CategoryData();
                data.isExpense = isExpense;
                data.iconResId = tx.getIconResId();
                customCategoryDataMap.put(key, data);
            }
        }
    }

    // -------------------------------------------------------------------
    // FETCH TRANSACTIONS + ASSIGN ICONS CORRECTLY
    // -------------------------------------------------------------------

    private void fetchDataFromFirebase(String type) {

        loadCategoryIconMappings(() -> {

            long startTime = getTimeFilterStart(type);

            db.collection("Users").document(userId)
                    .collection("transactions")
                    .whereGreaterThanOrEqualTo("date", new Date(startTime))
                    .get()
                    .addOnSuccessListener(task -> {

                        incomeCategoryData.clear();
                        expenseCategoryData.clear();

                        for (DocumentSnapshot doc : task.getDocuments()) {

                            String category = doc.getString("category");
                            if (category == null) continue;

                            boolean isExpense = Boolean.TRUE.equals(doc.getBoolean("isExpense"));

                            double amt = doc.getDouble("amount") != null
                                    ? doc.getDouble("amount")
                                    : 0;

                            String key = category.toLowerCase(Locale.ROOT).trim();

                            Map<String, CategorySummary> map =
                                    isExpense ? expenseCategoryData : incomeCategoryData;

                            CategorySummary summary =
                                    map.getOrDefault(key, new CategorySummary());

                            summary.totalAmount += amt;

                            // Store real category name for UI
                            if (summary.displayName == null)
                                summary.displayName = category;

                            // Assign icons
                            CategoryData icon = customCategoryDataMap.get(key);
                            if (icon != null) {
                                summary.iconResId = icon.iconResId;
                                summary.iconUri = icon.iconUri;
                            }

                            map.put(key, summary);
                        }

                        loadPieChartData(incomePieChart, incomeCategoryData, "Income");
                        loadPieChartData(expensePieChart, expenseCategoryData, "Expense");

                        updateCategoryCards(typeToggleGroup.getCheckedButtonId() == R.id.btnToggleIncome);

                        // ✅ ADDED: Refresh budget after data load (no behavior change)
                        updateBudgetUI();
                    });
        });
    }

    // -------------------------------------------------------------------
    // PIE CHART
    // -------------------------------------------------------------------

    private void loadPieChartData(PieChart chart, Map<String, CategorySummary> dataMap, String title) {
        List<PieEntry> entries = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();

        for (Map.Entry<String, CategorySummary> e : dataMap.entrySet()) {
            if (e.getValue().totalAmount > 0) {
                entries.add(new PieEntry(e.getValue().totalAmount, e.getValue().displayName));
            }
        }

        if (entries.isEmpty()) {
            chart.clear();
            chart.setNoDataText("No " + title + " data found");
            chart.invalidate();
            return;
        }

        int i = 0;
        for (PieEntry entry : entries) {
            colors.add(CHART_COLORS[i % CHART_COLORS.length]);
            i++;
        }

        PieDataSet dataSet = new PieDataSet(entries, title);
        dataSet.setColors(colors);
        dataSet.setSliceSpace(2f);
        dataSet.setValueTextColor(Color.WHITE);
        dataSet.setValueTextSize(12f);

        PieData data = new PieData(dataSet);
        chart.setData(data);
        chart.setUsePercentValues(true);
        chart.getDescription().setEnabled(false);
        chart.setDrawEntryLabels(false);
        chart.setDrawHoleEnabled(true);
        chart.setHoleRadius(55f);
        chart.setTransparentCircleRadius(60f);
        chart.setCenterText(title);
        chart.setCenterTextSize(18f);
        chart.getLegend().setEnabled(false);
        chart.invalidate();
    }

    // -------------------------------------------------------------------
    // CATEGORY CARDS
    // -------------------------------------------------------------------

    private void updateCategoryCards(boolean showIncome) {

        categoryCardsContainer.removeAllViews();

        Map<String, CategorySummary> dataMap =
                showIncome ? incomeCategoryData : expenseCategoryData;

        if (dataMap.isEmpty()) {
            TextView t = new TextView(this);
            t.setText("No data available");
            t.setTextColor(Color.GRAY);
            int pad = (int) (16 * getResources().getDisplayMetrics().density);
            t.setPadding(pad, pad, pad, pad);
            categoryCardsContainer.addView(t);
            return;
        }

        float total = 0;
        for (CategorySummary s : dataMap.values()) total += s.totalAmount;

        int colorIndex = 0;

        for (Map.Entry<String, CategorySummary> entry : dataMap.entrySet()) {

            CategorySummary s = entry.getValue();

            View view = getLayoutInflater().inflate(R.layout.chart_item_category, categoryCardsContainer, false);

            CardView card = view.findViewById(R.id.card_root);
            ImageView icon = view.findViewById(R.id.categoryIcon);
            TextView name = view.findViewById(R.id.categoryName);
            TextView amount = view.findViewById(R.id.categoryAmount);
            TextView percent = view.findViewById(R.id.categoryPercent);

            if (s.iconUri != null && !s.iconUri.isEmpty()) {
                Glide.with(this).load(Uri.parse(s.iconUri)).into(icon);
            } else {
                icon.setImageResource(s.iconResId);
            }

            name.setText(s.displayName);

            float p = (s.totalAmount / total) * 100f;
            amount.setText("৳" + new DecimalFormat("0.00").format(s.totalAmount));
            percent.setText(String.format(Locale.getDefault(), "%.1f%%", p));

            int bg = CHART_COLORS[colorIndex % CHART_COLORS.length];
            card.setCardBackgroundColor(adjustAlpha(bg, 0.2f));

            colorIndex++;

            categoryCardsContainer.addView(view);
        }
    }

    private int adjustAlpha(int color, float factor) {
        int alpha = Math.round(Color.alpha(color) * factor);
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    private void setupToggleGroupAppearance() {
        // Default state
        updateButtonAppearance(R.id.btnToggleExpense);

        typeToggleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;

            // Update colors
            updateButtonAppearance(checkedId);

            // Update cards: Income shown when Income selected
            boolean showIncome = (checkedId == R.id.btnToggleIncome);
            updateCategoryCards(showIncome);

            // ✅ ADDED: keep budget display updated when toggle changes (no condition changes)
            updateBudgetUI();
        });
    }

    private void updateButtonAppearance(int checkedId) {
        // Reset both buttons to neutral
        btnToggleExpense.setTextColor(colorBlack);
        btnToggleExpense.setBackgroundTintList(ColorStateList.valueOf(colorGrey));

        btnToggleIncome.setTextColor(colorBlack);
        btnToggleIncome.setBackgroundTintList(ColorStateList.valueOf(colorGrey));

        // Active color depends on which is selected
        if (checkedId == R.id.btnToggleExpense) {
            colorActive = ContextCompat.getColor(this, R.color.button_red);
        } else {
            colorActive = ContextCompat.getColor(this, R.color.button_green);
        }

        MaterialButton checkedButton = findViewById(checkedId);
        if (checkedButton != null) {
            checkedButton.setTextColor(ContextCompat.getColor(this, R.color.white));
            checkedButton.setBackgroundTintList(ColorStateList.valueOf(colorActive));
        }
    }

    // -------------------------------------------------------------------
    // SPINNER + NAV
    // -------------------------------------------------------------------

    private void setupSpinners() {
        String[] filters = getResources().getStringArray(R.array.time_filters);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                R.layout.spinner_selected_item,
                filters
        );
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item);

        chartTypeSpinner.setAdapter(adapter);
        chartTypeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(
                    AdapterView<?> parent, View view, int position, long id) {
                fetchDataFromFirebase(parent.getItemAtPosition(position).toString());

                // ✅ ADDED: budget key depends on filter selection
                updateBudgetUI();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private long getTimeFilterStart(String type) {
        Calendar c = Calendar.getInstance();

        switch (type) {
            case "Daily":
                c.add(Calendar.DAY_OF_YEAR, -1);
                break;
            case "Weekly":
                c.add(Calendar.WEEK_OF_YEAR, -1);
                break;
            case "Yearly":
                c.add(Calendar.YEAR, -1);
                break;
            default:
                c.add(Calendar.MONTH, -1);
                break;
        }

        return c.getTimeInMillis();
    }

    private void setupBottomNavigationView() {
        bottomNavigationView.setLabelVisibilityMode(BottomNavigationView.LABEL_VISIBILITY_UNLABELED);

        bottomNavigationView.setOnItemSelectedListener(item -> {
            int id = item.getItemId();

            if (id == R.id.nav_home) {
                startActivity(new Intent(this, HomeActivity.class));
                finish();
                return true;
            }
            if (id == R.id.nav_charts) return true;
            if (id == R.id.nav_chatbot) {
                startActivity(new Intent(this, ChatbotActivity.class));
                finish();
                return true;
            }
            if (id == R.id.nav_user) {
                startActivity(new Intent(this, UserActivity.class));
                finish();
                return true;
            }
            return false;
        });

        bottomNavigationView.setSelectedItemId(R.id.nav_charts);
    }
}
