package com.kioskmdm;
import android.content.*;
public class BootReceiver extends BroadcastReceiver {
 @Override public void onReceive(Context context,Intent intent) {
   if(!Prefs.setup(context))return;
   Policy.apply(context,Prefs.main(context));
   // Accessibility services are system-managed: wake our persisted service by prompting
   // the user to re-enable it if Android did not bind it on boot.
   Intent open=new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
   try{context.startActivity(open);}catch(RuntimeException ignored){}
 }
}