package io.github.pixelsuite;

import android.content.Context;
import android.content.res.ColorStateList;
import android.inputmethodservice.InputMethodService;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.WeakHashMap;

final class GboardToasts {
    private GboardToasts() {}

    private static final String CTOR = "(Ljava/lang/CharSequence;Ljava/lang/CharSequence;Landroid/view/View$OnClickListener;Landroid/view/View$OnClickListener;)V";
    private static final Map<View, String> sStyled = new WeakHashMap<View, String>();

    static void prepare(InputMethodService ims, ClassLoader cl) throws Throwable {
        String name = binder(ims);
        if (name == null) return;
        Class<?> b = cl.loadClass(name);
        for (Method m : b.getDeclaredMethods()) {
            if (Modifier.isStatic(m.getModifiers()) || m.getParameterTypes().length != 1 || m.getParameterTypes()[0] != View.class) continue;
            Xp.hookMethod(m, new Xp.Callback() {
                @Override protected void afterHookedMethod(Xp.Param p) {
                    try { if (p.args[0] instanceof View && GboardPlus.premium()) style((View) p.args[0]); } catch (Throwable ignored) { }
                }
            });
        }
    }

    private static String binder(Context c) throws Throwable {
        long ver = c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionCode;
        File f = new File(c.getCacheDir(), "pixelsuite_gboard_toastbinder_" + ver);
        if (f.isFile()) {
            byte[] b = new byte[(int) Math.min(256, f.length())];
            FileInputStream in = new FileInputStream(f);
            int n;
            try { n = in.read(b); } finally { in.close(); }
            String s = n <= 0 ? "" : new String(b, 0, n, "UTF-8").trim();
            return s.isEmpty() ? null : s;
        }
        String name = DexPeek.constructedWith(c, CTOR);
        FileOutputStream out = new FileOutputStream(f);
        try { out.write((name == null ? "" : name).getBytes("UTF-8")); } finally { out.close(); }
        return name;
    }

    static void style(final View root) {
        if (!root.isAttachedToWindow()) {
            root.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                public void onViewDetachedFromWindow(View v) { }
                public void onViewAttachedToWindow(final View v) {
                    v.removeOnAttachStateChangeListener(this);
                    try { apply(v); } catch (Throwable ignored) { }
                    final android.view.ViewTreeObserver.OnPreDrawListener[] once = new android.view.ViewTreeObserver.OnPreDrawListener[1];
                    once[0] = new android.view.ViewTreeObserver.OnPreDrawListener() {
                        public boolean onPreDraw() {
                            try { v.getViewTreeObserver().removeOnPreDrawListener(once[0]); } catch (Throwable ignored) { }
                            try { if (v.isAttachedToWindow()) apply(v); } catch (Throwable ignored) { }
                            return true;
                        }
                    };
                    try { v.getViewTreeObserver().addOnPreDrawListener(once[0]); } catch (Throwable ignored) { }
                }
            });
            return;
        }
        apply(root);
    }

    private static void apply(View root) {
        Context c = root.getContext();
        if (!SurfaceStyle.restyled(c)) return;
        View pill = SystemToasts.pill(root);
        if (pill == null || pill instanceof TextView) return;
        boolean superDark = SuperDark.inEffect(c), dark = superDark || FrostedSurface.dark(c);
        String state = (superDark ? "s" : dark ? "d" : "l") + SurfaceStyle.get(c);
        if (pill.getBackground() instanceof PillFrost && state.equals(sStyled.get(pill))) return;
        View decor = GboardPlus.imeDecor();
        boolean sameWindow = decor != null && root.getRootView() == decor.getRootView();
        PillFrost d = PillFrost.frost(pill, sameWindow, FrostedSurface.dp(c, 28));
        d.text(pill);
        actions(pill, dark);
        sStyled.put(pill, state);
    }

    private static void actions(View v, boolean dark) {
        if (v instanceof Button) {
            Button b = (Button) v;
            b.setTextColor(dark ? 0xFFA8C7FA : 0xFF0B57D0);
            try {
                Method tint = b.getClass().getMethod("setIconTint", ColorStateList.class);
                tint.invoke(b, ColorStateList.valueOf(dark ? 0xFFFFFFFF : 0xFF1F1F1F));
            } catch (Throwable ignored) { }
            return;
        }
        if (v instanceof ViewGroup) { ViewGroup g = (ViewGroup) v; for (int i = 0; i < g.getChildCount(); i++) actions(g.getChildAt(i), dark); }
    }
}
