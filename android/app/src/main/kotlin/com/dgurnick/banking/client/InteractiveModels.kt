package com.dgurnick.banking.client

import kotlinx.serialization.Serializable

/**
 * Data models for interactive native components that Remote Compose cannot support. RC is static -
 * it cannot handle zoom/pan gestures or expand/collapse interactions.
 */

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
        val ctaUrl: String
)

// ── Response wrapper ─────────────────────────────────────────────────────────

@Serializable
data class BffResponse(
        val type: String,
        val rc: String? = null,
        val mapData: MapData? = null,
        val offersData: OffersData? = null
)
