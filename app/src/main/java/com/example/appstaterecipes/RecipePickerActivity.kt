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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.example.appstaterecipes.debounce.DebounceActivity
import com.example.appstaterecipes.sample.SampleActivity
import com.example.appstaterecipes.ui.excludeBottom
import com.example.appstaterecipes.ui.onlyBottom
import com.example.appstaterecipes.ui.plus
import com.example.appstaterecipes.ui.theme.AppStateRecipesTheme

sealed interface RecipeTarget {
    data class InternalActivity(val activityClass: Class<out Activity>) : RecipeTarget
    data class ExternalApp(
        val packageName: String,
        val runConfigurationName: String,
        val modulePath: String = ":${runConfigurationName.replace('.', ':')}",
    ) : RecipeTarget
}

private sealed interface Item

private class Recipe(
    val name: String,
    val description: String,
    val target: RecipeTarget,
) : Item

private class Heading(val name: String) : Item

private val recipes = listOf(
    Heading("Transform — Rate Limiting"),
    Recipe(
        name = "Debounce",
        description = "Delays work until input stops changing for a quiet period (e.g. search-as-you-type).",
        target = RecipeTarget.InternalActivity(DebounceActivity::class.java),
    ),
    Recipe(
        name = "Sample",
        description = "Throttles high-frequency streams into a fixed periodic cadence (e.g. sensor readings).",
        target = RecipeTarget.InternalActivity(SampleActivity::class.java),
    ),
    Heading("StateStore — Multi-Window Navigation"),
    Recipe(
        name = "Browser Multi-Window Navigation",
        description = "Multi-window and multi-tab navigation coordinating Navigation 3 back stacks across Activity windows via StateStore.",
        target = RecipeTarget.ExternalApp(
            packageName = "com.example.appstaterecipes.browser",
            runConfigurationName = "apps.browser",
        ),
    ),
)

/**
 * Activity to browse and launch all AppState recipes.
 * Each recipe lives in its own subpackage or dedicated standalone application under the apps directory.
 */
class RecipePickerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppStateRecipesTheme {
                RecipePickerScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipePickerScreen(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var resumeTrigger by remember { mutableIntStateOf(0) }
    var uninstalledAppTarget by remember { mutableStateOf<RecipeTarget.ExternalApp?>(null) }

    LifecycleResumeEffect(Unit) {
        resumeTrigger++
        onPauseOrDispose { }
    }

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
            onRecipeClick = { target ->
                when (target) {
                    is RecipeTarget.InternalActivity -> {
                        context.startActivity(Intent(context, target.activityClass))
                    }
                    is RecipeTarget.ExternalApp -> {
                        val launchIntent = context.packageManager.getLaunchIntentForPackage(target.packageName)
                        if (launchIntent != null) {
                            context.startActivity(launchIntent)
                        } else {
                            uninstalledAppTarget = target
                        }
                    }
                }
            },
            isAppInstalled = { packageName ->
                // resumeTrigger is referenced so that installation status is re-evaluated on resume
                if (resumeTrigger >= 0) {
                    context.packageManager.getLaunchIntentForPackage(packageName) != null
                } else {
                    false
                }
            },
            contentPadding = PaddingValues(16.dp) + innerPadding.onlyBottom(),
            modifier = Modifier.padding(innerPadding.excludeBottom()),
        )
    }

    uninstalledAppTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { uninstalledAppTarget = null },
            title = { Text("Standalone App Not Installed") },
            text = {
                Column {
                    Text(
                        "This recipe lives in its own standalone application module. To run it, select the run configuration in Android Studio:",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "1. In the Android Studio toolbar, switch the run target to:",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = target.runConfigurationName,
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                ),
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "2. Click Run (or press Control+R / Shift+F10).",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Or from terminal:",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "./gradlew ${target.modulePath}:installDebug",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { uninstalledAppTarget = null }) {
                    Text("OK")
                }
            },
        )
    }
}

@Composable
fun RecipeList(
    onRecipeClick: (RecipeTarget) -> Unit,
    isAppInstalled: (packageName: String) -> Boolean,
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
                        onClick = { onRecipeClick(item.target) },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                        ) {
                            Text(
                                text = item.name,
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = item.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (item.target is RecipeTarget.ExternalApp) {
                                Spacer(modifier = Modifier.height(10.dp))
                                val installed = isAppInstalled(item.target.packageName)
                                StandaloneAppBadge(installed = installed)
                            }
                        }
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

@Composable
fun StandaloneAppBadge(
    installed: Boolean,
    modifier: Modifier = Modifier,
) {
    val indicatorColor = if (installed) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline
    }
    val labelText = if (installed) {
        "Standalone app • Installed"
    } else {
        "Standalone app • Not installed"
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(indicatorColor, CircleShape),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = labelText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun RecipePickerScreenPreview() {
    AppStateRecipesTheme {
        RecipeList(
            onRecipeClick = {},
            isAppInstalled = { true },
        )
    }
}
