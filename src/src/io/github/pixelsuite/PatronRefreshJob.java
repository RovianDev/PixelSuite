package io.github.pixelsuite;
public final class PatronRefreshJob extends android.app.job.JobService {
    @Override public boolean onStartJob(final android.app.job.JobParameters params){PatronAccess.refresh(getApplicationContext(),new Runnable(){public void run(){jobFinished(params,false);}});return true;}
    @Override public boolean onStopJob(android.app.job.JobParameters params){return true;}
}
