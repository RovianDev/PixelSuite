package io.github.pixelsuite;
import android.view.*;
import java.lang.ref.WeakReference;
import java.util.*;
final class ToastMotion {
 private ToastMotion(){}
 private static final int DISMISS=0x7e505401,WATCH=0x7e505402;
 private static final List<WeakReference<View>> shown=new ArrayList<WeakReference<View>>();
 static void register(View view,Runnable hide){android.content.Context context=view.getContext();while(context instanceof android.content.ContextWrapper){if(context instanceof android.app.Activity){watchWindow(((android.app.Activity)context).getWindow());break;}android.content.Context next=((android.content.ContextWrapper)context).getBaseContext();if(next==context)break;context=next;}unregister(view);view.setTag(DISMISS,hide);shown.add(new WeakReference<View>(view));view.setClickable(true);view.setOnTouchListener((v,event)->{if(event.getActionMasked()==MotionEvent.ACTION_DOWN)dismiss(v);if(event.getActionMasked()==MotionEvent.ACTION_UP)v.performClick();return true;});}
 static void unregister(View view){if(view==null)return;view.setTag(DISMISS,null);for(Iterator<WeakReference<View>> i=shown.iterator();i.hasNext();){View v=i.next().get();if(v==null||v==view)i.remove();}}
 static void dismiss(View view){Object action=view.getTag(DISMISS);unregister(view);if(action instanceof Runnable)((Runnable)action).run();}
 static void dismissAll(){List<WeakReference<View>> copy=new ArrayList<WeakReference<View>>(shown);for(WeakReference<View> ref:copy){View v=ref.get();if(v!=null)dismiss(v);}shown.clear();}
 static void watchWindow(Window window){
  if(window==null||!"io.github.roviandev.pixelsuite".equals(window.getContext().getPackageName()))return;
  Window.Callback callback=window.getCallback();if(callback==null)return;
  if(java.lang.reflect.Proxy.isProxyClass(callback.getClass())&&java.lang.reflect.Proxy.getInvocationHandler(callback) instanceof SwipeDismiss)return;
  try{window.setCallback((Window.Callback)java.lang.reflect.Proxy.newProxyInstance(Window.Callback.class.getClassLoader(),new Class<?>[]{Window.Callback.class},new SwipeDismiss(callback,ViewConfiguration.get(window.getContext()).getScaledTouchSlop())));}catch(Throwable ignored){}
 }
 private static final class SwipeDismiss implements java.lang.reflect.InvocationHandler {
  final Window.Callback callback;final float slopSquared;float downX,downY;boolean tracking;
  SwipeDismiss(Window.Callback callback,int slop){this.callback=callback;slopSquared=slop*slop;}
  public Object invoke(Object proxy,java.lang.reflect.Method method,Object[] args)throws Throwable{
   if(method.getDeclaringClass()==Object.class){if("equals".equals(method.getName()))return proxy==args[0];if("hashCode".equals(method.getName()))return System.identityHashCode(proxy);}
   if("dispatchTouchEvent".equals(method.getName())&&args!=null&&args.length==1&&args[0] instanceof MotionEvent){
    MotionEvent event=(MotionEvent)args[0];int action=event.getActionMasked();
    if(action==MotionEvent.ACTION_DOWN){downX=event.getRawX();downY=event.getRawY();tracking=true;}
    else if(action==MotionEvent.ACTION_MOVE&&tracking){float dx=event.getRawX()-downX,dy=event.getRawY()-downY;if(dx*dx+dy*dy>slopSquared){tracking=false;if(!shown.isEmpty())dismissAll();}}
    else if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL)tracking=false;
   }
   try{return method.invoke(callback,args);}catch(java.lang.reflect.InvocationTargetException error){throw error.getCause();}
  }
 }
 static void watchScroll(final View root){
  if(root.getTag(WATCH)!=null)return;root.setTag(WATCH,Boolean.TRUE);
  final ViewTreeObserver.OnScrollChangedListener listener=()->dismissAll();
  class ScrollWatch implements View.OnAttachStateChangeListener {
   ViewTreeObserver observer;
   public void onViewAttachedToWindow(View v){ViewTreeObserver next=v.getViewTreeObserver();if(observer==next)return;if(observer!=null&&observer.isAlive())observer.removeOnScrollChangedListener(listener);observer=next;observer.addOnScrollChangedListener(listener);}
   public void onViewDetachedFromWindow(View v){if(observer!=null&&observer.isAlive())observer.removeOnScrollChangedListener(listener);observer=null;dismissAll();}
  }
  ScrollWatch watch=new ScrollWatch();root.addOnAttachStateChangeListener(watch);
  if(root.isAttachedToWindow())watch.onViewAttachedToWindow(root);
 }
}
