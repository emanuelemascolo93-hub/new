package it.emmeweb.tvlauncher;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;

import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** App installate e sorgenti collegate — usato sia dalla home sia dal telecomando web. */
public class Catalog {

    public static Map<String, AppItem> apps(Context c) {
        PackageManager pm = c.getPackageManager();
        Map<String, AppItem> all = new HashMap<>();
        Intent q = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        for (ResolveInfo ri : pm.queryIntentActivities(q, 0)) add(pm, all, ri, true);
        Intent q2 = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        for (ResolveInfo ri : pm.queryIntentActivities(q2, 0)) add(pm, all, ri, false);
        all.remove(c.getPackageName());
        return all;
    }

    private static void add(PackageManager pm, Map<String, AppItem> all, ResolveInfo ri, boolean leanback) {
        String pkg = ri.activityInfo.packageName;
        if (all.containsKey(pkg)) return;
        Drawable icon = null;
        if (leanback) {
            try { icon = ri.activityInfo.loadBanner(pm); } catch (Exception ignored) { }
            if (icon == null) try { icon = pm.getApplicationBanner(pkg); } catch (Exception ignored) { }
        }
        if (icon == null) icon = ri.loadIcon(pm);
        Intent launch = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (launch == null) launch = pm.getLaunchIntentForPackage(pkg);
        if (launch == null) return;
        all.put(pkg, new AppItem(pkg, ri.loadLabel(pm).toString(), icon, launch, true));
    }

    /** Solo ingressi con un dispositivo collegato + TV digitale + USB. */
    public static List<AppItem> sources(Context c, Map<String, AppItem> all) {
        List<AppItem> l = new ArrayList<>();
        try {
            TvInputManager tim = (TvInputManager) c.getSystemService(Context.TV_INPUT_SERVICE);
            for (TvInputInfo in : tim.getTvInputList()) {
                if (!in.isPassthroughInput()) continue;
                String id = in.getId();
                if (tim.getInputState(id) != TvInputManager.INPUT_STATE_CONNECTED) continue;
                String port = String.valueOf(in.loadLabel(c));
                CharSequence custom = in.loadCustomLabel(c);
                String device = custom != null && custom.length() > 0 && !custom.toString().equals(port) ? custom.toString() : "";
                try {
                    Object hdmi = TvInputInfo.class.getMethod("getHdmiDeviceInfo").invoke(in);
                    if (hdmi != null) {
                        Object name = hdmi.getClass().getMethod("getDisplayName").invoke(hdmi);
                        if (name != null && !name.toString().trim().isEmpty()) device = name.toString().trim();
                    }
                } catch (Exception ignored) { }
                String label = device.isEmpty() ? port : device + " · " + port;
                Intent i = new Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(id));
                l.add(new AppItem("src:" + id, label, ContextCompat.getDrawable(c, iconFor(device, port)), i, false));
            }
        } catch (Exception ignored) { }
        AppItem tv = all.get("com.tcl.tv");
        if (tv != null) l.add(0, new AppItem("src:dtv", "Canali TV", ContextCompat.getDrawable(c, R.drawable.ic_tv), tv.intent, false));
        AppItem usb = all.get("com.tcl.ui_mediaCenter");
        if (usb != null) l.add(new AppItem("src:usb", "USB", ContextCompat.getDrawable(c, R.drawable.ic_usb), usb.intent, false));
        return l;
    }

    private static int iconFor(String device, String port) {
        String d = (device + " " + port).toLowerCase();
        if (d.contains("playstation") || d.contains("ps5") || d.contains("ps4") || d.contains("xbox") || d.contains("switch") || d.contains("nintendo")) return R.drawable.ic_console;
        if (d.contains("pc") || d.contains("nvidia") || d.contains("amd") || d.contains("desktop") || d.contains("laptop") || d.contains("computer")) return R.drawable.ic_pc;
        if (d.contains("sound") || d.contains("bar") || d.contains("audio") || d.contains("arc") || d.contains("receiver") || d.contains("denon") || d.contains("yamaha") || d.contains("sonos")) return R.drawable.ic_speaker;
        if (d.contains("fire") || d.contains("chromecast") || d.contains("apple") || d.contains("sky") || d.contains("decoder") || d.contains("box")) return R.drawable.ic_box;
        return R.drawable.ic_hdmi;
    }
}
