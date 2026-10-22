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

package com.example.appstaterecipes.browser

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Navigation 3 back-stack key representing an individual entry within a tab's back stack.
 *
 * @property tabId The ID of the tab this back-stack item belongs to.
 * @property index The 0-based depth/position of this entry within the tab's back-stack list
 *                 (0 being the root destination of the tab).
 * @property route The destination [BrowserRoute] displayed by this back-stack entry.
 */
@Serializable
data class BackStackItem(
    val tabId: String,
    val index: Int,
    val route: BrowserRoute,
) : NavKey {
    /**
     * Unique strongly-typed identifier for this back-stack entry ([EntryId]), used for
     * persistent keys such as [EntryScrollOffsetsKey].
     */
    val entryId: String get() = EntryId.of(tabId, index).value

    /**
     * Bundle-saveable content key (`"$entryId:$route"`) for Navigation 3's
     * `SaveableStateHolderNavEntryDecorator`.
     */
    val contentKey: String get() = "$entryId:$route"

    companion object {
        fun tabIdFromContentKey(contentKey: Any?): String? =
            (contentKey as? String)?.substringBefore(':', missingDelimiterValue = "")
                ?.takeIf { it.isNotEmpty() }
    }
}

/**
 * State representing a single browser window.
 *
 * @property windowId Unique identifier for this window.
 * @property tabIds Ordered list of tab IDs open within this window. Must contain at least one tab.
 * @property activeTabId The ID of the currently selected tab in [tabIds].
 */
@Serializable
data class WindowState(
    val windowId: String,
    val tabIds: List<String>,
    val activeTabId: String,
) {
    init {
        require(tabIds.isNotEmpty()) { "WindowState must have at least one tabId." }
        require(activeTabId in tabIds) { "activeTabId ($activeTabId) must be in tabIds ($tabIds)." }
    }
}

/**
 * Summary information for a single tab used by the UI tab bar and switcher.
 *
 * @property tabId Unique ID of the tab.
 * @property isSelected Whether this tab is currently the active tab in its window.
 * @property stackDepth The number of back-stack entries currently on this tab.
 * @property currentRoute The top-most [BrowserRoute] currently visible in this tab.
 */
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
data class BrowserWindowUiState(
    val windowId: String,
    val displayName: String,
    val activeTabId: String,
    val tabs: List<TabSummary>,
    val activeBackStack: List<BrowserRoute>,
    val allWindows: List<WindowSummary>,
) {
    val activeBackStackItems: List<BackStackItem> = activeBackStack.mapIndexed { index, route ->
        BackStackItem(
            tabId = activeTabId,
            index = index,
            route = route,
        )
    }
    val otherWindows: List<WindowSummary> = allWindows.filter { it.windowId != windowId }
    val canGoBack: Boolean get() = activeBackStack.size > 1
    val canPopOut: Boolean get() = tabs.size > 1
}
