package io.github.pixelsuite;
import android.app.Activity;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.view.*;
import android.widget.*;
final class HomeLinks extends LinearLayout {
 private boolean black;
 @Override protected void dispatchDraw(Canvas canvas){boolean now=AppSurfaces.active(getContext());if(now!=black){black=now;int ink=black?0xfff2f1f5:Color.BLACK;for(int i=0;i<getChildCount();i++){View child=getChildAt(i);if(child instanceof LinearLayout){LinearLayout segment=(LinearLayout)child;((ImageView)segment.getChildAt(0)).getDrawable().setTint(ink);((TextView)segment.getChildAt(1)).setTextColor(ink);}else child.setBackgroundColor(black?0x20ffffff:0x22000000);}}super.dispatchDraw(canvas);}

 HomeLinks(final Activity a){super(a);setGravity(Gravity.CENTER_VERTICAL);setPadding(dp(4),0,dp(4),0);setBackground(new ThemeGlass(a,26,ThemeGlass.SOCIAL));setLayerType(View.LAYER_TYPE_SOFTWARE,null);
  black=AppSurfaces.active(a);final int ink=black?0xfff2f1f5:Color.BLACK;
  int[] order={0,2,1};for(int at=0;at<order.length;at++){final int index=order[at];if(at>0){View line=new View(a);line.setBackgroundColor(AppSurfaces.active(a)?0x20ffffff:0x22000000);addView(line,new LayoutParams(dp(1),dp(16)));}
   LinearLayout segment=new LinearLayout(a);segment.setGravity(Gravity.CENTER);segment.setPadding(dp(3),0,dp(3),0);segment.setMinimumHeight(dp(44));
   ImageView icon=new ImageView(a);int id=a.getResources().getIdentifier(index==0?"brand_telegram_glyph":CommunityLinks.ICONS[index],"drawable",a.getPackageName());Drawable mark=a.getResources().getDrawable(id).mutate();mark.setTint(ink);icon.setImageDrawable(mark);icon.setScaleType(ImageView.ScaleType.FIT_CENTER);segment.addView(icon,new LayoutParams(dp(14),dp(16)));
   TextView label=new TextView(a);label.setText(CommunityLinks.NAMES[index]);label.setTextColor(ink);label.setTextSize(11.5f);label.setTypeface(Typeface.create("sans-serif-medium",0));label.setSingleLine(true);label.setPadding(dp(4),0,0,0);segment.addView(label);
   segment.setClickable(true);segment.setFocusable(true);segment.setContentDescription(CommunityLinks.NAMES[index]);segment.setOnClickListener(v->CommunityLinks.open(a,index));addView(segment,new LayoutParams(0,dp(44),index==0?1.14f:1f));
  }
 }
 @Override protected void onMeasure(int widthSpec,int heightSpec){super.onMeasure(View.MeasureSpec.makeMeasureSpec(Math.min(View.MeasureSpec.getSize(widthSpec),dp(232)),View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(dp(44),View.MeasureSpec.EXACTLY));}
 private int dp(float v){return Math.round(v*getResources().getDisplayMetrics().density);}
 static Drawable suiteMark(){return new Drawable(){final Paint paint=new Paint(3);public void draw(Canvas c){Rect b=getBounds();float r=Math.min(b.width(),b.height())/2f;paint.setColor(Color.BLACK);c.drawCircle(b.exactCenterX(),b.exactCenterY(),r,paint);paint.setColor(Color.WHITE);float unit=r*.37f,gap=r*.13f;for(int y=0;y<2;y++)for(int x=0;x<2;x++){float left=b.exactCenterX()+(x==0?-gap-unit:gap),top=b.exactCenterY()+(y==0?-gap-unit:gap);c.drawRect(left,top,left+unit,top+unit,paint);}}public void setAlpha(int a){paint.setAlpha(a);}public void setColorFilter(ColorFilter f){}public int getOpacity(){return PixelFormat.TRANSLUCENT;}};}
}
