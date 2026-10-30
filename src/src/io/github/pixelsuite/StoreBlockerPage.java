package io.github.pixelsuite;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.*;
import android.content.pm.*;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.*;
import android.text.*;
import android.view.*;
import android.view.animation.PathInterpolator;
import android.widget.*;
import java.text.Collator;
import java.util.*;
import java.util.concurrent.Executor;

final class StoreBlockerPage {
    private final Activity activity;
    private final Executor io;
    private final int text,secondary,accent,onAccent;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Set<String> core=new HashSet<String>(Arrays.asList(StoreBlocker.CORE));
    private final Set<String> selected=new TreeSet<String>();
    private final Set<String> installedPackages=new HashSet<String>();
    private Set<String> saved=new TreeSet<String>();
    private final Map<String,Long> checkedOrder=new HashMap<String,Long>();
    private long order;
    private final ArrayList<Entry> all=new ArrayList<Entry>(),shown=new ArrayList<Entry>();
    private final Collator collator=Collator.getInstance();
    private final android.util.LruCache<String,Drawable> icons=new android.util.LruCache<String,Drawable>(40);
    private final Switch unlockSwitch;
    private final TextView count;
    private EditText search;
    private TextView status;
    private ListView list;
    private LinearLayout folder,folderBody;private TextView folderTitle,folderSummary;private View folderArrow;private FolderArtwork folderArtwork;
    private boolean expanded,folderAttached;private android.animation.ValueAnimator foldAnimator;
    private final ArrayList<Entry> blockedShown=new ArrayList<Entry>();
    private ScopePanel.Hint versionHint;
    private AppsAdapter adapter;
    private RestartPill pill;
    private Dialog panel;
    private AlertDialog discard;
    private boolean loaded,loading,reload,saving,unlock,registered,disposed;
    private AppToast lockedToast;
    private Runnable initialLoad;
    void onInitialLoad(Runnable done){initialLoad=done;}
    private void ready(){if(initialLoad!=null){Runnable done=initialLoad;initialLoad=null;done.run();}}
    private final BroadcastReceiver packages=new BroadcastReceiver(){public void onReceive(Context c,Intent intent){
        if(Intent.ACTION_PACKAGE_REMOVED.equals(intent.getAction())&&!intent.getBooleanExtra(Intent.EXTRA_REPLACING,false)&&intent.getData()!=null){
            final String pkg=intent.getData().getSchemeSpecificPart();icons.remove(pkg);
            if(!core.contains(pkg)){
                selected.remove(pkg);saved.remove(pkg);checkedOrder.remove(pkg);
                io.execute(new Runnable(){public void run(){List<String> v=Root.run("settings get secure "+StoreBlocker.KEY);if(v!=null&&!v.isEmpty()&&!"null".equals(v.get(0))){Set<String> current=MiscOptions.parse(v.get(0));if(current.remove(pkg))Root.run("settings put secure "+StoreBlocker.KEY+" '"+MiscOptions.join(current)+"'");}}});
            }
        }
        load();
    }};
    private static final class Entry {
        final ApplicationInfo info;final String name,version;final long code;
        Entry(ApplicationInfo info,String name,String version,long code){this.info=info;this.name=name;this.version=version;this.code=code;}
    }
    StoreBlockerPage(Activity a,Executor io,int text,int secondary,int accent,int onAccent,Switch unlockSwitch,TextView count){
        activity=a;this.io=io;this.text=text;this.secondary=secondary;this.accent=accent;this.onAccent=onAccent;this.unlockSwitch=unlockSwitch;this.count=count;
    }
    private int dp(int v){return Math.round(v*activity.getResources().getDisplayMetrics().density);}
    private boolean live(){return !disposed&&!activity.isFinishing()&&!activity.isDestroyed();}
    void resume(){register();load();}
    void pause(){unregister();}
    private void register(){
        if(registered||disposed)return;
        IntentFilter f=new IntentFilter();f.addAction(Intent.ACTION_PACKAGE_ADDED);f.addAction(Intent.ACTION_PACKAGE_REMOVED);f.addAction(Intent.ACTION_PACKAGE_REPLACED);f.addDataScheme("package");
        try{
            if(Build.VERSION.SDK_INT>=33)Context.class.getMethod("registerReceiver",BroadcastReceiver.class,IntentFilter.class,int.class).invoke(activity,packages,f,4);
            else activity.registerReceiver(packages,f);
            registered=true;
        }catch(Exception failure){AppToast.makeText(activity,"Reopen this page to refresh installed apps",Toast.LENGTH_SHORT).show();}
    }
    private void unregister(){if(registered){try{activity.unregisterReceiver(packages);}catch(Exception ignored){}registered=false;}}
    void dispose(){disposed=true;unregister();if(lockedToast!=null)lockedToast.cancel();if(discard!=null)discard.dismiss();closePanel();}
    void load(){
        if(!live())return;if(loading||saving){reload=true;return;}loading=true;
        io.execute(new Runnable(){public void run(){
            try{
                List<String> values=Root.run("settings get secure "+StoreBlocker.KEY+"; settings get secure "+StoreBlocker.UNLOCK_KEY+"; settings get secure pixelsuite_store_scope_defaults_cleaned");
                if(values==null||values.size()<3)throw new IllegalStateException("Root unavailable");
                final boolean unlocked="1".equals(values.get(1));
                final boolean cleanOldDefaults=!"1".equals(values.get(2));
                final Set<String> original="null".equals(values.get(0))?new TreeSet<String>(core):MiscOptions.parse(values.get(0));
                if(!unlocked)original.addAll(core);
                final ArrayList<Entry> entries=new ArrayList<Entry>();PackageManager pm=activity.getPackageManager();
                Set<String> launchable=new HashSet<String>();final Set<String> installed=new HashSet<String>();
                for(ResolveInfo r:pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),0))if(r.activityInfo!=null)launchable.add(r.activityInfo.packageName);
                for(ApplicationInfo app:pm.getInstalledApplications(0)){
                    installed.add(app.packageName);
                    if(core.contains(app.packageName)||launchable.contains(app.packageName)){
                        String name;try{name=app.loadLabel(pm).toString();}catch(Exception unavailable){name=app.packageName;}PackageInfo pkg;try{pkg=pm.getPackageInfo(app.packageName,0);}catch(PackageManager.NameNotFoundException removedDuringLoad){continue;}long code=pkg.versionCode;
                        if(Build.VERSION.SDK_INT>=28)try{code=((Number)PackageInfo.class.getMethod("getLongVersionCode").invoke(pkg)).longValue();}catch(Exception ignored){}
                        entries.add(new Entry(app,name,pkg.versionName==null?"Unknown":pkg.versionName,code));
                    }
                }
                final Set<String> removed=new HashSet<String>();
                for(String pkg:original)if(!core.contains(pkg)&&!installed.contains(pkg))removed.add(pkg);
                if(cleanOldDefaults&&!unlocked)for(String pkg:new String[]{"com.android.systemui","com.google.android.documentsui","com.android.documentsui","com.android.settings","com.google.android.packageinstaller","com.android.packageinstaller","com.android.intentresolver","com.android.imsstack","com.android.phone"})if(original.contains(pkg))removed.add(pkg);
                if(!removed.isEmpty()){
                    original.removeAll(removed);
                    if(Root.run("settings put secure "+StoreBlocker.KEY+" '"+MiscOptions.join(original)+"'")==null)throw new IllegalStateException("Root unavailable");
                }
                if(cleanOldDefaults&&Root.run("settings put secure pixelsuite_store_scope_defaults_cleaned 1")==null)throw new IllegalStateException("Root unavailable");
                main.post(new Runnable(){public void run(){
                    if(!live())return;loading=false;
                    if(!loaded){loaded=true;unlock=unlocked;saved=new TreeSet<String>(original);selected.addAll(original);}
                    else if(panel==null&&!saving){unlock=unlocked;saved=new TreeSet<String>(original);selected.clear();selected.addAll(original);}
                    selected.removeAll(removed);saved.removeAll(removed);
                    for(String pkg:new HashSet<String>(selected))if(!core.contains(pkg)&&!installed.contains(pkg))selected.remove(pkg);
                    installedPackages.clear();installedPackages.addAll(installed);if(!unlock)selected.addAll(core);
                    all.clear();all.addAll(entries);icons.evictAll();refresh(false);ready();
                    if(reload){reload=false;load();}
                }});
            }catch(Exception failure){main.post(new Runnable(){public void run(){if(live()){loading=false;count.setText("Couldn't load apps");if(status!=null)status.setText("Check root access and reopen this page.");ready();}}});}
        }});
    }
    void unlockChanged(boolean value){if(!loaded||unlock==value)return;unlock=value;if(!unlock)selected.addAll(core);refresh(false);}
    void show(){
        if(panel!=null)return;
        expanded=false;versionHint=new ScopePanel.Hint(activity,accent);
        panel=new Dialog(activity){@Override public void cancel(){requestClose();}};
        panel.requestWindowFeature(Window.FEATURE_NO_TITLE);panel.setCanceledOnTouchOutside(true);
        final FrameLayout root=new FrameLayout(activity);
        final LinearLayout content=new LinearLayout(activity);content.setOrientation(1);content.setBackground(AppSurfaces.sheet(activity,28));content.setClipToOutline(true);content.setPadding(dp(16),0,dp(16),0);
        root.addView(content,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout header=new LinearLayout(activity);header.setOrientation(1);header.setGravity(Gravity.CENTER_HORIZONTAL);header.setPadding(0,dp(12),0,dp(8));
        View handle=new View(activity);handle.setBackground(AppSurfaces.field(activity,3));header.addView(handle,new LinearLayout.LayoutParams(dp(36),dp(5)));
        LinearLayout titleRow=new LinearLayout(activity);titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=label("Select apps",22,text);title.setPadding(dp(4),dp(12),0,dp(8));titleRow.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        TextView close=label("×",28,secondary);close.setGravity(Gravity.CENTER);AppCloseButton.style(close);close.setContentDescription("Close app selection");titleRow.addView(close,new LinearLayout.LayoutParams(dp(48),dp(48)));close.setOnClickListener(new View.OnClickListener(){public void onClick(View v){requestClose();}});header.addView(titleRow,new LinearLayout.LayoutParams(-1,-2));
        final float[] down=new float[1];header.setOnTouchListener(new View.OnTouchListener(){public boolean onTouch(View v,android.view.MotionEvent e){if(e.getActionMasked()==0){down[0]=e.getRawY();return true;}if(e.getActionMasked()==1){if(e.getRawY()-down[0]>dp(64))requestClose();return true;}return true;}});
        content.addView(header,new LinearLayout.LayoutParams(-1,-2));
        search=new EditText(activity);search.setSingleLine(true);search.setHint("Search apps");search.setContentDescription("Search installed apps");search.setTextColor(text);search.setHintTextColor(secondary);search.setBackground(new ThemeGlass(activity,24,ThemeGlass.FIELD));search.setLayerType(View.LAYER_TYPE_SOFTWARE,null);search.setPadding(dp(16),dp(10),dp(16),dp(10));content.addView(search,new LinearLayout.LayoutParams(-1,dp(48)));
        status=label(loaded?"":"Loading apps…",12,secondary);status.setPadding(dp(4),dp(8),dp(4),dp(8));content.addView(status);
        list=new ListView(activity);ToastMotion.watchScroll(list);list.setDivider(null);list.setBackgroundColor(Color.TRANSPARENT);createFolder();list.addHeaderView(folder,null,false);folderAttached=true;adapter=new AppsAdapter();list.setAdapter(adapter);content.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        TextView note=label("Tick Play Store in LSPosed for Pixel Suite.",12,secondary);note.setGravity(Gravity.CENTER);note.setPadding(0,dp(8),0,dp(8));content.addView(note,new LinearLayout.LayoutParams(-1,-2));
        final View footer=new View(activity);content.addView(footer,new LinearLayout.LayoutParams(-1,dp(80)));
        pill=new RestartPill(activity,root,accent,onAccent);pill.setApplyAction(new Runnable(){public void run(){apply();}});
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int c,int after){}public void afterTextChanged(Editable e){}public void onTextChanged(CharSequence s,int start,int before,int c){refresh(false);}});
        panel.setContentView(root);panel.show();
        Window window=panel.getWindow();if(window!=null){window.setBackgroundDrawableResource(android.R.color.transparent);window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);AppSurfaces.dim(window,.45f);window.setGravity(Gravity.BOTTOM);window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE|WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);AppPanelBounds.fit(activity,window,(int)(activity.getResources().getDisplayMetrics().heightPixels*.85f));window.setNavigationBarColor(Color.TRANSPARENT);}
        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener(){public WindowInsets onApplyWindowInsets(View v,WindowInsets in){content.setPadding(dp(16)+in.getSystemWindowInsetLeft(),0,dp(16)+in.getSystemWindowInsetRight(),in.getSystemWindowInsetBottom());return in;}});
        root.requestApplyInsets();refresh(false);load();
        content.setTranslationY(dp(36));content.setAlpha(0);content.animate().translationY(0).alpha(1).setDuration(260).setInterpolator(new PathInterpolator(.2f,0,0,1)).start();
    }
    private TextView label(String value,int size,int color){TextView t=new TextView(activity);t.setText(value);t.setTextSize(size);t.setTextColor(color);return t;}
    private void requestClose(){
        if(saving){AppToast.makeText(activity,"Wait for your changes to finish applying",Toast.LENGTH_SHORT).show();return;}
        if(!selected.equals(saved)){
            if(discard!=null&&discard.isShowing())return;
            discard=new AlertDialog.Builder(activity).setTitle("Discard changes?").setMessage("Your app selections haven't been applied.").setNegativeButton("Keep editing",null).setPositiveButton("Discard",new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int which){selected.clear();selected.addAll(saved);if(!unlock)selected.addAll(core);closePanel();}}).create();
            discard.show();Window w=discard.getWindow();if(w!=null)w.setBackgroundDrawable(AppSurfaces.dialog(activity,28));
            for(int which:new int[]{AlertDialog.BUTTON_POSITIVE,AlertDialog.BUTTON_NEGATIVE})discard.getButton(which).setTextColor(accent);
            int messageId=activity.getResources().getIdentifier("message","id","android");TextView message=(TextView)discard.findViewById(messageId);if(message!=null)message.setTextColor(text);
        }else closePanel();
    }
    private void closePanel(){
        if(pill!=null){pill.dispose();pill=null;}
        if(panel!=null){Dialog old=panel;panel=null;old.dismiss();}
        if(foldAnimator!=null){foldAnimator.cancel();foldAnimator=null;}if(folderArrow!=null)folderArrow.animate().cancel();if(versionHint!=null){versionHint.dismiss(false);versionHint=null;}
        search=null;list=null;status=null;adapter=null;folder=null;folderBody=null;folderArrow=null;checkedOrder.clear();
    }
    private void refresh(boolean animate){
        if(!loaded)return;refreshCount();if(list==null)return;
        String query=search.getText().toString().trim().toLowerCase(Locale.ROOT);shown.clear();blockedShown.clear();
        for(Entry entry:all)if(query.isEmpty()||entry.name.toLowerCase(Locale.ROOT).contains(query)||entry.info.packageName.toLowerCase(Locale.ROOT).contains(query)){
            (inSelectedFolder(entry.info.packageName)?blockedShown:shown).add(entry);
        }
        Comparator<Entry> byName=new Comparator<Entry>(){public int compare(Entry a,Entry b){boolean ac=core.contains(a.info.packageName),bc=core.contains(b.info.packageName);if(ac!=bc)return ac?-1:1;boolean as=selected.contains(a.info.packageName),bs=selected.contains(b.info.packageName);if(as!=bs)return as?-1:1;int n=collator.compare(a.name,b.name);return n==0?a.info.packageName.compareTo(b.info.packageName):n;}};
        Collections.sort(shown,byName);Collections.sort(blockedShown,(a,b)->{int n=collator.compare(a.name,b.name);return n==0?a.info.packageName.compareTo(b.info.packageName):n;});
        renderFolder(false);adapter.notifyDataSetChanged();
        status.setText(protectedCount()+" apps protected from updates · "+(shown.size()+blockedShown.size())+" shown");
        if(pill!=null)pill.showApply(!selected.equals(saved));
    }
    private boolean inSelectedFolder(String pkg){return core.contains(pkg);}
    private int selectedFolderCount(){int count=0;for(String pkg:core)if(installedPackages.contains(pkg))count++;return count;}
    private int protectedCount(){int count=0;for(String pkg:selected)if(installedPackages.contains(pkg))count++;return count;}
    private void createFolder(){
        folder=new LinearLayout(activity);folder.setOrientation(1);folder.setBackground(new ThemeGlass(activity,22,ThemeGlass.PANEL));
        LinearLayout header=new LinearLayout(activity);header.setGravity(Gravity.CENTER_VERTICAL);header.setPadding(dp(10),dp(10),dp(4),dp(10));
        folderArtwork=new FolderArtwork();header.addView(folderArtwork,new LinearLayout.LayoutParams(dp(52),dp(52)));
        LinearLayout words=new LinearLayout(activity);words.setOrientation(1);words.setPadding(dp(10),0,dp(4),0);folderTitle=label("Packages blocked",16,text);folderTitle.setTypeface(android.graphics.Typeface.create("sans-serif-medium",0));words.addView(folderTitle);
        folderSummary=label("",12,secondary);folderSummary.setPadding(0,dp(3),0,0);words.addView(folderSummary);header.addView(words,new LinearLayout.LayoutParams(0,-2,1));
        folderArrow=new FolderChevron();folderArrow.setBackground(new android.graphics.drawable.InsetDrawable(new ThemeGlass(activity,20,ThemeGlass.FIELD),dp(6)));folderArrow.setLayerType(View.LAYER_TYPE_SOFTWARE,null);folderArrow.setClickable(true);folderArrow.setFocusable(true);folderArrow.setOnClickListener(v->toggleFolder());header.addView(folderArrow,new LinearLayout.LayoutParams(dp(48),dp(48)));
        header.setClickable(true);header.setFocusable(true);header.setOnClickListener(v->toggleFolder());folder.addView(header,new LinearLayout.LayoutParams(-1,-2));
        folderBody=new LinearLayout(activity);folderBody.setOrientation(1);folderBody.setVisibility(View.GONE);folder.addView(folderBody,new LinearLayout.LayoutParams(-1,-2));
    }
    private void toggleFolder(){if(saving)return;expanded=!expanded;renderFolder(true);}
    private void renderFolder(boolean animate){
        if(folder==null)return;boolean hasMatches=!blockedShown.isEmpty();
        if(!hasMatches&&folderAttached){list.removeHeaderView(folder);folderAttached=false;}
        else if(hasMatches&&!folderAttached){list.addHeaderView(folder,null,false);folderAttached=true;}
        folder.setVisibility(hasMatches?View.VISIBLE:View.GONE);folderTitle.setText("Packages blocked");folderSummary.setText(selectedFolderCount()+" apps protected from updates");folderArtwork.invalidate();
        boolean open=expanded||(search!=null&&search.length()>0);
        folderArrow.setContentDescription(open?"Collapse blocked updates":"Expand blocked updates");
        if(foldAnimator!=null){foldAnimator.cancel();foldAnimator=null;}
        int before=folderBody.getVisibility()==View.GONE?0:folderBody.getHeight();
        for(int i=0;i<blockedShown.size();i++){
            Entry entry=blockedShown.get(i);View existing=i<folderBody.getChildCount()?folderBody.getChildAt(i):null;
            if(existing!=null&&existing.getTag() instanceof Holder&&entry.info.packageName.equals(((Holder)existing.getTag()).packageName))appRow(entry,existing);
            else{if(existing!=null)folderBody.removeViewAt(i);folderBody.addView(appRow(entry,null),i,new LinearLayout.LayoutParams(-1,-2));}
        }
        while(folderBody.getChildCount()>blockedShown.size())folderBody.removeViewAt(folderBody.getChildCount()-1);
        if(!animate||!AppMotion.enabled(activity)){
            folderBody.getLayoutParams().height=-2;folderBody.setVisibility(open?View.VISIBLE:View.GONE);folderBody.setAlpha(1);folderArrow.setRotation(open?180:0);return;
        }
        int width=Math.max(dp(240),folder.getWidth());folderBody.setVisibility(View.VISIBLE);folderBody.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
        final boolean show=open;final int target=open?folderBody.getMeasuredHeight():0;folderBody.getLayoutParams().height=before;folderBody.requestLayout();
        folderArrow.animate().rotation(open?180:0).setDuration(320).setInterpolator(new PathInterpolator(.2f,0,0,1)).start();
        foldAnimator=android.animation.ValueAnimator.ofInt(before,target);foldAnimator.setDuration(340);foldAnimator.setInterpolator(new PathInterpolator(.2f,0,0,1));
        foldAnimator.addUpdateListener(a->{folderBody.getLayoutParams().height=(Integer)a.getAnimatedValue();folderBody.setAlpha(target==0?Math.max(0,1-a.getAnimatedFraction()):Math.min(1,.3f+a.getAnimatedFraction()));folderBody.requestLayout();});
        foldAnimator.addListener(new android.animation.AnimatorListenerAdapter(){boolean canceled;public void onAnimationCancel(android.animation.Animator a){canceled=true;}public void onAnimationEnd(android.animation.Animator a){if(canceled||folderBody==null)return;folderBody.getLayoutParams().height=-2;folderBody.setVisibility(show?View.VISIBLE:View.GONE);folderBody.setAlpha(1);foldAnimator=null;}});foldAnimator.start();
    }
    private final class FolderChevron extends View {
        final android.graphics.Paint paint=new android.graphics.Paint(3);FolderChevron(){super(activity);}
        protected void onDraw(android.graphics.Canvas canvas){super.onDraw(canvas);paint.setColor(text);paint.setStyle(android.graphics.Paint.Style.STROKE);paint.setStrokeWidth(dp(2));paint.setStrokeCap(android.graphics.Paint.Cap.ROUND);paint.setStrokeJoin(android.graphics.Paint.Join.ROUND);float x=getWidth()/2f,y=getHeight()/2f;android.graphics.Path path=new android.graphics.Path();path.moveTo(x-dp(5),y-dp(2));path.lineTo(x,y+dp(3));path.lineTo(x+dp(5),y-dp(2));canvas.drawPath(path,paint);}
    }
    private final class FolderArtwork extends View {
        final android.graphics.Paint paint=new android.graphics.Paint(3);final android.graphics.Path clip=new android.graphics.Path();final ThemeGlass background=new ThemeGlass(activity,16,ThemeGlass.PANEL);
        FolderArtwork(){super(activity);setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);}
        protected void onDraw(android.graphics.Canvas canvas){super.onDraw(canvas);int w=getWidth(),h=getHeight();float cx=w/2f,cy=h/2f,r=Math.min(w,h)*.47f;
            paint.setColor(AppSurfaces.active(activity)?0xff191919:FrostedSurface.dark(activity)?0xff34343b:0xffd6d6e0);canvas.drawCircle(cx,cy,r,paint);
            float[][] places={{.28f,.28f},{.72f,.28f},{.28f,.72f},{.72f,.72f}};
            for(int i=0;i<Math.min(4,blockedShown.size());i++){Entry entry=blockedShown.get(i);Drawable icon=icon(entry);if(icon==null)continue;float size=w*.41f,x=w*places[i][0],y=h*places[i][1];int save=canvas.save();clip.reset();clip.addCircle(x,y,size*.5f,android.graphics.Path.Direction.CW);canvas.clipPath(clip);android.graphics.Rect bounds=icon.getBounds();int oldLeft=bounds.left,oldTop=bounds.top,oldRight=bounds.right,oldBottom=bounds.bottom;try{icon.setBounds(Math.round(x-size/2),Math.round(y-size/2),Math.round(x+size/2),Math.round(y+size/2));icon.draw(canvas);}finally{icon.setBounds(oldLeft,oldTop,oldRight,oldBottom);canvas.restoreToCount(save);}}

        }
    }
    private Drawable icon(Entry e){Drawable value=icons.get(e.info.packageName);if(value==null)try{value=e.info.loadIcon(activity.getPackageManager());icons.put(e.info.packageName,value);}catch(Exception ignored){}return value;}
    private void apply(){
        if(!loaded||saving||selected.equals(saved))return;
        saving=true;search.setEnabled(false);unlockSwitch.setEnabled(false);adapter.notifyDataSetChanged();pill.applying();
        final Set<String> next=new TreeSet<String>(selected);final long start=SystemClock.uptimeMillis();
        io.execute(new Runnable(){public void run(){
            final boolean ok=Root.run("settings put secure "+StoreBlocker.KEY+" '"+MiscOptions.join(next)+"' && am force-stop com.android.vending")!=null;
            main.postDelayed(new Runnable(){public void run(){if(!live())return;saving=false;unlockSwitch.setEnabled(true);if(ok){saved=new TreeSet<String>(next);refresh(false);if(pill!=null)pill.applied();}else if(pill!=null)pill.applyFailed();if(search!=null)search.setEnabled(true);if(adapter!=null)adapter.notifyDataSetChanged();refreshCount();if(reload){reload=false;load();}}},Math.max(0,650-(SystemClock.uptimeMillis()-start)));
        }});
    }
    private void refreshCount(){int held=0;for(String pkg:selected)if(installedPackages.contains(pkg))held++;count.setText(held+" apps protected from updates");}
    private final class AppsAdapter extends BaseAdapter {
        public int getCount(){return shown.size();}public Object getItem(int p){return shown.get(p);}public long getItemId(int p){return shown.get(p).info.packageName.hashCode();}
        public View getView(int pos,View reuse,ViewGroup parent){return appRow(shown.get(pos),reuse);}
    }
    private View appRow(final Entry e,View reuse){
            LinearLayout row;Holder h;
            if(reuse==null){row=new LinearLayout(activity);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(12),dp(12),dp(12),dp(12));h=new Holder();h.icon=new ImageView(activity);h.icon.setScaleType(ImageView.ScaleType.FIT_CENTER);LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(dp(36),dp(36));ip.rightMargin=dp(12);row.addView(h.icon,ip);
                LinearLayout names=new LinearLayout(activity);names.setOrientation(1);h.name=label("",15,text);names.addView(h.name);h.pkg=label("",12,secondary);names.addView(h.pkg);row.addView(names,new LinearLayout.LayoutParams(0,-2,1));h.info=label("!",17,accent);h.info.setGravity(Gravity.CENTER);h.info.setTypeface(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD);h.info.setBackground(new ScopePanel.SoftSurface(activity,true,accent));h.info.setLayerType(View.LAYER_TYPE_SOFTWARE,null);h.info.setClickable(true);h.info.setFocusable(true);row.addView(h.info,new LinearLayout.LayoutParams(dp(44),dp(48)));h.check=new CheckBox(activity);h.check.setClickable(false);h.check.setFocusable(false);h.check.setButtonTintList(ColorStateList.valueOf(accent));row.addView(h.check);row.setTag(h);
            }else{row=(LinearLayout)reuse;h=(Holder)row.getTag();}
            row.animate().cancel();row.setTranslationY(0);
            h.packageName=e.info.packageName;boolean guarded=!unlock&&core.contains(e.info.packageName);boolean enabled=!saving&&!guarded;
            h.name.setText(e.name);h.pkg.setText(e.info.packageName);h.check.setChecked(selected.contains(e.info.packageName));h.check.setEnabled(!saving);h.check.setAlpha(guarded?.55f:1f);
            Drawable icon=icons.get(e.info.packageName);if(icon==null)try{icon=e.info.loadIcon(activity.getPackageManager());icons.put(e.info.packageName,icon);}catch(Exception ignored){}if(icon!=null&&icon.getConstantState()!=null)icon=icon.getConstantState().newDrawable(activity.getResources()).mutate();h.icon.setImageDrawable(icon);
            row.setEnabled(!saving);row.setAlpha(1);h.name.setAlpha(enabled?1f:.55f);h.pkg.setAlpha(enabled?1f:.65f);h.icon.setAlpha(enabled?1f:.55f);h.info.setEnabled(!saving);
            h.info.setContentDescription("Installed version of "+e.name);h.info.setOnClickListener(v->{if(versionHint!=null)versionHint.show("**"+e.name+"**\nVersion "+e.version+"\nBuild "+e.code+"\n"+e.info.packageName,panel.getWindow().getDecorView());});row.setContentDescription(e.name+", "+e.info.packageName+(h.check.isChecked()?", updates blocked":"")+(guarded?", core app locked. Tap to learn why":""));
            final Holder holder=h;h.check.setClickable(true);h.check.setOnClickListener(v->row.performClick());row.setOnClickListener(new View.OnClickListener(){public void onClick(View v){
                if(saving)return;
                if(!unlock&&core.contains(e.info.packageName)){holder.check.setChecked(selected.contains(e.info.packageName));showCoreWarning("Turn on Unlock OEM apps to uncheck this app. Updating it may break Pixel Suite support.");return;}
                if(core.contains(e.info.packageName)&&selected.contains(e.info.packageName))showCoreWarning("Updating this core app may break Pixel Suite support.");
                if(selected.add(e.info.packageName))checkedOrder.put(e.info.packageName,++order);else{selected.remove(e.info.packageName);checkedOrder.remove(e.info.packageName);}refresh(true);
            }});return row;
    }
    private void showCoreWarning(String message){if(lockedToast!=null)lockedToast.cancel();lockedToast=AppToast.makeText(activity,message,Toast.LENGTH_LONG);lockedToast.show(panel!=null&&panel.getWindow()!=null?panel.getWindow().getDecorView():null);}
    private static final class Holder{String packageName;ImageView icon;TextView name,pkg,info;CheckBox check;}
}
