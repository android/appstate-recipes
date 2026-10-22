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

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

// [START browser_routes]
@Serializable
sealed interface BrowserRoute : NavKey {
    val title: String

    @Serializable
    data object Home : BrowserRoute {
        override val title: String = "Home"
    }

    @Serializable
    data class Search(val query: String) : BrowserRoute {
        override val title: String = "Search: $query"
    }

    @Serializable
    data class Article(val id: Int, val headline: String) : BrowserRoute {
        override val title: String = headline
    }
}
// [END browser_routes]
