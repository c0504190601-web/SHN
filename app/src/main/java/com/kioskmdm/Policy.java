package com.kdroid.filter;
import android.app.admin.*;
import android.content.*;
import android.os.*;
import java.util.*;

public final class Policy {
 static ComponentName cn(Context c){return new ComponentName(c,AdminReceiver.class);}
 static DevicePolicyManager d(Context c){return(DevicePolicyManager)c.getSystemService(Context.DEVICE_POLICY_SERVICE);}
 static boolean owner(Context c){return d(c).isDeviceOwnerApp(c.getPackageName());}
 static String fail(String label,Exception e){return "✗ "+label+": "+e.getClass().getSimpleName()+(e.getMessage()==null?"":" — "+e.getMessage());}
 static String apply(Context c,String main){
  List<String> report=new ArrayList<>();
  if(!owner(c))return "✗ אין הרשאת Device Owner; המדיניות לא הוחלה.";
  DevicePolicyManager m=d(c);ComponentName a=cn(c);
  String[][] restrictions={
   {"reset",UserManager.DISALLOW_FACTORY_RESET,"איפוס יצרן"},{"safe",UserManager.DISALLOW_SAFE_BOOT,"מצב בטוח"},
   {"adb",UserManager.DISALLOW_DEBUGGING_FEATURES,"ADB"},{"unknown_sources",UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES,"מקורות לא ידועים"},
   {"install_apps",UserManager.DISALLOW_INSTALL_APPS,"התקנת אפליקציות"},{"uninstall",UserManager.DISALLOW_UNINSTALL_APPS,"הסרת אפליקציות"},
   {"users",UserManager.DISALLOW_ADD_USER,"הוספת משתמשים"},{"wifi","no_config_wifi","שינוי Wi-Fi"},
   {"bluetooth","no_bluetooth","Bluetooth"},{"bluetooth_config","no_config_bluetooth","הגדרות Bluetooth"},
   {"date_time","no_config_date_time","תאריך ושעה"},{"location_config","no_config_location","הגדרות מיקום"},
   {"tethering","no_config_tethering","נקודה חמה"},{"mobile_config","no_config_mobile_networks","רשת סלולרית"},
   {"vpn_config","no_config_vpn","VPN"},{"private_dns","no_config_private_dns","Private DNS"},
   {"credentials","no_config_credentials","אישורים"},{"printing","no_printing","הדפסה"},
   {"cell_broadcast","no_config_cell_broadcasts","שידורי חירום"},{"share_location","no_share_location","שיתוף מיקום"},
   {"microphone","no_unmute_microphone","מיקרופון"}};
  for(String[] x:restrictions){boolean on=Prefs.opt(c,x[0],false);try{if(on)m.addUserRestriction(a,x[1]);else m.clearUserRestriction(a,x[1]);report.add("✓ "+x[2]+(on?" חסום":" מותר"));}catch(Exception e){report.add(fail(x[2],e));}}
  try{m.setCameraDisabled(a,Prefs.opt(c,"camera",false));report.add("✓ מצלמה עודכנה");}catch(Exception e){report.add(fail("מצלמה",e));}
  try{m.setScreenCaptureDisabled(a,Prefs.opt(c,"screenshots",false));report.add("✓ צילום מסך עודכן");}catch(Exception e){report.add(fail("צילום מסך",e));}
  try{m.setStatusBarDisabled(a,Prefs.opt(c,"status",true));report.add("✓ שורת מצב עודכנה");}catch(Exception e){report.add(fail("שורת מצב",e));}
  try{m.setLockTaskPackages(a,new String[]{c.getPackageName(),main});report.add("✓ רשימת Kiosk עודכנה");}catch(Exception e){report.add(fail("Kiosk",e));}
  for(String pkg:Prefs.blocked(c)){if(isProtected(c,pkg)){report.add("! דולגה אפליקציה מוגנת: "+pkg);continue;}try{boolean ok=m.setApplicationHidden(a,pkg,true);report.add((ok?"✓ ":"✗ ")+"הסתרת "+pkg+(ok?"":" לא אושרה"));}catch(Exception e){report.add(fail("הסתרת "+pkg,e));}}
  return android.text.TextUtils.join("\n",report);
 }
 static void applyBlocked(Context c){if(!owner(c))return;for(String pkg:Prefs.blocked(c))if(!isProtected(c,pkg))try{d(c).setApplicationHidden(cn(c),pkg,true);}catch(Exception ignored){}}
 static void setBlocked(Context c,String pkg,boolean b){if(!owner(c)||isProtected(c,pkg))return;try{boolean ok=d(c).setApplicationHidden(cn(c),pkg,b);if(ok){Set<String>s=Prefs.blocked(c);if(b)s.add(pkg);else s.remove(pkg);Prefs.blocked(c,s);}}catch(Exception ignored){}}
 static boolean isProtected(Context c,String p){return p.equals(c.getPackageName())||p.equals(Prefs.main(c))||p.equals("android")||p.equals("com.android.systemui")||p.contains("permissioncontroller")||p.contains("packageinstaller");}
 static void removeOwner(Context c){if(!owner(c))return;try{maintenance(c,true);d(c).clearDeviceOwnerApp(c.getPackageName());}catch(Exception ignored){}}
 static void maintenance(Context c,boolean on){if(!owner(c))return;DevicePolicyManager m=d(c);ComponentName a=cn(c);Prefs.maintenance(c,on);if(on){try{m.setStatusBarDisabled(a,false);}catch(Exception ignored){}try{m.setLockTaskPackages(a,new String[]{});}catch(Exception ignored){}}else apply(c,Prefs.main(c));}
}
