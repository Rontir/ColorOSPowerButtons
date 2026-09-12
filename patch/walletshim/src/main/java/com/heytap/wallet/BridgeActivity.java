package com.heytap.wallet;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Intent;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricPrompt;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.util.Log;
import android.widget.Toast;

/**
 * Invisible one-shot foreground bridge between the ColorOS Wallet shortcut and the selected app.
 *
 * <p>The Accessibility route opens this Activity only after the OEM Wallet has been dismissed.
 * Firmware that launches {@code com.heytap.wallet} directly can also reach this Activity through
 * CATEGORY_INFO. The actual target is always started from this Activity, not from a background
 * service/PendingIntent.
 */
public final class BridgeActivity extends Activity {
    static final String ACTION_OPEN = "heytap.wallet.intent.action.OPEN";
    static final String EXTRA_ACCESSIBILITY_ROUTE =
            "com.heytap.wallet.extra.ACCESSIBILITY_ROUTE";
    static final String EXTRA_RECOVERY_ROUTE =
            "com.heytap.wallet.extra.RECOVERY_ROUTE";

    private static final String TAG = "PowerActionsBridge";
    private static final long DIRECT_PACKAGE_SETTLE_MS = 120L;

    private CancellationSignal cancellationSignal;
    private boolean authenticationStarted;
    private boolean launchStarted;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setShowWhenLocked(true);
        setTurnScreenOn(true);
        LaunchTargetStore.initializeDefault(this);
        AccessibilityStateRepair.restoreIfAuthorized(this, "bridge launch");

        boolean accessibilityRoute = getIntent() != null
                && getIntent().getBooleanExtra(EXTRA_ACCESSIBILITY_ROUTE, false);
        boolean recoveryRoute = getIntent() != null
                && getIntent().getBooleanExtra(EXTRA_RECOVERY_ROUTE, false);

        if (recoveryRoute) {
            openTargetRespectingDeviceLock(true);
        } else if (accessibilityRoute) {
            openTargetRespectingDeviceLock(false);
        } else {
            getWindow().getDecorView().postDelayed(
                    () -> openTargetRespectingDeviceLock(false),
                    DIRECT_PACKAGE_SETTLE_MS);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        resetTransientState();
        boolean recoveryRoute = intent != null
                && intent.getBooleanExtra(EXTRA_RECOVERY_ROUTE, false);
        getWindow().getDecorView().post(() -> openTargetRespectingDeviceLock(recoveryRoute));
    }

    private void openTargetRespectingDeviceLock(boolean skipDuplicateGuard) {
        if (launchStarted || authenticationStarted || isFinishing()) {
            return;
        }

        if (!skipDuplicateGuard && !TargetLaunchGuard.tryAcquire()) {
            Log.i(TAG, "Duplicate bridge route suppressed");
            finishBridge();
            return;
        }

        KeyguardManager keyguardManager = getSystemService(KeyguardManager.class);
        if (keyguardManager != null && !keyguardManager.isDeviceLocked()) {
            launchTargetAndDisappear();
            return;
        }
        authenticateThenOpenTarget();
    }

    private void authenticateThenOpenTarget() {
        if (authenticationStarted || launchStarted || isFinishing()) {
            return;
        }
        authenticationStarted = true;
        try {
            BiometricPrompt prompt = new BiometricPrompt.Builder(this)
                    .setTitle(getString(R.string.authentication_title))
                    .setDescription(getString(R.string.authentication_description))
                    .setConfirmationRequired(false)
                    .setAllowedAuthenticators(
                            BiometricManager.Authenticators.BIOMETRIC_STRONG
                                    | BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                    .build();
            cancellationSignal = new CancellationSignal();
            prompt.authenticate(
                    cancellationSignal,
                    getMainExecutor(),
                    new BiometricPrompt.AuthenticationCallback() {
                        @Override
                        public void onAuthenticationSucceeded(
                                BiometricPrompt.AuthenticationResult result) {
                            authenticationStarted = false;
                            launchTargetAndDisappear();
                        }

                        @Override
                        public void onAuthenticationError(int errorCode, CharSequence errString) {
                            authenticationStarted = false;
                            Log.i(TAG, "Authentication ended: code=" + errorCode);
                            finishBridge();
                        }
                    });
        } catch (RuntimeException exception) {
            authenticationStarted = false;
            Log.e(TAG, "Unable to start system authentication", exception);
            Toast.makeText(this, R.string.authentication_unavailable, Toast.LENGTH_LONG).show();
            finishBridge();
        }
    }

    private void launchTargetAndDisappear() {
        if (launchStarted || isFinishing()) {
            return;
        }
        launchStarted = true;

        if (TargetLauncher.launchTarget(this)) {
            Log.i(TAG, "Selected target launched from foreground bridge");
            overridePendingTransition(0, 0);
            finish();
        } else {
            Log.e(TAG, "Selected target launch failed");
            Toast.makeText(this, R.string.target_launch_failed, Toast.LENGTH_LONG).show();
            finishBridge();
        }
    }

    private void resetTransientState() {
        if (cancellationSignal != null && !cancellationSignal.isCanceled()) {
            cancellationSignal.cancel();
        }
        cancellationSignal = null;
        authenticationStarted = false;
        launchStarted = false;
    }

    private void finishBridge() {
        overridePendingTransition(0, 0);
        finish();
    }

    @Override
    protected void onDestroy() {
        resetTransientState();
        super.onDestroy();
    }
}
