# Q25 KeyMapper Boot Fix

Q25 KeyMapper Boot Fix is a tiny Android helper for the Zinwa Q25 / BenOS boot keyboard issue where KeyMapper Accessibility starts before the first user unlock and touches credential-encrypted storage.

The app disables KeyMapper Accessibility during Direct Boot, then restores it after the first unlock.

## What It Does

- On `LOCKED_BOOT_COMPLETED` or locked `BOOT_COMPLETED`, remove KeyMapper from `enabled_accessibility_services`.
- Store the previous accessibility service list in device-protected storage.
- On `USER_UNLOCKED`, restore the previous list.
- After first unlock, KeyMapper remains available on the lockscreen and when the screen is off.

## One-Time Setup

Install the APK, open it once, then grant secure settings permission:

```bash
adb shell pm grant com.q25.keymapperbootfix android.permission.WRITE_SECURE_SETTINGS
```

Opening the app once is important because Android suppresses boot receivers for newly installed apps until they have been launched.

## Device Test

Verified on a connected Q25:

- Before unlock after reboot: user state `RUNNING_LOCKED`; KeyMapper removed from enabled accessibility services.
- After first unlock: user state `RUNNING_UNLOCKED`; KeyMapper restored automatically.

## Build

```bash
./gradlew assembleStandardDebug
./gradlew assembleSystemDebug
./gradlew assembleStandardRelease
./gradlew assembleSystemRelease
```

The standard APK checks GitHub Releases when opened. If a newer SemVer release
exists, it shows a dialog that opens the latest release page.

The system APK is intended for ROM inclusion. It has no launcher entry and does
not check GitHub for updates.

## Release Variants

GitHub releases publish two APKs:

- `q25-keymapper-boot-fix-standard-<version>.apk`: sideload build with launcher UI and update checker.
- `q25-keymapper-boot-fix-system-<version>.apk`: no-launcher build for `/system/priv-app`.

For ROM inclusion, install the system APK as a priv-app and allowlist:

```xml
<privapp-permissions package="com.q25.keymapperbootfix">
    <permission name="android.permission.WRITE_SECURE_SETTINGS"/>
</privapp-permissions>
```

### System APK Test Install

ROM testers can smoke-test the no-launcher system APK as a normal user app before
adding it to a ROM image:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\install-system-test.ps1 -ApkPath .\q25-keymapper-boot-fix-system-1.0.6.apk
```

On Linux/macOS:

```bash
chmod +x ./scripts/install-system-test.sh
./scripts/install-system-test.sh --apk ./q25-keymapper-boot-fix-system-1.0.6.apk
```

If Android reports a signature mismatch from an older local install, rerun with:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\install-system-test.ps1 -ApkPath .\q25-keymapper-boot-fix-system-1.0.6.apk -CleanInstall
```

Or on Linux/macOS:

```bash
./scripts/install-system-test.sh --apk ./q25-keymapper-boot-fix-system-1.0.6.apk --clean-install
```

The script installs the APK, grants `WRITE_SECURE_SETTINGS`, launches the hidden
activity once so Android will deliver boot broadcasts, and prints package state.

This is not the same as final ROM integration. For BenOS or another ROM, the APK
should still be installed under `/system/priv-app` with the priv-app permission
allowlist above.

Release signing is read from environment variables:

```bash
Q25_BOOT_FIX_KEYSTORE=/path/to/release.jks
Q25_BOOT_FIX_KEYSTORE_PASSWORD=...
Q25_BOOT_FIX_KEY_ALIAS=q25-keymapper-boot-fix
Q25_BOOT_FIX_KEY_PASSWORD=...
```

## CI and Releases

Pull requests run `.github/workflows/test.yml`:

- SemVer `versionName` bump check against `main`
- `testDebugUnitTest`
- `lintDebug`
- `assembleDebug`

Merges to `main` run `.github/workflows/release.yml`, which builds a signed release APK and publishes it as a GitHub Release. The release workflow needs these repository secrets:

- `KEYSTORE_BASE64`
- `KEYSTORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`

Release tags use the checked-in SemVer `versionName`, for example `v1.0.3`.

## Targeted KeyMapper Service

```text
io.github.sds100.keymapper/io.github.sds100.keymapper.system.accessibility.MyAccessibilityService
```

## Support

If this fix helps you, you can support ongoing device debugging and maintenance on Ko-fi:

[ko-fi.com/smh786](https://ko-fi.com/smh786)

## License

MIT
