package com.kioskmdm;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.KeyguardManager;
import android.bluetooth.BluetoothAdapter;
import android.content.*;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.net.wifi.WifiManager;
import android.os.*;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.view.*;
import android.view.accessibility.*;
import android.widget.*;

/** Persistent quick-settings shade. The component name preserves existing service permission. */
public class BackKeyService extends AccessibilityService {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private WindowManager windows;
    private View edge;
    private FrameLayout shade;
    private Tile wifi, bluetooth, rotation;
    private boolean registered;
    private boolean preview;
    private static BackKeyService instance;
    private final SharedPreferences.OnSharedPreferenceChangeListener preferences = (p,k) -> handler.post(this::reconcile);
    private final ContentObserver rotationObserver = new ContentObserver(handler) {
        @Override public void onChange(boolean selfChange) { refreshTiles(); }
    };
    private final Runnable healthCheck = new Runnable() {
        @Override public void run() { reconcile(); handler.postDelayed(this, 2000); }
    };
    private final BroadcastReceiver stateChanges = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent intent) {
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) { closeShade(); removeEdge(); }
            else { reconcile(); refreshTiles(); }
        }
    };

    static boolean enabled(Context context) {
        AccessibilityManager manager=(AccessibilityManager)context.getSystemService(ACCESSIBILITY_SERVICE);
        for(AccessibilityServiceInfo info:manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)) {
            if(info.getResolveInfo()!=null && new ComponentName(context,BackKeyService.class).equals(
                    new ComponentName(info.getResolveInfo().serviceInfo.packageName,info.getResolveInfo().serviceInfo.name))) return true;
        }
        return false;
    }
    static void dismissButton() { if(instance!=null){instance.closeShade();instance.reconcile();} }
    static void refreshAccess() { if(instance!=null)instance.reconcile(); }
    static void preview(boolean value) { if(instance!=null){instance.preview=value;instance.reconcile();} }

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        instance=this;
        windows=(WindowManager)getSystemService(WINDOW_SERVICE);
        if(!registered){
            IntentFilter filter=new IntentFilter();
            filter.addAction(Intent.ACTION_SCREEN_OFF);filter.addAction(Intent.ACTION_SCREEN_ON);
            filter.addAction(Intent.ACTION_USER_PRESENT);filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
            filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
            if(Build.VERSION.SDK_INT>=33)registerReceiver(stateChanges,filter,RECEIVER_EXPORTED);
            else registerReceiver(stateChanges,filter);
            Prefs.p(this).registerOnSharedPreferenceChangeListener(preferences);
            getContentResolver().registerContentObserver(Settings.System.getUriFor(Settings.System.ACCELEROMETER_ROTATION),false,rotationObserver);
            registered=true;
        }
        handler.removeCallbacks(healthCheck);
        handler.post(healthCheck);
    }

    private boolean shouldShow() {
        PowerManager power=(PowerManager)getSystemService(POWER_SERVICE);
        KeyguardManager keyguard=(KeyguardManager)getSystemService(KEYGUARD_SERVICE);
        return (Prefs.setup(this)||preview) && power.isInteractive() && !keyguard.isKeyguardLocked();
    }
    private void reconcile() {
        if(windows==null)return;
        if(!shouldShow()){closeShade();removeEdge();return;}
        // Do not remove/recreate the handle for window events or ordinary state refreshes.
        if(edge==null)createEdge();
    }
    private WindowManager.LayoutParams params(int width,int height,boolean focusable) {
        int flags=WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
        if(!focusable)flags|=WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        WindowManager.LayoutParams p=new WindowManager.LayoutParams(width,height,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,flags,PixelFormat.TRANSLUCENT);
        p.gravity=Gravity.TOP|Gravity.CENTER_HORIZONTAL;
        if(Build.VERSION.SDK_INT>=28)p.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        return p;
    }
    private int topInset() {
        int id=getResources().getIdentifier("status_bar_height","dimen","android");
        return Math.max(UI.dp(this,24),id==0?0:getResources().getDimensionPixelSize(id));
    }
    private void createEdge() {
        FrameLayout strip=new FrameLayout(this);
        strip.setContentDescription("פתיחת הגדרות מהירות");
        strip.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        strip.setBackgroundColor(0x08000000);
        View pill=new View(this);pill.setBackground(UI.bg(0xff8794a5,UI.dp(this,3)));
        FrameLayout.LayoutParams grip=new FrameLayout.LayoutParams(UI.dp(this,42),UI.dp(this,4),Gravity.CENTER);
        strip.addView(pill,grip);
        strip.setOnClickListener(v->openShade());
        strip.setOnTouchListener(new View.OnTouchListener(){
            float x,y;
            public boolean onTouch(View v,android.view.MotionEvent event){
                if(event.getActionMasked()==android.view.MotionEvent.ACTION_DOWN){x=event.getRawX();y=event.getRawY();return true;}
                if(event.getActionMasked()==android.view.MotionEvent.ACTION_UP){
                    float dx=Math.abs(event.getRawX()-x),dy=event.getRawY()-y;
                    if((dy>=UI.dp(BackKeyService.this,20)&&dy>dx)||(Math.abs(dy)<UI.dp(BackKeyService.this,12)&&dx<UI.dp(BackKeyService.this,12)))v.performClick();
                }
                return true;
            }
        });
        try{windows.addView(strip,params(-1,topInset()+UI.dp(this,4),false));edge=strip;}
        catch(RuntimeException ignored){edge=null;}
    }
    private void removeEdge(){
        if(edge!=null){try{windows.removeView(edge);}catch(RuntimeException ignored){}edge=null;}
    }

    private void openShade() {
        if(shade!=null||!shouldShow())return;
        FrameLayout screen=new FrameLayout(this){
            @Override public boolean dispatchKeyEvent(KeyEvent event){
                if(event.getKeyCode()==KeyEvent.KEYCODE_BACK){if(event.getAction()==KeyEvent.ACTION_UP)closeShade();return true;}
                return super.dispatchKeyEvent(event);
            }
        };
        screen.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        screen.setBackgroundColor(0x660b172c);
        screen.setFocusableInTouchMode(true);
        screen.setOnClickListener(v->closeShade());
        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setOnClickListener(v->{});
        LinearLayout panel=new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(UI.dp(this,18),UI.dp(this,20),UI.dp(this,18),UI.dp(this,8));
        panel.setBackground(UI.bg(0xfff6f8fd,UI.dp(this,28)));
        panel.setOnClickListener(v->{});
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=text("הגדרות מהירות",21,UI.INK);title.setTypeface(null,android.graphics.Typeface.BOLD);
        header.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        FrameLayout gear=new FrameLayout(this);gear.setContentDescription("פתיחת ההגדרות");
        gear.setBackground(UI.bg(Color.WHITE,UI.dp(this,18)));
        ControlIcon icon=new ControlIcon(this,ControlIcon.SETTINGS);
        gear.addView(icon,new FrameLayout.LayoutParams(UI.dp(this,30),UI.dp(this,30),Gravity.CENTER));
        gear.setOnClickListener(v->{closeShade();openSettings();});
        header.addView(gear,new LinearLayout.LayoutParams(UI.dp(this,52),UI.dp(this,52)));
        panel.addView(header);
        TextView clock=text(DateFormat.getTimeFormat(this).format(new java.util.Date()),15,UI.MUTED);
        clock.setPadding(0,UI.dp(this,2),0,UI.dp(this,18));panel.addView(clock);
        LinearLayout tiles=new LinearLayout(this);tiles.setOrientation(LinearLayout.HORIZONTAL);
        wifi=new Tile("Wi-Fi",ControlIcon.WIFI,()->DeviceControls.toggleWifi(this));
        bluetooth=new Tile("Bluetooth",ControlIcon.BLUETOOTH,()->DeviceControls.toggleBluetooth(this));
        rotation=new Tile("סיבוב אוטומטי",ControlIcon.ROTATION,()->DeviceControls.setRotation(this,DeviceControls.rotationState(this)!=DeviceControls.ON));
        for(Tile tile:new Tile[]{wifi,bluetooth,rotation}){
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,1);p.setMargins(UI.dp(this,3),0,UI.dp(this,3),0);tiles.addView(tile,p);
        }
        panel.addView(tiles,new LinearLayout.LayoutParams(-1,UI.dp(this,132)));
        TextView dismiss=text("החלק למעלה לסגירה",12,UI.MUTED);dismiss.setGravity(Gravity.CENTER);
        dismiss.setPadding(0,UI.dp(this,18),0,UI.dp(this,12));dismiss.setContentDescription("סגירת הגדרות מהירות");
        dismiss.setOnClickListener(v->closeShade());
        View.OnTouchListener swipeUp=new View.OnTouchListener(){
            float down;
            public boolean onTouch(View v,android.view.MotionEvent e){
                if(e.getActionMasked()==android.view.MotionEvent.ACTION_DOWN){down=e.getRawY();return true;}
                if(e.getActionMasked()==android.view.MotionEvent.ACTION_UP){if(e.getRawY()-down<UI.dp(BackKeyService.this,12))v.performClick();}
                return true;
            }
        };
        dismiss.setOnTouchListener(swipeUp);panel.addView(dismiss);
        scroll.addView(panel);
        int width=Math.min(getResources().getDisplayMetrics().widthPixels-UI.dp(this,16),UI.dp(this,580));
        FrameLayout.LayoutParams card=new FrameLayout.LayoutParams(width,-2,Gravity.TOP|Gravity.CENTER_HORIZONTAL);
        card.topMargin=topInset()+UI.dp(this,8);card.bottomMargin=UI.dp(this,24);
        screen.addView(scroll,card);
        try{
            windows.addView(screen,params(-1,-1,true));shade=screen;screen.requestFocus();refreshTiles();
            panel.setTranslationY(-UI.dp(this,36));panel.setAlpha(0f);
            panel.animate().translationY(0).alpha(1).setDuration(180).start();
        }catch(RuntimeException exception){shade=null;wifi=null;bluetooth=null;rotation=null;}
    }
    private TextView text(String text,int size,int color){
        TextView view=new TextView(this);view.setText(text);view.setTextSize(size);view.setTextColor(color);return view;
    }
    private final class Tile extends LinearLayout {
        final TextView label,state;final ControlIcon icon;
        Tile(String title,int kind,Runnable action){
            super(BackKeyService.this);setOrientation(VERTICAL);setGravity(Gravity.CENTER);setPadding(UI.dp(getContext(),4),UI.dp(getContext(),10),UI.dp(getContext(),4),UI.dp(getContext(),10));
            icon=new ControlIcon(getContext(),kind);addView(icon,new LinearLayout.LayoutParams(UI.dp(getContext(),30),UI.dp(getContext(),30)));
            label=text(title,14,UI.INK);label.setGravity(Gravity.CENTER);label.setPadding(0,UI.dp(getContext(),10),0,UI.dp(getContext(),4));addView(label);
            state=text("",12,UI.MUTED);state.setGravity(Gravity.CENTER);addView(state);
            setOnClickListener(v->{action.run();refreshTiles();handler.postDelayed(BackKeyService.this::refreshTiles,500);});
        }
        void update(int value){
            boolean on=value==DeviceControls.ON;
            setBackground(UI.bg(on?UI.ACCENT:Color.WHITE,UI.dp(getContext(),20)));
            icon.tint(on?Color.WHITE:UI.ACCENT);label.setTextColor(on?Color.WHITE:UI.INK);
            state.setText(DeviceControls.stateLabel(value));state.setTextColor(on?0xffdce8ff:UI.MUTED);
            setContentDescription(label.getText()+", "+state.getText());
            setEnabled(value!=DeviceControls.CHANGING&&value!=DeviceControls.UNAVAILABLE);
            setAlpha(value==DeviceControls.UNAVAILABLE?.5f:1f);
        }
    }
    private void refreshTiles(){
        if(wifi!=null)wifi.update(DeviceControls.wifiState(this));
        if(bluetooth!=null)bluetooth.update(DeviceControls.bluetoothState(this));
        if(rotation!=null)rotation.update(DeviceControls.rotationState(this));
    }
    private void openSettings(){
        if(!Prefs.setup(this)){UI.msg(this,"הווילון מוכן לשימוש לאחר סיום ההגדרה");return;}
        try{startActivity(new Intent(this,SettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP));}
        catch(RuntimeException exception){UI.msg(this,"לא ניתן לפתוח הגדרות כעת");}
    }
    private void closeShade(){
        if(shade!=null){try{windows.removeView(shade);}catch(RuntimeException ignored){}shade=null;}
        wifi=null;bluetooth=null;rotation=null;
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent event){reconcile();}
    @Override public void onInterrupt(){closeShade();reconcile();}
    @Override public void onConfigurationChanged(Configuration config){super.onConfigurationChanged(config);closeShade();removeEdge();handler.post(this::reconcile);}
    @Override public void onDestroy(){
        handler.removeCallbacksAndMessages(null);closeShade();removeEdge();
        if(registered){unregisterReceiver(stateChanges);Prefs.p(this).unregisterOnSharedPreferenceChangeListener(preferences);getContentResolver().unregisterContentObserver(rotationObserver);registered=false;}
        if(instance==this)instance=null;
        super.onDestroy();
    }
}
