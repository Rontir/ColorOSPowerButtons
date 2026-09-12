package com.heytap.wallet;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

/** Stores the single double-press target in device-protected storage. */
final class LaunchTargetStore {
    private static final String PREFERENCES = "power_actions_preferences";
    private static final String LEGACY_KEY_PACKAGE = "selected_package";
    private static final String LEGACY_KEY_LABEL = "selected_label";
    private static final String V04_DOUBLE_PACKAGE = "double_package";
    private static final String V04_DOUBLE_LABEL = "double_label";
    private static final String KEY_PACKAGE = "target_package";
    private static final String KEY_LABEL = "target_label";
    private static final String DEFAULT_PACKAGE = "com.google.android.apps.walletnfcrel";

    private LaunchTargetStore() {}

    static void initializeDefault(Context context) {
        SharedPreferences prefs = preferences(context);
        if (prefs.contains(KEY_PACKAGE)) {
            return;
        }

        String migratedPackage = prefs.getString(V04_DOUBLE_PACKAGE, null);
        String migratedLabel = prefs.getString(V04_DOUBLE_LABEL, migratedPackage);
        if (migratedPackage == null || migratedPackage.isEmpty()) {
            migratedPackage = prefs.getString(LEGACY_KEY_PACKAGE, null);
            migratedLabel = prefs.getString(LEGACY_KEY_LABEL, migratedPackage);
        }

        if (migratedPackage != null && !migratedPackage.isEmpty()) {
            setTarget(context, migratedPackage, migratedLabel);
            return;
        }

        if (TargetLauncher.isPackageLaunchable(context, DEFAULT_PACKAGE)) {
            setTarget(context, DEFAULT_PACKAGE, loadLabel(context, DEFAULT_PACKAGE));
        }
    }

    static void setTarget(Context context, String packageName, String label) {
        preferences(context).edit()
                .putString(KEY_PACKAGE, packageName)
                .putString(KEY_LABEL, label)
                .apply();
    }

    static String getPackage(Context context) {
        initializeDefault(context);
        String packageName = preferences(context).getString(KEY_PACKAGE, null);
        return packageName == null || packageName.isEmpty() ? null : packageName;
    }

    static String getLabel(Context context) {
        String packageName = getPackage(context);
        if (packageName == null) {
            return null;
        }
        String installedLabel = loadLabel(context, packageName);
        if (installedLabel != null) {
            return installedLabel;
        }
        return preferences(context).getString(KEY_LABEL, packageName);
    }

    private static String loadLabel(Context context, String packageName) {
        PackageManager packageManager = context.getPackageManager();
        try {
            ApplicationInfo info = packageManager.getApplicationInfo(packageName, 0);
            CharSequence label = packageManager.getApplicationLabel(info);
            return label == null ? packageName : label.toString();
        } catch (PackageManager.NameNotFoundException | RuntimeException ignored) {
            return null;
        }
    }

    private static SharedPreferences preferences(Context context) {
        Context storageContext = context.getApplicationContext()
                .createDeviceProtectedStorageContext();
        return storageContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }
}
