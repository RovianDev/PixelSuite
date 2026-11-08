package io.github.pixelsuite;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;

final class BackupRestorePage {
    private static final PathInterpolator EASE = new PathInterpolator(.2f, 0f, 0f, 1f);

    private final Activity a;
    private final Executor io;
    private final int ink, muted, accent;
    private final boolean dark;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final TextView summary, summarySub, heldTitle, heldSub, heldButton, installedHeading, goneHeading, goneNote;
    private final Line heldLine;
    private final LinearLayout installedCard, goneCard;

    private final Map<String, List<StoreBackup.Saved>> data = new HashMap<String, List<StoreBackup.Saved>>();
    private final Map<String, App> apps = new HashMap<String, App>();
    private final List<App> held = new ArrayList<App>();
    private final Set<String> expanded = new HashSet<String>();
    private final Map<String, Float> progress = new HashMap<String, Float>();
    private final Set<String> failed = new HashSet<String>();
    private final Map<String, Line> lines = new HashMap<String, Line>();
    private final Map<String, View> restoreButtons = new HashMap<String, View>();
    private boolean disposed, loaded, backingUp;

    static final class App {
        final String pkg, label, version; final long code; final boolean installed; final Drawable icon;
        App(String pkg, String label, String version, long code, boolean installed, Drawable icon) { this.pkg = pkg; this.label = label; this.version = version; this.code = code; this.installed = installed; this.icon = icon; }
    }

    BackupRestorePage(Activity activity, LinearLayout page, Executor executor, int text, int secondary, int accent, boolean dark) {
        a = activity; io = executor; ink = text; muted = secondary; this.accent = accent; this.dark = dark;
        LinearLayout top = card(page);
        summary = label("Loading saved apps…", 17, ink, true); summary.setPadding(dp(20), dp(18), dp(20), 0); top.addView(summary);
        summarySub = label("Saved in PixelSuite/APK backups on your phone", 13, muted, false); summarySub.setPadding(dp(20), dp(4), dp(20), dp(18)); top.addView(summarySub);

        heading(page, "Back up");
        LinearLayout heldCard = card(page);
        LinearLayout heldRow = new LinearLayout(a); heldRow.setGravity(Gravity.CENTER_VERTICAL); heldRow.setPadding(dp(20), dp(14), dp(14), dp(14));
        LinearLayout words = new LinearLayout(a); words.setOrientation(LinearLayout.VERTICAL);
        heldTitle = label("Held-back apps", 15, ink, false); words.addView(heldTitle);
        heldSub = label("Checking…", 13, muted, false); heldSub.setPadding(0, dp(3), 0, 0); words.addView(heldSub);
        heldLine = new Line(); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(3)); lp.topMargin = dp(8); words.addView(heldLine, lp); heldLine.setVisibility(View.GONE);
        heldRow.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
        heldButton = chip("Back up");
        heldButton.setOnClickListener(v -> chooseHeld());
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-2, dp(40)); bp.leftMargin = dp(12);
        heldRow.addView(heldButton, bp);
        heldCard.addView(heldRow, new LinearLayout.LayoutParams(-1, -2));

        installedHeading = heading(page, "Installed apps");
        installedCard = card(page);
        goneHeading = heading(page, "Not installed");
        goneNote = note(page, "These apps were uninstalled. Restore installs one again, signed in if it was saved with its data.");
        goneCard = card(page);
        goneHeading.setVisibility(View.GONE); goneNote.setVisibility(View.GONE); goneCard.setVisibility(View.GONE);
        page.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            public void onViewAttachedToWindow(View v) { }
            public void onViewDetachedFromWindow(View v) { dispose(); }
        });
        reload();
    }

    void dispose() { disposed = true; }
    private boolean live() { return !disposed && !a.isFinishing() && !a.isDestroyed(); }
    void resume() { if (loaded && progress.isEmpty() && !backingUp) reload(); }

    void reload() {
        if (disposed) return;
        io.execute(new Runnable() { public void run() {
            final Map<String, List<StoreBackup.Saved>> saved = StoreBackup.all();
            final Map<String, App> found = new HashMap<String, App>();
            PackageManager pm = a.getPackageManager();
            Set<String> hiddenOrGone = new HashSet<String>();
            for (String pkg : saved.keySet()) {
                try {
                    PackageInfo pi = pm.getPackageInfo(pkg, 0);
                    String label = pi.applicationInfo.loadLabel(pm).toString();
                    Drawable icon = pi.applicationInfo.loadIcon(pm);
                    found.put(pkg, new App(pkg, label, pi.versionName == null ? "" : pi.versionName, code(pi), true, icon));
                    remember(a, pkg, label, icon);
                } catch (Throwable notVisible) { hiddenOrGone.add(pkg); }
            }
            if (!hiddenOrGone.isEmpty()) {
                RootApps.Scan scan = RootApps.scan();
                Set<String> skip = new HashSet<String>(scan.packages); skip.removeAll(hiddenOrGone);
                for (RootApps.App x : RootApps.missing(pm, scan, skip, false, null, "Backup & Restore")) {
                    Drawable icon = null; try { icon = x.info.loadIcon(pm); } catch (Throwable ignored) { }
                    found.put(x.info.packageName, new App(x.info.packageName, x.label, x.version, x.code, true, icon));
                    hiddenOrGone.remove(x.info.packageName);
                }
                for (String pkg : hiddenOrGone) found.put(pkg, gone(pkg, saved.get(pkg)));
            }
            final List<App> needs = heldNeedingBackup(saved, pm);
            main.post(new Runnable() { public void run() {
                if (!live()) return;
                loaded = true;
                data.clear(); data.putAll(saved); apps.clear(); apps.putAll(found);
                held.clear(); held.addAll(needs);
                render();
            }});
        }});
    }

    private static long code(PackageInfo pi) {
        long code = pi.versionCode;
        if (Build.VERSION.SDK_INT >= 28) try { code = ((Number) PackageInfo.class.getMethod("getLongVersionCode").invoke(pi)).longValue(); } catch (Throwable ignored) { }
        return code;
    }

    private List<App> heldNeedingBackup(Map<String, List<StoreBackup.Saved>> saved, PackageManager pm) {
        List<App> out = new ArrayList<App>();
        try {
            List<String> v = Root.run("settings get secure " + StoreBlocker.KEY + "; settings get secure " + StoreBlocker.UNLOCK_KEY);
            if (v == null || v.size() < 2) return out;
            Set<String> list = "null".equals(v.get(0)) ? new HashSet<String>(java.util.Arrays.asList(StoreBlocker.CORE)) : MiscOptions.parse(v.get(0));
            if (!"1".equals(v.get(1))) list.addAll(java.util.Arrays.asList(StoreBlocker.CORE));
            Set<String> hidden = new HashSet<String>();
            for (String pkg : list) {
                PackageInfo pi;
                try { pi = pm.getPackageInfo(pkg, 0); } catch (Throwable notVisible) { hidden.add(pkg); continue; }
                long c = code(pi);
                if (savedAt(saved, pkg, c)) continue;
                String label; try { label = pi.applicationInfo.loadLabel(pm).toString(); } catch (Throwable t) { label = pkg; }
                out.add(new App(pkg, label, pi.versionName == null ? "" : pi.versionName, c, true, null));
            }
            if (!hidden.isEmpty()) {
                RootApps.Scan scan = RootApps.scan();
                Set<String> known = new HashSet<String>(scan.packages); known.removeAll(hidden);
                for (RootApps.App x : RootApps.missing(pm, scan, known, false, null, "Backup & Restore")) {
                    String pkg = x.info.packageName;
                    if (!hidden.contains(pkg) || savedAt(saved, pkg, x.code)) continue;
                    out.add(new App(pkg, x.label, x.version, x.code, true, null));
                }
            }
        } catch (Throwable ignored) { }
        Collections.sort(out, (x, y) -> x.label.compareToIgnoreCase(y.label));
        return out;
    }

    private static boolean savedAt(Map<String, List<StoreBackup.Saved>> saved, String pkg, long code) {
        List<StoreBackup.Saved> l = saved.get(pkg);
        if (l != null) for (StoreBackup.Saved x : l) if (x.code == code) return true;
        return false;
    }

    private App gone(String pkg, List<StoreBackup.Saved> saved) {
        String label = keptLabel(a, pkg); Drawable icon = keptIcon(a, pkg);
        if ((label == null || icon == null) && saved != null && !saved.isEmpty()) {
            File copy = StoreBackup.readableCopy(a, pkg, saved.get(0).code);
            if (copy != null) {
                try {
                    PackageManager pm = a.getPackageManager();
                    PackageInfo pi = pm.getPackageArchiveInfo(copy.getAbsolutePath(), 0);
                    if (pi != null && pi.applicationInfo != null) {
                        pi.applicationInfo.sourceDir = copy.getAbsolutePath(); pi.applicationInfo.publicSourceDir = copy.getAbsolutePath();
                        if (label == null) label = pi.applicationInfo.loadLabel(pm).toString();
                        if (icon == null) icon = pi.applicationInfo.loadIcon(pm);
                        remember(a, pkg, label, icon);
                    }
                } catch (Throwable ignored) {
                } finally { copy.delete(); }
            }
        }
        return new App(pkg, label == null ? pkg : label, "", -1, false, icon);
    }

    private static File metaDir(Context c) { File d = new File(c.getFilesDir(), "backup-meta"); d.mkdirs(); return d; }
    static void remember(Context c, String pkg, String label, Drawable icon) {
        try {
            File l = new File(metaDir(c), pkg + ".label");
            if (label != null && !l.isFile()) { FileOutputStream o = new FileOutputStream(l); o.write(label.getBytes("UTF-8")); o.close(); }
            File p = new File(metaDir(c), pkg + ".png");
            if (icon != null && !p.isFile()) {
                int size = Math.round(48 * c.getResources().getDisplayMetrics().density);
                Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
                Canvas cv = new Canvas(b); Drawable d = icon.getConstantState() != null ? icon.getConstantState().newDrawable().mutate() : icon;
                d.setBounds(0, 0, size, size); d.draw(cv);
                FileOutputStream o = new FileOutputStream(p); b.compress(Bitmap.CompressFormat.PNG, 100, o); o.close();
            }
        } catch (Throwable ignored) { }
    }
    private static String keptLabel(Context c, String pkg) {
        try { File l = new File(metaDir(c), pkg + ".label"); if (!l.isFile()) return null; byte[] b = new byte[(int) l.length()]; java.io.FileInputStream in = new java.io.FileInputStream(l); int n = in.read(b); in.close(); return new String(b, 0, Math.max(0, n), "UTF-8"); } catch (Throwable t) { return null; }
    }
    private Drawable keptIcon(Context c, String pkg) {
        try { File p = new File(metaDir(c), pkg + ".png"); if (!p.isFile()) return null; Bitmap b = BitmapFactory.decodeFile(p.getAbsolutePath()); return b == null ? null : new BitmapDrawable(c.getResources(), b); } catch (Throwable t) { return null; }
    }

    static void summarize(final Activity a, Executor io, final TextView into) {
        io.execute(new Runnable() { public void run() {
            final Map<String, List<StoreBackup.Saved>> m = StoreBackup.all();
            int versions = 0; for (List<StoreBackup.Saved> l : m.values()) versions += l.size();
            final String s = m.isEmpty() ? "Nothing saved yet" : m.size() + (m.size() == 1 ? " app" : " apps") + " saved · " + versions + (versions == 1 ? " version" : " versions");
            into.post(new Runnable() { public void run() { if (!a.isFinishing()) into.setText(I18n.cs(s)); } });
        }});
    }

    private void render() {
        lines.clear(); restoreButtons.clear();
        long kb = 0; int versions = 0;
        for (List<StoreBackup.Saved> l : data.values()) for (StoreBackup.Saved x : l) { kb += x.kb + x.dataKb; versions++; }
        if (data.isEmpty()) { summary.setText(I18n.cs("Nothing saved yet")); summarySub.setText(I18n.cs("Held-back apps are saved here before they update.")); }
        else { summary.setText(I18n.cs(data.size() + (data.size() == 1 ? " app" : " apps") + " · " + versions + (versions == 1 ? " version" : " versions") + (kb > 0 ? " · " + size(kb) : ""))); summarySub.setText(I18n.cs("The 3 newest versions of each app are kept")); }
        if (!backingUp) {
            heldSub.setText(I18n.cs(held.isEmpty() ? "Every held-back app is saved at its current version" : held.size() + (held.size() == 1 ? " app isn't" : " apps aren't") + " saved at the version installed now"));
            heldButton.setEnabled(!held.isEmpty()); heldButton.setAlpha(held.isEmpty() ? .45f : 1f); heldButton.setText(I18n.cs("Back up"));
        }
        List<App> in = new ArrayList<App>(), out = new ArrayList<App>();
        for (String pkg : data.keySet()) { App x = apps.get(pkg); if (x == null) continue; (x.installed ? in : out).add(x); }
        Collections.sort(in, (x, y) -> x.label.compareToIgnoreCase(y.label));
        Collections.sort(out, (x, y) -> x.label.compareToIgnoreCase(y.label));
        installedCard.removeAllViews(); goneCard.removeAllViews();
        if (in.isEmpty()) noteInside(installedCard, data.isEmpty() ? "No saved versions yet" : "No installed app has saved versions");
        for (int i = 0; i < in.size(); i++) installedCard.addView(appBlock(in.get(i), i == in.size() - 1), new LinearLayout.LayoutParams(-1, -2));
        boolean any = !out.isEmpty();
        goneHeading.setVisibility(any ? View.VISIBLE : View.GONE); goneNote.setVisibility(any ? View.VISIBLE : View.GONE); goneCard.setVisibility(any ? View.VISIBLE : View.GONE);
        for (int i = 0; i < out.size(); i++) goneCard.addView(appBlock(out.get(i), i == out.size() - 1), new LinearLayout.LayoutParams(-1, -2));
    }

    private View appBlock(final App app, boolean last) {
        final List<StoreBackup.Saved> saved = data.get(app.pkg);
        LinearLayout block = new LinearLayout(a); block.setOrientation(LinearLayout.VERTICAL);
        LinearLayout row = new LinearLayout(a); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(dp(16), dp(12), dp(8), dp(12)); row.setMinimumHeight(dp(72));
        row.setBackground(ripple(0));
        ImageView icon = new ImageView(a); icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        if (app.icon != null) icon.setImageDrawable(app.icon); else icon.setImageDrawable(new Placeholder(app.label));
        if (!app.installed) icon.setAlpha(.6f);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(40), dp(40)); ip.rightMargin = dp(14); row.addView(icon, ip);
        LinearLayout words = new LinearLayout(a); words.setOrientation(LinearLayout.VERTICAL);
        TextView name = label(app.label, 15, ink, false); name.setSingleLine(true); name.setEllipsize(android.text.TextUtils.TruncateAt.END); words.addView(name);
        int n = saved == null ? 0 : saved.size();
        String sub = (app.installed ? "Installed " + (app.version.isEmpty() ? "build " + app.code : app.version) : "Not installed") + " · " + n + (n == 1 ? " saved version" : " saved versions");
        TextView subT = label(sub, 13, muted, false); subT.setPadding(0, dp(2), 0, 0); words.addView(subT);
        Line line = new Line(); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(3)); lp.topMargin = dp(8); words.addView(line, lp);
        lines.put(app.pkg, line); applyProgress(app.pkg);
        row.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
        final StoreBackup.Saved target = target(app);
        ImageView restore = new ImageView(a); restore.setScaleType(ImageView.ScaleType.CENTER);
        int rid = a.getResources().getIdentifier("ic_ps_restore", "drawable", a.getPackageName()); if (rid != 0) restore.setImageResource(rid);
        restore.setColorFilter(BackupRim.green(a)); restore.setBackground(roundRipple(a, 4, 2));
        restore.setContentDescription(I18n.cs(target == null ? app.label + ": the saved version is the one installed" : "Restore " + app.label + " " + name(target)));
        restore.setVisibility(target != null ? View.VISIBLE : View.INVISIBLE);
        restore.setOnClickListener(v -> { if (target != null) chooseRestore(app, target); });
        restoreButtons.put(app.pkg, restore);
        row.addView(restore, new LinearLayout.LayoutParams(dp(44), dp(48)));
        final Chevron chev = new Chevron(); chev.setRotation(expanded.contains(app.pkg) ? 180 : 0);
        row.addView(chev, new LinearLayout.LayoutParams(dp(40), dp(48)));
        block.addView(row, new LinearLayout.LayoutParams(-1, -2));
        final LinearLayout versions = new LinearLayout(a); versions.setOrientation(LinearLayout.VERTICAL);
        if (saved != null) for (StoreBackup.Saved x : saved) versions.addView(versionRow(app, x), new LinearLayout.LayoutParams(-1, -2));
        versions.setVisibility(expanded.contains(app.pkg) ? View.VISIBLE : View.GONE);
        block.addView(versions, new LinearLayout.LayoutParams(-1, -2));
        row.setOnClickListener(v -> toggle(app.pkg, versions, chev));
        row.setContentDescription(I18n.cs(app.label + ", " + sub + ". Tap to " + (expanded.contains(app.pkg) ? "hide" : "show") + " saved versions"));
        if (!last) { View div = new View(a); div.setBackgroundColor(dark ? 0x1affffff : 0x12000000); LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(-1, Math.max(1, dp(.8f))); dl.leftMargin = dp(70); block.addView(div, dl); }
        return block;
    }

    private View versionRow(final App app, final StoreBackup.Saved x) {
        LinearLayout r = new LinearLayout(a); r.setGravity(Gravity.CENTER_VERTICAL); r.setPadding(dp(70), dp(6), dp(14), dp(10));
        LinearLayout words = new LinearLayout(a); words.setOrientation(LinearLayout.VERTICAL);
        words.addView(label(name(x), 14, ink, false));
        String what = app.installed ? (x.code == app.code ? "installed now" : x.code < app.code ? "older" : "newer") : "";
        TextView d = label((x.data ? "App and data" : "App only") + " · " + (x.kb + x.dataKb > 0 ? size(x.kb + x.dataKb) + " · " : "") + "build " + x.code + (what.isEmpty() ? "" : " · " + what), 12, muted, false); d.setPadding(0, dp(2), 0, 0); words.addView(d);
        r.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
        if (!(app.installed && x.code == app.code)) {
            TextView restore = chip("Restore");
            restore.setOnClickListener(v -> chooseRestore(app, x));
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-2, dp(36)); rp.rightMargin = dp(6); r.addView(restore, rp);
        }
        final TextView del = label("Delete", 14, muted, true); del.setGravity(Gravity.CENTER); del.setPadding(dp(12), 0, dp(12), 0); del.setMinHeight(dp(36));
        android.graphics.drawable.GradientDrawable fill = new android.graphics.drawable.GradientDrawable();
        fill.setCornerRadius(dp(18)); fill.setColor(dark || AppSurfaces.active(a) ? 0x1FFFFFFF : 0x0F000000);
        android.graphics.drawable.GradientDrawable dm = new android.graphics.drawable.GradientDrawable(); dm.setColor(0xffffffff); dm.setCornerRadius(dp(18));
        del.setBackground(new RippleDrawable(ColorStateList.valueOf(dark || AppSurfaces.active(a) ? 0x22ffffff : 0x14000000), fill, dm));
        del.setPadding(dp(16), 0, dp(16), 0);
        del.setContentDescription(I18n.cs("Delete saved version " + name(x)));
        final int red = dark || AppSurfaces.active(a) ? 0xFFFF8A80 : 0xFFC62828;
        final Runnable reset = () -> { del.setText(I18n.cs("Delete")); del.setTextColor(muted); del.setTag(null); };
        del.setOnClickListener(v -> {
            if (del.getTag() == null) { del.setTag(Boolean.TRUE); del.setText(I18n.cs("Delete?")); del.setTextColor(red); main.postDelayed(reset, 3000); return; }
            main.removeCallbacks(reset); del.setEnabled(false);
            io.execute(() -> { final boolean ok = StoreBackup.delete(app.pkg, x.code); main.post(() -> {
                if (!live()) return;
                if (!ok) { reset.run(); del.setEnabled(true); toast("Couldn't delete it. Check root."); return; }
                collapse(r, () -> reload());
            }); });
        });
        r.addView(del, new LinearLayout.LayoutParams(-2, dp(36)));
        return r;
    }

    private static String name(StoreBackup.Saved x) { return x.version == null || x.version.isEmpty() ? "Build " + x.code : x.version; }
    private static String size(long kb) { return kb >= 1024 * 1024 ? String.format(java.util.Locale.ROOT, "%.1f GB", kb / 1048576f) : kb >= 1024 ? Math.round(kb / 1024f) + " MB" : kb + " KB"; }

    private StoreBackup.Saved target(App app) {
        List<StoreBackup.Saved> l = data.get(app.pkg); if (l == null) return null;
        for (StoreBackup.Saved x : l) if (!app.installed || x.code != app.code) return x;
        return null;
    }

    private void toggle(String pkg, final LinearLayout versions, Chevron chev) {
        final boolean open = !expanded.contains(pkg);
        if (open) expanded.add(pkg); else expanded.remove(pkg);
        chev.animate().rotation(open ? 180 : 0).setDuration(320).setInterpolator(EASE).start();
        if (!AppMotion.enabled(a)) { versions.setVisibility(open ? View.VISIBLE : View.GONE); return; }
        int width = Math.max(dp(240), ((View) versions.getParent()).getWidth());
        versions.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        final int target = versions.getMeasuredHeight();
        final int from = open ? 0 : versions.getHeight();
        versions.setVisibility(View.VISIBLE); versions.getLayoutParams().height = from; versions.requestLayout();
        ValueAnimator anim = ValueAnimator.ofInt(from, open ? target : 0); anim.setDuration(340); anim.setInterpolator(EASE);
        anim.addUpdateListener(v -> { versions.getLayoutParams().height = (Integer) v.getAnimatedValue(); versions.setAlpha(open ? Math.min(1, .3f + v.getAnimatedFraction()) : Math.max(0, 1 - v.getAnimatedFraction())); versions.requestLayout(); });
        anim.addListener(new AnimatorListenerAdapter() { public void onAnimationEnd(Animator x) { versions.getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT; versions.setVisibility(open ? View.VISIBLE : View.GONE); versions.setAlpha(1); versions.requestLayout(); } });
        anim.start();
    }

    private void collapse(final View v, final Runnable done) {
        if (!AppMotion.enabled(a)) { done.run(); return; }
        final int h = v.getHeight();
        ValueAnimator anim = ValueAnimator.ofInt(h, 0); anim.setDuration(240); anim.setInterpolator(EASE);
        anim.addUpdateListener(x -> { v.getLayoutParams().height = (Integer) x.getAnimatedValue(); v.setAlpha(1 - x.getAnimatedFraction()); v.requestLayout(); });
        anim.addListener(new AnimatorListenerAdapter() { public void onAnimationEnd(Animator x) { done.run(); } });
        anim.start();
    }

    private void chooseRestore(final App app, final StoreBackup.Saved x) {
        if (progress.containsKey(app.pkg) || backingUp) return;
        if (x.data && !a.getPackageName().equals(app.pkg)) BackupChooser.restore(a, "Restore " + app.label + " " + name(x), app.installed, ink, muted, accent, d -> restore(app, x, d));
        else restore(app, x, false);
    }

    private void restore(final App app, final StoreBackup.Saved x, final boolean withData) {
        if (progress.containsKey(app.pkg) || backingUp) return;
        final String what = name(x);
        progress.put(app.pkg, 0f); failed.remove(app.pkg); applyProgress(app.pkg);
        io.execute(() -> {
            final String error = AppDataBackup.restore(app.pkg, x.code, withData, f -> main.post(() -> {
                Float now = progress.get(app.pkg);
                if (now != null && f > now && live()) { progress.put(app.pkg, f); applyProgress(app.pkg); }
            }));
            main.post(() -> {
                if (!live()) { progress.remove(app.pkg); return; }
                if (error != null) failed.add(app.pkg);
                Float f0 = progress.get(app.pkg);
                ValueAnimator fill = ValueAnimator.ofFloat(f0 == null ? 0f : f0, 1f); fill.setDuration(error == null ? 260 : 160);
                fill.addUpdateListener(v -> { progress.put(app.pkg, (Float) v.getAnimatedValue()); applyProgress(app.pkg); });
                fill.addListener(new AnimatorListenerAdapter() { public void onAnimationEnd(Animator an) {
                    main.postDelayed(() -> { progress.remove(app.pkg); failed.remove(app.pkg); if (!live()) return; applyProgress(app.pkg);
                        toast(error == null ? app.label + " is back to " + what + (withData ? ", with everything as it was" : "") : "Couldn't restore " + app.label + ": " + error); reload(); }, error == null ? 500 : 900);
                }});
                fill.start();
            });
        });
    }

    private void chooseHeld() {
        if (backingUp || held.isEmpty()) return;
        List<String> pk = new ArrayList<String>(); for (App x : held) pk.add(x.pkg);
        BackupChooser.backup(a, io, held.size() == 1 ? "Back up " + held.get(0).label : "Back up " + held.size() + " apps", pk, ink, muted, accent, this::backUpHeld);
    }

    private void backUpHeld(final boolean withData) {
        if (backingUp || held.isEmpty()) return;
        backingUp = true;
        final List<App> list = new ArrayList<App>(held);
        heldButton.setEnabled(false); heldButton.setAlpha(.6f); heldButton.setText(I18n.cs("Backing up…"));
        heldLine.fraction = 0; heldLine.failed = false; heldLine.setVisibility(View.VISIBLE); heldLine.invalidate();
        io.execute(() -> {
            int ok = 0;
            for (int i = 0; i < list.size(); i++) {
                final App x = list.get(i); final int at = i;
                main.post(() -> { if (live()) { heldSub.setText(I18n.cs("Saving " + x.label + " · " + (at + 1) + " of " + list.size())); glide(heldLine, at / (float) list.size()); } });
                boolean data = withData && !a.getPackageName().equals(x.pkg);
                boolean saved = StoreBackup.backup(x.pkg, x.version, x.code);
                if (saved && data) saved = AppDataBackup.backup(x.pkg, x.code, f -> main.post(() -> { if (live()) { float to = (at + .1f + .9f * f) / list.size(); if (to > heldLine.fraction) { heldLine.fraction = to; heldLine.invalidate(); } } })) == null;
                if (saved) {
                    ok++;
                    try { PackageManager pm = a.getPackageManager(); ApplicationInfo ai = pm.getApplicationInfo(x.pkg, 0); remember(a, x.pkg, x.label, ai.loadIcon(pm)); } catch (Throwable ignored) { }
                }
            }
            final int saved = ok;
            main.post(() -> {
                if (!live()) return;
                glide(heldLine, 1f);
                heldButton.setText(I18n.cs(saved == list.size() ? "Saved" : "Back up"));
                if (saved < list.size()) { heldLine.failed = true; heldLine.invalidate(); toast("Couldn't back up " + (list.size() - saved) + " of " + list.size() + ". Check root and free space."); }
                main.postDelayed(() -> { backingUp = false; if (!live()) return; heldLine.setVisibility(View.GONE); reload(); }, 900);
            });
        });
    }

    private void glide(final Line line, float to) {
        ValueAnimator v = ValueAnimator.ofFloat(line.fraction, to); v.setDuration(280); v.setInterpolator(EASE);
        v.addUpdateListener(x -> { line.fraction = (Float) x.getAnimatedValue(); line.invalidate(); }); v.start();
    }

    private void applyProgress(String pkg) {
        Line line = lines.get(pkg); Float f = progress.get(pkg);
        if (line != null) { line.setVisibility(f != null ? View.VISIBLE : View.GONE); if (f != null) { line.fraction = f; line.failed = failed.contains(pkg); line.invalidate(); } }
        View b = restoreButtons.get(pkg); if (b != null) { b.setEnabled(f == null); b.setAlpha(f == null ? 1f : .5f); }
    }

    private void toast(String s) { AppToast.makeText(a, I18n.cs(s), Toast.LENGTH_LONG).show(); }

    static RippleDrawable roundRipple(Activity a, float insetV, float insetH) {
        float d = a.getResources().getDisplayMetrics().density;
        android.graphics.drawable.GradientDrawable dot = new android.graphics.drawable.GradientDrawable();
        dot.setShape(android.graphics.drawable.GradientDrawable.OVAL); dot.setColor(0xffffffff);
        android.graphics.drawable.InsetDrawable mask = new android.graphics.drawable.InsetDrawable(dot, Math.round(insetH * d), Math.round(insetV * d), Math.round(insetH * d), Math.round(insetV * d));
        boolean dk = FrostedSurface.dark(a) || AppSurfaces.active(a);
        return new RippleDrawable(ColorStateList.valueOf(dk ? 0x22ffffff : 0x14000000), null, mask);
    }

    private RippleDrawable ripple(float radiusDp) {
        android.graphics.drawable.GradientDrawable mask = new android.graphics.drawable.GradientDrawable();
        mask.setColor(0xffffffff); mask.setCornerRadius(dp(radiusDp));
        return new RippleDrawable(ColorStateList.valueOf(dark || AppSurfaces.active(a) ? 0x22ffffff : 0x14000000), null, mask);
    }
    private int dp(float n) { return Math.round(n * a.getResources().getDisplayMetrics().density); }
    private TextView label(String value, float size, int color, boolean bold) { TextView t = new TextView(a); t.setText(I18n.cs(value)); t.setTextSize(size); t.setTextColor(color); if (bold) t.setTypeface(I18n.medium()); return t; }
    private void add(LinearLayout p, View v) { LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.bottomMargin = dp(12); p.addView(v, lp); }
    private TextView heading(LinearLayout p, String text) { TextView t = label(text, 14, muted, true); t.setPadding(dp(8), dp(12), dp(8), dp(8)); p.addView(t, new LinearLayout.LayoutParams(-1, -2)); return t; }
    private TextView note(LinearLayout p, String text) { TextView t = label(text, 14, muted, false); t.setPadding(dp(8), 0, dp(8), dp(8)); t.setLineSpacing(dp(2), 1); p.addView(t, new LinearLayout.LayoutParams(-1, -2)); return t; }
    private void noteInside(LinearLayout p, String text) { TextView t = label(text, 14, muted, false); t.setPadding(dp(20), dp(16), dp(20), dp(16)); p.addView(t, new LinearLayout.LayoutParams(-1, -2)); }
    private LinearLayout card(LinearLayout p) { LinearLayout v = new LinearLayout(a); v.setOrientation(LinearLayout.VERTICAL); v.setBackground(AppSurfaces.card(a, 22)); v.setClipToOutline(true); add(p, v); return v; }
    private TextView chip(String text) {
        TextView t = label(text, 14, ink, true); t.setGravity(Gravity.CENTER); t.setPadding(dp(16), 0, dp(16), 0); t.setSingleLine(true); I18n.fitLine(t);
        t.setBackground(new BackupRim(a, false, 18)); t.setLayerType(View.LAYER_TYPE_SOFTWARE, null); t.setClickable(true); t.setFocusable(true);
        t.setMinWidth(Math.round(t.getPaint().measureText(I18n.t("Backing up…"))) + dp(34));
        return t;
    }

    private final class Line extends View {
        float fraction; boolean failed; final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG); final RectF box = new RectF();
        Line() { super(a); setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO); setVisibility(View.GONE); }
        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), r = h / 2f; if (w <= 0) return;
            boolean dk = FrostedSurface.dark(a) || AppSurfaces.active(a);
            paint.setColor(dk ? 0x33ffffff : 0x1f000000); box.set(0, 0, w, h); c.drawRoundRect(box, r, r, paint);
            paint.setColor(failed ? (dk ? 0xFFFF8A80 : 0xFFC62828) : BackupRim.green(a)); box.set(0, 0, Math.max(h, w * Math.max(0f, Math.min(1f, fraction))), h); c.drawRoundRect(box, r, r, paint);
        }
    }

    private final class Chevron extends View {
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG); final Path path = new Path();
        Chevron() { super(a); setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO); }
        @Override protected void onDraw(Canvas c) {
            float d = getResources().getDisplayMetrics().density, x = getWidth() / 2f, y = getHeight() / 2f;
            paint.setColor(muted); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(2 * d); paint.setStrokeCap(Paint.Cap.ROUND); paint.setStrokeJoin(Paint.Join.ROUND);
            path.reset(); path.moveTo(x - 5 * d, y - 2 * d); path.lineTo(x, y + 3 * d); path.lineTo(x + 5 * d, y - 2 * d); c.drawPath(path, paint);
        }
    }

    private final class Placeholder extends Drawable {
        final String letter; final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Placeholder(String label) { letter = label == null || label.isEmpty() ? "?" : label.substring(0, 1).toUpperCase(java.util.Locale.ROOT); }
        @Override public void draw(Canvas c) {
            android.graphics.Rect b = getBounds(); float r = Math.min(b.width(), b.height()) / 2f;
            boolean dk = FrostedSurface.dark(a) || AppSurfaces.active(a);
            paint.setStyle(Paint.Style.FILL); paint.setColor(dk ? 0xff34343b : 0xffe3e1ec); c.drawCircle(b.exactCenterX(), b.exactCenterY(), r, paint);
            paint.setColor(muted); paint.setTextSize(r); paint.setTextAlign(Paint.Align.CENTER); paint.setTypeface(I18n.medium());
            c.drawText(letter, b.exactCenterX(), b.exactCenterY() - (paint.descent() + paint.ascent()) / 2f, paint);
        }
        @Override public void setAlpha(int x) { }
        @Override public void setColorFilter(android.graphics.ColorFilter f) { }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }
}
