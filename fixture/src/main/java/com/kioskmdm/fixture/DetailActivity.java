package com.kioskmdm.fixture;
import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;
public class DetailActivity extends Activity {
 @Override public void onCreate(Bundle state){super.onCreate(state);TextView text=new TextView(this);text.setText("DETAIL PAGE");text.setTextSize(28);text.setPadding(32,80,32,32);setContentView(text);}
}
