package de.robv.android.xposed;
import java.lang.reflect.Member;
public abstract class XC_MethodHook {
 protected void beforeHookedMethod(MethodHookParam p) throws Throwable{}
 protected void afterHookedMethod(MethodHookParam p) throws Throwable{}
 public static final class MethodHookParam {
  public Object thisObject;public Object[] args;public Member method;
  public Object getResult(){return null;}public void setResult(Object r){}
 }
 public class Unhook {}
}