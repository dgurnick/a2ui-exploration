package com.dgurnick.bff.agent

import com.dgurnick.bff.usecase.UseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.*

/**
 * Fallback use-case: matched when no other agent claims the prompt.
 *
 * Emits a single typed JSON object the Android client renders via Remote Compose.
 */
class FallbackAgent : UseCase {

    override fun canHandle(prompt: String): Boolean = true

    override fun generate(prompt: String, surfaceId: String): Flow<String> = flow {
        emit(buildJsonObject {
            put("type", "fallback")
            put("message", "Sorry, I didn\u2019t understand that.")
            putJsonArray("suggestions") {
                add(JsonPrimitive("Where is the nearest ATM?"))
                add(JsonPrimitive("What is my account balance?"))
                add(JsonPrimitive("What offers do you have for me?"))
            }
        }.toString())
    }
}