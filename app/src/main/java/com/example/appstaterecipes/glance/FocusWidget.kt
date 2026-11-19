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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.components.FilledButton
import androidx.glance.appwidget.components.OutlineButton
import androidx.glance.appwidget.components.Scaffold
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import kotlinx.coroutines.coroutineScope

// [START glance_provide_glance]
class FocusWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = FocusContainer.get(context)
        coroutineScope {
            val widgetState = focusWidgetTransform(
                summaryState = container.focusManager.summary,
                completedTodayFlow = container.taskRepository.completedTodayFlow,
                scope = this,
            )
            provideContent {
                val uiState by widgetState
                GlanceTheme {
                    FocusWidgetContent(uiState = uiState)
                }
            }
        }
    }
    // [END glance_provide_glance]

    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        val previewState = FocusWidgetUiState(
            statusLabel = "Focusing",
            taskTitle = "Write the ADS talk",
            formattedTime = "24:55",
            progress = 0.2f,
            primaryAction = WidgetPrimaryAction.Pause,
            isSessionActive = true,
            completedToday = 1,
        )
        provideContent {
            GlanceTheme {
                FocusWidgetContent(uiState = previewState)
            }
        }
    }
}

class FocusWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FocusWidget()
}

class FocusWidgetPrimaryActionCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val container = FocusContainer.get(context)
        container.focusManager.togglePrimaryAction(container.defaultTask)
    }
}

class FocusWidgetStopActionCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        FocusContainer.get(context).focusManager.stopSession()
    }
}

@Composable
private fun FocusWidgetContent(uiState: FocusWidgetUiState) {
    Scaffold(
        backgroundColor = GlanceTheme.colors.widgetBackground,
        modifier = GlanceModifier
            .fillMaxSize()
            .clickable(actionStartActivity<GlanceActivity>()),
    ) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(vertical = 12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = GlanceModifier.fillMaxWidth(),
            ) {
                Text(
                    text = "FocusFlow",
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                )
                Spacer(modifier = GlanceModifier.defaultWeight())
                Text(
                    text = "${uiState.completedToday} done today",
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 12.sp,
                    ),
                )
            }

            Spacer(modifier = GlanceModifier.height(6.dp))

            val subtitle = uiState.taskTitle?.let { "${uiState.statusLabel} • $it" }
                ?: uiState.statusLabel
            Text(
                text = subtitle,
                maxLines = 1,
                style = TextStyle(
                    color = GlanceTheme.colors.primary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )

            Spacer(modifier = GlanceModifier.defaultWeight())

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = GlanceModifier.fillMaxWidth(),
            ) {
                Text(
                    text = uiState.formattedTime,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
                Spacer(modifier = GlanceModifier.defaultWeight())

                if (uiState.isSessionActive) {
                    OutlineButton(
                        text = "Stop",
                        contentColor = GlanceTheme.colors.onSurfaceVariant,
                        onClick = actionRunCallback<FocusWidgetStopActionCallback>(),
                    )
                    Spacer(modifier = GlanceModifier.width(6.dp))
                }

                val actionLabel = when (uiState.primaryAction) {
                    WidgetPrimaryAction.Start -> "Start"
                    WidgetPrimaryAction.Pause -> "Pause"
                    WidgetPrimaryAction.Resume -> "Resume"
                }
                FilledButton(
                    text = actionLabel,
                    onClick = actionRunCallback<FocusWidgetPrimaryActionCallback>(),
                )
            }

            Spacer(modifier = GlanceModifier.height(8.dp))

            LinearProgressIndicator(
                progress = uiState.progress,
                color = GlanceTheme.colors.primary,
                backgroundColor = GlanceTheme.colors.surfaceVariant,
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .height(6.dp),
            )
        }
    }
}
