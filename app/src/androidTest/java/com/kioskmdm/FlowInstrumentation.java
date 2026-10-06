package com.kioskmdm;

import android.app.*;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.view.accessibility.*;
import java.io.*;

/** Runs real window, lifecycle and Back checks on the API 27 emulator without test libraries. */
public class FlowInstrumentation extends Instrumentation {
    private UiAutomation automation;
    private Context context;
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){
        Bundle status=new Bundle();status.putString("id","InstrumentationTestRunner");status.putInt("numtests",1);
        status.putInt("current",1);status.putString("class",getClass().getName());status.putString("test","testAndroid81ShadeAndBack");sendStatus(1,status);
        try{
            context=getTargetContext();
            automation=getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
            AccessibilityServiceInfo info=automation.getServiceInfo();info.flags|=AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;automation.setServiceInfo(info);
            testFlow();
            status.putString("stream","\nAndroid 8.1 shade, Back, screen wake and rotation passed.\n");sendStatus(0,status);
            Bundle done=new Bundle();done.putString("stream","\nOK (1 test)\n");finish(Activity.RESULT_OK,done);
        }catch(Throwable error){
            try{screenshot("failure");}catch(Throwable ignored){}
            status.putString("stack",android.util.Log.getStackTraceString(error));status.putString("stream",error.toString());sendStatus(-2,status);
            Bundle done=new Bundle();done.putString("stream","FAILURES!!!\n"+android.util.Log.getStackTraceString(error));finish(Activity.RESULT_OK,done);
        }
    }
    private void testFlow() throws Exception {
        shell("svc power stayon true");
        shell("dpm set-device-owner com.kioskmdm/.AdminReceiver");
        check(Policy.owner(context),"Device Owner setup failed");
        shell("appops set com.kioskmdm WRITE_SETTINGS allow");
        shell("settings put secure enabled_accessibility_services com.kioskmdm/.BackKeyService");
        shell("settings put secure accessibility_enabled 1");
        runOnMainSync(()->{Prefs.finish(context,"com.kioskmdm.fixture","2468");Policy.apply(context,"com.kioskmdm.fixture");});
        startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        waitFor("PRIMARY APP");
        tap("OPEN DETAIL");waitFor("DETAIL PAGE");back();waitFor("PRIMARY APP");
        for(int i=0;i<4;i++){back();SystemClock.sleep(600);waitFor("PRIMARY APP");noProtectedPage();}
        waitFor("פתיחת הגדרות מהירות");
        swipeDown();waitFor("הגדרות מהירות");waitFor("Wi-Fi");waitFor("Bluetooth");waitFor("סיבוב אוטומטי");
        screenshot("shade-portrait");
        int before=Settings.System.getInt(context.getContentResolver(),Settings.System.ACCELEROMETER_ROTATION,0);
        tap("סיבוב אוטומטי");SystemClock.sleep(700);
        check(Settings.System.getInt(context.getContentResolver(),Settings.System.ACCELEROMETER_ROTATION,0)!=(before),"Rotation tile did not update actual setting");
        back();waitAbsent("הגדרות מהירות");waitFor("PRIMARY APP");
        shell("input keyevent 223");SystemClock.sleep(600);shell("input keyevent 224");shell("wm dismiss-keyguard");
        waitFor("פתיחת הגדרות מהירות");swipeDown();waitFor("הגדרות מהירות");
        tap("פתיחת ההגדרות");waitFor("חיבורים");noProtectedPage();screenshot("settings-portrait");
        back();waitFor("PRIMARY APP");
        shell("settings put system accelerometer_rotation 0");shell("settings put system user_rotation 1");SystemClock.sleep(1200);
        waitFor("פתיחת הגדרות מהירות");swipeDown();waitFor("הגדרות מהירות");screenshot("shade-landscape");
        back();shell("settings put system user_rotation 0");SystemClock.sleep(800);
        shell("pm disable-user --user 0 com.kioskmdm.fixture");
        waitFor("נסה שוב");check(find("כניסת מנהל")==null,"Recovery must not expose an admin shortcut");noProtectedPage();
        tap("הגדרות");waitFor("חיבורים");screenshot("settings-from-unavailable-app");
        shell("pm enable com.kioskmdm.fixture");back();waitFor("PRIMARY APP");
    }
    private void noProtectedPage(){
        check(find("המכשיר מוגן")==null,"Protected-device screen appeared");
        check(find("סביבת העבודה מוגנת")==null,"Protected workspace wording appeared");
        check(find("KIOSK  /  סביבת עבודה מוגנת")==null,"Old branding appeared");
        check(find("כניסת מנהל עם קוד")==null,"Direct admin shortcut appeared");
    }
    private void back(){
        long t=SystemClock.uptimeMillis();
        automation.injectInputEvent(new KeyEvent(t,t,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BACK,0),true);
        automation.injectInputEvent(new KeyEvent(t,t+50,KeyEvent.ACTION_UP,KeyEvent.KEYCODE_BACK,0),true);
    }
    private AccessibilityNodeInfo find(String label){
        for(AccessibilityWindowInfo window:automation.getWindows()){
            AccessibilityNodeInfo node=walk(window.getRoot(),label);if(node!=null)return node;
        }
        return walk(automation.getRootInActiveWindow(),label);
    }
    private AccessibilityNodeInfo walk(AccessibilityNodeInfo node,String label){
        if(node==null)return null;
        if(label.contentEquals(node.getText()==null?"":node.getText())||label.contentEquals(node.getContentDescription()==null?"":node.getContentDescription()))return node;
        for(int i=0;i<node.getChildCount();i++){AccessibilityNodeInfo result=walk(node.getChild(i),label);if(result!=null)return result;}
        return null;
    }
    private AccessibilityNodeInfo waitFor(String label){
        long end=SystemClock.uptimeMillis()+12000;
        do{AccessibilityNodeInfo node=find(label);if(node!=null)return node;SystemClock.sleep(150);}while(SystemClock.uptimeMillis()<end);
        throw new AssertionError("Not found: "+label);
    }
    private void waitAbsent(String label){
        long end=SystemClock.uptimeMillis()+4000;
        while(find(label)!=null&&SystemClock.uptimeMillis()<end)SystemClock.sleep(100);
        check(find(label)==null,"Still visible: "+label);
    }
    private void tap(String label){
        AccessibilityNodeInfo node=waitFor(label);Rect r=new Rect();node.getBoundsInScreen(r);touch(r.centerX(),r.centerY(),r.centerX(),r.centerY(),80);
    }
    private void swipeDown(){
        int width=context.getResources().getDisplayMetrics().widthPixels;
        float density=context.getResources().getDisplayMetrics().density;
        touch(width/2f,8*density,width/2f,180*density,350);
    }
    private void touch(float x1,float y1,float x2,float y2,int duration){
        long down=SystemClock.uptimeMillis();
        for(int i=0;i<=10;i++){
            int action=i==0?MotionEvent.ACTION_DOWN:i==10?MotionEvent.ACTION_UP:MotionEvent.ACTION_MOVE;
            long now=SystemClock.uptimeMillis();
            MotionEvent e=MotionEvent.obtain(down,now,action,x1+(x2-x1)*i/10,y1+(y2-y1)*i/10,0);e.setSource(InputDevice.SOURCE_TOUCHSCREEN);
            automation.injectInputEvent(e,true);e.recycle();SystemClock.sleep(duration/10);
        }
    }
    private String shell(String command)throws Exception{
        ParcelFileDescriptor descriptor=automation.executeShellCommand(command);
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(descriptor)){
            byte[] buffer=new byte[4096];int count;while((count=in.read(buffer))!=-1)bytes.write(buffer,0,count);
        }
        return bytes.toString("UTF-8");
    }
    private void screenshot(String name)throws Exception{
        File folder=new File(context.getExternalFilesDir(null),"test-evidence");folder.mkdirs();
        Bitmap image=automation.takeScreenshot();check(image!=null,"Screenshot unavailable");
        try(FileOutputStream stream=new FileOutputStream(new File(folder,name+".png"))){image.compress(Bitmap.CompressFormat.PNG,100,stream);}image.recycle();
    }
    private void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
