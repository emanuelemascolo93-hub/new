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
        if (ACTION_BENCH.equals(action)) {
            cancel = true;
            try { backend(this).setState(tunnel, Tunnel.State.DOWN, null); } catch (Exception ignored) {}
            startForeground(1, buildNotification());
            setState("connecting", "Test endpoint…");
            new Thread(this::benchmark).start();
            return START_STICKY;
        }
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
    public static final String ACTION_BENCH = "it.emmeweb.warpvpn.BENCH";
    public static volatile String benchReport = "";

    /** Testa tutti gli endpoint candidati, salva i 10 più veloci e si collega al migliore. */
    private void benchmark() {
        cancel = false;
        WarpRegistrar reg = new WarpRegistrar(this);
        String base;
        try { base = reg.getProfile(); }
        catch (Exception e) { setState("error", "Registrazione WARP: " + e.getMessage()); return; }

        java.util.List<String> eps = reg.benchEndpoints();
        java.util.List<String[]> results = new java.util.ArrayList<>();
        java.util.List<String> allLines = new java.util.ArrayList<>();
        int i = 0;
        for (String ep : eps) {
            if (cancel) return;
            i++;
            benchReport = "Test " + i + "/" + eps.size() + ": " + ep + "\n\nMigliori finora:\n" + formatRanking(results, 10);
            setState("connecting", "Test endpoint " + i + "/" + eps.size());
            long ms = -1;
            if (bringUp(base, ep)) {
                ms = quickProbeMs();
                if (ms >= 0) results.add(new String[]{ep, String.valueOf(ms)});
            }
            allLines.add(ep + "\t" + (ms >= 0 ? ms + " ms" : "KO"));
            android.util.Log.i("WarpVPN", "bench " + ep + " -> " + (ms >= 0 ? ms + " ms" : "KO"));
            try { backend(this).setState(tunnel, Tunnel.State.DOWN, null); } catch (Exception ignored) {}
        }
        saveReportFile(results, allLines, eps.size());
        java.util.Collections.sort(results, (a, b) -> Long.compare(Long.parseLong(a[1]), Long.parseLong(b[1])));
        if (results.isEmpty()) {
            benchReport = "Nessun endpoint raggiungibile";
            setState("error", "Nessun endpoint WARP raggiungibile");
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (int k = 0; k < Math.min(10, results.size()); k++) {
            if (k > 0) sb.append(',');
            sb.append(results.get(k)[0]).append('=').append(results.get(k)[1]);
        }
        reg.setRanked(sb.toString());
        reg.setGoodEndpoint(results.get(0)[0]);
        benchReport = "Classifica (" + results.size() + " funzionanti su " + eps.size() + ") — file: " + reportPath + "\n" + formatRanking(results, 80);
        connectWithFallback();
    }

    public static volatile String reportPath = "";

    /** Salva la classifica completa in un file leggibile via adb pull. */
    private void saveReportFile(java.util.List<String[]> results, java.util.List<String> allLines, int total) {
        java.util.List<String[]> sorted = new java.util.ArrayList<>(results);
        java.util.Collections.sort(sorted, (a, b) -> Long.compare(Long.parseLong(a[1]), Long.parseLong(b[1])));
        StringBuilder sb = new StringBuilder();
        sb.append("# WarpVPN test endpoint — ").append(new java.util.Date()).append("\n");
        sb.append("# ").append(results.size()).append(" funzionanti su ").append(total).append("\n\n");
        sb.append("## CLASSIFICA (dal più veloce)\n");
        for (int k = 0; k < sorted.size(); k++) sb.append(k + 1).append('\t').append(sorted.get(k)[0]).append('\t').append(sorted.get(k)[1]).append(" ms\n");
        sb.append("\n## TUTTI (ordine di test)\n");
        for (String l : allLines) sb.append(l).append('\n');
        String text = sb.toString();
        java.io.File[] targets = {
                new java.io.File(getExternalFilesDir(null), "warp_endpoints.txt"),
                new java.io.File("/sdcard/Download/warp_endpoints.txt")
        };
        for (java.io.File f : targets) {
            try {
                if (f.getParentFile() != null) f.getParentFile().mkdirs();
                java.io.FileOutputStream o = new java.io.FileOutputStream(f);
                o.write(text.getBytes(StandardCharsets.UTF_8));
                o.close();
                reportPath = f.getAbsolutePath();
            } catch (Exception ignored) {}
        }
    }

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

        java.util.List<String> ranked = reg.rankedEndpoints();
        String bestEp = null; long bestMs = Long.MAX_VALUE; int working = 0, tried = 0;

        // 1) rimisura i preferiti del test e scegli il più veloce adesso
        java.util.List<String[]> now = new java.util.ArrayList<>();
        for (String ep : ranked) {
            if (cancel) return;
            if (bad.contains(ep)) continue;
            tried++;
            endpoint = ep;
            setState("connecting", "Misuro " + ep + " (" + tried + "/" + ranked.size() + ")" + (bestEp != null ? " — migliore: " + bestEp + " " + bestMs + " ms" : ""));
            benchReport = "Scelta tra i preferiti:\n" + formatRanking(now, 10);
            if (!bringUp(base, ep)) continue;
            long ms = quickProbeMs();
            if (ms >= 0) { now.add(new String[]{ep, String.valueOf(ms)}); if (ms < bestMs) { bestMs = ms; bestEp = ep; } }
            try { backend(this).setState(tunnel, Tunnel.State.DOWN, null); } catch (Exception ignored) {}
        }
        if (bestEp != null) {
            benchReport = "Scelta tra i preferiti (adesso):\n" + formatRanking(now, 10);
            if (!bringUp(base, bestEp)) { setState("error", "Connessione a " + bestEp + " fallita"); return; }
            endpoint = bestEp;
            reg.setGoodEndpoint(bestEp);
            setState("up", bestEp + " (" + bestMs + " ms)");
            return;
        }

        // 2) nessun preferito risponde: scansione completa
        java.util.List<String> eps = reg.endpoints();
        for (String ep : eps) {
            if (ranked.contains(ep)) continue;
            if (cancel) return;
            if (bad.contains(ep)) continue;
            tried++;
            endpoint = ep;
            setState("connecting", "Provo " + ep + " (" + tried + ")" + (bestEp != null ? " — migliore finora: " + bestEp + " " + bestMs + " ms" : ""));
            if (!bringUp(base, ep)) continue;
            long ms = probeMs();
            if (ms >= 0) {
                working++;
                if (ms < bestMs) { bestMs = ms; bestEp = ep; }
                // abbastanza veloce: tieni questo
                if (ms < 600) break;
                // altrimenti confronta con altri endpoint funzionanti (max 6)
                if (working >= 6) break;
            }
            try { backend(this).setState(tunnel, Tunnel.State.DOWN, null); } catch (Exception ignored) {}
            if (tried >= 45) break;
        }
        if (bestEp == null) { setState("error", "Nessun endpoint WARP raggiungibile: la rete blocca WireGuard/UDP"); return; }
        if (!bestEp.equals(endpoint) || !isUp()) {
            try { backend(this).setState(tunnel, Tunnel.State.DOWN, null); } catch (Exception ignored) {}
            if (!bringUp(base, bestEp)) { setState("error", "Riconnessione a " + bestEp + " fallita"); return; }
            endpoint = bestEp;
        }
        reg.setGoodEndpoint(bestEp);
        setState("up", bestEp + " (" + bestMs + " ms)");
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
