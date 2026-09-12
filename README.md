# ColorOS Power Actions

**Power Actions** is a small rootless Android utility for ColorOS phones where the physical power-button double press is hard-wired to the OEM Wallet. It keeps ColorOS's native double-press timing, intercepts the Wallet quick-launch route and opens an app selected by the user.

The current implementation is intentionally **double-press only**.

## Highlights

- `2x Power` -> any installed launchable app.
- Google Wallet uses `com.google.android.apps.wallet.main.QUICKDRAW` when available.
- AMOLED-first, Pixel-like UI with Android 12+ dynamic Material You accents.
- No root.
- No `READ_LOGS`.
- No foreground service.
- No overlay window.
- No wake lock.
- No network permission.
- Accessibility is restricted to the exact OPPO Wallet quick-launch activity and cannot read window contents.

## Tested direction

Development and real-device debugging focused on an OPPO Find X9s Pro Chinese ColorOS build. The implementation is based on the MIT-licensed Wallet bridge from `Nielk74/coloros-power-button-launcher`, while adding arbitrary-app selection and X9s-specific late-Wallet recovery.

ColorOS may route Wallet in two ways:

1. Directly to the absent `com.heytap.wallet` package. `BridgeActivity` exposes an invisible package front door and forwards it to the selected target.
2. Through `com.heytap.tas/com.nearme.wallet.nfc.ui.NfcConsumeActivity`. A narrowly scoped Accessibility service dismisses the OEM Wallet and launches a one-shot foreground bridge.

The app-drawer icon always opens `SettingsActivity`.

## Current redirect flow

Normal double press:

```text
ColorOS Wallet event
-> immediate BACK
-> 85 ms settle
-> one-shot BridgeActivity
-> selected app
```

On the tested X9s Pro firmware, ColorOS can re-open the Wallet activity roughly 0.8-1.0 seconds after the selected app is already visible. The recovery path is:

```text
late Wallet reassertion
-> one BACK
-> 75 ms settle
-> restore selected app through BridgeActivity
```

The restore is intentional. If BACK is delivered late during a transition, the target is brought forward again instead of leaving the launcher exposed.

## UI

The settings screen is AMOLED-first by default:

- pure black base background,
- dark elevated surfaces,
- dynamic system accent color,
- large rounded cards,
- pill buttons and status badges,
- Pixel-like typography hierarchy,
- large touch targets.

## Build and install on Linux

Requirements are handled by `build-and-install.sh`. It can install Android SDK 36 command-line components into `~/Android/Sdk` when needed.

```bash
chmod +x build-and-install.sh
./build-and-install.sh
```

Wireless ADB:

```bash
./build-and-install.sh 192.168.1.100:12345
```

The build output is:

```text
ColorOS-Power-Actions-v1.0.8-debug.apk
```

The installer also:

- removes old third-party power-button monitor packages,
- stops removed legacy services when present,
- resets the legacy overlay app-op,
- preserves the selected target when upgrading with the same signing key,
- sets `double_tap_power_button_value=1` (Wallet),
- appends only the Power Actions Accessibility entry while preserving other services,
- tries to grant `WRITE_SECURE_SETTINGS`,
- disables `com.finshell.wallet` for user 0 when it would take priority,
- never disables `com.heytap.tas`.

## Permissions

Power Actions requests only what the bridge needs:

- `RECEIVE_BOOT_COMPLETED` - restore its own Accessibility entry if ColorOS removes it after reboot.
- `WRITE_SECURE_SETTINGS` - ADB-granted boot-repair capability.
- `BIND_ACCESSIBILITY_SERVICE` - system-bound permission for the narrow redirect service.

There is no `INTERNET` permission.

## First-run setup and lock screen

Power Actions intentionally requests only the access it actually needs.

- `RECEIVE_BOOT_COMPLETED` is granted at install time.
- Accessibility is required for the scoped `com.heytap.tas` redirect. If it is missing when the app opens, Power Actions automatically opens Android Accessibility settings so the user can enable `Power Actions redirect`.
- `WRITE_SECURE_SETTINGS` has no normal Android permission dialog. The Linux/ADB installer grants it when ColorOS allows the grant; it is used only to restore this app's Accessibility entry and keep the ColorOS Wallet shortcut selected.
- No biometric permission is used. When the shortcut is triggered while the device is locked, Power Actions asks Android to dismiss the keyguard. System UI presents the normal fingerprint, face, PIN or pattern flow, and the selected target opens only after the device is unlocked.

## Diagnostics

If behavior changes after a ColorOS update:

```bash
./capture-power-actions-log.sh
```

Reproduce several double presses, stop with `Ctrl+C`, and inspect the resulting log before changing timing constants.

## Known limitation

Accessibility receives the Wallet event only after Android creates the OEM Wallet window. A very short Wallet flash may therefore remain visible on some firmware. Eliminating the OEM activity before window creation would require a deeper system/root-level hook.

## Upstream and license

Based on the MIT-licensed `Nielk74/coloros-power-button-launcher` Wallet bridge. The original license notice is retained in [`LICENSE-UPSTREAM.txt`](LICENSE-UPSTREAM.txt).
