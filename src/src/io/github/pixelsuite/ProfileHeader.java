package io.github.pixelsuite;
import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;
final class ProfileHeader extends LinearLayout {
 private final boolean dark,black;
 private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
 private TextView label(Activity a,String value,int size,int color,String family){TextView t=new TextView(a);t.setText(I18n.cs(value));t.setTextSize(size);t.setTextColor(color);t.setTypeface(Typeface.create(family,0));t.setIncludeFontPadding(false);t.setSingleLine(true);t.setGravity(Gravity.CENTER);return t;}
 ProfileHeader(Activity a){super(a);dark=FrostedSurface.dark(a);black=AppSurfaces.active(a);setOrientation(VERTICAL);setGravity(Gravity.CENTER_HORIZONTAL);setPadding(dp(4),dp(18),dp(4),dp(32));
  int ink=dark||black?0xffe6e6ea:0xff1f1f1f,secondary=dark||black?0xffc8c8cf:0xff3f3f45;
  final int page=a instanceof SettingsActivity?((SettingsActivity)a).pageColor():(black?0xff000000:dark?0xff121212:0xfff2f0f5);
  LinearLayout title=new LinearLayout(a);title.setGravity(Gravity.CENTER);title.setBaselineAligned(false);
  TextView pixel=label(a,"Pixel",34,ink,"sans-serif"),suite=label(a,"Suite",34,ink,"sans-serif");pixel.setLetterSpacing(-.015f);suite.setLetterSpacing(-.015f);title.addView(pixel);
  FrameLayout logo=new FrameLayout(a);logo.setContentDescription(I18n.cs("Pixel Suite logo"));
  final AppFrost logoGlass=AppFrost.pill(logo,new View(a),dp(15),page);logo.setBackground(logoGlass);
  logo.setOutlineProvider(new ViewOutlineProvider(){@Override public void getOutline(View v,Outline o){o.setOval(0,0,v.getWidth(),v.getHeight());}});logo.setElevation(black?0:dp(3));
  logo.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){public void onViewAttachedToWindow(View v){}public void onViewDetachedFromWindow(View v){logoGlass.release();}});
  ImageView mark=new ImageView(a);mark.setImageDrawable(SuiteIcon.live(a,ink));mark.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);logo.addView(mark,new FrameLayout.LayoutParams(-1,-1));
  PixelStack.attach(a,logo,mark,pixel,suite,ink,page,dark,black);
  LayoutParams lp=new LayoutParams(dp(30),dp(30));lp.leftMargin=lp.rightMargin=dp(7);title.addView(logo,lp);title.addView(suite);addView(title);
  TextView tagline=label(a,HomeLayout.TAGLINE,18,dark||black?0xffa8a8b0:0xff55555c,"sans-serif");lp=new LayoutParams(-2,-2);lp.topMargin=dp(8);addView(tagline,lp);
  PatronIdentity identity=new PatronIdentity(a,secondary);identity.setTranslationY(-dp(3)); lp=new LayoutParams(-1,-2);lp.topMargin=dp(34);addView(identity,lp);
  LinearLayout buttons=new LinearLayout(a);buttons.setGravity(Gravity.CENTER);int[] order={0,2,1};for(int at=0;at<3;at++){final int index=order[at];LinearLayout column=new LinearLayout(a);column.setOrientation(VERTICAL);column.setGravity(Gravity.CENTER_HORIZONTAL);column.setClickable(true);column.setFocusable(true);column.setContentDescription(I18n.cs(index==1?"Buy me a coffee":CommunityLinks.NAMES[index]));column.setOnClickListener(v->CommunityLinks.open(a,index));
   FrameLayout circle=new FrameLayout(a);GradientDrawable mask=new GradientDrawable();mask.setShape(GradientDrawable.OVAL);mask.setColor(Color.WHITE);
   final AppFrost glass=AppFrost.pill(circle,new View(a),dp(24),page);circle.setBackground(new RippleDrawable(ColorStateList.valueOf(dark||black?0x33ffffff:0x22000000),glass,mask));
   circle.setOutlineProvider(new ViewOutlineProvider(){@Override public void getOutline(View v,Outline o){o.setOval(0,0,v.getWidth(),v.getHeight());}});circle.setElevation(black?0:dp(3));
   circle.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){public void onViewAttachedToWindow(View v){}public void onViewDetachedFromWindow(View v){glass.release();}});circle.setClickable(true);circle.setFocusable(false);circle.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);circle.setOnClickListener(v->CommunityLinks.open(a,index));
   ImageView icon=new ImageView(a);Drawable glyph=a.getResources().getDrawable(a.getResources().getIdentifier(index==0?"brand_telegram_glyph":CommunityLinks.ICONS[index],"drawable",a.getPackageName())).mutate();glyph.setTint(ink);icon.setImageDrawable(glyph);FrameLayout.LayoutParams ip=index==0?new FrameLayout.LayoutParams(dp(22),dp(22),Gravity.CENTER):new FrameLayout.LayoutParams(dp(20),dp(20),Gravity.CENTER);if(index==0){float dd=a.getResources().getDisplayMetrics().density;icon.setTranslationX(-1f*dd);icon.setTranslationY(0.5f*dd);}circle.addView(icon,ip);column.addView(circle,new LayoutParams(dp(48),dp(48)));
   TextView name=label(a,CommunityLinks.NAMES[index],12,secondary,"sans-serif");name.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));name.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);LayoutParams np=new LayoutParams(-1,-2);np.topMargin=dp(6);column.addView(name,np);LayoutParams cp=new LayoutParams(dp(76),-2);if(at>0)cp.leftMargin=dp(8);buttons.addView(column,cp);
  }lp=new LayoutParams(-2,-2);lp.topMargin=dp(21);addView(buttons,lp);
 }
}
