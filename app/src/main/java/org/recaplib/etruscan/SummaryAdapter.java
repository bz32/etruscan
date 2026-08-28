package org.recaplib.etruscan;

import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class SummaryAdapter<T> extends RecyclerView.Adapter<SummaryAdapter.ViewHolder> {

    private final List<T> items;

    public SummaryAdapter(List<T> items) {
        this.items = items;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        TextView tv = new TextView(parent.getContext());
        tv.setTextSize(16f);
        tv.setPadding(8, 8, 8, 8);
        return new ViewHolder(tv);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.textView.setText(items.get(position).toString());
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView textView;
        ViewHolder(TextView view) {
            super(view);
            textView = view;
        }
    }
}
