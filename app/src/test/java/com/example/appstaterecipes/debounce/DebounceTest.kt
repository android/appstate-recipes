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

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.appstaterecipes.runTransformTest
import kotlinx.coroutines.delay
import org.junit.Assert.assertEquals
import org.junit.Test

class DebounceTest {

    @Test
    fun debounceEffect_invokesCallbackAfterTimeout() = runTransformTest {
        var input by mutableStateOf("initial")
        val emissions = mutableListOf<String>()

        testTransform(initialValue = Unit) {
            DebounceEffect(input, timeoutMillis = 300L) { debounced ->
                emissions.add(debounced)
            }
        }

        advanceTimeBy(100)
        assertEquals(emptyList<String>(), emissions)

        advanceTimeBy(200)
        assertEquals(listOf("initial"), emissions)
    }

    @Test
    fun debounceEffect_rapidChangesResetTimer() = runTransformTest {
        var input by mutableStateOf("a")
        val emissions = mutableListOf<String>()

        testTransform(initialValue = Unit) {
            DebounceEffect(input, timeoutMillis = 300L) { debounced ->
                emissions.add(debounced)
            }
        }

        // Change input before 300ms completes
        advanceTimeBy(200)
        input = "ab"
        sync()

        // Change again before another 300ms completes
        advanceTimeBy(200)
        input = "abc"
        sync()

        // 200ms after last change: still resetting
        advanceTimeBy(200)
        assertEquals(emptyList<String>(), emissions)

        // Allow 300ms from the last change
        advanceTimeBy(100)
        assertEquals(listOf("abc"), emissions)
    }

    @Test
    fun rememberDebounced_projectsDebouncedState() = runTransformTest {
        var input by mutableStateOf("start")

        val debouncedState = testTransform(initialValue = "start") {
            val debounced by rememberDebounced(input, timeoutMillis = 300L)
            debounced
        }

        assertEquals("start", debouncedState.value)

        input = "updated"
        sync()

        advanceTimeBy(150)
        assertEquals("start", debouncedState.value)

        advanceTimeBy(150)
        assertEquals("updated", debouncedState.value)
    }

    @Test
    fun rememberDebounced_structuralEqualityPreventsRedundantUpdates() = runTransformTest {
        var input by mutableStateOf("A")

        val debouncedState = testTransform(initialValue = "A") {
            val debounced by rememberDebounced(input, timeoutMillis = 300L)
            debounced
        }

        var readerInvocations = 0
        testTransform(initialValue = 0) {
            val current = debouncedState.value
            readerInvocations++
            current.length
        }

        assertEquals(1, readerInvocations)

        // Change to B
        input = "B"
        sync()
        advanceTimeBy(300)
        assertEquals(2, readerInvocations)

        // Change back to A
        input = "A"
        sync()
        advanceTimeBy(300)
        assertEquals(3, readerInvocations)

        // Set state to "B" and quickly back to "A" before timeout:
        // Even if an update completes with "A" (equals previous value),
        // mutableStateOf(value) structural equality policy does not trigger observer recomposition
        input = "B"
        sync()
        advanceTimeBy(100)
        input = "A"
        sync()
        advanceTimeBy(300)
        assertEquals(3, readerInvocations)
    }

    @Test
    fun debounceEffect_cancelsSuspendingCallbackMidFlight() = runTransformTest {
        var input by mutableStateOf("first")
        var startedCallback = false
        var completedCallback = false

        testTransform(initialValue = Unit) {
            DebounceEffect(input, timeoutMillis = 300L) {
                startedCallback = true
                delay(200L)
                completedCallback = true
            }
        }

        // Advance 300ms so DebounceEffect timeout completes and starts the callback
        advanceTimeBy(300)
        assertEquals(true, startedCallback)
        assertEquals(false, completedCallback)

        // Input changes while callback is suspended in delay(200L)
        // LaunchedEffect(value, timeoutMillis) restarts, cancelling previous coroutine
        input = "second"
        sync()

        // Advance enough time that the original callback would have finished if not cancelled
        advanceTimeBy(250)
        assertEquals(false, completedCallback)
    }

    @Test
    fun debounceEffect_rememberUpdatedStateSwapsLambdaWithoutResettingTimer() = runTransformTest {
        var input by mutableStateOf("v")
        var callbackInvocation = 0
        var callbackToRun: (String) -> Unit by mutableStateOf({ callbackInvocation = 1 })

        testTransform(initialValue = Unit) {
            val currentCallback = callbackToRun
            DebounceEffect(input, timeoutMillis = 300L) {
                currentCallback(it)
            }
        }

        // At 200ms, swap callback lambda without changing input or timeoutMillis
        advanceTimeBy(200)
        callbackToRun = { callbackInvocation = 2 }
        sync()

        // Timer should NOT have been reset by lambda change; should fire at 300ms total (+100ms)
        advanceTimeBy(100)
        assertEquals(2, callbackInvocation)
    }

    @Test(expected = IllegalArgumentException::class)
    fun debounceEffect_throwsOnNegativeTimeout() = runTransformTest {
        testTransform(initialValue = Unit) {
            DebounceEffect("start", timeoutMillis = -1L) {}
        }
    }
}
