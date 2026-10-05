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
                    "\nבדיקת 5 לחיצות: " + (Prefs.opt(AccessSetupActivity.this, "back_hold_tested", false)
                            ? "הצליחה" : "טרם זוהו 5 לחיצות") +
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
        Button accessibility = UI.b(this, "הפעלת שירות קיצור החזור");
        accessibility.setOnClickListener(v -> open(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        root.addView(accessibility);
        Button write = UI.secondary(this, "אישור שינוי סיבוב ובהירות");
        write.setOnClickListener(v -> open(new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,
                Uri.parse("package:" + getPackageName()))));
        root.addView(write);
        root.addView(UI.note(this, "לאחר ההפעלה: לחץ על חץ החזור 5 פעמים בתוך 3 שניות. אם המכשיר מעביר את המקשים לשירות, יופיע גלגל שיניים והבדיקה תסומן כהצלחה."));
        root.addView(UI.note(this, "אם המכשיר אינו מעביר את מקש החזור לשירות הנגישות, השתמש בכניסת המנהל המוגנת בקוד."));
        status = UI.note(this, "");
        root.addView(status);
        Button retest = UI.secondary(this, "איפוס בדיקת לחיצה");
        retest.setOnClickListener(v -> Prefs.setOpt(this, "back_hold_tested", false));
        root.addView(retest);
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
        BackKeyService.setTesting(true);
        handler.post(refresh);
    }
    @Override protected void onPause() {
        handler.removeCallbacks(refresh);
        BackKeyService.setTesting(false);
        super.onPause();
    }
    // Stay on the test screen while testing the Back shortcut. The visible button exits.
    @Override public void onBackPressed() { UI.msg(this, "ליציאה מהבדיקה לחץ על כפתור חזרה במסך"); }
}
