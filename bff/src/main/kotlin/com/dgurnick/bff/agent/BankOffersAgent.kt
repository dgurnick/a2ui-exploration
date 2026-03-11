package com.dgurnick.bff.agent

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.dgurnick.bff.model.*
import com.dgurnick.bff.usecase.UseCase

private val offersJson = Json { explicitNulls = false }

/**
 * Banking use-case: "What offers do you have for me?"
 *
 * Renders a personalised offers list — each offer card shows a title, tag,
 * description, expiry date, and an "Apply Now" action button.
 */
class BankOffersAgent : UseCase {

    override fun canHandle(prompt: String): Boolean {
        val p = prompt.lowercase()
        return p.contains("offer") || p.contains("deal") ||
               p.contains("promotion") || p.contains("promo") ||
               p.contains("rate") || p.contains("loan")
    }

    override fun generate(prompt: String, surfaceId: String): Flow<String> = flow {
        val d = 40L
        fun su(componentId: String, def: ComponentDefinition): String =
            offersJson.encodeToString(SurfaceUpdateMessage(
                surfaceUpdate = SurfaceUpdatePayload(
                    surfaceId = surfaceId,
                    components = listOf(ComponentEntry(id = componentId, component = def))
                )
            ))

        // ── 1. Root layout ────────────────────────────────────────────────────
        emit(su("root", component("Column") {
            putChildren("offers_header_row", "offers_list")
        }))
        delay(d)

        // ── 2. Header ─────────────────────────────────────────────────────────
        emit(su("offers_header_row", component("Row") {
            putString("alignment", "spaceBetween")
            putChildren("offers_title", "offers_count")
        }))
        delay(d)

        emit(su("offers_title", component("Text") {
            putLiteralString("text", "Your Personalised Offers")
            putString("usageHint", "h2")
        }))
        delay(d)

        emit(su("offers_count", component("Text") {
            putPath("text", "/summary/count")
            putString("usageHint", "label")
        }))
        delay(d)

        // ── 3. Offers list (template) ─────────────────────────────────────────
        emit(su("offers_list", component("List") {
            putTemplateChildren("/offers", "offer_template")
        }))
        delay(d)

        emit(su("offer_template", component("Card") {
            putChild("offer_content")
        }))
        delay(d)

        emit(su("offer_content", component("Column") {
            putChildren("offer_header_row", "offer_description", "offer_expires", "offer_btn")
        }))
        delay(d)

        emit(su("offer_header_row", component("Row") {
            putString("alignment", "spaceBetween")
            putChildren("offer_title", "offer_tag")
        }))
        delay(d)

        emit(su("offer_title", component("Text") {
            putPath("text", "title")
            putString("usageHint", "h3")
        }))
        delay(d)

        emit(su("offer_tag", component("Text") {
            putPath("text", "tag")
            putString("usageHint", "label")
        }))
        delay(d)

        emit(su("offer_description", component("Text") {
            putPath("text", "description")
        }))
        delay(d)

        emit(su("offer_expires", component("Text") {
            putPath("text", "expires")
            putString("usageHint", "label")
        }))
        delay(d)

        emit(su("offer_btn", component("Button") {
            putChild("offer_btn_lbl")
            putActionWithPath("apply_offer", "offer_id" to "id")
        }))
        delay(d)

        emit(su("offer_btn_lbl", component("Text") {
            putLiteralString("text", "Apply Now")
        }))
        delay(d)

        // ── 4. Data model ─────────────────────────────────────────────────────
        emit(offersJson.encodeToString(DataModelUpdateMessage(
            dataModelUpdate = DataModelUpdatePayload(
                surfaceId = surfaceId,
                contents = listOf(
                    DataEntry(key = "offers", valueMap = sampleOffers()),
                    DataEntry(key = "summary", valueMap = listOf(
                        DataEntry(key = "count", valueString = "4 offers")
                    ))
                )
            )
        )))
        delay(d)

        // ── 5. Trigger render ─────────────────────────────────────────────────
        emit(offersJson.encodeToString(BeginRenderingMessage(
            beginRendering = BeginRenderingPayload(surfaceId = surfaceId, root = "root")
        )))
    }

    private fun sampleOffers(): List<DataEntry> = listOf(
        DataEntry(key = "0", valueMap = listOf(
            DataEntry(key = "id", valueString = "offer1"),
            DataEntry(key = "title", valueString = "0% APR Balance Transfer"),
            DataEntry(key = "tag", valueString = "Credit Card"),
            DataEntry(key = "description", valueString = "Transfer your existing balances and pay 0% interest for 18 months. No transfer fee for the first 60 days."),
            DataEntry(key = "expires", valueString = "Expires 30 Apr 2026")
        )),
        DataEntry(key = "1", valueMap = listOf(
            DataEntry(key = "id", valueString = "offer2"),
            DataEntry(key = "title", valueString = "5.20% High-Yield Savings Rate"),
            DataEntry(key = "tag", valueString = "Savings"),
            DataEntry(key = "description", valueString = "Boost your savings with our highest rate yet. Available to existing checking customers — no minimum balance required."),
            DataEntry(key = "expires", valueString = "Expires 15 May 2026")
        )),
        DataEntry(key = "2", valueMap = listOf(
            DataEntry(key = "id", valueString = "offer3"),
            DataEntry(key = "title", valueString = "\$300 Cash Bonus"),
            DataEntry(key = "tag", valueString = "Checking"),
            DataEntry(key = "description", valueString = "Open a new Premium Checking account, set up direct deposit of \$500+, and receive a \$300 welcome bonus within 90 days."),
            DataEntry(key = "expires", valueString = "Expires 1 Jun 2026")
        )),
        DataEntry(key = "3", valueMap = listOf(
            DataEntry(key = "id", valueString = "offer4"),
            DataEntry(key = "title", valueString = "Personal Loan at 7.9% APR"),
            DataEntry(key = "tag", valueString = "Loan"),
            DataEntry(key = "description", valueString = "Based on your profile, you're pre-approved for a personal loan up to \$25,000 at 7.9% APR. Funds deposited within 2 business days."),
            DataEntry(key = "expires", valueString = "Pre-approval valid until 30 Apr 2026")
        ))
    )
}
