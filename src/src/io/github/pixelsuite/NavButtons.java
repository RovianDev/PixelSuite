package io.github.pixelsuite;

import android.animation.ValueAnimator;
import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;

final class NavButtons {
    private NavButtons() {}

    static final String KEY="pixelsuite_navbuttons";
    static final String LEFT="pixelsuite_navbuttons_left";
    static final String RIGHT="pixelsuite_navbuttons_right";
    static final String SHOW="pixelsuite_navbuttons_show";
    static final String HOLD_HOME="pixelsuite_navbuttons_hold_home";
    static final int HOME = 0, BACK = 1, RECENT = 2, HIDE = 3;

    private static boolean installed;
    private static WeakReference<Bar> bar = new WeakReference<Bar>(null);

    static final android.graphics.Rect[] zones = new android.graphics.Rect[2];
    static volatile int zonesRotation = -1;

    static boolean onButton(MotionEvent e, int index) {
        try {
            Bar b = bar.get();
            if (b == null || !b.isAttachedToWindow() || b.getDisplay() == null || b.getDisplay().getRotation() != zonesRotation) return false;
            if (index < 0 || index >= e.getPointerCount()) index = 0;
            float rx, ry;
            if (index == 0) { rx = e.getRawX(); ry = e.getRawY(); }
            else if (android.os.Build.VERSION.SDK_INT >= 29) {
                rx = (Float) MotionEvent.class.getMethod("getRawX", int.class).invoke(e, index);
                ry = (Float) MotionEvent.class.getMethod("getRawY", int.class).invoke(e, index);
            } else return false;
            for (android.graphics.Rect r : zones) {
                if (r != null && r.contains(Math.round(rx), Math.round(ry))) return true;
            }
        } catch (Throwable ignored) { }
        return false;
    }

    static synchronized void install(ClassLoader cl) {
        if (installed) return;
        installed = true;
        try {
            final Class<?> handle = Xp.findClass("com.android.launcher3.taskbar.StashedHandleView", cl);
            Xp.hookAllConstructors(handle, new Xp.Callback() {
                @Override protected void afterHookedMethod(Xp.Param p) {
                    final View h = (View) p.thisObject;
                    h.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                        @Override public void onViewAttachedToWindow(View v) { try { attach(h); } catch (Throwable t) { } }
                        @Override public void onViewDetachedFromWindow(View v) { }
                    });
                }
            });
            Xp.hookAllMethods(handle, "updateHandleColor", new Xp.Callback() {
                @Override protected void afterHookedMethod(Xp.Param p) {
                    Bar b = bar.get();
                    if (b != null && b.handle.get() == p.thisObject && p.args.length >= 2) b.colour((Boolean) p.args[0], (Boolean) p.args[1]);
                }
            });
            try {
                Xp.hookAllMethods(Xp.findClass("com.android.launcher3.taskbar.NavbarButtonsViewController", cl), "updateStateForFlag", new Xp.Callback() {
                    @Override protected void afterHookedMethod(Xp.Param p) {
                        Bar b=bar.get();
                        if(b!=null&&b.isAttachedToWindow())b.follow.onPreDraw();
                    }
                });
            } catch(Throwable ignored) { }
            try {
                Xp.hookAllMethods(Xp.findClass("com.android.quickstep.RotationTouchHelper", cl), "isInSwipeUpTouchRegion", new Xp.Callback() {
                    @Override protected void beforeHookedMethod(Xp.Param p) {
                        if (p.args.length < 1 || !(p.args[0] instanceof MotionEvent)) return;
                        int index = p.args.length > 1 && p.args[1] instanceof Integer ? (Integer) p.args[1] : 0;
                        if (onButton((MotionEvent) p.args[0], index)) {
                            p.setResult(false);
                            forgetTouch(p.thisObject);
                        }
                    }
                });
            } catch (Throwable t) { }
        } catch (Throwable t) { }
    }

    private static volatile Field sTransformerField, sLastRectField;
    static void forgetTouch(Object helper) {
        try {
            if (helper == null) return;
            if (sTransformerField == null) sTransformerField = field(helper.getClass(), "mOrientationTouchTransformer", "OrientationTouchTransformer");
            Object t = sTransformerField == null ? null : sTransformerField.get(helper);
            if (t == null) return;
            if (sLastRectField == null) sLastRectField = field(t.getClass(), "mLastRectTouched", "OrientationRectF");
            if (sLastRectField != null) sLastRectField.set(t, null);
        } catch (Throwable ignored) { }
    }
    private static Field field(Class<?> c, String name, String typeEnd) {
        for (Class<?> k = c; k != null && k != Object.class; k = k.getSuperclass()) {
            try { Field f = k.getDeclaredField(name); f.setAccessible(true); return f; } catch (Throwable ignored) { }
        }
        Field found = null;
        for (Class<?> k = c; k != null && k != Object.class; k = k.getSuperclass()) {
            for (Field f : k.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers()) || !f.getType().getName().endsWith(typeEnd)) continue;
                if (found != null) return null;
                found = f;
            }
        }
        if (found != null) found.setAccessible(true);
        return found;
    }

    private static void attach(View handle) throws Exception {
        if (!(handle.getRootView() instanceof ViewGroup)) return;
        Bar old = bar.get();
        if (old != null && old.handle.get() == handle && old.isAttachedToWindow()) return;
        if (old != null && old.getParent() instanceof ViewGroup) ((ViewGroup) old.getParent()).removeView(old);
        ViewGroup root = (ViewGroup) handle.getRootView();
        Bar b = new Bar(handle.getContext(), handle);
        root.addView(b, new FrameLayout.LayoutParams(-1, -1));
        bar = new WeakReference<Bar>(b);
    }

    private static int side(ContentResolver cr, String key, int fallback) {
        String v = Settings.Secure.getString(cr, key);
        if (v == null) return fallback;
        switch (v) {
            case "home": return HOME;
            case "back": return BACK;
            case "recent": return RECENT;
            case "hide": return HIDE;
            default: return fallback;
        }
    }

    static final class Bar extends FrameLayout {
        final WeakReference<View> handle;
        final Button left, right;
        boolean on, keyboardOnly, imeVisible;
        String hold = "circle";
        int colour = 0xffffffff;
        ValueAnimator colourAnim;
        private final ContentObserver observer;
        private final Handler actionHandler=new Handler(Looper.getMainLooper());
        Panel leftPanel,rightPanel;final int[] handleScreen=new int[2];

        Bar(Context c, View h) {
            super(c);
            handle = new WeakReference<View>(h);
            setClipChildren(false); setClipToPadding(false);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            left = new Button(c, this); right = new Button(c, this);
            addView(left, new LayoutParams(0, 0, Gravity.TOP | Gravity.START));
            addView(right, new LayoutParams(0, 0, Gravity.TOP | Gravity.START));
            observer = new ContentObserver(new Handler(Looper.getMainLooper())) {
                @Override public void onChange(boolean self) { read(); }
            };
            read();
        }

        @Override protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            ContentResolver cr = getContext().getContentResolver();
            for (String k : new String[]{KEY, LEFT, RIGHT, SHOW, HOLD_HOME}) cr.registerContentObserver(Settings.Secure.getUriFor(k), false, observer);
            getViewTreeObserver().addOnPreDrawListener(follow);
            try {
                android.content.IntentFilter f = new android.content.IntentFilter();
                f.addAction(android.content.Intent.ACTION_SCREEN_OFF); f.addAction(android.content.Intent.ACTION_SCREEN_ON); f.addAction(android.content.Intent.ACTION_USER_PRESENT);
                getContext().registerReceiver(lockWatch, f);
            } catch (Throwable t) { }
        }

        @Override protected void onConfigurationChanged(android.content.res.Configuration c) {
            super.onConfigurationChanged(c);
            turned();
        }
        private int lastRotation = -1;
        void turned() {
            zones[0] = null; zones[1] = null; zonesRotation = -1;
            if (leftPanel != null) leftPanel.show(false);
            if (rightPanel != null) rightPanel.show(false);
            requestLayout(); invalidate();
            postDelayed(new Runnable() { @Override public void run() { follow.onPreDraw(); } }, 120);
            postDelayed(new Runnable() { @Override public void run() { follow.onPreDraw(); } }, 450);
            postDelayed(new Runnable() { @Override public void run() { follow.onPreDraw(); } }, 1000);
        }

        @Override protected void onDetachedFromWindow() {
            getContext().getContentResolver().unregisterContentObserver(observer);
            getViewTreeObserver().removeOnPreDrawListener(follow);
            try { getContext().unregisterReceiver(lockWatch); } catch (Throwable ignored) { }
            if(leftPanel!=null)leftPanel.close();if(rightPanel!=null)rightPanel.close();
            super.onDetachedFromWindow();
        }

        void read() {
            ContentResolver cr = getContext().getContentResolver();
            on = "1".equals(Settings.Secure.getString(cr, KEY));
            left.kind = side(cr, LEFT, BACK);
            right.kind = side(cr, RIGHT, RECENT);
            keyboardOnly = false;
            String h = Settings.Secure.getString(cr, HOLD_HOME);
            hold = h == null ? "circle" : h;
            left.invalidate(); right.invalidate();
            requestLayout(); invalidate();
            View hv = handle.get();
            if (hv != null && hv.getRootView() != null) hv.getRootView().requestLayout();
        }

        void colour(boolean regionDark, boolean animate) {
            int to = regionDark ? 0xffffffff : 0xff1f1f1f;
            try {
                View hv = handle.get();
                if (hv != null) {
                    Field f = hv.getClass().getField(regionDark ? "mStashedHandleLightColor" : "mStashedHandleDarkColor");
                    to = 0xff000000 | f.getInt(hv);
                }
            } catch (Throwable ignored) { }
            if (colourAnim != null) colourAnim.cancel();
            if (!animate) { colour = to; left.invalidate(); right.invalidate(); return; }
            colourAnim = ValueAnimator.ofArgb(colour, to);
            colourAnim.setDuration(120);
            colourAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override public void onAnimationUpdate(ValueAnimator a) { colour = (Integer) a.getAnimatedValue(); left.invalidate(); right.invalidate(); }
            });
            colourAnim.start();
        }

        Object controllers() {
            Context c = handle.get() != null ? handle.get().getContext() : getContext();
            for (int i = 0; i < 6 && c != null; i++) {
                try { return Xp.getObjectField(c, "mControllers"); } catch (Throwable ignored) { }
                c = c instanceof android.content.ContextWrapper ? ((android.content.ContextWrapper) c).getBaseContext() : null;
            }
            throw new IllegalStateException("no taskbar controllers");
        }

        boolean locked() {
            try {
                android.app.KeyguardManager km = (android.app.KeyguardManager) getContext().getSystemService(Context.KEYGUARD_SERVICE);
                android.os.PowerManager pm = (android.os.PowerManager) getContext().getSystemService(Context.POWER_SERVICE);
                return (km != null && km.isKeyguardLocked()) || (pm != null && !pm.isInteractive());
            } catch (Throwable t) { return false; }
        }

        final android.content.BroadcastReceiver lockWatch = new android.content.BroadcastReceiver() {
            @Override public void onReceive(Context c, android.content.Intent i) {
                follow.onPreDraw();
                postDelayed(new Runnable() { @Override public void run() { follow.onPreDraw(); } }, 300);
                postDelayed(new Runnable() { @Override public void run() { follow.onPreDraw(); } }, 900);
            }
        };

        boolean keyboardUp() {
            try {
                Object nav = Xp.getObjectField(controllers(), "navbarButtonsViewController");
                return (((Integer) Xp.getObjectField(nav, "mState")) & 2) != 0;
            } catch (Throwable t) { return false; }
        }

        final ViewTreeObserver.OnPreDrawListener follow = new ViewTreeObserver.OnPreDrawListener() {
            @Override public boolean onPreDraw() {
                View hv = handle.get();
                int rotation = getDisplay() == null ? -1 : getDisplay().getRotation();
                if (lastRotation != -1 && rotation != lastRotation) { lastRotation = rotation; turned(); return true; }
                lastRotation = rotation;
                imeVisible=keyboardUp();left.keyboard(imeVisible);right.keyboard(imeVisible);
                boolean show = on && hv != null && (imeVisible || (hv.isShown() && hv.getAlpha() > 0.01f)) && (!keyboardOnly || imeVisible) && !locked();
                float dens = getResources().getDisplayMetrics().density;
                int size = Math.round(Math.min(48 * dens, Math.max(28 * dens, getHeight() > 0 ? getHeight() : 48 * dens)));
                float cy = getHeight() / 2f;
                if (hv != null) {
                    int[] a = new int[2], b = new int[2];
                    hv.getLocationInWindow(a); getLocationInWindow(b);
                    cy = a[1] - b[1] + hv.getHeight() / 2f;
                }
                place(left, show && left.kind != HIDE, 0.21f, cy, size, hv);
                place(right, show && right.kind != HIDE, 0.79f, cy, size, hv);
                return true;
            }
        };

        private void place(Button b, boolean show, float at, float cy, int size, View hv) {
            Panel panel=b==left?leftPanel:rightPanel;
            if(!show){b.setVisibility(GONE);if(panel!=null)panel.show(false);return;}
            try{
                if(panel==null){panel=new Panel(b);if(b==left)leftPanel=panel;else rightPanel=panel;}
                View h=handle.get();if(h==null)return;h.getLocationOnScreen(handleScreen);
                float density=getResources().getDisplayMetrics().density;
                int touchWidth=Math.max(size,Math.round(56*density)),touchHeight=Math.max(size,Math.round(44*density));
                int x=Math.round(getWidth()*at-touchWidth/2f),y=Math.round(handleScreen[1]+h.getHeight()/2f-touchHeight/2f);
                panel.place(x,y,touchWidth,touchHeight);zonesRotation=getDisplay()==null?-1:getDisplay().getRotation();b.setVisibility(VISIBLE);b.setAlpha(imeVisible||hv==null?1:hv.getAlpha());panel.show(true);
            }catch(Throwable error){b.setVisibility(GONE);}
        }
        Context taskbarContext(){Context c=getContext();for(int i=0;i<6&&c!=null;i++){try{Xp.getObjectField(c,"mControllers");return c;}catch(Throwable ignored){}c=c instanceof android.content.ContextWrapper?((android.content.ContextWrapper)c).getBaseContext():null;}return getContext();}
        final class Panel {
            final FrameLayout root;final android.view.WindowManager manager;final android.view.WindowManager.LayoutParams params;boolean added;int x=-1,y=-1,width=-1,height=-1;final int slot;
            Panel(Button button){
                slot=button==left?0:1;
                Context context=taskbarContext();android.view.WindowManager baseManager=(android.view.WindowManager)context.getSystemService(Context.WINDOW_SERVICE);Context windowContext=(Context)Xp.callMethod(context,"createWindowContext",baseManager.getDefaultDisplay(),2024,null);manager=(android.view.WindowManager)windowContext.getSystemService(Context.WINDOW_SERVICE);
                params=(android.view.WindowManager.LayoutParams)Xp.callMethod(context,"createDefaultWindowLayoutParams",2024,"PixelSuiteNavButtons"+(button==left?"Left":"Right"));
                params.token=null;
                params.flags|=android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL|android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
                params.flags&=~android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;params.gravity=Gravity.TOP|Gravity.LEFT;
                try{params.getClass().getMethod("setFitInsetsTypes",int.class).invoke(params,0);}catch(Throwable ignored){}
                root=new FrameLayout(windowContext);root.setClipChildren(false);root.setClipToPadding(false);
                if(button.getParent() instanceof ViewGroup)((ViewGroup)button.getParent()).removeView(button);
                root.addView(button,new FrameLayout.LayoutParams(-1,-1));
            }
            void place(int px,int py,int w,int h){
                if(zones[slot]==null)zones[slot]=new android.graphics.Rect();
                zones[slot].set(px,py,px+w,py+h);
                if(added&&x==px&&y==py&&width==w&&height==h)return;
                x=px;y=py;width=w;height=h;params.x=x;params.y=y;params.width=w;params.height=h;
                if(!added){manager.addView(root,params);added=true;}else manager.updateViewLayout(root,params);
            }
            void show(boolean value){
                if(!value)zones[slot]=null;int visibility=value?VISIBLE:GONE;if(root.getVisibility()!=visibility)root.setVisibility(visibility);
                int flags=value?params.flags&~android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE:params.flags|android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
                if(flags!=params.flags){params.flags=flags;if(added){try{manager.updateViewLayout(root,params);}catch(Throwable ignored){}}}
            }
            void close(){zones[slot]=null;if(added){added=false;try{manager.removeViewImmediate(root);}catch(Throwable ignored){}}}
        }

        void act(final Button b) {
            final int kind=b.kind;actionHandler.post(()->execute(kind));
        }
        private void execute(int kind) {
            int code = kind == BACK ? 1 : kind == HOME ? 2 : kind == RECENT ? 4 : 0;
            if (code == 0) return;
            try {
                Object nav = Xp.getObjectField(controllers(), "navButtonController");
                nav.getClass().getMethod("onButtonClick", int.class).invoke(nav, code);
            } catch (Throwable t) { }
        }

        void circleHint(boolean start) {
            try {
                Object nav=Xp.getObjectField(controllers(),"navButtonController");
                Object invoker=Xp.getObjectField(nav,"mContextualSearchInvoker");
                Object haptics=Xp.getObjectField(invoker,"contextualSearchHapticManager");
                if(start)Xp.callMethod(haptics,"vibrateForSearchHint");
                else Xp.callMethod(Xp.getObjectField(haptics,"vibratorWrapper"),"cancelVibrate");
            } catch(Throwable ignored) { }
        }

        boolean hold(Button b) {
            if (b.kind != HOME || "none".equals(hold)) return false;
            try {
                Object nav = Xp.getObjectField(controllers(), "navButtonController");
                if ("gemini".equals(hold)) {
                    Object proxy = Xp.getObjectField(nav, "mSystemUiProxy");
                    Bundle args = new Bundle();
                    args.putInt("invocation_type", 6);
                    proxy.getClass().getMethod("startAssistant", Bundle.class).invoke(proxy, args);
                    b.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                    return true;
                }
                if(Boolean.TRUE.equals(Xp.getObjectField(nav,"mScreenPinned")))return false;
                Object proxy=Xp.getObjectField(nav,"mSystemUiProxy");long flags=((Number)Xp.callMethod(proxy,"getLastSystemUiStateFlags")).longValue();
                if((flags&(584L|2052L))!=0)return false;
                Object invoker=Xp.getObjectField(nav,"mContextualSearchInvoker");if(invoker==null)return false;
                Class<?> builderType=Class.forName("android.app.contextualsearch.ContextualSearchConfig$Builder");Object builder=builderType.getConstructor().newInstance();
                android.graphics.Rect bounds=new android.graphics.Rect();int[] at=new int[2];b.getLocationOnScreen(at);bounds.set(at[0],at[1],at[0]+b.getWidth(),at[1]+b.getHeight());
                builderType.getMethod("setSourceBounds",android.graphics.Rect.class).invoke(builder,bounds);builderType.getMethod("setDisplayId",int.class).invoke(builder,b.getDisplay().getDisplayId());
                Object config=builderType.getMethod("build").invoke(builder);
                boolean launched=Boolean.TRUE.equals(Xp.callMethod(invoker,"invokeContextualSearchUncheckedWithHaptic",2,config));
                b.circleCommitted=launched;
                return launched;
            } catch (Throwable t) { return false; }
        }
    }

    static final class Button extends View {
        final Bar bar;
        int kind = HIDE;
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Path path = new Path();
        private float pressed,imeRotation,imeTarget;
        boolean circleCommitted,circleHintActive;
        private ValueAnimator imeAnimator;
        private ValueAnimator pressAnim;

        Button(Context c, Bar bar) {
            super(c);
            this.bar = bar;
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeCap(Paint.Cap.ROUND); paint.setStrokeJoin(Paint.Join.ROUND);
            setClickable(true); setLongClickable(true);
            setHapticFeedbackEnabled(true);
            setOnClickListener(new OnClickListener() {
                @Override public void onClick(View v) { performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY); Button.this.bar.act(Button.this); }
            });
            setOnLongClickListener(new OnLongClickListener() {
                @Override public boolean onLongClick(View v) { return Button.this.bar.hold(Button.this); }
            });
        }

        @Override public boolean performHapticFeedback(int feedbackConstant) {
            if(feedbackConstant==HapticFeedbackConstants.LONG_PRESS&&kind==HOME&&"circle".equals(bar.hold))return true;
            return super.performHapticFeedback(feedbackConstant);
        }

        @Override public CharSequence getContentDescription() {
            return kind == BACK ? I18n.cs("Back") : kind == HOME ? I18n.cs("Home") : kind == RECENT ? I18n.cs("Recent apps") : null;
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            int a = e.getActionMasked();
            if (a == MotionEvent.ACTION_DOWN) {
                press(1f);circleCommitted=false;
                circleHintActive=kind==HOME&&"circle".equals(bar.hold);
                if(circleHintActive)bar.circleHint(true);
            } else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
                press(0f);
                if(circleHintActive&&!circleCommitted)bar.circleHint(false);
                circleHintActive=false;
            }
            return super.onTouchEvent(e);
        }

        void keyboard(boolean visible){float target=kind==BACK&&visible?1f:0f;if(imeTarget==target)return;imeTarget=target;if(imeAnimator!=null)imeAnimator.cancel();imeAnimator=ValueAnimator.ofFloat(imeRotation,target);imeAnimator.setDuration(180);imeAnimator.addUpdateListener(value->{imeRotation=(Float)value.getAnimatedValue();invalidate();});imeAnimator.start();}

        private void press(float to) {
            if (pressAnim != null) pressAnim.cancel();
            pressAnim = ValueAnimator.ofFloat(pressed, to);
            pressAnim.setDuration(to > 0 ? 80 : 220);
            pressAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override public void onAnimationUpdate(ValueAnimator va) { pressed = (Float) va.getAnimatedValue(); invalidate(); }
            });
            pressAnim.start();
        }

        @Override protected void onDraw(Canvas canvas) {
            float d = getResources().getDisplayMetrics().density, cx = getWidth() / 2f, cy = getHeight() / 2f;
            int colour = bar.colour;
            if (pressed > 0.01f) {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor((colour & 0x00ffffff) | (Math.round(0x2e * pressed) << 24));
                canvas.drawCircle(cx, cy, Math.min(18f*d,Math.min(getWidth(), getHeight()) / 2f), paint);
                paint.setStyle(Paint.Style.STROKE);
            }
            paint.setColor(colour);
            paint.setStrokeWidth(2f * d);
            float s = 9f * d;
            path.rewind();
            switch (kind) {
                case BACK: {
                    double angle = Math.toRadians(this == bar.right ? 90.0 * imeRotation : 180.0 - 90.0 * imeRotation);
                    float dx = (float) Math.cos(angle), dy = (float) Math.sin(angle);
                    float px = -dy, py = dx;
                    path.moveTo(cx - dx * s * 0.75f + px * s * 0.95f, cy - dy * s * 0.75f + py * s * 0.95f);
                    path.lineTo(cx + dx * s * 0.85f, cy + dy * s * 0.85f);
                    path.lineTo(cx - dx * s * 0.75f - px * s * 0.95f, cy - dy * s * 0.75f - py * s * 0.95f);
                    path.close();
                    canvas.drawPath(path, paint);
                    break;
                }
                case HOME:
                    canvas.drawCircle(cx, cy, s * 0.9f, paint);
                    break;
                case RECENT: {
                    RectF r = new RectF(cx - s * 0.8f, cy - s * 0.8f, cx + s * 0.8f, cy + s * 0.8f);
                    canvas.drawRoundRect(r, 2.5f * d, 2.5f * d, paint);
                    break;
                }
                default: break;
            }
        }
    }
}
