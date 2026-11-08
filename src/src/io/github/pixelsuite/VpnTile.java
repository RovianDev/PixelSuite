package io.github.pixelsuite;

import android.app.PendingIntent;
import android.content.*;
import android.graphics.drawable.Icon;
import android.net.VpnService;
import android.os.Build;
import android.service.quicksettings.*;

public final class VpnTile extends TileService {
 private final Runnable update=new Runnable(){public void run(){show();}};
 static void refresh(Context c){try{TileService.requestListeningState(c,new ComponentName(c,VpnTile.class));}catch(Throwable ignored){}}
 @Override public void onStartListening(){super.onStartListening();PixelVpnService.watch(update);show();}
 @Override public void onStopListening(){PixelVpnService.unwatch(update);super.onStopListening();}
 @Override public void onDestroy(){PixelVpnService.unwatch(update);super.onDestroy();}
 @Override public void onClick(){
  super.onClick();
  if(!PixelVpnService.premium(this)){open(new Intent(this,SettingsActivity.class).putExtra("page","vpn"));return;}
  if(PixelVpnService.phoneOn()){PixelVpnService.stop(this);return;}
  if(PixelVpnService.state()!=PixelVpnService.OFF){PixelVpnService.phone(this,PixelVpnService.chosen(this));return;}
  String code=PixelVpnService.chosen(this);
  if(VpnService.prepare(this)!=null){open(new Intent(this,VpnPermissionActivity.class).putExtra(PixelVpnService.EXTRA_COUNTRY,code));return;}
  try{PixelVpnService.connect(this,code);}catch(Throwable blocked){open(new Intent(this,VpnPermissionActivity.class).putExtra(PixelVpnService.EXTRA_COUNTRY,code));}
  show();
 }
 private void open(Intent i){
  i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
  try{
   if(Build.VERSION.SDK_INT>=34)TileService.class.getMethod("startActivityAndCollapse",PendingIntent.class).invoke(this,PendingIntent.getActivity(this,0,i,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT));
   else TileService.class.getMethod("startActivityAndCollapse",Intent.class).invoke(this,i);
  }catch(Throwable t){try{startActivity(i);}catch(Throwable ignored){}}
 }
 private void show(){
  Tile tile=getQsTile();if(tile==null)return;
  int st=PixelVpnService.state();
  tile.setLabel(I18n.cs("VPN"));
  tile.setState(PixelVpnService.phoneOn()?Tile.STATE_ACTIVE:Tile.STATE_INACTIVE);
  int id=getResources().getIdentifier("ic_ps_vpn","drawable",getPackageName());if(id!=0)tile.setIcon(Icon.createWithResource(this,id));
  String sub=st==PixelVpnService.ON&&PixelVpnService.scope()==PixelVpnService.CHROME_ONLY?"Chrome only":st==PixelVpnService.ON?VpnServers.name(PixelVpnService.country(),""):st==PixelVpnService.CONNECTING?"Connecting…":PixelVpnService.premium(this)?VpnServers.name(PixelVpnService.chosen(this),""):"Patreon";
  if(Build.VERSION.SDK_INT>=29)try{Tile.class.getMethod("setSubtitle",CharSequence.class).invoke(tile,sub);}catch(Throwable ignored){}
  tile.updateTile();
 }
}
