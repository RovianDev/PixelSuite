package io.github.pixelsuite;

import android.content.Context;
import android.content.res.Resources;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class ComposeMenu {
    private ComposeMenu() {}

    static volatile boolean active;

    private static final int BASE_ID = 0x7ffe0100;
    private static final Map<Integer, String> labels = new java.util.concurrent.ConcurrentHashMap<Integer, String>();
    private static Constructor<?> newItem, newString;
    private static Object fixedCategory;
    private static Class<?> actionType;

    static void install() {
        try {
            Class<?> container = LauncherFeatures.type("popup.PopupContainer");
            Method show = null;
            for (Method m : container.getDeclaredMethods())
                if (m.getName().equals("showComposePopup") && m.getParameterTypes().length == 2 && m.getParameterTypes()[0] == int.class && List.class.isAssignableFrom(m.getParameterTypes()[1])) show = m;
            if (show == null) return;
            Class<?> item = LauncherFeatures.type("popup.ui.PopupItem");
            for (Constructor<?> c : item.getDeclaredConstructors()) if (c.getParameterTypes().length == 4 && c.getParameterTypes()[0] == int.class) newItem = c;
            if (newItem == null) throw new NoSuchMethodException("PopupItem(int, label, action, category)");
            newItem.setAccessible(true);
            actionType = newItem.getParameterTypes()[2];
            newString = LauncherFeatures.type("popup.ui.StringContainer$ResourceString").getDeclaredConstructor(int.class);
            newString.setAccessible(true);
            Class<?> category = newItem.getParameterTypes()[3];
            for (Object c : category.getEnumConstants()) if (((Enum<?>) c).name().equals("SYSTEM_SHORTCUT_FIXED")) fixedCategory = c;
            if (fixedCategory == null) fixedCategory = category.getEnumConstants()[0];
            Xp.hookMethod(Resources.class.getDeclaredMethod("getText", int.class), new Xp.Callback() {
                @Override protected void beforeHookedMethod(Xp.Param p) {
                    String s = labels.get((Integer) p.args[0]);
                    if (s != null) p.setResult(s);
                }
            });
            Xp.hookMethod(show, new Xp.Callback() {
                @Override protected void beforeHookedMethod(Xp.Param p) {
                    try { add(p); } catch (Throwable error) { }
                }
                @Override protected void afterHookedMethod(Xp.Param p) {
                    if (Boolean.TRUE.equals(Xp.getAdditionalInstanceField(p.thisObject, "pixelsuite_home_folder"))) { try { homeFolder(p.thisObject); } catch (Throwable error) { } return; }
                    try { onePanel(p.thisObject); } catch (Throwable error) { }
                }
            });
            try {
                Xp.hookAllMethods(container, "closeComplete", new Xp.Callback() {
                    @Override protected void afterHookedMethod(Xp.Param p) {
                        if (Boolean.TRUE.equals(Xp.getAdditionalInstanceField(p.thisObject, "pixelsuite_home_folder"))) DrawerFolderMenu.closedWith(p.thisObject);
                    }
                });
            } catch (Throwable error) { }
            try {
                Xp.hookAllMethods(LauncherFeatures.type("DropTargetBar"), "onDragStart", new Xp.Callback() {
                    @Override protected void afterHookedMethod(Xp.Param p) { DrawerFolderMenu.dragStarted(); }
                });
            } catch (Throwable error) { }
            try { cards(); } catch (Throwable error) { }
            try {
                Class<?> arrow = LauncherFeatures.type("popup.PopupContainerWithArrow");
                for (Method m : arrow.getDeclaredMethods()) {
                    if (!m.getName().equals("loadAppShortcuts") || m.getParameterTypes().length != 1) continue;
                    Xp.hookMethod(m, new Xp.Callback() {
                        @Override protected void beforeHookedMethod(Xp.Param p) {
                            try { if (LauncherFeatures.hidesShortcuts(p.thisObject, p.args[0])) { p.setResult(null);  } } catch (Throwable ignored) { }
                        }
                    });
                }
            } catch (Throwable error) { }
            active = true;
        } catch (Throwable error) { }
    }

    private static void add(Xp.Param p) throws Exception {
        final Object popup = p.thisObject;
        Object info = LauncherFeatures.get(popup, "itemInfo");
        if (info == null) return;
        if (LauncherFeatures.hidesShortcuts(popup, info)) p.args[0] = 0;
        if (LauncherFeatures.type("model.data.FolderInfo").isInstance(info)) {
            if (DrawerFolders.isFolder(info) || LauncherFeatures.privateItem(info)) return;
            Xp.setAdditionalInstanceField(popup, "pixelsuite_home_folder", Boolean.TRUE);
            return;
        }
        Context context = ((View) popup).getContext();
        LinearLayout rows = new LinearLayout(context);
        rows.setOrientation(LinearLayout.VERTICAL);
        if (LauncherFeatures.searchWidget(popup)) {
            final View bar = (View) LauncherFeatures.get(popup, "originalView");
            LauncherFeatures.addFolderRow(rows, "Hide widget", "pixelsuite_hide_search", 17, 0, new View.OnClickListener() {
                public void onClick(View v) { try { LauncherFeatures.call(popup, "close", true); } catch (Throwable ignored) { } LauncherHideSearch.hideFromMenu(bar); }
            });
        } else if (LauncherFeatures.widgetItem(info)) {
            if (!SearchDock.widgetView(LauncherFeatures.get(popup, "originalView"))) return;
            final Context app = context.getApplicationContext();
            LauncherFeatures.addFolderRow(rows, "Customize", "pixelsuite_customize", 10, 0, new View.OnClickListener() {
                public void onClick(View v) { try { LauncherFeatures.call(popup, "close", true); } catch (Throwable ignored) { } SearchDock.customize(app); }
            });
        } else {
            if (LauncherFeatures.identity(info) == null || LauncherFeatures.privateItem(info)) return;
            LauncherFeatures.appRows(popup, info, rows);
        }
        if (rows.getChildCount() == 0) return;
        List<Object> items = new ArrayList<Object>((List<?>) p.args[1]);
        int fallbackIcon = items.isEmpty() ? 0 : ((Integer) field(items.get(0), "iconResId"));
        java.util.Set<Integer> used = new java.util.HashSet<Integer>();
        for (Object it : items) try { used.add((Integer) field(it, "iconResId")); } catch (Throwable ignored) { }
        int front = 0;
        for (int i = 0; i < rows.getChildCount(); i++) {
            final View row = rows.getChildAt(i);
            Object tag = row.getTag();
            if (!(tag instanceof String) || !((String) tag).startsWith("pixelsuite_")) continue;
            String title = text(row);
            if (title == null) continue;
            int id = BASE_ID + labelIndex(title);
            int icon = icon(context, (String) tag, fallbackIcon);
            if ("pixelsuite_force_stop".equals(tag) && used.contains(icon)) icon = icon(context, "pixelsuite_force_stop_alt", icon);
            used.add(icon);
            Object action = Proxy.newProxyInstance(actionType.getClassLoader(), new Class<?>[]{actionType}, new java.lang.reflect.InvocationHandler() {
                @Override public Object invoke(Object proxy, Method m, Object[] args) {
                    if (m.getDeclaringClass() == Object.class) {
                        if (m.getName().equals("equals")) return proxy == args[0];
                        if (m.getName().equals("hashCode")) return System.identityHashCode(proxy);
                        return "PixelSuiteMenuAction";
                    }
                    try { row.performClick(); } catch (Throwable error) { }
                    return unit();
                }
            });
            Object made = newItem.newInstance(icon, newString.newInstance(id), action, fixedCategory);
            if ("pixelsuite_badge".equals(tag) || "pixelsuite_customize".equals(tag) || "pixelsuite_force_stop".equals(tag)) items.add(front++, made); else items.add(made);
        }
        p.args[1] = items;
    }

    private static void homeFolder(final Object popup) throws Exception {
        final View box = (View) popup;
        box.setAlpha(0f);
        box.setVisibility(View.INVISIBLE);
        box.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        final View icon = (View) LauncherFeatures.get(popup, "originalView");
        if (icon == null) return;
        icon.post(new Runnable() { public void run() {
            try { if (box.isAttachedToWindow()) DrawerFolderMenu.showHome(icon, popup); } catch (Throwable ignored) { }
        } });
    }

    private static volatile boolean styled;
    private static final ThreadLocal<int[]> inCard = new ThreadLocal<int[]>();
    private static int cardColor = -1;

    private static void onePanel(Object popup) throws Exception {
        Object info = LauncherFeatures.get(popup, "itemInfo");
        styled = !LauncherFeatures.privateItem(info);
        if (!styled) return;
        Object vm = LauncherFeatures.get(popup, "viewModel");
        Object state = LauncherFeatures.get(vm, "state$delegate");
        Object ui = state.getClass().getMethod("getValue").invoke(state);
        if (ui == null) return;
        Object style = field(ui, "mainSegmentsStyle");
        if (!(style instanceof Enum) || !"ACCORDION".equals(((Enum<?>) style).name())) return;
        Object list = null;
        for (Object c : style.getClass().getEnumConstants()) if (((Enum<?>) c).name().equals("LIST")) list = c;
        if (list == null) return;
        Constructor<?> make = ui.getClass().getDeclaredConstructor(List.class, List.class, List.class, style.getClass());
        make.setAccessible(true);
        Object one = make.newInstance(field(ui, "compactSystemShortcuts"), field(ui, "standardSystemShortcuts"), field(ui, "deepShortcuts"), list);
        for (Method m : state.getClass().getMethods()) if (m.getName().equals("setValue") && m.getParameterTypes().length == 1) { m.invoke(state, one); break; }
    }

    private static void cards() throws Exception {
        ClassLoader cl = LauncherFeatures.type("popup.PopupContainer").getClassLoader();
        Class<?> kt = Class.forName("com.android.launcher3.popup.ui.ComposePopupKt", false, cl);
        Xp.hookAllMethods(kt, "ExpandableContainer", new Xp.Callback() {
            @Override protected void beforeHookedMethod(Xp.Param p) { int[] d = inCard.get(); if (d == null) inCard.set(d = new int[1]); d[0]++; }
            @Override protected void afterHookedMethod(Xp.Param p) { int[] d = inCard.get(); if (d != null && d[0] > 0) d[0]--; }
        });
        Xp.hookMethod(Resources.class.getMethod("getColor", int.class, Resources.Theme.class), new Xp.Callback() {
            @Override protected void beforeHookedMethod(Xp.Param p) {
                int[] d = inCard.get();
                if (d == null || d[0] <= 0 || !styled) return;
                if (cardColor == -1) {
                    Resources r = (Resources) p.thisObject;
                    cardColor = r.getIdentifier("materialColorSurfaceContainer", "color", "com.google.android.apps.nexuslauncher");
                    if (cardColor == 0) cardColor = r.getIdentifier("materialColorSurfaceContainer", "color", "android");
                }
                if (cardColor != 0 && ((Integer) p.args[0]) == cardColor) p.setResult(0);
            }
        });
        try {
            Xp.hookAllMethods(Class.forName("com.android.launcher3.util.QuickstepBackgroundBlurHelper", false, cl), "isBlurEnabled", new Xp.Callback() {
                @Override protected void beforeHookedMethod(Xp.Param p) { int[] d = inCard.get(); if (d != null && d[0] > 0 && styled) p.setResult(Boolean.FALSE); }
            });
        } catch (Throwable ignored) { }
    }

    private static int labelIndex(String title) {
        for (Map.Entry<Integer, String> e : labels.entrySet()) if (e.getValue().equals(title)) return e.getKey() - BASE_ID;
        int n = labels.size();
        labels.put(BASE_ID + n, title);
        return n;
    }

    private static String text(View row) {
        try { Object t = LauncherFeatures.get(row, "mBubbleText"); if (t instanceof TextView) return ((TextView) t).getText().toString(); } catch (Throwable ignored) { }
        return findText(row);
    }
    private static String findText(View v) {
        if (v instanceof TextView) return ((TextView) v).getText().toString();
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) { String s = findText(((ViewGroup) v).getChildAt(i)); if (s != null) return s; }
        return null;
    }

    private static int icon(Context c, String tag, int fallback) {
        String name = tag.equals("pixelsuite_create") ? "ic_create_new_folder" : tag.equals("pixelsuite_icon") ? "ic_palette"
                : tag.equals("pixelsuite_rename") ? "ic_create" : tag.equals("pixelsuite_privacy") ? "ic_lock" : tag.equals("pixelsuite_badge") ? "ic_notification_off"
                : tag.equals("pixelsuite_hide_search") ? "design_ic_visibility_off" : tag.equals("pixelsuite_customize") ? "ic_setting"
                : tag.equals("pixelsuite_force_stop") ? "ic_block_no_shadow" : tag.equals("pixelsuite_force_stop_alt") ? "mtrl_ic_cancel" : null;
        int id = name == null ? 0 : c.getResources().getIdentifier(name, "drawable", c.getPackageName());
        if (id == 0 && tag.equals("pixelsuite_badge")) id = c.getResources().getIdentifier("ic_circle_dismiss", "drawable", c.getPackageName());
        return id != 0 ? id : fallback;
    }

    private static Object field(Object o, String name) throws Exception { Field f = o.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(o); }

    private static Object unitValue;
    private static Object unit() {
        if (unitValue == null) try { unitValue = Class.forName("kotlin.Unit", false, actionType.getClassLoader()).getField("INSTANCE").get(null); } catch (Throwable ignored) { }
        return unitValue;
    }
}
