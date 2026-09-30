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

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.appstaterecipes.ui.excludeBottom
import com.example.appstaterecipes.ui.onlyBottom
import com.example.appstaterecipes.ui.plus
import com.example.appstaterecipes.ui.theme.AppStateRecipesTheme

@Composable
fun DebounceScreen(viewModel: DebounceViewModel) {
    val uiState by viewModel.uiState
    DebounceScreenContent(
        uiState = uiState,
        textFieldState = viewModel.textFieldState,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebounceScreenContent(
    uiState: DebounceUiState,
    textFieldState: TextFieldState,
) {
    val queryText = textFieldState.text.toString()
    val hasQuery = queryText.isNotBlank()

    val debounceProgress = remember { Animatable(0f) }
    LaunchedEffect(queryText) {
        debounceProgress.snapTo(0f)
        if (hasQuery) {
            debounceProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = DEFAULT_DEBOUNCE_TIMEOUT_MILLIS.toInt(),
                    easing = LinearEasing,
                ),
            )
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Debounce") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding.excludeBottom())
                .imePadding(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    state = textFieldState,
                    label = { Text("Search Jetpack libraries") },
                    modifier = Modifier.fillMaxWidth(),
                    lineLimits = TextFieldLineLimits.SingleLine,
                )

                LinearProgressIndicator(
                    progress = { if (hasQuery) debounceProgress.value else 0f },
                    modifier = Modifier.fillMaxWidth(),
                )

                Text(
                    text = if (uiState.debouncedQuery.isBlank()) {
                        "Debounced query: (none)"
                    } else {
                        "Debounced query: \"${uiState.debouncedQuery}\""
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )

                Text(
                    text = "Results (${uiState.matchingItems.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp) +
                    innerPadding.onlyBottom(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(uiState.matchingItems) { item ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        ),
                    ) {
                        Text(
                            text = item,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DebounceScreenPreview() {
    AppStateRecipesTheme {
        DebounceScreenContent(
            uiState = DebounceUiState(
                debouncedQuery = "Compose",
                matchingItems = listOf(
                    "Activity Compose",
                    "Compose Animation",
                    "Compose Foundation",
                    "Compose Material 3",
                    "Compose UI",
                ),
            ),
            textFieldState = rememberTextFieldState("Compose"),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun DebounceScreenEmptyPreview() {
    AppStateRecipesTheme {
        DebounceScreenContent(
            uiState = DebounceUiState(
                debouncedQuery = "",
                matchingItems = listOf("Activity Compose", "AppState Transform"),
            ),
            textFieldState = rememberTextFieldState(),
        )
    }
}
