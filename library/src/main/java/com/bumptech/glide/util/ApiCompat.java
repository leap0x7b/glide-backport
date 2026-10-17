package com.bumptech.glide.util;

import android.os.Build;

/**
 * Compatibility helpers for running on API 1 (Android 1.0).
 *
 * <p>{@link Build.VERSION#SDK_INT} was only added in API 4 (Donut). On API 1-3 only the deprecated
 * {@link Build.VERSION#SDK} string exists. All version checks in Glide go through here so the same
 * bytecode runs on API 1 without throwing {@link NoSuchFieldError} at class-verification or
 * field-resolution time.
 *
 * <p>Integer constants from {@link Build.VERSION_CODES} are inlined by javac and are therefore safe
 * to reference directly, but the {@code SDK_INT} field itself must never be touched except inside
 * the guarded accessor below.
 */
public final class ApiCompat {
    // Inlined copies of Build.VERSION_CODES values so we never need to load that class at runtime
    // on API 1 devices (javac would inline them anyway, this just makes the guarantee explicit).
    public static final int ECLAIR = 5;
    public static final int HONEYCOMB = 11;
    public static final int HONEYCOMB_MR1 = 12;
    public static final int HONEYCOMB_MR2 = 13;
    public static final int ICE_CREAM_SANDWICH = 14;
    public static final int JELLY_BEAN = 16;
    public static final int JELLY_BEAN_MR1 = 17;
    public static final int KITKAT = 19;

    private ApiCompat() {
        // Utility class.
    }

    /**
     * Returns the device's API level, working all the way back to API 1.
     *
     * <p>Reads {@link Build.VERSION#SDK_INT} fresh on every call so tests that mutate it via
     * reflection (e.g. {@code Util.setSdkVersionInt}) are honored. The field access itself is
     * guarded for API 1-3 runtimes where it does not exist.
     */
    @SuppressWarnings("deprecation")
    public static int getSdkInt() {
        try {
            // Present on API 4+. Throws NoSuchFieldError on API 1-3 at field-resolution time.
            return Build.VERSION.SDK_INT;
        } catch (NoSuchFieldError e) {
            // API 1-3 only have Build.VERSION.SDK (a String like "1", "2", "3").
            try {
                String raw = (String) Build.VERSION.class.getField("SDK").get(null);
                return Integer.parseInt(raw);
            } catch (Exception ignored) {
                return 1;
            }
        } catch (Exception ignored) {
            return 1;
        }
    }

    /**
     * Returns true if the device API level is at least the given level.
     */
    public static boolean isAtLeast(int apiLevel) {
        return getSdkInt() >= apiLevel;
    }
}
