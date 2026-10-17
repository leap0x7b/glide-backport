package com.bumptech.glide.manager;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.ContextWrapper;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.util.Log;

import com.bumptech.glide.RequestManager;
import com.bumptech.glide.util.ApiCompat;
import com.bumptech.glide.util.Util;

import java.util.HashMap;
import java.util.Map;

/**
 * A collection of static methods for creating new {@link com.bumptech.glide.RequestManager}s or retrieving existing
 * ones from activities and fragment.
 */
public class RequestManagerRetriever implements Handler.Callback {
    private static final String TAG = "RMRetriever";
    static final String FRAGMENT_TAG = "com.bumptech.glide.manager";

    /** The singleton instance of RequestManagerRetriever. */
    private static final RequestManagerRetriever INSTANCE = new RequestManagerRetriever();

    private static final int ID_REMOVE_FRAGMENT_MANAGER = 1;
    private static final int ID_REMOVE_SUPPORT_FRAGMENT_MANAGER = 2;

    private static final int SUPPORT_LIBRARY_UNKNOWN = 0;
    private static final int SUPPORT_LIBRARY_AVAILABLE = 1;
    private static final int SUPPORT_LIBRARY_UNAVAILABLE = 2;

    // Tracks whether the support library is on the runtime classpath so we can skip checks that
    // would otherwise throw NoClassDefFoundError on API 1-3 or in apps that don't include it.
    private static volatile int supportLibraryAvailability = SUPPORT_LIBRARY_UNKNOWN;

    /** The top application level RequestManager. */
    private volatile RequestManager applicationManager;

    // Visible for testing.
    /** Pending adds for RequestManagerFragments. */
    final Map<android.app.FragmentManager, RequestManagerFragment> pendingRequestManagerFragments =
            new HashMap<android.app.FragmentManager, RequestManagerFragment>();

    // Visible for testing.
    /** Pending adds for SupportRequestManagerFragments. */
    final Map<FragmentManager, SupportRequestManagerFragment> pendingSupportRequestManagerFragments =
            new HashMap<FragmentManager, SupportRequestManagerFragment>();

    /** Main thread handler to handle cleaning up pending fragment maps. */
    private final Handler handler;

    /**
     * Retrieves and returns the RequestManagerRetriever singleton.
     */
    public static RequestManagerRetriever get() {
        return INSTANCE;
    }

    // Visible for testing.
    RequestManagerRetriever() {
        handler = new Handler(Looper.getMainLooper(), this /* Callback */);
    }

    private RequestManager getApplicationManager(Context context) {
        // Either an application context or we're on a background thread.
        if (applicationManager == null) {
            synchronized (this) {
                if (applicationManager == null) {
                    // Normally pause/resume is taken care of by the fragment we add to the fragment or activity.
                    // However, in this case since the manager attached to the application will not receive lifecycle
                    // events, we must force the manager to start resumed using ApplicationLifecycle.
                    applicationManager = new RequestManager(context.getApplicationContext(),
                            new ApplicationLifecycle(), new EmptyRequestManagerTreeNode());
                }
            }
        }

        return applicationManager;
    }

    public RequestManager get(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("You cannot start a load on a null Context");
        } else if (Util.isOnMainThread() && !(context instanceof Application)) {
            // instanceof FragmentActivity resolves support-library classes. When support-v4 is not
            // on the runtime classpath (API 1-3, or apps that don't use the support library) the
            // check would throw NoClassDefFoundError, so look the class up first and fall through
            // to the native Activity path instead of dropping to the application manager.
            try {
                if (isSupportLibraryAvailable() && context instanceof FragmentActivity) {
                    return get((FragmentActivity) context);
                } else if (context instanceof Activity) {
                    return get((Activity) context);
                } else if (context instanceof ContextWrapper) {
                    return get(((ContextWrapper) context).getBaseContext());
                }
            } catch (LinkageError e) {
                Log.w(TAG, "Support library not available, falling back to application manager", e);
            }
        }

        return getApplicationManager(context);
    }

    private static boolean isSupportLibraryAvailable() {
        if (supportLibraryAvailability == SUPPORT_LIBRARY_UNKNOWN) {
            try {
                Class.forName("android.support.v4.app.FragmentActivity");
                supportLibraryAvailability = SUPPORT_LIBRARY_AVAILABLE;
            } catch (ClassNotFoundException e) {
                supportLibraryAvailability = SUPPORT_LIBRARY_UNAVAILABLE;
            } catch (LinkageError e) {
                // Partially available or broken support library, treat it as unavailable.
                supportLibraryAvailability = SUPPORT_LIBRARY_UNAVAILABLE;
            }
        }
        return supportLibraryAvailability == SUPPORT_LIBRARY_AVAILABLE;
    }

    public RequestManager get(FragmentActivity activity) {
        if (Util.isOnBackgroundThread()) {
            return get(activity.getApplicationContext());
        } else {
            // Support fragments require the support library (min API 4). On API 1-3 fall back to
            // the application manager rather than crashing with VerifyError/NoClassDefFoundError.
            //
            // Casting to Activity is required for Dalvik: when support-v4 is not on the runtime
            // classpath, FragmentActivity cannot be resolved, its type degrades to
            // java.lang.Object, and passing that value to methods that take resolved types such
            // as Activity/Context makes the verifier hard-reject the class (VerifyError). With
            // the cast the verifier sees a plain Activity; the support-fragment calls that
            // follow are soft-replaced with throwing opcodes when support-v4 is missing, and the
            // LinkageError catch below is never reached because this method itself can only be
            // called when the caller has support-v4 types available.
            //
            // Note the intermediate cast to Object: javac elides a direct (Activity) cast
            // because FragmentActivity is a subtype of Activity at compile time, which would
            // leave no check-cast instruction in the bytecode.
            Activity baseActivity = (Activity) (Object) activity;
            try {
                assertNotDestroyed(baseActivity);
                FragmentManager fm = activity.getSupportFragmentManager();
                return supportFragmentGet(baseActivity, fm);
            } catch (LinkageError e) {
                Log.w(TAG, "Support fragments not available on this device, falling back to application manager", e);
                return getApplicationManager(baseActivity);
            }
        }
    }

    public RequestManager get(Fragment fragment) {
        if (fragment.getActivity() == null) {
            throw new IllegalArgumentException("You cannot start a load on a fragment before it is attached");
        }
        if (Util.isOnBackgroundThread()) {
            return get(fragment.getActivity().getApplicationContext());
        } else {
            try {
                FragmentManager fm = fragment.getChildFragmentManager();
                return supportFragmentGet(fragment.getActivity(), fm);
            } catch (LinkageError e) {
                Log.w(TAG, "Support fragments not available, falling back to application manager", e);
                return getApplicationManager(fragment.getActivity());
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public RequestManager get(Activity activity) {
        // Native fragments require API 11. On API 1-10 fall back to the application manager.
        // ApiCompat avoids NoSuchFieldError on API 1-3 where Build.VERSION.SDK_INT doesn't exist.
        if (Util.isOnBackgroundThread() || !ApiCompat.isAtLeast(ApiCompat.HONEYCOMB)) {
            return get(activity.getApplicationContext());
        } else {
            try {
                assertNotDestroyed(activity);
                android.app.FragmentManager fm = activity.getFragmentManager();
                return fragmentGet(activity, fm);
            } catch (LinkageError e) {
                Log.w(TAG, "Native fragments not available, falling back to application manager", e);
                return getApplicationManager(activity);
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    private static void assertNotDestroyed(Activity activity) {
        // Activity.isDestroyed() requires API 17. No-op on API 1-16. Guard with try/catch for
        // old runtimes where the method doesn't exist.
        if (ApiCompat.isAtLeast(ApiCompat.JELLY_BEAN_MR1)) {
            try {
                if (activity.isDestroyed()) {
                    throw new IllegalArgumentException("You cannot start a load for a destroyed activity");
                }
            } catch (NoSuchMethodError e) {
                // API 1-16: isDestroyed() doesn't exist, ignore.
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    public RequestManager get(android.app.Fragment fragment) {
        if (fragment.getActivity() == null) {
            throw new IllegalArgumentException("You cannot start a load on a fragment before it is attached");
        }
        // Child FragmentManager requires API 17. Fall back to application manager on API 1-16.
        if (Util.isOnBackgroundThread() || !ApiCompat.isAtLeast(ApiCompat.JELLY_BEAN_MR1)) {
            return get(fragment.getActivity().getApplicationContext());
        } else {
            try {
                android.app.FragmentManager fm = fragment.getChildFragmentManager();
                return fragmentGet(fragment.getActivity(), fm);
            } catch (LinkageError e) {
                Log.w(TAG, "Native child fragments not available, falling back to application manager", e);
                return getApplicationManager(fragment.getActivity());
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    RequestManagerFragment getRequestManagerFragment(final android.app.FragmentManager fm) {
        RequestManagerFragment current = (RequestManagerFragment) fm.findFragmentByTag(FRAGMENT_TAG);
        if (current == null) {
            current = pendingRequestManagerFragments.get(fm);
            if (current == null) {
                current = new RequestManagerFragment();
                pendingRequestManagerFragments.put(fm, current);
                fm.beginTransaction().add(current, FRAGMENT_TAG).commitAllowingStateLoss();
                handler.obtainMessage(ID_REMOVE_FRAGMENT_MANAGER, fm).sendToTarget();
            }
        }
        return current;
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    RequestManager fragmentGet(Context context, android.app.FragmentManager fm) {
        RequestManagerFragment current = getRequestManagerFragment(fm);
        RequestManager requestManager = current.getRequestManager();
        if (requestManager == null) {
            requestManager = new RequestManager(context,
                    current.getGlideLifecycle(), current.getRequestManagerTreeNode());
            current.setRequestManager(requestManager);
        }
        return requestManager;
    }

    SupportRequestManagerFragment getSupportRequestManagerFragment(final FragmentManager fm) {
        SupportRequestManagerFragment current = (SupportRequestManagerFragment) fm.findFragmentByTag(


            FRAGMENT_TAG);
        if (current == null) {
            current = pendingSupportRequestManagerFragments.get(fm);
            if (current == null) {
                current = new SupportRequestManagerFragment();
                pendingSupportRequestManagerFragments.put(fm, current);
                fm.beginTransaction().add(current, FRAGMENT_TAG).commitAllowingStateLoss();
                handler.obtainMessage(ID_REMOVE_SUPPORT_FRAGMENT_MANAGER, fm).sendToTarget();
            }
        }
        return current;
    }

    RequestManager supportFragmentGet(Context context, FragmentManager fm) {
        SupportRequestManagerFragment current = getSupportRequestManagerFragment(fm);
        RequestManager requestManager = current.getRequestManager();
        if (requestManager == null) {
            requestManager = new RequestManager(context,
                    current.getGlideLifecycle(), current.getRequestManagerTreeNode());
            current.setRequestManager(requestManager);
        }
        return requestManager;
    }

    @Override
    public boolean handleMessage(Message message) {
        boolean handled = true;
        Object removed = null;
        Object key = null;
        switch (message.what) {
            case ID_REMOVE_FRAGMENT_MANAGER:
                android.app.FragmentManager fm = (android.app.FragmentManager) message.obj;
                key = fm;
                removed = pendingRequestManagerFragments.remove(fm);
                break;
            case ID_REMOVE_SUPPORT_FRAGMENT_MANAGER:
                FragmentManager supportFm = (FragmentManager) message.obj;
                key = supportFm;
                removed = pendingSupportRequestManagerFragments.remove(supportFm);
                break;
            default:
                handled = false;
        }
        if (handled && removed == null && Log.isLoggable(TAG, Log.WARN)) {
            Log.w(TAG, "Failed to remove expected request manager fragment, manager: " + key);
        }
        return handled;
    }
}
