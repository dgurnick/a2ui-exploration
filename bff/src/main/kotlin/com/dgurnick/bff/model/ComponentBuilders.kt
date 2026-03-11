package com.dgurnick.bff.model

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.addJsonObject

/**
 * Helper builder DSL that constructs the [ComponentDefinition] (JsonObject) for
 * standard A2UI v0.8 catalog components.
 *
 * Usage:
 *   component("Text") { put("text", literalString("Hello!")) }
 */
fun component(typeName: String, block: BuilderScope.() -> Unit): ComponentDefinition {
    val inner = BuilderScope().also(block).build()
    return buildJsonObject { put(typeName, inner) }
}

class BuilderScope {
    private val obj = mutableMapOf<String, kotlinx.serialization.json.JsonElement>()

    fun build() = JsonObject(obj)

    /** text / label / url etc. – literal string value */
    fun putLiteralString(key: String, value: String) {
        obj[key] = buildJsonObject { put("literalString", value) }
    }

    /** text / label / url etc. – data model path */
    fun putPath(key: String, path: String) {
        obj[key] = buildJsonObject { put("path", path) }
    }

    /** text with both path initialiser and literal default */
    fun putBound(key: String, path: String, default: String) {
        obj[key] = buildJsonObject {
            put("path", path)
            put("literalString", default)
        }
    }

    fun putChildren(vararg ids: String) {
        obj["children"] = buildJsonObject {
            putJsonArray("explicitList") { ids.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } }
        }
    }

    fun putTemplateChildren(dataPath: String, templateComponentId: String) {
        obj["children"] = buildJsonObject {
            putJsonObject("template") {
                putJsonObject("dataBinding") { put("path", dataPath) }
                put("componentId", templateComponentId)
            }
        }
    }

    fun putChild(id: String) {
        obj["child"] = kotlinx.serialization.json.JsonPrimitive(id)
    }

    fun putAction(name: String, vararg context: Pair<String, String>) {
        obj["action"] = buildJsonObject {
            put("name", name)
            putJsonArray("context") {
                context.forEach { (k, v) ->
                    addJsonObject {
                        put("key", k)
                        putJsonObject("value") { put("literalString", v) }
                    }
                }
            }
        }
    }

    fun putActionWithPath(name: String, vararg context: Pair<String, String>) {
        obj["action"] = buildJsonObject {
            put("name", name)
            putJsonArray("context") {
                context.forEach { (k, v) ->
                    addJsonObject {
                        put("key", k)
                        putJsonObject("value") { put("path", v) }
                    }
                }
            }
        }
    }

    fun putString(key: String, value: String) {
        obj[key] = kotlinx.serialization.json.JsonPrimitive(value)
    }
}
