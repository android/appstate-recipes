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

import androidx.appstate.statestore.StateStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EntryScrollStateHolderTest {

    @Test
    fun scrollOffset_unsetEntry_returnsZero() {
        val scrollStateHolder = EntryScrollStateHolder(StateStore())

        assertEquals(0, scrollStateHolder.scrollOffset("tab-1:0"))
    }

    @Test
    fun updateScrollOffset_savesPerEntryPositionsAndClampsNegativeOffsets() {
        val stateStore = StateStore()
        val scrollStateHolder = EntryScrollStateHolder(stateStore)

        // Writing 0 or negative offset for an absent entry keeps map empty
        scrollStateHolder.updateScrollOffset("tab-1:0", -40)
        assertEquals(0, scrollStateHolder.scrollOffset("tab-1:0"))
        assertTrue(stateStore.getState(EntryScrollOffsetsKey).value.isEmpty())

        // Positive offsets are stored independently per entryId
        scrollStateHolder.updateScrollOffset("tab-1:0", 120)
        scrollStateHolder.updateScrollOffset("tab-1:1", 450)
        assertEquals(120, scrollStateHolder.scrollOffset("tab-1:0"))
        assertEquals(450, scrollStateHolder.scrollOffset("tab-1:1"))

        // Clamps negative updates on an existing entry to 0
        scrollStateHolder.updateScrollOffset("tab-1:0", -15)
        assertEquals(0, scrollStateHolder.scrollOffset("tab-1:0"))
    }

    @Test
    fun updateScrollOffset_whenEntryIsInactive_ignoresWrite() {
        val scrollStateHolder = EntryScrollStateHolder(StateStore())

        scrollStateHolder.updateScrollOffset(
            entryId = "tab-1:1",
            offsetPx = 450,
            isActiveEntry = { false },
        )

        assertEquals(0, scrollStateHolder.scrollOffset("tab-1:1"))
    }

    @Test
    fun clearEntry_removesOnlySpecifiedEntry() {
        val scrollStateHolder = EntryScrollStateHolder(StateStore())
        scrollStateHolder.updateScrollOffset("tab-1:0", 120)
        scrollStateHolder.updateScrollOffset("tab-1:1", 450)

        scrollStateHolder.clearEntry("tab-1:1")

        assertEquals(120, scrollStateHolder.scrollOffset("tab-1:0"))
        assertEquals(0, scrollStateHolder.scrollOffset("tab-1:1"))
    }

    @Test
    fun clearTabs_removesAllEntriesForSpecifiedTabsAndIgnoresEmptySet() {
        val scrollStateHolder = EntryScrollStateHolder(StateStore())
        scrollStateHolder.updateScrollOffset("tab-1:0", 120)
        scrollStateHolder.updateScrollOffset("tab-1:1", 450)
        scrollStateHolder.updateScrollOffset("tab-2:0", 300)

        scrollStateHolder.clearTabs(emptySet())
        assertEquals(120, scrollStateHolder.scrollOffset("tab-1:0"))

        scrollStateHolder.clearTabs(setOf("tab-1"))
        assertEquals(0, scrollStateHolder.scrollOffset("tab-1:0"))
        assertEquals(0, scrollStateHolder.scrollOffset("tab-1:1"))
        assertEquals(300, scrollStateHolder.scrollOffset("tab-2:0"))
    }
}
