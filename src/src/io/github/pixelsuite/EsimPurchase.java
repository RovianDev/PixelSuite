package io.github.pixelsuite;

import android.app.*;import android.content.*;import android.os.*;import android.provider.Settings;import android.view.*;import android.widget.*;import org.json.JSONObject;
import java.net.*;import java.security.*;import java.security.spec.X509EncodedKeySpec;import java.util.*;import java.util.concurrent.*;

final class EsimPurchase {
 static final String PASS="pixelsuite_esim",DEVICE="pixelsuite_device_id";
 private static final ExecutorService IO=Executors.newSingleThreadExecutor();private static final Handler MAIN=new Handler(Looper.getMainLooper());
 private static boolean refreshing;private static final ArrayList<Runnable> waiters=new ArrayList<Runnable>();
 private static String cachedPass,cachedDevice;private static boolean cachedValid;
 private static SharedPreferences prefs(Context c){return c.getSharedPreferences("esim_purchase",0);}
 static boolean validCode(String value){return value!=null&&value.matches("PSW-[A-HJ-NP-Z2-9]{4}-[A-HJ-NP-Z2-9]{4}-[A-HJ-NP-Z2-9]{4}");}
 static String code(String value){return value==null?"":value.trim().toUpperCase(java.util.Locale.ROOT);}
 static synchronized String device(Context c)throws Exception{
  String value=Settings.Secure.getString(c.getContentResolver(),DEVICE);
  if(value!=null&&!value.isEmpty()){if(!value.matches("[A-Za-z0-9_-]{43}"))throw new Exception("The device ID needs support. It has not been changed.");return value;}
  value=PatronAccess.random();if(Root.run("settings put secure "+DEVICE+" '"+value+"'")==null)throw new Exception("Allow root access to save your device ID.");
  if(!value.equals(Settings.Secure.getString(c.getContentResolver(),DEVICE)))throw new Exception("Couldn’t save the device ID. Check root access.");return value;
 }
 static JSONObject verify(String token,String did)throws Exception{
  if(token==null||token.length()>4096||!token.matches("[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+"))throw new Exception("Invalid purchase pass.");
  String[] parts=token.split("\\.");JSONObject h=new JSONObject(new String(PatronAccess.decode(parts[0]),"UTF-8"));
  if(h.length()!=2||!"ES256".equals(h.opt("alg"))||!"JWT".equals(h.opt("typ")))throw new Exception("Invalid signature format.");
  byte[] raw=PatronAccess.decode(parts[2]);if(raw.length!=64)throw new Exception("Invalid signature.");
  Signature signature=Signature.getInstance("SHA256withECDSA");signature.initVerify(KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(android.util.Base64.decode(PatronConfig.PUBLIC_KEY,android.util.Base64.DEFAULT))));
  signature.update((parts[0]+"."+parts[1]).getBytes("US-ASCII"));if(!signature.verify(PatronAccess.der(raw)))throw new Exception("Couldn’t verify this purchase.");
  JSONObject p=new JSONObject(new String(PatronAccess.decode(parts[1]),"UTF-8"));Object v=p.opt("v"),iat=p.opt("iat");
  if(!(v instanceof Integer||v instanceof Long)||((Number)v).longValue()!=1||!"pixelsuite-store".equals(p.opt("iss"))||!"io.github.roviandev.pixelsuite".equals(p.opt("aud"))||!"esim".equals(p.opt("product"))||!Boolean.TRUE.equals(p.opt("entitled"))||!(p.opt("sub") instanceof String)||!p.getString("sub").matches("[A-Za-z0-9_-]{6,64}")||did==null||!did.matches("[A-Za-z0-9_-]{43}")||!did.equals(p.opt("did"))||!(iat instanceof Integer||iat instanceof Long)||((Number)iat).longValue()<=0||((Number)iat).longValue()>System.currentTimeMillis()/1000+300||p.has("exp"))throw new Exception("This purchase pass is not valid for this phone.");
  return p;
 }
 static synchronized boolean unlocked(Context c){try{String token=Settings.Secure.getString(c.getContentResolver(),PASS),did=Settings.Secure.getString(c.getContentResolver(),DEVICE);if(!java.util.Objects.equals(token,cachedPass)||!java.util.Objects.equals(did,cachedDevice)){cachedPass=token;cachedDevice=did;cachedValid=false;verify(token,did);cachedValid=true;}return cachedValid;}catch(Exception invalid){return false;}}
 private static JSONObject post(String path,JSONObject body)throws Exception{
  HttpURLConnection connection=(HttpURLConnection)new URL(PatronConfig.SERVER+path).openConnection();try{
   connection.setRequestMethod("POST");connection.setConnectTimeout(15000);connection.setReadTimeout(20000);connection.setInstanceFollowRedirects(false);connection.setDoOutput(true);connection.setRequestProperty("Content-Type","application/json");byte[] bytes=body.toString().getBytes("UTF-8");connection.setFixedLengthStreamingMode(bytes.length);try(java.io.OutputStream out=connection.getOutputStream()){out.write(bytes);}
   int status=connection.getResponseCode();java.io.InputStream input=status==200?connection.getInputStream():connection.getErrorStream();JSONObject result=input==null?new JSONObject():new JSONObject(PatronAccess.read(input));
   if(status!=200)throw new Exception(result.optString("message","Couldn’t connect. Check your internet connection and try again."));return result;
  }catch(java.io.IOException e){throw new Exception("No connection. Your saved purchase has been kept.");}finally{connection.disconnect();}
 }
 private static void save(Context c,String token)throws Exception{
  verify(token,device(c));if(Root.run("settings put secure "+PASS+" '"+token+"'")==null)throw new Exception("Allow root access to save your purchase.");
  if(!token.equals(Settings.Secure.getString(c.getContentResolver(),PASS)))throw new Exception("Couldn’t save the purchase. Check root access.");
  prefs(c).edit().putLong("checked",System.currentTimeMillis()).remove("error").apply();PatronAccess.changed();schedule(c);
 }
 static void resume(final Context context){final Context c=context.getApplicationContext();IO.execute(()->{schedule(c);if(unlocked(c)&&System.currentTimeMillis()-prefs(c).getLong("checked",0)>=24*60*60*1000L)refresh(c,null);});}
 static void refresh(Context context,Runnable done){synchronized(EsimPurchase.class){if(done!=null)waiters.add(done);if(refreshing)return;refreshing=true;}final Context c=context.getApplicationContext();IO.execute(()->{
  try{String token=Settings.Secure.getString(c.getContentResolver(),PASS);if(token==null||token.isEmpty())return;String did=device(c);JSONObject claims=verify(token,did);JSONObject result=post("/esim/check",new JSONObject().put("sub",claims.getString("sub")).put("did",did));String status=result.getString("status");
   if("revoked".equals(status)){if(Root.run("settings delete secure "+PASS)==null)throw new Exception("Allow root access to update your purchase.");prefs(c).edit().putString("error","This purchase was revoked or moved to another phone.").apply();PatronAccess.changed();}
   else if(!"active".equals(status))throw new Exception("Couldn’t confirm the purchase. Your saved pass has been kept.");
   else prefs(c).edit().remove("error").apply();prefs(c).edit().putLong("checked",System.currentTimeMillis()).apply();
  }catch(Exception e){prefs(c).edit().putString("error",e.getMessage()).apply();}
  finally{synchronized(EsimPurchase.class){refreshing=false;for(Runnable callback:waiters)MAIN.post(callback);waiters.clear();}}
 });}
 private static void schedule(Context c){try{android.app.job.JobScheduler jobs=(android.app.job.JobScheduler)c.getSystemService(Context.JOB_SCHEDULER_SERVICE);for(android.app.job.JobInfo job:jobs.getAllPendingJobs())if(job.getId()==0x5AD2&&new ComponentName(c,EsimRefreshJob.class).equals(job.getService()))return;jobs.schedule(new android.app.job.JobInfo.Builder(0x5AD2,new ComponentName(c,EsimRefreshJob.class)).setRequiredNetworkType(android.app.job.JobInfo.NETWORK_TYPE_ANY).setPersisted(true).setPeriodic(24*60*60*1000L).build());}catch(Exception ignored){}}
 static void open(Activity a){new Sheet(a,"",false);}
 static void open(Activity a,String initial){new Sheet(a,validCode(code(initial))?code(initial):"",true);}
 private static final class Sheet{
  final Activity a;final Dialog dialog;final LinearLayout content;final int ink,muted;boolean busy;int mode;String entered="",buyer="",otp="",message="";EditText field;long resendAt;TextView resendButton;
  final Runnable countdown=new Runnable(){public void run(){if(dialog.isShowing()&&mode==3&&resendButton!=null){long seconds=Math.max(0,(resendAt-SystemClock.elapsedRealtime()+999)/1000);resendButton.setText(I18n.cs(seconds>0?"Resend code ("+seconds+"s)":"Resend code"));resendButton.setEnabled(!busy&&seconds==0);resendButton.setAlpha(busy||seconds>0?.5f:1f);if(seconds>0)MAIN.postDelayed(this,1000);}}};
  void update(){if(dialog.isShowing())render();}
  final Runnable updated=()->update();
  Sheet(Activity activity,String initial,boolean enter){a=activity;entered=initial;mode=enter?1:0;boolean dark=FrostedSurface.dark(a);ink=dark?0xfff3f1f6:0xff242329;muted=dark?0xffb9b8bf:0xff65636b;dialog=new Dialog(a);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);content=new LinearLayout(a);content.setOrientation(1);content.setPadding(dp(24),dp(16),dp(24),dp(24));content.setBackground(AppSurfaces.dialog(a,28));ScrollView scroll=new ScrollView(a);ToastMotion.watchScroll(scroll);scroll.addView(content);dialog.setContentView(scroll);dialog.setOnDismissListener(d->{PatronAccess.unwatch(updated);MAIN.removeCallbacks(countdown);});PatronAccess.watch(updated);render();dialog.show();Window w=dialog.getWindow();if(w!=null){w.setBackgroundDrawableResource(android.R.color.transparent);w.setGravity(Gravity.CENTER);w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);AppSurfaces.dim(w,.5f);AppPanelBounds.fit(a,w,-2);}}
  int dp(float value){return Math.round(value*a.getResources().getDisplayMetrics().density);}
  TextView text(String value,int size,boolean bold){TextView t=new TextView(a);t.setText(I18n.cs(value));t.setTextSize(size);t.setTextColor(bold?ink:muted);t.setTypeface(android.graphics.Typeface.create("sans-serif",bold?1:0));t.setPadding(0,dp(7),0,dp(7));content.addView(t);return t;}
  TextView button(String label,Runnable action){TextView t=text(label,16,true);t.setGravity(Gravity.CENTER);t.setMinimumHeight(dp(48));t.setBackground(new ThemeGlass(a,24,ThemeGlass.FIELD));((LinearLayout.LayoutParams)t.getLayoutParams()).topMargin=dp(10);t.setEnabled(!busy);t.setAlpha(busy?.5f:1);t.setOnClickListener(v->action.run());return t;}
  void input(String hint,String value,int type){field=new EditText(a);field.setSingleLine(true);field.setTextSize(16);field.setTextColor(ink);field.setHintTextColor(muted);field.setHint(I18n.cs(hint));field.setInputType(type);field.setText(I18n.cs(value));field.setPadding(dp(16),dp(10),dp(16),dp(10));field.setBackground(new ThemeGlass(a,22,ThemeGlass.FIELD));field.setEnabled(!busy);content.addView(field,new LinearLayout.LayoutParams(-1,dp(54)));}
  void remember(){if(field==null)return;if(mode==1)entered=field.getText().toString();else if(mode==2)buyer=field.getText().toString();else if(mode==3)otp=field.getText().toString();field=null;}
  void render(){if(a.isFinishing()||a.isDestroyed())return;remember();MAIN.removeCallbacks(countdown);resendButton=null;content.removeAllViews();TextView close=text("×",25,true);AppCloseButton.style(close);LinearLayout.LayoutParams closeSize=new LinearLayout.LayoutParams(dp(48),dp(48));closeSize.gravity=Gravity.END;close.setLayoutParams(closeSize);close.setOnClickListener(v->dialog.dismiss());text("Pixel watch eSIM",23,true);
   if(unlocked(a)){text("Pixel Watch eSIM ✓ (purchased)",18,true);text("Yours to keep on this phone. Patreon is not required.",15,false);button(busy?"Checking…":"Refresh purchase",()->{busy=true;render();refresh(a,()->{busy=false;if(dialog.isShowing()){message=prefs(a).getString("error",unlocked(a)?"Purchase is active.":"Purchase is no longer active.");render();}});});}
   else{text("One-time purchase, $10, yours to keep.",18,true);text("One phone at a time. Requires a compatible Pixel Watch and eSIM plan.",15,false);
    if(mode==0){text("After checkout, your purchase code goes to the email you pay with. Check your inbox and Junk/Spam folder for purchases@roviandev.com.",14,false);button("Buy · $10",()->buy());button("Enter code",()->{mode=1;message="";render();});button("Restore purchase",()->{mode=2;message="";render();});}
    if(mode==1){text("Find your purchase code in the email from purchases@roviandev.com. Check Junk/Spam too.",14,false);input("PSW-XXXX-XXXX-XXXX",entered,android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS|android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);button(busy?"Unlocking…":"Unlock",()->activate());button("Restore purchase by email",()->{remember();mode=2;message="";render();});}
    if(mode==2){input("Email used for purchase",buyer,android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);button(busy?"Sending…":"Send recovery code",()->startRestore());}
    if(mode==3){text("Purchase email: "+buyer,15,true);text("Check your inbox and Junk/Spam folder for purchases@roviandev.com. Use the newest code; it expires in 10 minutes.",14,false);input("6-digit code",otp,android.text.InputType.TYPE_CLASS_NUMBER);button(busy?"Restoring…":"Restore on this phone",()->restore());resendButton=button("Resend code",()->startRestore());countdown.run();button("Change email",()->{remember();mode=2;message="";render();});}
    if(mode!=0)button("Back",()->{remember();mode=0;message="";render();});
    text("Non-refundable once activated, except where required by law or Buy Me a Coffee’s rules.",13,false);
   }
   if(!message.isEmpty()){text(message,15,false);if(message.contains("support"))button("Contact support",()->{try{a.startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse("https://github.com/RovianDev/PixelSuite/issues")));}catch(Exception ignored){}});}
  }
  interface Work{void run()throws Exception;}
  void run(Work work){remember();busy=true;message="";render();IO.execute(()->{String error=null;try{work.run();}catch(Exception e){error=e.getMessage();}final String result=error;MAIN.post(()->{busy=false;if(!dialog.isShowing())return;field=null;if(result!=null)message=result;render();});});}
  void buy(){run(()->{JSONObject config=post("/esim/config",new JSONObject());if(!config.optBoolean("ready"))throw new Exception("The shop is being set up. Please try again later.");String url=config.getString("buy_url");android.net.Uri u=android.net.Uri.parse(url);String host=u.getHost();if(!"https".equals(u.getScheme())||!("buymeacoffee.com".equals(host)||"www.buymeacoffee.com".equals(host)))throw new Exception("Couldn’t verify the purchase link.");MAIN.post(()->{if(dialog.isShowing())a.startActivity(new Intent(Intent.ACTION_VIEW,u));});});}
  void activate(){remember();final String value=code(entered);if(!validCode(value)){message="Check your license code and try again.";render();return;}run(()->{JSONObject result=post("/esim/activate",new JSONObject().put("code",value).put("did",device(a)));save(a,result.getString("pass"));message="Purchase unlocked.";});}
  void startRestore(){remember();if(mode==3&&SystemClock.elapsedRealtime()<resendAt)return;final String address=buyer.trim().toLowerCase(java.util.Locale.ROOT);if(!android.util.Patterns.EMAIL_ADDRESS.matcher(address).matches()){message="Enter the email you paid with.";render();return;}run(()->{JSONObject result=post("/esim/restore/start",new JSONObject().put("email",address));buyer=address;otp="";resendAt=SystemClock.elapsedRealtime()+Math.max(60,Math.min(3600,result.optInt("retry_after",60)))*1000L;mode=3;message="If a purchase matches this email, your recovery code is on its way.";});}
  void restore(){remember();final String value=otp.trim();if(!value.matches("[0-9]{6}")){message="Enter the six-digit email code.";render();return;}run(()->{JSONObject result=post("/esim/restore/verify",new JSONObject().put("email",buyer).put("otp",value).put("did",device(a)));save(a,result.getString("pass"));message="Purchase restored on this phone.";});}
 }
}
