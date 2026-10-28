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

import com.example.appstaterecipes.browser.model.BrowserRoute
import com.example.appstaterecipes.browser.model.EntryId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserWindowSessionTest {

    private var nextId = 1
    private val navigator = BrowserNavigator(idGenerator = { (nextId++).toString() })

    @Test
    fun sessionActions_delegateToWindowScopedNavigatorOperations() {
        navigator.ensureWindow("win-A")
        val session = navigator.windowSession("win-A")
        val tab1Id = checkNotNull(session.uiState.value).activeTabId

        val tab2Id = session.newTab(BrowserRoute.Home)
        session.navigate(BrowserRoute.Search("kotlin"))
        assertEquals(2, session.uiState.value?.activeBackStack?.size)

        val pushedEntryId = EntryId.of(tab2Id, 1).value
        assertTrue(session.isActiveBackStackEntry(pushedEntryId))

        session.goBack()
        assertEquals(1, session.uiState.value?.activeBackStack?.size)
        assertFalse(session.isActiveBackStackEntry(pushedEntryId))

        session.selectTab(tab1Id)
        assertEquals(tab1Id, session.uiState.value?.activeTabId)

        session.moveTab(tab2Id, toIndex = 0)
        assertEquals(listOf(tab2Id, tab1Id), session.uiState.value?.tabs?.map { it.tabId })

        val poppedOutWinId = session.popOutTab(tab2Id)
        assertNotNull(poppedOutWinId)
        assertEquals(listOf(tab1Id), session.uiState.value?.tabs?.map { it.tabId })

        session.closeTab(tab1Id)
        assertNull(session.uiState.value)
    }

    @Test
    fun receiveTabFromWindow_whenSourceStillHasRemainingTabs_returnsFalse() {
        navigator.ensureWindow("win-A")
        navigator.ensureWindow("win-B")
        val sessionA = navigator.windowSession("win-A")
        val sessionB = navigator.windowSession("win-B")
        val secondTabInA = sessionA.newTab(BrowserRoute.Search("compose"))

        val shouldSwitch = sessionB.receiveTabFromWindow("win-A", secondTabInA)

        assertFalse(shouldSwitch)
        assertNotNull(sessionA.uiState.value)
        assertEquals(secondTabInA, sessionB.uiState.value?.activeTabId)
    }

    @Test
    fun receiveTabFromWindow_whenLastTabMovedToUnattachedWindow_returnsTrueAndClosesSource() {
        navigator.ensureWindow("win-A")
        navigator.ensureWindow("win-B") // unattached to any taskId
        val sessionA = navigator.windowSession("win-A")
        val sessionB = navigator.windowSession("win-B")
        val onlyTabInA = checkNotNull(sessionA.uiState.value).activeTabId

        val shouldSwitch = sessionB.receiveTabFromWindow("win-A", onlyTabInA)

        assertTrue(shouldSwitch)
        assertNull(sessionA.uiState.value)
        assertEquals(onlyTabInA, sessionB.uiState.value?.activeTabId)
    }

    @Test
    fun receiveTabFromWindow_whenLastTabMovedToAttachedWindow_returnsFalse() {
        navigator.ensureWindow("win-A")
        navigator.openWindow(requestedWindowId = "win-B", taskId = 202)
        val sessionA = navigator.windowSession("win-A")
        val sessionB = navigator.windowSession("win-B")
        val onlyTabInA = checkNotNull(sessionA.uiState.value).activeTabId

        val shouldSwitch = sessionB.receiveTabFromWindow("win-A", onlyTabInA)

        assertFalse(shouldSwitch)
        assertNull(sessionA.uiState.value)
        assertEquals(onlyTabInA, sessionB.uiState.value?.activeTabId)
    }
}
