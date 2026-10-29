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
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
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
    private final Map<Integer, Bitmap> camCache = new HashMap<>();
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            Date d = new Date();
            clock.setText(new SimpleDateFormat("HH:mm", Locale.ITALY).format(d));
            date.setText(new SimpleDateFormat("EEEE d MMMM", Locale.ITALY).format(d));
            handler.postDelayed(this, 10_000);
        }
    };
    private final Runnable camRefresh = new Runnable() {
        @Override public void run() { refreshCams(); handler.postDelayed(this, 15_000); }
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
        findViewById(R.id.gear).setOnClickListener(v -> settingsMenu());
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
        handler.post(camRefresh);
    }

    @Override protected void onPause() {
        super.onPause();
        handler.removeCallbacks(tick);
        handler.removeCallbacks(camRefresh);
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(pkgReceiver);
    }

    @Override public void onBackPressed() { /* la home non si chiude */ }

    // ================= sfondo =================

    private void loadWallpaper() {
        ImageView iv = findViewById(R.id.wallpaper);
        Bitmap bm = null;
        String p = prefs.wallpaperPath();
        if (!p.isEmpty() && new File(p).canRead()) bm = BitmapFactory.decodeFile(p);
        if (bm == null) {
            File f = new File("/sdcard/Pictures/emmeweb_tv.jpg");
            if (f.canRead()) bm = BitmapFactory.decodeFile(f.getPath());
        }
        if (bm == null) {
            try (InputStream in = getAssets().open("wallpaper.jpg")) { bm = BitmapFactory.decodeStream(in); }
            catch (Exception ignored) { }
        }
        if (bm != null) iv.setImageBitmap(bm);
    }

    private void pickWallpaper() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(this, "android.permission.READ_MEDIA_IMAGES") != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, "android.permission.READ_MEDIA_IMAGES"}, 1);
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
        List<String> names = new ArrayList<>();
        names.add("Sfondo EMMEWEB (predefinito)");
        for (String f : files) names.add(new File(f).getName());
        new AlertDialog.Builder(this).setTitle("Sfondo (immagini in Pictures/Download)")
                .setItems(names.toArray(new String[0]), (dlg, w) -> {
                    prefs.setWallpaperPath(w == 0 ? "" : files.get(w - 1));
                    loadWallpaper();
                }).show();
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] p, int[] r) {
        super.onRequestPermissionsResult(code, p, r);
        if (code == 1) for (int x : r) if (x == PackageManager.PERMISSION_GRANTED) { pickWallpaper(); return; }
    }

    // ================= righe =================

    private void build() {
        rows.removeAllViews();
        Map<String, AppItem> all = loadApps();
        Set<String> hidden = prefs.hidden();

        for (String row : prefs.rowOrder()) {
            if (!prefs.rowEnabled(row)) continue;
            switch (row) {
                case "sources": {
                    List<AppItem> l = sourceItems(all);
                    if (!l.isEmpty()) addRow(getString(R.string.row_sources), l);
                    break;
                }
                case "fav": {
                    List<AppItem> favs = new ArrayList<>();
                    for (String id : prefs.favorites()) { AppItem a = all.get(id); if (a != null) favs.add(a); }
                    if (!favs.isEmpty()) addRow(getString(R.string.row_fav), favs);
                    break;
                }
                case "cams": {
                    if (!prefs.nvrIp().isEmpty()) addRow(getString(R.string.row_cams), camItems());
                    break;
                }
                case "smart": {
                    List<AppItem> l = smartItems();
                    if (!l.isEmpty()) addRow(getString(R.string.row_smart), l);
                    break;
                }
                case "apps": {
                    List<AppItem> apps = new ArrayList<>(all.values());
                    Collections.sort(apps, (x, y) -> x.label.compareToIgnoreCase(y.label));
                    List<AppItem> visible = new ArrayList<>();
                    for (AppItem a : apps) if (showHidden || !hidden.contains(a.id)) visible.add(a);
                    addRow(showHidden ? "Tutte le app (incluse nascoste)" : getString(R.string.row_apps), visible);
                    break;
                }
                case "sys":
                    addRow(getString(R.string.row_sys), systemItems(all));
                    break;
            }
        }
    }

    private Map<String, AppItem> loadApps() {
        PackageManager pm = getPackageManager();
        Map<String, AppItem> all = new HashMap<>();
        Intent q = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        for (ResolveInfo ri : pm.queryIntentActivities(q, 0)) addApp(pm, all, ri, true);
        Intent q2 = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        for (ResolveInfo ri : pm.queryIntentActivities(q2, 0)) addApp(pm, all, ri, false);
        all.remove(getPackageName());
        return all;
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

    /** Sorgenti: ingressi HDMI/AV via TvInputManager + TV digitale + USB. */
    private List<AppItem> sourceItems(Map<String, AppItem> all) {
        List<AppItem> l = new ArrayList<>();
        try {
            TvInputManager tim = (TvInputManager) getSystemService(Context.TV_INPUT_SERVICE);
            for (TvInputInfo in : tim.getTvInputList()) {
                if (!in.isPassthroughInput()) continue;
                String label = String.valueOf(in.loadLabel(this));
                String id = in.getId();
                int res = android.R.drawable.ic_menu_slideshow;
                if (id.toUpperCase().contains("HDMI")) res = android.R.drawable.ic_menu_set_as;
                Intent i = new Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(id));
                l.add(new AppItem("src:" + id, label, ContextCompat.getDrawable(this, res), i, false));
            }
        } catch (Exception ignored) { }
        AppItem tv = all.get("com.tcl.tv");
        if (tv != null) l.add(0, new AppItem("src:dtv", "TV digitale", tv.icon, tv.intent, false));
        AppItem usb = all.get("com.tcl.ui_mediaCenter");
        if (usb != null) l.add(new AppItem("src:usb", "USB", usb.icon, usb.intent, false));
        return l;
    }

    private List<AppItem> camItems() {
        List<AppItem> l = new ArrayList<>();
        for (int ch = 1; ch <= prefs.nvrChannels(); ch++) {
            Bitmap bm = camCache.get(ch);
            Drawable d = bm != null ? new BitmapDrawable(getResources(), bm)
                    : ContextCompat.getDrawable(this, android.R.drawable.ic_menu_camera);
            Intent i = new Intent(this, CameraActivity.class)
                    .putExtra("name", prefs.camName(ch))
                    .putExtra("rtsp", "rtsp://" + prefs.nvrUser() + ":" + prefs.nvrPass() + "@" + prefs.nvrIp() + ":554/Streaming/Channels/" + ch + "02")
                    .putExtra("snap", snapUrl(ch))
                    .putExtra("user", prefs.nvrUser()).putExtra("pass", prefs.nvrPass());
            l.add(new AppItem("cam:" + ch, prefs.camName(ch), d, i, false));
        }
        return l;
    }

    private String snapUrl(int ch) {
        return "http://" + prefs.nvrIp() + "/ISAPI/Streaming/channels/" + ch + "01/picture?videoResolutionWidth=320&videoResolutionHeight=180";
    }

    private void refreshCams() {
        if (prefs.nvrIp().isEmpty() || !prefs.rowEnabled("cams")) return;
        for (int ch = 1; ch <= prefs.nvrChannels(); ch++) {
            final int c = ch;
            Net.IO.execute(() -> {
                Bitmap bm = Net.fetchImage(snapUrl(c), prefs.nvrUser(), prefs.nvrPass());
                if (bm != null) handler.post(() -> {
                    camCache.put(c, bm);
                    updateTile("cam:" + c, bm);
                });
            });
        }
    }

    private void updateTile(String id, Bitmap bm) {
        for (int r = 0; r < rows.getChildCount(); r++) {
            RecyclerView rv = rows.getChildAt(r).findViewById(R.id.list);
            if (rv == null || !(rv.getAdapter() instanceof TileAdapter)) continue;
            ((TileAdapter) rv.getAdapter()).updateIcon(id, new BitmapDrawable(getResources(), bm));
        }
    }

    private List<AppItem> smartItems() {
        List<AppItem> l = new ArrayList<>();
        for (String[] t : prefs.smartTiles()) {
            l.add(new AppItem("smart:" + t[1], t[0], ContextCompat.getDrawable(this, android.R.drawable.ic_menu_compass), null, false));
        }
        return l;
    }

    private List<AppItem> systemItems(Map<String, AppItem> all) {
        List<AppItem> l = new ArrayList<>();
        l.add(sys("sys:settings", "Impostazioni TV", android.R.drawable.ic_menu_preferences, new Intent(Settings.ACTION_SETTINGS)));
        l.add(sys("sys:wifi", "Rete", android.R.drawable.ic_menu_mapmode, new Intent(Settings.ACTION_WIFI_SETTINGS)));
        l.add(sys("sys:bt", "Accessori", android.R.drawable.ic_menu_share, new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)));
        l.add(sys("sys:launcher", "Launcher", android.R.drawable.ic_menu_manage, null));
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
        rv.setAdapter(new TileAdapter(items, prefs.iconSize(), new TileAdapter.Listener() {
            @Override public void onClick(AppItem it) { open(it); }
            @Override public void onLongClick(AppItem it, View anchor) { menu(it); }
        }));
        rows.addView(row);
    }

    // ================= azioni =================

    private void open(AppItem it) {
        if ("sys:launcher".equals(it.id)) { settingsMenu(); return; }
        if (it.id.startsWith("smart:")) { callWebhook(it); return; }
        try {
            Intent i = new Intent(it.intent);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, "Non riesco ad aprire " + it.label, Toast.LENGTH_SHORT).show();
        }
    }

    private void callWebhook(AppItem it) {
        String url = it.id.substring(6);
        Toast.makeText(this, it.label + "…", Toast.LENGTH_SHORT).show();
        Net.IO.execute(() -> {
            int code = Net.ping(url);
            handler.post(() -> Toast.makeText(this, code >= 200 && code < 300 ? it.label + ": ok" : it.label + ": errore (" + code + ")", Toast.LENGTH_SHORT).show());
        });
    }

    private void menu(AppItem it) {
        if (it.id.startsWith("cam:")) { camMenu(Integer.parseInt(it.id.substring(4))); return; }
        if (it.id.startsWith("smart:")) { smartMenu(it); return; }
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
                    switch (opts.get(w)) {
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

    // ================= impostazioni launcher =================

    private void settingsMenu() {
        String[] opts = {"Sfondo", "Righe (attiva/disattiva)", "Ordine righe", "Dimensione icone",
                showHidden ? "Nascondi le app nascoste" : "Mostra le app nascoste",
                "Telecamere (NVR)", "Smart home (aggiungi tile)", "Impostazioni TV", "Riavvia launcher"};
        new AlertDialog.Builder(this).setTitle("EMMEWEB TV — Impostazioni")
                .setItems(opts, (d, w) -> {
                    switch (w) {
                        case 0: pickWallpaper(); break;
                        case 1: rowsToggle(); break;
                        case 2: rowsOrder(); break;
                        case 3: iconSize(); break;
                        case 4: showHidden = !showHidden; build(); break;
                        case 5: nvrDialog(); break;
                        case 6: smartAdd(); break;
                        case 7: startActivity(new Intent(Settings.ACTION_SETTINGS)); break;
                        case 8: recreate(); break;
                    }
                }).show();
    }

    private String rowLabel(String r) {
        switch (r) {
            case "sources": return "Sorgenti";
            case "fav": return "Preferiti";
            case "cams": return "Telecamere";
            case "smart": return "Smart home";
            case "apps": return "Tutte le app";
            default: return "TV";
        }
    }

    private void rowsToggle() {
        List<String> order = prefs.rowOrder();
        String[] labels = new String[order.size()];
        boolean[] on = new boolean[order.size()];
        for (int i = 0; i < order.size(); i++) { labels[i] = rowLabel(order.get(i)); on[i] = prefs.rowEnabled(order.get(i)); }
        new AlertDialog.Builder(this).setTitle("Righe visibili")
                .setMultiChoiceItems(labels, on, (d, w, c) -> prefs.setRowEnabled(order.get(w), c))
                .setPositiveButton("OK", (d, w) -> build()).show();
    }

    private void rowsOrder() {
        List<String> order = prefs.rowOrder();
        String[] labels = new String[order.size()];
        for (int i = 0; i < order.size(); i++) labels[i] = (i + 1) + ". " + rowLabel(order.get(i));
        new AlertDialog.Builder(this).setTitle("Scegli la riga da spostare in alto")
                .setItems(labels, (d, w) -> {
                    if (w > 0) { String r = order.remove(w); order.add(w - 1, r); prefs.saveRowOrder(order); }
                    build();
                    rowsOrder();
                }).setNegativeButton("Fatto", null).show();
    }

    private void iconSize() {
        new AlertDialog.Builder(this).setTitle("Dimensione icone")
                .setSingleChoiceItems(new String[]{"Piccole", "Medie", "Grandi"}, prefs.iconSize(), (d, w) -> {
                    prefs.setIconSize(w); d.dismiss(); build();
                }).show();
    }

    private void nvrDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(40, 20, 40, 0);
        EditText ip = field(box, "IP NVR (es. 192.168.1.50)", prefs.nvrIp(), InputType.TYPE_CLASS_TEXT);
        EditText user = field(box, "Utente", prefs.nvrUser(), InputType.TYPE_CLASS_TEXT);
        EditText pass = field(box, "Password", prefs.nvrPass(), InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        EditText ch = field(box, "Numero telecamere", String.valueOf(prefs.nvrChannels()), InputType.TYPE_CLASS_NUMBER);
        new AlertDialog.Builder(this).setTitle("Telecamere — NVR HiWatch/Hikvision").setView(box)
                .setPositiveButton("Salva", (d, w) -> {
                    int n = 4; try { n = Integer.parseInt(ch.getText().toString().trim()); } catch (Exception ignored) { }
                    prefs.saveNvr(ip.getText().toString().trim(), user.getText().toString().trim(), pass.getText().toString(), n);
                    camCache.clear(); build(); refreshCams();
                })
                .setNeutralButton("Disattiva", (d, w) -> { prefs.saveNvr("", "admin", "", 4); build(); })
                .setNegativeButton("Annulla", null).show();
    }

    private void camMenu(int ch) {
        EditText name = new EditText(this);
        name.setText(prefs.camName(ch));
        new AlertDialog.Builder(this).setTitle("Nome telecamera " + ch).setView(name)
                .setPositiveButton("Salva", (d, w) -> { prefs.setCamName(ch, name.getText().toString().trim()); build(); })
                .setNegativeButton("Annulla", null).show();
    }

    private void smartAdd() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(40, 20, 40, 0);
        EditText name = field(box, "Nome (es. Avvia Deebot)", "", InputType.TYPE_CLASS_TEXT);
        EditText url = field(box, "URL webhook (http://…)", "http://", InputType.TYPE_TEXT_VARIATION_URI);
        new AlertDialog.Builder(this).setTitle("Nuova tile Smart home").setView(box)
                .setPositiveButton("Aggiungi", (d, w) -> {
                    List<String[]> l = prefs.smartTiles();
                    l.add(new String[]{name.getText().toString().trim(), url.getText().toString().trim()});
                    prefs.saveSmartTiles(l); build();
                }).setNegativeButton("Annulla", null).show();
    }

    private void smartMenu(AppItem it) {
        new AlertDialog.Builder(this).setTitle(it.label)
                .setItems(new String[]{"Esegui", "Elimina tile"}, (d, w) -> {
                    if (w == 0) callWebhook(it);
                    else {
                        List<String[]> l = prefs.smartTiles();
                        List<String[]> keep = new ArrayList<>();
                        for (String[] t : l) if (!("smart:" + t[1]).equals(it.id)) keep.add(t);
                        prefs.saveSmartTiles(keep); build();
                    }
                }).show();
    }

    private EditText field(LinearLayout box, String hint, String value, int type) {
        EditText e = new EditText(this);
        e.setHint(hint); e.setText(value); e.setInputType(type); e.setSingleLine(true);
        box.addView(e, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return e;
    }
}
