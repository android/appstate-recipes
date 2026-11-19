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

import androidx.appstate.transform.testing.TransformTestScope
import androidx.appstate.transform.testing.transformTest
import androidx.appstate.transform.transform
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GlanceTransformTest {

    @Test
    fun focusSessionManager_idleDoesNotSubscribeToTasksFlow_dynamicReadTracking() = transformTest {
        val activeTaskFlowCollectors = AtomicInteger(0)
        val backingTasks = MutableStateFlow(sampleFocusTasks)
        val countingTasksRepository = object : TaskRepository {
            override val tasksFlow: Flow<List<FocusTask>> = backingTasks
                .onStart { activeTaskFlowCollectors.incrementAndGet() }
                .onCompletion { activeTaskFlowCollectors.decrementAndGet() }
            override val completedTodayFlow = MutableStateFlow(0)
            override fun recordCompletedSession() {}
        }

        val manager = FocusSessionManager(
            taskRepository = countingTasksRepository,
            scope = backgroundScope,
            dispatcher = testDispatcher,
        )
        advance()

        // While Idle, early return skips tasksFlow.collectAsState() completely
        assertEquals(FocusSummary.Idle, manager.summary.value)
        assertEquals(0, activeTaskFlowCollectors.get())

        // Starting a session enters the active branch and subscribes to tasksFlow
        manager.startSession(sampleFocusTasks.first())
        settle()

        assertTrue(manager.summary.value is FocusSummary.Active)
        assertEquals(1, activeTaskFlowCollectors.get())

        // Stopping returns to Idle, disposing the tasksFlow collector automatically
        manager.stopSession()
        settle()

        assertEquals(FocusSummary.Idle, manager.summary.value)
        assertEquals(0, activeTaskFlowCollectors.get())
    }

    @Test
    fun focusSessionManager_combinesSessionAndTaskTitle_andCompletesCountdown() = transformTest {
        val repository = InMemoryTaskRepository(
            initialTasks = listOf(
                FocusTask(id = "task-1", title = "Write the ADS talk", durationMinutes = 1),
            ),
        )
        val manager = FocusSessionManager(
            taskRepository = repository,
            scope = backgroundScope,
            dispatcher = testDispatcher,
        )
        advance()

        manager.startSession(FocusTask(id = "task-1", title = "Write the ADS talk", durationMinutes = 1))
        settle()

        val initialSummary = manager.summary.value as FocusSummary.Active
        assertEquals("Write the ADS talk", initialSummary.taskTitle)
        assertEquals(60, initialSummary.remainingSeconds)
        assertFalse(initialSummary.isPaused)

        // Advance 5 seconds: 55 seconds remaining (00:55)
        advanceTimeBy(5.seconds)
        settle()

        val afterFiveSeconds = manager.summary.value as FocusSummary.Active
        assertEquals(55, afterFiveSeconds.remainingSeconds)

        // Advance remaining 55 seconds: session completes and increments completedToday
        advanceTimeBy(55.seconds)
        settle()

        assertEquals(FocusSummary.Idle, manager.summary.value)
        assertEquals(1, repository.completedTodayFlow.value)
    }

    @Test
    fun focusSessionManager_pauseAndResumePreservesRemainingTime() = transformTest {
        val repository = InMemoryTaskRepository()
        val manager = FocusSessionManager(
            taskRepository = repository,
            scope = backgroundScope,
            dispatcher = testDispatcher,
        )
        advance()

        manager.startSession(sampleFocusTasks.first())
        settle()

        advanceTimeBy(5.seconds)
        settle()
        assertEquals(24 * 60 + 55, (manager.summary.value as FocusSummary.Active).remainingSeconds)

        manager.pauseSession()
        settle()

        val paused = manager.summary.value as FocusSummary.Active
        assertTrue(paused.isPaused)
        assertEquals(24 * 60 + 55, paused.remainingSeconds)

        // While paused, advancing virtual time does not decrement remainingSeconds
        advanceTimeBy(30.seconds)
        settle()
        assertEquals(24 * 60 + 55, (manager.summary.value as FocusSummary.Active).remainingSeconds)

        // Resuming continues countdown from 24:55
        manager.resumeSession()
        settle()
        advanceTimeBy(5.seconds)
        settle()

        val resumed = manager.summary.value as FocusSummary.Active
        assertFalse(resumed.isPaused)
        assertEquals(24 * 60 + 50, resumed.remainingSeconds)
    }

    @Test
    fun focusWidgetTransform_projectsIdleActivePausedAndCompletedWidgetUiState() = transformTest {
        val repository = InMemoryTaskRepository()
        val manager = FocusSessionManager(
            taskRepository = repository,
            scope = backgroundScope,
            dispatcher = testDispatcher,
        )
        val widgetState = focusWidgetTransform(
            summaryState = manager.summary,
            completedTodayFlow = repository.completedTodayFlow,
            scope = backgroundScope,
            dispatcher = testDispatcher,
        )
        settle()

        assertEquals(
            FocusWidgetUiState(
                statusLabel = "Ready to focus",
                taskTitle = null,
                formattedTime = "25:00",
                progress = 0f,
                primaryAction = WidgetPrimaryAction.Start,
                isSessionActive = false,
                completedToday = 0,
            ),
            widgetState.value,
        )

        manager.startSession(sampleFocusTasks.first())
        settle()
        advanceTimeBy(5.seconds)
        settle()

        assertEquals("Focusing", widgetState.value.statusLabel)
        assertEquals("Write the ADS talk", widgetState.value.taskTitle)
        assertEquals("24:55", widgetState.value.formattedTime)
        assertEquals(WidgetPrimaryAction.Pause, widgetState.value.primaryAction)
        assertTrue(widgetState.value.isSessionActive)

        manager.togglePrimaryAction(sampleFocusTasks.first())
        settle()

        assertEquals("Focusing · paused", widgetState.value.statusLabel)
        assertEquals("24:55", widgetState.value.formattedTime)
        assertEquals(WidgetPrimaryAction.Resume, widgetState.value.primaryAction)

        manager.completeSession()
        settle()

        assertEquals("Ready to focus", widgetState.value.statusLabel)
        assertNull(widgetState.value.taskTitle)
        assertEquals(WidgetPrimaryAction.Start, widgetState.value.primaryAction)
        assertFalse(widgetState.value.isSessionActive)
        assertEquals(1, widgetState.value.completedToday)
    }

    @Test
    fun focusWidgetTransform_structuralEqualitySkipsRedundantWidgetRecompositions() = transformTest {
        val mutableTasks = MutableStateFlow(sampleFocusTasks)
        val repository = object : TaskRepository {
            override val tasksFlow = mutableTasks
            override val completedTodayFlow = MutableStateFlow(0)
            override fun recordCompletedSession() {}
        }
        val manager = FocusSessionManager(
            taskRepository = repository,
            scope = backgroundScope,
            dispatcher = testDispatcher,
        )
        val widgetState = focusWidgetTransform(
            summaryState = manager.summary,
            completedTodayFlow = repository.completedTodayFlow,
            scope = backgroundScope,
            dispatcher = testDispatcher,
        )
        settle()

        manager.startSession(sampleFocusTasks.first())
        settle()

        var widgetContentRecompositions = 0
        transform(
            initialValue = Unit,
            scope = backgroundScope,
            dispatcher = testDispatcher,
        ) {
            widgetState.value
            widgetContentRecompositions++
        }
        settle()
        assertEquals(1, widgetContentRecompositions)

        // Modify an unrelated inactive task in tasksFlow:
        // Active task ("write-ads-talk") title is unchanged, so FocusSummary.Active and
        // FocusWidgetUiState equal their previous values and Glance UI does not recompose.
        mutableTasks.value = sampleFocusTasks.map { task ->
            if (task.id == "plan-q1-roadmap") {
                task.copy(title = "Plan Q2 roadmap")
            } else {
                task
            }
        }
        settle()
        assertEquals(1, widgetContentRecompositions)

        // Renaming the active task DOES update FocusWidgetUiState and trigger recomposition
        mutableTasks.value = sampleFocusTasks.map { task ->
            if (task.id == "write-ads-talk") {
                task.copy(title = "Rehearse the ADS talk")
            } else {
                task
            }
        }
        settle()
        assertEquals(2, widgetContentRecompositions)
        assertEquals("Rehearse the ADS talk", widgetState.value.taskTitle)
    }

    @Test
    fun glanceViewModel_nestedTransformCombinesSummaryAndMiniTimerExpansion() = transformTest {
        val repository = InMemoryTaskRepository()
        val manager = FocusSessionManager(
            taskRepository = repository,
            scope = backgroundScope,
            dispatcher = testDispatcher,
        )
        val viewModel = GlanceViewModel(
            focusManager = manager,
            taskRepository = repository,
            coroutineScope = backgroundScope,
            dispatcher = testDispatcher,
        )
        settle()

        // Expanding while Idle keeps showMiniTimerControls = false
        viewModel.toggleMiniTimerExpanded()
        settle()
        assertTrue(viewModel.uiState.value.isMiniTimerExpanded)
        assertFalse(viewModel.uiState.value.showMiniTimerControls)

        // Starting a session turns showMiniTimerControls = true
        viewModel.startTask(sampleFocusTasks.first())
        settle()
        assertTrue(viewModel.uiState.value.showMiniTimerControls)

        // Collapsing turns showMiniTimerControls = false while session stays Active
        viewModel.toggleMiniTimerExpanded()
        settle()
        assertFalse(viewModel.uiState.value.showMiniTimerControls)
        assertTrue(viewModel.uiState.value.summary is FocusSummary.Active)
    }

    /**
     * Pumps nested/multi-stage Flow -> Snapshot -> Transform recompositions to quiescence.
     */
    private fun TransformTestScope.settle() {
        repeat(3) { advance() }
    }
}
