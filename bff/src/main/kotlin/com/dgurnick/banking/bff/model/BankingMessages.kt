package com.dgurnick.banking.bff.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

// ──────────────────────────────────────────────────────────────────────────────
// Server → Client messages  (Banking BFF)
// Each JSONL line is one of these sealed variants.
// ──────────────────────────────────────────────────────────────────────────────

/** A single A2UI property value – either a literal or a data-model path. */
@Serializable
data class BoundValue(
    val literalString: String? = null,
    val literalNumber: Double? = null,
    val literalBoolean: Boolean? = null,
    /** JSON Pointer e.g. /user/name */
    val path: String? = null
)

/** Children definition for container components. Exactly one field set. */
@Serializable
data class Children(
    val explicitList: List<String>? = null,
    val template: TemplateChildren? = null
)

@Serializable
data class TemplateChildren(
    val dataBinding: BoundValue,
    val componentId: String
)

/** An action attached to interactive components (e.g. Button). */
@Serializable
data class Action(
    val name: String,
    val context: List<ContextEntry> = emptyList()
)

@Serializable
data class ContextEntry(
    val key: String,
    val value: BoundValue
)

/**
 * Generic component wrapper – the outer key is the component type name
 * (e.g. "Text", "Button") and its value is the component properties map.
 * We use [JsonObject] so that any catalog component can be serialised.
 */
typealias ComponentDefinition = JsonObject

/** One entry in a surfaceUpdate components list. */
@Serializable
data class ComponentEntry(
    val id: String,
    val component: ComponentDefinition
)

// ─── surfaceUpdate ────────────────────────────────────────────────────────────

@Serializable
data class SurfaceUpdatePayload(
    val surfaceId: String = "default",
    val components: List<ComponentEntry>
)

@Serializable
data class SurfaceUpdateMessage(
    val surfaceUpdate: SurfaceUpdatePayload
)

// ─── dataModelUpdate ─────────────────────────────────────────────────────────

@Serializable
data class DataEntry(
    val key: String,
    val valueString: String? = null,
    val valueNumber: Double? = null,
    val valueBoolean: Boolean? = null,
    val valueMap: List<DataEntry>? = null
)

@Serializable
data class DataModelUpdatePayload(
    val surfaceId: String = "default",
    val path: String? = null,
    val contents: List<DataEntry>
)

@Serializable
data class DataModelUpdateMessage(
    val dataModelUpdate: DataModelUpdatePayload
)

// ─── beginRendering ───────────────────────────────────────────────────────────

@Serializable
data class BeginRenderingPayload(
    val surfaceId: String = "default",
    val root: String,
    val catalogId: String = "https://a2ui.org/specification/v0_8/standard_catalog_definition.json"
)

@Serializable
data class BeginRenderingMessage(
    val beginRendering: BeginRenderingPayload
)

// ─── deleteSurface ────────────────────────────────────────────────────────────

@Serializable
data class DeleteSurfacePayload(val surfaceId: String)

@Serializable
data class DeleteSurfaceMessage(val deleteSurface: DeleteSurfacePayload)

// ──────────────────────────────────────────────────────────────────────────────
// Client → Server messages  (Banking BFF)
// ──────────────────────────────────────────────────────────────────────────────

@Serializable
data class UserActionPayload(
    val name: String,
    val surfaceId: String,
    val sourceComponentId: String,
    val timestamp: String,
    val context: JsonObject = JsonObject(emptyMap())
)

@Serializable
data class ClientErrorPayload(
    val message: String,
    val componentId: String? = null,
    val details: JsonElement? = null
)

@Serializable
data class ClientEventMessage(
    val userAction: UserActionPayload? = null,
    val error: ClientErrorPayload? = null
)
