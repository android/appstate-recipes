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

package com.example.appstaterecipes.browser.ui

import android.os.Build
import android.view.View
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.ui.graphics.vector.VectorPainter
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Window
import androidx.compose.material3.Badge
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.mimeTypes
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.appstaterecipes.browser.BrowserRoute
import com.example.appstaterecipes.browser.BrowserWindowUiState
import com.example.appstaterecipes.browser.TabDragPayload
import com.example.appstaterecipes.browser.TabSummary
import com.example.appstaterecipes.browser.WindowSummary
import com.example.appstaterecipes.ui.theme.AppStateRecipesTheme
import java.lang.ref.WeakReference
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Computes the target horizontal scroll offset (in pixels) needed to make the tab with
 * [tabBounds] fully visible within [rowBounds], or `null` if the tab is already fully visible.
 *
 * [rowBounds] and [tabBounds] must be in the same unclipped coordinate space (such as root
 * coordinates with `clipBounds = false`).
 */
internal fun calculateAutoScrollOffsetToRevealTab(
    currentScroll: Int,
    maxScroll: Int,
    rowBounds: Rect,
    tabBounds: Rect,
): Int? {
    if (
        rowBounds.width <= 0f ||
        tabBounds.width <= 0f ||
        maxScroll <= 0 ||
        maxScroll == Int.MAX_VALUE
    ) {
        return null
    }

    val rawDelta = when {
        tabBounds.left < rowBounds.left || tabBounds.width > rowBounds.width -> {
            floor(tabBounds.left - rowBounds.left).toInt()
        }
        tabBounds.right > rowBounds.right -> {
            ceil(tabBounds.right - rowBounds.right).toInt()
        }
        else -> return null
    }

    val targetScroll = (currentScroll + rawDelta).coerceIn(0, maxScroll)
    return targetScroll.takeIf { it != currentScroll }
}

/**
 * Computes the target index to move [draggedTabId] to within [tabs] when the drag pointer is at
 * [dragX] (in the same coordinate space as [tabBounds]), or `null` if no neighbor tab's horizontal
 * midpoint has been crossed yet.
 */
internal fun calculateReorderTargetIndex(
    tabs: List<TabSummary>,
    draggedTabId: String,
    dragX: Float,
    tabBounds: Map<String, Rect>,
): Int? {
    val fromIndex = tabs.indexOfFirst { it.tabId == draggedTabId }
    if (fromIndex == -1 || tabs.size <= 1) return null
    val draggedBounds = tabBounds[draggedTabId] ?: return null
    val draggedCenter = draggedBounds.center.x

    return if (dragX > draggedCenter) {
        var targetIndex: Int? = null
        for (i in (fromIndex + 1)..tabs.lastIndex) {
            val bounds = tabBounds[tabs[i].tabId] ?: continue
            if (dragX > bounds.center.x) {
                targetIndex = i
            } else {
                break
            }
        }
        targetIndex
    } else if (dragX < draggedCenter) {
        var targetIndex: Int? = null
        for (i in (fromIndex - 1) downTo 0) {
            val bounds = tabBounds[tabs[i].tabId] ?: continue
            if (dragX < bounds.center.x) {
                targetIndex = i
            } else {
                break
            }
        }
        targetIndex
    } else {
        null
    }
}

/**
 * Non-observable state container for tracking drag geometry, pointer coordinates,
 * and layout lock flags across drag-and-drop frames without triggering composable recompositions.
 */
private class TabBarDragState {
    var activeDragXInRoot: Float? = null
    var tabRowBoundsInRoot: Rect? = null
    val tabBoundsInRoot = mutableMapOf<String, Rect>()
    var awaitingLayoutAfterReorder: Boolean = false
}

@Composable
fun BrowserTabBar(
    uiState: BrowserWindowUiState,
    onBack: () -> Unit,
    onSelectTab: (String) -> Unit,
    onCloseTab: (String) -> Unit,
    onNewTab: () -> Unit,
    onPopOutTab: (String) -> Unit,
    onMoveTabToWindow: (sourceWindowId: String, tabId: String, targetWindowId: String) -> Unit,
    onSwitchWindow: (String) -> Unit,
    onOpenWindowInNewWindow: (String) -> Unit,
    onCloseWindow: (String) -> Unit,
    onOpenStateStoreViewer: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    onMoveTab: (tabId: String, toIndex: Int) -> Unit = { _, _ -> },
    closingTabBackProgress: () -> Float = { 0f },
) {
    val windowId = uiState.windowId
    val windowDisplayName = uiState.displayName
    val activeTabId = uiState.activeTabId
    val tabs = uiState.tabs
    val allWindows = uiState.allWindows
    val otherWindows = uiState.otherWindows
    val canGoBack = uiState.canGoBack

    var isDragOver by remember { mutableStateOf(false) }
    val currentTabs by rememberUpdatedState(tabs)
    val currentOnMoveTab by rememberUpdatedState(onMoveTab)
    val currentOnMoveTabToWindow by rememberUpdatedState(onMoveTabToWindow)

    val dragState = remember(windowId) { TabBarDragState() }
    val tabScrollState = rememberScrollState()
    val bringIntoViewRequesters = remember(windowId) { mutableMapOf<String, BringIntoViewRequester>() }

    val knownTabIds = remember(windowId) {
        tabs.mapTo(mutableSetOf()) { it.tabId }
    }
    val tabIds = remember(tabs) { tabs.map { it.tabId } }
    LaunchedEffect(windowId, tabIds) {
        val addedTabId = tabIds.lastOrNull { it !in knownTabIds }
        knownTabIds.clear()
        knownTabIds.addAll(tabIds)
        if (addedTabId != null) {
            bringIntoViewRequesters[addedTabId]?.bringIntoView()
        }
    }

    fun tryReorderAt(dragX: Float, ignoreLayoutLock: Boolean = false) {
        if (!ignoreLayoutLock && dragState.awaitingLayoutAfterReorder) return
        if (TabDragShadowController.activeSourceWindowId != windowId) return
        val draggedTabId = TabDragShadowController.activeDraggedTabId ?: return
        val targetIndex = calculateReorderTargetIndex(
            tabs = currentTabs,
            draggedTabId = draggedTabId,
            dragX = dragX,
            tabBounds = dragState.tabBoundsInRoot,
        )
        if (targetIndex != null) {
            dragState.awaitingLayoutAfterReorder = true
            currentOnMoveTab(draggedTabId, targetIndex)
        }
    }

    val density = LocalDensity.current
    var autoscrollSpeedPxPerSecond by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(autoscrollSpeedPxPerSecond != 0f, windowId) {
        if (autoscrollSpeedPxPerSecond == 0f) return@LaunchedEffect
        var lastFrameTimeNanos = 0L
        while (autoscrollSpeedPxPerSecond != 0f) {
            withFrameNanos { frameTimeNanos ->
                if (lastFrameTimeNanos != 0L) {
                    val dtSeconds = (frameTimeNanos - lastFrameTimeNanos) / 1_000_000_000f
                    val delta = autoscrollSpeedPxPerSecond * dtSeconds
                    if (delta != 0f) {
                        tabScrollState.dispatchRawDelta(delta)
                        dragState.activeDragXInRoot?.let { tryReorderAt(it) }
                    }
                }
                lastFrameTimeNanos = frameTimeNanos
            }
        }
    }

    fun updateAutoscrollSpeed(dragX: Float) {
        val rowBounds = dragState.tabRowBoundsInRoot
        if (rowBounds == null) {
            autoscrollSpeedPxPerSecond = 0f
            return
        }
        val edgeZonePx = with(density) { 36.dp.toPx() }
        val maxSpeedPxPerSecond = with(density) { 480.dp.toPx() }

        autoscrollSpeedPxPerSecond = when {
            dragX < rowBounds.left + edgeZonePx && tabScrollState.value > 0 -> {
                val ratio = ((rowBounds.left + edgeZonePx - dragX) / edgeZonePx).coerceIn(0f, 1f)
                -maxSpeedPxPerSecond * ratio
            }
            dragX > rowBounds.right - edgeZonePx && tabScrollState.value < tabScrollState.maxValue -> {
                val ratio = ((dragX - (rowBounds.right - edgeZonePx)) / edgeZonePx).coerceIn(0f, 1f)
                maxSpeedPxPerSecond * ratio
            }
            else -> 0f
        }
    }

    DisposableEffect(windowId) {
        onDispose {
            TabDragShadowController.onTargetWindowExited(windowId)
        }
    }

    val dropTarget = remember(windowId) {
        object : DragAndDropTarget {
            override fun onStarted(event: DragAndDropEvent) {}

            override fun onEntered(event: DragAndDropEvent) {
                TabDragShadowController.onTargetWindowEntered(windowId)
                isDragOver = TabDragShadowController.activeSourceWindowId != windowId
            }

            override fun onMoved(event: DragAndDropEvent) {
                val dragX = event.toAndroidDragEvent().x
                dragState.activeDragXInRoot = dragX
                updateAutoscrollSpeed(dragX)
                tryReorderAt(dragX)
            }

            override fun onExited(event: DragAndDropEvent) {
                isDragOver = false
                dragState.activeDragXInRoot = null
                autoscrollSpeedPxPerSecond = 0f
                TabDragShadowController.onTargetWindowExited(windowId)
            }

            override fun onEnded(event: DragAndDropEvent) {
                isDragOver = false
                dragState.activeDragXInRoot = null
                autoscrollSpeedPxPerSecond = 0f
                dragState.awaitingLayoutAfterReorder = false
                TabDragShadowController.onDragEnded()
            }

            override fun onDrop(event: DragAndDropEvent): Boolean {
                val dragX = event.toAndroidDragEvent().x
                isDragOver = false
                dragState.activeDragXInRoot = null
                autoscrollSpeedPxPerSecond = 0f
                dragState.awaitingLayoutAfterReorder = false
                val payload = TabDragPayload.fromDragEvent(event)
                if (payload == null) {
                    TabDragShadowController.onDragEnded()
                    return false
                }
                val (sourceWindowId, tabId) = payload
                if (sourceWindowId == windowId) {
                    val targetIndex = calculateReorderTargetIndex(
                        tabs = currentTabs,
                        draggedTabId = tabId,
                        dragX = dragX,
                        tabBounds = dragState.tabBoundsInRoot,
                    )
                    if (targetIndex != null) {
                        currentOnMoveTab(tabId, targetIndex)
                    }
                } else {
                    currentOnMoveTabToWindow(sourceWindowId, tabId, windowId)
                }
                TabDragShadowController.onDragEnded()
                return true
            }
        }
    }

    val animatedBg by animateColorAsState(
        targetValue = if (isDragOver) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        label = "tab_bar_bg",
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .dragAndDropTarget(
                shouldStartDragAndDrop = { event ->
                    event.mimeTypes().contains(TabDragPayload.MIME_TYPE)
                },
                target = dropTarget,
            ),
        color = animatedBg,
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = 6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                IconButton(
                    onClick = onBack,
                    enabled = canGoBack,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Navigate back in tab",
                        modifier = Modifier.size(20.dp),
                    )
                }

                var windowMenuExpanded by remember { mutableStateOf(false) }

                Box {
                    Surface(
                        onClick = { windowMenuExpanded = true },
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 8.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Window,
                                contentDescription = "$windowDisplayName · Switch window",
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(16.dp),
                            )
                            Icon(
                                imageVector = Icons.Rounded.ArrowDropDown,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }

                    WindowSwitcherDropdownMenu(
                        expanded = windowMenuExpanded,
                        onDismissRequest = { windowMenuExpanded = false },
                        currentWindowId = windowId,
                        windows = allWindows,
                        onSelectWindowInCurrent = { targetWindowId ->
                            onSwitchWindow(targetWindowId)
                        },
                        onOpenWindowInNewWindow = { targetWindowId ->
                            onOpenWindowInNewWindow(targetWindowId)
                        },
                        onCloseWindow = { targetWindowId ->
                            onCloseWindow(targetWindowId)
                        },
                    )
                }

                val canPopOut = uiState.canPopOut
                val hasOtherWindows = otherWindows.isNotEmpty()
                val canPerformActions = canPopOut || hasOtherWindows

                val textMeasurer = rememberTextMeasurer()
                val openInNewPainter = rememberVectorPainter(Icons.AutoMirrored.Rounded.OpenInNew)
                val dragPreviewStyle = rememberTabDragPreviewStyle()

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .weight(1f)
                        .onGloballyPositioned { coords ->
                            dragState.tabRowBoundsInRoot = coords.boundsInRoot()
                            dragState.awaitingLayoutAfterReorder = false
                        }
                        .horizontalScroll(tabScrollState),
                ) {
                    tabs.forEach { tab ->
                        key(tab.tabId) {
                            val requester = remember(tab.tabId) { BringIntoViewRequester() }
                            DisposableEffect(tab.tabId) {
                                bringIntoViewRequesters[tab.tabId] = requester
                                onDispose {
                                    bringIntoViewRequesters.remove(tab.tabId)
                                    dragState.tabBoundsInRoot.remove(tab.tabId)
                                }
                            }
                            val zeroProgress: () -> Float = { 0f }
                            BrowserTabChip(
                                windowId = windowId,
                                tab = tab,
                                canDrag = canPerformActions,
                                canDragToNewWindow = canPopOut,
                                closingBackProgress = if (tab.isSelected) closingTabBackProgress else zeroProgress,
                                textMeasurer = textMeasurer,
                                openInNewPainter = openInNewPainter,
                                dragPreviewStyle = dragPreviewStyle,
                                onSelectTab = { onSelectTab(tab.tabId) },
                                onCloseTab = { onCloseTab(tab.tabId) },
                                modifier = Modifier
                                    .bringIntoViewRequester(requester)
                                    .onGloballyPositioned { coords ->
                                        dragState.tabBoundsInRoot[tab.tabId] = coords
                                            .findRootCoordinates()
                                            .localBoundingBoxOf(coords, clipBounds = false)
                                        dragState.awaitingLayoutAfterReorder = false
                                    },
                            )
                        }
                    }
                }

                FilledTonalIconButton(
                    onClick = onNewTab,
                    modifier = Modifier.size(32.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = "New Tab",
                        modifier = Modifier.size(18.dp),
                    )
                }

                var menuExpanded by remember { mutableStateOf(false) }

                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        enabled = canPerformActions,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = "Tab options",
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    TabActionsDropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        canPopOut = canPopOut,
                        onPopOut = { onPopOutTab(activeTabId) },
                        otherWindows = otherWindows,
                        onMoveToWindow = { targetWindowId ->
                            onMoveTabToWindow(windowId, activeTabId, targetWindowId)
                        },
                        onOpenStateStoreViewer = onOpenStateStoreViewer,
                    )
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            )
        }
    }
}

private val TabChipShape = RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp, bottomStart = 4.dp, bottomEnd = 4.dp)

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
    textMeasurer: TextMeasurer = rememberTextMeasurer(),
    openInNewPainter: VectorPainter = rememberVectorPainter(Icons.AutoMirrored.Rounded.OpenInNew),
    dragPreviewStyle: TabDragPreviewStyle = rememberTabDragPreviewStyle(),
) {
    val currentOnSelectTab by rememberUpdatedState(onSelectTab)
    val currentCanDrag by rememberUpdatedState(canDrag)
    val currentCanDragToNewWindow by rememberUpdatedState(canDragToNewWindow)
    val currentTab by rememberUpdatedState(tab)
    val dragOutIntentFactory = LocalBrowserDragOutIntentFactory.current
    val context = LocalContext.current
    val localView = LocalView.current
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val currentTabIconPainter by rememberUpdatedState(rememberVectorPainter(tab.currentRoute.icon))
    val currentDragPreviewStyle by rememberUpdatedState(dragPreviewStyle)

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
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        if (!currentCanDrag) return@detectDragGesturesAfterLongPress

                        currentOnSelectTab()

                        val canCreateNewWindow = currentCanDragToNewWindow
                        val canStartIntentSenderOnUnhandledDrag =
                            canCreateNewWindow && Build.VERSION.SDK_INT >= 35

                        val payload = TabDragPayload(sourceWindowId = windowId, tabId = tab.tabId)
                        val intentSender = if (canStartIntentSenderOnUnhandledDrag) {
                            dragOutIntentFactory.createDragOutPendingIntent(context, windowId, tab.tabId)?.intentSender
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

                        val viewRef = WeakReference(localView)
                        lateinit var shadowBuilder: TabDragShadowBuilder
                        val initialMode = TabDragShadowController.startDrag(
                            sourceWindowId = windowId,
                            canCreateNewWindow = canCreateNewWindow,
                            onPreviewModeChanged = { newMode ->
                                shadowBuilder.mode = newMode
                                viewRef.get()?.updateDragShadow(shadowBuilder)
                            },
                            draggedTabId = tab.tabId,
                        )

                        shadowBuilder = TabDragShadowBuilder(
                            density = density,
                            layoutDirection = layoutDirection,
                            tab = currentTab,
                            tabIconPainter = currentTabIconPainter,
                            openInNewPainter = openInNewPainter,
                            textMeasurer = textMeasurer,
                            style = currentDragPreviewStyle,
                            initialMode = initialMode,
                        )

                        val started = localView.startDragAndDrop(
                            clipData,
                            shadowBuilder,
                            windowId,
                            flags,
                        )
                        if (!started) {
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
private fun BrowserTabBarPreview() {
    val sampleTabs = listOf(
        TabSummary(
            tabId = "tab-1",
            isSelected = true,
            stackDepth = 1,
            currentRoute = BrowserRoute.Home,
        ),
        TabSummary(
            tabId = "tab-2",
            isSelected = false,
            stackDepth = 3,
            currentRoute = BrowserRoute.Article(42, "Article #42"),
        ),
    )
    val sampleAllWindows = listOf(
        WindowSummary(
            windowId = "win-1",
            displayName = "Window 1",
            tabCount = 2,
            firstTabTitles = listOf("Home", "Article #42"),
            isAttachedToActivity = true,
        ),
        WindowSummary(
            windowId = "win-2",
            displayName = "Window 2",
            tabCount = 3,
            firstTabTitles = listOf("Search: compose", "Deep Dive: NavDisplay"),
            isAttachedToActivity = true,
        ),
        WindowSummary(
            windowId = "win-3",
            displayName = "Window 3",
            tabCount = 1,
            firstTabTitles = listOf("Home"),
            isAttachedToActivity = false,
        ),
    )
    AppStateRecipesTheme {
        BrowserTabBar(
            uiState = BrowserWindowUiState(
                windowId = "win-1",
                displayName = "Window 1",
                activeTabId = "tab-1",
                tabs = sampleTabs,
                activeBackStack = listOf(BrowserRoute.Home),
                allWindows = sampleAllWindows,
            ),
            onBack = {},
            onSelectTab = {},
            onCloseTab = {},
            onNewTab = {},
            onPopOutTab = {},
            onMoveTabToWindow = { _, _, _ -> },
            onSwitchWindow = {},
            onOpenWindowInNewWindow = {},
            onCloseWindow = {},
        )
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



