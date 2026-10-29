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

package com.example.appstaterecipes.browser

import android.app.Application
import androidx.appstate.datastore.syncToDataStore
import androidx.appstate.statestore.StateStore
import com.example.appstaterecipes.StateStoreOwner
import com.example.appstaterecipes.browser.state.BrowserNavigator
import java.io.File
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// [START browser_datastore_sync]
class BrowserApplication : Application(), StateStoreOwner {
    override val stateStore: StateStore = StateStore()

    // [START_EXCLUDE]
    /**
     * Completes after a short startup delay to allow [syncToDataStore] to hydrate persisted keys
     * from disk before [BrowserActivity] opens its initial window.
     *
     * TODO: Replace this fixed delay once `syncToDataStore` in `androidx.appstate` exposes an API
     *  or callback signaling when initial DataStore restoration has completed.
     */
    val initialRestoreComplete: CompletableDeferred<Unit> = CompletableDeferred()
    // [END_EXCLUDE]

    private val dataStoreScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mainSyncScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        // Prime persisted browser keys in StateStore so syncToDataStore restores them from disk
        // at app startup before BrowserActivity opens a window.
        BrowserNavigator(stateStore).primeKeys()

        val dataStoreFile = File(filesDir, "appstate_statestore.preferences_pb")
        // Launching syncToDataStore on Dispatchers.Main means its internal listener runs on
        // Dispatchers.Main (forwarding the calling CoroutineContext's dispatcher to
        // GlobalSnapshotManager.ensureStarted), while DataStore file I/O runs on dataStoreScope
        // (Dispatchers.IO).
        mainSyncScope.launch {
            stateStore.syncToDataStore(dataStoreFile.absolutePath, dataStoreScope)
        }

        // [START_EXCLUDE]
        // Hacky workaround: `syncToDataStore` runs indefinitely and does not yet signal when the
        // initial disk read has populated all keys. Wait briefly on cold process start so
        // BrowserActivity doesn't open/overwrite a window before persisted tabs are hydrated.
        // TODO: Address initial restore completion signaling in the androidx.appstate library.
        mainSyncScope.launch {
            delay(INITIAL_RESTORE_DELAY)
            initialRestoreComplete.complete(Unit)
        }
        // [END_EXCLUDE]
    }

    // [START_EXCLUDE]
    private companion object {
        val INITIAL_RESTORE_DELAY = 250.milliseconds
    }
    // [END_EXCLUDE]
}
// [END browser_datastore_sync]
