package com.dgurnick.android.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dgurnick.android.a2ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import java.time.Instant

private const val TAG = "A2UI.ViewModel"

data class A2uiUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    /** Set when a surface has received BeginRendering and is ready to render. */
    val readySurfaceId: String? = null
)

class A2uiViewModel : ViewModel() {

    private val surfaceManager = A2uiSurfaceManager()
    private lateinit var graphQlClient: A2uiGraphQlClient

    private val _uiState = MutableStateFlow(A2uiUiState())
    val uiState: StateFlow<A2uiUiState> = _uiState.asStateFlow()

    fun init(baseUrl: String) {
        graphQlClient = A2uiGraphQlClient(baseUrl)
    }

    /** Subscribe to the BFF uiStream for [prompt] and process the A2UI event stream. */
    fun sendPrompt(prompt: String, surfaceId: String = "main") {
        _uiState.value = A2uiUiState(isLoading = true)

        viewModelScope.launch(Dispatchers.IO) {
            graphQlClient.subscribe(prompt, surfaceId)
                .catch { e ->
                    Log.e(TAG, "Stream error", e)
                    _uiState.value = A2uiUiState(error = e.message)
                }
                .collect { event -> handleEvent(event) }
        }
    }

    /** Returns the [A2uiSurface] for [surfaceId] so Compose can read it. */
    fun getSurface(surfaceId: String): A2uiSurface? = surfaceManager.get(surfaceId)

    /** Dispatch a user action to the BFF via GraphQL mutation. */
    fun dispatchAction(action: UserActionPayload) {
        viewModelScope.launch(Dispatchers.IO) {
            graphQlClient.sendUserAction(action)
        }
    }

    fun createUserAction(
        name: String,
        surfaceId: String,
        sourceComponentId: String,
        context: Map<String, String> = emptyMap()
    ) = UserActionPayload(
        name = name,
        surfaceId = surfaceId,
        sourceComponentId = sourceComponentId,
        timestamp = Instant.now().toString(),
        context = kotlinx.serialization.json.buildJsonObject {
            context.forEach { (k, v) -> put(k, JsonPrimitive(v)) }
        }
    )

    private fun handleEvent(event: A2uiStreamEvent) {
        when (event) {
            is A2uiStreamEvent.SurfaceUpdate -> {
                val surface = surfaceManager.getOrCreate(event.payload.surfaceId)
                event.payload.components.forEach { surface.applyComponentEntry(it) }
            }
            is A2uiStreamEvent.DataModelUpdate -> {
                val surface = surfaceManager.getOrCreate(event.payload.surfaceId)
                surface.applyDataModelUpdate(event.payload)
            }
            is A2uiStreamEvent.BeginRendering -> {
                val surface = surfaceManager.getOrCreate(event.payload.surfaceId)
                surface.applyBeginRendering(event.payload)
                Log.d(TAG, "Surface '${event.payload.surfaceId}' ready to render")
                _uiState.value = A2uiUiState(
                    isLoading = false,
                    readySurfaceId = event.payload.surfaceId
                )
            }
            is A2uiStreamEvent.DeleteSurface -> surfaceManager.delete(event.payload.surfaceId)
            is A2uiStreamEvent.StreamError -> _uiState.value = A2uiUiState(error = event.message)
            is A2uiStreamEvent.StreamClosed -> {
                if (_uiState.value.isLoading) _uiState.value = A2uiUiState(isLoading = false)
            }
        }
    }
}
