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

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.example.appstaterecipes.ui.excludeBottom
import com.example.appstaterecipes.ui.onlyBottom
import com.example.appstaterecipes.ui.plus
import com.example.appstaterecipes.ui.theme.AppStateRecipesTheme
import kotlin.math.round

@Composable
fun SampleScreen(viewModel: SampleViewModel) {
    val uiState by viewModel.uiState

    LifecycleResumeEffect(viewModel) {
        viewModel.startListening()
        onPauseOrDispose {
            viewModel.stopListening()
        }
    }

    SampleScreenContent(
        uiState = uiState,
        onSourceSelected = { viewModel.setSource(it) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SampleScreenContent(
    uiState: SampleUiState,
    onSourceSelected: (SensorSource) -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Sample") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding.excludeBottom())
                .verticalScroll(rememberScrollState())
                .padding(PaddingValues(16.dp) + innerPadding.onlyBottom()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "High-frequency sensor readings produce rapid jitter. Notice how different rememberSampled rates smooth the stream into distinct cadences.",
                style = MaterialTheme.typography.bodyMedium,
            )

            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth(),
            ) {
                SegmentedButton(
                    selected = uiState.activeSource == SensorSource.HARDWARE,
                    onClick = { onSourceSelected(SensorSource.HARDWARE) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    enabled = uiState.isHardwareSensorAvailable,
                ) {
                    Text(
                        if (uiState.isHardwareSensorAvailable) "Hardware Sensor" else "Hardware (None)",
                    )
                }
                SegmentedButton(
                    selected = uiState.activeSource == SensorSource.SIMULATED,
                    onClick = { onSourceSelected(SensorSource.SIMULATED) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                ) {
                    Text("Simulated Motion")
                }
            }

            MultiSampleTiltCard(
                uiState = uiState,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private data class SamplePoint(
    val label: String,
    val cadence: String,
    val acceleration: () -> Acceleration,
    val color: Color,
)

@Composable
private fun MultiSampleTiltCard(
    uiState: SampleUiState,
    modifier: Modifier = Modifier,
) {
    val rawCadence = if (uiState.rawHz > 0) "${uiState.rawHz}Hz" else "Measuring…"
    val points = listOf(
        SamplePoint(
            label = "Raw",
            cadence = rawCadence,
            acceleration = { uiState.raw },
            color = Color(0xFFEF4444), // Error Red
        ),
        SamplePoint(
            label = "Sampled",
            cadence = "250ms",
            acceleration = { uiState.sampled250ms },
            color = Color(0xFFF59E0B), // Amber
        ),
        SamplePoint(
            label = "Sampled",
            cadence = "500ms",
            acceleration = { uiState.sampled500ms },
            color = Color(0xFF10B981), // Emerald
        ),
        SamplePoint(
            label = "Sampled",
            cadence = "1000ms",
            acceleration = { uiState.sampled1000ms },
            color = Color(0xFF3B82F6), // Blue
        ),
    )

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Tilt & Sampling Comparison",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .background(
                        MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(12.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                val surfaceColor = MaterialTheme.colorScheme.surface
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val centerX = size.width / 2f
                    val centerY = size.height / 2f

                    // Grid crosshairs
                    drawLine(
                        color = Color.LightGray.copy(alpha = 0.4f),
                        start = Offset(0f, centerY),
                        end = Offset(size.width, centerY),
                        strokeWidth = 1.dp.toPx(),
                    )
                    drawLine(
                        color = Color.LightGray.copy(alpha = 0.4f),
                        start = Offset(centerX, 0f),
                        end = Offset(centerX, size.height),
                        strokeWidth = 1.dp.toPx(),
                    )

                    // Target concentric circles
                    val maxRadius = (minOf(size.width, size.height) / 2f) - 16.dp.toPx()
                    drawCircle(
                        color = Color.LightGray.copy(alpha = 0.25f),
                        radius = maxRadius * 0.5f,
                        center = Offset(centerX, centerY),
                        style = Stroke(width = 1.dp.toPx()),
                    )
                    drawCircle(
                        color = Color.LightGray.copy(alpha = 0.25f),
                        radius = maxRadius,
                        center = Offset(centerX, centerY),
                        style = Stroke(width = 1.dp.toPx()),
                    )

                    val maxRange = 9.8f
                    val maxOffsetX = (size.width / 2f) - 16.dp.toPx()
                    val maxOffsetY = (size.height / 2f) - 16.dp.toPx()
                    val dotRadius = 8.dp.toPx()
                    val outlineWidth = 1.5.dp.toPx()

                    // [START sample_draw_phase_optimization]
                    // Reading acceleration inside Draw phase: skips composition and layout entirely
                    points.asReversed().forEach { (_, _, accelerationProvider, color) ->
                        val acceleration = accelerationProvider()
                        val offsetX = (-acceleration.x / maxRange).coerceIn(-1f, 1f) * maxOffsetX
                        val offsetY = (acceleration.y / maxRange).coerceIn(-1f, 1f) * maxOffsetY
                        val center = Offset(centerX + offsetX, centerY + offsetY)

                        // Outline ring so overlapping dots stay cleanly distinct
                        drawCircle(
                            color = surfaceColor,
                            radius = dotRadius + outlineWidth,
                            center = center,
                        )
                        drawCircle(
                            color = color,
                            radius = dotRadius,
                            center = center,
                        )
                    }
                    // [END sample_draw_phase_optimization]
                }
            }

            // Legend / metrics in a 2x2 grid
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                points.chunked(2).forEach { rowPoints ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        rowPoints.forEach { point ->
                            LegendItem(
                                point = point,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendItem(
    point: SamplePoint,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .background(
                MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                shape = RoundedCornerShape(8.dp),
            )
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(point.color, shape = RoundedCornerShape(2.dp)),
        )
        Column {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = point.label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = point.cadence,
                    style = MaterialTheme.typography.labelMedium,
                    color = point.color,
                    fontWeight = FontWeight.Bold,
                )
            }
            CoordinateText(
                accelerationProvider = point.acceleration,
            )
        }
    }
}

@Composable
private fun CoordinateText(
    accelerationProvider: () -> Acceleration,
    modifier: Modifier = Modifier,
) {
    val acceleration = accelerationProvider()
    val roundedX = round(acceleration.x * 10f) / 10f
    val roundedY = round(acceleration.y * 10f) / 10f
    Text(
        text = "x: %.1f  y: %.1f".format(LocalLocale.current.platformLocale, roundedX, roundedY),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

@Preview(showBackground = true)
@Composable
private fun SampleScreenPreview() {
    val sampleState = SampleUiState(
        raw = Acceleration(x = 3.4f, y = -6.1f, z = 7.2f),
        sampled250ms = Acceleration(x = 2.8f, y = -5.4f, z = 7.8f),
        sampled500ms = Acceleration(x = 1.9f, y = -4.2f, z = 8.8f),
        sampled1000ms = Acceleration(x = 0.8f, y = -2.5f, z = 9.5f),
        isHardwareSensorAvailable = true,
        activeSource = SensorSource.HARDWARE,
    )
    AppStateRecipesTheme {
        SampleScreenContent(
            uiState = sampleState,
            onSourceSelected = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SampleScreenSimulatedPreview() {
    val sampleState = SampleUiState(
        raw = Acceleration(x = -2.2f, y = 4.4f, z = 9.1f),
        sampled250ms = Acceleration(x = -1.8f, y = 3.6f, z = 9.3f),
        sampled500ms = Acceleration(x = -1.0f, y = 2.2f, z = 9.6f),
        sampled1000ms = Acceleration(x = 0.0f, y = 0.0f, z = 9.8f),
        isHardwareSensorAvailable = false,
        activeSource = SensorSource.SIMULATED,
    )
    AppStateRecipesTheme {
        SampleScreenContent(
            uiState = sampleState,
            onSourceSelected = {},
        )
    }
}
