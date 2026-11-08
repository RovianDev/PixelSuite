package io.github.pixelsuite;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

final class RestartPill {
    static final String LAUNCHER = "com.google.android.apps.nexuslauncher";
    static final String FILES = "com.google.android.apps.nbu.files";
    private static final Map<String, String> pending = new LinkedHashMap<String, String>();
    private static boolean wasShown;
    private static final ExecutorService IO = new ThreadPoolExecutor(0, 1, 10L, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
    private static final PathInterpolator EASE = new PathInterpolator(.2f, 0f, 0f, 1f);
    static final String[] REBOOT_FEATURES = {"Hold power button", "Hold power button when locked", "Double-press power button",
            "Double-press volume down", "Auto-confirm 4-digit PIN", "Always-on display", "Backup & Restore", "Search in Chrome", "Super Dark-Mode"};
    private static boolean sRebootHidden;
    private static final java.util.WeakHashMap<Activity, Boolean> sActionPages = new java.util.WeakHashMap<Activity, Boolean>();
    static boolean needsReboot(android.content.Context c) {
        Boolean behind = SystemRev.behind(c);
        if (behind != null) return behind;
        try {
            long updated = c.getPackageManager().getPackageInfo(c.getPackageName(), 0).lastUpdateTime;
            long booted = System.currentTimeMillis() - android.os.SystemClock.elapsedRealtime();
            return updated > booted + 5000;
        } catch (Throwable t) { return false; }
    }
    private boolean reboot, armed;
    private WhyCard why;
    private AppFrost whyFrost;
    private android.view.ViewTreeObserver.OnScrollChangedListener whyScroll;
    private android.view.View whyRoot, whyPage;
    private PanelBack whyBack;
    private int whyPageY;

    private final Activity activity;
    private final LinearLayout pill;
    private final TextView label;
    private final RestartStatusIcon status;
    private final ImageView icon;
    private final boolean combo;
    private LinearLayout row;
    private TextView note, second;
    private Runnable secondAction;
    private CharSequence secondText;
    private boolean secondEnabled = true;
    private boolean secondShown() { return combo && secondText != null; }
    private final AppFrost pillFrost,noticeFrost;
    private TextView notice;
    private final Runnable clearNotice=new Runnable(){public void run(){dismissNotice();}};
    private void restoreRestart(){if(pill.getVisibility()==View.INVISIBLE)pill.setVisibility(View.VISIBLE);refresh();}
    private void stopNoticeAnimation(){notice.animate().withEndAction(null);notice.animate().cancel();}
    private void dismissNotice(){
        main.removeCallbacks(clearNotice);ToastMotion.unregister(notice);if(notice.getVisibility()!=View.VISIBLE)return;
        stopNoticeAnimation();
        if(!status.motion()||!notice.hasWindowFocus()){notice.setVisibility(View.GONE);restoreRestart();return;}
        notice.animate().alpha(0f).translationY(dp(activity.getResources().getDisplayMetrics().density,10)).scaleX(.84f).scaleY(.84f).setDuration(240).setInterpolator(new android.view.animation.AccelerateInterpolator(1.2f)).withLayer()
                .withEndAction(new Runnable(){public void run(){notice.setVisibility(View.GONE);notice.setTranslationY(0);notice.setScaleX(1);notice.setScaleY(1);restoreRestart();}}).start();
    }
    private boolean busy;
    private boolean showingDone;
    private Runnable applyAction;
    private boolean applyDirty;
    private final Runnable finishDone = new Runnable() { public void run() {
        showingDone=false;
        if(applyAction!=null){if(applyDirty||secondShown())refresh();else hide(true);return;}
        synchronized(pending){if(pending.isEmpty()){hide(true);return;}}
        refresh();
    }};
    private final Handler main = new Handler(Looper.getMainLooper());

    static String scopeOf(String key) {
        if (key == null) return null;
        if (key.equals(LauncherFeatures.GRID) || key.equals(LauncherFeatures.GRID_SIZE) || key.equals(LauncherFeatures.DRAWER)
                || key.equals(LauncherFeatures.DRAWER_GRID_SIZE) || key.equals(LauncherFeatures.ICONS)
                || key.equals(LauncherFeatures.HIDE_SHORTCUTS) || key.equals(LauncherFeatures.LABEL_SIZE)
                || key.equals(LauncherFeatures.LABEL_LEVEL) || key.equals(LauncherFeatures.RENAME)
                || key.equals(Feature.CLEAR_ALL) || key.equals(ClearAll.KEY_ONLY)
                || key.equals(LauncherFeatures.HIDE_SEARCH)) return LAUNCHER;
        return null;
    }

    static void changed(String key) {
        String pkg = scopeOf(key); if (pkg == null) return;
        synchronized (pending) { pending.put(pkg, LAUNCHER.equals(pkg) ? "Pixel Launcher" : "Files"); }
    }

    RestartPill(Activity a, FrameLayout root, int accent, int onAccent) { this(a, root, accent, onAccent, false); }

    RestartPill(Activity a, FrameLayout root, int accent, int onAccent, boolean comboPill) {
        activity = a; combo = comboPill;
        if (comboPill) sActionPages.put(a, Boolean.TRUE);
        View backdrop=root.getChildAt(0);if(backdrop instanceof SoftScrollEdges)backdrop=((ViewGroup)backdrop).getChildAt(0);
        float d = a.getResources().getDisplayMetrics().density;
        pill = new LinearLayout(a); pill.setOrientation(LinearLayout.HORIZONTAL); pill.setGravity(Gravity.CENTER);
        pill.setPadding(dp(d, 20), 0, dp(d, 20), 0); pill.setMinimumHeight(dp(d, 54));
        pillFrost=AppFrost.pill(pill,backdrop,dp(d,27),backgroundColor());pill.setBackground(pillFrost);
        pill.setElevation(dp(d, 6));
        status = new RestartStatusIcon(a,iconColor());
        icon = new ImageView(a){
            @Override protected void onWindowVisibilityChanged(int v){super.onWindowVisibilityChanged(v);status.setActive(v==View.VISIBLE&&hasWindowFocus());}
            @Override public void onWindowFocusChanged(boolean focus){super.onWindowFocusChanged(focus);status.setActive(focus&&getWindowVisibility()==View.VISIBLE);}
            @Override protected void onDetachedFromWindow(){status.setActive(false);super.onDetachedFromWindow();}
        };icon.setImageDrawable(status);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(d, 22), dp(d, 22)); ip.rightMargin = dp(d, 10);
        ViewGroup host = pill;
        if (combo) {
            pill.setOrientation(LinearLayout.VERTICAL);
            note = new TextView(a); note.setTextColor(iconColor()); note.setTextSize(13); note.setGravity(Gravity.CENTER); note.setIncludeFontPadding(false); note.setSingleLine(true); I18n.fitLine(note);
            note.setPadding(dp(d, 4), dp(d, 2), dp(d, 4), dp(d, 10)); note.setVisibility(View.GONE);
            pill.addView(note, new LinearLayout.LayoutParams(-2, -2) {{ gravity = Gravity.CENTER_HORIZONTAL; }});
            row = new LinearLayout(a); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER);
            second = new TextView(a); second.setTextSize(15); second.setGravity(Gravity.CENTER); second.setSingleLine(true); I18n.fitLine(second); second.setIncludeFontPadding(false);
            second.setTypeface(I18n.medium());
            second.setPadding(dp(d, 16), dp(d, 10), dp(d, 16), dp(d, 10)); second.setMinHeight(dp(d, 40));
            second.setBackground(new BackupRim(a, false, 20)); second.setLayerType(View.LAYER_TYPE_SOFTWARE, null); second.setTextColor(textColor()); second.setClickable(true); second.setFocusable(true); second.setVisibility(View.GONE);
            second.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { if (secondEnabled && secondAction != null) secondAction.run(); } });
            LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-2, -2); sp.rightMargin = dp(d, 14);
            row.addView(second, sp);
            pill.addView(row, new LinearLayout.LayoutParams(-2, -2) {{ gravity = Gravity.CENTER_HORIZONTAL; }});
            host = row;
        }
        host.addView(icon, ip);
        label = new TextView(a); label.setTextColor(textColor()); label.setTextSize(16); label.setGravity(Gravity.CENTER); label.setIncludeFontPadding(false); label.setSingleLine(true); I18n.fitLine(label);
        label.setTypeface(I18n.medium());
        host.addView(label);
        pill.setClickable(true); pill.setFocusable(true);
        pill.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { restart(); } });
        pill.setOnLongClickListener(new View.OnLongClickListener() { public boolean onLongClick(View v) { if (reboot) { sRebootHidden = true; armed = false; dismissWhy(); refresh(); return true; } synchronized (pending) { pending.clear(); } hide(true); return true; } });
        final FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        final int margin = dp(d, 16); lp.setMargins(margin, margin, margin, margin);
        root.addView(pill, lp);
        notice=new TextView(a);notice.setTextColor(textColor());notice.setTextSize(15);notice.setGravity(Gravity.CENTER);
        notice.setPadding(dp(d,18),dp(d,12),dp(d,18),dp(d,12));notice.setMinimumHeight(dp(d,54));
        notice.setMaxLines(2);notice.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        noticeFrost=AppFrost.pill(notice,backdrop,dp(d,27),backgroundColor());notice.setBackground(noticeFrost);notice.setElevation(dp(d,6));
        FrameLayout.LayoutParams np=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);np.setMargins(dp(d,24),dp(d,16),dp(d,24),dp(d,16));root.addView(notice,np);notice.setVisibility(View.GONE);
        notice.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener(){public WindowInsets onApplyWindowInsets(View v,WindowInsets in){np.bottomMargin=dp(d,84)+in.getSystemWindowInsetBottom();np.leftMargin=dp(d,24)+in.getSystemWindowInsetLeft();np.rightMargin=dp(d,24)+in.getSystemWindowInsetRight();v.setLayoutParams(np);return in;}});
        pill.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @SuppressWarnings("deprecation")
            public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                lp.bottomMargin = dp(d,84) + insets.getSystemWindowInsetBottom(); lp.leftMargin=margin+insets.getSystemWindowInsetLeft(); lp.rightMargin = margin + insets.getSystemWindowInsetRight();
                v.setLayoutParams(lp); return insets;
            }
        });
        pill.setVisibility(View.GONE);
        why = new WhyCard(a); why.setPadding(dp(d, 18), dp(d, 12), dp(d, 18), dp(d, 14)); why.setElevation(dp(d, 6));
        why.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        whyFrost = AppFrost.pill(why, backdrop, dp(d, 22), backgroundColor()); whyFrost.setCallback(why);
        root.addView(why, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL)); why.setVisibility(View.GONE);
        whyRoot = root; whyPage = backdrop;
        whyBack = new PanelBack(activity, new Runnable() { public void run() { dismissWhy(); } });
        whyScroll = new android.view.ViewTreeObserver.OnScrollChangedListener() { public void onScrollChanged() {
            if (!why.open || whyPage == null || android.os.SystemClock.uptimeMillis() - why.openedAt < 300) return;
            if (Math.abs(whyPage.getScrollY() - whyPageY) > dp(activity.getResources().getDisplayMetrics().density, 8)) dismissWhy();
        } };
        root.getViewTreeObserver().addOnScrollChangedListener(whyScroll);
    }

    private void showWhy() {
        float d = activity.getResources().getDisplayMetrics().density;
        why.removeAllViews();
        TextView head = new TextView(activity); head.setTextSize(14); head.setTypeface(I18n.medium()); head.setText(I18n.t("These run inside Android, so they update after a reboot:"));
        why.addView(head, new LinearLayout.LayoutParams(-1, -2));
        for (String f : REBOOT_FEATURES) {
            TextView line = new TextView(activity); line.setTextSize(14); line.setText("• " + I18n.t(f));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.topMargin = dp(d, 4); why.addView(line, lp);
        }
        for (int i = 0; i < why.getChildCount(); i++) ((TextView) why.getChildAt(i)).setTextColor(textColor());
        FrameLayout.LayoutParams wp = (FrameLayout.LayoutParams) why.getLayoutParams();
        FrameLayout.LayoutParams pp = (FrameLayout.LayoutParams) pill.getLayoutParams();
        wp.bottomMargin = pp.bottomMargin + Math.max(pill.getHeight(), dp(d, 54)) + dp(d, 10); wp.leftMargin = dp(d, 24); wp.rightMargin = dp(d, 24);
        why.setLayoutParams(wp);
        whyPageY = whyPage != null ? whyPage.getScrollY() : 0;
        why.expand(Math.max(pill.getWidth(), dp(d, 160)), Math.max(pill.getHeight(), dp(d, 54)), status.motion());
        if (whyBack != null) whyBack.on();
    }
    private void dismissWhy() {
        if (why == null || !why.open) return;
        why.collapse(status.motion());
        if (armed && !busy) { armed = false; refresh(); }
    }
    private void hideWhy() { if (why != null && why.open) why.collapse(false); }
    boolean whyOpen() { return why != null && why.open; }
    boolean whyOwns(MotionEvent e) { return under(why, e) || under(pill, e); }
    void closeWhy() { dismissWhy(); }
    boolean back() { if (!whyOpen()) return false; dismissWhy(); return true; }
    private static boolean under(View v, MotionEvent e) {
        if (v == null || v.getVisibility() != View.VISIBLE) return false;
        int[] at = new int[2]; v.getLocationOnScreen(at);
        float x = e.getRawX() - at[0], y = e.getRawY() - at[1];
        return x >= 0 && y >= 0 && x < v.getWidth() && y < v.getHeight();
    }

    private final class WhyCard extends LinearLayout implements android.view.Choreographer.FrameCallback {
        boolean open; private boolean ticking; private long last; long openedAt;
        private float p, pv, q, qv, fromW, fromH, downX, downY; private boolean moved, edge;
        private final android.graphics.RectF now = new android.graphics.RectF(); private final android.graphics.Path clip = new android.graphics.Path();
        private float radius;
        WhyCard(android.content.Context c) {
            super(c); setOrientation(VERTICAL); setWillNotDraw(false); setClickable(true);
            setOutlineProvider(new android.view.ViewOutlineProvider() { public void getOutline(View v, android.graphics.Outline o) {
                o.setRoundRect(Math.round(now.left), Math.round(now.top), Math.round(now.right), Math.round(now.bottom), radius); } });
        }
        private float dpx(float v) { return v * getResources().getDisplayMetrics().density; }
        void expand(float w, float h, boolean motion) {
            fromW = w; fromH = h; open = true; openedAt = android.os.SystemClock.uptimeMillis();
            setAlpha(1f); setTranslationY(0); setVisibility(View.VISIBLE);
            if (!motion) { p = q = 1; pv = qv = 0; invalidate(); return; }
            p = q = 0; pv = qv = 0; tick();
        }
        void collapse(boolean motion) {
            open = false; if (whyBack != null) whyBack.off();
            if (!motion) { p = q = 0; setVisibility(View.GONE); setAlpha(1f); setTranslationY(0); return; }
            tick();
        }
        private void tick() { if (ticking) return; ticking = true; last = 0; android.view.Choreographer.getInstance().postFrameCallback(this); }
        private float[] spring(float x, float v, float to, float k, float zeta, float dt) {
            float c = 2 * zeta * (float) Math.sqrt(k); int steps = Math.max(1, Math.round(dt / .004f)); float hh = dt / steps;
            for (int i = 0; i < steps; i++) { float a = -k * (x - to) - c * v; v += a * hh; x += v * hh; }
            return new float[]{x, v};
        }
        @Override public void doFrame(long nanos) {
            float dt = last == 0 ? 1 / 60f : Math.min(.05f, (nanos - last) / 1e9f); last = nanos;
            float to = open ? 1 : 0; float[] r;
            long t = android.os.SystemClock.uptimeMillis();
            if (open) { r = spring(p, pv, to, 380, .62f, dt); p = r[0]; pv = r[1]; if (t - openedAt > 45) { r = spring(q, qv, to, 300, .66f, dt); q = r[0]; qv = r[1]; } }
            else { r = spring(q, qv, to, 520, .92f, dt); q = r[0]; qv = r[1]; r = spring(p, pv, to, 460, .78f, dt); p = r[0]; pv = r[1]; }
            if (!open) {
                float sink = Math.max(0, Math.min(1, 1 - p));
                setTranslationY(sink * (fromH + dpx(10)));
                float a = Math.max(0, Math.min(1, (p - .04f) / .42f)); setAlpha(a * a * (3 - 2 * a));
                if (p < .04f && q < .08f) { p = q = 0; pv = qv = 0; ticking = false; setVisibility(View.GONE); setAlpha(1f); setTranslationY(0); return; }
            }
            invalidate(); invalidateOutline();
            boolean settled = Math.abs(p - to) < .001f && Math.abs(pv) < .005f && Math.abs(q - to) < .001f && Math.abs(qv) < .005f && (!open || t - openedAt > 700);
            if (settled) { p = q = to; ticking = false; if (!open) { setVisibility(View.GONE); setAlpha(1f); setTranslationY(0); } invalidate(); invalidateOutline(); return; }
            android.view.Choreographer.getInstance().postFrameCallback(this);
        }
        @Override protected boolean verifyDrawable(android.graphics.drawable.Drawable dr) { return dr == whyFrost || super.verifyDrawable(dr); }
        @Override protected void dispatchDraw(android.graphics.Canvas c) {
            float w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0) return;
            float cw = fromW + (w - fromW) * p, ch = fromH + (h - fromH) * q;
            cw = Math.max(fromW * .6f, cw); ch = Math.max(fromH * .6f, ch);
            now.set((w - cw) / 2f, h - ch, (w + cw) / 2f, h);
            float rp = Math.max(0, Math.min(1, p * 1.2f));
            radius = fromH / 2f + (dpx(22) - fromH / 2f) * rp;
            whyFrost.radius(radius); whyFrost.setBounds(Math.round(now.left), Math.round(now.top), Math.round(now.right), Math.round(now.bottom)); whyFrost.draw(c);
            long t = android.os.SystemClock.uptimeMillis() - openedAt;
            float out = Math.max(0, Math.min(1, q * 1.6f - .35f));
            for (int i = 0; i < getChildCount(); i++) {
                View k = getChildAt(i);
                float e = open ? ease((t - 110 - i * 26) / 260f) : out;
                k.setAlpha(e); k.setTranslationY((1 - e) * dpx(10));
            }
            if (open && t < 110 + getChildCount() * 26 + 300) postInvalidateOnAnimation();
            int save = c.save(); clip.reset(); clip.addRoundRect(now, radius, radius, android.graphics.Path.Direction.CW); c.clipPath(clip);
            super.dispatchDraw(c); c.restoreToCount(save);
        }
        private float ease(float x) { x = Math.max(0, Math.min(1, x)); return 1 - (1 - x) * (1 - x) * (1 - x); }
        @Override public boolean onTouchEvent(MotionEvent e) {
            if (!open) return false;
            float slop = android.view.ViewConfiguration.get(getContext()).getScaledTouchSlop();
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: downX = e.getX(); downY = e.getY(); moved = false; edge = PanelBack.atBackEdge(this, e); return true;
                case MotionEvent.ACTION_MOVE: if (!moved && !edge && Math.hypot(e.getX() - downX, e.getY() - downY) > slop) { moved = true; dismissWhy(); } return true;
                case MotionEvent.ACTION_UP: if (!moved) dismissWhy(); return true;
            }
            return true;
        }
    }

    void showRestriction(){
        refreshPalette();
        if(activity.isFinishing()||activity.isDestroyed())return;
        main.removeCallbacks(clearNotice);
        android.text.SpannableString message=new android.text.SpannableString(I18n.t("Super Dark-Mode uses its own black style.\nTurn it off to change styles."));
        int boldAt=Math.max(0,message.toString().indexOf("Super Dark-Mode"));message.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD),boldAt,Math.min(message.length(),boldAt+"Super Dark-Mode".length()),android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);notice.setText(message);
        if(pill.getVisibility()==View.VISIBLE)pill.setVisibility(View.INVISIBLE);
        boolean already=notice.getVisibility()==View.VISIBLE;
        stopNoticeAnimation();notice.setAlpha(1f);notice.setVisibility(View.VISIBLE);notice.requestApplyInsets();notice.setScaleX(1);notice.setScaleY(1);notice.setTranslationY(0);
        if(!already&&status.motion()){
            notice.setAlpha(0f);notice.setTranslationY(dp(activity.getResources().getDisplayMetrics().density,12));notice.setScaleX(.84f);notice.setScaleY(.84f);
            notice.animate().alpha(1f).translationY(0).scaleX(1).scaleY(1).setDuration(340).setInterpolator(new android.view.animation.OvershootInterpolator(.55f)).withLayer().start();
        }
        ToastMotion.watchScroll(activity.getWindow().getDecorView());ToastMotion.register(notice,clearNotice);
        main.postDelayed(clearNotice,5000);
    }
    void dispose(){if(whyBack!=null)whyBack.off();pillFrost.release();noticeFrost.release();if(whyFrost!=null)whyFrost.release();if(whyRoot!=null&&whyScroll!=null&&whyRoot.getViewTreeObserver().isAlive())whyRoot.getViewTreeObserver().removeOnScrollChangedListener(whyScroll);ToastMotion.unregister(notice);stopNoticeAnimation();main.removeCallbacks(clearNotice);main.removeCallbacks(finishDone);status.setActive(false);}

    private int backgroundColor(){boolean dark=FrostedSurface.dark(activity);return AppSurfaces.active(activity)?0xff000000:LauncherFeatures.surface(activity,dark?"system_surface_container_dark":"system_surface_container_light",dark?0xff211f26:0xfff3edf7);}
    private int textColor(){boolean dark=FrostedSurface.dark(activity);return LauncherFeatures.surface(activity,dark?"system_on_surface_dark":"system_on_surface_light",dark?0xffe6e1e5:0xff1d1b20);}
    private int iconColor(){boolean dark=FrostedSurface.dark(activity);return LauncherFeatures.surface(activity,dark?"system_on_surface_variant_dark":"system_on_surface_variant_light",dark?0xffcac4d0:0xff49454f);}
    void refreshPalette(){if(why!=null){for(int i=0;i<why.getChildCount();i++)((TextView)why.getChildAt(i)).setTextColor(textColor());whyFrost.pageColor(backgroundColor());why.invalidate();}label.setTextColor(textColor());if(note!=null)note.setTextColor(iconColor());if(second!=null){second.setTextColor(textColor());second.invalidate();}notice.setTextColor(textColor());status.setColor(iconColor());pillFrost.pageColor(backgroundColor());noticeFrost.pageColor(backgroundColor());pill.invalidate();notice.invalidate();}

    void refresh() {
        refreshPalette();
        if (busy) return;
        if(showingDone)return;
        String text;
        reboot = applyAction == null && !combo && !sRebootHidden && !sActionPages.containsKey(activity) && needsReboot(activity);
        if (reboot) text = armed ? "Tap again to reboot" : "Reboot";
        else if(applyAction!=null){if(!applyDirty&&!secondShown()){hide(true);return;}text="Apply";}
        else synchronized (pending) {
            if (pending.isEmpty()) { hide(false); return; }
            text = pending.size() == 1 ? "Refresh " + pending.values().iterator().next() : "Refresh " + pending.size() + " apps";
        }
        main.removeCallbacks(finishDone);
        pill.animate().withEndAction(null);pill.animate().cancel();pill.setAlpha(1f);pill.setScaleX(1f);pill.setScaleY(1f);pill.setTranslationY(0);label.animate().cancel();label.setAlpha(1f);label.setTranslationY(0);
        ViewGroup.LayoutParams width=pill.getLayoutParams();width.width=ViewGroup.LayoutParams.WRAP_CONTENT;pill.setLayoutParams(width);
        LinearLayout.LayoutParams textSize=(LinearLayout.LayoutParams)label.getLayoutParams();textSize.width=ViewGroup.LayoutParams.WRAP_CONTENT;textSize.weight=0;label.setLayoutParams(textSize);
        label.setText(I18n.cs(text));status.show(0);
        if(combo)comboViews();
        if(applyAction!=null)pill.setMinimumWidth(dp(activity.getResources().getDisplayMetrics().density,168));
        pill.setContentDescription(I18n.cs(reboot?"Reboot to finish updating. Long-press to hide.":applyAction==null?text + ". Applies your changes. Long-press to hide.":"Apply update blocking choices"));
        if(notice.getVisibility()==View.VISIBLE){pill.setVisibility(View.INVISIBLE);return;}
        if (pill.getVisibility() == View.VISIBLE) return;
        pill.setVisibility(View.VISIBLE);
        if (wasShown) { pill.setAlpha(1f); pill.setTranslationY(0f); pill.setScaleX(1f); pill.setScaleY(1f); return; }
        wasShown = true;
        pill.setAlpha(0f); pill.setTranslationY(dp(activity.getResources().getDisplayMetrics().density, 12)); pill.setScaleX(.84f); pill.setScaleY(.84f);
        pill.animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f).setDuration(340).setInterpolator(new android.view.animation.OvershootInterpolator(.55f)).withLayer().start();
    }

    private void hide(boolean animate) {
        wasShown = false;
        main.removeCallbacks(finishDone);
        if (pill.getVisibility() != View.VISIBLE) return;
        if (!animate) { pill.setVisibility(View.GONE);status.setActive(false); return; }
        pill.animate().withEndAction(null);pill.animate().cancel();
        pill.animate().alpha(0f).translationY(dp(activity.getResources().getDisplayMetrics().density, 10)).scaleX(.84f).scaleY(.84f).setDuration(240).setInterpolator(new android.view.animation.AccelerateInterpolator(1.2f)).withLayer()
                .withEndAction(new Runnable() { public void run() { pill.setVisibility(View.GONE);status.setActive(false); } }).start();
    }

    private void restart() {
        if (busy) return;
        if (reboot) {
            if (!armed) { armed = true; refresh(); showWhy(); return; }
            busy = true;
            ViewGroup.LayoutParams w = pill.getLayoutParams(); w.width = pill.getWidth(); pill.setLayoutParams(w);
            status.setActive(pill.hasWindowFocus()); status.show(1); changeLabel("Rebooting…");
            IO.execute(new Runnable() { public void run() {
                final boolean ok = Root.run("svc power reboot || reboot") != null;
                if (ok) return;
                main.post(new Runnable() { public void run() {
                    busy = false; armed = false; hideWhy();
                    if (activity.isFinishing() || activity.isDestroyed()) return;
                    refresh(); AppToast.makeText(activity, I18n.cs("Couldn't reboot: root access needed"), Toast.LENGTH_SHORT).show();
                }});
            }});
            return;
        }
        if(applyAction!=null){if(applyDirty&&!showingDone)applyAction.run();return;}
        final String command; synchronized (pending) {
            if (pending.isEmpty()) return;
            StringBuilder b = new StringBuilder();
            for (String pkg : pending.keySet()) { if (b.length() > 0) b.append("; "); b.append("am force-stop ").append(pkg); }
            command = b.toString();
        }
        busy = true;showingDone=false;main.removeCallbacks(finishDone);
        ViewGroup.LayoutParams width=pill.getLayoutParams();width.width=pill.getWidth();pill.setLayoutParams(width);
        LinearLayout.LayoutParams textSize=(LinearLayout.LayoutParams)label.getLayoutParams();textSize.width=ViewGroup.LayoutParams.WRAP_CONTENT;textSize.weight=0;label.setLayoutParams(textSize);label.setGravity(Gravity.CENTER);
        status.setActive(pill.hasWindowFocus());status.show(1);changeLabel("Refreshing…");
        final long started=android.os.SystemClock.uptimeMillis();
        IO.execute(new Runnable() { public void run() {
            final boolean ok = Root.run(command) != null;
            main.postDelayed(new Runnable() { public void run() {
                busy = false;
                if (activity.isFinishing() || activity.isDestroyed()) return;
                if (!ok) { refresh(); AppToast.makeText(activity, I18n.cs("Couldn't refresh: root access needed"), Toast.LENGTH_SHORT).show(); return; }
                synchronized (pending) { pending.clear(); }
                showingDone=true;status.show(2);changeLabel("Done");
                main.postDelayed(finishDone,1100);
            }},Math.max(0,650-(android.os.SystemClock.uptimeMillis()-started)));
        }});
    }

    private void comboViews(){
        if(!combo)return;
        boolean two=secondShown(),main=applyDirty||busy||showingDone;
        float d=activity.getResources().getDisplayMetrics().density;
        note.setVisibility(two?View.VISIBLE:View.GONE);
        second.setVisibility(two?View.VISIBLE:View.GONE);
        if(two){second.setText(I18n.cs(secondText));second.setEnabled(secondEnabled);second.setAlpha(secondEnabled?1f:.7f);}
        ((LinearLayout.LayoutParams)second.getLayoutParams()).rightMargin=main?dp(d,14):0;
        icon.setVisibility(main?View.VISIBLE:View.GONE);label.setVisibility(main?View.VISIBLE:View.GONE);
        pill.setPadding(dp(d,two?14:20),two?dp(d,12):0,dp(d,two?18:20),two?dp(d,10):0);
    }
    void setSecondary(Runnable action,String line,String... texts){
        secondAction=action;if(note!=null)note.setText(I18n.cs(line));
        if(second!=null&&texts!=null){float w=0;for(String t:texts)if(t!=null)w=Math.max(w,second.getPaint().measureText(t));second.setMinWidth((int)Math.ceil(w)+second.getPaddingLeft()+second.getPaddingRight()+dp(activity.getResources().getDisplayMetrics().density,2));}
    }
    void showSecondary(CharSequence text,boolean enabled){
        secondText=text;secondEnabled=enabled;
        if(!combo)return;
        if(text!=null)second.setContentDescription(I18n.cs(text));
        comboViews();
        if(!busy&&!showingDone)refresh();
        else if(text!=null&&pill.getVisibility()!=View.VISIBLE)pill.setVisibility(View.VISIBLE);
    }
    void setApplyAction(Runnable action){applyAction=action;applyDirty=false;pill.setOnLongClickListener(null);refresh();}
    void showApply(boolean dirty){applyDirty=dirty;refresh();}
    void applying(){
        busy=true;showingDone=false;main.removeCallbacks(finishDone);
        ViewGroup.LayoutParams size=pill.getLayoutParams();size.width=Math.max(pill.getWidth(),dp(activity.getResources().getDisplayMetrics().density,168));pill.setLayoutParams(size);
        status.setActive(pill.hasWindowFocus());status.show(1);changeLabel("Applying…");
    }
    void applied(){busy=false;applyDirty=false;showingDone=true;status.show(2);changeLabel("Done");main.postDelayed(finishDone,1100);}
    void applyFailed(){busy=false;showingDone=false;refresh();AppToast.makeText(activity,I18n.cs("Couldn't apply: root access needed"),Toast.LENGTH_SHORT).show();}

    private void changeLabel(final String text){
        pill.setContentDescription(I18n.cs(text));
        label.animate().cancel();
        if(!status.motion()){label.setText(I18n.cs(text));label.setAlpha(1);label.setTranslationY(0);return;}
        label.animate().alpha(0).translationY(-3*activity.getResources().getDisplayMetrics().density).setDuration(100).setInterpolator(EASE).withEndAction(new Runnable(){public void run(){
            label.setText(I18n.cs(text));label.setTranslationY(3*activity.getResources().getDisplayMetrics().density);
            label.animate().alpha(1).translationY(0).setDuration(180).setInterpolator(EASE).start();
        }}).start();
    }

    private static int dp(float density, float v) { return Math.round(v * density); }
}
