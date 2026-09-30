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

package com.example.appstaterecipes.sample

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

// [START sample_effect]
private class SampleTracker<T>(var lastSeen: T) {
    val hasPending = AtomicBoolean(true)
}

/**
 * Action Effect: Periodically invokes [onSampled] with the latest [value] every [periodMillis].
 *
 * Unlike [com.example.appstaterecipes.debounce.DebounceEffect], rapid changes to [value] do not
 * reset the periodic timer. Instead, the timer runs on a fixed cadence and emits the most recent
 * value observed during each period window.
 *
 * On initial composition, the starting [value] is marked pending and emitted on the first tick
 * at [periodMillis] (or superseded by any newer [value] that arrives before the first tick). On
 * subsequent ticks, if [value] has not changed since the previous tick, [onSampled] is skipped.
 *
 * This is useful for rate-limiting continuous streams (such as sensor updates, touch drag
 * coordinates, or playback scrubber positions) before dispatching network calls or disk writes.
 *
 * @param T The type of value being sampled.
 * @param value The high-frequency value to sample.
 * @param periodMillis The sampling interval in milliseconds.
 * @param onSampled Suspending callback invoked periodically with the freshest [value].
 */
@Composable
fun <T> SampleEffect(
    value: T,
    periodMillis: Long,
    onSampled: suspend (T) -> Unit,
) {
    require(periodMillis > 0L) { "Sample period must be positive, was $periodMillis" }
    val latestValue by rememberUpdatedState(value)
    val currentOnSampled by rememberUpdatedState(onSampled)
    val tracker = remember { SampleTracker(value) }

    SideEffect {
        if (value != tracker.lastSeen) {
            tracker.lastSeen = value
            tracker.hasPending.set(true)
        }
    }

    LaunchedEffect(periodMillis) {
        while (isActive) {
            delay(periodMillis)
            if (tracker.hasPending.compareAndSet(true, false)) {
                currentOnSampled(latestValue)
            }
        }
    }
}
// [END sample_effect]

// [START remember_sampled]
/**
 * State Transformation: Returns a [State] initialized to [value] that samples the latest [value]
 * every [periodMillis], dropping intermediate updates between ticks.
 *
 * Built on top of [SampleEffect]. This throttles high-frequency updates (e.g. 60Hz sensor streams)
 * to a steady cadence (e.g. 2Hz / 500ms) for UI rendering or downstream calculation. Because the
 * returned [State] is backed by [mutableStateOf] with structural equality (`equals()`), ticks
 * whose sampled value equals the previous sampled state do not trigger downstream recomposition.
 *
 * Inside an `androidx.appstate.transform.transform` block, this provides an alternative to
 * `Flow.sample()` while maintaining Compose snapshot observation semantics.
 *
 * @param T The type of the sampled value.
 * @param value The high-frequency input value to sample.
 * @param periodMillis The interval in milliseconds between updates to the resulting [State].
 * @return A [State] initialized to the starting [value] and updated on each sample tick.
 */
@Composable
fun <T> rememberSampled(
    value: T,
    periodMillis: Long,
): State<T> {
    val state = remember { mutableStateOf(value) }
    SampleEffect(value, periodMillis) { sampled ->
        state.value = sampled
    }
    return state
}
// [END remember_sampled]
