package io.github.pixelsuite;
import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
final class ThemeGlass extends Drawable {
 private final SuperDarkPalette.Rim exactRim=new SuperDarkPalette.Rim();
 static final int PANEL=0,SOCIAL=1,FIELD=2,RAISED=3;
 private final Context context;private final float radius,d;private final int kind;private final Paint fill=new Paint(3),rim=new Paint(3);private final RectF frame=new RectF();private boolean lastBlack,lastDark;private int lastStyle=-1;
 ThemeGlass(Context c,float r,int kind){context=c;AppSurfaces.watch(this);d=c.getResources().getDisplayMetrics().density;radius=r*d;this.kind=kind;rim.setStyle(Paint.Style.STROKE);rim.setStrokeWidth(1.1f*d);if(kind==SOCIAL)rim.setMaskFilter(new BlurMaskFilter(1.4f*d,BlurMaskFilter.Blur.NORMAL));}
 @Override protected void onBoundsChange(Rect b){super.onBoundsChange(b);frame.set(b);float inset=(kind==SOCIAL?4f:1.2f)*d;frame.inset(inset,inset);lastStyle=-1;}
 public void draw(Canvas c){boolean black=AppSurfaces.active(context),dark=FrostedSurface.dark(context);int style=SurfaceStyle.get(context);
  if(lastStyle!=style||lastBlack!=black||lastDark!=dark){lastStyle=style;lastBlack=black;lastDark=dark;
   int top,bottom;if(black){top=bottom=kind==FIELD?SuperDarkPalette.FIELD:kind==RAISED?SuperDarkPalette.SHEET:Color.BLACK;}else if(kind==SOCIAL){top=dark?0xffa3a5ab:0xfffefeff;bottom=dark?0xff777a82:0xffeae8ed;}else{top=dark?0xff303137:0xfffaf8fc;bottom=dark?0xff222329:0xffeeecf1;}
   if(style!=SurfaceStyle.FROSTED&&!black)bottom=top;
   fill.setShader(new LinearGradient(0,frame.top,0,Math.max(frame.top+1,frame.bottom),top,bottom,Shader.TileMode.CLAMP));
   if(kind==SOCIAL){int tone=black?0x30ffffff:dark?0x20ffffff:0x20000000;rim.setShader(new LinearGradient(0,frame.top,0,Math.max(frame.top+1,frame.bottom),tone,tone,Shader.TileMode.CLAMP));} else rim.setShader(new LinearGradient(0,frame.top,0,Math.max(frame.top+1,frame.bottom),black?new int[]{0x2affffff,0x10ffffff,0x08ffffff}:dark?new int[]{0x40ffffff,0x20ffffff,0x18ffffff}:new int[]{0x30000000,0x18000000,0x24000000},new float[]{0,.45f,1},Shader.TileMode.CLAMP));
  }
  c.drawRoundRect(frame,radius,radius,fill);
  if(black){if(kind!=FIELD)exactRim.draw(c,getBounds(),radius,d);return;}
  if(kind==SOCIAL){rim.setStrokeWidth(1.6f*d);rim.setAlpha(255);c.drawRoundRect(frame,radius,radius,rim);return;}
  rim.setStrokeWidth(3.1f*d);rim.setAlpha(40);c.drawRoundRect(frame,radius,radius,rim);
  rim.setStrokeWidth(1.9f*d);rim.setAlpha(80);c.drawRoundRect(frame,radius,radius,rim);
  rim.setStrokeWidth(.9f*d);rim.setAlpha(150);c.drawRoundRect(frame,radius,radius,rim);
 }
 public void setAlpha(int a){fill.setAlpha(a);rim.setAlpha(a);}public void setColorFilter(ColorFilter f){fill.setColorFilter(f);}public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
