package com.dgurnick.android.ui

import android.app.Application
import android.util.Base64
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dgurnick.android.a2ui.A2uiGraphQlClient
import com.dgurnick.android.a2ui.UserActionPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val TAG = "A2UI.ViewModel"

data class A2uiUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val rcDocument: ByteArray? = null
)

class A2uiViewModel(application: Application) : AndroidViewModel(application) {

    private val appCtx = application.applicationContext
    private lateinit var graphQlClient: A2uiGraphQlClient

    private val _uiState = MutableStateFlow(A2uiUiState())
    val uiState: StateFlow<A2uiUiState> = _uiState.asStateFlow()

    fun init(baseUrl: String) {
        graphQlClient = A2uiGraphQlClient(baseUrl)
    }

    fun sendPrompt(prompt: String, surfaceId: String = "main") {
        _uiState.value = A2uiUiState(isLoading = true)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                graphQlClient.subscribeRaw(prompt, surfaceId)
                    .catch { e ->
                        Log.e(TAG, "Stream error", e)
                        _uiState.value = A2uiUiState(error = e.message)
                    }
                    .collect { json ->
                        val obj = Json.parseToJsonElement(json).jsonObject
                        val rcBase64 = obj["rc"]?.jsonPrimitive?.content
                        if (rcBase64 != null) {
                            val bytes = Base64.decode(rcBase64, Base64.DEFAULT)
                            _uiState.value = A2uiUiState(isLoading = false, rcDocument = bytes)
                        }
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error", e)
                _uiState.value = A2uiUiState(error = e.message)
            }
        }
    }

    fun reset() {
        _uiState.value = A2uiUiState()
    }

    fun dispatchAction(action: UserActionPayload) {
        viewModelScope.launch(Dispatchers.IO) {
            graphQlClient.sendUserAction(action)
        }
    }
}