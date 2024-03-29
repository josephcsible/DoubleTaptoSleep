/*
An Xposed module that turns off the display when you double-tap on the status bar or lockscreen
Copyright (C) 2022  Joseph C. Sible

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <https://www.gnu.org/licenses/>.
*/

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

        // There's only one constructor, but it takes 66 parameters, so it's much cleaner to just use hookAllConstructors instead of findAndHookConstructor.
        XposedBridge.hookAllConstructors(XposedHelpers.findClass("com.android.systemui.shade.NotificationPanelViewController", lpparam.classLoader), new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                Object self = param.thisObject;
                Object mQsController = XposedHelpers.getObjectField(self, "mQsController");
                Object mView = XposedHelpers.getObjectField(self, "mView");
                Context context = (Context) XposedHelpers.callMethod(mView, "getContext");
                PowerManager mPowerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
                Class<?> clazz = XposedHelpers.getObjectField(mView, "mTouchHandler").getClass();

                GestureDetector gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onDoubleTap(MotionEvent e) {
                        boolean mPulsing = (boolean) XposedHelpers.getObjectField(self, "mPulsing");
                        boolean mDozing = (boolean) XposedHelpers.getObjectField(self, "mDozing");
                        int mBarState = (int) XposedHelpers.getObjectField(self, "mBarState");
                        float mQuickQsHeaderHeight = (float) XposedHelpers.getObjectField(mQsController, "mQuickQsHeaderHeight");
                        if (mPulsing || mDozing || (mBarState != 1 && e.getY() >= mQuickQsHeaderHeight))
                            return false;
                        XposedHelpers.callMethod(mPowerManager, "goToSleep", e.getEventTime());
                        return true;
                    }
                });

                XposedHelpers.findAndHookMethod(clazz, "onTouch", View.class, MotionEvent.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        gestureDetector.onTouchEvent((MotionEvent) param.args[1]);
                    }
                });

                XposedHelpers.findAndHookMethod(XposedHelpers.findClass("com.android.systemui.statusbar.phone.PhoneStatusBarViewController", lpparam.classLoader), "onTouch", MotionEvent.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        gestureDetector.onTouchEvent((MotionEvent) param.args[0]);
                    }
                });
            }
        });
    }
}
