package com.dgurnick.bff.agent

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.dgurnick.bff.model.*
import com.dgurnick.bff.usecase.UseCase

private val atmJson = Json { explicitNulls = false }

/**
 * Banking use-case: "Where's the closest ATM?"
 *
 * Renders a Map component with pin markers, followed by a location search bar
 * and a templated list of nearby ATMs with address, distance, and directions.
 */
class AtmFinderAgent : UseCase {

    override fun canHandle(prompt: String): Boolean {
        val p = prompt.lowercase()
        return p.contains("atm") || p.contains("cash machine") ||
               p.contains("closest") || p.contains("nearest") ||
               p.contains("withdraw")
    }

    override fun generate(prompt: String, surfaceId: String): Flow<String> = flow {
        val d = 40L

        fun su(componentId: String, def: ComponentDefinition): String =
            atmJson.encodeToString(SurfaceUpdateMessage(
                surfaceUpdate = SurfaceUpdatePayload(
                    surfaceId = surfaceId,
                    components = listOf(ComponentEntry(id = componentId, component = def))
                )
            ))

        // ── 1. Root layout ────────────────────────────────────────────────────
        emit(su("root", component("Column") {
            putChildren("header_row", "atm_map", "location_row", "atm_list")
        }))
        delay(d)

        // ── 2. Header ─────────────────────────────────────────────────────────
        emit(su("header_row", component("Row") {
            putString("alignment", "start")
            putChildren("header_title")
        }))
        delay(d)

        emit(su("header_title", component("Text") {
            putLiteralString("text", "Nearby ATMs")
            putString("usageHint", "h2")
        }))
        delay(d)

        // ── 3. Location search row ────────────────────────────────────────────
        emit(su("location_row", component("Row") {
            putString("alignment", "center")
            putChildren("location_input", "search_btn")
        }))
        delay(d)

        emit(su("location_input", component("TextField") {
            putBound("value", "/location/query", prompt)
            putLiteralString("placeholder", "Enter your location…")
        }))
        delay(d)

        emit(su("search_btn", component("Button") {
            putChild("search_lbl")
            putActionWithPath("find_atms", "query" to "/location/query")
        }))
        delay(d)

        emit(su("search_lbl", component("Text") {
            putLiteralString("text", "Find")
        }))
        delay(d)

        // ── 4. Map ────────────────────────────────────────────────────────────
        emit(su("atm_map", component("Map") {
            putLiteralNumber("centerLat", 37.7860)
            putLiteralNumber("centerLon", -122.4071)
            putMapMarkers("/atms", latField = "lat", lonField = "lon", labelField = "name")
        }))
        delay(d)

        // ── 5. ATM results list ───────────────────────────────────────────────
        emit(su("atm_list", component("List") {
            putTemplateChildren("/atms", "atm_item_template")
        }))
        delay(d)

        emit(su("atm_item_template", component("Card") {
            putChild("atm_item_content")
        }))
        delay(d)

        emit(su("atm_item_content", component("Row") {
            putString("alignment", "spaceBetween")
            putChildren("atm_info_col", "atm_right_col")
        }))
        delay(d)

        emit(su("atm_info_col", component("Column") {
            putChildren("atm_name", "atm_address", "atm_open")
        }))
        delay(d)

        emit(su("atm_name", component("Text") {
            putPath("text", "name")
            putString("usageHint", "h3")
        }))
        delay(d)

        emit(su("atm_address", component("Text") {
            putPath("text", "address")
        }))
        delay(d)

        emit(su("atm_open", component("Text") {
            putPath("text", "open_status")
            putString("usageHint", "label")
        }))
        delay(d)

        emit(su("atm_right_col", component("Column") {
            putChildren("atm_distance", "atm_btn")
        }))
        delay(d)

        emit(su("atm_distance", component("Text") {
            putPath("text", "distance")
            putString("usageHint", "label")
        }))
        delay(d)

        emit(su("atm_btn", component("Button") {
            putChild("atm_btn_lbl")
            putActionWithPath("get_directions", "atm_id" to "id")
        }))
        delay(d)

        emit(su("atm_btn_lbl", component("Text") {
            putLiteralString("text", "Directions")
        }))
        delay(d)

        // ── 6. Data model ─────────────────────────────────────────────────────
        emit(atmJson.encodeToString(DataModelUpdateMessage(
            dataModelUpdate = DataModelUpdatePayload(
                surfaceId = surfaceId,
                contents = listOf(
                    DataEntry(key = "location", valueMap = listOf(
                        DataEntry(key = "query", valueString = prompt)
                    )),
                    DataEntry(key = "atms", valueMap = sampleAtms())
                )
            )
        )))
        delay(d)

        // ── 7. Trigger render ─────────────────────────────────────────────────
        emit(atmJson.encodeToString(BeginRenderingMessage(
            beginRendering = BeginRenderingPayload(surfaceId = surfaceId, root = "root")
        )))
    }

    private fun sampleAtms(): List<DataEntry> = listOf(
        DataEntry(key = "0", valueMap = listOf(
            DataEntry(key = "id", valueString = "atm1"),
            DataEntry(key = "name", valueString = "First National Bank ATM"),
            DataEntry(key = "address", valueString = "100 Main St, San Francisco, CA"),
            DataEntry(key = "distance", valueString = "0.2 mi"),
            DataEntry(key = "open_status", valueString = "Open 24/7"),
            DataEntry(key = "lat", valueNumber = 37.7879),
            DataEntry(key = "lon", valueNumber = -122.4074)
        )),
        DataEntry(key = "1", valueMap = listOf(
            DataEntry(key = "id", valueString = "atm2"),
            DataEntry(key = "name", valueString = "City Credit Union ATM"),
            DataEntry(key = "address", valueString = "250 Market St, San Francisco, CA"),
            DataEntry(key = "distance", valueString = "0.5 mi"),
            DataEntry(key = "open_status", valueString = "Open until 10 PM"),
            DataEntry(key = "lat", valueNumber = 37.7870),
            DataEntry(key = "lon", valueNumber = -122.4065)
        )),
        DataEntry(key = "2", valueMap = listOf(
            DataEntry(key = "id", valueString = "atm3"),
            DataEntry(key = "name", valueString = "Metro Bank ATM"),
            DataEntry(key = "address", valueString = "88 Powell St, San Francisco, CA"),
            DataEntry(key = "distance", valueString = "0.8 mi"),
            DataEntry(key = "open_status", valueString = "Open 24/7"),
            DataEntry(key = "lat", valueNumber = 37.7850),
            DataEntry(key = "lon", valueNumber = -122.4085)
        )),
        DataEntry(key = "3", valueMap = listOf(
            DataEntry(key = "id", valueString = "atm4"),
            DataEntry(key = "name", valueString = "Pacific Savings ATM"),
            DataEntry(key = "address", valueString = "45 Mission St, San Francisco, CA"),
            DataEntry(key = "distance", valueString = "1.1 mi"),
            DataEntry(key = "open_status", valueString = "Closed – Opens 6 AM"),
            DataEntry(key = "lat", valueNumber = 37.7840),
            DataEntry(key = "lon", valueNumber = -122.4060)
        ))
    )
}
