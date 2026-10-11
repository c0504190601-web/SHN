package com.kioskmdm;
import android.app.Activity;import android.content.*;import android.os.Bundle;import android.widget.*;
public class AccessSetupActivity extends Activity {
 @Override protected void onCreate(Bundle state){
 super.onCreate(state);
 LinearLayout root=UI.root(this,"וילון הגדרות נפרד");
 root.addView(UI.note(this,"הווילון מופעל באפליקציית Kiosk Shade הנפרדת. התקן אותה והפעל את שירות הנגישות שלה."));
 Button button=UI.b(this,"פתיחת הגדרות הווילון");
 button.setOnClickListener(v->{try{Intent i=new Intent();i.setClassName("com.kioskmdm.shade","com.kioskmdm.shade.ShadeSettingsActivity");startActivity(i);}catch(RuntimeException e){UI.msg(this,"יש להתקין את Kiosk Shade תחילה");}});
 root.addView(button);
 Button back=UI.secondary(this,"חזרה");back.setOnClickListener(v->finish());root.addView(back);
 }
}