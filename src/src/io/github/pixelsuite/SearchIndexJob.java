package io.github.pixelsuite;

import android.app.job.JobParameters;
import android.app.job.JobService;

public final class SearchIndexJob extends JobService {
    @Override public boolean onStartJob(final JobParameters params) {
        if (!PixelSearch.active(this) || !PatronGate.ok(this)) { SearchIndex.cancel(this); return false; }
        if (params.getJobId() == SearchIndex.WATCH_ID) {
            boolean lost = false;
            try {
                android.net.Uri[] uris = (android.net.Uri[]) JobParameters.class.getMethod("getTriggeredContentUris").invoke(params);
                if (uris == null) lost = true;
                else for (android.net.Uri u : uris) {
                    String id = u.getLastPathSegment();
                    if (id == null || !android.text.TextUtils.isDigitsOnly(id)) continue;
                    android.database.Cursor q = getContentResolver().query(u, new String[]{"_id"}, null, null, null);
                    try { if (q == null || !q.moveToFirst()) lost = true; } finally { if (q != null) q.close(); }
                }
            } catch (Throwable ignored) { lost = true; }
            final boolean gone = lost;
            new Thread(new Runnable() { public void run() {
                try { SearchIndex.sync(SearchIndexJob.this, gone); }
                finally {
                    jobFinished(params, false);
                    SearchIndex.watch(SearchIndexJob.this, SearchIndex.auto(SearchIndexJob.this));
                    SearchIndex.sync(SearchIndexJob.this, false);
                }
            } }, "PixelSearchWatch").start();
            return true;
        }
        new Thread(new Runnable() { public void run() {
            try { SearchIndex.crawl(SearchIndexJob.this); } finally { jobFinished(params, false); }
        } }, "PixelSearchJob").start();
        return true;
    }

    @Override public boolean onStopJob(JobParameters params) { return true; }
}
