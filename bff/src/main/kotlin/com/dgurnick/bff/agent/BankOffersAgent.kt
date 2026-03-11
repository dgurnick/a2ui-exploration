package com.dgurnick.bff.agent

import com.dgurnick.bff.model.*
import com.dgurnick.bff.usecase.UseCase
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val offersJson = Json { explicitNulls = false }

/**
 * Banking use-case: "What offers do you have for me?"
 *
 * Renders rich OfferCard components — each card shows icon, tag, key rate, and an animated detail
 * panel with bullet highlights and an Apply Now CTA.
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
        val d = 40L
        fun su(id: String, def: ComponentDefinition): String =
                offersJson.encodeToString(
                        SurfaceUpdateMessage(
                                surfaceUpdate =
                                        SurfaceUpdatePayload(
                                                surfaceId = surfaceId,
                                                components =
                                                        listOf(
                                                                ComponentEntry(
                                                                        id = id,
                                                                        component = def
                                                                )
                                                        )
                                        )
                        )
                )

        // ── 1. Root layout ────────────────────────────────────────────────────
        emit(
                su(
                        "root",
                        component("Column") {
                            putChildren(
                                    "offers_header_row",
                                    "offer_card_0",
                                    "offer_card_1",
                                    "offer_card_2",
                                    "offer_card_3"
                            )
                        }
                )
        )
        delay(d)

        // ── 2. Header ─────────────────────────────────────────────────────────
        emit(
                su(
                        "offers_header_row",
                        component("Row") {
                            putString("alignment", "spaceBetween")
                            putChildren("offers_title", "offers_subtitle")
                        }
                )
        )
        delay(d)

        emit(
                su(
                        "offers_title",
                        component("Text") {
                            putLiteralString("text", "Your Personalised Offers")
                            putString("usageHint", "h2")
                        }
                )
        )
        delay(d)

        emit(
                su(
                        "offers_subtitle",
                        component("Text") {
                            putLiteralString("text", "4 offers · tap to expand")
                            putString("usageHint", "label")
                        }
                )
        )
        delay(d)

        // ── 3. Offer cards ────────────────────────────────────────────────────
        emit(
                su(
                        "offer_card_0",
                        component("OfferCard") {
                            putLiteralString("icon", "💳")
                            putLiteralString("title", "0% APR Balance Transfer")
                            putLiteralString("tag", "Credit Card")
                            putLiteralString("rate", "0% interest · 18 months")
                            putLiteralString(
                                    "description",
                                    "Transfer existing balances and pay zero interest for 18 months. " +
                                            "Initiated within the first 60 days means no transfer fee — saving you up to \$300."
                            )
                            putLiteralString(
                                    "highlights",
                                    "No transfer fee for first 60 days" +
                                            "|Up to \$15,000 transfer limit" +
                                            "|Covers all cards & store cards" +
                                            "|Automatic minimum payment protection"
                            )
                            putLiteralString("expires", "Expires 30 Apr 2026")
                            putString("color", "credit")
                            putAction("apply_offer", "offer_id" to "offer_balance_transfer")
                        }
                )
        )
        delay(d)

        emit(
                su(
                        "offer_card_1",
                        component("OfferCard") {
                            putLiteralString("icon", "📈")
                            putLiteralString("title", "High-Yield Savings Account")
                            putLiteralString("tag", "Savings")
                            putLiteralString("rate", "5.20% APR")
                            putLiteralString(
                                    "description",
                                    "Boost your savings with our highest rate ever. Available exclusively to " +
                                            "existing checking account customers. No minimum balance, no lock-in period."
                            )
                            putLiteralString(
                                    "highlights",
                                    "No minimum balance required" +
                                            "|FDIC insured up to \$250,000" +
                                            "|Unlimited free transfers" +
                                            "|Linked directly to your checking account"
                            )
                            putLiteralString("expires", "Rate locked until 15 May 2026")
                            putString("color", "savings")
                            putAction("apply_offer", "offer_id" to "offer_savings")
                        }
                )
        )
        delay(d)

        emit(
                su(
                        "offer_card_2",
                        component("OfferCard") {
                            putLiteralString("icon", "🏦")
                            putLiteralString("title", "\$300 Welcome Bonus")
                            putLiteralString("tag", "Checking")
                            putLiteralString("rate", "\$300 cash bonus")
                            putLiteralString(
                                    "description",
                                    "Open a new Premium Checking account, set up a qualifying direct deposit " +
                                            "of \$500 or more, and receive a \$300 cash bonus deposited within 90 days."
                            )
                            putLiteralString(
                                    "highlights",
                                    "Direct deposit of \$500+ required" +
                                            "|Bonus paid within 90 days" +
                                            "|No monthly fee with \$1,500 avg balance" +
                                            "|Free Visa debit card included"
                            )
                            putLiteralString("expires", "Expires 1 Jun 2026")
                            putString("color", "checking")
                            putAction("apply_offer", "offer_id" to "offer_checking_bonus")
                        }
                )
        )
        delay(d)

        emit(
                su(
                        "offer_card_3",
                        component("OfferCard") {
                            putLiteralString("icon", "💼")
                            putLiteralString("title", "Pre-Approved Personal Loan")
                            putLiteralString("tag", "Loan")
                            putLiteralString("rate", "7.9% APR")
                            putLiteralString(
                                    "description",
                                    "Based on your profile, you're pre-approved for a personal loan up to \$25,000. " +
                                            "Funds are deposited directly to your account within 2 business days of approval."
                            )
                            putLiteralString(
                                    "highlights",
                                    "Up to \$25,000 available" +
                                            "|Funds deposited in 2 business days" +
                                            "|Fixed monthly payments" +
                                            "|No prepayment penalty" +
                                            "|Flexible 24–60 month terms"
                            )
                            putLiteralString("expires", "Pre-approval valid until 30 Apr 2026")
                            putString("color", "loan")
                            putAction("apply_offer", "offer_id" to "offer_personal_loan")
                        }
                )
        )
        delay(d)

        // ── 4. Trigger render ─────────────────────────────────────────────────
        emit(
                offersJson.encodeToString(
                        BeginRenderingMessage(
                                beginRendering =
                                        BeginRenderingPayload(surfaceId = surfaceId, root = "root")
                        )
                )
        )
    }
}

