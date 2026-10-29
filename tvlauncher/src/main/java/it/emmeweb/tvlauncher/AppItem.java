package it.emmeweb.tvlauncher;

import android.content.Intent;
import android.graphics.drawable.Drawable;

public class AppItem {
    public final String id;        // package name, or "sys:xxx" for system shortcuts
    public final String label;
    public final Drawable icon;
    public final Intent intent;
    public final boolean isApp;

    public AppItem(String id, String label, Drawable icon, Intent intent, boolean isApp) {
        this.id = id;
        this.label = label;
        this.icon = icon;
        this.intent = intent;
        this.isApp = isApp;
    }
}
