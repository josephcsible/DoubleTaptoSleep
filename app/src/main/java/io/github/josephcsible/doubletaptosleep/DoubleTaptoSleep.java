package io.github.josephcsible.doubletaptosleep;

import android.content.Context;
import android.os.PowerManager;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class DoubleTaptoSleep implements IXposedHookLoadPackage {
    @Override
    public void handleLoadPackage(final LoadPackageParam lpparam) {
        if (!lpparam.packageName.equals("com.android.systemui"))
            return;

        // createTouchHandler is a virtual method that Google chose to call from the parent class's constructor for some reason.
        // That means mView and mPowerManager are null right now, so just save the class and do the real hooking after the child constructor runs.
        Class<?>[] clazz = new Class<?>[1];
        XposedHelpers.findAndHookMethod("com.android.systemui.statusbar.phone.NotificationPanelViewController", lpparam.classLoader, "createTouchHandler", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                clazz[0] = param.getResult().getClass();
            }
        });

        // There's only one constructor, but it takes 66 parameters, so it's much cleaner to just use hookAllConstructors instead of findAndHookConstructor.
        XposedBridge.hookAllConstructors(XposedHelpers.findClass("com.android.systemui.statusbar.phone.NotificationPanelViewController", lpparam.classLoader), new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                Object self = param.thisObject;
                Object mView = XposedHelpers.getObjectField(param.thisObject, "mView");
                Context context = (Context) XposedHelpers.callMethod(mView, "getContext");
                PowerManager mPowerManager = (PowerManager) XposedHelpers.getObjectField(param.thisObject, "mPowerManager");

                GestureDetector gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onDoubleTap(MotionEvent e) {
                        boolean mPulsing = (boolean) XposedHelpers.getObjectField(self, "mPulsing");
                        boolean mDozing = (boolean) XposedHelpers.getObjectField(self, "mDozing");
                        int mBarState = (int) XposedHelpers.getObjectField(param.thisObject, "mBarState");
                        float mQuickQsOffsetHeight = (float) XposedHelpers.getObjectField(self, "mQuickQsOffsetHeight");
                        if (mPulsing || mDozing || (mBarState != 1 && e.getY() >= mQuickQsOffsetHeight))
                            return false;
                        XposedHelpers.callMethod(mPowerManager, "goToSleep", e.getEventTime());
                        return true;
                    }
                });

                XposedHelpers.findAndHookMethod(clazz[0], "onTouch", View.class, MotionEvent.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        gestureDetector.onTouchEvent((MotionEvent) param.args[1]);
                    }
                });
            }
        });
    }
}
