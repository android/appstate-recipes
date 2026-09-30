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

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds

/**
 * Common contract for providing high-frequency [Acceleration] updates.
 */
interface AccelerationTracker {
    val isAvailable: Boolean
    fun start(onUpdate: (Acceleration) -> Unit)
    fun stop()
}

/**
 * Real device accelerometer tracker listening to Android hardware [SensorManager].
 */
class RealAccelerationTracker(context: Context) : AccelerationTracker, SensorEventListener {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val sensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private var onUpdateListener: ((Acceleration) -> Unit)? = null

    override val isAvailable: Boolean = sensor != null

    override fun start(onUpdate: (Acceleration) -> Unit) {
        stop()
        onUpdateListener = onUpdate
        sensor?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST)
        }
    }

    override fun stop() {
        sensorManager?.unregisterListener(this)
        onUpdateListener = null
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            onUpdateListener?.invoke(
                Acceleration(x = it.values[0], y = it.values[1], z = it.values[2]),
            )
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}

/**
 * Simulated accelerometer producing smooth random-walk motion with high-frequency jitter,
 * pulled gently toward center (0, 0) like an elastic spring.
 *
 * @param scope Coroutine scope for running the update loop.
 * @param targetHz Target update frequency in Hz (e.g. display refresh rate, 120Hz). Defaults to 60Hz.
 */
class SimulatedAccelerationTracker(
    private val scope: CoroutineScope,
    private val targetHz: Float = 60f,
) : AccelerationTracker {
    override val isAvailable: Boolean = true
    private var job: Job? = null

    override fun start(onUpdate: (Acceleration) -> Unit) {
        stop()
        job = scope.launch {
            var currentX = 0f
            var currentY = 0f
            var velocityX = 0f
            var velocityY = 0f

            val safeHz = targetHz.coerceAtLeast(1f)
            // Baseline was calibrated at 20Hz (50ms interval). Scale impulses and spring integration
            // by dt relative to baseline so motion speed and jitter amplitude stay natural regardless of refresh rate.
            val dtScale = (20f / safeHz).coerceIn(0.05f, 2f)
            val impulseScale = sqrt(dtScale)
            val stepDuration = (1000.0 / safeHz).roundToLong().coerceAtLeast(1L).milliseconds

            while (isActive) {
                // Spring force pulling back toward center (0, 0)
                val springForceX = -currentX * (0.08f * dtScale)
                val springForceY = -currentY * (0.08f * dtScale)

                // Random acceleration impulses (simulating hand wobble / motion wander).
                // Scaled by sqrt(dtScale) to maintain consistent random-walk diffusion across sample rates.
                val impulseX = (Random.nextFloat() - 0.5f) * (1.2f * impulseScale)
                val impulseY = (Random.nextFloat() - 0.5f) * (1.2f * impulseScale)

                // Update velocities with damping (scaled by dt)
                val damping = 0.88.pow(dtScale.toDouble()).toFloat()
                velocityX = (velocityX + springForceX + impulseX) * damping
                velocityY = (velocityY + springForceY + impulseY) * damping

                // Integrate position and clamp to realistic tilt boundaries (-6 to +6 m/s^2)
                currentX = (currentX + velocityX * dtScale).coerceIn(-6f, 6f)
                currentY = (currentY + velocityY * dtScale).coerceIn(-6f, 6f)

                // High-frequency sensor noise / jitter on top of continuous motion.
                // Left unscaled so instantaneous sensor noise amplitude matches physical sensor noise.
                val jitterX = (Random.nextFloat() - 0.5f) * 0.4f
                val jitterY = (Random.nextFloat() - 0.5f) * 0.4f
                val jitterZ = (Random.nextFloat() - 0.5f) * 0.2f

                onUpdate(
                    Acceleration(
                        x = currentX + jitterX,
                        y = currentY + jitterY,
                        z = 9.8f + jitterZ,
                    ),
                )
                delay(stepDuration)
            }
        }
    }

    override fun stop() {
        job?.cancel()
        job = null
    }
}
