package com.q25.keymapperbootfix;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class MainActivity extends Activity {
    private TextView status;
    private boolean checkedForUpdates;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(20);
        layout.setPadding(padding, padding, padding, padding);

        TextView title = new TextView(this);
        title.setText("Q25 KeyMapper Boot Fix");
        title.setTextSize(22);
        layout.addView(title);

        TextView body = new TextView(this);
        body.setText("Keeps KeyMapper Accessibility disabled only before the first unlock after boot, then restores it automatically.");
        body.setTextSize(16);
        body.setPadding(0, dp(12), 0, dp(12));
        layout.addView(body);

        status = new TextView(this);
        status.setTextSize(15);
        layout.addView(status);

        Button apply = new Button(this);
        apply.setText("Apply now");
        apply.setOnClickListener(view -> {
            AccessibilityGate.applyForCurrentLockState(this);
            refresh();
        });
        layout.addView(apply);

        TextView setup = new TextView(this);
        setup.setText("One-time setup:\nadb shell pm grant com.q25.keymapperbootfix android.permission.WRITE_SECURE_SETTINGS");
        setup.setTextSize(14);
        setup.setPadding(0, dp(16), 0, 0);
        layout.addView(setup);

        setContentView(layout);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
        if (!checkedForUpdates) {
            checkedForUpdates = true;
            UpdateChecker.checkForUpdates(this, this::showUpdateDialog);
        }
    }

    private void refresh() {
        status.setText(AccessibilityGate.status(this));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void showUpdateDialog(UpdateChecker.ReleaseInfo release) {
        if (isFinishing() || isDestroyed()) return;

        new AlertDialog.Builder(this)
                .setTitle("Update available")
                .setMessage("Version " + release.versionName + " is available.\n\nYou are running "
                        + BuildConfig.VERSION_NAME + ".")
                .setPositiveButton("Open release", (dialog, which) -> {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(release.releaseUrl));
                    startActivity(intent);
                })
                .setNegativeButton("Later", (dialog, which) ->
                        UpdateChecker.dismissRelease(this, release.versionName))
                .show();
    }
}
