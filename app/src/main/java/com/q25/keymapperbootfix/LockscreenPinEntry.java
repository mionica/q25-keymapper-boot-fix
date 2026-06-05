package com.q25.keymapperbootfix;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.KeyguardManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

/**
 * Accessibility service that automatically focuses input fields when apps are opened.
 * Supports browser address bars, message input fields, and other text fields.
 */
public class LockscreenPinEntry extends AccessibilityService {
    private static final String TAG = "LockscreenPinEntry";

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED |
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS |
                AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
        info.notificationTimeout = 100;
        setServiceInfo(info);
        Log.d(TAG, "LockscreenPinEntry Accessibility Service connected");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "LockscreenPinEntry Accessibility Service destroyed");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;
        // Check if accessibility service has proper permissions
        if (!isAccessibilityServiceEnabled()) {
            Log.w(TAG, "Accessibility service not properly enabled");
            return;
        }
    }

    @Override
    public void onInterrupt() {
        Log.d(TAG, "LockscreenPinEntry Accessibility Service interrupted");
    }

    /**
     * Check if the accessibility service is properly enabled with required permissions.
     */
    private boolean isAccessibilityServiceEnabled() {
        try {
            AccessibilityServiceInfo info = getServiceInfo();
            if (info == null) {
                Log.w(TAG, "Service info is null");
                return false;
            }
            // Check if we have the required event types
            boolean hasWindowStateChanged = (info.eventTypes & AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) != 0;
            if (!hasWindowStateChanged) {
                Log.w(TAG, "Missing TYPE_WINDOW_STATE_CHANGED event type");
                return false;
            }
            // Check if we have the flag to retrieve interactive windows
            boolean canRetrieveWindows = (info.flags & AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS) != 0;
            if (!canRetrieveWindows) {
                Log.w(TAG, "Missing FLAG_RETRIEVE_INTERACTIVE_WINDOWS flag");
                return false;
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error checking accessibility service status", e);
            return false;
        }
    }

    // --- Lockscreen PIN Entry ---

    private boolean isDeviceLocked() {
        KeyguardManager keyguardManager = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        return keyguardManager != null && keyguardManager.isKeyguardLocked();
    }

    private boolean clickButtonOnPinScreen(String viewId, String keyName) {
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode == null)
            return false;
        try {
            // only search on the PIN lockscreen !!!
            // on the Q25, all nodes are under a node with
            //   resource-id="com.android.systemui:id/keyguard_pin_view"
            // we can't search under that (viewId's are not available) *but*
            // what we can do is, search for what we're interested in, from the root node,
            // and then check that the node we find - if any - is a child of the
            // keyboard_pin_view
            List<AccessibilityNodeInfo> pinViewNodes = rootNode.findAccessibilityNodeInfosByViewId("com.android.systemui:id/keyguard_pin_view");
            if (pinViewNodes.size() != 1) {
                if (pinViewNodes.size() > 1)
                    Log.w(TAG, "Found "+pinViewNodes.size()+" instances of com.android.systemui:id/keyguard_pin_view!!!");
                return false;
            }
            List<AccessibilityNodeInfo> targetNodes = rootNode.findAccessibilityNodeInfosByViewId(viewId);
            if (targetNodes.size() != 1) {
                if (targetNodes.size() > 1)
                    Log.w(TAG, "Found "+targetNodes.size()+" instances of "+viewId+"!!!");
                return false;
            }
            AccessibilityNodeInfo pinViewNode = pinViewNodes.get(0);
            AccessibilityNodeInfo targetNode = targetNodes.get(0);
            try {
                if (! targetNode.isClickable()) {
                    Log.w(TAG, "Not clickable " + viewId);
                    return false;
                }
                // make sure the target node is a child of pinViewNode
                for (int i = 0; i < pinViewNode.getChildCount(); ++i) {
                    if (targetNode.equals(pinViewNode.getChild(i))) {
                        targetNode.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                        // just kidding - you don't want your PIN in logcat...
                        //Log.d(TAG, "Clicked "+keyName+" on PIN lockscreen");
                        targetNode.recycle();
                        return true;
                    }
                }
                // if we get here, the node isn't under keyguard_pin_view
                Log.w(TAG, viewId + " not found under keyguard_pin_view");
            } finally {
                pinViewNode.recycle();
                targetNode.recycle();
            }
        } finally {
            rootNode.recycle();
        }
        return false;
    }

    private String getCharFromKeyCode(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_W:
            case KeyEvent.KEYCODE_1:
                return "1";
            case KeyEvent.KEYCODE_E:
            case KeyEvent.KEYCODE_2:
                return "2";
            case KeyEvent.KEYCODE_R:
            case KeyEvent.KEYCODE_3:
                return "3";
            case KeyEvent.KEYCODE_S:
            case KeyEvent.KEYCODE_4:
                return "4";
            case KeyEvent.KEYCODE_D:
            case KeyEvent.KEYCODE_5:
                return "5";
            case KeyEvent.KEYCODE_F:
            case KeyEvent.KEYCODE_6:
                return "6";
            case KeyEvent.KEYCODE_Z:
            case KeyEvent.KEYCODE_7:
                return "7";
            case KeyEvent.KEYCODE_X:
            case KeyEvent.KEYCODE_8:
                return "8";
            case KeyEvent.KEYCODE_C:
            case KeyEvent.KEYCODE_9:
                return "9";
            case KeyEvent.KEYCODE_0:
                return "0";
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_DPAD_CENTER:
                return "\n";
            case KeyEvent.KEYCODE_DEL:
                return "\b";
            default:
                return null;
        }
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (event == null || event.getAction() != KeyEvent.ACTION_DOWN)
            return false;
        if (!isDeviceLocked())
            return false;
        String key = getCharFromKeyCode(event.getKeyCode());
        if (key == null)
            return false;
        // for digits, Q25 uses exactly one node of the type
        // <node text="" resource-id="com.android.systemui:id/key[0..9]" content-desc="[0..9]"
        //   class="android.view.ViewGroup" package="com.android.systemui"
        //   enabled="true" clickable="true" focusable="true"
        //   checkable="false" scrollable="false" long-clickable="false" password="false"
        //   ...>
        // enter/backspace have the resource-id's
        //   "com.android.systemui:id/key_enter" / "com.android.systemui:id/delete_button"
        // respectively, with class="android.widget.ImageButton"
        if (key == "\b")
            return clickButtonOnPinScreen("com.android.systemui:id/delete_button", "DEL");
        if (key == "\n")
            return clickButtonOnPinScreen("com.android.systemui:id/key_enter", "ENTER");
        if (key != null)
            return clickButtonOnPinScreen("com.android.systemui:id/key" + key, key);
        return false;
    }
}
