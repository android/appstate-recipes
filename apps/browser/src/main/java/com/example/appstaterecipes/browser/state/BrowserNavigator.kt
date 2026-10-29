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

import androidx.appstate.datastore.PersistToDataStore
import androidx.appstate.statestore.StateStore
import androidx.appstate.statestore.StateStoreKey
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.snapshots.Snapshot
import com.example.appstaterecipes.browser.BrowserActivity
import com.example.appstaterecipes.browser.model.BrowserRoute
import com.example.appstaterecipes.browser.model.EntryId
import com.example.appstaterecipes.browser.model.WindowState
import kotlinx.serialization.Serializable
import java.util.UUID

// [START browser_statestore_keys]
@Serializable
private object WindowsKey : StateStoreKey<Map<String, WindowState>>(defaultValue = emptyMap()),
    PersistToDataStore

@Serializable
private object TabBackStacksKey :
    StateStoreKey<Map<String, List<BrowserRoute>>>(defaultValue = emptyMap()),
    PersistToDataStore

private object ActiveWindowTasksKey : StateStoreKey<Map<String, Int>>(defaultValue = emptyMap())
// [END browser_statestore_keys]
/**
 * Stateless facade that encapsulates [StateStore] and coordinates multi-tab
 * Navigation 3 back stacks across an arbitrary number of [BrowserActivity] windows.
 *
 * Because all state lives inside [stateStore] under `private` singleton keys ([WindowsKey],
 * [TabBackStacksKey], and [EntryScrollOffsetsKey]), multiple [BrowserNavigator] instances wrapping
 * the same [StateStore] operate on the exact same windows and tabs, and multiple recipes can
 * share that [StateStore] without key collisions.
 */
@Stable
class BrowserNavigator(
    private val stateStore: StateStore = StateStore(),
    private val idGenerator: () -> String = { UUID.randomUUID().toString().take(8) },
    val scrollStateHolder: EntryScrollStateHolder = EntryScrollStateHolder(stateStore),
) {
    /**
     * Registers the persisted browser keys ([WindowsKey], [TabBackStacksKey], and
     * [EntryScrollOffsetsKey]) in [StateStore] so that [androidx.appstate.datastore.syncToDataStore]
     * can restore them from disk before [BrowserActivity] is opened for the first time.
     */
    fun primeKeys() {
        stateStore.getState(WindowsKey)
        stateStore.getState(TabBackStacksKey)
        scrollStateHolder.primeKey()
    }

    /**
     * Ensures [windowId] exists in [StateStore]. If it does not exist yet, initializes it
     * with a single tab starting at [initialRoute].
     */
    internal fun ensureWindow(
        windowId: String,
        initialRoute: BrowserRoute = BrowserRoute.Home,
    ) {
        Snapshot.withMutableSnapshot {
            if (windowId !in stateStore.getState(WindowsKey).value) {
                newTab(windowId, initialRoute)
            }
        }
    }

    /**
     * Resolves or generates a window ID, ensures the window exists, and attaches [taskId]
     * to it inside a single atomic snapshot update.
     *
     * @param requestedWindowId An explicit window ID to open, or `null` to attach to an existing
     *                          unattached window or generate a fresh window ID.
     * @param taskId The system task ID of the hosting Activity.
     * @return The resolved window ID that was opened and attached.
     */
    fun openWindow(
        requestedWindowId: String? = null,
        taskId: Int,
    ): String {
        return Snapshot.withMutableSnapshot {
            val resolvedWindowId = requestedWindowId
                ?: firstUnattachedWindowId()
                ?: "win-${idGenerator()}"

            ensureWindow(resolvedWindowId)
            attachWindowTask(resolvedWindowId, taskId)

            resolvedWindowId
        }
    }

    /**
     * Returns a consolidated [State] of [BrowserWindowUiState] for [windowId], or `null` if the
     * window has been closed (e.g. after its last tab was closed or moved to another window).
     */
    // [START browser_derived_ui_state]
    fun windowUiState(windowId: String): State<BrowserWindowUiState?> = derivedStateOf {
        val windows = stateStore.getState(WindowsKey).value
        val window = windows[windowId] ?: return@derivedStateOf null
        val tabBackStacks = stateStore.getState(TabBackStacksKey).value
        val activeBackStack = tabBackStacks[window.activeTabId].orEmpty()
        if (activeBackStack.isEmpty()) return@derivedStateOf null

        val allSummaries = buildWindowSummaries(
            windows = windows,
            tabBackStacks = tabBackStacks,
            taskIdByWindowId = stateStore.getState(ActiveWindowTasksKey).value,
        )

        BrowserWindowUiState(
            windowId = windowId,
            displayName = window.displayName,
            activeTabId = window.activeTabId,
            tabs = buildTabSummaries(window, tabBackStacks),
            activeBackStack = activeBackStack,
            allWindows = allSummaries,
        )
    }
    // [END browser_derived_ui_state]

    private fun buildTabSummaries(
        window: WindowState,
        tabBackStacks: Map<String, List<BrowserRoute>>,
    ): List<TabSummary> = window.tabIds.mapNotNull { tabId ->
        val backStack = tabBackStacks[tabId] ?: return@mapNotNull null
        val currentRoute = backStack.lastOrNull() ?: return@mapNotNull null
        TabSummary(
            tabId = tabId,
            isSelected = tabId == window.activeTabId,
            stackDepth = backStack.size,
            currentRoute = currentRoute,
        )
    }

    private fun buildWindowSummaries(
        windows: Map<String, WindowState>,
        tabBackStacks: Map<String, List<BrowserRoute>>,
        taskIdByWindowId: Map<String, Int>,
    ): List<WindowSummary> {
        return windows.values.map { window ->
            val firstTabTitles = window.tabIds.take(2).mapNotNull { tabId ->
                tabBackStacks[tabId]?.lastOrNull()?.title
            }
            WindowSummary(
                windowId = window.windowId,
                displayName = window.displayName,
                tabCount = window.tabIds.size,
                firstTabTitles = firstTabTitles,
                isAttachedToActivity = window.windowId in taskIdByWindowId,
            )
        }
    }

    /**
     * Records that [windowId] is currently attached to a live [BrowserActivity] with [taskId].
     * Also clears any previous window mapping that was associated with [taskId].
     */
    internal fun attachWindowTask(windowId: String, taskId: Int) {
        stateStore.updateState(ActiveWindowTasksKey) { windowTasks ->
            windowTasks.filterValues { it != taskId } + (windowId to taskId)
        }
    }

    /**
     * Removes the active task association for [windowId] if it matches [taskId].
     */
    fun detachWindowTask(windowId: String, taskId: Int) {
        stateStore.updateState(ActiveWindowTasksKey) { windowTasks ->
            if (windowTasks[windowId] == taskId) windowTasks - windowId else windowTasks
        }
    }

    /**
     * Returns the system task ID of the live [BrowserActivity] currently hosting [windowId],
     * or `null` if [windowId] is not currently attached to any live Activity window.
     */
    internal fun taskIdForWindow(windowId: String): Int? =
        stateStore.getState(ActiveWindowTasksKey).value[windowId]

    /**
     * Returns the ID of the first window that is not currently attached to a live Activity,
     * or `null` if no unattached windows currently exist in [StateStore].
     * Useful when launching the browser activity without an explicit target window ID.
     */
    internal fun firstUnattachedWindowId(): String? {
        val windows = stateStore.getState(WindowsKey).value
        val taskIdByWindowId = stateStore.getState(ActiveWindowTasksKey).value
        return windows.keys.firstOrNull { windowId -> windowId !in taskIdByWindowId }
    }

    /**
     * Opens a new tab in [windowId] starting at [initialRoute] and selects it.
     */
    // [START browser_atomic_updates]
    fun newTab(
        windowId: String,
        initialRoute: BrowserRoute = BrowserRoute.Home,
    ): String {
        val newTabId = "tab-${idGenerator()}"
        Snapshot.withMutableSnapshot {
            stateStore.updateState(TabBackStacksKey) { tabBackStacks ->
                tabBackStacks + (newTabId to listOf(initialRoute))
            }
            stateStore.updateState(WindowsKey) { windows ->
                val window = windows[windowId]
                if (window == null) {
                    windows + (windowId to WindowState(
                        windowId = windowId,
                        tabIds = listOf(newTabId),
                        activeTabId = newTabId,
                        windowNumber = nextWindowNumber(windows),
                    ))
                } else {
                    windows + (windowId to window.copy(
                        tabIds = window.tabIds + newTabId,
                        activeTabId = newTabId,
                    ))
                }
            }
        }
        return newTabId
    }

    // [START_EXCLUDE]
    /**
     * Switches the active tab in [windowId] to [tabId].
     */
    fun selectTab(windowId: String, tabId: String) {
        stateStore.updateState(WindowsKey) { windows ->
            val window = windows[windowId] ?: return@updateState windows
            if (tabId !in window.tabIds || window.activeTabId == tabId) return@updateState windows
            windows + (windowId to window.copy(activeTabId = tabId))
        }
    }

    /**
     * Reorders [tabId] within [windowId] to [toIndex].
     *
     * Because back stacks ([TabBackStacksKey]) and scroll offsets ([EntryScrollOffsetsKey]) are keyed
     * by `tabId` rather than tab index, reordering only updates [WindowState.tabIds].
     */
    fun moveTab(
        windowId: String,
        tabId: String,
        toIndex: Int,
    ) {
        stateStore.updateState(WindowsKey) { windows ->
            val window = windows[windowId] ?: return@updateState windows
            val fromIndex = window.tabIds.indexOf(tabId)
            if (fromIndex == -1) return@updateState windows
            val targetIndex = toIndex.coerceIn(0, window.tabIds.lastIndex)
            if (fromIndex == targetIndex) return@updateState windows

            val reorderedTabIds = window.tabIds.toMutableList().apply {
                removeAt(fromIndex)
                add(targetIndex, tabId)
            }
            windows + (windowId to window.copy(tabIds = reorderedTabIds))
        }
    }

    /**
     * Pushes [route] onto the active tab's back stack in [windowId].
     */
    fun navigate(windowId: String, route: BrowserRoute) {
        Snapshot.withMutableSnapshot {
            val activeTabId = stateStore.getState(WindowsKey).value[windowId]?.activeTabId
                ?: return@withMutableSnapshot
            stateStore.updateState(TabBackStacksKey) { tabBackStacks ->
                val currentBackStack = tabBackStacks[activeTabId] ?: return@updateState tabBackStacks
                tabBackStacks + (activeTabId to (currentBackStack + route))
            }
        }
    }

    /**
     * Pops the top route from [windowId]'s active tab back stack.
     *
     * - If the active tab has > 1 route on its stack, pops the top route and clears its saved
     *   scroll offset.
     * - If the active tab is at its root route (size == 1), closes the tab (which also removes
     *   the window if it was the last remaining tab in [windowId]).
     */
    fun goBack(windowId: String) {
        Snapshot.withMutableSnapshot {
            val window = stateStore.getState(WindowsKey).value[windowId]
                ?: return@withMutableSnapshot
            val activeTabId = window.activeTabId
            val currentBackStack = stateStore.getState(TabBackStacksKey).value[activeTabId].orEmpty()

            if (currentBackStack.size > 1) {
                val poppedEntryId = EntryId.of(activeTabId, currentBackStack.lastIndex).value
                stateStore.updateState(TabBackStacksKey) { tabBackStacks ->
                    tabBackStacks + (activeTabId to currentBackStack.dropLast(1))
                }
                scrollStateHolder.clearEntry(poppedEntryId)
            } else if (window.tabIds.size > 1) {
                closeTab(windowId, activeTabId)
            }
        }
    }

    /**
     * Closes [tabId] in [windowId] and removes its back stack from [TabBackStacksKey] and its
     * saved scroll offsets from [EntryScrollOffsetsKey].
     * If [tabId] was the last tab in [windowId], removes [windowId] from [WindowsKey].
     */
    // [END_EXCLUDE]
    fun closeTab(windowId: String, tabId: String) {
        Snapshot.withMutableSnapshot {
            detachTabFromWindow(windowId, tabId)
            stateStore.updateState(TabBackStacksKey) { tabBackStacks -> tabBackStacks - tabId }
            scrollStateHolder.clearTabs(setOf(tabId))
        }
    }

    // [START_EXCLUDE]
    /**
     * Tears off [tabId] from [sourceWindowId] into a newly created window and returns
     * the new `windowId` so the caller can launch a new [BrowserActivity] instance.
     *
     * Notice that [TabBackStacksKey] and [EntryScrollOffsetsKey] are not modified: the tab's
     * entire Navigation 3 back stack and per-entry scroll positions stay intact in [StateStore]
     * while ownership moves in [WindowsKey].
     */
    // [END_EXCLUDE]
    fun popOutTabToNewWindow(
        sourceWindowId: String,
        tabId: String,
    ): String? = Snapshot.withMutableSnapshot {
        val sourceWindow = stateStore.getState(WindowsKey).value[sourceWindowId]
            ?: return@withMutableSnapshot null
        if (sourceWindow.tabIds.size <= 1 || tabId !in sourceWindow.tabIds) {
            return@withMutableSnapshot null
        }

        val newWindowId = "win-${idGenerator()}"
        detachTabFromWindow(sourceWindowId, tabId)
        stateStore.updateState(WindowsKey) { windows ->
            windows + (newWindowId to WindowState(
                windowId = newWindowId,
                tabIds = listOf(tabId),
                activeTabId = tabId,
                windowNumber = nextWindowNumber(windows),
            ))
        }
        newWindowId
    }
    // [END browser_atomic_updates]

    /**
     * Moves [tabId] (along with its full Navigation 3 back stack and per-entry scroll state)
     * from [sourceWindowId] into [targetWindowId] and selects it in [targetWindowId].
     *
     * @return `true` if [tabId] was moved, was the last tab of [sourceWindowId], and
     * [targetWindowId] is not currently attached to any active Activity task—indicating that
     * the caller/UI should switch its task/window focus to [targetWindowId].
     */
    fun moveTabToWindow(
        sourceWindowId: String,
        tabId: String,
        targetWindowId: String,
    ): Boolean {
        if (sourceWindowId == targetWindowId) return false
        return Snapshot.withMutableSnapshot {
            val currentWindows = stateStore.getState(WindowsKey).value
            val sourceWindow = currentWindows[sourceWindowId] ?: return@withMutableSnapshot false
            if (tabId !in sourceWindow.tabIds || targetWindowId !in currentWindows) {
                return@withMutableSnapshot false
            }

            val isLastTabOfSource = sourceWindow.tabIds.size == 1
            val isTargetUnattached = taskIdForWindow(targetWindowId) == null

            detachTabFromWindow(sourceWindowId, tabId)
            stateStore.updateState(WindowsKey) { windows ->
                val targetWindow = windows[targetWindowId] ?: return@updateState windows
                windows + (targetWindowId to targetWindow.copy(
                    tabIds = targetWindow.tabIds + tabId,
                    activeTabId = tabId,
                ))
            }

            isLastTabOfSource && isTargetUnattached
        }
    }

    /**
     * Returns a [BrowserWindowSession] scoped to [windowId].
     */
    fun windowSession(windowId: String): BrowserWindowSession =
        DefaultBrowserWindowSession(this, windowId)

    /**
     * Cleans up [windowId] and all of its remaining tabs when the [BrowserActivity] task
     * is explicitly closed by the user.
     */
    fun closeWindow(windowId: String) {
        Snapshot.withMutableSnapshot {
            val window = stateStore.getState(WindowsKey).value[windowId]
                ?: return@withMutableSnapshot
            val closedTabIds = window.tabIds.toSet()
            stateStore.updateState(WindowsKey) { windows -> windows - windowId }
            stateStore.updateState(TabBackStacksKey) { tabBackStacks -> tabBackStacks - closedTabIds }
            stateStore.updateState(ActiveWindowTasksKey) { windowTasks -> windowTasks - windowId }
            scrollStateHolder.clearTabs(closedTabIds)
        }
    }

    fun isActiveBackStackEntry(entryId: String): Boolean {
        val parsedEntryId = EntryId.parse(entryId) ?: return false
        val stackIndex = parsedEntryId.stackIndex ?: return false
        val backStack = stateStore.getState(TabBackStacksKey).value[parsedEntryId.tabId] ?: return false
        return stackIndex in backStack.indices
    }

    private fun detachTabFromWindow(windowId: String, tabId: String) {
        var windowRemoved = false
        stateStore.updateState(WindowsKey) { windows ->
            val window = windows[windowId] ?: return@updateState windows
            val removedIndex = window.tabIds.indexOf(tabId)
            if (removedIndex == -1) return@updateState windows

            val remainingTabIds = window.tabIds - tabId
            if (remainingTabIds.isEmpty()) {
                windowRemoved = true
                windows - windowId
            } else {
                val nextActiveTabId = if (window.activeTabId == tabId) {
                    remainingTabIds[removedIndex.coerceAtMost(remainingTabIds.lastIndex)]
                } else {
                    window.activeTabId
                }
                windows + (windowId to window.copy(
                    tabIds = remainingTabIds,
                    activeTabId = nextActiveTabId,
                ))
            }
        }
        if (windowRemoved) {
            stateStore.updateState(ActiveWindowTasksKey) { windowTasks -> windowTasks - windowId }
        }
    }

    private fun nextWindowNumber(windows: Map<String, WindowState>): Int =
        (windows.values.maxOfOrNull { it.windowNumber } ?: 0) + 1
}
