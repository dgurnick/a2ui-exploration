package com.dgurnick.banking.bff.agent

import com.dgurnick.banking.bff.conversation.Conversation
import com.dgurnick.banking.bff.usecase.UseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("banking.loan")

/**
 * Banking use-case: Personal Loan workflow with conversational questions.
 *
 * This agent handles the loan application workflow by asking questions and providing personalized
 * loan details based on user responses. It uses conversation history to track workflow state.
 */
class LoanOffersAgent : UseCase {

  // Mock account balance - in production this would come from core banking API
  // These values match AccountBalanceAgent for consistency
  companion object {
    const val MOCK_CHECKING_BALANCE = 3240.55
    const val MOCK_SAVINGS_BALANCE = 41109.62
    // Max loan amount is 80% of combined balances (creditworthiness heuristic)
    val MAX_LOAN_AMOUNT = ((MOCK_CHECKING_BALANCE + MOCK_SAVINGS_BALANCE) * 0.8).toInt()
  }

  private enum class LoanStep {
    INITIAL, // User just asked about loans
    WAITING_AMOUNT, // We asked for amount, waiting for response
    INSUFFICIENT_BALANCE, // User selected amount exceeding their limit
    WAITING_PURPOSE, // We asked for purpose, waiting for response
    OFFER_SHOWN // Final offer has been shown
  }

  override fun canHandle(prompt: String, conversation: Conversation): Boolean {
    val p = prompt.lowercase()

    // Check conversation state to see if we're in a loan workflow
    val step = getLoanStep(conversation)
    log.info("LoanOffersAgent.canHandle: step=$step, prompt='$prompt'")

    // If we're in a loan conversation, only continue for loan-related prompts
    when (step) {
      LoanStep.WAITING_AMOUNT -> {
        // Only handle amount selections
        return p.contains("$") ||
                p.contains("1,000") ||
                p.contains("5,000") ||
                p.contains("10,000") ||
                p.contains("25,000") ||
                p.contains("50,000")
      }
      LoanStep.INSUFFICIENT_BALANCE -> {
        // Handle revised amount selections or exit
        return p.contains("$") ||
                p.contains("5,000") ||
                p.contains("10,000") ||
                p.contains("15,000") ||
                p.contains("20,000") ||
                p.contains("25,000") ||
                p.contains("30,000") ||
                p.contains("explore other")
      }
      LoanStep.WAITING_PURPOSE -> {
        // Only handle purpose selections
        return p.contains("consolidation") ||
                p.contains("improvement") ||
                p.contains("purchase") ||
                p.contains("emergency") ||
                p.contains("other purpose")
      }
      LoanStep.OFFER_SHOWN -> {
        // Don't handle goodbye phrases - let SummaryAgent handle those
        val isGoodbye =
                p.contains("that's all") ||
                        p.contains("no thanks") ||
                        p.contains("nothing else") ||
                        p.contains("i'm done") ||
                        p.contains("goodbye") ||
                        p.contains("bye")
        if (isGoodbye) return false

        // Don't handle "start over" - client handles this directly
        if (p.contains("start over") || p.contains("start new")) return false

        // Only handle post-offer responses
        return p.contains("yes") ||
                p.contains("start") ||
                p.contains("application") ||
                p.contains("other") ||
                p.contains("option") ||
                p.contains("no") ||
                p.contains("maybe") ||
                p.contains("later")
      }
      LoanStep.INITIAL -> {
        // Check if this starts a new loan conversation
        return p.contains("personal loan") ||
                p.contains("interested") && p.contains("loan") ||
                p.contains("learn more") && p.contains("loan") ||
                p.contains("i need") && (p.contains("$") || p.contains("loan")) ||
                p.contains("borrow")
      }
    }
  }

  private fun getLoanStep(conversation: Conversation): LoanStep {
    val messages = conversation.messages

    // Look backwards through assistant messages to find current state
    for (i in messages.indices.reversed()) {
      val msg = messages[i]
      if (msg.role == "assistant") {
        when {
          msg.content.contains("personalized offer") ||
                  msg.content.contains("Est. Monthly Payment") -> return LoanStep.OFFER_SHOWN
          msg.content.contains("What will you be using this loan for") ->
                  return LoanStep.WAITING_PURPOSE
          msg.content.contains("exceeds your current account balance") ||
                  msg.content.contains("within your approved limit") ->
                  return LoanStep.INSUFFICIENT_BALANCE
          msg.content.contains("How much are you looking to borrow") ->
                  return LoanStep.WAITING_AMOUNT
        }
      }
    }
    return LoanStep.INITIAL
  }

  override fun generate(
          prompt: String,
          surfaceId: String,
          conversation: Conversation
  ): Flow<String> = flow {
    val step = getLoanStep(conversation)
    log.info("LoanOffersAgent.generate: step=$step, prompt='$prompt'")

    when (step) {
      LoanStep.WAITING_PURPOSE -> {
        // User selected loan purpose - show final offer
        emit(generateFinalOffer(prompt, conversation))
      }
      LoanStep.WAITING_AMOUNT -> {
        // User selected loan amount - check if it exceeds their limit
        val requestedAmount = extractAmountValue(prompt)
        if (requestedAmount > MAX_LOAN_AMOUNT) {
          // Amount exceeds their limit - offer alternatives
          emit(generateInsufficientBalanceResponse(requestedAmount))
        } else {
          // Amount is within limit - ask about purpose
          emit(generatePurposeQuestion(extractAmount(prompt)))
        }
      }
      LoanStep.INSUFFICIENT_BALANCE -> {
        // User selected a revised amount after insufficient balance
        val p = prompt.lowercase()
        if (p.contains("explore other")) {
          // User wants to explore other options
          emit(
                  buildJsonObject {
                            put("type", "chat")
                            put(
                                    "text",
                                    "No problem! Let me know if you'd like to explore other financial options. Is there anything else I can help you with?"
                            )
                            putJsonArray("buttons") {
                              add("Check my account balance")
                              add("Find nearby ATMs")
                              add("What offers do you have?")
                            }
                          }
                          .toString()
          )
        } else {
          // User selected a new amount within their limit
          emit(generatePurposeQuestion(extractAmount(prompt)))
        }
      }
      LoanStep.OFFER_SHOWN -> {
        // Handle post-offer actions
        emit(handlePostOffer(prompt))
      }
      LoanStep.INITIAL -> {
        // Initial loan interest - ask about amount
        emit(generateAmountQuestion())
      }
    }
  }

  private fun extractAmountValue(prompt: String): Int {
    return when {
      prompt.contains("50,000") -> 50000
      prompt.contains("25,000") && prompt.contains("50,000") -> 50000
      prompt.contains("25,000") -> 25000
      prompt.contains("10,000") && prompt.contains("25,000") -> 25000
      prompt.contains("10,000") -> 10000
      prompt.contains("5,000") && prompt.contains("10,000") -> 10000
      prompt.contains("5,000") -> 5000
      prompt.contains("1,000") && prompt.contains("5,000") -> 5000
      prompt.contains("1,000") -> 1000
      prompt.contains("30,000") -> 30000
      prompt.contains("20,000") -> 20000
      prompt.contains("15,000") -> 15000
      else -> 10000
    }
  }

  private fun extractAmount(prompt: String): String {
    return when {
      prompt.contains("50,000") || prompt.contains("25,000 - 50,000") -> "$50,000"
      prompt.contains("25,000") || prompt.contains("10,000 - 25,000") -> "$25,000"
      prompt.contains("10,000") || prompt.contains("5,000 - 10,000") -> "$10,000"
      prompt.contains("5,000") || prompt.contains("1,000 - 5,000") -> "$5,000"
      prompt.contains("30,000") -> "$30,000"
      prompt.contains("20,000") -> "$20,000"
      prompt.contains("15,000") -> "$15,000"
      prompt.contains("1,000") -> "$5,000"
      else -> "$10,000"
    }
  }

  private fun generateInsufficientBalanceResponse(requestedAmount: Int): String {
    val combinedBalance = MOCK_CHECKING_BALANCE + MOCK_SAVINGS_BALANCE
    val formattedBalance = "%,.2f".format(combinedBalance)
    val formattedMax = "%,d".format(MAX_LOAN_AMOUNT)
    val formattedRequested = "%,d".format(requestedAmount)

    // Generate alternative amounts within their limit
    val alternatives = mutableListOf<String>()
    if (MAX_LOAN_AMOUNT >= 30000) alternatives.add("$30,000")
    if (MAX_LOAN_AMOUNT >= 25000) alternatives.add("$25,000")
    if (MAX_LOAN_AMOUNT >= 20000) alternatives.add("$20,000")
    if (MAX_LOAN_AMOUNT >= 15000) alternatives.add("$15,000")
    if (MAX_LOAN_AMOUNT >= 10000) alternatives.add("$10,000")
    if (MAX_LOAN_AMOUNT >= 5000) alternatives.add("$5,000")
    alternatives.add("I'd like to explore other options")

    return buildJsonObject {
              put("type", "chat")
              put(
                      "text",
                      """⚠️ **Amount Exceeds Limit**

The amount you requested ($$formattedRequested) exceeds your pre-approved limit.

Based on your combined account balance of $$formattedBalance, you're approved for loans up to **$$formattedMax**.

Please select an amount within your approved limit:"""
              )
              putJsonArray("buttons") { alternatives.take(4).forEach { add(it) } }
            }
            .toString()
  }

  private fun generateAmountQuestion(): String {
    return buildJsonObject {
              put("type", "chat")
              put(
                      "text",
                      "Great choice! Our Personal Loan offers competitive rates with fast approval. How much are you looking to borrow?"
              )
              putJsonArray("buttons") {
                add("$1,000 - $5,000")
                add("$5,000 - $10,000")
                add("$10,000 - $25,000")
                add("$25,000 - $50,000")
              }
            }
            .toString()
  }

  private fun generatePurposeQuestion(amount: String): String {
    return buildJsonObject {
              put("type", "chat")
              put(
                      "text",
                      "Perfect! For a loan around $amount, we have some great options. What will you be using this loan for?"
              )
              putJsonArray("buttons") {
                add("Debt Consolidation")
                add("Home Improvement")
                add("Major Purchase")
                add("Emergency Expenses")
                add("Other Purpose")
              }
            }
            .toString()
  }

  private fun generateFinalOffer(prompt: String, conversation: Conversation): String {
    val purpose =
            when {
              prompt.contains("debt consolidation", ignoreCase = true) -> "debt consolidation"
              prompt.contains("home improvement", ignoreCase = true) -> "home improvement"
              prompt.contains("major purchase", ignoreCase = true) -> "your major purchase"
              prompt.contains("emergency", ignoreCase = true) -> "emergency expenses"
              else -> "your needs"
            }

    // Extract the selected amount from conversation history
    val selectedAmount =
            conversation
                    .messages
                    .filter { it.role == "user" }
                    .map { it.content }
                    .firstOrNull { it.contains("$") }
                    ?.let { extractAmount(it) }
                    ?: "$10,000"

    // Calculate rates based on amount and purpose
    val amountValue = selectedAmount.replace("$", "").replace(",", "").toIntOrNull() ?: 10000
    val baseRate = if (purpose == "debt consolidation") 7.99 else 8.99
    val rate = if (amountValue >= 25000) baseRate - 0.5 else baseRate
    val monthlyPayment = (amountValue * (1 + rate / 100) / 36).toInt()

    return buildJsonObject {
              put("type", "chat")
              put(
                      "text",
                      """Based on your needs for $purpose, here's your personalized offer:

📋 **Personal Loan Offer**
━━━━━━━━━━━━━━━━━━━━
💰 **Amount:** $selectedAmount
💰 **Rate:** ${"%.2f".format(rate)}% APR
📅 **Term:** 36 months
💵 **Est. Monthly Payment:** $$monthlyPayment

✅ **Benefits:**
• No origination fees
• Fixed monthly payments
• No prepayment penalties
• Funds as soon as next business day

Would you like to proceed with your application?"""
              )
              putJsonArray("buttons") {
                add("Yes, start my application")
                add("See other loan options")
                add("No thanks, maybe later")
              }
            }
            .toString()
  }

  private fun handlePostOffer(prompt: String): String {
    val p = prompt.lowercase()
    return when {
      p.contains("yes") || p.contains("start") || p.contains("application") -> {
        buildJsonObject {
                  put("type", "chat")
                  put(
                          "text",
                          """🎉 **Great choice!**

I'm starting your application now. You'll need:
• Valid government ID
• Proof of income (pay stubs or tax returns)
• Bank account information

A loan specialist will be in touch within 24 hours to complete your application.

Is there anything else I can help you with?"""
                  )
                  putJsonArray("buttons") {
                    add("Check my account balance")
                    add("Find nearby ATMs")
                    add("No, that's all for now")
                  }
                }
                .toString()
      }
      p.contains("other") || p.contains("options") -> {
        buildJsonObject {
                  put("type", "chat")
                  put(
                          "text",
                          "Let me show you our other loan options. What would you like to explore?"
                  )
                  putJsonArray("buttons") {
                    add("Home Equity Line of Credit")
                    add("Auto Loan")
                    add("Credit Card with Balance Transfer")
                    add("Go back to Personal Loan")
                  }
                }
                .toString()
      }
      else -> {
        buildJsonObject {
                  put("type", "chat")
                  put(
                          "text",
                          "No problem! Your personalized offer will be saved for 30 days if you change your mind. Is there anything else I can help you with today?"
                  )
                  putJsonArray("buttons") {
                    add("Check my account balance")
                    add("Find nearby ATMs")
                    add("What offers do you have?")
                  }
                }
                .toString()
      }
    }
  }
}
