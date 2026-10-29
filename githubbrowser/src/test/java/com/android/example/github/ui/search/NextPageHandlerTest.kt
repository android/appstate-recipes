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

import com.google.common.truth.Truth.assertThat
import com.android.example.github.util.FakeRepoRepository
import com.android.example.github.util.MainDispatcherRule
import com.android.example.github.vo.Resource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(JUnit4::class)
class NextPageHandlerTest {

    @Rule
    @JvmField
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeRepoRepository()

    private lateinit var pageHandler: SearchViewModel.NextPageHandler

    @Before
    fun init() {
        pageHandler = SearchViewModel.NextPageHandler(repository)
    }

    private val status: SearchViewModel.LoadMoreState?
        get() = pageHandler.loadMoreState.value

    @Test
    fun constructor() {
        val initial = status
        assertThat(initial).isNotNull()
        assertThat(initial?.isRunning).isFalse()
        assertThat(initial?.errorMessage).isNull()
    }

    @Test
    fun reloadSameValue() {
        enqueueResponse("foo")
        pageHandler.queryNextPage("foo")
        assertThat(repository.searchNextPageCalls).containsExactly("foo")

        repository.searchNextPageCalls.clear()
        pageHandler.queryNextPage("foo")
        assertThat(repository.searchNextPageCalls).isEmpty()
    }

    @Test
    fun success() {
        enqueueResponse("foo")

        pageHandler.queryNextPage("foo")
        assertThat(repository.searchNextPageCalls).containsExactly("foo")
        pageHandler.onNewValue(Resource.loading(null))
        assertThat(status?.isRunning).isTrue()

        pageHandler.onNewValue(Resource.success(true))
        assertThat(pageHandler.hasMore).isTrue()
        assertThat(status?.isRunning).isFalse()

        // requery
        repository.searchNextPageCalls.clear()
        enqueueResponse("foo")
        pageHandler.queryNextPage("foo")
        assertThat(repository.searchNextPageCalls).containsExactly("foo")

        pageHandler.onNewValue(Resource.success(false))
        assertThat(pageHandler.hasMore).isFalse()
        assertThat(status?.isRunning).isFalse()

        // retry, no query
        repository.searchNextPageCalls.clear()
        pageHandler.queryNextPage("foo")
        assertThat(repository.searchNextPageCalls).isEmpty()
        pageHandler.queryNextPage("foo")
        assertThat(repository.searchNextPageCalls).isEmpty()

        // query another
        enqueueResponse("bar")
        pageHandler.queryNextPage("bar")
        assertThat(repository.searchNextPageCalls).containsExactly("bar")
    }

    @Test
    fun failure() {
        enqueueResponse("foo")
        pageHandler.queryNextPage("foo")
        pageHandler.onNewValue(Resource.error("idk", false))
        assertThat(status?.errorMessage).isEqualTo("idk")
        assertThat(status?.errorMessageIfNotHandled).isEqualTo("idk")
        assertThat(status?.errorMessageIfNotHandled).isNull()
        assertThat(status?.isRunning).isFalse()
        assertThat(pageHandler.hasMore).isTrue()

        repository.searchNextPageCalls.clear()
        enqueueResponse("foo")
        pageHandler.queryNextPage("foo")
        assertThat(status?.isRunning).isTrue()
        pageHandler.onNewValue(Resource.success(false))
        assertThat(status?.isRunning).isFalse()
        assertThat(status?.errorMessage).isNull()
        assertThat(pageHandler.hasMore).isFalse()
    }

    @Test
    fun nullOnChanged() {
        enqueueResponse("foo")
        pageHandler.queryNextPage("foo")
        pageHandler.onNewValue(null)
    }

    private fun enqueueResponse(query: String): MutableSharedFlow<Resource<Boolean>> {
        val flow = MutableSharedFlow<Resource<Boolean>>(replay = 1)
        repository.setNextPageFlow(query, flow)
        return flow
    }
}
