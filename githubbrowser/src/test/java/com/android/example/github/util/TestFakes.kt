/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.example.github.util

import androidx.room.InvalidationTracker
import com.android.example.github.api.AccessTokenResponse
import com.android.example.github.api.ApiResponse
import com.android.example.github.api.DeviceCodeResponse
import com.android.example.github.api.GithubAuthService
import com.android.example.github.api.GithubService
import com.android.example.github.db.GithubDb
import com.android.example.github.db.RepoDao
import com.android.example.github.db.UserDao
import androidx.appstate.statestore.StateStore
import com.android.example.github.repository.AuthRepository
import com.android.example.github.repository.AuthStateHolder
import com.android.example.github.repository.RepoRepository
import com.android.example.github.repository.UserRepository
import com.android.example.github.vo.Contributor
import com.android.example.github.vo.Repo
import com.android.example.github.vo.RepoSearchResult
import com.android.example.github.vo.Resource
import com.android.example.github.vo.User
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import java.lang.reflect.Proxy

open class FakeUserDao : UserDao() {
    val insertedUsers = mutableListOf<User>()
    override fun insert(user: User) {
        insertedUsers.add(user)
    }
    override fun findByLogin(login: String): Flow<User?> = emptyFlow()
}

open class FakeRepoDao : RepoDao() {
    override fun insert(vararg repos: Repo) {}
    override fun insert(result: RepoSearchResult) {}
    override fun insertContributors(contributors: List<Contributor>) {}
    override fun insertRepos(repositories: List<Repo>) {}
    override fun createRepoIfNotExists(repo: Repo): Long = 0L
    override fun load(ownerLogin: String, name: String): Flow<Repo?> = emptyFlow()
    override fun loadContributors(owner: String, name: String): Flow<List<Contributor>> = emptyFlow()
    override fun loadRepositories(owner: String): Flow<List<Repo>> = emptyFlow()
    override fun search(query: String): Flow<RepoSearchResult?> = emptyFlow()
    override fun loadById(repoIds: List<Int>): Flow<List<Repo>> = emptyFlow()
    override fun findSearchResult(query: String): RepoSearchResult? = null
}

open class FakeGithubDb(
    private val userDao: UserDao = FakeUserDao(),
    private val repoDao: RepoDao = FakeRepoDao()
) : GithubDb() {
    override fun userDao(): UserDao = userDao
    override fun repoDao(): RepoDao = repoDao
    override fun clearAllTables() {}
    override fun createInvalidationTracker(): InvalidationTracker {
        return InvalidationTracker(this, emptyMap(), emptyMap())
    }
    override fun runInTransaction(body: Runnable) {
        body.run()
    }
}

val dummyGithubService: GithubService = Proxy.newProxyInstance(
    GithubService::class.java.classLoader,
    arrayOf(GithubService::class.java)
) { _, _, _ -> emptyFlow<Any>() } as GithubService

fun fakeAuthRepository(authState: AuthStateHolder = AuthStateHolder(StateStore())) = AuthRepository(
    appExecutors = InstantAppExecutors(),
    githubAuthService = FakeGithubAuthService(),
    githubService = dummyGithubService,
    userDao = FakeUserDao(),
    authState = authState
)

class FakeGithubAuthService : GithubAuthService {
    var deviceCodeResponse: Result<DeviceCodeResponse> = Result.success(
        DeviceCodeResponse(
            deviceCode = "device_123",
            userCode = "ABCD-1234",
            verificationUri = "https://github.com/login/device",
            expiresIn = 900,
            interval = 1
        )
    )
    val pollResponses = ArrayDeque<Result<AccessTokenResponse>>()
    val requestedClientIds = mutableListOf<String>()
    val polledDeviceCodes = mutableListOf<String>()

    override suspend fun requestDeviceCode(clientId: String, scope: String): DeviceCodeResponse {
        requestedClientIds.add(clientId)
        return deviceCodeResponse.getOrThrow()
    }

    override suspend fun pollAccessToken(
        clientId: String,
        deviceCode: String,
        grantType: String
    ): AccessTokenResponse {
        polledDeviceCodes.add(deviceCode)
        val next = pollResponses.removeFirstOrNull()
            ?: Result.success(AccessTokenResponse(accessToken = "gho_test_token", tokenType = "bearer"))
        return next.getOrThrow()
    }
}

class FakeUserRepository : UserRepository(
    InstantAppExecutors(),
    FakeUserDao(),
    dummyGithubService
) {
    val loadUserCalls = mutableListOf<String>()
    private val userFlows = mutableMapOf<String, Flow<Resource<User>>>()
    var defaultUserFlow: Flow<Resource<User>> = emptyFlow()

    fun setUserFlow(login: String, flow: Flow<Resource<User>>) {
        userFlows[login] = flow
    }

    override fun loadUser(login: String): Flow<Resource<User>> {
        loadUserCalls.add(login)
        val source = userFlows[login] ?: defaultUserFlow
        return flow { emitAll(source) }
    }
}

class FakeRepoRepository : RepoRepository(
    InstantAppExecutors(),
    FakeGithubDb(),
    FakeRepoDao(),
    dummyGithubService
) {
    val loadReposCalls = mutableListOf<String>()
    val loadRepoCalls = mutableListOf<Pair<String, String>>()
    val loadContributorsCalls = mutableListOf<Pair<String, String>>()
    val searchCalls = mutableListOf<String>()
    val searchNextPageCalls = mutableListOf<String>()

    private val reposFlows = mutableMapOf<String, Flow<Resource<List<Repo>>>>()
    private val repoFlows = mutableMapOf<Pair<String, String>, Flow<Resource<Repo>>>()
    private val contributorsFlows = mutableMapOf<Pair<String, String>, Flow<Resource<List<Contributor>>>>()
    private val searchFlows = mutableMapOf<String, Flow<Resource<List<Repo>>>>()
    private val nextPageFlows = mutableMapOf<String, Flow<Resource<Boolean>>>()

    var defaultReposFlow: Flow<Resource<List<Repo>>> = emptyFlow()
    var defaultRepoFlow: Flow<Resource<Repo>> = emptyFlow()
    var defaultContributorsFlow: Flow<Resource<List<Contributor>>> = emptyFlow()
    var defaultSearchFlow: Flow<Resource<List<Repo>>> = emptyFlow()
    var defaultNextPageFlow: Flow<Resource<Boolean>> = emptyFlow()

    fun setReposFlow(owner: String, flow: Flow<Resource<List<Repo>>>) {
        reposFlows[owner] = flow
    }

    fun setRepoFlow(owner: String, name: String, flow: Flow<Resource<Repo>>) {
        repoFlows[owner to name] = flow
    }

    fun setContributorsFlow(owner: String, name: String, flow: Flow<Resource<List<Contributor>>>) {
        contributorsFlows[owner to name] = flow
    }

    fun setSearchFlow(query: String, flow: Flow<Resource<List<Repo>>>) {
        searchFlows[query] = flow
    }

    fun setNextPageFlow(query: String, flow: Flow<Resource<Boolean>>) {
        nextPageFlows[query] = flow
    }

    override fun loadRepos(owner: String): Flow<Resource<List<Repo>>> {
        loadReposCalls.add(owner)
        val source = reposFlows[owner] ?: defaultReposFlow
        return flow { emitAll(source) }
    }

    override fun loadRepo(owner: String, name: String): Flow<Resource<Repo>> {
        loadRepoCalls.add(owner to name)
        val source = repoFlows[owner to name] ?: defaultRepoFlow
        return flow { emitAll(source) }
    }

    override fun loadContributors(owner: String, name: String): Flow<Resource<List<Contributor>>> {
        loadContributorsCalls.add(owner to name)
        val source = contributorsFlows[owner to name] ?: defaultContributorsFlow
        return flow { emitAll(source) }
    }

    override fun search(query: String): Flow<Resource<List<Repo>>> {
        searchCalls.add(query)
        val source = searchFlows[query] ?: defaultSearchFlow
        return flow { emitAll(source) }
    }

    override fun searchNextPage(query: String): Flow<Resource<Boolean>> {
        searchNextPageCalls.add(query)
        return nextPageFlows[query] ?: defaultNextPageFlow
    }
}
