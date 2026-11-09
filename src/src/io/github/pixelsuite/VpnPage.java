package io.github.pixelsuite;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.*;
import android.net.VpnService;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.util.List;

final class VpnPage {
    private final Activity a;private final LinearLayout page;
    private final int text,text2,accent,onAccent,card;private final boolean dark;
    private TextView status,summary,button,off;private LinearLayout countries;private TextView updated;
    private boolean loading;
    private final Runnable changed=new Runnable(){public void run(){showStatus();showCountries();}};
    private final Runnable tick=new Runnable(){public void run(){if(status!=null&&status.isAttachedToWindow())showStatus();}};

    VpnPage(Activity activity,LinearLayout page,int text,int text2,int accent,int onAccent,int card,boolean dark){
        a=activity;this.page=page;this.text=text;this.text2=text2;this.accent=accent;this.onAccent=onAccent;this.card=card;this.dark=dark;
        build();
    }

    private void build(){
        LinearLayout box=new LinearLayout(a);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(18),dp(18),dp(18),dp(18));box.setBackground(background(24,24,false));
        LinearLayout head=new LinearLayout(a);head.setGravity(Gravity.CENTER_VERTICAL);
        ImageView icon=new ImageView(a);Drawable d=drawable("ic_ps_vpn");if(d!=null){d=d.mutate();d.setTint(SectionColors.ink(1));icon.setImageDrawable(d);}
        icon.setPadding(dp(9),dp(9),dp(9),dp(9));GradientDrawable disc=new GradientDrawable();disc.setShape(GradientDrawable.OVAL);disc.setColor(SectionColors.background(1,dark));icon.setBackground(disc);
        LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(dp(44),dp(44));ip.rightMargin=dp(16);head.addView(icon,ip);
        LinearLayout labels=new LinearLayout(a);labels.setOrientation(LinearLayout.VERTICAL);
        status=text("Not connected",18,text,false);labels.addView(status);
        summary=text("Pick a country, then tap Connect",14,text2,false);summary.setPadding(0,dp(4),0,0);labels.addView(summary);
        head.addView(labels,new LinearLayout.LayoutParams(0,-2,1));box.addView(head);
        button=text("Connect",16,onAccent,true);button.setGravity(Gravity.CENTER);button.setMinHeight(dp(52));
        button.setClickable(true);button.setFocusable(true);
        button.setOnClickListener(new View.OnClickListener(){public void onClick(View v){toggle();}});
        LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,-2);bp.topMargin=dp(16);box.addView(button,bp);
        off=text("Turn off",15,accent,true);off.setGravity(Gravity.CENTER);off.setMinHeight(dp(44));off.setClickable(true);off.setFocusable(true);off.setVisibility(View.GONE);
        off.setOnClickListener(new View.OnClickListener(){public void onClick(View v){PixelVpnService.stop(a);}});
        LinearLayout.LayoutParams op=new LinearLayout.LayoutParams(-1,-2);op.topMargin=dp(6);box.addView(off,op);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(12);page.addView(box,lp);

        TextView heading=text("Country",14,accent,true);heading.setPadding(dp(8),dp(16),dp(8),dp(8));heading.setTag("ps_section");page.addView(heading);
        countries=new LinearLayout(a);countries.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams cl=new LinearLayout.LayoutParams(-1,-2);cl.bottomMargin=dp(12);page.addView(countries,cl);

        LinearLayout refresh=new LinearLayout(a);refresh.setOrientation(LinearLayout.VERTICAL);refresh.setPadding(dp(18),dp(18),dp(18),dp(18));refresh.setMinimumHeight(dp(72));refresh.setGravity(Gravity.CENTER_VERTICAL);
        refresh.addView(text("Refresh servers",18,text,false));updated=text("Open-source servers change during the day",14,text2,false);updated.setPadding(0,dp(4),0,0);refresh.addView(updated);
        refresh.setBackground(background(24,24,true));refresh.setClickable(true);refresh.setOnClickListener(new View.OnClickListener(){public void onClick(View v){load(true);}});
        LinearLayout.LayoutParams rl=new LinearLayout.LayoutParams(-1,-2);rl.bottomMargin=dp(12);page.addView(refresh,rl);

        explanation("Open-source servers run by volunteers around the world. Pixel Suite connects to the fastest one.");
        explanation("Countries without servers right now show as Unavailable.");
        explanation("Add the VPN tile to Quick Settings to connect with one tap.");
        explanation("Chrome only: turn on VPN from Chrome's Extensions. Only Chrome uses it, and websites see that country.");

        status.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){
            public void onViewAttachedToWindow(View v){PixelVpnService.watch(changed);changed.run();}
            public void onViewDetachedFromWindow(View v){PixelVpnService.unwatch(changed);v.removeCallbacks(tick);}
        });
        showStatus();showCountries();load(false);
    }

    private boolean premium(){return PixelVpnService.premium(a);}

    private void toggle(){
        if(!premium()){PatronPaper.open(a,0,null);return;}
        if(PixelVpnService.state()!=PixelVpnService.OFF){
            if(PixelVpnService.scope()==PixelVpnService.CHROME_ONLY)PixelVpnService.phone(a,PixelVpnService.chosen(a));else PixelVpnService.stop(a);
            return;}
        connect(PixelVpnService.chosen(a));
    }

    private void connect(String code){
        Intent consent=VpnService.prepare(a);
        if(consent!=null){a.startActivity(new Intent(a,VpnPermissionActivity.class).putExtra(PixelVpnService.EXTRA_COUNTRY,code));return;}
        try{PixelVpnService.connect(a,code);}catch(Exception e){Toast.makeText(a,I18n.cs("Couldn't start the VPN"),Toast.LENGTH_SHORT).show();}
    }

    private void pick(String code){
        if(premium())PixelVpnService.switchCountry(a,code);else PixelVpnService.choose(a,code);
        showCountries();showStatus();
    }

    private void showStatus(){
        int st=PixelVpnService.state();String place=VpnServers.name(st==PixelVpnService.OFF?PixelVpnService.chosen(a):PixelVpnService.country(),"");
        if(st==PixelVpnService.ON){
            long s=Math.max(0,(System.currentTimeMillis()-PixelVpnService.since())/1000);
            int scope=PixelVpnService.scope();
            status.setText(I18n.cs("Connected · "+place));summary.setText(I18n.cs(String.format(java.util.Locale.ROOT,"%02d:%02d:%02d",s/3600,(s/60)%60,s%60)+(scope==PixelVpnService.CHROME_ONLY?" · Chrome only":scope==PixelVpnService.PHONE_NO_CHROME?" · All apps except Chrome":"")+"\n"+PixelVpnService.detail()));
            button.setText(I18n.cs(scope==PixelVpnService.CHROME_ONLY?"Use for all apps":"Disconnect"));
            status.removeCallbacks(tick);if(status.isAttachedToWindow())status.postDelayed(tick,1000);
        }else if(st==PixelVpnService.CONNECTING){
            status.setText(I18n.cs("Connecting to "+place+"…"));summary.setText(I18n.cs(PixelVpnService.detail()));button.setText(I18n.cs("Cancel"));
        }else{
            String error=PixelVpnService.error();
            status.setText(I18n.cs(error!=null?"Couldn't connect":"Not connected"));
            summary.setText(I18n.cs(error!=null?error:premium()?VpnServers.flag(PixelVpnService.chosen(a))+"  "+place+" is selected":"VPN is part of Patreon premium"));
            button.setText(I18n.cs(premium()?"Connect":"Unlock with Patreon"));
        }
        GradientDrawable pill=new GradientDrawable();pill.setCornerRadius(dp(26));
        off.setVisibility(st!=PixelVpnService.OFF&&PixelVpnService.scope()==PixelVpnService.CHROME_ONLY?View.VISIBLE:View.GONE);
        boolean on=st!=PixelVpnService.OFF&&!(st==PixelVpnService.ON&&PixelVpnService.scope()==PixelVpnService.CHROME_ONLY);pill.setColor(on?(AppSurfaces.active(a)?0xff1f1f1f:dark?0xff3a3a3f:0xffe6e1e5):accent);button.setTextColor(on?text:onAccent);
        GradientDrawable mask=new GradientDrawable();mask.setCornerRadius(dp(26));mask.setColor(Color.WHITE);
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(dark?0x33FFFFFF:0x1F000000),pill,mask));
    }

    private void load(final boolean force){
        if(loading)return;loading=true;if(force)updated.setText(I18n.cs("Refreshing…"));
        new Thread(new Runnable(){public void run(){
            String error=null;
            try{if(force)VpnServers.refresh(a);else VpnServers.ensure(a);}catch(Exception e){error=e.getMessage();}
            final String failed=error;
            a.runOnUiThread(new Runnable(){public void run(){
                loading=false;if(a.isFinishing()||a.isDestroyed())return;
                updated.setText(I18n.cs(failed!=null&&VpnServers.cached(a).isEmpty()?"Couldn't load servers. Tap to try again.":failed!=null?"Couldn't refresh. Showing the last list.":VpnServers.cached(a).size()+" open-source servers available"));
                showCountries();
            }});
        }},"PixelSuite-VPN-list").start();
    }

    private void showCountries(){
        if(countries==null)return;countries.removeAllViews();
        List<VpnServers.Server> all=VpnServers.cached(a);String chosen=PixelVpnService.chosen(a);
        if(all.isEmpty()){
            countries.addView(row("⏳",loading?"Loading servers…":"No servers yet",loading?"Getting the open-source server list":"Tap Refresh servers",false,null));
        }else{
            long best=0;for(VpnServers.Server s:all)best=Math.max(best,s.speed);
            countries.addView(row(VpnServers.flag(VpnServers.FASTEST),"Fastest server","Any country · up to "+VpnServers.speed(best),VpnServers.FASTEST.equals(chosen),VpnServers.FASTEST));
            List<VpnServers.Country> live=VpnServers.countries(all);
            for(VpnServers.Country c:live){
                countries.addView(row(VpnServers.flag(c.code),c.name,c.servers.size()+(c.servers.size()==1?" server":" servers")+" · up to "+VpnServers.speed(c.best),c.code.equals(chosen),c.code));
            }
            for(String k:VpnServers.missing(a,live)){
                View r=row(VpnServers.flag(k),VpnServers.name(k,k),"Unavailable · no servers right now",k.equals(chosen),null);
                r.setAlpha(0.45f);r.setContentDescription(I18n.cs(VpnServers.name(k,k)+", unavailable"+(k.equals(chosen)?", selected":"")));
                countries.addView(r);
            }
        }
        int n=countries.getChildCount();
        for(int i=0;i<n;i++){View r=countries.getChildAt(i);r.setBackground(background(i==0?24:6,i==n-1?24:6,true));if(i>0)((LinearLayout.LayoutParams)r.getLayoutParams()).topMargin=dp(3);}
    }

    private View row(String flag,String title,String sub,boolean selected,final String code){
        LinearLayout row=new LinearLayout(a);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(18),dp(14),dp(18),dp(14));row.setMinimumHeight(dp(72));
        TextView f=text(flag,24,text,false);f.setGravity(Gravity.CENTER);
        GradientDrawable disc=new GradientDrawable();disc.setShape(GradientDrawable.OVAL);disc.setColor(SectionColors.background(1,dark));f.setBackground(disc);
        LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(dp(44),dp(44));fp.rightMargin=dp(16);row.addView(f,fp);
        LinearLayout labels=new LinearLayout(a);labels.setOrientation(LinearLayout.VERTICAL);labels.addView(text(title,18,text,false));
        TextView s=text(sub,14,text2,false);s.setPadding(0,dp(4),0,0);labels.addView(s);row.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
        TextView mark=text(selected?"✓":"",22,accent,true);mark.setPadding(dp(12),0,0,0);row.addView(mark);
        row.setContentDescription(I18n.cs(title+(selected?", selected":"")));
        if(code!=null){row.setClickable(true);row.setFocusable(true);row.setOnClickListener(new View.OnClickListener(){public void onClick(View v){pick(code);}});}
        row.setLayoutParams(new LinearLayout.LayoutParams(-1,-2));
        return row;
    }

    private void explanation(String value){
        TextView t=text(value,15,text2,false);t.setTag("ps_explanation");t.setPadding(dp(18),dp(16),dp(18),dp(18));t.setLineSpacing(dp(3),1);t.setBackground(AppSurfaces.card(a,20));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(12);page.addView(t,lp);
    }

    private Drawable background(float top,float bottom,boolean ripple){
        top=dp(top);bottom=dp(bottom);
        GradientDrawable shape=AppSurfaces.card(a,24);shape.setColor(card);shape.setCornerRadii(new float[]{top,top,top,top,bottom,bottom,bottom,bottom});
        if(!ripple)return shape;
        GradientDrawable mask=new GradientDrawable();mask.setColor(Color.WHITE);mask.setCornerRadii(new float[]{top,top,top,top,bottom,bottom,bottom,bottom});
        return new RippleDrawable(ColorStateList.valueOf(dark?0x33FFFFFF:0x1F000000),shape,mask);
    }

    private TextView text(String s,float sp,int color,boolean medium){
        TextView t=new TextView(a);t.setText(I18n.cs(SettingsText.format(s)));t.setTextSize(TypedValue.COMPLEX_UNIT_SP,sp);t.setTextColor(color);
        if(medium)t.setTypeface(I18n.medium());return t;
    }

    private Drawable drawable(String name){int id=a.getResources().getIdentifier(name,"drawable",a.getPackageName());return id==0?null:a.getDrawable(id);}
    private int dp(float v){return Math.round(v*a.getResources().getDisplayMetrics().density);}
}
