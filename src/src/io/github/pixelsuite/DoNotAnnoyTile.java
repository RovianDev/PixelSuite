package io.github.pixelsuite;
import android.content.*;
import android.database.ContentObserver;
import android.graphics.drawable.Icon;
import android.os.*;
import android.provider.Settings;
import android.service.quicksettings.*;
import android.widget.Toast;
import java.util.List;
import java.util.concurrent.*;

public final class DoNotAnnoyTile extends TileService {
 private static final ExecutorService IO=Executors.newSingleThreadExecutor();
 private final Handler main=new Handler(Looper.getMainLooper());
 private boolean listening,registered,busy,value;private int revision;
 private final ContentObserver changes=new ContentObserver(main){@Override public void onChange(boolean self){read();}};
 static void refresh(Context c){TileService.requestListeningState(c,new ComponentName(c,DoNotAnnoyTile.class));}
 @Override public void onStartListening(){super.onStartListening();listening=true;Tile tile=getQsTile();if(tile!=null)value=tile.getState()==Tile.STATE_ACTIVE;if(!registered){getContentResolver().registerContentObserver(Settings.Secure.getUriFor(DoNotAnnoy.KEY),false,changes);registered=true;}read();}
 @Override public void onStopListening(){listening=false;revision++;unregister();super.onStopListening();}
 @Override public void onDestroy(){listening=false;revision++;unregister();super.onDestroy();}
 private void unregister(){if(registered){getContentResolver().unregisterContentObserver(changes);registered=false;}}
 private void read(){if(!listening)return;final int request=revision;IO.execute(()->{List<String> data=Root.run("settings get secure "+DoNotAnnoy.KEY);final Boolean loaded=data==null||data.isEmpty()?null:"1".equals(data.get(0));main.post(()->{if(!listening||request!=revision||busy)return;if(loaded==null){Tile tile=getQsTile();if(tile!=null){tile.setState(Tile.STATE_UNAVAILABLE);tile.updateTile();}}else{value=loaded;show();}});});}
 @Override public void onClick(){super.onClick();if(busy)return;busy=true;revision++;final boolean previous=value;value=!value;final boolean next=value;show();IO.execute(()->{final boolean ok=Root.run("settings put secure "+DoNotAnnoy.KEY+" "+(next?1:0))!=null;main.post(()->{busy=false;if(ok)SettingsActivity.invalidateDoNotAnnoy();if(!ok){value=previous;Toast.makeText(this,"Couldn't save: root access needed",Toast.LENGTH_SHORT).show();}if(listening)show();if(ok&&next)DoNotAnnoyPermissions.fromTile(this);});});}
 private void show(){Tile tile=getQsTile();if(tile==null)return;tile.setLabel("Do Not Annoy");tile.setState(value?Tile.STATE_ACTIVE:Tile.STATE_INACTIVE);int id=getResources().getIdentifier("ic_ps_do_not_annoy","drawable",getPackageName());tile.setIcon(Icon.createWithResource(this,id));if(Build.VERSION.SDK_INT>=29)try{Tile.class.getMethod("setSubtitle",CharSequence.class).invoke(tile,value?"On":"Off");}catch(Exception ignored){}tile.updateTile();}
}
