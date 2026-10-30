package io.github.pixelsuite;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
public final class DoNotAnnoyTileActivity extends Activity {
 @Override protected void onCreate(Bundle state){super.onCreate(state);startActivity(new Intent(this,SettingsActivity.class).putExtra("page","do_not_annoy").putExtra("request_dna_permissions",getIntent().getBooleanExtra("request_dna_permissions",false)));finish();}
}
