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

import com.google.common.truth.Truth.assertThat
import com.android.example.github.util.FakeRepoRepository
import com.android.example.github.util.FakeUserRepository
import com.android.example.github.util.MainDispatcherRule
import com.android.example.github.util.TestUtil
import com.android.example.github.util.fakeAuthRepository
import com.android.example.github.vo.Resource
import com.android.example.github.vo.User
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(JUnit4::class)
class UserViewModelTest {
    @Rule
    @JvmField
    val mainDispatcherRule = MainDispatcherRule()

    private val userRepository = FakeUserRepository()
    private val repoRepository = FakeRepoRepository()
    private lateinit var userViewModel: UserViewModel

    @Before
    fun init() {
        userViewModel = UserViewModel(
            userRepository,
            repoRepository,
            fakeAuthRepository(),
            mainDispatcherRule.testDispatcher
        )
    }

    @Test
    fun testNull() {
        mainDispatcherRule.advance()
        assertThat(userViewModel.user.value).isNull()
        assertThat(userRepository.loadUserCalls).isEmpty()
        userViewModel.setLogin("foo")
        mainDispatcherRule.advance()
        assertThat(userRepository.loadUserCalls).containsExactly("foo")
    }

    @Test
    fun testCallRepo() = runTest {
        userViewModel.setLogin("abc")
        mainDispatcherRule.advance()
        assertThat(userRepository.loadUserCalls).containsExactly("abc")
        userRepository.loadUserCalls.clear()
        userViewModel.setLogin("ddd")
        mainDispatcherRule.advance()
        assertThat(userRepository.loadUserCalls).containsExactly("ddd")
    }

    @Test
    fun sendResultToUI() = runTest {
        val foo = MutableSharedFlow<Resource<User>>(replay = 1)
        val bar = MutableSharedFlow<Resource<User>>(replay = 1)
        userRepository.setUserFlow("foo", foo)
        userRepository.setUserFlow("bar", bar)

        userViewModel.setLogin("foo")
        mainDispatcherRule.advance()

        val fooUser = TestUtil.createUser("foo")
        val fooValue = Resource.success(fooUser)
        foo.emit(fooValue)
        mainDispatcherRule.advance()

        assertThat(userViewModel.user.value).isEqualTo(fooValue)

        val barUser = TestUtil.createUser("bar")
        val barValue = Resource.success(barUser)
        bar.emit(barValue)
        userViewModel.setLogin("bar")
        mainDispatcherRule.advance()
        assertThat(userViewModel.user.value).isEqualTo(barValue)
    }

    @Test
    fun loadRepositories() = runTest {
        mainDispatcherRule.advance()
        assertThat(repoRepository.loadReposCalls).isEmpty()
        userViewModel.setLogin("foo")
        mainDispatcherRule.advance()
        assertThat(repoRepository.loadReposCalls).containsExactly("foo")
        repoRepository.loadReposCalls.clear()
        userViewModel.setLogin("bar")
        mainDispatcherRule.advance()
        assertThat(repoRepository.loadReposCalls).containsExactly("bar")
        assertThat(userRepository.loadUserCalls).containsExactly("foo", "bar")
    }

    @Test
    fun retry() = runTest {
        userViewModel.setLogin("foo")
        mainDispatcherRule.advance()
        assertThat(userRepository.loadUserCalls).containsExactly("foo")
        assertThat(repoRepository.loadReposCalls).containsExactly("foo")
        userRepository.loadUserCalls.clear()
        repoRepository.loadReposCalls.clear()

        userViewModel.retry()
        mainDispatcherRule.advance()
        assertThat(userRepository.loadUserCalls).containsExactly("foo")
        assertThat(repoRepository.loadReposCalls).containsExactly("foo")
    }

    @Test
    fun nullUser() = runTest {
        userViewModel.setLogin("foo")
        mainDispatcherRule.advance()
        userViewModel.setLogin(null)
        mainDispatcherRule.advance()
        assertThat(userViewModel.user.value).isNull()
    }

    @Test
    fun nullRepoList() = runTest {
        userViewModel.setLogin("foo")
        mainDispatcherRule.advance()
        userViewModel.setLogin(null)
        mainDispatcherRule.advance()
        assertThat(userViewModel.repositories.value).isNull()
    }

    @Test
    fun dontRefreshOnSameData() = runTest {
        assertThat(userViewModel.login.value).isNull()
        userViewModel.setLogin("foo")
        mainDispatcherRule.advance()
        assertThat(userViewModel.login.value).isEqualTo("foo")
        assertThat(userRepository.loadUserCalls).containsExactly("foo")

        userViewModel.setLogin("foo")
        mainDispatcherRule.advance()
        assertThat(userRepository.loadUserCalls).containsExactly("foo")

        userViewModel.setLogin("bar")
        mainDispatcherRule.advance()
        assertThat(userViewModel.login.value).isEqualTo("bar")
        assertThat(userRepository.loadUserCalls).containsExactly("foo", "bar")
    }

    @Test
    fun noRetryWithoutUser() {
        userViewModel.retry()
        mainDispatcherRule.advance()
        assertThat(userRepository.loadUserCalls).isEmpty()
        assertThat(repoRepository.loadReposCalls).isEmpty()
    }
}
