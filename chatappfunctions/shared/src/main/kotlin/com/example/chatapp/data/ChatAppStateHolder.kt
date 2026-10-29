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
package com.example.chatapp.data

import androidx.appstate.statestore.StateStore
import androidx.appstate.statestore.StateStoreKey
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

/** A screen of the app. */
sealed interface AppScreen {
    /** The list of all conversations. */
    data object ChatList : AppScreen

    /** The AppFunctions settings screen. */
    data object Settings : AppScreen

    /** The conversation with the contact or group identified by [endpointValue]. */
    data class Chat(val endpointValue: String) : AppScreen

    /** An ongoing call with the contact identified by [endpointValue]. */
    data class Call(val endpointValue: String) : AppScreen
}

const val DEFAULT_USER_NAME = "Me"

/** Display name of the user of the app. Used as the sender name of outgoing messages. */
private object CurrentUserNameKey : StateStoreKey<String>(DEFAULT_USER_NAME)

/** The screen the app is showing. Kept in sync with navigation by `MainActivity`. */
private object CurrentScreenKey : StateStoreKey<AppScreen>(AppScreen.ChatList)

/**
 * Application-level state shared between the AppFunctions service and the UI.
 *
 * Both run in the app process and receive this singleton from Hilt, so a change made by an agent
 * through an AppFunction (such as `changeScreen` or `changeUser`) is immediately visible to the
 * activity, and the activity reports what the user is doing back through it.
 *
 * The state lives in the shared [StateStore] under `private` keys, so it can only be read or
 * changed through this class.
 */
@Stable
@Singleton
class ChatAppStateHolder @Inject constructor(
    private val stateStore: StateStore,
) {
    /** The display name of the user of the app. */
    val currentUserName: State<String> = stateStore.getState(CurrentUserNameKey)

    /** The screen the app is showing. */
    val currentScreen: State<AppScreen> = stateStore.getState(CurrentScreenKey)

    fun setCurrentUserName(name: String) {
        stateStore.setState(CurrentUserNameKey, name)
    }

    fun setCurrentScreen(screen: AppScreen) {
        stateStore.setState(CurrentScreenKey, screen)
    }
}

/** Provides the single [StateStore] shared by the whole application. */
@Module
@InstallIn(SingletonComponent::class)
object StateStoreModule {
    @Provides
    @Singleton
    fun provideStateStore(): StateStore = StateStore()
}
