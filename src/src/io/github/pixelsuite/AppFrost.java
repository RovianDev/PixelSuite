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
    private boolean moving,still;private Object stillNode,stillHolder;private int stillW=-1,stillH=-1,stillR=-1;private Boolean stillAllowed;
    private final Paint liteEdge=new Paint(Paint.ANTI_ALIAS_FLAG),liteShine=new Paint(Paint.ANTI_ALIAS_FLAG);private final Matrix liteAt=new Matrix();private int liteMode=-1;
    void moving(boolean on){if(moving!=on){moving=on;invalidateSelf();}}
    void still(boolean on){if(still==on)return;still=on;stillAllowed=null;if(!on)dropStill();invalidateSelf();}
    @Override public void draw(Canvas canvas){
        if(released||getBounds().isEmpty())return;Context c=host.getContext();Rect b=getBounds();box.set(b);shape.reset();shape.addRoundRect(box,radius,radius,Path.Direction.CW);
        Integer solid=solid();boolean blurred=false;int color=still||moving?frost:frostTint();if(pillSurface){tint.setColor(color|0xff000000);canvas.drawPath(shape,tint);}
        boolean allowed=still?(stillAllowed!=null?stillAllowed:(stillAllowed=blurAllowed())):blurAllowed();
        if(solid==null&&canvas.isHardwareAccelerated()&&allowed&&init()){
            if(still&&drawStill(canvas))blurred=true;
            else{
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
        }
        blurredLastFrame=blurred;tint.setColor(solid!=null?solid:blurred?color:color|0xff000000);canvas.drawPath(shape,tint);
        int mode=GlassRim.mode(c);if(mode!=rimMode){rimMode=mode;rim=mode==GlassRim.NONE?null:GlassRim.of(c,radius);}
        if(rim!=null){if(moving)liteRim(canvas,mode);else rim.draw(canvas,box.left,box.top,box.right,box.bottom,radius,255);}
    }
    private boolean drawStill(Canvas canvas){
        try{
            Class<?> n=Class.forName("android.graphics.RenderNode");
            int r=radiusPx(),pad=r*2,w=content.getWidth()+pad*2,h=content.getHeight()+pad*2;
            if(content.getWidth()<=0||content.getHeight()<=0)return false;
            if(stillNode==null){stillNode=n.getConstructor(String.class).newInstance("Pixel Suite still frost");stillHolder=n.getConstructor(String.class).newInstance("Pixel Suite still frost layer");n.getMethod("setUseCompositingLayer",boolean.class,Paint.class).invoke(stillHolder,true,null);}
            if(stillW!=w||stillH!=h||stillR!=r){
                Class<?> fx=Class.forName("android.graphics.RenderEffect");
                effect.invoke(stillNode,fx.getMethod("createBlurEffect",float.class,float.class,Shader.TileMode.class).invoke(null,(float)r,(float)r,Shader.TileMode.CLAMP));
                position.invoke(stillNode,0,0,w,h);
                Canvas rec=(Canvas)begin.invoke(stillNode,w,h);
                try{rec.drawColor(pageColor|0xff000000);rec.translate(pad-content.getScrollX(),pad-content.getScrollY());content.draw(rec);}finally{end.invoke(stillNode);}
                position.invoke(stillHolder,0,0,w,h);
                Canvas hold=(Canvas)begin.invoke(stillHolder,w,h);
                try{draw.invoke(hold,stillNode);}finally{end.invoke(stillHolder);}
                stillW=w;stillH=h;stillR=r;
            }
            host.getLocationInWindow(hostAt);content.getLocationInWindow(pageAt);
            int save=canvas.save();
            try{canvas.clipPath(shape);canvas.translate(pageAt[0]-hostAt[0]-pad,pageAt[1]-hostAt[1]-pad);draw.invoke(canvas,stillHolder);}finally{canvas.restoreToCount(save);}
            return true;
        }catch(Throwable unavailable){dropStill();return false;}
    }
    private void dropStill(){for(Object o:new Object[]{stillNode,stillHolder})if(o!=null)try{discard.invoke(o);}catch(Throwable ignored){}stillNode=stillHolder=null;stillW=stillH=stillR=-1;}
    private void liteRim(Canvas canvas,int mode){
        float d=host.getResources().getDisplayMetrics().density,w=1.5f*d;
        if(liteMode!=mode){
            liteMode=mode;
            liteEdge.setStyle(Paint.Style.STROKE);liteEdge.setStrokeWidth(w);liteShine.setStyle(Paint.Style.STROKE);liteShine.setStrokeWidth(w);
            liteShine.setShader(new LinearGradient(0,0,0,1,mode==GlassRim.DARK?new int[]{0x34ffffff,0x14ffffff,0x0effffff,0x18ffffff}:new int[]{0xd0ffffff,0x80ffffff,0x5cffffff,0x74ffffff},new float[]{0f,.3f,.75f,1f},Shader.TileMode.CLAMP));
            liteEdge.setShader(mode==GlassRim.DARK?null:new LinearGradient(0,0,0,1,new int[]{0x14000000,0x1f000000,0x29000000},new float[]{0f,.5f,1f},Shader.TileMode.CLAMP));
        }
        liteAt.setScale(1f,Math.max(1f,box.height()));liteAt.postTranslate(0,box.top);
        float in=w/2f;
        if(liteEdge.getShader()!=null){liteEdge.getShader().setLocalMatrix(liteAt);canvas.drawRoundRect(box.left+in,box.top+in,box.right-in,box.bottom-in,Math.max(0,radius-in),Math.max(0,radius-in),liteEdge);in=w*1.4f;}
        liteShine.getShader().setLocalMatrix(liteAt);
        canvas.drawRoundRect(box.left+in,box.top+in,box.right-in,box.bottom-in,Math.max(0,radius-in),Math.max(0,radius-in),liteShine);
    }
    void release(){released=true;if(content.getViewTreeObserver().isAlive())content.getViewTreeObserver().removeOnPreDrawListener(follow);if(node!=null)try{discard.invoke(node);}catch(Throwable ignored){}node=null;dropStill();}
    @Override public void setAlpha(int value){}@Override public void setColorFilter(ColorFilter f){}@Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
