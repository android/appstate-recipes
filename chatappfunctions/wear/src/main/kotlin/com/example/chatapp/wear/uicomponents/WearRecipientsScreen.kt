/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.chatapp.wear.uicomponents

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.Text
import com.example.chatapp.RecipientsViewModel

@Composable
fun WearRecipientsScreen(
    viewModel: RecipientsViewModel = hiltViewModel(),
    onRecipientClick: (String) -> Unit,
    onSettingsClick: () -> Unit,
) {
    val recipients = viewModel.recipients
    val groups = viewModel.groups

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            ListHeader {
                Text(text = "Chats")
            }
        }

        items(groups) { group ->
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = { onRecipientClick(group.id) },
                label = { Text(text = group.name) },
                secondaryLabel = { Text(text = "Group") },
            )
        }

        items(recipients) { recipient ->
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = { onRecipientClick(recipient.id) },
                label = { Text(text = recipient.name) },
            )
        }

        item {
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = onSettingsClick,
                label = { Text(text = "Settings") },
            )
        }
    }
}
