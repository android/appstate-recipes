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

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.appstaterecipes.runTransformTest
import kotlinx.coroutines.delay
import org.junit.Assert.assertEquals
import org.junit.Test

class SampleTest {

    @Test
    fun sampleEffect_periodicallySamplesLatestValue() = runTransformTest {
        var input by mutableStateOf("v0")
        val emissions = mutableListOf<String>()

        testTransform(initialValue = Unit) {
            SampleEffect(input, periodMillis = 500L) { sampled ->
                emissions.add(sampled)
            }
        }

        // 200ms: value changes to v1
        advanceTimeBy(200)
        input = "v1"
        sync()

        // 400ms: value changes to v2
        advanceTimeBy(200)
        input = "v2"
        sync()

        // At 500ms, period completes and should capture latest value ("v2")
        advanceTimeBy(100)
        assertEquals(listOf("v2"), emissions)
    }

    @Test
    fun sampleEffect_doesNotEmitWhenNoPendingUpdate() = runTransformTest {
        var input by mutableStateOf("v0")
        val emissions = mutableListOf<String>()

        testTransform(initialValue = Unit) {
            SampleEffect(input, periodMillis = 500L) { sampled ->
                emissions.add(sampled)
            }
        }

        // First period emits initial value
        advanceTimeBy(500)
        assertEquals(listOf("v0"), emissions)

        // Second period without changes: should not emit again
        advanceTimeBy(500)
        assertEquals(listOf("v0"), emissions)

        // Change occurs: next tick should emit
        input = "v1"
        sync()

        advanceTimeBy(500)
        assertEquals(listOf("v0", "v1"), emissions)
    }

    @Test
    fun rememberSampled_projectsSampledState() = runTransformTest {
        var input by mutableStateOf(Acceleration(1f, 1f, 1f))

        val sampledState = testTransform(initialValue = Acceleration(1f, 1f, 1f)) {
            val sampled by rememberSampled(input, periodMillis = 500L)
            sampled
        }

        assertEquals(Acceleration(1f, 1f, 1f), sampledState.value)

        // Multiple rapid changes
        input = Acceleration(2f, 2f, 2f)
        sync()
        advanceTimeBy(200)

        input = Acceleration(3f, 3f, 3f)
        sync()

        // Sampled state shouldn't have changed yet
        assertEquals(Acceleration(1f, 1f, 1f), sampledState.value)

        // Ticks after 500ms
        advanceTimeBy(300)
        assertEquals(Acceleration(3f, 3f, 3f), sampledState.value)
    }

    @Test
    fun rememberSampled_structuralEqualityPreventsRedundantUpdates() = runTransformTest {
        var input by mutableStateOf("A")

        val sampledState = testTransform(initialValue = "A") {
            val sampled by rememberSampled(input, periodMillis = 300L)
            sampled
        }

        var readerInvocations = 0
        testTransform(initialValue = 0) {
            val current = sampledState.value
            readerInvocations++
            current.length
        }

        advanceTimeBy(300)
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

        // Set state to "B" and back to "A" within the sample period:
        // When tick arrives with "A" (equals previous value),
        // mutableStateOf(value) structural equality policy does not trigger observer recomposition
        input = "B"
        sync()
        advanceTimeBy(100)
        input = "A"
        sync()
        advanceTimeBy(200)
        assertEquals(3, readerInvocations)
    }

    @Test
    fun sampleViewModel_fineGrainedInvalidationOfDelegatedSampleUiState() = runTransformTest {
        var rawInput by mutableStateOf(Acceleration(0f, 0f, 0f))

        val uiState = testTransform<SampleUiState>(initialValue = ImmutableSampleUiState()) {
            val sampled1000ms by rememberSampled(rawInput, periodMillis = 1000L)

            val delegatedState = remember { DelegatedSampleUiState() }
            delegatedState.raw = rawInput
            delegatedState.sampled1000ms = sampled1000ms
            delegatedState
        }

        var sampled1000msReaderRecompositions = 0

        // Sub-composition / observer that only reads sampled1000ms
        testTransform(initialValue = Acceleration()) {
            val state = uiState.value
            val sampled = state.sampled1000ms
            sampled1000msReaderRecompositions++
            sampled
        }

        assertEquals(1, sampled1000msReaderRecompositions)

        // Rapid churn of raw updates (10 * 50ms = 500ms elapsed)
        for (i in 1..10) {
            rawInput = Acceleration(i.toFloat(), 0f, 0f)
            sync()
            advanceTimeBy(50)
        }

        // Observers of sampled1000ms should NOT have recomposed despite raw churning
        assertEquals(1, sampled1000msReaderRecompositions)

        // Reach the exact 1000ms sample window (+500ms)
        advanceTimeBy(500)
        // Now sampled1000ms updates to the latest raw value, triggering observer
        assertEquals(2, sampled1000msReaderRecompositions)
    }

    @Test
    fun sampleEffect_cancelsSuspendingCallbackMidFlightOnScopeDisposal() = runTransformTest {
        var active by mutableStateOf(true)
        var startedCallback = false
        var completedCallback = false

        testTransform(initialValue = Unit) {
            if (active) {
                SampleEffect(value = "v", periodMillis = 300L) {
                    startedCallback = true
                    delay(200L)
                    completedCallback = true
                }
            }
        }

        advanceTimeBy(300)
        assertEquals(true, startedCallback)
        assertEquals(false, completedCallback)

        // Dispose effect while callback is suspended in delay
        active = false
        sync()

        advanceTimeBy(300)
        assertEquals(false, completedCallback)
    }

    @Test
    fun sampleEffect_rememberUpdatedStateSwapsLambdaWithoutResettingTimer() = runTransformTest {
        var input by mutableStateOf("v0")
        var callbackInvocation = 0
        var callbackToRun: (String) -> Unit by mutableStateOf({ callbackInvocation = 1 })

        testTransform(initialValue = Unit) {
            val currentCallback = callbackToRun
            SampleEffect(input, periodMillis = 300L) {
                currentCallback(it)
            }
        }

        // At 200ms, change callback and value
        advanceTimeBy(200)
        callbackToRun = { callbackInvocation = 2 }
        input = "v1"
        sync()

        // Timer period is 300ms, not reset by callback swap or value change; fires at total 300ms (+100ms)
        advanceTimeBy(100)
        assertEquals(2, callbackInvocation)
    }

    @Test(expected = IllegalArgumentException::class)
    fun sampleEffect_throwsOnNonPositivePeriod() = runTransformTest {
        testTransform(initialValue = Unit) {
            SampleEffect("v0", periodMillis = 0L) {}
        }
    }
}
