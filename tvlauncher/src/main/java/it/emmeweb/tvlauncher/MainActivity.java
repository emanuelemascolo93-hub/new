package it.emmeweb.tvlauncher;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private static final String[] DEFAULT_FAVS = {
            "com.google.android.youtube.tv", "it.rainet.androidtv", "it.mediaset.infinitytv",
            "com.netflix.ninja", "com.amazon.amazonvideo.livingroom", "com.tcl.tv",
            "com.android.vending", "com.tcl.smartalexa"
    };
    private static final String[] IMG_DIRS = {"/sdcard/Pictures", "/sdcard/Download", "/sdcard/DCIM"};

    private Prefs prefs;
    private LinearLayout rows;
    private TextView clock, date;
    private boolean showHidden = false;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            Date d = new Date();
            clock.setText(new SimpleDateFormat("HH:mm", Locale.ITALY).format(d));
            date.setText(new SimpleDateFormat("EEEE d MMMM", Locale.ITALY).format(d));
            handler.postDelayed(this, 10_000);
        }
    };
    private final BroadcastReceiver pkgReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { build(); }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        prefs = new Prefs(this);
        rows = findViewById(R.id.rows);
        clock = findViewById(R.id.clock);
        date = findViewById(R.id.date);
        loadWallpaper();
        if (prefs.firstRun()) {
            prefs.saveFavorites(new ArrayList<>(Arrays.asList(DEFAULT_FAVS)));
            prefs.setFirstRunDone();
        }
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addDataScheme("package");
        registerReceiver(pkgReceiver, f);
    }

    @Override protected void onResume() {
        super.onResume();
        build();
        handler.post(tick);
    }

    @Override protected void onPause() {
        super.onPause();
        handler.removeCallbacks(tick);
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(pkgReceiver);
    }

    @Override public void onBackPressed() { /* la home non si chiude */ }

    // ---------- sfondo ----------

    private void loadWallpaper() {
        ImageView iv = findViewById(R.id.wallpaper);
        Bitmap bm = null;
        String p = prefs.wallpaperPath();
        if (!p.isEmpty() && new File(p).canRead()) bm = decode(p);
        if (bm == null) {
            File f = new File("/sdcard/Pictures/emmeweb_tv.jpg");
            if (f.canRead()) bm = decode(f.getPath());
        }
        if (bm == null) {
            try (InputStream in = getAssets().open("wallpaper.jpg")) {
                bm = BitmapFactory.decodeStream(in);
            } catch (Exception ignored) { }
        }
        if (bm != null) iv.setImageBitmap(bm);
    }

    private Bitmap decode(String path) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inSampleSize = 1;
            return BitmapFactory.decodeFile(path, o);
        } catch (Exception e) { return null; }
    }

    private void pickWallpaper() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE,
                    "android.permission.READ_MEDIA_IMAGES"}, 1);
            return;
        }
        List<String> files = new ArrayList<>();
        for (String d : IMG_DIRS) {
            File[] l = new File(d).listFiles();
            if (l == null) continue;
            for (File f : l) {
                String n = f.getName().toLowerCase();
                if (n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png")) files.add(f.getPath());
            }
        }
        Collections.sort(files);
        if (files.isEmpty()) {
            Toast.makeText(this, "Nessuna immagine in Pictures/Download. Caricala con: adb push file.jpg /sdcard/Pictures/", Toast.LENGTH_LONG).show();
            return;
        }
        List<String> names = new ArrayList<>();
        names.add("Sfondo EMMEWEB (predefinito)");
        for (String f : files) names.add(new File(f).getName());
        new AlertDialog.Builder(this).setTitle("Scegli sfondo")
                .setItems(names.toArray(new String[0]), (dlg, w) -> {
                    prefs.setWallpaperPath(w == 0 ? "" : files.get(w - 1));
                    loadWallpaper();
                }).show();
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] p, int[] r) {
        super.onRequestPermissionsResult(code, p, r);
        if (code == 1 && r.length > 0 && r[0] == PackageManager.PERMISSION_GRANTED) pickWallpaper();
    }

    // ---------- righe ----------

    private void build() {
        rows.removeAllViews();
        PackageManager pm = getPackageManager();
        Map<String, AppItem> all = new HashMap<>();

        Intent q = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        for (ResolveInfo ri : pm.queryIntentActivities(q, 0)) addApp(pm, all, ri, true);
        Intent q2 = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        for (ResolveInfo ri : pm.queryIntentActivities(q2, 0)) addApp(pm, all, ri, false);
        all.remove(getPackageName());

        Set<String> hidden = prefs.hidden();

        List<AppItem> favs = new ArrayList<>();
        for (String id : prefs.favorites()) {
            AppItem a = all.get(id);
            if (a != null) favs.add(a);
        }

        List<AppItem> apps = new ArrayList<>(all.values());
        Collections.sort(apps, (x, y) -> x.label.compareToIgnoreCase(y.label));
        List<AppItem> visible = new ArrayList<>();
        for (AppItem a : apps) if (showHidden || !hidden.contains(a.id)) visible.add(a);

        if (!favs.isEmpty()) addRow(getString(R.string.row_fav), favs);
        addRow(showHidden ? "Tutte le app (incluse nascoste)" : getString(R.string.row_apps), visible);
        addRow(getString(R.string.row_sys), systemItems(all));
    }

    private void addApp(PackageManager pm, Map<String, AppItem> all, ResolveInfo ri, boolean leanback) {
        String pkg = ri.activityInfo.packageName;
        if (all.containsKey(pkg)) return;
        Drawable icon = null;
        if (leanback) {
            try { icon = ri.activityInfo.loadBanner(pm); } catch (Exception ignored) { }
            if (icon == null) try { icon = pm.getApplicationBanner(pkg); } catch (Exception ignored) { }
        }
        if (icon == null) icon = ri.loadIcon(pm);
        Intent launch = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (launch == null) launch = pm.getLaunchIntentForPackage(pkg);
        if (launch == null) return;
        all.put(pkg, new AppItem(pkg, ri.loadLabel(pm).toString(), icon, launch, true));
    }

    private List<AppItem> systemItems(Map<String, AppItem> all) {
        List<AppItem> l = new ArrayList<>();
        AppItem live = all.get("com.tcl.tv");
        if (live != null) l.add(new AppItem("sys:tv", "TV / Sorgenti", live.icon, live.intent, false));
        l.add(sys("sys:settings", "Impostazioni", android.R.drawable.ic_menu_preferences, new Intent(Settings.ACTION_SETTINGS)));
        l.add(sys("sys:wifi", "Rete", android.R.drawable.ic_menu_mapmode, new Intent(Settings.ACTION_WIFI_SETTINGS)));
        l.add(sys("sys:bt", "Accessori", android.R.drawable.ic_menu_share, new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)));
        l.add(sys("sys:wall", "Sfondo", android.R.drawable.ic_menu_gallery, null));
        l.add(sys("sys:hidden", showHidden ? "Nascondi app nascoste" : "Mostra app nascoste", android.R.drawable.ic_menu_view, null));
        l.add(sys("sys:acc", "Tasto Home", android.R.drawable.ic_menu_manage, new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        return l;
    }

    private AppItem sys(String id, String label, int iconRes, Intent i) {
        return new AppItem(id, label, ContextCompat.getDrawable(this, iconRes), i, false);
    }

    private void addRow(String title, List<AppItem> items) {
        View row = LayoutInflater.from(this).inflate(R.layout.row, rows, false);
        ((TextView) row.findViewById(R.id.title)).setText(title);
        RecyclerView rv = row.findViewById(R.id.list);
        rv.setLayoutManager(new LinearLayoutManager(this, RecyclerView.HORIZONTAL, false));
        rv.setAdapter(new TileAdapter(items, new TileAdapter.Listener() {
            @Override public void onClick(AppItem it) { open(it); }
            @Override public void onLongClick(AppItem it, View anchor) { menu(it); }
        }));
        rows.addView(row);
    }

    private void open(AppItem it) {
        if ("sys:wall".equals(it.id)) { pickWallpaper(); return; }
        if ("sys:hidden".equals(it.id)) { showHidden = !showHidden; build(); return; }
        try {
            Intent i = new Intent(it.intent);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, "Non riesco ad aprire " + it.label, Toast.LENGTH_SHORT).show();
        }
    }

    private void menu(AppItem it) {
        if (!it.isApp) return;
        boolean fav = prefs.isFavorite(it.id);
        boolean hid = prefs.hidden().contains(it.id);
        List<String> opts = new ArrayList<>();
        opts.add(fav ? "Rimuovi dai preferiti" : "Aggiungi ai preferiti");
        if (fav) { opts.add("Sposta a sinistra"); opts.add("Sposta a destra"); }
        opts.add(hid ? "Mostra" : "Nascondi");
        opts.add("Info app");
        opts.add("Disinstalla");
        new AlertDialog.Builder(this).setTitle(it.label)
                .setItems(opts.toArray(new String[0]), (d, w) -> {
                    String o = opts.get(w);
                    switch (o) {
                        case "Aggiungi ai preferiti": case "Rimuovi dai preferiti": prefs.toggleFavorite(it.id); break;
                        case "Sposta a sinistra": prefs.moveFavorite(it.id, -1); break;
                        case "Sposta a destra": prefs.moveFavorite(it.id, +1); break;
                        case "Nascondi": case "Mostra": prefs.toggleHidden(it.id); break;
                        case "Info app":
                            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + it.id)));
                            return;
                        case "Disinstalla":
                            startActivity(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + it.id)));
                            return;
                    }
                    build();
                }).show();
    }
}
