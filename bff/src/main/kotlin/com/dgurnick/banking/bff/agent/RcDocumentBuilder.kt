package com.dgurnick.banking.bff.agent

import androidx.compose.remote.core.RcPlatformServices
import androidx.compose.remote.creation.RemoteComposeContext
import java.util.Base64
import kotlinx.serialization.json.*

private const val DOC_WIDTH = 1080
private const val DOC_HEIGHT = 1920
private const val LEFT_PAD = 48f

private const val COLOR_PRIMARY = 0xFF1A237E.toInt()
private const val COLOR_DARK = 0xFF212121.toInt()
private const val COLOR_LIGHT = 0xFF757575.toInt()
private const val COLOR_GREEN = 0xFF2E7D32.toInt()

/**
 * Creates a Remote Compose document on the server (BFF) from the typed data payload, serialises it
 * to a byte array, and returns a Base64 string for transmission.
 *
 * Uses flat canvas drawing ops (PAINT_VALUES + DRAW_TEXT_ANCHOR) instead of the layout
 * container API, to stay well within the 20 000-operation limit during applyDataOperations.
 */
fun buildRcDocument(data: JsonObject): String {
  val type = data["type"]?.jsonPrimitive?.content ?: "fallback"
  val ctx = RemoteComposeContext(DOC_WIDTH, DOC_HEIGHT, type, RcPlatformServices.None)
  when (type) {
    "account_balance" -> ctx.buildAccountBalance(data)
    "atm_list" -> ctx.buildAtmList(data)
    "offers" -> ctx.buildOffers(data)
    else -> ctx.buildFallback(data)
  }
  return Base64.getEncoder().encodeToString(ctx.buffer())
}

// ── helpers ─────────────────────────────────────────────────────────────────

/**
 * Sets paint properties then draws text anchored at (x, y).
 * panX = -1f → left-aligned at x; panY = NaN → y is the text baseline.
 */
private fun RemoteComposeContext.textLine(
        text: String,
        x: Float,
        y: Float,
        color: Int,
        fontSize: Float,
) {
  writer.getRcPaint().setAntiAlias(true).setColor(color).setTextSize(fontSize).commit()
  drawTextAnchored(text, x, y, -1f, Float.NaN, 0)
}

// ── use-case builders ────────────────────────────────────────────────────────

private fun RemoteComposeContext.buildAccountBalance(data: JsonObject) {
  val greeting = data["greeting"]?.jsonPrimitive?.content ?: "Good morning"
  val accounts = data["accounts"]?.jsonArray ?: JsonArray(emptyList())
  val netWorth = data["netWorth"]?.jsonPrimitive?.content ?: ""

  var y = 100f
  textLine(greeting, LEFT_PAD, y, COLOR_PRIMARY, 22f); y += 64f
  accounts.forEach { acc ->
    val a = acc.jsonObject
    val name = a["name"]?.jsonPrimitive?.content ?: ""
    val number = a["number"]?.jsonPrimitive?.content ?: ""
    val balance = a["balance"]?.jsonPrimitive?.content ?: ""
    textLine("$name  $number", LEFT_PAD, y, COLOR_LIGHT, 13f); y += 36f
    textLine(balance, LEFT_PAD, y, COLOR_DARK, 17f); y += 52f
  }
  textLine("Net Worth: $netWorth", LEFT_PAD, y, COLOR_PRIMARY, 19f)
}

private fun RemoteComposeContext.buildAtmList(data: JsonObject) {
  val atms = data["atms"]?.jsonArray ?: JsonArray(emptyList())

  var y = 100f
  textLine("Nearby ATMs", LEFT_PAD, y, COLOR_PRIMARY, 22f); y += 64f
  atms.forEach { atm ->
    val a = atm.jsonObject
    val name = a["name"]?.jsonPrimitive?.content ?: ""
    val distance = a["distance"]?.jsonPrimitive?.content ?: ""
    val openStatus = a["openStatus"]?.jsonPrimitive?.content ?: ""
    textLine("$name \u2014 $distance", LEFT_PAD, y, COLOR_DARK, 17f); y += 40f
    textLine(openStatus, LEFT_PAD, y, COLOR_LIGHT, 13f); y += 48f
  }
}

private fun RemoteComposeContext.buildOffers(data: JsonObject) {
  val offers = data["offers"]?.jsonArray ?: JsonArray(emptyList())

  var y = 100f
  textLine("Your Personal Offers", LEFT_PAD, y, COLOR_PRIMARY, 22f); y += 64f
  offers.forEach { offer ->
    val o = offer.jsonObject
    val title = o["title"]?.jsonPrimitive?.content ?: ""
    val rate = o["rate"]?.jsonPrimitive?.content ?: ""
    val tag = o["tag"]?.jsonPrimitive?.content ?: ""
    val desc = o["description"]?.jsonPrimitive?.content ?: ""
    textLine("$title  [$tag]", LEFT_PAD, y, COLOR_DARK, 17f); y += 40f
    textLine(rate, LEFT_PAD, y, COLOR_GREEN, 20f); y += 44f
    textLine(desc, LEFT_PAD, y, COLOR_LIGHT, 13f); y += 40f
  }
}

private fun RemoteComposeContext.buildFallback(data: JsonObject) {
  val message = data["message"]?.jsonPrimitive?.content ?: "Sorry, I didn\u2019t understand."
  val suggestions = data["suggestions"]?.jsonArray ?: JsonArray(emptyList())

  var y = 100f
  textLine(message, LEFT_PAD, y, COLOR_DARK, 18f); y += 60f
  textLine("You can try:", LEFT_PAD, y, COLOR_LIGHT, 14f); y += 44f
  suggestions.forEach { s ->
    textLine("\u2022 ${s.jsonPrimitive.content}", LEFT_PAD, y, COLOR_PRIMARY, 16f); y += 44f
  }
}
