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

package com.example.appstaterecipes.statestoreviewer

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.appstaterecipes.StateStoreOwner
import com.example.appstaterecipes.ui.excludeBottom
import com.example.appstaterecipes.ui.onlyBottom
import com.example.appstaterecipes.ui.plus
import com.example.appstaterecipes.ui.theme.AppStateRecipesTheme

/**
 * Activity that displays the live contents of the application's shared [androidx.appstate.statestore.StateStore].
 */
class StateStoreViewerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val stateStore = (application as? StateStoreOwner)?.stateStore

        setContent {
            AppStateRecipesTheme {
                if (stateStore == null) {
                    StateStoreViewerScreen(
                        uiState = StateStoreViewerUiState(),
                        onNavigateBack = { finish() },
                    )
                } else {
                    val uiState by rememberStateStoreViewerUiState(stateStore)
                    StateStoreViewerScreen(
                        uiState = uiState,
                        onNavigateBack = { finish() },
                        onPopOutWindow = {
                            val intent = Intent(this, StateStoreViewerActivity::class.java).apply {
                                addFlags(
                                    Intent.FLAG_ACTIVITY_NEW_DOCUMENT or
                                        Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
                                        Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT
                                )
                            }
                            startActivity(intent)
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StateStoreViewerScreen(
    uiState: StateStoreViewerUiState,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    onPopOutWindow: (() -> Unit)? = null,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("StateStore Viewer") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Navigate back",
                        )
                    }
                },
                actions = {
                    if (onPopOutWindow != null) {
                        TextButton(onClick = onPopOutWindow) {
                            Text(
                                text = "New Window ↗",
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding.excludeBottom()),
            contentPadding = PaddingValues(16.dp) + innerPadding.onlyBottom(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "summary_header") {
                StateStoreSummaryCard(uiState = uiState)
            }

            if (uiState.entries.isEmpty()) {
                item(key = "empty_state") {
                    EmptyStateStoreCard()
                }
            } else {
                items(
                    items = uiState.entries,
                    key = { it.qualifiedKeyName ?: it.keyName },
                ) { entry ->
                    StateStoreEntryCard(entry = entry)
                }
            }
        }
    }
}

@Composable
private fun StateStoreSummaryCard(
    uiState: StateStoreViewerUiState,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SummaryMetric(
                label = "Active Keys",
                value = uiState.totalKeyCount.toString(),
            )
            SummaryMetric(
                label = "Persisted",
                value = uiState.persistedKeyCount.toString(),
            )
            SummaryMetric(
                label = "Auto-Clear",
                value = uiState.autoClearKeyCount.toString(),
            )
        }
    }
}

@Composable
private fun SummaryMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

@Composable
private fun EmptyStateStoreCard(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "StateStore is currently empty",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "No StateStoreKey entries have been registered yet. Open a StateStore-backed recipe (such as Browser Multi-Window Navigation) to populate state and inspect live updates here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StateStoreEntryCard(
    entry: StateStoreEntryUiState,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = entry.keyName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                if (entry.qualifiedKeyName != null) {
                    Text(
                        text = entry.qualifiedKeyName,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                MetadataBadge(
                    text = "Type: ${entry.valueTypeName}",
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                )
                if (entry.isPersistedToDataStore) {
                    MetadataBadge(
                        text = "PersistToDataStore",
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                if (entry.autoClearKeyName != null) {
                    MetadataBadge(
                        text = "AutoClear on ${entry.autoClearKeyName}",
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
                MetadataBadge(
                    text = if (entry.isDefaultValue) "Default Value" else "Modified",
                    containerColor = if (entry.isDefaultValue) {
                        MaterialTheme.colorScheme.surface
                    } else {
                        MaterialTheme.colorScheme.secondaryContainer
                    },
                    contentColor = if (entry.isDefaultValue) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    },
                )
            }

            HorizontalDivider()

            Text(
                text = "Current Value",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(8.dp),
                    )
                    .horizontalScroll(rememberScrollState())
                    .padding(12.dp),
            ) {
                Text(
                    text = entry.formattedValue,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun MetadataBadge(
    text: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(6.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun StateStoreViewerScreenPopulatedPreview() {
    AppStateRecipesTheme {
        StateStoreViewerScreen(
            uiState = StateStoreViewerUiState(
                entries = listOf(
                    StateStoreEntryUiState(
                        keyName = "WindowsKey",
                        qualifiedKeyName = "com.example.appstaterecipes.browser.state.WindowsKey",
                        valueTypeName = "LinkedHashMap",
                        isPersistedToDataStore = true,
                        autoClearKeyName = null,
                        isDefaultValue = false,
                        formattedValue = """
                            {
                              "win-a1b2c3d4": WindowState(windowId=win-a1b2c3d4, tabIds=[tab-11223344], activeTabId=tab-11223344)
                            }
                        """.trimIndent(),
                        formattedDefaultValue = "{}",
                    ),
                    StateStoreEntryUiState(
                        keyName = "TabBackStacksKey",
                        qualifiedKeyName = "com.example.appstaterecipes.browser.state.TabBackStacksKey",
                        valueTypeName = "LinkedHashMap",
                        isPersistedToDataStore = true,
                        autoClearKeyName = null,
                        isDefaultValue = false,
                        formattedValue = """
                            {
                              "tab-11223344": [
                                Home,
                                Article(id=1, headline=Multi-Window State Management)
                              ]
                            }
                        """.trimIndent(),
                        formattedDefaultValue = "{}",
                    ),
                ),
            ),
            onNavigateBack = {},
            onPopOutWindow = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun StateStoreViewerScreenEmptyPreview() {
    AppStateRecipesTheme {
        StateStoreViewerScreen(
            uiState = StateStoreViewerUiState(),
            onNavigateBack = {},
        )
    }
}
