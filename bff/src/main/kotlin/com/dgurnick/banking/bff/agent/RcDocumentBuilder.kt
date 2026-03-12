package com.dgurnick.banking.bff.agent

import androidx.compose.remote.core.RcPlatformServices
import androidx.compose.remote.creation.RemoteComposeWriter
import java.util.Base64
import kotlinx.serialization.json.*

private const val DOC_WIDTH = 1080
private const val DOC_HEIGHT = 1920
private const val LEFT_PAD = 48f

private const val COLOR_PRIMARY = 0xFF1A237E.toInt()
private const val COLOR_DARK = 0xFF212121.toInt()
private const val COLOR_LIGHT = 0xFF757575.toInt()
private const val COLOR_GREEN = 0xFF2E7D32.toInt()
private const val COLOR_MAP_BG = 0xFFE8EAF6.toInt() // Light indigo background
private const val COLOR_MAP_ROAD = 0xFFFFFFFF.toInt() // White roads
private const val COLOR_MAP_MARKER = 0xFFD32F2F.toInt() // Red marker
private const val COLOR_MAP_USER = 0xFF1976D2.toInt() // Blue for user location
private const val COLOR_ACCENT = 0xFFFF6F00.toInt() // Orange accent for offers

/**
 * Creates a Remote Compose document on the server (BFF) from the typed data payload, serialises it
 * to a byte array, and returns a Base64 string for transmission.
 *
 * Uses RemoteComposeWriter directly with flat canvas drawing ops (PAINT_VALUES + DRAW_TEXT_ANCHOR)
 * to avoid the layout container overhead that triggers "Too many operations" in
 * applyDataOperations.
 */
fun buildRcDocument(data: JsonObject): String {
  val type = data["type"]?.jsonPrimitive?.content ?: "fallback"
  val writer = RemoteComposeWriter(DOC_WIDTH, DOC_HEIGHT, type, RcPlatformServices.None)
  writer.root {
    when (type) {
      "account_balance" -> writer.buildAccountBalance(data)
      "atm_list" -> writer.buildAtmList(data)
      "offers" -> writer.buildOffers(data)
      else -> writer.buildFallback(data)
    }
  }
  // buffer() returns ByteArray - need to find the usedSize
  // RemoteComposeWriter has no direct usedSize property so we check for trailing zeros
  val fullBuffer = writer.buffer()
  val usedSize = fullBuffer.indexOfLast { it != 0.toByte() } + 1
  val trimmedBytes = fullBuffer.copyOf(maxOf(usedSize, 100)) // Keep at least 100 bytes
  return Base64.getEncoder().encodeToString(trimmedBytes)
}

// ── helpers ─────────────────────────────────────────────────────────────────

/**
 * Sets paint properties then draws text anchored at (x, y). panX = -1f → left-aligned at x; panY =
 * NaN → y is the text baseline.
 */
private fun RemoteComposeWriter.textLine(
        text: String,
        x: Float,
        y: Float,
        color: Int,
        fontSize: Float,
) {
  getRcPaint().setAntiAlias(true).setColor(color).setTextSize(fontSize).commit()
  drawTextAnchored(text, x, y, -1f, Float.NaN, 0)
}

// ── use-case builders ────────────────────────────────────────────────────────

private fun RemoteComposeWriter.buildAccountBalance(data: JsonObject) {
  val greeting = data["greeting"]?.jsonPrimitive?.content ?: "Good morning"
  val accounts = data["accounts"]?.jsonArray ?: JsonArray(emptyList())
  val netWorth = data["netWorth"]?.jsonPrimitive?.content ?: ""

  var y = 100f
  textLine(greeting, LEFT_PAD, y, COLOR_PRIMARY, 32f)
  y += 80f
  accounts.forEach { acc ->
    val a = acc.jsonObject
    val name = a["name"]?.jsonPrimitive?.content ?: ""
    val number = a["number"]?.jsonPrimitive?.content ?: ""
    val balance = a["balance"]?.jsonPrimitive?.content ?: ""
    textLine("$name  •  $number", LEFT_PAD, y, COLOR_LIGHT, 20f)
    y += 48f
    textLine(balance, LEFT_PAD, y, COLOR_DARK, 28f)
    y += 72f
  }
  textLine("Net Worth: $netWorth", LEFT_PAD, y, COLOR_PRIMARY, 26f)
}

private fun RemoteComposeWriter.buildAtmList(data: JsonObject) {
  val atms = data["atms"]?.jsonArray ?: JsonArray(emptyList())

  var y = 100f
  textLine("Nearby ATMs", LEFT_PAD, y, COLOR_PRIMARY, 36f)
  y += 60f

  // Draw a stylized map area
  val mapLeft = LEFT_PAD
  val mapTop = y
  val mapWidth = DOC_WIDTH - (LEFT_PAD * 2)
  val mapHeight = 400f

  // Map background (light indigo)
  getRcPaint().setAntiAlias(true).setColor(COLOR_MAP_BG).setStyle(0).commit() // 0 = FILL
  drawRect(mapLeft, mapTop, mapLeft + mapWidth, mapTop + mapHeight)

  // Draw some "road" lines for visual interest
  getRcPaint().setColor(COLOR_MAP_ROAD).setStrokeWidth(8f).setStyle(1).commit() // 1 = STROKE
  // Horizontal roads
  drawLine(mapLeft, mapTop + 100f, mapLeft + mapWidth, mapTop + 100f)
  drawLine(mapLeft, mapTop + 250f, mapLeft + mapWidth, mapTop + 250f)
  // Vertical roads
  drawLine(mapLeft + 200f, mapTop, mapLeft + 200f, mapTop + mapHeight)
  drawLine(mapLeft + 600f, mapTop, mapLeft + 600f, mapTop + mapHeight)

  // Draw user location marker (blue dot in center)
  val userX = mapLeft + mapWidth / 2
  val userY = mapTop + mapHeight / 2
  getRcPaint().setColor(COLOR_MAP_USER).setStyle(0).commit()
  drawOval(userX - 20f, userY - 20f, userX + 20f, userY + 20f)
  // Inner white dot
  getRcPaint().setColor(COLOR_MAP_ROAD).commit()
  drawOval(userX - 8f, userY - 8f, userX + 8f, userY + 8f)

  // Draw ATM markers (red pins) at different positions
  val markerPositions =
          listOf(
                  Pair(mapLeft + 150f, mapTop + 80f),
                  Pair(mapLeft + 650f, mapTop + 150f),
                  Pair(mapLeft + 350f, mapTop + 300f),
          )

  atms.take(3).forEachIndexed { index, _ ->
    if (index < markerPositions.size) {
      val (mx, my) = markerPositions[index]
      // Red marker
      getRcPaint().setColor(COLOR_MAP_MARKER).setStyle(0).commit()
      drawOval(mx - 15f, my - 15f, mx + 15f, my + 15f)
      // White number label
      getRcPaint().setColor(COLOR_MAP_ROAD).setTextSize(18f).commit()
      drawTextAnchored("${index + 1}", mx, my + 6f, 0f, Float.NaN, 0) // Center-aligned
    }
  }

  y = mapTop + mapHeight + 40f

  // List ATMs below the map
  atms.forEachIndexed { index, atm ->
    val a = atm.jsonObject
    val name = a["name"]?.jsonPrimitive?.content ?: ""
    val distance = a["distance"]?.jsonPrimitive?.content ?: ""
    val openStatus = a["openStatus"]?.jsonPrimitive?.content ?: ""

    // Marker number
    textLine("${index + 1}.", LEFT_PAD, y, COLOR_MAP_MARKER, 24f)
    // ATM name and distance
    textLine("$name  •  $distance", LEFT_PAD + 40f, y, COLOR_DARK, 24f)
    y += 40f
    textLine(
            openStatus,
            LEFT_PAD + 40f,
            y,
            if (openStatus.contains("Open")) COLOR_GREEN else COLOR_LIGHT,
            20f
    )
    y += 56f
  }
}

private fun RemoteComposeWriter.buildOffers(data: JsonObject) {
  val offers = data["offers"]?.jsonArray ?: JsonArray(emptyList())

  var y = 100f
  textLine("✨ Your Personal Offers", LEFT_PAD, y, COLOR_PRIMARY, 36f)
  y += 80f

  offers.forEach { offer ->
    val o = offer.jsonObject
    val title = o["title"]?.jsonPrimitive?.content ?: ""
    val rate = o["rate"]?.jsonPrimitive?.content ?: ""
    val tag = o["tag"]?.jsonPrimitive?.content ?: ""
    val desc = o["description"]?.jsonPrimitive?.content ?: ""

    // Draw a card-like background
    val cardTop = y - 20f
    val cardHeight = 180f
    getRcPaint().setAntiAlias(true).setColor(0xFFF5F5F5.toInt()).setStyle(0).commit()
    drawRect(LEFT_PAD - 16f, cardTop, DOC_WIDTH.toFloat() - LEFT_PAD + 16f, cardTop + cardHeight)

    // Tag badge
    textLine(tag.uppercase(), LEFT_PAD, y, COLOR_ACCENT, 18f)
    y += 36f

    // Title
    textLine(title, LEFT_PAD, y, COLOR_DARK, 28f)
    y += 48f

    // Rate - prominent
    textLine(rate, LEFT_PAD, y, COLOR_GREEN, 36f)
    y += 50f

    // Description
    textLine(desc, LEFT_PAD, y, COLOR_LIGHT, 20f)
    y += 80f // Space between cards
  }
}

private fun RemoteComposeWriter.buildFallback(data: JsonObject) {
  val message = data["message"]?.jsonPrimitive?.content ?: "Sorry, I didn't understand."
  val suggestions = data["suggestions"]?.jsonArray ?: JsonArray(emptyList())

  var y = 100f
  textLine(message, LEFT_PAD, y, COLOR_DARK, 26f)
  y += 80f
  textLine("You can try:", LEFT_PAD, y, COLOR_LIGHT, 20f)
  y += 56f
  suggestions.forEach { s ->
    textLine("• ${s.jsonPrimitive.content}", LEFT_PAD, y, COLOR_PRIMARY, 22f)
    y += 52f
  }
}
