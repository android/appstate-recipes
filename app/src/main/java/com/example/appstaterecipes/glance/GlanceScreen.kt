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

package com.example.appstaterecipes.glance

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.example.appstaterecipes.ui.excludeBottom
import com.example.appstaterecipes.ui.onlyBottom
import com.example.appstaterecipes.ui.plus
import com.example.appstaterecipes.ui.theme.AppStateRecipesTheme
import kotlinx.coroutines.launch

@Composable
fun GlanceScreen(viewModel: GlanceViewModel) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val uiState by viewModel.uiState
    var placedWidgetCount by remember { mutableIntStateOf(0) }

    LifecycleResumeEffect(Unit) {
        val job = coroutineScope.launch {
            placedWidgetCount = GlanceAppWidgetManager(context)
                .getGlanceIds(FocusWidget::class.java)
                .size
        }
        onPauseOrDispose { job.cancel() }
    }

    GlanceScreenContent(
        uiState = uiState,
        placedWidgetCount = placedWidgetCount,
        onPinWidgetClick = {
            coroutineScope.launch {
                GlanceAppWidgetManager(context).requestPinGlanceAppWidget(
                    receiver = FocusWidgetReceiver::class.java,
                    preview = FocusWidget(),
                )
            }
        },
        onStartTask = viewModel::startTask,
        onPrimaryWidgetAction = viewModel::togglePauseOrStart,
        onPauseSession = viewModel::pauseSession,
        onResumeSession = viewModel::resumeSession,
        onCompleteSession = viewModel::completeSession,
        onStopSession = viewModel::stopSession,
        onToggleMiniTimerExpanded = viewModel::toggleMiniTimerExpanded,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlanceScreenContent(
    uiState: GlanceScreenUiState,
    placedWidgetCount: Int,
    onPinWidgetClick: () -> Unit,
    onStartTask: (FocusTask) -> Unit,
    onPrimaryWidgetAction: () -> Unit,
    onPauseSession: () -> Unit,
    onResumeSession: () -> Unit,
    onCompleteSession: () -> Unit,
    onStopSession: () -> Unit,
    onToggleMiniTimerExpanded: () -> Unit,
) {
    val activeTaskId = (uiState.summary as? FocusSummary.Active)?.taskId

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Glance Widget") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        },
        bottomBar = {
            val activeSummary = uiState.summary as? FocusSummary.Active
            if (activeSummary != null) {
                MiniTimerBottomBar(
                    summary = activeSummary,
                    isExpanded = uiState.isMiniTimerExpanded,
                    showControls = uiState.showMiniTimerControls,
                    onToggleExpanded = onToggleMiniTimerExpanded,
                    onPause = onPauseSession,
                    onResume = onResumeSession,
                    onComplete = onCompleteSession,
                    onStop = onStopSession,
                )
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding.excludeBottom()),
            contentPadding = PaddingValues(16.dp) + innerPadding.onlyBottom(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                WidgetLauncherCard(
                    placedWidgetCount = placedWidgetCount,
                    onPinWidgetClick = onPinWidgetClick,
                )
            }

            item {
                Text(
                    text = "Derived FocusWidgetUiState (focusWidgetTransform)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            item {
                WidgetStatePreviewCard(
                    widgetState = uiState.widgetPreviewState,
                    onPrimaryAction = onPrimaryWidgetAction,
                    onStop = onStopSession,
                )
            }

            item {
                Text(
                    text = "Focus Tasks",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            items(uiState.tasks, key = { it.id }) { task ->
                val isCurrentTask = activeTaskId == task.id
                TaskRowCard(
                    task = task,
                    isActive = isCurrentTask,
                    onStartClick = { onStartTask(task) },
                    onCompleteClick = onCompleteSession,
                )
            }
        }
    }
}

@Composable
private fun WidgetLauncherCard(
    placedWidgetCount: Int,
    onPinWidgetClick: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val indicatorColor = if (placedWidgetCount > 0) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline
                }
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(indicatorColor, CircleShape),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (placedWidgetCount > 0) {
                        "Home-screen widget active ($placedWidgetCount placed)"
                    } else {
                        "Home-screen widget not placed yet"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                text = "Start, pause, or resume a focus session here or directly on the FocusFlow home-screen widget. Both surfaces observe the same shared FocusSessionManager.summary transform.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FilledTonalButton(onClick = onPinWidgetClick) {
                Text("Pin FocusFlow Widget")
            }
        }
    }
}

@Composable
private fun WidgetStatePreviewCard(
    widgetState: FocusWidgetUiState,
    onPrimaryAction: () -> Unit,
    onStop: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "FocusFlow",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "${widgetState.completedToday} done today",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }

            val subtitle = widgetState.taskTitle?.let { "${widgetState.statusLabel} • $it" }
                ?: widgetState.statusLabel
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = widgetState.formattedTime,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Spacer(modifier = Modifier.weight(1f))

                if (widgetState.isSessionActive) {
                    OutlinedButton(onClick = onStop) {
                        Text("Stop")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }

                val actionLabel = when (widgetState.primaryAction) {
                    WidgetPrimaryAction.Start -> "Start"
                    WidgetPrimaryAction.Pause -> "Pause"
                    WidgetPrimaryAction.Resume -> "Resume"
                }
                Button(onClick = onPrimaryAction) {
                    Text(actionLabel)
                }
            }

            LinearProgressIndicator(
                progress = { widgetState.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
            )
        }
    }
}

@Composable
private fun TaskRowCard(
    task: FocusTask,
    isActive: Boolean,
    onStartClick: () -> Unit,
    onCompleteClick: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = "${task.durationMinutes} min focus block",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            if (isActive) {
                FilledTonalButton(onClick = onCompleteClick) {
                    Text("Done")
                }
            } else {
                Button(onClick = onStartClick) {
                    Text("Start")
                }
            }
        }
    }
}

@Composable
private fun MiniTimerBottomBar(
    summary: FocusSummary.Active,
    isExpanded: Boolean,
    showControls: Boolean,
    onToggleExpanded: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onComplete: () -> Unit,
    onStop: () -> Unit,
) {
    Surface(
        tonalElevation = 4.dp,
        shadowElevation = 4.dp,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (summary.isPaused) {
                            "Focusing · paused — ${summary.taskTitle}"
                        } else {
                            "Focusing — ${summary.taskTitle}"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = formatDurationSeconds(summary.remainingSeconds),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                }
                TextButton(onClick = onToggleExpanded) {
                    Text(if (isExpanded) "Less" else "More")
                }
            }

            AnimatedVisibility(visible = showControls) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                ) {
                    if (summary.isPaused) {
                        Button(onClick = onResume) {
                            Text("Resume")
                        }
                    } else {
                        Button(onClick = onPause) {
                            Text("Pause")
                        }
                    }
                    FilledTonalButton(onClick = onComplete) {
                        Text("Complete")
                    }
                    OutlinedButton(onClick = onStop) {
                        Text("Stop")
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun GlanceScreenActivePreview() {
    val activeSummary = FocusSummary.Active(
        taskId = "write-ads-talk",
        taskTitle = "Write the ADS talk",
        isPaused = false,
        remainingSeconds = 24 * 60 + 55,
        totalSeconds = 25 * 60,
    )
    AppStateRecipesTheme {
        GlanceScreenContent(
            uiState = GlanceScreenUiState(
                tasks = sampleFocusTasks,
                summary = activeSummary,
                widgetPreviewState = FocusWidgetUiState(
                    statusLabel = "Focusing",
                    taskTitle = "Write the ADS talk",
                    formattedTime = "24:55",
                    progress = 0.01f,
                    primaryAction = WidgetPrimaryAction.Pause,
                    isSessionActive = true,
                    completedToday = 0,
                ),
                isMiniTimerExpanded = true,
                showMiniTimerControls = true,
            ),
            placedWidgetCount = 1,
            onPinWidgetClick = {},
            onStartTask = {},
            onPrimaryWidgetAction = {},
            onPauseSession = {},
            onResumeSession = {},
            onCompleteSession = {},
            onStopSession = {},
            onToggleMiniTimerExpanded = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun GlanceScreenIdlePreview() {
    AppStateRecipesTheme {
        GlanceScreenContent(
            uiState = GlanceScreenUiState(
                tasks = sampleFocusTasks,
                summary = FocusSummary.Idle,
                widgetPreviewState = FocusWidgetUiState(),
            ),
            placedWidgetCount = 0,
            onPinWidgetClick = {},
            onStartTask = {},
            onPrimaryWidgetAction = {},
            onPauseSession = {},
            onResumeSession = {},
            onCompleteSession = {},
            onStopSession = {},
            onToggleMiniTimerExpanded = {},
        )
    }
}
