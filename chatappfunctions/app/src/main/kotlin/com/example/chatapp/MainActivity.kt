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
package com.example.chatapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.example.chatapp.data.AppScreen
import com.example.chatapp.data.ChatAppStateHolder
import com.example.chatapp.ui.theme.ChatAppTheme
import com.example.chatapp.uicomponents.AppFunctionsScreen
import com.example.chatapp.uicomponents.CallScreen
import com.example.chatapp.uicomponents.ChatScreen
import com.example.chatapp.uicomponents.RecipientsScreen
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    /** Application-level state shared with the AppFunctions service. */
    @Inject lateinit var appState: ChatAppStateHolder

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ChatAppTheme {
                val navController = rememberNavController()

                LaunchedEffect(intent) {
                    val navRoute = intent?.getStringExtra("nav_route")
                    if (navRoute != null) {
                        navController.navigate(navRoute) {
                            launchSingleTop = true
                        }
                    }
                }

                // Keep the shared app state and navigation in sync: show the screen when it is
                // changed outside the UI (for example by an agent calling the changeScreen
                // AppFunction), and report the screen the user navigates to.
                val currentScreen = appState.currentScreen.value
                // If the activity was launched with an explicit destination (a deep link or a
                // nav_route extra), show that instead of the stored screen on the first sync.
                val launchedWithDestination = remember {
                    intent?.data != null || intent?.hasExtra("nav_route") == true
                }
                var isFirstSync by remember { mutableStateOf(true) }
                LaunchedEffect(currentScreen) {
                    val skip = isFirstSync && launchedWithDestination
                    isFirstSync = false
                    if (skip) return@LaunchedEffect
                    val shownScreen = navController.currentBackStackEntry?.toAppScreen()
                    if (shownScreen != null && shownScreen != currentScreen) {
                        navController.show(currentScreen)
                    }
                }
                val currentEntry by navController.currentBackStackEntryAsState()
                LaunchedEffect(currentEntry) {
                    // Read the live entry: the effect above may have just navigated.
                    navController.currentBackStackEntry?.toAppScreen()?.let(appState::setCurrentScreen)
                }

                NavHost(navController = navController, startDestination = "recipients") {
                    composable(
                        route = "recipients",
                        deepLinks = listOf(navDeepLink { uriPattern = "app://com.example.appstaterecipes.chatapp/recipients" }),
                    ) {
                        RecipientsScreen(
                            onRecipientClick = { recipientId ->
                                navController.navigate("chat/$recipientId")
                            },
                            onSettingsClick = {
                                navController.navigate("functions")
                            },
                        )
                    }
                    composable(
                        route = "functions",
                        deepLinks = listOf(navDeepLink { uriPattern = "app://com.example.appstaterecipes.chatapp/functions" }),
                    ) {
                        AppFunctionsScreen(
                            onBackClick = { navController.popBackStack() },
                        )
                    }
                    composable(
                        route = "chat/{recipientId}?draft={draft}",
                        arguments =
                            listOf(
                                navArgument("recipientId") { type = NavType.StringType },
                                navArgument("draft") {
                                    type = NavType.StringType
                                    nullable = true
                                    defaultValue = null
                                },
                            ),
                        deepLinks =
                            listOf(
                                navDeepLink {
                                    uriPattern = "app://com.example.appstaterecipes.chatapp/chat/{recipientId}?draft={draft}"
                                },
                            ),
                    ) { backStackEntry ->
                        val recipientId = backStackEntry.arguments?.getString("recipientId") ?: "bot"
                        val draft = backStackEntry.arguments?.getString("draft")
                        ChatScreen(
                            recipientId = recipientId,
                            initialDraft = draft,
                            onCallClick = { navController.navigate("call/$recipientId") },
                            onBackClick = { navController.popBackStack() },
                        )
                    }
                    composable(
                        route = "call/{recipientId}",
                        arguments = listOf(navArgument("recipientId") { type = NavType.StringType }),
                        deepLinks = listOf(navDeepLink { uriPattern = "app://com.example.appstaterecipes.chatapp/call/{recipientId}" }),
                    ) { backStackEntry ->
                        val recipientId = backStackEntry.arguments?.getString("recipientId") ?: "bot"
                        CallScreen(
                            recipientId = recipientId,
                            onEndCall = { navController.popBackStack() },
                        )
                    }
                }
            }
        }
    }
}

private fun NavBackStackEntry.toAppScreen(): AppScreen? {
    val recipientId = arguments?.getString("recipientId")
    return when (destination.route) {
        "recipients" -> AppScreen.ChatList
        "functions" -> AppScreen.Settings
        "chat/{recipientId}?draft={draft}" -> recipientId?.let(AppScreen::Chat)
        "call/{recipientId}" -> recipientId?.let(AppScreen::Call)
        else -> null
    }
}

/**
 * Shows [screen] on top of the chat list. Other screens are popped first so that repeated
 * `changeScreen` calls from an agent don't keep stacking destinations on the back stack.
 */
private fun NavController.show(screen: AppScreen) {
    val route =
        when (screen) {
            AppScreen.ChatList -> {
                popBackStack(route = "recipients", inclusive = false)
                return
            }
            AppScreen.Settings -> "functions"
            is AppScreen.Chat -> "chat/${screen.endpointValue}"
            is AppScreen.Call -> "call/${screen.endpointValue}"
        }
    navigate(route) { popUpTo("recipients") }
}

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    ChatAppTheme {
        RecipientsScreen(onRecipientClick = {})
    }
}
