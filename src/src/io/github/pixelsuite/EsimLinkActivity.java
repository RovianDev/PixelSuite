package io.github.pixelsuite;
public final class EsimLinkActivity extends android.app.Activity{
 @Override public void onCreate(android.os.Bundle state){super.onCreate(state);android.net.Uri uri=getIntent().getData();String value="";
  if(uri!=null&&"pixelsuite".equals(uri.getScheme())&&"esim".equals(uri.getHost())){String candidate=EsimPurchase.code(uri.getQueryParameter("code"));if(EsimPurchase.validCode(candidate))value=candidate;}
  startActivity(new android.content.Intent(this,SettingsActivity.class).putExtra("page",WatchEsim.KEY).putExtra("esim_code",value));finish();
 }
}
