package it.emmeweb.tvlauncher;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

/** Tasto Home del telecomando + azioni per il telecomando web (testo, indietro, navigazione). */
public class HomeKeyService extends AccessibilityService {
    private static HomeKeyService instance;

    public static boolean isRunning() { return instance != null; }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(i);
    }

    @Override public void onDestroy() { instance = null; super.onDestroy(); }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_HOME) {
            if (event.getAction() == KeyEvent.ACTION_UP) {
                Intent i = new Intent(this, MainActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(i);
            }
            return true;
        }
        return super.onKeyEvent(event);
    }

    /** Scrive il testo nel campo attualmente a fuoco (ricerca Netflix/YouTube, password, URL…). */
    public static boolean typeText(String text) {
        if (instance == null) return false;
        try {
            AccessibilityNodeInfo root = instance.getRootInActiveWindow();
            if (root == null) return false;
            AccessibilityNodeInfo n = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
            if (n == null) n = findEditable(root);
            if (n == null) return false;
            Bundle b = new Bundle();
            CharSequence cur = n.getText();
            b.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, (cur == null ? "" : cur) + text);
            return n.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b);
        } catch (Exception e) { return false; }
    }

    private static AccessibilityNodeInfo findEditable(AccessibilityNodeInfo n) {
        if (n == null) return null;
        if (n.isEditable()) return n;
        for (int i = 0; i < n.getChildCount(); i++) {
            AccessibilityNodeInfo r = findEditable(n.getChild(i));
            if (r != null) return r;
        }
        return null;
    }

    public static boolean global(int action) {
        return instance != null && instance.performGlobalAction(action);
    }

    /** Navigazione: sposta il focus nel launcher o nell'app a fuoco (quando l'app lo consente). */
    public static void dpad(String dir) {
        if (instance == null) return;
        try {
            AccessibilityNodeInfo root = instance.getRootInActiveWindow();
            if (root == null) return;
            AccessibilityNodeInfo f = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
            if (f == null) f = root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY);
            if ("ok".equals(dir)) { if (f != null) f.performAction(AccessibilityNodeInfo.ACTION_CLICK); return; }
            int d = "up".equals(dir) ? android.view.View.FOCUS_UP : "down".equals(dir) ? android.view.View.FOCUS_DOWN
                    : "left".equals(dir) ? android.view.View.FOCUS_LEFT : android.view.View.FOCUS_RIGHT;
            AccessibilityNodeInfo next = f != null ? f.focusSearch(d) : null;
            if (next == null) next = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
            if (next != null) next.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
        } catch (Exception ignored) { }
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }
    @Override public void onInterrupt() { }
}
