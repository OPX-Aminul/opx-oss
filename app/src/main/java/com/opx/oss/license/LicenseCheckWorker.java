package com.opx.oss.license;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

/**
 * Periodic background validation of the bound license key. When the key is
 * blacklisted the local banned flag is set; the next foreground launch (or an
 * in-app refresh) will present the blocking UI.
 */
public final class LicenseCheckWorker extends Worker {

    public LicenseCheckWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            LicenseManager.checkNow(getApplicationContext());
            return Result.success();
        } catch (Exception e) {
            return Result.retry();
        }
    }
}
