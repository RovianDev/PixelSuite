package io.github.pixelsuite;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

final class StoreUpdates {
    private StoreUpdates() {}

    static final class Available {
        final long code; final String name;
        Available(long code, String name) { this.code = code; this.name = name; }
    }

    private static final String DB = "/data/data/" + StoreBlocker.PLAY + "/databases/localappstate.db";

    static Map<String, Available> read(Context c, Collection<String> packages) {
        Map<String, Available> out = new HashMap<String, Available>();
        if (packages.isEmpty()) return out;
        File dir = new File(c.getCacheDir(), "ps-play");
        String d = dir.getAbsolutePath();
        int uid = android.os.Process.myUid();
        try {
            if (Root.run("rm -rf '" + d + "'; mkdir -p '" + d + "'; for x in '' -wal -shm; do [ -f '" + DB + "'$x ] && cat '" + DB + "'$x > '" + d + "/s.db'$x; done; "
                    + "chown -R " + uid + ":" + uid + " '" + d + "'; chmod 700 '" + d + "'; chmod 600 '" + d + "'/*; restorecon -R '" + d + "' 2>/dev/null; [ -f '" + d + "/s.db' ] && echo ok") == null) return out;
            SQLiteDatabase db = SQLiteDatabase.openDatabase(d + "/s.db", null, SQLiteDatabase.OPEN_READWRITE);
            try {
                StringBuilder q = new StringBuilder();
                String[] args = new String[packages.size()];
                int i = 0;
                for (String p : packages) { q.append(i == 0 ? "?" : ",?"); args[i++] = p; }
                Cursor cur = db.rawQuery("SELECT package_name, app_details FROM appstate WHERE package_name IN (" + q + ")", args);
                try {
                    while (cur.moveToNext()) {
                        byte[] b = cur.isNull(1) ? null : cur.getBlob(1);
                        Available a = b == null ? null : details(b);
                        if (a != null) out.put(cur.getString(0), a);
                    }
                } finally { cur.close(); }
            } finally { db.close(); }
        } catch (Throwable ignored) {
        } finally {
            Root.run("rm -rf '" + d + "'");
        }
        return out;
    }

    private static Available details(byte[] b) {
        int[] pos = {0};
        while (pos[0] < b.length) {
            long key = varint(b, pos);
            if (key < 0) return null;
            int field = (int) (key >>> 3), type = (int) (key & 7);
            if (field == 17 && type == 2) {
                int len = (int) varint(b, pos);
                if (len < 0 || pos[0] + len > b.length) return null;
                return inner(b, pos[0], pos[0] + len);
            }
            if (!skip(b, pos, type)) return null;
        }
        return null;
    }

    private static Available inner(byte[] b, int from, int to) {
        int[] pos = {from};
        long code = -1;
        String name = null;
        while (pos[0] < to) {
            long key = varint(b, pos);
            if (key < 0) break;
            int field = (int) (key >>> 3), type = (int) (key & 7);
            if (field == 2 && type == 0) { code = varint(b, pos); continue; }
            if (type == 2) {
                int len = (int) varint(b, pos);
                if (len < 0 || pos[0] + len > to) break;
                if (name == null && len > 0 && len <= 40) {
                    String s = new String(b, pos[0], len, StandardCharsets.UTF_8);
                    if (s.matches("\\d+(\\.\\d+){1,4}")) name = s;
                }
                pos[0] += len;
                continue;
            }
            if (!skip(b, pos, type)) break;
        }
        return code > 0 ? new Available(code, name) : null;
    }

    private static long varint(byte[] b, int[] pos) {
        long v = 0;
        for (int shift = 0; shift < 64 && pos[0] < b.length; shift += 7) {
            int x = b[pos[0]++] & 0xff;
            v |= (long) (x & 0x7f) << shift;
            if ((x & 0x80) == 0) return v;
        }
        return -1;
    }

    private static boolean skip(byte[] b, int[] pos, int type) {
        switch (type) {
            case 0: return varint(b, pos) >= 0;
            case 1: pos[0] += 8; return pos[0] <= b.length;
            case 2: { long len = varint(b, pos); if (len < 0 || pos[0] + len > b.length) return false; pos[0] += (int) len; return true; }
            case 5: pos[0] += 4; return pos[0] <= b.length;
            default: return false;
        }
    }
}
