package com.dgurnick.bff.agent

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.dgurnick.bff.model.*
import com.dgurnick.bff.usecase.UseCase

private val fallbackJson = Json { explicitNulls = false }

/**
 * Fallback use-case — matched when no other agent claims the prompt.
 *
 * Renders a friendly "I didn't understand that" message followed by a list
 * of suggestion chips so the user knows exactly what they can ask.
 */
class FallbackAgent : UseCase {

    /** Always true — this is the catch-all at the end of the use-case list. */
    override fun canHandle(prompt: String): Boolean = true

    override fun generate(prompt: String, surfaceId: String): Flow<String> = flow {
        val d = 40L
        fun su(componentId: String, def: ComponentDefinition): String =
            fallbackJson.encodeToString(SurfaceUpdateMessage(
                surfaceUpdate = SurfaceUpdatePayload(
                    surfaceId = surfaceId,
                    components = listOf(ComponentEntry(id = componentId, component = def))
                )
            ))

        // ── 1. Root layout ────────────────────────────────────────────────────
        emit(su("root", component("Column") {
            putChildren("sorry_header", "sorry_subtext", "suggestions_header", "suggestions_list")
        }))
        delay(d)

        // ── 2. "I didn't quite catch that" ───────────────────────────────────
        emit(su("sorry_header", component("Text") {
            putLiteralString("text", "Sorry, I didn't understand that.")
            putString("usageHint", "h2")
        }))
        delay(d)

        emit(su("sorry_subtext", component("Text") {
            putLiteralString("text", "Here are some things you can ask me:")
            putString("usageHint", "body")
        }))
        delay(d)

        // ── 3. Suggestion chips ───────────────────────────────────────────────
        val suggestions = listOf(
            "atm_chip"      to "Where is the nearest ATM?",
            "balance_chip"  to "What is my account balance?",
            "offers_chip"   to "What offers do you have for me?",
        )

        emit(su("suggestions_list", component("Column") {
            putChildren(*suggestions.map { it.first }.toTypedArray())
        }))
        delay(d)

        for ((chipId, label) in suggestions) {
            emit(su(chipId, component("Button") {
                putLiteralString("label", label)
                putString("action", "sendPrompt")
                putLiteralString("actionParam", label)
            }))
            delay(d)
        }

        emit(su("suggestions_header", component("Text") {
            putLiteralString("text", "")   // spacer resolved before list
            putString("usageHint", "label")
        }))
        delay(d)
    }
}
