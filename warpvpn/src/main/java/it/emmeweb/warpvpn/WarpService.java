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
        if (ACTION_NEXT.equals(action)) {
            if (!endpoint.isEmpty()) bad.add(endpoint);
            new WarpRegistrar(this).setGoodEndpoint("");
            cancel = true;
            try { backend(this).setState(tunnel, Tunnel.State.DOWN, null); } catch (Exception ignored) {}
            startForeground(1, buildNotification());
            setState("connecting", "");
            new Thread(this::connectWithFallback).start();
            return START_STICKY;
        }
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

    public static final String ACTION_NEXT = "it.emmeweb.warpvpn.NEXT";
    public static volatile String benchReport = "";

    private String formatRanking(java.util.List<String[]> results, int max) {
        java.util.List<String[]> copy = new java.util.ArrayList<>(results);
        java.util.Collections.sort(copy, (a, b) -> Long.compare(Long.parseLong(a[1]), Long.parseLong(b[1])));
        StringBuilder sb = new StringBuilder();
        for (int k = 0; k < Math.min(max, copy.size()); k++)
            sb.append(String.format(java.util.Locale.ITALY, "%2d. %-34s %6s ms\n", k + 1, copy.get(k)[0], copy.get(k)[1]));
        return sb.toString();
    }

    /** Una sola richiesta con timeout corto: -1 se non passa, altrimenti i ms. */
    private long quickProbeMs() {
        long best = -1;
        for (int i = 0; i < 2; i++) {
            long t0 = System.currentTimeMillis();
            try {
                java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL("https://www.cloudflare.com/cdn-cgi/trace").openConnection();
                c.setConnectTimeout(3000);
                c.setReadTimeout(3000);
                c.setRequestProperty("User-Agent", "WarpVPN/1.3");
                if (c.getResponseCode() == 200) {
                    java.io.InputStream in = c.getInputStream();
                    byte[] b = new byte[2048]; int n = in.read(b); in.close();
                    if (n > 0) { long ms = System.currentTimeMillis() - t0; if (best < 0 || ms < best) best = ms; }
                }
            } catch (Exception ignored) { if (best < 0) return -1; }
        }
        return best;
    }
    private static final java.util.Set<String> bad = new java.util.HashSet<>();

    private void connectWithFallback() {
        cancel = false;
        WarpRegistrar reg = new WarpRegistrar(this);
        String base;
        try { base = reg.getProfile(); }
        catch (Exception e) { setState("error", "Registrazione WARP: " + e.getMessage()); return; }

        final long KEEP_MS = 100;   // soglia per tenere l'ultimo usato
        final long PICK_MS = 50;    // soglia per sceglierne uno nuovo
        java.util.List<String[]> now = new java.util.ArrayList<>();

        // 1) ultimo usato: se sotto KEEP_MS lo teniamo
        String last = reg.getGoodEndpoint();
        if (!last.isEmpty() && !bad.contains(last)) {
            endpoint = last;
            setState("connecting", "Verifico l'ultimo usato: " + last);
            if (bringUp(base, last)) {
                long ms = quickProbeMs();
                if (ms >= 0) now.add(new String[]{last, String.valueOf(ms)});
                if (ms >= 0 && ms < KEEP_MS) {
                    benchReport = "Ultimo usato ok: " + last + "  " + ms + " ms";
                    setState("up", last + " (" + ms + " ms)");
                    return;
                }
                try { backend(this).setState(tunnel, Tunnel.State.DOWN, null); } catch (Exception ignored) {}
            }
        }

        // 2) scorri la lista fissa e fermati al primo sotto PICK_MS
        String bestEp = null; long bestMs = Long.MAX_VALUE;
        java.util.List<String> list = reg.rankedEndpoints();
        int i = 0;
        for (String ep : list) {
            if (cancel) return;
            if (bad.contains(ep) || ep.equals(last)) continue;
            i++;
            endpoint = ep;
            setState("connecting", "Provo " + ep + " (" + i + "/" + list.size() + ")");
            benchReport = "Scelta nuovo endpoint (soglia " + PICK_MS + " ms):\n" + formatRanking(now, 50);
            if (!bringUp(base, ep)) continue;
            long ms = quickProbeMs();
            if (ms >= 0) {
                now.add(new String[]{ep, String.valueOf(ms)});
                if (ms < bestMs) { bestMs = ms; bestEp = ep; }
                if (ms < PICK_MS) { bestEp = ep; bestMs = ms; break; }
            }
            try { backend(this).setState(tunnel, Tunnel.State.DOWN, null); } catch (Exception ignored) {}
        }

        // 3) nessuno sotto soglia: il più veloce misurato
        if (bestEp != null) {
            benchReport = "Scelta nuovo endpoint:\n" + formatRanking(now, 50);
            if (!bestEp.equals(endpoint) || !isUp()) {
                try { backend(this).setState(tunnel, Tunnel.State.DOWN, null); } catch (Exception ignored) {}
                if (!bringUp(base, bestEp)) { setState("error", "Connessione a " + bestEp + " fallita"); return; }
                endpoint = bestEp;
            }
            reg.setGoodEndpoint(bestEp);
            setState("up", bestEp + " (" + bestMs + " ms)");
            return;
        }

        // 4) tutti KO: scansione completa di riserva
        int tried = 0;
        for (String ep : reg.endpoints()) {
            if (cancel) return;
            if (bad.contains(ep) || list.contains(ep)) continue;
            tried++;
            endpoint = ep;
            setState("connecting", "Scansione di riserva " + ep + " (" + tried + ")");
            if (!bringUp(base, ep)) continue;
            long ms = quickProbeMs();
            if (ms >= 0) {
                reg.setGoodEndpoint(ep);
                benchReport = "Trovato in scansione di riserva: " + ep + "  " + ms + " ms";
                setState("up", ep + " (" + ms + " ms)");
                return;
            }
            try { backend(this).setState(tunnel, Tunnel.State.DOWN, null); } catch (Exception ignored) {}
            if (tried >= 60) break;
        }
        setState("error", "Nessun endpoint WARP raggiungibile: la rete blocca WireGuard/UDP");
    }

    private boolean bringUp(String base, String ep) {
        try {
            Config config = Config.parse(new ByteArrayInputStream(WarpRegistrar.withEndpoint(base, ep).getBytes(StandardCharsets.UTF_8)));
            backend(this).setState(tunnel, Tunnel.State.UP, config);
            return true;
        } catch (Exception e) { return false; }
    }

    /** Latenza media (ms) di 2 richieste HTTPS via tunnel, oppure -1 se non passa. */
    private long probeMs() {
        long total = 0; int ok = 0;
        for (int i = 0; i < 3; i++) {
            if (cancel) return -1;
            long t0 = System.currentTimeMillis();
            try {
                java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL("https://www.cloudflare.com/cdn-cgi/trace").openConnection();
                c.setConnectTimeout(5000);
                c.setReadTimeout(5000);
                c.setRequestProperty("User-Agent", "WarpVPN/1.2");
                if (c.getResponseCode() == 200) {
                    java.io.InputStream in = c.getInputStream();
                    byte[] b = new byte[2048]; int n = in.read(b); in.close();
                    if (n > 0) { ok++; total += System.currentTimeMillis() - t0; if (ok >= 2) break; continue; }
                }
            } catch (Exception ignored) {}
            try { Thread.sleep(800); } catch (InterruptedException e) { return -1; }
        }
        return ok == 0 ? -1 : total / ok;
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
