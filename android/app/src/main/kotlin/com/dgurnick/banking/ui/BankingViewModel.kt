package com.dgurnick.banking.ui

import android.app.Application
import android.util.Base64
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dgurnick.banking.client.BankingGraphQlClient
import com.dgurnick.banking.client.MapData
import com.dgurnick.banking.client.MapMarker
import com.dgurnick.banking.client.Offer
import com.dgurnick.banking.client.OffersData
import com.dgurnick.banking.client.UserActionPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val TAG = "Banking.ViewModel"

/**
 * UI state that supports:
 * - RC documents (static server-rendered UI)
 * - Map data (native interactive map)
 * - Offers data (native expandable cards)
 */
data class BankingUiState(
        val isLoading: Boolean = false,
        val error: String? = null,
        val rcDocument: ByteArray? = null,
        val mapData: MapData? = null,
        val offersData: OffersData? = null
)

class BankingViewModel(application: Application) : AndroidViewModel(application) {

  private val appCtx = application.applicationContext
  private lateinit var graphQlClient: BankingGraphQlClient
  private val json = Json { ignoreUnknownKeys = true }

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
                .collect { jsonStr -> parseResponse(jsonStr) }
      } catch (e: Exception) {
        Log.e(TAG, "Unexpected error", e)
        _uiState.value = BankingUiState(error = e.message)
      }
    }
  }

  private fun parseResponse(jsonStr: String) {
    try {
      Log.d(TAG, "Parsing response: $jsonStr")
      val obj = json.parseToJsonElement(jsonStr).jsonObject
      val type = obj["type"]?.jsonPrimitive?.content

      Log.d(TAG, "Response type: $type")

      when (type) {
        "map" -> {
          Log.d(TAG, "Parsing map data")
          val mapDataObj = obj["mapData"]?.jsonObject ?: return
          val mapData =
                  MapData(
                          userLat = mapDataObj["userLat"]?.jsonPrimitive?.content?.toDoubleOrNull()
                                          ?: 0.0,
                          userLon = mapDataObj["userLon"]?.jsonPrimitive?.content?.toDoubleOrNull()
                                          ?: 0.0,
                          title = mapDataObj["title"]?.jsonPrimitive?.content ?: "Map",
                          markers =
                                  mapDataObj["markers"]?.jsonArray?.map { markerEl ->
                                    val m = markerEl.jsonObject
                                    MapMarker(
                                            id = m["id"]?.jsonPrimitive?.content ?: "",
                                            lat = m["lat"]?.jsonPrimitive?.content?.toDoubleOrNull()
                                                            ?: 0.0,
                                            lon = m["lon"]?.jsonPrimitive?.content?.toDoubleOrNull()
                                                            ?: 0.0,
                                            title = m["title"]?.jsonPrimitive?.content ?: "",
                                            snippet = m["snippet"]?.jsonPrimitive?.content ?: ""
                                    )
                                  }
                                          ?: emptyList()
                  )
          Log.d(TAG, "Map data parsed: ${mapData.markers.size} markers")
          _uiState.value = BankingUiState(isLoading = false, mapData = mapData)
        }
        "offers" -> {
          val offersDataObj = obj["offersData"]?.jsonObject ?: return
          val offersData =
                  OffersData(
                          title = offersDataObj["title"]?.jsonPrimitive?.content ?: "Offers",
                          offers =
                                  offersDataObj["offers"]?.jsonArray?.map { offerEl ->
                                    val o = offerEl.jsonObject
                                    Offer(
                                            id = o["id"]?.jsonPrimitive?.content ?: "",
                                            title = o["title"]?.jsonPrimitive?.content ?: "",
                                            rate = o["rate"]?.jsonPrimitive?.content ?: "",
                                            rateLabel = o["rateLabel"]?.jsonPrimitive?.content
                                                            ?: "",
                                            tag = o["tag"]?.jsonPrimitive?.content ?: "",
                                            summary = o["summary"]?.jsonPrimitive?.content ?: "",
                                            details = o["details"]?.jsonPrimitive?.content ?: "",
                                            ctaText = o["ctaText"]?.jsonPrimitive?.content ?: "",
                                            ctaUrl = o["ctaUrl"]?.jsonPrimitive?.content ?: ""
                                    )
                                  }
                                          ?: emptyList()
                  )
          _uiState.value = BankingUiState(isLoading = false, offersData = offersData)
        }
        else -> {
          // Legacy RC document response
          val rcBase64 = obj["rc"]?.jsonPrimitive?.content
          if (rcBase64 != null) {
            val bytes = Base64.decode(rcBase64, Base64.DEFAULT)
            _uiState.value = BankingUiState(isLoading = false, rcDocument = bytes)
          }
        }
      }
    } catch (e: Exception) {
      Log.e(TAG, "Parse error", e)
      _uiState.value = BankingUiState(error = "Failed to parse response: ${e.message}")
    }
  }

  fun reset() {
    _uiState.value = BankingUiState()
  }

  fun dispatchAction(action: UserActionPayload) {
    viewModelScope.launch(Dispatchers.IO) { graphQlClient.sendUserAction(action) }
  }
}
