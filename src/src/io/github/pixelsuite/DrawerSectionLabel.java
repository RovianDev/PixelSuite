package io.github.pixelsuite;

final class DrawerSectionLabel {
 private DrawerSectionLabel(){}
 static void install(){try{
  Class<?> divider=LauncherFeatures.type("appprediction.AppsDividerView");
  Xp.hookAllMethods(divider,"setShowAllAppsLabel",new Xp.Callback(){
   protected void beforeHookedMethod(Xp.Param p){if(p.args.length==1)p.args[0]=Boolean.TRUE;}
  });
  Xp.hookAllMethods(divider,"updateDividerType",new Xp.Callback(){
   protected void beforeHookedMethod(Xp.Param p){try{LauncherFeatures.set(p.thisObject,"mShowAllAppsLabel",true);}catch(Throwable ignored){}}
  });
  Xp.hookAllMethods(divider,"onDraw",new Xp.Callback(){
   protected void beforeHookedMethod(Xp.Param p){android.view.View v=(android.view.View)p.thisObject;((android.graphics.Canvas)p.args[0]).translate(0,-3*v.getResources().getDisplayMetrics().density);}
   protected void afterHookedMethod(Xp.Param p){android.view.View v=(android.view.View)p.thisObject;((android.graphics.Canvas)p.args[0]).translate(0,3*v.getResources().getDisplayMetrics().density);}
  });
 }catch(Throwable unsupported){}}
}
