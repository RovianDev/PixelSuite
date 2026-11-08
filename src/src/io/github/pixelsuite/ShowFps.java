package io.github.pixelsuite;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import java.util.concurrent.Executor;

final class ShowFps {
    private ShowFps() {}

    static final String KEY = "pixelsuite_show_fps";
    private static volatile Context sContext;
    private static Handler sH;
    private static WindowManager sWm;
    private static TextView sView;
    private static Object sCallback;
    private static int sTask = -1;
    private static boolean sRunning;
    private static volatile float sFps = -1f;
    private static volatile long sAt;
    private static String sShown;

    static void installSystem(ClassLoader cl) {
        try {
            Xp.hookAllMethods(Xp.findClass("com.android.server.am.ActivityManagerService", cl), "systemReady", new Xp.Callback() {
                @Override protected void afterHookedMethod(Xp.Param p) {
                    try { start((Context) Xp.getObjectField(p.thisObject, "mContext")); } catch (Throwable ignored) { }
                }
            });
        } catch (Throwable ignored) { }
    }

    private static synchronized void start(final Context c) {
        if (sContext != null || c == null) return;
        sContext = c;
        HandlerThread t = new HandlerThread("PixelSuiteFps"); t.start();
        final Handler h = new Handler(t.getLooper());
        sH = h;
        h.postDelayed(ShowFps::apply, 8000);
        try {
            c.getContentResolver().registerContentObserver(Settings.Secure.getUriFor(KEY), false, new ContentObserver(h) {
                @Override public void onChange(boolean self) { apply(); }
            });
            IntentFilter f = new IntentFilter(Intent.ACTION_SCREEN_ON);
            f.addAction(Intent.ACTION_SCREEN_OFF);
            f.addAction(Intent.ACTION_USER_PRESENT);
            c.registerReceiver(new android.content.BroadcastReceiver() {
                @Override public void onReceive(Context x, Intent i) { h.post(ShowFps::apply); }
            }, f, null, h);
        } catch (Throwable ignored) { }
    }

    private static boolean wanted() {
        try { return Settings.Secure.getInt(sContext.getContentResolver(), KEY, 0) == 1; } catch (Throwable t) { return false; }
    }

    private static void apply() {
        if (wanted()) show(); else hide();
    }

    private static int dp(float v) { return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, sContext.getResources().getDisplayMetrics())); }

    private static void show() {
        if (sRunning) return;
        try {
            Context c = sContext;
            sWm = (WindowManager) c.getSystemService(Context.WINDOW_SERVICE);
            TextView v = new TextView(c);
            v.setTextColor(Color.WHITE);
            v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            v.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            v.setPadding(dp(8), dp(3), dp(8), dp(3));
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(0x99000000);
            bg.setCornerRadius(dp(10));
            v.setBackground(bg);
            v.setText(I18n.cs("-- FPS"));
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                    2015 ,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.TOP | Gravity.END;
            int sb = 0;
            try { int id = c.getResources().getIdentifier("status_bar_height", "dimen", "android"); if (id != 0) sb = c.getResources().getDimensionPixelSize(id); } catch (Throwable ignored) { }
            lp.x = dp(10);
            lp.y = sb + dp(4);
            lp.setTitle(I18n.cs("PixelSuiteFps"));
            try { WindowManager.LayoutParams.class.getField("layoutInDisplayCutoutMode").setInt(lp, 3 ); } catch (Throwable ignored) { }
            try { Xp.setObjectField(lp, "privateFlags", (Integer) Xp.getObjectField(lp, "privateFlags") | 0x00000010 ); } catch (Throwable ignored) { }
            if (locked()) v.setVisibility(View.GONE);
            sWm.addView(v, lp);
            sView = v;
            sRunning = true;
            tick();
        } catch (Throwable ignored) { sRunning = false; }
    }

    private static void hide() {
        if (!sRunning) return;
        sRunning = false;
        unregister();
        sTask = -1;
        try { if (sView != null) sWm.removeViewImmediate(sView); } catch (Throwable ignored) { }
        sView = null;
        sShown = null;
    }

    private static void tick() {
        if (!sRunning) return;
        try {
            boolean locked = locked();
            if (sView != null && (sView.getVisibility() == View.VISIBLE) == locked) sView.setVisibility(locked ? View.GONE : View.VISIBLE);
            if (!locked) {
                int task = focusedTask();
                if (task != sTask) { unregister(); sTask = task; sFps = -1f; if (task >= 0) register(task); }
                refresh();
            }
        } catch (Throwable ignored) { }
        sH.postDelayed(ShowFps::tick, 120);
    }

    private static void refresh() {
        float f = sFps;
        if (f >= 0 && android.os.SystemClock.uptimeMillis() - sAt > 700) f = 0f;
        String text = f < 0 ? "-- FPS" : Math.round(f) + " FPS";
        if (sView != null && !text.equals(sShown)) { sView.setText(I18n.cs(text)); sShown = text; }
    }

    private static boolean locked() {
        try {
            android.app.KeyguardManager km = (android.app.KeyguardManager) sContext.getSystemService(Context.KEYGUARD_SERVICE);
            if (km != null && km.isKeyguardLocked()) return true;
            android.os.PowerManager pm = (android.os.PowerManager) sContext.getSystemService(Context.POWER_SERVICE);
            return pm != null && !pm.isInteractive();
        } catch (Throwable t) { return false; }
    }

    private static int focusedTask() {
        try {
            Object atm = Class.forName("android.app.ActivityTaskManager").getMethod("getService").invoke(null);
            Object info = Xp.callMethod(atm, "getFocusedRootTaskInfo");
            if (info == null) return -1;
            try {
                int[] children = (int[]) Xp.getObjectField(info, "childTaskIds");
                if (children != null && children.length > 0) return children[children.length - 1];
            } catch (Throwable ignored) { }
            return (Integer) Xp.getObjectField(info, "taskId");
        } catch (Throwable t) { return -1; }
    }

    private static void register(int task) {
        try {
            android.window.TaskFpsCallback cb = new android.window.TaskFpsCallback() {
                @Override public void onFpsReported(float fps) { sFps = fps; sAt = android.os.SystemClock.uptimeMillis(); if (sH != null) sH.post(ShowFps::refresh); }
            };
            Executor ex = r -> { if (sH != null) sH.post(r); };
            WindowManager.class.getMethod("registerTaskFpsCallback", int.class, Executor.class, Class.forName("android.window.TaskFpsCallback")).invoke(sWm, task, ex, cb);
            sCallback = cb;
        } catch (Throwable ignored) { sCallback = null; }
    }

    private static void unregister() {
        if (sCallback == null) return;
        try { WindowManager.class.getMethod("unregisterTaskFpsCallback", Class.forName("android.window.TaskFpsCallback")).invoke(sWm, sCallback); } catch (Throwable ignored) { }
        sCallback = null;
    }
}
