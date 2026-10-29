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
import okhttp3.Headers.Companion.headersOf
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import retrofit2.Response

@RunWith(JUnit4::class)
class ApiResponseTest {
    @Test
    fun exception() {
        val exception = Exception("foo")
        val (errorMessage) = ApiResponse.create<String>(exception)
        assertThat(errorMessage).isEqualTo("foo")
    }

    @Test
    fun success() {
        val apiResponse: ApiSuccessResponse<String> = ApiResponse
            .create<String>(Response.success("foo")) as ApiSuccessResponse<String>
        assertThat(apiResponse.body).isEqualTo("foo")
        assertThat(apiResponse.nextPage).isNull()
    }

    @Test
    fun link() {
        val link =
            "<https://api.github.com/search/repositories?q=foo&page=2>; rel=\"next\"," +
                    " <https://api.github.com/search/repositories?q=foo&page=34>; rel=\"last\""
        val headers = headersOf("link", link)
        val response = ApiResponse.create<String>(Response.success("foo", headers))
        assertThat((response as ApiSuccessResponse).nextPage).isEqualTo(2)
    }

    @Test
    fun badPageNumber() {
        val link = "<https://api.github.com/search/repositories?q=foo&page=dsa>; rel=\"next\""
        val headers = headersOf("link", link)
        val response = ApiResponse.create<String>(Response.success("foo", headers))
        assertThat((response as ApiSuccessResponse).nextPage).isNull()
    }

    @Test
    fun badLinkHeader() {
        val link = "<https://api.github.com/search/repositories?q=foo&page=dsa>; relx=\"next\""
        val headers = headersOf("link", link)
        val response = ApiResponse.create<String>(Response.success("foo", headers))
        assertThat((response as ApiSuccessResponse).nextPage).isNull()
    }

    @Test
    fun error() {
        val errorResponse = Response.error<String>(
            400,
            "blah".toResponseBody("application/txt".toMediaTypeOrNull())
        )
        val (errorMessage) = ApiResponse.create<String>(errorResponse) as ApiErrorResponse<String>
        assertThat(errorMessage).isEqualTo("blah")
    }
}