package io.github.pixelsuite;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

final class HomeOptions {
    static final String LABEL = "Pixel Suite settings";
    static final String HIDE_APPS_KEY = "pixelsuite_hide_apps_list";
    static final String PACKAGE = "io.github.roviandev.pixelsuite";
    static final String ACTIVITY = "io.github.pixelsuite.SettingsActivity";

    private HomeOptions() {}

    static volatile float downX = -1, downY = -1;

    static void follow(final View popup) {
        if (popup == null || downY < 0) return;
        boolean first = Xp.getAdditionalInstanceField(popup, "pixelsuite_follow") == null;
        Xp.setAdditionalInstanceField(popup, "pixelsuite_follow", downY);
        if (first) popup.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            public void onLayoutChange(View v, int l, int t, int r, int b, int ol, int ot, int or, int ob) { place(v); }
        });
        popup.post(new Runnable() { public void run() { place(popup); } });
    }

    private static void place(View v) {
        Object f = Xp.getAdditionalInstanceField(v, "pixelsuite_follow"); if (!(f instanceof Float)) return;
        float touchY = (Float) f;
        int h = v.getHeight(); if (h <= 0) return;
        int[] at = new int[2]; v.getLocationOnScreen(at);
        float baseTop = at[1] - v.getTranslationY();
        int screen = v.getResources().getDisplayMetrics().heightPixels, margin = FrostedSurface.dp(v.getContext(), 16), top = margin, bottom = margin;
        try { android.view.WindowInsets in = (android.view.WindowInsets)LauncherFeatures.call(v, "getRootWindowInsets"); if (in != null) { top += in.getStableInsetTop(); bottom += in.getStableInsetBottom(); } } catch (Throwable ignored) { }
        float want = Math.max(top, Math.min(touchY - h / 2f, screen - bottom - h));
        v.setTranslationY(want - baseTop);
        try { Object arrow = LauncherFeatures.get(v, "mArrow"); if (arrow instanceof View) ((View) arrow).setVisibility(View.INVISIBLE); } catch (Throwable ignored) { }
    }

    static void install() {
        try {
            Xp.hookAllMethods(LauncherFeatures.type("views.BaseDragLayer"), "onInterceptTouchEvent", new Xp.Callback() {
                protected void beforeHookedMethod(Xp.Param p) {
                    if (p.args.length > 0 && p.args[0] instanceof android.view.MotionEvent) {
                        android.view.MotionEvent e = (android.view.MotionEvent) p.args[0];
                        if (e.getActionMasked() == android.view.MotionEvent.ACTION_DOWN) { downX = e.getRawX(); downY = e.getRawY(); }
                    }
                }
            });
        } catch (Throwable error) { }
        try {
            final Class<?> options = LauncherFeatures.type("views.OptionsPopupView");
            Xp.hookAllMethods(LauncherFeatures.type("popup.ArrowPopup"), "onLayout", new Xp.Callback() {
                protected void afterHookedMethod(Xp.Param p) { if (options.isInstance(p.thisObject) && Xp.getAdditionalInstanceField(p.thisObject, "pixelsuite_follow") == null) follow((View) p.thisObject); }
            });
        } catch (Throwable error) { }
        Class<?> popup;
        try { popup = LauncherFeatures.type("views.OptionsPopupView"); }
        catch (RuntimeException composeMenu) { installComposeOptions(); installNexusBuilder(); return; }
        int hooks = 0;
        for (Method m : popup.getDeclaredMethods()) {
            if (!m.getName().equals("show") || !Modifier.isStatic(m.getModifiers())) continue;
            Class<?>[] types = m.getParameterTypes();
            int list = -1;
            for (int i = 0; i < types.length; i++) if (types[i] == List.class || types[i] == ArrayList.class) list = i;
            if (list < 0) continue;
            final int at = list;
            Xp.hookMethod(m, new Xp.Callback() {
                protected void beforeHookedMethod(Xp.Param p) {
                    try { p.args[at] = withSettings((List<?>) p.args[at]); }
                    catch (Throwable error) { }
                }
            });
            hooks++;
        }
        if (hooks == 0) installNexusBuilder();
    }

    static void installComposeOptions() {
        try { Xp.deoptimizeAll(LauncherFeatures.type("touch.WorkspaceTouchListener"), "onLongPress"); } catch (Throwable missing) { }
        try { Xp.deoptimizeAll(LauncherFeatures.type("Launcher"), "onKeyUp"); } catch (Throwable missing) { }
        try {
            Xp.hookAllMethods(LauncherFeatures.type("popup.WorkspaceLongPressOptions"), "getAll", new Xp.Callback() {
                protected void afterHookedMethod(Xp.Param p) {
                    try {
                        Object got = p.getResult();
                        if (!(got instanceof List)) return;
                        List<?> before = (List<?>) got, after = withSettings(before);
                        if (!sStockNoted) {
                            sStockNoted = true;
                            StringBuilder names = new StringBuilder();
                            for (Object item : before) names.append(item == null ? "-" : label(item)).append(" | ");
                        }
                        if (after == before) { return; }
                        if (got.getClass().isInstance(after)) { p.setResult(after); return; }
                        Object made;
                        try { made = got.getClass().getDeclaredConstructor().newInstance(); }
                        catch (NoSuchMethodException sized) { Constructor<?> k = got.getClass().getDeclaredConstructor(int.class); k.setAccessible(true); made = k.newInstance(after.size()); }
                        for (Object item : after) ((List<Object>) made).add(item);
                        try { Method build = made.getClass().getMethod("build"); made = build.invoke(made); } catch (NoSuchMethodException none) { }
                        if (got.getClass().isInstance(made)) p.setResult(made);
                    } catch (Throwable error) { }
                }
            });
        } catch (Throwable error) { }
    }

    private static boolean sStockNoted;
    private static java.lang.reflect.Field sPopupField;
    static void installNexusBuilder() {
        try {
            Class<?> popupType=Xp.findClass("com.google.android.apps.nexuslauncher.customize.OptionsPopupDialog$PopupView",LauncherFeatures.loader);
            Class<?> builder=Xp.findClass("c7.m",LauncherFeatures.loader);
            java.lang.reflect.Field popup=null;
            for(java.lang.reflect.Field f:builder.getDeclaredFields())if(f.getType()==popupType){f.setAccessible(true);popup=f;break;}
            Method build=builder.getDeclaredMethod("c");
            if(popup==null||build.getReturnType()!=void.class)return;
            sPopupField=popup;
            Xp.hookMethod(build,new Xp.Callback(){protected void afterHookedMethod(Xp.Param p){
                try{appendNativeRow(p.thisObject);}catch(Throwable error){}
            }});
        }catch(Throwable error){}
    }
    static void appendNativeRow(final Object builder) throws Exception {
        if(sPopupField==null)return;
        android.view.ViewGroup popup=(android.view.ViewGroup)sPopupField.get(builder);
        if(popup!=null)removeAppsList(popup);
        if(popup!=null)follow(popup);
        if(popup==null||popup.findViewWithTag("pixelsuite_settings")!=null)return;
        Context context=popup.getContext();int homeId=context.getResources().getIdentifier("settings_button_text","string",context.getPackageName());
        if(homeId==0)return;CharSequence home=context.getText(homeId);View sample=null;android.widget.TextView sampleLabel=null;int at=-1;
        for(int i=0;i<popup.getChildCount();i++){
            View child=popup.getChildAt(i);Object label;
            try{label=LauncherFeatures.get(child,"mBubbleText");}catch(Throwable ignored){continue;}
            if(label instanceof android.widget.TextView&&home.toString().contentEquals(((android.widget.TextView)label).getText())){sample=child;sampleLabel=(android.widget.TextView)label;at=i;break;}
        }
        if(sample==null)return;
        int layout=context.getResources().getIdentifier("wallpaper_options_popup_item","layout",context.getPackageName());if(layout==0)return;
        final Object option=newOption();
        LauncherFeatures.set(option,"label",LABEL);final int menuInk=sampleLabel.getCurrentTextColor();LauncherFeatures.set(option,"icon",SuiteIcon.glyph(menuInk));
        try{LauncherFeatures.set(option,"eventId",LauncherFeatures.type("logging.StatsLogManager$LauncherEvent").getField("IGNORE").get(null));}catch(Exception error){throw new IllegalStateException(error);}
        LauncherFeatures.set(option,"clickListener",new View.OnLongClickListener(){public boolean onLongClick(View view){open(view);return true;}});
        View row=android.view.LayoutInflater.from(context).inflate(layout,popup,false);row.setTag("pixelsuite_settings");
        android.widget.TextView text=(android.widget.TextView)LauncherFeatures.get(row,"mBubbleText");text.setText(I18n.cs(LABEL));MenuFont.copyInto(text,sampleLabel);
        View icon=(View)LauncherFeatures.get(row,"mIconView");icon.setBackground(SuiteIcon.glyph(menuInk));
        row.setBackgroundTintList(sample.getBackgroundTintList());
        try{Object mode=LauncherFeatures.call(sample,"getBackgroundTintBlendMode");LauncherFeatures.call(row,"setBackgroundTintBlendMode",mode);}catch(Throwable ignored){}
        row.getLayoutParams().width=sample.getLayoutParams().width;
        row.setOnClickListener(new View.OnClickListener(){public void onClick(View view){LauncherFeatures.call(builder,"d",view,option);}});
        row.setOnLongClickListener(new View.OnLongClickListener(){public boolean onLongClick(View view){return Boolean.TRUE.equals(LauncherFeatures.call(builder,"d",view,option));}});
        popup.addView(row,at+1);
    }
    static CharSequence appsListLabel(Context c){
        try{int id=c.getResources().getIdentifier("all_apps_button_label","string",c.getPackageName());if(id!=0)return c.getText(id);}catch(Throwable ignored){}
        return "Apps list";
    }
    static void removeAppsList(android.view.ViewGroup popup){
        if(!Feature.on(popup.getContext(),HIDE_APPS_KEY))return;
        CharSequence apps=appsListLabel(popup.getContext());
        for(int i=popup.getChildCount()-1;i>=0;i--){
            View child=popup.getChildAt(i);Object label;
            try{label=LauncherFeatures.get(child,"mBubbleText");}catch(Throwable ignored){continue;}
            if(!(label instanceof android.widget.TextView))continue;
            CharSequence text=((android.widget.TextView)label).getText();
            if(text==null||!(apps.toString().contentEquals(text)||"Apps list".contentEquals(text)))continue;
            popup.removeViewAt(i);
            try{LauncherFeatures.call(popup,"assignMarginsAndBackgrounds",popup);}catch(Throwable noReshape){}
        }
    }

    static Object newOption(){
        Class<?> type=LauncherFeatures.type("views.OptionsPopupView$OptionItem");
        try{return Xp.newInstance(type);}catch(Throwable constructorRemoved){
            try{Class<?> unsafe=Class.forName("sun.misc.Unsafe");Field field=unsafe.getDeclaredField("theUnsafe");field.setAccessible(true);return unsafe.getMethod("allocateInstance",Class.class).invoke(field.get(null),type);}
            catch(Exception error){throw new IllegalStateException("Cannot allocate native menu item",error);}
        }
    }

    static List<?> withSettings(List<?> items) {
        Activity a = LauncherFeatures.activity.get();
        if (a == null || items == null || items.isEmpty()) return items;
        boolean homeMenu=false; for(Object item:items)if(item!=null&&isHomeSettings(item,a)){homeMenu=true;break;}
        if(!homeMenu)return items;
        if (Feature.on(a, HIDE_APPS_KEY)) {
            CharSequence apps = appsListLabel(a);
            ArrayList<Object> kept = new ArrayList<Object>();
            for (Object item : items) { CharSequence l = item == null ? "" : label(item); if (!(apps.toString().contentEquals(l) || "Apps list".contentEquals(l))) kept.add(item); }
            if (kept.size() != items.size()) { items = kept; }
        }
        int home = -1;
        for (int i = 0; i < items.size(); i++) {
            Object item = items.get(i);
            if (item == null) continue;
            if (LABEL.contentEquals(label(item))) return items;
            if (isHomeSettings(item, a)) home = i;
        }
        if (home < 0) return items;
        Object ours = create(items.get(home), a);
        if (ours == null) ours = popupData(items.get(home), a);
        if (ours == null) return items;
        ArrayList<Object> copy = new ArrayList<Object>(items);
        copy.add(home + 1, ours);
        return copy;
    }

    static boolean isHomeSettings(Object item, Context c) {
        Object res = field(item, null, "labelRes", "mLabelRes");
        if (res instanceof Integer && ((Integer) res) != 0) {
            try { if (c.getResources().getResourceEntryName((Integer) res).equals("settings_button_text")) return true; }
            catch (Throwable ignored) { }
        }
        int id = c.getResources().getIdentifier("settings_button_text", "string", c.getPackageName());
        if (id != 0) {
            try { if (c.getString(id).contentEquals(label(item))) return true; } catch (Throwable ignored) { }
        }
        Object event = field(item, null, "eventId", "mEventId");
        return event != null && String.valueOf(event).contains("SETTINGS_BUTTON");
    }

    static CharSequence label(Object item) {
        Object label = field(item, CharSequence.class, "label", "mLabel");
        if (label instanceof CharSequence) return (CharSequence) label;
        if (label == null) return "";
        try {
            for (Class<?> k = label.getClass(); k != null && k != Object.class; k = k.getSuperclass())
                for (Field f : k.getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers())) continue;
                    f.setAccessible(true);
                    Object v = f.get(label);
                    if (v instanceof CharSequence) return (CharSequence) v;
                    if (f.getType() == int.class && (Integer) v != 0) {
                        Activity a = LauncherFeatures.activity.get();
                        if (a != null) return a.getString((Integer) v);
                    }
                }
        } catch (Throwable ignored) { }
        return "";
    }

    static int menuInk(Context c) {
        boolean night = (c.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        return night ? 0xFFFFFFFF : 0xFF1F1F1F;
    }

    static Object create(Object settings, final Context c) {
        for (Constructor<?> k : settings.getClass().getDeclaredConstructors()) {
            Class<?>[] t = k.getParameterTypes();
            if (t.length != 4 || t[0] != CharSequence.class || t[1] != Drawable.class || t[3] != View.OnLongClickListener.class) continue;
            Object event = field(settings, t[2], "eventId", "mEventId");
            if (event == null) return null;
            try {
                k.setAccessible(true);
                return k.newInstance(LABEL, SuiteIcon.glyph(menuInk(c)), event, new View.OnLongClickListener() {
                    public boolean onLongClick(View v) { open(v); return true; }
                });
            } catch (Throwable error) { return null; }
        }
        return null;
    }

    static Object popupData(Object settings, final Context c) {
        try {
            if (!settings.getClass().getName().endsWith(".PopupData")) return null;
            Class<?> u = Class.forName("sun.misc.Unsafe"); Field uf;
            try { uf = u.getDeclaredField("theUnsafe"); } catch (NoSuchFieldException e) { uf = u.getDeclaredField("THE_ONE"); }
            uf.setAccessible(true);
            Object out = u.getMethod("allocateInstance", Class.class).invoke(uf.get(null), settings.getClass());
            for (Class<?> k = settings.getClass(); k != null && k != Object.class; k = k.getSuperclass())
                for (Field f : k.getDeclaredFields()) { if (Modifier.isStatic(f.getModifiers())) continue; f.setAccessible(true); f.set(out, f.get(settings)); }
            ClassLoader loader = settings.getClass().getClassLoader();
            Field label = settings.getClass().getDeclaredField("label"); label.setAccessible(true);
            Class<?> fixed = Class.forName(label.getType().getName() + "$FixedString", false, loader);
            Object text = null;
            for (Constructor<?> k : fixed.getDeclaredConstructors()) {
                Class<?>[] t = k.getParameterTypes();
                if (t.length == 1 && t[0].isAssignableFrom(String.class)) { k.setAccessible(true); text = k.newInstance(I18n.cs(LABEL).toString()); break; }
            }
            if (text == null) { return null; }
            label.set(out, text);
            Field action = settings.getClass().getDeclaredField("popupAction"); action.setAccessible(true);
            Class<?> fn = action.getType();
            if (!fn.isInterface()) { return null; }
            Object done = null;
            try { done = Class.forName("kotlin.Unit", false, loader).getField("INSTANCE").get(null); } catch (Throwable renamed) { }
            if (done == null) try {
                Object v = Class.forName("bd.i", false, loader).getField("a").get(null);
                if (v != null && "kotlin.Unit".equals(String.valueOf(v))) done = v;
            } catch (Throwable renamed) { }
            final Object unit = done;
            action.set(out, java.lang.reflect.Proxy.newProxyInstance(loader, new Class<?>[]{fn}, (self, m, args) -> {
                if (m.getDeclaringClass() == Object.class) {
                    if ("equals".equals(m.getName())) return self == args[0];
                    if ("hashCode".equals(m.getName())) return System.identityHashCode(self);
                    return "pixelsuite";
                }
                Context from = c;
                if (args != null) for (Object x : args) if (x instanceof Context) { from = (Context) x; break; }
                openFrom(from);
                return unit;
            }));
            try { Field id = settings.getClass().getDeclaredField("id"); id.setAccessible(true); id.setInt(out, 0x50535549); } catch (NoSuchFieldException ignored) { }
            try {
                Field icon = settings.getClass().getDeclaredField("iconResId"); icon.setAccessible(true);
                int glyph = glyphRes(c);
                if (glyph != 0) icon.setInt(out, glyph);
                else for (String name : new String[]{"ic_palette", "ic_customize", "ic_style", "ic_wallpaper"}) {
                    int res = c.getResources().getIdentifier(name, "drawable", c.getPackageName());
                    if (res != 0) { icon.setInt(out, res); break; }
                }
            } catch (NoSuchFieldException ignored) { }
            return out;
        } catch (Throwable error) { return null; }
    }

    private static volatile int sGlyphRes, sSuiteGlyph;
    private static volatile android.content.res.Resources sSuiteRes;
    private static boolean sGlyphTried;

    static synchronized int glyphRes(Context c) {
        if (sGlyphTried) return sGlyphRes;
        sGlyphTried = true;
        try {
            android.content.res.Resources suite = c.createPackageContext(PACKAGE, Context.CONTEXT_IGNORE_SECURITY).getResources();
            int own = suite.getIdentifier("ic_ps_menu_glyph", "drawable", PACKAGE);
            if (own == 0) { return 0; }
            int host = 0;
            android.content.res.Resources sys = android.content.res.Resources.getSystem();
            android.util.TypedValue v = new android.util.TypedValue();
            for (String name : new String[]{"ic_print_error", "ic_print", "ic_doc_generic", "ic_doc_folder"}) {
                int id = sys.getIdentifier(name, "drawable", "android");
                if (id == 0) continue;
                try { c.getResources().getValue(id, v, true); } catch (Throwable missing) { continue; }
                if (v.string != null && v.string.toString().endsWith(".xml")) { host = id; break; }
            }
            if (host == 0) { return 0; }
            if (!readable(c, suite, own)) return 0;
            sSuiteRes = suite; sSuiteGlyph = own;
            final int target = host;
            Xp.hookAllMethods(android.content.res.Resources.class, "getXml", new Xp.Callback() {
                protected void beforeHookedMethod(Xp.Param p) {
                    try { if (p.args.length == 1 && Integer.valueOf(target).equals(p.args[0])) p.setResult(sSuiteRes.getXml(sSuiteGlyph)); } catch (Throwable ignored) { }
                }
            });
            Xp.Callback drawable = new Xp.Callback() {
                protected void beforeHookedMethod(Xp.Param p) {
                    try { if (p.args.length >= 1 && Integer.valueOf(target).equals(p.args[0])) p.setResult(SuiteIcon.glyph(menuInk(((android.content.res.Resources) p.thisObject).getConfiguration()))); } catch (Throwable ignored) { }
                }
            };
            Xp.hookAllMethods(android.content.res.Resources.class, "getDrawable", drawable);
            Xp.hookAllMethods(android.content.res.Resources.class, "getDrawableForDensity", drawable);
            sGlyphRes = host;
        } catch (Throwable error) { sGlyphRes = 0; }
        return sGlyphRes;
    }

    static boolean readable(Context c, android.content.res.Resources suite, int own) {
        android.content.res.XmlResourceParser x = null;
        try {
            x = suite.getXml(own);
            boolean vector = false, path = false;
            for (int t = x.next(); t != org.xmlpull.v1.XmlPullParser.END_DOCUMENT; t = x.next()) {
                if (t != org.xmlpull.v1.XmlPullParser.START_TAG) continue;
                android.util.AttributeSet set = android.util.Xml.asAttributeSet(x);
                if ("vector".equals(x.getName())) {
                    android.content.res.TypedArray a = c.getResources().obtainAttributes(set, new int[]{android.R.attr.viewportWidth});
                    vector = a.getFloat(0, -1f) == 24f; a.recycle();
                    android.content.res.TypedArray b = c.getTheme().obtainStyledAttributes(set, new int[]{android.R.attr.viewportWidth}, 0, 0);
                    vector = vector && b.getFloat(0, -1f) == 24f; b.recycle();
                } else if ("path".equals(x.getName()) && !path) {
                    android.content.res.TypedArray a = c.getResources().obtainAttributes(set, new int[]{android.R.attr.pathData});
                    String d = a.getString(0); a.recycle();
                    path = d != null && d.startsWith("M");
                }
            }
            return vector && path;
        } catch (Throwable error) { return false; }
        finally { if (x != null) try { x.close(); } catch (Throwable ignored) { } }
    }

    static int menuInk(android.content.res.Configuration config) {
        boolean night = (config.uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        return night ? 0xFFFFFFFF : 0xFF1F1F1F;
    }

    static void openFrom(Context c) {
        Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(new ComponentName(PACKAGE, ACTIVITY))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try { c.startActivity(intent); }
        catch (Throwable error) { Toast.makeText(c, I18n.cs("Pixel Suite isn't available"), Toast.LENGTH_SHORT).show(); }
    }

    static void open(View v) {
        Context c = v.getContext();
        Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(new ComponentName(PACKAGE, ACTIVITY))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        Bundle options = null;
        try { options = ActivityOptions.makeScaleUpAnimation(v, 0, 0, v.getWidth(), v.getHeight()).toBundle(); } catch (Throwable ignored) { }
        try { c.startActivity(intent, options); }
        catch (Throwable error) {
            Toast.makeText(c, I18n.cs("Pixel Suite isn't available"), Toast.LENGTH_SHORT).show();
        }
    }

    static Object field(Object o, Class<?> type, String... names) {
        for (String name : names) {
            for (Class<?> k = o.getClass(); k != null && k != Object.class; k = k.getSuperclass()) {
                try { Field f = k.getDeclaredField(name); f.setAccessible(true); return f.get(o); } catch (Throwable ignored) { }
            }
        }
        if (type == null) return null;
        for (Class<?> k = o.getClass(); k != null && k != Object.class; k = k.getSuperclass()) {
            for (Field f : k.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) || !type.isAssignableFrom(f.getType())) continue;
                try { f.setAccessible(true); return f.get(o); } catch (Throwable ignored) { }
            }
        }
        return null;
    }
}
