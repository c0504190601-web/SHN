package com.kioskmdm.shadehook;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.SystemClock;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.KeyEvent;
import android.view.ViewGroup;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
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
  securityFooter(p.classLoader);
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
 private static void collapseShade(Context c){
  if(c==null)return;
  try {
   Object bar=c.getSystemService("statusbar");
   if(bar==null)return;
   Method collapse=bar.getClass().getMethod("collapsePanels");
   collapse.invoke(bar);
  }catch(Throwable t){XposedBridge.log("Kiosk shade collapse: "+t);}
 }
 private static void launch(Context c){
  if(c==null)return;
  // Dismiss the SystemUI window first: otherwise our Settings task remains behind it.
  collapseShade(c);
  new Handler(Looper.getMainLooper()).postDelayed(()->{
   try {
    Intent i=new Intent();
    i.setClassName("com.kioskmdm","com.kioskmdm.ShadeEntryActivity");
    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
    c.startActivity(i);
    collapseShade(c);
   }catch(Throwable t){XposedBridge.log("Kiosk shade launch: "+t);}
  },180);
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
 private static boolean forbidden(Object value) {
  if(!(value instanceof String))return false;
  String s=((String)value).toLowerCase(Locale.ROOT).trim();
  return s.equals("location")||s.contains("locationtile")||s.equals("hotspot")
   ||s.contains("hotspot")||s.contains("tether")||s.equals("wifiap")||s.equals("wifi_hotspot");
 }
 private static Object specOf(Object tile){
  if(tile instanceof String)return tile;
  if(tile==null)return null;
  try{
   Method method=tile.getClass().getMethod("getTileSpec");
   return method.invoke(tile);
  }catch(Throwable ignored){return null;}
 }
 private static Collection<?> keepTiles(Collection<?> tiles){
  ArrayList<Object> safe=new ArrayList<>();
  for(Object tile:tiles){
   Object spec=specOf(tile);
   if(!forbidden(spec))safe.add(tile);
  }
  return safe;
 }
 private static String safeTileNames(String names){
  if(names==null)return null;
  StringBuilder safe=new StringBuilder();
  for(String part:names.split(",")){
   String item=part.trim();
   if(!forbidden(item)&&!item.isEmpty()){
    if(safe.length()>0)safe.append(',');
    safe.append(item);
   }
  }
  return safe.toString();
 }
 private static void tiles(ClassLoader loader){
  final String host="com.android.systemui.qs.QSTileHost";
  // Filter the persisted/default list when the host loads it.
  hook(host,loader,"loadTileSpecs",new XC_MethodHook(){
   @Override protected void afterHookedMethod(MethodHookParam p){
    if(active()&&p.getResult() instanceof Collection)
     p.setResult(new ArrayList<>(keepTiles((Collection<?>)p.getResult())));
   }
  });
  // MediaTek builds can use already-instantiated tiles; filter those as well.
  hook(host,loader,"getTiles",new XC_MethodHook(){
   @Override protected void afterHookedMethod(MethodHookParam p){
    if(active()&&p.getResult() instanceof Collection)
     p.setResult(new ArrayList<>(keepTiles((Collection<?>)p.getResult())));
   }
  });
  hook(host,loader,"onTuningChanged",new XC_MethodHook(){
   @Override protected void beforeHookedMethod(MethodHookParam p){
    if(!active()||p.args.length<2||!(p.args[0] instanceof String)||!(p.args[1] instanceof String))return;
    if("sysui_qs_tiles".equals(p.args[0]))p.args[1]=safeTileNames((String)p.args[1]);
   }
  });
  // Guard both the fully expanded panel and the compact top row.
  for(String panel:new String[]{"com.android.systemui.qs.QSPanel","com.android.systemui.qs.QuickQSPanel"}){
   hook(panel,loader,"setTiles",new XC_MethodHook(){
    @Override protected void beforeHookedMethod(MethodHookParam p){
     if(active()&&p.args.length>0&&p.args[0] instanceof Collection)
      p.args[0]=keepTiles((Collection<?>)p.args[0]);
    }
   });
  }
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
 private static void hideManagementFooter(Object owner){
  if(!active()||owner==null)return;
  for(String member:new String[]{"mRootView","mFooterView","mSecurityFooterView"}){
   try{
    Object root=XposedHelpers.getObjectField(owner,member);
    if(root instanceof View)((View)root).setVisibility(View.GONE);
   }catch(Throwable ignored){}
  }
 }
 private static void securityFooter(ClassLoader loader){
  // The Device Owner remains active; this hides only the optional SystemUI footer.
  for(String cls:new String[]{"com.android.systemui.qs.QSSecurityFooter",
    "com.android.systemui.qs.SecurityFooter"}){
   for(String method:new String[]{"getView","refreshState","handleRefreshState",
     "setListening","onAttachedToWindow","updateVisibility","handleUpdateState"}){
    hook(cls,loader,method,new XC_MethodHook(){
     @Override protected void afterHookedMethod(MethodHookParam p){
      if(!active())return;
      hideManagementFooter(p.thisObject);
      if(p.getResult() instanceof View)((View)p.getResult()).setVisibility(View.GONE);
     }
    });
   }
  }
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
  // Block all paths from SystemUI navigation, command dispatch and MTK recents.
  for(String type:new String[]{"com.android.systemui.statusbar.CommandQueue",
    "com.android.systemui.recents.Recents","com.android.systemui.recents.RecentsImpl",
    "com.android.systemui.statusbar.phone.StatusBar"}){
   for(String method:new String[]{"showRecentApps","toggleRecentApps","preloadRecentApps",
     "showRecents","toggleRecents","preloadRecents","startRecentsActivity",
     "showNextTask","showNextAffiliatedTask","showPrevAffiliatedTask"}){
    hook(type,loader,method,new XC_MethodHook(){
     @Override protected void beforeHookedMethod(MethodHookParam p){if(active())stop(p);}
    });
   }
  }
  // Hide the recent-apps navigation button as well as blocking its action.
  for(String method:new String[]{"onFinishInflate","reorient","setDisabledFlags",
    "updateCurrentView","updateRotatedViews","onConfigurationChanged"}){
   hook("com.android.systemui.statusbar.phone.NavigationBarView",loader,method,new XC_MethodHook(){
    @Override protected void afterHookedMethod(MethodHookParam p){
     if(!active())return;
     try{
      Object nav=p.thisObject;
      Method getter=nav.getClass().getMethod("getRecentsButton");
      Object button=getter.invoke(nav);
      if(button!=null)button.getClass().getMethod("setVisibility",int.class).invoke(button,View.GONE);
     }catch(Throwable t){XposedBridge.log("Kiosk recents button: "+t);}
    }
   });
  }
  // Fallback for direct activity launches that bypass the usual recents APIs.
  for(String method:new String[]{"onStart","onNewIntent"}){
   hook("com.android.systemui.recents.RecentsActivity",loader,method,new XC_MethodHook(){
    @Override protected void afterHookedMethod(MethodHookParam p){
     if(active()&&p.thisObject instanceof Activity)((Activity)p.thisObject).finish();
    }
   });
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