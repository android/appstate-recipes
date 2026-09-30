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

package com.example.appstaterecipes

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.appstaterecipes.debounce.DebounceActivity
import com.example.appstaterecipes.sample.SampleActivity
import com.example.appstaterecipes.ui.excludeBottom
import com.example.appstaterecipes.ui.onlyBottom
import com.example.appstaterecipes.ui.plus
import com.example.appstaterecipes.ui.theme.AppStateRecipesTheme

private sealed interface Item

private class Recipe(
    val name: String,
    val description: String,
    val activityClass: Class<out Activity>,
) : Item

private class Heading(val name: String) : Item

private val recipes = listOf(
    Heading("Transform — Rate Limiting"),
    Recipe(
        name = "Debounce",
        description = "Delays work until input stops changing for a quiet period (e.g. search-as-you-type).",
        activityClass = DebounceActivity::class.java,
    ),
    Recipe(
        name = "Sample",
        description = "Throttles high-frequency streams into a fixed periodic cadence (e.g. sensor readings).",
        activityClass = SampleActivity::class.java,
    ),
)

/**
 * Activity to browse and launch all AppState recipes.
 * Each recipe lives in its own subpackage with its own Activity and README.md.
 */
class RecipePickerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppStateRecipesTheme {
                RecipePickerScreen(
                    onRecipeClick = { activityClass ->
                        startActivity(Intent(this, activityClass))
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipePickerScreen(
    onRecipeClick: (Class<out Activity>) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("AppState Recipes") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        },
    ) { innerPadding ->
        RecipeList(
            onRecipeClick = onRecipeClick,
            contentPadding = PaddingValues(16.dp) + innerPadding.onlyBottom(),
            modifier = Modifier.padding(innerPadding.excludeBottom()),
        )
    }
}

@Composable
fun RecipeList(
    onRecipeClick: (Class<out Activity>) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 300.dp),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        items(
            items = recipes,
            span = { item ->
                when (item) {
                    is Heading -> GridItemSpan(maxLineSpan)
                    is Recipe -> GridItemSpan(1)
                }
            },
        ) { item ->
            when (item) {
                is Recipe -> {
                    Card(
                        onClick = { onRecipeClick(item.activityClass) },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    ) {
                        ListItem(
                            headlineContent = {
                                Text(
                                    text = item.name,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            },
                            supportingContent = {
                                Text(
                                    text = item.description,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            },
                            colors = ListItemDefaults.colors(
                                containerColor = Color.Transparent,
                            ),
                        )
                    }
                }

                is Heading -> {
                    Text(
                        text = item.name,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun RecipePickerScreenPreview() {
    AppStateRecipesTheme {
        RecipePickerScreen(onRecipeClick = {})
    }
}
