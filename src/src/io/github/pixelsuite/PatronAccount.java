package io.github.pixelsuite;
import android.app.Activity;
import android.app.Dialog;
import android.graphics.Typeface;
import android.graphics.Color;
import android.view.*;
import android.widget.*;
final class PatronAccount {
 private final Activity activity;private final Dialog dialog;private final LinearLayout content;private final int ink,muted;private boolean busy;
 private final Runnable changed=()->render();
 private PatronAccount(Activity a){activity=a;boolean dark=FrostedSurface.dark(a);ink=dark?0xfff2f0f5:0xff25242a;muted=dark?0xffb8b7bf:0xff62616a;
  dialog=new Dialog(a);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);content=new LinearLayout(a);content.setOrientation(1);content.setPadding(dp(22),dp(18),dp(22),dp(24));content.setBackground(AppSurfaces.sheet(a,28));
  ScrollView scroll=new ScrollView(a);ToastMotion.watchScroll(scroll);scroll.addView(content);dialog.setContentView(scroll);dialog.setOnDismissListener(d->PatronAccess.unwatch(changed));PatronAccess.watch(changed);render();dialog.show();
  Window w=dialog.getWindow();if(w!=null){w.setBackgroundDrawableResource(android.R.color.transparent);w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);AppSurfaces.dim(w,.5f);w.setGravity(Gravity.BOTTOM);AppPanelBounds.fit(a,w,-2);}
 }
 static void open(Activity a){if(!a.isFinishing()&&!a.isDestroyed())new PatronAccount(a);}
 private static final class InfinityMark extends android.text.style.ReplacementSpan {
  final android.graphics.Paint stroke=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);final android.graphics.Path loops=new android.graphics.Path();
  @Override public int getSize(android.graphics.Paint paint,CharSequence text,int start,int end,android.graphics.Paint.FontMetricsInt metrics){if(metrics!=null)paint.getFontMetricsInt(metrics);return Math.round(paint.getTextSize()*24f/14f);}
  @Override public void draw(android.graphics.Canvas canvas,CharSequence text,int start,int end,float x,int top,int baseline,int bottom,android.graphics.Paint paint){
   float width=paint.getTextSize()*24f/14f,height=paint.getTextSize()*11f/14f;android.graphics.Paint.FontMetricsInt fm=paint.getFontMetricsInt();float cy=baseline+(fm.ascent+fm.descent)/2f;
   stroke.setColor(paint.getColor());stroke.setAlpha(paint.getAlpha());stroke.setStyle(android.graphics.Paint.Style.STROKE);stroke.setStrokeWidth(paint.getTextSize()*1.6f/14f);stroke.setStrokeCap(android.graphics.Paint.Cap.ROUND);stroke.setStrokeJoin(android.graphics.Paint.Join.ROUND);
   loops.reset();loops.moveTo(width*.5f,height*.5f);loops.cubicTo(width*.32f,height*.08f,width*.06f,-height*.02f,width*.06f,height*.5f);loops.cubicTo(width*.06f,height*1.02f,width*.32f,height*.92f,width*.5f,height*.5f);loops.cubicTo(width*.68f,height*.08f,width*.94f,-height*.02f,width*.94f,height*.5f);loops.cubicTo(width*.94f,height*1.02f,width*.68f,height*.92f,width*.5f,height*.5f);
   int save=canvas.save();canvas.translate(x,cy-height/2f);canvas.drawPath(loops,stroke);canvas.restoreToCount(save);
  }
 }
 private int dp(float n){return Math.round(n*activity.getResources().getDisplayMetrics().density);}
 private TextView text(String value,int size,int color,boolean bold){TextView t=new TextView(activity);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));t.setPadding(0,dp(6),0,dp(6));return t;}
 private void button(String name,Runnable action){TextView t=text(name,16,ink,true);t.setGravity(Gravity.CENTER);t.setMinimumHeight(dp(48));t.setBackground(AppSurfaces.accountButton(activity,24));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(10);content.addView(t,lp);t.setEnabled(!busy);t.setAlpha(busy?.55f:1);t.setOnClickListener(v->action.run());}
 private void render(){if(activity.isFinishing()||activity.isDestroyed())return;content.removeAllViews();LinearLayout heading=new LinearLayout(activity);heading.setGravity(Gravity.CENTER_VERTICAL);
  heading.addView(text("Patreon account",22,ink,true),new LinearLayout.LayoutParams(0,-2,1));TextView close=text("×",27,muted,false);close.setGravity(Gravity.CENTER);AppCloseButton.style(close);close.setContentDescription("Close account");heading.addView(close,new LinearLayout.LayoutParams(dp(48),dp(48)));close.setOnClickListener(v->dialog.dismiss());content.addView(heading);
  if(!PatronAccess.loggedIn(activity)){content.addView(text("Sign in with Patreon",16,muted,false));button("Sign in with Patreon",()->{dialog.dismiss();PatronPaper.login(activity);});return;}
  content.addView(text(PatronAccess.prefs(activity).getString("name","Patreon member"),18,ink,true));
  if(PatronAccess.unlocked()){
   content.addView(text("Patron ✓",18,ink,true));boolean creator=false;try{creator="creator".equals(PatronAccess.verify(activity,PatronAccess.prefs(activity).getString("pass",""),false).optString("membership"));}catch(Exception ignored){}
   long expiry=PatronAccess.passExpiry(activity);if(creator){TextView expiration=text("Pass expires:  ∞",14,muted,false);android.text.SpannableString label=new android.text.SpannableString(expiration.getText());label.setSpan(new InfinityMark(),label.length()-1,label.length(),android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);expiration.setText(label);content.addView(expiration);}else if(expiry>0)content.addView(text("Pass expires "+java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM,java.text.DateFormat.SHORT).format(new java.util.Date(expiry*1000)),14,muted,false));
   button(busy?"Refreshing…":"Refresh",()->{busy=true;render();PatronAccess.refresh(activity,()->{busy=false;render();String error=PatronAccess.prefs(activity).getString("refresh_error",null);if(error!=null)AppToast.makeText(activity,error,Toast.LENGTH_LONG).show();else AppToast.makeText(activity,PatronAccess.unlocked()?"Membership refreshed":PatronAccess.membershipMessage(activity),Toast.LENGTH_LONG).show();});});
  }else{
   content.addView(text("A Quest for the Kind-Hearted",21,ink,true));content.addView(text(PatronAccess.membershipMessage(activity),16,muted,false));
   content.addView(text("Support Pixel Suite to unlock call recording, an ad-free Google feed and ad blocking.",15,muted,false));
   button("Read the quest",()->{dialog.dismiss();PatronPaper.open(activity,0,null);});
   button("Join on Patreon · $5/month",()->{PatronAccess.prefs(activity).edit().putBoolean("join_return",true).apply();dialog.dismiss();PatronPaper.join(activity);});
   button("Unlock",()->{dialog.dismiss();PatronPaper.login(activity);});
  }
  button("Sign out",()->{busy=true;PatronAccess.logout(activity);dialog.dismiss();});
 }
}
