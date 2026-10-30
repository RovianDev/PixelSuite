package io.github.pixelsuite;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

final class PatronMembershipRefresh {
    private final Activity activity;
    private final Handler main=new Handler(Looper.getMainLooper());
    private boolean active,joined;
    private int generation,attempts;
    private long lastAttempt=-30000;
    private Runnable retry;
    PatronMembershipRefresh(Activity activity){this.activity=activity;}
    void resume(boolean returnedFromJoin){
        active=true;
        if(!PatronAccess.loggedIn(activity)||PatronAccess.offline(activity))return;
        if(!returnedFromJoin&&("paid".equals(PatronAccess.prefs(activity).getString("membership",""))||SystemClock.elapsedRealtime()-lastAttempt<30000))return;
        if(retry!=null)main.removeCallbacks(retry);
        joined=returnedFromJoin;attempts=0;int run=++generation;check(run);
    }
    private void check(final int run){
        if(!active||run!=generation||activity.isFinishing()||activity.isDestroyed()||!PatronAccess.loggedIn(activity)||PatronAccess.offline(activity))return;
        lastAttempt=SystemClock.elapsedRealtime();attempts++;
        PatronAccess.refresh(activity,()->{
            if(!active||run!=generation||activity.isFinishing()||activity.isDestroyed())return;
            if(PatronAccess.unlocked()){
                if(joined){joined=false;PatronPaper.open(activity,2,null);}
                return;
            }
            if(!PatronAccess.loggedIn(activity)||attempts>=4)return;
            long delay=attempts==1?3000:attempts==2?8000:15000;
            retry=()->check(run);main.postDelayed(retry,delay);
        });
    }
    void pause(){active=false;generation++;if(retry!=null)main.removeCallbacks(retry);retry=null;}
}
