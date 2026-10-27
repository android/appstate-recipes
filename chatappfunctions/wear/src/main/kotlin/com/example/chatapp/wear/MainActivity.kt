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
import androidx.appstate.statestore.StateStore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.wear.compose.material3.MaterialTheme
import com.example.chatapp.data.AppScreen
import com.example.chatapp.data.currentScreen
import com.example.chatapp.data.setCurrentScreen
import com.example.chatapp.wear.uicomponents.WearAppFunctionsScreen
import com.example.chatapp.wear.uicomponents.WearCallScreen
import com.example.chatapp.wear.uicomponents.WearChatScreen
import com.example.chatapp.wear.uicomponents.WearRecipientsScreen
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.serialization.Serializable

@Serializable
sealed interface Screen : NavKey {
    @Serializable data object Recipients : Screen

    @Serializable data object Settings : Screen

    @Serializable data class Chat(val recipientId: String) : Screen

    @Serializable data class Call(val recipientId: String) : Screen
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    /** Application-level state shared with the AppFunctions service. */
    @Inject lateinit var stateStore: StateStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WearApp(stateStore)
        }
    }
}

@Composable
fun WearApp(stateStore: StateStore) {
    val backStack = remember { mutableStateListOf<Screen>(Screen.Recipients) }

    // Keep the shared StateStore and the back stack in sync: show the screen when it is changed
    // outside the UI (for example by an agent calling the changeScreen AppFunction), and report
    // the screen the user navigates to.
    val currentScreen = stateStore.currentScreen().value
    LaunchedEffect(currentScreen) {
        if (backStack.last().toAppScreen() != currentScreen) {
            backStack.show(currentScreen)
        }
    }
    LaunchedEffect(backStack.last()) {
        // Read the live back stack: the effect above may have just navigated.
        stateStore.setCurrentScreen(backStack.last().toAppScreen())
    }

    MaterialTheme {
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
                entryProvider {
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
        )
    }
}

private fun Screen.toAppScreen(): AppScreen =
    when (this) {
        Screen.Recipients -> AppScreen.ChatList
        Screen.Settings -> AppScreen.Settings
        is Screen.Chat -> AppScreen.Chat(recipientId)
        is Screen.Call -> AppScreen.Call(recipientId)
    }

private fun MutableList<Screen>.show(screen: AppScreen) {
    when (screen) {
        AppScreen.ChatList -> subList(1, size).clear()
        AppScreen.Settings -> add(Screen.Settings)
        is AppScreen.Chat -> add(Screen.Chat(screen.endpointValue))
        is AppScreen.Call -> add(Screen.Call(screen.endpointValue))
    }
}
