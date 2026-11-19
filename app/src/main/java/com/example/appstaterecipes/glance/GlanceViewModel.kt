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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

data class GlanceScreenUiState(
    val tasks: List<FocusTask> = emptyList(),
    val summary: FocusSummary = FocusSummary.Idle,
    val widgetPreviewState: FocusWidgetUiState = FocusWidgetUiState(),
    val isMiniTimerExpanded: Boolean = false,
    val showMiniTimerControls: Boolean = false,
)

class GlanceViewModel(
    private val focusManager: FocusSessionManager,
    private val taskRepository: TaskRepository,
    coroutineScope: CoroutineScope? = null,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private val effectiveScope: CoroutineScope = coroutineScope ?: viewModelScope

    var isMiniTimerExpanded by mutableStateOf(false)
        private set

    // [START glance_mini_timer_transform]
    private val widgetPreviewState: State<FocusWidgetUiState> = focusWidgetTransform(
        summaryState = focusManager.summary,
        completedTodayFlow = taskRepository.completedTodayFlow,
        scope = effectiveScope,
        dispatcher = dispatcher,
    )

    val uiState: State<GlanceScreenUiState> = transform(
        initialValue = GlanceScreenUiState(),
        scope = effectiveScope,
        dispatcher = dispatcher,
    ) {
        val summary by focusManager.summary
        val widgetPreview by widgetPreviewState
        val tasks by taskRepository.tasksFlow.collectAsState(initial = sampleFocusTasks)
        val expanded = isMiniTimerExpanded

        GlanceScreenUiState(
            tasks = tasks,
            summary = summary,
            widgetPreviewState = widgetPreview,
            isMiniTimerExpanded = expanded,
            showMiniTimerControls = expanded && summary is FocusSummary.Active,
        )
    }
    // [END glance_mini_timer_transform]

    fun startTask(task: FocusTask) {
        focusManager.startSession(task)
    }

    fun togglePauseOrStart() {
        val defaultTask = uiState.value.tasks.firstOrNull() ?: sampleFocusTasks.first()
        focusManager.togglePrimaryAction(defaultTask)
    }

    fun pauseSession() {
        focusManager.pauseSession()
    }

    fun resumeSession() {
        focusManager.resumeSession()
    }

    fun completeSession() {
        focusManager.completeSession()
        isMiniTimerExpanded = false
    }

    fun stopSession() {
        focusManager.stopSession()
        isMiniTimerExpanded = false
    }

    fun toggleMiniTimerExpanded() {
        isMiniTimerExpanded = !isMiniTimerExpanded
    }

    companion object {
        fun factory(
            focusManager: FocusSessionManager,
            taskRepository: TaskRepository,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                GlanceViewModel(
                    focusManager = focusManager,
                    taskRepository = taskRepository,
                )
            }
        }
    }
}
