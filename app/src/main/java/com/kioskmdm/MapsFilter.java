package com.kdroid.filter;

import android.app.admin.DevicePolicyManager;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.VpnService;
import android.os.Build;
import android.os.UserManager;
import java.util.*;

/**
 * סינון תמונות וסרטונים בגוגל מפות.
 * עובד בלי רוט: VPN מקומי שחל רק על אפליקציית המפות ומיירט רק את שאילתות ה-DNS שלה.
 * כתובות של שרתי תמונות/סרטונים מקבלות "לא קיים", וכל השאר מועבר לשרת ה-DNS הרגיל של הרשת.
 */
public final class MapsFilter {
 static final String KEY_ON = "maps_filter", KEY_EXTRA = "maps_extra";

 /** אפליקציות שהסינון חל עליהן (רק המותקנות מביניהן נכנסות ל-VPN). */
 static final String[] MAPS_PACKAGES = {"com.google.android.apps.maps", "com.google.android.apps.mapslite"};

 /** שרתי התמונות, הסרטונים ו-Street View. כל תת-דומיין שלהם נחסם גם הוא. */
 static final String[] BUILTIN = {
  "googleusercontent.com",            // תמונות של מקומות, תמונות משתמשים וסרטונים
  "ggpht.com",                        // תמונות ו-Street View
  "googlevideo.com",                  // סרטונים
  "streetviewpixels-pa.googleapis.com",
  "cbk0.google.com", "cbk1.google.com", "cbk2.google.com", "cbk3.google.com",
  "lh3.google.com", "lh4.google.com", "lh5.google.com", "lh6.google.com",
  "photos.google.com", "video.google.com"
 };

 static boolean enabled(Context c) { return Prefs.opt(c, KEY_ON, false); }
 static void setEnabled(Context c, boolean on) { Prefs.setOpt(c, KEY_ON, on); }
 static String extra(Context c) { return Prefs.p(c).getString(KEY_EXTRA, ""); }
 static void setExtra(Context c, String s) { Prefs.p(c).edit().putString(KEY_EXTRA, s == null ? "" : s).apply(); }

 static Set<String> domains(Context c) {
  Set<String> out = new HashSet<>(Arrays.asList(BUILTIN));
  for (String line : extra(c).split("[\\s,]+")) {
   String d = clean(line);
   if (d.length() > 0) out.add(d);
  }
  return out;
 }

 static String clean(String s) {
  s = s.trim().toLowerCase(Locale.ROOT);
  if (s.startsWith("http://")) s = s.substring(7);
  if (s.startsWith("https://")) s = s.substring(8);
  int slash = s.indexOf('/'); if (slash >= 0) s = s.substring(0, slash);
  while (s.startsWith("*.") || s.startsWith(".")) s = s.substring(s.charAt(0) == '*' ? 2 : 1);
  while (s.endsWith(".")) s = s.substring(0, s.length() - 1);
  return s;
 }

 static boolean blocked(String host, Set<String> list) {
  host = host.toLowerCase(Locale.ROOT);
  while (host.endsWith(".")) host = host.substring(0, host.length() - 1);
  for (String d : list) if (host.equals(d) || host.endsWith("." + d)) return true;
  return false;
 }

 static List<String> installedMaps(Context c) {
  List<String> out = new ArrayList<>();
  PackageManager pm = c.getPackageManager();
  for (String p : MAPS_PACKAGES) {
   try { pm.getApplicationInfo(p, PackageManager.MATCH_UNINSTALLED_PACKAGES); out.add(p); } catch (Exception ignored) {}
  }
  return out;
 }

 /** מחיל את המצב השמור: מפעיל או מכבה את הסינון. נקרא בהפעלה, באתחול ובכל שמירת מדיניות. */
 static void sync(Context c) {
  boolean on = enabled(c);
  if (Policy.owner(c)) {
   DevicePolicyManager m = Policy.d(c);
   ComponentName a = Policy.cn(c);
   if (on) {
    // VPN קבוע: המערכת מפעילה אותו מחדש לבד, ואין צורך באישור משתמש
    try { m.setAlwaysOnVpnPackage(a, c.getPackageName(), false); } catch (Exception ignored) {}
    try { m.addUserRestriction(a, UserManager.DISALLOW_CONFIG_VPN); } catch (Exception ignored) {}
    // DNS פרטי במצב "שם מארח" עוקף את ה-VPN — מחזירים אותו למצב אוטומטי
    if (Build.VERSION.SDK_INT >= 29) try {
     if (m.getGlobalPrivateDnsMode(a) == DevicePolicyManager.PRIVATE_DNS_MODE_PROVIDER_HOSTNAME)
      m.setGlobalPrivateDnsModeOpportunistic(a);
    } catch (Exception ignored) {}
   } else {
    try { if (c.getPackageName().equals(m.getAlwaysOnVpnPackage(a))) m.setAlwaysOnVpnPackage(a, null, false); } catch (Exception ignored) {}
    try { m.clearUserRestriction(a, UserManager.DISALLOW_CONFIG_VPN); } catch (Exception ignored) {}
   }
  }
  Intent i = new Intent(c, MapsVpnService.class).setAction(on ? MapsVpnService.ACTION_START : MapsVpnService.ACTION_STOP);
  if (on && VpnService.prepare(c) != null) return; // עדיין אין אישור VPN (מכשיר בלי Device Owner)
  try { c.startService(i); } catch (Exception ignored) {}
 }
}
