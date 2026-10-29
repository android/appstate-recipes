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

package com.example.appstaterecipes.browser.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class BrowserModelTest {

    @Test
    fun entryId_of_formatsTabIdAndStackIndex() {
        val entryId = EntryId.of("tab-123", 2)

        assertEquals("tab-123:2", entryId.value)
        assertEquals("tab-123", entryId.tabId)
        assertEquals(2, entryId.stackIndex)
    }

    @Test
    fun entryId_parse_extractsTabIdAndStackIndexIncludingColonsInTabId() {
        val standard = EntryId.parse("tab-abc:5")
        assertNotNull(standard)
        assertEquals("tab-abc", standard?.tabId)
        assertEquals(5, standard?.stackIndex)

        val nestedColonTabId = EntryId.parse("win-1:tab-abc:3")
        assertNotNull(nestedColonTabId)
        assertEquals("win-1:tab-abc", nestedColonTabId?.tabId)
        assertEquals(3, nestedColonTabId?.stackIndex)
    }

    @Test
    fun entryId_parse_invalidInputs_returnsNull() {
        assertNull(EntryId.parse(""))
        assertNull(EntryId.parse("invalid"))
        assertNull(EntryId.parse(":5"))
        assertNull(EntryId.parse("tab-abc:"))
        assertNull(EntryId.parse("tab-abc:notAnInt"))
    }

    @Test
    fun backStackItem_derivesEntryIdAndContentKey() {
        val item = BackStackItem(
            tabId = "tab-abc",
            stackIndex = 2,
            route = BrowserRoute.Article(42, "Structured BackStackItem"),
        )

        assertEquals("tab-abc", item.tabId)
        assertEquals(2, item.stackIndex)
        assertEquals("tab-abc:2", item.entryId)
        assertEquals(
            "tab-abc:2:Article(id=42, headline=Structured BackStackItem)",
            item.contentKey,
        )
        assertEquals("tab-abc", BackStackItem.tabIdFromContentKey(item.contentKey))
    }

    @Test
    fun backStackItem_tabIdFromContentKey_invalidKeys_returnsNull() {
        assertNull(BackStackItem.tabIdFromContentKey(null))
        assertNull(BackStackItem.tabIdFromContentKey(42))
        assertNull(BackStackItem.tabIdFromContentKey(""))
        assertNull(BackStackItem.tabIdFromContentKey(":0:Home"))
    }

    @Test
    fun windowState_emptyTabIds_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException::class.java) {
            WindowState(
                windowId = "win-1",
                tabIds = emptyList(),
                activeTabId = "tab-1",
            )
        }
    }

    @Test
    fun windowState_activeTabIdNotInTabIds_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException::class.java) {
            WindowState(
                windowId = "win-1",
                tabIds = listOf("tab-1", "tab-2"),
                activeTabId = "tab-missing",
            )
        }
    }

    @Test
    fun windowState_nonPositiveWindowNumber_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException::class.java) {
            WindowState(
                windowId = "win-1",
                tabIds = listOf("tab-1"),
                activeTabId = "tab-1",
                windowNumber = 0,
            )
        }
    }

    @Test
    fun persistedModels_roundTripThroughJsonSerialization() {
        val routes: List<BrowserRoute> = listOf(
            BrowserRoute.Home,
            BrowserRoute.Search("navigation 3"),
            BrowserRoute.Article(id = 7, headline = "StateStore Persistence"),
        )
        val encodedRoutes = Json.encodeToString(routes)
        assertEquals(routes, Json.decodeFromString<List<BrowserRoute>>(encodedRoutes))

        val windowState = WindowState(
            windowId = "win-2",
            tabIds = listOf("tab-1", "tab-2"),
            activeTabId = "tab-2",
            windowNumber = 2,
        )
        assertEquals("Window 2", windowState.displayName)
        val encodedWindow = Json.encodeToString(windowState)
        assertEquals(windowState, Json.decodeFromString<WindowState>(encodedWindow))
    }
}
