package com.kioskmdm;
import android.app.*;import android.content.*;import android.os.*;
public final class ShadeEntryActivity extends Activity {
 @Override protected void onCreate(Bundle b){
  super.onCreate(b);
  KeyguardManager k=(KeyguardManager)getSystemService(KEYGUARD_SERVICE);
  if(Prefs.setup(this)&&(k==null||!k.isKeyguardLocked())){
   try{startActivity(new Intent(this,SettingsActivity.class));}catch(RuntimeException ignored){}
  }
  finish();
 }
}