package io.github.pixelsuite;

import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.net.VpnService;
import android.os.Binder;
import android.os.Bundle;
import java.util.ArrayList;
import java.util.List;

public final class VpnProvider extends ContentProvider {
    static final String AUTHORITY="io.github.roviandev.pixelsuite.vpn";
    private static final String[] CALLERS={"com.android.chrome","com.chrome.beta","com.chrome.dev","com.chrome.canary"};
    private static volatile boolean sRefreshing;

    @Override public boolean onCreate(){return true;}

    private String caller(){
        if(Binder.getCallingUid()==android.os.Process.myUid())return "com.android.chrome";
        String[] packages=getContext().getPackageManager().getPackagesForUid(Binder.getCallingUid());
        if(packages!=null)for(String p:packages)for(String c:CALLERS)if(c.equals(p))return p;
        return null;
    }

    @Override public Bundle call(String method,String arg,Bundle extras){
        final String chrome=caller();
        if(chrome==null)throw new SecurityException("Caller not allowed");
        final Context c=getContext();
        long id=Binder.clearCallingIdentity();
        try{
            if("chrome".equals(method)){
                boolean on=extras!=null&&extras.getBoolean("on");
                if(!PixelVpnService.premium(c))return state(c);
                if(on&&VpnService.prepare(c)!=null){Bundle b=state(c);b.putBoolean("consent",true);return b;}
                try{PixelVpnService.chrome(c,on,PixelVpnService.chosen(c),chrome);}
                catch(Throwable blocked){Bundle b=state(c);b.putBoolean("consent",true);return b;}
                return state(c);
            }
            if("country".equals(method)&&arg!=null){
                if(!PixelVpnService.premium(c))return state(c);
                try{PixelVpnService.switchCountry(c,arg);}catch(Throwable blocked){Bundle b=state(c);b.putBoolean("consent",true);return b;}
                return state(c);
            }
            if("refresh".equals(method)){refresh(c,true);return state(c);}
            if("state".equals(method)){if(!VpnServers.fresh(c))refresh(c,false);return state(c);}
            throw new IllegalArgumentException("Unknown method");
        }finally{Binder.restoreCallingIdentity(id);}
    }

    private static void refresh(final Context c,final boolean force){
        if(sRefreshing)return;sRefreshing=true;
        new Thread(new Runnable(){public void run(){
            try{if(force)VpnServers.refresh(c);else VpnServers.ensure(c);}catch(Throwable ignored){}
            finally{sRefreshing=false;try{c.getContentResolver().notifyChange(PixelVpnService.STATE_URI,null);}catch(Throwable ignored){}}
        }},"PixelSuite-VPN-list").start();
    }

    static Bundle state(Context c){
        Bundle b=new Bundle();
        b.putBoolean("premium",PixelVpnService.premium(c));
        b.putInt("state",PixelVpnService.state());
        b.putInt("scope",PixelVpnService.scope());
        b.putBoolean("chromeOn",PixelVpnService.chromeOn());
        b.putBoolean("phoneOn",PixelVpnService.phoneOn());
        String chosen=PixelVpnService.chosen(c);
        b.putString("chosen",chosen);
        String now=PixelVpnService.state()==PixelVpnService.OFF?chosen:PixelVpnService.country();
        b.putString("country",VpnServers.name(now,now));
        b.putString("detail",PixelVpnService.detail());
        b.putString("code",PixelVpnService.serverCode());
        b.putString("error",PixelVpnService.error());
        b.putBoolean("loading",sRefreshing);
        ArrayList<String> rows=new ArrayList<String>();
        List<VpnServers.Server> all=VpnServers.cached(c);
        if(!all.isEmpty()){
            long best=0;for(VpnServers.Server s:all)best=Math.max(best,s.speed);
            rows.add(VpnServers.FASTEST+"\tFastest server\t"+all.size()+"\t"+best);
            List<VpnServers.Country> live=VpnServers.countries(all);
            for(VpnServers.Country k:live)rows.add(k.code+"\t"+k.name+"\t"+k.servers.size()+"\t"+k.best);
            for(String k:VpnServers.missing(c,live))rows.add(k+"\t"+VpnServers.name(k,k)+"\t0\t0\tunavailable");
        }
        b.putStringArrayList("countries",rows);
        return b;
    }

    @Override public Cursor query(Uri uri,String[] p,String s,String[] a,String o){return null;}
    @Override public String getType(Uri uri){return null;}
    @Override public Uri insert(Uri uri,ContentValues v){return null;}
    @Override public int delete(Uri uri,String s,String[] a){return 0;}
    @Override public int update(Uri uri,ContentValues v,String s,String[] a){return 0;}
}
