package com.dgurnick.banking.ui

import android.app.Application
import android.util.Base64
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dgurnick.banking.client.BankingGraphQlClient
import com.dgurnick.banking.client.ChatMessage
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

/** UI state for chatbot-style interface with message history. */
data class BankingUiState(
        val messages: List<ChatMessage> = emptyList(),
        val isLoading: Boolean = false,
        val error: String? = null
)

class BankingViewModel(application: Application) : AndroidViewModel(application) {

  private val appCtx = application.applicationContext
  private lateinit var graphQlClient: BankingGraphQlClient
  private val json = Json { ignoreUnknownKeys = true }

  private val _uiState = MutableStateFlow(BankingUiState())
  val uiState: StateFlow<BankingUiState> = _uiState.asStateFlow()

  companion object {
    val SUGGESTED_PROMPTS =
            listOf(
                    "Where is the nearest ATM?",
                    "What is my account balance?",
                    "What offers do you have for me?"
            )
  }

  fun init(baseUrl: String) {
    graphQlClient = BankingGraphQlClient(baseUrl)
    // Add initial welcome message
    addWelcomeMessage()
  }

  private fun addWelcomeMessage() {
    val welcomeMessage =
            ChatMessage.BotMessage(
                    text = "Welcome Friend! How can I help you today?",
                    buttons = SUGGESTED_PROMPTS
            )
    _uiState.value = BankingUiState(messages = listOf(welcomeMessage))
  }

  fun sendPrompt(prompt: String, surfaceId: String = "main", fromMessageId: String? = null) {
    // If prompt came from a button, mark those buttons as used
    if (fromMessageId != null) {
      markButtonsUsed(fromMessageId)
    }

    // Add user message to chat
    val userMessage = ChatMessage.UserMessage(text = prompt)
    val loadingMessage = ChatMessage.LoadingMessage()

    _uiState.update { currentState ->
      currentState.copy(
              messages = currentState.messages + userMessage + loadingMessage,
              isLoading = true
      )
    }

    viewModelScope.launch(Dispatchers.IO) {
      try {
        graphQlClient
                .subscribeRaw(prompt, surfaceId)
                .catch { e ->
                  Log.e(TAG, "Stream error", e)
                  removeLoadingAndAddError(e.message ?: "Unknown error")
                }
                .collect { jsonStr -> parseResponse(jsonStr) }
      } catch (e: Exception) {
        Log.e(TAG, "Unexpected error", e)
        removeLoadingAndAddError(e.message ?: "Unknown error")
      }
    }
  }

  private fun removeLoadingAndAddError(error: String) {
    _uiState.update { currentState ->
      val messagesWithoutLoading =
              currentState.messages.filterNot { it is ChatMessage.LoadingMessage }
      val errorMessage = ChatMessage.BotMessage(text = "Sorry, something went wrong: $error")
      currentState.copy(
              messages = messagesWithoutLoading + errorMessage,
              isLoading = false,
              error = error
      )
    }
  }

  private fun parseResponse(jsonStr: String) {
    try {
      Log.d(TAG, "Parsing response: $jsonStr")
      val obj = json.parseToJsonElement(jsonStr).jsonObject
      val type = obj["type"]?.jsonPrimitive?.content

      Log.d(TAG, "Response type: $type")

      when (type) {
        "chat" -> {
          Log.d(TAG, "Parsing chat response")
          val text = obj["text"]?.jsonPrimitive?.content ?: ""
          val buttons = obj["buttons"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
          addBotMessage(text, buttons)
        }
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
          addContentMessage(mapData = mapData)
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
                                            ctaUrl = o["ctaUrl"]?.jsonPrimitive?.content ?: "",
                                            ctaAction = o["ctaAction"]?.jsonPrimitive?.content
                                    )
                                  }
                                          ?: emptyList()
                  )
          addContentMessage(offersData = offersData)
        }
        "action" -> {
          Log.d(TAG, "Parsing action response")
          val action = obj["action"]?.jsonPrimitive?.content
          when (action) {
            "reset_conversation" -> {
              // Reset the conversation to start over
              reset()
            }
            else -> {
              Log.w(TAG, "Unknown action: $action")
            }
          }
        }
        else -> {
          // Legacy RC document response
          val rcBase64 = obj["rc"]?.jsonPrimitive?.content
          if (rcBase64 != null) {
            val bytes = Base64.decode(rcBase64, Base64.DEFAULT)
            addContentMessage(rcDocument = bytes)
          }
        }
      }
    } catch (e: Exception) {
      Log.e(TAG, "Parse error", e)
      removeLoadingAndAddError("Failed to parse response: ${e.message}")
    }
  }

  private fun addContentMessage(
          mapData: MapData? = null,
          offersData: OffersData? = null,
          rcDocument: ByteArray? = null
  ) {
    _uiState.update { currentState ->
      val messagesWithoutLoading =
              currentState.messages.filterNot { it is ChatMessage.LoadingMessage }
      val contentMessage =
              ChatMessage.ContentMessage(
                      mapData = mapData,
                      offersData = offersData,
                      rcDocument = rcDocument
              )
      currentState.copy(
              messages = messagesWithoutLoading + contentMessage,
              isLoading = false,
              error = null
      )
    }
  }

  private fun addBotMessage(text: String, buttons: List<String> = emptyList()) {
    _uiState.update { currentState ->
      val messagesWithoutLoading =
              currentState.messages.filterNot { it is ChatMessage.LoadingMessage }
      val botMessage = ChatMessage.BotMessage(text = text, buttons = buttons)
      currentState.copy(
              messages = messagesWithoutLoading + botMessage,
              isLoading = false,
              error = null
      )
    }
  }

  /** Mark buttons as used on a specific message (hides them from UI). */
  private fun markButtonsUsed(messageId: String) {
    _uiState.update { currentState ->
      currentState.copy(
              messages =
                      currentState.messages.map { message ->
                        if (message is ChatMessage.BotMessage && message.id == messageId) {
                          message.copy(buttonsUsed = true)
                        } else {
                          message
                        }
                      }
      )
    }
  }

  /**
   * Mark an offer as selected in a ContentMessage. This hides all other offers and disables the
   * selected one.
   */
  fun markOfferSelected(messageId: String, offerId: String) {
    _uiState.update { currentState ->
      currentState.copy(
              messages =
                      currentState.messages.map { message ->
                        if (message is ChatMessage.ContentMessage && message.id == messageId) {
                          message.copy(selectedOfferId = offerId)
                        } else {
                          message
                        }
                      }
      )
    }
  }

  /** Reset both local chat history and server-side conversation state. */
  fun reset(surfaceId: String = "main") {
    viewModelScope.launch(Dispatchers.IO) {
      try {
        graphQlClient.resetConversation(surfaceId)
        Log.d(TAG, "Server conversation reset successful")
      } catch (e: Exception) {
        Log.e(TAG, "Failed to reset server conversation", e)
      }
    }
    addWelcomeMessage()
  }

  fun dispatchAction(action: UserActionPayload) {
    viewModelScope.launch(Dispatchers.IO) { graphQlClient.sendUserAction(action) }
  }
}
