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

import androidx.appstate.statestore.StateStore
import com.google.common.truth.Truth.assertThat
import com.android.example.github.api.ApiResponse
import com.android.example.github.api.GithubService
import com.android.example.github.repository.AuthRepository
import com.android.example.github.repository.AuthStateHolder
import com.android.example.github.repository.LoginFlowState
import com.android.example.github.ui.repo.RepoViewModel
import com.android.example.github.ui.user.UserViewModel
import com.android.example.github.util.ApiUtil
import com.android.example.github.util.FakeGithubAuthService
import com.android.example.github.util.FakeRepoRepository
import com.android.example.github.util.FakeUserDao
import com.android.example.github.util.FakeUserRepository
import com.android.example.github.util.InstantAppExecutors
import com.android.example.github.util.MainDispatcherRule
import com.android.example.github.util.TestUtil
import com.android.example.github.util.dummyGithubService
import com.android.example.github.vo.Repo
import com.android.example.github.vo.Resource
import com.android.example.github.vo.User
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.lang.reflect.Proxy

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(JUnit4::class)
class SearchViewModelTest {
    @Rule
    @JvmField
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeRepoRepository()
    private val userRepository = FakeUserRepository()
    private val authService = FakeGithubAuthService()
    private val authState = AuthStateHolder(StateStore())
    private val userDao = FakeUserDao()
    private val octocatUser = TestUtil.createUser("octocat")
    private val authenticatedUserFlow: Flow<ApiResponse<User>> = ApiUtil.successCall(octocatUser)

    private val githubService: GithubService = Proxy.newProxyInstance(
        GithubService::class.java.classLoader,
        arrayOf(GithubService::class.java)
    ) { _, method, args ->
        if (method.name == "getAuthenticatedUser") {
            authenticatedUserFlow
        } else {
            method.invoke(dummyGithubService, *(args ?: emptyArray()))
        }
    } as GithubService

    private lateinit var authRepository: AuthRepository
    private lateinit var viewModel: SearchViewModel

    @Before
    fun init() {
        authRepository = AuthRepository(
            appExecutors = InstantAppExecutors(),
            githubAuthService = authService,
            githubService = githubService,
            userDao = userDao,
            authState = authState
        )
        viewModel = SearchViewModel(
            repoRepository = repository,
            dispatcher = mainDispatcherRule.testDispatcher,
            authRepository = authRepository,
            authState = authState,
            userRepository = userRepository
        )
    }

    @Test
    fun empty() = runTest {
        mainDispatcherRule.advance()
        viewModel.loadNextPage()
        mainDispatcherRule.advance()
        assertThat(repository.searchCalls).isEmpty()
        assertThat(repository.searchNextPageCalls).isEmpty()
    }

    @Test
    fun basic() = runTest {
        mainDispatcherRule.advance()
        viewModel.setQuery("foo")
        mainDispatcherRule.advance()
        assertThat(repository.searchCalls).containsExactly("foo")
        assertThat(repository.searchNextPageCalls).isEmpty()
    }

    @Test
    fun noObserverNoQuery() = runTest {
        repository.setNextPageFlow("foo", flowOf(Resource.success(true)))
        viewModel.setQuery("foo")
        mainDispatcherRule.advance()
        assertThat(repository.searchCalls).containsExactly("foo")
        viewModel.loadNextPage()
        mainDispatcherRule.advance()
        assertThat(repository.searchNextPageCalls).containsExactly("foo")
    }

    @Test
    fun swap() = runTest {
        val nextPage = MutableSharedFlow<Resource<Boolean>>(replay = 1)
        repository.setNextPageFlow("foo", nextPage)

        mainDispatcherRule.advance()
        assertThat(repository.searchCalls).isEmpty()
        assertThat(repository.searchNextPageCalls).isEmpty()

        viewModel.setQuery("foo")
        mainDispatcherRule.advance()
        assertThat(repository.searchCalls).containsExactly("foo")

        viewModel.loadNextPage()
        mainDispatcherRule.advance()

        assertThat(repository.searchNextPageCalls).containsExactly("foo")
        assertThat(viewModel.loadMoreStatus.value.isRunning).isTrue()

        viewModel.setQuery("bar")
        mainDispatcherRule.advance()
        assertThat(repository.searchCalls).containsExactly("foo", "bar")
        assertThat(repository.searchNextPageCalls).containsExactly("foo")
    }

    @Test
    fun refresh() = runTest {
        viewModel.refresh()
        mainDispatcherRule.advance()
        assertThat(repository.searchCalls).isEmpty()

        viewModel.setQuery("foo")
        mainDispatcherRule.advance()
        assertThat(repository.searchCalls).containsExactly("foo")

        repository.searchCalls.clear()
        viewModel.refresh()
        mainDispatcherRule.advance()
        assertThat(repository.searchCalls).containsExactly("foo")
    }

    @Test
    fun resetSameQuery() = runTest {
        viewModel.setQuery("foo")
        mainDispatcherRule.advance()
        assertThat(repository.searchCalls).containsExactly("foo")

        repository.searchCalls.clear()
        viewModel.setQuery("FOO")
        mainDispatcherRule.advance()
        assertThat(repository.searchCalls).isEmpty()

        viewModel.setQuery("bar")
        mainDispatcherRule.advance()
        assertThat(repository.searchCalls).containsExactly("bar")
    }

    @Test
    fun results() = runTest {
        val repos = listOf(TestUtil.createRepo("foo", "name", "desc"))
        val repoFlow = MutableSharedFlow<Resource<List<Repo>>>(replay = 1)
        repository.setSearchFlow("foo", repoFlow)

        viewModel.setQuery("foo")
        mainDispatcherRule.advance()
        assertThat(viewModel.results.value).isNull()
        assertThat(repository.searchCalls).containsExactly("foo")

        repoFlow.emit(Resource.loading(null))
        mainDispatcherRule.advance()
        assertThat(viewModel.results.value).isEqualTo(Resource.loading(null))
        assertThat(repository.searchCalls).containsExactly("foo")

        repoFlow.emit(Resource.success(repos))
        mainDispatcherRule.advance()
        assertThat(viewModel.results.value).isEqualTo(Resource.success(repos))
        assertThat(repository.searchCalls).containsExactly("foo")

        // Clearing query stops search and resets results to null
        viewModel.setQuery("")
        mainDispatcherRule.advance()
        assertThat(viewModel.query.value).isEmpty()
        assertThat(viewModel.results.value).isNull()
    }

    @Test
    fun loginAndLogout() = runTest {
        userRepository.setUserFlow("octocat", flowOf(Resource.success(octocatUser)))
        mainDispatcherRule.advance()

        assertThat(viewModel.loggedInUserLogin.value).isNull()
        assertThat(viewModel.loggedInUser.value).isNull()
        assertThat(viewModel.loginState.value).isEqualTo(LoginFlowState.Idle)

        viewModel.startLogin()
        mainDispatcherRule.advance()
        assertThat(viewModel.loginState.value).isEqualTo(
            LoginFlowState.AwaitingAuthorization(
                userCode = "ABCD-1234",
                verificationUri = "https://github.com/login/device"
            )
        )

        advanceTimeBy(1500L)
        mainDispatcherRule.advance()

        assertThat(viewModel.loginState.value).isEqualTo(LoginFlowState.Idle)
        assertThat(authState.loginFlowState.value).isEqualTo(LoginFlowState.Idle)
        assertThat(viewModel.loggedInUserLogin.value).isEqualTo("octocat")
        assertThat(authState.loggedInUserLogin.value).isEqualTo("octocat")
        assertThat(authState.accessToken.value).isEqualTo("gho_test_token")
        assertThat(viewModel.loggedInUser.value).isEqualTo(octocatUser)
        assertThat(authState.loggedInUser.value).isEqualTo(octocatUser)

        viewModel.logout()
        mainDispatcherRule.advance()
        assertThat(viewModel.loggedInUserLogin.value).isNull()
        assertThat(viewModel.loggedInUser.value).isNull()
        assertThat(authState.loggedInUserLogin.value).isNull()
        assertThat(authState.accessToken.value).isNull()
        assertThat(authState.loggedInUser.value).isNull()
    }

    @Test
    fun stateStoreSynchronizesAuthAcrossComponents() = runTest {
        userRepository.setUserFlow("octocat", flowOf(Resource.success(octocatUser)))
        val userViewModel = UserViewModel(
            userRepository = userRepository,
            repoRepository = repository,
            dispatcher = mainDispatcherRule.testDispatcher,
            authRepository = authRepository,
            authState = authState
        )
        val repoViewModel = RepoViewModel(
            repository = repository,
            dispatcher = mainDispatcherRule.testDispatcher,
            authState = authState
        )

        userViewModel.setLogin("octocat")
        repoViewModel.setId("octocat", "hello-world")
        viewModel.setQuery("compose")
        mainDispatcherRule.advance()

        assertThat(userViewModel.isCurrentUser.value).isFalse()
        assertThat(repository.searchCalls).containsExactly("compose")
        assertThat(repository.loadRepoCalls).containsExactly("octocat" to "hello-world")
        assertThat(repository.loadReposCalls).containsExactly("octocat")

        // Logging in via SearchViewModel updates StateStore and synchronizes across UserViewModel & RepoViewModel
        viewModel.startLogin()
        mainDispatcherRule.advance()
        advanceTimeBy(1500L)
        mainDispatcherRule.advance()

        assertThat(viewModel.loggedInUserLogin.value).isEqualTo("octocat")
        assertThat(userViewModel.loggedInUserLogin.value).isEqualTo("octocat")
        assertThat(userViewModel.isCurrentUser.value).isTrue()
        assertThat(repository.searchCalls).containsExactly("compose", "compose")
        assertThat(repository.loadRepoCalls).containsExactly(
            "octocat" to "hello-world",
            "octocat" to "hello-world"
        )
        assertThat(repository.loadReposCalls).containsExactly("octocat", "octocat")

        // Logging out from UserViewModel updates StateStore and synchronizes back to SearchViewModel
        userViewModel.logout()
        mainDispatcherRule.advance()

        assertThat(userViewModel.isCurrentUser.value).isFalse()
        assertThat(userViewModel.loggedInUserLogin.value).isNull()
        assertThat(viewModel.loggedInUserLogin.value).isNull()
        assertThat(viewModel.loggedInUser.value).isNull()
        assertThat(authState.isLoggedIn.value).isFalse()
    }
}
