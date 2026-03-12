package com.dgurnick.banking.client

import android.annotation.SuppressLint
import android.preference.PreferenceManager
import android.view.MotionEvent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

/**
 * Interactive map view using OpenStreetMap (osmdroid). Supports pinch-to-zoom and pan gestures -
 * something Remote Compose cannot do.
 *
 * Features:
 * - Pinch-to-zoom and pan gestures
 * - ATM markers on the map
 * - ATM list that filters automatically based on visible map area
 */
@Composable
fun InteractiveMapView(mapData: MapData, modifier: Modifier = Modifier) {
  val context = LocalContext.current

  // Track visible markers based on map bounds
  var visibleMarkers by remember { mutableStateOf(mapData.markers) }
  var mapViewRef by remember { mutableStateOf<MapView?>(null) }

  // Configure osmdroid
  LaunchedEffect(Unit) {
    val config = Configuration.getInstance()
    config.load(context, PreferenceManager.getDefaultSharedPreferences(context))
    config.userAgentValue = "BankingApp/1.0 (Android; osmdroid)"
    // Set cache location
    config.osmdroidTileCache = context.cacheDir
  }

  // Function to update visible markers based on map bounds
  fun updateVisibleMarkers(boundingBox: BoundingBox) {
    visibleMarkers =
            mapData.markers.filter { marker ->
              boundingBox.contains(GeoPoint(marker.lat, marker.lon))
            }
  }

  Column(modifier = modifier) {
    // Title with count of visible ATMs
    Text(
            text = "${mapData.title} (${visibleMarkers.size} visible)",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(16.dp)
    )

    // Map - fixed height for scrollable containers
    @SuppressLint("ClickableViewAccessibility")
    AndroidView(
            factory = { ctx ->
              MapView(ctx).apply {
                mapViewRef = this
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                setBuiltInZoomControls(false)

                // Enable hardware acceleration for better tile rendering
                setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)

                // Request parent to not intercept touch events when touching the map
                setOnTouchListener { v, event ->
                  when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                      // Disable parent scroll interception
                      v.parent?.requestDisallowInterceptTouchEvent(true)
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                      // Re-enable parent scroll interception
                      v.parent?.requestDisallowInterceptTouchEvent(false)
                    }
                  }
                  false // Let the map handle the event
                }

                // Set initial position and zoom
                val userPoint = GeoPoint(mapData.userLat, mapData.userLon)
                controller.setZoom(15.0)
                controller.setCenter(userPoint)

                // Add map listener to track zoom/scroll and filter ATM list
                addMapListener(
                        object : MapListener {
                          override fun onScroll(event: ScrollEvent?): Boolean {
                            boundingBox?.let { updateVisibleMarkers(it) }
                            return true
                          }

                          override fun onZoom(event: ZoomEvent?): Boolean {
                            boundingBox?.let { updateVisibleMarkers(it) }
                            return true
                          }
                        }
                )

                // Add user location marker (blue)
                val userMarker =
                        Marker(this).apply {
                          position = userPoint
                          setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                          title = "You are here"
                          snippet = "Your current location"
                        }
                overlays.add(userMarker)

                // Add ATM markers
                mapData.markers.forEach { markerData ->
                  val marker =
                          Marker(this).apply {
                            position = GeoPoint(markerData.lat, markerData.lon)
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            title = markerData.title
                            snippet = markerData.snippet
                          }
                  overlays.add(marker)
                }

                // Initialize visible markers after map is ready
                post { boundingBox?.let { updateVisibleMarkers(it) } }
              }
            },
            modifier = Modifier.fillMaxWidth().height(350.dp).padding(horizontal = 16.dp),
            update = { mapView ->
              // Refresh visible markers when map updates
              mapView.boundingBox?.let { updateVisibleMarkers(it) }
            }
    )

    // ATM list below map - shows only visible markers
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
      if (visibleMarkers.isEmpty()) {
        Text(
                text = "No ATMs visible in current view. Zoom out to see more.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp)
        )
      } else {
        visibleMarkers.forEachIndexed { index, marker ->
          Card(
                  modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                  colors =
                          CardDefaults.cardColors(
                                  containerColor = MaterialTheme.colorScheme.surfaceVariant
                          ),
                  onClick = {
                    // Center map on selected ATM
                    mapViewRef?.controller?.animateTo(GeoPoint(marker.lat, marker.lon))
                  }
          ) {
            Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                        text = "${index + 1}. ${marker.title}",
                        style = MaterialTheme.typography.bodyLarge
                )
                Text(
                        text = marker.snippet,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }
          }
        }
      }
    }
  }
}
