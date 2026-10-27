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
import com.android.example.github.util.ApiUtil
import com.android.example.github.util.CountingAppExecutors
import com.android.example.github.util.InstantAppExecutors
import com.android.example.github.util.MainDispatcherRule
import com.android.example.github.util.test
import com.android.example.github.vo.Resource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import retrofit2.Response
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(Parameterized::class)
class NetworkBoundResourceTest(private val useRealExecutors: Boolean) {

    @Rule
    @JvmField
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var handleSaveCallResult: (Foo) -> Unit
    private lateinit var handleShouldMatch: (Foo?) -> Boolean
    private lateinit var handleCreateCall: () -> Flow<ApiResponse<Foo>>

    private val dbData = MutableStateFlow<Foo?>(null)
    private lateinit var networkBoundResource: NetworkBoundResource<Foo, Foo>
    private val fetchedOnce = AtomicBoolean(false)
    private lateinit var countingAppExecutors: CountingAppExecutors

    init {
        if (useRealExecutors) {
            countingAppExecutors = CountingAppExecutors()
        }
    }

    @Before
    fun init() {
        val appExecutors = if (useRealExecutors)
            countingAppExecutors.appExecutors
        else
            InstantAppExecutors()
        networkBoundResource = object : NetworkBoundResource<Foo, Foo>(appExecutors) {
            override fun saveCallResult(item: Foo) {
                handleSaveCallResult(item)
            }

            override fun shouldFetch(data: Foo?): Boolean {
                return handleShouldMatch(data) && fetchedOnce.compareAndSet(false, true)
            }

            override fun loadFromDb(): Flow<Foo?> {
                return dbData
            }

            override fun createCall(): Flow<ApiResponse<Foo>> {
                return handleCreateCall()
            }
        }
    }

    private fun drain() {
        if (!useRealExecutors) {
            return
        }
        try {
            countingAppExecutors.drainTasks(1, java.util.concurrent.TimeUnit.SECONDS)
        } catch (t: Throwable) {
            throw AssertionError(t)
        }
    }

    @Test
    fun basicFromNetwork() = runTest {
        val saved = AtomicReference<Foo>()
        handleShouldMatch = { it == null }
        val fetchedDbValue = Foo(1)
        handleSaveCallResult = { foo ->
            saved.set(foo)
            dbData.value = fetchedDbValue
        }
        val networkResult = Foo(1)
        handleCreateCall = { ApiUtil.createCall(Response.success(networkResult)) }

        val observer = networkBoundResource.asFlow().test(this)
        drain()
        assertThat(saved.get()).isEqualTo(networkResult)
        assertThat(observer.values.last()).isEqualTo(Resource.success(fetchedDbValue))
        observer.finish()
    }

    @Test
    fun failureFromNetwork() = runTest {
        val saved = AtomicBoolean(false)
        handleShouldMatch = { it == null }
        handleSaveCallResult = {
            saved.set(true)
        }
        val body = "error".toResponseBody("text/html".toMediaTypeOrNull())
        handleCreateCall = { ApiUtil.createCall(Response.error<Foo>(500, body)) }

        val observer = networkBoundResource.asFlow().test(this)
        assertThat(saved.get()).isFalse()
        assertThat(observer.values.last().message).isEqualTo("error")
        observer.finish()
    }

    @Test
    fun dbSuccessWithoutNetwork() = runTest {
        val saved = AtomicBoolean(false)
        handleShouldMatch = { it == null }
        handleSaveCallResult = {
            saved.set(true)
        }

        val dbFoo = Foo(1)
        dbData.value = dbFoo
        val observer = networkBoundResource.asFlow().test(this)

        assertThat(observer.values.last()).isEqualTo(Resource.success(dbFoo))
        assertThat(saved.get()).isFalse()

        val dbFoo2 = Foo(2)
        dbData.value = dbFoo2
        assertThat(observer.values.last()).isEqualTo(Resource.success(dbFoo2))
        observer.finish()
    }

    @Test
    fun dbSuccessWithFetchFailure() = runTest {
        val dbValue = Foo(1)
        val saved = AtomicBoolean(false)
        handleShouldMatch = { foo -> foo === dbValue }
        handleSaveCallResult = {
            saved.set(true)
        }
        val body = "error".toResponseBody("text/html".toMediaTypeOrNull())
        val apiResponseFlow = MutableStateFlow<ApiResponse<Foo>>(ApiResponse.create(Response.error(400, body)))
        handleCreateCall = { apiResponseFlow }

        dbData.value = dbValue
        val observer = networkBoundResource.asFlow().test(this)

        assertThat(saved.get()).isFalse()
        assertThat(observer.values.last()).isEqualTo(Resource.error("error", dbValue))

        val dbValue2 = Foo(2)
        dbData.value = dbValue2
        assertThat(observer.values.last()).isEqualTo(Resource.error("error", dbValue2))
        observer.finish()
    }

    @Test
    fun dbSuccessWithReFetchSuccess() = runTest {
        val dbValue = Foo(1)
        val dbValue2 = Foo(2)
        val saved = AtomicReference<Foo>()
        handleShouldMatch = { foo -> foo === dbValue }
        val networkResult = Foo(1)
        handleSaveCallResult = { foo ->
            saved.set(foo)
            dbData.value = dbValue2
        }
        val apiResponseFlow = MutableStateFlow<ApiResponse<Foo>>(ApiResponse.create(Response.success(networkResult)))
        handleCreateCall = { apiResponseFlow }

        dbData.value = dbValue
        val observer = networkBoundResource.asFlow().test(this)
        drain()
        assertThat(saved.get()).isEqualTo(networkResult)
        assertThat(observer.values.last()).isEqualTo(Resource.success(dbValue2))
        observer.finish()
    }

    private data class Foo(var value: Int)

    companion object {
        @Parameterized.Parameters
        @JvmStatic
        fun param(): List<Boolean> {
            return arrayListOf(true, false)
        }
    }
}
