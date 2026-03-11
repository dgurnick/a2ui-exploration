package com.dgurnick.bff.agent

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import com.dgurnick.bff.model.*

private val json = Json { explicitNulls = false }

/**
 * Demo agent that generates a "Restaurant Finder" UI as an A2UI v0.8 JSONL stream.
 *
 * It produces:
 *  1. Multiple [SurfaceUpdateMessage]s (component definitions)
 *  2. A [DataModelUpdateMessage] with restaurant data
 *  3. A [BeginRenderingMessage] to trigger render on the client
 *
 * Each message is serialised to a JSON line (JSONL) and emitted via [Flow<String>].
 * The BFF SSE route sends each string as an SSE data event.
 */
class RestaurantFinderAgent(private val surfaceId: String = "main") {

    fun generate(prompt: String): Flow<String> = flow {
        // Simulate streaming latency between messages (like an LLM generating incrementally)
        val delayMs = 40L

        // ── 1. Root layout ────────────────────────────────────────────────────
        emit(surfaceUpdate("root", component("Column") {
            putChildren("search_bar_row", "results_list", "selected_card")
        }))
        delay(delayMs)

        // ── 2. Search bar row ─────────────────────────────────────────────────
        emit(surfaceUpdate("search_bar_row", component("Row") {
            putString("alignment", "center")
            putChildren("search_input", "search_btn")
        }))
        delay(delayMs)

        emit(surfaceUpdate("search_input", component("TextField") {
            putBound("value", "/search/query", prompt)
            putLiteralString("placeholder", "Search restaurants…")
        }))
        delay(delayMs)

        emit(surfaceUpdate("search_btn", component("Button") {
            putChild("search_btn_label")
            putActionWithPath("search", "query" to "/search/query")
        }))
        delay(delayMs)

        emit(surfaceUpdate("search_btn_label", component("Text") {
            putLiteralString("text", "Search")
        }))
        delay(delayMs)

        // ── 3. Results list ───────────────────────────────────────────────────
        emit(surfaceUpdate("results_list", component("List") {
            putTemplateChildren("/restaurants", "restaurant_item_template")
        }))
        delay(delayMs)

        // Template card used for each restaurant list item
        emit(surfaceUpdate("restaurant_item_template", component("Card") {
            putChild("restaurant_item_content")
        }))
        delay(delayMs)

        emit(surfaceUpdate("restaurant_item_content", component("Row") {
            putString("alignment", "spaceBetween")
            putChildren("restaurant_name_col", "restaurant_rating")
        }))
        delay(delayMs)

        emit(surfaceUpdate("restaurant_name_col", component("Column") {
            putChildren("restaurant_name", "restaurant_cuisine")
        }))
        delay(delayMs)

        emit(surfaceUpdate("restaurant_name", component("Text") {
            putPath("text", "name")
            putString("usageHint", "h3")
        }))
        delay(delayMs)

        emit(surfaceUpdate("restaurant_cuisine", component("Text") {
            putPath("text", "cuisine")
        }))
        delay(delayMs)

        emit(surfaceUpdate("restaurant_rating", component("Text") {
            putPath("text", "rating")
            putString("usageHint", "label")
        }))
        delay(delayMs)

        // ── 4. Selected restaurant detail card (initially hidden) ─────────────
        emit(surfaceUpdate("selected_card", component("Card") {
            putChild("selected_card_content")
        }))
        delay(delayMs)

        emit(surfaceUpdate("selected_card_content", component("Column") {
            putChildren("selected_name", "selected_address", "selected_phone", "book_btn")
        }))
        delay(delayMs)

        emit(surfaceUpdate("selected_name", component("Text") {
            putPath("text", "/selected/name")
            putString("usageHint", "h2")
        }))
        delay(delayMs)

        emit(surfaceUpdate("selected_address", component("Text") {
            putPath("text", "/selected/address")
        }))
        delay(delayMs)

        emit(surfaceUpdate("selected_phone", component("Text") {
            putPath("text", "/selected/phone")
        }))
        delay(delayMs)

        emit(surfaceUpdate("book_btn", component("Button") {
            putChild("book_btn_label")
            putActionWithPath("book_table", "restaurantId" to "/selected/id")
        }))
        delay(delayMs)

        emit(surfaceUpdate("book_btn_label", component("Text") {
            putLiteralString("text", "Book a Table")
        }))
        delay(delayMs)

        // ── 5. Data model ─────────────────────────────────────────────────────
        emit(json.encodeToString(DataModelUpdateMessage(
            dataModelUpdate = DataModelUpdatePayload(
                surfaceId = surfaceId,
                contents = listOf(
                    DataEntry(key = "search", valueMap = listOf(
                        DataEntry(key = "query", valueString = prompt)
                    )),
                    DataEntry(key = "restaurants", valueMap = sampleRestaurants()),
                    DataEntry(key = "selected", valueMap = listOf(
                        DataEntry(key = "id", valueString = ""),
                        DataEntry(key = "name", valueString = ""),
                        DataEntry(key = "address", valueString = ""),
                        DataEntry(key = "phone", valueString = "")
                    ))
                )
            )
        )))
        delay(delayMs)

        // ── 6. Trigger render ─────────────────────────────────────────────────
        emit(json.encodeToString(BeginRenderingMessage(
            beginRendering = BeginRenderingPayload(
                surfaceId = surfaceId,
                root = "root"
            )
        )))
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun surfaceUpdate(componentId: String, def: ComponentDefinition): String {
        return json.encodeToString(SurfaceUpdateMessage(
            surfaceUpdate = SurfaceUpdatePayload(
                surfaceId = surfaceId,
                components = listOf(ComponentEntry(id = componentId, component = def))
            )
        ))
    }

    private fun sampleRestaurants(): List<DataEntry> = listOf(
        DataEntry(key = "0", valueMap = listOf(
            DataEntry(key = "id", valueString = "r1"),
            DataEntry(key = "name", valueString = "The Golden Fork"),
            DataEntry(key = "cuisine", valueString = "Italian"),
            DataEntry(key = "rating", valueString = "4.8 ★"),
            DataEntry(key = "address", valueString = "12 Olive Lane, San Francisco"),
            DataEntry(key = "phone", valueString = "+1-415-555-0101")
        )),
        DataEntry(key = "1", valueMap = listOf(
            DataEntry(key = "id", valueString = "r2"),
            DataEntry(key = "name", valueString = "Sakura Garden"),
            DataEntry(key = "cuisine", valueString = "Japanese"),
            DataEntry(key = "rating", valueString = "4.6 ★"),
            DataEntry(key = "address", valueString = "88 Cherry Blossom Rd, San Francisco"),
            DataEntry(key = "phone", valueString = "+1-415-555-0202")
        )),
        DataEntry(key = "2", valueMap = listOf(
            DataEntry(key = "id", valueString = "r3"),
            DataEntry(key = "name", valueString = "Spice Route"),
            DataEntry(key = "cuisine", valueString = "Indian"),
            DataEntry(key = "rating", valueString = "4.5 ★"),
            DataEntry(key = "address", valueString = "45 Curry Street, San Francisco"),
            DataEntry(key = "phone", valueString = "+1-415-555-0303")
        ))
    )
}
