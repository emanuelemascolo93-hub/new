package it.emmeweb.warpvpn;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    private static final int REQ_VPN = 1;
    private TextView status, detail, report;

    private final BroadcastReceiver stateReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { render(); }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        status = findViewById(R.id.status);
        detail = findViewById(R.id.detail);
        report = findViewById(R.id.report);
        findViewById(R.id.btnBench).setOnClickListener(v -> startSvc(WarpService.ACTION_BENCH));

        findViewById(R.id.btnConnect).setOnClickListener(v -> connect());
        findViewById(R.id.btnDisconnect).setOnClickListener(v -> startSvc(WarpService.ACTION_DOWN));
        findViewById(R.id.btnNext).setOnClickListener(v -> startSvc(WarpService.ACTION_NEXT));
        findViewById(R.id.btnReset).setOnClickListener(v -> {
            new WarpRegistrar(this).reset();
            Toast.makeText(this, "Profilo azzerato: riconnetto", Toast.LENGTH_SHORT).show();
            startSvc(WarpService.ACTION_DOWN);
            findViewById(R.id.btnConnect).postDelayed(this::connect, 1500);
        });

        // connessione automatica all'apertura
        if (!WarpService.isUp()) connect();
    }

    private void connect() {
        Intent prep = VpnService.prepare(this);
        if (prep != null) startActivityForResult(prep, REQ_VPN);
        else startSvc(WarpService.ACTION_UP);
    }

    private void startSvc(String action) {
        Intent svc = new Intent(this, WarpService.class).setAction(action);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(svc); else startService(svc);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_VPN) {
            if (resultCode == RESULT_OK) startSvc(WarpService.ACTION_UP);
            else Toast.makeText(this, "Permesso VPN negato", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter f = new IntentFilter(WarpService.BROADCAST_STATE);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(stateReceiver, f, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(stateReceiver, f);
        render();
    }

    @Override
    protected void onPause() {
        super.onPause();
        try { unregisterReceiver(stateReceiver); } catch (Exception ignored) {}
    }

    private void render() {
        String s = WarpService.isUp() ? "up" : WarpService.state;
        String saved = new WarpRegistrar(this).getRanked();
        if (!WarpService.benchReport.isEmpty()) report.setText(WarpService.benchReport);
        else if (!saved.isEmpty()) {
            StringBuilder sb = new StringBuilder("Endpoint preferiti (dal test):\n");
            int k = 0;
            for (String item : saved.split(",")) {
                int eq = item.indexOf('=');
                if (eq > 0) sb.append(String.format(java.util.Locale.ITALY, "%2d. %-34s %6s ms\n", ++k, item.substring(0, eq), item.substring(eq + 1)));
            }
            report.setText(sb.toString());
        }
        else report.setText("");
        switch (s) {
            case "up": status.setText(R.string.status_up); detail.setText("Endpoint: " + WarpService.lastError); break;
            case "connecting": status.setText(R.string.status_connecting); detail.setText(WarpService.lastError); break;
            case "error": status.setText(R.string.status_down); detail.setText("Errore: " + WarpService.lastError); break;
            default: status.setText(R.string.status_down); detail.setText("");
        }
    }
}
