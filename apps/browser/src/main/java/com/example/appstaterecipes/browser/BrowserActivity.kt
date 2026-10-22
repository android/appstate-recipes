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

import android.app.Activity
import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Rect
import android.os.Build
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
import com.example.appstaterecipes.browser.ui.BrowserDragOutIntentFactory
import com.example.appstaterecipes.browser.ui.BrowserWindowScreen
import com.example.appstaterecipes.browser.ui.LocalBrowserDragOutIntentFactory
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
            (application as? BrowserApplication)?.isRestored?.await()
            if (currentWindowId == null) {
                val requestedWindowId = savedInstanceState?.getString(KEY_SAVED_WINDOW_ID)
                    ?: resolveWindowIdFromIntent(intent)
                currentWindowId = navigator.openWindow(
                    requestedWindowId = requestedWindowId,
                    taskId = taskId,
                )
            }
        }

        setContent {
            AppStateRecipesTheme {
                val activeWindowId = currentWindowId ?: return@AppStateRecipesTheme
                val context = LocalContext.current
                CompositionLocalProvider(
                    LocalBrowserDragOutIntentFactory provides BrowserDragOutIntentFactory { ctx, sourceWinId, tabId ->
                        val dragOutIntent = createDragOutTabIntent(ctx, sourceWinId, tabId)
                        PendingIntent.getActivity(
                            ctx,
                            tabId.hashCode(),
                            dragOutIntent,
                            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                        )
                    },
                ) {
                    BrowserWindowScreen(
                        windowId = activeWindowId,
                        navigator = navigator,
                        onSwitchWindow = { targetWindowId ->
                            switchToWindow(targetWindowId)
                        },
                        onLaunchNewWindow = { newWindowId ->
                            context.launchBrowserWindow(newWindowId)
                        },
                        onCloseEmptyWindow = {
                            finishAndRemoveTask()
                        },
                        onOpenStateStoreViewer = {
                            startActivity(
                                Intent(
                                    this@BrowserActivity,
                                    StateStoreViewerActivity::class.java,
                                ),
                            )
                        },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        lifecycleScope.launch {
            (application as? BrowserApplication)?.isRestored?.await()
            val requestedWindowId = resolveWindowIdFromIntent(intent)
            if (requestedWindowId != null && requestedWindowId != currentWindowId) {
                switchToWindow(requestedWindowId)
            }
        }
    }

    private fun resolveWindowIdFromIntent(intent: Intent): String? {
        intent.getStringExtra(EXTRA_WINDOW_ID)?.let { return it }
        val dragSourceWindowId = intent.getStringExtra(EXTRA_SOURCE_WINDOW_ID)
        val dragTabId = intent.getStringExtra(EXTRA_DRAG_OUT_TAB_ID)
        if (dragSourceWindowId != null && dragTabId != null) {
            intent.removeExtra(EXTRA_SOURCE_WINDOW_ID)
            intent.removeExtra(EXTRA_DRAG_OUT_TAB_ID)
            return navigator.popOutTabToNewWindow(dragSourceWindowId, dragTabId)
        }
        return null
    }

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
        currentWindowId?.let { windowId ->
            navigator.detachWindowTask(windowId, taskId)
        }
    }

    companion object {
        private const val EXTRA_WINDOW_ID = "extra_window_id"
        private const val EXTRA_SOURCE_WINDOW_ID = "extra_source_window_id"
        private const val EXTRA_DRAG_OUT_TAB_ID = "extra_drag_out_tab_id"
        private const val KEY_SAVED_WINDOW_ID = "key_saved_window_id"

        fun createBrowserWindowIntent(context: Context, windowId: String): Intent {
            return Intent(context, BrowserActivity::class.java).apply {
                putExtra(EXTRA_WINDOW_ID, windowId)
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_DOCUMENT or
                        Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
                        Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT
                )
            }
        }

        fun createDragOutTabIntent(context: Context, sourceWindowId: String, tabId: String): Intent {
            return Intent(context, BrowserActivity::class.java).apply {
                putExtra(EXTRA_SOURCE_WINDOW_ID, sourceWindowId)
                putExtra(EXTRA_DRAG_OUT_TAB_ID, tabId)
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_DOCUMENT or
                        Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
                        Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT
                )
            }
        }

        fun Context.launchBrowserWindow(windowId: String) {
            val intent = createBrowserWindowIntent(this, windowId)
            val options = createMatchingWindowBoundsOptions()
            if (options != null) {
                startActivity(intent, options)
            } else {
                startActivity(intent)
            }
        }

        private fun Context.createMatchingWindowBoundsOptions(): Bundle? {
            val activity = findActivity() ?: return null
            val currentBounds = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Rect(activity.windowManager.currentWindowMetrics.bounds)
            } else {
                val rect = Rect()
                activity.window.decorView.getWindowVisibleDisplayFrame(rect)
                rect
            }
            if (currentBounds.isEmpty) return null

            val maxBounds = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                activity.windowManager.maximumWindowMetrics.bounds
            } else {
                null
            }

            // In freeform / desktop windowing, offset slightly so the new window cascades
            // visibly while keeping the exact same width and height as the current window.
            val launchBounds = Rect(currentBounds)
            if (maxBounds != null && currentBounds != maxBounds) {
                val offsetPx = (24 * resources.displayMetrics.density).toInt()
                val dx = offsetPx.coerceAtMost((maxBounds.right - currentBounds.right).coerceAtLeast(0))
                val dy = offsetPx.coerceAtMost((maxBounds.bottom - currentBounds.bottom).coerceAtLeast(0))
                launchBounds.offset(dx, dy)
            }

            return ActivityOptions.makeBasic()
                .setLaunchBounds(launchBounds)
                .toBundle()
        }

        private tailrec fun Context.findActivity(): Activity? = when (this) {
            is Activity -> this
            is ContextWrapper -> baseContext.findActivity()
            else -> null
        }
    }
}
// [END browser_activity]
