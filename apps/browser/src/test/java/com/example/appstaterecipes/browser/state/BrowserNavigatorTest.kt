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
import com.example.appstaterecipes.browser.model.BrowserRoute
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

    private fun requireUiState(windowId: String): BrowserWindowUiState =
        checkNotNull(navigator.windowUiState(windowId).value) {
            "Expected non-null BrowserWindowUiState for window '$windowId'"
        }

    @Test
    fun popOutTabToNewWindow_preservesFullBackStackInNewWindow() {
        navigator.ensureWindow("win-A")
        val initialTabId = requireUiState("win-A").activeTabId
        val tab2Id = navigator.newTab("win-A", BrowserRoute.Home)
        navigator.navigate("win-A", BrowserRoute.Search("nav3"))
        navigator.navigate("win-A", BrowserRoute.Article(42, "Multi-Window Nav3"))

        val expectedStack = listOf(
            BrowserRoute.Home,
            BrowserRoute.Search("nav3"),
            BrowserRoute.Article(42, "Multi-Window Nav3"),
        )

        val newWindowId = navigator.popOutTabToNewWindow("win-A", tab2Id)
        assertNotNull(newWindowId)

        val winAState = requireUiState("win-A")
        assertEquals(listOf(initialTabId), winAState.tabs.map { it.tabId })
        assertEquals(listOf(BrowserRoute.Home), winAState.activeBackStack)

        val newWinState = requireUiState(checkNotNull(newWindowId))
        assertEquals(listOf(tab2Id), newWinState.tabs.map { it.tabId })
        assertEquals(expectedStack, newWinState.activeBackStack)
    }

    // [START_EXCLUDE]
    @Test
    fun popOutTabToNewWindow_whenSingleTabRemains_returnsNull() {
        navigator.ensureWindow("win-A")
        val onlyTabId = requireUiState("win-A").activeTabId

        assertNull(navigator.popOutTabToNewWindow("win-A", onlyTabId))
        assertEquals(listOf(onlyTabId), requireUiState("win-A").tabs.map { it.tabId })
    }

    @Test
    fun moveTabToWindow_mergesTabAndClosesSourceWindowWhenEmpty() {
        navigator.ensureWindow("win-A")
        val initialTabInA = requireUiState("win-A").activeTabId
        navigator.openWindow(requestedWindowId = "win-B", taskId = 202)
        navigator.navigate("win-B", BrowserRoute.Article(7, "Docked Article"))
        val tabInB = requireUiState("win-B").activeTabId

        navigator.moveTabToWindow(
            sourceWindowId = "win-B",
            tabId = tabInB,
            targetWindowId = "win-A",
        )

        assertNull(navigator.windowUiState("win-B").value)
        assertNull(navigator.taskIdForWindow("win-B"))

        val winA = requireUiState("win-A")
        assertEquals(listOf(initialTabInA, tabInB), winA.tabs.map { it.tabId })
        assertEquals(tabInB, winA.activeTabId)
        assertEquals(
            listOf(BrowserRoute.Home, BrowserRoute.Article(7, "Docked Article")),
            winA.activeBackStack,
        )
    }

    @Test
    fun moveTabToWindow_sameWindowOrUnknownIds_returnsFalse() {
        navigator.ensureWindow("win-A")
        navigator.ensureWindow("win-B")
        val tabInA = requireUiState("win-A").activeTabId

        assertFalse(navigator.moveTabToWindow("win-A", tabInA, "win-A"))
        assertFalse(navigator.moveTabToWindow("win-A", "tab-unknown", "win-B"))
        assertFalse(navigator.moveTabToWindow("win-A", tabInA, "win-unknown"))
        assertFalse(navigator.moveTabToWindow("win-unknown", tabInA, "win-B"))
    }

    @Test
    fun goBack_whenActiveTabHasMultipleRoutes_popsTopRoute() {
        navigator.ensureWindow("win-A")
        navigator.navigate("win-A", BrowserRoute.Search("kotlin"))
        assertEquals(2, requireUiState("win-A").activeBackStack.size)

        navigator.goBack("win-A")

        assertEquals(listOf(BrowserRoute.Home), requireUiState("win-A").activeBackStack)
    }

    @Test
    fun goBack_atRootOfOnlyRemainingTab_leavesWindowOpenForSystemBack() {
        navigator.ensureWindow("win-A")

        navigator.goBack("win-A")

        assertEquals(1, requireUiState("win-A").tabs.size)
        assertEquals(listOf(BrowserRoute.Home), requireUiState("win-A").activeBackStack)
    }

    @Test
    fun goBack_atRootWhenMultipleTabsExist_closesActiveTab() {
        navigator.ensureWindow("win-A")
        val firstTabId = requireUiState("win-A").activeTabId
        navigator.newTab("win-A", BrowserRoute.Home)
        assertEquals(2, requireUiState("win-A").tabs.size)

        navigator.goBack("win-A")

        val state = requireUiState("win-A")
        assertEquals(listOf(firstTabId), state.tabs.map { it.tabId })
        assertEquals(firstTabId, state.activeTabId)
    }

    @Test
    fun closeTab_inactiveTab_preservesActiveTabSelection() {
        navigator.ensureWindow("win-A")
        val tab1Id = requireUiState("win-A").activeTabId
        val tab2Id = navigator.newTab("win-A", BrowserRoute.Search("compose"))
        val tab3Id = navigator.newTab("win-A", BrowserRoute.Article(1, "Active"))

        navigator.closeTab("win-A", tab1Id)

        val state = requireUiState("win-A")
        assertEquals(listOf(tab2Id, tab3Id), state.tabs.map { it.tabId })
        assertEquals(tab3Id, state.activeTabId)
    }

    @Test
    fun closeTab_activeTab_selectsNeighboringRemainingTab() {
        navigator.ensureWindow("win-A")
        val tab1Id = requireUiState("win-A").activeTabId
        val tab2Id = navigator.newTab("win-A", BrowserRoute.Search("middle"))
        val tab3Id = navigator.newTab("win-A", BrowserRoute.Article(1, "end"))

        // Closing middle active tab selects next tab at same index (tab3Id)
        navigator.selectTab("win-A", tab2Id)
        navigator.closeTab("win-A", tab2Id)
        assertEquals(tab3Id, requireUiState("win-A").activeTabId)

        // Closing last-index active tab falls back to preceding tab (tab1Id)
        navigator.closeTab("win-A", tab3Id)
        assertEquals(tab1Id, requireUiState("win-A").activeTabId)
    }

    @Test
    fun closeTab_lastRemainingTab_closesWindowAndDetachesTask() {
        navigator.openWindow(requestedWindowId = "win-A", taskId = 101)
        val onlyTabId = requireUiState("win-A").activeTabId

        navigator.closeTab("win-A", onlyTabId)

        assertNull(navigator.windowUiState("win-A").value)
        assertNull(navigator.taskIdForWindow("win-A"))
    }

    @Test
    fun primeKeys_registersAllPersistedKeysWithEmptyDefaultValues() {
        val stateStore = StateStore()
        val isolatedNavigator = BrowserNavigator(stateStore = stateStore)
        assertEquals(0, stateStore.keys.size)

        isolatedNavigator.primeKeys()

        assertEquals(3, stateStore.keys.size)
        assertTrue(stateStore.keys.all { it is PersistToDataStore })
        assertNull(isolatedNavigator.firstUnattachedWindowId())
    }
    // [END_EXCLUDE]

    @Test
    fun scrollOffsets_preservedAcrossTabTransfersAndCleanedUpOnPopOrClose() {
        navigator.ensureWindow("win-A")
        val tab2Id = navigator.newTab("win-A", BrowserRoute.Article(1, "Article 1"))
        navigator.navigate("win-A", BrowserRoute.Search("compose"))
        navigator.navigate("win-A", BrowserRoute.Article(1, "Article 1"))

        val items = requireUiState("win-A").activeBackStackItems
        assertEquals(3, items.size)
        val firstArticleEntryId = items[0].entryId
        val searchEntryId = items[1].entryId
        val secondArticleEntryId = items[2].entryId

        val scrollStateHolder = navigator.scrollStateHolder
        scrollStateHolder.updateScrollOffset(firstArticleEntryId, 480, navigator::isActiveBackStackEntry)
        scrollStateHolder.updateScrollOffset(searchEntryId, 220, navigator::isActiveBackStackEntry)
        scrollStateHolder.updateScrollOffset(secondArticleEntryId, 960, navigator::isActiveBackStackEntry)

        // Pop out and move back: per-entry scroll offsets stay intact
        val newWindowId = checkNotNull(navigator.popOutTabToNewWindow("win-A", tab2Id))
        assertEquals(480, scrollStateHolder.scrollOffset(firstArticleEntryId))
        assertEquals(220, scrollStateHolder.scrollOffset(searchEntryId))
        assertEquals(960, scrollStateHolder.scrollOffset(secondArticleEntryId))

        navigator.moveTabToWindow(newWindowId, tab2Id, "win-A")
        assertEquals(960, scrollStateHolder.scrollOffset(secondArticleEntryId))

        // Popping top route clears only that entry's scroll offset
        navigator.goBack("win-A")
        assertEquals(0, scrollStateHolder.scrollOffset(secondArticleEntryId))
        assertEquals(480, scrollStateHolder.scrollOffset(firstArticleEntryId))
        assertEquals(220, scrollStateHolder.scrollOffset(searchEntryId))

        // Late onDispose write for already-popped entry is rejected by isActiveBackStackEntry
        scrollStateHolder.updateScrollOffset(secondArticleEntryId, 960, navigator::isActiveBackStackEntry)
        assertEquals(0, scrollStateHolder.scrollOffset(secondArticleEntryId))

        // Closing tab clears all remaining scroll offsets for that tab
        navigator.closeTab("win-A", tab2Id)
        assertEquals(0, scrollStateHolder.scrollOffset(firstArticleEntryId))
        assertEquals(0, scrollStateHolder.scrollOffset(searchEntryId))
    }

    // [START_EXCLUDE]
    @Test
    fun openWindow_withoutRequestedId_reattachesUnattachedWindowBeforeGeneratingFreshWindow() {
        // Simulate a persisted unattached window already in StateStore
        navigator.ensureWindow("win-restored", BrowserRoute.Article(9, "Restored"))

        // First launch without requestedWindowId reattaches to win-restored
        val firstOpened = navigator.openWindow(taskId = 101)
        assertEquals("win-restored", firstOpened)
        assertEquals(101, navigator.taskIdForWindow("win-restored"))

        // Second launch while win-restored is attached generates a new window ID
        val secondOpened = navigator.openWindow(taskId = 202)
        assertEquals("win-2", secondOpened)
        assertEquals(202, navigator.taskIdForWindow("win-2"))
    }

    @Test
    fun openWindow_withExplicitRequestedId_ensuresAndAttachesWindowAtomically() {
        val openedId = navigator.openWindow(requestedWindowId = "win-custom", taskId = 303)

        assertEquals("win-custom", openedId)
        assertEquals("win-custom", requireUiState("win-custom").windowId)
        assertEquals(303, navigator.taskIdForWindow("win-custom"))
    }

    @Test
    fun windowUiState_returnsConsolidatedWindowAndTabSummaries() {
        navigator.ensureWindow("win-A")
        navigator.ensureWindow("win-B")
        navigator.newTab("win-B", BrowserRoute.Search("compose"))
        navigator.newTab("win-B", BrowserRoute.Article(10, "Intro"))

        val winAUiState = requireUiState("win-A")
        assertEquals("Window 1", winAUiState.displayName)
        assertEquals(1, winAUiState.otherWindows.size)

        val winBSummary = winAUiState.otherWindows.single()
        assertEquals("win-B", winBSummary.windowId)
        assertEquals("Window 2", winBSummary.displayName)
        assertEquals(3, winBSummary.tabCount)
        assertEquals(listOf("Home", "Search: compose"), winBSummary.firstTabTitles)
    }

    @Test
    fun windowTitles_remainStableWhenEarlierWindowsCloseAndIncrementForNewWindows() {
        navigator.ensureWindow("win-A")
        navigator.newTab("win-A", BrowserRoute.Search("pop out"))
        val tab2Id = requireUiState("win-A").activeTabId
        val poppedWindowId = checkNotNull(navigator.popOutTabToNewWindow("win-A", tab2Id))

        assertEquals("Window 1", requireUiState("win-A").displayName)
        assertEquals("Window 2", requireUiState(poppedWindowId).displayName)

        // Closing Window 1 does not rename Window 2 to Window 1
        navigator.closeWindow("win-A")
        val remainingWin2 = requireUiState(poppedWindowId)
        assertEquals("Window 2", remainingWin2.displayName)
        assertEquals(listOf("Window 2"), remainingWin2.allWindows.map { it.displayName })

        // Opening another window while Window 2 is open assigns Window 3
        navigator.ensureWindow("win-C")
        assertEquals("Window 2", requireUiState(poppedWindowId).displayName)
        assertEquals("Window 3", requireUiState("win-C").displayName)
        assertEquals(
            listOf("Window 2", "Window 3"),
            requireUiState("win-C").allWindows.map { it.displayName },
        )

        // Moving the last tab of Window 2 into Window 3 closes Window 2 without renaming Window 3
        val tabInWin2 = requireUiState(poppedWindowId).activeTabId
        navigator.moveTabToWindow(poppedWindowId, tabInWin2, "win-C")
        assertNull(navigator.windowUiState(poppedWindowId).value)
        assertEquals("Window 3", requireUiState("win-C").displayName)

        // Closing all windows resets numbering back to Window 1 for the next fresh window
        navigator.closeWindow("win-C")
        navigator.ensureWindow("win-fresh")
        assertEquals("Window 1", requireUiState("win-fresh").displayName)
    }

    @Test
    fun taskTracking_reflectsAttachedActivityStateAndReassignsTasks() {
        navigator.ensureWindow("win-A")
        navigator.ensureWindow("win-B")
        navigator.attachWindowTask("win-A", taskId = 101)

        val initialSummaries = requireUiState("win-A").allWindows
        assertTrue(initialSummaries[0].isAttachedToActivity)
        assertFalse(initialSummaries[1].isAttachedToActivity)
        assertEquals("win-B", navigator.firstUnattachedWindowId())

        // Attaching the same taskId (101) to win-B automatically clears taskId 101 from win-A
        navigator.attachWindowTask("win-B", taskId = 101)
        assertNull(navigator.taskIdForWindow("win-A"))
        assertEquals(101, navigator.taskIdForWindow("win-B"))

        // Detaching with a non-matching taskId does not clear win-B's attachment
        navigator.detachWindowTask("win-B", taskId = 999)
        assertEquals(101, navigator.taskIdForWindow("win-B"))

        navigator.detachWindowTask("win-B", taskId = 101)
        assertNull(navigator.taskIdForWindow("win-B"))
    }

    @Test
    fun moveTab_reordersTabsClampsBoundsAndPreservesActiveTabAndScrollOffsets() {
        navigator.ensureWindow("win-A")
        val tab1Id = requireUiState("win-A").activeTabId
        val tab2Id = navigator.newTab("win-A", BrowserRoute.Search("compose"))
        val tab3Id = navigator.newTab("win-A", BrowserRoute.Article(7, "Drag Reorder"))
        navigator.navigate("win-A", BrowserRoute.Article(8, "Follow-up"))

        val entryId = requireUiState("win-A").activeBackStackItems.last().entryId
        navigator.scrollStateHolder.updateScrollOffset(entryId, 320, navigator::isActiveBackStackEntry)

        // Move tab1 to out-of-bounds positive index -> clamps to lastIndex (2)
        navigator.moveTab("win-A", tab1Id, toIndex = 99)
        val afterFirstMove = requireUiState("win-A")
        assertEquals(listOf(tab2Id, tab3Id, tab1Id), afterFirstMove.tabs.map { it.tabId })
        assertEquals(tab3Id, afterFirstMove.activeTabId)
        assertEquals(320, navigator.scrollStateHolder.scrollOffset(entryId))

        // Move tab3 to negative index -> clamps to 0
        navigator.moveTab("win-A", tab3Id, toIndex = -5)
        val afterSecondMove = requireUiState("win-A")
        assertEquals(listOf(tab3Id, tab2Id, tab1Id), afterSecondMove.tabs.map { it.tabId })
        assertEquals(
            listOf(BrowserRoute.Article(7, "Drag Reorder"), BrowserRoute.Article(8, "Follow-up")),
            afterSecondMove.activeBackStack,
        )
    }
    // [END_EXCLUDE]
}
// [END browser_navigator_test]
