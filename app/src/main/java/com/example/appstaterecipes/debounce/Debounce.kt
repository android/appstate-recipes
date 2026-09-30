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

package com.example.appstaterecipes.debounce

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.delay

const val DEFAULT_DEBOUNCE_TIMEOUT_MILLIS = 300L

// [START debounce_effect]
/**
 * Action Effect: Runs [onDebounced] only after [value] has remained unchanged for [timeoutMillis].
 *
 * If [value] or [timeoutMillis] changes while the timer is counting down—or while a previous
 * suspending [onDebounced] call is still in flight—the active coroutine is cancelled and the
 * timer restarts from zero (matching `Flow.debounce().collectLatest {}` semantics).
 *
 * Note that on initial composition, the timer starts for the initial [value] and invokes
 * [onDebounced] after [timeoutMillis] if [value] does not change.
 *
 * Inside an `androidx.appstate.transform.transform` block or UI composable, this is useful for
 * triggering asynchronous side effects (such as network search queries or autosaving drafts)
 * when user input has settled.
 *
 * @param T The type of value being debounced.
 * @param value The current input value to observe.
 * @param timeoutMillis The duration in milliseconds that [value] must remain unchanged before
 *   [onDebounced] is invoked.
 * @param onDebounced The suspending callback invoked with the debounced [value] once the timer
 *   elapses without interruption.
 */
@Composable
fun <T> DebounceEffect(
    value: T,
    timeoutMillis: Long,
    onDebounced: suspend (T) -> Unit,
) {
    require(timeoutMillis >= 0L) { "Debounce timeout must not be negative, was $timeoutMillis" }
    // Keep the latest callback reference without restarting the timer on recomposition
    val currentOnDebounced by rememberUpdatedState(onDebounced)

    LaunchedEffect(value, timeoutMillis) {
        delay(timeoutMillis)
        currentOnDebounced(value)
    }
}
// [END debounce_effect]

// [START remember_debounced]
/**
 * State Transformation: Returns a [State] initialized to [value] that updates to subsequent
 * values only after [timeoutMillis] has elapsed without further changes.
 *
 * Built on top of [DebounceEffect]. When [value] changes rapidly, intermediate values are dropped
 * and the returned [State] updates only when the input settles for at least [timeoutMillis].
 *
 * Inside an `androidx.appstate.transform.transform` block, this allows deriving filtered lists or
 * dependent state from rapidly changing input without constructing coroutine `Flow` pipelines.
 *
 * @param T The type of the state value.
 * @param value The input value to debounce.
 * @param timeoutMillis The silence window in milliseconds before updating the returned [State].
 * @return A [State] initialized to the starting [value] and updated with each debounced [value].
 */
@Composable
fun <T> rememberDebounced(
    value: T,
    timeoutMillis: Long,
): State<T> {
    val state = remember { mutableStateOf(value) }
    DebounceEffect(value, timeoutMillis) { debounced ->
        state.value = debounced
    }
    return state
}
// [END remember_debounced]
