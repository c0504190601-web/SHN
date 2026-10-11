package com.kioskmdm.shade;
import android.accessibilityservice.AccessibilityService;import android.app.*;import android.content.*;import android.graphics.PixelFormat;import android.os.*;import android.provider.Settings;import android.view.*;import android.view.accessibility.AccessibilityEvent;import android.widget.*;
public class ShadeService extends AccessibilityService {
 WindowManager wm;View edge,battery,shade;Handler h=new Handler(Looper.getMainLooper());
 final Runnable refresh=new Runnable(){public void run(){update();h.postDelayed(this,2500);}};
 WindowManager.LayoutParams pos(int w,int ht,int gravity,int y){WindowManager.LayoutParams p=new WindowManager.LayoutParams(w,ht,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT);p.gravity=gravity;p.y=y;return p;}
 void add(View v,WindowManager.LayoutParams p){try{wm.addView(v,p);}catch(Exception ignored){}}
 @Override protected void onServiceConnected(){super.onServiceConnected();wm=(WindowManager)getSystemService(WINDOW_SERVICE);TextView grip=new TextView(this);grip.setText("━━━━");grip.setGravity(Gravity.CENTER);grip.setTextColor(0xff60738e);grip.setOnClickListener(v->open());edge=grip;add(edge,pos(-1,60,Gravity.TOP,0));TextView bat=new TextView(this);bat.setTextColor(0xff172f46);bat.setTextSize(14);bat.setPadding(8,3,8,3);bat.setBackgroundColor(0xbbffffff);bat.setOnClickListener(v->open());battery=bat;add(battery,pos(-2,-2,Gravity.TOP|Gravity.RIGHT,52));h.post(refresh);}
 void update(){if(battery instanceof TextView){Intent i=registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));if(i!=null){int x=i.getIntExtra(BatteryManager.EXTRA_LEVEL,-1),scale=i.getIntExtra(BatteryManager.EXTRA_SCALE,100);((TextView)battery).setText(scale>0?Math.round(x*100f/scale)+"%":"--%");}}}
 void open(){if(shade!=null)return;LinearLayout l=new LinearLayout(this);l.setOrientation(1);l.setPadding(20,20,20,20);l.setBackgroundColor(0xfff2f6fb);l.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);TextView title=new TextView(this);title.setText("הגדרות מהירות");title.setTextSize(24);l.addView(title);
 button(l,"Wi-Fi",()->start(Settings.ACTION_WIFI_SETTINGS));button(l,"Bluetooth",()->start(Settings.ACTION_BLUETOOTH_SETTINGS));
 button(l,"נתונים סלולריים",()->start(Settings.ACTION_DATA_ROAMING_SETTINGS));
 button(l,"סיבוב מסך",()->{if(Settings.System.canWrite(this)){int v=Settings.System.getInt(getContentResolver(),Settings.System.ACCELEROMETER_ROTATION,0);Settings.System.putInt(getContentResolver(),Settings.System.ACCELEROMETER_ROTATION,1-v);}else start(Settings.ACTION_MANAGE_WRITE_SETTINGS);});
 button(l,"⚙ הגדרות",()->{close();Intent i=new Intent(this,ShadeSettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);startActivity(i);});
 button(l,"סגור",this::close);shade=l;add(shade,pos(-1,-2,Gravity.TOP,50));}
 void button(LinearLayout l,String label,Runnable a){Button b=new Button(this);b.setText(label);b.setOnClickListener(v->a.run());l.addView(b);}
 void start(String action){try{Intent i=new Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);startActivity(i);}catch(Exception e){Toast.makeText(this,"לא ניתן לפתוח הגדרה",0).show();}}
 void close(){if(shade!=null){try{wm.removeView(shade);}catch(Exception e){}shade=null;}}
 public void onAccessibilityEvent(AccessibilityEvent e){}public void onInterrupt(){}
 @Override public void onDestroy(){h.removeCallbacks(refresh);close();for(View v:new View[]{edge,battery})if(v!=null)try{wm.removeView(v);}catch(Exception e){}super.onDestroy();}
}