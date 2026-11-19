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

import android.content.Context
import androidx.appstate.transform.listener
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal val sampleFocusTasks = listOf(
    FocusTask(
        id = "write-ads-talk",
        title = "Write the ADS talk",
        durationMinutes = 25,
    ),
    FocusTask(
        id = "reply-design-doc",
        title = "Reply to design doc comments",
        durationMinutes = 15,
    ),
    FocusTask(
        id = "plan-q1-roadmap",
        title = "Plan Q1 roadmap",
        durationMinutes = 25,
    ),
)

class InMemoryTaskRepository(
    initialTasks: List<FocusTask> = sampleFocusTasks,
    initialCompletedToday: Int = 0,
) : TaskRepository {
    private val _tasksFlow = MutableStateFlow(initialTasks)
    override val tasksFlow: StateFlow<List<FocusTask>> = _tasksFlow.asStateFlow()

    private val _completedTodayFlow = MutableStateFlow(initialCompletedToday)
    override val completedTodayFlow: StateFlow<Int> = _completedTodayFlow.asStateFlow()

    override fun recordCompletedSession() {
        _completedTodayFlow.update { it + 1 }
    }
}

/**
 * Process-scoped holder shared between [GlanceActivity] and [FocusWidget].
 *
 * Uses [listener] from `androidx.appstate.transform` to wake any placed [FocusWidget] instances
 * via [FocusWidget.updateAll] when state changes outside an active Glance worker session.
 * Note that `updateAll` does not restart `provideGlance` if a Glance session is already running.
 */
internal class FocusContainer(appContext: Context) {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val taskRepository = InMemoryTaskRepository()
    val focusManager = FocusSessionManager(
        taskRepository = taskRepository,
        scope = appScope,
    )

    val defaultTask: FocusTask
        get() = taskRepository.tasksFlow.value.first()

    init {
        appScope.launch {
            listener {
                val summary by focusManager.summary
                val completedToday by taskRepository.completedTodayFlow.collectAsState()

                LaunchedEffect(summary, completedToday) {
                    val glanceIds = GlanceAppWidgetManager(appContext)
                        .getGlanceIds(FocusWidget::class.java)
                    if (glanceIds.isNotEmpty()) {
                        FocusWidget().updateAll(appContext)
                    }
                }
            }
        }
    }

    companion object {
        @Volatile
        private var instance: FocusContainer? = null

        fun get(context: Context): FocusContainer {
            return instance ?: synchronized(this) {
                instance ?: FocusContainer(context.applicationContext).also { instance = it }
            }
        }
    }
}
