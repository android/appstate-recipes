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

import androidx.appstate.statestore.StateStore
import androidx.compose.ui.geometry.Rect
import com.example.appstaterecipes.browser.ui.TabDragPreviewMode
import com.example.appstaterecipes.browser.ui.TabDragShadowController
import com.example.appstaterecipes.browser.ui.calculateAutoScrollOffsetToRevealTab
import com.example.appstaterecipes.browser.ui.calculateReorderTargetIndex
import com.example.appstaterecipes.browser.ui.handleContentAreaTabDrop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// [START browser_navigator_test]
class BrowserNavigatorTest {

    private var nextId = 1
    private val navigator = BrowserNavigator(idGenerator = { (nextId++).toString() })

    @Test
    fun popOutTab_preservesFullBackStackInNewWindow() {
        // 1. Initialize win-A (creates tab-1 at Home) and open a second tab (tab-2)
        navigator.ensureWindow("win-A")
        val tab2Id = navigator.newTab("win-A", BrowserRoute.Home)

        // 2. Navigate 3 levels deep inside tab-2: Home -> Search -> Article(42)
        navigator.navigate("win-A", BrowserRoute.Search("nav3"))
        navigator.navigate("win-A", BrowserRoute.Article(42, "Multi-Window Nav3"))

        val expectedStack = listOf(
            BrowserRoute.Home,
            BrowserRoute.Search("nav3"),
            BrowserRoute.Article(42, "Multi-Window Nav3"),
        )
        assertEquals(expectedStack, navigator.windowUiState("win-A").value?.activeBackStack)

        // 3. Pop tab-2 out of win-A into a new window
        val newWindowId = navigator.popOutTabToNewWindow("win-A", tab2Id)
        assertNotNull(newWindowId)

        // win-A falls back to tab-1 (Home), while newWindowId has tab-2 with all 3 routes intact
        val winAState = navigator.windowUiState("win-A").value!!
        assertEquals(listOf("tab-1"), winAState.tabs.map { it.tabId })
        assertEquals(listOf(BrowserRoute.Home), winAState.activeBackStack)

        val newWinState = navigator.windowUiState(newWindowId!!).value!!
        assertEquals(listOf(tab2Id), newWinState.tabs.map { it.tabId })
        assertEquals(expectedStack, newWinState.activeBackStack)

        // 4. When only one tab remains in a window, popping it out to a new window is disallowed
        assertNull(navigator.popOutTabToNewWindow("win-A", "tab-1"))
        assertEquals(listOf("tab-1"), navigator.windowUiState("win-A").value?.tabs?.map { it.tabId })
    }

    @Test
    fun moveTabToWindow_mergesTabAndClosesSourceWindowWhenEmpty() {
        navigator.ensureWindow("win-A") // tab-1
        navigator.ensureWindow("win-B") // tab-2
        navigator.attachWindowTask("win-B", taskId = 202)
        navigator.navigate("win-B", BrowserRoute.Article(7, "Docked Article"))

        val tabInB = navigator.windowUiState("win-B").value!!.activeTabId

        // Move the only tab in win-B into win-A
        navigator.moveTabToWindow(
            sourceWindowId = "win-B",
            tabId = tabInB,
            targetWindowId = "win-A",
        )

        // win-B is now empty and removed from WindowsKey and ActiveWindowTasksKey
        assertNull(navigator.windowUiState("win-B").value)
        assertNull(navigator.taskIdForWindow("win-B"))

        // win-A now owns both tabs, with tabInB active and its back stack intact
        val winA = navigator.windowUiState("win-A").value
        assertNotNull(winA)
        assertEquals(listOf("tab-1", tabInB), winA!!.tabs.map { it.tabId })
        assertEquals(tabInB, winA.activeTabId)
        assertEquals(
            listOf(BrowserRoute.Home, BrowserRoute.Article(7, "Docked Article")),
            winA.activeBackStack,
        )
    }

    @Test
    fun goBack_popsRouteOrClosesTab() {
        navigator.ensureWindow("win-A")
        navigator.navigate("win-A", BrowserRoute.Search("kotlin"))
        assertEquals(2, navigator.windowUiState("win-A").value!!.activeBackStack.size)

        // Pop the top search route
        navigator.goBack("win-A")
        assertEquals(listOf(BrowserRoute.Home), navigator.windowUiState("win-A").value!!.activeBackStack)

        // At root route (size == 1) when only one tab is open, goBack does not close the last tab
        // so Android's OnBackInvokedDispatcher can handle the window-close animation.
        navigator.goBack("win-A")
        assertNotNull(navigator.windowUiState("win-A").value)
        assertEquals(1, navigator.windowUiState("win-A").value!!.tabs.size)

        // When multiple tabs are open and active tab is at root route, goBack closes the active tab
        navigator.newTab("win-A", BrowserRoute.Home)
        assertEquals(2, navigator.windowUiState("win-A").value!!.tabs.size)
        navigator.goBack("win-A")
        assertEquals(1, navigator.windowUiState("win-A").value!!.tabs.size)
        assertEquals(listOf("tab-1"), navigator.windowUiState("win-A").value?.tabs?.map { it.tabId })
    }

    @Test
    fun closeTab_cleansUpTabStack() {
        navigator.ensureWindow("win-A")
        val tab2Id = navigator.newTab("win-A", BrowserRoute.Search("compose"))
        assertEquals(2, navigator.windowUiState("win-A").value!!.tabs.size)

        navigator.closeTab("win-A", tab2Id)
        assertEquals(1, navigator.windowUiState("win-A").value!!.tabs.size)
        assertEquals(listOf("tab-1"), navigator.windowUiState("win-A").value?.tabs?.map { it.tabId })
    }

    @Test
    fun primeKeys_registersPersistedKeysWithEmptyDefaultValues() {
        val stateStore = StateStore()
        val nav = BrowserNavigator(stateStore = stateStore)
        assertEquals(0, stateStore.keys.size)

        nav.primeKeys()

        assertEquals(3, stateStore.keys.size)
        assertNull(nav.firstOpenWindowId())
    }

    @Test
    fun scrollOffsets_preservedPerBackStackItemAcrossWindowsAndCleanedUpOnPopOrClose() {
        navigator.ensureWindow("win-A") // tab-1 at index 0: Home
        val tab2Id = navigator.newTab("win-A", BrowserRoute.Article(1, "Article 1"))

        // Push Search("compose") at index 1, then duplicate Article(1, "Article 1") at index 2
        navigator.navigate("win-A", BrowserRoute.Search("compose"))
        navigator.navigate("win-A", BrowserRoute.Article(1, "Article 1"))

        val items = navigator.windowUiState("win-A").value!!.activeBackStackItems
        assertEquals(3, items.size)
        val firstArticleEntryId = items[0].entryId
        val searchEntryId = items[1].entryId
        val secondArticleEntryId = items[2].entryId

        // Record distinct scroll positions for all three back-stack items (including duplicate routes)
        navigator.updateScrollOffset(firstArticleEntryId, 480)
        navigator.updateScrollOffset(searchEntryId, 220)
        navigator.updateScrollOffset(secondArticleEntryId, 960)

        assertEquals(480, navigator.scrollOffset(firstArticleEntryId))
        assertEquals(220, navigator.scrollOffset(searchEntryId))
        assertEquals(960, navigator.scrollOffset(secondArticleEntryId))

        // Pop tab-2 out to a new window: all per-entry scroll offsets stay intact
        val newWindowId = navigator.popOutTabToNewWindow("win-A", tab2Id)!!
        assertEquals(480, navigator.scrollOffset(firstArticleEntryId))
        assertEquals(220, navigator.scrollOffset(searchEntryId))
        assertEquals(960, navigator.scrollOffset(secondArticleEntryId))

        // Move tab-2 back into win-A: scroll offsets still intact
        navigator.moveTabToWindow(newWindowId, tab2Id, "win-A")
        assertEquals(480, navigator.scrollOffset(firstArticleEntryId))
        assertEquals(220, navigator.scrollOffset(searchEntryId))
        assertEquals(960, navigator.scrollOffset(secondArticleEntryId))

        // Pop the top item (secondArticleEntryId at index 2): its scroll offset is cleared,
        // while earlier items (including the first Article(1) at index 0) keep their scroll offsets.
        navigator.goBack("win-A")
        assertEquals(0, navigator.scrollOffset(secondArticleEntryId))
        assertEquals(480, navigator.scrollOffset(firstArticleEntryId))
        assertEquals(220, navigator.scrollOffset(searchEntryId))

        // Late onDispose writes for an already-popped entry are ignored
        navigator.updateScrollOffset(secondArticleEntryId, 960)
        assertEquals(0, navigator.scrollOffset(secondArticleEntryId))

        // Closing tab-2 clears all remaining scroll offsets for tab-2
        navigator.closeTab("win-A", tab2Id)
        assertEquals(0, navigator.scrollOffset(firstArticleEntryId))
        assertEquals(0, navigator.scrollOffset(searchEntryId))
    }

    @Test
    fun firstOpenWindowId_returnsNullWhenEmptyAndFirstWhenPresent() {
        assertNull(navigator.firstOpenWindowId())

        navigator.ensureWindow("win-1")
        assertEquals("win-1", navigator.firstOpenWindowId())

        navigator.ensureWindow("win-2")
        assertEquals("win-1", navigator.firstOpenWindowId())

        navigator.closeWindow("win-1")
        assertEquals("win-2", navigator.firstOpenWindowId())

        navigator.closeWindow("win-2")
        assertNull(navigator.firstOpenWindowId())
    }

    @Test
    fun windowUiState_returnsConsolidatedWindowAndTabSummaries() {
        // Create win-A with 1 tab ("Home")
        navigator.ensureWindow("win-A")

        // Create win-B with 3 tabs: "Home", Search("compose"), Article(10, "Intro")
        navigator.ensureWindow("win-B")
        navigator.newTab("win-B", BrowserRoute.Search("compose"))
        navigator.newTab("win-B", BrowserRoute.Article(10, "Intro"))

        val winAUiState = navigator.windowUiState("win-A").value
        assertNotNull(winAUiState)
        assertEquals("Window 1", winAUiState!!.displayName)
        assertEquals(1, winAUiState.otherWindows.size)
        val winBSummary = winAUiState.otherWindows.first()
        assertEquals("win-B", winBSummary.windowId)
        assertEquals("Window 2", winBSummary.displayName)
        assertEquals(3, winBSummary.tabCount)
        assertEquals(listOf("Home", "Search: compose"), winBSummary.firstTabTitles)
    }

    @Test
    fun allWindowSummariesAndTaskTracking_reflectsAttachedActivityState() {
        navigator.ensureWindow("win-A")
        navigator.ensureWindow("win-B")

        navigator.attachWindowTask("win-A", taskId = 101)

        val allSummaries = navigator.windowUiState("win-A").value!!.allWindows
        assertEquals(2, allSummaries.size)
        assertEquals(true, allSummaries[0].isAttachedToActivity)
        assertEquals(false, allSummaries[1].isAttachedToActivity)
        assertEquals(101, navigator.taskIdForWindow("win-A"))
        assertNull(navigator.taskIdForWindow("win-B"))

        // firstOpenWindowId prefers an unattached window ("win-B") over an attached one ("win-A")
        assertEquals("win-B", navigator.firstOpenWindowId())

        // When all existing windows are attached to live tasks, firstOpenWindowId returns null
        // so that launching a new Activity creates a fresh window instead of hijacking an active one.
        navigator.attachWindowTask("win-B", taskId = 202)
        assertNull(navigator.firstOpenWindowId())
        navigator.detachWindowTask("win-B", taskId = 202)

        // Detach win-A and attach win-B (simulating switching current Activity window to win-B)
        navigator.detachWindowTask("win-A", taskId = 101)
        navigator.attachWindowTask("win-B", taskId = 101)

        val updatedSummaries = navigator.windowUiState("win-B").value!!.allWindows
        assertEquals(false, updatedSummaries[0].isAttachedToActivity)
        assertEquals(true, updatedSummaries[1].isAttachedToActivity)
        assertNull(navigator.taskIdForWindow("win-A"))
        assertEquals(101, navigator.taskIdForWindow("win-B"))
    }

    @Test
    fun browserWindowSession_delegatesActionsAndComputesTabTransferSwitchPolicy() {
        navigator.ensureWindow("win-A")
        navigator.ensureWindow("win-B")
        val sessionA = navigator.windowSession("win-A")
        val sessionB = navigator.windowSession("win-B")

        // 1. Session A newTab & navigate
        val tab2 = sessionA.newTab(BrowserRoute.Home)
        sessionA.navigate(BrowserRoute.Search("kotlin"))
        assertEquals(2, sessionA.uiState.value?.activeBackStack?.size)
        val activeEntryId = EntryId.of(tab2, 1).value
        assertEquals(true, sessionA.isActiveBackStackEntry(activeEntryId))

        // 2. Session A goBack pops the top entry
        sessionA.goBack()
        assertEquals(1, sessionA.uiState.value?.activeBackStack?.size)
        assertEquals(false, sessionA.isActiveBackStackEntry(activeEntryId))

        // Attempting to save scroll offset on an inactive/popped entry with isValidEntry guard is ignored
        navigator.scrollStore.updateScrollOffset(
            entryId = activeEntryId,
            offsetPx = 450,
            isValidEntry = { sessionA.isActiveBackStackEntry(it) },
        )
        assertEquals(0, navigator.scrollOffset(activeEntryId))

        // 3. Transferring tab2 to win-B when win-A has another tab remaining does not require window switch
        val shouldSwitch1 = sessionB.receiveTabFromWindow("win-A", tab2)
        assertEquals(false, shouldSwitch1)

        // 4. Transferring the last remaining tab of win-A to unattached win-B signals that UI should switch window
        val remainingTabOfA = sessionA.uiState.value!!.activeTabId
        val shouldSwitch2 = sessionB.receiveTabFromWindow("win-A", remainingTabOfA)
        assertEquals(true, shouldSwitch2)
        assertNull(sessionA.uiState.value) // win-A was closed because its last tab was transferred
    }

    @Test
    fun structuredIdentityTypes_parseAndFormatCorrectly() {
        val entryId = EntryId.of("tab-123", 2)
        assertEquals("tab-123:2", entryId.value)
        assertEquals("tab-123", entryId.tabId)
        assertEquals(2, entryId.stackIndex)

        val parsed = EntryId.parse("tab-abc:5")
        assertNotNull(parsed)
        assertEquals("tab-abc", parsed?.tabId)
        assertEquals(5, parsed?.stackIndex)

        assertNull(EntryId.parse("invalid"))
        assertNull(EntryId.parse(":invalid"))

        val payload = TabDragPayload(sourceWindowId = "win-1", tabId = "tab-99")
        assertEquals("win-1:tab-99", payload.rawText)

        val parsedPayload = TabDragPayload.parse("win-1:tab-99")
        assertEquals(payload, parsedPayload)
        assertNull(TabDragPayload.parse("invalid-payload"))
    }

    @Test
    fun entryScrollStateStore_savesAndClearsPerEntryScrollPositions() {
        val stateStore = StateStore()
        val scrollStore = EntryScrollStateStore(stateStore)

        scrollStore.updateScrollOffset("tab-1:0", 120)
        scrollStore.updateScrollOffset("tab-1:1", 450)
        scrollStore.updateScrollOffset("tab-2:0", 300)

        assertEquals(120, scrollStore.scrollOffset("tab-1:0"))
        assertEquals(450, scrollStore.scrollOffset("tab-1:1"))
        assertEquals(300, scrollStore.scrollOffset("tab-2:0"))

        // Clear single popped entry
        scrollStore.clearEntry("tab-1:1")
        assertEquals(0, scrollStore.scrollOffset("tab-1:1"))
        assertEquals(120, scrollStore.scrollOffset("tab-1:0"))

        // Clear all entries of tab-1
        scrollStore.clearTabs(setOf("tab-1"))
        assertEquals(0, scrollStore.scrollOffset("tab-1:0"))
        assertEquals(300, scrollStore.scrollOffset("tab-2:0"))
    }

    @Test
    fun tabDragShadowController_switchesBetweenNewWindowAndMoveToWindowPreviews() {
        val observedModes = mutableListOf<TabDragPreviewMode>()

        // 1. Drag starts over the source window's tab bar in MoveToWindow mode
        val initialMode = TabDragShadowController.startDrag(
            sourceWindowId = "win-1",
            canCreateNewWindow = true,
            onPreviewModeChanged = { observedModes.add(it) },
        )
        assertEquals(
            TabDragPreviewMode.MoveToWindow,
            initialMode,
        )

        // 2. Exiting the source window's tab bar switches preview to NewWindow mode (when canCreateNewWindow = true)
        TabDragShadowController.onTargetWindowExited(
            targetWindowId = "win-1",
        )
        assertEquals(
            TabDragPreviewMode.NewWindow,
            observedModes.last(),
        )

        // 3. Entering another window's tab bar switches preview back to MoveToWindow
        TabDragShadowController.onTargetWindowEntered(
            targetWindowId = "win-2",
        )
        assertEquals(
            TabDragPreviewMode.MoveToWindow,
            observedModes.last(),
        )

        // 4. Exiting back to open space returns to NewWindow mode
        TabDragShadowController.onTargetWindowExited(
            targetWindowId = "win-2",
        )
        assertEquals(
            TabDragPreviewMode.NewWindow,
            observedModes.last(),
        )

        TabDragShadowController.onDragEnded()

        // 4. When source window only has 1 tab (canCreateNewWindow = false), drag starts in MoveToWindow
        val singleTabInitialMode = TabDragShadowController.startDrag(
            sourceWindowId = "win-1",
            canCreateNewWindow = false,
            onPreviewModeChanged = { observedModes.add(it) },
        )
        assertEquals(
            TabDragPreviewMode.MoveToWindow,
            singleTabInitialMode,
        )
        TabDragShadowController.onDragEnded()
    }

    @Test
    fun openWindow_attachesTaskAndEnsuresWindowAtomically() {
        // When opening without requestedWindowId, it generates a window ID using idGenerator and attaches taskId
        val openedId = navigator.openWindow(taskId = 101)
        assertEquals("win-1", openedId)

        val uiState = navigator.windowUiState(openedId).value
        assertNotNull(uiState)
        assertEquals(openedId, uiState?.windowId)
        assertEquals(101, navigator.taskIdForWindow(openedId))

        // When opening an explicit requestedWindowId, it ensures it and attaches taskId
        val customOpenedId = navigator.openWindow(requestedWindowId = "win-custom", taskId = 202)
        assertEquals("win-custom", customOpenedId)
        assertEquals(202, navigator.taskIdForWindow("win-custom"))
    }

    @Test
    fun backStackItem_structuredFieldsAndEntryId() {
        val item = BackStackItem(
            tabId = "tab-abc",
            index = 2,
            route = BrowserRoute.Article(42, "Structured BackStackItem"),
        )
        assertEquals("tab-abc", item.tabId)
        assertEquals(2, item.index)
        assertEquals("tab-abc:2", item.entryId)
        assertEquals(
            "tab-abc:2:Article(id=42, headline=Structured BackStackItem)",
            item.contentKey,
        )
        assertEquals("tab-abc", BackStackItem.tabIdFromContentKey(item.contentKey))
    }

    @Test
    fun moveTab_reordersTabsWithinWindowAndPreservesActiveTabAndStacks() {
        navigator.ensureWindow("win-A") // tab-1 (Home)
        val tab2Id = navigator.newTab("win-A", BrowserRoute.Search("compose"))
        val tab3Id = navigator.newTab("win-A", BrowserRoute.Article(7, "Drag Reorder"))
        navigator.navigate("win-A", BrowserRoute.Article(8, "Follow-up"))

        val entryId = navigator.windowUiState("win-A").value!!.activeBackStackItems.last().entryId
        navigator.updateScrollOffset(entryId, 320)

        assertEquals(
            listOf("tab-1", tab2Id, tab3Id),
            navigator.windowUiState("win-A").value!!.tabs.map { it.tabId },
        )

        // Move tab-1 from index 0 to index 2 (end)
        navigator.moveTab("win-A", "tab-1", toIndex = 2)
        val afterFirstMove = navigator.windowUiState("win-A").value!!
        assertEquals(listOf(tab2Id, tab3Id, "tab-1"), afterFirstMove.tabs.map { it.tabId })
        assertEquals(tab3Id, afterFirstMove.activeTabId)
        assertEquals(320, navigator.scrollOffset(entryId))

        // Move tab-3 to index 0 (start)
        navigator.moveTab("win-A", tab3Id, toIndex = 0)
        val afterSecondMove = navigator.windowUiState("win-A").value!!
        assertEquals(listOf(tab3Id, tab2Id, "tab-1"), afterSecondMove.tabs.map { it.tabId })
        assertEquals(
            listOf(BrowserRoute.Article(7, "Drag Reorder"), BrowserRoute.Article(8, "Follow-up")),
            afterSecondMove.activeBackStack,
        )
    }

    @Test
    fun calculateReorderTargetIndex_swapsWhenPointerCrossesNeighborMidpoints() {
        fun tabSummary(id: String) = TabSummary(
            tabId = id,
            currentRoute = BrowserRoute.Home,
            stackDepth = 1,
            isSelected = false,
        )
        val tabs = listOf(tabSummary("tab-1"), tabSummary("tab-2"), tabSummary("tab-3"))
        val bounds = mapOf(
            "tab-1" to Rect(0f, 0f, 100f, 36f),     // center = 50
            "tab-2" to Rect(106f, 0f, 206f, 36f),   // center = 156
            "tab-3" to Rect(212f, 0f, 312f, 36f),   // center = 262
        )

        // Dragging tab-1 right: before tab-2 center (156) -> null; past tab-2 center -> 1; past tab-3 center (262) -> 2
        assertNull(
            calculateReorderTargetIndex(
                tabs = tabs,
                draggedTabId = "tab-1",
                dragX = 140f,
                tabBounds = bounds,
            ),
        )
        assertEquals(
            1,
            calculateReorderTargetIndex(
                tabs = tabs,
                draggedTabId = "tab-1",
                dragX = 160f,
                tabBounds = bounds,
            ),
        )
        assertEquals(
            2,
            calculateReorderTargetIndex(
                tabs = tabs,
                draggedTabId = "tab-1",
                dragX = 270f,
                tabBounds = bounds,
            ),
        )

        // Dragging tab-3 left: before tab-2 center (156) -> null; past tab-2 center -> 1; past tab-1 center (50) -> 0
        assertNull(
            calculateReorderTargetIndex(
                tabs = tabs,
                draggedTabId = "tab-3",
                dragX = 170f,
                tabBounds = bounds,
            ),
        )
        assertEquals(
            1,
            calculateReorderTargetIndex(
                tabs = tabs,
                draggedTabId = "tab-3",
                dragX = 150f,
                tabBounds = bounds,
            ),
        )
        assertEquals(
            0,
            calculateReorderTargetIndex(
                tabs = tabs,
                draggedTabId = "tab-3",
                dragX = 40f,
                tabBounds = bounds,
            ),
        )
    }

    @Test
    fun calculateAutoScrollOffsetToRevealTab_scrollsOnlyWhenTabIsClippedOrOffscreen() {
        val rowBounds = Rect(80f, 0f, 380f, 36f) // viewport width = 300

        // 1. Tab is already completely visible inside [80, 380] -> returns null
        assertNull(
            calculateAutoScrollOffsetToRevealTab(
                currentScroll = 0,
                maxScroll = 240,
                rowBounds = rowBounds,
                tabBounds = Rect(200f, 0f, 340f, 36f),
            ),
        )

        // 2. Newly added tab overhangs the right edge (320..450 vs row right 380) -> scrolls right by 70
        assertEquals(
            70,
            calculateAutoScrollOffsetToRevealTab(
                currentScroll = 0,
                maxScroll = 240,
                rowBounds = rowBounds,
                tabBounds = Rect(320f, 0f, 450f, 36f),
            ),
        )

        // 3. Newly added tab is completely off-screen to the right (456..580) -> clamped to maxScroll (200)
        assertEquals(
            200,
            calculateAutoScrollOffsetToRevealTab(
                currentScroll = 0,
                maxScroll = 200,
                rowBounds = rowBounds,
                tabBounds = Rect(456f, 0f, 580f, 36f),
            ),
        )

        // 4. Tab is cut off on the left when already scrolled (30..150 vs row left 80) -> scrolls left by 50
        assertEquals(
            90,
            calculateAutoScrollOffsetToRevealTab(
                currentScroll = 140,
                maxScroll = 240,
                rowBounds = rowBounds,
                tabBounds = Rect(30f, 0f, 150f, 36f),
            ),
        )
    }

    @Test
    fun handleContentAreaTabDrop_popsOutSameWindowTabOrMovesCrossWindowTab() {
        val poppedOutTabs = mutableListOf<String>()
        val movedTabs = mutableListOf<Triple<String, String, String>>()

        // 1. Dropping a tab outside the tab bar in its own window when canPopOut = true pops it out
        val poppedOut = handleContentAreaTabDrop(
            payload = TabDragPayload(sourceWindowId = "win-1", tabId = "tab-2"),
            windowId = "win-1",
            canPopOut = true,
            onPopOutTab = { poppedOutTabs.add(it) },
            onMoveTabToWindow = { src, tab, dst -> movedTabs.add(Triple(src, tab, dst)) },
        )
        assertTrue(poppedOut)
        assertEquals(listOf("tab-2"), poppedOutTabs)
        assertTrue(movedTabs.isEmpty())

        // 2. Dropping the only remaining tab in its own window when canPopOut = false is ignored
        val ignoredSingleTab = handleContentAreaTabDrop(
            payload = TabDragPayload(sourceWindowId = "win-1", tabId = "tab-1"),
            windowId = "win-1",
            canPopOut = false,
            onPopOutTab = { poppedOutTabs.add(it) },
            onMoveTabToWindow = { src, tab, dst -> movedTabs.add(Triple(src, tab, dst)) },
        )
        assertFalse(ignoredSingleTab)
        assertEquals(listOf("tab-2"), poppedOutTabs)

        // 3. Dropping a tab dragged from another window into this window's content area moves it
        val movedCrossWindow = handleContentAreaTabDrop(
            payload = TabDragPayload(sourceWindowId = "win-1", tabId = "tab-1"),
            windowId = "win-2",
            canPopOut = false,
            onPopOutTab = { poppedOutTabs.add(it) },
            onMoveTabToWindow = { src, tab, dst -> movedTabs.add(Triple(src, tab, dst)) },
        )
        assertTrue(movedCrossWindow)
        assertEquals(listOf(Triple("win-1", "tab-1", "win-2")), movedTabs)
    }
}
// [END browser_navigator_test]
