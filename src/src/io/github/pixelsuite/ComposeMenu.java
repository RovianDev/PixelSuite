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
    private static void log(String m) { }

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
                    try { add(p); } catch (Throwable error) { log("Compose app menu rows unavailable: " + error); }
                }
            });
            try {
                Class<?> arrow = LauncherFeatures.type("popup.PopupContainerWithArrow");
                for (Method m : arrow.getDeclaredMethods()) {
                    if (!m.getName().equals("loadAppShortcuts") || m.getParameterTypes().length != 1) continue;
                    Xp.hookMethod(m, new Xp.Callback() {
                        @Override protected void beforeHookedMethod(Xp.Param p) {
                            try { if (hidesShortcuts(p.args[0])) { p.setResult(null); log("App shortcuts hidden in the Compose app menu"); } } catch (Throwable ignored) { }
                        }
                    });
                }
            } catch (Throwable error) { log("Compose app menu shortcuts hook unavailable: " + error); }
            active = true;
            log("Compose app menu: Pixel Suite rows added to it");
        } catch (Throwable error) { log("Compose app menu unavailable: " + error); }
    }

    private static void add(Xp.Param p) throws Exception {
        final Object popup = p.thisObject;
        Object info = LauncherFeatures.get(popup, "itemInfo");
        if (info == null) return;
        if (hidesShortcuts(info)) p.args[0] = 0;
        if (LauncherFeatures.type("model.data.FolderInfo").isInstance(info)) return;
        if (LauncherFeatures.identity(info) == null || LauncherFeatures.privateItem(info)) return;
        Context context = ((View) popup).getContext();
        LinearLayout rows = new LinearLayout(context);
        rows.setOrientation(LinearLayout.VERTICAL);
        LauncherFeatures.appRows(popup, info, rows);
        if (rows.getChildCount() == 0) return;
        List<Object> items = new ArrayList<Object>((List<?>) p.args[1]);
        int fallbackIcon = items.isEmpty() ? 0 : ((Integer) field(items.get(0), "iconResId"));
        for (int i = 0; i < rows.getChildCount(); i++) {
            final View row = rows.getChildAt(i);
            Object tag = row.getTag();
            if (!(tag instanceof String) || !((String) tag).startsWith("pixelsuite_")) continue;
            String title = text(row);
            if (title == null) continue;
            int id = BASE_ID + labelIndex(title);
            int icon = icon(context, (String) tag, fallbackIcon);
            Object action = Proxy.newProxyInstance(actionType.getClassLoader(), new Class<?>[]{actionType}, new java.lang.reflect.InvocationHandler() {
                @Override public Object invoke(Object proxy, Method m, Object[] args) {
                    if (m.getDeclaringClass() == Object.class) {
                        if (m.getName().equals("equals")) return proxy == args[0];
                        if (m.getName().equals("hashCode")) return System.identityHashCode(proxy);
                        return "PixelSuiteMenuAction";
                    }
                    try { row.performClick(); } catch (Throwable error) { log("Menu action failed: " + error); }
                    return unit();
                }
            });
            items.add(newItem.newInstance(icon, newString.newInstance(id), action, fixedCategory));
        }
        p.args[1] = items;
    }

    private static boolean hidesShortcuts(Object info) throws Exception {
        return info != null && LauncherFeatures.on(LauncherFeatures.HIDE_SHORTCUTS)
                && (LauncherFeatures.type("model.data.AppInfo").isInstance(info) || DrawerFolders.isDrawerMember(info)) && !LauncherFeatures.privateItem(info);
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
                : tag.equals("pixelsuite_rename") ? "ic_create" : tag.equals("pixelsuite_privacy") ? "ic_lock" : null;
        int id = name == null ? 0 : c.getResources().getIdentifier(name, "drawable", c.getPackageName());
        return id != 0 ? id : fallback;
    }

    private static Object field(Object o, String name) throws Exception { Field f = o.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(o); }

    private static Object unitValue;
    private static Object unit() {
        if (unitValue == null) try { unitValue = Class.forName("kotlin.Unit", false, actionType.getClassLoader()).getField("INSTANCE").get(null); } catch (Throwable ignored) { }
        return unitValue;
    }
}
