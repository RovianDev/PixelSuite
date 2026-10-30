package io.github.pixelsuite;
import android.app.Activity;
import android.content.res.ColorStateList;
import android.view.View;
import android.view.ViewTreeObserver;
final class SettingsSearchTone {
 private SettingsSearchTone(){}
 static void install(){Xp.hookAllMethods(Activity.class,"onResume",new Xp.Callback(){protected void afterHookedMethod(Xp.Param p){Activity a=(Activity)p.thisObject;if(!"com.android.settings".equals(a.getPackageName())||a.getWindow()==null)return;View root=a.getWindow().getDecorView();root.post(()->attach(a));root.postDelayed(()->attach(a),500);}});}
 static android.graphics.drawable.Drawable glass(View search,int color){
  float radius=search.getHeight()>0?search.getHeight()/2f:FrostedSurface.dp(search.getContext(),36);
  int top=android.graphics.Color.rgb(Math.min(255,android.graphics.Color.red(color)+5),Math.min(255,android.graphics.Color.green(color)+5),Math.min(255,android.graphics.Color.blue(color)+5));
  android.graphics.drawable.GradientDrawable shade=new android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,new int[]{(top&0xffffff)|0xcc000000,(color&0xffffff)|0xb8000000});shade.setCornerRadius(radius);shade.setDither(true);
  android.graphics.drawable.Drawable rim=GlassRim.of(search.getContext(),radius);
  return new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[]{shade,rim});
 }
 static void attach(Activity a){try{
  int id=a.getResources().getIdentifier("search_action_bar","id","com.android.settings");View search=a.findViewById(id);if(search==null||Boolean.TRUE.equals(Xp.getAdditionalInstanceField(search,"ps_search_tone")))return;
  View card=search.getParent() instanceof View?(View)search.getParent():null;if(card==null||!card.getClass().getName().contains("CardView"))return;
  ColorStateList original=(ColorStateList)Xp.callMethod(card,"getCardBackgroundColor");ColorStateList tint=search.getBackgroundTintList();android.graphics.drawable.Drawable originalSearch=search.getBackground();
  Xp.setAdditionalInstanceField(search,"ps_search_tone",Boolean.TRUE);
  search.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener(){int last=0;
   public boolean onPreDraw(){try{boolean normal=!SuperDark.inEffect(a)&&SurfaceStyle.get(a)==SurfaceStyle.FROSTED;int color=0;
    if(normal){android.util.TypedValue v=new android.util.TypedValue();color=a.getTheme().resolveAttribute(android.R.attr.colorBackground,v,true)&&v.type>=16&&v.type<=31?v.data:0xff202124;color|=0xff000000;if((color&0xffffff)<0x101010)color=0xff202124;}
    if(color!=last){Xp.callMethod(card,"setCardBackgroundColor",normal?android.graphics.Color.TRANSPARENT:original.getDefaultColor());search.setBackgroundTintList(normal?null:tint);search.setBackground(normal?glass(search,color):originalSearch);last=color;}
   }catch(Throwable ignored){}return true;}
  });
 }catch(Throwable ignored){}}
}
