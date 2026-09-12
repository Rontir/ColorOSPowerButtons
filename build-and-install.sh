#!/usr/bin/env bash
set -euo pipefail

BASE_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
WORK_DIR="$BASE_DIR/.work-upstream"
PATCH_DIR="$BASE_DIR/patch"
OUT_APK="$BASE_DIR/ColorOS-Power-Actions-v1.0.8-debug.apk"
UPSTREAM="https://github.com/Nielk74/coloros-power-button-launcher.git"
UPSTREAM_TAG="v1.2.0"
PKG="com.heytap.wallet"
SERVICE="com.heytap.wallet/com.heytap.wallet.WalletRedirectAccessibilityService"
OLD_V11="com.nielk74.colorospowerlauncher"
OLD_V10="com.antoine.chatgptpower"
FIN="com.finshell.wallet"
ANDROID_CMDLINE_VERSION="15859902"
DEFAULT_SDK="$HOME/Android/Sdk"

SERIAL="${1:-}"

log() { printf '\n== %s ==\n' "$*"; }
die() { echo "ERROR: $*" >&2; exit 1; }
need() { command -v "$1" >/dev/null 2>&1 || die "$2"; }

find_sdkmanager() {
  local candidates=()
  [[ -n "${ANDROID_HOME:-}" ]] && candidates+=("$ANDROID_HOME")
  [[ -n "${ANDROID_SDK_ROOT:-}" ]] && candidates+=("$ANDROID_SDK_ROOT")
  candidates+=("$HOME/Android/Sdk" "$HOME/Android/sdk" "/opt/android-sdk" "/usr/lib/android-sdk")

  local home
  for home in "${candidates[@]}"; do
    [[ -n "$home" ]] || continue
    if [[ -x "$home/cmdline-tools/latest/bin/sdkmanager" ]]; then
      ANDROID_HOME="$home"
      SDKMANAGER="$home/cmdline-tools/latest/bin/sdkmanager"
      return 0
    fi
    if [[ -x "$home/tools/bin/sdkmanager" ]]; then
      ANDROID_HOME="$home"
      SDKMANAGER="$home/tools/bin/sdkmanager"
      return 0
    fi
  done
  return 1
}

install_cmdline_tools() {
  local sdk="$DEFAULT_SDK"
  local url="https://dl.google.com/android/repository/commandlinetools-linux-${ANDROID_CMDLINE_VERSION}_latest.zip"
  local tmp
  tmp="$(mktemp -d)"
  trap 'rm -rf "${tmp:-}"' RETURN

  echo "Android SDK was not found."
  echo "It can be installed for this user in: $sdk"
  printf 'Install Android SDK now? [Y/n] '
  read -r answer
  case "${answer:-Y}" in y|Y|yes|YES|"") ;; *) die "Android SDK installation cancelled." ;; esac

  need curl "curl is required"
  need unzip "unzip is required"
  log "Downloading Android Command Line Tools"
  curl -fL "$url" -o "$tmp/cmdline-tools.zip"
  unzip -q "$tmp/cmdline-tools.zip" -d "$tmp/unpacked"

  rm -rf "$sdk/cmdline-tools/latest"
  mkdir -p "$sdk/cmdline-tools/latest"
  cp -a "$tmp/unpacked/cmdline-tools/." "$sdk/cmdline-tools/latest/"
  ANDROID_HOME="$sdk"
  SDKMANAGER="$sdk/cmdline-tools/latest/bin/sdkmanager"
}

ensure_android_sdk() {
  SDKMANAGER=""
  if ! find_sdkmanager; then install_cmdline_tools; fi

  export ANDROID_HOME
  export ANDROID_SDK_ROOT="$ANDROID_HOME"
  export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"

  log "Android SDK"
  echo "ANDROID_HOME=$ANDROID_HOME"
  "$SDKMANAGER" --version

  if [[ ! -f "$ANDROID_HOME/platforms/android-36/android.jar" ]] \
      || [[ ! -d "$ANDROID_HOME/build-tools/36.0.0" ]]; then
    echo "Android API 36 / Build Tools 36 are missing."
    "$SDKMANAGER" --sdk_root="$ANDROID_HOME" --licenses
    "$SDKMANAGER" --sdk_root="$ANDROID_HOME" \
      "platforms;android-36" "build-tools;36.0.0" "platform-tools"
  fi

  [[ -f "$ANDROID_HOME/platforms/android-36/android.jar" ]] \
    || die "Android platform 36 is missing."
}

need git "git is required"
need java "Java is required (JDK 17+ recommended)"

log "Java"
java -version 2>&1 | head -3
ensure_android_sdk

if [[ -x "$ANDROID_HOME/platform-tools/adb" ]]; then
  ADB_BIN="$ANDROID_HOME/platform-tools/adb"
elif command -v adb >/dev/null 2>&1; then
  ADB_BIN="$(command -v adb)"
else
  die "adb is required."
fi

ADB=("$ADB_BIN")
if [[ -n "$SERIAL" ]]; then ADB=("$ADB_BIN" -s "$SERIAL"); fi

log "Preparing ColorOS Power Actions source"
rm -rf "$WORK_DIR"
git clone --depth 1 --branch "$UPSTREAM_TAG" "$UPSTREAM" "$WORK_DIR"
rm -rf "$WORK_DIR/walletshim"
cp -a "$PATCH_DIR/walletshim" "$WORK_DIR/walletshim"
sed -i 's/rootProject.name = "ColorOSPowerLauncher"/rootProject.name = "ColorOSPowerActions"/' "$WORK_DIR/settings.gradle"
chmod +x "$WORK_DIR/gradlew"
printf 'sdk.dir=%s\n' "$ANDROID_HOME" > "$WORK_DIR/local.properties"

log "Lint + build"
(
  cd "$WORK_DIR"
  ./gradlew --no-daemon :walletshim:lintDebug :walletshim:assembleDebug
)

BUILT="$WORK_DIR/walletshim/build/outputs/apk/debug/walletshim-debug.apk"
[[ -f "$BUILT" ]] || die "APK not found after build: $BUILT"
cp -f "$BUILT" "$OUT_APK"
echo "APK: $OUT_APK"

log "Checking ADB"
"${ADB[@]}" get-state >/dev/null 2>&1 \
  || die "No ADB device. Check 'adb devices' or pass IP:PORT as the first argument."

log "Stopping old Power Actions / legacy monitors"
"${ADB[@]}" shell am stopservice -n "$PKG/.TriplePressMonitorService" >/dev/null 2>&1 || true
"${ADB[@]}" shell am force-stop "$PKG" >/dev/null 2>&1 || true
"${ADB[@]}" shell am force-stop "$OLD_V11" >/dev/null 2>&1 || true
"${ADB[@]}" shell am force-stop "$OLD_V10" >/dev/null 2>&1 || true
"${ADB[@]}" uninstall "$OLD_V11" >/dev/null 2>&1 || true
"${ADB[@]}" uninstall "$OLD_V10" >/dev/null 2>&1 || true

log "Installing / upgrading Power Actions"
if ! "${ADB[@]}" install --no-streaming -r -g -t "$OUT_APK"; then
  echo "In-place upgrade failed (usually a signing-key mismatch). Reinstalling cleanly."
  "${ADB[@]}" uninstall "$PKG" >/dev/null 2>&1 || true
  "${ADB[@]}" install --no-streaming -r -g -t "$OUT_APK"
fi

"${ADB[@]}" shell appops set "$PKG" SYSTEM_ALERT_WINDOW default >/dev/null 2>&1 || true

log "Granting reboot-repair permission"
if "${ADB[@]}" shell pm grant "$PKG" android.permission.WRITE_SECURE_SETTINGS; then
  echo "WRITE_SECURE_SETTINGS granted."
else
  echo "WARNING: ColorOS rejected WRITE_SECURE_SETTINGS."
  echo "Double press can work now, but Accessibility may need re-enabling after reboot."
  echo "Temporarily enable Developer options -> Disable system optimization, then run:"
  if [[ -n "$SERIAL" ]]; then
    echo "  $ADB_BIN -s '$SERIAL' shell pm grant $PKG android.permission.WRITE_SECURE_SETTINGS"
  else
    echo "  $ADB_BIN shell pm grant $PKG android.permission.WRITE_SECURE_SETTINGS"
  fi
fi

log "Selecting ColorOS Wallet mode"
"${ADB[@]}" shell settings put secure double_tap_power_button_value 1

log "Enabling scoped Accessibility redirect"
CURRENT="$("${ADB[@]}" shell settings get secure enabled_accessibility_services 2>/dev/null | tr -d '\r')"
[[ "$CURRENT" == "null" ]] && CURRENT=""
if [[ ":$CURRENT:" != *":$SERVICE:"* ]]; then
  [[ -n "$CURRENT" ]] && CURRENT="$CURRENT:$SERVICE" || CURRENT="$SERVICE"
  "${ADB[@]}" shell settings put secure enabled_accessibility_services "$CURRENT"
fi
"${ADB[@]}" shell settings put secure accessibility_enabled 1

log "FinShell check"
if "${ADB[@]}" shell pm path "$FIN" 2>/dev/null | grep -q '^package:'; then
  echo "FinShell can take priority over the bridge; disabling it for user 0."
  "${ADB[@]}" shell pm disable-user --user 0 "$FIN" || true
  echo "Rollback: $ADB_BIN shell pm enable $FIN"
else
  echo "FinShell not installed."
fi

log "Verification"
printf 'Wallet mode: '
"${ADB[@]}" shell settings get secure double_tap_power_button_value | tr -d '\r'
printf 'Package: '
"${ADB[@]}" shell pm path "$PKG" | tr -d '\r'
printf 'Accessibility redirect: '
"${ADB[@]}" shell settings get secure enabled_accessibility_services \
  | tr -d '\r' | grep -o 'com.heytap.wallet[^:]*' || true

if "${ADB[@]}" shell dumpsys package "$PKG" | grep -q 'android.permission.READ_LOGS'; then
  echo "WARNING: READ_LOGS still appears in package metadata."
else
  echo "Legacy READ_LOGS permission: removed"
fi

if "${ADB[@]}" shell dumpsys package "$PKG" | grep -q 'TriplePressMonitorService'; then
  echo "WARNING: legacy service still appears in package metadata."
else
  echo "Legacy foreground service: removed"
fi

log "Opening Power Actions"
"${ADB[@]}" shell am start -n "$PKG/.SettingsActivity"

echo
echo "Done. Power Actions is double-press only."
echo "Google Wallet itself was not removed."
