package com.disgusty.foxtalgia.util;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;

import androidx.annotation.ChecksSdkIntAtLeast;

/**
 * Platform version checks that work on every Android release.
 *
 * {@code Build.VERSION.SDK_INT} only exists since Android 1.6 (API 4): reading it on 1.0–1.5 throws
 * NoSuchFieldError, so the string field {@code Build.VERSION.SDK} (API 1) is parsed instead.
 */
public final class Sdk {
    public static final int INT = readSdkInt();

    private Sdk() {
    }

    @SuppressWarnings("deprecation")
    private static int readSdkInt() {
        try {
            return Integer.parseInt(Build.VERSION.SDK.trim());
        } catch (Throwable e) {
            return 1;
        }
    }

    @ChecksSdkIntAtLeast(parameter = 0)
    public static boolean atLeast(int api) {
        return INT >= api;
    }

    /** Android before 5.0, under 1.5 GB of RAM, or at most two CPU cores. */
    public static boolean isWeakDevice(Context context) {
        if (INT < 21) return true;
        if (Runtime.getRuntime().availableProcessors() <= 2) return true;
        try {
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (atLeast(19) && am.isLowRamDevice()) return true;
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            return mi.totalMem > 0 && mi.totalMem < 1536L * 1024 * 1024;
        } catch (Throwable e) {
            return false;
        }
    }

    /** Human-readable release name, e.g. "2.3.7". */
    public static String release() {
        String release = Build.VERSION.RELEASE;
        return release == null ? String.valueOf(INT) : release;
    }
}
