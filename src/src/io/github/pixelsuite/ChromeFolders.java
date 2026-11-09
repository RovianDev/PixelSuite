package io.github.pixelsuite;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.OvalShape;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.WeakHashMap;

final class ChromeFolders {
    private ChromeFolders() {}

    static final int MAX_SITES = 100000, COLS = 3, ROWS = 3, PER_PAGE = COLS * ROWS;
    private static final String PREFS = "pixelsuite_chrome_ui", KEY = "sd_folders_v1", TAG = "PixelSuite";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    static final class Site {
        String title, url;
        Site(String title, String url) { this.title = title; this.url = url; }
    }

    static final class Folder {
        int id, pos;
        boolean single;
        String name = "Folder";
        final ArrayList<Site> sites = new ArrayList<Site>();
    }

    private static ArrayList<Folder> sFolders;

    private static SharedPreferences prefs(Context c) { return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE); }

    static synchronized List<Folder> folders(Context c) {
        if (sFolders != null) return sFolders;
        sFolders = new ArrayList<Folder>();
        try {
            String json = prefs(c).getString(KEY, null);
            if (json != null) {
                JSONArray a = new JSONArray(json);
                for (int i = 0; i < a.length(); i++) {
                    JSONObject o = a.getJSONObject(i);
                    Folder f = new Folder();
                    f.id = o.optInt("id", i + 1);
                    f.pos = o.optInt("pos", 0);
                    f.name = o.optString("name", "Folder");
                    f.single = o.optBoolean("single", false);
                    JSONArray s = o.optJSONArray("sites");
                    if (s != null) for (int k = 0; k < s.length() && k < MAX_SITES; k++) {
                        JSONObject so = s.getJSONObject(k);
                        f.sites.add(new Site(so.optString("t", ""), so.optString("u", "")));
                    }
                    if (!f.sites.isEmpty()) sFolders.add(f);
                }
            }
        } catch (Throwable t) { }
        return sFolders;
    }

    static synchronized void save(Context c) {
        try {
            JSONArray a = new JSONArray();
            for (Folder f : folders(c)) {
                if (f.sites.isEmpty()) continue;
                JSONObject o = new JSONObject();
                o.put("id", f.id);
                o.put("pos", f.pos);
                o.put("name", f.name);
                if (f.single) o.put("single", true);
                JSONArray s = new JSONArray();
                for (Site x : f.sites) s.put(new JSONObject().put("t", x.title).put("u", x.url));
                o.put("sites", s);
                a.put(o);
            }
            prefs(c).edit().putString(KEY, a.toString()).apply();
            ChromeDialBookmarks.schedule();
        } catch (Throwable t) { }
    }

    private static Folder newFolder(Context c, int pos) {
        int id = 1;
        for (Folder f : folders(c)) id = Math.max(id, f.id + 1);
        Folder f = new Folder();
        f.id = id;
        f.pos = pos;
        folders(c).add(f);
        return f;
    }

    private static Folder byId(Context c, int id) { for (Folder f : folders(c)) if (f.id == id) return f; return null; }

    static boolean dark(Context c) {
        return (c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    static int iconSize(Context c) { return Math.round(47.5f * c.getResources().getDisplayMetrics().density); }
    private static int discColor(Context c) { return dark(c) ? 0xFF303134 : 0xFFE8EAED; }
    private static int folderColor(Context c) { return !dark(c) ? 0xFFDADCE0 : superDark() ? 0xFF262626 : 0xFF3C4043; }
    private static int panelColor(Context c) { return !dark(c) ? 0xFFF1F3F4 : superDark() ? 0xFF141414 : 0xFF2A2B2E; }

    private static boolean frosted(Context c) {
        try { return !(dark(c) && superDark()) && SurfaceStyle.get(c) == SurfaceStyle.FROSTED && FrostedSurface.blurEnabled(c); } catch (Throwable t) { return false; }
    }
    private static int cardColor(Context c) {
        if (dark(c) && superDark()) {
            int sheet = ChromeLayout.sheetColor(0xFF000000);
            return (Color.red(sheet) * 299 + Color.green(sheet) * 587 + Color.blue(sheet) * 114) / 1000 < 24 ? sheet | 0xFF000000 : 0xFF000000;
        }
        int style; try { style = SurfaceStyle.get(c); } catch (Throwable t) { style = SurfaceStyle.STOCK; }
        if (frosted(c)) return (panelColor(c) & 0xFFFFFF) | (dark(c) ? 0xB8000000 : 0xC4000000);
        if (style == SurfaceStyle.OFF) return dark(c) ? 0xFF202124 : 0xFFFFFFFF;
        return panelColor(c);
    }

    private static final class CardBg extends android.graphics.drawable.Drawable {
        final RectF rect = new RectF(); float radius; int color;
        int rim; float rimShow; GlassRim glass; float rimWidth = 1f;
        private final android.graphics.Paint edge = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private android.graphics.drawable.Drawable blur;
        CardBg(int color) { this.color = color; }
        void blur(View on, int radiusPx) {
            if (blur != null) return;
            try {
                Object root = LauncherFeatures.call(on, "getViewRootImpl");
                blur = (android.graphics.drawable.Drawable) LauncherFeatures.call(root, "createBackgroundBlurDrawable");
                LauncherFeatures.call(blur, "setBlurRadius", radiusPx);
                try { LauncherFeatures.call(blur, "setColor", Color.TRANSPARENT); } catch (Throwable ignored) { }
            } catch (Throwable unavailable) { blur = null; }
        }
        @Override public void draw(android.graphics.Canvas c) {
            if (rect.isEmpty()) rect.set(getBounds());
            if (blur != null) {
                try {
                    blur.setBounds(Math.round(rect.left), Math.round(rect.top), Math.round(rect.right), Math.round(rect.bottom));
                    LauncherFeatures.call(blur, "setCornerRadius", radius);
                    blur.draw(c);
                } catch (Throwable broken) { blur = null; }
            }
            paint.setColor(blur != null ? color : color | 0xFF000000);
            c.drawRoundRect(rect, radius, radius, paint);
            if (rimShow > 0f) {
                if (glass != null) glass.draw(c, rect.left, rect.top, rect.right, rect.bottom, radius, Math.round(255 * rimShow));
                else if (rim != 0) {
                    edge.setStyle(android.graphics.Paint.Style.STROKE); edge.setStrokeWidth(rimWidth);
                    edge.setColor(rim); edge.setAlpha(Math.round(Color.alpha(rim) * rimShow));
                    float h = rimWidth / 2f;
                    c.drawRoundRect(rect.left + h, rect.top + h, rect.right - h, rect.bottom - h, Math.max(0f, radius - h), Math.max(0f, radius - h), edge);
                }
            }
        }
        boolean see() { return blur != null; }
        @Override public void setAlpha(int a) { }
        @Override public void setColorFilter(android.graphics.ColorFilter f) { }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }

    private static boolean superDark() {
        try { if (SuperDark.inEffectNow()) return true; } catch (Throwable ignored) { }
        try {
            int s = ChromeLayout.sheetColor(0);
            if (s == 0) return false;
            return (Color.red(s) * 299 + Color.green(s) * 587 + Color.blue(s) * 114) / 1000 < 16;
        } catch (Throwable t) { return false; }
    }

    private static final ViewOutlineProvider OVAL = new ViewOutlineProvider() {
        @Override public void getOutline(View v, Outline o) { o.setOval(0, 0, v.getWidth(), v.getHeight()); }
    };

    private static final WeakHashMap<ImageView, Drawable> sStyled = new WeakHashMap<ImageView, Drawable>();

    static int sLabelRoom;

    private static void wideTitle(View tile) {
        TextView t = title(tile);
        if (t == null || !(tile.getParent() instanceof ViewGroup) || !(t.getParent() instanceof ViewGroup)) return;
        ViewGroup g = (ViewGroup) tile.getParent(), box = (ViewGroup) t.getParent();
        int avail = g.getWidth() - g.getPaddingLeft() - g.getPaddingRight();
        if (avail <= 0) return;
        int room = avail / ChromeSpeedDial.COLUMNS - Math.round(4 * tile.getResources().getDisplayMetrics().density);
        if (room <= 0) return;
        sLabelRoom = room;
        if (g.getClipChildren()) g.setClipChildren(false);
        for (View v = t.getParent() instanceof View ? (View) t.getParent() : null; v != null && v != g; v = v.getParent() instanceof View ? (View) v.getParent() : null) {
            if (v instanceof ViewGroup && ((ViewGroup) v).getClipChildren()) ((ViewGroup) v).setClipChildren(false);
        }
        ViewGroup.LayoutParams lp = t.getLayoutParams();
        if (!(lp instanceof ViewGroup.MarginLayoutParams) || lp.width == room) return;
        int inner = box.getWidth() - box.getPaddingLeft() - box.getPaddingRight();
        if (inner <= 0 || room <= inner) return;
        ViewGroup.MarginLayoutParams m = (ViewGroup.MarginLayoutParams) lp;
        int side = -(room - inner) / 2;
        m.width = room;
        m.leftMargin = side;
        m.rightMargin = side;
        m.setMarginStart(side);
        m.setMarginEnd(side);
        t.setMaxWidth(room);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setLayoutParams(m);
    }

    static void style(View tile) {
        try { wideTitle(tile); } catch (Throwable ignored) { }
        ImageView icon = icon(tile);
        if (icon == null) return;
        Context c = tile.getContext();
        ChromeTileIcons.apply(tile, icon);
        int s = iconSize(c);
        ViewGroup.LayoutParams lp = icon.getLayoutParams();
        if (lp instanceof ViewGroup.MarginLayoutParams && (lp.width != s || lp.height != s)) {
            ViewGroup.MarginLayoutParams m = (ViewGroup.MarginLayoutParams) lp;
            int old = lp.height > 0 ? lp.height : s;
            m.topMargin = Math.max(0, m.topMargin + (old - s) / 2);
            lp.width = s;
            lp.height = s;
            icon.setLayoutParams(lp);
        }
        if (icon.getOutlineProvider() != OVAL) { icon.setOutlineProvider(OVAL); icon.setClipToOutline(true); }
        Drawable d = icon.getDrawable();
        if (sStyled.containsKey(icon) && sStyled.get(icon) == d) return;
        sStyled.put(icon, d);
        boolean full = d != null && fullBleed(d);
        int pad = full ? 0 : Math.round(s * 0.17f);
        icon.setPadding(pad, pad, pad, pad);
        icon.setScaleType(full ? ImageView.ScaleType.CENTER_CROP : ImageView.ScaleType.FIT_CENTER);
        ShapeDrawable disc = new ShapeDrawable(new OvalShape());
        disc.getPaint().setColor(discColor(c));
        icon.setBackground(disc);
    }

    private static boolean fullBleed(Drawable d) {
        try {
            Bitmap b = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            Drawable copy = d.getConstantState() != null ? d.getConstantState().newDrawable().mutate() : null;
            if (copy != null) { copy.setBounds(0, 0, 24, 24); copy.draw(c); }
            else { Rect was = d.copyBounds(); d.setBounds(0, 0, 24, 24); d.draw(c); d.setBounds(was); }
            return fullBleed(b);
        } catch (Throwable t) { return false; }
    }

    private static boolean fullBleed(Bitmap b) {
        int w = b.getWidth(), h = b.getHeight();
        int[][] at = {{20, 20}, {80, 20}, {20, 80}, {80, 80}};
        for (int[] p : at) if (Color.alpha(b.getPixel(Math.min(w - 1, w * p[0] / 100), Math.min(h - 1, h * p[1] / 100))) < 200) return false;
        return true;
    }

    private static int sIconId, sTitleId;

    static ImageView icon(View tile) {
        if (sIconId == 0) sIconId = id(tile, "tile_view_icon");
        View v = sIconId > 0 ? tile.findViewById(sIconId) : null;
        return v instanceof ImageView ? (ImageView) v : null;
    }

    static TextView title(View tile) {
        if (sTitleId == 0) sTitleId = id(tile, "tile_view_title");
        View v = sTitleId > 0 ? tile.findViewById(sTitleId) : null;
        return v instanceof TextView ? (TextView) v : null;
    }

    private static int id(View v, String name) {
        try { int r = v.getResources().getIdentifier(name, "id", v.getContext().getPackageName()); return r == 0 ? -1 : r; } catch (Throwable t) { return -1; }
    }

    static int sTileW, sTileH, sIconTop = -1, sTitleBase = -1, sTitleW, sTitleColor = 0xFF5F6368;
    static float sTitleSize;
    static Typeface sTitleFace;

    static void reference(View tile) {
        if (tile.getMeasuredWidth() <= 0) return;
        sTileW = tile.getMeasuredWidth();
        sTileH = tile.getMeasuredHeight();
        ImageView ic = icon(tile);
        TextView t = title(tile);
        int iconTop = sIconTop, base = sTitleBase;
        if (ic != null && ic.getHeight() > 0) sIconTop = topIn(ic, tile);
        if (t != null && t.getHeight() > 0 && t.getBaseline() > 0) {
            sTitleBase = topIn(t, tile) + t.getBaseline();
            sTitleW = titleRoom(t, tile);
            sTitleColor = t.getCurrentTextColor();
            sTitleSize = t.getTextSize();
            sTitleFace = t.getTypeface();
        }
        if (iconTop != sIconTop || base != sTitleBase) {
            for (HashMap<Integer, FolderTile> views : sViews.values()) for (FolderTile f : views.values()) f.invalidate();
        }
    }

    static int titleRoom(TextView t, View tile) {
        int w = t.getWidth();
        try {
            int room = tile.getWidth() - tile.getPaddingLeft() - tile.getPaddingRight();
            if (tile.getParent() instanceof android.view.ViewGroup) {
                android.view.ViewGroup g = (android.view.ViewGroup) tile.getParent();
                int pitch = 0;
                for (int i = 0; i + 1 < g.getChildCount() && pitch <= 0; i++) {
                    View a = g.getChildAt(i), b = g.getChildAt(i + 1);
                    if (a.getVisibility() == View.VISIBLE && b.getVisibility() == View.VISIBLE && Math.abs(a.getTop() - b.getTop()) < 2)
                        pitch = Math.abs(b.getLeft() - a.getLeft());
                }
                if (pitch > 0) room = Math.max(room, pitch - Math.round(4 * tile.getResources().getDisplayMetrics().density));
            }
            int max = t.getMaxWidth();
            if (max > 0 && max < Integer.MAX_VALUE) room = Math.min(room, max);
            if (room > w) w = room;
        } catch (Throwable ignored) { }
        return Math.max(0, w - t.getCompoundPaddingLeft() - t.getCompoundPaddingRight());
    }

    static int topIn(View child, View tile) {
        int y = 0;
        View v = child;
        while (v != null && v != tile) {
            y += v.getTop();
            v = v.getParent() instanceof View ? (View) v.getParent() : null;
        }
        return v == tile ? y : child.getTop();
    }

    static float iconTopIn(View v) {
        if (!(v instanceof FolderTile)) {
            ImageView ic = icon(v);
            if (ic != null && ic.getHeight() > 0) return topIn(ic, v);
        }
        return sIconTop >= 0 ? sIconTop : 11 * v.getResources().getDisplayMetrics().density;
    }

    private static final HashMap<String, Bitmap> sIcons = new HashMap<String, Bitmap>();
    private static final HashMap<String, Boolean> sFull = new HashMap<String, Boolean>();

    private static String hash(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-1").digest(s.getBytes("UTF-8"));
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < 10; i++) b.append(String.format("%02x", d[i] & 0xff));
            return b.toString();
        } catch (Throwable t) { return Integer.toHexString(s.hashCode()); }
    }

    private static File iconFile(Context c, String url) {
        File dir = new File(c.getFilesDir(), "pixelsuite_speed_dial");
        if (!dir.isDirectory()) dir.mkdirs();
        return new File(dir, hash(url) + ".png");
    }

    private static void keepIcon(View tile, String url) {
        try {
            ImageView ic = icon(tile);
            Drawable d = ic == null ? null : ic.getDrawable();
            if (d == null) return;
            Bitmap b = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            Drawable copy = d.getConstantState() != null ? d.getConstantState().newDrawable(tile.getResources()).mutate() : null;
            Drawable use = copy != null ? copy : d;
            Rect was = use.copyBounds();
            if (ic.getImageTintList() != null) use.setTintList(ic.getImageTintList());
            use.setBounds(0, 0, 96, 96);
            use.draw(c);
            if (use == d) d.setBounds(was);
            sIcons.put(url, b);
            sFull.put(url, fullBleed(b));
            FileOutputStream out = new FileOutputStream(iconFile(tile.getContext(), url));
            try { b.compress(Bitmap.CompressFormat.PNG, 100, out); } finally { out.close(); }
        } catch (Throwable t) { }
    }

    static Bitmap folderIcon(Context c, String url) { return iconOf(c, url); }

    private static Bitmap iconOf(Context c, String url) {
        Bitmap b = sIcons.get(url);
        if (b != null || sIcons.containsKey(url)) return b;
        try {
            File f = iconFile(c, url);
            b = f.isFile() ? BitmapFactory.decodeFile(f.getPath()) : null;
        } catch (Throwable t) { b = null; }
        sIcons.put(url, b);
        if (b != null) sFull.put(url, fullBleed(b));
        return b;
    }

    private static void drawSite(Canvas c, Context ctx, Site s, RectF r, Paint p) {
        p.setShader(null);
        p.setColor(discColor(ctx));
        p.setStyle(Paint.Style.FILL);
        c.drawOval(r, p);
        Bitmap mine = ChromeTileIcons.custom(ctx, s.url);
        Bitmap b = mine != null ? mine : iconOf(ctx, s.url);
        if (b != null) {
            boolean full = mine != null || Boolean.TRUE.equals(sFull.get(s.url));
            int save = c.save();
            Path clip = new Path();
            clip.addOval(r, Path.Direction.CW);
            c.clipPath(clip);
            RectF dst = new RectF(r);
            if (!full) dst.inset(r.width() * 0.17f, r.height() * 0.17f);
            c.drawBitmap(b, null, dst, p);
            c.restoreToCount(save);
        } else {
            String letter = s.title == null || s.title.isEmpty() ? "?" : s.title.substring(0, 1).toUpperCase();
            p.setColor(dark(ctx) ? 0xFFE8EAED : 0xFF3C4043);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(r.height() * 0.45f);
            p.setTypeface(Typeface.DEFAULT_BOLD);
            c.drawText(letter, r.centerX(), r.centerY() - (p.descent() + p.ascent()) / 2, p);
            p.setTypeface(Typeface.DEFAULT);
        }
    }

    private static void drawLabel(Canvas c, Context ctx, String text, int width, float iconBottom, TextPaint tp) { drawLabel(c, ctx, text, width, iconBottom, tp, 1f); }

    private static void drawLabel(Canvas c, Context ctx, String text, int width, float iconBottom, TextPaint tp, float alpha) { drawLabel(c, ctx, text, width, iconBottom, tp, alpha, -1); }

    private static void drawLabel(Canvas c, Context ctx, String text, int width, float iconBottom, TextPaint tp, float alpha, float room0) {
        float d = ctx.getResources().getDisplayMetrics().density;
        tp.setColor(sTitleColor);
        tp.setTextSize(sTitleSize > 0 ? sTitleSize : 12 * ctx.getResources().getDisplayMetrics().scaledDensity);
        tp.setTypeface(sTitleFace != null ? sTitleFace : Typeface.DEFAULT);
        tp.setTextAlign(Paint.Align.CENTER);
        if (alpha < 1f) tp.setAlpha(Math.round(Color.alpha(sTitleColor) * Math.max(0f, alpha)));
        float room = room0 > 0 ? room0 : sTitleW > 0 ? sTitleW : width - 8 * d;
        room = Math.min(room, room0 > 0 ? width - 4 * d : Math.max(width - 4 * d, sLabelRoom));
        CharSequence t = TextUtils.ellipsize(text == null ? "" : text, tp, room, TextUtils.TruncateAt.END);
        float base = sTitleBase > 0 ? sTitleBase : iconBottom + 8 * d - tp.ascent();
        c.drawText(t, 0, t.length(), width / 2f, base, tp);
    }

    static final class FolderTile extends View {
        final int id;
        boolean iconHidden;
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        final TextPaint text = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        float pop = 1f;

        FolderTile(Context c, int id) {
            super(c);
            this.id = id;
            setClickable(true);
            setLongClickable(true);
            setOnTouchListener(new TileDrag(null));
            setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
                Folder f = ((FolderTile) v).folder();
                if (f != null && f.single && !f.sites.isEmpty() && v.getParent() instanceof ViewGroup) { openSite((ViewGroup) v.getParent(), f.sites.get(0), false); return; }
                open((FolderTile) v);
            } });
            setOnLongClickListener(new View.OnLongClickListener() { public boolean onLongClick(View v) { menu((FolderTile) v); return true; } });
            setHapticFeedbackEnabled(true);
        }

        Folder folder() { return byId(getContext(), id); }

        @Override protected void onMeasure(int w, int h) {
            float d = getResources().getDisplayMetrics().density;
            int tw = sTileW > 0 ? sTileW : Math.round(72 * d), th = sTileH > 0 ? sTileH : Math.round(96 * d);
            if (MeasureSpec.getMode(w) != MeasureSpec.UNSPECIFIED) tw = Math.min(tw, MeasureSpec.getSize(w));
            setMeasuredDimension(tw, th);
        }

        float iconCenterY() {
            int s = iconSize(getContext());
            int top = sIconTop >= 0 ? sIconTop : Math.round(11 * getResources().getDisplayMetrics().density);
            return top + s / 2f;
        }

        @Override protected void onDraw(Canvas c) {
            Folder f = folder();
            if (f == null) return;
            Context ctx = getContext();
            int s = iconSize(ctx);
            float cx = getWidth() / 2f, cy = iconCenterY();
            if (f.single && !f.sites.isEmpty()) {
                Site site = f.sites.get(0);
                if (!iconHidden) {
                    int save = c.save();
                    c.scale(pop, pop, cx, cy);
                    drawSite(c, ctx, site, new RectF(cx - s / 2f, cy - s / 2f, cx + s / 2f, cy + s / 2f), paint);
                    c.restoreToCount(save);
                }
                drawLabel(c, ctx, site.title, getWidth(), cy + s / 2f, text);
                return;
            }
            if (!iconHidden) {
                int save = c.save();
                c.scale(pop, pop, cx, cy);
                paint.setShader(null);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(folderColor(ctx));
                c.drawCircle(cx, cy, s / 2f, paint);
                int n = Math.min(4, f.sites.size());
                for (int i = 0; i < n; i++) {
                    float[] p = spot(i, n, s);
                    float m = p[2];
                    drawSite(c, ctx, f.sites.get(i), new RectF(cx + p[0] - m / 2, cy + p[1] - m / 2, cx + p[0] + m / 2, cy + p[1] + m / 2), paint);
                }
                c.restoreToCount(save);
            }
            drawLabel(c, ctx, f.name, getWidth(), cy + s / 2f, text);
        }

        void popIn() {
            ValueAnimator a = ValueAnimator.ofFloat(0.55f, 1f);
            a.setDuration(360);
            a.setInterpolator(ChromeTabTransition.SPRING);
            a.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() { public void onAnimationUpdate(ValueAnimator v) { pop = (Float) v.getAnimatedValue(); invalidate(); } });
            a.start();
        }
    }

    static float[] spot(int i, int n, float s) {
        n = Math.max(1, Math.min(4, n));
        if (i >= n) return new float[]{0f, 0f, s * 0.2f};
        float m = s * 0.38f, o = s * 0.42f;
        int rows = (n + 1) / 2, row = i / 2, inRow = Math.min(2, n - row * 2), col = i % 2;
        float x = (col - (inRow - 1) / 2f) * o, y = (row - (rows - 1) / 2f) * o;
        return new float[]{x, y, m};
    }

    static boolean isFolder(View v) { return v instanceof FolderTile; }

    static boolean canTake(View v) {
        if (!(v instanceof FolderTile)) return false;
        Folder f = ((FolderTile) v).folder();
        return f != null && f.sites.size() < MAX_SITES;
    }

    static int folderPos(View v) {
        Folder f = v instanceof FolderTile ? ((FolderTile) v).folder() : null;
        return f == null ? Integer.MAX_VALUE : f.pos;
    }

    private static final WeakHashMap<ViewGroup, HashMap<Integer, FolderTile>> sViews = new WeakHashMap<ViewGroup, HashMap<Integer, FolderTile>>();

    static void ensure(ViewGroup grid) {
        if (!ChromeSpeedDial.on(grid, true)) return;
        List<Folder> list = folders(grid.getContext());
        HashMap<Integer, FolderTile> views = sViews.get(grid);
        if (views == null) { views = new HashMap<Integer, FolderTile>(); sViews.put(grid, views); }
        for (int i = grid.getChildCount() - 1; i >= 0; i--) {
            View c = grid.getChildAt(i);
            if (c instanceof FolderTile && byId(grid.getContext(), ((FolderTile) c).id) == null) grid.removeView(c);
        }
        for (Folder f : list) {
            FolderTile v = views.get(f.id);
            if (v == null) { v = new FolderTile(grid.getContext(), f.id); views.put(f.id, v); }
            if (v.getParent() != grid) {
                if (v.getParent() instanceof ViewGroup) ((ViewGroup) v.getParent()).removeView(v);
                grid.addView(v);
            }
            v.invalidate();
        }
    }

    static void ensureLater(final ViewGroup grid) {
        MAIN.post(new Runnable() { public void run() { try { ensure(grid); } catch (Throwable ignored) { } } });
    }

    static void positions(ViewGroup grid, List<View> order) {
        boolean changed = false;
        for (int i = 0; i < order.size(); i++) {
            View v = order.get(i);
            if (!(v instanceof FolderTile)) continue;
            Folder f = ((FolderTile) v).folder();
            if (f != null && f.pos != i) { f.pos = i; changed = true; }
        }
        if (changed) { save(grid.getContext()); grid.requestLayout(); }
    }

    private static final WeakHashMap<View, Long> sLeaving = new WeakHashMap<View, Long>();

    static boolean leaving(View v) {
        Long at = sLeaving.get(v);
        if (at == null) return false;
        if (android.os.SystemClock.uptimeMillis() - at > 3000) { sLeaving.remove(v); v.setAlpha(1f); return false; }
        return true;
    }

    private static final WeakHashMap<View, WeakReference<Object>> sGroups = new WeakHashMap<View, WeakReference<Object>>();

    static void group(Object bridge) {
        try {
            Object group = null;
            for (Field f : fields(bridge.getClass())) { Object o = f.get(bridge); if (o != null && !(o instanceof Number)) { group = o; break; } }
            if (group == null) return;
            for (Field f : fields(group.getClass())) {
                Object holder = f.get(group);
                if (holder == null) continue;
                for (Field g : fields(holder.getClass())) {
                    if (!g.getType().getName().endsWith("MostVisitedTilesLayout")) continue;
                    Object layout = g.get(holder);
                    if (layout instanceof View) sGroups.put((View) layout, new WeakReference<Object>(group));
                    return;
                }
            }
        } catch (Throwable ignored) { }
    }

    private static Object groupOf(View grid) {
        WeakReference<Object> r = sGroups.get(grid);
        return r == null ? null : r.get();
    }

    private static Object sites(Object group) throws Exception {
        for (Field f : fields(group.getClass())) {
            Object o = f.get(group);
            if (o == null) continue;
            for (Field g : fields(o.getClass())) if (g.getType().getName().endsWith("MostVisitedSitesBridge")) return o;
        }
        return null;
    }

    private static long bridgePointer(Object sites) throws Exception {
        for (Field g : fields(sites.getClass())) {
            if (!g.getType().getName().endsWith("MostVisitedSitesBridge")) continue;
            Object bridge = g.get(sites);
            if (bridge == null) return 0;
            for (Class<?> k = bridge.getClass(); k != null && k != Object.class; k = k.getSuperclass())
                for (Field f : k.getDeclaredFields()) if (f.getType() == long.class && !Modifier.isStatic(f.getModifiers())) { f.setAccessible(true); return f.getLong(bridge); }
        }
        return 0;
    }

    private static Object suggestion(View tile) {
        try {
            for (Class<?> k = tile.getClass(); k != null && k != View.class; k = k.getSuperclass())
                for (Field f : k.getDeclaredFields()) if (f.getType().getName().endsWith(".SiteSuggestion")) { f.setAccessible(true); return f.get(tile); }
        } catch (Throwable ignored) { }
        return null;
    }

    static Site siteOf(Object suggestion) {
        try { String u = spec(gurlOf(suggestion)); return u == null ? null : new Site(titleOf(suggestion), u); } catch (Throwable t) { return null; }
    }

    private static Object gurlOf(Object suggestion) throws Exception {
        for (Field f : fields(suggestion.getClass())) if (f.getType().getName().equals("org.chromium.url.GURL")) return f.get(suggestion);
        return null;
    }

    private static String titleOf(Object suggestion) throws Exception {
        for (Field f : fields(suggestion.getClass())) if (f.getType() == String.class) return (String) f.get(suggestion);
        return "";
    }

    private static String spec(Object gurl) throws Exception {
        if (gurl == null) return null;
        for (Class<?> k = gurl.getClass(); k != null && k != Object.class; k = k.getSuperclass())
            for (Field f : k.getDeclaredFields()) if (f.getType() == String.class && !Modifier.isStatic(f.getModifiers())) { f.setAccessible(true); return (String) f.get(gurl); }
        return null;
    }

    private static Object gurl(ClassLoader l, String url) throws Exception {
        Class<?> k = Class.forName("org.chromium.url.GURL", false, l);
        Constructor<?> c = k.getConstructor(String.class);
        return c.newInstance(url);
    }

    private static int sDeleteId = -1;
    private static boolean sLooking, sLooked;

    static void prepare(final Object handler) {
        if (handler == null || sDeleteId >= 0 || sLooking) return;
        final Context app = appContext();
        if (app == null) return;
        final String key = "sd_delete_" + version(app) + "_" + handler.getClass().getName();
        int cached = prefs(app).getInt(key, -2);
        if (cached >= 0) { sDeleteId = cached; sLooked = true; return; }
        if (cached == -1) { sLooked = true; return; }
        sLooking = true;
        new Thread(new Runnable() {
            public void run() {
                int id = -1;
                try { id = DexPeek.constBeforeCall(app, handler.getClass().getName(), "LJ/N;", "ZJO", "(IJLjava/lang/Object;)Z"); } catch (Throwable t) { }
                final int found = id;
                MAIN.post(new Runnable() { public void run() {
                    sLooking = false;
                    sLooked = true;
                    sDeleteId = found;
                    prefs(app).edit().putInt(key, found).apply();
                }});
            }
        }, "PixelSuite-speed-dial").start();
    }

    private static int version(Context c) {
        try { return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionCode; } catch (Throwable t) { return 0; }
    }

    interface Pick { void picked(int item); }

    static PopupWindow menuAbove(final View anchor, String[] items, final Pick pick) {
        Context c = anchor.getContext();
        float d = c.getResources().getDisplayMetrics().density;
        boolean dark = dark(c);
        final PopupWindow w = new PopupWindow(c);
        final LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, Math.round(6 * d), 0, Math.round(6 * d));
        for (int i = 0; i < items.length; i++) {
            final int which = i;
            TextView t = new TextView(c);
            t.setText(I18n.cs(items[i]));
            t.setTextSize(15);
            t.setTypeface(I18n.medium());
            t.setTextColor(dark ? 0xFFE8EAED : 0xFF202124);
            t.setSingleLine(true);
            t.setMinWidth(Math.round(160 * d));
            t.setPadding(Math.round(20 * d), Math.round(13 * d), Math.round(28 * d), Math.round(13 * d));
            t.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(dark ? 0x33FFFFFF : 0x22000000), null, new ColorDrawable(Color.WHITE)));
            t.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { w.dismiss(); pick.picked(which); } });
            box.addView(t, new LinearLayout.LayoutParams(-1, -2));
        }
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(dark ? 0xFF303134 : 0xFFFFFFFF);
        bg.setCornerRadius(16 * d);
        w.setBackgroundDrawable(bg);
        w.setElevation(8 * d);
        w.setContentView(box);
        w.setWidth(ViewGroup.LayoutParams.WRAP_CONTENT);
        w.setHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        w.setFocusable(true);
        w.setOutsideTouchable(true);
        w.setAnimationStyle(0);
        box.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int mw = box.getMeasuredWidth(), mh = box.getMeasuredHeight();
        int[] at = new int[2];
        anchor.getLocationOnScreen(at);
        int screenW = c.getResources().getDisplayMetrics().widthPixels;
        int top = at[1] + Math.round(iconTopIn(anchor));
        int x = Math.max(Math.round(8 * d), Math.min(screenW - mw - Math.round(8 * d), at[0] + anchor.getWidth() / 2 - mw / 2));
        int y = top - mh - Math.round(12 * d);
        boolean above = y >= Math.round(40 * d);
        if (!above) y = at[1] + anchor.getHeight() + Math.round(8 * d);
        box.setAlpha(0f);
        box.setScaleX(0.92f);
        box.setScaleY(0.92f);
        box.setPivotX(at[0] + anchor.getWidth() / 2f - x);
        box.setPivotY(above ? mh : 0);
        w.showAtLocation(anchor, Gravity.NO_GRAVITY, x, y);
        box.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(160).setInterpolator(ChromeTabTransition.DECELERATE).start();
        return w;
    }

    private static Context appContext() {
        try { return (Context) Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null); } catch (Throwable t) { return null; }
    }

    private static boolean delete(ViewGroup grid, View tile) {
        try {
            if (sDeleteId < 0) {
                Object h = TileDrag.handlerOf(tile);
                if (h != null && !sLooking && !sLooked) {
                    Context app = appContext();
                    sLooked = true;
                    sDeleteId = DexPeek.constBeforeCall(app, h.getClass().getName(), "LJ/N;", "ZJO", "(IJLjava/lang/Object;)Z");
                    if (app != null) prefs(app).edit().putInt("sd_delete_" + version(app) + "_" + h.getClass().getName(), sDeleteId).apply();
                }
            }
            if (sDeleteId < 0) return false;
            Object group = groupOf(grid);
            Object sites = group == null ? null : sites(group);
            long ptr = sites == null ? 0 : bridgePointer(sites);
            Object s = suggestion(tile);
            Object url = s == null ? null : gurlOf(s);
            if (ptr == 0 || url == null) return false;
            Method m = Class.forName("J.N", false, tile.getClass().getClassLoader()).getDeclaredMethod("ZJO", int.class, long.class, Object.class);
            m.setAccessible(true);
            Object ok = m.invoke(null, sDeleteId, ptr, url);
            return Boolean.TRUE.equals(ok);
        } catch (Throwable t) {
            return false;
        }
    }

    private static void openSite(ViewGroup grid, Site s, boolean newTab) {
        try {
            Object group = groupOf(grid);
            Object sites = group == null ? null : sites(group);
            if (sites != null) {
                ClassLoader l = grid.getClass().getClassLoader();
                Class<?> bridge = Class.forName("org.chromium.chrome.browser.suggestions.mostvisited.MostVisitedSitesBridge", false, l);
                Class<?> gurlClass = Class.forName("org.chromium.url.GURL", false, l);
                Method make = bridge.getDeclaredMethod("makeSiteSuggestion", String.class, gurlClass, int.class, int.class, int.class);
                Object suggestion = make.invoke(null, s.title, gurl(l, s.url), 0, 3, 1);
                for (Class<?> k = sites.getClass(); k != null && k != Object.class; k = k.getSuperclass()) {
                    for (Method m : k.getDeclaredMethods()) {
                        Class<?>[] p = m.getParameterTypes();
                        if (p.length != 2 || p[0] != int.class || m.getReturnType() != void.class || Modifier.isStatic(m.getModifiers())) continue;
                        Field sf = null;
                        for (Field f : fields(p[1])) if (f.getType() == suggestion.getClass()) { sf = f; break; }
                        if (sf == null) continue;
                        Constructor<?> c = p[1].getDeclaredConstructor();
                        c.setAccessible(true);
                        Object tile = c.newInstance();
                        sf.set(tile, suggestion);
                        m.setAccessible(true);
                        m.invoke(sites, newTab ? 4 : 1, tile);
                        return;
                    }
                }
            }
        } catch (Throwable t) { }
        try {
            Context c = grid.getContext();
            c.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(s.url)).setPackage(c.getPackageName()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Throwable ignored) { }
    }

    private static boolean addBack(ViewGroup grid, Site s, Integer pos) {
        try {
            Object group = groupOf(grid);
            if (group == null) { toast(grid.getContext(), "Couldn't add it to the speed dial"); return false; }
            ClassLoader l = grid.getClass().getClassLoader();
            Class<?> gurlClass = Class.forName("org.chromium.url.GURL", false, l);
            String title = s.title == null || s.title.trim().isEmpty() ? Uri.parse(s.url).getHost() : s.title.trim();
            if (title == null) title = s.url;
            if (title.length() > 50) title = title.substring(0, 50);
            for (Field f : fields(group.getClass())) {
                Object o = f.get(group);
                if (o == null) continue;
                for (Class<?> k = o.getClass(); k != null && k != Object.class; k = k.getSuperclass()) {
                    for (Method m : k.getDeclaredMethods()) {
                        Class<?>[] p = m.getParameterTypes();
                        if (m.getReturnType() != boolean.class || p.length != 3 || p[0] != String.class || p[1] != gurlClass || p[2] != Integer.class) continue;
                        m.setAccessible(true);
                        boolean ok = Boolean.TRUE.equals(m.invoke(o, title, gurl(l, s.url), pos));
                        if (!ok) toast(grid.getContext(), "Chrome didn't take the shortcut (limit reached?)");
                        return ok;
                    }
                }
            }
        } catch (Throwable t) { }
        return false;
    }

    private static int items(ViewGroup grid) {
        int n = 0;
        for (View v : ChromeSpeedDial.order(grid)) if (v instanceof FolderTile || TileDrag.pinned(v)) n++;
        return n;
    }

    static boolean merge(final ViewGroup grid, final View dragged, final View target) {
        Context c = grid.getContext();
        try {
            Object ds = suggestion(dragged);
            if (ds == null) return false;
            Site moved = new Site(titleOf(ds), spec(gurlOf(ds)));
            if (moved.url == null) return false;
            if (target instanceof FolderTile) {
                final FolderTile ft = (FolderTile) target;
                Folder f = ft.folder();
                if (f == null) return false;
                if (f.sites.size() >= MAX_SITES) { toast(c, "This folder is full (" + MAX_SITES + " websites)"); return false; }
                if (f.single) { f.single = false; f.name = "Folder"; }
                keepIcon(dragged, moved.url);
                List<View> after = new ArrayList<View>(ChromeSpeedDial.order(grid));
                if (!delete(grid, dragged)) { toast(c, "Couldn't move it into the folder"); return false; }
                f.sites.add(moved);
                after.remove(dragged);
                renumber(after);
                save(c);
                sLeaving.put(dragged, android.os.SystemClock.uptimeMillis());
                dragged.setAlpha(0f);
                ft.invalidate();
                ft.popIn();
                grid.requestLayout();
                return true;
            }
            Object ts = suggestion(target);
            if (ts == null) return false;
            Site first = new Site(titleOf(ts), spec(gurlOf(ts)));
            if (first.url == null) return false;
            List<View> after = new ArrayList<View>(ChromeSpeedDial.order(grid));
            keepIcon(target, first.url);
            keepIcon(dragged, moved.url);
            if (!delete(grid, target)) { toast(c, "Couldn't make a folder in this Chrome version"); return false; }
            boolean both = delete(grid, dragged);
            if (both) after.remove(dragged);
            int at = Math.max(0, after.indexOf(target));
            renumber(after);
            Folder f = newFolder(c, at);
            f.sites.add(first);
            if (both) f.sites.add(moved);
            save(c);
            long now = android.os.SystemClock.uptimeMillis();
            sLeaving.put(target, now);
            target.setAlpha(0f);
            if (both) { sLeaving.put(dragged, now); dragged.setAlpha(0f); }
            ensure(grid);
            HashMap<Integer, FolderTile> views = sViews.get(grid);
            FolderTile ft = views == null ? null : views.get(f.id);
            if (ft != null) ft.popIn();
            grid.requestLayout();
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static void renumber(List<View> order) {
        for (int i = 0; i < order.size(); i++) {
            View v = order.get(i);
            Folder f = v instanceof FolderTile ? ((FolderTile) v).folder() : null;
            if (f != null) f.pos = i;
        }
    }

    static boolean addSingle(ViewGroup grid, String title, Object gurl, Integer pos) {
        try {
            Context c = grid.getContext();
            String url = spec(gurl);
            if (url == null || url.isEmpty()) return false;
            String k = ChromeTileIcons.key(url);
            for (View v : ChromeSpeedDial.order(grid)) {
                String u = v instanceof FolderTile ? null : ChromeTileIcons.urlOf(v);
                if (u != null && ChromeTileIcons.key(u).equals(k)) return false;
            }
            for (Folder f : folders(c)) for (Site x : f.sites) if (ChromeTileIcons.key(x.url).equals(k)) return false;
            int at = pos != null ? pos : items(grid);
            for (Folder f : folders(c)) if (f.pos >= at) f.pos++;
            Folder f = newFolder(c, at);
            f.single = true;
            String t = title == null || title.trim().isEmpty() ? Uri.parse(url).getHost() : title.trim();
            f.name = t == null ? url : t;
            f.sites.add(new Site(f.name, url));
            save(c);
            ensure(grid);
            HashMap<Integer, FolderTile> views = sViews.get(grid);
            FolderTile ft = views == null ? null : views.get(f.id);
            if (ft != null) ft.popIn();
            grid.requestLayout();
            return true;
        } catch (Throwable t) { return false; }
    }

    private static void drop(Context c, Folder gone, int putBack) {
        List<Folder> list = folders(c);
        list.remove(gone);
        for (Folder f : list) if (f.pos > gone.pos) f.pos = Math.max(0, f.pos + putBack - 1);
        save(c);
    }

    private static void toast(Context c, String s) {
        try { android.widget.Toast.makeText(c, I18n.cs(s), android.widget.Toast.LENGTH_SHORT).show(); } catch (Throwable ignored) { }
    }

    private static void menu(final FolderTile v) {
        final Folder f = v.folder();
        if (f == null || !(v.getParent() instanceof ViewGroup)) return;
        final ViewGroup grid = (ViewGroup) v.getParent();
        v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        if (f.single && !f.sites.isEmpty()) {
            final Site site = f.sites.get(0);
            menuAbove(v, new String[]{"Open in new tab", "Edit", "Remove"}, new Pick() {
                public void picked(int item) {
                    if (item == 0) openSite(grid, site, true);
                    else if (item == 1) edit(v.getContext(), site, new Runnable() { public void run() { save(v.getContext()); v.invalidate(); } });
                    else { drop(v.getContext(), f, 0); ensure(grid); grid.requestLayout(); TileAdd.ensure(grid); }
                }
            });
            return;
        }
        menuAbove(v, new String[]{"Rename", "Remove folder"}, new Pick() {
            public void picked(int item) {
                if (item == 0) rename(v.getContext(), f, new Runnable() { public void run() { v.invalidate(); } });
                else removeFolder(grid, v, f);
            }
        });
    }

    private static void edit(final Context c, final Site site, final Runnable done) {
        float d = c.getResources().getDisplayMetrics().density;
        final EditText name = new EditText(c), address = new EditText(c);
        name.setSingleLine(true);
        name.setHint(I18n.cs("Name"));
        name.setText(I18n.cs(site.title));
        name.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        address.setSingleLine(true);
        address.setHint(I18n.cs("URL"));
        address.setText(I18n.cs(site.url));
        address.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        address.setImeOptions(EditorInfo.IME_ACTION_DONE);
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(24 * d);
        box.setPadding(pad, pad / 2, pad, 0);
        final ChromeTileIcons.Header pic = ChromeTileIcons.header(c, site.url, name.getCurrentTextColor());
        box.addView(pic.view(c, name.getCurrentTextColor()));
        box.addView(name);
        box.addView(address);
        final AlertDialog dlg = new AlertDialog.Builder(c).setTitle(I18n.cs("Edit website")).setView(box)
                .setPositiveButton(I18n.cs("Save"), null).setNegativeButton(I18n.cs("Cancel"), null).create();
        final Runnable apply = new Runnable() { public void run() {
            String u = address.getText().toString().trim();
            if (u.isEmpty()) { address.setError(I18n.cs("Enter an address")); return; }
            if (!u.contains("://")) u = "https://" + u;
            Uri parsed = Uri.parse(u);
            if (parsed.getHost() == null || parsed.getHost().isEmpty()) { address.setError(I18n.cs("That isn't a web address")); return; }
            String n = name.getText().toString().trim();
            if (n.isEmpty()) n = parsed.getHost();
            if (n.length() > 50) n = n.substring(0, 50);
            String old = site.url;
            if (!u.equals(old)) {
                String oh = old == null ? null : Uri.parse(old).getHost();
                if (oh != null && oh.equalsIgnoreCase(parsed.getHost())) {
                    Bitmap b = iconOf(c, old);
                    if (b != null) {
                        sIcons.put(u, b);
                        sFull.put(u, fullBleed(b));
                        try { FileOutputStream out = new FileOutputStream(iconFile(c, u)); try { b.compress(Bitmap.CompressFormat.PNG, 100, out); } finally { out.close(); } } catch (Throwable ignored) { }
                    }
                }
            }
            site.title = n;
            site.url = u;
            pic.commit(u);
            if (done != null) done.run();
            dlg.dismiss();
        }};
        dlg.setOnShowListener(new android.content.DialogInterface.OnShowListener() {
            public void onShow(android.content.DialogInterface di) {
                dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() { public void onClick(View v) { apply.run(); } });
                name.requestFocus();
                Window w = dlg.getWindow();
                if (w != null) w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
            }
        });
        address.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            public boolean onEditorAction(TextView v, int a, KeyEvent k) { apply.run(); return true; }
        });
        dlg.show(); Rtl.dialog(dlg);
    }

    private static void rename(final Context c, final Folder f, final Runnable done) {
        final EditText e = new EditText(c);
        e.setSingleLine(true);
        e.setText(I18n.cs(f.name));
        e.setSelectAllOnFocus(true);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        e.setImeOptions(EditorInfo.IME_ACTION_DONE);
        FrameLayout box = new FrameLayout(c);
        int pad = Math.round(24 * c.getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(e);
        final AlertDialog d = new AlertDialog.Builder(c).setTitle(I18n.cs("Rename folder")).setView(box)
                .setPositiveButton(I18n.cs("Save"), null).setNegativeButton(I18n.cs("Cancel"), null).create();
        final Runnable apply = new Runnable() { public void run() {
            String n = e.getText().toString().trim();
            if (n.isEmpty()) n = "Folder";
            if (n.length() > 30) n = n.substring(0, 30);
            f.name = n;
            save(c);
            if (done != null) done.run();
            d.dismiss();
        }};
        d.setOnShowListener(new android.content.DialogInterface.OnShowListener() {
            public void onShow(android.content.DialogInterface di) {
                d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() { public void onClick(View v) { apply.run(); } });
                e.requestFocus();
                Window w = d.getWindow();
                if (w != null) w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
            }
        });
        e.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            public boolean onEditorAction(TextView v, int a, KeyEvent k) { apply.run(); return true; }
        });
        d.show(); Rtl.dialog(d);
    }

    private static void removeFolder(final ViewGroup grid, final FolderTile v, final Folder f) {
        final Context c = grid.getContext();
        int room = (ChromeSpeedDial.COLUMNS * ChromeSpeedDial.ROWS - 1) - (items(grid) - 1);
        if (room >= f.sites.size()) {
            AlertDialog d = new AlertDialog.Builder(c).setTitle(I18n.cs("Remove \"" + f.name + "\"?"))
                    .setMessage(I18n.cs(f.sites.size() == 1 ? "Its website goes back to the speed dial." : "Its " + f.sites.size() + " websites go back to the speed dial."))
                    .setPositiveButton(I18n.cs("Remove"), new android.content.DialogInterface.OnClickListener() {
                        public void onClick(android.content.DialogInterface di, int w) { unfolder(grid, v, f); }
                    }).setNegativeButton(I18n.cs("Cancel"), null).create();
            d.show(); Rtl.dialog(d);
            return;
        }
        Rtl.dialog(new AlertDialog.Builder(c).setTitle(I18n.cs("Remove \"" + f.name + "\"?"))
                .setMessage(I18n.cs("There's no room on the speed dial for its " + f.sites.size() + " websites, so they'll be deleted with the folder."))
                .setPositiveButton(I18n.cs("Delete"), new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface d, int w) { drop(c, f, 0); ensure(grid); grid.requestLayout(); }
                }).setNegativeButton(I18n.cs("Cancel"), null).show());
    }

    private static void unfolder(final ViewGroup grid, final FolderTile v, final Folder f) {
        final Context c = grid.getContext();
        {
            int at = 0;
            for (View x : ChromeSpeedDial.order(grid)) { if (x == v) break; if (!(x instanceof FolderTile)) at++; }
            ArrayList<Site> back = new ArrayList<Site>(f.sites);
            drop(c, f, back.size());
            ensure(grid);
            for (int i = 0; i < back.size(); i++) addBack(grid, back.get(i), at + i);
            grid.requestLayout();
        }
    }

    private static Panel sOpen;

    private static void open(FolderTile v) {
        if (sOpen != null || !(v.getParent() instanceof ViewGroup)) return;
        Folder f = v.folder();
        if (f == null) return;
        Activity a = activity(v.getContext());
        if (a == null) return;
        sOpen = new Panel(a, (ViewGroup) v.getParent(), v, f);
        sOpen.show();
    }

    private static Activity activity(Context c) {
        for (int i = 0; i < 10 && c != null; i++) {
            if (c instanceof Activity) return (Activity) c;
            if (!(c instanceof android.content.ContextWrapper)) return null;
            c = ((android.content.ContextWrapper) c).getBaseContext();
        }
        return null;
    }

    private static final class Panel {
        final Activity a;
        final ViewGroup grid;
        final WeakReference<FolderTile> tile;
        final Folder folder;
        final float d;
        Dialog dialog;
        FrameLayout root, card, strip, pager;
        View scrim;
        LinearLayout content, dots;
        TextView title;
        int cardW, cardH, cellW, cellH, page, pad, pagerTop, rowsUsed;
        int cols = COLS;
        CardBg bg;
        final RectF from = new RectF();
        float t;
        boolean closing;
        final ArrayList<SiteCell> cells = new ArrayList<SiteCell>();

        Panel(Activity a, ViewGroup grid, FolderTile tile, Folder f) {
            this.a = a;
            this.grid = grid;
            this.tile = new WeakReference<FolderTile>(tile);
            this.folder = f;
            this.d = a.getResources().getDisplayMetrics().density;
        }

        void show() {
            dialog = new Dialog(a, android.R.style.Theme_Translucent_NoTitleBar);
            dialog.setCancelable(true);
            Window w = dialog.getWindow();
            root = new FrameLayout(a) {
                @Override public boolean dispatchKeyEvent(KeyEvent e) {
                    if (e.getKeyCode() == KeyEvent.KEYCODE_BACK && e.getAction() == KeyEvent.ACTION_UP) { close(null); return true; }
                    return super.dispatchKeyEvent(e);
                }
            };
            scrim = new View(a);
            scrim.setBackgroundColor(0x52000000);
            scrim.setAlpha(0f);
            scrim.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { close(null); } });
            root.addView(scrim, new FrameLayout.LayoutParams(-1, -1));
            card = new FrameLayout(a);
            bg = new CardBg(panelColor(a));
            bg.rimWidth = Math.max(1f, d);
            boolean glassy = false; try { glassy = GlassRim.wanted(a); } catch (Throwable ignored) { }
            if (glassy) { try { bg.glass = GlassRim.of(a, 28 * d); } catch (Throwable ignored) { } }
            if (bg.glass == null && dark(a) && superDark()) bg.rim = ChromeLayout.sheetOutline(cardColor(a) | 0xFF000000, 0xFFE3E3E3);
            else if (bg.glass == null) bg.rim = ChromeLayout.sheetOutline(cardColor(a) | 0xFF000000, dark(a) ? 0xFFE3E3E3 : 0xFF1F1F1F);
            card.setBackground(bg);
            card.setElevation(8 * d);
            card.setClickable(true);
            root.addView(card, new FrameLayout.LayoutParams(-2, -2));
            dialog.setContentView(root);
            dialog.setOnKeyListener(new android.content.DialogInterface.OnKeyListener() {
                public boolean onKey(android.content.DialogInterface di, int code, KeyEvent e) {
                    if (code == KeyEvent.KEYCODE_BACK) { if (e.getAction() == KeyEvent.ACTION_UP) close(null); return true; }
                    return false;
                }
            });
            if (w != null) {
                w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                w.setLayout(-1, -1);
                w.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
                w.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                w.setWindowAnimations(0);
                w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
                w.setStatusBarColor(Color.TRANSPARENT);
                w.setNavigationBarColor(Color.TRANSPARENT);
            }
            build();
            dialog.setOnDismissListener(new android.content.DialogInterface.OnDismissListener() {
                public void onDismiss(android.content.DialogInterface di) {
                    FolderTile ft = tile.get();
                    if (ft != null && ft.iconHidden) { ft.iconHidden = false; ft.invalidate(); }
                    if (sOpen == Panel.this) sOpen = null;
                }
            });
            dialog.show(); Rtl.dialog(dialog);
            backGesture();
            root.getViewTreeObserver().addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener() {
                public boolean onPreDraw() {
                    root.getViewTreeObserver().removeOnPreDrawListener(this);
                    place();
                    animateOpen();
                    return false;
                }
            });
        }

        private void backGesture() {
            try {
                if (android.os.Build.VERSION.SDK_INT < 33) return;
                Object dispatcher = Dialog.class.getMethod("getOnBackInvokedDispatcher").invoke(dialog);
                final Class<?> cb = Class.forName("android.window.OnBackInvokedCallback");
                Object callback = java.lang.reflect.Proxy.newProxyInstance(cb.getClassLoader(), new Class<?>[]{cb}, new java.lang.reflect.InvocationHandler() {
                    public Object invoke(Object self, Method m, Object[] args) {
                        String n = m.getName();
                        if ("onBackInvoked".equals(n)) { card.post(new Runnable() { public void run() { close(null); } }); return null; }
                        if ("hashCode".equals(n)) return System.identityHashCode(self);
                        if ("equals".equals(n)) return args != null && args.length == 1 && args[0] == self;
                        if ("toString".equals(n)) return "PixelSuiteFolderBack";
                        return null;
                    }
                });
                dispatcher.getClass().getMethod("registerOnBackInvokedCallback", int.class, cb).invoke(dispatcher, 0, callback);
            } catch (Throwable t) { }
        }

        void build() {
            DisplayInfo di = new DisplayInfo(a);
            pad = Math.round(14 * d);
            int base = Math.max(sTileW > 0 ? sTileW : Math.round(72 * d), Math.round(76 * d));
            cellW = Math.round(base * 1.3f);
            int n = folder.sites.size();
            cols = n < COLS ? Math.max(2, n) : COLS;
            cardW = cols * cellW + 2 * pad;
            int maxW = Math.round(di.w - 32 * d);
            if (cardW > maxW) { cellW = (maxW - 2 * pad) / cols; cardW = cols * cellW + 2 * pad; }
            cellH = (sTileH > 0 ? sTileH : Math.round(96 * d)) + Math.round(4 * d);
            rowsUsed = Math.max(1, Math.min(ROWS, (Math.min(folder.sites.size(), PER_PAGE) + cols - 1) / cols));
            content = new LinearLayout(a);
            content.setOrientation(LinearLayout.VERTICAL);
            content.setPadding(pad, Math.round(16 * d), pad, Math.round(10 * d));
            title = new TextView(a);
            title.setText(I18n.cs(folder.name));
            title.setTextSize(20);
            title.setTypeface(I18n.medium());
            title.setTextColor(dark(a) ? 0xFFE8EAED : 0xFF202124);
            title.setGravity(Gravity.CENTER);
            title.setSingleLine(true);
            title.setEllipsize(TextUtils.TruncateAt.END);
            title.setPadding(Math.round(16 * d), Math.round(4 * d), Math.round(16 * d), Math.round(10 * d));
            title.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
                rename(a, folder, new Runnable() { public void run() { title.setText(I18n.cs(folder.name)); FolderTile ft = tile.get(); if (ft != null) ft.invalidate(); } });
            }});
            content.addView(title, new LinearLayout.LayoutParams(-1, -2));
            pager = new FrameLayout(a) {
                float downX, downY; boolean swiping; float startTx;
                private boolean begin(MotionEvent e) {
                    float dx = e.getRawX() - downX, dy = e.getRawY() - downY;
                    if (swiping || pages() < 2 || Math.abs(dx) <= ViewConfiguration.get(a).getScaledTouchSlop() || Math.abs(dx) <= Math.abs(dy)) return swiping;
                    swiping = true;
                    strip.animate().cancel();
                    startTx = strip.getTranslationX();
                    downX = e.getRawX();
                    for (SiteCell c : cells) c.cancelPress();
                    return true;
                }
                @Override public boolean onInterceptTouchEvent(MotionEvent e) {
                    if (dragging != null) return false;
                    switch (e.getActionMasked()) {
                        case MotionEvent.ACTION_DOWN: downX = e.getRawX(); downY = e.getRawY(); swiping = false; break;
                        case MotionEvent.ACTION_MOVE: if (begin(e)) return true; break;
                    }
                    return false;
                }
                @Override public boolean onTouchEvent(MotionEvent e) {
                    switch (e.getActionMasked()) {
                        case MotionEvent.ACTION_DOWN: downX = e.getRawX(); downY = e.getRawY(); swiping = false; return true;
                        case MotionEvent.ACTION_MOVE: {
                            if (!begin(e)) return true;
                            float tx = startTx + (e.getRawX() - downX), min = -(pages() - 1) * pageW();
                            if (tx > 0) tx *= 0.35f; else if (tx < min) tx = min + (tx - min) * 0.35f;
                            strip.setTranslationX(tx);
                            return true;
                        }
                        case MotionEvent.ACTION_UP: case MotionEvent.ACTION_CANCEL: {
                            if (!swiping) return true;
                            float dx = e.getRawX() - downX;
                            int target = page;
                            if (dx < -pageW() * 0.18f) target = page + 1; else if (dx > pageW() * 0.18f) target = page - 1;
                            go(Math.max(0, Math.min(pages() - 1, target)));
                            swiping = false;
                            return true;
                        }
                    }
                    return true;
                }
            };
            pager.setClipChildren(true);
            strip = new FrameLayout(a);
            pager.addView(strip, new FrameLayout.LayoutParams(Math.max(1, pages()) * pageW(), ROWS * cellH));
            content.addView(pager, new LinearLayout.LayoutParams(pageW(), rowsUsed * cellH));
            dots = new LinearLayout(a);
            dots.setOrientation(LinearLayout.HORIZONTAL);
            dots.setGravity(Gravity.CENTER);
            content.addView(dots, new LinearLayout.LayoutParams(-1, Math.round(18 * d)));
            card.addView(content, new FrameLayout.LayoutParams(cardW, -2));
            cells.clear();
            for (Site s : folder.sites) { SiteCell c = new SiteCell(a, this, s); cells.add(c); strip.addView(c, new FrameLayout.LayoutParams(cellW, cellH)); }
            arrange(false);
            content.measure(View.MeasureSpec.makeMeasureSpec(cardW, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            cardH = content.getMeasuredHeight();
            pagerTop = content.getPaddingTop() + title.getMeasuredHeight();
        }

        int pageW() { return cellW * cols; }
        int pages() { return Math.max(1, (folder.sites.size() + PER_PAGE - 1) / PER_PAGE); }
        float cellX(int i) { return (i / PER_PAGE) * pageW() + (i % cols) * cellW; }
        float cellY(int i) { return ((i % PER_PAGE) / cols) * cellH; }

        void arrange(boolean animate) {
            ViewGroup.LayoutParams slp = strip.getLayoutParams();
            if (slp != null && slp.width != pages() * pageW()) { slp.width = pages() * pageW(); strip.setLayoutParams(slp); }
            for (int i = 0; i < cells.size(); i++) {
                SiteCell c = cells.get(i);
                if (c == dragging) continue;
                c.baseX = cellX(i);
                c.baseY = cellY(i);
                if (animate) c.animate().translationX(c.baseX).translationY(c.baseY).setDuration(200).setInterpolator(ChromeTabTransition.DECELERATE).start();
                else { c.setTranslationX(c.baseX); c.setTranslationY(c.baseY); }
            }
            dots.removeAllViews();
            int n = pages();
            if (n > 1) for (int i = 0; i < n; i++) {
                View dot = new View(a);
                ShapeDrawable sd = new ShapeDrawable(new OvalShape());
                sd.getPaint().setColor(i == page ? (dark(a) ? 0xFFE8EAED : 0xFF3C4043) : (dark(a) ? 0x66E8EAED : 0x553C4043));
                dot.setBackground(sd);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Math.round(6 * d), Math.round(6 * d));
                lp.setMargins(Math.round(4 * d), 0, Math.round(4 * d), 0);
                dots.addView(dot, lp);
            }
            if (page > n - 1) go(n - 1);
        }

        void go(int p) {
            page = p;
            strip.animate().translationX(-p * pageW()).setDuration(260).setInterpolator(ChromeTabTransition.DECELERATE).start();
            for (int i = 0; i < dots.getChildCount(); i++) {
                ShapeDrawable sd = (ShapeDrawable) dots.getChildAt(i).getBackground();
                sd.getPaint().setColor(i == p ? (dark(a) ? 0xFFE8EAED : 0xFF3C4043) : (dark(a) ? 0x66E8EAED : 0x553C4043));
                dots.getChildAt(i).invalidate();
            }
        }

        private boolean circle(int left, int top) {
            FolderTile ft = tile.get();
            if (ft == null || !ft.isAttachedToWindow()) return false;
            int[] me = new int[2], at = new int[2];
            root.getLocationOnScreen(me);
            ft.getLocationOnScreen(at);
            float s = iconSize(a), cx = at[0] + ft.getWidth() / 2f - me[0], cy = at[1] + ft.iconCenterY() - me[1];
            from.set(cx - s / 2f - left, cy - s / 2f - top, cx + s / 2f - left, cy + s / 2f - top);
            return true;
        }

        void place() {
            DisplayInfo di = new DisplayInfo(a);
            int[] me = new int[2];
            root.getLocationOnScreen(me);
            float cx = di.w / 2f, cy = di.h / 2f, s = iconSize(a);
            FolderTile ft = tile.get();
            if (ft != null && ft.isAttachedToWindow()) {
                int[] at = new int[2];
                ft.getLocationOnScreen(at);
                cx = at[0] + ft.getWidth() / 2f - me[0];
                cy = at[1] + ft.iconCenterY() - me[1];
            }
            int rw = root.getWidth() > 0 ? root.getWidth() : di.w, rh = root.getHeight() > 0 ? root.getHeight() : di.h;
            if (rh < cardH + 32 * d) rh = Math.max(rh, di.h);
            int top = Math.round(cy - cardH / 2f);
            int minTop = di.top + Math.round(16 * d), maxTop = rh - di.bottom - cardH - Math.round(16 * d);
            top = Math.max(minTop, Math.min(Math.max(minTop, maxTop), top));
            int left = Math.round(cx - cardW / 2f);
            left = Math.max(Math.round(16 * d), Math.min(rw - cardW - Math.round(16 * d), left));
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) card.getLayoutParams();
            lp.width = cardW;
            lp.height = cardH;
            lp.leftMargin = left;
            lp.topMargin = top;
            card.setLayoutParams(lp);
            card.layout(left, top, left + cardW, top + cardH);
            from.set(cx - s / 2f - left, cy - s / 2f - top, cx + s / 2f - left, cy + s / 2f - top);
            card.setOutlineProvider(new ViewOutlineProvider() {
                @Override public void getOutline(View v, Outline o) {
                    shape(t);
                    o.setRoundRect(Math.round(bg.rect.left), Math.round(bg.rect.top), Math.round(bg.rect.right), Math.round(bg.rect.bottom), bg.radius);
                }
            });
            card.setClipToOutline(true);
            if (frosted(a)) bg.blur(card, FrostedSurface.depthBlurRadius(a));
            flights();
        }

        void flights() {
            float s = iconSize(a);
            float iconTop = sIconTop >= 0 ? sIconTop : 11 * d;
            int first = page * PER_PAGE, n = page == 0 ? Math.min(4, folder.sites.size()) : 0;
            for (int i = 0; i < cells.size(); i++) {
                SiteCell c = cells.get(i);
                int j = i - first;
                c.flies = j >= 0 && j < PER_PAGE;
                c.extra = n == 0 || j >= n;
                if (!c.flies) { c.setAlpha(1f); continue; }
                float[] p = n == 0 ? new float[]{0f, 0f, s * 0.2f} : spot(j, n, s);
                float iconX = pad + cellX(i) - first / PER_PAGE * pageW() + cellW / 2f, iconY = pagerTop + cellY(i) + iconTop + s / 2f;
                c.fx = from.centerX() + p[0] - iconX;
                c.fy = from.centerY() + p[1] - iconY;
                c.fs = p[2] / s;
                c.setPivotX(cellW / 2f);
                c.setPivotY(iconTop + s / 2f);
            }
        }

        void shape(float e) {
            bg.rect.set(from.left + (0 - from.left) * e, from.top + (0 - from.top) * e, from.right + (cardW - from.right) * e, from.bottom + (cardH - from.bottom) * e);
            bg.radius = (from.width() / 2f) + (28 * d - from.width() / 2f) * e;
        }

        void set(float v) {
            t = v;
            shape(v);
            card.invalidateOutline();
            bg.color = ChromeTabTransition.mix(folderColor(a), cardColor(a), ChromeTabTransition.clamp(v * 1.4f));
            bg.rimShow = ChromeTabTransition.clamp((v - 0.3f) / 0.5f);
            bg.invalidateSelf();
            float words = ChromeTabTransition.clamp((v - 0.4f) / 0.5f);
            title.setAlpha(words);
            dots.setAlpha(words);
            float names = ChromeTabTransition.clamp((v - 0.55f) / 0.45f);
            for (SiteCell c : cells) {
                if (!c.flies || c == dragging) continue;
                float k = 1f - v;
                c.setTranslationX(c.baseX + c.fx * k);
                c.setTranslationY(c.baseY + c.fy * k);
                float sc = c.fs + (1f - c.fs) * v;
                c.setScaleX(sc);
                c.setScaleY(sc);
                c.names = names;
                c.setAlpha(c.extra ? ChromeTabTransition.clamp((v - 0.15f) / 0.45f) : 1f);
                c.invalidate();
            }
            card.setElevation(bg.see() ? 0f : 8 * d * v);
        }

        void animateOpen() {
            final FolderTile ft = tile.get();
            if (ft != null) card.postOnAnimation(new Runnable() { public void run() { card.postOnAnimation(new Runnable() { public void run() {
                if (!closing) { ft.iconHidden = true; ft.invalidate(); }
            }}); }});
            set(0f);
            scrim.animate().alpha(1f).setDuration(300).setInterpolator(ChromeTabTransition.DECELERATE).start();
            ValueAnimator va = ValueAnimator.ofFloat(0f, 1f);
            va.setDuration(420);
            va.setInterpolator(ChromeTabTransition.EMPHASIZED);
            va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() { public void onAnimationUpdate(ValueAnimator v) { set((Float) v.getAnimatedValue()); } });
            va.addListener(new AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(Animator x) { if (!closing) { set(1f); scrim.setAlpha(1f); } }
            });
            va.start();
            card.postDelayed(new Runnable() { public void run() {
                if (closing || t >= 0.99f) return;
                set(1f); scrim.setAlpha(1f);
            }}, 900);
        }

        void close(final Runnable after) {
            if (closing) return;
            closing = true;
            if (dragging != null) { dragging.endDrag(false); }
            for (SiteCell c : cells) { c.animate().cancel(); if (c.lifted) { c.lifted = false; c.setElevation(0); } }
            strip.animate().cancel();
            strip.setTranslationX(-page * pageW());
            try { circle(card.getLeft(), card.getTop()); } catch (Throwable ignored) { }
            flights();
            scrim.animate().alpha(0f).setDuration(300).setInterpolator(ChromeTabTransition.ACCELERATE).start();
            ValueAnimator va = ValueAnimator.ofFloat(t, 0f);
            va.setDuration(360);
            va.setInterpolator(ChromeTabTransition.EMPHASIZED);
            va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() { public void onAnimationUpdate(ValueAnimator v) { set((Float) v.getAnimatedValue()); } });
            va.addListener(new AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(Animator x) { finish(after); }
            });
            va.start();
        }

        void finish(final Runnable after) {
            final FolderTile ft = tile.get();
            if (ft == null || !ft.isAttachedToWindow()) { done(after); return; }
            ft.iconHidden = false; ft.invalidate();
            ft.postOnAnimation(new Runnable() { public void run() { ft.postOnAnimation(new Runnable() { public void run() { done(after); } }); } });
        }

        private void done(Runnable after) {
            FolderTile ft = tile.get();
            if (ft != null && ft.iconHidden) { ft.iconHidden = false; ft.invalidate(); }
            try { dialog.dismiss(); } catch (Throwable ignored) { }
            if (sOpen == this) sOpen = null;
            if (after != null) after.run();
        }

        void changed() {
            save(a);
            FolderTile ft = tile.get();
            if (ft != null) ft.invalidate();
        }

        SiteCell dragging;

        void openSite(final Site s, final boolean newTab) {
            if (newTab) { ChromeFolders.openSite(grid, s, true); return; }
            closing = true;
            scrim.animate().alpha(0f).setDuration(150).start();
            card.animate().alpha(0f).setDuration(150).withEndAction(new Runnable() { public void run() { finish(new Runnable() { public void run() { ChromeFolders.openSite(grid, s, false); } }); } }).start();
        }

        void takeOut(final Site s) {
            int room = 14 - items(grid);
            if (room < 1) { toast(a, "No room on the speed dial"); return; }
            folder.sites.remove(s);
            if (folder.sites.isEmpty()) drop(a, folder, 0); else save(a);
            close(new Runnable() { public void run() {
                addBack(grid, s, null);
                ensure(grid);
                grid.requestLayout();
            }});
        }

        void remove(Site s) {
            int i = folder.sites.indexOf(s);
            if (i < 0) return;
            folder.sites.remove(i);
            SiteCell c = cells.remove(i);
            strip.removeView(c);
            if (folder.sites.isEmpty()) { drop(a, folder, 0); close(new Runnable() { public void run() { ensure(grid); grid.requestLayout(); } }); return; }
            changed();
            arrange(true);
        }

        int slotAt(float x, float y) {
            int col = Math.max(0, Math.min(cols - 1, (int) Math.floor(x / cellW)));
            int row = Math.max(0, Math.min(ROWS - 1, (int) Math.floor(y / cellH)));
            int i = page * PER_PAGE + row * cols + col;
            return Math.max(0, Math.min(cells.size() - 1, i));
        }
    }

    private static final class SiteCell extends View {
        final Panel panel;
        final Site site;
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        final TextPaint text = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        float downX, downY, lastX, lastY;
        boolean pressed, lifted, inDrag, outside;
        PopupWindow menu;
        Runnable longPress, edge;
        int from;
        float stripAt;
        float baseX, baseY, fx, fy, fs = 1f, names = 1f;
        boolean flies, extra;

        SiteCell(Context c, Panel p, Site s) {
            super(c);
            panel = p;
            site = s;
            setClickable(true);
        }

        @Override protected void onDraw(Canvas c) {
            Context ctx = getContext();
            int s = iconSize(ctx);
            float cx = getWidth() / 2f, top = sIconTop >= 0 ? sIconTop : 11 * getResources().getDisplayMetrics().density;
            drawSite(c, ctx, site, new RectF(cx - s / 2f, top, cx + s / 2f, top + s), paint);
            if (names > 0.01f) drawLabel(c, ctx, site.title, getWidth(), top + s, text, names, getWidth() - 6 * getResources().getDisplayMetrics().density);
        }

        void cancelPress() {
            if (longPress != null) removeCallbacks(longPress);
            pressed = false;
            if (lifted && !inDrag) settle();
        }

        void settle() {
            lifted = false;
            animate().scaleX(1f).scaleY(1f).setDuration(160).start();
            setElevation(0);
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            int slop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = lastX = e.getRawX(); downY = lastY = e.getRawY();
                    pressed = true;
                    longPress = new Runnable() { public void run() { if (pressed) lift(); } };
                    postDelayed(longPress, ViewConfiguration.getLongPressTimeout());
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    lastX = e.getRawX(); lastY = e.getRawY();
                    float dx = lastX - downX, dy = lastY - downY;
                    if (!lifted) { if (dx * dx + dy * dy > slop * slop) { removeCallbacks(longPress); pressed = false; } return true; }
                    if (!inDrag && dx * dx + dy * dy > slop * slop) startDrag();
                    if (inDrag) drag(dx, dy);
                    return true;
                }
                case MotionEvent.ACTION_UP:
                    removeCallbacks(longPress);
                    if (inDrag) { endDrag(true); return true; }
                    if (lifted) { settle(); return true; }
                    if (pressed) { pressed = false; playSoundEffect(android.view.SoundEffectConstants.CLICK); panel.openSite(site, false); }
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    removeCallbacks(longPress);
                    if (inDrag) endDrag(false); else if (lifted) settle();
                    pressed = false;
                    return true;
            }
            return true;
        }

        void lift() {
            lifted = true;
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            setElevation(6 * getResources().getDisplayMetrics().density);
            animate().scaleX(1.1f).scaleY(1.1f).setDuration(160).setInterpolator(ChromeTabTransition.DECELERATE).start();
            menu = menuAbove(this, new String[]{"Open in new tab", "Edit", "Move to speed dial", "Remove"}, new Pick() {
                public void picked(int item) {
                    if (item == 0) panel.openSite(site, true);
                    else if (item == 1) edit(panel.a, site, new Runnable() { public void run() { panel.changed(); invalidate(); } });
                    else if (item == 2) panel.takeOut(site);
                    else panel.remove(site);
                }
            });
        }

        void startDrag() {
            inDrag = true;
            if (menu != null) { try { menu.dismiss(); } catch (Throwable ignored) { } menu = null; }
            panel.dragging = this;
            from = panel.cells.indexOf(this);
            stripAt = panel.strip.getTranslationX();
            bringToFront();
        }

        void drag(float dx, float dy) {
            int i = panel.cells.indexOf(this);
            setTranslationX(panel.cellX(from) + dx - (panel.strip.getTranslationX() - stripAt));
            setTranslationY(panel.cellY(from) + dy);
            int[] at = new int[2];
            panel.card.getLocationOnScreen(at);
            float m = 24 * getResources().getDisplayMetrics().density;
            boolean out = lastX < at[0] - m || lastX > at[0] + panel.card.getWidth() + m || lastY < at[1] - m || lastY > at[1] + panel.card.getHeight() + m;
            if (out != outside) {
                outside = out;
                animate().scaleX(out ? 0.9f : 1.1f).scaleY(out ? 0.9f : 1.1f).setDuration(140).start();
                panel.card.animate().alpha(out ? 0.75f : 1f).setDuration(140).start();
            }
            if (out) return;
            int[] pg = new int[2];
            panel.pager.getLocationOnScreen(pg);
            float inX = lastX - pg[0], inY = lastY - pg[1];
            float d = getResources().getDisplayMetrics().density;
            if ((inX < 20 * d && panel.page > 0) || (inX > panel.pageW() - 20 * d && panel.page < panel.pages() - 1)) {
                if (edge == null) {
                    final int dir = inX < panel.pageW() / 2f ? -1 : 1;
                    edge = new Runnable() { public void run() { edge = null; panel.go(panel.page + dir); } };
                    postDelayed(edge, 450);
                }
            } else if (edge != null) { removeCallbacks(edge); edge = null; }
            int slot = panel.slotAt(inX, inY);
            if (slot != i) {
                panel.cells.remove(this);
                panel.cells.add(slot, this);
                panel.arrange(true);
                try { Haptics.tick(getContext()); } catch (Throwable ignored) { }
            }
        }

        void endDrag(boolean drop) {
            if (edge != null) { removeCallbacks(edge); edge = null; }
            inDrag = false;
            panel.dragging = null;
            panel.card.animate().alpha(1f).setDuration(120).start();
            if (drop && outside) {
                outside = false;
                panel.cells.remove(this);
                panel.cells.add(Math.max(0, Math.min(panel.cells.size(), from)), this);
                panel.takeOut(site);
                return;
            }
            outside = false;
            panel.folder.sites.clear();
            for (SiteCell c : panel.cells) panel.folder.sites.add(c.site);
            panel.changed();
            settle();
            panel.arrange(true);
        }
    }

    private static int mix(int a, int b, float t) {
        int ar = (a >> 16) & 0xff, ag = (a >> 8) & 0xff, ab = a & 0xff, br = (b >> 16) & 0xff, bg = (b >> 8) & 0xff, bb = b & 0xff;
        return 0xFF000000 | (Math.round(ar + (br - ar) * t) << 16) | (Math.round(ag + (bg - ag) * t) << 8) | Math.round(ab + (bb - ab) * t);
    }

    private static final class DisplayInfo {
        int w, h, top, bottom;
        DisplayInfo(Activity a) {
            View dv = a.getWindow().getDecorView();
            w = dv.getWidth() > 0 ? dv.getWidth() : a.getResources().getDisplayMetrics().widthPixels;
            h = dv.getHeight() > 0 ? dv.getHeight() : a.getResources().getDisplayMetrics().heightPixels;
            try {
                android.view.WindowInsets in = dv.getRootWindowInsets();
                if (in != null) { top = in.getSystemWindowInsetTop(); bottom = in.getSystemWindowInsetBottom(); }
            } catch (Throwable ignored) { }
        }
    }

    static final class Ring extends Drawable {
        final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        float cx, cy, r, scale;
        ValueAnimator anim;

        Ring(Context c) {
            fill.setColor(dark(c) ? 0x40E8EAED : 0x403C4043);
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(2.5f * c.getResources().getDisplayMetrics().density);
            stroke.setColor(dark(c) ? 0xCCE8EAED : 0xCC3C4043);
        }

        void at(float x, float y, float radius) { cx = x; cy = y; r = radius; invalidateSelf(); }

        void grow(final View host) {
            if (anim != null) anim.cancel();
            anim = ValueAnimator.ofFloat(scale, 1f);
            anim.setDuration(260);
            anim.setInterpolator(ChromeTabTransition.SPRING);
            anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() { public void onAnimationUpdate(ValueAnimator v) { scale = (Float) v.getAnimatedValue(); invalidateSelf(); host.invalidate(); } });
            anim.start();
        }

        @Override public void draw(Canvas c) {
            if (scale <= 0.01f) return;
            float rr = r * (0.8f + 0.2f * scale);
            fill.setAlpha(Math.round(0x40 * scale));
            c.drawCircle(cx, cy, rr, fill);
            stroke.setAlpha(Math.round(0xCC * scale));
            c.drawCircle(cx, cy, rr, stroke);
        }

        @Override public void setAlpha(int a) { }
        @Override public void setColorFilter(android.graphics.ColorFilter f) { }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }

    private static List<Field> fields(Class<?> c) {
        List<Field> out = new ArrayList<Field>();
        for (Class<?> k = c; k != null && k != Object.class; k = k.getSuperclass()) {
            for (Field f : k.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive()) continue;
                try { f.setAccessible(true); out.add(f); } catch (Throwable ignored) { }
            }
        }
        return out;
    }
}
