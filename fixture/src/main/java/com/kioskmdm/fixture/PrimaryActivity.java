package com.kioskmdm.fixture;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.*;
public class PrimaryActivity extends Activity {
 @Override public void onCreate(Bundle state) {
  super.onCreate(state);
  LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(32,80,32,32);
  TextView title=new TextView(this);title.setText("PRIMARY APP");title.setTextSize(28);root.addView(title);
  Button detail=new Button(this);detail.setText("OPEN DETAIL");detail.setOnClickListener(v->startActivity(new Intent(this,DetailActivity.class)));root.addView(detail);
  setContentView(root);
 }
}
