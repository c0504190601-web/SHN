package com.kioskmdm;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Experimental alternative shade for ROM camera shortcut intents.
 * It is an Activity in the existing lock-task allowlisted package:
 * no accessibility overlay or root permission is required.
 */
public class CameraShadeActivity extends Activity {
    private final Handler handler=new Handler(Looper.getMainLooper());
    private Tile wifi,bluetooth,rotation,mobile;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (!Prefs.setup(this)) { finish(); return; }
        KeyguardManager guard=(KeyguardManager)getSystemService(KEYGUARD_SERVICE);
        // Secure camera gestures can originate at the keyguard. Never expose controls there.
        if (guard!=null && guard.isKeyguardLocked()) { finish(); return; }

        LinearLayout root=UI.root(this,"הגדרות מהירות");
        root.addView(UI.note(this,"וילון חלופי שאינו תלוי בשירות הנגישות. ניסיון פתיחה בלחיצה כפולה על כפתור ההדלקה."));

        LinearLayout controls=UI.card(this,root,"חיבורים ותצוגה");
        LinearLayout first=row();
        wifi=new Tile("Wi-Fi",ControlIcon.WIFI,()->DeviceControls.toggleWifi(this));
        bluetooth=new Tile("Bluetooth",ControlIcon.BLUETOOTH,()->DeviceControls.toggleBluetooth(this));
        addTile(first,wifi);addTile(first,bluetooth);controls.addView(first);
        LinearLayout second=row();
        rotation=new Tile("סיבוב מסך",ControlIcon.ROTATION,
                ()->DeviceControls.setRotation(this,DeviceControls.rotationState(this)!=DeviceControls.ON));
        mobile=new Tile("נתונים",ControlIcon.CLOCK,()->openSettings());
        addTile(second,rotation);addTile(second,mobile);controls.addView(second);

        Button settings=UI.b(this,"פתיחת כל ההגדרות");
        settings.setOnClickListener(v->openSettings());root.addView(settings);

        Button back=UI.secondary(this,"חזרה לאפליקציה");
        back.setOnClickListener(v->returnToKiosk());root.addView(back);
        refresh();
    }

    private LinearLayout row() {
        LinearLayout row=new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0,UI.dp(this,6),0,UI.dp(this,6));
        return row;
    }
    private void addTile(LinearLayout row,Tile tile) {
        LinearLayout.LayoutParams layout=new LinearLayout.LayoutParams(0,UI.dp(this,125),1);
        layout.setMargins(UI.dp(this,3),0,UI.dp(this,3),0);
        row.addView(tile,layout);
    }
    private void refresh() {
        if(wifi==null)return;
        wifi.update(DeviceControls.wifiState(this));
        bluetooth.update(DeviceControls.bluetoothState(this));
        rotation.update(DeviceControls.rotationState(this));
        mobile.updateMobile(DeviceControls.cellularConnected(this));
    }
    private void openSettings() {
        try { startActivity(new Intent(this,SettingsActivity.class)); }
        catch(RuntimeException e) { UI.msg(this,"מסך ההגדרות אינו זמין כרגע"); }
    }
    private void returnToKiosk() {
        Intent target=getPackageManager().getLaunchIntentForPackage(Prefs.main(this));
        try {
            if (target!=null && !Prefs.maintenance(this)) {
                target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                startActivity(target);
            } else startActivity(new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
        } catch(RuntimeException ignored) { }
        finish();
    }
    @Override protected void onResume() {super.onResume();refresh();}
    @Override protected void onDestroy() { handler.removeCallbacksAndMessages(null);super.onDestroy(); }
    @Override public void onBackPressed(){returnToKiosk();}

    private class Tile extends LinearLayout {
        private final ControlIcon control;
        private final TextView name,state;
        Tile(String label,int kind,Runnable action) {
            super(CameraShadeActivity.this);
            setOrientation(VERTICAL);
            setGravity(Gravity.CENTER);
            setPadding(UI.dp(getContext(),8),UI.dp(getContext(),12),UI.dp(getContext(),8),UI.dp(getContext(),12));
            setBackground(UI.bg(Color.WHITE,UI.dp(getContext(),20)));
            FrameLayout bubble=new FrameLayout(getContext());
            control=new ControlIcon(getContext(),kind);
            bubble.addView(control,new FrameLayout.LayoutParams(UI.dp(getContext(),30),UI.dp(getContext(),30),Gravity.CENTER));
            addView(bubble,new LinearLayout.LayoutParams(-1,UI.dp(getContext(),40)));
            name=UI.note(CameraShadeActivity.this,label);name.setTextSize(16);name.setGravity(Gravity.CENTER);
            name.setPadding(0,0,0,0);addView(name);
            state=UI.note(CameraShadeActivity.this,"");state.setGravity(Gravity.CENTER);
            state.setPadding(0,0,0,0);addView(state);
            setOnClickListener(v->{action.run();refresh();handler.postDelayed(CameraShadeActivity.this::refresh,500);});
        }
        void update(int value) {
            boolean on=value==DeviceControls.ON;
            setBackground(UI.bg(on?UI.ACCENT:Color.WHITE,UI.dp(getContext(),20)));
            control.tint(on?Color.WHITE:UI.ACCENT);
            name.setTextColor(on?Color.WHITE:UI.INK);
            state.setText(DeviceControls.stateLabel(value));
            state.setTextColor(on?0xffe2eaff:UI.MUTED);
            setEnabled(value!=DeviceControls.CHANGING && value!=DeviceControls.UNAVAILABLE);
            setAlpha(value==DeviceControls.UNAVAILABLE?.6f:1f);
        }
        void updateMobile(boolean connected) {
            setBackground(UI.bg(connected?UI.ACCENT:Color.WHITE,UI.dp(getContext(),20)));
            control.tint(connected?Color.WHITE:UI.ACCENT);
            name.setTextColor(connected?Color.WHITE:UI.INK);
            state.setText(connected?"מחובר":"לא מחובר");
            state.setTextColor(connected?0xffe2eaff:UI.MUTED);
        }
    }
}
