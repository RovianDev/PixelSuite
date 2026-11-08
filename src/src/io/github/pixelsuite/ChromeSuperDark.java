package io.github.pixelsuite;

import android.app.Activity;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.WeakHashMap;

final class ChromeSuperDark {
    private ChromeSuperDark() {}
    private static WeakReference<View> toolbar = new WeakReference<View>(null);
    private static volatile boolean incognito;
    private static volatile boolean urlFocused;
    private static volatile long blendUntil;
    private static boolean blending(){return urlFocused||android.os.SystemClock.uptimeMillis()<blendUntil;}
    private static final WeakHashMap<Object,Object> originals = new WeakHashMap<Object,Object>();
    private static final WeakHashMap<View,Boolean> watched = new WeakHashMap<View,Boolean>();

    private static int mapped(int c) {
        int rgb=c&0xffffff;
        if(rgb==0x121212||rgb==0x131314||rgb==0x282a2c||rgb==0x121213)return c&0xff000000;
        if(rgb==0x1e1f20||rgb==0x333537||rgb==0x1b1b1b||rgb==0x323436)return (c&0xff000000)|0x0a0a0a;
        if(blending())for(int[] l:FOCUS){float t=along(rgb,l[0],l[1]);if(t>=0f)return (c&0xff000000)|ChromeTabTransition.mix(0xff000000|l[2],0xff000000|l[3],t)&0xffffff;}
        return c;
    }
    private static final int[][] FOCUS={{0x131314,0x282a2c,0x000000,0x000000},{0x333537,0x1b1b1b,0x0a0a0a,0x0a0a0a}};
    private static float along(int rgb,int a,int b){
        int[] ca={(a>>16)&255,(a>>8)&255,a&255},cb={(b>>16)&255,(b>>8)&255,b&255},cc={(rgb>>16)&255,(rgb>>8)&255,rgb&255};
        int k=0;for(int i=1;i<3;i++)if(Math.abs(cb[i]-ca[i])>Math.abs(cb[k]-ca[k]))k=i;
        if(cb[k]==ca[k])return -1f;
        float t=(cc[k]-ca[k])/(float)(cb[k]-ca[k]);
        if(t<0f||t>1f)return -1f;
        for(int i=0;i<3;i++)if(Math.abs(ca[i]+(cb[i]-ca[i])*t-cc[i])>1.01f)return -1f;
        return t;
    }
    private static volatile long holdUntil;
    static void hold(long ms){long u=android.os.SystemClock.uptimeMillis()+ms;if(u>holdUntil)holdUntil=u;}
    static boolean incognitoNow(){return incognito||android.os.SystemClock.uptimeMillis()<holdUntil;}
    private static boolean active(){return incognitoNow()&&SuperDark.inEffectNow();}
    static boolean incognitoSelected(){return incognito;}
    static int darken(int c){return SuperDark.inEffectNow()?mapped(c):c;}
    static int mapActive(int c){return active()?mapped(c):c;}
    private static View findToolbar(View v){
        if(v.getClass().getName().equals("org.chromium.chrome.browser.toolbar.top.ToolbarPhone"))return v;
        if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){View r=findToolbar(g.getChildAt(i));if(r!=null)return r;}}
        return null;
    }
    private static boolean mode(View v)throws Exception{
        for(Class<?> c=v.getClass();c!=null&&c!=View.class;c=c.getSuperclass())for(Field f:c.getDeclaredFields()){
            if(f.getType().isPrimitive())continue;
            Method m;try{m=f.getType().getMethod("isIncognito");}catch(NoSuchMethodException e){continue;}
            if(m.getReturnType()!=boolean.class)continue;
            f.setAccessible(true);Object model=f.get(v);if(model==null)continue;m.setAccessible(true);return (Boolean)m.invoke(model);
        }
        throw new NoSuchMethodException();
    }
    private static void watch(final Activity a){
        final View root=a.getWindow().getDecorView();
        if(watched.put(root,Boolean.TRUE)!=null)return;
        root.getViewTreeObserver().addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener(){
            public boolean onPreDraw(){
                try{View t=toolbar.get();if(t==null||t.getRootView()!=root){t=findToolbar(root);toolbar=new WeakReference<View>(t);}
                    if(t!=null){boolean was=incognito;incognito=mode(t);if(was!=incognito)switchUntil=android.os.SystemClock.uptimeMillis()+800;}
                    boolean f=t!=null&&t.hasFocus();if(f!=urlFocused){urlFocused=f;blendUntil=android.os.SystemClock.uptimeMillis()+700;}}catch(Throwable ignored){incognito=false;}
                return true;
            }
        });
    }
    private static boolean leftover(int c){int rgb=c&0xffffff;return rgb==0x121212||rgb==0x131314||rgb==0x282a2c||rgb==0x333537||rgb==0x121213||rgb==0x323436;}
    private static final int[][] HUB={{0x121213,0x000000,0x000000,0x000000},{0x323436,0x1a1a1a,0x0a0a0a,0x1a1a1a}};
    private static volatile long switchUntil;
    private static boolean switchingNow(){return android.os.SystemClock.uptimeMillis()<switchUntil&&SuperDark.inEffectNow();}
    private static int hub(int c){int rgb=c&0xffffff;for(int[] l:HUB){float t=along(rgb,l[0],l[1]);if(t>=0f)return (c&0xff000000)|(ChromeTabTransition.mix(0xff000000|l[2],0xff000000|l[3],t)&0xffffff);}return c;}
    private static int mapNow(int c){if(active())return mapped(c);if(leftover(c)&&SuperDark.inEffectNow())return mapped(c);return c;}
    private static int mapDrawn(Object d,int c){if(switchingNow()&&inHub(d)){int h=hub(c);if(h!=c)return h;}return mapNow(c);}
    private static final WeakHashMap<Object,Boolean> hubOf=new WeakHashMap<Object,Boolean>();
    private static boolean inHub(Object d){
        Boolean k=hubOf.get(d);if(k!=null)return k;
        boolean yes=false;
        try{Object cb=((android.graphics.drawable.Drawable)d).getCallback();View v=cb instanceof View?(View)cb:null;
            for(int i=0;i<10&&v!=null;i++){
                if(v.getId()!=View.NO_ID){try{String n=v.getResources().getResourceEntryName(v.getId());if(n.startsWith("hub_")||"pane_frame".equals(n)){yes=true;break;}}catch(Throwable ignored){}}
                v=v.getParent() instanceof View?(View)v.getParent():null;}
            if(!yes&&cb!=null&&cb.getClass()==View.class){View b=(View)cb;if(b.getWidth()<=0)return false;int[] at=new int[2];b.getLocationOnScreen(at);
                android.util.DisplayMetrics dm=b.getResources().getDisplayMetrics();
                yes=b.getWidth()>=dm.widthPixels*0.9f&&b.getHeight()<=96*dm.density&&at[1]+b.getHeight()>=dm.heightPixels-2;}
        }catch(Throwable ignored){}
        if(((android.graphics.drawable.Drawable)d).getCallback()!=null)hubOf.put(d,yes);
        return yes;
    }
    private static final Xp.Callback ARG=new Xp.Callback(){
        @Override protected void beforeHookedMethod(Xp.Param p){if(p.args.length>0&&p.args[0] instanceof Integer)p.args[0]=mapNow((Integer)p.args[0]);}
    };
    private static boolean sNatives;
    static void natives(ClassLoader cl){
        if(sNatives)return;sNatives=true;
        final java.util.Map<String,int[]> args=new java.util.HashMap<String,int[]>();
        args.put("VFFFIIIIJOOZZZ",new int[]{4});args.put("VFFIIJOZ",new int[]{3});args.put("VFIIIIJOZ",new int[]{4});args.put("VIJ",new int[]{1});
        try{Class<?> k=Class.forName("J.N",false,cl);
            for(final Method m:k.getDeclaredMethods()){final int[] at=args.get(m.getName());if(at==null)continue;
                try{Xp.hookMethod(m,new Xp.Callback(){@Override protected void beforeHookedMethod(Xp.Param p){
                    try{for(int i:at){if(i>=p.args.length||!(p.args[i] instanceof Integer))continue;int c=(Integer)p.args[i];if((c>>>24)!=0xff)continue;
                        int next=nativeColor(c);if(next!=c)p.args[i]=next;}}catch(Throwable ignored){}
                }});}catch(Throwable ignored){}}
        }catch(Throwable ignored){}
    }
    private static int nativeColor(int c){
        if(!SuperDark.inEffectNow())return c;
        if(leftover(c))return mapped(c);
        if(switchingNow()){int h=hub(c);if(h!=c)return h;}
        return c;
    }
    private static View menuList(View v){
        try{if("app_menu_list".equals(v.getResources().getResourceEntryName(v.getId())))return v;}catch(Throwable ignored){}
        if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){View r=menuList(g.getChildAt(i));if(r!=null)return r;}}return null;
    }
    private static void menuInk(View v){
        android.content.res.ColorStateList ink=new android.content.res.ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{}},new int[]{0xff777777,0xffe3e3e3});
        if(v.getBackground() instanceof android.graphics.drawable.RippleDrawable){android.graphics.drawable.RippleDrawable r=(android.graphics.drawable.RippleDrawable)v.getBackground();r.setColor(android.content.res.ColorStateList.valueOf(0x33ffffff));for(int i=0;i<r.getNumberOfLayers();i++)if(r.getId(i)!=android.R.id.mask)r.getDrawable(i).mutate().setTint(0xff303134);}
        if(v instanceof ViewGroup&&v.getBackground()!=null&&!(v.getBackground() instanceof android.graphics.drawable.RippleDrawable))v.getBackground().mutate().setTint(SuperDark.inEffectNow()?0xff0a0a0a:0xff1e1f20);
        if(v instanceof android.widget.TextView){android.widget.TextView t=(android.widget.TextView)v;if(t.getTextColors().getDefaultColor()!=0xffe3e3e3)t.setTextColor(ink);for(android.graphics.drawable.Drawable d:t.getCompoundDrawablesRelative())if(d!=null){d.clearColorFilter();d.setTintList(ink);}}
        if(v instanceof android.widget.Button){android.content.res.ColorStateList bg=v.getBackgroundTintList();if(bg==null||bg.getDefaultColor()!=0xff303134)v.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff303134));}
        if(v instanceof android.widget.ImageView){android.widget.ImageView i=(android.widget.ImageView)v;android.content.res.ColorStateList old=i.getImageTintList();if(old==null||old.getDefaultColor()!=0xffe3e3e3)i.setImageTintList(ink);
            if(v.getBackground()!=null){android.content.res.ColorStateList bg=v.getBackgroundTintList();if(bg==null||bg.getDefaultColor()!=0xff303134)v.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff303134));}}
        if(v instanceof android.widget.ListView)((android.widget.ListView)v).setCacheColorHint(0);
        if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++)menuInk(g.getChildAt(i));}
    }
    private static void menu(final android.widget.PopupWindow popup){
        if(!incognito)return;final View root=popup.getContentView();if(root==null||menuList(root)==null)return;
        final int fill=SuperDark.inEffectNow()?0xff0a0a0a:0xff1e1f20;
        android.graphics.drawable.Drawable bg=popup.getBackground();if(bg!=null){bg=bg.mutate();bg.setTint(fill);popup.setBackgroundDrawable(bg);}
        menuInk(root);
        root.getViewTreeObserver().addOnGlobalLayoutListener(new android.view.ViewTreeObserver.OnGlobalLayoutListener(){public void onGlobalLayout(){menuInk(root);}});
    }
    static void install(){
        try{Xp.hookAllMethods(Activity.class,"onPostResume",new Xp.Callback(){@Override protected void afterHookedMethod(Xp.Param p){try{Activity a=(Activity)p.thisObject;if(a.getClass().getName().startsWith("org.chromium.chrome."))watch(a);}catch(Throwable ignored){}}});}catch(Throwable ignored){}
        try{Xp.hookAllMethods(ColorDrawable.class,"draw",new Xp.Callback(){@Override protected void beforeHookedMethod(Xp.Param p){
            ColorDrawable d=(ColorDrawable)p.thisObject;int[] o=(int[])originals.get(d);int now=d.getColor();
            if(o!=null&&now!=o[1]){originals.remove(d);o=null;}
            if(o==null){if(mapDrawn(d,now)==now)return;o=new int[]{now,now};originals.put(d,o);}
            int next=mapDrawn(d,o[0]);if(now!=next){o[1]=next;d.setColor(next);}
            if(next==o[0])originals.remove(d);
        }});}catch(Throwable ignored){}
        try{final Method get=GradientDrawable.class.getMethod("getColor");Xp.hookAllMethods(GradientDrawable.class,"draw",new Xp.Callback(){@Override protected void beforeHookedMethod(Xp.Param p)throws Throwable{
            GradientDrawable d=(GradientDrawable)p.thisObject;android.content.res.ColorStateList now=(android.content.res.ColorStateList)get.invoke(d);if(now==null||now.isStateful())return;
            Object[] o=(Object[])originals.get(d);
            if(o!=null&&now.getDefaultColor()!=(Integer)o[1]){originals.remove(d);o=null;}
            if(o==null){if(mapDrawn(d,now.getDefaultColor())==now.getDefaultColor())return;o=new Object[]{now,now.getDefaultColor()};originals.put(d,o);}
            android.content.res.ColorStateList original=(android.content.res.ColorStateList)o[0];
            int next=mapDrawn(d,original.getDefaultColor());if(now.getDefaultColor()!=next){o[1]=next;d.setColor(next);}
            if(next==original.getDefaultColor())originals.remove(d);
        }});}catch(Throwable ignored){}
        try{Xp.hookAllMethods(android.graphics.Canvas.class,"drawColor",ARG);}catch(Throwable ignored){}
        Xp.Callback paint=new Xp.Callback(){@Override protected void beforeHookedMethod(Xp.Param p){
            if(p.args.length==0||!(p.args[0] instanceof android.graphics.Paint)||!SuperDark.inEffectNow())return;
            android.graphics.Paint source=(android.graphics.Paint)p.args[0];int next=mapNow(source.getColor());
            if(switchingNow()){try{android.graphics.Rect clip=((android.graphics.Canvas)p.thisObject).getClipBounds();if(clip.top==0&&clip.height()<Math.round(96*android.content.res.Resources.getSystem().getDisplayMetrics().density)){int h=hub(source.getColor());if(h!=source.getColor())next=h;}}catch(Throwable ignored){}}
            if(next==source.getColor())return;
            android.graphics.Paint copy=new android.graphics.Paint(source);copy.setColor(next);p.args[0]=copy;
        }};
        for(String name:new String[]{"android.graphics.Canvas","android.graphics.BaseCanvas","android.graphics.BaseRecordingCanvas"})try{Xp.hookAllMethods(Class.forName(name),"drawPaint",paint);}catch(Throwable ignored){}
        try{Class<?> w=Class.forName("com.android.internal.policy.PhoneWindow");Xp.hookAllMethods(w,"setStatusBarColor",ARG);Xp.hookAllMethods(w,"setNavigationBarColor",ARG);}catch(Throwable ignored){}
        Xp.Callback popup=new Xp.Callback(){@Override protected void beforeHookedMethod(Xp.Param p){try{menu((android.widget.PopupWindow)p.thisObject);}catch(Throwable ignored){}}};
        try{Xp.hookAllMethods(android.widget.PopupWindow.class,"showAtLocation",popup);Xp.hookAllMethods(android.widget.PopupWindow.class,"showAsDropDown",popup);}catch(Throwable ignored){}
    }
}
