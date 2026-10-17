package com.bumptech.glide.load.resource.bitmap;

import android.util.Log;

/**
 * EXIF access via reflection so {@link TransformationUtils} can load on API 1-4 where
 * {@code android.media.ExifInterface} (added in API 5) does not exist.
 *
 * <p>All framework interaction happens through reflection; this class itself has no static
 * dependency on {@code ExifInterface} and therefore verifies on API 1.
 */
final class ExifCompat {
    private static final String TAG = "ExifCompat";

    // Inlined copies of ExifInterface constants (API 5+). Used to avoid loading that class.
    static final String TAG_ORIENTATION = "Orientation";
    static final int ORIENTATION_UNDEFINED = 0;

    private ExifCompat() {
    }

    /**
     * Returns the EXIF orientation for the file at path, or {@link #ORIENTATION_UNDEFINED} if
     * EXIF is unavailable (API 1-4) or on any failure.
     */
    static int getOrientation(String path) {
        try {
            Class<?> exifClass = Class.forName("android.media.ExifInterface");
            Object exif = exifClass.getConstructor(String.class).newInstance(path);
            Object[] args = new Object[] { TAG_ORIENTATION, ORIENTATION_UNDEFINED };
            // getAttributeInt(String, int)
            java.lang.reflect.Method m =
                    exifClass.getMethod("getAttributeInt", String.class, int.class);
            Object result = m.invoke(exif, args);
            if (result instanceof Integer) {
                return (Integer) result;
            }
        } catch (ClassNotFoundException e) {
            // API 1-4: ExifInterface doesn't exist. Fall through to default.
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "ExifInterface not available (API 1-4), assuming no rotation");
            }
        } catch (Exception e) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Unable to get orientation for image with path=" + path, e);
            }
        }
        return ORIENTATION_UNDEFINED;
    }
}
