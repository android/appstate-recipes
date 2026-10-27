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
import com.android.example.github.api.ApiResponse
import com.android.example.github.api.GithubService
import com.android.example.github.api.RepoSearchResponse
import com.android.example.github.db.GithubDb
import com.android.example.github.db.RepoDao
import com.android.example.github.util.ApiUtil.successCall
import com.android.example.github.util.InstantAppExecutors
import com.android.example.github.util.MainDispatcherRule
import com.android.example.github.util.TestUtil
import com.android.example.github.util.test
import com.android.example.github.vo.Contributor
import com.android.example.github.vo.Repo
import com.android.example.github.vo.RepoSearchResult
import com.android.example.github.vo.Resource
import com.android.example.github.vo.Status
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.`when`
import org.mockito.Mockito.RETURNS_DEFAULTS
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(JUnit4::class)
class RepoRepositoryTest {
    private lateinit var repository: RepoRepository
    private val dao = mock(RepoDao::class.java)
    private val service = mock(GithubService::class.java)

    @Rule
    @JvmField
    val mainDispatcherRule = MainDispatcherRule()

    @Before
    fun init() {
        val db = mock(GithubDb::class.java) { invocation ->
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
        `when`(db.repoDao()).thenReturn(dao)
        repository = RepoRepository(InstantAppExecutors(), db, dao, service)
    }

    @Test
    fun loadRepoFromNetwork() = runTest {
        val repo = TestUtil.createRepo("foo", "bar", "desc")
        `when`(dao.load("foo", "bar")).thenReturn(flowOf(null, repo))
        val call = successCall(repo)
        `when`(service.getRepo("foo", "bar")).thenReturn(call)

        val observer = repository.loadRepo("foo", "bar").test(this)
        verify(dao).load("foo", "bar")
        verify(service).getRepo("foo", "bar")
        verify(dao).insert(repo)
        assertThat(observer.values.last()).isEqualTo(Resource.success(repo))
        observer.finish()
    }

    @Test
    fun loadContributors() = runTest {
        val repo = TestUtil.createRepo("foo", "bar", "desc")
        val contributor = TestUtil.createContributor(repo, "log", 3)
        val contributors = listOf(contributor)
        val call = successCall(contributors)

        `when`(dao.loadContributors("foo", "bar")).thenReturn(flowOf(emptyList(), contributors))
        `when`(service.getContributors("foo", "bar")).thenReturn(call)

        val observer = repository.loadContributors("foo", "bar").test(this)
        verify(dao).loadContributors("foo", "bar")
        verify(service).getContributors("foo", "bar")

        val captor = ArgumentCaptor.forClass(List::class.java) as ArgumentCaptor<List<Contributor>>
        verify(dao).insertContributors(captor.capture() ?: emptyList())
        assertThat(captor.value).hasSize(1)
        assertThat(captor.value[0].repoName).isEqualTo("bar")
        assertThat(captor.value[0].repoOwner).isEqualTo("foo")
        assertThat(observer.values.last()).isEqualTo(Resource.success(contributors))
        observer.finish()
    }

    @Test
    fun searchNextPage_null() = runTest {
        `when`(dao.findSearchResult("foo")).thenReturn(null)
        val observer = repository.searchNextPage("foo").test(this)
        verify(dao).findSearchResult("foo")
        observer.finish()
    }

    @Test
    fun search_fromDb() = runTest {
        val ids = arrayListOf(1, 2)
        val repoList = arrayListOf<Repo>()
        val dbResult = RepoSearchResult("foo", ids, 2, null)

        `when`(dao.search("foo")).thenReturn(flowOf(dbResult))
        `when`(dao.loadOrdered(ids)).thenReturn(flowOf(repoList))

        val observer = repository.search("foo").test(this)
        verify(service, never()).searchRepos("foo")
        assertThat(observer.values.last()).isEqualTo(Resource.success(repoList))
        observer.finish()
    }

    @Test
    fun search_fromServer() = runTest {
        val ids = arrayListOf(1, 2)
        val repo1 = TestUtil.createRepo(1, "owner", "repo 1", "desc 1")
        val repo2 = TestUtil.createRepo(2, "owner", "repo 2", "desc 2")
        val repoList = arrayListOf(repo1, repo2)
        val apiResponse = RepoSearchResponse(2, repoList)

        `when`(dao.search("foo")).thenReturn(flowOf(null, RepoSearchResult("foo", ids, 2, null)))
        `when`(dao.loadOrdered(ids)).thenReturn(flowOf(repoList))
        `when`(service.searchRepos("foo")).thenReturn(flowOf(ApiResponse.create(Response.success(apiResponse))))

        val observer = repository.search("foo").test(this)
        verify(service).searchRepos("foo")
        verify(dao).insertRepos(repoList)
        assertThat(observer.values.last()).isEqualTo(Resource.success(repoList))
        observer.finish()
    }

    @Test
    fun search_fromServer_error() = runTest {
        `when`(dao.search("foo")).thenReturn(flowOf(null))
        `when`(service.searchRepos("foo")).thenReturn(flowOf(ApiResponse.create(Exception("idk"))))

        val observer = repository.search("foo").test(this)
        assertThat(observer.values.last().status).isEqualTo(Status.ERROR)
        assertThat(observer.values.last().message).isEqualTo("idk")
        observer.finish()
    }
}
