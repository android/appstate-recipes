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
import com.example.appstaterecipes.browser.state.TabSummary

/**
 * Computes the target index to move [draggedTabId] to within [tabs] when the drag pointer is at
 * [dragX] (in the same coordinate space as [tabBounds]), or `null` if no neighbor tab's horizontal
 * midpoint has been crossed yet.
 */
internal fun calculateReorderTargetIndex(
    tabs: List<TabSummary>,
    draggedTabId: String,
    dragX: Float,
    tabBounds: Map<String, Rect>,
): Int? {
    val fromIndex = tabs.indexOfFirst { it.tabId == draggedTabId }
    if (fromIndex == -1 || tabs.size <= 1) return null
    val draggedBounds = tabBounds[draggedTabId] ?: return null
    val draggedCenter = draggedBounds.center.x

    return if (dragX > draggedCenter) {
        var targetIndex: Int? = null
        for (i in (fromIndex + 1)..tabs.lastIndex) {
            val bounds = tabBounds[tabs[i].tabId] ?: continue
            if (dragX > bounds.center.x) {
                targetIndex = i
            } else {
                break
            }
        }
        targetIndex
    } else if (dragX < draggedCenter) {
        var targetIndex: Int? = null
        for (i in (fromIndex - 1) downTo 0) {
            val bounds = tabBounds[tabs[i].tabId] ?: continue
            if (dragX < bounds.center.x) {
                targetIndex = i
            } else {
                break
            }
        }
        targetIndex
    } else {
        null
    }
}

/**
 * Non-observable state container for tracking drag geometry, pointer coordinates,
 * and layout lock flags across drag-and-drop frames without triggering composable recompositions.
 */
internal class TabBarDragState {
    var activeDragXInRoot: Float? = null
    var tabRowBoundsInRoot: Rect? = null
    val tabBoundsInRoot = mutableMapOf<String, Rect>()
    var awaitingLayoutAfterReorder: Boolean = false
}
