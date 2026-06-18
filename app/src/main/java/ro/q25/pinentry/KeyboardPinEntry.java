package ro.q25.pinentry;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.KeyguardManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.List;

/**
 * Accessibility service that automatically focuses input fields when apps are opened.
 * Supports browser address bars, message input fields, and other text fields.
 */
public class KeyboardPinEntry extends AccessibilityService {
    private static final String TAG = "KeyboardPinEntry";

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        // we have no need to listen to window events
        info.eventTypes = 0;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS |
                AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
        info.notificationTimeout = 100;
        setServiceInfo(info);
        Log.d(TAG, "KeyboardPinEntry Accessibility Service connected");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "KeyboardPinEntry Accessibility Service destroyed");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
        Log.d(TAG, "KeyboardPinEntry Accessibility Service interrupted");
    }

    private boolean isDeviceLocked() {
        KeyguardManager keyguardManager = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        return keyguardManager != null && keyguardManager.isKeyguardLocked();
    }

    private static final String pinContainerQ25Lockscreen = "com.android.systemui:id/keyguard_pin_view";
    private static final String pinContainerCecApp = "hr.asseco.android.jimba.cecro:id/keypad";
    private static final String pinContainerCecTokenApp = "ro.cec.android.mtoken:id/virtual_keypad_background";

    // Wise is a bit different - the backspace is outside the keypad object, so use a container of
    // both it and the keypad
    //private static final String pinContainerWiseApp = "com.transferwise.android:id/keypad";
    private static final String pinContainerWiseApp = "com.transferwise.android:id/container";

    // Revolut and BT Pay seem to disable accessibility access
//  private static final String pinContainerRevolutApp = "com.revolut.revolut:id/PinCodeView_keypad";
//  private static final String pinContainerBTPayApp = "ro.btrl.pay:id/action_bar_root";

    // lookup a specific PIN container, so we'll be sure we're not clicking randomly
    private AccessibilityNodeInfo getContainer(String name) {
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode == null)
            return null;
        List<AccessibilityNodeInfo> pinViewNodes = rootNode.findAccessibilityNodeInfosByViewId(name);
        AccessibilityNodeInfo result = null;
        if (pinViewNodes.size() == 1)
            result = pinViewNodes.get(0);
        else if (pinViewNodes.size() > 1)
            Log.w(TAG, "Found "+pinViewNodes.size()+" instances of "+name+"!!!");
        rootNode.recycle();
        return result;
    }

    private boolean clickButtonInContainer(AccessibilityNodeInfo container, String viewId, String text, String keyName, int clickDelta) {
        if (container == null)
            return false;
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode == null)
            return false;
        if(text != null && keyName == null)
            keyName = text;
        List<AccessibilityNodeInfo> targetNodes;
        if (viewId != null)
            targetNodes = rootNode.findAccessibilityNodeInfosByViewId(viewId);
        else
            targetNodes = rootNode.findAccessibilityNodeInfosByText(text);
        AccessibilityNodeInfo targetNode = null;
        if (targetNodes.size() == 1)
            targetNode = targetNodes.get(0);
        else if (targetNodes.size() > 1)
            Log.w(TAG, "Found "+targetNodes.size()+" instances of "+viewId+"!!!");
        if (targetNode == null) {
            rootNode.recycle();
            return false;
        }
        boolean result = false;
        // make sure the target node is a child of pinViewNode
        for (int i = 0; i < container.getChildCount(); ++i) {
            AccessibilityNodeInfo crtNode = container.getChild(i);
            if (targetNode.equals(crtNode)) {
                // this is the normal case
                if (clickDelta == 0) {
                    if (targetNode.isClickable()) {
                        result = targetNode.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                        // just kidding - you don't want your PIN in logcat...
                        //Log.d(TAG, "Clicked "+keyName);
                    } else
                        Log.w(TAG, "Not clickable: " + viewId);
                    crtNode.recycle();
                    break;
                }
                // for CEC, you get to backspace as the first clickable thing after 0
                else if (clickDelta > 0) {
                    crtNode.recycle();
                    for (int j = i + 1; j < container.getChildCount(); ++j) {
                        crtNode = container.getChild(j);
                        if (crtNode.isClickable()) {
                            --clickDelta;
                            if (clickDelta == 0) {
                                result = crtNode.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                crtNode.recycle();
                                break;
                            }
                        }
                        crtNode.recycle();
                    }
                    break;
                }
                // for BTPay, you get to any digit via the last clickable thing before it (!)
                else { // if (clickBefore < 0)
                    crtNode.recycle();
                    for (int j = i - 1; j >= 0; --j) {
                        crtNode = container.getChild(j);
                        if (crtNode.isClickable()) {
                            ++clickDelta;
                            if (clickDelta == 0) {
                                result = crtNode.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                crtNode.recycle();
                                break;
                            }
                        }
                        crtNode.recycle();
                    }
                    break;
                }
            }
            crtNode.recycle();
        }
        targetNode.recycle();
        rootNode.recycle();
        // if we get here, the node doesn't exist in the PIN container
        if (! result)
            Log.w(TAG, ((viewId != null) ? viewId : keyName) + " not found");
        return result;
    }

    private boolean clickButtonInContainer(AccessibilityNodeInfo container, String viewId, String text, String keyName) {
        return clickButtonInContainer(container, viewId, text, keyName, 0);
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
        String key = getCharFromKeyCode(event.getKeyCode());
        if (key == null)
            return false;
        // handle the Q25 PIN lockscreen
        if (isDeviceLocked()) {
            // on the Q25, all nodes are under a node with
            //   resource-id="com.android.systemui:id/keyguard_pin_view"
            AccessibilityNodeInfo pinContainer = getContainer(pinContainerQ25Lockscreen);
            if (pinContainer == null)
                return false;
            // Q25 PIN entry lockscreen has
            // <node text="" resource-id="com.android.systemui:id/key[0..9]" content-desc="[0..9]"
            //   class="android.view.ViewGroup" package="com.android.systemui"
            //   enabled="true" clickable="true" focusable="true"
            //   checkable="false" scrollable="false" long-clickable="false" password="false"
            //   ...>
            // enter/backspace have the resource-id's
            //   "com.android.systemui:id/key_enter" / "com.android.systemui:id/delete_button"
            boolean result = false;
            if (key == "\b")
                result = clickButtonInContainer(pinContainer, "com.android.systemui:id/delete_button", null, "DEL");
            if (key == "\n")
                result = clickButtonInContainer(pinContainer, "com.android.systemui:id/key_enter", null, "ENTER");
            if (key != null)
                result = clickButtonInContainer(pinContainer, "com.android.systemui:id/key" + key, null, key);
            pinContainer.recycle();
            return result;
        }
        // handle known apps with PIN init screens
        AccessibilityNodeInfo pinContainer = null;
        pinContainer = getContainer(pinContainerCecApp);
        if (pinContainer != null) {
            // CEC PIN entry screen has
            // <node text="[0..9]" resource-id="" content-desc=""
            //   class="android.widget.Button" package="hr.asseco.android.jimba.cecro"
            //   enabled="true" clickable="true" focusable="true"
            //   checkable="false" scrollable="false" long-clickable="false" password="false"
            //   ...>
            // backspace's a bitch - no text, no resId, class "android.widget.ImageButton"
            //   have to detect is as the next clickable node after 0
            // ENTER's an interesting case: on the app login screen, it doesn't exist, but on the
            //   screen for inputting a PIN for confirmation, it does (identified by text="OK")
            boolean result = false;
            if (key == "\b")
                result = clickButtonInContainer(pinContainer, null, "0", "DEL", 1);
            else if (key == "\n")
                result = clickButtonInContainer(pinContainer, null, "OK", "ENTER");
            else if (key != null)
                result = clickButtonInContainer(pinContainer, null, key, null);
            pinContainer.recycle();
            return result;
        }
        pinContainer = getContainer(pinContainerCecTokenApp);
        if (pinContainer != null) {
            // CEC eToken PIN entry screen has
            // <node text="[0..9]" resource-id="ro.cec.android.mtoken:id/Pin[0..9]" content-desc="[0..9]"
            //   class="android.widget.Button" package="ro.cec.android.mtoken"
            //   enabled="true" clickable="true" focusable="true"
            //   checkable="false" scrollable="false" long-clickable="false" password="false"
            //   ...>
            // enter/backspace have the resource-id's
            //   "ro.cec.android.mtoken:id/PinOk" / "ro.cec.android.mtoken:id/PinDel"
            boolean result = false;
            if (key == "\b")
                result = clickButtonInContainer(pinContainer, "ro.cec.android.mtoken:id/PinDel", null, "DEL");
            else if (key == "\n")
                result = clickButtonInContainer(pinContainer, "ro.cec.android.mtoken:id/PinOk", null, "ENTER");
            else if (key != null)
                result = clickButtonInContainer(pinContainer, "ro.cec.android.mtoken:id/Pin" + key, null, key);
            pinContainer.recycle();
            return result;
        }
        pinContainer = getContainer(pinContainerWiseApp);
        if (pinContainer != null) {
            // Wise PIN entry screen has
            // <node text="[0..9]" resource-id="com.transferwise.android:id/button[0..9]" content-desc=""
            //   class="android.widget.Button" package="com.transferwise.android"
            //   enabled="true" clickable="true" focusable="true"
            //   checkable="false" scrollable="false" long-clickable="false" password="false"
            //   ...>
            // enter/backspace have the resource-id's
            //   "com.transferwise.android:id/button_accept" / "com.transferwise.android:id/button_backspace"
            boolean result = false;
            if (key == "\b")
                result = clickButtonInContainer(pinContainer, "com.transferwise.android:id/button_backspace", null, "DEL");
            else if (key == "\n")
                result = clickButtonInContainer(pinContainer, "com.transferwise.android:id/button_accept", null, "ENTER");
            else if (key != null)
                result = clickButtonInContainer(pinContainer, "com.transferwise.android:id/button" + key, null, key);
            pinContainer.recycle();
            return result;
        }
        /*
        // Revolut doesn't work
        pinContainer = getContainer(pinContainerRevolutApp);
        if (pinContainer != null) {
            Log.d(TAG, "Revolut PIN entry");
            // Revolut PIN entry screen has
            // <node text="" resource-id="com.revolut.revolut:id/internalViewKeypadView_button[0..9]" content-desc="[0..9]"
            //   class="android.view.Button" package="com.revolut.revolut"
            //   enabled="true" clickable="true" focusable="true"
            //   checkable="false" scrollable="false" long-clickable="false" password="false"
            //   ...>
            // backspace has the resource-id
            //   "com.revolut.revolut:id/internalViewKeypadView_buttonActionEnd"
            boolean result = false;
            if (key == "\b")
                result = clickButtonInContainer(pinContainer, "com.revolut.revolut:id/internalViewKeypadView_buttonActionEnd", null, "DEL");
            if (key != null && key != "\n")
                result = clickButtonInContainer(pinContainer, "com.revolut.revolut:id/internalViewKeypadView_button" + key, null, key);
            pinContainer.recycle();
            return result;
        }
        */
        /*
        // BT Pay doesn't work
        pinContainer = getContainer(pinContainerBTPayApp);
        if (pinContainer != null) {
            Log.d(TAG, "BTPay PIN entry");
            // BT Pay PIN entry screen is even worse than CEC; you get
            // <node text="[0..9]" resource-id="" content-desc="[0..9]"
            //   class="android.widget.TextView" package="ro.btrl.pay"
            //   enabled="true" clickable="false" focusable="false"
            //   checkable="false" scrollable="false" long-clickable="false" password="false"
            //   ...>
            // and the node that's actually clickable is the immediate predecessor
            // backspace has no text, no resId, class "android.widget.Button"
            //   have to detect is as the next clickable node after 0
            boolean result = false;
            if (key == "\b")
                result = clickButtonInContainer(pinContainer, null, "0", "DEL", 1);
            if (key != null && key != "\n")
                result = clickButtonInContainer(pinContainer, null, key, key, -1);
            pinContainer.recycle();
            return result;
        }
        */
        return false;
    }
}
