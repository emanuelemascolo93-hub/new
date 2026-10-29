package it.emmeweb.tvlauncher;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Preferiti (ordinati), app nascoste, sfondo. */
public class Prefs {
    private final SharedPreferences sp;

    public Prefs(Context c) {
        sp = c.getSharedPreferences("launcher", Context.MODE_PRIVATE);
    }

    public List<String> favorites() {
        String s = sp.getString("fav", "");
        if (s.isEmpty()) return new ArrayList<>();
        return new ArrayList<>(Arrays.asList(s.split(",")));
    }

    public void saveFavorites(List<String> l) {
        sp.edit().putString("fav", String.join(",", l)).apply();
    }

    public boolean isFavorite(String id) { return favorites().contains(id); }

    public void toggleFavorite(String id) {
        List<String> l = favorites();
        if (!l.remove(id)) l.add(id);
        saveFavorites(l);
    }

    public void moveFavorite(String id, int delta) {
        List<String> l = favorites();
        int i = l.indexOf(id);
        int j = i + delta;
        if (i < 0 || j < 0 || j >= l.size()) return;
        l.remove(i);
        l.add(j, id);
        saveFavorites(l);
    }

    public Set<String> hidden() {
        return new HashSet<>(sp.getStringSet("hidden", new HashSet<>()));
    }

    public void toggleHidden(String id) {
        Set<String> h = hidden();
        if (!h.remove(id)) h.add(id);
        sp.edit().putStringSet("hidden", h).apply();
    }

    public boolean firstRun() { return sp.getBoolean("first", true); }
    public void setFirstRunDone() { sp.edit().putBoolean("first", false).apply(); }

    // --- righe e aspetto ---
    public static final String[] ALL_ROWS = {"sources", "fav", "cams", "smart", "apps", "sys"};
    public List<String> rowOrder() {
        String s = sp.getString("rows", "sources,fav,cams,smart,apps,sys");
        return new ArrayList<>(Arrays.asList(s.split(",")));
    }
    public void saveRowOrder(List<String> l) { sp.edit().putString("rows", String.join(",", l)).apply(); }
    public boolean rowEnabled(String r) { return sp.getBoolean("row_" + r, true); }
    public void setRowEnabled(String r, boolean v) { sp.edit().putBoolean("row_" + r, v).apply(); }
    public int iconSize() { return sp.getInt("icon", 1); } // 0 piccole, 1 medie, 2 grandi
    public void setIconSize(int v) { sp.edit().putInt("icon", v).apply(); }

    // --- telecamere (NVR HiWatch/Hikvision) ---
    public String nvrIp() { return sp.getString("nvr_ip", ""); }
    public String nvrUser() { return sp.getString("nvr_user", "admin"); }
    public String nvrPass() { return sp.getString("nvr_pass", ""); }
    public int nvrChannels() { return sp.getInt("nvr_ch", 4); }
    public void saveNvr(String ip, String user, String pass, int ch) {
        sp.edit().putString("nvr_ip", ip).putString("nvr_user", user).putString("nvr_pass", pass).putInt("nvr_ch", ch).apply();
    }
    public String camName(int ch) { return sp.getString("cam_name_" + ch, "Camera " + ch); }
    public void setCamName(int ch, String n) { sp.edit().putString("cam_name_" + ch, n).apply(); }

    // --- smart home: "nome|url" per riga ---
    public List<String[]> smartTiles() {
        List<String[]> l = new ArrayList<>();
        for (String line : sp.getString("smart", "").split("\n")) {
            if (line.trim().isEmpty()) continue;
            String[] p = line.split("\\|", 2);
            if (p.length == 2) l.add(new String[]{p[0], p[1]});
        }
        return l;
    }
    public void saveSmartTiles(List<String[]> l) {
        StringBuilder sb = new StringBuilder();
        for (String[] t : l) sb.append(t[0]).append("|").append(t[1]).append("\n");
        sp.edit().putString("smart", sb.toString()).apply();
    }

    public String wallpaperPath() { return sp.getString("wall", ""); }
    public void setWallpaperPath(String p) { sp.edit().putString("wall", p).apply(); }
}
