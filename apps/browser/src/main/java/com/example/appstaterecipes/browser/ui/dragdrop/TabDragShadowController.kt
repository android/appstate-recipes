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

package com.example.appstaterecipes.browser.ui.dragdrop

import android.view.View
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Visual mode for the system drag shadow while dragging a browser tab.
 */
@Immutable
sealed interface TabDragPreviewMode {
    /**
     * Dropping at the current location will pop the tab out into a new browser window.
     * Renders a miniature browser window frame with a "New window" header and page wireframe.
     */
    data object NewWindow : TabDragPreviewMode

    /**
     * Dropping at the current location will move/merge the tab into an existing browser window.
     * Renders a compact floating tab chip.
     */
    data object MoveToWindow : TabDragPreviewMode

    /**
     * Dropping at the current location is not supported (e.g., outside the tab bar when
     * dragging out into a new window is disabled). Renders a grayed-out floating tab chip.
     */
    data object Unavailable : TabDragPreviewMode
}

/**
 * Coordinates cross-window drag shadow updates between the source `BrowserActivity` window
 * (which owns the system drag token and [View.updateDragShadow]) and any target tab bar or
 * window drop target that the pointer enters or exits during an active drag.
 */
internal object TabDragShadowController {
    private class ActiveDragSession(
        val sourceWindowId: String,
        val draggedTabId: String?,
        val canCreateNewWindow: Boolean,
        initialMode: TabDragPreviewMode,
        var hoveredTargetWindowId: String? = null,
    ) {
        var previewMode by mutableStateOf(initialMode)
    }

    private var activeSession by mutableStateOf<ActiveDragSession?>(null)

    val activeSourceWindowId: String?
        get() = activeSession?.sourceWindowId

    val activeDraggedTabId: String?
        get() = activeSession?.draggedTabId

    val activePreviewMode: TabDragPreviewMode
        get() = activeSession?.previewMode ?: TabDragPreviewMode.MoveToWindow

    fun startDrag(
        sourceWindowId: String,
        canCreateNewWindow: Boolean,
        draggedTabId: String? = null,
    ) {
        // A drag starts from within the source window's tab bar, so the initial preview
        // is always MoveToWindow (the tab chip). If the pointer moves out of any window,
        // onTargetWindowExited will transition to NewWindow (if canCreateNewWindow is true)
        // or Unavailable (if false).
        activeSession = ActiveDragSession(
            sourceWindowId = sourceWindowId,
            draggedTabId = draggedTabId,
            canCreateNewWindow = canCreateNewWindow,
            initialMode = TabDragPreviewMode.MoveToWindow,
            hoveredTargetWindowId = sourceWindowId,
        )
    }

    fun onTargetWindowEntered(targetWindowId: String) {
        val session = activeSession ?: return
        session.hoveredTargetWindowId = targetWindowId
        session.previewMode = TabDragPreviewMode.MoveToWindow
    }

    fun onTargetWindowExited(targetWindowId: String) {
        val session = activeSession ?: return
        if (session.hoveredTargetWindowId == targetWindowId) {
            session.hoveredTargetWindowId = null
            session.previewMode = defaultMode(session.canCreateNewWindow)
        }
    }

    fun onDragEnded() {
        activeSession = null
    }

    private fun defaultMode(canCreateNewWindow: Boolean): TabDragPreviewMode =
        if (canCreateNewWindow) {
            TabDragPreviewMode.NewWindow
        } else {
            TabDragPreviewMode.Unavailable
        }
}
