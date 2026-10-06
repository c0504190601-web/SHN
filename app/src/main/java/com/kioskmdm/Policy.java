package com.kioskmdm;
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
  if(!owner(c))return;DevicePolicyManager m=d(c);ComponentName a=cn(c);
  r(m,a,UserManager.DISALLOW_FACTORY_RESET,Prefs.opt(c,"reset",true));r(m,a,UserManager.DISALLOW_SAFE_BOOT,Prefs.opt(c,"safe",true));r(m,a,UserManager.DISALLOW_DEBUGGING_FEATURES,Prefs.opt(c,"adb",true));r(m,a,UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES,Prefs.opt(c,"install",true));
  if(Build.VERSION.SDK_INT>=26)r(m,a,UserManager.DISALLOW_INSTALL_APPS,Prefs.opt(c,"install",true));
  r(m,a,UserManager.DISALLOW_UNINSTALL_APPS,Prefs.opt(c,"uninstall",true));r(m,a,UserManager.DISALLOW_ADD_USER,Prefs.opt(c,"users",true));
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
  if(on){try{m.setStatusBarDisabled(a,false);}catch(Exception ignored){}r(m,a,UserManager.DISALLOW_FACTORY_RESET,Prefs.opt(c,"reset",true));r(m,a,UserManager.DISALLOW_SAFE_BOOT,Prefs.opt(c,"safe",true));r(m,a,UserManager.DISALLOW_DEBUGGING_FEATURES,Prefs.opt(c,"adb",true));r(m,a,UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES,Prefs.opt(c,"install",true));if(Build.VERSION.SDK_INT>=26)r(m,a,UserManager.DISALLOW_INSTALL_APPS,Prefs.opt(c,"install",true));r(m,a,UserManager.DISALLOW_UNINSTALL_APPS,Prefs.opt(c,"uninstall",true));r(m,a,UserManager.DISALLOW_ADD_USER,Prefs.opt(c,"users",true));try{m.setLockTaskPackages(a,new String[]{});}catch(Exception ignored){}}
  else apply(c,Prefs.main(c));
 }
}