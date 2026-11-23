package io.github.pixelsuite;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.AssetFileDescriptor;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Outline;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.util.LruCache;
import android.view.Choreographer;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.ViewTreeObserver;
import android.view.accessibility.AccessibilityManager;
import android.widget.FrameLayout;
import android.widget.ImageView;

final class FeaturePreview extends FrameLayout implements TextureView.SurfaceTextureListener,
        ViewTreeObserver.OnScrollChangedListener, ViewTreeObserver.OnGlobalLayoutListener {
    private static final float ASPECT = 540f / 1140f;
    private static final float CORNER_RATIO = 59f / 540f;
    private static final long SETTLE_MS = 320;
    private static final long RELEASE_DELAY_MS = 1500;
    private static final long FADE_MS = 200;

    private static Handler worker;
    private static Handler worker() {
        if (worker == null) {
            HandlerThread thread = new HandlerThread("PixelSuite previews", android.os.Process.THREAD_PRIORITY_DISPLAY);
            thread.start();
            worker = new Handler(thread.getLooper());
        }
        return worker;
    }
    private static final LruCache<String, Bitmap> posters = new LruCache<String, Bitmap>(6 * 1024 * 1024) {
        @Override protected int sizeOf(String key, Bitmap value) { return value.getByteCount(); }
    };

    private final Handler main = new Handler(Looper.getMainLooper());
    private final Rect visible = new Rect();
    private final FrameLayout phone;
    private final TextureView texture;
    private final ImageView poster;
    private final PowerManager power;
    private final AccessibilityManager access;

    private String clip;
    private int video, startStill, resultStill;
    private String shownStill;
    private boolean attached, disposed, hostActive, motion = true, failed;
    private long activeSince;
    private boolean wantPlaying;
    private boolean awaitingFrame;
    private boolean releaseScheduled;
    private volatile int generation;

    private MediaPlayer player;
    private int playerClip;
    private Step step;
    private int position;
    private Surface surface;
    private SurfaceTexture surfaceTex, texturePending;

    private final Runnable settleCheck = new Runnable() { public void run() { checkPlayback(); } };
    private final Runnable releaseHidden = new Runnable() {
        public void run() {
            releaseScheduled = false;
            if (!canPlay()) worker().post(new Runnable() { public void run() { releasePlayerW(true); } });
        }
    };
    private final ContentObserver animatorSetting = new ContentObserver(new Handler(Looper.getMainLooper())) {
        @Override public void onChange(boolean selfChange) { updateMotion(); }
    };
    private final BroadcastReceiver powerChange = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { updateMotion(); }
    };
    private final AccessibilityManager.TouchExplorationStateChangeListener explorationChange =
            new AccessibilityManager.TouchExplorationStateChangeListener() {
                public void onTouchExplorationStateChanged(boolean enabled) { updateMotion(); }
            };

    FeaturePreview(Context context, String key) {
        super(context);
        power = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        access = (AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);
        phone = new FrameLayout(context);
        phone.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), view.getWidth() * CORNER_RATIO);
            }
        });
        phone.setClipToOutline(true);
        addView(phone);
        texture = new TextureView(context);
        texture.setOpaque(true);
        texture.setSurfaceTextureListener(this);
        texture.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        phone.addView(texture, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        poster = new ImageView(context);
        poster.setScaleType(ImageView.ScaleType.FIT_XY);
        poster.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        phone.addView(poster, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        setClip(key, false);
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width = View.getDefaultSize(0, widthSpec);
        int height = View.getDefaultSize(0, heightSpec);
        setMeasuredDimension(width, height);
        int phoneWidth = Math.min(width, Math.round(height * ASPECT));
        int phoneHeight = Math.min(height, Math.round(phoneWidth / ASPECT));
        phone.measure(MeasureSpec.makeMeasureSpec(phoneWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(phoneHeight, MeasureSpec.EXACTLY));
    }

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int width = phone.getMeasuredWidth(), height = phone.getMeasuredHeight();
        int x = (getWidth() - width) / 2, y = (getHeight() - height) / 2;
        phone.layout(x, y, x + width, y + height);
    }

    void setClip(String key, boolean fade) {
        if (key.equals(clip)) return;
        clip = key;
        generation++;
        failed = false;
        video = resource("preview_" + key);
        startStill = resource("preview_start_" + key);
        resultStill = resource("preview_still_" + key);
        shownStill = null;
        boolean animate = fade && motion && attached && visibleEnough();
        showStill(animate);
        final int gen = generation;
        if (wantPlaying) {
            wantPlaying = false;
            worker().post(new Runnable() { public void run() { pauseW(); } });
        }
        worker().post(new Runnable() { public void run() { if (gen == generation) { releasePlayerW(false); position = 0; } } });
        checkPlayback();
    }

    void syncWith(FeaturePreview other) {
        if (other.step == null) other.step = new Step();
        step = other.step;
    }

    private static final class Step {
        long epoch;
        final java.util.Set<FeaturePreview> playing = new java.util.HashSet<FeaturePreview>();
    }

    private float[] turnAt, turnDeg;
    private float turnStill;
    private volatile long playEpoch;
    private volatile int playLength;
    private boolean turnerPosted;
    private final Choreographer.FrameCallback turner = new Choreographer.FrameCallback() {
        public void doFrame(long frameTimeNanos) { turnFrame(); }
    };

    void turns(float[] at, float[] degrees, float stillDegrees) {
        turnAt = at; turnDeg = degrees; turnStill = stillDegrees;
    }

    private void playing(MediaPlayer mp, int from) {
        if (turnAt == null) return;
        final long epoch = SystemClock.uptimeMillis() - from;
        final int length = Math.max(1, mp.getDuration());
        main.post(new Runnable() { public void run() { playEpoch = epoch; playLength = length; startTurner(); } });
    }

    private void startTurner() {
        if (turnerPosted || turnAt == null || !attached) return;
        turnerPosted = true;
        Choreographer.getInstance().postFrameCallback(turner);
    }

    private long turnSynced;

    private void turnFrame() {
        turnerPosted = false;
        long epoch = playEpoch; int length = playLength;
        if (turnAt == null || !attached || epoch == 0 || length <= 0) return;
        long now = SystemClock.uptimeMillis();
        applyTurn(angleAt(((now - epoch) % length) / 1000f));
        if (now - turnSynced > 1500) {
            turnSynced = now;
            worker().post(new Runnable() { public void run() {
                try { MediaPlayer mp = player; if (mp != null && mp.isPlaying()) playing(mp, mp.getCurrentPosition()); } catch (Throwable ignored) { }
            } });
        }
        startTurner();
    }

    private float angleAt(float t) {
        if (t <= turnAt[0]) return turnDeg[0];
        for (int i = 1; i < turnAt.length; i++) {
            if (t <= turnAt[i]) {
                float k = (t - turnAt[i - 1]) / Math.max(0.001f, turnAt[i] - turnAt[i - 1]);
                k = k * k * (3f - 2f * k);
                return turnDeg[i - 1] + (turnDeg[i] - turnDeg[i - 1]) * k;
            }
        }
        return turnDeg[turnDeg.length - 1];
    }

    private void applyTurn(float degrees) {
        float k = Math.abs(degrees) / 90f, fit = 1f;
        if (phone.getWidth() > 0 && phone.getHeight() > 0)
            fit = Math.min(1f, Math.min(getWidth() / (float) phone.getHeight(), getHeight() / (float) phone.getWidth()));
        float scale = 1f + (fit - 1f) * k;
        phone.setRotation(degrees); phone.setScaleX(scale); phone.setScaleY(scale);
    }

    private int stepAt(MediaPlayer mp, int own) {
        if (step == null) return own;
        long now = SystemClock.uptimeMillis();
        if (step.playing.isEmpty()) { step.epoch = now - own; return own; }
        int length = Math.max(1, mp.getDuration());
        return (int) ((now - step.epoch) % length);
    }

    private void stepped(boolean playing) {
        if (step == null) return;
        if (playing) step.playing.add(this); else step.playing.remove(this);
    }

    void setHostActive(boolean active) {
        if (active && !hostActive) activeSince = SystemClock.uptimeMillis();
        boolean leaving = hostActive && !active;
        hostActive = active;
        if (!active) pauseNow();
        if (leaving) keepFrame();
        updateMotion();
    }

    private void keepFrame() {
        try {
            if (!attached || disposed || !motion || failed || poster.getVisibility() == VISIBLE) return;
            Context context = getContext();
            if (context instanceof android.app.Activity && ((android.app.Activity) context).isFinishing()) return;
            Bitmap frame = visibleEnough() && texture.isAvailable() ? texture.getBitmap() : null;
            poster.animate().cancel();
            if (frame != null) {
                shownStill = "frame";
                poster.setImageBitmap(frame);
                poster.setAlpha(1f);
                poster.setVisibility(VISIBLE);
                return;
            }
            shownStill = null;
            showStill(false);
            final int gen = generation;
            worker().post(new Runnable() { public void run() { if (gen == generation) { releasePlayerW(false); position = 0; } } });
        } catch (Throwable ignored) { }
    }

    void dispose() {
        disposed = true;
        hostActive = false;
        detach();
    }

    private int resource(String name) {
        return getResources().getIdentifier(name, "raw", getContext().getPackageName());
    }

    private void showStill(final boolean fadeIn) {
        final int still = motion && !failed ? startStill : resultStill;
        if (turnAt != null && playEpoch == 0) applyTurn(still == startStill ? turnDeg[0] : turnStill);
        final String name = still + "@" + Math.max(1, phone.getWidth());
        if (name.equals(shownStill) && poster.getVisibility() == VISIBLE) return;
        shownStill = name;
        poster.animate().cancel();
        Bitmap cached = posters.get(name);
        if (cached != null) { putPoster(cached, fadeIn); return; }
        if (still == 0) return;
        final Resources res = getResources();
        final int width = phone.getWidth();
        final int gen = generation;
        worker().post(new Runnable() {
            public void run() {
                Bitmap decoded = decode(res, still, width);
                if (decoded == null) return;
                posters.put(name, decoded);
                final Bitmap shown = decoded;
                main.post(new Runnable() { public void run() { if (gen == generation && name.equals(shownStill)) putPoster(shown, fadeIn); } });
            }
        });
    }

    private void putPoster(Bitmap bitmap, boolean fadeIn) {
        poster.setImageBitmap(bitmap);
        poster.setVisibility(VISIBLE);
        if (fadeIn) { poster.setAlpha(0f); poster.animate().alpha(1f).setDuration(FADE_MS).start(); }
        else poster.setAlpha(1f);
    }

    private static Bitmap decode(Resources res, int id, int width) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeResource(res, id, o);
            int sample = 1;
            if (width > 0) while (o.outWidth / (sample * 2) >= width) sample *= 2;
            o = new BitmapFactory.Options();
            o.inSampleSize = sample;
            o.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeResource(res, id, o);
        } catch (Throwable error) { return null; }
    }

    private void filmShowing(int gen) {
        if (gen != generation || !wantPlaying) return;
        poster.animate().cancel();
        poster.animate().alpha(0f).setDuration(FADE_MS).withEndAction(new Runnable() {
            public void run() { if (wantPlaying) poster.setVisibility(INVISIBLE); poster.setAlpha(1f); }
        }).start();
    }

    private void updateMotion() {
        boolean was = motion;
        float scale = 1f;
        try { scale = Settings.Global.getFloat(getContext().getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1f); } catch (Throwable ignored) { }
        motion = scale != 0f
                && (power == null || !power.isPowerSaveMode())
                && (access == null || !access.isTouchExplorationEnabled());
        if (was != motion) {
            shownStill = null;
            if (!motion) { pauseNow(); poster.setVisibility(VISIBLE); }
            showStill(false);
        }
        checkPlayback();
    }

    private boolean visibleEnough() {
        return attached && getWindowVisibility() == VISIBLE && isShown()
                && phone.getWidth() > 0 && phone.getHeight() > 0
                && phone.getGlobalVisibleRect(visible)
                && 2L * visible.width() * visible.height() >= (long) phone.getWidth() * phone.getHeight();
    }

    private boolean canPlay() {
        return hostActive && motion && !failed && !disposed && video != 0 && texture.isAvailable()
                && hasWindowFocus() && visibleEnough();
    }

    private void checkPlayback() {
        if (!attached) return;
        main.removeCallbacks(settleCheck);
        if (canPlay()) {
            long wait = activeSince + SETTLE_MS - SystemClock.uptimeMillis();
            if (wait > 0) { main.postDelayed(settleCheck, wait); return; }
            cancelRelease();
            if (!wantPlaying) {
                wantPlaying = true;
                awaitingFrame = true;
                final int gen = generation, id = video;
                final SurfaceTexture st = texture.getSurfaceTexture();
                final Resources res = getResources();
                final String name = clip;
                worker().post(new Runnable() { public void run() { playW(gen, id, st, res, name); } });
            }
            return;
        }
        pauseNow();
    }

    private void pauseNow() {
        if (wantPlaying) {
            wantPlaying = false;
            worker().post(new Runnable() { public void run() { pauseW(); } });
        }
        if (attached && !releaseScheduled) {
            releaseScheduled = true;
            main.postDelayed(releaseHidden, RELEASE_DELAY_MS);
        }
    }

    private void cancelRelease() {
        if (!releaseScheduled) return;
        main.removeCallbacks(releaseHidden);
        releaseScheduled = false;
    }

    private void playW(final int gen, int id, SurfaceTexture st, Resources res, final String name) {
        if (gen != generation) return;
        try {
            if (player != null && playerClip != id) releasePlayerW(false);
            if (surface != null && surfaceTex != st) {
                releasePlayerW(true);
                try { surface.release(); } catch (Throwable ignored) { }
                surface = null; surfaceTex = null;
            }
            if (surface == null) {
                if (st == null) return;
                surface = new Surface(st); surfaceTex = st;
            }
            if (player == null) {
                AssetFileDescriptor src = res.openRawResourceFd(id);
                if (src == null) { failW(gen, name, null); return; }
                MediaPlayer mp = new MediaPlayer();
                try {
                    mp.setDataSource(src.getFileDescriptor(), src.getStartOffset(), src.getLength());
                } finally { try { src.close(); } catch (Throwable ignored) { } }
                mp.setSurface(surface);
                mp.setVolume(0, 0);
                mp.setLooping(true);
                mp.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                    public boolean onError(MediaPlayer source, int what, int extra) {
                        if (source == player) failW(gen, name, what + ", " + extra);
                        return true;
                    }
                });
                mp.prepare();
                player = mp; playerClip = id;
                int at = stepAt(mp, position);
                if (at > 0) seekExact(mp, at);
                if (gen != generation) { releasePlayerW(false); return; }
                mp.start();
                stepped(true);
                playing(mp, Math.max(0, at));
            } else if (!player.isPlaying()) {
                if (step != null) seekExact(player, stepAt(player, player.getCurrentPosition()));
                int from = player.getCurrentPosition();
                player.start();
                stepped(true);
                playing(player, from);
            }
        } catch (Throwable error) { failW(gen, name, String.valueOf(error)); }
    }

    private static void seekExact(MediaPlayer mp, int at) {
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            try { MediaPlayer.class.getMethod("seekTo", long.class, int.class).invoke(mp, (long) at, 3); return; } catch (Throwable ignored) { }
        }
        mp.seekTo(at);
    }

    private void pauseW() {
        try { if (player != null && player.isPlaying()) player.pause(); } catch (Throwable ignored) { }
        stepped(false);
        if (turnAt != null) main.post(new Runnable() { public void run() { playEpoch = 0; } });
    }

    private void releasePlayerW(boolean remember) {
        MediaPlayer old = player;
        if (old == null) return;
        stepped(false);
        player = null; playerClip = 0;
        if (remember) { try { position = old.getCurrentPosition(); } catch (Throwable ignored) { position = 0; } }
        try { old.release(); } catch (Throwable ignored) { }
    }

    private void releaseSurfaceW() {
        releasePlayerW(false);
        if (surface != null) { try { surface.release(); } catch (Throwable ignored) { } surface = null; surfaceTex = null; }
        if (texturePending != null) { try { texturePending.release(); } catch (Throwable ignored) { } texturePending = null; }
    }

    private void failW(final int gen, String name, String why) {
        Log.w("PixelSuite", "Preview playback failed for " + name + (why == null ? "" : " (" + why + ")"));
        releasePlayerW(false);
        main.post(new Runnable() {
            public void run() {
                if (gen != generation) return;
                failed = true; wantPlaying = false; shownStill = null;
                showStill(false);
            }
        });
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (disposed) return;
        attached = true;
        ViewTreeObserver vto = getViewTreeObserver();
        vto.addOnScrollChangedListener(this);
        vto.addOnGlobalLayoutListener(this);
        Context context = getContext();
        try {
            context.getContentResolver().registerContentObserver(
                    Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, animatorSetting);
            context.registerReceiver(powerChange, new IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED));
        } catch (Throwable ignored) { }
        if (access != null) access.addTouchExplorationStateChangeListener(explorationChange);
        updateMotion();
    }

    private void detach() {
        main.removeCallbacks(settleCheck);
        if (turnerPosted) { Choreographer.getInstance().removeFrameCallback(turner); turnerPosted = false; }
        playEpoch = 0;
        cancelRelease();
        if (attached) {
            attached = false;
            ViewTreeObserver vto = getViewTreeObserver();
            if (vto.isAlive()) { vto.removeOnScrollChangedListener(this); vto.removeOnGlobalLayoutListener(this); }
            Context context = getContext();
            try {
                context.getContentResolver().unregisterContentObserver(animatorSetting);
                context.unregisterReceiver(powerChange);
            } catch (Throwable ignored) { }
            if (access != null) access.removeTouchExplorationStateChangeListener(explorationChange);
        }
        wantPlaying = false;
        poster.animate().cancel();
        poster.setAlpha(1f);
        poster.setVisibility(VISIBLE);
        worker().post(new Runnable() { public void run() { releasePlayerW(true); } });
    }

    @Override protected void onDetachedFromWindow() {
        detach();
        super.onDetachedFromWindow();
    }

    @Override public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        checkPlayback();
    }

    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (attached) checkPlayback();
    }

    @Override protected void onVisibilityChanged(View changed, int visibility) {
        super.onVisibilityChanged(changed, visibility);
        if (attached) checkPlayback();
    }

    @Override public void onScrollChanged() { checkPlayback(); }
    @Override public void onGlobalLayout() { checkPlayback(); }

    @Override public void onSurfaceTextureAvailable(SurfaceTexture available, int width, int height) {
        if (disposed) return;
        if (shownStill == null) showStill(false);
        checkPlayback();
    }

    @Override public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {}

    @Override public boolean onSurfaceTextureDestroyed(final SurfaceTexture destroyed) {
        wantPlaying = false;
        poster.setVisibility(VISIBLE);
        worker().post(new Runnable() { public void run() { texturePending = destroyed; releaseSurfaceW(); } });
        return false;
    }

    @Override public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        if (awaitingFrame && wantPlaying) { awaitingFrame = false; filmShowing(generation); }
    }
}
