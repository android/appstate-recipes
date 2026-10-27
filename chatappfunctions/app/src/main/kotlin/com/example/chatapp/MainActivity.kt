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
import androidx.appstate.statestore.StateStore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.example.chatapp.data.currentScreen
import com.example.chatapp.data.setCurrentScreen
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
    @Inject lateinit var stateStore: StateStore

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

                // Keep the shared StateStore and navigation in sync: show the screen when it is
                // changed outside the UI (for example by an agent calling the changeScreen
                // AppFunction), and report the screen the user navigates to.
                val currentScreen = stateStore.currentScreen().value
                LaunchedEffect(currentScreen) {
                    val shownScreen = navController.currentBackStackEntry?.toAppScreen()
                    if (shownScreen != null && shownScreen != currentScreen) {
                        navController.show(currentScreen)
                    }
                }
                val currentEntry by navController.currentBackStackEntryAsState()
                LaunchedEffect(currentEntry) {
                    // Read the live entry: the effect above may have just navigated.
                    navController.currentBackStackEntry?.toAppScreen()?.let(stateStore::setCurrentScreen)
                }

                NavHost(navController = navController, startDestination = "recipients") {
                    composable(
                        route = "recipients",
                        deepLinks = listOf(navDeepLink { uriPattern = "app://com.example.chatapp/recipients" }),
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
                        deepLinks = listOf(navDeepLink { uriPattern = "app://com.example.chatapp/functions" }),
                    ) {
                        AppFunctionsScreen(
                            onBackClick = { navController.popBackStack() },
                        )
                    }
                    composable(
                        route = "chat/{recipientId}",
                        arguments = listOf(navArgument("recipientId") { type = NavType.StringType }),
                        deepLinks = listOf(navDeepLink { uriPattern = "app://com.example.chatapp/chat/{recipientId}" }),
                    ) { backStackEntry ->
                        val recipientId = backStackEntry.arguments?.getString("recipientId") ?: "bot"
                        ChatScreen(
                            recipientId = recipientId,
                            onCallClick = { navController.navigate("call/$recipientId") },
                            onBackClick = { navController.popBackStack() },
                        )
                    }
                    composable(
                        route = "call/{recipientId}",
                        arguments = listOf(navArgument("recipientId") { type = NavType.StringType }),
                        deepLinks = listOf(navDeepLink { uriPattern = "app://com.example.chatapp/call/{recipientId}" }),
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
        "chat/{recipientId}" -> recipientId?.let(AppScreen::Chat)
        "call/{recipientId}" -> recipientId?.let(AppScreen::Call)
        else -> null
    }
}

private fun NavController.show(screen: AppScreen) {
    when (screen) {
        AppScreen.ChatList -> popBackStack(route = "recipients", inclusive = false)
        AppScreen.Settings -> navigate("functions") { launchSingleTop = true }
        is AppScreen.Chat -> navigate("chat/${screen.endpointValue}") { launchSingleTop = true }
        is AppScreen.Call -> navigate("call/${screen.endpointValue}") { launchSingleTop = true }
    }
}

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    ChatAppTheme {
        RecipientsScreen(onRecipientClick = {})
    }
}
