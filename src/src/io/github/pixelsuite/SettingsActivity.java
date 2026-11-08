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
    private BackupRestorePage mBackupPage;
    private TextView mBackupSummary;

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

    private static final ExecutorService STORE_IO = new ThreadPoolExecutor(0, 1, 10L,
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
        I18n.refresh(this);

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

            ImageView suiteLogo=new ImageView(this);suiteLogo.setImageDrawable(HomeLinks.suiteMark());suiteLogo.setContentDescription(I18n.cs("Pixel Suite logo"));

            LinearLayout.LayoutParams logoSize=new LinearLayout.LayoutParams(dp(30),dp(30));logoSize.leftMargin=dp(7);titleLine.addView(suiteLogo,logoSize);

            heading.addView(titleLine);

        }else if(premiumPage(pageId)){
            title.setIncludeFontPadding(false);
            LinearLayout titleLine=new LinearLayout(this){
                private final android.graphics.Rect inkBounds=new android.graphics.Rect();
                @Override protected void onLayout(boolean changed,int l,int t,int r,int b){
                    super.onLayout(changed,l,t,r,b);if(getChildCount()!=2)return;
                    TextView label=(TextView)getChildAt(0);View logo=getChildAt(1);String value=label.getText().toString();
                    label.getPaint().getTextBounds(value,0,value.length(),inkBounds);
                    float center=label.getTop()+label.getBaseline()+(inkBounds.top+inkBounds.bottom)/2f;
                    logo.setTranslationY(center-logo.getTop()-logo.getHeight()/2f);
                }
            };titleLine.setBaselineAligned(false);titleLine.setGravity(Gravity.CENTER_VERTICAL);
            titleLine.addView(title,new LinearLayout.LayoutParams(-2,-2));
            ImageView badge=new DollarBadgeView(this);badge.setTag("patron.header");badge.setContentDescription(I18n.cs("Patreon feature"));
            LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(dp(30),dp(38));bp.leftMargin=dp(2);titleLine.addView(badge,bp);
            heading.addView(titleLine,new LinearLayout.LayoutParams(-1,-2));
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
        if(premiumPage(pageId))dropRowBadges(page);

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

        setContentView(root);Rtl.activity(this);mAppRoot=root;root.setBackgroundColor(mBg);TapFeedback.watch(root);
        scroll.setBackgroundColor(mBg);mPageScroll=scroll;getWindow().setSoftInputMode(0x30);
        int searchClearance=dp(84)+Math.max(0,Math.round((getResources().getConfiguration().fontScale-1)*dp(20)));
        page.setPadding(page.getPaddingLeft(),page.getPaddingTop(),page.getPaddingRight(),searchClearance);
        if(pageId==null||"home".equals(pageId)){
            mLanguage=new LanguagePill(this,scroll,new LanguagePill.Host(){
                public int background(){return mBg;}public int primary(){return mText;}public int secondary(){return mTextSecondary;}public int accent(){return mAccent;}
                public void picked(String code){languagePicked(code);}
            });
            root.addView(mLanguage,new FrameLayout.LayoutParams(-1,-1));
        }
        if(sLanguageFade){sLanguageFade=false;root.setAlpha(0f);root.animate().alpha(1f).setDuration(320).setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f)).start();}
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

        if(mStoreBlockedCount!=null)mInitialReads.add("blocker_summary");

        root.getViewTreeObserver().addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener(){public boolean onPreDraw(){

            if(!mInitialReads.isEmpty())return false;

            root.getViewTreeObserver().removeOnPreDrawListener(this);return true;

        }});

        mPaletteObserver=new android.database.ContentObserver(mMain){@Override public void onChange(boolean self){SurfaceStyle.refresh();refreshAppSurfaces();if(mSearch!=null)mSearch.palette();if(mLanguage!=null)mLanguage.palette();}};

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
        if(!GmsScope.checked){GmsScope.checked=true;GmsScope.onRemoved=new Runnable(){public void run(){mMain.post(new Runnable(){public void run(){showGmsScopeDialog(true);}});}};IO.execute(new Runnable(){public void run(){final boolean reboot=GmsScope.needsReboot(SettingsActivity.this);final boolean scoped=!reboot&&GmsScope.scoped(SettingsActivity.this);if(reboot||scoped)mMain.postDelayed(new Runnable(){public void run(){showGmsScopeDialog(reboot);}},1500);}});}

    }

    private void resumeSettingsContent(){
        if(getIntent().getBooleanExtra("request_dna_permissions",false)){
            getIntent().removeExtra("request_dna_permissions");mMain.post(new Runnable(){public void run(){if(!isFinishing()&&!isDestroyed())DoNotAnnoyPermissions.request(SettingsActivity.this);}});
        }

        if (mRestart != null) mRestart.refresh();

        final boolean firstEntitlement=!PatronAccess.initialized();

        if(firstEntitlement)mInitialReads.add("entitlement");

        PatronAccess.resume(getApplicationContext(),firstEntitlement?new Runnable(){public void run(){mInitialReads.add("settings");mPatronChanged.run();initialReadDone("entitlement");}}:null);

        load();if(mIconPackPage!=null)mIconPackPage.refresh();if(mCallRecordingSettings!=null)mCallRecordingSettings.reload();if(mStoreBlockerPage!=null)mStoreBlockerPage.resume();if(mBackupPage!=null)mBackupPage.resume();if(mBackupSummary!=null)BackupRestorePage.summarize(this,STORE_IO,mBackupSummary);refreshBlockerSummary();

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

            AppToast.makeText(this,I18n.cs("Set a phone screen lock first"),Toast.LENGTH_LONG).show();finish();return;

        }

        mPrivacyFailures=0;

        try{

            android.hardware.biometrics.BiometricPrompt.Builder b=privacyPrompt();

            b.setAllowedAuthenticators(0x00ff);b.setConfirmationRequired(false);

            b.setNegativeButton("PIN",privacyExecutor(),new DialogInterface.OnClickListener(){public void onClick(DialogInterface dialog,int which){privacyCredentialLater(attempt);}});

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

        b.setTitle(I18n.cs("Privacy & Security"));b.setDescription("Confirm it's you to change privacy settings");return b;

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
    @Override protected void onDestroy(){GmsScope.onRemoved=null;if(mSearch!=null)mSearch.dispose();PatronAccess.unwatch(mPatronChanged);if(mIconPackPage!=null)mIconPackPage.dispose();if(mStoreBlockerPage!=null)mStoreBlockerPage.dispose();stopDeveloperGear();if(mPaletteObserver!=null)getContentResolver().unregisterContentObserver(mPaletteObserver);if(mDnaObserver!=null)getContentResolver().unregisterContentObserver(mDnaObserver);if(mPrivacyGate!=null){mPrivacyGate.stop();if(mPrivacyAuthSignal!=null)mPrivacyAuthSignal.cancel();}if(mRestart!=null)mRestart.dispose();for(AlertDialog dialog:new java.util.ArrayList<AlertDialog>(mSettingsDialogs))dialog.dismiss();for(FeaturePreview preview:mPreviews)preview.dispose();super.onDestroy();}

    private static boolean sPropChecked;

    private static final class FeaturePage {

        final String group,key,icon,title,summary;

        FeaturePage(String group,String key,String icon,String title,String summary){this.group=group;this.key=key;this.icon=icon;this.title=title;this.summary=summary;}

    }

    private static final FeaturePage[] PAGES={

        new FeaturePage("buttons",Feature.FLASHLIGHT,"ic_ps_power_double","Double-press power button","Turn the flashlight on or off, open the camera (stock), or do nothing."),

        new FeaturePage("buttons",Feature.VOLDOWN_CAMERA,"ic_ps_volume_double","Double-press volume down","With the screen off, open the camera or turn the flashlight on or off."),

        new FeaturePage("buttons",PowerHold.KEY,"ic_ps_power_hold","Hold power button","Gemini, Circle to Search, a screenshot or a quiet mode."),

        new FeaturePage("gestures",Feature.THREE_FINGER,"ic_ps_threefinger","Three-finger screenshot","Swipe down with three fingers to take a screenshot."),

        new FeaturePage("gestures",ScreenRecordTidy.KEY,"ic_ps_screen_record","Screen recording sheet","A tidier recording sheet. Needs your phone in English."),

        new FeaturePage("gestures",Feature.SCREENSHOT_FREE_CROP,"ic_ps_crop_free","Free screenshot crop","Crop screenshots to any size, even a single line."),

        new FeaturePage("doodles",Feature.SCREENSHOT_DOODLES,"ic_ps_studio_tools","Pixel Studio","Draw shapes and arrows on screenshots."),

        new FeaturePage("doodles",Feature.PHOTOS_DOODLES,"ic_ps_photos_doodles","Google Photos","Draw shapes on photos: Edit → Doodles. They stay editable."),

        new FeaturePage("gestures",Feature.DOUBLE_TAP_SLEEP,"ic_ps_sleep","Double tap to sleep","Double-tap an empty spot on the home screen to lock your phone."),

        new FeaturePage("gestures",Feature.TAP_CHECK,"ic_ps_tapwake_toggle","Tap to wake","Wake your phone with a tap, with pocket protection."),

        new FeaturePage("launcher",LauncherFeatures.GRID,"ic_ps_folder_grid","Folder grid","Folders grow with the number of apps inside. Pick a grid from each folder's three-dot menu."),

        new FeaturePage("drawer",LauncherFeatures.DRAWER,"ic_ps_drawer_folder","App drawer folders","Place folders anywhere among your apps, with their own grids and sorting."),

        new FeaturePage("drawer",LauncherFeatures.DRAG_PILL,"ic_ps_home_drop","Drag to home screen","Hold an app in the drawer and drag it onto Add to home screen."),

        new FeaturePage("themes",LauncherFeatures.ICONS,"ic_ps_change_icon","Change icons","Hold an app and choose Change icon to use your own image."),

        new FeaturePage("gestures",FolderDrag.TWO_FINGER,"ic_ps_two_finger","Two-finger drag","While holding an app or folder, swipe with another finger to scroll or turn pages."),

        new FeaturePage("gestures",MultiDrag.KEY,"ic_ps_multi_drag","Move several apps","Start dragging an app, then tap more apps with another finger. Drop them all on the home screen or into a folder."),

        new FeaturePage("launcher",LauncherFeatures.RENAME,"ic_ps_rename","Rename apps","Hold an app and choose Rename."),

        new FeaturePage("launcher",LauncherFeatures.LABEL_SIZE,"ic_ps_label_size","Icon label size","Five text sizes for app names. Icons stay the same size."),

        new FeaturePage("launcher",LauncherFeatures.HIDE_SEARCH,"ic_ps_search_bar","Hide search bar","Removes the search bar from the bottom of the home screen. The dock moves down and the apps use the space."),

        new FeaturePage("launcher","clear_badge","ic_ps_clear_badge","Clear badge","Hold an app or folder with a notification dot and choose Clear badge."),

        new FeaturePage("recents",Feature.CLEAR_ALL,"ic_ps_clearall","Clear all in recents","Clear all is always there in recents, no scrolling."),

        new FeaturePage("recents",RecentsLock.KEY,"ic_ps_recents_lock","Lock apps in recents","Lock an app's card so Clear all keeps it."),

        new FeaturePage("drawer",LauncherFeatures.HIDE_SHORTCUTS,"ic_ps_shortcuts","Hide app shortcuts","Hides app shortcuts like View orders from the drawer menu."),

        new FeaturePage("themes",SuperDark.KEY,"ic_ps_super_dark","Super Dark-Mode","Pure black backgrounds and darker menus in dark theme."),

        new FeaturePage("files",Feature.FILES_SORT,"ic_ps_files","Newest first","Folders first, then your newest files."),
        new FeaturePage("gestures",NavButtons.KEY,"ic_ps_navigation","Navigation buttons","Choose Back, Home or Recent beside the gesture bar. Hold Home for Circle to Search or Gemini.")

    };

    private final Map<String,TextView> mChoiceLabels=new LinkedHashMap<String,TextView>();

    private final Map<String,Integer> mChoiceOffsets=new LinkedHashMap<String,Integer>();

    private final Map<String,ImageView> mChoiceIcons=new LinkedHashMap<String,ImageView>();

    private final Map<String,String[]> mChoiceItems=new LinkedHashMap<String,String[]>();

    private FeaturePage feature(String id){for(FeaturePage f:PAGES)if(f.key.equals(id))return f;return null;}

    private static boolean premiumPage(String id){
        return "gboard".equals(id)||"gboard_autocorrect".equals(id)||"chrome".equals(id)||"extensions".equals(id)||"ad_blocker".equals(id)
                ||"vpn".equals(id)||"dark_pages".equals(id)||"google_dialer".equals(id)||"google_app".equals(id);
    }
    private static void dropRowBadges(View v){
        if(!(v instanceof ViewGroup))return;
        ViewGroup g=(ViewGroup)v;
        for(int i=g.getChildCount()-1;i>=0;i--){View c=g.getChildAt(i);if("patron.badge".equals(c.getTag()))g.removeViewAt(i);else dropRowBadges(c);}
    }
    int pageColor(){return mBg;}
    private String pageTitle(String id){

        FeaturePage f=feature(id);if(f!=null)return f.title;

        if("buttons".equals(id))return "Buttons";if("gestures".equals(id))return "Gestures";

        if("doodles".equals(id))return "Doodles";
        if("aod".equals(id))return "Always-on display";

        if("drawer".equals(id))return "App drawer";if("launcher".equals(id))return "Pixel Launcher";if("files".equals(id))return "Files";if("themes".equals(id))return "Themes";

        if("privacy".equals(id))return "Privacy & Security";

        if("do_not_annoy".equals(id))return "Do Not Annoy";
        if("share_menu".equals(id))return "Share menu";

        if("recents".equals(id))return "Recent Apps";

        if("blockers".equals(id))return "OTA & Store blocker";

        if("store_blocker".equals(id))return "Play Store blocker";

        if("store_backups".equals(id))return "Backup & Restore";

        if("ota_blocker".equals(id))return "OTA blocker";

        if("google_apps".equals(id))return "Google apps";

        if("vpn".equals(id))return "VPN";

        if("extensions".equals(id))return "Extensions";if("ad_blocker".equals(id))return "Ad Blocker";
        if("dark_pages".equals(id))return "Dark pages";

        if("google_dialer".equals(id))return "Google dialer+";

        if("chrome".equals(id))return "Chrome+";if("google_news".equals(id))return "Google news";
        if("google_app".equals(id))return "Google app";if("gboard".equals(id))return "Gboard+";if("gboard_autocorrect".equals(id))return "Auto-correction";

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
        if("aod".equals(id))return "The clock and info while charging, and after the power button.";

        if("drawer".equals(id))return "Folders, dragging and app shortcuts.";if("launcher".equals(id))return "Folders, names, search bar and recents.";

        if("files".equals(id))return "A better starting view for your files.";if("themes".equals(id))return "System colors, your way.";

        if("privacy".equals(id))return "Protected with your fingerprint or PIN.";

        if("do_not_annoy".equals(id))return "Stay connected. Keep incoming calls quiet.";
        if("share_menu".equals(id))return "Pinned and hidden sharing apps.";

        if("recents".equals(id))return "Keep what matters. Clear the rest.";

        if("blockers".equals(id))return "Play Store updates and system updates";

        if("store_blocker".equals(id))return "Choose which apps stay on their current version.";

        if("store_backups".equals(id))return "Saved versions of your apps, ready to put back.";

        if("ota_blocker".equals(id))return "System update controls.";

        if("google_apps".equals(id))return "Tools for your Google apps.";

        if("vpn".equals(id))return "Browse from another country.";

        if("extensions".equals(id))return "Add-ons for Chrome+.";if("ad_blocker".equals(id))return "Filtering levels and website exceptions.";
        if("dark_pages".equals(id))return "Dark websites in Chrome.";

        if("google_dialer".equals(id))return "Call recording";

        if("chrome".equals(id))return "Smoother hand-crafted animations, built-in extensions, bottom controls and a custom speed dial.";if("google_news".equals(id))return "Cleaner news browsing.";
        if("google_app".equals(id))return "Hide ads in Google feed";if("gboard".equals(id))return "Toolbar, keys, resize, auto-correction, clipboard";if("gboard_autocorrect".equals(id))return "Choose the languages Gboard corrects.";

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
        if("aod".equals(id)){
            LinearLayout controls=group(page);
            row(controls,AodPlus.KEY_CHARGING,"ic_ps_aod_charging",mAccent,"AOD while charging","Always-on display while plugged in");
            row(controls,AodPlus.KEY_AFTER,"ic_ps_aod_after",mAccent,"AOD after screen off","The power button shows the always-on display for a few seconds");
            finishGroup(controls);
            LinearLayout timing=group(page);
            navChoice(timing,AodPlus.KEY_SECONDS,"Turn off after",new String[]{"3 seconds","5 seconds","10 seconds","15 seconds","30 seconds"},new String[]{"3","5","10","15","30"},"5");
            finishGroup(timing);
            extraPreview(page,"aod_charging","AOD while charging");
            extraPreview(page,"aod_after","AOD after screen off");
            section(page,"How it works");
            explanation(page,"Your own Always-on display setting isn't changed.");
            explanation(page,"With Always-on display off, the power button turns the screen off into the always-on display. It goes off after the time you choose.");
            explanation(page,"Battery saver can still turn it off.");
            return;
        }
        if(Feature.FLASHLIGHT.equals(id)){
            LinearLayout choices=group(page);
            int pressValue=1;try{pressValue=android.provider.Settings.Secure.getInt(getContentResolver(),Feature.FLASHLIGHT,Feature.defaultFor(Feature.FLASHLIGHT));}catch(Throwable ignored){}
            mNavSaved.put(Feature.FLASHLIGHT,pressValue==2?"2":pressValue==1?"1":"0");
            navChoice(choices,Feature.FLASHLIGHT,"Double-press power button",new String[]{"Off","Flashlight","Camera (stock)"},new String[]{"2","1","0"},"0");
            finishGroup(choices);
            section(page,"How it works");
            explanation(page,"Flashlight: double-press power to turn it on or off, also when locked. Camera (stock) opens the camera.");
            featurePreview(page,f);
            return;
        }
        if(Feature.VOLDOWN_CAMERA.equals(id)){
            LinearLayout choices=group(page);
            mNavSaved.put(VolumeCamera.KEY_ACTION,VolumeCamera.action(this));
            navChoice(choices,VolumeCamera.KEY_ACTION,"Double-press volume down",new String[]{"Off","Camera","Flashlight"},new String[]{"off","camera","flashlight"},"off");
            finishGroup(choices);
            section(page,"How it works");
            explanation(page,"With the screen off and Always-on display on, double-press volume down. Not during calls or while media plays.");
            featurePreview(page,f);
            return;
        }
        if(PowerHold.KEY.equals(id)){
            LinearLayout choices=group(page);
            navChoice(choices,PowerHold.KEY,"Hold power button",new String[]{"Off","Gemini (stock)","Circle to Search","Take a screenshot","Silence","Do Not Disturb","Do Not Annoy","No Annoyance"},new String[]{PowerHold.OFF,PowerHold.GEMINI,PowerHold.CIRCLE,PowerHold.SCREENSHOT,PowerHold.SILENCE,PowerHold.DND,PowerHold.DNA,PowerHold.ALL},PowerHold.GEMINI);
            navChoice(choices,PowerHold.KEY_LOCKED,"Hold power button when locked",new String[]{"Gemini (stock)","Silence","Do Not Disturb","Do Not Annoy","No Annoyance"},new String[]{PowerHold.GEMINI,PowerHold.SILENCE,PowerHold.DND,PowerHold.DNA,PowerHold.ALL},PowerHold.lockedChoice(getContentResolver()));
            finishGroup(choices);
            section(page,"How it works");
            explanation(page,"Circle to Search works like holding the navigation handle. Screenshot works like power + volume down.");
            explanation(page,"Silence, Do Not Disturb and Do Not Annoy turn on with a hold and off with the next one. No Annoyance does all three together.");
            explanation(page,"While your phone is locked, a hold does what you pick in Hold power button when locked. Each choice works on its own.");
            featurePreview(page,f);
            return;
        }

        if(f!=null){
            if(LauncherFeatures.HIDE_SEARCH.equals(f.key)){
                LinearLayout controls=group(page);row(controls,f.key,f.icon,mAccent,f.title,f.summary);finishGroup(controls);
                section(page,"How it works");
                explanation(page,"You can also hold the search bar and choose Hide widget. It hides right away.");
                explanation(page,"Search stays in the app drawer.");
                return;
            }
            if("clear_badge".equals(f.key)){
                section(page,"How it works");
                explanation(page,f.summary);
                explanation(page,"Notifications that can be dismissed are cleared, and the dot goes away.");
                featurePreview(page,f);return;
            }

            LinearLayout controls=group(page);row(controls,f.key,Feature.TAP_CHECK.equals(f.key)?"ic_ps_tapwake_toggle":f.icon,mAccent,(Feature.SCREENSHOT_DOODLES.equals(f.key)||Feature.PHOTOS_DOODLES.equals(f.key)?"Use Doodles":LauncherFeatures.HIDE_SHORTCUTS.equals(f.key)?"Hide app shortcuts":LauncherFeatures.ICONS.equals(f.key)?"Use custom app icons":LauncherFeatures.LABEL_SIZE.equals(f.key)?"Customize icon labels":SuperDark.KEY.equals(f.key)?"Use Super Dark-Mode":"Use "+(I18n.on()?f.title:f.title.toLowerCase(java.util.Locale.ROOT))),"Turn this feature on or off.");if(LauncherFeatures.ICONS.equals(f.key))row(controls,DrawerIconColors.KEY,"ic_ps_drawer",mAccent,"Colorful icons in the app drawer (Stock)","With themed icons on, the app drawer keeps each app's own colors.");finishGroup(controls);

            section(page,"How it works");

            if(LauncherFeatures.DRAWER.equals(f.key)){

                explanation(page,"Hold a folder and drag it anywhere among your apps.");
                explanation(page,"Drag a folder onto Move to bottom to scroll; push deeper to go faster.");
                explanation(page,"Hold an app or folder with a dot and choose Clear badge.");
                explanation(page,"Drag an app onto another to make a folder. Use Add apps to pick more.");

                extraPreview(page,"drawer_folder_move","Move folders between apps");
                extraPreview(page,"drawer_move_bottom","Move to bottom and scroll");
                featurePreview(page,f);

                section(page,"Sort from the three-dot menu");

                explanation(page,"Auto sorts A to Z; Custom lets you arrange them.");

                extraPreview(page,"folder_sort","Sorting and custom order");

                explanation(page,"Hold a folder to rename, sort or remove it. Removing it doesn't uninstall its apps.");

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

                explanation(page,"Applies right away. Open folders and search keep the usual size.");

            }

            if(Feature.TAP_CHECK.equals(f.key)){

                section(page,"Wake the screen");LinearLayout wake=group(page);

                choice(wake,Feature.DOUBLE_TAP_WAKE,"Wake gesture",new String[]{"Single tap","Double tap"});finishGroup(wake);

                explanation(page,"If a double tap doesn't register, tap once more within three seconds.");

                section(page,"Lock-screen taps");LinearLayout lock=group(page);

                choice(lock,Feature.LOCK_SHORTCUTS,"Lock-screen behavior",new String[]{"Pixel Suite","Stock"});finishGroup(lock);

                explanation(page,"Pixel Suite: double-tapping a lock-screen card turns the screen off without opening it.");

            }

            if(LauncherFeatures.GRID.equals(f.key))explanation(page,"Pick 3×3, 4×4 or 5×5 from a folder's three-dot menu. Bigger grids appear as the folder fills up.");

            if(LauncherFeatures.RENAME.equals(f.key))explanation(page,"Reset brings back the original name.");

            if(LauncherFeatures.RENAME.equals(f.key))explanation(page,"Shows right away everywhere. Work profile and Private Space apps keep their names.");

            if(Feature.CLEAR_ALL.equals(f.key))explanation(page,"Changing this option restarts Pixel Launcher.");

            if(RecentsLock.KEY.equals(f.key))explanation(page,"Locks stay after a restart.");

            if(FolderDrag.TWO_FINGER.equals(f.key))explanation(page,"Drawer folders: hold one on Move to bottom to scroll; push deeper to go faster.");

            if(MultiDrag.KEY.equals(f.key))explanation(page,"Removing apps from the home screen doesn't uninstall them.");

            if(LauncherFeatures.ICONS.equals(f.key))explanation(page,"Shows right away everywhere. Tap the reset arrow for the original icon.");

            return;

        }

        if("do_not_annoy".equals(id)){
            try{getPackageManager().getPackageInfo("com.android.imsstack",0);mDnaPixelStack=true;}catch(android.content.pm.PackageManager.NameNotFoundException absent){mDnaPixelStack=false;}
            LinearLayout controls=group(page);
            row(controls,DoNotAnnoy.KEY,"ic_ps_do_not_annoy",mAccent,"Do Not Annoy","Silences calls and keeps you connected. Add its Quick Settings tile.");
            row(controls,"pixelsuite_dna_watch","ic_ps_watch",mAccent,"Pixel Watch with LTE","For a Pixel Watch with its own mobile plan: its network goes off too. Needs the Pixel Suite watch app.");finishGroup(controls);
            explanation(page,mDnaPixelStack?"Callers hear that you're unavailable. You get a notification with a Call button for each missed call. Emergency calls always come through.":"Calls are declined before your phone rings. You get a notification with a Call button for each missed call. Emergency calls always come through.");
            explanation(page,"No LTE Pixel Watch? Leave this off.");
            explanation(page,"Get the Pixel Suite watch app from the developer on Telegram.");
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

            row(controls,StoreBlocker.UNLOCK_KEY,"ic_ps_store_blocker",mAccent,"Unlock OEM apps","Updating these core apps from the Play Store may break Pixel Suite features. Pixel Suite updates them for you with each release.");finishGroup(controls);

            navigation(controls=group(page),"Select apps","Loading…","ic_ps_drawer",new Runnable(){public void run(){mStoreBlockerPage.show();}});

            TextView selectedCount=navigationSummary(controls);finishGroup(controls);

            LinearLayout backups=group(page);
            navigation(backups,"Backup & Restore","Loading…","ic_ps_store_backup",new Runnable(){public void run(){openPage("store_backups");}});
            mBackupSummary=navigationSummary(backups);finishGroup(backups);
            section(page,"How it works");
            explanation(page,"Apps with a newer version on the Play Store show it in red. Tap ! to update, back up or restore.");
            explanation(page,"Update saves the current version first, then opens its Play Store page. It's held back again once updated.");
            explanation(page,"When you untick apps, Back up saves their current versions first.");
            explanation(page,"The green button puts a saved version back, with its data and sign-ins.");
            explanation(page,"Backup & Restore keeps every saved version, also of uninstalled apps.");

            mStoreBlockerPage=new StoreBlockerPage(this,STORE_IO,mText,mTextSecondary,mAccent,mOnAccent,mSwitches.get(StoreBlocker.UNLOCK_KEY),selectedCount);

            extraPreview(page,"209_store","Hold back app updates");

            return;

        }

        if("ota_blocker".equals(id)){

            LinearLayout controls=group(page);

            row(controls,OtaBlocker.KEY,"ic_ps_update",mAccent,"OTA blocker","Stops system updates and hides Software updates in Settings.");

            row(controls,OtaBlocker.RESET_KEY,"ic_ps_privacy",mAccent,"Lock factory reset","Erase all data is greyed out in Settings while OTA blocker is on.");finishGroup(controls);

            updateMiscPicker();explanation(page,"Reopen Settings to see the change.");

            extraPreview(page,"209_ota","System updates and factory reset");

            return;

        }

        if("store_backups".equals(id)){
            mBackupPage=new BackupRestorePage(this,page,IO,mText,mTextSecondary,mAccent,mDark);
            section(page,"How it works");
            explanation(page,"App and data saves everything (sign-ins, chats, settings and files) and restores it exactly as it was. App only saves just the app.");
            explanation(page,"Held-back apps are saved automatically before they update.");
            explanation(page,"Restore puts it back right here. A version saved with its data asks whether to restore the data too.");
            explanation(page,"The 3 newest versions of each app are kept.");
            explanation(page,"Apps that lock their sign-in to the phone, like many banking apps, may ask you to sign in again after an uninstall.");
            explanation(page,"Tap an app to see its saved versions.");
            return;
        }
        if("vpn".equals(id)){new VpnPage(this,page,mText,mTextSecondary,mAccent,mOnAccent,mCard,mDark);return;}
        if("gboard_autocorrect".equals(id)){new GboardAutoCorrectPage(this,page,mText,mTextSecondary,mAccent,mOnAccent,switchTrackOff(),sectionColor("ic_ps_google_apps"),sectionInk("ic_ps_google_apps"),mDark);return;}

        if("ad_blocker".equals(id)){
            new AdBlockerPage(this,page,IO,mText,mTextSecondary,mAccent,mDark,new AdBlockerPage.Presenter(){public void show(AlertDialog dialog){showSettingsDialog(dialog);}});return;
        }

        if("extensions".equals(id)){
            LinearLayout list=group(page);
            final String vpnPage="vpn";
            navigation(list,"Ad Blocker","Block ads, with filtering per site","ic_ps_cr_adblock",new Runnable(){public void run(){openPage("ad_blocker");}});
            PatronUi.navigationBadge(this,(LinearLayout)list.getChildAt(list.getChildCount()-1),"Ad Blocker");
            navigation(list,"VPN","Browse from another country, in Chrome or every app","ic_ps_vpn",new Runnable(){public void run(){openPage(vpnPage);}});
            PatronUi.navigationBadge(this,(LinearLayout)list.getChildAt(list.getChildCount()-1),"VPN");
            navigation(list,"Dark pages","Dark websites, always, with dark mode or on a schedule","ic_ps_cr_dark",new Runnable(){public void run(){openPage("dark_pages");}});
            PatronUi.navigationBadge(this,(LinearLayout)list.getChildAt(list.getChildCount()-1),"Dark pages");
            finishGroup(list);
            explanation(page,"Tap Extensions on Chrome's new tab page to use them. Tap one for its settings, or hold it to pin it to the toolbar.");
            explanation(page,"More to come.");
            return;
        }

        if("dark_pages".equals(id)){
            LinearLayout main=group(page);
            row(main,DarkPages.KEY,"ic_ps_cr_dark",mAccent,"Dark pages","Dark websites in Chrome");
            finishGroup(main);
            LinearLayout look=group(page);
            choice(look,DarkPages.KEY_STYLE,"Style",new String[]{"Dark","Super Dark"});
            choice(look,DarkPages.KEY_WHEN,"Turns on",new String[]{"Always","With the phone's dark mode","On a schedule"});
            finishGroup(look);
            mDarkTimes=group(page);
            navigation(mDarkTimes,"Starts",DarkPages.time(this,darkSetting(DarkPages.KEY_FROM,DarkPages.DEFAULT_FROM)),"ic_ps_cr_schedule",new Runnable(){public void run(){pickDarkTime(DarkPages.KEY_FROM,"Starts");}});
            mDarkFrom=navigationSummary(mDarkTimes);
            navigation(mDarkTimes,"Ends",DarkPages.time(this,darkSetting(DarkPages.KEY_TO,DarkPages.DEFAULT_TO)),"ic_ps_cr_schedule",new Runnable(){public void run(){pickDarkTime(DarkPages.KEY_TO,"Ends");}});
            mDarkTo=navigationSummary(mDarkTimes);
            finishGroup(mDarkTimes);
            mDarkTimes.setVisibility(darkSetting(DarkPages.KEY_WHEN,DarkPages.ALWAYS)==DarkPages.SCHEDULE?View.VISIBLE:View.GONE);
            explanation(page,"Websites open dark, with no white flash. Super Dark uses pure black.");
            explanation(page,"In Chrome, open Extensions and tap Dark pages to turn it off for one website. Pictures and videos keep their colors.");
            return;
        }

        if("google_apps".equals(id)){

            LinearLayout apps=group(page);

            appNavigation(apps,"Google dialer+","Call recording","com.google.android.dialer","ic_ps_call_recording","google_dialer");
            appNavigation(apps,"Chrome+","Smoother hand-crafted animations, built-in extensions, bottom controls, custom speed dial","com.android.chrome","ic_ps_chrome","chrome");
            appNavigation(apps,"Gboard+","Toolbar, keys, resize, auto-correction, clipboard",SuperDarkApps.GBOARD,"ic_ps_google_apps","gboard");
            appNavigation(apps,"Google app","Hide ads in Google feed",GoogleFeedAds.PACKAGE,"ic_ps_google_apps","google_app");
            appNavigation(apps,"Google news","Ad blocking for news browsing","com.google.android.apps.magazines","ic_ps_news","google_news");
            appNavigation(apps,"Pixel watch","eSIM setup with a QR code",WatchEsim.PACKAGE,"ic_ps_google_apps","pixel_watch");

            finishGroup(apps);

            AdBlockCard protection=new AdBlockCard(this,mText,mTextSecondary);LinearLayout.LayoutParams protectionLayout=new LinearLayout.LayoutParams(-1,-2);protectionLayout.topMargin=dp(8);protectionLayout.bottomMargin=0;page.addView(protection,protectionLayout);return;

        }

        if("chrome".equals(id)){
            LinearLayout controls=group(page);
            row(controls,ChromeLayout.KEY,"ic_ps_cr_layout",mAccent,"Chrome+","Smoother hand-crafted animations, built-in extensions, bottom controls, custom speed dial");
            navigation(controls,"Extensions","Ad Blocker, VPN, Dark pages and more to come","ic_ps_cr_extensions",new Runnable(){public void run(){openPage("extensions");}});
            finishGroup(controls);
            mAdProtectionStatus=text("",15,mText,true);mAdProtectionStatus.setPadding(dp(18),dp(16),dp(18),dp(16));mAdProtectionStatus.setBackground(AppSurfaces.card(this,20));
            LinearLayout.LayoutParams protectionStatus=new LinearLayout.LayoutParams(-1,-2);protectionStatus.bottomMargin=dp(12);page.addView(mAdProtectionStatus,protectionStatus);updateAdProtectionStatus();
            mAdProtectionStatus.setOnClickListener(new View.OnClickListener(){public void onClick(View v){if(!PatronAccess.unlocked())PatronPaper.open(SettingsActivity.this,0,null);}});updateAdProtectionStatus();
            extraPreview(page,"chrome_plus_transform","From Chrome to Chrome+");
            extraPreview(page,"209_chrome","Browse without empty ad boxes");
            section(page,"How it works");
            LinearLayout guide=group(page);guide.setPadding(dp(18),dp(10),dp(18),dp(18));guide.setBackground(AppSurfaces.card(this,20));
            chromeGuidePart(guide,"Chrome+","Controls move to the bottom, with smooth tab animations. In the tab switcher, the orange + opens an incognito tab.");
            chromeGuidePart(guide,"Ad blocker","Filter ads per site, from basic to complete. Complete also hides cookie banners and pop-ups. You can remove any element too.");
            chromeGuidePart(guide,"Speed dial","Round icons for up to 19 websites and folders. Hold and drag to move one, or drop it on another to make a folder. Edit a website to give it your own picture.");
            chromeGuidePart(guide,"Folders","Tap to open; hold to rename or remove. Hold a website inside to edit, move or remove it.");
            chromeGuidePart(guide,"Extensions","Tap Extensions on the new tab page to use them. Hold one to pin it to the toolbar.");
            return;
        }
        if("google_news".equals(id)){
            boolean news="google_news".equals(id);
            explanation(page,news?"Blocks ads while you read the news.":"Choose a filtering level per site from the ad blocker button in Chrome.");
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

        if("gboard".equals(id)){
            LinearLayout mainGroup=group(page);
            row(mainGroup,GboardMain.KEY,"ic_ps_gb_power",mAccent,"Use Gboard+","All of Gboard+ at once. Your choices are kept while it's off");
            finishGroup(mainGroup);
            Lockable options=new Lockable(this);page.addView(options,new LinearLayout.LayoutParams(-1,-2));mGboardOptions=options;
            LinearLayout look=group(options);
            row(look,GboardFrost.KEY,"ic_ps_gb_frost",mAccent,"Frosted Gboard","See-through keyboard background, with rims");
            finishGroup(look);
            explanation(options,"In some apps, like Chrome, the keyboard's top corners look white. Those apps end their page where the keyboard starts, so there's nothing behind the corners to show.");
            section(options,"Toolbar");
            LinearLayout controls=group(options);
            navigation(controls,"Edit toolbar shortcuts",gboardBarSummary(),"ic_ps_gb_toolbar",new Runnable(){public void run(){gboardBarEditor();}});
            row(controls,GboardPlus.KEY_HIDE_CHIPS,"ic_ps_gb_chips",mAccent,"Hide suggestion chips","Keeps chips like Screenshot off the toolbar");
            finishGroup(controls);
            section(options,"Keys");
            controls=group(options);
            row(controls,GboardPlus.KEY_KEYS,"ic_ps_gb_keys",mAccent,"Rounded keys","Rounded keys and corners, with smooth key pop-ups");
            row(controls,GboardPlus.KEY_DEL_LETTERS,"ic_ps_gb_backspace",mAccent,"Backspace swipe deletes letters","Swipe left from delete to remove letter by letter");
            navigation(controls,"Resize",gboardGeoSummary(),"ic_ps_gb_resize",new Runnable(){public void run(){gboardResizer();}});
            finishGroup(controls);
            section(options,"Typing and clipboard");
            controls=group(options);
            navigation(controls,"Auto-correction",gboardAutoSummary(),"ic_ps_gb_autocorrect",new Runnable(){public void run(){if(!PatronAccess.unlocked()){PatronPaper.open(SettingsActivity.this,0,null);return;}openPage("gboard_autocorrect");}});
            row(controls,GboardPlus.KEY_DICTATION,"ic_ps_gb_voice",mAccent,"Voice typing with checkmark","Speak, then tap the checkmark to add your words");
            row(controls,GboardPlus.KEY_CLIP,"ic_ps_gb_clipboard",mAccent,"Clipboard in one column","One clip per row, exactly as you copied it");
            finishGroup(controls);
            section(page,"How it works");
            LinearLayout guide=group(page);guide.setPadding(dp(18),dp(10),dp(18),dp(18));guide.setBackground(AppSurfaces.card(this,20));
            chromeGuidePart(guide,"Use Gboard+","One switch for everything here. While it's off, Gboard works as it comes and your choices are kept.");
            chromeGuidePart(guide,"Frosted Gboard","The keyboard's background turns to frosted glass: what's behind it shows through, blurred, with the rims. The keys stay as they are.");
            chromeGuidePart(guide,"The toolbar","Up to 7 shortcuts, in the same order in every language.");
            chromeGuidePart(guide,"Edit toolbar shortcuts","Hold a shortcut and drag it onto the toolbar, along it to reorder, or off to remove it. Tap one to see what it does. Hide the side buttons to give your shortcuts more room.");
            chromeGuidePart(guide,"Text editing","Undo, Redo, Quick Select, Select all, Cut, Copy and Paste work in every app. Quick Select: tap for a word, tap again to clear, hold to select all.");
            chromeGuidePart(guide,"Keys and backspace","Rounded keys with smooth pop-ups, in light, dark and Super Dark. Swipe left from delete to remove letter by letter.");
            chromeGuidePart(guide,"Resize","Drag the handles to change the size, or the middle to move the keyboard. Reset goes back to Gboard's size.");
            chromeGuidePart(guide,"Auto-correction","Turn auto-correction off for the languages you choose. Suggestions still show.");
            chromeGuidePart(guide,"Voice typing","Off, the words appear as you speak. With Voice typing with checkmark on, speak, then tap the checkmark to add your words or X to cancel. It needs Rambler as the dictation type in Gboard's voice settings.");
            chromeGuidePart(guide,"Clipboard history","Gboard keeps every clip until you delete it. Pinned clips have blue cards, and the X at the top deletes all but the pinned ones. Clipboard in one column shows one clip per row.");
            TextView restart=text("Gboard restarts when you change any of these.",14,mTextSecondary,false);restart.setPadding(0,dp(14),0,0);guide.addView(restart,new LinearLayout.LayoutParams(-1,-2));
            return;
        }
        if("google_app".equals(id)){

            LinearLayout controls=group(page);

            row(controls,GoogleFeedAds.KEY,"ic_ps_google_apps",mAccent,"Hide ads in Google feed","Removes sponsored cards from your feed, with no gaps left");

            try{LinearLayout toggle=(LinearLayout)controls.getChildAt(0);FrameLayout badge=(FrameLayout)toggle.getChildAt(0);((ImageView)badge.getChildAt(0)).setImageDrawable(googleIcon(sectionInk("ic_ps_google_apps")));}catch(Exception unavailable){}

            finishGroup(controls);extraPreview(page,"209_feed","A feed without sponsored gaps");return;

        }

        if("google_dialer".equals(id)){

            LinearLayout controls=group(page);

            row(controls,CallRecording.KEY,"ic_ps_call_recording",mAccent,"Call recording","Record calls in the Phone app, in any country, without the announcement");

            navigation(controls,"Recording mode","Automatic: every call is recorded","ic_ps_recording_mode",new Runnable(){public void run(){if(mCallRecordingSettings!=null)mCallRecordingSettings.chooseMode();}});

            View modeRow=controls.getChildAt(controls.getChildCount()-1);

            TextView modeSummary=(TextView)((LinearLayout)((LinearLayout)modeRow).getChildAt(1)).getChildAt(1);

            navigation(controls,"Recording quality","HD: clearer voice (default)","ic_ps_recording_mode",new Runnable(){public void run(){if(mCallRecordingSettings!=null)mCallRecordingSettings.chooseQuality();}});

            View qualityRow=controls.getChildAt(controls.getChildCount()-1);

            TextView qualitySummary=(TextView)((LinearLayout)((LinearLayout)qualityRow).getChildAt(1)).getChildAt(1);

            navigation(controls,"Recordings folder",CallRecordingSettings.DEFAULT,"ic_ps_files_category",new Runnable(){public void run(){if(mCallRecordingSettings!=null)mCallRecordingSettings.choose();}});

            View folderRow=controls.getChildAt(controls.getChildCount()-1);

            TextView folderSummary=(TextView)((LinearLayout)((LinearLayout)folderRow).getChildAt(1)).getChildAt(1);

            mCallRecordingSettings=new CallRecordingSettings(this,IO,folderRow,folderSummary,new CallRecordingSettings.Presenter(){public void show(AlertDialog dialog){showSettingsDialog(dialog);}});

            mCallRecordingSettings.modeRow(modeRow,modeSummary);mCallRecordingSettings.qualityRow(qualityRow,qualitySummary);mCallRecordingSettings.enabled(Boolean.TRUE.equals(sSwitchValues.get(CallRecording.KEY)));finishGroup(controls);

            explanation(page,"Call recording may be restricted by your country’s laws. Obtain any required consent before recording. You are responsible for lawful use. RovianDev is not responsible for illegal use.");

            extraPreview(page,"209_recording","Record and save calls");

            return;

        }

        if("share_menu".equals(id)){

            LinearLayout controls=group(page);

            row(controls,ShareSheet.KEY,"ic_ps_share_sheet",mAccent,"Share sheet tools","Pin, reorder and hide sharing apps");finishGroup(controls);

            section(page,"How it works");
            explanation(page,"• Drag an app into the top row to pin it, then drag to reorder.\n\n• Hold an app or contact to pin or hide it.\n\n• Hidden items are under Hidden apps and Hidden contacts. Hold one to unhide it.");

            extraPreview(page,"209_share_combined","Pin, reorder, hide and restore sharing apps and contacts");

            return;

        }

        if("miscellaneous".equals(id)){

            LinearLayout controls=group(page);

            row(controls,ScreenshotWarm.KEY,"ic_ps_fast_screenshot",mAccent,"Reduce screenshot delay","Screenshots appear instantly. Uses a little extra memory.");

            row(controls,MiscSystem.SECURE_KEY,"ic_ps_secure_capture",mAccent,"Allow screenshots of secure screens","Screenshots and recordings in apps that block them.\nTurn off other modules that do this first.");

            row(controls,MiscSystem.A11Y_KEY,"ic_ps_accessibility",mAccent,"Hide accessibility from apps","Apps you choose can't see your accessibility services.\nTurn off other modules that do this first.");

            navigation(controls,"Choose apps","Choose which apps can't see your accessibility services","ic_ps_drawer",new Runnable(){public void run(){

                MiscAppPicker.show(SettingsActivity.this,IO,mText,mTextSecondary,mAccent,new MiscAppPicker.Presenter(){public void show(AlertDialog dialog){showSettingsDialog(dialog);}});

            }});

            mMiscChooseApps=controls.getChildAt(controls.getChildCount()-1);updateMiscPicker();

            row(controls,KeyboardBarTweak.KEY,"ic_ps_keyboard",mAccent,"Keyboard bar tweak","Hides the globe under your keyboard and moves the arrow to the right");

            row(controls,MiscSystem.DEV_KEY,"ic_ps_developer",mAccent,"Hide Developer Options","Apps that refuse to open with developer options on, like some banking apps, work normally.\nTap the gear to open Developer options.");

            row(controls,RefreshRate90.KEY,"ic_ps_refresh_rate",mAccent,"Refresh rate slider","Adds a 60 / 120 Hz slider under Smooth display (Settings > Display & touch)");

            row(controls,ShowFps.KEY,"ic_ps_fps",mAccent,"Show FPS","Live frame counter at the top right");

            finishGroup(controls);

            return;

        }

        if("recents".equals(id)){

            LinearLayout controls=group(page);

            row(controls,Feature.CLEAR_ALL,"ic_ps_clearall",mAccent,"Always show Clear all","Keeps Clear all next to Screenshot and Select.");
            row(controls,ClearAll.KEY_ONLY,"ic_ps_clearall_only",mAccent,"Only Clear all","Only Clear all, in the middle");

            row(controls,RecentsLock.KEY,"ic_ps_recents_lock",mAccent,"Lock apps in recents","Choose Lock from an app's recents menu to keep its card when you clear apps.");finishGroup(controls);

            explanation(page,"To protect an app with your fingerprint or PIN, use Privacy & Security.");

            extraPreview(page,RecentsLock.KEY,"Locking recent apps");

            section(page,"Landscape view");

            explanation(page,"Turn your phone sideways in recents for a cleaner view.");

            extraPreview(page,"recents_landscape","Landscape recent apps");

            return;

        }

        if("privacy".equals(id)){

            LinearLayout protection=group(page);

            row(protection,PrivacyLock.ENABLED_KEY,"ic_ps_app_folder_lock",mAccent,"App & folder lock","Fingerprint or PIN before locked apps and folders open");

            row(protection,PrivacyLock.PROTECT_KEY,"ic_ps_protect_apps",mAccent,"Protect locked apps","Asks before a locked app is uninstalled or paused");

            row(protection,PrivacyLock.GRACE_KEY,"ic_ps_unlock_timer",mAccent,"Keep unlocked for 1 minute","Stays unlocked for a minute after you leave it");finishGroup(protection);

            section(page,"Lock screen");
            LinearLayout lockScreen=group(page);
            row(lockScreen,PinAuto.KEY,"ic_ps_pin_auto",mAccent,"Auto-confirm 4-digit PIN","Unlocks as soon as you type the 4th digit of your PIN, without Enter");
            finishGroup(lockScreen);
            extraPreview(page,"privacy_combined","Open locked apps and folders with your fingerprint or PIN");
            extraPreview(page,"pin_auto","Unlock on the 4th digit");
            section(page,"How it works");
            explanation(page,"Hold an app or folder and choose Lock or Unlock.\n\nLocking a folder doesn't lock the apps inside.");
            explanation(page,"Auto-confirm works with a 4-digit PIN, from your next unlock.");

            return;

        }

        if("updates".equals(id)){buildUpdates(page);return;}

        if("about".equals(id)){

            explanation(page,"To come back here, long-press the home screen and choose Pixel Suite settings. Needs root and LSPosed.");

            section(page,"Backup");LinearLayout backup=group(page);
            navigation(backup,"Back up","Save your settings, folders and icons to a file","ic_ps_backup",new Runnable(){public void run(){startBackup();}});
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

                explanation(page,"Blur Off\nNo blur, solid surfaces.\n\nStock\nThe system's own look.\n\nFrosted Glass (Default)\nBlurs what's behind folders, menus and messages.");

            }

            return;

        }

        LinearLayout categories=homeSection(page,"Shortcuts");

        navigation(categories,"Buttons","Flashlight and camera shortcuts","ic_ps_buttons",new Runnable(){public void run(){openPage("buttons");}});

        navigation(categories,"Gestures","Screenshots, sleep, wake and dragging","ic_ps_gestures",new Runnable(){public void run(){openPage("gestures");}});

        finishGroup(categories);

        categories=homeSection(page,"Personalize");

        navigation(categories,"Pixel Launcher","Folders, drawer, names, search bar and recents","ic_ps_launcher",new Runnable(){public void run(){openPage("launcher");}});

        navigation(categories,"Themes","Icons, Super Dark-Mode and blur","ic_ps_themes",new Runnable(){public void run(){openPage("themes");}});

        navigation(categories,"Doodles","Shapes in Pixel Studio and Google Photos","ic_ps_doodles",new Runnable(){public void run(){openPage("doodles");}});

        navigation(categories,"Always-on display","While charging and after the power button","ic_ps_aod",new Runnable(){public void run(){openPage("aod");}});

        finishGroup(categories);

        categories=homeSection(page,"Sharing");

        navigation(categories,"Share menu","Reorder pinned apps and hide sharing apps","ic_ps_share_sheet",new Runnable(){public void run(){openPage("share_menu");}});

        navigation(categories,"Files","Newest files first, in Files and the file picker","ic_ps_files_category",new Runnable(){public void run(){openPage("files");}});

        finishGroup(categories);

        categories=homeSection(page,"Patreon");

        navigation(categories,"Google apps","Phone, Chrome, Gboard, Google, News and watch","ic_ps_google_apps",new Runnable(){public void run(){openPage("google_apps");}});

        googleNavigationIcon(categories);

        navigation(categories,"VPN","Browse from another country","ic_ps_vpn",new Runnable(){public void run(){openPage("vpn");}});

        PatronUi.navigationBadge(this,(LinearLayout)categories.getChildAt(categories.getChildCount()-1),"VPN");

        finishGroup(categories);

        categories=homeSection(page,"Protection");

        navigation(categories,"Privacy & Security","Lock apps and folders","ic_ps_privacy",new Runnable(){public void run(){openPage("privacy");}});

        navigation(categories,"Do Not Annoy","Keep incoming calls quiet and stay connected","ic_ps_do_not_annoy",new Runnable(){public void run(){openPage("do_not_annoy");}});
        navigation(categories,"OTA & Store blocker","Play Store updates and system updates","ic_ps_update_blocker",new Runnable(){public void run(){openPage("blockers");}});

        finishGroup(categories);

        categories=homeSection(page,"More");

        navigation(categories,"Miscellaneous","Screenshots, accessibility, keyboard and developer options","ic_ps_miscellaneous",new Runnable(){public void run(){openPage("miscellaneous");}});

        finishGroup(categories);

        section(page,"Pixel Suite");LinearLayout about=group(page);navigation(about,"Changelog","What's new","ic_ps_changelog",new Runnable(){public void run(){openPage("changelog");}});navigation(about,"Updates","Check for updates","ic_ps_update",new Runnable(){public void run(){openPage("updates");}});navigation(about,"About and support","Setup, community and support","ic_ps_info",new Runnable(){public void run(){openPage("about");}});finishGroup(about);

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

            button.setClickable(true);button.setFocusable(true);button.setContentDescription(I18n.cs(CommunityLinks.NAMES[index]+". "+CommunityLinks.CAPTIONS[index]+". Opens an external app."));button.setOnClickListener(new View.OnClickListener(){public void onClick(View v){CommunityLinks.open(SettingsActivity.this,index);}});

            LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,-2,0);if(position>0)bp.topMargin=dp(8);actions.addView(button,bp);

        }

    }

    private void updateAdProtectionStatus(){
        if(mAdProtectionStatus!=null){mAdProtectionStatus.setText(I18n.cs(PatronAccess.unlocked()?"Included with your Patreon unlock":"Unlock ad blocking with Patreon"));mAdProtectionStatus.setClickable(!PatronAccess.unlocked());mAdProtectionStatus.setFocusable(!PatronAccess.unlocked());}
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

        mFeaturePreview=new FeaturePreview(this,feature.key,mAccent,mOnAccent,mText,mTextSecondary,mCard,mDark);mFeaturePreview.setContentDescription(I18n.cs(feature.title+". A looping demo. It doesn't change your settings."));card.addView(mFeaturePreview,new LinearLayout.LayoutParams(-1,dp("chrome_plus_transform".equals(feature.key)?360:"privacy_combined".equals(feature.key)?400:(SuperDark.KEY.equals(feature.key)||LauncherFeatures.DRAWER.equals(feature.key)||LauncherFeatures.GRID.equals(feature.key)||"folder_sort".equals(feature.key)||FolderDrag.TWO_FINGER.equals(feature.key)||MultiDrag.KEY.equals(feature.key))?330:300)));mPreviews.add(mFeaturePreview);mPreviewEntries.add(new PreviewEntry(previewHeading,card,mFeaturePreview,feature.title));

    }

    private void extraPreview(LinearLayout page,String key,String title){featurePreview(page,new FeaturePage("launcher",key,"ic_ps_folder_grid",title,""));}

    private TextView mUpdateStatus;

    private android.widget.ProgressBar mUpdateProgress;

    private void beginUpdateInstall(){

        UpdateInstaller.start(this,new UpdateInstaller.Progress(){public void show(String message,int percent){

            if(isDestroyed()||isFinishing())return;

            if(mUpdateStatus!=null)mUpdateStatus.setText(I18n.cs(message));

            if(mUpdateProgress!=null){mUpdateProgress.setVisibility(View.VISIBLE);mUpdateProgress.setIndeterminate(percent<0&&message.contains("Verifying"));if(percent>=0){mUpdateProgress.setIndeterminate(false);mUpdateProgress.setProgress(percent);}else if(!message.contains("Verifying"))mUpdateProgress.setVisibility(View.GONE);}

        }});

    }

    private void startBackup(){
        try{Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json").putExtra(Intent.EXTRA_TITLE,Backup.fileName());startActivityForResult(i,Backup.SAVE);}
        catch(Exception e){AppToast.makeText(this,I18n.cs("No file picker available"),Toast.LENGTH_LONG).show();}
    }
    private void startRestore(){
        try{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*"+"/"+"*").putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"application/json","text/plain","application/octet-stream"});startActivityForResult(i,Backup.OPEN);}
        catch(Exception e){AppToast.makeText(this,I18n.cs("No file picker available"),Toast.LENGTH_LONG).show();}
    }
    private void backupResult(final int request,final Intent data){
        final android.net.Uri uri=data==null?null:data.getData();if(uri==null)return;
        if(request==Backup.SAVE){
            AppToast.makeText(this,I18n.cs("Backing up…"),Toast.LENGTH_SHORT).show();
            IO.execute(new Runnable(){public void run(){boolean ok;try{String json=Backup.create(SettingsActivity.this);java.io.OutputStream out=getContentResolver().openOutputStream(uri,"wt");if(out==null)throw new java.io.IOException();try{out.write(json.getBytes("UTF-8"));}finally{out.close();}ok=true;}catch(Throwable t){ok=false;}
                final boolean done=ok;mMain.post(new Runnable(){public void run(){if(isFinishing()||isDestroyed())return;if(done)AppToast.makeText(SettingsActivity.this,I18n.cs("Backup saved"),Toast.LENGTH_SHORT).show();else{showRootBanner(true);AppToast.makeText(SettingsActivity.this,I18n.cs("Couldn't back up: root access needed"),Toast.LENGTH_LONG).show();}}});}});
            return;
        }
        IO.execute(new Runnable(){public void run(){org.json.JSONObject read=null;try{java.io.InputStream in=getContentResolver().openInputStream(uri);if(in!=null)try{java.io.ByteArrayOutputStream b=new java.io.ByteArrayOutputStream();byte[] buf=new byte[65536];for(int n;(n=in.read(buf))>0;)b.write(buf,0,n);read=Backup.read(b.toString("UTF-8"));}finally{in.close();}}catch(Throwable ignored){}
            final org.json.JSONObject backup=read;mMain.post(new Runnable(){public void run(){if(isFinishing()||isDestroyed())return;
                if(backup==null){AppToast.makeText(SettingsActivity.this,I18n.cs("That isn't a Pixel Suite backup"),Toast.LENGTH_LONG).show();return;}
                showSettingsDialog(new AlertDialog.Builder(SettingsActivity.this).setTitle(I18n.cs("Restore this backup?")).setMessage(I18n.cs(Backup.summary(backup))).setNegativeButton(I18n.cs("Cancel"),null).setPositiveButton(I18n.cs("Restore"),new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){
                    AppToast.makeText(SettingsActivity.this,I18n.cs("Restoring…"),Toast.LENGTH_SHORT).show();
                    IO.execute(new Runnable(){public void run(){final boolean ok=Backup.restore(SettingsActivity.this,backup);mMain.post(new Runnable(){public void run(){if(isFinishing()||isDestroyed())return;
                        if(ok){AppToast.makeText(SettingsActivity.this,I18n.cs("Backup restored"),Toast.LENGTH_SHORT).show();recreate();}else{showRootBanner(true);AppToast.makeText(SettingsActivity.this,I18n.cs("Couldn't restore: root access needed"),Toast.LENGTH_LONG).show();}}});}});
                }}).create());}});}});
    }

    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if((request==Backup.SAVE||request==Backup.OPEN)&&result==RESULT_OK){backupResult(request,data);return;}if(request==CallRecordingSettings.PICK_FOLDER&&mCallRecordingSettings!=null){mCallRecordingSettings.result(result,data);return;}if(request==202&&getIntent().getBooleanExtra("resume_update_install",false)){getIntent().removeExtra("resume_update_install");try{if((Boolean)getPackageManager().getClass().getMethod("canRequestPackageInstalls").invoke(getPackageManager()))beginUpdateInstall();else if(mUpdateStatus!=null)mUpdateStatus.setText(I18n.cs("Installation permission was not enabled. Tap Download and install when ready."));}catch(Exception unavailable){}}}

    private void buildUpdates(LinearLayout page){

        explanation(page,"Pixel Suite checks for updates and installs them right here.");

        final android.widget.Switch automatic=new android.widget.Switch(this);automatic.setText(I18n.cs("Check for updates automatically"));android.graphics.drawable.Drawable updateIcon=drawable("ic_ps_update");if(updateIcon!=null){updateIcon=updateIcon.mutate();updateIcon.setTint(mAccent);updateIcon.setBounds(0,0,dp(24),dp(24));automatic.setCompoundDrawables(updateIcon,null,null,null);automatic.setCompoundDrawablePadding(dp(12));}automatic.setTextColor(mText);automatic.setChecked(ReleaseUpdates.enabled(this));automatic.setPadding(dp(18),dp(16),dp(18),dp(16));page.addView(automatic);

        automatic.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener(){public void onCheckedChanged(android.widget.CompoundButton v,boolean checked){ReleaseUpdates.prefs(SettingsActivity.this).edit().putBoolean("enabled",checked).apply();ReleaseUpdates.schedule(SettingsActivity.this);if(checked)ReleaseUpdates.check(SettingsActivity.this,false,null);}});

        final TextView status=text(ReleaseUpdates.status(this),15,mTextSecondary,false);mUpdateStatus=status;status.setPadding(dp(18),dp(12),dp(18),dp(16));page.addView(status);mUpdateProgress=new android.widget.ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);mUpdateProgress.setMax(100);mUpdateProgress.setVisibility(View.GONE);page.addView(mUpdateProgress,new LinearLayout.LayoutParams(-1,dp(8)));

        LinearLayout actions=group(page);

        navigation(actions,"Check now","Check for updates now","ic_ps_update",new Runnable(){public void run(){status.setText(I18n.cs("Checking for updates…"));ReleaseUpdates.check(SettingsActivity.this,true,new ReleaseUpdates.Result(){public void done(String message){if(!isDestroyed())status.setText(I18n.cs(message));}});}});

        navigation(actions,"Allow update notifications","Turn on notifications in Android settings","ic_ps_update",new Runnable(){public void run(){if(android.os.Build.VERSION.SDK_INT>=33&&checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=0)requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},201);else{Intent settings=new Intent("android.settings.APP_NOTIFICATION_SETTINGS");settings.putExtra("android.provider.extra.APP_PACKAGE",getPackageName());try{startActivity(settings);}catch(Exception unavailable){AppToast.makeText(SettingsActivity.this,I18n.cs("Open Android app settings to manage notifications."),0).show();}}}});

        navigation(actions,"Download and install","Download and install the update in Pixel Suite","ic_ps_update",new Runnable(){public void run(){beginUpdateInstall();}});

        finishGroup(actions);

    }

    private boolean mGmsScopeShown;
    private void showGmsScopeDialog(boolean reboot){
        if(mGmsScopeShown)return;
        if(isFinishing()||isDestroyed()||!mPageResumed){GmsScope.checked=false;return;}
        mGmsScopeShown=true;
        showSettingsDialog(new AlertDialog.Builder(this).setTitle(I18n.cs(reboot?GmsScope.REBOOT_TITLE:GmsScope.TITLE)).setMessage(I18n.cs(reboot?GmsScope.REBOOT_MESSAGE:GmsScope.MESSAGE)).setPositiveButton(I18n.cs("OK"),null).create());
    }

    private final java.util.List<AlertDialog> mSettingsDialogs=new java.util.ArrayList<AlertDialog>();

    private void showSettingsDialog(final AlertDialog dialog){

        dialog.setCancelable(true);dialog.setCanceledOnTouchOutside(true);dialog.show();Rtl.dialog(dialog);mSettingsDialogs.add(dialog);dialog.setOnDismissListener(new DialogInterface.OnDismissListener(){public void onDismiss(DialogInterface d){mSettingsDialogs.remove(dialog);}});

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

        else if(FrostedDialog.wanted(this)){window.setBackgroundDrawable(new FrostedDialog(this,dp(28)));window.setDimAmount(0f);}

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
            points.clear();points.add("Hold an app and choose Rename. It shows right away everywhere.");
            points.add("Reset restores the original name. Work-profile and Private Space apps keep their names.");
        }else if("recents".equals(id)){
            points.clear();points.add("Lock a card from its menu to keep it when you clear apps.");
            points.add("For fingerprint or PIN protection, use Privacy & Security.");
            points.add("Turn your phone sideways in recents for a cleaner view.");
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
                if(end>=0)end+="Telegram for the APK.".length();
                else{String tg=I18n.t("Telegram");end=linked.toString().indexOf(tg);if(end>=0)end+=tg.length();}
                if(end>=0){linked.insert(end,"\u00A0\uFFFC");
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
                    note.setText(I18n.cs(linked));note.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());note.setHighlightColor(android.graphics.Color.TRANSPARENT);
                }
            }
        }
    }

    private void changelogEntry(LinearLayout page,String heading,String body){
        section(page,heading);
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(18),dp(16),dp(18),dp(18));card.setBackground(AppSurfaces.card(this,20));
        String[] blocks=body.trim().split("\\n\\s*\\n");
        boolean any=false;
        for(int b=0;b<blocks.length;b++){
            String[] lines=blocks[b].trim().split("\\n");
            if(lines.length==0||lines[0].trim().isEmpty())continue;
            boolean list=lines.length>1;
            if(!list&&b>0&&b==blocks.length-1){
                TextView foot=text(lines[0].trim(),14,mTextSecondary,false);foot.setText(I18n.cs(ChangelogText.format(lines[0].trim())));foot.setLineSpacing(dp(3),1);foot.setTextIsSelectable(true);
                LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(-1,-2);fp.topMargin=any?dp(26):0;card.addView(foot,fp);any=true;continue;
            }
            if(!list){
                TextView intro=text(lines[0].trim(),16,mText,false);intro.setText(I18n.cs(ChangelogText.format(lines[0].trim())));intro.setLineSpacing(dp(4),1);intro.setTextIsSelectable(true);
                LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(-1,-2);ip.topMargin=any?dp(26):0;card.addView(intro,ip);any=true;
            }else{
                TextView title=text(lines[0].trim(),17,mText,true);
                LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-1,-2);tp.topMargin=any?dp(32):0;card.addView(title,tp);any=true;
                for(int i=1;i<lines.length;i++){
                    String line=lines[i].trim();if(line.isEmpty())continue;
                    String[] item=ChangelogText.split(line);
                    LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);
                    TextView mark=text(item[0],"\u2022".equals(item[0])?16:15,mTextSecondary,false);mark.setGravity(Gravity.CENTER_HORIZONTAL);mark.setIncludeFontPadding(true);
                    TextView words=text(item[1],15,mText,false);words.setText(I18n.cs(ChangelogText.item(item[1])));words.setLineSpacing(dp(3),1);words.setTextIsSelectable(true);
                    row.addView(mark,new LinearLayout.LayoutParams(dp(30),-2));row.addView(words,new LinearLayout.LayoutParams(0,-2,1));
                    LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2);rp.topMargin=dp(i==1?14:24);card.addView(row,rp);
                }
            }
        }
        if(!any)return;
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(12);page.addView(card,lp);
    }

    private void chromeGuidePart(LinearLayout card,String title,String body){
        TextView heading=text(title,15,mText,true);heading.setPadding(0,dp(12),0,dp(4));card.addView(heading,new LinearLayout.LayoutParams(-1,-2));
        TextView detail=text(body,15,mTextSecondary,false);detail.setLineSpacing(dp(3),1);card.addView(detail,new LinearLayout.LayoutParams(-1,-2));
    }

    private void explanation(LinearLayout page,String value){

        TextView t=text(value,15,mTextSecondary,false);t.setTag("ps_explanation");t.setPadding(dp(18),dp(16),dp(18),dp(18));t.setLineSpacing(dp(3),1);

        GradientDrawable bg=AppSurfaces.card(this,20);t.setBackground(bg);

        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(12);page.addView(t,lp);

    }

    private java.util.List<String> gboardBarChoice(){
        java.util.List<String> out=new java.util.ArrayList<String>();
        try{String v=android.provider.Settings.Secure.getString(getContentResolver(),GboardPlus.KEY_BAR);if(v!=null&&v.indexOf(':')>0)for(String id:v.substring(v.indexOf(':')+1).split(","))if(!id.isEmpty()&&!out.contains(id))out.add(id);}catch(Throwable ignored){}
        return out;
    }
    private String gboardBarLabel(String id){return GboardBarEditor.label(id);}
    private String gboardBarSummary(){
        java.util.List<String> c=gboardBarChoice();
        StringBuilder x=new StringBuilder();
        if(gboardSecure(GboardPlus.KEY_ROWS))x.append(gboardSecure(GboardPlus.KEY_ROWS_TOP)?" · Two rows, shortcuts on top":" · Two rows");
        if(gboardSecure(GboardPlus.KEY_HIDE_MENU)&&gboardSecure(GboardPlus.KEY_HIDE_RIGHT))x.append(" · Side buttons hidden");
        else if(gboardSecure(GboardPlus.KEY_HIDE_MENU))x.append(" · Menu button hidden");
        else if(gboardSecure(GboardPlus.KEY_HIDE_RIGHT))x.append(" · Emoji/voice hidden");
        if(c.isEmpty())return "Up to 7 shortcuts, their order, the side buttons and two rows"+x;
        StringBuilder b=new StringBuilder();for(String id:c){if(b.length()>0)b.append(", ");b.append(gboardBarLabel(id));}return b.append(x).toString();
    }
    private boolean gboardSecure(String key){try{return android.provider.Settings.Secure.getInt(getContentResolver(),key,0)==1;}catch(Throwable t){return false;}}
    private float[] gboardGeo(){
        float[] g={1f,0.5f,1f,0f};
        try{String v=android.provider.Settings.Secure.getString(getContentResolver(),GboardPlus.KEY_GEO);
            if(v!=null&&!v.trim().isEmpty()){String[] f=v.split(",");for(int i=0;i<4&&i<f.length;i++)g[i]=Float.parseFloat(f[i].trim());}}catch(Throwable ignored){}
        return g;
    }
    private String gboardAutoSummary(){return "Per language";}
    private String gboardGeoSummary(){
        float[] g=gboardGeo();
        if(Math.abs(g[0]-1f)<0.005f&&Math.abs(g[2]-1f)<0.005f&&Math.round(g[3])==0)return "Move and resize the keyboard";
        int up=Math.round(g[3]);return Math.round(g[0]*100)+"% wide · "+Math.round(g[2]*100)+"% tall · "+(up>=0?up+" dp up":(-up)+" dp down");
    }
    private void gboardResizer(){
        if(!PatronAccess.unlocked()){PatronPaper.open(this,0,null);return;}
        float[] g=gboardGeo();
        final boolean[] saved={false};
        GboardResizer.open(this,g[0],g[1],g[2],Math.round(g[3]),mDark,mBg,mCard,mText,mTextSecondary,mAccent,new GboardResizer.Saver(){public void save(float w,float cx,float h,int up){
            final String cmd="settings put secure "+GboardPlus.KEY_GEO+" "+GboardResizer.value(w,cx,h,up)+" && settings put secure "+GboardPlus.KEY_FREE+" 0 && am force-stop "+SuperDarkApps.GBOARD;
            IO.execute(new Runnable(){public void run(){final boolean ok=Root.run(cmd)!=null;mMain.post(new Runnable(){public void run(){if(isFinishing()||isDestroyed())return;if(ok)saved[0]=true;android.widget.Toast.makeText(SettingsActivity.this,I18n.cs(ok?"Saved. Gboard restarts with the new size":"Couldn't save (root)"),android.widget.Toast.LENGTH_SHORT).show();}});}});
        }},new Runnable(){public void run(){if(saved[0]&&!isFinishing()&&!isDestroyed())recreate();}});
    }
    private void gboardBarEditor(){
        if(!PatronAccess.unlocked()){PatronPaper.open(this,0,null);return;}
        IO.execute(new Runnable(){public void run(){java.util.List<String> lines=Root.run("cat /data/data/"+SuperDarkApps.GBOARD+"/cache/pixelsuite_gboard_ids 2>/dev/null");final String list=lines==null?null:android.text.TextUtils.join("\n",lines);mMain.post(new Runnable(){public void run(){if(isFinishing()||isDestroyed())return;GboardBarEditor.gboardList(list);gboardBarEditorOpen();}});}});
    }
    private void gboardBarEditorOpen(){
        GboardBarEditor.open(this,gboardBarChoice(),gboardSecure(GboardPlus.KEY_HIDE_MENU),gboardSecure(GboardPlus.KEY_HIDE_RIGHT),gboardSecure(GboardPlus.KEY_ROWS),gboardSecure(GboardPlus.KEY_ROWS_TOP),mDark,mBg,mCard,mText,mTextSecondary,mAccent,new GboardBarEditor.Saver(){public void save(java.util.List<String> order,boolean hideMenu,boolean hideRight,boolean rows,boolean rowsTop){
            StringBuilder v=new StringBuilder(String.valueOf(System.currentTimeMillis())).append(':');for(int i=0;i<order.size();i++){if(i>0)v.append(',');v.append(order.get(i));}
            final String cmd="settings put secure "+GboardPlus.KEY_BAR+" "+v+" && settings put secure "+GboardPlus.KEY_HIDE_MENU+" "+(hideMenu?1:0)+" && settings put secure "+GboardPlus.KEY_HIDE_RIGHT+" "+(hideRight?1:0)+" && settings put secure "+GboardPlus.KEY_ROWS+" "+(rows?1:0)+" && settings put secure "+GboardPlus.KEY_ROWS_TOP+" "+(rowsTop?1:0)+" && am force-stop "+SuperDarkApps.GBOARD;
            IO.execute(new Runnable(){public void run(){final boolean ok=Root.run(cmd)!=null;mMain.post(new Runnable(){public void run(){if(isFinishing()||isDestroyed())return;android.widget.Toast.makeText(SettingsActivity.this,I18n.cs(ok?"Saved. Gboard restarts with your toolbar":"Couldn't save (root)"),android.widget.Toast.LENGTH_SHORT).show();if(ok)recreate();}});}});
        }});
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

        row.setClickable(true);row.setFocusable(true);row.setContentDescription(I18n.cs(title));row.setTag("search.anchor:"+title);row.setOnClickListener(new View.OnClickListener(){public void onClick(View v){if(PatronAccess.protectedPage(getIntent().getStringExtra("page"))&&!PatronAccess.unlocked()){PatronPaper.open(SettingsActivity.this,0,null);return;}action.run();}});group.addView(row,new LinearLayout.LayoutParams(-1,-2));

    }

    private void updateChoiceLabel(String key,int value){

        TextView label=mChoiceLabels.get(key);String[] items=mChoiceItems.get(key);Integer offset=mChoiceOffsets.get(key);

        if(label!=null&&items!=null)label.setText(I18n.cs(items[Math.max(0,Math.min(items.length-1,value-(offset==null?0:offset)))]));

        if(LauncherFeatures.LABEL_LEVEL.equals(key)&&mLabelSlider!=null){mLabelSlider.setEnabled(true);mLabelSlider.setProgress(IconLabelScale.level(value));showLabelSize(value);}

        if(SurfaceStyle.KEY.equals(key)&&mStyleSlider!=null){mSavedStyle=Math.max(0,Math.min(2,value));mStyleLoaded=true;updateStyleControls();}
        if(DarkPages.KEY_WHEN.equals(key)&&mDarkTimes!=null)mDarkTimes.setVisibility(value==DarkPages.SCHEDULE?View.VISIBLE:View.GONE);

        ImageView icon=mChoiceIcons.get(key);

        if(icon!=null){

            String name=folderGridKey(key)?gridIcon(key):Feature.LOCK_SHORTCUTS.equals(key)?"ic_ps_lock_options":DarkPages.KEY_STYLE.equals(key)?"ic_ps_cr_dark":DarkPages.KEY_WHEN.equals(key)?"ic_ps_cr_schedule":value==1?"ic_ps_double_tap":"ic_ps_single_tap";

            Drawable d=drawable(name);if(d!=null){d=d.mutate();d.setTint(mAccent);icon.setImageDrawable(d);}

        }

    }

    private void showLabelSize(int value){

        int level=IconLabelScale.level(value);

        TextView meter=mChoiceLabels.get(LauncherFeatures.LABEL_LEVEL);

        if(meter!=null)meter.setText(I18n.cs("Level "+(level+1)+" · "+IconLabelScale.name(level)+(level==2?" (Default)":"")));

        if(mLabelPreview!=null)mLabelPreview.setTextSize(16*IconLabelScale.factor(level));

        for(int i=0;i<5;i++)if(mLabelStops[i]!=null){mLabelStops[i].setTextColor(i==level?mAccent:mTextSecondary);mLabelStops[i].setTypeface(null,i==level?Typeface.BOLD:Typeface.NORMAL);}

    }

    private void showStyle(int value){
        TextView meter=mChoiceLabels.get(SurfaceStyle.KEY);
        if(meter!=null)meter.setText(I18n.cs(STYLE_LABELS[value]));
        if(mStylePreview!=null)mStylePreview.setStyle(value);
        for(int i=0;i<3;i++)if(mStyleStops[i]!=null){
            mStyleNames[i].setTextColor(i==value?mAccent:mText);
            mStyleNames[i].setTypeface((i==value?Typeface.create("sans-serif",Typeface.BOLD):I18n.medium()));
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
        mStyleSlider.setContentDescription(I18n.cs("Surface style, three options"));box.addView(mStyleSlider,new LinearLayout.LayoutParams(-1,dp(48)));
        LinearLayout stops=new LinearLayout(this);stops.setOrientation(0);stops.setGravity(Gravity.TOP);stops.setBaselineAligned(false);
        String[] captions={"No blur anywhere","Google's own look","Blurred and tinted"};
        for(int i=0;i<3;i++){
            final int stop=i;LinearLayout column=new LinearLayout(this);column.setOrientation(1);column.setGravity(Gravity.TOP|Gravity.CENTER_HORIZONTAL);column.setPadding(dp(4),dp(4),dp(4),dp(8));
            TextView name=text(STYLE_LABELS[i],14,mText,false);name.setTypeface(I18n.medium());name.setSingleLine(true);name.setEllipsize(android.text.TextUtils.TruncateAt.END);name.setGravity(Gravity.CENTER);column.addView(name,new LinearLayout.LayoutParams(-1,-2));
            TextView description=text(captions[i],12,mTextSecondary,false);description.setMaxLines(2);description.setGravity(Gravity.TOP|Gravity.CENTER_HORIZONTAL);LinearLayout.LayoutParams descSize=new LinearLayout.LayoutParams(-1,-2);descSize.topMargin=dp(3);column.addView(description,descSize);
            column.setContentDescription(I18n.cs(STYLE_LABELS[i]+", "+captions[i]));column.setEnabled(false);column.setAlpha(.42f);column.setOnClickListener(v->{if(mStyleLoaded&&mStyleSlider.isEnabled()){mStyleSlider.setProgress(stop);saveStyle(stop);}});
            mStyleStops[i]=column;mStyleNames[i]=name;mStyleDescriptions[i]=description;stops.addView(column,new LinearLayout.LayoutParams(0,-2,1));
        }
        box.addView(stops,new LinearLayout.LayoutParams(-1,-2));
        mStylePreview=new SurfacePreview(this,mDark,mAccent,mText);mStylePreview.setContentDescription(I18n.cs("Preview of the chosen surface style on a folder over a wallpaper"));
        LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(-1,dp(220));pp.topMargin=dp(12);box.addView(mStylePreview,pp);
        mStyleSuperDarkNote=text("With Super Dark-Mode, panels stay black.",15,mTextSecondary,false);mStyleSuperDarkNote.setLineSpacing(dp(3),1);mStyleSuperDarkNote.setVisibility(View.GONE);LinearLayout.LayoutParams noteSize=new LinearLayout.LayoutParams(-1,-2);noteSize.topMargin=dp(12);box.addView(mStyleSuperDarkNote,noteSize);
        mStyleSlider.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener(){
            public void onStartTrackingTouch(android.widget.SeekBar slider){if(mStylePreview!=null){mStylePreview.setInteracting(true);mStylePreview.setStyle(slider.getProgress());}}
            public void onStopTrackingTouch(android.widget.SeekBar slider){if(mStylePreview!=null){mStylePreview.setStyle(slider.getProgress());mStylePreview.setInteracting(false);}}
            public void onProgressChanged(android.widget.SeekBar slider,int value,boolean user){showStyle(value);if(user&&!mUpdating)saveStyle(value);}
        });
        group.addView(box,new LinearLayout.LayoutParams(-1,-2));
    }

    private void updateStyleControls(){
        if(mStyleSlider==null)return;
        mStyleSlider.setEnabled(mStyleLoaded);mStyleSlider.setAlpha(mStyleLoaded?1f:.42f);mStyleSlider.setContentDescription(I18n.cs("Surface style, three options"));
        if(mStyleSuperDarkNote!=null){boolean dark=(getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES;mStyleSuperDarkNote.setVisibility(dark&&mStyleSuperDark?View.VISIBLE:View.GONE);}
        mStyleSlider.setProgress(mSavedStyle);showStyle(mSavedStyle);
    }

    private void saveStyle(final int value){

        if(!mStyleLoaded)return;

        mSavedStyle=value;showStyle(value);mSaves++;

        final boolean blurOff=value==SurfaceStyle.OFF||SuperDark.inEffect(this);

        final String cmd="settings put secure "+SurfaceStyle.KEY+" "+value+"; settings put global disable_window_blurs "+(blurOff?1:0)+"; am force-stop "+PhotosEditor.PACKAGE+" >/dev/null 2>&1; true";

        IO.execute(new Runnable(){public void run(){final boolean ok=Root.run(cmd)!=null;mMain.post(new Runnable(){public void run(){if(isFinishing()||isDestroyed())return;if(ok){SurfaceStyle.refresh();RestartPill.changed(SurfaceStyle.KEY);if(mRestart!=null)mRestart.refresh();}if(!ok){showRootBanner(true);AppToast.makeText(SettingsActivity.this,I18n.cs("Could not save the surface style"),Toast.LENGTH_SHORT).show();load();}}});}});

    }

    private void labelSize(LinearLayout group){

        LinearLayout box=new LinearLayout(this);box.setOrientation(1);box.setPadding(dp(18),dp(12),dp(18),dp(20));

        TextView meter=text("Loading…",18,mAccent,true);meter.setGravity(Gravity.CENTER);box.addView(meter,new LinearLayout.LayoutParams(-1,-2));

        mChoiceLabels.put(LauncherFeatures.LABEL_LEVEL,meter);

        mChoiceItems.put(LauncherFeatures.LABEL_LEVEL,new String[]{"Smallest","Small","Medium","Large","Largest"});

        mChoiceOffsets.put(LauncherFeatures.LABEL_LEVEL,0);

        mLabelSlider=new LabelSizeSlider(this,mAccent,mDark?0xff686970:0xff9d9da5);mLabelSlider.setEnabled(false);

        mLabelSlider.setProgressTintList(ColorStateList.valueOf(mAccent));mLabelSlider.setThumbTintList(ColorStateList.valueOf(mAccent));

        mLabelSlider.setContentDescription(I18n.cs("Icon label size, five levels"));

        box.addView(mLabelSlider,new LinearLayout.LayoutParams(-1,dp(48)));

        LinearLayout stops=new LinearLayout(this);stops.setOrientation(0);

        for(int i=0;i<5;i++){final int stop=i;TextView label=text((i+1)+"\n"+IconLabelScale.name(i),13,mTextSecondary,false);label.setGravity(Gravity.TOP|Gravity.CENTER_HORIZONTAL);label.setPadding(dp(5),dp(4),dp(5),dp(8));label.setContentDescription(I18n.cs("Level "+(i+1)+", "+IconLabelScale.name(i)));label.setOnClickListener(new View.OnClickListener(){public void onClick(View v){if(mLabelSlider.isEnabled()){mLabelSlider.setProgress(stop);saveLabelSize(stop);}}});mLabelStops[i]=label;stops.addView(label,new LinearLayout.LayoutParams(0,dp(56),1));}

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

        IO.execute(new Runnable(){public void run(){final boolean ok=Root.run("settings put secure "+LauncherFeatures.LABEL_LEVEL+" "+IconLabelScale.level(value))!=null;mMain.post(new Runnable(){public void run(){if(isFinishing()||isDestroyed())return;if(ok){RestartPill.changed(LauncherFeatures.LABEL_LEVEL);if(mRestart!=null)mRestart.refresh();}if(!ok){showRootBanner(true);AppToast.makeText(SettingsActivity.this,I18n.cs("Could not save text size"),Toast.LENGTH_SHORT).show();load();}}});}});

    }

    private LinearLayout mNavBody,mNavHome;
    private final Map<String,TextView> mNavLabels=new LinkedHashMap<String,TextView>();
    private final Map<String,String> mNavSaved=new LinkedHashMap<String,String>();
    private String navValue(String key,String fallback){String saved=mNavSaved.get(key);if(saved!=null)return saved;try{String value=android.provider.Settings.Secure.getString(getContentResolver(),key);return value==null?fallback:value;}catch(Throwable ignored){return fallback;}}
    private void buildNavigationButtons(LinearLayout page){
        LinearLayout toggle=group(page);row(toggle,NavButtons.KEY,"ic_ps_navigation",mAccent,"Navigation buttons","Buttons beside the gesture bar");finishGroup(toggle);
        section(page,"How it works");explanation(page,"Choose Back, Home or Recent for each side. Hold Home for Circle to Search or Gemini.");
        mNavBody=new LinearLayout(this);mNavBody.setOrientation(1);page.addView(mNavBody,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout sides=group(mNavBody);
        String[] labels={"Home","Back","Recent","Hide"},values={"home","back","recent","hide"};
        navChoice(sides,NavButtons.LEFT,"Left button",labels,values,"back");
        navChoice(sides,NavButtons.RIGHT,"Right button",labels,values,"recent");finishGroup(sides);
        mNavHome=group(mNavBody);navChoice(mNavHome,NavButtons.HOLD_HOME,"Hold Home for",new String[]{"Circle to Search","Gemini","Nothing"},new String[]{"circle","gemini","none"},"circle");finishGroup(mNavHome);
        explanation(mNavBody,"Swipe gestures still work.");
        updateNavigationButtons();
    }
    private void navChoice(LinearLayout group,final String key,final String title,final String[] labels,final String[] values,final String fallback){
        String current=navValue(key,fallback);int selected=java.util.Arrays.asList(values).indexOf(current);if(selected<0)selected=java.util.Arrays.asList(values).indexOf(fallback);
        navigation(group,title,labels[Math.max(0,selected)],SystemToasts.KEY.equals(key)?"ic_ps_toast":NavButtons.LEFT.equals(key)?"ic_ps_nav_left":NavButtons.RIGHT.equals(key)?"ic_ps_nav_right":AodPlus.KEY_SECONDS.equals(key)?"ic_ps_aod_timer":Feature.FLASHLIGHT.equals(key)?"ic_ps_power_double":VolumeCamera.KEY_ACTION.equals(key)?"ic_ps_volume_double":PowerHold.KEY.equals(key)?"ic_ps_power_hold":PowerHold.KEY_LOCKED.equals(key)?"ic_ps_lock_options":"ic_ps_nav_hold",()->{
            int choice=java.util.Arrays.asList(values).indexOf(navValue(key,fallback));
            showSettingsDialog(new AlertDialog.Builder(this).setTitle(I18n.cs(title)).setSingleChoiceItems(I18n.all(labels),Math.max(0,choice),(dialog,which)->{
                dialog.dismiss();mSaves++;final String value=values[which];IO.execute(()->{final boolean ok=Root.run(navCommand(key,value))!=null;mMain.post(()->{if(isFinishing()||isDestroyed())return;if(ok){mNavSaved.put(key,value);mNavLabels.get(key).setText(I18n.cs(labels[which]));updateNavigationButtons();}else{showRootBanner(true);AppToast.makeText(this,I18n.cs("Couldn't save: root access needed"),Toast.LENGTH_SHORT).show();}});});
            }).create());
        });mNavLabels.put(key,navigationSummary(group));
    }
    private static String navCommand(String key,String value){
        String put="settings put secure "+key+" "+value;
        if(Feature.FLASHLIGHT.equals(key))return put+"; settings put secure "+Flashlight.KEY_TARGET+" 0; settings put secure "+Flashlight.KEY_ENABLED+" "+("2".equals(value)?0:1)+"; settings put secure camera_double_tap_power_gesture_disabled "+("2".equals(value)?1:0);
        if(VolumeCamera.KEY_ACTION.equals(key))return put+"; settings put secure "+Feature.VOLDOWN_CAMERA+" "+("off".equals(value)?0:1);
        return put;
    }
    private void updateNavigationButtons(){
        if(mNavBody==null)return;Switch toggle=mSwitches.get(NavButtons.KEY);boolean on=toggle!=null&&toggle.isChecked();mNavBody.setVisibility(on?View.VISIBLE:View.GONE);
        mNavHome.setVisibility(View.VISIBLE);
    }

    private LinearLayout mDarkTimes;private TextView mDarkFrom,mDarkTo;
    private int darkSetting(String key,int fallback){try{return android.provider.Settings.Secure.getInt(getContentResolver(),key,fallback);}catch(Throwable t){return fallback;}}
    private void updateDarkTimes(){
        if(mDarkFrom!=null)mDarkFrom.setText(I18n.cs(DarkPages.time(this,darkSetting(DarkPages.KEY_FROM,DarkPages.DEFAULT_FROM))));
        if(mDarkTo!=null)mDarkTo.setText(I18n.cs(DarkPages.time(this,darkSetting(DarkPages.KEY_TO,DarkPages.DEFAULT_TO))));
    }
    private void pickDarkTime(final String key,String title){
        if(!PatronAccess.unlocked()){PatronPaper.open(this,0,null);return;}
        final int current=darkSetting(key,DarkPages.KEY_FROM.equals(key)?DarkPages.DEFAULT_FROM:DarkPages.DEFAULT_TO);
        final boolean h24=android.text.format.DateFormat.is24HourFormat(this);
        LinearLayout wheels=new LinearLayout(this);wheels.setOrientation(LinearLayout.HORIZONTAL);wheels.setGravity(Gravity.CENTER);wheels.setPadding(dp(24),dp(8),dp(24),0);
        final android.widget.NumberPicker hours=new android.widget.NumberPicker(this),minutes=new android.widget.NumberPicker(this),half=new android.widget.NumberPicker(this);
        String[] mins=new String[60];for(int i=0;i<60;i++)mins[i]=String.format(java.util.Locale.ROOT,"%02d",i);
        int h=current/60,m=current%60;
        if(h24){String[] hs=new String[24];for(int i=0;i<24;i++)hs[i]=String.format(java.util.Locale.ROOT,"%02d",i);hours.setMinValue(0);hours.setMaxValue(23);hours.setDisplayedValues(hs);hours.setValue(h);}
        else{hours.setMinValue(1);hours.setMaxValue(12);hours.setValue(h%12==0?12:h%12);}
        minutes.setMinValue(0);minutes.setMaxValue(59);minutes.setDisplayedValues(mins);minutes.setValue(m);
        java.text.DateFormatSymbols symbols=new java.text.DateFormatSymbols();half.setMinValue(0);half.setMaxValue(1);half.setDisplayedValues(symbols.getAmPmStrings());half.setValue(h<12?0:1);
        for(android.widget.NumberPicker wheel:new android.widget.NumberPicker[]{hours,minutes,half}){wheel.setWrapSelectorWheel(wheel!=half);wheel.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);if(android.os.Build.VERSION.SDK_INT>=29)try{android.widget.NumberPicker.class.getMethod("setTextColor",int.class).invoke(wheel,mText);}catch(Throwable ignored){}}
        LinearLayout.LayoutParams wheel=new LinearLayout.LayoutParams(-2,-2);wheel.leftMargin=dp(6);wheel.rightMargin=dp(6);
        wheels.addView(hours,wheel);TextView colon=text(":",22,mText,true);wheels.addView(colon);wheels.addView(minutes,wheel);if(!h24)wheels.addView(half,wheel);
        showSettingsDialog(new AlertDialog.Builder(this).setTitle(I18n.cs(title)).setView(wheels).setNegativeButton(I18n.cs("Cancel"),null).setPositiveButton(I18n.cs("Set"),new DialogInterface.OnClickListener(){public void onClick(DialogInterface dialog,int which){
            int hour=h24?hours.getValue():(hours.getValue()%12)+(half.getValue()==1?12:0);
            final int value=hour*60+minutes.getValue();mSaves++;
            IO.execute(new Runnable(){public void run(){final boolean ok=Root.run("settings put secure "+key+" "+value)!=null;mMain.post(new Runnable(){public void run(){
                if(isFinishing()||isDestroyed())return;
                if(ok)updateDarkTimes();else{showRootBanner(true);AppToast.makeText(SettingsActivity.this,I18n.cs("Couldn't save: root access needed"),Toast.LENGTH_SHORT).show();}
            }});}});
        }}).create());
    }

    private void choice(LinearLayout group,String key,String title,String[] items){choice(group,key,title,items,0);}

    private void choice(LinearLayout group,final String key,final String title,final String[] items,final int offset){

        LinearLayout row=new LinearLayout(this);row.setOrientation(0);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(18),dp(22),dp(18),dp(22));row.setMinimumHeight(dp(96));

        ImageView icon=new ImageView(this);Drawable d=drawable(folderGridKey(key)?gridIcon(key):Feature.LOCK_SHORTCUTS.equals(key)?"ic_ps_lock_options":DarkPages.KEY_STYLE.equals(key)?"ic_ps_cr_dark":DarkPages.KEY_WHEN.equals(key)?"ic_ps_cr_schedule":"ic_ps_single_tap");if(d!=null){d=d.mutate();d.setTint(mAccent);icon.setImageDrawable(d);}

        LinearLayout.LayoutParams iconParams=new LinearLayout.LayoutParams(dp(28),dp(28));iconParams.rightMargin=dp(18);row.addView(icon,iconParams);mChoiceIcons.put(key,icon);

        LinearLayout labels=new LinearLayout(this);labels.setOrientation(1);labels.addView(text(title,15,mTextSecondary,true));

        TextView value=text("Loading\u2026",18,mAccent,true);value.setPadding(0,dp(8),0,0);labels.addView(value);

        row.addView(labels,new LinearLayout.LayoutParams(0,-2,1));TextView arrow=text("\u203a",28,mAccent,true);arrow.setPadding(dp(12),0,0,0);row.addView(arrow);

        mChoiceLabels.put(key,value);mChoiceItems.put(key,items);mChoiceOffsets.put(key,offset);

        row.setClickable(true);row.setFocusable(true);row.setOnClickListener(new View.OnClickListener(){public void onClick(View v){

            IO.execute(new Runnable(){public void run(){final int[] current=Root.readSecure(new String[]{key});mMain.post(new Runnable(){public void run(){

                if(isFinishing()||isDestroyed())return;if(current==null){showRootBanner(true);return;}

                showSettingsDialog(new AlertDialog.Builder(SettingsActivity.this).setTitle(I18n.cs(title)).setSingleChoiceItems(I18n.all(items),Math.max(0,Math.min(items.length-1,current[0]-offset)),new DialogInterface.OnClickListener(){public void onClick(DialogInterface dialog,final int which){

                    dialog.dismiss();mSaves++;IO.execute(new Runnable(){public void run(){

                        String command="settings put secure "+key+" "+(which+offset);

                        if(Feature.DOUBLE_TAP_WAKE.equals(key))command+="; settings put secure doze_tap_gesture 1";

                        final boolean ok=Root.run(command)!=null;mMain.post(new Runnable(){public void run(){if(isFinishing()||isDestroyed())return;if(ok){updateChoiceLabel(key,which+offset);RestartPill.changed(key);if(mRestart!=null)mRestart.refresh();}else{showRootBanner(true);AppToast.makeText(SettingsActivity.this,I18n.cs("Could not save this option"),0).show();}}});

                    }});

                }}).setNegativeButton(I18n.cs("Cancel"),null).create());

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

                }else if(GboardMain.KEY.equals(keys[i])){

                    try{values[i]=GboardMain.on(getContentResolver())?1:0;}catch(Throwable ignored){}

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

                        if (ok) {searchSaved(key,on);if(DoNotAnnoy.KEY.equals(key)){DoNotAnnoyTile.refresh(SettingsActivity.this);if(on)DoNotAnnoyPermissions.request(SettingsActivity.this);}updateMiscPicker();if(MiscSystem.DEV_KEY.equals(key))AppToast.makeText(SettingsActivity.this,I18n.cs("Reopen apps that were already open."),Toast.LENGTH_LONG).show();if(MiscSystem.A11Y_KEY.equals(key))AppToast.makeText(SettingsActivity.this,I18n.cs("Changes apply the next time the chosen apps open."),Toast.LENGTH_LONG).show();RestartPill.changed(key); if (mRestart != null) mRestart.refresh(); return; }

                        mUpdating = true;

                        sSwitchValues.put(key,!on);sw.setChecked(!on);searchSaved(key,!on);

                        updateMiscPicker();

                        mUpdating = false;

                        showRootBanner(true);

                        AppToast.makeText(SettingsActivity.this, I18n.cs("Couldn't save: root access needed"),

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
        if(GboardMain.KEY.equals(key)||GboardToolbar.KEY.equals(key)||GboardPlus.KEY_SHORTCUTS.equals(key)||GboardPlus.KEY_KEYS.equals(key)||GboardPlus.KEY_CLIP.equals(key)||GboardPlus.KEY_HIDE_CHIPS.equals(key)||GboardPlus.KEY_HIDE_MENU.equals(key)||GboardPlus.KEY_HIDE_RIGHT.equals(key)||GboardPlus.KEY_DICTATION.equals(key)||GboardPlus.KEY_ROWS.equals(key)||GboardPlus.KEY_ROWS_TOP.equals(key)||GboardPlus.KEY_FREE.equals(key)||GboardPlus.KEY_DEL_LETTERS.equals(key)||GboardPlus.KEY_AR_NO_AUTOCORRECT.equals(key)||GboardFrost.KEY.equals(key))return put+" && am force-stop "+SuperDarkApps.GBOARD;

        if(WatchEsim.KEY.equals(key))return put+" && am force-stop "+WatchEsim.PACKAGE;

        if (Feature.FLASHLIGHT.equals(key) && on) {

            return put + "; settings put secure " + Flashlight.KEY_TARGET + " 0"

                    + "; settings put secure " + Flashlight.KEY_ENABLED + " 1";

        }

        if (Feature.FILES_SORT.equals(key)) return put + "; am force-stop " + RestartPill.FILES + "; am force-stop com.google.android.documentsui; am force-stop com.android.documentsui; true";

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

            target.setContentDescription(I18n.cs("Open Developer options"));

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
            {String all=s.getText().toString();java.util.regex.Matcher end=java.util.regex.Pattern.compile("[.\u0964!?\u061f]\\s+|\u3002").matcher(all);
             if(all.indexOf('\n')<0&&end.find()&&end.end()<all.length())s.setText(all.substring(0,end.start()+1).trim()+"\n"+all.substring(end.end()).trim());}
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

        sw.setContentDescription(I18n.cs(title));

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

        row.setContentDescription(I18n.cs(title));

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

            AppToast.makeText(this,I18n.cs("Developer options aren't available on this device."),Toast.LENGTH_SHORT).show();

        }

    }

    private TextView navigationSummary(LinearLayout group){return (TextView)((LinearLayout)((LinearLayout)group.getChildAt(group.getChildCount()-1)).getChildAt(1)).getChildAt(1);}

    private void refreshBlockerSummary(){

        if(mStoreBlockedCount==null)return;

        IO.execute(new Runnable(){public void run(){

            final java.util.List<String> values=Root.run("settings get secure "+StoreBlocker.KEY+"; settings get secure "+OtaBlocker.KEY+"; settings get secure "+StoreBlocker.UNLOCK_KEY);
            final java.util.Set<String> rooted=new java.util.HashSet<String>();try{rooted.addAll(RootApps.scan().packages);}catch(Throwable ignored){}

            mMain.post(new Runnable(){public void run(){if(isFinishing()||isDestroyed())return;if(values==null||values.size()<3){mStoreBlockedCount.setText(I18n.cs("Couldn't load apps"));mOtaStatus.setText(I18n.cs("Unavailable"));initialReadDone("blocker_summary");return;}

                java.util.Set<String> blocked="null".equals(values.get(0))?new java.util.HashSet<String>(java.util.Arrays.asList(StoreBlocker.CORE)):MiscOptions.parse(values.get(0));

                if(!"1".equals(values.get(2)))blocked.addAll(java.util.Arrays.asList(StoreBlocker.CORE));
                int count=0;for(String pkg:blocked)if(rooted.contains(pkg))count++;else try{getPackageManager().getApplicationInfo(pkg,0);count++;}catch(Exception missing){}

                mStoreBlockedCount.setText(I18n.cs(count+" apps protected from updates"));mOtaStatus.setText(I18n.cs("1".equals(values.get(1))?"On":"Off"));initialReadDone("blocker_summary");

            }});

        }});

    }

    private void updateMiscPicker(){
        updateNavigationButtons();
        updateGboardMain();

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

        TextView b = text("Pixel Suite needs root. Allow it in your root manager, then reopen this screen.", 14, mDark ? 0xFFFFB4AB : 0xFF8C1D18, false);

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

    private Lockable mGboardOptions;

    private void updateGboardMain(){
        if(mGboardOptions==null)return;
        Boolean on=sSwitchValues.get(GboardMain.KEY);
        mGboardOptions.lock(on!=null&&!on);
    }

    private static final class Lockable extends LinearLayout {
        private boolean locked;
        Lockable(android.content.Context c){super(c);setOrientation(VERTICAL);}
        void lock(boolean on){
            if(locked==on)return;
            locked=on;
            animate().cancel();
            if(isAttachedToWindow()&&isShown())animate().alpha(on?0.38f:1f).setDuration(200).setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
            else setAlpha(on?0.38f:1f);
            setDescendantFocusability(on?FOCUS_BLOCK_DESCENDANTS:FOCUS_AFTER_DESCENDANTS);
            setImportantForAccessibility(on?IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS:IMPORTANT_FOR_ACCESSIBILITY_AUTO);
        }
        @Override public boolean onInterceptTouchEvent(android.view.MotionEvent e){return locked||super.onInterceptTouchEvent(e);}
        @Override public boolean onTouchEvent(android.view.MotionEvent e){return locked||super.onTouchEvent(e);}
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

        t.setText(I18n.cs(SettingsText.format(s)));

        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);

        t.setTextColor(color);

        if (medium) t.setTypeface(I18n.medium());

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
        return key==null||key.isEmpty()||key.startsWith("pixelsuite_privacy")||key.equals(PrivacyLock.ENABLED_KEY)||key.equals(PrivacyLock.PROTECT_KEY)||key.equals(PrivacyLock.GRACE_KEY)||key.equals(DoNotAnnoy.KEY)||key.equals("pixelsuite_dna_watch")||key.equals(StoreBlocker.UNLOCK_KEY)||key.equals(OtaBlocker.KEY)||key.equals(OtaBlocker.RESET_KEY)||key.equals(MiscSystem.A11Y_KEY)||key.equals(WatchEsim.KEY)
                ||key.equals(Feature.FLASHLIGHT)||key.equals(Feature.VOLDOWN_CAMERA)||key.equals(VolumeCamera.KEY_ACTION)||key.equals(PowerHold.KEY)||key.equals(PowerHold.KEY_LOCKED);
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
                    else if(GboardMain.KEY.equals(keys[i]))try{values[i]=GboardMain.on(getContentResolver())?1:0;}catch(Throwable ignored){}
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
    private boolean mPanelTouch,mPanelEdge;private float mPanelX,mPanelY;
    @Override public boolean dispatchTouchEvent(android.view.MotionEvent e){
        int action=e.getActionMasked();
        if(action==android.view.MotionEvent.ACTION_DOWN){
            mPanelTouch=false;
            boolean card=mRestart!=null&&mRestart.whyOpen()&&!mRestart.whyOwns(e);
            boolean list=!card&&mLanguage!=null&&mLanguage.isOpen()&&!mLanguage.owns(e);
            if(card||list){mPanelTouch=true;mPanelX=e.getRawX();mPanelY=e.getRawY();mPanelEdge=PanelBack.atBackEdge(getWindow().getDecorView(),e);}
        }
        if(mPanelTouch){
            if(action==android.view.MotionEvent.ACTION_MOVE&&!mPanelEdge&&Math.hypot(e.getRawX()-mPanelX,e.getRawY()-mPanelY)>android.view.ViewConfiguration.get(this).getScaledTouchSlop())closePanels();
            else if(action==android.view.MotionEvent.ACTION_UP)closePanels();
            if(action==android.view.MotionEvent.ACTION_UP||action==android.view.MotionEvent.ACTION_CANCEL)mPanelTouch=false;
            return true;
        }
        return super.dispatchTouchEvent(e);
    }
    private void closePanels(){if(mRestart!=null&&mRestart.whyOpen())mRestart.closeWhy();else if(mLanguage!=null&&mLanguage.isOpen())mLanguage.close();}
    @Override public void onBackPressed(){if(mRestart!=null&&mRestart.back())return;if(mLanguage!=null&&mLanguage.back())return;if(mSearch!=null&&mSearch.back())return;super.onBackPressed();}
    private LanguagePill mLanguage;private static boolean sLanguageFade;
    private void languagePicked(final String code){
        I18n.pick(this,code);AppSearchIndex.forget();
        final android.content.Context app=getApplicationContext();
        new Thread(()->{final boolean ok=Root.run("settings put secure "+I18n.KEY+" "+code)!=null;if(!ok)mMain.post(()->AppToast.makeText(app,I18n.cs("Couldn't save the language"),0).show());},"pixelsuite-language").start();
        sLanguageFade=true;
        if(mAppRoot!=null)mAppRoot.animate().alpha(0f).setDuration(170).withEndAction(this::recreate).start();else recreate();
    }
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
