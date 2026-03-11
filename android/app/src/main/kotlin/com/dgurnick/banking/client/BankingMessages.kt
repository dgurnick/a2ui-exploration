package com.dgurnick.banking.client

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

// ──────────────────────────────────────────────────────────────────────────────
// Banking client wire models – Android client-side data models
// ──────────────────────────────────────────────────────────────────────────────

/** Matches the wire-format BoundValue. */
@Serializable
data class BoundValue(
    val literalString: String? = null,
    val literalNumber: Double? = null,
    val literalBoolean: Boolean? = null,
    val path: String? = null
)

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

@Serializable
data class ContextEntry(
    val key: String,
    val value: BoundValue
)

@Serializable
data class Action(
    val name: String,
    val context: List<ContextEntry> = emptyList()
)

/** A flat component entry received in a surfaceUpdate. */
@Serializable
data class ComponentEntry(
    val id: String,
    /** The raw JSON object – outer key is the component type, value is props. */
    val component: JsonObject
)

// ─── Server → Client ──────────────────────────────────────────────────────────

@Serializable
data class SurfaceUpdatePayload(
    val surfaceId: String = "default",
    val components: List<ComponentEntry>
)

@Serializable
data class SurfaceUpdateMessage(val surfaceUpdate: SurfaceUpdatePayload)

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
data class DataModelUpdateMessage(val dataModelUpdate: DataModelUpdatePayload)

@Serializable
data class BeginRenderingPayload(
    val surfaceId: String = "default",
    val root: String,
    val catalogId: String = "https://a2ui.org/specification/v0_8/standard_catalog_definition.json"
)

@Serializable
data class BeginRenderingMessage(val beginRendering: BeginRenderingPayload)

@Serializable
data class DeleteSurfacePayload(val surfaceId: String)

@Serializable
data class DeleteSurfaceMessage(val deleteSurface: DeleteSurfacePayload)

// ─── Client → Server ──────────────────────────────────────────────────────────

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
    val componentId: String? = null
)

@Serializable
data class ClientEventMessage(
    val userAction: UserActionPayload? = null,
    val error: ClientErrorPayload? = null
)


