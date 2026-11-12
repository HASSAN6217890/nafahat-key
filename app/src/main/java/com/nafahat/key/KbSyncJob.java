package com.nafahat.key;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;

/** Every 6 hours on any network: download the Nafahat library (KbSync). Never uploads anything. */
public final class KbSyncJob extends JobService {
    private static final int JOB_ID = 5005;
    private static final long PERIOD_MS = 6L * 60 * 60 * 1000;

    /** Idempotent; called when the keyboard or the app opens (no boot permission is used). */
    public static void schedule(Context context) {
        try {
            JobScheduler js = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
            if (js == null || js.getPendingJob(JOB_ID) != null) return;
            js.schedule(new JobInfo.Builder(JOB_ID, new ComponentName(context, KbSyncJob.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(PERIOD_MS)
                .build());
        } catch (RuntimeException ignored) { }
    }

    @Override public boolean onStartJob(JobParameters params) {
        final Context app = getApplicationContext();
        new Thread(() -> {
            int code = KbSync.run(app, false);
            jobFinished(params, code == KbSync.OFFLINE);
        }, "nafahatkey-kb").start();
        return true;
    }

    @Override public boolean onStopJob(JobParameters params) { return true; }
}
