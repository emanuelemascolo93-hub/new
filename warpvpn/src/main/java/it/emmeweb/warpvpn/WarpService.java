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
        new Thread(() -> {
            try {
                String conf = new WarpRegistrar(this).getProfile();
                Config config = Config.parse(new ByteArrayInputStream(conf.getBytes(StandardCharsets.UTF_8)));
                backend(this).setState(tunnel, Tunnel.State.UP, config);
                setState("up", "");
            } catch (Exception e) {
                setState("error", e.getMessage() == null ? e.toString() : e.getMessage());
            }
        }).start();
        return START_STICKY;
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
