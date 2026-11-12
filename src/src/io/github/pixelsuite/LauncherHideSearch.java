package io.github.pixelsuite;

import android.app.Application;
import android.content.Context;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

final class LauncherHideSearch {
    private static final String TAG="PixelSuiteHomeSearch";
    private static volatile boolean enabled, ready, read;
    private static boolean warned, reported;
    private static ClassLoader loader;
    private static final Set<Method> factories=new HashSet<Method>();
    private static final ThreadLocal<Build> builds=new ThreadLocal<Build>();
    private static final ThreadLocal<Boolean> stock=new ThreadLocal<Boolean>();
    private static final class Build { Object properties, builder; Build previous; boolean hidden; Boolean hide; float[] space; Object inv; }
    private static boolean legacy;
    private static int qsbHeightId, qsbShadowId;
    private static boolean drawerWarned;
    private LauncherHideSearch() {}
    private static Object get(Object o,String name)throws Exception {
        return field(o.getClass(),name).get(o);
    }
    private static Field field(Class<?> c,String name)throws Exception {
        for(Class<?> k=c;k!=null;k=k.getSuperclass())try{Field f=k.getDeclaredField(name);f.setAccessible(true);return f;}catch(NoSuchFieldException ignored){}
        String other=Xp.otherSpelling(name);
        if(other!=null)for(Class<?> k=c;k!=null;k=k.getSuperclass())try{Field f=k.getDeclaredField(other);f.setAccessible(true);return f;}catch(NoSuchFieldException ignored){}
        throw new NoSuchFieldException(name);
    }
    private static void warn(Throwable t){if(!warned){warned=true;android.util.Log.w(TAG,"Unsupported home-search layout; keeping stock",t);}}
    private static void read(Context c){
        if(read||c==null)return;
        try{enabled=Settings.Secure.getInt(c.getContentResolver(),LauncherFeatures.HIDE_SEARCH,0)==1;read=true;}
        catch(Throwable t){warn(t);}
    }
    private static boolean phone(Object properties)throws Exception {
        if(properties==null||!Boolean.TRUE.equals(get(properties,"isPhone"))||Boolean.TRUE.equals(opt(properties,"isLargeScreen","isTablet")))return false;
        Object taskbar=opt(properties,"taskbarConfiguration",null);
        if(taskbar!=null&&Boolean.TRUE.equals(get(taskbar,"isTaskbarPresent")))return false;
        Object config=opt(properties,"deviceConfiguration",null);
        Object transpose=config!=null?get(config,"transposeLayoutWithOrientation"):opt(properties,"transposeLayoutWithOrientation",null);
        return !(Boolean.TRUE.equals(get(properties,"isLandscape"))&&Boolean.TRUE.equals(transpose));
    }
    private static Object opt(Object o,String name,String other){
        try{return get(o,name);}catch(Throwable t){}
        if(other!=null)try{return get(o,other);}catch(Throwable t){}
        return null;
    }
    private static boolean hidden(Context c){
        if(!enabled||!ready)return false;
        try{
            Object activity=Xp.callStaticMethod(Xp.findClass("com.android.launcher3.views.ActivityContext",loader),"lookupContext",c);
            Object dp=Xp.callMethod(activity,"getDeviceProfile");
            Object hp=get(dp,"mHotseatProfile");
            return phone(get(dp,"mDeviceProperties"))&&Boolean.TRUE.equals(Xp.getAdditionalInstanceField(hp,"ps_hidden_search"));
        }catch(Throwable t){warn(t);return false;}
    }
    private static void hookFactory(Class<?> c)throws Exception {
        Method m=null;
        for(Class<?> k=c;k!=null&&m==null;k=k.getSuperclass())for(Method candidate:k.getDeclaredMethods())
            if(candidate.getName().equals("createView")&&candidate.getParameterTypes().length==1&&View.class.isAssignableFrom(candidate.getReturnType())&&ViewGroup.class.isAssignableFrom(candidate.getParameterTypes()[0])){m=candidate;break;}
        if(m==null)throw new NoSuchMethodException("search createView");
        synchronized(factories){
            if(factories.contains(m))return;
            if(Xp.hookMethod(m,new Xp.Callback(){protected void beforeHookedMethod(Xp.Param p){
                ViewGroup parent=(ViewGroup)p.args[0];
                if(parent.getClass().getName().equals("com.android.launcher3.Hotseat")&&hidden(parent.getContext()))p.setResult(new EmptySearch(parent.getContext()));
            }})==null)throw new IllegalStateException("search factory hook unavailable");
            factories.add(m);
        }
    }
    static void install(ClassLoader cl){
        loader=cl;
        try{
            Class<?> builder;boolean onePass=false;
            try{builder=Xp.findClass("com.android.launcher3.DeviceProfile$Builder",cl);}
            catch(Throwable older){builder=Xp.findClass("com.android.launcher3.deviceprofile.DeviceProfileBuilder",cl);onePass=true;}
            Class<?> properties=Xp.findClass("com.android.launcher3.deviceprofile.DeviceProperties",cl);
            Class<?> hotseat=Xp.findClass("com.android.launcher3.Hotseat",cl);
            Xp.findAndHookMethod(Application.class,"attach",Context.class,new Xp.Callback(){protected void afterHookedMethod(Xp.Param p){read((Context)p.args[0]);}});
            try{read((Context)Xp.callStaticMethod(Class.forName("android.app.ActivityThread"),"currentApplication"));}catch(Throwable ignored){}
            if(Xp.hookAllMethods(builder,"build",new Xp.Callback(){
                protected void beforeHookedMethod(Xp.Param p){Build b=new Build();b.previous=builds.get();b.builder=p.thisObject;builds.set(b);}
                protected void afterHookedMethod(Xp.Param p){Build b=builds.get();if(b!=null){restoreSpace(b);if(b.previous==null)builds.remove();else builds.set(b.previous);}
                    if(b!=null&&b.hidden&&p.getResult()!=null&&!Boolean.TRUE.equals(stock.get()))keepDrawer(p.thisObject,p.getResult());}
            }).isEmpty())throw new IllegalStateException("profile builder unavailable");
            int propertyHooks=Xp.hookAllConstructors(properties,new Xp.Callback(){protected void afterHookedMethod(Xp.Param p)throws Throwable{seen(p.thisObject);}}).size();
            try{
                Class<?> made=Xp.findClass("com.android.launcher3.deviceprofile.DeviceProperties$Factory",cl);
                for(Method m:made.getDeclaredMethods())if(m.getReturnType()==properties&&Xp.hookMethod(m,new Xp.Callback(){protected void afterHookedMethod(Xp.Param p)throws Throwable{seen(p.getResult());}})!=null)propertyHooks++;
            }catch(Throwable noFactory){}
            if(propertyHooks==0)throw new IllegalStateException("device properties hook unavailable");
            boolean initial;try{Xp.findClass("com.android.launcher3.deviceprofile.HotseatProfileInitialValues",cl);initial=!onePass;}catch(Throwable none){initial=false;}
            Class<?> factory=null;try{factory=Xp.findClass("com.android.launcher3.qsb.QsbWidgetFactory",cl);}catch(Throwable none){}
            legacy=!onePass&&!initial&&factory==null;
            if(initial)installInitialValues(cl);else installOnePass(cl);
            if(legacy)installLegacyBar(hotseat);else hookFactory(factory);
            if(!legacy&&Xp.hookAllConstructors(hotseat,new Xp.Callback(){protected void beforeHookedMethod(Xp.Param p){
                if(p.args.length==0||!(p.args[0] instanceof Context)||!hidden((Context)p.args[0]))return;
                try{
                    Object component=Xp.callStaticMethod(Xp.findClass("com.android.launcher3.dagger.LauncherComponentProvider",loader),"get",p.args[0]);
                    hookFactory(Xp.callMethod(component,"getQsbWidgetFactory").getClass());
                }catch(Throwable t){warn(t);}
            }}).isEmpty())throw new IllegalStateException("hotseat constructor hook unavailable");
            Xp.deoptimizeAll(builder,"build");
            ready=true;
        }catch(Throwable t){ready=false;warn(t);}
    }

    private static void seen(Object props)throws Throwable{
        Build b=builds.get();
        if(b!=null&&props!=null&&((Integer)get(props,"widthPx"))>0){b.properties=props;if(legacy)zeroSpace(b);}
    }

    private static void installInitialValues(ClassLoader cl)throws Exception {
        final Class<?> initial=Xp.findClass("com.android.launcher3.deviceprofile.HotseatProfileInitialValues",cl);
        for(String n:new String[]{"qsbHeight","qsbShadowHeight","qsbVisualHeight","qsbSpace","barSizePx","isQsbInline"})field(initial,n);
        Class<?> factory=Xp.findClass("com.android.launcher3.deviceprofile.HotseatProfile$Factory",cl);
        if(Xp.hookAllConstructors(initial,new Xp.Callback(){protected void afterHookedMethod(Xp.Param p){
            Build b=builds.get();if(!enabled||!ready||b==null||Boolean.TRUE.equals(stock.get()))return;
            String[] names={"qsbHeight","qsbShadowHeight","qsbVisualHeight","qsbSpace","barSizePx"};int[] old=new int[names.length];Field[] fs=new Field[names.length];boolean changed=false;
            try{
                if(!phone(b.properties)||Boolean.TRUE.equals(get(p.thisObject,"isQsbInline")))return;
                for(int i=0;i<names.length;i++){fs[i]=field(initial,names[i]);old[i]=fs[i].getInt(p.thisObject);}
                int reclaimed=old[2]+old[3];if(reclaimed<=0||old[4]<=reclaimed)return;
                changed=true;for(int i=0;i<4;i++)fs[i].setInt(p.thisObject,0);fs[4].setInt(p.thisObject,old[4]-reclaimed);
                Xp.setAdditionalInstanceField(p.thisObject,"ps_hidden_search",Boolean.TRUE);b.hidden=true;
            }catch(Throwable t){if(changed)for(int i=0;i<fs.length;i++)try{fs[i].setInt(p.thisObject,old[i]);}catch(Throwable ignored){}warn(t);}
        }}).isEmpty())throw new IllegalStateException("initial profile hook unavailable");
        if(Xp.hookAllMethods(factory,"createHotseatProfile",new Xp.Callback(){protected void afterHookedMethod(Xp.Param p){
            if(p.args.length>0&&p.getResult()!=null&&Boolean.TRUE.equals(Xp.getAdditionalInstanceField(p.args[0],"ps_hidden_search")))Xp.setAdditionalInstanceField(p.getResult(),"ps_hidden_search",Boolean.TRUE);
        }}).isEmpty())throw new IllegalStateException("hotseat factory hook unavailable");
        Xp.deoptimizeAll(factory,"createHotseatProfile");
    }

    private static void installOnePass(ClassLoader cl)throws Exception {
        final Class<?> spec=Xp.findClass("com.android.launcher3.responsive.CalculatedHotseatSpec",cl);
        field(spec,"hotseatQsbSpace");
        Class<?> specs=Xp.findClass("com.android.launcher3.responsive.HotseatSpecsProvider",cl);
        Class<?> profile=Xp.findClass("com.android.launcher3.deviceprofile.HotseatProfile",cl);
        if(!legacy)field(profile,"isQsbInline");
        if(Xp.hookAllMethods(specs,"getCalculatedSpec",new Xp.Callback(){protected void afterHookedMethod(Xp.Param p){
            Object r=p.getResult();if(r==null||!spec.isInstance(r)||!hideNow())return;
            try{field(spec,"hotseatQsbSpace").setInt(r,0);}catch(Throwable t){warn(t);}
        }}).isEmpty())throw new IllegalStateException("hotseat spec hook unavailable");
        Xp.findAndHookMethod(android.content.res.Resources.class,"getDimensionPixelSize",int.class,new Xp.Callback(){protected void beforeHookedMethod(Xp.Param p){
            if(!enabled||builds.get()==null)return;
            android.content.res.Resources res=(android.content.res.Resources)p.thisObject;
            if(qsbHeightId==0)try{String pkg="com.google.android.apps.nexuslauncher";qsbHeightId=res.getIdentifier("qsb_widget_height","dimen",pkg);qsbShadowId=res.getIdentifier("qsb_shadow_height","dimen",pkg);if(qsbHeightId==0)qsbHeightId=-1;}catch(Throwable t){qsbHeightId=-1;}
            int id=(Integer)p.args[0];
            if((id==qsbHeightId||id==qsbShadowId)&&id>0&&hideNow())p.setResult(0);
        }});
        if(Xp.hookAllConstructors(profile,new Xp.Callback(){protected void afterHookedMethod(Xp.Param p){
            Build b=builds.get();if(b==null||!hideNow())return;
            if(!legacy)try{if(Boolean.TRUE.equals(get(p.thisObject,"isQsbInline")))return;}catch(Throwable t){warn(t);return;}
            Xp.setAdditionalInstanceField(p.thisObject,"ps_hidden_search",Boolean.TRUE);b.hidden=true;
        }}).isEmpty())throw new IllegalStateException("hotseat profile hook unavailable");
    }

    private static boolean hideNow(){
        Build b=builds.get();
        if(!enabled||!ready||b==null||Boolean.TRUE.equals(stock.get()))return false;
        if(b.hide==null){
            boolean hide=false;
            try{
                if(b.properties!=null&&phone(b.properties)){
                    boolean[] inline=(boolean[])get(get(b.builder,"inv"),"inlineQsb");
                    hide=true;if(inline!=null)for(boolean x:inline)if(x){hide=false;break;}
                }
            }catch(Throwable t){warn(t);}
            if(b.properties==null)return false;
            b.hide=hide;
        }
        return b.hide;
    }

    private static void zeroSpace(Build b){
        if(b.space!=null||!hideNow())return;
        try{Object inv=get(b.builder,"inv");Field f=field(inv.getClass(),"hotseatQsbSpace");float[] old=(float[])f.get(inv);if(old==null)return;
            b.inv=inv;b.space=old;f.set(inv,new float[old.length]);}catch(Throwable t){warn(t);}
    }
    private static void restoreSpace(Build b){
        if(b.space==null)return;
        try{field(b.inv.getClass(),"hotseatQsbSpace").set(b.inv,b.space);}catch(Throwable t){warn(t);}
        b.space=null;b.inv=null;
    }
    private static void installLegacyBar(Class<?> hotseat)throws Exception {
        if(Xp.hookAllConstructors(hotseat,new Xp.Callback(){protected void afterHookedMethod(Xp.Param p){
            if(p.args.length==0||!(p.args[0] instanceof Context)||!hidden((Context)p.args[0]))return;
            try{shrink((View)get(p.thisObject,"mQsb"));}catch(Throwable t){warn(t);}
        }}).isEmpty())throw new IllegalStateException("hotseat constructor hook unavailable");
    }
    private static void shrink(View bar){
        if(bar==null)return;
        bar.setScaleX(0);bar.setScaleY(0);bar.setClickable(false);bar.setLongClickable(false);bar.setFocusable(false);
        bar.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        if(bar instanceof ViewGroup)((ViewGroup)bar).setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);

    }

    private static void keepDrawer(Object builder,Object hiddenProfile){
        try{
            stock.set(Boolean.TRUE);
            Object stockProfile=Xp.callMethod(builder,"build");
            if(stockProfile==null)return;
            for(String n:new String[]{"mAllAppsProfile","mResponsiveAllAppsWidthSpec","mResponsiveAllAppsHeightSpec","mResponsiveAllAppsCellSpec","mResponsiveFolderWidthSpec","mResponsiveFolderHeightSpec","mFolderProfile"}){
                try{Field f=field(hiddenProfile.getClass(),n);f.set(hiddenProfile,f.get(stockProfile));}
                catch(Throwable t){if(!drawerWarned){drawerWarned=true;android.util.Log.w(TAG,"Drawer value "+n+" not kept",t);}}
            }
        }catch(Throwable t){if(!drawerWarned){drawerWarned=true;android.util.Log.w(TAG,"Stock drawer build failed",t);}}
        finally{stock.remove();}
    }

    static void hideFromMenu(final View bar){
        final Context c=bar.getContext();
        if(!ready){android.widget.Toast.makeText(c,I18n.cs("Hide search bar isn't supported on this launcher version"),android.widget.Toast.LENGTH_SHORT).show();return;}
        try{Settings.Secure.putInt(c.getContentResolver(),LauncherFeatures.HIDE_SEARCH,1);}
        catch(Throwable t){android.util.Log.w(TAG,"Couldn't save Hide search bar",t);android.widget.Toast.makeText(c,I18n.cs("Couldn't hide it here. Turn on Hide search bar in Pixel Suite."),android.widget.Toast.LENGTH_LONG).show();return;}
        enabled=true;read=true;
        final android.app.Activity a;
        try{a=(android.app.Activity)Xp.callStaticMethod(Xp.findClass("com.android.launcher3.views.ActivityContext",loader),"lookupContext",c);}
        catch(Throwable t){warn(t);return;}
        if(!(bar.getParent() instanceof ViewGroup)){restart(a);return;}
        final ViewGroup hotseat=(ViewGroup)bar.getParent();
        LauncherHideMotion.run(a,hotseat,bar,new LauncherHideMotion.Apply(){public boolean apply(){return applyLive(a,hotseat,bar);}},new Runnable(){public void run(){restart(a);}});
    }

    private static boolean applyLive(android.app.Activity a,ViewGroup hotseat,View bar){
        try{
            Field q=field(hotseat.getClass(),"mQsb");
            if(legacy)shrink(bar);
            else if(q.get(hotseat)==bar){
                View empty=new EmptySearch(a);
                hotseat.removeView(bar);q.set(hotseat,empty);hotseat.addView(empty);
            }
            Object idp=get(Xp.callMethod(a,"getDeviceProfile"),"inv");
            Object prefs=get(idp,"mPrefs");
            Object item=Xp.findClass("com.android.launcher3.LauncherPrefs",loader).getField("GRID_NAME").get(null);
            Method inner=null;
            for(Class<?> k=prefs.getClass();k!=null&&inner==null;k=k.getSuperclass())for(Method m:k.getDeclaredMethods())if(m.getName().equals("getInner")&&m.getParameterTypes().length==2){inner=m;break;}
            if(inner==null)throw new NoSuchMethodException("getInner");
            inner.setAccessible(true);
            Object grid=inner.invoke(prefs,item,get(item,"defaultValue"));
            Xp.callMethod(idp,"initGrid",grid);
            boolean changed=Boolean.TRUE.equals(Xp.callMethod(a,"initDeviceProfile",idp));
            Object dp=Xp.callMethod(a,"getDeviceProfile");
            if(!Boolean.TRUE.equals(Xp.getAdditionalInstanceField(get(dp,"mHotseatProfile"),"ps_hidden_search"))){android.util.Log.w(TAG,"Live layout: profile without the bar wasn't built (changed="+changed+")");return false;}
            if(changed){
                Xp.callMethod(a,"dispatchDeviceProfileChanged");
                Xp.callMethod(a,"reapplyUi");
                try{Xp.callMethod(get(a,"mDragLayer"),"recreateControllers");}catch(Throwable ignored){}
            }
            ViewGroup workspace=(ViewGroup)get(a,"mWorkspace");
            for(int i=0;i<workspace.getChildCount();i++){View cell=workspace.getChildAt(i);try{Xp.callMethod(cell,"resetCellSizeInternal",dp);}catch(Throwable ignored){}cell.requestLayout();}
            try{Xp.callMethod(hotseat,"resetCellSizeInternal",dp);}catch(Throwable ignored){}
            hotseat.requestLayout();workspace.requestLayout();
            return true;
        }catch(Throwable t){android.util.Log.w(TAG,"Live layout failed",t);return false;}
    }

    private static void restart(android.app.Activity a){
        try{Object idp=get(Xp.callMethod(a,"getDeviceProfile"),"inv");Xp.callMethod(idp,"onConfigChanged");return;}
        catch(Throwable t){android.util.Log.w(TAG,"Couldn't rebuild the profile",t);}
        try{a.recreate();}catch(Throwable t){warn(t);}
    }

    private static final class EmptySearch extends View {
        EmptySearch(Context c){super(c);super.setVisibility(GONE);setAlpha(0);setClickable(false);setLongClickable(false);setFocusable(false);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);}
        @Override public void setVisibility(int visibility){super.setVisibility(GONE);}
        @Override protected void onMeasure(int w,int h){setMeasuredDimension(0,0);}
    }
}
