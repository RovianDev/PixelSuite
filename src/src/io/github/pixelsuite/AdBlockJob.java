package io.github.pixelsuite;

import android.app.job.JobParameters;
import android.app.job.JobService;

public final class AdBlockJob extends JobService {
    @Override
    public boolean onStartJob(final JobParameters params) {
        new Thread(new Runnable() {
            @Override public void run() {
                boolean retry = false;
                try {
                    if (PatronAccess.valid(getApplicationContext()) && AdBlock.isOn()) retry = !AdBlock.update(getApplicationContext()).ok;
                    else { if (AdBlock.isOn()) AdBlock.setEnabled(getApplicationContext(), false); AdBlock.stop(getApplicationContext()); }
                } catch (Throwable ignored) { }
                jobFinished(params, retry);
            }
        }, "PixelSuiteAdBlock").start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return true;
    }
}
