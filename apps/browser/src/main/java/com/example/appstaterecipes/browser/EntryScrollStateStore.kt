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
import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.filter
import kotlinx.serialization.Serializable

@Serializable
internal object EntryScrollOffsetsKey :
    StateStoreKey<Map<String, Int>>(defaultValue = emptyMap()),
    PersistToDataStore

/**
 * Encapsulates the persistence and restoration of per-entry vertical scroll offsets
 * within [StateStore].
 */
@Stable
class EntryScrollStateStore(
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
     * Updates the saved scroll offset for [entryId] if [isValidEntry] evaluates to true.
     */
    fun updateScrollOffset(
        entryId: String,
        offsetPx: Int,
        isValidEntry: (String) -> Boolean = { true },
    ) {
        val coercedOffset = offsetPx.coerceAtLeast(0)
        Snapshot.withMutableSnapshot {
            if (!isValidEntry(entryId)) return@withMutableSnapshot
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
                offsets.filterKeys { key ->
                    val entryTabId = EntryId.parse(key)?.tabId ?: key.substringBeforeLast(':')
                    entryTabId !in tabIds
                }
            }
        }
    }
}

/**
 * CompositionLocal providing the current entry's [ScrollState] to child destinations.
 */
val LocalEntryScrollState = staticCompositionLocalOf<ScrollState?> { null }

/**
 * Remembers a [ScrollState] initialized with the saved offset for [entryId],
 * automatically saving updates on scroll idle and on disposal.
 */
@Composable
fun rememberEntryScrollState(
    entryId: String,
    scrollStore: EntryScrollStateStore,
    isValidEntry: (String) -> Boolean = { true },
): ScrollState {
    val scrollState = remember(entryId) {
        ScrollState(initial = scrollStore.scrollOffset(entryId))
    }
    val currentScrollStore by rememberUpdatedState(scrollStore)
    val currentIsValidEntry by rememberUpdatedState(isValidEntry)

    LaunchedEffect(entryId, scrollState) {
        snapshotFlow { scrollState.isScrollInProgress }
            .dropWhile { !it }
            .filter { !it }
            .collect {
                currentScrollStore.updateScrollOffset(entryId, scrollState.value, currentIsValidEntry)
            }
    }
    DisposableEffect(entryId, scrollState) {
        onDispose {
            currentScrollStore.updateScrollOffset(entryId, scrollState.value, currentIsValidEntry)
        }
    }
    return scrollState
}

/**
 * Scopes an entry's [ScrollState] to [content] via [LocalEntryScrollState].
 */
@Composable
fun ProvideEntryScrollState(
    entryId: String,
    scrollStore: EntryScrollStateStore,
    isValidEntry: (String) -> Boolean = { true },
    content: @Composable () -> Unit,
) {
    val scrollState = rememberEntryScrollState(
        entryId = entryId,
        scrollStore = scrollStore,
        isValidEntry = isValidEntry,
    )
    CompositionLocalProvider(LocalEntryScrollState provides scrollState) {
        content()
    }
}
