package io.github.pixelsuite;
import android.app.Activity;
import android.content.*;
import android.graphics.Color;
import android.os.*;
import android.util.TypedValue;
import android.view.*;
import android.widget.*;
final class AppToast {
 private final Context context;private final CharSequence message;private final int duration;private PopupWindow popup;private TextView body;private View host;private View.OnAttachStateChangeListener detach;private android.widget.Toast fallback;
 private final Handler handler=new Handler(Looper.getMainLooper());private final Runnable hide=()->dismiss(true);
 private AppToast(Context c,CharSequence text,int length){context=c;message=text;duration=length;}
 static AppToast makeText(Context c,CharSequence text,int duration){return new AppToast(c,text,duration);}
 static AppToast makeText(Context c,int resource,int duration){return makeText(c,c.getText(resource),duration);}
 private static Activity owner(Context c){while(c instanceof ContextWrapper){if(c instanceof Activity)return (Activity)c;Context next=((ContextWrapper)c).getBaseContext();if(next==c)break;c=next;}return null;}
 void show(){show(null);}
 void show(View parent){if(Looper.myLooper()!=Looper.getMainLooper()){handler.post(()->show(parent));return;}Activity a=owner(context);
  if(a==null||!"io.github.roviandev.pixelsuite".equals(a.getPackageName())){fallback=android.widget.Toast.makeText(context,message,duration);fallback.show();return;}if(a.isFinishing()||a.isDestroyed())return;
  View anchor=parent!=null?parent:a.getWindow().getDecorView();ToastMotion.dismissAll();ToastMotion.watchScroll(anchor);
  TypedValue value=new TypedValue();a.getTheme().resolveAttribute(android.R.attr.colorAccent,value,true);int accent=value.data;
  body=new TextView(a);body.setText(message);body.setTextSize(14);body.setGravity(Gravity.CENTER);body.setTextColor(AppSurfaces.active(a)||FrostedSurface.dark(a)?Color.WHITE:0xff25242a);body.setPadding(dp(a,20),dp(a,15),dp(a,20),dp(a,15));body.setBackground(AppSurfaces.pill(a,new ScopePanel.SoftSurface(a,false,accent)));body.setLayerType(View.LAYER_TYPE_SOFTWARE,null);body.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
  popup=new PopupWindow(body,a.getResources().getDisplayMetrics().widthPixels-dp(a,40),-2,false);popup.setBackgroundDrawable(null);popup.setOutsideTouchable(false);popup.setClippingEnabled(true);popup.setElevation(dp(a,3));
  int inset=0,res=a.getResources().getIdentifier("status_bar_height","dimen","android");if(res!=0)inset=a.getResources().getDimensionPixelSize(res);
  popup.showAtLocation(anchor,Gravity.TOP|Gravity.CENTER_HORIZONTAL,0,inset+dp(a,12));
  ToastMotion.register(body,hide);body.setOnClickListener(v->dismiss(true));body.announceForAccessibility(message);
  host=anchor;detach=new View.OnAttachStateChangeListener(){public void onViewAttachedToWindow(View v){}public void onViewDetachedFromWindow(View v){dismiss(false);}};host.addOnAttachStateChangeListener(detach);
  if(AppMotion.enabled(a)){body.setAlpha(0);body.setScaleX(.94f);body.setScaleY(.94f);body.animate().alpha(1).scaleX(1).scaleY(1).setDuration(220).setInterpolator(new android.view.animation.DecelerateInterpolator()).start();}
  handler.postDelayed(hide,duration==android.widget.Toast.LENGTH_LONG?3500:2000);
 }
 private static int dp(Context c,float n){return Math.round(n*c.getResources().getDisplayMetrics().density);}
 void cancel(){dismiss(true);if(fallback!=null){fallback.cancel();fallback=null;}}
 private void dismiss(boolean animated){handler.removeCallbacks(hide);if(host!=null&&detach!=null)host.removeOnAttachStateChangeListener(detach);host=null;detach=null;PopupWindow old=popup;TextView view=body;popup=null;body=null;if(old==null)return;ToastMotion.unregister(view);view.animate().cancel();Activity a=owner(context);if(animated&&a!=null&&AppMotion.enabled(a))view.animate().alpha(0).scaleX(.96f).scaleY(.96f).setDuration(150).withEndAction(()->old.dismiss()).start();else old.dismiss();}
}
