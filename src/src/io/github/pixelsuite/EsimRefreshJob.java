package io.github.pixelsuite;
public final class EsimRefreshJob extends android.app.job.JobService{
 public boolean onStartJob(final android.app.job.JobParameters p){EsimPurchase.refresh(this,()->jobFinished(p,false));return true;}
 public boolean onStopJob(android.app.job.JobParameters p){return true;}
}
