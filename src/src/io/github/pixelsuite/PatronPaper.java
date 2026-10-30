package io.github.pixelsuite;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.*;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.view.*;
import android.view.animation.DecelerateInterpolator;
import android.widget.*;

final class PatronPaper {
    private PatronPaper(){}
    static void open(final Activity activity,final int state,String error){
        if(activity.isFinishing()||activity.isDestroyed())return;
        final boolean dark=FrostedSurface.dark(activity),black=AppSurfaces.active(activity);
        final int ink=dark?0xfff1dfbd:0xff493220,muted=dark?0xffd5c19f:0xff644a31;
        final float d=activity.getResources().getDisplayMetrics().density;
        final ScrollDialog dialog=new ScrollDialog(activity);
        SinglePage paper=new SinglePage(activity);paper.setOrientation(1);
        TextView close=text(activity,"×",26,muted,false);close.setGravity(Gravity.CENTER);AppCloseButton.style(close);close.setContentDescription("Close");close.setOnClickListener(v->dialog.dismiss());
        final WaxSeal seal=new WaxSeal(activity,state==2);LinearLayout.LayoutParams sealSize=new LinearLayout.LayoutParams(dp(d,56),dp(d,56));sealSize.gravity=Gravity.CENTER;paper.addView(seal,sealSize);
        String heading=state==2?"✨ Quest Complete!":state==1?"Welcome back!":"📜 A Quest for the Kind-Hearted";
        TextView title=state==0?new QuestTitle(activity,ink):state==2?new CompleteTitle(activity,ink):text(activity,heading,24,ink,true);title.setTypeface(Typeface.create("serif",Typeface.BOLD));title.setGravity(Gravity.CENTER);title.setMaxLines(2);paper.addView(title);paper.title=title;
        String statusText=error!=null&&!error.equals(PatronAccess.membershipMessage(activity))?error:state==2?"Patron verified":state==1?"Ready to check membership":"No paid membership found. Choose the paid tier to unlock.";
        TextView status=text(activity,statusText,14,dark?0xffedc78d:0xff805a2e,true);status.setGravity(Gravity.CENTER);status.setMaxLines(2);status.setPadding(dp(d,12),dp(d,7),dp(d,12),dp(d,7));GradientDrawable statusBackground=new GradientDrawable();statusBackground.setCornerRadius(dp(d,18));statusBackground.setColor(dark?0x344e3b27:0x1870482f);status.setBackground(statusBackground);
        LinearLayout.LayoutParams statusSize=new LinearLayout.LayoutParams(-2,-2);statusSize.gravity=Gravity.CENTER;paper.addView(status,statusSize);paper.status=status;
        LinearLayout paragraphs=new LinearLayout(activity);paragraphs.setOrientation(1);paper.paragraphs=paragraphs;
        String[] copy=state==2?new String[]{"Thank you for supporting Pixel Suite. Your Patreon features are ready."}:state==1?new String[]{"Joined? Tap Unlock to confirm your paid Patreon membership."}:new String[]{
            "Pixel Suite is built by one person, in spare hours, out of love for Pixel phones.",
            "The Google apps tools (call recording, an ad-free Google feed and the system-wide ad blocker) are unlocked for patrons. Every bit of support goes straight back into the app: keeping up with Android and Google app updates, fixing what they break, and building new features.",
            "Pixel Suite has no ads or trackers. Account and payment details are used only to verify your unlock.",
            "Join on Patreon to unlock the Patreon features, and thank you for keeping the project alive. 💙"};
        for(String paragraph:copy){TextView body=text(activity,paragraph,15,muted,false);body.setLineSpacing(0,1.3f);body.setGravity(state==0?Gravity.START:Gravity.CENTER);paragraphs.addView(body,new LinearLayout.LayoutParams(-1,-2));}
        ScrollView bodyScroll=new ScrollView(activity);bodyScroll.setFillViewport(false);bodyScroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);bodyScroll.setVerticalScrollBarEnabled(false);bodyScroll.addView(paragraphs,new ScrollView.LayoutParams(-1,-2));ToastMotion.watchScroll(bodyScroll);paper.addView(bodyScroll,new LinearLayout.LayoutParams(-1,-2));paper.bodyScroll=bodyScroll;
        LinearLayout main=new LinearLayout(activity);main.setGravity(Gravity.CENTER);main.setMinimumHeight(dp(d,52));main.setPadding(dp(d,18),0,dp(d,18),0);
        int buttonInk=dark?0xff302016:0xfffff1d5;GradientDrawable button=new GradientDrawable();button.setCornerRadius(dp(d,28));button.setColor(dark?0xffdec18c:0xff70482f);main.setBackground(button);
        if(state==0){int id=activity.getResources().getIdentifier("brand_patreon","drawable",activity.getPackageName());if(id!=0){ImageView mark=new ImageView(activity);android.graphics.drawable.Drawable logo=activity.getResources().getDrawable(id,activity.getTheme()).mutate();logo.setTint(buttonInk);mark.setImageDrawable(logo);mark.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);LinearLayout.LayoutParams iconSize=new LinearLayout.LayoutParams(dp(d,22),dp(d,24));iconSize.rightMargin=dp(d,10);main.addView(mark,iconSize);}}
        String label=state==2?"Let's go":state==1?"Unlock":"Join on Patreon";main.addView(text(activity,label,17,buttonInk,true));main.setContentDescription(label);main.setClickable(true);main.setFocusable(true);
        LinearLayout.LayoutParams buttonSize=new LinearLayout.LayoutParams(-1,dp(d,52));buttonSize.topMargin=dp(d,20);paper.addView(main,buttonSize);paper.main=main;
        main.setOnClickListener(new View.OnClickListener(){public void onClick(View v){dialog.closeWith(new Runnable(){public void run(){if(state==2)return;if(state==1){login(activity);return;}PatronAccess.prefs(activity).edit().putBoolean("join_return",true).apply();join(activity);}});}});
        if(state==0){TextView unlock=text(activity,"Already a patron? Unlock",14,ink,true);unlock.setGravity(Gravity.CENTER);unlock.setMinHeight(dp(d,48));paper.addView(unlock,new LinearLayout.LayoutParams(-1,-2));paper.unlock=unlock;unlock.setOnClickListener(new View.OnClickListener(){public void onClick(View v){dialog.closeWith(new Runnable(){public void run(){login(activity);}});}});}
        final ScrollPanel panel=new ScrollPanel(activity,dark,black);panel.addView(paper,new FrameLayout.LayoutParams(-1,-2));FrameLayout.LayoutParams closeSize=new FrameLayout.LayoutParams(dp(d,48),dp(d,48),Gravity.TOP|Gravity.RIGHT);closeSize.topMargin=dp(d,25);closeSize.rightMargin=dp(d,25);panel.addView(close,closeSize);dialog.panel=panel;
        int scrollWidth=activity.getResources().getDisplayMetrics().widthPixels-dp(d,32);
        if(paper.naturalHeightForWidth(scrollWidth)>availableHeight(activity))scrollWidth=activity.getResources().getDisplayMetrics().widthPixels-dp(d,24);
        dialog.setContentView(panel);dialog.setCanceledOnTouchOutside(true);dialog.show();
        Window window=dialog.getWindow();if(window!=null){window.setWindowAnimations(0);window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));AppPanelBounds.fit(activity,window,scrollWidth,-2);window.setGravity(Gravity.CENTER);window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);AppSurfaces.dim(window,.56f);
            if(FrostedSurface.blurEnabled(activity)&&!black)try{WindowManager.LayoutParams lp=window.getAttributes();lp.getClass().getMethod("setBlurBehindRadius",int.class).invoke(lp,dp(d,20));lp.flags|=4;window.setAttributes(lp);}catch(Exception ignored){}
        }
        panel.post(new Runnable(){public void run(){if(activity.isFinishing()||activity.isDestroyed())return;panel.reveal(true,new Runnable(){public void run(){if(state==2)seal.celebrate();}});}});
    }
    static boolean motion(Context c){return AppMotion.enabled(c);}
    static final String JOIN_URL="https://www.patreon.com/checkout/RovianDev?rid=29923672&vanity=16907904";
    static void join(Activity activity){
        Intent open=new Intent(Intent.ACTION_VIEW,Uri.parse(JOIN_URL)).addCategory(Intent.CATEGORY_BROWSABLE);
        Intent generic=new Intent(Intent.ACTION_VIEW,Uri.parse("https://example.com")).addCategory(Intent.CATEGORY_BROWSABLE);
        android.content.pm.ResolveInfo browser=activity.getPackageManager().resolveActivity(generic,android.content.pm.PackageManager.MATCH_DEFAULT_ONLY);
        if(browser!=null&&browser.activityInfo!=null&&!"android".equals(browser.activityInfo.packageName))open.setPackage(browser.activityInfo.packageName);
        try{activity.startActivity(open);}catch(Exception unavailable){AppToast.makeText(activity,"Open the Pixel Suite paid tier in your browser.",0).show();}
    }
    static void login(final Activity activity){
        if(PatronAccess.hasSession(activity)){
            AppToast.makeText(activity,"Checking Patreon membership…",0).show();
            PatronAccess.refresh(activity,new Runnable(){public void run(){if(activity.isFinishing()||activity.isDestroyed())return;String error=PatronAccess.prefs(activity).getString("refresh_error",null);if(error!=null){open(activity,0,error);return;}if(PatronAccess.unlocked())open(activity,2,null);else open(activity,0,PatronAccess.membershipMessage(activity));}});return;
        }
        authorize(activity);
    }
    private static void authorize(Activity activity){
        if(!PatronAccess.configured()){AppToast.makeText(activity,"Patreon sign-in is being set up.",Toast.LENGTH_LONG).show();return;}
        String state=PatronAccess.random();PatronAccess.prefs(activity).edit().putString("nonce",state).putLong("login_at",System.currentTimeMillis()).commit();
        Uri login=Uri.parse(PatronConfig.SERVER+"/login").buildUpon().appendQueryParameter("state",state).appendQueryParameter("device",PatronAccess.device(activity)).build();
        try{activity.startActivity(new Intent(Intent.ACTION_VIEW,login));}catch(Exception unavailable){AppToast.makeText(activity,"Open a browser to sign in with Patreon.",0).show();}
    }
    private static int dp(float d,float n){return Math.round(d*n);}
    private static TextView text(Activity a,String s,int size,int color,boolean bold){TextView t=new TextView(a);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));return t;}

    private static final class CompleteTitle extends TextView {
        final Paint star=new Paint(Paint.ANTI_ALIAS_FLAG);
        CompleteTitle(Activity activity,int ink){super(activity);setText("Quest Complete!");setTextSize(24);setTextColor(ink);setContentDescription("✨ Quest Complete!");star.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));star.setColor(ink);}
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);
            android.text.Layout lines=getLayout();if(lines==null||lines.getLineCount()==0)return;
            star.setTextSize(getTextSize());
            float x=getCompoundPaddingLeft()+lines.getLineLeft(0)-star.measureText("✨")-6*getResources().getDisplayMetrics().density;
            canvas.drawText("✨",x,getExtendedPaddingTop()+lines.getLineBaseline(0),star);
        }
    }

    private static final class QuestTitle extends TextView {
        final Paint symbol=new Paint(Paint.ANTI_ALIAS_FLAG);
        QuestTitle(Activity activity,int ink){super(activity);setText("A Quest for the\nKind-Hearted");setTextSize(24);setTextColor(ink);setContentDescription(getText());symbol.setColor(ink);symbol.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));}
        @Override protected void onDraw(Canvas canvas){android.text.Layout lines=getLayout();if(lines==null||lines.getLineCount()==0)return;
            float density=getResources().getDisplayMetrics().density;getPaint().setColor(getCurrentTextColor());
            for(int i=0;i<lines.getLineCount();i++){String line=getText().subSequence(lines.getLineStart(i),lines.getLineEnd(i)).toString().replace("\n","");canvas.drawText(line,getCompoundPaddingLeft()+lines.getLineLeft(i)+(i==0?2*density:0),getExtendedPaddingTop()+lines.getLineBaseline(i),getPaint());}
            symbol.setTextSize(getTextSize());float gap=6*density;
            float x=getCompoundPaddingLeft()+lines.getLineLeft(0)-symbol.measureText("📜")-gap;
            float baseline=getExtendedPaddingTop()+lines.getLineBaseline(0);canvas.drawText("📜",x,baseline,symbol);
        }
    }

    private static final class SinglePage extends LinearLayout {
        TextView title,status,unlock;View main;LinearLayout paragraphs;ScrollView bodyScroll;
        SinglePage(Context c){super(c);}
        int naturalHeightForWidth(int width){arrange(1f,15);super.onMeasure(MeasureSpec.makeMeasureSpec(width,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED));return getMeasuredHeight();}
        private void gap(View v,int pixels){LinearLayout.LayoutParams lp=(LinearLayout.LayoutParams)v.getLayoutParams();lp.topMargin=pixels;}
        private void arrange(float factor,int bodySize){float d=getResources().getDisplayMetrics().density;
            setPadding(dp(d,41),dp(d,37),dp(d,41),dp(d,33));
            gap(title,dp(d,12*factor));gap(status,dp(d,14*factor));gap(bodyScroll,dp(d,16*factor));gap(main,dp(d,20*factor));if(unlock!=null)gap(unlock,dp(d,14*factor));
            for(int i=0;i<paragraphs.getChildCount();i++){TextView body=(TextView)paragraphs.getChildAt(i);body.setTextSize(bodySize);body.setLineSpacing(0,1.3f);gap(body,i==0?0:dp(d,10*factor));}
            bodyScroll.getLayoutParams().height=LayoutParams.WRAP_CONTENT;
        }
        @Override protected void onMeasure(int widthSpec,int heightSpec){
            int available=MeasureSpec.getSize(heightSpec);if(available<=0)available=availableHeight((Activity)getContext());
            boolean fits=false;float[] factors={1f,.9f,.8f,.7f};
            for(float factor:factors){arrange(factor,15);super.onMeasure(widthSpec,MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED));if(getMeasuredHeight()<=available){fits=true;break;}}
            if(!fits){arrange(.7f,14);super.onMeasure(widthSpec,MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED));fits=getMeasuredHeight()<=available;}
            if(!fits){int chrome=getMeasuredHeight()-bodyScroll.getMeasuredHeight();bodyScroll.getLayoutParams().height=Math.max(0,available-chrome);}
            super.onMeasure(widthSpec,MeasureSpec.makeMeasureSpec(available,MeasureSpec.AT_MOST));
        }
    }
    private static int availableHeight(Activity activity){
        Rect visible=new Rect();activity.getWindow().getDecorView().getWindowVisibleDisplayFrame(visible);
        int height=visible.height()>0?visible.height():activity.getResources().getDisplayMetrics().heightPixels;
        return Math.max(1,height-dp(activity.getResources().getDisplayMetrics().density,48));
    }

    private static final class ScrollDialog extends Dialog {
        ScrollPanel panel;boolean rolling,closed;Runnable after;
        ScrollDialog(Context c){super(c);requestWindowFeature(Window.FEATURE_NO_TITLE);}
        void closeWith(Runnable action){if(rolling||closed)return;after=action;dismiss();}
        @Override public void dismiss(){
            if(closed||rolling)return;
            if(panel==null||!isShowing()||!motion(getContext())){finishClose();return;}
            rolling=true;panel.setEnabled(false);panel.reveal(false,new Runnable(){public void run(){finishClose();}});
        }
        @Override public void cancel(){dismiss();}
        private void finishClose(){if(closed)return;closed=true;super.dismiss();Runnable action=after;after=null;if(action!=null)action.run();}
    }

    private static final class ScrollPanel extends FrameLayout {
        final Paint fill=new Paint(Paint.ANTI_ALIAS_FLAG),detail=new Paint(Paint.ANTI_ALIAS_FLAG),grain=new Paint(Paint.ANTI_ALIAS_FLAG),rim=new Paint(Paint.ANTI_ALIAS_FLAG);
        final Path edge=new Path();final RectF bounds=new RectF();Shader rodShader;final float d;final boolean dark;boolean black;
        float amount;float[] specks;ValueAnimator roll;
        ScrollPanel(Context c,boolean dark,boolean black){super(c);AppSurfaces.watch(this);this.dark=dark;this.black=black;d=getResources().getDisplayMetrics().density;setWillNotDraw(false);setClipChildren(false);amount=motion(c)?0:1;grain.setStrokeWidth(.65f*d);grain.setColor(black?Color.TRANSPARENT:dark?0x188e7956:0x217b572d);rim.setStyle(Paint.Style.STROKE);rim.setStrokeWidth(2.4f*d);}
        @Override protected void onMeasure(int widthSpec,int heightSpec){int available=availableHeight((Activity)getContext());super.onMeasure(widthSpec,MeasureSpec.makeMeasureSpec(Math.min(available,MeasureSpec.getSize(heightSpec)>0?MeasureSpec.getSize(heightSpec):available),MeasureSpec.AT_MOST));}
        @Override protected void onSizeChanged(int w,int h,int ow,int oh){super.onSizeChanged(w,h,ow,oh);specks=new float[1600];java.util.Random random=new java.util.Random(416);for(int i=0;i<specks.length;i+=2){specks[i]=14*d+random.nextFloat()*Math.max(1,w-28*d);specks[i+1]=random.nextFloat()*h;}fill.setShader(new LinearGradient(0,0,w,h,dark?new int[]{black?SuperDarkPalette.BACKGROUND:0xff4c3c2a,black?SuperDarkPalette.BACKGROUND:0xff34291e,black?SuperDarkPalette.BACKGROUND:0xff463522}:new int[]{0xffdfc08d,0xffffedc9,0xffead0a0},new float[]{0,.5f,1},Shader.TileMode.CLAMP));rim.setShader(new LinearGradient(0,0,0,h,new int[]{0x30ffffff,0x12ffffff,0x0cffffff,0x16ffffff},new float[]{0,.35f,.75f,1},Shader.TileMode.CLAMP));
            edge.reset();float left=13*d,right=w-13*d,top=13*d,bottom=h-13*d;edge.moveTo(left,top);edge.lineTo(right,top);int index=0;
            for(float y=top+8*d;y<bottom;y+=13*d)edge.lineTo(right+(float)Math.sin(++index*1.9)*1.3f*d,y);
            edge.lineTo(right,bottom);edge.lineTo(left,bottom);
            for(float y=bottom-8*d;y>top;y-=13*d)edge.lineTo(left+(float)Math.sin(++index*1.6)*1.3f*d,y);edge.close();
            rodShader=new LinearGradient(0,-7*d,0,7*d,dark?new int[]{black?0xff161616:0xff5b4129,black?0xff1a1a1a:0xffb49262,black?0xff161616:0xff49321f}:new int[]{0xff957044,0xffffe5ae,0xffaf824b},new float[]{0,.43f,1},Shader.TileMode.CLAMP);
        }

        void reveal(boolean opening,final Runnable end){
            if(roll!=null){roll.removeAllListeners();roll.cancel();}
            if(!motion(getContext())){amount=opening?1:0;invalidate();if(end!=null)end.run();return;}
            roll=ValueAnimator.ofFloat(amount,opening?1:0);roll.setDuration(450);roll.setInterpolator(new DecelerateInterpolator(1.6f));roll.addUpdateListener(new ValueAnimator.AnimatorUpdateListener(){public void onAnimationUpdate(ValueAnimator value){amount=(Float)value.getAnimatedValue();invalidate();}});roll.addListener(new android.animation.AnimatorListenerAdapter(){@Override public void onAnimationEnd(android.animation.Animator a){roll=null;if(end!=null)end.run();}});roll.start();
        }
        private float top(){return getHeight()/2f-(getHeight()/2f-13*d)*amount;}
        private float bottom(){return getHeight()/2f+(getHeight()/2f-13*d)*amount;}
        @Override protected void onDraw(Canvas canvas){
            boolean now=AppSurfaces.active(getContext());if(now!=black){black=now;grain.setColor(black?Color.TRANSPARENT:dark?0x188e7956:0x217b572d);onSizeChanged(getWidth(),getHeight(),getWidth(),getHeight());}
            super.onDraw(canvas);canvas.save();canvas.clipRect(0,top(),getWidth(),bottom());
            canvas.drawPath(edge,fill);canvas.save();canvas.clipPath(edge);if(specks!=null)canvas.drawPoints(specks,grain);canvas.restore();
            detail.setShader(null);detail.setStyle(Paint.Style.STROKE);detail.setStrokeWidth(5*d);detail.setColor(dark?0x50503922:0x40784b21);canvas.drawPath(edge,detail);detail.setStrokeWidth(d);detail.setColor(dark?0xff796044:0xffbc935e);canvas.drawPath(edge,detail);detail.setStyle(Paint.Style.FILL);
            if(black){rim.setStrokeWidth(3.2f*d);rim.setAlpha(45);canvas.drawPath(edge,rim);rim.setStrokeWidth(1.8f*d);rim.setAlpha(85);canvas.drawPath(edge,rim);rim.setStrokeWidth(.8f*d);rim.setAlpha(150);canvas.drawPath(edge,rim);}canvas.restore();
        }

        @Override protected void dispatchDraw(Canvas canvas){canvas.save();canvas.clipRect(13*d,top()+5*d,getWidth()-13*d,bottom()-5*d);super.dispatchDraw(canvas);canvas.restore();rod(canvas,top());rod(canvas,bottom());}
        private void rod(Canvas canvas,float y){
            canvas.save();canvas.translate(0,y);float radius=7*d;detail.setStyle(Paint.Style.FILL);detail.setShader(rodShader);bounds.set(9*d,-radius,getWidth()-9*d,radius);canvas.drawRoundRect(bounds,radius,radius,detail);canvas.drawCircle(10*d,0,8*d,detail);canvas.drawCircle(getWidth()-10*d,0,8*d,detail);detail.setShader(null);detail.setStyle(Paint.Style.STROKE);detail.setStrokeWidth(d);detail.setColor(dark?0xff342316:0xff997142);canvas.drawCircle(10*d,0,4*d,detail);canvas.drawCircle(getWidth()-10*d,0,4*d,detail);detail.setStyle(Paint.Style.FILL);canvas.restore();
        }

        @Override protected void onDetachedFromWindow(){if(roll!=null){roll.removeAllListeners();roll.cancel();roll=null;}super.onDetachedFromWindow();}
    }

    private static final class WaxSeal extends View {
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);final Path wax=new Path(),star=new Path();final boolean gold;float burst=1;ValueAnimator sparkle;
        WaxSeal(Context c,boolean gold){super(c);this.gold=gold;setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            for(int i=0;i<64;i++){double angle=i*Math.PI*2/64;float r=27+(float)Math.sin(i*Math.PI/4)*1.3f;float x=44+(float)Math.cos(angle)*r,y=44+(float)Math.sin(angle)*r;if(i==0)wax.moveTo(x,y);else wax.lineTo(x,y);}wax.close();
            for(int i=0;i<10;i++){double angle=-Math.PI/2+i*Math.PI/5;float r=i%2==0?12:5.3f,x=44+(float)Math.cos(angle)*r,y=44+(float)Math.sin(angle)*r;if(i==0)star.moveTo(x,y);else star.lineTo(x,y);}star.close();
        }
        void celebrate(){if(!motion(getContext()))return;burst=0;sparkle=ValueAnimator.ofFloat(0,1);sparkle.setDuration(1000);sparkle.setInterpolator(new DecelerateInterpolator());sparkle.addUpdateListener(new ValueAnimator.AnimatorUpdateListener(){public void onAnimationUpdate(ValueAnimator a){burst=(Float)a.getAnimatedValue();invalidate();}});sparkle.start();}
        @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);float scale=Math.min(getWidth(),getHeight())/88f;canvas.save();canvas.scale(scale,scale);
            if(gold){paint.setShader(new RadialGradient(44,44,43,new int[]{0x66f3d36c,0x00f3d36c},null,Shader.TileMode.CLAMP));canvas.drawCircle(44,44,43,paint);paint.setShader(null);}
            paint.setColor(0x40341a16);canvas.save();canvas.translate(0,2);canvas.drawPath(wax,paint);canvas.restore();paint.setShader(new LinearGradient(20,17,66,72,new int[]{0xffa73638,0xff731c25,0xff51151c},null,Shader.TileMode.CLAMP));canvas.drawPath(wax,paint);paint.setShader(null);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.4f);paint.setColor(0xffbc5553);canvas.drawCircle(44,43,22,paint);paint.setColor(0xff4f121a);canvas.drawCircle(44,45,20.5f,paint);paint.setStyle(Paint.Style.FILL);paint.setColor(0xff4c1119);canvas.save();canvas.translate(0,1);canvas.drawPath(star,paint);canvas.restore();paint.setColor(gold?0xffefcb79:0xffd17b6d);canvas.drawPath(star,paint);
            if(gold&&burst<1){paint.setColor(0xffffde80);paint.setAlpha(Math.round((1-burst)*230));for(int i=0;i<8;i++){double a=i*Math.PI/4;float radius=26+burst*15,x=44+(float)Math.cos(a)*radius,y=44+(float)Math.sin(a)*radius,r=1.5f+(1-burst)*1.5f;canvas.drawRect(x-r,y-.6f,x+r,y+.6f,paint);canvas.drawRect(x-.6f,y-r,x+.6f,y+r,paint);}paint.setAlpha(255);}canvas.restore();
        }
        @Override protected void onDetachedFromWindow(){if(sparkle!=null)sparkle.cancel();super.onDetachedFromWindow();}
    }
}
