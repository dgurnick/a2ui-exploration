package com.dgurnick.android.a2ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.*
import java.time.Instant

// ─── Surface data classes ─────────────────────────────────────────────────────

data class ResolvedComponent(
    val id: String,
    val typeName: String,
    val props: JsonObject
)

class A2uiSurface {
    private val _componentBuffer = mutableMapOf<String, ResolvedComponent>()
    val componentBuffer: Map<String, ResolvedComponent> get() = _componentBuffer
    val dataModel = A2uiDataModel()
    var rootId: String? = null
        private set
    var isReady: Boolean = false
        private set

    fun applyComponentEntry(entry: ComponentEntry) {
        val typeName = entry.component.keys.firstOrNull() ?: return
        val props = entry.component[typeName] as? JsonObject ?: JsonObject(emptyMap())
        _componentBuffer[entry.id] = ResolvedComponent(id = entry.id, typeName = typeName, props = props)
    }

    fun applyDataModelUpdate(payload: DataModelUpdatePayload) = dataModel.applyUpdate(payload)

    fun applyBeginRendering(payload: BeginRenderingPayload) {
        rootId = payload.root
        isReady = true
    }
}

class A2uiSurfaceManager {
    private val surfaces = mutableMapOf<String, A2uiSurface>()
    fun getOrCreate(id: String): A2uiSurface = surfaces.getOrPut(id) { A2uiSurface() }
    fun get(id: String): A2uiSurface? = surfaces[id]
    fun delete(id: String) { surfaces.remove(id) }
}

// ─── Widget composable type ───────────────────────────────────────────────────

typealias WidgetComposable = @Composable (
    comp: ResolvedComponent,
    model: A2uiDataModel,
    surface: A2uiSurface,
    onAction: (UserActionPayload) -> Unit
) -> Unit

// ─── Widget registry ──────────────────────────────────────────────────────────

object WidgetRegistry {
    private val registry = mutableMapOf<String, WidgetComposable>(
        "Column"    to ColumnWidget,
        "Row"       to RowWidget,
        "Text"      to TextWidget,
        "Button"    to ButtonWidget,
        "Card"      to CardWidget,
        "List"      to ListWidget,
        "TextField" to TextFieldWidget
    )

    fun register(typeName: String, widget: WidgetComposable) { registry[typeName] = widget }
    fun get(typeName: String): WidgetComposable? = registry[typeName]
}

// ─── Root surface composable ──────────────────────────────────────────────────

@Composable
fun A2uiSurfaceView(
    surface: A2uiSurface,
    onAction: (UserActionPayload) -> Unit
) {
    val rootId = surface.rootId ?: return
    RenderComponent(
        componentId = rootId,
        model = surface.dataModel,
        surface = surface,
        onAction = onAction
    )
}

@Composable
fun RenderComponent(
    componentId: String,
    model: A2uiDataModel,
    surface: A2uiSurface,
    onAction: (UserActionPayload) -> Unit
) {
    val comp = surface.componentBuffer[componentId] ?: return
    val widget = WidgetRegistry.get(comp.typeName) ?: UnknownWidget
    widget(comp, model, surface, onAction)
}

// ─── Children rendering helper ────────────────────────────────────────────────

@Composable
fun RenderChildren(
    comp: ResolvedComponent,
    model: A2uiDataModel,
    surface: A2uiSurface,
    onAction: (UserActionPayload) -> Unit
) {
    val childrenJson = comp.props["children"] ?: return
    val children = runCatching { Json.decodeFromJsonElement<Children>(childrenJson) }.getOrNull() ?: return

    children.explicitList?.forEach { childId ->
        RenderComponent(childId, model, surface, onAction)
    }

    children.template?.let { template ->
        val listPath = template.dataBinding.path ?: return
        val items = model.resolveList(listPath) ?: return
        items.indices.forEach { _ ->
            RenderComponent(template.componentId, model, surface, onAction)
        }
    }
}

// ─── JSON prop helpers ────────────────────────────────────────────────────────

private fun JsonObject.string(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull

private fun JsonObject.boundValue(key: String): BoundValue? =
    this[key]?.let { runCatching { Json.decodeFromJsonElement<BoundValue>(it) }.getOrNull() }

private fun JsonObject.action(key: String): Action? =
    this[key]?.let { runCatching { Json.decodeFromJsonElement<Action>(it) }.getOrNull() }

// ─── Standard widget implementations ─────────────────────────────────────────

val ColumnWidget: WidgetComposable = { comp, model, surface, onAction ->
    Column(modifier = Modifier.fillMaxWidth()) {
        RenderChildren(comp, model, surface, onAction)
    }
}

val RowWidget: WidgetComposable = { comp, model, surface, onAction ->
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RenderChildren(comp, model, surface, onAction)
    }
}

val TextWidget: WidgetComposable = { comp, model, _, _ ->
    val text = comp.props.boundValue("text")?.resolveString(model) ?: ""
    Text(
        text = text,
        style = when (comp.props.string("style")) {
            "headline1" -> MaterialTheme.typography.headlineLarge
            "headline2" -> MaterialTheme.typography.headlineMedium
            "headline3" -> MaterialTheme.typography.headlineSmall
            "body1"     -> MaterialTheme.typography.bodyLarge
            "caption"   -> MaterialTheme.typography.labelSmall
            else        -> MaterialTheme.typography.bodyMedium
        },
        modifier = Modifier.padding(4.dp)
    )
}

val ButtonWidget: WidgetComposable = { comp, model, surface, onAction ->
    val text    = comp.props.boundValue("text")?.resolveString(model) ?: "Button"
    val enabled = comp.props.boundValue("enabled")?.resolveBoolean(model) ?: true
    val action  = comp.props.action("action")
    Button(
        onClick = {
            action?.let { a ->
                onAction(UserActionPayload(
                    name = a.name,
                    surfaceId = surface.rootId ?: "default",
                    sourceComponentId = comp.id,
                    timestamp = Instant.now().toString(),
                    context = buildJsonObject {
                        a.context.forEach { entry ->
                            put(entry.key, entry.value.resolveString(model) ?: "")
                        }
                    }
                ))
            }
        },
        enabled = enabled,
        modifier = Modifier.padding(4.dp)
    ) { Text(text) }
}

val CardWidget: WidgetComposable = { comp, model, surface, onAction ->
    Card(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
        Column(modifier = Modifier.padding(8.dp)) {
            RenderChildren(comp, model, surface, onAction)
        }
    }
}

val ListWidget: WidgetComposable = { comp, model, surface, onAction ->
    val children = comp.props["children"]?.let {
        runCatching { Json.decodeFromJsonElement<Children>(it) }.getOrNull()
    }
    val template = children?.template
    if (template != null) {
        val items = model.resolveList(template.dataBinding.path ?: "") ?: emptyList()
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(items.size) { _ ->
                RenderComponent(template.componentId, model, surface, onAction)
            }
        }
    } else {
        Column(modifier = Modifier.fillMaxWidth()) {
            RenderChildren(comp, model, surface, onAction)
        }
    }
}

val TextFieldWidget: WidgetComposable = { comp, model, surface, onAction ->
    val label  = comp.props.boundValue("label")?.resolveString(model) ?: ""
    val action = comp.props.action("action")
    var localValue by remember { mutableStateOf(comp.props.boundValue("value")?.resolveString(model) ?: "") }
    OutlinedTextField(
        value = localValue,
        onValueChange = { localValue = it },
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        singleLine = true,
        trailingIcon = if (action != null) {
            {
                IconButton(onClick = {
                    onAction(UserActionPayload(
                        name = action.name,
                        surfaceId = surface.rootId ?: "default",
                        sourceComponentId = comp.id,
                        timestamp = Instant.now().toString(),
                        context = buildJsonObject {
                            put("value", localValue)
                            action.context.forEach { entry ->
                                put(entry.key, entry.value.resolveString(model) ?: "")
                            }
                        }
                    ))
                }) { Text("→") }
            }
        } else null
    )
}

val UnknownWidget: WidgetComposable = { comp, _, _, _ ->
    Text(
        text = "[Unknown: ${comp.typeName}]",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(4.dp)
    )
}
