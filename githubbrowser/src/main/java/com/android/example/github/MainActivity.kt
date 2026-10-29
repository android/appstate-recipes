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

package com.android.example.github

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.android.example.github.ui.navigation.RepoRoute
import com.android.example.github.ui.navigation.SearchRoute
import com.android.example.github.ui.navigation.UserRoute
import com.android.example.github.ui.repo.RepoScreen
import com.android.example.github.ui.search.SearchScreen
import com.android.example.github.ui.theme.GithubTheme
import com.android.example.github.ui.user.UserScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            GithubTheme {
                val backStack = rememberNavBackStack(SearchRoute)
                val popBackStack = {
                    if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
                }

                NavDisplay(
                    backStack = backStack,
                    entryDecorators = listOf(
                        rememberSaveableStateHolderNavEntryDecorator(),
                        rememberViewModelStoreNavEntryDecorator()
                    ),
                    onBack = { if (backStack.size > 1) popBackStack() else finish() },
                    entryProvider = entryProvider {
                        entry<SearchRoute> {
                            SearchScreen(
                                onRepoClick = { owner, name ->
                                    backStack.add(RepoRoute(owner = owner, name = name))
                                },
                                onUserClick = { login, avatarUrl ->
                                    backStack.add(UserRoute(login = login, avatarUrl = avatarUrl))
                                }
                            )
                        }
                        entry<RepoRoute> { key ->
                            RepoScreen(
                                owner = key.owner,
                                name = key.name,
                                onBack = popBackStack,
                                onContributorClick = { login, avatarUrl ->
                                    backStack.add(UserRoute(login = login, avatarUrl = avatarUrl))
                                }
                            )
                        }
                        entry<UserRoute> { key ->
                            UserScreen(
                                login = key.login,
                                avatarUrl = key.avatarUrl,
                                onBack = popBackStack,
                                onRepoClick = { owner, name ->
                                    backStack.add(RepoRoute(owner = owner, name = name))
                                }
                            )
                        }
                    }
                )
            }
        }
    }
}
