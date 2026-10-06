package com.kioskmdm;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.KeyguardManager;
import android.content.*;
import android.graphics.PixelFormat;
import android.os.*;
import android.view.*;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.widget.TextView;

/** Observes key events only. Does not read screen contents or consume normal Back. */
public class BackKeyService extends AccessibilityService {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private WindowManager windows;
    private View gear;
    private boolean receiverRegistered;
    private static BackKeyService instance;
    private final BroadcastReceiver screenOff = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { cancel(); hide(); }
    };

    static boolean enabled(Context context) {
        AccessibilityManager manager = (AccessibilityManager)
                context.getSystemService(ACCESSIBILITY_SERVICE);
        for (AccessibilityServiceInfo info : manager.getEnabledAccessibilityServiceList(
                AccessibilityServiceInfo.FEEDBACK_ALL_MASK)) {
            if (info.getResolveInfo() != null && new ComponentName(context, BackKeyService.class)
                    .equals(new ComponentName(info.getResolveInfo().serviceInfo.packageName,
                            info.getResolveInfo().serviceInfo.name))) return true;
        }
        return false;
    }

    static void setTesting(boolean value) {
        testing = value;
        if (instance != null) instance.refreshGear();
    }

    static void dismissButton() {
        if (instance != null) instance.hide();
    }

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        windows = (WindowManager) getSystemService(WINDOW_SERVICE);
        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(screenOff, filter, RECEIVER_NOT_EXPORTED);
        else registerReceiver(screenOff, filter);
        receiverRegistered = true;
        refreshGear();
    }

    private void show() {
        hide();
        TextView button = new TextView(this);
        button.setText("⚙");
        button.setContentDescription("פתיחת הגדרות הקיוסק");
        button.setTextSize(27);
        button.setTextColor(0xffffffff);
        button.setGravity(Gravity.CENTER);
        button.setBackground(UI.bg(UI.ACCENT, UI.dp(this, 18)));
        button.setElevation(UI.dp(this, 6));
        button.setOnClickListener(v -> {
            if (!Prefs.setup(this) || Prefs.maintenance(this)) return;
            try {
                startActivity(new Intent(this, SettingsActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
            } catch (RuntimeException exception) { UI.msg(this, "לא ניתן לפתוח הגדרות כעת"); }
        });
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                UI.dp(this, 56), UI.dp(this, 56), WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.BOTTOM | Gravity.START;
        params.x = UI.dp(this, 8);
        params.y = UI.dp(this, 8);
        try {
            windows.addView(button, params);
            gear = button;
        } catch (RuntimeException exception) { UI.msg(this, "לא ניתן להציג את כפתור ההגדרות"); }
    }

    private void hide() {
        if (gear != null && windows != null) {
            try { windows.removeView(gear); } catch (RuntimeException ignored) { }
        }
        gear = null;
    }

    private void refreshGear() {
        if (Prefs.setup(this) && !Prefs.maintenance(this)) show(); else hide();
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) { refreshGear(); }
    @Override public void onInterrupt() { hide(); }
    @Override public void onDestroy() {
        hide();
        if (receiverRegistered) unregisterReceiver(screenOff);
        if (instance == this) instance = null;
        super.onDestroy();
    }
}
