package com.dgurnick.bff.agent

import com.dgurnick.bff.usecase.UseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.*

/**
 * Banking use-case: "What offers do you have for me?"
 *
 * Emits a single typed JSON object the Android client renders via Remote Compose.
 */
class BankOffersAgent : UseCase {

    override fun canHandle(prompt: String): Boolean {
        val p = prompt.lowercase()
        return p.contains("offer") ||
                p.contains("deal") ||
                p.contains("promotion") ||
                p.contains("promo") ||
                p.contains("rate") ||
                p.contains("loan")
    }

    override fun generate(prompt: String, surfaceId: String): Flow<String> = flow {
        val data = buildJsonObject {
            put("type", "offers")
            putJsonArray("offers") {
                addJsonObject {
                    put("title", "Premium Cash Back Card")
                    put("rate", "5%")
                    put("tag", "Most Popular")
                    put("description", "on every purchase, every day")
                }
                addJsonObject {
                    put("title", "Auto Loan Refi")
                    put("rate", "4.9%")
                    put("tag", "Low Rate")
                    put("description", "APR for well-qualified buyers")
                }
                addJsonObject {
                    put("title", "Home Equity Line")
                    put("rate", "6.75%")
                    put("tag", "Flexible")
                    put("description", "variable APR, up to \$500k")
                }
                addJsonObject {
                    put("title", "Personal Loan")
                    put("rate", "8.99%")
                    put("tag", "Fast Approval")
                    put("description", "funds in as little as 1 day")
                }
            }
        }
        val rcBase64 = buildRcDocument(data)
        emit(buildJsonObject { put("rc", rcBase64) }.toString())
    }
}
