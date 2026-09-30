/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.appstaterecipes.sample

import android.app.Activity
import android.os.Build
import android.view.Display

/**
 * Display refresh-rate helpers for demo purposes.
 *
 * In production apps, locking the display mode to maximum refresh rate is generally discouraged
 * to preserve battery life. For this recipe demo, locking the refresh rate ensures that LTPO
 * variable refresh rate displays do not aggressively down-throttle to 60, 40, 30, or 24Hz while
 * viewing real-time high-frequency sensor updates without touching the screen.
 */
internal object Utils {

    /**
     * Resolves the maximum supported refresh rate for the current display resolution on API 30+,
     * or defaults to 60Hz.
     */
    fun getMaxRefreshRate(activity: Activity): Float {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val currentDisplay = activity.display
            return getMatchingHighRefreshMode(currentDisplay)?.refreshRate
                ?: currentDisplay?.supportedModes?.maxOfOrNull { it.refreshRate }
                ?: 60f
        }
        return 60f
    }

    /**
     * Locks the activity window to the highest refresh rate supported at the current screen resolution,
     * and disables Adaptive Refresh Rate (ARR) power savings balancing on Android 15+.
     */
    fun lockToHighRefreshRate(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val highRefreshMode = getMatchingHighRefreshMode(activity.display)
            if (highRefreshMode != null) {
                activity.window.attributes = activity.window.attributes.apply {
                    preferredDisplayModeId = highRefreshMode.modeId
                }
            }

            // On Android 15+ (API 35+ / Vanilla Ice Cream), disable Adaptive Refresh Rate (ARR)
            // power savings balancing so Choreographer and SurfaceFlinger don't throttle rendering
            // down to 60 or 40Hz when there are no physical touch events on screen.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                activity.window.isFrameRatePowerSavingsBalanced = false
            }
        }
    }

    private fun getMatchingHighRefreshMode(display: Display?): Display.Mode? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || display == null) return null
        val currentMode = display.mode ?: return null
        return display.supportedModes
            ?.filter { it.physicalWidth == currentMode.physicalWidth && it.physicalHeight == currentMode.physicalHeight }
            ?.maxByOrNull { it.refreshRate }
    }
}
