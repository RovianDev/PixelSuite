package io.github.pixelsuite;

import android.content.Context;
import android.util.Base64;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;

final class VpnServers {
    private VpnServers(){}

    static final String FASTEST="auto";
    private static final String LIST="https://www.vpngate.net/api/iphone/";
    private static final String CACHE="vpngate_servers.csv";
    private static final long FRESH=30L*60L*1000L;

    static final class Server {
        String host,ip,country,code,config;int score,ping;long speed;int sessions;
        String name(){return host+".opengw.net";}
        int[] udpPorts(){
            LinkedHashSet<Integer> out=new LinkedHashSet<Integer>();
            try{
                String text=new String(Base64.decode(config,Base64.DEFAULT),"UTF-8");boolean udp=false;int port=0;
                for(String line:text.split("\n")){line=line.trim();if(line.startsWith("proto "))udp=line.contains("udp");if(line.startsWith("remote ")){String[] p=line.split("\\s+");if(p.length>2)try{port=Integer.parseInt(p[2]);}catch(NumberFormatException ignored){}}}
                if(udp&&port>0)out.add(port);
            }catch(Exception ignored){}
            out.add(1194);out.add(1195);out.add(443);
            int[] r=new int[out.size()];int i=0;for(Integer p:out)r[i++]=p;return r;
        }
        int[] ports(){
            LinkedHashSet<Integer> out=new LinkedHashSet<Integer>();
            try{
                String text=new String(Base64.decode(config,Base64.DEFAULT),"UTF-8");boolean tcp=false;int port=0;
                for(String line:text.split("\n")){line=line.trim();if(line.startsWith("proto "))tcp=line.contains("tcp");if(line.startsWith("remote ")){String[] p=line.split("\\s+");if(p.length>2)try{port=Integer.parseInt(p[2]);}catch(NumberFormatException ignored){}}}
                if(tcp&&port>0)out.add(port);
            }catch(Exception ignored){}
            out.add(443);out.add(992);
            int[] r=new int[Math.min(3,out.size())];int i=0;for(Integer p:out){if(i>=r.length)break;r[i++]=p;}return r;
        }
    }

    static final class Country {
        final String code,name;final List<Server> servers=new ArrayList<Server>();long best;
        Country(String code,String name){this.code=code;this.name=name;}
    }

    private static List<Server> sServers;private static long sLoadedAt;

    static synchronized List<Server> cached(Context c){
        if(sServers==null){File f=new File(c.getFilesDir(),CACHE);if(f.exists()){try{sServers=parse(read(new FileInputStream(f)));sLoadedAt=f.lastModified();}catch(Exception ignored){}}}
        return sServers==null?new ArrayList<Server>():sServers;
    }

    static synchronized boolean fresh(Context c){cached(c);return sServers!=null&&!sServers.isEmpty()&&System.currentTimeMillis()-sLoadedAt<FRESH;}

    static List<Server> refresh(Context c)throws IOException{
        HttpURLConnection http=(HttpURLConnection)new URL(LIST).openConnection();
        http.setConnectTimeout(10000);http.setReadTimeout(20000);http.setRequestProperty("User-Agent","PixelSuite");
        try{
            if(http.getResponseCode()!=200)throw new IOException("Server list unavailable ("+http.getResponseCode()+")");
            String text=read(http.getInputStream());List<Server> list=parse(text);
            if(list.isEmpty())throw new IOException("Server list was empty");
            File tmp=new File(c.getFilesDir(),CACHE+".tmp");FileOutputStream out=new FileOutputStream(tmp);try{out.write(text.getBytes("UTF-8"));}finally{out.close();}
            tmp.renameTo(new File(c.getFilesDir(),CACHE));
            synchronized(VpnServers.class){sServers=list;sLoadedAt=System.currentTimeMillis();}
            return list;
        }finally{http.disconnect();}
    }

    static List<Server> ensure(Context c)throws IOException{
        if(fresh(c))return cached(c);
        try{return refresh(c);}catch(IOException e){List<Server> old=cached(c);if(!old.isEmpty())return old;throw e;}
    }

    private static String read(InputStream in)throws IOException{
        try{ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[16384];int n;while((n=in.read(b))>0)out.write(b,0,n);return out.toString("UTF-8");}finally{in.close();}
    }

    static List<Server> parse(String text){
        List<Server> out=new ArrayList<Server>();
        for(String line:text.split("\n")){
            line=line.trim();if(line.isEmpty()||line.startsWith("*")||line.startsWith("#"))continue;
            String[] p=line.split(",",-1);if(p.length<15)continue;
            try{
                Server s=new Server();s.host=p[0];s.ip=p[1];s.score=(int)Math.min(Integer.MAX_VALUE,Long.parseLong(p[2]));s.ping=p[3].isEmpty()?0:Integer.parseInt(p[3]);
                s.speed=Long.parseLong(p[4]);s.country=p[5];s.code=p[6].toUpperCase(Locale.ROOT);s.sessions=p[7].isEmpty()?0:Integer.parseInt(p[7]);s.config=p[14];
                if(s.ip.isEmpty()||s.code.length()!=2||!s.ip.matches("[0-9.]+"))continue;out.add(s);
            }catch(NumberFormatException ignored){}
        }
        return out;
    }

    static List<Country> countries(List<Server> list){
        Map<String,Country> map=new HashMap<String,Country>();
        for(Server s:list){Country c=map.get(s.code);if(c==null){c=new Country(s.code,name(s.code,s.country));map.put(s.code,c);}c.servers.add(s);c.best=Math.max(c.best,s.speed);}
        List<Country> out=new ArrayList<Country>(map.values());
        for(Country c:out)Collections.sort(c.servers,BEST);
        Collections.sort(out,new Comparator<Country>(){public int compare(Country a,Country b){int r=Long.compare(b.best,a.best);return r!=0?r:a.name.compareToIgnoreCase(b.name);}});
        return out;
    }

    static synchronized List<String> missing(Context c,List<Country> now){
        List<String> out=new ArrayList<String>();
        try{
            android.content.SharedPreferences p=PixelVpnService.prefs(c);
            java.util.TreeSet<String> seen=new java.util.TreeSet<String>();
            for(String k:p.getString("seen_countries","").split(","))if(k.length()==2)seen.add(k);
            java.util.HashSet<String> live=new java.util.HashSet<String>();for(Country k:now)live.add(k.code);
            int before=seen.size();seen.addAll(live);
            String chosen=PixelVpnService.chosen(c);if(chosen!=null&&chosen.length()==2&&!FASTEST.equals(chosen))seen.add(chosen.toUpperCase(Locale.ROOT));
            if(seen.size()!=before){StringBuilder b=new StringBuilder();for(String k:seen){if(b.length()>0)b.append(',');b.append(k);}p.edit().putString("seen_countries",b.toString()).apply();}
            if(live.isEmpty())return out;
            for(String k:seen)if(!live.contains(k))out.add(k);
            Collections.sort(out,new Comparator<String>(){public int compare(String a,String b){return name(a,a).compareToIgnoreCase(name(b,b));}});
        }catch(Throwable ignored){}
        return out;
    }

    static final Comparator<Server> BEST=new Comparator<Server>(){public int compare(Server a,Server b){return Integer.compare(b.score,a.score);}};

    static List<Server> candidates(List<Server> list,String code,int max){
        List<Server> out=new ArrayList<Server>();
        if(FASTEST.equals(code)){
            Map<String,Integer> taken=new HashMap<String,Integer>();
            List<Server> byScore=new ArrayList<Server>(list);Collections.sort(byScore,BEST);
            for(Server s:byScore){Integer n=taken.get(s.code);if(n==null)n=0;if(n<2){out.add(s);taken.put(s.code,n+1);}}
            List<Server> bySpeed=new ArrayList<Server>(list);Collections.sort(bySpeed,new Comparator<Server>(){public int compare(Server a,Server b){return Long.compare(b.speed,a.speed);}});
            for(int i=0;i<Math.min(8,bySpeed.size());i++)if(!out.contains(bySpeed.get(i)))out.add(bySpeed.get(i));
            Collections.sort(out,BEST);
            return out.size()>max?new ArrayList<Server>(out.subList(0,max)):out;
        }
        for(Server s:list)if(s.code.equalsIgnoreCase(code))out.add(s);
        Collections.sort(out,BEST);
        return out.size()>max?new ArrayList<Server>(out.subList(0,max)):out;
    }

    static String name(String code,String fallback){
        if(FASTEST.equals(code))return I18n.t("Fastest server");
        if(I18n.on()&&code!=null&&code.length()==2)try{String n=new Locale("",code).getDisplayCountry(I18n.shownLocale());if(n!=null&&!n.isEmpty()&&!n.equalsIgnoreCase(code))return n;}catch(Exception ignored){}
        if("GB".equals(code))return "United Kingdom";if("US".equals(code))return "United States";if("KR".equals(code))return "South Korea";if("RU".equals(code))return "Russia";
        try{String n=new Locale("",code).getDisplayCountry(Locale.ENGLISH);if(n!=null&&!n.isEmpty()&&!n.equalsIgnoreCase(code))return n;}catch(Exception ignored){}
        return fallback==null||fallback.isEmpty()?code:fallback;
    }

    static String flag(String code){
        if(code==null||code.length()!=2||FASTEST.equals(code))return "⚡";
        code=code.toUpperCase(Locale.ROOT);
        return new String(Character.toChars(0x1F1E6+code.charAt(0)-'A'))+new String(Character.toChars(0x1F1E6+code.charAt(1)-'A'));
    }

    static String speed(long bps){
        if(bps>=1000000L)return (bps/1000000L)+" Mbps";if(bps>=1000L)return (bps/1000L)+" kbps";return bps+" bps";
    }
}
