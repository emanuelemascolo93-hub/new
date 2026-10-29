package it.emmeweb.tvlauncher;

import android.graphics.drawable.Drawable;
import android.widget.FrameLayout;
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

    private static final int[] TILE_W = {120, 150, 190};
    private static final int[] ICON_H = {58, 74, 96};

    private final List<AppItem> items;
    private final Listener listener;
    private final int size;

    public TileAdapter(List<AppItem> items, int size, Listener listener) {
        this.items = items;
        this.size = Math.max(0, Math.min(2, size));
        this.listener = listener;
    }

    public void updateIcon(String id, Drawable d) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).id.equals(id)) {
                items.set(i, new AppItem(id, items.get(i).label, d, items.get(i).intent, items.get(i).isApp));
                notifyItemChanged(i);
            }
        }
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.tile, parent, false);
        float dp = parent.getResources().getDisplayMetrics().density;
        v.getLayoutParams().width = (int) (TILE_W[size] * dp);
        FrameLayout box = v.findViewById(R.id.iconBox);
        box.getLayoutParams().height = (int) (ICON_H[size] * dp);
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
