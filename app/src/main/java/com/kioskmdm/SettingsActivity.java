package com.kioskmdm;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.database.ContentObserver;
import android.media.AudioManager;
import android.os.*;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

public class SettingsActivity extends Activity {
    private Switch rotation;
    private SeekBar brightness,volume;
    private TextView brightnessValue,volumeValue;
    private AudioManager audio;
    private boolean refreshing;
    private final ContentObserver observer=new ContentObserver(new Handler(Looper.getMainLooper())){
        @Override public void onChange(boolean selfChange){refresh();}
    };

    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        LinearLayout root=UI.root(this,"הגדרות");
        LinearLayout connections=UI.card(this,root,"חיבורים");
        systemRow(connections,"Wi-Fi","בחירת רשת וחיבור לאינטרנט",ControlIcon.WIFI,Settings.ACTION_WIFI_SETTINGS);
        systemRow(connections,"Bluetooth","חיבור לאוזניות ולמכשירים",ControlIcon.BLUETOOTH,Settings.ACTION_BLUETOOTH_SETTINGS);
        if(Build.VERSION.SDK_INT>=31&&!DeviceControls.bluetoothPermission(this)){
            Button permission=UI.secondary(this,"אישור Bluetooth");
            permission.setOnClickListener(v->requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT},1));connections.addView(permission);
        }
        LinearLayout display=UI.card(this,root,"תצוגה");
        rotation=UI.sw(this,"סיבוב מסך אוטומטי",false);
        rotation.setContentDescription("סיבוב מסך אוטומטי");
        rotation.setOnCheckedChangeListener((button,on)->{
            if(refreshing)return;
            DeviceControls.setRotation(this,on);refresh();
        });
        display.addView(rotation);
        brightnessValue=UI.note(this,"בהירות");display.addView(brightnessValue);
        brightness=new SeekBar(this);brightness.setContentDescription("בהירות מסך");brightness.setMax(254);
        brightness.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar bar,int value,boolean fromUser){
                if(!fromUser)return;
                if(!Settings.System.canWrite(SettingsActivity.this)){
                    UI.msg(SettingsActivity.this,"יש לאשר שינוי הגדרות תצוגה דרך הגדרות המנהל");refresh();return;
                }
                try{
                    Settings.System.putInt(getContentResolver(),Settings.System.SCREEN_BRIGHTNESS_MODE,Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
                    Settings.System.putInt(getContentResolver(),Settings.System.SCREEN_BRIGHTNESS,value+1);
                    brightnessValue.setText("בהירות  ·  "+Math.round((value+1)*100f/255)+"%");
                }catch(SecurityException e){refresh();}
            }
            public void onStartTrackingTouch(SeekBar bar){}
            public void onStopTrackingTouch(SeekBar bar){}
        });
        display.addView(brightness);
        LinearLayout sound=UI.card(this,root,"צלילים");
        volumeValue=UI.note(this,"עוצמת קול במדיה");sound.addView(volumeValue);
        audio=(AudioManager)getSystemService(AUDIO_SERVICE);
        volume=new SeekBar(this);volume.setContentDescription("עוצמת קול במדיה");
        volume.setMax(audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
        volume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar bar,int value,boolean fromUser){
                if(fromUser){audio.setStreamVolume(AudioManager.STREAM_MUSIC,value,0);volumeValue.setText("עוצמת קול במדיה  ·  "+Math.round(value*100f/Math.max(1,volume.getMax()))+"%");}
            }
            public void onStartTrackingTouch(SeekBar bar){}
            public void onStopTrackingTouch(SeekBar bar){}
        });
        sound.addView(volume);
        LinearLayout general=UI.card(this,root,"כללי");
        systemRow(general,"תאריך ושעה","אזור זמן וכיוון השעון",ControlIcon.CLOCK,Settings.ACTION_DATE_SETTINGS);
        actionRow(general,"אפשרויות מנהל","כניסה באמצעות קוד",ControlIcon.SETTINGS,
                ()->startActivity(new Intent(this,AdminActivity.class)));
        Button close=UI.b(this,"חזרה לאפליקציה");close.setOnClickListener(v->finish());root.addView(close);
    }
    private void systemRow(LinearLayout parent,String title,String caption,int icon,String action){
        actionRow(parent,title,caption,icon,()->{
            try{startActivity(new Intent(action));}catch(RuntimeException e){UI.msg(this,"האפשרות אינה זמינה כרגע");}
        });
    }
    private void actionRow(LinearLayout parent,String title,String caption,int kind,Runnable action){
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(UI.dp(this,6),UI.dp(this,14),UI.dp(this,6),UI.dp(this,14));
        row.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x154263df),UI.bg(android.graphics.Color.WHITE,UI.dp(this,16)),null));
        row.setContentDescription(title);row.setOnClickListener(v->action.run());
        FrameLayout bubble=new FrameLayout(this);bubble.setBackground(UI.bg(0xffeef1fe,UI.dp(this,16)));
        ControlIcon icon=new ControlIcon(this,kind);bubble.addView(icon,new FrameLayout.LayoutParams(UI.dp(this,25),UI.dp(this,25),Gravity.CENTER));
        row.addView(bubble,new LinearLayout.LayoutParams(UI.dp(this,46),UI.dp(this,46)));
        LinearLayout labels=new LinearLayout(this);labels.setOrientation(LinearLayout.VERTICAL);labels.setPadding(UI.dp(this,12),0,UI.dp(this,12),0);
        TextView name=UI.note(this,title);name.setTextSize(16);name.setTextColor(UI.INK);name.setPadding(0,0,0,UI.dp(this,4));labels.addView(name);
        TextView detail=UI.note(this,caption);detail.setTextSize(12);detail.setPadding(0,0,0,0);labels.addView(detail);
        row.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
        TextView chevron=UI.note(this,"‹");chevron.setTextSize(26);chevron.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);row.addView(chevron);
        parent.addView(row,new LinearLayout.LayoutParams(-1,-2));
    }
    @Override protected void onResume(){super.onResume();getContentResolver().registerContentObserver(Settings.System.CONTENT_URI,true,observer);BackKeyService.refreshAccess();refresh();}
    @Override protected void onPause(){getContentResolver().unregisterContentObserver(observer);super.onPause();}
    private void refresh(){
        if(rotation==null)return;
        refreshing=true;
        rotation.setChecked(Settings.System.getInt(getContentResolver(),Settings.System.ACCELEROMETER_ROTATION,0)==1);
        int value=Settings.System.getInt(getContentResolver(),Settings.System.SCREEN_BRIGHTNESS,128);
        brightness.setProgress(Math.max(0,value-1));brightnessValue.setText("בהירות  ·  "+Math.round(value*100f/255)+"%");
        int sound=audio.getStreamVolume(AudioManager.STREAM_MUSIC);volume.setProgress(sound);
        volumeValue.setText("עוצמת קול במדיה  ·  "+Math.round(sound*100f/Math.max(1,volume.getMax()))+"%");
        refreshing=false;
    }
}
