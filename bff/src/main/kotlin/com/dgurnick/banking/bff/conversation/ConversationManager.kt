package com.dgurnick.banking.bff.conversation

import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("banking.conversation")

/** Represents a single message in the conversation. */
data class Message(
        val role: String, // "user" or "assistant"
        val content: String,
        val timestamp: Long = System.currentTimeMillis()
)

/** Represents a conversation with its history. */
data class Conversation(
        val surfaceId: String,
        val messages: MutableList<Message> = mutableListOf(),
        var lastActivity: Long = System.currentTimeMillis()
) {
  fun addUserMessage(content: String) {
    val msg = Message(role = "user", content = content)
    messages.add(msg)
    lastActivity = System.currentTimeMillis()
    ConversationLogger.logMessage(surfaceId, "CUSTOMER", content)
  }

  fun addAssistantMessage(content: String) {
    val msg = Message(role = "assistant", content = content)
    messages.add(msg)
    lastActivity = System.currentTimeMillis()
    ConversationLogger.logMessage(surfaceId, "AGENT", content)
  }

  /** Get the last N messages from the conversation. */
  fun getRecentMessages(count: Int = 10): List<Message> {
    return messages.takeLast(count)
  }

  /** Check if a specific agent has been active in this conversation. */
  fun hasAgentContext(agentKeyword: String): Boolean {
    return messages.any { it.content.contains(agentKeyword, ignoreCase = true) }
  }
}

/**
 * Logs conversation messages to a plain text file.
 *
 * Log format: [TIMESTAMP] [SURFACE_ID] [SOURCE] MESSAGE Log file location:
 * bff/conversation-logs/conversations.log
 */
object ConversationLogger {
  private val logDir = File("conversation-logs")
  private val logFile = File(logDir, "conversations.log")
  private val dateFormatter =
          DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZoneId.systemDefault())

  init {
    if (!logDir.exists()) {
      logDir.mkdirs()
      log.info("Created conversation log directory: ${logDir.absolutePath}")
    }
  }

  /**
   * Log a message to the conversation log file.
   *
   * @param surfaceId The surface/session identifier
   * @param source Either "CUSTOMER" or "AGENT"
   * @param message The message content
   */
  @Synchronized
  fun logMessage(surfaceId: String, source: String, message: String) {
    try {
      val timestamp = dateFormatter.format(Instant.now())
      // Truncate long messages (like RC base64) for readability
      val displayMessage =
              if (message.length > 500) {
                message.take(200) + "... [truncated, ${message.length} chars total]"
              } else {
                message
              }
      // Escape newlines for single-line log entries
      val escapedMessage = displayMessage.replace("\n", "\\n")
      val logLine = "[$timestamp] [$surfaceId] [$source] $escapedMessage\n"
      logFile.appendText(logLine)
    } catch (e: Exception) {
      log.error("Failed to write to conversation log: ${e.message}")
    }
  }

  /** Log a session event (start, end, etc.) */
  @Synchronized
  fun logEvent(surfaceId: String, event: String) {
    try {
      val timestamp = dateFormatter.format(Instant.now())
      val logLine = "[$timestamp] [$surfaceId] [EVENT] $event\n"
      logFile.appendText(logLine)
    } catch (e: Exception) {
      log.error("Failed to write to conversation log: ${e.message}")
    }
  }
}

/**
 * Manages conversations per surfaceId.
 *
 * Thread-safe singleton that tracks conversation history across requests.
 */
object ConversationManager {
  private val conversations = ConcurrentHashMap<String, Conversation>()

  // Auto-expire conversations after 30 minutes of inactivity
  private const val EXPIRY_MS = 30 * 60 * 1000L

  /** Get or create a conversation for a given surfaceId. */
  fun getConversation(surfaceId: String): Conversation {
    cleanupExpiredConversations()
    return conversations.getOrPut(surfaceId) {
      log.info("Creating new conversation for surfaceId=$surfaceId")
      ConversationLogger.logEvent(surfaceId, "SESSION_START")
      Conversation(surfaceId)
    }
  }

  /** Clear a specific conversation. */
  fun clearConversation(surfaceId: String) {
    conversations.remove(surfaceId)
    ConversationLogger.logEvent(surfaceId, "SESSION_CLEARED")
    log.info("Cleared conversation for surfaceId=$surfaceId")
  }

  /** Clear all conversations. */
  fun clearAll() {
    conversations.keys.forEach { surfaceId ->
      ConversationLogger.logEvent(surfaceId, "SESSION_CLEARED_ALL")
    }
    conversations.clear()
    log.info("Cleared all conversations")
  }

  private fun cleanupExpiredConversations() {
    val now = System.currentTimeMillis()
    conversations.entries.removeIf { (surfaceId, conv) ->
      val expired = (now - conv.lastActivity) > EXPIRY_MS
      if (expired) {
        ConversationLogger.logEvent(
                surfaceId,
                "SESSION_EXPIRED (inactive ${(now - conv.lastActivity) / 1000}s)"
        )
        log.info(
                "Expiring conversation for surfaceId=$surfaceId (inactive for ${(now - conv.lastActivity) / 1000}s)"
        )
      }
      expired
    }
  }
}
