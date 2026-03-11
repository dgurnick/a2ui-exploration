package com.dgurnick.banking.bff.agent

import androidx.compose.remote.core.RcPlatformServices
import androidx.compose.remote.creation.RemoteComposeContext
import androidx.compose.remote.creation.modifiers.RecordingModifier
import java.util.Base64
import kotlinx.serialization.json.*

private const val DOC_WIDTH = 1080
private const val DOC_HEIGHT = 1920

private const val COLOR_PRIMARY = 0xFF1A237E.toInt()
private const val COLOR_DARK = 0xFF212121.toInt()
private const val COLOR_MEDIUM = 0xFF424242.toInt()
private const val COLOR_LIGHT = 0xFF757575.toInt()
private const val COLOR_GREEN = 0xFF2E7D32.toInt()

/**
 * Creates a Remote Compose document on the server (BFF) from the typed data payload, serialises it
 * to a byte array, and returns a Base64 string for transmission.
 *
 * This is the correct RC architecture: the server produces the binary document; the Android device
 * only needs RemoteComposePlayer to render it.
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

private fun RemoteComposeContext.rcStyle(sizeSp: Float, color: Int = COLOR_DARK): Int =
        addTextStyle(
                null,
                null,
                sizeSp,
                null,
                null,
                color,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                0
        )

// text inside a fill-width, wrap-height box — 'this' inside content is RemoteComposeContext
private fun RemoteComposeContext.textItem(
        text: String,
        style: Int,
        mod: RecordingModifier = RecordingModifier().fillMaxWidth().wrapContentHeight().padding(4f)
) {
  box(mod, 0, 0) { drawTextAnchored(text, 0f, 0f, DOC_WIDTH.toFloat(), 56f, style) }
}

// ── use-case builders ────────────────────────────────────────────────────────

private fun RemoteComposeContext.buildAccountBalance(data: JsonObject) {
  val titleStyle = rcStyle(22f, COLOR_PRIMARY)
  val labelStyle = rcStyle(13f, COLOR_LIGHT)
  val valueStyle = rcStyle(17f, COLOR_DARK)
  val totalStyle = rcStyle(19f, COLOR_PRIMARY)

  val greeting = data["greeting"]?.jsonPrimitive?.content ?: "Good morning"
  val accounts = data["accounts"]?.jsonArray ?: JsonArray(emptyList())
  val netWorth = data["netWorth"]?.jsonPrimitive?.content ?: ""

  column(RecordingModifier().fillMaxSize().padding(24f), 0, 0) {
    textItem(greeting, titleStyle)
    accounts.forEach { acc ->
      val a = acc.jsonObject
      val name = a["name"]?.jsonPrimitive?.content ?: ""
      val number = a["number"]?.jsonPrimitive?.content ?: ""
      val balance = a["balance"]?.jsonPrimitive?.content ?: ""
      textItem("$name  $number", labelStyle)
      textItem(balance, valueStyle)
    }
    textItem("Net Worth: $netWorth", totalStyle)
  }
}

private fun RemoteComposeContext.buildAtmList(data: JsonObject) {
  val titleStyle = rcStyle(22f, COLOR_PRIMARY)
  val nameStyle = rcStyle(17f, COLOR_DARK)
  val detailStyle = rcStyle(13f, COLOR_LIGHT)

  val atms = data["atms"]?.jsonArray ?: JsonArray(emptyList())

  column(RecordingModifier().fillMaxSize().padding(24f), 0, 0) {
    textItem("Nearby ATMs", titleStyle)
    atms.forEach { atm ->
      val a = atm.jsonObject
      val name = a["name"]?.jsonPrimitive?.content ?: ""
      val distance = a["distance"]?.jsonPrimitive?.content ?: ""
      val openStatus = a["openStatus"]?.jsonPrimitive?.content ?: ""
      textItem("$name \u2014 $distance", nameStyle)
      textItem(openStatus, detailStyle)
    }
  }
}

private fun RemoteComposeContext.buildOffers(data: JsonObject) {
  val titleStyle = rcStyle(22f, COLOR_PRIMARY)
  val offerStyle = rcStyle(17f, COLOR_DARK)
  val rateStyle = rcStyle(20f, COLOR_GREEN)
  val descStyle = rcStyle(13f, COLOR_MEDIUM)

  val offers = data["offers"]?.jsonArray ?: JsonArray(emptyList())

  column(RecordingModifier().fillMaxSize().padding(24f), 0, 0) {
    textItem("Your Personal Offers", titleStyle)
    offers.forEach { offer ->
      val o = offer.jsonObject
      val title = o["title"]?.jsonPrimitive?.content ?: ""
      val rate = o["rate"]?.jsonPrimitive?.content ?: ""
      val tag = o["tag"]?.jsonPrimitive?.content ?: ""
      val desc = o["description"]?.jsonPrimitive?.content ?: ""
      textItem("$title  [$tag]", offerStyle)
      textItem(rate, rateStyle)
      textItem(desc, descStyle)
    }
  }
}

private fun RemoteComposeContext.buildFallback(data: JsonObject) {
  val msgStyle = rcStyle(18f, COLOR_DARK)
  val hintStyle = rcStyle(14f, COLOR_LIGHT)
  val suggStyle = rcStyle(16f, COLOR_PRIMARY)

  val message = data["message"]?.jsonPrimitive?.content ?: "Sorry, I didn\u2019t understand."
  val suggestions = data["suggestions"]?.jsonArray ?: JsonArray(emptyList())

  column(RecordingModifier().fillMaxSize().padding(24f), 0, 0) {
    textItem(message, msgStyle)
    textItem("You can try:", hintStyle)
    suggestions.forEach { s -> textItem("\u2022 ${s.jsonPrimitive.content}", suggStyle) }
  }
}
