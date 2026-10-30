package io.github.pixelsuite;
public final class PatronCallbackActivity extends android.app.Activity {
    @Override public void onCreate(android.os.Bundle state){super.onCreate(state);android.net.Uri uri=getIntent().getData();
        if(uri==null||!"pixelsuite".equals(uri.getScheme())||!"unlock".equals(uri.getHost())){finish();return;}
        android.widget.TextView waiting=new android.widget.TextView(this);waiting.setText("Confirming Patreon membership…");waiting.setTextSize(18);waiting.setGravity(android.view.Gravity.CENTER);setContentView(waiting);
        PatronAccess.accept(this,uri,new java.util.function.Consumer<String>(){public void accept(String error){android.content.Intent next=new android.content.Intent(PatronCallbackActivity.this,SettingsActivity.class).putExtra("page","google_apps");if(error==null&&PatronAccess.unlocked())next.putExtra("patron_thanks",true);else next.putExtra("patron_error",error==null?PatronAccess.membershipMessage(PatronCallbackActivity.this):error);startActivity(next);finish();}});
    }
}
