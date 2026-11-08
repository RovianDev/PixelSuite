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
    private final Map<String,StoreUpdates.Available> updates=new HashMap<String,StoreUpdates.Available>();
    private final Map<String,Integer> rank=new HashMap<String,Integer>();
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
    private final Set<String> wasHeld=new HashSet<String>(),backedUp=new HashSet<String>();
    private boolean backingUp;
    private final Map<String,List<StoreBackup.Saved>> backups=new HashMap<String,List<StoreBackup.Saved>>();
    private final Map<String,Float> restoring=new HashMap<String,Float>();
    private final Set<String> restoreFailed=new HashSet<String>();
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
        if((Intent.ACTION_PACKAGE_REPLACED.equals(intent.getAction())||(Intent.ACTION_PACKAGE_ADDED.equals(intent.getAction())&&intent.getBooleanExtra(Intent.EXTRA_REPLACING,false)))&&intent.getData()!=null){
            final String pkg=intent.getData().getSchemeSpecificPart();io.execute(new Runnable(){public void run(){StoreBackup.endUpdate(pkg);}});
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
        }catch(Exception failure){AppToast.makeText(activity,I18n.cs("Reopen this page to refresh installed apps"),Toast.LENGTH_SHORT).show();}
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
                RootApps.Scan scan=RootApps.scan();
                for(RootApps.App x:RootApps.missing(pm,scan,installed,true,core,"Update blocker"))entries.add(new Entry(x.info,x.label,x.version,x.code));
                installed.addAll(scan.packages);
                final Set<String> removed=new HashSet<String>();
                for(String pkg:original)if(!core.contains(pkg)&&!installed.contains(pkg))removed.add(pkg);
                if(cleanOldDefaults&&!unlocked)for(String pkg:new String[]{"com.android.systemui","com.google.android.documentsui","com.android.documentsui","com.android.settings","com.google.android.packageinstaller","com.android.packageinstaller","com.android.intentresolver","com.android.imsstack","com.android.phone"})if(original.contains(pkg))removed.add(pkg);
                if(!removed.isEmpty()){
                    original.removeAll(removed);
                    if(Root.run("settings put secure "+StoreBlocker.KEY+" '"+MiscOptions.join(original)+"'")==null)throw new IllegalStateException("Root unavailable");
                }
                if(cleanOldDefaults&&Root.run("settings put secure pixelsuite_store_scope_defaults_cleaned 1")==null)throw new IllegalStateException("Root unavailable");
                Set<String> heldNow=new HashSet<String>(original);if(!unlocked)heldNow.addAll(core);heldNow.retainAll(installed);
                final Map<String,StoreUpdates.Available> found=StoreUpdates.read(activity,heldNow);
                final Map<String,List<StoreBackup.Saved>> savedApks=StoreBackup.all();
                main.post(new Runnable(){public void run(){
                    if(!live())return;loading=false;
                    if(!loaded){loaded=true;unlock=unlocked;saved=new TreeSet<String>(original);selected.addAll(original);}
                    else if(panel==null&&!saving){unlock=unlocked;saved=new TreeSet<String>(original);selected.clear();selected.addAll(original);}
                    selected.removeAll(removed);saved.removeAll(removed);
                    for(String pkg:new HashSet<String>(selected))if(!core.contains(pkg)&&!installed.contains(pkg))selected.remove(pkg);
                    installedPackages.clear();installedPackages.addAll(installed);if(!unlock)selected.addAll(core);
                    updates.clear();updates.putAll(found);backups.clear();backups.putAll(savedApks);
                    all.clear();all.addAll(entries);icons.evictAll();refresh(false);ready();
                    if(reload){reload=false;load();}
                }});
            }catch(Exception failure){main.post(new Runnable(){public void run(){if(live()){loading=false;count.setText(I18n.cs("Couldn't load apps"));if(status!=null)status.setText(I18n.cs("Check root access and reopen this page."));ready();}}});}
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
        TextView close=label("×",28,secondary);close.setGravity(Gravity.CENTER);AppCloseButton.style(close);close.setContentDescription(I18n.cs("Close app selection"));titleRow.addView(close,new LinearLayout.LayoutParams(dp(48),dp(48)));close.setOnClickListener(new View.OnClickListener(){public void onClick(View v){requestClose();}});header.addView(titleRow,new LinearLayout.LayoutParams(-1,-2));
        final float[] down=new float[1];header.setOnTouchListener(new View.OnTouchListener(){public boolean onTouch(View v,android.view.MotionEvent e){if(e.getActionMasked()==0){down[0]=e.getRawY();return true;}if(e.getActionMasked()==1){if(e.getRawY()-down[0]>dp(64))requestClose();return true;}return true;}});
        content.addView(header,new LinearLayout.LayoutParams(-1,-2));
        search=new EditText(activity);search.setSingleLine(true);search.setHint(I18n.cs("Search apps"));search.setContentDescription(I18n.cs("Search installed apps"));search.setTextColor(text);search.setHintTextColor(secondary);search.setBackground(new ThemeGlass(activity,24,ThemeGlass.FIELD));search.setLayerType(View.LAYER_TYPE_SOFTWARE,null);search.setPadding(dp(16),dp(10),dp(16),dp(10));content.addView(search,new LinearLayout.LayoutParams(-1,dp(48)));
        status=label(loaded?"":"Loading apps…",12,secondary);status.setPadding(dp(4),dp(8),dp(4),dp(8));content.addView(status);
        list=new ListView(activity);ToastMotion.watchScroll(list);list.setDivider(null);list.setBackgroundColor(Color.TRANSPARENT);createFolder();list.addHeaderView(folder,null,false);folderAttached=true;adapter=new AppsAdapter();list.setAdapter(adapter);content.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        TextView note=label("Tick Play Store in LSPosed for Pixel Suite.",12,secondary);note.setGravity(Gravity.CENTER);note.setPadding(0,dp(8),0,dp(8));content.addView(note,new LinearLayout.LayoutParams(-1,-2));
        final View footer=new View(activity);content.addView(footer,new LinearLayout.LayoutParams(-1,dp(80)));
        pill=new RestartPill(activity,root,accent,onAccent,true);pill.setSecondary(new Runnable(){public void run(){chooseBackupAll();}},"Unticked apps can get updates","Back up 99","Backing up…","Saved");pill.setApplyAction(new Runnable(){public void run(){apply();}});
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int c,int after){}public void afterTextChanged(Editable e){}public void onTextChanged(CharSequence s,int start,int before,int c){refresh(false);}});
        panel.setContentView(root);panel.show();Rtl.dialog(panel);
        Window window=panel.getWindow();if(window!=null){window.setBackgroundDrawableResource(android.R.color.transparent);window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);AppSurfaces.dim(window,.45f);window.setGravity(Gravity.BOTTOM);window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE|WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);AppPanelBounds.fit(activity,window,(int)(activity.getResources().getDisplayMetrics().heightPixels*.85f));window.setNavigationBarColor(Color.TRANSPARENT);}
        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener(){public WindowInsets onApplyWindowInsets(View v,WindowInsets in){content.setPadding(dp(16)+in.getSystemWindowInsetLeft(),0,dp(16)+in.getSystemWindowInsetRight(),in.getSystemWindowInsetBottom());return in;}});
        root.requestApplyInsets();refresh(false);load();
        content.setTranslationY(dp(36));content.setAlpha(0);content.animate().translationY(0).alpha(1).setDuration(260).setInterpolator(new PathInterpolator(.2f,0,0,1)).start();
    }
    private TextView label(String value,int size,int color){TextView t=new TextView(activity);t.setText(I18n.cs(value));t.setTextSize(size);t.setTextColor(color);return t;}
    private void requestClose(){
        if(saving){AppToast.makeText(activity,I18n.cs("Wait for your changes to finish applying"),Toast.LENGTH_SHORT).show();return;}
        if(!selected.equals(saved)){
            if(discard!=null&&discard.isShowing())return;
            discard=new AlertDialog.Builder(activity).setTitle(I18n.cs("Discard changes?")).setMessage(I18n.cs("Your app selections haven't been applied.")).setNegativeButton(I18n.cs("Keep editing"),null).setPositiveButton(I18n.cs("Discard"),new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int which){selected.clear();selected.addAll(saved);if(!unlock)selected.addAll(core);closePanel();}}).create();
            discard.show();Rtl.dialog(discard);Window w=discard.getWindow();if(w!=null)w.setBackgroundDrawable(AppSurfaces.dialog(activity,28));
            for(int which:new int[]{AlertDialog.BUTTON_POSITIVE,AlertDialog.BUTTON_NEGATIVE})discard.getButton(which).setTextColor(accent);
            int messageId=activity.getResources().getIdentifier("message","id","android");TextView message=(TextView)discard.findViewById(messageId);if(message!=null)message.setTextColor(text);
        }else closePanel();
    }
    private void closePanel(){
        if(pill!=null){pill.dispose();pill=null;}
        wasHeld.clear();backedUp.clear();backingUp=false;
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
        if(animate&&!rank.isEmpty()){final Map<String,Integer> r=rank;Collections.sort(shown,(a,b)->{Integer x=r.get(a.info.packageName),y=r.get(b.info.packageName);if(x==null&&y==null)return byName.compare(a,b);if(x==null)return 1;if(y==null)return -1;return x-y;});}
        else{Collections.sort(shown,byName);rank.clear();for(int i=0;i<shown.size();i++)rank.put(shown.get(i).info.packageName,i);}
        Collections.sort(blockedShown,(a,b)->{int n=collator.compare(a.name,b.name);return n==0?a.info.packageName.compareTo(b.info.packageName):n;});
        renderFolder(false);adapter.notifyDataSetChanged();
        status.setText(I18n.cs(protectedCount()+" apps protected from updates · "+(shown.size()+blockedShown.size())+" shown"));
        if(pill!=null)pill.showApply(!selected.equals(saved));
        updateBackupPill();
    }
    private boolean savedNow(Entry e){List<StoreBackup.Saved> l=backups.get(e.info.packageName);if(l!=null)for(StoreBackup.Saved x:l)if(x.code==e.code)return true;return false;}
    private ArrayList<Entry> toBackUp(){
        wasHeld.addAll(saved);if(!unlock)wasHeld.addAll(core);
        ArrayList<Entry> out=new ArrayList<Entry>();
        for(Entry e:all){String p=e.info.packageName;if(!installedPackages.contains(p)||backedUp.contains(p)||savedNow(e))continue;
            boolean pending=selected.contains(p)!=saved.contains(p),unheld=wasHeld.contains(p)&&!selected.contains(p);
            if(pending||unheld)out.add(e);}
        return out;
    }
    private boolean pendingChange(){for(Entry e:all){String p=e.info.packageName;if(installedPackages.contains(p)&&selected.contains(p)!=saved.contains(p))return true;}return false;}
    private void refreshBackups(){io.execute(new Runnable(){public void run(){final Map<String,List<StoreBackup.Saved>> m=StoreBackup.all();main.post(new Runnable(){public void run(){if(!live())return;backups.clear();backups.putAll(m);if(adapter!=null)adapter.notifyDataSetChanged();renderFolder(false);}});}});}
    private String pillLine;
    private void updateBackupPill(){
        if(pill==null||backingUp)return;
        int n=loaded?toBackUp().size():0;
        boolean pending=loaded&&pendingChange();
        String line=pending?"Save them first, then apply":"Unticked apps can get updates";
        if(!line.equals(pillLine)){pillLine=line;pill.setSecondary(new Runnable(){public void run(){chooseBackupAll();}},line,"Back up 99","Backing up…","Saved");}
        if(n>0)pill.showSecondary("Back up "+n,true);else if(pending)pill.showSecondary("Saved",false);else pill.showSecondary(null,true);
    }
    private void chooseBackupAll(){
        if(backingUp||!live()||pill==null)return;
        final ArrayList<Entry> list=toBackUp();if(list.isEmpty())return;
        ArrayList<String> pk=new ArrayList<String>();for(Entry e:list)pk.add(e.info.packageName);
        BackupChooser.backup(activity,io,list.size()==1?"Back up "+list.get(0).name:"Back up "+list.size()+" apps",pk,text,secondary,accent,data->backupAll(data));
    }
    private boolean save(Entry e,boolean data){
        String pkg=e.info.packageName;
        if(!StoreBackup.backup(pkg,e.version,e.code))return false;
        return !data||activity.getPackageName().equals(pkg)||AppDataBackup.backup(pkg,e.code,null)==null;
    }
    private void backupAll(final boolean data){
        if(backingUp||!live()||pill==null)return;
        final ArrayList<Entry> list=toBackUp();if(list.isEmpty())return;
        backingUp=true;pill.showSecondary("Backing up…",false);final long start=SystemClock.uptimeMillis();
        io.execute(new Runnable(){public void run(){
            final Set<String> ok=new HashSet<String>();
            for(Entry e:list)if(save(e,data))ok.add(e.info.packageName);
            main.postDelayed(new Runnable(){public void run(){
                if(!live()||pill==null){backingUp=false;return;}
                backedUp.addAll(ok);if(!ok.isEmpty())refreshBackups();
                if(ok.size()==list.size()){
                    pill.showSecondary("Saved",false);
                    main.postDelayed(new Runnable(){public void run(){backingUp=false;if(live()&&pill!=null)updateBackupPill();}},1100);
                }else{backingUp=false;say("Couldn't back up "+(list.size()-ok.size())+" of "+list.size()+". Check root and free space.");updateBackupPill();}
            }},Math.max(0,650-(SystemClock.uptimeMillis()-start)));
        }});
    }
    private boolean inSelectedFolder(String pkg){return core.contains(pkg);}
    private int selectedFolderCount(){int count=0;for(String pkg:core)if(installedPackages.contains(pkg))count++;return count;}
    private int protectedCount(){int count=0;for(String pkg:selected)if(installedPackages.contains(pkg))count++;return count;}
    private void createFolder(){
        folder=new LinearLayout(activity);folder.setOrientation(1);folder.setBackground(new ThemeGlass(activity,22,ThemeGlass.RAISED));
        LinearLayout header=new LinearLayout(activity);header.setGravity(Gravity.CENTER_VERTICAL);header.setPadding(dp(10),dp(10),dp(4),dp(10));
        folderArtwork=new FolderArtwork();header.addView(folderArtwork,new LinearLayout.LayoutParams(dp(52),dp(52)));
        LinearLayout words=new LinearLayout(activity);words.setOrientation(1);words.setPadding(dp(10),0,dp(4),0);folderTitle=label("Packages blocked",16,text);folderTitle.setTypeface(I18n.medium());words.addView(folderTitle);
        folderSummary=label("",12,secondary);folderSummary.setPadding(0,dp(3),0,0);words.addView(folderSummary);header.addView(words,new LinearLayout.LayoutParams(0,-2,1));
        folderArrow=new FolderChevron();folderArrow.setBackground(new android.graphics.drawable.InsetDrawable(new ThemeGlass(activity,20,ThemeGlass.FIELD),dp(6)));folderArrow.setLayerType(View.LAYER_TYPE_SOFTWARE,null);folderArrow.setClickable(true);folderArrow.setFocusable(true);folderArrow.setOnClickListener(v->toggleFolder());header.addView(folderArrow,new LinearLayout.LayoutParams(dp(48),dp(48)));
        header.setClickable(true);header.setFocusable(true);header.setOnClickListener(v->toggleFolder());folder.addView(header,new LinearLayout.LayoutParams(-1,-2));
        folderBody=new LinearLayout(activity);folderBody.setOrientation(1);folderBody.setVisibility(View.GONE);folder.addView(folderBody,new LinearLayout.LayoutParams(-1,-2));
    }
    private void toggleFolder(){if(saving)return;expanded=!expanded;renderFolder(true);}
    private void folderHeight(int height){
        ViewGroup.LayoutParams params=folderBody.getLayoutParams();params.height=height;
        folderBody.setLayoutParams(params);
    }
    private void finishFolderLayout(boolean open){
        folderHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        folderBody.setVisibility(open?View.VISIBLE:View.GONE);folderBody.setAlpha(1);
        folder.requestLayout();if(list!=null)list.requestLayout();
    }
    private void renderFolder(boolean animate){
        if(folder==null)return;boolean hasMatches=!blockedShown.isEmpty();
        if(!hasMatches&&folderAttached){list.removeHeaderView(folder);folderAttached=false;}
        else if(hasMatches&&!folderAttached){list.addHeaderView(folder,null,false);folderAttached=true;}
        folder.setVisibility(hasMatches?View.VISIBLE:View.GONE);folderTitle.setText(I18n.cs("Packages blocked"));folderSummary.setText(I18n.cs(selectedFolderCount()+" apps protected from updates"));folderArtwork.invalidate();
        boolean open=expanded||(search!=null&&search.length()>0);
        folderArrow.setContentDescription(I18n.cs(open?"Collapse blocked updates":"Expand blocked updates"));
        if(foldAnimator!=null){foldAnimator.cancel();foldAnimator=null;}
        int before=folderBody.getVisibility()==View.GONE?0:folderBody.getHeight();
        for(int i=0;i<blockedShown.size();i++){
            Entry entry=blockedShown.get(i);View existing=i<folderBody.getChildCount()?folderBody.getChildAt(i):null;
            if(existing!=null&&existing.getTag() instanceof Holder&&entry.info.packageName.equals(((Holder)existing.getTag()).packageName))appRow(entry,existing);
            else{if(existing!=null)folderBody.removeViewAt(i);folderBody.addView(appRow(entry,null),i,new LinearLayout.LayoutParams(-1,-2));}
        }
        while(folderBody.getChildCount()>blockedShown.size())folderBody.removeViewAt(folderBody.getChildCount()-1);
        if(!animate||!AppMotion.enabled(activity)){
            finishFolderLayout(open);folderArrow.setRotation(open?180:0);return;
        }
        LinearLayout.LayoutParams bodyParams=(LinearLayout.LayoutParams)folderBody.getLayoutParams();
        int width=folder.getWidth()-folder.getPaddingLeft()-folder.getPaddingRight()-bodyParams.leftMargin-bodyParams.rightMargin;
        if(width<=0){finishFolderLayout(open);folderArrow.setRotation(open?180:0);return;}
        folderBody.setVisibility(View.VISIBLE);folderHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        folderBody.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
        final boolean show=open;final int target=open?folderBody.getMeasuredHeight():0;folderHeight(before);
        folderArrow.animate().rotation(open?180:0).setDuration(320).setInterpolator(new PathInterpolator(.2f,0,0,1)).start();
        foldAnimator=android.animation.ValueAnimator.ofInt(before,target);foldAnimator.setDuration(340);foldAnimator.setInterpolator(new PathInterpolator(.2f,0,0,1));
        foldAnimator.addUpdateListener(a->{folderHeight((Integer)a.getAnimatedValue());folderBody.setAlpha(target==0?Math.max(0,1-a.getAnimatedFraction()):Math.min(1,.3f+a.getAnimatedFraction()));});
        foldAnimator.addListener(new android.animation.AnimatorListenerAdapter(){boolean canceled;public void onAnimationCancel(android.animation.Animator a){canceled=true;}public void onAnimationEnd(android.animation.Animator a){if(canceled||folderBody==null)return;finishFolderLayout(show);foldAnimator=null;}});foldAnimator.start();
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
    private void refreshCount(){int held=0;for(String pkg:selected)if(installedPackages.contains(pkg))held++;count.setText(I18n.cs(held+" apps protected from updates"));}
    private final class AppsAdapter extends BaseAdapter {
        public int getCount(){return shown.size();}public Object getItem(int p){return shown.get(p);}public long getItemId(int p){return shown.get(p).info.packageName.hashCode();}
        public View getView(int pos,View reuse,ViewGroup parent){return appRow(shown.get(pos),reuse);}
    }
    private View appRow(final Entry e,View reuse){
            LinearLayout row;Holder h;
            if(reuse==null){row=new LinearLayout(activity);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(12),dp(12),dp(12),dp(12));h=new Holder();h.icon=new ImageView(activity);h.icon.setScaleType(ImageView.ScaleType.FIT_CENTER);LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(dp(36),dp(36));ip.rightMargin=dp(12);row.addView(h.icon,ip);
                LinearLayout names=new LinearLayout(activity);names.setOrientation(1);h.name=label("",15,text);names.addView(h.name);h.pkg=label("",12,secondary);names.addView(h.pkg);h.line=new ProgressLine();LinearLayout.LayoutParams lineP=new LinearLayout.LayoutParams(-1,dp(3));lineP.topMargin=dp(6);names.addView(h.line,lineP);h.line.setVisibility(View.GONE);row.addView(names,new LinearLayout.LayoutParams(0,-2,1));
                h.restore=new ImageView(activity);h.restore.setScaleType(ImageView.ScaleType.CENTER);int rid=activity.getResources().getIdentifier("ic_ps_restore","drawable",activity.getPackageName());if(rid!=0)h.restore.setImageResource(rid);h.restore.setBackground(BackupRestorePage.roundRipple(activity,4,0)); h.restore.setClickable(true);h.restore.setFocusable(true);h.restore.setVisibility(View.GONE);row.addView(h.restore,new LinearLayout.LayoutParams(dp(40),dp(48)));h.info=label("!",17,accent);h.info.setGravity(Gravity.CENTER);h.info.setTypeface(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD);h.info.setBackground(new ScopePanel.SoftSurface(activity,true,accent));h.info.setLayerType(View.LAYER_TYPE_SOFTWARE,null);h.info.setClickable(true);h.info.setFocusable(true);row.addView(h.info,new LinearLayout.LayoutParams(dp(44),dp(48)));h.check=new CheckBox(activity);h.check.setClickable(false);h.check.setFocusable(false);h.check.setButtonTintList(ColorStateList.valueOf(accent));row.addView(h.check);row.setTag(h);
            }else{row=(LinearLayout)reuse;h=(Holder)row.getTag();}
            row.animate().cancel();row.setTranslationY(0);
            h.packageName=e.info.packageName;boolean guarded=!unlock&&core.contains(e.info.packageName);boolean enabled=!saving&&!guarded;
            h.name.setText(I18n.cs(e.name));h.pkg.setText(I18n.cs(versionLine(e))); h.check.setChecked(selected.contains(e.info.packageName));h.check.jumpDrawablesToCurrentState(); h.check.setEnabled(!saving);h.check.setAlpha(guarded?.55f:1f);
            Drawable icon=icons.get(e.info.packageName);if(icon==null)try{icon=e.info.loadIcon(activity.getPackageManager());icons.put(e.info.packageName,icon);}catch(Exception ignored){}if(icon!=null&&icon.getConstantState()!=null)icon=icon.getConstantState().newDrawable(activity.getResources()).mutate();h.icon.setImageDrawable(icon);
            row.setEnabled(!saving);row.setAlpha(1);h.name.setAlpha(enabled?1f:.55f);h.pkg.setAlpha(enabled?1f:.65f);h.icon.setAlpha(enabled?1f:.55f);h.info.setEnabled(!saving);
            bindRestore(e,h);
            h.info.setContentDescription(I18n.cs((saved.contains(e.info.packageName)||(!unlock&&core.contains(e.info.packageName))?"Update, back up or restore ":"Installed version of ")+e.name));h.info.setOnClickListener(v->actions(e)); row.setContentDescription(I18n.cs(e.name+", "+e.info.packageName+(h.check.isChecked()?", updates blocked":"")+(guarded?", core app locked. Tap to learn why":"")));
            final Holder holder=h;h.check.setClickable(true);h.check.setOnClickListener(v->row.performClick());row.setOnClickListener(new View.OnClickListener(){public void onClick(View v){
                hideKeyboard();
                if(saving)return;
                if(!unlock&&core.contains(e.info.packageName)){holder.check.setChecked(selected.contains(e.info.packageName));showCoreWarning("Turn on Unlock OEM apps to uncheck this app. Updating it may break Pixel Suite support.");return;}
                if(core.contains(e.info.packageName)&&selected.contains(e.info.packageName))showCoreWarning("Updating this core app may break Pixel Suite support.");
                if(selected.add(e.info.packageName))checkedOrder.put(e.info.packageName,++order);else{selected.remove(e.info.packageName);checkedOrder.remove(e.info.packageName);}refresh(true);
            }});return row;
    }
    private StoreUpdates.Available newer(Entry e){StoreUpdates.Available a=updates.get(e.info.packageName);return a!=null&&a.code>e.code&&(saved.contains(e.info.packageName)||(!unlock&&core.contains(e.info.packageName)))?a:null;}
    private CharSequence versionLine(Entry e){
        String base=e.info.packageName+"  ·  "+e.version;StoreUpdates.Available a=newer(e);if(a==null)return base;
        String extra="  ("+(a.name!=null?a.name:I18n.t("Build {0}").equals("Build {0}")?"build "+a.code:I18n.t("Build "+a.code))+")";
        SpannableString t=new SpannableString(base+extra);
        int red=FrostedSurface.dark(activity)?0xFFFF8A80:0xFFC62828;
        t.setSpan(new android.text.style.ForegroundColorSpan(red),base.length(),t.length(),0);
        return t;
    }
    private boolean backupOn(){return true;}
    private void hideKeyboard(){
        if(search==null)return;
        try{if(search.hasFocus()){((android.view.inputmethod.InputMethodManager)activity.getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(search.getWindowToken(),0);search.clearFocus();}}catch(Throwable ignored){}
    }
    private void say(String message){AppToast t=AppToast.makeText(activity,I18n.cs(message),Toast.LENGTH_LONG);t.show(panel!=null&&panel.getWindow()!=null?panel.getWindow().getDecorView():null);}
    private AlertDialog styled(AlertDialog d){
        d.show();Rtl.dialog(d);Window w=d.getWindow();if(w!=null)w.setBackgroundDrawable(AppSurfaces.dialog(activity,28));
        for(int which:new int[]{AlertDialog.BUTTON_POSITIVE,AlertDialog.BUTTON_NEGATIVE,AlertDialog.BUTTON_NEUTRAL}){Button b=d.getButton(which);if(b!=null)b.setTextColor(accent);}
        int messageId=activity.getResources().getIdentifier("message","id","android");TextView message=(TextView)d.findViewById(messageId);if(message!=null)message.setTextColor(text);
        return d;
    }
    private void actions(final Entry e){
        final String pkg=e.info.packageName;
        boolean held=saved.contains(pkg)||(!unlock&&core.contains(pkg));
        if(!held){if(versionHint!=null&&panel!=null)versionHint.show("**"+e.name+"**\nVersion "+e.version+"\nBuild "+e.code+"\n"+pkg+"\n\nTick it and apply to hold it back.",panel.getWindow().getDecorView());return;}
        io.execute(new Runnable(){public void run(){
            final List<StoreBackup.Saved> saved=StoreBackup.list(pkg);
            main.post(new Runnable(){public void run(){
                if(!live())return;
                final boolean backup=backupOn();
                final ArrayList<String> items=new ArrayList<String>();
                items.add(backup?"Back up and update":"Update");items.add("Back up now");
                if(!saved.isEmpty())items.add("Restore a saved version ("+saved.size()+")");
                StoreUpdates.Available a=newer(e);
                styled(new AlertDialog.Builder(activity).setTitle(I18n.cs(e.name+" · "+e.version+(a!=null?" → "+(a.name!=null?a.name:"build "+a.code):""))).setItems(I18n.all(items.toArray(new String[0])),new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int which){
                    if(which==0)update(e,backup);
                    else if(which==1)BackupChooser.backup(activity,io,"Back up "+e.name,java.util.Collections.singletonList(e.info.packageName),text,secondary,accent,data->backupOnly(e,data));
                    else restorePick(e,saved);
                }}).setNegativeButton(I18n.cs("Cancel"),null).create());
            }});
        }});
    }
    private void update(final Entry e,final boolean backup){
        say(backup?"Saving "+e.name+" "+e.version+"…":"Opening Play Store…");
        io.execute(new Runnable(){public void run(){
            final boolean kept=!backup||save(e,BackupChooser.withData(activity));
            final boolean opened=kept&&StoreBackup.openUpdate(e.info.packageName,e.code);
            main.post(new Runnable(){public void run(){
                if(!live())return;
                if(!kept)say("Couldn't save "+e.name+", so it wasn't updated. Check root and free space.");
                else if(!opened)say("Couldn't open Play Store");
                else say((backup?"Saved "+e.version+". ":"")+"Tap Update in Play Store. "+e.name+" is held back again once it's updated.");
            }});
        }});
    }
    private void backupOnly(final Entry e,final boolean data){
        final String pkg=e.info.packageName;if(restoring.containsKey(pkg))return;
        restoring.put(pkg,0f);restoreFailed.remove(pkg);showProgress(pkg);
        final AppDataBackup.Progress at=f->main.post(()->{Float now=restoring.get(pkg);if(now!=null&&f>now&&live()){restoring.put(pkg,f);showProgress(pkg);}});
        io.execute(new Runnable(){public void run(){
            boolean ok=StoreBackup.backup(pkg,e.version,e.code);
            at.at(data?.1f:.9f);
            if(ok&&data&&!activity.getPackageName().equals(pkg))ok=AppDataBackup.backup(pkg,e.code,f->at.at(.1f+.9f*f))==null;
            final boolean kept=ok;
            main.post(new Runnable(){public void run(){
                if(!live()){restoring.remove(pkg);return;}
                if(!kept)restoreFailed.add(pkg);
                Float f0=restoring.get(pkg);
                android.animation.ValueAnimator fill=android.animation.ValueAnimator.ofFloat(f0==null?0f:f0,1f);fill.setDuration(kept?260:160);
                fill.addUpdateListener(a->{restoring.put(pkg,(Float)a.getAnimatedValue());showProgress(pkg);});
                fill.addListener(new android.animation.AnimatorListenerAdapter(){public void onAnimationEnd(android.animation.Animator a){
                    main.postDelayed(new Runnable(){public void run(){restoring.remove(pkg);restoreFailed.remove(pkg);if(!live())return;showProgress(pkg);
                        say(kept?"Saved "+e.name+" "+e.version+(data?" with its data":""):"Couldn't save "+e.name+". Check root and free space.");if(kept)refreshBackups();}},kept?500:900);
                }});
                fill.start();
            }});
        }});
    }
    private void restorePick(final Entry e,final List<StoreBackup.Saved> saved){
        String[] labels=new String[saved.size()];
        for(int i=0;i<saved.size();i++){StoreBackup.Saved x=saved.get(i);labels[i]=(x.version.isEmpty()?"Build "+x.code:x.version)+(x.code==e.code?" (installed)":x.code<e.code?" (older)":"");}
        styled(new AlertDialog.Builder(activity).setTitle(I18n.cs("Restore "+e.name)).setItems(I18n.all(labels),new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int which){
            final StoreBackup.Saved x=saved.get(which);
            if(x.data&&!activity.getPackageName().equals(e.info.packageName)){BackupChooser.restore(activity,"Restore "+e.name+" "+(x.version.isEmpty()?"build "+x.code:x.version),true,text,secondary,accent,data->doRestore(e,x,data));return;}
            styled(new AlertDialog.Builder(activity).setTitle(I18n.cs("Restore "+(x.version.isEmpty()?"build "+x.code:x.version)+"?"))
                    .setMessage(I18n.cs(e.name+" goes back to this version. Its data stays as it is now. If it doesn't open after, clear its storage in Settings."))
                    .setNegativeButton(I18n.cs("Cancel"),null).setPositiveButton(I18n.cs("Restore"),new DialogInterface.OnClickListener(){public void onClick(DialogInterface d2,int w2){doRestore(e,x,false);}}).create());
        }}).setNegativeButton(I18n.cs("Cancel"),null).create());
    }

    private void showCoreWarning(String message){if(lockedToast!=null)lockedToast.cancel();lockedToast=AppToast.makeText(activity,I18n.cs(message),Toast.LENGTH_LONG);lockedToast.show(panel!=null&&panel.getWindow()!=null?panel.getWindow().getDecorView():null);}
    private static final class Holder{String packageName;ImageView icon,restore;TextView name,pkg,info;CheckBox check;ProgressLine line;}
    private StoreBackup.Saved restoreTarget(Entry e){List<StoreBackup.Saved> l=backups.get(e.info.packageName);if(l==null)return null;for(StoreBackup.Saved x:l)if(x.code!=e.code)return x;return null;}
    private void bindRestore(final Entry e,Holder h){
        String pkg=e.info.packageName;final StoreBackup.Saved target=restoreTarget(e);Float f=restoring.get(pkg);
        int g=BackupRim.green(activity);
        List<StoreBackup.Saved> any=backups.get(pkg);final boolean has=any!=null&&!any.isEmpty();
        h.restore.setVisibility(View.VISIBLE);
        int rid=activity.getResources().getIdentifier(has?"ic_ps_restore":"ic_ps_store_backup","drawable",activity.getPackageName());
        Integer shown=(Integer)h.restore.getTag();if(rid!=0&&(shown==null||shown!=rid)){h.restore.setImageResource(rid);h.restore.setTag(rid);}
        h.restore.setColorFilter(g);h.restore.setEnabled(f==null&&!saving);h.restore.setAlpha(f==null?1f:.5f);
        h.restore.setContentDescription(I18n.cs(!has?"Back up "+e.name:target==null?"Restore "+e.name+" (its saved version is the one installed)":"Restore "+e.name+" "+(target.version.isEmpty()?"build "+target.code:target.version)));
        h.restore.setOnClickListener(v->{if(has)restoreNow(e);else BackupChooser.backup(activity,io,"Back up "+e.name,java.util.Collections.singletonList(e.info.packageName),text,secondary,accent,data->backupOnly(e,data));});
        h.line.setVisibility(f!=null?View.VISIBLE:View.GONE);if(f!=null){h.line.fraction=f;h.line.failed=restoreFailed.contains(pkg);h.line.invalidate();}
    }
    private void showProgress(String pkg){
        Float f=restoring.get(pkg);
        java.util.List<ViewGroup> groups=new java.util.ArrayList<ViewGroup>();if(list!=null)groups.add(list);if(folderBody!=null)groups.add(folderBody);
        for(ViewGroup g:groups)for(int i=0;i<g.getChildCount();i++){View c=g.getChildAt(i);if(!(c.getTag() instanceof Holder))continue;Holder h=(Holder)c.getTag();if(!pkg.equals(h.packageName))continue;
            h.line.setVisibility(f!=null?View.VISIBLE:View.GONE);if(f!=null){h.line.fraction=f;h.line.failed=restoreFailed.contains(pkg);h.line.invalidate();}h.restore.setEnabled(f==null&&!saving);h.restore.setAlpha(f==null?1f:.5f);}
    }
    private void restoreNow(final Entry e){
        final String pkg=e.info.packageName;if(restoring.containsKey(pkg)||saving)return;
        final StoreBackup.Saved x=restoreTarget(e);if(x==null){say("The saved version of "+e.name+" is the one installed now");return;}
        if(x.data&&!activity.getPackageName().equals(pkg)){BackupChooser.restore(activity,"Restore "+e.name+" "+(x.version.isEmpty()?"build "+x.code:x.version),true,text,secondary,accent,data->doRestore(e,x,data));return;}
        doRestore(e,x,false);
    }
    private void doRestore(final Entry e,final StoreBackup.Saved x,final boolean data){
        final String pkg=e.info.packageName;if(restoring.containsKey(pkg)||saving)return;
        final String label=x.version.isEmpty()?"build "+x.code:x.version;
        restoring.put(pkg,0f);restoreFailed.remove(pkg);showProgress(pkg);
        io.execute(new Runnable(){public void run(){
            final String error=AppDataBackup.restore(pkg,x.code,data,f->main.post(()->{if(restoring.containsKey(pkg)&&live()){Float now=restoring.get(pkg);if(now==null||f>now){restoring.put(pkg,f);showProgress(pkg);}}}));
            main.post(new Runnable(){public void run(){
                if(!live()){restoring.remove(pkg);return;}
                final float from=restoring.containsKey(pkg)?restoring.get(pkg):0f;
                if(error!=null)restoreFailed.add(pkg);
                android.animation.ValueAnimator fill=android.animation.ValueAnimator.ofFloat(from,1f);fill.setDuration(error==null?260:160);
                fill.addUpdateListener(a->{restoring.put(pkg,(Float)a.getAnimatedValue());showProgress(pkg);});
                fill.addListener(new android.animation.AnimatorListenerAdapter(){public void onAnimationEnd(android.animation.Animator a){
                    main.postDelayed(new Runnable(){public void run(){restoring.remove(pkg);restoreFailed.remove(pkg);if(!live())return;showProgress(pkg);
                        say(error==null?e.name+" is back to "+label+(data?", with everything as it was":""):"Couldn't restore "+e.name+": "+error);load();}},error==null?500:900);
                }});
                fill.start();
            }});
        }});
    }
    private final class ProgressLine extends View {
        float fraction;boolean failed;final android.graphics.Paint paint=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);final android.graphics.RectF box=new android.graphics.RectF();
        ProgressLine(){super(activity);setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);}
        @Override protected void onDraw(android.graphics.Canvas c){
            float w=getWidth(),h=getHeight(),r=h/2f;if(w<=0)return;
            boolean dark=FrostedSurface.dark(activity)||AppSurfaces.active(activity);
            paint.setColor(dark?0x33ffffff:0x1f000000);box.set(0,0,w,h);c.drawRoundRect(box,r,r,paint);
            paint.setColor(failed?(dark?0xFFFF8A80:0xFFC62828):BackupRim.green(activity));box.set(0,0,Math.max(h,w*Math.max(0f,Math.min(1f,fraction))),h);c.drawRoundRect(box,r,r,paint);
        }
    }
}
