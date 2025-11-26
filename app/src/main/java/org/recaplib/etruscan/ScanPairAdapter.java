package org.recaplib.etruscan;

import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class ScanPairAdapter extends RecyclerView.Adapter<ScanPairAdapter.ViewHolder> {

    private final List<ScanPair> scanPairs;

    public ScanPairAdapter(List<ScanPair> scanPairs) {
        this.scanPairs = scanPairs;
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
        ScanPair pair = scanPairs.get(position);
        holder.textView.setText(pair.toString());
    }

    @Override
    public int getItemCount() {
        return scanPairs.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView textView;
        ViewHolder(TextView view) {
            super(view);
            textView = view;
        }
    }
}
