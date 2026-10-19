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

import androidx.appstate.transform.testing.transformTest
import androidx.appstate.transform.transform
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import org.junit.Assert.assertEquals
import org.junit.Test

class SampleTest {

    @Test
    fun sampleEffect_periodicallySamplesLatestValue() = transformTest {
        var input by mutableStateOf("v0")
        val emissions = mutableListOf<String>()

        transform(
            initialValue = Unit,
            scope = backgroundScope,
            dispatcher = testDispatcher,
        ) {
            SampleEffect(input, periodMillis = 500L) { sampled ->
                emissions.add(sampled)
            }
        }
        advance()

        // 200ms: value changes to v1
        advanceTimeBy(200.milliseconds)
        advance()
        input = "v1"
        advance()

        // 400ms: value changes to v2
        advanceTimeBy(200.milliseconds)
        advance()
        input = "v2"
        advance()

        // At 500ms, period completes and should capture latest value ("v2")
        advanceTimeBy(100.milliseconds)
        advance()
        assertEquals(listOf("v2"), emissions)
    }

    @Test
    fun sampleEffect_doesNotEmitWhenNoPendingUpdate() = transformTest {
        var input by mutableStateOf("v0")
        val emissions = mutableListOf<String>()

        transform(
            initialValue = Unit,
            scope = backgroundScope,
            dispatcher = testDispatcher,
        ) {
            SampleEffect(input, periodMillis = 500L) { sampled ->
                emissions.add(sampled)
            }
        }
        advance()

        // First period emits initial value
        advanceTimeBy(500.milliseconds)
        advance()
        assertEquals(listOf("v0"), emissions)

        // Second period without changes: should not emit again
        advanceTimeBy(500.milliseconds)
        advance()
        assertEquals(listOf("v0"), emissions)

        // Change occurs: next tick should emit
        input = "v1"
        advance()

        advanceTimeBy(500.milliseconds)
        advance()
        assertEquals(listOf("v0", "v1"), emissions)
    }

    @Test
    fun rememberSampled_projectsSampledState() = transformTest {
        var input by mutableStateOf(Acceleration(1f, 1f, 1f))

        val sampledState = transform(
            initialValue = Acceleration(1f, 1f, 1f),
            scope = backgroundScope,
            dispatcher = testDispatcher,
        ) {
            val sampled by rememberSampled(input, periodMillis = 500L)
            sampled
        }
        advance()

        assertEquals(Acceleration(1f, 1f, 1f), sampledState.value)

        // Multiple rapid changes
        input = Acceleration(2f, 2f, 2f)
        advance()
        advanceTimeBy(200.milliseconds)
        advance()

        input = Acceleration(3f, 3f, 3f)
        advance()

        // Sampled state shouldn't have changed yet
        assertEquals(Acceleration(1f, 1f, 1f), sampledState.value)

        // Ticks after 500ms
        advanceTimeBy(300.milliseconds)
        advance()
        assertEquals(Acceleration(3f, 3f, 3f), sampledState.value)
    }

    @Test
    fun rememberSampled_structuralEqualityPreventsRedundantUpdates() = transformTest {
        var input by mutableStateOf("A")

        val sampledState = transform(
            initialValue = "A",
            scope = backgroundScope,
            dispatcher = testDispatcher,
        ) {
            val sampled by rememberSampled(input, periodMillis = 300L)
            sampled
        }
        advance()

        var readerInvocations = 0
        transform(
            initialValue = Unit,
            scope = backgroundScope,
            dispatcher = testDispatcher,
        ) {
            sampledState.value
            readerInvocations++
        }
        advance()

        advanceTimeBy(300.milliseconds)
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

        // Set state to "B" and back to "A" within the sample period:
        // When tick arrives with "A" (equals previous value),
        // mutableStateOf(value) structural equality policy does not trigger observer recomposition
        input = "B"
        advance()
        advanceTimeBy(100.milliseconds)
        advance()
        input = "A"
        advance()
        advanceTimeBy(200.milliseconds)
        advance()
        assertEquals(3, readerInvocations)
    }

    @Test
    fun sampleViewModel_fineGrainedInvalidationOfSampleUiState() = transformTest {
        var rawInput by mutableStateOf(Acceleration(0f, 0f, 0f))

        val uiState = transform(
            initialValue = SampleUiState(),
            scope = backgroundScope,
            dispatcher = testDispatcher,
        ) { state ->
            val sampled1000ms by rememberSampled(rawInput, periodMillis = 1000L)

            state.raw = rawInput
            state.sampled1000ms = sampled1000ms
            state
        }
        advance()

        var sampled1000msReaderRecompositions = 0

        // Sub-composition / observer that only reads sampled1000ms
        transform(
            initialValue = Unit,
            scope = backgroundScope,
            dispatcher = testDispatcher,
        ) {
            val state = uiState.value
            state.sampled1000ms
            sampled1000msReaderRecompositions++
        }
        advance()

        assertEquals(1, sampled1000msReaderRecompositions)

        // Rapid churn of raw updates (10 * 50ms = 500ms elapsed)
        for (i in 1..10) {
            rawInput = Acceleration(i.toFloat(), 0f, 0f)
            advance()
            advanceTimeBy(50.milliseconds)
            advance()
        }

        // Observers of sampled1000ms should NOT have recomposed despite raw churning
        assertEquals(1, sampled1000msReaderRecompositions)

        // Reach the exact 1000ms sample window (+500ms)
        advanceTimeBy(500.milliseconds)
        advance()
        // Now sampled1000ms updates to the latest raw value, triggering observer
        assertEquals(2, sampled1000msReaderRecompositions)
    }

    @Test
    fun sampleEffect_cancelsSuspendingCallbackMidFlightOnScopeDisposal() = transformTest {
        var active by mutableStateOf(true)
        var startedCallback = false
        var completedCallback = false

        transform(
            initialValue = Unit,
            scope = backgroundScope,
            dispatcher = testDispatcher,
        ) {
            if (active) {
                SampleEffect(value = "v", periodMillis = 300L) {
                    startedCallback = true
                    delay(200L)
                    completedCallback = true
                }
            }
        }
        advance()

        advanceTimeBy(300.milliseconds)
        advance()
        assertEquals(true, startedCallback)
        assertEquals(false, completedCallback)

        // Dispose effect while callback is suspended in delay
        active = false
        advance()

        advanceTimeBy(300.milliseconds)
        advance()
        assertEquals(false, completedCallback)
    }

    @Test
    fun sampleEffect_rememberUpdatedStateSwapsLambdaWithoutResettingTimer() = transformTest {
        var input by mutableStateOf("v0")
        var callbackInvocation = 0
        var callbackToRun: (String) -> Unit by mutableStateOf({ callbackInvocation = 1 })

        transform(
            initialValue = Unit,
            scope = backgroundScope,
            dispatcher = testDispatcher,
        ) {
            val currentCallback = callbackToRun
            SampleEffect(input, periodMillis = 300L) {
                currentCallback(it)
            }
        }
        advance()

        // At 200ms, change callback and value
        advanceTimeBy(200.milliseconds)
        advance()
        callbackToRun = { callbackInvocation = 2 }
        input = "v1"
        advance()

        // Timer period is 300ms, not reset by callback swap or value change; fires at total 300ms (+100ms)
        advanceTimeBy(100.milliseconds)
        advance()
        assertEquals(2, callbackInvocation)
    }

    @Test(expected = IllegalArgumentException::class)
    fun sampleEffect_throwsOnNonPositivePeriod() = transformTest {
        transform(
            initialValue = Unit,
            scope = backgroundScope,
            dispatcher = testDispatcher,
        ) {
            SampleEffect("v0", periodMillis = 0L) {}
        }
    }
}
