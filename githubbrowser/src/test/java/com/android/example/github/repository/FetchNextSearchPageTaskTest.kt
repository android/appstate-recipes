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

package com.android.example.github.repository

import com.google.common.truth.Truth.assertThat
import com.android.example.github.api.GithubService
import com.android.example.github.api.RepoSearchResponse
import com.android.example.github.db.GithubDb
import com.android.example.github.db.RepoDao
import com.android.example.github.util.TestUtil
import com.android.example.github.vo.RepoSearchResult
import com.android.example.github.vo.Resource
import okhttp3.Headers.Companion.headersOf
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mockito.`when`
import org.mockito.Mockito.RETURNS_DEFAULTS
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import retrofit2.Call
import retrofit2.Response
import java.io.IOException

@RunWith(JUnit4::class)
class FetchNextSearchPageTaskTest {

    private lateinit var service: GithubService
    private lateinit var db: GithubDb
    private lateinit var repoDao: RepoDao
    private lateinit var task: FetchNextSearchPageTask

    @Before
    fun init() {
        service = mock(GithubService::class.java)
        db = mock(GithubDb::class.java) { invocation ->
            // Room 2.7+ runInTransaction relies on internal state that a mock doesn't have,
            // so run the transaction body directly.
            val body = invocation.arguments.firstOrNull()
            if (invocation.method.name == "runInTransaction" && body is Runnable) {
                body.run()
                null
            } else {
                RETURNS_DEFAULTS.answer(invocation)
            }
        }
        repoDao = mock(RepoDao::class.java)
        `when`(db.repoDao()).thenReturn(repoDao)
        task = FetchNextSearchPageTask("foo", service, db)
    }

    @Test
    fun withoutResult() {
        `when`(repoDao.search("foo")).thenReturn(null)
        task.run()
        assertThat(task.flow.value).isNull()
        verifyNoMoreInteractions(service)
    }

    @Test
    fun noNextPage() {
        createDbResult(null)
        task.run()
        assertThat(task.flow.value).isEqualTo(Resource.success(false))
        verifyNoMoreInteractions(service)
    }

    @Test
    fun nextPageWithNull() {
        createDbResult(1)
        val repos = TestUtil.createRepos(10, "a", "b", "c")
        val result = RepoSearchResponse(10, repos)
        val call = createCall(result, null)
        `when`(service.searchRepos("foo", 1)).thenReturn(call)
        task.run()
        verify(repoDao).insertRepos(repos)
        assertThat(task.flow.value).isEqualTo(Resource.success(false))
    }

    @Test
    fun nextPageWithMore() {
        createDbResult(1)
        val repos = TestUtil.createRepos(10, "a", "b", "c")
        val result = RepoSearchResponse(10, repos)
        result.nextPage = 2
        val call = createCall(result, 2)
        `when`(service.searchRepos("foo", 1)).thenReturn(call)
        task.run()
        verify(repoDao).insertRepos(repos)
        assertThat(task.flow.value).isEqualTo(Resource.success(true))
    }

    @Test
    fun nextPageApiError() {
        createDbResult(1)
        val call = mock<Call<RepoSearchResponse>>()
        `when`(call.execute()).thenReturn(
            Response.error(
                400, "bar".toResponseBody("txt".toMediaTypeOrNull())
            )
        )
        `when`(service.searchRepos("foo", 1)).thenReturn(call)
        task.run()
        assertThat(task.flow.value).isEqualTo(Resource.error("bar", true))
    }

    @Test
    fun nextPageIOError() {
        createDbResult(1)
        val call = mock<Call<RepoSearchResponse>>()
        `when`(call.execute()).thenThrow(IOException("bar"))
        `when`(service.searchRepos("foo", 1)).thenReturn(call)
        task.run()
        assertThat(task.flow.value).isEqualTo(Resource.error("bar", true))
    }

    private fun createDbResult(nextPage: Int?) {
        val result = RepoSearchResult(
            "foo", emptyList(),
            0, nextPage
        )
        `when`(repoDao.findSearchResult("foo")).thenReturn(result)
    }

    private fun createCall(body: RepoSearchResponse, nextPage: Int?): Call<RepoSearchResponse> {
        val headers = if (nextPage == null)
            null
        else
            headersOf(
                "link",
                "<https://api.github.com/search/repositories?q=foo&page=" + nextPage
                        + ">; rel=\"next\""
            )
        val success = if (headers == null)
            Response.success(body)
        else
            Response.success(body, headers)
        val call = mock<Call<RepoSearchResponse>>()
        `when`(call.execute()).thenReturn(success)

        return call
    }
}
