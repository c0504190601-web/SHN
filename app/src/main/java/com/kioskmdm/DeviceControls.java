package com.kioskmdm;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.provider.Settings;

/** Shared controls read actual device state; toggles never fake a successful change. */
final class DeviceControls {
    static final int OFF = 0, ON = 1, CHANGING = 2, UNAVAILABLE = 3, NEED_PERMISSION = 4;

    private static WifiManager wifi(Context c) {
        return (WifiManager) c.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
    }
    private static BluetoothAdapter bluetooth(Context c) {
        BluetoothManager manager = (BluetoothManager) c.getSystemService(Context.BLUETOOTH_SERVICE);
        return manager == null ? null : manager.getAdapter();
    }
    static boolean bluetoothPermission(Context c) {
        return Build.VERSION.SDK_INT < 31 || c.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                == PackageManager.PERMISSION_GRANTED;
    }
    static int wifiState(Context c) {
        try {
            WifiManager manager = wifi(c);
            if (manager == null) return UNAVAILABLE;
            int state = manager.getWifiState();
            if (state == WifiManager.WIFI_STATE_ENABLED) return ON;
            if (state == WifiManager.WIFI_STATE_DISABLED) return OFF;
            if (state == WifiManager.WIFI_STATE_ENABLING || state == WifiManager.WIFI_STATE_DISABLING) return CHANGING;
        } catch (RuntimeException ignored) { }
        return UNAVAILABLE;
    }
    @SuppressLint("MissingPermission")
    static int bluetoothState(Context c) {
        if (!bluetoothPermission(c)) return NEED_PERMISSION;
        try {
            BluetoothAdapter adapter = bluetooth(c);
            if (adapter == null) return UNAVAILABLE;
            int state = adapter.getState();
            if (state == BluetoothAdapter.STATE_ON) return ON;
            if (state == BluetoothAdapter.STATE_OFF) return OFF;
            return CHANGING;
        } catch (RuntimeException ignored) { return UNAVAILABLE; }
    }
    static int rotationState(Context c) {
        if (!Settings.System.canWrite(c)) return NEED_PERMISSION;
        return Settings.System.getInt(c.getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, 0) == 1 ? ON : OFF;
    }
    static void toggleWifi(Context c) {
        int state = wifiState(c);
        if (state == CHANGING) return;
        try {
            if (state == UNAVAILABLE || !wifi(c).setWifiEnabled(state != ON)) UI.msg(c, "לא ניתן לשנות את מצב ה־Wi-Fi");
        } catch (RuntimeException exception) { UI.msg(c, "אין אפשרות לשנות Wi-Fi במכשיר הזה"); }
    }
    @SuppressLint("MissingPermission")
    static void toggleBluetooth(Context c) {
        int state = bluetoothState(c);
        if (state == NEED_PERMISSION) { UI.msg(c, "יש לאשר הרשאת Bluetooth בהגדרות"); return; }
        if (state == CHANGING) return;
        try {
            BluetoothAdapter adapter = bluetooth(c);
            if (adapter == null || !(state == ON ? adapter.disable() : adapter.enable()))
                UI.msg(c, "לא ניתן לשנות את מצב ה־Bluetooth");
        } catch (RuntimeException exception) { UI.msg(c, "אין אפשרות לשנות Bluetooth במכשיר הזה"); }
    }
    static boolean setRotation(Context c, boolean on) {
        if (!Settings.System.canWrite(c)) { UI.msg(c, "יש לאשר שינוי הגדרות תצוגה דרך הגדרות המנהל"); return false; }
        try {
            return Settings.System.putInt(c.getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, on ? 1 : 0);
        } catch (RuntimeException exception) { UI.msg(c, "לא ניתן לשנות את סיבוב המסך"); return false; }
    }
    static String stateLabel(int state) {
        if (state == ON) return "פעיל";
        if (state == OFF) return "כבוי";
        if (state == CHANGING) return "משנה מצב…";
        if (state == NEED_PERMISSION) return "נדרש אישור";
        return "לא זמין";
    }
}
