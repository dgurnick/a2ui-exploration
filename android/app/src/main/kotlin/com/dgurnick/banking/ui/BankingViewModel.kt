package com.dgurnick.banking.ui

import android.app.Application
import android.util.Base64
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dgurnick.banking.client.BankingGraphQlClient
import com.dgurnick.banking.client.UserActionPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val TAG = "Banking.ViewModel"

data class BankingUiState(
        val isLoading: Boolean = false,
        val error: String? = null,
        val rcDocument: ByteArray? = null
)

class BankingViewModel(application: Application) : AndroidViewModel(application) {

  private val appCtx = application.applicationContext
  private lateinit var graphQlClient: BankingGraphQlClient

  private val _uiState = MutableStateFlow(BankingUiState())
  val uiState: StateFlow<BankingUiState> = _uiState.asStateFlow()

  fun init(baseUrl: String) {
    graphQlClient = BankingGraphQlClient(baseUrl)
  }

  fun sendPrompt(prompt: String, surfaceId: String = "main") {
    _uiState.value = BankingUiState(isLoading = true)
    viewModelScope.launch(Dispatchers.IO) {
      try {
        graphQlClient
                .subscribeRaw(prompt, surfaceId)
                .catch { e ->
                  Log.e(TAG, "Stream error", e)
                  _uiState.value = BankingUiState(error = e.message)
                }
                .collect { json ->
                  val obj = Json.parseToJsonElement(json).jsonObject
                  val rcBase64 = obj["rc"]?.jsonPrimitive?.content
                  if (rcBase64 != null) {
                    val bytes = Base64.decode(rcBase64, Base64.DEFAULT)
                    _uiState.value = BankingUiState(isLoading = false, rcDocument = bytes)
                  }
                }
      } catch (e: Exception) {
        Log.e(TAG, "Unexpected error", e)
        _uiState.value = BankingUiState(error = e.message)
      }
    }
  }

  fun reset() {
    _uiState.value = BankingUiState()
  }

  fun dispatchAction(action: UserActionPayload) {
    viewModelScope.launch(Dispatchers.IO) { graphQlClient.sendUserAction(action) }
  }
}
