package io.github.pixelsuite;

import android.app.Activity;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.os.*;
import android.text.*;
import android.text.style.*;
import android.view.*;
import android.view.inputmethod.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;

final class AppSearch extends FrameLayout implements Choreographer.FrameCallback {
    interface Host {
        int accent();int onAccent();int trackOff();int primary();int secondary();int background();
        boolean inline(AppSearchIndex.Entry e);boolean locked(AppSearchIndex.Entry e);boolean updating();
        boolean checked(String key);void toggle(AppSearchIndex.Entry e,boolean on,Switch control);
        void navigate(AppSearchIndex.Entry e);void searchVisible(boolean open);void reveal(View target);
    }
    private final Activity activity;private final Host host;private final View page;
    private final Bar bar;private final EditText input;private final TextView clear;
    private final Viewport results;private final LinearLayout rows;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private AppSearchIndex index;private List<AppSearchIndex.Entry> matches=Collections.emptyList();
    private final Map<String,Switch> switches=new HashMap<String,Switch>();
    private final Map<String,View> liveActions=new HashMap<String,View>();
    private final List<AppSearchIndex.Entry> liveEntries=new ArrayList<AppSearchIndex.Entry>();
    private final Paint paint=new Paint(3);private final RectF rect=new RectF();
    private float fraction,velocity,target;private long frame;private boolean ticking,opened,disposed;
    private int nav,status,ime;private float downX,downY;private int resultsHeight;private boolean barGesture,resultsGesture,keyboardDismissRequested;
    private Runnable afterClose;
    private AppFrost panelFrost,barFrost;
    private float panel, panelVelocity, panelTarget, extent, extentVelocity, extentTarget, keyboard, keyboardVelocity, keyboardTarget;
    private boolean imeAnimating,keyboardRequested,windowHeld;private int previousSoftMode,previousSystemUi;private boolean previousFits;
    private final Rect panelBounds=new Rect();private boolean hasQuery;
    private final Map<String,View> stableRows=new LinkedHashMap<String,View>();
    private final Map<View,Float> oldTops=new HashMap<View,Float>();
    private void holdWindow(){if(windowHeld)return;windowHeld=true;Window w=activity.getWindow();previousSoftMode=w.getAttributes().softInputMode;previousSystemUi=w.getDecorView().getSystemUiVisibility();previousFits=(previousSystemUi&0x700)!=0x700;w.setSoftInputMode(0x30);if(Build.VERSION.SDK_INT>=30)try{Window.class.getMethod("setDecorFitsSystemWindows",boolean.class).invoke(w,false);}catch(Throwable ignored){}else w.getDecorView().setSystemUiVisibility(previousSystemUi|0x700);requestApplyInsets();}
    private void restoreWindow(){if(!windowHeld)return;windowHeld=false;activity.getWindow().setSoftInputMode(previousSoftMode);if(Build.VERSION.SDK_INT>=30)try{Window.class.getMethod("setDecorFitsSystemWindows",boolean.class).invoke(activity.getWindow(),previousFits);}catch(Throwable ignored){}activity.getWindow().getDecorView().setSystemUiVisibility(previousSystemUi);}
    private boolean imeFirst;private float imeOffset,imeStartFraction;
    boolean imeAnimationRunning(){return imeAnimating;}
    void imeProgress(int bottom,int navigation,float progress){float raw=Math.max(0,bottom-navigation);if(imeFirst){imeFirst=false;imeStartFraction=progress;imeOffset=keyboard-raw;}float correction=imeOffset*Math.max(0,(1-progress)/Math.max(.001f,1-imeStartFraction));ime(Math.round(navigation+raw+correction),navigation);}
    void imeStarting(){imeAnimating=true;imeFirst=true;}
    private void tick(){if(!ticking&&!disposed){ticking=true;frame=0;Choreographer.getInstance().postFrameCallback(this);}}
    private float stepVelocity(float value,float v,float goal,float k,float damping,float dt){return v+(-k*(value-goal)-2*damping*(float)Math.sqrt(k)*v)*dt;}
    private boolean settled(float value,float v,float goal){return Math.abs(value-goal)<.25f&&Math.abs(v)<1;}

    AppSearch(Activity a,View content,Host h){
        super(a);activity=a;host=h;page=content;setWillNotDraw(false);setClipChildren(false);setFocusable(false);
        results=new Viewport(a);
        results.setClipToOutline(true);results.setClipToPadding(false);results.setOutlineProvider(new ViewOutlineProvider(){public void getOutline(View v,Outline o){if(!panelBounds.isEmpty())o.setRoundRect(panelBounds,dp(24));else o.setEmpty();}});results.setFillViewport(false);results.setElevation(dp(6));
        rows=new LinearLayout(a);rows.setOrientation(1);rows.setPadding(dp(8),dp(8),dp(8),dp(8));results.addView(rows);addView(results);
        bar=new Bar(a);bar.setElevation(dp(6));addView(bar);
        panelFrost=new AppFrost(results,page,dp(24),host.background());results.setBackground(panelFrost);
        barFrost=AppFrost.pill(bar,page,dp(28),host.background());barFrost.setCallback(bar);
        input=new EditText(a){@Override public boolean onKeyPreIme(int key,KeyEvent event){if(key==KeyEvent.KEYCODE_BACK){if(event.getAction()==KeyEvent.ACTION_UP)back();return true;}return super.onKeyPreIme(key,event);}};input.setSingleLine(true);input.setTextSize(16);input.setBackgroundColor(Color.TRANSPARENT);input.setHint(I18n.cs("Search"));
        input.setOnTouchListener((v,event)->{if(event.getActionMasked()==MotionEvent.ACTION_DOWN)keyboardDismissRequested=false;return false;});
        input.setPadding(0,0,0,0);
        if(Build.VERSION.SDK_INT>=29)try{for(String method:new String[]{"setTextSelectHandle","setTextSelectHandleLeft","setTextSelectHandleRight"}){android.graphics.drawable.ColorDrawable transparent=new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT);transparent.setBounds(0,0,1,1);TextView.class.getMethod(method,Drawable.class).invoke(input,transparent);}}catch(Throwable ignored){}
input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);input.setInputType(1|android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);bar.addView(input);
        clear=new TextView(a);clear.setText(I18n.cs("×"));clear.setTextSize(25);clear.setGravity(Gravity.CENTER);clear.setContentDescription(I18n.cs("Clear search"));clear.setOnClickListener(v->input.setText(""));bar.addView(clear);
        input.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){if(opened)update();}public void afterTextChanged(Editable e){}});
        input.setOnEditorActionListener((v,action,event)->{if(action==EditorInfo.IME_ACTION_SEARCH&&!matches.isEmpty()){activate(matches.get(0));return true;}return false;});
        setOnApplyWindowInsetsListener((v,in)->{status=in.getSystemWindowInsetTop();int b=in.getSystemWindowInsetBottom();if(Build.VERSION.SDK_INT<30){ime(b>dp(100)?b:0,b>dp(100)?nav:b);}else SearchIme.installInsets(this,in);return in;});
        if(Build.VERSION.SDK_INT>=30)SearchIme.install(this);
        addOnAttachStateChangeListener(new OnAttachStateChangeListener(){public void onViewAttachedToWindow(View v){requestApplyInsets();}public void onViewDetachedFromWindow(View v){Choreographer.getInstance().removeFrameCallback(AppSearch.this);}});
        input.setVisibility(INVISIBLE);clear.setVisibility(INVISIBLE);results.setVisibility(INVISIBLE);
    }
    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    boolean isOpen(){return opened||fraction>.001f;}
    String query(){return input.getText().toString();}
    void restore(String q){post(()->{open();input.setText(I18n.cs(q));input.setSelection(input.length());});}
    void ime(int bottom,int navigation){if(bottom<=navigation+dp(8))keyboardDismissRequested=false;ime=bottom;nav=navigation;keyboardTarget=Math.max(0,bottom-navigation);if(imeAnimating||!motion()){keyboard=keyboardTarget;keyboardVelocity=0;render();}else tick();}
    void imeFinished(){imeAnimating=false;WindowInsets in=getRootWindowInsets();if(in!=null)SearchIme.installInsets(this,in);tick();}
    private boolean motion(){return android.provider.Settings.Global.getFloat(activity.getContentResolver(),"animator_duration_scale",1f)>0;}
    void open(){
        if(disposed)return;boolean first=!isOpen();holdWindow();keyboardRequested=false;keyboardDismissRequested=false;opened=true;liveActions.clear();liveEntries.clear();if(page.getParent() instanceof View)collectActions((View)page.getParent());host.searchVisible(true);input.setVisibility(VISIBLE);input.requestFocus();
        bar.setContentDescription(null);performHapticFeedback(6);spring(1);
        if(first&&input.length()>0)update();
        if(index==null){final Context c=activity.getApplicationContext();io.execute(()->{try{final AppSearchIndex found=AppSearchIndex.load(c);main.post(()->{if(!disposed){index=found;if(opened)update();}});}catch(Exception e){main.post(()->{if(opened){rows.removeAllViews();rows.addView(text("Search unavailable",15,host.secondary()));}});}});}

    }
    boolean back(){
        if(!isOpen())return false;
        if(ime>nav+dp(32)&&!keyboardDismissRequested){
            keyboardDismissRequested=true;
            ((InputMethodManager)activity.getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(input.getWindowToken(),0);
        }else close();
        return true;
    }
    void close(){
        if(!isOpen())return;opened=false;panelTarget=0;input.clearFocus();
        ((InputMethodManager)activity.getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(input.getWindowToken(),0);spring(0);
    }
    void close(Runnable next){afterClose=next;close();}
    private void spring(float to){target=to;if(to==0)panelTarget=0;
        if(!motion()){fraction=to;panel=to;target=to;velocity=panelVelocity=0;render();if(to==0)closed();else {keyboardRequested=true;((InputMethodManager)activity.getSystemService(Context.INPUT_METHOD_SERVICE)).showSoftInput(input,InputMethodManager.SHOW_IMPLICIT);}return;}tick();}
    public void doFrame(long t){
        if(disposed)return;float dt=frame==0?1f/120:Math.min(.032f,(t-frame)/1e9f);frame=t;
        if(opened&&fraction>.9f){panelTarget=1;if(!keyboardRequested){keyboardRequested=true;((InputMethodManager)activity.getSystemService(Context.INPUT_METHOD_SERVICE)).showSoftInput(input,InputMethodManager.SHOW_IMPLICIT);}}
        for(int i=0;i<6;i++){float h=dt/6;velocity=stepVelocity(fraction,velocity,target,600,1,h);fraction+=velocity*h;
            panelVelocity=stepVelocity(panel,panelVelocity,panelTarget,700,1,h);panel+=panelVelocity*h;
            extentVelocity=stepVelocity(extent,extentVelocity,extentTarget,500,.9f,h);extent+=extentVelocity*h;
            if(!imeAnimating){keyboardVelocity=stepVelocity(keyboard,keyboardVelocity,keyboardTarget,600,1,h);keyboard+=keyboardVelocity*h;}}
        boolean rest=Math.abs(fraction-target)<.001f&&Math.abs(velocity)<.01f&&Math.abs(panel-panelTarget)<.001f&&Math.abs(panelVelocity)<.01f&&settled(extent,extentVelocity,extentTarget)&&settled(keyboard,keyboardVelocity,keyboardTarget);
        if(rest){fraction=target;panel=panelTarget;extent=extentTarget;keyboard=keyboardTarget;velocity=panelVelocity=extentVelocity=keyboardVelocity=0;}render();
        if(rest&&!imeAnimating){ticking=false;if(!opened&&target==0)closed();}else Choreographer.getInstance().postFrameCallback(this);
    }
    private float f(){return Math.max(0,Math.min(1,fraction));}
    private void render(){
        float f=f();input.setAlpha(Math.max(0,(f-.38f)/.62f));input.setTranslationX(dp(6)*(1-f));clear.setAlpha(f);clear.setVisibility(opened&&input.length()>0?VISIBLE:INVISIBLE);
        position();bar.invalidate();invalidate();
    }
    private void closed(){
        restoreWindow();keyboardRequested=false;hasQuery=false;stableRows.clear();input.setText("");input.setVisibility(INVISIBLE);clear.setVisibility(INVISIBLE);results.setVisibility(INVISIBLE);rows.removeAllViews();matches=Collections.emptyList();switches.clear();liveActions.clear();liveEntries.clear();
        bar.setContentDescription(I18n.cs("Search, button"));host.searchVisible(false);bar.sendAccessibilityEvent(32);
        Runnable next=afterClose;afterClose=null;if(next!=null)next.run();
    }
    void palette(){if(Build.VERSION.SDK_INT>=29)try{android.graphics.drawable.GradientDrawable cursor=new android.graphics.drawable.GradientDrawable();cursor.setColor(host.accent());cursor.setSize(dp(2),dp(24));TextView.class.getMethod("setTextCursorDrawable",Drawable.class).invoke(input,cursor);}catch(Throwable ignored){}panelFrost.pageColor(host.background());barFrost.pageColor(host.background());input.setTextColor(host.primary());input.setHintTextColor(host.secondary());clear.setTextColor(host.primary());if(opened)update();bar.invalidate();results.invalidate();}
    void stateChanged(String key){Switch sw=switches.get(key);if(sw!=null){sw.setOnCheckedChangeListener(null);sw.setChecked(host.checked(key));sw.setEnabled(true);AppSearchIndex.Entry found=null;for(AppSearchIndex.Entry e:matches)if(key.equals(e.key)){found=e;break;}if(found!=null)bindSwitch(sw,found);}}
    private void bindSwitch(Switch sw,AppSearchIndex.Entry e){sw.setOnCheckedChangeListener((v,on)->{if(host.updating())return;sw.performHapticFeedback(6);sw.setEnabled(false);host.toggle(e,on,sw);});}
    private TextView text(String s,int size,int color){TextView t=new TextView(activity);t.setText(I18n.cs(s));t.setTextSize(size);t.setTextColor(color);return t;}
    private void update(){
        for(int i=0;i<rows.getChildCount();i++){View v=rows.getChildAt(i);oldTops.put(v,v.getY());}switches.clear();String q=query().trim();hasQuery=!q.isEmpty();if(!hasQuery){matches=Collections.emptyList();rows.removeAllViews();stableRows.clear();oldTops.clear();results.getOverlay().clear();extent=extentTarget=extentVelocity=0;position();tick();return;}
        for(int i=rows.getChildCount()-1;i>=0;i--)if(!stableRows.containsValue(rows.getChildAt(i)))rows.removeViewAt(i);
        results.setVisibility(VISIBLE);matches=index==null?Collections.emptyList():index.including(liveEntries).find(q);
        if(index==null||matches.isEmpty()){TextView empty=text(index==null?"Searching…":"No results for \""+q+"\"",15,host.secondary());empty.setGravity(Gravity.CENTER);empty.setPadding(dp(16),dp(20),dp(16),dp(20));rows.addView(empty);}
        final Set<String> wanted=new HashSet<String>();for(AppSearchIndex.Entry e:matches)wanted.add(e.id);
        for(String id:new ArrayList<String>(stableRows.keySet()))if(!wanted.contains(id)){View gone=stableRows.remove(id);rows.removeView(gone);if(motion()){results.getOverlay().add(gone);gone.animate().alpha(0).setDuration(90).withEndAction(()->results.getOverlay().remove(gone)).start();}}
        if(matches.isEmpty()){stableRows.clear();while(rows.getChildCount()>1)rows.removeViewAt(0);}
        int n=0;for(final AppSearchIndex.Entry e:matches){
            View retained=stableRows.get(e.id);if(retained!=null){int old=rows.indexOfChild(retained);if(old!=n){rows.removeView(retained);rows.addView(retained,n);}LinearLayout labelBox=(LinearLayout)((LinearLayout)retained).getChildAt(((LinearLayout)retained).getChildAt(0) instanceof ImageView?1:0);((TextView)labelBox.getChildAt(0)).setText(I18n.cs(highlight(e.title,q)));if("TOGGLE".equals(e.kind)){Switch sw=findSwitch(retained);if(sw!=null){switches.put(e.key,sw);sw.setOnCheckedChangeListener(null);sw.setChecked(host.checked(e.key));if(!host.locked(e)&&host.inline(e))bindSwitch(sw,e);}}n++;continue;}

            LinearLayout row=new LinearLayout(activity);row.setGravity(Gravity.CENTER_VERTICAL);row.setMinimumHeight(dp(64));row.setPadding(dp(12),dp(10),dp(12),dp(10));
            int id=getResources().getIdentifier(e.icon,"drawable",activity.getPackageName());boolean hasIcon=id!=0&&!"ic_ps_info".equals(e.icon);
            if(hasIcon){ImageView icon=new ImageView(activity);icon.setImageResource(id);icon.setColorFilter(host.accent());row.addView(icon,new LinearLayout.LayoutParams(dp(24),dp(24)));}
            LinearLayout labels=new LinearLayout(activity);labels.setOrientation(1);TextView title=text(e.title,16,host.primary());title.setMaxLines(2);title.setEllipsize(TextUtils.TruncateAt.END);title.setText(I18n.cs(highlight(e.title,q)));labels.addView(title);
            TextView crumb=text(e.breadcrumb,13,host.secondary());crumb.setMaxLines(2);labels.addView(crumb);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.leftMargin=hasIcon?dp(12):0;lp.rightMargin=dp(8);row.addView(labels,lp);
            if("TOGGLE".equals(e.kind)){
                boolean lock=host.locked(e);Switch sw=new Switch(activity);sw.setChecked(host.checked(e.key));
                SwitchStyle.apply(sw,host.accent(),host.trackOff(),host.onAccent(),host.secondary());sw.setEnabled(!lock&&host.inline(e));sw.setAlpha(lock?.4f:1);sw.setContentDescription(I18n.cs(e.title+", "+e.breadcrumb+", switch, "+(sw.isChecked()?"on":"off")));
                row.addView(sw);if(lock)row.addView(text("🔒",13,host.secondary()));
                if(!lock&&host.inline(e)){switches.put(e.key,sw);bindSwitch(sw,e);}
                row.setOnClickListener(v->activate(e));
            }else {if(!"TEXT".equals(e.kind))row.addView(text("›",26,host.secondary()));row.setOnClickListener(v->activate(e));}
            row.setFocusable(true);rows.addView(row,n,new LinearLayout.LayoutParams(-1,-2));stableRows.put(e.id,row);
            if(motion()&&n<8){row.setAlpha(0);row.animate().alpha(1).setStartDelay(0).setDuration(120).start();}n++;
        }
        clear.setVisibility(input.length()>0?VISIBLE:INVISIBLE);rows.requestLayout();results.requestLayout();
        rows.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener(){public boolean onPreDraw(){rows.getViewTreeObserver().removeOnPreDrawListener(this);if(!hasQuery){extent=extentTarget=extentVelocity=0;return true;}extentTarget=rows.getMeasuredHeight();if(!motion())extent=extentTarget;for(Map.Entry<View,Float> item:oldTops.entrySet()){View v=item.getKey();if(v.getParent()==rows){float delta=item.getValue()-v.getTop();v.animate().cancel();v.setAlpha(1f);v.setTranslationY(delta);if(motion())v.animate().translationY(0).setDuration(180).start();else v.setTranslationY(0);}}oldTops.clear();tick();return true;}});
    }
    private Switch findSwitch(View v){if(v instanceof Switch)return (Switch)v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){Switch found=findSwitch(g.getChildAt(i));if(found!=null)return found;}}return null;}
    private CharSequence highlight(String title,String query){
        SpannableString out=new SpannableString(title);String norm=AppSearchIndex.normal(title);
        for(String q:AppSearchIndex.normal(query).split(" +")){int at=norm.indexOf(q);if(at>=0&&at+q.length()<=title.length()){out.setSpan(new ForegroundColorSpan(host.accent()),at,at+q.length(),33);out.setSpan(new StyleSpan(Typeface.BOLD),at,at+q.length(),33);}}return out;
    }
    private void activate(AppSearchIndex.Entry e){View action=liveActions.get(e.id);if(action!=null)close(()->{if(action.isAttachedToWindow())host.reveal(action);});else close(()->host.navigate(e));}
    private String viewTitle(View view){
        if(view instanceof TextView&&!(view instanceof Switch)){String s=((TextView)view).getText().toString().trim();if(!s.isEmpty()&&!"›".equals(s))return s;}
        if(view instanceof ViewGroup){ViewGroup g=(ViewGroup)view;for(int i=0;i<g.getChildCount();i++){String found=viewTitle(g.getChildAt(i));if(found!=null)return found;}}
        CharSequence description=view.getContentDescription();return description==null?null:description.toString();
    }
    private boolean hasSwitch(View v){if(v instanceof Switch)return true;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++)if(hasSwitch(g.getChildAt(i)))return true;}return false;}
    private void collectActions(View view){
        if(view==this||!view.isShown())return;
        if(view instanceof TextView&&"!".equals(((TextView)view).getText().toString().trim()))return;
        if(view.isClickable()&&view.isEnabled()&&!hasSwitch(view)){
            String title=viewTitle(view);if(title!=null&&!title.trim().isEmpty())try{
                String id="live:"+System.identityHashCode(view);String pageId=activity.getIntent().getStringExtra("page");
                org.json.JSONObject o=new org.json.JSONObject();o.put("id",id);o.put("title",title);o.put("page",pageId==null?"":pageId);o.put("kind","ACTION");o.put("breadcrumb",activity.getTitle());o.put("icon","ic_ps_info");
                liveEntries.add(new AppSearchIndex.Entry(o));liveActions.put(id,view);return;
            }catch(org.json.JSONException ignored){}
        }
        if(view instanceof ViewGroup){ViewGroup g=(ViewGroup)view;for(int i=0;i<g.getChildCount();i++)collectActions(g.getChildAt(i));}
    }
    protected void onMeasure(int w,int h){
        int width=MeasureSpec.getSize(w),height=MeasureSpec.getSize(h);setMeasuredDimension(width,height);
        bar.measure(MeasureSpec.makeMeasureSpec(Math.max(1,width-dp(24)),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(dp(56)+Math.max(0,Math.round((getResources().getConfiguration().fontScale-1)*dp(20))),MeasureSpec.EXACTLY));
        int available=Math.max(1,height-nav-bar.getMeasuredHeight()-dp(44)-status);
        results.measure(MeasureSpec.makeMeasureSpec(Math.max(1,width-dp(24)),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(available,MeasureSpec.EXACTLY));resultsHeight=available;
    }
    protected void onLayout(boolean changed,int l,int t,int r,int b){
        bar.layout(dp(12),0,getWidth()-dp(12),bar.getMeasuredHeight());results.layout(dp(12),status+dp(12),getWidth()-dp(12),status+dp(12)+resultsHeight);extentTarget=hasQuery?rows.getMeasuredHeight():0;render();
    }
    private void position(){if(bar==null)return;
        float y=getHeight()-nav-dp(12)-keyboard-bar.getHeight();bar.setTranslationY(y);
        if(!hasQuery){panelBounds.setEmpty();panelFrost.setBounds(panelBounds);results.setClipBounds(panelBounds);results.invalidateOutline();results.setVisibility(INVISIBLE);results.viewport(0,0);return;}
        float available=Math.max(0,y-dp(8)-results.getTop());float visible=Math.min(available,Math.max(0,extent));float bottom=visible;float top=bottom*(1-Math.max(0,Math.min(1,panel)));
        panelBounds.set(0,Math.round(top),results.getWidth(),Math.round(bottom));results.setClipBounds(panelBounds);panelFrost.setBounds(panelBounds);results.invalidateOutline();results.viewport(top,bottom);results.setAlpha(Math.min(1,Math.max(0,panel)*2.25f));results.invalidate();
    }
    protected void onDraw(Canvas c){paint.setColor(Color.BLACK);paint.setAlpha(Math.round((AppSurfaces.active(activity)?184:FrostedSurface.dark(activity)?82:51)*f()));c.drawRect(0,0,getWidth(),getHeight(),paint);}
    public boolean dispatchTouchEvent(MotionEvent e){
        float y=e.getY(),x=e.getX();float bw=bar.widthNow(),left=(getWidth()-bw)/2;float top=bar.getTranslationY();boolean onBar=y>=top&&y<top+bar.getHeight()&&x>=left&&x<=left+bw;
        if(e.getActionMasked()==MotionEvent.ACTION_DOWN){downY=y;downX=x;barGesture=onBar;
            resultsGesture=opened&&results.getVisibility()==VISIBLE&&x>=results.getLeft()&&x<results.getRight()&&y>=results.getTop()+panelBounds.top&&y<results.getTop()+panelBounds.bottom;
        }
        if(resultsGesture){boolean handled=super.dispatchTouchEvent(e);if(e.getActionMasked()==MotionEvent.ACTION_UP||e.getActionMasked()==MotionEvent.ACTION_CANCEL)resultsGesture=false;return handled;}
        if(!opened&&isOpen()&&onBar&&e.getActionMasked()==MotionEvent.ACTION_DOWN){open();return true;}
        if(!isOpen()){
            if(!onBar&&!barGesture)return false;
            if(e.getActionMasked()==MotionEvent.ACTION_UP&&Math.abs(y-downY)<dp(12)){open();return true;}return true;
        }
        if(e.getActionMasked()==MotionEvent.ACTION_MOVE&&barGesture&&y-downY>dp(30)){close();return true;}
        boolean onResults=results.getVisibility()==VISIBLE&&y>=results.getTop()+panelBounds.top&&y<results.getTop()+panelBounds.bottom;
        if(!onBar&&!onResults){
            if(e.getActionMasked()==MotionEvent.ACTION_MOVE&&Math.abs(y-downY)>dp(8)&&input.length()==0)close();
            if(e.getActionMasked()==MotionEvent.ACTION_UP)close();return true;
        }return super.dispatchTouchEvent(e);
    }
    void dispose(){restoreWindow();disposed=true;Choreographer.getInstance().removeFrameCallback(this);main.removeCallbacksAndMessages(null);io.shutdownNow();panelFrost.release();barFrost.release();}
    private final class Viewport extends FrameLayout {
        final OverScroller fling;final int slop;float startY,lastY,offset,visibleTop,visibleBottom;VelocityTracker tracker;boolean dragging;
        Viewport(Context c){super(c);fling=new OverScroller(c);slop=ViewConfiguration.get(c).getScaledTouchSlop();setClipChildren(false);}
        void setFillViewport(boolean ignored){}
        void viewport(float top,float bottom){visibleTop=top;visibleBottom=bottom;offset=Math.max(0,Math.min(offset,maxScroll()));if(rows!=null)rows.setTranslationY(-offset);}
        float maxScroll(){return Math.max(0,rows.getHeight()-Math.max(0,visibleBottom-visibleTop));}
        protected void onMeasure(int w,int h){setMeasuredDimension(MeasureSpec.getSize(w),MeasureSpec.getSize(h));rows.measure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(w),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED));}
        protected void onLayout(boolean c,int l,int t,int r,int b){rows.layout(0,0,getWidth(),rows.getMeasuredHeight());viewport(visibleTop,visibleBottom);}
        public boolean onInterceptTouchEvent(MotionEvent e){int a=e.getActionMasked();if(a==0){startY=lastY=e.getY();dragging=false;fling.abortAnimation();}if(a==2&&Math.abs(e.getY()-startY)>slop){dragging=true;lastY=e.getY();return true;}return dragging;}
        public boolean onTouchEvent(MotionEvent e){if(tracker==null)tracker=VelocityTracker.obtain();tracker.addMovement(e);int a=e.getActionMasked();if(a==0){startY=lastY=e.getY();return true;}if(a==2){offset=Math.max(0,Math.min(maxScroll(),offset+lastY-e.getY()));lastY=e.getY();viewport(visibleTop,visibleBottom);invalidate();return true;}if(a==1||a==3){if(a==1){tracker.computeCurrentVelocity(1000);fling.fling(0,Math.round(offset),0,Math.round(-tracker.getYVelocity()),0,0,0,Math.round(maxScroll()));postInvalidateOnAnimation();}tracker.recycle();tracker=null;dragging=false;return true;}return true;}
        public void computeScroll(){if(fling.computeScrollOffset()){offset=fling.getCurrY();viewport(visibleTop,visibleBottom);postInvalidateOnAnimation();}}
        public boolean performAccessibilityAction(int action,Bundle args){if(action==4096||action==8192){offset=Math.max(0,Math.min(maxScroll(),offset+(action==4096?1:-1)*(visibleBottom-visibleTop)));viewport(visibleTop,visibleBottom);invalidate();return true;}return super.performAccessibilityAction(action,args);}
    }
    private final class Bar extends FrameLayout {
        @Override protected boolean verifyDrawable(Drawable drawable){return drawable==barFrost||super.verifyDrawable(drawable);}
        final Paint p=new Paint(3),label=new Paint(3);final Path clip=new Path();final RectF bounds=new RectF();final Rect labelInk=new Rect();
        Bar(Context c){super(c);label.setTextSize(16*getResources().getDisplayMetrics().scaledDensity);label.setTypeface(I18n.medium());label.getTextBounds("Search",0,6,labelInk);setWillNotDraw(false);setContentDescription(I18n.cs("Search, button"));setFocusable(true);setOnClickListener(v->open());}
        float widthNow(){float closed=Math.min(dp(96)+label.measureText(I18n.t("Search")),getWidth()-dp(8));return closed+(getWidth()-closed)*f();}
        protected void onMeasure(int w,int h){super.onMeasure(w,h);int width=MeasureSpec.getSize(w),height=MeasureSpec.getSize(h);input.measure(MeasureSpec.makeMeasureSpec(Math.max(1,width-dp(104)),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(height,MeasureSpec.EXACTLY));clear.measure(MeasureSpec.makeMeasureSpec(dp(48),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(height,MeasureSpec.EXACTLY));}
        protected void onLayout(boolean ch,int l,int t,int r,int b){input.layout(dp(52),0,getWidth()-dp(52),getHeight());clear.layout(getWidth()-dp(52),0,getWidth()-dp(4),getHeight());}
        protected void onDraw(Canvas c){
            float inset=(getWidth()-widthNow())/2;bounds.set(inset,0,getWidth()-inset,getHeight());float radius=getHeight()/2f;clip.reset();clip.addRoundRect(bounds,radius,radius,Path.Direction.CW);
            barFrost.setBounds(Math.round(bounds.left),0,Math.round(bounds.right),getHeight());barFrost.radius(radius);barFrost.draw(c);
            float iconWidth=dp(20),gap=dp(10),groupLeft=(getWidth()-iconWidth-gap-labelInk.width())/2f;
            float labelX=groupLeft+iconWidth+gap-labelInk.left;
            float collapsedIcon=groupLeft+dp(8);float cx=collapsedIcon+(dp(30)-collapsedIcon)*f();p.setColor(host.secondary());p.setAlpha(255);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(2));c.drawCircle(cx,getHeight()/2f-dp(2),dp(7),p);c.drawLine(cx+dp(5),getHeight()/2f+dp(3),cx+dp(11),getHeight()/2f+dp(9),p);p.setStyle(Paint.Style.FILL);
            label.setColor(host.primary());label.setAlpha(Math.round(255*Math.max(0,1-f()*3)));c.drawText(I18n.t("Search"),labelX,getHeight()/2f-labelInk.exactCenterY(),label);
        }
    }
}
