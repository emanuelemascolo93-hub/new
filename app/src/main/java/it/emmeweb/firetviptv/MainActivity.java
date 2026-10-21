package it.emmeweb.firetviptv;

import android.content.Intent;
import android.net.VpnService;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {
    private static final int REQ_SETTINGS = 1;
    private static final int REQ_VPN = 2;

    private PlaylistStore store;
    private final Handler ui = new Handler(Looper.getMainLooper());

    private List<Channel> all = new ArrayList<>();
    private List<Channel> visible = new ArrayList<>();
    private final List<String> groups = new ArrayList<>();
    private TextAdapter groupAdapter, channelAdapter;
    private TextView status, vpnStatus;
    private boolean vpnAutoTried = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        store = new PlaylistStore(this);

        status = findViewById(R.id.status);
        vpnStatus = findViewById(R.id.vpnStatus);

        RecyclerView groupList = findViewById(R.id.groupList);
        RecyclerView channelList = findViewById(R.id.channelList);
        groupList.setLayoutManager(new LinearLayoutManager(this));
        channelList.setLayoutManager(new LinearLayoutManager(this));

        groupAdapter = new TextAdapter(this::selectGroup);
        channelAdapter = new TextAdapter(this::play);
        groupList.setAdapter(groupAdapter);
        channelList.setAdapter(channelAdapter);

        findViewById(R.id.btnSettings).setOnClickListener(v -> openSettings());
        findViewById(R.id.btnReload).setOnClickListener(v -> reload());

        if (store.getSource().isEmpty()) {
            openSettings();
        } else if (store.hasCache()) {
            loadFromCache();
        } else {
            reload();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateVpnStatus();
        if (!vpnAutoTried && store.isVpnAutostart() && !store.getVpnConfig().trim().isEmpty()
                && !VpnManager.get(this).isUp()) {
            vpnAutoTried = true;
            Intent prep = VpnService.prepare(this);
            if (prep != null) startActivityForResult(prep, REQ_VPN);
            else connectVpn();
        }
    }

    private void connectVpn() {
        vpnStatus.setText("VPN: connessione…");
        new Thread(() -> {
            try {
                VpnManager.get(this).up(store.getVpnConfig());
            } catch (Exception e) {
                ui.post(() -> toast("Errore VPN: " + e.getMessage()));
            }
            ui.post(this::updateVpnStatus);
        }).start();
    }

    private void updateVpnStatus() {
        vpnStatus.setText(VpnManager.get(this).isUp() ? R.string.vpn_status_up : R.string.vpn_status_down);
    }

    private void openSettings() {
        startActivityForResult(new Intent(this, SettingsActivity.class), REQ_SETTINGS);
    }

    private void reload() {
        status.setText(R.string.loading);
        new Thread(() -> {
            try {
                store.refresh();
                List<Channel> list = store.load();
                ui.post(() -> setChannels(list));
            } catch (Exception e) {
                ui.post(() -> status.setText("Errore: " + e.getMessage()));
            }
        }).start();
    }

    private void loadFromCache() {
        status.setText(R.string.loading);
        new Thread(() -> {
            try {
                List<Channel> list = store.load();
                ui.post(() -> setChannels(list));
            } catch (Exception e) {
                ui.post(() -> status.setText("Errore: " + e.getMessage()));
            }
        }).start();
    }

    private void setChannels(List<Channel> list) {
        all = list;
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Channel c : all) counts.put(c.group, counts.containsKey(c.group) ? counts.get(c.group) + 1 : 1);
        groups.clear();
        groups.add(getString(R.string.all_groups));
        groups.addAll(counts.keySet());
        List<String> labels = new ArrayList<>();
        labels.add(getString(R.string.all_groups) + " (" + all.size() + ")");
        for (Map.Entry<String, Integer> e : counts.entrySet()) labels.add(e.getKey() + " (" + e.getValue() + ")");
        groupAdapter.setItems(labels);
        selectGroup(0);
        status.setText(getString(R.string.channels_count, all.size()));
    }

    private void selectGroup(int index) {
        visible = new ArrayList<>();
        if (index == 0) visible.addAll(all);
        else {
            String g = groups.get(index);
            for (Channel c : all) if (c.group.equals(g)) visible.add(c);
        }
        List<String> names = new ArrayList<>(visible.size());
        for (Channel c : visible) names.add(c.name);
        channelAdapter.setItems(names);
    }

    private void play(int index) {
        Intent i = new Intent(this, PlayerActivity.class);
        PlayerActivity.setQueue(visible, index);
        startActivity(i);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_SETTINGS) {
            if (store.hasCache()) loadFromCache();
        } else if (requestCode == REQ_VPN && resultCode == RESULT_OK) {
            connectVpn();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) { openSettings(); return true; }
        return super.onKeyDown(keyCode, event);
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }
}
