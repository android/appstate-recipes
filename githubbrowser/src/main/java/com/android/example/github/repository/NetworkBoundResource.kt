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

import androidx.annotation.MainThread
import androidx.annotation.WorkerThread
import com.android.example.github.AppExecutors
import com.android.example.github.api.ApiEmptyResponse
import com.android.example.github.api.ApiErrorResponse
import com.android.example.github.api.ApiResponse
import com.android.example.github.api.ApiSuccessResponse
import com.android.example.github.vo.Resource
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * A generic class that can provide a resource backed by both the sqlite database and the network.
 *
 * You can read more about it in the [Architecture
 * Guide](https://developer.android.com/arch).
 * @param <ResultType>
 * @param <RequestType>
 */
abstract class NetworkBoundResource<ResultType, RequestType>
@MainThread constructor(private val appExecutors: AppExecutors) {

    fun asFlow(): Flow<Resource<ResultType>> = flow {
        emit(Resource.loading(null))
        val dbSource = loadFromDb()
        val firstDb = dbSource.first()
        if (shouldFetch(firstDb)) {
            emit(Resource.loading(firstDb))
            when (val apiResponse = createCall().first()) {
                is ApiSuccessResponse -> {
                    withContext(appExecutors.diskIO.asCoroutineDispatcher()) {
                        saveCallResult(processResponse(apiResponse))
                    }
                    emitAll(dbSource.filterNotNull().map { Resource.success(it) })
                }
                is ApiEmptyResponse -> {
                    emitAll(dbSource.filterNotNull().map { Resource.success(it) })
                }
                is ApiErrorResponse -> {
                    onFetchFailed()
                    emitAll(dbSource.map { Resource.error(apiResponse.errorMessage, it) })
                }
            }
        } else {
            emitAll(dbSource.filterNotNull().map { Resource.success(it) })
        }
    }

    protected open fun onFetchFailed() {}

    @WorkerThread
    protected open fun processResponse(response: ApiSuccessResponse<RequestType>) = response.body

    @WorkerThread
    protected abstract fun saveCallResult(item: RequestType)

    @MainThread
    protected abstract fun shouldFetch(data: ResultType?): Boolean

    @MainThread
    protected abstract fun loadFromDb(): Flow<ResultType?>

    @MainThread
    protected abstract fun createCall(): Flow<ApiResponse<RequestType>>
}
