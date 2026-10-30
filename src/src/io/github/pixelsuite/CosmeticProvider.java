package io.github.pixelsuite;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;

public final class CosmeticProvider extends ContentProvider {
    private static final String[] CALLERS = {"com.android.chrome", "com.chrome.beta", "com.chrome.dev", "com.chrome.canary"};
    private static volatile boolean sFetching;

    @Override public boolean onCreate() { return true; }

    private boolean allowed() {
        if (Binder.getCallingUid() == android.os.Process.myUid()) return true;
        String[] packages = getContext().getPackageManager().getPackagesForUid(Binder.getCallingUid());
        if (packages != null) for (String p : packages) for (String c : CALLERS) if (c.equals(p)) return true;
        return false;
    }

    @Override public Bundle call(String method, String arg, Bundle extras) {
        if (!allowed()) throw new SecurityException("Caller not allowed");
        final android.content.Context c = getContext();
        if (!Cosmetic.present(c)) fetch(c);
        if ("generic".equals(method)) return Cosmetic.generic(c, arg);
        if ("host".equals(method)) return Cosmetic.host(c, arg);
        throw new IllegalArgumentException("Unknown method");
    }

    private static void fetch(final android.content.Context c) {
        if (sFetching) return;
        sFetching = true;
        new Thread(new Runnable() {
            @Override public void run() {
                try { if (AdBlock.isOn() && AdBlock.entitled(c)) Cosmetic.update(c); } catch (Throwable ignored) { } finally { sFetching = false; }
            }
        }, "PixelSuiteCosmetic").start();
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) { return null; }
    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { return 0; }
}
