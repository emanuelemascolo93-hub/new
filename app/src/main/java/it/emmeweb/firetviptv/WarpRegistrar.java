package it.emmeweb.firetviptv;

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
        sb.append("Endpoint = ").append(endpoint).append('\n');
        return sb.toString();
    }
}
