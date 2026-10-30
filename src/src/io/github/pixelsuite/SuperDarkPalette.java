package io.github.pixelsuite;

import android.graphics.*;

final class SuperDarkPalette {
 static final int BACKGROUND=0xff000000,LOW=0xff060606,CARD=0xff0b0b0b,
  SHEET=0xff121212,FIELD=0xff1a1a1a,DIALOG=0xff0e0e0e,VARIANT=0xff161616,
  MENU=0xff0a0a0a,VEIL=0x14ffffff,LIGHT_VEIL=0x0fffffff,RIPPLE=0x33ffffff;
 static final float DIM=184f/255f;
 static final class Rim {
  Bitmap bitmap;final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
  int left,top,width,height;float radius,density;
  void draw(Canvas target,Rect bounds,float r,float d){
   int w=bounds.width(),h=bounds.height();if(w<=0||h<=0)return;
   if(bitmap==null||width!=w||height!=h||radius!=r||density!=d){
    width=w;height=h;radius=r;density=d;
    bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(bitmap);
    float inset=2.4f*d;Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setStyle(Paint.Style.STROKE);
    p.setStrokeWidth(2.4f*d);p.setMaskFilter(new BlurMaskFilter(2*d,BlurMaskFilter.Blur.NORMAL));
    p.setShader(new LinearGradient(0,0,0,Math.max(1,h),new int[]{0x30ffffff,0x12ffffff,0x0cffffff,0x16ffffff},new float[]{0,.35f,.75f,1},Shader.TileMode.CLAMP));
    c.drawRoundRect(inset,inset,w-inset,h-inset,Math.max(0,r-inset),Math.max(0,r-inset),p);
   }
   target.drawBitmap(bitmap,bounds.left,bounds.top,paint);
  }
 }
}
