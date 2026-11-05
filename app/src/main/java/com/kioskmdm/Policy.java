package com.kdroid.filter;
import android.app.admin.*;
import android.content.*;
import android.os.*;
import java.util.*;

public final class Policy {
 static ComponentName cn(Context c){return new ComponentName(c,AdminReceiver.class);}
 static DevicePolicyManager d(Context c){return(DevicePolicyManager)c.getSystemService(Context.DEVICE_POLICY_SERVICE);}
 static boolean owner(Context c){return d(c).isDeviceOwnerApp(c.getPackageName());}
 static void r(DevicePolicyManager m,ComponentName a,String key,boolean on){try{if(on)m.addUserRestriction(a,key);else m.clearUserRestriction(a,key);}catch(Exception ignored){}}
 static void apply(Context c,String main){
  if(!owner(c))return;
  DevicePolicyManager m=d(c);ComponentName a=cn(c);
  String[][] restrictions={
   {"reset",UserManager.DISALLOW_FACTORY_RESET},{"safe",UserManager.DISALLOW_SAFE_BOOT},
   {"adb",UserManager.DISALLOW_DEBUGGING_FEATURES},{"install",UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES},
   {"install",UserManager.DISALLOW_INSTALL_APPS},{"uninstall",UserManager.DISALLOW_UNINSTALL_APPS},
   {"users",UserManager.DISALLOW_ADD_USER},{"wifi","no_config_wifi"},{"bluetooth","no_bluetooth"},
   {"bluetooth_config","no_config_bluetooth"},{"date_time","no_config_date_time"},
   {"location_config","no_config_location"},{"tethering","no_config_tethering"},
   {"mobile_config","no_config_mobile_networks"},{"vpn_config","no_config_vpn"},
   {"private_dns","no_config_private_dns"},{"credentials","no_config_credentials"},
   {"printing","no_printing"},{"cell_broadcast","no_config_cell_broadcasts"},
   {"share_location","no_share_location"},{"microphone","no_unmute_microphone"}
  };
  for(String[] item:restrictions)r(m,a,item[1],Prefs.opt(c,item[0],false));
  try{m.setCameraDisabled(a,Prefs.opt(c,"camera",false));}catch(Exception ignored){}
  try{m.setScreenCaptureDisabled(a,Prefs.opt(c,"screenshots",false));}catch(Exception ignored){}
  try{m.setStatusBarDisabled(a,Prefs.opt(c,"status",true));}catch(Exception ignored){}
  try{m.setLockTaskPackages(a,new String[]{c.getPackageName(),main});}catch(Exception ignored){}
  applyBlocked(c);
 }
 static void applyBlocked(Context c){if(!owner(c))return;for(String pkg:Prefs.blocked(c))if(!isProtected(c,pkg))try{d(c).setApplicationHidden(cn(c),pkg,true);}catch(Exception ignored){}}
 static void setBlocked(Context c,String pkg,boolean b){if(!owner(c)||isProtected(c,pkg))return;try{d(c).setApplicationHidden(cn(c),pkg,b);Set<String>s=Prefs.blocked(c);if(b)s.add(pkg);else s.remove(pkg);Prefs.blocked(c,s);}catch(Exception ignored){}}
 static boolean isProtected(Context c,String p){return p.equals(c.getPackageName())||p.equals(Prefs.main(c))||p.equals("android")||p.equals("com.android.systemui")||p.contains("permissioncontroller")||p.contains("packageinstaller");}
 static void removeOwner(Context c){if(!owner(c))return;try{maintenance(c,true);d(c).clearDeviceOwnerApp(c.getPackageName());}catch(Exception ignored){}}
 static void maintenance(Context c,boolean on){
  if(!owner(c))return;DevicePolicyManager m=d(c);ComponentName a=cn(c);Prefs.maintenance(c,on);
  if(on){
   try{m.setStatusBarDisabled(a,false);}catch(Exception ignored){}
   try{m.setLockTaskPackages(a,new String[]{});}catch(Exception ignored){}
  }else apply(c,Prefs.main(c));
 }
}
