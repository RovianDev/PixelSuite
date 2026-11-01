package io.github.pixelsuite;
import android.app.Activity;
import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.regex.*;
final class ScopePanel {
 private ScopePanel(){}
 static final class Entry {final String name,pkg,detail;Entry(String n,String p,String d){name=n;pkg=p;detail=d;}}
 static java.util.List<Entry> entries(String id,String title,String note){
  LinkedHashMap<String,Entry> entries=new LinkedHashMap<String,Entry>();

  Pattern token=Pattern.compile("\\*\\*([^*]+)\\*\\* \\(([^)]+)\\)");
  for(String line:note.split("\n")){
   Matcher m=token.matcher(line);while(m.find()){
    String name=m.group(1),packages=m.group(2);
    String detail=purpose(id,title,name);
    for(String pkg:packages.split(" / ")){
     if(pkg.startsWith("android;"))pkg="system";

     if(!entries.containsKey(pkg))entries.put(pkg,new Entry(name,pkg,detail));
    }
   }
  }
  if("miscellaneous".equals(id))for(String pkg:new String[]{"org.futo.inputmethod.latin","org.futo.inputmethod.latin.playstore","org.futo.inputmethod.latin.unstable"})entries.put(pkg,new Entry("FUTO Keyboard",pkg,"Hides the keyboard globe and moves the dismiss arrow to the right."));
  return new ArrayList<Entry>(entries.values());
 }
 static java.util.List<Entry> installedEntries(Context context,java.util.List<Entry> candidates){
  LinkedHashMap<String,Entry> apps=new LinkedHashMap<String,Entry>();
  HashSet<String> packages=new HashSet<String>();
  for(Entry item:candidates){
   if(!installed(context,item.pkg))continue;
   String identity=item.name.toLowerCase(java.util.Locale.ROOT);
   Entry previous=apps.get(identity);
   if(previous==null || (!installed(context,previous.pkg) && installed(context,item.pkg)))apps.put(identity,item);
  }
  ArrayList<Entry> result=new ArrayList<Entry>();
  for(Entry item:apps.values())if(packages.add(item.pkg))result.add(item);
  return result;
 }
 private static boolean installed(Context context,String pkg){
  if("system".equals(pkg))return true;
  try{context.getPackageManager().getPackageInfo(pkg,0);return true;}catch(android.content.pm.PackageManager.NameNotFoundException absent){return false;}
 }
 private static String purpose(String id,String title,String name){
  if(NavButtons.KEY.equals(id)&&("Gboard".equals(name)||"FUTO Keyboard".equals(name)))return "Moves the keyboard dismiss button to the far right while Navigation buttons is enabled. Restart your keyboard after adding this scope.";
  if(NavButtons.KEY.equals(id)&&"Pixel Launcher".equals(name))return "Adds navigation buttons beside the gesture bar. After the module update, restart Pixel Launcher once; button choices then apply live.";
  if("themes".equals(id)||SuperDark.KEY.equals(id)){
   if("Settings".equals(name)||"Settings Services".equals(name)||"Wallpaper & style".equals(name)||"Permission Controller".equals(name)||"Digital Wellbeing".equals(name)||"Pixel Troubleshooting".equals(name)||"Personal Safety".equals(name))return "Frosted Glass soft scrolling edges on screens opened from Android Settings, including their supported sub-pages. Follows light, dark and Super Dark. Enable this scope in LSPosed and restart the app or reboot.";
   if("Pixel Launcher".equals(name))return "Applies launcher icons, folders and menus, plus the frosted soft edge behind the app drawer search area. The drawer effect is active only with Frosted Glass.";
  }
  if("IMS Stack".equals(name))return "Keeps incoming calls quiet through the Pixel calling stack while Do Not Annoy is on.";
  if("Phone Services".equals(name))return "Declines incoming calls for Do Not Annoy when the Pixel calling-stack path is unavailable.";
  if("Google".equals(name))return "google_app".equals(id)?"Removes sponsored cards and their empty space from the Google feed.":SuperDark.KEY.equals(id)?"Applies Super Dark to the Google app and feed.":"Shows customized app names and icons in home-screen Google search.";
  if("Phone".equals(name))return "Enables call recording and its controls in the Google Phone app.";
  if("Pixel watch".equals(name))return "Enables QR-code eSIM setup in the Pixel Watch phone app.";
  if("Share sheet".equals(name))return "Reorders pinned sharing apps and adds hidden-app and contact folders to Android's share sheet.";
  if("Play Store".equals(name))return "Keeps selected apps on their current version by blocking their Play Store updates.";
  if("Pixel Launcher".equals(name))return "privacy".equals(id)?"Locks apps and folders, shows padlock badges, and protects locked apps in recents.":"Enables "+title.toLowerCase(java.util.Locale.ROOT)+" in Pixel Launcher.";
  if("Pixel Studio".equals(name))return "themes".equals(id)||SuperDark.KEY.equals(id)?"Matches screenshot-editor menus and controls to your chosen theme and surface style.":"Adds Doodles and cropping tools to the screenshot editor.";
  if("Google Photos".equals(name))return "themes".equals(id)||SuperDark.KEY.equals(id)?"Matches photo-editor menus and controls to your chosen theme and surface style.":"share_menu".equals(id)?"Matches Photos' share-page colors to the share sheet.":"Adds editable Doodles to the photo editor.";
  if("Settings Services".equals(name))return "Applies Super Dark to Settings search.";
  if("Package installer".equals(name))return "Applies Super Dark to app uninstall dialogs.";
  if("Settings".equals(name))return "miscellaneous".equals(id)?"Adds the refresh-rate slider to Display & touch.":SuperDark.KEY.equals(id)?"Applies Super Dark to Settings.":"Controls Software updates and the factory-reset option in Settings.";
  if("Gboard".equals(name))return SuperDark.KEY.equals(id)?"Applies Super Dark to Gboard.":"Hides the keyboard globe and moves the dismiss arrow to the right.";
  if("Files by Google".equals(name))return "Shows newest files first in the file browser.";
  if("Files (system)".equals(name))return "Shows newest files first in the system file picker.";
  if("Chrome".equals(name))return "Removes supported empty ad boxes from web pages opened in Chrome.";
  if("System UI".equals(name)&&"launcher".equals(id))return "Places toast messages at the top, middle or stock bottom position. Changes apply to the next toast.";
  if("System UI".equals(name))return "buttons".equals(id)||Feature.VOLDOWN_CAMERA.equals(id)?"Opens the camera from the volume-down shortcut.":"privacy".equals(id)?"Shows fingerprint and PIN prompts for locked apps and folders.":"miscellaneous".equals(id)?"Keeps screenshots ready for faster capture.":"recents".equals(id)?"Fades the status bar while recent apps are viewed in landscape.":"gestures".equals(id)?"Provides cleaner screen-recording controls.":"Matches system menus and messages to your chosen theme and surface style.";
  if("System Framework".equals(name))return "buttons".equals(id)?"Detects power-button and volume-down double presses for the flashlight and camera shortcuts.":Feature.FLASHLIGHT.equals(id)?"Toggles the flashlight with a double press of the power button.":Feature.VOLDOWN_CAMERA.equals(id)?"Detects volume-down double presses while the screen is off.":"privacy".equals(id)?"Requires authentication before opening protected apps or uninstalling, disabling or pausing them.":"google_dialer".equals(id)?"Keeps calls responsive while recordings are saved.":"ota_blocker".equals(id)||"blockers".equals(id)?"Blocks system update installation and protects factory reset.":"miscellaneous".equals(id)?"Allows secure-screen capture, hides accessibility and debugging status from apps, and provides the FPS counter.":"Handles wake, sleep and screenshot gestures in Android's system process.";
  return "Enables "+title.toLowerCase(java.util.Locale.ROOT)+" in "+name+".";
 }
 static void add(final Activity activity,LinearLayout page,String id,String title,String note,int ink,int muted,int accent){
  LinearLayout panel=new LinearLayout(activity);panel.setOrientation(1);panel.setPadding(dp(activity,8),dp(activity,8),dp(activity,8),dp(activity,8));panel.setBackground(AppSurfaces.card(activity,20));
  ToastMotion.watchScroll(page);final Hint hint=new Hint(activity,accent);panel.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){public void onViewAttachedToWindow(View v){}public void onViewDetachedFromWindow(View v){hint.dismiss(false);}});
  final View heading=page.getChildCount()>0?page.getChildAt(page.getChildCount()-1):null;
  final Runnable refresh=()->{
   hint.dismiss(false);panel.removeAllViews();
   java.util.List<Entry> current=installedEntries(activity,entries(id,title,note));
   panel.setVisibility(current.isEmpty()?View.GONE:View.VISIBLE);
   if(heading!=null)heading.setVisibility(current.isEmpty()?View.GONE:View.VISIBLE);
  for(final Entry item:current){
   LinearLayout row=new LinearLayout(activity);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(activity,10),dp(activity,8),0,dp(activity,8));
   LinearLayout words=new LinearLayout(activity);words.setOrientation(1);
   TextView name=new TextView(activity);name.setText(item.name);name.setTextSize(15);name.setTypeface(Typeface.DEFAULT,Typeface.BOLD);name.setTextColor(ink);words.addView(name);
   TextView pkg=new TextView(activity);pkg.setText(item.pkg);pkg.setTextSize(12);pkg.setTextColor(muted);pkg.setPadding(0,dp(activity,3),0,0);words.addView(pkg);
   row.addView(words,new LinearLayout.LayoutParams(0,-2,1));
   TextView info=new TextView(activity);info.setText("!");info.setTextSize(17);info.setTypeface(Typeface.DEFAULT,Typeface.BOLD);info.setTextColor(accent);info.setGravity(Gravity.CENTER);info.setBackground(new SoftSurface(activity,true,accent));info.setLayerType(View.LAYER_TYPE_SOFTWARE,null);info.setClickable(true);info.setFocusable(true);info.setContentDescription("What "+item.name+" ("+item.pkg+") is used for");info.setOnClickListener(v->hint.show("**Used for:**\n"+item.detail));
   row.addView(info,new LinearLayout.LayoutParams(dp(activity,48),dp(activity,48)));panel.addView(row,new LinearLayout.LayoutParams(-1,-2));
  }
  };
  final android.content.BroadcastReceiver changes=new android.content.BroadcastReceiver(){public void onReceive(Context c,android.content.Intent intent){refresh.run();}};
  panel.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){
   boolean registered;
   public void onViewAttachedToWindow(View v){
    android.content.IntentFilter filter=new android.content.IntentFilter();filter.addAction(android.content.Intent.ACTION_PACKAGE_ADDED);filter.addAction(android.content.Intent.ACTION_PACKAGE_REMOVED);filter.addAction(android.content.Intent.ACTION_PACKAGE_REPLACED);filter.addDataScheme("package");
    try{if(android.os.Build.VERSION.SDK_INT>=33)Context.class.getMethod("registerReceiver",android.content.BroadcastReceiver.class,android.content.IntentFilter.class,int.class).invoke(activity,changes,filter,4);else activity.registerReceiver(changes,filter);registered=true;}catch(Exception ignored){}
    refresh.run();
   }
   public void onViewDetachedFromWindow(View v){if(registered){try{activity.unregisterReceiver(changes);}catch(Exception ignored){}registered=false;}}
  });
  refresh.run();
  LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(activity,16);page.addView(panel,lp);
 }
 static int dp(Context c,float value){return Math.round(value*c.getResources().getDisplayMetrics().density);}
 static final class SoftSurface extends Drawable {
  final SuperDarkPalette.Rim exactRim=new SuperDarkPalette.Rim();final Context context;final boolean circle;final int accent;final Paint paint=new Paint(3);final RectF frame=new RectF();
  SoftSurface(Context c,boolean round,int color){context=c;AppSurfaces.watch(this);circle=round;accent=color;}
  public void draw(Canvas c){boolean black=AppSurfaces.active(context),dark=FrostedSurface.dark(context);float d=context.getResources().getDisplayMetrics().density;
   frame.set(getBounds());if(circle){float cx=frame.centerX(),cy=frame.centerY();frame.set(cx-13*d,cy-13*d,cx+13*d,cy+13*d);}else frame.inset(3*d,3*d);
   float radius=circle?13*d:22*d;paint.setStyle(Paint.Style.FILL);paint.setMaskFilter(null);paint.setColor(black?(circle?0xff161616:SuperDarkPalette.BACKGROUND):dark?0xff25252b:AppSurfaces.normalCard(context));c.drawRoundRect(frame,radius,radius,paint);
   if(black){if(!circle)exactRim.draw(c,getBounds(),radius,d);return;}
   paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.4f*d);paint.setColor(black?0x70bfc1c8:dark?0x608b8d99:0x50747582);paint.setMaskFilter(new BlurMaskFilter(1.8f*d,BlurMaskFilter.Blur.NORMAL));c.drawRoundRect(frame,radius,radius,paint);paint.setMaskFilter(null);
  }
  public void setAlpha(int a){}public void setColorFilter(ColorFilter f){}public int getOpacity(){return PixelFormat.TRANSLUCENT;}
 }
 static final class Hint {
  final Activity activity;final int accent;final Handler handler=new Handler(Looper.getMainLooper());PopupWindow popup;TextView body;final Runnable hide=()->dismiss(true);
  Hint(Activity a,int color){activity=a;accent=color;}
  void show(String message){show(message,null);}
  void show(String message,View anchor){dismiss(false);if(activity.isFinishing()||activity.isDestroyed())return;
   body=new TextView(activity);body.setText(SettingsText.format(message));body.setTextSize(14);body.setTextColor(AppSurfaces.active(activity)||FrostedSurface.dark(activity)?0xfff2f0f5:0xff25242a);body.setGravity(Gravity.CENTER);body.setPadding(dp(activity,20),dp(activity,17),dp(activity,20),dp(activity,17));body.setBackground(new SoftSurface(activity,false,accent));body.setLayerType(View.LAYER_TYPE_SOFTWARE,null);body.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
   popup=new PopupWindow(body,AppPanelBounds.width(activity),-2,false);popup.setBackgroundDrawable(null);popup.setOutsideTouchable(false);popup.setClippingEnabled(true);popup.setElevation(dp(activity,3));
   popup.showAtLocation(anchor==null?activity.getWindow().getDecorView():anchor,Gravity.CENTER,0,0);body.announceForAccessibility(message.replace("**",""));
   if(AppMotion.enabled(activity)){body.setAlpha(0);body.setScaleX(.94f);body.setScaleY(.94f);body.setTranslationY(-dp(activity,8));body.animate().alpha(1).scaleX(1).scaleY(1).translationY(0).setDuration(260).setInterpolator(new android.view.animation.DecelerateInterpolator()).start();}
   ToastMotion.register(body,hide);body.setOnClickListener(v->dismiss(true));handler.postDelayed(hide,5000);
  }
  void dismiss(boolean animate){handler.removeCallbacks(hide);final PopupWindow old=popup;final TextView view=body;popup=null;body=null;if(old==null)return;
   ToastMotion.unregister(view);if(view!=null)view.animate().cancel();if(animate&&view!=null&&AppMotion.enabled(activity))view.animate().alpha(0).scaleX(.94f).scaleY(.94f).translationY(-dp(activity,5)).setDuration(180).setInterpolator(new android.view.animation.AccelerateInterpolator()).withEndAction(()->old.dismiss()).start();else old.dismiss();
  }
 }
}
