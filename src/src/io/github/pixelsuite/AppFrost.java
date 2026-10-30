package io.github.pixelsuite;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.view.*;
import java.lang.reflect.Method;

final class AppFrost extends Drawable {
    private final View host,content;private final Paint tint=new Paint(3);private final Path shape=new Path();private final RectF box=new RectF();
    private final int[] hostAt=new int[2],pageAt=new int[2];
    private boolean pillSurface;private int darkOpacity=0xcc;private int lightOpacity=0xcc;void lightOpacity(int value){lightOpacity=Math.max(0,Math.min(255,value));frost=frostTint();invalidateSelf();}
    private int blackRole=SuperDarkPalette.BACKGROUND;void blackRole(int role){blackRole=role;invalidateSelf();}
    private float radius;private int pageColor,blur=-1,rimMode=-1,scrollY=-1,frost,preferredBlur;private boolean released,blurredLastFrame;
    private GlassRim rim;private Object node;private Method position,begin,end,effect,draw,discard;
    private final ViewTreeObserver.OnPreDrawListener follow;
    AppFrost(View host,View content,float radius,int pageColor){this.host=host;AppSurfaces.watch(this);this.content=content;this.radius=radius;this.pageColor=pageColor;frost=frostTint();preferredBlur=FrostedSurface.depthBlurRadius(host.getContext());follow=()->{if(content.isDirty()||scrollY!=content.getScrollY()){scrollY=content.getScrollY();invalidateSelf();}return true;};content.getViewTreeObserver().addOnPreDrawListener(follow);}
    static AppFrost pill(View host,View content,float radius,int pageColor){AppFrost frost=new AppFrost(host,content,radius,pageColor);frost.blackRole(SuperDarkPalette.BACKGROUND);frost.pillSurface=true;frost.darkOpacity=0xf2;frost.lightOpacity(0xf2);return frost;}
    void radius(float value){radius=value;}
    void pageColor(int value){pageColor=value;frost=frostTint();invalidateSelf();}
    private boolean init(){
        if(node!=null)return true;if(android.os.Build.VERSION.SDK_INT<31)return false;
        try{Class<?> n=Class.forName("android.graphics.RenderNode"),fx=Class.forName("android.graphics.RenderEffect");node=n.getConstructor(String.class).newInstance("Pixel Suite app frost");
            position=n.getMethod("setPosition",int.class,int.class,int.class,int.class);begin=n.getMethod("beginRecording",int.class,int.class);end=n.getMethod("endRecording");effect=n.getMethod("setRenderEffect",fx);draw=Canvas.class.getMethod("drawRenderNode",n);discard=n.getMethod("discardDisplayList");return true;
        }catch(Throwable unavailable){node=null;return false;}
    }
    private int launcherColor(String name,int fallback){return FrostedSurface.resourceColor(host.getContext(),name,fallback);}
    private int frostTint(){boolean dark=FrostedSurface.dark(host.getContext());if(pillSurface&&dark)return (LauncherFeatures.surface(host.getContext(),"system_surface_container_high_dark",0xff303137)&0xffffff)|(darkOpacity<<24);int tone=FrostedSurface.composite(launcherColor("blur_shade_panel_fg",dark?0x522c303d:0x66dedee8),launcherColor("blur_shade_panel_bg",dark?0x520e0e0f:0x66f9f9fe));return (tone&0xffffff)|((dark?darkOpacity:lightOpacity)<<24);}
    private int radiusPx(){return preferredBlur;}
    private boolean blurAllowed(){
        Context c=host.getContext();try{Object manager=c.getSystemService(Context.WINDOW_SERVICE);return Boolean.TRUE.equals(manager.getClass().getMethod("isCrossWindowBlurEnabled").invoke(manager));}
        catch(Throwable older){return android.provider.Settings.Global.getInt(c.getContentResolver(),"disable_window_blurs",0)==0;}
    }
    private Integer solid(){
        Context c=host.getContext();if(AppSurfaces.active(c))return blackRole;
        int style=SurfaceStyle.get(c);boolean dark=FrostedSurface.dark(c);
        if(style==SurfaceStyle.OFF)return dark?FrostedSurface.offDark(c):0xffffffff;
        if(style==SurfaceStyle.STOCK)return LauncherFeatures.surface(c,dark?"system_surface_container_high_dark":"system_surface_container_high_light",dark?0xff2b2930:0xfff3edf7);
        return null;
    }
    @Override public void draw(Canvas canvas){
        if(released||getBounds().isEmpty())return;Context c=host.getContext();Rect b=getBounds();box.set(b);shape.reset();shape.addRoundRect(box,radius,radius,Path.Direction.CW);
        Integer solid=solid();boolean blurred=false;int color=frostTint();if(pillSurface){tint.setColor(color|0xff000000);canvas.drawPath(shape,tint);}
        if(solid==null&&canvas.isHardwareAccelerated()&&blurAllowed()&&init()){
            boolean recording=false;
            try{
                int r=radiusPx();if(blur!=r){Class<?> fx=Class.forName("android.graphics.RenderEffect");Object filter=fx.getMethod("createBlurEffect",float.class,float.class,Shader.TileMode.class).invoke(null,(float)r,(float)r,Shader.TileMode.CLAMP);effect.invoke(node,filter);blur=r;}
                int pad=r*2,width=b.width()+pad*2,height=b.height()+pad*2;position.invoke(node,0,0,width,height);
                host.getLocationInWindow(hostAt);content.getLocationInWindow(pageAt);float dx=hostAt[0]-pageAt[0]+b.left,dy=hostAt[1]-pageAt[1]+b.top;
                Canvas rec=(Canvas)begin.invoke(node,width,height);recording=true;
                rec.drawColor(pageColor|0xff000000);rec.translate(-dx+pad-content.getScrollX(),-dy+pad-content.getScrollY());content.draw(rec);end.invoke(node);recording=false;
                int save=canvas.save();try{canvas.clipPath(shape);canvas.translate(b.left-pad,b.top-pad);draw.invoke(canvas,node);blurred=true;}finally{canvas.restoreToCount(save);}
            }catch(Throwable unavailable){if(recording)try{end.invoke(node);}catch(Throwable ignored){}blurred=false;}
        }
        blurredLastFrame=blurred;tint.setColor(solid!=null?solid:blurred?color:color|0xff000000);canvas.drawPath(shape,tint);
        int mode=GlassRim.mode(c);if(mode!=rimMode){rimMode=mode;rim=mode==GlassRim.NONE?null:GlassRim.of(c,radius);}
        if(rim!=null)rim.draw(canvas,box.left,box.top,box.right,box.bottom,radius,255);
    }
    void release(){released=true;if(content.getViewTreeObserver().isAlive())content.getViewTreeObserver().removeOnPreDrawListener(follow);if(node!=null)try{discard.invoke(node);}catch(Throwable ignored){}node=null;}
    @Override public void setAlpha(int value){}@Override public void setColorFilter(ColorFilter f){}@Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
