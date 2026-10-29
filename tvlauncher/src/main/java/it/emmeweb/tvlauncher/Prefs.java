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

    public String wallpaperPath() { return sp.getString("wall", ""); }
    public void setWallpaperPath(String p) { sp.edit().putString("wall", p).apply(); }
}
