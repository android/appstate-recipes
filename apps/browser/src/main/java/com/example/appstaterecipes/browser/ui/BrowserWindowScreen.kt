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

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.mimeTypes
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.scene.Scene
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.NavigationEventTransitionState
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.example.appstaterecipes.browser.model.BackStackItem
import com.example.appstaterecipes.browser.model.BrowserRoute
import com.example.appstaterecipes.browser.state.BrowserNavigator
import com.example.appstaterecipes.browser.state.BrowserWindowSession
import com.example.appstaterecipes.browser.state.BrowserWindowUiState
import com.example.appstaterecipes.browser.state.EntryScrollStateHolder
import com.example.appstaterecipes.browser.state.TabSummary
import com.example.appstaterecipes.browser.state.WindowSummary
import com.example.appstaterecipes.browser.state.rememberBrowserWindowSession
import com.example.appstaterecipes.browser.state.rememberEntryScrollState
import com.example.appstaterecipes.browser.ui.dragdrop.TabDragPayload
import com.example.appstaterecipes.browser.ui.dragdrop.TabDragPreviewMode
import com.example.appstaterecipes.browser.ui.dragdrop.TabDragShadowController
import com.example.appstaterecipes.browser.ui.panes.ArticlePane
import com.example.appstaterecipes.browser.ui.panes.HomePane
import com.example.appstaterecipes.browser.ui.panes.SearchPane
import com.example.appstaterecipes.browser.ui.tabbar.BrowserTabBar
import com.example.appstaterecipes.browser.window.BrowserWindowLauncher
import com.example.appstaterecipes.ui.excludeBottom
import com.example.appstaterecipes.ui.theme.AppStateRecipesTheme

// [START browser_window_screen]
@Composable
fun BrowserWindowScreen(
    windowId: String,
    navigator: BrowserNavigator,
    onSwitchWindow: (String) -> Unit,
    onLaunchNewWindow: (String) -> Unit,
    onCloseEmptyWindow: () -> Unit,
    modifier: Modifier = Modifier,
    // [START_EXCLUDE]
    onOpenStateStoreViewer: (() -> Unit)? = null,
    // [END_EXCLUDE]
) {
    val session = rememberBrowserWindowSession(navigator, windowId)
    val currentUiState by session.uiState

    // When the last tab in this window is closed or moved into another window,
    // uiState becomes null. Close this Activity task cleanly.
    LaunchedEffect(windowId, currentUiState == null) {
        if (currentUiState == null) {
            onCloseEmptyWindow()
        }
    }

    val uiState = currentUiState ?: return

    // [START_EXCLUDE]
    val closingTabBackProgress = rememberTabCloseBackProgress(
        enabled = uiState.canPopOut && !uiState.canGoBack,
        onCloseTab = { session.closeTab(uiState.activeTabId) },
    )
    // [END_EXCLUDE]

    BrowserWindowContent(
        uiState = uiState,
        session = session,
        // [START_EXCLUDE]
        closingTabBackProgress = closingTabBackProgress,
        // [END_EXCLUDE]
        onPopOutTab = { tabId ->
            session.popOutTab(tabId)?.let(onLaunchNewWindow)
        },
        // [START_EXCLUDE]
        onMoveTabToWindow = { sourceWindowId, tabId, targetWindowId ->
            if (targetWindowId == windowId) {
                val shouldSwitchToTargetWindow = session.receiveTabFromWindow(sourceWindowId, tabId)
                if (shouldSwitchToTargetWindow) {
                    onSwitchWindow(targetWindowId)
                }
            } else {
                val shouldSwitchToTargetWindow = navigator.moveTabToWindow(sourceWindowId, tabId, targetWindowId)
                if (sourceWindowId == windowId && shouldSwitchToTargetWindow) {
                    onSwitchWindow(targetWindowId)
                }
            }
        },
        // [END_EXCLUDE]
        onSwitchWindow = onSwitchWindow,
        onOpenWindowInNewWindow = onLaunchNewWindow,
        onCloseWindow = { targetWindowId ->
            navigator.closeWindow(targetWindowId)
        },
        // [START_EXCLUDE]
        onOpenStateStoreViewer = onOpenStateStoreViewer,
        // [END_EXCLUDE]
        scrollStateHolder = navigator.scrollStateHolder,
        modifier = modifier,
    )
}
// [END browser_window_screen]

/**
 * Intercepts predictive back gestures when at the root of a tab's back stack to close
 * the tab, returning a lambda provider for real-time back progress (`0f`..`1f`) so
 * reading the animated progress can be deferred to the Draw phase (`graphicsLayer`).
 *
 * Back-handling precedence across the browser window:
 * 1. `uiState.canGoBack` (activeBackStack.size > 1): [NavDisplay]'s internal back handler is enabled and pops routes.
 * 2. `!uiState.canGoBack && uiState.canPopOut` (tabs.size > 1): This handler is enabled, fades out the
 *    active tab during predictive back, and closes the active tab when committed.
 * 3. `!uiState.canGoBack && !uiState.canPopOut`: Both handlers are disabled, so [android.window.OnBackInvokedDispatcher]
 *    runs the system split-screen window-close predictive animation and finishes the Activity.
 *
 * @param enabled Whether tab-close back handling should be active (typically `uiState.canPopOut && !uiState.canGoBack`).
 * @param onCloseTab Callback invoked when the predictive back gesture completes.
 * @return Lambda provider for real-time predictive back progress between `0f` and `1f`.
 */
@Composable
fun rememberTabCloseBackProgress(
    enabled: Boolean,
    onCloseTab: () -> Unit,
): () -> Float {
    val currentOnCloseTab by rememberUpdatedState(onCloseTab)
    val tabCloseBackState = rememberNavigationEventState(currentInfo = NavigationEventInfo.None)

    NavigationBackHandler(
        state = tabCloseBackState,
        isBackEnabled = enabled,
        onBackCompleted = { currentOnCloseTab() },
    )

    return {
        val transitionState = tabCloseBackState.transitionState
        if (enabled && transitionState is NavigationEventTransitionState.InProgress) {
            transitionState.latestEvent.progress
        } else {
            0f
        }
    }
}

@Composable
fun BrowserWindowContent(
    uiState: BrowserWindowUiState,
    session: BrowserWindowSession,
    onPopOutTab: (String) -> Unit,
    onMoveTabToWindow: (sourceWindowId: String, tabId: String, targetWindowId: String) -> Unit,
    onSwitchWindow: (String) -> Unit,
    onOpenWindowInNewWindow: (String) -> Unit,
    onCloseWindow: (String) -> Unit,
    modifier: Modifier = Modifier,
    onOpenStateStoreViewer: (() -> Unit)? = null,
    scrollStateHolder: EntryScrollStateHolder = remember { EntryScrollStateHolder() },
    closingTabBackProgress: () -> Float = { 0f },
) {
    val windowId = uiState.windowId
    val activeTabId = uiState.activeTabId
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val canDragToSplit = remember(context, configuration) {
        BrowserWindowLauncher.supportsSplitLaunch(context)
    }
    val canDragToNewWindow = uiState.canPopOut && canDragToSplit
    val currentCanDragToNewWindow by rememberUpdatedState(canDragToNewWindow)
    val currentOnPopOutTab by rememberUpdatedState(onPopOutTab)
    val currentOnMoveTabToWindow by rememberUpdatedState(onMoveTabToWindow)
    val contentTargetId = "$windowId:content"

    DisposableEffect(contentTargetId) {
        onDispose {
            TabDragShadowController.onTargetWindowExited(contentTargetId)
        }
    }

    val contentDropTarget = remember(windowId) {
        object : DragAndDropTarget {
            override fun onEntered(event: DragAndDropEvent) {
                val sourceWindowId = TabDragShadowController.activeSourceWindowId
                if (sourceWindowId != null && sourceWindowId != windowId) {
                    TabDragShadowController.onTargetWindowEntered(contentTargetId)
                } else if (sourceWindowId == windowId) {
                    TabDragShadowController.onTargetWindowExited(windowId)
                }
            }

            override fun onExited(event: DragAndDropEvent) {
                TabDragShadowController.onTargetWindowExited(contentTargetId)
            }

            override fun onEnded(event: DragAndDropEvent) {
                TabDragShadowController.onDragEnded()
            }

            override fun onDrop(event: DragAndDropEvent): Boolean {
                val payload = TabDragPayload.fromDragEvent(event)
                val handled = handleContentAreaTabDrop(
                    payload = payload,
                    windowId = windowId,
                    canPopOut = currentCanDragToNewWindow,
                    onPopOutTab = currentOnPopOutTab,
                    onMoveTabToWindow = currentOnMoveTabToWindow,
                )
                TabDragShadowController.onDragEnded()
                return handled
            }
        }
    }

    val liveCloseProgress = closingTabBackProgress()
    val animatedCloseProgress by key(activeTabId) {
        animateFloatAsState(
            targetValue = liveCloseProgress,
            label = "tab_close_predictive_back",
        )
    }
    val effectiveCloseProgress: () -> Float = {
        val currentLiveProgress = closingTabBackProgress()
        if (currentLiveProgress > 0f) currentLiveProgress else animatedCloseProgress
    }
    val layoutDirection = LocalLayoutDirection.current

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            BrowserTabBar(
                uiState = uiState,
                canDragToNewWindow = canDragToNewWindow,
                closingTabBackProgress = effectiveCloseProgress,
                onBack = { session.goBack() },
                onSelectTab = { tabId -> session.selectTab(tabId) },
                onCloseTab = { tabId -> session.closeTab(tabId) },
                onNewTab = { session.newTab() },
                onPopOutTab = onPopOutTab,
                onMoveTab = { tabId, toIndex -> session.moveTab(tabId, toIndex) },
                onMoveTabToWindow = onMoveTabToWindow,
                onSwitchWindow = onSwitchWindow,
                onOpenWindowInNewWindow = onOpenWindowInNewWindow,
                onCloseWindow = onCloseWindow,
                onOpenStateStoreViewer = onOpenStateStoreViewer,
            )
        },
    ) { innerPadding ->
        val contentInsets = innerPadding.excludeBottom()
        // [START browser_nav_display]
        val provider = remember(scrollStateHolder, session) {
            entryProvider<BackStackItem> {
                entry<BackStackItem>(
                    clazzContentKey = { it.contentKey },
                ) { item ->
                    val scrollState = rememberEntryScrollState(
                        entryId = item.entryId,
                        scrollStateHolder = scrollStateHolder,
                        isActiveEntry = { session.isActiveBackStackEntry(item.entryId) },
                    )
                    when (val route = item.route) {
                        is BrowserRoute.Home -> HomePane(
                            scrollState = scrollState,
                            onSearch = { query ->
                                session.navigate(BrowserRoute.Search(query))
                            },
                            onOpenArticle = { id, headline ->
                                session.navigate(BrowserRoute.Article(id, headline))
                            },
                        )
                        is BrowserRoute.Search -> SearchPane(
                            query = route.query,
                            scrollState = scrollState,
                            onOpenArticle = { id, headline ->
                                session.navigate(BrowserRoute.Article(id, headline))
                            },
                        )
                        is BrowserRoute.Article -> ArticlePane(
                            article = route,
                            scrollState = scrollState,
                            onOpenRelatedArticle = { id, headline ->
                                session.navigate(BrowserRoute.Article(id, headline))
                            },
                            onOpenInNewTab = { id, headline ->
                                session.newTab(BrowserRoute.Article(id, headline))
                            },
                        )
                    }
                }
            }
        }

        val backStackItems = remember(uiState.activeTabId, uiState.activeBackStack) {
            uiState.activeBackStackItems
        }

        NavDisplay(
            backStack = backStackItems,
            onBack = { session.goBack() },
            entryProvider = provider,
            // [START_EXCLUDE]
            transitionSpec = {
                if (isTabSwitch()) {
                    fadeIn() togetherWith fadeOut()
                } else {
                    slideInHorizontally(initialOffsetX = { it }) togetherWith
                        slideOutHorizontally(targetOffsetX = { -it })
                }
            },
            popTransitionSpec = {
                if (isTabSwitch()) {
                    fadeIn() togetherWith fadeOut()
                } else {
                    slideInHorizontally(initialOffsetX = { -it }) togetherWith
                        slideOutHorizontally(targetOffsetX = { it })
                }
            },
            predictivePopTransitionSpec = {
                EnterTransition.None togetherWith
                    slideOutHorizontally(targetOffsetX = { (it * 0.3f).toInt() })
            },
            modifier = Modifier
                .padding(top = contentInsets.calculateTopPadding())
                .fillMaxSize()
                .dragAndDropTarget(
                    shouldStartDragAndDrop = { event ->
                        event.mimeTypes().contains(TabDragPayload.MIME_TYPE)
                    },
                    target = contentDropTarget,
                )
                .padding(
                    start = contentInsets.calculateStartPadding(layoutDirection),
                    end = contentInsets.calculateEndPadding(layoutDirection),
                )
                .consumeWindowInsets(contentInsets)
                .graphicsLayer {
                    alpha = (1f - effectiveCloseProgress() * 0.85f).coerceIn(0f, 1f)
                },
            // [END_EXCLUDE]
        )
        // [END browser_nav_display]
    }
}

/**
 * Handles a tab dropped onto a browser window's content area (outside of [BrowserTabBar]).
 *
 * - When a tab is dragged out of its own window's [BrowserTabBar] into the content area while
 *   [canPopOut] is `true` (where the [TabDragPreviewMode.NewWindow] preview is shown), dropping
 *   it pops the tab out into a new browser window via [onPopOutTab].
 * - When a tab is dragged from another window into this window's content area, dropping it
 *   moves the tab into this window via [onMoveTabToWindow].
 *
 * @return `true` if the drop was consumed, or `false` otherwise.
 */
internal fun handleContentAreaTabDrop(
    payload: TabDragPayload?,
    windowId: String,
    canPopOut: Boolean,
    onPopOutTab: (tabId: String) -> Unit,
    onMoveTabToWindow: (sourceWindowId: String, tabId: String, targetWindowId: String) -> Unit,
): Boolean {
    if (payload == null) return false
    val (sourceWindowId, tabId) = payload
    return if (sourceWindowId == windowId) {
        if (canPopOut) {
            onPopOutTab(tabId)
            true
        } else {
            false
        }
    } else {
        onMoveTabToWindow(sourceWindowId, tabId, windowId)
        true
    }
}

private fun AnimatedContentTransitionScope<Scene<BackStackItem>>.isTabSwitch(): Boolean {
    val initialTabId = BackStackItem.tabIdFromContentKey(initialState.entries.lastOrNull()?.contentKey)
    val targetTabId = BackStackItem.tabIdFromContentKey(targetState.entries.lastOrNull()?.contentKey)
    return initialTabId != targetTabId
}

@Preview(showBackground = true)
@Composable
private fun BrowserWindowContentPreview() {
    val sampleTabs = listOf(
        TabSummary(
            tabId = "tab-1",
            isSelected = true,
            stackDepth = 1,
        ),
        TabSummary(
            tabId = "tab-2",
            isSelected = false,
            stackDepth = 2,
            currentRoute = BrowserRoute.Article(1, "Multi-Window State Management"),
        ),
    )
    val allWindows = listOf(
        WindowSummary(
            windowId = "win-preview",
            displayName = "Window 1",
            tabCount = 2,
            firstTabTitles = listOf("Home", "Multi-Window State Management"),
            isAttachedToActivity = true,
        ),
        WindowSummary(
            windowId = "win-2",
            displayName = "Window 2",
            tabCount = 2,
            firstTabTitles = listOf("Search: compose", "Documentation"),
            isAttachedToActivity = false,
        ),
    )
    val uiState = BrowserWindowUiState(
        windowId = "win-preview",
        displayName = "Window 1",
        activeTabId = "tab-1",
        tabs = sampleTabs,
        activeBackStack = listOf(BrowserRoute.Home),
        allWindows = allWindows,
    )
    val previewUiState = remember { mutableStateOf<BrowserWindowUiState?>(uiState) }
    val fakeSession = remember {
        object : BrowserWindowSession {
            override val windowId: String = "win-preview"
            override val uiState: State<BrowserWindowUiState?> = previewUiState
            override fun navigate(route: BrowserRoute) {}
            override fun goBack() {}
            override fun newTab(initialRoute: BrowserRoute): String = "tab-new"
            override fun selectTab(tabId: String) {}
            override fun closeTab(tabId: String) {}
            override fun moveTab(tabId: String, toIndex: Int) {}
            override fun popOutTab(tabId: String): String? = null
            override fun receiveTabFromWindow(sourceWindowId: String, tabId: String): Boolean = false
            override fun isActiveBackStackEntry(entryId: String): Boolean = true
        }
    }

    AppStateRecipesTheme {
        BrowserWindowContent(
            uiState = uiState,
            session = fakeSession,
            onPopOutTab = {},
            onMoveTabToWindow = { _, _, _ -> },
            onSwitchWindow = {},
            onOpenWindowInNewWindow = {},
            onCloseWindow = {},
        )
    }
}
