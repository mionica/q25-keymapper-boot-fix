#!/usr/bin/env sh
set -eu

ADB="${ADB:-adb}"
APK_PATH=""
CLEAN_INSTALL=0
PACKAGE_NAME="ro.q25.pinentry"
ACTIVITY="$PACKAGE_NAME/.MainActivity"

while [ "$#" -gt 0 ]; do
    case "$1" in
        --apk)
            APK_PATH="$2"
            shift 2
            ;;
        --adb)
            ADB="$2"
            shift 2
            ;;
        --clean-install)
            CLEAN_INSTALL=1
            shift
            ;;
        -h|--help)
            echo "Usage: $0 [--apk path/to/q25-keymapper-boot-fix-system-<version>.apk] [--adb path/to/adb] [--clean-install]"
            exit 0
            ;;
        *)
            echo "Unknown argument: $1" >&2
            exit 2
            ;;
    esac
done

if [ -z "$APK_PATH" ]; then
    APK_PATH="$(find . -maxdepth 1 -name 'q25-keymapper-boot-fix-system-*.apk' -print | sort | tail -n 1)"
fi

if [ -z "$APK_PATH" ] || [ ! -f "$APK_PATH" ]; then
    echo "Pass --apk or run this from a folder containing q25-keymapper-boot-fix-system-*.apk" >&2
    exit 1
fi

echo "Using APK: $APK_PATH"
"$ADB" devices

if [ "$CLEAN_INSTALL" -eq 1 ]; then
    echo "Uninstalling existing package, if present..."
    "$ADB" uninstall "$PACKAGE_NAME" || true
fi

echo "Installing no-launcher system-test APK..."
"$ADB" install -r "$APK_PATH"

echo "Granting WRITE_SECURE_SETTINGS..."
"$ADB" shell pm grant "$PACKAGE_NAME" android.permission.WRITE_SECURE_SETTINGS

echo "Launching once to clear Android's stopped/notLaunched state..."
"$ADB" shell am start -n "$ACTIVITY"

echo "Checking for launcher entry; expected result is no output below:"
"$ADB" shell cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER |
    grep "$PACKAGE_NAME" || true

echo "Package state:"
"$ADB" shell dumpsys package "$PACKAGE_NAME" |
    grep -E "versionName|WRITE_SECURE_SETTINGS|stopped|notLaunched|BootStateReceiver" || true

cat <<'EOF'

Next test:
  adb reboot
Then before first unlock, confirm KeyMapper Accessibility is absent:
  adb shell settings get secure enabled_accessibility_services
After first unlock, KeyMapper should be restored automatically.

Note: this is only a no-launcher user-app test. ROM integration should install the APK as /system/priv-app with a privapp permissions allowlist.
EOF
