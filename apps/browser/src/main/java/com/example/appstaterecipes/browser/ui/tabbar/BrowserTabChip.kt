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

package com.example.appstaterecipes.browser.ui.tabbar

import android.graphics.Picture
import android.os.Build
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.appstaterecipes.browser.model.BrowserRoute
import com.example.appstaterecipes.browser.state.TabSummary
import com.example.appstaterecipes.browser.ui.dragdrop.LocalBrowserDragOutIntentFactory
import com.example.appstaterecipes.browser.ui.dragdrop.TabDragPayload
import com.example.appstaterecipes.browser.ui.dragdrop.TabDragShadowBuilder
import com.example.appstaterecipes.browser.ui.dragdrop.TabDragShadowController
import com.example.appstaterecipes.browser.ui.icon
import com.example.appstaterecipes.ui.theme.AppStateRecipesTheme

private val TabChipShape = RoundedCornerShape(
    topStart = 10.dp,
    topEnd = 10.dp,
    bottomStart = 4.dp,
    bottomEnd = 4.dp,
)

@Composable
internal fun BrowserTabChip(
    windowId: String,
    tab: TabSummary,
    onSelectTab: () -> Unit,
    onCloseTab: () -> Unit,
    modifier: Modifier = Modifier,
    canDrag: Boolean = true,
    canDragToNewWindow: Boolean = true,
    closingBackProgress: () -> Float = { 0f },
    dragPreviewPicture: Picture? = null,
    onPrepareDrag: () -> Unit = {},
    onReleaseDragPreparation: () -> Unit = {},
    onDragShadowStarted: (TabDragShadowBuilder) -> Unit = {},
    onDragStartFailed: () -> Unit = {},
) {
    val currentOnSelectTab by rememberUpdatedState(onSelectTab)
    val currentCanDrag by rememberUpdatedState(canDrag)
    val currentCanDragToNewWindow by rememberUpdatedState(canDragToNewWindow)
    val currentOnPrepareDrag by rememberUpdatedState(onPrepareDrag)
    val currentOnReleaseDragPreparation by rememberUpdatedState(onReleaseDragPreparation)
    val currentOnDragShadowStarted by rememberUpdatedState(onDragShadowStarted)
    val currentOnDragStartFailed by rememberUpdatedState(onDragStartFailed)
    val dragOutIntentFactory = LocalBrowserDragOutIntentFactory.current
    val context = LocalContext.current
    val localView = LocalView.current
    val density = LocalDensity.current
    val fallbackPicture = remember { Picture() }
    val effectivePicture = dragPreviewPicture ?: fallbackPicture

    val containerColor = if (tab.isSelected) {
        MaterialTheme.colorScheme.surface
    } else {
        Color.Transparent
    }
    val contentColor = if (tab.isSelected) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .graphicsLayer {
                val isBeingDragged =
                    TabDragShadowController.activeSourceWindowId == windowId &&
                        TabDragShadowController.activeDraggedTabId == tab.tabId
                val backAlpha = (1f - closingBackProgress() * 0.75f).coerceIn(0f, 1f)
                val dragAlpha = if (isBeingDragged) 0.38f else 1f
                alpha = backAlpha * dragAlpha
            }
            .clip(TabChipShape)
            .background(color = containerColor, shape = TabChipShape)
            .pointerInput(windowId, tab.tabId) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    if (currentCanDrag) {
                        currentOnPrepareDrag()
                    }
                    waitForUpOrCancellation(pass = PointerEventPass.Initial)
                    currentOnReleaseDragPreparation()
                }
            }
            .pointerInput(windowId, tab.tabId, effectivePicture) {
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        if (!currentCanDrag) return@detectDragGesturesAfterLongPress

                        currentOnSelectTab()

                        val canCreateNewWindow = currentCanDragToNewWindow
                        val isCaptionBarVisible = Build.VERSION.SDK_INT >= 35 &&
                            localView.rootWindowInsets?.isVisible(
                                android.view.WindowInsets.Type.captionBar(),
                            ) == true
                        val canStartIntentSenderOnUnhandledDrag =
                            TabDragPayload.shouldStartIntentSenderOnUnhandledDrag(
                                canCreateNewWindow = canCreateNewWindow,
                                sdkInt = Build.VERSION.SDK_INT,
                                isCaptionBarVisible = isCaptionBarVisible,
                            )

                        val payload = TabDragPayload(sourceWindowId = windowId, tabId = tab.tabId)
                        val intentSender = if (canStartIntentSenderOnUnhandledDrag) {
                            dragOutIntentFactory
                                .createDragOutPendingIntent(context, windowId, tab.tabId)
                                ?.intentSender
                        } else {
                            null
                        }

                        val clipData = payload.toClipData(intentSender)

                        val flags = if (Build.VERSION.SDK_INT >= 35) {
                            if (canStartIntentSenderOnUnhandledDrag) {
                                View.DRAG_FLAG_GLOBAL_SAME_APPLICATION or
                                    View.DRAG_FLAG_START_INTENT_SENDER_ON_UNHANDLED_DRAG
                            } else {
                                View.DRAG_FLAG_GLOBAL_SAME_APPLICATION
                            }
                        } else {
                            View.DRAG_FLAG_GLOBAL
                        }

                        TabDragShadowController.startDrag(
                            sourceWindowId = windowId,
                            canCreateNewWindow = canCreateNewWindow,
                            draggedTabId = tab.tabId,
                        )

                        val shadowBuilder = TabDragShadowBuilder(
                            density = density,
                            picture = effectivePicture,
                        )
                        currentOnDragShadowStarted(shadowBuilder)

                        val started = localView.startDragAndDrop(
                            clipData,
                            shadowBuilder,
                            windowId,
                            flags,
                        )
                        if (!started) {
                            currentOnDragStartFailed()
                            TabDragShadowController.onDragEnded()
                        }
                    },
                    onDrag = { _, _ -> },
                )
            }
            .selectable(
                selected = tab.isSelected,
                onClick = onSelectTab,
                role = Role.Tab,
            )
            .widthIn(min = 100.dp, max = 200.dp)
            .padding(start = 10.dp, top = 4.dp, bottom = 4.dp, end = 2.dp),
    ) {
        Icon(
            imageVector = tab.currentRoute.icon,
            contentDescription = null,
            tint = if (tab.isSelected) MaterialTheme.colorScheme.primary else contentColor.copy(alpha = 0.7f),
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(6.dp))

        Text(
            text = tab.title,
            color = contentColor,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (tab.isSelected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )

        if (tab.stackDepth > 1) {
            Spacer(Modifier.width(4.dp))
            Badge(
                containerColor = if (tab.isSelected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                contentColor = if (tab.isSelected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            ) {
                Text(
                    text = tab.stackDepth.toString(),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }

        IconButton(
            onClick = onCloseTab,
            modifier = Modifier.size(28.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "Close tab",
                tint = contentColor.copy(alpha = 0.7f),
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun BrowserTabChipSelectedPreview() {
    AppStateRecipesTheme {
        BrowserTabChip(
            windowId = "win-1",
            tab = TabSummary(
                tabId = "tab-1",
                isSelected = true,
                stackDepth = 1,
            ),
            onSelectTab = {},
            onCloseTab = {},
            modifier = Modifier.padding(8.dp),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun BrowserTabChipUnselectedPreview() {
    AppStateRecipesTheme {
        BrowserTabChip(
            windowId = "win-1",
            tab = TabSummary(
                tabId = "tab-2",
                isSelected = false,
                stackDepth = 3,
                currentRoute = BrowserRoute.Article(1, "Multi-Window Guide"),
            ),
            onSelectTab = {},
            onCloseTab = {},
            modifier = Modifier.padding(8.dp),
        )
    }
}
