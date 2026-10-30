package io.github.pixelsuite;

import android.content.Context;
import android.content.res.Resources;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import java.util.ArrayList;
import java.util.List;

final class KeyboardBarTweak {
    private KeyboardBarTweak() {}
    static final String KEY = "pixelsuite_keyboard_bar";
    private static final String NAV = "android.inputmethodservice.navigationbar.";
    private static boolean installed;
    private static Boolean on;

    private static boolean on() {
        Boolean v = on; if (v != null) return v;
        boolean value = false;
        try {
            Context c = (Context) Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null);
            if (c != null) { value = "1".equals(Settings.Secure.getString(c.getContentResolver(), KEY)); on = value; }
        } catch (Throwable ignored) { }
        return value;
    }

    static synchronized void install(String pkg) {
        if (installed) return;
        installed = true;
        try {
            Class<?> inflater = Class.forName(NAV + "NavigationBarInflaterView");
            Xp.hookAllMethods(inflater, "inflateLayout", new Xp.Callback() {
                @Override protected void beforeHookedMethod(Xp.Param p) {
                    if (p.args.length > 0 && p.args[0] instanceof String && on()) p.args[0] = rewrite((String) p.args[0]);
                }
                @Override protected void afterHookedMethod(Xp.Param p) {
                    if (p.thisObject instanceof View && on()) keepLeftToRight((View) p.thisObject);
                }
            });
            Xp.hookAllMethods(inflater, "getDefaultLayout", new Xp.Callback() {
                @Override protected void afterHookedMethod(Xp.Param p) {
                    if (p.getResult() instanceof String && on()) p.setResult(rewrite((String) p.getResult()));
                }
            });
            Xp.deoptimizeAll(inflater, "inflateLayout");
            Xp.deoptimizeAll(inflater, "onFinishInflate");
        } catch (Throwable error) { }
        try {
            Xp.hookAllMethods(Class.forName(NAV + "NavigationBarView"), "onFinishInflate", new Xp.Callback() {
                @Override protected void afterHookedMethod(Xp.Param p) {
                    try { if (p.thisObject instanceof View){View bar=(View)p.thisObject;if(on())fixViews(bar);installNavArrow(bar);} } catch (Throwable error) { }
                }
            });
        } catch (Throwable error) { }
    }

    private static void installNavArrow(View bar){
        if(Xp.getAdditionalInstanceField(bar,"pixelsuite_nav_arrow")!=null)return;
        View back=find(bar,id(bar.getResources(),"input_method_nav_back"));if(back==null)return;
        ViewGroup ends=group(bar,id(bar.getResources(),"input_method_nav_ends_group"));
        View item=ends==null?back:childOf(ends,back);if(item==null)item=back;
        ArrowPlacement placement=new ArrowPlacement(bar,back,item);Xp.setAdditionalInstanceField(bar,"pixelsuite_nav_arrow",placement);bar.addOnAttachStateChangeListener(placement);if(bar.isAttachedToWindow())placement.onViewAttachedToWindow(bar);
    }
    private static final class ArrowPlacement implements android.view.ViewTreeObserver.OnPreDrawListener,View.OnAttachStateChangeListener {
        final View bar,back,item;final float original;final int[] at=new int[2],rootAt=new int[2];boolean nav,attached;
        final android.database.ContentObserver observer=new android.database.ContentObserver(new android.os.Handler(android.os.Looper.getMainLooper())){public void onChange(boolean self){read();}};
        ArrowPlacement(View b,View arrow,View moving){bar=b;back=arrow;item=moving;original=item.getTranslationX();}
        void read(){try{nav="1".equals(Settings.Secure.getString(bar.getContext().getContentResolver(),NavButtons.KEY));}catch(Throwable ignored){nav=false;}if(!nav)item.setTranslationX(original);bar.invalidate();}
        public void onViewAttachedToWindow(View v){if(attached)return;attached=true;read();bar.getContext().getContentResolver().registerContentObserver(Settings.Secure.getUriFor(NavButtons.KEY),false,observer);bar.getViewTreeObserver().addOnPreDrawListener(this);}
        public void onViewDetachedFromWindow(View v){if(!attached)return;attached=false;bar.getContext().getContentResolver().unregisterContentObserver(observer);if(bar.getViewTreeObserver().isAlive())bar.getViewTreeObserver().removeOnPreDrawListener(this);item.setTranslationX(original);}
        public boolean onPreDraw(){if(nav&&back.isShown()){View root=bar.getRootView();back.getLocationOnScreen(at);root.getLocationOnScreen(rootAt);float target=rootAt[0]+root.getWidth()-36*bar.getResources().getDisplayMetrics().density;float delta=target-(at[0]+back.getWidth()/2f);if(Math.abs(delta)>.5f)item.setTranslationX(item.getTranslationX()+delta);}return true;}
    }

    static String rewrite(String spec) {
        if (spec == null) return null;
        String[] groups = spec.split(";", -1);
        if (groups.length != 3) return spec;
        String back = null;
        List<List<String>> kept = new ArrayList<List<String>>();
        for (String group : groups) {
            List<String> out = new ArrayList<String>();
            for (String token : group.split(",", -1)) {
                String name = token.trim(); int size = name.indexOf('['); if (size >= 0) name = name.substring(0, size).trim();
                if (name.isEmpty() || "ime_switcher".equals(name)) continue;
                if ("back".equals(name)) { if (back == null) back = token.trim(); continue; }
                out.add(token.trim());
            }
            kept.add(out);
        }
        if (back != null) kept.get(2).add(back);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 3; i++) {
            if (i > 0) sb.append(';');
            List<String> g = kept.get(i);
            for (int j = 0; j < g.size(); j++) { if (j > 0) sb.append(','); sb.append(g.get(j)); }
        }
        return sb.toString();
    }

    private static void fixViews(View bar) {
        Resources res = bar.getResources();
        ViewGroup ends = group(bar, id(res, "input_method_nav_ends_group"));
        if (ends == null) return;
        ViewGroup center = group(bar, id(res, "input_method_nav_center_group"));
        View switcher = find(bar, id(res, "input_method_nav_ime_switcher"));
        if (switcher != null) { if (!removeFrom(ends, switcher) && !removeFrom(center, switcher)) switcher.setVisibility(View.GONE); }
        View back = find(bar, id(res, "input_method_nav_back"));
        if (back != null) {
            View item = childOf(ends, back);
            if (item == null && center != null) {
                View fromCenter = childOf(center, back);
                if (fromCenter != null) { center.removeView(fromCenter); ends.addView(fromCenter); }
            } else if (item != null && ends.indexOfChild(item) != ends.getChildCount() - 1) { ends.removeView(item); ends.addView(item); }
        }
        keepLeftToRight(bar);
    }

    private static void keepLeftToRight(View root) {
        ViewGroup ends = group(root, id(root.getResources(), "input_method_nav_ends_group"));
        if (ends != null) ends.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
    }

    private static int id(Resources res, String name) { return res.getIdentifier(name, "id", "android"); }
    private static View find(View root, int id) { return id == 0 ? null : root.findViewById(id); }
    private static ViewGroup group(View root, int id) { View v = find(root, id); return v instanceof ViewGroup ? (ViewGroup) v : null; }
    private static View childOf(ViewGroup group, View view) {
        if (group == null || view == null) return null;
        View current = view; ViewParent parent = current.getParent();
        while (parent instanceof View) { if (parent == group) return current; current = (View) parent; parent = current.getParent(); }
        return null;
    }
    private static boolean removeFrom(ViewGroup group, View view) { View item = childOf(group, view); if (item == null) return false; group.removeView(item); return true; }
}
