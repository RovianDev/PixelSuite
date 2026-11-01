package io.github.pixelsuite;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.IBinder;
import android.os.Parcel;
import android.os.SystemClock;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class GmsScope {
    private GmsScope() {}

    static final String GMS = "com.google.android.gms";
    static final String TITLE = "Remove Google Play services from the scope";
    static final String MESSAGE = "Pixel Suite no longer uses Google Play services, and keeping it in Pixel Suite's LSPosed scope can cost you Device and Strong integrity.\n\nOpen LSPosed (or Vector), untick Google Play services for Pixel Suite, then reboot.";
    private static final String CONFIG = "/data/adb/lspd/config/";
    static final String REBOOT_TITLE = "Reboot to restore Play Integrity";
    static final String REBOOT_MESSAGE = "Pixel Suite removed Google Play services from its LSPosed scope, because it can cost you Device and Strong integrity and is no longer used. Reboot your phone to finish.";
    private static final String DESCRIPTOR = "io.github.libxposed.service.IXposedService";
    private static final int GET_API_VERSION = IBinder.FIRST_CALL_TRANSACTION + 1;
    private static final int GET_SCOPE = IBinder.FIRST_CALL_TRANSACTION + 10;
    private static final int REMOVE_SCOPE = IBinder.FIRST_CALL_TRANSACTION + 12;
    static volatile boolean checked;
    static volatile Runnable onRemoved;

    private static SharedPreferences prefs(Context c) { return c.getSharedPreferences("gms_scope", Context.MODE_PRIVATE); }
    private static long bootTime() { return (System.currentTimeMillis() - SystemClock.elapsedRealtime()) / 60000L; }

    static boolean needsReboot(Context c) {
        long removedBoot = prefs(c).getLong("removed_boot", -1);
        if (removedBoot < 0) return false;
        if (Math.abs(bootTime() - removedBoot) <= 1) return true;
        prefs(c).edit().remove("removed_boot").apply();
        return false;
    }

    static void onService(final Context context, final IBinder binder) {
        new Thread(new Runnable() { public void run() {
            try {
                if (apiVersion(binder) < 101 || !scope(binder).contains(GMS)) return;
                Parcel data = Parcel.obtain(), reply = Parcel.obtain();
                try {
                    data.writeInterfaceToken(DESCRIPTOR);
                    data.writeStringList(Collections.singletonList(GMS));
                    binder.transact(REMOVE_SCOPE, data, reply, 0);
                    reply.readException();
                } finally { data.recycle(); reply.recycle(); }
                if (scope(binder).contains(GMS)) return;
                prefs(context).edit().putLong("removed_boot", bootTime()).apply();
                Runnable r = onRemoved;
                if (r != null) r.run();
            } catch (Throwable ignored) { }
        }}, "PixelSuiteScope").start();
    }

    private static int apiVersion(IBinder binder) throws Exception {
        Parcel data = Parcel.obtain(), reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(DESCRIPTOR);
            binder.transact(GET_API_VERSION, data, reply, 0);
            reply.readException();
            return reply.readInt();
        } finally { data.recycle(); reply.recycle(); }
    }

    private static List<String> scope(IBinder binder) throws Exception {
        Parcel data = Parcel.obtain(), reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(DESCRIPTOR);
            binder.transact(GET_SCOPE, data, reply, 0);
            reply.readException();
            List<String> list = reply.createStringArrayList();
            return list == null ? new ArrayList<String>() : list;
        } finally { data.recycle(); reply.recycle(); }
    }

    static boolean scoped(Context context) {
        File dir = new File(context.getCacheDir(), "scope-check");
        String path = dir.getAbsolutePath();
        int uid = android.os.Process.myUid();
        try {
            String copy = "rm -rf '" + path + "'; [ -f " + CONFIG + "modules_config.db ] || exit 1; mkdir -p '" + path + "'"
                    + "; for f in modules_config.db modules_config.db-wal modules_config.db-shm; do if [ -f " + CONFIG + "$f ]; then cp " + CONFIG + "$f '" + path + "/'; fi; done"
                    + "; chown -R " + uid + ":" + uid + " '" + path + "'; chmod 700 '" + path + "'; chmod 600 '" + path + "'/*; restorecon -R '" + path + "' 2>/dev/null; true";
            if (Root.run(copy) == null) return false;
            File db = new File(dir, "modules_config.db");
            if (!db.isFile()) return false;
            SQLiteDatabase sql = SQLiteDatabase.openDatabase(db.getPath(), null, SQLiteDatabase.OPEN_READWRITE);
            try {
                Cursor c = sql.rawQuery("SELECT 1 FROM scope s JOIN modules m ON s.mid = m.mid WHERE m.module_pkg_name = ? AND s.app_pkg_name = ? LIMIT 1",
                        new String[]{context.getPackageName(), GMS});
                try { return c.moveToFirst(); } finally { c.close(); }
            } finally {
                sql.close();
            }
        } catch (Throwable t) {
            return false;
        } finally {
            File[] files = dir.listFiles();
            if (files != null) for (File f : files) f.delete();
            dir.delete();
        }
    }
}
