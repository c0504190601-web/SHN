package com.kdroid.filter;
import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.os.*;
import android.provider.Settings;
import android.text.InputType;
import android.widget.*;
import java.util.*;
import java.io.*;

public class MainActivity extends Activity {
 EditText pin,pin2; ArrayList<String> pk=new ArrayList<>(),names=new ArrayList<>(); Spinner sp; LinearLayout l;
 boolean setupShown=false;

 protected void onCreate(Bundle b){
  super.onCreate(b);
  if(!Prefs.setup(this)&&Policy.owner(this)&&Prefs.romConfigured())Prefs.bootstrapFromRom(this);
  if(!Prefs.setup(this)){
   if(Policy.owner(this))setup();else enrollment();
  }else launch();
 }

 @Override protected void onResume(){
  super.onResume();
  // אחרי שהמשתמש הריץ את פקודת ADB וחזר לאפליקציה, מזהים אוטומטית את ההרשאה.
  if(!Prefs.setup(this)&&Policy.owner(this)&&!setupShown)setup();
 }

 void enrollment(){
  l=UI.root(this,"הפעלת הרשאת מנהל");
  l.addView(UI.note(this,"כדי להפעיל את ההגנה, יש להגדיר את K-Droid Filter כמנהל המכשיר (Device Owner). בחר באחת משתי הדרכים:"));
  Button adb=UI.b(this,"1. העתק פקודת ADB");
  adb.setOnClickListener(v->copyAdbCommand());
  l.addView(adb);
  l.addView(UI.note(this,"העתק את הפקודה, חבר את המכשיר למחשב עם ADB פעיל, והרץ אותה בחלון CMD/PowerShell. לאחר מכן חזור לאפליקציה."));
  Button root=UI.b(this,"2. הפעל באמצעות Root");
  root.setOnClickListener(v->activateWithRoot(root));
  l.addView(root);
  l.addView(UI.note(this,"אפשרות Root דורשת מכשיר עם Root פעיל ואישור הרשאת superuser. אם הפקודה לא נתמכת במכשיר, אפשר להשתמש במסלול ADB."));
 }

 void copyAdbCommand(){
  String command="adb shell dpm set-device-owner com.kdroid.filter/.AdminReceiver";
  ClipboardManager cm=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
  cm.setPrimaryClip(ClipData.newPlainText("ADB Device Owner command",command));
  UI.msg(this,"פקודת ADB הועתקה");
 }

 void activateWithRoot(Button button){
  button.setEnabled(false);
  button.setText("מנסה להפעיל הרשאת מנהל…");
  new Thread(()->{
   String output="";
   int exit=-1;
   try{
    Process p=new ProcessBuilder("su","-c","dpm set-device-owner com.kdroid.filter/.AdminReceiver").redirectErrorStream(true).start();
    ByteArrayOutputStream out=new ByteArrayOutputStream();
    InputStream in=p.getInputStream();
    byte[] buffer=new byte[1024];int n;
    while((n=in.read(buffer))!=-1)out.write(buffer,0,n);
    exit=p.waitFor();
    output=out.toString("UTF-8");
   }catch(Exception e){output=e.getClass().getSimpleName()+": "+e.getMessage();}
   final String result=output;
   final int code=exit;
   runOnUiThread(()->{
    if(Policy.owner(this)){
     UI.msg(this,"הרשאת Device Owner הופעלה בהצלחה");
     setup();
    }else{
     button.setEnabled(true);
     button.setText("2. נסה שוב באמצעות Root");
     String detail=result.trim();
     if(detail.length()>220)detail=detail.substring(0,220);
     new AlertDialog.Builder(this).setTitle("לא ניתן להפעיל דרך Root")
      .setMessage("המכשיר לא אישר הפעלת Device Owner. אפשר לנסות דרך ADB.\n\n"+(detail.length()==0?"לא התקבלה הודעת שגיאה.":detail))
      .setPositiveButton("הבנתי",null).show();
    }
   });
  }).start();
 }

 void setup(){
  if(setupShown)return;
  setupShown=true;
  l=UI.root(this,"הגדרת Kiosk MDM");
  l.addView(UI.note(this,Policy.owner(this)?"✓ הרשאת Device Owner פעילה":"לפני הנעילה יש להגדיר את האפליקציה כ-Device Owner"));
  pin=new EditText(this);pin.setHint("קוד מנהל – לפחות 4 ספרות");pin.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_VARIATION_PASSWORD);l.addView(pin);
  pin2=new EditText(this);pin2.setHint("אימות קוד מנהל");pin2.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_VARIATION_PASSWORD);l.addView(pin2);
  l.addView(UI.note(this,"הגנות"));
  addOpt("חסום איפוס להגדרות יצרן","reset",true);addOpt("חסום מצב בטוח (Safe Mode)","safe",true);addOpt("חסום ADB / אפשרויות מפתחים","adb",false);addOpt("חסום התקנת אפליקציות","install",true);addOpt("חסום הסרת אפליקציות","uninstall",true);addOpt("חסום הוספת משתמשים","users",true);addOpt("חסום שורת מצב והתראות","status",true);
  l.addView(UI.note(this,"בחר אפליקציה ראשית"));
  PackageManager pm=getPackageManager();Intent q=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
  for(ResolveInfo r:pm.queryIntentActivities(q,0)){String p=r.activityInfo.packageName;if(p.equals(getPackageName()))continue;if(pm.getLaunchIntentForPackage(p)!=null){pk.add(p);names.add(r.loadLabel(pm)+"  •  "+p);}}
  sp=new Spinner(this);sp.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,names));l.addView(sp);
  Button overlay=UI.b(this,"אישור כפתור הגדרות צף");overlay.setOnClickListener(v->{if(Build.VERSION.SDK_INT>=23&&!Settings.canDrawOverlays(this))startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,android.net.Uri.parse("package:"+getPackageName())));});l.addView(overlay);
  Button go=UI.b(this,"הפעל הגנה ונעל את המכשיר");go.setOnClickListener(v->finishSetup());l.addView(go);
 }
 void addOpt(String label,String key,boolean def){Switch s=UI.sw(this,label,Prefs.opt(this,key,def));s.setOnCheckedChangeListener((v,on)->Prefs.setOpt(this,key,on));l.addView(s);}
 void finishSetup(){
  if(!Policy.owner(this)){UI.msg(this,"Device Owner עדיין לא פעיל");return;}
  if(pin.getText().length()<4){UI.msg(this,"קוד המנהל חייב להכיל לפחות 4 ספרות");return;}
  if(!pin.getText().toString().equals(pin2.getText().toString())){UI.msg(this,"הקודים אינם זהים");return;}
  if(pk.isEmpty()||sp.getSelectedItemPosition()<0){UI.msg(this,"לא נמצאה אפליקציה ראשית תקינה");return;}
  String p=pk.get(sp.getSelectedItemPosition());Prefs.finish(this,p,pin.getText().toString());Policy.apply(this,p);startService(new Intent(this,QuickAccessService.class));launch();
 }
 void launch(){
  if(Prefs.maintenance(this))return;
  String p=Prefs.main(this);Intent i=getPackageManager().getLaunchIntentForPackage(p);if(i==null){emergency();return;}
  Policy.applyBlocked(this);startService(new Intent(this,QuickAccessService.class));
  if(Policy.owner(this))try{startLockTask();}catch(Exception ignored){}
  i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);startActivity(i);moveTaskToBack(true);
 }
 @Override public void onBackPressed(){if(Prefs.setup(this)&&!Prefs.maintenance(this))launch();else super.onBackPressed();}
 void emergency(){l=UI.root(this,"המכשיר נעול");l.addView(UI.note(this,"האפליקציה הראשית אינה זמינה. ההגנה נשארה פעילה."));Button a=UI.b(this,"מנהל 🔒");a.setOnClickListener(v->startActivity(new Intent(this,AdminActivity.class)));l.addView(a);}
}
