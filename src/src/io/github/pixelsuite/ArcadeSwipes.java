package io.github.pixelsuite;

import android.util.Log;
import android.view.WindowManager;

import java.lang.reflect.Field;

final class ArcadeSwipes {
    private ArcadeSwipes() { }

    static final String PLAYING = "PixelSuite arcade: playing";
    private static final String POLICY = "com.android.server.wm.DisplayPolicy";

    static void installSystem(ClassLoader cl) {
        Class<?> callbacks = Xp.findClass("com.android.server.wm.SystemGesturesPointerEventListener$Callbacks", cl);
        Xp.Callback skip = new Xp.Callback() {
            @Override protected void beforeHookedMethod(Xp.Param p) {
                if (playing(p.thisObject)) p.setResult(null);
            }
        };
        int hooks = 0;
        for (int i = 1; i <= 80; i++) {
            Class<?> k;
            try { k = Class.forName(POLICY + "$" + i, false, cl); } catch (Throwable t) { continue; }
            if (!callbacks.isAssignableFrom(k)) continue;
            for (String m : new String[]{"onSwipeFromBottom", "onSwipeFromLeft", "onSwipeFromRight"}) hooks += Xp.hookAllMethods(k, m, skip).size();
        }
        if (hooks == 0) hooks = fallback(cl);
    }

    private static int fallback(ClassLoader cl) {
        final Class<?> listener = Xp.findClass("com.android.server.wm.SystemGesturesPointerEventListener", cl);
        final int none, bottom, left, right;
        try {
            none = staticInt(listener, "SWIPE_NONE"); bottom = staticInt(listener, "SWIPE_FROM_BOTTOM");
            left = staticInt(listener, "SWIPE_FROM_LEFT"); right = staticInt(listener, "SWIPE_FROM_RIGHT");
        } catch (Throwable t) { Log.w("PixelSuite", "arcade swipes: no swipe constants (" + t + ")"); return 0; }
        return Xp.hookAllMethods(listener, "detectSwipe", new Xp.Callback() {
            @Override protected void afterHookedMethod(Xp.Param p) {
                Object r = p.getResult();
                if (!(r instanceof Integer)) return;
                int swipe = (Integer) r;
                if ((swipe == bottom || swipe == left || swipe == right) && playing(get(p.thisObject, "mCallbacks"))) p.setResult(none);
            }
        }).size();
    }

    private static int staticInt(Class<?> k, String name) throws Exception {
        Field f = k.getDeclaredField(name); f.setAccessible(true); return f.getInt(null);
    }

    static boolean playing(Object callbacks) {
        try {
            if (callbacks == null) return false;
            Object policy = null;
            for (Class<?> k = callbacks.getClass(); k != null && policy == null; k = k.getSuperclass())
                for (Field f : k.getDeclaredFields())
                    if (POLICY.equals(f.getType().getName())) { f.setAccessible(true); policy = f.get(callbacks); break; }
            if (policy == null) return false;
            Object win = get(get(policy, "mDisplayContent"), "mCurrentFocus");
            if (win == null) win = get(policy, "mFocusedWindow");
            Object attrs = get(win, "mAttrs");
            if (!(attrs instanceof WindowManager.LayoutParams)) return false;
            WindowManager.LayoutParams lp = (WindowManager.LayoutParams) attrs;
            CharSequence title = lp.getTitle();
            return title != null && PLAYING.contentEquals(title) && lp.packageName != null && lp.packageName.contains("pixelsuite");
        } catch (Throwable t) { return false; }
    }

    private static Object get(Object o, String name) {
        if (o == null) return null;
        for (Class<?> k = o.getClass(); k != null; k = k.getSuperclass()) {
            try { Field f = k.getDeclaredField(name); f.setAccessible(true); return f.get(o); }
            catch (NoSuchFieldException ignored) {  }
            catch (Throwable t) { return null; }
        }
        return null;
    }
}
