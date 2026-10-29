package it.emmeweb.tvlauncher;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

/** Tiene acceso il telecomando web anche quando la home non e' in primo piano. */
public class RemoteService extends Service {
    private RemoteServer server;

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("remote", "Telecomando web", NotificationManager.IMPORTANCE_MIN));
        Notification n = new Notification.Builder(this, "remote")
                .setContentTitle("EMMEWEB TV")
                .setContentText("Telecomando web attivo sulla porta " + RemoteServer.PORT)
                .setSmallIcon(android.R.drawable.ic_menu_manage)
                .build();
        startForeground(1, n);
        try { server = new RemoteServer(this); server.start(); } catch (Exception ignored) { }
    }

    @Override public int onStartCommand(Intent i, int f, int id) { return START_STICKY; }
    @Override public void onDestroy() { if (server != null) server.stop(); super.onDestroy(); }
    @Override public IBinder onBind(Intent i) { return null; }
}
