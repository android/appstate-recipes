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

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import androidx.appfunctions.AppFunctionAppUnknownException
import androidx.appfunctions.AppFunctionContext
import androidx.appfunctions.AppFunctionElementNotFoundException
import androidx.appfunctions.AppFunctionInvalidArgumentException
import androidx.appfunctions.AppFunctionSerializable
import androidx.appfunctions.AppFunctionStringValueConstraint
import androidx.appfunctions.service.AppFunction
import androidx.appstate.statestore.StateStore
import com.example.chatapp.data.CallManager
import com.example.chatapp.data.MessageRepository
import com.example.chatapp.data.RecipientsRepository
import com.example.chatapp.data.AppScreen
import com.example.chatapp.data.setCurrentScreen
import com.example.chatapp.data.setCurrentUserName
import javax.inject.Inject

/**
 * Provides functions for chat-related operations such as retrieving contacts and sending messages.
 */
class AppFunctions
    @Inject
    constructor(
        private val messageRepository: MessageRepository,
        private val recipientsRepository: RecipientsRepository,
        private val callManager: CallManager,
        private val stateStore: StateStore,
    ) {
        /**
         * Search for contacts or groups by name.
         *
         * @param appFunctionContext The context of this app function call.
         * @param query Search string for the contact or group name. Can be partial or full names.
         *   If blank, returns the most recently contacted entities.
         * @param filterType Filter results by entity type. Accepts "INDIVIDUAL" or "GROUP".
         */
        @AppFunction(isDescribedByKDoc = true)
        suspend fun searchContacts(
            appFunctionContext: AppFunctionContext,
            query: String,
            @AppFunctionStringValueConstraint(enumValues = ["INDIVIDUAL", "GROUP"])
            filterType: String,
        ): List<ContactSearchResult> {
            val recipients =
                when (filterType) {
                    "INDIVIDUAL" -> {
                        recipientsRepository.searchRecipients(query, 3).map {
                            ContactSearchResult(
                                endpointValue = it.id,
                                endpointType = "INDIVIDUAL",
                                displayName = it.name,
                            )
                        }
                    }
                    "GROUP" -> {
                        recipientsRepository.searchGroups(query, 3).map {
                            ContactSearchResult(
                                endpointValue = it.id,
                                endpointType = "GROUP",
                                displayName = it.name,
                            )
                        }
                    }
                    else -> {
                        recipientsRepository.searchAny(query, maxCount = 3)
                            .ifEmpty {
                                throw AppFunctionInvalidArgumentException(
                                    "Only INDIVIDUAL or GROUP are accepted filter arguments.",
                                )
                            }
                    }
                }

            if (recipients.isEmpty()) {
                throw AppFunctionInvalidArgumentException(
                    "$filterType with name $query not found. Ask the user to clarify the name",
                )
            }
            return recipients
        }

        /**
         * Send a text message with optional image attachments.
         *
         * @param appFunctionContext The context of this app function call.
         * @param name The display name of the recipient for confirmation purposes.
         * @param endpointValue The unique identifier for the recipient or group.
         * @param messageBody The text content of the message. Cannot be empty.
         * @param imageUris List of URIs for images to attach.
         */
        @AppFunction(isDescribedByKDoc = true)
        suspend fun send(
            appFunctionContext: AppFunctionContext,
            name: String,
            endpointValue: String,
            messageBody: String,
            imageUris: List<Uri>? = null,
        ): Result {
            if (messageBody.isBlank()) {
                throw AppFunctionInvalidArgumentException("Message body cannot be empty")
            }
            val displayName =
                recipientsRepository.getRecipientById(endpointValue)?.name
                    ?: recipientsRepository.getGroupById(endpointValue)?.name
                    ?: throw AppFunctionElementNotFoundException(
                        "No contact or group found for endpointValue: $endpointValue",
                    )

            val sentMessageId =
                try {
                    messageRepository.send(
                        text = messageBody,
                        recipientIds = listOf(endpointValue),
                        imageUris = imageUris?.map { it.toString() },
                    )
                } catch (e: Exception) {
                    throw AppFunctionAppUnknownException("Failed to send message: ${e.message}")
                }

            // 3. RETURN: Provide a confirmation string
            // The bot may use this string or the fact that it didn't throw to confirm success.
            return Result(sentMessageId, "Message sent to: $displayName.")
        }

        /**
         * Initiate a voice call.
         *
         * @param appFunctionContext The context of this app function call.
         * @param contactName The name of the contact. Use this if the ID is unknown.
         * @param endpointValue The unique identifier for the recipient.
         */
        @AppFunction(isDescribedByKDoc = true)
        suspend fun makeCall(
            appFunctionContext: AppFunctionContext,
            contactName: String? = null,
            endpointValue: String? = null,
        ): PendingIntent {
            if (contactName == null && endpointValue == null) {
                throw AppFunctionInvalidArgumentException(
                    "Specify either contactName or endpointValue. Both cannot be null.",
                )
            }

            val recipient =
                with(recipientsRepository) {
                    if (endpointValue == null) {
                        getRecipientByName(checkNotNull(contactName))
                            ?: throw AppFunctionElementNotFoundException(
                                "No recipient exists for contactName: $contactName",
                            )
                    } else {
                        getRecipientById(endpointValue)
                            ?: throw AppFunctionElementNotFoundException(
                                "No recipient exists for endpointValue: $endpointValue",
                            )
                    }
                }

            // Call manager should technically also record it here depending on app architecture,
            // but we will launch the intent to handle it in the UI.
            callManager.startCall(recipient.id)

            // Create a pending intent to the MainActivity, initiating the call
            return PendingIntent.getActivity(
                appFunctionContext.context,
                0,
                Intent(appFunctionContext.context, Class.forName("com.example.chatapp.MainActivity"))
                    .apply { putExtra("nav_route", "call/${recipient.id}") },
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        /**
         * Change the user signed in to the chat app.
         *
         * @param appFunctionContext The context of this app function call.
         * @param userName The display name of the user to switch to. It is shown on the chat list
         *   and used as the sender name of the messages the user sends. Cannot be blank.
         */
        @AppFunction(isDescribedByKDoc = true)
        suspend fun changeUser(
            appFunctionContext: AppFunctionContext,
            userName: String,
        ): String {
            if (userName.isBlank()) {
                throw AppFunctionInvalidArgumentException("userName cannot be blank.")
            }
            stateStore.setCurrentUserName(userName.trim())
            return "Signed in as ${userName.trim()}."
        }

        /**
         * Change the screen shown in the chat app.
         *
         * @param appFunctionContext The context of this app function call.
         * @param screen The screen to show: "CHAT_LIST" for the list of all conversations, "CHAT"
         *   for a single conversation (requires `endpointValue`), or "SETTINGS" for the app's
         *   settings. If the app is open, it navigates there right away. To start a call, use
         *   `makeCall` instead.
         * @param endpointValue The unique identifier of the contact or group whose conversation to
         *   show, as returned by `searchContacts`. Required when `screen` is "CHAT".
         */
        @AppFunction(isDescribedByKDoc = true)
        suspend fun changeScreen(
            appFunctionContext: AppFunctionContext,
            @AppFunctionStringValueConstraint(enumValues = ["CHAT_LIST", "CHAT", "SETTINGS"])
            screen: String,
            endpointValue: String? = null,
        ): String {
            val (appScreen, confirmation) =
                when (screen) {
                    "CHAT_LIST" -> AppScreen.ChatList to "Showing the chat list."
                    "SETTINGS" -> AppScreen.Settings to "Showing settings."
                    "CHAT" -> {
                        if (endpointValue.isNullOrBlank()) {
                            throw AppFunctionInvalidArgumentException(
                                "endpointValue is required when screen is CHAT.",
                            )
                        }
                        val name =
                            recipientsRepository.getRecipientById(endpointValue)?.name
                                ?: recipientsRepository.getGroupById(endpointValue)?.name
                                ?: throw AppFunctionElementNotFoundException(
                                    "No contact or group found for endpointValue: $endpointValue",
                                )
                        AppScreen.Chat(endpointValue) to "Showing the chat with $name."
                    }
                    else -> throw AppFunctionInvalidArgumentException(
                        "screen must be CHAT_LIST, CHAT, or SETTINGS.",
                    )
                }
            stateStore.setCurrentScreen(appScreen)
            return confirmation
        }

        /** Represents a result from a contact or group search. */
        @AppFunctionSerializable(isDescribedByKDoc = true)
        data class ContactSearchResult(
            /** The unique identifier. */
            val endpointValue: String,
            /** The type of the found entity, either "INDIVIDUAL" or "GROUP". */
            val endpointType: String,
            /** The human-readable name of the contact or group. */
            val displayName: String,
        )

        /** Result of a message sending operation. */
        @AppFunctionSerializable(isDescribedByKDoc = true)
        data class Result(
            /** The unique identifier for the successfully sent message. */
            val messageId: String,
            /** A human-readable status message indicating success. */
            val message: String,
        )

        /** Represents a recipient of a message. */
        @AppFunctionSerializable(isDescribedByKDoc = true)
        data class Recipient(
            /** Unique identifier for the contact. */
            val id: String,
            /** Human-readable name. */
            val name: String,
            /** Email address of the contact. */
            val email: String,
        )

        /** Represents a group of recipients. */
        @AppFunctionSerializable(isDescribedByKDoc = true)
        data class ChatGroup(
            /** Unique identifier for the group. */
            val id: String,
            /** Name of the group. */
            val name: String,
            /** List of members belonging to the group. */
            val recipients: List<Recipient>,
        )
    }
