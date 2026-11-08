package io.github.pixelsuite;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Xml;
import org.json.JSONArray;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;
import java.io.File;
import java.io.StringReader;
import java.net.IDN;
import java.net.URI;
import java.util.*;

final class ChromeSiteSettings {
    private ChromeSiteSettings() {}
    static final Uri URI_SETTINGS=Uri.parse("content://io.github.roviandev.pixelsuite.cosmetic");
    static final String PREFS="pixelsuite_shield";
    static final String[] BROWSERS={"com.android.chrome","com.chrome.beta","com.chrome.dev","com.chrome.canary"};
    private static final String OWNER="io.github.roviandev.pixelsuite";
    private static Snapshot cached;
    private static long cachedAt;
    private static boolean observing;

    static final class Snapshot {
        int defaultLevel=2;
        final SortedMap<String,Integer> levels=new TreeMap<String,Integer>();
        final SortedMap<String,Set<String>> hidden=new TreeMap<String,Set<String>>();
        int level(String host){String key=domain(host);Integer v=key==null?null:levels.get(key);return v==null?defaultLevel:v;}
        Set<String> selectors(String host){String key=domain(host);Set<String> v=key==null?null:hidden.get(key);return v==null?new HashSet<String>():new HashSet<String>(v);}
        int count(){Set<String> v=new HashSet<String>(levels.keySet());v.addAll(hidden.keySet());return v.size();}
    }
    private static SharedPreferences own(Context c){return c.getApplicationContext().getSharedPreferences(PREFS,0);}
    private static boolean owner(Context c){return OWNER.equals(c.getPackageName());}
    static synchronized void invalidate(){cached=null;cachedAt=0;}

    static String domain(String raw){
        if(raw==null)return null;
        String value=raw.trim();
        if(value.isEmpty()||value.matches(".*[\\s\\p{Cntrl}].*"))return null;
        try{
            URI uri=new URI(value.contains("://")?value:"https://"+value);
            if(!"http".equalsIgnoreCase(uri.getScheme())&&!"https".equalsIgnoreCase(uri.getScheme()))return null;
            String host=uri.getHost();
            if(host==null){host=uri.getRawAuthority();if(host==null||host.contains("@")||host.contains("["))return null;int port=host.lastIndexOf(':');if(port>=0){if(!host.substring(port+1).matches("[0-9]+"))return null;host=host.substring(0,port);}}
            if(host.endsWith("."))host=host.substring(0,host.length()-1);
            host=IDN.toASCII(host,IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
            if(host.isEmpty()||host.length()>253)return null;
            for(String label:host.split("\\.",-1))if(!label.matches("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?"))return null;
            return host;
        }catch(Exception invalid){return null;}
    }
    private static Snapshot parse(String data){
        Snapshot result=new Snapshot();
        try{
            JSONObject json=new JSONObject(data==null?"{}":data);
            int d=json.optInt("default_level",2);result.defaultLevel=d>=1&&d<=3?d:2;
            Iterator<String> keys=json.keys();
            while(keys.hasNext()){
                String key=keys.next();
                if(key.startsWith("level:")){String host=domain(key.substring(6));int v=json.optInt(key,-1);if(host!=null&&v>=0&&v<=3)result.levels.put(host,v);}
                else if(key.startsWith("hide:")){String host=domain(key.substring(5));JSONArray list=json.optJSONArray(key);if(host==null||list==null)continue;Set<String> set=new TreeSet<String>();for(int i=0;i<list.length();i++){String v=list.optString(i,"").trim();if(!v.isEmpty()&&v.length()<600)set.add(v);}if(!set.isEmpty())result.hidden.put(host,set);}
            }
        }catch(Exception invalid){throw new IllegalArgumentException("Invalid site settings",invalid);}
        return result;
    }
    private static String encode(SharedPreferences p){
        JSONObject result=new JSONObject();
        try{for(Map.Entry<String,?> e:p.getAll().entrySet()){
            String key=e.getKey();Object v=e.getValue();
            if((key.startsWith("level:")||"default_level".equals(key))&&v instanceof Integer)result.put(key,v);
            else if(key.startsWith("hide:")&&v instanceof Set){JSONArray list=new JSONArray();for(Object item:(Set<?>)v)if(item instanceof String)list.put(item);result.put(key,list);}
        }}catch(Exception invalid){throw new IllegalStateException(invalid);}
        return result.toString();
    }
    private static void observe(final Context c){
        if(observing)return;
        try{c.getContentResolver().registerContentObserver(URI_SETTINGS,true,new ContentObserver(new Handler(Looper.getMainLooper())){
            @Override public void onChange(boolean self){invalidate();ChromeShield.refreshAll();}
        });observing=true;}catch(Throwable ignored){}
    }
    private static Bundle remote(Context c,String op,Bundle args){
        Bundle out=c.getContentResolver().call(URI_SETTINGS,"shield",op,args);
        if(out==null||!out.getBoolean("ok",false))throw new IllegalStateException("Site settings unavailable");
        return out;
    }
    private static void migrateRemote(Context c){
        SharedPreferences legacy=c.getApplicationContext().getSharedPreferences(PREFS,0);
        if(legacy.getBoolean("site_bridge_v1",false))return;
        Bundle data=new Bundle();data.putString("source",c.getPackageName());data.putString("data",encode(legacy));
        remote(c,"import",data);
        SharedPreferences.Editor edit=legacy.edit();
        for(String key:legacy.getAll().keySet())if(key.startsWith("level:")||key.startsWith("hide:")||"default_level".equals(key))edit.remove(key);
        edit.putBoolean("site_bridge_v1",true).commit();
    }
    static synchronized Snapshot snapshot(Context c){
        if(owner(c))return parse(encode(own(c)));
        long now=SystemClock.uptimeMillis();if(cached!=null&&now-cachedAt<1000)return cached;
        observe(c.getApplicationContext());
        try{migrateRemote(c);Snapshot next=parse(remote(c,"snapshot",null).getString("data"));cached=next;cachedAt=now;return next;}
        catch(Throwable unavailable){
            if(cached!=null)return cached;
            SharedPreferences legacy=c.getApplicationContext().getSharedPreferences(PREFS,0);
            if(!legacy.getBoolean("site_bridge_v1",false))return parse(encode(legacy));
            Snapshot none=new Snapshot();none.defaultLevel=0;return none;
        }
    }
    static void change(Context c,String operation,String host,int level,Collection<String> selectors){
        Bundle data=new Bundle();if(host!=null)data.putString("host",host);data.putInt("level",level);
        if(selectors!=null)data.putStringArrayList("selectors",new ArrayList<String>(selectors));
        if(owner(c))serve(c,operation,data);else remote(c,operation,data);
        invalidate();
    }
    static synchronized Bundle serve(Context c,String op,Bundle data){
        SharedPreferences p=own(c);SharedPreferences.Editor edit=p.edit();boolean write=!"snapshot".equals(op);
        String host=data==null?null:domain(data.getString("host"));int level=data==null?-1:data.getInt("level",-1);
        if("import".equals(op)){
            String source=data.getString("source");if(!Arrays.asList(BROWSERS).contains(source))throw new IllegalArgumentException("Invalid browser");
            String flag="imported:"+source;
            if(!p.getBoolean(flag,false)){
                Snapshot legacy=parse(data.getString("data"));
                for(Map.Entry<String,Integer> e:legacy.levels.entrySet())if(!p.contains("level:"+e.getKey()))edit.putInt("level:"+e.getKey(),e.getValue());
                for(Map.Entry<String,Set<String>> e:legacy.hidden.entrySet()){Set<String> merged=new HashSet<String>(p.getStringSet("hide:"+e.getKey(),Collections.<String>emptySet()));merged.addAll(e.getValue());edit.putStringSet("hide:"+e.getKey(),merged);}
                if(!p.contains("default_level"))edit.putInt("default_level",legacy.defaultLevel);edit.putBoolean(flag,true);
            }
        }else if("default".equals(op)){if(level<1||level>3)throw new IllegalArgumentException("Invalid level");edit.putInt("default_level",level);}
        else if("level".equals(op)){if(host==null||level<0||level>3)throw new IllegalArgumentException("Invalid website/level");edit.putInt("level:"+host,level);}
        else if("resetLevel".equals(op)){if(host==null)throw new IllegalArgumentException("Invalid website");edit.remove("level:"+host);}
        else if("clearHidden".equals(op)){if(host==null)throw new IllegalArgumentException("Invalid website");edit.remove("hide:"+host);}
        else if("addHidden".equals(op)){
            if(host==null)throw new IllegalArgumentException("Invalid website");Set<String> values=new HashSet<String>(p.getStringSet("hide:"+host,Collections.<String>emptySet()));
            ArrayList<String> added=data.getStringArrayList("selectors");if(added!=null)for(String v:added)if(v!=null&&!v.trim().isEmpty()&&v.length()<600)values.add(v.trim());edit.putStringSet("hide:"+host,values);
        }else if("resetAll".equals(op)){for(String key:p.getAll().keySet())if(key.startsWith("level:")||key.startsWith("hide:"))edit.remove(key);}
        else if(!"snapshot".equals(op))throw new IllegalArgumentException("Unknown operation");
        if(write){if(!edit.commit())throw new IllegalStateException("Could not save site settings");c.getContentResolver().notifyChange(URI_SETTINGS,null);}
        Bundle out=new Bundle();out.putBoolean("ok",true);out.putString("data",encode(p));return out;
    }

    static void importLegacy(Context c){
        if(!owner(c))return;
        File parent=new File(c.getApplicationInfo().dataDir).getParentFile();
        for(String pkg:BROWSERS){
            if(own(c).getBoolean("imported:"+pkg,false))continue;
            String path=new File(parent,pkg+"/shared_prefs/"+PREFS+".xml").getPath();
            List<String> lines=Root.run("[ ! -f '"+path+"' ] || cat '"+path+"'");
            if(lines==null)continue;
            JSONObject json=new JSONObject();
            try{
                StringBuilder xml=new StringBuilder();for(String line:lines)xml.append(line).append('\n');
                if(xml.length()>0){
                    XmlPullParser parser=Xml.newPullParser();parser.setInput(new StringReader(xml.toString()));
                    for(int event=parser.getEventType();event!=XmlPullParser.END_DOCUMENT;event=parser.next()){
                        if(event!=XmlPullParser.START_TAG)continue;
                        String key=parser.getAttributeValue(null,"name");if(key==null)continue;
                        if("int".equals(parser.getName())&&(key.startsWith("level:")||"default_level".equals(key)))json.put(key,Integer.parseInt(parser.getAttributeValue(null,"value")));
                        else if("set".equals(parser.getName())&&key.startsWith("hide:")){JSONArray set=new JSONArray();while((event=parser.next())!=XmlPullParser.END_DOCUMENT){if(event==XmlPullParser.END_TAG&&"set".equals(parser.getName()))break;if(event==XmlPullParser.START_TAG&&"string".equals(parser.getName()))set.put(parser.nextText());}json.put(key,set);}
                    }
                }
                Bundle args=new Bundle();args.putString("source",pkg);args.putString("data",json.toString());serve(c,"import",args);
            }catch(Exception invalid){throw new IllegalStateException("Couldn't read existing Chrome site settings",invalid);}
        }
    }
}
