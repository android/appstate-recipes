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

import androidx.appstate.transform.testing.transformTest
import androidx.appstate.transform.transform
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import org.junit.Assert.assertEquals
import org.junit.Test

class DebounceTest {

    @Test
    fun debounceEffect_invokesCallbackAfterTimeout() = transformTest {
        var input by mutableStateOf("initial")
        val emissions = mutableListOf<String>()

        transform(
            initialValue = Unit,
            scope = backgroundScope,
            dispatcher = testDispatcher,
        ) {
            DebounceEffect(input, timeoutMillis = 300L) { debounced ->
                emissions.add(debounced)
            }
        }
        advance()

        advanceTimeBy(100.milliseconds)
        advance()
        assertEquals(emptyList<String>(), emissions)

        advanceTimeBy(200.milliseconds)
        advance()
        assertEquals(listOf("initial"), emissions)
    }

    @Test
    fun debounceEffect_rapidChangesResetTimer() = transformTest {
        var input by mutableStateOf("a")
        val emissions = mutableListOf<String>()

        transform(
            initialValue = Unit,
            scope = backgroundScope,
            dispatcher = testDispatcher,
        ) {
            DebounceEffect(input, timeoutMillis = 300L) { debounced ->
                emissions.add(debounced)
            }
        }
        advance()

        // Change input before 300ms completes
        advanceTimeBy(200.milliseconds)
        advance()
        input = "ab"
        advance()

        // Change again before another 300ms completes
        advanceTimeBy(200.milliseconds)
        advance()
        input = "abc"
        advance()

        // 200ms after last change: still resetting
        advanceTimeBy(200.milliseconds)
        advance()
        assertEquals(emptyList<String>(), emissions)

        // Allow 300ms from the last change
        advanceTimeBy(100.milliseconds)
        advance()
        assertEquals(listOf("abc"), emissions)
    }

    @Test
    fun rememberDebounced_projectsDebouncedState() = transformTest {
        var input by mutableStateOf("start")

        val debouncedState = transform(
            initialValue = "start",
            scope = backgroundScope,
            dispatcher = testDispatcher,
        ) {
            val debounced by rememberDebounced(input, timeoutMillis = 300L)
            debounced
        }
        advance()

        assertEquals("start", debouncedState.value)

        input = "updated"
        advance()

        advanceTimeBy(150.milliseconds)
        advance()
        assertEquals("start", debouncedState.value)

        advanceTimeBy(150.milliseconds)
        advance()
        assertEquals("updated", debouncedState.value)
    }

    @Test
    fun rememberDebounced_structuralEqualityPreventsRedundantUpdates() = transformTest {
        var input by mutableStateOf("A")

        val debouncedState = transform(
            initialValue = "A",
            scope = backgroundScope,
            dispatcher = testDispatcher,
        ) {
            val debounced by rememberDebounced(input, timeoutMillis = 300L)
            debounced
        }
        advance()

        var readerInvocations = 0
        transform(
            initialValue = Unit,
            scope = backgroundScope,
            dispatcher = testDispatcher,
        ) {
            debouncedState.value
            readerInvocations++
        }
        advance()

        assertEquals(1, readerInvocations)

        // Change to B
        input = "B"
        advance()
        advanceTimeBy(300.milliseconds)
        advance()
        assertEquals(2, readerInvocations)

        // Change back to A
        input = "A"
        advance()
        advanceTimeBy(300.milliseconds)
        advance()
        assertEquals(3, readerInvocations)

        // Set state to "B" and quickly back to "A" before timeout:
        // Even if an update completes with "A" (equals previous value),
        // mutableStateOf(value) structural equality policy does not trigger observer recomposition
        input = "B"
        advance()
        advanceTimeBy(100.milliseconds)
        advance()
        input = "A"
        advance()
        advanceTimeBy(300.milliseconds)
        advance()
        assertEquals(3, readerInvocations)
    }

    @Test
    fun debounceEffect_cancelsSuspendingCallbackMidFlight() = transformTest {
        var input by mutableStateOf("first")
        var startedCallback = false
        var completedCallback = false

        transform(
            initialValue = Unit,
            scope = backgroundScope,
            dispatcher = testDispatcher,
        ) {
            DebounceEffect(input, timeoutMillis = 300L) {
                startedCallback = true
                delay(200L)
                completedCallback = true
            }
        }
        advance()

        // Advance 300ms so DebounceEffect timeout completes and starts the callback
        advanceTimeBy(300.milliseconds)
        advance()
        assertEquals(true, startedCallback)
        assertEquals(false, completedCallback)

        // Input changes while callback is suspended in delay(200L)
        // LaunchedEffect(value, timeoutMillis) restarts, cancelling previous coroutine
        input = "second"
        advance()

        // Advance enough time that the original callback would have finished if not cancelled
        advanceTimeBy(250.milliseconds)
        advance()
        assertEquals(false, completedCallback)
    }

    @Test
    fun debounceEffect_rememberUpdatedStateSwapsLambdaWithoutResettingTimer() = transformTest {
        var input by mutableStateOf("v")
        var callbackInvocation = 0
        var callbackToRun: (String) -> Unit by mutableStateOf({ callbackInvocation = 1 })

        transform(
            initialValue = Unit,
            scope = backgroundScope,
            dispatcher = testDispatcher,
        ) {
            val currentCallback = callbackToRun
            DebounceEffect(input, timeoutMillis = 300L) {
                currentCallback(it)
            }
        }
        advance()

        // At 200ms, swap callback lambda without changing input or timeoutMillis
        advanceTimeBy(200.milliseconds)
        advance()
        callbackToRun = { callbackInvocation = 2 }
        advance()

        // Timer should NOT have been reset by lambda change; should fire at 300ms total (+100ms)
        advanceTimeBy(100.milliseconds)
        advance()
        assertEquals(2, callbackInvocation)
    }

    @Test(expected = IllegalArgumentException::class)
    fun debounceEffect_throwsOnNegativeTimeout() = transformTest {
        transform(
            initialValue = Unit,
            scope = backgroundScope,
            dispatcher = testDispatcher,
        ) {
            DebounceEffect("start", timeoutMillis = -1L) {}
        }
    }
}
