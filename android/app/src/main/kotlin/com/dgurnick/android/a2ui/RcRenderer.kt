package com.dgurnick.android.a2ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.remote.creation.compose.ExperimentalRemoteCreationComposeApi
import androidx.compose.remote.creation.compose.capture.captureSingleRemoteDocument
import androidx.compose.remote.creation.compose.layout.RemoteColumn
import androidx.compose.remote.creation.compose.layout.RemoteRow
import androidx.compose.remote.creation.compose.layout.RemoteText
import androidx.compose.remote.creation.compose.modifier.*
import androidx.compose.remote.creation.compose.state.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

@OptIn(ExperimentalRemoteCreationComposeApi::class)
suspend fun renderAgentResponse(ctx: Context, json: String): ByteArray {
    val obj = Json.parseToJsonElement(json).jsonObject
    val type = obj["type"]?.jsonPrimitive?.content ?: "fallback"
    return withContext(Dispatchers.Main) {
        captureSingleRemoteDocument(ctx) {
            when (type) {
                "account_balance" -> AccountBalanceContent(obj)
                "atm_list" -> AtmListContent(obj)
                "offers" -> OffersContent(obj)
                else -> FallbackContent(obj)
            }
        }.bytes
    }
}

@Composable
private fun AccountBalanceContent(data: JsonObject) {
    val greeting = data["greeting"]?.jsonPrimitive?.content ?: "Good morning"
    val accounts = data["accounts"]?.jsonArray ?: JsonArray(emptyList())
    val netWorth = data["netWorth"]?.jsonPrimitive?.content ?: ""
    RemoteColumn(modifier = RemoteModifier.fillMaxSize()) {
        RemoteText(text = greeting.rs)
        accounts.forEach { acc ->
            val a = acc.jsonObject
            RemoteRow {
                RemoteText(text = (a["name"]?.jsonPrimitive?.content ?: "").rs)
                RemoteText(text = (a["balance"]?.jsonPrimitive?.content ?: "").rs)
            }
        }
        RemoteRow {
            RemoteText(text = "Net Worth".rs)
            RemoteText(text = netWorth.rs)
        }
    }
}

@Composable
private fun AtmListContent(data: JsonObject) {
    val atms = data["atms"]?.jsonArray ?: JsonArray(emptyList())
    RemoteColumn(modifier = RemoteModifier.fillMaxSize()) {
        RemoteText(text = "Nearby ATMs".rs)
        atms.forEach { atm ->
            val a = atm.jsonObject
            RemoteRow {
                RemoteText(text = (a["name"]?.jsonPrimitive?.content ?: "").rs)
                RemoteText(text = (a["distance"]?.jsonPrimitive?.content ?: "").rs)
            }
            RemoteText(text = (a["openStatus"]?.jsonPrimitive?.content ?: "").rs)
        }
    }
}

@Composable
private fun OffersContent(data: JsonObject) {
    val offers = data["offers"]?.jsonArray ?: JsonArray(emptyList())
    RemoteColumn(modifier = RemoteModifier.fillMaxSize()) {
        RemoteText(text = "Your Personal Offers".rs)
        offers.forEach { offer ->
            val o = offer.jsonObject
            RemoteRow {
                RemoteText(text = (o["title"]?.jsonPrimitive?.content ?: "").rs)
                RemoteText(text = (o["rate"]?.jsonPrimitive?.content ?: "").rs)
            }
            RemoteText(text = (o["description"]?.jsonPrimitive?.content ?: "").rs)
        }
    }
}

@Composable
private fun FallbackContent(data: JsonObject) {
    val message = data["message"]?.jsonPrimitive?.content ?: "Sorry, I didn\u2019t understand."
    val suggestions = data["suggestions"]?.jsonArray ?: JsonArray(emptyList())
    RemoteColumn(modifier = RemoteModifier.fillMaxSize()) {
        RemoteText(text = message.rs)
        RemoteText(text = "You can try:".rs)
        suggestions.forEach { s ->
            RemoteText(text = "\u2022 ${s.jsonPrimitive.content}".rs)
        }
    }
}