package com.dgurnick.android.a2ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.time.Instant

// ─── Map support types ────────────────────────────────────────────────────────

@Serializable
data class MapMarkersConfig(
    val dataBinding: BoundValue,
    val latField: String,
    val lonField: String,
    val labelField: String
)

data class MapPin(val lat: Double, val lon: Double, val label: String)

private fun asDouble(v: Any?): Double? = when (v) {
    is Double -> v
    is Number -> v.toDouble()
    is String -> v.toDoubleOrNull()
    else -> null
}

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
        "TextField" to TextFieldWidget,
        "Map"       to MapWidget
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

val MapWidget: WidgetComposable = { comp, model, _, _ ->
    // Resolve center coordinates
    val centerLat = (comp.props.boundValue("centerLat")?.resolve(model) as? Double) ?: 37.7860
    val centerLon = (comp.props.boundValue("centerLon")?.resolve(model) as? Double) ?: -122.4071

    // Parse markers config and build pin list from data model
    val markersConfig = comp.props["markers"]?.let {
        runCatching { Json.decodeFromJsonElement<MapMarkersConfig>(it) }.getOrNull()
    }
    val pins: List<MapPin> = if (markersConfig != null) {
        val items = model.resolveList(markersConfig.dataBinding.path ?: "") ?: emptyList()
        items.mapNotNull { item ->
            val lat = asDouble(item[markersConfig.latField]) ?: return@mapNotNull null
            val lon = asDouble(item[markersConfig.lonField]) ?: return@mapNotNull null
            MapPin(lat, lon, item[markersConfig.labelField]?.toString() ?: "")
        }
    } else emptyList()

    // Compute bounding box with padding
    val allLats = pins.map { it.lat } + centerLat
    val allLons = pins.map { it.lon } + centerLon
    val pad = 0.003
    val minLat = (allLats.minOrNull() ?: centerLat) - pad
    val maxLat = (allLats.maxOrNull() ?: centerLat) + pad
    val minLon = (allLons.minOrNull() ?: centerLon) - pad
    val maxLon = (allLons.maxOrNull() ?: centerLon) + pad

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
    ) {
        val w = size.width
        val h = size.height

        fun latToY(lat: Double) = ((maxLat - lat) / (maxLat - minLat) * h).toFloat()
        fun lonToX(lon: Double) = ((lon - minLon) / (maxLon - minLon) * w).toFloat()

        // ── Background tiles ──────────────────────────────────────────────────
        drawRect(color = Color(0xFFDCEAF5))
        val tileSize = 40f
        var ty = 0f
        var rowEven = true
        while (ty < h) {
            var tx = if (rowEven) 0f else tileSize
            while (tx < w) {
                drawRect(
                    color = Color(0xFFC8DCF0),
                    topLeft = Offset(tx, ty),
                    size = Size(tileSize, tileSize)
                )
                tx += tileSize * 2
            }
            ty += tileSize
            rowEven = !rowEven
        }

        // ── Street grid ───────────────────────────────────────────────────────
        val streetColor = Color(0xFFFFFFFF)
        val streetWidth = 3f
        // Horizontal streets
        for (frac in listOf(0.3f, 0.5f, 0.7f)) {
            drawLine(streetColor, Offset(0f, h * frac), Offset(w, h * frac), streetWidth)
        }
        // Vertical streets
        for (frac in listOf(0.25f, 0.5f, 0.75f)) {
            drawLine(streetColor, Offset(w * frac, 0f), Offset(w * frac, h), streetWidth)
        }

        // ── Center location pin (blue) ─────────────────────────────────────────
        val cx = lonToX(centerLon)
        val cy = latToY(centerLat)
        drawCircle(Color(0xFF1565C0), radius = 10f, center = Offset(cx, cy))
        drawCircle(Color.White, radius = 4f, center = Offset(cx, cy))

        // ── ATM markers (red pin with white dot) ─────────────────────────────
        pins.forEach { pin ->
            val px = lonToX(pin.lon)
            val py = latToY(pin.lat)
            drawCircle(Color(0xFFD32F2F), radius = 14f, center = Offset(px, py))
            drawCircle(Color.White, radius = 4f, center = Offset(px, py))
        }
    }
}

val UnknownWidget: WidgetComposable = { comp, _, _, _ ->
    Text(
        text = "[Unknown: ${comp.typeName}]",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(4.dp)
    )
}
