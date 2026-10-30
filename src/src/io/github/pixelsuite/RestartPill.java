package io.github.pixelsuite;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
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

    private final Activity activity;
    private final LinearLayout pill;
    private final TextView label;
    private final RestartStatusIcon status;
    private final ImageView icon;
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
        if(applyAction!=null){if(applyDirty)refresh();else hide(true);return;}
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
                || key.equals(SuperDark.KEY) || key.equals(SurfaceStyle.KEY)) return LAUNCHER;
        if (key.equals(Feature.FILES_SORT)) return FILES;
        return null;
    }

    static void changed(String key) {
        String pkg = scopeOf(key); if (pkg == null) return;
        synchronized (pending) { pending.put(pkg, LAUNCHER.equals(pkg) ? "Pixel Launcher" : "Files"); }
    }

    RestartPill(Activity a, FrameLayout root, int accent, int onAccent) {
        activity = a;
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
        pill.addView(icon, ip);
        label = new TextView(a); label.setTextColor(textColor()); label.setTextSize(16); label.setGravity(Gravity.CENTER); label.setIncludeFontPadding(false); label.setSingleLine(true);
        label.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        pill.addView(label);
        pill.setClickable(true); pill.setFocusable(true);
        pill.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { restart(); } });
        pill.setOnLongClickListener(new View.OnLongClickListener() { public boolean onLongClick(View v) { synchronized (pending) { pending.clear(); } hide(true); return true; } });
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
    }

    void showRestriction(){
        refreshPalette();
        if(activity.isFinishing()||activity.isDestroyed())return;
        main.removeCallbacks(clearNotice);
        android.text.SpannableString message=new android.text.SpannableString("Super Dark-Mode uses its own black style.\nTurn it off to change styles.");
        message.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD),0,"Super Dark-Mode".length(),android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);notice.setText(message);
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
    void dispose(){pillFrost.release();noticeFrost.release();ToastMotion.unregister(notice);stopNoticeAnimation();main.removeCallbacks(clearNotice);main.removeCallbacks(finishDone);status.setActive(false);}

    private int backgroundColor(){boolean dark=FrostedSurface.dark(activity);return AppSurfaces.active(activity)?0xff000000:LauncherFeatures.surface(activity,dark?"system_surface_container_dark":"system_surface_container_light",dark?0xff211f26:0xfff3edf7);}
    private int textColor(){boolean dark=FrostedSurface.dark(activity);return LauncherFeatures.surface(activity,dark?"system_on_surface_dark":"system_on_surface_light",dark?0xffe6e1e5:0xff1d1b20);}
    private int iconColor(){boolean dark=FrostedSurface.dark(activity);return LauncherFeatures.surface(activity,dark?"system_on_surface_variant_dark":"system_on_surface_variant_light",dark?0xffcac4d0:0xff49454f);}
    void refreshPalette(){label.setTextColor(textColor());notice.setTextColor(textColor());status.setColor(iconColor());pillFrost.pageColor(backgroundColor());noticeFrost.pageColor(backgroundColor());pill.invalidate();notice.invalidate();}

    void refresh() {
        refreshPalette();
        if (busy) return;
        if(showingDone)return;
        String text;
        if(applyAction!=null){if(!applyDirty){hide(true);return;}text="Apply";}
        else synchronized (pending) {
            if (pending.isEmpty()) { hide(false); return; }
            text = pending.size() == 1 ? "Restart " + pending.values().iterator().next() : "Restart " + pending.size() + " apps";
        }
        main.removeCallbacks(finishDone);
        pill.animate().withEndAction(null);pill.animate().cancel();pill.setAlpha(1f);pill.setScaleX(1f);pill.setScaleY(1f);pill.setTranslationY(0);label.animate().cancel();label.setAlpha(1f);label.setTranslationY(0);
        ViewGroup.LayoutParams width=pill.getLayoutParams();width.width=ViewGroup.LayoutParams.WRAP_CONTENT;pill.setLayoutParams(width);
        LinearLayout.LayoutParams textSize=(LinearLayout.LayoutParams)label.getLayoutParams();textSize.width=ViewGroup.LayoutParams.WRAP_CONTENT;textSize.weight=0;label.setLayoutParams(textSize);
        label.setText(text);status.show(0);
        if(applyAction!=null)pill.setMinimumWidth(dp(activity.getResources().getDisplayMetrics().density,168));
        pill.setContentDescription(applyAction==null?text + ". Applies your changes. Long-press to hide.":"Apply update blocking choices");
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
        status.setActive(pill.hasWindowFocus());status.show(1);changeLabel("Restarting…");
        final long started=android.os.SystemClock.uptimeMillis();
        IO.execute(new Runnable() { public void run() {
            final boolean ok = Root.run(command) != null;
            main.postDelayed(new Runnable() { public void run() {
                busy = false;
                if (activity.isFinishing() || activity.isDestroyed()) return;
                if (!ok) { refresh(); AppToast.makeText(activity, "Couldn't restart: root access needed", Toast.LENGTH_SHORT).show(); return; }
                synchronized (pending) { pending.clear(); }
                showingDone=true;status.show(2);changeLabel("Done");
                main.postDelayed(finishDone,1100);
            }},Math.max(0,650-(android.os.SystemClock.uptimeMillis()-started)));
        }});
    }

    void setApplyAction(Runnable action){applyAction=action;applyDirty=false;pill.setOnLongClickListener(null);refresh();}
    void showApply(boolean dirty){applyDirty=dirty;refresh();}
    void applying(){
        busy=true;showingDone=false;main.removeCallbacks(finishDone);
        ViewGroup.LayoutParams size=pill.getLayoutParams();size.width=Math.max(pill.getWidth(),dp(activity.getResources().getDisplayMetrics().density,168));pill.setLayoutParams(size);
        status.setActive(pill.hasWindowFocus());status.show(1);changeLabel("Applying…");
    }
    void applied(){busy=false;applyDirty=false;showingDone=true;status.show(2);changeLabel("Done");main.postDelayed(finishDone,1100);}
    void applyFailed(){busy=false;showingDone=false;refresh();AppToast.makeText(activity,"Couldn't apply: root access needed",Toast.LENGTH_SHORT).show();}

    private void changeLabel(final String text){
        pill.setContentDescription(text);
        label.animate().cancel();
        if(!status.motion()){label.setText(text);label.setAlpha(1);label.setTranslationY(0);return;}
        label.animate().alpha(0).translationY(-3*activity.getResources().getDisplayMetrics().density).setDuration(100).setInterpolator(EASE).withEndAction(new Runnable(){public void run(){
            label.setText(text);label.setTranslationY(3*activity.getResources().getDisplayMetrics().density);
            label.animate().alpha(1).translationY(0).setDuration(180).setInterpolator(EASE).start();
        }}).start();
    }

    private static int dp(float density, float v) { return Math.round(v * density); }
}
