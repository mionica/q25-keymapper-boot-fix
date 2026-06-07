package ro.q25.pinentry;

import android.app.Activity;
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
        title.setText("Q25 KeyMapper Boot Fix / PIN entry helper");
        title.setTextSize(22);
        layout.addView(title);

        TextView body = new TextView(this);
        body.setText("Keeps KeyMapper Accessibility disabled only before the first unlock after boot, then restores it automatically. " +
                     "This enables typing a password at boot, without KeyMapper constantly crashing in the background.\n" +
                     "\n" +
                     "Additionaly, an accesibility service is provided that enables typing the PIN on the lockscreen using the keyboard, " +
                     "*after* the phone is first unlocked\n" +
					 "It also supports pin entry on they keyboard in two Romanian banking apps\n" +
                     "\n" +
					 "This work is derived from the excellent com.q25.keymapperbootfix by smh786\n"
					 );
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
        setup.setText("One-time setup:\nadb shell pm grant ro.q25.pinentry android.permission.WRITE_SECURE_SETTINGS");
        setup.setTextSize(14);
        setup.setPadding(0, dp(16), 0, 0);
        layout.addView(setup);

        setContentView(layout);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
		// disable self-updating - this is mostly for personal consumption
        //if (BuildConfig.UPDATE_CHECK_ENABLED && !checkedForUpdates) {
        //    checkedForUpdates = true;
        //    UpdateChecker.checkForUpdates(this, this::showUpdateDialog);
        //}
    }

    private void refresh() {
        status.setText(AccessibilityGate.status(this));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

}
