package com.opx.oss.license;

import android.content.Context;

import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

/** Schedules the periodic background license validation. */
public final class LicenseScheduler {

    private static final String UNIQUE_WORK = "opxoss_license_check";

    private LicenseScheduler() {
    }

    public static void schedule(Context context) {
        try {
            Constraints constraints = new Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build();
            PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                    LicenseCheckWorker.class, 8, TimeUnit.HOURS)
                    .setConstraints(constraints)
                    .build();
            WorkManager.getInstance(context.getApplicationContext())
                    .enqueueUniquePeriodicWork(UNIQUE_WORK,
                            ExistingPeriodicWorkPolicy.UPDATE, request);
        } catch (Exception ignored) {
        }
    }
}
