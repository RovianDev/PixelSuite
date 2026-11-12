package io.github.pixelsuite;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Choreographer;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

final class MultiDrag {
    static final String KEY = "pixelsuite_multi_drag";
    static final PathInterpolator EMPHASIZED_DECELERATE = new PathInterpolator(0.05f, 0.7f, 0.1f, 1f);
    static final PathInterpolator STANDARD = new PathInterpolator(0.2f, 0f, 0f, 1f);

    static final class Pick {
        View view; Object info; String key; Bitmap icon; final RectF from = new RectF();
        float alpha, scaleX, scaleY; long added; float x, y; boolean placed; float sx, sy; long landed;
    }

    static final List<Pick> picks = new ArrayList<Pick>();
    private static StackView stack; private static Badge badge; private static ViewGroup badgeHost;
    private static Object primaryInfo; private static String primaryKey;
    private static boolean drawerHandled, workspaceDropped, ending; private static Object droppedFolder;
    private static Object buttonDropped; private static View removeButton, uninstallButton; private static boolean barShifted;
    private static int dropX, dropY;
    private static boolean looping;
    private static int generation;

    private MultiDrag() {}

    static boolean on() { return LauncherFeatures.on(KEY); }
    private static Object get(Object o, String n) { return LauncherFeatures.get(o, n); }
    private static Object call(Object o, String n, Object... v) { return LauncherFeatures.call(o, n, v); }
    private static Class<?> type(String n) { return LauncherFeatures.type(n); }
    static boolean active() { return !picks.isEmpty(); }

    static void install() {
        try {
            Xp.hookAllMethods(type("Workspace"), "onDrop", new Xp.Callback() {
                protected void afterHookedMethod(Xp.Param p) { if (active()) workspaceDropped = true; }
            });
            Xp.hookAllMethods(type("folder.Folder"), "onDrop", new Xp.Callback() {
                protected void afterHookedMethod(Xp.Param p) {
                    if (active() && !DrawerFolders.isFolder(get(p.thisObject, "mInfo"))) droppedFolder = p.thisObject;
                }
            });
            try {
            Xp.hookAllMethods(type("ButtonDropTarget"), "onDragStart", new Xp.Callback() {
                protected void afterHookedMethod(Xp.Param p) { try { frost((View) p.thisObject); } catch (Throwable error) { } }
            });
            Xp.hookAllMethods(type("ButtonDropTarget"), "onDragEnter", new Xp.Callback() {
                protected void afterHookedMethod(Xp.Param p) { try { hover((View) p.thisObject, true); } catch (Throwable error) { } }
            });
            Xp.hookAllMethods(type("ButtonDropTarget"), "onDragExit", new Xp.Callback() {
                protected void afterHookedMethod(Xp.Param p) { try { hover((View) p.thisObject, false); } catch (Throwable ignored) { } }
            });
            Xp.hookAllMethods(type("ButtonDropTarget"), "onDrop", new Xp.Callback() {
                protected void afterHookedMethod(Xp.Param p) {
                    if (!active()) return;
                    buttonDropped = p.thisObject;
                    try { if (isRemoveButton(p.thisObject)) flyInto((View) p.thisObject, p.args.length > 0 ? p.args[0] : null); } catch (Throwable ignored) { }
                }
            });
            Xp.Callback noUninstall = new Xp.Callback() {
                protected void beforeHookedMethod(Xp.Param p) { if (active() && p.thisObject == uninstallButton) p.setResult(false); }
            };
            Xp.hookAllMethods(type("ButtonDropTarget"), "isDropEnabled", noUninstall);
            Xp.hookAllMethods(type("ButtonDropTarget"), "acceptDrop", noUninstall);
            } catch (Throwable error) { }
            try {
                Xp.hookAllMethods(type("dragndrop.DragController"), "checkTouchMove", new Xp.Callback() {
                    protected void afterHookedMethod(Xp.Param p) {
                        try {
                            Object target = p.getResult();
                            if (target != null && type("ButtonDropTarget").isInstance(target)) return;
                            Object drag = get(p.thisObject, "mDragObject"); if (drag == null) return;
                            if (Boolean.TRUE.equals(get(drag, "dragComplete"))) return;
                            View view = (View) get(drag, "dragView");
                            if (view != null && Math.abs(view.getAlpha() - 0.65f) < 0.01f) view.setAlpha(1f);
                        } catch (Throwable ignored) { }
                    }
                });
            } catch (Throwable error) { }
            Xp.hookAllMethods(type("dragndrop.DragController"), "callOnDragEnd", new Xp.Callback() {
                protected void afterHookedMethod(Xp.Param p) { try { restoreStarters(); ended(); } catch (Throwable error) { reset(); } }
            });
        } catch (Throwable error) { }
    }

    static void tap(Activity a, float x, float y) {
        try {
            if (ending) { return; }
            Object controller = get(a, "mDragController"), drag = get(controller, "mDragObject");
            if (drag == null) { return; }
            Object info = get(drag, "dragInfo");
            if (info == null || type("model.data.FolderInfo").isInstance(info) || DrawerFolders.isFolder(info)) { return; }
            if (picks.isEmpty()) {
                primaryInfo = info; primaryKey = LauncherFeatures.identity(info);
                drawerHandled = false; workspaceDropped = false; droppedFolder = null;
            }
            View hit = findApp(a.getWindow().getDecorView(), Math.round(x), Math.round(y));
            if (hit == null) { return; }
            Object tag = hit.getTag(); String key = LauncherFeatures.identity(tag);
            if (key == null || tag == info || tag == primaryInfo) { return; }
            boolean drawer = drawerItem(tag);
            boolean inFolder = DrawerFolders.isDrawerMember(tag);
            if (DrawerDrag.fromFolder() && !inFolder) { return; }
            if (DrawerDrag.fromFolder() && !DrawerDrag.inHeldFolder(hit)) { return; }
            if (DrawerDrag.active() && !DrawerDrag.fromFolder() && (inFolder || !drawer || featured(hit, tag))) { return; }
            if (!DrawerDrag.active() && inFolder) { return; }
            if (drawer && key.equals(primaryKey)) return;
            for (Pick old : picks) if (old.view == hit || (drawer && key.equals(old.key))) return;
            Pick pick = new Pick(); pick.view = hit; pick.info = tag; pick.key = key; pick.added = SystemClock.uptimeMillis();
            pick.icon = iconOf(hit, pick.from);
            if (pick.icon == null) { return; }
            pick.alpha = hit.getAlpha(); pick.scaleX = hit.getScaleX(); pick.scaleY = hit.getScaleY();
            picks.add(pick);
            if (!attach(a)) { picks.remove(pick); return; }
            Xp.setAdditionalInstanceField(hit, "pixelsuite_picked", Boolean.TRUE); hidden.put(hit, Boolean.TRUE);
            float left = 0f;
            hit.animate().alpha(left).scaleX(.82f).scaleY(.82f).setDuration(160).setInterpolator(STANDARD).start();
            badge.count(1 + picks.size());
            shiftBar(a, true);
            Haptics.tick(a);
            loop();
        } catch (Throwable error) { }
    }

    static void rebound(View v) {
        boolean flagged = Boolean.TRUE.equals(Xp.getAdditionalInstanceField(v, "pixelsuite_picked"));
        boolean starter = Boolean.TRUE.equals(Xp.getAdditionalInstanceField(v, "pixelsuite_starter"));
        Object tag = v.getTag(); String key = tag == null ? null : LauncherFeatures.identity(tag);
        String held = heldDrawerKey();
        boolean isStarter = held != null && key != null && key.equals(held) && drawerItem(tag);
        if (isStarter) {
            v.animate().cancel(); v.setAlpha(0f);
            if (!starter) { Xp.setAdditionalInstanceField(v, "pixelsuite_starter", Boolean.TRUE); starters.add(new java.lang.ref.WeakReference<View>(v)); }
            return;
        }
        if (starter) { Xp.setAdditionalInstanceField(v, "pixelsuite_starter", null); if (!flagged) v.setAlpha(1f); }
        if (picks.isEmpty() && !flagged) return;
        Pick match = null;
        for (Pick p : picks) if (drawerItem(p.info) ? key != null && key.equals(p.key) : p.view == v && p.info == tag) match = p;
        v.animate().cancel();
        if (match != null) {
            match.view = v; Xp.setAdditionalInstanceField(v, "pixelsuite_picked", Boolean.TRUE); hidden.put(v, Boolean.TRUE);
            v.setAlpha(0f); v.setScaleX(.82f); v.setScaleY(.82f);
        } else if (flagged) {
            Xp.setAdditionalInstanceField(v, "pixelsuite_picked", null);
            v.setAlpha(1f); v.setScaleX(1f); v.setScaleY(1f);
        }
    }

    private static final java.util.ArrayList<java.lang.ref.WeakReference<View>> starters = new java.util.ArrayList<java.lang.ref.WeakReference<View>>();

    private static String heldDrawerKey() {
        try {
            if (!DrawerDrag.active() || !drawerOpenDrag()) return null;
            Activity a = LauncherFeatures.activity.get(); if (a == null) return null;
            Object drag = get(get(a, "mDragController"), "mDragObject"); if (drag == null) return null;
            Object info = get(drag, "dragInfo");
            return info != null && drawerItem(info) ? LauncherFeatures.identity(info) : null;
        } catch (Throwable ignored) { return null; }
    }

    private static boolean drawerOpenDrag() {
        try { Activity a = LauncherFeatures.activity.get(); return a != null && Boolean.TRUE.equals(call(get(a, "mDragController"), "isDragging")); } catch (Throwable t) { return false; }
    }

    private static void restoreStarters() {
        for (java.lang.ref.WeakReference<View> r : starters) {
            View v = r.get(); if (v == null) continue;
            Xp.setAdditionalInstanceField(v, "pixelsuite_starter", null);
            if (!Boolean.TRUE.equals(Xp.getAdditionalInstanceField(v, "pixelsuite_picked"))) { v.animate().cancel(); v.setAlpha(1f); }
        }
        starters.clear();
    }

    private static boolean featured(View v, Object tag) {
        try { if (((Number) get(tag, "container")).intValue() == -176) return true; } catch (Throwable ignored) { }
        for (android.view.ViewParent p = v.getParent(); p instanceof View; p = p.getParent()) if (p.getClass().getSimpleName().toLowerCase().contains("predict")) return true;
        return false;
    }

    static void takeOut(String heldKey) {
        List<String> keys = new ArrayList<String>();
        if (heldKey != null) keys.add(heldKey);
        for (Pick p : picks) if (drawerItem(p.info) && p.key != null && !keys.contains(p.key)) keys.add(p.key);
        if (DrawerFolders.takeOut(keys)) drawerHandled = true;
    }

    static boolean drawerItem(Object tag) { return type("model.data.AppInfo").isInstance(tag) || DrawerFolders.isDrawerMember(tag); }

    static View findApp(View v, int x, int y) {
        if (v.getVisibility() != View.VISIBLE || v.getAlpha() < .05f) return null;
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = g.getChildCount() - 1; i >= 0; i--) { View hit = findApp(g.getChildAt(i), x, y); if (hit != null) return hit; }
        }
        if (!type("BubbleTextView").isInstance(v)) return null;
        Object tag = v.getTag(); if (tag == null || LauncherFeatures.privateItem(tag)) return null;
        try { if (((Number) get(tag, "itemType")).intValue() != 0) return null; } catch (Throwable t) { return null; }
        if (LauncherFeatures.identity(tag) == null) return null;
        Rect r = new Rect(); if (!v.getGlobalVisibleRect(r) || !r.contains(x, y) || !v.isShown()) return null;
        return v;
    }

    static Bitmap iconOf(View v, RectF out) {
        try {
            TextView text = (TextView) v; Drawable d = text.getCompoundDrawables()[1];
            int size; try { size = ((Number) get(v, "mIconSize")).intValue(); } catch (Throwable t) { size = d == null ? 0 : d.getBounds().width(); }
            if (d == null || size <= 0) return null;
            float scale = v.getScaleX();
            Bitmap b = null;
            try {
                android.graphics.Picture picture = new android.graphics.Picture();
                Rect old = d.copyBounds();
                Canvas rec = picture.beginRecording(size, size);
                try { d.setBounds(0, 0, size, size); d.draw(rec); } finally { picture.endRecording(); d.setBounds(old); }
                Bitmap drawn = LauncherIcon.picture(picture);
                int[] at = new int[2]; v.getLocationInWindow(at);
                float left = at[0] + (v.getWidth() - size) / 2f * scale, top = at[1] + v.getPaddingTop() * scale;
                out.set(left, top, left + size * scale, top + size * scale);
                return drawn;
            } catch (Throwable t) { }
            Bitmap own = findBitmap(d, 0);
            if (own != null) {
                try { b = own.copy(Bitmap.Config.ARGB_8888, false); if (b != null && (b.getWidth() != size || b.getHeight() != size)) { Bitmap fit = Bitmap.createScaledBitmap(b, size, size, true); if (fit != b) { b.recycle(); b = fit; } } }
                catch (Throwable t) { b = null; }
            }
            if (b == null) {
                try { b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888); Rect old = d.copyBounds(); d.setBounds(0, 0, size, size); d.draw(new Canvas(b)); d.setBounds(old); }
                catch (Throwable t) { return null; }
            }
            int[] at = new int[2]; v.getLocationInWindow(at);
            float left = at[0] + (v.getWidth() - size) / 2f * scale, top = at[1] + v.getPaddingTop() * scale;
            out.set(left, top, left + size * scale, top + size * scale);
            return shaped(b);
        } catch (Throwable error) { return null; }
    }

    private static String maskPackage; private static android.graphics.Path maskPath;
    private static android.graphics.Path currentMask(Context c) {
        try {
            String json = android.provider.Settings.Secure.getString(c.getContentResolver(), "theme_customization_overlay_packages");
            String pkg = json == null ? "" : new org.json.JSONObject(json).optString("android.theme.customization.adaptive_icon_shape", "");
            if (maskPath != null && pkg.equals(maskPackage)) return maskPath;
            String data = null;
            if (!pkg.isEmpty()) {
                Resources r = c.createPackageContext(pkg, 0).getResources();
                int id = r.getIdentifier("config_icon_mask", "string", pkg); if (id == 0) id = r.getIdentifier("config_icon_mask", "string", "android");
                if (id != 0) data = r.getString(id);
            }
            if (data == null) { Resources sys = Resources.getSystem(); data = sys.getString(sys.getIdentifier("config_icon_mask", "string", "android")); }
            android.graphics.Path path = (android.graphics.Path) Class.forName("android.util.PathParser").getMethod("createPathFromPathData", String.class).invoke(null, data);
            maskPackage = pkg; maskPath = path;
            return path;
        } catch (Throwable error) { return null; }
    }

    private static Bitmap shaped(Bitmap b) {
        try {
            Activity a = LauncherFeatures.activity.get(); android.graphics.Path mask = a == null ? null : ShapeWatch.path(a);
            if (mask == null && a != null) mask = currentMask(a);
            if (mask == null) return b;
            int n = b.getWidth();
            Bitmap out = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888); Canvas canvas = new Canvas(out);
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            canvas.save(); canvas.scale(n / 100f, n / 100f); canvas.drawPath(mask, paint); canvas.restore();
            paint.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN));
            canvas.drawBitmap(b, 0, 0, paint);
            b.recycle();
            return out;
        } catch (Throwable error) { return b; }
    }

    private static Bitmap findBitmap(Object o, int depth) {
        if (o == null || depth > 2) return null;
        if (o instanceof Bitmap) { Bitmap b = (Bitmap) o; return b.getWidth() >= 16 && b.getHeight() >= 16 && !b.isRecycled() ? b : null; }
        for (Class<?> c = o.getClass(); c != null && c != Object.class && c != Drawable.class; c = c.getSuperclass()) {
            for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive() || f.getType().isEnum()) continue;
                String type = f.getType().getName();
                if (type.startsWith("java.") || type.startsWith("android.content") || type.startsWith("android.os") || type.equals("android.graphics.Paint") || type.equals("android.graphics.Rect")) continue;
                try {
                    f.setAccessible(true); Object v = f.get(o);
                    if (v instanceof Bitmap || (v instanceof Drawable) || type.startsWith("com.android.launcher3")) { Bitmap b = findBitmap(v, depth + 1); if (b != null) return b; }
                } catch (Throwable ignored) { }
            }
        }
        return null;
    }

    private static boolean attach(Activity a) {
        ViewGroup layer = (ViewGroup) call(a, "getDragLayer");
        View dragView = dragView(a);
        if (stack == null || stack.getParent() != layer) {
            detach();
            stack = new StackView(a);
            Object params = call(layer, "generateLayoutParams", new FrameLayout.LayoutParams(-1, -1));
            try { LauncherFeatures.set(params, "ignoreInsets", true); } catch (Throwable ignored) { }
            int index = dragView != null && dragView.getParent() == layer ? layer.indexOfChild(dragView) : layer.getChildCount();
            layer.addView(stack, index, (ViewGroup.LayoutParams) params);
            badgeHost = (ViewGroup) a.getWindow().getDecorView();
            badge = new Badge(a); badge.setBounds(0, 0, badgeHost.getWidth(), badgeHost.getHeight());
            badgeHost.getOverlay().add(badge);
        }
        return true;
    }

    private static void detach() {
        if (stack != null && stack.getParent() instanceof ViewGroup) ((ViewGroup) stack.getParent()).removeView(stack);
        if (badge != null && badgeHost != null) { badgeHost.getOverlay().remove(badge); badgeHost.invalidate(); }
        stack = null; badge = null; badgeHost = null;
    }

    static View dragView(Activity a) {
        try { Object drag = get(get(a, "mDragController"), "mDragObject"); return drag == null ? null : (View) get(drag, "dragView"); }
        catch (Throwable t) { return null; }
    }

    static void follow() {
        if (!active() || ending) return;
        Activity a = LauncherFeatures.activity.get(); if (a == null) return;
        try { Object drag = get(get(a, "mDragController"), "mDragObject"); if (drag != null) { dropX = ((Number) get(drag, "x")).intValue(); dropY = ((Number) get(drag, "y")).intValue(); } }
        catch (Throwable ignored) { }
    }

    private static void loop() {
        if (looping) return; looping = true;
        Choreographer.getInstance().postFrameCallback(new Choreographer.FrameCallback() {
            public void doFrame(long nanos) {
                if (stack == null) { looping = false; return; }
                stack.frame(); stack.invalidate(); if (badgeHost != null) badgeHost.invalidate();
                Choreographer.getInstance().postFrameCallback(this);
            }
        });
    }

    static void handledInFolder() { if (active()) drawerHandled = true; }

    static void drawerDrop() {
        if (!active()) return;
        try {
            List<LauncherData.Folder> folders = LauncherFeatures.data.folders();
            LauncherData.Folder target = null;
            for (LauncherData.Folder f : folders) if (primaryKey != null && f.apps.contains(primaryKey)) target = f;
            List<String> keys = new ArrayList<String>();
            for (Pick p : picks) if (drawerItem(p.info) && !keys.contains(p.key) && !p.key.equals(primaryKey)) keys.add(p.key);
            if (keys.isEmpty()) return;
            for (LauncherData.Folder f : folders) f.apps.removeAll(keys);
            if (target != null) { int at = target.apps.indexOf(primaryKey) + 1; target.apps.addAll(Math.max(0, Math.min(at, target.apps.size())), keys); }
            for (int i = folders.size() - 1; i >= 0; i--) if (folders.get(i).apps.isEmpty()) folders.remove(i);
            if (LauncherFeatures.data.save(folders)) { drawerHandled = true; LauncherFeatures.refresh(); }
        } catch (Throwable error) { }
    }

    private static void ended() {
        if (!active() || ending) return;
        ending = true;
        final int session = generation;
        final Activity a = LauncherFeatures.activity.get();
        final boolean removing = buttonDropped != null && !drawerHandled && isRemoveButton(buttonDropped);
        final boolean success = drawerHandled || workspaceDropped || droppedFolder != null || removing;
        if (removing && a != null) {
            removeOriginals(a, new ArrayList<Pick>(picks));
        }
        if (stack != null) stack.finish(success);
        if (badge != null) badge.finish();
        final long flight = stack != null ? stack.flightLeft() : 0;
        if (success && !drawerHandled && !removing && a != null) {
            stack.postDelayed(new Runnable() { public void run() {
                if (session != generation) return;
                try { placeOnHome(a); } catch (Throwable error) { }
                restoreViews(); reset();
            }}, 320);
        } else {
            View host = stack;
            Runnable done = new Runnable() { public void run() { if (session == generation) { restoreViews(); reset(); } } };
            if (host != null) host.postDelayed(done, Math.max(flight, success ? 240 : 380)); else done.run();
        }
    }

    private static void placeOnHome(Activity a) throws Exception {
        List<Pick> moving = new ArrayList<Pick>(picks);
        Object folder = droppedFolder;
        int container = -1;
        try { container = ((Number) get(primaryInfo, "container")).intValue(); } catch (Throwable ignored) { }
        if (folder == null && container >= 0) folder = folderById(a, container);
        ViewGroup layer = (ViewGroup) call(a, "getDragLayer");
        int[] base = new int[2]; layer.getLocationInWindow(base);
        int wx = dropX + base[0], wy = dropY + base[1];
        if (folder == null) { View icon = folderIconAt(a.getWindow().getDecorView(), wx, wy); if (icon != null) folder = get(icon, "mFolder"); }
        if (folder != null) { intoFolder(a, folder, moving); return; }
        onPage(a, moving, wx, wy);
    }

    private static Object newItem(Activity a, Pick p) {
        try {
            if (type("model.data.AppInfo").isInstance(p.info)) return LauncherFeatures.workspaceItem(p.info, a);
            Object app = HomeFolderApps.find(LauncherFeatures.apps(), p.key);
            return app == null ? null : LauncherFeatures.workspaceItem(app, a);
        } catch (Throwable error) { return null; }
    }

    private static boolean homeItem(Pick p) { return !drawerItem(p.info); }

    private static void intoFolder(Activity a, Object folder, List<Pick> moving) throws Exception {
        Object info = get(folder, "mInfo"); int id = FolderSort.homeId(info); List<Pick> gone = new ArrayList<Pick>();
        for (Pick p : moving) {
            int from = -1; try { from = ((Number) get(p.info, "container")).intValue(); } catch (Throwable ignored) { }
            if (homeItem(p) && from == id) continue;
            Object item = newItem(a, p); if (item == null) continue;
            HomeFolderApps.add(folder, info, item);
            if (homeItem(p)) gone.add(p);
        }
        removeOriginals(a, gone);
        try { Object icon = get(folder, "mFolderIcon"); call(icon, "updatePreviewItems", false); ((View) icon).invalidate(); } catch (Throwable ignored) { }
    }

    private static void onPage(Activity a, List<Pick> moving, int wx, int wy) throws Exception {
        Object workspace = get(a, "mWorkspace");
        int pageIndex = ((Number) call(workspace, "getNextPage")).intValue();
        ViewGroup page = (ViewGroup) ((ViewGroup) workspace).getChildAt(pageIndex);
        int countX = ((Number) get(page, "mCountX")).intValue(), countY = ((Number) get(page, "mCountY")).intValue();
        boolean[][] used = new boolean[countX][countY];
        ViewGroup cells = (ViewGroup) get(page, "mShortcutsAndWidgets");
        int screenId = -1;
        for (int i = 0; i < cells.getChildCount(); i++) {
            View child = cells.getChildAt(i); Object lp = child.getLayoutParams();
            try {
                int x = ((Number) get(lp, "mCellX")).intValue(), y = ((Number) get(lp, "mCellY")).intValue();
                int w = Math.max(1, ((Number) get(lp, "cellHSpan")).intValue()), h = Math.max(1, ((Number) get(lp, "cellVSpan")).intValue());
                for (int xx = x; xx < Math.min(countX, x + w); xx++) for (int yy = y; yy < Math.min(countY, y + h); yy++) if (xx >= 0 && yy >= 0) used[xx][yy] = true;
            } catch (Throwable error) { throw new IllegalStateException("Cannot read occupied home cells", error); }
            if (screenId < 0) try { screenId = ((Number) get(child.getTag(), "screenId")).intValue(); } catch (Throwable ignored) { }
        }
        if (screenId < 0) try { screenId = ((Number) call(workspace, "getScreenIdForPageIndex", pageIndex)).intValue(); } catch (Throwable ignored) { }
        if (screenId < 0) try { screenId = ((Number) call(workspace, "getIdForScreen", page)).intValue(); } catch (Throwable ignored) { }
        if (screenId < 0) { return; }
        int[] at = new int[2]; page.getLocationInWindow(at);
        float cw = (page.getWidth() - page.getPaddingLeft() - page.getPaddingRight()) * page.getScaleX() / countX;
        float ch = (page.getHeight() - page.getPaddingTop() - page.getPaddingBottom()) * page.getScaleY() / countY;
        int dropCellX = Math.max(0, Math.min(countX - 1, (int) ((wx - at[0] - page.getPaddingLeft() * page.getScaleX()) / Math.max(1, cw))));
        int dropCellY = Math.max(0, Math.min(countY - 1, (int) ((wy - at[1] - page.getPaddingTop() * page.getScaleY()) / Math.max(1, ch))));
        List<int[]> free = new ArrayList<int[]>();
        for (int y = 0; y < countY; y++) for (int x = 0; x < countX; x++) if (!used[x][y]) free.add(new int[]{x, y, (x - dropCellX) * (x - dropCellX) + 2 * (y - dropCellY) * (y - dropCellY), y * countX + x});
        java.util.Collections.sort(free, new java.util.Comparator<int[]>() { public int compare(int[] p, int[] q) { return p[2] != q[2] ? p[2] - q[2] : p[3] - q[3]; } });
        Object writer = call(a, "getModelWriter");
        List<Object> items = new ArrayList<Object>(); List<Pick> placed = new ArrayList<Pick>(); int next = 0;
        for (Pick p : moving) {
            if (next >= free.size()) break;
            Object item = newItem(a, p); if (item == null) continue;
            int[] cell = free.get(next++);
            call(writer, "addItemToDatabase", item, -100, screenId, cell[0], cell[1]);
            items.add(item); placed.add(p);
        }
        if (items.isEmpty()) { if (moving.size() > 0) toast(a, "No room on this page for the other apps"); return; }
        try { call(get(a, "modelCallbacks"), "bindItems", items, true); }
        catch (Throwable noBindItems) {
            for (Object item : items) { View view = (View) call(a, "createShortcut", item); call(workspace, "addInScreenFromBind", view, item); }
        }
        removeOriginals(a, placed);
        if (placed.size() < moving.size()) toast(a, (moving.size() - placed.size()) + " apps did not fit on this page");
    }

    private static void removeOriginals(final Activity a, List<Pick> list) {
        final List<Pick> todo = new ArrayList<Pick>();
        boolean fromFolder = false;
        for (Pick p : list) if (homeItem(p)) { todo.add(p); try { if (((Number) get(p.info, "container")).intValue() >= 0) fromFolder = true; } catch (Throwable ignored) { } }
        if (todo.isEmpty()) return;
        final long start = SystemClock.uptimeMillis(); final boolean folderMoves = fromFolder;
        final Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() { public void run() {
            if (LauncherFeatures.activity.get() != a || a.isDestroyed()) return;
            long waited = SystemClock.uptimeMillis() - start;
            if (folderMoves) {
                boolean busy = waited < 900;
                for (Pick p : todo) {
                    try {
                        int container = ((Number) get(p.info, "container")).intValue();
                        Object folder = container >= 0 ? folderById(a, container) : null;
                        if (folder != null && (Boolean.TRUE.equals(get(folder, "mIsOpen")) || ((Number) get(folder, "mState")).intValue() == 1)) busy = true;
                    } catch (Throwable ignored) { }
                }
                if (busy) {
                    if (waited >= 3000) { return; }
                    handler.postDelayed(this, 120); return;
                }
            }
            try { removeNow(a, todo); } catch (Throwable error) { }
        } });
    }

    private static void removeNow(Activity a, List<Pick> todo) throws Exception {
        java.util.LinkedHashMap<Integer, List<Object>> byFolder = new java.util.LinkedHashMap<Integer, List<Object>>();
        for (Pick p : todo) {
            int container = ((Number) get(p.info, "container")).intValue();
            if (container >= 0) { List<Object> l = byFolder.get(container); if (l == null) byFolder.put(container, l = new ArrayList<Object>()); l.add(p.info); continue; }
            View view = p.view != null && p.view.isAttachedToWindow() && p.view.getTag() == p.info ? p.view : viewOf(a.getWindow().getDecorView(), p.info);
            if (view == null) { continue; }
            try { call(a, "removeItem", view, p.info, true, "Pixel Suite: moved with other apps"); }
            catch (Throwable fourArgs) { call(a, "removeItem", view, p.info, true); }
        }
        for (java.util.Map.Entry<Integer, List<Object>> e : byFolder.entrySet()) {
            View icon = findFolderIcon(a.getWindow().getDecorView(), e.getKey());
            if (icon == null) { continue; }
            Object folder = get(icon, "mFolder"), info = get(icon, "mInfo");
            List<?> contents = (List<?>) get(info, "contents");
            List<Object> removing = new ArrayList<Object>();
            for (Object item : e.getValue()) if (contents.contains(item)) removing.add(item);
            if (removing.isEmpty()) continue;
            int count = contents.size();
            if (removing.size() >= count) {
                try { call(a, "removeItem", icon, info, true, "Pixel Suite: moved with other apps"); }
                catch (Throwable fourArgs) { call(a, "removeItem", icon, info, true); }
            } else HomeFolderApps.remove(a, folder, removing);
        }
    }

    private static View viewOf(View v, Object tag) {
        if (v.getTag() == tag && type("BubbleTextView").isInstance(v)) return v;
        if (v instanceof ViewGroup) { ViewGroup g = (ViewGroup) v; for (int i = 0; i < g.getChildCount(); i++) { View r = viewOf(g.getChildAt(i), tag); if (r != null) return r; } }
        return null;
    }

    static Object folderById(Activity a, int id) {
        View icon = findFolderIcon(a.getWindow().getDecorView(), id);
        return icon == null ? null : get(icon, "mFolder");
    }
    private static View findFolderIcon(View v, int id) {
        try { if (type("folder.FolderIcon").isInstance(v) && FolderSort.homeId(get(v, "mInfo")) == id && !DrawerFolders.isFolder(get(v, "mInfo"))) return v; } catch (Throwable ignored) { }
        if (v instanceof ViewGroup) { ViewGroup g = (ViewGroup) v; for (int i = 0; i < g.getChildCount(); i++) { View r = findFolderIcon(g.getChildAt(i), id); if (r != null) return r; } }
        return null;
    }
    private static View folderIconAt(View v, int x, int y) {
        if (v.getVisibility() != View.VISIBLE) return null;
        if (v instanceof ViewGroup) { ViewGroup g = (ViewGroup) v; for (int i = g.getChildCount() - 1; i >= 0; i--) { View r = folderIconAt(g.getChildAt(i), x, y); if (r != null) return r; } }
        try {
            if (type("folder.FolderIcon").isInstance(v) && !DrawerFolders.isFolder(get(v, "mInfo"))) { Rect r = new Rect(); if (v.getGlobalVisibleRect(r) && r.contains(x, y)) return v; }
        } catch (Throwable ignored) { }
        return null;
    }

    private static void toast(Context c, String text) { try { android.widget.Toast.makeText(c, I18n.cs(text), android.widget.Toast.LENGTH_SHORT).show(); } catch (Throwable ignored) { } }

    private static void restoreViews() {
        for (Pick p : picks) {
            View v = p.view; if (v == null) continue;
            v.animate().cancel(); v.setScaleX(p.scaleX); v.setScaleY(p.scaleY); Xp.setAdditionalInstanceField(v, "pixelsuite_picked", null);
            v.setAlpha(0f); v.animate().alpha(p.alpha <= 0 ? 1f : p.alpha).setDuration(180).start();
        }
    }

    private static final java.util.WeakHashMap<View, Boolean> hidden = new java.util.WeakHashMap<View, Boolean>();

    private static void reset() {
        for (View v : new ArrayList<View>(hidden.keySet())) {
            if (v == null || !Boolean.TRUE.equals(Xp.getAdditionalInstanceField(v, "pixelsuite_picked"))) continue;
            Xp.setAdditionalInstanceField(v, "pixelsuite_picked", null);
            v.animate().cancel(); v.setAlpha(1f); v.setScaleX(1f); v.setScaleY(1f);
        }
        hidden.clear();
        restoreStarters();
        generation++;
        for (Pick p : picks) try { p.icon.recycle(); } catch (Throwable ignored) { }
        picks.clear(); detach(); ending = false; primaryInfo = null; primaryKey = null;
        drawerHandled = false; workspaceDropped = false; droppedFolder = null; buttonDropped = null;
        Activity a = LauncherFeatures.activity.get(); if (a != null) shiftBar(a, false);
    }

    static void frost(final View button) {
        if (Boolean.TRUE.equals(Xp.getAdditionalInstanceField(button, "pixelsuite_frosted"))) return;
        Xp.setAdditionalInstanceField(button, "pixelsuite_frosted", Boolean.TRUE);
        Context c = button.getContext();
        float height = button.getHeight() > 0 ? button.getHeight() : FrostedSurface.dp(c, 48);
        FrostedSurface.frost(button, height / 2f / c.getResources().getDisplayMetrics().density, "Drop target");
        int ink = FrostedSurface.dark(c) ? 0xffeee9f1 : 0xff202027;
        if (button instanceof TextView) {
            TextView t = (TextView) button; t.setTextColor(ink);
            t.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(ink));
        }
    }

    private static View lit;
    static void hover(final View button, boolean on) {
        if (on) { if (lit != null && lit != button) light(lit, false); lit = button; }
        else if (lit == button) lit = null;
        light(button, on);
    }
    static void unlight() { View v = lit; lit = null; if (v != null) light(v, false); }
    private static void light(final View button, boolean on) {
        Context c = button.getContext(); boolean dark = FrostedSurface.dark(c);
        int accent = LauncherFeatures.surface(c, dark ? "system_accent1_700" : "system_accent1_200", dark ? 0xff0842a0 : 0xffa8c7fa);
        int ink = on ? (dark ? 0xffd3e3fd : 0xff041e49) : (dark ? 0xffeee9f1 : 0xff202027);
        outline(button, false);
        android.graphics.drawable.Drawable bg = button.getBackground();
        if (bg instanceof android.graphics.drawable.LayerDrawable) {
            android.graphics.drawable.LayerDrawable layers = (android.graphics.drawable.LayerDrawable) bg;
            Drawable tint = layers.getNumberOfLayers() > 1 ? layers.getDrawable(1) : null;
            if (tint instanceof android.graphics.drawable.GradientDrawable) {
                int base[] = FrostedSurface.colors(c), glow = (accent & 0xffffff) | 0xe6000000;
                ((android.graphics.drawable.GradientDrawable) tint).setColors(on ? new int[]{glow, glow} : base);
            }
        }
        if (button instanceof TextView) {
            TextView t = (TextView) button; t.setTextColor(ink); t.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(ink));
        }
        if (on) outline(button, true);
        button.invalidate();
        button.animate().scaleX(on ? 1.08f : 1f).scaleY(on ? 1.08f : 1f).setDuration(on ? 220 : 180).setStartDelay(0).setInterpolator(on ? EMPHASIZED_DECELERATE : STANDARD).start();
    }

    private static void outline(View button, boolean on) {
        Object mine = Xp.getAdditionalInstanceField(button, "pixelsuite_outline");
        if (mine instanceof Object[]) {
            Object[] kept = (Object[]) mine;
            Xp.setAdditionalInstanceField(button, "pixelsuite_outline", null);
            if (button.getBackground() == kept[0]) button.setBackground((Drawable) kept[1]);
        }
        if (!on) return;
        Drawable base = button.getBackground();
        android.graphics.drawable.GradientDrawable ring = new android.graphics.drawable.GradientDrawable();
        float h = button.getHeight() > 0 ? button.getHeight() : FrostedSurface.dp(button.getContext(), 48);
        ring.setColor(0); ring.setCornerRadius(h / 2f); ring.setStroke(FrostedSurface.dp(button.getContext(), 1), 0x66ffffff);
        android.graphics.drawable.LayerDrawable lit = new android.graphics.drawable.LayerDrawable(base == null ? new Drawable[]{ring} : new Drawable[]{base, ring});
        lit.setPaddingMode(android.graphics.drawable.LayerDrawable.PADDING_MODE_STACK);
        int l = button.getPaddingLeft(), t = button.getPaddingTop(), r = button.getPaddingRight(), b = button.getPaddingBottom();
        Xp.setAdditionalInstanceField(button, "pixelsuite_outline", new Object[]{lit, base});
        button.setBackground(lit);
        button.setPadding(l, t, r, b);
    }

    static boolean hiding(View v) { return barShifted && v != null && v == uninstallButton; }
    static boolean holds(View v) { return barShifted && v != null && (v == uninstallButton || v == removeButton); }

    private static boolean isRemoveButton(Object target) {
        String n = target.getClass().getSimpleName().toLowerCase(), t = text(target);
        return n.contains("delete") || n.contains("remove") || (!n.contains("secondary") && t.startsWith("remove"));
    }
    private static boolean isUninstallButton(Object target) {
        String n = target.getClass().getSimpleName().toLowerCase(), t = text(target);
        return n.contains("secondary") || n.contains("uninstall") || t.contains("uninstall");
    }
    private static String text(Object v) { try { return String.valueOf(((TextView) v).getText()).toLowerCase(); } catch (Throwable t) { return ""; } }
    private static void buttons(View v, List<View> out) {
        try { if (type("ButtonDropTarget").isInstance(v)) { out.add(v); return; } } catch (Throwable ignored) { }
        if (v instanceof ViewGroup) { ViewGroup g = (ViewGroup) v; for (int i = 0; i < g.getChildCount(); i++) buttons(g.getChildAt(i), out); }
    }

    private static void shiftBar(Activity a, boolean multi) {
        try {
            if (multi == barShifted) return;
            List<View> all = new ArrayList<View>(); buttons(a.getWindow().getDecorView(), all);
            if (all.isEmpty()) return;
            ViewGroup bar = null;
            for (android.view.ViewParent q = all.get(0).getParent(); q instanceof ViewGroup; q = q.getParent()) {
                if (bar == null) bar = (ViewGroup) q;
                try { if (type("DropTargetBar").isInstance(q)) { bar = (ViewGroup) q; break; } } catch (Throwable ignored) { }
            }
            if (bar == null) return;
            if (multi) {
                removeButton = null; uninstallButton = null;
                for (View b : all) frost(b);
                for (View b : all) { if (isUninstallButton(b)) uninstallButton = b; else if (isRemoveButton(b)) removeButton = b; }
                if (uninstallButton != null) uninstallButton.animate().cancel();
                if (uninstallButton != null) uninstallButton.animate().alpha(0f).scaleX(.8f).scaleY(.8f).setDuration(220).setInterpolator(STANDARD).start();
                if (removeButton != null) {
                    int[] bl = new int[2], rl = new int[2]; bar.getLocationOnScreen(bl); removeButton.getLocationOnScreen(rl);
                    float dx = bl[0] + bar.getWidth() / 2f - (rl[0] - removeButton.getTranslationX() + removeButton.getWidth() / 2f);
                    removeButton.animate().cancel();
                    removeButton.animate().translationX(dx).setDuration(420).setStartDelay(60).setInterpolator(EMPHASIZED_DECELERATE).start();
                }
                barShifted = true;
            } else {
                barShifted = false;
                final View u = uninstallButton, r = removeButton;
                boolean shown = bar.getVisibility() == View.VISIBLE && bar.getAlpha() > 0f && bar.isAttachedToWindow();
                if (u != null && !Balloon.busy(u)) {
                    u.animate().cancel();
                    if (shown) u.animate().alpha(1f).scaleX(1f).scaleY(1f).setStartDelay(0).setDuration(200).setInterpolator(STANDARD).start();
                    else { u.setAlpha(1f); u.setScaleX(1f); u.setScaleY(1f); }
                }
                if (r != null) {
                    r.animate().cancel();
                    if (shown) r.animate().translationX(0f).setStartDelay(0).setDuration(240).setInterpolator(STANDARD).start();
                    else r.setTranslationX(0f);
                }
                uninstallButton = null; removeButton = null;
            }
        } catch (Throwable error) { }
    }

    private static void flyInto(View button, Object drag) {
        Activity a = LauncherFeatures.activity.get();
        if (a == null || stack == null || picks.isEmpty()) return;
        int[] sw = new int[2]; stack.getLocationInWindow(sw);
        float x = Float.NaN, y = Float.NaN;
        try {
            Rect to = (Rect) call(button, "getIconRect", drag);
            View dv = drag == null ? null : (View) get(drag, "dragView");
            ViewGroup layer = (ViewGroup) call(a, "getDragLayer");
            if (to != null && dv != null && dv.getMeasuredWidth() > 0) {
                int[] lw = new int[2]; layer.getLocationInWindow(lw);
                x = lw[0] + to.left + dv.getMeasuredWidth() / 2f - sw[0];
                y = lw[1] + to.top + dv.getMeasuredHeight() / 2f - sw[1];
            }
        } catch (Throwable ignored) { }
        if (Float.isNaN(x)) {
            int[] bw = new int[2]; button.getLocationInWindow(bw);
            float icon = FrostedSurface.dp(a, 24);
            if (button instanceof TextView) {
                Drawable[] d = ((TextView) button).getCompoundDrawablesRelative();
                if (d[0] != null && d[0].getBounds().width() > 0) icon = d[0].getBounds().width();
            }
            boolean rtl = button.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
            x = bw[0] + (rtl ? button.getWidth() - button.getPaddingRight() - icon / 2f : button.getPaddingLeft() + icon / 2f) * button.getScaleX() - sw[0];
            y = bw[1] + button.getHeight() * button.getScaleY() / 2f - sw[1];
        }
        stack.fly(x, y);
        if (badge != null) badge.fly(x + sw[0], y + sw[1]);
    }

    static void cancel() {
        for (Pick p : picks) {
            View v = p.view; if (v == null) continue;
            v.animate().cancel(); v.setAlpha(p.alpha); v.setScaleX(p.scaleX); v.setScaleY(p.scaleY);
            Xp.setAdditionalInstanceField(v, "pixelsuite_picked", null);
        }
        reset();
    }

    static final class StackView extends View {
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG), shade = new Paint(Paint.ANTI_ALIAS_FLAG);
        final float density; float cx, cy, size; boolean known; long last;
        final float[] hx = new float[24], hy = new float[24]; final long[] ht = new long[24]; int head;
        int state; long stateAt; float flyX, flyY;
        final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        StackView(Context c) { super(c); density = c.getResources().getDisplayMetrics().density; setWillNotDraw(false); setClickable(false); setFocusable(false); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); }
        public boolean onTouchEvent(android.view.MotionEvent e) { return false; }

        void finish(boolean success) { if (state == 3) return; state = success ? 1 : 2; stateAt = SystemClock.uptimeMillis(); }

        static final long FLIGHT = 300, STAGGER = 34, RIPPLE = 260;
        static long delayOf(int order) { return Math.min(order, 8) * STAGGER; }

        void fly(float x, float y) {
            if (state == 3 || !known) return;
            flyX = x; flyY = y;
            for (Pick p : picks) { p.sx = p.x; p.sy = p.y; p.landed = 0; }
            state = 3; stateAt = SystemClock.uptimeMillis();
            boolean dark = FrostedSurface.dark(getContext());
            ring.setStyle(Paint.Style.STROKE); ring.setStrokeWidth(2f * density);
            ring.setColor(LauncherFeatures.surface(getContext(), dark ? "system_accent1_200" : "system_accent1_600", dark ? 0xffaac7ff : 0xff3d5f99));
        }

        long flightLeft() {
            if (state != 3) return 0;
            long total = delayOf(Math.max(0, picks.size() - 1)) + FLIGHT + RIPPLE + 40;
            return Math.max(0, total - (SystemClock.uptimeMillis() - stateAt));
        }

        void frame() {
            Activity a = LauncherFeatures.activity.get(); long now = SystemClock.uptimeMillis();
            View drag = a == null ? null : dragView(a);
            if (drag != null && state == 0) {
                int[] at = new int[2], me = new int[2]; drag.getLocationInWindow(at); getLocationInWindow(me);
                float w = drag.getWidth() * drag.getScaleX(), h = drag.getHeight() * drag.getScaleY();
                cx = at[0] - me[0] + w / 2f; cy = at[1] - me[1] + h / 2f; size = Math.min(w, h); known = true;
            }
            if (!known) return;
            float dt = last == 0 ? 16 : Math.min(48, now - last); last = now;
            head = (head + 1) % hx.length; hx[head] = cx; hy[head] = cy; ht[head] = now;
            int n = picks.size();
            for (int i = 0; i < n; i++) {
                Pick p = picks.get(i); int depth = n - 1 - i;
                float tx = cx + (depth + 1) * 5f * density, ty = cy + (depth + 1) * 4f * density;
                if (!p.placed) { p.x = tx; p.y = ty; p.placed = true; }
                float k = 1f - (float) Math.pow(1f - Math.max(.10f, .34f - depth * .07f), dt / 16f);
                p.x += (tx - p.x) * k; p.y += (ty - p.y) * k;
            }
        }

        private float[] past(long ago) {
            long want = SystemClock.uptimeMillis() - ago;
            for (int i = 0; i < hx.length; i++) { int j = (head - i + hx.length) % hx.length; if (ht[j] != 0 && ht[j] <= want) return new float[]{hx[j], hy[j]}; }
            return null;
        }

        protected void onDraw(Canvas c) {
            if (!known || picks.isEmpty()) return;
            long now = SystemClock.uptimeMillis(); int n = picks.size();
            float card = size * .86f;
            if (state == 3) { flight(c, now, n, card); return; }
            float end = state == 0 ? 0f : Math.min(1f, (now - stateAt) / (state == 1 ? 220f : 340f));
            if (state == 0) {
                Pick top = picks.get(n - 1);
                float[] alphas = {.22f, .13f, .07f}; long[] ago = {45, 90, 140};
                for (int g = 2; g >= 0; g--) {
                    float[] at = past(ago[g]); if (at == null) continue;
                    float moved = (float) Math.hypot(at[0] - cx, at[1] - cy);
                    float strength = Math.min(1f, moved / (24 * density)); if (strength <= .02f) continue;
                    draw(c, top.icon, at[0], at[1], card * (1f - .05f * (g + 1)), 0, alphas[g] * strength);
                }
            }
            for (int i = Math.max(0, n - 4); i < n; i++) {
                Pick p = picks.get(i); int depth = n - 1 - i;
                float x = p.x, y = p.y, s = card, alpha = depth >= 3 ? .0f : 1f - depth * .12f, turn = (depth % 2 == 0 ? 5f : -4f) * (depth + 1) * .6f;
                float fly = Math.min(1f, (now - p.added) / 420f);
                if (fly < 1f) {
                    float e = EMPHASIZED_DECELERATE.getInterpolation(fly);
                    x = p.from.centerX() + (x - p.from.centerX()) * e; y = p.from.centerY() + (y - p.from.centerY()) * e - (float) Math.sin(Math.PI * e) * 28 * density;
                    s = p.from.width() + (s - p.from.width()) * e; turn *= e; alpha = 1f;
                }
                if (state == 1) { float e = STANDARD.getInterpolation(end); x += (cx - x) * e; y += (cy - y) * e; s *= 1f - .6f * e; alpha *= 1f - e; }
                if (state == 2) {
                    float e = EMPHASIZED_DECELERATE.getInterpolation(end);
                    int[] me = new int[2]; getLocationInWindow(me);
                    x += (p.from.centerX() - me[0] - x) * e; y += (p.from.centerY() - me[1] - y) * e; s += (p.from.width() - s) * e; turn *= 1f - e; alpha = alpha + (1f - alpha) * e;
                }
                if (alpha > .01f) draw(c, p.icon, x, y, s, turn, alpha);
            }
        }

        private void flight(Canvas c, long now, int n, float card) {
            float lift = 34 * density;
            for (int i = 0; i < n; i++) {
                Pick p = picks.get(i); int order = n - 1 - i;
                float t = (now - stateAt - delayOf(order)) / (float) FLIGHT;
                if (t >= 1f) { if (p.landed == 0) p.landed = now; continue; }
                int depth = order;
                float rest = (depth % 2 == 0 ? 5f : -4f) * (depth + 1) * .6f;
                if (t <= 0f) {
                    if (depth >= 3) continue;
                    draw(c, p.icon, p.sx, p.sy, card, rest, 1f - depth * .12f);
                    continue;
                }
                float e = STANDARD.getInterpolation(t);
                float x = p.sx + (flyX - p.sx) * e;
                float y = p.sy + (flyY - p.sy) * e - (float) Math.sin(Math.PI * e) * lift * (1f - Math.min(depth, 4) * .12f);
                float s = card * (1f - .84f * e);
                float alpha = t < .65f ? 1f : 1f - (t - .65f) / .35f;
                float turn = rest * (1f - e) + (order % 2 == 0 ? 18f : -18f) * e;
                if (depth >= 3) alpha *= Math.min(1f, t * 4f);
                draw(c, p.icon, x, y, s, turn, alpha);
            }
            for (int i = 0; i < n; i++) {
                Pick p = picks.get(i); if (p.landed == 0) continue;
                float r = (now - p.landed) / (float) RIPPLE; if (r >= 1f) continue;
                float e = EMPHASIZED_DECELERATE.getInterpolation(r);
                ring.setAlpha(Math.round(110 * (1f - r)));
                c.drawCircle(flyX, flyY, (8 + 18 * e) * density, ring);
            }
        }

        private void draw(Canvas c, Bitmap icon, float x, float y, float s, float turn, float alpha) {
            if (icon == null || icon.isRecycled()) return;
            c.save(); c.rotate(turn, x, y);
            shade.setColor(0xff000000); shade.setAlpha(Math.round(46 * alpha));
            c.drawOval(x - s * .42f, y + s * .30f, x + s * .42f, y + s * .52f, shade);
            paint.setAlpha(Math.round(255 * alpha));
            c.drawBitmap(icon, null, new RectF(x - s / 2f, y - s / 2f, x + s / 2f, y + s / 2f), paint);
            c.restore();
        }
    }

    static final class Badge extends Drawable {
        final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), ring = new Paint(Paint.ANTI_ALIAS_FLAG), text = new Paint(Paint.ANTI_ALIAS_FLAG);
        final float density; final Context context; int count, previous; long shownAt, changedAt, endAt;
        static float pop(float t) { float u = t - 1f, k = 2.2f; return u * u * ((k + 1) * u + k) + 1f; }
        Badge(Context c) {
            context = c; density = c.getResources().getDisplayMetrics().density;
            boolean dark = FrostedSurface.dark(c);
            fill.setColor(LauncherFeatures.surface(c, dark ? "system_accent1_200" : "system_accent1_600", dark ? 0xffaac7ff : 0xff3d5f99));
            ring.setColor(LauncherFeatures.surface(c, dark ? "system_neutral1_900" : "system_neutral1_10", dark ? 0xff1b1b1f : 0xfffdfcff));
            text.setColor(LauncherFeatures.surface(c, dark ? "system_accent1_800" : "system_accent1_0", dark ? 0xff0a305f : 0xffffffff));
            text.setTextSize(12.5f * density); text.setTextAlign(Paint.Align.CENTER);
            text.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        }
        void count(int n) { long now = SystemClock.uptimeMillis(); if (count == 0) shownAt = now; previous = count; count = n; changedAt = now; }
        void finish() { if (endAt == 0) endAt = SystemClock.uptimeMillis(); }
        float flyX, flyY; long flyAt;
        void fly(float x, float y) { if (flyAt != 0) return; flyX = x; flyY = y; flyAt = SystemClock.uptimeMillis(); }
        public void draw(Canvas c) {
            Activity a = LauncherFeatures.activity.get(); if (a == null || count < 2) return;
            View drag = dragView(a); if (drag == null && stack == null) return;
            float x, y;
            if (drag != null && endAt == 0 && flyAt == 0) {
                int[] at = new int[2]; drag.getLocationInWindow(at);
                float w = drag.getWidth() * drag.getScaleX();
                x = at[0] + w * .86f; y = at[1] + drag.getHeight() * drag.getScaleY() * .12f;
                lastX = x; lastY = y;
            } else { x = lastX; y = lastY; }
            long now = SystemClock.uptimeMillis();
            float appear = pop(Math.min(1f, (now - shownAt) / 320f));
            float pulse = (now - changedAt) < 240 && previous >= 2 ? 1f + .18f * (float) Math.sin(Math.PI * (now - changedAt) / 240f) : 1f;
            float vanish = endAt == 0 ? 1f : 1f - Math.min(1f, (now - endAt) / 160f);
            if (flyAt != 0) {
                float t = Math.min(1f, (now - flyAt) / 240f), e = STANDARD.getInterpolation(t);
                x += (flyX - x) * e * .5f; y += (flyY - y) * e * .5f;
                vanish = Math.min(vanish, 1f - e);
            }
            float scale = appear * pulse * vanish; if (scale <= .01f) return;
            String label = count > 99 ? "99+" : String.valueOf(count);
            float h = 22 * density, w = Math.max(h, text.measureText(label) + 14 * density);
            c.save(); c.translate(x, y); c.scale(scale, scale);
            c.drawRoundRect(new RectF(-w / 2 - 2 * density, -h / 2 - 2 * density, w / 2 + 2 * density, h / 2 + 2 * density), h, h, ring);
            c.drawRoundRect(new RectF(-w / 2, -h / 2, w / 2, h / 2), h / 2, h / 2, fill);
            float roll = previous >= 2 ? STANDARD.getInterpolation(Math.min(1f, (now - changedAt) / 220f)) : 1f;
            float base = -(text.descent() + text.ascent()) / 2f;
            c.clipRect(-w / 2, -h / 2, w / 2, h / 2);
            int alpha = text.getAlpha();
            if (roll < 1f) { text.setAlpha(Math.round(255 * (1f - roll))); c.drawText(previous > 99 ? "99+" : String.valueOf(previous), 0, base - roll * 9 * density, text); }
            text.setAlpha(Math.round(255 * roll)); c.drawText(label, 0, base + (1f - roll) * 9 * density, text); text.setAlpha(alpha);
            c.restore();
        }
        float lastX, lastY;
        public void setAlpha(int alpha) { }
        public void setColorFilter(ColorFilter filter) { }
        public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
