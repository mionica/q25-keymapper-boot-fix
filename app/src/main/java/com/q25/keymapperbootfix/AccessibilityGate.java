package com.q25.keymapperbootfix;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.UserManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

final class AccessibilityGate {
    static final String KEYMAPPER_SERVICE =
            "io.github.sds100.keymapper/io.github.sds100.keymapper.system.accessibility.MyAccessibilityService";

    private static final String TAG = "Q25BootFix";
    private static final String PREFS = "boot_fix";
    private static final String KEY_RESTORE_LIST = "restore_list";
    private static final String KEY_DISABLED_BY_US = "disabled_by_us";

    private AccessibilityGate() {
    }

    static boolean hasWriteSecureSettings(Context context) {
        return context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS)
                == PackageManager.PERMISSION_GRANTED;
    }

    static boolean isUserUnlocked(Context context) {
        UserManager userManager = context.getSystemService(UserManager.class);
        return userManager != null && userManager.isUserUnlocked();
    }

    static void applyForCurrentLockState(Context context) {
        if (isUserUnlocked(context)) {
            restoreKeyMapper(context);
        } else {
            disableKeyMapperUntilUnlock(context);
        }
    }

    static void disableKeyMapperUntilUnlock(Context context) {
        if (!hasWriteSecureSettings(context)) {
            Log.w(TAG, "WRITE_SECURE_SETTINGS is not granted; cannot disable KeyMapper.");
            return;
        }

        String enabled = getEnabledServices(context);
        if (enabled == null || !containsService(enabled, KEYMAPPER_SERVICE)) {
            return;
        }

        prefs(context).edit()
                .putString(KEY_RESTORE_LIST, enabled)
                .putBoolean(KEY_DISABLED_BY_US, true)
                .apply();

        String updated = joinWithout(enabled, KEYMAPPER_SERVICE);
        Settings.Secure.putString(
                context.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                updated
        );

        Log.i(TAG, "Disabled KeyMapper Accessibility until first user unlock.");
    }

    static void restoreKeyMapper(Context context) {
        if (!hasWriteSecureSettings(context)) {
            Log.w(TAG, "WRITE_SECURE_SETTINGS is not granted; cannot restore KeyMapper.");
            return;
        }

        SharedPreferences prefs = prefs(context);
        String current = getEnabledServices(context);
        String restoreList = prefs.getString(KEY_RESTORE_LIST, null);
        boolean disabledByUs = prefs.getBoolean(KEY_DISABLED_BY_US, false);

        if (containsService(current, KEYMAPPER_SERVICE)) {
            prefs.edit().putBoolean(KEY_DISABLED_BY_US, false).apply();
            return;
        }

        String updated = null;
        if (disabledByUs && !TextUtils.isEmpty(restoreList)) {
            updated = restoreList;
        } else if (!TextUtils.isEmpty(current)) {
            updated = KEYMAPPER_SERVICE + ":" + current;
        } else {
            updated = KEYMAPPER_SERVICE;
        }

        Settings.Secure.putString(
                context.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                updated
        );

        prefs.edit().putBoolean(KEY_DISABLED_BY_US, false).apply();
        Log.i(TAG, "Restored KeyMapper Accessibility after first user unlock.");
    }

    static String status(Context context) {
        StringBuilder builder = new StringBuilder();
        builder.append("User unlocked: ").append(isUserUnlocked(context)).append('\n');
        builder.append("WRITE_SECURE_SETTINGS: ").append(hasWriteSecureSettings(context)).append('\n');
        builder.append("KeyMapper enabled: ")
                .append(containsService(getEnabledServices(context), KEYMAPPER_SERVICE))
                .append('\n');
        return builder.toString();
    }

    private static SharedPreferences prefs(Context context) {
        Context deContext = context.createDeviceProtectedStorageContext();
        return deContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String getEnabledServices(Context context) {
        return Settings.Secure.getString(
                context.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        );
    }

    private static boolean containsService(String serviceList, String service) {
        if (TextUtils.isEmpty(serviceList)) return false;
        return Arrays.asList(serviceList.split(":")).contains(service);
    }

    private static String joinWithout(String serviceList, String serviceToRemove) {
        List<String> kept = new ArrayList<>();
        if (!TextUtils.isEmpty(serviceList)) {
            for (String service : serviceList.split(":")) {
                if (!service.equals(serviceToRemove) && !service.isEmpty()) {
                    kept.add(service);
                }
            }
        }
        return TextUtils.join(":", kept);
    }
}
