package it.emmeweb.warpvpn;

import android.content.Context;
import android.content.SharedPreferences;

import com.wireguard.crypto.KeyPair;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** Registrazione automatica su Cloudflare WARP (1.1.1.1) e generazione del profilo WireGuard. */
public class WarpRegistrar {
    private static final String PREFS = "warp";
    private static final String KEY_CONF = "conf";
    private static final String API = "https://api.cloudflareclient.com/v0a2158/reg";

    private final SharedPreferences prefs;

    public WarpRegistrar(Context ctx) {
        prefs = ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public boolean hasProfile() { return !prefs.getString(KEY_CONF, "").isEmpty(); }

    private static final String KEY_GOOD = "good_endpoint";
    public String getGoodEndpoint() { return prefs.getString(KEY_GOOD, ""); }
    public void setGoodEndpoint(String e) { prefs.edit().putString(KEY_GOOD, e).apply(); }

    /** Lista fissa degli endpoint, dal più veloce al più lento (test del 23/10/2026 sulla linea di casa). */
    public static final String[] FIXED = {
        "engage.cloudflareclient.com:903",
        "engage.cloudflareclient.com:928",
        "162.159.193.1:4500",
        "162.159.192.1:854",
        "162.159.195.1:908",
        "162.159.195.1:942",
        "162.159.192.1:500",
        "162.159.192.1:928",
        "162.159.193.1:878",
        "162.159.192.1:878",
        "162.159.192.1:934",
        "engage.cloudflareclient.com:945",
        "162.159.192.1:4500",
        "162.159.193.1:859",
        "162.159.195.1:4500",
        "162.159.193.1:945",
        "162.159.193.1:891",
        "engage.cloudflareclient.com:864",
        "engage.cloudflareclient.com:878",
        "162.159.193.1:2408",
        "162.159.192.1:2408",
        "engage.cloudflareclient.com:854",
        "162.159.193.1:890",
        "162.159.195.1:903",
        "engage.cloudflareclient.com:942",
        "162.159.192.1:945",
        "162.159.193.1:880",
        "162.159.195.1:500",
        "162.159.192.1:891",
        "162.159.195.1:890",
        "162.159.195.1:939",
        "162.159.195.1:945",
        "162.159.192.1:942",
        "162.159.193.1:934",
        "162.159.195.1:880",
        "162.159.192.1:864",
        "engage.cloudflareclient.com:500",
        "162.159.195.1:943",
        "engage.cloudflareclient.com:4500",
        "engage.cloudflareclient.com:908",
        "162.159.192.1:903",
        "engage.cloudflareclient.com:859",
        "162.159.192.1:943",
        "162.159.192.1:894"
    };

    public java.util.List<String> rankedEndpoints() { return java.util.Arrays.asList(FIXED); }

    /** Scansione completa di riserva (usata solo se tutti i FIXED sono KO). */
    public java.util.List<String> endpoints() {
        String[] hosts = {"engage.cloudflareclient.com", "162.159.192.1", "162.159.193.1", "162.159.195.1", "188.114.97.1", "188.114.98.1"};
        int[] ports = {2408, 500, 4500, 1701, 854, 859, 864, 878, 880, 890, 891, 894, 903, 908, 928, 934, 939, 942, 943, 945, 946, 955, 968, 987, 988, 1002, 1010, 1014, 1018, 1070, 1074, 1180, 1387, 1843, 2371, 2506, 3138, 3476, 3581, 3854, 4177, 4198, 4233, 5279, 5956, 7103, 7152, 7156, 7281, 7559, 8319, 8742, 8854, 8886};
        java.util.List<String> out = new java.util.ArrayList<>(rankedEndpoints());
        for (int p : ports) for (String h : hosts) { String e = h + ":" + p; if (!out.contains(e)) out.add(e); }
        return out;
    }

    public static String withEndpoint(String profile, String endpoint) {
        return profile + "Endpoint = " + endpoint + "\nPersistentKeepalive = 25\n";
    }

    public void reset() { prefs.edit().remove(KEY_CONF).apply(); }

    /** Restituisce il profilo WireGuard, registrando il dispositivo se necessario. Da chiamare in background. */
    public String getProfile() throws Exception {
        String c = prefs.getString(KEY_CONF, "");
        if (!c.isEmpty()) return c;
        c = register();
        prefs.edit().putString(KEY_CONF, c).apply();
        return c;
    }

    private String register() throws Exception {
        KeyPair kp = new KeyPair();
        String pub = kp.getPublicKey().toBase64();
        String priv = kp.getPrivateKey().toBase64();

        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));

        JSONObject body = new JSONObject();
        body.put("key", pub);
        body.put("install_id", "");
        body.put("fcm_token", "");
        body.put("tos", f.format(new Date()));
        body.put("model", "PC");
        body.put("serial_number", "");
        body.put("locale", "it_IT");

        HttpURLConnection c = (HttpURLConnection) new URL(API).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(20000);
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty("User-Agent", "okhttp/3.12.1");
        c.setRequestProperty("CF-Client-Version", "a-6.30-2158");
        c.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        c.setRequestProperty("Accept", "application/json");
        try (OutputStream o = c.getOutputStream()) {
            o.write(body.toString().getBytes(StandardCharsets.UTF_8));
        }
        int code = c.getResponseCode();
        InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] b = new byte[4096];
        int n;
        while (in != null && (n = in.read(b)) > 0) buf.write(b, 0, n);
        String resp = buf.toString("UTF-8");
        if (code != 200) throw new Exception("WARP registrazione fallita (HTTP " + code + "): " + resp);

        JSONObject j = new JSONObject(resp);
        JSONObject conf = j.getJSONObject("config");
        JSONArray peers = conf.getJSONArray("peers");
        JSONObject peer = peers.getJSONObject(0);
        String peerPub = peer.getString("public_key");
        String endpoint = peer.getJSONObject("endpoint").getString("host");
        JSONObject addr = conf.getJSONObject("interface").getJSONObject("addresses");
        String v4 = addr.getString("v4");
        String v6 = addr.optString("v6", "");

        StringBuilder sb = new StringBuilder();
        sb.append("[Interface]\n");
        sb.append("PrivateKey = ").append(priv).append('\n');
        sb.append("Address = ").append(v4).append("/32");
        if (!v6.isEmpty()) sb.append(", ").append(v6).append("/128");
        sb.append('\n');
        sb.append("DNS = 1.1.1.1, 1.0.0.1\n");
        sb.append("MTU = 1280\n\n");
        sb.append("[Peer]\n");
        sb.append("PublicKey = ").append(peerPub).append('\n');
        sb.append("AllowedIPs = 0.0.0.0/0, ::/0\n");
        // Endpoint aggiunto a runtime (vedi endpoints())
        return sb.toString();
    }
}
