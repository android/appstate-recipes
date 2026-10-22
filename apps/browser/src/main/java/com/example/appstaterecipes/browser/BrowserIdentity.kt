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

package com.example.appstaterecipes.browser

import android.content.ClipData
import android.content.IntentSender
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.toAndroidDragEvent

/**
 * Strongly-typed identifier for a back stack entry formatted as `"$tabId:$stackIndex"`.
 */
@JvmInline
value class EntryId(val value: String) {
    val tabId: String
        get() = value.substringBeforeLast(':')

    val stackIndex: Int?
        get() = value.substringAfterLast(':', "").toIntOrNull()

    val isValid: Boolean
        get() = stackIndex != null && tabId.isNotEmpty()

    companion object {
        fun of(tabId: String, stackIndex: Int): EntryId = EntryId("$tabId:$stackIndex")

        fun parse(value: String): EntryId? {
            val separatorIndex = value.lastIndexOf(':')
            if (separatorIndex <= 0) return null
            val stackIndex = value.substring(separatorIndex + 1).toIntOrNull() ?: return null
            return EntryId(value)
        }
    }
}

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
    }
}
