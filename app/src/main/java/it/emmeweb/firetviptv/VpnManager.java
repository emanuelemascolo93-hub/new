package it.emmeweb.firetviptv;

import android.content.Context;

import com.wireguard.android.backend.GoBackend;
import com.wireguard.android.backend.Tunnel;
import com.wireguard.config.Config;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

/** Gestione del tunnel WireGuard integrato (GoBackend). */
public class VpnManager {
    private static VpnManager instance;

    private final GoBackend backend;
    private final Tunnel tunnel = new Tunnel() {
        @Override public String getName() { return "iptv"; }
        @Override public void onStateChange(State newState) { state = newState; }
    };
    private volatile Tunnel.State state = Tunnel.State.DOWN;

    private VpnManager(Context ctx) {
        backend = new GoBackend(ctx.getApplicationContext());
    }

    public static synchronized VpnManager get(Context ctx) {
        if (instance == null) instance = new VpnManager(ctx);
        return instance;
    }

    public boolean isUp() {
        try {
            return backend.getState(tunnel) == Tunnel.State.UP;
        } catch (Exception e) {
            return state == Tunnel.State.UP;
        }
    }

    /** Profilo da usare: WARP automatico oppure configurazione personalizzata. Da chiamare in background. */
    public static String resolveProfile(Context ctx) throws Exception {
        PlaylistStore store = new PlaylistStore(ctx);
        if (store.isWarp()) return new WarpRegistrar(ctx).getProfile();
        String custom = store.getVpnConfig().trim();
        if (custom.isEmpty()) throw new Exception("Nessuna configurazione WireGuard");
        return custom;
    }

    /** Da chiamare in background, dopo aver ottenuto il permesso VpnService. */
    public void up(String confText) throws Exception {
        Config config = Config.parse(new ByteArrayInputStream(confText.getBytes(StandardCharsets.UTF_8)));
        backend.setState(tunnel, Tunnel.State.UP, config);
    }

    public void down() throws Exception {
        backend.setState(tunnel, Tunnel.State.DOWN, null);
    }
}
