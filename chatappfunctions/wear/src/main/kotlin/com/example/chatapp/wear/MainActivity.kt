/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.chatapp.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.navigation3.rememberSwipeDismissableSceneStrategy
import com.example.chatapp.data.AppScreen
import com.example.chatapp.data.ChatAppStateHolder
import com.example.chatapp.wear.uicomponents.WearAppFunctionsScreen
import com.example.chatapp.wear.uicomponents.WearCallScreen
import com.example.chatapp.wear.uicomponents.WearChatScreen
import com.example.chatapp.wear.uicomponents.WearRecipientsScreen
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.serialization.Serializable

@Serializable
sealed interface Screen : NavKey {
    @Serializable
    data object Recipients : Screen

    @Serializable
    data object Settings : Screen

    @Serializable
    data class Chat(val recipientId: String) : Screen

    @Serializable
    data class Call(val recipientId: String) : Screen
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    /** Application-level state shared with the AppFunctions service. */
    @Inject lateinit var appState: ChatAppStateHolder

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WearApp(appState)
        }
    }
}

@Composable
fun WearApp(appState: ChatAppStateHolder) {
    val backStack = rememberNavBackStack(Screen.Recipients)

    // Keep the shared app state and the back stack in sync: show the screen when it is changed
    // outside the UI (for example by an agent calling the changeScreen AppFunction), and report
    // the screen the user navigates to.
    val currentScreen = appState.currentScreen.value
    LaunchedEffect(currentScreen) {
        if ((backStack.last() as Screen).toAppScreen() != currentScreen) {
            backStack.show(currentScreen)
        }
    }
    LaunchedEffect(backStack.last()) {
        // Read the live back stack: the effect above may have just navigated.
        appState.setCurrentScreen((backStack.last() as Screen).toAppScreen())
    }

    MaterialTheme {
        AppScaffold {
            val swipeDismissableSceneStrategy = rememberSwipeDismissableSceneStrategy<NavKey>()

            NavDisplay(
                backStack = backStack,
                onBack = {
                    if (backStack.size > 1) {
                        backStack.removeAt(backStack.size - 1)
                    }
                },
                entryDecorators =
                    listOf(
                        rememberSaveableStateHolderNavEntryDecorator(),
                        rememberViewModelStoreNavEntryDecorator(),
                    ),
                entryProvider =
                    entryProvider<NavKey> {
                        entry<Screen.Recipients> {
                            WearRecipientsScreen(
                                onRecipientClick = { id -> backStack.add(Screen.Chat(id)) },
                                onSettingsClick = { backStack.add(Screen.Settings) },
                            )
                        }
                        entry<Screen.Settings> {
                            WearAppFunctionsScreen()
                        }
                        entry<Screen.Chat> { key ->
                            WearChatScreen(
                                recipientId = key.recipientId,
                                onCallClick = { backStack.add(Screen.Call(key.recipientId)) },
                            )
                        }
                        entry<Screen.Call> { key ->
                            WearCallScreen(
                                recipientId = key.recipientId,
                                onEndCall = { backStack.removeAt(backStack.size - 1) },
                            )
                        }
                    },
                sceneStrategies = listOf(swipeDismissableSceneStrategy),
            )
        }
    }
}

private fun Screen.toAppScreen(): AppScreen =
    when (this) {
        Screen.Recipients -> AppScreen.ChatList
        Screen.Settings -> AppScreen.Settings
        is Screen.Chat -> AppScreen.Chat(recipientId)
        is Screen.Call -> AppScreen.Call(recipientId)
    }

/**
 * Shows [screen] on top of the recipients list. Other screens are popped first so that repeated
 * `changeScreen` calls from an agent don't keep stacking destinations on the back stack.
 */
private fun MutableList<NavKey>.show(screen: AppScreen) {
    subList(1, size).clear()
    when (screen) {
        AppScreen.ChatList -> Unit
        AppScreen.Settings -> add(Screen.Settings)
        is AppScreen.Chat -> add(Screen.Chat(screen.endpointValue))
        is AppScreen.Call -> add(Screen.Call(screen.endpointValue))
    }
}
