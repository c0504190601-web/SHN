package com.kioskmdm;
import android.content.*;import android.database.*;import android.net.Uri;
public final class HookStateProvider extends ContentProvider {
 @Override public boolean onCreate(){return true;}
 @Override public Cursor query(Uri u,String[] p,String s,String[] a,String o){
  if(!"state".equals(u.getLastPathSegment()))return null;
  MatrixCursor c=new MatrixCursor(new String[]{"active"});
  c.addRow(new Object[]{Prefs.setup(getContext())&&(Prefs.opt(getContext(),"original_shade",false)||Prefs.opt(getContext(),"power_shade",false))&&!Prefs.maintenance(getContext())?1:0});
  return c;
 }
 @Override public String getType(Uri u){return "vnd.android.cursor.item/vnd.com.kioskmdm.hookstate";}
 @Override public Uri insert(Uri u,ContentValues v){throw new UnsupportedOperationException();}
 @Override public int delete(Uri u,String w,String[] a){throw new UnsupportedOperationException();}
 @Override public int update(Uri u,ContentValues v,String w,String[] a){throw new UnsupportedOperationException();}
}