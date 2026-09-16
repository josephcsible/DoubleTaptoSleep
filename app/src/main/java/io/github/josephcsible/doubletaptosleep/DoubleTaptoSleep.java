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

import android.app.Application;
import android.content.Context;
import android.os.PowerManager;
import android.view.GestureDetector;
import android.view.MotionEvent;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class DoubleTaptoSleep implements IXposedHookLoadPackage {
    @Override
    public void handleLoadPackage(final LoadPackageParam lpparam) {
        if (!lpparam.packageName.equals("com.android.systemui"))
            return;

        XposedHelpers.findAndHookMethod(Application.class, "attach", Context.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                Context context = (Context) param.args[0];
                PowerManager mPowerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
                int reason = XposedHelpers.getStaticIntField(PowerManager.class, "GO_TO_SLEEP_REASON_POWER_BUTTON");

                GestureDetector gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onDoubleTap(MotionEvent e) {
                        XposedHelpers.callMethod(mPowerManager, "goToSleep", e.getEventTime(), reason, 0);
                        return true;
                    }
                });

                XposedHelpers.findAndHookMethod(XposedHelpers.findClass("com.android.systemui.statusbar.phone.PhoneStatusBarView", lpparam.classLoader), "onTouchEvent", MotionEvent.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        gestureDetector.onTouchEvent((MotionEvent) param.args[0]);
                    }
                });
            }
        });
    }
}
