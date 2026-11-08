package io.github.pixelsuite;

import android.app.*;
import android.content.*;
import android.net.VpnService;
import android.os.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.*;

public final class PixelVpnService extends VpnService {

    static final String ACTION_CONNECT="io.github.pixelsuite.vpn.CONNECT",ACTION_STOP="io.github.pixelsuite.vpn.STOP",ACTION_SCOPE="io.github.pixelsuite.vpn.SCOPE",EXTRA_COUNTRY="country",EXTRA_SCOPE="scope",EXTRA_CHROME="chrome";
    static final int OFF=0,CONNECTING=1,ON=2;
    static final int PHONE=0,CHROME_ONLY=1,PHONE_NO_CHROME=2;
    static final android.net.Uri STATE_URI=android.net.Uri.parse("content://"+VpnProvider.AUTHORITY+"/state");
    private static final String PREFS="pixelsuite_vpn",CHANNEL="pixelsuite_vpn";
    private static final int NOTE=7301;

    private static volatile int sState=OFF;
    private static volatile String sCountry,sDetail="",sError;
    private static volatile long sSince;
    private static volatile int sScope=PHONE;
    private static volatile String sServerCode;
    static String serverCode(){return sState==ON?sServerCode:null;}
    private static volatile String sChrome="com.android.chrome";
    private static final CopyOnWriteArrayList<Runnable> sListeners=new CopyOnWriteArrayList<Runnable>();
    private static final Handler MAIN=new Handler(Looper.getMainLooper());

    static int state(){return sState;}
    static String country(){return sCountry;}
    static String detail(){return sDetail;}
    static String error(){return sError;}
    static long since(){return sSince;}
    static int scope(){return sScope;}
    static boolean chromeOn(){return sState!=OFF&&sScope!=PHONE_NO_CHROME;}
    static boolean phoneOn(){return sState!=OFF&&sScope!=CHROME_ONLY;}
    static void watch(Runnable r){sListeners.addIfAbsent(r);}
    static void unwatch(Runnable r){sListeners.remove(r);}
    static SharedPreferences prefs(Context c){return c.getSharedPreferences(PREFS,0);}
    static String chosen(Context c){return prefs(c).getString("country","US");}
    static void choose(Context c,String code){prefs(c).edit().putString("country",code).apply();changed(c);}
    static boolean premium(Context c){try{return PatronAccess.unlocked()||PatronGate.ok(c);}catch(Throwable t){return false;}}

    private static void changed(final Context c){
        MAIN.post(new Runnable(){public void run(){for(Runnable r:sListeners)r.run();}});
        try{VpnTile.refresh(c);}catch(Throwable ignored){}
        try{c.getContentResolver().notifyChange(STATE_URI,null);}catch(Throwable ignored){}
    }

    static void connect(Context c,String code){connect(c,code,PHONE,null);}
    static void connect(Context c,String code,int scope,String chrome){
        Intent i=new Intent(c,PixelVpnService.class).setAction(ACTION_CONNECT).putExtra(EXTRA_COUNTRY,code).putExtra(EXTRA_SCOPE,scope);
        if(chrome!=null)i.putExtra(EXTRA_CHROME,chrome);
        c.startService(i);
    }
    static void scope(Context c,int scope,String chrome){
        Intent i=new Intent(c,PixelVpnService.class).setAction(ACTION_SCOPE).putExtra(EXTRA_SCOPE,scope);
        if(chrome!=null)i.putExtra(EXTRA_CHROME,chrome);
        c.startService(i);
    }
    static void switchCountry(Context c,String code){
        boolean same=code.equals(chosen(c));choose(c,code);
        if(!same&&sState!=OFF)connect(c,code,sScope,null);
    }
    static void phone(Context c,String code){
        if(sState==OFF)connect(c,code,PHONE,null);else scope(c,PHONE,null);
    }
    static void chrome(Context c,boolean on,String code,String pkg){
        if(on){if(sState==OFF)connect(c,code,CHROME_ONLY,pkg);else if(sScope==PHONE_NO_CHROME)scope(c,PHONE,pkg);}
        else{if(sState==OFF)return;if(sScope==CHROME_ONLY)stop(c);else if(sScope==PHONE)scope(c,PHONE_NO_CHROME,pkg);}
    }
    static void stop(Context c){
        if(sState==OFF)return;
        try{c.startService(new Intent(c,PixelVpnService.class).setAction(ACTION_STOP));}catch(Throwable t){}
    }

    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private volatile VpnTunnel client;
    private volatile ParcelFileDescriptor tun;
    private volatile int session;
    private Thread keepAlive;
    private volatile FileOutputStream toTun;
    private volatile String label="";

    @Override public int onStartCommand(Intent intent,int flags,int startId){
        String action=intent==null?null:intent.getAction();
        if(ACTION_STOP.equals(action)){disconnect("Disconnected",true);return START_NOT_STICKY;}
        if(intent!=null&&intent.hasExtra(EXTRA_CHROME))sChrome=intent.getStringExtra(EXTRA_CHROME);
        if(!premium(this)){fail("VPN is a Patreon feature");stopSelf();return START_NOT_STICKY;}
        if(ACTION_SCOPE.equals(action)&&sState!=OFF){
            final int scope=intent.getIntExtra(EXTRA_SCOPE,PHONE);if(scope==sScope)return START_STICKY;
            sScope=scope;changed(this);note();
            if(sState==ON){final int id=session;final VpnTunnel c=client;worker.execute(new Runnable(){public void run(){
                try{if(current(id)&&c!=null)tunnel(id,c);}catch(IOException e){if(current(id))lost(id,e.getMessage());}}});}
            return START_STICKY;
        }
        String code=intent!=null&&intent.hasExtra(EXTRA_COUNTRY)?intent.getStringExtra(EXTRA_COUNTRY):chosen(this);
        if(intent!=null&&ACTION_CONNECT.equals(action))sScope=intent.getIntExtra(EXTRA_SCOPE,PHONE);
        else if(intent==null||!ACTION_SCOPE.equals(action))sScope=PHONE;
        else sScope=intent.getIntExtra(EXTRA_SCOPE,PHONE);
        start(code);
        return START_STICKY;
    }

    @Override public void onRevoke(){disconnect("Another VPN took over",true);}
    @Override public void onDestroy(){disconnect(null,false);worker.shutdownNow();super.onDestroy();}

    private void start(final String code){
        final int id=++session;
        closeClient();
        sCountry=code;sError=null;sState=CONNECTING;sDetail="Finding servers…";sSince=0;changed(this);note();
        worker.execute(new Runnable(){public void run(){work(id,code);}});
    }

    private boolean current(int id){return id==session;}

    private void work(final int id,String code){
        try{
            List<VpnServers.Server> all;
            try{all=VpnServers.ensure(this);}catch(IOException e){throw new IOException("Couldn't load the server list. Check your connection.");}
            sNear=VpnServers.FASTEST.equals(code);
            List<VpnServers.Server> list=VpnServers.candidates(all,code,sNear?40:20);
            if(list.isEmpty())throw new IOException("No open-source servers in "+VpnServers.name(code,code)+" right now");
            List<VpnServers.Server> good=new ArrayList<VpnServers.Server>(),later=new ArrayList<VpnServers.Server>();
            for(VpnServers.Server sv:list)(isBad(sv)?later:good).add(sv);
            if(!later.isEmpty()){good.addAll(later);list=good;}
            String last="No server answered";
            boolean skipUdp=System.currentTimeMillis()<prefs(this).getLong("ovpn_skip_until",0);
            List<Target> udp=skipUdp?new ArrayList<Target>():probeUdp(list,id);
            if(!current(id))return;
            int n=0;
            for(Target t:udp){
                if(!current(id))return;
                n++;sDetail="Trying the faster UDP connection (up to 30 s)…";changed(this);
                final OvpnClient c=new OvpnClient();client=c;
                try{
                    OvpnClient.Config cf=OvpnClient.Config.parse(t.server.config);cf.waitMs=n==1?30000:12000;
                    c.connect(t.server.ip,t.port,cf,"vpn","vpn",protector);
                    prefs(this).edit().putInt("ovpn_stalls",0).remove("ovpn_skip_until").apply();
                    if(!current(id)){c.close();return;}
                    up(id,c,t);return;
                }catch(IOException e){
                    last=e.getMessage()==null?"Connection failed":e.getMessage();
                   c.close();
                    if(last.contains("timed out")){
                        int stalls=prefs(this).getInt("ovpn_stalls",0)+1;
                        SharedPreferences.Editor ed=prefs(this).edit().putInt("ovpn_stalls",stalls);
                        if(stalls>=2){ed.putLong("ovpn_skip_until",System.currentTimeMillis()+12L*3600_000L).putInt("ovpn_stalls",0);}
                        ed.apply();
                        break;
                    }
                }
            }
            List<Target> targets=probe(list,id);
            if(!current(id))return;
            if(targets.isEmpty()&&udp.isEmpty())throw new IOException("Couldn't reach any server in "+VpnServers.name(code,code));
            n=0;
            for(Target t:targets){
                if(!current(id))return;
                n++;sDetail="Connecting to server "+n+" of "+targets.size()+"…";changed(this);
                final SstpClient c=new SstpClient();client=c;
                try{
                    c.connect(t.server.ip,t.port,t.server.name(),"vpn","vpn",protector);
                    if(!current(id)){c.close();return;}
                    up(id,c,t);return;
                }catch(IOException e){
                    last=e.getMessage()==null?"Connection failed":e.getMessage();
                   c.close();
                }
            }
            throw new IOException(last);
        }catch(Exception e){
            if(current(id)){fail(e.getMessage()==null?"Couldn't connect":e.getMessage());stopSelf();}
        }
    }

    private final VpnTunnel.Protector protector=new VpnTunnel.Protector(){
        public boolean protect(Socket s){return PixelVpnService.this.protect(s);}
        public boolean protect(java.net.DatagramSocket s){return PixelVpnService.this.protect(s);}
    };

    private List<Target> probeUdp(List<VpnServers.Server> list,int id){
        sDetail="Checking "+list.size()+" servers…";changed(this);
        final List<Target> all=new ArrayList<Target>();
        for(VpnServers.Server s:list){if(!OvpnClient.Config.parse(s.config).supported())continue;for(int p:s.udpPorts())all.add(new Target(s,p));}
        if(all.isEmpty())return new ArrayList<Target>();
        ExecutorService pool=Executors.newFixedThreadPool(Math.min(160,all.size()));
        final CountDownLatch done=new CountDownLatch(all.size());
        for(final Target t:all)pool.execute(new Runnable(){public void run(){try{t.rtt=OvpnClient.probe(t.server.ip,t.port,protector,1800);}finally{done.countDown();}}});
        try{done.await(3000,TimeUnit.MILLISECONDS);}catch(InterruptedException ignored){}
        pool.shutdownNow();
        List<Target> ok=new ArrayList<Target>();for(Target t:all)if(t.rtt>0)ok.add(t);
        Collections.sort(ok,new Comparator<Target>(){public int compare(Target a,Target b){return Double.compare(b.rank(),a.rank());}});
        List<Target> out=new ArrayList<Target>();Set<String> seen=new HashSet<String>();
        for(Target t:ok)if(seen.add(t.server.ip)){out.add(t);if(out.size()>=4)break;}
        return out;
    }

    private static final class Target{final VpnServers.Server server;final int port;long rtt=-1;Target(VpnServers.Server s,int p){server=s;port=p;}
        double rank(){
            double mbps=Math.max(0.1,server.speed/1e6),r=Math.max(20,rtt);
            if(sNear)return Math.min(mbps,100.0)*Math.pow(80.0/(80.0+r),2)/(1.0+server.sessions/40.0);
            return mbps*(120.0/(120.0+r))/(1.0+server.sessions/40.0);}}
    private static volatile boolean sNear;

    private List<Target> probe(List<VpnServers.Server> list,int id){
        sDetail="Checking "+list.size()+" servers…";changed(this);
        ExecutorService pool=Executors.newFixedThreadPool(Math.min(128,Math.max(1,list.size()*3)));
        final List<Target> all=new ArrayList<Target>();for(VpnServers.Server s:list)for(int p:s.ports())all.add(new Target(s,p));
        final boolean[] ok=new boolean[all.size()];
        final CountDownLatch done=new CountDownLatch(all.size());
        for(int i=0;i<all.size();i++){final int k=i;pool.execute(new Runnable(){public void run(){
            Socket s=new Socket();
            try{s.bind(null);protect(s);long t0=SystemClock.elapsedRealtime();s.connect(new InetSocketAddress(all.get(k).server.ip,all.get(k).port),3000);all.get(k).rtt=SystemClock.elapsedRealtime()-t0;ok[k]=true;}catch(Exception ignored){}
            finally{try{s.close();}catch(IOException ignored){}done.countDown();}
        }});}
        try{done.await(5,TimeUnit.SECONDS);}catch(InterruptedException ignored){}
        pool.shutdownNow();
        List<Target> reachable=new ArrayList<Target>();
        for(int i=0;i<all.size();i++)if(ok[i])reachable.add(all.get(i));
        Collections.sort(reachable,new Comparator<Target>(){public int compare(Target a,Target b){return Double.compare(b.rank(),a.rank());}});
        List<Target> out=new ArrayList<Target>();Set<String> seen=new HashSet<String>();
        for(Target t:reachable)if(seen.add(t.server.ip)){out.add(t);if(out.size()>=6)break;}
        return out;
    }

    private void up(final int id,final VpnTunnel c,Target t)throws IOException{
        label=VpnServers.name(t.server.code,t.server.country);
        final int[] probe={0};final boolean[] answered={false};
        final int[] synPort={0};final int[] synFlags={-1};
        c.start(new VpnTunnel.Sink(){
            public void ip(byte[] data,int off,int len){
                if(probe[0]!=0&&dnsReply(data,off,len,probe[0])){answered[0]=true;return;}
                if(synPort[0]!=0){int f=tcpReply(data,off,len,synPort[0]);if(f>=0){synFlags[0]=f;return;}}
                FileOutputStream o=toTun;if(o!=null)try{o.write(data,off,len);}catch(IOException ignored){}}
            public void closed(String why){if(current(id)&&client==c)lost(id,why);}
        });
        sDetail="Checking the connection…";changed(this);
        for(int attempt=0;attempt<3&&!answered[0];attempt++){
            String dnsIp=attempt==1&&c.dns.length>0?c.dns[0]:"8.8.8.8";
            probe[0]=40000+new Random().nextInt(20000);
            byte[] q=dnsQuery(c.localIp,dnsIp,probe[0]);
            byte[] buf=new byte[8+q.length];System.arraycopy(q,0,buf,8,q.length);
            try{c.sendIp(buf,q.length);}catch(IOException e){break;}
            for(int w=0;w<20&&!answered[0];w++)try{Thread.sleep(50);}catch(InterruptedException e){break;}
            if(!current(id))return;
        }
        probe[0]=0;
        if(!answered[0]){bad(t);throw new IOException("Server connected but passes no traffic");}
        sDetail="Checking that websites open…";changed(this);
        boolean web=false;String why="no answer";
        for(String ip:new String[]{"1.1.1.1","8.8.8.8","9.9.9.9"}){
            if(!current(id))return;
            synPort[0]=30000+new Random().nextInt(20000);synFlags[0]=-1;
            int seq=new Random().nextInt();
            byte[] syn=tcpPacket(c.localIp,ip,synPort[0],443,seq,0,0x02);
            byte[] buf=new byte[8+syn.length];System.arraycopy(syn,0,buf,8,syn.length);
            try{c.sendIp(buf,syn.length);}catch(IOException e){break;}
            for(int w=0;w<30&&synFlags[0]<0;w++)try{Thread.sleep(50);}catch(InterruptedException e){break;}
            int f=synFlags[0];
            if(f>=0&&(f&0x12)==0x12){
                byte[] rst=tcpPacket(c.localIp,ip,synPort[0],443,seq+1,0,0x04);byte[] rb=new byte[8+rst.length];System.arraycopy(rst,0,rb,8,rst.length);
                try{c.sendIp(rb,rst.length);}catch(IOException ignored){}
                web=true;break;
            }
            why=f>=0&&(f&0x04)!=0?"connections refused":"no answer";
        }
        synPort[0]=0;
        if(!web){bad(t);throw new IOException("Server connected but websites don't open ("+why+")");}
        tunnel(id,c);
        if(!current(id))return;
        keepAlive=new Thread(new Runnable(){public void run(){
            try{while(current(id)&&!c.closed()){Thread.sleep(15000);long idle=SystemClock.elapsedRealtime()-c.lastRx;
                if(idle>75000){lost(id,"Server stopped answering");return;}
                if(idle>20000)try{c.echo();}catch(IOException e){lost(id,"Connection lost");return;}}}
            catch(InterruptedException ignored){}
        }},"PixelSuite-VPN-keepalive");keepAlive.setDaemon(true);keepAlive.start();
        sServerCode=t.server.code;
        sState=ON;sSince=System.currentTimeMillis();
        sDetail=c.protocol()+" · "+t.server.host+" · "+(t.rtt>0?t.rtt+" ms":c.localIp);
        changed(this);note();
    }

    private synchronized void tunnel(final int id,final VpnTunnel c)throws IOException{
        Builder b=new Builder();
        int scope=sScope;
        b.setSession("Pixel Suite VPN · "+label+(scope==CHROME_ONLY?" · Chrome":""));
        b.addAddress(c.localIp,32);
        b.addRoute("0.0.0.0",0);
        String[] dns=c.dns.length>0?c.dns:new String[]{"8.8.8.8","1.1.1.1"};
        for(String d:dns)try{b.addDnsServer(d);}catch(Exception ignored){}
        b.setMtu(1400);
        try{
            if(scope==CHROME_ONLY)b.addAllowedApplication(sChrome);
            else{b.addDisallowedApplication(getPackageName());if(scope==PHONE_NO_CHROME)b.addDisallowedApplication(sChrome);}
        }catch(Exception e){throw new IOException("Chrome isn't installed");}
        if(Build.VERSION.SDK_INT>=29)try{b.getClass().getMethod("setMetered",boolean.class).invoke(b,false);}catch(Exception ignored){}
        Intent open=new Intent(this,SettingsActivity.class).putExtra("page","vpn").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        b.setConfigureIntent(PendingIntent.getActivity(this,1,open,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT));
        final ParcelFileDescriptor fd=b.establish();
        if(fd==null)throw new IOException("Android didn't allow the VPN. Open the VPN page and tap Connect.");
        if(!current(id)){try{fd.close();}catch(IOException ignored){}c.close();return;}
        ParcelFileDescriptor old=tun;tun=fd;toTun=new FileOutputStream(fd.getFileDescriptor());
        if(old!=null)try{old.close();}catch(IOException ignored){}
        Thread pump=new Thread(new Runnable(){public void run(){
            FileInputStream fromTun=new FileInputStream(fd.getFileDescriptor());byte[] buf=new byte[8+32767];
            try{while(current(id)&&tun==fd&&!c.closed()){int n=fromTun.read(buf,8,buf.length-8);if(n<=0)continue;if((buf[8]&0xF0)!=0x40)continue;c.sendIp(buf,n);}}
            catch(IOException e){if(current(id)&&tun==fd&&!c.closed())lost(id,"Connection lost");}
        }},"PixelSuite-VPN-tun");pump.setDaemon(true);pump.start();
    }

    static byte[] dnsQuery(String src,String dst,int port){
        byte[] name={3,'w','w','w',6,'g','o','o','g','l','e',3,'c','o','m',0};
        int dnsLen=12+name.length+4,udpLen=8+dnsLen,total=20+udpLen;
        byte[] p=new byte[total];
        p[0]=0x45;p[2]=(byte)(total>>8);p[3]=(byte)total;int ident=new Random().nextInt(65536);p[4]=(byte)(ident>>8);p[5]=(byte)ident;p[6]=0x40;p[8]=64;p[9]=17;
        String[] a=src.split("\\."),b=dst.split("\\.");for(int i=0;i<4;i++){p[12+i]=(byte)Integer.parseInt(a[i]);p[16+i]=(byte)Integer.parseInt(b[i]);}
        long sum=0;for(int i=0;i<20;i+=2)sum+=((p[i]&255)<<8)|(p[i+1]&255);while((sum>>16)!=0)sum=(sum&0xffff)+(sum>>16);int ck=(int)(~sum&0xffff);p[10]=(byte)(ck>>8);p[11]=(byte)ck;
        p[20]=(byte)(port>>8);p[21]=(byte)port;p[22]=0;p[23]=53;p[24]=(byte)(udpLen>>8);p[25]=(byte)udpLen;
        int q=28;int qid=new Random().nextInt(65536);p[q]=(byte)(qid>>8);p[q+1]=(byte)qid;p[q+2]=0x01;p[q+5]=1;
        System.arraycopy(name,0,p,q+12,name.length);int e=q+12+name.length;p[e+1]=1;p[e+3]=1;
        return p;
    }

    static byte[] tcpPacket(String src,String dst,int sport,int dport,int seq,int ack,int flags){
        boolean syn=(flags&0x02)!=0;int tcpLen=syn?24:20,total=20+tcpLen;
        byte[] p=new byte[total];
        p[0]=0x45;p[2]=(byte)(total>>8);p[3]=(byte)total;int ident=new Random().nextInt(65536);p[4]=(byte)(ident>>8);p[5]=(byte)ident;p[6]=0x40;p[8]=64;p[9]=6;
        String[] a=src.split("\\."),b=dst.split("\\.");for(int i=0;i<4;i++){p[12+i]=(byte)Integer.parseInt(a[i]);p[16+i]=(byte)Integer.parseInt(b[i]);}
        long sum=0;for(int i=0;i<20;i+=2)sum+=((p[i]&255)<<8)|(p[i+1]&255);while((sum>>16)!=0)sum=(sum&0xffff)+(sum>>16);int ck=(int)(~sum&0xffff);p[10]=(byte)(ck>>8);p[11]=(byte)ck;
        int t=20;p[t]=(byte)(sport>>8);p[t+1]=(byte)sport;p[t+2]=(byte)(dport>>8);p[t+3]=(byte)dport;
        p[t+4]=(byte)(seq>>>24);p[t+5]=(byte)(seq>>>16);p[t+6]=(byte)(seq>>>8);p[t+7]=(byte)seq;
        p[t+8]=(byte)(ack>>>24);p[t+9]=(byte)(ack>>>16);p[t+10]=(byte)(ack>>>8);p[t+11]=(byte)ack;
        p[t+12]=(byte)((tcpLen/4)<<4);p[t+13]=(byte)flags;p[t+14]=(byte)0xFA;p[t+15]=(byte)0xF0;
        if(syn){p[t+20]=2;p[t+21]=4;p[t+22]=(byte)(1300>>8);p[t+23]=(byte)(1300&255);}
        sum=0;for(int i=12;i<20;i+=2)sum+=((p[i]&255)<<8)|(p[i+1]&255);sum+=6;sum+=tcpLen;
        for(int i=t;i<total;i+=2)sum+=((p[i]&255)<<8)|(i+1<total?(p[i+1]&255):0);
        while((sum>>16)!=0)sum=(sum&0xffff)+(sum>>16);ck=(int)(~sum&0xffff);p[t+16]=(byte)(ck>>8);p[t+17]=(byte)ck;
        return p;
    }

    private static int tcpReply(byte[] d,int off,int len,int port){
        if(len<40||(d[off]&0xF0)!=0x40||d[off+9]!=6)return -1;
        int ihl=(d[off]&15)*4;if(len<ihl+20)return -1;
        int sp=((d[off+ihl]&255)<<8)|(d[off+ihl+1]&255),dp=((d[off+ihl+2]&255)<<8)|(d[off+ihl+3]&255);
        if(sp!=443||dp!=port)return -1;
        return d[off+ihl+13]&0xFF;
    }

    private void bad(Target t){
        try{prefs(this).edit().putLong("bad_"+t.server.ip,System.currentTimeMillis()+6L*3600_000L).apply();}catch(Throwable ignored){}
    }

    private boolean isBad(VpnServers.Server s){
        try{return System.currentTimeMillis()<prefs(this).getLong("bad_"+s.ip,0);}catch(Throwable e){return false;}
    }

    private static boolean dnsReply(byte[] d,int off,int len,int port){
        if(len<28||(d[off]&0xF0)!=0x40||d[off+9]!=17)return false;
        int ihl=(d[off]&15)*4;if(len<ihl+8)return false;
        int sp=((d[off+ihl]&255)<<8)|(d[off+ihl+1]&255),dp=((d[off+ihl+2]&255)<<8)|(d[off+ihl+3]&255);
        return sp==53&&dp==port;
    }

    private void lost(final int id,final String why){
        MAIN.post(new Runnable(){public void run(){
            if(!current(id)||sState==OFF)return;
            if(sState==ON&&sCountry!=null){start(sCountry);sDetail="Reconnecting… ("+why+")";changed(PixelVpnService.this);}
            else{fail(why);stopSelf();}
        }});
    }

    private void fail(String why){
        session++;closeTunnel();
        sState=OFF;sError=why;sDetail="";sSince=0;changed(this);
        cancelNote();
    }

    private void disconnect(String why,boolean stopSelf){
        session++;closeTunnel();
        if(sState!=OFF){sState=OFF;sError=null;sDetail=why==null?"":why;sSince=0;changed(this);}
        cancelNote();
        if(stopSelf)stopSelf();
    }

    private void closeClient(){
        final VpnTunnel c=client;client=null;
        if(c!=null)new Thread(new Runnable(){public void run(){c.close();}}).start();
        Thread k=keepAlive;keepAlive=null;if(k!=null)k.interrupt();
    }

    private void closeTunnel(){
        closeClient();
        ParcelFileDescriptor fd=tun;tun=null;toTun=null;if(fd!=null)try{fd.close();}catch(IOException ignored){}
    }

    private void note(){
        try{
            if(Build.VERSION.SDK_INT>=33&&checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=0)return;
            NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);if(nm==null)return;
            if(Build.VERSION.SDK_INT>=26){Class<?> channel=Class.forName("android.app.NotificationChannel");
                if(nm.getClass().getMethod("getNotificationChannel",String.class).invoke(nm,CHANNEL)==null){Object ch=channel.getConstructor(String.class,CharSequence.class,int.class).newInstance(CHANNEL,"VPN",2);nm.getClass().getMethod("createNotificationChannel",channel).invoke(nm,ch);}}
            String place=VpnServers.name(sCountry,sCountry)+(sScope==CHROME_ONLY?" · Chrome only":sScope==PHONE_NO_CHROME?" · not Chrome":"");
            PendingIntent open=PendingIntent.getActivity(this,2,new Intent(this,SettingsActivity.class).putExtra("page","vpn").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
            PendingIntent stop=PendingIntent.getService(this,3,new Intent(this,PixelVpnService.class).setAction(ACTION_STOP),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
            int icon=getResources().getIdentifier("ic_ps_vpn","drawable",getPackageName());
            Notification.Builder n=new Notification.Builder(this);
            if(Build.VERSION.SDK_INT>=26)n.getClass().getMethod("setChannelId",String.class).invoke(n,CHANNEL);
            n.setSmallIcon(icon).setContentTitle(I18n.cs(sState==ON?"VPN · "+place:"Connecting to "+place+"…")).setContentText(I18n.cs(sState==ON?"Free volunteer server":sDetail))
             .setOngoing(true).setOnlyAlertOnce(true).setContentIntent(open).addAction(new Notification.Action.Builder(android.graphics.drawable.Icon.createWithResource(this,icon),"Disconnect",stop).build());
            if(sState==ON)n.setWhen(sSince).setShowWhen(true).setUsesChronometer(true);
            nm.notify(NOTE,n.build());
        }catch(Throwable t){}
    }
    private void cancelNote(){try{((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).cancel(NOTE);}catch(Throwable ignored){}}
}
