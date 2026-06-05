package com.q25.keymapperbootfix;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class BootStateReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) return;

        String action = intent.getAction();
        if (Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_USER_UNLOCKED.equals(action)) {
            AccessibilityGate.applyForCurrentLockState(context);
        }
    }
}
