package it.emmeweb.warpvpn;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.VpnService;
import android.os.Build;

/** Riattiva la VPN all'avvio della Stick (solo se il permesso VPN è già stato concesso). */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        if (VpnService.prepare(ctx) != null) return; // serve consenso: lo chiederà l'app alla prossima apertura
        Intent svc = new Intent(ctx, WarpService.class).setAction(WarpService.ACTION_UP);
        if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(svc); else ctx.startService(svc);
    }
}
