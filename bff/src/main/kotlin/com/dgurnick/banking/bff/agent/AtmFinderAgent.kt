package com.dgurnick.banking.bff.agent

import com.dgurnick.banking.bff.conversation.Conversation
import com.dgurnick.banking.bff.usecase.UseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.*

/**
 * Banking use-case: "Where is the nearest ATM?"
 *
 * Emits structured map data for native interactive map rendering, since Remote Compose cannot
 * support zoom/pan gestures.
 */
class AtmFinderAgent : UseCase {

    override fun canHandle(prompt: String, conversation: Conversation): Boolean {
        val p = prompt.lowercase()
        return p.contains("atm") ||
                p.contains("cash machine") ||
                p.contains("closest") ||
                p.contains("nearest") ||
                p.contains("withdraw")
    }

    override fun generate(
            prompt: String,
            surfaceId: String,
            conversation: Conversation
    ): Flow<String> = flow {
        val (userLat, userLon) = parseLocation(prompt) ?: Pair(37.7860, -122.4071)

        // Send structured map data for native interactive map
        val response = buildJsonObject {
            put("type", "map")
            putJsonObject("mapData") {
                put("userLat", userLat)
                put("userLon", userLon)
                put("title", "Nearby ATMs")
                putJsonArray("markers") {
                    addJsonObject {
                        put("id", "atm1")
                        put("lat", userLat + 0.003)
                        put("lon", userLon + 0.002)
                        put("title", "First National Bank ATM")
                        put("snippet", "0.2 mi • Open 24/7")
                    }
                    addJsonObject {
                        put("id", "atm2")
                        put("lat", userLat + 0.001)
                        put("lon", userLon + 0.005)
                        put("title", "City Credit Union ATM")
                        put("snippet", "0.5 mi • Open until 10 PM")
                    }
                    addJsonObject {
                        put("id", "atm3")
                        put("lat", userLat - 0.004)
                        put("lon", userLon - 0.003)
                        put("title", "Metro Bank ATM")
                        put("snippet", "0.8 mi • Open 24/7")
                    }
                    addJsonObject {
                        put("id", "atm4")
                        put("lat", userLat - 0.006)
                        put("lon", userLon + 0.001)
                        put("title", "Pacific Savings ATM")
                        put("snippet", "1.1 mi • Open 24/7")
                    }
                }
            }
        }
        emit(response.toString())
    }

    private fun parseLocation(prompt: String): Pair<Double, Double>? {
        val m = Regex("""\[user_lat:([\-\d.]+),user_lon:([\-\d.]+)\]""").find(prompt) ?: return null
        val lat = m.groupValues[1].toDoubleOrNull() ?: return null
        val lon = m.groupValues[2].toDoubleOrNull() ?: return null
        return Pair(lat, lon)
    }
}
