package it.emmeweb.tvlauncher;

import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.VideoView;

import androidx.appcompat.app.AppCompatActivity;

/** Telecamera a schermo intero: prova lo stream RTSP, altrimenti snapshot ISAPI aggiornati ogni secondo. */
public class CameraActivity extends AppCompatActivity {
    private final Handler h = new Handler(Looper.getMainLooper());
    private boolean snapshotMode = false, running = true;
    private String snapUrl, user, pass;
    private ImageView img;
    private TextView status;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);
        VideoView vv = new VideoView(this);
        img = new ImageView(this);
        img.setScaleType(ImageView.ScaleType.FIT_CENTER);
        status = new TextView(this);
        status.setTextColor(0xFFFFFFFF);
        status.setPadding(40, 30, 40, 30);
        root.addView(vv, new FrameLayout.LayoutParams(-1, -1));
        root.addView(img, new FrameLayout.LayoutParams(-1, -1));
        root.addView(status);
        setContentView(root);

        String name = getIntent().getStringExtra("name");
        String rtsp = getIntent().getStringExtra("rtsp");
        snapUrl = getIntent().getStringExtra("snap");
        user = getIntent().getStringExtra("user");
        pass = getIntent().getStringExtra("pass");
        status.setText(name + " — connessione…");

        img.setVisibility(View.GONE);
        vv.setOnPreparedListener(mp -> { status.setText(name); mp.setLooping(false); });
        vv.setOnErrorListener((mp, w, e) -> { startSnapshots(name); return true; });
        try { vv.setVideoURI(Uri.parse(rtsp)); vv.start(); }
        catch (Exception e) { startSnapshots(name); }
        // se in 8 secondi lo stream non parte, passa agli snapshot
        h.postDelayed(() -> { if (!vv.isPlaying()) startSnapshots(name); }, 8000);
    }

    private void startSnapshots(String name) {
        if (snapshotMode) return;
        snapshotMode = true;
        img.setVisibility(View.VISIBLE);
        status.setText(name + " (snapshot)");
        Runnable loop = new Runnable() {
            @Override public void run() {
                if (!running) return;
                Net.IO.execute(() -> {
                    Bitmap bm = Net.fetchImage(snapUrl, user, pass);
                    h.post(() -> { if (bm != null) img.setImageBitmap(bm); else status.setText(name + " — nessuna immagine"); });
                });
                h.postDelayed(this, 1000);
            }
        };
        h.post(loop);
    }

    @Override protected void onDestroy() { running = false; h.removeCallbacksAndMessages(null); super.onDestroy(); }
}
