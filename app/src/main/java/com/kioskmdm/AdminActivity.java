package com.kioskmdm;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.os.Bundle;
import android.text.InputType;
import android.widget.*;
import java.util.*;

public class AdminActivity extends Activity {
    private LinearLayout root;
    private boolean authenticated;
    private boolean submenu;

    @Override protected void onCreate(Bundle state) { super.onCreate(state); login(); }

    private EditText password(String hint) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        return input;
    }

    private void login() {
        root = UI.root(this, "כניסת מנהל");
        root.addView(UI.note(this, "ניהול המכשיר מוגן בקוד האישי שלך"));
        EditText input = password("קוד מנהל");
        root.addView(input);
        Button login = UI.b(this, "כניסה מאובטחת");
        login.setOnClickListener(v -> {
            long blocked = Prefs.p(this).getLong("pin_blocked_until", 0);
            if (System.currentTimeMillis() < blocked) { UI.msg(this, "יש להמתין 30 שניות בין ניסיונות נוספים"); return; }
            if (Prefs.pin(this, input.getText().toString())) {
                Prefs.p(this).edit().remove("pin_failures").remove("pin_blocked_until").apply();
                input.setText(""); authenticated = true; menu();
            } else {
                int fails = Prefs.p(this).getInt("pin_failures", 0) + 1;
                Prefs.p(this).edit().putInt("pin_failures", fails >= 5 ? 0 : fails)
                        .putLong("pin_blocked_until", fails >= 5 ? System.currentTimeMillis() + 30000 : 0).apply();
                input.setText(""); UI.msg(this, "קוד שגוי");
            }
        });
        root.addView(login);
        Button cancel = UI.secondary(this, "חזרה");
        cancel.setOnClickListener(v -> finish()); root.addView(cancel);
    }

    private void menu() {
        if (!authenticated) return;
        submenu = false;
        root = UI.root(this, "ניהול המכשיר");
        root.addView(UI.note(this, Prefs.maintenance(this) ? "מצב תחזוקה פעיל" : "הגנות הקיוסק פעילות"));
        button("הגנות והגבלות", this::manage);
        button("אפליקציות ואפליקציה ראשית", this::apps);
        button("שינוי קוד מנהל", this::changePin);
        button("הסרת הניהול מהמכשיר", this::removeManagement);
        button("כניסה לתחזוקה והגדרת קיצור החזור", () -> {
            enterMaintenance();
            startActivity(new Intent(this, AccessSetupActivity.class));
        });
        button("הפעלת מצב תחזוקה", () -> { enterMaintenance(); menu(); });
        button("סיום תחזוקה וחזרה לקיוסק", this::lock);
        Button close = UI.secondary(this, "סגירת הניהול");
        close.setOnClickListener(v -> finish()); root.addView(close);
    }

    private void button(String title, Runnable action) {
        Button button = UI.b(this, title);
        button.setOnClickListener(v -> action.run()); root.addView(button);
    }

    private void enterMaintenance() {
        Policy.maintenance(this, true);
        try { stopLockTask(); } catch (RuntimeException ignored) { }
        UI.msg(this, "מצב תחזוקה פעיל — ניתן לחזור לקיוסק מתפריט המנהל");
    }

    private void lock() {
        Policy.maintenance(this, false);
        authenticated = false;
        startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
        finish();
    }

    private void page(String title) {
        submenu = true;
        root = UI.root(this, title);
        Button back = UI.secondary(this, "חזרה לניהול");
        back.setOnClickListener(v -> menu()); root.addView(back);
    }

    private void manage() {
        page("הגנות והגבלות");
        option("חסום איפוס להגדרות יצרן", "reset", true);
        option("חסום מצב בטוח", "safe", true);
        option("חסום ADB ואפשרויות מפתחים", "adb", false);
        option("חסום התקנה ממקורות לא ידועים", "install", true);
        option("חסום הסרת אפליקציות", "uninstall", true);
        option("חסום הוספת משתמשים", "users", true);
        option("חסום שורת מצב והתראות", "status", true);
        button("שמירת הגדרות", () -> {
            if (!Prefs.maintenance(this)) Policy.apply(this, Prefs.main(this));
            UI.msg(this, Prefs.maintenance(this) ? "נשמר. ההגנות יחזרו בסיום התחזוקה" : "ההגדרות נשמרו");
        });
    }

    private void option(String title, String key, boolean defaultValue) {
        Switch option = UI.sw(this, title, Prefs.opt(this, key, defaultValue));
        option.setOnCheckedChangeListener((view, on) -> Prefs.setOpt(this, key, on));
        root.addView(option);
    }

    private void apps() {
        page("האפליקציות שלך");
        root.addView(UI.note(this, "בחר אפליקציה ראשית או שנה את הגישה לאפליקציות אחרות."));
        PackageManager pm = getPackageManager();
        Map<String, String> entries = new TreeMap<>();
        Intent query = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        for (ResolveInfo info : pm.queryIntentActivities(query, PackageManager.MATCH_DISABLED_COMPONENTS)) {
            String pkg = info.activityInfo.packageName;
            if (!pkg.equals(getPackageName())) entries.put(pkg, info.loadLabel(pm).toString());
        }
        // Hidden apps disappear from launcher queries; keep them available for unblocking.
        for (String pkg : Prefs.blocked(this)) if (!entries.containsKey(pkg)) {
            try { entries.put(pkg, pm.getApplicationLabel(pm.getApplicationInfo(pkg,
                    PackageManager.MATCH_UNINSTALLED_PACKAGES)).toString()); }
            catch (PackageManager.NameNotFoundException exception) { entries.put(pkg, pkg); }
        }
        for (Map.Entry<String, String> entry : entries.entrySet()) {
            String pkg = entry.getKey();
            LinearLayout card = UI.card(this, root, entry.getValue());
            card.addView(UI.note(this, pkg));
            boolean main = pkg.equals(Prefs.main(this));
            Button select = UI.secondary(this, main ? "האפליקציה הראשית הנוכחית" : "הגדרה כאפליקציה ראשית");
            select.setEnabled(!main);
            select.setOnClickListener(v -> {
                Policy.setBlocked(this, pkg, false);
                if (pm.getLaunchIntentForPackage(pkg) == null) {
                    UI.msg(this, "לא ניתן לפתוח את האפליקציה הזאת"); return;
                }
                Prefs.main(this, pkg);
                if (!Prefs.maintenance(this)) Policy.apply(this, pkg);
                apps();
            });
            card.addView(select);
            Switch blocked = UI.sw(this, "חסימת האפליקציה", Prefs.blocked(this).contains(pkg));
            blocked.setEnabled(!Policy.isProtected(this, pkg));
            blocked.setOnCheckedChangeListener((view, on) -> {
                Policy.setBlocked(this, pkg, on);
                boolean actual = Prefs.blocked(this).contains(pkg);
                if (actual != on) { UI.msg(this, "לא ניתן לשנות את חסימת האפליקציה"); apps(); }
            });
            card.addView(blocked);
        }
    }

    private void removeManagement() {
        if (!Policy.owner(this)) { UI.msg(this, "האפליקציה אינה Device Owner"); return; }
        new AlertDialog.Builder(this)
                .setTitle("הסרת ניהול")
                .setMessage("הפעולה תסיר את הרשאות הניהול והגנות הקיוסק מהמכשיר. להמשיך?")
                .setNegativeButton("ביטול", null)
                .setPositiveButton("הסר ניהול", (dialog, which) -> {
                    try {
                        enterMaintenance();
                        Policy.d(this).clearDeviceOwnerApp(getPackageName());
                        Prefs.p(this).edit().clear().apply();
                        UI.msg(this, "הניהול הוסר מהמכשיר");
                        startActivity(new Intent(this, MainActivity.class)
                                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK));
                        finish();
                    } catch (RuntimeException exception) {
                        UI.msg(this, "לא ניתן להסיר את הניהול במכשיר הזה");
                    }
                }).show();
    }

    private void changePin() {
        page("שינוי קוד מנהל");
        EditText first = password("קוד חדש — לפחות 4 ספרות");
        EditText second = password("אימות הקוד החדש");
        root.addView(first); root.addView(second);
        button("שמירת הקוד החדש", () -> {
            String value = first.getText().toString();
            if (!value.matches("[0-9]{4,}")) { UI.msg(this, "יש להזין לפחות 4 ספרות"); return; }
            if (!value.equals(second.getText().toString())) { UI.msg(this, "הקודים אינם זהים"); return; }
            Prefs.changePin(this, value); UI.msg(this, "קוד המנהל עודכן"); menu();
        });
    }

    @Override protected void onResume() {
        super.onResume();
        if (authenticated && !submenu) menu();
    }

    @Override public void onBackPressed() {
        if (authenticated && submenu) menu();
        else { authenticated = false; finish(); }
    }
}
