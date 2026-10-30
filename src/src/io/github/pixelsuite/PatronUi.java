package io.github.pixelsuite;
import android.app.Activity;import android.view.*;import android.widget.*;
final class PatronUi {
 private PatronUi(){}
 private static void prompt(Activity a,String key){if(WatchEsim.KEY.equals(key))EsimPurchase.open(a);else PatronPaper.open(a,0,null);}
 static void decorate(final Activity a,LinearLayout row,final String key){
  if(!PatronAccess.protectedKey(key))return;row.setTag("paid.row:"+key);boolean watch=WatchEsim.KEY.equals(key);
  ImageView badge=new DollarBadgeView(a,watch);badge.setContentDescription(watch?"$10 one-time eSIM purchase":"Patreon feature");badge.setTag("patron.badge");
  View last=row.getChildAt(row.getChildCount()-1);if(last instanceof Switch){ViewGroup.LayoutParams original=last.getLayoutParams();float d=a.getResources().getDisplayMetrics().density;original.width=Math.max(Math.round(64*d),((Switch)last).getSwitchMinWidth()+last.getPaddingLeft()+last.getPaddingRight());original.height=Math.round(48*d);
   row.removeView(last);FrameLayout slot=new FrameLayout(a);last.setTag("patron.switch");slot.addView(last,new FrameLayout.LayoutParams(-2,-2,Gravity.CENTER));
   View cover=new View(a);cover.setTag("patron.cover");cover.setContentDescription(watch?"Unlock Watch eSIM · $10 one-time purchase":"Unlock this feature with Patreon");cover.setClickable(true);cover.setFocusable(true);cover.setOnClickListener(v->prompt(a,key));slot.addView(cover,new FrameLayout.LayoutParams(-1,-1));row.addView(slot,original);
  }
  int size=Math.round(24*a.getResources().getDisplayMetrics().density);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(size,Math.round(32*a.getResources().getDisplayMetrics().density));lp.leftMargin=size/3;row.addView(badge,Math.max(0,row.getChildCount()-1),lp);apply(row);
 }
 static void navigationBadge(Activity a,LinearLayout row,String title){ImageView badge=new DollarBadgeView(a,"Pixel watch".equals(title),"Google apps".equals(title));badge.setTag("patron.badge");badge.setContentDescription("Pixel watch".equals(title)?"$10 one-time eSIM purchase":"Patreon feature");float d=a.getResources().getDisplayMetrics().density;LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(Math.round(28*d),Math.round(34*d));lp.leftMargin=Math.round(8*d);lp.rightMargin=Math.round(2*d);row.addView(badge,Math.max(0,row.getChildCount()-1),lp);apply(row);}
 static void apply(View view){apply(view,null);}
 private static void apply(View view,String inherited){Object raw=view.getTag();String tag=raw instanceof String?(String)raw:"";String key=tag.startsWith("paid.row:")?tag.substring(9):inherited;boolean allowed=key==null?PatronAccess.unlocked():PatronAccess.allowed(view.getContext(),key);
  if("patron.badge".equals(tag)){view.setVisibility(View.VISIBLE);view.setAlpha(1);}
  if("patron.cover".equals(tag))view.setVisibility(allowed?View.GONE:View.VISIBLE);
  if("patron.switch".equals(tag)&&!allowed)view.setEnabled(false);
  if(tag.startsWith("paid.row:")){view.setAlpha(1);ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){View child=group.getChildAt(i);child.setAlpha("patron.badge".equals(child.getTag())?1:allowed?1:.68f);}}
  if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)apply(group.getChildAt(i),key);}view.invalidate();
 }
 static boolean intercept(Activity a,String key){if(!PatronAccess.protectedKey(key)||PatronAccess.allowed(a,key))return false;prompt(a,key);return true;}
}
