package com.dgurnick.banking.bff.agent

import com.dgurnick.banking.bff.conversation.Conversation
import com.dgurnick.banking.bff.conversation.Message
import com.dgurnick.banking.bff.usecase.UseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("banking.agent.summary")

/**
 * SummaryAgent handles end-of-conversation scenarios. When a user indicates they're done (e.g., "No
 * thanks", "That's all"), this agent generates a summary of the conversation and offers a "Start
 * over" button.
 */
class SummaryAgent : UseCase {

  override fun canHandle(prompt: String, conversation: Conversation): Boolean {
    val p = prompt.lowercase()

    // Handle explicit goodbye/done phrases
    val isDone =
            p.contains("no, that's all") ||
                    p.contains("no thanks") ||
                    p.contains("that's all for now") ||
                    p.contains("nothing else") ||
                    p.contains("i'm done") ||
                    p.contains("goodbye") ||
                    p.contains("bye")

    // Handle "Start over" to reset conversation
    val isStartOver = p.contains("start over") || p.contains("start new")

    val shouldHandle = isDone || isStartOver
    log.info("SummaryAgent.canHandle: prompt='$p', shouldHandle=$shouldHandle")
    return shouldHandle
  }

  override fun generate(
          prompt: String,
          surfaceId: String,
          conversation: Conversation
  ): Flow<String> = flow {
    val p = prompt.lowercase()
    log.info("SummaryAgent.generate: prompt='$p'")

    // Handle "Start over" - return a special action type
    if (p.contains("start over") || p.contains("start new")) {
      emit(
              buildJsonObject {
                        put("type", "action")
                        put("action", "reset_conversation")
                        put("text", "Starting a fresh conversation...")
                      }
                      .toString()
      )
      return@flow
    }

    // Generate conversation summary
    val summary = generateSummary(conversation)

    emit(
            buildJsonObject {
                      put("type", "chat")
                      put(
                              "text",
                              """
        |👋 **Thank you for banking with us today!**
        |
        |$summary
        |
        |Have a great day! If you need anything else, just start a new conversation.
      """.trimMargin()
                      )
                      putJsonArray("buttons") { add("Start over") }
                    }
                    .toString()
    )
  }

  /** Analyzes conversation history to generate a helpful summary of what was accomplished. */
  private fun generateSummary(conversation: Conversation): String {
    val messages = conversation.messages
    val summaryItems = mutableListOf<String>()

    // Scan conversation for key activities
    for (msg in messages) {
      val content = msg.content.lowercase()

      // Loan application started
      if (msg.role == "assistant" && content.contains("starting your application")) {
        // Find the loan details from earlier in conversation
        val loanAmount = extractLoanAmount(messages)
        val loanPurpose = extractLoanPurpose(messages)
        summaryItems.add(
                "📋 **Personal Loan Application Started**\n   • Amount: $loanAmount\n   • Purpose: $loanPurpose\n   • A loan specialist will contact you within 24 hours"
        )
      }

      // ATM lookup
      if (msg.role == "user" && (content.contains("atm") || content.contains("nearest"))) {
        summaryItems.add("📍 **ATM Locations Provided**")
      }

      // Account balance check
      if (msg.role == "user" && content.contains("balance")) {
        summaryItems.add("💰 **Account Balance Checked**")
      }

      // Offers viewed
      if (msg.role == "user" && content.contains("offers")) {
        summaryItems.add("🎁 **Personal Offers Reviewed**")
      }
    }

    // Remove duplicates and build summary
    val uniqueItems = summaryItems.toSet().toList()

    if (uniqueItems.isEmpty()) {
      return "📝 **Session Summary**\n   Thanks for chatting with us!"
    }

    return "📝 **Here's what we covered today:**\n\n${uniqueItems.joinToString("\n\n")}"
  }

  private fun extractLoanAmount(messages: List<Message>): String {
    for (msg in messages) {
      if (msg.role == "user") {
        val content = msg.content
        if (content.contains("$") && content.contains("-")) {
          // Extract amount range like "$5,000 - $10,000"
          val amountMatch = Regex("\\$([\\d,]+)\\s*-\\s*\\$([\\d,]+)").find(content)
          if (amountMatch != null) {
            return amountMatch.value
          }
        }
      }
    }
    return "Not specified"
  }

  private fun extractLoanPurpose(messages: List<Message>): String {
    val purposes =
            listOf(
                    "Debt Consolidation",
                    "Home Improvement",
                    "Major Purchase",
                    "Emergency Expenses",
                    "Other Purpose"
            )
    for (msg in messages) {
      if (msg.role == "user") {
        for (purpose in purposes) {
          if (msg.content.contains(purpose, ignoreCase = true)) {
            return purpose
          }
        }
      }
    }
    return "Not specified"
  }
}
