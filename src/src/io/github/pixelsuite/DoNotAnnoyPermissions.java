package io.github.pixelsuite;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.Build;
import android.service.quicksettings.TileService;
import java.util.ArrayList;
final class DoNotAnnoyPermissions {
 private static String[] missing(Context c){ArrayList<String> permissions=new ArrayList<String>();if(c.checkSelfPermission("android.permission.CALL_PHONE")!=PackageManager.PERMISSION_GRANTED)permissions.add("android.permission.CALL_PHONE");if(Build.VERSION.SDK_INT>=33&&c.checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=PackageManager.PERMISSION_GRANTED)permissions.add("android.permission.POST_NOTIFICATIONS");return permissions.toArray(new String[0]);}
 static void request(Activity a){String[] permissions=missing(a);if(permissions.length>0&&!a.isFinishing()&&!a.isDestroyed())a.requestPermissions(permissions,824);}
 static void fromTile(TileService service){if(missing(service).length==0)return;Intent i=new Intent(service,DoNotAnnoyTileActivity.class).putExtra("request_dna_permissions",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);try{if(Build.VERSION.SDK_INT>=34){PendingIntent launch=PendingIntent.getActivity(service,824,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);TileService.class.getMethod("startActivityAndCollapse",PendingIntent.class).invoke(service,launch);}else TileService.class.getMethod("startActivityAndCollapse",Intent.class).invoke(service,i);}catch(Exception ignored){ }}
}
