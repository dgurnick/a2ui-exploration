package com.dgurnick.android.a2ui

import kotlinx.serialization.json.*

private const val TAG = "A2UI.DataModel"

/**
 * Stores the A2UI data model for a single surface as a mutable JSON-like tree. Supports the
 * [DataEntry] adjacency list format from [DataModelUpdateMessage].
 */
class A2uiDataModel {
    private val root = mutableMapOf<String, Any?>()

    /** Apply a [DataModelUpdatePayload] to the model. */
    fun applyUpdate(payload: DataModelUpdatePayload) {
        val targetMap =
                if (payload.path.isNullOrBlank() || payload.path == "/") {
                    root
                } else {
                    navigateOrCreate(root, payload.path)
                }
        for (entry in payload.contents) {
            targetMap[entry.key] = resolveDataEntry(entry)
        }
    }

    /**
     * Resolve a JSON Pointer path against the data model. Returns the value, or null if not found.
     */
    fun resolve(path: String): Any? {
        if (path.isBlank()) return root
        val parts = path.removePrefix("/").split("/").filter { it.isNotEmpty() }
        var current: Any? = root
        for (part in parts) {
            val prev = current
            current =
                    when (prev) {
                        is Map<*, *> -> prev[part]
                        is List<*> -> part.toIntOrNull()?.let { prev.getOrNull(it) }
                        else -> return null
                    }
        }
        return current
    }

    fun resolveString(path: String): String? = resolve(path)?.toString()

    fun resolveBoolean(path: String): Boolean? =
            when (val v = resolve(path)) {
                is Boolean -> v
                is String -> v.toBooleanStrictOrNull()
                else -> null
            }

    fun resolveList(path: String): List<Map<String, Any?>>? {
        val v = resolve(path) ?: return null
        @Suppress("UNCHECKED_CAST")
        return when (v) {
            is List<*> -> v as? List<Map<String, Any?>>
            is Map<*, *> ->
                    (v as Map<String, Any?>).keys
                            .sortedBy { it.toString().toIntOrNull() ?: Int.MAX_VALUE }
                            .mapNotNull { (v as Map<String, Any?>)[it] as? Map<String, Any?> }
            else -> null
        }
    }

    /** Write a value to a path (used for BoundValue initialization shorthand). */
    fun write(path: String, value: Any?) {
        if (path.isBlank() || path == "/") return
        val parts = path.removePrefix("/").split("/").filter { it.isNotEmpty() }
        val leafKey = parts.last()
        val parentPath = parts.dropLast(1).joinToString("/", prefix = "/")
        val map =
                if (parentPath.isEmpty() || parentPath == "/") root
                else navigateOrCreate(root, parentPath)
        map[leafKey] = value
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun resolveDataEntry(entry: DataEntry): Any? =
            when {
                entry.valueString != null -> entry.valueString
                entry.valueNumber != null -> entry.valueNumber
                entry.valueBoolean != null -> entry.valueBoolean
                entry.valueMap != null -> {
                    val map = mutableMapOf<String, Any?>()
                    for (child in entry.valueMap) map[child.key] = resolveDataEntry(child)
                    map
                }
                else -> null
            }

    private fun navigateOrCreate(
            from: MutableMap<String, Any?>,
            path: String
    ): MutableMap<String, Any?> {
        val parts = path.removePrefix("/").split("/").filter { it.isNotEmpty() }
        var current = from
        for (part in parts) {
            @Suppress("UNCHECKED_CAST")
            current =
                    current.getOrPut(part) { mutableMapOf<String, Any?>() } as
                            MutableMap<String, Any?>
        }
        return current
    }
}

/**
 * Resolves a [BoundValue] against the data model. If both [BoundValue.path] and a literal are
 * present, writes the literal to the data model (initialization shorthand) and returns the literal.
 */
fun BoundValue.resolve(model: A2uiDataModel): Any? {
    return when {
        path != null && literalString != null -> {
            model.write(path, literalString)
            literalString
        }
        path != null && literalNumber != null -> {
            model.write(path, literalNumber)
            literalNumber
        }
        path != null && literalBoolean != null -> {
            model.write(path, literalBoolean)
            literalBoolean
        }
        path != null -> model.resolve(path)
        literalString != null -> literalString
        literalNumber != null -> literalNumber
        literalBoolean != null -> literalBoolean
        else -> null
    }
}

fun BoundValue.resolveString(model: A2uiDataModel): String? = resolve(model)?.toString()

fun BoundValue.resolveBoolean(model: A2uiDataModel): Boolean? = resolve(model) as? Boolean
