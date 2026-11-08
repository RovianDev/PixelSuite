package io.github.pixelsuite;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.View;

final class AppSurfaces {
    private AppSurfaces() {}
    private static volatile boolean loaded, superDark;
    private static boolean observing;
    private static final java.util.Map<View,Boolean> views=new java.util.WeakHashMap<>();
    static void watch(View v){views.put(v,Boolean.TRUE);}
    private static final java.util.Map<Drawable,Boolean> surfaces=new java.util.WeakHashMap<>();
    private static final java.util.Map<android.view.Window,Float> windows=new java.util.WeakHashMap<>();
    static void watch(Drawable d){surfaces.put(d,Boolean.TRUE);}
    static void dim(android.view.Window w,float normal){ToastMotion.watchWindow(w);windows.put(w,normal);try{w.setDimAmount(active(w.getContext())?SuperDarkPalette.DIM:normal);}catch(Throwable closing){}}
    private static void observe(Context c){if(observing)return;observing=true;try{final Context app=c.getApplicationContext();app.getContentResolver().registerContentObserver(android.provider.Settings.Secure.getUriFor(SuperDark.KEY),false,new android.database.ContentObserver(new android.os.Handler(android.os.Looper.getMainLooper())){public void onChange(boolean self){refresh(app);}});}catch(Throwable ignored){observing=false;}}
    static boolean enabled(boolean dark,boolean setting){return dark&&setting;}
    static void refresh(Context c){superDark=Feature.on(c,SuperDark.KEY);loaded=true;observe(c);for(View v:new java.util.ArrayList<View>(views.keySet()))if(v!=null)v.invalidate();for(Drawable d:new java.util.ArrayList<Drawable>(surfaces.keySet()))if(d!=null)d.invalidateSelf();for(java.util.Map.Entry<android.view.Window,Float> e:new java.util.ArrayList<java.util.Map.Entry<android.view.Window,Float>>(windows.entrySet())){android.view.Window w=e.getKey();if(w==null)continue;android.view.View decor=w.peekDecorView();if(decor!=null&&!decor.isAttachedToWindow())continue;try{w.setDimAmount(active(w.getContext())?SuperDarkPalette.DIM:e.getValue());}catch(Throwable closing){}}}
    static boolean active(Context c){if(!loaded)refresh(c);return enabled(FrostedSurface.dark(c),superDark);}
    static int normalCard(Context c){return LauncherFeatures.surface(c,FrostedSurface.dark(c)?"system_surface_container_high_dark":"system_surface_bright_light",FrostedSurface.dark(c)?0xff2b2930:0xfffef7ff);}
    static GradientDrawable card(Context c,float radius){return new Surface(c,radius,SuperDarkPalette.BACKGROUND,normalCard(c),false);}
    static GradientDrawable menu(Context c,float radius){return new Surface(c,radius,SuperDarkPalette.BACKGROUND,FrostedSurface.dark(c)?0xff202024:0xfff4f1f5,true);}
    static GradientDrawable field(Context c,float radius){return new Surface(c,radius,0xff1a1a1a,normalCard(c),false);}
    static GradientDrawable sheet(Context c,float radius){return new Surface(c,radius,SuperDarkPalette.BACKGROUND,normalCard(c),true);}
    static Drawable accountButton(Context c,float radius){return new SwitchSurface(c,new ThemeGlass(c,radius,ThemeGlass.FIELD),field(c,radius));}
    static GradientDrawable dialog(Context c,float radius){return new Surface(c,radius,SuperDarkPalette.BACKGROUND,normalCard(c),true);}
    static Drawable role(Context c,Drawable normal,float radius,int color,boolean floating){return new SwitchSurface(c,normal,new Surface(c,radius,color,normalCard(c),floating));}
    static Drawable support(Context c,Drawable normal,float radius){return new SwitchSurface(c,normal,new Surface(c,radius,SuperDarkPalette.BACKGROUND,normalCard(c),false));}
    static Drawable pill(Context c,Drawable normal){return new SwitchSurface(c,normal,new Surface(c,24,0xff000000,0xff000000,true));}
    static void invalidateTree(View v){if(v.getBackground()!=null)v.getBackground().invalidateSelf();v.invalidate();if(v instanceof android.view.ViewGroup){android.view.ViewGroup g=(android.view.ViewGroup)v;for(int i=0;i<g.getChildCount();i++)invalidateTree(g.getChildAt(i));}}
    private static final class Surface extends GradientDrawable {
        final Context context;final int black,normal;final float radius;final boolean floating;
        final SuperDarkPalette.Rim exactRim=new SuperDarkPalette.Rim();final Paint rim=new Paint(Paint.ANTI_ALIAS_FLAG);int last;final RectF frame=new RectF();
        Surface(Context c,float radius,int black,int normal,boolean floating){context=c;this.radius=radius*c.getResources().getDisplayMetrics().density;this.black=black;this.normal=normal;this.floating=floating;watch(this);last=active(c)?black:normal;setColor(last);setCornerRadius(this.radius);float d=c.getResources().getDisplayMetrics().density;rim.setStyle(Paint.Style.STROKE);rim.setStrokeWidth(2.4f*d);rim.setMaskFilter(new BlurMaskFilter(2*d,BlurMaskFilter.Blur.NORMAL));}
        @Override protected void onBoundsChange(Rect bounds){super.onBoundsChange(bounds);frame.set(bounds);float inset=2.4f*context.getResources().getDisplayMetrics().density;frame.inset(inset,inset);rim.setShader(new LinearGradient(0,frame.top,0,Math.max(frame.top+1,frame.bottom),new int[]{0x30ffffff,0x12ffffff,0x0cffffff,0x16ffffff},new float[]{0,.35f,.75f,1},Shader.TileMode.CLAMP));}
        @Override public void draw(Canvas canvas){boolean on=active(context);int color=on?black:normal;if(last!=color){last=color;super.setColor(color);}super.draw(canvas);if(on&&floating){exactRim.draw(canvas,getBounds(),radius,context.getResources().getDisplayMetrics().density);}}

    }
    private static final class SwitchSurface extends Drawable {
        final Context context;final Drawable normal,black;
        SwitchSurface(Context c,Drawable normal,Drawable black){context=c;this.normal=normal;this.black=black;watch(this);}
        private Drawable shown(){return active(context)?black:normal;}
        @Override protected void onBoundsChange(Rect b){normal.setBounds(b);black.setBounds(b);}
        @Override public void draw(Canvas c){shown().draw(c);}
        @Override public void setAlpha(int alpha){normal.setAlpha(alpha);black.setAlpha(alpha);}
        @Override public void setColorFilter(ColorFilter filter){normal.setColorFilter(filter);black.setColorFilter(filter);}
        @Override public int getOpacity(){return PixelFormat.OPAQUE;}
        @Override public void getOutline(Outline outline){shown().getOutline(outline);}
        @Override public boolean isStateful(){return normal.isStateful();}
        @Override protected boolean onStateChange(int[] state){return normal.setState(state);}
    }
}
