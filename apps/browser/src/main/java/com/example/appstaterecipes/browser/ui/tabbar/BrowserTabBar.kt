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
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Window
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
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
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.appstaterecipes.browser.model.BrowserRoute
import com.example.appstaterecipes.browser.state.BrowserWindowUiState
import com.example.appstaterecipes.browser.state.TabSummary
import com.example.appstaterecipes.browser.state.WindowSummary
import com.example.appstaterecipes.browser.ui.TabActionsDropdownMenu
import com.example.appstaterecipes.browser.ui.WindowSwitcherDropdownMenu
import com.example.appstaterecipes.browser.ui.dragdrop.OffscreenTabDragShadowCapture
import com.example.appstaterecipes.browser.ui.dragdrop.TabDragPayload
import com.example.appstaterecipes.browser.ui.dragdrop.TabDragPreviewMode
import com.example.appstaterecipes.browser.ui.dragdrop.TabDragShadowBuilder
import com.example.appstaterecipes.browser.ui.dragdrop.TabDragShadowController
import com.example.appstaterecipes.ui.theme.AppStateRecipesTheme

private class ActiveDragShadowSession {
    var builder: TabDragShadowBuilder? = null
    var lastDraggedTab: TabSummary? = null
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
    modifier: Modifier = Modifier,
    onOpenStateStoreViewer: (() -> Unit)? = null,
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
    onMoveTab: (tabId: String, toIndex: Int) -> Unit = { _, _ -> },
    canDragToNewWindow: Boolean = uiState.canPopOut,
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

    fun tryReorderAt(dragX: Float) {
        if (dragState.awaitingLayoutAfterReorder) return
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
                .windowInsetsPadding(windowInsets.only(WindowInsetsSides.Top))
                .padding(top = 6.dp),
        ) {
            val localView = LocalView.current
            val dragPreviewPicture = remember { Picture() }
            var preparedDragTabId by remember { mutableStateOf<String?>(null) }
            val dragShadowSession = remember { ActiveDragShadowSession() }

            val isActiveDragSource = TabDragShadowController.activeSourceWindowId == windowId
            val activeDraggedTabId = if (isActiveDragSource) {
                TabDragShadowController.activeDraggedTabId
            } else {
                null
            }
            val previewTabId = activeDraggedTabId ?: preparedDragTabId
            val matchedPreviewTab = tabs.firstOrNull { it.tabId == previewTabId }
            val previewTab = matchedPreviewTab
                ?: dragShadowSession.lastDraggedTab?.takeIf { isActiveDragSource }
            val previewMode = if (isActiveDragSource) {
                TabDragShadowController.activePreviewMode
            } else {
                TabDragPreviewMode.MoveToWindow
            }

            SideEffect {
                if (isActiveDragSource) {
                    if (matchedPreviewTab != null) {
                        dragShadowSession.lastDraggedTab = matchedPreviewTab
                    }
                } else {
                    dragShadowSession.builder = null
                    dragShadowSession.lastDraggedTab = null
                }
            }

            if (previewTab != null) {
                key(previewTab.tabId) {
                    OffscreenTabDragShadowCapture(
                        tab = previewTab,
                        mode = previewMode,
                        picture = dragPreviewPicture,
                        onPictureRecorded = {
                            if (TabDragShadowController.activeSourceWindowId == windowId) {
                                dragShadowSession.builder?.let { builder ->
                                    localView.updateDragShadow(builder)
                                }
                            }
                        },
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(windowInsets.only(WindowInsetsSides.Horizontal))
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
                val canDragTabs = canPopOut || hasOtherWindows
                val hasMenuActions = canDragTabs || onOpenStateStoreViewer != null

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
                                canDrag = canDragTabs,
                                canDragToNewWindow = canDragToNewWindow,
                                closingBackProgress = if (tab.isSelected) closingTabBackProgress else zeroProgress,
                                dragPreviewPicture = dragPreviewPicture,
                                onPrepareDrag = { preparedDragTabId = tab.tabId },
                                onReleaseDragPreparation = {
                                    if (preparedDragTabId == tab.tabId) {
                                        preparedDragTabId = null
                                    }
                                },
                                onDragShadowStarted = { builder ->
                                    dragShadowSession.builder = builder
                                    dragShadowSession.lastDraggedTab = tab
                                },
                                onDragStartFailed = {
                                    dragShadowSession.builder = null
                                    dragShadowSession.lastDraggedTab = null
                                },
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
                        enabled = hasMenuActions,
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
