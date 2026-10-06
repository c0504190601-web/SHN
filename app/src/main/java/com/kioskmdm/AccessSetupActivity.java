package com.kioskmdm;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.widget.*;

/** Entered only during initial setup or after admin authentication in maintenance mode. */
public class AccessSetupActivity extends Activity {
    private TextView status;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refresh = new Runnable() {
        public void run() {
            if (status != null) status.setText(
                    "שירות לחיצה: " + (BackKeyService.enabled(AccessSetupActivity.this) ? "פעיל" : "כבוי") +
                    "\nשינוי הגדרות תצוגה: " + (Settings.System.canWrite(AccessSetupActivity.this)
                            ? "מאושר" : "נדרש אישור"));
            handler.postDelayed(this, 500);
        }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        // Maintenance itself can only be entered after the administrator PIN.
        if (Prefs.setup(this) && !Prefs.maintenance(this)) { finish(); return; }
        LinearLayout root = UI.root(this, "הכנת הגישה להגדרות");
        root.addView(UI.note(this, "הפעל את שירות Kiosk MDM ברשימת שירותי הנגישות. השירות מזהה מקשים בלבד ואינו קורא את תוכן המסך."));
        Button accessibility = UI.b(this, "הפעלת שירות כפתור ההגדרות");
        accessibility.setOnClickListener(v -> open(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        root.addView(accessibility);
        Button write = UI.secondary(this, "אישור שינוי סיבוב ובהירות");
        write.setOnClickListener(v -> open(new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,
                Uri.parse("package:" + getPackageName()))));
        root.addView(write);
        root.addView(UI.note(this, "לאחר הפעלת השירות יופיע כפתור ⚙ קבוע בפינה השמאלית־תחתונה. לחיצה עליו תפתח את הגדרות הקיוסק."));
        root.addView(UI.note(this, "הכניסה לניהול נשארת מוגנת בקוד המנהל."));
        status = UI.note(this, "");
        root.addView(status);
        Button done = UI.b(this, "חזרה");
        done.setOnClickListener(v -> finish());
        root.addView(done);
    }

    private void open(Intent intent) {
        try { startActivity(intent); }
        catch (RuntimeException exception) { UI.msg(this, "מסך ההרשאה אינו זמין במכשיר"); }
    }

    @Override protected void onResume() {
        super.onResume();
        if (isFinishing()) return;
        handler.post(refresh);
    }
    @Override protected void onPause() {
        handler.removeCallbacks(refresh);
        super.onPause();
    }
}
