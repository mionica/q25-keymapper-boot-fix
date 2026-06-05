param(
    [string]$ApkPath = "",
    [string]$AdbPath = "adb",
    [switch]$CleanInstall
)

$ErrorActionPreference = "Stop"

function Resolve-ApkPath {
    param([string]$PathFromUser)

    if ($PathFromUser) {
        return (Resolve-Path -LiteralPath $PathFromUser).Path
    }

    $candidate = Get-ChildItem -Path (Get-Location) -Filter "q25-keymapper-boot-fix-system-*.apk" |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1

    if (-not $candidate) {
        throw "Pass -ApkPath or run this from a folder containing q25-keymapper-boot-fix-system-*.apk"
    }

    return $candidate.FullName
}

$apk = Resolve-ApkPath $ApkPath
$packageName = "com.q25.keymapperbootfix"
$activity = "$packageName/.MainActivity"

Write-Host "Using APK: $apk"
& $AdbPath devices

if ($CleanInstall) {
    Write-Host "Uninstalling existing package, if present..."
    & $AdbPath uninstall $packageName | Out-Host
}

Write-Host "Installing no-launcher system-test APK..."
& $AdbPath install -r $apk | Out-Host

Write-Host "Granting WRITE_SECURE_SETTINGS..."
& $AdbPath shell pm grant $packageName android.permission.WRITE_SECURE_SETTINGS

Write-Host "Launching once to clear Android's stopped/notLaunched state..."
& $AdbPath shell am start -n $activity | Out-Host

Write-Host "Checking for launcher entry; expected result is no output below:"
& $AdbPath shell cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER |
    Select-String $packageName

Write-Host "Package state:"
& $AdbPath shell dumpsys package $packageName |
    Select-String "versionName|WRITE_SECURE_SETTINGS|stopped|notLaunched|BootStateReceiver"

Write-Host ""
Write-Host "Next test:"
Write-Host "  adb reboot"
Write-Host "Then before first unlock, confirm KeyMapper Accessibility is absent:"
Write-Host "  adb shell settings get secure enabled_accessibility_services"
Write-Host "After first unlock, KeyMapper should be restored automatically."
Write-Host ""
Write-Host "Note: this is only a no-launcher user-app test. ROM integration should install the APK as /system/priv-app with a privapp permissions allowlist."
