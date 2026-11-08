package io.github.pixelsuite;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.WeakHashMap;

final class ChromeTileIcons {
    private ChromeTileIcons() { }

    static final int REQUEST = 0x5053;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final HashMap<String, Bitmap> sCache = new HashMap<String, Bitmap>();
    private static Header sWaiting;
    private static boolean sInstalled;
    private static final WeakHashMap<Dialog, Header> sDialogs = new WeakHashMap<Dialog, Header>();
    private static final WeakHashMap<View, Header> sSaveButtons = new WeakHashMap<View, Header>();
    private static final WeakHashMap<ImageView, Drawable[]> sOriginal = new WeakHashMap<ImageView, Drawable[]>();
    private static final WeakHashMap<ImageView, Drawable> sMine = new WeakHashMap<ImageView, Drawable>();
    private static final WeakHashMap<ImageView, ColorStateList> sTint = new WeakHashMap<ImageView, ColorStateList>();

    private static boolean on(Context c) {
        try { return ChromeLayout.enabled(c); } catch (Throwable t) { return false; }
    }

    static String key(String url) {
        if (url == null) return "";
        String u = url.trim().toLowerCase(Locale.ROOT);
        int s = u.indexOf("://");
        if (s >= 0) u = u.substring(s + 3);
        if (u.startsWith("www.")) u = u.substring(4);
        while (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        return u;
    }

    private static File file(Context c, String url) {
        File dir = new File(c.getFilesDir(), "pixelsuite_custom_icons");
        if (!dir.isDirectory()) dir.mkdirs();
        String k = key(url);
        try {
            byte[] d = MessageDigest.getInstance("SHA-1").digest(k.getBytes("UTF-8"));
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < 12; i++) b.append(String.format("%02x", d[i] & 0xff));
            return new File(dir, b + ".png");
        } catch (Throwable t) { return new File(dir, Integer.toHexString(k.hashCode()) + ".png"); }
    }

    static Bitmap custom(Context c, String url) {
        if (url == null || c == null) return null;
        String k = key(url);
        synchronized (sCache) {
            if (sCache.containsKey(k)) return sCache.get(k);
        }
        Bitmap b = null;
        try { File f = file(c, url); if (f.isFile()) b = BitmapFactory.decodeFile(f.getPath()); } catch (Throwable ignored) { }
        synchronized (sCache) { sCache.put(k, b); }
        return b;
    }

    private static void store(Context c, String url, Bitmap b) {
        String k = key(url);
        try {
            File f = file(c, url);
            if (b == null) { f.delete(); }
            else { FileOutputStream out = new FileOutputStream(f); try { b.compress(Bitmap.CompressFormat.PNG, 100, out); } finally { out.close(); } }
        } catch (Throwable t) { }
        synchronized (sCache) { sCache.put(k, b); }
    }

    static void apply(View tile, ImageView icon) {
        try {
            String url = urlOf(tile);
            if (url == null) return;
            Bitmap b = custom(tile.getContext(), url);
            Drawable now = icon.getDrawable();
            Drawable mine = sMine.get(icon);
            if (b == null) {
                if (mine != null && now == mine) {
                    Drawable[] o = sOriginal.remove(icon);
                    sMine.remove(icon);
                    icon.setImageTintList(sTint.remove(icon));
                    icon.setImageDrawable(o != null ? o[0] : null);
                }
                return;
            }
            if (mine != null && now == mine && ((BitmapDrawable) mine).getBitmap() == round(b)) return;
            if (now != mine) sOriginal.put(icon, new Drawable[]{now});
            if (!sTint.containsKey(icon)) sTint.put(icon, icon.getImageTintList());
            BitmapDrawable d = new BitmapDrawable(tile.getResources(), round(b));
            sMine.put(icon, d);
            icon.setImageTintList(null);
            icon.setImageDrawable(d);
        } catch (Throwable ignored) { }
    }

    private static final java.util.WeakHashMap<Bitmap, Bitmap> sRound = new java.util.WeakHashMap<Bitmap, Bitmap>();
    static Bitmap round(Bitmap b) {
        if (b == null) return null;
        Bitmap r = sRound.get(b);
        if (r != null) return r;
        try {
            int side = Math.min(b.getWidth(), b.getHeight());
            r = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888);
            android.graphics.Canvas c = new android.graphics.Canvas(r);
            android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG | android.graphics.Paint.FILTER_BITMAP_FLAG);
            android.graphics.BitmapShader shader = new android.graphics.BitmapShader(b, android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP);
            android.graphics.Matrix m = new android.graphics.Matrix();
            m.setTranslate(-(b.getWidth() - side) / 2f, -(b.getHeight() - side) / 2f);
            shader.setLocalMatrix(m);
            p.setShader(shader);
            c.drawCircle(side / 2f, side / 2f, side / 2f, p);
        } catch (Throwable t) { r = b; }
        sRound.put(b, r);
        return r;
    }

    static String urlOf(View tile) {
        try {
            for (Class<?> k = tile.getClass(); k != null && k != View.class; k = k.getSuperclass())
                for (Field f : k.getDeclaredFields()) if (f.getType().getName().endsWith(".SiteSuggestion")) {
                    f.setAccessible(true);
                    Object s = f.get(tile);
                    ChromeFolders.Site site = s == null ? null : ChromeFolders.siteOf(s);
                    return site == null ? null : site.url;
                }
        } catch (Throwable ignored) { }
        return null;
    }

    static void refresh(View any) {
        if (any == null) return;
        refreshIn(any.getRootView());
    }

    private static void refreshIn(View v) {
        if (v.getClass().getName().endsWith("SuggestionsTileView")) { try { ChromeFolders.style(v); } catch (Throwable ignored) { } }
        v.invalidate();
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) refreshIn(((ViewGroup) v).getChildAt(i));
    }

    private static Drawable tileIcon(View root, String url) {
        if (root == null || url == null) return null;
        if (root.getClass().getName().endsWith("SuggestionsTileView") && key(url).equals(key(urlOf(root)))) {
            ImageView ic = ChromeFolders.icon(root);
            Drawable d = ic == null ? null : ic.getDrawable();
            if (d != null && d.getConstantState() != null) {
                Drawable c = d.getConstantState().newDrawable(root.getResources()).mutate();
                if (ic.getImageTintList() != null) c.setTintList(ic.getImageTintList());
                return c;
            }
            return d;
        }
        if (root instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) root).getChildCount(); i++) { Drawable d = tileIcon(((ViewGroup) root).getChildAt(i), url); if (d != null) return d; }
        return null;
    }

    static final class Header {
        final String oldUrl;
        final ImageView preview;
        final TextView reset;
        final WeakReference<Activity> act;
        Drawable website;
        Bitmap picked;
        boolean cleared;

        Header(Context c, String url, Drawable websiteIcon, int textColor) {
            oldUrl = url;
            website = websiteIcon;
            act = new WeakReference<Activity>(activity(c));
            float d = c.getResources().getDisplayMetrics().density;
            preview = new ImageView(c);
            preview.setOutlineProvider(new ViewOutlineProvider() { @Override public void getOutline(View v, Outline o) { o.setOval(0, 0, v.getWidth(), v.getHeight()); } });
            preview.setClipToOutline(true);
            GradientDrawable disc = new GradientDrawable();
            disc.setShape(GradientDrawable.OVAL);
            disc.setColor(ChromeFolders.dark(c) ? 0xFF303134 : 0xFFE8EAED);
            preview.setBackground(disc);
            preview.setContentDescription(I18n.cs("Change the picture"));
            preview.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { pick(Header.this); } });
            reset = new TextView(c);
            reset.setText(I18n.cs("Use the website's icon"));
            reset.setTextColor((textColor & 0x00FFFFFF) | 0xB3000000);
            reset.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            reset.setPadding(Math.round(8 * d), Math.round(6 * d), Math.round(8 * d), Math.round(6 * d));
            reset.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { picked = null; cleared = true; show(); } });
            show();
        }

        View view(Context c, int textColor) {
            float d = c.getResources().getDisplayMetrics().density;
            LinearLayout box = new LinearLayout(c);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setGravity(Gravity.CENTER_HORIZONTAL);
            box.setPadding(0, Math.round(8 * d), 0, Math.round(4 * d));
            int s = Math.round(72 * d);
            box.addView(preview, new LinearLayout.LayoutParams(s, s));
            TextView hint = new TextView(c);
            hint.setText(I18n.cs("Tap the picture to change it"));
            hint.setTextColor((textColor & 0x00FFFFFF) | 0x99000000);
            hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            hint.setPadding(0, Math.round(6 * d), 0, 0);
            box.addView(hint, new LinearLayout.LayoutParams(-2, -2));
            box.addView(reset, new LinearLayout.LayoutParams(-2, -2));
            return box;
        }

        void show() {
            Context c = preview.getContext();
            Bitmap mine = picked != null ? picked : cleared ? null : custom(c, oldUrl);
            reset.setVisibility(mine != null ? View.VISIBLE : View.GONE);
            if (mine != null) {
                preview.setPadding(0, 0, 0, 0);
                preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
                preview.setImageDrawable(new BitmapDrawable(c.getResources(), mine));
            } else {
                int pad = Math.round(72 * c.getResources().getDisplayMetrics().density * 0.17f);
                preview.setPadding(pad, pad, pad, pad);
                preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
                preview.setImageDrawable(website != null ? website : letter(c, oldUrl));
            }
        }

        void commit(String newUrl) {
            Context c = preview.getContext().getApplicationContext();
            String now = newUrl == null || newUrl.trim().isEmpty() ? oldUrl : newUrl;
            if (now == null) return;
            boolean moved = oldUrl != null && !key(oldUrl).equals(key(now));
            Bitmap had = oldUrl == null ? null : custom(c, oldUrl);
            if (picked != null) store(c, now, picked);
            else if (cleared) store(c, now, null);
            else if (moved && had != null) store(c, now, had);
            else return;
            if (moved) store(c, oldUrl, null);
            Activity a = act.get();
            if (a != null) refresh(a.getWindow().getDecorView());
        }
    }

    private static Drawable letter(Context c, String url) {
        String k = key(url);
        String l = k.isEmpty() ? "?" : k.substring(0, 1).toUpperCase(Locale.ROOT);
        int s = 96;
        Bitmap b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0xFF757575);
        cv.drawCircle(s / 2f, s / 2f, s / 2f, p);
        p.setColor(Color.WHITE);
        p.setTextSize(s * 0.45f);
        p.setTextAlign(Paint.Align.CENTER);
        p.setFakeBoldText(true);
        Paint.FontMetrics fm = p.getFontMetrics();
        cv.drawText(l, s / 2f, s / 2f - (fm.ascent + fm.descent) / 2f, p);
        return new BitmapDrawable(c.getResources(), b);
    }

    static Header header(Context c, String url, int textColor) {
        Bitmap b = null;
        Drawable web = null;
        try { b = ChromeFolders.folderIcon(c, url); } catch (Throwable ignored) { }
        if (b != null) web = new BitmapDrawable(c.getResources(), b);
        return new Header(c, url, web, textColor);
    }

    static Activity activity(Context c) {
        for (int i = 0; c != null && i < 10; i++) {
            if (c instanceof Activity) return (Activity) c;
            if (!(c instanceof android.content.ContextWrapper)) return null;
            c = ((android.content.ContextWrapper) c).getBaseContext();
        }
        return null;
    }

    private static void pick(Header h) {
        Activity a = h.act.get();
        if (a == null) return;
        try {
            Intent i = new Intent(Intent.ACTION_GET_CONTENT);
            i.setType("image/*");
            i.addCategory(Intent.CATEGORY_OPENABLE);
            sWaiting = h;
            try { a.startActivityForResult(i, REQUEST); }
            catch (android.content.ActivityNotFoundException e) {
                Intent o = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                o.setType("image/*");
                o.addCategory(Intent.CATEGORY_OPENABLE);
                a.startActivityForResult(o, REQUEST);
            }
        } catch (Throwable t) {
            sWaiting = null;
        }
    }

    private static void got(final Activity a, final Uri uri) {
        final Header h = sWaiting;
        sWaiting = null;
        if (h == null || uri == null) return;
        new Thread(new Runnable() { public void run() {
            Bitmap out = null;
            try {
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inJustDecodeBounds = true;
                InputStream in = a.getContentResolver().openInputStream(uri);
                try { BitmapFactory.decodeStream(in, null, o); } finally { if (in != null) in.close(); }
                int side = Math.min(o.outWidth, o.outHeight), sample = 1;
                while (side / (sample * 2) >= 512) sample *= 2;
                o = new BitmapFactory.Options();
                o.inSampleSize = sample;
                in = a.getContentResolver().openInputStream(uri);
                Bitmap full;
                try { full = BitmapFactory.decodeStream(in, null, o); } finally { if (in != null) in.close(); }
                if (full != null) {
                    int s = Math.min(full.getWidth(), full.getHeight());
                    Bitmap sq = Bitmap.createBitmap(full, (full.getWidth() - s) / 2, (full.getHeight() - s) / 2, s, s);
                    out = Bitmap.createScaledBitmap(sq, 256, 256, true);
                }
            } catch (Throwable t) { }
            final Bitmap res = out;
            MAIN.post(new Runnable() { public void run() {
                if (res == null) { android.widget.Toast.makeText(a, I18n.cs("Couldn't open that picture"), android.widget.Toast.LENGTH_SHORT).show(); return; }
                h.picked = res;
                h.cleared = false;
                h.show();
            } });
        } }).start();
    }

    static void install() {
        if (sInstalled) return;
        sInstalled = true;
        Xp.Callback result = new Xp.Callback() {
            @Override protected void beforeHookedMethod(Xp.Param p) {
                try {
                    int at = -1;
                    for (int i = 0; i < p.args.length; i++) if (p.args[i] instanceof Integer) { at = i; break; }
                    if (at < 0 || p.args.length < at + 3 || !Integer.valueOf(REQUEST).equals(p.args[at])) return;
                    int code = (Integer) p.args[at + 1];
                    Intent data = p.args[at + 2] instanceof Intent ? (Intent) p.args[at + 2] : null;
                    p.setResult(null);
                    if (code == Activity.RESULT_OK && data != null) got((Activity) p.thisObject, data.getData());
                    else sWaiting = null;
                } catch (Throwable ignored) { }
            }
        };
        Xp.hookAllMethods(Activity.class, "dispatchActivityResult", result);
        Xp.hookAllMethods(Activity.class, "onActivityResult", result);
        Xp.hookAllMethods(ImageView.class, "setImageDrawable", new Xp.Callback() {
            @Override protected void afterHookedMethod(Xp.Param p) {
                try {
                    final ImageView iv = (ImageView) p.thisObject;
                    Drawable mine = sMine.get(iv);
                    if (mine == null || p.args[0] == mine) return;
                    sOriginal.put(iv, new Drawable[]{(Drawable) p.args[0]});
                    sMine.remove(iv);
                    MAIN.post(new Runnable() { public void run() {
                        for (View v = iv; v != null; v = v.getParent() instanceof View ? (View) v.getParent() : null)
                            if (v.getClass().getName().endsWith("SuggestionsTileView")) { ChromeFolders.style(v); break; }
                    } });
                } catch (Throwable ignored) { }
            }
        });
        Xp.hookAllMethods(Dialog.class, "show", new Xp.Callback() {
            @Override protected void afterHookedMethod(Xp.Param p) {
                final Dialog d = (Dialog) p.thisObject;
                MAIN.postDelayed(new Runnable() { public void run() { try { chromeDialog(d); } catch (Throwable ignored) { } } }, 30);
                try { if (d.getWindow() != null) unpinToRemove(d.getWindow().getDecorView()); } catch (Throwable ignored) { }
            }
        });
        Xp.Callback popup = new Xp.Callback() {
            @Override protected void afterHookedMethod(Xp.Param p) {
                try { View v = ((android.widget.PopupWindow) p.thisObject).getContentView(); if (v != null) unpinToRemove(v); } catch (Throwable ignored) { }
            }
        };
        Xp.hookAllMethods(android.widget.PopupWindow.class, "showAtLocation", popup);
        Xp.hookAllMethods(android.widget.PopupWindow.class, "showAsDropDown", popup);
        Xp.hookAllMethods(View.class, "performClick", new Xp.Callback() {
            @Override protected void beforeHookedMethod(Xp.Param p) {
                try {
                    Header h = sSaveButtons.get(p.thisObject);
                    if (h == null) return;
                    EditText url = urlField(((View) p.thisObject).getRootView());
                    h.commit(url == null ? null : url.getText().toString());
                } catch (Throwable ignored) { }
            }
        });
    }

    private static void unpinToRemove(final View root) {
        if (root == null || !on(root.getContext())) return;
        final Runnable swap = new Runnable() { public void run() { swapText(root, 0); } };
        swap.run();
        MAIN.post(swap);
        MAIN.postDelayed(swap, 60);
        root.getViewTreeObserver().addOnGlobalLayoutListener(new android.view.ViewTreeObserver.OnGlobalLayoutListener() { public void onGlobalLayout() { swap.run(); } });
    }

    private static void swapText(View v, int depth) {
        if (depth > 12) return;
        if (v instanceof TextView && !(v instanceof EditText)) {
            CharSequence t = ((TextView) v).getText();
            if (t != null && "Unpin".equals(t.toString().trim())) ((TextView) v).setText(I18n.cs("Remove"));
            return;
        }
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) swapText(((ViewGroup) v).getChildAt(i), depth + 1);
    }

    private static void chromeDialog(Dialog d) {
        if (!d.isShowing() || sDialogs.containsKey(d)) return;
        Window w = d.getWindow();
        if (w == null || !on(d.getContext())) return;
        View root = w.getDecorView();
        ArrayList<EditText> edits = new ArrayList<EditText>();
        edits(root, edits);
        if (edits.size() < 2 || !hasText(root, "shortcut")) return;
        EditText url = urlField(root);
        if (url == null) return;
        ViewGroup box = common(edits.get(0), edits.get(1));
        while (box != null && !(box instanceof LinearLayout && ((LinearLayout) box).getOrientation() == LinearLayout.VERTICAL)) box = box.getParent() instanceof ViewGroup ? (ViewGroup) box.getParent() : null;
        if (box == null) {  return; }
        String old = url.getText().toString().trim();
        Activity a = activity(d.getContext());
        if (a == null) a = d.getOwnerActivity();
        Drawable web = tileIcon(a != null ? a.getWindow().getDecorView() : null, old);
        int tc = edits.get(0).getCurrentTextColor();
        Header h = new Header(a != null ? a : d.getContext(), old.isEmpty() ? null : old, web, tc);
        if (old.isEmpty()) h.preview.setImageDrawable(letter(d.getContext(), "+"));
        box.addView(h.view(d.getContext(), tc), 0, new LinearLayout.LayoutParams(-1, -2));
        sDialogs.put(d, h);
        View save = button(root, new String[]{"save", "done", "add", "ok"});
        if (save != null) sSaveButtons.put(save, h);
            }

    private static void edits(View v, ArrayList<EditText> out) {
        if (v instanceof EditText && v.isShown()) out.add((EditText) v);
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) edits(((ViewGroup) v).getChildAt(i), out);
    }

    private static boolean hasText(View v, String part) {
        if (v instanceof TextView && !(v instanceof EditText)) {
            CharSequence t = ((TextView) v).getText();
            if (t != null && t.toString().toLowerCase(Locale.ROOT).contains(part)) return true;
        }
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) if (hasText(((ViewGroup) v).getChildAt(i), part)) return true;
        return false;
    }

    private static EditText urlField(View root) {
        ArrayList<EditText> e = new ArrayList<EditText>();
        edits(root, e);
        for (EditText x : e) if ((x.getInputType() & android.text.InputType.TYPE_MASK_VARIATION) == android.text.InputType.TYPE_TEXT_VARIATION_URI) return x;
        for (EditText x : e) { String t = x.getText().toString(); if (t.contains("://") || (t.contains(".") && !t.contains(" "))) return x; }
        return e.size() >= 2 ? e.get(1) : null;
    }

    private static View button(View v, String[] words) {
        if (v instanceof Button || (v instanceof TextView && v.isClickable())) {
            CharSequence t = ((TextView) v).getText();
            String s = t == null ? "" : t.toString().trim().toLowerCase(Locale.ROOT);
            for (String w : words) if (s.equals(w)) return v;
        }
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) { View f = button(((ViewGroup) v).getChildAt(i), words); if (f != null) return f; }
        return null;
    }

    private static ViewGroup common(View a, View b) {
        ArrayList<View> up = new ArrayList<View>();
        for (View v = a; v != null; v = v.getParent() instanceof View ? (View) v.getParent() : null) up.add(v);
        for (View v = b; v != null; v = v.getParent() instanceof View ? (View) v.getParent() : null) if (up.contains(v) && v instanceof ViewGroup) return (ViewGroup) v;
        return null;
    }
}
