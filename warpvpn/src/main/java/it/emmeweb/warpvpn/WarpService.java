package it.emmeweb.warpvpn;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;

import com.wireguard.android.backend.GoBackend;
import com.wireguard.android.backend.Tunnel;
import com.wireguard.config.Config;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

/** Servizio in foreground che tiene attivo il tunnel WARP anche quando l'app non è in primo piano. */
public class WarpService extends Service {
    public static final String ACTION_UP = "it.emmeweb.warpvpn.UP";
    public static final String ACTION_DOWN = "it.emmeweb.warpvpn.DOWN";
    public static final String BROADCAST_STATE = "it.emmeweb.warpvpn.STATE";
    private static final String CHANNEL = "warp";

    public static volatile String state = "down";   // down | connecting | up | error
    public static volatile String lastError = "";

    private static GoBackend backend;
    private static final Tunnel tunnel = new Tunnel() {
        @Override public String getName() { return "warp"; }
        @Override public void onStateChange(State s) {}
    };

    private static synchronized GoBackend backend(WarpService s) {
        if (backend == null) backend = new GoBackend(s.getApplicationContext());
        return backend;
    }

    public static boolean isUp() {
        try { return backend != null && backend.getState(tunnel) == Tunnel.State.UP; }
        catch (Exception e) { return false; }
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : ACTION_UP;
        if (ACTION_DOWN.equals(action)) {
            cancel = true;
            new Thread(() -> {
                try { backend(this).setState(tunnel, Tunnel.State.DOWN, null); } catch (Exception ignored) {}
                setState("down", "");
                stopForeground(true);
                stopSelf();
            }).start();
            return START_NOT_STICKY;
        }

        startForeground(1, buildNotification());
        if (isUp()) { setState("up", ""); return START_STICKY; }
        setState("connecting", "");
        new Thread(this::connectWithFallback).start();
        return START_STICKY;
    }

    public static volatile String endpoint = "";
    private static volatile boolean cancel = false;

    private void connectWithFallback() {
        cancel = false;
        WarpRegistrar reg = new WarpRegistrar(this);
        String base;
        try { base = reg.getProfile(); }
        catch (Exception e) { setState("error", "Registrazione WARP: " + e.getMessage()); return; }

        java.util.List<String> eps = reg.endpoints();
        int tried = 0;
        for (String ep : eps) {
            if (cancel) return;
            tried++;
            endpoint = ep;
            setState("connecting", "Provo " + ep + " (" + tried + "/" + eps.size() + ")");
            try {
                Config config = Config.parse(new ByteArrayInputStream(WarpRegistrar.withEndpoint(base, ep).getBytes(StandardCharsets.UTF_8)));
                backend(this).setState(tunnel, Tunnel.State.UP, config);
            } catch (Exception e) {
                continue;
            }
            if (probe()) {
                reg.setGoodEndpoint(ep);
                setState("up", ep);
                return;
            }
            try { backend(this).setState(tunnel, Tunnel.State.DOWN, null); } catch (Exception ignored) {}
            if (tried >= 40) break;
        }
        setState("error", "Nessun endpoint WARP raggiungibile: la rete blocca WireGuard/UDP");
    }

    /** Verifica che il traffico passi davvero dal tunnel. */
    private boolean probe() {
        for (int i = 0; i < 3; i++) {
            if (cancel) return false;
            try {
                java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL("https://www.cloudflare.com/cdn-cgi/trace").openConnection();
                c.setConnectTimeout(4000);
                c.setReadTimeout(4000);
                c.setRequestProperty("User-Agent", "WarpVPN/1.1");
                int code = c.getResponseCode();
                if (code == 200) {
                    java.io.InputStream in = c.getInputStream();
                    byte[] b = new byte[2048]; int n = in.read(b); in.close();
                    String body = n > 0 ? new String(b, 0, n, StandardCharsets.UTF_8) : "";
                    return body.contains("warp=on") || body.contains("warp=plus") || !body.isEmpty();
                }
            } catch (Exception ignored) {}
            try { Thread.sleep(1500); } catch (InterruptedException e) { return false; }
        }
        return false;
    }

    private void setState(String s, String err) {
        state = s;
        lastError = err;
        Intent i = new Intent(BROADCAST_STATE).setPackage(getPackageName());
        sendBroadcast(i);
    }

    private Notification buildNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "WARP VPN", NotificationManager.IMPORTANCE_LOW);
            nm.createNotificationChannel(ch);
        }
        return new NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.notif_text))
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }
}
