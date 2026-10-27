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

package com.android.example.github.ui.repo

import androidx.appstate.statestore.StateStore
import com.google.common.truth.Truth.assertThat
import com.android.example.github.util.FakeRepoRepository
import com.android.example.github.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(JUnit4::class)
class RepoViewModelTest {

    @Rule
    @JvmField
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeRepoRepository()
    private lateinit var repoViewModel: RepoViewModel

    @Before
    fun init() {
        repoViewModel = RepoViewModel(repository, StateStore(), mainDispatcherRule.testDispatcher)
    }

    @Test
    fun testNull() {
        mainDispatcherRule.advance()
        assertThat(repoViewModel.repo.value).isNull()
        assertThat(repoViewModel.contributors.value).isNull()
        assertThat(repository.loadRepoCalls).isEmpty()
    }

    @Test
    fun dontFetchWithoutObservers() {
        repoViewModel.setId("a", "b")
        mainDispatcherRule.advance()
        assertThat(repository.loadRepoCalls).containsExactly("a" to "b")
    }

    @Test
    fun fetchWhenObserved() = runTest {
        repoViewModel.setId("a", "b")
        mainDispatcherRule.advance()
        assertThat(repository.loadRepoCalls).containsExactly("a" to "b")
    }

    @Test
    fun changeWhileObserved() = runTest {
        repoViewModel.setId("a", "b")
        mainDispatcherRule.advance()
        repoViewModel.setId("c", "d")
        mainDispatcherRule.advance()

        assertThat(repository.loadRepoCalls).containsExactly("a" to "b", "c" to "d")
    }

    @Test
    fun contributors() = runTest {
        mainDispatcherRule.advance()
        assertThat(repository.loadContributorsCalls).isEmpty()
        repoViewModel.setId("foo", "bar")
        mainDispatcherRule.advance()
        assertThat(repository.loadContributorsCalls).containsExactly("foo" to "bar")
    }

    @Test
    fun resetId() = runTest {
        assertThat(repoViewModel.repoId.value).isNull()
        repoViewModel.setId("foo", "bar")
        mainDispatcherRule.advance()
        assertThat(repoViewModel.repoId.value).isEqualTo(RepoViewModel.RepoId("foo", "bar"))
        repoViewModel.setId("foo", "bar")
        mainDispatcherRule.advance()
        repoViewModel.setId("a", "b")
        mainDispatcherRule.advance()
        assertThat(repoViewModel.repoId.value).isEqualTo(RepoViewModel.RepoId("a", "b"))
    }

    @Test
    fun retry() = runTest {
        repoViewModel.retry()
        mainDispatcherRule.advance()
        assertThat(repository.loadRepoCalls).isEmpty()
        repoViewModel.setId("foo", "bar")
        mainDispatcherRule.advance()
        assertThat(repository.loadRepoCalls).containsExactly("foo" to "bar")
        repository.loadRepoCalls.clear()
        repoViewModel.retry()
        mainDispatcherRule.advance()
        assertThat(repository.loadRepoCalls).containsExactly("foo" to "bar")
    }

    @Test
    fun blankRepoId() = runTest {
        repoViewModel.setId("", "")
        mainDispatcherRule.advance()
        assertThat(repoViewModel.repo.value).isNull()
        assertThat(repoViewModel.contributors.value).isNull()
        assertThat(repository.loadRepoCalls).isEmpty()
        assertThat(repository.loadContributorsCalls).isEmpty()
    }
}
