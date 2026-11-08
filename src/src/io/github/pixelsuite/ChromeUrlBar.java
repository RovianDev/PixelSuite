package io.github.pixelsuite;

import android.graphics.Canvas;
import android.graphics.Picture;
import android.os.Build;
import android.os.SystemClock;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.widget.TextView;

import java.util.WeakHashMap;

final class ChromeUrlBar {
    private ChromeUrlBar() {}

    private static boolean sInstalled;
    private static final WeakHashMap<View, St> sState = new WeakHashMap<View, St>();
    private static long sLoggedAt;

    private static final WeakHashMap<View, Boolean> sWatched = new WeakHashMap<View, Boolean>();
    private static long sSecond; private static int sLines;
    private static String sLastFrame;

    private static String name(View v) {
        try { return v.getId() == View.NO_ID ? v.getClass().getSimpleName() : v.getResources().getResourceEntryName(v.getId()); } catch (Throwable t) { return v.getClass().getSimpleName(); }
    }

    private static void watch(final View v) {
        if (sWatched.containsKey(v)) return;
        sWatched.put(v, Boolean.TRUE);
        final java.lang.ref.WeakReference<View> ref = new java.lang.ref.WeakReference<View>(v);
        v.getViewTreeObserver().addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener() {
            public boolean onPreDraw() {
                View u = ref.get();
                if (u == null || !ChromeLayout.on() || !u.isAttachedToWindow()) return true;
                try { sample((TextView) u); } catch (Throwable ignored) { }
                return true;
            }
        });
    }

    private static void sample(TextView t) {
        StringBuilder b = new StringBuilder();
        int[] at = new int[2]; t.getLocationOnScreen(at);
        float alpha = 1f; View lb = null;
        for (View p = t; p != null; p = p.getParent() instanceof View ? (View) p.getParent() : null) {
            alpha *= p.getAlpha();
            if (lb == null && "location_bar".equals(name(p))) lb = p;
        }
        CharSequence cs = t.getText();
        b.append("text='").append(cs).append("' color=").append(Integer.toHexString(t.getCurrentTextColor())).append(" scroll=").append(t.getScrollX())
         .append(" at=").append(at[0]).append(',').append(at[1]).append(" w=").append(t.getWidth()).append(" alpha=").append(Math.round(alpha * 100)).append("% shown=").append(t.isShown());
        if (lb instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) lb;
            for (int i = 0; i < g.getChildCount(); i++) {
                View c = g.getChildAt(i);
                if (c == t || c.getVisibility() == View.GONE) continue;
                b.append(" | ").append(name(c)).append(" x=").append(Math.round(c.getX())).append(" w=").append(c.getWidth()).append(" a=").append(Math.round(c.getAlpha() * 100));
                if (c.getVisibility() != View.VISIBLE) b.append(" invisible");
            }
            if (lb.getParent() instanceof android.view.ViewGroup) {
                android.view.ViewGroup row = (android.view.ViewGroup) lb.getParent();
                b.append(" | toolbar:");
                for (int i = 0; i < row.getChildCount(); i++) {
                    View c = row.getChildAt(i);
                    if (c == lb || c.getVisibility() == View.GONE || c.getWidth() == 0) continue;
                    b.append(' ').append(name(c)).append(" x=").append(c.getLeft()).append(" w=").append(c.getWidth());
                    if (c.getVisibility() != View.VISIBLE) b.append(" invisible");
                    if (c instanceof android.view.ViewGroup && "toolbar_buttons".equals(name(c))) {
                        android.view.ViewGroup tb = (android.view.ViewGroup) c;
                        b.append(" [");
                        for (int j = 0; j < tb.getChildCount(); j++) {
                            View d = tb.getChildAt(j);
                            if (d.getVisibility() == View.GONE) continue;
                            b.append(name(d)).append(" x=").append(d.getLeft()).append(" w=").append(d.getWidth()).append(d.getVisibility() != View.VISIBLE ? " invisible" : "").append("; ");
                        }
                        b.append(']');
                    }
                }
            }
        }
        View cc = t.getRootView().findViewById(t.getResources().getIdentifier("control_container", "id", t.getContext().getPackageName()));
        if (cc != null) b.append(" | control_container vis=").append(cc.getVisibility()).append(" a=").append(Math.round(cc.getAlpha() * 100)).append(" y=").append(Math.round(cc.getTranslationY()));
        String line = b.toString();
        if (line.equals(sLastFrame)) return;
        sLastFrame = line;
        long now = SystemClock.uptimeMillis();
        if (now - sSecond > 1000) { sSecond = now; sLines = 0; }
        if (++sLines > 40) return;
    }

    private static final class St {
        Picture picture;
        String lastSig, shownSig, shownText;
        long since;
        int same;
        float shownFade;
        boolean recording, holding;
        int held, copies;
        String heldFrom;
        Canvas real;
    }

    static synchronized void install(ClassLoader l) {
        if (sInstalled || Build.VERSION.SDK_INT < 28) return;
        sInstalled = true;
        Class<?> bar = null;
        for (String n : new String[]{"org.chromium.chrome.browser.omnibox.UrlBarApi26", "org.chromium.chrome.browser.omnibox.UrlBar"}) {
            try { bar = l.loadClass(n); break; } catch (Throwable ignored) { }
        }
        if (bar == null) { return; }
        Xp.hookAllMethods(bar, "onDraw", new Xp.Callback() {
            @Override protected void beforeHookedMethod(Xp.Param p) throws Throwable { before(p); }
            @Override protected void afterHookedMethod(Xp.Param p) throws Throwable { after(p); }
        });
        Xp.hookAllMethods(bar, "getLeftFadingEdgeStrength", new Xp.Callback() {
            @Override protected void beforeHookedMethod(Xp.Param p) {
                St s = sState.get((View) p.thisObject);
                if (s != null && s.holding) p.setResult(s.shownFade);
            }
        });
    }

    private static String spans(CharSequence t) {
        if (!(t instanceof Spanned)) return "";
        Spanned s = (Spanned) t;
        ForegroundColorSpan[] f = s.getSpans(0, s.length(), ForegroundColorSpan.class);
        StringBuilder b = new StringBuilder();
        for (ForegroundColorSpan x : f) b.append(s.getSpanStart(x)).append('-').append(s.getSpanEnd(x)).append(':').append(Integer.toHexString(x.getForegroundColor())).append(',');
        return b.toString();
    }

    private static String sig(TextView t) {
        CharSequence cs = t.getText();
        boolean bad = t.isLayoutRequested() || t.getLayout() == null;
        return (cs == null ? "" : cs.toString()) + "|" + t.getScrollX() + "|" + t.getWidth() + "x" + t.getHeight() + "|" + Integer.toHexString(t.getCurrentTextColor())
            + "|" + Integer.toHexString(t.getCurrentHintTextColor()) + "|" + bad + "|" + t.getGravity() + "|" + t.getTextAlignment() + "|" + t.getTextDirection() + "|" + spans(cs);
    }

    private static void before(Xp.Param p) throws Throwable {
        View v = (View) p.thisObject;
        Canvas c = (Canvas) p.args[0];
        St s = sState.get(v);
        if (s == null) { s = new St(); sState.put(v, s); }
        if (s.recording) { try { s.picture.endRecording(); } catch (Throwable ignored) { } s.recording = false; s.real = null; }
        if (!ChromeLayout.on() || c == null || v.getWidth() <= 0 || v.getHeight() <= 0) { s.holding = false; return; }
        if (v.isFocused()) { s.holding = false; s.picture = null; s.shownText = null; s.shownSig = null; return; }
        TextView t = (TextView) v;
        if (c.isHardwareAccelerated()) watch(v);
        String sig = sig(t);

        if (!c.isHardwareAccelerated()) {
            if (s.picture != null && !sig.equals(s.shownSig)) {
                show(c, v, s.picture);
                p.setResult(null);
                s.copies++;
            }
            return;
        }

        CharSequence cs = t.getText();
        String text = cs == null ? "" : cs.toString();
        boolean bad = v.isLayoutRequested() || t.getLayout() == null;
        long now = SystemClock.uptimeMillis();
        if (!sig.equals(s.lastSig)) { s.lastSig = sig; s.since = now; s.same = 0; } else s.same++;
        boolean clearing = text.isEmpty() && s.shownText != null && !s.shownText.isEmpty() && now - s.since < 250;
        long age = now - s.since;
        boolean settled = s.picture == null ? (!bad || age > 300) : ((!bad && !clearing && s.same >= 2) || age > 400);
        if (settled) {
            Picture pic = new Picture();
            Canvas rc = pic.beginRecording(v.getWidth(), v.getHeight());
            rc.translate(-v.getScrollX(), -v.getScrollY());
            s.picture = pic;
            s.real = c; s.recording = true;
            p.args[0] = rc;
            if ((s.held > 0 || s.copies > 0) && now - sLoggedAt > 1000) {
                sLoggedAt = now;
            }
            s.held = 0; s.copies = 0;
            s.shownText = text; s.shownSig = sig; s.shownFade = v.getScrollX() > 0 ? 1f : 0f;
            s.holding = false;
        } else if (s.picture != null) {
            if (s.held == 0) s.heldFrom = s.shownText;
            s.held++;
            s.holding = true;
            show(c, v, s.picture);
            p.setResult(null);
            v.postInvalidateOnAnimation();
        } else {
            v.postInvalidateOnAnimation();
        }
    }

    private static void after(Xp.Param p) throws Throwable {
        View v = (View) p.thisObject;
        St s = sState.get(v);
        if (s == null || !s.recording) return;
        s.recording = false;
        s.picture.endRecording();
        Canvas real = s.real; s.real = null;
        if (real != null) show(real, v, s.picture);
    }

    private static void show(Canvas c, View v, Picture pic) {
        int save = c.save();
        c.translate(v.getScrollX(), v.getScrollY());
        c.drawPicture(pic);
        c.restoreToCount(save);
    }
}
