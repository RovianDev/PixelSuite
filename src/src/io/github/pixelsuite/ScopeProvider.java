package io.github.pixelsuite;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;

public final class ScopeProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String order) { return null; }
    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String selection, String[] args) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { return 0; }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        if (!"SendBinder".equals(method) || extras == null) return null;
        int uid = Binder.getCallingUid();
        if (uid != 0 && uid != 1000) return null;
        IBinder binder = extras.getBinder("binder");
        if (binder != null && getContext() != null) GmsScope.onService(getContext().getApplicationContext(), binder);
        return new Bundle();
    }
}
