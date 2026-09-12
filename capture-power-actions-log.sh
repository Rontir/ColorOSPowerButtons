#!/usr/bin/env bash
set -euo pipefail
ADB_BIN="${ADB_BIN:-adb}"
SERIAL="${1:-}"
ADB=("$ADB_BIN")
if [[ -n "$SERIAL" ]]; then ADB=("$ADB_BIN" -s "$SERIAL"); fi
"${ADB[@]}" get-state >/dev/null 2>&1 || { echo "No ADB device." >&2; exit 1; }
echo "Clearing logcat. Now reproduce 3-5 double presses. Press Ctrl+C when done."
"${ADB[@]}" logcat -c
"${ADB[@]}" logcat -v threadtime | grep -Ei 'PowerActions|PowerActionsRedirect|PowerActionsBridge|ActivityTaskManager|ActivityManager|com\.heytap\.tas|com\.heytap\.wallet|NfcConsumeActivity'
