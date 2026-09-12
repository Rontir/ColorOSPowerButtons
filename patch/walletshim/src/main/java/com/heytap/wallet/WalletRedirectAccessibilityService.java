package com.heytap.wallet;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

/** Redirects only the exact ColorOS Wallet quick-launch activity. */
public final class WalletRedirectAccessibilityService extends AccessibilityService {
    private static final String TAG = "PowerActionsRedirect";
    private static final String OPPO_WALLET_PACKAGE = "com.heytap.tas";
    private static final String OPPO_QUICK_LAUNCH_ACTIVITY =
            "com.nearme.wallet.nfc.ui.NfcConsumeActivity";

    private static final long BRIDGE_DELAY_MS = 85L;
    private static final long INITIAL_EVENT_QUIET_MS = 350L;
    private static final long REASSERT_PROTECTION_MS = 1_800L;
    private static final long REASSERT_DEBOUNCE_MS = 400L;
    private static final long REASSERT_BRIDGE_DELAY_MS = 75L;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable initialBridge = () -> launchOneShotBridge(false);
    private final Runnable recoveryBridge = () -> launchOneShotBridge(true);

    private long cycleStartedAt;
    private long protectionUntil;
    private long recoveryDebounceUntil;

    @Override
    public void onCreate() {
        super.onCreate();
        LaunchTargetStore.initializeDefault(this);
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        Log.i(TAG, "Scoped ColorOS Wallet redirect enabled");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return;
        }
        if (!TextUtils.equals(OPPO_WALLET_PACKAGE, event.getPackageName())
                || !TextUtils.equals(OPPO_QUICK_LAUNCH_ACTIVITY, event.getClassName())) {
            return;
        }

        long now = SystemClock.elapsedRealtime();
        if (now < protectionUntil) {
            long cycleAge = now - cycleStartedAt;
            if (cycleAge < INITIAL_EVENT_QUIET_MS) {
                Log.i(TAG, "Duplicate callback from initial Wallet window ignored");
                return;
            }
            if (now >= recoveryDebounceUntil) {
                recoveryDebounceUntil = now + REASSERT_DEBOUNCE_MS;
                boolean dismissed = performGlobalAction(GLOBAL_ACTION_BACK);
                mainHandler.removeCallbacks(recoveryBridge);
                mainHandler.postDelayed(recoveryBridge, REASSERT_BRIDGE_DELAY_MS);
                Log.i(TAG, "Late ColorOS Wallet reassertion detected; recoveryBack="
                        + dismissed + "; target restore scheduled");
            } else {
                Log.i(TAG, "Duplicate callback from late Wallet reassertion ignored");
            }
            return;
        }

        startNewRedirectCycle(now);
    }

    private void startNewRedirectCycle(long now) {
        cycleStartedAt = now;
        protectionUntil = now + REASSERT_PROTECTION_MS;
        recoveryDebounceUntil = 0L;

        boolean dismissed = performGlobalAction(GLOBAL_ACTION_BACK);
        Log.i(TAG, "ColorOS Wallet quick launch detected; immediateBack=" + dismissed);

        mainHandler.removeCallbacks(initialBridge);
        mainHandler.removeCallbacks(recoveryBridge);
        mainHandler.postDelayed(initialBridge, BRIDGE_DELAY_MS);
    }

    private void launchOneShotBridge(boolean recovery) {
        Intent intent = new Intent(this, BridgeActivity.class)
                .setAction(BridgeActivity.ACTION_OPEN)
                .putExtra(BridgeActivity.EXTRA_ACCESSIBILITY_ROUTE, true)
                .putExtra(BridgeActivity.EXTRA_RECOVERY_ROUTE, recovery)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        try {
            startActivity(intent);
            Log.i(TAG, recovery
                    ? "Recovery bridge started over late Wallet reassertion"
                    : "One-shot bridge started after Wallet dismissal");
        } catch (RuntimeException exception) {
            Log.e(TAG, recovery ? "Unable to start recovery bridge" : "Unable to start one-shot bridge", exception);
        }
    }

    @Override
    public void onInterrupt() {
        Log.i(TAG, "ColorOS Wallet redirect interrupted");
    }

    @Override
    public void onDestroy() {
        mainHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
