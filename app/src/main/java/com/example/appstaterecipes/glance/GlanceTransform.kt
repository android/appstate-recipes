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

import androidx.appstate.transform.transform
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

const val DEFAULT_FOCUS_DURATION_MINUTES = 25
const val DEFAULT_FOCUS_DURATION_SECONDS = DEFAULT_FOCUS_DURATION_MINUTES * 60

data class FocusTask(
    val id: String,
    val title: String,
    val durationMinutes: Int = DEFAULT_FOCUS_DURATION_MINUTES,
)

enum class FocusPhase {
    Idle,
    Focusing,
    Paused,
}

data class FocusSession(
    val phase: FocusPhase = FocusPhase.Idle,
    val activeTaskId: String? = null,
    val remainingSeconds: Int = 0,
    val totalSeconds: Int = DEFAULT_FOCUS_DURATION_SECONDS,
)

sealed interface FocusSummary {
    data object Idle : FocusSummary

    data class Active(
        val taskId: String,
        val taskTitle: String,
        val isPaused: Boolean,
        val remainingSeconds: Int,
        val totalSeconds: Int,
    ) : FocusSummary {
        val progress: Float
            get() {
                val safeTotal = totalSeconds.coerceAtLeast(1)
                val elapsed = (safeTotal - remainingSeconds).coerceIn(0, safeTotal)
                return elapsed.toFloat() / safeTotal.toFloat()
            }
    }
}

enum class WidgetPrimaryAction {
    Start,
    Pause,
    Resume,
}

data class FocusWidgetUiState(
    val statusLabel: String = "Ready to focus",
    val taskTitle: String? = null,
    val formattedTime: String = formatDurationSeconds(DEFAULT_FOCUS_DURATION_SECONDS),
    val progress: Float = 0f,
    val primaryAction: WidgetPrimaryAction = WidgetPrimaryAction.Start,
    val isSessionActive: Boolean = false,
    val completedToday: Int = 0,
)

interface TaskRepository {
    val tasksFlow: Flow<List<FocusTask>>
    val completedTodayFlow: Flow<Int>
    fun recordCompletedSession()
}

// [START glance_shared_transform]
/**
 * Manages focus timer sessions and exposes a derived [FocusSummary] as Compose [State].
 *
 * When [FocusPhase.Idle], [summary] returns early before collecting [TaskRepository.tasksFlow],
 * so `tasksFlow` is only subscribed while a session is active. ViewModels and Glance widgets can
 * read [summary] directly or build nested [transform] blocks on top of it.
 */
class FocusSessionManager(
    private val taskRepository: TaskRepository,
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val _sessionFlow = MutableStateFlow(FocusSession())
    val sessionFlow: StateFlow<FocusSession> = _sessionFlow.asStateFlow()

    private var timerJob: Job? = null

    val summary: State<FocusSummary> = transform<FocusSummary>(
        initialValue = FocusSummary.Idle,
        scope = scope,
        dispatcher = dispatcher,
    ) { previous ->
        val session by sessionFlow.collectAsState()
        if (session.phase == FocusPhase.Idle) {
            return@transform FocusSummary.Idle
        }

        val tasks = taskRepository.tasksFlow.collectAsState(initial = null).value
            ?: return@transform previous
        val task = tasks.find { it.id == session.activeTaskId }

        FocusSummary.Active(
            taskId = session.activeTaskId.orEmpty(),
            taskTitle = task?.title ?: "Focus session",
            isPaused = session.phase == FocusPhase.Paused,
            remainingSeconds = session.remainingSeconds,
            totalSeconds = session.totalSeconds,
        )
    }
    // [END glance_shared_transform]

    @Synchronized
    fun startSession(task: FocusTask) {
        val durationSeconds = (task.durationMinutes * 60).coerceAtLeast(1)
        _sessionFlow.value = FocusSession(
            phase = FocusPhase.Focusing,
            activeTaskId = task.id,
            remainingSeconds = durationSeconds,
            totalSeconds = durationSeconds,
        )
        startCountdownLoop()
    }

    @Synchronized
    fun pauseSession() {
        val current = _sessionFlow.value
        if (current.phase != FocusPhase.Focusing) return
        timerJob?.cancel()
        timerJob = null
        _sessionFlow.value = current.copy(phase = FocusPhase.Paused)
    }

    @Synchronized
    fun resumeSession() {
        val current = _sessionFlow.value
        if (current.phase != FocusPhase.Paused) return
        _sessionFlow.value = current.copy(phase = FocusPhase.Focusing)
        startCountdownLoop()
    }

    @Synchronized
    fun stopSession() {
        timerJob?.cancel()
        timerJob = null
        _sessionFlow.value = FocusSession()
    }

    @Synchronized
    fun completeSession() {
        val current = _sessionFlow.value
        if (current.phase == FocusPhase.Idle) return
        timerJob?.cancel()
        timerJob = null
        _sessionFlow.value = FocusSession()
        taskRepository.recordCompletedSession()
    }

    @Synchronized
    fun togglePrimaryAction(defaultTask: FocusTask) {
        when (_sessionFlow.value.phase) {
            FocusPhase.Idle -> startSession(defaultTask)
            FocusPhase.Focusing -> pauseSession()
            FocusPhase.Paused -> resumeSession()
        }
    }

    private fun startCountdownLoop() {
        timerJob?.cancel()
        timerJob = scope.launch(dispatcher) {
            while (true) {
                delay(1_000L)
                var finished = false
                _sessionFlow.update { current ->
                    if (current.phase != FocusPhase.Focusing) {
                        current
                    } else if (current.remainingSeconds <= 1) {
                        finished = true
                        FocusSession()
                    } else {
                        current.copy(remainingSeconds = current.remainingSeconds - 1)
                    }
                }
                if (finished) {
                    taskRepository.recordCompletedSession()
                    break
                }
            }
        }
    }
}

// [START glance_widget_transform]
/**
 * Derives [FocusWidgetUiState] for [FocusWidget] by combining [FocusSessionManager.summary] with
 * [completedTodayFlow].
 *
 * Defining the widget's state in a [transform] runs the derivation on [dispatcher]
 * (`Dispatchers.Default` by default) for the lifetime of [scope] and makes it unit-testable on the
 * JVM with `transformTest`.
 */
fun focusWidgetTransform(
    summaryState: State<FocusSummary>,
    completedTodayFlow: Flow<Int>,
    scope: CoroutineScope,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
): State<FocusWidgetUiState> = transform(
    initialValue = FocusWidgetUiState(),
    scope = scope,
    dispatcher = dispatcher,
) {
    val summary by summaryState
    val completedToday by completedTodayFlow.collectAsState(initial = 0)

    when (val current = summary) {
        FocusSummary.Idle -> FocusWidgetUiState(
            statusLabel = "Ready to focus",
            taskTitle = null,
            formattedTime = formatDurationSeconds(DEFAULT_FOCUS_DURATION_SECONDS),
            progress = 0f,
            primaryAction = WidgetPrimaryAction.Start,
            isSessionActive = false,
            completedToday = completedToday,
        )

        is FocusSummary.Active -> FocusWidgetUiState(
            statusLabel = if (current.isPaused) "Focusing · paused" else "Focusing",
            taskTitle = current.taskTitle,
            formattedTime = formatDurationSeconds(current.remainingSeconds),
            progress = current.progress,
            primaryAction = if (current.isPaused) {
                WidgetPrimaryAction.Resume
            } else {
                WidgetPrimaryAction.Pause
            },
            isSessionActive = true,
            completedToday = completedToday,
        )
    }
}
// [END glance_widget_transform]

internal fun formatDurationSeconds(totalSeconds: Int): String {
    val clamped = totalSeconds.coerceAtLeast(0)
    val minutes = clamped / 60
    val seconds = clamped % 60
    return "${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
}
