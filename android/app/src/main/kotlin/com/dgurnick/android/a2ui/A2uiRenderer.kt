package com.dgurnick.android.a2ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import java.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

// ─── Map support types ────────────────────────────────────────────────────────

@Serializable
data class MapMarkersConfig(
        val dataBinding: BoundValue,
        val latField: String,
        val lonField: String,
        val labelField: String
)

data class MapPin(val lat: Double, val lon: Double, val label: String)

private fun asDouble(v: Any?): Double? =
        when (v) {
          is Double -> v
          is Number -> v.toDouble()
          is String -> v.toDoubleOrNull()
          else -> null
        }

// ─── Surface data classes ─────────────────────────────────────────────────────

data class ResolvedComponent(val id: String, val typeName: String, val props: JsonObject)

class A2uiSurface {
  private val _componentBuffer = mutableMapOf<String, ResolvedComponent>()
  val componentBuffer: Map<String, ResolvedComponent>
    get() = _componentBuffer
  val dataModel = A2uiDataModel()
  var rootId: String? = null
    private set
  var isReady: Boolean = false
    private set

  fun applyComponentEntry(entry: ComponentEntry) {
    val typeName = entry.component.keys.firstOrNull() ?: return
    val props = entry.component[typeName] as? JsonObject ?: JsonObject(emptyMap())
    _componentBuffer[entry.id] =
            ResolvedComponent(id = entry.id, typeName = typeName, props = props)
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
  fun delete(id: String) {
    surfaces.remove(id)
  }
}

// ─── Widget composable type ───────────────────────────────────────────────────

typealias WidgetComposable =
        @Composable
        (
                comp: ResolvedComponent,
                model: A2uiDataModel,
                surface: A2uiSurface,
                onAction: (UserActionPayload) -> Unit) -> Unit

// ─── Widget registry ──────────────────────────────────────────────────────────

object WidgetRegistry {
  private val registry =
          mutableMapOf<String, WidgetComposable>(
                  "Column" to ColumnWidget,
                  "Row" to RowWidget,
                  "Text" to TextWidget,
                  "Button" to ButtonWidget,
                  "Card" to CardWidget,
                  "List" to ListWidget,
                  "TextField" to TextFieldWidget,
                  "Map" to MapWidget,
                  "OfferCard" to OfferCardWidget
          )

  fun register(typeName: String, widget: WidgetComposable) {
    registry[typeName] = widget
  }
  fun get(typeName: String): WidgetComposable? = registry[typeName]
}

// ─── Root surface composable ──────────────────────────────────────────────────

@Composable
fun A2uiSurfaceView(surface: A2uiSurface, onAction: (UserActionPayload) -> Unit) {
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
  val children =
          runCatching { Json.decodeFromJsonElement<Children>(childrenJson) }.getOrNull() ?: return

  children.explicitList?.forEach { childId -> RenderComponent(childId, model, surface, onAction) }

  children.template?.let { template ->
    val listPath = template.dataBinding.path ?: return
    val items = model.resolveList(listPath) ?: return
    items.indices.forEach { _ -> RenderComponent(template.componentId, model, surface, onAction) }
  }
}

// ─── JSON prop helpers ────────────────────────────────────────────────────────

private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

private fun JsonObject.boundValue(key: String): BoundValue? =
        this[key]?.let { runCatching { Json.decodeFromJsonElement<BoundValue>(it) }.getOrNull() }

private fun JsonObject.action(key: String): Action? =
        this[key]?.let { runCatching { Json.decodeFromJsonElement<Action>(it) }.getOrNull() }

// ─── Standard widget implementations ─────────────────────────────────────────

val ColumnWidget: WidgetComposable = { comp, model, surface, onAction ->
  Column(modifier = Modifier.fillMaxWidth()) { RenderChildren(comp, model, surface, onAction) }
}

val RowWidget: WidgetComposable = { comp, model, surface, onAction ->
  Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    RenderChildren(comp, model, surface, onAction)
  }
}

val TextWidget: WidgetComposable = { comp, model, _, _ ->
  val text = comp.props.boundValue("text")?.resolveString(model) ?: ""
  Text(
          text = text,
          style =
                  when (comp.props.string("style")) {
                    "headline1" -> MaterialTheme.typography.headlineLarge
                    "headline2" -> MaterialTheme.typography.headlineMedium
                    "headline3" -> MaterialTheme.typography.headlineSmall
                    "body1" -> MaterialTheme.typography.bodyLarge
                    "caption" -> MaterialTheme.typography.labelSmall
                    else -> MaterialTheme.typography.bodyMedium
                  },
          modifier = Modifier.padding(4.dp)
  )
}

val ButtonWidget: WidgetComposable = { comp, model, surface, onAction ->
  val text = comp.props.boundValue("text")?.resolveString(model) ?: "Button"
  val enabled = comp.props.boundValue("enabled")?.resolveBoolean(model) ?: true
  val action = comp.props.action("action")
  Button(
          onClick = {
            action?.let { a ->
              onAction(
                      UserActionPayload(
                              name = a.name,
                              surfaceId = surface.rootId ?: "default",
                              sourceComponentId = comp.id,
                              timestamp = Instant.now().toString(),
                              context =
                                      buildJsonObject {
                                        a.context.forEach { entry ->
                                          put(entry.key, entry.value.resolveString(model) ?: "")
                                        }
                                      }
                      )
              )
            }
          },
          enabled = enabled,
          modifier = Modifier.padding(4.dp)
  ) { Text(text) }
}

val CardWidget: WidgetComposable = { comp, model, surface, onAction ->
  Card(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
    Column(modifier = Modifier.padding(8.dp)) { RenderChildren(comp, model, surface, onAction) }
  }
}

val ListWidget: WidgetComposable = { comp, model, surface, onAction ->
  val children =
          comp.props["children"]?.let {
            runCatching { Json.decodeFromJsonElement<Children>(it) }.getOrNull()
          }
  val template = children?.template
  if (template != null) {
    val items = model.resolveList(template.dataBinding.path ?: "") ?: emptyList()
    LazyColumn(modifier = Modifier.fillMaxWidth()) {
      items(items.size) { _ -> RenderComponent(template.componentId, model, surface, onAction) }
    }
  } else {
    Column(modifier = Modifier.fillMaxWidth()) { RenderChildren(comp, model, surface, onAction) }
  }
}

val TextFieldWidget: WidgetComposable = { comp, model, surface, onAction ->
  val label = comp.props.boundValue("label")?.resolveString(model) ?: ""
  val action = comp.props.action("action")
  var localValue by remember {
    mutableStateOf(comp.props.boundValue("value")?.resolveString(model) ?: "")
  }
  OutlinedTextField(
          value = localValue,
          onValueChange = { localValue = it },
          label = { Text(label) },
          modifier = Modifier.fillMaxWidth().padding(8.dp),
          singleLine = true,
          trailingIcon =
                  if (action != null) {
                    {
                      IconButton(
                              onClick = {
                                onAction(
                                        UserActionPayload(
                                                name = action.name,
                                                surfaceId = surface.rootId ?: "default",
                                                sourceComponentId = comp.id,
                                                timestamp = Instant.now().toString(),
                                                context =
                                                        buildJsonObject {
                                                          put("value", localValue)
                                                          action.context.forEach { entry ->
                                                            put(
                                                                    entry.key,
                                                                    entry.value.resolveString(model)
                                                                            ?: ""
                                                            )
                                                          }
                                                        }
                                        )
                                )
                              }
                      ) { Text("→") }
                    }
                  } else null
  )
}

val MapWidget: WidgetComposable = { comp, model, _, _ ->
  val centerLat = (comp.props.boundValue("centerLat")?.resolve(model) as? Double) ?: 37.7860
  val centerLon = (comp.props.boundValue("centerLon")?.resolve(model) as? Double) ?: -122.4071

  val markersConfig =
          comp.props["markers"]?.let {
            runCatching { Json.decodeFromJsonElement<MapMarkersConfig>(it) }.getOrNull()
          }
  val pins: List<MapPin> =
          if (markersConfig != null) {
            val items = model.resolveList(markersConfig.dataBinding.path ?: "") ?: emptyList()
            items.mapNotNull { item ->
              val lat = asDouble(item[markersConfig.latField]) ?: return@mapNotNull null
              val lon = asDouble(item[markersConfig.lonField]) ?: return@mapNotNull null
              MapPin(lat, lon, item[markersConfig.labelField]?.toString() ?: "")
            }
          } else emptyList()

  val mapViewHolder = remember { arrayOfNulls<MapView>(1) }
  DisposableEffect(Unit) { onDispose { mapViewHolder[0]?.onDetach() } }

  AndroidView(
          modifier =
                  Modifier.fillMaxWidth()
                          .height(220.dp)
                          .padding(horizontal = 8.dp, vertical = 4.dp)
                          .clip(RoundedCornerShape(12.dp)),
          factory = { ctx ->
            Configuration.getInstance().apply {
              load(ctx, ctx.getSharedPreferences("osmdroid", 0))
              userAgentValue = ctx.packageName
            }
            MapView(ctx)
                    .apply {
                      setTileSource(TileSourceFactory.MAPNIK)
                      setMultiTouchControls(true)
                      controller.setZoom(15.0)
                      controller.setCenter(GeoPoint(centerLat, centerLon))
                      onResume()
                    }
                    .also { mapViewHolder[0] = it }
          },
          update = { mapView ->
            mapView.overlays.clear()
            Marker(mapView).apply {
              position = GeoPoint(centerLat, centerLon)
              title = "You are here"
              setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
              mapView.overlays.add(this)
            }
            pins.forEach { pin ->
              Marker(mapView).apply {
                position = GeoPoint(pin.lat, pin.lon)
                title = pin.label
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                mapView.overlays.add(this)
              }
            }
            mapView.controller.setCenter(GeoPoint(centerLat, centerLon))
            mapView.invalidate()
          }
  )
}

val OfferCardWidget: WidgetComposable = { comp, model, surface, onAction ->
  var expanded by remember { mutableStateOf(false) }

  val title = comp.props.boundValue("title")?.resolveString(model) ?: ""
  val tag = comp.props.boundValue("tag")?.resolveString(model) ?: ""
  val icon = comp.props.boundValue("icon")?.resolveString(model) ?: "💳"
  val rate = comp.props.boundValue("rate")?.resolveString(model) ?: ""
  val description = comp.props.boundValue("description")?.resolveString(model) ?: ""
  val highlights = comp.props.boundValue("highlights")?.resolveString(model) ?: ""
  val expires = comp.props.boundValue("expires")?.resolveString(model) ?: ""
  val color = comp.props.string("color") ?: "secondary"
  val action = comp.props.action("action")

  val containerColor =
          when (color) {
            "credit" -> MaterialTheme.colorScheme.tertiaryContainer
            "savings" -> MaterialTheme.colorScheme.primaryContainer
            "loan" -> MaterialTheme.colorScheme.errorContainer
            else -> MaterialTheme.colorScheme.secondaryContainer
          }
  val onContainerColor =
          when (color) {
            "credit" -> MaterialTheme.colorScheme.onTertiaryContainer
            "savings" -> MaterialTheme.colorScheme.onPrimaryContainer
            "loan" -> MaterialTheme.colorScheme.onErrorContainer
            else -> MaterialTheme.colorScheme.onSecondaryContainer
          }

  Card(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
          colors = CardDefaults.cardColors(containerColor = containerColor),
          onClick = { expanded = !expanded }
  ) {
    Column(modifier = Modifier.padding(16.dp)) {

      // ── Header row: icon + title + tag chip ───────────────────────────
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(icon, style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.width(12.dp))
        Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = onContainerColor,
                modifier = Modifier.weight(1f)
        )
        Surface(shape = RoundedCornerShape(12.dp), color = onContainerColor.copy(alpha = 0.15f)) {
          Text(
                  tag,
                  style = MaterialTheme.typography.labelSmall,
                  color = onContainerColor,
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
          )
        }
      }

      // ── Key metric (always visible) ───────────────────────────────────
      if (rate.isNotBlank()) {
        Spacer(modifier = Modifier.height(6.dp))
        Text(rate, style = MaterialTheme.typography.headlineSmall, color = onContainerColor)
      }

      // ── Expandable detail ─────────────────────────────────────────────
      AnimatedVisibility(visible = expanded) {
        Column {
          Spacer(modifier = Modifier.height(12.dp))
          HorizontalDivider(color = onContainerColor.copy(alpha = 0.25f))
          Spacer(modifier = Modifier.height(10.dp))

          Text(description, style = MaterialTheme.typography.bodyMedium, color = onContainerColor)

          if (highlights.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            highlights.split("|").map { it.trim() }.filter { it.isNotBlank() }.forEach { bullet ->
              Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(vertical = 2.dp)) {
                Text("•  ", color = onContainerColor, style = MaterialTheme.typography.bodySmall)
                Text(bullet, style = MaterialTheme.typography.bodySmall, color = onContainerColor)
              }
            }
          }

          Spacer(modifier = Modifier.height(12.dp))
          Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                    expires,
                    style = MaterialTheme.typography.labelSmall,
                    color = onContainerColor.copy(alpha = 0.7f),
                    modifier = Modifier.weight(1f)
            )
            if (action != null) {
              Button(
                      onClick = {
                        onAction(
                                UserActionPayload(
                                        name = action.name,
                                        surfaceId = surface.rootId ?: "default",
                                        sourceComponentId = comp.id,
                                        timestamp = Instant.now().toString(),
                                        context =
                                                buildJsonObject {
                                                  action.context.forEach { entry ->
                                                    put(
                                                            entry.key,
                                                            entry.value.resolveString(model) ?: ""
                                                    )
                                                  }
                                                }
                                )
                        )
                      },
                      colors =
                              ButtonDefaults.buttonColors(
                                      containerColor = onContainerColor,
                                      contentColor = containerColor
                              )
              ) { Text("Apply Now") }
            }
          }
        }
      }

      // ── Expand hint ───────────────────────────────────────────────────
      Spacer(modifier = Modifier.height(4.dp))
      Text(
              if (expanded) "▲ tap to collapse" else "▼ tap for details",
              style = MaterialTheme.typography.labelSmall,
              color = onContainerColor.copy(alpha = 0.55f)
      )
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
