package io.github.pixelsuite;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.inputmethodservice.InputMethodService;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class GboardClipAll {
    private GboardClipAll() {}

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final long FOREVER = 100L * 365 * 24 * 3600 * 1000;

    private static Method sQuery, sDeleteIds;
    private static String sHandler;
    private static Method sReload;
    private static String sAdapterReload;
    private static WeakReference<Object> sAdapter = new WeakReference<Object>(null);
    private static WeakReference<Object> sKeyboard = new WeakReference<Object>(null);
    private static final ThreadLocal<Boolean> sClearing = new ThreadLocal<Boolean>();

    static void prepare(final InputMethodService ims, ClassLoader cl) throws Throwable {
        String utils = GboardPlus.gboardClass(ims, "cliputils", "Failed to parse retention hours");
        sHandler = GboardPlus.gboardClass(ims, "clipdata", "This copied item is a duplicate of an item that is already pinned.");
        if (utils == null) return;
        Class<?> u = cl.loadClass(utils);
        Method retention = null, list = null, query = null, delIds = null;
        int retentions = 0, delIdsCount = 0;
        for (Method m : u.getDeclaredMethods()) {
            if (!Modifier.isStatic(m.getModifiers())) continue;
            Class<?>[] t = m.getParameterTypes();
            Class<?> r = m.getReturnType();
            if (r == long.class && t.length == 1 && t[0] == Context.class) { retention = m; retentions++; }
            else if (t.length == 2 && t[0] == Context.class && t[1] == boolean.class && !r.isPrimitive() && List.class.isAssignableFrom(r)) list = m;
            else if (t.length == 4 && t[0] == Context.class && t[1] == String.class && t[2] == String[].class && t[3] == String.class && List.class.isAssignableFrom(r)) query = m;
            else if (r == void.class && t.length == 2 && t[0] == Context.class && t[1] == List.class) { delIds = m; delIdsCount++; }
        }
        if (delIdsCount != 1) delIds = null;
        sQuery = query; sDeleteIds = delIds;
        if (query != null) query.setAccessible(true);
        if (delIds != null) delIds.setAccessible(true);
        if (retention != null && retentions == 1) Xp.hookMethod(retention, new GboardMain.Kept() {
            @Override protected void afterHookedMethod(Xp.Param p) { if (GboardPlus.premium()) p.setResult(FOREVER); }
        });
        if (delIds != null && sHandler != null) Xp.hookMethod(delIds, new GboardMain.Kept() {
            @Override protected void beforeHookedMethod(Xp.Param p) {
                if (Boolean.TRUE.equals(sClearing.get()) || !GboardPlus.premium()) return;
                for (StackTraceElement e : Thread.currentThread().getStackTrace()) if (sHandler.equals(e.getClassName())) { p.setResult(null); return; }
            }
        });
        if (list != null && query != null) {
            final Class<?> listType = list.getReturnType();
            Method copy = null;
            for (Method m : listType.getDeclaredMethods())
                if (Modifier.isStatic(m.getModifiers()) && m.getParameterTypes().length == 1 && m.getParameterTypes()[0] == Collection.class && listType.isAssignableFrom(m.getReturnType())) { if (copy != null) { copy = null; break; } copy = m; }
            final Method copyOf = copy;
            if (copyOf != null) {
                copyOf.setAccessible(true);
                Xp.hookMethod(list, new Xp.Callback() {
                    @Override protected void afterHookedMethod(Xp.Param p) {
                        try {
                            if (!(p.getResult() instanceof List) || !(p.args[0] instanceof Context) || !GboardPlus.premium()) return;
                            List<?> shown = (List<?>) p.getResult();
                            List<?> all = (List<?>) sQuery.invoke(null, p.args[0], "(item_type & 3) = 0", null, "timestamp DESC");
                            if (all == null || all.isEmpty()) return;
                            Set<String> keys = new HashSet<String>();
                            for (Object o : all) keys.add(key(o));
                            int first = -1, last = -1;
                            for (int i = 0; i < shown.size(); i++) if (keys.contains(key(shown.get(i)))) { if (first < 0) first = i; last = i; }
                            if (first < 0 && all.size() == 0) return;
                            int at = first >= 0 ? first : (Boolean.TRUE.equals(p.args[1]) && !shown.isEmpty() ? 1 : 0);
                            int after = first >= 0 ? last + 1 : at;
                            ArrayList<Object> out = new ArrayList<Object>(shown.size() + all.size());
                            for (int i = 0; i < at && i < shown.size(); i++) out.add(shown.get(i));
                            out.addAll(all);
                            for (int i = after; i < shown.size(); i++) out.add(shown.get(i));
                            p.setResult(copyOf.invoke(null, out));
                        } catch (Throwable ignored) { }
                    }
                });
            }
        }
        if (list != null) {
            try {
                final Method listOf = list;
                listOf.setAccessible(true);
                final Context app = ims.getApplicationContext();
                String cb = GboardPlus.gboardClass(ims, "clipreloadcall", "((%s | ?) - (%s & ?)) & ? = 0");
                Class<?> cc = cb == null ? null : cl.loadClass(cb);
                Method call = null;
                if (cc != null && java.util.concurrent.Callable.class.isAssignableFrom(cc))
                    for (Method m : cc.getDeclaredMethods()) if ("call".equals(m.getName()) && m.getParameterTypes().length == 0) { call = m; break; }
                if (call != null) Xp.hookMethod(call, new Xp.Callback() {
                    @Override protected void afterHookedMethod(Xp.Param p) {
                        try {
                            if (!GboardPlus.premium() || !(p.getResult() instanceof List)) return;
                            List<?> gboard = (List<?>) p.getResult();
                            if (gboard.isEmpty()) return;
                            List<?> all = (List<?>) listOf.invoke(null, app, true);
                            if (all == null || all.isEmpty() || all.get(0) != gboard.get(0)) return;
                            p.setResult(new ArrayList<Object>(all));
                        } catch (Throwable ignored) { }
                    }
                });
            } catch (Throwable t) { }
        }
        try {
            final Method trim = trimMethod(ims, cl);
            if (trim != null) Xp.hookMethod(trim, new Xp.Callback() {
                @Override protected void beforeHookedMethod(Xp.Param p) {
                    if (!GboardPlus.premium()) return;
                    p.setResult(null);
                }
            });
        } catch (Throwable t) { }
        try {
            Class<?> kb = cl.loadClass("com.google.android.apps.inputmethod.libs.clipboard.ClipboardKeyboard");
            Xp.hookAllConstructors(kb, new Xp.Callback() {
                @Override protected void afterHookedMethod(Xp.Param p) { sKeyboard = new WeakReference<Object>(p.thisObject); }
            });
            sReload = reloadMethod(ims, kb);
        } catch (Throwable ignored) { }
    }

    private static Method trimMethod(Context c, ClassLoader cl) throws Throwable {
        String adapter = GboardPlus.gboardClass(c, "clipadapter", "com/google/android/apps/inputmethod/libs/clipboard/ClipboardAdapter");
        if (adapter == null) return null;
        Class<?> ad = cl.loadClass(adapter);
        long ver = c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionCode;
        File f = new File(c.getCacheDir(), "pixelsuite_gboard_cliptrim_" + ver);
        String name = null;
        if (f.isFile()) {
            byte[] b = new byte[(int) f.length()];
            FileInputStream in = new FileInputStream(f);
            try { in.read(b); } finally { in.close(); }
            name = new String(b, "UTF-8").trim();
        } else {
            Map<String, List<String>> calls = DexPeek.methodCalls(c, adapter);
            if (calls == null) return null;
            int found = 0;
            for (Method m : ad.getDeclaredMethods()) {
                if (Modifier.isStatic(m.getModifiers()) || m.getParameterTypes().length != 0 || m.getReturnType() != void.class) continue;
                List<String> l = calls.get(m.getName());
                if (l == null) continue;
                boolean set = false, remove = false;
                for (String x : l) { if (x.startsWith("Ljava/util/HashSet;.<init>")) set = true; if (x.startsWith("Ljava/util/List;.remove(I)")) remove = true; }
                if (set && remove) { name = m.getName(); found++; }
            }
            if (found != 1) name = "";
            FileOutputStream out = new FileOutputStream(f);
            try { out.write(name.getBytes("UTF-8")); } finally { out.close(); }
        }
        if (name == null || name.isEmpty()) return null;
        Method m = ad.getDeclaredMethod(name);
        m.setAccessible(true);
        return m;
    }

    private static Context app() {
        try { return (Context) Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null); } catch (Throwable t) { return null; }
    }

    private static Method reloadMethod(Context c, Class<?> kb) throws Throwable {
        long ver = c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionCode;
        File f = new File(c.getCacheDir(), "pixelsuite_gboard_clipreload2_" + ver);
        String name = null;
        if (f.isFile()) {
            byte[] b = new byte[(int) f.length()];
            FileInputStream in = new FileInputStream(f);
            try { in.read(b); } finally { in.close(); }
            String[] parts = new String(b, "UTF-8").trim().split(" ");
            name = parts[0];
            if (parts.length > 1) sAdapterReload = parts[1];
        }
        if (name == null || name.isEmpty()) {
            String adapter = GboardPlus.gboardClass(c, "clipadapter", "com/google/android/apps/inputmethod/libs/clipboard/ClipboardAdapter");
            Map<String, List<String>> calls = DexPeek.methodCalls(c, kb.getName());
            if (adapter == null || calls == null) return null;
            String owner = "L" + adapter.replace('.', '/') + ";.";
            for (Method m : kb.getDeclaredMethods()) {
                if (Modifier.isStatic(m.getModifiers()) || m.getParameterTypes().length != 0 || m.getReturnType() != void.class) continue;
                List<String> l = calls.get(m.getName());
                if (l != null && l.size() == 1 && l.get(0).startsWith(owner) && l.get(0).endsWith("()V")) {
                    name = m.getName();
                    sAdapterReload = l.get(0).substring(owner.length(), l.get(0).indexOf('('));
                    break;
                }
            }
            if (name == null) return null;
            FileOutputStream out = new FileOutputStream(f);
            try { out.write((name + (sAdapterReload != null ? " " + sAdapterReload : "")).getBytes("UTF-8")); } finally { out.close(); }
        }
        Method m = kb.getDeclaredMethod(name);
        m.setAccessible(true);
        return m;
    }

    private static String key(Object o) {
        if (o == null) return "null";
        StringBuilder b = new StringBuilder(o.getClass().getName());
        for (Class<?> k = o.getClass(); k != null && k != Object.class; k = k.getSuperclass())
            for (Field f : k.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                Class<?> t = f.getType();
                if (!(t == long.class || t == int.class || t == String.class || t.getName().equals("java.time.Instant"))) continue;
                try { f.setAccessible(true); b.append('|').append(String.valueOf(f.get(o)).hashCode()); } catch (Throwable ignored) { }
            }
        return b.toString();
    }

    private static final Set<View> sDone = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<View, Boolean>());
    private static WeakReference<View> sIcons = new WeakReference<View>(null);
    private static final Set<View> sWaiting = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<View, Boolean>());
    private static final Set<View> sRetries = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<View, Boolean>());
    private static WeakReference<android.app.Dialog> sDialog = new WeakReference<android.app.Dialog>(null);

    static void ensureButton(View anyRow, Object adapter) {
        if (adapter != null && sAdapter.get() != adapter) sAdapter = new WeakReference<Object>(adapter);
        ensureButton(anyRow);
    }

    static void ensureButton(View anyRow) {
        try {
            if (anyRow == null || !GboardPlus.premium()) return;
            View done = sIcons.get();
            if (done != null && done.isAttachedToWindow() && sDone.contains(done)) return;
            View root = GboardPlus.imeDecor();
            if (root == null) root = anyRow.getRootView();
            View box = find(root, "key_pos_clipboard_icons");
            if (box != null && sDone.contains(box)) return;
            if (box == null) {
                if (!anyRow.isAttachedToWindow() && sWaiting.add(anyRow)) anyRow.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                    public void onViewAttachedToWindow(View v) { v.removeOnAttachStateChangeListener(this); sWaiting.remove(v); ensureButton(v); }
                    public void onViewDetachedFromWindow(View v) { }
                });
                return;
            }
            if (!(box instanceof LinearLayout)) { return; }
            final LinearLayout icons = (LinearLayout) box;
            final View pin = find(icons, "key_pos_clipboard_batch_pin");
            final View batchDelete = find(icons, "key_pos_clipboard_batch_delete");
            View model = pin != null && pin.getWidth() > 0 ? pin : null;
            if (model == null) for (int i = 0; i < icons.getChildCount(); i++) { View c = icons.getChildAt(i); if (c.getVisibility() == View.VISIBLE && c.getWidth() > 0) { model = c; break; } }
            if (model == null) {
                if (sRetries.add(icons)) icons.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                    int tries;
                    public void onGlobalLayout() {
                        if (sDone.contains(icons) || ++tries > 20) { try { icons.getViewTreeObserver().removeOnGlobalLayoutListener(this); } catch (Throwable ignored) { } sRetries.remove(icons); return; }
                        sRetries.remove(icons);
                        ensureButton(icons);
                        sRetries.add(icons);
                    }
                });
                return;
            }
            sDone.add(icons);
            sIcons = new WeakReference<View>(icons);
            final Context c = icons.getContext();
            final TextView title = title(icons.getRootView(), icons);
            final View button = new View(c) {
                final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
                @Override protected void onDraw(Canvas k) {
                    float d = getResources().getDisplayMetrics().density, w = getWidth(), h = getHeight(), r = Math.min(w, h) * 0.30f;
                    int ink = title != null ? title.getCurrentTextColor() : 0xFF444746;
                    p.setColor(ink); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2f * d); p.setStrokeCap(Paint.Cap.ROUND);
                    k.drawCircle(w / 2f, h / 2f, r, p);
                    float x = r * 0.42f;
                    k.drawLine(w / 2f - x, h / 2f - x, w / 2f + x, h / 2f + x, p);
                    k.drawLine(w / 2f + x, h / 2f - x, w / 2f - x, h / 2f + x, p);
                }
            };
            button.setContentDescription(I18n.cs("Clear clipboard"));
            TypedValue tv = new TypedValue();
            if (c.getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, tv, true)) { try { button.setBackground(c.getDrawable(tv.resourceId)); } catch (Throwable ignored) { } }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(model.getWidth(), model.getHeight());
            if (model.getLayoutParams() instanceof LinearLayout.LayoutParams) {
                LinearLayout.LayoutParams m = (LinearLayout.LayoutParams) model.getLayoutParams();
                lp.setMargins(m.leftMargin, m.topMargin, m.rightMargin, m.bottomMargin);
                lp.setMarginStart(m.getMarginStart()); lp.setMarginEnd(m.getMarginEnd());
                lp.gravity = m.gravity;
            }
            int at = 0;
            for (int i = 0; i < icons.getChildCount(); i++) if (icons.getChildAt(i).getVisibility() == View.VISIBLE) { at = i; break; }
            icons.addView(button, at, lp);
            button.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
                try { v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP); } catch (Throwable ignored) { }
                ask(v);
            }});
            icons.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                public void onGlobalLayout() {
                    boolean edit = batchDelete != null && batchDelete.isShown() && batchDelete.getWidth() > 0 && batchDelete.getAlpha() > 0.05f;
                    int want = edit || !GboardPlus.premium() ? View.GONE : View.VISIBLE;
                    if (button.getVisibility() != want) { button.setVisibility(want);  }
                }
            });
        } catch (Throwable ignored) { }
    }

    private static TextView title(View root, View icons) {
        View header = icons;
        for (int i = 0; i < 4 && header.getParent() instanceof View; i++) header = (View) header.getParent();
        return firstText(header);
    }

    private static TextView firstText(View v) {
        if (v instanceof TextView && v.isShown() && ((TextView) v).getText() != null && ((TextView) v).getText().length() > 0) return (TextView) v;
        if (v instanceof ViewGroup) { ViewGroup g = (ViewGroup) v; for (int i = 0; i < g.getChildCount(); i++) { TextView t = firstText(g.getChildAt(i)); if (t != null) return t; } }
        return null;
    }

    private static void ask(final View anchor) {
        android.app.Dialog old = sDialog.get();
        if (old != null && old.isShowing()) return;
        GboardPlus.confirm(anchor, "Clear the clipboard?", "Every clip is deleted except pinned ones.", "Clear", new Runnable() { public void run() { clear(anchor.getContext().getApplicationContext()); } }, sDialog);
    }

    private static void clear(final Context c) {
        new Thread(new Runnable() { public void run() {
            try {
                if (sDeleteIds == null) { return; }
                List<Long> ids = unpinnedIds(c);
                if (!ids.isEmpty()) {
                    sClearing.set(Boolean.TRUE);
                    try { sDeleteIds.invoke(null, c, ids); } finally { sClearing.remove(); }
                }
            } catch (Throwable t) { }
            MAIN.post(new Runnable() { public void run() { reload(c); } });
        }}, "PixelSuiteClipClear").start();
    }

    private static List<Long> unpinnedIds(Context c) {
        List<Long> out = new ArrayList<Long>();
        android.database.Cursor q = null;
        try {
            android.net.Uri uri = android.net.Uri.parse("content://" + c.getPackageName() + ".clipboard_content/clips");
            q = c.getContentResolver().query(uri, new String[] { "_id" }, "(item_type & 3) = 0", null, null);
            if (q != null) while (q.moveToNext()) out.add(q.getLong(0));
        } catch (Throwable t) { }
        finally { if (q != null) try { q.close(); } catch (Throwable ignored) { } }
        return out;
    }

    private static void reload(Context c) {
        try {
            Object ad = sAdapter.get();
            if (ad != null && sAdapterReload != null) {
                for (Class<?> k = ad.getClass(); k != null && k != Object.class; k = k.getSuperclass()) {
                    try { Method m = k.getDeclaredMethod(sAdapterReload); m.setAccessible(true); m.invoke(ad);  return; } catch (NoSuchMethodException ignored) { }
                }
            }
            Object kb = sKeyboard.get();
            if (kb != null && sReload != null) { sReload.invoke(kb);  return; }
        } catch (Throwable t) { }
    }

    private static View find(View v, String id) {
        if (v == null) return null;
        if (v.getId() != View.NO_ID && v.getId() != 0) { try { if (id.equals(v.getResources().getResourceEntryName(v.getId()))) return v; } catch (Throwable ignored) { } }
        if (v instanceof ViewGroup) { ViewGroup g = (ViewGroup) v; for (int i = 0; i < g.getChildCount(); i++) { View f = find(g.getChildAt(i), id); if (f != null) return f; } }
        return null;
    }
}
