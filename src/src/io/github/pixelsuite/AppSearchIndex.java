package io.github.pixelsuite;

import android.content.Context;
import org.json.*;
import java.io.*;
import java.text.Normalizer;
import java.util.*;

final class AppSearchIndex {
    static final class Entry {
        final String id,page,title,description,kind,key,icon,breadcrumb,anchor;
        final String[] fields;
        final String[][] words;
        Entry(JSONObject o) {
            String enTitle=o.optString("title"),enDescription=o.optString("description"),enCrumb=o.optString("breadcrumb");
            id=o.optString("id");page=o.optString("page");title=title(enTitle,enDescription,o.optString("kind"));description=I18n.phrases(enDescription);
            kind=o.optString("kind");key=o.optString("key");icon=o.optString("icon");breadcrumb=crumb(enCrumb);anchor=o.optString("anchor");
            String english=I18n.on()?" "+enTitle+" "+enCrumb+" "+enDescription:"";
            fields=new String[]{normal(title),normal(o.optString("keywords")+english),normal(breadcrumb),normal(description)};
            words=new String[fields.length][];for(int i=0;i<fields.length;i++)words[i]=fields[i].split(" +");
        }
    }
    static String title(String enTitle,String enDescription,String kind){
        String t=I18n.t(enTitle);
        if(!I18n.on()||!"TEXT".equals(kind)||!t.equals(enTitle)||enDescription==null||enDescription.length()<=enTitle.length()||!enDescription.startsWith(enTitle))return t;
        String full=I18n.t(enDescription);if(full.equals(enDescription))return t;int nl=full.indexOf('\n');if(nl>=0)full=full.substring(0,nl);
        int n=Math.max(24,enTitle.length());if(full.length()<=n)return full;
        int cut=full.lastIndexOf(' ',n);if(cut<n/2)cut=n;return full.substring(0,cut).trim()+"\u2026";
    }
    static String crumb(String en){if(!I18n.on()||en==null)return en;String[] parts=en.split(" › ",-1);StringBuilder b=new StringBuilder();for(int i=0;i<parts.length;i++){if(i>0)b.append(" › ");b.append(I18n.t(parts[i]));}return b.toString();}
    private static volatile AppSearchIndex cached;
    static void forget(){cached=null;}
    final List<Entry> entries;
    private AppSearchIndex(List<Entry> e){entries=Collections.unmodifiableList(e);}
    AppSearchIndex including(List<Entry> live){List<Entry> merged=new ArrayList<Entry>(entries);merged.addAll(live);return new AppSearchIndex(merged);}
    static AppSearchIndex load(Context c)throws Exception {
        if(cached!=null)return cached;
        synchronized(AppSearchIndex.class){
            if(cached!=null)return cached;
            int id=c.getResources().getIdentifier("app_search","raw",c.getPackageName());
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            try(InputStream in=c.getResources().openRawResource(id)){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)bytes.write(b,0,n);}
            JSONArray array=new JSONArray(bytes.toString("UTF-8"));List<Entry> e=new ArrayList<Entry>();
            for(int i=0;i<array.length();i++)e.add(new Entry(array.getJSONObject(i)));
            cached=new AppSearchIndex(e);return cached;
        }
    }
    static String normal(String s){return Normalizer.normalize(s,Normalizer.Form.NFD).replaceAll("\\p{M}+","").toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+"," ").trim();}
    static boolean near(String q,String word){
        if(word.startsWith(q))return true;
        if(!q.isEmpty()&&q.codePointAt(0)>=0x2E80&&word.contains(q))return true;
        if(q.length()<5)return false;
        for(int n=Math.max(0,q.length()-1);n<=Math.min(word.length(),q.length()+1);n++){
            String w=word.substring(0,n);int i=0,j=0,edits=0;
            while(i<q.length()&&j<w.length()){
                if(q.charAt(i)==w.charAt(j)){i++;j++;}
                else {if(++edits>1)break;if(q.length()>=w.length())i++;if(w.length()>=q.length())j++;}
            }
            if(edits+(q.length()-i)+(w.length()-j)<=1)return true;
        }return false;
    }
    List<Entry> find(String raw){
        final String q=normal(raw);if(q.isEmpty())return Collections.emptyList();
        final Map<Entry,Integer> scores=new HashMap<Entry,Integer>();List<Entry> result=new ArrayList<Entry>();
        for(Entry e:entries){int score=0;boolean all=true;
            if("lsposed scope".equals(e.fields[0]))continue;
            for(String term:q.split(" +")){int best=999;
                for(int f=0;f<e.fields.length;f++)for(String word:e.words[f])if(near(term,word)){best=Math.min(best,f*20+(word.startsWith(term)?0:8));break;}
                if(best==999){all=false;break;}score+=best;
            }
            if(all){if(e.fields[0].startsWith(q))score-=100;if("TEXT".equals(e.kind))score+=5;if("TOGGLE".equals(e.kind))score-=2;scores.put(e,score);result.add(e);}
        }
        Collections.sort(result,(a,b)->{int d=scores.get(a)-scores.get(b);return d!=0?d:a.title.compareToIgnoreCase(b.title);});
        Set<String> unique=new HashSet<String>();List<Entry> compact=new ArrayList<Entry>();
        for(Entry e:result)if(unique.add(e.page+"\n"+e.title))compact.add(e);
        return compact;
    }
}
