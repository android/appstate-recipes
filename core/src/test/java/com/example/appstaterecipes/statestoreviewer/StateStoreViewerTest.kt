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

package com.example.appstaterecipes.statestoreviewer

import androidx.appstate.datastore.PersistToDataStore
import androidx.appstate.statestore.StateStore
import androidx.appstate.statestore.StateStoreKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StateStoreViewerTest {

    private object CounterKey : StateStoreKey<Int>(defaultValue = 0), PersistToDataStore

    private object EphemeralKey : StateStoreKey<List<String>>(
        defaultValue = emptyList(),
        autoClearKey = CounterKey,
    )

    @Test
    fun observeViewerUiState_tracksEmptyAndPopulatedState() {
        val stateStore = StateStore()
        val viewerState = stateStore.observeViewerUiState()

        // Initially empty
        assertEquals(0, viewerState.value.totalKeyCount)
        assertTrue(viewerState.value.entries.isEmpty())

        // Touch CounterKey with default value
        stateStore.getState(CounterKey)
        assertEquals(1, viewerState.value.totalKeyCount)
        assertEquals(1, viewerState.value.persistedKeyCount)
        assertEquals(0, viewerState.value.autoClearKeyCount)

        val initialCounterEntry = viewerState.value.entries.single()
        assertEquals("CounterKey", initialCounterEntry.keyName)
        assertTrue(initialCounterEntry.isPersistedToDataStore)
        assertTrue(initialCounterEntry.isDefaultValue)
        assertEquals("0", initialCounterEntry.formattedValue)

        // Update CounterKey and set EphemeralKey
        stateStore.setState(CounterKey, 42)
        stateStore.setState(EphemeralKey, listOf("alpha", "beta"))

        assertEquals(2, viewerState.value.totalKeyCount)
        assertEquals(1, viewerState.value.persistedKeyCount)
        assertEquals(1, viewerState.value.autoClearKeyCount)

        val entriesByName = viewerState.value.entries.associateBy { it.keyName }
        val updatedCounter = entriesByName.getValue("CounterKey")
        assertFalse(updatedCounter.isDefaultValue)
        assertEquals("42", updatedCounter.formattedValue)

        val ephemeral = entriesByName.getValue("EphemeralKey")
        assertFalse(ephemeral.isPersistedToDataStore)
        assertEquals("CounterKey", ephemeral.autoClearKeyName)
        assertEquals("[\n  \"alpha\",\n  \"beta\"\n]", ephemeral.formattedValue)
    }

    @Test
    fun formatStateValue_formatsNestedMapsAndLists() {
        val nested = mapOf(
            "win-1" to listOf("tab-1", "tab-2"),
            "empty" to emptyList<String>(),
        )
        val formatted = formatStateValue(nested)
        val expected = """
            {
              "win-1": [
                "tab-1",
                "tab-2"
              ],
              "empty": []
            }
        """.trimIndent()
        assertEquals(expected, formatted)
    }
}
