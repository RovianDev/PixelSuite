package io.github.pixelsuite;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;

final class DollarBadgeView extends ImageView {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path star=new Path();
    private final Rect visible=new Rect();
    private ValueAnimator shimmer;
    private float phase;private final boolean gold,mixed;
    private final Runnable motionChanged=new Runnable(){public void run(){updateMotion();invalidate();}};
    DollarBadgeView(Context c){this(c,false);}
    DollarBadgeView(Context c,boolean gold){this(c,gold,false);}
    DollarBadgeView(Context c,boolean gold,boolean mixed){
        super(c);this.gold=gold;this.mixed=mixed;int id=getResources().getIdentifier(gold?"ic_ps_watch_dollar":"ic_ps_patron_dollar","drawable",c.getPackageName());if(id!=0)setImageResource(id);setScaleType(ScaleType.FIT_CENTER);if(mixed)setImageBitmap(blendedBadge(c));
        star.moveTo(0,-5);star.lineTo(1.3f,-1.5f);star.lineTo(5,0);star.lineTo(1.5f,1.3f);star.lineTo(0,5);star.lineTo(-1.4f,1.5f);star.lineTo(-5,0);star.lineTo(-1.4f,-1.5f);star.close();
    }
    private android.graphics.Bitmap blendedBadge(Context c){
        int w=156,h=204;android.graphics.Bitmap result=android.graphics.Bitmap.createBitmap(w,h,android.graphics.Bitmap.Config.ARGB_8888);
        Canvas base=new Canvas(result);android.graphics.drawable.Drawable blue=getDrawable().mutate();blue.setBounds(0,0,w,h);blue.draw(base);
        android.graphics.Bitmap layer=android.graphics.Bitmap.createBitmap(w,h,android.graphics.Bitmap.Config.ARGB_8888);Canvas overlay=new Canvas(layer);
        android.graphics.drawable.Drawable amber=c.getResources().getDrawable(c.getResources().getIdentifier("ic_ps_watch_dollar","drawable",c.getPackageName()),c.getTheme()).mutate();amber.setBounds(0,0,w,h);amber.draw(overlay);
        Paint mask=new Paint(Paint.ANTI_ALIAS_FLAG);mask.setShader(new android.graphics.LinearGradient(0,0,0,h,new int[]{0x00ffffff,0x00ffffff,0xffffffff,0xffffffff},new float[]{0,.50f,.80f,1},android.graphics.Shader.TileMode.CLAMP));mask.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_IN));overlay.drawRect(0,0,w,h,mask);base.drawBitmap(layer,0,0,null);layer.recycle();return result;
    }
    private void updateMotion(){
        boolean active=isAttachedToWindow()&&isShown()&&hasWindowFocus()&&getWindowVisibility()==VISIBLE&&PatronPaper.motion(getContext());
        if(!active){if(shimmer!=null){shimmer.cancel();shimmer=null;}return;}
        if(shimmer!=null)return;
        shimmer=ValueAnimator.ofFloat(0,1);shimmer.setDuration(2800);shimmer.setRepeatCount(ValueAnimator.INFINITE);shimmer.setInterpolator(new LinearInterpolator());
        shimmer.addUpdateListener(new ValueAnimator.AnimatorUpdateListener(){public void onAnimationUpdate(ValueAnimator value){phase=(Float)value.getAnimatedValue();if(getGlobalVisibleRect(visible))invalidate();}});shimmer.start();
    }
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();AppMotion.watch(motionChanged);updateMotion();}
    @Override protected void onDetachedFromWindow(){AppMotion.unwatch(motionChanged);if(shimmer!=null){shimmer.cancel();shimmer=null;}super.onDetachedFromWindow();}
    @Override public void onWindowFocusChanged(boolean focus){super.onWindowFocusChanged(focus);updateMotion();}
    @Override protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);updateMotion();}
    @Override protected void onVisibilityChanged(View changed,int visibility){super.onVisibilityChanged(changed,visibility);updateMotion();}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);updateMotion();if(getDrawable()==null)return;
        Rect bounds=getDrawable().getBounds();canvas.save();canvas.translate(getPaddingLeft(),getPaddingTop());canvas.concat(getImageMatrix());canvas.translate(bounds.left,bounds.top);canvas.scale(bounds.width()/52f,bounds.height()/68f);
        drawStar(canvas,7,8,1f,phase);drawStar(canvas,43,55,.8f,(phase+.43f)%1);canvas.restore();
    }
    private void drawStar(Canvas c,float x,float y,float size,float position){
        float brightness=shimmer==null?1f:(float)Math.pow(Math.max(0,Math.sin(position*Math.PI*2)),.7);
        float scale=size*(shimmer==null?1f:.12f+1.18f*brightness);int alpha=shimmer==null?255:Math.round(255*brightness);
        c.save();c.translate(x,y);c.scale(scale,scale);paint.setStyle(Paint.Style.FILL);paint.setColor((gold||(mixed&&y>34))?0xffffd978:0xffa8dcff);paint.setAlpha(alpha);c.drawPath(star,paint);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(.6f);paint.setStrokeJoin(Paint.Join.ROUND);paint.setColor((gold||(mixed&&y>34))?0xffab7724:0xff5a9dcc);paint.setAlpha(alpha);c.drawPath(star,paint);paint.setAlpha(255);c.restore();
    }
}
