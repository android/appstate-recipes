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

import androidx.appstate.statestore.StateStore
import com.google.common.truth.Truth.assertThat
import com.android.example.github.api.AccessTokenResponse
import com.android.example.github.api.ApiResponse
import com.android.example.github.api.AuthInterceptor
import com.android.example.github.api.GithubService
import com.android.example.github.util.ApiUtil
import com.android.example.github.util.FakeGithubAuthService
import com.android.example.github.util.FakeUserDao
import com.android.example.github.util.InstantAppExecutors
import com.android.example.github.util.MainDispatcherRule
import com.android.example.github.util.TestUtil
import com.android.example.github.util.dummyGithubService
import com.android.example.github.vo.User
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.lang.reflect.Proxy

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(JUnit4::class)
class AuthRepositoryTest {
    @Rule
    @JvmField
    val mainDispatcherRule = MainDispatcherRule()

    private val userDao = FakeUserDao()
    private val authState = AuthStateHolder(StateStore())
    private val authService = FakeGithubAuthService()
    private var authenticatedUserFlow: Flow<ApiResponse<User>> =
        ApiUtil.successCall(TestUtil.createUser("octocat"))

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

    @Before
    fun init() {
        authRepository = AuthRepository(
            appExecutors = InstantAppExecutors(),
            githubAuthService = authService,
            githubService = githubService,
            userDao = userDao,
            authState = authState
        )
    }

    @Test
    fun requestDeviceCode() = runTest {
        val result = authRepository.requestDeviceCode(clientId = "test_client_id")
        assertThat(result.isSuccess).isTrue()
        assertThat(authService.requestedClientIds).containsExactly("test_client_id")
        assertThat(result.getOrNull()?.userCode).isEqualTo("ABCD-1234")
    }

    @Test
    fun pollForAccessTokenSuccessAfterPending() = runTest {
        authService.pollResponses.add(
            Result.success(AccessTokenResponse(error = "authorization_pending"))
        )
        authService.pollResponses.add(
            Result.success(AccessTokenResponse(accessToken = "gho_valid_token", tokenType = "bearer"))
        )

        val result = authRepository.pollForAccessToken(
            deviceCode = "device_123",
            intervalSeconds = 1,
            clientId = "test_client_id"
        )

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()?.login).isEqualTo("octocat")
        assertThat(authService.polledDeviceCodes).containsExactly("device_123", "device_123")
        assertThat(authState.accessToken.value).isEqualTo("gho_valid_token")
        assertThat(authState.loggedInUserLogin.value).isEqualTo("octocat")
        assertThat(authState.loggedInUser.value?.login).isEqualTo("octocat")
        assertThat(authState.isLoggedIn.value).isTrue()
        assertThat(userDao.insertedUsers.map { it.login }).containsExactly("octocat")
    }

    @Test
    fun pollForAccessTokenDenied() = runTest {
        authService.pollResponses.add(
            Result.success(
                AccessTokenResponse(
                    error = "access_denied",
                    errorDescription = "The user cancelled authorization"
                )
            )
        )

        val result = authRepository.pollForAccessToken(
            deviceCode = "device_123",
            intervalSeconds = 1,
            clientId = "test_client_id"
        )

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).isEqualTo("The user cancelled authorization")
        assertThat(authState.isLoggedIn.value).isFalse()
        assertThat(authState.loggedInUserLogin.value).isNull()
    }

    @Test
    fun logoutClearsState() = runTest {
        authRepository.pollForAccessToken(
            deviceCode = "device_123",
            intervalSeconds = 1,
            clientId = "test_client_id"
        )
        assertThat(authState.isLoggedIn.value).isTrue()

        authRepository.logout()
        assertThat(authState.isLoggedIn.value).isFalse()
        assertThat(authState.accessToken.value).isNull()
        assertThat(authState.loggedInUserLogin.value).isNull()
        assertThat(authState.loggedInUser.value).isNull()
    }

    @Test
    fun authInterceptorAttachesHeaderWhenTokenPresent() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        server.start()

        try {
            val client = OkHttpClient.Builder()
                .addInterceptor(AuthInterceptor(authState))
                .build()

            // Without token
            client.newCall(Request.Builder().url(server.url("/user")).build()).execute()
            val reqWithoutToken = server.takeRequest()
            assertThat(reqWithoutToken.getHeader("Authorization")).isNull()

            // With token in AuthStateHolder
            authState.setAccessToken("gho_secret_123")
            client.newCall(Request.Builder().url(server.url("/user")).build()).execute()
            val reqWithToken = server.takeRequest()
            assertThat(reqWithToken.getHeader("Authorization")).isEqualTo("Bearer gho_secret_123")
        } finally {
            server.shutdown()
        }
    }
}
