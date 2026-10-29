package it.emmeweb.tvlauncher;

import android.content.Context;

import java.io.File;

import dadb.AdbKeyPair;
import dadb.Dadb;

/** Invia tasti reali alla TV tramite ADB locale (127.0.0.1:5555). */
public class Adb {
    private static Dadb dadb;
    private static final Object lock = new Object();

    private static Dadb get(Context c) throws Exception {
        synchronized (lock) {
            if (dadb != null) return dadb;
            File dir = new File(c.getFilesDir(), "adb");
            dir.mkdirs();
            File priv = new File(dir, "adbkey"), pub = new File(dir, "adbkey.pub");
            if (!priv.exists()) AdbKeyPair.generate(priv, pub);
            AdbKeyPair kp = AdbKeyPair.read(priv, pub);
            dadb = Dadb.create("127.0.0.1", 5555, kp);
            return dadb;
        }
    }

    public static String shell(Context c, String cmd) {
        try {
            return get(c).shell(cmd).getAllOutput();
        } catch (Exception e) {
            synchronized (lock) { try { if (dadb != null) dadb.close(); } catch (Exception ignored) { } dadb = null; }
            return null;
        }
    }

    public static boolean key(Context c, String keycode) {
        return shell(c, "input keyevent " + keycode) != null;
    }

    public static boolean available(Context c) {
        String r = shell(c, "echo ok");
        return r != null && r.contains("ok");
    }
}
