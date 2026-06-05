package com.q25.keymapperbootfix;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class UpdateChecker {
    interface Callback {
        void onUpdateAvailable(ReleaseInfo release);
    }

    static final class ReleaseInfo {
        final String versionName;
        final String releaseUrl;

        ReleaseInfo(String versionName, String releaseUrl) {
            this.versionName = versionName;
            this.releaseUrl = releaseUrl;
        }
    }

    private static final String TAG = "Q25UpdateChecker";
    private static final String LATEST_RELEASE_URL =
            "https://api.github.com/repos/smh786/q25-keymapper-boot-fix/releases/latest";
    private static final String PREFS = "update_checker";
    private static final String KEY_DISMISSED_VERSION = "dismissed_version";
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private UpdateChecker() {
    }

    static void checkForUpdates(Context context, Callback callback) {
        Context appContext = context.getApplicationContext();
        EXECUTOR.execute(() -> {
            try {
                ReleaseInfo release = fetchLatestRelease();
                if (release == null) return;

                String dismissedVersion = prefs(appContext).getString(KEY_DISMISSED_VERSION, null);
                if (release.versionName.equals(dismissedVersion)) return;
                if (compareSemVer(release.versionName, BuildConfig.VERSION_NAME) <= 0) return;

                MAIN.post(() -> callback.onUpdateAvailable(release));
            } catch (Exception error) {
                Log.w(TAG, "Update check failed", error);
            }
        });
    }

    static void dismissRelease(Context context, String versionName) {
        prefs(context).edit().putString(KEY_DISMISSED_VERSION, versionName).apply();
    }

    private static ReleaseInfo fetchLatestRelease() throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(LATEST_RELEASE_URL).openConnection();
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        connection.setRequestProperty("User-Agent", "q25-keymapper-boot-fix/" + BuildConfig.VERSION_NAME);

        int code = connection.getResponseCode();
        if (code < 200 || code >= 300) {
            Log.w(TAG, "GitHub release check failed with HTTP " + code);
            return null;
        }

        StringBuilder response = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                connection.getInputStream(),
                StandardCharsets.UTF_8
        ))) {
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
        } finally {
            connection.disconnect();
        }

        JSONObject json = new JSONObject(response.toString());
        String tagName = json.optString("tag_name", "");
        String releaseUrl = json.optString("html_url", "");
        String versionName = normalizeVersion(tagName);
        if (TextUtils.isEmpty(versionName) || TextUtils.isEmpty(releaseUrl)) return null;

        return new ReleaseInfo(versionName, releaseUrl);
    }

    private static SharedPreferences prefs(Context context) {
        Context deContext = context.createDeviceProtectedStorageContext();
        return deContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String normalizeVersion(String value) {
        if (value == null) return "";
        String trimmed = value.trim();
        return trimmed.toLowerCase(Locale.US).startsWith("v") ? trimmed.substring(1) : trimmed;
    }

    private static int compareSemVer(String left, String right) {
        int[] leftParts = parseCoreVersion(left);
        int[] rightParts = parseCoreVersion(right);
        for (int i = 0; i < leftParts.length; i++) {
            if (leftParts[i] != rightParts[i]) {
                return leftParts[i] > rightParts[i] ? 1 : -1;
            }
        }
        return 0;
    }

    private static int[] parseCoreVersion(String version) {
        String core = normalizeVersion(version).split("-", 2)[0].split("\\+", 2)[0];
        String[] parts = core.split("\\.");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid SemVer: " + version);
        }

        return new int[]{
                Integer.parseInt(parts[0]),
                Integer.parseInt(parts[1]),
                Integer.parseInt(parts[2])
        };
    }
}
