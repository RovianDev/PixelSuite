package io.github.pixelsuite;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

public final class SearchFileProvider extends ContentProvider {
    private static volatile String scans;

    @Override public boolean onCreate() {
        try { scans = new File(getContext().getCacheDir(), "scans/out").getCanonicalPath() + "/"; } catch (Throwable ignored) { }
        return true;
    }

    static Uri uri(String path) {
        return new Uri.Builder().scheme("content").authority(PixelSearch.FILE_AUTHORITY).appendPath("f").appendPath(path).build();
    }

    private static File file(Uri uri) throws FileNotFoundException {
        java.util.List<String> seg = uri.getPathSegments();
        if (seg.size() != 2 || !"f".equals(seg.get(0))) throw new FileNotFoundException();
        try {
            File f = new File(seg.get(1)).getCanonicalFile();
            String made = scans;
            if (made != null && f.getPath().startsWith(made) && f.isFile()) return f;
            File root = SearchIndex.root().getCanonicalFile();
            String p = f.getPath(), r = root.getPath() + "/";
            if (!p.startsWith(r) || p.startsWith(r + "Android/data/") || p.startsWith(r + "Android/obb/") || !f.isFile()) throw new FileNotFoundException();
            return f;
        } catch (FileNotFoundException e) { throw e; }
        catch (Throwable t) { throw new FileNotFoundException(); }
    }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (mode != null && !mode.equals("r")) throw new FileNotFoundException();
        return ParcelFileDescriptor.open(file(uri), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override public String getType(Uri uri) {
        String last = uri.getLastPathSegment();
        String m = last == null ? null : android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(SearchIndex.ext(last));
        return m == null ? "application/octet-stream" : m;
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sortOrder) {
        MatrixCursor out = new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE});
        try { File f = file(uri); out.addRow(new Object[]{f.getName(), f.length()}); } catch (Throwable ignored) { }
        return out;
    }

    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String selection, String[] args) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { return 0; }
}
