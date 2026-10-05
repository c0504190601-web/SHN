package com.kioskmdm;

import android.app.Activity;
import android.content.*;
import android.content.pm.*;
import android.os.*;
import android.text.InputType;
import android.widget.*;
import java.util.*;

public class MainActivity extends Activity {
    private EditText pin, pin2;
    private final ArrayList<String> packages = new ArrayList<>();
    private Spinner apps;
    private LinearLayout root;
    private boolean opening;
    private long lastLaunch;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (!Prefs.setup(this)) setup();
        else lockedSurface();
    }

    @Override protected void onResume() {
        super.onResume();
        opening = false;
        if (Prefs.setup(this)) route();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        // Never leave old setup controls behind a returning application.
        if (Prefs.setup(this)) lockedSurface();
    }

    private void setup() {
        root = UI.root(this, "ברוכים הבאים");
        root.addView(UI.note(this, Policy.owner(this) ? "הרשאת ניהול המכשיר פעילה"
                : "לפני הפעלת הקיוסק יש להגדיר את האפליקציה כ־Device Owner"));
        LinearLayout security = UI.card(this, root, "קוד מנהל");
        pin = pinField("קוד מנהל — לפחות 4 ספרות");
        pin2 = pinField("הקלד את הקוד שוב");
        security.addView(pin); security.addView(pin2);
        LinearLayout protections = UI.card(this, root, "הגנות המכשיר");
        option(protections, "חסום איפוס להגדרות יצרן", "reset", true);
        option(protections, "חסום מצב בטוח", "safe", true);
        option(protections, "חסום ADB ואפשרויות מפתחים", "adb", false);
        option(protections, "חסום התקנה ממקורות לא ידועים", "install", true);
        option(protections, "חסום הסרת אפליקציות", "uninstall", true);
        option(protections, "חסום הוספת משתמשים", "users", true);
        option(protections, "חסום שורת מצב והתראות", "status", true);
        LinearLayout main = UI.card(this, root, "האפליקציה הראשית");
        PackageManager pm = getPackageManager();
        Intent query = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        ArrayList<String> names = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ResolveInfo info : pm.queryIntentActivities(query, 0)) {
            String pkg = info.activityInfo.packageName;
            if (pkg.equals(getPackageName()) || !seen.add(pkg) || pm.getLaunchIntentForPackage(pkg) == null) continue;
            packages.add(pkg); names.add(info.loadLabel(pm).toString());
        }
        apps = new Spinner(this);
        apps.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, names));
        main.addView(apps);
        Button access = UI.secondary(this, "הכנת לחיצה ארוכה וסיבוב מסך");
        access.setOnClickListener(v -> startActivity(new Intent(this, AccessSetupActivity.class)));
        root.addView(access);
        root.addView(UI.note(this, "בדוק את קיצור החזור לפני הנעילה. אם הוא אינו זמין, תישאר כניסת מנהל מוגנת בקוד במסך הקיוסק."));
        Button finish = UI.b(this, "שמירה והפעלת הקיוסק");
        finish.setOnClickListener(v -> finishSetup());
        root.addView(finish);
    }

    private EditText pinField(String hint) {
        EditText field = new EditText(this);
        field.setHint(hint);
        field.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        return field;
    }

    private void option(LinearLayout parent, String title, String key, boolean defaultValue) {
        Switch option = UI.sw(this, title, Prefs.opt(this, key, defaultValue));
        option.setOnCheckedChangeListener((v, on) -> Prefs.setOpt(this, key, on));
        parent.addView(option);
    }

    private void finishSetup() {
        if (!Policy.owner(this)) { UI.msg(this, "הרשאת Device Owner אינה פעילה"); return; }
        String password = pin.getText().toString();
        if (!password.matches("[0-9]{4,}")) { UI.msg(this, "הקוד צריך להכיל לפחות 4 ספרות"); return; }
        if (!password.equals(pin2.getText().toString())) { UI.msg(this, "הקודים אינם זהים"); return; }
        int position = apps.getSelectedItemPosition();
        if (position < 0 || position >= packages.size()) { UI.msg(this, "בחר אפליקציה ראשית"); return; }
        String pkg = packages.get(position);
        if (getPackageManager().getLaunchIntentForPackage(pkg) == null) { UI.msg(this, "האפליקציה אינה זמינה"); return; }
        Prefs.finish(this, pkg, password);
        // Replace the entire view before the other app starts, including on the same Activity instance.
        pin.setText(""); pin2.setText("");
        pin = null; pin2 = null; apps = null; packages.clear();
        lockedSurface();
        Policy.apply(this, pkg);
        route();
    }

    private void lockedSurface() {
        root = UI.root(this, "סביבת העבודה מוגנת");
        root.addView(UI.note(this, "פותח את האפליקציה הראשית…"));
    }

    private void route() {
        if (Prefs.maintenance(this)) { maintenance(); return; }
        lockedSurface();
        ensureLockTask();
        if (!BackKeyService.enabled(this) || !Prefs.opt(this, "back_hold_tested", false)) {
            recovery("קיצור החזור עדיין לא הוגדר או לא זוהה במכשיר הזה. מנהל יכול להשלים את ההגדרה באמצעות הקוד.");
            return;
        }
        if (lastLaunch > 0 && SystemClock.uptimeMillis() - lastLaunch < 1200) {
            recovery("האפליקציה הראשית נסגרה. אפשר לפתוח אותה מחדש או להיכנס למנהל.");
            return;
        }
        launch();
    }

    private void launch() {
        if (opening || Prefs.maintenance(this)) return;
        String pkg = Prefs.main(this);
        Intent intent = getPackageManager().getLaunchIntentForPackage(pkg);
        if (intent == null) { recovery("האפליקציה הראשית אינה זמינה. כניסה לניהול דורשת את קוד המנהל."); return; }
        lockedSurface();
        Policy.applyBlocked(this);
        try {
            ensureLockTask();
            opening = true;
            lastLaunch = SystemClock.uptimeMillis();
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            startActivity(intent);
        } catch (RuntimeException exception) {
            opening = false;
            recovery("לא ניתן לפתוח את האפליקציה הראשית. נדרשת בדיקת מנהל.");
        }
    }

    private void ensureLockTask() {
        if (Prefs.maintenance(this)) return;
        try {
            android.app.ActivityManager manager = (android.app.ActivityManager) getSystemService(ACTIVITY_SERVICE);
            if (Policy.owner(this) && Policy.d(this).isLockTaskPermitted(getPackageName())
                    && manager.getLockTaskModeState() == android.app.ActivityManager.LOCK_TASK_MODE_NONE) {
                startLockTask();
            }
        } catch (RuntimeException exception) { UI.msg(this, "לא ניתן להפעיל נעילת קיוסק כעת"); }
    }

    private void recovery(String message) {
        root = UI.root(this, "המכשיר מוגן");
        root.addView(UI.note(this, message));
        Button launch = UI.b(this, "פתיחת האפליקציה הראשית");
        launch.setOnClickListener(v -> launch());
        root.addView(launch);
        Button admin = UI.secondary(this, "כניסת מנהל עם קוד");
        admin.setOnClickListener(v -> startActivity(new Intent(this, AdminActivity.class)));
        root.addView(admin);
    }

    private void maintenance() {
        root = UI.root(this, "מצב תחזוקה פעיל");
        root.addView(UI.note(this, "אפשר לסיים את התחזוקה ולחזור לסביבת העבודה בכל רגע."));
        Button end = UI.b(this, "סיום תחזוקה וחזרה לקיוסק");
        end.setOnClickListener(v -> { Policy.maintenance(this, false); lastLaunch = 0; route(); });
        root.addView(end);
        Button admin = UI.secondary(this, "כניסת מנהל");
        admin.setOnClickListener(v -> startActivity(new Intent(this, AdminActivity.class)));
        root.addView(admin);
    }

    @Override public void onBackPressed() {
        if (Prefs.setup(this)) { if (!Prefs.maintenance(this)) launch(); }
        else super.onBackPressed();
    }
}
