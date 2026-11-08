package io.github.pixelsuite;

import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;

final class TapFeedback {
    private TapFeedback(){}
    private static final java.util.WeakHashMap<View,Boolean> roots=new java.util.WeakHashMap<View,Boolean>();
    static void watch(View root){if(roots.put(root,Boolean.TRUE)!=null)return;root.getViewTreeObserver().addOnGlobalLayoutListener(()->scan(root));root.post(()->scan(root));}
    private static boolean ripple(Drawable d){
        if(d instanceof RippleDrawable)return true;
        if(d instanceof InsetDrawable)return ripple(((InsetDrawable)d).getDrawable());
        if(d instanceof LayerDrawable){LayerDrawable layers=(LayerDrawable)d;for(int i=0;i<layers.getNumberOfLayers();i++)if(ripple(layers.getDrawable(i)))return true;}
        return false;
    }
    private static void apply(View view,boolean circle){
        if(view.getForeground()!=null||ripple(view.getBackground()))return;
        float density=view.getResources().getDisplayMetrics().density;
        Drawable mask=new Drawable(){final Paint paint=new Paint(3);final RectF rect=new RectF();
            public void draw(Canvas c){Rect b=getBounds();paint.setColor(Color.WHITE);if(circle)c.drawCircle(b.exactCenterX(),b.exactCenterY(),Math.min(b.width(),b.height())/2f,paint);else{rect.set(b);c.drawRoundRect(rect,24*density,24*density,paint);}}
            public void setAlpha(int a){}public void setColorFilter(ColorFilter f){}public int getOpacity(){return PixelFormat.TRANSLUCENT;}};
        int color=FrostedSurface.dark(view.getContext())?0x33ffffff:0x26000000;
        view.setForeground(new RippleDrawable(ColorStateList.valueOf(color),null,mask));
    }
    private static void scan(View view){
        if(view instanceof PatronIdentity){View photo=((PatronIdentity)view).getChildAt(0);photo.setDuplicateParentStateEnabled(true);apply(photo,true);}
        else if(view.isClickable()&&("Open Developer options".contentEquals(view.getContentDescription()==null?"":view.getContentDescription())||I18n.t("Open Developer options").contentEquals(view.getContentDescription()==null?"":view.getContentDescription()))&&view instanceof ViewGroup){View badge=((ViewGroup)view).getChildAt(0);badge.setDuplicateParentStateEnabled(true);apply(badge,true);}
        else if(view.isClickable()&&!(view instanceof EditText)&&!(view instanceof CompoundButton)&&!(view instanceof AbsSeekBar)&&!view.getClass().getName().startsWith(AppSearch.class.getName())){
            boolean linkText=view instanceof TextView&&((TextView)view).getMovementMethod()!=null;
            if(!linkText)apply(view,view instanceof ImageView);
        }
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)scan(group.getChildAt(i));}
    }
}
