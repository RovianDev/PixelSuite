package io.github.pixelsuite;

import android.app.Activity;
import android.content.Intent;
import android.net.VpnService;
import android.os.Bundle;
import android.widget.Toast;

public final class VpnPermissionActivity extends Activity {
 private String code,chrome;private int scope;
 @Override protected void onCreate(Bundle b){
  super.onCreate(b);Rtl.activity(this);
  code=getIntent().getStringExtra(PixelVpnService.EXTRA_COUNTRY);if(code==null)code=PixelVpnService.chosen(this);
  scope=getIntent().getIntExtra(PixelVpnService.EXTRA_SCOPE,PixelVpnService.PHONE);chrome=getIntent().getStringExtra(PixelVpnService.EXTRA_CHROME);
  if(!PixelVpnService.premium(this)){startActivity(new Intent(this,SettingsActivity.class).putExtra("page","vpn"));finish();return;}
  Intent consent=VpnService.prepare(this);
  if(consent==null){go();return;}
  try{startActivityForResult(consent,1);}catch(Exception e){Toast.makeText(this,I18n.cs("Couldn't ask for VPN permission"),Toast.LENGTH_SHORT).show();finish();}
 }
 @Override protected void onActivityResult(int request,int result,Intent data){
  if(result==RESULT_OK)go();
  else{Toast.makeText(this,I18n.cs("VPN permission is needed to connect"),Toast.LENGTH_SHORT).show();finish();}
 }
 private void go(){
  try{if(scope==PixelVpnService.CHROME_ONLY)PixelVpnService.chrome(this,true,code,chrome==null?"com.android.chrome":chrome);else PixelVpnService.phone(this,code);}
  catch(Exception e){Toast.makeText(this,I18n.cs("Couldn't start the VPN"),Toast.LENGTH_SHORT).show();}
  finish();overridePendingTransition(0,0);
 }
}
