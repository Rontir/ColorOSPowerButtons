package com.heytap.wallet;

import android.os.SystemClock;

import java.util.concurrent.atomic.AtomicLong;

/** Ensures two ColorOS routing paths cannot launch the selected target for one double press. */
final class TargetLaunchGuard {
    private static final long DUPLICATE_WINDOW_MS = 300L;
    private static final AtomicLong LAST_LAUNCH = new AtomicLong(0L);

    private TargetLaunchGuard() {}

    static boolean tryAcquire() {
        long now = SystemClock.elapsedRealtime();
        while (true) {
            long previous = LAST_LAUNCH.get();
            if (previous != 0L && now - previous < DUPLICATE_WINDOW_MS) {
                return false;
            }
            if (LAST_LAUNCH.compareAndSet(previous, now)) {
                return true;
            }
        }
    }
}
