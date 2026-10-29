package it.emmeweb.tvlauncher;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class TileAdapter extends RecyclerView.Adapter<TileAdapter.VH> {

    public interface Listener {
        void onClick(AppItem item);
        void onLongClick(AppItem item, View anchor);
    }

    private final List<AppItem> items;
    private final Listener listener;

    public TileAdapter(List<AppItem> items, Listener listener) {
        this.items = items;
        this.listener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.tile, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        AppItem it = items.get(pos);
        h.icon.setImageDrawable(it.icon);
        h.label.setText(it.label);
        h.itemView.setOnClickListener(v -> listener.onClick(it));
        h.itemView.setOnLongClickListener(v -> { listener.onLongClick(it, v); return true; });
        h.itemView.setOnFocusChangeListener((v, f) ->
                v.animate().scaleX(f ? 1.12f : 1f).scaleY(f ? 1.12f : 1f).setDuration(120).start());
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        ImageView icon; TextView label;
        VH(View v) { super(v); icon = v.findViewById(R.id.icon); label = v.findViewById(R.id.label); }
    }
}
