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

package com.android.example.github.ui.search

import androidx.appstate.transform.transform
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.example.github.repository.AuthRepository
import com.android.example.github.repository.AuthStateHolder
import com.android.example.github.repository.LoginFlowState
import com.android.example.github.repository.RepoRepository
import com.android.example.github.repository.UserRepository
import com.android.example.github.vo.Repo
import com.android.example.github.vo.Resource
import com.android.example.github.vo.Status
import com.android.example.github.vo.User
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repoRepository: RepoRepository,
    private val authRepository: AuthRepository,
    private val authState: AuthStateHolder,
    private val userRepository: UserRepository,
    private val dispatcher: CoroutineDispatcher
) : ViewModel() {

    private val _query = mutableStateOf("")
    val query: State<String> = _query
    private val refreshTrigger = mutableIntStateOf(0)

    val results: State<Resource<List<Repo>>?> = transform(
        initialValue = null,
        scope = viewModelScope,
        dispatcher = dispatcher
    ) {
        val currentQuery = _query.value
        val trigger = refreshTrigger.intValue
        val activeLogin = authState.loggedInUserLogin.value
        if (currentQuery.isEmpty()) {
            null
        } else {
            remember(currentQuery, trigger, activeLogin) { repoRepository.search(currentQuery) }
                .collectAsState(initial = null).value
        }
    }

    val loggedInUserLogin: State<String?> = authState.loggedInUserLogin

    val loggedInUser: State<User?> = transform(
        initialValue = null,
        scope = viewModelScope,
        dispatcher = dispatcher
    ) {
        val login = authState.loggedInUserLogin.value
        if (login == null) {
            null
        } else {
            remember(login) { userRepository.loadUser(login) }
                .collectAsState(initial = null).value?.data
                ?: authState.loggedInUser.value
        }
    }

    val loginState: State<LoginFlowState> = authState.loginFlowState
    private var loginJob: Job? = null

    val nextPageHandler = NextPageHandler(repoRepository, viewModelScope)

    val loadMoreStatus: State<LoadMoreState>
        get() = nextPageHandler.loadMoreState

    fun setQuery(originalInput: String) {
        val input = originalInput.lowercase().trim()
        if (input != _query.value) {
            nextPageHandler.reset()
            _query.value = input
        }
    }

    fun loadNextPage() {
        val current = _query.value
        if (current.isNotEmpty()) {
            nextPageHandler.queryNextPage(current)
        }
    }

    fun refresh() {
        if (_query.value.isNotEmpty()) {
            refreshTrigger.intValue++
        }
    }

    fun startLogin() {
        loginJob?.cancel()
        authState.setLoginFlowState(LoginFlowState.RequestingCode)
        loginJob = viewModelScope.launch(dispatcher) {
            val code = authRepository.requestDeviceCode().getOrElse { error ->
                authState.setLoginFlowState(
                    LoginFlowState.Error(error.message ?: "Failed to request device code")
                )
                return@launch
            }
            authState.setLoginFlowState(
                LoginFlowState.AwaitingAuthorization(code.userCode, code.verificationUri)
            )
            val newState = authRepository.pollForAccessToken(code.deviceCode, code.interval).fold(
                onSuccess = { LoginFlowState.Idle },
                onFailure = { LoginFlowState.Error(it.message ?: "Login failed") }
            )
            authState.setLoginFlowState(newState)
        }
    }

    fun cancelLogin() {
        loginJob?.cancel()
        loginJob = null
        authState.setLoginFlowState(LoginFlowState.Idle)
    }

    fun logout() {
        cancelLogin()
        authRepository.logout()
    }

    class LoadMoreState(
        val isRunning: Boolean,
        val errorMessage: String?
    ) {
        private var handledError = false

        val errorMessageIfNotHandled: String?
            get() = if (handledError) null else errorMessage.also { handledError = true }
    }

    class NextPageHandler(
        private val repository: RepoRepository,
        private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main.immediate)
    ) {
        private val _loadMoreState = mutableStateOf(LoadMoreState(isRunning = false, errorMessage = null))
        val loadMoreState: State<LoadMoreState> = _loadMoreState

        private var query: String? = null
        private var job: Job? = null
        var hasMore: Boolean = false
            private set

        fun queryNextPage(query: String) {
            if (this.query == query) {
                return
            }
            unregister()
            this.query = query
            hasMore = true
            _loadMoreState.value = LoadMoreState(isRunning = true, errorMessage = null)
            job = scope.launch {
                repository.searchNextPage(query).collect(::onNewValue)
            }
        }

        fun onNewValue(value: Resource<Boolean>?) {
            when (value?.status) {
                null -> reset()
                Status.SUCCESS -> finish(hasMore = value.data == true, errorMessage = null)
                Status.ERROR -> finish(hasMore = true, errorMessage = value.message)
                Status.LOADING -> Unit // keep loading
            }
        }

        fun reset() {
            unregister()
            hasMore = true
            _loadMoreState.value = LoadMoreState(isRunning = false, errorMessage = null)
        }

        private fun finish(hasMore: Boolean, errorMessage: String?) {
            this.hasMore = hasMore
            unregister()
            _loadMoreState.value = LoadMoreState(isRunning = false, errorMessage = errorMessage)
        }

        private fun unregister() {
            job?.cancel()
            job = null
            if (hasMore) {
                query = null
            }
        }
    }
}
