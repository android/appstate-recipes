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

package com.example.appstaterecipes.browser.ui.panes

internal val PopularSuggestions = listOf("Navigation 3", "StateStore", "Multi-Window")

internal val FeaturedArticles = listOf(
    1 to "Multi-Window State Management with StateStore",
    2 to "NavDisplay & Seamless Predictive Back Navigation",
    3 to "Cross-Activity Drag and Drop Architecture",
    4 to "Preserving Per-Entry Scroll State Across Windows",
    5 to "Persisting Multi-Tab Back Stacks with DataStore",
    6 to "Atomic Snapshot Updates for Tab Tear-Off",
    7 to "Desktop Windowing & Freeform Launch Bounds",
    8 to "Scoping Compose Recomposition per Browser Window",
)

internal val SearchResults = listOf(
    42 to "Deep Dive: NavDisplay & StateStore Integration",
    43 to "Coordinating BackStacks Across Android 15 Multi-Window",
    44 to "DataStore Persistence for Tab State",
    45 to "Hoisting Scroll Offsets per BackStack Entry",
    46 to "Cross-Window Tab Merging and Task Lifecycle",
    47 to "Predictive Back Transitions in Tabbed Browsers",
    48 to "Snapshot Isolation for Multi-Activity UI State",
    49 to "Custom Drag Shadows for Browser Tab Tear-Off",
)

internal val ArticleSections = listOf(
    "1. Application-Scoped StateStore" to
        "Storing window and tab back stacks in an application-scoped StateStore allows " +
        "multiple BrowserActivity instances to observe and mutate shared navigation state " +
        "atomically without coupling Activities directly to one another.",
    "2. Per-Entry Scroll State Preservation" to
        "Each item on a tab's back stack has a unique entryId (\"tabId:stackIndex\"). " +
        "Scroll offsets are stored in EntryScrollOffsetsKey by entryId so visiting the " +
        "same route twice on one stack—or moving a tab to another window—retains each " +
        "entry's independent scroll position.",
    "3. Zero-Recomposition Scroll Saves" to
        "Reading the initial scroll offset inside Snapshot.withoutReadObservation ensures " +
        "that saving scroll progress to StateStore never triggers unnecessary UI " +
        "recompositions while scrolling.",
    "4. Automatic Cleanup on Back & Close" to
        "When an entry is popped via goBack or a tab is closed via closeTab/closeWindow, " +
        "its associated scroll offset entries are removed atomically in the same mutable snapshot.",
)
