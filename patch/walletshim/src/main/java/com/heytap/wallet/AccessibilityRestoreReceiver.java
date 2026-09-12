package com.heytap.wallet;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/** Restores only this app's scoped Accessibility redirect after reboot or package update. */
public final class AccessibilityRestoreReceiver extends BroadcastReceiver {
    private static final String TAG = "PowerActionsRestore";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent == null ? null : intent.getAction();
        if (!Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action)
                && !Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !Intent.ACTION_USER_UNLOCKED.equals(action)
                && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            return;
        }

        LaunchTargetStore.initializeDefault(context);
        AccessibilityStateRepair.restoreIfAuthorized(context, action);
        Log.i(TAG, "Restore check completed after " + action);
    }
}
