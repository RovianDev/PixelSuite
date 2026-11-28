package io.github.pixelsuite;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.ExifInterface;
import android.media.Image;
import android.media.ImageReader;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Size;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.Window;
import android.view.animation.OvershootInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class SearchScan extends Activity {
    static final String FROM_APP = "from_app", MODE = "mode";
    private boolean fromApp, idMode, galleryMode;
    private TextView side;
    static final String[] FILTERS = {"original", "auto", "enhanced", "gray", "bw", "lighten"};
    static final String[] FILTER_NAMES = {"Original", "Auto", "Enhanced", "Grayscale", "Black & white", "Lighten"};
    private static final int PICK = 71, CAMERA = 72;

    static final class Page {
        File src;
        float[] quad = {0, 0, 1, 0, 1, 1, 0, 1};
        int turn;
        String filter = "auto";
        Bitmap base, shown;
    }

    private final List<Page> pages = new ArrayList<Page>();
    private final Set<Page> chosen = new HashSet<Page>();
    private final Handler main = new Handler(Looper.getMainLooper());
    private HandlerThread thread;
    private Handler bg;
    private File dir, out;
    private boolean dark, black, studioOpen, opening, busy, asked, everything;
    private int ink, ink2, surface, chip, accent, onAccent;
    private float d;
    private String filter = "auto";
    private int current;

    private FrameLayout root, cameraBox, studio;
    private TextureView view;
    private Frame frame;
    private Stack stack;
    private CameraDevice camera;
    private android.media.MediaActionSound shutter;
    private CameraCaptureSession session;
    private ImageReader reader;
    private Surface surface0;
    private int sensor = 90;
    private volatile float[] live;
    private ImageView big;
    private LinearLayout strip, chips;
    private TextView scope, whole;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (!PdfHost.ok(this)) { PdfHost.locked(this); finish(); return; }
        filter = PdfHost.look(this);
        fromApp = getIntent().getBooleanExtra(FROM_APP, false);
        idMode = "id".equals(getIntent().getStringExtra(MODE));
        galleryMode = "gallery".equals(getIntent().getStringExtra(MODE));
        d = getResources().getDisplayMetrics().density;
        dark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        try { black = PdfHost.superDark(this); } catch (Throwable ignored) { }
        if (black) dark = true;
        ink = dark ? 0xfff1f3f6 : 0xff1c1d21; ink2 = dark ? 0xffa9adb5 : 0xff5d626b;
        surface = black ? 0xff000000 : dark ? 0xff17181b : 0xfff5f6fa;
        chip = dark ? 0x24ffffff : 0x14000000;
        accent = tone(dark ? "system_accent1_200" : "system_accent1_600", dark ? 0xff8ab4f8 : 0xff1a73e8);
        onAccent = dark ? tone("system_accent1_800", 0xff0b1b33) : 0xffffffff;
        dir = new File(getCacheDir(), "scans/session"); out = new File(getCacheDir(), "scans/out");
        wipe(dir); wipe(out); dir.mkdirs(); out.mkdirs();
        thread = new HandlerThread("scan"); thread.start(); bg = new Handler(thread.getLooper());
        Window w = getWindow();
        w.setStatusBarColor(Color.BLACK); w.setNavigationBarColor(Color.BLACK);
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        setContentView(root);
        root.setOnApplyWindowInsetsListener((v, in) -> { topInset = in.getSystemWindowInsetTop(); bottomInset = in.getSystemWindowInsetBottom(); inset(); return in; });
        buildCamera();
        inset();
        main.postDelayed(watch, 500);
        if (galleryMode) main.postDelayed(this::pick, 200);
    }

    private int tone(String name, int fallback) {
        try { int id = getResources().getIdentifier(name, "color", "android"); if (id != 0) return getResources().getColor(id) | 0xff000000; } catch (Throwable ignored) { }
        return fallback;
    }

    private int topInset, bottomInset;
    private View studioColumn;

    private void inset() {
        if (cameraBox != null) cameraBox.setPadding(0, topInset, 0, bottomInset);
        if (studioColumn != null) studioColumn.setPadding(0, topInset, 0, bottomInset);
        View crop = studio == null ? null : studio.findViewWithTag("crop");
        if (crop != null) crop.setPadding(0, topInset, 0, bottomInset);
    }

    private static void wipe(File f) {
        File[] all = f.listFiles();
        if (all != null) for (File x : all) { if (x.isDirectory()) wipe(x); x.delete(); }
    }

    private int dp(float v) { return Math.round(v * d); }

    private void toast(String s) { PdfHost.toast(this, I18n.cs(s)); }

    private static void bloom(final View v) {
        v.setScaleX(0.8f); v.setScaleY(0.8f);
        ValueAnimator wide = ValueAnimator.ofFloat(0.8f, 1f), tall = ValueAnimator.ofFloat(0.8f, 1f);
        wide.setDuration(360); wide.setInterpolator(new OvershootInterpolator(2.4f));
        wide.addUpdateListener(a -> v.setScaleX((Float) a.getAnimatedValue()));
        tall.setDuration(400); tall.setStartDelay(60); tall.setInterpolator(new OvershootInterpolator(2.4f));
        tall.addUpdateListener(a -> v.setScaleY((Float) a.getAnimatedValue()));
        wide.start(); tall.start();
    }

    private TextView text(String s, float size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(I18n.cs(s)); t.setTextSize(TypedValue.COMPLEX_UNIT_SP, size); t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return t;
    }

    private View round(String glyph, String label, int size, int fill, int color, final Runnable run) {
        ImageView v = new ImageView(this);
        v.setImageDrawable(new SearchGlyph(glyph, color));
        int pad = Math.round(size * 0.24f);
        v.setPadding(pad, pad, pad, pad);
        GradientDrawable g = new GradientDrawable(); g.setShape(GradientDrawable.OVAL); g.setColor(fill);
        v.setBackground(g);
        v.setContentDescription(I18n.cs(label));
        v.setOnClickListener(x -> { x.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); bloom(x); run.run(); });
        return v;
    }

    private void buildCamera() {
        cameraBox = new FrameLayout(this);
        root.addView(cameraBox, new FrameLayout.LayoutParams(-1, -1));
        int w = getResources().getDisplayMetrics().widthPixels, top = dp(64);
        view = new TextureView(this);
        FrameLayout.LayoutParams vp = new FrameLayout.LayoutParams(w, Math.round(w * 4f / 3f));
        vp.topMargin = top;
        cameraBox.addView(view, vp);
        view.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            public void onSurfaceTextureAvailable(SurfaceTexture s, int a, int b) { start(); }
            public void onSurfaceTextureSizeChanged(SurfaceTexture s, int a, int b) { }
            public boolean onSurfaceTextureDestroyed(SurfaceTexture s) { stop(); return true; }
            public void onSurfaceTextureUpdated(SurfaceTexture s) { }
        });
        frame = new Frame(this);
        FrameLayout.LayoutParams fp = new FrameLayout.LayoutParams(w, Math.round(w * 4f / 3f));
        fp.topMargin = top;
        cameraBox.addView(frame, fp);
        View close = round(SearchGlyph.CLEAR, "Close", dp(44), 0x33ffffff, Color.WHITE, this::finish);
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(dp(44), dp(44), Gravity.TOP | Gravity.START);
        cp.topMargin = dp(12); cp.setMarginStart(dp(14));
        cameraBox.addView(close, cp);
        TextView title = text(idMode ? "Scan ID Card" : "Smart Scan", 17, Color.WHITE, true);
        FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        tp.topMargin = dp(23);
        cameraBox.addView(title, tp);
        if (idMode) {
            side = text("Front side", 14, Color.WHITE, true);
            side.setPadding(dp(14), dp(6), dp(14), dp(6));
            GradientDrawable sg = new GradientDrawable(); sg.setCornerRadius(dp(16)); sg.setColor(0x55000000); side.setBackground(sg);
            FrameLayout.LayoutParams sp0 = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            sp0.topMargin = dp(84);
            cameraBox.addView(side, sp0);
        }
        FrameLayout bar = new FrameLayout(this);
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(-1, dp(132), Gravity.BOTTOM);
        bp.bottomMargin = dp(18);
        cameraBox.addView(bar, bp);
        View gallery = round("album", "Add from gallery", dp(52), 0x33ffffff, Color.WHITE, this::pick);
        FrameLayout.LayoutParams gp = new FrameLayout.LayoutParams(dp(52), dp(52), Gravity.CENTER_VERTICAL | Gravity.START);
        gp.setMarginStart(dp(34));
        bar.addView(gallery, gp);
        View shutter = new View(this);
        GradientDrawable ring = new GradientDrawable(); ring.setShape(GradientDrawable.OVAL); ring.setColor(Color.WHITE); ring.setStroke(dp(5), 0x66ffffff);
        shutter.setBackground(ring);
        shutter.setContentDescription(I18n.cs("Take photo"));
        shutter.setOnClickListener(x -> { x.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY); bloom(x); shoot(); });
        bar.addView(shutter, new FrameLayout.LayoutParams(dp(78), dp(78), Gravity.CENTER));
        stack = new Stack(this);
        stack.setContentDescription(I18n.cs("Pages"));
        stack.setOnClickListener(x -> { if (!pages.isEmpty()) { x.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); openStudio(); } });
        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(dp(84), dp(96), Gravity.CENTER_VERTICAL | Gravity.END);
        sp.setMarginEnd(dp(22));
        bar.addView(stack, sp);
    }

    private final Runnable watch = new Runnable() {
        public void run() {
            if (isFinishing()) return;
            try {
                if (!studioOpen && camera != null && view != null && view.isAvailable()) {
                    final Bitmap b = view.getBitmap(96, 128);
                    if (b != null) bg.post(() -> { live = detect(b); b.recycle(); frame.postInvalidate(); });
                }
            } catch (Throwable ignored) { }
            main.postDelayed(this, 380);
        }
    };

    final class Frame extends View {
        private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG), wash = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final float[] at = new float[8];
        private boolean has;
        private float flash;
        Frame(Context c) { super(c); line.setStyle(Paint.Style.STROKE); line.setStrokeWidth(dp(2.5f)); line.setStrokeJoin(Paint.Join.ROUND); }
        void blink() { ValueAnimator a = ValueAnimator.ofFloat(0.55f, 0f); a.setDuration(260); a.addUpdateListener(x -> { flash = (Float) x.getAnimatedValue(); invalidate(); }); a.start(); }
        @Override protected void onDraw(Canvas c) {
            float[] q = live;
            if (q != null) {
                for (int i = 0; i < 8; i++) { float to = q[i] * (i % 2 == 0 ? getWidth() : getHeight()); at[i] = has ? at[i] + (to - at[i]) * 0.45f : to; }
                has = true;
                path.reset(); path.moveTo(at[0], at[1]); path.lineTo(at[2], at[3]); path.lineTo(at[4], at[5]); path.lineTo(at[6], at[7]); path.close();
                wash.setColor(PdfHost.alpha(accent, 16)); c.drawPath(path, wash);
                line.setColor(accent); c.drawPath(path, line);
                postInvalidateDelayed(40);
            } else has = false;
            if (flash > 0) { wash.setColor(Color.argb(Math.round(255 * flash), 255, 255, 255)); c.drawRect(0, 0, getWidth(), getHeight(), wash); }
        }
    }

    final class Stack extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG), label = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF box = new RectF();
        private final Path clip = new Path();
        Stack(Context c) { super(c); setClickable(true); label.setTextAlign(Paint.Align.CENTER); label.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL)); }
        @Override protected void onDraw(Canvas c) {
            int n = pages.size();
            float w = getWidth(), h = getHeight(), cw = w * 0.66f, ch = cw * 1.3f, cx = w * 0.46f, cy = h * 0.56f, r = dp(8);
            if (n == 0) {
                paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(1.5f)); paint.setColor(0x55ffffff);
                box.set(cx - cw / 2, cy - ch / 2, cx + cw / 2, cy + ch / 2); c.drawRoundRect(box, r, r, paint);
                paint.setStyle(Paint.Style.FILL);
                return;
            }
            float[] tilt = {-9, 7, 0};
            int from = Math.max(0, n - 3);
            for (int i = from; i < n; i++) {
                Bitmap b = pages.get(i).shown;
                int s = c.save();
                c.rotate(tilt[3 - (n - i)], cx, cy);
                box.set(cx - cw / 2, cy - ch / 2, cx + cw / 2, cy + ch / 2);
                paint.setStyle(Paint.Style.FILL); paint.setColor(0x55000000);
                c.drawRoundRect(box.left + dp(1), box.top + dp(2), box.right + dp(1), box.bottom + dp(2), r, r, paint);
                paint.setColor(Color.WHITE); c.drawRoundRect(box, r, r, paint);
                if (b != null && !b.isRecycled()) {
                    clip.reset(); clip.addRoundRect(box, r, r, Path.Direction.CW);
                    c.clipPath(clip);
                    float k = Math.max(box.width() / b.getWidth(), box.height() / b.getHeight());
                    float bw = b.getWidth() * k, bh = b.getHeight() * k;
                    c.drawBitmap(b, null, new RectF(cx - bw / 2, cy - bh / 2, cx + bw / 2, cy + bh / 2), paint);
                }
                c.restoreToCount(s);
            }
            float br = dp(13), bx = w - br - dp(2), by = br + dp(2);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(black ? 0xd9000000 : dark ? 0xcc2b2d31 : 0xe6ffffff);
            c.drawCircle(bx, by, br, paint);
            PdfHost.rim(SearchScan.this, c, bx - br, by - br, bx + br, by + br, br, dp(1.3f), dark);
            label.setColor(dark ? Color.WHITE : 0xff1c1d21); label.setTextSize(dp(13));
            c.drawText(String.valueOf(n), bx, by + dp(4.6f), label);
        }
    }

    private void start() {
        if (studioOpen || camera != null || opening || view == null || !view.isAvailable()) return;
        if (checkSelfPermission("android.permission.CAMERA") != PackageManager.PERMISSION_GRANTED) {
            if (!asked) { asked = true; requestPermissions(new String[]{"android.permission.CAMERA"}, CAMERA); }
            return;
        }
        try {
            CameraManager cm = (CameraManager) getSystemService(CAMERA_SERVICE);
            String id = null;
            for (String x : cm.getCameraIdList()) { Integer f = cm.getCameraCharacteristics(x).get(CameraCharacteristics.LENS_FACING); if (f != null && f == CameraCharacteristics.LENS_FACING_BACK) { id = x; break; } }
            if (id == null) { toast("Couldn't open the camera"); return; }
            CameraCharacteristics ch = cm.getCameraCharacteristics(id);
            StreamConfigurationMap map = ch.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            Size shot = best(map.getOutputSizes(ImageFormat.JPEG), 100000), pre = best(map.getOutputSizes(SurfaceTexture.class), 1920);
            Integer so = ch.get(CameraCharacteristics.SENSOR_ORIENTATION);
            sensor = so == null ? 90 : so;
            reader = ImageReader.newInstance(shot.getWidth(), shot.getHeight(), ImageFormat.JPEG, 2);
            reader.setOnImageAvailableListener(r -> {
                Image img = null;
                try {
                    img = r.acquireNextImage();
                    ByteBuffer buf = img.getPlanes()[0].getBuffer();
                    byte[] data = new byte[buf.remaining()]; buf.get(data);
                    File f = new File(dir, "p" + System.nanoTime() + ".jpg");
                    FileOutputStream o = new FileOutputStream(f); o.write(data); o.close();
                    add(f);
                } catch (Throwable t) { android.util.Log.w("PixelSuite", "scan: shot", t); busy = false; }
                finally { if (img != null) img.close(); }
            }, bg);
            view.getSurfaceTexture().setDefaultBufferSize(pre.getWidth(), pre.getHeight());
            opening = true;
            cm.openCamera(id, new CameraDevice.StateCallback() {
                @Override public void onOpened(CameraDevice dev) { opening = false; camera = dev; session(); }
                @Override public void onDisconnected(CameraDevice dev) { opening = false; dev.close(); camera = null; }
                @Override public void onError(CameraDevice dev, int e) { opening = false; dev.close(); camera = null; main.post(() -> toast("Couldn't open the camera")); }
            }, bg);
        } catch (Throwable t) { opening = false; android.util.Log.w("PixelSuite", "scan: camera", t); toast("Couldn't open the camera"); }
    }

    private static Size best(Size[] all, int limit) {
        Size pick = null, any = null;
        for (Size s : all) {
            if (Math.max(s.getWidth(), s.getHeight()) > limit) continue;
            long area = (long) s.getWidth() * s.getHeight();
            if (any == null || area > (long) any.getWidth() * any.getHeight()) any = s;
            if (Math.abs(s.getWidth() / (float) s.getHeight() - 4f / 3f) < 0.02f && (pick == null || area > (long) pick.getWidth() * pick.getHeight())) pick = s;
        }
        return pick != null ? pick : any != null ? any : all[0];
    }

    private void session() {
        try {
            surface0 = new Surface(view.getSurfaceTexture());
            camera.createCaptureSession(Arrays.asList(surface0, reader.getSurface()), new CameraCaptureSession.StateCallback() {
                @Override public void onConfigured(CameraCaptureSession s) {
                    try {
                        session = s;
                        CaptureRequest.Builder b = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                        b.addTarget(surface0);
                        b.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                        s.setRepeatingRequest(b.build(), null, bg);
                    } catch (Throwable t) { android.util.Log.w("PixelSuite", "scan: preview", t); }
                }
                @Override public void onConfigureFailed(CameraCaptureSession s) { main.post(() -> toast("Couldn't open the camera")); }
            }, bg);
        } catch (Throwable t) { android.util.Log.w("PixelSuite", "scan: session", t); }
    }

    private void stop() {
        try { if (session != null) session.close(); } catch (Throwable ignored) { }
        try { if (camera != null) camera.close(); } catch (Throwable ignored) { }
        try { if (reader != null) reader.close(); } catch (Throwable ignored) { }
        session = null; camera = null; reader = null; opening = false; live = null;
    }

    private void shoot() {
        if (camera == null || session == null || busy) return;
        busy = true;
        frame.blink();
        try {
            if (shutter == null) { shutter = new android.media.MediaActionSound(); shutter.load(android.media.MediaActionSound.SHUTTER_CLICK); }
            shutter.play(android.media.MediaActionSound.SHUTTER_CLICK);
        } catch (Throwable ignored) { }
        try {
            CaptureRequest.Builder b = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            b.addTarget(reader.getSurface());
            b.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            b.set(CaptureRequest.JPEG_ORIENTATION, sensor);
            b.set(CaptureRequest.JPEG_QUALITY, (byte) 97);
            session.capture(b.build(), null, bg);
        } catch (Throwable t) { busy = false; android.util.Log.w("PixelSuite", "scan: capture", t); }
    }

    private void sideLabel() {
        if (side == null) return;
        String s = pages.size() % 2 == 0 ? "Front side" : "Back side";
        side.setText(I18n.cs(s));
        bloom(side);
    }

    private void pick() {
        try {
            Intent i = new Intent(Intent.ACTION_GET_CONTENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            startActivityForResult(i, PICK);
        } catch (Throwable t) { toast("Couldn't open it"); }
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != PICK || result != RESULT_OK || data == null) { if (request == PICK && galleryMode && pages.isEmpty()) finish(); return; }
        final List<Uri> all = new ArrayList<Uri>();
        ClipData clip = data.getClipData();
        if (clip != null) for (int i = 0; i < clip.getItemCount(); i++) all.add(clip.getItemAt(i).getUri());
        else if (data.getData() != null) all.add(data.getData());
        bg.post(() -> {
            for (Uri u : all) {
                try {
                    File f = new File(dir, "g" + System.nanoTime() + ".jpg");
                    InputStream in = getContentResolver().openInputStream(u);
                    OutputStream o = new FileOutputStream(f);
                    byte[] buf = new byte[65536]; int n;
                    while ((n = in.read(buf)) > 0) o.write(buf, 0, n);
                    in.close(); o.close();
                    add(f);
                } catch (Throwable t) { android.util.Log.w("PixelSuite", "scan: import", t); }
            }
        });
    }

    @Override public void onRequestPermissionsResult(int request, String[] names, int[] results) {
        if (request != CAMERA) return;
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) start();
        else toast("Camera access is needed to scan");
    }

    @Override protected void onResume() {
        super.onResume();
        if (!PdfHost.ok(this)) { PdfHost.locked(this); finish(); return; }
        if (shutter == null) try { shutter = new android.media.MediaActionSound(); shutter.load(android.media.MediaActionSound.SHUTTER_CLICK); } catch (Throwable ignored) { shutter = null; }
        if (view != null && !studioOpen) start();
    }
    @Override protected void onPause() { super.onPause(); stop(); }
    @Override protected void onDestroy() {
        super.onDestroy();
        main.removeCallbacksAndMessages(null);
        if (thread != null) thread.quitSafely();
        try { if (shutter != null) shutter.release(); } catch (Throwable ignored) { }
        shutter = null;
    }

    @Override public void onBackPressed() {
        if (studio != null && studio.findViewWithTag("crop") != null) { studio.removeView(studio.findViewWithTag("crop")); return; }
        if (studioOpen) { closeStudio(); return; }
        super.onBackPressed();
    }

    private void add(File f) {
        try {
            Bitmap full = load(f, 4200);
            if (full == null) { busy = false; return; }
            final Page p = new Page();
            p.src = f; p.filter = filter;
            Bitmap tiny = Bitmap.createScaledBitmap(full, Math.max(1, full.getWidth() * 160 / Math.max(full.getWidth(), full.getHeight())), Math.max(1, full.getHeight() * 160 / Math.max(full.getWidth(), full.getHeight())), true);
            float[] q = detect(tiny);
            if (tiny != full) tiny.recycle();
            if (q != null) p.quad = q;
            p.base = warp(full, p.quad, 0, 1100);
            full.recycle();
            p.shown = filtered(p.base, p.filter);
            main.post(() -> {
                busy = false;
                pages.add(p);
                sideLabel();
                if (galleryMode && !studioOpen) { openStudio(); return; }
                if (studioOpen) { current = pages.size() - 1; fill(); return; }
                drop(p);
            });
        } catch (Throwable t) { busy = false; android.util.Log.w("PixelSuite", "scan: page", t); }
    }

    private void drop(final Page p) {
        final ImageView fly = new ImageView(this);
        fly.setImageBitmap(p.shown);
        fly.setScaleType(ImageView.ScaleType.CENTER_CROP);
        final int[] a = new int[2], b = new int[2], r = new int[2];
        view.getLocationInWindow(a); stack.getLocationInWindow(b); root.getLocationInWindow(r);
        final float fw = view.getWidth() * 0.8f, fh = view.getHeight() * 0.8f, fx = a[0] - r[0] + view.getWidth() * 0.1f, fy = a[1] - r[1] + view.getHeight() * 0.1f;
        final float tw = stack.getWidth() * 0.66f, th = tw * 1.3f, tx = b[0] - r[0] + stack.getWidth() * 0.46f - tw / 2, ty = b[1] - r[1] + stack.getHeight() * 0.56f - th / 2;
        root.addView(fly, new FrameLayout.LayoutParams(Math.round(fw), Math.round(fh)));
        fly.setPivotX(0); fly.setPivotY(0);
        fly.setX(fx); fly.setY(fy);
        ValueAnimator wide = ValueAnimator.ofFloat(0f, 1f), tall = ValueAnimator.ofFloat(0f, 1f);
        wide.setDuration(380); wide.setInterpolator(new OvershootInterpolator(0.9f));
        wide.addUpdateListener(x -> { float t = (Float) x.getAnimatedValue(); fly.setX(fx + (tx - fx) * t); fly.setScaleX(1 + (tw / fw - 1) * t); });
        tall.setDuration(420); tall.setStartDelay(60); tall.setInterpolator(new OvershootInterpolator(1.1f));
        tall.addUpdateListener(x -> { float t = (Float) x.getAnimatedValue(); fly.setY(fy + (ty - fy) * t); fly.setScaleY(1 + (th / fh - 1) * t); });
        tall.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator x) { root.removeView(fly); stack.invalidate(); bloom(stack); }
        });
        wide.start(); tall.start();
    }

    private View pill(String label, String glyph, boolean filled, final Runnable run) {
        LinearLayout p = new LinearLayout(this);
        p.setGravity(Gravity.CENTER_VERTICAL);
        p.setPadding(dp(14), dp(9), dp(16), dp(9));
        GradientDrawable g = new GradientDrawable(); g.setCornerRadius(dp(22)); g.setColor(filled ? accent : chip);
        p.setBackground(g);
        ImageView i = new ImageView(this);
        i.setImageDrawable(new SearchGlyph(glyph, filled ? onAccent : ink));
        p.addView(i, new LinearLayout.LayoutParams(dp(20), dp(20)));
        TextView t = text(label, 14, filled ? onAccent : ink, true);
        t.setPadding(dp(8), 0, 0, 0);
        p.addView(t);
        p.setOnClickListener(x -> { x.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); bloom(x); run.run(); });
        return p;
    }

    private void openStudio() {
        if (studioOpen) return;
        studioOpen = true;
        stop();
        current = Math.max(0, pages.size() - 1);
        chosen.clear(); everything = true;
        studio = new FrameLayout(this);
        studio.setBackgroundColor(surface);
        studio.setClickable(true);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        studio.addView(col, new FrameLayout.LayoutParams(-1, -1));
        studioColumn = col;
        inset();
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(12), dp(14), dp(12), dp(8));
        top.addView(round(SearchGlyph.BACK, "Back", dp(44), chip, ink, this::closeStudio), new LinearLayout.LayoutParams(dp(44), dp(44)));
        col.addView(top);
        big = new ImageView(this);
        big.setScaleType(ImageView.ScaleType.FIT_CENTER);
        big.setPadding(dp(18), dp(6), dp(18), dp(6));
        col.addView(big, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout tools = new LinearLayout(this);
        tools.setGravity(Gravity.CENTER);
        tools.setPadding(0, dp(6), 0, dp(6));
        String[][] t = {{"turn", "Rotate"}, {"crop", "Crop"}, {SearchGlyph.PREV, "Move left"}, {SearchGlyph.NEXT, "Move right"}, {"bin", "Delete"}};
        for (int i = 0; i < t.length; i++) {
            final int which = i;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(44), dp(44));
            lp.setMargins(dp(7), 0, dp(7), 0);
            tools.addView(round(t[i][0], t[i][1], dp(44), chip, ink, () -> tool(which)), lp);
        }
        col.addView(tools);
        LinearLayout pick = new LinearLayout(this);
        pick.setGravity(Gravity.CENTER_VERTICAL);
        pick.setPadding(dp(20), dp(4), dp(14), dp(2));
        scope = text("All pages", 13, ink2, false);
        pick.addView(scope, new LinearLayout.LayoutParams(0, -2, 1));
        whole = text("Select all", 13, ink, true);
        whole.setPadding(dp(14), dp(7), dp(14), dp(7));
        whole.setOnClickListener(x -> { x.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); bloom(x); everything = !everything; fill(); });
        pick.addView(whole);
        col.addView(pick);
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        strip = new LinearLayout(this);
        strip.setPadding(dp(14), dp(6), dp(14), dp(6));
        hs.addView(strip);
        col.addView(hs);
        HorizontalScrollView cs = new HorizontalScrollView(this);
        cs.setHorizontalScrollBarEnabled(false);
        chips = new LinearLayout(this);
        chips.setPadding(dp(14), dp(6), dp(14), dp(8));
        cs.addView(chips);
        col.addView(cs);
        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER);
        actions.setPadding(dp(14), dp(6), dp(14), dp(18));
        actions.addView(pill("Save as PDF", SearchGlyph.PAGE, false, this::save));
        LinearLayout.LayoutParams shp = new LinearLayout.LayoutParams(-2, -2);
        shp.setMarginStart(dp(8));
        actions.addView(pill("Share", SearchGlyph.SHARE, true, this::share), shp);
        col.addView(actions, new LinearLayout.LayoutParams(-1, -2));
        root.addView(studio, new FrameLayout.LayoutParams(-1, -1));
        fill();
        final int[] a = new int[2], r = new int[2];
        stack.getLocationInWindow(a); root.getLocationInWindow(r);
        studio.setPivotX(a[0] - r[0] + stack.getWidth() / 2f); studio.setPivotY(a[1] - r[1] + stack.getHeight() / 2f);
        studio.setScaleX(0.12f); studio.setScaleY(0.12f);
        ValueAnimator wide = ValueAnimator.ofFloat(0.12f, 1f), tall = ValueAnimator.ofFloat(0.12f, 1f);
        wide.setDuration(340); wide.setInterpolator(new OvershootInterpolator(0.8f));
        wide.addUpdateListener(x -> studio.setScaleX((Float) x.getAnimatedValue()));
        tall.setDuration(380); tall.setStartDelay(50); tall.setInterpolator(new OvershootInterpolator(0.8f));
        tall.addUpdateListener(x -> studio.setScaleY((Float) x.getAnimatedValue()));
        wide.start(); tall.start();
        getWindow().setStatusBarColor(surface); getWindow().setNavigationBarColor(surface);
        if (!dark) getWindow().getDecorView().setSystemUiVisibility(0x2000 | 0x10);
    }

    private void closeStudio() {
        if (!studioOpen || studio == null) return;
        final View gone = studio;
        studioOpen = false; studio = null; studioColumn = null;
        getWindow().setStatusBarColor(Color.BLACK); getWindow().setNavigationBarColor(Color.BLACK);
        getWindow().getDecorView().setSystemUiVisibility(0);
        gone.animate().scaleX(0.12f).scaleY(0.12f).alpha(0f).setDuration(220).withEndAction(() -> { root.removeView(gone); stack.invalidate(); start(); }).start();
    }

    private void fill() {
        if (!studioOpen || strip == null) return;
        if (pages.isEmpty()) { closeStudio(); return; }
        current = Math.max(0, Math.min(current, pages.size() - 1));
        chosen.retainAll(pages);
        big.setImageBitmap(pages.get(current).shown);
        strip.removeAllViews();
        for (int i = 0; i < pages.size(); i++) {
            final int at = i;
            final Page p = pages.get(i);
            FrameLayout cell = new FrameLayout(this);
            ImageView th = new ImageView(this);
            th.setScaleType(ImageView.ScaleType.CENTER_CROP);
            th.setImageBitmap(p.shown);
            th.setClipToOutline(true);
            GradientDrawable shape = new GradientDrawable(); shape.setCornerRadius(dp(10)); shape.setColor(Color.WHITE);
            th.setBackground(shape);
            cell.addView(th, new FrameLayout.LayoutParams(-1, -1));
            boolean picked = everything || i == current;
            GradientDrawable ringd = new GradientDrawable(); ringd.setCornerRadius(dp(12)); ringd.setColor(Color.TRANSPARENT);
            ringd.setStroke(dp(3), picked ? accent : Color.TRANSPARENT);
            cell.setForeground(ringd);
            TextView n = new TextView(this);
            n.setText(String.valueOf(i + 1)); n.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11); n.setGravity(Gravity.CENTER);
            n.setTextColor(picked ? onAccent : Color.WHITE);
            GradientDrawable nb = new GradientDrawable(); nb.setShape(GradientDrawable.OVAL); nb.setColor(picked ? accent : 0x99000000);
            n.setBackground(nb);
            FrameLayout.LayoutParams np = new FrameLayout.LayoutParams(dp(20), dp(20), Gravity.BOTTOM | Gravity.END);
            np.setMargins(0, 0, dp(4), dp(4));
            cell.addView(n, np);
            cell.setOnClickListener(x -> {
                x.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                everything = false;
                current = at;
                fill();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(62), dp(82));
            lp.setMarginEnd(dp(8));
            strip.addView(cell, lp);
        }
        scope.setText(everything ? I18n.cs("All pages") : I18n.t("{0} selected").replace("{0}", "1"));
        GradientDrawable wb = new GradientDrawable(); wb.setCornerRadius(dp(18)); wb.setColor(everything ? accent : chip);
        whole.setBackground(wb);
        whole.setTextColor(everything ? onAccent : ink);
        chips.removeAllViews();
        String now = pages.get(current).filter;
        for (int i = 0; i < FILTERS.length; i++) {
            final String id = FILTERS[i];
            boolean on = id.equals(now);
            TextView c = text(FILTER_NAMES[i], 14, on ? onAccent : ink, true);
            c.setPadding(dp(16), dp(9), dp(16), dp(9));
            GradientDrawable g = new GradientDrawable(); g.setCornerRadius(dp(20)); g.setColor(on ? accent : chip);
            c.setBackground(g);
            c.setOnClickListener(x -> { x.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); bloom(x); look(id); });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.setMarginEnd(dp(8));
            chips.addView(c, lp);
        }
    }

    private List<Page> targets() {
        List<Page> who = new ArrayList<Page>();
        if (everything) who.addAll(pages); else if (current >= 0 && current < pages.size()) who.add(pages.get(current));
        return who;
    }

    private void look(final String id) {
        final List<Page> who = targets();
        if (everything) filter = id;
        bg.post(() -> {
            for (Page p : who) { p.filter = id; try { p.shown = filtered(p.base, id); } catch (Throwable ignored) { } }
            main.post(this::fill);
        });
    }

    private void rebase(final Page p) {
        bg.post(() -> {
            try {
                Bitmap full = load(p.src, 4200);
                if (full == null) return;
                p.base = warp(full, p.quad, p.turn, 1100);
                full.recycle();
                p.shown = filtered(p.base, p.filter);
            } catch (Throwable t) { android.util.Log.w("PixelSuite", "scan: page", t); }
            main.post(() -> { fill(); if (stack != null) stack.invalidate(); });
        });
    }

    private void tool(int which) {
        if (pages.isEmpty()) return;
        final Page p = pages.get(current);
        if (which == 0) { p.turn = (p.turn + 90) % 360; rebase(p); }
        else if (which == 1) crop(p);
        else if (which == 2 && current > 0) { pages.set(current, pages.get(current - 1)); pages.set(current - 1, p); current--; fill(); }
        else if (which == 3 && current < pages.size() - 1) { pages.set(current, pages.get(current + 1)); pages.set(current + 1, p); current++; fill(); }
        else if (which == 4) { pages.remove(p); chosen.remove(p); fill(); if (stack != null) stack.invalidate(); }
    }

    private void crop(final Page p) {
        bg.post(() -> {
            final Bitmap pic = load(p.src, 1400);
            if (pic == null) return;
            main.post(() -> {
                if (!studioOpen || studio == null) return;
                final FrameLayout box = new FrameLayout(this);
                box.setTag("crop");
                box.setBackgroundColor(surface);
                box.setClickable(true);
                final Corners corners = new Corners(this, pic, p.quad);
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -1);
                lp.setMargins(dp(18), dp(76), dp(18), dp(40));
                box.addView(corners, lp);
                View done = pill("Done", SearchGlyph.TICK, true, () -> { p.quad = corners.quad.clone(); studio.removeView(box); rebase(p); });
                FrameLayout.LayoutParams dpm = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
                dpm.setMargins(0, dp(16), dp(14), 0);
                box.addView(done, dpm);
                studio.addView(box, new FrameLayout.LayoutParams(-1, -1));
                inset();
                box.setAlpha(0f); box.animate().alpha(1f).setDuration(160).start();
            });
        });
    }

    final class Corners extends View {
        final Bitmap pic; final float[] quad;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final RectF area = new RectF();
        private final Path path = new Path();
        private int held = -1;
        Corners(Context c, Bitmap pic, float[] quad) { super(c); this.pic = pic; this.quad = quad.clone(); }
        @Override protected void onDraw(Canvas c) {
            float k = Math.min(getWidth() / (float) pic.getWidth(), getHeight() / (float) pic.getHeight());
            float w = pic.getWidth() * k, h = pic.getHeight() * k;
            area.set((getWidth() - w) / 2f, (getHeight() - h) / 2f, (getWidth() + w) / 2f, (getHeight() + h) / 2f);
            c.drawBitmap(pic, null, area, paint);
            path.reset();
            for (int i = 0; i < 4; i++) { float x = area.left + quad[i * 2] * w, y = area.top + quad[i * 2 + 1] * h; if (i == 0) path.moveTo(x, y); else path.lineTo(x, y); }
            path.close();
            paint.setStyle(Paint.Style.FILL); paint.setColor(PdfHost.alpha(accent, 14)); c.drawPath(path, paint);
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(2.5f)); paint.setColor(accent); c.drawPath(path, paint);
            for (int i = 0; i < 4; i++) {
                float x = area.left + quad[i * 2] * w, y = area.top + quad[i * 2 + 1] * h;
                paint.setStyle(Paint.Style.FILL); paint.setColor(Color.WHITE); c.drawCircle(x, y, dp(i == held ? 15 : 11), paint);
                paint.setStyle(Paint.Style.STROKE); paint.setColor(accent); c.drawCircle(x, y, dp(i == held ? 15 : 11), paint);
            }
            paint.setStyle(Paint.Style.FILL);
        }
        @Override public boolean onTouchEvent(MotionEvent e) {
            float w = area.width(), h = area.height();
            if (w <= 0 || h <= 0) return false;
            int a = e.getActionMasked();
            if (a == MotionEvent.ACTION_DOWN) {
                held = -1; float bestD = dp(48);
                for (int i = 0; i < 4; i++) {
                    float dx = e.getX() - (area.left + quad[i * 2] * w), dy = e.getY() - (area.top + quad[i * 2 + 1] * h), dist = (float) Math.hypot(dx, dy);
                    if (dist < bestD) { bestD = dist; held = i; }
                }
                invalidate();
                return held >= 0;
            }
            if (held < 0) return false;
            if (a == MotionEvent.ACTION_MOVE) {
                quad[held * 2] = Math.max(0f, Math.min(1f, (e.getX() - area.left) / w));
                quad[held * 2 + 1] = Math.max(0f, Math.min(1f, (e.getY() - area.top) / h));
                invalidate();
            } else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) { held = -1; invalidate(); }
            return true;
        }
    }

    private File pdf(String name) throws Exception {
        List<Page> all = new ArrayList<Page>(pages);
        wipe(out);
        File f = new File(out, name + ".pdf");
        int edge = PdfHost.edge(SearchScan.this), q = edge > 2200 ? 92 : 85;
        PdfKit.Writer w = new PdfKit.Writer(f, null);
        try {
            if (idMode) {
                for (int i = 0; i < all.size(); i += 2) {
                    int pw = 2480, ph = 3508;
                    Bitmap sheet = Bitmap.createBitmap(pw, ph, Bitmap.Config.ARGB_8888);
                    Canvas c = new Canvas(sheet); c.drawColor(Color.WHITE);
                    Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
                    for (int k = 0; k < 2 && i + k < all.size(); k++) {
                        Bitmap one = finished(all.get(i + k), edge);
                        if (one == null) continue;
                        float cw = pw * 0.58f, chh = cw * one.getHeight() / one.getWidth();
                        if (chh > ph * 0.4f) { chh = ph * 0.4f; cw = chh * one.getWidth() / one.getHeight(); }
                        float cx = (pw - cw) / 2, cy = k == 0 ? ph * 0.27f - chh / 2 : ph * 0.69f - chh / 2;
                        c.drawBitmap(one, null, new RectF(cx, cy, cx + cw, cy + chh), paint);
                        one.recycle();
                    }
                    w.page(sheet, 595, 842, q);
                    sheet.recycle();
                }
            } else {
                for (Page p : all) {
                    Bitmap done = finished(p, edge);
                    if (done == null) continue;
                    w.page(done, 595, 595f * done.getHeight() / done.getWidth(), q);
                    done.recycle();
                }
            }
            w.finish();
        } catch (Exception e) { w.abort(); throw e; }
        return f;
    }

    private Bitmap finished(Page p, int edge) {
        Bitmap full = load(p.src, 4200);
        if (full == null) return null;
        Bitmap page = warp(full, p.quad, p.turn, edge);
        full.recycle();
        Bitmap done = filtered(page, p.filter);
        if (done != page) page.recycle();
        return done;
    }

    private static String clean(String s) {
        String n = s == null ? "" : s.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", " ").trim();
        if (n.toLowerCase(Locale.ROOT).endsWith(".pdf")) n = n.substring(0, n.length() - 4).trim();
        return n.isEmpty() ? "Scan" : n.length() > 80 ? n.substring(0, 80) : n;
    }

    private String stamp() { return (idMode ? "ID card " : "Scan ") + new SimpleDateFormat("yyyy-MM-dd HHmm", Locale.US).format(new Date()); }

    private void save() { name(false); }

    private void name(final boolean thenShare) {
        if (pages.isEmpty()) return;
        AlertDialog.Builder box = new AlertDialog.Builder(this, dark ? android.R.style.Theme_DeviceDefault_Dialog_Alert : android.R.style.Theme_DeviceDefault_Light_Dialog_Alert);
        final EditText name = new EditText(box.getContext());
        name.setSingleLine(true);
        name.setText(stamp());
        name.setSelectAllOnFocus(true);
        name.setTextColor(ink); name.setHintTextColor(ink2); name.setHighlightColor(PdfHost.alpha(accent, 30));
        name.setBackgroundTintList(android.content.res.ColorStateList.valueOf(accent));
        FrameLayout wrap = new FrameLayout(box.getContext());
        wrap.setPadding(dp(22), dp(8), dp(22), 0);
        wrap.addView(name);
        box.setTitle(I18n.cs("File name")).setView(wrap)
                .setNegativeButton(I18n.cs("Cancel"), null)
                .setPositiveButton(I18n.cs("Save"), (x, y) -> {
                    final String n = clean(name.getText().toString());
                    toast("Making the PDF…");
                    bg.post(() -> {
                        boolean ok = false;
                        File made = null;
                        try { made = pdf(n); ok = keep(made, n); } catch (Throwable t) { android.util.Log.w("PixelSuite", "scan: save", t); }
                        final boolean fine = ok;
                        final File out = made;
                        if (thenShare) { main.post(() -> { if (!fine || out == null) { toast("Couldn't save the PDF"); return; } if (big != null) bloom(big); send(out); }); return; }
                        main.post(() -> { toast(fine ? (PdfKit.canFiles() ? "Saved to Pixel PDF" : "Saved to Download") : "Couldn't save the PDF"); if (fine && big != null) bloom(big); if (fine && fromApp) main.postDelayed(this::finish, 350); });
                    });
                }).show();
    }

    private boolean keep(File f, String name) throws Exception {
        if (PdfKit.canFiles()) {
            File to = PdfKit.unique(PdfKit.library(), name, ".pdf");
            PdfKit.copy(new FileInputStream(f), to);
            PdfKit.scan(this, to);
            return true;
        }
        OutputStream o = null;
        try {
            ContentValues v = new ContentValues();
            v.put("_display_name", name + ".pdf"); v.put("mime_type", "application/pdf"); v.put("relative_path", "Download/");
            Uri made = getContentResolver().insert(Uri.parse("content://media/external/downloads"), v);
            if (made != null) o = getContentResolver().openOutputStream(made);
        } catch (Throwable ignored) { }
        if (o == null) {
            File folder = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS);
            folder.mkdirs();
            File to = new File(folder, name + ".pdf");
            for (int i = 2; to.exists() && i < 200; i++) to = new File(folder, name + " (" + i + ").pdf");
            o = new FileOutputStream(to);
        }
        InputStream in = new FileInputStream(f);
        byte[] buf = new byte[65536]; int n;
        while ((n = in.read(buf)) > 0) o.write(buf, 0, n);
        in.close(); o.close();
        return true;
    }

    private void share() { name(true); }

    private void send(File f) {
        try {
            Uri u = PdfHost.uri(f.getPath());
            Intent i = new Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM, u).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            i.setClipData(ClipData.newRawUri("", u));
            startActivity(Intent.createChooser(i, I18n.cs("Share")).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
        } catch (Throwable t) { toast("Couldn't open it"); }
    }

    static Bitmap load(File f, int maxSide) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getPath(), o);
            int s = 1;
            while (Math.max(o.outWidth, o.outHeight) / s > maxSide) s *= 2;
            o = new BitmapFactory.Options(); o.inSampleSize = s; o.inPreferredConfig = Bitmap.Config.ARGB_8888;
            Bitmap b = BitmapFactory.decodeFile(f.getPath(), o);
            if (b == null) return null;
            int turn = 0;
            try {
                int e = new ExifInterface(f.getPath()).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1);
                turn = e == 6 ? 90 : e == 3 ? 180 : e == 8 ? 270 : 0;
            } catch (Throwable ignored) { }
            if (turn == 0) return b;
            Matrix m = new Matrix(); m.postRotate(turn);
            Bitmap r = Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), m, true);
            if (r != b) b.recycle();
            return r;
        } catch (Throwable t) { return null; }
    }

    static Bitmap warp(Bitmap src, float[] q, int turn, int maxSide) {
        float w = src.getWidth(), h = src.getHeight();
        float[] from = new float[8];
        for (int i = 0; i < 8; i++) from[i] = q[i] * (i % 2 == 0 ? w : h);
        double top = Math.hypot(from[2] - from[0], from[3] - from[1]), bottom = Math.hypot(from[4] - from[6], from[5] - from[7]);
        double left = Math.hypot(from[6] - from[0], from[7] - from[1]), right = Math.hypot(from[4] - from[2], from[5] - from[3]);
        float ow = (float) Math.max(top, bottom), oh = (float) Math.max(left, right);
        if (ow < 8 || oh < 8) { ow = w; oh = h; from = new float[]{0, 0, w, 0, w, h, 0, h}; }
        float k = Math.min(1f, maxSide / Math.max(ow, oh));
        int tw = Math.max(1, Math.round(ow * k)), th = Math.max(1, Math.round(oh * k));
        Matrix m = new Matrix();
        m.setPolyToPoly(from, 0, new float[]{0, 0, tw, 0, tw, th, 0, th}, 0, 4);
        Bitmap outB = Bitmap.createBitmap(tw, th, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(outB);
        c.drawColor(Color.WHITE);
        c.drawBitmap(src, m, new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG));
        if (turn % 360 == 0) return outB;
        Matrix r = new Matrix(); r.postRotate(turn);
        Bitmap turned = Bitmap.createBitmap(outB, 0, 0, tw, th, r, true);
        if (turned != outB) outB.recycle();
        return turned;
    }

    static float[] detect(Bitmap b) {
        try {
            int w = b.getWidth(), h = b.getHeight(), n = w * h;
            if (w < 24 || h < 24) return null;
            int[] px = new int[n];
            b.getPixels(px, 0, w, 0, 0, w, h);
            int[] g = new int[n], hist = new int[256];
            for (int i = 0; i < n; i++) { int p = px[i]; int v = (((p >> 16) & 255) * 77 + ((p >> 8) & 255) * 150 + (p & 255) * 29) >> 8; g[i] = v; hist[v]++; }
            long sum = 0; for (int i = 0; i < 256; i++) sum += (long) i * hist[i];
            long sumB = 0; int wB = 0, cut = 127; double bestV = -1;
            for (int t = 0; t < 256; t++) {
                wB += hist[t]; if (wB == 0) continue;
                int wF = n - wB; if (wF == 0) break;
                sumB += (long) t * hist[t];
                double mB = sumB / (double) wB, mF = (sum - sumB) / (double) wF, v = (double) wB * wF * (mB - mF) * (mB - mF);
                if (v > bestV) { bestV = v; cut = t; }
            }
            int[] mark = new int[n], stack = new int[n];
            int bestLabel = 0, bestSize = 0, label = 0;
            for (int s = 0; s < n; s++) {
                if (g[s] <= cut || mark[s] != 0) continue;
                label++;
                int top = 0, size = 0;
                stack[top++] = s; mark[s] = label;
                while (top > 0) {
                    int at = stack[--top]; size++;
                    int x = at % w, y = at / w;
                    if (x > 0 && mark[at - 1] == 0 && g[at - 1] > cut) { mark[at - 1] = label; stack[top++] = at - 1; }
                    if (x < w - 1 && mark[at + 1] == 0 && g[at + 1] > cut) { mark[at + 1] = label; stack[top++] = at + 1; }
                    if (y > 0 && mark[at - w] == 0 && g[at - w] > cut) { mark[at - w] = label; stack[top++] = at - w; }
                    if (y < h - 1 && mark[at + w] == 0 && g[at + w] > cut) { mark[at + w] = label; stack[top++] = at + w; }
                }
                if (size > bestSize) { bestSize = size; bestLabel = label; }
            }
            if (bestSize < n * 0.12f || bestSize > n * 0.97f) return null;
            int tl = Integer.MAX_VALUE, br = Integer.MIN_VALUE, tr = Integer.MIN_VALUE, bl = Integer.MIN_VALUE;
            float[] q = new float[8];
            for (int i = 0; i < n; i++) {
                if (mark[i] != bestLabel) continue;
                int x = i % w, y = i / w;
                if (x + y < tl) { tl = x + y; q[0] = x; q[1] = y; }
                if (x + y > br) { br = x + y; q[4] = x; q[5] = y; }
                if (x - y > tr) { tr = x - y; q[2] = x; q[3] = y; }
                if (y - x > bl) { bl = y - x; q[6] = x; q[7] = y; }
            }
            double a = Math.hypot(q[2] - q[0], q[3] - q[1]), c = Math.hypot(q[4] - q[6], q[5] - q[7]), e = Math.hypot(q[6] - q[0], q[7] - q[1]), f = Math.hypot(q[4] - q[2], q[5] - q[3]);
            if (Math.min(a, c) < w * 0.25 || Math.min(e, f) < h * 0.25) return null;
            for (int i = 0; i < 8; i++) q[i] = Math.max(0f, Math.min(1f, (q[i] + 0.5f) / (i % 2 == 0 ? w : h)));
            return q;
        } catch (Throwable t) { return null; }
    }

    static Bitmap filtered(Bitmap base, String mode) {
        if (base == null) return null;
        if ("original".equals(mode)) return base.copy(Bitmap.Config.ARGB_8888, false);
        int w = base.getWidth(), h = base.getHeight(), n = w * h;
        int[] px = new int[n];
        base.getPixels(px, 0, w, 0, 0, w, h);
        if ("auto".equals(mode)) {
            int[][] hist = new int[3][256];
            for (int i = 0; i < n; i += 3) { int p = px[i]; hist[0][(p >> 16) & 255]++; hist[1][(p >> 8) & 255]++; hist[2][p & 255]++; }
            int[][] map = new int[3][256];
            int total = (n + 2) / 3;
            for (int ch = 0; ch < 3; ch++) {
                int lo = 0, hi = 255, acc = 0;
                for (int v = 0; v < 256; v++) { acc += hist[ch][v]; if (acc >= total * 0.01f) { lo = v; break; } }
                acc = 0;
                for (int v = 255; v >= 0; v--) { acc += hist[ch][v]; if (acc >= total * 0.03f) { hi = v; break; } }
                if (hi - lo < 24) { lo = 0; hi = 255; }
                for (int v = 0; v < 256; v++) map[ch][v] = Math.max(0, Math.min(255, (v - lo) * 255 / (hi - lo)));
            }
            for (int i = 0; i < n; i++) { int p = px[i]; px[i] = 0xff000000 | (map[0][(p >> 16) & 255] << 16) | (map[1][(p >> 8) & 255] << 8) | map[2][p & 255]; }
        } else {
            int[] light = paper(base, w, h);
            boolean gray = "gray".equals(mode), bw = "bw".equals(mode), soft = "lighten".equals(mode);
            int[] curve = new int[256];
            for (int v = 0; v < 256; v++) curve[v] = (int) Math.round(255 * Math.pow(v / 255.0, soft ? 0.74 : 1.55));
            for (int i = 0; i < n; i++) {
                int p = px[i], r = (p >> 16) & 255, g = (p >> 8) & 255, b = p & 255;
                int back = Math.max(40, light[i] & 255);
                if (soft) {
                    int k = 255 * 256 / ((back * 3 + 255) / 4 + 1);
                    r = curve[Math.min(255, r * k >> 8)]; g = curve[Math.min(255, g * k >> 8)]; b = curve[Math.min(255, b * k >> 8)];
                } else if (gray || bw) {
                    int v = (r * 77 + g * 150 + b * 29) >> 8;
                    v = Math.min(255, v * 262 / back);
                    if (bw) v = v > 205 ? 255 : v < 135 ? 0 : (v - 135) * 255 / 70;
                    else v = curve[v];
                    r = g = b = v;
                } else {
                    r = curve[Math.min(255, r * 262 / back)]; g = curve[Math.min(255, g * 262 / back)]; b = curve[Math.min(255, b * 262 / back)];
                }
                px[i] = 0xff000000 | (r << 16) | (g << 8) | b;
            }
        }
        Bitmap made = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        made.setPixels(px, 0, w, 0, 0, w, h);
        return made;
    }

    private static int[] paper(Bitmap base, int w, int h) {
        int sw = Math.max(4, w / 28), sh = Math.max(4, h / 28);
        Bitmap small = Bitmap.createScaledBitmap(base, sw, sh, true);
        int[] s = new int[sw * sh];
        small.getPixels(s, 0, sw, 0, 0, sw, sh);
        if (small != base) small.recycle();
        int[] lum = new int[sw * sh], grown = new int[sw * sh];
        for (int i = 0; i < s.length; i++) { int p = s[i]; lum[i] = (((p >> 16) & 255) * 77 + ((p >> 8) & 255) * 150 + (p & 255) * 29) >> 8; }
        for (int y = 0; y < sh; y++) for (int x = 0; x < sw; x++) {
            int best = 0;
            for (int dy = -2; dy <= 2; dy++) for (int dx = -2; dx <= 2; dx++) {
                int xx = x + dx, yy = y + dy;
                if (xx < 0 || yy < 0 || xx >= sw || yy >= sh) continue;
                if (lum[yy * sw + xx] > best) best = lum[yy * sw + xx];
            }
            grown[y * sw + x] = 0xff000000 | (best << 16) | (best << 8) | best;
        }
        Bitmap map = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888);
        map.setPixels(grown, 0, sw, 0, 0, sw, sh);
        Bitmap wide = Bitmap.createScaledBitmap(map, w, h, true);
        int[] outL = new int[w * h];
        wide.getPixels(outL, 0, w, 0, 0, w, h);
        if (wide != map) wide.recycle();
        map.recycle();
        return outL;
    }
}
