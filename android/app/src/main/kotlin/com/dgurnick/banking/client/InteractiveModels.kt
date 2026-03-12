package com.dgurnick.banking.client

import java.util.UUID
import kotlinx.serialization.Serializable

/**
 * Data models for interactive native components that Remote Compose cannot support. RC is static -
 * it cannot handle zoom/pan gestures or expand/collapse interactions.
 */

// ── Chat Messages ───────────────────────────────────────────────────────────

sealed class ChatMessage(
        open val id: String = UUID.randomUUID().toString(),
        open val timestamp: Long = System.currentTimeMillis()
) {
  /** Message from the assistant/bot */
  data class BotMessage(
          override val id: String = UUID.randomUUID().toString(),
          override val timestamp: Long = System.currentTimeMillis(),
          val text: String,
          val buttons: List<String> = emptyList(),
          val buttonsUsed: Boolean = false // Track if buttons have been clicked
  ) : ChatMessage(id, timestamp)

  /** Message from the user */
  data class UserMessage(
          override val id: String = UUID.randomUUID().toString(),
          override val timestamp: Long = System.currentTimeMillis(),
          val text: String
  ) : ChatMessage(id, timestamp)

  /** Content response (map, offers, RC document) */
  data class ContentMessage(
          override val id: String = UUID.randomUUID().toString(),
          override val timestamp: Long = System.currentTimeMillis(),
          val mapData: MapData? = null,
          val offersData: OffersData? = null,
          val rcDocument: ByteArray? = null,
          val selectedOfferId: String? = null // Track selected offer (hides others)
  ) : ChatMessage(id, timestamp)

  /** Loading indicator message */
  data class LoadingMessage(
          override val id: String = UUID.randomUUID().toString(),
          override val timestamp: Long = System.currentTimeMillis()
  ) : ChatMessage(id, timestamp)
}

// ── Map Data (for ATM finder) ────────────────────────────────────────────────

@Serializable
data class MapData(
        val userLat: Double,
        val userLon: Double,
        val title: String,
        val markers: List<MapMarker>
)

@Serializable
data class MapMarker(
        val id: String,
        val lat: Double,
        val lon: Double,
        val title: String,
        val snippet: String
)

// ── Offers Data (for expandable cards) ───────────────────────────────────────

@Serializable data class OffersData(val title: String, val offers: List<Offer>)

@Serializable
data class Offer(
        val id: String,
        val title: String,
        val rate: String,
        val rateLabel: String,
        val tag: String,
        val summary: String,
        val details: String,
        val ctaText: String,
        val ctaUrl: String = "",
        val ctaAction: String? = null // If set, sends this message instead of opening URL
)

// ── Response wrapper ─────────────────────────────────────────────────────────

@Serializable
data class BffResponse(
        val type: String,
        val rc: String? = null,
        val mapData: MapData? = null,
        val offersData: OffersData? = null
)
