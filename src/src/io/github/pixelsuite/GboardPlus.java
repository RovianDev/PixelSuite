package io.github.pixelsuite;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.Icon;
import android.inputmethodservice.InputMethodService;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import android.widget.TextView;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.regex.Pattern;

final class GboardPlus {
    private GboardPlus() {}

    static final String KEY_SHORTCUTS = "pixelsuite_gboard_shortcuts";
    static final String KEY_KEYS = "pixelsuite_gboard_ios_keys";
    static final String KEY_CLIP = "pixelsuite_gboard_clip_single";
    static final String KEY_HIDE_CHIPS = "pixelsuite_gboard_hide_chips";
    static final String KEY_HIDE_MENU = "pixelsuite_gboard_hide_menu";
    static final String KEY_HIDE_RIGHT = "pixelsuite_gboard_hide_right";
    static final String KEY_ROWS = "pixelsuite_gboard_two_rows";
    static final String KEY_DICTATION = "pixelsuite_gboard_dictation";
    static final String KEY_ROWS_TOP = "pixelsuite_gboard_shortcuts_top";
    static final String KEY_LOWER = "pixelsuite_gboard_lower";
    static final String KEY_FREE = "pixelsuite_gboard_free_position";
    static final String KEY_DEL_LETTERS = "pixelsuite_gboard_delete_letters";
    static final String KEY_AR_NO_AUTOCORRECT = "pixelsuite_gboard_ar_no_autocorrect";
    static final int FREE_DP = 18;
    static final String KEY_GEO = "pixelsuite_gboard_geometry";
    static final String KEY_BAR = "pixelsuite_gboard_bar";
    private static final String TAG = "PixelSuite";
    private static final String BAR = "com.google.android.libraries.inputmethod.accesspoint.widget.AccessPointsBar";
    private static final String SOFT_KEY = "com.google.android.libraries.inputmethod.widgets.SoftKeyView";
    private static final String HOLDER_STRING = "CustomizeAccessPointsCountOnBar = ";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static WeakReference<InputMethodService> sIme = new WeakReference<InputMethodService>(null);
    private static final Map<String, Boolean> sOn = new HashMap<String, Boolean>();
    private static ClassLoader sCl;

    static void install(ClassLoader cl) {
        sCl = cl;
        try { installIme(cl); } catch (Throwable t) { Log.w("ime", t); }
        try { installValidIds(); } catch (Throwable t) { Log.w("ids", t); }
        try { installKeys(cl); } catch (Throwable t) { Log.w("keys", t); }
        try { installClipboard(cl); } catch (Throwable t) { Log.w("clipboard", t); }
        try { installViews(cl); } catch (Throwable t) { Log.w("views", t); }
        try { installRows(cl); } catch (Throwable t) { Log.w("rows", t); }
        try { installOverlayWatch(); } catch (Throwable t) { Log.w("overlay watch", t); }
        try { installHintWatch(); } catch (Throwable t) { Log.w("hint watch", t); }
        try { installDeleteSwipe(cl); } catch (Throwable t) { Log.w("delete swipe", t); }
        try { installPanelSwipe(cl); } catch (Throwable t) { Log.w("panel swipe", t); }
        try { installBarTaps(cl); } catch (Throwable t) { Log.w("shortcut taps", t); }
        try { installVoice(cl); } catch (Throwable t) { Log.w("voice", t); }
        try { installGeometry(); } catch (Throwable t) { Log.w("geometry", t); }
        try { installTwoLineFit(); } catch (Throwable t) { Log.w("two-line fit", t); }
        try { GboardFrost.install(); } catch (Throwable t) { Log.w("frosted glass", t); }
    }

    private static void installTwoLineFit() {

        try {
            final Class<?> holder = Class.forName("com.google.android.apps.inputmethod.latin.keyboard.widget.LatinFixedCountCandidatesHolderView", false, sCl);
            boolean own = false;
            for (Method m : holder.getDeclaredMethods()) if ("onLayout".equals(m.getName())) own = true;
            Xp.hookAllMethods(own ? holder : android.widget.LinearLayout.class, "onLayout", new Xp.Callback() {
                @Override protected void beforeHookedMethod(Xp.Param p) {
                    try {
                        if (!holder.isInstance(p.thisObject) || p.args.length < 5 || !on(KEY_ROWS)) return;
                        ViewGroup g = (ViewGroup) p.thisObject;
                        int h = (Integer) p.args[4] - (Integer) p.args[2] - g.getPaddingTop() - g.getPaddingBottom();
                        if (h <= 0) return;
                        for (int i = 0; i < g.getChildCount(); i++) {
                            View c = g.getChildAt(i);
                            if (c.getVisibility() == View.GONE || c.getMeasuredHeight() <= h) continue;
                            c.measure(View.MeasureSpec.makeMeasureSpec(c.getMeasuredWidth(), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
                        }
                    } catch (Throwable ignored) { }
                }
            });
        } catch (Throwable t) { Log.w("candidate height", t); }
    }

    private static boolean on(String key) {
        synchronized (sOn) {
            Boolean v = sOn.get(key);
            if (v != null) return v;
        }
        boolean v = false;
        try {
            Context c = app();
            if (c == null) return false;
            v = (KEY_SHORTCUTS.equals(key) || Settings.Secure.getInt(c.getContentResolver(), key, 0) == 1) && PatronGate.ok(c);
        } catch (Throwable ignored) { return false; }
        synchronized (sOn) { sOn.put(key, v); }
        return v;
    }

    static boolean opt(String key) { return on(key); }
    static View imeDecor() {
        try {
            InputMethodService ims = sIme.get();
            android.app.Dialog w = ims == null ? null : ims.getWindow();
            return w == null || w.getWindow() == null ? null : w.getWindow().peekDecorView();
        } catch (Throwable t) { return null; }
    }
    private static volatile Boolean sPremium;
    private static long sPremiumAt;
    static boolean premium() {
        long now = android.os.SystemClock.uptimeMillis();
        Boolean v = sPremium;
        if (v != null && now - sPremiumAt < 30000) return v;
        boolean ok = false;
        try { Context c = app(); ok = c != null && PatronGate.ok(c); } catch (Throwable ignored) { }
        sPremium = ok; sPremiumAt = now;
        return ok;
    }
    static String gboardClass(Context c, String what, String literal) throws Throwable { return Shortcuts.cachedClass(c, what, literal); }

    static void confirm(View anchor, String title, String message, String yes, final Runnable then, Object holder) {
        try {
            InputMethodService ims = sIme.get();
            if (ims == null || anchor == null || anchor.getWindowToken() == null) return;
            boolean dark = sDarkKeys != null ? sDarkKeys : (ims.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES;
            Context c = new android.view.ContextThemeWrapper(ims, dark ? android.R.style.Theme_DeviceDefault_Dialog_Alert : android.R.style.Theme_DeviceDefault_Light_Dialog_Alert);
            android.app.AlertDialog d = new android.app.AlertDialog.Builder(c)
                    .setTitle(I18n.cs(title)).setMessage(I18n.cs(message))
                    .setNegativeButton(I18n.cs("Cancel"), null)
                    .setPositiveButton(I18n.cs(yes), new android.content.DialogInterface.OnClickListener() {
                        @Override public void onClick(android.content.DialogInterface x, int w) { try { then.run(); } catch (Throwable ignored) { } }
                    }).create();
            android.view.Window w = d.getWindow();
            if (w != null) {
                android.view.WindowManager.LayoutParams lp = w.getAttributes();
                lp.token = anchor.getWindowToken();
                lp.type = android.view.WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG;
                w.setAttributes(lp);
                w.addFlags(android.view.WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
            }
            d.show();
            boolean black = dark && superDark(ims);
            float dp = ims.getResources().getDisplayMetrics().density;
            int bg = dark ? (black ? 0xFF1C1C1E : 0xFF2C2C2E) : 0xFFF2F2F7;
            int fg = dark ? 0xFFFFFFFF : 0xFF000000;
            int sub = dark ? 0xFFAEAEB2 : 0xFF3C3C43;
            boolean styled = false;
            if (w != null && (SurfaceStyle.restyled(d.getContext()) || black)) {
                try { FrostedSurface.compact(d); FrostedSurface.style(d); styled = true; } catch (Throwable ignored) { }
            }
            if (w != null && !styled) {
                android.graphics.drawable.GradientDrawable shape = new android.graphics.drawable.GradientDrawable();
                shape.setColor(bg);
                shape.setCornerRadius(28 * dp);
                w.setBackgroundDrawable(new android.graphics.drawable.InsetDrawable(shape, (int) (16 * dp)));
            }
            android.widget.Button no = d.getButton(android.content.DialogInterface.BUTTON_NEGATIVE);
            android.widget.Button ok = d.getButton(android.content.DialogInterface.BUTTON_POSITIVE);
            if (no != null) { no.setTextColor(dark ? 0xFF0A84FF : 0xFF007AFF); no.setAllCaps(false); }
            if (ok != null) { ok.setTextColor(dark ? 0xFFFF453A : 0xFFFF3B30); ok.setAllCaps(false); }
            int titleId = ims.getResources().getIdentifier("alertTitle", "id", "android");
            View t = titleId == 0 ? null : d.findViewById(titleId);
            if (t instanceof TextView) ((TextView) t).setTextColor(fg);
            View msg = d.findViewById(android.R.id.message);
            if (msg instanceof TextView) ((TextView) msg).setTextColor(sub);
        } catch (Throwable ignored) { }
    }

    private static Context app() {
        InputMethodService ims = sIme.get();
        if (ims != null) return ims.getApplicationContext();
        try { return (Context) Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null); } catch (Throwable t) { return null; }
    }

    static String barChoice() {
        try {
            Context c = app();
            if (c == null) return null;
            String v = Settings.Secure.getString(c.getContentResolver(), KEY_BAR);
            if (v == null || v.indexOf(':') <= 0 || !PatronGate.ok(c)) return null;
            return v;
        } catch (Throwable t) { return null; }
    }

    private static boolean superDark(Context c) {
        try { return Settings.Secure.getInt(c.getContentResolver(), SuperDark.KEY, 0) == 1; } catch (Throwable t) { return false; }
    }

    private static boolean sLateHooks;

    private static void preparePowerKey(final InputMethodService ims, ClassLoader cl) throws Throwable {
        String name = gboardClass(ims, "powerkey", "com/google/android/libraries/inputmethod/accesspoint/impl/PowerKeyAccessPointHolderController");
        if (name == null) return;
        Class<?> k = cl.loadClass(name);
        for (Method m : k.getDeclaredMethods()) {
            Class<?>[] t = m.getParameterTypes();
            Class<?> r = m.getReturnType();
            boolean st = Modifier.isStatic(m.getModifiers());
            if (st && r == String.class && t.length == 4 && t[0] == Context.class && t[2] == boolean.class) {
                Xp.hookMethod(m, new Xp.Callback() {
                    @Override protected void afterHookedMethod(Xp.Param p) { if (emojiButton()) p.setResult("smiley"); }
                });
            } else if (!st && r == void.class && t.length == 1 && !t[0].isPrimitive() && !View.class.isAssignableFrom(t[0]) && t[0] != String.class && !t[0].getName().startsWith("java")) {
                Xp.hookMethod(m, new Xp.Callback() {
                    @Override protected void beforeHookedMethod(Xp.Param p) { if (emojiButton()) p.setResult(null); }
                });
            } else if (!st && t.length == 1 && t[0] == String.class && r != String.class && r != void.class && !r.isPrimitive()) {
                Xp.hookMethod(m, new Xp.Callback() {
                    @Override protected void beforeHookedMethod(Xp.Param p) { if (emojiButton()) p.setResult(null); }
                });
            } else if (!st && t.length == 0 && java.util.List.class.isAssignableFrom(r)) {
                Xp.hookMethod(m, new Xp.Callback() {
                    @Override protected void beforeHookedMethod(Xp.Param p) { if (emojiButton()) p.setResult(java.util.Collections.emptyList()); }
                });
            }
        }
    }

    private static boolean emojiButton() { return premium() && !on(KEY_HIDE_RIGHT); }

    private static boolean sClipsKept;

    private static void installIme(final ClassLoader cl) {
        Xp.hookAllMethods(InputMethodService.class, "onCreate", new GboardMain.Kept() {
            @Override protected void beforeHookedMethod(Xp.Param p) {
                if (!(p.thisObject instanceof InputMethodService)) return;
                if (GboardMain.off()) {
                    final InputMethodService kept = (InputMethodService) p.thisObject;
                    if (!sClipsKept && GboardMain.turnedOff() && PatronGate.ok(kept)) {
                        sClipsKept = true;
                        new Thread(new Runnable() { public void run() {
                            try { GboardClipAll.prepare(kept, cl); } catch (Throwable t) { Log.w("clip unlimited", t); }
                        }}, "PixelSuite-Gboard").start();
                    }
                    return;
                }
                sIme = new WeakReference<InputMethodService>((InputMethodService) p.thisObject);
                sCustomizing = false; sCustAt = 0;
                try { if (on(KEY_SHORTCUTS) || barChoice() != null) Shortcuts.prepare((InputMethodService) p.thisObject, cl); } catch (Throwable t) { Log.w("shortcuts", t); }
                final InputMethodService ims = (InputMethodService) p.thisObject;
                if (!sDictationReady && on(KEY_DICTATION) && PatronGate.ok(ims) && cached(ims, "jetsonflag", "jetsonreader1", "jetsonreader2", "jetsonreader3", "accessorygate", "agentic")) {
                    try { prepareDictationFlag(ims, cl); } catch (Throwable t) { android.util.Log.w(VOICE_TAG, "toolbar dictation at start: " + t); }
                }
                if (!sLateHooks && PatronGate.ok(ims)) {
                    sLateHooks = true;
                    new Thread(new Runnable() { public void run() {
                        try { prepareRowsClicks(ims, cl); } catch (Throwable t) { Log.w("toolbar taps", t); }
                        try { prepareArabic(cl); } catch (Throwable t) { Log.w("arabic", t); }
                        try { prepareChips(ims, cl); } catch (Throwable t) { Log.w("undo chip", t); }
                        try { preparePopups(ims, cl); } catch (Throwable t) { Log.w("key pop-ups", t); }
                        try { prepareResizeBounds(ims, cl); } catch (Throwable t) { Log.w("resize bounds", t); }
                        try { prepareNga(ims, cl); } catch (Throwable t) { Log.w("voice nga", t); }
                        try { prepareRambler(ims, cl); } catch (Throwable t) { Log.w("voice rambler", t); }
                        try { prepareClassicVoice(ims, cl); } catch (Throwable t) { Log.w("classic voice", t); }
                        try { prepareDictationFlag(ims, cl); } catch (Throwable t) { android.util.Log.w(VOICE_TAG, "toolbar dictation: " + t); }
                        try { preparePowerKey(ims, cl); } catch (Throwable t) { Log.w("emoji button", t); }
                        try { prepareShowing(ims, cl); } catch (Throwable t) { Log.w("toolbar showing", t); }
                        try { prepareClipCopies(ims, cl); } catch (Throwable t) { Log.w("clip copies", t); }
                        try { prepareClipIcons(ims, cl); } catch (Throwable t) { Log.w("clip icons", t); }
                        try { prepareClipConfirm(cl); } catch (Throwable t) { Log.w("clip confirm", t); }
                        try { prepareArabicAutocorrect(cl); } catch (Throwable t) { Log.w("arabic autocorrect", t); }
                        try { GboardClipAll.prepare(ims, cl); } catch (Throwable t) { Log.w("clip unlimited", t); }
                        try { prepareClipLift(); } catch (Throwable t) { Log.w("clip lift", t); }
                        try { GboardAutoCorrect.start(ims); } catch (Throwable t) { Log.w("auto-correction", t); }
                        try { GboardToasts.prepare(ims, cl); } catch (Throwable t) { Log.w("toasts", t); }
                    }}, "PixelSuite-Gboard").start();
                }
            }
        });
    }

    private static void prepareArabicAutocorrect(ClassLoader cl) throws Throwable {
        final Class<?> ime;
        try { ime = cl.loadClass("com.google.android.apps.inputmethod.libs.latin5.LatinIme"); } catch (Throwable t) { return; }
        Field loc = null;
        for (Field f : ime.getDeclaredFields()) if (f.getType() == java.util.Locale.class && !Modifier.isStatic(f.getModifiers())) { loc = f; break; }
        if (loc == null) return;
        loc.setAccessible(true);
        final Field locale = loc;
        for (Method m : ime.getDeclaredMethods()) {
            Class<?>[] t = m.getParameterTypes();
            if (Modifier.isStatic(m.getModifiers()) || t.length != 2 || t[0] != android.view.inputmethod.EditorInfo.class || !t[1].isEnum()) continue;
            Class<?> r = m.getReturnType();
            if (r.isPrimitive() || r == Void.TYPE || r.getName().startsWith("java")) continue;
            Field d = null, b = null;
            try { d = r.getDeclaredField("d"); b = r.getDeclaredField("b"); } catch (NoSuchFieldException e) { continue; }
            if (d.getType() != boolean.class || b.getType() != int.class) continue;
            d.setAccessible(true); b.setAccessible(true);
            final Field flag = d, bits = b;
            Xp.hookMethod(m, new Xp.Callback() {
                @Override protected void afterHookedMethod(Xp.Param p) {
                    try {
                        Object params = p.getResult();
                        if (params == null || !premium()) return;
                        Object mode = p.args[1];
                        if (!(mode instanceof Enum) || !"DM_VIRTUAL_KEYBOARD".equals(((Enum<?>) mode).name())) return;
                        Object l = locale.get(p.thisObject);
                        if (!(l instanceof java.util.Locale) || !GboardAutoCorrect.offFor(app(), (java.util.Locale) l)) return;
                        if ((bits.getInt(params) & 1) == 0 || !flag.getBoolean(params)) return;
                        flag.setBoolean(params, false);
                    } catch (Throwable ignored) { }
                }
            });
            return;
        }
    }

    private static void installValidIds() {
        Xp.hookAllMethods(Resources.class, "getStringArray", new Xp.Callback() {
            @Override protected void afterHookedMethod(Xp.Param p) {
                try {
                    if (!(p.getResult() instanceof String[])) return;
                    String[] a = (String[]) p.getResult();
                    if (a.length < 5 || a.length > 200) return;
                    List<String> l = Arrays.asList(a);
                    if (!l.contains("textediting") || !l.contains("settings") || !l.contains("clipboard")) return;
                    Catalog.ids(a);
                    if (l.contains(Shortcuts.IDS[0]) || !on(KEY_SHORTCUTS)) return;
                    String[] out = Arrays.copyOf(a, a.length + Shortcuts.IDS.length);
                    System.arraycopy(Shortcuts.IDS, 0, out, a.length, Shortcuts.IDS.length);
                    p.setResult(out);
                } catch (Throwable ignored) { }
            }
        });
    }

    static final class Shortcuts {
        static final String[] IDS = { "pixelsuite_undo", "pixelsuite_redo", "pixelsuite_select", "pixelsuite_select_all", "pixelsuite_cut", "pixelsuite_copy", "pixelsuite_paste" };
        static final String[] LABELS = { "Undo", "Redo", "Quick Select", "Select all", "Cut", "Copy", "Paste" };
        private static boolean sHooked;
        private static Class<?> sDef;
        private static final Map<Object, Boolean> sSeeded = new WeakHashMap<Object, Boolean>();
        private static final ThreadLocal<Boolean> ADDING = new ThreadLocal<Boolean>();
        private static Object[] sDefs;

        static synchronized void prepare(InputMethodService ims, ClassLoader cl) throws Throwable {
            if (sHooked) return;
            sHooked = true;
            sDef = defClass(cl);
            if (sDef == null) { return; }
            String holderName = cachedClass(ims, "holder", HOLDER_STRING);
            if (holderName == null) { return; }
            Class<?> holder = cl.loadClass(holderName);
            for (final Method m : holder.getDeclaredMethods()) {
                Class<?>[] t = m.getParameterTypes();
                if (t.length != 2 || t[0] != sDef || t[1] != boolean.class || m.getReturnType() != void.class || Modifier.isStatic(m.getModifiers())) continue;
                Xp.hookMethod(m, new Xp.Callback() {
                    @Override protected void afterHookedMethod(Xp.Param p) {
                        try {
                            if (Boolean.TRUE.equals(ADDING.get()) || p.args[0] == null) return;
                            Catalog.def(p.args[0]);
                            if (!"settings".equals(id(p.args[0]))) return;
                            add(m, p.thisObject, (Boolean) p.args[1]);
                        } catch (Throwable t) { Log.w("shortcuts add", t); }
                    }
                });
            }
        }

        private static Class<?> defClass(ClassLoader cl) throws Throwable {
            Class<?> bar = cl.loadClass(BAR);
            for (Method m : bar.getDeclaredMethods()) {
                Class<?>[] t = m.getParameterTypes();
                Class<?> r = m.getReturnType();
                if (t.length == 1 && t[0] == int.class && !r.isPrimitive() && !View.class.isAssignableFrom(r) && !r.getName().startsWith("java")) return r;
            }
            return null;
        }

        private static String cachedClass(Context c, String what, String literal) throws Throwable {
            long ver = c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionCode;
            File f = new File(c.getCacheDir(), "pixelsuite_gboard_" + what + "_" + ver);
            if (f.isFile()) {
                byte[] b = new byte[(int) f.length()];
                FileInputStream in = new FileInputStream(f);
                try { in.read(b); } finally { in.close(); }
                String s = new String(b, "UTF-8").trim();
                if (!s.isEmpty()) return s;
            }
            String name = DexPeek.classNameWithString(c, literal);
            if (name != null) {
                FileOutputStream out = new FileOutputStream(f);
                try { out.write(name.getBytes("UTF-8")); } finally { out.close(); }
            }
            return name;
        }

        private static String id(Object def) {
            for (Class<?> k = def.getClass(); k != null && k != Object.class; k = k.getSuperclass()) {
                for (Field f : k.getDeclaredFields()) {
                    if (f.getType() != String.class || Modifier.isStatic(f.getModifiers())) continue;
                    try { f.setAccessible(true); Object v = f.get(def); if ("settings".equals(v)) return "settings"; } catch (Throwable ignored) { }
                }
            }
            return null;
        }

        private static void add(Method add, final Object holder, boolean flag) throws Throwable {
            synchronized (sSeeded) { if (sSeeded.containsKey(holder)) return; sSeeded.put(holder, Boolean.TRUE); }
            if (on(KEY_SHORTCUTS)) {
                Object[] defs = defs();
                if (defs != null) {
                    ADDING.set(Boolean.TRUE);
                    try {
                        for (Object d : defs) add.invoke(holder, d, flag);
                    } finally { ADDING.set(Boolean.FALSE); }
                }
            }
            MAIN.post(new Runnable() { public void run() { try { applyOrder(holder); } catch (Throwable t) { Log.w("toolbar order", t); } } });
        }

        private static boolean sOrderChecked;

        static void applyOrder(Object holder) throws Throwable {
            String choice = barChoice();
            InputMethodService ims = sIme.get();
            if (choice == null || ims == null) return;
            String stamp = choice.substring(0, choice.indexOf(':'));
            File done = new File(ims.getCacheDir(), "pixelsuite_gboard_bar_applied");
            boolean same = false;
            if (done.isFile()) {
                byte[] b = new byte[(int) done.length()];
                FileInputStream in = new FileInputStream(done);
                try { in.read(b); } finally { in.close(); }
                same = stamp.equals(new String(b, "UTF-8").trim());
            }
            if (same && sOrderChecked) return;
            sOrderChecked = true;
            List<String> ids = new ArrayList<String>();
            for (String id : choice.substring(choice.indexOf(':') + 1).split(",")) if (!id.trim().isEmpty() && !ids.contains(id.trim())) ids.add(id.trim());
            if (ids.isEmpty()) return;
            String helperName = cachedClass(ims, "order", "storeAccessPointsCountOnBar");
            if (helperName == null) { return; }
            Class<?> helper = holder.getClass().getClassLoader().loadClass(helperName);
            Object keeper = null;
            for (Class<?> k = holder.getClass(); k != null && keeper == null && k != Object.class; k = k.getSuperclass())
                for (Field f : k.getDeclaredFields()) if (f.getType() == helper && !Modifier.isStatic(f.getModifiers())) { f.setAccessible(true); keeper = f.get(holder); if (keeper != null) break; }
            if (keeper == null) { return; }
            Method setOrder = null, setCount = null, current = null;
            for (Method m : helper.getDeclaredMethods()) {
                Class<?>[] t = m.getParameterTypes();
                if (Modifier.isStatic(m.getModifiers())) continue;
                if (t.length == 1 && t[0] == List.class && m.getReturnType() == void.class) setOrder = m;
                else if (t.length == 1 && t[0] == int.class && m.getReturnType() == void.class) setCount = m;
                else if (t.length == 0 && List.class.isAssignableFrom(m.getReturnType())) current = m;
            }
            if (setOrder == null || setCount == null) { return; }
            ArrayList<Object> order = new ArrayList<Object>(ids);
            Object cur = null;
            ArrayList<Object> rest = new ArrayList<Object>();
            if (current != null) { current.setAccessible(true); cur = current.invoke(keeper); if (cur instanceof List) for (Object o : (List<?>) cur) if (!order.contains(o) && !rest.contains(o)) rest.add(o); }
            for (String id : IDS) if (!order.contains(id) && !rest.contains(id)) rest.add(id);
            File grouped = new File(ims.getCacheDir(), "pixelsuite_gboard_bar_grouped");
            String groupStamp = stamp + ":1";
            boolean group = false;
            if (grouped.isFile()) {
                byte[] g = new byte[(int) grouped.length()];
                FileInputStream gin = new FileInputStream(grouped);
                try { gin.read(g); } finally { gin.close(); }
                group = !groupStamp.equals(new String(g, "UTF-8").trim());
            } else group = true;
            if (group) {
                List<String> named = new ArrayList<String>();
                List<Object> other = new ArrayList<Object>();
                for (Object o : rest) if (o instanceof String) named.add((String) o); else other.add(o);
                rest.clear();
                rest.addAll(GboardBarEditor.organized(named));
                rest.addAll(other);
            }
            order.addAll(rest);
            setOrder.setAccessible(true); setCount.setAccessible(true);
            setOrder.invoke(keeper, order);
            setCount.invoke(keeper, Math.min(ids.size(), GboardToolbar.MAX));
            FileOutputStream out = new FileOutputStream(done);
            try { out.write(stamp.getBytes("UTF-8")); } finally { out.close(); }
            if (group) {
                FileOutputStream gout = new FileOutputStream(grouped);
                try { gout.write(groupStamp.getBytes("UTF-8")); } finally { gout.close(); }
            }
        }

        private static synchronized Object[] defs() throws Throwable {
            if (sDefs != null) return sDefs;
            Method factory = null;
            for (Method m : sDef.getDeclaredMethods()) {
                if (Modifier.isStatic(m.getModifiers()) && m.getParameterTypes().length == 0 && m.getReturnType() != sDef && !m.getReturnType().isPrimitive() && !m.getReturnType().getName().startsWith("java")) { factory = m; break; }
            }
            if (factory == null) { return null; }
            factory.setAccessible(true);
            Class<?> b = factory.getReturnType();
            Method setId = null, setRun = null, build = null;
            for (Method m : b.getDeclaredMethods()) {
                Class<?>[] t = m.getParameterTypes();
                if (t.length == 1 && t[0] == String.class && m.getReturnType() == void.class) setId = m;
                else if (t.length == 1 && t[0] == Runnable.class) setRun = m;
                else if (t.length == 0 && m.getReturnType() == sDef) build = m;
            }
            if (setId == null || setRun == null || build == null) { return null; }
            Context c = app();
            Object[] out = new Object[IDS.length];
            for (int i = 0; i < IDS.length; i++) {
                Object builder = factory.invoke(null);
                setId.invoke(builder, IDS[i]);
                Field idField = null;
                List<Field> strings = new ArrayList<Field>();
                Field icon = null;
                for (Field f : b.getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers())) continue;
                    f.setAccessible(true);
                    if (f.getType() == String.class) { if (IDS[i].equals(f.get(builder))) idField = f; else strings.add(f); }
                    else if (f.getType() == Icon.class) icon = f;
                }
                if (idField == null || icon == null || strings.isEmpty()) { return null; }
                strings.get(0).set(builder, LABELS[i]);
                icon.set(builder, Icon.createWithBitmap(Glyph.draw(c, i)));
                final int which = i;
                setRun.invoke(builder, new Runnable() { public void run() { act(which); } });
                out[i] = build.invoke(builder);
            }
            sDefs = out;
            return out;
        }

        static void act(int which) {
            try {
                InputMethodService ims = sIme.get();
                InputConnection ic = ims == null ? null : ims.getCurrentInputConnection();
                if (ic == null) return;
                switch (which) {
                    case 0: keys(ic, KeyEvent.KEYCODE_Z, KeyEvent.META_CTRL_ON | KeyEvent.META_CTRL_LEFT_ON); break;
                    case 1: keys(ic, KeyEvent.KEYCODE_Z, KeyEvent.META_CTRL_ON | KeyEvent.META_CTRL_LEFT_ON | KeyEvent.META_SHIFT_ON | KeyEvent.META_SHIFT_LEFT_ON); break;
                    case 2: { int[] w = quickSelect(ic); if (w != null && !GboardSelectMenu.select(ims, ic, w[0], w[1], w[2], false)) ic.setSelection(w[0], w[1]); break; }
                    case 3: { int[] all = selectAll(ic); if (all != null && !GboardSelectMenu.select(ims, ic, all[0], all[1], all[2], true)) ic.setSelection(all[0], all[1]); break; }
                    case 4: copy(ims, ic, true); break;
                    case 5: copy(ims, ic, false); break;
                    case 6: paste(ims, ic); break;
                }
            } catch (Throwable t) { Log.w("shortcut action", t); }
        }

        private static int len(CharSequence c) { return c == null ? 0 : c.length(); }

        private static int[] selectAll(InputConnection ic) {
            int before = len(ic.getTextBeforeCursor(1 << 16, 0)), sel = len(ic.getSelectedText(0)), after = len(ic.getTextAfterCursor(1 << 16, 0));
            int total = before + sel + after;
            ExtractedText et = ic.getExtractedText(new ExtractedTextRequest(), 0);
            if (et != null && et.text != null && et.startOffset == 0) total = Math.max(total, et.text.length());
            return total > 0 ? new int[] { 0, total, before } : null;
        }

        private static void copy(InputMethodService ims, InputConnection ic, boolean cut) {
            CharSequence sel = ic.getSelectedText(0);
            if (sel == null || sel.length() == 0) return;
            android.content.ClipboardManager cm = (android.content.ClipboardManager) ims.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) cm.setPrimaryClip(android.content.ClipData.newPlainText("text", sel.toString()));
            if (cut) ic.commitText("", 1);
        }

        private static void paste(InputMethodService ims, InputConnection ic) {
            android.content.ClipboardManager cm = (android.content.ClipboardManager) ims.getSystemService(Context.CLIPBOARD_SERVICE);
            android.content.ClipData clip = cm == null ? null : cm.getPrimaryClip();
            CharSequence t = clip == null || clip.getItemCount() == 0 ? null : clip.getItemAt(0).coerceToText(ims);
            if (t != null && t.length() > 0) ic.commitText(t, 1);
            else ic.performContextMenuAction(android.R.id.paste);
        }

        private static boolean menu(InputConnection ic, String name) {
            try {
                int id = android.R.id.class.getField(name).getInt(null);
                return ic.performContextMenuAction(id);
            } catch (Throwable t) { return false; }
        }

        private static void keys(InputConnection ic, int code, int meta) {
            long now = android.os.SystemClock.uptimeMillis();
            ic.sendKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0, meta));
            ic.sendKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0, meta));
        }

        private static int sQsFrom = -1, sQsTo = -1, sQsCaret = -1;

        static int[] quickSelect(InputConnection ic) {
            ExtractedText et = ic.getExtractedText(new ExtractedTextRequest(), 0);
            CharSequence sel = ic.getSelectedText(0);
            boolean has = (sel != null && sel.length() > 0) || (et != null && et.text != null && et.selectionStart != et.selectionEnd);
            if (has) {
                int from, to;
                if (et != null && et.text != null && et.selectionStart != et.selectionEnd) {
                    from = Math.min(et.selectionStart, et.selectionEnd) + et.startOffset;
                    to = Math.max(et.selectionStart, et.selectionEnd) + et.startOffset;
                } else {
                    from = len(ic.getTextBeforeCursor(1 << 16, 0));
                    to = from + len(sel);
                }
                int caret = from == sQsFrom && to == sQsTo && sQsCaret >= 0 ? sQsCaret : to;
                sQsFrom = sQsTo = sQsCaret = -1;
                ic.setSelection(caret, caret);
                return null;
            }
            if (et == null || et.text == null) return null;
            int caret = Math.max(0, Math.min(et.selectionStart, et.selectionEnd)) + et.startOffset;
            int[] w = selectWord(ic, et);
            if (w != null && w[0] != w[1]) { sQsFrom = w[0]; sQsTo = w[1]; sQsCaret = caret; }
            else sQsFrom = sQsTo = sQsCaret = -1;
            return w != null && w[0] != w[1] ? new int[] { w[0], w[1], caret } : null;
        }

        private static int[] selectWord(InputConnection ic, ExtractedText et) {
            if (et == null || et.text == null) return null;
            CharSequence t = et.text;
            int s = Math.max(0, Math.min(et.selectionStart, et.selectionEnd)), e = Math.min(t.length(), Math.max(et.selectionStart, et.selectionEnd));
            while (s > 0 && word(t.charAt(s - 1))) s--;
            while (e < t.length() && word(t.charAt(e))) e++;
            if (s == e) {
                int b = s;
                while (b > 0 && !word(t.charAt(b - 1)) && t.charAt(b - 1) != '\n') b--;
                if (b > 0 && word(t.charAt(b - 1))) {
                    e = b; s = b;
                    while (s > 0 && word(t.charAt(s - 1))) s--;
                } else {
                    while (e < t.length() && !word(t.charAt(e))) e++;
                    s = e;
                    while (e < t.length() && word(t.charAt(e))) e++;
                }
            }
            if (s == e) return null;
            return new int[] { s + et.startOffset, e + et.startOffset };
        }

        private static boolean word(char c) {
            if (Character.isLetterOrDigit(c)) return true;
            int type = Character.getType(c);
            return type == Character.NON_SPACING_MARK || type == Character.COMBINING_SPACING_MARK || type == Character.ENCLOSING_MARK || c == '\'' || c == '\u2019' || c == '_';
        }
    }

    static final class Catalog {
        private static final List<String> IDS = new ArrayList<String>();
        private static final Map<String, String> NAMES = new HashMap<String, String>();
        private static String sWritten;

        static synchronized void ids(String[] a) {
            boolean changed = false;
            for (String id : a) if (id != null && !id.startsWith("pixelsuite_") && !IDS.contains(id)) { IDS.add(id); changed = true; }
            if (changed) write();
        }

        static synchronized void def(Object def) {
            String id = null, name = null;
            for (Class<?> k = def.getClass(); k != null && k != Object.class; k = k.getSuperclass())
                for (Field f : k.getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers()) || f.getType() != String.class) continue;
                    try {
                        f.setAccessible(true);
                        Object v = f.get(def);
                        if (!(v instanceof String) || ((String) v).isEmpty()) continue;
                        String sv = (String) v;
                        if (id == null && sv.matches("[a-z0-9_]+")) id = sv;
                        else if (name == null && !sv.matches("[a-z0-9_]+")) name = sv;
                    } catch (Throwable ignored) { }
                }
            if (id == null || name == null || id.startsWith("pixelsuite_") || name.equals(NAMES.get(id))) return;
            NAMES.put(id, name);
            write();
        }

        private static void write() {
            try {
                InputMethodService ims = sIme.get();
                if (ims == null || IDS.isEmpty()) return;
                StringBuilder b = new StringBuilder();
                for (String id : IDS) { String n = NAMES.get(id); b.append(id).append('\t').append(n == null ? "" : n.replace('\n', ' ').replace('\t', ' ')).append('\n'); }
                String out = b.toString();
                if (out.equals(sWritten)) return;
                sWritten = out;
                File f = new File(ims.getCacheDir(), "pixelsuite_gboard_ids");
                FileOutputStream o = new FileOutputStream(f);
                try { o.write(out.getBytes("UTF-8")); } finally { o.close(); }
            } catch (Throwable ignored) { }
        }
    }

    static final class Glyph {
        static Bitmap draw(Context c, int which) {
            float d = c == null ? 3f : c.getResources().getDisplayMetrics().density;
            int size = Math.round(24 * d);
            Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas cv = new Canvas(b);
            cv.scale(size / 24f, size / 24f);
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(Color.WHITE);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2f);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
            Path path = new Path();
            switch (which) {
                case 0: case 1: {
                    boolean redo = which == 1;
                    if (redo) { cv.save(); cv.scale(-1, 1, 12, 12); }
                    path.moveTo(9, 7); path.lineTo(5, 11); path.lineTo(9, 15);
                    cv.drawPath(path, p);
                    path.reset();
                    path.moveTo(5, 11); path.lineTo(14, 11);
                    path.cubicTo(17.5f, 11, 20, 13.5f, 20, 16); path.cubicTo(20, 18.5f, 17.5f, 20, 15, 20); path.lineTo(11, 20);
                    cv.drawPath(path, p);
                    if (redo) cv.restore();
                    break;
                }
                case 2: {
                    path.moveTo(4, 7); path.lineTo(4, 4); path.lineTo(7, 4);
                    path.moveTo(17, 4); path.lineTo(20, 4); path.lineTo(20, 7);
                    path.moveTo(20, 17); path.lineTo(20, 20); path.lineTo(17, 20);
                    path.moveTo(7, 20); path.lineTo(4, 20); path.lineTo(4, 17);
                    path.moveTo(10.5f, 8.5f); path.lineTo(13.5f, 8.5f); path.moveTo(12, 8.5f); path.lineTo(12, 15.5f); path.moveTo(10.5f, 15.5f); path.lineTo(13.5f, 15.5f);
                    cv.drawPath(path, p);
                    p.setStyle(Paint.Style.FILL);
                    cv.drawCircle(8.6f, 12, 1.25f, p); cv.drawCircle(15.4f, 12, 1.25f, p);
                    break;
                }
                case 3: {
                    float[][] seg = { {4, 7, 4, 4, 7, 4}, {17, 4, 20, 4, 20, 7}, {20, 17, 20, 20, 17, 20}, {7, 20, 4, 20, 4, 17} };
                    for (float[] g : seg) { path.moveTo(g[0], g[1]); path.lineTo(g[2], g[3]); path.lineTo(g[4], g[5]); }
                    path.moveTo(10, 4); path.lineTo(14, 4); path.moveTo(20, 10); path.lineTo(20, 14); path.moveTo(10, 20); path.lineTo(14, 20); path.moveTo(4, 10); path.lineTo(4, 14);
                    cv.drawPath(path, p);
                    break;
                }
                case 4: {
                    cv.drawCircle(6.5f, 17.5f, 2.6f, p); cv.drawCircle(17.5f, 17.5f, 2.6f, p);
                    path.moveTo(8.4f, 15.6f); path.lineTo(18, 4); path.moveTo(15.6f, 15.6f); path.lineTo(6, 4);
                    cv.drawPath(path, p);
                    break;
                }
                case 5: {
                    cv.drawRoundRect(new RectF(8, 8, 20, 20), 2, 2, p);
                    path.moveTo(5, 16); path.lineTo(5, 6); path.cubicTo(5, 4.9f, 5.9f, 4, 7, 4); path.lineTo(16, 4);
                    cv.drawPath(path, p);
                    break;
                }
                default: {
                    cv.drawRoundRect(new RectF(5, 5, 19, 21), 2, 2, p);
                    cv.drawRoundRect(new RectF(9, 3, 15, 7), 1.2f, 1.2f, p);
                    break;
                }
            }
            return b;
        }
    }

    private static final Pattern KEY_ID = Pattern.compile("^([A-F]\\d\\d|key_pos_(shift|del|delete|space|ime_action|enter|switch_to_[a-z_]+|comma|period|emoji|main_panel_left_fun|back_to_prime))$");
    private static final Map<Integer, Boolean> sKeyIds = new HashMap<Integer, Boolean>();
    private static final Map<String, float[]> sTwoPart = new HashMap<String, float[]>();

    private static void installKeys(ClassLoader cl) throws Throwable {
        final Class<?> key = cl.loadClass(SOFT_KEY);
        Xp.hookAllMethods(key, "onFinishInflate", new Xp.Callback() {
            @Override protected void afterHookedMethod(Xp.Param p) {
                try {
                    View v = (View) p.thisObject;
                    if (on(KEY_KEYS) && styled(v)) {
                        v.setBackground(faceFor(v));
                        if (actionKey(v)) clearAction(v);
                    }
                } catch (Throwable ignored) { }
            }
        });
        sKeyClass = key;
        Xp.hookAllMethods(View.class, "setBackgroundDrawable", new Xp.Callback() {
            @Override protected void beforeHookedMethod(Xp.Param p) {
                try {
                    if (p.args.length != 1 || p.args[0] instanceof FaceDrawable) return;
                    if (!key.isInstance(p.thisObject)) {
                        if (p.args[0] instanceof PanelDrawable) return;
                        PanelDrawable panel;
                        synchronized (sPanels) { panel = sPanels.get(p.thisObject); }
                        if (panel != null && on(KEY_KEYS)) { p.args[0] = panel; return; }
                        if (p.args[0] != null && p.thisObject instanceof View && insideAction((View) p.thisObject) && on(KEY_KEYS)) p.args[0] = null;
                        return;
                    }
                    View v = (View) p.thisObject;
                    if (!on(KEY_KEYS) || !styled(v)) return;
                    p.args[0] = faceFor(v);
                } catch (Throwable ignored) { }
            }
        });
        Xp.hookAllConstructors(android.widget.ImageView.class, new Xp.Callback() {
            @Override protected void afterHookedMethod(Xp.Param p) {
                try { View v = (View) p.thisObject; if (actionCircle(v) && on(KEY_KEYS) && v.getVisibility() != View.INVISIBLE) v.setVisibility(View.INVISIBLE); } catch (Throwable ignored) { }
            }
        });
    }

    private static final Map<View, FaceDrawable> sFaceDrawables = new WeakHashMap<View, FaceDrawable>();
    private static final Map<View, Long> sPanelAreas = new WeakHashMap<View, Long>();
    private static final Map<View, PanelDrawable> sPanels = new WeakHashMap<View, PanelDrawable>();

    static void operatorPanel(View key) {
        View area = null;
        for (android.view.ViewParent p = key.getParent(); p instanceof View; p = p.getParent()) if ("input_area".equals(idName((View) p))) { area = (View) p; break; }
        if (area == null || area.getWidth() <= 0) return;
        long now = android.os.SystemClock.uptimeMillis();
        synchronized (sPanelAreas) { Long at = sPanelAreas.get(area); if (at != null && (at < 0 || now - at < 2000)) return; sPanelAreas.put(area, now); }
        final View list = findClass(area, "ReadingTextCandidateHolderView", 0);
        if (list == null || !(list.getParent() instanceof View)) return;
        synchronized (sPanelAreas) { sPanelAreas.put(area, -1L); }
        final View frame = (View) list.getParent();
        final PanelDrawable panel = new PanelDrawable(frame, list);
        synchronized (sPanels) { sPanels.put(frame, panel); }
        MAIN.post(new Runnable() { public void run() {
            try { if (list.getBackground() != null) list.setBackground(null); frame.setBackground(panel); } catch (Throwable ignored) { }
        } });
    }

    static final class PanelDrawable extends android.graphics.drawable.Drawable {
        final java.lang.ref.WeakReference<View> host, list;
        final Paint face = new Paint(Paint.ANTI_ALIAS_FLAG), shade = new Paint(Paint.ANTI_ALIAS_FLAG);
        final RectF r = new RectF();
        PanelDrawable(View host, View list) { this.host = new java.lang.ref.WeakReference<View>(host); this.list = new java.lang.ref.WeakReference<View>(list); }

        @Override public void draw(Canvas c) {
            View v = host.get();
            android.graphics.Rect b = getBounds();
            if (v == null || b.width() <= 0 || b.height() <= 0) return;
            float d = v.getResources().getDisplayMetrics().density;
            float last = lastRow(v, b.height());
            r.set(b.left + 3 * d, b.top + 4 * d, b.right - 3 * d, b.bottom - last * (1 - 0.812f));
            boolean dark = dark(v);
            boolean black = dark && superDark(v.getContext());
            float radius = 9f * d;
            if (!dark) {
                shade.setColor(0x59898A8D);
                c.drawRoundRect(r.left, r.top + 1.2f * d, r.right, r.bottom + 1.2f * d, radius, radius, shade);
            }
            face.setColor(dark ? (black ? 0xFF2C2C2E : 0xFF464648) : 0xFFFFFFFF);
            c.drawRoundRect(r, radius, radius, face);
        }

        private boolean dark(View v) {
            View l = list.get();
            TextView t = l == null ? null : text(l);
            if (t == null) return (v.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES;
            int col = t.getCurrentTextColor();
            return (Color.red(col) * 299 + Color.green(col) * 587 + Color.blue(col) * 114) / 1000 > 140;
        }

        private static float lastRow(View v, int h) {
            if (v.getParent() instanceof ViewGroup) {
                ViewGroup p = (ViewGroup) v.getParent();
                for (int i = 0; i < p.getChildCount(); i++) {
                    View s = p.getChildAt(i);
                    if (s == v || !(s instanceof ViewGroup) || Math.abs(s.getHeight() - h) > 2) continue;
                    ViewGroup rows = (ViewGroup) s;
                    if (rows.getChildCount() < 2) continue;
                    View row = rows.getChildAt(rows.getChildCount() - 1);
                    if (row.getHeight() > 0 && row.getHeight() < h) return row.getHeight();
                }
            }
            return h / 3f;
        }

        @Override public void setAlpha(int a) { }
        @Override public void setColorFilter(android.graphics.ColorFilter f) { }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }
    private static Class<?> sKeyClass;
    private static final Map<Integer, Boolean> sActionIds = new HashMap<Integer, Boolean>();
    private static volatile Boolean sDarkKeys;
    private static float sSymSize;
    private static android.graphics.Typeface sSymFace;

    static boolean actionCircle(View v) {
        Object t = v.getTag();
        if (!(t instanceof String)) return false;
        String s = (String) t;
        return s.startsWith(".background-icon.for-action-key") && !s.contains("for-preview");
    }

    static boolean switchBg(View v) {
        Object t = v.getTag();
        if (!(t instanceof String) || !((String) t).startsWith(".background-icon") || ((String) t).contains("for-preview")) return false;
        for (android.view.ViewParent p = v.getParent(); p instanceof View; p = p.getParent()) {
            View k = (View) p;
            if (k.getId() != View.NO_ID) {
                String n = "";
                try { n = k.getResources().getResourceEntryName(k.getId()); } catch (Throwable ignored) { }
                if (n.startsWith("key_pos_switch_to_symbol")) return true;
                if (n.startsWith("key_pos_")) return false;
            }
        }
        return false;
    }

    static boolean actionKey(View v) {
        int id = v.getId();
        if (id == View.NO_ID || id == 0) return false;
        Boolean known;
        synchronized (sActionIds) { known = sActionIds.get(id); }
        if (known == null) {
            String n = "";
            try { n = v.getResources().getResourceEntryName(id); } catch (Throwable ignored) { }
            known = "key_pos_ime_action".equals(n) || "key_pos_enter".equals(n);
            synchronized (sActionIds) { sActionIds.put(id, known); }
        }
        return known;
    }

    private static boolean insideAction(View v) {
        Class<?> k = sKeyClass;
        if (k == null) return false;
        android.view.ViewParent p = v.getParent();
        for (int i = 0; i < 5 && p instanceof View; i++, p = p.getParent())
            if (k.isInstance(p)) return actionKey((View) p);
        return false;
    }

    static boolean clearAction(View v) {
        if (!(v instanceof ViewGroup)) return false;
        List<View> bg = new ArrayList<View>();
        collect((ViewGroup) v, bg);
        for (View x : bg) try { x.setBackground(null); } catch (Throwable ignored) { }
        return !bg.isEmpty();
    }

    private static void collect(ViewGroup g, List<View> out) {
        for (int i = 0; i < g.getChildCount(); i++) {
            View ch = g.getChildAt(i);
            if (ch.getBackground() != null) out.add(ch);
            if (ch instanceof ViewGroup) collect((ViewGroup) ch, out);
        }
    }

    private static FaceDrawable faceFor(View v) {
        synchronized (sFaceDrawables) {
            FaceDrawable f = sFaceDrawables.get(v);
            if (f == null) { f = new FaceDrawable(v); sFaceDrawables.put(v, f); }
            return f;
        }
    }

    static final class FaceDrawable extends android.graphics.drawable.Drawable {
        final WeakReference<View> key;
        final KeyFace face = new KeyFace();
        FaceDrawable(View v) { key = new WeakReference<View>(v); }
        @Override public void draw(Canvas c) { View v = key.get(); if (v != null) try { face.draw(v, c); } catch (Throwable ignored) { } }
        @Override public void setAlpha(int a) { }
        @Override public void setColorFilter(android.graphics.ColorFilter f) { }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
        @Override public boolean isStateful() { return true; }
        @Override protected boolean onStateChange(int[] state) { invalidateSelf(); return true; }
    }

    private static void abcFace(View v) {
        if (!(v instanceof ViewGroup)) return;
        if (!abcLabel(v, 0) || otherLabel(v, 0)) {
            if (sAbcKeys.remove(v) != null && v.getBackground() instanceof FaceDrawable) { v.setBackground(sAbcOld.remove(v)); v.invalidate(); }
            return;
        }
        if (v.getBackground() instanceof FaceDrawable) { hideSwitchBg(v, 0); return; }
        sAbcKeys.put(v, Boolean.TRUE);
        sAbcOld.put(v, v.getBackground());
        v.setBackground(faceFor(v));
        hideSwitchBg(v, 0);
        v.invalidate();
    }

    private static final Map<View, Boolean> sAbcKeys = new WeakHashMap<View, Boolean>();
    private static final Map<View, android.graphics.drawable.Drawable> sAbcOld = new WeakHashMap<View, android.graphics.drawable.Drawable>();

    static boolean otherLabel(View v, int depth) {
        if (depth > 5 || v.getVisibility() != View.VISIBLE) return false;
        if (v instanceof TextView) { CharSequence c = ((TextView) v).getText(); String t = c == null ? "" : c.toString().trim(); return t.length() > 0 && !t.equalsIgnoreCase("ABC"); }
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) if (otherLabel(((ViewGroup) v).getChildAt(i), depth + 1)) return true;
        return false;
    }

    private static View findText(View v, String text) {
        if (v instanceof TextView) { CharSequence c = ((TextView) v).getText(); return c != null && c.toString().equals(text) ? v : null; }
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) { View f = findText(((ViewGroup) v).getChildAt(i), text); if (f != null) return f; }
        return null;
    }

    static boolean abcLabel(View v, int depth) {
        if (depth > 5) return false;
        if (v instanceof TextView) { CharSequence c = ((TextView) v).getText(); return c != null && c.toString().trim().equalsIgnoreCase("ABC"); }
        CharSequence cd = v.getContentDescription();
        if (cd != null && cd.toString().trim().equalsIgnoreCase("ABC")) return true;
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) if (abcLabel(((ViewGroup) v).getChildAt(i), depth + 1)) return true;
        return false;
    }

    private static void hideSwitchBg(View v, int depth) {
        if (depth > 5) return;
        if (v instanceof android.widget.ImageView) {
            Object tag = v.getTag();
            if (tag instanceof String && ((String) tag).startsWith(".background-icon") && !((String) tag).contains("for-preview") && v.getVisibility() == View.VISIBLE) v.setVisibility(View.INVISIBLE);
            return;
        }
        if (depth > 0 && v.getBackground() != null && !(v.getBackground() instanceof FaceDrawable) && v instanceof ViewGroup) v.setBackground(null);
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) hideSwitchBg(((ViewGroup) v).getChildAt(i), depth + 1);
    }

    private static boolean styled(View v) {
        int id = v.getId();
        if (id == View.NO_ID || id == 0) return false;
        if (v.getClass().getName().contains("AccessPoint")) return false;
        Boolean known;
        synchronized (sKeyIds) { known = sKeyIds.get(id); }
        if (known == null) {
            String name = "";
            try { name = v.getResources().getResourceEntryName(id); } catch (Throwable ignored) { }
            known = KEY_ID.matcher(name).matches();
            synchronized (sKeyIds) { sKeyIds.put(id, known); }
        }
        if (!known && (sAbcKeys.containsKey(v) || (abcLabel(v, 0) && !otherLabel(v, 0)))) return true;
        return known;
    }

    static final class KeyFace {
        final Paint face = new Paint(Paint.ANTI_ALIAS_FLAG), shade = new Paint(Paint.ANTI_ALIAS_FLAG);
        final RectF r = new RectF();

        int lastW = -1, lastH = -1, lastTop = Integer.MIN_VALUE, lastRowH = -1;
        View labelBox;

        private void align(View v) {
            final View box = labelBox;
            if (twoPart(v, box)) return;
            final android.widget.ImageView solo = soloIcon(v);
            if (solo != null) {
                View target = box != null && box != solo && inside(solo, box) ? box : solo;
                android.graphics.drawable.Drawable dr = solo.getDrawable();
                RectF b = new RectF(dr.getBounds());
                if (!b.isEmpty()) {
                    solo.getImageMatrix().mapRect(b);
                    float center = offset(v, solo)[1] + solo.getPaddingTop() + b.centerY();
                    final float dy = Math.round((r.top + r.bottom) / 2f - center);
                    final View t = target;
                    if (Math.abs(dy) <= v.getHeight() * 0.4f && Math.abs(t.getTranslationY() - dy) >= 1f)
                        MAIN.post(new Runnable() { public void run() { try { t.setTranslationY(dy); } catch (Throwable ignored) { } } });
                    return;
                }
            }
            if (box == null || box.getParent() == null) return;
            int[] at = offset(v, box);
            float center = at[1] + box.getHeight() / 2f;
            final float dy = Math.round((r.top + r.bottom) / 2f - center);
            if (Math.abs(box.getTranslationY() - dy) < 1f) return;
            MAIN.post(new Runnable() { public void run() { try { box.setTranslationY(dy); } catch (Throwable ignored) { } } });
        }

        private boolean twoPart(View v, View box) {
            List<android.widget.ImageView> icons = new ArrayList<android.widget.ImageView>();
            List<TextView> texts = new ArrayList<TextView>();
            parts(v, icons, texts, 0);
            if (icons.size() != 1 || texts.size() != 1) return false;
            final android.widget.ImageView icon = icons.get(0);
            final TextView text = texts.get(0);
            android.graphics.drawable.Drawable dr = icon.getDrawable();
            RectF b = new RectF(dr.getBounds());
            if (b.isEmpty() || r.height() <= 0) return false;
            icon.getImageMatrix().mapRect(b);
            float iconC = offset(v, icon)[1] + icon.getPaddingTop() + b.centerY(), textC = offset(v, text)[1] + text.getHeight() / 2f;
            String key = idName(v), t = text.getText().toString();
            boolean latin = true;
            for (int i = 0; i < t.length(); i++) if (t.charAt(i) > 0x024F) latin = false;
            if (latin) {
                float dy = 0;
                if (box != null && box.getParent() != null) dy = Math.round((r.top + r.bottom) / 2f - (offset(v, box)[1] + box.getHeight() / 2f));
                float ic = iconC + (box != null && (box == icon || inside(icon, box)) ? dy : 0), tc = textC + (box != null && (box == text || inside(text, box)) ? dy : 0);
                synchronized (sTwoPart) { sTwoPart.put(key, new float[] { (ic - r.top) / r.height(), (tc - r.top) / r.height() }); }
                if (icon != box && icon.getTranslationY() != 0) MAIN.post(new Runnable() { public void run() { icon.setTranslationY(0); } });
                if (text != box && text.getTranslationY() != 0) MAIN.post(new Runnable() { public void run() { text.setTranslationY(0); } });
                return false;
            }
            float[] ref;
            synchronized (sTwoPart) { ref = sTwoPart.get(key); }
            if (ref == null) return false;
            final float di = Math.round(r.top + ref[0] * r.height() - iconC), dt = Math.round(r.top + ref[1] * r.height() - textC);
            final View still = box != null && box != icon && box != text && (inside(icon, box) || inside(text, box)) ? box : null;
            if (Math.abs(icon.getTranslationY() - di) < 1f && Math.abs(text.getTranslationY() - dt) < 1f && (still == null || still.getTranslationY() == 0)) return true;
            MAIN.post(new Runnable() { public void run() {
                try {
                    if (still != null) still.setTranslationY(0);
                    icon.setTranslationY(di);
                    text.setTranslationY(dt);
                } catch (Throwable ignored) { }
            }});
            return true;
        }

        private void parts(View v, List<android.widget.ImageView> icons, List<TextView> texts, int depth) {
            if (v.getVisibility() != View.VISIBLE || v.getAlpha() < 0.05f || actionCircle(v)) return;
            if (v instanceof TextView) { CharSequence t = ((TextView) v).getText(); if (t != null && t.length() > 0) texts.add((TextView) v); return; }
            if (v instanceof android.widget.ImageView) { if (((android.widget.ImageView) v).getDrawable() != null && v.getWidth() > 0) icons.add((android.widget.ImageView) v); return; }
            if (v instanceof ViewGroup && depth < 5) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) parts(((ViewGroup) v).getChildAt(i), icons, texts, depth + 1);
        }

        private boolean inside(View child, View parent) {
            for (android.view.ViewParent p = child.getParent(); p instanceof View; p = p.getParent()) if (p == parent) return true;
            return false;
        }

        private android.widget.ImageView soloIcon(View v) {
            List<android.widget.ImageView> found = new ArrayList<android.widget.ImageView>();
            if (!icons(v, found, 0) || found.size() != 1) return null;
            return found.get(0);
        }

        private boolean icons(View v, List<android.widget.ImageView> out, int depth) {
            if (v.getVisibility() != View.VISIBLE || v.getAlpha() < 0.05f || actionCircle(v)) return true;
            if (v instanceof TextView) { CharSequence t = ((TextView) v).getText(); return t == null || t.length() == 0; }
            if (v instanceof android.widget.ImageView) { if (((android.widget.ImageView) v).getDrawable() != null && v.getWidth() > 0) out.add((android.widget.ImageView) v); return true; }
            if (v instanceof ViewGroup && depth < 5) {
                ViewGroup g = (ViewGroup) v;
                for (int i = 0; i < g.getChildCount(); i++) if (!icons(g.getChildAt(i), out, depth + 1)) return false;
            }
            return true;
        }

        private void clearContainers(View v) {
            if (!(v instanceof ViewGroup)) return;
            final List<View> bg = new ArrayList<View>();
            boxes((ViewGroup) v, bg, 0);
            if (!bg.isEmpty()) MAIN.post(new Runnable() { public void run() { for (View x : bg) try { x.setBackground(null); } catch (Throwable ignored) { } } });
        }

        private void boxes(ViewGroup g, List<View> out, int depth) {
            if (depth > 3) return;
            for (int i = 0; i < g.getChildCount(); i++) {
                View ch = g.getChildAt(i);
                if (!(ch instanceof ViewGroup)) continue;
                if (ch.getBackground() != null) out.add(ch);
                boxes((ViewGroup) ch, out, depth + 1);
            }
        }
        Boolean isFunction, action;
        final Map<View, Boolean> tinted = new WeakHashMap<View, Boolean>();

        private void whiteIcons(View v) {
            if (actionCircle(v)) { if (v.getVisibility() != View.INVISIBLE) v.setVisibility(View.INVISIBLE); return; }
            if (v instanceof android.widget.ImageView) {
                android.widget.ImageView iv = (android.widget.ImageView) v;
                if (!tinted.containsKey(iv) || iv.getColorFilter() == null) { tinted.put(iv, Boolean.TRUE); iv.setColorFilter(Color.WHITE); }
            } else if (v instanceof TextView) {
                TextView t = (TextView) v;
                if (t.getCurrentTextColor() != Color.WHITE && t.getAlpha() != 0f) t.setTextColor(Color.WHITE);
            }
            if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) whiteIcons(((ViewGroup) v).getChildAt(i));
        }

        void draw(View v, Canvas c) {
            int w = v.getWidth(), h = v.getHeight();
            if (w <= 0 || h <= 0) return;
            float d = v.getResources().getDisplayMetrics().density;
            hideHints(v);
            clearContainers(v);
            symbolSwitch(v);
            int rowTop = v.getTop(), rowH = v.getParent() instanceof View ? ((View) v.getParent()).getHeight() : h;
            boolean column = rowH > h * 1.6f;
            if (rowH <= 0 || column) { rowH = h; rowTop = 0; }
            if (w != lastW || h != lastH || rowTop != lastTop || rowH != lastRowH) {
                lastW = w; lastH = h; lastTop = rowTop; lastRowH = rowH;
                float left = 0, right = w, bottom = h * 0.81f;
                View host = find(v, "host");
                if (host != null && host != v && host.getWidth() > 0) { int[] at = offset(v, host); left = at[0]; right = at[0] + host.getWidth(); }
                View label = labelArea(host != null && host != v && host instanceof ViewGroup ? host : v);
                if (label != null) { int[] at = offset(v, label); float lb = at[1] + label.getHeight(); if (at[1] <= 2 * d && lb > h * 0.75f && lb < h * 0.9f) bottom = lb; }
                float gapX = 3f * d, top = 4f * d;
                top = 4f * d - rowTop;
                bottom = rowH * 0.812f - rowTop;
                r.set(left + gapX, top, right - gapX, Math.max(top + 10 * d, bottom));
                labelBox = labelArea(host != null && host != v && host instanceof ViewGroup ? host : v);
            }
            align(v);
            if (isFunction == null) isFunction = function(v);
            float radius = 9f * d;
            corners(v, d);
            if (action == null) action = actionKey(v);
            sideKeys(v, action);
            boolean dark = action && sDarkKeys != null ? sDarkKeys : darkLabels(v);
            if (!action && text(v) != null) sDarkKeys = dark;
            boolean black = dark && superDark(v.getContext());
            if (column && !action) columnIcon(v, dark);
            operatorPanel(v);
            boolean function = isFunction;
            int color = dark ? (black ? 0xFF2C2C2E : 0xFF464648) : (function ? 0xFFACB1BA : 0xFFFFFFFF);
            if (v.isPressed()) color = dark ? (black ? 0xFF48484A : 0xFF6B6B6E) : (function ? 0xFFFFFFFF : 0xFFD6D8DD);
            if (action) {
                clearAction(v);
                color = !dark ? (v.isPressed() ? 0xFF4DA2FF : 0xFF007AFF) : black ? (v.isPressed() ? 0xFF3A93FF : 0xFF0A78F0) : (v.isPressed() ? 0xFF4DA3FF : 0xFF0A84FF);
                whiteIcons(v);
            }
            if (!dark) {
                shade.setColor(0x59898A8D);
                c.drawRoundRect(r.left, r.top + 1.2f * d, r.right, r.bottom + 1.2f * d, radius, radius, shade);
            }
            face.setColor(color);
            c.drawRoundRect(r, radius, radius, face);
        }

        Boolean del;
        private final Map<View, Integer> inked = new WeakHashMap<View, Integer>();

        private void columnIcon(View v, boolean dark) {
            final android.widget.ImageView solo = soloIcon(v);
            if (solo == null) return;
            if (!dark) {
                if (inked.remove(solo) != null) MAIN.post(new Runnable() { public void run() { try { solo.clearColorFilter(); } catch (Throwable ignored) { } } });
                return;
            }
            int ink = 0xFFE3E3E3;
            if (v.getParent() instanceof ViewGroup) {
                ViewGroup g = (ViewGroup) v.getParent();
                for (int i = 0; i < g.getChildCount(); i++) { TextView t = text(g.getChildAt(i)); if (t != null) { ink = t.getCurrentTextColor() | 0xFF000000; break; } }
            }
            final int col = ink;
            Integer was = inked.get(solo);
            if (was != null && was == col) return;
            inked.put(solo, col);
            MAIN.post(new Runnable() { public void run() { try { solo.setColorFilter(col, android.graphics.PorterDuff.Mode.SRC_IN); } catch (Throwable ignored) { } } });
        }

        private void sideKeys(final View v, boolean action) {
            if (del == null) del = "key_pos_del".equals(idName(v));
            if (!del && !action) return;
            if (v.getLayoutDirection() != View.LAYOUT_DIRECTION_LTR) MAIN.post(new Runnable() { public void run() { try { v.setLayoutDirection(View.LAYOUT_DIRECTION_LTR); } catch (Throwable ignored) { } } });
            unmirror(v);
            if (del && v.getParent() instanceof ViewGroup) {
                ViewGroup row = (ViewGroup) v.getParent();
                boolean shift = false;
                for (int i = 0; i < row.getChildCount() && !shift; i++) shift = "key_pos_shift".equals(idName(row.getChildAt(i)));
                if (shift && row.getWidth() > 0 && v.getWidth() > 0) sDelRatio = v.getWidth() / (float) row.getWidth();
            }
        }

        private void unmirror(View v) {
            if (v instanceof android.widget.ImageView && v.getClass().getName().endsWith("AutoMirroredImageView")) {
                if (v.getLayoutDirection() != View.LAYOUT_DIRECTION_LTR) { final View x = v; MAIN.post(new Runnable() { public void run() { try { x.setLayoutDirection(View.LAYOUT_DIRECTION_LTR); x.invalidate(); } catch (Throwable ignored) { } } }); }
                return;
            }
            if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) unmirror(((ViewGroup) v).getChildAt(i));
        }

        private void symbolSwitch(View v) {
            try {
                String n = "";
                try { n = v.getResources().getResourceEntryName(v.getId()); } catch (Throwable ignored) { }
                TextView t = text(v);
                String label = t == null || t.getText() == null ? "" : t.getText().toString();
                boolean sw = n.startsWith("key_pos_switch_to_symbol") || label.equalsIgnoreCase("ABC");
                if (!sw) return;
                bgIcons(v, 0);
                if (t == null) return;
                if (label.contains("?123") || label.contains("123")) { if (!label.equalsIgnoreCase("ABC")) { sSymSize = t.getTextSize(); sSymFace = t.getTypeface(); } return; }
                if (sSymSize > 0 && Math.abs(t.getTextSize() - sSymSize) > 0.5f) t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, sSymSize);
                if (sSymFace != null && t.getTypeface() != sSymFace) t.setTypeface(sSymFace);
            } catch (Throwable ignored) { }
        }

        private void bgIcons(View v, int depth) {
            if (depth > 4) return;
            Object tag = v.getTag();
            if (v instanceof android.widget.ImageView && tag instanceof String && ((String) tag).startsWith(".background-icon") && !((String) tag).contains("for-preview")) {
                if (v.getVisibility() == View.VISIBLE) v.setVisibility(View.INVISIBLE);
                return;
            }
            if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) bgIcons(((ViewGroup) v).getChildAt(i), depth + 1);
        }

        private boolean function(View v) {
            String n = "";
            try { n = v.getResources().getResourceEntryName(v.getId()); } catch (Throwable ignored) { }
            if (abcLabel(v, 0)) return true;
            return n.startsWith("key_pos_") && !n.equals("key_pos_space") && !n.equals("key_pos_comma") && !n.equals("key_pos_period");
        }

        private boolean darkLabels(View v) {
            TextView t = text(v);
            int col;
            if (t != null) col = t.getCurrentTextColor();
            else return (v.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES;
            return (Color.red(col) * 299 + Color.green(col) * 587 + Color.blue(col) * 114) / 1000 > 140;
        }

        private void hideHints(View v) {
            if (!(v instanceof ViewGroup)) return;
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                View ch = g.getChildAt(i);
                if (ch instanceof TextView && ch.getClass().getName().endsWith("MaterialTextView") && ch.getAlpha() != 0f) ch.setAlpha(0f);
            }
        }
    }

    private static final Map<View, Boolean> sRounded = new WeakHashMap<View, Boolean>();

    private static void corners(View key, float d) {
        View holder = null;
        for (View c = key; c != null; c = c.getParent() instanceof View ? (View) c.getParent() : null) {
            if (c.getId() != View.NO_ID) {
                String n = "";
                try { n = c.getResources().getResourceEntryName(c.getId()); } catch (Throwable ignored) { }
                if ("keyboard_holder".equals(n)) { holder = c; break; }
            }
        }
        if (holder == null || !(holder.getParent() instanceof View)) return;
        View panel = (View) holder.getParent();
        if (panel.getParent() instanceof View && ((View) panel.getParent()).getHeight() < key.getRootView().getHeight() * 0.9f) panel = (View) panel.getParent();
        synchronized (sRounded) { if (sRounded.containsKey(panel)) return; sRounded.put(panel, Boolean.TRUE); }
        synchronized (sOutlined) { if (sOutlined.containsKey(panel)) return; }
        if (holder.getParent() instanceof ViewGroup) { ViewGroup lin = (ViewGroup) holder.getParent(); int[] orig; synchronized (sPad) { orig = sPad.get(lin); } if (orig != null) { outline(panel, lin, orig, geo(), d); return; } }
        final float r = 24 * d;
        final View target = panel;
        MAIN.post(new Runnable() { public void run() {
            try {
                target.setOutlineProvider(new android.view.ViewOutlineProvider() {
                    @Override public void getOutline(View view, android.graphics.Outline o) { o.setRoundRect(0, 0, view.getWidth(), Math.round(view.getHeight() + r), r); }
                });
                target.setClipToOutline(true);
                target.invalidateOutline();
            } catch (Throwable t) { Log.w("corners", t); }
        }});
    }

    private static final Map<Integer, Boolean> sMenuIds = new HashMap<Integer, Boolean>();

    private static void installViews(ClassLoader cl) throws Throwable {
        Class<?> chips = null;
        try { chips = cl.loadClass("com.google.android.apps.inputmethod.libs.proactivesuggestion.ProactiveSuggestionsHolderView"); } catch (Throwable ignored) { }
        final Class<?> chipsClass = chips;
        final Class<?> key = cl.loadClass(SOFT_KEY);
        final Class<?> bar = cl.loadClass(BAR);
        Xp.hookAllMethods(View.class, "setVisibility", new Xp.Callback() {
            @Override protected void beforeHookedMethod(Xp.Param p) {
                try {
                    if (p.args.length != 1 || !(p.args[0] instanceof Integer)) return;
                    Object v = p.thisObject;
                    if ((Integer) p.args[0] == View.VISIBLE && v instanceof View) { try { dragIcon((View) v); } catch (Throwable ignored) { } }
                    if (!sOwnVis && (bar.isInstance(v) || (v instanceof View && ((View) v).getId() != View.NO_ID && (right((View) v) || (key.isInstance(v) && menu((View) v))))))
                        synchronized (sAsked) { sAsked.put((View) v, (Integer) p.args[0]); }
                    if (sVoice && sClassicVoice && bar.isInstance(v) && (Integer) p.args[0] == View.VISIBLE && !sOwnVis && android.os.SystemClock.uptimeMillis() - sClassicStart > 800 && inHeader((View) v)) {
                        headerFade();
                        sClassicVoice = false;
                        voiceOn(false);
                    }
                    if (!sOwnVis && (Integer) p.args[0] == View.VISIBLE && v instanceof View) { boolean tag; synchronized (sTags) { tag = sTags.containsKey(v); } if (tag) { MAIN.post(new Runnable() { public void run() { tags(); } }); } }
                    if (sVoice && (Integer) p.args[0] == View.VISIBLE && v instanceof View && infoButton((View) v) && toolbarOn()) { p.args[0] = View.GONE; return; }
                    if (sVoice && !sOwnVis && (Integer) p.args[0] == View.VISIBLE && v instanceof View) { boolean hid; synchronized (sVoiceHidden) { hid = sVoiceHidden.containsKey(v); } if (hid) { synchronized (sVoiceHidden) { sVoiceHidden.put((View) v, View.VISIBLE); } p.args[0] = View.GONE; return; } }
                    if (sVoice && !sOwnVis && bar.isInstance(v) && (Integer) p.args[0] == View.VISIBLE && inHeader((View) v) && toolbarOn()) { synchronized (sBarsHidden) { sBarsHidden.put((View) v, Boolean.TRUE); } p.args[0] = View.INVISIBLE; return; }
                    if (sVoice && v instanceof View && key.isInstance(v) && menu((View) v) && on(KEY_HIDE_MENU)) { p.args[0] = View.GONE; return; }
                    if (sCustomizing && v instanceof View && key.isInstance(v) && menu((View) v)) return;
                    if (sVoice && !sCustomizing && !sOwnVis && (Integer) p.args[0] == View.VISIBLE && v.getClass().getName().endsWith("CopyImageSourceView") && toolbarOn()) {
                        synchronized (sCustHidden) { sCustHidden.put((View) v, Boolean.TRUE); }
                        p.args[0] = View.INVISIBLE; return;
                    }
                    if (sCustomizing && !sOwnVis && (Integer) p.args[0] == View.VISIBLE && v instanceof View && powerKeyPart((View) v) && on(KEY_HIDE_RIGHT)) {
                        p.args[0] = "key_pos_header_power_key".equals(idName((View) v)) ? View.GONE : View.INVISIBLE; return;
                    }
                    if (sCustomizing && !sOwnVis && v instanceof View && bar.isInstance(v)) MAIN.post(new Runnable() { public void run() { tags(); } });
                    if (sVoice && (bar.isInstance(v) || (v instanceof View && ((View) v).getId() != View.NO_ID && (right((View) v) || (key.isInstance(v) && menu((View) v)))))) return;
                    if (!sCustomizing && (Integer) p.args[0] != View.VISIBLE && bar.isInstance(v) && on(KEY_ROWS) && !landscape((View) v) && inHeader((View) v)) { p.args[0] = View.VISIBLE; return; }
                    if ((Integer) p.args[0] == View.VISIBLE && v instanceof android.widget.ImageView && actionCircle((View) v) && on(KEY_KEYS)) { p.args[0] = View.INVISIBLE; return; }
                    if ((Integer) p.args[0] == View.VISIBLE && v instanceof android.widget.ImageView && switchBg((View) v) && on(KEY_KEYS)) { p.args[0] = View.INVISIBLE; return; }
                    if ((Integer) p.args[0] != View.GONE && key.isInstance(v)) { Boolean h; synchronized (sHiddenKeys) { h = sHiddenKeys.get(v); } if (Boolean.TRUE.equals(h) && on(KEY_KEYS)) { p.args[0] = View.GONE; return; } }
                    if ((Integer) p.args[0] == View.GONE) return;
                    if (v instanceof View && ((View) v).getId() != View.NO_ID && right((View) v) && on(KEY_HIDE_RIGHT)) { p.args[0] = View.GONE; return; }
                    if (chipsClass != null && chipsClass.isInstance(v)) {
                        if (!on(KEY_HIDE_CHIPS)) return;
                        p.args[0] = View.GONE;
                        showBar((View) v, bar);
                    } else if (key.isInstance(v) && menu((View) v) && on(KEY_HIDE_MENU)) {
                        p.args[0] = View.GONE;
                    }
                } catch (Throwable ignored) { }
            }
        });
        Xp.hookAllMethods(key, "onFinishInflate", new Xp.Callback() {
            @Override protected void afterHookedMethod(Xp.Param p) {
                try {
                    View v = (View) p.thisObject;
                    if (sVoice && !(sClassicVoice && menu(v) && on(KEY_HIDE_MENU))) return;
                    if (sCustomizing && menu(v)) return;
                    if ((menu(v) && on(KEY_HIDE_MENU)) || (right(v) && on(KEY_HIDE_RIGHT))) {
                        synchronized (sAsked) { sAsked.put(v, v.getVisibility()); }
                        sOwnVis = true;
                        try { v.setVisibility(View.GONE); } finally { sOwnVis = false; }
                    }
                } catch (Throwable ignored) { }
            }
        });
    }

    private static final Map<View, Integer> sRowH = new WeakHashMap<View, Integer>();
    private static final Map<View, Integer> sTries = new WeakHashMap<View, Integer>();
    private static final Map<View, Boolean> sHeader = new WeakHashMap<View, Boolean>();
    private static Method sSetMeasured;

    private static String idName(View v) {
        if (v.getId() == View.NO_ID || v.getId() == 0) return "";
        try { return v.getResources().getResourceEntryName(v.getId()); } catch (Throwable t) { return ""; }
    }

    private static boolean inHeader(View v) {
        Boolean known;
        synchronized (sHeader) { known = sHeader.get(v); }
        if (known != null) return known;
        boolean h = false;
        for (View c = v; c != null; c = c.getParent() instanceof View ? (View) c.getParent() : null)
            if ("keyboard_header_view_holder".equals(idName(c))) { h = true; break; }
        if (v.isAttachedToWindow()) synchronized (sHeader) { sHeader.put(v, h); }
        return h;
    }

    private static volatile int sOneRow;

    private static void installInline() throws Throwable {
        if (android.os.Build.VERSION.SDK_INT < 30) return;
        Class<?> is = Class.forName("android.view.inputmethod.InlineSuggestion");
        Xp.hookAllMethods(is, "inflate", new Xp.Callback() {
            @Override protected void beforeHookedMethod(Xp.Param p) {
                try {
                    int row = sOneRow;
                    if (row > 0 && on(KEY_ROWS) && p.args.length >= 2 && p.args[1] instanceof android.util.Size) {
                        android.util.Size z = (android.util.Size) p.args[1];
                        if (z.getHeight() > row) p.args[1] = new android.util.Size(z.getWidth(), row);
                    }
                } catch (Throwable ignored) { }
            }
        });
        Xp.hookAllConstructors(Class.forName("android.widget.inline.InlinePresentationSpec$Builder"), new Xp.Callback() {
            @Override protected void beforeHookedMethod(Xp.Param p) {
                try {
                    int row = sOneRow;
                    if (p.args.length < 2) return;
                    if (row > 0 && on(KEY_ROWS)) for (int i = 0; i < 2; i++) if (p.args[i] instanceof android.util.Size) {
                        android.util.Size z = (android.util.Size) p.args[i];
                        if (z.getHeight() > row) p.args[i] = new android.util.Size(z.getWidth(), row);
                    }
                } catch (Throwable ignored) { }
            }
        });
    }

    private static void inlineSurfaces(View v, StringBuilder sb, int depth) {
        if (depth > 8) return;
        if (v instanceof android.view.SurfaceView) sb.append("\n  surface ").append(inlineDesc(v));
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) inlineSurfaces(((ViewGroup) v).getChildAt(i), sb, depth + 1);
    }

    private static String inlineDesc(View v) {
        String cls = v.getClass().getName();
        cls = cls.substring(cls.lastIndexOf('.') + 1);
        String s = cls + "#" + idName(v) + " " + v.getWidth() + "x" + v.getHeight() + " at " + v.getLeft() + "," + v.getTop()
                + " y=" + v.getY() + " ty=" + v.getTranslationY() + " vis=" + v.getVisibility() + " pad=" + v.getPaddingTop() + "/" + v.getPaddingBottom();
        if (v instanceof ViewGroup) s += " clip=" + ((ViewGroup) v).getClipChildren();
        return s;
    }

    private static void installRows(ClassLoader cl) throws Throwable {
        Class<?> holder = cl.loadClass("com.google.android.libraries.inputmethod.keyboard.impl.KeyboardViewHolder");
        Method measure = null;
        for (Class<?> k = holder; k != null && measure == null && k != View.class; k = k.getSuperclass())
            try { measure = k.getDeclaredMethod("onMeasure", int.class, int.class); } catch (NoSuchMethodException ignored) { }
        if (measure == null) return;
        try { installInline(); } catch (Throwable t) { Log.w("autofill size", t); }
        sSetMeasured = View.class.getDeclaredMethod("setMeasuredDimension", int.class, int.class);
        sSetMeasured.setAccessible(true);
        final Class<?> holderClass = holder;
        Xp.hookMethod(measure, new Xp.Callback() {
            @Override protected void afterHookedMethod(Xp.Param p) {
                try {
                    if (!holderClass.isInstance(p.thisObject)) return;
                    try { bodyHeight((ViewGroup) p.thisObject); } catch (Throwable t) { Log.w("height", t); }
                    if (!on(KEY_ROWS)) return;
                    ViewGroup g = (ViewGroup) p.thisObject;
                    if (landscape(g)) return;
                    if (!"keyboard_header_view_holder".equals(idName(g))) return;
                    int w = g.getMeasuredWidth(), h = g.getMeasuredHeight();
                    if (w <= 0 || h <= 0) return;
                    synchronized (sRowH) { sRowH.put(g, h); }
                    sOneRow = h;
                    int target = h * 2;
                    for (int i = 0; i < g.getChildCount(); i++) {
                        View ch = g.getChildAt(i);
                        if (ch.getVisibility() == View.GONE) continue;
                        int cw = Math.max(0, w - g.getPaddingLeft() - g.getPaddingRight()), chh = Math.max(0, target - g.getPaddingTop() - g.getPaddingBottom());
                        ch.measure(View.MeasureSpec.makeMeasureSpec(cw, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(chh, View.MeasureSpec.EXACTLY));
                        if (ch instanceof ViewGroup) {
                            ViewGroup sk = (ViewGroup) ch;
                            for (int k = 0; k < sk.getChildCount(); k++) {
                                View in = sk.getChildAt(k);
                                if (in.getVisibility() == View.GONE || !in.getClass().getName().endsWith("ScaledKeyboardViewInner")) continue;
                                in.measure(View.MeasureSpec.makeMeasureSpec(Math.max(0, cw - sk.getPaddingLeft() - sk.getPaddingRight()), View.MeasureSpec.EXACTLY),
                                        View.MeasureSpec.makeMeasureSpec(Math.max(0, chh - sk.getPaddingTop() - sk.getPaddingBottom()), View.MeasureSpec.EXACTLY));
                            }
                        }
                    }
                    sSetMeasured.invoke(g, w, target);
                } catch (Throwable t) { Log.w("rows measure", t); }
            }
        });
        Class<?> cons;
        try { cons = cl.loadClass("androidx.constraintlayout.widget.ConstraintLayout"); } catch (Throwable t) { return; }
        final Class<?> bar = cl.loadClass(BAR);
        Xp.hookAllMethods(cons, "onLayout", new Xp.Callback() {
            @Override protected void afterHookedMethod(Xp.Param p) {
                try {
                    ViewGroup inner = (ViewGroup) p.thisObject;
                    if (!inner.getClass().getName().endsWith("ScaledKeyboardViewInner")) return;
                    try { lower(inner); } catch (Throwable t) { Log.w("lower", t); }
                    try { keepLtr(inner, bar); } catch (Throwable t) { Log.w("toolbar direction", t); }
                    if (!on(KEY_ROWS) || landscape(inner)) { try { oneRow(inner, bar); } catch (Throwable t) { Log.w("one row", t); } return; }
                    View holderView = null;
                    for (View c = inner; c != null; c = c.getParent() instanceof View ? (View) c.getParent() : null)
                        if ("keyboard_header_view_holder".equals(idName(c))) { holderView = c; break; }
                    if (holderView == null) return;
                    Integer rowH;
                    synchronized (sRowH) { rowH = sRowH.get(holderView); }
                    int W = inner.getWidth(), H = inner.getHeight();
                    if (rowH == null) return;
                    if (H < rowH * 1.5f) {
                        View sk = inner.getParent() instanceof View ? (View) inner.getParent() : null;
                        int ph = sk == null ? 0 : sk.getHeight() - sk.getPaddingTop() - sk.getPaddingBottom();
                        if (ph < rowH * 1.5f) {  return; }
                        Integer n;
                        synchronized (sTries) { n = sTries.get(inner); sTries.put(inner, n == null ? 1 : n + 1); }
                        if (n != null && n >= 3) return;
                        final View fix = inner;
                        MAIN.post(new Runnable() { public void run() { try { fix.requestLayout(); } catch (Throwable ignored) { } } });
                        return;
                    }
                    rows(inner, bar, W, H, H / 2);
                } catch (Throwable t) { Log.w("rows layout", t); }
            }
        });
    }

    private static final Map<View, int[]> sTitlePad = new WeakHashMap<View, int[]>();

    private static void voiceTitle(View ch) {
        if (ch == null || ch.getClass() != android.widget.FrameLayout.class || !hasTitle((ViewGroup) ch)) return;
        int[] orig;
        synchronized (sTitlePad) { orig = sTitlePad.get(ch); }
        int pl = ch.getPaddingLeft(), pr = ch.getPaddingRight();
        if (sVoice && on(KEY_ROWS)) {
            if (pl == pr) return;
            if (orig == null) { orig = new int[] { pl, pr }; synchronized (sTitlePad) { sTitlePad.put(ch, orig); } }
            int side = Math.max(pl, pr);
            ch.setPadding(side, ch.getPaddingTop(), side, ch.getPaddingBottom());
                    } else if (orig != null) {
            synchronized (sTitlePad) { sTitlePad.remove(ch); }
            ch.setPadding(orig[0], ch.getPaddingTop(), orig[1], ch.getPaddingBottom());
        }
    }

    private static boolean hasTitle(ViewGroup g) {
        for (int i = 0; i < g.getChildCount(); i++) if (g.getChildAt(i).getClass().getName().endsWith(".AutoCenterScaleTextView")) return true;
        return false;
    }

    private static final Runnable VOICE_TITLES = new Runnable() { public void run() {
        try {
            View h = headerHolder();
            if (h == null || !on(KEY_ROWS) || landscape(h)) return;
            voiceTitles(h);
        } catch (Throwable ignored) { }
    }};

    private static void voiceTitles(View v) {
        if (!(v instanceof ViewGroup)) return;
        if (v.getClass() == android.widget.FrameLayout.class && hasTitle((ViewGroup) v)) { voiceTitle(v); return; }
        ViewGroup g = (ViewGroup) v;
        for (int i = 0; i < g.getChildCount(); i++) voiceTitles(g.getChildAt(i));
    }

    private static void rows(ViewGroup inner, Class<?> bar, int W, int H, int rowH) {
        boolean shortcutsTop = on(KEY_ROWS_TOP);
        int sTop = shortcutsTop ? 0 : H - rowH, gTop = shortcutsTop ? H - rowH : 0;
        View barView = findInstance(inner, bar);
        if (barView == null) return;
        if (barView.getVisibility() != View.VISIBLE && !sVoice && !sCustomizing) { final View b = barView; MAIN.post(new Runnable() { public void run() { if (sVoice || sCustomizing) return; b.setTag(TAG_HID, null); b.setVisibility(View.VISIBLE); } }); }
        View frame = barView.getParent() instanceof View ? (View) barView.getParent() : null;
        View column = barView;
        while (column.getParent() != inner && column.getParent() instanceof View) column = (View) column.getParent();
        if (frame == null || column.getParent() != inner) return;
        int menuRight = 0, powerLeft = W;
        for (int i = 0; i < inner.getChildCount(); i++) {
            View ch = inner.getChildAt(i);
            if (ch == column || ch.getVisibility() == View.GONE) continue;
            String n = idName(ch);
            if (!n.startsWith("key_pos_header_")) continue;
            int cw = ch.getWidth() > 0 ? ch.getWidth() : ch.getMeasuredWidth();
            if (cw <= 0) continue;
            ch.measure(View.MeasureSpec.makeMeasureSpec(cw, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(rowH, View.MeasureSpec.EXACTLY));
            ch.layout(ch.getLeft(), sTop, ch.getLeft() + cw, sTop + rowH);
            if (!shows(ch)) continue;
            sSideW = cw;
            if (ch.getLeft() < W / 2) menuRight = Math.max(menuRight, ch.getRight()); else powerLeft = Math.min(powerLeft, ch.getLeft());
        }
        boolean lpChanged = false;
        final int colL = column.getLeft(), colW = column.getWidth();
        boolean custNow = sCustomizing;
        if (!custNow && colW > 0 && (colL > 0 || colW < W - 1)) { try { custNow = customizeText(inner.getRootView(), 0); } catch (Throwable ignored) { } }
        final boolean adopt = custNow && colW > 0 && column.getHeight() > 0;
        final int barLeft = adopt ? Math.max(0, menuRight - colL) : menuRight;
        final int barRight = adopt ? Math.max(0, colL + colW - powerLeft) : W - powerLeft;
        if (frame instanceof ViewGroup) {
            ViewGroup f = (ViewGroup) frame;
            for (int i = 0; i < f.getChildCount(); i++) {
                View ch = f.getChildAt(i);
                if (!(ch.getLayoutParams() instanceof android.widget.FrameLayout.LayoutParams)) continue;
                android.widget.FrameLayout.LayoutParams lp = (android.widget.FrameLayout.LayoutParams) ch.getLayoutParams();
                boolean isBar = ch == barView;
                int wantTop = isBar ? sTop : gTop, wantLeft = isBar ? barLeft : 0, wantRight = isBar ? barRight : 0;
                if (lp.height != rowH || lp.topMargin != wantTop || lp.leftMargin != wantLeft || lp.rightMargin != wantRight || lp.width != ViewGroup.LayoutParams.MATCH_PARENT
                        || lp.gravity != (android.view.Gravity.TOP | android.view.Gravity.START)) lpChanged = true;
                lp.height = rowH;
                lp.gravity = android.view.Gravity.TOP | android.view.Gravity.START;
                lp.topMargin = isBar ? sTop : gTop;
                lp.leftMargin = wantLeft;
                lp.rightMargin = wantRight;
                lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
                if (!isBar) voiceTitle(ch);
            }
        }
        if (adopt) {
            if (lpChanged) {
                int ct = column.getTop(), ch = column.getHeight();
                column.measure(View.MeasureSpec.makeMeasureSpec(colW, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(ch, View.MeasureSpec.EXACTLY));
                column.layout(colL, ct, colL + colW, ct + ch);
            }
            grow(barView, null, W, true);
            return;
        }
        float[] before = positions(barView);
        column.measure(View.MeasureSpec.makeMeasureSpec(W, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(H, View.MeasureSpec.EXACTLY));
        column.layout(0, 0, W, H);
        grow(barView, before, W);
            }

    private static void oneRow(ViewGroup inner, Class<?> bar) {
        if (!inHeader(inner)) return;
        View barView = findInstance(inner, bar);
        if (barView == null || !(barView.getParent() instanceof ViewGroup)) return;
        ViewGroup frame = (ViewGroup) barView.getParent();
        try { span(inner, barView); } catch (Throwable t) { Log.w("toolbar span", t); }
        boolean suggesting = false;
        for (int i = 0; i < frame.getChildCount() && !suggesting; i++) {
            View ch = frame.getChildAt(i);
            if (ch == barView || ch.getVisibility() != View.VISIBLE || !ch.getClass().getName().contains("Candidates") || !(ch instanceof ViewGroup)) continue;
            ViewGroup g = (ViewGroup) ch;
            for (int k = 0; k < g.getChildCount(); k++) { View c = g.getChildAt(k); if (c.getVisibility() == View.VISIBLE && c.getWidth() > 0 && c.getAlpha() > 0.01f) { suggesting = true; break; } }
        }
        final View b = barView;
        final boolean hide = suggesting;
        if (hide ? b.getVisibility() == View.VISIBLE : b.getVisibility() == View.INVISIBLE && Boolean.TRUE.equals(b.getTag(TAG_HID))) {
            MAIN.post(new Runnable() { public void run() {
                try {
                    if (hide) { b.setVisibility(View.INVISIBLE); b.setTag(TAG_HID, Boolean.TRUE); }
                    else { b.setTag(TAG_HID, null); b.setVisibility(View.VISIBLE); }
                } catch (Throwable ignored) { }
            }});
        }
    }

    private static final int TAG_HID = 0x7f5e0001;
    private static boolean sLtrLogged;

    private static boolean toolbarOn() {
        return on(KEY_SHORTCUTS) || on(KEY_ROWS) || on(KEY_HIDE_MENU) || on(KEY_HIDE_RIGHT) || on(GboardToolbar.KEY) || barChoice() != null;
    }

    private static void keepLtr(final ViewGroup inner, Class<?> bar) {
        if (!inHeader(inner) || !toolbarOn() || !(inner.getParent() instanceof View)) return;
        final int lang = ((View) inner.getParent()).getLayoutDirection();
        final List<View> others = new ArrayList<View>();
        View barView = findInstance(inner, bar);
        if (barView != null && barView.getParent() instanceof ViewGroup) {
            ViewGroup frame = (ViewGroup) barView.getParent();
            for (int i = 0; i < frame.getChildCount(); i++) { View ch = frame.getChildAt(i); if (ch != barView && ch.getLayoutDirection() != lang) others.add(ch); }
        }
        final boolean flip = inner.getLayoutDirection() != View.LAYOUT_DIRECTION_LTR;
        if (!flip && others.isEmpty()) return;
        MAIN.post(new Runnable() { public void run() {
            try {
                if (flip) inner.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
                for (View o : others) o.setLayoutDirection(lang);
                if (flip && !sLtrLogged) { sLtrLogged = true; }
            } catch (Throwable ignored) { }
        }});
    }
    private static int sSideW;

    private static boolean shows(View v) {
        if (v.getVisibility() != View.VISIBLE || v.getAlpha() < 0.05f || v.getWidth() <= 0 && v.getMeasuredWidth() <= 0) return false;
        return content(v, 0);
    }

    private static boolean content(View v, int depth) {
        if (v.getVisibility() != View.VISIBLE || v.getAlpha() < 0.05f) return false;
        if (v instanceof android.widget.ImageView) return ((android.widget.ImageView) v).getDrawable() != null;
        if (v instanceof TextView) { CharSequence t = ((TextView) v).getText(); return t != null && t.length() > 0; }
        if (v instanceof ViewGroup && depth < 6) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) if (content(g.getChildAt(i), depth + 1)) return true;
        }
        return false;
    }

    private static void span(ViewGroup inner, View barView) {
        View column = barView;
        while (column.getParent() != inner && column.getParent() instanceof View) column = (View) column.getParent();
        if (column.getParent() != inner || column.getWidth() <= 0) return;
        int W = inner.getWidth();
        boolean leftShows = false, rightShows = false;
        for (int i = 0; i < inner.getChildCount(); i++) {
            View ch = inner.getChildAt(i);
            if (ch == column || !idName(ch).startsWith("key_pos_header_")) continue;
            if (right(ch) && on(KEY_HIDE_RIGHT) && ch.getVisibility() != View.GONE) { final View x = ch; MAIN.post(new Runnable() { public void run() { x.setVisibility(View.GONE); } }); continue; }
            if (!shows(ch)) continue;
            sSideW = ch.getWidth();
            if (ch.getLeft() + ch.getWidth() / 2 < W / 2) leftShows = true; else rightShows = true;
        }
        int left = leftShows ? column.getLeft() : 0, right = rightShows ? column.getRight() : W;
        float[] before = positions(barView);
        if (right - left > column.getWidth() / 2 && (column.getLeft() != left || column.getRight() != right)) {
            column.measure(View.MeasureSpec.makeMeasureSpec(right - left, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(column.getHeight(), View.MeasureSpec.EXACTLY));
            column.layout(left, column.getTop(), right, column.getBottom());
        }
        grow(barView, before, W);
    }

    private static final Map<View, Integer> sBarW = new WeakHashMap<View, Integer>();
    private static final android.view.animation.Interpolator GLIDE = new android.view.animation.PathInterpolator(0.2f, 0f, 0f, 1f);

    private static float[] positions(View barView) {
        if (!(barView instanceof ViewGroup)) return null;
        ViewGroup g = (ViewGroup) barView;
        float[] out = new float[g.getChildCount()];
        for (int i = 0; i < out.length; i++) { View c = g.getChildAt(i); out[i] = c.getWidth() > 0 ? barView.getLeft() + absLeft(barView) + c.getLeft() + c.getWidth() / 2f : Float.NaN; }
        return out;
    }

    private static int absLeft(View v) {
        int x = 0;
        for (View p = v.getParent() instanceof View ? (View) v.getParent() : null; p != null && !"keyboard_header_view_holder".equals(idName(p)); p = p.getParent() instanceof View ? (View) p.getParent() : null) {
            x += p.getLeft();
            if (p.getClass().getName().endsWith("ScaledKeyboardViewInner")) break;
        }
        return x;
    }

    private static void grow(View barView, float[] before, int W) { grow(barView, before, W, sCustomizing); }

    private static void grow(View barView, float[] before, int W, boolean customizing) {
        if (!(barView instanceof ViewGroup) || W <= 0) return;
        ViewGroup g = (ViewGroup) barView;
        int bw = barView.getWidth();
        if (bw <= 0) return;
        int side = sSideW > 0 ? sSideW : W / 9;
        float room = bw / (float) Math.max(1, W - 2 * side);
        final float scale = Math.max(1f, Math.min(1.18f, 1f + (room - 1f) * 0.62f));
        Integer last;
        synchronized (sBarW) { last = sBarW.put(barView, bw); }
        boolean changed = last != null && Math.abs(last - bw) > 2;
        List<View> items = new ArrayList<View>();
        boolean other = false;
        for (int i = 0; i < g.getChildCount(); i++) {
            View c = g.getChildAt(i);
            if (c.getVisibility() != View.VISIBLE || c.getWidth() <= 0 || c instanceof android.widget.Space) continue;
            if (c.getClass().getName().endsWith("AccessPointsBarItemSoftKeyView")) items.add(c); else other = true;
        }
        final boolean still = customizing;
        if (still) {
            for (int i = 0; i < g.getChildCount(); i++) {
                View c = g.getChildAt(i);
                if (c.getScaleX() != 1f || c.getScaleY() != 1f) { c.animate().cancel(); c.setScaleX(1f); c.setScaleY(1f); }
                for (View f : faces(c)) if (f.getScaleX() != 1f || f.getScaleY() != 1f) { f.animate().cancel(); f.setScaleX(1f); f.setScaleY(1f); }
            }
            return;
        }
        if (!other && !items.isEmpty() && room > 1.02f && !still) {
            float slot = bw / (float) items.size();
            for (int i = 0; i < items.size(); i++) {
                View c = items.get(i);
                int cw = c.getWidth(), l = Math.round(i * slot + (slot - cw) / 2f);
                if (Math.abs(c.getLeft() - l) > 1) c.layout(l, c.getTop(), l + cw, c.getBottom());
            }
        }
        for (int i = 0; i < g.getChildCount(); i++) {
            final View c = g.getChildAt(i);
            if (c.getVisibility() != View.VISIBLE || c.getWidth() <= 0 || c instanceof android.widget.Space) continue;
            float now = barView.getLeft() + absLeft(barView) + c.getLeft() + c.getWidth() / 2f;
            float was = before != null && i < before.length ? before[i] : Float.NaN;
            boolean glide = !still && changed && !Float.isNaN(was) && Math.abs(was - now) > 1f;
            try { pill(c); } catch (Throwable ignored) { }
            final List<View> faces = faces(c);
            float cur = faces.isEmpty() ? c.getScaleX() : faces.get(0).getScaleX();
            if (!glide && Math.abs(cur - scale) < 0.005f && Math.abs(c.getScaleX() - (faces.isEmpty() ? scale : 1f)) < 0.005f) continue;
            final float dx = glide ? was - now : 0f;
            final boolean animate = changed && !still;
            MAIN.post(new Runnable() { public void run() {
                try {
                    List<View> targets = faces.isEmpty() ? java.util.Collections.singletonList(c) : faces;
                    if (!faces.isEmpty() && (c.getScaleX() != 1f || c.getScaleY() != 1f)) { c.setScaleX(1f); c.setScaleY(1f); }
                    if (!animate) { for (View t : targets) { t.setScaleX(scale); t.setScaleY(scale); } return; }
                    if (dx != 0f) c.setTranslationX(c.getTranslationX() + dx);
                    c.animate().translationX(0f).setDuration(280).setInterpolator(GLIDE).start();
                    for (View t : targets) if (t != c || faces.isEmpty()) t.animate().scaleX(scale).scaleY(scale).setDuration(280).setInterpolator(GLIDE).start();
                } catch (Throwable ignored) { }
            }});
        }
    }
    private static final Map<View, android.graphics.drawable.Drawable> sPills = new WeakHashMap<View, android.graphics.drawable.Drawable>();

    private static final Map<View, android.graphics.drawable.Drawable> sMyPills = new WeakHashMap<View, android.graphics.drawable.Drawable>();

    private static void pill(final View item) {
        if (!item.getClass().getName().endsWith("AccessPointsBarItemSoftKeyView")) return;
        if (!sPills.containsKey(item)) sPills.put(item, null);
        View rip = rippleOf(item, 0);
        if (rip == null) return;
        android.graphics.drawable.Drawable mine = sMyPills.get(rip);
        if (sCustomizing) { if (mine != null && rip.getBackground() == mine) rip.setBackground(sPills.get(rip)); return; }
        if (mine != null && rip.getBackground() == mine) return;
        sPills.put(rip, rip.getBackground());
        Context c = item.getContext();
        boolean dark = sDarkKeys != null ? sDarkKeys : (c.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        int grey = dark ? (superDark(c) ? 0xFF48484A : 0xFF5A5A5E) : 0xFFC9CCD2;
        android.graphics.drawable.GradientDrawable on = new android.graphics.drawable.GradientDrawable();
        on.setColor(grey);
        on.setCornerRadius(1000f);
        android.graphics.drawable.StateListDrawable sl = new android.graphics.drawable.StateListDrawable();
        sl.addState(new int[]{android.R.attr.state_pressed}, on);
        sl.addState(new int[0], new android.graphics.drawable.ColorDrawable(0));
        sl.setEnterFadeDuration(60);
        sl.setExitFadeDuration(160);
        sMyPills.put(rip, sl);
        rip.setBackground(sl);
    }

    private static View rippleOf(View v, int depth) {
        if (depth > 4) return null;
        Object tag = v.getTag();
        if (tag instanceof String && ((String) tag).contains("item-ripple-background")) return v;
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) { View f = rippleOf(((ViewGroup) v).getChildAt(i), depth + 1); if (f != null) return f; }
        return null;
    }

    private static List<View> faces(View item) {
        List<View> out = new ArrayList<View>();
        if (!(item instanceof ViewGroup)) return out;
        ViewGroup g = (ViewGroup) item;
        for (int i = 0; i < g.getChildCount(); i++) {
            View ch = g.getChildAt(i);
            Object tag = ch.getTag();
            if (tag instanceof String && ((String) tag).startsWith(".background")) continue;
            out.add(ch);
        }
        return out;
    }

    private static void lower(ViewGroup inner) {
        if (!inHeader(inner)) return;
        View holder = null;
        for (View c = inner; c != null; c = c.getParent() instanceof View ? (View) c.getParent() : null)
            if ("keyboard_holder".equals(idName(c))) { holder = c; break; }
        if (holder == null || !(holder.getParent() instanceof ViewGroup)) return;
        place((ViewGroup) holder.getParent(), inner);
    }

    static final class Geo {
        float w = 1f, cx = 0.5f, h = 1f; int up = 0;
        boolean plain() { return Math.abs(w - 1f) < 0.005f && Math.abs(h - 1f) < 0.005f && up == 0; }
    }

    private static volatile Geo sGeo = new Geo();
    private static volatile long sGeoRead;
    private static Boolean sGeoPatron;

    static Geo geo() {
        long now = android.os.SystemClock.uptimeMillis();
        if (now - sGeoRead < 1500) return sGeo;
        sGeoRead = now;
        Geo g = new Geo();
        try {
            Context c = app();
            String v = c == null ? null : Settings.Secure.getString(c.getContentResolver(), KEY_GEO);
            if (v != null && !v.trim().isEmpty()) {
                if (sGeoPatron == null) sGeoPatron = PatronGate.ok(c);
                if (sGeoPatron) {
                    String[] f = v.split(",");
                    g.w = clamp(Float.parseFloat(f[0]), 0.6f, 1f);
                    g.cx = clamp(Float.parseFloat(f[1]), g.w / 2f, 1f - g.w / 2f);
                    g.h = clamp(Float.parseFloat(f[2]), 0.75f, 1.35f);
                    g.up = Math.round(clamp(Float.parseFloat(f[3]), -FREE_DP, 260));
                }
            }
        } catch (Throwable ignored) { }
        sGeo = g;
        return g;
    }

    private static float clamp(float v, float lo, float hi) { return Math.max(lo, Math.min(hi, v)); }

    private static Boolean sLowPatron;

    static void lowestPlace(InputMethodService ims) {
        android.app.Dialog dlg = ims.getWindow();
        View decor = dlg == null || dlg.getWindow() == null ? null : dlg.getWindow().peekDecorView();
        if (decor == null) return;
        int id = Resources.getSystem().getIdentifier("inputArea", "id", "android");
        View area = id == 0 ? null : decor.findViewById(id);
        if (area == null || !(area.getLayoutParams() instanceof ViewGroup.MarginLayoutParams)) return;
        if (sLowPatron == null) sLowPatron = PatronGate.ok(ims);
        boolean portrait = ims.getResources().getConfiguration().orientation != android.content.res.Configuration.ORIENTATION_LANDSCAPE;
        int want = sLowPatron && portrait ? -Math.round(FREE_DP * ims.getResources().getDisplayMetrics().density) : 0;
        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) area.getLayoutParams();
        if (lp.bottomMargin == want) return;
        lp.bottomMargin = want;
        area.setLayoutParams(lp);
            }

    private static int lowestPx() {
        try {
            InputMethodService ims = sIme.get();
            if (ims == null) return 0;
            if (sLowPatron == null) sLowPatron = PatronGate.ok(ims);
            if (!sLowPatron) return 0;
            if (ims.getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) return 0;
            return Math.round(FREE_DP * ims.getResources().getDisplayMetrics().density);
        } catch (Throwable t) { return 0; }
    }

    private static final Map<Object, Boolean> sBoundsDone = new WeakHashMap<Object, Boolean>();

    static void prepareResizeBounds(InputMethodService ims, ClassLoader cl) throws Throwable {
        String name = Shortcuts.cachedClass(ims, "resize", "com/google/android/libraries/inputmethod/keyboardmode/ResizableKeyboardModeController");
        if (name == null) { return; }
        Class<?> base = cl.loadClass(name).getSuperclass();
        if (base == null || base == Object.class) return;
        for (Method m : base.getDeclaredMethods()) {
            Class<?>[] t = m.getParameterTypes();
            if (Modifier.isStatic(m.getModifiers())) continue;
            if (t.length == 2 && t[0] == android.graphics.Rect.class && t[1] == int.class && m.getReturnType() == void.class) {
                Xp.hookMethod(m, new Xp.Callback() {
                    @Override protected void beforeHookedMethod(Xp.Param p) {
                        try {
                            int px = lowestPx();
                            if (px <= 0 || !(p.args[0] instanceof android.graphics.Rect)) return;
                            android.graphics.Rect r = new android.graphics.Rect((android.graphics.Rect) p.args[0]);
                            r.bottom += px;
                            p.args[0] = r;
                                                    } catch (Throwable t) { Log.w("resize area", t); }
                    }
                });
            } else if ("activate".equals(m.getName()) && t.length == 1 && !t[0].isPrimitive()) {
                Xp.hookMethod(m, new Xp.Callback() {
                    @Override protected void beforeHookedMethod(Xp.Param p) {
                        try {
                            int px = lowestPx();
                            Object bundle = p.args[0];
                            if (px <= 0 || bundle == null) return;
                            synchronized (sBoundsDone) { if (sBoundsDone.containsKey(bundle)) return; sBoundsDone.put(bundle, Boolean.TRUE); }
                            for (Field f : bundle.getClass().getDeclaredFields()) {
                                if (Modifier.isStatic(f.getModifiers()) || f.getType() != android.graphics.Rect.class) continue;
                                f.setAccessible(true);
                                android.graphics.Rect r = (android.graphics.Rect) f.get(bundle);
                                if (r == null || r.height() <= 0) continue;
                                android.graphics.Rect g = new android.graphics.Rect(r);
                                g.bottom += px;
                                f.set(bundle, g);
                                                                break;
                            }
                        } catch (Throwable t) { Log.w("resize area", t); }
                    }
                });
            }
        }
    }

    private static void installGeometry() {
        Xp.hookAllMethods(InputMethodService.class, "onStartInputView", new Xp.Callback() {
            @Override protected void beforeHookedMethod(Xp.Param p) { sGeoRead = 0; }
            @Override protected void afterHookedMethod(Xp.Param p) {
                try { if (p.thisObject instanceof InputMethodService) lowestPlace((InputMethodService) p.thisObject); } catch (Throwable t) { Log.w("lowest place", t); }
                try { if (p.thisObject instanceof InputMethodService && sLateHooks) GboardAutoCorrect.opened((InputMethodService) p.thisObject); } catch (Throwable ignored) { }
                try { if (p.thisObject instanceof InputMethodService) GboardPreviewShot.schedule((InputMethodService) p.thisObject, 700); } catch (Throwable ignored) { }
                MAIN.post(new Runnable() { public void run() {
                    try {
                        ViewGroup lin = sLin == null ? null : sLin.get();
                        ViewGroup inner = sInner == null ? null : sInner.get();
                        View body = sBody == null ? null : sBody.get();
                        if (lin != null && inner != null && lin.isAttachedToWindow()) place(lin, inner);
                        if (body != null) body.requestLayout();
                    } catch (Throwable ignored) { }
                }});
            }
        });
        Xp.hookAllMethods(View.class, "setPadding", new Xp.Callback() {
            @Override protected void beforeHookedMethod(Xp.Param p) {
                try {
                    if (sOurPad || sPad.isEmpty() || p.args.length != 4) return;
                    int[] orig, ours;
                    synchronized (sPad) { orig = sPad.get(p.thisObject); ours = sPadOurs.get(p.thisObject); }
                    if (orig == null) return;
                    orig[0] = (Integer) p.args[0]; orig[1] = (Integer) p.args[2]; orig[2] = (Integer) p.args[3];
                    if (ours == null) return;
                    p.args[0] = orig[0] + ours[0]; p.args[2] = orig[1] + ours[1]; p.args[3] = orig[2] + ours[2];
                } catch (Throwable ignored) { }
            }
        });
        Xp.hookAllMethods(View.class, "setTranslationY", new Xp.Callback() {
            @Override protected void beforeHookedMethod(Xp.Param p) {
                try {
                    if (sShift.isEmpty()) return;
                    float[] st;
                    synchronized (sShift) { st = sShift.get(p.thisObject); }
                    if (st == null || p.args.length != 1 || !(p.args[0] instanceof Float)) return;
                    st[0] = (Float) p.args[0];
                    p.args[0] = st[0] + st[1];
                } catch (Throwable ignored) { }
            }
        });
    }

    private static final Map<View, float[]> sShift = new WeakHashMap<View, float[]>();
    private static final Map<View, int[]> sPad = new WeakHashMap<View, int[]>(), sPadOurs = new WeakHashMap<View, int[]>();
    private static boolean sOurPad;

    private static WeakReference<ViewGroup> sLin, sInner;
    private static WeakReference<View> sBody;

    private static void place(final ViewGroup lin, View inner) {
        if (sLin == null || sLin.get() != lin) sLin = new WeakReference<ViewGroup>(lin);
        if (inner instanceof ViewGroup && (sInner == null || sInner.get() != inner)) sInner = new WeakReference<ViewGroup>((ViewGroup) inner);
        final Geo g = landscape(inner) ? new Geo() : geo();
        View panelV = lin.getParent() instanceof View && ((View) lin.getParent()).getHeight() < inner.getRootView().getHeight() * 0.9f ? (View) lin.getParent() : lin;
        final View panel = panelV;
        final float d = inner.getResources().getDisplayMetrics().density;
        int[] orig;
        synchronized (sPad) { orig = sPad.get(lin); if (orig == null) { orig = new int[] { lin.getPaddingLeft(), lin.getPaddingRight(), lin.getPaddingBottom() }; sPad.put(lin, orig); } }
        int W = lin.getWidth();
        if (W <= 0) return;
        int left = Math.max(0, Math.round(W * (g.cx - g.w / 2f))), right = Math.max(0, Math.round(W * (1f - g.cx - g.w / 2f)));
        final int upPx = Math.round(Math.max(0, g.up) * d);
        final int fl = left, fr = right;
        final int pl = orig[0] + left, pr = orig[1] + right, pb = orig[2] + upPx;
        float down = 0;
        float[] st;
        synchronized (sShift) { st = sShift.get(panel); if (st == null) { st = new float[] { panel.getTranslationY(), 0f }; sShift.put(panel, st); } }
        final float[] shift = st;
        final float extra = down;
        final boolean padChange = lin.getPaddingLeft() != pl || lin.getPaddingRight() != pr || lin.getPaddingBottom() != pb;
        final boolean shiftChange = Math.abs(shift[1] - extra) > 0.5f;
        outline(panel, lin, orig, g, d);
        if (!padChange && !shiftChange) return;
        MAIN.post(new Runnable() { public void run() {
            try {
                if (padChange) {
                    synchronized (sPad) { sPadOurs.put(lin, new int[] { fl, fr, upPx }); }
                    sOurPad = true;
                    try { lin.setPadding(pl, lin.getPaddingTop(), pr, pb); } finally { sOurPad = false; }
                }
                if (shiftChange) { shift[1] = extra; panel.setTranslationY(shift[0]); }
                panel.invalidateOutline();
            } catch (Throwable ignored) { }
        }});
    }

    private static final Map<View, Boolean> sOutlined = new WeakHashMap<View, Boolean>();

    private static void outline(final View panel, final ViewGroup lin, final int[] orig, final Geo g, final float d) {
        final boolean round = on(KEY_KEYS) || !g.plain();
        synchronized (sOutlined) { if (sOutlined.containsKey(panel) || !round) return; sOutlined.put(panel, Boolean.TRUE); }
        MAIN.post(new Runnable() { public void run() {
            try {
                panel.setOutlineProvider(new android.view.ViewOutlineProvider() {
                    @Override public void getOutline(View view, android.graphics.Outline o) {
                        View c = lin; int x = 0, y = 0;
                        while (c != null && c != view) { x += c.getLeft(); y += c.getTop(); c = c.getParent() instanceof View ? (View) c.getParent() : null; }
                        int l = x + lin.getPaddingLeft() - orig[0], r = x + lin.getWidth() - (lin.getPaddingRight() - orig[1]);
                        int b = y + lin.getHeight() - (lin.getPaddingBottom() - orig[2]);
                        boolean full = l <= 0 && r >= view.getWidth() && lin.getPaddingBottom() == orig[2];
                        float rad = (full ? 24 : 20) * d;
                        if (full) o.setRoundRect(0, 0, view.getWidth(), Math.round(view.getHeight() + rad), rad);
                        else o.setRoundRect(Math.max(0, l), 0, Math.min(view.getWidth(), r), Math.min(view.getHeight(), b), rad);
                    }
                });
                panel.setClipToOutline(true);
                panel.invalidateOutline();
            } catch (Throwable t) { Log.w("corners", t); }
        }});
    }

    private static void bodyHeight(ViewGroup g) throws Throwable {
        if (landscape(g)) return;
        if ((sBody == null || sBody.get() != g) && g.getParent() instanceof View && "keyboard_holder".equals(idName((View) g.getParent())) && !"keyboard_header_view_holder".equals(idName(g))) sBody = new WeakReference<View>(g);
        Geo geo = geo();
        if (!(g.getParent() instanceof View) || !"keyboard_holder".equals(idName((View) g.getParent())) || "keyboard_header_view_holder".equals(idName(g))) return;
        float factor = geo.h * rowsFactor(g);
        if (Math.abs(factor - 1f) < 0.005f) return;
        if (sBody == null || sBody.get() != g) sBody = new WeakReference<View>(g);
        int w = g.getMeasuredWidth(), h = g.getMeasuredHeight();
        if (w <= 0 || h <= 0) return;
        int target = Math.round(h * factor);
        for (int i = 0; i < g.getChildCount(); i++) {
            View ch = g.getChildAt(i);
            if (ch.getVisibility() == View.GONE) continue;
            ch.measure(View.MeasureSpec.makeMeasureSpec(Math.max(0, w - g.getPaddingLeft() - g.getPaddingRight()), View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(Math.max(0, target - g.getPaddingTop() - g.getPaddingBottom()), View.MeasureSpec.EXACTLY));
        }
        sSetMeasured.invoke(g, w, target);
    }

    private static String sNumberKey; private static boolean sNumberDe, sNumberSearched; private static long sNumberAt; private static boolean sNumberOn;
    private static int sLettersRows; private static Boolean sNumberLogged;
    private static android.content.SharedPreferences gboardFile(Context c, boolean de) {
        try {
            Context x = de ? (Context) Context.class.getMethod("createDeviceProtectedStorageContext").invoke(c) : c;
            return x.getSharedPreferences(c.getPackageName() + "_preferences", Context.MODE_PRIVATE | 4);
        } catch (Throwable t) { return null; }
    }
    private static String numberKey(android.content.SharedPreferences sp) {
        if (sp == null) return null;
        try {
            Map<String, ?> all = sp.getAll();
            if (all.get("enable_number_row") instanceof Boolean) return "enable_number_row";
            String best = null;
            for (Map.Entry<String, ?> e : all.entrySet()) {
                String k = e.getKey().toLowerCase(java.util.Locale.ROOT);
                if (!(e.getValue() instanceof Boolean) || !k.contains("number_row") || k.contains("_in_") || k.contains("password")) continue;
                if (best == null || e.getKey().length() < best.length()) best = e.getKey();
            }
            return best;
        } catch (Throwable ignored) { }
        return null;
    }
    private static boolean numberRowOn(Context c) {
        long now = android.os.SystemClock.uptimeMillis();
        if (now - sNumberAt < 800) return sNumberOn;
        sNumberAt = now;
        boolean on;
        try {
            if (!sNumberSearched) {
                sNumberSearched = true;
                String k = numberKey(gboardFile(c, true));
                if (k != null) { sNumberKey = k; sNumberDe = true; }
                else { k = numberKey(gboardFile(c, false)); if (k != null) { sNumberKey = k; sNumberDe = false; } }
            }
            if (sNumberKey != null) { android.content.SharedPreferences sp = gboardFile(c, sNumberDe); on = sp != null && sp.getBoolean(sNumberKey, false); }
            else on = sLettersRows == 5;
        } catch (Throwable t) { on = false; }
        sNumberOn = on;
        if (sNumberLogged == null || sNumberLogged != on) {
            sNumberLogged = on;
        }
        return on;
    }
    private static final Pattern ROW_ID = Pattern.compile("^[A-H]\\d\\d$");
    private static final Map<String, Integer> sPageRows = new java.util.HashMap<String, Integer>();
    private static float rowsFactor(final ViewGroup g) {
        boolean[] lettersPage = new boolean[1]; int[] keys = new int[1];
        int named = keyRows(g, lettersPage);
        countKeys(g, keys, 0);
        final String page = named + ":" + keys[0] + ":" + lettersPage[0];
        Integer seen; synchronized (sPageRows) { seen = sPageRows.get(page); }
        int rows = seen != null ? seen : named;
        if (g.getTag(0x7f5e0377) == null) {
            g.setTag(0x7f5e0377, Boolean.TRUE);
            g.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                public void onLayoutChange(View v, int l, int t, int r, int b, int ol, int ot, int or, int ob) {
                    try {
                        boolean[] lp = new boolean[1]; int[] k = new int[1];
                        int nm = keyRows(g, lp); countKeys(g, k, 0);
                        String pg = nm + ":" + k[0] + ":" + lp[0];
                        int real = screenRows(g);
                        if (real < 2) return;
                        Integer before; synchronized (sPageRows) { before = sPageRows.put(pg, real); }
                        if (before == null || before != real) { g.post(new Runnable() { public void run() { g.requestLayout(); } }); return; }
                        rowHeights(g, real);
                    } catch (Throwable ignored) { }
                }
            });
        }
        if (lettersPage[0] && rows > 0) sLettersRows = rows;
        boolean on = numberRowOn(g.getContext());
        float f = 1f;
        if (on && rows == 4) f = sLettersBody > 0 && sNumberRowH > 0 && sNumberRowH < sLettersBody / 2f ? (sLettersBody - sNumberRowH) / sLettersBody : 4f / 5f;
        return f;
    }
    private static float sLetterRow, sBottomRow, sLettersBody, sNumberRowH; private static String sRowsLayoutSaid; private static long sRowsAt; private static int sRowsTries;
    private static final int ROW_ORIG = 0x7f5e0379;
    private static void rowHeights(ViewGroup g, int rows) {
        java.util.List<java.util.List<View>> byRow = rowKeys(g);
        if (byRow.size() != rows || rows < 3) return;
        java.util.List<View> boxes = new java.util.ArrayList<View>();
        for (java.util.List<View> r : byRow) { View b = rowBox(g, r, byRow); if (b == null) { return; } boxes.add(b); }
        boolean on = numberRowOn(g.getContext());
        if (on && rows == 5) {
            float sum = 0; for (int i = 1; i < rows - 1; i++) sum += boxes.get(i).getHeight();
            sLetterRow = sum / (rows - 2); sBottomRow = boxes.get(rows - 1).getHeight();
            float body = g.getHeight(), number = boxes.get(0).getHeight();
            if (body > 0 && number > 0 && (Math.abs(body - sLettersBody) > 1 || Math.abs(number - sNumberRowH) > 1)) { sLettersBody = body; sNumberRowH = number; }
            return;
        }
        if (!on || rows != 4 || sLetterRow <= 0 || sBottomRow <= 0) { restoreRows(g); return; }
        float total = 0; for (View b : boxes) total += b.getHeight();
        if (total <= 0) return;
        float unit = total / (3 * sLetterRow + sBottomRow);
        java.util.Map<View, Float> want = new java.util.LinkedHashMap<View, Float>();
        for (int i = 0; i < rows; i++) {
            View b = boxes.get(i);
            float delta = (i == rows - 1 ? sBottomRow : sLetterRow) * unit - b.getHeight();
            for (View v = b; v != null && v != g; v = v.getParent() instanceof View ? (View) v.getParent() : null) {
                Float w = want.get(v); want.put(v, (w == null ? v.getHeight() : w) + delta);
            }
        }
        boolean changed = false;
        for (java.util.Map.Entry<View, Float> e : want.entrySet()) {
            View v = e.getKey(); float desired = e.getValue();
            if (Math.abs(desired - v.getHeight()) < 1.5f || v.getHeight() <= 0) continue;
            if (!(v.getParent() instanceof android.widget.LinearLayout) || ((android.widget.LinearLayout) v.getParent()).getOrientation() != android.widget.LinearLayout.VERTICAL) continue;
            if (!(v.getLayoutParams() instanceof android.widget.LinearLayout.LayoutParams)) continue;
            android.widget.LinearLayout.LayoutParams lp = (android.widget.LinearLayout.LayoutParams) v.getLayoutParams();
            if (v.getTag(ROW_ORIG) == null) { v.setTag(ROW_ORIG, new float[]{lp.height, lp.weight}); synchronized (sRowsTouched) { sRowsTouched.add(new java.lang.ref.WeakReference<View>(v)); } }
            if (lp.weight > 0) lp.weight = lp.weight * desired / v.getHeight();
            else if (lp.height > 0) lp.height = Math.round(desired);
            else continue;
            v.setLayoutParams(lp); changed = true;
        }
        if (changed) {
            long now = android.os.SystemClock.uptimeMillis();
            if (now - sRowsAt > 1000) { sRowsAt = now; sRowsTries = 0; }
            if (++sRowsTries > 4) { return; }
        } else if (!want.isEmpty()) {
            boolean off = false; for (int i = 0; i < rows; i++) if (Math.abs(boxes.get(i).getHeight() - (i == rows - 1 ? sBottomRow : sLetterRow) * unit) > 3) off = true;
            if (off) sayRows(g, "symbols rows: no holder Pixel Suite can resize");
        }
    }
    private static final java.util.List<java.lang.ref.WeakReference<View>> sRowsTouched = new java.util.ArrayList<java.lang.ref.WeakReference<View>>();
    private static void restoreRows(ViewGroup g) {
        synchronized (sRowsTouched) {
            for (int i = sRowsTouched.size() - 1; i >= 0; i--) {
                View b = sRowsTouched.get(i).get();
                if (b == null) { sRowsTouched.remove(i); continue; }
                if (!isAncestor(g, b)) continue;
                sRowsTouched.remove(i);
                Object t = b.getTag(ROW_ORIG);
                if (!(t instanceof float[]) || !(b.getLayoutParams() instanceof android.widget.LinearLayout.LayoutParams)) continue;
                android.widget.LinearLayout.LayoutParams lp = (android.widget.LinearLayout.LayoutParams) b.getLayoutParams();
                lp.height = Math.round(((float[]) t)[0]); lp.weight = ((float[]) t)[1]; b.setTag(ROW_ORIG, null); b.setLayoutParams(lp);
            }
        }
    }
    private static void sayRows(ViewGroup g, String what) {
        if (what.equals(sRowsLayoutSaid)) return;
        sRowsLayoutSaid = what;
    }
    private static java.util.List<java.util.List<View>> rowKeys(ViewGroup g) {
        java.util.List<View> keys = new java.util.ArrayList<View>(); java.util.List<Integer> mids = new java.util.ArrayList<Integer>();
        keyViews(g, g, keys, mids, 0);
        java.util.List<java.util.List<View>> out = new java.util.ArrayList<java.util.List<View>>();
        if (keys.isEmpty()) return out;
        Integer[] order = new Integer[keys.size()]; for (int i = 0; i < order.length; i++) order[i] = i;
        final java.util.List<Integer> m = mids;
        java.util.Arrays.sort(order, new java.util.Comparator<Integer>() { public int compare(Integer a, Integer b) { return m.get(a) - m.get(b); } });
        float gap = 14 * g.getResources().getDisplayMetrics().density;
        int last = Integer.MIN_VALUE; java.util.List<View> row = null;
        for (int i : order) { if (row == null || m.get(i) - last > gap) { row = new java.util.ArrayList<View>(); out.add(row); } row.add(keys.get(i)); last = m.get(i); }
        return out;
    }
    private static void keyViews(View v, ViewGroup root, java.util.List<View> keys, java.util.List<Integer> mids, int depth) {
        if (depth > 8 || v.getVisibility() != View.VISIBLE) return;
        if (v != root) {
            String n = idName(v);
            if (n != null && (ROW_ID.matcher(n).matches() || (n.startsWith("key_pos_") && !n.startsWith("key_pos_header"))) && v.getHeight() > 0) {
                int y = 0; View c = v;
                while (c != null && c != root) { y += c.getTop(); c = c.getParent() instanceof View ? (View) c.getParent() : null; }
                if (c == root) { keys.add(v); mids.add(y + v.getHeight() / 2); }
                return;
            }
        }
        if (v instanceof ViewGroup) { ViewGroup x = (ViewGroup) v; for (int i = 0; i < x.getChildCount(); i++) keyViews(x.getChildAt(i), root, keys, mids, depth + 1); }
    }
    private static View rowBox(ViewGroup g, java.util.List<View> row, java.util.List<java.util.List<View>> all) {
        java.util.Set<View> others = new java.util.HashSet<View>();
        for (java.util.List<View> r : all) if (r != row) others.addAll(r);
        View best = null;
        for (android.view.ViewParent p = row.get(0).getParent(); p instanceof View && p != g; p = p.getParent()) {
            View a = (View) p; boolean mine = true;
            for (View k : row) if (!isAncestor(a, k)) { mine = false; break; }
            if (!mine) break;
            for (View k : others) if (isAncestor(a, k)) { mine = false; break; }
            if (!mine) break;
            best = a;
        }
        return best;
    }
    private static void countKeys(View v, int[] keys, int depth) {
        if (depth > 8) return;
        String n = idName(v);
        if (n != null && n.startsWith("key_pos_") && !n.startsWith("key_pos_header")) { keys[0]++; return; }
        if (v instanceof ViewGroup) { ViewGroup g = (ViewGroup) v; for (int i = 0; i < g.getChildCount(); i++) if (g.getChildAt(i).getVisibility() != View.GONE) countKeys(g.getChildAt(i), keys, depth + 1); }
    }
    private static int screenRows(ViewGroup g) {
        java.util.List<Integer> mids = new java.util.ArrayList<Integer>();
        mids(g, g, mids, 0);
        if (mids.isEmpty()) return 0;
        java.util.Collections.sort(mids);
        float gap = 14 * g.getResources().getDisplayMetrics().density;
        int rows = 1, last = mids.get(0);
        for (int m : mids) { if (m - last > gap) rows++; last = m; }
        return rows;
    }
    private static void mids(View v, ViewGroup root, java.util.List<Integer> out, int depth) {
        if (depth > 8 || v.getVisibility() != View.VISIBLE) return;
        if (v != root) {
            String n = idName(v);
            if (n != null && (ROW_ID.matcher(n).matches() || (n.startsWith("key_pos_") && !n.startsWith("key_pos_header"))) && v.getHeight() > 0) {
                int y = 0; View c = v;
                while (c != null && c != root) { y += c.getTop(); c = c.getParent() instanceof View ? (View) c.getParent() : null; }
                if (c == root) out.add(y + v.getHeight() / 2);
                return;
            }
        }
        if (v instanceof ViewGroup) { ViewGroup g = (ViewGroup) v; for (int i = 0; i < g.getChildCount(); i++) mids(g.getChildAt(i), root, out, depth + 1); }
    }
    static void rowsReset() { sNumberAt = 0; sNumberSearched = sNumberKey != null; sLettersRows = 0; }
    private static int keyRows(ViewGroup g, boolean[] lettersPage) {
        java.util.Set<Character> letters = new java.util.HashSet<Character>(); boolean[] bottom = new boolean[1];
        keyRows(g, letters, bottom, lettersPage, 0);
        return letters.size() + (bottom[0] ? 1 : 0);
    }
    private static void keyRows(ViewGroup g, java.util.Set<Character> letters, boolean[] bottom, boolean[] lettersPage, int depth) {
        if (depth > 6) return;
        for (int i = 0; i < g.getChildCount(); i++) {
            View v = g.getChildAt(i);
            if (v.getVisibility() == View.GONE) continue;
            String n = idName(v);
            if (n != null && ROW_ID.matcher(n).matches()) letters.add(n.charAt(0));
            else if (n != null && (n.equals("key_pos_space") || n.startsWith("key_pos_switch_to_"))) { bottom[0] = true; if (n.startsWith("key_pos_switch_to_symbol")) lettersPage[0] = true; }
            else if (v instanceof ViewGroup) keyRows((ViewGroup) v, letters, bottom, lettersPage, depth + 1);
        }
    }

    private static boolean sPanelTrack, sPanelSwipe;
    private static float sPanelX, sPanelY;
    private static View sPager;
    private static final Map<View, Object> sPagers = new WeakHashMap<View, Object>();

    private static void installPanelSwipe(ClassLoader cl) throws Throwable {
        final Class<?> pagerClass = cl.loadClass("com.google.android.libraries.inputmethod.accesspoint.widget.AccessPointsPanelViewPager");
        Class<?> kv = cl.loadClass("com.google.android.libraries.inputmethod.widgets.SoftKeyboardView");
        Xp.hookAllMethods(kv, "dispatchTouchEvent", new Xp.Callback() {
            @Override protected void beforeHookedMethod(Xp.Param p) {
                try {
                    if (p.args.length != 1 || !(p.args[0] instanceof MotionEvent)) return;
                    MotionEvent e = (MotionEvent) p.args[0];
                    View kb = (View) p.thisObject;
                    int a = e.getActionMasked();
                    if (a == MotionEvent.ACTION_DOWN) {
                        sPanelTrack = false; sPanelSwipe = false; sPager = null;
                        Object pg;
                        synchronized (sPagers) { pg = sPagers.get(kb); }
                        if (pg == null) { View f = findInstance(kb, pagerClass); pg = f; if (f != null) synchronized (sPagers) { sPagers.put(kb, f); } }
                        if (!(pg instanceof View) || !((View) pg).isShown()) return;
                        sPager = (View) pg; sPanelTrack = true; sPanelX = e.getRawX(); sPanelY = e.getRawY();
                        return;
                    }
                    if (!sPanelTrack || sPager == null) return;
                    if (a == MotionEvent.ACTION_MOVE && !sPanelSwipe) {
                        float dx = Math.abs(e.getRawX() - sPanelX), dy = Math.abs(e.getRawY() - sPanelY);
                        int slop = android.view.ViewConfiguration.get(kb.getContext()).getScaledTouchSlop();
                        if (dx < slop || dx < dy) return;
                        sPanelSwipe = true;
                        MotionEvent cancel = MotionEvent.obtain(e);
                        cancel.setAction(MotionEvent.ACTION_CANCEL);
                        p.args[0] = cancel;
                        return;
                    }
                    if (sPanelSwipe) {
                        toPager(e, a == MotionEvent.ACTION_MOVE ? -1 : a);
                        if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) { sPanelTrack = false; sPanelSwipe = false; }
                        p.setResult(Boolean.TRUE);
                    }
                } catch (Throwable t) { Log.w("panel swipe", t); }
            }
            @Override protected void afterHookedMethod(Xp.Param p) {
                try {
                    if (sPanelSwipe && p.args.length == 1 && p.args[0] instanceof MotionEvent && ((MotionEvent) p.args[0]).getActionMasked() == MotionEvent.ACTION_CANCEL && sPanelTrack)
                        toPager((MotionEvent) p.args[0], MotionEvent.ACTION_DOWN);
                } catch (Throwable ignored) { }
            }
        });
    }

    private static void toPager(MotionEvent e, int action) {
        View pg = sPager;
        if (pg == null) return;
        int[] at = new int[2];
        pg.getLocationOnScreen(at);
        MotionEvent m = MotionEvent.obtain(e.getDownTime(), e.getEventTime(), action < 0 ? e.getActionMasked() : action, e.getRawX() - at[0], e.getRawY() - at[1], e.getMetaState());
        try { pg.dispatchTouchEvent(m); } finally { m.recycle(); }
    }

    private static View sTapItem;
    private static int sTapWhich = -1;
    private static boolean sTapHeld;

    private static final Runnable QS_HOLD = new Runnable() { public void run() {
        View item = sTapItem;
        if (item == null || sTapWhich != 2 || sCustomizing) return;
        sTapWhich = -1;
        sTapHeld = true;
        try { item.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS, android.view.HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING); } catch (Throwable ignored) { }
    }};
    private static float sTapX, sTapY;
    private static final Map<View, View> sBars = new WeakHashMap<View, View>();

    private static void installBarTaps(ClassLoader cl) throws Throwable {
        final Class<?> barClass = cl.loadClass(BAR);
        final Class<?> kv = cl.loadClass("com.google.android.libraries.inputmethod.widgets.SoftKeyboardView");
        Xp.hookAllMethods(kv, "dispatchTouchEvent", new Xp.Callback() {
            @Override protected void beforeHookedMethod(Xp.Param p) {
                try {
                    if (p.args.length != 1 || !(p.args[0] instanceof MotionEvent)) return;
                    MotionEvent e = (MotionEvent) p.args[0];
                    int a = e.getActionMasked();
                    View kb = (View) p.thisObject;
                    if (a == MotionEvent.ACTION_DOWN) {
                        sRouteTo = null;
                        if (toolbarOn() && !sVoice) {
                            View bar = barOnScreen(kb.getRootView(), barClass);
                            if (bar != null && !isAncestor(kb, bar) && inside(bar, e.getRawX(), e.getRawY())) {
                                View owner = owner(bar, kv);
                                if (owner != null && owner != kb) {
                                                                        route(owner, e);
                                    sRouteTo = owner;
                                    p.setResult(Boolean.TRUE);
                                    return;
                                }
                            }
                        }
                    } else if (sRouteTo != null && sRouteTo != kb && !isAncestor(kb, sRouteTo)) {
                        View to = sRouteTo;
                        if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) sRouteTo = null;
                        route(to, e);
                        p.setResult(Boolean.TRUE);
                        return;
                    }
                    if (a == MotionEvent.ACTION_DOWN) {
                        sTapItem = null; sTapWhich = -1; sTapHeld = false;
                        MAIN.removeCallbacks(QS_HOLD);
                        if (!on(KEY_SHORTCUTS) || sVoice) return;
                        View bar;
                        synchronized (sBars) { bar = sBars.get(kb); }
                        if (bar == null || !bar.isAttachedToWindow() || !isAncestor(kb, bar) || !bar.isShown()) {
                            bar = shownBar(kb, barClass);
                            synchronized (sBars) { if (bar != null) sBars.put(kb, bar); else sBars.remove(kb); }
                        }
                        if (!(bar instanceof ViewGroup) || !bar.isShown()) return;
                        View item = itemAt((ViewGroup) bar, e.getRawX(), e.getRawY());
                        int which = item == null ? -1 : ours((ViewGroup) bar, item);
                        if (which < 0) return;
                        sTapItem = item; sTapWhich = which; sTapX = e.getRawX(); sTapY = e.getRawY();
                        if (which == 2 && !sCustomizing) MAIN.postDelayed(QS_HOLD, Math.min(300, android.view.ViewConfiguration.getLongPressTimeout()));
                        if (which == 2 && !sCustomizing) GboardSelectMenu.warm(sIme.get());
                        return;
                    }
                    final View item = sTapItem;
                    if (item == null) return;
                    if (a == MotionEvent.ACTION_MOVE) {
                        int slop = android.view.ViewConfiguration.get(item.getContext()).getScaledTouchSlop() * 2;
                        if (sTapWhich >= 0 && Math.hypot(e.getRawX() - sTapX, e.getRawY() - sTapY) > slop) { sTapWhich = -1; sTapItem = null; MAIN.removeCallbacks(QS_HOLD); }
                    } else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
                        MAIN.removeCallbacks(QS_HOLD);
                        final int w = a == MotionEvent.ACTION_UP ? sTapWhich : -1;
                        final boolean held = sTapHeld;
                        sTapItem = null; sTapWhich = -1; sTapHeld = false;
                        if (w >= 0 || held) {
                            MotionEvent cancel = MotionEvent.obtain(e);
                            cancel.setAction(MotionEvent.ACTION_CANCEL);
                            p.args[0] = cancel;
                            if (w >= 0) MAIN.post(new Runnable() { public void run() { Shortcuts.act(w); } });
                            else if (held && a == MotionEvent.ACTION_UP) MAIN.post(new Runnable() { public void run() { Shortcuts.act(3); } });
                        }
                    }
                } catch (Throwable t) { Log.w("shortcut tap", t); }
            }
            @Override protected void afterHookedMethod(Xp.Param p) {
                try {
                } catch (Throwable ignored) { }
            }
        });
    }

    private static View sRouteTo;

    private static View shownBar(View v, Class<?> type) {
        if (type.isInstance(v)) return v.isShown() && v.getWidth() > 0 && v.getAlpha() > 0.01f ? v : null;
        if (!(v instanceof ViewGroup) || v.getVisibility() != View.VISIBLE) return null;
        ViewGroup g = (ViewGroup) v;
        for (int i = 0; i < g.getChildCount(); i++) { View f = shownBar(g.getChildAt(i), type); if (f != null) return f; }
        return null;
    }

    private static WeakReference<View> sShownBar;

    private static View barOnScreen(View root, Class<?> type) {
        View b = sShownBar == null ? null : sShownBar.get();
        if (b != null && b.isShown() && b.getWidth() > 0 && b.getAlpha() > 0.01f && b.getRootView() == root) return b;
        b = shownBar(root, type);
        sShownBar = b == null ? null : new WeakReference<View>(b);
        return b;
    }

    private static boolean isAncestor(View parent, View child) {
        for (Object c = child; c instanceof View; c = ((View) c).getParent()) if (c == parent) return true;
        return false;
    }

    private static boolean inside(View v, float rx, float ry) {
        int[] at = new int[2];
        v.getLocationOnScreen(at);
        return rx >= at[0] && rx < at[0] + v.getWidth() && ry >= at[1] && ry < at[1] + v.getHeight();
    }

    private static View owner(View bar, Class<?> kv) {
        for (Object c = bar.getParent(); c instanceof View; c = ((View) c).getParent()) if (kv.isInstance(c)) return (View) c;
        return null;
    }

    private static void route(View to, MotionEvent e) {
        int[] at = new int[2];
        to.getLocationOnScreen(at);
        MotionEvent m = MotionEvent.obtain(e);
        m.offsetLocation(e.getRawX() - e.getX() - at[0], e.getRawY() - e.getY() - at[1]);
        try { to.dispatchTouchEvent(m); } finally { m.recycle(); }
    }

    private static View itemAt(ViewGroup bar, float rx, float ry) {
        int[] at = new int[2];
        for (int i = 0; i < bar.getChildCount(); i++) {
            View c = bar.getChildAt(i);
            if (c.getVisibility() != View.VISIBLE || c.getWidth() <= 0) continue;
            c.getLocationOnScreen(at);
            if (rx >= at[0] && rx < at[0] + c.getWidth() && ry >= at[1] && ry < at[1] + c.getHeight()) return c;
        }
        return null;
    }

    private static int ours(ViewGroup bar, View item) {
        try {
            for (Class<?> k = bar.getClass(); k != null && k != ViewGroup.class; k = k.getSuperclass())
                for (Field f : k.getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive()) continue;
                    f.setAccessible(true);
                    Object m = f.get(bar);
                    if (!(m instanceof Map)) continue;
                    for (Object entry : ((Map<?, ?>) m).values()) {
                        if (entry == null || !holds(entry, item)) continue;
                        int w = idIn(entry, 0);
                        if (w >= 0) return w;
                    }
                }
        } catch (Throwable ignored) { }
        CharSequence cd = item.getContentDescription();
        if (cd != null) for (int i = 0; i < Shortcuts.LABELS.length; i++) if (Shortcuts.LABELS[i].contentEquals(cd)) return i;
        return -1;
    }

    private static boolean holds(Object o, View v) throws Throwable {
        for (Field f : o.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) || !View.class.isAssignableFrom(f.getType())) continue;
            f.setAccessible(true);
            if (f.get(o) == v) return true;
        }
        return false;
    }

    private static int idIn(Object o, int depth) throws Throwable {
        for (Class<?> k = o.getClass(); k != null && k != Object.class; k = k.getSuperclass())
            for (Field f : k.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive()) continue;
                f.setAccessible(true);
                Object v = f.get(o);
                if (v instanceof String) { int i = Arrays.asList(Shortcuts.IDS).indexOf(v); if (i >= 0) return i; }
                else if (depth == 0 && v != null && !(v instanceof View) && !v.getClass().getName().startsWith("java") && !v.getClass().getName().startsWith("android")) { int i = idIn(v, 1); if (i >= 0) return i; }
            }
        return -1;
    }

    private static Field sShowingField, sPanelField;

    private static Field boolField(Class<?> c, String name) {
        for (Class<?> k = c; k != null && k != Object.class; k = k.getSuperclass())
            try { Field f = k.getDeclaredField(name); if (f.getType() == boolean.class && !Modifier.isStatic(f.getModifiers())) { f.setAccessible(true); return f; } } catch (NoSuchFieldException ignored) { }
        return null;
    }
    private static boolean sShowingProbed, sShowingLogged;

    static void prepareShowing(InputMethodService ims, ClassLoader cl) throws Throwable {
        final String mgr = Shortcuts.cachedClass(ims, "apmanager", "Can't handle click event: The access points are not showing");
        final String holder = Shortcuts.cachedClass(ims, "holder", HOLDER_STRING);
        if (mgr == null || holder == null) { return; }
        final Class<?> hc = cl.loadClass(holder);
        final Class<?> bar = cl.loadClass(BAR);
        for (Method m : cl.loadClass(mgr).getDeclaredMethods()) {
            Class<?>[] t = m.getParameterTypes();
            if (Modifier.isStatic(m.getModifiers()) || m.getReturnType() != boolean.class || t.length != 1 || t[0].isPrimitive() || t[0].getName().startsWith("java") || t[0].getName().startsWith("android")) continue;
            Xp.hookMethod(m, new Xp.Callback() {
                @Override protected void beforeHookedMethod(Xp.Param p) {
                    try {
                        if (!on(KEY_ROWS) || sVoice) return;
                        Context c = app();
                        if (c != null && c.getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) return;
                        Object h = null;
                        for (Field f : p.thisObject.getClass().getDeclaredFields()) if (f.getType() == hc && !Modifier.isStatic(f.getModifiers())) { f.setAccessible(true); h = f.get(p.thisObject); break; }
                        if (h == null) return;
                        if (!sShowingProbed) { sShowingProbed = true; sShowingField = boolField(h.getClass(), "o"); sPanelField = boolField(h.getClass(), "p"); }
                        if (sShowingField == null || sPanelField == null || sShowingField.getBoolean(h) || sPanelField.getBoolean(h)) return;
                        InputMethodService ims = sIme.get();
                        android.app.Dialog dlg = ims == null ? null : ims.getWindow();
                        View decor = dlg == null || dlg.getWindow() == null ? null : dlg.getWindow().peekDecorView();
                        if (decor == null || barOnScreen(decor, bar) == null) return;
                        sShowingField.setBoolean(h, true);
                                            } catch (Throwable ignored) { }
                }
            });
        }
    }

    static void prepareRowsClicks(InputMethodService ims, ClassLoader cl) throws Throwable {
        String holder = Shortcuts.cachedClass(ims, "holder", HOLDER_STRING);
        if (holder == null) return;
        Method hide = null;
        int n = 0;
        for (Method m : cl.loadClass(holder).getDeclaredMethods()) {
            Class<?>[] t = m.getParameterTypes();
            if (!Modifier.isStatic(m.getModifiers()) && m.getReturnType() == void.class && t.length == 2 && t[0] == boolean.class && t[1] == boolean.class) { hide = m; n++; }
        }
        if (hide == null || n != 1) { return; }
        Xp.hookMethod(hide, new Xp.Callback() {
            @Override protected void beforeHookedMethod(Xp.Param p) {
                try {
                    if (!on(KEY_ROWS)) return;
                    Context c = app();
                    if (c != null && c.getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) return;
                    if (sVoice) {
                        synchronized (sShown) { sHideSnapshot = booleans(p.thisObject); sHideHolder = new WeakReference<Object>(p.thisObject); }
                        return;
                    }
                    if (sClosing) return;
                    Field pf = sPanelField != null ? sPanelField : boolField(p.thisObject.getClass(), "p");
                    if (pf != null && pf.getBoolean(p.thisObject)) return;
                    if (sCustomizing) return;
                    p.setResult(null);
                } catch (Throwable ignored) { }
            }
            @Override protected void afterHookedMethod(Xp.Param p) {
                try {
                    if (!sVoice) return;
                    Map<Field, Boolean> before;
                    synchronized (sShown) { before = sHideSnapshot; sHideSnapshot = null; }
                    if (before == null) return;
                    for (Map.Entry<Field, Boolean> e : before.entrySet()) {
                        if (Boolean.TRUE.equals(e.getValue()) && !e.getKey().getBoolean(p.thisObject)) { synchronized (sShown) { sShown.put(e.getKey(), Boolean.TRUE); } }
                    }
                } catch (Throwable ignored) { }
            }
        });
    }

    private static volatile boolean sClosing, sCustomizing;
    private static final Map<Field, Boolean> sShown = new HashMap<Field, Boolean>();
    private static Map<Field, Boolean> sHideSnapshot;
    private static WeakReference<Object> sHideHolder;

    private static Map<Field, Boolean> booleans(Object o) throws Throwable {
        Map<Field, Boolean> m = new HashMap<Field, Boolean>();
        for (Class<?> k = o.getClass(); k != null && k != Object.class; k = k.getSuperclass())
            for (Field f : k.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) || f.getType() != boolean.class) continue;
                f.setAccessible(true);
                m.put(f, f.getBoolean(o));
            }
        return m;
    }

    private static void reshowBar() {
        try {
            Object holder = sHideHolder == null ? null : sHideHolder.get();
            List<Field> fields;
            synchronized (sShown) { fields = new ArrayList<Field>(sShown.keySet()); }
            if (holder == null || fields.isEmpty()) return;
            for (Field f : fields) f.setBoolean(holder, true);
        } catch (Throwable t) { Log.w("reshow bar", t); }
    }

    private static volatile boolean sVoice;
    static boolean previewBusy() { return sVoice || sCustomizing; }
    private static final Map<View, Boolean> sGlows = new WeakHashMap<View, Boolean>(), sGlowSeen = new WeakHashMap<View, Boolean>();
    private static final Map<View, Integer> sAsked = new WeakHashMap<View, Integer>();
    private static final int TAG_VOICE = 0x7f5e0002;
    private static boolean sOwnVis;

    private static void installVoice(ClassLoader cl) {
        try { installMicTaps(); } catch (Throwable t) { Log.w("mic taps", t); }
        Xp.hookAllMethods(InputMethodService.class, "onStartInputView", new Xp.Callback() {
            @Override protected void afterHookedMethod(Xp.Param p) {
                try {
                    MAIN.post(new Runnable() { public void run() { tags(); } });
                    if (sVoice) return;
                    endShot();
                    micBack();
                    showBars();
                    showVoiceHidden();
                    glowOff();
                    View h = headerHolder();
                    if (h != null && h.getAlpha() < 1f) { h.animate().cancel(); h.setAlpha(1f); }
                } catch (Throwable ignored) { }
            }
        });
        Xp.hookAllMethods(InputMethodService.class, "onConfigurationChanged", new Xp.Callback() {
            @Override protected void beforeHookedMethod(Xp.Param p) {
                try { sUiModeWas = ((InputMethodService) p.thisObject).getResources().getConfiguration().uiMode; } catch (Throwable ignored) { }
            }
            @Override protected void afterHookedMethod(Xp.Param p) {
                try {
                    if (!(p.args.length > 0 && p.args[0] instanceof android.content.res.Configuration)) return;
                    int mode = ((android.content.res.Configuration) p.args[0]).uiMode;
                    if ((mode & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == (sUiModeWas & android.content.res.Configuration.UI_MODE_NIGHT_MASK)) return;
                    MAIN.post(new Runnable() { public void run() { voiceFresh(); } });
                } catch (Throwable t) { Log.w("voice theme", t); }
            }
        });
        final Xp.Callback closing = new Xp.Callback() {
            @Override protected void beforeHookedMethod(Xp.Param p) { sClosing = true; sCustomizing = false; }
            @Override protected void afterHookedMethod(Xp.Param p) { sClosing = false; }
        };
        Xp.hookAllMethods(InputMethodService.class, "onWindowHidden", closing);
        Xp.hookAllMethods(InputMethodService.class, "onFinishInputView", new Xp.Callback() {
            @Override protected void beforeHookedMethod(Xp.Param p) { sClosing = true; }
            @Override protected void afterHookedMethod(Xp.Param p) {
                sClosing = false;
                rowsReset();
                try {
                    micBack(); showBars();
                    showVoiceHidden();
                    glowOff();
                    if (!sVoice) return;
                    View any;
                    synchronized (sGlows) { any = sGlows.isEmpty() ? null : sGlows.keySet().iterator().next(); sGlows.clear(); }
                    if (any != null) glow(any, false); else { sClassicVoice = false; sVoice = false; }
                } catch (Throwable t) { Log.w("voice end", t); }
            }
        });
    }

    private static volatile int sUiModeWas;

    private static void voiceFresh() {
        try {
            synchronized (sGlows) { sGlows.clear(); }
            sClassicVoice = false; sClassicAt = 0; sMicRoute = false; sMicShifted = null;
            voiceOn(false);
            endShot(); micBack(); showBars(); showVoiceHidden(); glowOff();
            View h = headerHolder();
            if (h != null && h.getAlpha() < 1f) { h.animate().cancel(); h.setAlpha(1f); }
        } catch (Throwable t) { Log.w("voice fresh", t); }
    }

    private static void glow(View g, boolean attached) {
        boolean now;
        synchronized (sGlows) {
            if (attached) sGlows.put(g, Boolean.TRUE); else sGlows.remove(g);
            now = !sGlows.isEmpty();
        }
        voiceOn(now);
    }

    private static void voiceViews(View root, boolean on) throws Throwable {
        if (sCl == null) return;
        sOwnVis = true;
        try { voiceViews(root, on, sCl.loadClass(BAR)); } finally { sOwnVis = false; }
    }

    private static void voiceViews(View root, boolean on, Class<?> bar) throws Throwable {
        removeX();
        if (!on) reshowBar();
        List<View> found = new ArrayList<View>();
        collectSides(root, bar, found);
        for (View v : found) {
            Integer asked;
            synchronized (sAsked) { asked = sAsked.get(v); }
            if (bar.isInstance(v)) {
                if (!on) { v.setTag(TAG_VOICE, null); v.requestLayout(); }
                if (on && inHeader(v) && toolbarOn() && v.getVisibility() == View.VISIBLE) { synchronized (sBarsHidden) { sBarsHidden.put(v, Boolean.TRUE); } v.setVisibility(View.INVISIBLE); }
            } else if (asked != null) {
                if (on && menu(v) && on(KEY_HIDE_MENU)) continue;
                v.setVisibility(asked);
            }
        }
        if (on) {
            final View r = root;
            View hh = headerHolder();
            final View scope = hh != null ? hh : r;
            copyLayers(scope, 0, true);
            noInfo(scope, 0);
            for (int ms : new int[] { 80, 250, 600, 1200 }) MAIN.postDelayed(new Runnable() { public void run() { if (sVoice) noInfo(scope, 0); } }, ms);
            if (sShot == null) { micToVoiceItem(); MAIN.postDelayed(new Runnable() { public void run() { if (sVoice) glowOn(); } }, 60); }
            if (sClassicVoice) {

            }
        } else {
            micBack();
            showBars();
            showVoiceHidden();
            if (!sCustomizing) showCustHidden();
            final View r = root; final Class<?> b = bar;
            sideKeysHidden(r, b);
            for (int ms : new int[] { 50, 150, 400 }) MAIN.postDelayed(new Runnable() { public void run() { if (!sVoice) sideKeysHidden(r, b); } }, ms);
        }
    }

    private static final Map<View, Boolean> sBarsHidden = new WeakHashMap<View, Boolean>();
    private static final Map<View, Boolean> sMoved = new WeakHashMap<View, Boolean>();

    private static void showBars() {
        List<View> all;
        synchronized (sBarsHidden) { all = new ArrayList<View>(sBarsHidden.keySet()); sBarsHidden.clear(); }
        sOwnVis = true;
        try { for (View v : all) if (v != null && v.getVisibility() == View.INVISIBLE) v.setVisibility(View.VISIBLE); } finally { sOwnVis = false; }
    }

    private static void micToVoiceItem() {
        try {
            if (!sVoice || sCl == null || !toolbarOn()) return;
            View h = headerHolder();
            if (h == null) return;
            Class<?> bar = sCl.loadClass(BAR);
            View b = findInstance(h, bar);
            if (!(b instanceof ViewGroup) || b.getWidth() <= 0) return;
            View item = voiceItem((ViewGroup) b);
            List<View> mics = new ArrayList<View>();
            powerKeys(h, mics);
            View m = null;
            for (View c : mics) if (c.getVisibility() == View.VISIBLE && c.getWidth() > 0) { m = c; break; }
            if (item == null || m == null) return;
            float d = h.getResources().getDisplayMetrics().density;
            int[] il = new int[2]; item.getLocationOnScreen(il);
            int[] ml = new int[2]; m.getLocationOnScreen(ml);
            float laidLeft = ml[0] - m.getTranslationX();
            float target = Math.max(il[0], 4 * d);
            float tx = target - laidLeft;
            View arrow = arrowBeside(h, m, laidLeft, ml[1]);
            unclip(m, h);
            if (Math.abs(m.getTranslationX() - tx) > 0.5f) m.setTranslationX(tx);
            synchronized (sMoved) { sMoved.put(m, Boolean.TRUE); }
            if (arrow != null) {
                int[] al = new int[2]; arrow.getLocationOnScreen(al);
                float aLaidLeft = al[0] - arrow.getTranslationX();
                float gap = Math.max(0f, laidLeft - (aLaidLeft + arrow.getWidth()));
                float atx = (target + m.getWidth() + gap) - aLaidLeft;
                unclip(arrow, h);
                if (Math.abs(arrow.getTranslationX() - atx) > 0.5f) arrow.setTranslationX(atx);
                synchronized (sMoved) { sMoved.put(arrow, Boolean.TRUE); }
            }
            sMicHost = new WeakReference<View>(h);
        } catch (Throwable ignored) { }
    }

    private static final Map<View, Boolean> sTags = new WeakHashMap<View, Boolean>();
    private static WeakReference<View> sTagWatch = new WeakReference<View>(null);

    private static void tags() {
        try {
            if (!toolbarOn()) return;
            InputMethodService ims = sIme.get();
            android.app.Dialog dlg = ims == null ? null : ims.getWindow();
            View decor = dlg == null || dlg.getWindow() == null ? null : dlg.getWindow().peekDecorView();
            View h = headerHolder();
            if (decor == null || h == null) return;
            customizeCheck(decor, h);
            if (sTagWatch.get() != decor) {
                sTagWatch = new WeakReference<View>(decor);
                decor.getViewTreeObserver().addOnGlobalLayoutListener(new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override public void onGlobalLayout() { tags(); }
                });
            }
            int[] hl = new int[2]; h.getLocationOnScreen(hl);
            List<View> found = new ArrayList<View>();
            findTags(decor, hl[1] - h.getHeight() / 2, hl[1] + h.getHeight(), h.getResources().getDisplayMetrics().density, found, 0);
            if (found.isEmpty()) return;
            List<View> mics = new ArrayList<View>();
            powerKeys(h, mics);
            boolean micShown = false;
            for (View m : mics) if (m.isShown() && m.getWidth() > 0) micShown = true;
            boolean hide = !micShown || sShot != null;
            sOwnVis = true;
            try {
                for (View t : found) {
                    synchronized (sTags) { sTags.put(t, Boolean.TRUE); }
                    int want = hide ? View.INVISIBLE : View.VISIBLE;
                    if (t.getVisibility() != want && (hide || t.getVisibility() == View.INVISIBLE)) t.setVisibility(want);
                }
            } finally { sOwnVis = false; }
        } catch (Throwable ignored) { }
    }

    private static final Map<View, android.graphics.drawable.Drawable> sBarBgs = new WeakHashMap<View, android.graphics.drawable.Drawable>();

    private static long sCustAt;
    private static boolean sCustQueued;

    private static void customizeCheck(final View decor, final View h) {
        long t0 = android.os.SystemClock.uptimeMillis();
        if (sCustomizing && t0 - sCustAt < 300) {
            if (!sCustQueued) { sCustQueued = true; MAIN.postDelayed(new Runnable() { public void run() { sCustQueued = false; customizeCheck(decor, h); } }, 320); }
            return;
        }
        sCustAt = t0;
        try {
            watchCorner(h);
            boolean now = customizeText(decor, 0);
            if (now == sCustomizing && !now) return;
            boolean changed = now != sCustomizing;
            sCustomizing = now;
            Class<?> bar = sCl == null ? null : sCl.loadClass(BAR);
            if (bar == null) return;
            List<View> found = new ArrayList<View>();
            collectSides(h, bar, found);
            sOwnVis = true;
            try {
                for (View v : found) {
                    if (bar.isInstance(v)) {
                        if (now) {
                            for (View t = v; t != null && t != h; t = t.getParent() instanceof View ? (View) t.getParent() : null) {
                                android.graphics.drawable.Drawable bg = t.getBackground();
                                if (bg != null && !(bg instanceof FaceDrawable)) { synchronized (sBarBgs) { if (!sBarBgs.containsKey(t)) sBarBgs.put(t, bg); } t.setBackground(null); }
                                if (t != v) break;
                            }
                        }
                    } else if (changed && menu(v) && on(KEY_HIDE_MENU)) {
                        v.setVisibility(now ? View.VISIBLE : View.GONE);
                    }
                }
                if (now) {
                    if (changed) maskCorner(h, bar);
                    if (changed || sMask == null) { final View fh = h; final Class<?> fb = bar; MAIN.postDelayed(new Runnable() { public void run() { if (sCustomizing && sMask == null) maskCorner(fh, fb); } }, 250); }
                    if (changed) showCustHidden();
                    if (on(KEY_HIDE_RIGHT)) powerKeyOff(decor);

                }
                else { showCustHidden(); unmaskCorner(); }
                if (!now) {
                    List<Map.Entry<View, android.graphics.drawable.Drawable>> all;
                    synchronized (sBarBgs) { all = new ArrayList<Map.Entry<View, android.graphics.drawable.Drawable>>(sBarBgs.entrySet()); sBarBgs.clear(); }
                    for (Map.Entry<View, android.graphics.drawable.Drawable> e : all) if (e.getKey() != null && e.getKey().getBackground() == null) e.getKey().setBackground(e.getValue());
                }
            } finally { sOwnVis = false; }
        } catch (Throwable ignored) { }
    }

    private static final Map<View, Boolean> sCustHidden = new WeakHashMap<View, Boolean>();

    private static void dragIcon(View content) {
        View parent = content.getParent() instanceof View ? (View) content.getParent() : null;
        if (parent == null || !parent.getClass().getName().endsWith("AccessPointDragPopupView")) return;
        String label = null;
        android.widget.ImageView icon = null;
        List<View> all = new ArrayList<View>();
        all.add(content);
        for (int i = 0; i < all.size(); i++) {
            View x = all.get(i);
            if (x instanceof ViewGroup) for (int k = 0; k < ((ViewGroup) x).getChildCount(); k++) all.add(((ViewGroup) x).getChildAt(k));
            if (x instanceof TextView && label == null) { CharSequence t = ((TextView) x).getText(); if (t != null && t.length() > 0) label = t.toString().trim(); }
            if (x instanceof android.widget.ImageView && icon == null) {
                Object tag = x.getTag();
                if (tag instanceof String && ((String) tag).contains("icon")) icon = (android.widget.ImageView) x;
            }
        }
        if (icon == null) for (View x : all) if (x instanceof android.widget.ImageView) { icon = (android.widget.ImageView) x; break; }
        if (label == null || icon == null) return;
        for (int i = 0; i < Shortcuts.LABELS.length; i++) {
            if (!Shortcuts.LABELS[i].equals(label)) continue;
            icon.setImageBitmap(Glyph.draw(content.getContext(), i));
            icon.setVisibility(View.VISIBLE);
            return;
        }
    }

    private static WeakReference<View> sPowerInd = new WeakReference<View>(null), sPowerKey = new WeakReference<View>(null);

    private static boolean powerKeyPart(View v) {
        Object tag = v.getTag();
        if (tag instanceof String && ((String) tag).contains("power-key-customized-state-indicator")) return true;
        return v.getId() != View.NO_ID && "key_pos_header_power_key".equals(idName(v));
    }

    private static void keepPowerKeyOff() {
        if (!sCustomizing || !on(KEY_HIDE_RIGHT)) return;
        View ind = sPowerInd.get(), key = sPowerKey.get();
        boolean was = sOwnVis; sOwnVis = true;
        try {
            if (ind != null && ind.getVisibility() == View.VISIBLE) ind.setVisibility(View.INVISIBLE);
            if (key != null && key.getVisibility() != View.GONE) key.setVisibility(View.GONE);
        } finally { sOwnVis = was; }
    }

    private static void powerKeyOff(View v) {
        if (v == null) return;
        Object tag = v.getTag();
        if (tag instanceof String && ((String) tag).contains("power-key-customized-state-indicator")) sPowerInd = new WeakReference<View>(v);
        if ("key_pos_header_power_key".equals(idName(v))) sPowerKey = new WeakReference<View>(v);
        if (tag instanceof String && ((String) tag).contains("power-key-customized-state-indicator") && v.getVisibility() == View.VISIBLE) {
            boolean was = sOwnVis; sOwnVis = true;
            try { v.setVisibility(View.INVISIBLE); } finally { sOwnVis = was; }
        }
        if ("key_pos_header_power_key".equals(idName(v)) && v.getVisibility() != View.GONE) {
            boolean was = sOwnVis; sOwnVis = true;
            try { v.setVisibility(View.GONE); } finally { sOwnVis = was; }
        }
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) powerKeyOff(((ViewGroup) v).getChildAt(i));
    }

    private static final String HINT_TAG = ".access-point-drag-in-area-hint";
    private static final Map<View, Boolean> sHints = new WeakHashMap<View, Boolean>(), sHintHidden = new WeakHashMap<View, Boolean>();

    private static void installHintWatch() throws Throwable {
        Xp.hookAllMethods(ViewGroup.class, "addView", new Xp.Callback() {
            @Override protected void afterHookedMethod(Xp.Param p) {
                if (p.args.length == 0 || !(p.args[0] instanceof View)) return;
                View v = (View) p.args[0];
                Object tag = v.getTag();
                if (tag instanceof CharSequence && HINT_TAG.contentEquals((CharSequence) tag)) watchHint(v);
            }
        });
    }

    private static void watchHint(final View v) {
        synchronized (sHints) { if (sHints.containsKey(v)) return; sHints.put(v, Boolean.TRUE); }
        final android.view.ViewTreeObserver.OnPreDrawListener pre = new android.view.ViewTreeObserver.OnPreDrawListener() {
            @Override public boolean onPreDraw() { try { hint(v); } catch (Throwable ignored) { } return true; }
        };
        v.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View x) { x.getViewTreeObserver().addOnPreDrawListener(pre); try { hint(x); } catch (Throwable ignored) { } }
            @Override public void onViewDetachedFromWindow(View x) { x.getViewTreeObserver().removeOnPreDrawListener(pre); }
        });
        if (v.isAttachedToWindow()) v.getViewTreeObserver().addOnPreDrawListener(pre);
        try { hint(v); } catch (Throwable ignored) { }
    }

    private static void hint(View v) {
        boolean hid; synchronized (sHintHidden) { hid = sHintHidden.containsKey(v); }
        android.graphics.Rect slot = on(KEY_HIDE_RIGHT) && v.getWidth() > 0 ? powerSlot() : null;
        boolean onSlot = false;
        if (slot != null) {
            int[] at = new int[2]; v.getLocationOnScreen(at);
            onSlot = slot.contains(at[0] + v.getWidth() / 2, at[1] + v.getHeight() / 2);
        }
        boolean was = sOwnVis; sOwnVis = true;
        try {
            if (onSlot) {
                if (v.getVisibility() == View.VISIBLE) v.setVisibility(View.INVISIBLE);
                if (!hid) synchronized (sHintHidden) { sHintHidden.put(v, Boolean.TRUE); }
            } else if (hid && (slot != null || !on(KEY_HIDE_RIGHT))) {
                synchronized (sHintHidden) { sHintHidden.remove(v); }
                if (v.getVisibility() == View.INVISIBLE) v.setVisibility(View.VISIBLE);
            }
        } finally { sOwnVis = was; }
    }

    private static android.graphics.Rect powerSlot() {
        View k = sPowerKey.get();
        if (k == null || !k.isAttachedToWindow()) {
            View h = headerHolder();
            k = h == null ? null : findIdName(h, "key_pos_header_power_key", 0);
            if (k != null) sPowerKey = new WeakReference<View>(k);
        }
        int[] at = new int[2];
        if (k != null && k.isAttachedToWindow() && k.getWidth() > 0 && k.getHeight() > 0) {
            k.getLocationOnScreen(at);
            return new android.graphics.Rect(at[0], at[1], at[0] + k.getWidth(), at[1] + k.getHeight());
        }
        View h = headerHolder();
        if (h == null || h.getWidth() <= 0 || h.getHeight() <= 0) return null;
        h.getLocationOnScreen(at);
        int size = Math.round(h.getHeight() * 1.2f);
        return new android.graphics.Rect(at[0] + h.getWidth() - size, at[1], at[0] + h.getWidth(), at[1] + h.getHeight());
    }

    private static View findIdName(View v, String name, int depth) {
        if (v.getId() != View.NO_ID && name.equals(idName(v))) return v;
        if (depth > 30 || !(v instanceof ViewGroup)) return null;
        ViewGroup g = (ViewGroup) v;
        for (int i = 0; i < g.getChildCount(); i++) { View r = findIdName(g.getChildAt(i), name, depth + 1); if (r != null) return r; }
        return null;
    }

    private static final Map<android.graphics.drawable.Drawable, WeakReference<View>> sOvl = new WeakHashMap<android.graphics.drawable.Drawable, WeakReference<View>>();
    private static final Map<android.graphics.drawable.Drawable, Boolean> sOvlHidden = new WeakHashMap<android.graphics.drawable.Drawable, Boolean>();
    private static WeakReference<View> sCornerHeader = new WeakReference<View>(null);
    private static WeakReference<View> sCornerBar = new WeakReference<View>(null);

    private static void installOverlayWatch() throws Throwable {
        Xp.hookAllMethods(android.view.ViewOverlay.class, "add", new Xp.Callback() {
            @Override protected void afterHookedMethod(Xp.Param p) {
                try {
                    if (p.args.length != 1 || !(p.args[0] instanceof android.graphics.drawable.Drawable)) return;
                    android.graphics.drawable.Drawable d = (android.graphics.drawable.Drawable) p.args[0];
                    if (d == sMask || d instanceof android.graphics.drawable.ColorDrawable) return;
                    Object g = field(p.thisObject, "mOverlayViewGroup");
                    Object host = g == null ? null : field(g, "mHostView");
                    if (!(host instanceof View)) return;
                    synchronized (sOvl) { sOvl.put(d, new WeakReference<View>((View) host)); }
                    hideCorner();
                } catch (Throwable ignored) { }
            }
        });
    }

    private static Object field(Object o, String name) {
        for (Class<?> k = o.getClass(); k != null; k = k.getSuperclass())
            try { Field f = k.getDeclaredField(name); f.setAccessible(true); return f.get(o); } catch (NoSuchFieldException ignored) { } catch (Throwable t) { return null; }
        return null;
    }

    private static void watchCorner(View h) {
        if (h == null || sCornerHeader.get() == h) return;
        sCornerHeader = new WeakReference<View>(h);
        try { if (sCl != null) sCornerBar = new WeakReference<View>(findInstance(h, sCl.loadClass(BAR))); } catch (Throwable ignored) { }
        h.getViewTreeObserver().addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener() {
            public boolean onPreDraw() { hideCorner(); try { keepPowerKeyOff(); } catch (Throwable ignored) { } return true; }
        });
    }

    private static void hideCorner() {
        try {
            synchronized (sOvl) { if (sOvl.isEmpty()) return; }
            View h = sCornerHeader.get();
            if (h == null || !h.isShown() || !on(KEY_ROWS) || !on(KEY_HIDE_MENU) || landscape(h)) return;
            View b = sCornerBar.get();
            if (b == null || !b.isAttachedToWindow()) {
                try { b = sCl == null ? null : findInstance(h, sCl.loadClass(BAR)); } catch (Throwable ignored) { b = null; }
                sCornerBar = new WeakReference<View>(b);
            }
            if (b == null) return;
            int[] hl = new int[2], bl = new int[2], ol = new int[2];
            h.getLocationOnScreen(hl); b.getLocationOnScreen(bl);
            float dn = h.getResources().getDisplayMetrics().density;
            List<Map.Entry<android.graphics.drawable.Drawable, WeakReference<View>>> all;
            synchronized (sOvl) { all = new ArrayList<Map.Entry<android.graphics.drawable.Drawable, WeakReference<View>>>(sOvl.entrySet()); }
            for (Map.Entry<android.graphics.drawable.Drawable, WeakReference<View>> e : all) {
                android.graphics.drawable.Drawable d = e.getKey();
                View host = e.getValue().get();
                if (d == null || host == null || !host.isAttachedToWindow()) continue;
                synchronized (sOvlHidden) { if (sOvlHidden.containsKey(d)) { if (d.getAlpha() != 0) d.setAlpha(0); continue; } }
                android.graphics.Rect r = d.getBounds();
                int w = r.width(), ht = r.height();
                if (w < 20 * dn || w > 72 * dn || Math.abs(w - ht) > Math.max(6, w / 8)) continue;
                host.getLocationOnScreen(ol);
                int left = ol[0] + r.left, top = ol[1] + r.top, bottom = top + ht;
                boolean corner = left < hl[0] + h.getWidth() / 4 && top >= hl[1] - 8 * dn && bottom <= hl[1] + h.getHeight() + 8 * dn;
                boolean outsideBarRow = bottom <= bl[1] + 8 * dn || top >= bl[1] + b.getHeight() - 8 * dn;
                if (!corner || !outsideBarRow) continue;
                synchronized (sOvlHidden) { sOvlHidden.put(d, Boolean.TRUE); }
                d.setAlpha(0);
                            }
        } catch (Throwable ignored) { }
    }

    private static android.graphics.drawable.ColorDrawable sMask;
    private static WeakReference<View> sMaskHost;

    private static void maskCorner(View h, Class<?> bar) {
        try {
            unmaskCorner();
            View b = findInstance(h, bar);
            View root = h.getRootView();
            if (b == null || root == null || h.getWidth() <= 0) return;
            int[] hl = new int[2], bl = new int[2], rl = new int[2];
            h.getLocationOnScreen(hl); b.getLocationOnScreen(bl); root.getLocationOnScreen(rl);
            int rowBottom = bl[1] - hl[1];
            if (rowBottom <= 0) return;
            float d = h.getResources().getDisplayMetrics().density;
            int w = (int) Math.min(h.getWidth() / 4, 64 * d);
            int left = hl[0] - rl[0], top = hl[1] - rl[1];
            int sx = left + Math.min(h.getWidth() - 1, w + (int) (24 * d)), sy = top + Math.max(0, rowBottom / 3);
            android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(1, 1, android.graphics.Bitmap.Config.ARGB_8888);
            android.graphics.Canvas cv = new android.graphics.Canvas(bmp);
            cv.translate(-sx, -sy);
            root.draw(cv);
            int c = bmp.getPixel(0, 0);
            bmp.recycle();
            if (Color.alpha(c) < 200) return;
            android.graphics.drawable.ColorDrawable m = new android.graphics.drawable.ColorDrawable(c | 0xFF000000);
            m.setBounds(left, top, left + w, top + rowBottom);
            root.getOverlay().add(m);
            sMask = m; sMaskHost = new WeakReference<View>(root);
        } catch (Throwable ignored) { }
    }

    private static void unmaskCorner() {
        try {
            View h = sMaskHost == null ? null : sMaskHost.get();
            if (sMask != null && h != null) h.getOverlay().remove(sMask);
            sMask = null;
        } catch (Throwable ignored) { }
    }

    private static void showCustHidden() {
        List<View> back;
        synchronized (sCustHidden) { back = new ArrayList<View>(sCustHidden.keySet()); sCustHidden.clear(); }
        boolean was = sOwnVis; sOwnVis = true;
        try { for (View x : back) if (x != null && x.getVisibility() == View.INVISIBLE) x.setVisibility(View.VISIBLE); } finally { sOwnVis = was; }
    }

    @SuppressWarnings("unchecked")

    private static void customTree(View v, int depth, StringBuilder b) {
        if (depth > 30) return;
        int[] l = new int[2]; v.getLocationOnScreen(l);
        for (int i = 0; i < depth; i++) b.append(' ');
        b.append(v.getClass().getName()).append(" id=").append(idName(v)).append(" vis=").append(v.getVisibility()).append(" a=").append(v.getAlpha())
                .append(" at=").append(l[0]).append(',').append(l[1]).append(' ').append(v.getWidth()).append('x').append(v.getHeight())
                .append(" bg=").append(v.getBackground() == null ? "-" : v.getBackground().getClass().getSimpleName()).append(" tag=").append(v.getTag());
        if (v.getContentDescription() != null) b.append(" cd=\"").append(v.getContentDescription()).append('"');
        if (v instanceof TextView) b.append(" text=\"").append(((TextView) v).getText()).append('"');
        if (v instanceof android.widget.ImageView) { android.graphics.drawable.Drawable d = ((android.widget.ImageView) v).getDrawable(); b.append(" img=").append(d == null ? "-" : d.getClass().getSimpleName()); }
        b.append('\n');
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) customTree(((ViewGroup) v).getChildAt(i), depth + 1, b);
    }

    private static void emptyButtons(View v, Class<?> bar, float d, int depth) {
        if (depth > 16 || v.getVisibility() != View.VISIBLE) return;
        if (bar.isInstance(v) || (v instanceof ViewGroup && findInstance(v, bar) != null)) {
            for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) emptyButtons(((ViewGroup) v).getChildAt(i), bar, d, depth + 1);
            return;
        }
        int w = v.getWidth(), hh = v.getHeight();
        boolean round = w >= 24 * d && w <= 72 * d && hh >= 24 * d && hh <= 72 * d && Math.abs(w - hh) < 12 * d;
        boolean hiddenRight = v.getId() != View.NO_ID && right(v) && on(KEY_HIDE_RIGHT);
        if (hiddenRight || (round && !menu(v) && !hasContent(v, 0) && (v.getBackground() != null || v instanceof android.widget.ImageView || v instanceof ViewGroup))) {
            synchronized (sCustHidden) { sCustHidden.put(v, Boolean.TRUE); }
            v.setVisibility(View.INVISIBLE);
            return;
        }
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) emptyButtons(((ViewGroup) v).getChildAt(i), bar, d, depth + 1);
    }

    private static boolean hasContent(View v, int depth) {
        if (depth > 6 || v.getVisibility() != View.VISIBLE || v.getAlpha() < 0.05f) return false;
        if (v instanceof TextView) { CharSequence c = ((TextView) v).getText(); return c != null && c.toString().trim().length() > 0; }
        if (v instanceof android.widget.ImageView) {
            Object tag = v.getTag();
            if (tag instanceof String && ((String) tag).startsWith(".background-icon")) return false;
            android.graphics.drawable.Drawable dr = ((android.widget.ImageView) v).getDrawable(); return dr != null && dr.getIntrinsicWidth() != 0 && dr.getAlpha() > 0;
        }
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) if (hasContent(((ViewGroup) v).getChildAt(i), depth + 1)) return true;
        return false;
    }

    private static void copyLayers(View v, int depth, boolean hide) {
        if (depth > 12) return;
        if (v.getClass().getName().endsWith("CopyImageSourceView")) {
            if (hide && v.getVisibility() == View.VISIBLE) {
                synchronized (sCustHidden) { sCustHidden.put(v, Boolean.TRUE); }
                boolean was = sOwnVis; sOwnVis = true;
                try { v.setVisibility(View.INVISIBLE); } finally { sOwnVis = was; }
            }
            return;
        }
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) copyLayers(((ViewGroup) v).getChildAt(i), depth + 1, hide);
    }

    private static boolean customizeText(View v, int depth) {
        if (depth > 22 || v.getVisibility() != View.VISIBLE) return false;
        if (v instanceof TextView) {
            CharSequence c = ((TextView) v).getText();
            if (c == null || c.length() < 8) return false;
            String t = c.toString();
            if (t.equals("Hold and drag to customize")) return true;
            try { if (t.equals(v.getResources().getString(0x7f140026))) return v.getWidth() > 0; } catch (Throwable ignored) { }
            return false;
        }
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) if (customizeText(((ViewGroup) v).getChildAt(i), depth + 1)) return true;
        return false;
    }

    private static void findTags(View v, int top, int bottom, float d, List<View> out, int depth) {
        if (depth > 22 || v.getVisibility() == View.GONE) return;
        try { if (sCl != null && (sCl.loadClass(BAR).isInstance(v) || isKey(v) && !(v instanceof TextView))) { if (!(v instanceof ViewGroup) || sCl.loadClass(BAR).isInstance(v)) return; } } catch (Throwable ignored) { }
        if (v instanceof TextView) {
            CharSequence c = ((TextView) v).getText();
            if (c != null && c.toString().matches("[A-Z]{2,3}(-[A-Z]{2,3})?") && v.getWidth() > 0 && v.getWidth() < 56 * d) {
                int[] l = new int[2]; v.getLocationOnScreen(l);
                boolean inPanel = false;
                for (android.view.ViewParent q = v.getParent(); q instanceof View; q = q.getParent()) if (q.getClass().getName().contains("AccessPoint")) { inPanel = true; break; }
                if (!inPanel && l[1] >= top && l[1] <= bottom) {
                    View t = v;
                    while (t.getParent() instanceof ViewGroup) {
                        ViewGroup g = (ViewGroup) t.getParent();
                        if (g.getChildCount() != 1 || g.getWidth() > 64 * d || g.getHeight() > 48 * d || g.getWidth() <= 0) break;
                        try { if (sCl != null && (sCl.loadClass(BAR).isInstance(g) || isKey(g))) break; } catch (Throwable ignored) { }
                        t = g;
                    }
                    out.add(t);
                }
            }
            return;
        }
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) findTags(((ViewGroup) v).getChildAt(i), top, bottom, d, out, depth + 1);
    }

    private static void unclip(View v, View top) {
        for (View c = v; c != top && c.getParent() instanceof ViewGroup; c = (View) c.getParent()) { ((ViewGroup) c.getParent()).setClipChildren(false); ((ViewGroup) c.getParent()).setClipToPadding(false); }
    }

    private static View arrowBeside(View h, View mic, float micLaidLeft, int micTop) {
        List<View> all = new ArrayList<View>();
        buttons(h, mic, all);
        float d = h.getResources().getDisplayMetrics().density;
        View best = null; float bestRight = -1;
        for (View v : all) {
            int[] l = new int[2]; v.getLocationOnScreen(l);
            float laidLeft = l[0] - v.getTranslationX(), right = laidLeft + v.getWidth();
            boolean row = l[1] < micTop + mic.getHeight() && l[1] + v.getHeight() > micTop;
            if (!row || right > micLaidLeft + 2 * d || micLaidLeft - right > 40 * d || v.getWidth() > 72 * d) continue;
            if (right > bestRight) { bestRight = right; best = v; }
        }
        return best;
    }

    private static void buttons(View v, View mic, List<View> out) {
        if (v == mic || v.getVisibility() != View.VISIBLE) return;
        try { if (sCl.loadClass(BAR).isInstance(v)) return; } catch (Throwable ignored) { }
        if ((v.isClickable() || isKey(v)) && v.getWidth() > 0) { out.add(v); return; }
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) buttons(((ViewGroup) v).getChildAt(i), mic, out);
    }

    private static WeakReference<View> sMicHost;
    private static float sMicShift;
    private static boolean sMicRoute;
    private static MotionEvent sMicShifted;

    private static void installMicTaps() {
        Xp.hookAllMethods(ViewGroup.class, "dispatchTouchEvent", new Xp.Callback() {
            @Override protected void beforeHookedMethod(Xp.Param p) {
                try {
                    if (!sVoice && !sMicRoute) return;
                    View host = sMicHost == null ? null : sMicHost.get();
                    if (host == null || p.thisObject != host || !(p.args[0] instanceof MotionEvent)) return;
                    MotionEvent e = (MotionEvent) p.args[0];
                    int a = e.getActionMasked();
                    if (a == MotionEvent.ACTION_DOWN) {
                        sMicRoute = false;
                        List<View> moved;
                        synchronized (sMoved) { moved = new ArrayList<View>(sMoved.keySet()); }
                        int[] hl = new int[2]; host.getLocationOnScreen(hl);
                        for (View m : moved) {
                            if (m == null || m.getVisibility() != View.VISIBLE || m.getTranslationX() == 0f) continue;
                            int[] ml = new int[2]; m.getLocationOnScreen(ml);
                            float x = e.getRawX(), y = e.getRawY();
                            if (x >= ml[0] && x <= ml[0] + m.getWidth() && y >= ml[1] && y <= ml[1] + m.getHeight()) { sMicRoute = true; sMicShift = m.getTranslationX(); break; }
                        }
                    }
                    if (!sMicRoute) return;
                    e.offsetLocation(-sMicShift, 0f);
                    sMicShifted = e;
                } catch (Throwable ignored) { }
            }
            @Override protected void afterHookedMethod(Xp.Param p) {
                try {
                    if (sMicShifted == null || p.args[0] != sMicShifted) return;
                    MotionEvent e = (MotionEvent) p.args[0];
                    sMicShifted = null;
                    e.offsetLocation(sMicShift, 0f);
                    int a = e.getActionMasked();
                    if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) sMicRoute = false;
                } catch (Throwable ignored) { }
            }
        });
    }

    private static void powerKeys(View v, List<View> out) {
        if (v.getId() != View.NO_ID && right(v)) { out.add(v); return; }
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) powerKeys(((ViewGroup) v).getChildAt(i), out);
    }

    private static void micBack() {
        List<View> all;
        synchronized (sMoved) { all = new ArrayList<View>(sMoved.keySet()); sMoved.clear(); }
        for (View v : all) if (v != null) v.setTranslationX(0f);
    }

    private static void sideKeysHidden(View root, Class<?> bar) {
        try {
            List<View> found = new ArrayList<View>();
            collectSides(root, bar, found);
            sOwnVis = true;
            try {
                for (View v : found) {
                    if (bar.isInstance(v) || v.getVisibility() == View.GONE) continue;
                    if ((menu(v) && on(KEY_HIDE_MENU)) || (right(v) && on(KEY_HIDE_RIGHT))) v.setVisibility(View.GONE);
                }
            } finally { sOwnVis = false; }
        } catch (Throwable ignored) { }
    }

    private static boolean infoButton(View v) {
        if (!inHeader(v)) return false;
        if ("key_pos_header_notice".equals(idName(v))) return true;
        CharSequence cd = v.getContentDescription();
        if (cd == null) return false;
        String s = cd.toString().toLowerCase(java.util.Locale.ROOT);
        return s.equals("open more voice options") || s.equals("all voice commands") || s.equals("tips") || s.equals("voice commands");
    }

    private static void noInfo(View v, int depth) {
        if (depth > 16) return;
        if (v.getVisibility() == View.VISIBLE && infoButton(v)) {
            View t = v;
            float dd = v.getResources().getDisplayMetrics().density;
            if (v.getWidth() > 72 * dd || v.getHeight() > 72 * dd || (sCl != null && v instanceof ViewGroup && holdsBar(v))) return;
            sOwnVis = true;
            try { t.setVisibility(View.GONE); } finally { sOwnVis = false; }
            synchronized (sVoiceHidden) { if (!sVoiceHidden.containsKey(t)) sVoiceHidden.put(t, View.VISIBLE); }
            return;
        }
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) noInfo(((ViewGroup) v).getChildAt(i), depth + 1);
    }

    private static boolean holdsBar(View v) {
        try { return findInstance(v, sCl.loadClass(BAR)) != null; } catch (Throwable t) { return true; }
    }

    private static long sFadeAt;

    private static android.graphics.drawable.BitmapDrawable sShot;
    private static WeakReference<ViewGroup> sShotHost;
    private static long sLastLayout, sShotAt;
    private static WeakReference<View> sLayoutWatch = new WeakReference<View>(null);

    private static void headerFade() {
        try {
            if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper() || !toolbarOn()) return;
            long now = android.os.SystemClock.uptimeMillis();
            if (now - sFadeAt < 300) return;
            final View h = headerHolder();
            if (h == null || !h.isLaidOut() || h.getWidth() <= 0 || h.getHeight() <= 0 || !(h.getParent() instanceof ViewGroup)) return;
            sFadeAt = now;
            endShot();
            final ViewGroup parent = (ViewGroup) h.getParent();
            android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(h.getWidth(), h.getHeight(), android.graphics.Bitmap.Config.ARGB_8888);
            h.draw(new android.graphics.Canvas(bmp));
            android.graphics.drawable.BitmapDrawable shot = new android.graphics.drawable.BitmapDrawable(h.getResources(), bmp);
            shot.setBounds(h.getLeft(), h.getTop(), h.getRight(), h.getBottom());
            parent.getOverlay().add(shot);
            sShot = shot; sShotHost = new WeakReference<ViewGroup>(parent); sShotAt = now; sLastLayout = now;
            h.animate().cancel();
            h.setAlpha(0f);
            tags();
            if (sLayoutWatch.get() != h) {
                sLayoutWatch = new WeakReference<View>(h);
                h.getViewTreeObserver().addOnGlobalLayoutListener(new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override public void onGlobalLayout() {
                        sLastLayout = android.os.SystemClock.uptimeMillis();
                        if (sVoice && sShot == null) micToVoiceItem();
                    }
                });
            }
            MAIN.postDelayed(new Runnable() { public void run() { settle(h); } }, 35);
        } catch (Throwable ignored) { endShot(); }
    }

    private static void settle(final View h) {
        long now = android.os.SystemClock.uptimeMillis();
        if (sShot == null) return;
        if (now - sLastLayout < 70 && now - sShotAt < 380) { MAIN.postDelayed(new Runnable() { public void run() { settle(h); } }, 25); return; }
        try {
            if (sVoice) { micToVoiceItem(); glowOn(); }
        } catch (Throwable ignored) { }
        final android.graphics.drawable.BitmapDrawable shot = sShot;
        MAIN.postDelayed(new Runnable() { public void run() { tags(); } }, 160);
        android.animation.ValueAnimator va = android.animation.ValueAnimator.ofFloat(0f, 1f);
        va.setDuration(150);
        va.setInterpolator(new android.view.animation.PathInterpolator(0.2f, 0f, 0f, 1f));
        va.addUpdateListener(new android.animation.ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(android.animation.ValueAnimator a) {
                float f = (Float) a.getAnimatedValue();
                h.setAlpha(f);
                if (shot != null) { shot.setAlpha((int) (255 * (1f - f))); ViewGroup g = sShotHost == null ? null : sShotHost.get(); if (g != null) g.invalidate(); }
            }
        });
        va.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator a) { h.setAlpha(1f); if (sShot == shot) endShot(); }
            @Override public void onAnimationCancel(android.animation.Animator a) { h.setAlpha(1f); if (sShot == shot) endShot(); }
        });
        va.start();
    }

    private static void endShot() {
        try {
            android.graphics.drawable.BitmapDrawable d = sShot;
            sShot = null;
            ViewGroup g = sShotHost == null ? null : sShotHost.get();
            if (d != null && g != null) { g.getOverlay().remove(d); g.invalidate(); }
        } catch (Throwable ignored) { }
    }

    private static View headerHolder() {
        InputMethodService ims = sIme.get();
        android.app.Dialog dlg = ims == null ? null : ims.getWindow();
        View decor = dlg == null || dlg.getWindow() == null ? null : dlg.getWindow().peekDecorView();
        if (decor == null) return null;
        int id = decor.getResources().getIdentifier("keyboard_header_view_holder", "id", decor.getContext().getPackageName());
        return id == 0 ? null : decor.findViewById(id);
    }

    private static final Map<View, Integer> sVoiceHidden = new WeakHashMap<View, Integer>();

    private static View findShownInstance(View v, Class<?> c) {
        if (c.isInstance(v) && v.isShown() && v.getWidth() > 0) return v;
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) { View f = findShownInstance(((ViewGroup) v).getChildAt(i), c); if (f != null) return f; }
        return null;
    }

    private static boolean contains(View outer, View inner) {
        for (View c = inner; c != null; c = c.getParent() instanceof View ? (View) c.getParent() : null) if (c == outer) return true;
        return false;
    }

    private static void hideLeft(View v, View b, int barLeft, int top, int bottom) {
        if (v == b || v.getVisibility() != View.VISIBLE) return;
        if (glowy(v)) return;
        boolean button = v.isClickable() || v.isLongClickable() || (sCl != null && isKey(v));
        if (contains(v, b) || (!button && v instanceof ViewGroup)) {
            if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) hideLeft(((ViewGroup) v).getChildAt(i), b, barLeft, top, bottom);
            return;
        }
        if (!button) return;
        if (v.getWidth() <= 0 || v.getHeight() <= 0) return;
        int[] l = new int[2]; v.getLocationOnScreen(l);
        float d = v.getResources().getDisplayMetrics().density;
        boolean sameRow = l[1] < bottom && l[1] + v.getHeight() > top;
        if (!sameRow || l[0] + v.getWidth() > barLeft + 2 * d) return;
        synchronized (sVoiceHidden) { if (!sVoiceHidden.containsKey(v)) sVoiceHidden.put(v, v.getVisibility()); }
        sOwnVis = true;
        try { v.setVisibility(View.GONE); } finally { sOwnVis = false; }
    }

    private static boolean glowy(View v) {
        String n = v.getClass().getName().toLowerCase(java.util.Locale.ROOT);
        if (n.contains("glow") || n.contains("lottie") || n.contains("animation") || v instanceof android.view.SurfaceView || v instanceof android.view.TextureView) return true;
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) if (glowy(((ViewGroup) v).getChildAt(i))) return true;
        return false;
    }

    private static boolean isKey(View v) {
        try { return sCl.loadClass(SOFT_KEY).isInstance(v); } catch (Throwable t) { return false; }
    }

    private static Glow sGlowDr;
    private static android.animation.ValueAnimator sGlowAnim;
    private static WeakReference<View> sGlowHost, sGlowHeader;

    static final class Glow extends android.graphics.drawable.Drawable {
        final android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        final int peak;
        float edge = 0.7f;
        int alpha = 255, lastW = -1, lastH = -1; float lastEdge = -1;
        Glow(int peak) { this.peak = peak; }
        @Override public void draw(android.graphics.Canvas c) {
            android.graphics.Rect r = getBounds();
            if (r.width() <= 0 || r.height() <= 0) return;
            if (r.width() != lastW || r.height() != lastH || edge != lastEdge) {
                lastW = r.width(); lastH = r.height(); lastEdge = edge;
                int clear = peak & 0x00FFFFFF, a = peak >>> 24;
                int mid = clear | ((a * 45 / 100) << 24), low = clear | ((a * 18 / 100) << 24);
                float e = Math.max(0.3f, Math.min(1f, edge));
                float[] at = e >= 0.999f ? new float[] { 0f, 0.55f, 0.85f, 0.94f, 1f } : new float[] { 0f, e * 0.6f, e, e + (1 - e) * 0.35f, 1f };
                int[] col = { clear, mid, peak, low, clear };
                paint.setShader(new android.graphics.LinearGradient(0, 0, 0, r.height(), col, at, android.graphics.Shader.TileMode.CLAMP));
            }
            paint.setAlpha(alpha);
            c.save();
            c.translate(r.left, r.top);
            c.drawRect(0, 0, r.width(), r.height(), paint);
            c.restore();
        }
        @Override public void setAlpha(int a) { alpha = a; invalidateSelf(); }
        @Override public void setColorFilter(android.graphics.ColorFilter cf) { }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }

    private static void glowOn() {
        try {
            boolean gboardGlow;
            synchronized (sGlows) { gboardGlow = !sGlows.isEmpty(); }
            if (!sVoice || gboardGlow || sGlowDr != null) return;
            final View h = headerHolder();
            if (h == null || h.getWidth() <= 0) return;
            boolean dark = sDarkKeys != null ? sDarkKeys : (h.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES;
            final View host = glowHost(h);
            final Glow g = new Glow(dark ? 0x803D7BFF : 0x662F6BFF);
            placeGlow(g, h, host);
            host.getOverlay().add(g);
            sGlowDr = g; sGlowHost = new WeakReference<View>(host); sGlowHeader = new WeakReference<View>(h);
            g.setAlpha(0);
            android.animation.ValueAnimator va = android.animation.ValueAnimator.ofFloat(0f, 1f);
            va.setDuration(1400);
            va.setRepeatCount(android.animation.ValueAnimator.INFINITE);
            va.setRepeatMode(android.animation.ValueAnimator.REVERSE);
            va.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
            final long t0 = android.os.SystemClock.uptimeMillis();
            va.addUpdateListener(new android.animation.ValueAnimator.AnimatorUpdateListener() {
                @Override public void onAnimationUpdate(android.animation.ValueAnimator an) {
                    Glow d = sGlowDr;
                    if (d == null) { an.cancel(); return; }
                    float in = Math.min(1f, (android.os.SystemClock.uptimeMillis() - t0) / 300f);
                    d.setAlpha((int) (255 * in * (0.55f + 0.45f * (Float) an.getAnimatedValue())));
                    placeGlow(d, h, host);
                    host.invalidate();
                }
            });
            sGlowAnim = va;
            va.start();
        } catch (Throwable ignored) { }
    }

    private static View glowHost(View h) {
        float dp = h.getResources().getDisplayMetrics().density;
        int[] hl = new int[2]; h.getLocationInWindow(hl);
        int bottom = hl[1] + h.getHeight();
        android.view.ViewParent p = h.getParent();
        for (int i = 0; i < 4 && p instanceof View; i++, p = p.getParent()) {
            View v = (View) p;
            int[] vl = new int[2]; v.getLocationInWindow(vl);
            if (vl[1] + v.getHeight() - bottom >= 24 * dp && vl[1] <= hl[1]) return v;
        }
        return h;
    }

    private static void placeGlow(Glow g, View h, View host) {
        int top = (int) (h.getHeight() * 0.35f);
        if (host == h) { g.edge = 1f; g.setBounds(0, top, h.getWidth(), h.getHeight()); return; }
        int[] hl = new int[2], vl = new int[2];
        h.getLocationInWindow(hl); host.getLocationInWindow(vl);
        int x = hl[0] - vl[0], y = hl[1] - vl[1];
        int tail = Math.min((int) (40 * h.getResources().getDisplayMetrics().density), host.getHeight() - (y + h.getHeight()));
        if (tail <= 0) { g.edge = 1f; g.setBounds(x, y + top, x + h.getWidth(), y + h.getHeight()); return; }
        int t = y + top, b = y + h.getHeight() + tail;
        g.edge = (float) (h.getHeight() - top) / (b - t);
        g.setBounds(x, t, x + h.getWidth(), b);
    }

    private static void glowOff() {
        try {
            Glow d = sGlowDr;
            sGlowDr = null;
            if (sGlowAnim != null) { sGlowAnim.cancel(); sGlowAnim = null; }
            View h = sGlowHost == null ? null : sGlowHost.get();
            if (d != null && h != null) { h.getOverlay().remove(d); h.invalidate(); }
        } catch (Throwable ignored) { }
    }

    private static void dumpTree(View v, int depth, StringBuilder b) {
        if (depth > 24) return;
        int[] l = new int[2]; v.getLocationOnScreen(l);
        for (int i = 0; i < depth; i++) b.append("  ");
        b.append(v.getClass().getName()).append(" id=").append(idName(v)).append(" vis=").append(v.getVisibility())
                .append(" click=").append(v.isClickable()).append(" alpha=").append(v.getAlpha())
                .append(" at=").append(l[0]).append(',').append(l[1]).append(' ').append(v.getWidth()).append('x').append(v.getHeight());
        CharSequence cd = v.getContentDescription();
        if (cd != null) b.append(" cd=\"").append(cd).append('"');
        if (v instanceof TextView) b.append(" text=\"").append(((TextView) v).getText()).append('"');
        b.append('\n');
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) dumpTree(((ViewGroup) v).getChildAt(i), depth + 1, b);
    }

    private static void showVoiceHidden() {
        List<Map.Entry<View, Integer>> all;
        synchronized (sVoiceHidden) { all = new ArrayList<Map.Entry<View, Integer>>(sVoiceHidden.entrySet()); sVoiceHidden.clear(); }
        sOwnVis = true;
        try { for (Map.Entry<View, Integer> e : all) if (e.getKey() != null) e.getKey().setVisibility(e.getValue()); } finally { sOwnVis = false; }
    }

    private static VoiceX sVoiceX;

    private static View voiceItem(ViewGroup bar) {
        View first = null;
        for (int i = 0; i < bar.getChildCount(); i++) {
            View c = bar.getChildAt(i);
            if (c.getWidth() <= 0 || c.getVisibility() == View.GONE) continue;
            if (first == null || absLeft(c) < absLeft(first)) first = c;
            if (itemIs(bar, c, "voice")) return c;
            CharSequence cd = c.getContentDescription();
            if (cd != null && cd.toString().toLowerCase(java.util.Locale.ROOT).contains("voice")) return c;
        }
        return first;
    }

    private static boolean itemIs(ViewGroup bar, View item, String word) {
        try {
            for (Class<?> k = bar.getClass(); k != null && k != ViewGroup.class; k = k.getSuperclass())
                for (Field f : k.getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive()) continue;
                    f.setAccessible(true);
                    Object m = f.get(bar);
                    if (!(m instanceof Map)) continue;
                    for (Object entry : ((Map<?, ?>) m).values()) if (entry != null && holds(entry, item) && hasWord(entry, word, 0)) return true;
                }
        } catch (Throwable ignored) { }
        return false;
    }

    private static boolean hasWord(Object o, String word, int depth) throws Throwable {
        for (Class<?> k = o.getClass(); k != null && k != Object.class; k = k.getSuperclass())
            for (Field f : k.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive()) continue;
                f.setAccessible(true);
                Object v = f.get(o);
                if (v instanceof String) { String sv = (String) v; if (sv.matches("[a-z0-9_]+") && sv.contains(word)) return true; }
                else if (depth == 0 && v != null && !(v instanceof View) && !v.getClass().getName().startsWith("java") && !v.getClass().getName().startsWith("android") && hasWord(v, word, 1)) return true;
            }
        return false;
    }

    private static android.widget.PopupWindow sXWindow;

    private static View findId(View v, String name) {
        if (name.equals(idName(v))) return v;
        if (!(v instanceof ViewGroup)) return null;
        ViewGroup g = (ViewGroup) v;
        for (int i = 0; i < g.getChildCount(); i++) { View f = findId(g.getChildAt(i), name); if (f != null) return f; }
        return null;
    }

    private static View shownOrLaidBar(View v, Class<?> type) {
        if (type.isInstance(v)) return v.getWidth() > 0 && v.isAttachedToWindow() && inHeader(v) ? v : null;
        if (!(v instanceof ViewGroup)) return null;
        ViewGroup g = (ViewGroup) v;
        for (int i = 0; i < g.getChildCount(); i++) { View f = shownOrLaidBar(g.getChildAt(i), type); if (f != null) return f; }
        return null;
    }

    private static void removeX() {
        final VoiceX x = sVoiceX;
        final android.widget.PopupWindow w = sXWindow;
        sVoiceX = null;
        sXWindow = null;
        if (x != null) x.animate().cancel();
        if (w != null) { try { w.dismiss(); } catch (Throwable ignored) { } }
        else if (x != null && x.getParent() instanceof ViewGroup) ((ViewGroup) x.getParent()).removeView(x);
    }

    private static void stopVoice() {
        if (stopRambler()) { MAIN.postDelayed(new Runnable() { public void run() { if (sVoice) voiceOn(false); } }, 400); return; }
        if (stopVoiceNga()) { return; }
        sendEvent(-10179);
        MAIN.postDelayed(new Runnable() { public void run() { if (sVoice) sendEvent(-10066); } }, 350);
    }

    private static Method sDispatch, sEventOf;
    private static Constructor<?> sKeyData;

    private static boolean sendEvent(int code) {
        try {
            InputMethodService ims = sIme.get();
            if (ims == null || !dispatchReady(ims)) return false;
            Object keyData = sKeyData.newInstance(code, null, "", Integer.MAX_VALUE);
            Object event = sEventOf.invoke(null, keyData);
            sDispatch.invoke(ims, event);
            return true;
        } catch (Throwable t) { Log.w("voice stop", t); return false; }
    }

    private static boolean dispatchReady(InputMethodService ims) {
        try {
            if (ims == null || sCl == null) return false;
            if (sDispatch == null) {
                Class<?> action = sCl.loadClass("com.google.android.libraries.inputmethod.metadata.ActionDef");
                Class<?> data = null;
                for (Constructor<?> k : action.getDeclaredConstructors()) {
                    Class<?>[] t = k.getParameterTypes();
                    if (t.length >= 10 && t[1].isArray() && !t[1].getComponentType().isPrimitive()) data = t[1].getComponentType();
                }
                if (data == null) return false;
                for (Constructor<?> k : data.getDeclaredConstructors()) {
                    Class<?>[] t = k.getParameterTypes();
                    if (t.length == 4 && t[0] == int.class && t[2] == Object.class && t[3] == int.class) { k.setAccessible(true); sKeyData = k; }
                }
                if (sKeyData == null) return false;
                for (Class<?> c = ims.getClass(); c != null && c != InputMethodService.class && sDispatch == null; c = c.getSuperclass())
                    for (Method m : c.getDeclaredMethods()) {
                        Class<?>[] t = m.getParameterTypes();
                        if (Modifier.isStatic(m.getModifiers()) || m.getReturnType() != void.class || t.length != 1 || t[0].isPrimitive() || t[0].getName().startsWith("java") || t[0].getName().startsWith("android")) continue;
                        Method of = null;
                        for (Method f : t[0].getDeclaredMethods()) {
                            Class<?>[] ft = f.getParameterTypes();
                            if (Modifier.isStatic(f.getModifiers()) && f.getReturnType() == t[0] && ft.length == 1 && ft[0] == data) { of = f; break; }
                        }
                        if (of == null) continue;
                        m.setAccessible(true); of.setAccessible(true);
                        sDispatch = m; sEventOf = of;
                        break;
                    }
                if (sDispatch == null) return false;
            }
            return true;
        } catch (Throwable t) { Log.w("voice events", t); return false; }
    }

    static final class VoiceX extends View {
        final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), line = new Paint(Paint.ANTI_ALIAS_FLAG), shade = new Paint(Paint.ANTI_ALIAS_FLAG);
        boolean down;

        VoiceX(Context c) {
            super(c);
            line.setStyle(Paint.Style.STROKE);
            line.setStrokeCap(Paint.Cap.ROUND);
            setContentDescription(I18n.cs("Stop voice typing"));
            setClickable(true);
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    press(true);
                    try { performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP); } catch (Throwable ignored) { }
                    return true;
                case MotionEvent.ACTION_UP:
                    press(false);
                    if (e.getX() >= -getWidth() * 0.3f && e.getX() <= getWidth() * 1.3f && e.getY() >= -getHeight() * 0.3f && e.getY() <= getHeight() * 1.3f) {
                        try { playSoundEffect(android.view.SoundEffectConstants.CLICK); } catch (Throwable ignored) { }
                        MAIN.post(new Runnable() { public void run() { stopVoice(); } });
                    }
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    press(false);
                    return true;
            }
            return true;
        }

        void press(boolean p) { if (down == p) return; down = p; animate().scaleX(p ? 0.9f : 1f).scaleY(p ? 0.9f : 1f).setDuration(90).start(); invalidate(); }

        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), d = getResources().getDisplayMetrics().density, r = Math.min(w, h) / 2f - 1.5f * d;
            boolean dark = sDarkKeys != null ? sDarkKeys : (getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES;
            boolean black = dark && superDark(getContext());
            int bg = dark ? (black ? (down ? 0xFF48484A : 0xFF2C2C2E) : (down ? 0xFF6B6B6E : 0xFF464648)) : (down ? 0xFFD6D8DD : 0xFFFFFFFF);
            if (!dark) { shade.setColor(0x40898A8D); c.drawCircle(w / 2f, h / 2f + 1.2f * d, r, shade); }
            fill.setColor(bg);
            c.drawCircle(w / 2f, h / 2f, r, fill);
            line.setColor(dark ? 0xFFF2F2F7 : 0xFF3C4043);
            line.setStrokeWidth(2.2f * d);
            float a = r * 0.42f;
            c.drawLine(w / 2f - a, h / 2f - a, w / 2f + a, h / 2f + a, line);
            c.drawLine(w / 2f + a, h / 2f - a, w / 2f - a, h / 2f + a, line);
        }
    }

    private static void collectSides(View v, Class<?> bar, List<View> out) {
        if (bar.isInstance(v)) { out.add(v); return; }
        if (v.getId() != View.NO_ID && (right(v) || menu(v))) out.add(v);
        if (!(v instanceof ViewGroup)) return;
        ViewGroup g = (ViewGroup) v;
        for (int i = 0; i < g.getChildCount(); i++) collectSides(g.getChildAt(i), bar, out);
    }

    private static Class<?> sKeyDef, sActionDef;
    private static Field sDefActions, sActData, sActType, sActLabels, sActIcons, sActDesc, sDataText;
    private static Object sHamzaData, sHamzaType, sLongTemplate, sLongOne, sPressType;
    private static Class<?> sList;
    private static Method sListOf;
    private static String sNoLabel;
    private static final List<Field> sDefLists = new ArrayList<Field>(), sActLists = new ArrayList<Field>();
    private static final List<Field> sDataTexts = new ArrayList<Field>();
    private static volatile boolean sWawDone;
    private static final Map<Object, Boolean> sWawDefs = new WeakHashMap<Object, Boolean>();
    private static final Map<View, Boolean> sHiddenKeys = new WeakHashMap<View, Boolean>();
    private static WeakReference<View> sHamzaKey;
    private static volatile float sDelRatio;

    static void prepareArabic(ClassLoader cl) throws Throwable {
        sKeyDef = cl.loadClass("com.google.android.libraries.inputmethod.metadata.SoftKeyDef");
        sActionDef = cl.loadClass("com.google.android.libraries.inputmethod.metadata.ActionDef");
        for (Field f : sKeyDef.getDeclaredFields()) if (!Modifier.isStatic(f.getModifiers()) && f.getType().isArray() && f.getType().getComponentType() == sActionDef) sDefActions = f;
        Class<?> type = null, data = null, list = null;
        for (Constructor<?> k : sActionDef.getDeclaredConstructors()) {
            Class<?>[] t = k.getParameterTypes();
            if (t.length < 10) continue;
            if (t[1].isArray() && !t[1].getComponentType().isPrimitive()) { type = t[0]; data = t[1].getComponentType(); }
            else if (data == null && List.class.isAssignableFrom(t[1])) { type = t[0]; list = t[1]; }
        }
        if (type == null) { return; }
        if (sDefActions != null && data != null) {
            for (Field f : sActionDef.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                if (f.getType() == type) sActType = f;
                else if (f.getType().isArray() && f.getType().getComponentType() == data) sActData = f;
                else if (f.getType() == String[].class) sActLabels = f;
                else if (f.getType() == int[].class) sActIcons = f;
                else if (f.getType() == String.class) sActDesc = f;
            }
            for (Field f : data.getDeclaredFields()) if (!Modifier.isStatic(f.getModifiers()) && f.getType() == Object.class) { sDataText = f; f.setAccessible(true); sDataTexts.add(f); }
            if (sActType == null || sActData == null || sActLabels == null || sDataText == null) { return; }
        } else if (list != null) {
            sList = list;
            for (Field f : sKeyDef.getDeclaredFields()) if (!Modifier.isStatic(f.getModifiers()) && f.getType() == list) { f.setAccessible(true); sDefLists.add(f); }
            for (Field f : sActionDef.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) {
                    if (f.getType() == String.class && sNoLabel == null) try { f.setAccessible(true); Object v = f.get(null); if (v instanceof String) sNoLabel = (String) v; } catch (Throwable ignored) { }
                    continue;
                }
                if (f.getType() == type) sActType = f;
                else if (f.getType() == list) { f.setAccessible(true); sActLists.add(f); }
                else if (f.getType() == int[].class) sActIcons = f;
                else if (f.getType() == String.class) sActDesc = f;
            }
            for (Method m : list.getDeclaredMethods()) {
                Class<?>[] t = m.getParameterTypes();
                if (Modifier.isStatic(m.getModifiers()) && Modifier.isPublic(m.getModifiers()) && t.length == 1 && t[0] == java.util.Collection.class && m.getReturnType() == list) { m.setAccessible(true); sListOf = m; }
            }
            if (sDefLists.isEmpty() || sActLists.size() < 2 || sActType == null || sListOf == null) { return; }
        } else { return; }
        for (Field f : new Field[] { sDefActions, sActType, sActData, sActLabels, sActIcons, sActDesc, sDataText }) if (f != null) f.setAccessible(true);
        Method bind = null;
        Class<?> key = cl.loadClass(SOFT_KEY);
        for (Method m : key.getDeclaredMethods()) {
            Class<?>[] t = m.getParameterTypes();
            if (t.length == 2 && t[0] == sKeyDef && t[1] == long.class) bind = m;
        }
        if (bind == null) return;
        bind.setAccessible(true);
        sBind = bind;
        Xp.hookMethod(bind, new Xp.Callback() {
            @Override protected void beforeHookedMethod(Xp.Param p) {
                if (p.args[0] == null) return;
                View view = (View) p.thisObject;
                synchronized (sLastDef) { sLastDef.put(view, p.args[0]); }
                try { if (on(KEY_KEYS)) sawKey(view, p.args[0], p.args[1]); } catch (Throwable t) { Log.w("arabic key", t); }
                try { if (on(KEY_KEYS) && ("key_pos_del".equals(idName(view)) || actionKey(view))) ltrNow(view); } catch (Throwable t) { Log.w("ltr key", t); }
                try { if (on(KEY_HIDE_MENU)) { Object c = menuKeys(view, p.args[0], p.args[1]); if (c != null) p.args[0] = c; } } catch (Throwable t) { Log.w("menu on comma", t); }
                try { if (on(KEY_KEYS)) symbolKey(view, p.args[0]); } catch (Throwable t) { Log.w("symbols", t); }
            }
            @Override protected void afterHookedMethod(Xp.Param p) {
                try { if (on(KEY_KEYS)) abcFace((View) p.thisObject); } catch (Throwable ignored) { }
            }
        });
    }

    private static Object[] actions(Object def) throws Throwable {
        if (sList == null) { Object a = sDefActions.get(def); return a instanceof Object[] ? (Object[]) a : new Object[0]; }
        Field f = sDefActions;
        if (f == null) {
            for (Field c : sDefLists) { Object v = c.get(def); if (v instanceof List && !((List<?>) v).isEmpty() && sActionDef.isInstance(((List<?>) v).get(0))) { sDefActions = f = c; break; } }
            if (f == null) return new Object[0];
        }
        Object v = f.get(def);
        return v instanceof List ? ((List<?>) v).toArray() : new Object[0];
    }
    private static Object listOf(Object[] items) throws Throwable { return sListOf.invoke(null, java.util.Arrays.asList(items)); }
    private static void setActions(Object def, Object[] acts) throws Throwable {
        Field f = sDefActions;
        if (sList == null) { Object typed = java.lang.reflect.Array.newInstance(sActionDef, acts.length); System.arraycopy(acts, 0, typed, 0, acts.length); f.set(def, typed); return; }
        if (f == null) { actions(def); f = sDefActions; }
        if (f != null) f.set(def, listOf(acts));
    }
    private static boolean actFields(Object action) throws Throwable {
        if (sActData != null) return true;
        if (sList == null) return false;
        for (Field c : sActLists) {
            Object v = c.get(action);
            if (!(v instanceof List) || ((List<?>) v).isEmpty() || ((List<?>) v).get(0) instanceof CharSequence) continue;
            for (Field o : sActLists) if (o != c) { sActLabels = o; break; }
            sActData = c;
            return true;
        }
        return false;
    }
    private static Object[] datas(Object action) throws Throwable {
        Field f = sActData;
        if (sList == null) { Object d = f.get(action); return d instanceof Object[] ? (Object[]) d : null; }
        if (!actFields(action)) return null;
        f = sActData;
        Object v = f.get(action);
        return v instanceof List ? ((List<?>) v).toArray() : null;
    }
    private static Object[] newDatas(int n) {
        Field f = sActData;
        return sList == null ? (Object[]) java.lang.reflect.Array.newInstance(f.getType().getComponentType(), n) : new Object[n];
    }
    private static void setDatas(Object action, Object[] d) throws Throwable {
        Field f = sActData;
        if (sList == null) { f.set(action, d); return; }
        if (f != null) f.set(action, listOf(d));
    }
    private static String[] labelsOf(Object action) throws Throwable {
        Field f = sActLabels;
        if (sList == null) { Object l = f.get(action); return l instanceof String[] ? (String[]) l : null; }
        if (!actFields(action)) return null;
        f = sActLabels;
        Object v = f == null ? null : f.get(action);
        if (!(v instanceof List) || ((List<?>) v).isEmpty()) return null;
        Object[] in = ((List<?>) v).toArray();
        String[] out = new String[in.length];
        for (int k = 0; k < in.length; k++) out[k] = in[k] == sNoLabel || !(in[k] instanceof String) ? null : (String) in[k];
        return out;
    }
    private static void setLabels(Object action, String[] l) throws Throwable {
        Field f = sActLabels;
        if (sList == null) { f.set(action, l); return; }
        if (f == null) return;
        Object[] in = new Object[l == null ? 0 : l.length];
        for (int k = 0; k < in.length; k++) in[k] = l[k] != null ? l[k] : sNoLabel != null ? sNoLabel : "";
        f.set(action, listOf(in));
    }

    private static String text(Object action) throws Throwable {
        Object d = datas(action);
        if (!(d instanceof Object[]) || ((Object[]) d).length == 0 || ((Object[]) d)[0] == null) return null;
        return dataText(((Object[]) d)[0]);
    }

    private static String dataText(Object data) {
        if (data == null) return null;
        String code = null;
        if (sDataTexts.isEmpty()) synchronized (sDataTexts) {
            if (sDataTexts.isEmpty()) for (Field f : data.getClass().getDeclaredFields()) if (!Modifier.isStatic(f.getModifiers()) && f.getType() == Object.class) { f.setAccessible(true); sDataTexts.add(f); }
        }
        for (Field f : sDataTexts) {
            try {
                Object v = f.get(data);
                if (v instanceof CharSequence && ((CharSequence) v).length() > 0) return v.toString();
                if (code == null && v instanceof Integer && (Integer) v > 0x20 && Character.isValidCodePoint((Integer) v)) code = new String(Character.toChars((Integer) v));
            } catch (Throwable ignored) { }
        }
        return code;
    }

    private static Method sBind;
    private static final Map<View, Object> sLastDef = new WeakHashMap<View, Object>();
    private static final Map<View, Object[]> sCommaViews = new WeakHashMap<View, Object[]>();
    private static final Map<Object, Object> sMenuCopies = new WeakHashMap<Object, Object>();

    private static void rebind(Map<View, Object[]> pending) {
        final List<Map.Entry<View, Object[]>> l;
        synchronized (pending) { if (pending.isEmpty()) return; l = new ArrayList<Map.Entry<View, Object[]>>(pending.entrySet()); pending.clear(); }
        MAIN.post(new Runnable() { public void run() {
            for (Map.Entry<View, Object[]> e : l) {
                try {
                    View v = e.getKey();
                    Object last;
                    synchronized (sLastDef) { last = sLastDef.get(v); }
                    if (v == null || last != e.getValue()[0] || sBind == null) continue;
                    sBind.invoke(v, e.getValue()[0], e.getValue()[1]);
                } catch (Throwable t) { Log.w("rebind key", t); }
            }
        }});
    }

    private static String press(Object def) throws Throwable {
        for (Object a : actions(def)) { String t = text(a); if (t != null) return t; }
        return null;
    }

    private static void sawKey(View key, Object def, Object flags) throws Throwable {
        Object[] acts = actions(def);
        if (acts.length == 0) return;
        String press = null;
        Object pressAct = null;
        for (Object a : acts) { String t = text(a); if (t != null) { press = t; pressAct = a; break; } }
        if (press == null) return;
        if (sPressType == null) sPressType = sActType.get(pressAct);
        if ("ؤ".equals(press) && sHamzaData == null) {
            sHamzaData = datas(pressAct)[0];
            sHamzaType = sActType.get(pressAct);
        }
        if ("ؤ".equals(press)) sHamzaKey = new WeakReference<View>(key);
        if (sLongTemplate == null)
            for (Object a : acts) {
                Object d = datas(a);
                if (a == pressAct || sActType.get(a) == sPressType || !(d instanceof Object[]) || (sList == null && labelsOf(a) == null)) continue;
                int n = ((Object[]) d).length;
                if (n >= 2) { sLongTemplate = a; break; }
                if (n == 1 && sLongOne == null) { sLongOne = a; }
            }
        if ("و".equals(press)) synchronized (sWawDefs) { sWawDefs.put(def, Boolean.TRUE); }
        if ("ؤ".equals(press) || "و".equals(press)) settleSoon();
        if (sHamzaData == null || (sLongTemplate == null && sLongOne == null)) return;
        applyHamza();
    }

    private static void applyHamza() throws Throwable {
        List<Object> pending;
        synchronized (sWawDefs) { pending = new ArrayList<Object>(sWawDefs.keySet()); }
        for (Object w : pending) { if (giveHamza(w)) sWawDone = true; }
        if (sWawDone) hideHamza();
    }

    private static volatile boolean sSettlePosted;
    private static void settleSoon() {
        if (sSettlePosted) return;
        sSettlePosted = true;
        MAIN.postDelayed(new Runnable() { public void run() {
            sSettlePosted = false;
            try {
                if (!on(KEY_KEYS) || sHamzaData == null) return;
                if (sLongTemplate == null) {
                    List<Object> defs;
                    synchronized (sLastDef) { defs = new ArrayList<Object>(sLastDef.values()); }
                    for (Object def : defs) {
                        if (def == null || sLongTemplate != null) continue;
                        for (Object a : actions(def)) {
                            Object d = datas(a);
                            if (sActType.get(a) == sPressType || !(d instanceof Object[]) || (sList == null && labelsOf(a) == null)) continue;
                            int n = ((Object[]) d).length;
                            if (n >= 2) { sLongTemplate = a; break; }
                            if (n == 1 && sLongOne == null) { sLongOne = a; }
                        }
                    }
                }
                if (sLongTemplate == null && sLongOne == null) { return; }
                boolean any;
                synchronized (sWawDefs) { any = !sWawDefs.isEmpty(); }
                if (!any) { return; }
                applyHamza();
            } catch (Throwable ignored) { }
        }}, 120);
    }

    private static boolean giveHamza(Object wawDef) throws Throwable {
        Object[] acts = actions(wawDef);
        for (Object a : acts) {
            Object d = datas(a);
            if (d instanceof Object[]) for (Object x : (Object[]) d) if (x != null && "ؤ".equals(dataText(x))) return true;
        }
        Object[] grown = hamzaActions(acts);
        if (grown == null) { return false; }
        setActions(wawDef, grown);
                return true;
    }

    private static volatile Object sMenuData;
    private static int sMenuIcon;
    private static String sMenuDesc;
    private static Field sDefIcons, sDefDesc;

    private static Object menuKeys(View key, Object def, Object flags) throws Throwable {
        if (menu(key)) {
            boolean had = sMenuData != null;
            learnMenu(def);
            if (!had && sMenuData != null) rebind(sCommaViews);
            return null;
        }
        if (commaPopup(key, def) < 0) return null;
        if (sMenuData == null) { synchronized (sCommaViews) { sCommaViews.put(key, new Object[] { def, flags }); } return null; }
        Object c;
        synchronized (sMenuCopies) { c = sMenuCopies.get(def); }
        if (c != null) return c;
        Object[] acts = menuActions(def);
        if (acts == null) return null;
        c = copy(def);
        setActions(c, acts);
        synchronized (sMenuCopies) { sMenuCopies.put(def, c); }
        return c;
    }

    private static void learnMenu(Object def) throws Throwable {
        if (sMenuData != null) return;
        if (sDefIcons == null)
            for (Field f : sKeyDef.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                if (f.getType() == Object[].class) { f.setAccessible(true); sDefIcons = f; }
                else if (f.getType() == String.class && sDefDesc == null) { f.setAccessible(true); sDefDesc = f; }
            }
        int icon = 0;
        Object icons = sDefIcons == null ? null : sDefIcons.get(def);
        if (icons instanceof Object[]) for (Object o : (Object[]) icons) { icon = drawableId(o); if (icon != 0) break; }
        if (icon == 0 && sList != null)
            for (Field f : sDefLists) {
                Object v = f.get(def);
                if (!(v instanceof List)) continue;
                for (Object o : (List<?>) v) { if (o == null || o instanceof CharSequence || sActionDef.isInstance(o)) continue; icon = drawableId(o); if (icon != 0) break; }
                if (icon != 0) break;
            }
        Object data = null;
        for (Object a : actions(def)) {
            Object d = datas(a);
            if (d instanceof Object[] && ((Object[]) d).length > 0 && ((Object[]) d)[0] != null) { data = ((Object[]) d)[0]; break; }
        }
        if (data == null || icon == 0) return;
        Object desc = sDefDesc == null ? null : sDefDesc.get(def);
        sMenuIcon = icon;
        sMenuDesc = desc instanceof String ? (String) desc : null;
        sMenuData = data;
    }

    private static int drawableId(Object o) {
        if (o instanceof Integer) return drawable((Integer) o) ? (Integer) o : 0;
        if (o == null || o.getClass().getName().startsWith("java")) return 0;
        for (Field f : o.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) || f.getType() != int.class) continue;
            try { f.setAccessible(true); int v = f.getInt(o); if (drawable(v)) return v; } catch (Throwable ignored) { }
        }
        return 0;
    }

    private static boolean drawable(int id) {
        if ((id >>> 24) != 0x7f) return false;
        try { Context c = app(); String t = c == null ? null : c.getResources().getResourceTypeName(id); return "drawable".equals(t) || "mipmap".equals(t); } catch (Throwable t) { return false; }
    }

    private static int commaPopup(View key, Object def) throws Throwable {
        if (sActIcons == null) return -1;
        Object[] acts = actions(def);
        boolean comma = false;
        String n = idName(key);
        if (n.equals("key_pos_comma") || n.equals("key_pos_emoji")) comma = true;
        for (Object a : acts) { String t = text(a); if (",".equals(t) || "،".equals(t)) comma = true; }
        if (!comma) return -1;
        for (int i = 0; i < acts.length; i++) {
            Object d = datas(acts[i]), ic = sActIcons.get(acts[i]);
            if (!(d instanceof Object[]) || !(ic instanceof int[])) continue;
            int[] icons = (int[]) ic;
            if (icons.length >= 2 && icons.length == ((Object[]) d).length && icons[icons.length - 1] != 0) return i;
        }
        return -1;
    }

    private static Object[] menuActions(Object def) throws Throwable {
        Object[] acts = actions(def);
        int at = -1;
        if (sActIcons == null) return null;
        for (int i = 0; i < acts.length && at < 0; i++) {
            Object d = datas(acts[i]), ic = sActIcons.get(acts[i]);
            if (d instanceof Object[] && ic instanceof int[] && ((int[]) ic).length >= 2 && ((int[]) ic).length == ((Object[]) d).length) at = i;
        }
        if (at < 0) return null;
        Object[] data = datas(acts[at]);
        for (Object x : data) if (x == sMenuData) return null;
        int[] icons = (int[]) sActIcons.get(acts[at]);
        int[] from;
        if (data.length == 3) from = new int[] { 0, -1, 2, 1 };
        else { from = new int[data.length + 1]; for (int i = 0, j = 0; i < from.length; i++) from[i] = i == 1 ? -1 : j++; }
        Object[] grown = newDatas(from.length);
        int[] grownIcons = new int[from.length];
        for (int i = 0; i < from.length; i++) {
            java.lang.reflect.Array.set(grown, i, from[i] < 0 ? sMenuData : data[from[i]]);
            grownIcons[i] = from[i] < 0 ? sMenuIcon : icons[from[i]];
        }
        Object hold = copy(acts[at]);
        setDatas(hold, grown);
        sActIcons.set(hold, grownIcons);
        Object labels = labelsOf(acts[at]);
        if (labels instanceof String[] && ((String[]) labels).length == data.length) {
            String[] l = (String[]) labels, gl = new String[from.length];
            boolean words = false;
            for (String x : l) if (x != null && !x.isEmpty()) words = true;
            String mine = !words ? (l.length > 0 ? l[l.length - 1] : null) : (sMenuDesc == null ? "Menu" : sMenuDesc);
            for (int i = 0; i < gl.length; i++) gl[i] = from[i] < 0 ? mine : l[from[i]];
            setLabels(hold, gl);
        }
        Object[] out = (Object[]) java.lang.reflect.Array.newInstance(sActionDef, acts.length);
        System.arraycopy(acts, 0, out, 0, acts.length);
        out[at] = hold;
                return out;
    }

    private static Object[] hamzaActions(Object[] acts) throws Throwable {
        for (Object a : acts) {
            Object d = datas(a);
            if (d instanceof Object[]) for (Object x : (Object[]) d) if (x != null && "ؤ".equals(dataText(x))) return null;
        }
        Object tpl = sLongTemplate != null ? sLongTemplate : sLongOne;
        if (tpl == null) return null;
        for (int at = 0; at < acts.length; at++) {
            if (sActType.get(acts[at]) != sActType.get(tpl)) continue;
            Object d = datas(acts[at]);
            if (!(d instanceof Object[])) return null;
            Object[] old = (Object[]) d;
            Object[] more = newDatas(old.length + 1);
            for (int i = 0; i < old.length; i++) java.lang.reflect.Array.set(more, i, old[i]);
            java.lang.reflect.Array.set(more, old.length, sHamzaData);
            Object joined = copy(acts[at]);
            setDatas(joined, more);
            Object l = labelsOf(acts[at]);
            String[] labels = new String[old.length + 1];
            for (int i = 0; i < old.length; i++) { String t = l instanceof String[] && ((String[]) l).length == old.length ? ((String[]) l)[i] : null; labels[i] = t != null ? t : dataText(old[i]); }
            labels[old.length] = "ؤ";
            setLabels(joined, labels);
            if (sActIcons != null) { Object ic = sActIcons.get(acts[at]); if (ic instanceof int[] && ((int[]) ic).length == old.length) { int[] g = java.util.Arrays.copyOf((int[]) ic, old.length + 1); sActIcons.set(joined, g); } }
            Object[] out = (Object[]) java.lang.reflect.Array.newInstance(sActionDef, acts.length);
            System.arraycopy(acts, 0, out, 0, acts.length);
            out[at] = joined;
            return out;
        }
        Object hold = copy(tpl);
        Object[] arr = newDatas(1);
        java.lang.reflect.Array.set(arr, 0, sHamzaData);
        setDatas(hold, arr);
        setLabels(hold, new String[] { "ؤ" });
        if (sActIcons != null) sActIcons.set(hold, sList == null ? null : new int[1]);
        if (sActDesc != null) sActDesc.set(hold, null);
        Object[] grown = (Object[]) java.lang.reflect.Array.newInstance(sActionDef, acts.length + 1);
        System.arraycopy(acts, 0, grown, 0, acts.length);
        grown[acts.length] = hold;
        return grown;
    }

    private static final Map<View, String> sKeyText = new WeakHashMap<View, String>();
    private static final Map<ViewGroup, List<View>> sRowOrder = new WeakHashMap<ViewGroup, List<View>>();
    private static final Map<ViewGroup, Boolean> sRowPosted = new WeakHashMap<ViewGroup, Boolean>();

    private static void symbolKey(View key, Object def) throws Throwable {
        String t = press(def);
        synchronized (sKeyText) { sKeyText.put(key, t); }
        if (!(key.getParent() instanceof android.widget.LinearLayout)) return;
        final android.widget.LinearLayout row = (android.widget.LinearLayout) key.getParent();
        boolean known;
        synchronized (sRowOrder) { known = sRowOrder.containsKey(row); }
        if (!known && !"#".equals(t) && !"$".equals(t) && !"_".equals(t)) return;
        synchronized (sRowPosted) { if (sRowPosted.put(row, Boolean.TRUE) != null) return; }
        MAIN.post(new Runnable() { public void run() {
            synchronized (sRowPosted) { sRowPosted.remove(row); }
            try { fixRow(row); } catch (Throwable x) { Log.w("symbols row", x); }
        }});
    }

    private static void fixRow(android.widget.LinearLayout row) {
        List<View> cur = new ArrayList<View>();
        for (int i = 0; i < row.getChildCount(); i++) cur.add(row.getChildAt(i));
        List<View> nat;
        synchronized (sRowOrder) {
            nat = sRowOrder.get(row);
            if (nat == null || nat.size() != cur.size() || !nat.containsAll(cur)) { nat = new ArrayList<View>(cur); sRowOrder.put(row, nat); }
        }
        List<View> want = new ArrayList<View>(nat);
        for (int i = 0; i + 2 < nat.size(); i++) {
            String a, b, c;
            synchronized (sKeyText) { a = sKeyText.get(nat.get(i)); b = sKeyText.get(nat.get(i + 1)); c = sKeyText.get(nat.get(i + 2)); }
            if ("#".equals(a) && "$".equals(b) && "_".equals(c)) { want.set(i, nat.get(i + 2)); want.set(i + 1, nat.get(i)); want.set(i + 2, nat.get(i + 1)); break; }
        }
        if (want.equals(cur)) return;
        for (int i = 0; i < want.size(); i++) {
            View v = want.get(i);
            if (row.getChildAt(i) == v) continue;
            ViewGroup.LayoutParams lp = v.getLayoutParams();
            row.removeView(v);
            row.addView(v, i, lp);
        }
            }

    private static Object copy(Object o) throws Throwable {
        Class<?> u = Class.forName("sun.misc.Unsafe");
        Field f;
        try { f = u.getDeclaredField("theUnsafe"); } catch (NoSuchFieldException e) { f = u.getDeclaredField("THE_ONE"); }
        f.setAccessible(true);
        Object unsafe = f.get(null);
        Object out = u.getMethod("allocateInstance", Class.class).invoke(unsafe, o.getClass());
        for (Class<?> k = o.getClass(); k != null && k != Object.class; k = k.getSuperclass())
            for (Field x : k.getDeclaredFields()) {
                if (Modifier.isStatic(x.getModifiers())) continue;
                x.setAccessible(true);
                x.set(out, x.get(o));
            }
        return out;
    }

    private static void ltrNow(View v) {
        if (v.getLayoutDirection() != View.LAYOUT_DIRECTION_LTR) v.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        if (v instanceof android.widget.ImageView && v.getClass().getName().endsWith("AutoMirroredImageView")) { v.invalidate(); return; }
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) ltrNow(((ViewGroup) v).getChildAt(i));
    }

    private static void hideHamza() {
        final View hz = sHamzaKey == null ? null : sHamzaKey.get();
        if (hz == null) return;
        synchronized (sHiddenKeys) { sHiddenKeys.put(hz, Boolean.TRUE); }
        Runnable apply = new Runnable() { public void run() {
            try {
                if (hz.getVisibility() != View.GONE) hz.setVisibility(View.GONE);
                if (!(hz.getParent() instanceof android.widget.LinearLayout)) { return; }
                android.widget.LinearLayout row = (android.widget.LinearLayout) hz.getParent();
                View del = null;
                float sum = 0;
                for (int i = 0; i < row.getChildCount(); i++) {
                    View c = row.getChildAt(i);
                    if (c.getVisibility() == View.GONE) continue;
                    if ("key_pos_del".equals(idName(c))) { del = c; continue; }
                    if (c.getLayoutParams() instanceof android.widget.LinearLayout.LayoutParams) sum += ((android.widget.LinearLayout.LayoutParams) c.getLayoutParams()).weight;
                }
                if (del == null || sum <= 0 || !(del.getLayoutParams() instanceof android.widget.LinearLayout.LayoutParams)) { return; }
                float r = sDelRatio > 0.05f && sDelRatio < 0.3f ? sDelRatio : 0.135f;
                android.widget.LinearLayout.LayoutParams lp = (android.widget.LinearLayout.LayoutParams) del.getLayoutParams();
                float total = row.getWeightSum();
                float want = total > 0 ? r * total : r * sum / (1f - r);
                if (Math.abs(lp.weight - want) < 0.01f) return;
                if (total > 0) {
                    lp.weight = r * total;
                    float scale = (total - lp.weight) / sum;
                    for (int i = 0; i < row.getChildCount(); i++) {
                        View c = row.getChildAt(i);
                        if (c == del || c.getVisibility() == View.GONE || !(c.getLayoutParams() instanceof android.widget.LinearLayout.LayoutParams)) continue;
                        android.widget.LinearLayout.LayoutParams clp = (android.widget.LinearLayout.LayoutParams) c.getLayoutParams();
                        clp.weight = clp.weight * scale;
                        c.setLayoutParams(clp);
                    }
                } else lp.weight = r * sum / (1f - r);
                del.setLayoutParams(lp);
            } catch (Throwable ignored) { }
        }};
        if (Looper.myLooper() == Looper.getMainLooper()) apply.run(); else MAIN.post(apply);
    }

    static void prepareChips(InputMethodService ims, ClassLoader cl) throws Throwable {
        final Class<?> def = Shortcuts.defClass(cl);
        if (def == null) return;
        String name = Shortcuts.cachedClass(ims, "notify", "AddAccessPointNotification.notifyInternal->NotificationCenter.notify");
        if (name == null) return;
        for (Method m : cl.loadClass(name).getDeclaredMethods()) {
            Class<?>[] t = m.getParameterTypes();
            if (t.length == 0 || t[0] != def) continue;
            Xp.hookMethod(m, new Xp.Callback() {
                @Override protected void beforeHookedMethod(Xp.Param p) {
                    try {
                        if (p.args[0] == null) return;
                        if (defId(p.args[0], "mic_animation")) { voiceOn(true); return; }
                        if (!undoDef(p.args[0]) || !(on(KEY_HIDE_CHIPS) || on(KEY_SHORTCUTS))) return;
                        p.setResult(null);
                                            } catch (Throwable ignored) { }
                }
            });
        }
        String pro = Shortcuts.cachedClass(ims, "proactive", "Requesting to show proactive suggestions: %s in %s");
        if (pro != null) {
            int k = 0;
            for (Method m : cl.loadClass(pro).getDeclaredMethods()) {
                Class<?>[] t = m.getParameterTypes();
                if (Modifier.isStatic(m.getModifiers()) || m.getReturnType() != void.class || t.length != 1 || t[0] == Class.class || t[0].isPrimitive()) continue;
                Xp.hookMethod(m, new Xp.Callback() {
                    @Override protected void beforeHookedMethod(Xp.Param p) {
                        try {
                            if (!on(KEY_HIDE_CHIPS)) return;
                            p.setResult(null);
                                                    } catch (Throwable ignored) { }
                    }
                });
                k++;
            }
        }
        String rm = Shortcuts.cachedClass(ims, "unnotify", "RemoveAccessPointNotification.notify->NotificationCenter.notify");
        if (rm == null) { return; }
        for (Constructor<?> k : cl.loadClass(rm).getDeclaredConstructors()) {
            Class<?>[] t = k.getParameterTypes();
            if (t.length != 2 || t[1] != String.class) continue;
            Xp.hookMethod(k, new Xp.Callback() {
                @Override protected void afterHookedMethod(Xp.Param p) {
                    try { if ("mic_animation".equals(p.args[1])) voiceOn(false); } catch (Throwable ignored) { }
                }
            });
        }
    }

    private static WeakReference<Object> sNga;
    private static Method sNgaHandle;
    private static Class<?> sKeyDataClass, sEventClass;
    private static Field sEventKeys;

    static void prepareNga(InputMethodService ims, ClassLoader cl) throws Throwable {
        String name = Shortcuts.cachedClass(ims, "nga", "com/google/android/apps/inputmethod/libs/nga/impl/NgaVoiceInputHandlerSwitcher");
        if (name == null) { return; }
        for (Method m : cl.loadClass(name).getDeclaredMethods()) {
            Class<?>[] t = m.getParameterTypes();
            if (Modifier.isStatic(m.getModifiers()) || m.getReturnType() != boolean.class || t.length != 1 || t[0].isPrimitive() || t[0].getName().startsWith("java") || t[0].getName().startsWith("android")) continue;
            final Method hm = m;
            Xp.hookMethod(m, new Xp.Callback() {
                @Override protected void beforeHookedMethod(Xp.Param p) {
                    try {
                        int code = eventCode(p.args[0]);
                        if (code == -10042 || ((code == -10066 || code == -10108) && sVoice)) headerFade();
                    } catch (Throwable ignored) { }
                }
                @Override protected void afterHookedMethod(Xp.Param p) {
                    try {
                        int code = eventCode(p.args[0]);
                        if (code == 0) return;
                        boolean handled = Boolean.TRUE.equals(p.getResult());
                        sNga = new WeakReference<Object>(p.thisObject);
                        sNgaHandle = hm;
                        if (code == -10042 && handled) voiceOn(true);
                        else if (code == -10066 || code == -10108) voiceOn(false);
                    } catch (Throwable t) { Log.w("voice handler", t); }
                }
            });
        }
    }

    private static WeakReference<Object> sRambler;

    static void prepareRambler(InputMethodService ims, ClassLoader cl) throws Throwable {
        String name = Shortcuts.cachedClass(ims, "rambler", "newUiState: %s, oldUiState: %s");
        if (name == null) { return; }
        for (Method m : cl.loadClass(name).getDeclaredMethods()) {
            Class<?>[] t = m.getParameterTypes();
            if (Modifier.isStatic(m.getModifiers()) || m.getReturnType() != void.class || t.length != 2 || !t[0].isEnum()) continue;
            Xp.hookMethod(m, new Xp.Callback() {
                @Override protected void afterHookedMethod(Xp.Param p) {
                    try {
                        String st = String.valueOf(p.args[0]);
                        sRambler = new WeakReference<Object>(p.thisObject);
                        if (st.equals("WAITING") || st.equals("LISTENING") || st.equals("PROCESSING") || st.equals("RETRYING")) voiceOn(true);
                        else if (st.equals("IDLING") || st.equals("ERROR")) voiceOn(false);
                    } catch (Throwable t) { Log.w("dictation state", t); }
                }
            });
        }
    }

    private static long sClassicAt, sClassicStart;
    private static volatile boolean sClassicVoice;

    static void prepareClassicVoice(InputMethodService ims, ClassLoader cl) throws Throwable {
        if (on(KEY_DICTATION)) return;
        try { prepareClassicMic(ims, cl); } catch (Throwable t) { Log.w("classic mic", t); }
        String name = Shortcuts.cachedClass(ims, "agentic", "Exiting rambler mode when escape is pressed.");
        if (name == null) { return; }
        for (Method m : cl.loadClass(name).getDeclaredMethods()) {
            Class<?>[] t = m.getParameterTypes();
            if (!Modifier.isStatic(m.getModifiers()) && m.getReturnType() == android.os.Bundle.class) {
                Xp.hookMethod(m, new Xp.Callback() {
                    @Override protected void afterHookedMethod(Xp.Param p) {
                        try {
                            if (on(KEY_DICTATION) || !(p.getResult() instanceof android.os.Bundle)) return;
                            android.os.Bundle b = (android.os.Bundle) p.getResult();
                            if ("jetson".equals(b.getString("session_type"))) { b.remove("session_type"); }
                        } catch (Throwable t) { Log.w("classic voice bundle", t); }
                    }
                });
                continue;
            }
            if (Modifier.isStatic(m.getModifiers()) || m.getReturnType() != boolean.class || t.length != 1 || t[0].isPrimitive() || t[0].getName().startsWith("java") || t[0].getName().startsWith("android")) continue;
            Xp.hookMethod(m, new Xp.Callback() {
                @Override protected void beforeHookedMethod(Xp.Param p) {
                    try {
                        if (on(KEY_DICTATION)) return;
                        int code = eventCode(p.args[0]);
                        if (code != -10225 && code != -10224) return;
                        p.setResult(Boolean.TRUE);
                        long now = android.os.SystemClock.uptimeMillis();
                        if (now - sClassicAt < 500) return;
                        sClassicAt = now;
                        MAIN.post(new Runnable() { public void run() {
                            sClassicVoice = true;
                            sClassicStart = android.os.SystemClock.uptimeMillis();
                            headerFade();
                            voiceOn(true);
                            boolean sent = sendEvent(-10042);
                            if (!sent) { sClassicVoice = false; voiceOn(false); }
                                                    }});
                    } catch (Throwable t) { Log.w("classic voice", t); }
                }
            });
        }
    }

    private static void prepareClassicMic(InputMethodService ims, ClassLoader cl) throws Throwable {
        String name = Shortcuts.cachedClass(ims, "voiceicon", "com/google/android/apps/inputmethod/latin/voiceicon/VoiceIconAccessPointProviderModule");
        if (name == null) return;
        Method state = null;
        for (Method m : cl.loadClass(name).getDeclaredMethods()) {
            Class<?>[] t = m.getParameterTypes();
            if (Modifier.isStatic(m.getModifiers()) || m.getReturnType() != void.class || t.length != 1 || t[0] != int.class) continue;
            if (state != null) return;
            state = m;
        }
        if (state == null) return;
        Xp.hookMethod(state, new Xp.Callback() {
            @Override protected void beforeHookedMethod(Xp.Param p) {
                try {
                    if (on(KEY_DICTATION) || !(p.args[0] instanceof Integer)) return;
                    int s = (Integer) p.args[0];
                    if ((s & SPARKLE_MIC) != 0 && (s & ~SPARKLE_MIC) != 0) p.args[0] = s & ~SPARKLE_MIC;
                } catch (Throwable t) { Log.w("classic mic", t); }
            }
        });
    }

    private static final int SPARKLE_MIC = 64;

    static final String VOICE_TAG = "PixelSuiteVoice";

    private static boolean cached(Context c, String... what) {
        try {
            long ver = c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionCode;
            for (String w : what) {
                File f = new File(c.getCacheDir(), "pixelsuite_gboard_" + w + "_" + ver);
                if (!f.isFile() || f.length() == 0) return false;
            }
            return true;
        } catch (Throwable t) { return false; }
    }

    private static volatile boolean sDictationReady;
    private static final String TOOLBAR_DICTATION = "enable_jetson_in_toolbar";
    private static volatile Object sToolbarDictation;

    static synchronized void prepareDictationFlag(InputMethodService ims, ClassLoader cl) throws Throwable {
        if (!on(KEY_DICTATION) || sDictationReady) return;
        String owner = Shortcuts.cachedClass(ims, "jetsonflag", TOOLBAR_DICTATION);
        if (owner == null) { android.util.Log.w(VOICE_TAG, "toolbar dictation: Gboard's flag wasn't found"); return; }
        Object flag = null;
        Class<?> type = null;
        for (Field f : cl.loadClass(owner).getDeclaredFields()) {
            Class<?> t = f.getType();
            if (!Modifier.isStatic(f.getModifiers()) || t.isPrimitive() || t.isArray() || t.getName().startsWith("java") || t.getName().startsWith("android")) continue;
            try {
                f.setAccessible(true);
                Object v = f.get(null);
                if (v != null && TOOLBAR_DICTATION.equals(flagName(t, v))) { flag = v; type = t; break; }
            } catch (Throwable ignored) { }
        }
        if (flag == null) { android.util.Log.w(VOICE_TAG, "toolbar dictation: no flag named " + TOOLBAR_DICTATION + " in " + owner); return; }
        List<Method> getters = new ArrayList<Method>();
        for (Method m : type.getMethods()) {
            if (Modifier.isStatic(m.getModifiers()) || m.getParameterTypes().length != 0 || m.getReturnType() != Object.class) continue;
            try {
                Method impl = flag.getClass().getMethod(m.getName());
                if (Modifier.isAbstract(impl.getModifiers()) || getters.contains(impl)) continue;
                impl.setAccessible(true);
                getters.add(impl);
            } catch (Throwable ignored) { }
        }
        if (getters.isEmpty()) { android.util.Log.w(VOICE_TAG, "toolbar dictation: Gboard's flag has no value getter"); return; }
        sToolbarDictation = flag;
        for (Method g : getters) {
            Xp.hookMethod(g, new Xp.Callback() {
                @Override protected void afterHookedMethod(Xp.Param p) {
                    if (p.thisObject != sToolbarDictation || !Boolean.FALSE.equals(p.getResult())) return;
                    if (on(KEY_DICTATION)) p.setResult(Boolean.TRUE);
                }
            });
        }
        sDictationReady = true;
        for (String name : dictationReaders(ims, cl)) {
            try { for (Method m : cl.loadClass(name).getDeclaredMethods()) { Xp.deoptimize(m); } } catch (Throwable ignored) { }
        }
        String[] readers = {
            "com/google/android/apps/inputmethod/libs/agenticdictation/extension/RamblerController",
            "com/google/android/libraries/inputmethod/accessory/AccessoryInputModeManager",
            "com/google/android/apps/inputmethod/libs/agenticdictation/ui/impl/RamblerUiVkController",
        };
        String[] keys = { "jetsonreader1", "jetsonreader2", "jetsonreader3" };
        for (int i = 0; i < readers.length; i++) {
            try {
                String name = Shortcuts.cachedClass(ims, keys[i], readers[i]);
                if (name == null) continue;
                for (Method m : cl.loadClass(name).getDeclaredMethods()) { Xp.deoptimize(m); }
            } catch (Throwable ignored) { }
        }
    }

    private static List<String> dictationReaders(InputMethodService ims, ClassLoader cl) {
        List<String> out = new ArrayList<String>();
        try { String a = Shortcuts.cachedClass(ims, "accessorygate", ACCESSORY_TRACE); if (a != null) out.add(a); } catch (Throwable ignored) { }
        try { Class<?> ctl = dictationController(ims, cl); if (ctl != null) out.add(ctl.getName()); } catch (Throwable ignored) { }
        return out;
    }

    private static final String ACCESSORY_TRACE = "AccessoryInputModeManager.onDestroyExtension->NotificationCenter.clearNotification";

    private static Class<?> dictationController(InputMethodService ims, ClassLoader cl) throws Throwable {
        String agentic = Shortcuts.cachedClass(ims, "agentic", "Exiting rambler mode when escape is pressed.");
        if (agentic == null) return null;
        for (Class<?> c = cl.loadClass(agentic); c != null && c != Object.class; c = c.getSuperclass())
            for (Field f : c.getDeclaredFields()) {
                Class<?> t = f.getType();
                if (Modifier.isStatic(f.getModifiers()) || t.isPrimitive() || t.isArray() || t.getName().startsWith("java") || t.getName().startsWith("android")) continue;
                int longs = 0, bools = 0;
                for (Field g : t.getDeclaredFields()) {
                    if (Modifier.isStatic(g.getModifiers())) continue;
                    if (g.getType() == long.class) longs++; else if (g.getType() == boolean.class) bools++;
                }
                if (longs >= 2 && bools >= 2) return t;
            }
        return null;
    }

    private static String flagName(Class<?> type, Object flag) {
        for (Method m : type.getMethods()) {
            if (Modifier.isStatic(m.getModifiers()) || m.getParameterTypes().length != 0 || m.getReturnType() != String.class) continue;
            try { Object s = m.invoke(flag); if (TOOLBAR_DICTATION.equals(s)) return (String) s; } catch (Throwable ignored) { }
        }
        return null;
    }

    private static boolean stopRambler() {
        try {
            Object r = sRambler == null ? null : sRambler.get();
            if (r == null) return false;
            for (Class<?> k = r.getClass(); k != null && k != Object.class; k = k.getSuperclass())
                for (Field f : k.getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers()) || f.getType() != Runnable.class) continue;
                    f.setAccessible(true);
                    Object run = f.get(r);
                    if (run instanceof Runnable) { ((Runnable) run).run(); return true; }
                }
        } catch (Throwable t) { Log.w("dictation stop", t); }
        return false;
    }

    private static int eventCode(Object event) throws Throwable {
        if (event == null) return 0;
        if (sEventKeys == null) {
            for (Field f : event.getClass().getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) || !f.getType().isArray() || f.getType().getComponentType().isPrimitive() || f.getType().getComponentType().getName().startsWith("java")) continue;
                f.setAccessible(true);
                sEventKeys = f; sEventClass = event.getClass(); sKeyDataClass = f.getType().getComponentType();
                break;
            }
            if (sEventKeys == null) return 0;
        }
        Object arr = sEventKeys.get(event);
        if (!(arr instanceof Object[]) || ((Object[]) arr).length == 0 || ((Object[]) arr)[0] == null) return 0;
        Object kd = ((Object[]) arr)[0];
        for (Field f : kd.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) || f.getType() != int.class) continue;
            f.setAccessible(true);
            return f.getInt(kd);
        }
        return 0;
    }

    private static boolean stopVoiceNga() {
        try {
            Object nga = sNga == null ? null : sNga.get();
            if (nga == null || sNgaHandle == null || sKeyDataClass == null || sEventClass == null) return false;
            Constructor<?> kc = null;
            for (Constructor<?> k : sKeyDataClass.getDeclaredConstructors()) {
                Class<?>[] t = k.getParameterTypes();
                if (t.length == 4 && t[0] == int.class && t[2] == Object.class && t[3] == int.class) kc = k;
            }
            Method of = null;
            for (Method m : sEventClass.getDeclaredMethods()) {
                Class<?>[] t = m.getParameterTypes();
                if (Modifier.isStatic(m.getModifiers()) && m.getReturnType() == sEventClass && t.length == 1 && t[0] == sKeyDataClass) { of = m; break; }
            }
            if (kc == null || of == null) return false;
            kc.setAccessible(true); of.setAccessible(true); sNgaHandle.setAccessible(true);
            Object kd = kc.newInstance(-10066, null, "", Integer.MAX_VALUE);
            Object ev = of.invoke(null, kd);
            Object r = sNgaHandle.invoke(nga, ev);
            return Boolean.TRUE.equals(r);
        } catch (Throwable t) { Log.w("voice stop nga", t); return false; }
    }

    private static boolean defId(Object def, String id) throws Throwable {
        for (Class<?> k = def.getClass(); k != null && k != Object.class; k = k.getSuperclass())
            for (Field f : k.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) || f.getType() != String.class) continue;
                f.setAccessible(true);
                if (id.equals(f.get(def))) return true;
            }
        return false;
    }

    private static void voiceOn(boolean on) {
        if (!on) { sClassicVoice = false; if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) { showVoiceHidden(); glowOff(); } }
        if (sVoice == on) return;
        sVoice = on;
        MAIN.removeCallbacks(VOICE_TITLES); MAIN.post(VOICE_TITLES); MAIN.postDelayed(VOICE_TITLES, 150);
        final boolean o = on;
        if (!on && android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            try {
                InputMethodService ims0 = sIme.get();
                android.app.Dialog dlg0 = ims0 == null ? null : ims0.getWindow();
                View decor0 = dlg0 == null || dlg0.getWindow() == null ? null : dlg0.getWindow().peekDecorView();
                if (decor0 != null) { voiceViews(decor0, false); return; }
            } catch (Throwable t) { Log.w("voice views", t); }
        }
        MAIN.post(new Runnable() { public void run() {
            try {
                InputMethodService ims = sIme.get();
                android.app.Dialog dlg = ims == null ? null : ims.getWindow();
                View decor = dlg == null || dlg.getWindow() == null ? null : dlg.getWindow().peekDecorView();
                if (decor != null) voiceViews(decor, o);
                else if (!o) removeX();
            } catch (Throwable t) { Log.w("voice views", t); }
        }});
    }

    private static boolean undoDef(Object def) throws Throwable {
        for (Class<?> k = def.getClass(); k != null && k != Object.class; k = k.getSuperclass())
            for (Field f : k.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) || f.getType() != String.class) continue;
                f.setAccessible(true);
                Object v = f.get(def);
                if (v instanceof String && ((String) v).toLowerCase(java.util.Locale.ROOT).contains("undo") && !((String) v).startsWith("pixelsuite_")) return true;
            }
        return false;
    }

    private static final Map<View, android.animation.Animator> sPopOut = new WeakHashMap<View, android.animation.Animator>();
    private static final Map<View, Boolean> sPopped = new WeakHashMap<View, Boolean>();
    private static final android.view.animation.Interpolator POP_IN = new android.view.animation.OvershootInterpolator(1.6f);
    private static final android.view.animation.Interpolator POP_OUT = new android.view.animation.PathInterpolator(0.4f, 0f, 1f, 1f);

    static void preparePopups(InputMethodService ims, ClassLoader cl) throws Throwable {
        String name = Shortcuts.cachedClass(ims, "popups", "com/google/android/libraries/inputmethod/popupviewmanager/AbstractPopupViewManager");
        if (name == null) return;
        final Class<?> key = cl.loadClass(SOFT_KEY);
        for (Method m : cl.loadClass(name).getDeclaredMethods()) {
            if (m.getReturnType() != void.class || Modifier.isStatic(m.getModifiers())) continue;
            Class<?>[] t = m.getParameterTypes();
            if (t.length == 3 && t[0] == View.class && t[1] == android.animation.Animator.class && t[2] == boolean.class) {
                Xp.hookMethod(m, new Xp.Callback() {
                    @Override protected void beforeHookedMethod(Xp.Param p) { try { popOut(p); } catch (Throwable ignored) { } }
                });
            } else if (t.length == 1 && popupParams(t[0])) {
                Xp.hookMethod(m, new Xp.Callback() {
                    @Override protected void beforeHookedMethod(Xp.Param p) { try { endLingering(); } catch (Throwable ignored) { } }
                    @Override protected void afterHookedMethod(Xp.Param p) { try { popIn(p.args[0], key); } catch (Throwable ignored) { } }
                });
            }
        }
    }

    private static boolean popupParams(Class<?> c) {
        if (c.isPrimitive() || c.getName().startsWith("java") || c.getName().startsWith("android")) return false;
        int views = 0; boolean anim = false;
        for (Field f : c.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers())) continue;
            if (f.getType() == View.class) views++;
            else if (f.getType() == android.animation.Animator.class) anim = true;
        }
        return views >= 2 && anim;
    }

    private static void popIn(Object prm, Class<?> key) throws Throwable {
        if (prm == null || !on(KEY_KEYS)) return;
        List<View> views = new ArrayList<View>();
        for (Field f : prm.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers())) continue;
            f.setAccessible(true);
            if (f.getType() == android.animation.Animator.class) { if (f.get(prm) != null) return; continue; }
            if (f.getType() != View.class) continue;
            Object v = f.get(prm);
            if (v instanceof View) views.add((View) v);
        }
        if (views.size() < 2) return;
        View popup = views.get(0), anchor = views.get(1);
        if (key.isInstance(popup) && !key.isInstance(anchor)) { View x = popup; popup = anchor; anchor = x; }
        boolean keyBubble = key.isInstance(anchor) ? styled(anchor) : findInstance(popup, key) != null && !(anchor.getClass().getName().contains("AccessPoint"));
        if (!keyBubble) return;
        noDots(popup);
        try { greyBubble(popup, anchor); } catch (Throwable ignored) { }
        final View pv = popup;
        pv.post(new Runnable() { public void run() { noDots(pv); } });
        synchronized (sPopOut) { if (Boolean.TRUE.equals(sPopped.get(popup)) && popup.isShown()) return; sPopped.put(popup, Boolean.TRUE); }
        popup.animate().cancel();
        pivot(popup);
        popup.setAlpha(1f); popup.setScaleX(0.7f); popup.setScaleY(0.7f);
        popup.animate().scaleX(1f).scaleY(1f).setDuration(110).setInterpolator(POP_IN).start();
    }

    private static final int TAG_GREY = 0x7f5e0003;

    private static void greyBubble(View popup, View anchor) {
        Boolean fromKey = sDarkKeys != null ? sDarkKeys : keyIsDark(anchor);
        boolean dark = fromKey != null ? fromKey : (popup.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        View face = bubbleFace(popup, 0);
        if (face == null) return;
        int color = !dark ? 0xFFFFFFFF : superDark(popup.getContext()) ? 0xFF48484A : 0xFF636366;
        android.graphics.drawable.Drawable bg = face.getBackground();
        if (bg == null) return;
        bg = bg.mutate();
        bg.setColorFilter(new android.graphics.PorterDuffColorFilter(color, android.graphics.PorterDuff.Mode.SRC_IN));
        face.setTag(TAG_GREY, color);
        if (!dark) { face.setElevation(Math.max(face.getElevation(), 2f * face.getResources().getDisplayMetrics().density)); }
        bubbleText(popup, dark ? 0xFFFFFFFF : 0xFF000000);
        face.invalidate();
    }

    private static void bubbleText(View v, int color) {
        if (v instanceof TextView) { if (((TextView) v).getCurrentTextColor() != color) ((TextView) v).setTextColor(color); return; }
        if (v instanceof android.widget.ImageView) { ((android.widget.ImageView) v).setColorFilter(color, android.graphics.PorterDuff.Mode.SRC_IN); return; }
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) bubbleText(((ViewGroup) v).getChildAt(i), color);
    }

    private static Boolean keyIsDark(View v) {
        if (v == null) return null;
        if (v instanceof TextView && ((TextView) v).getText() != null && ((TextView) v).getText().length() > 0 && v.getAlpha() > 0.01f) {
            int col = ((TextView) v).getCurrentTextColor();
            return (Color.red(col) * 299 + Color.green(col) * 587 + Color.blue(col) * 114) / 1000 > 140;
        }
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) { Boolean b = keyIsDark(((ViewGroup) v).getChildAt(i)); if (b != null) return b; }
        return null;
    }

    private static View bubbleFace(View v, int depth) {
        android.graphics.drawable.Drawable bg = v.getBackground();
        if (bg != null && !(bg instanceof android.graphics.drawable.ColorDrawable)) return v;
        if (!(v instanceof ViewGroup) || depth > 3) return null;
        ViewGroup g = (ViewGroup) v;
        for (int i = 0; i < g.getChildCount(); i++) { View f = bubbleFace(g.getChildAt(i), depth + 1); if (f != null) return f; }
        return null;
    }

    private static void pivot(final View v) {
        if (v.getWidth() > 0 && v.getHeight() > 0) { v.setPivotX(v.getWidth() / 2f); v.setPivotY(v.getHeight()); return; }
        v.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override public void onLayoutChange(View x, int l, int t, int r, int b, int ol, int ot, int or, int ob) {
                x.removeOnLayoutChangeListener(this);
                x.setPivotX((r - l) / 2f); x.setPivotY(b - t);
            }
        });
    }

    private static void noDots(View v) {
        try {
            if (v instanceof TextView) {
                CharSequence t = ((TextView) v).getText();
                String s = t == null ? "" : t.toString().trim();
                boolean dots = s.equals("…") || s.equals("...") || s.equals("⋯") || s.equals("···");
                if (dots && v.getAlpha() != 0f) v.setAlpha(0f);
                return;
            }
            if (!(v instanceof ViewGroup)) return;
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) noDots(g.getChildAt(i));
        } catch (Throwable ignored) { }
    }

    private static void popOut(Xp.Param p) {
        if (!(p.args[0] instanceof View)) return;
        final View v = (View) p.args[0];
        synchronized (sPopOut) { if (sPopped.remove(v) == null) return; }
        v.animate().cancel();
        if (p.args[1] != null || !v.isShown()) { v.setScaleX(1f); v.setScaleY(1f); v.setAlpha(1f); return; }
        android.animation.ObjectAnimator fade = android.animation.ObjectAnimator.ofFloat(v, "alpha", v.getAlpha(), 0f);
        fade.setStartDelay(45);
        fade.setDuration(75);
        android.animation.ObjectAnimator sx = android.animation.ObjectAnimator.ofFloat(v, "scaleX", v.getScaleX(), 0.94f);
        android.animation.ObjectAnimator sy = android.animation.ObjectAnimator.ofFloat(v, "scaleY", v.getScaleY(), 0.94f);
        sx.setStartDelay(30); sx.setDuration(90);
        sy.setStartDelay(30); sy.setDuration(90);
        final android.animation.AnimatorSet out = new android.animation.AnimatorSet();
        out.playTogether(fade, sx, sy);
        out.setInterpolator(POP_OUT);
        out.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator a) {
                synchronized (sPopOut) { if (sPopOut.get(v) == out) sPopOut.remove(v); }
                v.postDelayed(new Runnable() { public void run() {
                    synchronized (sPopOut) { if (sPopped.containsKey(v) || sPopOut.containsKey(v)) return; }
                    v.setScaleX(1f); v.setScaleY(1f); v.setAlpha(1f);
                }}, 60);
            }
        });
        synchronized (sPopOut) { sPopOut.put(v, out); }
        p.args[1] = out;
    }

    private static void endLingering() {
        List<android.animation.Animator> going;
        synchronized (sPopOut) { if (sPopOut.isEmpty()) return; going = new ArrayList<android.animation.Animator>(sPopOut.values()); sPopOut.clear(); }
        for (android.animation.Animator a : going) { try { a.end(); } catch (Throwable ignored) { } }
    }

    private static int sDelId;
    private static boolean sTracking, sSwiping, sNoExtract;
    private static float sDownX;
    private static int sBase, sCount;

    private static void installDeleteSwipe(ClassLoader cl) throws Throwable {
        Class<?> kv = cl.loadClass("com.google.android.libraries.inputmethod.widgets.SoftKeyboardView");
        Xp.hookAllMethods(kv, "dispatchTouchEvent", new Xp.Callback() {
            @Override protected void beforeHookedMethod(Xp.Param p) {
                try {
                    if (p.args.length != 1 || !(p.args[0] instanceof MotionEvent)) return;
                    MotionEvent e = (MotionEvent) p.args[0];
                    int a = e.getActionMasked();
                    if (a == MotionEvent.ACTION_DOWN) {
                        sTracking = false; sSwiping = false;
                        if (!on(KEY_DEL_LETTERS)) return;
                        ViewGroup g = (ViewGroup) p.thisObject;
                        if (sDelId == 0) sDelId = g.getResources().getIdentifier("key_pos_del", "id", g.getContext().getPackageName());
                        View del = sDelId == 0 ? null : g.findViewById(sDelId);
                        if (del == null || !del.isShown()) return;
                        int[] k = new int[2], me = new int[2];
                        del.getLocationOnScreen(k); g.getLocationOnScreen(me);
                        float x = e.getRawX(), y = e.getRawY();
                        if (x >= k[0] && x <= k[0] + del.getWidth() && y >= k[1] && y <= k[1] + del.getHeight()) { sTracking = true; sDownX = x; }
                        return;
                    }
                    if (!sTracking) return;
                    float d = ((View) p.thisObject).getResources().getDisplayMetrics().density;
                    float dx = sDownX - e.getRawX();
                    if (a == MotionEvent.ACTION_MOVE) {
                        if (!sSwiping) {
                            if (dx < 12 * d) return;
                            sSwiping = true;
                            sBase = cursor();
                            sCount = 0;
                            MotionEvent cancel = MotionEvent.obtain(e);
                            cancel.setAction(MotionEvent.ACTION_CANCEL);
                            p.args[0] = cancel;
                                                        return;
                        }
                        int n = Math.max(0, (int) ((dx - 12 * d) / (9 * d)) + 1);
                        if (sBase >= 0) n = Math.min(n, sBase);
                        if (n != sCount) { sCount = n; select(n); try { ((View) p.thisObject).performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK); } catch (Throwable ignored) { } }
                        p.setResult(Boolean.TRUE);
                        return;
                    }
                    if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
                        boolean was = sSwiping;
                        sTracking = false; sSwiping = false;
                        if (!was) return;
                        if (a == MotionEvent.ACTION_UP) finishDelete();
                        p.setResult(Boolean.TRUE);
                    }
                } catch (Throwable t) { Log.w("delete swipe", t); }
            }
        });
    }

    private static int cursor() {
        try {
            InputMethodService ims = sIme.get();
            InputConnection ic = ims == null ? null : ims.getCurrentInputConnection();
            if (ic == null) return -1;
            ExtractedText et = ic.getExtractedText(new ExtractedTextRequest(), 0);
            if (et == null) { sNoExtract = true; return -1; }
            sNoExtract = false;
            return Math.min(et.selectionStart, et.selectionEnd) + et.startOffset;
        } catch (Throwable t) { return -1; }
    }

    private static void select(int n) {
        try {
            InputMethodService ims = sIme.get();
            InputConnection ic = ims == null ? null : ims.getCurrentInputConnection();
            if (ic == null || sBase < 0) return;
            ic.setSelection(sBase - n, sBase);
        } catch (Throwable ignored) { }
    }

    private static void finishDelete() {
        try {
            InputMethodService ims = sIme.get();
            InputConnection ic = ims == null ? null : ims.getCurrentInputConnection();
            if (ic == null || sCount <= 0) return;
            if (sBase >= 0) { ic.setSelection(sBase - sCount, sBase); ic.commitText("", 1); }
            else ic.deleteSurroundingText(sCount, 0);
        } catch (Throwable ignored) { }
    }

    private static boolean landscape(View v) {
        return v.getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
    }

    private static View findInstance(View v, Class<?> type) {
        if (type.isInstance(v)) return v;
        if (!(v instanceof ViewGroup)) return null;
        ViewGroup g = (ViewGroup) v;
        for (int i = 0; i < g.getChildCount(); i++) { View f = findInstance(g.getChildAt(i), type); if (f != null) return f; }
        return null;
    }

    private static final Map<Integer, Boolean> sRightIds = new HashMap<Integer, Boolean>();

    private static boolean right(View v) {
        int id = v.getId();
        if (id == View.NO_ID) return false;
        Boolean m;
        synchronized (sRightIds) { m = sRightIds.get(id); }
        if (m == null) {
            String n = "";
            try { n = v.getResources().getResourceEntryName(id); } catch (Throwable ignored) { }
            m = "key_pos_header_power_key".equals(n);
            synchronized (sRightIds) { sRightIds.put(id, m); }
        }
        return m;
    }

    private static boolean menu(View v) {
        int id = v.getId();
        if (id == View.NO_ID) return false;
        Boolean m;
        synchronized (sMenuIds) { m = sMenuIds.get(id); }
        if (m == null) {
            String n = "";
            try { n = v.getResources().getResourceEntryName(id); } catch (Throwable ignored) { }
            m = "key_pos_header_access_points_menu".equals(n);
            synchronized (sMenuIds) { sMenuIds.put(id, m); }
        }
        return m;
    }

    private static void showBar(final View chips, final Class<?> bar) {
        MAIN.post(new Runnable() { public void run() {
            try {
                if (sVoice) return;
                if (!(chips.getParent() instanceof ViewGroup)) return;
                ViewGroup g = (ViewGroup) chips.getParent();
                for (int i = 0; i < g.getChildCount(); i++) {
                    View ch = g.getChildAt(i);
                    if (ch != chips && ch.getVisibility() == View.VISIBLE && ch.getClass().getName().contains("Candidates")) return;
                }
                for (int i = 0; i < g.getChildCount(); i++) { View ch = g.getChildAt(i); if (bar.isInstance(ch) && ch.getVisibility() != View.VISIBLE) ch.setVisibility(View.VISIBLE); }
            } catch (Throwable ignored) { }
        }});
    }

    private static View labelArea(View v) {
        if (!(v instanceof ViewGroup)) return null;
        ViewGroup g = (ViewGroup) v;
        View best = null;
        for (int i = 0; i < g.getChildCount(); i++) {
            View ch = g.getChildAt(i);
            if (ch.getVisibility() != View.VISIBLE || ch.getHeight() == 0) continue;
            if (ch.getClass().getName().endsWith("MaterialTextView")) continue;
            if (best == null || ch.getHeight() > best.getHeight()) best = ch;
        }
        return best;
    }

    private static TextView text(View v) {
        if (v instanceof TextView && !v.getClass().getName().endsWith("MaterialTextView")) return (TextView) v;
        if (!(v instanceof ViewGroup)) return null;
        ViewGroup g = (ViewGroup) v;
        for (int i = 0; i < g.getChildCount(); i++) { TextView t = text(g.getChildAt(i)); if (t != null && t.getText() != null && t.getText().length() > 0) return t; }
        return null;
    }

    private static View find(View v, String idName) {
        if (v.getId() != View.NO_ID) {
            try { if (idName.equals(v.getResources().getResourceEntryName(v.getId()))) return v; } catch (Throwable ignored) { }
        }
        if (!(v instanceof ViewGroup)) return null;
        ViewGroup g = (ViewGroup) v;
        for (int i = 0; i < g.getChildCount(); i++) { View f = find(g.getChildAt(i), idName); if (f != null) return f; }
        return null;
    }

    private static int[] offset(View root, View child) {
        int x = 0, y = 0;
        for (View c = child; c != null && c != root; c = c.getParent() instanceof View ? (View) c.getParent() : null) { x += c.getLeft(); y += c.getTop(); }
        return new int[] { x, y };
    }

    private static void installClipboard(ClassLoader cl) throws Throwable {
        Class<?> lm;
        try { lm = cl.loadClass("android.support.v7.widget.StaggeredGridLayoutManager"); }
        catch (Throwable t) { lm = cl.loadClass("androidx.recyclerview.widget.StaggeredGridLayoutManager"); }
        final Map<Class<?>, Boolean> clip = new HashMap<Class<?>, Boolean>();
        for (Constructor<?> k : lm.getDeclaredConstructors()) {
            Class<?>[] t = k.getParameterTypes();
            if (t.length < 1 || t[0] != int.class) continue;
            Xp.hookMethod(k, new Xp.Callback() {
                @Override protected void beforeHookedMethod(Xp.Param p) {
                    try {
                        if (p.thisObject == null || !(p.args[0] instanceof Integer) || (Integer) p.args[0] < 2) return;
                        Class<?> owner = p.thisObject.getClass();
                        Boolean is;
                        synchronized (clip) { is = clip.get(owner); }
                        if (is == null) {
                            is = false;
                            for (Constructor<?> c : owner.getDeclaredConstructors()) for (Class<?> pt : c.getParameterTypes()) if (pt.getName().endsWith("clipboard.ClipboardKeyboard")) is = true;
                            synchronized (clip) { clip.put(owner, is); }
                        }
                        if (!is || !on(KEY_CLIP)) return;
                        p.args[0] = 1;
                    } catch (Throwable ignored) { }
                }
                @Override protected void afterHookedMethod(Xp.Param p) {
                    try {
                        Boolean is;
                        synchronized (clip) { is = clip.get(p.thisObject.getClass()); }
                        if (!Boolean.TRUE.equals(is) || !on(KEY_CLIP)) return;
                        for (Class<?> k = p.thisObject.getClass(); k != null && k != Object.class; k = k.getSuperclass()) {
                            try { Method g = k.getDeclaredMethod("setGapStrategy", int.class); g.setAccessible(true); g.invoke(p.thisObject, 0); break; } catch (NoSuchMethodException ignored) { }
                        }
                        synchronized (sClipLms) { sClipLms.put(p.thisObject, Boolean.TRUE); }
                    } catch (Throwable ignored) { }
                }
            });
        }
        try {
            Class<?> rv = cl.loadClass(lm.getName().substring(0, lm.getName().lastIndexOf('.') + 1) + "RecyclerView");
            Method lmGet = null;
            for (Method m : rv.getDeclaredMethods()) if (m.getParameterTypes().length == 0 && m.getReturnType() != Object.class && !m.getReturnType().isPrimitive() && m.getReturnType().isAssignableFrom(lm)) { lmGet = m; break; }
            final Method getLm = lmGet;
            Xp.hookAllMethods(rv, "onChildAttachedToWindow", new Xp.Callback() {
                @Override protected void afterHookedMethod(Xp.Param p) {
                    try {
                        if (!on(KEY_CLIP) || getLm == null || p.args.length != 1 || !(p.args[0] instanceof View)) return;
                        getLm.setAccessible(true);
                        Object l = getLm.invoke(p.thisObject);
                        Boolean ours;
                        synchronized (sClipLms) { ours = l == null ? null : sClipLms.get(l); }
                        if (ours == null) return;
                        final View item = (View) p.args[0];
                        final float d = item.getResources().getDisplayMetrics().density;
                        noTypeIcons(item, d);
                        item.post(new Runnable() { public void run() { noTypeIcons(item, d); } });
                    } catch (Throwable ignored) { }
                }
            });
        } catch (Throwable t) { Log.w("clip icons", t); }
    }

    private static final Map<Object, Boolean> sClipLms = new WeakHashMap<Object, Boolean>();

    private static volatile boolean sBinding;
    private static Class<?> sClipAdapter;
    private static final Map<Object, Integer> sBindDrawables = new java.util.IdentityHashMap<Object, Integer>();
    private static boolean sDrawHook;

    static void prepareClipIcons(InputMethodService ims, ClassLoader cl) throws Throwable {
        if (!sDrawHook) {
            sDrawHook = true;
            Xp.hookAllMethods(android.content.res.Resources.class, "getDrawable", new Xp.Callback() {
                @Override protected void afterHookedMethod(Xp.Param p) {
                    if (!sBinding || p.args.length < 1 || !(p.args[0] instanceof Integer) || p.getResult() == null) return;
                    if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) return;
                    synchronized (sBindDrawables) { sBindDrawables.put(p.getResult(), (Integer) p.args[0]); }
                }
            });
        }
        try { installClipSound(); } catch (Throwable t) { Log.w("clip sound", t); }
        String name = Shortcuts.cachedClass(ims, "clipadapter", "com/google/android/apps/inputmethod/libs/clipboard/ClipboardAdapter");
        if (name == null) { return; }
        final Class<?> ad = cl.loadClass(name);
        sClipAdapter = ad;
        for (Method m : ad.getDeclaredMethods()) {
            Class<?>[] t = m.getParameterTypes();
            if (Modifier.isStatic(m.getModifiers()) || m.getReturnType() != void.class || t.length != 2 || t[1] != int.class || t[0].isPrimitive() || t[0].getName().startsWith("java") || t[0].getName().startsWith("android.") && !t[0].getName().contains("RecyclerView")) continue;
            Xp.hookMethod(m, new Xp.Callback() {
                @Override protected void beforeHookedMethod(Xp.Param p) {
                    if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) { synchronized (sBindDrawables) { sBindDrawables.clear(); } sBinding = true; }
                }
                @Override protected void afterHookedMethod(Xp.Param p) {
                    sBinding = false;
                    View row = null;
                    try { row = p.args[0] != null ? itemView(p.args[0]) : null; } catch (Throwable ignored) { }
                    try { if (on(KEY_CLIP) && row != null && row.hasOnClickListeners()) wrapClipClick(row); } catch (Throwable ignored) { }
                    try { if (row != null && premium()) { fast(row); if (row.getParent() instanceof View) fast((View) row.getParent()); } } catch (Throwable ignored) { }
                    try { if (row != null && p.args[0] != null && premium()) cardColour(row, ad, p.thisObject, (Integer) p.args[1]); } catch (Throwable ignored) { }
                    try { if (p.args[0] != null) { GboardClipAll.ensureButton(row, p.thisObject); } } catch (Throwable t) { }
                    try {
                        if (!on(KEY_CLIP) || p.args[0] == null) return;
                        int pos = (Integer) p.args[1];
                        Object item = null;
                        for (Field f : listFields(ad)) {
                            Object l = f.get(p.thisObject);
                            if (l instanceof List && pos >= 0 && pos < ((List<?>) l).size()) { Object o = ((List<?>) l).get(pos); if (o != null && entityRecord(o) != null) { item = o; break; } }
                        }
                        if (item == null) return;
                        hideTypeIcon(p.args[0], item);
                    } catch (Throwable ignored) { }
                }
            });
        }
    }

    private static volatile float sKeyVol = -1f;
    private static boolean sClickHooked;

    private static final Map<View, android.graphics.drawable.Drawable> sCardOrig = new WeakHashMap<View, android.graphics.drawable.Drawable>();
    private static final Map<Class<?>, Object[]> sMarkers = new HashMap<Class<?>, Object[]>();

    static final class CardBg extends android.graphics.drawable.Drawable {
        final android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        final float radius;
        CardBg(float radius) { this.radius = radius; }
        void colour(int c) { if (paint.getColor() != c) { paint.setColor(c); invalidateSelf(); } }
        @Override public void draw(android.graphics.Canvas c) {
            android.graphics.Rect b = getBounds();
            c.drawRoundRect(b.left, b.top, b.right, b.bottom, radius, radius, paint);
        }
        @Override public void getOutline(android.graphics.Outline o) { o.setRoundRect(getBounds(), radius); }
        @Override public void setAlpha(int a) { }
        @Override public void setColorFilter(android.graphics.ColorFilter cf) { }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }

    private static Object[] markers(Class<?> k) {
        synchronized (sMarkers) { if (sMarkers.containsKey(k)) return sMarkers.get(k); }
        List<Object> out = new ArrayList<Object>();
        try { for (Field f : k.getDeclaredFields()) if (Modifier.isStatic(f.getModifiers()) && f.getType() == k) { f.setAccessible(true); Object o = f.get(null); if (o != null) out.add(o); } } catch (Throwable ignored) { }
        Object[] m = out.size() == 3 ? out.toArray() : null;
        synchronized (sMarkers) { sMarkers.put(k, m); }
        return m;
    }

    private static Boolean pinnedAt(List<?> l, int pos) {
        Object item = l.get(pos);
        if (item == null) return null;
        Object[] m = markers(item.getClass());
        if (m == null) return null;
        int[] at = { -1, -1, -1 };
        for (int i = 0, n = l.size(); i < n; i++) { Object o = l.get(i); for (int j = 0; j < 3; j++) if (o == m[j]) at[j] = i; }
        java.util.Arrays.sort(at);
        if (at[0] < 0) return null;
        if (pos == at[0] || pos == at[1] || pos == at[2]) return null;
        return pos > at[1] && pos < at[2];
    }

    private static void cardColour(View row, Class<?> ad, Object adapter, int pos) throws Throwable {
        List<?> list = null;
        for (Field f : listFields(ad)) { Object l = f.get(adapter); if (l instanceof List && pos >= 0 && pos < ((List<?>) l).size()) { list = (List<?>) l; break; } }
        if (list == null) return;
        Boolean pinned = pinnedAt(list, pos);
        if (pinned == null) return;
        boolean superDark = SuperDarkApps.activeAnywhere();
        boolean dark = superDark || (row.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        int colour = pinned ? (superDark ? 0xFF13263D : dark ? 0xFF2B3A4F : 0xFFD3E3FD) : (superDark ? 0xFF1C1C1E : 0);
        android.graphics.drawable.Drawable bg = row.getBackground();
        if (colour == 0) {
            if (bg instanceof CardBg) { android.graphics.drawable.Drawable o = sCardOrig.get(row); row.setBackground(o); }
            return;
        }
        if (bg instanceof CardBg) { ((CardBg) bg).colour(colour); return; }
        float r = 12 * row.getResources().getDisplayMetrics().density;
        if (bg instanceof android.graphics.drawable.GradientDrawable) {
            try { Object v = android.graphics.drawable.GradientDrawable.class.getMethod("getCornerRadius").invoke(bg); if (v instanceof Float && (Float) v > 0) r = (Float) v; } catch (Throwable ignored) { }
        }
        sCardOrig.put(row, bg);
        CardBg card = new CardBg(r);
        card.colour(colour);
        row.setBackground(card);
    }

    private static final Map<Class<?>, Field> sItemViewField = new HashMap<Class<?>, Field>();

    private static View itemView(Object holder) {
        Field cached;
        synchronized (sItemViewField) { cached = sItemViewField.get(holder.getClass()); }
        if (cached != null) { try { Object v = cached.get(holder); if (v instanceof View) return (View) v; } catch (Throwable ignored) { } }
        for (Class<?> k = holder.getClass(); k != null && k != Object.class; k = k.getSuperclass()) {
            if (!k.getName().contains("RecyclerView") && !k.getName().endsWith("ViewHolder") && k.getSuperclass() != Object.class) continue;
            for (Field f : k.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) || !View.class.isAssignableFrom(f.getType())) continue;
                try { f.setAccessible(true); Object v = f.get(holder); if (v instanceof View) { synchronized (sItemViewField) { sItemViewField.put(holder.getClass(), f); } return (View) v; } } catch (Throwable ignored) { }
            }
        }
        return null;
    }

    private static void installClipSound() {
        if (sClickHooked) return;
        sClickHooked = true;
        Xp.hookAllMethods(android.media.AudioManager.class, "playSoundEffect", new Xp.Callback() {
            @Override protected void beforeHookedMethod(Xp.Param p) {
                try {
                    if (p.args.length < 1 || !(p.args[0] instanceof Integer) || sOwnSound) return;
                    sKeyVol = p.args.length > 1 && p.args[1] instanceof Float ? (Float) p.args[1] : -1f;
                } catch (Throwable ignored) { }
            }
        });
    }

    private static boolean sOwnSound;
    private static Field sListenerInfo, sOnClick;

    private static void wrapClipClick(View row) {
        try {
            if (sListenerInfo == null) { sListenerInfo = View.class.getDeclaredField("mListenerInfo"); sListenerInfo.setAccessible(true); }
            Object li = sListenerInfo.get(row);
            if (li == null) return;
            if (sOnClick == null) { sOnClick = li.getClass().getDeclaredField("mOnClickListener"); sOnClick.setAccessible(true); }
            Object l = sOnClick.get(li);
            if (!(l instanceof View.OnClickListener) || l instanceof ClipClick) return;
            sOnClick.set(li, new ClipClick((View.OnClickListener) l));
        } catch (Throwable ignored) { }
    }

    private static final class ClipClick implements View.OnClickListener {
        final View.OnClickListener gboard;
        ClipClick(View.OnClickListener g) { gboard = g; }
        @Override public void onClick(View v) {
            try { if (on(KEY_CLIP)) clipSound(v); } catch (Throwable ignored) { }
            gboard.onClick(v);
        }
    }

    private static void clipSound(View v) {
        Context c = v.getContext();
        boolean on = false;
        try { on = ((Context) Context.class.getMethod("createDeviceProtectedStorageContext").invoke(c)).getSharedPreferences("com.google.android.inputmethod.latin_preferences", Context.MODE_PRIVATE).getBoolean("enable_sound_on_keypress", false); } catch (Throwable ignored) { }
        if (!on) try { on = c.getSharedPreferences("com.google.android.inputmethod.latin_preferences", Context.MODE_PRIVATE).getBoolean("enable_sound_on_keypress", false); } catch (Throwable ignored) { }
        if (!on) return;
        android.media.AudioManager am = (android.media.AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        if (am == null) return;
        sOwnSound = true;
        try { am.playSoundEffect(android.media.AudioManager.FX_KEYPRESS_STANDARD, sKeyVol); } finally { sOwnSound = false; }
    }

    private static final Map<Class<?>, Field[]> sListFields = new HashMap<Class<?>, Field[]>();
    private static final Map<Class<?>, Field> sEntityField = new HashMap<Class<?>, Field>();
    private static final Field NONE;
    static { Field n = null; try { n = GboardPlus.class.getDeclaredField("TAG"); } catch (Throwable ignored) { } NONE = n; }
    private static final Map<Class<?>, Field[]> sImageFields = new HashMap<Class<?>, Field[]>();
    private static final Map<Class<?>, Method[]> sIntMethods = new HashMap<Class<?>, Method[]>();
    private static final Map<Integer, Object> sTypeStates = new HashMap<Integer, Object>();
    private static int sTypeStatesNight = -1;

    private static Field[] listFields(Class<?> ad) {
        synchronized (sListFields) {
            Field[] r = sListFields.get(ad);
            if (r != null) return r;
            List<Field> out = new ArrayList<Field>();
            for (Field f : ad.getDeclaredFields()) if (!Modifier.isStatic(f.getModifiers()) && f.getType() == List.class) { f.setAccessible(true); out.add(f); }
            r = out.toArray(new Field[0]);
            sListFields.put(ad, r);
            return r;
        }
    }

    private static Object entityRecord(Object item) {
        try {
            Field cached;
            synchronized (sEntityField) { cached = sEntityField.get(item.getClass()); }
            if (cached == NONE && cached != null) return null;
            if (cached != null) return cached.get(item);
            Field found = null;
            for (Field f : item.getClass().getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive() || f.getType().getName().startsWith("java")) continue;
                int ints = 0, strings = 0;
                for (Field g : f.getType().getDeclaredFields()) { if (Modifier.isStatic(g.getModifiers())) continue; if (g.getType() == int.class) ints++; else if (g.getType() == String.class) strings++; }
                if (ints == 3 && strings >= 3) { f.setAccessible(true); found = f; break; }
            }
            synchronized (sEntityField) { sEntityField.put(item.getClass(), found != null ? found : NONE); }
            return found != null ? found.get(item) : null;
        } catch (Throwable ignored) { }
        return null;
    }

    private static void hideTypeIcon(Object holder, Object item) throws Throwable {
        Context c = null;
        Field[] imageFields;
        synchronized (sImageFields) {
            imageFields = sImageFields.get(holder.getClass());
            if (imageFields == null) {
                List<Field> out = new ArrayList<Field>();
                for (Class<?> k = holder.getClass(); k != null && k != Object.class; k = k.getSuperclass())
                    for (Field f : k.getDeclaredFields()) if (!Modifier.isStatic(f.getModifiers()) && android.widget.ImageView.class.isAssignableFrom(f.getType())) { f.setAccessible(true); out.add(f); }
                imageFields = out.toArray(new Field[0]);
                sImageFields.put(holder.getClass(), imageFields);
            }
        }
        List<android.widget.ImageView> imgs = new ArrayList<android.widget.ImageView>(imageFields.length);
        for (Field f : imageFields) { Object v = f.get(holder); if (v instanceof android.widget.ImageView) { imgs.add((android.widget.ImageView) v); c = ((View) v).getContext(); } }
        if (c == null) return;
        Method[] intMethods;
        synchronized (sIntMethods) {
            intMethods = sIntMethods.get(item.getClass());
            if (intMethods == null) {
                List<Method> out = new ArrayList<Method>();
                for (Method m : item.getClass().getDeclaredMethods()) if (!Modifier.isStatic(m.getModifiers()) && m.getReturnType() == int.class && m.getParameterTypes().length == 0) { m.setAccessible(true); out.add(m); }
                intMethods = out.toArray(new Method[0]);
                sIntMethods.put(item.getClass(), intMethods);
            }
        }
        int night = c.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        java.util.Set<Integer> ids = new java.util.HashSet<Integer>();
        List<Object> states = new ArrayList<Object>();
        synchronized (sTypeStates) {
            if (night != sTypeStatesNight) { sTypeStates.clear(); sTypeStatesNight = night; }
            for (Method m : intMethods) {
                int id;
                try { id = (Integer) m.invoke(item); } catch (Throwable t) { continue; }
                if ((id >>> 24) != 0x7f) continue;
                ids.add(id);
                Object st;
                if (sTypeStates.containsKey(id)) st = sTypeStates.get(id);
                else {
                    st = null;
                    try {
                        if ("drawable".equals(c.getResources().getResourceTypeName(id))) {
                            android.graphics.drawable.Drawable dr = c.getDrawable(id);
                            if (dr != null) st = dr.getConstantState();
                        }
                    } catch (Throwable ignored) { }
                    sTypeStates.put(id, st);
                }
                if (st != null) states.add(st);
            }
        }
        for (android.widget.ImageView iv : imgs) {
            android.graphics.drawable.Drawable dr = iv.getDrawable();
            if (dr == null || dr instanceof android.graphics.drawable.BitmapDrawable || iv.getVisibility() != View.VISIBLE) continue;
            Integer loaded;
            synchronized (sBindDrawables) { loaded = sBindDrawables.get(dr); }
            android.graphics.drawable.Drawable.ConstantState cs = dr.getConstantState();
            if ((loaded != null && ids.contains(loaded)) || (cs != null && states.contains(cs))) iv.setVisibility(View.GONE);
        }
    }

    private static boolean sLiftHooked;
    private static void prepareClipLift() {
        if (sLiftHooked) return;
        sLiftHooked = true;
        Xp.findAndHookMethod(android.animation.AnimatorSet.class, "start", new Xp.Callback() {
            @Override protected void beforeHookedMethod(Xp.Param p) {
                try {
                    if (!(p.thisObject instanceof android.animation.AnimatorSet)) return;
                    boolean clip = on(KEY_CLIP);
                    if (!clip && !premium()) return;
                    android.animation.AnimatorSet set = (android.animation.AnimatorSet) p.thisObject;
                    if (pop(set, clip)) return;
                    unscale(set, 0, clip);
                } catch (Throwable ignored) { }
            }
        });
    }

    private static void unscale(android.animation.AnimatorSet set, int depth, boolean clip) {
        if (depth > 3) return;
        for (android.animation.Animator a : set.getChildAnimations()) {
            if (a instanceof android.animation.AnimatorSet) { unscale((android.animation.AnimatorSet) a, depth + 1, clip); continue; }
            if (!(a instanceof android.animation.ObjectAnimator)) continue;
            android.animation.ObjectAnimator o = (android.animation.ObjectAnimator) a;
            String prop = o.getPropertyName();
            if (!"scaleX".equals(prop) && !"scaleY".equals(prop)) continue;
            Object t = o.getTarget();
            if (!(t instanceof View) || !liftCard((View) t, 0)) continue;
            if (!clip) { liftFast((View) t); liftColours((View) t); continue; }
            o.setFloatValues(1f, 1f);
            ((View) t).setScaleX(1f); ((View) t).setScaleY(1f);
            liftFast((View) t);
            liftColours((View) t);
            fitLift((View) t);
        }
    }

    private static final android.animation.TimeInterpolator POP_GROW = new android.view.animation.PathInterpolator(0.05f, 0.7f, 0.1f, 1f);
    private static final android.animation.TimeInterpolator POP_FADE_IN = new android.view.animation.DecelerateInterpolator(1.5f);
    private static final android.animation.TimeInterpolator POP_FADE_OUT = new android.view.animation.DecelerateInterpolator(1f);
    private static final long POP_OPEN = 260, POP_CLOSE = 150;
    private static final Map<View, WeakReference<Pop>> sPops = new WeakHashMap<View, WeakReference<Pop>>();

    private static boolean pop(android.animation.AnimatorSet set, boolean clip) {
        View card = popCard(set, 0);
        if (card == null) return false;
        View menu = popMenu(card);
        if (menu == null) return false;
        List<android.animation.ObjectAnimator> all = new ArrayList<android.animation.ObjectAnimator>();
        popAnimators(set, all, 0);
        boolean flies = false;
        View root = null;
        for (android.animation.ObjectAnimator o : all) {
            Object t = o.getTarget();
            if (!(t instanceof View)) continue;
            String prop = o.getPropertyName();
            if (t == card && ("x".equals(prop) || "y".equals(prop) || "translationX".equals(prop) || "translationY".equals(prop))) flies = true;
            if (t != card && popAncestor((View) t, card)) root = (View) t;
        }
        if (!flies) return false;
        Pop pop;
        synchronized (sPops) { WeakReference<Pop> w = sPops.get(card); pop = w == null ? null : w.get(); }
        if (root == null && pop != null) { pop.stop(); pop = null; }
        if (pop == null) {
            pop = new Pop(card, menu);
            synchronized (sPops) { sPops.put(card, new WeakReference<Pop>(pop)); }
        }
        for (android.animation.ObjectAnimator o : all) if (o.getTarget() instanceof View) o.setTarget(pop.standIn);
        try {
            int steps = Math.max(1, set.getChildAnimations().size());
            if (root == null) {
                card.setScaleX(1f); card.setScaleY(1f);
                liftFast(card);
                liftColours(card);
                if (clip) fitLift(card);
                set.setDuration(Math.max(1, POP_OPEN / steps));
                pop.open();
            } else {
                set.setDuration(Math.max(1, (POP_CLOSE + 15) / steps));
                pop.close(root);
            }
        } catch (Throwable t) {
            pop.shown();
        }
        return true;
    }

    private static View popCard(android.animation.AnimatorSet set, int depth) {
        if (depth > 3) return null;
        for (android.animation.Animator a : set.getChildAnimations()) {
            if (a instanceof android.animation.AnimatorSet) { View v = popCard((android.animation.AnimatorSet) a, depth + 1); if (v != null) return v; continue; }
            if (!(a instanceof android.animation.ObjectAnimator)) continue;
            android.animation.ObjectAnimator o = (android.animation.ObjectAnimator) a;
            String prop = o.getPropertyName();
            if (!"scaleX".equals(prop) && !"scaleY".equals(prop)) continue;
            Object t = o.getTarget();
            if (t instanceof View && liftCard((View) t, 0)) return (View) t;
        }
        return null;
    }

    private static View popMenu(View card) {
        if (!(card.getParent() instanceof View) || !(((View) card.getParent()).getParent() instanceof ViewGroup)) return null;
        View holder = (View) card.getParent();
        ViewGroup box = (ViewGroup) holder.getParent();
        for (int i = 0; i < box.getChildCount(); i++) { View c = box.getChildAt(i); if (c != holder && c instanceof android.widget.ScrollView) return c; }
        return null;
    }

    private static void popAnimators(android.animation.AnimatorSet set, List<android.animation.ObjectAnimator> out, int depth) {
        if (depth > 3) return;
        for (android.animation.Animator a : set.getChildAnimations()) {
            if (a instanceof android.animation.AnimatorSet) popAnimators((android.animation.AnimatorSet) a, out, depth + 1);
            else if (a instanceof android.animation.ObjectAnimator) out.add((android.animation.ObjectAnimator) a);
        }
    }

    private static boolean popAncestor(View a, View v) {
        for (android.view.ViewParent p = v.getParent(); p != null; p = p.getParent()) if (p == a) return true;
        return false;
    }

    private static float popMenuHeight(View m) {
        float mh = m.getHeight();
        if (m instanceof ViewGroup && ((ViewGroup) m).getChildCount() == 1) {
            int full = ((ViewGroup) m).getChildAt(0).getMeasuredHeight() + m.getPaddingTop() + m.getPaddingBottom();
            if (full > mh) mh = full;
        }
        return mh;
    }

    private static final class Pop extends android.animation.AnimatorListenerAdapter
            implements android.animation.ValueAnimator.AnimatorUpdateListener, android.view.ViewTreeObserver.OnPreDrawListener, View.OnAttachStateChangeListener {
        final View card, menu;
        final View standIn;
        final float density;
        android.animation.ValueAnimator anim;
        android.view.ViewTreeObserver watched;
        boolean closing, first, cardFade;
        View root;
        float rootFrom = 1f, scaleFrom = 1f, scaleTo = 1f;

        Pop(View card, View menu) {
            this.card = card;
            this.menu = menu;
            standIn = new View(card.getContext());
            density = card.getResources().getDisplayMetrics().density;
            menu.addOnAttachStateChangeListener(this);
        }

        void open() {
            closing = false; first = true; cardFade = false;
            card.setAlpha(1f);
            if (menu instanceof ViewGroup && ((ViewGroup) menu).getChildCount() > 0) ((ViewGroup) menu).getChildAt(0).setAlpha(1f);
            menu.setAlpha(0f);
            menu.setScaleX(0.9f); menu.setScaleY(0.9f);
            watch();
            run(POP_OPEN);
        }

        void shown() {
            stop();
            try {
                card.setAlpha(1f);
                if (menu instanceof ViewGroup && ((ViewGroup) menu).getChildCount() > 0) ((ViewGroup) menu).getChildAt(0).setAlpha(1f);
                menu.setAlpha(1f); menu.setScaleX(1f); menu.setScaleY(1f);
            } catch (Throwable ignored) { }
        }

        void close(View root) {
            stop();
            closing = true;
            this.root = root;
            rootFrom = root.getAlpha();
            scaleFrom = menu.getScaleX();
            scaleTo = Math.min(scaleFrom, 0.96f);
            showRow();
            watch();
            run(POP_CLOSE);
        }

        void run(long ms) {
            anim = android.animation.ValueAnimator.ofFloat(0f, 1f);
            anim.setDuration(ms);
            anim.setInterpolator(new android.view.animation.LinearInterpolator());
            anim.addUpdateListener(this);
            anim.addListener(this);
            anim.start();
        }

        void stop() {
            android.animation.ValueAnimator a = anim;
            anim = null;
            if (a != null) a.cancel();
            unwatch();
        }

        public void onAnimationUpdate(android.animation.ValueAnimator a) {
            if (a != anim) return;
            float f = a.getAnimatedFraction();
            if (!closing) {
                float s = 0.9f + 0.1f * POP_GROW.getInterpolation(f);
                menu.setScaleX(s); menu.setScaleY(s);
                menu.setAlpha(POP_FADE_IN.getInterpolation(Math.min(1f, f * POP_OPEN / 120f)));
                if (cardFade) card.setAlpha(0.6f + 0.4f * POP_FADE_IN.getInterpolation(Math.min(1f, f * POP_OPEN / 150f)));
            } else {
                float e = POP_FADE_OUT.getInterpolation(f);
                if (root != null) root.setAlpha(rootFrom * (1f - e));
                float s = scaleFrom + (scaleTo - scaleFrom) * e;
                menu.setScaleX(s); menu.setScaleY(s);
            }
        }

        @Override public void onAnimationEnd(android.animation.Animator a) {
            if (a != anim) return;
            anim = null;
            unwatch();
            if (!closing) { menu.setAlpha(1f); menu.setScaleX(1f); menu.setScaleY(1f); card.setAlpha(1f); }
        }

        public boolean onPreDraw() {
            try {
                if (first && !closing) {
                    first = false;
                    if (card.getParent() instanceof View && ((View) card.getParent()).getTranslationY() < -density) { cardFade = true; card.setAlpha(0.6f); }
                }
                pivot();
            } catch (Throwable ignored) { }
            return true;
        }

        void pivot() {
            View holder = (View) card.getParent();
            float cl = holder.getLeft() + holder.getTranslationX() + card.getLeft() + card.getTranslationX();
            float ct = holder.getTop() + holder.getTranslationY() + card.getTop() + card.getTranslationY();
            float cr = cl + card.getWidth(), cb = ct + card.getHeight();
            float ml = menu.getLeft() + menu.getTranslationX(), mt = menu.getTop() + menu.getTranslationY();
            float mw = menu.getWidth(), mh = popMenuHeight(menu), mr = ml + mw, mb = mt + mh, slack = 2 * density;
            float px, py;
            if (mt >= cb - slack || mb <= ct + slack) {
                py = mt >= cb - slack ? 0 : mh;
                px = Math.abs(mr - cr) <= Math.abs(ml - cl) ? mw : 0;
            } else {
                py = Math.max(0, Math.min(mh, ct - mt));
                px = ml >= cr - slack ? 0 : mr <= cl + slack ? mw : mw / 2;
            }
            if (Math.abs(menu.getPivotX() - px) > 0.5f) menu.setPivotX(px);
            if (Math.abs(menu.getPivotY() - py) > 0.5f) menu.setPivotY(py);
        }

        void showRow() {
            try {
                View decor = imeDecor();
                View rv = decor == null ? null : findClass(decor, "RecyclerView", 0);
                if (!(rv instanceof ViewGroup)) return;
                ViewGroup g = (ViewGroup) rv;
                View row = null;
                for (int i = 0; i < g.getChildCount(); i++) {
                    View c = g.getChildAt(i);
                    if (c.getVisibility() != View.INVISIBLE) continue;
                    if (row != null) return;
                    row = c;
                }
                if (row != null) row.setVisibility(View.VISIBLE);
            } catch (Throwable ignored) { }
        }

        void watch() {
            unwatch();
            try { watched = menu.getViewTreeObserver(); watched.addOnPreDrawListener(this); } catch (Throwable ignored) { watched = null; }
        }

        void unwatch() {
            android.view.ViewTreeObserver w = watched;
            watched = null;
            try { if (w != null && w.isAlive()) w.removeOnPreDrawListener(this); } catch (Throwable ignored) { }
            try { menu.getViewTreeObserver().removeOnPreDrawListener(this); } catch (Throwable ignored) { }
        }

        public void onViewAttachedToWindow(View v) { }
        public void onViewDetachedFromWindow(View v) {
            stop();
            v.removeOnAttachStateChangeListener(this);
            synchronized (sPops) { sPops.remove(card); }
        }
    }

    private static final Map<View, Long> sFitted = new WeakHashMap<View, Long>();

    private static Method sSetFrameRate;
    private static boolean sSetFrameRateLooked;
    private static float sMaxRate;
    private static final Map<View, Boolean> sFast = new WeakHashMap<View, Boolean>();

    private static void fast(View v) {
        if (v == null) return;
        synchronized (sFast) { if (sFast.containsKey(v)) return; }
        if (!sSetFrameRateLooked) {
            sSetFrameRateLooked = true;
            try { if (android.os.Build.VERSION.SDK_INT >= 35) sSetFrameRate = View.class.getMethod("setRequestedFrameRate", float.class); } catch (Throwable ignored) { }
        }
        if (sSetFrameRate == null) return;
        float rate = maxRate(v);
        if (rate <= 61f) return;
        try { sSetFrameRate.invoke(v, rate); synchronized (sFast) { sFast.put(v, Boolean.TRUE); } } catch (Throwable ignored) { }
    }

    private static float maxRate(View v) {
        if (sMaxRate > 0) return sMaxRate;
        float best = 0;
        try {
            android.view.Display d = v.getDisplay();
            if (d == null) return 0;
            for (android.view.Display.Mode m : d.getSupportedModes()) best = Math.max(best, m.getRefreshRate());
        } catch (Throwable ignored) { }
        if (best > 0) sMaxRate = best;
        return best;
    }

    private static void liftFast(View card) {
        try {
            fast(card);
            if (!(card.getParent() instanceof View)) return;
            View holder = (View) card.getParent();
            fast(holder);
            if (!(holder.getParent() instanceof ViewGroup)) return;
            ViewGroup box = (ViewGroup) holder.getParent();
            fast(box);
            for (int i = 0; i < box.getChildCount(); i++) fast(box.getChildAt(i));
        } catch (Throwable ignored) { }
    }

    private static void fitLift(final View card) {
        try {
            long now = android.os.SystemClock.uptimeMillis();
            Long last = sFitted.get(card);
            if (last != null && now - last < 150) return;
            if (!(card.getParent() instanceof View)) return;
            final View holder = (View) card.getParent();
            if (!(holder.getParent() instanceof ViewGroup)) return;
            final ViewGroup box = (ViewGroup) holder.getParent();
            View menu = null;
            for (int i = 0; i < box.getChildCount(); i++) { View c = box.getChildAt(i); if (c != holder && c instanceof android.widget.ScrollView) { menu = c; break; } }
            int rowW = clipRowWidth();
            if (menu == null || rowW <= 0) return;
            sFitted.put(card, now);
            final View m = menu;
            try { box.setClipChildren(false); box.setClipToPadding(false); } catch (Throwable ignored) { }
            ViewGroup.LayoutParams lp = holder.getLayoutParams();
            float dens = box.getResources().getDisplayMetrics().density;
            int side = Math.max(holder.getLeft(), Math.round(12 * dens));
            int maxW = box.getWidth() > 0 ? box.getWidth() - 2 * side : rowW;
            int w = Math.min(rowW, Math.max(1, maxW));
            if (lp != null && lp.width != w) { lp.width = w; holder.setLayoutParams(lp); }
            if (box.getWidth() > 0) holder.setTranslationX(side - holder.getLeft());
            final long until = android.os.SystemClock.uptimeMillis() + 900;
            box.getViewTreeObserver().addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener() {
                float lifted;
                public boolean onPreDraw() {
                    if (android.os.SystemClock.uptimeMillis() > until || !box.isAttachedToWindow()) {
                        box.getViewTreeObserver().removeOnPreDrawListener(this);
                        return true;
                    }
                    if (card.getHeight() <= 0 || m.getHeight() <= 0) return true;
                    try { keepClear(m, box.getContext()); } catch (Throwable ignored) { }
                    float d = box.getResources().getDisplayMetrics().density, gap = 8 * d;
                    float mLeft = m.getLeft(), mTop = m.getTop();
                    float cLeft = holder.getLeft() + holder.getTranslationX() + card.getLeft();
                    float cTop = holder.getTop() + holder.getTranslationY() + card.getTop() + lifted;
                    float boxTop = 0, boxBottom = box.getHeight();
                    float wantLeft = cLeft + card.getWidth() - m.getWidth() - 12 * d;
                    float mh = m.getHeight();
                    if (m instanceof ViewGroup && ((ViewGroup) m).getChildCount() == 1) {
                        int full = ((ViewGroup) m).getChildAt(0).getMeasuredHeight() + m.getPaddingTop() + m.getPaddingBottom();
                        if (full > mh) {
                            mh = full;
                            ViewGroup.LayoutParams lp = m.getLayoutParams();
                            if (lp != null && lp.height != full) { lp.height = full; m.setLayoutParams(lp); }
                        }
                    }
                    float below = cTop + card.getHeight() + gap, above = cTop - gap - mh;
                    float wantTop, lift = 0;
                    if (below + mh <= boxBottom) wantTop = below;
                    else if (above >= boxTop) wantTop = above;
                    else {
                        lift = Math.max(0, Math.min(below + mh - boxBottom, cTop - boxTop));
                        wantTop = Math.min(below - lift, boxBottom - mh);
                    }
                    if (Math.abs(lift - lifted) > 0.5f) { holder.setTranslationY(holder.getTranslationY() - (lift - lifted)); lifted = lift; }
                    float tx = wantLeft - mLeft, ty = wantTop - mTop;
                    if (Math.abs(m.getTranslationX() - tx) > 0.5f) m.setTranslationX(tx);
                    if (Math.abs(m.getTranslationY() - ty) > 0.5f) m.setTranslationY(ty);
                    return true;
                }
            });
        } catch (Throwable ignored) { }
    }

    private static void liftColours(View card) {
        boolean styled = false;
        try { styled = liftMenu(card); } catch (Throwable ignored) { }
        if (styled) return;
        try {
            if (!SuperDarkApps.activeAnywhere()) return;
            int cardColour = 0xFF1C1C1E;
            View decor = imeDecor();
            View rv = decor == null ? null : findClass(decor, "RecyclerView", 0);
            if (rv instanceof ViewGroup) {
                ViewGroup g = (ViewGroup) rv;
                for (int i = 0; i < g.getChildCount(); i++) {
                    View c = g.getChildAt(i);
                    if (c.getVisibility() == View.INVISIBLE && c.getBackground() instanceof CardBg) { cardColour = ((CardBg) c.getBackground()).paint.getColor(); break; }
                }
            }
            surface(card, cardColour, 0);
            if (!(card.getParent() instanceof View) || !(((View) card.getParent()).getParent() instanceof ViewGroup)) return;
            View holder = (View) card.getParent();
            ViewGroup box = (ViewGroup) holder.getParent();
            for (int i = 0; i < box.getChildCount(); i++) {
                View c = box.getChildAt(i);
                if (c == holder || !(c instanceof android.widget.ScrollView)) continue;
                if (c.getBackground() instanceof CardBg) c.setBackground(null);
                if (!(c.getBackground() instanceof PillFrost)) tintBlocks(c, 0xFF1C1C1E, 0);
                break;
            }
        } catch (Throwable ignored) { }
    }

    private static boolean liftMenu(View card) {
        if (!(card.getParent() instanceof View) || !(((View) card.getParent()).getParent() instanceof ViewGroup)) return false;
        View holder = (View) card.getParent();
        ViewGroup box = (ViewGroup) holder.getParent();
        android.content.Context c = card.getContext();
        if (!SurfaceStyle.restyled(c) && !SuperDark.inEffect(c)) return false;
        View decor0 = imeDecor();
        boolean same0 = decor0 != null && card.getRootView() == decor0.getRootView();
        View cardBg = backgroundView(card, 0);
        if (cardBg != null) {
            float r = 16 * c.getResources().getDisplayMetrics().density;
            android.graphics.drawable.Drawable old = cardBg.getBackground();
            if (old instanceof CardBg) r = ((CardBg) old).radius;
            else if (old instanceof android.graphics.drawable.GradientDrawable) {
                try { Object x = android.graphics.drawable.GradientDrawable.class.getMethod("getCornerRadius").invoke(old); if (x instanceof Float && (Float) x > 0) r = (Float) x; } catch (Throwable ignored) { }
            }
            if (!(old instanceof PillFrost)) PillFrost.frost(cardBg, same0, r).text(card);
        }
        for (int i = 0; i < box.getChildCount(); i++) {
            View m = box.getChildAt(i);
            if (m == holder || !(m instanceof android.widget.ScrollView)) continue;
            View decor = imeDecor();
            boolean sameWindow = decor != null && m.getRootView() == decor.getRootView();
            PillFrost d = PillFrost.frost(m, sameWindow, FrostedSurface.dp(c, 24));
            d.text(m);
            boolean light = !d.superDark && !FrostedSurface.dark(c);
            clearBlocks(m, light ? 0xFF1F1F1F : 0xFFFFFFFF, light ? 0x1F000000 : 0x33FFFFFF, 0);
            return true;
        }
        return false;
    }

    private static View backgroundView(View v, int depth) {
        if (v.getBackground() != null) return v;
        if (depth >= 2 || !(v instanceof ViewGroup)) return null;
        ViewGroup g = (ViewGroup) v;
        for (int i = 0; i < g.getChildCount(); i++) { View f = backgroundView(g.getChildAt(i), depth + 1); if (f != null) return f; }
        return null;
    }

    private static void clearBlocks(View v, int ink, int ripple, int depth) {
        if (depth > 0) {
            if (v instanceof android.widget.ImageView) { ((android.widget.ImageView) v).setImageTintList(android.content.res.ColorStateList.valueOf(ink)); return; }
            if (v.getBackground() != null && !(v instanceof android.widget.TextView) && !(v.getBackground() instanceof ClearShape)) {
                android.graphics.drawable.Drawable bg = v.getBackground();
                if (bg instanceof android.graphics.drawable.RippleDrawable) {
                    android.graphics.drawable.RippleDrawable r = (android.graphics.drawable.RippleDrawable) bg;
                    for (int j = 0; j < r.getNumberOfLayers(); j++) if (r.getId(j) != android.R.id.mask && r.getDrawable(j) != null) bg = r.getDrawable(j);
                }
                ClearShape c = new ClearShape();
                c.radii(bg);
                boolean was = sOwnVis; sOwnVis = true;
                try { v.setBackgroundTintList(null); v.setBackground(c); } finally { sOwnVis = was; }
            }
        }
        if (depth >= 4 || !(v instanceof ViewGroup)) return;
        ViewGroup g = (ViewGroup) v;
        for (int i = 0; i < g.getChildCount(); i++) clearBlocks(g.getChildAt(i), ink, ripple, depth + 1);
    }

    static final class ClearShape extends android.graphics.drawable.GradientDrawable {
        ClearShape() { super.setColor(0); }
        void radii(android.graphics.drawable.Drawable from) {
            try {
                if (from instanceof android.graphics.drawable.GradientDrawable) {
                    Class<?> gd = android.graphics.drawable.GradientDrawable.class;
                    Object r = gd.getMethod("getCornerRadii").invoke(from);
                    if (r instanceof float[]) { super.setCornerRadii((float[]) r); return; }
                    Object one = gd.getMethod("getCornerRadius").invoke(from);
                    if (one instanceof Float) super.setCornerRadius((Float) one);
                }
            } catch (Throwable ignored) { }
        }
        @Override public void setColor(int c) { }
        @Override public void setColor(android.content.res.ColorStateList c) { }
        @Override public void setColors(int[] c) { }
        @Override public void setTintList(android.content.res.ColorStateList t) { }
        @Override public void setColorFilter(android.graphics.ColorFilter f) { }
        @Override public void setAlpha(int a) { }
        @Override public void draw(android.graphics.Canvas canvas) { }
    }

    private static void keepClear(View menu, android.content.Context c) {
        if (!(menu instanceof ViewGroup) || !(menu.getBackground() instanceof PillFrost)) return;
        boolean light = !SuperDark.inEffect(c) && !FrostedSurface.dark(c);
        ViewGroup g = (ViewGroup) menu;
        for (int i = 0; i < g.getChildCount(); i++) {
            View stack = g.getChildAt(i);
            if (!(stack instanceof ViewGroup)) continue;
            ViewGroup st = (ViewGroup) stack;
            for (int k = 0; k < st.getChildCount(); k++) {
                View item = st.getChildAt(k);
                if (item.getBackground() != null && !(item.getBackground() instanceof ClearShape)) clearBlocks(item, light ? 0xFF1F1F1F : 0xFFFFFFFF, 0, 1);
            }
        }
    }

    private static void tintBlocks(View v, int colour, int depth) {
        android.graphics.drawable.Drawable bg = v.getBackground();
        if (depth > 0 && bg != null && !(v instanceof android.widget.TextView) && !(v instanceof android.widget.ImageView) && texts(v, 0) == 1) {
            bg = bg.mutate();
            android.graphics.PorterDuffColorFilter f = new android.graphics.PorterDuffColorFilter(colour, android.graphics.PorterDuff.Mode.SRC_IN);
            if (bg instanceof android.graphics.drawable.RippleDrawable) {
                android.graphics.drawable.RippleDrawable r = (android.graphics.drawable.RippleDrawable) bg;
                for (int j = 0; j < r.getNumberOfLayers(); j++) if (r.getId(j) != android.R.id.mask) r.getDrawable(j).setColorFilter(f);
            } else bg.setColorFilter(f);
        }
        if (depth >= 3 || !(v instanceof ViewGroup)) return;
        ViewGroup g = (ViewGroup) v;
        for (int i = 0; i < g.getChildCount(); i++) tintBlocks(g.getChildAt(i), colour, depth + 1);
    }

    private static int texts(View v, int depth) {
        if (v instanceof android.widget.TextView) return 1;
        if (depth > 4 || !(v instanceof ViewGroup)) return 0;
        int n = 0; ViewGroup g = (ViewGroup) v;
        for (int i = 0; i < g.getChildCount(); i++) n += texts(g.getChildAt(i), depth + 1);
        return n;
    }

    private static boolean surface(View v, int colour, int depth) {
        android.graphics.drawable.Drawable bg = v.getBackground();
        if (bg instanceof CardBg) { ((CardBg) bg).colour(colour); return true; }
        if (bg != null) {
            float r = 16 * v.getResources().getDisplayMetrics().density;
            if (bg instanceof android.graphics.drawable.GradientDrawable) {
                try { Object x = android.graphics.drawable.GradientDrawable.class.getMethod("getCornerRadius").invoke(bg); if (x instanceof Float && (Float) x > 0) r = (Float) x; } catch (Throwable ignored) { }
            }
            CardBg c = new CardBg(r);
            c.colour(colour);
            v.setBackground(c);
            return true;
        }
        if (depth >= 2 || !(v instanceof ViewGroup)) return false;
        ViewGroup g = (ViewGroup) v;
        for (int i = 0; i < g.getChildCount(); i++) if (surface(g.getChildAt(i), colour, depth + 1)) return true;
        return false;
    }

    private static int clipRowWidth() {
        View decor = imeDecor();
        View rv = decor == null ? null : findClass(decor, "RecyclerView", 0);
        if (rv instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) rv;
            for (int i = 0; i < g.getChildCount(); i++) { View c = g.getChildAt(i); if (c.getVisibility() == View.VISIBLE && c.getWidth() > 0) return c.getWidth(); }
        }
        return -1;
    }

    private static View findClass(View v, String suffix, int depth) {
        if (v.getClass().getName().endsWith(suffix) && v.isShown()) return v;
        if (depth > 25 || !(v instanceof ViewGroup)) return null;
        ViewGroup g = (ViewGroup) v;
        for (int i = 0; i < g.getChildCount(); i++) { View f = findClass(g.getChildAt(i), suffix, depth + 1); if (f != null) return f; }
        return null;
    }

    private static boolean liftCard(View v, int depth) {
        if (v.getClass().getName().endsWith("TransformationTextView")) return true;
        if (depth > 3 || !(v instanceof ViewGroup)) return false;
        ViewGroup g = (ViewGroup) v;
        for (int i = 0; i < g.getChildCount(); i++) if (liftCard(g.getChildAt(i), depth + 1)) return true;
        return false;
    }

    private static boolean sClipConfirmed;
    private static WeakReference<android.app.Dialog> sClipDialog;

    static void prepareClipConfirm(ClassLoader cl) throws Throwable {
        final Class<?> ck = cl.loadClass("com.google.android.apps.inputmethod.libs.clipboard.ClipboardKeyboard");
        for (final Method m : ck.getDeclaredMethods()) {
            Class<?>[] t = m.getParameterTypes();
            if (Modifier.isStatic(m.getModifiers()) || m.getReturnType() != void.class || t.length != 3 || t[1] != int.class || t[2] != boolean.class || t[0].isPrimitive() || t[0].getName().startsWith("java") || t[0].getName().startsWith("android")) continue;
            Xp.hookMethod(m, new Xp.Callback() {
                @Override protected void beforeHookedMethod(final Xp.Param p) {
                    try {
                        if (sClipConfirmed) { sClipConfirmed = false; return; }
                        if (!on(KEY_CLIP) || p.args[0] == null || !pinned(p.args[0])) return;
                        p.setResult(null);
                        final Object kb = p.thisObject;
                        final Object[] args = p.args.clone();
                        MAIN.post(new Runnable() { public void run() { askDelete(kb, m, args); } });
                    } catch (Throwable ignored) { }
                }
            });
        }
    }

    private static boolean pinned(Object item) {
        try {
            for (Field f : item.getClass().getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive() || f.getType().getName().startsWith("java")) continue;
                List<Field> ints = new ArrayList<Field>();
                int strings = 0;
                for (Field g : f.getType().getDeclaredFields()) { if (Modifier.isStatic(g.getModifiers())) continue; if (g.getType() == int.class) ints.add(g); else if (g.getType() == String.class) strings++; }
                if (ints.size() != 3 || strings < 3) continue;
                java.util.Collections.sort(ints, new java.util.Comparator<Field>() { public int compare(Field a, Field b) { return a.getName().compareTo(b.getName()); } });
                f.setAccessible(true);
                Object rec = f.get(item);
                if (rec == null) return false;
                ints.get(0).setAccessible(true);
                return (ints.get(0).getInt(rec) & 1) != 0;
            }
        } catch (Throwable ignored) { }
        return false;
    }

    private static String clipText(Object item) {
        try {
            Object rec = entityRecord(item);
            if (rec == null) return null;
            for (Field g : rec.getClass().getDeclaredFields()) if (!Modifier.isStatic(g.getModifiers()) && g.getType() == boolean.class) { g.setAccessible(true); if (g.getBoolean(rec)) return null; }
            List<Field> strs = new ArrayList<Field>();
            for (Field g : rec.getClass().getDeclaredFields()) if (!Modifier.isStatic(g.getModifiers()) && g.getType() == String.class) strs.add(g);
            java.util.Collections.sort(strs, new java.util.Comparator<Field>() { public int compare(Field a, Field b) { return a.getName().compareTo(b.getName()); } });
            if (strs.isEmpty()) return null;
            strs.get(0).setAccessible(true);
            Object v = strs.get(0).get(rec);
            return v instanceof String ? (String) v : null;
        } catch (Throwable t) { return null; }
    }

    private static void askDelete(final Object kb, final Method del, final Object[] args) {
        try {
            android.app.Dialog old = sClipDialog == null ? null : sClipDialog.get();
            if (old != null && old.isShowing()) return;
            InputMethodService ims = sIme.get();
            android.app.Dialog win = ims == null ? null : ims.getWindow();
            View decor = win == null || win.getWindow() == null ? null : win.getWindow().peekDecorView();
            if (ims == null || decor == null || decor.getWindowToken() == null) { restoreClips(kb); return; }
            boolean dark = sDarkKeys != null ? sDarkKeys : (ims.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES;
            Context c = new android.view.ContextThemeWrapper(ims, dark ? android.R.style.Theme_DeviceDefault_Dialog_Alert : android.R.style.Theme_DeviceDefault_Light_Dialog_Alert);
            String text = clipText(args[0]);
            if (text != null && text.length() > 120) text = text.substring(0, 117) + "…";
            final boolean[] done = { false };
            android.app.AlertDialog.Builder b = new android.app.AlertDialog.Builder(c)
                    .setTitle(I18n.cs("Delete this pinned clip?"))
                    .setNegativeButton(I18n.cs("Cancel"), null)
                    .setPositiveButton(I18n.cs("Delete"), new android.content.DialogInterface.OnClickListener() {
                        @Override public void onClick(android.content.DialogInterface d, int w) {
                            done[0] = true;
                            try { del.setAccessible(true); sClipConfirmed = true; del.invoke(kb, args); } catch (Throwable t) { sClipConfirmed = false; restoreClips(kb); }
                        }
                    });
            if (text != null && !text.isEmpty()) b.setMessage(I18n.cs(text));
            final android.app.AlertDialog d = b.create();
            d.setOnDismissListener(new android.content.DialogInterface.OnDismissListener() {
                @Override public void onDismiss(android.content.DialogInterface x) { if (!done[0]) restoreClips(kb); }
            });
            android.view.Window w = d.getWindow();
            if (w != null) {
                android.view.WindowManager.LayoutParams lp = w.getAttributes();
                lp.token = decor.getWindowToken();
                lp.type = android.view.WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG;
                w.setAttributes(lp);
                w.addFlags(android.view.WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
            }
            sClipDialog = new WeakReference<android.app.Dialog>(d);
            d.show();
            boolean black = dark && superDark(ims);
            float dp = ims.getResources().getDisplayMetrics().density;
            int bg = dark ? (black ? 0xFF1C1C1E : 0xFF2C2C2E) : 0xFFF2F2F7;
            int fg = dark ? 0xFFFFFFFF : 0xFF000000;
            int sub = dark ? 0xFFAEAEB2 : 0xFF3C3C43;
            boolean styled = false;
            if (w != null && (SurfaceStyle.restyled(d.getContext()) || black)) {
                try { FrostedSurface.compact(d); FrostedSurface.style(d); styled = true; } catch (Throwable ignored) { }
            }
            if (w != null && !styled) {
                android.graphics.drawable.GradientDrawable shape = new android.graphics.drawable.GradientDrawable();
                shape.setColor(bg);
                shape.setCornerRadius(28 * dp);
                w.setBackgroundDrawable(new android.graphics.drawable.InsetDrawable(shape, (int) (16 * dp)));
            }
            android.widget.Button cancel = d.getButton(android.content.DialogInterface.BUTTON_NEGATIVE);
            android.widget.Button delete = d.getButton(android.content.DialogInterface.BUTTON_POSITIVE);
            if (cancel != null) { cancel.setTextColor(dark ? 0xFF0A84FF : 0xFF007AFF); cancel.setAllCaps(false); }
            if (delete != null) { delete.setTextColor(dark ? 0xFFFF453A : 0xFFFF3B30); delete.setAllCaps(false); }
            int titleId = ims.getResources().getIdentifier("alertTitle", "id", "android");
            View title = titleId == 0 ? null : d.findViewById(titleId);
            if (title instanceof TextView) ((TextView) title).setTextColor(fg);
            View msg = d.findViewById(android.R.id.message);
            if (msg instanceof TextView) ((TextView) msg).setTextColor(sub);
        } catch (Throwable t) { restoreClips(kb); }
    }

    private static void restoreClips(Object kb) {
        try {
            for (Class<?> k = kb.getClass(); k != null && k != Object.class; k = k.getSuperclass())
                for (Field f : k.getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive()) continue;
                    f.setAccessible(true);
                    Object v = f.get(kb);
                    if (v == null) continue;
                    if (v instanceof ViewGroup && v.getClass().getName().endsWith("RecyclerView")) {
                        ViewGroup rv = (ViewGroup) v;
                        for (int i = 0; i < rv.getChildCount(); i++) { View ch = rv.getChildAt(i); ch.setTranslationX(0f); ch.setAlpha(1f); }
                    }
                    if (sClipAdapter != null && sClipAdapter.isInstance(v)) {
                        Class<?> base = v.getClass().getSuperclass();
                        if (base == null) continue;
                        for (Method m : base.getDeclaredMethods())
                            if (!Modifier.isStatic(m.getModifiers()) && m.getReturnType() == void.class && m.getParameterTypes().length == 0) { m.setAccessible(true); m.invoke(v); break; }
                    }
                }
        } catch (Throwable ignored) { }
    }

    static void prepareClipCopies(InputMethodService ims, ClassLoader cl) throws Throwable {
        String name = Shortcuts.cachedClass(ims, "clipdata", "This copied item is a duplicate of an item that is already pinned.");
        if (name == null) { return; }
        for (Method m : cl.loadClass(name).getDeclaredMethods()) {
            Class<?>[] t = m.getParameterTypes();
            if (Modifier.isStatic(m.getModifiers()) || m.getReturnType() != void.class || t.length != 1 || t[0] == List.class || !List.class.isAssignableFrom(t[0])) continue;
            final Class<?> listType = t[0];
            Xp.hookMethod(m, new Xp.Callback() {
                @Override protected void beforeHookedMethod(Xp.Param p) {
                    try {
                        if (!on(KEY_CLIP) || !(p.args[0] instanceof List)) return;
                        List<?> items = (List<?>) p.args[0];
                        if (items.size() < 2) return;
                        Object kept = null; int plain = 0;
                        for (Object it : items) if (entityType(it) == 0) { plain++; if (kept == null) kept = it; }
                        if (plain != 1 || kept == null) return;
                        Method one = null;
                        for (Method f : listType.getDeclaredMethods())
                            if (Modifier.isStatic(f.getModifiers()) && f.getParameterTypes().length == 1 && f.getParameterTypes()[0] == Object.class && listType.isAssignableFrom(f.getReturnType())) { one = f; break; }
                        if (one == null) return;
                        one.setAccessible(true);
                        Object single = one.invoke(null, kept);
                        if (single instanceof List && ((List<?>) single).size() == 1) p.args[0] = single;
                    } catch (Throwable ignored) { }
                }
            });
        }
    }

    private static int entityType(Object item) throws Throwable {
        for (Field f : item.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive() || f.getType().getName().startsWith("java")) continue;
            List<Field> ints = new ArrayList<Field>();
            int strings = 0;
            for (Field g : f.getType().getDeclaredFields()) {
                if (Modifier.isStatic(g.getModifiers())) continue;
                if (g.getType() == int.class) ints.add(g); else if (g.getType() == String.class) strings++;
            }
            if (ints.size() != 3 || strings < 3) continue;
            java.util.Collections.sort(ints, new java.util.Comparator<Field>() { public int compare(Field a, Field b) { return a.getName().compareTo(b.getName()); } });
            f.setAccessible(true);
            Object rec = f.get(item);
            if (rec == null) return 0;
            ints.get(1).setAccessible(true);
            return ints.get(1).getInt(rec);
        }
        return 0;
    }

    private static void noTypeIcons(View v, float d) {
        if (v instanceof android.widget.ImageView) {
            ViewGroup.LayoutParams lp = v.getLayoutParams();
            if (lp != null && lp.width == ViewGroup.LayoutParams.MATCH_PARENT) return;
            int h = v.getHeight() > 0 ? v.getHeight() : lp != null && lp.height > 0 ? lp.height : 0;
            android.graphics.drawable.Drawable dr = ((android.widget.ImageView) v).getDrawable();
            if (h <= 0 && dr != null) h = dr.getIntrinsicHeight();
            if (h > 0 && h <= 32 * d && v.getVisibility() != View.GONE) v.setVisibility(View.GONE);
            return;
        }
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) noTypeIcons(((ViewGroup) v).getChildAt(i), d);
    }

    static final class Log {
        static void w(String what, Throwable t) { }
    }
}
