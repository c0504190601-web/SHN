package com.kioskmdm;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.widget.*;

public class AccessSetupActivity extends Activity {
    private TextView status;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable refresh=new Runnable(){
        public void run(){
            BackKeyService.preview(true);
            if(status!=null)status.setText("וילון הגדרות: "+(BackKeyService.enabled(AccessSetupActivity.this)?"פעיל":"כבוי")+
                    "\nסיבוב ובהירות: "+(Settings.System.canWrite(AccessSetupActivity.this)?"מאושר":"נדרש אישור"));
            handler.postDelayed(this,1000);
        }
    };
    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        if(Prefs.setup(this)&&!Prefs.maintenance(this)){finish();return;}
        LinearLayout root=UI.root(this,"הרשאות תצוגה");
        root.addView(UI.note(this,"הפעל את ״וילון הגדרות״ בשירותי הנגישות. השירות מציג את הווילון ואינו קורא את תוכן המסך."));
        Button access=UI.b(this,"הפעלת וילון ההגדרות");
        access.setOnClickListener(v->open(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));root.addView(access);
        Button write=UI.secondary(this,"אישור שינוי סיבוב ובהירות");
        write.setOnClickListener(v->open(new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,Uri.parse("package:"+getPackageName()))));root.addView(write);
        if(Build.VERSION.SDK_INT>=31){
            Button bt=UI.secondary(this,"הרשאת Bluetooth");
            bt.setOnClickListener(v->requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT},1));root.addView(bt);
        }
        root.addView(UI.note(this,"משוך מהפס שבקצה העליון כדי לפתוח את הווילון. גלגל השיניים פותח את ההגדרות המלאות."));
        status=UI.note(this,"");root.addView(status);
        Button done=UI.b(this,"חזרה");done.setOnClickListener(v->finish());root.addView(done);
    }
    private void open(Intent intent){try{startActivity(intent);}catch(RuntimeException e){UI.msg(this,"מסך ההרשאה אינו זמין במכשיר");}}
    @Override protected void onResume(){super.onResume();if(!isFinishing())handler.post(refresh);}
    @Override protected void onPause(){handler.removeCallbacks(refresh);BackKeyService.preview(false);super.onPause();}
}
