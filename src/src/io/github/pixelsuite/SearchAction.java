package io.github.pixelsuite;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import java.io.File;

public final class SearchAction extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        try { handle(getIntent()); } catch (Throwable t) { android.util.Log.w("PixelSuite", "search: action", t); }
        finish();
        overridePendingTransition(0, 0);
    }

    private void handle(Intent in) {
        if (in == null) return;
        String token = in.getStringExtra("token");
        if (token == null || !token.equals(SearchProvider.token(this)) || !PixelSearch.active(this) || !PatronGate.ok(this)) return;
        String act = in.getStringExtra("act"), value = in.getStringExtra("value");
        if (act == null || value == null) return;
        if ("call".equals(act)) {
            String n = value.replaceAll("[^0-9+*#,;]", "");
            if (n.isEmpty()) return;
            boolean can = checkSelfPermission("android.permission.CALL_PHONE") == PackageManager.PERMISSION_GRANTED;
            boolean placed = false;
            if (can) try {
                android.telecom.TelecomManager telecom = (android.telecom.TelecomManager) getSystemService(TELECOM_SERVICE);
                telecom.placeCall(Uri.fromParts("tel", n, null), new android.os.Bundle());
                placed = true;
            } catch (Throwable t) { android.util.Log.w("PixelSuite", "search: place call " + t); }
            if (!placed) startActivity(new Intent(can ? Intent.ACTION_CALL : Intent.ACTION_DIAL, Uri.fromParts("tel", n, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return;
        }
        if ("scan".equals(act)) { if (PixelSearch.scanOn(this) && !SuiteApps.openPdf(this, true)) startActivity(new Intent(this, SearchScan.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return; }
        File f = new File(value);
        if (!f.isFile() && !"folder".equals(act)) { AppToast.makeText(this, I18n.cs("That file isn't there any more"), Toast.LENGTH_SHORT).show(); return; }
        if ("open".equals(act)) {
            Uri u = SearchFileProvider.uri(f.getAbsolutePath());
            Intent v = new Intent(Intent.ACTION_VIEW).setDataAndType(u, getContentResolver().getType(u)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            try { startActivity(v); }
            catch (Throwable t) { AppToast.makeText(this, I18n.cs("No app can open this file"), Toast.LENGTH_SHORT).show(); }
        } else if ("share".equals(act)) {
            Uri u = SearchFileProvider.uri(f.getAbsolutePath());
            Intent s = new Intent(Intent.ACTION_SEND).setType(getContentResolver().getType(u)).putExtra(Intent.EXTRA_STREAM, u).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            s.setClipData(android.content.ClipData.newRawUri("", u));
            startActivity(Intent.createChooser(s, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION));
        } else if ("folder".equals(act)) {
            File dir = f.isDirectory() ? f : f.getParentFile();
            if (dir == null) return;
            String root = SearchIndex.root().getAbsolutePath();
            String rel = dir.getAbsolutePath().startsWith(root) ? dir.getAbsolutePath().substring(root.length()) : "";
            if (rel.startsWith("/")) rel = rel.substring(1);
            Uri doc = Uri.parse("content://com.android.externalstorage.documents/document/" + Uri.encode("primary:" + rel));
            Intent o = new Intent(Intent.ACTION_VIEW).setDataAndType(doc, "vnd.android.document/directory").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            try {
                java.util.ArrayList<Intent> others = new java.util.ArrayList<Intent>();
                Intent probe = new Intent(Intent.ACTION_VIEW).setDataAndType(Uri.fromFile(dir), "resource/folder");
                for (android.content.pm.ResolveInfo r : getPackageManager().queryIntentActivities(probe, 0)) {
                    if (r.activityInfo == null || getPackageName().equals(r.activityInfo.packageName)) continue;
                    others.add(new Intent(probe).setClassName(r.activityInfo.packageName, r.activityInfo.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                }
                if (!others.isEmpty()) {
                    android.os.StrictMode.setVmPolicy(new android.os.StrictMode.VmPolicy.Builder().build());
                    startActivity(Intent.createChooser(o, null).putExtra(Intent.EXTRA_INITIAL_INTENTS, others.toArray(new android.os.Parcelable[0])).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    return;
                }
            } catch (Throwable ignored) { }
            try { startActivity(o); }
            catch (Throwable t) {
                try { startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("content://com.android.externalstorage.documents/root/primary"), "vnd.android.document/root").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
                catch (Throwable ignored) { AppToast.makeText(this, I18n.cs("No app can open this folder"), Toast.LENGTH_SHORT).show(); }
            }
        }
    }
}
