package io.github.pixelsuite;

import java.util.ArrayList;
import java.util.List;

final class ScreenRecordTidy {
    static final String KEY = "pixelsuite_screen_record_tidy";
    private ScreenRecordTidy() {}

    static void install(ClassLoader cl) {
        try { composeHooks(cl); } catch (Throwable error) { }
        try {
            Xp.hookAllMethods(android.content.res.Resources.class, "getText", new Xp.Callback() {
                protected void afterHookedMethod(Xp.Param p) {
                    try {
                        if (p.args.length != 1) return;
                        Object r = p.getResult();
                        if (!(r instanceof CharSequence) || ((CharSequence) r).length() < 60 || !((CharSequence) r).toString().contains("careful with things like passwords")) return;
                        if (enabled()) p.setResult("");
                    } catch (Throwable ignored) { }
                }
            });
            ComposeFrost.install("screen recording sheet", "ScreenCaptureUi");
            Xp.hookAllMethods(android.app.Activity.class, "onResume", new Xp.Callback() {
                protected void afterHookedMethod(Xp.Param p) {
                    if (!p.thisObject.getClass().getName().endsWith("PostRecordingActivity")) return;
                    recordingFront = true;
                    if (SuperDark.inEffectNow()) DarkChips.on("screen recording");
                }
            });
            Xp.hookAllMethods(android.app.Activity.class, "onCreate", new Xp.Callback() {
                protected void afterHookedMethod(Xp.Param p) {
                    if (p.thisObject.getClass().getName().endsWith("PostRecordingActivity")) WindowFrost.frost((android.app.Activity) p.thisObject, "recording preview", false);
                }
            });
            Xp.hookAllMethods(android.app.Dialog.class, "onStart", new Xp.Callback() {
                protected void afterHookedMethod(Xp.Param p) {
                    try {
                        android.app.Dialog d = (android.app.Dialog) p.thisObject;
                        if (d.getWindow() == null) return;
                        boolean known; synchronized (pills) { known = pills.containsKey(d); }
                        if (known || snackbar(d)) { pill(d); return; }
                        if (!recordingFront || SuperDark.inEffect(d.getContext()) || !SurfaceStyle.restyled(d.getContext())) return;
                        frostDialog(d.getWindow().getDecorView(), d.getWindow());
                    } catch (Throwable ignored) { }
                }
            });
            try {
                Class<?> snack = Class.forName("com.android.systemui.screencapture.record.smallscreen.ui.SnackbarDialogDelegate", false, cl);
                Xp.hookAllMethods(snack, "onCreate", new Xp.Callback() {
                    protected void afterHookedMethod(Xp.Param p) {
                        try {
                            if (p.args[0] instanceof android.app.Dialog) pill((android.app.Dialog) p.args[0]);
                        } catch (Throwable ignored) { }
                    }
                });
            } catch (Throwable error) { }
            Xp.hookAllMethods(android.app.Activity.class, "onPause", new Xp.Callback() {
                protected void afterHookedMethod(Xp.Param p) {
                    if (p.thisObject.getClass().getName().endsWith("PostRecordingActivity")) { recordingFront = false; DarkChips.off(); }
                }
            });
        } catch (Throwable error) { }
    }

    static volatile boolean recordingFront;

    static void pill(android.app.Dialog d) {
        synchronized (pills) { pills.put(d, Boolean.TRUE); }
        final android.view.Window w = d.getWindow(); if (w == null) return;
        final android.view.View decor = w.getDecorView();
        restyle(decor, w);
        decor.post(() -> restyle(decor, w));
        decor.getViewTreeObserver().addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener() {
            int frames;
            public boolean onPreDraw() {
                restyle(decor, w);
                if (++frames >= 3 || !decor.isAttachedToWindow()) decor.getViewTreeObserver().removeOnPreDrawListener(this);
                return true;
            }
        });
    }

    private static final java.util.Map<android.view.View, Object[]> styled = new java.util.WeakHashMap<android.view.View, Object[]>();

    private static int look(android.content.Context c) {
        if (!SurfaceStyle.restyled(c)) return -1;
        return (SuperDark.inEffect(c) ? 1 : 0) | (FrostedSurface.dark(c) ? 2 : 0) | (FrostedSurface.blurEnabled(c) ? 4 : 0) | (SurfaceStyle.get(c) << 3);
    }

    private static void restyle(android.view.View decor, android.view.Window w) {
        try {
            android.content.Context c = decor.getContext();
            android.view.View content = decor.findViewById(android.R.id.content);
            android.view.View root = content == null ? decor : content;
            int now = look(c);
            android.widget.TextView text = SystemToasts.message(root);
            if (text != null) {
                android.view.View card = null;
                for (android.view.View at = text; at != null && at != decor; at = at.getParent() instanceof android.view.View ? (android.view.View) at.getParent() : null)
                    if (SystemToasts.shows(at.getBackground())) { card = at; break; }
                if (card == null) card = decor;
                Object[] was = styled.get(card);
                if (was != null && (Integer) was[0] == now) return;
                if (was == null) {
                    if (now < 0) return;
                    java.util.Map<android.widget.TextView, android.content.res.ColorStateList> colors = new java.util.HashMap<android.widget.TextView, android.content.res.ColorStateList>();
                    keepColors(root, colors);
                    was = new Object[]{now, card.getBackground(), card.getForeground(), card.getClipToOutline(), card == decor ? decor.getBackground() : null, colors};
                    styled.put(card, was);
                }
                was[0] = now;
                if (now < 0) {
                    card.setBackground((android.graphics.drawable.Drawable) was[1]);
                    card.setForeground((android.graphics.drawable.Drawable) was[2]);
                    card.setClipToOutline((Boolean) was[3]);
                    if (card == decor) w.setBackgroundDrawable((android.graphics.drawable.Drawable) was[4]);
                    @SuppressWarnings("unchecked") java.util.Map<android.widget.TextView, android.content.res.ColorStateList> colors = (java.util.Map<android.widget.TextView, android.content.res.ColorStateList>) was[5];
                    for (java.util.Map.Entry<android.widget.TextView, android.content.res.ColorStateList> e : colors.entrySet()) e.getKey().setTextColor(e.getValue());
                    return;
                }
                PillFrost f = PillFrost.apply(card, root, false);
                if (card == decor) w.setBackgroundDrawable(f);
                return;
            }
            if (!(root instanceof android.view.ViewGroup) || ((android.view.ViewGroup) root).getChildCount() == 0) return;
            android.view.View host = ((android.view.ViewGroup) root).getChildAt(0);
            if (!drawsCompose(host) || host.getWidth() == 0 || host.getHeight() == 0) return;
            Object[] was = styled.get(host);
            if (was != null && (Integer) was[0] == now && Boolean.TRUE.equals(was[1])) return;
            if (was == null) { if (now < 0) return; was = new Object[]{now, Boolean.FALSE}; styled.put(host, was); }
            was[0] = now;
            if (now < 0) { was[1] = Boolean.TRUE; host.setLayerType(android.view.View.LAYER_TYPE_NONE, null); dropBacking((android.view.ViewGroup) root); return; }
            was[1] = recolor(host, c);
            final android.view.View h = host; final android.view.ViewGroup r = (android.view.ViewGroup) root; final android.view.Window win = w;
            host.postDelayed(() -> measure(h, r, win, 0), 48);
        } catch (Throwable ignored) { }
    }

    private static final float LO = 249f, HI = 85f;
    private static final java.util.Map<android.view.ViewGroup, android.view.View> backings = new java.util.WeakHashMap<android.view.ViewGroup, android.view.View>();

    private static int ink(android.content.Context c) { return !SuperDark.inEffect(c) && !FrostedSurface.dark(c) ? 0xff1f1f1f : 0xffffffff; }

    private static void dropBacking(android.view.ViewGroup root) {
        android.view.View b = backings.remove(root);
        if (b != null && b.getParent() == root) root.removeView(b);
    }

    private static void measure(final android.view.View host, final android.view.ViewGroup root, final android.view.Window w, final int tries) {
        try {
            if (!host.isAttachedToWindow() || !(root instanceof android.widget.FrameLayout)) return;
            final android.view.View decor = w.getDecorView();
            final int dw = decor.getWidth(), dh = decor.getHeight();
            if (dw <= 0 || dh <= 0) return;
            final int sw = Math.max(1, dw / 2), sh = Math.max(1, dh / 2);
            final android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(sw, sh, android.graphics.Bitmap.Config.ARGB_8888);
            android.view.PixelCopy.request(w, bmp, result -> {
                try {
                    if (result != android.view.PixelCopy.SUCCESS) { if (tries < 3) host.postDelayed(() -> measure(host, root, w, tries + 1), 120); return; }
                    int[] px = new int[sw * sh];
                    bmp.getPixels(px, 0, sw, 0, 0, sw, sh);
                    int l = sw, t = sh, r = -1, b = -1;
                    for (int y = 0; y < sh; y++) for (int x = 0; x < sw; x++) {
                        if ((px[y * sw + x] >>> 24) < 200) continue;
                        if (x < l) l = x; if (x > r) r = x; if (y < t) t = y; if (y > b) b = y;
                    }
                    if (r - l < 20 || b - t < 8) { if (tries < 3) host.postDelayed(() -> measure(host, root, w, tries + 1), 120); return; }
                    android.graphics.Rect box = new android.graphics.Rect(l * dw / sw, t * dh / sh, (r + 1) * dw / sw, (b + 1) * dh / sh);
                    back(host, root, box);
                } catch (Throwable ignored) {
                } finally { bmp.recycle(); }
            }, new android.os.Handler(android.os.Looper.getMainLooper()));
        } catch (Throwable ignored) { }
    }

    private static void back(android.view.View host, android.view.ViewGroup root, android.graphics.Rect box) {
        int[] at = new int[2]; root.getLocationInWindow(at);
        android.view.View b = backings.get(root);
        if (b == null || b.getParent() != root) { b = new android.view.View(root.getContext()); backings.put(root, b); root.addView(b, 0, new android.widget.FrameLayout.LayoutParams(box.width(), box.height())); }
        android.widget.FrameLayout.LayoutParams lp = (android.widget.FrameLayout.LayoutParams) b.getLayoutParams();
        lp.width = box.width(); lp.height = box.height(); lp.leftMargin = box.left - at[0]; lp.topMargin = box.top - at[1];
        lp.gravity = android.view.Gravity.TOP | android.view.Gravity.LEFT;
        b.setLayoutParams(lp);
        PillFrost.frost(b, false, -1);
        int fg = ink(host.getContext());
        float k = 255f / (LO - HI), s = (LO * k + 170f) / 255f;
        float[] m = new float[20];
        m[4] = android.graphics.Color.red(fg); m[9] = android.graphics.Color.green(fg); m[14] = android.graphics.Color.blue(fg);
        m[15] = -k * 0.299f; m[16] = -k * 0.587f; m[17] = -k * 0.114f; m[18] = s; m[19] = -170f;
        android.graphics.Paint paint = new android.graphics.Paint();
        paint.setColorFilter(new android.graphics.ColorMatrixColorFilter(m));
        host.setLayerType(android.view.View.LAYER_TYPE_HARDWARE, paint);
    }

    private static void keepColors(android.view.View v, java.util.Map<android.widget.TextView, android.content.res.ColorStateList> out) {
        if (v instanceof android.widget.TextView) out.put((android.widget.TextView) v, ((android.widget.TextView) v).getTextColors());
        else if (v instanceof android.view.ViewGroup) { android.view.ViewGroup g = (android.view.ViewGroup) v; for (int i = 0; i < g.getChildCount(); i++) keepColors(g.getChildAt(i), out); }
    }

    private static boolean drawsCompose(android.view.View v) {
        if (v.getClass().getName().contains("Compose")) return true;
        if (v instanceof android.view.ViewGroup) { android.view.ViewGroup g = (android.view.ViewGroup) v; for (int i = 0; i < g.getChildCount(); i++) if (drawsCompose(g.getChildAt(i))) return true; }
        return false;
    }

    private static boolean recolor(android.view.View host, android.content.Context c) {
        boolean superDark = SuperDark.inEffect(c);
        float lo = LO, hi = HI;
        int bg = superDark ? 0xff000000 : FrostedSurface.tone(c) | 0xff000000;
        int fg = ink(c);
        float[] m = new float[20];
        int[] b = {android.graphics.Color.red(bg), android.graphics.Color.green(bg), android.graphics.Color.blue(bg)};
        int[] f = {android.graphics.Color.red(fg), android.graphics.Color.green(fg), android.graphics.Color.blue(fg)};
        for (int ch = 0; ch < 3; ch++) {
            float a = (f[ch] - b[ch]) / (hi - lo);
            m[ch * 5] = a * 0.299f; m[ch * 5 + 1] = a * 0.587f; m[ch * 5 + 2] = a * 0.114f;
            m[ch * 5 + 4] = b[ch] - a * lo;
        }
        m[18] = 1f;
        android.graphics.Paint paint = new android.graphics.Paint();
        paint.setColorFilter(new android.graphics.ColorMatrixColorFilter(m));
        host.setLayerType(android.view.View.LAYER_TYPE_HARDWARE, paint);
        return true;
    }

    private static boolean recorderSnackbar(String name) {
        return name.contains("Snackbar") && (name.contains("screencapture") || name.contains("screenrecord"));
    }

    static boolean snackbar(android.app.Dialog d) {
        if (recorderSnackbar(d.getClass().getName())) return true;
        for (Class<?> k = d.getClass(); k != null && k != android.app.Dialog.class; k = k.getSuperclass())
            for (java.lang.reflect.Field f : k.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive()) continue;
                try { f.setAccessible(true); Object v = f.get(d); if (v != null && recorderSnackbar(v.getClass().getName())) return true; } catch (Throwable ignored) { }
            }
        return false;
    }
    static final java.util.Map<Object, Boolean> pills = new java.util.WeakHashMap<Object, Boolean>();

    private static void frostDialog(android.view.View decor, android.view.Window window) {
        android.view.View content = decor.findViewById(android.R.id.content);
        android.view.View card = card(content == null ? decor : content, decor.getWidth() > 0 ? decor.getWidth() : decor.getResources().getDisplayMetrics().widthPixels);
        if (card == null && decor.getBackground() != null) card = decor;
        if (card == null) return;
        if (card.getBackground() instanceof PillFrost) return;
        float corner = FrostedSurface.dp(card.getContext(), 28);
        android.graphics.drawable.Drawable bg = card.getBackground();
        try {
            android.graphics.drawable.Drawable inner = bg;
            for (int i = 0; i < 3 && inner != null && !(inner instanceof android.graphics.drawable.GradientDrawable); i++) {
                if (inner instanceof android.graphics.drawable.InsetDrawable) inner = (android.graphics.drawable.Drawable) Xp.callMethod(inner, "getDrawable");
                else if (inner instanceof android.graphics.drawable.LayerDrawable && ((android.graphics.drawable.LayerDrawable) inner).getNumberOfLayers() > 0) inner = ((android.graphics.drawable.LayerDrawable) inner).getDrawable(0);
                else break;
            }
            if (inner instanceof android.graphics.drawable.GradientDrawable) { float r = (Float) Xp.callMethod(inner, "getCornerRadius"); if (r > 0) corner = r; }
        } catch (Throwable ignored) { }
        PillFrost d = PillFrost.frost(card, false, corner);
        if (card == decor && window != null) window.setBackgroundDrawable(d);
    }
    private static android.view.View card(android.view.View v, int windowW) {
        if (v.getBackground() != null && !(v.getBackground() instanceof android.graphics.drawable.ColorDrawable && ((android.graphics.drawable.ColorDrawable) v.getBackground()).getColor() == 0) && v.getWidth() > 0 && v.getWidth() < windowW) return v;
        if (v instanceof android.view.ViewGroup) { android.view.ViewGroup g = (android.view.ViewGroup) v; for (int i = 0; i < g.getChildCount(); i++) { android.view.View f = card(g.getChildAt(i), windowW); if (f != null) return f; } }
        return null;
    }

    private static final ThreadLocal<Boolean> armed = new ThreadLocal<Boolean>();
    private static volatile int toolbarBottom;
    private static java.lang.ref.WeakReference<Object> toolbarRegion;
    private static java.lang.reflect.Field regionField;
    private static void noteToolbar(Object content) {
        try {
            if (regionField == null) { java.lang.reflect.Field f = content.getClass().getDeclaredField("f$1"); f.setAccessible(true); regionField = f; }
            Object r = regionField.get(content);
            if (!(r instanceof android.graphics.Region)) return;
            if (toolbarRegion == null || toolbarRegion.get() != r) { toolbarRegion = new java.lang.ref.WeakReference<Object>(r); toolbarBottom = 0; }
            android.graphics.Rect b = ((android.graphics.Region) r).getBounds();
            if (!b.isEmpty() && b.bottom > toolbarBottom) toolbarBottom = b.bottom;
        } catch (Throwable ignored) { }
    }
    private static void noteRegion(android.graphics.Region r) {
        if (toolbarRegion == null || toolbarRegion.get() != r) { toolbarRegion = new java.lang.ref.WeakReference<Object>(r); toolbarBottom = 0; }
        android.graphics.Rect b = r.getBounds();
        if (!b.isEmpty() && b.bottom > toolbarBottom) toolbarBottom = b.bottom;
    }
    private static final java.util.Map<Object, Object> wrapped = new java.util.WeakHashMap<Object, Object>();
    private static final String COMPOSE = "com.android.systemui.screencapture.record.smallscreen.ui.compose.";

    private static void composeHooks(ClassLoader cl) throws Throwable {
        Class<?> lambda = null;
        try { lambda = Xp.findClass(COMPOSE + "SmallScreenCaptureRecordContent$$ExternalSyntheticLambda4", cl); } catch (RuntimeException toolbarFunction) { }
        final Class<?> content = lambda;
        final Class<?> columnKt = Xp.findClass("androidx.compose.foundation.layout.ColumnKt", cl);
        final Class<?> policyType = Xp.findClass("androidx.compose.ui.layout.MeasurePolicy", cl);
        if (content == null) Xp.hookAllMethods(Xp.findClass(COMPOSE + "SmallScreenCaptureRecordContent", cl), "Toolbar", new Xp.Callback() {
            protected void beforeHookedMethod(Xp.Param p) {
                try {
                    boolean on = enabled();
                    armed.set(on ? Boolean.TRUE : null);
                    if (!on) return;
                    for (Object arg : p.args) if (arg instanceof android.graphics.Region) { noteRegion((android.graphics.Region) arg); break; }
                } catch (Throwable ignored) { }
            }
            protected void afterHookedMethod(Xp.Param p) { armed.set(null); }
        });
        else Xp.hookAllMethods(content, "invoke", new Xp.Callback() {
            protected void beforeHookedMethod(Xp.Param p) {
                try {
                    boolean on = enabled();
                    armed.set(on ? Boolean.TRUE : null);
                    if (on) noteToolbar(p.thisObject);
                } catch (Throwable ignored) { }
            }
            protected void afterHookedMethod(Xp.Param p) { armed.set(null); }
        });
        try {
            final Class<?> offset = Xp.findClass(COMPOSE + "SmallScreenCaptureRecordContent$$ExternalSyntheticLambda" + (content == null ? "9" : "2"), cl);
            final java.lang.reflect.Field kind = offset.getDeclaredField("$r8$classId"); kind.setAccessible(true);
            offset.getDeclaredField("f$0");
            Xp.hookAllMethods(offset, "invoke", new Xp.Callback() {
                protected void afterHookedMethod(Xp.Param p) {
                    try {
                        if (p.args.length != 1 || ((Number) kind.get(p.thisObject)).intValue() != 1 || !(p.getResult() instanceof Integer)) return;
                        int bottom = toolbarBottom;
                        if (bottom <= 0 || !enabled()) return;
                        int away = -(bottom + Math.round(8 * android.content.res.Resources.getSystem().getDisplayMetrics().density));
                        if (away < (Integer) p.getResult()) p.setResult(away);
                    } catch (Throwable ignored) { }
                }
            });
        } catch (Throwable error) { }
        final boolean newLayout = content == null;
        java.lang.reflect.Method gapOf = null; Object arrangements = null;
        if (newLayout) try {
            Class<?> arrangement = Xp.findClass("androidx.compose.foundation.layout.Arrangement", cl);
            arrangements = arrangement.getField("INSTANCE").get(null);
            Class<?> vertical = Xp.findClass("androidx.compose.foundation.layout.Arrangement$Vertical", cl);
            for (java.lang.reflect.Method m : arrangement.getDeclaredMethods()) {
                Class<?>[] t = m.getParameterTypes();
                if (m.getName().startsWith("spacedBy") && t.length == 1 && t[0] == float.class && vertical.isAssignableFrom(m.getReturnType())) { gapOf = m; break; }
            }
        } catch (Throwable error) { }
        final java.lang.reflect.Method spaced = gapOf; final Object spacing = arrangements;
        Xp.hookAllMethods(columnKt, "columnMeasurePolicy", new Xp.Callback() {
            protected void beforeHookedMethod(Xp.Param p) {
                try {
                    if (spaced == null || armed.get() == null || p.args.length < 1) return;
                    Class<?>[] t = ((java.lang.reflect.Method) p.method).getParameterTypes();
                    if (!t[0].getName().endsWith("Arrangement$Vertical")) return;
                    Object gap = spaced.invoke(spacing, 5f);
                    if (t[0].isInstance(gap)) p.args[0] = gap;
                } catch (Throwable error) { }
            }
            protected void afterHookedMethod(Xp.Param p) {
                try {
                    if (armed.get() == null) return;
                    armed.set(null);
                    final Object original = p.getResult();
                    if (original == null) return;
                    Object proxy;
                    synchronized (wrapped) {
                        proxy = wrapped.get(original);
                        if (proxy == null) {
                            proxy = java.lang.reflect.Proxy.newProxyInstance(policyType.getClassLoader(), new Class<?>[] {policyType}, new java.lang.reflect.InvocationHandler() {
                                public Object invoke(Object self, java.lang.reflect.Method m, Object[] args) throws Throwable {
                                    if (args != null && args.length == 3 && m.getName().startsWith("measure") && args[1] instanceof List) {
                                        List<Object> reversed = new ArrayList<Object>((List<?>) args[1]);
                                        java.util.Collections.reverse(reversed);
                                        args = new Object[] {args[0], reversed, args[2]};
                                    }
                                    try { return m.invoke(original, args); }
                                    catch (java.lang.reflect.InvocationTargetException e) { throw e.getCause() != null ? e.getCause() : e; }
                                }
                            });
                            wrapped.put(original, proxy);
                        }
                    }
                    p.setResult(proxy);
                } catch (Throwable error) { }
            }
        });
        if (newLayout) try {
            Class<?> modifier = Xp.findClass("androidx.compose.ui.Modifier", cl);
            java.lang.reflect.Method tall = null;
            for (java.lang.reflect.Method m : Xp.findClass("androidx.compose.foundation.layout.SizeKt", cl).getDeclaredMethods()) {
                Class<?>[] t = m.getParameterTypes();
                if (m.getName().startsWith("height-") && t.length == 2 && t[0] == modifier && t[1] == float.class && java.lang.reflect.Modifier.isStatic(m.getModifiers())) { tall = m; break; }
            }
            final java.lang.reflect.Method height = tall;
            final java.lang.reflect.Method alpha = Xp.findClass("androidx.compose.ui.draw.AlphaKt", cl).getMethod("alpha", modifier, float.class);
            final java.util.Map<Object, Object> gone = new java.util.WeakHashMap<Object, Object>();
            Xp.hookAllMethods(Xp.findClass(COMPOSE + "CaptureTargetSelectorKt", cl), "CaptureTargetSelector", new Xp.Callback() {
                protected void beforeHookedMethod(Xp.Param p) {
                    try {
                        if (height == null || !enabled()) return;
                        Class<?>[] t = ((java.lang.reflect.Method) p.method).getParameterTypes();
                        for (int i = 0; i < t.length; i++) {
                            if (!"androidx.compose.ui.Modifier".equals(t[i].getName()) || p.args[i] == null) continue;
                            Object made;
                            synchronized (gone) {
                                made = gone.get(p.args[i]);
                                if (made == null) { made = alpha.invoke(null, height.invoke(null, p.args[i], 0f), 0f); gone.put(p.args[i], made); }
                            }
                            p.args[i] = made;
                            return;
                        }
                    } catch (Throwable error) { }
                }
            });
        } catch (Throwable error) { }
        final Class<?> settings = Xp.findClass(COMPOSE + "RecordDetailsSettingsKt", cl);
        Xp.hookAllMethods(settings, "SettingsRow", new Xp.Callback() {
            protected void beforeHookedMethod(Xp.Param p) {
                try {
                    if (!enabled()) return;
                    for (Object arg : p.args) {
                        if (arg != null && arg.getClass().getName().endsWith("ComposableLambdaImpl")) {
                            try { java.lang.reflect.Field b = arg.getClass().getDeclaredField("_block"); b.setAccessible(true); arg = b.get(arg); } catch (Throwable ignored) { }
                        }
                        if (arg == null) continue;
                        String name = arg.getClass().getName();
                        if (!name.endsWith("RecordDetailsSettingsKt$$ExternalSyntheticLambda5") && !(newLayout && name.contains("RecordDetailsSettingsKt$$ExternalSyntheticLambda"))) continue;
                        if (newLayout) try {
                            java.lang.reflect.Field id = arg.getClass().getDeclaredField("$r8$classId"); id.setAccessible(true);
                            if (((Number) id.get(arg)).intValue() != 0) continue;
                        } catch (NoSuchFieldException single) { }
                        java.lang.reflect.Field f = arg.getClass().getDeclaredField("f$0"); f.setAccessible(true);
                        Object owner = f.get(arg);
                        if (owner != null && owner.getClass().getName().endsWith("RecordDetailsTargetViewModel")) {
                            p.setResult(null);
                            return;
                        }
                    }
                } catch (Throwable error) { }
            }
        });
    }

    private static final Feature.Cached switchCache = new Feature.Cached(KEY);
    private static android.app.Application systemUiApp;

    private static boolean enabled() {
        try {
            if (systemUiApp == null) systemUiApp = (android.app.Application) Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null);
            return systemUiApp != null && switchCache.on(systemUiApp);
        } catch (Throwable error) { return false; }
    }
}
