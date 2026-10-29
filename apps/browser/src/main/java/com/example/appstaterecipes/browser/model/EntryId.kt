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

package com.example.appstaterecipes.browser.model

/**
 * Strongly-typed identifier for a back stack entry formatted as `"$tabId:$stackIndex"`.
 */
@JvmInline
value class EntryId(val value: String) {
    val tabId: String
        get() = value.substringBeforeLast(':')

    val stackIndex: Int?
        get() = value.substringAfterLast(':', "").toIntOrNull()

    companion object {
        fun of(tabId: String, stackIndex: Int): EntryId = EntryId("$tabId:$stackIndex")

        fun parse(value: String): EntryId? {
            val separatorIndex = value.lastIndexOf(':')
            if (separatorIndex <= 0) return null
            value.substring(separatorIndex + 1).toIntOrNull() ?: return null
            return EntryId(value)
        }
    }
}
