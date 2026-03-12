package com.dgurnick.banking.bff.agent

import com.dgurnick.banking.bff.conversation.Conversation
import com.dgurnick.banking.bff.usecase.UseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.*

/**
 * Banking use-case: "What is my account balance?"
 *
 * Emits a single typed JSON object the Android client renders via Remote Compose.
 */
class AccountBalanceAgent : UseCase {

    override fun canHandle(prompt: String, conversation: Conversation): Boolean {
        val p = prompt.lowercase()
        return p.contains("balance") ||
                p.contains("account") ||
                p.contains("statement") ||
                p.contains("transaction")
    }

    override fun generate(
            prompt: String,
            surfaceId: String,
            conversation: Conversation
    ): Flow<String> = flow {
        val data = buildJsonObject {
            put("type", "account_balance")
            put("greeting", "Good morning, Fadi")
            putJsonArray("accounts") {
                addJsonObject {
                    put("name", "Everyday Checking")
                    put("number", "\u2022\u2022\u2022\u20224821")
                    put("balance", "\$3,240.55")
                    put("available", "\$3,190.55")
                }
                addJsonObject {
                    put("name", "High-Yield Savings")
                    put("number", "\u2022\u2022\u2022\u20229037")
                    put("balance", "\$41,109.62")
                    put("available", "\$41,109.62")
                }
                addJsonObject {
                    put("name", "Freedom Credit Card")
                    put("number", "\u2022\u2022\u2022\u20225521")
                    put("balance", "-\$897.14")
                    put("available", "\$9,102.86")
                }
            }
            put("netWorth", "\$43,453.03")
        }
        val rcBase64 = buildRcDocument(data)
        emit(buildJsonObject { put("rc", rcBase64) }.toString())
    }
}
