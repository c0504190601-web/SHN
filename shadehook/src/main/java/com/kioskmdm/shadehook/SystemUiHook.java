package com.kioskmdm.shadehook;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.SystemClock;
import android.view.View;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import de.robv.android.xposed.AndroidAppHelper;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/** A17mini Android 8.1 SystemUI experiment; scoped only to com.android.systemui. */
public final class SystemUiHook implements IXposedHookLoadPackage {
 private static final Uri STATE=Uri.parse("content://com.kioskmdm.hookstate/state");
 private static volatile long expires;
 private static volatile boolean cached;

 @Override public void handleLoadPackage(XC_LoadPackage.LoadPackageParam p){
  if(!"com.android.systemui".equals(p.packageName))return;
  gear(p.classLoader);
  tiles(p.classLoader);
  notifications(p.classLoader);
  recents(p.classLoader);
  escapes(p.classLoader);
  XposedBridge.log("Kiosk shade: hooks registered");
 }
 private static Class<?> get(String name,ClassLoader loader) {
  return XposedHelpers.findClassIfExists(name,loader);
 }
 private static void hook(String name,ClassLoader loader,String method,XC_MethodHook cb){
  Class<?> cl=get(name,loader);
  if(cl!=null)try{XposedBridge.hookAllMethods(cl,method,cb);}
  catch(Throwable t){XposedBridge.log("Kiosk shade "+name+"."+method+": "+t);}
 }
 private static Context context(){
  Object o=AndroidAppHelper.currentApplication();
  return o instanceof Context?(Context)o:null;
 }
 private static boolean active(){return active(context());}
 private static boolean active(Context c){
  if(c==null)return false;
  long now=SystemClock.uptimeMillis();
  if(now<expires)return cached;
  boolean enabled=false;
  try(Cursor cur=c.getContentResolver().query(STATE,null,null,null,null)){
   enabled=cur!=null&&cur.moveToFirst()&&cur.getInt(0)==1;
  }catch(Throwable ignored){}
  cached=enabled; expires=now+500;
  return enabled;
 }
 private static void stop(XC_MethodHook.MethodHookParam p){
  if(!(p.method instanceof Method)){p.setResult(null);return;}
  Class<?> r=((Method)p.method).getReturnType();
  if(r==boolean.class)p.setResult(false);
  else if(r==int.class)p.setResult(0);
  else if(r==long.class)p.setResult(0L);
  else if(r==float.class)p.setResult(0f);
  else if(r==double.class)p.setResult(0d);
  else p.setResult(null);
 }
 private static void launch(Context c){
  Intent i=new Intent();
  i.setClassName("com.kioskmdm","com.kioskmdm.ShadeEntryActivity");
  i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
  c.startActivity(i);
 }
 private static void gear(ClassLoader loader){
  for(String type:new String[]{"com.android.systemui.qs.QSFooterImpl","com.android.systemui.qs.QSFooter"}){
   hook(type,loader,"onClick",new XC_MethodHook(){
    @Override protected void beforeHookedMethod(MethodHookParam p){
     if(p.args.length==0||!(p.args[0] instanceof View))return;
     View v=(View)p.args[0];
     if(!active(v.getContext()))return;
     String id;
     try{id=v.getResources().getResourceEntryName(v.getId());}catch(Throwable t){return;}
     if(!"settings_button".equals(id)&&!"settings_button_container".equals(id))return;
     try{launch(v.getContext());stop(p);}catch(Throwable t){XposedBridge.log("Kiosk shade gear "+t);}
    }
   });
   hook(type,loader,"startSettingsActivity",new XC_MethodHook(){
    @Override protected void beforeHookedMethod(MethodHookParam p){
     Context c=context();
     if(!active(c))return;
     try{launch(c);stop(p);}catch(Throwable t){XposedBridge.log("Kiosk shade settings "+t);}
    }
   });
  }
 }
 private static boolean forbidden(Object o){
  if(!(o instanceof String))return false;
  String v=((String)o).trim().toLowerCase(Locale.ROOT);
  return v.equals("location")||v.equals("hotspot")||v.equals("wifi_hotspot")||v.equals("wifiap")||v.equals("tether");
 }
 private static void tiles(ClassLoader loader){
  hook("com.android.systemui.qs.QSTileHost",loader,"loadTileSpecs",new XC_MethodHook(){
   @Override protected void afterHookedMethod(MethodHookParam p){
    if(!active()||!(p.getResult() instanceof List))return;
    List<?> spec=(List<?>)p.getResult();
    ArrayList<Object> keep=new ArrayList<>();
    for(Object item:spec)if(!forbidden(item))keep.add(item);
    if(keep.size()!=spec.size())p.setResult(keep);
   }
  });
  for(String method:new String[]{"show","showImmediately"}){
   hook("com.android.systemui.qs.customize.QSCustomizer",loader,method,new XC_MethodHook(){
    @Override protected void beforeHookedMethod(MethodHookParam p){if(active())stop(p);}
   });
  }
  hook("com.android.systemui.statusbar.policy.HotspotControllerImpl",loader,"setHotspotEnabled",new XC_MethodHook(){
   @Override protected void beforeHookedMethod(MethodHookParam p){
    if(active()&&p.args.length>0&&Boolean.TRUE.equals(p.args[0]))stop(p);
   }
  });
 }
 private static void notifications(ClassLoader loader){
  for(String method:new String[]{"onAttachedToWindow","onLayout"}){
   hook("com.android.systemui.statusbar.stack.NotificationStackScrollLayout",loader,method,new XC_MethodHook(){
    @Override protected void afterHookedMethod(MethodHookParam p){
     if(active() && p.thisObject instanceof View)((View)p.thisObject).setVisibility(View.GONE);
    }
   });
  }
  hook("com.android.systemui.statusbar.phone.NotificationPanelView",loader,"setNoVisibleNotifications",new XC_MethodHook(){
   @Override protected void beforeHookedMethod(MethodHookParam p){
    if(active()&&p.args.length>0)p.args[0]=true;
   }
  });
  hook("com.android.systemui.statusbar.policy.HeadsUpManager",loader,"showNotification",new XC_MethodHook(){
   @Override protected void beforeHookedMethod(MethodHookParam p){if(active())stop(p);}
  });
  hook("com.android.systemui.statusbar.phone.NotificationIconAreaController",loader,"shouldShowNotificationIcon",new XC_MethodHook(){
   @Override protected void beforeHookedMethod(MethodHookParam p){if(active())stop(p);}
  });
 }
 private static void recents(ClassLoader loader){
  for(String type:new String[]{"com.android.systemui.recents.Recents","com.android.systemui.recents.RecentsImpl","com.android.systemui.statusbar.phone.StatusBar"}){
   for(String method:new String[]{"showRecentApps","toggleRecentApps","preloadRecentApps","showRecents","toggleRecents"}){
    hook(type,loader,method,new XC_MethodHook(){
     @Override protected void beforeHookedMethod(MethodHookParam p){if(active())stop(p);}
    });
   }
  }
 }
 private static void escapes(ClassLoader loader){
  for(String type:new String[]{"com.android.systemui.qs.tileimpl.QSTileImpl","com.android.systemui.qs.tiles.WifiTile",
   "com.android.systemui.qs.tiles.BluetoothTile","com.android.systemui.qs.tiles.CellularTile",
   "com.android.systemui.qs.tiles.RotationLockTile","com.android.systemui.qs.tiles.HotspotTile",
   "com.android.systemui.qs.tiles.LocationTile"}){
   hook(type,loader,"handleLongClick",new XC_MethodHook(){
    @Override protected void beforeHookedMethod(MethodHookParam p){if(active())stop(p);}
   });
  }
  for(String type:new String[]{"com.android.systemui.statusbar.phone.StatusBar","com.android.systemui.statusbar.phone.ActivityStarterDelegate"}){
   for(String method:new String[]{"startActivity","startActivityDismissingKeyguard","postStartActivityDismissingKeyguard"}){
    hook(type,loader,method,new XC_MethodHook(){
     @Override protected void beforeHookedMethod(MethodHookParam p){
      if(!active()||p.args.length==0||!(p.args[0] instanceof Intent))return;
      Intent i=(Intent)p.args[0];
      boolean settings=i.getAction()!=null&&i.getAction().startsWith("android.settings.");
      if(i.getComponent()!=null&&"com.android.settings".equals(i.getComponent().getPackageName()))settings=true;
      if(settings)try{launch(context());stop(p);}catch(Throwable ignored){}
     }
    });
   }
  }
 }
}