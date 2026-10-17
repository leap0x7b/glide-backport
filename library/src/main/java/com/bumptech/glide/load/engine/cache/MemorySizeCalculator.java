package com.bumptech.glide.load.engine.cache;

import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.Log;

import com.bumptech.glide.util.ApiCompat;

/**
 * A calculator that tries to intelligently determine cache sizes for a given device based on some constants and the
 * devices screen density, width, and height.
 */
public class MemorySizeCalculator {
    private static final String TAG = "MemorySizeCalculator";

    // Visible for testing.
    static final int BYTES_PER_ARGB_8888_PIXEL = 4;
    static final int MEMORY_CACHE_TARGET_SCREENS = 2;
    static final int BITMAP_POOL_TARGET_SCREENS = 4;
    static final float MAX_SIZE_MULTIPLIER = 0.4f;
    static final float LOW_MEMORY_MAX_SIZE_MULTIPLIER = 0.33f;

    private final int bitmapPoolSize;
    private final int memoryCacheSize;

    interface ScreenDimensions {
        int getWidthPixels();
        int getHeightPixels();
    }

    public MemorySizeCalculator(Context context) {
        this((ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE),
                new DisplayMetricsScreenDimensions(context.getResources().getDisplayMetrics()));
    }

    // Visible for testing.
    MemorySizeCalculator(ActivityManager activityManager, ScreenDimensions screenDimensions) {
        final int maxSize = getMaxSize(activityManager);

        final int screenSize = screenDimensions.getWidthPixels() * screenDimensions.getHeightPixels()
                * BYTES_PER_ARGB_8888_PIXEL;

        int targetPoolSize = screenSize * BITMAP_POOL_TARGET_SCREENS;
        int targetMemoryCacheSize = screenSize * MEMORY_CACHE_TARGET_SCREENS;

        if (targetMemoryCacheSize + targetPoolSize <= maxSize) {
            memoryCacheSize = targetMemoryCacheSize;
            bitmapPoolSize = targetPoolSize;
        } else {
            int part = Math.round((float) maxSize / (BITMAP_POOL_TARGET_SCREENS + MEMORY_CACHE_TARGET_SCREENS));
            memoryCacheSize = part * MEMORY_CACHE_TARGET_SCREENS;
            bitmapPoolSize = part * BITMAP_POOL_TARGET_SCREENS;
        }

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Calculated memory cache size: " + toMb(memoryCacheSize) + " pool size: " + toMb(bitmapPoolSize)
                    + " memory class limited? " + (targetMemoryCacheSize + targetPoolSize > maxSize) + " max size: "
                    + toMb(maxSize) + " memoryClass: " + getMemoryClass(activityManager) + " isLowMemoryDevice: "
                    + isLowMemoryDevice(activityManager));
        }
    }

    /**
     * Returns the recommended memory cache size for the device it is run on in bytes.
     */
    public int getMemoryCacheSize() {
        return memoryCacheSize;
    }

    /**
     * Returns the recommended bitmap pool size for the device it is run on in bytes.
     */
    public int getBitmapPoolSize() {
        return bitmapPoolSize;
    }

    private static int getMaxSize(ActivityManager activityManager) {
        // ActivityManager.getMemoryClass() requires API 5 (and is unreliable before API 11).
        // API 1-4 devices (e.g. G1) had a 16MB heap; use that as the fallback.
        final int memoryClassBytes = getMemoryClass(activityManager) * 1024 * 1024;
        final boolean isLowMemoryDevice = isLowMemoryDevice(activityManager);
        return Math.round(memoryClassBytes
                * (isLowMemoryDevice ? LOW_MEMORY_MAX_SIZE_MULTIPLIER : MAX_SIZE_MULTIPLIER));
    }

    private static int getMemoryClass(ActivityManager activityManager) {
        try {
            return activityManager.getMemoryClass();
        } catch (NoSuchMethodError e) {
            // API 1-4: no getMemoryClass().
            return 16;
        } catch (Exception e) {
            return 16;
        }
    }

    private static String toMb(int bytes) {
        // Formatter.formatFileSize() requires API 3; manual formatting works on API 1.
        // Keep one decimal like the framework formatter for log readability.
        float mb = bytes / (1024f * 1024f);
        return String.format("%.1fMB", mb);
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    private static boolean isLowMemoryDevice(ActivityManager activityManager) {
        // ActivityManager.isLowRamDevice() requires API 19. Treat API 1-10 as low-memory
        // (small heaps, no largeHeap) to stay conservative on old devices.
        if (ApiCompat.isAtLeast(ApiCompat.KITKAT)) {
            try {
                return activityManager.isLowRamDevice();
            } catch (NoSuchMethodError e) {
                return true;
            }
        } else {
            return !ApiCompat.isAtLeast(ApiCompat.HONEYCOMB);
        }
    }

    private static class DisplayMetricsScreenDimensions implements ScreenDimensions {
        private final DisplayMetrics displayMetrics;

        public DisplayMetricsScreenDimensions(DisplayMetrics displayMetrics) {
            this.displayMetrics = displayMetrics;
        }

        @Override
        public int getWidthPixels() {
            return displayMetrics.widthPixels;
        }

        @Override
        public int getHeightPixels() {
            return displayMetrics.heightPixels;
        }
    }
}
