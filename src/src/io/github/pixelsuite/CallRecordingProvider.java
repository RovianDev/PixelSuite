package io.github.pixelsuite;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.Settings;

import java.io.FileNotFoundException;

public final class CallRecordingProvider extends ContentProvider {
    static final String AUTHORITY = "io.github.roviandev.pixelsuite.callrecordings";
    private static final String DIALER = "com.google.android.dialer";

    @Override public boolean onCreate() { return true; }

    private boolean fromDialer() {
        int uid = Binder.getCallingUid();
        if (uid == android.os.Process.myUid()) return true;
        String[] packages = getContext().getPackageManager().getPackagesForUid(uid);
        if (packages != null) for (String p : packages) if (DIALER.equals(p)) return true;
        return false;
    }

    private Uri tree() {
        String v = Settings.Secure.getString(getContext().getContentResolver(), CallRecording.FOLDER_KEY);
        return v != null && v.startsWith("tree:") ? Uri.parse(v.substring(5)) : null;
    }

    private boolean inTree(Uri tree, Uri doc) {
        try {
            if (!tree.getAuthority().equals(doc.getAuthority())) return false;
            String root = DocumentsContract.getTreeDocumentId(tree), id = DocumentsContract.getDocumentId(doc);
            return id.equals(root) || id.startsWith(root.endsWith("/") ? root : root + "/");
        } catch (Throwable t) { return false; }
    }

    @Override public Bundle call(String method, String arg, Bundle extras) {
        if (!fromDialer() || arg == null) return null;
        Uri tree = tree(); if (tree == null) return null;
        ContentResolver cr = getContext().getContentResolver();
        long token = Binder.clearCallingIdentity();
        try {
            if ("create".equals(method)) {
                String name = arg.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_");
                Uri parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree));
                Uri doc = DocumentsContract.createDocument(cr, parent, "audio/mp4", name);
                if (doc == null) return null;
                Bundle out = new Bundle();
                out.putString("doc", doc.toString());
                return out;
            }
            if ("delete".equals(method)) {
                Uri doc = Uri.parse(arg);
                if (inTree(tree, doc)) DocumentsContract.deleteDocument(cr, doc);
                return new Bundle();
            }
        } catch (Throwable error) {
        } finally { Binder.restoreCallingIdentity(token); }
        return null;
    }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!fromDialer()) throw new FileNotFoundException("not allowed");
        Uri tree = tree();
        String d = uri.getQueryParameter("u");
        if (tree == null || d == null) throw new FileNotFoundException("no folder picked");
        Uri doc = Uri.parse(d);
        if (!inTree(tree, doc)) throw new FileNotFoundException("outside the picked folder");
        long token = Binder.clearCallingIdentity();
        try { return getContext().getContentResolver().openFileDescriptor(doc, "rw"); }
        finally { Binder.restoreCallingIdentity(token); }
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) { return null; }
    @Override public String getType(Uri uri) { return "audio/mp4"; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String selection, String[] args) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { return 0; }
}
