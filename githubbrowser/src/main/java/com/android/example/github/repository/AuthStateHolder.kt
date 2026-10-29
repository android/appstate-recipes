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
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.snapshots.Snapshot
import com.android.example.github.vo.User
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Progress of the GitHub device-flow sign-in (requesting a code, waiting for the user to authorize
 * it, or a failure). This is not persisted.
 */
sealed interface LoginFlowState {
    data object Idle : LoginFlowState
    data object RequestingCode : LoginFlowState
    data class AwaitingAuthorization(
        val userCode: String,
        val verificationUri: String
    ) : LoginFlowState
    data class Error(val message: String) : LoginFlowState
}

// Note: `PersistToDataStore` writes values to disk as plain, unencrypted JSON. That's fine for a
// sample, but production apps should encrypt auth tokens before persisting them (for example,
// with a key from the Android Keystore and Tink):
// https://developer.android.com/privacy-and-security/keystore
@Serializable
private object AccessTokenKey : StateStoreKey<String>(""), PersistToDataStore

@Serializable
private object LoggedInUserLoginKey : StateStoreKey<String>(""), PersistToDataStore

private object LoggedInUserKey : StateStoreKey<User?>(
    defaultValue = null,
    autoClearKey = LoggedInUserLoginKey,
    shouldClearState = { stateStore ->
        stateStore.getState(LoggedInUserLoginKey).value.isBlank()
    }
)

private object LoginFlowStateKey : StateStoreKey<LoginFlowState>(LoginFlowState.Idle)

/**
 * Holds the app's authentication state in the shared [StateStore].
 *
 * The [StateStoreKey]s are `private`, so the only way to read or change authentication state is
 * through this class. Callers read the `State` properties, and every reader (the network layer,
 * repositories, and `ViewModel`s) sees the same values because they all live in one [StateStore].
 */
@Stable
@Singleton
class AuthStateHolder @Inject constructor(
    private val stateStore: StateStore
) {
    /** The OAuth access token, or `null` when signed out. */
    val accessToken: State<String?> = derivedStateOf {
        stateStore.getState(AccessTokenKey).value.ifBlank { null }
    }

    /** The signed-in user's login, or `null` when signed out. */
    val loggedInUserLogin: State<String?> = derivedStateOf {
        stateStore.getState(LoggedInUserLoginKey).value.ifBlank { null }
    }

    /**
     * The signed-in [User] fetched after sign-in, or `null` if not yet loaded or signed out.
     *
     * [LoggedInUserKey] uses `autoClearKey`, so [StateStore] removes its entry when the login is
     * cleared and creates a new `State` on the next write. Look the key up on every read instead of
     * caching the `State` returned by [StateStore.getState], which would stop updating.
     */
    val loggedInUser: State<User?> = derivedStateOf {
        stateStore.getState(LoggedInUserKey).value
    }

    val isLoggedIn: State<Boolean> = derivedStateOf {
        accessToken.value != null && loggedInUserLogin.value != null
    }

    val loginFlowState: State<LoginFlowState> = stateStore.getState(LoginFlowStateKey)

    /**
     * Registers the persisted keys ([AccessTokenKey] and [LoggedInUserLoginKey]) in [StateStore]
     * so that [androidx.appstate.datastore.syncToDataStore] restores them from disk at startup.
     */
    fun primeKeys() {
        stateStore.getState(AccessTokenKey)
        stateStore.getState(LoggedInUserLoginKey)
    }

    fun setAccessToken(token: String) {
        stateStore.setState(AccessTokenKey, token)
    }

    /** Records [user] as the signed-in user, updating the login and cached user together. */
    fun setLoggedInUser(user: User) {
        Snapshot.withMutableSnapshot {
            stateStore.setState(LoggedInUserLoginKey, user.login)
            stateStore.setState(LoggedInUserKey, user)
        }
    }

    fun setLoginFlowState(state: LoginFlowState) {
        stateStore.setState(LoginFlowStateKey, state)
    }

    /** Signs out by clearing all authentication state in a single atomic snapshot. */
    fun clear() {
        Snapshot.withMutableSnapshot {
            stateStore.setState(AccessTokenKey, "")
            stateStore.setState(LoggedInUserLoginKey, "")
            stateStore.setState(LoggedInUserKey, null)
            stateStore.setState(LoginFlowStateKey, LoginFlowState.Idle)
        }
    }
}
