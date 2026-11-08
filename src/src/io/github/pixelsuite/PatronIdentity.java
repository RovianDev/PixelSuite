package io.github.pixelsuite;
import android.app.Activity;
import android.graphics.*;
import android.view.*;
import android.widget.*;
final class PatronIdentity extends LinearLayout {
 private final Activity activity;private final Portrait photo;private final TextView name;
 private final Runnable refresh=new Runnable(){public void run(){render();}};
 PatronIdentity(Activity a,int color){super(a);activity=a;setOrientation(VERTICAL);setGravity(Gravity.CENTER_HORIZONTAL);
  photo=new Portrait(a);addView(photo,new LayoutParams(dp(118),dp(118)));
  name=new TextView(a);name.setTextSize(18);name.setTextColor(color);name.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));name.setIncludeFontPadding(false);name.setSingleLine(true);
  LayoutParams nameSize=new LayoutParams(-2,-2);nameSize.topMargin=dp(9);addView(name,nameSize);
  setClickable(true);setFocusable(true);setOnClickListener(v->PatronAccount.open(activity));render();
 }
 private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
 private void render(){boolean connected=PatronAccess.loggedIn(activity);String label=connected?PatronAccess.prefs(activity).getString("name","Patreon account"):"Log in";name.setText(connected?label:I18n.cs(label));photo.setContentDescription(I18n.cs(connected?"Patreon profile photo":"Sign in with Patreon"));photo.load(connected,label);}
 @Override protected void onAttachedToWindow(){super.onAttachedToWindow();PatronAccess.watch(refresh);render();PatronAccess.ensureProfile(activity);}
 @Override protected void onDetachedFromWindow(){PatronAccess.unwatch(refresh);super.onDetachedFromWindow();}
 private static final class Portrait extends View {
  private final Paint paint=new Paint(3);private Bitmap bitmap;private long stamp=-1;private String letter="";private boolean signedIn;private final android.graphics.drawable.Drawable patron;
  Portrait(Activity a){super(a);patron=a.getResources().getDrawable(a.getResources().getIdentifier("brand_patreon","drawable",a.getPackageName())).mutate();}
  void load(boolean connected,String label){
   signedIn=connected;
   java.io.File file=new java.io.File(getContext().getFilesDir(),"patron-photo.png");long next=connected&&file.isFile()?file.lastModified():0;
   if(next!=stamp){stamp=next;bitmap=next==0?null:BitmapFactory.decodeFile(file.getPath());}
   letter=connected&&!label.isEmpty()?label.substring(0,1).toUpperCase(java.util.Locale.ROOT):"?";invalidate();
  }
  @Override protected void onDraw(Canvas c){super.onDraw(c);float cx=getWidth()/2f,cy=getHeight()/2f,r=Math.min(cx,cy),edge=r*.91f;
   boolean dark=FrostedSurface.dark(getContext()),black=AppSurfaces.active(getContext());edge=r-3*getResources().getDisplayMetrics().density;
   paint.setColor(black?0xff1a1a1a:dark?0xff2b2b30:Color.WHITE);c.drawCircle(cx,cy,r,paint);
   paint.setColor(black?SuperDarkPalette.FIELD:dark?0xff303947:0xffd5e6fa);c.drawCircle(cx,cy,edge,paint);
   if(bitmap!=null){BitmapShader shader=new BitmapShader(bitmap,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP);Matrix m=new Matrix();float scale=edge*2/Math.min(bitmap.getWidth(),bitmap.getHeight());m.setScale(scale,scale);m.postTranslate(cx-bitmap.getWidth()*scale/2,cy-bitmap.getHeight()*scale/2);shader.setLocalMatrix(m);paint.setShader(shader);c.drawCircle(cx,cy,edge,paint);paint.setShader(null);}
   else if(!signedIn){
    patron.setTint(dark?0xfff2f1f5:Color.BLACK);float height=r*.95f,width=height*patron.getIntrinsicWidth()/Math.max(1,patron.getIntrinsicHeight());
    patron.setBounds(Math.round(cx-width/2),Math.round(cy-height/2),Math.round(cx+width/2),Math.round(cy+height/2));patron.draw(c);
   }
   else{paint.setColor(dark?0xffdce8fa:0xff345374);paint.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));paint.setTextSize(r*.7f);paint.setTextAlign(Paint.Align.CENTER);c.drawText(letter,cx,cy-(paint.ascent()+paint.descent())/2,paint);}

  }
 }
}
