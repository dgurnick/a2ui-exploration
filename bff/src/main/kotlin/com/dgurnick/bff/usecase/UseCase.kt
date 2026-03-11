package com.dgurnick.bff.usecase

import kotlinx.coroutines.flow.Flow

/**
 * A single conversational use-case that the BFF knows how to handle.
 *
 * Implementations are registered in [Application.kt] and injected into
 * [A2uiSubscription]. The subscription calls [canHandle] on each registered
 * use-case in order, and delegates to the first match.
 */
interface UseCase {
    /** Returns true if this use-case understands the given natural-language prompt. */
    fun canHandle(prompt: String): Boolean

    /** Generate the A2UI JSONL stream for this prompt on the given surface. */
    fun generate(prompt: String, surfaceId: String): Flow<String>
}
