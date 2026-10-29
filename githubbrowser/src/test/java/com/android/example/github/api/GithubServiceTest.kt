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

package com.android.example.github.api

import com.google.common.truth.Truth.assertThat
import com.android.example.github.util.FlowCallAdapterFactory
import com.android.example.github.vo.User
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.buffer
import okio.source
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

@RunWith(JUnit4::class)
class GithubServiceTest {
    private lateinit var service: GithubService

    private lateinit var mockWebServer: MockWebServer

    @Before
    fun createService() {
        mockWebServer = MockWebServer()
        service = Retrofit.Builder()
            .baseUrl(mockWebServer.url("/"))
            .addConverterFactory(GsonConverterFactory.create())
            .addCallAdapterFactory(FlowCallAdapterFactory())
            .build()
            .create(GithubService::class.java)
    }

    @After
    fun stopService() {
        mockWebServer.shutdown()
    }

    @Test
    fun getUser() {
        enqueueResponse("user-yigit.json")
        val yigit = (runBlocking { service.getUser("yigit").first() } as ApiSuccessResponse).body

        val request = mockWebServer.takeRequest()
        assertThat(request.path).isEqualTo("/users/yigit")

        assertThat(yigit).isNotNull()
        assertThat(yigit.avatarUrl).isEqualTo("https://avatars3.githubusercontent.com/u/89202?v=3")
        assertThat(yigit.company).isEqualTo("Google")
        assertThat(yigit.blog).isEqualTo("birbit.com")
    }

    @Test
    fun getRepos() {
        enqueueResponse("repos-yigit.json")
        val repos = (runBlocking { service.getRepos("yigit").first() } as ApiSuccessResponse).body

        val request = mockWebServer.takeRequest()
        assertThat(request.path).isEqualTo("/users/yigit/repos")

        assertThat(repos).hasSize(2)

        val repo = repos[0]
        assertThat(repo.fullName).isEqualTo("yigit/AckMate")

        val owner = repo.owner
        assertThat(owner).isNotNull()
        assertThat(owner.login).isEqualTo("yigit")
        assertThat(owner.url).isEqualTo("https://api.github.com/users/yigit")

        val repo2 = repos[1]
        assertThat(repo2.fullName).isEqualTo("yigit/android-architecture")
    }

    @Test
    fun getContributors() {
        enqueueResponse("contributors.json")
        val value = runBlocking { service.getContributors("foo", "bar").first() }
        val contributors = (value as ApiSuccessResponse).body
        assertThat(contributors).hasSize(3)
        val yigit = contributors[0]
        assertThat(yigit.login).isEqualTo("yigit")
        assertThat(yigit.avatarUrl).isEqualTo("https://avatars3.githubusercontent.com/u/89202?v=3")
        assertThat(yigit.contributions).isEqualTo(291)
        assertThat(contributors[1].login).isEqualTo("guavabot")
        assertThat(contributors[2].login).isEqualTo("coltin")
    }

    @Test
    fun search() {
        val next = """<https://api.github.com/search/repositories?q=foo&page=2>; rel="next""""
        val last = """<https://api.github.com/search/repositories?q=foo&page=34>; rel="last""""
        enqueueResponse(
            "search.json", mapOf(
                "link" to "$next,$last"
            )
        )
        val response = runBlocking { service.searchRepos("foo").first() } as ApiSuccessResponse

        assertThat(response).isNotNull()
        assertThat(response.body.total).isEqualTo(41)
        assertThat(response.body.items).hasSize(30)
        assertThat(response.links["next"]).isEqualTo("https://api.github.com/search/repositories?q=foo&page=2")
        assertThat(response.nextPage).isEqualTo(2)
    }

    private fun enqueueResponse(fileName: String, headers: Map<String, String> = emptyMap()) {
        val inputStream = javaClass.classLoader!!
            .getResourceAsStream("api-response/$fileName")
        val source = inputStream.source().buffer()
        val mockResponse = MockResponse()
        for ((key, value) in headers) {
            mockResponse.addHeader(key, value)
        }
        mockWebServer.enqueue(
            mockResponse
                .setBody(source.readString(Charsets.UTF_8))
        )
    }
}
