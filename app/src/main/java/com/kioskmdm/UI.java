package com.kioskmdm;
import android.app.*;import android.content.*;import android.graphics.Color;import android.graphics.drawable.GradientDrawable;import android.view.*;import android.widget.*;
public final class UI{
 static int dp(Context c,int n){return(int)(n*c.getResources().getDisplayMetrics().density+.5f);}
 static LinearLayout root(Activity a,String title){a.getWindow().setStatusBarColor(Color.rgb(20,45,85));ScrollView s=new ScrollView(a);LinearLayout l=new LinearLayout(a);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(dp(a,22),dp(a,24),dp(a,22),dp(a,28));l.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);l.setBackgroundColor(Color.rgb(246,248,252));TextView t=new TextView(a);t.setText(title);t.setTextSize(28);t.setTextColor(Color.rgb(20,45,85));t.setGravity(Gravity.RIGHT);t.setPadding(0,0,0,dp(a,18));l.addView(t);s.addView(l);a.setContentView(s);return l;}
 static Button b(Activity a,String s){Button b=new Button(a);b.setText(s);b.setTextSize(16);b.setAllCaps(false);b.setGravity(Gravity.CENTER);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(a,54));p.setMargins(0,dp(a,6),0,dp(a,6));b.setLayoutParams(p);return b;}
 static Switch sw(Activity a,String s,boolean on){Switch v=new Switch(a);v.setText(s);v.setTextSize(17);v.setChecked(on);v.setPadding(dp(a,8),dp(a,10),dp(a,8),dp(a,10));return v;}
 static TextView note(Activity a,String s){TextView v=new TextView(a);v.setText(s);v.setTextSize(14);v.setTextColor(Color.DKGRAY);v.setGravity(Gravity.RIGHT);v.setPadding(0,0,0,dp(a,12));return v;}
 static void msg(Context c,String s){Toast.makeText(c,s,Toast.LENGTH_SHORT).show();}
}