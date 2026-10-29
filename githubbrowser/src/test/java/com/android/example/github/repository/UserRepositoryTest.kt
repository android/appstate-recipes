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
import com.android.example.github.db.UserDao
import com.android.example.github.util.ApiUtil
import com.android.example.github.util.InstantAppExecutors
import com.android.example.github.util.MainDispatcherRule
import com.android.example.github.util.TestUtil
import com.android.example.github.util.test
import com.android.example.github.vo.Resource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(JUnit4::class)
class UserRepositoryTest {
    private val userDao = mock(UserDao::class.java)
    private val githubService = mock(GithubService::class.java)
    private val repo = UserRepository(InstantAppExecutors(), userDao, githubService)

    @Rule
    @JvmField
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun loadUser() = runTest {
        val user = TestUtil.createUser("abc")
        `when`(userDao.findByLogin("abc")).thenReturn(flowOf(user))
        val observer = repo.loadUser("abc").test(this)
        verify(userDao).findByLogin("abc")
        observer.finish()
    }

    @Test
    fun goToNetwork() = runTest {
        val user = TestUtil.createUser("foo")
        `when`(userDao.findByLogin("foo")).thenReturn(flowOf(null, user))
        val call = ApiUtil.successCall(user)
        `when`(githubService.getUser("foo")).thenReturn(call)

        val observer = repo.loadUser("foo").test(this)
        verify(githubService).getUser("foo")
        verify(userDao).insert(user)
        assertThat(observer.values.last()).isEqualTo(Resource.success(user))
        observer.finish()
    }

    @Test
    fun dontGoToNetwork() = runTest {
        val user = TestUtil.createUser("foo")
        `when`(userDao.findByLogin("foo")).thenReturn(flowOf(user))

        val observer = repo.loadUser("foo").test(this)
        verify(githubService, never()).getUser("foo")
        assertThat(observer.values.last()).isEqualTo(Resource.success(user))
        observer.finish()
    }
}
