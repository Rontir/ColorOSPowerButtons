# Changelog

## 1.0.8

- Use Android `KeyguardManager.requestDismissKeyguard` for locked-screen launches.
- Let System UI present the normal device unlock instead of an app-owned biometric prompt.
- Launch the selected target only after the system keyguard is actually dismissed.
- Dismiss OPPO Wallet reassertions during unlock without creating duplicate unlock requests.
- Automatically open Accessibility settings when the scoped redirect is not enabled.
- If `WRITE_SECURE_SETTINGS` is granted, keep Wallet mode selected and restore the Accessibility entry after reboot.
- Remove the no-longer-needed `USE_BIOMETRIC` permission.

## 1.0.6

- Add AMOLED-first Pixel-like Material You settings UI.
- Keep the stable X9s Pro late-Wallet recovery behavior.
- Use a pure-black base background with dynamic system accents.
- Add larger rounded surfaces, pill controls and a cleaner selected-app card.
- Add GitHub Actions lint/build workflow.
- Consolidate Linux build, install and diagnostics scripts.

## 1.0.5

- Improve recovery when ColorOS re-opens the OEM Wallet after the selected app is already visible.
- Restore the selected app after the guarded late-Wallet dismissal.
- Debounce callbacks from the same Wallet window.

## 1.0.4

- Establish the lightweight double-press baseline for Find X9s Pro.
- Remove the legacy logcat monitor, overlay and foreground-service path from the main implementation.

## 1.0.2

- Use a one-shot foreground bridge for reliable target handoff.

## 1.0.0

- Initial arbitrary-app double-power bridge.
