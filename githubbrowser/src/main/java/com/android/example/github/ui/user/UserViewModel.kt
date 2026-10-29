/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.example.github.ui.user

import androidx.appstate.transform.transform
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.example.github.repository.AuthRepository
import com.android.example.github.repository.AuthStateHolder
import com.android.example.github.repository.RepoRepository
import com.android.example.github.repository.UserRepository
import com.android.example.github.vo.Repo
import com.android.example.github.vo.Resource
import com.android.example.github.vo.User
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

@HiltViewModel
class UserViewModel @Inject constructor(
    userRepository: UserRepository,
    repoRepository: RepoRepository,
    private val authRepository: AuthRepository,
    private val authState: AuthStateHolder,
    private val dispatcher: CoroutineDispatcher
) : ViewModel() {

    private val _login = mutableStateOf<String?>(null)
    val login: State<String?> = _login
    private val retryTrigger = mutableIntStateOf(0)

    val loggedInUserLogin: State<String?> = authState.loggedInUserLogin

    val isCurrentUser: State<Boolean> = derivedStateOf {
        val currentLogin = _login.value
        !currentLogin.isNullOrBlank() && currentLogin == authState.loggedInUserLogin.value
    }

    val user: State<Resource<User>?> = loadForLogin(userRepository::loadUser)

    val repositories: State<Resource<List<Repo>>?> = loadForLogin(repoRepository::loadRepos)

    fun setLogin(login: String?) {
        _login.value = login
    }

    fun retry() {
        retryTrigger.intValue++
    }

    fun logout() {
        authRepository.logout()
    }

    /**
     * Loads [load] for the current login, reloading on [retry] and whenever the signed-in user
     * changes so requests pick up the new authentication state.
     */
    private fun <T> loadForLogin(load: (String) -> Flow<Resource<T>>): State<Resource<T>?> =
        transform(initialValue = null, scope = viewModelScope, dispatcher = dispatcher) {
            val currentLogin = _login.value
            val trigger = retryTrigger.intValue
            val activeLogin = authState.loggedInUserLogin.value
            if (currentLogin == null) {
                null
            } else {
                remember(currentLogin, trigger, activeLogin) { load(currentLogin) }
                    .collectAsState(initial = null).value
            }
        }
}
