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

import android.content.ClipData
import android.content.IntentSender
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.toAndroidDragEvent

/**
 * Structured payload transferred during drag-and-drop of a browser tab.
 */
data class TabDragPayload(
    val sourceWindowId: String,
    val tabId: String,
) {
    val rawText: String
        get() = "$sourceWindowId:$tabId"

    fun toClipData(intentSender: IntentSender? = null): ClipData {
        val clipItem = if (intentSender != null && android.os.Build.VERSION.SDK_INT >= 35) {
            ClipData.Item.Builder()
                .setText(rawText)
                .setIntentSender(intentSender)
                .build()
        } else {
            ClipData.Item(rawText)
        }
        return ClipData("Browser Tab", arrayOf(MIME_TYPE), clipItem)
    }

    companion object {
        const val MIME_TYPE = "application/vnd.example.browsertab"

        fun parse(rawText: String?): TabDragPayload? {
            if (rawText == null) return null
            val separatorIndex = rawText.indexOf(':')
            if (separatorIndex <= 0 || separatorIndex == rawText.lastIndex) return null
            val sourceWindowId = rawText.substring(0, separatorIndex)
            val tabId = rawText.substring(separatorIndex + 1)
            return TabDragPayload(sourceWindowId = sourceWindowId, tabId = tabId)
        }

        fun fromDragEvent(event: DragAndDropEvent): TabDragPayload? {
            val clipData = event.toAndroidDragEvent().clipData ?: return null
            if (clipData.itemCount == 0) return null
            val rawText = clipData.getItemAt(0).text?.toString() ?: return null
            return parse(rawText)
        }

        /**
         * Returns `true` only when dragging a tab can delegate an unhandled drop outside all app
         * windows to the system via `DRAG_FLAG_START_INTENT_SENDER_ON_UNHANDLED_DRAG`.
         *
         * Gated to desktop windowing (`isCaptionBarVisible == true`) because on full-screen and
         * split-screen phones/tablets without a desktop caption bar, attaching an Activity
         * [IntentSender] activates System UI's `ShellDropTarget` split-screen drop zone across the
         * top of the display, directly overlapping `BrowserTabBar` and stealing tab drags. In
         * non-desktop modes, dropping onto the window's content area already pops out a new window
         * via `BrowserWindowScreen`, and `DRAG_FLAG_GLOBAL_SAME_APPLICATION` handles cross-window
         * tab moves.
         */
        internal fun shouldStartIntentSenderOnUnhandledDrag(
            canCreateNewWindow: Boolean,
            sdkInt: Int,
            isCaptionBarVisible: Boolean,
        ): Boolean = canCreateNewWindow && sdkInt >= 35 && isCaptionBarVisible
    }
}
