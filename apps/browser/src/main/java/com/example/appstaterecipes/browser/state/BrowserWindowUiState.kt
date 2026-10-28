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

package com.example.appstaterecipes.browser.state

import androidx.compose.runtime.Immutable
import com.example.appstaterecipes.browser.model.BackStackItem
import com.example.appstaterecipes.browser.model.BrowserRoute

/**
 * Summary information for a single tab used by the UI tab bar and switcher.
 *
 * @property tabId Unique ID of the tab.
 * @property isSelected Whether this tab is currently the active tab in its window.
 * @property stackDepth The number of back-stack entries currently on this tab.
 * @property currentRoute The top-most [BrowserRoute] currently visible in this tab.
 */
@Immutable
data class TabSummary(
    val tabId: String,
    val isSelected: Boolean,
    val stackDepth: Int,
    val currentRoute: BrowserRoute = BrowserRoute.Home,
) {
    val title: String get() = currentRoute.title
}

/**
 * Summary information for a window, used by the multi-window switcher menu.
 *
 * @property windowId Unique ID of the window.
 * @property displayName User-friendly label for the window (e.g. "Window 1").
 * @property tabCount Number of tabs currently open in this window.
 * @property firstTabTitles The titles of the first few tabs, displayed in the switcher overview.
 * @property isAttachedToActivity Whether this window is currently attached to a live Activity task.
 */
@Immutable
data class WindowSummary(
    val windowId: String,
    val displayName: String,
    val tabCount: Int,
    val firstTabTitles: List<String>,
    val isAttachedToActivity: Boolean = false,
)

/**
 * Consolidated UI state for a single browser window screen.
 *
 * @property windowId The ID of the window being displayed.
 * @property displayName User-friendly label for the window.
 * @property activeTabId The ID of the currently selected tab.
 * @property tabs The summaries of all tabs in this window.
 * @property activeBackStack The list of [BrowserRoute] destinations on the active tab's stack.
 * @property allWindows Summaries of all known windows across the application.
 * @property activeBackStackItems The Navigation 3 [BackStackItem] keys for the active tab's stack.
 */
@Immutable
data class BrowserWindowUiState(
    val windowId: String,
    val displayName: String,
    val activeTabId: String,
    val tabs: List<TabSummary>,
    val activeBackStack: List<BrowserRoute>,
    val allWindows: List<WindowSummary>,
) {
    val activeBackStackItems: List<BackStackItem> = activeBackStack.mapIndexed { stackIndex, route ->
        BackStackItem(
            tabId = activeTabId,
            stackIndex = stackIndex,
            route = route,
        )
    }
    val otherWindows: List<WindowSummary> = allWindows.filter { it.windowId != windowId }
    val canGoBack: Boolean get() = activeBackStack.size > 1
    val canPopOut: Boolean get() = tabs.size > 1
}
