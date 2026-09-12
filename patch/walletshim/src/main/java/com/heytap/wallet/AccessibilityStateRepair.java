package com.heytap.wallet;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

/** Restores only this app's accessibility entry while preserving all others. */
final class AccessibilityStateRepair {
    private static final String TAG = "PowerActionsRestore";

    private AccessibilityStateRepair() {}

    static boolean restoreIfAuthorized(Context context, String reason) {
        if (context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS)
                != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "WRITE_SECURE_SETTINGS missing; cannot repair after " + reason);
            return false;
        }

        ComponentName target = new ComponentName(
                context, WalletRedirectAccessibilityService.class);
        String current = Settings.Secure.getString(
                context.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (containsComponent(current, target)) {
            return true;
        }

        String flattenedTarget = target.flattenToString();
        String updated = TextUtils.isEmpty(current)
                ? flattenedTarget
                : current + ":" + flattenedTarget;
        try {
            boolean servicesWritten = Settings.Secure.putString(
                    context.getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                    updated);
            boolean accessibilityEnabled = Settings.Secure.putInt(
                    context.getContentResolver(),
                    Settings.Secure.ACCESSIBILITY_ENABLED,
                    1);
            if (servicesWritten && accessibilityEnabled) {
                Log.i(TAG, "Restored redirect after " + reason);
                return true;
            }
        } catch (SecurityException exception) {
            Log.e(TAG, "Secure settings rejected after " + reason, exception);
        } catch (RuntimeException exception) {
            Log.e(TAG, "Unable to repair redirect after " + reason, exception);
        }
        return false;
    }

    private static boolean containsComponent(String enabledServices, ComponentName target) {
        if (TextUtils.isEmpty(enabledServices)) {
            return false;
        }
        TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(':');
        splitter.setString(enabledServices);
        while (splitter.hasNext()) {
            ComponentName enabled = ComponentName.unflattenFromString(splitter.next());
            if (target.equals(enabled)) {
                return true;
            }
        }
        return false;
    }
}
