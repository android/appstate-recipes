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

package com.example.appstaterecipes.browser.window

import android.app.Activity
import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Point
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.view.WindowInsets
import androidx.window.core.layout.WindowSizeClass
import androidx.window.core.layout.computeWindowSizeClass
import com.example.appstaterecipes.browser.BrowserActivity
import com.example.appstaterecipes.browser.state.BrowserNavigator

/**
 * Platform helper for launching multi-instance [BrowserActivity] tasks, resolving target window IDs
 * from incoming [Intent] extras, and cascading freeform/desktop window bounds.
 */
object BrowserWindowLauncher {
    private const val EXTRA_WINDOW_ID = "extra_window_id"
    private const val EXTRA_DRAG_OUT_SOURCE_WINDOW_ID = "extra_source_window_id"
    private const val EXTRA_DRAG_OUT_TAB_ID = "extra_drag_out_tab_id"

    /**
     * Resolves an explicit target `windowId` from [intent], or pops out a dragged tab into a new
     * window if [intent] carries drag-out extras.
     */
    fun resolveWindowIdFromIntent(
        intent: Intent,
        navigator: BrowserNavigator,
    ): String? {
        intent.getStringExtra(EXTRA_WINDOW_ID)?.let { return it }
        val dragOutSourceWindowId = intent.getStringExtra(EXTRA_DRAG_OUT_SOURCE_WINDOW_ID)
        val dragOutTabId = intent.getStringExtra(EXTRA_DRAG_OUT_TAB_ID)
        if (dragOutSourceWindowId != null && dragOutTabId != null) {
            intent.removeExtra(EXTRA_DRAG_OUT_SOURCE_WINDOW_ID)
            intent.removeExtra(EXTRA_DRAG_OUT_TAB_ID)
            return navigator.popOutTabToNewWindow(dragOutSourceWindowId, dragOutTabId)
        }
        return null
    }

    private fun createBrowserWindowIntent(
        context: Context,
        windowId: String,
    ): Intent {
        return Intent(context, BrowserActivity::class.java).apply {
            putExtra(EXTRA_WINDOW_ID, windowId)
            addFlags(newDocumentLaunchFlags(supportsSplitLaunch(context)))
        }
    }

    private fun createDragOutTabIntent(context: Context, sourceWindowId: String, tabId: String): Intent {
        return Intent(context, BrowserActivity::class.java).apply {
            putExtra(EXTRA_DRAG_OUT_SOURCE_WINDOW_ID, sourceWindowId)
            putExtra(EXTRA_DRAG_OUT_TAB_ID, tabId)
            addFlags(newDocumentLaunchFlags(supportsSplitLaunch(context)))
        }
    }

    fun createDragOutPendingIntent(
        context: Context,
        sourceWindowId: String,
        tabId: String,
    ): PendingIntent {
        val dragOutIntent = createDragOutTabIntent(context, sourceWindowId, tabId)
        return PendingIntent.getActivity(
            context,
            tabId.hashCode(),
            dragOutIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    fun launchBrowserWindow(context: Context, windowId: String) {
        val intent = createBrowserWindowIntent(context, windowId)
        val options = context.createFreeformWindowBoundsOptions()
        if (options != null) {
            context.startActivity(intent, options)
        } else {
            context.startActivity(intent)
        }
    }

    /**
     * Returns `true` when the active display is wide enough ([WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND],
     * `>= 840dp`)—or in desktop windowing—to support dragging a tab out into an adjacent split or
     * freeform window. Evaluated against `maximumWindowMetrics` so already-split panes on large
     * screens still qualify, while smaller displays (such as phones and portrait tablets) disable
     * drag-to-split and open explicit new windows as separate tasks instead.
     */
    internal fun supportsSplitLaunch(context: Context): Boolean {
        val activity = context.findActivity() ?: return false
        val isCaptionBarVisible = Build.VERSION.SDK_INT >= 35 &&
            activity.window?.decorView?.rootWindowInsets?.isVisible(
                WindowInsets.Type.captionBar(),
            ) == true
        val maxBounds = activity.maxWindowBounds()
        val density = context.resources.displayMetrics.density
        if (maxBounds == null || maxBounds.isEmpty || density <= 0f) {
            return isCaptionBarVisible
        }
        return supportsSplitLaunch(
            displayWidthDp = maxBounds.width() / density,
            displayHeightDp = maxBounds.height() / density,
            isCaptionBarVisible = isCaptionBarVisible,
        )
    }

    internal fun supportsSplitLaunch(
        displayWidthDp: Float,
        displayHeightDp: Float,
        isCaptionBarVisible: Boolean = false,
    ): Boolean {
        if (isCaptionBarVisible) return true
        if (displayWidthDp <= 0f || displayHeightDp <= 0f) return false
        val displaySizeClass = WindowSizeClass.BREAKPOINTS_V1.computeWindowSizeClass(
            widthDp = displayWidthDp,
            heightDp = displayHeightDp,
        )
        return displaySizeClass.isWidthAtLeastBreakpoint(
            WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND,
        )
    }

    private fun newDocumentLaunchFlags(launchAdjacent: Boolean): Int {
        val baseFlags = Intent.FLAG_ACTIVITY_NEW_DOCUMENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK
        return if (launchAdjacent) {
            baseFlags or Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT
        } else {
            baseFlags
        }
    }

    private fun Activity.currentWindowBounds(): Rect {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Rect(windowManager.currentWindowMetrics.bounds)
        } else {
            val rect = Rect()
            window.decorView.getWindowVisibleDisplayFrame(rect)
            rect
        }
    }

    private fun Activity.maxWindowBounds(): Rect? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Rect(windowManager.maximumWindowMetrics.bounds)
        } else {
            val realSize = Point()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay?.getRealSize(realSize) ?: return null
            if (realSize.x <= 0 || realSize.y <= 0) null else Rect(0, 0, realSize.x, realSize.y)
        }
    }

    private fun Context.createFreeformWindowBoundsOptions(): Bundle? {
        val activity = findActivity() ?: return null
        if (!activity.isInMultiWindowMode) return null

        val currentBounds = activity.currentWindowBounds()
        if (currentBounds.isEmpty) return null
        val maxBounds = activity.maxWindowBounds()

        val isCaptionBarVisible = Build.VERSION.SDK_INT >= 35 &&
            activity.window?.decorView?.rootWindowInsets?.isVisible(
                WindowInsets.Type.captionBar(),
            ) == true
        val isFloatingFreeform = maxBounds != null &&
            !maxBounds.isEmpty &&
            currentBounds.width() < maxBounds.width() &&
            currentBounds.height() < maxBounds.height()
        if (!isCaptionBarVisible && !isFloatingFreeform) return null

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
