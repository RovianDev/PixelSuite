package io.github.pixelsuite;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.animation.OvershootInterpolator;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

final class BackupChooser {
    private BackupChooser() {}

    interface Chosen { void go(boolean withData); }

    private static final String PREFS = "pixelsuite_backups", WITH_DATA = "with_data";

    static boolean withData(Activity a) { try { return a.getSharedPreferences(PREFS, 0).getBoolean(WITH_DATA, true); } catch (Throwable t) { return true; } }
    private static void remember(Activity a, boolean data) { try { SharedPreferences.Editor e = a.getSharedPreferences(PREFS, 0).edit(); e.putBoolean(WITH_DATA, data); e.apply(); } catch (Throwable ignored) { } }

    static void backup(final Activity a, Executor io, String title, final List<String> packages, int ink, int muted, int accent, final Chosen done) {
        final Option[] rows = show(a, title, "Back up", withData(a), ink, muted, accent,
                "App and data", "Everything: the app, its sign-ins, chats, settings and files. Restores exactly as it was.",
                "App only", "Just the app. Its data isn't saved.", new Chosen() { public void go(boolean data) { remember(a, data); done.go(data); } });
        rows[0].size.setText(I18n.cs("Working out the size…")); rows[1].size.setText(I18n.cs("Working out the size…"));
        io.execute(new Runnable() { public void run() {
            Map<String, long[]> m = AppDataBackup.sizes(packages);
            long apk = 0, data = 0; for (long[] v : m.values()) { apk += v[0]; data += v[1]; }
            final boolean known = !m.isEmpty();
            long free = -1; try { free = new android.os.StatFs(android.os.Environment.getDataDirectory().getPath()).getAvailableBytes() / 1024; } catch (Throwable ignored) { }
            final long f = free;
            final long[] need = {apk + data, apk};
            rows[0].size.post(new Runnable() { public void run() {
                for (int i = 0; i < 2; i++) {
                    Option o = rows[i];
                    o.need = known ? need[i] : -1; o.free = f;
                    boolean fits = fits(o);
                    o.size.setText(I18n.cs(!known ? "" : fits ? "About " + size(need[i]) : "About " + size(need[i]) + " · only " + size(Math.max(0, f)) + " free"));
                    o.size.setTextColor(fits ? BackupRim.green(a) : (FrostedSurface.dark(a) || AppSurfaces.active(a) ? 0xffff6b6b : 0xffc62828));
                    o.size.setVisibility(known ? View.VISIBLE : View.GONE);
                }
                allow(rows);
            }});
        }});
    }

    private static final long ROOM_KB = 256L * 1024L;
    private static boolean fits(Option o) { return o.need < 0 || o.free < 0 || o.need + ROOM_KB <= o.free; }

    private static void allow(Option[] rows) {
        AlertDialog d = rows[0].dialog; if (d == null || rows[0].chosen == null) return;
        Button b = d.getButton(AlertDialog.BUTTON_POSITIVE); if (b == null) return;
        boolean ok = fits(rows[rows[0].chosen[0] ? 0 : 1]);
        b.setEnabled(ok); b.setAlpha(ok ? 1f : .4f);
    }

    static void restore(Activity a, String title, boolean installed, int ink, int muted, int accent, Chosen done) {
        Option[] rows = show(a, title, "Restore", true, ink, muted, accent,
                "App and data", installed ? "Puts everything back exactly as it was saved. Its data now is replaced." : "Puts everything back exactly as it was saved.",
                "App only", installed ? "Only the app goes back. Its data now stays." : "Only the app, signed out and empty.", done);
        rows[0].size.setVisibility(View.GONE); rows[1].size.setVisibility(View.GONE);
    }

    static String size(long kb) {
        if (kb >= 1024L * 1024L) return String.format(java.util.Locale.ROOT, "%.1f GB", kb / 1048576f);
        if (kb >= 1024) return Math.round(kb / 1024f) + " MB";
        return Math.max(1, kb) + " KB";
    }

    private static final class Option { LinearLayout row; TextView size; Check check; long need = -1, free = -1; AlertDialog dialog; boolean[] chosen; }

    private static Option[] show(final Activity a, String title, String action, boolean dataFirst, final int ink, final int muted, final int accent,
                                 String t1, String s1, String t2, String s2, final Chosen done) {
        final float d = a.getResources().getDisplayMetrics().density;
        LinearLayout body = new LinearLayout(a); body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(Math.round(20 * d), Math.round(6 * d), Math.round(20 * d), Math.round(2 * d));
        final Option[] rows = {option(a, t1, s1, ink, muted), option(a, t2, s2, ink, muted)};
        final boolean[] data = {dataFirst};
        for (int i = 0; i < 2; i++) {
            final int index = i;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.bottomMargin = Math.round(10 * d);
            body.addView(rows[i].row, lp);
            rows[i].row.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
                boolean want = index == 0;
                if (data[0] == want) return;
                data[0] = want; select(a, rows, want, true); allow(rows);
            }});
        }
        select(a, rows, dataFirst, false);
        final AlertDialog dialog = new AlertDialog.Builder(a).setTitle(I18n.cs(title)).setView(body)
                .setNegativeButton(I18n.cs("Cancel"), null)
                .setPositiveButton(I18n.cs(action), (x, w) -> done.go(data[0])).create();
        dialog.show(); Rtl.dialog(dialog);
        Window w = dialog.getWindow(); if (w != null) w.setBackgroundDrawable(AppSurfaces.dialog(a, 28));
        for (int which : new int[]{AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE}) { Button b = dialog.getButton(which); if (b != null) b.setTextColor(accent); }
        int titleId = a.getResources().getIdentifier("alertTitle", "id", "android");
        TextView t = titleId == 0 ? null : (TextView) dialog.findViewById(titleId); if (t != null) t.setTextColor(ink);
        rows[0].dialog = dialog; rows[0].chosen = data; allow(rows);
        return rows;
    }

    private static Option option(Activity a, String title, String sub, int ink, int muted) {
        float d = a.getResources().getDisplayMetrics().density;
        Option o = new Option();
        o.row = new LinearLayout(a); o.row.setGravity(Gravity.CENTER_VERTICAL);
        o.row.setPadding(Math.round(16 * d), Math.round(14 * d), Math.round(14 * d), Math.round(14 * d)); o.row.setMinimumHeight(Math.round(76 * d));
        o.row.setClickable(true); o.row.setFocusable(true); o.row.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        LinearLayout words = new LinearLayout(a); words.setOrientation(LinearLayout.VERTICAL);
        TextView t = new TextView(a); t.setText(I18n.cs(title)); t.setTextSize(16); t.setTextColor(ink); t.setTypeface(I18n.medium()); words.addView(t);
        TextView s = new TextView(a); s.setText(I18n.cs(sub)); s.setTextSize(13.5f); s.setTextColor(muted); s.setPadding(0, Math.round(3 * d), 0, 0); s.setLineSpacing(Math.round(1.5f * d), 1); words.addView(s);
        o.size = new TextView(a); o.size.setTextSize(12.5f); o.size.setTextColor(BackupRim.green(a)); o.size.setTypeface(I18n.medium()); o.size.setPadding(0, Math.round(5 * d), 0, 0); words.addView(o.size);
        o.row.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
        o.check = new Check(a, muted);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(Math.round(26 * d), Math.round(26 * d)); cp.leftMargin = Math.round(12 * d);
        o.row.addView(o.check, cp);
        o.row.setContentDescription(I18n.cs(title + ". " + sub));
        return o;
    }

    private static void select(Activity a, Option[] rows, boolean dataChosen, boolean animate) {
        float d = a.getResources().getDisplayMetrics().density;
        boolean dark = FrostedSurface.dark(a) || AppSurfaces.active(a);
        for (int i = 0; i < 2; i++) {
            boolean on = (i == 0) == dataChosen;
            GradientDrawable mask = new GradientDrawable(); mask.setColor(0xffffffff); mask.setCornerRadius(20 * d);
            GradientDrawable plain = new GradientDrawable(); plain.setCornerRadius(20 * d); plain.setColor(0x00000000);
            plain.setStroke(Math.max(1, Math.round(d)), dark ? 0x26ffffff : 0x1f000000);
            android.graphics.drawable.Drawable frame = on ? new BackupRim(a, false, 20) : plain;
            rows[i].row.setBackground(new RippleDrawable(ColorStateList.valueOf(dark ? 0x22ffffff : 0x14000000), frame, mask));
            rows[i].row.setSelected(on);
            rows[i].check.set(on, animate);
        }
    }

    private static final class Check extends View {
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG); final Path tick = new Path(); final int muted; boolean on; float k = 1f;
        Check(Activity a, int muted) { super(a); this.muted = muted; setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO); }
        void set(boolean value, boolean animate) {
            on = value;
            if (on && animate && AppMotion.enabled(getContext())) {
                ValueAnimator v = ValueAnimator.ofFloat(.55f, 1f); v.setDuration(260); v.setInterpolator(new OvershootInterpolator(2.2f));
                v.addUpdateListener(x -> { k = (Float) x.getAnimatedValue(); invalidate(); }); v.start();
            } else { k = 1f; invalidate(); }
        }
        @Override protected void onDraw(Canvas c) {
            float d = getResources().getDisplayMetrics().density, cx = getWidth() / 2f, cy = getHeight() / 2f, r = Math.min(cx, cy) - 1.5f * d;
            if (!on) { paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(2 * d); paint.setColor(muted); c.drawCircle(cx, cy, r, paint); return; }
            paint.setStyle(Paint.Style.FILL); paint.setColor(BackupRim.green(getContext())); c.drawCircle(cx, cy, r * k, paint);
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(2.2f * d); paint.setStrokeCap(Paint.Cap.ROUND); paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setColor(AppSurfaces.active(getContext()) ? 0xff000000 : 0xffffffff);
            tick.reset(); float s = r * k; tick.moveTo(cx - .45f * s, cy + .02f * s); tick.lineTo(cx - .12f * s, cy + .35f * s); tick.lineTo(cx + .48f * s, cy - .3f * s); c.drawPath(tick, paint);
        }
    }
}
