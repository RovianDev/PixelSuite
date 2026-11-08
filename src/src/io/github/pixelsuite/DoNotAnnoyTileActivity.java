package io.github.pixelsuite;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
public final class DoNotAnnoyTileActivity extends Activity {
 @Override protected void onCreate(Bundle state){
  super.onCreate(state);Rtl.activity(this);
  Intent in=getIntent();ComponentName tile=null;
  try{tile=in.getParcelableExtra("android.intent.extra.COMPONENT_NAME");}catch(Throwable ignored){}
  if(tile!=null&&VpnTile.class.getName().equals(tile.getClassName()))startActivity(new Intent(this,SettingsActivity.class).putExtra("page","vpn"));
  else startActivity(new Intent(this,SettingsActivity.class).putExtra("page","do_not_annoy").putExtra("request_dna_permissions",in.getBooleanExtra("request_dna_permissions",false)));
  finish();
 }
}
