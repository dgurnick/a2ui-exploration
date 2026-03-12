package com.dgurnick.banking.bff.agent

import com.dgurnick.banking.bff.usecase.UseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.*

/**
 * Banking use-case: "What offers do you have for me?"
 *
 * Emits structured offers data for native expandable card rendering, since Remote Compose cannot
 * support expand/collapse interactions.
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
        val response = buildJsonObject {
            put("type", "offers")
            putJsonObject("offersData") {
                put("title", "Your Personal Offers")
                putJsonArray("offers") {
                    addJsonObject {
                        put("id", "offer1")
                        put("title", "Premium Cash Back Card")
                        put("rate", "5%")
                        put("rateLabel", "cash back")
                        put("tag", "Most Popular")
                        put("summary", "Earn on every purchase, every day")
                        put(
                                "details",
                                "• No annual fee for the first year\n• 5% cash back on all purchases\n• 0% intro APR for 15 months\n• Free FICO score access\n• Cell phone protection included"
                        )
                        put("ctaText", "Apply Now")
                        put("ctaUrl", "https://example.com/apply/cashback")
                    }
                    addJsonObject {
                        put("id", "offer2")
                        put("title", "Auto Loan Refinance")
                        put("rate", "4.9%")
                        put("rateLabel", "APR")
                        put("tag", "Low Rate")
                        put("summary", "Lower your monthly payment")
                        put(
                                "details",
                                "• Rates as low as 4.9% APR\n• No application fees\n• Flexible terms from 24-84 months\n• Quick online application\n• Decision in minutes"
                        )
                        put("ctaText", "Check Your Rate")
                        put("ctaUrl", "https://example.com/apply/auto")
                    }
                    addJsonObject {
                        put("id", "offer3")
                        put("title", "Home Equity Line")
                        put("rate", "6.75%")
                        put("rateLabel", "variable APR")
                        put("tag", "Flexible")
                        put("summary", "Access up to \$500k in equity")
                        put(
                                "details",
                                "• Borrow up to 85% of your home equity\n• Interest-only payment option\n• No closing costs on lines up to \$250k\n• Draw funds as needed\n• Tax-deductible interest*"
                        )
                        put("ctaText", "Get Started")
                        put("ctaUrl", "https://example.com/apply/heloc")
                    }
                    addJsonObject {
                        put("id", "offer4")
                        put("title", "Personal Loan")
                        put("rate", "8.99%")
                        put("rateLabel", "APR")
                        put("tag", "Fast Approval")
                        put("summary", "Funds in as little as 1 day")
                        put(
                                "details",
                                "• Borrow \$1,000 to \$50,000\n• Fixed rates from 8.99% APR\n• No collateral required\n• Same-day approval possible\n• No prepayment penalties"
                        )
                        put("ctaText", "Apply Now")
                        put("ctaUrl", "https://example.com/apply/personal")
                    }
                }
            }
        }
        emit(response.toString())
    }
}
