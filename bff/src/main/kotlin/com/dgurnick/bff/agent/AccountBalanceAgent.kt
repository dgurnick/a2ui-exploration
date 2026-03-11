package com.dgurnick.bff.agent

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.dgurnick.bff.model.*
import com.dgurnick.bff.usecase.UseCase

private val balJson = Json { explicitNulls = false }

/**
 * Banking use-case: "What is my account balance?"
 *
 * Renders a greeting header, a templated list of account balance cards
 * (checking, savings, credit card), and a net-worth summary card.
 */
class AccountBalanceAgent : UseCase {

    override fun canHandle(prompt: String): Boolean {
        val p = prompt.lowercase()
        return p.contains("balance") || p.contains("account") ||
               p.contains("statement") || p.contains("transaction")
    }

    override fun generate(prompt: String, surfaceId: String): Flow<String> = flow {
        val d = 40L
        fun su(componentId: String, def: ComponentDefinition): String =
            balJson.encodeToString(SurfaceUpdateMessage(
                surfaceUpdate = SurfaceUpdatePayload(
                    surfaceId = surfaceId,
                    components = listOf(ComponentEntry(id = componentId, component = def))
                )
            ))

        // ── 1. Root layout ────────────────────────────────────────────────────
        emit(su("root", component("Column") {
            putChildren("greeting_row", "accounts_list", "net_worth_card")
        }))
        delay(d)

        // ── 2. Greeting header ────────────────────────────────────────────────
        emit(su("greeting_row", component("Row") {
            putString("alignment", "spaceBetween")
            putChildren("greeting_text", "updated_text")
        }))
        delay(d)

        emit(su("greeting_text", component("Text") {
            putLiteralString("text", "Good morning, Alex")
            putString("usageHint", "h2")
        }))
        delay(d)

        emit(su("updated_text", component("Text") {
            putLiteralString("text", "Updated just now")
            putString("usageHint", "label")
        }))
        delay(d)

        // ── 3. Accounts list (template) ───────────────────────────────────────
        emit(su("accounts_list", component("List") {
            putTemplateChildren("/accounts", "account_template")
        }))
        delay(d)

        emit(su("account_template", component("Card") {
            putChild("account_content")
        }))
        delay(d)

        emit(su("account_content", component("Column") {
            putChildren("account_header_row", "balance_row", "available_row", "txn_btn")
        }))
        delay(d)

        emit(su("account_header_row", component("Row") {
            putString("alignment", "spaceBetween")
            putChildren("account_name_text", "account_number_text")
        }))
        delay(d)

        emit(su("account_name_text", component("Text") {
            putPath("text", "name")
            putString("usageHint", "h3")
        }))
        delay(d)

        emit(su("account_number_text", component("Text") {
            putPath("text", "number")
            putString("usageHint", "label")
        }))
        delay(d)

        emit(su("balance_row", component("Row") {
            putString("alignment", "spaceBetween")
            putChildren("balance_label", "balance_amount")
        }))
        delay(d)

        emit(su("balance_label", component("Text") {
            putLiteralString("text", "Balance")
        }))
        delay(d)

        emit(su("balance_amount", component("Text") {
            putPath("text", "balance")
            putString("usageHint", "h2")
        }))
        delay(d)

        emit(su("available_row", component("Row") {
            putString("alignment", "spaceBetween")
            putChildren("available_label", "available_amount")
        }))
        delay(d)

        emit(su("available_label", component("Text") {
            putLiteralString("text", "Available")
        }))
        delay(d)

        emit(su("available_amount", component("Text") {
            putPath("text", "available")
        }))
        delay(d)

        emit(su("txn_btn", component("Button") {
            putChild("txn_btn_lbl")
            putActionWithPath("view_transactions", "account_id" to "id")
        }))
        delay(d)

        emit(su("txn_btn_lbl", component("Text") {
            putLiteralString("text", "View Transactions")
        }))
        delay(d)

        // ── 4. Net worth summary card ─────────────────────────────────────────
        emit(su("net_worth_card", component("Card") {
            putChild("net_worth_content")
        }))
        delay(d)

        emit(su("net_worth_content", component("Row") {
            putString("alignment", "spaceBetween")
            putChildren("net_worth_label", "net_worth_amount")
        }))
        delay(d)

        emit(su("net_worth_label", component("Text") {
            putLiteralString("text", "Net Worth")
            putString("usageHint", "h3")
        }))
        delay(d)

        emit(su("net_worth_amount", component("Text") {
            putPath("text", "/summary/net_worth")
            putString("usageHint", "h2")
        }))
        delay(d)

        // ── 5. Data model ─────────────────────────────────────────────────────
        emit(balJson.encodeToString(DataModelUpdateMessage(
            dataModelUpdate = DataModelUpdatePayload(
                surfaceId = surfaceId,
                contents = listOf(
                    DataEntry(key = "accounts", valueMap = sampleAccounts()),
                    DataEntry(key = "summary", valueMap = listOf(
                        DataEntry(key = "net_worth", valueString = "$\u200B42,350.17")
                    ))
                )
            )
        )))
        delay(d)

        // ── 6. Trigger render ─────────────────────────────────────────────────
        emit(balJson.encodeToString(BeginRenderingMessage(
            beginRendering = BeginRenderingPayload(surfaceId = surfaceId, root = "root")
        )))
    }

    private fun sampleAccounts(): List<DataEntry> = listOf(
        DataEntry(key = "0", valueMap = listOf(
            DataEntry(key = "id", valueString = "acc1"),
            DataEntry(key = "name", valueString = "Everyday Checking"),
            DataEntry(key = "number", valueString = "••••4821"),
            DataEntry(key = "balance", valueString = "$\u200B3,240.55"),
            DataEntry(key = "available", valueString = "$\u200B3,190.55"),
            DataEntry(key = "type", valueString = "checking")
        )),
        DataEntry(key = "1", valueMap = listOf(
            DataEntry(key = "id", valueString = "acc2"),
            DataEntry(key = "name", valueString = "High-Yield Savings"),
            DataEntry(key = "number", valueString = "••••9037"),
            DataEntry(key = "balance", valueString = "$\u200B41,109.62"),
            DataEntry(key = "available", valueString = "$\u200B41,109.62"),
            DataEntry(key = "type", valueString = "savings")
        )),
        DataEntry(key = "2", valueMap = listOf(
            DataEntry(key = "id", valueString = "acc3"),
            DataEntry(key = "name", valueString = "Rewards Credit Card"),
            DataEntry(key = "number", valueString = "••••1154"),
            DataEntry(key = "balance", valueString = "-$\u200B2,000.00"),
            DataEntry(key = "available", valueString = "$\u200B8,000.00"),
            DataEntry(key = "type", valueString = "credit")
        ))
    )
}
