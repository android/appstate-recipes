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

package com.example.appstaterecipes.debounce

import androidx.appstate.transform.transform
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

data class DebounceUiState(
    val debouncedQuery: String = "",
    val matchingItems: List<String> = jetpackCatalog,
)

class DebounceViewModel : ViewModel() {
    val textFieldState = TextFieldState()

    // [START debounce_transform_usage]
    val uiState: State<DebounceUiState> = transform(
        initialValue = DebounceUiState(),
        scope = viewModelScope,
    ) {
        val rawQuery = textFieldState.text.toString()
        val debouncedQuery by rememberDebounced(rawQuery, timeoutMillis = DEFAULT_DEBOUNCE_TIMEOUT_MILLIS)

        val filtered =
            if (debouncedQuery.isBlank()) {
                jetpackCatalog
            } else {
                jetpackCatalog.filter { it.contains(debouncedQuery, ignoreCase = true) }
            }

        DebounceUiState(
            debouncedQuery = debouncedQuery,
            matchingItems = filtered,
        )
    }
    // [END debounce_transform_usage]
}

private val jetpackCatalog = listOf(
    "Activity Compose",
    "Annotation",
    "AppFunctions",
    "AppState StateStore",
    "AppState StateStore DataStore",
    "AppState Transform",
    "Arch Core",
    "Autofill",
    "Benchmark Macrobenchmark",
    "Biometric",
    "Browser Custom Tabs",
    "CameraX Camera2",
    "CameraX View",
    "Car App",
    "Collection",
    "Compose Animation",
    "Compose Foundation",
    "Compose Material 3",
    "Compose Material 3 Adaptive",
    "Compose Runtime",
    "Compose UI",
    "Concurrent Futures",
    "ConstraintLayout Compose",
    "Core KTX",
    "Core Splashscreen",
    "Credentials",
    "DataStore Preferences",
    "DataStore Proto",
    "DocumentFile",
    "DragAndDrop",
    "Emoji2",
    "ExifInterface",
    "Glance AppWidget",
    "Graphics Shapes",
    "Health Connect",
    "Hilt Navigation Compose",
    "Ink Stroke",
    "Interpolator",
    "Lifecycle LiveData",
    "Lifecycle Runtime Compose",
    "Lifecycle ViewModel",
    "Lifecycle ViewModel Compose",
    "Media3 ExoPlayer",
    "Media3 UI",
    "Navigation 3",
    "Navigation Compose",
    "Paging 3",
    "Paging Compose",
    "Palette",
    "PrivacySandbox Tools",
    "ProfileInstaller",
    "RecyclerView",
    "Resource Inspection",
    "Room Database",
    "Room Paging",
    "SavedState",
    "Security Crypto",
    "ShareTarget",
    "SlidingPaneLayout",
    "SQLite",
    "Startup",
    "Tracing",
    "Transition",
    "VectorDrawable",
    "ViewPager2",
    "Wear Compose Foundation",
    "Wear Compose Material 3",
    "WebKit",
    "Window WindowManager",
    "WorkManager",
    "WorkManager Multiprocess",
)
