package it.emmeweb.firetviptv;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Preferenze + cache locale della lista. */
public class PlaylistStore {
    private static final String PREFS = "iptv";
    private static final String KEY_SOURCE = "source";
    private static final String KEY_VPN_CONF = "vpn_conf";
    private static final String KEY_VPN_AUTO = "vpn_auto";
    private static final String KEY_WARP = "warp";
    private static final String CACHE_FILE = "playlist.m3u";

    private static List<Channel> cached;

    private final Context ctx;
    private final SharedPreferences prefs;

    public PlaylistStore(Context ctx) {
        this.ctx = ctx.getApplicationContext();
        this.prefs = this.ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public String getSource() { return prefs.getString(KEY_SOURCE, ""); }
    public void setSource(String s) { prefs.edit().putString(KEY_SOURCE, s.trim()).apply(); }

    public String getVpnConfig() { return prefs.getString(KEY_VPN_CONF, ""); }
    public void setVpnConfig(String s) { prefs.edit().putString(KEY_VPN_CONF, s).apply(); }

    public boolean isVpnAutostart() { return prefs.getBoolean(KEY_VPN_AUTO, true); }

    public boolean isWarp() { return prefs.getBoolean(KEY_WARP, true); }
    public void setWarp(boolean b) { prefs.edit().putBoolean(KEY_WARP, b).apply(); }
    public void setVpnAutostart(boolean b) { prefs.edit().putBoolean(KEY_VPN_AUTO, b).apply(); }

    private File cacheFile() { return new File(ctx.getFilesDir(), CACHE_FILE); }

    public boolean hasCache() { return cacheFile().exists() && cacheFile().length() > 0; }

    /** Scarica (o copia dal file) la lista nella cache. Da chiamare in background. */
    public void refresh() throws IOException {
        String src = getSource();
        if (src.isEmpty()) throw new IOException("Sorgente lista non impostata");
        InputStream in;
        if (src.startsWith("http://") || src.startsWith("https://")) {
            String url = src;
            HttpURLConnection c = null;
            for (int hop = 0; hop < 6; hop++) {
                c = (HttpURLConnection) new URL(url).openConnection();
                c.setConnectTimeout(15000);
                c.setReadTimeout(30000);
                c.setInstanceFollowRedirects(false);
                c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 5.1.1; AFTT) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/70.0.3538.110 Safari/537.36");
                c.setRequestProperty("Accept", "*/*");
                int code = c.getResponseCode();
                if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                    String loc = c.getHeaderField("Location");
                    if (loc == null) throw new IOException("Redirect senza Location");
                    url = new URL(new URL(url), loc).toString();
                    c.disconnect();
                    c = null;
                    continue;
                }
                if (code != 200) throw new IOException("HTTP " + code);
                break;
            }
            if (c == null) throw new IOException("Troppi redirect");
            in = c.getInputStream();
        } else {
            File f = new File(src);
            if (!f.canRead()) throw new IOException("File non leggibile: " + src);
            in = new FileInputStream(f);
        }
        File tmp = new File(ctx.getFilesDir(), CACHE_FILE + ".tmp");
        try (InputStream i = in; OutputStream o = new FileOutputStream(tmp)) {
            byte[] buf = new byte[32 * 1024];
            int n;
            while ((n = i.read(buf)) > 0) o.write(buf, 0, n);
        }
        if (!tmp.renameTo(cacheFile())) throw new IOException("Impossibile salvare la cache");
        cached = null;
    }

    /** Legge e parsa la cache. Da chiamare in background. */
    public synchronized List<Channel> load() throws IOException {
        if (cached != null) return cached;
        if (!hasCache()) return new ArrayList<>();
        try (InputStreamReader r = new InputStreamReader(new FileInputStream(cacheFile()), StandardCharsets.UTF_8)) {
            cached = M3UParser.parse(r);
        }
        return cached;
    }
}
