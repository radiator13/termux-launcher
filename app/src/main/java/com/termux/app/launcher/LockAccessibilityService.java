package com.termux.app.launcher;

import android.accessibilityservice.AccessibilityService;
import android.os.Build;
import android.view.accessibility.AccessibilityEvent;

public class LockAccessibilityService extends AccessibilityService {

    private static LockAccessibilityService sInstance;

    @Override
    protected void onServiceConnected() {
        sInstance = this;
    }

    @Override
    public void onDestroy() {
        if (sInstance == this) {
            sInstance = null;
        }
        super.onDestroy();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }

    public static boolean lockScreen() {
        LockAccessibilityService service = sInstance;
        return service != null
            && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
            && service.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
    }
}
