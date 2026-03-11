package com.dgurnick.banking.bff.usecase

import kotlinx.coroutines.flow.Flow

/**
 * A single conversational use-case that the BFF knows how to handle.
 *
 * Implementations are registered in [Application.kt] and injected into
 * [BankingSubscription]. The subscription calls [canHandle] on each registered
 * use-case in order, and delegates to the first match.
 */
interface UseCase {
    /** Returns true if this use-case understands the given natural-language prompt. */
    fun canHandle(prompt: String): Boolean

    /** Generate the Remote Compose document stream for this prompt on the given surface. */
    fun generate(prompt: String, surfaceId: String): Flow<String>
}
