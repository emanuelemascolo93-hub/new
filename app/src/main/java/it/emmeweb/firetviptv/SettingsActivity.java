package it.emmeweb.firetviptv;

import android.content.Intent;
import android.net.VpnService;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class SettingsActivity extends AppCompatActivity {
    private static final int REQ_VPN = 101;

    private PlaylistStore store;
    private EditText playlistSource, vpnConfig;
    private CheckBox vpnAutostart;
    private TextView vpnStatus;
    private final Handler ui = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        store = new PlaylistStore(this);

        playlistSource = findViewById(R.id.playlistSource);
        vpnConfig = findViewById(R.id.vpnConfig);
        vpnAutostart = findViewById(R.id.vpnAutostart);
        vpnStatus = findViewById(R.id.vpnStatus);

        playlistSource.setText(store.getSource());
        vpnConfig.setText(store.getVpnConfig());
        vpnAutostart.setChecked(store.isVpnAutostart());

        Button btnLoad = findViewById(R.id.btnLoad);
        Button btnSave = findViewById(R.id.btnSave);
        Button btnConnect = findViewById(R.id.btnVpnConnect);
        Button btnDisconnect = findViewById(R.id.btnVpnDisconnect);

        btnSave.setOnClickListener(v -> { save(); toast("Salvato"); });

        btnLoad.setOnClickListener(v -> {
            save();
            toast(getString(R.string.loading));
            new Thread(() -> {
                try {
                    store.refresh();
                    int n = store.load().size();
                    ui.post(() -> {
                        toast(getString(R.string.channels_count, n));
                        setResult(RESULT_OK);
                        finish();
                    });
                } catch (Exception e) {
                    ui.post(() -> toast("Errore: " + e.getMessage()));
                }
            }).start();
        });

        btnConnect.setOnClickListener(v -> {
            save();
            if (store.getVpnConfig().trim().isEmpty()) { toast("Configurazione WireGuard vuota"); return; }
            Intent prep = VpnService.prepare(this);
            if (prep != null) startActivityForResult(prep, REQ_VPN);
            else connectVpn();
        });

        btnDisconnect.setOnClickListener(v -> new Thread(() -> {
            try {
                VpnManager.get(this).down();
                ui.post(this::updateVpnStatus);
            } catch (Exception e) {
                ui.post(() -> toast("Errore VPN: " + e.getMessage()));
            }
        }).start());

        updateVpnStatus();
    }

    private void save() {
        store.setSource(playlistSource.getText().toString());
        store.setVpnConfig(vpnConfig.getText().toString());
        store.setVpnAutostart(vpnAutostart.isChecked());
    }

    private void connectVpn() {
        vpnStatus.setText("VPN: connessione…");
        new Thread(() -> {
            try {
                VpnManager.get(this).up(store.getVpnConfig());
                ui.post(this::updateVpnStatus);
            } catch (Exception e) {
                ui.post(() -> { toast("Errore VPN: " + e.getMessage()); updateVpnStatus(); });
            }
        }).start();
    }

    private void updateVpnStatus() {
        boolean up = VpnManager.get(this).isUp();
        vpnStatus.setText(up ? R.string.vpn_status_up : R.string.vpn_status_down);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_VPN) {
            if (resultCode == RESULT_OK) connectVpn();
            else toast("Permesso VPN negato");
        }
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
}
