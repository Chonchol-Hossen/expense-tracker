package com.example.expensetracker;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.EditText;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.expensetracker.adapter.ChatAdapter;
import com.example.expensetracker.model.Message;
import com.google.ai.client.generativeai.GenerativeModel;
import com.google.ai.client.generativeai.java.GenerativeModelFutures;
import com.google.ai.client.generativeai.type.BlockThreshold;
import com.google.ai.client.generativeai.type.Content;
import com.google.ai.client.generativeai.type.GenerateContentResponse;
import com.google.ai.client.generativeai.type.HarmCategory;
import com.google.ai.client.generativeai.type.SafetySetting;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.text.SimpleDateFormat;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;

public class ChatbotActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private EditText inputMessage;
    private ImageView sendBtn;
    private ChatAdapter chatAdapter;
    private ArrayList<Message> messageList;
    private BottomNavigationView bottomNavigationView;
    private GenerativeModel model;
    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private ListenerRegistration balanceListener;
    private ListenerRegistration transactionListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chatbot);

        inputMessage = findViewById(R.id.editTextMessage);
        sendBtn = findViewById(R.id.buttonSend);
        recyclerView = findViewById(R.id.recyclerViewChat);
        bottomNavigationView = findViewById(R.id.bottomNavigationView);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        messageList = new ArrayList<>();
        chatAdapter = new ChatAdapter(messageList);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(chatAdapter);

        // 👇 ADD THE KEYBOARD HANDLER
        handleKeyboardVisibility();

        // Gemini model setup
        List<SafetySetting> safetySettings = new ArrayList<>();
        safetySettings.add(new SafetySetting(HarmCategory.HARASSMENT, BlockThreshold.NONE));
        safetySettings.add(new SafetySetting(HarmCategory.HATE_SPEECH, BlockThreshold.NONE));
        safetySettings.add(new SafetySetting(HarmCategory.SEXUALLY_EXPLICIT, BlockThreshold.NONE));
        safetySettings.add(new SafetySetting(HarmCategory.DANGEROUS_CONTENT, BlockThreshold.NONE));

        model = new GenerativeModel("gemini-2.5-flash", BuildConfig.GEMINI_API_KEY, null, safetySettings);

        sendBtn.setOnClickListener(v -> {
            String msg = inputMessage.getText().toString().trim();
            if (!msg.isEmpty()) {
                inputMessage.setText("");
                sendMessageToGemini(msg, 1);
            } else {
                addBotMessage("Please enter a message.");
            }
        });

        setupBottomNavigationView();
        setupFirestoreListeners();
    }

    // ✔ ADD THIS METHOD
    private void handleKeyboardVisibility() {
        final View rootView = findViewById(android.R.id.content);

        rootView.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            int heightDiff = rootView.getRootView().getHeight() - rootView.getHeight();
            boolean isKeyboardVisible = heightDiff > dpToPx(200);

            if (isKeyboardVisible) {
                bottomNavigationView.setVisibility(View.GONE);
            } else {
                bottomNavigationView.setVisibility(View.VISIBLE);
            }
        });
    }

    // ✔ Helper for DP → PX
    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    private void addBotMessage(String text) {
        messageList.add(new Message(text, false));
        chatAdapter.notifyItemInserted(messageList.size() - 1);
        recyclerView.scrollToPosition(messageList.size() - 1);
    }

    private void addTypingIndicator() {
        messageList.add(new Message("...", false));
        chatAdapter.notifyItemInserted(messageList.size() - 1);
        recyclerView.scrollToPosition(messageList.size() - 1);
    }

    private void removeTypingIndicator() {
        if (!messageList.isEmpty()) {
            Message last = messageList.get(messageList.size() - 1);
            if (last.getMessageText().equals("...")) {
                messageList.remove(messageList.size() - 1);
                chatAdapter.notifyItemRemoved(messageList.size());
            }
        }
    }

    private void setupFirestoreListeners() {
        String userId = auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : null;
        if (userId == null) return;

        balanceListener = db.collection("Users").document(userId)
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null) Log.e("ChatbotActivity", "Balance listener error: " + e.getMessage());
                });

        transactionListener = db.collection("Users").document(userId).collection("transactions")
                .orderBy("date", Query.Direction.DESCENDING).limit(1)
                .addSnapshotListener((querySnapshot, e) -> {
                    if (e != null) Log.e("ChatbotActivity", "Transaction listener error: " + e.getMessage());
                });
    }

    private void sendMessageToGemini(String msg, int retryCount) {
        if (retryCount > 3) {
            addBotMessage("Sorry, I’m having trouble connecting right now. Please try again later.");
            return;
        }

        messageList.add(new Message(msg, true));
        chatAdapter.notifyItemInserted(messageList.size() - 1);
        recyclerView.scrollToPosition(messageList.size() - 1);
        addTypingIndicator();

        String userId = auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : null;
        if (userId == null) {
            removeTypingIndicator();
            addBotMessage("You’re not signed in. Please log in first.");
            return;
        }

        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Asia/Dhaka"));
        String currentTime = now.format(DateTimeFormatter.ofPattern("hh:mm"));

        String persona =
                "You are FinMate, an intelligent and friendly AI assistant built into an Expense Tracker app. "
                        + "You can analyze user transactions, balance, and spending patterns from Firestore data, "
                        + "and also answer general questions. Keep answers concise but helpful.";

        StringBuilder chatHistory = new StringBuilder();
        for (Message m : messageList) {
            chatHistory.append(m.isSentByUser() ? "User: " : "Expense Tracker: ").append(m.getMessageText()).append("\n");
        }

        db.collection("Users").document(userId).get().addOnSuccessListener(documentSnapshot -> {
            double balance = documentSnapshot.getDouble("balance") != null ? documentSnapshot.getDouble("balance") : 0.0;
            double totalIncome = documentSnapshot.getDouble("totalIncome") != null ? documentSnapshot.getDouble("totalIncome") : 0.0;
            double totalExpense = documentSnapshot.getDouble("totalExpense") != null ? documentSnapshot.getDouble("totalExpense") : 0.0;

            StringBuilder context = new StringBuilder();
            context.append(String.format(Locale.getDefault(), "User balance: ৳%.2f\n", balance));
            context.append(String.format(Locale.getDefault(), "Total income: ৳%.2f\n", totalIncome));
            context.append(String.format(Locale.getDefault(), "Total expenses: ৳%.2f\n", totalExpense));

            Date[] range = getDateRangeFromMessage(msg);
            Date startDate = range[0];
            Date endDate = range[1];

            Query transactionQuery = db.collection("Users").document(userId)
                    .collection("transactions")
                    .whereGreaterThanOrEqualTo("date", startDate)
                    .whereLessThanOrEqualTo("date", endDate)
                    .orderBy("date", Query.Direction.DESCENDING)
                    .limit(100);

            transactionQuery.get().addOnSuccessListener(querySnapshot -> {
                if (!querySnapshot.isEmpty()) {
                    context.append(String.format(Locale.getDefault(),
                            "\nTransactions from %s to %s:\n",
                            new SimpleDateFormat("dd MMM, yyyy", Locale.getDefault()).format(startDate),
                            new SimpleDateFormat("dd MMM, yyyy", Locale.getDefault()).format(endDate)));
                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        double amount = doc.getDouble("amount") != null ? doc.getDouble("amount") : 0.0;
                        String category = doc.getString("category") != null ? doc.getString("category") : "Unknown";
                        boolean isExpense = doc.getBoolean("isExpense") != null && doc.getBoolean("isExpense");
                        String type = isExpense ? "Expense" : "Income";
                        String dateStr = doc.getDate("date") != null ?
                                new SimpleDateFormat("dd MMM, yyyy", Locale.getDefault()).format(doc.getDate("date")) : "Unknown";
                        context.append(String.format(Locale.getDefault(), "- %s: ৳%.2f on %s (%s)\n", type, amount, dateStr, category));
                    }
                }

                String prompt = persona + "\n\n" + context
                        + "\nChat history:\n" + chatHistory
                        + "\nUser question: " + msg
                        + "\nCurrent time: " + currentTime
                        + "\n\nAnswer:";

                sendPromptToGemini(prompt, retryCount);
            }).addOnFailureListener(e -> {
                removeTypingIndicator();
                addBotMessage("Could not fetch your transaction data.");
            });
        }).addOnFailureListener(e -> {
            removeTypingIndicator();
            addBotMessage("Error retrieving your data.");
        });
    }

    private void sendPromptToGemini(String fullPrompt, int retryCount) {
        new Thread(() -> {
            try {
                GenerativeModelFutures modelFutures = GenerativeModelFutures.from(model);
                Content content = new Content.Builder().addText(fullPrompt).build();

                ListenableFuture<GenerateContentResponse> future = modelFutures.generateContent(content);
                Futures.addCallback(future, new FutureCallback<>() {
                    @Override
                    public void onSuccess(GenerateContentResponse result) {
                        runOnUiThread(() -> {
                            removeTypingIndicator();
                            String reply = (result != null && result.getText() != null && !result.getText().trim().isEmpty())
                                    ? result.getText().trim()
                                    : "I'm not sure about that. Try rephrasing.";
                            addBotMessage(reply);
                        });
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        Log.e("ChatbotActivity", "Gemini API error: " + t.getMessage());
                        int delay = (int) Math.pow(2, retryCount) * 1000;
                        recyclerView.postDelayed(() -> sendMessageToGemini(fullPrompt, retryCount + 1), delay);
                    }
                }, Executors.newSingleThreadExecutor());
            } catch (Exception e) {
                runOnUiThread(() -> {
                    removeTypingIndicator();
                    addBotMessage("Something went wrong while contacting the AI.");
                });
            }
        }).start();
    }

    private Date[] getDateRangeFromMessage(String message) {
        Calendar start = Calendar.getInstance();
        Calendar end = Calendar.getInstance();
        message = message.toLowerCase(Locale.getDefault());

        if (message.contains("all time") || message.contains("overall") || message.contains("everything")) {
            start.set(2000, Calendar.JANUARY, 1);
        } else if (message.contains("last month")) {
            start.add(Calendar.MONTH, -1);
            start.set(Calendar.DAY_OF_MONTH, 1);
            end.set(Calendar.MONTH, start.get(Calendar.MONTH));
            end.set(Calendar.DAY_OF_MONTH, end.getActualMaximum(Calendar.DAY_OF_MONTH));
        } else if (message.contains("this month")) {
            start.set(Calendar.DAY_OF_MONTH, 1);
        } else if (message.contains("last week")) {
            start.add(Calendar.WEEK_OF_YEAR, -1);
            start.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
            end.add(Calendar.WEEK_OF_YEAR, -1);
            end.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY);
        } else if (message.contains("this year")) {
            start.set(Calendar.DAY_OF_YEAR, 1);
        } else if (message.contains("last year")) {
            start.add(Calendar.YEAR, -1);
            start.set(Calendar.DAY_OF_YEAR, 1);
            end.set(Calendar.YEAR, start.get(Calendar.YEAR));
            end.set(Calendar.MONTH, Calendar.DECEMBER);
            end.set(Calendar.DAY_OF_MONTH, 31);
        } else {
            String[] months = {"january", "february", "march", "april", "may", "june",
                    "july", "august", "september", "october", "november", "december"};
            for (int i = 0; i < months.length; i++) {
                if (message.contains(months[i])) {
                    start.set(Calendar.MONTH, i);
                    start.set(Calendar.DAY_OF_MONTH, 1);
                    end.set(Calendar.MONTH, i);
                    end.set(Calendar.DAY_OF_MONTH, end.getActualMaximum(Calendar.DAY_OF_MONTH));
                    break;
                }
            }
            start.add(Calendar.DAY_OF_YEAR, -90);
        }

        start.set(Calendar.HOUR_OF_DAY, 0);
        end.set(Calendar.HOUR_OF_DAY, 23);
        return new Date[]{start.getTime(), end.getTime()};
    }

    private void setupBottomNavigationView() {
        bottomNavigationView.setLabelVisibilityMode(BottomNavigationView.LABEL_VISIBILITY_UNLABELED);
        bottomNavigationView.setSelectedItemId(R.id.nav_chatbot);

        bottomNavigationView.setOnNavigationItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                startActivity(new Intent(this, HomeActivity.class));
                finish();
                return true;
            } else if (id == R.id.nav_charts) {
                startActivity(new Intent(this, ChartsActivity.class));
                finish();
                return true;
            } else if (id == R.id.nav_chatbot) {
                return true;
            } else if (id == R.id.nav_user) {
                startActivity(new Intent(this, UserActivity.class));
                finish();
                return true;
            }
            return false;
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (balanceListener != null) balanceListener.remove();
        if (transactionListener != null) transactionListener.remove();
    }
}
