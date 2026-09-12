package com.heytap.wallet;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.widget.Toast;

/**
 * Invisible one-shot foreground bridge between the ColorOS Wallet shortcut and the selected app.
 *
 * <p>When the phone is locked, the bridge asks Android to dismiss the keyguard instead of showing
 * an app-owned biometric prompt. A secure device therefore presents the normal system unlock UI
 * (fingerprint, face, PIN, pattern, etc.). The selected target is opened only after Android reports
 * that the keyguard is gone.
 */
public final class BridgeActivity extends Activity {
    static final String ACTION_OPEN = "heytap.wallet.intent.action.OPEN";
    static final String EXTRA_ACCESSIBILITY_ROUTE =
            "com.heytap.wallet.extra.ACCESSIBILITY_ROUTE";
    static final String EXTRA_RECOVERY_ROUTE =
            "com.heytap.wallet.extra.RECOVERY_ROUTE";

    private static final String TAG = "PowerActionsBridge";
    private static final long DIRECT_PACKAGE_SETTLE_MS = 120L;
    private static final long UNLOCK_POLL_MS = 100L;
    private static final long UNLOCK_TIMEOUT_MS = 60_000L;
    private static final long POST_UNLOCK_SETTLE_MS = 90L;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable unlockPoll = this::pollForUnlock;

    private KeyguardManager keyguardManager;
    private boolean routeAccepted;
    private boolean keyguardFlowActive;
    private boolean dismissRequestSent;
    private boolean skipDuplicateGuardForRoute;
    private boolean launchStarted;
    private long unlockStartedAt;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setShowWhenLocked(true);
        setTurnScreenOn(true);
        keyguardManager = getSystemService(KeyguardManager.class);
        LaunchTargetStore.initializeDefault(this);
        AccessibilityStateRepair.restoreIfAuthorized(this, "bridge launch");
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        resetTransientState();
        handleIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (keyguardFlowActive) {
            if (!isDeviceLocked()) {
                completeSystemUnlock();
            } else {
                requestSystemUnlockIfNeeded();
            }
        }
    }

    private void handleIntent(Intent intent) {
        boolean accessibilityRoute = intent != null
                && intent.getBooleanExtra(EXTRA_ACCESSIBILITY_ROUTE, false);
        boolean recoveryRoute = intent != null
                && intent.getBooleanExtra(EXTRA_RECOVERY_ROUTE, false);

        if (recoveryRoute) {
            beginRoute(true);
        } else if (accessibilityRoute) {
            beginRoute(false);
        } else {
            getWindow().getDecorView().postDelayed(
                    () -> beginRoute(false),
                    DIRECT_PACKAGE_SETTLE_MS);
        }
    }

    private void beginRoute(boolean skipDuplicateGuard) {
        if (launchStarted || isFinishing()) {
            return;
        }

        skipDuplicateGuardForRoute = skipDuplicateGuard;

        if (isDeviceLocked()) {
            beginSystemUnlock();
            return;
        }

        if (!routeAccepted) {
            if (!skipDuplicateGuard && !TargetLaunchGuard.tryAcquire()) {
                Log.i(TAG, "Duplicate bridge route suppressed");
                finishBridge();
                return;
            }
            routeAccepted = true;
        }

        launchTargetAndDisappear();
    }

    private void beginSystemUnlock() {
        if (keyguardFlowActive || isFinishing()) {
            return;
        }
        keyguardFlowActive = true;
        dismissRequestSent = false;
        unlockStartedAt = SystemClock.elapsedRealtime();
        Log.i(TAG, "Device locked; requesting normal Android keyguard dismissal");

        getWindow().getDecorView().post(this::requestSystemUnlockIfNeeded);
        mainHandler.removeCallbacks(unlockPoll);
        mainHandler.postDelayed(unlockPoll, UNLOCK_POLL_MS);
    }

    private void requestSystemUnlockIfNeeded() {
        if (!keyguardFlowActive || dismissRequestSent || isFinishing()) {
            return;
        }
        if (!isDeviceLocked()) {
            completeSystemUnlock();
            return;
        }
        if (keyguardManager == null) {
            Log.w(TAG, "KeyguardManager unavailable; waiting for manual unlock");
            return;
        }

        dismissRequestSent = true;
        try {
            keyguardManager.requestDismissKeyguard(
                    this,
                    new KeyguardManager.KeyguardDismissCallback() {
                        @Override
                        public void onDismissSucceeded() {
                            Log.i(TAG, "System keyguard dismissal succeeded");
                            completeSystemUnlock();
                        }

                        @Override
                        public void onDismissCancelled() {
                            Log.i(TAG, "System keyguard dismissal cancelled");
                            keyguardFlowActive = false;
                            mainHandler.removeCallbacks(unlockPoll);
                            finishBridge();
                        }

                        @Override
                        public void onDismissError() {
                            Log.w(TAG, "System keyguard dismissal reported an error; waiting for normal unlock");
                        }
                    });
        } catch (RuntimeException exception) {
            Log.w(TAG, "Unable to request keyguard dismissal; waiting for normal unlock", exception);
        }
    }

    private void pollForUnlock() {
        if (!keyguardFlowActive || launchStarted || isFinishing()) {
            return;
        }

        long elapsed = SystemClock.elapsedRealtime() - unlockStartedAt;
        if (elapsed >= UNLOCK_TIMEOUT_MS) {
            Log.i(TAG, "System unlock wait timed out");
            keyguardFlowActive = false;
            finishBridge();
            return;
        }

        if (!isDeviceLocked()) {
            completeSystemUnlock();
            return;
        }

        mainHandler.postDelayed(unlockPoll, UNLOCK_POLL_MS);
    }

    private void completeSystemUnlock() {
        if (!keyguardFlowActive || launchStarted || isFinishing()) {
            return;
        }
        keyguardFlowActive = false;
        mainHandler.removeCallbacks(unlockPoll);
        Log.i(TAG, "Normal Android unlock completed; opening selected target");
        mainHandler.postDelayed(
                () -> beginRoute(skipDuplicateGuardForRoute),
                POST_UNLOCK_SETTLE_MS);
    }

    private boolean isDeviceLocked() {
        return keyguardManager != null
                && (keyguardManager.isDeviceLocked() || keyguardManager.isKeyguardLocked());
    }

    private void launchTargetAndDisappear() {
        if (launchStarted || isFinishing()) {
            return;
        }
        if (isDeviceLocked()) {
            beginSystemUnlock();
            return;
        }

        keyguardFlowActive = false;
        mainHandler.removeCallbacks(unlockPoll);
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
        mainHandler.removeCallbacks(unlockPoll);
        routeAccepted = false;
        keyguardFlowActive = false;
        dismissRequestSent = false;
        skipDuplicateGuardForRoute = false;
        launchStarted = false;
        unlockStartedAt = 0L;
    }

    private void finishBridge() {
        overridePendingTransition(0, 0);
        finish();
    }

    @Override
    protected void onDestroy() {
        mainHandler.removeCallbacksAndMessages(null);
        resetTransientState();
        super.onDestroy();
    }
}
