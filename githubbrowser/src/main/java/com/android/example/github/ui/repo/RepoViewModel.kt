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

package com.android.example.github.ui.repo

import androidx.appstate.statestore.StateStore
import androidx.appstate.transform.transform
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.example.github.repository.RepoRepository
import com.android.example.github.repository.loggedInUserLogin
import com.android.example.github.vo.Contributor
import com.android.example.github.vo.Repo
import com.android.example.github.vo.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

@HiltViewModel
class RepoViewModel @Inject constructor(
    private val repository: RepoRepository,
    private val stateStore: StateStore,
    private val dispatcher: CoroutineDispatcher
) : ViewModel() {

    private val _repoId = mutableStateOf<RepoId?>(null)
    val repoId: State<RepoId?> = _repoId
    private val retryTrigger = mutableIntStateOf(0)

    val repo: State<Resource<Repo>?> = loadForRepo { repository.loadRepo(it.owner, it.name) }

    val contributors: State<Resource<List<Contributor>>?> =
        loadForRepo { repository.loadContributors(it.owner, it.name) }

    fun setId(owner: String, name: String) {
        _repoId.value = RepoId(owner, name)
    }

    fun retry() {
        retryTrigger.intValue++
    }

    /**
     * Loads [load] for the current repo, reloading on [retry] and whenever the signed-in user
     * changes so requests pick up the new authentication state.
     */
    private fun <T> loadForRepo(load: (RepoId) -> Flow<Resource<T>>): State<Resource<T>?> =
        transform(initialValue = null, scope = viewModelScope, dispatcher = dispatcher) {
            val currentId = _repoId.value
            val trigger = retryTrigger.intValue
            val activeLogin = stateStore.loggedInUserLogin().value
            if (currentId == null || currentId.name.isEmpty() || currentId.owner.isEmpty()) {
                null
            } else {
                remember(currentId, trigger, activeLogin) { load(currentId) }
                    .collectAsState(initial = null).value
            }
        }

    data class RepoId(val owner: String, val name: String)
}
