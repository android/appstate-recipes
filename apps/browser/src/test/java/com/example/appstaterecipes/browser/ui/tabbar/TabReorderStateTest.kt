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

package com.example.appstaterecipes.browser.ui.tabbar

import androidx.compose.ui.geometry.Rect
import com.example.appstaterecipes.browser.model.BrowserRoute
import com.example.appstaterecipes.browser.state.TabSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TabReorderStateTest {

    private fun tabSummary(id: String) = TabSummary(
        tabId = id,
        currentRoute = BrowserRoute.Home,
        stackDepth = 1,
        isSelected = false,
    )

    private val threeTabBounds = mapOf(
        "tab-1" to Rect(0f, 0f, 100f, 36f),   // center = 50
        "tab-2" to Rect(106f, 0f, 206f, 36f), // center = 156
        "tab-3" to Rect(212f, 0f, 312f, 36f), // center = 262
    )

    @Test
    fun calculateReorderTargetIndex_draggingRight_returnsFurthestCrossedMidpoint() {
        val tabs = listOf(tabSummary("tab-1"), tabSummary("tab-2"), tabSummary("tab-3"))

        assertNull(
            calculateReorderTargetIndex(
                tabs = tabs,
                draggedTabId = "tab-1",
                dragX = 140f,
                tabBounds = threeTabBounds,
            ),
        )
        assertEquals(
            1,
            calculateReorderTargetIndex(
                tabs = tabs,
                draggedTabId = "tab-1",
                dragX = 160f,
                tabBounds = threeTabBounds,
            ),
        )
        assertEquals(
            2,
            calculateReorderTargetIndex(
                tabs = tabs,
                draggedTabId = "tab-1",
                dragX = 270f,
                tabBounds = threeTabBounds,
            ),
        )
    }

    @Test
    fun calculateReorderTargetIndex_draggingLeft_returnsFurthestCrossedMidpoint() {
        val tabs = listOf(tabSummary("tab-1"), tabSummary("tab-2"), tabSummary("tab-3"))

        assertNull(
            calculateReorderTargetIndex(
                tabs = tabs,
                draggedTabId = "tab-3",
                dragX = 170f,
                tabBounds = threeTabBounds,
            ),
        )
        assertEquals(
            1,
            calculateReorderTargetIndex(
                tabs = tabs,
                draggedTabId = "tab-3",
                dragX = 150f,
                tabBounds = threeTabBounds,
            ),
        )
        assertEquals(
            0,
            calculateReorderTargetIndex(
                tabs = tabs,
                draggedTabId = "tab-3",
                dragX = 40f,
                tabBounds = threeTabBounds,
            ),
        )
    }

    @Test
    fun calculateReorderTargetIndex_singleTabOrUnknownDraggedTab_returnsNull() {
        val singleTab = listOf(tabSummary("tab-1"))
        assertNull(
            calculateReorderTargetIndex(
                tabs = singleTab,
                draggedTabId = "tab-1",
                dragX = 200f,
                tabBounds = threeTabBounds,
            ),
        )

        val tabs = listOf(tabSummary("tab-1"), tabSummary("tab-2"))
        assertNull(
            calculateReorderTargetIndex(
                tabs = tabs,
                draggedTabId = "tab-missing",
                dragX = 200f,
                tabBounds = threeTabBounds,
            ),
        )
    }

    @Test
    fun calculateReorderTargetIndex_missingBoundsForDraggedOrNeighborTab_handlesGracefully() {
        val tabs = listOf(tabSummary("tab-1"), tabSummary("tab-2"), tabSummary("tab-3"))

        // Missing dragged tab bounds -> null
        assertNull(
            calculateReorderTargetIndex(
                tabs = tabs,
                draggedTabId = "tab-1",
                dragX = 270f,
                tabBounds = threeTabBounds - "tab-1",
            ),
        )

        // Missing middle neighbor bounds skips unmeasured neighbor without crashing
        assertEquals(
            2,
            calculateReorderTargetIndex(
                tabs = tabs,
                draggedTabId = "tab-1",
                dragX = 270f,
                tabBounds = threeTabBounds - "tab-2",
            ),
        )
    }
}
