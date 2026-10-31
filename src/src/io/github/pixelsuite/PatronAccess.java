package io.github.pixelsuite;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import org.json.JSONObject;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.SecureRandom;
import java.security.spec.X509EncodedKeySpec;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class PatronAccess {
    static final String KEY="pixelsuite_patron";
    private static final long DAY=86400L;
    private static final ExecutorService NET=Executors.newSingleThreadExecutor();
    private static final ExecutorService BLOCKER=Executors.newSingleThreadExecutor();
    private static final Object APPLY=new Object();
    private static final ExecutorService RECONCILE=Executors.newSingleThreadExecutor();
    private static final java.util.concurrent.atomic.AtomicBoolean reconcilePending=new java.util.concurrent.atomic.AtomicBoolean();
    private static volatile boolean initialized;
    static boolean initialized(){return initialized;}
    static void resume(final Context context,final Runnable ready){
        final Context c=context.getApplicationContext();
        if(!reconcilePending.compareAndSet(false,true)){
            if(ready!=null)RECONCILE.execute(new Runnable(){public void run(){MAIN.post(ready);}});return;
        }
        RECONCILE.execute(new Runnable(){public void run(){try{reconcile(c);}finally{reconcilePending.set(false);if(ready!=null)MAIN.post(ready);}}});
    }
    private static final java.util.concurrent.atomic.AtomicBoolean blockerRunning=new java.util.concurrent.atomic.AtomicBoolean();
    private static final CopyOnWriteArrayList<Runnable> listeners=new CopyOnWriteArrayList<Runnable>();
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static volatile boolean unlocked;
    private static boolean refreshing;
    private static final java.util.ArrayList<Runnable> refreshWaiters=new java.util.ArrayList<Runnable>();
    private PatronAccess(){}
    static boolean unlocked(){return unlocked;}
    static SharedPreferences prefs(Context c){return c.getSharedPreferences("pixelsuite_patron",0);}
    static boolean configured(){return PatronConfig.SERVER.startsWith("https://")&&!PatronConfig.CAMPAIGN.isEmpty();}
    static boolean protectedKey(String key){return CallRecording.KEY.equals(key)||GoogleFeedAds.KEY.equals(key)||WatchEsim.KEY.equals(key);}
    static boolean allowed(Context c,String key){return WatchEsim.KEY.equals(key)?EsimPurchase.unlocked(c):unlocked();}
    static boolean protectedPage(String page){return "google_dialer".equals(page)||"google_app".equals(page);}
    static String random(){byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);return Base64.encodeToString(bytes,Base64.URL_SAFE|Base64.NO_WRAP|Base64.NO_PADDING);}
    static synchronized String device(Context c){String id=prefs(c).getString("device",null);if(id==null){id=random();prefs(c).edit().putString("device",id).commit();}return id;}
    static void watch(Runnable action){listeners.addIfAbsent(action);}
    static void unwatch(Runnable action){listeners.remove(action);}
    static void changed(){MAIN.post(new Runnable(){public void run(){for(Runnable r:listeners)r.run();}});}
    static boolean offline(Context c){try{android.net.NetworkInfo n=((ConnectivityManager)c.getSystemService(Context.CONNECTIVITY_SERVICE)).getActiveNetworkInfo();return n==null||!n.isConnected();}catch(Exception e){return false;}}
    static byte[] decode(String value){return Base64.decode(value,Base64.URL_SAFE|Base64.NO_WRAP|Base64.NO_PADDING);}
    static byte[] der(byte[] raw)throws Exception{
        if(raw.length!=64)throw new Exception("signature");java.io.ByteArrayOutputStream body=new java.io.ByteArrayOutputStream();
        for(int off:new int[]{0,32}){int start=off;while(start<off+31&&raw[start]==0)start++;boolean pad=(raw[start]&128)!=0;body.write(2);body.write(off+32-start+(pad?1:0));if(pad)body.write(0);body.write(raw,start,off+32-start);}
        byte[] data=body.toByteArray();java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();out.write(48);out.write(data.length);out.write(data);return out.toByteArray();
    }
    static JSONObject verify(Context c,String pass,boolean login)throws Exception{return verify(c,pass,login,false);}
    private static JSONObject verify(Context c,String pass,boolean login,boolean forRefresh)throws Exception{
        if(!configured()||pass==null||pass.length()>4096||!pass.matches("[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+"))throw new Exception("pass");
        String[] parts=pass.split("\\.");JSONObject header=new JSONObject(new String(decode(parts[0]),"UTF-8"));
        if(!"ES256".equals(header.optString("alg"))||!"JWT".equals(header.optString("typ")))throw new Exception("algorithm");
        Signature signature=Signature.getInstance("SHA256withECDSA");signature.initVerify(KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(Base64.decode(PatronConfig.PUBLIC_KEY,Base64.DEFAULT))));
        signature.update((parts[0]+"."+parts[1]).getBytes("UTF-8"));if(!signature.verify(der(decode(parts[2]))))throw new Exception("signature");
        JSONObject p=new JSONObject(new String(decode(parts[1]),"UTF-8"));long now=Math.max(System.currentTimeMillis()/1000,prefs(c).getLong("clock",0));
        if(!(p.get("entitled") instanceof Boolean))throw new Exception("entitlement");
        long issued=p.getLong("iat"),expiry=p.getLong("exp"),grace=p.getLong("offline_until");
        if(p.optInt("v")!=1||!"pixelsuite-patreon".equals(p.optString("iss"))||!"io.github.roviandev.pixelsuite".equals(p.optString("aud"))||!PatronConfig.CAMPAIGN.equals(p.optString("campaign"))||!device(c).equals(p.optString("did"))||!p.optString("sub").matches("[0-9]+")||!p.optString("sid").matches("[A-Za-z0-9_-]{43}")||issued>now+300||expiry-issued!=35*DAY||grace-expiry!=7*DAY||now>grace||(!forRefresh&&now>expiry&&(login||!offline(c))))throw new Exception("claims");
        prefs(c).edit().putLong("clock",now).apply();return p;
    }
    static void reconcile(Context c){synchronized(APPLY){reconcileLocked(c);initialized=true;}}
    private static void reconcileLocked(Context c){
        List<String> lines=Root.run("settings get secure "+KEY);String token=lines!=null&&!lines.isEmpty()?lines.get(0).trim():prefs(c).getString("pass","");
        if(prefs(c).getBoolean("signed_out",false)){if(!"null".equals(token)&&token.length()>0)Root.run("settings delete secure "+KEY);token="";}
        boolean allowed=false;JSONObject claims=null;try{claims=verify(c,token,false);allowed=claims.optBoolean("entitled",false);}catch(Exception ignored){}
        boolean before=unlocked;unlocked=allowed;
        if(!allowed){
            if(claims!=null&&!claims.optBoolean("entitled",false))Root.run("settings delete secure "+KEY);
            Root.run("settings put secure "+CallRecording.KEY+" 0; settings put secure "+GoogleFeedAds.KEY+" 0");
            if(AdBlock.isOn())AdBlock.setEnabled(c,false);
            prefs(c).edit().putBoolean("defaults_applied",false).apply();
            if(configured()&&!offline(c))try{verify(c,token,false,true);prefs(c).edit().putString("pass",token).apply();if(System.currentTimeMillis()-prefs(c).getLong("checked",0)>7*DAY*1000)refresh(c);}catch(Exception ignored){}
        }else{
            prefs(c).edit().putString("pass",token).apply();
            if(!prefs(c).getBoolean("defaults_applied",false)){
                if(Root.run("settings put secure "+GoogleFeedAds.KEY+" 1")!=null){
                    prefs(c).edit().putBoolean("defaults_applied",true).apply();updateBlocker(c,true);
                }
            }else updateBlocker(c,false);
            schedule(c);
            long age=System.currentTimeMillis()-prefs(c).getLong("checked",0);
            if(age>7*DAY*1000||claims.optLong("exp")-System.currentTimeMillis()/1000<7*DAY)refresh(c);
        }
        if(before!=allowed)changed();
    }
    private static void updateBlocker(Context context,final boolean activate){
        if(!blockerRunning.compareAndSet(false,true))return;final Context c=context.getApplicationContext();
        BLOCKER.execute(new Runnable(){public void run(){try{if(!valid(c))return;if(activate||!AdBlock.isOn())AdBlock.setEnabled(c,true);else AdBlock.ensure(c);if(prefs(c).getBoolean("refresh_after_blocker",false)){prefs(c).edit().remove("refresh_after_blocker").apply();refreshPremiumApps(c);}}finally{if(!valid(c)&&AdBlock.isOn())AdBlock.setEnabled(c,false);blockerRunning.set(false);}}});
    }
    static void refresh(final Context context){refresh(context,null);}
    static boolean hasSession(Context c){try{verify(c,prefs(c).getString("pass",""),false,true);return loggedIn(c);}catch(Exception invalid){return false;}}
    static long passExpiry(Context c){try{return verify(c,prefs(c).getString("pass",""),false,true).getLong("exp");}catch(Exception invalid){return 0;}}
    static String membershipMessage(Context c){return "free".equals(prefs(c).getString("membership",""))?"You’re a free member. Choose the paid tier to unlock":"No paid membership found. Choose the paid tier to unlock.";}
    static final String[] PREMIUM_APPS={ChromeCosmetic.PACKAGE,"com.google.android.apps.magazines",CallRecording.PACKAGE,AliasProvider.GOOGLE};
    static void refreshPremiumApps(Context c){
        boolean call=false;
        try{android.media.AudioManager audio=(android.media.AudioManager)c.getSystemService(Context.AUDIO_SERVICE);int mode=audio==null?0:audio.getMode();call=mode==android.media.AudioManager.MODE_IN_CALL||mode==android.media.AudioManager.MODE_IN_COMMUNICATION||mode==android.media.AudioManager.MODE_RINGTONE;}catch(Throwable ignored){}
        StringBuilder cmd=new StringBuilder();
        for(String pkg:PREMIUM_APPS){if(call&&CallRecording.PACKAGE.equals(pkg))continue;cmd.append("am force-stop ").append(pkg).append(';');}
        if(cmd.length()>0)Root.run(cmd.toString());
    }
    private static void savePass(Context c,String fresh,JSONObject claims)throws Exception{savePass(c,fresh,claims,false);}
    private static void savePass(Context c,String fresh,JSONObject claims,boolean login)throws Exception{
        boolean paid=claims.optBoolean("entitled",false);
        String command=paid?"settings put secure "+KEY+" '"+fresh+"'":"settings delete secure "+KEY;
        if(Root.run(command)==null)throw new Exception("Root access is needed to save membership changes.");
        prefs(c).edit().putString("pass",fresh).putLong("checked",System.currentTimeMillis()).putBoolean("logged_in",true).remove("signed_out").putString("membership",claims.optString("membership",paid?"paid":"none")).remove("refresh_error").apply();
        if(login&&paid){Root.run("settings put secure "+CallRecording.KEY+" 1; settings put secure "+GoogleFeedAds.KEY+" 1");prefs(c).edit().putBoolean("defaults_applied",false).putBoolean("refresh_after_blocker",true).apply();}
        reconcile(c);changed();
        if(login)refreshPremiumApps(c);
    }
    static void refresh(final Context context,final Runnable completion){
        synchronized(PatronAccess.class){
            if(completion!=null)refreshWaiters.add(completion);
            if(refreshing)return;
            if(!configured()){for(Runnable r:refreshWaiters)MAIN.post(r);refreshWaiters.clear();return;}
            refreshing=true;
        }
        final Context c=context.getApplicationContext();NET.execute(new Runnable(){public void run(){
            HttpURLConnection connection=null;try{
                String pass=prefs(c).getString("pass","");verify(c,pass,false,true);
                connection=(HttpURLConnection)new URL(PatronConfig.SERVER+"/refresh").openConnection();connection.setRequestMethod("POST");connection.setConnectTimeout(15000);connection.setReadTimeout(20000);connection.setRequestProperty("Authorization","Bearer "+pass);connection.setInstanceFollowRedirects(false);
                int code=connection.getResponseCode();
                if(code==401||code==403){Root.run("settings delete secure "+KEY);prefs(c).edit().remove("pass").remove("logged_in").remove("membership").putString("refresh_error","Sign-in expired. Sign in with Patreon again.").apply();reconcile(c);changed();}
                else if(code==200){String fresh=new JSONObject(read(connection.getInputStream())).getString("pass");savePass(c,fresh,verify(c,fresh,true));}
                else throw new Exception("Couldn’t check membership. Try again shortly.");
            }catch(Exception failure){prefs(c).edit().putString("refresh_error",failure.getMessage()==null?"Couldn’t check membership. Try again shortly.":failure.getMessage()).apply();}
            finally{if(connection!=null)connection.disconnect();synchronized(PatronAccess.class){refreshing=false;for(Runnable r:refreshWaiters)MAIN.post(r);refreshWaiters.clear();}}
        }});
    }
    static String read(java.io.InputStream in)throws Exception{try{java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();byte[] b=new byte[1024];int n;while((n=in.read(b))!=-1){if(out.size()+n>16384)throw new Exception("response");out.write(b,0,n);}return out.toString("UTF-8");}finally{in.close();}}
    static void accept(final Context context,final android.net.Uri uri,final java.util.function.Consumer<String> done){
        final Context c=context.getApplicationContext();NET.execute(new Runnable(){public void run(){String error=null;try{
            String state=uri.getQueryParameter("state"),expected=prefs(c).getString("nonce","");long started=prefs(c).getLong("login_at",0);
            if(expected.isEmpty()&&state!=null&&state.equals(prefs(c).getString("claimed_nonce",""))){MAIN.post(new Runnable(){public void run(){done.accept(null);}});return;}
            if(expected.isEmpty()||!expected.equals(state)||System.currentTimeMillis()-started>10*60*1000)throw new Exception("Sign-in expired. Tap Unlock to try again.");
            prefs(c).edit().remove("nonce").remove("join_return").putString("claimed_nonce",expected).apply();String code=uri.getQueryParameter("error");
            if(code!=null)throw new Exception("not_active".equals(code)?"No active membership found. Join, then tap Unlock again.":"Sign-in wasn't completed. You can try again.");
            String token=uri.getQueryParameter("pass");try{verify(c,token,true);}catch(Exception invalid){throw new Exception("Couldn't verify this sign-in. Tap Unlock to try again.");}
            savePass(c,token,verify(c,token,true),true);ensureProfile(c);
        }catch(Exception e){error=e.getMessage()==null?"Couldn't confirm membership. Try again.":e.getMessage();}final String message=error;MAIN.post(new Runnable(){public void run(){done.accept(message);}});}});
    }
    static boolean claimPending(Context c){long started=prefs(c).getLong("login_at",0);return !prefs(c).getString("nonce","").isEmpty()&&started>0&&System.currentTimeMillis()-started<10*60*1000;}
    static void claim(final Context context,final java.util.function.Consumer<String> done,final Runnable pending){
        final Context c=context.getApplicationContext();NET.execute(new Runnable(){public void run(){
            HttpURLConnection connection=null;android.net.Uri result=null;
            try{
                if(!configured()||!claimPending(c))return;
                String nonce=prefs(c).getString("nonce","");
                JSONObject body=new JSONObject().put("state",nonce).put("device",device(c));
                connection=(HttpURLConnection)new URL(PatronConfig.SERVER+"/claim").openConnection();connection.setRequestMethod("POST");connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(10000);connection.setReadTimeout(10000);connection.setDoOutput(true);connection.setRequestProperty("Content-Type","application/json");
                byte[] bytes=body.toString().getBytes("UTF-8");connection.getOutputStream().write(bytes);
                if(connection.getResponseCode()==200){
                    JSONObject answer=new JSONObject(read(connection.getInputStream()));
                    android.net.Uri.Builder uri=android.net.Uri.parse("pixelsuite://unlock").buildUpon().appendQueryParameter("state",nonce);
                    if(answer.has("pass"))uri.appendQueryParameter("pass",answer.getString("pass"));else uri.appendQueryParameter("error",answer.optString("error","sign_in_failed"));
                    result=uri.build();
                }
            }catch(Exception ignored){}
            finally{if(connection!=null)connection.disconnect();}
            if(result!=null&&claimPending(c))accept(c,result,done);else if(pending!=null)MAIN.post(pending);
        }});
    }
    private static void schedule(Context c){try{android.app.job.JobScheduler scheduler=(android.app.job.JobScheduler)c.getSystemService(Context.JOB_SCHEDULER_SERVICE);if(scheduler==null)return;for(android.app.job.JobInfo job:scheduler.getAllPendingJobs())if(job.getId()==0x5AD1)return;scheduler.schedule(new android.app.job.JobInfo.Builder(0x5AD1,new android.content.ComponentName(c,PatronRefreshJob.class)).setRequiredNetworkType(android.app.job.JobInfo.NETWORK_TYPE_ANY).setPersisted(true).setPeriodic(7*DAY*1000).build());}catch(Exception ignored){}}
    static boolean loggedIn(Context c){return prefs(c).getBoolean("logged_in",false);}
    static boolean valid(Context c){try{List<String> data=Root.run("settings get secure "+KEY);return data!=null&&!data.isEmpty()&&verify(c,data.get(0),false).optBoolean("entitled",false);}catch(Exception e){return false;}}
    private static final java.util.concurrent.atomic.AtomicBoolean profileRunning=new java.util.concurrent.atomic.AtomicBoolean();
    private static volatile long profileAttempt;
    static void ensureProfile(final Context context){
        final Context c=context.getApplicationContext();
        if(!loggedIn(c)||!profileRunning.compareAndSet(false,true))return;
        long now=android.os.SystemClock.elapsedRealtime();
        boolean photo=new java.io.File(c.getFilesDir(),"patron-photo.png").isFile();
        if(profileAttempt!=0&&now-profileAttempt<(photo?15*60*1000:30*1000)){profileRunning.set(false);return;}
        profileAttempt=now;
        NET.execute(new Runnable(){public void run(){try{loadProfile(c);}finally{profileRunning.set(false);changed();}}});
    }
    private static boolean photoHost(URL url){String h=url.getHost().toLowerCase(java.util.Locale.ROOT);return "https".equals(url.getProtocol())&&(h.endsWith(".patreonusercontent.com")||h.equals("patreon.com")||h.endsWith(".patreon.com"));}
    private static void loadProfile(Context c){
        HttpURLConnection connection=null;try{
            connection=(HttpURLConnection)new URL(PatronConfig.SERVER+"/profile").openConnection();connection.setRequestMethod("POST");connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(15000);connection.setReadTimeout(15000);connection.setRequestProperty("Authorization","Bearer "+prefs(c).getString("pass",""));if(connection.getResponseCode()!=200)return;
            JSONObject profile=new JSONObject(read(connection.getInputStream()));prefs(c).edit().putString("name",profile.optString("name","Patreon account")).apply();
            String full=profile.optString("photo"),small=profile.optString("thumbnail");
            if(!downloadPhoto(c,full)&&!small.equals(full))downloadPhoto(c,small);
        }catch(Exception ignored){}finally{if(connection!=null)connection.disconnect();}
    }
    private static boolean downloadPhoto(Context c,String url){
        HttpURLConnection picture=null;try{
            if(url.isEmpty())return false;URL image=new URL(url);
            for(int hop=0;hop<4;hop++){
                if(!photoHost(image))return false;
                picture=(HttpURLConnection)image.openConnection();picture.setInstanceFollowRedirects(false);picture.setConnectTimeout(10000);picture.setReadTimeout(10000);picture.setRequestProperty("Accept","image/*");
                int status=picture.getResponseCode();
                if(status==200)break;
                String location=picture.getHeaderField("Location");picture.disconnect();picture=null;
                if(status<300||status>399||location==null)return false;image=new URL(image,location);
            }
            if(picture==null||picture.getResponseCode()!=200)return false;
            java.io.InputStream in=picture.getInputStream();java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();
            try{byte[] chunk=new byte[4096];int n;while((n=in.read(chunk))!=-1){if(bytes.size()+n>1024*1024)throw new Exception("photo");bytes.write(chunk,0,n);}}finally{in.close();}
            byte[] raw=bytes.toByteArray();android.graphics.BitmapFactory.Options options=new android.graphics.BitmapFactory.Options();options.inJustDecodeBounds=true;android.graphics.BitmapFactory.decodeByteArray(raw,0,raw.length,options);
            if(options.outWidth<1||options.outHeight<1||options.outWidth>8192||options.outHeight>8192)return false;
            options.inSampleSize=Math.max(1,Math.max(options.outWidth,options.outHeight)/256);options.inJustDecodeBounds=false;
            android.graphics.Bitmap bitmap=android.graphics.BitmapFactory.decodeByteArray(raw,0,raw.length,options);if(bitmap==null)return false;
            android.util.AtomicFile photo=new android.util.AtomicFile(new java.io.File(c.getFilesDir(),"patron-photo.png"));java.io.FileOutputStream out=null;
            try{out=photo.startWrite();if(!bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out))throw new Exception("photo");photo.finishWrite(out);out=null;return true;}finally{if(out!=null)photo.failWrite(out);bitmap.recycle();}
        }catch(Exception ignored){return false;}finally{if(picture!=null)picture.disconnect();}
    }
    static void logout(final Context context){final Context c=context.getApplicationContext();final String pass=prefs(c).getString("pass","");
        prefs(c).edit().remove("pass").remove("name").remove("logged_in").remove("nonce").remove("membership").remove("refresh_error").putBoolean("signed_out",true).commit();
        NET.execute(new Runnable(){public void run(){
        Root.run("settings delete secure "+KEY+"; settings put secure "+CallRecording.KEY+" 0; settings put secure "+GoogleFeedAds.KEY+" 0");
        c.deleteFile("patron-photo.png");profileAttempt=0;
        try{AdBlock.setEnabled(c,false);}catch(Throwable ignored){}
        reconcile(c);changed();
        refreshPremiumApps(c);
        HttpURLConnection connection=null;try{connection=(HttpURLConnection)new URL(PatronConfig.SERVER+"/logout").openConnection();connection.setRequestMethod("POST");connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(10000);connection.setReadTimeout(10000);connection.setRequestProperty("Authorization","Bearer "+pass);connection.getResponseCode();}catch(Exception ignored){}finally{if(connection!=null)connection.disconnect();}
    }});}
}
