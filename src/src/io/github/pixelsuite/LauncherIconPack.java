package io.github.pixelsuite;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.TimeInterpolator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.LruCache;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.PathInterpolator;
import android.view.inputmethod.EditorInfo;
import android.widget.AbsListView;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.BaseAdapter;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

final class LauncherIconPack {
    private LauncherIconPack() {}

    static final int SIZE = 256;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService WORK = new ThreadPoolExecutor(0, 1, 15, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
    private static final ExecutorService THUMBS = new ThreadPoolExecutor(0, 2, 10, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());

    private static volatile String sActive;
    private static String sWanted;
    private static final Set<String> sQueued = new HashSet<String>();
    private static boolean sAttached;

    static void attach(final Activity a) {
        if (sAttached) return;
        sAttached = true;
        final Context app = a.getApplicationContext();
        sActive = read(app);
        try {
            app.getContentResolver().registerContentObserver(Settings.Secure.getUriFor(IconPacks.KEY), false, new ContentObserver(MAIN) {
                @Override public void onChange(boolean self) { switchTo(read(app), false); }
            });
        } catch (Throwable ignored) { }
        try {
            IntentFilter f = new IntentFilter();
            f.addAction(Intent.ACTION_PACKAGE_ADDED);
            f.addAction(Intent.ACTION_PACKAGE_REPLACED);
            f.addAction(Intent.ACTION_PACKAGE_REMOVED);
            f.addDataScheme("package");
            app.registerReceiver(new BroadcastReceiver() {
                @Override public void onReceive(Context c, Intent i) {
                    Uri d = i.getData();
                    String pkg = d == null ? null : d.getSchemeSpecificPart();
                    String active = sActive;
                    if (pkg == null || active == null || !pkg.equals(active)) return;
                    IconPacks.forget(pkg);
                    if (Intent.ACTION_PACKAGE_REMOVED.equals(i.getAction()) && i.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return;
                    switchTo(active, true);
                }
            }, f);
        } catch (Throwable ignored) { }
        if (sActive != null && !sActive.isEmpty()) {
            MAIN.postDelayed(() -> prepareAll(sActive, false), 2000);
        }
    }

    private static String read(Context c) {
        try { String v = Settings.Secure.getString(c.getContentResolver(), IconPacks.KEY); return v == null ? "" : v.trim(); }
        catch (Throwable t) { return ""; }
    }

    private static void switchTo(String pkg, boolean force) {
        if (pkg == null) pkg = "";
        if (!force && pkg.equals(sActive) && sWanted == null) return;
        sWanted = pkg;
        if (pkg.isEmpty()) { finish(""); return; }
        prepareAll(pkg, true);
    }

    private static void prepareAll(final String pkg, final boolean crossfade) {
        final Activity a = LauncherFeatures.activity.get();
        if (a == null) { if (crossfade) finish(pkg); return; }
        final List<Object[]> jobs = new ArrayList<Object[]>();
        try {
            Object store = LauncherFeatures.call(LauncherFeatures.call(a, "getActivityComponent"), "getAppsStore");
            Object[] apps = (Object[]) LauncherFeatures.get(store, "mApps");
            for (Object app : apps) {
                String key = LauncherFeatures.identity(app);
                if (key == null) continue;
                jobs.add(new Object[]{key, component(key), app});
            }
        } catch (Throwable ignored) { }
        final Context c = a.getApplicationContext();
        WORK.execute(() -> {
            IconPacks.Pack pack = IconPacks.get(c, pkg);
            if (pack != null) {
                File dir = dir(c, pack);
                for (Object[] j : jobs) draw(c, pack, dir, (String) j[0], (ComponentName) j[1], j[2]);
                cleanOld(c, dir);
            }
            MAIN.post(() -> {
                if (!pkg.equals(sWanted) && crossfade) return;
                if (crossfade) finish(pack == null ? "" : pkg);
                else { retireAll(); LauncherFeatures.refresh(); }
            });
        });
    }

    private static void finish(String pkg) {
        sWanted = null;
        final Activity a = LauncherFeatures.activity.get();
        Runnable swap = () -> { sActive = pkg; retireAll(); LauncherFeatures.refresh(); };
        if (a == null) { swap.run(); return; }
        crossfade(a, swap);
    }

    private static void retireAll() {
        synchronized (LauncherIcon.cache) {
            LauncherIcon.retired.addAll(LauncherIcon.cache.values());
            LauncherIcon.cache.clear();
            LauncherIcon.missing.clear();
        }
        try {
            Activity a = LauncherFeatures.activity.get();
            if (a != null) {
                Object provider = Xp.callStaticMethod(LauncherFeatures.type("dagger.LauncherComponentProvider"), "get", a);
                ((java.util.Queue<?>) LauncherFeatures.get(LauncherFeatures.call(provider, "getIconPool"), "pool")).clear();
            }
        } catch (Throwable ignored) { }
        AppPicker.refreshIcons();
    }

    static File globalFile(Context c, String key, Object info) {
        String pkg = sActive;
        if (pkg == null || pkg.isEmpty() || c == null) return null;
        try {
            IconPacks.Pack pack = IconPacks.get(c, pkg);
            if (pack == null) return null;
            File dir = dir(c, pack);
            File f = new File(dir, name(key) + ".png");
            if (f.isFile()) return f;
            if (new File(dir, name(key) + ".none").exists()) return null;
            queue(c, pack, key, info);
        } catch (Throwable ignored) { }
        return null;
    }

    private static void queue(final Context c, final IconPacks.Pack pack, final String key, Object info) {
        synchronized (sQueued) { if (!sQueued.add(pack.pkg + "|" + key)) return; }
        final ComponentName cn = component(key);
        final Object app = info;
        WORK.execute(() -> {
            draw(c, pack, dir(c, pack), key, cn, app);
            synchronized (sQueued) { sQueued.remove(pack.pkg + "|" + key); }
            MAIN.post(() -> {
                synchronized (LauncherIcon.cache) { LauncherIcon.retire(key); }
                LauncherIcon.main.removeCallbacks(REFRESH);
                LauncherIcon.main.postDelayed(REFRESH, 120);
            });
        });
    }

    private static final Runnable REFRESH = LauncherFeatures::refresh;

    private static void draw(Context c, IconPacks.Pack pack, File dir, String key, ComponentName cn, Object info) {
        try {
            File png = new File(dir, name(key) + ".png"), none = new File(dir, name(key) + ".none");
            if (png.isFile() || none.exists()) return;
            if (!dir.isDirectory() && !dir.mkdirs()) return;
            Bitmap b = null;
            String n = pack.nameFor(cn);
            if (n != null) {
                Drawable d = pack.drawable(n);
                if (d != null) b = IconPacks.fill(IconPacks.render(d, SIZE));
            }
            if (b == null) { none.createNewFile(); return; }
            write(b, dir, png);
            b.recycle();
        } catch (Throwable ignored) { }
    }

    private static Bitmap own(String key, Object info) {
        try {
            Object bitmapInfo;
            synchronized (LauncherIcon.cache) { bitmapInfo = LauncherIcon.nativeIcons.get(key); }
            if (bitmapInfo == null && info != null) {
                Object current = LauncherFeatures.get(info, "bitmap");
                if (!LauncherIcon.ours(current)) bitmapInfo = current;
            }
            Object icon = bitmapInfo == null ? null : LauncherFeatures.get(bitmapInfo, "icon");
            if (!(icon instanceof Bitmap)) return null;
            Bitmap b = (Bitmap) icon;
            return b.getConfig() == Bitmap.Config.ARGB_8888 ? b : b.copy(Bitmap.Config.ARGB_8888, false);
        } catch (Throwable t) {
            return null;
        }
    }

    private static ComponentName component(String key) {
        int bar = key.indexOf('|');
        return ComponentName.unflattenFromString(bar >= 0 ? key.substring(bar + 1) : key);
    }

    private static File dir(Context c, IconPacks.Pack pack) {
        return new File(new File(new File(c.getFilesDir(), "pixelsuite-icons"), "packs"), pack.pkg + "-" + pack.version + "-v6");
    }

    private static void cleanOld(Context c, File keep) {
        File[] dirs = new File(new File(c.getFilesDir(), "pixelsuite-icons"), "packs").listFiles();
        if (dirs == null) return;
        for (File d : dirs) {
            if (d.equals(keep)) continue;
            File[] files = d.listFiles();
            if (files != null) for (File f : files) f.delete();
            d.delete();
        }
    }

    static String name(String key) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(key.getBytes("UTF-8"));
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < 16; i++) s.append(String.format(Locale.ROOT, "%02x", digest[i] & 255));
        return s.toString();
    }

    private static void write(Bitmap b, File dir, File target) throws Exception {
        File tmp = File.createTempFile("pack-", ".png", dir);
        try {
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                b.compress(Bitmap.CompressFormat.PNG, 100, out);
                out.getFD().sync();
            }
            if (!tmp.renameTo(target)) throw new java.io.IOException("rename");
        } finally {
            tmp.delete();
        }
    }

    static File pickFile(Context c, String key) throws Exception {
        return new File(new File(c.getFilesDir(), "pixelsuite-icons"), LauncherIcon.filename(key).replace(".png", ".pack.png"));
    }

    static void openPicker(final Activity a, final String key) {
        final List<IconPacks.Info> packs = IconPacks.installed(a);
        final ComponentName cn = component(key);
        final String label = LauncherIcon.chosen(key).label;
        final boolean dark = FrostedSurface.dark(a);
        final int ink = AppPicker.textColor(a), accent = AppPicker.accent(a);

        LinearLayout root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(0, dp(a, 4), 0, 0);

        final AlertDialog dialog = new AlertDialog.Builder(a).setTitle("Icon pack").setView(root).setNegativeButton("Cancel", null).create();

        if (packs.isEmpty()) {
            TextView none = new TextView(a);
            none.setText("No icon packs installed yet.\nInstall one from the Play Store (or any APK) and it shows here straight away.");
            none.setTextColor((ink & 0xffffff) | 0xb3000000);
            none.setTextSize(15);
            none.setGravity(Gravity.CENTER);
            none.setPadding(dp(a, 24), dp(a, 20), dp(a, 24), dp(a, 12));
            MenuFont.apply(none);
            root.addView(none, new LinearLayout.LayoutParams(-1, -2));
            TextView get = pill(a, "Get icon packs", true, accent, ink, dark);
            LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(-2, dp(a, 44));
            gp.gravity = Gravity.CENTER_HORIZONTAL;
            gp.bottomMargin = dp(a, 12);
            root.addView(get, gp);
            get.setOnClickListener(v -> {
                dialog.dismiss();
                try {
                    a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=icon%20pack&c=apps")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } catch (Throwable t) {
                    Toast.makeText(a, "Play Store is unavailable", Toast.LENGTH_SHORT).show();
                }
            });
            FrostedSurface.show(dialog);
            return;
        }

        HorizontalScrollView scroller = new HorizontalScrollView(a);
        scroller.setHorizontalScrollBarEnabled(false);
        final LinearLayout chips = new LinearLayout(a);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.setPadding(dp(a, 20), 0, dp(a, 20), 0);
        scroller.addView(chips);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2);
        sp.bottomMargin = dp(a, 10);
        root.addView(scroller, sp);

        final LinearLayout search = new LinearLayout(a);
        search.setOrientation(LinearLayout.HORIZONTAL);
        search.setGravity(Gravity.CENTER_VERTICAL);
        final GradientDrawable searchBg = new GradientDrawable();
        searchBg.setCornerRadius(dp(a, 26));
        searchBg.setColor(dark ? 0x26ffffff : 0x14000000);
        final int idle = dark ? 0x33ffffff : 0x1f000000;
        searchBg.setStroke(dp(a, 1), idle);
        search.setBackground(searchBg);
        ImageView lens = new ImageView(a);
        LauncherGlyph lensGlyph = new LauncherGlyph(a, 11);
        lensGlyph.setTintList(ColorStateList.valueOf(ink));
        lens.setImageDrawable(lensGlyph);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(a, 22), dp(a, 22));
        lp.leftMargin = dp(a, 16);
        lp.rightMargin = dp(a, 10);
        search.addView(lens, lp);
        final EditText field = new EditText(a);
        field.setBackground(null);
        field.setSingleLine(true);
        field.setHint("Search icons");
        field.setTextSize(16);
        field.setTextColor(ink);
        field.setHintTextColor((ink & 0xffffff) | 0x8c000000);
        field.setImeOptions(EditorInfo.IME_ACTION_SEARCH | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        field.setPadding(0, 0, 0, 0);
        MenuFont.apply(field);
        search.addView(field, new LinearLayout.LayoutParams(0, -1, 1f));
        final ImageView clear = new ImageView(a);
        LauncherGlyph clearGlyph = new LauncherGlyph(a, 12);
        clearGlyph.setTintList(ColorStateList.valueOf(ink));
        clear.setImageDrawable(clearGlyph);
        clear.setPadding(dp(a, 12), dp(a, 12), dp(a, 12), dp(a, 12));
        clear.setContentDescription("Clear search");
        clear.setVisibility(View.GONE);
        search.addView(clear, new LinearLayout.LayoutParams(dp(a, 44), dp(a, 44)));
        LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(-1, dp(a, 52));
        fp.leftMargin = fp.rightMargin = dp(a, 20);
        fp.bottomMargin = dp(a, 6);
        root.addView(search, fp);
        field.setOnFocusChangeListener((v, focused) -> searchBg.setStroke(dp(a, focused ? 2 : 1), focused ? accent : idle));

        FrameLayout box = new FrameLayout(a);
        final GridView grid = new GridView(a);
        grid.setColumnWidth(dp(a, 76));
        grid.setNumColumns(GridView.AUTO_FIT);
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setVerticalSpacing(dp(a, 6));
        grid.setHorizontalSpacing(dp(a, 6));
        grid.setSelector(new android.graphics.drawable.ColorDrawable(0));
        grid.setClipToPadding(false);
        grid.setPadding(dp(a, 12), dp(a, 4), dp(a, 12), dp(a, 12));
        final TextView empty = new TextView(a);
        empty.setGravity(Gravity.CENTER);
        empty.setTextSize(15);
        empty.setTextColor((ink & 0xffffff) | 0x99000000);
        MenuFont.apply(empty);
        box.addView(grid, new FrameLayout.LayoutParams(-1, -1));
        box.addView(empty, new FrameLayout.LayoutParams(-1, -1));
        grid.setEmptyView(empty);
        root.addView(box, new LinearLayout.LayoutParams(-1, (int) (a.getResources().getDisplayMetrics().heightPixels * 0.5f)));

        final State st = new State();
        final IconAdapter adapter = new IconAdapter(a, st, ink, accent);
        grid.setAdapter(adapter);

        final Runnable filter = () -> {
            String q = field.getText().toString().trim().toLowerCase(Locale.ROOT).replace(' ', '_');
            clear.setVisibility(q.isEmpty() ? View.GONE : View.VISIBLE);
            st.shown.clear();
            if (st.pack != null) {
                if (q.isEmpty()) {
                    st.matches = st.suggested.size();
                    st.shown.addAll(st.suggested);
                    for (String n : st.pack.all) if (!st.suggested.contains(n)) st.shown.add(n);
                } else {
                    st.matches = 0;
                    String loose = q.replace("_", "");
                    for (String n : st.pack.all) {
                        String l = n.toLowerCase(Locale.ROOT);
                        if (l.contains(q) || l.replace("_", "").contains(loose)) st.shown.add(n);
                    }
                }
            }
            empty.setText(st.pack == null ? "Loading…" : st.shown.isEmpty() ? "No icons match “" + field.getText().toString().trim() + "”" : "");
            adapter.notifyDataSetChanged();
            grid.setSelection(0);
        };
        field.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a1, int b, int c) { }
            public void onTextChanged(CharSequence s, int a1, int b, int c) { }
            public void afterTextChanged(Editable s) { filter.run(); }
        });
        field.setOnEditorActionListener((v, action, ev) -> { AppPicker.keyboard(field, false); return true; });
        clear.setOnClickListener(v -> field.setText(""));
        search.setOnClickListener(v -> { field.requestFocus(); AppPicker.keyboard(field, true); });

        final List<TextView> chipViews = new ArrayList<TextView>();
        String start = sActive;
        int first = 0;
        for (int i = 0; i < packs.size(); i++) if (packs.get(i).pkg.equals(start)) first = i;
        for (int i = 0; i < packs.size(); i++) {
            final IconPacks.Info info = packs.get(i);
            final TextView chip = pill(a, info.label, false, accent, ink, dark);
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-2, dp(a, 36));
            cp.rightMargin = dp(a, 8);
            chips.addView(chip, cp);
            chipViews.add(chip);
            chip.setOnClickListener(v -> {
                for (TextView t : chipViews) select(t, t == chip, accent, ink, dark);
                spring(chip);
                load(a, st, info.pkg, cn, label, filter);
            });
        }
        select(chipViews.get(first), true, accent, ink, dark);
        load(a, st, packs.get(first).pkg, cn, label, filter);

        grid.setOnItemClickListener((parent, view, position, id) -> {
            if (st.pack == null || position >= st.shown.size()) return;
            spring(view);
            final IconPacks.Pack pack = st.pack;
            final String name = st.shown.get(position);
            final Context c = a.getApplicationContext();
            WORK.execute(() -> {
                boolean ok = false;
                try {
                    Drawable d = pack.drawable(name);
                    if (d != null) {
                        Bitmap b = IconPacks.fill(IconPacks.render(d, 512));
                        File target = pickFile(c, key);
                        File dir = target.getParentFile();
                        if (dir != null && (dir.isDirectory() || dir.mkdirs())) {
                            write(b, dir, target);
                            File photo = LauncherIcon.file(c, key);
                            if (photo.exists()) photo.delete();
                            ok = true;
                        }
                        b.recycle();
                    }
                } catch (Throwable ignored) { }
                final boolean done = ok;
                MAIN.post(() -> {
                    if (!done) { Toast.makeText(a, "Could not use this icon", Toast.LENGTH_SHORT).show(); return; }
                    AppPicker.keyboard(field, false);
                    dialog.dismiss();
                    synchronized (LauncherIcon.cache) { LauncherIcon.retire(key); }
                    LauncherFeatures.refresh();
                    morph(LauncherIcon.chosen(key).icon.get());
                });
            });
        });
        grid.setOnScrollListener(new AbsListView.OnScrollListener() {
            public void onScrollStateChanged(AbsListView v, int s) { if (s != SCROLL_STATE_IDLE) AppPicker.keyboard(field, false); }
            public void onScroll(AbsListView v, int f, int n, int t) { }
        });

        if (dialog.getWindow() != null) dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        FrostedSurface.show(dialog);
        sheet(dialog);
        dialog.setOnDismissListener(d -> st.thumbs.evictAll());
    }

    private static void sheet(AlertDialog dialog) {
        try {
            Window w = dialog.getWindow();
            if (w == null) return;
            w.setGravity(Gravity.BOTTOM);
            WindowManager.LayoutParams p = w.getAttributes();
            p.width = dialog.getContext().getResources().getDisplayMetrics().widthPixels - dp(dialog.getContext(), 24);
            p.y = dp(dialog.getContext(), 12);
            w.setAttributes(p);
            final View decor = w.getDecorView();
            decor.setTranslationY(dp(dialog.getContext(), 80));
            decor.setAlpha(0f);
            decor.animate().translationY(0).alpha(1f).setDuration(380).setInterpolator(new PathInterpolator(0.05f, 0.7f, 0.1f, 1f)).start();
        } catch (Throwable ignored) { }
    }

    private static final class State {
        IconPacks.Pack pack;
        String loading;
        List<String> suggested = new ArrayList<String>();
        final List<String> shown = new ArrayList<String>();
        int matches;
        final LruCache<String, Bitmap> thumbs = new LruCache<String, Bitmap>(24 * 1024 * 1024) {
            @Override protected int sizeOf(String k, Bitmap b) { return b.getByteCount(); }
        };
    }

    private static void load(final Activity a, final State st, final String pkg, final ComponentName cn, final String label, final Runnable filter) {
        st.loading = pkg;
        st.pack = null;
        st.suggested = new ArrayList<String>();
        filter.run();
        final Context c = a.getApplicationContext();
        THUMBS.execute(() -> {
            final IconPacks.Pack p = IconPacks.get(c, pkg);
            final List<String> s = p == null ? new ArrayList<String>() : p.suggestions(cn, label);
            MAIN.post(() -> {
                if (!pkg.equals(st.loading)) return;
                if (p == null) { Toast.makeText(a, "Could not read this icon pack", Toast.LENGTH_SHORT).show(); return; }
                st.pack = p;
                st.suggested = s;
                filter.run();
            });
        });
    }

    private static final class IconAdapter extends BaseAdapter {
        final Activity a; final State st; final int ink, accent;
        IconAdapter(Activity a, State st, int ink, int accent) { this.a = a; this.st = st; this.ink = ink; this.accent = accent; }
        public int getCount() { return st.pack == null ? 0 : st.shown.size(); }
        public Object getItem(int i) { return st.shown.get(i); }
        public long getItemId(int i) { return i; }
        public View getView(int i, View old, ViewGroup parent) {
            Cell cell = old instanceof Cell ? (Cell) old : new Cell(a, ink);
            final String name = st.shown.get(i);
            final IconPacks.Pack pack = st.pack;
            final String id = pack.pkg + "/" + name;
            cell.tag = id;
            cell.label.setText(IconPacks.title(name));
            cell.setContentDescription(IconPacks.title(name));
            cell.mark(i < st.matches, accent);
            Bitmap cached = st.thumbs.get(id);
            if (cached != null) {
                cell.icon.setImageBitmap(cached);
                cell.icon.setAlpha(1f);
            } else {
                cell.icon.setImageDrawable(null);
                final Cell target = cell;
                final int size = dp(a, 56);
                THUMBS.execute(() -> {
                    if (!id.equals(target.tag)) return;
                    Drawable d = pack.drawable(name);
                    final Bitmap b = d == null ? null : IconPacks.render(d, size);
                    if (b != null) st.thumbs.put(id, b);
                    MAIN.post(() -> {
                        if (!id.equals(target.tag) || b == null) return;
                        target.icon.setImageBitmap(b);
                        target.icon.setAlpha(0f);
                        target.icon.animate().alpha(1f).setDuration(160).start();
                    });
                });
            }
            return cell;
        }
    }

    private static final class Cell extends LinearLayout {
        final ImageView icon; final TextView label; volatile String tag;
        Cell(Context c, int ink) {
            super(c);
            setOrientation(VERTICAL);
            setGravity(Gravity.CENTER_HORIZONTAL);
            setPadding(0, dp(c, 8), 0, dp(c, 6));
            icon = new ImageView(c);
            addView(icon, new LayoutParams(dp(c, 56), dp(c, 56)));
            label = new TextView(c);
            label.setTextSize(11);
            label.setTextColor((ink & 0xffffff) | 0xb3000000);
            label.setSingleLine(true);
            label.setEllipsize(TextUtils.TruncateAt.END);
            label.setGravity(Gravity.CENTER);
            MenuFont.apply(label);
            LayoutParams lp = new LayoutParams(-1, -2);
            lp.topMargin = dp(c, 4);
            addView(label, lp);
        }
        void mark(boolean match, int accent) {
            if (!match) { setBackground(null); return; }
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(dp(getContext(), 18));
            g.setColor((accent & 0xffffff) | 0x29000000);
            int in = dp(getContext(), 2);
            setBackground(new android.graphics.drawable.InsetDrawable(g, in, 0, in, 0));
        }
    }

    private static TextView pill(Context c, String text, boolean filled, int accent, int ink, boolean dark) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextSize(14);
        t.setGravity(Gravity.CENTER);
        t.setSingleLine(true);
        t.setPadding(dp(c, 16), 0, dp(c, 16), 0);
        MenuFont.apply(t);
        select(t, filled, accent, ink, dark);
        return t;
    }

    private static void select(TextView t, boolean on, int accent, int ink, boolean dark) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(t.getContext(), 18));
        if (on) {
            g.setColor(accent);
            t.setTextColor(luminance(accent) > 0.5 ? 0xff101114 : 0xffffffff);
        } else {
            g.setColor(0);
            g.setStroke(dp(t.getContext(), 1), dark ? 0x40ffffff : 0x33000000);
            t.setTextColor(ink);
        }
        t.setBackground(g);
    }

    private static double luminance(int color) {
        return (0.2126 * ((color >> 16) & 255) + 0.7152 * ((color >> 8) & 255) + 0.0722 * (color & 255)) / 255.0;
    }

    static final TimeInterpolator SPRING = t -> (float) (1 - Math.exp(-6.5 * t) * Math.cos(9.5 * t));

    private static void spring(View v) {
        if (v == null) return;
        v.animate().cancel();
        v.setScaleX(0.88f);
        v.setScaleY(0.88f);
        v.animate().scaleX(1f).scaleY(1f).setDuration(420).setInterpolator(SPRING).start();
    }

    private static void morph(final View icon) {
        if (icon == null || !icon.isAttachedToWindow()) return;
        icon.animate().cancel();
        icon.setScaleX(0.6f);
        icon.setScaleY(0.6f);
        icon.setAlpha(0.35f);
        icon.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(560).setInterpolator(SPRING).start();
    }

    private static void crossfade(final Activity a, final Runnable swap) {
        final Window w = a.getWindow();
        final View decor = w == null ? null : w.getDecorView();
        if (decor == null || decor.getWidth() == 0 || !(decor instanceof ViewGroup)) { swap.run(); return; }
        try {
            final Bitmap still = Bitmap.createBitmap(decor.getWidth(), decor.getHeight(), Bitmap.Config.ARGB_8888);
            Class<?> listenerType = Class.forName("android.view.PixelCopy$OnPixelCopyFinishedListener");
            Object listener = java.lang.reflect.Proxy.newProxyInstance(listenerType.getClassLoader(), new Class<?>[]{listenerType}, (self, m, args) -> {
                if ("onPixelCopyFinished".equals(m.getName())) {
                    int result = (Integer) args[0];
                    if (result != 0) { still.recycle(); swap.run(); return null; }
                    final ImageView cover = new ImageView(a);
                    cover.setImageBitmap(still);
                    cover.setScaleType(ImageView.ScaleType.FIT_XY);
                    ((ViewGroup) decor).addView(cover, new ViewGroup.LayoutParams(-1, -1));
                    swap.run();
                    cover.animate().alpha(0f).setStartDelay(60).setDuration(340).setInterpolator(new PathInterpolator(0.2f, 0f, 0f, 1f))
                            .setListener(new AnimatorListenerAdapter() {
                                @Override public void onAnimationEnd(Animator x) { ((ViewGroup) decor).removeView(cover); still.recycle(); }
                            }).start();
                    return null;
                }
                if ("hashCode".equals(m.getName())) return System.identityHashCode(self);
                if ("equals".equals(m.getName())) return self == args[0];
                return null;
            });
            Class.forName("android.view.PixelCopy").getMethod("request", Window.class, Bitmap.class, listenerType, Handler.class)
                    .invoke(null, w, still, listener, MAIN);
        } catch (Throwable t) {
            swap.run();
        }
    }

    static int dp(Context c, float v) { return FrostedSurface.dp(c, v); }
}
