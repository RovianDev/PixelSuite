package io.github.pixelsuite;
import android.app.Activity;
import android.view.*;
final class AppPanelBounds {
 private AppPanelBounds(){}
 static int width(Activity a){View root=a.getWindow().getDecorView();int w=root.getWidth()>0?root.getWidth():a.getResources().getDisplayMetrics().widthPixels;WindowInsets in=root.getRootWindowInsets();if(in!=null)w-=in.getSystemWindowInsetLeft()+in.getSystemWindowInsetRight();return Math.max(1,Math.min(w-dp(a,48),dp(a,560)));}
 static int maxHeight(Activity a){View root=a.getWindow().getDecorView();int h=root.getHeight()>0?root.getHeight():a.getResources().getDisplayMetrics().heightPixels;WindowInsets in=root.getRootWindowInsets();if(in!=null)h-=in.getSystemWindowInsetTop()+in.getSystemWindowInsetBottom();return Math.max(1,h-dp(a,48));}
 static void fit(final Activity a,final Window w,int requestedHeight){fit(a,w,width(a),requestedHeight);}
 static void fit(final Activity a,final Window w,int requestedWidth,int requestedHeight){if(w==null)return;ToastMotion.watchWindow(w);int targetWidth=Math.min(width(a),requestedWidth>0?requestedWidth:width(a));int targetHeight=requestedHeight>0?Math.min(requestedHeight,maxHeight(a)):requestedHeight;w.setLayout(targetWidth,targetHeight);WindowManager.LayoutParams lp=w.getAttributes();if((lp.gravity&Gravity.VERTICAL_GRAVITY_MASK)==Gravity.BOTTOM){lp.gravity=Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL;lp.y=dp(a,16);w.setAttributes(lp);}final View decor=w.getDecorView();decor.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener(){public void onGlobalLayout(){if(a.isFinishing()||a.isDestroyed())return;int max=maxHeight(a);if(decor.getHeight()>max&&w.getAttributes().height!=max)w.setLayout(Math.min(width(a),w.getAttributes().width),max);}});}
 private static int dp(Activity a,int n){return Math.round(n*a.getResources().getDisplayMetrics().density);}
}
