package io.github.pixelsuite;

import android.app.Activity;

import android.app.AlertDialog;

import android.content.DialogInterface;

import android.content.Intent;

import android.content.res.ColorStateList;

import android.content.res.Configuration;

import android.graphics.Color;

import android.graphics.Typeface;

import android.graphics.drawable.ColorDrawable;

import android.graphics.drawable.Drawable;

import android.graphics.drawable.GradientDrawable;

import android.graphics.drawable.RippleDrawable;

import android.os.Bundle;

import android.os.Handler;

import android.os.Looper;

import android.util.TypedValue;

import android.view.Gravity;

import android.view.View;

import android.view.ViewGroup;

import android.view.Window;

import android.view.WindowInsets;

import android.widget.CompoundButton;

import android.widget.FrameLayout;

import android.widget.ImageView;

import android.widget.LinearLayout;

import android.widget.ScrollView;

import android.widget.Switch;

import android.widget.TextView;

import android.widget.Toast;

import java.util.LinkedHashMap;

import java.util.Map;

import java.util.concurrent.ExecutorService;

import java.util.concurrent.LinkedBlockingQueue;

import java.util.concurrent.ThreadPoolExecutor;

import java.util.concurrent.TimeUnit;

public class SettingsActivity extends Activity {

    private static final String LAUNCHER = "com.google.android.apps.nexuslauncher";

    private boolean mDark;

    private boolean mAppSuperDark;

    private View mAppRoot;
    private AppSearch mSearch;
    private boolean mSearchRootReady;
    private boolean mSearchStatesLoading;
    private long mSearchStatesAt;
    private android.widget.ScrollView mPageScroll;
    private TextView mAdProtectionStatus;
    private boolean mDnaPixelStack;

    private IconPackPage mIconPackPage;

    private final Runnable mPatronChanged=new Runnable(){public void run(){if(!isFinishing()&&!isDestroyed()){updateAdProtectionStatus();load();if(mCallRecordingSettings!=null)mCallRecordingSettings.reload();if(mAppRoot!=null)PatronUi.apply(mAppRoot);}}};

    private final java.util.Set<String> mInitialReads=new java.util.HashSet<String>();

    private void initialReadDone(String source){mInitialReads.remove(source);if(mAppRoot!=null){mAppRoot.jumpDrawablesToCurrentState();mAppRoot.invalidate();}}

    private android.database.ContentObserver mPaletteObserver,mDnaObserver;

    private float mDp;

    private int mBg, mCard, mText, mTextSecondary, mAccent, mOnAccent;

    static void invalidateDoNotAnnoy(){sSwitchValues.remove(DoNotAnnoy.KEY);}
    private static final Map<String,Boolean> sSwitchValues=new java.util.concurrent.ConcurrentHashMap<String,Boolean>();

    private final Map<String, Switch> mSwitches = new LinkedHashMap<String, Switch>();

    private static final ExecutorService IO = new ThreadPoolExecutor(0, 1, 10L,

            TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());

    private final Handler mMain = new Handler(Looper.getMainLooper());

    private TextView mBanner;

    private View mMiscChooseApps;

    private CallRecordingSettings mCallRecordingSettings;

    private StoreBlockerPage mStoreBlockerPage;

    private TextView mStoreBlockedCount,mOtaStatus;

    private boolean mUpdating;

    private int mSaves;

    private android.widget.SeekBar mLabelSlider;

    private android.widget.SeekBar mStyleSlider;

    private final LinearLayout[] mStyleStops=new LinearLayout[3];
    private final TextView[] mStyleNames=new TextView[3];
    private final TextView[] mStyleDescriptions=new TextView[3];
    private TextView mStyleSuperDarkNote;

    private SurfacePreview mStylePreview;

    private boolean mStyleSuperDark,mStyleLoaded;

    private int mSavedStyle=SurfaceStyle.FROSTED;

    private static final String[] STYLE_LABELS={"Blur Off","Stock","Frosted Glass"};

    private TextView mLabelPreview;

    private final TextView[] mLabelStops=new TextView[5];

    private FeaturePreview mFeaturePreview;

    private RestartPill mRestart;

    private ImageView mDeveloperGear;

    private android.animation.ObjectAnimator mGearRotation;

    private PrivacyPageGate mPrivacyGate;

    private View mPrivacyContent;

    private android.os.CancellationSignal mPrivacyAuthSignal;

    private int mPrivacyFailures;

    private final java.util.List<FeaturePreview> mPreviews=new java.util.ArrayList<FeaturePreview>();

    @Override

    protected void onCreate(Bundle savedInstanceState) {

        mDark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)

                == Configuration.UI_MODE_NIGHT_YES;

        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);

        mDp = getResources().getDisplayMetrics().density;

        resolvePalette();

        styleWindow();

        LinearLayout page = new LinearLayout(this);

        page.setOrientation(LinearLayout.VERTICAL);

        page.setPadding(dp(16), dp(8), dp(16), dp(96));

        String pageId = getIntent().getStringExtra("page");
        if("google_apps".equals(pageId))page.setPadding(dp(16),dp(8),dp(16),dp(16));

        if (pageId != null && !pageId.equals("home")) {

            TextView back = text("\u2039  Back", 17, mAccent, true);

            back.setPadding(dp(8),dp(12),dp(8),dp(8));back.setMinimumHeight(dp(48));

            back.setOnClickListener(new View.OnClickListener(){public void onClick(View v){finish();}});

            page.addView(back);

        }

        LinearLayout heading=page;

        if(pageId==null||"home".equals(pageId)){

            heading=new LinearLayout(this);heading.setOrientation(LinearLayout.VERTICAL);heading.setGravity(Gravity.START);
            page.addView(heading,new LinearLayout.LayoutParams(-1,-2));

        }

        if(pageId==null||"home".equals(pageId)){
            heading.addView(new ProfileHeader(this),new LinearLayout.LayoutParams(-1,-2));
        }else{
        TextView title = text(pageTitle(pageId), 36, mText, false);

        title.setPadding(dp(8), dp(32), dp(8), dp(8));

        if(pageId==null||"home".equals(pageId))title.setGravity(Gravity.CENTER_HORIZONTAL);

        if(pageId==null||"home".equals(pageId))heading.addView(new PatronIdentity(this,mText));

        if(pageId==null||"home".equals(pageId)){

            title.setPadding(0,0,0,0);title.setIncludeFontPadding(false);title.setSingleLine(true);

            LinearLayout titleLine=new LinearLayout(this){
                private final android.graphics.Rect inkBounds=new android.graphics.Rect();
                @Override protected void onLayout(boolean changed,int l,int t,int r,int b){
                    super.onLayout(changed,l,t,r,b);if(getChildCount()!=2)return;
                    TextView label=(TextView)getChildAt(0);View logo=getChildAt(1);String value=label.getText().toString();
                    label.getPaint().getTextBounds(value,0,value.length(),inkBounds);
                    float center=label.getTop()+label.getBaseline()+(inkBounds.top+inkBounds.bottom)/2f;
                    logo.setTranslationY(center-logo.getTop()-logo.getHeight()/2f+dp(2));
                }
            };titleLine.setBaselineAligned(false);titleLine.setGravity(Gravity.CENTER_VERTICAL);titleLine.setPadding(dp(8),dp(12),dp(8),dp(8));

            titleLine.addView(title,new LinearLayout.LayoutParams(-2,-2));

            ImageView suiteLogo=new ImageView(this);suiteLogo.setImageDrawable(HomeLinks.suiteMark());suiteLogo.setContentDescription("Pixel Suite logo");

            LinearLayout.LayoutParams logoSize=new LinearLayout.LayoutParams(dp(30),dp(30));logoSize.leftMargin=dp(7);titleLine.addView(suiteLogo,logoSize);

            heading.addView(titleLine);

        }else heading.addView(title);

        TextView subtitle = text(pageSubtitle(pageId), 17, mTextSecondary, false);

        subtitle.setPadding(dp(8), 0, dp(8), dp(12));

        if(pageId==null||"home".equals(pageId)){subtitle.setGravity(Gravity.START);subtitle.setTranslationX(0);subtitle.setPadding(dp(8),0,dp(8),dp(32));}

        if(subtitle.length()>0)heading.addView(subtitle);

        }

        if("changelog".equals(pageId)){

            TextView version = text("  " + versionName() + "  \u00b7  RovianDev  ", 12, mAccent, true);

            GradientDrawable chip = AppSurfaces.card(this,18);

            version.setBackground(chip); version.setPadding(dp(12),dp(8),dp(12),dp(8));

            LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(-2,-2); chipParams.leftMargin=dp(8);chipParams.bottomMargin=dp(20);

            heading.addView(version,chipParams);

        }

        mBanner = banner();

        mBanner.setVisibility(View.GONE);

        page.addView(mBanner);

        buildPage(page);
        consolidateExplanations(page,pageId);
        combineFeaturePreviews(page);
        appendScopeFooter(page,pageId);

        final ScrollView scroll = new SmoothScrollView(this);

        scroll.setFillViewport(true);

        scroll.setClipToPadding(true);

        scroll.addView(page, new FrameLayout.LayoutParams(

                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        scroll.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {

            @Override

            @SuppressWarnings("deprecation")

            public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {

                v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),

                        insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());

                return insets;

            }

        });

        FrameLayout root = new FrameLayout(this);

        root.addView(new SoftScrollEdges(this,scroll,mBg), new FrameLayout.LayoutParams(-1, -1));

        mRestart = new RestartPill(this, root, mAccent, mOnAccent);

        if("privacy".equals(pageId)){

            mPrivacyGate=new PrivacyPageGate();mPrivacyContent=root;

            root.setVisibility(View.INVISIBLE);

            getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);

        }

        setContentView(root);mAppRoot=root;root.setBackgroundColor(mBg);TapFeedback.watch(root);
        scroll.setBackgroundColor(mBg);mPageScroll=scroll;getWindow().setSoftInputMode(0x30);
        int searchClearance=dp(84)+Math.max(0,Math.round((getResources().getConfiguration().fontScale-1)*dp(20)));
        page.setPadding(page.getPaddingLeft(),page.getPaddingTop(),page.getPaddingRight(),searchClearance);
        mSearch=new AppSearch(this,scroll,new AppSearch.Host(){
            public int background(){return mBg;}public int accent(){return mAccent;}public int onAccent(){return mOnAccent;}public int trackOff(){return switchTrackOff();}public int primary(){return mText;}public int secondary(){return mTextSecondary;}
            public boolean inline(AppSearchIndex.Entry e){return mSearchRootReady&&!searchComplex(e.key)&&!locked(e);}
            public boolean updating(){return mUpdating;}
            public boolean locked(AppSearchIndex.Entry e){return !mSearchRootReady||(PatronAccess.protectedKey(e.key)&&!PatronAccess.allowed(SettingsActivity.this,e.key));}
            public boolean checked(String key){Boolean v=sSwitchValues.get(key);return v!=null?v:Feature.on(SettingsActivity.this,key);}
            public void toggle(AppSearchIndex.Entry e,boolean on,Switch sw){
                sSwitchValues.put(e.key,on);Switch original=mSwitches.get(e.key);mUpdating=true;if(original!=null)original.setChecked(on);mUpdating=false;save(e.key,on,sw);
            }
            public void navigate(AppSearchIndex.Entry e){
                Intent next=new Intent(SettingsActivity.this,SettingsActivity.class);next.putExtra("page",e.page);next.putExtra("search_anchor",e.anchor);startActivity(next);
            }
            public void searchVisible(boolean open){if(open)loadSearchStates();}
            public void reveal(View target){revealView(target);}
        });
        root.addView(mSearch,new FrameLayout.LayoutParams(-1,-1));mSearch.palette();
        if(savedInstanceState!=null&&savedInstanceState.getBoolean("search_open"))mSearch.restore(savedInstanceState.getString("search_query",""));
        root.post(()->revealSearchAnchor());

        boolean cached=mChoiceLabels.isEmpty();

        for(String key:mSwitches.keySet())if(!sSwitchValues.containsKey(key))cached=false;

        if((!mSwitches.isEmpty()||!mChoiceLabels.isEmpty())&&!cached)mInitialReads.add("settings");

        if(mCallRecordingSettings!=null&&!mCallRecordingSettings.cached()){mInitialReads.add("recording");mCallRecordingSettings.onInitialLoad(new Runnable(){public void run(){initialReadDone("recording");}});}

        if(mStoreBlockerPage!=null){mInitialReads.add("store");mStoreBlockerPage.onInitialLoad(new Runnable(){public void run(){initialReadDone("store");}});}

        if(mStoreBlockedCount!=null)mInitialReads.add("blocker_summary");

        root.getViewTreeObserver().addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener(){public boolean onPreDraw(){

            if(!mInitialReads.isEmpty())return false;

            root.getViewTreeObserver().removeOnPreDrawListener(this);return true;

        }});

        mPaletteObserver=new android.database.ContentObserver(mMain){@Override public void onChange(boolean self){SurfaceStyle.refresh();refreshAppSurfaces();if(mSearch!=null)mSearch.palette();}};

        getContentResolver().registerContentObserver(android.provider.Settings.Secure.getUriFor(SuperDark.KEY),false,mPaletteObserver);
        getContentResolver().registerContentObserver(android.provider.Settings.Secure.getUriFor(SurfaceStyle.KEY),false,mPaletteObserver);
        getContentResolver().registerContentObserver(android.provider.Settings.Global.getUriFor("disable_window_blurs"),false,mPaletteObserver);

        if(mSwitches.containsKey(DoNotAnnoy.KEY)){
            mDnaObserver=new android.database.ContentObserver(mMain){@Override public void onChange(boolean self){if(!isFinishing()&&!isDestroyed())load();}};
            getContentResolver().registerContentObserver(android.provider.Settings.Secure.getUriFor(DoNotAnnoy.KEY),false,mDnaObserver);
        }
        PatronAccess.watch(mPatronChanged);

        scroll.requestApplyInsets();

    }

    private PatronMembershipRefresh mPatronMembershipRefresh;
    @Override
    protected void onResume() {

        super.onResume();mPageResumed=true;mPageNavigation=false;animatePageIn();refreshAppSurfaces();startDeveloperGear();EsimPurchase.resume(this);
        if(getIntent().hasExtra("esim_code")){final String code=getIntent().getStringExtra("esim_code");getIntent().removeExtra("esim_code");mMain.post(()->EsimPurchase.open(SettingsActivity.this,code));}

        if(getIntent().getBooleanExtra("patron_thanks",false)){getIntent().removeExtra("patron_thanks");mMain.post(new Runnable(){public void run(){PatronPaper.open(SettingsActivity.this,2,null);}});}

        else if(getIntent().hasExtra("patron_error")){final String error=getIntent().getStringExtra("patron_error");getIntent().removeExtra("patron_error");mMain.post(new Runnable(){public void run(){PatronPaper.open(SettingsActivity.this,0,error);}});}

        else if(PatronAccess.claimPending(this))claimSignIn(0);

        else if(PatronAccess.prefs(this).getBoolean("join_return",false)&&!PatronAccess.loggedIn(this)){PatronAccess.prefs(this).edit().putBoolean("join_return",false).apply();mMain.post(new Runnable(){public void run(){PatronPaper.open(SettingsActivity.this,1,null);}});}

        if(mPatronMembershipRefresh==null)mPatronMembershipRefresh=new PatronMembershipRefresh(this);
        boolean returnedFromJoin=PatronAccess.prefs(this).getBoolean("join_return",false);
        if(returnedFromJoin)PatronAccess.prefs(this).edit().putBoolean("join_return",false).apply();
        mPatronMembershipRefresh.resume(returnedFromJoin);

        if(mPrivacyGate!=null){mPrivacyGate.resume();if(!mPrivacyGate.isOpen()){mPrivacyContent.setVisibility(View.INVISIBLE);authenticatePrivacy();return;}}

        resumeSettingsContent();

    }

    private void resumeSettingsContent(){
        if(getIntent().getBooleanExtra("request_dna_permissions",false)){
            getIntent().removeExtra("request_dna_permissions");mMain.post(new Runnable(){public void run(){if(!isFinishing()&&!isDestroyed())DoNotAnnoyPermissions.request(SettingsActivity.this);}});
        }

        if (mRestart != null) mRestart.refresh();

        final boolean firstEntitlement=!PatronAccess.initialized();

        if(firstEntitlement)mInitialReads.add("entitlement");

        PatronAccess.resume(getApplicationContext(),firstEntitlement?new Runnable(){public void run(){mInitialReads.add("settings");mPatronChanged.run();initialReadDone("entitlement");}}:null);

        load();if(mIconPackPage!=null)mIconPackPage.refresh();if(mCallRecordingSettings!=null)mCallRecordingSettings.reload();if(mStoreBlockerPage!=null)mStoreBlockerPage.resume();refreshBlockerSummary();

        ReleaseUpdates.schedule(SettingsActivity.this); ReleaseUpdates.check(SettingsActivity.this,false,null);

        for(FeaturePreview preview:mPreviews)preview.setHostActive(true);

        if(!sPropChecked&&Feature.on(this,SuperDark.KEY)){sPropChecked=true;

            IO.execute(new Runnable(){public void run(){Root.run("[ \"$(getprop "+SuperDark.PROP+")\" = 1 ] || setprop "+SuperDark.PROP+" 1");}});}

    }

    @Override protected void onPause(){
        mPageResumed=false;
        if(mPatronMembershipRefresh!=null)mPatronMembershipRefresh.pause();

        stopDeveloperGear();

        if(mStoreBlockerPage!=null)mStoreBlockerPage.pause();

        if(mPrivacyGate!=null){mPrivacyGate.pause();mPrivacyContent.setVisibility(View.INVISIBLE);}

        for(FeaturePreview preview:mPreviews)preview.setHostActive(false);super.onPause();

    }

    @Override protected void onStop(){

        if(mPrivacyGate!=null){mPrivacyGate.stop();if(mPrivacyAuthSignal!=null)mPrivacyAuthSignal.cancel();}

        super.onStop();

    }

    private void authenticatePrivacy(){

        final int attempt=mPrivacyGate.begin();if(attempt==0)return;

        android.app.KeyguardManager lock=(android.app.KeyguardManager)getSystemService(KEYGUARD_SERVICE);

        if(lock==null||!lock.isDeviceSecure()){

            AppToast.makeText(this,"Set a phone screen lock first",Toast.LENGTH_LONG).show();finish();return;

        }

        mPrivacyFailures=0;

        try{

            android.hardware.biometrics.BiometricPrompt.Builder b=privacyPrompt();

            b.setAllowedAuthenticators(0x00ff);b.setConfirmationRequired(false);

            b.setNegativeButton("Pin",privacyExecutor(),new DialogInterface.OnClickListener(){public void onClick(DialogInterface dialog,int which){privacyCredentialLater(attempt);}});

            mPrivacyAuthSignal=new android.os.CancellationSignal();

            b.build().authenticate(mPrivacyAuthSignal,privacyExecutor(),new android.hardware.biometrics.BiometricPrompt.AuthenticationCallback(){

                @Override public void onAuthenticationSucceeded(android.hardware.biometrics.BiometricPrompt.AuthenticationResult result){privacyAuthenticated(attempt);}

                @Override public void onAuthenticationFailed(){if(mPrivacyGate.isPending(attempt)&&++mPrivacyFailures>=3)privacyCredentialLater(attempt);}

                @Override public void onAuthenticationError(int code,CharSequence message){

                    if(code==13||code==7||code==9||code==11||code==12||code==1)privacyCredentialLater(attempt);

                    else privacyAuthFailed(attempt);

                }

            });

        }catch(Throwable error){privacyCredentialLater(attempt);}

    }

    private android.hardware.biometrics.BiometricPrompt.Builder privacyPrompt(){

        android.hardware.biometrics.BiometricPrompt.Builder b=new android.hardware.biometrics.BiometricPrompt.Builder(this);

        b.setTitle("Privacy & Security");b.setDescription("Confirm it's you to change privacy settings");return b;

    }

    private java.util.concurrent.Executor privacyExecutor(){return new java.util.concurrent.Executor(){public void execute(Runnable r){mMain.post(r);}};}

    private void privacyCredentialLater(int attempt){

        final int next=mPrivacyGate.replace(attempt);

        if(next==0){mPrivacyGate.error(attempt);return;}

        if(mPrivacyAuthSignal!=null)mPrivacyAuthSignal.cancel();

        mMain.postDelayed(new Runnable(){public void run(){

            if(isFinishing()||isDestroyed()||!mPrivacyGate.isPending(next)){mPrivacyGate.error(next);return;}

            try{

                android.hardware.biometrics.BiometricPrompt.Builder b=privacyPrompt();b.setAllowedAuthenticators(0x8000);

                mPrivacyAuthSignal=new android.os.CancellationSignal();

                b.build().authenticate(mPrivacyAuthSignal,privacyExecutor(),new android.hardware.biometrics.BiometricPrompt.AuthenticationCallback(){

                    @Override public void onAuthenticationSucceeded(android.hardware.biometrics.BiometricPrompt.AuthenticationResult result){privacyAuthenticated(next);}

                    @Override public void onAuthenticationError(int code,CharSequence message){privacyAuthFailed(next);}

                });

            }catch(Throwable error){privacyAuthFailed(next);}

        }},250);

    }

    private void privacyAuthenticated(int attempt){

        if(isFinishing()||isDestroyed()||!mPrivacyGate.success(attempt))return;

        mPrivacyContent.setVisibility(View.VISIBLE);resumeSettingsContent();

    }

    private void privacyAuthFailed(int attempt){if(mPrivacyGate.error(attempt)&&!isFinishing())finish();}

    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] grants){
        super.onRequestPermissionsResult(code,permissions,grants);
        if(code==825){
            boolean allowed=grants.length>0&&grants[0]==android.content.pm.PackageManager.PERMISSION_GRANTED;
            Switch sw=mSwitches.get("pixelsuite_dna_watch");
            if(sw!=null){mUpdating=true;sSwitchValues.put("pixelsuite_dna_watch",allowed);sw.setChecked(allowed);mUpdating=false;save("pixelsuite_dna_watch",allowed,sw);}
            if(allowed)WatchCell.schedule(this);
        }
    }
    @Override protected void onDestroy(){if(mSearch!=null)mSearch.dispose();PatronAccess.unwatch(mPatronChanged);if(mIconPackPage!=null)mIconPackPage.dispose();if(mStoreBlockerPage!=null)mStoreBlockerPage.dispose();stopDeveloperGear();if(mPaletteObserver!=null)getContentResolver().unregisterContentObserver(mPaletteObserver);if(mDnaObserver!=null)getContentResolver().unregisterContentObserver(mDnaObserver);if(mPrivacyGate!=null){mPrivacyGate.stop();if(mPrivacyAuthSignal!=null)mPrivacyAuthSignal.cancel();}if(mRestart!=null)mRestart.dispose();for(AlertDialog dialog:new java.util.ArrayList<AlertDialog>(mSettingsDialogs))dialog.dismiss();for(FeaturePreview preview:mPreviews)preview.dispose();super.onDestroy();}

    private static boolean sPropChecked;

    private static final class FeaturePage {

        final String group,key,icon,title,summary;

        FeaturePage(String group,String key,String icon,String title,String summary){this.group=group;this.key=key;this.icon=icon;this.title=title;this.summary=summary;}

    }

    private static final FeaturePage[] PAGES={

        new FeaturePage("buttons",Feature.FLASHLIGHT,"ic_ps_flashlight","Power button flashlight","Double-press the power button to turn the flashlight on or off."),

        new FeaturePage("buttons",Feature.VOLDOWN_CAMERA,"ic_ps_camera","Volume-down camera","Double-press volume down while the screen is off to open the camera. Doesn't work during calls or while media is playing."),

        new FeaturePage("gestures",Feature.THREE_FINGER,"ic_ps_threefinger","Three-finger screenshot","Swipe down with three fingers to take a screenshot."),

        new FeaturePage("gestures",ScreenRecordTidy.KEY,"ic_ps_screen_record","Screen recording sheet","Moves X and Start below the recording options for a cleaner screen. Works with System UI in English."),

        new FeaturePage("gestures",Feature.SCREENSHOT_FREE_CROP,"ic_ps_crop_free","Free screenshot crop","Crop screenshots to any size in Pixel Studio, even a thin strip or a single line of text."),

        new FeaturePage("doodles",Feature.SCREENSHOT_DOODLES,"ic_ps_studio_tools","Pixel Studio","Draw shapes and arrows. Tap the shape or rainbow for more options; drag the menu to move it."),

        new FeaturePage("doodles",Feature.PHOTOS_DOODLES,"ic_ps_photos_doodles","Google Photos","Open a photo → Edit → Doodles, beside Crop. Saved shapes stay editable; Revert removes them. Doodles runs offline; Photos backup stays unchanged."),

        new FeaturePage("gestures",Feature.DOUBLE_TAP_SLEEP,"ic_ps_sleep","Double tap to sleep","Double-tap an empty spot on the home screen to lock your phone."),

        new FeaturePage("gestures",Feature.TAP_CHECK,"ic_ps_tapwake_toggle","Tap to wake","Wake your phone with a single or double tap, with pocket protection. Double-tap the lock screen to turn it off again."),

        new FeaturePage("launcher",LauncherFeatures.GRID,"ic_ps_folder_grid","Folder grid","Folders grow with the number of apps inside. Pick a grid from each folder's three-dot menu."),

        new FeaturePage("drawer",LauncherFeatures.DRAWER,"ic_ps_drawer_folder","App drawer folders","Place folders anywhere among your apps, with their own grids and sorting."),

        new FeaturePage("drawer",LauncherFeatures.DRAG_PILL,"ic_ps_home_drop","Drag to home screen","Hold an app in the drawer and drag it onto Add to home screen. Dragging sideways or down keeps you in the drawer."),

        new FeaturePage("themes",LauncherFeatures.ICONS,"ic_ps_change_icon","Change icons","Hold an app and choose Change icon. Move and zoom your image, then confirm. It keeps your icon shape."),

        new FeaturePage("gestures",FolderDrag.TWO_FINGER,"ic_ps_two_finger","Two-finger drag","Hold a drawer folder with one finger and swipe with another to scroll. For apps, use another finger to turn home or folder pages, or scroll the drawer."),

        new FeaturePage("gestures",MultiDrag.KEY,"ic_ps_multi_drag","Move several apps","Start dragging an app, then tap more apps with another finger. Drop them all on the home screen or into a folder."),

        new FeaturePage("launcher",LauncherFeatures.RENAME,"ic_ps_rename","Rename apps","Hold an app and choose Rename. The new name shows right away on the home screen, in folders, in the drawer and in search."),

        new FeaturePage("launcher",LauncherFeatures.LABEL_SIZE,"ic_ps_label_size","Icon label size","Five text sizes for app names on the home screen, in the drawer and on closed folders. Smaller text fits longer names. Icons stay the same size."),

        new FeaturePage("launcher","clear_badge","ic_ps_clear_badge","Clear badge","Hold an app or folder with a notification dot and choose Clear badge."),

        new FeaturePage("recents",Feature.CLEAR_ALL,"ic_ps_clearall","Clear all in recents","Clear all is always next to Select and Screenshot in recents, so you don't have to scroll to the far left."),

        new FeaturePage("recents",RecentsLock.KEY,"ic_ps_recents_lock","Lock apps in recents","Choose Lock from an app's recents menu: its card stays when you swipe it or use Clear all. Tap its lock badge to unlock it."),

        new FeaturePage("drawer",LauncherFeatures.HIDE_SHORTCUTS,"ic_ps_shortcuts","Hide app shortcuts","Hides shortcuts that apps add to their drawer menu, such as View orders. The launcher's own options and Pixel Suite's stay."),

        new FeaturePage("themes",SuperDark.KEY,"ic_ps_super_dark","Super Dark-Mode","Pure black backgrounds and darker menus in dark theme. Off by default."),

        new FeaturePage("files",Feature.FILES_SORT,"ic_ps_files","Newest first","Shows folders first and the newest files at the top, in Files by Google and Android's file picker."),
        new FeaturePage("gestures",NavButtons.KEY,"ic_ps_navigation","Navigation buttons","Choose Back, Home or Recent beside the gesture bar. Hold Home for Circle to Search or Gemini.")

    };

    private final Map<String,TextView> mChoiceLabels=new LinkedHashMap<String,TextView>();

    private final Map<String,Integer> mChoiceOffsets=new LinkedHashMap<String,Integer>();

    private final Map<String,ImageView> mChoiceIcons=new LinkedHashMap<String,ImageView>();

    private final Map<String,String[]> mChoiceItems=new LinkedHashMap<String,String[]>();

    private FeaturePage feature(String id){for(FeaturePage f:PAGES)if(f.key.equals(id))return f;return null;}

    private String pageTitle(String id){

        FeaturePage f=feature(id);if(f!=null)return f.title;

        if("buttons".equals(id))return "Buttons";if("gestures".equals(id))return "Gestures";

        if("doodles".equals(id))return "Doodles";

        if("drawer".equals(id))return "App drawer";if("launcher".equals(id))return "Pixel Launcher";if("files".equals(id))return "Files";if("themes".equals(id))return "Themes";

        if("privacy".equals(id))return "Privacy & Security";

        if("do_not_annoy".equals(id))return "Do Not Annoy";
        if("share_menu".equals(id))return "Share menu";

        if("recents".equals(id))return "Recent Apps";

        if("blockers".equals(id))return "OTA & Store blocker";

        if("store_blocker".equals(id))return "Play Store blocker";

        if("ota_blocker".equals(id))return "OTA blocker";

        if("google_apps".equals(id))return "Google apps";

        if("google_dialer".equals(id))return "Google dialer";

        if("chrome".equals(id))return "Chrome";if("google_news".equals(id))return "Google news";
        if("google_app".equals(id))return "Google app";

        if("pixel_watch".equals(id))return "Pixel watch";

        if("miscellaneous".equals(id))return "Miscellaneous";

        if("changelog".equals(id))return "Changelog";

        if("updates".equals(id))return "Updates";if("about".equals(id))return "About and support";return "Pixel Suite";

    }

    private String pageSubtitle(String id){

        if(feature(id)!=null)return "Make it work your way.";

        if("buttons".equals(id))return "Quick actions from your phone's buttons.";

        if("gestures".equals(id))return "Capture, sleep, wake and app dragging.";

        if("doodles".equals(id))return "Draw on screenshots and photos.";

        if("drawer".equals(id))return "Folders, dragging and app shortcuts.";if("launcher".equals(id))return "Folders, names and recents.";

        if("files".equals(id))return "A better starting view for your files.";if("themes".equals(id))return "System colors, your way.";

        if("privacy".equals(id))return "Protected with your fingerprint or PIN.";

        if("do_not_annoy".equals(id))return "Stay connected. Keep incoming calls quiet.";
        if("share_menu".equals(id))return "Pinned and hidden sharing apps.";

        if("recents".equals(id))return "Keep what matters. Clear the rest.";

        if("blockers".equals(id))return "Play Store updates and system updates";

        if("store_blocker".equals(id))return "Choose which apps stay on their current version.";

        if("ota_blocker".equals(id))return "System update controls.";

        if("google_apps".equals(id))return "Tools for your Google apps.";

        if("google_dialer".equals(id))return "Call recording";

        if("chrome".equals(id))return "Fewer ads and empty ad boxes.";if("google_news".equals(id))return "Cleaner news browsing.";
        if("google_app".equals(id))return "Hide ads in Google feed";

        if("pixel_watch".equals(id))return "eSIM setup with a QR code";

        if("miscellaneous".equals(id))return "Small fixes, simple controls.";

        if("changelog".equals(id))return "What's new";

        if("updates".equals(id))return "";if("about".equals(id))return "Made by RovianDev";return HomeLayout.TAGLINE;

    }

    private boolean mPageResumed,mPageNavigation,mPageClosing;
    private boolean mClaiming;
    private void claimSignIn(final int attempt){
        if(mClaiming&&attempt==0)return;mClaiming=true;
        PatronAccess.claim(this,new java.util.function.Consumer<String>(){public void accept(String error){
            mClaiming=false;if(isFinishing()||isDestroyed())return;
            if(error==null&&PatronAccess.unlocked())PatronPaper.open(SettingsActivity.this,2,null);
            else PatronPaper.open(SettingsActivity.this,0,error==null?PatronAccess.membershipMessage(SettingsActivity.this):error);
        }},new Runnable(){public void run(){
            if(isFinishing()||isDestroyed()||!mPageResumed||!PatronAccess.claimPending(SettingsActivity.this)||attempt>=45){mClaiming=false;return;}
            mMain.postDelayed(new Runnable(){public void run(){if(mPageResumed&&PatronAccess.claimPending(SettingsActivity.this))claimSignIn(attempt+1);else mClaiming=false;}},2000);
        }});
    }
    @Override public void startActivity(Intent intent,Bundle options){
        boolean page=intent.getComponent()!=null&&SettingsActivity.class.getName().equals(intent.getComponent().getClassName());
        mPageNavigation=page&&!"privacy".equals(intent.getStringExtra("page"));
        super.startActivity(intent,options);
        if(page)overridePendingTransition(0,0);
    }
    private void animatePageIn(){
        if(mPageScroll==null)return;
        mPageScroll.animate().cancel();
        if(!AppMotion.enabled(this)){mPageScroll.setAlpha(1);mPageScroll.setTranslationY(0);return;}
        mPageScroll.setAlpha(0);mPageScroll.setTranslationY(dp(20));
        mPageScroll.getViewTreeObserver().addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener(){
            public boolean onPreDraw(){
                if(!mInitialReads.isEmpty())return true;
                mPageScroll.getViewTreeObserver().removeOnPreDrawListener(this);
                if(!mPageClosing)mPageScroll.animate().alpha(1).translationY(0).setDuration(240).setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
                return true;
            }
        });
    }
    private void finishPage(){super.finish();overridePendingTransition(0,0);}
    @Override public void finish(){
        if(mPageClosing)return;
        mPageNavigation=true;mPageClosing=true;
        if(mPageScroll!=null&&AppMotion.enabled(this)&&(mPrivacyGate==null||mPrivacyGate.isOpen())){
            mPageScroll.animate().cancel();
            mPageScroll.animate().alpha(0).translationY(dp(20)).setDuration(160).setInterpolator(new android.view.animation.AccelerateInterpolator()).withEndAction(()->finishPage()).start();
        }else finishPage();
    }
    private void openPage(String id){startActivity(new Intent(this,SettingsActivity.class).putExtra("page",id));}

    private void buildPage(LinearLayout page){

        String id=getIntent().getStringExtra("page");FeaturePage f=feature(id);
        if(NavButtons.KEY.equals(id)){buildNavigationButtons(page);return;}

        if(f!=null){
            if("clear_badge".equals(f.key)){
                section(page,"How it works");
                explanation(page,f.summary);
                explanation(page,"Dismissable notifications are cleared. Notifications that cannot be dismissed remain, but stop showing a dot.");
                featurePreview(page,f);return;
            }

            LinearLayout controls=group(page);row(controls,f.key,Feature.TAP_CHECK.equals(f.key)?"ic_ps_tapwake_toggle":f.icon,mAccent,(Feature.SCREENSHOT_DOODLES.equals(f.key)||Feature.PHOTOS_DOODLES.equals(f.key)?"Use Doodles":LauncherFeatures.HIDE_SHORTCUTS.equals(f.key)?"Hide app shortcuts":LauncherFeatures.ICONS.equals(f.key)?"Use custom app icons":LauncherFeatures.LABEL_SIZE.equals(f.key)?"Customize icon labels":SuperDark.KEY.equals(f.key)?"Use Super Dark-Mode":"Use "+f.title.toLowerCase(java.util.Locale.ROOT)),"Turn this feature on or off.");if(LauncherFeatures.ICONS.equals(f.key))row(controls,DrawerIconColors.KEY,"ic_ps_drawer",mAccent,"Colorful icons in the app drawer (Stock)","With themed icons on, the app drawer and its folders keep each app's own colors. The home screen stays themed.");finishGroup(controls);

            section(page,"How it works");

            if(LauncherFeatures.DRAWER.equals(f.key)){

                explanation(page,"Hold a drawer folder and drag it anywhere among your apps. Apps glide aside, and the folder lands with a gentle bounce.");
                explanation(page,"Touch Move to bottom while holding a folder to start scrolling. Push deeper to scroll faster; use the top edge to scroll up. You can also swipe with a second finger while holding the folder.");
                explanation(page,"Hold an app or folder with a notification dot and choose Clear badge. Dismissable notifications are cleared; others remain but stop showing a dot.");
                explanation(page,"Drag an app onto another to make a folder. Use Add apps to pick more.");

                extraPreview(page,"drawer_folder_move","Move folders between apps");
                extraPreview(page,"drawer_move_bottom","Move to bottom and scroll");
                featurePreview(page,f);

                section(page,"Sort from the three-dot menu");

                explanation(page,"Three-dot menu → Sort: Auto sorts apps A to Z; Custom lets you arrange them yourself.");

                extraPreview(page,"folder_sort","Sorting and custom order");

                explanation(page,"A folder can hold a single app and follows your icon shape. Hold a folder to rename, sort or remove it; removing a folder doesn't uninstall its apps.");

            }else{

                explanation(page,f.summary);

                if(!LauncherFeatures.LABEL_SIZE.equals(f.key))featurePreview(page,f);

            }

            if(SuperDark.KEY.equals(f.key)){

                section(page,"Dark theme and schedules");

                explanation(page,"Follows your dark theme schedule. In light theme, normal colors and blur come back.");

            }

            if(LauncherFeatures.GRID.equals(f.key))extraPreview(page,"folder_sort","Folder sorting");

            if(LauncherFeatures.LABEL_SIZE.equals(f.key)){

                section(page,"Text size");LinearLayout options=group(page);labelSize(options);finishGroup(options);

                explanation(page,"Updates app names right away. Uses your system font and Bold text setting. Names inside open folders and in search results keep their usual size.");

            }

            if(Feature.TAP_CHECK.equals(f.key)){

                section(page,"Wake the screen");LinearLayout wake=group(page);

                choice(wake,Feature.DOUBLE_TAP_WAKE,"Wake gesture",new String[]{"Single tap","Double tap"});finishGroup(wake);

                explanation(page,"If a double tap doesn't register, tap once more within three seconds.");

                section(page,"Lock-screen taps");LinearLayout lock=group(page);

                choice(lock,Feature.LOCK_SHORTCUTS,"Lock-screen behavior",new String[]{"Pixel Suite","Stock"});finishGroup(lock);

                explanation(page,"Pixel Suite: double-tapping a lock-screen card turns the screen off without opening the card or vibrating. Stock: keeps Google's normal behavior.");

            }

            if(LauncherFeatures.GRID.equals(f.key))explanation(page,"Grid choices appear once a folder has 3 or more apps: 3×3, 4×4 from 10 apps, and 5×5 from 17 apps. With fewer apps, the folder uses a smaller grid; your choice comes back as the folder fills up.");

            if(LauncherFeatures.RENAME.equals(f.key))explanation(page,"Reset brings back the original name.");

            if(LauncherFeatures.RENAME.equals(f.key))explanation(page,"Changes show right away on the home screen, in the drawer, in folders and in the Google search bar. Apps in your work profile and Private Space keep their names.");

            if(Feature.CLEAR_ALL.equals(f.key))explanation(page,"Changing this option restarts Pixel Launcher.");

            if(RecentsLock.KEY.equals(f.key))explanation(page,"Locks survive reboots. A recents lock keeps the card; Android can still close the app in the background.");

            if(FolderDrag.TWO_FINGER.equals(f.key))explanation(page,"For drawer folders, you can also touch Move to bottom and push deeper to scroll faster, or drag toward the top edge to scroll up.");

            if(MultiDrag.KEY.equals(f.key))explanation(page,"Pick apps from the same folder or screen. Removing them from the home screen doesn't uninstall them.");

            if(LauncherFeatures.ICONS.equals(f.key))explanation(page,"Changes show right away on the home screen, in the drawer, in folders and in the Google search bar. Tap the reset arrow to bring back the original icon. Apps in Private Space aren't included.");

            return;

        }

        if("do_not_annoy".equals(id)){
            try{getPackageManager().getPackageInfo("com.android.imsstack",0);mDnaPixelStack=true;}catch(android.content.pm.PackageManager.NameNotFoundException absent){mDnaPixelStack=false;}
            LinearLayout controls=group(page);
            row(controls,DoNotAnnoy.KEY,"ic_ps_do_not_annoy",mAccent,"Do Not Annoy","Silences calls. Keeps your phone\nconnected.  (Quick Settings>Add tile)");
            row(controls,"pixelsuite_dna_watch","ic_ps_watch",mAccent,"Pixel Watch with LTE","Only if your Pixel Watch has its own mobile plan: calls can ring on the watch too. Turn this on to switch the watch's mobile network off with Do Not Annoy (and back on after). Needs the Pixel Suite watch app.");finishGroup(controls);
            explanation(page,mDnaPixelStack?"Callers hear that you're unavailable: a switched-off message or your voicemail, depending on your carrier. Your phone stays connected, using 4G/5G only while this is on. You'll get a notification with a Call button for each missed call. Emergency calls always come through.":"Calls are declined before your phone rings. Callers hear your voicemail or a busy tone, depending on your carrier. You'll get a notification with a Call button for each missed call. Emergency calls always come through.");
            explanation(page,"No Pixel Watch, or a watch without LTE? Leave this off; you don't need the watch app.");
            explanation(page,"To sync (Do Not Annoy) with an eSIM-enabled Pixel Watch, you'll need the Pixel Suite Watch app. Contact the developer on Telegram for the APK.");
            extraPreview(page,mDnaPixelStack?"209_do_not_annoy":"209_do_not_annoy_decline","Keep calls quiet and stay connected");return;
        }
        if("blockers".equals(id)){

            LinearLayout controls=group(page);

            navigation(controls,"Play Store blocker","Loading…","ic_ps_store_blocker",new Runnable(){public void run(){openPage("store_blocker");}});

            mStoreBlockedCount=navigationSummary(controls);

            navigation(controls,"OTA blocker","Off","ic_ps_ota_blocker",new Runnable(){public void run(){openPage("ota_blocker");}});

            mOtaStatus=navigationSummary(controls);finishGroup(controls);return;

        }

        if("store_blocker".equals(id)){

            LinearLayout controls=group(page);

            row(controls,StoreBlocker.UNLOCK_KEY,"ic_ps_store_blocker",mAccent,"Unlock OEM apps","Unlocking and updating these core packages directly from the Play Store may break compatibility or custom Pixel Suite hooks. You proceed at your own risk. Core packages are tested and maintained through official Pixel Suite releases.");finishGroup(controls);

            navigation(controls=group(page),"Select apps","Loading…","ic_ps_drawer",new Runnable(){public void run(){mStoreBlockerPage.show();}});

            TextView selectedCount=navigationSummary(controls);finishGroup(controls);

            mStoreBlockerPage=new StoreBlockerPage(this,IO,mText,mTextSecondary,mAccent,mOnAccent,mSwitches.get(StoreBlocker.UNLOCK_KEY),selectedCount);

            extraPreview(page,"209_store","Hold back app updates");

            return;

        }

        if("ota_blocker".equals(id)){

            LinearLayout controls=group(page);

            row(controls,OtaBlocker.KEY,"ic_ps_update",mAccent,"OTA blocker","Stops system update checks and downloads, and removes Software updates from Settings.");

            row(controls,OtaBlocker.RESET_KEY,"ic_ps_privacy",mAccent,"Lock factory reset","Erase all data is greyed out in Settings while OTA blocker is on.");finishGroup(controls);

            updateMiscPicker();explanation(page,"Takes effect at once. Reopen Settings to see the change.");

            extraPreview(page,"209_ota","System updates and factory reset");

            return;

        }

        if("google_apps".equals(id)){

            LinearLayout apps=group(page);

            appNavigation(apps,"Google dialer","Call recording","com.google.android.dialer","ic_ps_call_recording","google_dialer");

            appNavigation(apps,"Google app","Hide ads in Google feed",GoogleFeedAds.PACKAGE,"ic_ps_google_apps","google_app");

            appNavigation(apps,"Chrome","Ad blocking and empty ad-box cleanup","com.android.chrome","ic_ps_chrome","chrome");
            appNavigation(apps,"Google news","Ad blocking for news browsing","com.google.android.apps.magazines","ic_ps_news","google_news");
            appNavigation(apps,"Pixel watch","eSIM setup with a QR code",WatchEsim.PACKAGE,"ic_ps_google_apps","pixel_watch");

            finishGroup(apps);

            AdBlockCard protection=new AdBlockCard(this,mText,mTextSecondary);LinearLayout.LayoutParams protectionLayout=new LinearLayout.LayoutParams(-1,-2);protectionLayout.topMargin=dp(8);protectionLayout.bottomMargin=0;page.addView(protection,protectionLayout);return;

        }

        if("chrome".equals(id)||"google_news".equals(id)){
            boolean news="google_news".equals(id);
            explanation(page,news?"Uses the system-wide ad blocker for news browsing. Articles opened in Chrome also get empty ad-box cleanup.":"Blocks ad domains and removes supported empty ad boxes while you browse. Uses the always-on ad blocker.");
            mAdProtectionStatus=text("",15,mText,true);mAdProtectionStatus.setPadding(dp(18),dp(16),dp(18),dp(16));mAdProtectionStatus.setBackground(AppSurfaces.card(this,20));
            LinearLayout.LayoutParams protectionStatus=new LinearLayout.LayoutParams(-1,-2);protectionStatus.bottomMargin=dp(12);page.addView(mAdProtectionStatus,protectionStatus);updateAdProtectionStatus();
            mAdProtectionStatus.setOnClickListener(new View.OnClickListener(){public void onClick(View v){if(!PatronAccess.unlocked())PatronPaper.open(SettingsActivity.this,0,null);}});updateAdProtectionStatus();
            extraPreview(page,news?"209_google_news":"209_chrome",news?"Cleaner news articles":"Browse without empty ad boxes");return;
        }
        if("pixel_watch".equals(id)){

            LinearLayout controls=group(page);

            row(controls,WatchEsim.KEY,"ic_ps_google_apps",mAccent,"eSIM setup with a QR code","Adds Scan QR code to Mobile network when your carrier isn't listed");

            try{FrameLayout badge=(FrameLayout)((LinearLayout)controls.getChildAt(0)).getChildAt(0);((ImageView)badge.getChildAt(0)).setImageDrawable(AppBrandIcons.load(this,WatchEsim.PACKAGE,sectionInk("ic_ps_google_apps")));}catch(Exception missing){}

            finishGroup(controls);extraPreview(page,"209_watch","Set up eSIM with a QR code");return;

        }

        if("google_app".equals(id)){

            LinearLayout controls=group(page);

            row(controls,GoogleFeedAds.KEY,"ic_ps_google_apps",mAccent,"Hide ads in Google feed","Removes sponsored cards from your feed, frame and space included");

            try{LinearLayout toggle=(LinearLayout)controls.getChildAt(0);FrameLayout badge=(FrameLayout)toggle.getChildAt(0);((ImageView)badge.getChildAt(0)).setImageDrawable(googleIcon(sectionInk("ic_ps_google_apps")));}catch(Exception unavailable){}

            finishGroup(controls);extraPreview(page,"209_feed","A feed without sponsored gaps");return;

        }

        if("google_dialer".equals(id)){

            LinearLayout controls=group(page);

            row(controls,CallRecording.KEY,"ic_ps_call_recording",mAccent,"Call recording","Record calls in the native Phone app, in any country, without the announcement.");

            navigation(controls,"Recording mode","Automatic: every call is recorded","ic_ps_recording_mode",new Runnable(){public void run(){if(mCallRecordingSettings!=null)mCallRecordingSettings.chooseMode();}});

            View modeRow=controls.getChildAt(controls.getChildCount()-1);

            TextView modeSummary=(TextView)((LinearLayout)((LinearLayout)modeRow).getChildAt(1)).getChildAt(1);

            navigation(controls,"Recordings folder",CallRecordingSettings.DEFAULT,"ic_ps_files_category",new Runnable(){public void run(){if(mCallRecordingSettings!=null)mCallRecordingSettings.choose();}});

            View folderRow=controls.getChildAt(controls.getChildCount()-1);

            TextView folderSummary=(TextView)((LinearLayout)((LinearLayout)folderRow).getChildAt(1)).getChildAt(1);

            mCallRecordingSettings=new CallRecordingSettings(this,IO,folderRow,folderSummary,new CallRecordingSettings.Presenter(){public void show(AlertDialog dialog){showSettingsDialog(dialog);}});

            mCallRecordingSettings.modeRow(modeRow,modeSummary);mCallRecordingSettings.enabled(Boolean.TRUE.equals(sSwitchValues.get(CallRecording.KEY)));finishGroup(controls);

            explanation(page,"Call recording may be restricted by your country’s laws. Obtain any required consent before recording. You are responsible for lawful use. RovianDev is not responsible for illegal use.");

            extraPreview(page,"209_recording","Record and save calls");

            return;

        }

        if("share_menu".equals(id)){

            LinearLayout controls=group(page);

            row(controls,ShareSheet.KEY,"ic_ps_share_sheet",mAccent,"Share sheet tools","Drag apps to pin and reorder them. Organize sharing apps and contacts.");finishGroup(controls);

            section(page,"How it works");
            explanation(page,"• Arrange: drag an app into the top row to pin it, then drag pinned apps to reorder. Apps below keep your chosen order; new apps join at the bottom.\n\n• Hold an app for Pin / Hide. Its shortcuts appear between them; tap the arrow below an app to open its shortcuts directly. Pinning or hiding keeps the app together.\n\n• Hide: hold an app or contact, or use Edit to select several apps. Hidden apps are grouped with their shortcuts and icons.\n\n• Restore: open Hidden apps or Hidden contacts, then hold an item to unhide it. Tap an item to share.\n\n• Contacts row: use Edit, then minus to hide or plus to show.");

            extraPreview(page,"209_share_combined","Pin, reorder, hide and restore sharing apps and contacts");

            return;

        }

        if("miscellaneous".equals(id)){

            LinearLayout controls=group(page);

            row(controls,ScreenshotWarm.KEY,"ic_ps_fast_screenshot",mAccent,"Reduce screenshot delay","Keeps the screenshot service ready, so screenshots appear instantly. Uses a little extra memory.");

            row(controls,MiscSystem.SECURE_KEY,"ic_ps_secure_capture",mAccent,"Allow screenshots of secure screens","Take screenshots and recordings in apps that normally block them. Apps aren't told.\nIf you use the DisableFlagSecure module, remove it before turning this on.");

            row(controls,MiscSystem.A11Y_KEY,"ic_ps_accessibility",mAccent,"Hide accessibility from apps","Apps you choose can't see your accessibility services.\nIf you use the DoNotTryAccessibility module, remove it before turning this on.");

            navigation(controls,"Choose apps","Choose which apps can't see your accessibility services","ic_ps_drawer",new Runnable(){public void run(){

                MiscAppPicker.show(SettingsActivity.this,IO,mText,mTextSecondary,mAccent,new MiscAppPicker.Presenter(){public void show(AlertDialog dialog){showSettingsDialog(dialog);}});

            }});

            mMiscChooseApps=controls.getChildAt(controls.getChildCount()-1);updateMiscPicker();

            row(controls,KeyboardBarTweak.KEY,"ic_ps_keyboard",mAccent,"Keyboard bar tweak","Hides the globe and moves the dismiss arrow to the right, in the bar under your keyboard. Restarts your keyboard.");

            row(controls,MiscSystem.DEV_KEY,"ic_ps_developer",mAccent,"Hide Developer Options","Apps can't tell developer options or USB debugging are on, so apps that refuse to open with them on (like some banking apps) work normally.\nTap the gear to open Developer options.");

            row(controls,RefreshRate90.KEY,"ic_ps_refresh_rate",mAccent,"Refresh rate slider","Adds a 60 / 120 Hz slider under Smooth display (Settings > Display & touch)");

            row(controls,ShowFps.KEY,"ic_ps_fps",mAccent,"Show FPS","Live frame counter at the top right");

            finishGroup(controls);

            return;

        }

        if("recents".equals(id)){

            LinearLayout controls=group(page);

            row(controls,Feature.CLEAR_ALL,"ic_ps_clearall",mAccent,"Always show Clear all","Keeps Clear all next to Screenshot and Select.");

            row(controls,RecentsLock.KEY,"ic_ps_recents_lock",mAccent,"Lock apps in recents","Choose Lock from an app's recents menu to keep its card when you clear apps.");finishGroup(controls);

            explanation(page,"In split screen, each app locks on its own. A recents lock only keeps the card; to require your fingerprint or PIN to open an app, use Privacy & Security.");

            extraPreview(page,RecentsLock.KEY,"Locking recent apps");

            section(page,"Landscape view");

            explanation(page,"Turn your phone sideways: app names stay centered above their cards and the status bar fades out. It comes back when you turn the phone back or leave recents.");

            extraPreview(page,"recents_landscape","Landscape recent apps");

            return;

        }

        if("privacy".equals(id)){

            LinearLayout protection=group(page);

            row(protection,PrivacyLock.ENABLED_KEY,"ic_ps_app_folder_lock",mAccent,"App & folder lock","Asks for your fingerprint or PIN before locked apps and folders open. Turning it off keeps your locks for later.");

            row(protection,PrivacyLock.PROTECT_KEY,"ic_ps_protect_apps",mAccent,"Protect locked apps","Asks before a locked app is uninstalled, disabled or paused, or a locked folder is removed.");

            row(protection,PrivacyLock.GRACE_KEY,"ic_ps_unlock_timer",mAccent,"Keep unlocked for 1 minute","On: an app or folder you just unlocked stays open for 1 minute after you leave it. Off: apps lock as soon as you leave them, and folders as soon as you close them.");finishGroup(protection);

            extraPreview(page,"privacy_combined","Open locked apps and folders with your fingerprint or PIN");
            section(page,"How it works");
            explanation(page,"Hold an app or folder and choose Lock. Open it with your fingerprint or PIN. Choose Unlock from the same menu to remove the lock.\n\nLocking a folder protects the folder itself. Its apps still open from the drawer or search unless you lock them too.");

            return;

        }

        if("updates".equals(id)){buildUpdates(page);return;}

        if("about".equals(id)){

            explanation(page,"All of Pixel Suite's settings in one place. To get back here, long-press the home screen → Pixel Suite settings. Needs root and LSPosed.");



            section(page,"Backup");LinearLayout backup=group(page);
            navigation(backup,"Back up","Save all settings, launcher folders, sorting and icons to a file","ic_ps_backup",new Runnable(){public void run(){startBackup();}});
            navigation(backup,"Restore","Bring everything back from a backup file","ic_ps_restore",new Runnable(){public void run(){startRestore();}});
            finishGroup(backup);

            section(page,"Community and support");communityActions(page);

            return;

        }

        if("changelog".equals(id)){


            LinearLayout history=group(page);navigation(history,"Full changelog on GitHub","Every feature, fix and release","ic_ps_changelog",new Runnable(){public void run(){startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse("https://github.com/RovianDev/PixelSuite/releases")));}});finishGroup(history);

            String[] entries=Changelog.read(this).split("(?m)^## ");int shown=0;

            for(String entry:entries){if(entry.trim().isEmpty())continue;if(shown++>=3)break;int end=entry.indexOf('\n');String heading=end<0?entry.trim():entry.substring(0,end).trim();String body=end<0?"":entry.substring(end+1).trim();int introEnd=body.indexOf("\n\n");if(introEnd>=0&&!body.startsWith("✨")&&!body.startsWith("•")&&!body.startsWith("-"))body=body.substring(introEnd+2).trim();changelogEntry(page,heading,body);}

            return;

        }

        boolean category="buttons".equals(id)||"gestures".equals(id)||"doodles".equals(id)||"launcher".equals(id)||"drawer".equals(id)||"files".equals(id)||"themes".equals(id);

        if(category){
            if("themes".equals(id)){section(page,"Surfaces and blur");LinearLayout style=group(page);surfaceStyle(style);finishGroup(style);}


            LinearLayout features=group(page);if("launcher".equals(id)){

                navigation(features,"App drawer","Folders, dragging and app shortcuts","ic_ps_drawer",new Runnable(){public void run(){openPage("drawer");}});

                navigation(features,"Recent Apps","Clear all, locked cards and landscape view","ic_ps_recent_apps",new Runnable(){public void run(){openPage("recents");}});

            }for(final FeaturePage item:PAGES)if(item.group.equals(id)&&(!"themes".equals(id)||SuperDark.KEY.equals(item.key)))navigation(features,item.title,item.summary,item.icon,new Runnable(){public void run(){openPage(item.key);}});finishGroup(features);
            if("launcher".equals(id)){
                section(page,"Toasts");
                LinearLayout toasts=group(page);
                navChoice(toasts,SystemToasts.KEY,"Show toasts",new String[]{"Top","Middle","Bottom (Stock)"},new String[]{"top","middle","bottom"},"top");
                finishGroup(toasts);
            }
            if("themes".equals(id))((LinearLayout.LayoutParams)features.getLayoutParams()).bottomMargin=dp(6);

            if("themes".equals(id)){

                LinearLayout iconPacks=group(page);
                for(final FeaturePage item:PAGES)if(LauncherFeatures.ICONS.equals(item.key))navigation(iconPacks,item.title,item.summary,item.icon,new Runnable(){public void run(){openPage(item.key);}});
                navigation(iconPacks,"Choose Icon pack","Loading…","ic_ps_icon_pack",new Runnable(){public void run(){if(mIconPackPage!=null)mIconPackPage.show();}});

                mIconPackPage=new IconPackPage(this,navigationSummary(iconPacks),mText,mTextSecondary,mAccent);finishGroup(iconPacks);
                for(int i=0;i<iconPacks.getChildCount();i++){
                    View joined=iconPacks.getChildAt(i);joined.setBackground(rowBackground(i==0?dp(24):0,i==iconPacks.getChildCount()-1?dp(24):0));
                    ((LinearLayout.LayoutParams)joined.getLayoutParams()).topMargin=0;
                }

                extraPreview(page,LauncherFeatures.ICONS,"Use your own image for an app icon");

                extraPreview(page,"209_icon_pack","Apply matching icons across your apps");



                section(page,"The three styles");

                explanation(page,"Blur Off\nTurns off blur everywhere, including Android's window blur. Surfaces are white in light theme and tinted black in dark theme.\n\nStock\nGoogle's own surfaces. Pixel Suite's other changes stay.\n\nFrosted Glass (Default)\nBlurs what's behind folders, menus and messages, with Pixel Suite's tint.");



            }

            return;

        }

        LinearLayout categories=homeSection(page,"Shortcuts");

        navigation(categories,"Buttons","Flashlight and camera shortcuts","ic_ps_buttons",new Runnable(){public void run(){openPage("buttons");}});

        navigation(categories,"Gestures","Screenshots, sleep, wake and dragging","ic_ps_gestures",new Runnable(){public void run(){openPage("gestures");}});

        finishGroup(categories);

        categories=homeSection(page,"Personalize");

        navigation(categories,"Pixel Launcher","Folders, drawer, names and recents","ic_ps_launcher",new Runnable(){public void run(){openPage("launcher");}});

        navigation(categories,"Themes","Icons, Super Dark-Mode and blur","ic_ps_themes",new Runnable(){public void run(){openPage("themes");}});

        navigation(categories,"Doodles","Shapes in Pixel Studio and Google Photos","ic_ps_doodles",new Runnable(){public void run(){openPage("doodles");}});

        finishGroup(categories);

        categories=homeSection(page,"Sharing");

        navigation(categories,"Share menu","Reorder pinned apps and hide sharing apps","ic_ps_share_sheet",new Runnable(){public void run(){openPage("share_menu");}});

        navigation(categories,"Files","Newest files first, in Files and the file picker","ic_ps_files_category",new Runnable(){public void run(){openPage("files");}});

        finishGroup(categories);

        categories=homeSection(page,"Google");

        navigation(categories,"Google apps","Phone, Google, Chrome, News and watch","ic_ps_google_apps",new Runnable(){public void run(){openPage("google_apps");}});

        googleNavigationIcon(categories);

        finishGroup(categories);

        categories=homeSection(page,"Protection");

        navigation(categories,"Privacy & Security","Lock apps and folders","ic_ps_privacy",new Runnable(){public void run(){openPage("privacy");}});

        navigation(categories,"Do Not Annoy","Keep incoming calls quiet and stay connected","ic_ps_do_not_annoy",new Runnable(){public void run(){openPage("do_not_annoy");}});
        navigation(categories,"OTA & Store blocker","Play Store updates and system updates","ic_ps_update_blocker",new Runnable(){public void run(){openPage("blockers");}});

        finishGroup(categories);

        categories=homeSection(page,"More");

        navigation(categories,"Miscellaneous","Screenshots, accessibility, keyboard and developer options","ic_ps_miscellaneous",new Runnable(){public void run(){openPage("miscellaneous");}});

        finishGroup(categories);

        section(page,"Pixel Suite");LinearLayout about=group(page);navigation(about,"Changelog","What's new, available offline","ic_ps_changelog",new Runnable(){public void run(){openPage("changelog");}});navigation(about,"Updates","Check for updates and manage notifications","ic_ps_update",new Runnable(){public void run(){openPage("updates");}});navigation(about,"About and support","Setup, community and support","ic_ps_info",new Runnable(){public void run(){openPage("about");}});finishGroup(about);

    }

    private LinearLayout supportCorner(){

        LinearLayout stack=new LinearLayout(this);stack.setOrientation(LinearLayout.VERTICAL);stack.setGravity(Gravity.RIGHT);

        int[] order={0,2,1};

        for(int position=0;position<order.length;position++){

            final int index=order[position];LinearLayout button=new LinearLayout(this);button.setOrientation(LinearLayout.HORIZONTAL);button.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);button.setPadding(dp(10),dp(6),dp(10),dp(6));button.setMinimumHeight(dp(48));

            ImageView icon=new ImageView(this);Drawable mark=drawable(index==0?"brand_telegram_glyph":CommunityLinks.ICONS[index]);

            if(mark!=null){mark=mark.mutate();if(index==2)mark.setTint(mDark?Color.WHITE:Color.BLACK);icon.setImageDrawable(mark);}

            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);

            LinearLayout.LayoutParams imageParams=new LinearLayout.LayoutParams(dp(20),dp(20));imageParams.rightMargin=dp(7);button.addView(icon,imageParams);

            TextView label=text(CommunityLinks.NAMES[index],12,mText,true);label.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));label.setSingleLine(true);label.setEllipsize(android.text.TextUtils.TruncateAt.END);label.setMaxWidth(dp(100));label.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);if(index==2)label.setTranslationX(HomeLayout.PATREON_TEXT_X_DP*getResources().getDisplayMetrics().density);else if(index==1)label.setTranslationX(HomeLayout.COFFEE_TEXT_X_DP*getResources().getDisplayMetrics().density);button.addView(label,new LinearLayout.LayoutParams(dp(68),-2));

            supportSurface(button);button.setElevation(dp(1));

            button.setClickable(true);button.setFocusable(true);button.setContentDescription(CommunityLinks.NAMES[index]+". "+CommunityLinks.CAPTIONS[index]+". Opens an external app.");

            button.setOnClickListener(new View.OnClickListener(){public void onClick(View v){CommunityLinks.open(SettingsActivity.this,index);}});

            LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(48));params.gravity=Gravity.RIGHT;if(position>0)params.topMargin=dp(6);stack.addView(button,params);

        }return stack;

    }

    private void supportSurface(final View button){

        supportBackground(button,null);

        button.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){

            public void onViewDetachedFromWindow(View v){}

            public void onViewAttachedToWindow(final View v){v.post(new Runnable(){public void run(){

                if(!v.isAttachedToWindow())return;

                try{

                    if(!FrostedSurface.blurEnabled(SettingsActivity.this))return;

                    Object root=LauncherFeatures.call(v,"getViewRootImpl");

                    Drawable blur=(Drawable)LauncherFeatures.call(root,"createBackgroundBlurDrawable");

                    LauncherFeatures.call(blur,"setBlurRadius",FrostedSurface.depthBlurRadius(SettingsActivity.this));

                    LauncherFeatures.call(blur,"setCornerRadius",(float)dp(24));

                    LauncherFeatures.call(blur,"setColor",Color.TRANSPARENT);supportBackground(v,blur);

                }catch(Throwable unavailable){supportBackground(v,null);}

            }});}

        });

    }

    private void supportBackground(View button,Drawable blur){

        GradientDrawable tint=new GradientDrawable();tint.setColor((AppSurfaces.normalCard(this) & 0x00ffffff)|(blur==null?0xff000000:0xcc000000));tint.setCornerRadius(dp(24));

        Drawable fill=blur==null?tint:new android.graphics.drawable.LayerDrawable(new Drawable[]{blur,tint});fill=AppSurfaces.support(this,fill,24);

        GradientDrawable mask=new GradientDrawable();mask.setColor(Color.WHITE);mask.setCornerRadius(dp(24));

        button.setBackground(new android.graphics.drawable.InsetDrawable(new RippleDrawable(ColorStateList.valueOf(mDark?0x33FFFFFF:0x22000000),fill,mask),0,dp(2),0,dp(2)));

    }

    private ImageView brandIcon(int index){

        ImageView icon=new ImageView(this);icon.setImageDrawable(drawable(CommunityLinks.ICONS[index]));icon.setScaleType(ImageView.ScaleType.FIT_CENTER);icon.setPadding(dp(10),dp(10),dp(10),dp(10));GradientDrawable bg=new GradientDrawable();bg.setColor(CommunityLinks.BADGES[index]);bg.setCornerRadius(dp(16));icon.setBackground(bg);icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);return icon;

    }

    private void communityActions(LinearLayout page){

        LinearLayout actions=new LinearLayout(this);actions.setOrientation(LinearLayout.VERTICAL);LinearLayout.LayoutParams outer=new LinearLayout.LayoutParams(-1,-2);outer.bottomMargin=dp(12);page.addView(actions,outer);

        int[] order={0,2,1};for(int position=0;position<order.length;position++){

            final int index=order[position];LinearLayout button=new LinearLayout(this);button.setOrientation(LinearLayout.HORIZONTAL);button.setGravity(Gravity.CENTER_VERTICAL);button.setPadding(dp(18),dp(16),dp(18),dp(16));button.setMinimumHeight(dp(80));button.setBackground(rowBackground(dp(24),dp(24)));

            LinearLayout.LayoutParams iconParams=new LinearLayout.LayoutParams(dp(48),dp(48));iconParams.rightMargin=dp(16);button.addView(brandIcon(index),iconParams);

            LinearLayout labels=new LinearLayout(this);labels.setOrientation(1);labels.addView(text(CommunityLinks.NAMES[index],18,mText,false));

            TextView caption=text(CommunityLinks.CAPTIONS[index],14,mTextSecondary,false);caption.setPadding(0,dp(4),0,0);labels.addView(caption);button.addView(labels,new LinearLayout.LayoutParams(0,-2,1));

            button.setClickable(true);button.setFocusable(true);button.setContentDescription(CommunityLinks.NAMES[index]+". "+CommunityLinks.CAPTIONS[index]+". Opens an external app.");button.setOnClickListener(new View.OnClickListener(){public void onClick(View v){CommunityLinks.open(SettingsActivity.this,index);}});

            LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,-2,0);if(position>0)bp.topMargin=dp(8);actions.addView(button,bp);

        }

    }

    private void updateAdProtectionStatus(){
        if(mAdProtectionStatus!=null){mAdProtectionStatus.setText(PatronAccess.unlocked()?"Included with your Patreon unlock":"Unlock ad blocking with Patreon");mAdProtectionStatus.setClickable(!PatronAccess.unlocked());mAdProtectionStatus.setFocusable(!PatronAccess.unlocked());}
    }
    private void appendScopeFooter(LinearLayout page,String id){
        String note="do_not_annoy".equals(id)?FeatureScopes.doNotAnnoy(mDnaPixelStack):FeatureScopes.get(id);if(note==null)return;
        if(ScopePanel.installedEntries(this,ScopePanel.entries(id,pageTitle(id),note)).isEmpty())return;
        String title=Feature.SCREENSHOT_DOODLES.equals(id)||Feature.SCREENSHOT_FREE_CROP.equals(id)?"Pixel Studio setup":Feature.PHOTOS_DOODLES.equals(id)?"Google Photos setup":"LSPosed scope";
        section(page,title);ScopePanel.add(this,page,id,pageTitle(id),note,mText,mTextSecondary,mAccent);
    }
    private static final class PreviewEntry {
        final View heading;final LinearLayout card;final FeaturePreview preview;final String title;
        PreviewEntry(View h,LinearLayout c,FeaturePreview p,String t){heading=h;card=c;preview=p;title=t;}
    }
    private final java.util.List<PreviewEntry> mPreviewEntries=new java.util.ArrayList<PreviewEntry>();
    private void combineFeaturePreviews(LinearLayout page){
        if(mPreviewEntries.size()<2)return;
        PreviewEntry last=mPreviewEntries.get(mPreviewEntries.size()-1);int lastAt=page.indexOfChild(last.card);
        View anchor=lastAt+1<page.getChildCount()?page.getChildAt(lastAt+1):null;
        for(PreviewEntry entry:mPreviewEntries){entry.card.removeView(entry.preview);page.removeView(entry.heading);page.removeView(entry.card);}
        int at=anchor==null?page.getChildCount():page.indexOfChild(anchor);
        TextView heading=text("Features preview",14,mTextSecondary,true);heading.setPadding(dp(8),dp(4),dp(8),dp(8));page.addView(heading,at++);
        LinearLayout card=new LinearLayout(this);card.setOrientation(1);card.setPadding(dp(8),dp(12),dp(8),dp(12));card.setBackground(AppSurfaces.card(this,24));
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.bottomMargin=dp(16);page.addView(card,at,cp);
        for(int i=0;i<mPreviewEntries.size();i+=2){
            LinearLayout phones=new LinearLayout(this);phones.setOrientation(0);phones.setBaselineAligned(false);card.addView(phones,new LinearLayout.LayoutParams(-1,-2));
            for(int j=i;j<Math.min(i+2,mPreviewEntries.size());j++){
                PreviewEntry entry=mPreviewEntries.get(j);LinearLayout column=new LinearLayout(this);column.setOrientation(1);
                LinearLayout.LayoutParams col=new LinearLayout.LayoutParams(0,-2,1);if(j>i)col.leftMargin=dp(8);phones.addView(column,col);
                TextView label=text(entry.title,12,mTextSecondary,false);label.setGravity(Gravity.CENTER);label.setMaxLines(2);label.setMinimumHeight(dp(38));column.addView(label,new LinearLayout.LayoutParams(-1,dp(38)));
                entry.preview.setCompact(true);column.addView(entry.preview,new LinearLayout.LayoutParams(-1,dp(280)));
            }
        }
    }
    private void featurePreview(LinearLayout page,FeaturePage feature){

        section(page,"Feature preview");View previewHeading=page.getChildAt(page.getChildCount()-1);LinearLayout card=new LinearLayout(this);card.setOrientation(1);card.setPadding(dp(12),dp(12),dp(12),dp(12));card.setBackground(AppSurfaces.card(this,24));LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.bottomMargin=dp(16);page.addView(card,cp);

        mFeaturePreview=new FeaturePreview(this,feature.key,mAccent,mOnAccent,mText,mTextSecondary,mCard,mDark);mFeaturePreview.setContentDescription(feature.title+". A looping demo. It doesn't change your settings.");card.addView(mFeaturePreview,new LinearLayout.LayoutParams(-1,dp("privacy_combined".equals(feature.key)?400:(SuperDark.KEY.equals(feature.key)||LauncherFeatures.DRAWER.equals(feature.key)||LauncherFeatures.GRID.equals(feature.key)||"folder_sort".equals(feature.key)||FolderDrag.TWO_FINGER.equals(feature.key)||MultiDrag.KEY.equals(feature.key))?330:300)));mPreviews.add(mFeaturePreview);mPreviewEntries.add(new PreviewEntry(previewHeading,card,mFeaturePreview,feature.title));

    }

    private void extraPreview(LinearLayout page,String key,String title){featurePreview(page,new FeaturePage("launcher",key,"ic_ps_folder_grid",title,""));}

    private TextView mUpdateStatus;

    private android.widget.ProgressBar mUpdateProgress;

    private void beginUpdateInstall(){

        UpdateInstaller.start(this,new UpdateInstaller.Progress(){public void show(String message,int percent){

            if(isDestroyed()||isFinishing())return;

            if(mUpdateStatus!=null)mUpdateStatus.setText(message);

            if(mUpdateProgress!=null){mUpdateProgress.setVisibility(View.VISIBLE);mUpdateProgress.setIndeterminate(percent<0&&message.contains("Verifying"));if(percent>=0){mUpdateProgress.setIndeterminate(false);mUpdateProgress.setProgress(percent);}else if(!message.contains("Verifying"))mUpdateProgress.setVisibility(View.GONE);}

        }});

    }

    private void startBackup(){
        try{Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json").putExtra(Intent.EXTRA_TITLE,Backup.fileName());startActivityForResult(i,Backup.SAVE);}
        catch(Exception e){AppToast.makeText(this,"No file picker available",Toast.LENGTH_LONG).show();}
    }
    private void startRestore(){
        try{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*"+"/"+"*").putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"application/json","text/plain","application/octet-stream"});startActivityForResult(i,Backup.OPEN);}
        catch(Exception e){AppToast.makeText(this,"No file picker available",Toast.LENGTH_LONG).show();}
    }
    private void backupResult(final int request,final Intent data){
        final android.net.Uri uri=data==null?null:data.getData();if(uri==null)return;
        if(request==Backup.SAVE){
            AppToast.makeText(this,"Backing up…",Toast.LENGTH_SHORT).show();
            IO.execute(new Runnable(){public void run(){boolean ok;try{String json=Backup.create(SettingsActivity.this);java.io.OutputStream out=getContentResolver().openOutputStream(uri,"wt");if(out==null)throw new java.io.IOException();try{out.write(json.getBytes("UTF-8"));}finally{out.close();}ok=true;}catch(Throwable t){ok=false;}
                final boolean done=ok;mMain.post(new Runnable(){public void run(){if(isFinishing()||isDestroyed())return;if(done)AppToast.makeText(SettingsActivity.this,"Backup saved",Toast.LENGTH_SHORT).show();else{showRootBanner(true);AppToast.makeText(SettingsActivity.this,"Couldn't back up: root access needed",Toast.LENGTH_LONG).show();}}});}});
            return;
        }
        IO.execute(new Runnable(){public void run(){org.json.JSONObject read=null;try{java.io.InputStream in=getContentResolver().openInputStream(uri);if(in!=null)try{java.io.ByteArrayOutputStream b=new java.io.ByteArrayOutputStream();byte[] buf=new byte[65536];for(int n;(n=in.read(buf))>0;)b.write(buf,0,n);read=Backup.read(b.toString("UTF-8"));}finally{in.close();}}catch(Throwable ignored){}
            final org.json.JSONObject backup=read;mMain.post(new Runnable(){public void run(){if(isFinishing()||isDestroyed())return;
                if(backup==null){AppToast.makeText(SettingsActivity.this,"That isn't a Pixel Suite backup",Toast.LENGTH_LONG).show();return;}
                showSettingsDialog(new AlertDialog.Builder(SettingsActivity.this).setTitle("Restore this backup?").setMessage(Backup.summary(backup)).setNegativeButton("Cancel",null).setPositiveButton("Restore",new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){
                    AppToast.makeText(SettingsActivity.this,"Restoring…",Toast.LENGTH_SHORT).show();
                    IO.execute(new Runnable(){public void run(){final boolean ok=Backup.restore(SettingsActivity.this,backup);mMain.post(new Runnable(){public void run(){if(isFinishing()||isDestroyed())return;
                        if(ok){AppToast.makeText(SettingsActivity.this,"Backup restored",Toast.LENGTH_SHORT).show();recreate();}else{showRootBanner(true);AppToast.makeText(SettingsActivity.this,"Couldn't restore: root access needed",Toast.LENGTH_LONG).show();}}});}});
                }}).create());}});}});
    }

    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if((request==Backup.SAVE||request==Backup.OPEN)&&result==RESULT_OK){backupResult(request,data);return;}if(request==CallRecordingSettings.PICK_FOLDER&&mCallRecordingSettings!=null){mCallRecordingSettings.result(result,data);return;}if(request==202&&getIntent().getBooleanExtra("resume_update_install",false)){getIntent().removeExtra("resume_update_install");try{if((Boolean)getPackageManager().getClass().getMethod("canRequestPackageInstalls").invoke(getPackageManager()))beginUpdateInstall();else if(mUpdateStatus!=null)mUpdateStatus.setText("Installation permission was not enabled. Tap Download and install when ready.");}catch(Exception unavailable){}}}

    private void buildUpdates(LinearLayout page){

        explanation(page,"Pixel Suite checks for official updates from time to time. Download an update here, follow its progress, then confirm the install.");

        final android.widget.Switch automatic=new android.widget.Switch(this);automatic.setText("Check for updates automatically");android.graphics.drawable.Drawable updateIcon=drawable("ic_ps_update");if(updateIcon!=null){updateIcon=updateIcon.mutate();updateIcon.setTint(mAccent);updateIcon.setBounds(0,0,dp(24),dp(24));automatic.setCompoundDrawables(updateIcon,null,null,null);automatic.setCompoundDrawablePadding(dp(12));}automatic.setTextColor(mText);automatic.setChecked(ReleaseUpdates.enabled(this));automatic.setPadding(dp(18),dp(16),dp(18),dp(16));page.addView(automatic);

        automatic.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener(){public void onCheckedChanged(android.widget.CompoundButton v,boolean checked){ReleaseUpdates.prefs(SettingsActivity.this).edit().putBoolean("enabled",checked).apply();ReleaseUpdates.schedule(SettingsActivity.this);if(checked)ReleaseUpdates.check(SettingsActivity.this,false,null);}});

        final TextView status=text(ReleaseUpdates.status(this),15,mTextSecondary,false);mUpdateStatus=status;status.setPadding(dp(18),dp(12),dp(18),dp(16));page.addView(status);mUpdateProgress=new android.widget.ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);mUpdateProgress.setMax(100);mUpdateProgress.setVisibility(View.GONE);page.addView(mUpdateProgress,new LinearLayout.LayoutParams(-1,dp(8)));

        LinearLayout actions=group(page);

        navigation(actions,"Check now","Check for updates now","ic_ps_update",new Runnable(){public void run(){status.setText("Checking both repositories…");ReleaseUpdates.check(SettingsActivity.this,true,new ReleaseUpdates.Result(){public void done(String message){if(!isDestroyed())status.setText(message);}});}});

        navigation(actions,"Allow update notifications","Turn on notifications in Android settings","ic_ps_update",new Runnable(){public void run(){if(android.os.Build.VERSION.SDK_INT>=33&&checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=0)requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},201);else{Intent settings=new Intent("android.settings.APP_NOTIFICATION_SETTINGS");settings.putExtra("android.provider.extra.APP_PACKAGE",getPackageName());try{startActivity(settings);}catch(Exception unavailable){AppToast.makeText(SettingsActivity.this,"Open Android app settings to manage notifications.",0).show();}}}});

        navigation(actions,"Download and install","Download and install the update in Pixel Suite","ic_ps_update",new Runnable(){public void run(){beginUpdateInstall();}});

        finishGroup(actions);

    }

    private final java.util.List<AlertDialog> mSettingsDialogs=new java.util.ArrayList<AlertDialog>();

    private void showSettingsDialog(final AlertDialog dialog){

        dialog.setCancelable(true);dialog.setCanceledOnTouchOutside(true);dialog.show();mSettingsDialogs.add(dialog);dialog.setOnDismissListener(new DialogInterface.OnDismissListener(){public void onDismiss(DialogInterface d){mSettingsDialogs.remove(dialog);}});

        Window window=dialog.getWindow();if(window==null)return;TapFeedback.watch(window.getDecorView());

        styleSettingsWindow(window);
        AppPanelBounds.fit(this,window,window.getAttributes().width,-2);

        for(String name:new String[]{"parentPanel","topPanel","contentPanel","buttonPanel"}){

            int id=getResources().getIdentifier(name,"id","android");View panel=window.getDecorView().findViewById(id);if(panel!=null)panel.setBackgroundColor(Color.TRANSPARENT);

        }

        ToastMotion.watchScroll(window.getDecorView());colorSettingsDialog(window.getDecorView());

        if(dialog.getListView()!=null)dialog.getListView().setOnHierarchyChangeListener(new ViewGroup.OnHierarchyChangeListener(){public void onChildViewAdded(View parent,View child){colorSettingsDialog(child);}public void onChildViewRemoved(View parent,View child){}});

        for(int which:new int[]{AlertDialog.BUTTON_POSITIVE,AlertDialog.BUTTON_NEGATIVE,AlertDialog.BUTTON_NEUTRAL})if(dialog.getButton(which)!=null)dialog.getButton(which).setTextColor(mAccent);

    }

    private void styleSettingsWindow(Window window){

        if(window==null)return;TapFeedback.watch(window.getDecorView());

        if(AppSurfaces.active(this)){window.setBackgroundDrawable(AppSurfaces.dialog(this,28));AppSurfaces.dim(window,.0f);}

        else{GradientDrawable fill=new GradientDrawable();fill.setColor(mBg);fill.setCornerRadius(dp(28));window.setBackgroundDrawable(fill);window.setDimAmount(0f);}

        AppSurfaces.invalidateTree(window.getDecorView());

    }

    private void colorSettingsDialog(View view){

        if(view instanceof TextView)((TextView)view).setTextColor(mText);

        if(view instanceof android.widget.CheckedTextView){

            ColorStateList checks=new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{mAccent,mTextSecondary});

            ((android.widget.CheckedTextView)view).setCheckMarkTintList(checks);

        }

        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)colorSettingsDialog(group.getChildAt(i));}

    }

    private void consolidateExplanations(LinearLayout page,String id){
        java.util.ArrayList<TextView> notes=new java.util.ArrayList<TextView>();
        java.util.ArrayList<View> headings=new java.util.ArrayList<View>();
        int first=-1;
        for(int i=0;i<page.getChildCount();i++){
            View child=page.getChildAt(i);
            if("ps_explanation".equals(child.getTag()) && child instanceof TextView){
                notes.add((TextView)child);if(first<0)first=i;
                if(i>0){View previous=page.getChildAt(i-1);if("ps_section".equals(previous.getTag())){headings.add(previous);if(first==i)first=i-1;}}
            }
        }
        if(notes.isEmpty())return;
        java.util.LinkedHashSet<String> points=new java.util.LinkedHashSet<String>();
        for(TextView note:notes)for(String paragraph:note.getText().toString().split("\n\\s*\n")){String point=paragraph.trim().replaceFirst("^(?:[•·]\\s*)+", "");if(!point.isEmpty())points.add(point);}
        if(LauncherFeatures.RENAME.equals(id)){
            points.clear();points.add("Hold an app and choose Rename. Changes appear instantly on the home screen, in folders, in the drawer and in Google search.");
            points.add("Reset restores the original name. Work-profile and Private Space apps keep their names.");
        }else if("recents".equals(id)){
            points.clear();points.add("Lock a card from its menu to keep it when clearing apps. Split-screen apps lock separately; locks survive reboots.");
            points.add("A recents lock keeps the card, not the app running. For fingerprint or PIN protection, use Privacy & Security.");
            points.add("Landscape: app names stay centered above cards and the status bar fades out. It returns when you turn back or leave recents.");
        }
        for(TextView note:notes)page.removeView(note);for(View heading:headings)page.removeView(heading);
        StringBuilder body=new StringBuilder();for(String point:points){if(body.length()>0)body.append("\n\n");body.append("• ").append(point);}
        explanation(page,body.toString());View panel=page.getChildAt(page.getChildCount()-1);page.removeView(panel);page.addView(panel,Math.min(first,page.getChildCount()));
        if("do_not_annoy".equals(id)&&panel instanceof TextView){
            TextView note=(TextView)panel;
            boolean black=AppSurfaces.active(this);
            GradientDrawable circle=new GradientDrawable();circle.setShape(GradientDrawable.OVAL);circle.setColor(black?0xff1a1a1a:mDark?0xff2b2b30:android.graphics.Color.WHITE);
            Drawable mark=drawable("brand_telegram_glyph");
            if(mark!=null){
                mark=mark.mutate();mark.setTint(mDark||black?0xffe6e6ea:0xff1f1f1f);
                android.graphics.drawable.LayerDrawable bubble=new android.graphics.drawable.LayerDrawable(new Drawable[]{AppSurfaces.role(this,circle,999,SuperDarkPalette.FIELD,false),mark});
                bubble.setLayerInset(1,dp(4),dp(5),dp(6),dp(5));bubble.setBounds(0,0,dp(24),dp(24));
                android.text.SpannableStringBuilder linked=new android.text.SpannableStringBuilder(note.getText());
                int end=linked.toString().indexOf("Telegram for the APK.");
                if(end>=0){
                    end+="Telegram for the APK.".length();linked.insert(end,"\u00A0\uFFFC");
                    linked.setSpan(new android.text.style.ImageSpan(bubble){
                        @Override public int getSize(android.graphics.Paint paint,CharSequence text,int start,int end,android.graphics.Paint.FontMetricsInt metrics){
                            int size=getDrawable().getBounds().width();
                            if(metrics!=null){android.graphics.Paint.FontMetricsInt font=paint.getFontMetricsInt();int center=(font.ascent+font.descent)/2;metrics.ascent=Math.min(font.ascent,center-size/2);metrics.descent=Math.max(font.descent,center+size/2);metrics.top=Math.min(font.top,metrics.ascent);metrics.bottom=Math.max(font.bottom,metrics.descent);}
                            return size;
                        }
                        @Override public void draw(android.graphics.Canvas canvas,CharSequence text,int start,int end,float x,int top,int y,int bottom,android.graphics.Paint paint){
                            Drawable icon=getDrawable();android.graphics.Paint.FontMetrics metrics=paint.getFontMetrics();int saved=canvas.save();canvas.translate(x,y+(metrics.ascent+metrics.descent-icon.getBounds().height())/2f);icon.draw(canvas);canvas.restoreToCount(saved);
                        }
                    },end+1,end+2,android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    linked.setSpan(new android.text.style.ClickableSpan(){public void onClick(View view){CommunityLinks.open(SettingsActivity.this,0);}public void updateDrawState(android.text.TextPaint paint){paint.setUnderlineText(false);}},end+1,end+2,android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    note.setText(linked);note.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());note.setHighlightColor(android.graphics.Color.TRANSPARENT);
                }
            }
        }
    }

    private void changelogEntry(LinearLayout page,String heading,String body){
        section(page,heading);
        String[] blocks=body.trim().split("\\n\\s*\\n");
        for(int b=0;b<blocks.length;b++){
            String[] lines=blocks[b].trim().split("\\n");
            if(lines.length==0||lines[0].trim().isEmpty())continue;
            boolean list=lines.length>1;
            if(!list&&b>0&&b==blocks.length-1){TextView foot=text(lines[0].trim(),14,mTextSecondary,false);foot.setText(ChangelogText.format(lines[0].trim()));foot.setLineSpacing(dp(3),1);foot.setTextIsSelectable(true);foot.setPadding(dp(18),dp(4),dp(18),dp(18));page.addView(foot);continue;}
            LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(18),dp(16),dp(18),dp(18));card.setBackground(AppSurfaces.card(this,20));
            if(!list){
                TextView intro=text(lines[0].trim(),16,mText,false);intro.setText(ChangelogText.format(lines[0].trim()));intro.setLineSpacing(dp(4),1);intro.setTextIsSelectable(true);card.addView(intro);
            }else{
                TextView title=text(lines[0].trim(),17,mText,true);card.addView(title);
                for(int i=1;i<lines.length;i++){
                    String line=lines[i].trim();if(line.isEmpty())continue;
                    String[] item=ChangelogText.split(line);
                    LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);
                    TextView mark=text(item[0],"\u2022".equals(item[0])?16:15,mTextSecondary,false);mark.setGravity(Gravity.CENTER_HORIZONTAL);mark.setIncludeFontPadding(true);
                    TextView words=text(item[1],15,mText,false);words.setText(ChangelogText.item(item[1]));words.setLineSpacing(dp(3),1);words.setTextIsSelectable(true);
                    row.addView(mark,new LinearLayout.LayoutParams(dp(30),-2));row.addView(words,new LinearLayout.LayoutParams(0,-2,1));
                    LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2);rp.topMargin=dp(i==1?14:12);card.addView(row,rp);
                }
            }
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(12);page.addView(card,lp);
        }
    }

    private void explanation(LinearLayout page,String value){

        TextView t=text(value,15,mTextSecondary,false);t.setTag("ps_explanation");t.setPadding(dp(18),dp(16),dp(18),dp(18));t.setLineSpacing(dp(3),1);

        GradientDrawable bg=AppSurfaces.card(this,20);t.setBackground(bg);

        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(12);page.addView(t,lp);

    }

    private void navigation(LinearLayout group,String title,String summary,String icon,final Runnable action){

        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(18),dp(18),dp(18),dp(18));row.setMinimumHeight(dp(80));

        ImageView image=new ImageView(this);Drawable drawable=drawable(icon);if(drawable!=null){drawable=drawable.mutate();drawable.setTint(sectionInk(icon));image.setImageDrawable(drawable);}

        image.setPadding(dp(9),dp(9),dp(9),dp(9));image.setScaleType(ImageView.ScaleType.FIT_CENTER);GradientDrawable badge=new GradientDrawable();badge.setShape(GradientDrawable.OVAL);badge.setColor(sectionColor(icon));image.setBackground(badge);

        LinearLayout.LayoutParams iconParams=new LinearLayout.LayoutParams(dp(44),dp(44));iconParams.rightMargin=dp(16);row.addView(image,iconParams);

        LinearLayout labels=new LinearLayout(this);labels.setOrientation(1);labels.addView(text(title,18,mText,false));

        TextView description=text(summary,14,mTextSecondary,false);description.setMaxLines(2);description.setEllipsize(android.text.TextUtils.TruncateAt.END);description.setPadding(0,dp(4),0,0);labels.addView(description);

        row.addView(labels,new LinearLayout.LayoutParams(0,-2,1));TextView arrow=text("\u203a",28,mTextSecondary,false);arrow.setPadding(dp(12),0,0,0);row.addView(arrow);

        if("Google apps".equals(title)||"google_apps".equals(getIntent().getStringExtra("page")))PatronUi.navigationBadge(this,row,title);

        row.setClickable(true);row.setFocusable(true);row.setContentDescription(title);row.setTag("search.anchor:"+title);row.setOnClickListener(new View.OnClickListener(){public void onClick(View v){if(PatronAccess.protectedPage(getIntent().getStringExtra("page"))&&!PatronAccess.unlocked()){PatronPaper.open(SettingsActivity.this,0,null);return;}action.run();}});group.addView(row,new LinearLayout.LayoutParams(-1,-2));

    }

    private void updateChoiceLabel(String key,int value){

        TextView label=mChoiceLabels.get(key);String[] items=mChoiceItems.get(key);Integer offset=mChoiceOffsets.get(key);

        if(label!=null&&items!=null)label.setText(items[Math.max(0,Math.min(items.length-1,value-(offset==null?0:offset)))]);

        if(LauncherFeatures.LABEL_LEVEL.equals(key)&&mLabelSlider!=null){mLabelSlider.setEnabled(true);mLabelSlider.setProgress(IconLabelScale.level(value));showLabelSize(value);}

        if(SurfaceStyle.KEY.equals(key)&&mStyleSlider!=null){mSavedStyle=Math.max(0,Math.min(2,value));mStyleLoaded=true;updateStyleControls();}

        ImageView icon=mChoiceIcons.get(key);

        if(icon!=null){

            String name=folderGridKey(key)?gridIcon(key):Feature.LOCK_SHORTCUTS.equals(key)?"ic_ps_lock_options":value==1?"ic_ps_double_tap":"ic_ps_single_tap";

            Drawable d=drawable(name);if(d!=null){d=d.mutate();d.setTint(mAccent);icon.setImageDrawable(d);}

        }

    }

    private void showLabelSize(int value){

        int level=IconLabelScale.level(value);

        TextView meter=mChoiceLabels.get(LauncherFeatures.LABEL_LEVEL);

        if(meter!=null)meter.setText("Level "+(level+1)+" · "+IconLabelScale.name(level)+(level==2?" (Default)":""));

        if(mLabelPreview!=null)mLabelPreview.setTextSize(16*IconLabelScale.factor(level));

        for(int i=0;i<5;i++)if(mLabelStops[i]!=null){mLabelStops[i].setTextColor(i==level?mAccent:mTextSecondary);mLabelStops[i].setTypeface(null,i==level?Typeface.BOLD:Typeface.NORMAL);}

    }

    private void showStyle(int value){
        TextView meter=mChoiceLabels.get(SurfaceStyle.KEY);
        if(meter!=null)meter.setText(STYLE_LABELS[value]);
        if(mStylePreview!=null)mStylePreview.setStyle(value);
        for(int i=0;i<3;i++)if(mStyleStops[i]!=null){
            mStyleNames[i].setTextColor(i==value?mAccent:mText);
            mStyleNames[i].setTypeface(Typeface.create(i==value?"sans-serif":"sans-serif-medium",i==value?Typeface.BOLD:Typeface.NORMAL));
            mStyleDescriptions[i].setTextColor(mTextSecondary);
            mStyleStops[i].setEnabled(mStyleLoaded);mStyleStops[i].setAlpha(mStyleLoaded?1f:.42f);
        }
    }

    private void surfaceStyle(LinearLayout group){
        LinearLayout box=new LinearLayout(this);box.setOrientation(1);box.setPadding(dp(18),dp(12),dp(18),dp(20));
        TextView meter=text("Loading…",18,mAccent,true);meter.setGravity(Gravity.CENTER);box.addView(meter,new LinearLayout.LayoutParams(-1,-2));
        mChoiceLabels.put(SurfaceStyle.KEY,meter);mChoiceItems.put(SurfaceStyle.KEY,SurfaceStyle.NAMES);mChoiceOffsets.put(SurfaceStyle.KEY,0);
        mStyleSlider=new LabelSizeSlider(this,mAccent,mDark?0xff686970:0xff9d9da5,3);mStyleSlider.setEnabled(false);
        mStyleSlider.setProgressTintList(ColorStateList.valueOf(mAccent));mStyleSlider.setThumbTintList(ColorStateList.valueOf(mAccent));
        mStyleSlider.setContentDescription("Surface style, three options");box.addView(mStyleSlider,new LinearLayout.LayoutParams(-1,dp(48)));
        LinearLayout stops=new LinearLayout(this);stops.setOrientation(0);stops.setGravity(Gravity.TOP);stops.setBaselineAligned(false);
        String[] captions={"No blur anywhere","Google's own look","Blurred and tinted"};
        for(int i=0;i<3;i++){
            final int stop=i;LinearLayout column=new LinearLayout(this);column.setOrientation(1);column.setGravity(Gravity.TOP|Gravity.CENTER_HORIZONTAL);column.setPadding(dp(4),dp(4),dp(4),dp(8));
            TextView name=text(STYLE_LABELS[i],14,mText,false);name.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));name.setSingleLine(true);name.setEllipsize(android.text.TextUtils.TruncateAt.END);name.setGravity(Gravity.CENTER);column.addView(name,new LinearLayout.LayoutParams(-1,-2));
            TextView description=text(captions[i],12,mTextSecondary,false);description.setMaxLines(2);description.setGravity(Gravity.TOP|Gravity.CENTER_HORIZONTAL);LinearLayout.LayoutParams descSize=new LinearLayout.LayoutParams(-1,-2);descSize.topMargin=dp(3);column.addView(description,descSize);
            column.setContentDescription(STYLE_LABELS[i]+", "+captions[i]);column.setEnabled(false);column.setAlpha(.42f);column.setOnClickListener(v->{if(mStyleLoaded&&mStyleSlider.isEnabled()){mStyleSlider.setProgress(stop);saveStyle(stop);}});
            mStyleStops[i]=column;mStyleNames[i]=name;mStyleDescriptions[i]=description;stops.addView(column,new LinearLayout.LayoutParams(0,-2,1));
        }
        box.addView(stops,new LinearLayout.LayoutParams(-1,-2));
        mStylePreview=new SurfacePreview(this,mDark,mAccent,mText);mStylePreview.setContentDescription("Preview of the chosen surface style on a folder over a wallpaper");
        LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(-1,dp(220));pp.topMargin=dp(12);box.addView(mStylePreview,pp);
        mStyleSuperDarkNote=text("With Super Dark-Mode, panels stay black. Blur Off removes their rims; Stock and Frosted Glass keep them. Your choice applies fully when Super Dark is off.",15,mTextSecondary,false);mStyleSuperDarkNote.setLineSpacing(dp(3),1);mStyleSuperDarkNote.setVisibility(View.GONE);LinearLayout.LayoutParams noteSize=new LinearLayout.LayoutParams(-1,-2);noteSize.topMargin=dp(12);box.addView(mStyleSuperDarkNote,noteSize);
        mStyleSlider.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener(){
            public void onStartTrackingTouch(android.widget.SeekBar slider){if(mStylePreview!=null){mStylePreview.setInteracting(true);mStylePreview.setStyle(slider.getProgress());}}
            public void onStopTrackingTouch(android.widget.SeekBar slider){if(mStylePreview!=null){mStylePreview.setStyle(slider.getProgress());mStylePreview.setInteracting(false);}}
            public void onProgressChanged(android.widget.SeekBar slider,int value,boolean user){showStyle(value);if(user&&!mUpdating)saveStyle(value);}
        });
        group.addView(box,new LinearLayout.LayoutParams(-1,-2));
    }

    private void updateStyleControls(){
        if(mStyleSlider==null)return;
        mStyleSlider.setEnabled(mStyleLoaded);mStyleSlider.setAlpha(mStyleLoaded?1f:.42f);mStyleSlider.setContentDescription("Surface style, three options");
        if(mStyleSuperDarkNote!=null){boolean dark=(getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES;mStyleSuperDarkNote.setVisibility(dark&&mStyleSuperDark?View.VISIBLE:View.GONE);}
        mStyleSlider.setProgress(mSavedStyle);showStyle(mSavedStyle);
    }

    private void saveStyle(final int value){

        if(!mStyleLoaded)return;

        mSavedStyle=value;showStyle(value);mSaves++;

        final boolean blurOff=value==SurfaceStyle.OFF||SuperDark.inEffect(this);

        final String cmd="settings put secure "+SurfaceStyle.KEY+" "+value+"; settings put global disable_window_blurs "+(blurOff?1:0)+"; am force-stop "+PhotosEditor.PACKAGE+" >/dev/null 2>&1; true";

        IO.execute(new Runnable(){public void run(){final boolean ok=Root.run(cmd)!=null;mMain.post(new Runnable(){public void run(){if(isFinishing()||isDestroyed())return;if(ok){SurfaceStyle.refresh();RestartPill.changed(SurfaceStyle.KEY);if(mRestart!=null)mRestart.refresh();}if(!ok){showRootBanner(true);AppToast.makeText(SettingsActivity.this,"Could not save the surface style",Toast.LENGTH_SHORT).show();load();}}});}});

    }

    private void labelSize(LinearLayout group){

        LinearLayout box=new LinearLayout(this);box.setOrientation(1);box.setPadding(dp(18),dp(12),dp(18),dp(20));

        TextView meter=text("Loading…",18,mAccent,true);meter.setGravity(Gravity.CENTER);box.addView(meter,new LinearLayout.LayoutParams(-1,-2));

        mChoiceLabels.put(LauncherFeatures.LABEL_LEVEL,meter);

        mChoiceItems.put(LauncherFeatures.LABEL_LEVEL,new String[]{"Smallest","Small","Medium","Large","Largest"});

        mChoiceOffsets.put(LauncherFeatures.LABEL_LEVEL,0);

        mLabelSlider=new LabelSizeSlider(this,mAccent,mDark?0xff686970:0xff9d9da5);mLabelSlider.setEnabled(false);

        mLabelSlider.setProgressTintList(ColorStateList.valueOf(mAccent));mLabelSlider.setThumbTintList(ColorStateList.valueOf(mAccent));

        mLabelSlider.setContentDescription("Icon label size, five levels");

        box.addView(mLabelSlider,new LinearLayout.LayoutParams(-1,dp(48)));

        LinearLayout stops=new LinearLayout(this);stops.setOrientation(0);

        for(int i=0;i<5;i++){final int stop=i;TextView label=text((i+1)+"\n"+IconLabelScale.name(i),13,mTextSecondary,false);label.setGravity(Gravity.TOP|Gravity.CENTER_HORIZONTAL);label.setPadding(dp(5),dp(4),dp(5),dp(8));label.setContentDescription("Level "+(i+1)+", "+IconLabelScale.name(i));label.setOnClickListener(new View.OnClickListener(){public void onClick(View v){if(mLabelSlider.isEnabled()){mLabelSlider.setProgress(stop);saveLabelSize(stop);}}});mLabelStops[i]=label;stops.addView(label,new LinearLayout.LayoutParams(0,dp(56),1));}

        box.addView(stops,new LinearLayout.LayoutParams(-1,-2));

        ImageView icon=new ImageView(this);Drawable image=drawable("ic_ps_label_size");if(image!=null){image=image.mutate();image.setTint(mAccent);icon.setImageDrawable(image);}

        LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(dp(48),dp(48));ip.gravity=Gravity.CENTER_HORIZONTAL;ip.topMargin=dp(16);box.addView(icon,ip);

        mLabelPreview=text("App name",16*.85f,mText,false);mLabelPreview.setGravity(Gravity.CENTER);mLabelPreview.setPadding(0,dp(8),0,dp(8));box.addView(mLabelPreview,new LinearLayout.LayoutParams(-1,-2));

        TextView note=text("Changes only the text under icons.\nIcon size and shape stay the same.",13,mTextSecondary,false);note.setGravity(Gravity.CENTER);box.addView(note,new LinearLayout.LayoutParams(-1,-2));

        mLabelSlider.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(android.widget.SeekBar s){}public void onStopTrackingTouch(android.widget.SeekBar s){}public void onProgressChanged(android.widget.SeekBar s,int value,boolean user){showLabelSize(value);if(user&&!mUpdating)saveLabelSize(value);}});

        group.addView(box,new LinearLayout.LayoutParams(-1,-2));

    }

    private void saveLabelSize(final int value){

        showLabelSize(value);mSaves++;

        IO.execute(new Runnable(){public void run(){final boolean ok=Root.run("settings put secure "+LauncherFeatures.LABEL_LEVEL+" "+IconLabelScale.level(value))!=null;mMain.post(new Runnable(){public void run(){if(isFinishing()||isDestroyed())return;if(ok){RestartPill.changed(LauncherFeatures.LABEL_LEVEL);if(mRestart!=null)mRestart.refresh();}if(!ok){showRootBanner(true);AppToast.makeText(SettingsActivity.this,"Could not save text size",Toast.LENGTH_SHORT).show();load();}}});}});

    }

    private LinearLayout mNavBody,mNavHome;
    private final Map<String,TextView> mNavLabels=new LinkedHashMap<String,TextView>();
    private final Map<String,String> mNavSaved=new LinkedHashMap<String,String>();
    private String navValue(String key,String fallback){String saved=mNavSaved.get(key);if(saved!=null)return saved;try{String value=android.provider.Settings.Secure.getString(getContentResolver(),key);return value==null?fallback:value;}catch(Throwable ignored){return fallback;}}
    private void buildNavigationButtons(LinearLayout page){
        LinearLayout toggle=group(page);row(toggle,NavButtons.KEY,"ic_ps_navigation",mAccent,"Navigation buttons","Buttons beside the gesture bar");finishGroup(toggle);
        section(page,"How it works");explanation(page,"Choose Back, Home or Recent for each side. Hold Home for Circle to Search or Gemini. Swipe gestures still work.");
        mNavBody=new LinearLayout(this);mNavBody.setOrientation(1);page.addView(mNavBody,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout sides=group(mNavBody);
        String[] labels={"Home","Back","Recent","Hide"},values={"home","back","recent","hide"};
        navChoice(sides,NavButtons.LEFT,"Left button",labels,values,"back");
        navChoice(sides,NavButtons.RIGHT,"Right button",labels,values,"recent");finishGroup(sides);
        mNavHome=group(mNavBody);navChoice(mNavHome,NavButtons.HOLD_HOME,"Hold Home for",new String[]{"Circle to Search","Gemini","Nothing"},new String[]{"circle","gemini","none"},"circle");finishGroup(mNavHome);
        explanation(mNavBody,"Swipe gestures continue to work. Changes apply right away.");
        updateNavigationButtons();
    }
    private void navChoice(LinearLayout group,final String key,final String title,final String[] labels,final String[] values,final String fallback){
        String current=navValue(key,fallback);int selected=java.util.Arrays.asList(values).indexOf(current);if(selected<0)selected=java.util.Arrays.asList(values).indexOf(fallback);
        navigation(group,title,labels[Math.max(0,selected)],SystemToasts.KEY.equals(key)?"ic_ps_toast":NavButtons.LEFT.equals(key)?"ic_ps_nav_left":NavButtons.RIGHT.equals(key)?"ic_ps_nav_right":"ic_ps_nav_hold",()->{
            int choice=java.util.Arrays.asList(values).indexOf(navValue(key,fallback));
            showSettingsDialog(new AlertDialog.Builder(this).setTitle(title).setSingleChoiceItems(labels,Math.max(0,choice),(dialog,which)->{
                dialog.dismiss();mSaves++;final String value=values[which];IO.execute(()->{final boolean ok=Root.run("settings put secure "+key+" "+value)!=null;mMain.post(()->{if(isFinishing()||isDestroyed())return;if(ok){mNavSaved.put(key,value);mNavLabels.get(key).setText(labels[which]);updateNavigationButtons();}else{showRootBanner(true);AppToast.makeText(this,"Couldn't save: root access needed",Toast.LENGTH_SHORT).show();}});});
            }).create());
        });mNavLabels.put(key,navigationSummary(group));
    }
    private void updateNavigationButtons(){
        if(mNavBody==null)return;Switch toggle=mSwitches.get(NavButtons.KEY);boolean on=toggle!=null&&toggle.isChecked();mNavBody.setVisibility(on?View.VISIBLE:View.GONE);
        mNavHome.setVisibility(View.VISIBLE);
    }

    private void choice(LinearLayout group,String key,String title,String[] items){choice(group,key,title,items,0);}

    private void choice(LinearLayout group,final String key,final String title,final String[] items,final int offset){

        LinearLayout row=new LinearLayout(this);row.setOrientation(0);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(18),dp(22),dp(18),dp(22));row.setMinimumHeight(dp(96));

        ImageView icon=new ImageView(this);Drawable d=drawable(folderGridKey(key)?gridIcon(key):Feature.LOCK_SHORTCUTS.equals(key)?"ic_ps_lock_options":"ic_ps_single_tap");if(d!=null){d=d.mutate();d.setTint(mAccent);icon.setImageDrawable(d);}

        LinearLayout.LayoutParams iconParams=new LinearLayout.LayoutParams(dp(28),dp(28));iconParams.rightMargin=dp(18);row.addView(icon,iconParams);mChoiceIcons.put(key,icon);

        LinearLayout labels=new LinearLayout(this);labels.setOrientation(1);labels.addView(text(title,15,mTextSecondary,true));

        TextView value=text("Loading\u2026",18,mAccent,true);value.setPadding(0,dp(8),0,0);labels.addView(value);

        row.addView(labels,new LinearLayout.LayoutParams(0,-2,1));TextView arrow=text("\u203a",28,mAccent,true);arrow.setPadding(dp(12),0,0,0);row.addView(arrow);

        mChoiceLabels.put(key,value);mChoiceItems.put(key,items);mChoiceOffsets.put(key,offset);

        row.setClickable(true);row.setFocusable(true);row.setOnClickListener(new View.OnClickListener(){public void onClick(View v){

            IO.execute(new Runnable(){public void run(){final int[] current=Root.readSecure(new String[]{key});mMain.post(new Runnable(){public void run(){

                if(isFinishing()||isDestroyed())return;if(current==null){showRootBanner(true);return;}

                showSettingsDialog(new AlertDialog.Builder(SettingsActivity.this).setTitle(title).setSingleChoiceItems(items,Math.max(0,Math.min(items.length-1,current[0]-offset)),new DialogInterface.OnClickListener(){public void onClick(DialogInterface dialog,final int which){

                    dialog.dismiss();mSaves++;IO.execute(new Runnable(){public void run(){

                        String command="settings put secure "+key+" "+(which+offset);

                        if(Feature.DOUBLE_TAP_WAKE.equals(key))command+="; settings put secure doze_tap_gesture 1";

                        final boolean ok=Root.run(command)!=null;mMain.post(new Runnable(){public void run(){if(isFinishing()||isDestroyed())return;if(ok){updateChoiceLabel(key,which+offset);RestartPill.changed(key);if(mRestart!=null)mRestart.refresh();}else{showRootBanner(true);AppToast.makeText(SettingsActivity.this,"Could not save this option",0).show();}}});

                    }});

                }}).setNegativeButton("Cancel",null).create());

            }});}});

        }});group.addView(row,new LinearLayout.LayoutParams(-1,-2));

    }

    private void load() {

        java.util.LinkedHashSet<String> allKeys=new java.util.LinkedHashSet<String>(mSwitches.keySet());

        allKeys.addAll(mChoiceLabels.keySet());if(mStyleSlider!=null)allKeys.add(SuperDark.KEY);

        final String[] keys = allKeys.toArray(new String[0]);

        if(keys.length==0){initialReadDone("settings");return;}

        final int savesBefore = mSaves;

        IO.execute(new Runnable() {

            @Override

            public void run() {

                final int[] values = Root.readSecure(keys);

                if(values!=null)for(int i=0;i<keys.length;i++)if(PrivacyLock.GRACE_KEY.equals(keys[i])){

                    try{values[i]=PrivacyLock.graceOn(android.provider.Settings.Secure.getString(getContentResolver(),PrivacyLock.GRACE_KEY))?1:0;}catch(Throwable ignored){}

                }else if(MiscOptions.defaultOff(keys[i])){

                    try{values[i]="1".equals(android.provider.Settings.Secure.getString(getContentResolver(),keys[i]))?1:0;}catch(Throwable ignored){}

                }else if(PrivacyLock.ENABLED_KEY.equals(keys[i])||PrivacyLock.PROTECT_KEY.equals(keys[i])){

                    try{values[i]=PrivacyLock.onValue(android.provider.Settings.Secure.getString(getContentResolver(),keys[i]))?1:0;}catch(Throwable ignored){}

                }

                mMain.post(new Runnable() {

                    @Override

                    public void run() {

                        if (isFinishing() || isDestroyed()) return;

                        if (mSaves != savesBefore && values != null) {

                            load();

                            return;

                        }

                        if (values == null) {

                            showRootBanner(true);

                            for (Switch sw : mSwitches.values()) sw.setEnabled(false);

                            updateMiscPicker();

                            if(mLabelSlider!=null)mLabelSlider.setEnabled(false);

                            if(mStyleSlider!=null){mStyleLoaded=false;updateStyleControls();}

                            initialReadDone("settings");return;

                        }

                        showRootBanner(false);

                        mUpdating = true;

                        for(int j=0;j<keys.length;j++)if(SuperDark.KEY.equals(keys[j]))mStyleSuperDark=values[j]==1;

                        for (int i = 0; i < keys.length; i++) {

                            Switch sw = mSwitches.get(keys[i]);

                            if(sw!=null){if(PatronAccess.protectedKey(keys[i])&&!PatronAccess.allowed(SettingsActivity.this,keys[i]))values[i]=0;boolean checked=values[i]==1;sSwitchValues.put(keys[i],checked);if(sw.isChecked()!=checked)sw.setChecked(checked);sw.setVisibility(View.VISIBLE);sw.setEnabled(true);sw.jumpDrawablesToCurrentState();}

                            updateChoiceLabel(keys[i],values[i]);

                        }

                        updateMiscPicker();

                        mUpdating = false;if(mAppRoot!=null)PatronUi.apply(mAppRoot);initialReadDone("settings");

                    }

                });

            }

        });

    }

    private void save(final String key, final boolean on, final Switch sw) {

        if(mPrivacyGate!=null&&!mPrivacyGate.isOpen())return;

        if(PatronUi.intercept(this,key)){mUpdating=true;sw.setChecked(false);mUpdating=false;return;}

        if("pixelsuite_dna_watch".equals(key)&&on&&android.os.Build.VERSION.SDK_INT>=31&&checkSelfPermission("android.permission.BLUETOOTH_CONNECT")!=android.content.pm.PackageManager.PERMISSION_GRANTED){
            mUpdating=true;sSwitchValues.put(key,false);sw.setChecked(false);mUpdating=false;
            requestPermissions(new String[]{"android.permission.BLUETOOTH_CONNECT"},825);return;
        }

        mSaves++;

        IO.execute(new Runnable() {

            @Override

            public void run() {

                final boolean ok = Root.run(command(key, on)) != null;

                if(ok&&MiscSystem.A11Y_KEY.equals(key)){

                    java.util.List<String> apps=Root.run("settings get secure "+MiscSystem.A11Y_APPS_KEY);

                    if(apps!=null&&!apps.isEmpty())Root.run("true"+MiscOptions.stopCommands(MiscOptions.parse(apps.get(0))));

                }

                mMain.post(new Runnable() {

                    @Override

                    public void run() {

                        if (isFinishing() || isDestroyed()) return;

                        if (ok) {searchSaved(key,on);if(DoNotAnnoy.KEY.equals(key)){DoNotAnnoyTile.refresh(SettingsActivity.this);if(on)DoNotAnnoyPermissions.request(SettingsActivity.this);}updateMiscPicker();if(MiscSystem.DEV_KEY.equals(key))AppToast.makeText(SettingsActivity.this,"Reopen apps that were already open.",Toast.LENGTH_LONG).show();if(MiscSystem.A11Y_KEY.equals(key))AppToast.makeText(SettingsActivity.this,"Changes apply the next time the chosen apps open.",Toast.LENGTH_LONG).show();RestartPill.changed(key); if (mRestart != null) mRestart.refresh(); return; }

                        mUpdating = true;

                        sSwitchValues.put(key,!on);sw.setChecked(!on);searchSaved(key,!on);

                        updateMiscPicker();

                        mUpdating = false;

                        showRootBanner(true);

                        AppToast.makeText(SettingsActivity.this, "Couldn't save: root access needed",

                                Toast.LENGTH_SHORT).show();

                    }

                });

            }

        });

    }

    private static String command(String key, boolean on) {

        if(KeyboardBarTweak.KEY.equals(key))return MiscOptions.keyboardCommand(on);

        String put = "settings put secure " + key + " " + (on ? 1 : 0);

        if(RefreshRate90.KEY.equals(key))return put+" && am force-stop com.android.settings";

        if(ShareSheet.KEY.equals(key))return put+" && am force-stop "+ShareSheet.PACKAGE;

        if(GoogleFeedAds.KEY.equals(key))return put+" && am force-stop "+GoogleFeedAds.PACKAGE;

        if(WatchEsim.KEY.equals(key))return put+" && am force-stop "+WatchEsim.PACKAGE;

        if (Feature.FLASHLIGHT.equals(key) && on) {

            return put + "; settings put secure " + Flashlight.KEY_TARGET + " 0"

                    + "; settings put secure " + Flashlight.KEY_ENABLED + " 1";

        }

        if (Feature.CLEAR_ALL.equals(key)) {

            return put + "; am force-stop " + LAUNCHER;

        }

        if (SuperDark.KEY.equals(key)) return put + "; " + SuperDark.commands(on) + (on ? "" : "; if [ \"$(settings get secure " + SurfaceStyle.KEY + ")\" = 0 ]; then settings put global disable_window_blurs 1; fi; true");

        if (Feature.PHOTOS_DOODLES.equals(key)) return put + "; am force-stop " + PhotosEditor.PACKAGE;

        if (Feature.TAP_CHECK.equals(key) && on) return put + "; settings put secure doze_tap_gesture 1";

        if (Feature.SCREENSHOT_DOODLES.equals(key) || Feature.SCREENSHOT_FREE_CROP.equals(key)) {

            return put + "; am force-stop " + ScreenshotEditor.PACKAGE;

        }

        return put;

    }

    private void row(LinearLayout group, final String key, String icon, int iconColor,

                     String title, String summary) {

        LinearLayout row = new LinearLayout(this);

        row.setOrientation(LinearLayout.HORIZONTAL);

        row.setGravity(Gravity.CENTER_VERTICAL);

        row.setPadding(dp(18), dp(16), dp(16), dp(16));

        row.setMinimumHeight(dp(72));

        FrameLayout badge = new FrameLayout(this);

        GradientDrawable circle = new GradientDrawable();

        circle.setShape(GradientDrawable.OVAL);

        circle.setColor(sectionColor(icon));

        badge.setBackground(circle);

        ImageView iv = new ImageView(this);

        Drawable d = drawable(icon);

        if (d != null) { d = d.mutate(); d.setTint(sectionInk(icon)); iv.setImageDrawable(d); }

        badge.addView(iv, new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));

        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(dp(40), dp(40));

        blp.rightMargin = dp(16);

        if(MiscSystem.DEV_KEY.equals(key)){

            row.setPadding(dp(10),dp(16),dp(16),dp(16));

            FrameLayout target=new FrameLayout(this);target.setMinimumHeight(dp(48));

            target.addView(badge,new FrameLayout.LayoutParams(dp(40),dp(40),Gravity.CENTER));

            LinearLayout.LayoutParams targetParams=new LinearLayout.LayoutParams(dp(56),ViewGroup.LayoutParams.MATCH_PARENT);

            targetParams.rightMargin=dp(8);row.addView(target,targetParams);

            target.setClickable(true);target.setFocusable(true);

            target.setContentDescription("Open Developer options");

            iv.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);

            badge.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);

            target.setOnClickListener(new View.OnClickListener(){public void onClick(View v){openDeveloperOptions();}});

            mDeveloperGear=iv;

        }else row.addView(badge, blp);

        LinearLayout texts = new LinearLayout(this);

        texts.setOrientation(LinearLayout.VERTICAL);

        texts.addView(text(title, 17, mText, false));

        final TextView s = text(summary, 14, mTextSecondary, false);
        if(DoNotAnnoy.KEY.equals(key)){
            s.setLines(2);s.setHorizontallyScrolling(true);
            s.addOnLayoutChangeListener(new View.OnLayoutChangeListener(){public void onLayoutChange(View v,int l,int t,int r,int b,int oldL,int oldT,int oldR,int oldB){
                int width=s.getWidth()-s.getPaddingLeft()-s.getPaddingRight();if(width<=0)return;
                android.graphics.Paint fit=new android.graphics.Paint(s.getPaint());float size=14*getResources().getDisplayMetrics().scaledDensity;fit.setTextSize(size);
                float needed=0;for(String line:s.getText().toString().split("\n"))needed=Math.max(needed,fit.measureText(line));
                s.setTextSize(TypedValue.COMPLEX_UNIT_PX,needed>width?size*width/needed:size);
            }});
        }

        s.setPadding(0, dp(2), 0, 0);

        texts.addView(s);

        row.addView(texts, new LinearLayout.LayoutParams(0,

                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        final Switch sw = new Switch(this);

        sw.setThumbTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},

                new int[]{mAccent, mTextSecondary}));

        tintSwitchTrack(key,sw);

        sw.setContentDescription(title);

        Boolean known=sSwitchValues.get(key);

        if(known!=null){sw.setChecked(known);sw.jumpDrawablesToCurrentState();}

        else sw.setVisibility(View.INVISIBLE);

        sw.setEnabled(known!=null);

        sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {

            @Override

            public void onCheckedChanged(CompoundButton b, boolean on) {

                if (!mUpdating) {sSwitchValues.put(key,on);save(key, on, sw);updateMiscPicker();}

            }

        });

        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(

                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);

        slp.leftMargin = dp(12);

        row.addView(sw, slp);

        mSwitches.put(key, sw);

        row.setTag("search.anchor:"+key);row.setClickable(true);

        row.setFocusable(true);

        row.setOnClickListener(new View.OnClickListener() {

            @Override

            public void onClick(View v) {

                if(PatronUi.intercept(SettingsActivity.this,key))return;

                if (sw.isEnabled()) sw.toggle();

            }

        });

        row.setContentDescription(title);

        PatronUi.decorate(this,row,key);

        group.addView(row, new LinearLayout.LayoutParams(

                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

    }

    private void startDeveloperGear(){

        if(mDeveloperGear==null||mGearRotation!=null)return;

        float angle=mDeveloperGear.getRotation();

        mGearRotation=android.animation.ObjectAnimator.ofFloat(mDeveloperGear,"rotation",angle,angle+360f);

        mGearRotation.setDuration(14000L);

        mGearRotation.setInterpolator(new android.view.animation.LinearInterpolator());

        mGearRotation.setRepeatCount(android.animation.ValueAnimator.INFINITE);

        mGearRotation.start();

    }

    private void stopDeveloperGear(){

        if(mGearRotation==null)return;

        mGearRotation.cancel();mGearRotation=null;

        if(mDeveloperGear!=null)mDeveloperGear.setRotation(mDeveloperGear.getRotation()%360f);

    }

    private void openDeveloperOptions(){

        try{startActivity(new Intent("android.settings.APPLICATION_DEVELOPMENT_SETTINGS"));}

        catch(android.content.ActivityNotFoundException unavailable){

            AppToast.makeText(this,"Developer options aren't available on this device.",Toast.LENGTH_SHORT).show();

        }

    }

    private TextView navigationSummary(LinearLayout group){return (TextView)((LinearLayout)((LinearLayout)group.getChildAt(group.getChildCount()-1)).getChildAt(1)).getChildAt(1);}

    private void refreshBlockerSummary(){

        if(mStoreBlockedCount==null)return;

        IO.execute(new Runnable(){public void run(){

            final java.util.List<String> values=Root.run("settings get secure "+StoreBlocker.KEY+"; settings get secure "+OtaBlocker.KEY+"; settings get secure "+StoreBlocker.UNLOCK_KEY);

            mMain.post(new Runnable(){public void run(){if(isFinishing()||isDestroyed())return;if(values==null||values.size()<3){mStoreBlockedCount.setText("Couldn't load apps");mOtaStatus.setText("Unavailable");initialReadDone("blocker_summary");return;}

                java.util.Set<String> blocked="null".equals(values.get(0))?new java.util.HashSet<String>(java.util.Arrays.asList(StoreBlocker.CORE)):MiscOptions.parse(values.get(0));

                if(!"1".equals(values.get(2)))blocked.addAll(java.util.Arrays.asList(StoreBlocker.CORE));
                int count=0;for(String pkg:blocked)try{getPackageManager().getApplicationInfo(pkg,0);count++;}catch(Exception missing){}

                mStoreBlockedCount.setText(count+" apps protected from updates");mOtaStatus.setText("1".equals(values.get(1))?"On":"Off");initialReadDone("blocker_summary");

            }});

        }});

    }

    private void updateMiscPicker(){
        updateNavigationButtons();

        Switch ota=mSwitches.get(OtaBlocker.KEY),reset=mSwitches.get(OtaBlocker.RESET_KEY);

        if(ota!=null&&reset!=null){boolean enabled=ota.isEnabled()&&ota.isChecked();reset.setEnabled(enabled);View row=(View)reset.getParent();row.setAlpha(enabled?1f:.4f);}

        if(mStoreBlockerPage!=null){Switch unlock=mSwitches.get(StoreBlocker.UNLOCK_KEY);if(unlock!=null)mStoreBlockerPage.unlockChanged(unlock.isChecked());}

        if(mCallRecordingSettings!=null){Switch recording=mSwitches.get(CallRecording.KEY);mCallRecordingSettings.enabled(recording!=null&&recording.isEnabled()&&recording.isChecked());}

        if(mMiscChooseApps==null)return;

        Switch toggle=mSwitches.get(MiscSystem.A11Y_KEY);

        boolean enabled=toggle!=null&&toggle.isEnabled()&&toggle.isChecked();

        mMiscChooseApps.setEnabled(enabled);mMiscChooseApps.setClickable(enabled);mMiscChooseApps.setAlpha(enabled?1f:.4f);

    }

    private TextView banner() {

        TextView b = text("Pixel Suite needs root access to change these settings. Allow it in KernelSU, then reopen this screen.", 14, mDark ? 0xFFFFB4AB : 0xFF8C1D18, false);

        GradientDrawable bg = new GradientDrawable();

        bg.setCornerRadius(dp(20));

        bg.setColor(mDark ? 0xFF5C1F1A : 0xFFFCDAD6);

        b.setBackground(bg);

        b.setPadding(dp(18), dp(14), dp(18), dp(14));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(

                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);

        lp.bottomMargin = dp(8);

        b.setLayoutParams(lp);

        return b;

    }

    private static String gridIcon(String key){return LauncherFeatures.DRAWER_GRID_SIZE.equals(key)?"ic_ps_drawer_grid":"ic_ps_home_grid";}

    private static boolean folderGridKey(String key){return LauncherFeatures.GRID_SIZE.equals(key)||LauncherFeatures.DRAWER_GRID_SIZE.equals(key);}

    private void showRootBanner(boolean show) {

        mBanner.setVisibility(show ? View.VISIBLE : View.GONE);

    }

    private LinearLayout homeSection(LinearLayout page,String label){

        TextView heading=text(label,14,mTextSecondary,true);

        heading.setPadding(dp(8),dp(4),dp(8),dp(8));page.addView(heading);

        return group(page);

    }

    private void section(LinearLayout page, String label) {

        TextView t = text(label, 14, mAccent, true);

        t.setPadding(dp(8), dp(16), dp(8), dp(8));

        t.setTag("ps_section");page.addView(t);

    }

    private LinearLayout group(LinearLayout page) {

        LinearLayout g = new LinearLayout(this);

        g.setOrientation(LinearLayout.VERTICAL);

        LinearLayout.LayoutParams layout=new LinearLayout.LayoutParams(-1,-2);layout.bottomMargin=dp(12);

        page.addView(g,layout);

        return g;

    }

    private void finishGroup(LinearLayout g) {

        int n = g.getChildCount();

        for (int i = 0; i < n; i++) {

            float big = dp(24), small = dp(6);

            float top = i == 0 ? big : small;

            float bottom = i == n - 1 ? big : small;

            View row = g.getChildAt(i);

            row.setBackground(rowBackground(top, bottom));

            if (i > 0) {

                ((LinearLayout.LayoutParams) row.getLayoutParams()).topMargin = dp(3);

            }

        }

    }

    private Drawable rowBackground(float top, float bottom) {

        GradientDrawable shape = AppSurfaces.card(this,24);

        shape.setColor(mCard);

        shape.setCornerRadii(new float[]{top, top, top, top, bottom, bottom, bottom, bottom});

        GradientDrawable mask = new GradientDrawable();

        mask.setColor(Color.WHITE);

        mask.setCornerRadii(new float[]{top, top, top, top, bottom, bottom, bottom, bottom});

        int ripple = mDark ? 0x33FFFFFF : 0x1F000000;

        return new RippleDrawable(ColorStateList.valueOf(ripple), shape, mask);

    }

    private TextView text(String s, float sp, int color, boolean medium) {

        TextView t = new TextView(this);

        t.setText(SettingsText.format(s));

        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);

        t.setTextColor(color);

        if (medium) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));

        return t;

    }

    private void resolvePalette() {

        AppSurfaces.refresh(this);mAppSuperDark=AppSurfaces.active(this);

        if (mDark) {

            mBg = sys("system_surface_container_dark", 0xFF211F26);

            mCard = sys("system_surface_container_high_dark", 0xFF2B2930);

            mText = sys("system_on_surface_dark", 0xFFE6E1E5);

            mTextSecondary = sys("system_on_surface_variant_dark", 0xFFCAC4D0);

            mAccent = sys("system_primary_dark", 0xFFD0BCFF);

            mOnAccent = sys("system_on_primary_dark", 0xFF381E72);

        } else {

            mBg = sys("system_surface_container_light", 0xFFF3EDF7);

            mCard = sys("system_surface_bright_light", 0xFFFEF7FF);

            mText = sys("system_on_surface_light", 0xFF1D1B20);

            mTextSecondary = sys("system_on_surface_variant_light", 0xFF49454F);

            mAccent = sys("system_primary_light", 0xFF6750A4);

            mOnAccent = sys("system_on_primary_light", 0xFFFFFFFF);

        }

        if(mAppSuperDark){mBg=0xff000000;mCard=0xff0b0b0b;}

    }

    private int sectionFamily(String icon){

        String page=getIntent().getStringExtra("page");FeaturePage f=feature(page);if(f!=null)page=f.group;

        return SectionColors.family(page,icon);

    }

    private int sectionColor(String icon){return SectionColors.background(sectionFamily(icon),mDark);}

    private int sectionInk(String icon){return SectionColors.ink(sectionFamily(icon));}

    private Drawable googleIcon(int color) throws android.content.pm.PackageManager.NameNotFoundException {

        return AppBrandIcons.load(this,GoogleFeedAds.PACKAGE,color);

    }

    private void googleNavigationIcon(LinearLayout group){

        try{((ImageView)((LinearLayout)group.getChildAt(group.getChildCount()-1)).getChildAt(0)).setImageDrawable(googleIcon(sectionInk("ic_ps_google_apps")));}catch(Exception missing){}

    }

    private void appNavigation(LinearLayout group,String title,String summary,String pkg,String fallback,final String page){

        navigation(group,title,summary,fallback,new Runnable(){public void run(){openPage(page);}});

        try{ImageView icon=(ImageView)((LinearLayout)group.getChildAt(group.getChildCount()-1)).getChildAt(0);

            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);icon.setImageDrawable(AppBrandIcons.load(this,pkg,sectionInk(fallback)));

        }catch(Exception missing){}

    }

    private final class OemSwitchTrack extends Drawable {

        private final Drawable original;

        private final android.graphics.Paint paint=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);

        private final android.graphics.RectF bar=new android.graphics.RectF();

        OemSwitchTrack(Drawable original){this.original=original;}

        @Override public boolean isStateful(){return true;}

        @Override protected boolean onStateChange(int[] state){invalidateSelf();return true;}

        @Override public int getIntrinsicWidth(){return original!=null?original.getIntrinsicWidth():dp(36);}

        @Override public int getIntrinsicHeight(){return original!=null?original.getIntrinsicHeight():dp(32);}

        @Override public boolean getPadding(android.graphics.Rect padding){return original!=null&&original.getPadding(padding);}

        @Override public void draw(android.graphics.Canvas canvas){

            boolean checked=false;for(int state:getState())if(state==android.R.attr.state_checked)checked=true;

            paint.setColor(checked?0xffff0000:mAppSuperDark?0xff1a1a1a:mDark?0xff49454f:0xffcac4d0);

            android.graphics.Rect bounds=getBounds();float half=dp(6);bar.set(bounds.left,bounds.exactCenterY()-half,bounds.right,bounds.exactCenterY()+half);

            canvas.drawRoundRect(bar,half,half,paint);

        }

        @Override public void setAlpha(int alpha){paint.setAlpha(alpha);invalidateSelf();}

        @Override public void setColorFilter(android.graphics.ColorFilter filter){}

        @Override public int getOpacity(){return android.graphics.PixelFormat.TRANSLUCENT;}

    }

    int switchTrackOff(){return mAppSuperDark?0xff1a1a1a:mDark?0xff49454f:0xffcac4d0;}

    private void tintSwitchTrack(String key,Switch sw){

        if(!StoreBlocker.UNLOCK_KEY.equals(key)){SwitchStyle.apply(sw,mAccent,switchTrackOff(),mOnAccent,mTextSecondary);return;}

        sw.setThumbTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{mOnAccent,mTextSecondary}));

        if(StoreBlocker.UNLOCK_KEY.equals(key)){

            if(!(sw.getTrackDrawable() instanceof OemSwitchTrack)){sw.setTrackTintList(null);sw.setTrackDrawable(new OemSwitchTrack(sw.getTrackDrawable()));}

            sw.getTrackDrawable().setState(sw.getDrawableState());sw.invalidate();return;

        }

        sw.setTrackTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{mAccent,switchTrackOff()}));

    }

    private boolean searchComplex(String key){
        return key==null||key.isEmpty()||key.startsWith("pixelsuite_privacy")||key.equals(PrivacyLock.ENABLED_KEY)||key.equals(PrivacyLock.PROTECT_KEY)||key.equals(PrivacyLock.GRACE_KEY)||key.equals(DoNotAnnoy.KEY)||key.equals("pixelsuite_dna_watch")||key.equals(StoreBlocker.UNLOCK_KEY)||key.equals(OtaBlocker.KEY)||key.equals(OtaBlocker.RESET_KEY)||key.equals(MiscSystem.A11Y_KEY)||key.equals(WatchEsim.KEY);
    }
    private void loadSearchStates(){
        if(mSearchStatesLoading||(mSearchRootReady&&android.os.SystemClock.uptimeMillis()-mSearchStatesAt<2000))return;
        mSearchStatesLoading=true;mSearchStatesAt=android.os.SystemClock.uptimeMillis();
        IO.execute(()->{try{
            AppSearchIndex index=AppSearchIndex.load(getApplicationContext());java.util.Set<String> unique=new java.util.LinkedHashSet<String>();
            for(AppSearchIndex.Entry e:index.entries)if("TOGGLE".equals(e.kind)&&!e.key.isEmpty())unique.add(e.key);
            String[] keys=unique.toArray(new String[0]);java.util.List<String> stored=Root.run("settings list secure");
            final int[] values=stored==null?null:new int[keys.length];
            if(stored!=null){java.util.Map<String,String> selected=new java.util.HashMap<String,String>();for(String line:stored){int split=line.indexOf('=');if(split>0&&unique.contains(line.substring(0,split)))selected.put(line.substring(0,split),line.substring(split+1));}
                for(int i=0;i<keys.length;i++){String raw=selected.get(keys[i]);try{values[i]=Integer.parseInt(raw);}catch(Exception unset){values[i]=Feature.defaultFor(keys[i]);}
                    if(PrivacyLock.ENABLED_KEY.equals(keys[i])||PrivacyLock.PROTECT_KEY.equals(keys[i]))values[i]=PrivacyLock.onValue(raw)?1:0;
                    else if(PrivacyLock.GRACE_KEY.equals(keys[i]))values[i]=PrivacyLock.graceOn(raw)?1:0;
                    else if(MiscOptions.defaultOff(keys[i]))values[i]="1".equals(raw)?1:0;
                }selected.clear();stored.clear();}
            mMain.post(()->{mSearchStatesLoading=false;if(isFinishing()||isDestroyed())return;mSearchRootReady=values!=null;
                if(values!=null)for(int i=0;i<keys.length;i++)sSwitchValues.put(keys[i],values[i]==1&&(!PatronAccess.protectedKey(keys[i])||PatronAccess.allowed(SettingsActivity.this,keys[i])));
                if(mSearch!=null)mSearch.palette();});
        }catch(Exception ignored){mMain.post(()->mSearchStatesLoading=false);}});
    }
    private void searchSaved(String key,boolean on){
        mUpdating=true;Switch original=mSwitches.get(key);if(original!=null&&original.isChecked()!=on)original.setChecked(on);mUpdating=false;
        if(mSearch!=null)mSearch.stateChanged(key);
    }
    @Override public void onBackPressed(){if(mSearch!=null&&mSearch.back())return;super.onBackPressed();}
    @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);if(mSearch!=null){out.putBoolean("search_open",mSearch.isOpen());out.putString("search_query",mSearch.query());}}
    @Override public void onWindowFocusChanged(boolean focus){
        super.onWindowFocusChanged(focus);
        if(mSearch==null)return;
        if(focus){mSearch.setVisibility(View.VISIBLE);return;}
        mMain.post(()->{
            if(mSearch==null||hasWindowFocus())return;
            boolean authentication=mPrivacyGate!=null&&!mPrivacyGate.isOpen();
            mSearch.setVisibility(!authentication&&(mPageNavigation||!mPageResumed)?View.VISIBLE:View.INVISIBLE);
        });
    }
    private void revealSearchAnchor(){
        String anchor=getIntent().getStringExtra("search_anchor");if(anchor==null||mPageScroll==null)return;getIntent().removeExtra("search_anchor");
        if(anchor.equals("@PatronAccount")){PatronAccount.open(this);return;}
        if(anchor.equals("@PatronPaper")){PatronPaper.open(this,0,null);return;}
        if(anchor.equals("@EsimPurchase")){EsimPurchase.open(this);return;}
        View found=findSearchAnchor(mPageScroll,anchor);if(found==null&&mSwitches.containsKey(anchor))found=(View)mSwitches.get(anchor).getParent();if(found==null)return;
        revealView(found);
    }
    private void revealView(View found){
        if(found==null||mPageScroll==null)return;
        java.util.List<View> chain=new java.util.ArrayList<View>();
        for(View v=found;v!=null&&v!=mPageScroll;v=v.getParent() instanceof View?(View)v.getParent():null)chain.add(v);
        View row=chain.size()>=3?chain.get(chain.size()-3):found;
        final View hit=row;
        mPageScroll.postDelayed(()->{if(!hit.isAttachedToWindow())return;android.graphics.Rect bounds=new android.graphics.Rect();hit.getDrawingRect(bounds);mPageScroll.offsetDescendantRectToMyCoords(hit,bounds);
            mPageScroll.smoothScrollTo(0,Math.max(0,bounds.centerY()-mPageScroll.getHeight()/2));
            final android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable();bg.setCornerRadius(dp(20));bg.setColor((mAccent&0xffffff)|0x40000000);bg.setStroke(dp(2),mAccent);
            hit.getOverlay().add(bg);bg.setBounds(0,0,hit.getWidth(),hit.getHeight());bg.setAlpha(0);
            android.animation.ValueAnimator pulse=android.animation.ValueAnimator.ofFloat(0,1,0.45f,1,0.45f,1,0);pulse.setStartDelay(250);pulse.setDuration(1600);pulse.addUpdateListener(v->bg.setAlpha(Math.round(255*(Float)v.getAnimatedValue())));pulse.addListener(new android.animation.AnimatorListenerAdapter(){public void onAnimationEnd(android.animation.Animator a){hit.getOverlay().remove(bg);}});pulse.start();
        },180);
    }
    private View findSearchAnchor(View root,String anchor){
        View found=findSearchAnchor(root,anchor,0);if(found!=null)return found;
        found=findSearchAnchor(root,anchor,1);return found!=null?found:findSearchAnchor(root,anchor,2);
    }
    private View findSearchAnchor(View v,String anchor,int pass){
        if(pass==0&&("search.anchor:"+anchor).equals(v.getTag()))return v;
        if(pass>0&&v instanceof TextView){String text=((TextView)v).getText().toString();String wanted=SettingsText.format(anchor).toString();if(pass==1?text.equals(wanted):!wanted.isEmpty()&&text.startsWith(wanted))return v;}
        if(v instanceof ViewGroup){ViewGroup group=(ViewGroup)v;for(int i=0;i<group.getChildCount();i++){View found=findSearchAnchor(group.getChildAt(i),anchor,pass);if(found!=null)return found;}}
        return null;
    }

    private void refreshAppSurfaces(){

        AppSurfaces.refresh(this);boolean now=AppSurfaces.active(this);if(now==mAppSuperDark)return;

        resolvePalette();styleWindow();if(mAppRoot!=null){mAppRoot.setBackgroundColor(mBg);if(mPageScroll!=null)mPageScroll.setBackgroundColor(mBg);AppSurfaces.invalidateTree(mAppRoot);}

        for(Map.Entry<String,Switch> entry:mSwitches.entrySet())tintSwitchTrack(entry.getKey(),entry.getValue());

        for(AlertDialog dialog:new java.util.ArrayList<AlertDialog>(mSettingsDialogs))if(dialog.isShowing())styleSettingsWindow(dialog.getWindow());

        if(mRestart!=null)mRestart.refreshPalette();if(mSearch!=null)mSearch.palette();

    }

    @SuppressWarnings("deprecation")

    private void styleWindow() {

        Window w = getWindow();

        w.setBackgroundDrawable(new ColorDrawable(mBg));

        w.setStatusBarColor(Color.TRANSPARENT);

        w.setNavigationBarColor(Color.TRANSPARENT);

        View decor = w.getDecorView();

        int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN

                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;

        if (!mDark) {

            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;

            flags |= 0x00000010;

        }

        decor.setSystemUiVisibility(flags);

    }

    private int sys(String name, int fallback) {

        try {

            int id = getResources().getIdentifier(name, "color", "android");

            if (id != 0) return getResources().getColor(id, getTheme());

        } catch (Throwable ignored) { }

        return fallback;

    }

    private Drawable drawable(String name) {

        try {

            int id = getResources().getIdentifier(name, "drawable", getPackageName());

            if (id != 0) return getResources().getDrawable(id, getTheme());

        } catch (Throwable ignored) { }

        return null;

    }

    private String versionName() {

        try {

            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;

        } catch (Throwable t) {

            return "";

        }

    }

    private int dp(float v) {

        return Math.round(v * mDp);

    }

}
