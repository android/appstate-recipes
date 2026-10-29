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

package com.android.example.github.db

import android.database.sqlite.SQLiteException
import com.google.common.truth.Truth.assertThat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.example.github.util.TestUtil
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RepoDaoTest : DbTest() {

    @Test
    fun insertAndRead() = runTest {
        val repo = TestUtil.createRepo("foo", "bar", "desc")
        db.repoDao().insert(repo)
        val loaded = db.repoDao().load("foo", "bar").first()
        assertThat(loaded).isNotNull()
        assertThat(loaded!!.name).isEqualTo("bar")
        assertThat(loaded.description).isEqualTo("desc")
        assertThat(loaded.owner).isNotNull()
        assertThat(loaded.owner.login).isEqualTo("foo")
    }

    @Test
    fun insertContributorsWithoutRepo() {
        val repo = TestUtil.createRepo("foo", "bar", "desc")
        val contributor = TestUtil.createContributor(repo, "c1", 3)
        try {
            db.repoDao().insertContributors(listOf(contributor))
            throw AssertionError("must fail because repo does not exist")
        } catch (ex: SQLiteException) {
        }

    }

    @Test
    fun insertContributors() = runTest {
        val repo = TestUtil.createRepo("foo", "bar", "desc")
        val c1 = TestUtil.createContributor(repo, "c1", 3)
        val c2 = TestUtil.createContributor(repo, "c2", 7)
        db.runInTransaction {
            db.repoDao().insert(repo)
            db.repoDao().insertContributors(arrayListOf(c1, c2))
        }
        val list = db.repoDao().loadContributors("foo", "bar").first()
        assertThat(list).hasSize(2)
        val first = list[0]

        assertThat(first.login).isEqualTo("c2")
        assertThat(first.contributions).isEqualTo(7)

        val second = list[1]
        assertThat(second.login).isEqualTo("c1")
        assertThat(second.contributions).isEqualTo(3)
    }

    @Test
    fun createIfNotExists_exists() {
        val repo = TestUtil.createRepo("foo", "bar", "desc")
        db.repoDao().insert(repo)
        assertThat(db.repoDao().createRepoIfNotExists(repo)).isEqualTo(-1L)
    }

    @Test
    fun createIfNotExists_doesNotExist() {
        val repo = TestUtil.createRepo("foo", "bar", "desc")
        assertThat(db.repoDao().createRepoIfNotExists(repo)).isEqualTo(1L)
    }

    @Test
    fun insertContributorsThenUpdateRepo() = runTest {
        val repo = TestUtil.createRepo("foo", "bar", "desc")
        db.repoDao().insert(repo)
        val contributor = TestUtil.createContributor(repo, "aa", 3)
        db.repoDao().insertContributors(listOf(contributor))
        var data = db.repoDao().loadContributors("foo", "bar")
        assertThat(data.first()).hasSize(1)

        val update = TestUtil.createRepo("foo", "bar", "desc")
        db.repoDao().insert(update)
        data = db.repoDao().loadContributors("foo", "bar")
        assertThat(data.first()).hasSize(1)
    }
}
