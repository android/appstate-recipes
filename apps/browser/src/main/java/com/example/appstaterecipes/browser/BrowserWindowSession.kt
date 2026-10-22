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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember

/**
 * Window-scoped session that provides a cohesive interface for UI interactions within
 * a single browser window.
 *
 * This eliminates the need to pass `windowId` into every navigator action and currying
 * numerous lambdas down the Compose UI tree.
 */
@Immutable
interface BrowserWindowSession {
    val windowId: String
    val uiState: State<BrowserWindowUiState?>

    fun navigate(route: BrowserRoute)
    fun goBack()

    fun newTab(initialRoute: BrowserRoute = BrowserRoute.Home): String
    fun selectTab(tabId: String)
    fun closeTab(tabId: String)
    fun moveTab(tabId: String, toIndex: Int)
    fun popOutTab(tabId: String): String?

    /**
     * Receives a tab dragged or moved from another window into this window.
     *
     * @return true if the transfer moved the last tab out of [sourceWindowId] into this
     * window and this window was not yet attached to a task, indicating that the caller/UI
     * should switch task/window focus to this window.
     */
    fun receiveTabFromWindow(sourceWindowId: String, tabId: String): Boolean

    fun windowSummaries(): List<WindowSummary>

    /**
     * Checks if [entryId] is currently an active entry on any tab's back stack.
     */
    fun isActiveBackStackEntry(entryId: String): Boolean
}

/**
 * Default implementation of [BrowserWindowSession] that delegates to [BrowserNavigator].
 */
internal class DefaultBrowserWindowSession(
    private val navigator: BrowserNavigator,
    override val windowId: String,
) : BrowserWindowSession {

    override val uiState: State<BrowserWindowUiState?>
        get() = navigator.windowUiState(windowId)

    override fun navigate(route: BrowserRoute) {
        navigator.navigate(windowId, route)
    }

    override fun goBack() {
        navigator.goBack(windowId)
    }

    override fun newTab(initialRoute: BrowserRoute): String {
        return navigator.newTab(windowId, initialRoute)
    }

    override fun selectTab(tabId: String) {
        navigator.selectTab(windowId, tabId)
    }

    override fun closeTab(tabId: String) {
        navigator.closeTab(windowId, tabId)
    }

    override fun moveTab(tabId: String, toIndex: Int) {
        navigator.moveTab(windowId, tabId, toIndex)
    }

    override fun popOutTab(tabId: String): String? {
        return navigator.popOutTabToNewWindow(windowId, tabId)
    }

    override fun receiveTabFromWindow(sourceWindowId: String, tabId: String): Boolean {
        return navigator.receiveTabFromWindow(
            sourceWindowId = sourceWindowId,
            tabId = tabId,
            targetWindowId = windowId,
        )
    }

    override fun windowSummaries(): List<WindowSummary> {
        return navigator.windowSummaries()
    }

    override fun isActiveBackStackEntry(entryId: String): Boolean {
        return navigator.isActiveBackStackEntry(entryId)
    }
}

/**
 * Remembers a [BrowserWindowSession] bound to the given [navigator] and [windowId].
 */
@Composable
fun rememberBrowserWindowSession(
    navigator: BrowserNavigator,
    windowId: String,
): BrowserWindowSession {
    return remember(navigator, windowId) {
        navigator.windowSession(windowId)
    }
}

