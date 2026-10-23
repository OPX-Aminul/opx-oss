package com.opx.oss;

import android.app.Activity;
import android.app.Application;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.opx.oss.logger.LogEntry;
import com.opx.oss.logger.LogStore;
import com.opx.oss.ota.NotificationCenter;
import com.opx.oss.ota.UpdateScheduler;
import com.opx.oss.utils.Utils;

public class opxOSSApp extends com.opx.terminal.App {

    @Override
    public void onCreate() {
        super.onCreate();
        LogStore store = LogStore.init(this);
        store.add(LogEntry.INFO, "session", "==== opxOSS " + BuildConfig.VERSION_NAME
                + " session start ====");
        String abi = Build.SUPPORTED_ABIS.length > 0 ? Build.SUPPORTED_ABIS[0] : "unknown";
        store.add(LogEntry.INFO, "session", "Device: " + Build.MANUFACTURER + " " + Build.MODEL
                + " · Android " + Build.VERSION.RELEASE
                + " (" + abi + ")");
        com.opx.terminal.bridge.opxOSSLog.install((level, tag, message, error) -> {
            LogStore s = LogStore.peek();
            if (s == null) return;
            String text = message;
            if (error != null) {
                text = (text == null || text.isEmpty() ? "" : text + System.lineSeparator())
                        + android.util.Log.getStackTraceString(error);
            }
            s.add(level, tag, text);
        });

        NotificationCenter.ensureChannel(this);
        UpdateScheduler.schedule(this);
        applyEdgeToEdgeInsets();
        com.opx.oss.engine.GuestShellFactory.install();
        com.opx.terminal.backend.TerminalSession.setShellChooser(() -> {
            try {
                com.opx.oss.utils.Core core = new com.opx.oss.utils.Core(this);
                if (!com.opx.oss.engine.EngineType.isChosen(core)) return null;
                return com.opx.oss.engine.EngineType.isRootless(core) ? "ssh:guest" : null;
            } catch (Throwable t) {
                return null;
            }
        });
    }

    private void applyEdgeToEdgeInsets() {
        registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override
            public void onActivityStarted(@NonNull Activity activity) {
                Utils.applySystemBarInsets(activity);
            }

            @Override
            public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle state) {}

            @Override
            public void onActivityResumed(@NonNull Activity activity) {}

            @Override
            public void onActivityPaused(@NonNull Activity activity) {}

            @Override
            public void onActivityStopped(@NonNull Activity activity) {}

            @Override
            public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle b) {}

            @Override
            public void onActivityDestroyed(@NonNull Activity activity) {}
        });
    }
}
