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

import com.android.example.github.AppExecutors
import com.android.example.github.BuildConfig
import com.android.example.github.api.ApiErrorResponse
import com.android.example.github.api.ApiSuccessResponse
import com.android.example.github.api.DeviceCodeResponse
import com.android.example.github.api.GithubAuthService
import com.android.example.github.api.GithubService
import com.android.example.github.db.UserDao
import com.android.example.github.testing.OpenForTesting
import com.android.example.github.vo.User
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@OpenForTesting
@Singleton
class AuthRepository @Inject constructor(
    private val appExecutors: AppExecutors,
    private val githubAuthService: GithubAuthService,
    private val githubService: GithubService,
    private val userDao: UserDao,
    private val authState: AuthStateHolder
) {

    suspend fun requestDeviceCode(
        clientId: String = BuildConfig.GITHUB_CLIENT_ID
    ): Result<DeviceCodeResponse> {
        return try {
            val response = githubAuthService.requestDeviceCode(clientId = clientId)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun pollForAccessToken(
        deviceCode: String,
        intervalSeconds: Int,
        clientId: String = BuildConfig.GITHUB_CLIENT_ID
    ): Result<User> {
        var currentInterval = intervalSeconds.coerceAtLeast(1)
        while (true) {
            delay(currentInterval * 1000L)
            val tokenResponse = try {
                githubAuthService.pollAccessToken(
                    clientId = clientId,
                    deviceCode = deviceCode
                )
            } catch (e: Exception) {
                return Result.failure(e)
            }

            if (!tokenResponse.accessToken.isNullOrBlank()) {
                authState.setAccessToken(tokenResponse.accessToken)
                return fetchAndSaveAuthenticatedUser()
            }

            when (tokenResponse.error) {
                "authorization_pending" -> {
                    // Continue polling
                }
                "slow_down" -> {
                    currentInterval += 5
                }
                else -> {
                    val errorMsg = tokenResponse.errorDescription
                        ?: tokenResponse.error
                        ?: "Authentication failed"
                    return Result.failure(IllegalStateException(errorMsg))
                }
            }
        }
    }

    private suspend fun fetchAndSaveAuthenticatedUser(): Result<User> {
        return try {
            when (val apiResponse = githubService.getAuthenticatedUser().first()) {
                is ApiSuccessResponse -> {
                    val user = apiResponse.body
                    appExecutors.diskIO.execute {
                        userDao.insert(user)
                    }
                    authState.setLoggedInUser(user)
                    Result.success(user)
                }
                is ApiErrorResponse -> {
                    authState.clear()
                    Result.failure(IllegalStateException(apiResponse.errorMessage))
                }
                else -> {
                    authState.clear()
                    Result.failure(IllegalStateException("Empty user response from GitHub"))
                }
            }
        } catch (e: Exception) {
            authState.clear()
            Result.failure(e)
        }
    }

    fun logout() {
        authState.clear()
    }
}
