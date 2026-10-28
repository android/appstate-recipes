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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember

/**
 * Snapshot of a single [StateStoreKey] entry and its current value in [StateStore].
 */
@Immutable
data class StateStoreEntryUiState(
    val keyName: String,
    val qualifiedKeyName: String?,
    val valueTypeName: String,
    val isPersistedToDataStore: Boolean,
    val autoClearKeyName: String?,
    val isDefaultValue: Boolean,
    val formattedValue: String,
    val formattedDefaultValue: String,
)

/**
 * Overall UI state for the StateStore viewer.
 */
@Immutable
data class StateStoreViewerUiState(
    val entries: List<StateStoreEntryUiState> = emptyList(),
) {
    val totalKeyCount: Int get() = entries.size
    val persistedKeyCount: Int get() = entries.count { it.isPersistedToDataStore }
    val autoClearKeyCount: Int get() = entries.count { it.autoClearKeyName != null }
}

/**
 * Creates a reactive [State] of [StateStoreViewerUiState] that automatically updates whenever
 * keys are added/removed in [StateStore] or when the value of any registered [StateStoreKey] changes.
 */
fun StateStore.observeViewerUiState(): State<StateStoreViewerUiState> = derivedStateOf {
    val entries = keys
        .map { key -> key.toEntryUiState(this) }
        .sortedBy { it.qualifiedKeyName ?: it.keyName }
    StateStoreViewerUiState(entries = entries)
}

/**
 * Composable helper that remembers the reactive [StateStoreViewerUiState] for [stateStore].
 */
@Composable
fun rememberStateStoreViewerUiState(stateStore: StateStore): State<StateStoreViewerUiState> {
    return remember(stateStore) { stateStore.observeViewerUiState() }
}

private fun StateStoreKey<*>.toEntryUiState(stateStore: StateStore): StateStoreEntryUiState {
    @Suppress("UNCHECKED_CAST")
    val currentValue = stateStore.getState(this as StateStoreKey<Any?>).value
    val defaultValue = this.defaultValue
    val keyName = this::class.simpleName ?: this.toString()
    val qualifiedKeyName = this::class.qualifiedName
    val valueTypeName = currentValue?.let { it::class.simpleName }
        ?: defaultValue?.let { it::class.simpleName }
        ?: "Any?"

    return StateStoreEntryUiState(
        keyName = keyName,
        qualifiedKeyName = qualifiedKeyName,
        valueTypeName = valueTypeName,
        isPersistedToDataStore = this is PersistToDataStore,
        autoClearKeyName = autoClearKey?.let { it::class.simpleName ?: it.toString() },
        isDefaultValue = currentValue == defaultValue,
        formattedValue = formatStateValue(currentValue),
        formattedDefaultValue = formatStateValue(defaultValue),
    )
}

/**
 * Formats an arbitrary state value into a human-readable, indented multi-line string
 * suitable for inspection in the StateStore Viewer.
 */
internal fun formatStateValue(value: Any?, indentLevel: Int = 0): String {
    val indent = "  ".repeat(indentLevel)
    val childIndent = "  ".repeat(indentLevel + 1)

    return when (value) {
        null -> "null"
        is String -> "\"$value\""
        is Char -> "'$value'"
        is Number, is Boolean -> value.toString()
        is Map<*, *> -> {
            if (value.isEmpty()) {
                "{}"
            } else {
                buildString {
                    appendLine("{")
                    val entries = value.entries.toList()
                    entries.forEachIndexed { index, (mapKey, mapValue) ->
                        val formattedKey = formatStateValue(mapKey, indentLevel = 0)
                        val formattedValue = formatStateValue(mapValue, indentLevel = indentLevel + 1)
                        val suffix = if (index < entries.lastIndex) "," else ""
                        append("$childIndent$formattedKey: $formattedValue$suffix")
                        if (index < entries.lastIndex) appendLine()
                    }
                    appendLine()
                    append("$indent}")
                }
            }
        }
        is Iterable<*> -> {
            val list = value.toList()
            if (list.isEmpty()) {
                "[]"
            } else {
                buildString {
                    appendLine("[")
                    list.forEachIndexed { index, item ->
                        val formattedItem = formatStateValue(item, indentLevel = indentLevel + 1)
                        val suffix = if (index < list.lastIndex) "," else ""
                        append("$childIndent$formattedItem$suffix")
                        if (index < list.lastIndex) appendLine()
                    }
                    appendLine()
                    append("$indent]")
                }
            }
        }
        is Array<*> -> formatStateValue(value.toList(), indentLevel)
        else -> value.toString()
    }
}
