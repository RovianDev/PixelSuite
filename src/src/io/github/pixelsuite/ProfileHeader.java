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
 private TextView label(Activity a,String value,int size,int color,String family){TextView t=new TextView(a);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setTypeface(Typeface.create(family,0));t.setIncludeFontPadding(false);t.setSingleLine(true);t.setGravity(Gravity.CENTER);return t;}
 ProfileHeader(Activity a){super(a);dark=FrostedSurface.dark(a);black=AppSurfaces.active(a);setOrientation(VERTICAL);setGravity(Gravity.CENTER_HORIZONTAL);setPadding(dp(4),dp(8),dp(4),dp(32));
  int ink=dark||black?0xffe6e6ea:0xff1f1f1f,secondary=dark||black?0xffc8c8cf:0xff3f3f45,surface=black?0xff1a1a1a:dark?0xff2b2b30:Color.WHITE;
  LinearLayout title=new LinearLayout(a);title.setGravity(Gravity.CENTER);title.setBaselineAligned(false);
  TextView pixel=label(a,"Pixel",34,ink,"sans-serif"),suite=label(a,"Suite",34,ink,"sans-serif");pixel.setLetterSpacing(-.015f);suite.setLetterSpacing(-.015f);title.addView(pixel);
  ImageView logo=new ImageView(a);Drawable mark=a.getResources().getDrawable(a.getResources().getIdentifier("ic_pixelsuite_logo","drawable",a.getPackageName())).mutate();if(dark||black){ColorMatrix matrix=new ColorMatrix(new float[]{-1,0,0,0,255,0,-1,0,0,255,0,0,-1,0,255,0,0,0,1,0});mark.setColorFilter(new ColorMatrixColorFilter(matrix));}logo.setImageDrawable(mark);logo.setContentDescription("Pixel Suite logo");LayoutParams lp=new LayoutParams(dp(30),dp(30));lp.leftMargin=lp.rightMargin=dp(7);title.addView(logo,lp);title.addView(suite);addView(title);
  TextView tagline=label(a,HomeLayout.TAGLINE,18,dark||black?0xffa8a8b0:0xff55555c,"sans-serif");lp=new LayoutParams(-2,-2);lp.topMargin=dp(8);addView(tagline,lp);
  PatronIdentity identity=new PatronIdentity(a,secondary);lp=new LayoutParams(-1,-2);lp.topMargin=dp(34);addView(identity,lp);
  LinearLayout buttons=new LinearLayout(a);buttons.setGravity(Gravity.CENTER);int[] order={0,2,1};for(int at=0;at<3;at++){final int index=order[at];LinearLayout column=new LinearLayout(a);column.setOrientation(VERTICAL);column.setGravity(Gravity.CENTER_HORIZONTAL);column.setClickable(true);column.setFocusable(true);column.setContentDescription(index==1?"Buy me a coffee":CommunityLinks.NAMES[index]);column.setOnClickListener(v->CommunityLinks.open(a,index));
   FrameLayout circle=new FrameLayout(a);GradientDrawable fill=new GradientDrawable();fill.setShape(GradientDrawable.OVAL);fill.setColor(surface);GradientDrawable mask=new GradientDrawable();mask.setShape(GradientDrawable.OVAL);mask.setColor(Color.WHITE);circle.setBackground(new RippleDrawable(ColorStateList.valueOf(dark||black?0x33ffffff:0x22000000),AppSurfaces.role(a,fill,999,SuperDarkPalette.FIELD,false),mask));circle.setClickable(true);circle.setFocusable(false);circle.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);circle.setOnClickListener(v->CommunityLinks.open(a,index));
   ImageView icon=new ImageView(a);Drawable glyph=a.getResources().getDrawable(a.getResources().getIdentifier(index==0?"brand_telegram_glyph":CommunityLinks.ICONS[index],"drawable",a.getPackageName())).mutate();glyph.setTint(ink);icon.setImageDrawable(glyph);FrameLayout.LayoutParams ip=index==0?new FrameLayout.LayoutParams(dp(22),dp(22),Gravity.CENTER):new FrameLayout.LayoutParams(dp(20),dp(20),Gravity.CENTER);if(index==0){float dd=a.getResources().getDisplayMetrics().density;icon.setTranslationX(-1f*dd);icon.setTranslationY(0.5f*dd);}circle.addView(icon,ip);column.addView(circle,new LayoutParams(dp(48),dp(48)));
   TextView name=label(a,CommunityLinks.NAMES[index],12,secondary,"sans-serif");name.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));name.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);LayoutParams np=new LayoutParams(-1,-2);np.topMargin=dp(6);column.addView(name,np);LayoutParams cp=new LayoutParams(dp(76),-2);if(at>0)cp.leftMargin=dp(8);buttons.addView(column,cp);
  }lp=new LayoutParams(-2,-2);lp.topMargin=dp(21);addView(buttons,lp);
 }
}
