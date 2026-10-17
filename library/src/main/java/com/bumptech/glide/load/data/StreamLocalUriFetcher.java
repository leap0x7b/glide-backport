package com.bumptech.glide.load.data;

import android.annotation.TargetApi;
import android.content.ContentResolver;
import android.content.Context;
import android.content.UriMatcher;
import android.net.Uri;
import android.os.Build;

import com.bumptech.glide.util.ApiCompat;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

/**
 * Fetches an {@link java.io.InputStream} for a local {@link android.net.Uri}.
 */
public class StreamLocalUriFetcher extends LocalUriFetcher<InputStream> {
    /** A lookup uri (e.g. content://com.android.contacts/contacts/lookup/3570i61d948d30808e537) */
    private static final int ID_LOOKUP = 1;
    /** A contact thumbnail uri (e.g. content://com.android.contacts/contacts/38/photo) */
    private static final int ID_THUMBNAIL = 2;
    /** A contact uri (e.g. content://com.android.contacts/contacts/38) */
    private static final int ID_CONTACT = 3;
    /**
     * A contact display photo (high resolution) uri
     * (e.g. content://com.android.contacts/5/display_photo)
     */
    private static final int ID_DISPLAY_PHOTO = 4;


    private static volatile UriMatcher uriMatcher;

    private static UriMatcher getUriMatcher() {
        // Lazy + reflection: ContactsContract (API 5+) doesn't exist on API 1-4. Building the
        // matcher eagerly in <clinit> would throw NoClassDefFoundError on those devices.
        if (uriMatcher == null) {
            synchronized (StreamLocalUriFetcher.class) {
                if (uriMatcher == null) {
                    String authority = ContactsCompat.getAuthority();
                    UriMatcher matcher = new UriMatcher(UriMatcher.NO_MATCH);
                    matcher.addURI(authority, "contacts/lookup/*/#", ID_LOOKUP);
                    matcher.addURI(authority, "contacts/lookup/*", ID_LOOKUP);
                    matcher.addURI(authority, "contacts/#/photo", ID_THUMBNAIL);
                    matcher.addURI(authority, "contacts/#", ID_CONTACT);
                    matcher.addURI(authority, "contacts/#/display_photo", ID_DISPLAY_PHOTO);
                    uriMatcher = matcher;
                }
            }
        }
        return uriMatcher;
    }

    public StreamLocalUriFetcher(Context context, Uri uri) {
        super(context, uri);
    }

    @Override
    protected InputStream loadResource(Uri uri, ContentResolver contentResolver) throws FileNotFoundException {
        final int matchedUri = getUriMatcher().match(uri);
        return loadResourceFromUri(uri, contentResolver, matchedUri);
    }

    @Override
    protected void close(InputStream data) throws IOException {
        data.close();
    }

    private InputStream loadResourceFromUri(Uri uri, ContentResolver contentResolver, int matchedUri)
            throws FileNotFoundException {
        switch (matchedUri) {
            case ID_LOOKUP:
            case ID_CONTACT:
                // If it was a Lookup uri then resolve it first, then continue loading the contact uri.
                if (matchedUri == ID_LOOKUP) {
                    uri = ContactsCompat.lookupContact(contentResolver, uri);
                    if (uri == null) {
                        throw new FileNotFoundException("Contact cannot be found");
                    }
                }
                InputStream photo = openContactPhotoInputStream(contentResolver, uri);
                // On API 1-4 ContactsContract doesn't exist; fall back to a generic stream.
                if (photo != null) {
                    return photo;
                }
                return contentResolver.openInputStream(uri);
            case ID_THUMBNAIL:
            case ID_DISPLAY_PHOTO:
            case UriMatcher.NO_MATCH:
            default:
                return contentResolver.openInputStream(uri);
        }
    }

    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    private InputStream openContactPhotoInputStream(ContentResolver contentResolver, Uri contactUri) {
        // openContactPhotoInputStream(uri, true) requires API 14; 2-arg version requires API 5.
        // ContactsCompat returns null on API 1-4 so callers can fall back to openInputStream().
        boolean preferHighres = ApiCompat.isAtLeast(ApiCompat.ICE_CREAM_SANDWICH);
        return ContactsCompat.openContactPhotoInputStream(contentResolver, contactUri, preferHighres);
    }
}
