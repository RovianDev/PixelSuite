package io.github.pixelsuite;
public final class PatronRefreshJob extends android.app.job.JobService {
    @Override public boolean onStartJob(final android.app.job.JobParameters params){final android.content.Context c=getApplicationContext();PatronAccess.refresh(c,new Runnable(){public void run(){PatronAccess.resume(c,new Runnable(){public void run(){jobFinished(params,false);}});}});return true;}
    @Override public boolean onStopJob(android.app.job.JobParameters params){return true;}
}
