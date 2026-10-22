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

import androidx.appstate.datastore.PersistToDataStore
import androidx.appstate.statestore.StateStore
import androidx.appstate.statestore.StateStoreKey
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
private object WindowsKey : StateStoreKey<Map<String, WindowState>>(defaultValue = emptyMap()),
    PersistToDataStore

@Serializable
private object TabStacksKey :
    StateStoreKey<Map<String, List<BrowserRoute>>>(defaultValue = emptyMap()),
    PersistToDataStore

private object ActiveWindowTasksKey : StateStoreKey<Map<String, Int>>(defaultValue = emptyMap())

// [START browser_navigator]
/**
 * Stateless facade that encapsulates [StateStore] and coordinates multi-tab
 * Navigation 3 back stacks across an arbitrary number of [BrowserActivity] windows.
 *
 * Because all state lives inside [stateStore] under `private` singleton keys ([WindowsKey],
 * [TabStacksKey], and [EntryScrollOffsetsKey]), multiple [BrowserNavigator] instances wrapping
 * the same [StateStore] operate on the exact same windows and tabs, and multiple recipes can
 * share that [StateStore] without key collisions.
 */
@Stable
class BrowserNavigator(
    private val stateStore: StateStore = StateStore(),
    private val idGenerator: () -> String = { UUID.randomUUID().toString().take(8) },
    val scrollStore: EntryScrollStateStore = EntryScrollStateStore(stateStore),
) {
    /**
     * Registers the persisted browser keys ([WindowsKey], [TabStacksKey], and
     * [EntryScrollOffsetsKey]) in [StateStore] so that [androidx.appstate.datastore.syncToDataStore]
     * can restore them from disk before [BrowserActivity] is opened for the first time.
     */
    fun primeKeys() {
        stateStore.getState(WindowsKey)
        stateStore.getState(TabStacksKey)
        scrollStore.primeKey()
    }

    /**
     * Ensures [windowId] exists in [StateStore]. If it does not exist yet, initializes it
     * with a single tab starting at [initialRoute].
     */
    fun ensureWindow(
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
     * @param initialRoute The route for the initial tab if a new window needs to be created.
     * @return The resolved window ID that was opened and attached.
     */
    fun openWindow(
        requestedWindowId: String? = null,
        taskId: Int,
        initialRoute: BrowserRoute = BrowserRoute.Home,
    ): String {
        return Snapshot.withMutableSnapshot {
            val resolvedId = requestedWindowId
                ?: firstOpenWindowId()
                ?: "win-${idGenerator()}"

            ensureWindow(resolvedId, initialRoute)
            attachWindowTask(resolvedId, taskId)

            resolvedId
        }
    }

    /**
     * Returns a consolidated [State] of [BrowserWindowUiState] for [windowId], or `null` if the
     * window has been closed (e.g. after its last tab was closed or moved to another window).
     */
    fun windowUiState(windowId: String): State<BrowserWindowUiState?> = derivedStateOf {
        val windows = stateStore.getState(WindowsKey).value
        val window = windows[windowId] ?: return@derivedStateOf null
        val stacks = stateStore.getState(TabStacksKey).value
        val activeBackStack = stacks[window.activeTabId].orEmpty()
        if (activeBackStack.isEmpty()) return@derivedStateOf null

        val allSummaries = buildWindowSummaries(
            windows = windows,
            stacks = stacks,
            activeTasks = stateStore.getState(ActiveWindowTasksKey).value,
        )
        val displayName = allSummaries.find { it.windowId == windowId }?.displayName ?: "Window"

        BrowserWindowUiState(
            windowId = windowId,
            displayName = displayName,
            activeTabId = window.activeTabId,
            tabs = buildTabSummaries(window, stacks),
            activeBackStack = activeBackStack,
            allWindows = allSummaries,
        )
    }

    private fun buildTabSummaries(
        window: WindowState,
        stacks: Map<String, List<BrowserRoute>>,
    ): List<TabSummary> = window.tabIds.mapNotNull { tabId ->
        val stack = stacks[tabId] ?: return@mapNotNull null
        val currentRoute = stack.lastOrNull() ?: return@mapNotNull null
        TabSummary(
            tabId = tabId,
            isSelected = tabId == window.activeTabId,
            stackDepth = stack.size,
            currentRoute = currentRoute,
        )
    }

    private fun buildWindowSummaries(
        windows: Map<String, WindowState>,
        stacks: Map<String, List<BrowserRoute>>,
        activeTasks: Map<String, Int>,
    ): List<WindowSummary> {
        return windows.values.mapIndexed { index, window ->
            val displayName = "Window ${index + 1}"
            val firstTwoTitles = window.tabIds.take(2).mapNotNull { tabId ->
                stacks[tabId]?.lastOrNull()?.title
            }
            WindowSummary(
                windowId = window.windowId,
                displayName = displayName,
                tabCount = window.tabIds.size,
                firstTabTitles = firstTwoTitles,
                isAttachedToActivity = window.windowId in activeTasks,
            )
        }
    }

    /**
     * Records that [windowId] is currently attached to a live [BrowserActivity] with [taskId].
     * Also clears any previous window mapping that was associated with [taskId].
     */
    fun attachWindowTask(windowId: String, taskId: Int) {
        stateStore.updateState(ActiveWindowTasksKey) { tasks ->
            tasks.filterValues { it != taskId } + (windowId to taskId)
        }
    }

    /**
     * Removes the active task association for [windowId] if it matches [taskId].
     */
    fun detachWindowTask(windowId: String, taskId: Int) {
        stateStore.updateState(ActiveWindowTasksKey) { tasks ->
            if (tasks[windowId] == taskId) tasks - windowId else tasks
        }
    }

    /**
     * Returns the system task ID of the live [BrowserActivity] currently hosting [windowId],
     * or `null` if [windowId] is not currently open in any live Activity window.
     */
    fun taskIdForWindow(windowId: String): Int? =
        stateStore.getState(ActiveWindowTasksKey).value[windowId]

    /**
     * Returns the ID of the first window that is not currently attached to a live Activity,
     * or `null` if no unattached windows currently exist in [StateStore].
     * Useful when launching the browser activity without an explicit target window ID.
     */
    fun firstOpenWindowId(): String? {
        val windows = stateStore.getState(WindowsKey).value.keys
        val activeTasks = stateStore.getState(ActiveWindowTasksKey).value
        return windows.firstOrNull { it !in activeTasks }
    }

    /**
     * Opens a new tab in [windowId] starting at [initialRoute] and selects it.
     */
    fun newTab(
        windowId: String,
        initialRoute: BrowserRoute = BrowserRoute.Home,
    ): String {
        val newTabId = "tab-${idGenerator()}"
        Snapshot.withMutableSnapshot {
            stateStore.updateState(TabStacksKey) { stacks ->
                stacks + (newTabId to listOf(initialRoute))
            }
            stateStore.updateState(WindowsKey) { windows ->
                val window = windows[windowId]
                if (window == null) {
                    windows + (windowId to WindowState(windowId, listOf(newTabId), newTabId))
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
     * Because back stacks ([TabStacksKey]) and scroll offsets ([EntryScrollOffsetsKey]) are keyed
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

            val reorderedTabs = window.tabIds.toMutableList().apply {
                removeAt(fromIndex)
                add(targetIndex, tabId)
            }
            windows + (windowId to window.copy(tabIds = reorderedTabs))
        }
    }

    /**
     * Pushes [route] onto the active tab's back stack in [windowId].
     */
    fun navigate(windowId: String, route: BrowserRoute) {
        Snapshot.withMutableSnapshot {
            val activeTabId = stateStore.getState(WindowsKey).value[windowId]?.activeTabId
                ?: return@withMutableSnapshot
            stateStore.updateState(TabStacksKey) { stacks ->
                val currentStack = stacks[activeTabId] ?: return@updateState stacks
                stacks + (activeTabId to (currentStack + route))
            }
        }
    }

    /**
     * Returns the saved vertical scroll offset in pixels for the back-stack item identified by
     * [entryId], or `0` if none has been recorded.
     */
    fun scrollOffset(entryId: String): Int = scrollStore.scrollOffset(entryId)

    /**
     * Saves the vertical scroll offset [offsetPx] for the back-stack item identified by [entryId].
     * If the item or its tab has already been popped or closed, this is a no-op.
     */
    fun updateScrollOffset(entryId: String, offsetPx: Int) {
        scrollStore.updateScrollOffset(entryId, offsetPx, ::isActiveBackStackEntry)
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
            val currentStack = stateStore.getState(TabStacksKey).value[activeTabId].orEmpty()

            if (currentStack.size > 1) {
                val poppedEntryId = backStackEntryId(activeTabId, currentStack.lastIndex)
                stateStore.updateState(TabStacksKey) { stacks ->
                    stacks + (activeTabId to currentStack.dropLast(1))
                }
                scrollStore.clearEntry(poppedEntryId)
            } else if (window.tabIds.size > 1) {
                closeTab(windowId, activeTabId)
            }
        }
    }

    /**
     * Closes [tabId] in [windowId] and removes its back stack from [TabStacksKey] and its
     * saved scroll offsets from [EntryScrollOffsetsKey].
     * If [tabId] was the last tab in [windowId], removes [windowId] from [WindowsKey].
     */
    fun closeTab(windowId: String, tabId: String) {
        Snapshot.withMutableSnapshot {
            detachTabFromWindow(windowId, tabId)
            stateStore.updateState(TabStacksKey) { stacks -> stacks - tabId }
            scrollStore.clearTabs(setOf(tabId))
        }
    }

    /**
     * Tears off [tabId] from [sourceWindowId] into a newly created window and returns
     * the new `windowId` so the caller can launch a new [BrowserActivity] instance.
     *
     * Notice that [TabStacksKey] and [EntryScrollOffsetsKey] are not modified: the tab's
     * entire Navigation 3 back stack and per-entry scroll positions stay intact in [StateStore]
     * while ownership moves in [WindowsKey].
     */
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
            ))
        }
        newWindowId
    }

    /**
     * Moves [tabId] (along with its full Navigation 3 back stack and per-entry scroll state)
     * from [sourceWindowId] into [targetWindowId] and selects it in [targetWindowId].
     */
    fun moveTabToWindow(
        sourceWindowId: String,
        tabId: String,
        targetWindowId: String,
    ) {
        if (sourceWindowId == targetWindowId) return
        Snapshot.withMutableSnapshot {
            val windowsSnapshot = stateStore.getState(WindowsKey).value
            val sourceWindow = windowsSnapshot[sourceWindowId] ?: return@withMutableSnapshot
            if (tabId !in sourceWindow.tabIds || targetWindowId !in windowsSnapshot) {
                return@withMutableSnapshot
            }

            detachTabFromWindow(sourceWindowId, tabId)
            stateStore.updateState(WindowsKey) { windows ->
                val targetWindow = windows[targetWindowId] ?: return@updateState windows
                windows + (targetWindowId to targetWindow.copy(
                    tabIds = targetWindow.tabIds + tabId,
                    activeTabId = tabId,
                ))
            }
        }
    }

    /**
     * Transfers [tabId] from [sourceWindowId] into [targetWindowId].
     *
     * @return `true` if the tab moved was the last tab of [sourceWindowId], and [targetWindowId]
     * is not currently attached to any active Activity task—indicating that the caller/UI
     * should switch its task/window focus to [targetWindowId].
     */
    fun receiveTabFromWindow(
        sourceWindowId: String,
        tabId: String,
        targetWindowId: String,
    ): Boolean = Snapshot.withMutableSnapshot {
        val windows = stateStore.getState(WindowsKey).value
        val sourceWindow = windows[sourceWindowId] ?: return@withMutableSnapshot false
        val isLastTabOfSource = sourceWindow.tabIds.size == 1 && sourceWindow.tabIds.first() == tabId
        val targetIsUnattached = taskIdForWindow(targetWindowId) == null

        moveTabToWindow(sourceWindowId, tabId, targetWindowId)

        isLastTabOfSource && targetIsUnattached
    }

    /**
     * Returns a [BrowserWindowSession] scoped to [windowId].
     */
    fun windowSession(windowId: String): BrowserWindowSession =
        DefaultBrowserWindowSession(this, windowId)

    /**
     * Returns the list of all current window summaries.
     */
    fun windowSummaries(): List<WindowSummary> {
        val windows = stateStore.getState(WindowsKey).value
        val stacks = stateStore.getState(TabStacksKey).value
        val activeTasks = stateStore.getState(ActiveWindowTasksKey).value
        return buildWindowSummaries(windows, stacks, activeTasks)
    }

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
            stateStore.updateState(TabStacksKey) { stacks -> stacks - closedTabIds }
            stateStore.updateState(ActiveWindowTasksKey) { tasks -> tasks - windowId }
            scrollStore.clearTabs(closedTabIds)
        }
    }

    private fun parseEntryId(entryId: String): EntryId? = EntryId.parse(entryId)

    fun isActiveBackStackEntry(entryId: String): Boolean {
        val parsed = parseEntryId(entryId) ?: return false
        val stackIndex = parsed.stackIndex ?: return false
        val stack = stateStore.getState(TabStacksKey).value[parsed.tabId] ?: return false
        return stackIndex in stack.indices
    }

    private fun detachTabFromWindow(windowId: String, tabId: String) {
        var windowRemoved = false
        stateStore.updateState(WindowsKey) { windows ->
            val window = windows[windowId] ?: return@updateState windows
            val closedIndex = window.tabIds.indexOf(tabId)
            if (closedIndex == -1) return@updateState windows

            val remainingTabs = window.tabIds - tabId
            if (remainingTabs.isEmpty()) {
                windowRemoved = true
                windows - windowId
            } else {
                val nextActiveTabId = if (window.activeTabId == tabId) {
                    remainingTabs[closedIndex.coerceAtMost(remainingTabs.lastIndex)]
                } else {
                    window.activeTabId
                }
                windows + (windowId to window.copy(
                    tabIds = remainingTabs,
                    activeTabId = nextActiveTabId,
                ))
            }
        }
        if (windowRemoved) {
            stateStore.updateState(ActiveWindowTasksKey) { tasks -> tasks - windowId }
        }
    }

    companion object {
        fun backStackEntryId(tabId: String, stackIndex: Int): String = "$tabId:$stackIndex"
    }
}
// [END browser_navigator]
