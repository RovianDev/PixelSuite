package io.github.pixelsuite;
import android.view.*;
import java.util.List;
final class SearchIme {
    static void install(AppSearch view){try{View.class.getMethod("setWindowInsetsAnimationCallback",WindowInsetsAnimation.Callback.class).invoke(view,new Follow(view));}catch(Throwable ignored){}}
    static void installInsets(AppSearch view,WindowInsets in){installInsets(view,in,false,0);}
    static void installInsets(AppSearch view,WindowInsets in,boolean frame,float progress){
        if(!frame&&view.imeAnimationRunning())return;
        try{
            Class<?> type=Class.forName("android.view.WindowInsets$Type");int mask=(Integer)type.getMethod("ime").invoke(null),navMask=(Integer)type.getMethod("navigationBars").invoke(null);
            java.lang.reflect.Method get=WindowInsets.class.getMethod("getInsets",int.class);Object keyboard=get.invoke(in,mask),navigation=get.invoke(in,navMask);
            int bottom=keyboard.getClass().getField("bottom").getInt(keyboard),nav=navigation.getClass().getField("bottom").getInt(navigation);if(frame)view.imeProgress(bottom,nav,progress);else view.ime(bottom,nav);
        }catch(Throwable ignored){view.ime(in.getSystemWindowInsetBottom(),0);}
    }
    static final class Follow extends WindowInsetsAnimation.Callback {
        final AppSearch view;Follow(AppSearch v){super(0);view=v;}
        @Override public void onPrepare(WindowInsetsAnimation animation){view.imeStarting();}
        @Override public WindowInsets onProgress(WindowInsets in,List<WindowInsetsAnimation> running){float progress=0;for(WindowInsetsAnimation animation:running)try{int mask=(Integer)animation.getClass().getMethod("getTypeMask").invoke(animation);int keyboard=(Integer)Class.forName("android.view.WindowInsets$Type").getMethod("ime").invoke(null);if((mask&keyboard)!=0)progress=(Float)animation.getClass().getMethod("getInterpolatedFraction").invoke(animation);}catch(Throwable ignored){}installInsets(view,in,true,progress);return in;}
        @Override public void onEnd(WindowInsetsAnimation animation){view.imeFinished();}
    }
}
