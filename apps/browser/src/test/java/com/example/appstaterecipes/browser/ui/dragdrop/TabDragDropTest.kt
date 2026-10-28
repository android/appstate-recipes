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

import com.example.appstaterecipes.browser.ui.handleContentAreaTabDrop
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TabDragDropTest {

    @Before
    fun setUp() {
        TabDragShadowController.onDragEnded()
    }

    @After
    fun tearDown() {
        TabDragShadowController.onDragEnded()
    }

    @Test
    fun tabDragPayload_parse_validFormatRoundTrips() {
        val payload = TabDragPayload(sourceWindowId = "win-1", tabId = "tab-99")
        assertEquals("win-1:tab-99", payload.rawText)
        assertEquals(
            TabDragPayload(sourceWindowId = "win-1", tabId = "tab-99"),
            TabDragPayload.parse(payload.rawText),
        )
    }

    @Test
    fun tabDragPayload_parse_invalidInputs_returnsNull() {
        assertNull(TabDragPayload.parse(null))
        assertNull(TabDragPayload.parse(""))
        assertNull(TabDragPayload.parse("invalid-payload"))
        assertNull(TabDragPayload.parse(":tab-99"))
        assertNull(TabDragPayload.parse("win-1:"))
    }

    @Test
    fun tabDragShadowController_whenCanCreateNewWindow_switchesBetweenMoveAndNewWindowOnEnterExit() {
        TabDragShadowController.startDrag(
            sourceWindowId = "win-1",
            canCreateNewWindow = true,
            draggedTabId = "tab-2",
        )
        assertEquals(TabDragPreviewMode.MoveToWindow, TabDragShadowController.activePreviewMode)
        assertEquals("win-1", TabDragShadowController.activeSourceWindowId)
        assertEquals("tab-2", TabDragShadowController.activeDraggedTabId)

        // Exiting the source window switches preview to NewWindow
        TabDragShadowController.onTargetWindowExited(targetWindowId = "win-1")
        assertEquals(TabDragPreviewMode.NewWindow, TabDragShadowController.activePreviewMode)

        // Entering another window switches preview back to MoveToWindow
        TabDragShadowController.onTargetWindowEntered(targetWindowId = "win-2")
        assertEquals(TabDragPreviewMode.MoveToWindow, TabDragShadowController.activePreviewMode)

        // Exiting the target window back into open space restores NewWindow
        TabDragShadowController.onTargetWindowExited(targetWindowId = "win-2")
        assertEquals(TabDragPreviewMode.NewWindow, TabDragShadowController.activePreviewMode)
    }

    @Test
    fun tabDragShadowController_whenCannotCreateNewWindow_switchesToUnavailableOnExitAndRestoresOnEnter() {
        TabDragShadowController.startDrag(
            sourceWindowId = "win-1",
            canCreateNewWindow = false,
        )
        assertEquals(TabDragPreviewMode.MoveToWindow, TabDragShadowController.activePreviewMode)

        TabDragShadowController.onTargetWindowExited(targetWindowId = "win-1")
        assertEquals(TabDragPreviewMode.Unavailable, TabDragShadowController.activePreviewMode)

        TabDragShadowController.onTargetWindowEntered(targetWindowId = "win-1")
        assertEquals(TabDragPreviewMode.MoveToWindow, TabDragShadowController.activePreviewMode)
    }

    @Test
    fun tabDragShadowController_exitingUnmatchedWindow_doesNotClearCurrentHover() {
        TabDragShadowController.startDrag(
            sourceWindowId = "win-1",
            canCreateNewWindow = true,
        )
        TabDragShadowController.onTargetWindowEntered(targetWindowId = "win-2")
        assertEquals(TabDragPreviewMode.MoveToWindow, TabDragShadowController.activePreviewMode)

        // Spurious exit from win-1 after already entering win-2 should be ignored
        TabDragShadowController.onTargetWindowExited(targetWindowId = "win-1")
        assertEquals(TabDragPreviewMode.MoveToWindow, TabDragShadowController.activePreviewMode)
    }

    @Test
    fun handleContentAreaTabDrop_sameWindowWhenCanPopOut_popsOutTab() {
        val poppedOutTabs = mutableListOf<String>()
        val movedTabs = mutableListOf<Triple<String, String, String>>()

        val handled = handleContentAreaTabDrop(
            payload = TabDragPayload(sourceWindowId = "win-1", tabId = "tab-2"),
            windowId = "win-1",
            canPopOut = true,
            onPopOutTab = { poppedOutTabs.add(it) },
            onMoveTabToWindow = { src, tab, dst -> movedTabs.add(Triple(src, tab, dst)) },
        )

        assertTrue(handled)
        assertEquals(listOf("tab-2"), poppedOutTabs)
        assertTrue(movedTabs.isEmpty())
    }

    @Test
    fun handleContentAreaTabDrop_sameWindowWhenSingleTab_returnsFalseWithoutSideEffects() {
        val poppedOutTabs = mutableListOf<String>()
        val movedTabs = mutableListOf<Triple<String, String, String>>()

        val handled = handleContentAreaTabDrop(
            payload = TabDragPayload(sourceWindowId = "win-1", tabId = "tab-1"),
            windowId = "win-1",
            canPopOut = false,
            onPopOutTab = { poppedOutTabs.add(it) },
            onMoveTabToWindow = { src, tab, dst -> movedTabs.add(Triple(src, tab, dst)) },
        )

        assertFalse(handled)
        assertTrue(poppedOutTabs.isEmpty())
        assertTrue(movedTabs.isEmpty())
    }

    @Test
    fun handleContentAreaTabDrop_crossWindow_movesTabIntoTargetWindow() {
        val poppedOutTabs = mutableListOf<String>()
        val movedTabs = mutableListOf<Triple<String, String, String>>()

        val handled = handleContentAreaTabDrop(
            payload = TabDragPayload(sourceWindowId = "win-1", tabId = "tab-1"),
            windowId = "win-2",
            canPopOut = false,
            onPopOutTab = { poppedOutTabs.add(it) },
            onMoveTabToWindow = { src, tab, dst -> movedTabs.add(Triple(src, tab, dst)) },
        )

        assertTrue(handled)
        assertTrue(poppedOutTabs.isEmpty())
        assertEquals(listOf(Triple("win-1", "tab-1", "win-2")), movedTabs)
    }

    @Test
    fun handleContentAreaTabDrop_nullPayload_returnsFalse() {
        val handled = handleContentAreaTabDrop(
            payload = null,
            windowId = "win-1",
            canPopOut = true,
            onPopOutTab = { error("Should not pop out") },
            onMoveTabToWindow = { _, _, _ -> error("Should not move tab") },
        )

        assertFalse(handled)
    }

    @Test
    fun shouldStartIntentSenderOnUnhandledDrag_requiresDesktopCaptionBarAndApi35() {
        assertTrue(
            TabDragPayload.shouldStartIntentSenderOnUnhandledDrag(
                canCreateNewWindow = true,
                sdkInt = 35,
                isCaptionBarVisible = true,
            ),
        )
        assertFalse(
            TabDragPayload.shouldStartIntentSenderOnUnhandledDrag(
                canCreateNewWindow = true,
                sdkInt = 35,
                isCaptionBarVisible = false,
            ),
        )
        assertFalse(
            TabDragPayload.shouldStartIntentSenderOnUnhandledDrag(
                canCreateNewWindow = false,
                sdkInt = 35,
                isCaptionBarVisible = true,
            ),
        )
        assertFalse(
            TabDragPayload.shouldStartIntentSenderOnUnhandledDrag(
                canCreateNewWindow = true,
                sdkInt = 34,
                isCaptionBarVisible = true,
            ),
        )
    }
}
