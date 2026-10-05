package com.kioskmdm;
import android.content.*;import android.util.Base64;import java.security.*;import java.nio.charset.StandardCharsets;import java.util.*;
public final class Prefs{
 static android.content.SharedPreferences p(Context c){return c.getSharedPreferences("kiosk",0);}
 static boolean setup(Context c){return p(c).getBoolean("setup",false);} static String main(Context c){return p(c).getString("main","");}
 static boolean opt(Context c,String k,boolean d){return p(c).getBoolean(k,d);} static void opt(Context c,String k,boolean v){p(c).edit().putBoolean(k,v).apply();}
 static void finish(Context c,String pkg,String pin){byte[] salt=new byte[16];new SecureRandom().nextBytes(salt);p(c).edit().putBoolean("setup",true).putString("main",pkg).putString("salt",Base64.encodeToString(salt,Base64.NO_WRAP)).putString("pin",hash(pin,salt)).apply();}
 static boolean pin(Context c,String s){try{byte[] salt=Base64.decode(p(c).getString("salt",""),Base64.NO_WRAP);return MessageDigest.isEqual(p(c).getString("pin","").getBytes(StandardCharsets.UTF_8),hash(s,salt).getBytes(StandardCharsets.UTF_8));}catch(Exception e){return false;}}
 static void changePin(Context c,String pin){byte[] salt=new byte[16];new SecureRandom().nextBytes(salt);p(c).edit().putString("salt",Base64.encodeToString(salt,Base64.NO_WRAP)).putString("pin",hash(pin,salt)).apply();}
 static void main(Context c,String s){p(c).edit().putString("main",s).apply();} static boolean maintenance(Context c){return p(c).getBoolean("maintenance",false);} static void maintenance(Context c,boolean b){p(c).edit().putBoolean("maintenance",b).apply();}
 static Set<String> blocked(Context c){return new HashSet<>(p(c).getStringSet("blocked",Collections.emptySet()));} static void blocked(Context c,Set<String>s){p(c).edit().putStringSet("blocked",new HashSet<>(s)).apply();}
 static String hash(String s,byte[] salt){try{MessageDigest md=MessageDigest.getInstance("SHA-256");md.update(salt);for(int i=0;i<12000;i++){md.update(s.getBytes(StandardCharsets.UTF_8));md.update(salt);}return Base64.encodeToString(md.digest(),Base64.NO_WRAP);}catch(Exception e){return "";}}
}