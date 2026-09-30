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

package com.example.appstaterecipes

import androidx.appstate.transform.transform
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class TransformTestContext(
    val testScope: TestScope,
    val dispatcher: TestDispatcher,
) {
    fun <T> testTransform(
        initialValue: T,
        block: @Composable () -> T,
    ): State<T> {
        val state = transform(
            initialValue = initialValue,
            scope = testScope,
            dispatcher = dispatcher,
            onUpdate = block,
        )
        sync()
        return state
    }

    fun sync() {
        // GlobalSnapshotManager is process-wide and binds to the first test's dispatcher;
        // pumping apply notifications manually keeps subsequent tests independent of run order.
        Snapshot.sendApplyNotifications()
        testScope.testScheduler.runCurrent()
    }

    fun advanceTimeBy(millis: Long) {
        testScope.testScheduler.advanceTimeBy(millis)
        testScope.testScheduler.runCurrent()
        sync()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
fun runTransformTest(testBody: TransformTestContext.() -> Unit) = runTest {
    val dispatcher = StandardTestDispatcher(testScheduler)
    val scope = TestScope(dispatcher)
    val context = TransformTestContext(scope, dispatcher)
    try {
        context.testBody()
    } finally {
        scope.cancel()
    }
}
