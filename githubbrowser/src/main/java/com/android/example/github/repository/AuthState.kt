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

package com.android.example.github.repository

import androidx.appstate.datastore.PersistToDataStore
import androidx.appstate.statestore.StateStore
import androidx.appstate.statestore.StateStoreKey
import androidx.compose.runtime.State
import com.android.example.github.ui.search.LoginUiState
import com.android.example.github.vo.User
import kotlinx.serialization.Serializable

@Serializable
object AccessTokenKey : StateStoreKey<String>(""), PersistToDataStore

@Serializable
object LoggedInUserLoginKey : StateStoreKey<String>(""), PersistToDataStore

object LoggedInUserKey : StateStoreKey<User?>(
    defaultValue = null,
    autoClearKey = LoggedInUserLoginKey,
    shouldClearState = { stateStore ->
        stateStore.loggedInUserLogin().value.isBlank()
    }
)

object LoginUiStateKey : StateStoreKey<LoginUiState>(LoginUiState.Idle)

fun StateStore.accessToken(): State<String> = getState(AccessTokenKey)

fun StateStore.setAccessToken(token: String?) {
    setState(AccessTokenKey, token.orEmpty())
}

fun StateStore.loggedInUserLogin(): State<String> = getState(LoggedInUserLoginKey)

fun StateStore.setLoggedInUserLogin(login: String?) {
    setState(LoggedInUserLoginKey, login.orEmpty())
}

fun StateStore.loggedInUser(): State<User?> = getState(LoggedInUserKey)

fun StateStore.setLoggedInUser(user: User?) {
    setState(LoggedInUserKey, user)
}

fun StateStore.loginUiState(): State<LoginUiState> = getState(LoginUiStateKey)

fun StateStore.setLoginUiState(state: LoginUiState) {
    setState(LoginUiStateKey, state)
}

fun StateStore.clearAuthState() {
    setState(AccessTokenKey, "")
    setState(LoggedInUserLoginKey, "")
    setState(LoggedInUserKey, null)
    setState(LoginUiStateKey, LoginUiState.Idle)
}
