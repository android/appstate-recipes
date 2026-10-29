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

import android.app.PendingIntent
import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Interface that provides platform-level drag-out pending intents to UI components
 * without coupling the UI layer directly to `BrowserActivity`.
 */
fun interface BrowserDragOutIntentFactory {
    fun createDragOutPendingIntent(
        context: Context,
        sourceWindowId: String,
        tabId: String,
    ): PendingIntent?
}

/**
 * CompositionLocal providing [BrowserDragOutIntentFactory] to UI components.
 */
val LocalBrowserDragOutIntentFactory = staticCompositionLocalOf<BrowserDragOutIntentFactory> {
    BrowserDragOutIntentFactory { _, _, _ -> null }
}
