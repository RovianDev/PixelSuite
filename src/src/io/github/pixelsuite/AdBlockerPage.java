package io.github.pixelsuite;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.database.ContentObserver;
import android.graphics.drawable.RippleDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.*;
import java.util.concurrent.Executor;

final class AdBlockerPage {
    interface Presenter { void show(AlertDialog dialog); }
    private final Activity a;
    private final Executor io;
    private final Presenter presenter;
    private final int ink,muted,accent;
    private final boolean dark;
    private final LinearLayout allowed,levels,removed;
    private final TextView status,count,defaultRow;
    private final EditText website;
    private final Handler main=new Handler(Looper.getMainLooper());
    private ChromeSiteSettings.Snapshot data=new ChromeSiteSettings.Snapshot();
    private boolean disposed,busy,registered;
    private final Runnable membership=new Runnable(){public void run(){if(!disposed)render();}};
    private final ContentObserver observer=new ContentObserver(main){@Override public void onChange(boolean self){reload();}};

    AdBlockerPage(Activity activity,LinearLayout page,Executor executor,int text,int secondary,int accent,boolean dark,Presenter dialogs){
        a=activity;io=executor;ink=text;muted=secondary;this.accent=accent;this.dark=dark;presenter=dialogs;
        status=label("",15,ink,true);status.setPadding(dp(18),dp(16),dp(18),dp(16));status.setBackground(AppSurfaces.card(a,20));add(page,status);status.setOnClickListener(v->gate());
        count=label("Loading site settings…",14,muted,false);count.setPadding(dp(8),dp(8),dp(8),dp(16));add(page,count);
        heading(page,"Filtering level");LinearLayout defaults=card(page);defaultRow=action(defaults,"Optimal","Default for all websites",()->defaultChoice());
        note(page,"Optimal hides search ads. Complete also hides cookie banners and pop-ups. A site's own level comes first.");
        heading(page,"Allowed websites");LinearLayout allowCard=card(page);
        noteInside(allowCard,"Ads aren't filtered on these websites. Tap one to remove it.");
        LinearLayout input=new LinearLayout(a);input.setGravity(Gravity.CENTER_VERTICAL);input.setPadding(dp(12),dp(4),dp(12),dp(12));allowCard.addView(input,new LinearLayout.LayoutParams(-1,-2));
        website=new EditText(a);website.setTextColor(ink);website.setHintTextColor(muted);website.setTextSize(15);website.setSingleLine(true);website.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);website.setImeOptions(EditorInfo.IME_ACTION_DONE);website.setHint(I18n.cs("Add website: domain or URL"));website.setContentDescription(I18n.cs("Add website"));website.setPadding(dp(12),dp(10),dp(12),dp(10));website.setBackground(AppSurfaces.field(a,18));input.addView(website,new LinearLayout.LayoutParams(0,-2,1));
        TextView add=label("Add",15,accent,true);add.setGravity(Gravity.CENTER);add.setMinHeight(dp(48));add.setPadding(dp(14),0,dp(4),0);add.setOnClickListener(v->addAllowed());input.addView(add,new LinearLayout.LayoutParams(-2,-2));
        website.setOnEditorActionListener((v,id,event)->{if(id==EditorInfo.IME_ACTION_DONE){addAllowed();return true;}return false;});website.setOnClickListener(v->{if(!PatronAccess.unlocked())gate();});
        allowed=new LinearLayout(a);allowed.setOrientation(1);allowCard.addView(allowed,new LinearLayout.LayoutParams(-1,-2));
        heading(page,"Per-site levels");levels=card(page);noteInside(levels,"Tap a website to change its level or use the default.");
        heading(page,"Removed elements");removed=card(page);noteInside(removed,"Bring back elements you removed.");
        LinearLayout reset=card(page);action(reset,"Reset all site settings","Clear every website setting",()->confirmReset());
        page.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){public void onViewAttachedToWindow(View v){register();}public void onViewDetachedFromWindow(View v){dispose();}});
        render();reload();
    }
    private void register(){if(registered||disposed)return;registered=true;a.getContentResolver().registerContentObserver(ChromeSiteSettings.URI_SETTINGS,true,observer);PatronAccess.watch(membership);}
    void dispose(){if(disposed)return;disposed=true;if(registered){a.getContentResolver().unregisterContentObserver(observer);PatronAccess.unwatch(membership);registered=false;}}
    private int dp(float n){return Math.round(n*a.getResources().getDisplayMetrics().density);}
    private TextView label(String value,int size,int color,boolean bold){TextView t=new TextView(a);t.setText(I18n.cs(value));t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(I18n.medium());return t;}
    private void add(LinearLayout p,View v){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(12);p.addView(v,lp);}
    private void heading(LinearLayout p,String text){TextView t=label(text,14,muted,true);t.setPadding(dp(8),dp(12),dp(8),dp(8));p.addView(t,new LinearLayout.LayoutParams(-1,-2));}
    private LinearLayout card(LinearLayout p){LinearLayout v=new LinearLayout(a);v.setOrientation(1);v.setBackground(AppSurfaces.card(a,22));add(p,v);return v;}
    private void note(LinearLayout p,String text){TextView t=label(text,14,muted,false);t.setPadding(dp(8),0,dp(8),dp(8));t.setLineSpacing(dp(2),1);add(p,t);}
    private void noteInside(LinearLayout p,String text){TextView t=label(text,14,muted,false);t.setPadding(dp(18),dp(12),dp(18),dp(12));t.setLineSpacing(dp(2),1);p.addView(t,new LinearLayout.LayoutParams(-1,-2));}
    private TextView action(LinearLayout p,String title,String subtitle,Runnable click){
        TextView t=label(title+(subtitle.isEmpty()?"":"\n"+subtitle),15,ink,false);t.setPadding(dp(18),dp(14),dp(18),dp(14));t.setMinHeight(dp(64));t.setLineSpacing(dp(3),1);t.setBackground(new RippleDrawable(ColorStateList.valueOf(dark?0x33ffffff:0x22000000),null,AppSurfaces.card(a,18)));t.setFocusable(true);t.setOnClickListener(v->{if(gate())click.run();});p.addView(t,new LinearLayout.LayoutParams(-1,-2));return t;
    }
    private boolean gate(){if(PatronAccess.unlocked())return true;PatronPaper.open(a,0,null);return false;}
    void reload(){if(disposed)return;io.execute(()->{try{ChromeSiteSettings.importLegacy(a);final ChromeSiteSettings.Snapshot next=ChromeSiteSettings.snapshot(a);main.post(()->{if(disposed)return;data=next;render();});}catch(Throwable failure){main.post(()->{if(!disposed)count.setText(I18n.cs("Couldn't load site settings. Open Chrome once, then reopen this page."));});}});}
    private void change(String op,String host,int level,Runnable done){
        if(busy||!gate())return;busy=true;count.setText(I18n.cs("Saving…"));
        io.execute(()->{try{ChromeSiteSettings.change(a,op,host,level,null);final ChromeSiteSettings.Snapshot next=ChromeSiteSettings.snapshot(a);main.post(()->{if(disposed)return;busy=false;data=next;if(done!=null)done.run();render();});}catch(Throwable failure){main.post(()->{if(disposed)return;busy=false;count.setText(I18n.cs("Couldn't save site settings. Try again."));});}});
    }
    private static String name(int level){return level==0?"No filtering":level==1?"Basic":level==3?"Complete":"Optimal";}
    private void render(){
        status.setText(I18n.cs(PatronAccess.unlocked()?"Included with your Patreon unlock":"Unlock ad blocking with Patreon"));
        website.setFocusable(PatronAccess.unlocked());website.setFocusableInTouchMode(PatronAccess.unlocked());
        if(!busy)count.setText(I18n.cs(data.count()+" website"+(data.count()==1?"":"s")+" with custom settings"));
        defaultRow.setText(I18n.cs(name(data.defaultLevel)+"\nDefault for all websites"));
        allowed.removeAllViews();int n=0;for(Map.Entry<String,Integer> e:data.levels.entrySet())if(e.getValue()==0){String host=e.getKey();action(allowed,host,"No filtering · tap to remove",()->change("resetLevel",host,0,null));n++;}if(n==0)noteInside(allowed,"No allowed websites");
        while(levels.getChildCount()>1)levels.removeViewAt(1);n=0;for(Map.Entry<String,Integer> e:data.levels.entrySet())if(e.getValue()>0){String host=e.getKey();action(levels,host,name(e.getValue()),()->siteChoice(host));n++;}if(n==0)noteInside(levels,"All websites use the default level");
        while(removed.getChildCount()>1)removed.removeViewAt(1);n=0;for(Map.Entry<String,Set<String>> e:data.hidden.entrySet()){String host=e.getKey();action(removed,host,e.getValue().size()+" removed element(s) · Restore",()->change("clearHidden",host,0,null));n++;}if(n==0)noteInside(removed,"No removed elements");
    }
    private void addAllowed(){if(!gate()||busy)return;String host=ChromeSiteSettings.domain(website.getText().toString());if(host==null){website.setError(I18n.cs("Enter a domain or an http/https URL"));return;}change("level",host,0,()->website.setText(""));}
    private void defaultChoice(){
        presenter.show(new AlertDialog.Builder(a).setTitle(I18n.cs("Filtering level")).setSingleChoiceItems(I18n.all(new String[]{"Basic","Optimal","Complete"}),data.defaultLevel-1,(dialog,which)->{dialog.dismiss();change("default",null,which+1,null);}).setNegativeButton(I18n.cs("Cancel"),null).create());
    }
    private void siteChoice(String host){
        Integer current=data.levels.get(host);int selected=current==null?0:current+1;
        presenter.show(new AlertDialog.Builder(a).setTitle(I18n.cs(host)).setSingleChoiceItems(I18n.all(new String[]{"Use default ("+name(data.defaultLevel)+")","No filtering","Basic","Optimal","Complete"}),selected,(dialog,which)->{dialog.dismiss();change(which==0?"resetLevel":"level",host,which-1,null);}).setNegativeButton(I18n.cs("Cancel"),null).create());
    }
    private void confirmReset(){
        presenter.show(new AlertDialog.Builder(a).setTitle(I18n.cs("Reset all site settings?")).setMessage(I18n.cs("Clear every website setting? Your default level stays.")).setNegativeButton(I18n.cs("Cancel"),null).setPositiveButton(I18n.cs("Reset"),(dialog,which)->change("resetAll",null,0,null)).create());
    }
}
