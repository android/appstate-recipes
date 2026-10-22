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
import android.os.Looper
import androidx.appstate.datastore.PersistToDataStore
import androidx.appstate.datastore.syncToDataStore
import androidx.appstate.statestore.StateStore
import androidx.appstate.transform.transform
import androidx.compose.runtime.State
import androidx.compose.runtime.snapshots.ObserverHandle
import androidx.compose.runtime.snapshots.Snapshot
import com.example.appstaterecipes.StateStoreOwner
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import java.util.Collections

class BrowserApplication : Application(), StateStoreOwner {
    override val stateStore: StateStore = StateStore()

    /**
     * Completes once all primed [PersistToDataStore] keys have completed their initial restore
     * pass from [syncToDataStore].
     */
    val isRestored: CompletableDeferred<Unit> = CompletableDeferred()

    private val applicationScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        // Prime androidx.appstate.transform's GlobalSnapshotManager on Dispatchers.Main
        // before any Dispatchers.Default transform/listener (such as syncToDataStore) starts.
        // Compose UI's SnapshotStateObserver and Transition animations require
        // Snapshot.sendApplyNotifications() to run on the main UI thread.
        CoroutineScope(Dispatchers.Main).apply {
            transform(initialValue = Unit, scope = this, dispatcher = Dispatchers.Main) {}
            cancel()
        }

        // Prime persisted browser keys in StateStore so syncToDataStore restores them from disk
        // at app startup before BrowserActivity opens a window.
        BrowserNavigator(stateStore).primeKeys()

        val persistedKeyCount = stateStore.keys.count { it is PersistToDataStore }
        if (persistedKeyCount == 0) {
            isRestored.complete(Unit)
        } else {
            // Inside syncToDataStore, each primed PersistToDataStore key runs a LaunchedEffect(key)
            // on Dispatchers.Default that reads DataStore, calls stateStore.setState(key, decoded),
            // and finally sets its internal `restored.value = true`. Observing those `restored`
            // state transitions tells us deterministically when all primed keys have been hydrated.
            val restoredKeyStates = Collections.synchronizedSet(mutableSetOf<State<*>>())
            lateinit var observerHandle: ObserverHandle
            observerHandle = Snapshot.registerGlobalWriteObserver { stateObject ->
                if (Looper.myLooper() != Looper.getMainLooper()) {
                    val state = stateObject as? State<*> ?: return@registerGlobalWriteObserver
                    if (state.value == true && restoredKeyStates.add(state)) {
                        if (restoredKeyStates.size >= persistedKeyCount) {
                            observerHandle.dispose()
                            isRestored.complete(Unit)
                        }
                    }
                }
            }
        }

        val dataStoreFile = File(filesDir, "appstate_statestore.preferences_pb")
        applicationScope.launch {
            stateStore.syncToDataStore(dataStoreFile.absolutePath, applicationScope)
        }
    }
}
