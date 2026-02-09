package com.sss.michael.exo.util;

import android.util.Log;

import com.sss.michael.exo.ExoConfig;

public class ExoLog {
    private static final String TAG = "Exo_SSS";

    public static void log(String log) {
        log(log, false);
    }

    public static void log(String log, boolean needStackTrace) {
        if (ExoConfig.LOG_ENABLE) Log.e(TAG, log);
        if (needStackTrace) {
            if (ExoConfig.LOG_ENABLE) {
                // 增加类名和行号，一眼看出是哪个组件打印的
                StackTraceElement caller = Thread.currentThread().getStackTrace()[3];
                String tag = TAG + "[" + caller.getFileName() + ":" + caller.getLineNumber() + "]";
                Log.e(tag, log);
            }
        }
    }

    public static void log(String log, Throwable e) {
        Log.e(TAG, log, e);
    }

}
