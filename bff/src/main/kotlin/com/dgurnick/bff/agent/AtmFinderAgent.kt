package com.dgurnick.bff.agent

import com.dgurnick.bff.usecase.UseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.*

/**
 * Banking use-case: "Where is the nearest ATM?"
 *
 * Emits a single typed JSON object the Android client renders via Remote Compose.
 */
class AtmFinderAgent : UseCase {

    override fun canHandle(prompt: String): Boolean {
        val p = prompt.lowercase()
        return p.contains("atm") ||
                p.contains("cash machine") ||
                p.contains("closest") ||
                p.contains("nearest") ||
                p.contains("withdraw")
    }

    override fun generate(prompt: String, surfaceId: String): Flow<String> = flow {
        val (lat, lon) = parseLocation(prompt) ?: Pair(37.7860, -122.4071)
        val data = buildJsonObject {
            put("type", "atm_list")
            put("userLat", lat)
            put("userLon", lon)
            putJsonArray("atms") {
                addJsonObject {
                    put("name", "First National Bank ATM")
                    put("address", "0.2 mi NE")
                    put("distance", "0.2 mi")
                    put("openStatus", "Open 24/7")
                }
                addJsonObject {
                    put("name", "City Credit Union ATM")
                    put("address", "0.5 mi E")
                    put("distance", "0.5 mi")
                    put("openStatus", "Open until 10 PM")
                }
                addJsonObject {
                    put("name", "Metro Bank ATM")
                    put("address", "0.8 mi SW")
                    put("distance", "0.8 mi")
                    put("openStatus", "Open 24/7")
                }
                addJsonObject {
                    put("name", "Pacific Savings ATM")
                    put("address", "1.1 mi S")
                    put("distance", "1.1 mi")
                    put("openStatus", "Open 24/7")
                }
            }
        }
        val rcBase64 = buildRcDocument(data)
        emit(buildJsonObject { put("rc", rcBase64) }.toString())
    }

    private fun parseLocation(prompt: String): Pair<Double, Double>? {
        val m = Regex("""\[user_lat:([\-\d.]+),user_lon:([\-\d.]+)\]""").find(prompt) ?: return null
        val lat = m.groupValues[1].toDoubleOrNull() ?: return null
        val lon = m.groupValues[2].toDoubleOrNull() ?: return null
        return Pair(lat, lon)
    }
}
