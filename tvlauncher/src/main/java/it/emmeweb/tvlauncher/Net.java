package it.emmeweb.tvlauncher;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** HTTP minimale con Basic + Digest (ISAPI Hikvision/HiWatch) e GET semplici per i webhook. */
public class Net {
    public static final ExecutorService IO = Executors.newFixedThreadPool(4);

    public static Bitmap fetchImage(String url, String user, String pass) {
        try {
            byte[] b = get(url, user, pass);
            return b == null ? null : BitmapFactory.decodeByteArray(b, 0, b.length);
        } catch (Exception e) { return null; }
    }

    public static int ping(String url) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(4000); c.setReadTimeout(6000);
            int code = c.getResponseCode();
            c.disconnect();
            return code;
        } catch (Exception e) { return -1; }
    }

    public static byte[] get(String url, String user, String pass) throws Exception {
        HttpURLConnection c = open(url);
        if (user != null && !user.isEmpty())
            c.setRequestProperty("Authorization", "Basic " + Base64.encodeToString((user + ":" + pass).getBytes(), Base64.NO_WRAP));
        int code = c.getResponseCode();
        if (code == 401 && user != null) {
            String ch = c.getHeaderField("WWW-Authenticate");
            c.disconnect();
            if (ch != null && ch.startsWith("Digest")) {
                c = open(url);
                c.setRequestProperty("Authorization", digest(ch, "GET", new URL(url).getFile(), user, pass));
                code = c.getResponseCode();
            }
        }
        if (code != 200) { c.disconnect(); return null; }
        try (InputStream in = c.getInputStream()) {
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192]; int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            return bo.toByteArray();
        } finally { c.disconnect(); }
    }

    private static HttpURLConnection open(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(4000); c.setReadTimeout(8000);
        return c;
    }

    private static String digest(String challenge, String method, String uri, String user, String pass) throws Exception {
        Map<String, String> p = new HashMap<>();
        Matcher m = Pattern.compile("(\\w+)=(?:\"([^\"]*)\"|([^,]*))").matcher(challenge.substring(7));
        while (m.find()) p.put(m.group(1), m.group(2) != null ? m.group(2) : m.group(3));
        String realm = p.get("realm"), nonce = p.get("nonce"), qop = p.get("qop"), opaque = p.get("opaque");
        String cnonce = Long.toHexString(System.nanoTime());
        String nc = "00000001";
        String ha1 = md5(user + ":" + realm + ":" + pass);
        String ha2 = md5(method + ":" + uri);
        String resp = qop != null ? md5(ha1 + ":" + nonce + ":" + nc + ":" + cnonce + ":auth:" + ha2) : md5(ha1 + ":" + nonce + ":" + ha2);
        StringBuilder sb = new StringBuilder("Digest username=\"" + user + "\", realm=\"" + realm + "\", nonce=\"" + nonce
                + "\", uri=\"" + uri + "\", response=\"" + resp + "\"");
        if (qop != null) sb.append(", qop=auth, nc=").append(nc).append(", cnonce=\"").append(cnonce).append("\"");
        if (opaque != null) sb.append(", opaque=\"").append(opaque).append("\"");
        return sb.toString();
    }

    private static String md5(String s) throws Exception {
        byte[] d = MessageDigest.getInstance("MD5").digest(s.getBytes("UTF-8"));
        StringBuilder sb = new StringBuilder();
        for (byte b : d) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
