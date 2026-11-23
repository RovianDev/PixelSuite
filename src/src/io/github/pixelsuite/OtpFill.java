package io.github.pixelsuite;

import android.app.Notification;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
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

        for (int i = 0; i < out.length(); i++) if (Character.isDigit(out.charAt(i))) return out.toString();
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
        Xp.hookAllMethods(ims, "doStartInput", shown);
    }

    private static void listen(final InputMethodService ime) {
        sIme = new WeakReference<InputMethodService>(ime);
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
                        if (now - last > 250 && !sTyping && (chip == null || !chip.isAttachedToWindow()) && ime.isInputViewShown()) {
                            last = now;
                            MAIN.post(EXPIRE);
                        }
                    } catch (Throwable ignored) { }
                    return true;
                }
            });
        } catch (Throwable ignored) { }
    }

    private static void refresh(InputMethodService ime) {
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
        View chip = sChip.get();
        if (chip == null || chip.getParent() != frame) { drop(); chip = chip(ime); sChip = new WeakReference<View>(chip);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            lp.topMargin = dp(ime, 4);
            frame.addView(chip, lp);
        }
        chip.bringToFront();
        ((TextView) chip.findViewWithTag("code")).setText(sCode);
        TextView from = (TextView) chip.findViewWithTag("from");
        from.setText(sFrom == null ? "" : sFrom);
        from.setVisibility(sFrom == null || sFrom.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private static void drop() {
        View chip = sChip.get();
        sChip = new WeakReference<View>(null);
        try { if (chip != null && chip.getParent() instanceof ViewGroup) ((ViewGroup) chip.getParent()).removeView(chip); } catch (Throwable ignored) { }
    }

    private static int dp(Context c, float v) { return Math.round(v * c.getResources().getDisplayMetrics().density); }

    private static View chip(final InputMethodService ime) {
        boolean dark = (ime.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        LinearLayout pill = new LinearLayout(ime);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        pill.setPadding(dp(ime, 16), 0, dp(ime, 16), 0);
        pill.setMinimumHeight(dp(ime, 36));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(ime, 18));
        bg.setColor(dark ? 0xFF2E3136 : 0xFFFFFFFF);
        bg.setStroke(Math.max(1, dp(ime, 1)), dark ? 0xFF8AB4F8 : 0xFF1A73E8);
        pill.setBackground(bg);
        pill.setElevation(dp(ime, 6));
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
