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

import android.os.SystemClock
import androidx.appstate.transform.transform
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlin.math.round

data class Acceleration(val x: Float = 0f, val y: Float = 0f, val z: Float = 0f)

enum class SensorSource {
    HARDWARE,
    SIMULATED,
}

// [START sample_stable_state]
@Stable
class SampleUiState(
    raw: Acceleration = Acceleration(),
    rawHz: Int = 0,
    sampled250ms: Acceleration = Acceleration(),
    sampled500ms: Acceleration = Acceleration(),
    sampled1000ms: Acceleration = Acceleration(),
    isHardwareSensorAvailable: Boolean = true,
    activeSource: SensorSource = SensorSource.HARDWARE,
) {
    var raw by mutableStateOf(raw)
        internal set
    var rawHz by mutableIntStateOf(rawHz)
        internal set
    var sampled250ms by mutableStateOf(sampled250ms)
        internal set
    var sampled500ms by mutableStateOf(sampled500ms)
        internal set
    var sampled1000ms by mutableStateOf(sampled1000ms)
        internal set
    var isHardwareSensorAvailable by mutableStateOf(isHardwareSensorAvailable)
        internal set
    var activeSource by mutableStateOf(activeSource)
        internal set
}
// [END sample_stable_state]

class SampleViewModel(
    private val realTracker: AccelerationTracker,
    simulatedTracker: AccelerationTracker? = null,
    targetSimulatedHz: Float = 60f,
) : ViewModel() {
    private val simulatedTracker: AccelerationTracker =
        simulatedTracker ?: SimulatedAccelerationTracker(viewModelScope, targetHz = targetSimulatedHz)

    private var rawAcceleration by mutableStateOf(Acceleration())
    private var rawHz by mutableIntStateOf(0)

    private var activeSource by mutableStateOf(
        if (realTracker.isAvailable) SensorSource.HARDWARE else SensorSource.SIMULATED,
    )

    private var isListening = false
    private var eventCount = 0
    private var windowStartUptimeMillis = 0L

    private val currentTracker: AccelerationTracker
        get() = when (activeSource) {
            SensorSource.HARDWARE -> realTracker
            SensorSource.SIMULATED -> simulatedTracker
        }

    private fun handleAccelerationUpdate(acceleration: Acceleration) {
        rawAcceleration = acceleration
        val now = SystemClock.uptimeMillis()
        if (windowStartUptimeMillis == 0L) {
            windowStartUptimeMillis = now
            eventCount = 0
        } else {
            eventCount++
            val elapsed = now - windowStartUptimeMillis
            if (elapsed >= 1000L) {
                rawHz = round((eventCount * 1000f) / elapsed).toInt()
                eventCount = 0
                windowStartUptimeMillis = now
            }
        }
    }

    fun startListening() {
        isListening = true
        eventCount = 0
        windowStartUptimeMillis = 0L
        currentTracker.start { acceleration ->
            handleAccelerationUpdate(acceleration)
        }
    }

    fun stopListening() {
        isListening = false
        realTracker.stop()
        simulatedTracker.stop()
        eventCount = 0
        windowStartUptimeMillis = 0L
    }

    fun setSource(source: SensorSource) {
        if (activeSource == source) return
        val wasListening = isListening
        if (wasListening) {
            currentTracker.stop()
        }
        activeSource = source
        eventCount = 0
        windowStartUptimeMillis = 0L
        rawHz = 0
        if (wasListening) {
            currentTracker.start { acceleration ->
                handleAccelerationUpdate(acceleration)
            }
        }
    }

    override fun onCleared() {
        stopListening()
    }

    // [START sample_transform_usage]
    val uiState: State<SampleUiState> = transform(
        initialValue = SampleUiState(),
        scope = viewModelScope,
    ) { state ->
        val sampled250ms by rememberSampled(rawAcceleration, periodMillis = 250L)
        val sampled500ms by rememberSampled(rawAcceleration, periodMillis = 500L)
        val sampled1000ms by rememberSampled(rawAcceleration, periodMillis = 1000L)

        state.raw = rawAcceleration
        state.rawHz = rawHz
        state.sampled250ms = sampled250ms
        state.sampled500ms = sampled500ms
        state.sampled1000ms = sampled1000ms
        state.isHardwareSensorAvailable = realTracker.isAvailable
        state.activeSource = activeSource

        state
    }
    // [END sample_transform_usage]

    companion object {
        fun factory(
            realTracker: AccelerationTracker,
            targetSimulatedHz: Float = 60f,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SampleViewModel(realTracker, targetSimulatedHz = targetSimulatedHz)
            }
        }
    }
}
