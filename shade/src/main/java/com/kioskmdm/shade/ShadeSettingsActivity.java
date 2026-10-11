package com.kioskmdm.shade;
import android.app.Activity;import android.content.Intent;import android.os.Bundle;import android.provider.Settings;import android.view.View;import android.widget.*;
public class ShadeSettingsActivity extends Activity{
 LinearLayout panel;
 void add(String name,String action){Button b=new Button(this);b.setText(name);b.setOnClickListener(v->{try{startActivity(new Intent(action));}catch(Exception e){Toast.makeText(this,"האפשרות אינה זמינה",Toast.LENGTH_SHORT).show();}});panel.addView(b);}
 public void onCreate(Bundle state){super.onCreate(state);panel=new LinearLayout(this);panel.setOrientation(1);panel.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);panel.setPadding(18,30,18,18);setContentView(panel);
 TextView title=new TextView(this);title.setText("הגדרות הווילון");title.setTextSize(24);panel.addView(title);
 add("רשת Wi-Fi",Settings.ACTION_WIFI_SETTINGS);
 add("Bluetooth",Settings.ACTION_BLUETOOTH_SETTINGS);
 add("SIM ונתונים סלולריים",Settings.ACTION_DATA_ROAMING_SETTINGS);
 add("הרשאות נגישות",Settings.ACTION_ACCESSIBILITY_SETTINGS);
 add("הרשאות סיבוב ובהירות",Settings.ACTION_MANAGE_WRITE_SETTINGS);
 Button back=new Button(this);back.setText("חזרה");back.setOnClickListener(v->finish());panel.addView(back);
 }
}