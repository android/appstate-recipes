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

import android.graphics.Picture
import android.graphics.Point
import android.view.View
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.draw
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.util.lerp
import com.example.appstaterecipes.browser.model.BrowserRoute
import com.example.appstaterecipes.browser.state.TabSummary
import com.example.appstaterecipes.browser.ui.icon
import com.example.appstaterecipes.ui.theme.AppStateRecipesTheme
import kotlin.math.roundToInt

internal val DragPreviewCanvasWidth = 196.dp
internal val DragPreviewCanvasHeight = 118.dp
internal val DragPreviewTouchOffsetY = 40.dp

private val MiniTabShape = RoundedCornerShape(
    topStart = 7.dp,
    topEnd = 7.dp,
    bottomStart = 0.dp,
    bottomEnd = 0.dp,
)

/**
 * Custom [View.DragShadowBuilder] that replays an offscreen-recorded [Picture] of
 * [BrowserTabDragPreviewCard], allowing the drag shadow to be authored in Compose and animated
 * smoothly between [TabDragPreviewMode.MoveToWindow] and [TabDragPreviewMode.NewWindow] via
 * [View.updateDragShadow].
 *
 * Because [View.updateDragShadow] reuses the [android.view.Surface] buffer allocated during
 * [View.startDragAndDrop], [onProvideShadowMetrics] allocates a translucent surface sized for the
 * miniature window preview and anchors the touch point on the tab chip in both modes.
 */
internal class TabDragShadowBuilder(
    private val density: Density,
    private val picture: Picture,
) : View.DragShadowBuilder() {

    override fun onProvideShadowMetrics(outShadowSize: Point, outShadowTouchPoint: Point) {
        with(density) {
            val widthPx = DragPreviewCanvasWidth.roundToPx()
            val heightPx = DragPreviewCanvasHeight.roundToPx()
            outShadowSize.set(widthPx, heightPx)
            outShadowTouchPoint.set(widthPx / 2, DragPreviewTouchOffsetY.roundToPx())
        }
    }

    override fun onDrawShadow(canvas: android.graphics.Canvas) {
        if (picture.width > 0 && picture.height > 0) {
            canvas.drawPicture(picture)
        }
    }
}

/**
 * Zero-size offscreen layout slot that composes [BrowserTabDragPreviewCard] and records its drawing
 * commands into [picture] without rendering anything to the screen canvas.
 *
 * [onPictureRecorded] is invoked immediately after each [Picture] recording finishes so an active
 * drag session can push the latest animation frame via [View.updateDragShadow].
 */
@Composable
internal fun OffscreenTabDragShadowCapture(
    tab: TabSummary,
    mode: TabDragPreviewMode,
    picture: Picture,
    modifier: Modifier = Modifier,
    onPictureRecorded: () -> Unit = {},
) {
    val currentOnPictureRecorded by rememberUpdatedState(onPictureRecorded)
    Layout(
        modifier = modifier.clearAndSetSemantics {},
        content = {
            BrowserTabDragPreviewCard(
                tab = tab,
                mode = mode,
                modifier = Modifier.drawWithContent {
                    val width = size.width.roundToInt()
                    val height = size.height.roundToInt()
                    if (width > 0 && height > 0) {
                        val pictureCanvas = Canvas(picture.beginRecording(width, height))
                        try {
                            draw(this, layoutDirection, pictureCanvas, size) {
                                this@drawWithContent.drawContent()
                            }
                        } finally {
                            picture.endRecording()
                        }
                        currentOnPictureRecorded()
                    }
                },
            )
        },
    ) { measurables, _ ->
        val widthPx = DragPreviewCanvasWidth.roundToPx()
        val heightPx = DragPreviewCanvasHeight.roundToPx()
        val placeable = measurables.first().measure(Constraints.fixed(widthPx, heightPx))
        layout(0, 0) {
            placeable.place(0, 0)
        }
    }
}

@Composable
internal fun BrowserTabDragPreviewCard(
    tab: TabSummary,
    mode: TabDragPreviewMode,
    modifier: Modifier = Modifier,
) {
    val transition = updateTransition(targetState = mode, label = "TabDragPreviewTransition")
    val expansionProgress by transition.animateFloat(
        transitionSpec = {
            spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow,
            )
        },
        label = "ExpansionProgress",
    ) { targetMode ->
        when (targetMode) {
            TabDragPreviewMode.MoveToWindow,
            TabDragPreviewMode.Unavailable,
            -> 0f
            TabDragPreviewMode.NewWindow -> 1f
        }
    }
    val disabledProgress by transition.animateFloat(
        transitionSpec = {
            spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMedium,
            )
        },
        label = "DisabledProgress",
    ) { targetMode ->
        when (targetMode) {
            TabDragPreviewMode.Unavailable -> 1f
            TabDragPreviewMode.MoveToWindow,
            TabDragPreviewMode.NewWindow,
            -> 0f
        }
    }

    BrowserTabDragPreviewFrame(
        tab = tab,
        expansionProgress = expansionProgress,
        disabledProgress = disabledProgress,
        modifier = modifier,
    )
}

@Composable
internal fun BrowserTabDragPreviewFrame(
    tab: TabSummary,
    expansionProgress: Float,
    modifier: Modifier = Modifier,
    disabledProgress: Float = 0f,
) {
    val p = expansionProgress.coerceIn(0f, 1f)
    val d = disabledProgress.coerceIn(0f, 1f)

    val cardWidth = lerp(124.dp, 184.dp, p)
    val cardHeight = lerp(32.dp, 106.dp, p)
    val cardTop = lerp(24.dp, 6.dp, p)
    val cornerRadius = lerp(10.dp, 14.dp, p)
    val shadowOffsetY = lerp(2.dp, 3.dp, p)
    val shadowAlpha = lerp(lerp(0.16f, 0.18f, p), 0.06f, d)
    val cardShape = RoundedCornerShape(cornerRadius)
    val cardContainerColor = lerp(
        MaterialTheme.colorScheme.surface,
        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.82f),
        d,
    )
    val cardBorderColor = lerp(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f),
        d,
    )

    Box(
        modifier = modifier.size(DragPreviewCanvasWidth, DragPreviewCanvasHeight),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = cardTop + shadowOffsetY)
                .size(cardWidth, cardHeight)
                .background(Color.Black.copy(alpha = shadowAlpha), cardShape),
        )

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = cardTop)
                .size(cardWidth, cardHeight)
                .clip(cardShape)
                .background(cardContainerColor, cardShape)
                .border(1.5.dp, cardBorderColor, cardShape),
        ) {
            if (p > 0.001f) {
                NewWindowPreviewHeader(expansionProgress = p)
            }

            TabPreviewStripRow(
                tab = tab,
                expansionProgress = p,
                disabledProgress = d,
            )

            if (p > 0.001f) {
                NewWindowPreviewWireframe(
                    expansionProgress = p,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
            }
        }
    }
}

@Composable
private fun NewWindowPreviewHeader(
    expansionProgress: Float,
    modifier: Modifier = Modifier,
) {
    val headerHeight = lerp(0.dp, 22.dp, expansionProgress)
    val contentAlpha = ((expansionProgress - 0.35f) / 0.65f).coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(headerHeight)
            .clipToBounds()
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = expansionProgress)),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(align = Alignment.Bottom, unbounded = true)
                .height(22.dp)
                .padding(start = 10.dp, end = 12.dp)
                .graphicsLayer { alpha = contentAlpha },
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(12.dp),
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = "New window",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 1,
            )
            Spacer(Modifier.weight(1f))
            Row(
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(3) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .background(
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.45f),
                                shape = CircleShape,
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun TabPreviewStripRow(
    tab: TabSummary,
    expansionProgress: Float,
    disabledProgress: Float,
    modifier: Modifier = Modifier,
) {
    val tabStripHeight = lerp(32.dp, 26.dp, expansionProgress)
    val iconColor = lerp(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
        disabledProgress,
    )
    val titleColor = lerp(
        MaterialTheme.colorScheme.onSurface,
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
        disabledProgress,
    )
    val badgeContainerColor = lerp(
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
        disabledProgress,
    )
    val badgeContentColor = lerp(
        MaterialTheme.colorScheme.onPrimaryContainer,
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
        disabledProgress,
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(tabStripHeight)
            .background(
                MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = expansionProgress),
            )
            .padding(
                start = lerp(0.dp, 8.dp, expansionProgress),
                top = lerp(0.dp, 3.dp, expansionProgress),
                end = lerp(0.dp, 26.dp, expansionProgress),
            ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxSize()
                .background(
                    MaterialTheme.colorScheme.surface.copy(alpha = expansionProgress),
                    MiniTabShape,
                )
                .padding(horizontal = lerp(10.dp, 8.dp, expansionProgress)),
        ) {
            Icon(
                imageVector = tab.currentRoute.icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = tab.title,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (tab.stackDepth > 1) {
                Spacer(Modifier.width(6.dp))
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .height(15.dp)
                        .widthIn(min = 16.dp)
                        .background(badgeContainerColor, CircleShape)
                        .padding(horizontal = 5.dp),
                ) {
                    Text(
                        text = tab.stackDepth.toString(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            platformStyle = PlatformTextStyle(includeFontPadding = false),
                        ),
                        color = badgeContentColor,
                        maxLines = 1,
                        modifier = Modifier.wrapContentHeight(unbounded = true),
                    )
                }
            }
        }
    }
}

@Composable
private fun NewWindowPreviewWireframe(
    expansionProgress: Float,
    modifier: Modifier = Modifier,
) {
    val contentAlpha = ((expansionProgress - 0.25f) / 0.75f).coerceIn(0f, 1f)

    Box(
        modifier = modifier.clipToBounds(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(align = Alignment.Top, unbounded = true)
                .height(58.dp)
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .graphicsLayer { alpha = contentAlpha },
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        shape = RoundedCornerShape(6.dp),
                    ),
            )
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.82f)
                    .height(7.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(3.5.dp),
                    ),
            )
            Spacer(Modifier.height(5.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.56f)
                    .height(7.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(3.5.dp),
                    ),
            )
        }
    }
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

@Preview(showBackground = true, name = "Drag Preview - Mid Transition (50%)")
@Composable
private fun BrowserTabDragPreviewMidTransitionPreview() {
    AppStateRecipesTheme {
        Box(modifier = Modifier.padding(12.dp)) {
            BrowserTabDragPreviewFrame(
                tab = TabSummary(
                    tabId = "tab-2",
                    isSelected = true,
                    stackDepth = 3,
                    currentRoute = BrowserRoute.Article(42, "Article #42"),
                ),
                expansionProgress = 0.5f,
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

@Preview(showBackground = true, name = "Drag Preview - Unavailable")
@Composable
private fun BrowserTabDragPreviewUnavailablePreview() {
    AppStateRecipesTheme {
        Box(modifier = Modifier.padding(12.dp)) {
            BrowserTabDragPreviewCard(
                tab = TabSummary(
                    tabId = "tab-2",
                    isSelected = true,
                    stackDepth = 3,
                    currentRoute = BrowserRoute.Article(42, "Article #42"),
                ),
                mode = TabDragPreviewMode.Unavailable,
            )
        }
    }
}
