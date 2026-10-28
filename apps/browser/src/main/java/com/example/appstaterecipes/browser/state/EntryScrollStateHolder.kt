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
import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import com.example.appstaterecipes.browser.model.EntryId
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.filter
import kotlinx.serialization.Serializable

// [START browser_scroll_state_holder]
@Serializable
internal object EntryScrollOffsetsKey :
    StateStoreKey<Map<String, Int>>(defaultValue = emptyMap()),
    PersistToDataStore

/**
 * Encapsulates the persistence and restoration of per-entry vertical scroll offsets
 * within [StateStore].
 */
@Stable
class EntryScrollStateHolder(
    private val stateStore: StateStore = StateStore(),
) {
    /**
     * Registers the scroll offsets key with [StateStore] for DataStore synchronization.
     */
    fun primeKey() {
        stateStore.getState(EntryScrollOffsetsKey)
    }

    /**
     * Returns the saved vertical scroll offset in pixels for [entryId], or `0` if none.
     * Reads without registering a Compose snapshot observer to prevent unnecessary recompositions.
     */
    fun scrollOffset(entryId: String): Int = Snapshot.withoutReadObservation {
        stateStore.getState(EntryScrollOffsetsKey).value[entryId] ?: 0
    }

    /**
     * Updates the saved scroll offset for [entryId] if [isActiveEntry] evaluates to true.
     */
    fun updateScrollOffset(
        entryId: String,
        offsetPx: Int,
        isActiveEntry: (String) -> Boolean = { true },
    ) {
        val coercedOffset = offsetPx.coerceAtLeast(0)
        Snapshot.withMutableSnapshot {
            if (!isActiveEntry(entryId)) return@withMutableSnapshot
            stateStore.updateState(EntryScrollOffsetsKey) { offsets ->
                if (coercedOffset == 0 && entryId !in offsets) {
                    offsets
                } else if (offsets[entryId] == coercedOffset) {
                    offsets
                } else {
                    offsets + (entryId to coercedOffset)
                }
            }
        }
    }

    /**
     * Removes the recorded scroll offset for a specific [entryId] (e.g. when popped).
     */
    fun clearEntry(entryId: String) {
        Snapshot.withMutableSnapshot {
            stateStore.updateState(EntryScrollOffsetsKey) { offsets ->
                offsets - entryId
            }
        }
    }

    /**
     * Removes recorded scroll offsets for all entries belonging to [tabIds].
     */
    fun clearTabs(tabIds: Set<String>) {
        if (tabIds.isEmpty()) return
        Snapshot.withMutableSnapshot {
            stateStore.updateState(EntryScrollOffsetsKey) { offsets ->
                offsets.filterKeys { entryId ->
                    val entryTabId = EntryId.parse(entryId)?.tabId ?: entryId.substringBeforeLast(':')
                    entryTabId !in tabIds
                }
            }
        }
    }
}

/**
 * Remembers a [ScrollState] initialized with the saved offset for [entryId],
 * automatically saving updates on scroll idle and on disposal.
 */
@Composable
fun rememberEntryScrollState(
    entryId: String,
    scrollStateHolder: EntryScrollStateHolder,
    isActiveEntry: (String) -> Boolean = { true },
): ScrollState {
    val scrollState = remember(entryId) {
        ScrollState(initial = scrollStateHolder.scrollOffset(entryId))
    }
    val currentScrollStateHolder by rememberUpdatedState(scrollStateHolder)
    val currentIsActiveEntry by rememberUpdatedState(isActiveEntry)

    LaunchedEffect(entryId, scrollState) {
        snapshotFlow { scrollState.isScrollInProgress }
            .dropWhile { !it }
            .filter { !it }
            .collect {
                currentScrollStateHolder.updateScrollOffset(entryId, scrollState.value, currentIsActiveEntry)
            }
    }
    DisposableEffect(entryId, scrollState) {
        onDispose {
            currentScrollStateHolder.updateScrollOffset(entryId, scrollState.value, currentIsActiveEntry)
        }
    }
    return scrollState
}
// [END browser_scroll_state_holder]
