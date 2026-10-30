package io.github.pixelsuite;
import android.content.Context;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
final class AppMotion {
 private static volatile boolean enabled=true;
 private static boolean watching;
 private static final java.util.concurrent.CopyOnWriteArrayList<Runnable> listeners=new java.util.concurrent.CopyOnWriteArrayList<Runnable>();
 static void watch(Runnable r){listeners.addIfAbsent(r);}
 static void unwatch(Runnable r){listeners.remove(r);}
 static synchronized boolean enabled(Context context){
  if(!watching){final Context c=context.getApplicationContext();read(c);
   c.getContentResolver().registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),false,new ContentObserver(new Handler(Looper.getMainLooper())){
    @Override public void onChange(boolean self){read(c);for(Runnable r:listeners)r.run();}
   });watching=true;
  }return enabled;
 }
 private static void read(Context c){enabled=Settings.Global.getFloat(c.getContentResolver(),Settings.Global.ANIMATOR_DURATION_SCALE,1)!=0;}
}
