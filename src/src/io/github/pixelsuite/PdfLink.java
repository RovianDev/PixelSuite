package io.github.pixelsuite;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Process;

public final class PdfLink extends ContentProvider {
    @Override public boolean onCreate() { return true; }

    private boolean trusted(Context c) {
        try {
            int uid = Binder.getCallingUid();
            PackageManager pm = c.getPackageManager();
            if (pm.checkSignatures(uid, Process.myUid()) != PackageManager.SIGNATURE_MATCH) return false;
            String[] names = pm.getPackagesForUid(uid);
            if (names != null) for (String n : names) if (PdfHost.APP.equals(n)) return true;
        } catch (Throwable ignored) { }
        return false;
    }

    @Override public Bundle call(String method, String arg, Bundle extras) {
        Bundle out = new Bundle();
        Context c = getContext();
        if (c == null || !trusted(c)) return out;
        long id = Binder.clearCallingIdentity();
        try {
            boolean ok = PatronGate.ok(c);
            if ("pass".equals(method)) out.putBoolean("ok", ok);
            else if ("files".equals(method) && ok) out.putBoolean("ok", Root.run("appops set " + PdfHost.APP + " MANAGE_EXTERNAL_STORAGE allow; true") != null);
        } catch (Throwable ignored) {
        } finally { Binder.restoreCallingIdentity(id); }
        return out;
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sortOrder) { return null; }
    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String selection, String[] args) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { return 0; }
}
