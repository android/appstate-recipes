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

package com.example.appstaterecipes.browser.window

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserWindowLauncherTest {

    @Test
    fun supportsSplitLaunch_smallerDisplaysDisableSplitLaunch() {
        // Portrait phone (Pixel 9 Pro: ~427dp x 952dp) -> Compact width
        assertFalse(
            BrowserWindowLauncher.supportsSplitLaunch(
                displayWidthDp = 427f,
                displayHeightDp = 952f,
            ),
        )

        // Portrait tablet (800dp x 1280dp) -> Medium width (< 840dp)
        assertFalse(
            BrowserWindowLauncher.supportsSplitLaunch(
                displayWidthDp = 800f,
                displayHeightDp = 1280f,
            ),
        )

        // Invalid / zero display dimensions
        assertFalse(
            BrowserWindowLauncher.supportsSplitLaunch(
                displayWidthDp = 0f,
                displayHeightDp = 0f,
            ),
        )
    }

    @Test
    fun supportsSplitLaunch_expandedDisplaysAndDesktopEnableSplitLaunch() {
        // Unfolded foldable inner screen (852dp x 883dp / 841dp x 701dp) -> Expanded width
        assertTrue(
            BrowserWindowLauncher.supportsSplitLaunch(
                displayWidthDp = 852f,
                displayHeightDp = 883f,
            ),
        )
        assertTrue(
            BrowserWindowLauncher.supportsSplitLaunch(
                displayWidthDp = 841f,
                displayHeightDp = 701f,
            ),
        )

        // Landscape tablet (1280dp x 800dp) -> Expanded width
        assertTrue(
            BrowserWindowLauncher.supportsSplitLaunch(
                displayWidthDp = 1280f,
                displayHeightDp = 800f,
            ),
        )

        // Desktop windowing with visible caption bar -> enabled even on narrower displays
        assertTrue(
            BrowserWindowLauncher.supportsSplitLaunch(
                displayWidthDp = 720f,
                displayHeightDp = 900f,
                isCaptionBarVisible = true,
            ),
        )
    }
}
