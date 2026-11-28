package io.github.pixelsuite;

import android.app.Notification;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.inputmethodservice.InputMethodService;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Parcelable;
import android.os.SystemClock;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

final class OtpFill {
    private OtpFill() {}

    static final String KEY = "pixelsuite_otp_fill";
    static final String AUTO_KEY = "pixelsuite_otp_auto";
    private static final String ACTION = "io.github.roviandev.pixelsuite.OTP_CODE";
    private static final String ONLY_SYSTEM = "android.permission.INTERACT_ACROSS_USERS_FULL";
    private static final long LIFE = 180000;

    private static boolean on(Context c, String key) {
        try { return "1".equals(Settings.Secure.getString(c.getContentResolver(), key)); } catch (Throwable t) { return false; }
    }

    private static Handler sWorker;
    private static WeakReference<Notification> sLastSeen = new WeakReference<Notification>(null);
    private static final LinkedHashMap<String, Long> sSent = new LinkedHashMap<String, Long>();
    private static long sOnAt;
    private static boolean sOn;

    static void installSystem(ClassLoader cl) {
        Class<?> service = Xp.findClass("com.android.server.notification.NotificationManagerService", cl);
        if (service == null) return;
        Xp.hookAllMethods(service, "enqueueNotificationInternal", new Xp.Callback() {
            @Override protected void beforeHookedMethod(Xp.Param p) {
                try {
                    Notification n = null;
                    for (Object a : p.args) if (a instanceof Notification) { n = (Notification) a; break; }
                    if (n == null || sLastSeen.get() == n) return;
                    sLastSeen = new WeakReference<Notification>(n);
                    if ((n.flags & (Notification.FLAG_GROUP_SUMMARY | Notification.FLAG_ONGOING_EVENT | Notification.FLAG_FOREGROUND_SERVICE)) != 0) {
                        return;
                    }
                    final Context c = (Context) Xp.callMethod(p.thisObject, "getContext");
                    if (c == null) return;
                    long now = SystemClock.uptimeMillis();
                    if (sOnAt == 0 || now - sOnAt > 2000) { sOn = on(c, KEY); sOnAt = now; }
                    if (!sOn) return;
                    final String pkg = p.args.length > 0 && p.args[0] instanceof String ? (String) p.args[0] : null;
                    final String text = textOf(n);
                    if (pkg == null || text == null) return;
                    worker().post(new Runnable() { public void run() {
                        try { deliver(c, pkg, text); } catch (Throwable ignored) { }
                    } });
                } catch (Throwable ignored) { }
            }
        });
    }

    private static synchronized Handler worker() {
        if (sWorker == null) { HandlerThread t = new HandlerThread("pixelsuite-codes"); t.start(); sWorker = new Handler(t.getLooper()); }
        return sWorker;
    }

    private static String textOf(Notification n) {
        Bundle x = n.extras;
        if (x == null) return null;
        StringBuilder out = new StringBuilder();
        add(out, x.getCharSequence(Notification.EXTRA_TITLE));
        CharSequence newest = null;
        try {
            Parcelable[] messages = x.getParcelableArray("android.messages");
            if (messages != null && messages.length > 0 && messages[messages.length - 1] instanceof Bundle) {
                Bundle last = (Bundle) messages[messages.length - 1];
                long time = last.getLong("time", 0);
                if (time != 0 && System.currentTimeMillis() - time > LIFE) return null;
                newest = last.getCharSequence("text");
            }
        } catch (Throwable ignored) { }
        if (newest != null) add(out, newest);
        else {
            CharSequence big = x.getCharSequence(Notification.EXTRA_BIG_TEXT), text = x.getCharSequence(Notification.EXTRA_TEXT);
            add(out, text);
            if (big != null && (text == null || !big.toString().contentEquals(text))) add(out, big);
            try {
                CharSequence[] lines = x.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
                if (lines != null) for (int i = Math.max(0, lines.length - 3); i < lines.length; i++) add(out, lines[i]);
            } catch (Throwable ignored) { }
            add(out, x.getCharSequence(Notification.EXTRA_SUB_TEXT));
            add(out, x.getCharSequence(Notification.EXTRA_SUMMARY_TEXT));
        }
        if (out.length() < 4) return null;

        int caps = 0;
        for (int i = 0; i <= out.length(); i++) {
            char ch = i < out.length() ? out.charAt(i) : ' ';
            if (Character.isDigit(ch)) return out.toString();
            if (ch >= 'A' && ch <= 'Z') caps++;
            else { if (caps >= 6 && caps <= 8 && !Character.isLetter(ch)) return out.toString(); caps = Character.isLetter(ch) ? 99 : 0; }
        }
        return null;
    }

    private static void add(StringBuilder out, CharSequence s) {
        if (s == null || s.length() == 0 || out.length() > 3000) return;
        if (out.length() > 0) out.append('\n');
        out.append(s, 0, Math.min(s.length(), 1500));
    }

    private static void deliver(Context c, String pkg, String text) {
        String code = OtpFinder.find(text);
        if (code == null) return;
        String keyboard = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
        if (keyboard == null) { return; }
        int slash = keyboard.indexOf('/');
        if (slash > 0) keyboard = keyboard.substring(0, slash);
        if (keyboard.equals(pkg)) return;
        long now = SystemClock.uptimeMillis();
        synchronized (sSent) {
            for (Iterator<Map.Entry<String, Long>> it = sSent.entrySet().iterator(); it.hasNext(); ) if (now - it.next().getValue() > 30000) it.remove();
            if (sSent.containsKey(pkg + ':' + code)) { return; }
            sSent.put(pkg + ':' + code, now);
        }
        String from = pkg;
        try { from = String.valueOf(c.getPackageManager().getApplicationLabel(c.getPackageManager().getApplicationInfo(pkg, 0))); } catch (Throwable ignored) { }
        Intent i = new Intent(ACTION).setPackage(keyboard).addFlags(Intent.FLAG_RECEIVER_FOREGROUND | 0x40000000 );
        i.putExtra("code", code).putExtra("from", from);
        UserHandle user = null;
        try { user = (UserHandle) UserHandle.class.getField("CURRENT").get(null); } catch (Throwable ignored) { }
        if (user != null) c.sendBroadcastAsUser(i, user); else c.sendBroadcast(i);
    }

    private static boolean sKeyboard;
    private static WeakReference<InputMethodService> sIme = new WeakReference<InputMethodService>(null);
    private static BroadcastReceiver sReceiver;
    private static WeakReference<InputMethodService> sHeld = new WeakReference<InputMethodService>(null);
    private static String sCode, sFrom;
    private static long sAt;
    private static boolean sAutoTried, sTyping;
    private static WeakReference<View> sChip = new WeakReference<View>(null);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Runnable EXPIRE = new Runnable() { public void run() { InputMethodService ime = sIme.get(); if (ime != null) refresh(ime); } };

    static synchronized void installKeyboard() {
        if (sKeyboard) return;
        sKeyboard = true;
        Class<?> ims = InputMethodService.class;
        Xp.hookAllMethods(ims, "onCreate", new Xp.Callback() {
            @Override protected void afterHookedMethod(Xp.Param p) {
                try { if (p.thisObject instanceof InputMethodService) listen((InputMethodService) p.thisObject); } catch (Throwable ignored) { }
            }
        });
        Xp.hookAllMethods(ims, "onDestroy", new Xp.Callback() {
            @Override protected void beforeHookedMethod(Xp.Param p) {
                try {
                    if (sHeld.get() == p.thisObject && sReceiver != null) {
                        try { ((Context) p.thisObject).unregisterReceiver(sReceiver); } catch (Throwable ignored) { }
                        sReceiver = null; sHeld = new WeakReference<InputMethodService>(null);
                    }
                    if (sIme.get() == p.thisObject) sIme = new WeakReference<InputMethodService>(null);
                } catch (Throwable ignored) { }
            }
        });
        Xp.Callback shown = new Xp.Callback() {
            @Override protected void afterHookedMethod(final Xp.Param p) {
                if (sCode == null && sChip.get() == null) return;
                MAIN.post(new Runnable() { public void run() {
                    try { if (p.thisObject instanceof InputMethodService) refresh((InputMethodService) p.thisObject); } catch (Throwable ignored) { }
                } });
            }
        };
        Xp.hookAllMethods(ims, "showWindow", shown);
        Xp.hookAllMethods(ims, "doStartInput", new Xp.Callback() {
            @Override protected void afterHookedMethod(final Xp.Param p) {
                if (sCode == null && sChip.get() == null) return;
                MAIN.post(new Runnable() { public void run() {
                    try { sAutoTried = false; if (p.thisObject instanceof InputMethodService) refresh((InputMethodService) p.thisObject); } catch (Throwable ignored) { }
                } });
            }
        });
    }

    private static void listen(final InputMethodService ime) {
        sIme = new WeakReference<InputMethodService>(ime);
        reach(ime);
        InputMethodService held = sHeld.get();
        if (sReceiver != null && held == ime) return;
        if (sReceiver != null) {
            try { if (held != null) held.unregisterReceiver(sReceiver); } catch (Throwable ignored) { }
            sReceiver = null; sHeld = new WeakReference<InputMethodService>(null);
        }
        sReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) {
                try {
                    String code = i.getStringExtra("code");
                    if (code == null || code.length() < 4 || code.length() > 12 || !on(c, KEY)) return;
                    sCode = code; sFrom = i.getStringExtra("from"); sAt = SystemClock.uptimeMillis(); sAutoTried = false;
                    MAIN.removeCallbacks(EXPIRE); MAIN.postDelayed(EXPIRE, LIFE + 200);
                    InputMethodService now = sIme.get();
                    if (now != null) { refresh(now); watch(now); }
                } catch (Throwable ignored) { }
            }
        };
        IntentFilter f = new IntentFilter(ACTION);
        try {
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                Context.class.getMethod("registerReceiver", BroadcastReceiver.class, IntentFilter.class, String.class, Handler.class, int.class).invoke(ime, sReceiver, f, ONLY_SYSTEM, MAIN, 4);
            } else ime.registerReceiver(sReceiver, f, ONLY_SYSTEM, MAIN);
            sHeld = new WeakReference<InputMethodService>(ime);
        } catch (Throwable failed) { sReceiver = null; sHeld = new WeakReference<InputMethodService>(null); }
    }

    private static WeakReference<View> sWatched = new WeakReference<View>(null);

    private static void watch(final InputMethodService ime) {
        try {
            final View decor = ime.getWindow().getWindow().getDecorView();
            if (sWatched.get() == decor) return;
            sWatched = new WeakReference<View>(decor);
            decor.getViewTreeObserver().addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener() {
                long last;
                public boolean onPreDraw() {
                    try {
                        if (sCode == null || SystemClock.uptimeMillis() - sAt >= LIFE) {
                            decor.getViewTreeObserver().removeOnPreDrawListener(this);
                            if (sWatched.get() == decor) sWatched = new WeakReference<View>(null);
                            if (sChip.get() != null) MAIN.post(EXPIRE);
                            return true;
                        }
                        long now = SystemClock.uptimeMillis();
                        View chip = sChip.get();
                        if (now - last > 250 && !sTyping && ime.isInputViewShown()) {
                            last = now;
                            if (chip == null || !chip.isAttachedToWindow()) MAIN.post(EXPIRE);
                            else if (entered(ime, sCode)) { sCode = null; MAIN.post(EXPIRE); }
                            else nudge();
                        }
                    } catch (Throwable ignored) { }
                    return true;
                }
            });
        } catch (Throwable ignored) { }
    }

    private static boolean sNudged;

    static void nudge() {
        if (sCode == null && sChip.get() == null) return;
        if (sNudged) return;
        sNudged = true;
        MAIN.post(new Runnable() { public void run() {
            sNudged = false;
            try { InputMethodService ime = sIme.get(); if (ime != null) refresh(ime); } catch (Throwable ignored) { }
        } });
    }

    private static void refresh(final InputMethodService ime) {
        boolean live = sCode != null && SystemClock.uptimeMillis() - sAt < LIFE && on(ime, KEY);
        if (!live) { sCode = null; drop(); return; }
        if (sTyping) return;
        ViewGroup frame = null;
        try { Object f = Xp.getObjectField(ime, "mInputFrame"); if (f instanceof ViewGroup) frame = (ViewGroup) f; } catch (Throwable ignored) { }
        if (frame == null || !ime.isInputViewShown()) return;
        if (!sAutoTried) {
            sAutoTried = true;
            boolean auto = on(ime, AUTO_KEY), waiting = auto && waitingForCode(ime, sCode);
            if (waiting) { type(ime, sCode); return; }
        }
        try {
            View strip = GboardPlus.slot(new Rect());
            if (strip == null && GboardPlus.strips()) { drop(); return; }
            if (strip != null && strip.getRootView() != frame.getRootView() && strip.getRootView() instanceof FrameLayout) frame = (ViewGroup) strip.getRootView();
        } catch (Throwable ignored) { }
        View chip = sChip.get();
        if (chip == null || chip.getParent() != frame) {
            drop(); chip = chip(ime); sChip = new WeakReference<View>(chip);
            chip.setVisibility(View.INVISIBLE);
            frame.addView(chip, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT));
            chip.post(new Runnable() { public void run() { nudge(); } });
        }
        TextView code = (TextView) chip.findViewWithTag("code");
        if (!sCode.contentEquals(code.getText())) code.setText(sCode);
        TextView from = (TextView) chip.findViewWithTag("from");
        String who = sFrom == null ? "" : sFrom;
        if (!who.contentEquals(from.getText())) from.setText(who);
        place(ime, frame, chip);
    }

    private static final int HIDE = 0, SLOT = 1, LIFT = 2, APART = 3, PLAIN = 4;
    private static boolean sClash;
    private static int sKept;

    private static void keep(int px) {
        if (px == sKept || px != 0 && Math.abs(px - sKept) <= 2) return;
        sKept = px;
        try { GboardPlus.reserve(px); } catch (Throwable ignored) { }
    }

    private static View inline(View v) {
        if (v.getVisibility() != View.VISIBLE) return null;
        if ("android.widget.inline.InlineContentView".equals(v.getClass().getName())) return v.getWidth() > 0 && v.isShown() ? v : null;
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) { View hit = inline(g.getChildAt(i)); if (hit != null) return hit; }
        }
        return null;
    }

    private static void place(InputMethodService ime, ViewGroup frame, View chip) {
        try {
            int W = frame.getWidth(), H = frame.getHeight();
            if (W <= 0 || H <= 0) return;
            int[] home = new int[2], at = new int[2];
            frame.getLocationOnScreen(home);
            frame.getLocationInWindow(at);
            Rect row = new Rect();
            View strip = null;
            boolean known = false;
            try { strip = GboardPlus.slot(row); known = GboardPlus.strips(); } catch (Throwable ignored) { }
            int mode = strip == null ? (known ? HIDE : PLAIN) : strip.getRootView() != frame.getRootView() ? APART : sClash ? LIFT : SLOT;
            TextView from = (TextView) chip.findViewWithTag("from");
            int label = (mode == APART || mode == PLAIN) && from.getText().length() > 0 ? View.VISIBLE : View.GONE;
            if (from.getVisibility() != label) from.setVisibility(label);
            float lift = dp(ime, mode == SLOT ? 1.5f : 6);
            if (chip.getElevation() != lift) chip.setElevation(lift);
            int cw, tall;
            if (chip.getWidth() > 0 && !chip.isLayoutRequested()) { cw = chip.getWidth(); tall = chip.getHeight(); }
            else { chip.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)); cw = chip.getMeasuredWidth(); tall = chip.getMeasuredHeight(); }
            if (cw <= 0 || tall <= 0) return;
            int pad = dp(ime, 6), x = 0, y = 0;
            float k = 1f;
            boolean show = true;
            if (mode == SLOT) {
                float s = 1f;
                try { s = GboardPlus.stripScale(strip); } catch (Throwable ignored) { }
                if (!(s > 0.3f && s < 3f)) s = 1f;
                k = Math.max(0.6f, Math.min(1f, (row.height() - dp(ime, 6)) / (float) tall));
                int need = Math.round((pad + cw * k + dp(ime, 8)) / s);
                keep(need);
                x = row.left + pad - home[0];
                y = row.top + Math.round((row.height() - tall * k) / 2f) - home[1];
                try {
                    View busy = strip.isLayoutRequested() || GboardPlus.held(strip) < need - 3 ? null : inline(frame.getRootView());
                    Rect seen = new Rect();
                    if (busy != null && busy.getGlobalVisibleRect(seen)) {
                        Rect mine = new Rect(at[0] + x, at[1] + y, at[0] + x + Math.round(cw * k), at[1] + y + Math.round(tall * k));
                        if (Rect.intersects(mine, seen)) { sClash = true; mode = LIFT; }
                    }
                } catch (Throwable ignored) { }
            }
            if (mode == LIFT) {
                keep(0);
                k = 1f;
                x = row.left + pad - home[0];
                y = row.top - tall - dp(ime, 6) - home[1];
                if (y < 0) y = row.top + Math.round((row.height() - tall) / 2f) - home[1];
            } else if (mode == APART) {
                keep(0);
                View root = strip.getRootView();
                int[] p = new int[2];
                root.getLocationOnScreen(p);
                x = p[0] + root.getWidth() / 2 - cw / 2 - home[0];
                int above = p[1] - tall - dp(ime, 8) - home[1], below = p[1] + root.getHeight() + dp(ime, 8) - home[1];
                if (above >= dp(ime, 24)) y = above; else if (below + tall <= H - dp(ime, 8)) y = below; else show = false;
            } else if (mode == HIDE) {
                show = false;
            } else if (mode == PLAIN) {
                int top = dp(ime, 4);
                float shift = 0;
                if (sSeenTop >= 0) {
                    Rect area = sSeenRegion ? sSeenArea : null;
                    int edge = sSeenTop;
                    boolean apart = area != null && edge >= area.bottom - dp(ime, 8);
                    if (area != null && !(edge > area.top && edge < area.bottom)) edge = area.top;
                    int room = H - dp(ime, 44);
                    if (apart) {
                        int above = area.top - at[1] - tall - dp(ime, 8), below = area.bottom - at[1] + dp(ime, 8);
                        if (above > 0) top = above; else if (below < room) top = below;
                    } else if (edge - at[1] > 0 && edge - at[1] < room) top = edge - at[1] + dp(ime, 4);
                    if (area != null && area.width() > dp(ime, 120)) shift = area.exactCenterX() - at[0] - W / 2f;
                }
                try {
                    View busy = inline(frame.getRootView());
                    if (busy != null) {
                        int[] b = new int[2];
                        busy.getLocationInWindow(b);
                        int above = b[1] - at[1] - tall - dp(ime, 6);
                        if (above >= 0) top = above;
                    }
                } catch (Throwable ignored) { }
                x = Math.round((W - cw) / 2f + shift);
                y = top;
            }
            x = Math.max(0, Math.min(x, W - Math.round(cw * k)));
            y = Math.max(0, Math.min(y, H - Math.round(tall * k)));
            if (chip.getPivotX() != 0f) chip.setPivotX(0f);
            if (chip.getPivotY() != 0f) chip.setPivotY(0f);
            if (chip.getScaleX() != k) { chip.setScaleX(k); chip.setScaleY(k); }
            if (chip.getTranslationX() != 0f) chip.setTranslationX(0f);
            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) chip.getLayoutParams();
            if (lp.leftMargin != x || lp.topMargin != y) { lp.leftMargin = x; lp.topMargin = y; chip.setLayoutParams(lp); }
            int vis = show ? View.VISIBLE : View.INVISIBLE;
            if (chip.getVisibility() != vis) chip.setVisibility(vis);
            if (show && frame.getChildAt(frame.getChildCount() - 1) != chip) chip.bringToFront();
        } catch (Throwable ignored) { }
    }

    private static int sSeenTop = -1;
    private static boolean sSeenRegion;
    private static final Rect sSeenArea = new Rect();
    private static Class<?> sInsetsOwner;

    private static void reach(InputMethodService ime) {
        try {
            Class<?> c = ime.getClass();
            while (c != null && c != Object.class) {
                try { c.getDeclaredMethod("onComputeInsets", InputMethodService.Insets.class); break; } catch (Throwable none) { c = c.getSuperclass(); }
            }
            if (c == null || c == Object.class || c == sInsetsOwner) return;
            sInsetsOwner = c;
            Xp.hookAllMethods(c, "onComputeInsets", new Xp.Callback() {
                @Override protected void afterHookedMethod(Xp.Param p) {
                    try {
                        if (p.args == null || p.args.length != 1 || !(p.args[0] instanceof InputMethodService.Insets)) return;
                        InputMethodService.Insets in = (InputMethodService.Insets) p.args[0];
                        boolean region = in.touchableInsets == InputMethodService.Insets.TOUCHABLE_INSETS_REGION && !in.touchableRegion.isEmpty();
                        Rect now = region ? in.touchableRegion.getBounds() : null;
                        boolean moved = in.visibleTopInsets != sSeenTop || region != sSeenRegion || region && !now.equals(sSeenArea);
                        sSeenTop = in.visibleTopInsets;
                        sSeenRegion = region;
                        if (region) sSeenArea.set(now); else sSeenArea.setEmpty();
                        View chip = sChip.get();
                        InputMethodService owner = sIme.get();
                        if (in.touchableInsets == InputMethodService.Insets.TOUCHABLE_INSETS_REGION && chip != null && chip.isAttachedToWindow() && chip.getWidth() > 0 && chip.getVisibility() == View.VISIBLE
                                && owner != null && chip.getRootView() == owner.getWindow().getWindow().getDecorView()) {
                            int[] at = new int[2];
                            chip.getLocationInWindow(at);
                            in.touchableRegion.union(new Rect(at[0], at[1], at[0] + Math.round(chip.getWidth() * chip.getScaleX()), at[1] + Math.round(chip.getHeight() * chip.getScaleY())));
                        }
                        if (moved) nudge();
                    } catch (Throwable ignored) { }
                }
            });
        } catch (Throwable ignored) { }
    }

    private static boolean entered(InputMethodService ime, String code) {
        try {
            InputConnection ic = ime.getCurrentInputConnection();
            if (ic == null || code == null) return false;
            CharSequence before = ic.getTextBeforeCursor(code.length() + 1, 0);
            if (before == null || before.length() < code.length()) return false;
            String text = before.toString();
            if (!text.regionMatches(true, text.length() - code.length(), code, 0, code.length())) return false;
            return text.length() == code.length() || !Character.isLetterOrDigit(text.charAt(0));
        } catch (Throwable t) { return false; }
    }

    private static void drop() {
        View chip = sChip.get();
        sChip = new WeakReference<View>(null);
        sClash = false;
        keep(0);
        try { if (chip != null && chip.getParent() instanceof ViewGroup) ((ViewGroup) chip.getParent()).removeView(chip); } catch (Throwable ignored) { }
    }

    private static final class Digits extends android.graphics.drawable.Drawable {
        private final android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final int color;
        Digits(int color) { this.color = color; }
        @Override public void draw(android.graphics.Canvas c) {
            Rect b = getBounds();
            float s = Math.min(b.width(), b.height()), u = s / 20f, x = b.left, y = b.top;
            paint.setColor(color);
            paint.setStyle(android.graphics.Paint.Style.STROKE);
            paint.setStrokeWidth(1.6f * u);
            c.drawRoundRect(x + 2 * u, y + 3.5f * u, x + 18 * u, y + 17.5f * u, 3 * u, 3 * u, paint);
            paint.setStyle(android.graphics.Paint.Style.FILL);
            paint.setTextAlign(android.graphics.Paint.Align.CENTER);
            paint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            paint.setTextSize(7.2f * u);
            c.drawText("123", x + 10 * u, y + 13.2f * u, paint);
        }
        @Override public void setAlpha(int a) { }
        @Override public void setColorFilter(android.graphics.ColorFilter f) { }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }

    private static int dp(Context c, float v) { return Math.round(v * c.getResources().getDisplayMetrics().density); }

    private static View chip(final InputMethodService ime) {
        boolean dark = (ime.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        LinearLayout pill = new LinearLayout(ime);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        pill.setPadding(dp(ime, 12), 0, dp(ime, 16), 0);
        pill.setMinimumHeight(dp(ime, 36));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(ime, 18));
        bg.setColor(dark ? 0xFF2E3136 : 0xFFFFFFFF);
        pill.setBackground(bg);
        pill.setElevation(dp(ime, 6));
        View mark = new View(ime);
        mark.setBackground(new Digits(dark ? 0xFFE3E3E3 : 0xFF444746));
        LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(dp(ime, 20), dp(ime, 20));
        ml.rightMargin = dp(ime, 8);
        pill.addView(mark, ml);
        TextView code = new TextView(ime);
        code.setTag("code");
        code.setTextSize(17);
        code.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        code.setLetterSpacing(0.12f);
        code.setTextColor(dark ? 0xFFFFFFFF : 0xFF1F1F1F);
        code.setSingleLine(true);
        pill.addView(code);
        TextView from = new TextView(ime);
        from.setTag("from");
        from.setTextSize(13);
        from.setTextColor(dark ? 0xFFBDC1C6 : 0xFF5F6368);
        from.setSingleLine(true);
        from.setEllipsize(android.text.TextUtils.TruncateAt.END);
        from.setMaxWidth(dp(ime, 150));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = dp(ime, 10);
        pill.addView(from, lp);
        pill.setClickable(true);
        pill.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            String c = sCode;
            if (c != null) type(ime, c);
        } });
        pill.setOnLongClickListener(new View.OnLongClickListener() { public boolean onLongClick(View v) { sCode = null; drop(); return true; } });
        return pill;
    }

    private static boolean waitingForCode(InputMethodService ime, String code) {
        try {
            EditorInfo info = ime.getCurrentInputEditorInfo();
            InputConnection ic = ime.getCurrentInputConnection();
            if (info == null || ic == null) return false;
            int type = info.inputType, klass = type & InputType.TYPE_MASK_CLASS;
            if (klass == InputType.TYPE_NULL || klass == InputType.TYPE_CLASS_PHONE || klass == InputType.TYPE_CLASS_DATETIME) return false;
            boolean digitsOnly = true;
            for (int i = 0; i < code.length(); i++) if (code.charAt(i) < '0' || code.charAt(i) > '9') digitsOnly = false;
            boolean numbers = klass == InputType.TYPE_CLASS_NUMBER && (type & (InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED)) == 0;
            boolean named = OtpFinder.mentionsCode(info.hintText) || OtpFinder.mentionsCode(info.label);

            if (klass == InputType.TYPE_CLASS_TEXT && (type & InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS) != 0 && (info.imeOptions & EditorInfo.IME_MASK_ACTION) != EditorInfo.IME_ACTION_SEARCH) named = true;
            if (klass == InputType.TYPE_CLASS_TEXT && (type & (InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_IME_MULTI_LINE)) != 0) return false;
            if (!(numbers && digitsOnly) && !named) return false;
            ExtractedText all = ic.getExtractedText(new ExtractedTextRequest(), 0);
            if (all != null && all.text != null) return all.text.length() == 0;
            CharSequence before = ic.getTextBeforeCursor(1, 0), after = ic.getTextAfterCursor(1, 0);
            return (before == null || before.length() == 0) && (after == null || after.length() == 0);
        } catch (Throwable t) { return false; }
    }

    private static void type(final InputMethodService ime, final String code) {
        sTyping = true;
        sCode = null;
        drop();
        try { InputConnection ic = ime.getCurrentInputConnection(); if (ic != null) ic.finishComposingText(); } catch (Throwable ignored) { }
        for (int i = 0; i < code.length(); i++) {
            final String ch = String.valueOf(code.charAt(i));
            MAIN.postDelayed(new Runnable() { public void run() {
                try { InputConnection ic = ime.getCurrentInputConnection(); if (ic != null) ic.commitText(ch, 1); } catch (Throwable ignored) { }
            } }, i * 45L);
        }
        MAIN.postDelayed(new Runnable() { public void run() { sTyping = false; } }, code.length() * 45L + 60);
    }
}
