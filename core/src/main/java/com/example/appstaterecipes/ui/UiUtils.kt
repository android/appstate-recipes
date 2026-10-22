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

package com.example.appstaterecipes.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Stable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * Returns a [PaddingValues] that delegates left, top, and right padding to [this],
 * but returns 0.dp for bottom padding so that scrollable content can draw behind
 * navigation bars.
 */
@Stable
fun PaddingValues.excludeBottom(): PaddingValues = object : PaddingValues {
    override fun calculateLeftPadding(layoutDirection: LayoutDirection): Dp =
        this@excludeBottom.calculateLeftPadding(layoutDirection)

    override fun calculateTopPadding(): Dp =
        this@excludeBottom.calculateTopPadding()

    override fun calculateRightPadding(layoutDirection: LayoutDirection): Dp =
        this@excludeBottom.calculateRightPadding(layoutDirection)

    override fun calculateBottomPadding(): Dp = 0.dp
}

/**
 * Returns a [PaddingValues] containing only the bottom inset from [this],
 * with all other edges zeroed out.
 */
@Stable
fun PaddingValues.onlyBottom(): PaddingValues = object : PaddingValues {
    override fun calculateLeftPadding(layoutDirection: LayoutDirection): Dp = 0.dp
    override fun calculateTopPadding(): Dp = 0.dp
    override fun calculateRightPadding(layoutDirection: LayoutDirection): Dp = 0.dp
    override fun calculateBottomPadding(): Dp = this@onlyBottom.calculateBottomPadding()
}

/**
 * Combines two [PaddingValues] by adding their respective insets together.
 */
@Stable
operator fun PaddingValues.plus(other: PaddingValues): PaddingValues = object : PaddingValues {
    override fun calculateLeftPadding(layoutDirection: LayoutDirection): Dp =
        this@plus.calculateLeftPadding(layoutDirection) + other.calculateLeftPadding(layoutDirection)

    override fun calculateTopPadding(): Dp =
        this@plus.calculateTopPadding() + other.calculateTopPadding()

    override fun calculateRightPadding(layoutDirection: LayoutDirection): Dp =
        this@plus.calculateRightPadding(layoutDirection) + other.calculateRightPadding(layoutDirection)

    override fun calculateBottomPadding(): Dp =
        this@plus.calculateBottomPadding() + other.calculateBottomPadding()
}
