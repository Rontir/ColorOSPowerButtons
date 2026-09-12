#!/usr/bin/env bash
set -euo pipefail

BASE_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
APK="${1:-$BASE_DIR/ColorOS-Power-Actions-v1.0.6-debug.apk}"
SERIAL="${2:-}"
PKG="com.heytap.wallet"
SERVICE="com.heytap.wallet/com.heytap.wallet.WalletRedirectAccessibilityService"

[[ -f "$APK" ]] || { echo "APK not found: $APK" >&2; exit 1; }
command -v adb >/dev/null 2>&1 || { echo "adb not found" >&2; exit 1; }

ADB=(adb)
[[ -n "$SERIAL" ]] && ADB=(adb -s "$SERIAL")

"${ADB[@]}" shell am stopservice -n "$PKG/.TriplePressMonitorService" >/dev/null 2>&1 || true
"${ADB[@]}" shell am force-stop "$PKG" >/dev/null 2>&1 || true
"${ADB[@]}" uninstall com.nielk74.colorospowerlauncher >/dev/null 2>&1 || true
"${ADB[@]}" uninstall com.antoine.chatgptpower >/dev/null 2>&1 || true

if ! "${ADB[@]}" install --no-streaming -r -g -t "$APK"; then
  "${ADB[@]}" uninstall "$PKG" >/dev/null 2>&1 || true
  "${ADB[@]}" install --no-streaming -r -g -t "$APK"
fi

"${ADB[@]}" shell appops set "$PKG" SYSTEM_ALERT_WINDOW default >/dev/null 2>&1 || true
"${ADB[@]}" shell pm grant "$PKG" android.permission.WRITE_SECURE_SETTINGS || true
"${ADB[@]}" shell settings put secure double_tap_power_button_value 1

CURRENT="$("${ADB[@]}" shell settings get secure enabled_accessibility_services 2>/dev/null | tr -d '\r')"
[[ "$CURRENT" == "null" ]] && CURRENT=""
if [[ ":$CURRENT:" != *":$SERVICE:"* ]]; then
  [[ -n "$CURRENT" ]] && CURRENT="$CURRENT:$SERVICE" || CURRENT="$SERVICE"
  "${ADB[@]}" shell settings put secure enabled_accessibility_services "$CURRENT"
fi
"${ADB[@]}" shell settings put secure accessibility_enabled 1
"${ADB[@]}" shell am start -n "$PKG/.SettingsActivity"
