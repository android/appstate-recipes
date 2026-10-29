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
package com.example.chatapp.appfunctions

import android.content.Context
import androidx.appfunctions.AppFunctionData
import androidx.appfunctions.AppFunctionInvalidArgumentException
import androidx.appfunctions.AppFunctionManager
import androidx.appfunctions.AppFunctionSearchSpec
import androidx.appfunctions.ExecuteAppFunctionRequest
import androidx.appfunctions.ExecuteAppFunctionResponse
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.chatapp.data.MessageRepository
import com.example.chatapp.data.RecipientsRepository
import com.example.chatapp.data.AppScreen
import com.example.chatapp.data.ChatAppStateHolder
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject
import kotlin.test.assertIs

/**
 * Instrumented test for AppFunctions executing via AppFunctionManager. Stubs are left for the user
 * to fill out.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class AppFunctionInstrumentationTest {
    @get:Rule val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var messageRepository: MessageRepository

    @Inject lateinit var recipientsRepository: RecipientsRepository

    @Inject lateinit var appState: ChatAppStateHolder

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val appFunctionManager: AppFunctionManager =
        checkNotNull(AppFunctionManager.getInstance(context))

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun testSend_success() =
        runBlocking<Unit> {
            val sendMessageFunctionMetadata =
                appFunctionManager
                    .searchAppFunctions(
                        AppFunctionSearchSpec(packageNames = setOf(context.packageName)),
                    )
                    .single { it.id == ChatAppFunctionService.FUNCTION_ID_SEND_MESSAGE }
            val testRecipient = recipientsRepository.getAllRecipients().first()
            val request =
                ExecuteAppFunctionRequest(
                    targetPackageName = context.packageName,
                    ChatAppFunctionService.FUNCTION_ID_SEND_MESSAGE,
                    AppFunctionData.Builder(
                        sendMessageFunctionMetadata.parameters,
                        sendMessageFunctionMetadata.components,
                    )
                        .setString("endpointValue", testRecipient.id)
                        .setString("messageBody", "Hello!")
                        .build(),
                )

            val response = appFunctionManager.executeAppFunction(request)
            val successResponse = assertIs<ExecuteAppFunctionResponse.Success>(response)
            assertThat(
                successResponse.returnValue
                    .getString(ExecuteAppFunctionResponse.Success.PROPERTY_RETURN_VALUE),
            )
                .isEqualTo("Message sent to: Alice Smith.")
            // Verify that the message was actually saved in the repository
            val messages = messageRepository.getMessages(testRecipient.id).first()
            assertThat(messages).isNotEmpty()
            assertThat(messages.first().content).isEqualTo("Hello!")
        }

    @Test
    fun testSend_emptyContent_fails() =
        runBlocking<Unit> {
            val sendMessageFunctionMetadata =
                appFunctionManager
                    .searchAppFunctions(
                        AppFunctionSearchSpec(packageNames = setOf(context.packageName)),
                    )
                    .single { it.id == ChatAppFunctionService.FUNCTION_ID_SEND_MESSAGE }
            val testRecipient = recipientsRepository.getAllRecipients().first()
            val request =
                ExecuteAppFunctionRequest(
                    targetPackageName = context.packageName,
                    ChatAppFunctionService.FUNCTION_ID_SEND_MESSAGE,
                    AppFunctionData.Builder(
                        sendMessageFunctionMetadata.parameters,
                        sendMessageFunctionMetadata.components,
                    )
                        .setString("endpointValue", testRecipient.id)
                        .setString("messageBody", "")
                        .build(),
                )

            val response = appFunctionManager.executeAppFunction(request)
            val errorResponse = assertIs<ExecuteAppFunctionResponse.Error>(response)
            val error = assertIs<AppFunctionInvalidArgumentException>(errorResponse.error)
            assertThat(error).hasMessageThat().contains("Message body cannot be empty")
        }

    @Test
    fun testSearchContacts_success() =
        runBlocking<Unit> {
            val getRecipientsFunctionMetadata =
                appFunctionManager
                    .searchAppFunctions(
                        AppFunctionSearchSpec(packageNames = setOf(context.packageName)),
                    )
                    .single { it.id == ChatAppFunctionService.FUNCTION_ID_SEARCH_CONTACTS }

            val request =
                ExecuteAppFunctionRequest(
                    targetPackageName = context.packageName,
                    ChatAppFunctionService.FUNCTION_ID_SEARCH_CONTACTS,
                    AppFunctionData.Builder(
                        getRecipientsFunctionMetadata.parameters,
                        getRecipientsFunctionMetadata.components,
                    )
                        .setString("query", "Alice")
                        .setString("contactType", "INDIVIDUAL")
                        .build(),
                )

            val response = appFunctionManager.executeAppFunction(request)
            val successResponse = assertIs<ExecuteAppFunctionResponse.Success>(response)
            assertThat(
                successResponse.returnValue
                    .getAppFunctionDataList(
                        ExecuteAppFunctionResponse.Success.PROPERTY_RETURN_VALUE,
                    )
                    ?.map { it.deserialize(ContactSearchResult::class.java) },
            )
                .containsExactly(
                    ContactSearchResult(
                        contactDisplayName = "Alice Smith",
                        contactType = "INDIVIDUAL",
                        endpointValue = "1",
                        endpointDisplayName = "alice@example.com",
                    ),
                )
        }

    @Test
    fun testChangeUser_updatesSharedStateStore() =
        runBlocking<Unit> {
            val metadata = functionMetadata(ChatAppFunctionService.FUNCTION_ID_CHANGE_USER)
            val request =
                ExecuteAppFunctionRequest(
                    targetPackageName = context.packageName,
                    ChatAppFunctionService.FUNCTION_ID_CHANGE_USER,
                    AppFunctionData.Builder(metadata.parameters, metadata.components)
                        .setString("userName", "Jordan")
                        .build(),
                )

            val response = appFunctionManager.executeAppFunction(request)
            val successResponse = assertIs<ExecuteAppFunctionResponse.Success>(response)
            assertThat(
                successResponse.returnValue.getString(ExecuteAppFunctionResponse.Success.PROPERTY_RETURN_VALUE),
            ).isEqualTo("Signed in as Jordan.")
            // The same ChatAppStateHolder (and StateStore) is shared with the rest of the app.
            assertThat(appState.currentUserName.value).isEqualTo("Jordan")
        }

    @Test
    fun testChangeScreen_updatesSharedStateStore() =
        runBlocking<Unit> {
            val metadata = functionMetadata(ChatAppFunctionService.FUNCTION_ID_CHANGE_SCREEN)
            val request =
                ExecuteAppFunctionRequest(
                    targetPackageName = context.packageName,
                    ChatAppFunctionService.FUNCTION_ID_CHANGE_SCREEN,
                    AppFunctionData.Builder(metadata.parameters, metadata.components)
                        .setString("screen", "CHAT")
                        .setString("endpointValue", "2")
                        .build(),
                )

            val response = appFunctionManager.executeAppFunction(request)
            val successResponse = assertIs<ExecuteAppFunctionResponse.Success>(response)
            assertThat(
                successResponse.returnValue.getString(ExecuteAppFunctionResponse.Success.PROPERTY_RETURN_VALUE),
            ).isEqualTo("Showing the chat with Bob Johnson.")
            assertThat(appState.currentScreen.value).isEqualTo(AppScreen.Chat("2"))
        }

    private suspend fun functionMetadata(id: String) =
        appFunctionManager
            .searchAppFunctions(AppFunctionSearchSpec(packageNames = setOf(context.packageName)))
            .single { it.id == id }
}
