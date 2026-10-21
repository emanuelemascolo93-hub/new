package it.emmeweb.firetviptv;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/** Adapter semplice per liste testuali navigabili col telecomando. */
public class TextAdapter extends RecyclerView.Adapter<TextAdapter.VH> {
    public interface Listener { void onClick(int position); }

    private final List<String> items = new ArrayList<>();
    private final Listener listener;

    public TextAdapter(Listener listener) { this.listener = listener; }

    public void setItems(List<String> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_text, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        h.text.setText(items.get(position));
        h.text.setOnClickListener(v -> {
            int p = h.getBindingAdapterPosition();
            if (p != RecyclerView.NO_POSITION) listener.onClick(p);
        });
    }

    @Override public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        final TextView text;
        VH(View v) { super(v); text = v.findViewById(R.id.text); }
    }
}
