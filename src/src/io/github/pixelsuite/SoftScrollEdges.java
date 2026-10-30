package io.github.pixelsuite;
import android.app.Activity;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import java.lang.reflect.Method;
final class SoftScrollEdges extends FrameLayout {
 private final ScrollView scroll;private final float density;private final Paint tint=new Paint(),mask=new Paint(),layer=new Paint();
 private final Paint topTint=new Paint(),bottomTint=new Paint(),topMask=new Paint(),bottomMask=new Paint();
 private final RectF top=new RectF(),bottom=new RectF();private final int background;private int palette=-1,status,nav;private boolean frosted;private NativeBlur blur;
 SoftScrollEdges(Activity a,ScrollView content,int color){super(a);scroll=content;background=color;density=getResources().getDisplayMetrics().density;setClipChildren(true);content.setClipToPadding(false);content.setVerticalFadingEdgeEnabled(false);addView(content,new LayoutParams(-1,-1));mask.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));getViewTreeObserver().addOnScrollChangedListener(()->invalidate());}
 private float dp(float n){return n*density;}
 @Override public WindowInsets onApplyWindowInsets(WindowInsets in){status=in.getSystemWindowInsetTop();nav=in.getSystemWindowInsetBottom();palette=-1;return super.onApplyWindowInsets(in);}
 @Override protected void onSizeChanged(int w,int h,int ow,int oh){super.onSizeChanged(w,h,ow,oh);palette=-1;}
 private void prepare(){int style=SurfaceStyle.get(getContext());boolean black=AppSurfaces.active(getContext()),dark=FrostedSurface.dark(getContext());int page=scroll.getBackground() instanceof android.graphics.drawable.ColorDrawable?((android.graphics.drawable.ColorDrawable)scroll.getBackground()).getColor():background;int key=page^(style+(black?8:0)+(dark?16:0));if(key==palette)return;palette=key;frosted=style==SurfaceStyle.FROSTED;
  int color=page;if(black)color=Color.BLACK;else if(frosted){color=FrostedSurface.composite(FrostedSurface.resourceColor(getContext(),"blur_shade_panel_fg",dark?0x522c303d:0x66dedee8),FrostedSurface.resourceColor(getContext(),"blur_shade_panel_bg",dark?0x520e0e0f:0x66f9f9fe))|0xff000000;}else if(style==SurfaceStyle.STOCK)color=page;
  top.set(0,0,getWidth(),Math.min(getHeight()/2f,status+dp(24)));bottom.set(0,Math.max(top.bottom,getHeight()-nav-dp(88)),getWidth(),getHeight());
  int clear=color&0xffffff;topTint.setShader(new LinearGradient(0,0,0,Math.max(1,top.bottom),new int[]{color,color&0xffffff|0xc0000000,clear},new float[]{0,.35f,1},Shader.TileMode.CLAMP));bottomTint.setShader(new LinearGradient(0,bottom.top,0,Math.max(bottom.top+1,bottom.bottom),new int[]{clear,color&0xffffff|0xc0000000,color},new float[]{0,.65f,1},Shader.TileMode.CLAMP));
  topMask.setShader(new LinearGradient(0,0,0,Math.max(1,top.bottom),Color.WHITE,0x00ffffff,Shader.TileMode.CLAMP));bottomMask.setShader(new LinearGradient(0,bottom.top,0,Math.max(bottom.top+1,bottom.bottom),0x00ffffff,Color.WHITE,Shader.TileMode.CLAMP));topMask.setXfermode(mask.getXfermode());bottomMask.setXfermode(mask.getXfermode());
  if(frosted&&android.os.Build.VERSION.SDK_INT>=31&&blur==null)try{blur=new NativeBlur(FrostedSurface.depthBlurRadius(getContext()));}catch(Throwable ignored){}
 }
 @Override protected void dispatchDraw(Canvas c){prepare();float above=Math.max(0,scroll.getScrollY());float below=scroll.getChildCount()==0?0:Math.max(0,scroll.getChildAt(0).getHeight()+scroll.getPaddingTop()+scroll.getPaddingBottom()-scroll.getHeight()-scroll.getScrollY());float ta=Math.min(1,above/dp(16)),ba=Math.min(1,below/dp(16));
  boolean recorded=false;if(frosted&&blur!=null&&c.isHardwareAccelerated()&&(ta>0||ba>0))try{Canvas rec=blur.begin(getWidth(),getHeight());super.dispatchDraw(rec);blur.end();recorded=true;}catch(Throwable ignored){blur.finish();}
  super.dispatchDraw(c);edge(c,top,topTint,topMask,ta,recorded);edge(c,bottom,bottomTint,bottomMask,ba,recorded);
 }
 private void edge(Canvas c,RectF band,Paint shade,Paint gradient,float amount,boolean recorded){if(amount<=0||band.isEmpty())return;
  if(recorded){int save=c.save();c.clipRect(band);layer.setAlpha(Math.round(255*amount));int composite=c.saveLayer(band,layer);try{blur.draw(c);c.drawRect(band,gradient);}catch(Throwable ignored){}c.restoreToCount(composite);c.restoreToCount(save);}
  shade.setAlpha(Math.round(255*amount));c.drawRect(band,shade);
 }
 @Override protected void onDetachedFromWindow(){if(blur!=null)blur.release();super.onDetachedFromWindow();}
 static final class NativeBlur {
  private static final Object[] EMPTY=new Object[0];final Object node;final Method begin,end,draw,discard,position;final Object[] size=new Object[2],bounds=new Object[4],nodeArg=new Object[1];int width=-1,height=-1;boolean recording;
  NativeBlur(int radius)throws Exception{Class<?> n=Class.forName("android.graphics.RenderNode"),fx=Class.forName("android.graphics.RenderEffect");node=n.getConstructor(String.class).newInstance("Pixel Suite scroll edge");begin=n.getMethod("beginRecording",int.class,int.class);end=n.getMethod("endRecording");position=n.getMethod("setPosition",int.class,int.class,int.class,int.class);draw=Canvas.class.getMethod("drawRenderNode",n);discard=n.getMethod("discardDisplayList");Object effect=fx.getMethod("createBlurEffect",float.class,float.class,Shader.TileMode.class).invoke(null,(float)Math.max(1,radius),(float)Math.max(1,radius),Shader.TileMode.CLAMP);n.getMethod("setRenderEffect",fx).invoke(node,effect);nodeArg[0]=node;bounds[0]=bounds[1]=0;}
  Canvas begin(int w,int h)throws Exception{if(width!=w||height!=h){width=w;height=h;size[0]=bounds[2]=w;size[1]=bounds[3]=h;position.invoke(node,bounds);}Canvas c=(Canvas)begin.invoke(node,size);recording=true;return c;}
  void end()throws Exception{end.invoke(node,EMPTY);recording=false;}
  void finish(){if(recording)try{end();}catch(Exception ignored){}}
  void draw(Canvas c)throws Exception{draw.invoke(c,nodeArg);}
  void release(){try{discard.invoke(node,EMPTY);}catch(Exception ignored){}}
 }
}
