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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.appstaterecipes.browser.state.WindowSummary
import com.example.appstaterecipes.ui.theme.AppStateRecipesTheme

@Composable
fun WindowSwitcherDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    currentWindowId: String,
    windows: List<WindowSummary>,
    onSelectWindowInCurrent: (String) -> Unit,
    onOpenWindowInNewWindow: (String) -> Unit,
    onCloseWindow: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
    ) {
        Text(
            text = "Open windows",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )

        windows.forEach { window ->
            val isCurrentWindow = window.windowId == currentWindowId
            val canSelectInCurrent = !isCurrentWindow && !window.isAttachedToActivity
            val contentAlpha = if (canSelectInCurrent || isCurrentWindow) 1f else 0.5f

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(min = 240.dp, max = 340.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .clickable(enabled = canSelectInCurrent) {
                            onDismissRequest()
                            onSelectWindowInCurrent(window.windowId)
                        }
                        .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                ) {
                    if (isCurrentWindow) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = "Current window",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                    } else {
                        val indicatorColor = if (window.isAttachedToActivity) {
                            MaterialTheme.colorScheme.primary.copy(alpha = contentAlpha)
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        }
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(18.dp),
                        ) {
                            Spacer(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(indicatorColor, CircleShape),
                            )
                        }
                    }

                    WindowSummaryInfo(
                        window = window,
                        isHighlighted = isCurrentWindow,
                        contentAlpha = contentAlpha,
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(end = 6.dp),
                ) {
                    if (!isCurrentWindow && !window.isAttachedToActivity) {
                        IconButton(
                            onClick = {
                                onDismissRequest()
                                onOpenWindowInNewWindow(window.windowId)
                            },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                contentDescription = "Open ${window.displayName} in new window",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            onCloseWindow(window.windowId)
                        },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close ${window.displayName}",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TabActionsDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    canPopOut: Boolean,
    onPopOut: () -> Unit,
    otherWindows: List<WindowSummary>,
    onMoveToWindow: (String) -> Unit,
    onOpenStateStoreViewer: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
    ) {
        DropdownMenuItem(
            text = {
                Text(
                    text = "Pop out to new window",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
            },
            enabled = canPopOut,
            onClick = {
                onDismissRequest()
                onPopOut()
            },
        )

        if (onOpenStateStoreViewer != null) {
            DropdownMenuItem(
                text = {
                    Text(
                        text = "StateStore Viewer",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                onClick = {
                    onDismissRequest()
                    onOpenStateStoreViewer()
                },
            )
        }

        if (otherWindows.isNotEmpty()) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            Text(
                text = "Move tab to",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )

            otherWindows.forEach { targetWindow ->
                DropdownMenuItem(
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.DriveFileMove,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    text = {
                        WindowSummaryInfo(
                            window = targetWindow,
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                    },
                    onClick = {
                        onDismissRequest()
                        onMoveToWindow(targetWindow.windowId)
                    },
                )
            }
        }
    }
}

@Composable
internal fun WindowSummaryInfo(
    window: WindowSummary,
    modifier: Modifier = Modifier,
    isHighlighted: Boolean = false,
    contentAlpha: Float = 1f,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = window.displayName,
                fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha),
            )
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = contentAlpha),
                shape = RoundedCornerShape(4.dp),
            ) {
                Text(
                    text = "${window.tabCount} ${if (window.tabCount == 1) "tab" else "tabs"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = contentAlpha),
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }

        window.firstTabTitles.forEach { title ->
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun WindowSwitcherDropdownMenuPreview() {
    val sampleWindows = listOf(
        WindowSummary(
            windowId = "win-1",
            displayName = "Window 1",
            tabCount = 2,
            firstTabTitles = listOf("Home", "Article #42"),
            isAttachedToActivity = true,
        ),
        WindowSummary(
            windowId = "win-2",
            displayName = "Window 2",
            tabCount = 3,
            firstTabTitles = listOf("Search: compose", "Deep Dive: NavDisplay"),
            isAttachedToActivity = true,
        ),
        WindowSummary(
            windowId = "win-3",
            displayName = "Window 3",
            tabCount = 1,
            firstTabTitles = listOf("Persisted Session"),
            isAttachedToActivity = false,
        ),
    )
    AppStateRecipesTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            WindowSwitcherDropdownMenu(
                expanded = true,
                onDismissRequest = {},
                currentWindowId = "win-1",
                windows = sampleWindows,
                onSelectWindowInCurrent = {},
                onOpenWindowInNewWindow = {},
                onCloseWindow = {},
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun MoveTabDropdownMenuPreview() {
    val sampleOtherWindows = listOf(
        WindowSummary(
            windowId = "win-2",
            displayName = "Window 2",
            tabCount = 3,
            firstTabTitles = listOf("Search: compose", "Deep Dive: NavDisplay"),
        ),
        WindowSummary(
            windowId = "win-3",
            displayName = "Window 3",
            tabCount = 1,
            firstTabTitles = listOf("Browser Home"),
        ),
    )
    AppStateRecipesTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            TabActionsDropdownMenu(
                expanded = true,
                onDismissRequest = {},
                canPopOut = true,
                onPopOut = {},
                otherWindows = sampleOtherWindows,
                onMoveToWindow = {},
            )
        }
    }
}
