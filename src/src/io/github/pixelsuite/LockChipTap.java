package io.github.pixelsuite;

import android.app.KeyguardManager;
import android.content.Context;
import android.os.SystemClock;
import android.view.View;

import java.lang.reflect.Field;

final class LockChipTap {
    private LockChipTap() { }

    private static final long GAP = 400L;
    private static Field sInfo, sClick;

    static void installSystemUi(ClassLoader cl) {
        Class<?> util = Xp.findClass("com.google.android.systemui.smartspace.BcSmartSpaceUtil", cl);
        if (util == null) return;
        Xp.Callback wrap = new Xp.Callback() {
            @Override protected void afterHookedMethod(Xp.Param p) {
                try {
                    if (p.args.length == 0 || !(p.args[0] instanceof View)) return;
                    View v = (View) p.args[0];
                    View.OnClickListener now = listener(v);
                    if (now == null || now instanceof Later) return;
                    v.setOnClickListener(new Later(now));
                } catch (Throwable ignored) { }
            }
        };
        Xp.hookAllMethods(util, "setOnClickListener", wrap);
        Xp.hookAllMethods(util, "setOnClickListener$1", wrap);
    }

    private static View.OnClickListener listener(View v) throws Exception {
        if (sInfo == null) { sInfo = View.class.getDeclaredField("mListenerInfo"); sInfo.setAccessible(true); }
        Object info = sInfo.get(v);
        if (info == null) return null;
        if (sClick == null) { sClick = info.getClass().getDeclaredField("mOnClickListener"); sClick.setAccessible(true); }
        return (View.OnClickListener) sClick.get(info);
    }

    private static boolean waiting(Context c) {
        try {
            if (!Feature.on(c, Feature.TAP_CHECK) || Feature.on(c, Feature.LOCK_SHORTCUTS)) return false;
            KeyguardManager k = (KeyguardManager) c.getSystemService(Context.KEYGUARD_SERVICE);
            return k != null && k.isKeyguardLocked();
        } catch (Throwable t) {
            return false;
        }
    }

    private static final class Later implements View.OnClickListener {
        final View.OnClickListener real;
        Runnable pending;
        long at;

        Later(View.OnClickListener real) { this.real = real; }

        @Override public void onClick(final View v) {
            if (!waiting(v.getContext())) { real.onClick(v); return; }
            long now = SystemClock.uptimeMillis();
            if (pending != null && now - at <= GAP) {
                v.removeCallbacks(pending);
                pending = null;
                return;
            }
            at = now;
            final Runnable run = new Runnable() {
                @Override public void run() {
                    if (pending != this) return;
                    pending = null;
                    try { real.onClick(v); } catch (Throwable ignored) { }
                }
            };
            pending = run;
            v.postDelayed(run, GAP);
        }
    }
}
