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

package com.android.example.github

import android.app.Application
import androidx.appstate.datastore.syncToDataStore
import androidx.appstate.statestore.StateStore
import com.android.example.github.repository.AuthStateHolder
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class GithubApp : Application() {

    @Inject
    lateinit var stateStore: StateStore

    @Inject
    lateinit var authState: AuthStateHolder

    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val dataStoreScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        // Register the persisted auth keys so syncToDataStore restores them from disk.
        authState.primeKeys()

        // Launching on Dispatchers.Main runs syncToDataStore's snapshot listener on the main
        // thread, while dataStoreScope (Dispatchers.IO) is passed to DataStore for file I/O.
        mainScope.launch {
            try {
                val path = filesDir.resolve("github_auth_statestore.json").absolutePath
                stateStore.syncToDataStore(path, dataStoreScope)
            } catch (e: Exception) {
                Timber.e(e, "Failed to sync StateStore with DataStore")
            }
        }
    }
}
