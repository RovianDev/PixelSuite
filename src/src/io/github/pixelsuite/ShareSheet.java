package io.github.pixelsuite;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Pair;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class ShareSheet {
    private ShareSheet() {}

    static final String PACKAGE = "com.android.intentresolver";
    static final String KEY = "pixelsuite_share_sheet";
    private static final String PREFS = "pixelsuite_share";
    private static final String P_HIDDEN = "hidden", P_ORDER = "order";
    private static final int FOLDER_TYPE = 77, CFOLDER_TYPE = 78;
    private static final String P_CHIDDEN = "hidden_contacts", P_CORDER = "contact_order", P_CHIDE = "contacts_row_hidden", F_CSTASH = "ps_share_cstash";
    private static volatile Set<String> sHiddenContacts = Collections.emptySet();
    private static volatile Map<String, Integer> sCOrder = Collections.emptyMap();
    private static volatile List<String> sCOrderList = Collections.emptyList();
    private static volatile boolean sContactsHidden, sInHidden;
    private static final String F_TARGET = "ps_share_t", F_POS = "ps_share_pos", F_ADAPTER = "ps_share_la",
            F_FOLDER = "ps_share_folder", F_GRID = "ps_share_grid", F_STASH = "ps_share_stash", F_DIM = "ps_share_dim",
            F_CHECK = "ps_share_check", F_PINNED = "ps_share_pinned", F_LABELKEY = "ps_share_lk", F_PREVW = "ps_share_prevw";
    private static volatile Set<String> sHiddenPkgs = Collections.emptySet();

    private static volatile boolean sOn;
    private static volatile Set<String> sHidden = Collections.emptySet();
    private static volatile Map<String, Integer> sOrder = Collections.emptyMap();
    private static volatile List<String> sOrderList = Collections.emptyList();
    private static Class<?> cListAdapter, cDialog, cGrid, cRci;
    private static WeakReference<Activity> sAct = new WeakReference<>(null);
    private static Drag sDrag;
    private static boolean sSelecting;
    private static final LinkedHashMap<String, List<String>> sSel = new LinkedHashMap<>();
    private static int sFolderPop;
    private static Chrome sChrome;
    private static Object sBackCb;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Map<String, Drawable> ICONS = new HashMap<>();

    static void install(ClassLoader cl) {
        Class<?> act = Xp.findClass("com.android.intentresolver.ChooserActivity", cl);
        if (act == null) return;
        installTouchArea();
        cListAdapter = Xp.findClass("com.android.intentresolver.ChooserListAdapter", cl);
        cDialog = Xp.findClass("com.android.intentresolver.ChooserTargetActionsDialogFragment", cl);
        cGrid = Xp.findClass("com.android.intentresolver.grid.ChooserGridAdapter", cl);
        cRci = Xp.findClass("com.android.intentresolver.ResolvedComponentInfo", cl);
        if (cListAdapter == null || cGrid == null) return;

        Xp.hookAllMethods(act, "onCreate", new Xp.Callback() {
            @Override protected void beforeHookedMethod(Xp.Param p) {
                Activity a = (Activity) p.thisObject;
                sTh = null;
                sOn = Feature.on(a, KEY);
                if (sOn) load(a);
            }
            @Override protected void afterHookedMethod(Xp.Param p) {
                if (!sOn) return;
                final Activity a = (Activity) p.thisObject;
                sAct = new WeakReference<>(a);
                sSelecting = false; sSel.clear(); sDrag = null;
                a.getWindow().getDecorView().post(() -> attachChrome(a));
            }
        });
        Xp.hookAllMethods(act, "onDestroy", new Xp.Callback() {
            @Override protected void beforeHookedMethod(Xp.Param p) {
                if (sAct.get() != p.thisObject) return;
                sDrag = null; sSelecting = false; sSel.clear(); setBack((Activity) p.thisObject, false);
                if (sChrome != null && sChrome.act == p.thisObject) sChrome = null;
            }
        });
        Xp.hookAllMethods(Activity.class, "dispatchTouchEvent", new Xp.Callback() {
            @Override protected void beforeHookedMethod(Xp.Param p) {
                Drag d = sDrag;
                if (d == null || d.act != p.thisObject || !(p.args[0] instanceof MotionEvent)) return;
                d.onTouch((MotionEvent) p.args[0]);
                p.setResult(Boolean.TRUE);
            }
        });

        Class<?> cmp = Xp.findClass("com.android.intentresolver.model.AbstractResolverComparator", cl);
        if (cmp != null && cRci != null) Xp.hookAllMethods(cmp, "compare", new Xp.Callback() {
            @Override protected void beforeHookedMethod(Xp.Param p) {
                if (!sOn || p.args.length != 2 || !cRci.isInstance(p.args[0]) || !cRci.isInstance(p.args[1])) return;
                Map<String, Integer> o = sOrder;
                if (o.isEmpty()) return;
                Object a = p.args[0], b = p.args[1];
                if (!Xp.getBooleanField(a, "mPinned") || !Xp.getBooleanField(b, "mPinned")) return;
                ResolveInfo ra = (ResolveInfo) Xp.callMethod(a, "getResolveInfoAt"), rb = (ResolveInfo) Xp.callMethod(b, "getResolveInfoAt");
                if (ra == null || rb == null || userId(ra) != -2 || userId(rb) != -2) return;
                Integer ia = o.get(flat(Xp.getObjectField(a, "name"))), ib = o.get(flat(Xp.getObjectField(b, "name")));
                if (ia == null && ib == null) return;
                if (ia == null) { p.setResult(1); return; }
                if (ib == null) { p.setResult(-1); return; }
                p.setResult(Integer.compare(ia, ib));
            }
        });

        Class<?> rla = Xp.findClass("com.android.intentresolver.ResolverListAdapter", cl);
        if (rla != null) {
            Xp.hookAllMethods(rla, "processSortedList", new Xp.Callback() {
                @Override protected void beforeHookedMethod(Xp.Param p) {
                    if (!cListAdapter.isInstance(p.thisObject)) return;
                    stash(p.thisObject).clear();
                    cstash(p.thisObject).clear();
                    if (sOn && p.args.length == 1 && p.args[0] instanceof List) {
                        try {
                            List<?> in = (List<?>) p.args[0];
                            List<Object> pinned = new ArrayList<>(), rest = new ArrayList<>();
                            for (Object o : in) {
                                boolean pin = false;
                                try { pin = Xp.getBooleanField(o, "mPinned") && userId((ResolveInfo) Xp.callMethod(o, "getResolveInfoAt")) == -2; } catch (Throwable ignored) { }
                                (pin ? pinned : rest).add(o);
                            }
                            if (pinned.size() < 2 && (pinned.isEmpty() || in.indexOf(pinned.get(0)) == 0)) return;
                            final Map<String, Integer> o = sOrder;
                            Collections.sort(pinned, (a, b) -> {
                                Integer ia = o.get(flat(Xp.getObjectField(a, "name"))), ib = o.get(flat(Xp.getObjectField(b, "name")));
                                if (ia != null || ib != null) return ia == null ? 1 : ib == null ? -1 : Integer.compare(ia, ib);
                                String pa = String.valueOf(((ComponentName) Xp.getObjectField(a, "name")).getPackageName());
                                String pb = String.valueOf(((ComponentName) Xp.getObjectField(b, "name")).getPackageName());
                                return pa.compareToIgnoreCase(pb);
                            });
                            List<Object> out = new ArrayList<>(pinned);
                            out.addAll(rest);
                            p.args[0] = out;
                        } catch (Throwable ignored) { }
                    }
                }
            });
            Xp.hookAllMethods(rla, "addResolveInfo", new Xp.Callback() {
                @Override protected void beforeHookedMethod(Xp.Param p) {
                    if (!sOn || !cListAdapter.isInstance(p.thisObject) || p.args.length != 1 || p.args[0] == null) return;
                    Object dri = p.args[0];
                    if (Xp.getBooleanField(dri, "mPinned")) return;
                    String c = flat(Xp.callMethod(dri, "getResolvedComponentName"));
                    if (c == null) return;
                    String lk = null;
                    if (!sHidden.contains(c)) {
                        if (!sHiddenPkgs.contains(ComponentName.unflattenFromString(c).getPackageName())) return;
                        lk = labelKey((ResolveInfo) Xp.getObjectField(dri, "mResolveInfo"), ((Context) Xp.getObjectField(p.thisObject, "mContext")).getPackageManager());
                        if (lk == null || !sHidden.contains(lk)) return;
                    }
                    List<Object> s = stash(p.thisObject);
                    for (Object o : s) {
                        if (c.equals(flat(Xp.callMethod(o, "getResolvedComponentName")))) { p.setResult(null); return; }
                        if (lk != null && lk.equals(Xp.getAdditionalInstanceField(o, F_LABELKEY))) { p.setResult(null); return; }
                    }
                    if (lk != null) Xp.setAdditionalInstanceField(dri, F_LABELKEY, lk);
                    s.add(dri);
                    p.setResult(null);
                }
            });
        }
        Xp.hookAllMethods(cListAdapter, "updateList", new Xp.Callback() {
            @Override protected void afterHookedMethod(Xp.Param p) {
                if (!sOn || !(p.getResult() instanceof List)) return;
                try {
                    Object la = p.thisObject;
                    int ranked = (Integer) Xp.callMethod(la, "getRankedTargetCount");
                    List<?> dl = new ArrayList<>((List<?>) Xp.getObjectField(la, "mDisplayList"));
                    Set<String> top = new HashSet<>();
                    for (int i = 0; i < Math.min(ranked, dl.size()); i++) {
                        String c = flat(Xp.callMethod(dl.get(i), "getResolvedComponentName")); if (c != null) top.add(c);
                    }
                    try { for (Object o : (List<?>) Xp.getObjectField(la, "mCallerTargets")) { String c = flat(Xp.callMethod(o, "getResolvedComponentName")); if (c != null) top.add(c); } } catch (Throwable ignored) { }
                    if (top.isEmpty()) return;
                    List<Object> out = new ArrayList<>();
                    for (Object o : (List<?>) p.getResult()) {
                        if (!bool(o, "isMultiDisplayResolveInfo") && top.contains(flat(Xp.callMethod(o, "getResolvedComponentName")))) continue;
                        out.add(o);
                    }
                    p.setResult(out);
                } catch (Throwable ignored) { }
            }
        });
        if (rla != null) Xp.hookAllMethods(rla, "processSortedList", new Xp.Callback() {
            @Override protected void afterHookedMethod(Xp.Param p) {
                if (!cListAdapter.isInstance(p.thisObject)) return;
                int n = 0;
                try { for (Object o : (List<?>) Xp.getObjectField(p.thisObject, "mDisplayList")) if (Xp.getBooleanField(o, "mPinned")) n++; } catch (Throwable ignored) { }
                Xp.setAdditionalInstanceField(p.thisObject, F_PINNED, n);
            }
        });
        Xp.hookAllMethods(cListAdapter, "getRankedTargetCount", new Xp.Callback() {
            @Override protected void afterHookedMethod(Xp.Param p) {
                if (!sOn) return;
                Object n = Xp.getAdditionalInstanceField(p.thisObject, F_PINNED);
                if (!(n instanceof Integer) || (Integer) n == 0) return;
                try {
                    int pinned = (Integer) n;
                    int want = pinned;
                    int count = ((List<?>) Xp.getObjectField(p.thisObject, "mDisplayList")).size();
                    if (Xp.getBooleanField(p.thisObject, "mFilterLastUsed") && (Integer) Xp.getObjectField(p.thisObject, "mLastChosenPosition") >= 0) count--;
                    p.setResult(Math.max(0, Math.min(want, count)));
                } catch (Throwable ignored) { }
            }
        });
        Xp.hookAllMethods(cListAdapter, "addServiceResults", new Xp.Callback() {
            @Override protected void beforeHookedMethod(Xp.Param p) {
                if (!sOn || sInHidden || p.args.length != 5 || !(p.args[1] instanceof List)) return;
                List<?> in = (List<?>) p.args[1];
                if (in.isEmpty() || sHiddenContacts.isEmpty()) return;
                Map<?, ?> m = p.args[3] instanceof Map ? (Map<?, ?>) p.args[3] : null;
                List<Object> keep = new ArrayList<>(), hid = new ArrayList<>();
                for (Object ct : in) { String k = chooserTargetKey(ct, m); (k != null && sHiddenContacts.contains(k) ? hid : keep).add(ct); }
                if (hid.isEmpty()) return;
                Object la = p.thisObject;
                try {
                    Field f = cListAdapter.getDeclaredField("mServiceTargets");
                    f.setAccessible(true);
                    Object orig = f.get(la);
                    List<Object> side = new ArrayList<>();
                    f.set(la, side);
                    sInHidden = true;
                    try { Xp.callMethod(la, "addServiceResults", p.args[0], hid, p.args[2], p.args[3], p.args[4]); }
                    finally { sInHidden = false; f.set(la, orig); }
                    List<Object> cs = cstash(la);
                    for (Object t : side) {
                        if (t == null || !bool(t, "isSelectableTargetInfo")) continue;
                        String k = contactKey(t);
                        boolean dup = false;
                        for (Object o : cs) if (k != null && k.equals(contactKey(o))) { dup = true; break; }
                        if (!dup) cs.add(t);
                    }
                } catch (Throwable ignored) { }
                p.args[1] = keep;
            }
            @Override protected void afterHookedMethod(Xp.Param p) {
                if (sOn && !sInHidden) orderContacts(p.thisObject);
            }
        });
        Xp.Callback noContacts = new Xp.Callback() {
            @Override protected void afterHookedMethod(Xp.Param p) { if (sOn && sContactsHidden) p.setResult(0); }
        };
        Xp.hookAllMethods(cListAdapter, "getServiceTargetCount", noContacts);
        Xp.hookAllMethods(cListAdapter, "getSelectableServiceTargetCount", noContacts);
        Xp.hookAllMethods(cGrid, "getListPosition", new Xp.Callback() {
            @Override protected void beforeHookedMethod(Xp.Param p) { syncDirect(p.thisObject); }
        });

        Class<?> divider = Xp.findClass("com.android.intentresolver.grid.AllAppDividerDecoration", cl);
        if (divider != null) Xp.hookAllMethods(divider, "onDraw", new Xp.Callback() {
            @Override protected void beforeHookedMethod(Xp.Param p) {
                if (sOn) try { Xp.setBooleanField(p.thisObject, "showDivider", true); } catch (Throwable ignored) { }
            }
        });
        Xp.hookAllMethods(cListAdapter, "onBindView", new Xp.Callback() {
            @Override protected void beforeHookedMethod(Xp.Param p) {
                if (!sOn || p.args.length != 3 || !(p.args[0] instanceof View)) return;
                View v = (View) p.args[0];
                Object prev = Xp.getAdditionalInstanceField(v, F_TARGET);
                View tv = v.findViewById(android.R.id.text1);
                int w = tv != null && tv.getLayoutParams() != null ? tv.getLayoutParams().width : -1;
                Xp.setAdditionalInstanceField(v, F_PREVW, prev != null && w > 0 ? new Object[]{prev, w} : null);
            }
            @Override protected void afterHookedMethod(Xp.Param p) {
                if (!sOn || p.args.length != 3 || !(p.args[0] instanceof View)) return;
                View v = (View) p.args[0];
                try {
                    Object[] prev = (Object[]) Xp.getAdditionalInstanceField(v, F_PREVW);
                    Object t = p.args[1];
                    View tv = v.findViewById(android.R.id.text1);
                    if (tv instanceof TextView) oneLine(v, (TextView) tv);
                    if (t != null && tv instanceof TextView && tv.getBackground() != null && bool(t, "isPinned")) {
                        int fitted = fitPinnedLabel(v, (TextView) tv);
                        if (fitted <= 0 && prev != null && (prev[0] == t || key(comps(prev[0])).equals(key(comps(t))))) fitted = (Integer) prev[1];
                        if (fitted > 0) {
                            tv.removeOnLayoutChangeListener((View.OnLayoutChangeListener) Xp.getObjectField(p.thisObject, "mPinTextSpacingListener"));
                            ViewGroup.LayoutParams lp = tv.getLayoutParams();
                            lp.width = fitted;
                            tv.setLayoutParams(lp);
                        }
                    }
                } catch (Throwable ignored) { }
                Xp.setAdditionalInstanceField(v, F_TARGET, p.args[1]);
                Xp.setAdditionalInstanceField(v, F_POS, p.args[2]);
                Xp.setAdditionalInstanceField(v, F_ADAPTER, p.thisObject);
                decorate(v);
            }
        });

        Xp.hookAllMethods(cGrid, "getItemCount", new Xp.Callback() {
            @Override protected void beforeHookedMethod(Xp.Param p) { syncDirect(p.thisObject); }
            @Override protected void afterHookedMethod(Xp.Param p) {
                int n = (folderShown(p.thisObject) ? 1 : 0) + (contactsFolderShown(p.thisObject) ? 1 : 0);
                if (n > 0 && p.getResult() instanceof Integer) p.setResult((Integer) p.getResult() + n);
            }
        });
        Xp.hookAllMethods(cGrid, "getItemViewType", new Xp.Callback() {
            @Override protected void beforeHookedMethod(Xp.Param p) {
                syncDirect(p.thisObject);
                int t = folderAt(p.thisObject, (Integer) p.args[0]);
                if (t != 0) p.setResult(t);
            }
        });
        Xp.hookAllMethods(cGrid, "onCreateViewHolder", new Xp.Callback() {
            @Override protected void beforeHookedMethod(Xp.Param p) {
                sMakingFolder = p.args.length == 2 && (Integer.valueOf(FOLDER_TYPE).equals(p.args[1]) || Integer.valueOf(CFOLDER_TYPE).equals(p.args[1])) ? (Integer) p.args[1] : 0;
                if (sMakingFolder != 0) p.args[1] = 1;
            }
            @Override protected void afterHookedMethod(Xp.Param p) {
                int type = sMakingFolder;
                if (type == 0) return;
                sMakingFolder = 0;
                if (p.getResult() != null) makeFolder(p.getResult(), type);
            }
        });
        Xp.hookAllMethods(cGrid, "onBindViewHolder", new Xp.Callback() {
            @Override protected void beforeHookedMethod(Xp.Param p) {
                if (p.args.length < 2 || p.args[0] == null) return;
                syncDirect(p.thisObject);
                Object t = Xp.getObjectField(p.args[0], "mViewType");
                if (Integer.valueOf(FOLDER_TYPE).equals(t) || Integer.valueOf(CFOLDER_TYPE).equals(t)) { bindFolder(p.thisObject, p.args[0], (Integer) t); p.setResult(null); return; }
                if (sOn && Integer.valueOf(5).equals(t)) try { rowBefore(p.args[0]); } catch (Throwable ignored) { }
            }
            @Override protected void afterHookedMethod(Xp.Param p) {
                if (!sOn || p.args.length < 2 || p.args[0] == null) return;
                try { if (Integer.valueOf(5).equals(Xp.getObjectField(p.args[0], "mViewType"))) centreRow(p.args[0]); } catch (Throwable ignored) { }
            }
        });
        Class<?> span = Xp.findClass("com.android.intentresolver.profiles.ChooserMultiProfilePagerAdapter$ChooserProfileAdapterBinder$1", cl);
        if (span != null) Xp.hookAllMethods(span, "getSpanSize", new Xp.Callback() {
            @Override protected void afterHookedMethod(Xp.Param p) {
                if (!sOn || Integer.valueOf(1).equals(p.getResult())) return;
                Object g = captured(p.thisObject, cGrid);
                if (g != null && folderAt(g, (Integer) p.args[0]) != 0) p.setResult(1);
            }
        });

        Class<?> rowLong = Xp.findClass("com.android.intentresolver.grid.ChooserGridAdapter$$ExternalSyntheticLambda3", cl);
        if (rowLong != null) Xp.hookAllMethods(rowLong, "onLongClick", new Xp.Callback() {
            @Override protected void beforeHookedMethod(Xp.Param p) {
                if (!sOn) return;
                if (sSelecting) { p.setResult(Boolean.TRUE); return; }
                try {
                    Object grid = Xp.getObjectField(p.thisObject, "f$0"), holder = Xp.getObjectField(p.thisObject, "f$1");
                    int idx = (Integer) Xp.getObjectField(p.thisObject, "f$2");
                    if (startDrag(grid, holder, idx)) p.setResult(Boolean.TRUE);
                } catch (Throwable ignored) { }
            }
        });
        Class<?> itemLong = Xp.findClass("com.android.intentresolver.grid.ItemViewHolder$$ExternalSyntheticLambda1", cl);
        if (itemLong != null) Xp.hookAllMethods(itemLong, "onLongClick", new Xp.Callback() {
            @Override protected void beforeHookedMethod(Xp.Param p) { if (sOn && sSelecting) p.setResult(Boolean.TRUE); }
        });
        Xp.Callback tap = new Xp.Callback() {
            @Override protected void beforeHookedMethod(Xp.Param p) {
                if (!sOn || !sSelecting || p.args.length != 1 || !(p.args[0] instanceof View)) return;
                toggle((View) p.args[0]);
                p.setResult(null);
            }
        };
        Class<?> rowTap = Xp.findClass("com.android.intentresolver.grid.ChooserGridAdapter$$ExternalSyntheticLambda2", cl);
        if (rowTap != null) Xp.hookAllMethods(rowTap, "onClick", tap);
        Class<?> itemTap = Xp.findClass("com.android.intentresolver.grid.ItemViewHolder$$ExternalSyntheticLambda0", cl);
        if (itemTap != null) Xp.hookAllMethods(itemTap, "onClick", tap);

        if (cDialog != null) {
            Xp.hookAllMethods(cDialog, "onCreateView", new Xp.Callback() {
                @Override protected void afterHookedMethod(Xp.Param p) {
                    if (!sOn || p.thisObject.getClass() != cDialog || !(p.getResult() instanceof View)) return;
                    boolean contact = Xp.getObjectField(p.thisObject, "mShortcutId") != null;
                    List<?> t = (List<?>) Xp.getObjectField(p.thisObject, "mTargetInfos");
                    if (t == null || t.isEmpty()) return;
                    if (contact) {
                        if (Xp.getBooleanField(p.thisObject, "mIsShortcutPinned")) return;
                    } else {
                        for (Object o : t) if (Xp.getBooleanField(o, "mPinned")) return;
                        Activity a = activityOf(p.thisObject);
                        if (a != null && isCallerTarget(a, t)) return;
                    }
                    View rv = findByClass((View) p.getResult(), "RecyclerView");
                    if (rv == null) return;
                    Object ad = Xp.getObjectField(rv, "mAdapter");
                    if (ad == null) return;
                    List<Object> items = new ArrayList<>((List<?>) Xp.getObjectField(ad, "mItems"));
                    items.add(new Pair<Object, Object>(new Glyph(Glyph.EYE_OFF, Color.BLACK, ((View) p.getResult()).getResources()), contact ? "Hide contact" : "Hide app"));
                    Xp.setObjectField(ad, "mItems", items);
                }
            });
            Xp.hookAllMethods(cDialog, "onClick", new Xp.Callback() {
                @Override protected void beforeHookedMethod(Xp.Param p) {
                    if (!sOn || p.thisObject.getClass() != cDialog || p.args.length != 2) return;
                    List<?> t = (List<?>) Xp.getObjectField(p.thisObject, "mTargetInfos");
                    if (t == null) return;
                    if ((Integer) p.args[1] < t.size()) {
                        if (Xp.getObjectField(p.thisObject, "mShortcutId") == null) {
                            try { Fly.start(activityOf(p.thisObject), t.get((Integer) p.args[1])); } catch (Throwable ignored) { }
                        }
                        return;
                    }
                    p.setResult(null);
                    Object sid = Xp.getObjectField(p.thisObject, "mShortcutId");
                    if (sid != null) {
                        String pkg = null;
                        try { pkg = ((ComponentName) Xp.callMethod(t.get(0), "getResolvedComponentName")).getPackageName(); } catch (Throwable ignored) { }
                        final Activity a = activityOf(p.thisObject);
                        try { Xp.callMethod(p.thisObject, "dismissInternal", false, false); } catch (Throwable ignored) { }
                        if (a != null && pkg != null) hideContacts(a, Collections.singletonList(pkg + "|" + sid));
                        return;
                    }
                    List<String> comps = new ArrayList<>();
                    for (Object o : t) { String c = flat(Xp.callMethod(o, "getResolvedComponentName")); if (c != null) comps.add(c); }
                    final Activity a = activityOf(p.thisObject);
                    try { Xp.callMethod(p.thisObject, "dismissInternal", false, false); } catch (Throwable ignored) { }
                    if (a != null) hideApps(a, comps, null);
                }
            });
        }
    }

    private static SharedPreferences prefs(Context c) { return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }

    private static void load(Context c) {
        SharedPreferences sp = prefs(c);
        Set<String> h = new HashSet<>(sp.getStringSet(P_HIDDEN, Collections.<String>emptySet()));
        PackageManager pm = c.getPackageManager();
        boolean pruned = false;
        for (String s : new ArrayList<>(h)) {
            String pkg = pkgOf(s);
            boolean gone = pkg == null;
            if (!gone) { try { pm.getPackageInfo(pkg, 0); } catch (PackageManager.NameNotFoundException e) { gone = true; } catch (Throwable ignored) { } }
            if (gone) { h.remove(s); pruned = true; }
        }
        for (String s : new ArrayList<>(h)) {
            if (s.startsWith(LABEL)) continue;
            String lk = labelKey(ComponentName.unflattenFromString(s), pm);
            if (lk != null && h.add(lk)) pruned = true;
        }
        if (pruned) sp.edit().putStringSet(P_HIDDEN, new HashSet<>(h)).apply();
        setHidden(h);
        setOrder(split(sp.getString(P_ORDER, "")));
        Set<String> hc = new HashSet<>(sp.getStringSet(P_CHIDDEN, Collections.<String>emptySet()));
        boolean cpruned = false;
        for (String k : new ArrayList<>(hc)) {
            int bar = k.indexOf('|');
            String pkg = bar > 0 ? k.substring(0, bar) : null;
            if (pkg != null && pkg.indexOf('/') > 0) pkg = pkg.substring(0, pkg.indexOf('/'));
            boolean gone = pkg == null;
            if (!gone) { try { pm.getPackageInfo(pkg, 0); } catch (PackageManager.NameNotFoundException e) { gone = true; } catch (Throwable ignored) { } }
            if (gone) { hc.remove(k); cpruned = true; }
        }
        if (cpruned) sp.edit().putStringSet(P_CHIDDEN, new HashSet<>(hc)).apply();
        sHiddenContacts = hc;
        setCOrder(split(sp.getString(P_CORDER, "")));
        sContactsHidden = sp.getBoolean(P_CHIDE, false);
    }

    private static void setOrder(List<String> list) {
        Map<String, Integer> m = new HashMap<>();
        for (int i = 0; i < list.size(); i++) m.put(list.get(i), i);
        sOrderList = list; sOrder = m;
    }

    private static void setCOrder(List<String> list) {
        Map<String, Integer> m = new HashMap<>();
        for (int i = 0; i < list.size(); i++) m.put(list.get(i), i);
        sCOrderList = list; sCOrder = m;
    }

    static String contactKey(Object t) {
        try {
            Object si = Xp.callMethod(t, "getDirectShareShortcutInfo");
            if (si != null) return Xp.callMethod(si, "getPackage") + "|" + Xp.callMethod(si, "getId");
            return flat(Xp.callMethod(t, "getChooserTargetComponentName")) + "|" + Xp.callMethod(t, "getDisplayLabel");
        } catch (Throwable e) { return null; }
    }

    private static String chooserTargetKey(Object ct, Map<?, ?> shortcuts) {
        try {
            Object si = shortcuts == null ? null : shortcuts.get(ct);
            if (si != null) return Xp.callMethod(si, "getPackage") + "|" + Xp.callMethod(si, "getId");
            return flat(Xp.callMethod(ct, "getComponentName")) + "|" + Xp.callMethod(ct, "getTitle");
        } catch (Throwable e) { return null; }
    }

    @SuppressWarnings("unchecked")
    private static List<Object> cstash(Object la) {
        Object s = Xp.getAdditionalInstanceField(la, F_CSTASH);
        if (s == null) { s = new ArrayList<Object>(); Xp.setAdditionalInstanceField(la, F_CSTASH, s); }
        return (List<Object>) s;
    }

    @SuppressWarnings("unchecked")
    private static void orderContacts(Object la) {
        try {
            final Map<String, Integer> o = sCOrder;
            if (o.isEmpty()) return;
            List<Object> st = (List<Object>) Xp.getObjectField(la, "mServiceTargets");
            List<Integer> at = new ArrayList<>(); List<Object> pins = new ArrayList<>();
            for (int i = 0; i < st.size(); i++) { Object t = st.get(i); if (t != null && bool(t, "isSelectableTargetInfo") && bool(t, "isPinned")) { at.add(i); pins.add(t); } }
            if (pins.size() < 2) return;
            List<Object> sorted = new ArrayList<>(pins);
            Collections.sort(sorted, (x, y) -> {
                Integer a = o.get(contactKey(x)), b = o.get(contactKey(y));
                if (a == null && b == null) return 0;
                return a == null ? 1 : b == null ? -1 : Integer.compare(a, b);
            });
            if (sorted.equals(pins)) return;
            for (int j = 0; j < at.size(); j++) st.set(at.get(j), sorted.get(j));
            Xp.callMethod(la, "notifyDataSetChanged");
        } catch (Throwable ignored) { }
    }

    private static List<String> split(String s) {
        List<String> out = new ArrayList<>();
        if (s != null) for (String x : s.split("\n")) if (!x.trim().isEmpty() && !out.contains(x.trim())) out.add(x.trim());
        return out;
    }

    private static final String LABEL = "L:";

    private static String pkgOf(String entry) {
        if (entry == null) return null;
        if (entry.startsWith(LABEL)) { int bar = entry.indexOf('|'); return bar > LABEL.length() ? entry.substring(LABEL.length(), bar) : null; }
        ComponentName cn = ComponentName.unflattenFromString(entry);
        return cn == null ? null : cn.getPackageName();
    }

    private static String labelKey(ResolveInfo ri, PackageManager pm) {
        try {
            if (ri == null || ri.activityInfo == null) return null;
            CharSequence l = ri.activityInfo.loadLabel(pm);
            return l == null ? null : LABEL + ri.activityInfo.packageName + "|" + l.toString().trim();
        } catch (Throwable t) { return null; }
    }

    private static String labelKey(ComponentName cn, PackageManager pm) {
        try {
            if (cn == null) return null;
            CharSequence l = pm.getActivityInfo(cn, 0).loadLabel(pm);
            return l == null ? null : LABEL + cn.getPackageName() + "|" + l.toString().trim();
        } catch (Throwable t) { return null; }
    }

    private static void setHidden(Set<String> h) {
        Set<String> pk = new HashSet<>();
        for (String s : h) if (s.startsWith(LABEL)) { String p = pkgOf(s); if (p != null) pk.add(p); }
        sHiddenPkgs = pk;
        sHidden = h;
    }

    private static void saveHidden(Context c, Set<String> h) {
        setHidden(h);
        prefs(c).edit().putStringSet(P_HIDDEN, new HashSet<>(h)).apply();
    }

    private static final int TAG_LABEL = 0x7f7ffd51;
    private static void oneLine(View cell, TextView tv) {
        Object base = tv.getTag(TAG_LABEL);
        if (base instanceof Float && Math.abs(tv.getTextSize() - (Float) base) > 0.1f) tv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, (Float) base);
        else if (!(base instanceof Float)) tv.setTag(TAG_LABEL, tv.getTextSize());
        tv.setMaxLines(1);
        tv.setEllipsize(android.text.TextUtils.TruncateAt.END);
    }

    private static int fitPinnedLabel(View cell, TextView tv) {
        try {
            int avail = cell.getWidth() - cell.getPaddingLeft() - cell.getPaddingRight();
            if (avail <= 0) return 0;
            tv.measure(View.MeasureSpec.makeMeasureSpec(avail, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            android.text.Layout l = tv.getLayout();
            if (l == null || l.getLineCount() == 0) return 0;
            int max = 0;
            for (int i = 0; i < l.getLineCount(); i++) max = Math.max((int) Math.ceil(l.getLineMax(i)), max);
            int w = tv.getPaddingLeft() + tv.getPaddingRight() + max;
            return w < avail ? w : 0;
        } catch (Throwable t) { return 0; }
    }

    @SuppressWarnings("unchecked")
    private static List<Object> stash(Object la) {
        Object s = Xp.getAdditionalInstanceField(la, F_STASH);
        if (s == null) { s = new ArrayList<Object>(); Xp.setAdditionalInstanceField(la, F_STASH, s); }
        return (List<Object>) s;
    }

    private static boolean folderShown(Object grid) {
        if (!sOn || grid == null) return false;
        Object la = Xp.getObjectField(grid, "mChooserListAdapter");
        return la != null && Xp.getBooleanField(la, "mAppTargetsReady") && !stash(la).isEmpty();
    }

    private static boolean contactsFolderShown(Object grid) {
        if (!sOn || grid == null || sContactsHidden) return false;
        Object la = Xp.getObjectField(grid, "mChooserListAdapter");
        return la != null && Xp.getBooleanField(la, "mAppTargetsReady") && !cstash(la).isEmpty();
    }

    private static int folderAt(Object grid, int i) {
        boolean apps = folderShown(grid), contacts = contactsFolderShown(grid);
        if (!apps && !contacts) return 0;
        int b = base(grid);
        if (apps) { if (i == b) return FOLDER_TYPE; b++; }
        if (contacts && i == b) return CFOLDER_TYPE;
        return 0;
    }

    private static void syncDirect(Object grid) {
        try {
            Object o = Xp.getAdditionalInstanceField(grid, "ps_share_direct");
            boolean orig;
            if (o == null) { orig = Xp.getBooleanField(grid, "mShouldShowDirectTargets"); Xp.setAdditionalInstanceField(grid, "ps_share_direct", orig); }
            else orig = (Boolean) o;
            boolean want = orig && !(sOn && sContactsHidden);
            if (Xp.getBooleanField(grid, "mShouldShowDirectTargets") != want) Xp.setBooleanField(grid, "mShouldShowDirectTargets", want);
        } catch (Throwable ignored) { }
    }

    private static int base(Object grid) {
        Object la = Xp.getObjectField(grid, "mChooserListAdapter");
        int alpha = (Integer) Xp.callMethod(la, "getAlphaTargetCount");
        int rows = (Integer) Xp.callMethod(grid, "getCallerAndRankedTargetRowCount");
        return alpha + rows + (Xp.getBooleanField(grid, "mShouldShowDirectTargets") ? 1 : 0);
    }

    private static final Map<Class<?>, Field> CAPTURED = new HashMap<>();
    private static Object captured(Object o, Class<?> type) {
        try {
            Field f;
            synchronized (CAPTURED) {
                f = CAPTURED.get(o.getClass());
                if (f == null && !CAPTURED.containsKey(o.getClass())) {
                    for (Field x : o.getClass().getDeclaredFields()) if (type.isAssignableFrom(x.getType())) { x.setAccessible(true); f = x; break; }
                    CAPTURED.put(o.getClass(), f);
                }
            }
            return f == null ? null : f.get(o);
        } catch (Throwable t) { return null; }
    }

    private static String flat(Object cn) { return cn instanceof ComponentName ? ((ComponentName) cn).flattenToString() : null; }

    private static int userId(ResolveInfo ri) { try { return (Integer) Xp.getObjectField(ri, "targetUserId"); } catch (Throwable t) { return -2; } }

    private static boolean bool(Object o, String m) { try { return Boolean.TRUE.equals(Xp.callMethod(o, m)); } catch (Throwable t) { return false; } }

    private static List<String> comps(Object t) {
        List<String> out = new ArrayList<>();
        try {
            List<?> all = (List<?>) Xp.callMethod(t, "getAllDisplayTargets");
            if (all != null) for (Object o : all) { String c = flat(Xp.callMethod(o, "getResolvedComponentName")); if (c != null && !out.contains(c)) out.add(c); }
        } catch (Throwable ignored) { }
        if (out.isEmpty()) { String c = flat(Xp.callMethod(t, "getResolvedComponentName")); if (c != null) out.add(c); }
        return out;
    }

    private static String key(List<String> comps) { List<String> s = new ArrayList<>(comps); Collections.sort(s); return TextUtils.join("|", s); }

    private static Activity activityOf(Object fragment) {
        try { return (Activity) Xp.getObjectField(Xp.getObjectField(fragment, "mHost"), "activity"); } catch (Throwable t) { return null; }
    }

    private static Activity activityOf(Context c) {
        while (c instanceof ContextWrapper) { if (c instanceof Activity) return (Activity) c; c = ((ContextWrapper) c).getBaseContext(); }
        return null;
    }

    private static Object activeAdapter(Activity a) {
        try { return Xp.callMethod(Xp.getObjectField(a, "mChooserMultiProfilePagerAdapter"), "getActiveListAdapter"); } catch (Throwable t) { return null; }
    }

    private static boolean isCallerTarget(Activity a, List<?> targets) {
        Object la = activeAdapter(a);
        if (la == null) return false;
        List<?> callers = (List<?>) Xp.getObjectField(la, "mCallerTargets");
        if (callers == null) return false;
        for (Object c : callers) {
            String cc = flat(Xp.callMethod(c, "getResolvedComponentName"));
            for (Object t : targets) if (c == t || (cc != null && cc.equals(flat(Xp.callMethod(t, "getResolvedComponentName"))))) return true;
        }
        return false;
    }

    private static View findByClass(View v, String simple) {
        for (Class<?> k = v.getClass(); k != null && k != View.class; k = k.getSuperclass()) if (k.getSimpleName().equals(simple)) return v;
        if (v instanceof ViewGroup) { ViewGroup g = (ViewGroup) v; for (int i = 0; i < g.getChildCount(); i++) { View r = findByClass(g.getChildAt(i), simple); if (r != null) return r; } }
        return null;
    }

    private static View shownByClass(View v, String simple) {
        if (!v.isShown()) return null;
        for (Class<?> k = v.getClass(); k != null && k != View.class; k = k.getSuperclass()) if (k.getSimpleName().equals(simple)) return v;
        if (v instanceof ViewGroup) { ViewGroup g = (ViewGroup) v; for (int i = 0; i < g.getChildCount(); i++) { View r = shownByClass(g.getChildAt(i), simple); if (r != null) return r; } }
        return null;
    }

    private static View contactsRow(View list) {
        if (!(list instanceof ViewGroup)) return null;
        ViewGroup g = (ViewGroup) list;
        for (int i = 0; i < g.getChildCount(); i++) {
            View c = g.getChildAt(i);
            Object h = c.getTag();
            if (h != null && Integer.valueOf(0).equals(safeField(h, "mViewType")) && safeField(h, "mCells") != null) return c;
        }
        return null;
    }

    private static Object safeField(Object o, String name) { try { return Xp.getObjectField(o, name); } catch (Throwable t) { return null; } }

    private static void collapseContacts(final Chrome ch) {
        final Activity a = ch.act;
        final View row = contactsRow(ch.targetList());
        Haptics.tick(a);
        final Runnable done = () -> {
            sContactsHidden = true;
            prefs(a).edit().putBoolean(P_CHIDE, true).apply();
            Object la = activeAdapter(a);
            try { if (la != null) Xp.callMethod(la, "notifyDataSetChanged"); } catch (Throwable ignored) { }
            ch.busy = false;
        };
        final View cf = findFolder(a.getWindow().getDecorView(), CFOLDER_TYPE);
        if (cf != null) {
            cf.animate().cancel();
            cf.animate().scaleX(0f).scaleY(0f).alpha(0f).setDuration(260).setInterpolator(new PathInterpolator(0.4f, 0f, 0.6f, 1f)).start();
        }
        final Runnable done0 = done;
        final Runnable doneAll = () -> { done0.run(); if (cf != null) MAIN.post(() -> { cf.setScaleX(1f); cf.setScaleY(1f); cf.setAlpha(1f); }); };
        if (row == null || row.getHeight() == 0) { if (cf != null) MAIN.postDelayed(doneAll, 270); else doneAll.run(); return; }
        ch.busy = true;
        final ViewGroup.LayoutParams lp = row.getLayoutParams();
        final int keep = lp.height, h0 = row.getHeight();
        ValueAnimator va = ValueAnimator.ofFloat(0f, 1f).setDuration(340);
        va.setInterpolator(new PathInterpolator(0.4f, 0f, 0.2f, 1f));
        va.addUpdateListener(an -> {
            float f = (Float) an.getAnimatedValue();
            lp.height = Math.max(1, Math.round(h0 * (1f - f)));
            row.setLayoutParams(lp);
            row.setAlpha(Math.max(0f, 1f - f * 1.6f));
            row.setScaleX(1f - 0.04f * f); row.setScaleY(1f - 0.04f * f);
        });
        va.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator an) {
                lp.height = keep; row.setLayoutParams(lp);
                row.setAlpha(1f); row.setScaleX(1f); row.setScaleY(1f);
                doneAll.run();
            }
        });
        va.start();
    }

    private static void expandContacts(final Chrome ch) {
        final Activity a = ch.act;
        Haptics.tick(a);
        sContactsHidden = false;
        prefs(a).edit().putBoolean(P_CHIDE, false).apply();
        Object la = activeAdapter(a);
        if (la != null && !cstash(la).isEmpty()) sFolderPop = CFOLDER_TYPE;
        try { if (la != null) Xp.callMethod(la, "notifyDataSetChanged"); } catch (Throwable ignored) { }
        ch.busy = true;
        final long until = android.os.SystemClock.uptimeMillis() + 800;
        final ViewTreeObserver vto = ch.decor.getViewTreeObserver();
        vto.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override public boolean onPreDraw() {
                final View row = contactsRow(ch.targetList());
                if (row == null || row.getHeight() == 0) {
                    if (android.os.SystemClock.uptimeMillis() > until) { ch.decor.getViewTreeObserver().removeOnPreDrawListener(this); ch.busy = false; }
                    return true;
                }
                ch.decor.getViewTreeObserver().removeOnPreDrawListener(this);
                final ViewGroup.LayoutParams lp = row.getLayoutParams();
                final int keep = lp.height, h1 = row.getHeight();
                lp.height = 1; row.setLayoutParams(lp); row.setAlpha(0f);
                ValueAnimator va = ValueAnimator.ofFloat(0f, 1f).setDuration(380);
                va.setInterpolator(new PathInterpolator(0.2f, 0f, 0f, 1f));
                va.addUpdateListener(an -> {
                    float f = (Float) an.getAnimatedValue();
                    lp.height = Math.max(1, Math.round(h1 * f));
                    row.setLayoutParams(lp);
                    row.setAlpha(Math.max(0f, (f - 0.25f) / 0.75f));
                    row.setScaleX(0.96f + 0.04f * f); row.setScaleY(0.96f + 0.04f * f);
                });
                va.addListener(new android.animation.AnimatorListenerAdapter() {
                    @Override public void onAnimationEnd(android.animation.Animator an) {
                        lp.height = keep; row.setLayoutParams(lp); row.setAlpha(1f); row.setScaleX(1f); row.setScaleY(1f);
                        ch.busy = false;
                        if (sSelecting) decorateAllLater(a);
                    }
                });
                va.start();
                return false;
            }
        });
    }

    private static void decorateAllLater(final Activity a) { MAIN.post(() -> refreshAll(a)); }

    private static void cells(View v, List<View> out) {
        if (Xp.getAdditionalInstanceField(v, F_TARGET) != null) out.add(v);
        if (v instanceof ViewGroup) { ViewGroup g = (ViewGroup) v; for (int i = 0; i < g.getChildCount(); i++) cells(g.getChildAt(i), out); }
    }

    private static View findFolder(View v, int type) {
        if (Integer.valueOf(type).equals(Xp.getAdditionalInstanceField(v, F_FOLDER)) && v.isShown()) return v;
        if (v instanceof ViewGroup) { ViewGroup g = (ViewGroup) v; for (int i = 0; i < g.getChildCount(); i++) { View r = findFolder(g.getChildAt(i), type); if (r != null) return r; } }
        return null;
    }

    private static int dp(Context c, float v) { return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics())); }

    private static void recreate(Activity a) {
        try { if (!a.isFinishing() && !a.isDestroyed()) Xp.callMethod(a, "recreatePagerAdapter"); } catch (Throwable ignored) { }
    }

    private static boolean startDrag(Object grid, Object holder, int idx) {
        Object la = Xp.getObjectField(grid, "mChooserListAdapter");
        View[] cellsArr = (View[]) Xp.getObjectField(holder, "mCells");
        if (la == null || cellsArr == null || idx < 0 || idx >= cellsArr.length) return false;
        View pressed = cellsArr[idx];
        final int viewType = (Integer) Xp.getObjectField(holder, "mViewType");
        final boolean contacts = viewType == 0;
        List<Object> holders = new ArrayList<>();
        View rowView = (View) Xp.getObjectField(holder, "itemView");
        if (rowView.getParent() instanceof ViewGroup) {
            ViewGroup rv = (ViewGroup) rowView.getParent();
            for (int i = 0; i < rv.getChildCount(); i++) {
                Object h = rv.getChildAt(i).getTag();
                if (h != null && h.getClass() == holder.getClass() && Integer.valueOf(viewType).equals(Xp.getObjectField(h, "mViewType"))) holders.add(h);
            }
        }
        if (!holders.contains(holder)) holders.add(holder);
        final List<Object[]> found = new ArrayList<>();
        final boolean rtl = pressed.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
        int[] loc = new int[2];
        for (Object h : holders) {
            View row = (View) Xp.getObjectField(h, "itemView");
            if (row == null || !row.isShown() || row.getParent() == null) continue;
            try { if ((Integer) Xp.callStaticMethod(row.getParent().getClass(), "getChildAdapterPosition", row) < 0) continue; } catch (Throwable ignored) { }
            View[] cs = (View[]) Xp.getObjectField(h, "mCells");
            int[] pos = (int[]) Xp.getObjectField(h, "mItemIndices");
            if (cs == null || pos == null) continue;
            for (int k = 0; k < cs.length && k < pos.length; k++) {
                View c = cs[k];
                if (c == null || c.getVisibility() != View.VISIBLE || !c.isShown()) continue;
                Object bp = Xp.getAdditionalInstanceField(c, F_POS);
                int lp = bp instanceof Integer ? (Integer) bp : pos[k];
                if (!Integer.valueOf(contacts ? 1 : 2).equals(Xp.callMethod(la, "getPositionTargetType", lp))) continue;
                Object t = Xp.callMethod(la, "targetInfoForPosition", lp, true);
                if (t == null || !bool(t, "isPinned")) continue;
                if (contacts ? !bool(t, "isSelectableTargetInfo") : (!bool(t, "isDisplayResolveInfo") || bool(t, "isMultiDisplayResolveInfo"))) continue;
                boolean dup = false;
                for (Object[] f : found) if (f[0] == c || ((Integer) f[2]) == lp) { dup = true; break; }
                if (dup) continue;
                c.getLocationInWindow(loc);
                found.add(new Object[]{c, t, lp, loc[1] + c.getHeight() / 2, loc[0] + c.getWidth() / 2});
            }
        }
        final int rowTol = Math.max(1, pressed.getHeight() / 2);
        Collections.sort(found, (x, y) -> {
            int dy = (Integer) x[3] - (Integer) y[3];
            if (Math.abs(dy) > rowTol) return dy;
            int dx = (Integer) x[4] - (Integer) y[4];
            return rtl ? -dx : dx;
        });
        List<View> pins = new ArrayList<>(); List<Object> targets = new ArrayList<>(); List<Integer> listPos = new ArrayList<>(); int origin = -1, originPos = -1;
        for (Object[] f : found) {
            if (f[0] == pressed) { origin = pins.size(); originPos = (Integer) f[2]; }
            pins.add((View) f[0]); targets.add(f[1]); listPos.add((Integer) f[2]);
        }
        if (origin < 0) return false;
        Activity a = activityOf(pressed.getContext());
        if (a == null) return false;
        Drag d = new Drag(a, grid, la, pins, targets, origin, originPos);
        d.positions = listPos;
        d.contacts = contacts;
        if (!d.lift()) return false;
        sDrag = d;
        return true;
    }

    private static final class Drag {
        final Activity act; final Object grid, la; final List<View> pins; final List<Object> targets; final int origin, listPos;
        final float[] cx, cy; final List<ViewGroup> unclipped = new ArrayList<>(); int slot; boolean contacts; List<Integer> positions; Ghost ghost; float refX, refY, gx0, gy0; boolean started, moved, done; final int slop;

        Drag(Activity a, Object grid, Object la, List<View> pins, List<Object> targets, int origin, int listPos) {
            this.act = a; this.grid = grid; this.la = la; this.pins = pins; this.targets = targets; this.origin = origin; this.listPos = listPos;
            this.slot = origin; this.cx = new float[pins.size()]; this.cy = new float[pins.size()];
            this.slop = ViewConfiguration.get(a).getScaledTouchSlop();
        }

        boolean lift() {
            ViewGroup decor = (ViewGroup) act.getWindow().getDecorView();
            int[] l = new int[2];
            int[] ld0 = new int[2]; act.getWindow().getDecorView().getLocationInWindow(ld0);
            for (int k = 0; k < pins.size(); k++) {
                View p = pins.get(k);
                p.getLocationInWindow(l);
                cx[k] = l[0] - ld0[0] + p.getWidth() / 2f; cy[k] = l[1] - ld0[1] + p.getHeight() / 2f;
                if (p.getParent() instanceof ViewGroup) {
                    ViewGroup row = (ViewGroup) p.getParent();
                    if (row.getClipChildren() && !unclipped.contains(row)) { row.setClipChildren(false); unclipped.add(row); }
                    if (row.getParent() instanceof ViewGroup) {
                        ViewGroup list = (ViewGroup) row.getParent();
                        if (list.getClipChildren() && !unclipped.contains(list)) { list.setClipChildren(false); unclipped.add(list); }
                    }
                }
                p.setTranslationZ(0f);
            }
            View cell = pins.get(origin);
            ghost = Ghost.of(cell, decor);
            if (ghost == null) return false;
            gx0 = ghost.getTranslationX(); gy0 = ghost.getTranslationY();
            cell.setAlpha(0f);
            ghost.animate().scaleX(1.14f).scaleY(1.14f).setDuration(220).setInterpolator(new OvershootInterpolator(2f)).start();
            ghost.lift(true);
            Haptics.tick(act);
            MAIN.post(() -> {
                try {
                    long now = android.os.SystemClock.uptimeMillis();
                    MotionEvent cancel = MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, 0, 0, 0);
                    act.getWindow().superDispatchTouchEvent(cancel);
                    cancel.recycle();
                } catch (Throwable ignored) { }
            });
            return true;
        }

        void onTouch(MotionEvent e) {
            if (done) return;
            int action = e.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN || (!started && action == MotionEvent.ACTION_MOVE)) { refX = e.getX(); refY = e.getY(); started = true; return; }
            if (action == MotionEvent.ACTION_MOVE) {
                float dx = e.getX() - refX, dy = e.getY() - refY;
                if (Math.abs(dx) > slop || Math.abs(dy) > slop) moved = true;
                ghost.setTranslationX(gx0 + dx); ghost.setTranslationY(gy0 + dy);
                float centerX = gx0 + dx + ghost.getWidth() / 2f, centerY = gy0 + dy + ghost.getHeight() / 2f;
                int best = 0; float bd = Float.MAX_VALUE;
                int nearRow = origin; float rd = Float.MAX_VALUE;
                for (int k = 0; k < cy.length; k++) { float d = Math.abs(cy[k] - centerY); if (d < rd) { rd = d; nearRow = k; } }
                Object row = pins.get(nearRow).getParent();
                for (int k = 0; k < cx.length; k++) { if (pins.get(k).getParent() != row) continue; float d = Math.abs(cx[k] - centerX); if (d < bd) { bd = d; best = k; } }
                if (best != slot) { slot = best; Haptics.tick(act); shuffle(); }
            } else if (action == MotionEvent.ACTION_UP) {
                drop(true);
            } else if (action == MotionEvent.ACTION_CANCEL) {
                if (slot != origin) { slot = origin; shuffle(); }
                drop(false);
            }
        }

        void shuffle() {
            for (int k = 0; k < pins.size(); k++) {
                if (k == origin) continue;
                int to = k;
                if (origin < slot && k > origin && k <= slot) to = k - 1;
                else if (slot < origin && k >= slot && k < origin) to = k + 1;
                View pk = pins.get(k);
                if (cy[to] != cy[k] && pk.getParent() instanceof View) ((View) pk.getParent()).setTranslationZ(1f);
                pk.animate().translationX(cx[to] - cx[k]).translationY(cy[to] - cy[k]).setDuration(300).setInterpolator(new OvershootInterpolator(1.5f)).start();
            }
        }

        void drop(boolean released) {
            done = true;
            final boolean still = !moved && slot == origin;
            float tx = gx0 + (cx[slot] - cx[origin]), ty = gy0 + (cy[slot] - cy[origin]);
            ghost.lift(false);
            ghost.animate().translationX(tx).translationY(ty).scaleX(1f).scaleY(1f).setDuration(200)
                    .setInterpolator(new DecelerateInterpolator(1.6f)).withEndAction(() -> finish(released && still)).start();
        }

        void finish(boolean openDialog) {
            if (sDrag == this) sDrag = null;
            ViewGroup gp = (ViewGroup) ghost.getParent();
            if (gp != null) gp.removeView(ghost);
            boolean changed = slot != origin;
            if (changed) {
                try { reorder(); } catch (Throwable ignored) { changed = false; }
            }
            for (View v : pins) { v.animate().cancel(); v.setTranslationX(0f); v.setTranslationY(0f); if (v.getParent() instanceof View) ((View) v.getParent()).setTranslationZ(0f); }
            for (ViewGroup g : unclipped) g.setClipChildren(true);
            pins.get(origin).setAlpha(1f);
            if (changed) {
                try { for (int k = 0; k < pins.size(); k++) { int lp = positions.get(k); Xp.callMethod(la, "onBindView", pins.get(k), Xp.callMethod(la, "targetInfoForPosition", lp, true), lp); } } catch (Throwable ignored) { }
                List<ViewGroup> rows = new ArrayList<>();
                for (View v : pins) if (v.getParent() instanceof ViewGroup && !rows.contains(v.getParent())) rows.add((ViewGroup) v.getParent());
                for (int pass = 0; pass < 2; pass++) for (ViewGroup row : rows) settle(row);
            }
            if (openDialog) {
                try { Xp.callMethod(Xp.getObjectField(grid, "mChooserActivityDelegate"), "onTargetLongPressed", listPos); } catch (Throwable ignored) { }
            }
        }

        static void settle(ViewGroup row) {
            try {
                if (row.getWidth() == 0) return;
                row.measure(View.MeasureSpec.makeMeasureSpec(row.getWidth(), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(row.getHeight(), View.MeasureSpec.EXACTLY));
                row.layout(row.getLeft(), row.getTop(), row.getRight(), row.getBottom());
            } catch (Throwable ignored) { }
        }

        @SuppressWarnings("unchecked")
        void reorder() {
            List<Object> dl = (List<Object>) Xp.getObjectField(la, contacts ? "mServiceTargets" : "mDisplayList");
            List<Object> row = new ArrayList<>(targets);
            row.add(slot, row.remove(origin));
            int[] at = new int[targets.size()];
            for (int k = 0; k < targets.size(); k++) {
                at[k] = -1;
                for (int i = 0; i < dl.size(); i++) if (dl.get(i) == targets.get(k)) { at[k] = i; break; }
                if (at[k] < 0) throw new IllegalStateException();
            }
            Arrays.sort(at);
            for (int j = 0; j < at.length; j++) dl.set(at[j], row.get(j));
            if (contacts) {
                List<String> order = new ArrayList<>();
                for (Object o : dl) if (o != null && bool(o, "isSelectableTargetInfo") && bool(o, "isPinned")) { String k = contactKey(o); if (k != null && !order.contains(k)) order.add(k); }
                for (String old : sCOrderList) if (!order.contains(old)) order.add(old);
                setCOrder(order);
                prefs(act).edit().putString(P_CORDER, TextUtils.join("\n", order)).apply();
                return;
            }
            List<String> order = new ArrayList<>();
            for (Object o : dl) if (Xp.getBooleanField(o, "mPinned")) { String c = flat(Xp.callMethod(o, "getResolvedComponentName")); if (c != null && !order.contains(c)) order.add(c); }
            for (String old : sOrderList) if (!order.contains(old)) order.add(old);
            setOrder(order);
            prefs(act).edit().putString(P_ORDER, TextUtils.join("\n", order)).apply();
        }
    }

    private static void rowBefore(Object holder) {
        View[] cells = (View[]) Xp.getObjectField(holder, "mCells");
        ViewGroup row = (ViewGroup) Xp.getObjectField(holder, "mRow");
        if (cells == null || row == null) return;
        if (row.getLayoutTransition() != null) row.setLayoutTransition(null);
        int[] lefts = new int[cells.length];
        int shown = 0;
        for (int i = 0; i < cells.length; i++) {
            View c = cells[i];
            boolean vis = c != null && c.getVisibility() == View.VISIBLE && row.isLaidOut() && row.getWidth() > 0;
            lefts[i] = vis ? c.getLeft() : Integer.MIN_VALUE;
            if (vis) shown++;
        }
        row.setTag(ROW_GLIDE, new Object[]{lefts, shown});
    }

    private static void centreRow(Object holder) {
        final View[] cells = (View[]) Xp.getObjectField(holder, "mCells");
        final ViewGroup row = (ViewGroup) Xp.getObjectField(holder, "mRow");
        if (cells == null || row == null) return;
        int shown = 0;
        for (View c : cells) if (c != null && c.getVisibility() == View.VISIBLE) shown++;
        boolean partial = shown > 0 && shown < cells.length;
        for (View c : cells) if (c != null && c.getVisibility() == View.INVISIBLE) c.setVisibility(View.GONE);
        if (row instanceof LinearLayout) {
            int g = (partial ? android.view.Gravity.CENTER_HORIZONTAL : android.view.Gravity.START) | android.view.Gravity.TOP;
            if (!Integer.valueOf(g).equals(row.getTag(ROW_GLIDE + 1))) { row.setTag(ROW_GLIDE + 1, g); ((LinearLayout) row).setGravity(g); }
        }
        int perCell = 0, fullW = 0;
        for (View c : cells) {
            if (c == null || c.getLayoutParams() == null) continue;
            Object base = c.getTag(ROW_GLIDE + 2);
            if (!(base instanceof Integer)) { base = c.getLayoutParams().width; c.setTag(ROW_GLIDE + 2, base); }
            perCell = (Integer) base; fullW += perCell;
        }
        if (perCell > 0) {
            int rowW = row.getWidth() > 0 ? row.getWidth() - row.getPaddingLeft() - row.getPaddingRight() : fullW;
            int want = partial ? Math.min(Math.round(perCell * 1.2f), rowW / Math.max(1, shown)) : perCell;
            want = Math.max(perCell, want);
            boolean changed = false;
            for (View c : cells) {
                if (c == null || c.getLayoutParams() == null) continue;
                if (c.getLayoutParams().width != want) { c.getLayoutParams().width = want; c.requestLayout(); changed = true; }
            }
            if (changed || partial) row.post(() -> {
                for (View c : cells) {
                    if (c == null || c.getVisibility() != View.VISIBLE) continue;
                    View t1 = c.findViewById(android.R.id.text1);
                    if (!(t1 instanceof TextView) || t1.getBackground() == null || t1.getLayoutParams() == null) continue;
                    int avail = c.getWidth() - c.getPaddingLeft() - c.getPaddingRight();
                    if (avail <= 0) continue;
                    t1.getLayoutParams().width = -2;
                    int fitted = fitPinnedLabel(c, (TextView) t1);
                    t1.getLayoutParams().width = fitted > 0 ? fitted : avail;
                    t1.requestLayout();
                }
            });
        }
        Object tag = row.getTag(ROW_GLIDE);
        row.setTag(ROW_GLIDE, null);
        if (!(tag instanceof Object[])) return;
        final int[] lefts = (int[]) ((Object[]) tag)[0];
        int before = (Integer) ((Object[]) tag)[1];
        if (before == 0 || before == shown || sDrag != null) return;
        row.getViewTreeObserver().addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener() {
            @Override public boolean onPreDraw() {
                row.getViewTreeObserver().removeOnPreDrawListener(this);
                android.view.animation.Interpolator em = new android.view.animation.PathInterpolator(0.2f, 0f, 0f, 1f);
                for (int i = 0; i < cells.length && i < lefts.length; i++) {
                    View c = cells[i];
                    if (c == null || c.getVisibility() != View.VISIBLE) continue;
                    c.animate().cancel();
                    if (lefts[i] == Integer.MIN_VALUE) {
                        c.setAlpha(0f); c.setScaleX(0.8f); c.setScaleY(0.8f);
                        c.animate().alpha(1f).scaleX(1f).scaleY(1f).setStartDelay(90).setDuration(260).setInterpolator(em).start();
                    } else if (lefts[i] != c.getLeft()) {
                        c.setTranslationX(lefts[i] - c.getLeft());
                        c.animate().translationX(0f).setStartDelay(0).setDuration(320).setInterpolator(em).start();
                    }
                }
                return true;
            }
        });
    }
    private static final int ROW_GLIDE = 0x7f7ffd41;

    private static final class Ghost extends View {
        final View src; final int pad; final RectF icon = new RectF(); final Paint shadow = new Paint(Paint.ANTI_ALIAS_FLAG);
        float lift;

        private Ghost(Context c, View src, int pad) { super(c); this.src = src; this.pad = pad; shadow.setColor(0xFF000000); }

        static Ghost of(View cell, ViewGroup decor) {
            if (cell.getWidth() == 0) return null;
            int pad = dp(cell.getContext(), 24);
            Ghost g = new Ghost(cell.getContext(), cell, pad);
            View iv = cell.findViewById(android.R.id.icon);
            if (iv != null) {
                float l = iv.getLeft() + iv.getPaddingLeft(), t = iv.getTop() + iv.getPaddingTop();
                float w = iv.getWidth() - iv.getPaddingLeft() - iv.getPaddingRight(), h = iv.getHeight() - iv.getPaddingTop() - iv.getPaddingBottom();
                float s = Math.min(w, h) * 0.9f, cxx = l + w / 2f + pad, cyy = t + h / 2f + pad;
                g.icon.set(cxx - s / 2, cyy - s / 2, cxx + s / 2, cyy + s / 2);
            }
            int[] lc = new int[2], ld = new int[2];
            cell.getLocationInWindow(lc); decor.getLocationInWindow(ld);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(cell.getWidth() + 2 * pad, cell.getHeight() + 2 * pad);
            lp.gravity = Gravity.TOP | Gravity.START;
            decor.addView(g, lp);
            g.setTranslationX(lc[0] - ld[0] - pad); g.setTranslationY(lc[1] - ld[1] - pad);
            g.setTranslationZ(dp(cell.getContext(), 24));
            return g;
        }

        void lift(boolean up) {
            ValueAnimator va = ValueAnimator.ofFloat(lift, up ? 1f : 0f).setDuration(up ? 220 : 180);
            va.addUpdateListener(a -> { lift = (Float) a.getAnimatedValue(); invalidate(); });
            va.start();
        }

        @Override protected void onDraw(Canvas c) {
            if (lift > 0.01f && !icon.isEmpty()) {
                float r = dp(getContext(), 12) * lift;
                shadow.setShadowLayer(Math.max(0.5f, r), 0, dp(getContext(), 5) * lift, Color.argb((int) (90 * lift), 0, 0, 0));
                c.drawOval(new RectF(icon.left + icon.width() * 0.06f, icon.top + icon.height() * 0.06f, icon.right - icon.width() * 0.06f, icon.bottom - icon.height() * 0.06f), shadow);
            }
            c.save(); c.translate(pad, pad);
            try { src.draw(c); } catch (Throwable ignored) { }
            c.restore();
        }
    }

    private static final class Fly {
        final Activity act; final String comp; final boolean toPinned; final ImageView ghost; final ViewGroup decor;
        final long started = android.os.SystemClock.uptimeMillis(); float lastX = Float.NaN, lastY = Float.NaN; boolean landed;

        static void start(Activity a, Object target) {
            if (a == null || target == null) return;
            String comp = flat(Xp.callMethod(target, "getResolvedComponentName"));
            if (comp == null) return;
            boolean wasPinned = Xp.getBooleanField(target, "mPinned");
            ViewGroup decor = (ViewGroup) a.getWindow().getDecorView();
            List<View> all = new ArrayList<>(); cells(decor, all);
            View src = null;
            for (View v : all) {
                Object t = Xp.getAdditionalInstanceField(v, F_TARGET);
                if (t == null || !v.isShown() || bool(t, "isSelectableTargetInfo") || !comp.equals(flat(Xp.callMethod(t, "getResolvedComponentName")))) continue;
                int type = posType(v);
                if (src == null || (wasPinned ? type == 2 : type == 3)) src = v;
            }
            if (src == null) return;
            ImageView iv = src.findViewById(android.R.id.icon) instanceof ImageView ? (ImageView) src.findViewById(android.R.id.icon) : null;
            if (iv == null || iv.getDrawable() == null) return;
            RectF r = iconRect(iv, decor);
            ImageView g = new ImageView(a);
            g.setImageDrawable(copy(a, iv.getDrawable()));
            g.setScaleType(ImageView.ScaleType.FIT_CENTER);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(Math.round(r.width()), Math.round(r.height()), Gravity.TOP | Gravity.START);
            decor.addView(g, lp);
            g.setTranslationX(r.left); g.setTranslationY(r.top);
            g.setTranslationZ(dp(a, 24));
            g.animate().scaleX(1.12f).scaleY(1.12f).setDuration(200).setInterpolator(new OvershootInterpolator(2f)).start();
            Fly f = new Fly(a, decor, comp, !wasPinned, g);
            MAIN.postDelayed(f::poll, 32);
        }

        private Fly(Activity a, ViewGroup decor, String comp, boolean toPinned, ImageView ghost) {
            this.act = a; this.decor = decor; this.comp = comp; this.toPinned = toPinned; this.ghost = ghost;
        }

        static int posType(View v) {
            try { return (Integer) Xp.callMethod(Xp.getAdditionalInstanceField(v, F_ADAPTER), "getPositionTargetType", Xp.getAdditionalInstanceField(v, F_POS)); }
            catch (Throwable t) { return -1; }
        }

        static RectF iconRect(View iv, View decor) {
            int[] l = new int[2], ld = new int[2];
            iv.getLocationInWindow(l); decor.getLocationInWindow(ld);
            float x = l[0] - ld[0] + iv.getPaddingLeft(), y = l[1] - ld[1] + iv.getPaddingTop();
            float w = iv.getWidth() - iv.getPaddingLeft() - iv.getPaddingRight(), h = iv.getHeight() - iv.getPaddingTop() - iv.getPaddingBottom();
            float s = Math.min(w, h);
            return new RectF(x + (w - s) / 2f, y + (h - s) / 2f, x + (w + s) / 2f, y + (h + s) / 2f);
        }

        void poll() {
            if (landed) return;
            if (act.isDestroyed() || android.os.SystemClock.uptimeMillis() - started > 1600) { vanish(); return; }
            View dest = null, alt = null;
            List<View> all = new ArrayList<>(); cells(decor, all);
            Rect vr = new Rect();
            for (View v : all) {
                Object t = Xp.getAdditionalInstanceField(v, F_TARGET);
                if (t == null || !v.isShown() || v.getWidth() == 0 || bool(t, "isSelectableTargetInfo")) continue;
                if (bool(t, "isPinned") != toPinned || !comp.equals(flat(Xp.callMethod(t, "getResolvedComponentName")))) continue;
                int type = posType(v);
                boolean onScreen = v.getGlobalVisibleRect(vr) && vr.height() >= v.getHeight() / 2;
                if (toPinned ? type == 2 : type == 3) { if (onScreen) { dest = v; break; } }
                else if (!toPinned && type == 2 && onScreen) alt = v;
            }
            if (dest == null && alt != null && listReady() && android.os.SystemClock.uptimeMillis() - started > 250) dest = alt;
            if (dest == null) {
                if (android.os.SystemClock.uptimeMillis() - started > 900 && listReady()) { vanish(); return; }
                MAIN.postDelayed(this::poll, 16);
                return;
            }
            View iv = dest.findViewById(android.R.id.icon);
            if (iv == null) { vanish(); return; }
            RectF r = iconRect(iv, decor);
            Rect vis = new Rect();
            if (!iv.getGlobalVisibleRect(vis) || vis.height() < iv.getHeight() / 2) { vanish(); return; }
            if (Float.isNaN(lastX) || Math.abs(lastX - r.left) > 0.5f || Math.abs(lastY - r.top) > 0.5f) {
                lastX = r.left; lastY = r.top;
                MAIN.postDelayed(this::poll, 16);
                return;
            }
            land(dest, iv, r);
        }

        boolean listReady() {
            Object la = activeAdapter(act);
            return la != null && Xp.getBooleanField(la, "mAppTargetsReady");
        }

        void land(final View cell, final View iv, RectF r) {
            landed = true;
            ghost.animate().cancel();
            final View label = cell.findViewById(android.R.id.text1);
            iv.setAlpha(0f);
            if (label != null) label.setAlpha(0f);
            float sx = r.width() / Math.max(1f, ghost.getWidth());
            ghost.setPivotX(0f); ghost.setPivotY(0f);
            float fromX = ghost.getTranslationX(), fromY = ghost.getTranslationY(), fromS = ghost.getScaleX();
            float w = ghost.getWidth(), h = ghost.getHeight();
            fromX -= (fromS - 1f) * w / 2f; fromY -= (fromS - 1f) * h / 2f;
            ghost.setTranslationX(fromX); ghost.setTranslationY(fromY);
            final float fx = fromX, fy = fromY, fs = fromS, tx = r.left, ty = r.top;
            ValueAnimator va = ValueAnimator.ofFloat(0f, 1f).setDuration(460);
            va.setInterpolator(new PathInterpolator(0.3f, 0f, 0.1f, 1f));
            final float arc = (ty > fy ? -1f : 1f) * dp(act, 28);
            va.addUpdateListener(a -> {
                float t = (Float) a.getAnimatedValue();
                ghost.setTranslationX(fx + (tx - fx) * t);
                ghost.setTranslationY(fy + (ty - fy) * t + arc * (float) Math.sin(Math.PI * t));
                float sc = fs + (sx - fs) * t + 0.08f * (float) Math.sin(Math.PI * t);
                ghost.setScaleX(sc); ghost.setScaleY(sc);
            });
            va.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(android.animation.Animator a) {
                    iv.setAlpha(1f);
                    if (label != null) label.animate().alpha(1f).setDuration(180).start();
                    ViewGroup p = (ViewGroup) ghost.getParent(); if (p != null) p.removeView(ghost);
                    iv.setScaleX(0.92f); iv.setScaleY(0.92f);
                    iv.animate().scaleX(1f).scaleY(1f).setDuration(260).setInterpolator(new OvershootInterpolator(2.5f)).start();
                    Haptics.tick(act);
                }
            });
            va.start();
        }

        void vanish() {
            landed = true;
            ghost.animate().translationYBy(toPinned ? -dp(act, 24) : dp(act, 48)).alpha(0f).scaleX(0.7f).scaleY(0.7f).setDuration(300)
                    .setInterpolator(new DecelerateInterpolator())
                    .withEndAction(() -> { ViewGroup p = (ViewGroup) ghost.getParent(); if (p != null) p.removeView(ghost); }).start();
        }
    }

    static void hideApps(final Activity a, List<String> comps, List<View> fromCells) {
        if (comps.isEmpty()) return;
        Set<String> h = new HashSet<>(sHidden);
        h.addAll(comps);
        for (String c : comps) { String lk = labelKey(ComponentName.unflattenFromString(c), a.getPackageManager()); if (lk != null) h.add(lk); }
        saveHidden(a, h);
        ViewGroup decor = (ViewGroup) a.getWindow().getDecorView();
        List<View> cellsToFly = fromCells;
        if (cellsToFly == null) {
            cellsToFly = new ArrayList<>();
            List<View> all = new ArrayList<>(); cells(decor, all);
            for (View v : all) {
                if (!v.isShown()) continue;
                Object t = Xp.getAdditionalInstanceField(v, F_TARGET);
                if (t == null || bool(t, "isSelectableTargetInfo")) continue;
                for (String c : comps(t)) if (comps.contains(c)) { cellsToFly.add(v); break; }
            }
        }
        float[] dest = folderPoint(a, decor);
        int[] ld = new int[2]; decor.getLocationInWindow(ld);
        for (View v : cellsToFly) {
            Rect vis = new Rect();
            if (!v.getGlobalVisibleRect(vis)) continue;
            Ghost g = Ghost.of(v, decor);
            if (g == null) continue;
            v.setAlpha(0f);
            float gx = g.getTranslationX() + g.getWidth() / 2f, gy = g.getTranslationY() + g.getHeight() / 2f;
            g.animate().translationXBy(dest[0] - ld[0] - gx).translationYBy(dest[1] - ld[1] - gy).scaleX(0.22f).scaleY(0.22f).alpha(0.15f)
                    .setDuration(380).setInterpolator(new PathInterpolator(0.3f, 0f, 0.15f, 1f))
                    .withEndAction(() -> { ViewGroup p = (ViewGroup) g.getParent(); if (p != null) p.removeView(g); }).start();
        }
        Haptics.tick(a);
        final List<View> flown = cellsToFly;
        final boolean newFolder = findFolder(decor, FOLDER_TYPE) == null;
        MAIN.postDelayed(() -> {
            sFolderPop = newFolder ? FOLDER_TYPE : -FOLDER_TYPE;
            if (!hideAppsInPlace(a, comps)) { sFolderPop = FOLDER_TYPE; recreate(a); }
            for (View v : flown) v.setAlpha(1f);
        }, cellsToFly.isEmpty() ? 0 : 400);
    }

    private static void addUnique(List<Object> to, Object o) {
        String k = bool(o, "isSelectableTargetInfo") ? contactKey(o) : flat(Xp.callMethod(o, "getResolvedComponentName"));
        for (Object x : to) {
            String xk = bool(x, "isSelectableTargetInfo") ? contactKey(x) : flat(Xp.callMethod(x, "getResolvedComponentName"));
            if (x == o || (k != null && k.equals(xk))) return;
        }
        to.add(o);
    }

    @SuppressWarnings("unchecked")
    private static void refilterTop(Object la) {
        List<Object> dl = (List<Object>) Xp.getObjectField(la, "mDisplayList");
        List<Object> sl = (List<Object>) Xp.getObjectField(la, "mSortedList");
        int ranked = (Integer) Xp.callMethod(la, "getRankedTargetCount");
        Set<String> top = new HashSet<>();
        for (int i = 0; i < Math.min(ranked, dl.size()); i++) { String c = flat(Xp.callMethod(dl.get(i), "getResolvedComponentName")); if (c != null) top.add(c); }
        try { for (Object o : (List<?>) Xp.getObjectField(la, "mCallerTargets")) { String c = flat(Xp.callMethod(o, "getResolvedComponentName")); if (c != null) top.add(c); } } catch (Throwable ignored) { }
        for (java.util.Iterator<Object> it = sl.iterator(); it.hasNext(); ) {
            Object o = it.next();
            if (!bool(o, "isMultiDisplayResolveInfo") && top.contains(flat(Xp.callMethod(o, "getResolvedComponentName")))) it.remove();
        }
    }

    @SuppressWarnings("unchecked")
    private static boolean hideAppsInPlace(Activity a, List<String> comps) {
        Object la = activeAdapter(a);
        if (la == null) return false;
        try {
            List<Object> dl = (List<Object>) Xp.getObjectField(la, "mDisplayList");
            List<Object> sl = (List<Object>) Xp.getObjectField(la, "mSortedList");
            List<Object> st = stash(la);
            for (java.util.Iterator<Object> it = dl.iterator(); it.hasNext(); ) {
                Object o = it.next();
                if (Xp.getBooleanField(o, "mPinned")) continue;
                if (comps.contains(flat(Xp.callMethod(o, "getResolvedComponentName")))) { it.remove(); addUnique(st, o); }
            }
            for (java.util.Iterator<Object> it = sl.iterator(); it.hasNext(); ) {
                Object o = it.next();
                boolean hit = false;
                for (String c : comps(o)) if (comps.contains(c)) { hit = true; break; }
                if (!hit) continue;
                it.remove();
                if (bool(o, "isMultiDisplayResolveInfo")) {
                    List<?> subs = (List<?>) Xp.callMethod(o, "getAllDisplayTargets");
                    if (subs != null) for (Object x : subs) if (comps.contains(flat(Xp.callMethod(x, "getResolvedComponentName")))) addUnique(st, x);
                } else addUnique(st, o);
            }
            refilterTop(la);
            Xp.callMethod(la, "notifyDataSetChanged");
            return true;
        } catch (Throwable t) { return false; }
    }

    @SuppressWarnings("unchecked")
    private static boolean unhideAppsInPlace(Activity a, List<Object> items) {
        Object la = activeAdapter(a);
        if (la == null) return false;
        try {
            List<Object> dl = (List<Object>) Xp.getObjectField(la, "mDisplayList");
            List<Object> sl = (List<Object>) Xp.getObjectField(la, "mSortedList");
            List<Object> st = stash(la);
            java.text.Collator col = java.text.Collator.getInstance(a.getResources().getConfiguration().locale);
            for (Object o : items) {
                st.remove(o);
                String c = flat(Xp.callMethod(o, "getResolvedComponentName"));
                boolean inDl = false;
                for (Object x : dl) if (c != null && c.equals(flat(Xp.callMethod(x, "getResolvedComponentName")))) { inDl = true; break; }
                if (!inDl) dl.add(o);
                String lab = String.valueOf(label(a, o));
                int at = sl.size();
                for (int i = 0; i < sl.size(); i++) if (col.compare(lab, String.valueOf(label(a, sl.get(i)))) < 0) { at = i; break; }
                sl.add(at, o);
            }
            refilterTop(la);
            Xp.callMethod(la, "notifyDataSetChanged");
            return true;
        } catch (Throwable t) { return false; }
    }

    @SuppressWarnings("unchecked")
    private static boolean hideContactsInPlace(Activity a, List<String> keys) {
        Object la = activeAdapter(a);
        if (la == null) return false;
        try {
            List<Object> st = (List<Object>) Xp.getObjectField(la, "mServiceTargets");
            List<Object> cs = cstash(la);
            for (java.util.Iterator<Object> it = st.iterator(); it.hasNext(); ) {
                Object o = it.next();
                if (o != null && bool(o, "isSelectableTargetInfo") && keys.contains(contactKey(o))) { it.remove(); addUnique(cs, o); }
            }
            Xp.callMethod(la, "notifyDataSetChanged");
            return true;
        } catch (Throwable t) { return false; }
    }

    @SuppressWarnings("unchecked")
    private static boolean unhideContactsInPlace(Activity a, List<Object> items) {
        Object la = activeAdapter(a);
        if (la == null) return false;
        try {
            List<Object> st = (List<Object>) Xp.getObjectField(la, "mServiceTargets");
            int max = (Integer) Xp.getObjectField(la, "mMaxRankedTargets");
            for (Object o : items) {
                cstash(la).remove(o);
                st.add(o);
                while (st.size() > max) st.remove(st.size() - 2 >= 0 ? st.size() - 2 : 0);
            }
            Xp.callMethod(la, "notifyDataSetChanged");
            return true;
        } catch (Throwable t) { return false; }
    }

    private static void deflateFolder(final Activity a, int type, final Runnable then) {
        final View f = findFolder(a.getWindow().getDecorView(), type);
        if (f == null) { then.run(); return; }
        f.animate().cancel();
        f.animate().scaleX(0f).scaleY(0f).alpha(0f).setDuration(260).setInterpolator(new PathInterpolator(0.4f, 0f, 0.6f, 1f))
                .withEndAction(() -> { then.run(); f.setScaleX(1f); f.setScaleY(1f); f.setAlpha(1f); }).start();
    }

    private static void unhideFromPanel(final Activity a, final int type, final List<Object> go, boolean last) {
        final boolean contacts = type == CFOLDER_TYPE;
        Runnable apply = () -> {
            boolean ok = contacts ? unhideContactsInPlace(a, go) : unhideAppsInPlace(a, go);
            if (!ok) recreate(a);
        };
        if (last) deflateFolder(a, type, apply); else apply.run();
    }

    private static float[] folderPoint(Activity a, ViewGroup decor) {
        View f = findFolder(decor, FOLDER_TYPE);
        Rect r = new Rect();
        if (f != null && f.getGlobalVisibleRect(r)) {
            View iv = f.findViewById(android.R.id.icon);
            if (iv != null && iv.getGlobalVisibleRect(r)) return new float[]{r.exactCenterX(), r.exactCenterY()};
            return new float[]{r.exactCenterX(), r.exactCenterY()};
        }
        List<View> all = new ArrayList<>(); cells(decor, all);
        for (View v : all) {
            if (!v.isShown()) continue;
            View p = v;
            while (p.getParent() instanceof View && findByClassSelf(p) == null) p = (View) p.getParent();
            if (p.getGlobalVisibleRect(r)) return new float[]{r.exactCenterX(), r.bottom - dp(a, 48)};
        }
        decor.getGlobalVisibleRect(r);
        return new float[]{r.exactCenterX(), r.bottom - dp(a, 96)};
    }

    private static View findByClassSelf(View v) {
        for (Class<?> k = v.getClass(); k != null && k != View.class; k = k.getSuperclass()) if (k.getSimpleName().equals("RecyclerView")) return v;
        return null;
    }

    private static void unhide(Activity a, Object dri) {
        Set<String> h = new HashSet<>(sHidden);
        h.remove(flat(Xp.callMethod(dri, "getResolvedComponentName")));
        Object lk = Xp.getAdditionalInstanceField(dri, F_LABELKEY);
        if (lk != null) h.remove(lk);
        try { h.remove(labelKey((ResolveInfo) Xp.getObjectField(dri, "mResolveInfo"), a.getPackageManager())); } catch (Throwable ignored) { }
        try { h.remove(labelKey((ComponentName) Xp.callMethod(dri, "getResolvedComponentName"), a.getPackageManager())); } catch (Throwable ignored) { }
        saveHidden(a, h);
    }

    private static int sMakingFolder;

    private static void makeFolder(Object holder, final int type) {
        try {
            Field f = Xp.findClass("com.android.intentresolver.grid.ViewHolderBase", holder.getClass().getClassLoader()).getDeclaredField("mViewType");
            f.setAccessible(true);
            f.setInt(holder, type);
            View v = (View) Xp.getObjectField(holder, "itemView");
            Xp.setAdditionalInstanceField(v, F_FOLDER, type);
            v.setOnClickListener(x -> {
                if (sSelecting || sDrag != null) return;
                Activity a = activityOf(x.getContext());
                Object g = Xp.getAdditionalInstanceField(x, F_GRID);
                if (a != null && g != null) openPanel(a, Xp.getObjectField(g, "mChooserListAdapter"), x, type);
            });
            v.setOnLongClickListener(x -> true);
        } catch (Throwable ignored) { }
    }

    private static void bindFolder(Object grid, Object holder, int type) {
        View v = (View) Xp.getObjectField(holder, "itemView");
        Object vh = v.getTag();
        Xp.setAdditionalInstanceField(v, F_GRID, grid);
        Object la = Xp.getObjectField(grid, "mChooserListAdapter");
        List<Object> hidden = type == CFOLDER_TYPE ? cstash(la) : stash(la);
        String name = type == CFOLDER_TYPE ? "Hidden contacts" : "Hidden apps";
        ImageView icon = (ImageView) Xp.getObjectField(vh, "icon");
        TextView text = (TextView) Xp.getObjectField(vh, "text");
        TextView text2 = (TextView) Xp.getObjectField(vh, "text2");
        Context c = v.getContext();
        List<Drawable> minis = new ArrayList<>();
        for (int i = 0; i < hidden.size() && minis.size() < 4; i++) { Drawable d = icon(c, hidden.get(i)); if (d != null) minis.add(d); }
        icon.setColorFilter((ColorFilter) null);
        icon.setImageDrawable(new FolderIcon(minis, theme(c).folder));
        text.setText(name);
        text.setMaxLines(2);
        text.setBackground(null);
        text.setPaddingRelative(0, 0, 0, 0);
        if (text2 != null) { text2.setText(""); text2.setVisibility(View.GONE); }
        v.setContentDescription(name + ", " + hidden.size());
        v.setEnabled(true);
        v.setAlpha(sSelecting ? 0.35f : 1f);
        v.setScaleX(1f); v.setScaleY(1f);
        if (sFolderPop == type) {
            sFolderPop = 0;
            v.animate().cancel();
            v.setPivotX(v.getWidth() / 2f); v.setPivotY(v.getHeight() / 2f);
            v.setScaleX(0f); v.setScaleY(0f); v.setAlpha(0f);
            v.animate().scaleX(1f).scaleY(1f).alpha(sSelecting ? 0.35f : 1f).setStartDelay(40).setDuration(380).setInterpolator(new OvershootInterpolator(1.3f)).start();
        } else if (sFolderPop == -type) {
            sFolderPop = 0;
            icon.setScaleX(0.85f); icon.setScaleY(0.85f);
            icon.animate().scaleX(1f).scaleY(1f).setStartDelay(40).setDuration(320).setInterpolator(new OvershootInterpolator(2.2f)).start();
        }
    }

    private static Drawable icon(Context c, Object dri) {
        if (bool(dri, "isSelectableTargetInfo")) return contactIcon(c, dri);
        try {
            ResolveInfo ri = (ResolveInfo) Xp.getObjectField(dri, "mResolveInfo");
            String k = flat(Xp.callMethod(dri, "getResolvedComponentName"));
            synchronized (ICONS) {
                Drawable d = ICONS.get(k);
                if (d == null) { d = ri.loadIcon(c.getPackageManager()); if (d != null) ICONS.put(k, d); }
                return d;
            }
        } catch (Throwable t) { return null; }
    }

    private static Drawable copy(Context c, Drawable d) {
        try { Drawable.ConstantState s = d.getConstantState(); if (s != null) return s.newDrawable(c.getResources()).mutate(); } catch (Throwable ignored) { }
        return d;
    }

    private static Drawable contactIcon(Context c, Object t) {
        String k = "c:" + contactKey(t);
        synchronized (ICONS) { Drawable d = ICONS.get(k); if (d != null) return d; }
        Drawable d = null;
        try { d = (Drawable) Xp.getObjectField(Xp.getObjectField(t, "mDisplayIconHolder"), "mDisplayIcon"); } catch (Throwable ignored) { }
        if (d == null) try {
            Object si = Xp.callMethod(t, "getDirectShareShortcutInfo");
            if (si != null) d = (Drawable) Xp.callMethod(c.getSystemService("launcherapps"), "getShortcutIconDrawable", si, c.getResources().getDisplayMetrics().densityDpi);
        } catch (Throwable ignored) { }
        if (d == null) try { Object ic = Xp.getObjectField(t, "mChooserTargetIcon"); if (ic != null) d = (Drawable) Xp.callMethod(ic, "loadDrawable", c); } catch (Throwable ignored) { }
        if (d == null) try {
            Object src = Xp.getObjectField(t, "mSourceInfo");
            if (src != null) d = icon(c, src);
            else { ResolveInfo ri = (ResolveInfo) Xp.getObjectField(t, "mBackupResolveInfo"); if (ri != null) d = ri.loadIcon(c.getPackageManager()); }
        } catch (Throwable ignored) { }
        if (d != null) synchronized (ICONS) { ICONS.put(k, d); }
        return d;
    }

    private static CharSequence label(Context c, Object dri) {
        if (bool(dri, "isSelectableTargetInfo")) { Object l = Xp.callMethod(dri, "getDisplayLabel"); return l instanceof CharSequence ? (CharSequence) l : ""; }
        try {
            Object l = Xp.getObjectField(dri, "mDisplayLabel");
            if (l instanceof CharSequence && ((CharSequence) l).length() > 0) return (CharSequence) l;
            return ((ResolveInfo) Xp.getObjectField(dri, "mResolveInfo")).loadLabel(c.getPackageManager());
        } catch (Throwable t) { return ""; }
    }

    private static final class FolderIcon extends Drawable {
        final List<Drawable> icons; final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        FolderIcon(List<Drawable> icons, int color) { this.icons = icons; bg.setColor(color); }
        @Override public void draw(Canvas c) {
            Rect b = getBounds();
            float s = Math.min(b.width(), b.height()), x0 = b.exactCenterX() - s / 2, y0 = b.exactCenterY() - s / 2;
            c.drawCircle(b.exactCenterX(), b.exactCenterY(), s / 2, bg);
            float mini = s * 0.3f, gap = s * 0.05f, ox = x0 + (s - 2 * mini - gap) / 2, oy = y0 + (s - 2 * mini - gap) / 2;
            for (int i = 0; i < icons.size() && i < 4; i++) {
                Drawable d = icons.get(i);
                int col = i % 2, row = i / 2;
                int l = Math.round(ox + col * (mini + gap)), t = Math.round(oy + row * (mini + gap));
                Rect old = new Rect(d.getBounds());
                d.setBounds(l, t, Math.round(l + mini), Math.round(t + mini));
                d.draw(c);
                d.setBounds(old);
            }
        }
        @Override public int getIntrinsicWidth() { return 192; }
        @Override public int getIntrinsicHeight() { return 192; }
        @Override public void setAlpha(int a) { bg.setAlpha(a); }
        @Override public void setColorFilter(ColorFilter f) { }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    private static void openPanel(final Activity a, final Object la, View folder, final int type) {
        if (la == null) return;
        final boolean contacts = type == CFOLDER_TYPE;
        final List<Object> items = new ArrayList<>(contacts ? cstash(la) : stash(la));
        if (items.isEmpty()) return;
        Haptics.tick(a);
        final Th th = theme(a);
        final PanelDialog d = new PanelDialog(a);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        final boolean[] changed = {false}, quiet = {false};

        FrameLayout root = new FrameLayout(a);
        root.setPadding(dp(a, 20), dp(a, 24), dp(a, 20), dp(a, 24));
        root.setOnClickListener(x -> d.dismiss());
        final LinearLayout card = new LinearLayout(a);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(a, 20), dp(a, 20), dp(a, 20), dp(a, 16));
        card.setBackground(new Surface(th, dp(a, 28), alpha(th.card, 0xFF)));
        card.setClickable(true);
        LinearLayout header = new LinearLayout(a);
        header.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout texts = new LinearLayout(a);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(a);
        title.setText(contacts ? "Hidden contacts" : "Hidden apps"); title.setTextColor(th.text); title.setTextSize(20); title.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        texts.addView(title);
        final TextView sub = new TextView(a);
        sub.setText("Tap to share. Long-press to unhide."); sub.setTextColor(th.text2); sub.setTextSize(13);
        sub.setPadding(0, dp(a, 2), 0, dp(a, 14));
        texts.addView(sub);
        header.addView(texts, new LinearLayout.LayoutParams(0, -2, 1f));
        final EditBubble edit = new EditBubble(a, th);
        edit.setContentDescription(contacts ? "Edit: select hidden contacts to unhide" : "Edit: select hidden apps to unhide");
        LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(edit.wantWidth(), EditBubble.wantHeight(a));
        elp.leftMargin = dp(a, 8);
        header.addView(edit, elp);
        if (!th.superDark && !th.blur) edit.setElevation(dp(a, 1));
        card.addView(header, new LinearLayout.LayoutParams(-1, -2));
        ScrollView sv = new ScrollView(a);
        sv.setVerticalScrollBarEnabled(false);
        final LinearLayout grid = new LinearLayout(a);
        grid.setOrientation(LinearLayout.VERTICAL);
        sv.addView(grid);
        int maxH = (int) (a.getResources().getDisplayMetrics().heightPixels * 0.55f);
        card.addView(sv, new LinearLayout.LayoutParams(-1, -2));
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER);
        root.addView(card, clp);

        final boolean[] selecting = {false};
        final List<Object> picked = new ArrayList<>();
        final TextView unhidePill = new TextView(a);
        unhidePill.setTextColor(th.onAccent); unhidePill.setTextSize(15); unhidePill.setGravity(Gravity.CENTER);
        unhidePill.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        unhidePill.setPadding(dp(a, 24), dp(a, 14), dp(a, 24), dp(a, 14));
        unhidePill.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), new Surface(th, dp(a, 28), th.accent, false), null));
        unhidePill.setCompoundDrawablePadding(dp(a, 10));
        Glyph eye = new Glyph(Glyph.EYE, th.onAccent, a.getResources()); eye.setBounds(0, 0, dp(a, 20), dp(a, 20));
        unhidePill.setCompoundDrawablesRelative(eye, null, null, null);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(-2, -2);
        plp.gravity = Gravity.CENTER_HORIZONTAL; plp.topMargin = dp(a, 12);
        unhidePill.setVisibility(View.GONE);
        final Runnable[] fill = new Runnable[1];
        final Runnable updatePill = () -> {
            int n = picked.size();
            boolean show = selecting[0] && n > 0;
            String what = contacts ? (n == 1 ? "contact" : "contacts") : (n == 1 ? "app" : "apps");
            if (n > 0) unhidePill.setText("Unhide " + n + " " + what);
            if (show && unhidePill.getVisibility() != View.VISIBLE) {
                unhidePill.setVisibility(View.VISIBLE); unhidePill.setAlpha(0f); unhidePill.setScaleX(0.9f); unhidePill.setScaleY(0.9f);
                unhidePill.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(220).setInterpolator(new OvershootInterpolator(1.4f)).start();
            } else if (!show && unhidePill.getVisibility() == View.VISIBLE) {
                unhidePill.animate().alpha(0f).scaleX(0.9f).scaleY(0.9f).setDuration(160).withEndAction(() -> { if (!(selecting[0] && !picked.isEmpty())) unhidePill.setVisibility(View.GONE); }).start();
            }
        };
        final Runnable refreshChecks = () -> {
            for (int r = 0; r < grid.getChildCount(); r++) {
                ViewGroup row = (ViewGroup) grid.getChildAt(r);
                for (int c = 0; c < row.getChildCount(); c++) {
                    View cell = row.getChildAt(c);
                    Object dri = cell.getTag();
                    if (dri == null) continue;
                    Check ck = (Check) Xp.getAdditionalInstanceField(cell, F_CHECK);
                    if (!selecting[0]) {
                        if (ck != null) { cell.getOverlay().remove(ck); Xp.setAdditionalInstanceField(cell, F_CHECK, null); }
                        continue;
                    }
                    if (ck == null) {
                        ck = new Check(cell, th);
                        cell.getOverlay().add(ck);
                        Xp.setAdditionalInstanceField(cell, F_CHECK, ck);
                        ck.set(picked.contains(dri), false);
                        ck.appear();
                    } else ck.set(picked.contains(dri), true);
                    ck.place();
                }
            }
        };
        final Runnable setMode = () -> {
            sub.setText(selecting[0] ? (contacts ? "Tick contacts to unhide." : "Tick apps to unhide.") : "Tap to share. Long-press to unhide.");
            refreshChecks.run();
            updatePill.run();
        };
        edit.setOnClickListener(x -> {
            selecting[0] = !selecting[0];
            picked.clear();
            edit.setActive(selecting[0]);
            Haptics.tick(a);
            setMode.run();
        });
        unhidePill.setOnClickListener(x -> {
            if (picked.isEmpty()) return;
            final List<Object> go = new ArrayList<>(picked);
            for (Object dri : go) { if (contacts) unhideContact(a, dri); else unhide(a, dri); }
            items.removeAll(go);
            final boolean last = items.isEmpty();
            selecting[0] = false; picked.clear();
            edit.setActive(false);
            Haptics.tick(a);
            List<View> out = new ArrayList<>();
            for (int r = 0; r < grid.getChildCount(); r++) {
                ViewGroup row = (ViewGroup) grid.getChildAt(r);
                for (int c = 0; c < row.getChildCount(); c++) if (go.contains(row.getChildAt(c).getTag())) out.add(row.getChildAt(c));
            }
            setMode.run();
            for (View v : out) v.animate().scaleX(0.5f).scaleY(0.5f).alpha(0f).setDuration(200).start();
            grid.postDelayed(() -> {
                if (last) { quiet[0] = true; d.dismiss(); }
                else { fill[0].run(); grid.setAlpha(0.4f); grid.animate().alpha(1f).setDuration(160).start(); }
                unhideFromPanel(a, type, go, last);
            }, 220);
        });
        fill[0] = () -> {
            grid.removeAllViews();
            LinearLayout row = null;
            for (int i = 0; i < items.size(); i++) {
                if (i % 4 == 0) { row = new LinearLayout(a); row.setOrientation(LinearLayout.HORIZONTAL); grid.addView(row, new LinearLayout.LayoutParams(-1, -2)); }
                final Object dri = items.get(i);
                final LinearLayout cell = new LinearLayout(a);
                cell.setOrientation(LinearLayout.VERTICAL);
                cell.setGravity(Gravity.CENTER_HORIZONTAL);
                cell.setPadding(dp(a, 4), dp(a, 10), dp(a, 4), dp(a, 10));
                cell.setBackground(new RippleDrawable(ColorStateList.valueOf(th.ripple), null, new Surface(th, dp(a, 16), Color.WHITE, false)));
                cell.setTag(dri);
                ImageView iv = new ImageView(a);
                iv.setId(android.R.id.icon);
                Drawable ic = icon(a, dri);
                if (ic != null) iv.setImageDrawable(copy(a, ic));
                cell.addView(iv, new LinearLayout.LayoutParams(dp(a, 52), dp(a, 52)));
                TextView lb = new TextView(a);
                lb.setText(label(a, dri)); lb.setTextColor(th.text); lb.setTextSize(12); lb.setGravity(Gravity.CENTER);
                lb.setMaxLines(2); lb.setEllipsize(TextUtils.TruncateAt.END);
                lb.setPadding(0, dp(a, 6), 0, 0);
                cell.addView(lb, new LinearLayout.LayoutParams(-1, -2));
                cell.setOnClickListener(x -> {
                    if (selecting[0]) {
                        if (picked.contains(dri)) picked.remove(dri); else picked.add(dri);
                        Haptics.tick(a);
                        refreshChecks.run();
                        updatePill.run();
                        return;
                    }
                    quiet[0] = true; d.dismiss(); if (contacts) launchContact(a, la, dri); else launch(a, dri);
                });
                cell.setOnLongClickListener(x -> {
                    if (selecting[0]) return true;
                    Haptics.tick(a);
                    showUnhide(a, th, cell, contacts ? "Unhide contact" : "Unhide app", () -> {
                        if (contacts) unhideContact(a, dri); else unhide(a, dri);
                        items.remove(dri);
                        final boolean last = items.isEmpty();
                        cell.animate().scaleX(0.5f).scaleY(0.5f).alpha(0f).setDuration(200).withEndAction(() -> {
                            if (last) { quiet[0] = true; d.dismiss(); }
                            else { fill[0].run(); grid.setAlpha(0.4f); grid.animate().alpha(1f).setDuration(160).start(); }
                            unhideFromPanel(a, type, Collections.singletonList(dri), last);
                        }).start();
                    });
                    return true;
                });
                row.addView(cell, new LinearLayout.LayoutParams(0, -2, 1f));
            }
            if (row != null) for (int k = row.getChildCount(); k < 4; k++) row.addView(new View(a), new LinearLayout.LayoutParams(0, 1, 1f));
        };
        fill[0].run();
        card.addView(unhidePill, plp);
        grid.measure(View.MeasureSpec.makeMeasureSpec(a.getResources().getDisplayMetrics().widthPixels - dp(a, 80), View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED);
        if (grid.getMeasuredHeight() > maxH) sv.getLayoutParams().height = maxH;

        d.setContentView(root, new ViewGroup.LayoutParams(-1, -2));
        d.setCanceledOnTouchOutside(true);
        final Window w = d.getWindow();
        final float dimTo = 0f;
        final int blurTo = 0;
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout(-1, -2);
            w.setGravity(Gravity.CENTER);
            w.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams lp = w.getAttributes();
            lp.dimAmount = 0f;
            lp.windowAnimations = 0;
            w.setAttributes(lp);
        }
        sScrim = new java.lang.ref.WeakReference<View>(null);
        if (!th.superDark) card.setElevation(dp(a, 8));
        final int[] from = new int[2];
        if (folder != null && folder.isAttachedToWindow()) { folder.getLocationOnScreen(from); from[0] += folder.getWidth() / 2; from[1] += folder.getHeight() / 2; }
        else { from[0] = a.getResources().getDisplayMetrics().widthPixels / 2; from[1] = a.getResources().getDisplayMetrics().heightPixels / 2; }
        final float startScale = folder != null && folder.getWidth() > 0 ? Math.max(0.12f, Math.min(0.4f, folder.getWidth() / (float) (a.getResources().getDisplayMetrics().widthPixels - dp(a, 40)))) : 0.6f;
        final PathInterpolator emphasized = new PathInterpolator(0.05f, 0.7f, 0.1f, 1f), accelerate = new PathInterpolator(0.3f, 0f, 0.8f, 0.15f);
        final Runnable aim = () -> {
            int[] at = new int[2]; card.getLocationOnScreen(at);
            card.setPivotX(Math.max(0, Math.min(card.getWidth(), from[0] - at[0])));
            card.setPivotY(Math.max(0, Math.min(card.getHeight(), from[1] - at[1])));
        };
        d.closer = done -> {
            card.animate().cancel();
            card.setPivotX(card.getWidth() / 2f); card.setPivotY(card.getHeight() / 2f);
            card.animate().alpha(0f).scaleX(0.94f).scaleY(0.94f).setStartDelay(0).setDuration(150).setInterpolator(new android.view.animation.AccelerateInterpolator(1.5f)).withEndAction(done).start();
        };
        card.setAlpha(0f);
        d.show();
        card.setScaleX(0.94f); card.setScaleY(0.94f);
        card.post(() -> {
            card.setPivotX(card.getWidth() / 2f); card.setPivotY(card.getHeight() / 2f);
            card.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(200).setInterpolator(new DecelerateInterpolator(1.5f)).start();
        });
    }

    private static final class PanelDialog extends Dialog {
        interface Closer { void close(Runnable done); }
        Closer closer; boolean closing;
        PanelDialog(Context c) { super(c); }
        @Override public void dismiss() {
            if (closing || closer == null || !isShowing()) { super.dismiss(); return; }
            closing = true;
            try { closer.close(() -> { try { PanelDialog.super.dismiss(); } catch (Throwable ignored) { } }); }
            catch (Throwable t) { super.dismiss(); }
        }
    }

    private static java.lang.ref.WeakReference<View> sScrim = new java.lang.ref.WeakReference<View>(null);

    private static void backdrop(final Window w, float from, float to, final float dimTo, final int blurTo, long ms, android.animation.TimeInterpolator curve) {
        if (w == null) return;
        final View scrim = sScrim.get();
        android.animation.ValueAnimator va = android.animation.ValueAnimator.ofFloat(from, to).setDuration(ms);
        va.setInterpolator(curve);
        va.addUpdateListener(x -> {
            try {
                float f = (Float) x.getAnimatedValue();
                WindowManager.LayoutParams lp = w.getAttributes();
                if (scrim != null) scrim.setAlpha(dimTo * f);
                if (blurTo > 0) { WindowManager.LayoutParams.class.getMethod("setBlurBehindRadius", int.class).invoke(lp, Math.round(blurTo * f)); w.setAttributes(lp); }
            } catch (Throwable ignored) { }
        });
        va.start();
    }

    private static void blurBehind(Window w, int radius) {
        try {
            w.addFlags(4 );
            WindowManager.LayoutParams lp = w.getAttributes();
            WindowManager.LayoutParams.class.getMethod("setBlurBehindRadius", int.class).invoke(lp, radius);
            w.setAttributes(lp);
        } catch (Throwable ignored) { }
    }

    private static void showUnhide(Activity a, Th th, View anchor, final Runnable action) { showUnhide(a, th, anchor, "Unhide app", action); }

    private static void showUnhide(Activity a, Th th, View anchor, String unhideLabel, final Runnable action) {
        LinearLayout pill = new LinearLayout(a);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setPadding(dp(a, 16), dp(a, 12), dp(a, 20), dp(a, 12));
        pill.setBackground(new Surface(th, dp(a, 24), th.menu));
        ImageView iv = new ImageView(a);
        iv.setImageDrawable(new Glyph(Glyph.EYE, th.text, a.getResources()));
        pill.addView(iv, new LinearLayout.LayoutParams(dp(a, 20), dp(a, 20)));
        TextView tv = new TextView(a);
        tv.setText(unhideLabel); tv.setTextColor(th.text); tv.setTextSize(15);
        tv.setPadding(dp(a, 12), 0, 0, 0);
        pill.addView(tv);
        final PopupWindow pw = new PopupWindow(pill, -2, -2, true);
        pw.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        pw.setOutsideTouchable(true);
        if (!th.superDark) pw.setElevation(dp(a, 6));
        pill.setOnClickListener(x -> { pw.dismiss(); action.run(); });
        pill.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int[] l = new int[2]; anchor.getLocationOnScreen(l);
        int x = l[0] + anchor.getWidth() / 2 - pill.getMeasuredWidth() / 2;
        int y = l[1] - pill.getMeasuredHeight() - dp(a, 6);
        if (y < dp(a, 24)) y = l[1] + anchor.getHeight() + dp(a, 6);
        pill.setAlpha(0f); pill.setScaleX(0.85f); pill.setScaleY(0.85f);
        pw.showAtLocation(anchor, Gravity.NO_GRAVITY, Math.max(dp(a, 8), x), y);
        pill.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(160).setInterpolator(new DecelerateInterpolator()).start();
    }

    private static void fadeFolderThenRecreate(final Activity a, int type) {
        View f = findFolder(a.getWindow().getDecorView(), type);
        if (f == null) { recreate(a); return; }
        f.animate().alpha(0f).scaleX(0.6f).scaleY(0.6f).setDuration(260).setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> recreate(a)).start();
    }

    private static void launchContact(Activity a, Object la, Object t) {
        try {
            Xp.setBooleanField(a, "mIsSuccessfullySelected", true);
            Object user = null;
            try { user = Xp.getObjectField(la, "mUserHandle"); } catch (Throwable ignored) { }
            if (user == null) user = android.os.Process.myUserHandle();
            Xp.callMethod(a, "safelyStartActivityAsUser", t, user);
            if (!bool(t, "isSuspended")) a.finish();
        } catch (Throwable ignored) { }
    }

    private static void unhideContact(Activity a, Object t) {
        Set<String> h = new HashSet<>(sHiddenContacts);
        h.remove(contactKey(t));
        sHiddenContacts = h;
        prefs(a).edit().putStringSet(P_CHIDDEN, new HashSet<>(h)).apply();
    }

    static void hideContacts(final Activity a, List<String> keys) {
        if (keys.isEmpty()) return;
        Set<String> h = new HashSet<>(sHiddenContacts);
        h.addAll(keys);
        sHiddenContacts = h;
        prefs(a).edit().putStringSet(P_CHIDDEN, new HashSet<>(h)).apply();
        final ViewGroup decor = (ViewGroup) a.getWindow().getDecorView();
        List<View> all = new ArrayList<>(), fly = new ArrayList<>();
        cells(decor, all);
        for (View v : all) {
            Object t = Xp.getAdditionalInstanceField(v, F_TARGET);
            if (t != null && v.isShown() && bool(t, "isSelectableTargetInfo") && keys.contains(contactKey(t))) fly.add(v);
        }
        int[] ld = new int[2]; decor.getLocationInWindow(ld);
        float[] dest;
        View f = findFolder(decor, CFOLDER_TYPE);
        Rect r = new Rect();
        boolean intoFolder = f != null && f.getGlobalVisibleRect(r) && r.height() > f.getHeight() / 2;
        if (intoFolder) { View iv = f.findViewById(android.R.id.icon); if (iv != null) iv.getGlobalVisibleRect(r); dest = new float[]{r.exactCenterX(), r.exactCenterY()}; }
        else { decor.getGlobalVisibleRect(r); dest = new float[]{r.exactCenterX(), r.bottom + dp(a, 72)}; }
        Haptics.tick(a);
        for (final View v : fly) {
            final Ghost g = Ghost.of(v, decor);
            if (g == null) continue;
            v.setAlpha(0f);
            g.lift(true);
            final float gx = g.getTranslationX(), gy = g.getTranslationY();
            final float tx = dest[0] - ld[0] - g.getWidth() / 2f, ty = dest[1] - ld[1] - g.getHeight() / 2f;
            final boolean folder = intoFolder;
            g.animate().scaleX(1.14f).scaleY(1.14f).translationY(gy - dp(a, 12)).setDuration(170).setInterpolator(new DecelerateInterpolator(1.5f)).withEndAction(() -> {
                final float sy = g.getTranslationY();
                final PathInterpolator ex = new PathInterpolator(0.3f, 0f, 0.2f, 1f), ey = new PathInterpolator(0.55f, 0f, 0.85f, 0.35f);
                ValueAnimator va = ValueAnimator.ofFloat(0f, 1f).setDuration(520);
                va.addUpdateListener(an -> {
                    float tt = (Float) an.getAnimatedValue();
                    g.setTranslationX(gx + (tx - gx) * ex.getInterpolation(tt));
                    g.setTranslationY(sy + (ty - sy) * ey.getInterpolation(tt));
                    float sc = 1.14f + ((folder ? 0.25f : 0.6f) - 1.14f) * tt;
                    g.setScaleX(sc); g.setScaleY(sc);
                    g.setAlpha(folder ? 1f - 0.85f * tt * tt : 1f - 0.3f * tt);
                });
                va.addListener(new android.animation.AnimatorListenerAdapter() {
                    @Override public void onAnimationEnd(android.animation.Animator an) { ViewGroup pp = (ViewGroup) g.getParent(); if (pp != null) pp.removeView(g); }
                });
                g.lift(false);
                va.start();
            }).start();
        }
        final boolean newFolder = findFolder(decor, CFOLDER_TYPE) == null;
        MAIN.postDelayed(() -> {
            sFolderPop = newFolder ? CFOLDER_TYPE : -CFOLDER_TYPE;
            if (!hideContactsInPlace(a, keys)) { sFolderPop = CFOLDER_TYPE; recreate(a); }
            for (View v : fly) v.setAlpha(1f);
        }, fly.isEmpty() ? 0 : 720);
    }

    private static void launch(Activity a, Object dri) {
        try {
            Xp.setBooleanField(a, "mIsSuccessfullySelected", true);
            try { Xp.callMethod(a, "maybeRemoveSharedText", dri); } catch (Throwable ignored) { }
            ResolveInfo ri = (ResolveInfo) Xp.getObjectField(dri, "mResolveInfo");
            Object user = null;
            try { user = Xp.getObjectField(ri, "userHandle"); } catch (Throwable ignored) { }
            if (user == null) user = android.os.Process.myUserHandle();
            Xp.callMethod(a, "safelyStartActivityAsUser", dri, user);
            if (!bool(dri, "isSuspended")) a.finish();
        } catch (Throwable ignored) { }
    }

    private static boolean selectable(View v, Object t) {
        if (t == null || bool(t, "isSelectableTargetInfo") || bool(t, "isPinned")) return false;
        if (!bool(t, "isDisplayResolveInfo") && !bool(t, "isMultiDisplayResolveInfo")) return false;
        try {
            Object la = Xp.getAdditionalInstanceField(v, F_ADAPTER), pos = Xp.getAdditionalInstanceField(v, F_POS);
            Object type = Xp.callMethod(la, "getPositionTargetType", pos);
            return Integer.valueOf(2).equals(type) || Integer.valueOf(3).equals(type);
        } catch (Throwable e) { return false; }
    }

    private static void decorate(View v) {
        Object t = Xp.getAdditionalInstanceField(v, F_TARGET);
        Check ck = (Check) Xp.getAdditionalInstanceField(v, F_CHECK);
        if (!sSelecting) {
            if (ck != null) { v.getOverlay().remove(ck); Xp.setAdditionalInstanceField(v, F_CHECK, null); }
            if (Boolean.TRUE.equals(Xp.getAdditionalInstanceField(v, F_DIM))) { v.setAlpha(1f); Xp.setAdditionalInstanceField(v, F_DIM, null); }
            return;
        }
        if (!selectable(v, t)) {
            if (ck != null) { v.getOverlay().remove(ck); Xp.setAdditionalInstanceField(v, F_CHECK, null); }
            v.animate().alpha(0.35f).setDuration(150).start();
            Xp.setAdditionalInstanceField(v, F_DIM, Boolean.TRUE);
            return;
        }
        if (Boolean.TRUE.equals(Xp.getAdditionalInstanceField(v, F_DIM))) { v.animate().alpha(1f).setDuration(150).start(); Xp.setAdditionalInstanceField(v, F_DIM, null); }
        boolean on = sSel.containsKey(key(comps(t)));
        if (ck == null) {
            ck = new Check(v, theme(v.getContext()));
            v.getOverlay().add(ck);
            Xp.setAdditionalInstanceField(v, F_CHECK, ck);
            ck.set(on, false);
            ck.appear();
        } else ck.set(on, true);
        ck.place();
    }

    private static void refreshAll(Activity a) {
        List<View> all = new ArrayList<>(); cells(a.getWindow().getDecorView(), all);
        for (View v : all) decorate(v);
        for (int type : new int[]{FOLDER_TYPE, CFOLDER_TYPE}) {
            View f = findFolder(a.getWindow().getDecorView(), type);
            if (f != null) f.animate().alpha(sSelecting ? 0.35f : 1f).setDuration(150).start();
        }
    }

    private static void toggle(View v) {
        Object t = Xp.getAdditionalInstanceField(v, F_TARGET);
        if (!selectable(v, t)) return;
        List<String> c = comps(t);
        String k = key(c);
        if (sSel.containsKey(k)) sSel.remove(k); else sSel.put(k, c);
        Haptics.tick(v.getContext());
        Activity a = activityOf(v.getContext());
        if (a != null) refreshAll(a);
        if (sChrome != null) sChrome.update();
    }

    static void setSelecting(Activity a, boolean on) {
        if (sSelecting == on) return;
        if (on && sDrag != null) return;
        sSelecting = on;
        sSel.clear();
        refreshAll(a);
        setBack(a, on);
        if (sChrome != null) { sChrome.pin.setActive(on); sChrome.update(); }
        Haptics.tick(a);
    }

    private static void hideSelected(Activity a) {
        List<String> comps = new ArrayList<>();
        for (List<String> l : sSel.values()) for (String c : l) if (!comps.contains(c)) comps.add(c);
        List<View> fly = new ArrayList<>(), all = new ArrayList<>();
        cells(a.getWindow().getDecorView(), all);
        for (View v : all) {
            Object t = Xp.getAdditionalInstanceField(v, F_TARGET);
            if (v.isShown() && t != null && sSel.containsKey(key(comps(t)))) fly.add(v);
        }
        setSelecting(a, false);
        hideApps(a, comps, fly);
    }

    private static void setBack(final Activity a, boolean on) {
        try {
            Object disp = Activity.class.getMethod("getOnBackInvokedDispatcher").invoke(a);
            Class<?> cbI = Class.forName("android.window.OnBackInvokedCallback");
            Class<?> dI = Class.forName("android.window.OnBackInvokedDispatcher");
            if (on && sBackCb == null) {
                sBackCb = java.lang.reflect.Proxy.newProxyInstance(cbI.getClassLoader(), new Class<?>[]{cbI}, (proxy, m, args) -> {
                    if ("onBackInvoked".equals(m.getName())) MAIN.post(() -> setSelecting(a, false));
                    else if ("hashCode".equals(m.getName())) return System.identityHashCode(proxy);
                    else if ("equals".equals(m.getName())) return proxy == args[0];
                    return null;
                });
                dI.getMethod("registerOnBackInvokedCallback", int.class, cbI).invoke(disp, 1000000 , sBackCb);
            } else if (!on && sBackCb != null) {
                dI.getMethod("unregisterOnBackInvokedCallback", cbI).invoke(disp, sBackCb);
                sBackCb = null;
            }
        } catch (Throwable ignored) { if (!on) sBackCb = null; }
    }

    private static final class Check extends Drawable {
        final View cell; final Th th; final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); final Path tick = new Path();
        boolean on; float prog, appear = 1f;
        Check(View cell, Th th) { this.cell = cell; this.th = th; }
        void set(boolean v, boolean anim) {
            if (v == on && anim) return;
            on = v;
            if (!anim) { prog = v ? 1f : 0f; invalidateSelf(); return; }
            ValueAnimator va = ValueAnimator.ofFloat(prog, v ? 1f : 0f).setDuration(200);
            va.setInterpolator(new OvershootInterpolator(2f));
            va.addUpdateListener(x -> { prog = (Float) x.getAnimatedValue(); invalidateSelf(); cell.invalidate(); });
            va.start();
        }
        void appear() {
            ValueAnimator va = ValueAnimator.ofFloat(0f, 1f).setDuration(220);
            va.setInterpolator(new OvershootInterpolator(1.8f));
            va.addUpdateListener(x -> { appear = (Float) x.getAnimatedValue(); invalidateSelf(); cell.invalidate(); });
            va.start();
        }
        void place() {
            View iv = cell.findViewById(android.R.id.icon);
            if (iv == null || iv.getWidth() == 0) { cell.post(() -> { View i2 = cell.findViewById(android.R.id.icon); if (i2 != null && i2.getWidth() > 0) place(); }); return; }
            float l = iv.getLeft() + iv.getPaddingLeft(), t = iv.getTop() + iv.getPaddingTop();
            float w = iv.getWidth() - iv.getPaddingLeft() - iv.getPaddingRight(), h = iv.getHeight() - iv.getPaddingTop() - iv.getPaddingBottom();
            float s = Math.min(w, h), cx = l + w / 2f + s * 0.36f, cy = t + h / 2f - s * 0.36f;
            int r = Math.max(1, Math.round(s * 0.17f));
            setBounds(Math.round(cx) - r, Math.round(cy) - r, Math.round(cx) + r, Math.round(cy) + r);
            cell.invalidate();
        }
        @Override public void draw(Canvas c) {
            Rect b = getBounds();
            if (b.isEmpty()) return;
            float cx = b.exactCenterX(), cy = b.exactCenterY(), r = b.width() / 2f * Math.max(0f, appear);
            float sw = Math.max(2f, r * 0.16f);
            p.setStyle(Paint.Style.FILL); p.setColor(th.card | 0xFF000000);
            c.drawCircle(cx, cy, r, p);
            float pr = Math.max(0f, Math.min(1.2f, prog));
            if (pr > 0.01f) {
                p.setColor(th.accent);
                c.drawCircle(cx, cy, r * Math.min(1f, pr), p);
                tick.reset();
                tick.moveTo(cx - r * 0.42f, cy + r * 0.02f);
                tick.lineTo(cx - r * 0.12f, cy + r * 0.32f);
                tick.lineTo(cx + r * 0.45f, cy - r * 0.3f);
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(r * 0.2f); p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeJoin(Paint.Join.ROUND);
                p.setColor(th.onAccent); p.setAlpha((int) (255 * Math.min(1f, pr)));
                c.drawPath(tick, p);
                p.setAlpha(255);
            }
            if (pr < 0.99f) {
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(sw); p.setColor(th.text2);
                p.setAlpha((int) (255 * (1f - Math.min(1f, pr))));
                c.drawCircle(cx, cy, r - sw / 2f, p);
                p.setAlpha(255);
            }
        }
        @Override public void setAlpha(int a) { }
        @Override public void setColorFilter(ColorFilter f) { }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    private static void attachChrome(Activity a) {
        if (!sOn || a.isFinishing()) return;
        ViewGroup decor = (ViewGroup) a.getWindow().getDecorView();
        Chrome ch = new Chrome(a, decor);
        sChrome = ch;
    }

    private static void installTouchArea() {
        try {
            Xp.hookAllMethods(ViewTreeObserver.class, "dispatchOnComputeInternalInsets", new Xp.Callback() {
                @Override protected void afterHookedMethod(Xp.Param p) {
                    try {
                        Chrome ch = sChrome;
                        if (ch == null || p.args.length != 1 || p.args[0] == null) return;
                        Object info = p.args[0];
                        java.lang.reflect.Field mode = info.getClass().getDeclaredField("mTouchableInsets");
                        mode.setAccessible(true);
                        if (mode.getInt(info) != 3) return;
                        android.graphics.Region region = (android.graphics.Region) info.getClass().getField("touchableRegion").get(info);
                        for (View b : new View[]{ch.pin, ch.minus, ch.plus}) {
                            if (b.getVisibility() != View.VISIBLE || !b.isAttachedToWindow() || b.getRootView() != ((View) ch.decor).getRootView()) continue;
                            int[] at = new int[2];
                            b.getLocationInWindow(at);
                            int pad = dp(b.getContext(), 6);
                            region.op(at[0] - pad, at[1] - pad, at[0] + b.getWidth() + pad, at[1] + b.getHeight() + pad, android.graphics.Region.Op.UNION);
                        }
                    } catch (Throwable ignored) { }
                }
            });
        } catch (Throwable ignored) { }
    }

    private static final float FLOAT_K = 0.86f;

    private static final class Chrome implements ViewTreeObserver.OnPreDrawListener {
        final Activity act; final ViewGroup decor; final FrameLayout layer; final EditBubble pin, minus, plus; final TextView pill; final Th th;
        final int size, bh, margin, rs; boolean pillShown; View headline, row; int headPadEnd = -1; boolean busy;

        Chrome(Activity a, ViewGroup decor) {
            this.act = a; this.decor = decor; th = theme(a);
            margin = dp(a, 8);
            EditBubble eb = new EditBubble(a, th);
            size = eb.wantWidth(); bh = EditBubble.wantHeight(a);
            layer = new FrameLayout(a);
            layer.setClipChildren(false);
            decor.addView(layer, new FrameLayout.LayoutParams(-1, -1));
            layer.setTranslationZ(dp(a, 16));
            pin = eb;
            pin.setContentDescription("Edit: select apps to hide");
            pin.setOnClickListener(v -> setSelecting(act, !sSelecting));
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(size, bh, Gravity.TOP | Gravity.START);
            layer.addView(pin, lp);
            pin.setVisibility(View.INVISIBLE);
            pill = new TextView(a);
            pill.setTextColor(th.onAccent); pill.setTextSize(15); pill.setGravity(Gravity.CENTER);
            pill.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
            pill.setPadding(dp(a, 24), dp(a, 14), dp(a, 24), dp(a, 14));
            pill.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), new Surface(th, dp(a, 28), th.accent, false), null));
            pill.setCompoundDrawablePadding(dp(a, 10));
            Glyph g = new Glyph(Glyph.EYE_OFF, th.onAccent, a.getResources()); g.setBounds(0, 0, dp(a, 20), dp(a, 20));
            pill.setCompoundDrawablesRelative(g, null, null, null);
            if (!th.superDark) pill.setElevation(dp(a, 6));
            if (!th.superDark && !th.blur) pin.setElevation(dp(a, 1));
            pill.setOnClickListener(v -> { if (!sSel.isEmpty()) hideSelected(act); });
            layer.addView(pill, new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START));
            pill.setVisibility(View.INVISIBLE);
            rs = dp(a, 30);
            minus = new EditBubble(a, th, 1);
            minus.setContentDescription("Hide the contacts row");
            minus.setOnClickListener(v -> { if (!busy) collapseContacts(this); });
            plus = new EditBubble(a, th, 2);
            plus.setContentDescription("Show the contacts row");
            plus.setOnClickListener(v -> { if (!busy) expandContacts(this); });
            for (EditBubble b : new EditBubble[]{minus, plus}) {
                if (!th.superDark && !th.blur) b.setElevation(dp(a, 1));
                layer.addView(b, new FrameLayout.LayoutParams(rs, rs, Gravity.TOP | Gravity.START));
                b.setVisibility(View.INVISIBLE);
            }
            decor.getViewTreeObserver().addOnPreDrawListener(this);
        }

        WeakReference<View> listRef = new WeakReference<>(null);
        View targetList() {
            View v = listRef.get();
            if (v != null && v.isAttachedToWindow() && v.isShown()) return v;
            v = shownByClass(decor, "ChooserTargetView");
            listRef = new WeakReference<>(v);
            return v;
        }

        void pop(final View b, boolean show) {
            Object state = b.getTag();
            if (show && !Boolean.TRUE.equals(state)) {
                b.setTag(Boolean.TRUE);
                b.animate().cancel();
                b.setVisibility(View.VISIBLE); b.setAlpha(0f); b.setScaleX(0.5f); b.setScaleY(0.5f);
                b.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(220).setInterpolator(new OvershootInterpolator(1.8f)).start();
            } else if (!show && Boolean.TRUE.equals(state)) {
                b.setTag(Boolean.FALSE);
                b.animate().cancel();
                b.animate().alpha(0f).scaleX(0.5f).scaleY(0.5f).setDuration(150).withEndAction(() -> { if (!Boolean.TRUE.equals(b.getTag())) b.setVisibility(View.INVISIBLE); }).start();
            }
        }

        final Map<String, Integer> ids = new HashMap<>();
        View byId(String name) {
            Integer id = ids.get(name);
            if (id == null) { id = act.getResources().getIdentifier(name, "id", act.getPackageName()); ids.put(name, id); }
            return id == 0 ? null : decor.findViewById(id);
        }

        void update() {
            int n = sSel.size();
            boolean show = sSelecting && n > 0;
            if (n > 0) pill.setText(n == 1 ? "Hide 1 app" : "Hide " + n + " apps");
            if (show != pillShown) {
                pillShown = show;
                pill.animate().cancel();
                if (show) {
                    pill.setVisibility(View.VISIBLE); pill.setAlpha(0f); pill.setScaleX(0.9f); pill.setScaleY(0.9f);
                    pill.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(220).setInterpolator(new OvershootInterpolator(1.4f)).start();
                } else {
                    pill.animate().alpha(0f).scaleX(0.9f).scaleY(0.9f).setDuration(160).withEndAction(() -> { if (!pillShown) pill.setVisibility(View.INVISIBLE); }).start();
                }
            }
        }

        @Override public boolean onPreDraw() {
            if (sChrome != this || act.isDestroyed()) { decor.getViewTreeObserver().removeOnPreDrawListener(this); return true; }
            try { place(); } catch (Throwable ignored) { }
            return true;
        }

        void place() {
            Object la = activeAdapter(act);
            boolean ready = la != null && Xp.getBooleanField(la, "mAppTargetsReady");
            if (row == null || !row.isAttachedToWindow()) { row = byId("chooser_headline_row"); headline = byId("headline"); headPadEnd = -1; }
            int[] l = new int[2], ld = new int[2];
            decor.getLocationInWindow(ld);
            float x, y;
            float panelTop = Float.NaN;
            float panelRight = 0;
            if (row != null && row.isShown() && row.getHeight() > 0) {
                row.getLocationInWindow(l);
                x = l[0] + row.getWidth() - row.getPaddingEnd() - size;
                for (String other : new String[]{"reselection_action", "include_text_action"}) {
                    View o = byId(other);
                    if (o != null && o.isShown()) { int[] lo = new int[2]; o.getLocationInWindow(lo); x = Math.min(x, lo[0] - margin - size); }
                }
                float cy = l[1] + row.getHeight() / 2f;
                if (headline != null && headline.isShown() && headline.getHeight() > 0) {
                    int[] lh = new int[2]; headline.getLocationInWindow(lh); cy = lh[1] + headline.getHeight() / 2f;
                    if (headPadEnd < 0 && headline instanceof TextView) {
                        headPadEnd = headline.getPaddingEnd();
                        headline.setPaddingRelative(headline.getPaddingStart(), headline.getPaddingTop(), headPadEnd + size + margin, headline.getPaddingBottom());
                    }
                }
                y = cy - bh / 2f;
            } else {
                View drawer = findByClass(decor, "ResolverDrawerLayout");
                if (drawer == null || !(drawer instanceof ViewGroup) || ((ViewGroup) drawer).getChildCount() == 0) { pin.setVisibility(View.INVISIBLE); return; }
                ViewGroup g = (ViewGroup) drawer; View top = null; int topY = Integer.MAX_VALUE;
                for (int i = 0; i < g.getChildCount(); i++) { View c = g.getChildAt(i); if (c.getVisibility() == View.VISIBLE && c.getHeight() > 0 && c.getTop() < topY) { topY = c.getTop(); top = c; } }
                if (top == null) { pin.setVisibility(View.INVISIBLE); return; }
                top.getLocationInWindow(l);
                x = l[0] + top.getWidth() - size - dp(act, 16);
                int topInset = 0;
                try { android.view.WindowInsets wi = decor.getRootWindowInsets(); if (wi != null) topInset = wi.getSystemWindowInsetTop(); } catch (Throwable ignored) { }
                float above = l[1] - dp(act, 8) - bh;
                if (above >= ld[1] + topInset + dp(act, 4)) {
                    y = above + 8f * act.getResources().getDisplayMetrics().density; panelTop = l[1]; panelRight = l[0] + top.getWidth();
                    x = l[0] + (top.getWidth() - size) / 2f;
                }
                else y = l[1] + dp(act, 12);
            }
            float fk = Float.isNaN(panelTop) ? 1f : FLOAT_K;
            pin.setK(fk); minus.setK(fk); plus.setK(fk);
            pin.setTranslationX(x - ld[0]); pin.setTranslationY(y - ld[1]);
            int vis = ready ? View.VISIBLE : View.INVISIBLE;
            if (pin.getVisibility() != vis) {
                pin.setVisibility(vis);
                if (vis == View.VISIBLE) { pin.setAlpha(0f); pin.setScaleX(0.6f); pin.setScaleY(0.6f); pin.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(220).setInterpolator(new OvershootInterpolator(1.6f)).start(); }
            }
            View list = targetList();
            View crow = contactsRow(list);
            boolean showMinus = ready && sSelecting && !sContactsHidden && !busy && crow != null && crow.isShown() && crow.getHeight() > dp(act, 24);
            float bob = (float) (1.2f * act.getResources().getDisplayMetrics().density * Math.sin(2 * Math.PI * (android.os.SystemClock.uptimeMillis() % 1800L) / 1800.0));
            float dd = act.getResources().getDisplayMetrics().density;
            float besideX = x + size / 2f + 57.5f * dd - rs / 2f;
            float besideY = panelTop - dp(act, 3) - (bh + rs) / 2f + dd + bob;
            if (showMinus && list != null) {
                list.getLocationInWindow(l);
                if (Float.isNaN(panelTop)) {
                    minus.setTranslationX(l[0] + (list.getWidth() - rs) / 2f - ld[0]);
                    minus.setTranslationY(l[1] - dp(act, 10) - rs / 2f - ld[1] + bob);
                } else { minus.setTranslationX(besideX - ld[0]); minus.setTranslationY(besideY - ld[1]); }
            }
            pop(minus, showMinus);
            boolean showPlus = ready && sSelecting && sContactsHidden && !busy && list != null && list.isShown();
            if (showPlus) {
                list.getLocationInWindow(l);
                if (Float.isNaN(panelTop)) {
                    plus.setTranslationX(l[0] + (list.getWidth() - rs) / 2f - ld[0]);
                    plus.setTranslationY(l[1] - dp(act, 10) - rs / 2f - ld[1] + bob);
                } else { plus.setTranslationX(besideX - ld[0]); plus.setTranslationY(besideY - ld[1]); }
            }
            pop(plus, showPlus);
            if (showMinus || showPlus) layer.postInvalidateOnAnimation();
            if (pill.getVisibility() == View.VISIBLE) {
                int bottomInset = 0;
                try { android.view.WindowInsets wi = decor.getRootWindowInsets(); if (wi != null) bottomInset = wi.getSystemWindowInsetBottom(); } catch (Throwable ignored) { }
                pill.setTranslationX((decor.getWidth() - pill.getWidth()) / 2f);
                pill.setTranslationY(decor.getHeight() - bottomInset - dp(act, 24) - pill.getHeight());
            }
        }
    }

    private static final class EditBubble extends View {
        final Th th; final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), sheen = new Paint(Paint.ANTI_ALIAS_FLAG),
                rim = new Paint(Paint.ANTI_ALIAS_FLAG), ink = new Paint(Paint.ANTI_ALIAS_FLAG), shade = new Paint(Paint.ANTI_ALIAS_FLAG);
        final float d; float act; final RectF r = new RectF(); int kind;
        float k = 1f;
        void setK(float v) { if (k != v) { k = v; invalidate(); } }
        EditBubble(Context c, Th th, int kind) { this(c, th); this.kind = kind; }
        EditBubble(Context c, Th th) {
            super(c);
            this.th = th;
            d = c.getResources().getDisplayMetrics().density;
            ink.setTextSize(14 * c.getResources().getDisplayMetrics().scaledDensity);
            ink.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
            rim.setStyle(Paint.Style.STROKE);
            rim.setStrokeWidth(th.superDark ? 2.4f * d : 1.2f * d);
            if (th.superDark && th.rims) { try { rim.setMaskFilter(new android.graphics.BlurMaskFilter(1.2f * d, android.graphics.BlurMaskFilter.Blur.NORMAL)); } catch (Throwable ignored) { } }
            shade.setStyle(Paint.Style.STROKE); shade.setStrokeWidth(1f * d);
            setClickable(true);
            setBackground(new RippleDrawable(ColorStateList.valueOf(th.ripple), null, new Surface(th, 10000, Color.WHITE, false)));
            if (th.superDark && th.rims) setLayerType(LAYER_TYPE_SOFTWARE, null);
        }
        int wantWidth() { return Math.round(12 * d + 4 * d + 8 * d + Math.max(ink.measureText("Edit"), ink.measureText("Done")) + 14 * d); }
        static int wantHeight(Context c) { return dp(c, 34); }
        void setActive(boolean on) {
            ValueAnimator va = ValueAnimator.ofFloat(act, on ? 1f : 0f).setDuration(220);
            va.addUpdateListener(x -> { act = (Float) x.getAnimatedValue(); invalidate(); });
            va.start();
            animate().scaleX(0.9f).scaleY(0.9f).setDuration(80).withEndAction(() -> animate().scaleX(1f).scaleY(1f).setDuration(240).setInterpolator(new OvershootInterpolator(2f)).start()).start();
        }
        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), rad = h / 2f;
            if (k != 1f) c.scale(k, k, w / 2f, h / 2f);
            r.set(0, 0, w, h);
            int base;
            if (th.superDark) base = 0xFF121212;
            else if (th.blur) base = k != 1f ? alpha(th.tile, th.dark ? 0xE0 : 0xEB) : (th.dark ? 0x24FFFFFF : 0x99FFFFFF);
            else base = th.tile;
            fill.setColor(blend(base, th.accent, act));
            c.drawRoundRect(r, rad, rad, fill);
            if (th.blur && act < 1f) {
                sheen.setShader(new LinearGradient(0, 0, 0, h, new int[]{th.dark ? 0x26FFFFFF : 0x66FFFFFF, 0x00FFFFFF}, new float[]{0f, 0.55f}, Shader.TileMode.CLAMP));
                sheen.setAlpha((int) (255 * (1f - act)));
                c.drawRoundRect(r, rad, rad, sheen);
            }
            if (th.superDark ? th.rims : (th.blur || !th.dark)) {
                RectF in = new RectF(r); in.inset(rim.getStrokeWidth() / 2f, rim.getStrokeWidth() / 2f);
                int[] cols = th.superDark ? new int[]{0x30FFFFFF, 0x12FFFFFF, 0x0CFFFFFF, 0x16FFFFFF}
                        : th.dark ? new int[]{0x40FFFFFF, 0x14FFFFFF, 0x0AFFFFFF, 0x1AFFFFFF}
                        : th.blur ? new int[]{0xCCFFFFFF, 0x66FFFFFF, 0x14000000, 0x22000000}
                        : new int[]{0x14000000, 0x0F000000, 0x0F000000, 0x1A000000};
                rim.setShader(new LinearGradient(0, 0, 0, h, cols, new float[]{0f, 0.35f, 0.75f, 1f}, Shader.TileMode.CLAMP));
                rim.setAlpha((int) (255 * (1f - act * 0.6f)));
                c.drawRoundRect(in, rad - rim.getStrokeWidth() / 2f, rad - rim.getStrokeWidth() / 2f, rim);
            }
            int col = blend(th.text, th.onAccent, act);
            ink.setColor(col);
            if (kind != 0) {
                ink.setStrokeWidth(2.2f * d); ink.setStrokeCap(Paint.Cap.ROUND);
                float half = Math.min(w, h) * 0.22f, mx = w / 2f, my = h / 2f;
                c.drawLine(mx - half, my, mx + half, my, ink);
                if (kind == 2) c.drawLine(mx, my - half, mx, my + half, ink);
                return;
            }
            float dx = 12 * d + 2 * d, dr = 1.9f * d, gap = 5.2f * d, cy = h / 2f;
            for (int i = -1; i <= 1; i++) c.drawCircle(dx, cy + i * gap, dr, ink);
            float tx = dx + 2 * d + 8 * d, ty = cy - (ink.descent() + ink.ascent()) / 2f;
            ink.setAlpha((int) (255 * (1f - act))); c.drawText("Edit", tx, ty, ink);
            ink.setAlpha((int) (255 * act)); c.drawText("Done", tx, ty, ink);
            ink.setAlpha(255);
        }
    }

    private static final class Th {
        boolean dark, superDark, blur, rims = true;
        int card, menu, tile, folder, text, text2, accent, onAccent, ripple;
    }

    private static int sys(Context c, String name, int fallback) {
        try { int id = c.getResources().getIdentifier(name, "color", "android"); return id == 0 ? fallback : c.getColor(id); } catch (Throwable t) { return fallback; }
    }

    private static int blend(int a, int b, float f) {
        f = Math.max(0f, Math.min(1f, f));
        return Color.argb((int) (Color.alpha(a) + (Color.alpha(b) - Color.alpha(a)) * f), (int) (Color.red(a) + (Color.red(b) - Color.red(a)) * f),
                (int) (Color.green(a) + (Color.green(b) - Color.green(a)) * f), (int) (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * f));
    }

    private static int alpha(int c, int a) { return (c & 0x00FFFFFF) | (a << 24); }

    private static Th sTh;
    static Th theme(Context c) {
        Th cached = sTh;
        if (cached != null) return cached;
        Th t = new Th();
        t.dark = FrostedSurface.dark(c);
        t.superDark = t.dark && SuperDark.inEffect(c);
        int style = SurfaceStyle.get(c);
        t.blur = !t.superDark && style == SurfaceStyle.FROSTED;
        t.rims = style != SurfaceStyle.OFF;
        if (t.dark) {
            int n9 = sys(c, "system_neutral1_900", 0xFF1B1B1F), n8 = sys(c, "system_neutral1_800", 0xFF303034);
            t.card = blend(n9, n8, 0.45f);
            t.menu = blend(n9, n8, 0.6f);
            t.tile = sys(c, "system_neutral2_800", 0xFF2F3033);
            t.folder = alpha(sys(c, "system_neutral2_700", 0xFF46464F), 0xE6);
            t.text = sys(c, "system_neutral1_50", 0xFFF1F0F4);
            t.text2 = sys(c, "system_neutral2_200", 0xFFC6C5D0);
            t.accent = sys(c, "system_accent1_200", 0xFFB9C3FF);
            t.onAccent = sys(c, "system_accent1_800", 0xFF22307A);
            t.ripple = 0x33FFFFFF;
            if (style == SurfaceStyle.OFF && !t.superDark) { t.card = 0xFF121212; t.menu = 0xFF1A1A1A; }
        } else {
            t.card = style == SurfaceStyle.OFF ? 0xFFFFFFFF : sys(c, "system_neutral1_50", 0xFFF2F0F4);
            t.menu = sys(c, "system_neutral1_10", 0xFFFEFBFF);
            t.tile = sys(c, "system_neutral2_100", 0xFFE3E1EC);
            t.folder = alpha(sys(c, "system_neutral2_100", 0xFFE3E1EC), 0xF0);
            t.text = sys(c, "system_neutral1_900", 0xFF1B1B1F);
            t.text2 = sys(c, "system_neutral2_700", 0xFF46464F);
            t.accent = sys(c, "system_accent1_600", 0xFF4355B9);
            t.onAccent = 0xFFFFFFFF;
            t.ripple = 0x1F000000;
        }
        if (t.superDark) { t.card = 0xFF0E0E0E; t.menu = 0xFF0A0A0A; t.tile = 0xFF161616; t.folder = 0xFF161616; t.ripple = 0x33FFFFFF; }
        if (t.blur) { t.card = alpha(t.card, t.dark ? 0xD9 : 0xE6); t.menu = alpha(t.menu, t.dark ? 0xE6 : 0xF0); }
        sTh = t;
        return t;
    }

    private static final class Surface extends Drawable {
        final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), rimP = new Paint(Paint.ANTI_ALIAS_FLAG);
        final float radius; final boolean rim; final float rimW; final RectF r = new RectF();
        GlassRim glass;
        Surface(Th th, float radius, int color) { this(th, radius, color, th.superDark && th.rims); if (th.blur && !th.superDark) glass = new GlassRim(radius, 1.5f * Resources.getSystem().getDisplayMetrics().density, th.dark); }
        Surface(Th th, float radius, int color, boolean rim) {
            this.radius = radius; this.rim = rim;
            fill.setColor(color);
            rimW = 2.4f * Resources.getSystem().getDisplayMetrics().density;
            rimP.setStyle(Paint.Style.STROKE); rimP.setStrokeWidth(rimW);
            try { rimP.setMaskFilter(new android.graphics.BlurMaskFilter(Resources.getSystem().getDisplayMetrics().density * 1.2f, android.graphics.BlurMaskFilter.Blur.NORMAL)); } catch (Throwable ignored) { }
        }
        @Override public void draw(Canvas c) {
            Rect b = getBounds();
            r.set(b);
            float rad = Math.min(radius, Math.min(r.width(), r.height()) / 2f);
            if (Color.alpha(fill.getColor()) > 0) c.drawRoundRect(r, rad, rad, fill);
            if (rim) {
                rimP.setShader(new LinearGradient(0, b.top, 0, b.bottom, new int[]{0x30FFFFFF, 0x12FFFFFF, 0x0CFFFFFF, 0x16FFFFFF}, new float[]{0f, 0.35f, 0.75f, 1f}, Shader.TileMode.CLAMP));
                RectF in = new RectF(r); in.inset(rimW / 2f, rimW / 2f);
                c.drawRoundRect(in, Math.max(0, rad - rimW / 2f), Math.max(0, rad - rimW / 2f), rimP);
            }
            if (glass != null) glass.draw(c, r.left, r.top, r.right, r.bottom, rad, Color.alpha(fill.getColor()) > 0 ? 255 : 0);
        }
        @Override public void setAlpha(int a) { fill.setAlpha(a); }
        @Override public void setColorFilter(ColorFilter f) { fill.setColorFilter(f); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    private static final class Glyph extends Drawable {
        static final int EYE = 0, EYE_OFF = 1, PIN = 2;
        final int kind; final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); final float density;
        Glyph(int kind, int color, Resources res) { this.kind = kind; p.setColor(color); density = res.getDisplayMetrics().density; }
        @Override public void draw(Canvas c) {
            Rect b = getBounds();
            float s = Math.min(b.width(), b.height()) / 24f;
            c.save(); c.translate(b.exactCenterX() - 12 * s, b.exactCenterY() - 12 * s); c.scale(s, s);
            if (kind == PIN) {
                p.setStyle(Paint.Style.FILL);
                Path path = new Path();
                path.addRoundRect(new RectF(7, 2, 17, 4), 1, 1, Path.Direction.CW);
                path.moveTo(8, 3.5f); path.lineTo(16, 3.5f); path.lineTo(16, 9); path.cubicTo(16, 10.66f, 17.34f, 12, 19, 12);
                path.lineTo(19, 14); path.lineTo(5, 14); path.lineTo(5, 12); path.cubicTo(6.66f, 12, 8, 10.66f, 8, 9); path.close();
                path.moveTo(11, 14); path.lineTo(13, 14); path.lineTo(13, 21); path.lineTo(12, 22); path.lineTo(11, 21); path.close();
                c.drawPath(path, p);
            } else {
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2f); p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeJoin(Paint.Join.ROUND);
                Path eye = new Path();
                eye.moveTo(2, 12); eye.quadTo(12, 2.5f, 22, 12); eye.quadTo(12, 21.5f, 2, 12); eye.close();
                c.drawPath(eye, p);
                c.drawCircle(12, 12, 3.2f, p);
                if (kind == EYE_OFF) c.drawLine(4, 4, 20, 20, p);
            }
            c.restore();
        }
        @Override public int getIntrinsicWidth() { return Math.round(24 * density); }
        @Override public int getIntrinsicHeight() { return Math.round(24 * density); }
        @Override public void setAlpha(int a) { p.setAlpha(a); }
        @Override public void setColorFilter(ColorFilter f) { p.setColorFilter(f); }
        @Override public void setTintList(ColorStateList t) { if (t != null) p.setColor(t.getDefaultColor()); invalidateSelf(); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
