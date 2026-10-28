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

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.lifecycleScope
import com.example.appstaterecipes.StateStoreOwner
import com.example.appstaterecipes.browser.state.BrowserNavigator
import com.example.appstaterecipes.browser.ui.BrowserWindowScreen
import com.example.appstaterecipes.browser.ui.dragdrop.BrowserDragOutIntentFactory
import com.example.appstaterecipes.browser.ui.dragdrop.LocalBrowserDragOutIntentFactory
import com.example.appstaterecipes.browser.window.BrowserWindowLauncher
import com.example.appstaterecipes.statestoreviewer.StateStoreViewerActivity
import com.example.appstaterecipes.ui.theme.AppStateRecipesTheme
import kotlinx.coroutines.launch

// [START browser_activity]
class BrowserActivity : ComponentActivity() {

    private val navigator: BrowserNavigator by lazy {
        BrowserNavigator((application as StateStoreOwner).stateStore)
    }

    private var currentWindowId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        lifecycleScope.launch {
            // [START_EXCLUDE]
            (application as? BrowserApplication)?.initialRestoreComplete?.await()
            // [END_EXCLUDE]
            if (currentWindowId == null) {
                val requestedWindowId = savedInstanceState?.getString(KEY_SAVED_WINDOW_ID)
                    ?: BrowserWindowLauncher.resolveWindowIdFromIntent(intent, navigator)
                currentWindowId = navigator.openWindow(
                    requestedWindowId = requestedWindowId,
                    taskId = taskId,
                )
            }
        }

        setContent {
            // [START_EXCLUDE]
            CompositionLocalProvider(
                LocalBrowserDragOutIntentFactory provides BrowserDragOutIntentFactory(
                    BrowserWindowLauncher::createDragOutPendingIntent,
                ),
            ) {
            // [END_EXCLUDE]
            AppStateRecipesTheme {
                val windowId = currentWindowId ?: return@AppStateRecipesTheme
                val context = LocalContext.current
                BrowserWindowScreen(
                    windowId = windowId,
                    navigator = navigator,
                    onSwitchWindow = { targetWindowId ->
                        switchToWindow(targetWindowId)
                    },
                    onLaunchNewWindow = { newWindowId ->
                        BrowserWindowLauncher.launchBrowserWindow(context, newWindowId)
                    },
                    onCloseEmptyWindow = {
                        finishAndRemoveTask()
                    },
                    // [START_EXCLUDE]
                    onOpenStateStoreViewer = {
                        startActivity(
                            Intent(
                                this@BrowserActivity,
                                StateStoreViewerActivity::class.java,
                            ),
                        )
                    },
                    // [END_EXCLUDE]
                )
            }
            // [START_EXCLUDE]
            }
            // [END_EXCLUDE]
        }
    }

    // [START_EXCLUDE]
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        lifecycleScope.launch {
            (application as? BrowserApplication)?.initialRestoreComplete?.await()
            val requestedWindowId = BrowserWindowLauncher.resolveWindowIdFromIntent(intent, navigator)
            if (requestedWindowId != null && requestedWindowId != currentWindowId) {
                switchToWindow(requestedWindowId)
            }
        }
    }
    // [END_EXCLUDE]

    private fun switchToWindow(targetWindowId: String) {
        val previousWindowId = currentWindowId
        if (previousWindowId == targetWindowId) return

        navigator.openWindow(requestedWindowId = targetWindowId, taskId = taskId)
        currentWindowId = targetWindowId
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        currentWindowId?.let { outState.putString(KEY_SAVED_WINDOW_ID, it) }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Detach this Activity's task so the window and its tabs remain preserved in StateStore
        // (and DataStore) and can be reattached when re-entering BrowserActivity or via the
        // window switcher menu. Explicit window deletion happens via closeWindow / closeTab.
        if (!isChangingConfigurations) {
            currentWindowId?.let { windowId ->
                navigator.detachWindowTask(windowId, taskId)
            }
        }
    }

    companion object {
        private const val KEY_SAVED_WINDOW_ID = "key_saved_window_id"
    }
}
// [END browser_activity]
