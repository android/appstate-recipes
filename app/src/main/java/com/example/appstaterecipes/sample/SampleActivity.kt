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

package com.example.appstaterecipes.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.appstaterecipes.ui.theme.AppStateRecipesTheme

class SampleActivity : ComponentActivity() {
    private val viewModel: SampleViewModel by viewModels {
        val maxRefreshRate = Utils.getMaxRefreshRate(this)
        SampleViewModel.factory(
            realTracker = RealAccelerationTracker(applicationContext),
            targetSimulatedHz = maxRefreshRate,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Lock display mode for demo purposes
        Utils.lockToHighRefreshRate(this)

        setContent {
            AppStateRecipesTheme {
                SampleScreen(viewModel = viewModel)
            }
        }
    }
}
