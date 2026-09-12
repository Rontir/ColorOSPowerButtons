package com.heytap.wallet;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.util.Log;

/** Creates and opens the configured double-press target from a foreground bridge Activity. */
final class TargetLauncher {
    private static final String TAG = "PowerActions";
    private static final String GOOGLE_WALLET_PACKAGE =
            "com.google.android.apps.walletnfcrel";
    private static final String GOOGLE_WALLET_QUICKDRAW_ACTION =
            "com.google.android.apps.wallet.main.QUICKDRAW";

    private TargetLauncher() {}

    static Intent createTargetIntent(Context context) {
        String packageName = LaunchTargetStore.getPackage(context);
        if (packageName == null) {
            return null;
        }

        if (GOOGLE_WALLET_PACKAGE.equals(packageName)) {
            Intent quickDraw = new Intent(GOOGLE_WALLET_QUICKDRAW_ACTION)
                    .setPackage(GOOGLE_WALLET_PACKAGE)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            try {
                if (context.getPackageManager().resolveActivity(quickDraw, 0) != null) {
                    return quickDraw;
                }
            } catch (RuntimeException exception) {
                Log.i(TAG, "Google Wallet QUICKDRAW unavailable; using launcher fallback");
            }
        }

        return createLauncherIntent(context, packageName);
    }

    static boolean launchTarget(Context context) {
        Intent intent = createTargetIntent(context);
        if (intent == null) {
            return false;
        }
        try {
            context.startActivity(intent);
            return true;
        } catch (ActivityNotFoundException | SecurityException exception) {
            Log.w(TAG, "Selected target rejected the launch", exception);
            return false;
        } catch (RuntimeException exception) {
            Log.e(TAG, "Unable to open selected target", exception);
            return false;
        }
    }

    static boolean isPackageLaunchable(Context context, String packageName) {
        if (GOOGLE_WALLET_PACKAGE.equals(packageName)) {
            return createTargetIntent(context) != null;
        }
        return createLauncherIntent(context, packageName) != null;
    }

    private static Intent createLauncherIntent(Context context, String packageName) {
        try {
            PackageManager packageManager = context.getPackageManager();
            Intent intent = packageManager.getLaunchIntentForPackage(packageName);
            if (intent == null) {
                return null;
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
            return intent;
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
