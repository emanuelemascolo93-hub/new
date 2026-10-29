package it.emmeweb.tvlauncher;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

/** Telecomando web: http://IP-TV:8080 (solo rete locale, protetto da PIN). */
public class RemoteServer extends NanoHTTPD {
    public static final int PORT = 8080;
    private final Context ctx;
    private final Prefs prefs;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<String, Bitmap> iconCache = new HashMap<>();

    public RemoteServer(Context c) {
        super(PORT);
        ctx = c.getApplicationContext();
        prefs = new Prefs(ctx);
    }

    @Override
    public Response serve(IHTTPSession s) {
        String uri = s.getUri();
        try {
            if (uri.equals("/") || uri.equals("/index.html")) return html("remote.html");
            if (uri.equals("/api/pin")) {
                Map<String, String> body = new HashMap<>();
                s.parseBody(body);
                String pin = s.getParms().get("pin");
                boolean ok = pin != null && pin.equals(prefs.pin());
                return json(new JSONObject().put("ok", ok));
            }
            // tutte le altre API richiedono il PIN
            String pin = s.getHeaders().get("x-pin");
            if (pin == null || !pin.equals(prefs.pin())) return newFixedLengthResponse(Response.Status.UNAUTHORIZED, "application/json", "{\"ok\":false,\"error\":\"pin\"}");

            Map<String, String> p = s.getParms();
            switch (uri) {
                case "/api/state": return json(state());
                case "/api/launch": { String pkg = p.get("pkg"); AppItem a = Catalog.apps(ctx).get(pkg); if (a != null) start(a.intent); return ok(); }
                case "/api/source": {
                    String id = p.get("id");
                    for (AppItem a : Catalog.sources(ctx, Catalog.apps(ctx))) if (a.id.equals(id)) start(a.intent);
                    return ok();
                }
                case "/api/cam": {
                    int ch = Integer.parseInt(p.get("ch"));
                    Intent i = new Intent(ctx, CameraActivity.class)
                            .putExtra("name", prefs.camName(ch))
                            .putExtra("rtsp", "rtsp://" + prefs.nvrUser() + ":" + prefs.nvrPass() + "@" + prefs.nvrIp() + ":554/Streaming/Channels/" + ch + "02")
                            .putExtra("snap", "http://" + prefs.nvrIp() + "/ISAPI/Streaming/channels/" + ch + "01/picture")
                            .putExtra("user", prefs.nvrUser()).putExtra("pass", prefs.nvrPass());
                    start(i);
                    return ok();
                }
                case "/api/snap": {
                    int ch = Integer.parseInt(p.get("ch"));
                    byte[] b = Net.get("http://" + prefs.nvrIp() + "/ISAPI/Streaming/channels/" + ch + "01/picture?videoResolutionWidth=640&videoResolutionHeight=360", prefs.nvrUser(), prefs.nvrPass());
                    if (b == null) return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "no image");
                    return newFixedLengthResponse(Response.Status.OK, "image/jpeg", new ByteArrayInputStream(b), b.length);
                }
                case "/api/smart": {
                    int i = Integer.parseInt(p.get("i"));
                    List<String[]> l = prefs.smartTiles();
                    if (i < l.size()) { String url = l.get(i)[1]; Net.IO.execute(() -> Net.ping(url)); }
                    return ok();
                }
                case "/api/smart/add": {
                    List<String[]> l = prefs.smartTiles();
                    l.add(new String[]{p.get("name"), p.get("url")});
                    prefs.saveSmartTiles(l); refreshHome(); return ok();
                }
                case "/api/smart/del": {
                    int i = Integer.parseInt(p.get("i"));
                    List<String[]> l = prefs.smartTiles();
                    if (i < l.size()) l.remove(i);
                    prefs.saveSmartTiles(l); refreshHome(); return ok();
                }
                case "/api/nvr": {
                    int n = 4; try { n = Integer.parseInt(p.get("ch")); } catch (Exception ignored) { }
                    prefs.saveNvr(p.get("ip"), p.get("user"), p.get("pass"), n); refreshHome(); return ok();
                }
                case "/api/cam/name": { prefs.setCamName(Integer.parseInt(p.get("ch")), p.get("name")); refreshHome(); return ok(); }
                case "/api/text": {
                    String t = p.get("t");
                    boolean done = HomeKeyService.typeText(t != null ? t : "");
                    return json(new JSONObject().put("ok", done));
                }
                case "/api/key": {
                    String k = p.get("k");
                    AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
                    switch (k) {
                        case "home": start(new Intent(ctx, MainActivity.class)); break;
                        case "back": HomeKeyService.global(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK); break;
                        case "volup": am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI); break;
                        case "voldown": am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI); break;
                        case "mute": am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_SHOW_UI); break;
                        case "up": case "down": case "left": case "right": case "ok":
                            HomeKeyService.dpad(k); break;
                        case "settings": start(new Intent(android.provider.Settings.ACTION_SETTINGS)); break;
                    }
                    return ok();
                }
                case "/api/wallpaper": {
                    Map<String, String> files = new HashMap<>();
                    s.parseBody(files);
                    String tmp = files.get("file");
                    if (tmp != null) {
                        File dst = new File(ctx.getFilesDir(), "wallpaper_custom.jpg");
                        java.nio.file.Files.copy(new File(tmp).toPath(), dst.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        prefs.setWallpaperPath(dst.getPath());
                        refreshHome();
                    }
                    return ok();
                }
                case "/api/wallpaper/reset": { prefs.setWallpaperPath(""); refreshHome(); return ok(); }
            }
            return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "404");
        } catch (Exception e) {
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "err: " + e);
        }
    }

    private JSONObject state() throws Exception {
        Map<String, AppItem> all = Catalog.apps(ctx);
        JSONObject o = new JSONObject();
        JSONArray apps = new JSONArray();
        List<AppItem> list = new ArrayList<>(all.values());
        Collections.sort(list, (x, y) -> x.label.compareToIgnoreCase(y.label));
        List<String> favs = prefs.favorites();
        for (AppItem a : list) {
            if (prefs.hidden().contains(a.id)) continue;
            apps.put(new JSONObject().put("id", a.id).put("label", a.label).put("fav", favs.contains(a.id)).put("icon", iconB64(a)));
        }
        o.put("apps", apps);
        JSONArray src = new JSONArray();
        for (AppItem a : Catalog.sources(ctx, all)) src.put(new JSONObject().put("id", a.id).put("label", a.label));
        o.put("sources", src);
        JSONArray cams = new JSONArray();
        if (!prefs.nvrIp().isEmpty()) for (int ch = 1; ch <= prefs.nvrChannels(); ch++) cams.put(new JSONObject().put("ch", ch).put("name", prefs.camName(ch)));
        o.put("cams", cams);
        o.put("nvr", new JSONObject().put("ip", prefs.nvrIp()).put("user", prefs.nvrUser()).put("ch", prefs.nvrChannels()));
        JSONArray smart = new JSONArray();
        for (String[] t : prefs.smartTiles()) smart.put(new JSONObject().put("name", t[0]).put("url", t[1]));
        o.put("smart", smart);
        o.put("accessibility", HomeKeyService.isRunning());
        return o;
    }

    private String iconB64(AppItem a) {
        try {
            Bitmap bm = iconCache.get(a.id);
            if (bm == null) {
                Drawable d = a.icon;
                int w = 160, h = d instanceof BitmapDrawable && ((BitmapDrawable) d).getBitmap().getWidth() > ((BitmapDrawable) d).getBitmap().getHeight() * 1.3 ? 90 : 160;
                bm = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                Canvas c = new Canvas(bm);
                d.setBounds(0, 0, w, h);
                d.draw(c);
                iconCache.put(a.id, bm);
            }
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            bm.compress(Bitmap.CompressFormat.PNG, 80, bo);
            return "data:image/png;base64," + Base64.encodeToString(bo.toByteArray(), Base64.NO_WRAP);
        } catch (Exception e) { return ""; }
    }

    private void start(Intent i) {
        main.post(() -> {
            try {
                Intent x = new Intent(i);
                x.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                ctx.startActivity(x);
            } catch (Exception ignored) { }
        });
    }

    private void refreshHome() { main.post(() -> { if (MainActivity.instance != null) MainActivity.instance.rebuild(); }); }

    private Response html(String asset) throws Exception {
        try (InputStream in = ctx.getAssets().open(asset)) {
            byte[] b = new byte[in.available()];
            int n = in.read(b);
            return newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", new ByteArrayInputStream(b, 0, n), n);
        }
    }

    private Response json(JSONObject o) { return newFixedLengthResponse(Response.Status.OK, "application/json", o.toString()); }
    private Response ok() { return newFixedLengthResponse(Response.Status.OK, "application/json", "{\"ok\":true}"); }
}
