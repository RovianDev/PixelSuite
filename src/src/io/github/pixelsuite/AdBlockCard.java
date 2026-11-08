package io.github.pixelsuite;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class AdBlockCard extends LinearLayout implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final ExecutorService WORK=Executors.newSingleThreadExecutor();
    private static boolean started;
    private static volatile String restartNote;
    private final TextView status,warning,update;
    private final SharedPreferences prefs;
    private final int ink,secondary;
    private boolean attached,busy;
    private final Runnable entitlement=new Runnable(){public void run(){refresh();}};

    static synchronized void start(Context context){
        if(!PatronAccess.unlocked()||started)return;started=true;
        final Context app=context.getApplicationContext();
        WORK.execute(new Runnable(){public void run(){
            if(!AdBlock.isOn()||app.getSharedPreferences("pixelsuite_adblock",0).getInt("domains",0)==0){
                AdBlock.Result r=AdBlock.setEnabled(app,true);
                restartNote=r.needsReboot?"Restart the phone once to start blocking.":!r.ok?r.message:null;
            }else AdBlock.ensure(app);
        }});
    }

    AdBlockCard(Context c,int text,int sub){
        super(c);ink=text;secondary=sub;setOrientation(VERTICAL);setPadding(dp(22),dp(22),dp(22),dp(20));
        setBackground(AppSurfaces.card(c,26));
        TextView eyebrow=label("AD BLOCKING",12,secondary,true);eyebrow.setLetterSpacing(.12f);addView(eyebrow);
        LinearLayout hero=new LinearLayout(c);hero.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=label("Fewer ads.\nFaster pages.",27,ink,true);title.setLineSpacing(dp(1),1f);
        hero.addView(title,new LayoutParams(0,-2,1));
        ProtectionMark mark=new ProtectionMark(c);mark.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        hero.addView(mark,new LayoutParams(dp(124),dp(132)));addView(hero,new LayoutParams(-1,-2));
        TextView description=label("Blocks ads in Chrome, Google News and other apps. The lists update daily.",15,secondary,false);
        description.setLineSpacing(dp(3),1f);addView(description);
        status=label("Preparing ad protection…",14,ink,true);status.setPadding(0,dp(18),0,dp(4));status.setAccessibilityLiveRegion(ACCESSIBILITY_LIVE_REGION_POLITE);addView(status);
        warning=label("",14,secondary,false);warning.setVisibility(GONE);addView(warning);
        update=label("Update lists",15,ink,true);update.setGravity(Gravity.CENTER);update.setPadding(dp(16),dp(12),dp(16),dp(12));update.setMinimumHeight(dp(48));
        GradientDrawable button=new GradientDrawable();button.setColor(FrostedSurface.dark(c)?0xff303137:0xffe6eaf0);button.setCornerRadius(dp(24));update.setBackground(button);
        LayoutParams action=new LayoutParams(-1,-2);action.topMargin=dp(14);addView(update,action);update.setClickable(true);update.setFocusable(true);
        update.setOnClickListener(new OnClickListener(){public void onClick(View v){updateLists();}});
        TextView note=label("Works in every app and browser, also with Private DNS.",12,secondary,false);note.setPadding(0,dp(14),0,dp(10));addView(note);
        TextView credit=label("",12,secondary,false);
        credit.setText(I18n.cs(Html.fromHtml(I18n.t("Ad lists: <a href=\"https://github.com/hagezi/dns-blocklists-legacy\">HaGeZi Multi LIGHT</a> (GPL-3.0) + <a href=\"https://adaway.org/hosts.txt\">AdAway</a> (CC BY 3.0). Pixel Suite's own blocker."))));
        credit.setLinkTextColor(ink);credit.setMovementMethod(LinkMovementMethod.getInstance());addView(credit);
        prefs=c.getSharedPreferences("pixelsuite_adblock",0);
    }
    private TextView label(String value,int size,int color,boolean bold){TextView v=new TextView(getContext());v.setText(I18n.cs(value));v.setTextSize(size);v.setTextColor(color);v.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));return v;}
    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();attached=true;PatronAccess.watch(entitlement);prefs.registerOnSharedPreferenceChangeListener(this);refresh();}
    @Override protected void onDetachedFromWindow(){attached=false;PatronAccess.unwatch(entitlement);prefs.unregisterOnSharedPreferenceChangeListener(this);super.onDetachedFromWindow();}
    @Override public void onSharedPreferenceChanged(SharedPreferences p,String key){refresh();}
    private void refresh(){
        if(!attached)return;
        if(!PatronAccess.unlocked()){status.setText(I18n.cs("Unlock with Patreon"));warning.setVisibility(GONE);update.setText(I18n.cs("Update lists"));update.setAlpha(.4f);invalidateMark(this);return;}update.setAlpha(1f);invalidateMark(this);
        WORK.execute(new Runnable(){public void run(){
            final String value=AdBlock.status(getContext());
            final String conflict=AdBlock.otherBlocker()?"AdAway or Systemless hosts is also on. Use only one blocker.":null;
            post(new Runnable(){public void run(){if(!attached)return;if(!PatronAccess.unlocked()){refresh();return;}status.setText(I18n.cs(value));String message=conflict!=null?conflict:restartNote;warning.setText(I18n.cs(message));warning.setVisibility(message==null?GONE:VISIBLE);}});
        }});
    }
    private void updateLists(){
        if(!PatronAccess.unlocked()){PatronPaper.open((android.app.Activity)getContext(),0,null);return;}
        if(busy)return;busy=true;update.setEnabled(false);update.setText(I18n.cs("Updating…"));
        final Context app=getContext().getApplicationContext();
        WORK.execute(new Runnable(){public void run(){
            final AdBlock.Result r=AdBlock.isOn()?AdBlock.update(app):AdBlock.setEnabled(app,true);
            restartNote=r.needsReboot?"Restart the phone once to start blocking.":!r.ok?r.message:null;
            post(new Runnable(){public void run(){busy=false;if(!attached)return;update.setEnabled(true);update.setText(I18n.cs("Update lists"));refresh();if(!r.ok&&r.message!=null)AppToast.makeText(getContext(),I18n.cs(r.message),Toast.LENGTH_LONG).show();}});
        }});
    }

    private static void invalidateMark(View v){v.invalidate();if(v instanceof android.view.ViewGroup){android.view.ViewGroup g=(android.view.ViewGroup)v;for(int i=0;i<g.getChildCount();i++)invalidateMark(g.getChildAt(i));}}

    private static final class ProtectionMark extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path shield=new Path(),check=new Path();
        private android.animation.ValueAnimator shine;private float progress;
        private final Runnable motionChanged=new Runnable(){public void run(){motion();invalidate();}};
        @Override protected void onAttachedToWindow(){super.onAttachedToWindow();AppMotion.watch(motionChanged);motion();}
        private final android.graphics.Matrix sweep=new android.graphics.Matrix();
        private final android.graphics.LinearGradient reflection=new android.graphics.LinearGradient(0,0,28,28,new int[]{0x00ffffff,0xbbffffff,0x00ffffff},new float[]{0,.5f,1},android.graphics.Shader.TileMode.CLAMP);
        ProtectionMark(Context c){
            super(c);
            shield.moveTo(64,28);shield.cubicTo(75,35,85,38,93,39);
            shield.lineTo(91,63);shield.cubicTo(89,82,77,94,64,101);
            shield.cubicTo(51,94,39,82,37,63);shield.lineTo(35,39);
            shield.cubicTo(43,38,53,35,64,28);shield.close();
            check.moveTo(49,64);check.lineTo(60,75);check.lineTo(80,53);
        }
        private void motion(){boolean active=isAttachedToWindow()&&hasWindowFocus()&&getWindowVisibility()==VISIBLE&&PatronAccess.unlocked()&&PatronPaper.motion(getContext());if(!active){if(shine!=null){shine.cancel();shine=null;}return;}if(shine!=null)return;shine=android.animation.ValueAnimator.ofFloat(0,1);shine.setDuration(6500);shine.setRepeatCount(-1);shine.setInterpolator(new android.view.animation.LinearInterpolator());shine.addUpdateListener(new android.animation.ValueAnimator.AnimatorUpdateListener(){public void onAnimationUpdate(android.animation.ValueAnimator a){progress=(Float)a.getAnimatedValue();invalidate();}});shine.start();}
        @Override protected void onDetachedFromWindow(){AppMotion.unwatch(motionChanged);if(shine!=null){shine.cancel();shine=null;}super.onDetachedFromWindow();}
        @Override public void onWindowFocusChanged(boolean focus){super.onWindowFocusChanged(focus);motion();}
        @Override protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);motion();}
        @Override protected void onDraw(Canvas canvas){motion();
            super.onDraw(canvas);float scale=Math.min(getWidth(),getHeight())/128f;
            canvas.save();canvas.translate((getWidth()-128*scale)/2,(getHeight()-128*scale)/2);canvas.scale(scale,scale);
            paint.setStyle(Paint.Style.FILL);paint.setColor(PatronAccess.unlocked()?SectionColors.background(1,FrostedSurface.dark(getContext())):FrostedSurface.dark(getContext())?0xff343438:0xffdddddf);
            canvas.drawCircle(64,64,59,paint);
            paint.setColor(PatronAccess.unlocked()?SectionColors.ink(1):0xff88888c);paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(5);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);
            canvas.drawPath(shield,paint);paint.setStrokeWidth(6);canvas.drawPath(check,paint);
            if(shine!=null&&progress<.38f){float position=-65+progress/.38f*220;sweep.setTranslate(position,position*.5f);reflection.setLocalMatrix(sweep);paint.setShader(reflection);paint.setStrokeWidth(5);canvas.drawPath(shield,paint);paint.setStrokeWidth(6);canvas.drawPath(check,paint);paint.setShader(null);}
            canvas.restore();
        }
    }
}
