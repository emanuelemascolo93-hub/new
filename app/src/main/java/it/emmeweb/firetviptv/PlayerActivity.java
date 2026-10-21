package it.emmeweb.firetviptv;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.PlayerView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@UnstableApi
public class PlayerActivity extends AppCompatActivity {
    private static final String DEFAULT_UA = "Mozilla/5.0 (Linux; Android 5.1.1; AFTT) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/70 Safari/537.36";

    private static List<Channel> queue = new ArrayList<>();
    private static int startIndex = 0;

    public static void setQueue(List<Channel> list, int index) {
        queue = new ArrayList<>(list);
        startIndex = index;
    }

    private ExoPlayer player;
    private PlayerView playerView;
    private ProgressBar buffering;
    private TextView overlay;
    private int index;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Runnable hideOverlay = () -> overlay.setVisibility(View.GONE);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        playerView = findViewById(R.id.playerView);
        buffering = findViewById(R.id.buffering);
        overlay = findViewById(R.id.overlay);
        index = Math.max(0, Math.min(startIndex, queue.size() - 1));
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!queue.isEmpty()) playChannel(queue.get(index));
    }

    @Override
    protected void onStop() {
        super.onStop();
        release();
    }

    private void release() {
        if (player != null) {
            player.release();
            player = null;
        }
    }

    private void playChannel(Channel ch) {
        release();
        showOverlay((index + 1) + "  " + ch.name);

        Map<String, String> headers = new HashMap<>();
        if (ch.referer != null) headers.put("Referer", ch.referer);
        DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory()
                .setUserAgent(ch.userAgent != null ? ch.userAgent : DEFAULT_UA)
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(15000)
                .setReadTimeoutMs(15000)
                .setDefaultRequestProperties(headers);
        DefaultDataSource.Factory ds = new DefaultDataSource.Factory(this, http);

        DefaultLoadControl loadControl = new DefaultLoadControl.Builder()
                .setBufferDurationsMs(10000, 30000, 1500, 3000)
                .build();

        player = new ExoPlayer.Builder(this)
                .setMediaSourceFactory(new DefaultMediaSourceFactory(ds))
                .setLoadControl(loadControl)
                .build();
        playerView.setPlayer(player);
        player.addListener(new Player.Listener() {
            @Override public void onPlaybackStateChanged(int state) {
                buffering.setVisibility(state == Player.STATE_BUFFERING ? View.VISIBLE : View.GONE);
            }
            @Override public void onPlayerError(PlaybackException error) {
                showOverlay("Errore: " + error.getErrorCodeName());
                // ritenta una volta dopo 3 s
                ui.postDelayed(() -> { if (player != null) { player.prepare(); player.play(); } }, 3000);
            }
        });
        player.setMediaItem(MediaItem.fromUri(ch.url));
        player.setPlayWhenReady(true);
        player.prepare();
    }

    private void showOverlay(String text) {
        overlay.setText(text);
        overlay.setVisibility(View.VISIBLE);
        ui.removeCallbacks(hideOverlay);
        ui.postDelayed(hideOverlay, 3000);
    }

    private void zap(int delta) {
        if (queue.isEmpty()) return;
        index = (index + delta + queue.size()) % queue.size();
        playChannel(queue.get(index));
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_CHANNEL_UP:
                zap(+1); return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_CHANNEL_DOWN:
                zap(-1); return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                showOverlay((index + 1) + "  " + queue.get(index).name); return true;
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_PLAY:
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
                if (player != null) player.setPlayWhenReady(!player.getPlayWhenReady()); return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}
