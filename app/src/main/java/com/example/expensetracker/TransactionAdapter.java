package com.example.expensetracker;

import android.content.Context;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TransactionAdapter extends RecyclerView.Adapter<TransactionAdapter.TransactionViewHolder> {

  // --- Listener Interface ---
  public interface OnTransactionActionListener {
    void onEdit(Transaction transaction);
    void onDelete(Transaction transaction);
  }

  private final OnTransactionActionListener listener;
  private final Context context;
  private List<Transaction> transactions = new ArrayList<>();

  public TransactionAdapter(Context context, OnTransactionActionListener listener) {
    this.context = context;
    this.listener = listener;
  }

  // ---------------------------
  // SET TRANSACTIONS (DiffUtil)
  // ---------------------------
  public void setTransactions(List<Transaction> newTransactions) {
    TransactionDiffCallback diffCallback =
            new TransactionDiffCallback(this.transactions, newTransactions);
    DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(diffCallback);

    this.transactions.clear();
    this.transactions.addAll(newTransactions);
    diffResult.dispatchUpdatesTo(this);
  }

  @NonNull
  @Override
  public TransactionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    View v = LayoutInflater.from(context).inflate(R.layout.item_transaction, parent, false);
    return new TransactionViewHolder(v);
  }

  @Override
  public void onBindViewHolder(@NonNull TransactionViewHolder holder, int position) {

    // Reset recycled icon view to avoid wrong images
    Glide.with(context).clear(holder.transactionIcon);
    holder.transactionIcon.setImageDrawable(null);

    Transaction tx = transactions.get(position);

    // Text fields
    holder.categoryText.setText(tx.getCategory() != null ? tx.getCategory() : "Unknown");
    holder.paymentMethodText.setText(tx.getPaymentMethod() != null ? tx.getPaymentMethod() : "");

    Date date = tx.getDate();
    holder.dateText.setText(date != null ? formatDate(date) : "");

    String amt = String.format(Locale.getDefault(), "৳ %.2f", tx.getAmount());
    holder.amountText.setText(amt);

    int color = tx.isExpense()
            ? ContextCompat.getColor(context, R.color.button_red)
            : ContextCompat.getColor(context, R.color.button_green);
    holder.amountText.setTextColor(color);

    // ICON LOADING – icons are already resolved in HomeActivity via master category map
    String iconUri = tx.getIconUri();
    int iconResId = tx.getIconResId();
    int fallbackResId = tx.isExpense() ? R.drawable.ic_other : R.drawable.ic_salary;

    if (iconUri != null && !iconUri.isEmpty()) {
      Glide.with(context)
              .load(Uri.parse(iconUri))
              .placeholder(iconResId != 0 ? iconResId : fallbackResId)
              .error(iconResId != 0 ? iconResId : fallbackResId)
              .into(holder.transactionIcon);
    } else if (iconResId != 0) {
      holder.transactionIcon.setImageResource(iconResId);
    } else {
      holder.transactionIcon.setImageResource(fallbackResId);
    }

    // Long press → popup menu (delete, future: edit)
    holder.itemView.setOnLongClickListener(v -> {
      showPopupMenu(v, tx);
      return true;
    });
  }

  private void showPopupMenu(View view, Transaction transaction) {
    PopupMenu popup = new PopupMenu(context, view);
    popup.getMenuInflater().inflate(R.menu.menu_transaction_actions, popup.getMenu());

    popup.setOnMenuItemClickListener(item -> {
      int itemId = item.getItemId();
      if (itemId == R.id.action_delete) {
        if (listener != null) listener.onDelete(transaction);
        return true;
      }
      // If later you add Edit:
      // else if (itemId == R.id.action_edit) listener.onEdit(transaction);
      return false;
    });

    popup.show();
  }

  @Override
  public int getItemCount() {
    return transactions != null ? transactions.size() : 0;
  }

  // DATE FORMATTER
  private String formatDate(Date date) {
    Calendar today = Calendar.getInstance();
    Calendar txCal = Calendar.getInstance();
    txCal.setTime(date);

    boolean isToday =
            today.get(Calendar.YEAR) == txCal.get(Calendar.YEAR) &&
                    today.get(Calendar.DAY_OF_YEAR) == txCal.get(Calendar.DAY_OF_YEAR);

    if (isToday) return "Today";

    SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
    return sdf.format(date);
  }

  // VIEW HOLDER
  public static class TransactionViewHolder extends RecyclerView.ViewHolder {
    ImageView transactionIcon;
    TextView categoryText, paymentMethodText, amountText, dateText;

    public TransactionViewHolder(@NonNull View itemView) {
      super(itemView);
      transactionIcon = itemView.findViewById(R.id.transactionIcon);
      categoryText = itemView.findViewById(R.id.categoryText);
      paymentMethodText = itemView.findViewById(R.id.paymentMethodText);
      amountText = itemView.findViewById(R.id.amountText);
      dateText = itemView.findViewById(R.id.dateText);
    }
  }

  // DIFF UTIL
  public static class TransactionDiffCallback extends DiffUtil.Callback {
    private final List<Transaction> oldList;
    private final List<Transaction> newList;

    public TransactionDiffCallback(List<Transaction> oldList, List<Transaction> newList) {
      this.oldList = oldList;
      this.newList = newList;
    }

    @Override
    public int getOldListSize() {
      return oldList.size();
    }

    @Override
    public int getNewListSize() {
      return newList.size();
    }

    @Override
    public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
      String oldId = oldList.get(oldItemPosition).getId();
      String newId = newList.get(newItemPosition).getId();
      if (oldId == null || newId == null) return false;
      return oldId.equals(newId);
    }

    @Override
    public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
      return oldList.get(oldItemPosition).equals(newList.get(newItemPosition));
    }
  }
}
