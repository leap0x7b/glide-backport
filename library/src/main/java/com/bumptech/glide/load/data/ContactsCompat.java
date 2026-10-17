package com.bumptech.glide.load.data;

import android.content.ContentResolver;
import android.net.Uri;
import android.util.Log;

import java.io.InputStream;

/**
 * Contacts access via reflection so {@link StreamLocalUriFetcher} can load on API 1-4 where
 * {@code android.provider.ContactsContract} (added in API 5) does not exist.
 */
final class ContactsCompat {
    private static final String TAG = "ContactsCompat";
    private static final String CONTACTS_CONTRACT = "android.provider.ContactsContract";
    private static final String DEFAULT_AUTHORITY = "com.android.contacts";

    private ContactsCompat() {
    }

    static String getAuthority() {
        try {
            Class<?> clazz = Class.forName(CONTACTS_CONTRACT);
            return (String) clazz.getField("AUTHORITY").get(null);
        } catch (Exception e) {
            // API 1-4: no ContactsContract. Return modern authority so the matcher simply never
            // matches and callers fall back to openInputStream().
            return DEFAULT_AUTHORITY;
        }
    }

    static Uri lookupContact(ContentResolver resolver, Uri uri) {
        try {
            Class<?> contacts = Class.forName(CONTACTS_CONTRACT + "$Contacts");
            java.lang.reflect.Method m =
                    contacts.getMethod("lookupContact", ContentResolver.class, Uri.class);
            return (Uri) m.invoke(null, resolver, uri);
        } catch (ClassNotFoundException e) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "ContactsContract not available (API 1-4)");
            }
            return null;
        } catch (Exception e) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "lookupContact failed", e);
            }
            return null;
        }
    }

    static InputStream openContactPhotoInputStream(ContentResolver resolver, Uri contactUri,
            boolean preferHighres) {
        try {
            Class<?> contacts = Class.forName(CONTACTS_CONTRACT + "$Contacts");
            if (preferHighres) {
                try {
                    java.lang.reflect.Method m = contacts.getMethod("openContactPhotoInputStream",
                            ContentResolver.class, Uri.class, boolean.class);
                    return (InputStream) m.invoke(null, resolver, contactUri, true);
                } catch (NoSuchMethodException e) {
                    // Pre-ICS: fall through to 2-arg version.
                }
            }
            java.lang.reflect.Method m = contacts.getMethod("openContactPhotoInputStream",
                    ContentResolver.class, Uri.class);
            return (InputStream) m.invoke(null, resolver, contactUri);
        } catch (ClassNotFoundException e) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "ContactsContract not available (API 1-4)");
            }
            return null;
        } catch (Exception e) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "openContactPhotoInputStream failed", e);
            }
            return null;
        }
    }
}
