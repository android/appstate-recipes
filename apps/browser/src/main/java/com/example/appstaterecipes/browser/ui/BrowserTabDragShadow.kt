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

import android.graphics.Point
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.appstaterecipes.browser.BrowserRoute
import com.example.appstaterecipes.browser.TabSummary
import com.example.appstaterecipes.ui.theme.AppStateRecipesTheme
import kotlin.math.roundToInt

/**
 * Returns the corresponding Material icon for this [BrowserRoute].
 */
internal val BrowserRoute.icon: ImageVector
    get() = when (this) {
        is BrowserRoute.Home -> Icons.Rounded.Home
        is BrowserRoute.Search -> Icons.Rounded.Search
        is BrowserRoute.Article -> Icons.Rounded.Description
    }

/**
 * Visual mode for the system drag shadow while dragging a browser tab.
 */
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
}

/**
 * Coordinates cross-window drag shadow updates between the source `BrowserActivity` window
 * (which owns the system drag token and [View.updateDragShadow]) and any target [BrowserTabBar]
 * that the pointer enters or exits during an active drag.
 */
internal object TabDragShadowController {
    private class ActiveDragSession(
        val sourceWindowId: String,
        val draggedTabId: String?,
        val canCreateNewWindow: Boolean,
        val onPreviewModeChanged: (TabDragPreviewMode) -> Unit,
        var hoveredTargetWindowId: String? = null,
    )

    private var activeSession by mutableStateOf<ActiveDragSession?>(null)

    val activeSourceWindowId: String?
        get() = activeSession?.sourceWindowId

    val activeDraggedTabId: String?
        get() = activeSession?.draggedTabId

    fun startDrag(
        sourceWindowId: String,
        canCreateNewWindow: Boolean,
        onPreviewModeChanged: (TabDragPreviewMode) -> Unit,
        draggedTabId: String? = null,
    ): TabDragPreviewMode {
        // A drag starts from within the source window's tab bar, so the initial preview
        // is always MoveToWindow (the tab chip). If the pointer moves out of any window,
        // onTargetWindowExited will transition to NewWindow (if canCreateNewWindow is true).
        val initialMode = TabDragPreviewMode.MoveToWindow
        activeSession = ActiveDragSession(
            sourceWindowId = sourceWindowId,
            draggedTabId = draggedTabId,
            canCreateNewWindow = canCreateNewWindow,
            onPreviewModeChanged = onPreviewModeChanged,
            hoveredTargetWindowId = sourceWindowId,
        )
        return initialMode
    }

    fun onTargetWindowEntered(targetWindowId: String) {
        val session = activeSession ?: return
        session.hoveredTargetWindowId = targetWindowId
        session.onPreviewModeChanged(TabDragPreviewMode.MoveToWindow)
    }

    fun onTargetWindowExited(targetWindowId: String) {
        val session = activeSession ?: return
        if (session.hoveredTargetWindowId == targetWindowId) {
            session.hoveredTargetWindowId = null
            session.onPreviewModeChanged(defaultMode(session.canCreateNewWindow))
        }
    }

    fun onDragEnded() {
        activeSession = null
    }

    private fun defaultMode(canCreateNewWindow: Boolean): TabDragPreviewMode =
        if (canCreateNewWindow) {
            TabDragPreviewMode.NewWindow
        } else {
            TabDragPreviewMode.MoveToWindow
        }
}

internal val DragPreviewCanvasWidth = 196.dp
internal val DragPreviewCanvasHeight = 118.dp
internal val DragPreviewTouchOffsetY = 40.dp

internal data class TabDragPreviewStyle(
    val primary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
    val surface: Color,
    val onSurface: Color,
    val surfaceContainer: Color,
    val surfaceContainerHigh: Color,
    val surfaceContainerHighest: Color,
    val surfaceVariant: Color,
    val outlineVariant: Color,
    val badgeTextStyle: TextStyle,
    val tabTitleTextStyle: TextStyle,
)

@Composable
internal fun rememberTabDragPreviewStyle(): TabDragPreviewStyle {
    val colorScheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    return remember(colorScheme, typography) {
        TabDragPreviewStyle(
            primary = colorScheme.primary,
            primaryContainer = colorScheme.primaryContainer,
            onPrimaryContainer = colorScheme.onPrimaryContainer,
            secondaryContainer = colorScheme.secondaryContainer,
            onSecondaryContainer = colorScheme.onSecondaryContainer,
            surface = colorScheme.surface,
            onSurface = colorScheme.onSurface,
            surfaceContainer = colorScheme.surfaceContainer,
            surfaceContainerHigh = colorScheme.surfaceContainerHigh,
            surfaceContainerHighest = colorScheme.surfaceContainerHighest,
            surfaceVariant = colorScheme.surfaceVariant,
            outlineVariant = colorScheme.outlineVariant,
            badgeTextStyle = typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            tabTitleTextStyle = typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
        )
    }
}

/**
 * Custom [View.DragShadowBuilder] that renders either:
 * - [TabDragPreviewMode.NewWindow]: A miniature browser window card with a "New window" title bar,
 *   mini tab bar, and page content wireframe when dropping will tear the tab into a new window.
 * - [TabDragPreviewMode.MoveToWindow]: A compact floating tab chip when dropping will merge the tab
 *   into an existing window.
 *
 * Because [View.updateDragShadow] reuses the [android.view.Surface] buffer allocated during
 * [View.startDragAndDrop], [onProvideShadowMetrics] allocates a translucent surface sized for the
 * miniature window preview and anchors the touch point on the tab chip in both modes.
 */
internal class TabDragShadowBuilder(
    private val density: Density,
    private val layoutDirection: LayoutDirection,
    private val tab: TabSummary,
    private val tabIconPainter: Painter,
    private val openInNewPainter: Painter,
    private val textMeasurer: TextMeasurer,
    private val style: TabDragPreviewStyle,
    initialMode: TabDragPreviewMode,
) : View.DragShadowBuilder() {

    var mode: TabDragPreviewMode = initialMode
    private val drawScope = CanvasDrawScope()

    override fun onProvideShadowMetrics(outShadowSize: Point, outShadowTouchPoint: Point) {
        with(density) {
            val widthPx = DragPreviewCanvasWidth.roundToPx()
            val heightPx = DragPreviewCanvasHeight.roundToPx()
            outShadowSize.set(widthPx, heightPx)
            outShadowTouchPoint.set(widthPx / 2, DragPreviewTouchOffsetY.roundToPx())
        }
    }

    override fun onDrawShadow(canvas: android.graphics.Canvas) {
        val canvasSize = with(density) {
            Size(DragPreviewCanvasWidth.toPx(), DragPreviewCanvasHeight.toPx())
        }
        drawScope.draw(
            density = density,
            layoutDirection = layoutDirection,
            canvas = androidx.compose.ui.graphics.Canvas(canvas),
            size = canvasSize,
        ) {
            drawTabDragPreview(
                tab = tab,
                mode = mode,
                tabIconPainter = tabIconPainter,
                openInNewPainter = openInNewPainter,
                textMeasurer = textMeasurer,
                style = style,
            )
        }
    }
}

internal fun DrawScope.drawTabDragPreview(
    tab: TabSummary,
    mode: TabDragPreviewMode,
    tabIconPainter: Painter,
    openInNewPainter: Painter,
    textMeasurer: TextMeasurer,
    style: TabDragPreviewStyle,
) {
    when (mode) {
        is TabDragPreviewMode.NewWindow -> drawNewWindowDragPreview(
            tab = tab,
            tabIconPainter = tabIconPainter,
            openInNewPainter = openInNewPainter,
            textMeasurer = textMeasurer,
            style = style,
        )
        is TabDragPreviewMode.MoveToWindow -> drawMoveToWindowDragPreview(
            tab = tab,
            tabIconPainter = tabIconPainter,
            textMeasurer = textMeasurer,
            style = style,
        )
    }
}

private fun DrawScope.drawNewWindowDragPreview(
    tab: TabSummary,
    tabIconPainter: Painter,
    openInNewPainter: Painter,
    textMeasurer: TextMeasurer,
    style: TabDragPreviewStyle,
) {
    val pad = 6.dp.toPx()
    val windowWidth = size.width - pad * 2
    val windowHeight = size.height - pad * 2
    val cornerRadius = 14.dp.toPx()

    drawRoundRect(
        color = Color.Black.copy(alpha = 0.18f),
        topLeft = Offset(pad, pad + 3.dp.toPx()),
        size = Size(windowWidth, windowHeight),
        cornerRadius = CornerRadius(cornerRadius, cornerRadius),
    )

    val windowRect = Rect(pad, pad, pad + windowWidth, pad + windowHeight)
    val windowPath = Path().apply {
        addRoundRect(RoundRect(windowRect, CornerRadius(cornerRadius, cornerRadius)))
    }

    drawPath(
        path = windowPath,
        color = style.surface,
    )

    val headerHeight = 22.dp.toPx()
    val headerPath = Path().apply {
        addRoundRect(
            RoundRect(
                rect = Rect(pad, pad, pad + windowWidth, pad + headerHeight),
                topLeft = CornerRadius(cornerRadius, cornerRadius),
                topRight = CornerRadius(cornerRadius, cornerRadius),
                bottomLeft = CornerRadius.Zero,
                bottomRight = CornerRadius.Zero,
            )
        )
    }
    drawPath(path = headerPath, color = style.primaryContainer)

    val headerIconSize = 12.dp.toPx()
    val headerIconX = pad + 10.dp.toPx()
    val headerIconY = pad + (headerHeight - headerIconSize) / 2f
    translate(left = headerIconX, top = headerIconY) {
        with(openInNewPainter) {
            draw(
                size = Size(headerIconSize, headerIconSize),
                colorFilter = ColorFilter.tint(style.onPrimaryContainer),
            )
        }
    }

    val headerLabelLayout = textMeasurer.measure(
        text = "New window",
        style = style.badgeTextStyle.copy(color = style.onPrimaryContainer),
        maxLines = 1,
    )
    drawText(
        textLayoutResult = headerLabelLayout,
        topLeft = Offset(
            x = headerIconX + headerIconSize + 5.dp.toPx(),
            y = pad + (headerHeight - headerLabelLayout.size.height) / 2f,
        ),
    )

    val dotRadius = 2.5.dp.toPx()
    val dotCenterY = pad + headerHeight / 2f
    for (i in 0..2) {
        drawCircle(
            color = style.onPrimaryContainer.copy(alpha = 0.45f),
            radius = dotRadius,
            center = Offset(
                x = pad + windowWidth - 12.dp.toPx() - i * 8.dp.toPx(),
                y = dotCenterY,
            ),
        )
    }

    val tabStripTop = pad + headerHeight
    val tabStripHeight = 26.dp.toPx()
    drawRect(
        color = style.surfaceContainerHigh,
        topLeft = Offset(pad, tabStripTop),
        size = Size(windowWidth, tabStripHeight),
    )

    val miniTabLeft = pad + 8.dp.toPx()
    val miniTabTop = tabStripTop + 3.dp.toPx()
    val miniTabWidth = windowWidth - 34.dp.toPx()
    val miniTabHeight = tabStripHeight - 3.dp.toPx()
    val miniTabPath = Path().apply {
        addRoundRect(
            RoundRect(
                rect = Rect(miniTabLeft, miniTabTop, miniTabLeft + miniTabWidth, miniTabTop + miniTabHeight),
                topLeft = CornerRadius(7.dp.toPx(), 7.dp.toPx()),
                topRight = CornerRadius(7.dp.toPx(), 7.dp.toPx()),
                bottomLeft = CornerRadius.Zero,
                bottomRight = CornerRadius.Zero,
            )
        )
    }
    drawPath(path = miniTabPath, color = style.surface)

    drawTabRowContents(
        tab = tab,
        left = miniTabLeft + 8.dp.toPx(),
        top = miniTabTop,
        width = miniTabWidth - 16.dp.toPx(),
        height = miniTabHeight,
        tabIconPainter = tabIconPainter,
        textMeasurer = textMeasurer,
        style = style,
    )

    val bodyTop = tabStripTop + tabStripHeight + 8.dp.toPx()
    val bodyLeft = pad + 12.dp.toPx()
    val bodyInnerWidth = windowWidth - 24.dp.toPx()

    drawRoundRect(
        color = style.surfaceContainerHighest,
        topLeft = Offset(bodyLeft, bodyTop),
        size = Size(bodyInnerWidth, 12.dp.toPx()),
        cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
    )

    val line1Top = bodyTop + 18.dp.toPx()
    drawRoundRect(
        color = style.surfaceVariant,
        topLeft = Offset(bodyLeft, line1Top),
        size = Size(bodyInnerWidth * 0.82f, 7.dp.toPx()),
        cornerRadius = CornerRadius(3.5.dp.toPx(), 3.5.dp.toPx()),
    )
    val line2Top = line1Top + 12.dp.toPx()
    drawRoundRect(
        color = style.surfaceVariant.copy(alpha = 0.7f),
        topLeft = Offset(bodyLeft, line2Top),
        size = Size(bodyInnerWidth * 0.56f, 7.dp.toPx()),
        cornerRadius = CornerRadius(3.5.dp.toPx(), 3.5.dp.toPx()),
    )

    drawPath(
        path = windowPath,
        color = style.primary,
        style = Stroke(width = 1.5.dp.toPx()),
    )
}

private fun DrawScope.drawMoveToWindowDragPreview(
    tab: TabSummary,
    tabIconPainter: Painter,
    textMeasurer: TextMeasurer,
    style: TabDragPreviewStyle,
) {
    val minChipWidth = 84.dp.toPx()
    val maxChipWidth = 124.dp.toPx()
    val horizontalPadding = 20.dp.toPx()
    val iconAndGapWidth = 14.dp.toPx() + 6.dp.toPx()
    val badgeAndGapWidth = if (tab.stackDepth > 1) {
        val depthLayout = textMeasurer.measure(
            text = tab.stackDepth.toString(),
            style = style.badgeTextStyle,
            maxLines = 1,
        )
        (depthLayout.size.width + 10.dp.toPx()).coerceAtLeast(16.dp.toPx()) + 6.dp.toPx()
    } else {
        0f
    }
    val unwrappedTitleWidth = textMeasurer.measure(
        text = tab.title,
        style = style.tabTitleTextStyle,
        maxLines = 1,
    ).size.width
    val chipWidth = (horizontalPadding + iconAndGapWidth + unwrappedTitleWidth + badgeAndGapWidth)
        .coerceIn(minChipWidth, maxChipWidth)
    val chipLeft = (size.width - chipWidth) / 2f
    val chipHeight = 32.dp.toPx()
    val chipTop = DragPreviewTouchOffsetY.toPx() - chipHeight / 2f
    val chipCornerRadius = 10.dp.toPx()

    drawRoundRect(
        color = Color.Black.copy(alpha = 0.16f),
        topLeft = Offset(chipLeft, chipTop + 2.dp.toPx()),
        size = Size(chipWidth, chipHeight),
        cornerRadius = CornerRadius(chipCornerRadius, chipCornerRadius),
    )

    drawRoundRect(
        color = style.surface,
        topLeft = Offset(chipLeft, chipTop),
        size = Size(chipWidth, chipHeight),
        cornerRadius = CornerRadius(chipCornerRadius, chipCornerRadius),
    )
    drawRoundRect(
        color = style.primary,
        topLeft = Offset(chipLeft, chipTop),
        size = Size(chipWidth, chipHeight),
        cornerRadius = CornerRadius(chipCornerRadius, chipCornerRadius),
        style = Stroke(width = 1.5.dp.toPx()),
    )

    drawTabRowContents(
        tab = tab,
        left = chipLeft + 10.dp.toPx(),
        top = chipTop,
        width = chipWidth - 20.dp.toPx(),
        height = chipHeight,
        tabIconPainter = tabIconPainter,
        textMeasurer = textMeasurer,
        style = style,
    )
}

private fun DrawScope.drawTabRowContents(
    tab: TabSummary,
    left: Float,
    top: Float,
    width: Float,
    height: Float,
    tabIconPainter: Painter,
    textMeasurer: TextMeasurer,
    style: TabDragPreviewStyle,
) {
    val iconSize = 14.dp.toPx()
    val iconTop = top + (height - iconSize) / 2f
    translate(left = left, top = iconTop) {
        with(tabIconPainter) {
            draw(
                size = Size(iconSize, iconSize),
                colorFilter = ColorFilter.tint(style.primary),
            )
        }
    }

    var availableRight = left + width
    if (tab.stackDepth > 1) {
        val depthLayout = textMeasurer.measure(
            text = tab.stackDepth.toString(),
            style = style.badgeTextStyle.copy(color = style.onPrimaryContainer),
            maxLines = 1,
        )
        val pillWidth = (depthLayout.size.width + 10.dp.toPx()).coerceAtLeast(16.dp.toPx())
        val pillHeight = 15.dp.toPx()
        val pillLeft = availableRight - pillWidth
        val pillTop = top + (height - pillHeight) / 2f
        drawRoundRect(
            color = style.primaryContainer,
            topLeft = Offset(pillLeft, pillTop),
            size = Size(pillWidth, pillHeight),
            cornerRadius = CornerRadius(pillHeight / 2f, pillHeight / 2f),
        )
        drawText(
            textLayoutResult = depthLayout,
            topLeft = Offset(
                x = pillLeft + (pillWidth - depthLayout.size.width) / 2f,
                y = pillTop + (pillHeight - depthLayout.size.height) / 2f,
            ),
        )
        availableRight = pillLeft - 6.dp.toPx()
    }

    val titleLeft = left + iconSize + 6.dp.toPx()
    val maxTitleWidth = (availableRight - titleLeft).roundToInt().coerceAtLeast(20)
    val titleLayout = textMeasurer.measure(
        text = tab.title,
        style = style.tabTitleTextStyle.copy(color = style.onSurface),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        constraints = Constraints(maxWidth = maxTitleWidth),
    )
    drawText(
        textLayoutResult = titleLayout,
        topLeft = Offset(
            x = titleLeft,
            y = top + (height - titleLayout.size.height) / 2f,
        ),
    )
}

@Composable
internal fun BrowserTabDragPreviewCard(
    tab: TabSummary,
    mode: TabDragPreviewMode,
    modifier: Modifier = Modifier,
) {
    val tabIconPainter = rememberVectorPainter(tab.currentRoute.icon)
    val openInNewPainter = rememberVectorPainter(Icons.AutoMirrored.Rounded.OpenInNew)
    val textMeasurer = rememberTextMeasurer()
    val style = rememberTabDragPreviewStyle()

    Spacer(
        modifier = modifier
            .size(DragPreviewCanvasWidth, DragPreviewCanvasHeight)
            .drawWithCache {
                onDrawBehind {
                    drawTabDragPreview(
                        tab = tab,
                        mode = mode,
                        tabIconPainter = tabIconPainter,
                        openInNewPainter = openInNewPainter,
                        textMeasurer = textMeasurer,
                        style = style,
                    )
                }
            },
    )
}

@Preview(showBackground = true, name = "Drag Preview - Pop Out to New Window")
@Composable
private fun BrowserTabDragPreviewNewWindowPreview() {
    AppStateRecipesTheme {
        Box(modifier = Modifier.padding(12.dp)) {
            BrowserTabDragPreviewCard(
                tab = TabSummary(
                    tabId = "tab-2",
                    isSelected = true,
                    stackDepth = 3,
                    currentRoute = BrowserRoute.Article(42, "Article #42"),
                ),
                mode = TabDragPreviewMode.NewWindow,
            )
        }
    }
}

@Preview(showBackground = true, name = "Drag Preview - Move to Another Window")
@Composable
private fun BrowserTabDragPreviewMoveToWindowPreview() {
    AppStateRecipesTheme {
        Box(modifier = Modifier.padding(12.dp)) {
            BrowserTabDragPreviewCard(
                tab = TabSummary(
                    tabId = "tab-2",
                    isSelected = true,
                    stackDepth = 3,
                    currentRoute = BrowserRoute.Article(42, "Article #42"),
                ),
                mode = TabDragPreviewMode.MoveToWindow,
            )
        }
    }
}
