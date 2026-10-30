package io.github.pixelsuite;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextPaint;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

final class TextTool {
    private TextTool() {}

    static boolean on;

    private static Class<?> cIvl, cIwh, cIvm, cIxk, cIxl, cIvy, cLpp;
    private static Constructor<?> newIvl, newIwh, newIvm, newIxk, newIxl, newIvy, newLpp;
    private static Field ivlItem, ivlStyle, ivlText, ivlPad, ivlBox, ivlDirty, ivlPaint, ivlSpacing, ivlLines;
    private static Field iwhXf, iwhShown, iwhOwner, ixhRot, ixhScale, ixhPos, ixlA, ixlB, ivyA, ivyB;
    private static Field ivmColour, ivmFont, ijvInt, ikaFace, ikaName;
    private static Field iwbScale, iwbDensity, iwbExport, iwiItem, iwiAlpha;
    private static Field jhuAdd, jhuDelete, jhuEditing, jhuK, loaUndo, jofOpen, jofHit, iwpSelected, iwpViewSize;
    private static Method ivlSetText, ivlSetStyle, ivlLayout, ixhMatrix, ixhScaleAbout, ixhSetScale, ixhSetPos, ixhMove, ixhSetRot, ixhK, ixhL;
    private static Method jhuDefaults, jhuScene, ijwColour, ijwFont, ijwColours, ijwFonts, tbaCall, ixsMake, iwpViewToArea, iwpSelect, iwpScale;
    private static Method areaOf, areaRot, undoPush, ixkApply, jhyTap, hitItem;

    private static WeakReference<Object> jhu = new WeakReference<Object>(null), jhy = new WeakReference<Object>(null);

    static void install(ClassLoader cl) {
        try {
            cIvl = Class.forName("ivl", false, cl); cIwh = Class.forName("iwh", false, cl); cIvm = Class.forName("ivm", false, cl);
            cIxk = Class.forName("ixk", false, cl); cIxl = Class.forName("ixl", false, cl); cIvy = Class.forName("ivy", false, cl);
            cLpp = Class.forName("lpp", false, cl);
            Class<?> ixh = Class.forName("ixh", false, cl), ijv = Class.forName("ijv", false, cl), ika = Class.forName("ika", false, cl);
            Class<?> iwb = Class.forName("iwb", false, cl), iwi = Class.forName("iwi", false, cl), ivp = Class.forName("ivp", false, cl);
            Class<?> jhuC = Class.forName("jhu", false, cl), jhyC = Class.forName("jhy", false, cl), jofC = Class.forName("jof", false, cl);
            Class<?> iwp = Class.forName("iwp", false, cl), ijw = Class.forName("ijw", false, cl), tba = Class.forName("tba", false, cl);
            Class<?> ixs = Class.forName("ixs", false, cl), iwz = Class.forName("iwz", false, cl), ixb = Class.forName("ixb", false, cl);
            Class<?> loa = Class.forName("loa", false, cl);
            if (!TextPaint.class.equals(f(cIvl, "g").getType()) || !String.class.equals(f(cIvl, "c").getType())) throw new NoSuchFieldException("not 192717's caption");

            newIvl = cIvl.getConstructor(cIwh); newIwh = cIwh.getDeclaredConstructor(byte[].class); newIwh.setAccessible(true);
            newIvm = cIvm.getConstructor(ijv, ika); newIxk = cIxk.getConstructor(cIvl, String.class, cIvm);
            newIxl = cIxl.getConstructor(float.class, float.class); newIvy = cIvy.getConstructor(float.class, float.class);
            newLpp = cLpp.getConstructor(String.class, RectF.class);

            ivlItem = f(cIvl, "a"); ivlStyle = f(cIvl, "b"); ivlText = f(cIvl, "c"); ivlPad = f(cIvl, "d"); ivlBox = f(cIvl, "e");
            ivlDirty = f(cIvl, "f"); ivlPaint = f(cIvl, "g"); ivlSpacing = f(cIvl, "h"); ivlLines = f(cIvl, "i");
            iwhXf = f(cIwh, "a"); iwhShown = f(cIwh, "b"); iwhOwner = f(cIwh, "e");
            ixhRot = f(ixh, "b"); ixhScale = f(ixh, "c"); ixhPos = f(ixh, "d");
            ixlA = f(cIxl, "a"); ixlB = f(cIxl, "b"); ivyA = f(cIvy, "a"); ivyB = f(cIvy, "b");
            ivmColour = f(cIvm, "a"); ivmFont = f(cIvm, "b"); ijvInt = f(ijv, "c"); ikaFace = f(ika, "c"); ikaName = f(ika, "e");
            iwbScale = f(iwb, "b"); iwbDensity = f(iwb, "d"); iwbExport = f(iwb, "g"); iwiItem = f(iwi, "a"); iwiAlpha = f(iwi, "b");
            jhuAdd = f(jhuC, "b"); jhuDelete = f(jhuC, "c"); jhuEditing = f(jhuC, "e"); jhuK = f(jhuC, "k"); loaUndo = f(loa, "j");
            jofOpen = f(jofC, "e"); jofHit = f(jofC, "i"); iwpSelected = f(iwp, "o"); iwpViewSize = f(iwp, "c");

            ivlSetText = m(cIvl, "i", String.class); ivlSetStyle = m(cIvl, "g", cIvm); ivlLayout = m(cIvl, "j");
            ixhMatrix = m(ixh, "b"); ixhScaleAbout = m(ixh, "f", float.class, cIvy); ixhSetScale = m(ixh, "h", float.class);
            ixhSetPos = m(ixh, "i", cIxl); ixhMove = m(ixh, "j", cIxl); ixhSetRot = m(ixh, "g", float.class); ixhK = m(ixh, "k"); ixhL = m(ixh, "l");
            jhuDefaults = m(jhuC, "a"); jhuScene = m(jhuC, "b");
            ijwColour = m(ijw, "a"); ijwFont = m(ijw, "b"); ijwColours = m(ijw, "c"); ijwFonts = m(ijw, "d");
            tbaCall = m(tba, "a", Object.class); ixsMake = m(ixs, "g", float.class, float.class, int.class);
            iwpViewToArea = m(iwp, "p", cIvy); iwpSelect = m(iwp, "F", iwz); iwpScale = m(iwp, "b");
            areaOf = m(iwp, "k"); areaRot = m(ivp, "b");
            ixkApply = m(cIxk, "b"); jhyTap = m(jhyC, "u");
            for (Method x : loaUndo.getType().getMethods()) if (x.getName().equals("d") && x.getParameterTypes().length == 1 && x.getParameterTypes()[0].isAssignableFrom(cIxk)) undoPush = x;
            if (undoPush == null) for (Method x : Class.forName("klt", false, cl).getMethods()) if (x.getName().equals("d") && x.getParameterTypes().length == 1 && x.getParameterTypes()[0].isAssignableFrom(cIxk)) undoPush = x;
            Class<?> ivo = Class.forName("ivo", false, cl); hitItem = m(ivo, "b");
            fqaU = m(Class.forName("fqa", false, cl), "U", ixh, ivp);

            Xp.hookAllConstructors(jhuC, new Xp.Callback() {
                @Override protected void afterHookedMethod(Xp.Param p) { jhu = new WeakReference<Object>(p.thisObject); }
            });
            Xp.hookMethod(m(jhuC, "e", cIvl), new Xp.Callback() {
                @Override protected void beforeHookedMethod(Xp.Param p) {
                    jhu = new WeakReference<Object>(p.thisObject);
                    try { open(p.args[0]); p.setResult(null); } catch (Throwable t) { }
                }
            });
            Xp.hookMethod(m(jhyC, "s", cIvl), new Xp.Callback() {
                @Override protected void beforeHookedMethod(Xp.Param p) { jhy = new WeakReference<Object>(p.thisObject); opening = true; }
                @Override protected void afterHookedMethod(Xp.Param p) {
                    opening = false;
                    if (editing() != null) try { jhyTap.invoke(p.thisObject); } catch (Throwable ignored) { }
                    refreshMenu();
                }
            });
            Xp.hookMethod(m(jhyC, "z"), new Xp.Callback() {
                @Override protected void beforeHookedMethod(Xp.Param p) {
                    jhy = new WeakReference<Object>(p.thisObject);
                    if (cIvl.isInstance(selected())) p.setResult(null);
                }
            });
            Xp.hookMethod(m(jofC, "J", ixb), new Xp.Callback() {
                @Override protected void beforeHookedMethod(Xp.Param p) {
                    try {
                        Object hit = jofHit.get(p.thisObject), target = null;
                        boolean cross = hit != null && iwi.isInstance(hit);
                        if (hit != null && !cross) target = iwhOwner.get(hitItem.invoke(hit));
                        Object ed = editing();
                        if (ed != null) {
                            if (cross && iwiItem.get(hit) == ivlItem.get(ed)) { stopEditing(false); return; }
                            if (target == ed) showKeyboard();
                            p.setResult(null);
                            return;
                        }
                        if (target != null && cIvl.isInstance(target) && target != selected()) {
                            HELD.set(jofOpen.get(p.thisObject));
                            jofOpen.set(p.thisObject, null);
                        }
                    } catch (Throwable t) { }
                }
                @Override protected void afterHookedMethod(Xp.Param p) {
                    Object o = HELD.get(); HELD.set(null);
                    if (o != null) try { jofOpen.set(p.thisObject, o); } catch (Throwable ignored) { }
                    refreshMenu();
                }
            });
            Xp.hookMethod(iwpSelect, new Xp.Callback() {
                @Override protected void beforeHookedMethod(Xp.Param p) {
                    Object ed = editing();
                    if (ed != null && fingerDown && p.args[0] != ed) { p.setResult(null); }
                }
                @Override protected void afterHookedMethod(Xp.Param p) {
                    Object ed = editing();
                    if (ed != null && fingerDown && p.args[0] != ed) return;
                    if (ed != null && p.args[0] != ed) stopEditing(true);
                    refreshMenu();
                }
            });
            Xp.hookMethod(ivlLayout, new Xp.Callback() {
                @Override protected void beforeHookedMethod(Xp.Param p) {
                    try { layout(p.thisObject); p.setResult(null); } catch (Throwable t) { }
                }
            });
            Xp.hookMethod(m(cIvl, "f", Canvas.class, ivp, iwb), new Xp.Callback() {
                @Override protected void beforeHookedMethod(Xp.Param p) {
                    float[] keep = frozen.get(p.thisObject);
                    if (keep == null) return;
                    try {
                        Object x = xf(p.thisObject), pos = ixhPos.get(x);
                        if (ixlA.getFloat(pos) == keep[0] && ixlB.getFloat(pos) == keep[1] && ixhScale.getFloat(x) == keep[2] && ixhRot.getFloat(x) == keep[3]) return;
                        ixhSetPos.invoke(x, newIxl.newInstance(keep[0], keep[1]));
                        ixhSetScale.invoke(x, keep[2]);
                        ixhSetRot.invoke(x, keep[3]);
                        ixhK.invoke(x); ixhL.invoke(x);
                    } catch (Throwable ignored) { }
                }
            });
            Xp.hookMethod(m(iwi, "f", Canvas.class, ivp, iwb), new Xp.Callback() {
                @Override protected void beforeHookedMethod(Xp.Param p) {
                    try {
                        Object item = iwhOwner.get(iwiItem.get(p.thisObject));
                        if (!cIvl.isInstance(item)) return;
                        if (!iwbExport.getBoolean(p.args[2])) frame((Canvas) p.args[0], p.args[1], p.args[2], item, p.thisObject);
                        p.setResult(null);
                    } catch (Throwable t) { }
                }
            });
            on = true;
        } catch (Throwable t) { on = false; }
    }

    private static final ThreadLocal<Object> HELD = new ThreadLocal<Object>();
    private static final Map<Object, float[]> frozen = new WeakHashMap<Object, float[]>();
    private static void freezeSelected() {
        try {
            Object sel = selected();
            if (!cIvl.isInstance(sel)) return;
            Object x = xf(sel), pos = ixhPos.get(x);
            frozen.put(sel, new float[]{ixlA.getFloat(pos), ixlB.getFloat(pos), ixhScale.getFloat(x), ixhRot.getFloat(x)});
        } catch (Throwable ignored) { }
    }
    private static Method fqaU;

    private static Field f(Class<?> k, String n) throws Exception { Field x = k.getDeclaredField(n); x.setAccessible(true); return x; }
    private static Method m(Class<?> k, String n, Class<?>... t) throws Exception { Method x = k.getDeclaredMethod(n, t); x.setAccessible(true); return x; }

    private static WeakReference<Object> editingRef = new WeakReference<Object>(null);
    private static boolean opening;
    private static String origText; private static Object origStyle;
    private static final Map<Object, Float> widths = new WeakHashMap<Object, Float>();
    private static final Map<Object, List<Line>> laid = new WeakHashMap<Object, List<Line>>();

    static boolean ownsFrame(Object iwi) {
        if (!on || iwi == null) return false;
        try { return cIvl.isInstance(iwhOwner.get(iwiItem.get(iwi))); } catch (Throwable t) { return false; }
    }

    static Object editing() { return on ? editingRef.get() : null; }

    private static Object scene() {
        try { Object h = jhu.get(); if (h != null) return jhuScene.invoke(h); } catch (Throwable ignored) { }
        try { View v = ScreenshotEditor.canvasView.get(); if (v != null) { DoodleOverlay.Bridge.resolve(v); return DoodleOverlay.Bridge.viewScene.get(v); } } catch (Throwable ignored) { }
        return null;
    }
    private static Object selected() { try { Object s = scene(); return s == null ? null : iwpSelected.get(s); } catch (Throwable t) { return null; } }
    private static Object xf(Object ivl) throws Exception { return iwhXf.get(ivlItem.get(ivl)); }
    private static void redraw() { View v = ScreenshotEditor.canvasView.get(); if (v != null) v.invalidate(); }

    static void doodlesOpening() {
        if (!on) return;
        try {
            if (editing() != null) stopEditing(true);
            hideMenu();
        } catch (Throwable t) { }
    }
    private static void closeDoodles() {
        try { if (DoodleOverlay.active()) { DoodleOverlay.leave(true); } } catch (Throwable ignored) { }
    }

    static void pageSet(String page) {
        if (!on || opening || page == null) return;
        if ("CAPTION".equals(page)) closeDoodles();
        if (editing() != null && !"CAPTION".equals(page)) stopEditing(true);
        main.post(new Runnable() { @Override public void run() { refreshMenu(); } });
    }

    private static WeakReference<Object> returnTo = new WeakReference<Object>(null);
    private static long returnAt;
    static void cropDragFromText() {
        Object s = selected();
        returnTo = new WeakReference<Object>(cIvl != null && cIvl.isInstance(s) ? s : null);
        returnAt = android.os.SystemClock.uptimeMillis();
    }

    static boolean textSelected() {
        if (!on) return false;
        try { return editing() != null || (cIvl.isInstance(selected()) && "CAPTION".equals(TopBarKeep.currentPage())); } catch (Throwable t) { return false; }
    }

    static boolean textAt(View c, MotionEvent e) {
        try { return on && ownerOf(StudioItems.itemAt(c, e)) != null; } catch (Throwable t) { return false; }
    }

    static boolean nearSelectedFrame(float rawX, float rawY) {
        if (onSelectedText(rawX, rawY)) return true;
        try {
            Object ivl = selected();
            View cv = ScreenshotEditor.canvasView.get();
            if (!cIvl.isInstance(ivl) || cv == null) return false;
            Matrix m = localToScreen(ivl, cv);
            if (m == null) return false;
            RectF box = (RectF) ivlBox.get(ivl);
            float[] p = {box.centerX(), box.top};
            m.mapPoints(p);
            float reach = 60 * cv.getResources().getDisplayMetrics().density;
            return Math.hypot(rawX - p[0], rawY - p[1]) < reach;
        } catch (Throwable t) { return false; }
    }

    static boolean onSelectedText(float rawX, float rawY) {
        if (!on) return false;
        try {
            Object ivl = editing();
            if (ivl == null) { Object s = selected(); if (cIvl.isInstance(s)) ivl = s; }
            View cv = ScreenshotEditor.canvasView.get();
            if (ivl == null || cv == null) return false;
            Matrix m = localToScreen(ivl, cv);
            if (m == null) return false;
            RectF box = new RectF((RectF) ivlBox.get(ivl));
            m.mapRect(box);
            float reach = 28 * cv.getResources().getDisplayMetrics().density;
            box.inset(-reach, -reach);
            return box.contains(rawX, rawY);
        } catch (Throwable t) { return false; }
    }

    private static void open(Object ivl) throws Exception {
        Object h = jhu.get();
        if (h == null) throw new IllegalStateException("no caption state");
        Object back = returnTo.get();
        returnTo = new WeakReference<Object>(null);
        if (ivl == null && back != null && android.os.SystemClock.uptimeMillis() - returnAt < 3000) {
            try { iwpSelect.invoke(scene(), back); } catch (Throwable ignored) { }
            refreshMenu();
            return;
        }
        if (editing() != null) stopEditing(true);
        closeDoodles();
        View cv0 = ScreenshotEditor.canvasView.get();
        if (cv0 != null && StudioZoom.zoomed(cv0)) StudioZoom.reset(cv0, false);
        if (CropKeep.suspend()) { }
        userTop = Float.NaN;
        if (ivl == null) ivl = create(h);
        else { try { iwpSelect.invoke(scene(), ivl); } catch (Throwable ignored) { } }
        jhuEditing.set(h, null);
        origText = (String) ivlText.get(ivl); origStyle = ivlStyle.get(ivl);
        editingRef = new WeakReference<Object>(ivl);
        iwhShown.setBoolean(ivlItem.get(ivl), true);
        startTyping(ivl);
    }

    private static Object create(Object h) throws Exception {
        Activity a = ScreenshotEditor.activity();
        Object scene = jhuScene.invoke(h);
        Object defaults = jhuDefaults.invoke(h);
        Object font = ijwFont.invoke(defaults);
        try { List<?> fonts = (List<?>) ijwFonts.invoke(defaults); if (fonts != null && fonts.size() > 1) font = fonts.get(1); } catch (Throwable ignored) { }
        Object style = newIvm.newInstance(ijwColour.invoke(defaults), font);
        Object ivl = newIvl.newInstance(newIwh.newInstance((Object) null));
        TextPaint paint = (TextPaint) ivlPaint.get(ivl);
        android.util.DisplayMetrics dm = a.getResources().getDisplayMetrics();
        paint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 56, dm));
        ivlPad.setFloat(ivl, TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 24, dm));
        ivlDirty.setBoolean(ivl, true);
        ivlSetStyle.invoke(ivl, style);
        ivlLayout.invoke(ivl);
        float adjust = 1f / (Float) iwpScale.invoke(scene);
        float rot = (Float) areaRot.invoke(areaOf.invoke(scene));
        Object xf = ixsMake.invoke(null, rot, adjust, 4);
        RectF box = (RectF) ivlBox.get(ivl);
        Object half = mapPoint(xf, box.width() * 0.5f, 0f);
        View cv = ScreenshotEditor.canvasView.get();
        float[] centre = topOfPicture(a, cv);
        Object at = iwpViewToArea.invoke(scene, newIvy.newInstance(centre[0], centre[1]));
        float px = ivyA.getFloat(at) - ivyA.getFloat(half), py = ivyB.getFloat(at) - ivyB.getFloat(half);
        Object ixh = xf(ivl);
        ixhSetPos.invoke(ixh, newIxl.newInstance(px, py));
        ixhSetRot.invoke(ixh, rot);
        ixhSetScale.invoke(ixh, adjust);
        ixhK.invoke(ixh); ixhL.invoke(ixh);
        tbaCall.invoke(jhuAdd.get(h), ivl);
        return ivl;
    }

    private static Object mapPoint(Object xf, float x, float y) throws Exception {
        float[] pt = {x, y};
        ((Matrix) ixhMatrix.invoke(xf)).mapPoints(pt);
        return newIvy.newInstance(pt[0], pt[1]);
    }

    private static float[] topOfPicture(Activity a, View cv) {
        float dens = a.getResources().getDisplayMetrics().density;
        RectF r = cv == null ? null : DoodleOverlay.Bridge.sceneRect(cv);
        if (r == null || r.isEmpty()) return visibleCentre(a, cv);
        return new float[]{r.centerX(), r.top + 20 * dens};
    }

    private static float[] visibleCentre(Activity a, View cv) {
        float dens = a.getResources().getDisplayMetrics().density;
        View decor = a.getWindow().getDecorView();
        float h = decor.getHeight(), w = decor.getWidth();
        float top = 120 * dens, bottom = h * 0.58f - 150 * dens;
        float sx = w / 2f, sy = Math.max(top + 40 * dens, (top + bottom) / 2f);
        if (cv == null) return new float[]{sx, sy};
        int[] at = new int[2]; cv.getLocationOnScreen(at);
        float z = cv.getScaleX(), tx = cv.getTranslationX(), ty = cv.getTranslationY();
        float ox = at[0] - tx, oy = at[1] - ty;
        if (z <= 0f) z = 1f;
        return new float[]{(sx - ox - tx) / z, (sy - oy - ty) / z};
    }

    static void stopEditing(boolean save) {
        Object ivl = editingRef.get();
        if (ivl == null) return;
        editingRef = new WeakReference<Object>(null);
        endTyping();
        try {
            String text = input == null ? (String) ivlText.get(ivl) : input.getText().toString();
            Object h = jhu.get();
            if (save && text.trim().isEmpty() && h != null) {
                tbaCall.invoke(jhuDelete.get(h), ivl);
            } else if (save && h != null) {
                Object style = ivlStyle.get(ivl);
                boolean sameText = text.equals(origText), sameStyle = style.equals(origStyle);
                ivlSetText.invoke(ivl, origText); ivlSetStyle.invoke(ivl, origStyle); ivlLayout.invoke(ivl);
                Object step = newIxk.newInstance(ivl, sameText ? null : text, sameStyle ? null : style);
                ixkApply.invoke(step);
                if (!(sameText && sameStyle) && undoPush != null) undoPush.invoke(loaUndo.get(jhuK.get(h)), step);
            }
        } catch (Throwable t) { }
        CropKeep.resume();
        redraw();
        refreshMenu();
    }

    private static boolean checkTap;
    private static final float CHECK_RISE = 21f;

    private static boolean showsCheck(Object ivl) {
        try {
            String text = editingRef.get() == ivl && input != null ? input.getText().toString() : (String) ivlText.get(ivl);
            return text != null && text.trim().length() > 0;
        } catch (Throwable t) { return true; }
    }

    private static volatile float framePerDp;

    private static void checkPressed() {
        try { if (editing() != null) stopEditing(true); } catch (Throwable ignored) { }
        deselect();
        main.postDelayed(new Runnable() { @Override public void run() {
            try { if (editing() == null && cIvl.isInstance(selected())) { deselect(); if (cIvl.isInstance(selected())) CaptionDeselect.tapAway(true); } } catch (Throwable ignored) { }
        } }, 150);
    }

    private static void deselect() {
        try {
            Object scene = scene();
            if (scene == null || !cIvl.isInstance(selected())) return;
            iwpSelect.invoke(scene, new Object[]{null});
            redraw();
            View cv = ScreenshotEditor.canvasView.get();
            if (cv != null) cv.invalidate();
        } catch (Throwable ignored) { }
    }

    static boolean donePressed() {
        if (editing() == null) return false;
        stopEditing(true);
        return true;
    }

    private static Typer input;
    private static int savedSoftInput = -1;
    private static final Handler main = new Handler(Looper.getMainLooper());
    private static boolean caretOn;
    private static final Runnable blink = new Runnable() {
        @Override public void run() { caretOn = !caretOn; redraw(); if (editing() != null) main.postDelayed(this, 530); }
    };

    static final class Typer extends EditText {
        Typer(Context c) { super(c); }
        @Override protected void onSelectionChanged(int s, int e) { super.onSelectionChanged(s, e); caretOn = true; redraw(); }
    }

    private static void startTyping(final Object ivl) throws Exception {
        final Activity a = ScreenshotEditor.activity();
        if (a == null) return;
        ViewGroup decor = (ViewGroup) a.getWindow().getDecorView();
        if (input == null || input.getContext() != a) {
            if (input != null && input.getParent() != null) ((ViewGroup) input.getParent()).removeView(input);
            input = new Typer(a);
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
            input.setImeOptions(EditorInfo.IME_FLAG_NO_ENTER_ACTION | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
            input.setBackground(null);
            input.setAlpha(0f);
            input.setCursorVisible(false);
            input.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int st, int c, int af) { }
                @Override public void onTextChanged(CharSequence s, int st, int b, int c) { }
                @Override public void afterTextChanged(Editable s) { typed(s.toString()); }
            });
        }
        if (input.getParent() == null) decor.addView(input, new FrameLayout.LayoutParams(1, 1, Gravity.TOP | Gravity.START));
        if (savedSoftInput < 0) {
            savedSoftInput = a.getWindow().getAttributes().softInputMode;
            a.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING | WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED);
        }
        String text = (String) ivlText.get(ivl);
        typing = true;
        input.setText(text);
        input.setSelection(text.length());
        typing = false;
        showKeyboard();
        caretOn = true;
        main.removeCallbacks(blink); main.postDelayed(blink, 530);
        refreshMenu();
    }

    private static void showKeyboard() {
        final Typer t = input;
        if (t == null) return;
        t.setFocusableInTouchMode(true);
        t.requestFocus();
        t.post(new Runnable() { @Override public void run() {
            try { ((InputMethodManager) t.getContext().getSystemService(Context.INPUT_METHOD_SERVICE)).showSoftInput(t, 0); } catch (Throwable ignored) { }
        } });
    }

    private static void endTyping() {
        main.removeCallbacks(blink);
        Activity a = ScreenshotEditor.activity();
        if (input != null) {
            try { ((InputMethodManager) input.getContext().getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(input.getWindowToken(), 0); } catch (Throwable ignored) { }
            input.clearFocus();
        }
        if (a != null && savedSoftInput >= 0) { try { a.getWindow().setSoftInputMode(savedSoftInput); } catch (Throwable ignored) { } }
        savedSoftInput = -1;
    }

    private static boolean typing;

    private static void typed(String text) {
        Object ivl = editing();
        if (ivl == null || typing) return;
        try {
            RectF before = new RectF((RectF) ivlBox.get(ivl));
            Object xf = xf(ivl);
            float[] c0 = {before.centerX(), before.top};
            ((Matrix) ixhMatrix.invoke(xf)).mapPoints(c0);
            ivlSetText.invoke(ivl, text);
            syncFace(ivl);
            ivlLayout.invoke(ivl);
            if (!widths.containsKey(ivl)) {
                RectF after = (RectF) ivlBox.get(ivl);
                float[] c1 = {after.centerX(), after.top};
                ((Matrix) ixhMatrix.invoke(xf)).mapPoints(c1);
                ixhMove.invoke(xf, newIxl.newInstance(c0[0] - c1[0], c0[1] - c1[1]));
            }
        } catch (Throwable t) { }
        caretOn = true;
        redraw();
    }

    private static boolean syncFace(Object ivl) {
        try {
            Object font = ivmFont.get(ivlStyle.get(ivl));
            Typeface tf = font == null ? null : (Typeface) ikaFace.get(font);
            TextPaint paint = (TextPaint) ivlPaint.get(ivl);
            if (tf == null || paint.getTypeface() == tf) return false;
            paint.setTypeface(tf);
            ivlDirty.setBoolean(ivl, true);
            return true;
        } catch (Throwable t) { return false; }
    }

    private static void syncFaceAndLayout(Object ivl) {
        try {
            RectF before = new RectF((RectF) ivlBox.get(ivl));
            Object xf = xf(ivl);
            float[] c0 = {before.centerX(), before.top};
            ((Matrix) ixhMatrix.invoke(xf)).mapPoints(c0);
            if (!syncFace(ivl)) return;
            ivlLayout.invoke(ivl);
            RectF after = (RectF) ivlBox.get(ivl);
            float[] c1 = {after.centerX(), after.top};
            ((Matrix) ixhMatrix.invoke(xf)).mapPoints(c1);
            ixhMove.invoke(xf, newIxl.newInstance(c0[0] - c1[0], c0[1] - c1[1]));
            redraw();
        } catch (Throwable t) { }
    }

    static final class Line { int start; String text; RectF rect; boolean empty; }

    @SuppressWarnings("unchecked")
    private static void layout(Object ivl) throws Exception {
        TextPaint paint = (TextPaint) ivlPaint.get(ivl);
        String text = (String) ivlText.get(ivl);
        float pad = ivlPad.getFloat(ivl);
        List<Object> list = (List<Object>) ivlLines.get(ivl);
        RectF box = (RectF) ivlBox.get(ivl);
        list.clear();
        float spacing = paint.getFontSpacing();
        ivlSpacing.setFloat(ivl, spacing);
        box.set(0, 0, 0, 0);
        Float width = widths.get(ivl);
        List<Line> lines = new ArrayList<Line>();
        float y = pad, widest = 0;
        boolean any = false;
        int index = 0;
        for (String para : text.split("\n", -1)) {
            List<int[]> parts = width == null ? single(para) : wrap(para, width, paint);
            for (int[] part : parts) {
                String s = para.substring(part[0], part[1]);
                Line ln = new Line(); ln.start = index + part[0]; ln.text = s;
                if (s.trim().isEmpty()) {
                    ln.empty = true; ln.rect = new RectF(0, y, 0, y + spacing);
                    y += spacing + pad;
                } else {
                    Rect r = new Rect();
                    paint.getTextBounds(s, 0, s.length(), r);
                    RectF rf = new RectF(r.left, y, r.right, y + r.height());
                    list.add(newLpp.newInstance(s, rf));
                    ln.rect = rf;
                    widest = Math.max(widest, r.width());
                    y += r.height() + pad;
                    any = true;
                }
                lines.add(ln);
            }
            index += para.length() + 1;
        }
        float boxW = (width != null ? Math.max(width, widest) : Math.max(widest, editingRef.get() == ivl && !any ? spacing * 0.6f : 0f)) + pad * 2;
        for (Line ln : lines) {
            float w = ln.rect.width();
            ln.rect.left = ln.rect.left + boxW / 2f - w / 2f;
            ln.rect.right = ln.rect.left + w;
        }
        if (any || width != null || editingRef.get() == ivl) box.set(0, 0, boxW, y);
        laid.put(ivl, lines);
    }

    private static List<int[]> single(String para) { List<int[]> l = new ArrayList<int[]>(); l.add(new int[]{0, para.length()}); return l; }

    private static List<int[]> wrap(String para, float width, TextPaint paint) {
        List<int[]> out = new ArrayList<int[]>();
        int start = 0, n = para.length();
        if (n == 0) { out.add(new int[]{0, 0}); return out; }
        while (start < n) {
            int end = start, lastBreak = -1;
            while (end < n) {
                float w = paint.measureText(para, start, end + 1);
                if (w > width && end > start) break;
                if (para.charAt(end) == ' ') lastBreak = end + 1;
                end++;
            }
            if (end < n && lastBreak > start) end = lastBreak;
            out.add(new int[]{start, end});
            start = end;
        }
        return out;
    }

    private static final Paint white = new Paint(Paint.ANTI_ALIAS_FLAG), shade = new Paint(Paint.ANTI_ALIAS_FLAG), fill = new Paint(Paint.ANTI_ALIAS_FLAG), caret = new Paint();

    private static void frame(Canvas canvas, Object area, Object cfg, Object ivl, Object iwi) throws Exception {
        Object xf = xf(ivl);
        float perDp = (iwbDensity.getFloat(cfg) / iwbScale.getFloat(cfg)) / ixhScale.getFloat(xf);
        iwiAlpha.setFloat(iwi, 1f);
        Matrix u = (Matrix) fqaU.invoke(null, xf, area);
        RectF box = new RectF((RectF) ivlBox.get(ivl));
        float out = 6 * perDp;
        box.inset(-out, -out);
        int save = canvas.save();
        canvas.concat(u);
        try {
            if (!showsCheck(ivl)) {
                if (editing() == ivl && caretOn && input != null) drawCaret(canvas, ivl, input.getSelectionEnd(), perDp);
                return;
            }
            white.setStyle(Paint.Style.STROKE); white.setColor(0xffffffff); white.setStrokeWidth(2 * perDp);
            shade.setStyle(Paint.Style.STROKE); shade.setColor(0x59000000); shade.setStrokeWidth(4 * perDp);
            float r = 12 * perDp;
            canvas.drawRoundRect(box, r, r, shade);
            canvas.drawRoundRect(box, r, r, white);
            float hw = 4 * perDp, hh = 11 * perDp;
            for (float x : new float[]{box.left, box.right}) pill(canvas, x - hw, box.centerY() - hh, x + hw, box.centerY() + hh, hw);
            float cr = 6 * perDp;
            for (float[] c : new float[][]{{box.right, box.top}, {box.right, box.bottom}, {box.left, box.bottom}}) dot(canvas, c[0], c[1], cr, perDp);
            fill.setStyle(Paint.Style.FILL); fill.setColor(0xffffffff);
            framePerDp = perDp;
            float xr = 12 * perDp, cx = ((RectF) ivlBox.get(ivl)).left, cy = ((RectF) ivlBox.get(ivl)).top;
            Paint x = new Paint(Paint.ANTI_ALIAS_FLAG); x.setStyle(Paint.Style.STROKE); x.setStrokeWidth(2 * perDp); x.setColor(0xff1d1b20); x.setStrokeCap(Paint.Cap.ROUND);
            if (showsCheck(ivl)) {
                canvas.drawCircle(cx, cy, xr, fill);
                float k = 4.5f * perDp;
                canvas.drawLine(cx - k, cy - k, cx + k, cy + k, x); canvas.drawLine(cx + k, cy - k, cx - k, cy + k, x);
                float vx = ((RectF) ivlBox.get(ivl)).centerX(), vy = cy - CHECK_RISE * perDp;
                canvas.drawCircle(vx, vy, xr, fill);
                android.graphics.Path tick = new android.graphics.Path();
                tick.moveTo(vx - 5f * perDp, vy + 0.5f * perDp); tick.lineTo(vx - 1.5f * perDp, vy + 4f * perDp); tick.lineTo(vx + 5.5f * perDp, vy - 4f * perDp);
                x.setStrokeJoin(Paint.Join.ROUND);
                canvas.drawPath(tick, x);
            }
            if (editing() == ivl && caretOn && input != null) drawCaret(canvas, ivl, input.getSelectionEnd(), perDp);
        } finally { canvas.restoreToCount(save); }
    }

    private static void pill(Canvas c, float l, float t, float r, float b, float rad) {
        fill.setStyle(Paint.Style.FILL); fill.setColor(0xffffffff);
        shade.setStrokeWidth(rad * 0.5f);
        c.drawRoundRect(l, t, r, b, rad, rad, fill);
        c.drawRoundRect(l, t, r, b, rad, rad, shade);
    }
    private static void dot(Canvas c, float x, float y, float rad, float perDp) {
        fill.setStyle(Paint.Style.FILL); fill.setColor(0xffffffff);
        shade.setStrokeWidth(1.5f * perDp);
        c.drawCircle(x, y, rad, fill);
        c.drawCircle(x, y, rad, shade);
    }

    private static void drawCaret(Canvas canvas, Object ivl, int at, float perDp) throws Exception {
        List<Line> lines = laid.get(ivl);
        TextPaint paint = (TextPaint) ivlPaint.get(ivl);
        RectF box = (RectF) ivlBox.get(ivl);
        Paint.FontMetrics fm = paint.getFontMetrics();
        float x = box.centerX(), base = box.top + ivlPad.getFloat(ivl) - fm.ascent;
        if (lines != null && !lines.isEmpty()) {
            Line pick = lines.get(lines.size() - 1);
            for (int i = 0; i < lines.size(); i++) {
                Line ln = lines.get(i);
                int end = ln.start + ln.text.length();
                boolean nextStartsHere = i + 1 < lines.size() && lines.get(i + 1).start == at;
                if (at >= ln.start && at <= end && !nextStartsHere) { pick = ln; break; }
            }
            if (pick.empty) { x = box.centerX(); base = pick.rect.top - fm.ascent * 0.8f; }
            else {
                int off = Math.max(0, Math.min(pick.text.length(), at - pick.start));
                x = pick.rect.left + paint.measureText(pick.text, 0, off);
                base = pick.rect.bottom;
            }
        }
        caret.setColor(paint.getColor() == 0xffffffff || paint.getColor() == -328966 ? 0xffffffff : paint.getColor());
        caret.setStrokeWidth(Math.max(2 * perDp, paint.getTextSize() * 0.045f));
        canvas.drawLine(x, base + fm.ascent * 0.92f, x, base + fm.descent * 0.6f, caret);
    }

    private static int drag;
    private static float startWidth, startScale, startDist, prevWidth;
    private static float[] startLocal, centre;
    private static Matrix toLocal;
    private static Object startPos;
    private static boolean menuTouch;
    static boolean menuTouching() { return menuTouch; }

    private static int remote;
    private static Object moveStep;
    private static float[] remoteRaw;
    private static Matrix screenToArea;

    private static void pushMove() {
        Object step = moveStep; moveStep = null;
        if (step == null) return;
        try {
            Method end = step.getClass().getDeclaredMethod("e"); end.setAccessible(true); end.invoke(step);
            Object h = jhu.get();
            if (h != null && undoPush != null) undoPush.invoke(loaUndo.get(jhuK.get(h)), step);
        } catch (Throwable t) { }
    }

    private static MotionEvent heldDown;
    private static Object remoteTarget;

    private static Object ownerOf(Object hit) {
        try {
            if (hit == null) return null;
            if (cIvl.isInstance(hit)) return hit;
            Object item = null;
            if (cIwh.isInstance(hit)) item = hit;
            else if (iwiItem.getDeclaringClass().isInstance(hit)) item = iwiItem.get(hit);
            else try { item = hitItem.invoke(hit); } catch (Throwable ignored) { }
            Object o = item == null ? null : iwhOwner.get(item);
            return cIvl.isInstance(o) ? o : null;
        } catch (Throwable t) { return null; }
    }

    private static boolean cropPageDown(Activity a, View cv, MotionEvent e) {
        try {
            remoteTarget = null;
            Object sel = selected();
            int found = cIvl.isInstance(sel) ? grab(a, cv, e, sel) : -1;
            if (found == 2) return true;
            drag = 0;
            if (found == 4) { checkTap = true; return true; }
            if (found == 3) return false;
            RectF box = ScreenshotEditor.currentCropRect();
            if (box != null && !box.isEmpty()) {
                float[] p = toCanvas(cv, e.getRawX(), e.getRawY());
                float reach = 32 * cv.getResources().getDisplayMetrics().density;
                boolean nearX = Math.abs(p[0] - box.left) < reach || Math.abs(p[0] - box.right) < reach;
                boolean nearY = Math.abs(p[1] - box.top) < reach || Math.abs(p[1] - box.bottom) < reach;
                if (nearX && nearY) { freezeSelected(); return false; }
            }
            Object hit = StudioItems.itemAt(cv, e), text = ownerOf(hit);
            if (text == null) return false;
            remoteTarget = text;
            remote = 1;
            remoteRaw = new float[]{e.getRawX(), e.getRawY()};
            if (heldDown != null) heldDown.recycle();
            heldDown = MotionEvent.obtain(e);
            return true;
        } catch (Throwable t) { remoteTarget = null; return false; }
    }

    private static float[] toCanvas(View cv, float rawX, float rawY) {
        int[] at = new int[2]; cv.getLocationOnScreen(at);
        float[] p = {rawX - at[0], rawY - at[1]};
        Matrix inv = new Matrix();
        Matrix m = new Matrix(cv.getMatrix()); m.postTranslate(-cv.getLeft(), -cv.getTop());
        if (m.invert(inv)) { p[0] += cv.getLeft(); p[1] += cv.getTop(); inv.mapPoints(p); }
        return p;
    }
    private static String lastHit = "?";

    private static boolean isOwner(Object hit, Object ivl) {
        try {
            Object item = ivlItem.get(ivl);
            if (hit == item) return true;
            if (iwiItem.getDeclaringClass().isInstance(hit)) return iwiItem.get(hit) == item;
            if (cIwh.isInstance(hit)) return iwhOwner.get(hit) == ivl;
            try { Object h = hitItem.invoke(hit); return h == item || (h != null && iwhOwner.get(h) == ivl); } catch (Throwable ignored) { }
        } catch (Throwable t) { }
        return false;
    }

    private static boolean replay(Activity a, MotionEvent e, boolean rest) {
        MotionEvent down = heldDown; heldDown = null;
        try {
            if (down != null) { a.getWindow().superDispatchTouchEvent(down); down.recycle(); }
            a.getWindow().superDispatchTouchEvent(e);
        } catch (Throwable t) { }
        remote = rest ? 3 : 0;
        return true;
    }

    private static boolean remoteMove(Activity a, View cv, MotionEvent e, int am) {
        try {
            if (remote == 4) { if (am == MotionEvent.ACTION_UP || am == MotionEvent.ACTION_CANCEL) remote = 0; return true; }
            if (remote == 3 || remote == 4) remoteTarget = null;
            if (remote == 3) {
                try { a.getWindow().superDispatchTouchEvent(e); } catch (Throwable ignored) { }
                if (am == MotionEvent.ACTION_UP || am == MotionEvent.ACTION_CANCEL) remote = 0;
                return true;
            }
            if (remote == 1 && remoteTarget != null && (am == MotionEvent.ACTION_UP || am == MotionEvent.ACTION_CANCEL)) { remoteTarget = null; return replay(a, e, false); }
            if (remote == 1 && (am == MotionEvent.ACTION_UP || am == MotionEvent.ACTION_CANCEL)) {
                MotionEvent d = heldDown;
                float y = e.getRawY();
                int[] at = new int[2]; a.getWindow().getDecorView().getLocationOnScreen(at);
                int top = barBottom(a), bottom = toolbarTop(a);
                boolean between = bottom <= top || (y > at[1] + top && y < at[1] + bottom);
                boolean onSelf = onSelectedText(e.getRawX(), e.getRawY());
                boolean other = false;
                if (!onSelf && d != null) try {
                    Object hit = StudioItems.itemAt(cv, d), sel = editing() != null ? editing() : selected();
                    boolean self = hit != null && sel != null && (hit == sel || hit == ivlItem.get(sel) || isOwner(hit, sel));
                    if (self) onSelf = true; else other = hit != null;
                    lastHit = hit == null ? "nothing" : hit.getClass().getSimpleName();
                } catch (Throwable ignored) { }
                if (between && am == MotionEvent.ACTION_UP && (onSelf || !other)) {
                    if (heldDown != null) { heldDown.recycle(); heldDown = null; }
                    remote = 0;
                    if (onSelf && editing() == null) {
                        Object sel = selected();
                        try { if (cIvl.isInstance(sel)) open(sel); } catch (Throwable t) { }
                    } else if (onSelf) showKeyboard();
                    else { }
                    return true;
                }
                return replay(a, e, false);
            }
            if (am == MotionEvent.ACTION_UP || am == MotionEvent.ACTION_CANCEL) {
                boolean ours = remote == 2; remote = 0; remoteTarget = null;
                if (ours) { pushMove(); redraw(); }
                return ours;
            }
            Object ivl = remoteTarget != null ? remoteTarget : editing();
            if (ivl == null) { Object s = selected(); if (cIvl.isInstance(s)) ivl = s; }
            if (remoteTarget == null && (ivl == null || (editing() == null && !"CAPTION".equals(TopBarKeep.currentPage())))) {
                if (heldDown != null) { heldDown.recycle(); heldDown = null; }
                remote = 4;
                return true;
            }
            if (remote == 1) {
                if (e.getPointerCount() > 1 || am == MotionEvent.ACTION_POINTER_DOWN) {
                    if (heldDown != null) { heldDown.recycle(); heldDown = null; }
                    remoteTarget = null;
                    if (editing() != null) { remote = 4; return true; }
                    remote = 0;
                    return false;
                }
                if (am != MotionEvent.ACTION_MOVE) return true;
                float slop = android.view.ViewConfiguration.get(a).getScaledTouchSlop();
                if (Math.hypot(e.getRawX() - remoteRaw[0], e.getRawY() - remoteRaw[1]) < slop) return true;
                if (heldDown != null) { heldDown.recycle(); heldDown = null; }
                Matrix toScreen = localToScreen(ivl, cv), item = new Matrix((Matrix) ixhMatrix.invoke(xf(ivl))), itemInv = new Matrix();
                if (toScreen == null || !item.invert(itemInv)) { remote = 0; return false; }
                Matrix areaToScreen = new Matrix(itemInv); areaToScreen.postConcat(toScreen);
                screenToArea = new Matrix();
                if (!areaToScreen.invert(screenToArea)) { remote = 0; return false; }
                remote = 2;
                moveStep = null;
                try {
                    Constructor<?> k = cIvl.getClassLoader().loadClass("ixi").getDeclaredConstructor(ivlItem.getType());
                    k.setAccessible(true);
                    moveStep = k.newInstance(ivlItem.get(ivl));
                } catch (Throwable t) { }
            }
            if (remote == 2 && am == MotionEvent.ACTION_MOVE) {
                float[] v = {e.getRawX() - remoteRaw[0], e.getRawY() - remoteRaw[1]};
                try {
                    Matrix ts = localToScreen(ivl, cv), it = new Matrix((Matrix) ixhMatrix.invoke(xf(ivl))), ii = new Matrix();
                    if (ts != null && it.invert(ii)) { Matrix as = new Matrix(ii); as.postConcat(ts); Matrix sa = new Matrix(); if (as.invert(sa)) screenToArea = sa; }
                } catch (Throwable ignored) { }
                screenToArea.mapVectors(v);
                ixhMove.invoke(xf(ivl), newIxl.newInstance(v[0], v[1]));
                remoteRaw = new float[]{e.getRawX(), e.getRawY()};
                redraw();
                if (remoteTarget != null) for (android.view.ViewParent vp = cv.getParent(); vp instanceof View; vp = vp.getParent()) ((View) vp).invalidate();
            }
            return true;
        } catch (Throwable t) { remote = 0; return false; }
    }

    static boolean touch(Activity a, View cv, MotionEvent e) {
        if (!on) return false;
        int am = e.getActionMasked();
        if (am == MotionEvent.ACTION_DOWN) fingerDown = true;
        else if (am == MotionEvent.ACTION_UP || am == MotionEvent.ACTION_CANCEL) {
            fingerDown = false; cutSteady = 0;
            if (!frozen.isEmpty()) { frozen.clear(); redraw(); }
        }
        if (am == MotionEvent.ACTION_DOWN) {
            menuTouch = onMenu(e.getRawX(), e.getRawY());
            drag = 0; remote = 0; remoteTarget = null; checkTap = false;
        }
        if (menuTouch) { if (am == MotionEvent.ACTION_UP || am == MotionEvent.ACTION_CANCEL) menuTouch = false; return false; }
        try {
            if (am == MotionEvent.ACTION_DOWN) {
                if (editing() == null && "CROP_TOOL".equals(TopBarKeep.currentPage())) return cropPageDown(a, cv, e);
                if (editing() == null && !"CAPTION".equals(TopBarKeep.currentPage())) return false;
                Object ivl = selected();
                if (!cIvl.isInstance(ivl)) { return false; }
                int found = grab(a, cv, e, ivl);
                if (found == 4) { drag = 0; checkTap = true; return true; }
                if (found < 0 || found == 3) { drag = 0; return false; }
                if (drag == 0) {
                    if (DoodleOverlay.active() || (found == 0 && CropGrab.wouldTake(cv, e.getRawX(), e.getRawY()))) { remote = 0; return false; }
                    remote = 1;
                    remoteRaw = new float[]{e.getRawX(), e.getRawY()};
                    if (heldDown != null) heldDown.recycle();
                    heldDown = MotionEvent.obtain(e);
                    return true;
                }
                return true;
            }
            if (checkTap) {
                if (am == MotionEvent.ACTION_UP || am == MotionEvent.ACTION_CANCEL) { checkTap = false; if (am == MotionEvent.ACTION_UP) checkPressed(); }
                return true;
            }
            if (remote != 0) return remoteMove(a, cv, e, am);
            if (drag == 0) return false;
            Object ivl = selected();
            if (!cIvl.isInstance(ivl)) { drag = 0; return true; }
            if (am == MotionEvent.ACTION_MOVE) {
                Object xf = xf(ivl);
                if (drag == 2) {
                    float k = (float) Math.hypot(e.getRawX() - screenCentre[0], e.getRawY() - screenCentre[1]) / startDist;
                    k = Math.max(0.15f, Math.min(8f, k));
                    ixhSetScale.invoke(xf, startScale); ixhSetPos.invoke(xf, startPos);
                    ixhScaleAbout.invoke(xf, k, newIvy.newInstance(centre[0], centre[1]));
                } else {
                    float[] pt = {e.getRawX(), e.getRawY()};
                    toLocal.mapPoints(pt);
                    TextPaint paint = (TextPaint) ivlPaint.get(ivl);
                    float min = paint.getTextSize() * 0.8f;
                    float w = Math.max(min, startWidth + (pt[0] - startLocal[0]) * drag);
                    widths.put(ivl, w);
                    ivlLayout.invoke(ivl);
                    if (drag == -1) {
                        float d = w - prevWidth;
                        float[] v = {-d, 0};
                        ((Matrix) ixhMatrix.invoke(xf)).mapVectors(v);
                        ixhMove.invoke(xf, newIxl.newInstance(v[0], v[1]));
                    }
                    prevWidth = w;
                }
                redraw();
            } else if (am == MotionEvent.ACTION_UP || am == MotionEvent.ACTION_CANCEL) {
                drag = 0;
                redraw();
            }
            return true;
        } catch (Throwable t) { drag = 0; return false; }
    }

    private static int grab(Activity a, View cv, MotionEvent e, Object ivl) throws Exception {
        Matrix toScreen = localToScreen(ivl, cv);
        if (toScreen == null) return -1;
        toLocal = new Matrix();
        if (!toScreen.invert(toLocal)) return -1;
        float[] pt = {e.getRawX(), e.getRawY()};
        toLocal.mapPoints(pt);
        float perPx = toLocal.mapRadius(1f);
        float reach = 26 * a.getResources().getDisplayMetrics().density * perPx;
        RectF box = new RectF((RectF) ivlBox.get(ivl));
        float dens = a.getResources().getDisplayMetrics().density;
        box.inset(-6 * dens * perPx, -6 * dens * perPx);
        float pad = ivlPad.getFloat(ivl);
        RectF inner = new RectF((RectF) ivlBox.get(ivl));
        inner.inset(14 * dens * perPx, 0);
        drag = 0;
        RectF plain = (RectF) ivlBox.get(ivl);
        boolean marks = showsCheck(ivl);
        float drawDp = framePerDp > 0 ? framePerDp : dens * perPx, hit = Math.max(22 * dens * perPx, 16 * drawDp);
        if (marks && Math.hypot(pt[0] - plain.left, pt[1] - plain.top) <= hit) return 3;
        if (marks && Math.hypot(pt[0] - plain.centerX(), pt[1] - (plain.top - CHECK_RISE * drawDp)) <= hit) return 4;
        boolean inside = inner.contains(pt[0], pt[1]);
        if (!marks) return inside || box.contains(pt[0], pt[1]) ? 1 : 0;
        if (inside) {  }
        else if (near(pt, box.right, box.centerY(), reach)) drag = 1;
        else if (near(pt, box.left, box.centerY(), reach)) drag = -1;
        else if (near(pt, box.right, box.top, reach) || near(pt, box.right, box.bottom, reach) || near(pt, box.left, box.bottom, reach)) drag = 2;
        if (drag == 0) return inside || box.contains(pt[0], pt[1]) ? 1 : 0;
        startLocal = pt;
        Float w = widths.get(ivl);
        startWidth = prevWidth = w != null ? w : ((RectF) ivlBox.get(ivl)).width() - 2 * pad;
        Object xf = xf(ivl);
        startScale = ixhScale.getFloat(xf);
        startPos = ixhPos.get(xf);
        RectF b = (RectF) ivlBox.get(ivl);
        float[] c = {b.centerX(), b.centerY()};
        ((Matrix) ixhMatrix.invoke(xf)).mapPoints(c);
        centre = c;
        float[] cs = {b.centerX(), b.centerY()};
        toScreen.mapPoints(cs);
        startDist = Math.max(1f, (float) Math.hypot(e.getRawX() - cs[0], e.getRawY() - cs[1]));
        screenCentre = cs;
        return 2;
    }

    private static float[] screenCentre;

    private static boolean near(float[] p, float x, float y, float reach) { return Math.abs(p[0] - x) <= reach && Math.abs(p[1] - y) <= reach; }

    private static Matrix localToScreen(Object ivl, View cv) throws Exception {
        Object scene = scene();
        if (scene == null || cv == null) return null;
        Matrix m = new Matrix((Matrix) ixhMatrix.invoke(xf(ivl)));
        Object p0 = iwpViewToArea.invoke(scene, newIvy.newInstance(0f, 0f)), p1 = iwpViewToArea.invoke(scene, newIvy.newInstance(1000f, 0f)), p2 = iwpViewToArea.invoke(scene, newIvy.newInstance(0f, 1000f));
        float[] src = {0, 0, 1000, 0, 0, 1000};
        float[] dst = {ivyA.getFloat(p0), ivyB.getFloat(p0), ivyA.getFloat(p1), ivyB.getFloat(p1), ivyA.getFloat(p2), ivyB.getFloat(p2)};
        Matrix viewToArea = new Matrix();
        if (!viewToArea.setPolyToPoly(src, 0, dst, 0, 3)) return null;
        Matrix areaToView = new Matrix();
        if (!viewToArea.invert(areaToView)) return null;
        m.postConcat(areaToView);
        m.postConcat(cv.getMatrix());
        int[] at = new int[2]; cv.getLocationOnScreen(at);
        m.postTranslate(at[0] - cv.getTranslationX(), at[1] - cv.getTranslationY());
        return m;
    }

    private static WeakReference<View> menu = new WeakReference<View>(null);
    private static Object menuFor; private static boolean menuTyping;

    static boolean onMenu(float x, float y) {
        View v = menu.get();
        if (v == null || !v.isAttachedToWindow() || v.getAlpha() < 0.5f) return false;
        int[] at = new int[2]; v.getLocationOnScreen(at);
        return x >= at[0] && y >= at[1] && x < at[0] + v.getWidth() && y < at[1] + v.getHeight();
    }

    static void refreshMenu() {
        if (!on) return;
        try {
            Object ivl = editing();
            boolean typingNow = ivl != null;
            if (ivl == null) {
                Object s = selected();
                if (cIvl.isInstance(s) && "CAPTION".equals(TopBarKeep.currentPage())) ivl = s;
            }
            if (DoodleOverlay.active()) ivl = null;
            if (ivl == null) { hideMenu(); return; }
            View v = menu.get();
            if (v != null && v.isAttachedToWindow() && menuFor == ivl && menuTyping == typingNow) { mark(v, ivl); return; }
            hideMenu();
            showMenu(ivl, typingNow);
        } catch (Throwable t) { }
    }

    private static void hideMenu() {
        View v = menu.get();
        menu = new WeakReference<View>(null); menuFor = null;
        if (widthAnim != null) { widthAnim.cancel(); widthAnim = null; progress = sExpanded ? 1f : 0f; }
        main.removeCallbacks(follow);
        if (v != null && v.getParent() != null) ((ViewGroup) v.getParent()).removeView(v);
    }

    static final int SHOWN = 4;
    static boolean sExpanded;
    private static float progress;
    private static android.animation.ValueAnimator widthAnim;
    private static View moreFonts, moreColours, sideGrabber, doneView;
    private static LinearLayout outer, fontRowV, colourRowV;
    private static NoScroll fontScrollV, colourScrollV;
    private static final List<View> extraFonts = new ArrayList<View>(), extraColours = new ArrayList<View>();
    private static int nFonts;
    private static float mDens = 1f;

    static final class NoScroll extends HorizontalScrollView {
        boolean scrollable;
        NoScroll(Context c) { super(c); setHorizontalScrollBarEnabled(false); setOverScrollMode(OVER_SCROLL_NEVER); setFillViewport(true); }
        @Override public boolean onInterceptTouchEvent(MotionEvent e) { return scrollable && super.onInterceptTouchEvent(e); }
        @Override public boolean onTouchEvent(MotionEvent e) { return scrollable && super.onTouchEvent(e); }
    }

    private static int dp(float v) { return Math.round(v * mDens); }
    private static int padW() { return dp(12); }
    private static int grabW() { return dp(24); }
    private static int doneW() { return doneView != null ? dp(48) : 0; }
    private static int rowH() { return dp(40); }
    private static int screenW() { Activity a = ScreenshotEditor.activity(); return (a == null ? dp(400) : a.getResources().getDisplayMetrics().widthPixels) - dp(16); }
    static final int FULL_COLUMNS = 8;
    private static int columns() { return Math.max(SHOWN + 1, Math.min(FULL_COLUMNS, nFonts)); }
    private static float colCompact() { return Math.min(dp(52), (screenW() - 2f * padW() - doneW()) / (SHOWN + 1)); }
    private static float colFull() { return (screenW() - 2f * padW() - grabW() - doneW()) / columns(); }
    private static int compactWidth() { return Math.round(2 * padW() + doneW() + colCompact() * (SHOWN + 1)); }
    private static float col(float p) { return colCompact() + (colFull() - colCompact()) * p; }
    private static int widthAt(float p) { return Math.round(2 * padW() + grabW() * p + doneW() + col(p) * (SHOWN + 1 + (columns() - SHOWN - 1) * p)); }

    private static void width(View v, int w) {
        ViewGroup.LayoutParams lp = v.getLayoutParams();
        if (lp != null && lp.width != w) { lp.width = w; v.setLayoutParams(lp); }
    }

    private static void applyProgress() {
        View shell = menu.get();
        if (shell == null || outer == null) return;
        float p = Math.max(0f, Math.min(1f, progress));
        int w = widthAt(p);
        ViewGroup.LayoutParams lp = shell.getLayoutParams();
        if (lp != null && lp.width != w) { lp.width = w; shell.setLayoutParams(lp); }
        float c = col(p);
        distribute(fontRowV, c, p);
        distribute(colourRowV, c, p);
        for (View more : new View[]{moreFonts, moreColours}) {
            if (more == null) continue;
            more.setEnabled(p < 0.5f);
            float fold = Math.min(1f, p * 1.4f);
            more.setAlpha(fold >= 1f ? 0f : 1f);
            more.setScaleX(1 - fold); more.setScaleY(1 - fold);
        }
        grow(extraFonts, p, c);
        grow(extraColours, p, c);
        sideGrabber.setAlpha(Math.max(0f, (p - 0.5f) * 2f));
        sideGrabber.setTranslationX((1 - p) * dp(16));
        sideGrabber.setVisibility(p > 0.01f ? View.VISIBLE : View.INVISIBLE);
        outer.setPadding(padW(), dp(2), padW() + Math.round(grabW() * p), dp(2));
        fontScrollV.scrollable = colourScrollV.scrollable = p >= 1f;
        if (p <= 0f) { fontScrollV.scrollTo(0, 0); colourScrollV.scrollTo(0, 0); }
    }

    private static void distribute(ViewGroup row, float c, float p) {
        if (row == null) return;
        float x = 0;
        for (int i = 0; i < row.getChildCount(); i++) {
            View v = row.getChildAt(i);
            float w = (v == moreFonts || v == moreColours) ? c * (1 - p) : (extraFonts.contains(v) || extraColours.contains(v)) ? c * p : c;
            width(v, Math.round(x + w) - Math.round(x));
            x += w;
        }
    }

    private static void grow(List<View> views, float p, float c) {
        int w = Math.round(c * p);
        for (int i = 0; i < views.size(); i++) {
            View v = views.get(i);
            float start = Math.min(i, 7) * 0.05f, local = Math.max(0f, Math.min(1f, (p - 0.2f - start) / 0.45f));
            v.setAlpha(w > dp(2) ? 1f : 0f);
            float sc = 0.5f + 0.5f * local;
            v.setScaleX(sc); v.setScaleY(sc);
            v.setEnabled(local > 0.5f);
        }
    }

    static void setExpanded(boolean open, boolean animate) {
        sExpanded = open;
        if (widthAnim != null) widthAnim.cancel();
        float to = open ? 1f : 0f;
        if (!animate) { progress = to; applyProgress(); return; }
        widthAnim = android.animation.ValueAnimator.ofFloat(progress, to);
        widthAnim.setDuration(open ? 380 : 300);
        widthAnim.setInterpolator(open ? new android.view.animation.PathInterpolator(0.2f, 0f, 0f, 1f)
                                       : new android.view.animation.PathInterpolator(0.3f, 0f, 0.1f, 1f));
        widthAnim.addUpdateListener(new android.animation.ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(android.animation.ValueAnimator va) { progress = (Float) va.getAnimatedValue(); applyProgress(); }
        });
        widthAnim.start();
    }

    private static View sideGrabber(Activity a, final int ink) {
        final View g = new View(a) {
            final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            final android.graphics.Path arrow = new android.graphics.Path();
            @Override protected void onDraw(Canvas canvas) {
                float cx = getWidth() - dp(12), cy = getHeight() / 2f, h = dp(isPressed() ? 36 : 30), w = dp(4);
                p.setStyle(Paint.Style.FILL);
                p.setColor(ink); p.setAlpha(isPressed() ? 255 : 150);
                canvas.drawRoundRect(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f, w / 2f, w / 2f, p);
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(2)); p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeJoin(Paint.Join.ROUND);
                float ax = cx - dp(9), sz = 3.5f * mDens;
                arrow.rewind();
                arrow.moveTo(ax + sz * 0.6f, cy - sz); arrow.lineTo(ax - sz * 0.4f, cy); arrow.lineTo(ax + sz * 0.6f, cy + sz);
                canvas.drawPath(arrow, p);
            }
        };
        g.setContentDescription("Make text menu smaller");
        g.setOnTouchListener(new View.OnTouchListener() {
            float startX, startP; android.view.VelocityTracker vt; boolean moved;
            @Override public boolean onTouch(View v, MotionEvent e) {
                if (progress <= 0.01f && e.getActionMasked() == MotionEvent.ACTION_DOWN) return false;
                if (vt != null) { MotionEvent r = MotionEvent.obtain(e); r.setLocation(e.getRawX(), e.getRawY()); vt.addMovement(r); r.recycle(); }
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN: {
                        if (widthAnim != null) widthAnim.cancel();
                        startX = e.getRawX(); startP = progress; moved = false;
                        if (vt != null) vt.recycle();
                        vt = android.view.VelocityTracker.obtain();
                        MotionEvent r = MotionEvent.obtain(e); r.setLocation(e.getRawX(), e.getRawY()); vt.addMovement(r); r.recycle();
                        v.setPressed(true); v.invalidate();
                        buzz(v, android.view.HapticFeedbackConstants.LONG_PRESS);
                        if (v.getParent() != null) v.getParent().requestDisallowInterceptTouchEvent(true);
                        return true;
                    }
                    case MotionEvent.ACTION_MOVE: {
                        float dx = e.getRawX() - startX;
                        if (Math.abs(dx) > dp(4)) moved = true;
                        float span = Math.max(1, (screenW() - compactWidth()) / 2f);
                        progress = Math.max(0f, Math.min(1f, startP + dx / span));
                        applyProgress();
                        return true;
                    }
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL: {
                        v.setPressed(false); v.invalidate();
                        float vx = 0;
                        if (vt != null) { vt.computeCurrentVelocity(1000); vx = vt.getXVelocity(); vt.recycle(); vt = null; }
                        boolean close = !moved || vx < -dp(300) || (vx < dp(300) && progress < 0.7f);
                        if (close) buzz(v, android.view.HapticFeedbackConstants.CLOCK_TICK);
                        setExpanded(!close, true);
                        return true;
                    }
                }
                return true;
            }
        });
        return g;
    }

    static final class MoreFonts extends View {
        final int ink; boolean ring;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        MoreFonts(Context c, int ink) { super(c); this.ink = ink; }
        @Override protected void onDraw(Canvas c) {
            float d = getResources().getDisplayMetrics().density, cx = getWidth() / 2f, cy = getHeight() / 2f;
            if (ring) { p.setStyle(Paint.Style.FILL); p.setColor((ink & 0x00ffffff) | 0x26000000); c.drawRoundRect(cx - 22 * d, cy - 18 * d, cx + 22 * d, cy + 18 * d, 12 * d, 12 * d, p); }
            p.setStyle(Paint.Style.FILL); p.setColor(ink); p.setTextAlign(Paint.Align.CENTER); p.setTextSize(15 * d);
            c.drawText("Aa", cx - 3 * d, cy + 5 * d, p);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.8f * d); p.setStrokeCap(Paint.Cap.ROUND);
            float px = cx + 11 * d, py = cy - 7 * d, r = 3.5f * d;
            c.drawLine(px - r, py, px + r, py, p); c.drawLine(px, py - r, px, py + r, p);
        }
    }

    private static void showMenu(final Object ivl, boolean typingNow) throws Exception {
        final Activity a = ScreenshotEditor.activity();
        Object h = jhu.get();
        if (a == null || h == null) return;
        Object defaults = jhuDefaults.invoke(h);
        List<?> fonts = (List<?>) ijwFonts.invoke(defaults), colours = (List<?>) ijwColours.invoke(defaults);
        if (fonts == null || colours == null) return;
        final float dens = a.getResources().getDisplayMetrics().density;
        mDens = dens;
        nFonts = fonts.size();
        extraFonts.clear(); extraColours.clear();
        FrameLayout shell = StudioToolbar.shell(a, 26);
        int ink = StudioToolbar.ringColour();
        outer = new LinearLayout(a); outer.setOrientation(LinearLayout.VERTICAL);
        outer.addView(pill(a, ink), new LinearLayout.LayoutParams(-1, dp(14)));
        LinearLayout middle = new LinearLayout(a); middle.setOrientation(LinearLayout.HORIZONTAL); middle.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout rows = new LinearLayout(a); rows.setOrientation(LinearLayout.VERTICAL);
        fontScrollV = new NoScroll(a);
        fontRowV = new LinearLayout(a);
        moreFonts = null;
        for (int i = 0; i < fonts.size(); i++) {
            if (i == SHOWN) {
                moreFonts = new MoreFonts(a, ink);
                moreFonts.setContentDescription("More fonts");
                moreFonts.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { setExpanded(true, true); } });
                fontRowV.addView(moreFonts, new LinearLayout.LayoutParams(dp(52), rowH()));
            }
            final Object font = fonts.get(i);
            TextView t = new TextView(a);
            t.setText("Aa"); t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18); t.setTextColor(ink); t.setGravity(Gravity.CENTER);
            try { Typeface tf = (Typeface) ikaFace.get(font); if (tf != null) t.setTypeface(tf); t.setContentDescription((String) ikaName.get(font)); } catch (Throwable ignored) { }
            t.setTag(font);
            t.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { style(null, font); } });
            fontRowV.addView(t, new LinearLayout.LayoutParams(dp(52), rowH()));
            if (i >= SHOWN) extraFonts.add(t);
        }
        fontScrollV.addView(fontRowV);
        rows.addView(fontScrollV, new LinearLayout.LayoutParams(-1, -2));
        colourScrollV = new NoScroll(a);
        colourRowV = new LinearLayout(a);
        moreColours = null;
        for (int i = 0; i < colours.size(); i++) {
            if (i == SHOWN) {
                moreColours = new View(a);
                moreColours.setBackground(new DoodleOverlay.RainbowIcon(dens));
                moreColours.setContentDescription("More colours");
                moreColours.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { setExpanded(true, true); } });
                colourRowV.addView(moreColours, new LinearLayout.LayoutParams(dp(52), rowH()));
            }
            final Object colour = colours.get(i);
            Dot d = new Dot(a, ijvInt.getInt(colour), ink);
            d.setTag(colour);
            d.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { style(colour, null); } });
            colourRowV.addView(d, new LinearLayout.LayoutParams(dp(52), rowH()));
            if (i >= SHOWN) extraColours.add(d);
        }
        colourScrollV.addView(colourRowV);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
        cp.topMargin = dp(4);
        rows.addView(colourScrollV, cp);
        middle.addView(rows, new LinearLayout.LayoutParams(0, -2, 1f));
        doneView = null;
        if (typingNow) {
            TextView done = new TextView(a);
            done.setText("✓"); done.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20); done.setGravity(Gravity.CENTER);
            GradientDrawable g = new GradientDrawable(); g.setShape(GradientDrawable.OVAL); g.setColor(ink);
            done.setBackground(g);
            done.setTextColor(StudioToolbar.darkMenu() ? 0xff1d1b20 : 0xffffffff);
            done.setContentDescription("Done");
            done.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { stopEditing(true); } });
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dp(40), dp(40));
            dlp.leftMargin = dp(8);
            middle.addView(done, dlp);
            doneView = done;
        }
        outer.addView(middle, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(-1, dp(14));
        outer.addView(pill(a, ink), pp);
        shell.addView(outer, new FrameLayout.LayoutParams(-1, -2));
        sideGrabber = sideGrabber(a, ink);
        shell.addView(sideGrabber, new FrameLayout.LayoutParams(padW() + grabW(), dp(96), Gravity.END | Gravity.CENTER_VERTICAL));
        ViewGroup decor = (ViewGroup) a.getWindow().getDecorView();
        progress = sExpanded ? 1f : 0f;
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(widthAt(progress), -2, BOTTOM_G);
        lp.bottomMargin = bottomMargin(a);
        if (typingNow && CropKeep.suspended) up(a, lp);
        if (!Float.isNaN(userTop)) { lp.gravity = TOP_G; lp.bottomMargin = 0; lp.topMargin = Math.round(userTop); }
        cutTarget = null;
        decor.addView(shell, lp);
        menu = new WeakReference<View>(shell); menuFor = ivl; menuTyping = typingNow;
        applyProgress();
        mark(shell, ivl);
        shell.setAlpha(0f); shell.setScaleX(0.96f); shell.setScaleY(0.96f);
        shell.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(180).setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
        main.removeCallbacks(follow); main.postDelayed(follow, 100);
    }

    static final int TOP_G = Gravity.TOP | Gravity.CENTER_HORIZONTAL, BOTTOM_G = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;

    private static int imeHeight(Activity a) {
        if (editing() == null) return 0;
        try {
            android.view.WindowInsets in = a.getWindow().getDecorView().getRootWindowInsets();
            if (in != null) {
                int type = (Integer) Class.forName("android.view.WindowInsets$Type").getMethod("ime").invoke(null);
                Object insets = android.view.WindowInsets.class.getMethod("getInsets", int.class).invoke(in, type);
                return insets.getClass().getField("bottom").getInt(insets);
            }
        } catch (Throwable ignored) { }
        return 0;
    }

    private static int bottomMargin(Activity a) {
        View decor = a.getWindow().getDecorView();
        float dens = a.getResources().getDisplayMetrics().density;
        int ime = imeHeight(a);
        if (ime > 0) return ime + Math.round(8 * dens);
        return decor.getHeight() - toolbarTop(a) + Math.round(12 * dens);
    }

    private static int barCache = -1;
    private static long barReadAt, toolReadAt;
    private static int toolCache = -1;

    private static int toolbarTop(Activity a) {
        long now = android.os.SystemClock.uptimeMillis();
        if (toolCache > 0 && (fingerDown || grabbing || now - toolReadAt < 1000)) return toolCache;
        toolReadAt = now;
        toolCache = StudioToolbar.anchorTop(a);
        return toolCache;
    }
    private static volatile boolean fingerDown;
    private static int barBottom(Activity a) {
        long now = android.os.SystemClock.uptimeMillis();
        if (barCache > 0 && now - barReadAt < 2000) return barCache;
        barReadAt = now;
        int r = StudioToolbar.topBarBottom(a);
        if (r > 0 && r > barCache) barCache = r;
        return barCache > 0 ? barCache : StudioToolbar.topBarGuess(a);
    }

    private static void up(Activity a, FrameLayout.LayoutParams lp) {
        lp.gravity = TOP_G;
        lp.topMargin = barBottom(a) + Math.round(8 * a.getResources().getDisplayMetrics().density);
        lp.bottomMargin = 0;
    }

    private static RectF cutTarget; private static int cutSteady;

    private static void fitCut(Activity a, View menuView) {
        RectF t = space(a, menuView, Float.NaN);
        if (t == null) { cutSteady = 0; return; }
        if (cutTarget != null && Math.abs(cutTarget.bottom - t.bottom) < 1f && Math.abs(cutTarget.top - t.top) < 1f) cutSteady++;
        else { cutTarget = t; cutSteady = 0; }
        if (cutSteady >= 1) glideCut(t, 280, new android.view.animation.DecelerateInterpolator(1.6f));
    }

    private static RectF space(Activity a, View menuView, float menuTopAt) {
        View cv = ScreenshotEditor.canvasView.get();
        if (cv == null || menuView.getHeight() <= 0) return null;
        float dens = a.getResources().getDisplayMetrics().density;
        View decor = a.getWindow().getDecorView();
        int[] d = new int[2], m = new int[2], c = new int[2];
        decor.getLocationOnScreen(d); menuView.getLocationOnScreen(m); cv.getLocationOnScreen(c);
        float gap = 14 * dens;
        float barBottom = d[1] + barBottom(a), floor = d[1] + lowestTop(a);
        float menuTop = Float.isNaN(menuTopAt) ? m[1] : d[1] + menuTopAt, menuBottom = menuTop + menuView.getHeight();
        float above = menuTop - barBottom, below = floor - menuBottom;
        float top, bottom;
        if (above > below) { top = barBottom + gap; bottom = menuTop - gap; } else { top = menuBottom + gap; bottom = floor - gap; }
        RectF t = new RectF(16 * dens - c[0] + d[0], top - c[1], d[0] + decor.getWidth() - 16 * dens - c[0], bottom - c[1]);
        return t.height() < 60 * dens ? null : t;
    }

    private static android.animation.ValueAnimator cutAnim;
    private static RectF cutGoal;
    private static void glideCut(RectF space, long ms, android.animation.TimeInterpolator curve) {
        RectF e = CropKeep.cutRect();
        if (e == null || e.width() <= 0 || e.height() <= 0) return;
        float f = Math.min(space.width() / e.width(), space.height() / e.height());
        float w = e.width() * f, h = e.height() * f;
        final RectF to = new RectF(space.centerX() - w / 2f, space.centerY() - h / 2f, space.centerX() + w / 2f, space.centerY() + h / 2f);
        if (cutGoal != null && near(cutGoal, to) && (cutAnim != null && cutAnim.isRunning() || near(e, to))) return;
        if (cutAnim != null) cutAnim.cancel();
        cutGoal = to;
        if (near(e, to)) return;
        final RectF from = new RectF(e);
        cutAnim = android.animation.ValueAnimator.ofFloat(0f, 1f);
        cutAnim.setDuration(ms);
        cutAnim.setInterpolator(curve);
        cutAnim.addUpdateListener(new android.animation.ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(android.animation.ValueAnimator va) {
                float k = (Float) va.getAnimatedValue();
                CropKeep.fitCut(new RectF(from.left + (to.left - from.left) * k, from.top + (to.top - from.top) * k,
                        from.right + (to.right - from.right) * k, from.bottom + (to.bottom - from.bottom) * k));
            }
        });
        cutAnim.start();
    }
    private static boolean near(RectF x, RectF y) { return Math.abs(x.left - y.left) < 1f && Math.abs(x.top - y.top) < 1f && Math.abs(x.width() - y.width()) < 1f; }

    private static int lowestTop(Activity a) {
        int ime = imeHeight(a);
        return ime > 0 ? a.getWindow().getDecorView().getHeight() - ime : toolbarTop(a);
    }

    private static float userTop = Float.NaN;
    private static boolean grabbing;
    private static float grabRaw, grabTop, gLo, gHi;
    private static android.view.VelocityTracker grabVelocity;
    private static android.animation.ValueAnimator flingAnim;

    private static float minTop(Activity a) { return barBottom(a) + 8 * a.getResources().getDisplayMetrics().density; }
    private static float maxTop(Activity a, View v) { return lowestTop(a) - v.getHeight() - 8 * a.getResources().getDisplayMetrics().density; }
    private static float clampTop(Activity a, View v, float y) {
        float lo = minTop(a), hi = maxTop(a, v);
        return hi < lo ? lo : Math.max(lo, Math.min(hi, y));
    }

    private static View pill(final Activity a, final int ink) {
        final float dens = a.getResources().getDisplayMetrics().density;
        final View pill = new View(a) {
            final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override protected void onDraw(Canvas canvas) {
                p.setColor(ink);
                p.setAlpha(isPressed() ? 255 : 150);
                float w = (isPressed() ? 48 : 40) * dens, h = 5 * dens, cx = getWidth() / 2f, cy = getHeight() / 2f;
                canvas.drawRoundRect(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2, h / 2, h / 2, p);
            }
        };
        pill.setContentDescription("Move text menu");
        pill.setOnTouchListener(new View.OnTouchListener() {
            @Override public boolean onTouch(View v, MotionEvent e) {
                if (e.getActionMasked() == MotionEvent.ACTION_DOWN && Math.abs(e.getX() - v.getWidth() / 2f) > 110 * dens) return false;
                return grab(a, v, e);
            }
        });
        return pill;
    }

    private static void buzz(View v, int kind) {
        if (v == null) return;
        if (!v.performHapticFeedback(kind, android.view.HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING)) {
            View root = v.getRootView();
            if (root != null) root.performHapticFeedback(kind, android.view.HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
        }
    }

    private static boolean grab(Activity a, View pill, MotionEvent e) {
        View shell = menu.get();
        if (shell == null) return false;
        if (grabVelocity != null) { MotionEvent raw = MotionEvent.obtain(e); raw.setLocation(e.getRawX(), e.getRawY()); grabVelocity.addMovement(raw); raw.recycle(); }
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                int[] at = new int[2], d = new int[2];
                shell.getLocationOnScreen(at); a.getWindow().getDecorView().getLocationOnScreen(d);
                if (flingAnim != null) flingAnim.cancel();
                grabTop = at[1] - d[1]; grabRaw = e.getRawY(); grabbing = true;
                shell.setTranslationY(0);
                placeTop(a, shell, grabTop);
                gLo = minTop(a); gHi = maxTop(a, shell);
                if (grabVelocity != null) grabVelocity.recycle();
                grabVelocity = android.view.VelocityTracker.obtain();
                MotionEvent raw = MotionEvent.obtain(e); raw.setLocation(e.getRawX(), e.getRawY()); grabVelocity.addMovement(raw); raw.recycle();
                pill.setPressed(true); pill.invalidate();
                buzz(pill, android.view.HapticFeedbackConstants.LONG_PRESS);
                if (pill.getParent() != null) pill.getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                float want = grabTop + (e.getRawY() - grabRaw);
                float y = gHi < gLo ? gLo : Math.max(gLo, Math.min(gHi, want));
                userTop = y;
                shell.setTranslationY(y - grabTop);
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                pill.setPressed(false); pill.invalidate();
                float vy = 0;
                if (grabVelocity != null) { grabVelocity.computeCurrentVelocity(1000); vy = grabVelocity.getYVelocity(); grabVelocity.recycle(); grabVelocity = null; }
                int fling = android.view.ViewConfiguration.get(a).getScaledMinimumFlingVelocity();
                float now = Float.isNaN(userTop) ? grabTop : userTop;
                if (e.getActionMasked() == MotionEvent.ACTION_UP && Math.abs(vy) > Math.max(fling * 4, 500 * a.getResources().getDisplayMetrics().density)) flingMenu(a, shell, now, vy);
                else { shell.setTranslationY(0); placeTop(a, shell, now); }
                grabbing = false;
                return true;
            }
        }
        return true;
    }

    private static void flingMenu(final Activity a, final View shell, final float from, float vy) {
        final float dens = a.getResources().getDisplayMetrics().density;
        final float edge = vy < 0 ? minTop(a) : clampTop(a, shell, Float.MAX_VALUE);
        final float distance = Math.abs(edge - from);
        final float speed = Math.max(Math.abs(vy), 1400 * dens);
        final float travel = distance < 1 ? 0 : Math.max(90, Math.min(320, distance / speed * 1000f));
        final float amp = Math.min(30 * dens, Math.max(8 * dens, speed * 0.012f));
        final float bounce = 520;
        final float inward = vy < 0 ? 1 : -1;
        placeTop(a, shell, edge);
        shell.setTranslationY(from - edge);
        if (menuTyping && editing() != null && CropKeep.suspended) {
            RectF t = space(a, shell, edge);
            if (t != null) { cutTarget = t; cutSteady = 1; glideCut(t, (long) (travel + 260), new android.view.animation.DecelerateInterpolator(1.4f)); }
        }
        flingAnim = android.animation.ValueAnimator.ofFloat(0f, travel + bounce);
        flingAnim.setDuration((long) (travel + bounce));
        flingAnim.setInterpolator(new android.view.animation.LinearInterpolator());
        flingAnim.addUpdateListener(new android.animation.ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(android.animation.ValueAnimator va) {
                float t = (Float) va.getAnimatedValue(), y;
                if (t < travel) {
                    float f = t / travel;
                    y = from + (edge - from) * (f * (1.15f - 0.15f * f));
                } else {
                    float b = (t - travel) / bounce;
                    float arc = (float) Math.abs(Math.sin(Math.PI * 2.2 * Math.pow(b, 0.8)));
                    y = edge + inward * amp * arc * (float) Math.pow(1 - b, 2.2);
                }
                shell.setTranslationY(y - edge);
            }
        });
        flingAnim.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator an) { shell.setTranslationY(0); }
        });
        flingAnim.start();
        main.postDelayed(new Runnable() { @Override public void run() { if (flingAnim != null && flingAnim.isRunning()) buzz(shell, android.view.HapticFeedbackConstants.CLOCK_TICK); } }, (long) travel);
    }

    private static void placeTop(Activity a, View shell, float top) {
        if (a == null || shell == null) return;
        userTop = top;
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) shell.getLayoutParams();
        lp.gravity = TOP_G; lp.bottomMargin = 0; lp.topMargin = Math.round(top);
        shell.setLayoutParams(lp);
    }

    private static final Runnable follow = new Runnable() {
        @Override public void run() {
            View v = menu.get();
            Activity a = ScreenshotEditor.activity();
            if (v == null || a == null || !v.isAttachedToWindow()) return;
            try {
                if (DoodleOverlay.active()) { hideMenu(); return; }
                if (menuFor != null && !fingerDown) syncFaceAndLayout(menuFor);
                FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) v.getLayoutParams();
                if (grabbing || fingerDown || (flingAnim != null && flingAnim.isRunning())) {  }
                else if (!Float.isNaN(userTop)) {
                    int t = Math.round(clampTop(a, v, userTop));
                    if (lp.gravity != TOP_G || lp.bottomMargin != 0 || Math.abs(lp.topMargin - t) > 1) { lp.gravity = TOP_G; lp.bottomMargin = 0; lp.topMargin = t; v.setLayoutParams(lp); }
                    else if (menuTyping && editing() != null && CropKeep.suspended && !grabbing) fitCut(a, v);
                } else if (menuTyping && editing() != null && CropKeep.suspended) {
                    int top = lp.topMargin, grav = lp.gravity, bot = lp.bottomMargin;
                    up(a, lp);
                    if (grav != lp.gravity || bot != lp.bottomMargin || Math.abs(lp.topMargin - top) > 1) v.setLayoutParams(lp);
                    else fitCut(a, v);
                } else {
                    int b = bottomMargin(a);
                    if (lp.gravity != BOTTOM_G || lp.topMargin != 0) { lp.gravity = BOTTOM_G; lp.topMargin = 0; lp.bottomMargin = b; v.setLayoutParams(lp); }
                    else if (Math.abs(lp.bottomMargin - b) > 1) { lp.bottomMargin = b; v.setLayoutParams(lp); }
                }
            } catch (Throwable ignored) { }
            main.postDelayed(this, 100);
        }
    };

    private static void mark(View v, Object ivl) {
        try {
            Object style = ivlStyle.get(ivl);
            Object font = ivmFont.get(style), colour = ivmColour.get(style);
            markAll(v, font, colour);
            boolean hiddenFont = false, hiddenColour = false;
            for (View x : extraFonts) if (x.getTag() != null && (x.getTag() == font || x.getTag().equals(font))) hiddenFont = true;
            for (View x : extraColours) if (x.getTag() != null && (x.getTag() == colour || x.getTag().equals(colour))) hiddenColour = true;
            if (moreFonts instanceof MoreFonts) { ((MoreFonts) moreFonts).ring = hiddenFont; moreFonts.invalidate(); }
            if (moreColours != null && moreColours.getBackground() instanceof DoodleOverlay.RainbowIcon) {
                ((DoodleOverlay.RainbowIcon) moreColours.getBackground()).ring = hiddenColour ? StudioToolbar.ringColour() : 0;
                moreColours.invalidate();
            }
        } catch (Throwable ignored) { }
    }
    private static void markAll(View v, Object font, Object colour) {
        if (v instanceof Dot) { ((Dot) v).chosen = v.getTag() == colour || (v.getTag() != null && v.getTag().equals(colour)); v.invalidate(); }
        else if (v instanceof TextView && v.getTag() != null) {
            boolean chosen = v.getTag() == font || v.getTag().equals(font);
            GradientDrawable g = new GradientDrawable(); g.setCornerRadius(12 * v.getResources().getDisplayMetrics().density);
            g.setColor(chosen ? (StudioToolbar.ringColour() & 0x00ffffff) | 0x26000000 : 0);
            v.setBackground(g);
        }
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) markAll(((ViewGroup) v).getChildAt(i), font, colour);
    }

    private static void style(Object colour, Object font) {
        try {
            Object ivl = editing();
            boolean typingNow = ivl != null;
            if (ivl == null) ivl = selected();
            if (!cIvl.isInstance(ivl)) return;
            Object old = ivlStyle.get(ivl);
            Object next = newIvm.newInstance(colour != null ? colour : ivmColour.get(old), font != null ? font : ivmFont.get(old));
            if (typingNow) { ivlSetStyle.invoke(ivl, next); ivlLayout.invoke(ivl); }
            else {
                Object step = newIxk.newInstance(ivl, null, next);
                ixkApply.invoke(step);
                Object h = jhu.get();
                if (h != null && undoPush != null) undoPush.invoke(loaUndo.get(jhuK.get(h)), step);
            }
            View v = menu.get();
            if (v != null) mark(v, ivl);
            redraw();
        } catch (Throwable t) { }
    }

    static final class Dot extends View {
        final int colour, ring; boolean chosen;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        Dot(Context c, int colour, int ring) { super(c); this.colour = colour; this.ring = ring; }
        @Override protected void onDraw(Canvas c) {
            float d = getResources().getDisplayMetrics().density, cx = getWidth() / 2f, cy = getHeight() / 2f;
            float r = Math.min(16 * d, Math.min(getWidth(), getHeight()) / 2f);
            if (r <= 2 * d) return;
            p.setStyle(Paint.Style.FILL); p.setColor(colour);
            c.drawCircle(cx, cy, chosen ? r - 4 * d : r - 1 * d, p);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1 * d); p.setColor((ring & 0x00ffffff) | 0x40000000);
            c.drawCircle(cx, cy, chosen ? r - 4 * d : r - 1 * d, p);
            if (chosen) { p.setStrokeWidth(2 * d); p.setColor(ring); c.drawCircle(cx, cy, r - 1 * d, p); }
        }
    }
}
