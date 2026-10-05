package com.kioskmdm;

import android.app.Activity;
import android.content.Intent;
import android.database.ContentObserver;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.*;

public class SettingsActivity extends Activity {
    private Switch rotation;
    private SeekBar brightness;
    private boolean refreshing;
    private final ContentObserver observer = new ContentObserver(new Handler(Looper.getMainLooper())) {
        @Override public void onChange(boolean selfChange) { refresh(); }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = UI.root(this, "הגדרות המכשיר");
        root.addView(UI.note(this, "ההעדפות שלך, במקום אחד"));
        LinearLayout connections = UI.card(this, root, "חיבורים ושעה");
        addSystem(connections, "Wi-Fi", Settings.ACTION_WIFI_SETTINGS);
        addSystem(connections, "Bluetooth", Settings.ACTION_BLUETOOTH_SETTINGS);
        addSystem(connections, "תאריך ושעה", Settings.ACTION_DATE_SETTINGS);
        LinearLayout display = UI.card(this, root, "תצוגה");
        rotation = UI.sw(this, "סיבוב מסך אוטומטי", false);
        rotation.setOnCheckedChangeListener((button, enabled) -> {
            if (refreshing) return;
            if (!Settings.System.canWrite(this)) {
                UI.msg(this, "נדרש אישור מנהל להרשאת שינוי הגדרות מערכת");
                refresh();
                return;
            }
            try {
                if (!Settings.System.putInt(getContentResolver(),
                        Settings.System.ACCELEROMETER_ROTATION, enabled ? 1 : 0)) {
                    UI.msg(this, "לא ניתן לשנות את סיבוב המסך");
                }
            } catch (SecurityException exception) {
                UI.msg(this, "אין הרשאה לשינוי סיבוב המסך");
            }
            refresh();
        });
        display.addView(rotation);
        display.addView(UI.note(this, "הסיבוב חל על אפליקציות שתומכות בשינוי כיוון המסך."));
        display.addView(UI.note(this, "בהירות מסך"));
        brightness = new SeekBar(this);
        brightness.setMax(254);
        brightness.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar bar, int value, boolean fromUser) {
                if (!fromUser) return;
                if (!Settings.System.canWrite(SettingsActivity.this)) {
                    UI.msg(SettingsActivity.this, "נדרש אישור מנהל להרשאת שינוי הגדרות מערכת");
                    refresh();
                    return;
                }
                try {
                    Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE,
                            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
                    Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, value + 1);
                } catch (SecurityException exception) { refresh(); }
            }
            public void onStartTrackingTouch(SeekBar bar) { }
            public void onStopTrackingTouch(SeekBar bar) { }
        });
        display.addView(brightness);
        LinearLayout sound = UI.card(this, root, "צלילים");
        sound.addView(UI.note(this, "עוצמת קול במדיה"));
        AudioManager audio = (AudioManager) getSystemService(AUDIO_SERVICE);
        SeekBar volume = new SeekBar(this);
        volume.setMax(audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
        volume.setProgress(audio.getStreamVolume(AudioManager.STREAM_MUSIC));
        volume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar bar, int value, boolean fromUser) {
                if (fromUser) audio.setStreamVolume(AudioManager.STREAM_MUSIC, value, 0);
            }
            public void onStartTrackingTouch(SeekBar bar) { }
            public void onStopTrackingTouch(SeekBar bar) { }
        });
        sound.addView(volume);
        Button admin = UI.secondary(this, "כניסת מנהל");
        admin.setOnClickListener(v -> startActivity(new Intent(this, AdminActivity.class)));
        root.addView(admin);
        Button close = UI.b(this, "חזרה לאפליקציה");
        close.setOnClickListener(v -> finish());
        root.addView(close);
    }

    private void addSystem(LinearLayout parent, String title, String action) {
        Button button = UI.secondary(this, title);
        button.setOnClickListener(v -> {
            try { startActivity(new Intent(action)); }
            catch (RuntimeException exception) { UI.msg(this, "האפשרות אינה זמינה במצב הנוכחי"); }
        });
        parent.addView(button);
    }

    @Override protected void onResume() {
        super.onResume();
        getContentResolver().registerContentObserver(Settings.System.CONTENT_URI, true, observer);
        refresh();
    }

    @Override protected void onPause() {
        getContentResolver().unregisterContentObserver(observer);
        super.onPause();
    }

    private void refresh() {
        refreshing = true;
        rotation.setChecked(Settings.System.getInt(getContentResolver(),
                Settings.System.ACCELEROMETER_ROTATION, 0) == 1);
        brightness.setProgress(Math.max(0, Settings.System.getInt(getContentResolver(),
                Settings.System.SCREEN_BRIGHTNESS, 128) - 1));
        refreshing = false;
    }
}
